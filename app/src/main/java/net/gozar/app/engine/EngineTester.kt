package net.gozar.app.engine

import android.content.Context
import gozarcore.Gozarcore
import net.gozar.app.ConfigBuilder
import net.gozar.app.GhajarLog
import net.gozar.app.ProxyConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.abs

/** One step of a test, in the order it ran. [ok] null means "not applicable to this protocol". */
data class TestStep(val name: String, val ok: Boolean?, val detail: String = "", val ms: Int? = null) {
    fun toJson(): JSONObject = JSONObject().put("n", name).put("d", detail).apply {
        if (ok != null) put("ok", ok)
        if (ms != null) put("ms", ms)
    }
    companion object {
        fun fromJson(o: JSONObject) = TestStep(o.optString("n"), if (o.has("ok")) o.optBoolean("ok") else null,
            o.optString("d"), if (o.has("ms")) o.optInt("ms") else null)
    }
}

/** The outcome of one real test of one profile on the engine that would carry it. */
data class EngineTestResult(
    val engine: EngineId,
    /** The core started and published its local proxy (or Xray accepted the config). */
    val coreStarted: Boolean,
    /** At least one HTTPS request went through the tunnel and got the expected answer. */
    val internetOk: Boolean,
    /** Median round trip of the successful requests, ms. */
    val latencyMs: Int?,
    /** Short, redacted reason when something failed. */
    val error: String?,
    val testedAt: Long = System.currentTimeMillis(),
    /** Mean absolute deviation of the successful round trips, ms. */
    val jitterMs: Int? = null,
    /** Share of the requests that got no answer, 0..100. */
    val lossPct: Int? = null,
    /** Time from starting the engine to its local proxy being ready, ms. */
    val handshakeMs: Int? = null,
    val exitIp: String? = null,
    /** ISO country of the exit, as reported by the probe endpoint. */
    val exitCountry: String? = null,
    val steps: List<TestStep> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("engine", engine.name).put("core", coreStarted).put("net", internetOk).put("at", testedAt)
        .put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
        .apply {
            latencyMs?.let { put("lat", it) }; jitterMs?.let { put("jit", it) }; lossPct?.let { put("loss", it) }
            handshakeMs?.let { put("hs", it) }; exitIp?.let { put("ip", it) }; exitCountry?.let { put("cc", it) }
            error?.let { put("err", it) }
        }

    companion object {
        fun fromJson(o: JSONObject): EngineTestResult? = runCatching {
            EngineTestResult(
                engine = EngineId.valueOf(o.getString("engine")),
                coreStarted = o.optBoolean("core"), internetOk = o.optBoolean("net"),
                latencyMs = o.optInt("lat", -1).takeIf { it >= 0 }, error = o.optString("err").ifBlank { null },
                testedAt = o.optLong("at"), jitterMs = o.optInt("jit", -1).takeIf { it >= 0 },
                lossPct = o.optInt("loss", -1).takeIf { it >= 0 }, handshakeMs = o.optInt("hs", -1).takeIf { it >= 0 },
                exitIp = o.optString("ip").ifBlank { null }, exitCountry = o.optString("cc").ifBlank { null },
                steps = o.optJSONArray("steps")?.let { a -> (0 until a.length()).map { TestStep.fromJson(a.getJSONObject(it)) } }.orEmpty()
            )
        }.getOrNull()
    }
}

/**
 * Last test result per config, kept across restarts so the server list can
 * show latency, jitter, loss, exit country and when it was measured.
 */
object EngineTestStore {
    private const val PREFS = "ghajar_engine_tests"
    private val results = ConcurrentHashMap<String, EngineTestResult>()
    @Volatile private var loaded = false
    @Volatile private var context: Context? = null
    private val _version = kotlinx.coroutines.flow.MutableStateFlow(0)
    /** Bumps on every new result, for UI that wants to recompose. */
    val version: kotlinx.coroutines.flow.StateFlow<Int> = _version

    fun attach(ctx: Context) { context = ctx.applicationContext; load() }

    fun get(id: String): EngineTestResult? { load(); return results[id] }

    fun put(id: String, r: EngineTestResult) {
        results[id] = r
        _version.value = _version.value + 1
        val ctx = context ?: return
        runCatching {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(id, r.toJson().toString()).apply()
        }
    }

    private fun load() {
        if (loaded) return
        val ctx = context ?: return
        loaded = true
        runCatching {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                (v as? String)?.let { EngineTestResult.fromJson(JSONObject(it)) }?.let { results[k] = it }
            }
        }
    }
}

/**
 * Tests a profile on the engine that will actually carry it.
 *
 * Steps, each recorded with its outcome: configuration, DNS resolution of the
 * server, TCP reachability, TLS handshake (where the protocol uses TLS over
 * TCP), engine start (local proxy ready), then several HTTPS requests through
 * the tunnel for latency, jitter and loss, and one to a trace endpoint for
 * the exit IP and country.
 *
 * - Xray profiles: requests go through Xray's own measureDelay (a temporary
 *   Xray instance per request); Xray's test API does not expose the exit IP.
 * - sing-box and sidecar profiles: a temporary sing-box (and helper) on a free
 *   local port; everything above goes through its SOCKS5 inbound, and the
 *   processes are always stopped.
 *
 * Nothing is inferred: no answer means failed, and a TCP handshake alone is
 * never reported as a working server.
 */
object EngineTester {

    /** Same target Xray's measureDelay uses: a 204 with no body. */
    const val PROBE_URL = "https://www.gstatic.com/generate_204"
    /** Plain-text "ip=… loc=…" from Cloudflare's edge. */
    const val TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace"

    /** The probe targets; only local end-to-end harnesses change them. */
    @Volatile internal var probeUrl = PROBE_URL
    @Volatile internal var traceUrl = TRACE_URL

    @Volatile private var appContext: Context? = null
    private val singboxSlots = Semaphore(2)
    private val seq = AtomicInteger()

    fun attach(context: Context) {
        appContext = context.applicationContext
        EngineTestStore.attach(context)
    }

    /**
     * Drop-in for `Gozarcore.measureDelay(ConfigBuilder.buildForTest(cfg))`:
     * milliseconds, or -1 when the profile did not carry a request. Blocking.
     * Records a quick result for the server list.
     */
    fun realDelay(cfg: ProxyConfig, chain: ProxyConfig? = null, boundMs: Long = 0L): Long =
        when (EngineRouting.engineFor(cfg)) {
            EngineId.SINGBOX -> test(cfg, if (boundMs > 0) boundMs.toInt() else 10_000, probes = 2, trace = false).latencyMs?.toLong() ?: -1L
            else -> {
                val json = ConfigBuilder.buildForTest(cfg, chain)
                val ms = if (boundMs > 0) Gozarcore.measureDelayBounded(json, boundMs) else Gozarcore.measureDelay(json)
                if (chain == null) EngineTestStore.put(cfg.id, merge(cfg.id, EngineTestResult(EngineId.XRAY, ms >= 0, ms >= 0,
                    ms.takeIf { it >= 0 }?.toInt(), if (ms >= 0) null else "no answer through Xray",
                    lossPct = if (ms >= 0) 0 else 100)))
                ms
            }
        }

    /** Keeps the fields a quick test cannot measure (exit IP, country, steps) from the last full one. */
    private fun merge(id: String, quick: EngineTestResult): EngineTestResult {
        val last = EngineTestStore.get(id) ?: return quick
        if (!quick.internetOk) return quick.copy(exitIp = last.exitIp, exitCountry = last.exitCountry)
        return quick.copy(jitterMs = quick.jitterMs ?: last.jitterMs, exitIp = last.exitIp, exitCountry = last.exitCountry,
            steps = quick.steps.ifEmpty { last.steps })
    }

    /** A full result for [cfg]. Blocking; call off the main thread. */
    fun test(cfg: ProxyConfig, timeoutMs: Int = 10_000, probes: Int = 5, trace: Boolean = true): EngineTestResult {
        val engine = EngineRouting.engineFor(cfg)
        val r = when (engine) {
            EngineId.SINGBOX -> testSingBox(cfg, timeoutMs, probes, trace)
            EngineId.XRAY -> testXray(cfg, probes)
            else -> EngineTestResult(engine, coreStarted = false, internetOk = false, latencyMs = null,
                error = "this engine is tested by connecting to it",
                steps = listOf(TestStep("engine", null, "tested by connecting")))
        }
        val stored = if (probes < 5 || !trace) merge(cfg.id, r) else r
        EngineTestStore.put(cfg.id, stored)
        return stored
    }

    // ---- reachability steps shared by every engine ----

    private fun preflight(cfg: ProxyConfig, steps: MutableList<TestStep>): Boolean {
        val target = Reach.target(cfg)
        if (target.host.isBlank()) {
            // mieru:// full links keep the servers inside their own encoded
            // configuration; the engine resolves and dials them itself.
            if (cfg.protocol == "mieru") { steps += TestStep("dns", null, "inside the engine's configuration"); return true }
            steps += TestStep("dns", false, "no server address"); return false
        }
        val dnsStart = System.nanoTime()
        val addr = runCatching { InetAddress.getAllByName(target.host).first() }.getOrNull()
        if (addr == null) { steps += TestStep("dns", false, "cannot resolve ${target.host}"); return false }
        steps += TestStep("dns", true, addr.hostAddress.orEmpty(), ms(dnsStart))
        if (target.udp) {
            steps += TestStep("tcp", null, "UDP protocol; reachability is proven by the handshake")
        } else {
            val t = System.nanoTime()
            val ok = runCatching { Socket().use { it.connect(InetSocketAddress(addr, target.port), 5000) }; true }.getOrDefault(false)
            steps += TestStep("tcp", ok, "${target.host}:${target.port}", if (ok) ms(t) else null)
            if (!ok) return false
            if (target.tls) steps += tlsHandshake(addr, target)
        }
        return true
    }

    private fun tlsHandshake(addr: InetAddress, target: Reach.Target): TestStep {
        val t = System.nanoTime()
        return runCatching {
            // Only the handshake is checked here; certificate trust is the
            // engine's decision (some profiles pin or allow insecure).
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
                override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })
            val ctx = SSLContext.getInstance("TLS").apply { init(null, trustAll, null) }
            val raw = Socket().apply { connect(InetSocketAddress(addr, target.port), 5000); soTimeout = 5000 }
            (ctx.socketFactory.createSocket(raw, target.sni, target.port, true) as SSLSocket).use { s ->
                if (target.sni.isNotBlank() && !target.sni.first().isDigit()) {
                    s.sslParameters = s.sslParameters.apply { serverNames = listOf(SNIHostName(target.sni)) }
                }
                s.startHandshake()
                val cn = (s.session.peerCertificates.firstOrNull() as? X509Certificate)?.subjectX500Principal?.name.orEmpty()
                TestStep("tls", true, "${s.session.protocol} ${cn.take(60)}", ms(t))
            }
        }.getOrElse { TestStep("tls", false, it.javaClass.simpleName, null) }
    }

    // ---- Xray ----

    private fun testXray(cfg: ProxyConfig, probes: Int): EngineTestResult {
        val steps = mutableListOf(TestStep("config", true, "Xray"))
        preflight(cfg, steps)
        val json = runCatching { ConfigBuilder.buildForTest(cfg) }.getOrElse {
            return EngineTestResult(EngineId.XRAY, false, false, null, "config: ${it.javaClass.simpleName}", steps = steps)
        }
        val samples = (1..probes).map { runCatching { Gozarcore.measureDelay(json) }.getOrDefault(-1L).takeIf { it >= 0 }?.toInt() }
        return summarise(EngineId.XRAY, coreStarted = samples.any { it != null }, samples, steps, null, null, null,
            failure = "no answer through Xray")
    }

    // ---- sing-box (+ sidecar) ----

    private fun testSingBox(cfg: ProxyConfig, timeoutMs: Int, probes: Int, trace: Boolean): EngineTestResult {
        val steps = mutableListOf<TestStep>()
        val ctx = appContext ?: return EngineTestResult(EngineId.SINGBOX, false, false, null, "not initialised")
        if (!SingBoxRunner.available(ctx)) return EngineTestResult(EngineId.SINGBOX, false, false, null, "sing-box is not in this build")
        val spec = runCatching { SingBoxConfig.spec(cfg) }.getOrElse {
            return EngineTestResult(EngineId.SINGBOX, false, false, null, it.message ?: "bad profile",
                steps = listOf(TestStep("config", false, it.message.orEmpty())))
        } ?: return EngineTestResult(EngineId.SINGBOX, false, false, null, "not a sing-box profile")
        val side = JSONObject(spec).optJSONObject("sidecar")
        if (side != null) {
            val launch = runCatching { Sidecars.launch(side) }.getOrElse {
                return EngineTestResult(EngineId.SINGBOX, false, false, null, it.message,
                    steps = listOf(TestStep("config", false, it.message.orEmpty())))
            }
            steps += TestStep("config", true, "sing-box + ${side.optString("kind")}")
            if (!java.io.File(ctx.applicationInfo.nativeLibraryDir, launch.binary).exists()) {
                return EngineTestResult(EngineId.SINGBOX, false, false, null, "${launch.binary} is not in this build", steps = steps)
            }
        } else steps += TestStep("config", true, "sing-box")
        if (!preflight(cfg, steps)) {
            return EngineTestResult(EngineId.SINGBOX, false, false, null, steps.last().let { "${it.name}: ${it.detail}" }, steps = steps)
        }
        if (!singboxSlots.tryAcquire(timeoutMs.toLong(), TimeUnit.MILLISECONDS)) {
            return EngineTestResult(EngineId.SINGBOX, false, false, null, "busy", steps = steps)
        }
        val runner = SingBoxRunner("SingBoxTest", "singbox-test-${seq.incrementAndGet() % 4}")
        try {
            val started = System.nanoTime()
            val failure = runner.start(ctx, spec)
            if (failure != null) {
                steps += TestStep("engine", false, failure)
                return EngineTestResult(EngineId.SINGBOX, false, false, null, failure, steps = steps)
            }
            val hs = ms(started)
            steps += TestStep("engine", true, "local proxy ready on ${runner.socksPort}", hs)
            val samples = probeSeries(runner.socksPort, timeoutMs, probes)
            val exit = if (trace && samples.any { it != null }) exitInfo(runner.socksPort, timeoutMs) else null
            val reason = (runner.sidecarOutput() + runner.lastOutput()).lastOrNull {
                it.contains("error", true) || it.contains("fail", true) || it.contains("denied", true)
            }?.take(200)
            return summarise(EngineId.SINGBOX, true, samples, steps, hs, exit?.first, exit?.second,
                failure = reason ?: "no answer through the tunnel")
        } finally {
            runner.stop()
            singboxSlots.release()
        }
    }

    private fun summarise(
        engine: EngineId, coreStarted: Boolean, samples: List<Int?>, steps: MutableList<TestStep>,
        handshakeMs: Int?, exitIp: String?, exitCountry: String?, failure: String
    ): EngineTestResult {
        val ok = samples.filterNotNull()
        val loss = if (samples.isEmpty()) 100 else (samples.size - ok.size) * 100 / samples.size
        steps += TestStep("https", ok.isNotEmpty(), "${ok.size}/${samples.size} answered", ok.minOrNull())
        if (exitIp != null) steps += TestStep("exit", true, listOfNotNull(exitIp, exitCountry).joinToString(" "))
        if (ok.isEmpty()) return EngineTestResult(engine, coreStarted, false, null, failure,
            lossPct = 100, handshakeMs = handshakeMs, steps = steps)
        val sorted = ok.sorted()
        val median = sorted[sorted.size / 2]
        val jitter = ok.map { abs(it - median) }.average().toInt()
        return EngineTestResult(engine, coreStarted, true, median, null, jitterMs = jitter, lossPct = loss,
            handshakeMs = handshakeMs, exitIp = exitIp, exitCountry = exitCountry, steps = steps)
    }

    /** [n] HTTPS requests through a local SOCKS5 port; one entry per request, null for no answer. */
    fun probeSeries(socksPort: Int, timeoutMs: Int, n: Int): List<Int?> {
        // One warm-up request pays for the tunnel's own handshake and is not counted.
        probeOnce(socksPort, timeoutMs)
        return (1..n.coerceAtLeast(1)).map { probeOnce(socksPort, timeoutMs) }
    }

    /** Kept for callers of the old single-number API. */
    fun probe(socksPort: Int, timeoutMs: Int): Int? = probeSeries(socksPort, timeoutMs, 1).firstOrNull()

    private fun probeOnce(socksPort: Int, timeoutMs: Int): Int? = runCatching {
        val started = System.nanoTime()
        val conn = URL(probeUrl).openConnection(socks(socksPort)) as HttpURLConnection
        conn.connectTimeout = timeoutMs; conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = false; conn.useCaches = false
        val code = try { conn.responseCode } finally { conn.disconnect() }
        if (code == 204 || code == 200) ms(started) else null
    }.onFailure { GhajarLog.d("EngineTester", "probe failed: ${it.javaClass.simpleName}") }.getOrNull()

    /** Exit IP and country through the tunnel, from the trace endpoint. */
    fun exitInfo(socksPort: Int, timeoutMs: Int): Pair<String, String?>? = runCatching {
        val conn = URL(traceUrl).openConnection(socks(socksPort)) as HttpURLConnection
        conn.connectTimeout = timeoutMs; conn.readTimeout = timeoutMs
        val body = try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
        val map = parseTrace(body)
        map["ip"]?.let { it to map["loc"] }
    }.getOrNull()

    fun parseTrace(body: String): Map<String, String> = body.lineSequence()
        .mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it).trim() to l.substring(it + 1).trim() } }
        .toMap()

    private fun socks(port: Int) = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))

    private fun ms(startNanos: Long): Int = ((System.nanoTime() - startNanos) / 1_000_000L).toInt()
}

/** Where a profile's first network hop goes, for the reachability steps. Pure; unit-tested. */
object Reach {
    data class Target(val host: String, val port: Int, val udp: Boolean, val tls: Boolean, val sni: String)

    private val UDP = setOf("tuic", "hysteria", "hysteria2", "wireguard", "amneziawg", "juicity")

    fun target(c: ProxyConfig): Target {
        val x = c.extraJson()
        return when (c.protocol) {
            // DNS tunnels talk to the resolver, not to the tunnel server.
            "dnstt", "vaydns", "noizdns", "masterdns", "stormdns", "cottendns", "slipstream" -> Target(
                c.address, c.port, udp = c.mode.ifBlank { "udp" } == "udp", tls = c.mode == "dot" || c.mode == "doh", sni = c.address)
            "ssh" -> {
                val via = x.optJSONObject("transport")
                val proxyHost = via?.optString("proxyHost").orEmpty()
                if (proxyHost.isNotBlank()) Target(proxyHost, via!!.optInt("proxyPort", 80), false,
                    via.optString("mode").contains("tls") || via.optString("mode") == "wss", via.optString("sni").ifBlank { c.address })
                else Target(c.address, c.port, false,
                    via?.optString("mode").orEmpty().let { it.contains("tls") || it == "wss" },
                    via?.optString("sni").orEmpty().ifBlank { c.address })
            }
            else -> Target(c.address, c.port, udp = c.protocol in UDP,
                tls = c.security == "tls" || c.security == "reality" || c.protocol in setOf("anytls", "openconnect", "naive", "trojan", "sstp", "shadowtls"),
                sni = c.sni.ifBlank { c.host.ifBlank { c.address } })
        }
    }
}

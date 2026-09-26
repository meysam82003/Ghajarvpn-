package net.gozar.app.engine

import android.content.Context
import gozarcore.Gozarcore
import net.gozar.app.ConfigBuilder
import net.gozar.app.GhajarLog
import net.gozar.app.ProxyConfig
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The outcome of one real test of one profile on the engine that would carry it. */
data class EngineTestResult(
    val engine: EngineId,
    /** The core started and published its local proxy (or Xray accepted the config). */
    val coreStarted: Boolean,
    /** An HTTPS request went through the tunnel and got the expected answer. */
    val internetOk: Boolean,
    /** Round trip of that request in ms, only when [internetOk]. */
    val latencyMs: Int?,
    /** Short, redacted reason when something failed. */
    val error: String?,
    val testedAt: Long = System.currentTimeMillis()
)

/**
 * Tests a profile on the engine that will actually carry it.
 *
 * Before this, every "real delay" went to Xray, so a TUIC or SSH profile was
 * measured by a core that cannot speak it. Now:
 *
 * - Xray profiles: unchanged, Xray's own measureDelay (a request through a
 *   temporary Xray instance).
 * - sing-box profiles: a temporary sing-box on a free local port, then an
 *   HTTPS request through its SOCKS5 inbound; the process is always stopped.
 *
 * Nothing is inferred: no answer means failed, and a TCP handshake alone is
 * never reported as a working server.
 */
object EngineTester {

    /** Same target Xray's measureDelay uses: a 204 with no body. */
    const val PROBE_URL = "https://www.gstatic.com/generate_204"

    @Volatile private var appContext: Context? = null
    private val singboxSlots = Semaphore(2)
    private val seq = AtomicInteger()

    fun attach(context: Context) { appContext = context.applicationContext }

    /**
     * Drop-in for `Gozarcore.measureDelay(ConfigBuilder.buildForTest(cfg))`:
     * milliseconds, or -1 when the profile did not carry a request. Blocking.
     */
    fun realDelay(cfg: ProxyConfig, chain: ProxyConfig? = null, boundMs: Long = 0L): Long =
        when (EngineRouting.engineFor(cfg)) {
            EngineId.SINGBOX -> test(cfg, if (boundMs > 0) boundMs.toInt() else 10_000).latencyMs?.toLong() ?: -1L
            else -> {
                val json = ConfigBuilder.buildForTest(cfg, chain)
                if (boundMs > 0) Gozarcore.measureDelayBounded(json, boundMs) else Gozarcore.measureDelay(json)
            }
        }

    /** A full result for [cfg]. Blocking; call off the main thread. */
    fun test(cfg: ProxyConfig, timeoutMs: Int = 10_000): EngineTestResult {
        val engine = EngineRouting.engineFor(cfg)
        return when (engine) {
            EngineId.SINGBOX -> testSingBox(cfg, timeoutMs)
            EngineId.XRAY -> {
                val ms = runCatching { Gozarcore.measureDelay(ConfigBuilder.buildForTest(cfg)) }.getOrDefault(-1L)
                EngineTestResult(engine, coreStarted = ms >= 0, internetOk = ms >= 0,
                    latencyMs = ms.takeIf { it >= 0 }?.toInt(), error = if (ms >= 0) null else "no answer through Xray")
            }
            else -> EngineTestResult(engine, coreStarted = false, internetOk = false, latencyMs = null,
                error = "this engine is tested by connecting to it")
        }
    }

    private fun testSingBox(cfg: ProxyConfig, timeoutMs: Int): EngineTestResult {
        val ctx = appContext ?: return EngineTestResult(EngineId.SINGBOX, false, false, null, "not initialised")
        if (!SingBoxRunner.available(ctx)) return EngineTestResult(EngineId.SINGBOX, false, false, null, "sing-box is not in this build")
        val spec = SingBoxConfig.spec(cfg) ?: return EngineTestResult(EngineId.SINGBOX, false, false, null, "not a sing-box profile")
        if (!singboxSlots.tryAcquire(timeoutMs.toLong(), TimeUnit.MILLISECONDS)) {
            return EngineTestResult(EngineId.SINGBOX, false, false, null, "busy")
        }
        val runner = SingBoxRunner("SingBoxTest", "singbox-test-${seq.incrementAndGet() % 4}")
        try {
            val failure = runner.start(ctx, spec)
            if (failure != null) return EngineTestResult(EngineId.SINGBOX, false, false, null, failure)
            val ms = probe(runner.socksPort, timeoutMs)
            return if (ms != null) EngineTestResult(EngineId.SINGBOX, true, true, ms, null)
            else EngineTestResult(EngineId.SINGBOX, true, false, null,
                runner.lastOutput().lastOrNull { it.contains("ERROR") || it.contains("error") }?.take(200) ?: "no answer through the tunnel")
        } finally {
            runner.stop()
            singboxSlots.release()
        }
    }

    /**
     * One HTTPS request through a local SOCKS5 port. The host is left
     * unresolved so the name is resolved at the far end, as it would be for
     * real traffic. Returns the round trip in ms, or null.
     */
    fun probe(socksPort: Int, timeoutMs: Int): Int? = runCatching {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        // The first request pays for the tunnel handshake; the second is the
        // delay a user would see, which is what the list shows.
        var measured: Int? = null
        repeat(2) {
            val started = System.nanoTime()
            val conn = URL(PROBE_URL).openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            val code = try { conn.responseCode } finally { conn.disconnect() }
            if (code != 204 && code != 200) return@runCatching null
            measured = ((System.nanoTime() - started) / 1_000_000L).toInt()
        }
        measured
    }.onFailure { GhajarLog.d("EngineTester", "probe failed: ${it.javaClass.simpleName}") }.getOrNull()
}

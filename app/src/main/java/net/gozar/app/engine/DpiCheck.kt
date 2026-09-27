package net.gozar.app.engine

import android.content.Context
import net.gozar.app.GhajarLog
import net.gozar.app.ProxyConfig
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * "How does DPI see this profile?" A diagnostic, never part of a connection.
 *
 * One real engine test is sent through `ghajar-helper dpirelay`, which
 * forwards it unchanged to the real server and records the first bytes in
 * each direction; `ndpi-classify` (nDPI 6.0, LGPL components only) then
 * classifies that flow. The result says what an nDPI-based filter would call
 * the traffic (e.g. "SSH", "OpenVPN", "HTTP") and which risks it raises.
 *
 * Limits, shown to the user as they are: nDPI's TLS, QUIC and DNS dissectors
 * are dual-licensed and are off in this build, so TLS-based profiles are only
 * matched by port; profiles whose first hop is not one server:port (DNS
 * tunnels, link-only engines, Tor, Psiphon, OpenVPN, IKEv2) are not checked.
 */
object DpiCheck {

    data class Result(
        val protocol: String? = null,
        val category: String? = null,
        val confidence: String? = null,
        val risks: List<String> = emptyList(),
        val packets: Int = 0,
        val note: String? = null,
        val error: String? = null
    )

    const val CLASSIFIER = "libndpiclassify.so"

    private val NOT_APPLICABLE = mapOf(
        "dnstt" to "a DNS tunnel talks to resolvers, not to one server", "vaydns" to "", "noizdns" to "",
        "masterdns" to "", "stormdns" to "", "cottendns" to "", "slipstream" to "",
        "mieru" to "the Mieru link is used as is", "brook" to "the Brook link is used as is",
        "tor" to "Tor picks its own relays", "psiphon" to "Psiphon picks its own servers",
        "openvpn" to "OpenVPN runs in its own module", "ikev2" to "IKEv2 runs in the system IPsec stack"
    )

    /** Why [c] cannot be checked, or null when it can. */
    fun unsupported(c: ProxyConfig): String? {
        NOT_APPLICABLE[c.protocol]?.let { return it.ifBlank { "a DNS tunnel talks to resolvers, not to one server" } }
        if (c.protocol == "ssh" && c.extraJson().optJSONObject("transport")?.optString("proxyHost").orEmpty().isNotBlank())
            return "the first hop is an HTTP proxy"
        if (c.address.isBlank() || c.port !in 1..65535) return "no server address"
        return null
    }

    /**
     * The same profile pointed at the relay. TLS names and HTTP hosts keep the
     * original server name, so the recorded bytes are what the real server
     * would see. Pure; unit-tested.
     */
    fun viaRelay(c: ProxyConfig, relayPort: Int): ProxyConfig {
        val origin = c.address
        val isName = origin.any { it.isLetter() } && !origin.contains(':')
        var r = c.copy(id = "dpi:" + c.id, address = "127.0.0.1", port = relayPort)
        if (r.sni.isBlank() && isName) r = r.copy(sni = origin)
        if (r.host.isBlank() && isName) r = r.copy(host = origin)
        if (c.protocol == "amneziawg" || c.protocol == "wireguard") {
            val x = c.extraJson()
            val conf = x.optString("conf")
            if (conf.isNotBlank()) {
                val fixed = conf.lines().joinToString("\n") { line ->
                    if (line.trim().startsWith("Endpoint", true)) "Endpoint = 127.0.0.1:$relayPort" else line
                }
                r = r.copy(extra = x.put("conf", fixed).toString())
            }
        }
        return r
    }

    /** Parses the classifier's JSON line (nDPI may print notices before it). */
    fun parse(output: String): Result {
        val line = output.lines().lastOrNull { it.trim().startsWith("{") } ?: return Result(error = "no answer from the classifier")
        return runCatching {
            val o = JSONObject(line)
            val risks = o.optJSONArray("risks")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            val conf = o.optString("confidence")
            Result(
                protocol = o.optString("protocol").takeIf { it.isNotBlank() && it != "Unknown" },
                category = o.optString("category").takeIf { it.isNotBlank() && it != "Unspecified" },
                confidence = conf, risks = risks, packets = o.optInt("packets"),
                note = if (conf.contains("port", true)) "matched by port only: the TLS/QUIC dissectors are not in this build (nDPI dual licence)" else null
            )
        }.getOrElse { Result(error = "unreadable classifier output") }
    }

    /** Runs the check. Blocking (~15 s); call off the main thread. */
    fun run(context: Context, c: ProxyConfig): Result {
        unsupported(c)?.let { return Result(error = "not checked: $it") }
        val lib = context.applicationInfo.nativeLibraryDir
        if (!File(lib, CLASSIFIER).exists()) return Result(error = "nDPI is not in this build")
        if (!File(lib, Sidecars.HELPER).exists()) return Result(error = "ghajar-helper is not in this build")
        val target = Reach.target(c)
        val port = SingBoxRunner.freePort() ?: return Result(error = "no free local port")
        val out = File(context.cacheDir, "dpi-${System.nanoTime()}.flow")
        val host = if (target.host.contains(':')) "[${target.host}]" else target.host
        val args = mutableListOf("dpirelay", "-listen", SidecarLaunch.PORT, "-target", "$host:${target.port}",
            "-out", out.absolutePath, "-idle", "8s")
        if (target.udp) args += "-udp"
        val relay = SidecarRunner("DpiRelay", "dpi")
        try {
            relay.start(context, SidecarLaunch(Sidecars.HELPER, args, socks = false), port)?.let { return Result(error = it) }
            val test = EngineTester.test(viaRelay(c, port), timeoutMs = 10_000, probes = 2, trace = false)
            val deadline = System.currentTimeMillis() + 10_000
            while (!out.exists() && System.currentTimeMillis() < deadline) Thread.sleep(200)
            if (!out.exists()) return Result(error = "no traffic was recorded" + (test.error?.let { " ($it)" } ?: ""))
            val p = ProcessBuilder(File(lib, CLASSIFIER).absolutePath, out.absolutePath).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(15, TimeUnit.SECONDS)) p.destroyForcibly()
            return parse(text).let { r -> if (!test.internetOk && r.error == null) r.copy(note = listOfNotNull(r.note, "the test connection itself failed: ${test.error}").joinToString("; ")) else r }
        } catch (e: Exception) {
            GhajarLog.e("DpiCheck", "failed: ${e.javaClass.simpleName}")
            return Result(error = e.javaClass.simpleName)
        } finally {
            relay.stop()
            out.delete()
            EngineTestStore.forget("dpi:" + c.id)
        }
    }
}

package net.gozar.app.engine

import android.content.Context
import net.gozar.app.GhajarLog
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * How to run one helper process ("sidecar") in front of sing-box.
 *
 * Every non-sing-box engine in this app has the same shape: an executable
 * built in CI from pinned source (`lib<name>.so` in nativeLibraryDir) that
 * listens on one local TCP port. Either that port is a SOCKS5 server
 * ([socks] = true: Mieru, Brook, AmneziaWG, SSTP, SoftEther), or a
 * raw forward to whatever the remote server forwards to ([socks] = false:
 * the SSH transports), which sing-box then
 * speaks SOCKS5 or SSH to. sing-box always sits on top, so every engine ends
 * in the same local SOCKS5 inbound that zeptun carries the device into.
 *
 * Arguments and file contents may contain [PORT] and [DIR]; the runner
 * replaces them with the chosen local port and the private working directory.
 */
data class SidecarLaunch(
    val binary: String,
    val args: List<String>,
    val socks: Boolean,
    /** Files written into the working directory before start, name to content. */
    val files: Map<String, String> = emptyMap(),
    val env: Map<String, String> = emptyMap(),
    val readyTimeoutMs: Long = 15_000,
    /** Files that hold credentials; deleted once the process is listening. */
    val secretFiles: Set<String> = files.keys
) {
    companion object {
        const val PORT = "{port}"
        /** A second free local port, for engines that need one (Mieru's RPC). */
        const val PORT2 = "{port2}"
        const val DIR = "{dir}"
    }
}

/**
 * Runs one [SidecarLaunch]. Returns from [start] only when the process is
 * alive and its local port accepts a connection; whether traffic actually
 * flows is for the engine test to decide.
 */
class SidecarRunner(private val tag: String, private val subdir: String) {

    @Volatile private var process: Process? = null
    @Volatile private var stopping = false
    private val tail = ArrayDeque<String>()

    @Volatile var onUnexpectedExit: ((Int?) -> Unit)? = null

    fun isRunning(): Boolean = process?.isAlive == true

    fun lastOutput(): List<String> = synchronized(tail) { tail.toList() }

    /** Returns null on success or a reason that is safe to show. */
    fun start(context: Context, launch: SidecarLaunch, port: Int): String? {
        stop()
        stopping = false
        val bin = File(context.applicationInfo.nativeLibraryDir, launch.binary)
        if (!bin.exists()) return "${launch.binary.removePrefix("lib").removeSuffix(".so")} is not in this build"
        val dir = File(context.noBackupFilesDir, subdir).apply { mkdirs() }
        val port2 = SingBoxRunner.freePort() ?: 0
        fun fill(s: String) = s.replace(SidecarLaunch.PORT2, port2.toString()).replace(SidecarLaunch.PORT, port.toString()).replace(SidecarLaunch.DIR, dir.absolutePath)
        runCatching { launch.files.forEach { (name, content) -> File(dir, name).writeText(fill(content)) } }
            .onFailure { return "could not write the engine configuration" }
        val p = runCatching {
            ProcessBuilder(listOf(bin.absolutePath) + launch.args.map(::fill))
                .directory(dir)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = dir.absolutePath
                    environment()["TMPDIR"] = context.cacheDir.absolutePath
                    launch.env.forEach { (k, v) -> environment()[k] = fill(v) }
                }
                .start()
        }.getOrElse { return "${launch.binary} could not be started: ${it.javaClass.simpleName}" }
        process = p
        synchronized(tail) { tail.clear() }
        thread(isDaemon = true, name = "$tag-log") {
            runCatching {
                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val line = GhajarLog.redact(raw)
                        synchronized(tail) { tail.addLast(line); while (tail.size > 60) tail.removeFirst() }
                        if (!stopping) GhajarLog.i(tag, line)
                    }
                }
            }
            val code = runCatching { p.waitFor() }.getOrNull()
            if (!stopping && process === p) {
                GhajarLog.e(tag, "exited unexpectedly, code=$code")
                process = null
                // A throw here would kill the whole app from a daemon thread.
                runCatching { onUnexpectedExit?.invoke(code) }
                    .onFailure { GhajarLog.e(tag, "exit handler failed: ${it.javaClass.simpleName}") }
            }
        }
        val deadline = System.currentTimeMillis() + launch.readyTimeoutMs
        var ready = false
        while (System.currentTimeMillis() < deadline) {
            if (stopping) break
            if (!p.isAlive) break
            if (runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300); true } }.getOrDefault(false)) { ready = true; break }
            Thread.sleep(150)
        }
        launch.secretFiles.forEach { runCatching { File(dir, it).delete() } }
        if (ready) return null
        val last = lastOutput().lastOrNull { it.isNotBlank() }.orEmpty().take(200)
        val alive = p.isAlive
        stop()
        return if (!alive) "${launch.binary.removePrefix("lib").removeSuffix(".so")} exited: $last"
            else "${launch.binary.removePrefix("lib").removeSuffix(".so")} did not open its local port"
    }

    fun stop() {
        stopping = true
        val p = process ?: return
        process = null
        runCatching { p.destroy(); if (!p.waitFor(2000, TimeUnit.MILLISECONDS)) p.destroyForcibly() }
    }
}

/** Builds the [SidecarLaunch] for a spec's "sidecar" object. Pure, so it is unit-tested. */
object Sidecars {

    /** Every binary a sidecar kind needs, for availability checks. */
    fun binaryFor(kind: String): String = when (kind) {
        "sshtransport", "awg", "mieru", "brook", "sstp", "softether" -> HELPER
        else -> throw IllegalArgumentException("unknown engine: $kind")
    }

    fun launch(raw: JSONObject): SidecarLaunch = launchWith(raw)

    private fun launchWith(spec: JSONObject): SidecarLaunch = when (val kind = spec.optString("kind")) {
        "sshtransport" -> sshTransport(spec)
        "awg" -> SidecarLaunch(HELPER, listOf("awg", "-listen", SidecarLaunch.PORT, "-config", "${SidecarLaunch.DIR}/awg.conf"),
            socks = true, files = mapOf("awg.conf" to spec.optString("conf").also {
                require(it.contains("[Interface]", true) && it.contains("[Peer]", true)) { "AmneziaWG: the profile needs an [Interface] and a [Peer]" }
            }))
        "mieru" -> SidecarLaunch(HELPER, listOf("mieru", "-listen", SidecarLaunch.PORT, "-rpc", SidecarLaunch.PORT2,
            "-url", spec.optString("url").also { require(it.startsWith("mieru://") || it.startsWith("mierus://")) { "Mieru: a mieru:// or mierus:// link is needed" } },
            "-dir", SidecarLaunch.DIR), socks = true, readyTimeoutMs = 20_000)
        "sstp" -> sstp(spec)
        "softether" -> softether(spec)
        "brook" -> SidecarLaunch(HELPER, listOf("brook", "-listen", SidecarLaunch.PORT,
            "-url", spec.optString("url").also { require(it.startsWith("brook://")) { "Brook: a brook:// link is needed" } }), socks = true)
        else -> throw IllegalArgumentException("unknown engine: $kind")
    }

    /**
     * ghajar-helper sstp (native/ghajar-helper/sstp.go): SSTP + PPP in
     * userspace, served as SOCKS5. The password goes through a file that is
     * deleted once the helper listens, never on the command line.
     */
    fun sstp(spec: JSONObject): SidecarLaunch {
        val args = mutableListOf("sstp", "-listen", SidecarLaunch.PORT,
            "-server", spec.optString("server").also { require(it.isNotBlank() && !it.startsWith(":")) { "SSTP: no server" } },
            "-user", spec.optString("user").also { require(it.isNotBlank()) { "SSTP: no user name" } },
            "-auth", spec.optString("auth").ifBlank { "auto" },
            "-mtu", spec.optInt("mtu", 1400).coerceIn(576, 1500).toString())
        spec.optString("sni").takeIf { it.isNotBlank() }?.let { args += listOf("-sni", it) }
        spec.optString("pin").takeIf { it.isNotBlank() }?.let {
            require(Regex("^[0-9a-fA-F:]{64,95}$").matches(it)) { "SSTP: the certificate pin must be a SHA-256 in hex" }
            args += listOf("-pin", it)
        }
        if (spec.optBoolean("allowInsecure")) args += "-insecure"
        return SidecarLaunch(HELPER, args, socks = true, files = mapOf("sstp.pass" to spec.optString("password")),
            env = mapOf("SSTP_PASSWORD_FILE" to "${SidecarLaunch.DIR}/sstp.pass"), readyTimeoutMs = 30_000)
    }

    /**
     * ghajar-helper softether (native/ghajar-helper/softether.go): SoftEther's
     * own protocol to a Virtual Hub, DHCP (or a static address) and ARP in
     * userspace, served as SOCKS5. Password via a file deleted once listening.
     */
    fun softether(spec: JSONObject): SidecarLaunch {
        val args = mutableListOf("softether", "-listen", SidecarLaunch.PORT,
            "-server", spec.optString("server").also { require(it.isNotBlank() && !it.startsWith(":")) { "SoftEther: no server" } },
            "-hub", spec.optString("hub").ifBlank { "DEFAULT" },
            "-user", spec.optString("user").also { require(it.isNotBlank()) { "SoftEther: no user name" } },
            "-mtu", spec.optInt("mtu", 1400).coerceIn(576, 1500).toString())
        if (spec.optBoolean("plain")) args += "-plain"
        spec.optString("sni").takeIf { it.isNotBlank() }?.let { args += listOf("-sni", it) }
        spec.optString("pin").takeIf { it.isNotBlank() }?.let {
            require(Regex("^[0-9a-fA-F:]{64,95}$").matches(it)) { "SoftEther: the certificate pin must be a SHA-256 in hex" }
            args += listOf("-pin", it)
        }
        if (spec.optBoolean("allowInsecure")) args += "-insecure"
        spec.optString("ip").takeIf { it.isNotBlank() }?.let {
            require(Regex("^\\d{1,3}(\\.\\d{1,3}){3}/\\d{1,2}$").matches(it)) { "SoftEther: static address must look like 10.0.0.2/24" }
            args += listOf("-ip", it)
        }
        spec.optString("gw").takeIf { it.isNotBlank() }?.let { args += listOf("-gw", it) }
        spec.optString("dns").takeIf { it.isNotBlank() }?.let { args += listOf("-dns", it) }
        return SidecarLaunch(HELPER, args, socks = true, files = mapOf("se.pass" to spec.optString("password")),
            env = mapOf("SE_PASSWORD_FILE" to "${SidecarLaunch.DIR}/se.pass"), readyTimeoutMs = 30_000)
    }

    /** The in-repo helper (native/ghajar-helper, GPL-3.0), built in CI. */
    const val HELPER = "libghajarhelper.so"

    /** SSH transport modes the helper implements; "direct" needs no helper at all. */
    val SSH_MODES = setOf("payload", "http-proxy", "https-proxy", "tls", "payload-tls", "ws", "wss")

    /**
     * ghajar-helper sshtransport (native/ghajar-helper/sshtransport.go): a raw
     * forward to the SSH server through the chosen disguise; sing-box's SSH
     * client (with its host-key check) runs on top.
     */
    fun sshTransport(spec: JSONObject): SidecarLaunch {
        val mode = spec.optString("mode")
        require(mode in SSH_MODES) { "SSH: unknown transport mode $mode" }
        val host = spec.optString("host").also { require(it.isNotBlank()) { "SSH: no server" } }
        val args = mutableListOf("sshtransport", "-listen", SidecarLaunch.PORT, "-mode", mode,
            "-host", host, "-port", spec.optInt("port", 22).toString())
        val proxyHost = spec.optString("proxyHost")
        if (proxyHost.isNotBlank()) args += listOf("-proxy", proxyHost + ":" + spec.optInt("proxyPort", if (mode == "https-proxy" || mode.contains("tls") || mode == "wss") 443 else 80))
        else require(mode != "http-proxy" && mode != "https-proxy") { "SSH: this mode needs a proxy address" }
        spec.optString("sni").takeIf { it.isNotBlank() }?.let { args += listOf("-sni", it) }
        spec.optString("payload").takeIf { it.isNotBlank() }?.let {
            args += listOf("-payload", java.util.Base64.getEncoder().encodeToString(it.toByteArray()))
        }
        spec.optString("wsPath").takeIf { it.isNotBlank() }?.let { args += listOf("-ws-path", it) }
        spec.optString("wsHost").takeIf { it.isNotBlank() }?.let { args += listOf("-ws-host", it) }
        spec.optString("ua").takeIf { it.isNotBlank() }?.let { args += listOf("-ua", it) }
        if (spec.optBoolean("wsFraming")) args += "-ws-framing"
        if (spec.optBoolean("verify")) args += "-verify"
        require(mode !in setOf("payload", "payload-tls") || spec.optString("payload").isNotBlank()) { "SSH: this mode needs a payload" }
        return SidecarLaunch(HELPER, args, socks = false)
    }

}

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
 * ([socks] = true: MasterDNS family, Mieru, Juicity, AmneziaWG, SSTP), or a
 * raw forward to whatever the remote server forwards to ([socks] = false:
 * the dnstt family, Slipstream, the SSH transports), which sing-box then
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
        fun fill(s: String) = s.replace(SidecarLaunch.PORT, port.toString()).replace(SidecarLaunch.DIR, dir.absolutePath)
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
                onUnexpectedExit?.invoke(code)
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

    private val HEX_KEY = Regex("^[0-9a-fA-F]{64}$")

    /** Every binary a sidecar kind needs, for availability checks. */
    fun binaryFor(kind: String): String = when (kind) {
        "dnstt" -> "libdnstt.so"
        else -> throw IllegalArgumentException("unknown engine: $kind")
    }

    fun launch(spec: JSONObject): SidecarLaunch = when (val kind = spec.optString("kind")) {
        "dnstt" -> SidecarLaunch("libdnstt.so", dnsttArgs(spec, SidecarLaunch.PORT), socks = false)
        else -> throw IllegalArgumentException("unknown engine: $kind")
    }

    /**
     * dnstt-client (v1.20260501.0, dnstt-client/main.go):
     * `(-udp ADDR | -dot ADDR | -doh URL) -pubkey HEX DOMAIN 127.0.0.1:PORT`.
     */
    fun dnsttArgs(spec: JSONObject, port: String): List<String> {
        val (flag, resolver) = resolverFlag(spec)
        val domain = domain(spec)
        return listOf(flag, resolver, "-pubkey", pubkey(spec), domain, "127.0.0.1:$port")
    }

    internal fun resolverFlag(spec: JSONObject): Pair<String, String> {
        val resolver = spec.optString("resolver").trim()
        require(resolver.isNotEmpty()) { "DNS tunnel: no resolver" }
        val flag = when (spec.optString("transport", "udp")) { "doh" -> "-doh"; "dot" -> "-dot"; else -> "-udp" }
        return flag to resolver
    }

    internal fun domain(spec: JSONObject): String =
        spec.optString("domain").trim().trim('.').also { require(it.isNotEmpty()) { "DNS tunnel: no domain" } }

    internal fun pubkey(spec: JSONObject): String =
        spec.optString("pubkey").trim().also { require(HEX_KEY.matches(it)) { "DNS tunnel: the server key must be 64 hex digits" } }.lowercase()
}

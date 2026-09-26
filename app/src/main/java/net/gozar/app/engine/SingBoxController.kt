package net.gozar.app.engine

import android.content.Context
import net.gozar.app.GhajarLog
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the sing-box executable (`libsingbox.so`, built in CI from the pinned
 * source by scripts/build-singbox.sh) as a subprocess with a local SOCKS5
 * inbound, the same contract AetherController already uses.
 *
 * The process is this app's UID, and the app excludes itself from its own
 * tun (GozarVpnService.applyPerApp), so sing-box's sockets to the server do
 * not loop back into the tunnel.
 *
 * One instance per running sing-box; see [SingBoxController] for the session one.
 *
 * Honest by construction: [start] returns false unless `sing-box check`
 * accepted the configuration AND the process is alive AND its SOCKS port
 * accepts a connection. Whether traffic actually flows is decided by the
 * engine test (EngineTester), never assumed here.
 */
class SingBoxRunner(private val TAG: String, private val subdir: String) {

    private val READY_TIMEOUT_MS = 30_000L
    private val TAIL_LINES = 60

    @Volatile var socksPort = 0
        private set

    @Volatile private var process: Process? = null
    /** Runs first when the profile is a DNS tunnel; stopped with this runner. */
    private val dnstt = DnsttRunner("$TAG-dnstt")
    @Volatile private var stopping = false
    private val tail = ArrayDeque<String>()

    /** Called once when the process exits without [stop] having asked it to. */
    @Volatile var onUnexpectedExit: ((Int?) -> Unit)? = null

    fun isRunning(): Boolean = process?.isAlive == true

    /** Whether a dnstt client is carrying this runner's upstream. */
    fun dnsttRunning(): Boolean = dnstt.isRunning()

    private fun workDir(context: Context): File = File(context.noBackupFilesDir, subdir).apply { mkdirs() }

    /** The last lines sing-box printed, already redacted. */
    fun lastOutput(): List<String> = synchronized(tail) { tail.toList() }

    /**
     * Starts sing-box for [spec] (from [SingBoxConfig.spec]).
     * Returns null on success, or a short reason that is safe to show and log.
     */
    fun start(context: Context, spec: String, port: Int = 0): String? {
        stop()
        stopping = false
        // Any failure after this point also tears down what did start
        // (dnstt in front of sing-box), so nothing is left running.
        val failure = startInner(context, spec, port)
        if (failure != null) stop()
        return failure
    }

    private fun startInner(context: Context, spec: String, port: Int): String? {
        val bin = binary(context)
        if (!bin.exists()) return "sing-box is not in this build"
        val chosen = if (port > 0) port else freePort() ?: return "no free local port"
        val dir = workDir(context)
        val file = File(dir, "config.json")
        var effective = spec
        val tunnel = runCatching { org.json.JSONObject(spec).optJSONObject("dnstt") }.getOrNull()
        if (tunnel != null) {
            if (!DnsttRunner.available(context)) return "dnstt is not in this build"
            val dnsttPort = freePort() ?: return "no free local port"
            val failure = dnstt.start(context, tunnel, dnsttPort)
            if (failure != null) return failure
            dnstt.onUnexpectedExit = { code -> if (!stopping) { stop(); onUnexpectedExit?.invoke(code) } }
            effective = org.json.JSONObject(spec).apply {
                optJSONObject("outbound")?.put("server_port", dnsttPort)
                remove("dnstt")
            }.toString()
        }
        val json = runCatching { SingBoxConfig.full(effective, chosen) }.getOrElse { return "bad profile: ${it.javaClass.simpleName}" }
        file.writeText(json)

        // Validate first: a configuration error is reported as one, instead
        // of as "timed out waiting for the proxy".
        val check = runCatching {
            val p = ProcessBuilder(bin.absolutePath, "check", "-c", file.absolutePath, "-D", dir.absolutePath, "--disable-color")
                .directory(dir).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(15, TimeUnit.SECONDS)) { p.destroyForcibly(); return "sing-box check timed out" }
            p.exitValue() to out
        }.getOrElse { return "sing-box could not be started: ${it.javaClass.simpleName}" }
        if (check.first != 0) {
            val reason = GhajarLog.redact(check.second.lineSequence().lastOrNull { it.isNotBlank() }.orEmpty()).take(240)
            GhajarLog.e(TAG, "config rejected: $reason")
            file.delete()
            return "sing-box rejected the profile: $reason"
        }

        val p = runCatching {
            ProcessBuilder(bin.absolutePath, "run", "-c", file.absolutePath, "-D", dir.absolutePath, "--disable-color")
                .directory(dir).redirectErrorStream(true)
                .apply { environment()["HOME"] = dir.absolutePath; environment()["TMPDIR"] = context.cacheDir.absolutePath }
                .start()
        }.getOrElse {
            file.delete()
            return "sing-box could not be started: ${it.javaClass.simpleName}"
        }
        process = p
        socksPort = chosen
        synchronized(tail) { tail.clear() }
        thread(isDaemon = true, name = "singbox-log") {
            runCatching {
                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val line = GhajarLog.redact(raw)
                        synchronized(tail) {
                            tail.addLast(line)
                            while (tail.size > TAIL_LINES) tail.removeFirst()
                        }
                        if (!stopping) GhajarLog.i(TAG, line)
                    }
                }
            }
            val code = runCatching { p.waitFor() }.getOrNull()
            if (!stopping && process === p) {
                GhajarLog.e(TAG, "sing-box exited unexpectedly, code=$code")
                process = null
                onUnexpectedExit?.invoke(code)
            }
        }
        // The configuration holds credentials; sing-box has read it by the
        // time its inbound listens, so it does not stay on disk.
        val ready = waitForPort(p, chosen)
        file.delete()
        return if (ready) null else {
            val last = lastOutput().lastOrNull { it.isNotBlank() }.orEmpty().take(240)
            stop()
            if (last.isBlank()) "sing-box did not open its local proxy" else "sing-box did not open its local proxy: $last"
        }
    }

    private fun waitForPort(p: Process, port: Int): Boolean {
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (stopping || !p.isAlive) return false
            val ok = runCatching {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300); true }
            }.getOrDefault(false)
            if (ok) {
                GhajarLog.i(TAG, "socks ready on 127.0.0.1:$port")
                return true
            }
            Thread.sleep(200)
        }
        GhajarLog.e(TAG, "timed out waiting for the local proxy")
        return false
    }

    fun stop() {
        stopping = true
        dnstt.onUnexpectedExit = null
        dnstt.stop()
        val p = process ?: return
        process = null
        runCatching {
            p.destroy()
            if (!p.waitFor(2000, TimeUnit.MILLISECONDS)) p.destroyForcibly()
        }
    }

    companion object {
        fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, "libsingbox.so")
        fun available(context: Context): Boolean = runCatching { binary(context).exists() }.getOrDefault(false)
        internal fun freePort(): Int? = runCatching { ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort } }.getOrNull()
    }
}

/** The instance that carries the VPN session. Engine tests use their own [SingBoxRunner]. */
val SingBoxController = SingBoxRunner("SingBox", "singbox")

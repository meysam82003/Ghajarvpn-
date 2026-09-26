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
 * Runs upstream dnstt-client (`libdnstt.so`, built by scripts/build-dnstt.sh
 * from the pinned CC0 source) as a subprocess.
 *
 * The invocation is the one in dnstt-client/main.go at v1.20260501.0:
 *
 *     dnstt-client (-udp ADDR | -dot ADDR | -doh URL) -pubkey HEX DOMAIN 127.0.0.1:PORT
 *
 * dnstt forwards each TCP connection on the local port through the tunnel to
 * whatever the server operator put behind it (a SOCKS5 proxy or an SSH
 * server). sing-box then speaks SOCKS5 or SSH to that local port
 * (SingBoxConfig, protocol "dnstt"), and zeptun carries the device into
 * sing-box. An open local port only means dnstt is listening; whether the
 * tunnel carries anything is decided by the engine test.
 */
class DnsttRunner(private val tag: String) {

    @Volatile private var process: Process? = null
    @Volatile private var stopping = false
    private val tail = ArrayDeque<String>()

    @Volatile var onUnexpectedExit: ((Int?) -> Unit)? = null

    fun isRunning(): Boolean = process?.isAlive == true

    fun lastOutput(): List<String> = synchronized(tail) { tail.toList() }

    /** [spec] is the "dnstt" object of a SingBoxConfig spec. Returns null on success or a reason. */
    fun start(context: Context, spec: JSONObject, port: Int): String? {
        stop()
        stopping = false
        val bin = binary(context)
        if (!bin.exists()) return "dnstt is not in this build"
        val args = runCatching { args(spec, port) }.getOrElse { return it.message ?: "incomplete DNS tunnel profile" }
        val p = runCatching {
            ProcessBuilder(listOf(bin.absolutePath) + args)
                .redirectErrorStream(true)
                .apply { environment()["TMPDIR"] = context.cacheDir.absolutePath }
                .start()
        }.getOrElse { return "dnstt could not be started: ${it.javaClass.simpleName}" }
        process = p
        synchronized(tail) { tail.clear() }
        thread(isDaemon = true, name = "dnstt-log") {
            runCatching {
                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val line = GhajarLog.redact(raw)
                        synchronized(tail) { tail.addLast(line); while (tail.size > 40) tail.removeFirst() }
                        if (!stopping) GhajarLog.i(tag, line)
                    }
                }
            }
            val code = runCatching { p.waitFor() }.getOrNull()
            if (!stopping && process === p) {
                GhajarLog.e(tag, "dnstt exited unexpectedly, code=$code")
                process = null
                onUnexpectedExit?.invoke(code)
            }
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (stopping) return "stopped"
            if (!p.isAlive) {
                val last = lastOutput().lastOrNull { it.isNotBlank() }.orEmpty().take(200)
                return "dnstt exited: $last"
            }
            val up = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300); true } }.getOrDefault(false)
            if (up) return null
            Thread.sleep(150)
        }
        stop()
        return "dnstt did not open its local port"
    }

    fun stop() {
        stopping = true
        val p = process ?: return
        process = null
        runCatching { p.destroy(); if (!p.waitFor(2000, TimeUnit.MILLISECONDS)) p.destroyForcibly() }
    }

    companion object {
        fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, "libdnstt.so")
        fun available(context: Context): Boolean = runCatching { binary(context).exists() }.getOrDefault(false)

        private val HEX_KEY = Regex("^[0-9a-fA-F]{64}$")

        /** The command line for [spec]; throws with a readable reason when something required is missing. */
        fun args(spec: JSONObject, port: Int): List<String> {
            val transport = spec.optString("transport", "udp")
            val resolver = spec.optString("resolver").trim()
            val domain = spec.optString("domain").trim().trim('.')
            val key = spec.optString("pubkey").trim()
            require(resolver.isNotEmpty()) { "DNS tunnel: no resolver" }
            require(domain.isNotEmpty()) { "DNS tunnel: no domain" }
            require(HEX_KEY.matches(key)) { "DNS tunnel: the server key must be 64 hex digits" }
            val flag = when (transport) { "doh" -> "-doh"; "dot" -> "-dot"; else -> "-udp" }
            return listOf(flag, resolver, "-pubkey", key.lowercase(), domain, "127.0.0.1:$port")
        }
    }
}

package net.gozar.app.sharing

import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A short-lived page on this phone's own Wi-Fi/hotspot address that hands one
 * payload (config links) to a device in the same room, e.g. a laptop that
 * cannot scan a QR code.
 *
 * - binds only to a private (site-local) IPv4 address of a local interface,
 *   never to 0.0.0.0 or a public address;
 * - the path is a random 128-bit token; any other path is a 404 and counts as
 *   a bad attempt, and too many bad attempts close the portal;
 * - it expires on its own, can be one-time (closes after the first read) and
 *   can be revoked at any moment;
 * - requests are rate-limited per client address;
 * - it serves plain text only, never files from disk.
 */
class LocalSharePortal(
    private val payload: String,
    private val ttlMs: Long = 10 * 60_000L,
    private val oneTime: Boolean = true,
    private val maxRequestsPerMinute: Int = 20,
    private val maxBadAttempts: Int = 20,
    private val now: () -> Long = System::currentTimeMillis,
    /** Tests only: allow a loopback bind. */
    private val allowLoopback: Boolean = false
) {
    val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private var server: ServerSocket? = null
    private val closed = AtomicBoolean(false)
    private val badAttempts = AtomicInteger(0)
    private val served = AtomicInteger(0)
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()
    private var expiresAt = 0L

    var onClosed: ((reason: String) -> Unit)? = null

    val isOpen: Boolean get() = !closed.get() && server != null && now() < expiresAt
    val servedCount: Int get() = served.get()
    val expiry: Long get() = expiresAt

    /** Opens on [address]; returns the URL to hand to the other device. */
    fun start(address: InetAddress): String {
        require(address is Inet4Address) { "only IPv4 LAN addresses" }
        require(address.isSiteLocalAddress || (allowLoopback && address.isLoopbackAddress)) { "پورتال فقط روی آدرس شبکهٔ محلی باز می‌شود." }
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(address, 0), 8)
        server = s
        expiresAt = now() + ttlMs
        Thread({ acceptLoop(s) }, "ghajar-share-portal").apply { isDaemon = true }.start()
        return "http://${address.hostAddress}:${s.localPort}/$token"
    }

    fun revoke(reason: String = "revoked") {
        if (closed.compareAndSet(false, true)) {
            runCatching { server?.close() }
            onClosed?.invoke(reason)
        }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!closed.get()) {
            if (now() >= expiresAt) { revoke("expired"); return }
            val client = try { s.soTimeout = 1000; s.accept() } catch (e: java.net.SocketTimeoutException) { continue } catch (e: SocketException) { return }
            runCatching { handle(client) }
            runCatching { client.close() }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 3000
        val peer = client.inetAddress.hostAddress.orEmpty()
        val line = readRequestLine(client.getInputStream()) ?: return respond(client, 400, "bad request")
        if (!rateOk(peer)) return respond(client, 429, "too many requests")
        val parts = line.split(' ')
        if (parts.size < 2 || parts[0] != "GET") return respond(client, 405, "method not allowed")
        if (now() >= expiresAt) { respond(client, 410, "expired"); revoke("expired"); return }
        if (parts[1].trimStart('/') != token) {
            respond(client, 404, "not found")
            if (badAttempts.incrementAndGet() >= maxBadAttempts) revoke("too many bad attempts")
            return
        }
        respond(client, 200, payload)
        served.incrementAndGet()
        if (oneTime) revoke("served")
    }

    private fun rateOk(peer: String): Boolean {
        val t = now()
        val list = hits.getOrPut(peer) { mutableListOf() }
        synchronized(list) {
            list.removeAll { t - it > 60_000L }
            list += t
            return list.size <= maxRequestsPerMinute
        }
    }

    /** Reads the request head (bounded to 8 KB) and returns its first line. */
    private fun readRequestLine(input: InputStream): String? {
        val buf = StringBuilder()
        var total = 0
        var prev3 = ""
        while (total < 8192) {
            val b = input.read()
            if (b < 0) break
            total++
            buf.append(b.toChar())
            prev3 = (prev3 + b.toChar()).takeLast(4)
            if (prev3 == "\r\n\r\n" || prev3.endsWith("\n\n")) break
        }
        if (total >= 8192) return null
        return buf.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun respond(client: Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 410 -> "Gone"; 429 -> "Too Many Requests"; else -> "Error" }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
    }

    companion object {
        /** Private IPv4 addresses of this phone's Wi-Fi / hotspot / Ethernet interfaces. */
        fun lanAddresses(): List<Inet4Address> = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.name.startsWith("tun") }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.toList()
        }.getOrDefault(emptyList())
    }
}

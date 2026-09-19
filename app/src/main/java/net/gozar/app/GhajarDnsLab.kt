package net.gozar.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** How a resolver is reached. */
enum class DnsTransport {
    /** Plain UDP on port 53 - fast, and the one a network can rewrite. */
    UDP,

    /** Plain TCP on port 53 - survives some UDP-level interference. */
    TCP,

    /** DNS over TLS, port 853. */
    DOT,

    /** DNS over HTTPS, RFC 8484. */
    DOH
}

/**
 * One resolver the scan can measure.
 *
 * [address] is an IP or host for UDP/TCP/DoT, and a full URL for DoH.
 */
data class DnsResolver(
    val name: String,
    val transport: DnsTransport,
    val address: String,
    /** Short note shown under the name - who runs it, or what it is for. */
    val note: String = "",
    /** True for resolvers that exist to unblock a region's own services. */
    val local: Boolean = false
) {
    val id: String get() = "${transport.name}:$address"
}

/** What a single measurement found. */
sealed interface DnsProbeResult {
    data object Pending : DnsProbeResult
    data object Testing : DnsProbeResult

    data class Ok(
        val ms: Int,
        val addresses: List<String>,
        /**
         * The resolver answered, but with an address that is not the site's.
         *
         * This is the finding that matters most: a poisoned resolver is fast
         * and looks perfectly healthy, and picking it by latency alone is how
         * a "working" DNS setting breaks every blocked site.
         */
        val poisoned: Boolean,
        /** Answered, but its question did not match ours. */
        val mismatched: Boolean
    ) : DnsProbeResult

    /** Answered with a response code other than NOERROR. */
    data class Refused(val rcode: String, val ms: Int) : DnsProbeResult

    /** No usable answer. [reason] is the real exception class or timeout. */
    data class Failed(val reason: String) : DnsProbeResult
}

/**
 * Measuring DNS resolvers for real, and finding the poisoned ones.
 *
 * The app already publishes a resolver inside the tunnel and can turn on DoH,
 * but both were fixed choices: there was no way to find out which resolver
 * this network actually lets through, or which one is answering with a lie.
 * Nothing here replaces those settings - it measures, and the result can then
 * be chosen as an override.
 *
 * Every number is a real query over the resolver's own transport. Nothing is
 * inferred from a ping, because a host that answers ICMP or a TCP handshake on
 * 53 may still never return a record.
 */
object GhajarDnsLab {

    /** A site that is blocked in the places this app is used. A resolver that
     *  answers it with a local sinkhole address is poisoning. */
    const val BLOCKED_PROBE_HOST = "www.youtube.com"

    /** A site nothing blocks, to tell "this resolver is dead" from "this
     *  resolver lies about blocked names". */
    const val NEUTRAL_PROBE_HOST = "example.com"

    /**
     * Addresses a filtered answer is made of.
     *
     * 10.10.34.x is the long-standing Iranian sinkhole block; the RFC 5737
     * documentation ranges and the unspecified/loopback answers are what other
     * censoring resolvers return. Any of them in place of a real address means
     * the answer was manufactured.
     */
    private val SINKHOLE_PREFIXES = listOf(
        "10.10.34.", "10.10.35.",
        "192.0.2.", "198.51.100.", "203.0.113.",
        "0.0.0.0", "127.0.0.1", "::1"
    )

    /**
     * The resolvers to offer.
     *
     * Global resolvers over four transports, and the Iranian ones, which are
     * the fastest route to that country's own banking and government sites and
     * are deliberately marked [DnsResolver.local] because they do not unblock
     * anything else - a scan that ranked them by latency alone would recommend
     * exactly the wrong thing.
     */
    val Catalogue: List<DnsResolver> = listOf(
        DnsResolver("Cloudflare", DnsTransport.UDP, "1.1.1.1", "1.1.1.1"),
        DnsResolver("Cloudflare", DnsTransport.DOH, "https://1.1.1.1/dns-query", "DoH"),
        DnsResolver("Cloudflare", DnsTransport.DOT, "1.1.1.1", "DoT 853"),
        DnsResolver("Cloudflare", DnsTransport.TCP, "1.1.1.1", "TCP 53"),
        DnsResolver("Google", DnsTransport.UDP, "8.8.8.8", "8.8.8.8"),
        DnsResolver("Google", DnsTransport.DOH, "https://8.8.8.8/dns-query", "DoH"),
        DnsResolver("Google", DnsTransport.DOT, "8.8.8.8", "DoT 853"),
        DnsResolver("Quad9", DnsTransport.UDP, "9.9.9.9", "9.9.9.9"),
        DnsResolver("Quad9", DnsTransport.DOH, "https://9.9.9.9/dns-query", "DoH"),
        DnsResolver("AdGuard", DnsTransport.UDP, "94.140.14.14", "ad blocking"),
        DnsResolver("AdGuard", DnsTransport.DOH, "https://dns.adguard-dns.com/dns-query", "DoH"),
        DnsResolver("OpenDNS", DnsTransport.UDP, "208.67.222.222", "Cisco"),
        DnsResolver("OpenDNS", DnsTransport.DOH, "https://doh.opendns.com/dns-query", "DoH"),
        DnsResolver("Mullvad", DnsTransport.DOH, "https://dns.mullvad.net/dns-query", "DoH"),
        DnsResolver("Quad101", DnsTransport.UDP, "101.101.101.101", "TWNIC"),
        DnsResolver("DNS.SB", DnsTransport.DOH, "https://doh.dns.sb/dns-query", "DoH"),
        DnsResolver("CleanBrowsing", DnsTransport.DOH, "https://doh.cleanbrowsing.org/doh/family-filter/", "DoH"),
        DnsResolver("Shecan", DnsTransport.UDP, "178.22.122.100", "ایران", local = true),
        DnsResolver("Shecan", DnsTransport.DOH, "https://free.shecan.ir/dns-query", "ایران", local = true),
        DnsResolver("Radar", DnsTransport.UDP, "10.202.10.10", "ایران", local = true),
        DnsResolver("Electro", DnsTransport.UDP, "78.157.42.100", "ایران", local = true),
        DnsResolver("Begzar", DnsTransport.UDP, "185.55.226.26", "ایران", local = true),
        DnsResolver("403", DnsTransport.UDP, "10.202.10.202", "ایران", local = true),
        DnsResolver("Pishgaman", DnsTransport.UDP, "5.202.100.100", "ایران", local = true),
        DnsResolver("Asiatech", DnsTransport.UDP, "194.36.174.161", "ایران", local = true)
    )

    /**
     * Measures one resolver against [host].
     *
     * Timing covers the whole exchange, including the TLS or HTTPS handshake
     * for the encrypted transports, because that is what a client actually
     * waits for on the first query.
     */
    suspend fun probe(
        resolver: DnsResolver,
        host: String = BLOCKED_PROBE_HOST,
        timeoutMs: Int = 4_000
    ): DnsProbeResult = withContext(Dispatchers.IO) {
        val id = (1..0xFFFE).random()
        val request = runCatching { DnsWire.query(host, DnsWire.TYPE_A, id) }.getOrNull()
            ?: return@withContext DnsProbeResult.Failed("bad host")
        val started = System.currentTimeMillis()
        try {
            val reply = when (resolver.transport) {
                DnsTransport.UDP -> overUdp(resolver.address, request, timeoutMs)
                DnsTransport.TCP -> overTcp(resolver.address, 53, request, timeoutMs, tls = false)
                DnsTransport.DOT -> overTcp(resolver.address, 853, request, timeoutMs, tls = true)
                DnsTransport.DOH -> overHttps(resolver.address, request, timeoutMs)
            }
            val elapsed = (System.currentTimeMillis() - started).toInt()
            val answer = DnsWire.parse(reply, id, host)
            when {
                answer.rcode != DnsWire.RCODE_NOERROR ->
                    DnsProbeResult.Refused(DnsWire.rcodeName(answer.rcode), elapsed)
                answer.addresses.isEmpty() ->
                    DnsProbeResult.Failed("empty answer")
                else -> DnsProbeResult.Ok(
                    ms = elapsed,
                    addresses = answer.addresses,
                    poisoned = answer.addresses.any { looksManufactured(it) },
                    mismatched = answer.mismatched
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DnsProbeResult.Failed(e.javaClass.simpleName)
        }
    }

    /**
     * Sends one prepared query and returns the raw reply.
     *
     * Exposed so the scan engine can run its own probes - recursion checks,
     * TCP fallbacks, TXT lookups - without a second copy of the socket code.
     * A duplicate of this is how one path ends up with a timeout the other
     * does not have, or a TLS check the other skips.
     *
     * [forceTcp] sends a UDP resolver's query over TCP on 53 instead, which is
     * both a capability test and the correct retry for a truncated answer.
     */
    internal fun exchange(
        resolver: DnsResolver,
        request: ByteArray,
        timeoutMs: Int,
        forceTcp: Boolean = false
    ): ByteArray = when {
        forceTcp && (resolver.transport == DnsTransport.UDP || resolver.transport == DnsTransport.TCP) ->
            overTcp(resolver.address, 53, request, timeoutMs, tls = false)
        resolver.transport == DnsTransport.UDP -> overUdp(resolver.address, request, timeoutMs)
        resolver.transport == DnsTransport.TCP -> overTcp(resolver.address, 53, request, timeoutMs, tls = false)
        resolver.transport == DnsTransport.DOT -> overTcp(resolver.address, 853, request, timeoutMs, tls = true)
        else -> overHttps(resolver.address, request, timeoutMs)
    }

    /** An answer that is a sinkhole rather than the site. */
    internal fun looksManufactured(address: String): Boolean =
        SINKHOLE_PREFIXES.any { address == it || address.startsWith(it) }

    /**
     * Scans a list, reporting each result as it lands.
     *
     * Bounded concurrency: a scan of thirty resolvers opening thirty sockets
     * at once measures the phone's own contention rather than the resolvers.
     */
    suspend fun scan(
        resolvers: List<DnsResolver> = Catalogue,
        host: String = BLOCKED_PROBE_HOST,
        concurrency: Int = 6,
        onResult: (DnsResolver, DnsProbeResult) -> Unit
    ) = coroutineScope {
        val gate = Semaphore(concurrency)
        resolvers.forEach { resolver ->
            launch {
                gate.withPermit {
                    onResult(resolver, DnsProbeResult.Testing)
                    onResult(resolver, probe(resolver, host))
                }
            }
        }
    }

    private fun overUdp(server: String, request: ByteArray, timeoutMs: Int): ByteArray =
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val target = InetAddress.getByName(server)
            socket.send(DatagramPacket(request, request.size, target, 53))
            val buffer = ByteArray(1500)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            buffer.copyOf(packet.length)
        }

    /** TCP and DoT differ only by the TLS wrapper; both use the length prefix. */
    private fun overTcp(
        server: String,
        port: Int,
        request: ByteArray,
        timeoutMs: Int,
        tls: Boolean
    ): ByteArray {
        val plain = Socket()
        plain.connect(InetSocketAddress(server, port), timeoutMs)
        plain.soTimeout = timeoutMs
        val socket = if (!tls) plain else {
            // getDefault() is declared as SocketFactory, which has no overload
            // for layering TLS over an existing socket - the cast is what makes
            // createSocket(Socket, host, port, autoClose) visible.
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket(plain, server, port, true) as SSLSocket)
                .also { it.startHandshake() }
        }
        return socket.use { s ->
            val out = s.getOutputStream()
            out.write((request.size shr 8) and 0xFF)
            out.write(request.size and 0xFF)
            out.write(request)
            out.flush()
            val input = DataInputStream(s.getInputStream())
            val length = (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
            if (length !in 1..8192) throw IllegalStateException("absurd length $length")
            ByteArray(length).also { input.readFully(it) }
        }
    }

    private fun overHttps(url: String, request: ByteArray, timeoutMs: Int): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/dns-message")
            setRequestProperty("Accept", "application/dns-message")
        }
        return try {
            conn.outputStream.use { it.write(request) }
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("HTTP $code")
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}

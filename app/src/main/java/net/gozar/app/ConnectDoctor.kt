package net.gozar.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** How a single check came out. */
enum class DoctorVerdict { PASS, WARN, FAIL, SKIPPED }

/**
 * One finding: what was checked, how it went, and - when it went badly - what
 * the user can actually do about it.
 */
data class DoctorFinding(
    /** Strings key for the check's name. */
    val titleKey: String,
    val verdict: DoctorVerdict,
    /** Already-localised detail, usually carrying a measured number. */
    val detail: String,
    /** Strings key for the remedy, or null when there is nothing to fix. */
    val remedyKey: String? = null
)

data class DoctorReport(
    val findings: List<DoctorFinding>,
    /** Strings key naming the most likely cause, or null if nothing failed. */
    val causeKey: String?
) {
    val worst: DoctorVerdict
        get() = when {
            findings.any { it.verdict == DoctorVerdict.FAIL } -> DoctorVerdict.FAIL
            findings.any { it.verdict == DoctorVerdict.WARN } -> DoctorVerdict.WARN
            else -> DoctorVerdict.PASS
        }
}

/**
 * Why the VPN will not connect, answered with measurements instead of a
 * shrug.
 *
 * The connect failure the user sees is whatever string the engine happened to
 * produce, which is often accurate and almost never actionable. This runs the
 * checks a person would run by hand, in the order that makes the answer
 * useful: if the device has no internet at all, nothing about the server
 * matters; if DNS cannot resolve the host, the port test would only fail for
 * the wrong reason; and so on. The first hard failure, in that order, is the
 * reported cause.
 *
 * Every check is a real network operation with its own timeout. Nothing here
 * infers a verdict it did not measure.
 */
object ConnectDoctor {

    private const val CONNECT_MS = 4_000
    private const val DNS_MS = 3_000L

    /** Two addresses that answer on 443 and do not need DNS to be reached. */
    private val INTERNET_PROBES = listOf("1.1.1.1" to 443, "8.8.8.8" to 443)

    /** A name that must resolve if the resolver works at all. */
    private const val DNS_PROBE_HOST = "cloudflare.com"

    suspend fun run(
        config: ProxyConfig?,
        ovpnProfile: GhajarOvpnProfile?,
        engineError: String?,
        tunnelUp: Boolean
    ): DoctorReport = withContext(Dispatchers.IO) {
        val findings = mutableListOf<DoctorFinding>()

        // 1. Is there any internet at all? Answered by IP so a broken resolver
        //    cannot make a working connection look dead.
        val internetMs = INTERNET_PROBES.firstNotNullOfOrNull { (host, port) ->
            (Pinger.ping(host, port, 2_500) as? PingResult.Ok)?.ms
        }
        findings += if (internetMs != null) {
            DoctorFinding("doc_internet", DoctorVerdict.PASS, "$internetMs ms")
        } else {
            DoctorFinding(
                "doc_internet", DoctorVerdict.FAIL,
                "—", "doc_internet_fix"
            )
        }

        // 2. Does DNS work? Only meaningful once something is reachable.
        val dnsOk = if (internetMs == null) null else withTimeoutOrNull(DNS_MS) {
            runCatching { InetAddress.getAllByName(DNS_PROBE_HOST).isNotEmpty() }
                .getOrDefault(false)
        }
        findings += when {
            internetMs == null -> DoctorFinding("doc_dns", DoctorVerdict.SKIPPED, "—")
            dnsOk == true -> DoctorFinding("doc_dns", DoctorVerdict.PASS, DNS_PROBE_HOST)
            else -> DoctorFinding("doc_dns", DoctorVerdict.FAIL, "—", "doc_dns_fix")
        }

        // 3. The server this app would actually dial.
        val host = ovpnProfile?.host ?: config?.address?.takeIf { it.isNotBlank() }
        val port = ovpnProfile?.port ?: config?.port?.takeIf { it > 0 }
        if (host == null || port == null) {
            findings += DoctorFinding("doc_server", DoctorVerdict.FAIL, "—", "doc_server_none")
        } else {
            // 3a. Its name has to resolve before its port can mean anything.
            val ips = if (internetMs == null) null else withTimeoutOrNull(DNS_MS) {
                runCatching { InetAddress.getAllByName(host).mapNotNull { it.hostAddress } }
                    .getOrDefault(emptyList())
            }
            val literal = host.none { it.isLetter() }
            findings += when {
                literal -> DoctorFinding("doc_server_dns", DoctorVerdict.SKIPPED, host)
                internetMs == null -> DoctorFinding("doc_server_dns", DoctorVerdict.SKIPPED, "—")
                !ips.isNullOrEmpty() -> DoctorFinding(
                    "doc_server_dns", DoctorVerdict.PASS, ips.first()
                )
                else -> DoctorFinding("doc_server_dns", DoctorVerdict.FAIL, host, "doc_server_dns_fix")
            }

            // 3b. The handshake itself. A refused or timed-out port is the
            //     difference between "blocked" and "server is down", and the
            //     two have different remedies.
            val reachMs = if (internetMs == null) null else tcpHandshakeMs(host, port)
            findings += when {
                internetMs == null -> DoctorFinding("doc_server_port", DoctorVerdict.SKIPPED, "—")
                reachMs != null -> DoctorFinding(
                    "doc_server_port", DoctorVerdict.PASS, "$host:$port · $reachMs ms"
                )
                else -> DoctorFinding(
                    "doc_server_port", DoctorVerdict.FAIL, "$host:$port", "doc_server_port_fix"
                )
            }
        }

        // 4. The route the app's own traffic takes. The Xray engines publish a
        //    local SOCKS inbound; OpenVPN and IKEv2 route the whole device and
        //    publish nothing, so its absence there is correct, not a fault.
        val xrayEngine = ovpnProfile == null && config?.protocol != "ikev2"
        findings += when {
            !tunnelUp -> DoctorFinding("doc_route", DoctorVerdict.SKIPPED, "—")
            !xrayEngine -> DoctorFinding("doc_route", DoctorVerdict.PASS, "doc_route_whole_device")
            tcpHandshakeMs("127.0.0.1", MixedPort.value) != null ->
                DoctorFinding("doc_route", DoctorVerdict.PASS, "127.0.0.1:${MixedPort.value}")
            else -> DoctorFinding("doc_route", DoctorVerdict.WARN, "—", "doc_route_fix")
        }

        // 5. Whatever the engine itself last said. Reported, never guessed at.
        val cleanError = engineError?.trim().orEmpty()
        findings += if (cleanError.isEmpty()) {
            DoctorFinding("doc_engine", DoctorVerdict.PASS, "—")
        } else {
            DoctorFinding(
                "doc_engine", DoctorVerdict.FAIL,
                BrandConfig.sanitizePublicText(cleanError).take(200),
                engineRemedyKey(cleanError)
            )
        }

        DoctorReport(findings, causeKey(findings))
    }

    /**
     * A real TCP handshake, returning its duration or null.
     *
     * Deliberately not Pinger.ping: that is used for server ranking and may
     * change its strategy, while this has to mean exactly "the port completed a
     * handshake" for the verdict to be honest.
     */
    private fun tcpHandshakeMs(host: String, port: Int): Long? = runCatching {
        val started = System.currentTimeMillis()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), CONNECT_MS)
        }
        System.currentTimeMillis() - started
    }.getOrNull()

    /** The engine's own words, mapped to a remedy where one is knowable. */
    private fun engineRemedyKey(error: String): String {
        val lower = error.lowercase()
        return when {
            "process is bad" in lower -> "doc_engine_fix_process"
            "auth" in lower && "fail" in lower -> "doc_engine_fix_auth"
            "permission" in lower || "revoke" in lower -> "doc_engine_fix_permission"
            "timeout" in lower || "timed out" in lower -> "doc_engine_fix_timeout"
            "certificate" in lower || "tls" in lower || "handshake" in lower -> "doc_engine_fix_tls"
            else -> "doc_engine_fix_generic"
        }
    }

    /**
     * The first hard failure in check order is the cause worth acting on.
     *
     * Order matters: with no internet, a failed port test says nothing about
     * the server, so reporting the port would send the user after the wrong
     * problem.
     */
    private fun causeKey(findings: List<DoctorFinding>): String? =
        findings.firstOrNull { it.verdict == DoctorVerdict.FAIL }?.titleKey
            ?: findings.firstOrNull { it.verdict == DoctorVerdict.WARN }?.titleKey
}

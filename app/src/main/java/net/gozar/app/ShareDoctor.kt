package net.gozar.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Whether VPN Share is actually usable right now, and if not, which step is
 * missing.
 *
 * The dialog could already show an address and a credential, but nothing ever
 * checked that anything was listening on them - so a user who had the hotspot
 * off, or whose OEM names its AP interface something this app does not
 * recognise, got a plausible-looking address that no second device could ever
 * reach, with no way to tell which of the two it was.
 *
 * Every check here is the same kind as [ConnectDoctor]'s: a real observation,
 * reported as a [DoctorFinding], in the order where the first failure is the
 * one worth acting on.
 */
object ShareDoctor {

    private const val CONNECT_MS = 1_500

    /**
     * [socksPort] and [httpPort] are passed in rather than read here, so the
     * report describes the ports the running tunnel was actually built with.
     */
    suspend fun run(
        sharingOn: Boolean,
        tunnelUp: Boolean,
        engineSupported: Boolean,
        hotspotIp: String?,
        socksPort: Int,
        httpPort: Int,
        credentialSet: Boolean
    ): DoctorReport = withContext(Dispatchers.IO) {
        val findings = mutableListOf<DoctorFinding>()

        findings += if (sharingOn) {
            DoctorFinding("share_chk_on", DoctorVerdict.PASS, "—")
        } else {
            DoctorFinding("share_chk_on", DoctorVerdict.FAIL, "—", "share_fix_on")
        }

        findings += if (tunnelUp) {
            DoctorFinding("share_chk_tunnel", DoctorVerdict.PASS, "—")
        } else {
            DoctorFinding("share_chk_tunnel", DoctorVerdict.FAIL, "—", "share_fix_tunnel")
        }

        // The Xray engines publish the shared inbounds; OpenVPN and IKEv2 are
        // separate engines with no such inbound at all, so there is nothing to
        // listen on and no address that would ever work.
        findings += when {
            !tunnelUp -> DoctorFinding("share_chk_engine", DoctorVerdict.SKIPPED, "—")
            engineSupported -> DoctorFinding("share_chk_engine", DoctorVerdict.PASS, "—")
            else -> DoctorFinding("share_chk_engine", DoctorVerdict.FAIL, "—", "share_fix_engine")
        }

        // The hotspot's own interface. Not the Wi-Fi station and never the
        // cellular one - see hotspotInterfaceAddress for why that matters.
        findings += if (hotspotIp != null) {
            DoctorFinding("share_chk_hotspot", DoctorVerdict.PASS, hotspotIp)
        } else {
            DoctorFinding("share_chk_hotspot", DoctorVerdict.FAIL, "—", "share_fix_hotspot")
        }

        // The part that was never checked: is anything actually accepting a
        // connection on the address the dialog tells the user to type in?
        val reachable: (Int) -> Boolean = { port ->
            hotspotIp != null && listening(hotspotIp, port)
        }
        val canProbe = sharingOn && tunnelUp && engineSupported && hotspotIp != null
        findings += when {
            !canProbe -> DoctorFinding("share_chk_socks", DoctorVerdict.SKIPPED, "—")
            reachable(socksPort) ->
                DoctorFinding("share_chk_socks", DoctorVerdict.PASS, "$hotspotIp:$socksPort")
            else -> DoctorFinding(
                "share_chk_socks", DoctorVerdict.FAIL, "$hotspotIp:$socksPort", "share_fix_port"
            )
        }
        findings += when {
            !canProbe -> DoctorFinding("share_chk_http", DoctorVerdict.SKIPPED, "—")
            reachable(httpPort) ->
                DoctorFinding("share_chk_http", DoctorVerdict.PASS, "$hotspotIp:$httpPort")
            // The HTTP inbound is the convenience one; SOCKS still works
            // without it, so this is a warning rather than a failure.
            else -> DoctorFinding(
                "share_chk_http", DoctorVerdict.WARN, "$hotspotIp:$httpPort", "share_fix_port"
            )
        }

        findings += if (credentialSet) {
            DoctorFinding("share_chk_cred", DoctorVerdict.PASS, "—")
        } else {
            DoctorFinding("share_chk_cred", DoctorVerdict.WARN, "—", "share_fix_cred")
        }

        DoctorReport(findings, cause(findings))
    }

    /** A real connect, so "the port is open" means the port is open. */
    private fun listening(host: String, port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_MS) }
        true
    }.getOrDefault(false)

    private fun cause(findings: List<DoctorFinding>): String? =
        findings.firstOrNull { it.verdict == DoctorVerdict.FAIL }?.titleKey
            ?: findings.firstOrNull { it.verdict == DoctorVerdict.WARN }?.titleKey
}

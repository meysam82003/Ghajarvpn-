package net.gozar.app

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.NetworkInterface

/**
 * What actually happens to traffic if the tunnel drops - reported, not assumed.
 *
 * The app already had a kill-switch toggle and a link to Android's VPN
 * settings, but nothing that said whether any of it was in effect. The three
 * things that decide whether traffic leaks out the normal path are Android's
 * own always-on/block-without-VPN pair, where DNS is resolved, and whether the
 * device has IPv6 that the tunnel does not carry.
 *
 * The IPv6 check is the one worth having: when the tun is built IPv4-only and
 * the phone holds a global IPv6 address on Wi-Fi or its SIM, IPv6 traffic
 * never enters the tunnel at all. Nothing in the app reported that, and it is
 * a leak that exists while the tunnel looks perfectly healthy.
 *
 * Where a fact cannot be read, it is reported as unknown. Nothing here claims
 * a protection is in place because it would be nice if it were.
 */
object LeakGuard {

    /** The hidden Settings.Secure key Android stores always-on VPN under. */
    private const val ALWAYS_ON_KEY = "always_on_vpn_app"
    private const val LOCKDOWN_KEY = "always_on_vpn_lockdown"

    suspend fun run(context: Context, store: ConfigStore): DoctorReport =
        withContext(Dispatchers.IO) {
            val findings = mutableListOf<DoctorFinding>()
            val app = context.applicationContext

            // 1. Android's own always-on VPN. Readable on most builds through
            //    Settings.Secure; when it is not, that is said rather than
            //    guessed at.
            val alwaysOn = runCatching {
                Settings.Secure.getString(app.contentResolver, ALWAYS_ON_KEY)
            }.getOrNull()
            findings += when {
                alwaysOn == null -> DoctorFinding(
                    "leak_always_on", DoctorVerdict.SKIPPED, "leak_unknown", "leak_fix_always_on"
                )
                alwaysOn == app.packageName -> DoctorFinding(
                    "leak_always_on", DoctorVerdict.PASS, "—"
                )
                alwaysOn.isBlank() -> DoctorFinding(
                    "leak_always_on", DoctorVerdict.FAIL, "—", "leak_fix_always_on"
                )
                // Always-on is set, but to a different VPN app.
                else -> DoctorFinding(
                    "leak_always_on", DoctorVerdict.WARN, "—", "leak_fix_always_on"
                )
            }

            // 2. Block connections without VPN - the half that actually stops
            //    the leak. Always-on alone only restarts the VPN.
            val lockdown = runCatching {
                Settings.Secure.getInt(app.contentResolver, LOCKDOWN_KEY, -1)
            }.getOrDefault(-1)
            findings += when (lockdown) {
                1 -> DoctorFinding("leak_lockdown", DoctorVerdict.PASS, "—")
                0 -> DoctorFinding("leak_lockdown", DoctorVerdict.FAIL, "—", "leak_fix_lockdown")
                else -> DoctorFinding(
                    "leak_lockdown", DoctorVerdict.SKIPPED, "leak_unknown", "leak_fix_lockdown"
                )
            }

            // 3. The app's own kill switch, which puts up a blocking tun when
            //    a session dies rather than letting traffic fall back.
            findings += if (store.killSwitch.value) {
                DoctorFinding("leak_kill", DoctorVerdict.PASS, "—")
            } else {
                DoctorFinding("leak_kill", DoctorVerdict.WARN, "—", "leak_fix_kill")
            }

            // 4. DNS. The tun publishes its own resolver, so queries go through
            //    the tunnel; encrypted DNS additionally hides them from the
            //    network the tunnel rides on.
            findings += if (store.encryptedDns.value) {
                DoctorFinding("leak_dns", DoctorVerdict.PASS, "—")
            } else {
                DoctorFinding("leak_dns", DoctorVerdict.WARN, "—", "leak_fix_dns")
            }

            // 5. IPv6 the tunnel does not carry. Measured from the interfaces
            //    themselves, not inferred from a setting.
            val v6 = globalIpv6Interfaces()
            val tunnelUp = VpnState.state.value == Connection.CONNECTED
            findings += when {
                v6.isEmpty() -> DoctorFinding("leak_ipv6", DoctorVerdict.PASS, "—")
                !tunnelUp -> DoctorFinding("leak_ipv6", DoctorVerdict.SKIPPED, v6.joinToString())
                tunnelCarriesIpv6() -> DoctorFinding("leak_ipv6", DoctorVerdict.PASS, v6.joinToString())
                else -> DoctorFinding(
                    "leak_ipv6", DoctorVerdict.FAIL, v6.joinToString(), "leak_fix_ipv6"
                )
            }

            DoctorReport(findings, cause(findings))
        }

    /**
     * Physical interfaces holding a globally routable IPv6 address.
     *
     * Link-local (fe80::) addresses are excluded: they never leave the link and
     * are not a leak. The tunnel's own interface is excluded by name, because
     * its IPv6 address is the tunnel carrying IPv6, not a leak around it.
     */
    private fun globalIpv6Interfaces(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !isTunnelInterfaceName(it.name) }
            .filter { iface ->
                iface.inetAddresses.asSequence().any {
                    it is Inet6Address && !it.isLinkLocalAddress && !it.isSiteLocalAddress
                }
            }
            .map { it.name }
            .toList()
    }.getOrDefault(emptyList())

    /** Whether the tunnel's own interface carries an IPv6 address. */
    private fun tunnelCarriesIpv6(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && isTunnelInterfaceName(it.name) }
            .flatMap { it.inetAddresses.asSequence() }
            .any { it is Inet6Address && !it.isLinkLocalAddress }
    }.getOrDefault(false)

    /** Split out so the name matching is testable without a network stack. */
    internal fun isTunnelInterfaceName(name: String): Boolean =
        name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("ipsec")

    private fun cause(findings: List<DoctorFinding>): String? =
        findings.firstOrNull { it.verdict == DoctorVerdict.FAIL }?.titleKey
            ?: findings.firstOrNull { it.verdict == DoctorVerdict.WARN }?.titleKey
}

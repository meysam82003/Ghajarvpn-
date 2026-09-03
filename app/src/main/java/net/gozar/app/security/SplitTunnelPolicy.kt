package net.gozar.app

/**
 * Pure policy used by the VPN process before touching [android.net.VpnService.Builder].
 * Keeping package resolution here makes the empty-allowlist and uninstalled-app
 * behaviour deterministic and unit-testable.
 */
object SplitTunnelPolicy {
    data class Plan(
        val allowedPackages: Set<String> = emptySet(),
        val disallowedPackages: Set<String> = emptySet(),
        val error: Error? = null
    )

    enum class Error { EMPTY_ALLOWLIST }

    fun resolve(
        mode: PerAppMode,
        selectedPackages: Set<String>,
        installedPackages: Set<String>,
        vpnPackage: String
    ): Plan {
        val installed = selectedPackages
            .asSequence()
            .filter { it != vpnPackage && it in installedPackages }
            .toSortedSet()

        return when (mode) {
            PerAppMode.OFF -> Plan(disallowedPackages = setOf(vpnPackage))
            PerAppMode.ALLOWLIST -> if (installed.isEmpty()) {
                Plan(error = Error.EMPTY_ALLOWLIST)
            } else {
                Plan(allowedPackages = installed)
            }
            PerAppMode.BLOCKLIST -> Plan(disallowedPackages = installed + vpnPackage)
        }
    }
}

/** IPv6 is always captured by the TUN; this controls proxying versus fail-closed blocking. */
enum class Ipv6Mode { BLOCK, TUNNEL }

object LeakProtectionPolicy {
    const val TUN_IPV6_ADDRESS = "fd00:4748:4a41::2"

    fun dnsUpstreams(encryptedDns: Boolean, strict: Boolean): List<String> = when {
        encryptedDns && strict -> listOf(
            "https://1.1.1.1/dns-query",
            "https://8.8.8.8/dns-query"
        )
        encryptedDns -> listOf(
            "https://1.1.1.1/dns-query",
            "https://8.8.8.8/dns-query",
            "1.1.1.1",
            "8.8.8.8"
        )
        else -> listOf("1.1.1.1", "8.8.8.8")
    }
}

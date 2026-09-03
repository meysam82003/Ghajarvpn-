package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitTunnelPolicyTest {
    private val self = "com.ghajarvpn.app"
    private val installed = setOf("app.alpha", "app.beta", self)

    @Test
    fun off_routes_device_but_excludes_vpn_process_to_prevent_loop() {
        val plan = SplitTunnelPolicy.resolve(PerAppMode.OFF, emptySet(), installed, self)

        assertEquals(setOf(self), plan.disallowedPackages)
        assertEquals(emptySet<String>(), plan.allowedPackages)
        assertNull(plan.error)
    }

    @Test
    fun allowlist_never_falls_back_to_whole_device_when_empty() {
        val plan = SplitTunnelPolicy.resolve(PerAppMode.ALLOWLIST, emptySet(), installed, self)

        assertEquals(SplitTunnelPolicy.Error.EMPTY_ALLOWLIST, plan.error)
    }

    @Test
    fun allowlist_ignores_uninstalled_and_self_packages() {
        val plan = SplitTunnelPolicy.resolve(
            PerAppMode.ALLOWLIST,
            setOf("app.alpha", "missing.app", self),
            installed,
            self
        )

        assertEquals(setOf("app.alpha"), plan.allowedPackages)
        assertNull(plan.error)
    }

    @Test
    fun blocklist_keeps_selected_apps_direct_and_always_excludes_self() {
        val plan = SplitTunnelPolicy.resolve(
            PerAppMode.BLOCKLIST,
            setOf("app.beta", "missing.app"),
            installed,
            self
        )

        assertEquals(setOf("app.beta", self), plan.disallowedPackages)
        assertNull(plan.error)
    }

    @Test
    fun strict_encrypted_dns_has_no_plain_fallback() {
        assertEquals(
            listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query"),
            LeakProtectionPolicy.dnsUpstreams(encryptedDns = true, strict = true)
        )
    }
}

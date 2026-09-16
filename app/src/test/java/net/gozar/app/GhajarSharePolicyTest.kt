package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Policy unit tests cannot prove end-to-end Android hotspot interception. */
class GhajarSharePolicyTest {
    private val up = GhajarShareNetwork(vpnConnected = true, relayReady = true, vpnRouteBound = true)
    private val down = GhajarShareNetwork(vpnConnected = false, relayReady = true, vpnRouteBound = false)

    @Test fun vpnOnlyNeverFallsBackOnVpnLoss() {
        val policy = GhajarSharePolicy(GhajarShareMode.VPN_ONLY, 0)
        assertEquals(GhajarShareRoute.VPN, policy.open("one", true, up, 1))
        assertEquals(GhajarShareRoute.DENY, policy.forward("one", 1, down.copy(directApproved = true), 2))
        assertEquals(0, policy.usedBytes("one"))
        assertEquals(GhajarShareRoute.DENY, policy.open("two", true, down.copy(directApproved = true), 3))
    }

    @Test fun hybridDirectFallbackRequiresExplicitConsentAndLiveRelay() {
        val policy = GhajarSharePolicy(GhajarShareMode.HYBRID, 0)
        assertEquals(GhajarShareRoute.DENY, policy.open("one", true, down, 1))
        assertEquals(GhajarShareRoute.DIRECT, policy.open("one", true, down.copy(directApproved = true), 2))
        assertEquals(GhajarShareRoute.DENY, policy.forward("one", 1, down.copy(directApproved = true, relayReady = false), 3))
        assertEquals(GhajarShareRoute.VPN, policy.forward("one", 1, up, 4))
    }

    @Test fun clientsOnlyNeedsProvedHostIsolation() {
        val policy = GhajarSharePolicy(GhajarShareMode.CLIENTS_ONLY, 0)
        assertEquals(GhajarShareRoute.DENY, policy.open("one", true, up, 1))
        assertEquals(GhajarShareRoute.VPN, policy.open("one", true, up.copy(hostIsolationVerified = true), 2))
        assertEquals(GhajarShareRoute.DENY, policy.forward("one", 1, up, 3))
    }

    @Test fun switchingModesRevokesDirectRatherThanKeepingStaleAccess() {
        val policy = GhajarSharePolicy(GhajarShareMode.HYBRID, 0)
        assertEquals(GhajarShareRoute.DIRECT, policy.open("one", true, down.copy(directApproved = true), 1))
        policy.changeMode(GhajarShareMode.VPN_ONLY)
        assertEquals(GhajarShareRoute.DENY, policy.forward("one", 1, down.copy(directApproved = true), 2))
        assertEquals(GhajarShareRoute.VPN, policy.forward("one", 1, up, 3))
    }

    @Test fun missingAuthenticationNeverCreatesSession() {
        val policy = GhajarSharePolicy(GhajarShareMode.VPN_ONLY, 0)
        assertEquals(GhajarShareRoute.DENY, policy.open("one", false, up, 1))
        assertEquals(GhajarShareRoute.DENY, policy.open("", true, up, 1))
        assertEquals(0, policy.activeClientCount())
    }

    @Test fun clientQuotaPersistsAcrossReconnectAndPreventsOverrun() {
        val policy = GhajarSharePolicy(GhajarShareMode.VPN_ONLY, 0)
        policy.setClientLimit("one", GhajarShareClientLimit(maxBytes = 10))
        assertEquals(GhajarShareRoute.VPN, policy.open("one", true, up, 1))
        assertEquals(GhajarShareRoute.VPN, policy.forward("one", 8, up, 2))
        policy.disconnect("one")
        assertEquals(GhajarShareRoute.VPN, policy.open("one", true, up, 3))
        assertEquals(GhajarShareRoute.DENY, policy.forward("one", 3, up, 4))
        assertEquals(GhajarShareRoute.VPN, policy.forward("one", 2, up, 5))
        assertEquals(GhajarShareRoute.DENY, policy.forward("one", 1, up, 6))
        assertEquals(10, policy.usedBytes("one"))
    }

    @Test fun globalQuotaIsSharedAndZeroQuotaBlocksAdmissions() {
        val policy = GhajarSharePolicy(GhajarShareMode.VPN_ONLY, 0, globalByteLimit = 5)
        assertEquals(GhajarShareRoute.VPN, policy.open("one", true, up, 1))
        assertEquals(GhajarShareRoute.VPN, policy.open("two", true, up, 1))
        assertEquals(GhajarShareRoute.VPN, policy.forward("one", 3, up, 2))
        assertEquals(GhajarShareRoute.DENY, policy.forward("two", 3, up, 2))
        assertEquals(GhajarShareRoute.VPN, policy.forward("two", 2, up, 2))
        assertEquals(GhajarShareRoute.DENY, policy.open("three", true, up, 3))
        assertEquals(5, policy.globalUsedBytes())
    }

    @Test fun clientAndSessionTimeLimitsCannotResetWithReconnect() {
        val policy = GhajarSharePolicy(GhajarShareMode.VPN_ONLY, startedAtMs = 0, globalTimeLimitMs = 20)
        policy.setClientLimit("one", GhajarShareClientLimit(maxDurationMs = 10))
        assertEquals(GhajarShareRoute.VPN, policy.open("one", true, up, 1))
        policy.disconnect("one")
        assertEquals(GhajarShareRoute.DENY, policy.open("one", true, up, 11))
        assertEquals(GhajarShareRoute.DENY, policy.open("two", true, up, 20))
    }

    @Test fun clientBlocksPausesAndCapacityApplyBeforeForwarding() {
        val policy = GhajarSharePolicy(GhajarShareMode.VPN_ONLY, 0, maxClients = 1)
        assertEquals(GhajarShareRoute.VPN, policy.open("one", true, up, 1))
        assertEquals(GhajarShareRoute.DENY, policy.open("two", true, up, 2))
        policy.setClientLimit("one", GhajarShareClientLimit(paused = true))
        assertEquals(GhajarShareRoute.DENY, policy.forward("one", 1, up, 3))
        policy.setClientLimit("one", GhajarShareClientLimit(blocked = true))
        assertEquals(0, policy.activeClientCount())
        assertEquals(GhajarShareRoute.DENY, policy.open("one", true, up, 4))
        policy.setAllowNewClients(false)
        assertEquals(GhajarShareRoute.DENY, policy.open("two", true, up, 5))
    }

    @Test fun stopIsTerminalAndIdempotent() {
        val policy = GhajarSharePolicy(GhajarShareMode.VPN_ONLY, 0)
        assertEquals(GhajarShareRoute.VPN, policy.open("one", true, up, 1))
        policy.stop()
        policy.stop()
        assertEquals(0, policy.activeClientCount())
        assertEquals(GhajarShareRoute.DENY, policy.forward("one", 1, up, 2))
        assertEquals(GhajarShareRoute.DENY, policy.open("two", true, up, 3))
    }

    @Test fun invalidLimitsAndBackwardClockFailSafe() {
        var rejected = false
        try { GhajarShareClientLimit(maxBytes = -1) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        val policy = GhajarSharePolicy(GhajarShareMode.VPN_ONLY, startedAtMs = 100)
        assertEquals(GhajarShareRoute.DENY, policy.open("one", true, up, 99))
        assertFalse(policy.globalUsedBytes() > 0)
    }
}

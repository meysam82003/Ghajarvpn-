package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhajarLinkFlowTest {
    @Test fun aPendingSessionIsNotAConnectionFailure() {
        assertEquals(GhajarLinkState.PENDING, GhajarLinkFlow.responseState("pending", null, null))
    }

    @Test fun onlyARealIssuedTokenCompletesLogin() {
        assertEquals(GhajarLinkState.LINKED, GhajarLinkFlow.responseState("linked", null, "issued-token"))
        assertEquals("issued-token", GhajarLinkFlow.bearer(" issued-token "))
    }

    @Test fun missingOrJsonNullTokensNeverCompleteLogin() {
        listOf(null, "", " ", "null", "NULL", "undefined").forEach { value ->
            assertNull(GhajarLinkFlow.bearer(value))
            assertEquals(GhajarLinkState.SERVER_ERROR, GhajarLinkFlow.responseState("linked", null, value))
        }
    }

    @Test fun headerInjectionIsNotAcceptedAsABearerToken() {
        listOf("token\r\nX-Injected: yes", "two tokens", "token\u0000tail").forEach {
            assertNull(GhajarLinkFlow.bearer(it))
        }
    }

    @Test fun channelMembershipHasItsOwnActionableState() {
        assertEquals(GhajarLinkState.FORCE_JOIN, GhajarLinkFlow.responseState("linked", "force_join", null))
        assertTrue(GhajarLinkFlow.message(GhajarLinkState.FORCE_JOIN).contains("عضویت"))
    }

    @Test fun phoneVerificationIsCompletedInTheBot() {
        assertEquals(GhajarLinkState.PHONE_REQUIRED, GhajarLinkFlow.responseState("linked", "phone_required", null))
        assertTrue(GhajarLinkFlow.message(GhajarLinkState.PHONE_REQUIRED).contains("داخل ربات"))
    }

    @Test fun aGateCannotBeBypassedEvenIfATokenIsAlsoPresent() {
        assertEquals(GhajarLinkState.FORCE_JOIN,
            GhajarLinkFlow.responseState("linked", "force_join", "unexpected-token"))
        assertEquals(GhajarLinkState.SERVER_ERROR,
            GhajarLinkFlow.responseState("linked", "unknown_gate", "unexpected-token"))
    }

    @Test fun continuingVerificationDoesNotReuseAnAlreadyClaimedCode() {
        assertEquals(listOf("tg://resolve?domain=Ghajar_vpnbot&start=start", "https://t.me/Ghajar_vpnbot?start=start"),
            GhajarUiRules.botVerificationUrls("@Ghajar_vpnbot"))
        assertTrue(GhajarUiRules.botVerificationUrls("not/a/bot").all { !it.contains("link_") })
    }

    @Test fun expiredAndMissingSessionsDoNotWaitUntilTheLocalTimerEnds() {
        assertEquals(GhajarLinkState.EXPIRED, GhajarLinkFlow.responseState("expired", null, null))
        assertEquals(GhajarLinkState.NOT_FOUND, GhajarLinkFlow.responseState("not_found", null, null))
    }

    @Test fun malformedServerStatesAreNotReportedAsUserInaction() {
        listOf(null, "", "error", "other").forEach {
            assertEquals(GhajarLinkState.SERVER_ERROR, GhajarLinkFlow.responseState(it, null, "token"))
        }
    }

    @Test fun aStrayTokenCannotPromoteAPendingOrExpiredSession() {
        assertEquals(GhajarLinkState.PENDING, GhajarLinkFlow.responseState("pending", null, "token"))
        assertEquals(GhajarLinkState.EXPIRED, GhajarLinkFlow.responseState("expired", null, "token"))
    }

    @Test fun countdownUsesOriginalExpiryWithNoNegativeSeconds() {
        assertEquals(300, GhajarLinkFlow.remainingSeconds(301_000, 1_000))
        assertEquals(1, GhajarLinkFlow.remainingSeconds(301_000, 300_999))
        assertEquals(0, GhajarLinkFlow.remainingSeconds(301_000, 301_000))
        assertEquals(0, GhajarLinkFlow.remainingSeconds(301_000, 400_000))
    }

    @Test fun aClockRollbackCannotDisplayAnUnboundedCountdown() {
        assertEquals(900, GhajarLinkFlow.remainingSeconds(2_000_000, 1_000))
    }

    @Test fun repeatedNetworkFailuresBackOffButNormalPollingStaysFast() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L),
            (0..4).map(GhajarLinkFlow::retryDelayMillis))
        assertEquals(30_000L, GhajarLinkFlow.retryDelayMillis(Int.MAX_VALUE))
        assertEquals(2_000L, GhajarLinkFlow.retryDelayMillis(-1))
    }

    @Test fun unauthenticatedLoginErrorsCannotEraseAnAccount() {
        assertFalse(GhajarLinkFlow.invalidatesAccount(false, 401))
        assertFalse(GhajarLinkFlow.invalidatesAccount(false, 403))
        assertTrue(GhajarLinkFlow.invalidatesAccount(true, 401))
        assertTrue(GhajarLinkFlow.invalidatesAccount(true, 403))
        assertFalse(GhajarLinkFlow.invalidatesAccount(true, 500))
    }

    @Test fun transientFailuresAndPendingHaveDifferentExplanations() {
        val states = listOf(GhajarLinkState.PENDING, GhajarLinkState.NETWORK_ERROR,
            GhajarLinkState.SERVER_ERROR, GhajarLinkState.STORAGE_ERROR)
        assertEquals(states.size, states.map(GhajarLinkFlow::message).distinct().size)
        assertTrue(states.all { GhajarLinkFlow.message(it).isNotBlank() })
    }
}

package net.gozar.app

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class GhajarCommerceRulesTest {
    @Test fun cancellationEscapesResultWrapper() {
        val cancellation = CancellationException("The coroutine scope left the composition")
        try {
            storeResult<Unit> { throw cancellation }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) { assertSame(cancellation, actual) }
    }
    @Test fun operationalErrorsRemainFailures() {
        assertTrue(storeResult<Unit> { throw IllegalStateException("network") }.isFailure)
    }
    @Test fun onlyPaidConfirmsPayment() {
        assertTrue(GhajarCommerceRules.paid(" PAID "))
        listOf("unpaid", "waiting", "pending", "success", "1", "").forEach { assertFalse(GhajarCommerceRules.paid(it)) }
    }
    @Test fun terminalStatusesDoNotIncludePending() {
        listOf("expire", "EXPIRED", "reject", "cancelled", "CANCELED").forEach { assertTrue(GhajarCommerceRules.terminal(it)) }
        assertFalse(GhajarCommerceRules.terminal("waiting"))
    }
    @Test fun cardVariantsExposeReceiptControls() {
        listOf("carttocart", "carttocart_pv", "card_to_card").forEach { assertTrue(GhajarCommerceRules.cardPayment(it, null)) }
        assertTrue(GhajarCommerceRules.cardPayment("manual", "1234"))
        assertFalse(GhajarCommerceRules.cardPayment("url", null))
    }
    @Test fun returningPosterCyclesWithoutImmediateRepetition() {
        assertEquals(0, GhajarCommerceRules.nextPoster(-1, 4))
        for (last in 0..3) assertNotEquals(last, GhajarCommerceRules.nextPoster(last, 4))
        assertEquals(0, GhajarCommerceRules.nextPoster(3, 4))
        assertEquals(0, GhajarCommerceRules.nextPoster(0, 1))
    }
    @Test fun internalEnglishErrorIsNotDisplayed() {
        assertFalse(GhajarCommerceRules.publicMessage("The coroutine scope left the composition").contains("coroutine"))
    }
    private val trusted = setOf("blupal.net", "shaparak.ir")
    @Test fun realBluPalLiveAndSandboxLinksAreAllowed() {
        assertTrue(GhajarPaymentPolicy.allows("https://blupal.net/payment/opaque-token", "httpuser87890.ir", trusted))
        assertTrue(GhajarPaymentPolicy.allows("https://blupal.net/sandbox/payment/token", null, trusted))
    }
    @Test fun lookalikeAndCredentialHostsAreRejected() {
        listOf("https://blupal.net.evil.example/p", "https://blupal.net@evil.example/p",
            "http://blupal.net/p", "https://blupal.net:444/p", "file:///tmp/receipt").forEach {
            assertFalse(it, GhajarPaymentPolicy.allows(it, null, trusted))
        }
    }
    @Test fun onlyExactApiIssuedHostGetsTemporaryTrust() {
        assertTrue(GhajarPaymentPolicy.allows("https://checkout.example/p", "checkout.example", trusted))
        assertFalse(GhajarPaymentPolicy.allows("https://other.checkout.example/p", "checkout.example", trusted))
    }

    @Test fun pendingReceiptDoesNotCreditWalletOrDeliverService() {
        listOf("waiting", "pending", "unpaid").forEach {
            assertEquals(GhajarPaymentOutcome.PENDING, GhajarCommerceRules.paymentOutcome(it, true, true, true, true))
        }
    }
    @Test fun paidStandaloneTopUpOnlyCreditsWallet() {
        assertEquals(GhajarPaymentOutcome.WALLET_CREDITED, GhajarCommerceRules.paymentOutcome("paid", true, false, false, false))
    }
    @Test fun adminWalletOnlyModeOverridesAutomaticServiceDelivery() {
        assertEquals(GhajarPaymentOutcome.WALLET_CREDITED, GhajarCommerceRules.paymentOutcome("paid", false, true, true, true))
    }
    @Test fun deliveryRequiresPaidReadyAndActualServiceTogether() {
        assertEquals(GhajarPaymentOutcome.PAID_WAITING, GhajarCommerceRules.paymentOutcome("paid", false, false, false, true))
        assertEquals(GhajarPaymentOutcome.PAID_WAITING, GhajarCommerceRules.paymentOutcome("paid", false, false, true, false))
        assertEquals(GhajarPaymentOutcome.SERVICE_READY, GhajarCommerceRules.paymentOutcome("paid", false, false, true, true))
    }
    @Test fun rejectedInvoiceCannotBeClassifiedAsSuccess() {
        assertEquals(GhajarPaymentOutcome.NOT_APPROVED, GhajarCommerceRules.paymentOutcome("rejected", true, true, true, true))
    }
}

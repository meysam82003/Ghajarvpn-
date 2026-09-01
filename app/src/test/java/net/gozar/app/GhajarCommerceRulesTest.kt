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

    private val random = kotlin.random.Random(42)

    @Test fun singlePosterIsRandomAndNeverRepeatsThePreviousLaunch() {
        for (count in 2..33) for (last in -1 until count) {
            assertNotEquals("count=$count last=$last", last, GhajarCommerceRules.randomPoster(last, count, random))
        }
    }
    @Test fun singlePosterAlwaysStaysInsideThePosterSet() {
        for (count in 1..33) for (last in -1 until count) {
            val chosen = GhajarCommerceRules.randomPoster(last, count, random)
            assertTrue(chosen in 0 until count)
        }
        assertEquals(0, GhajarCommerceRules.randomPoster(0, 1, random))
    }
    @Test fun welcomeTipsAreNonBlankPersianFeatureNotes() {
        assertTrue(GhajarCommerceRules.welcomeTips.size >= 12)
        GhajarCommerceRules.welcomeTips.forEach { tip ->
            assertTrue(tip.isNotBlank())
            assertTrue(tip.any { it in '\u0600'..'\u06ff' })
        }
    }
    @Test fun welcomeTipRotatesWithoutImmediateRepetition() {
        val tips = GhajarCommerceRules.welcomeTips
        for (last in tips.indices) {
            val next = GhajarCommerceRules.randomWelcomeTip(last, random)
            assertNotEquals(tips[last], next)
            assertTrue(next in tips)
        }
        assertEquals(0, GhajarCommerceRules.welcomeTipIndex(tips.first()))
        assertEquals(0, GhajarCommerceRules.welcomeTipIndex("نامعلوم"))
    }

    @Test fun paidOrdersEnterTheStageMachineAndWalletTopUpsNeverDo() {
        assertEquals(GhajarOrderStage.PAYMENT_CONFIRMED, GhajarOrderFlow.initialStage(true, false))
        assertNull(GhajarOrderFlow.initialStage(true, true))
        assertNull(GhajarOrderFlow.initialStage(false, false))
    }
    @Test fun provisioningFailsIntoARefundableStageAndDeliveryClosesIt() {
        var stage = GhajarOrderStage.PAYMENT_CONFIRMED
        stage = GhajarOrderFlow.onDeliveryAttempt(stage)
        assertEquals(GhajarOrderStage.PROVISIONING, stage)
        stage = GhajarOrderFlow.onDeliveryFailed(stage)
        assertEquals(GhajarOrderStage.PROVISION_FAILED, stage)
        assertTrue(GhajarOrderFlow.walletFallbackAllowed(stage))
        assertEquals(GhajarOrderStage.WALLET_REFUNDED, GhajarOrderFlow.onWalletRefunded(stage))
        assertFalse(GhajarOrderFlow.walletFallbackAllowed(GhajarOrderStage.WALLET_REFUNDED))
        assertNull(GhajarOrderFlow.onWalletRefunded(GhajarOrderStage.WALLET_REFUNDED))
    }
    @Test fun deliveredServiceCanNeverBeRefundedToTheWallet() {
        var stage = GhajarOrderStage.PAYMENT_CONFIRMED
        stage = GhajarOrderFlow.onDeliveryAttempt(stage)
        stage = GhajarOrderFlow.onDelivered(stage, 3)
        assertEquals(GhajarOrderStage.DELIVERED, stage)
        assertFalse(GhajarOrderFlow.walletFallbackAllowed(stage))
        assertNull(GhajarOrderFlow.onWalletRefunded(stage))
        assertEquals(GhajarOrderStage.DELIVERED, GhajarOrderFlow.onDeliveryFailed(stage))
    }
    @Test fun walletRefundIsIdempotentAcrossRepeatedAttempts() {
        val first = GhajarOrderFlow.onWalletRefunded(GhajarOrderStage.PROVISION_FAILED)
        assertEquals(GhajarOrderStage.WALLET_REFUNDED, first)
        // Re-crediting an already refunded invoice is a no-op (null), never double credit.
        assertNull(GhajarOrderFlow.onWalletRefunded(first!!))
    }
    @Test fun pendingOrRejectedCardToCardReceiptNeverQualifiesForWalletCredit() {
        listOf("waiting", "pending", "unpaid").forEach { status ->
            assertFalse(status, GhajarOrderFlow.refundEligible(status, false, false, false))
        }
        listOf("rejected", "expired", "canceled").forEach { status ->
            assertFalse(status, GhajarOrderFlow.refundEligible(status, false, false, false))
        }
        assertFalse(GhajarOrderFlow.refundEligible("paid", false, true, true))
        assertTrue(GhajarOrderFlow.refundEligible("paid", false, false, false))
        assertTrue(GhajarOrderFlow.refundEligible("paid", false, true, false))
        assertFalse(GhajarOrderFlow.refundEligible("paid", true, false, false))
    }
    @Test fun stageMachineSurvivesStorageRoundTrip() {
        GhajarOrderStage.entries.forEach { stage ->
            assertEquals(stage, GhajarOrderFlow.fromStorage(stage.name))
        }
        assertNull(GhajarOrderFlow.fromStorage(null))
        assertNull(GhajarOrderFlow.fromStorage("garbage"))
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

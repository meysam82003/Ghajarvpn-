package net.gozar.app

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GhajarRenewalTest {
    private fun options(price: Long = 170000, token: String? = "fresh") = GhajarRenewOptions(
        "user", "panel", listOf(GhajarRenewProduct("p20", "20 GB", 20, 30, price, true, "")),
        "p20", true, 0, 310000,
        GhajarRenewCustomOptions(true, false, 8500, 0, 1, 500, 30, 30), token, "custom-token"
    )
    private val selection = GhajarRenewSelection(productCode = "p20")
    private val done = GhajarRenewResult(true, false, "user", 0, 140000, 170000, null)

    @Test fun syncedPriceUsesFreshTokenWithoutWarning() = runBlocking {
        val displayed = options(token = "old").quote(selection)
        var calls = 0
        val result = checkedRenewal(displayed, { options() }) {
            calls++; assertEquals("fresh", it.fxQuote); done
        }
        assertSame(done, result.result); assertEquals(1, calls)
    }
    @Test fun changedAmountRequiresExplicitConfirmationWithoutCharging() = runBlocking {
        var calls = 0
        val result = checkedRenewal(options().quote(selection), { options(180000) }) { calls++; done }
        assertNull(result.result); assertEquals(180000L, result.quote.price); assertEquals(0, calls)
        assertSame(done, checkedRenewal(result.quote, { options(180000) }) { calls++; done }.result)
        assertEquals(1, calls)
    }
    @Test fun priceReductionAlsoRequiresConfirmation() = runBlocking {
        assertNull(checkedRenewal(options().quote(selection), { options(160000) }) { fail(); done }.result)
    }
    @Test fun tokenRejectionWithSameAmountRetriesExactlyOnce() = runBlocking {
        var calls = 0
        val result = checkedRenewal(options().quote(selection), { options() }) {
            if (++calls == 1) throw GhajarRenewQuoteRejected(170000, "rotated")
            assertEquals("rotated", it.fxQuote); done
        }
        assertSame(done, result.result); assertEquals(2, calls)
    }
    @Test fun rateChangesAfterRefreshNeverAutoAcceptNewAmount() = runBlocking {
        var calls = 0
        val result = checkedRenewal(options().quote(selection), { options() }) {
            calls++; throw GhajarRenewQuoteRejected(185000, "next")
        }
        assertNull(result.result); assertEquals(185000L, result.quote.price); assertEquals(1, calls)
    }
    @Test fun repeatedRejectionIsBoundedAndDoesNotClaimPriceChanged() = runBlocking {
        var calls = 0
        try {
            checkedRenewal(options().quote(selection), { options() }) {
                calls++; throw GhajarRenewQuoteRejected(170000, "next")
            }; fail()
        } catch (e: IllegalStateException) { assertFalse(e.message.orEmpty().contains("تغییر")) }
        assertEquals(2, calls)
    }
    @Test fun networkFailureDoesNotRepeatConfirmation() = runBlocking {
        var calls = 0
        try {
            checkedRenewal(options().quote(selection), { options() }) { calls++; throw IOException("timeout") }
            fail()
        } catch (_: IOException) { }
        assertEquals(1, calls)
    }
    @Test fun failedRefreshDoesNotCharge() = runBlocking {
        var calls = 0
        try {
            checkedRenewal(options().quote(selection), { throw IOException("offline") }) { calls++; done }
            fail()
        } catch (_: IOException) { }
        assertEquals(0, calls)
    }
    @Test fun cancellationPropagates() = runBlocking {
        val cancelled = CancellationException("closed")
        try {
            checkedRenewal(options().quote(selection), { options() }) { throw cancelled }; fail()
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }
    @Test fun missingProductCannotBeCharged() = runBlocking {
        try {
            checkedRenewal(options().quote(selection), { options().copy(products = emptyList()) }) { fail(); done }
            fail()
        } catch (_: IllegalStateException) { }
    }
    @Test fun insufficientWalletKeepsPaymentResultAndBalance() = runBlocking {
        val shortfall = done.copy(completed = false, requiresPayment = true, amountDue = 70000, balance = 100000)
        val result = checkedRenewal(options().quote(selection), { options().copy(balance = 100000) }) { shortfall }
        assertSame(shortfall, result.result); assertEquals(100000L, result.options.balance)
    }
    @Test fun exactMoneyParsesEquivalentRepresentationsAndLargeIntegers() {
        listOf("170000", "170000.0", "1.7E+5").forEach { assertEquals(170000L, renewalToman(it)) }
        assertEquals(9007199254740993L, renewalToman("9007199254740993"))
        assertEquals(310000L, renewalBalance("310000.99"))
        for (value in listOf("0.1", "NaN", "-1", "9223372036854775808")) {
            assertTrue(runCatching { renewalToman(value) }.isFailure)
        }
    }
    @Test fun customUsesSeparateQuoteAndFixedDays() {
        val quote = options().quote(GhajarRenewSelection(volumeGb = 20, timeDays = 30))
        assertEquals(170000L, quote.price); assertEquals("custom-token", quote.fxQuote)
        assertEquals(30, quote.selection.timeDays)
    }
    @Test fun customRoundingAndDiscountUseExactArithmetic() {
        val original = options()
        val custom = original.copy(discountPercent = 10,
            custom = original.custom.copy(pricePerGb = 8001, pricePerDay = 101, fxRoundStep = 1000))
        assertEquals(148000L, custom.quote(GhajarRenewSelection(volumeGb = 20, timeDays = 30)).price)
    }
    @Test fun customOutOfRangeOrUnavailableCannotBeCharged() {
        val opt = options()
        for (choice in listOf(GhajarRenewSelection(volumeGb = 0, timeDays = 30),
            GhajarRenewSelection(volumeGb = 20, timeDays = 0))) assertTrue(runCatching { opt.quote(choice) }.isFailure)
        assertTrue(runCatching { opt.copy(custom = opt.custom.copy(enabled = false))
            .quote(GhajarRenewSelection(volumeGb = 20, timeDays = 30)) }.isFailure)
    }
    @Test fun custom409ComparesDiscountedAmountInsteadOfBasePrice() = runBlocking {
        val opt = options().copy(discountPercent = 10)
        val quote = opt.quote(GhajarRenewSelection(volumeGb = 20, timeDays = 30))
        var calls = 0
        val result = checkedRenewal(quote, { opt }) {
            if (++calls == 1) throw GhajarRenewQuoteRejected(170000, "custom-fresh")
            assertEquals(153000L, it.price); done
        }
        assertSame(done, result.result); assertEquals(2, calls)
    }
    @Test fun fxDisabledAndHiddenPricesKeepTheirSemantics() {
        val quote = options(token = null).copy(showPrice = false).quote(selection)
        assertNull(quote.fxQuote); assertFalse(quote.showPrice)
    }
}

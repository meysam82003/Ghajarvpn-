package net.gozar.app

import java.math.BigDecimal
import java.math.RoundingMode

data class GhajarRenewProduct(
    val code: String,
    val name: String,
    val volumeGb: Int,
    val timeDays: Int,
    val price: Long,
    val showPrice: Boolean,
    val note: String,
    val isCurrentPlan: Boolean = false
)

data class GhajarRenewCustomOptions(
    val enabled: Boolean,
    val forced: Boolean,
    val pricePerGb: Long,
    val pricePerDay: Long,
    val minVolumeGb: Int,
    val maxVolumeGb: Int,
    val minTimeDays: Int,
    val maxTimeDays: Int,
    val fxRoundStep: Long = 0
)

data class GhajarRenewOptions(
    val username: String,
    val panelName: String,
    val products: List<GhajarRenewProduct>,
    val currentPlanCode: String?,
    val showPrice: Boolean,
    val discountPercent: Int,
    val balance: Long,
    val custom: GhajarRenewCustomOptions,
    val productFxQuote: String? = null,
    val customFxQuote: String? = null
)

data class GhajarRenewResult(
    val completed: Boolean,
    val requiresPayment: Boolean,
    val username: String,
    val amountDue: Long,
    val balance: Long,
    val price: Long,
    val orderId: String?
)

/** The API contract is whole toman (not rial or USDT). Never compare binary floats. */
internal fun renewalToman(value: String): Long = BigDecimal(value).longValueExact().also {
    require(it >= 0) { "مبلغ تمدید معتبر نیست" }
}

internal fun renewalBalance(value: String?): Long =
    value?.takeUnless { it == "null" }?.let { BigDecimal(it).setScale(0, RoundingMode.FLOOR).longValueExact() } ?: 0

internal data class GhajarRenewSelection(
    val productCode: String? = null,
    val volumeGb: Int? = null,
    val timeDays: Int? = null
)

internal data class GhajarRenewQuote(
    val selection: GhajarRenewSelection,
    val price: Long,
    val fxQuote: String?,
    val showPrice: Boolean
)

/** Only thrown for the server's explicit pre-charge 409/price_changed response. */
internal class GhajarRenewQuoteRejected(val price: Long, val fxQuote: String?) :
    IllegalStateException("قیمت تمدید نیاز به بررسی دوباره دارد")

internal fun renewalDiscountedPrice(price: Long, discount: Int, step: Long): Long {
    val discounted = BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(100L - discount))
        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).max(BigDecimal.ZERO)
    return renewalRound(discounted, step)
}

private fun renewalRound(value: BigDecimal, step: Long): Long =
    (if (step > 0) value.divide(BigDecimal.valueOf(step), 0, RoundingMode.CEILING)
        .multiply(BigDecimal.valueOf(step)) else value).longValueExact()

internal fun GhajarRenewOptions.quote(selection: GhajarRenewSelection): GhajarRenewQuote {
    if (selection.productCode != null) {
        require(!custom.forced) { "برای این سرویس حجم/زمان دلخواه انتخاب کنید" }
        val product = products.firstOrNull { it.code == selection.productCode }
            ?: error("پلن انتخاب‌شده دیگر موجود نیست؛ پلن دیگری انتخاب کنید")
        return GhajarRenewQuote(selection, product.price, productFxQuote, showPrice && product.showPrice)
    }
    require(custom.enabled) { "تمدید دلخواه برای این سرویس فعال نیست" }
    val volume = requireNotNull(selection.volumeGb)
    val days = requireNotNull(selection.timeDays)
    require(volume in custom.minVolumeGb..custom.maxVolumeGb) { "حجم انتخاب‌شده خارج از محدوده مجاز است" }
    require(days in custom.minTimeDays..custom.maxTimeDays) { "زمان انتخاب‌شده خارج از محدوده مجاز است" }
    val subtotal = BigDecimal.valueOf(custom.pricePerGb).multiply(BigDecimal.valueOf(volume.toLong()))
        .add(BigDecimal.valueOf(custom.pricePerDay).multiply(BigDecimal.valueOf(days.toLong())))
    val price = renewalDiscountedPrice(renewalRound(subtotal, custom.fxRoundStep), discountPercent, custom.fxRoundStep)
    return GhajarRenewQuote(selection, price, customFxQuote, showPrice)
}

internal data class GhajarRenewAttempt(
    val options: GhajarRenewOptions,
    val quote: GhajarRenewQuote,
    // null means a different amount needs the user's explicit confirmation.
    val result: GhajarRenewResult? = null
)

/** Revalidate the user's immutable selection, then send that fresh quote to the server. */
internal suspend fun checkedRenewal(
    displayed: GhajarRenewQuote,
    latest: suspend () -> GhajarRenewOptions,
    confirm: suspend (GhajarRenewQuote) -> GhajarRenewResult
): GhajarRenewAttempt {
    val options = latest()
    var quote = options.quote(displayed.selection)
    // A dollar-priced panel moves its toman price with every rate update;
    // a drift of up to one percent goes through without a second question.
    fun changed(q: GhajarRenewQuote) = kotlin.math.abs(q.price - displayed.price) > maxOf(1L, displayed.price / 100)
    if (changed(quote)) return GhajarRenewAttempt(options, quote)
    repeat(2) { attempt ->
        try {
            return GhajarRenewAttempt(options, quote, confirm(quote))
        } catch (rejected: GhajarRenewQuoteRejected) {
            // A rotated/expired token alone is not a price change. The backend rejects
            // this response before charging, so one retry with the returned token is safe.
            val price = if (quote.selection.productCode == null)
                renewalDiscountedPrice(rejected.price, options.discountPercent, options.custom.fxRoundStep)
            else rejected.price
            quote = quote.copy(price = price, fxQuote = rejected.fxQuote)
            if (changed(quote)) return GhajarRenewAttempt(options, quote)
            if (attempt == 1 || rejected.fxQuote == null)
                error("دریافت تأیید معتبر قیمت ممکن نشد؛ دوباره تلاش کنید")
        }
    }
    error("تأیید تمدید انجام نشد")
}

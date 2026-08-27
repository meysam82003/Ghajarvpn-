package net.gozar.app

import kotlinx.coroutines.CancellationException

/** Navigation cancellation must never become a red purchase error. */
internal inline fun <T> storeResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

internal object GhajarCommerceRules {
    fun paid(value: String) = value.trim().equals("paid", ignoreCase = true)
    fun terminal(value: String) = value.trim().lowercase(java.util.Locale.ROOT) in
        setOf("reject", "rejected", "expire", "expired", "canceled", "cancelled")
    fun cardPayment(kind: String, card: String?) =
        kind.lowercase(java.util.Locale.ROOT) in setOf("carttocart", "carttocart_pv", "card", "card_to_card") || !card.isNullOrBlank()
    fun nextPoster(last: Int, count: Int): Int = if (count <= 1) 0 else (last + 1).mod(count)
    fun publicMessage(message: String): String =
        if (message.any { it in '\u0600'..'\u06ff' }) BrandConfig.sanitizePublicText(message).take(500)
        else "عملیات کامل نشد؛ اتصال اینترنت و وضعیت سفارش را بررسی کن."
}

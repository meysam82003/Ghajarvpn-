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

internal enum class GhajarPaymentOutcome { WALLET_CREDITED, SERVICE_READY, PAID_WAITING, NOT_APPROVED, PENDING }

/** Lifecycle of one paid order so delivery and wallet fallback can never double-fire. */
internal enum class GhajarOrderStage { PAYMENT_CONFIRMED, PROVISIONING, DELIVERED, PROVISION_FAILED, WALLET_REFUNDED }

internal object GhajarOrderFlow {
    fun fromStorage(value: String?): GhajarOrderStage? =
        value?.let { stored -> runCatching { GhajarOrderStage.valueOf(stored) }.getOrNull() }

    /** Only a real confirmed payment for a service enters the stage machine; wallet top-ups never do. */
    fun initialStage(paid: Boolean, walletTopUp: Boolean): GhajarOrderStage? =
        if (paid && !walletTopUp) GhajarOrderStage.PAYMENT_CONFIRMED else null

    fun onDeliveryAttempt(stage: GhajarOrderStage): GhajarOrderStage = when (stage) {
        GhajarOrderStage.PAYMENT_CONFIRMED, GhajarOrderStage.PROVISION_FAILED -> GhajarOrderStage.PROVISIONING
        else -> stage
    }

    fun onDelivered(stage: GhajarOrderStage, imported: Int): GhajarOrderStage =
        if (imported > 0 && stage in setOf(GhajarOrderStage.PROVISIONING, GhajarOrderStage.PROVISION_FAILED, GhajarOrderStage.PAYMENT_CONFIRMED))
            GhajarOrderStage.DELIVERED else stage

    fun onDeliveryFailed(stage: GhajarOrderStage): GhajarOrderStage = when (stage) {
        GhajarOrderStage.PAYMENT_CONFIRMED, GhajarOrderStage.PROVISIONING, GhajarOrderStage.PROVISION_FAILED ->
            GhajarOrderStage.PROVISION_FAILED
        else -> stage
    }

    /** Wallet fallback is only allowed while the paid service has not been delivered. */
    fun walletFallbackAllowed(stage: GhajarOrderStage): Boolean =
        stage in setOf(GhajarOrderStage.PAYMENT_CONFIRMED, GhajarOrderStage.PROVISIONING, GhajarOrderStage.PROVISION_FAILED)

    /** Idempotent final transition; a delivered or already-refunded invoice can never be refunded again. */
    fun onWalletRefunded(stage: GhajarOrderStage): GhajarOrderStage? =
        if (walletFallbackAllowed(stage)) GhajarOrderStage.WALLET_REFUNDED else null

    /**
     * A wallet credit for an undelivered paid order is only legitimate after the
     * server confirmed the payment. Pending or rejected card-to-card receipts are
     * unconfirmed money and must never trigger a credit.
     */
    fun refundEligible(status: String, walletTopUp: Boolean, serviceReady: Boolean, hasService: Boolean): Boolean {
        if (walletTopUp) return false
        return GhajarCommerceRules.paymentOutcome(status, walletTopUp, false, serviceReady, hasService) ==
            GhajarPaymentOutcome.PAID_WAITING
    }
}

internal object GhajarCommerceRules {
    fun paymentOutcome(status: String, walletTopUp: Boolean, walletOnly: Boolean,
        serviceReady: Boolean, hasService: Boolean): GhajarPaymentOutcome = when {
        paid(status) && (walletTopUp || walletOnly) -> GhajarPaymentOutcome.WALLET_CREDITED
        paid(status) && serviceReady && hasService -> GhajarPaymentOutcome.SERVICE_READY
        paid(status) -> GhajarPaymentOutcome.PAID_WAITING
        terminal(status) -> GhajarPaymentOutcome.NOT_APPROVED
        else -> GhajarPaymentOutcome.PENDING
    }

    fun paid(value: String) = value.trim().equals("paid", ignoreCase = true)
    fun terminal(value: String) = value.trim().lowercase(java.util.Locale.ROOT) in
        setOf("reject", "rejected", "expire", "expired", "canceled", "cancelled")
    fun cardPayment(kind: String, card: String?) =
        kind.lowercase(java.util.Locale.ROOT) in setOf("carttocart", "carttocart_pv", "card", "card_to_card") || !card.isNullOrBlank()
    fun nextPoster(last: Int, count: Int): Int = if (count <= 1) 0 else (last + 1).mod(count)

    /**
     * Random poster index for the single-poster welcome; the previous poster is
     * excluded so one launch never repeats the exact image of the last launch.
     */
    fun randomPoster(last: Int, count: Int, random: kotlin.random.Random): Int = when {
        count <= 1 -> 0
        count == 2 -> (last + 1).mod(count)
        else -> {
            val candidates = (0 until count).filter { it != last }
            candidates[random.nextInt(candidates.size)]
        }
    }

    /** Short Persian welcome tips about features that really exist in the app. */
    val welcomeTips: List<String> = listOf(
        "از فروشگاه قاجار می‌توانی سرویس بخری و مستقیم داخل برنامه تحویل بگیری.",
        "ساب‌ها هنگام ورود به برنامه به‌صورت خودکار به‌روزرسانی می‌شوند.",
        "با «تست همه»، سریع‌ترین سرور را در چند ثانیه پیدا کن.",
        "سرورها به‌طور پیش‌فرض از سریع‌ترین به کندترین مرتب می‌شوند.",
        "دکمهٔ «آپدیت ساب‌ها» سرویس‌های فعال را همان لحظه تازه می‌کند.",
        "فایل‌های OpenVPN را داخل قاجار ایمپورت کن و مستقیم وصل شو.",
        "کیف پول قاجار خرید سرویس را با موجودی داخلی ساده می‌کند.",
        "پرداخت با کارت‌به‌کارت، درگاه امن و BluPal داخل خود برنامه انجام می‌شود.",
        "اگر پرداخت موفق شد ولی سرویس تحویل نشد، مبلغ به کیف پول برمی‌گردد.",
        "فاکتور نیمه‌کاره روی گوشی ذخیره می‌شود و می‌توانی پرداخت را ادامه بدهی.",
        "با تست کیفیت اتصال، پینگ هر سرور را قبل از وصل‌شدن ببین.",
        "روی نقشهٔ کرهٔ زمین، کشور آی‌پی فعلی‌ات را ببین.",
        "ویجت قاجار اتصال و قطع را از صفحهٔ اصلی گوشی ممکن می‌کند.",
        "اعلان وضعیت اتصال، پینگ و دکمهٔ قطع را همیشه همراه تو نگه می‌دارد.",
        "سرویس تمام‌شده یا کم‌مانده را پیام‌های قاجار به تو هشدار می‌دهد.",
        "فایل‌های سازگار با V2ray و لینک‌های Happ را یک‌جا ایمپورت کن.",
        "در حالت Auto، قاجار خودش سریع‌ترین سرور سالم را انتخاب می‌کند.",
        "حجم مصرفی سرعت لحظه‌ای را از صفحهٔ خانه ببین."
    )

    /** Pick a welcome tip that differs from the previous one when possible. */
    fun randomWelcomeTip(last: Int, random: kotlin.random.Random): String = when {
        welcomeTips.size <= 1 -> welcomeTips.firstOrNull().orEmpty()
        else -> {
            val candidates = welcomeTips.indices.filter { it != last }
            val index = candidates[random.nextInt(candidates.size)]
            welcomeTips[index]
        }
    }

    fun welcomeTipIndex(tip: String): Int = welcomeTips.indexOf(tip).coerceAtLeast(0)
    fun publicMessage(message: String): String =
        if (message.any { it in '\u0600'..'\u06ff' }) BrandConfig.sanitizePublicText(message).take(500)
        else "عملیات کامل نشد؛ اتصال اینترنت و وضعیت سفارش را بررسی کن."
}

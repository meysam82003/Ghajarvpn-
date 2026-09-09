package net.gozar.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

internal data class GhajarDelivery(val service: GhajarServiceDetails, val imported: Int, val synced: Boolean)

/** Checkout belongs to the Activity, not to a disposable pager composition. */
class GhajarCheckoutViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val api = GhajarStoreApi(app)
    private val store = ConfigStore.get(app)
    private val prefs = app.getSharedPreferences("ghajar_checkout_v1", 0)
    val revision = mutableIntStateOf(0)
    val busy = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val message = mutableStateOf<String?>(null)
    val purchase = mutableStateOf<GhajarPurchaseResult?>(null)
    val methods = mutableStateOf<GhajarPaymentOptions?>(null)
    val payment = mutableStateOf<GhajarPaymentInit?>(null)
    val receipt = mutableStateOf<Uri?>(null)
    val receiptSent = mutableStateOf(false)
    val walletTopUp = mutableStateOf(false)
    internal val delivery = mutableStateOf<GhajarDelivery?>(null)
    val openUrl = mutableStateOf<String?>(null)
    /** Delivery/refund lifecycle of the current paid order; null for unpaid browsing. */
    private val stage = mutableStateOf<GhajarOrderStage?>(null)
    private var owner = ""
    // The wallet fallback for one invoice is attempted at most once per session
    // and only after the panel had a real chance to finish provisioning.
    private var paidWaitingChecks = 0
    private var fallbackAttemptedOrderId: String? = null

    init {
        busy.value = true
        viewModelScope.launch {
            try {
                owner = accountId()
                restore()
            } finally {
                busy.value = false
            }
            if (purchase.value != null) runOperation { methods.value = api.paymentOptions() }
        }
    }

    private fun accountId(): String = GhajarAccountStore(app).token().takeIf { it.isNotBlank() }?.let {
        MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
    }.orEmpty()

    private fun runOperation(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                val current = accountId()
                if (current.isBlank()) throw GhajarApiException("ابتدا حساب را به ربات متصل کن.")
                if (owner.isNotBlank() && owner != current) {
                    purchase.value = null; payment.value = null; methods.value = null
                    receipt.value = null; delivery.value = null; openUrl.value = null
                    receiptSent.value = false; walletTopUp.value = false; stage.value = null
                    owner = current
                    prefs.edit().clear().apply()
                    throw GhajarApiException("حساب تغییر کرده است؛ سفارش مربوط به حساب قبلی بود.")
                }
                owner = current
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error.value = GhajarCommerceRules.publicMessage(failure.message.orEmpty())
            } finally {
                busy.value = false
            }
        }
    }

    fun buy(request: GhajarPurchaseRequest) = runOperation {
        if (payment.value != null) throw GhajarApiException("ابتدا وضعیت فاکتور فعلی را بررسی کن؛ پرداخت دوباره لازم نیست.")
        walletTopUp.value = false
        val result = api.purchase(request)
        if (result.requiresPayment) {
            purchase.value = result; payment.value = null; receiptSent.value = false
            persist()
            methods.value = api.paymentOptions()
            message.value = "برای همین سفارش، روش پرداخت را انتخاب کن."
        } else {
            require(result.completed) { "خرید تأیید نشد؛ وضعیت سرویس را بررسی کن." }
            // Wallet-balance purchases are paid orders too: track delivery so a
            // failed delivery can still fall back to the wallet exactly once.
            stage.value = GhajarOrderFlow.initialStage(paid = true, walletTopUp = false)
            val service = result.service ?: result.username?.let { api.service(it) }
                ?: throw GhajarApiException("سفارش ثبت شد؛ خروجی سرویس هنوز آماده نیست.")
            deliver(service, finishCheckout = true)
        }
    }

    fun topUp(amount: Long) = runOperation {
        require(amount > 0) { "مبلغ شارژ را به تومان وارد کن." }
        if (payment.value != null) throw GhajarApiException("ابتدا وضعیت فاکتور فعلی را بررسی کن.")
        val options = api.paymentOptions()
        methods.value = options
        walletTopUp.value = true
        purchase.value = GhajarPurchaseResult(false, true, null, amount, options.balance, amount, null)
        persist()
        message.value = "روش پرداخت شارژ کیف پول را انتخاب کن."
    }

    fun refreshMethods() = runOperation { methods.value = api.paymentOptions() }

    fun beginPayment(method: GhajarPaymentMethod) = runOperation {
        val target = purchase.value ?: return@runOperation
        if (payment.value != null) {
            message.value = "فاکتور فعلی را تکمیل یا وضعیتش را بررسی کن؛ پرداخت دوباره لازم نیست."
            return@runOperation
        }
        require(target.amountDue >= method.minimum && (method.maximum <= 0 || target.amountDue <= method.maximum)) {
            "مبلغ سفارش خارج از محدودهٔ این روش است."
        }
        val result = api.beginPayment(method.id, target.amountDue, target.username)
        require(result.orderId.isNotBlank()) { "شناسهٔ فاکتور از سرور دریافت نشد." }
        payment.value = result
        receipt.value = null
        receiptSent.value = false
        paidWaitingChecks = 0
        fallbackAttemptedOrderId = null
        persist() // Save the exact card and amount before leaving for payment.
        message.value = result.message.ifBlank { "فاکتور آماده است." }
        openUrl.value = result.url
    }

    fun uploadReceipt() = runOperation {
        if (receiptSent.value) return@runOperation
        val invoice = payment.value ?: return@runOperation
        val photo = receipt.value ?: return@runOperation
        message.value = api.uploadReceipt(invoice.orderId, photo)
        receiptSent.value = true
        persist()
    }

    fun trial(code: String, username: String?) = runOperation {
        val service = api.createTrial(code, username)
        deliver(service)
    }

    fun importOwned(username: String) = runOperation { deliver(api.service(username)) }

    private suspend fun deliver(service: GhajarServiceDetails, finishCheckout: Boolean = false) {
        // Delivery of an already-delivered fingerprint is idempotent in the API;
        // the stage machine keeps paid orders from being refunded after delivery.
        val paidStage = stage.value?.let(GhajarOrderFlow::onDeliveryAttempt)
        if (paidStage != null) stage.value = paidStage
        delivery.value = GhajarDelivery(service, 0, false)
        val imported = try {
            val count = api.importServiceOnce(store, service)
            if (paidStage != null) stage.value = GhajarOrderFlow.onDelivered(paidStage, count)
            count
        } catch (failure: Exception) {
            if (paidStage != null) {
                stage.value = GhajarOrderFlow.onDeliveryFailed(paidStage)
                persist() // A paid order must resume as PROVISION_FAILED, never re-charge.
            }
            throw failure
        }
        // Only a parsed/imported configuration counts as installed.
        val installed = imported > 0
        delivery.value = GhajarDelivery(service, imported, installed)
        message.value = if (installed) "سرویس به قاجار VPN اضافه شد؛ QR و اطلاعات اتصال آماده است."
            else "سرویس صادر شد؛ خروجی اتصال هنوز در دسترس نیست."
        if (installed) {
            revision.intValue++
            // Importing a trial/owned service must not discard an unrelated unpaid invoice.
            if (finishCheckout) {
                purchase.value = null; payment.value = null; receipt.value = null
                receiptSent.value = false; walletTopUp.value = false; stage.value = null
                prefs.edit().clear().apply()
            }
        }
    }

    /** Only authenticated server state can confirm payment, never a redirect URL. */
    fun checkPayment() = runOperation {
        val invoice = payment.value ?: return@runOperation
        val status = api.paymentStatus(invoice.orderId)
        val value = status.optString("payment_status")
        val service = status.optJSONObject("service")
        val serviceReady = status.optBoolean("is_service_ready")
        val outcome = GhajarCommerceRules.paymentOutcome(value, walletTopUp.value, status.optBoolean("wallet_credited_only"),
            serviceReady, service != null)
        when (outcome) {
            GhajarPaymentOutcome.WALLET_CREDITED -> {
                // The panel confirmed the money went back to the wallet; record it once.
                stage.value = GhajarOrderFlow.onWalletRefunded(
                    stage.value ?: GhajarOrderFlow.initialStage(paid = true, walletTopUp = walletTopUp.value)
                    ?: GhajarOrderStage.PAYMENT_CONFIRMED) ?: GhajarOrderStage.WALLET_REFUNDED
                persist()
                methods.value = api.paymentOptions()
                message.value = if (walletTopUp.value) "شارژ کیف پول تأیید شد و موجودی بروزرسانی شد."
                    else "پرداخت تأیید شد و مبلغ به کیف پول برگشت؛ خرید را از محصولات ادامه بده."
                purchase.value = null; payment.value = null; receipt.value = null
                receiptSent.value = false; walletTopUp.value = false
                stage.value = null
                prefs.edit().clear().apply()
            }
            GhajarPaymentOutcome.SERVICE_READY ->
                deliver(api.serviceFrom(requireNotNull(service), purchase.value?.username.orEmpty()), finishCheckout = true)
            GhajarPaymentOutcome.PAID_WAITING -> {
                // Payment is real but the service is not delivered yet: the order
                // becomes PROVISION_FAILED territory with an idempotent wallet fallback.
                if (stage.value == null) stage.value = GhajarOrderFlow.initialStage(paid = true, walletTopUp = walletTopUp.value)
                stage.value = stage.value ?: GhajarOrderStage.PAYMENT_CONFIRMED
                paidWaitingChecks++
                persist()
                val mayFallback = GhajarOrderFlow.refundEligible(value, walletTopUp.value, serviceReady, service != null) &&
                    GhajarOrderFlow.walletFallbackAllowed(stage.value!!) &&
                    fallbackAttemptedOrderId != invoice.orderId &&
                    (paidWaitingChecks >= 2 || stage.value == GhajarOrderStage.PROVISION_FAILED)
                if (mayFallback) {
                    fallbackAttemptedOrderId = invoice.orderId
                    val credited = runCatching { api.requestWalletFallback(invoice.orderId) }.getOrDefault(false)
                    if (credited) {
                        stage.value = GhajarOrderFlow.onWalletRefunded(stage.value!!) ?: GhajarOrderStage.WALLET_REFUNDED
                        methods.value = api.paymentOptions()
                        message.value = "پرداخت تأیید شد ولی سرویس تحویل نشد؛ مبلغ به‌صورت خودکار به کیف پول برگشت."
                        purchase.value = null; payment.value = null; receipt.value = null
                        receiptSent.value = false; walletTopUp.value = false; stage.value = null
                        prefs.edit().clear().apply()
                    } else {
                        stage.value = GhajarOrderFlow.onDeliveryFailed(stage.value!!)
                        persist()
                        message.value = "پرداخت تأیید شد؛ سرویس در حال آماده‌سازی است. دوباره پرداخت نکن."
                    }
                } else {
                    message.value = "پرداخت تأیید شد؛ سرویس در حال آماده‌سازی است. دوباره پرداخت نکن."
                }
            }
            GhajarPaymentOutcome.NOT_APPROVED ->
                message.value = status.optString("reason").takeUnless { it.isBlank() || it == "null" }
                    ?: "این فاکتور تأیید نشده یا منقضی است."
            GhajarPaymentOutcome.PENDING -> message.value = if (receiptSent.value) "رسید ارسال شده و در انتظار بررسی ادمین است."
                else "فاکتور در انتظار پرداخت یا تأیید سرور است؛ پس از پرداخت، بررسی وضعیت را بزن."
        }
    }

    fun leaveInvoice() {
        if (busy.value) return
        // Local navigation only: this does not cancel a real payment on the server.
        purchase.value = null; payment.value = null; methods.value = null
        receipt.value = null; receiptSent.value = false; walletTopUp.value = false; stage.value = null
        paidWaitingChecks = 0; fallbackAttemptedOrderId = null
        prefs.edit().clear().apply()
        message.value = "به محصولات برگشتی. اگر پرداخت کرده‌ای، قبل از خرید دوباره «سرویس‌های من» را بررسی کن."
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        val p = purchase.value ?: return@withContext
        val root = JSONObject().put("owner", owner).put("username", p.username)
            .put("due", p.amountDue).put("balance", p.balance).put("price", p.price)
            .put("receipt_sent", receiptSent.value).put("wallet_top_up", walletTopUp.value)
        stage.value?.let { root.put("stage", it.name) }
        payment.value?.let { v ->
            root.put("payment", JSONObject().put("kind", v.kind).put("order", v.orderId)
                .put("url", v.url).put("card", v.cardNumber).put("holder", v.cardHolder)
                .put("amount", v.amount).put("rial", v.amountRial).put("message", v.message))
        }
        val encrypted = Crypto.encrypt(root.toString()) ?: throw GhajarApiException("ذخیرهٔ امن فاکتور انجام نشد؛ از پرداخت خارج نشو.")
        if (!prefs.edit().putString("invoice", encrypted).commit()) throw GhajarApiException("ذخیرهٔ فاکتور روی گوشی ناموفق بود.")
    }

    private suspend fun restore() {
        val root = withContext(Dispatchers.IO) {
            runCatching { prefs.getString("invoice", null)?.let(Crypto::decrypt)?.let(::JSONObject) }.getOrNull()
        } ?: return
        if (owner.isBlank() || root.optString("owner") != owner) return
        purchase.value = GhajarPurchaseResult(false, true, root.optString("username").takeUnless { it == "null" || it.isBlank() },
            root.optLong("due"), root.optLong("balance"), root.optLong("price"), null)
        receiptSent.value = root.optBoolean("receipt_sent")
        walletTopUp.value = root.optBoolean("wallet_top_up")
        stage.value = GhajarOrderFlow.fromStorage(root.optString("stage").takeUnless { it.isBlank() })
        root.optJSONObject("payment")?.let { p ->
            fun optional(key: String) = p.optString(key).takeUnless { it.isBlank() || it == "null" }
            payment.value = GhajarPaymentInit(p.optString("kind"), p.optString("order"), optional("url"),
                optional("card"), optional("holder"), p.optLong("amount"), p.optLong("rial"), p.optString("message"))
        }
    }
}

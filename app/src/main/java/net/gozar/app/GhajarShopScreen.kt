package net.gozar.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale

/** Fully native storefront backed by the API in Ghajar_vpnbot_-3-1.zip. */
@Composable
fun GhajarShopScreen(modifier: Modifier = Modifier, active: Boolean = true) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { ConfigStore.get(context.applicationContext) }
    val api = remember { GhajarStoreApi(context) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val checkoutModel = remember(context) { ViewModelProvider(context as ComponentActivity)[GhajarCheckoutViewModel::class.java] }
    val checkoutBusy by checkoutModel.busy
    val delivery by checkoutModel.delivery
    val receiptSent by checkoutModel.receiptSent
    val walletTopUp by checkoutModel.walletTopUp
    var walletAmount by remember { mutableStateOf("") }
    val requestedUrl by checkoutModel.openUrl

    var linked by remember { mutableStateOf(api.isLinked) }
    var linkSession by remember { mutableStateOf(api.pendingLink()) }
    var linkGate by remember(linkSession?.sessionToken) { mutableStateOf<GhajarLinkState?>(null) }
    var linkState by remember { mutableStateOf(GhajarLinkState.PENDING) }
    var linkChecking by remember { mutableStateOf(false) }
    var linkCheckKey by remember { mutableIntStateOf(0) }
    var linkRemaining by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by checkoutModel.error
    var message by checkoutModel.message
    var refreshKey by remember { mutableIntStateOf(0) }
    var section by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    var confirmation by remember { mutableStateOf<GhajarPurchaseRequest?>(null) }
    var confirmationTitle by remember { mutableStateOf("") }
    var confirmationPrice by remember { mutableStateOf<Long?>(null) }

    var panels by remember { mutableStateOf<List<GhajarPanel>>(emptyList()) }
    var selectedPanel by remember { mutableStateOf<GhajarPanel?>(null) }
    var categories by remember { mutableStateOf<List<GhajarCategory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<GhajarCategory?>(null) }
    var timeRanges by remember { mutableStateOf<List<GhajarTimeRange>>(emptyList()) }
    var selectedTime by remember { mutableStateOf<GhajarTimeRange?>(null) }
    var products by remember { mutableStateOf<List<GhajarProduct>>(emptyList()) }
    var owned by remember { mutableStateOf<List<GhajarOwnedService>>(emptyList()) }
    var notices by remember { mutableStateOf<List<GhajarNotice>>(emptyList()) }
    var loadedPanelId by remember { mutableStateOf<String?>(null) }

    var customMode by remember { mutableStateOf(false) }
    var customTraffic by remember { mutableStateOf("") }
    var customDays by remember { mutableStateOf("") }
    var customQuote by remember { mutableStateOf<GhajarCustomQuote?>(null) }
    var customUsername by remember { mutableStateOf("") }
    var customNote by remember { mutableStateOf("") }
    var discountCode by remember { mutableStateOf("") }

    val pendingPurchase by checkoutModel.purchase
    val paymentOptions by checkoutModel.methods
    val paymentInit by checkoutModel.payment
    var receiptUri by checkoutModel.receipt
    var trialOptions by remember { mutableStateOf<GhajarTrialOptions?>(null) }

    suspend fun refreshOwnedAndNotices() {
        owned = api.ownedServices()
        notices = api.notices()
    }

    val checkout = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkoutModel.checkPayment()
        refreshKey++
    }

    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        receiptUri = uri
        if (uri != null) storeResult { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun openCheckout(url: String) {
        checkout.launch(
            Intent(context, SecurePaymentActivity::class.java)
                .putExtra(SecurePaymentActivity.EXTRA_URL, url)
        )
    }

    fun openBot(session: GhajarLinkSession? = linkSession) {
        if (session == null || !GhajarUiRules.validPendingLink(session.code, session.sessionToken,
                session.expiresAtMillis, System.currentTimeMillis())) {
            api.clearPendingLink()
            linkSession = null
            error = "کد اتصال معتبر نیست یا منقضی شده؛ دوباره «اتصال با تلگرام» را بزن."
            return
        }
        val verification = linkGate != null
        val launch: (String) -> Boolean = { url ->
            storeResult {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE))
            }.isSuccess
        }
        val opened = if (verification) GhajarUiRules.botVerificationUrls(session.botUsername).any(launch)
            else GhajarUiRules.launchBotLogin(session.botUsername, session.code, launch)
        if (opened) {
            message = if (verification) "ربات باز شد؛ «Start / شروع» را بزن و مراحل تأیید حساب را کامل کن."
                else "لینک تلگرام با کد اتصال آماده باز شد؛ «Start / شروع» را بزن و برگرد. نیازی به تایپ کد نیست."
        } else {
            error = if (verification) "تلگرام یا مرورگر در دسترس نیست؛ ربات قاجار را باز کن و /start بفرست."
                else "تلگرام یا مرورگر در دسترس نیست؛ فرمان کامل اتصال را کپی کن."
        }
    }

    LaunchedEffect(requestedUrl, active) {
        if (active && requestedUrl != null) {
            checkoutModel.openUrl.value = null
            openCheckout(requestedUrl!!)
        }
    }
    LaunchedEffect(active, paymentInit?.orderId, lifecycle) {
        if (!active || paymentInit == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                checkoutModel.checkPayment()
                delay(15_000)
            }
        }
    }

    LaunchedEffect(section, pendingPurchase) {
        listState.scrollToItem(0)
        if (section == 3 && linked) checkoutModel.refreshMethods()
    }

    LaunchedEffect(linked, refreshKey) {
        if (!linked) return@LaunchedEffect
        busy = true
        error = null
        storeResult {
            panels = api.countries()
            if (selectedPanel == null || panels.none { it.id == selectedPanel?.id }) selectedPanel = panels.firstOrNull()
            refreshOwnedAndNotices()
        }.onFailure { error = BrandConfig.sanitizePublicText(it.message ?: "خطا در دریافت فروشگاه") }
        busy = false
    }

    LaunchedEffect(selectedPanel?.id) {
        val panel = selectedPanel ?: return@LaunchedEffect
        loadedPanelId = null
        selectedCategory = null
        selectedTime = null
        customMode = false
        customQuote = null
        products = emptyList()
        storeResult {
            categories = api.categories(panel.id)
            timeRanges = api.timeRanges(panel.id)
            loadedPanelId = panel.id
        }.onFailure { error = GhajarCommerceRules.publicMessage(it.message.orEmpty()) }
    }
    LaunchedEffect(loadedPanelId, selectedCategory?.id, selectedTime?.days, customMode) {
        val panel = selectedPanel ?: return@LaunchedEffect
        if (loadedPanelId != panel.id) return@LaunchedEffect
        busy = true
        try {
            storeResult {
                products = if (customMode) emptyList() else api.products(panel.id, selectedCategory?.id, selectedTime?.days)
            }.onFailure { error = GhajarCommerceRules.publicMessage(it.message.orEmpty()) }
        } finally { busy = false }
    }

    LaunchedEffect(linkSession?.sessionToken, active, lifecycle) {
        val session = linkSession ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            do {
                linkRemaining = GhajarLinkFlow.remainingSeconds(session.expiresAtMillis, System.currentTimeMillis())
                if (linkRemaining > 0) delay(1_000)
            } while (linkRemaining > 0)
        }
    }

    LaunchedEffect(linkSession?.sessionToken, linkCheckKey, active, lifecycle) {
        val session = linkSession ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // A durable save may have finished just as Android stopped the Activity.
            if (api.isLinked) {
                linked = true; linkSession = null
                message = GhajarLinkFlow.message(GhajarLinkState.LINKED)
                GhajarNotificationMonitor.refresh(context.applicationContext)
                return@repeatOnLifecycle
            }
            var failures = 0
            while (true) {
                val result = if (System.currentTimeMillis() >= session.expiresAtMillis) GhajarLinkState.EXPIRED else {
                    linkChecking = true
                    try {
                        api.pollLink(session.sessionToken)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        GhajarLinkState.NETWORK_ERROR
                    } catch (_: Exception) {
                        GhajarLinkState.SERVER_ERROR
                    } finally {
                        linkChecking = false
                    }
                }
                currentCoroutineContext().ensureActive()
                linkState = result
                linkGate = GhajarLinkFlow.verificationGate(linkGate, result)
                when (result) {
                    GhajarLinkState.LINKED -> {
                        linked = true; linkSession = null; error = null
                        message = GhajarLinkFlow.message(result)
                        GhajarNotificationMonitor.refresh(context.applicationContext)
                        return@repeatOnLifecycle
                    }
                    GhajarLinkState.EXPIRED, GhajarLinkState.NOT_FOUND, GhajarLinkState.SUPERSEDED -> {
                        if (result != GhajarLinkState.SUPERSEDED) api.clearPendingLink()
                        linkSession = null; message = null
                        error = GhajarLinkFlow.message(result)
                        return@repeatOnLifecycle
                    }
                    else -> Unit
                }
                failures = if (result in setOf(GhajarLinkState.NETWORK_ERROR, GhajarLinkState.SERVER_ERROR,
                        GhajarLinkState.STORAGE_ERROR)) (failures + 1).coerceAtMost(4) else 0
                val remainingMs = (session.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(1)
                delay(minOf(GhajarLinkFlow.retryDelayMillis(failures), remainingMs))
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 14.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ShopHeader(linked = linked, onRefresh = { refreshKey++ })
        }
        if (busy || checkoutBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        message?.let { text -> item { StatusCard(text, error = false, onDismiss = { message = null }) } }
        error?.let { text -> item { StatusCard(text, error = true, onDismiss = { error = null }) } }

        if (!linked) {
            item {
                LinkAccountCard(
                    session = linkSession,
                    busy = busy,
                    state = linkState,
                    verification = linkGate != null,
                    checking = linkChecking,
                    remainingSeconds = linkRemaining,
                    onBegin = {
                        scope.launch {
                            busy = true
                            error = null
                            message = null
                            try {
                                linkSession = api.beginLink()
                                linkState = GhajarLinkState.PENDING
                                linkRemaining = GhajarLinkFlow.remainingSeconds(linkSession!!.expiresAtMillis, System.currentTimeMillis())
                                openBot(linkSession)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: IOException) {
                                error = "کد اتصال دریافت نشد؛ اینترنت را بررسی کن و دوباره تلاش کن."
                            } catch (_: Exception) {
                                error = "ساخت کد اتصال انجام نشد؛ چند لحظه بعد دوباره تلاش کن."
                            } finally { busy = false }
                        }
                    },
                    onOpenBot = { openBot() },
                    onCheck = { if (!linkChecking) linkCheckKey++ },
                    onCancel = {
                        api.clearPendingLink(); linkSession = null
                        message = "درخواست ورود در این گوشی لغو شد؛ برای ادامه کد تازه بگیر."
                        error = null
                    },
                    onCopyCommand = {
                        linkSession?.let { clipboard.setText(AnnotatedString("/link ${it.code}")) }
                        message = "فرمان اتصال کپی شد؛ آن را بدون ویرایش در ربات بفرست."
                    }
                )
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("خرید", "سرویس‌ها", "پیام‌ها", "کیف پول").forEachIndexed { index, label ->
                        FilterChip(selected = section == index, onClick = { section = index },
                            label = { Text(label, maxLines = 1) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (section == 3) {
                item {
                    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("کیف پول قاجار", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(paymentOptions?.let { "${formatPrice(it.balance)} ${it.currency}" } ?: "در حال دریافت موجودی…",
                                style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(walletAmount, { walletAmount = asciiDigits(it).filter(Char::isDigit).take(12) },
                                label = { Text("مبلغ شارژ به تومان") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { checkoutModel.topUp(walletAmount.toLongOrNull() ?: 0); section = 0 },
                                enabled = !checkoutBusy && (walletAmount.toLongOrNull() ?: 0) > 0,
                                modifier = Modifier.fillMaxWidth()) { Text("شارژ کیف پول") }
                            OutlinedButton(onClick = checkoutModel::refreshMethods, enabled = !checkoutBusy,
                                modifier = Modifier.fillMaxWidth()) { Text("بروزرسانی موجودی") }
                            Text("شارژ پس از تأیید پنل به موجودی اضافه می‌شود. برای شارژ، سرویس جدید ساخته نمی‌شود.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (section == 2) {
                if (notices.isEmpty() && !busy) item { Text("پیام تازه‌ای ندارید", modifier = Modifier.padding(16.dp)) }
                items(notices, key = { "notice:${it.id}" }) { NoticeCard(it) }
            }

            if (section == 1) {
                item { SectionTitle("سرویس‌های من", "برای دریافت خودکار کانفیگ روی سرویس بزن") }
                if (owned.isEmpty() && !busy) item { Text("هنوز سرویسی برای این حساب ثبت نشده است.") }
                items(owned, key = { "owned:${it.username}" }) { service ->
                    OwnedServiceCard(service) { checkoutModel.importOwned(service.username) }
                }
            }

            if (section == 0) {
            if (pendingPurchase == null) {
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            storeResult { api.trialOptions() }
                                .onSuccess { trialOptions = it }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
                    enabled = !busy && !checkoutBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CardGiftcard, null)
                    Spacer(Modifier.width(8.dp))
                    Text("دریافت سرویس تست")
                }
            }
            trialOptions?.let { options ->
                if (!options.canRequest) item { Text("سهمیهٔ سرویس تست در دسترس نیست", color = MaterialTheme.colorScheme.error) }
                items(options.panels, key = { "trial:${it.code}" }) { panel ->
                    OutlinedButton(
                        onClick = {
                            checkoutModel.trial(panel.code, customUsername)
                            trialOptions = null
                        },
                        enabled = options.canRequest && !checkoutBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تست ${panel.name}") }
                }
            }
            item { SectionTitle("۱. انتخاب سرویس", "قیمت و موجودی مستقیماً از پنل دریافت می‌شود") }
            if (panels.isNotEmpty()) {
                item {
                    FilterRow(
                        items = panels,
                        selected = selectedPanel,
                        label = { it.name },
                        onSelect = { selectedPanel = it }
                    )
                }
            }
            if (categories.isNotEmpty()) {
                item {
                    NullableFilterRow(
                        allLabel = "همه دسته‌ها",
                        items = categories,
                        selected = selectedCategory,
                        label = { it.name },
                        onSelect = { selectedCategory = it }
                    )
                }
            }
            if (timeRanges.isNotEmpty()) {
                item {
                    NullableFilterRow(
                        allLabel = "همه مدت‌ها",
                        items = timeRanges,
                        selected = selectedTime,
                        label = { it.name },
                        onSelect = { selectedTime = it }
                    )
                }
            }

            selectedPanel?.takeIf { it.custom }?.let {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = !customMode, onClick = { customMode = false }, label = { Text("پلن‌های آماده") })
                        FilterChip(selected = customMode, onClick = { customMode = true }, label = { Text("سرویس سفارشی") })
                    }
                }
            }

            if (customMode) {
                item {
                    CustomServiceCard(
                        traffic = customTraffic,
                        days = customDays,
                        quote = customQuote,
                        onTrafficChange = { customTraffic = asciiDigits(it).take(5); customQuote = null },
                        onDaysChange = { customDays = asciiDigits(it).take(4); customQuote = null },
                        onQuote = {
                            val panel = selectedPanel ?: return@CustomServiceCard
                            if (busy) return@CustomServiceCard
                            val requestedTraffic = customTraffic
                            val requestedDays = customDays
                            scope.launch {
                                busy = true
                                storeResult {
                                    api.customQuote(panel.id, requestedTraffic.toIntOrNull() ?: 0, requestedDays.toIntOrNull() ?: 0)
                                }.onSuccess {
                                    if (customTraffic == requestedTraffic && customDays == requestedDays && selectedPanel?.id == panel.id) customQuote = it
                                }.onFailure { error = it.message }
                                busy = false
                            }
                        }
                    )
                }
            } else {
                items(products, key = { "product:${it.id}" }) { product ->
                    ProductCard(product, enabled = !busy && !checkoutBusy) {
                        confirmationTitle = product.name
                        confirmationPrice = product.price
                        confirmation = GhajarPurchaseRequest(countryId = product.countryId, serviceId = product.id)
                    }
                }
            }

            selectedPanel?.let { panel ->
                if (customMode) {
                    item {
                        Button(
                            onClick = {
                                confirmationTitle = "سرویس سفارشی · $customTraffic گیگ · $customDays روز"
                                confirmationPrice = customQuote?.price
                                confirmation = GhajarPurchaseRequest(countryId = panel.id,
                                    customTrafficGb = customTraffic.toIntOrNull(), customTimeDays = customDays.toIntOrNull())
                            },
                            enabled = customQuote?.price != null && !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("خرید سرویس سفارشی") }
                    }
                }
            }
            }

            pendingPurchase?.takeIf { it.requiresPayment }?.let { purchase ->
                item { SectionTitle("۳. پرداخت", "فاکتور روی گوشی حفظ می‌شود؛ وضعیت را از سرور بررسی کن") }
                item { PaymentSummary(purchase, walletTopUp) }
                if (paymentInit == null) {
                    if (paymentOptions == null) item {
                        OutlinedButton(onClick = checkoutModel::refreshMethods, enabled = !checkoutBusy,
                            modifier = Modifier.fillMaxWidth()) { Text("دریافت روش‌های پرداخت") }
                    }
                    items(paymentOptions?.methods.orEmpty(), key = { "pay:${it.id}" }) { method ->
                        PaymentMethodCard(method, purchase.amountDue, !checkoutBusy) { checkoutModel.beginPayment(method) }
                    }
                }
            }
            paymentInit?.let { payment ->
                if (GhajarCommerceRules.cardPayment(payment.kind, payment.cardNumber)) item {
                    CardToCardCard(payment, receiptUri, checkoutBusy, receiptSent,
                        onPickReceipt = { receiptPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                        onUpload = checkoutModel::uploadReceipt)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("کد پیگیری: ${payment.orderId}", style = MaterialTheme.typography.labelMedium)
                        payment.url?.let { url ->
                            Button(onClick = { openCheckout(url) }, enabled = !checkoutBusy,
                                modifier = Modifier.fillMaxWidth()) { Text("ادامهٔ همین پرداخت") }
                        }
                        OutlinedButton(onClick = checkoutModel::checkPayment, enabled = !checkoutBusy,
                            modifier = Modifier.fillMaxWidth()) { Text("پرداخت کردم؛ بررسی و دریافت سرویس") }
                        Text("بستن صفحه به معنی لغو تراکنش نیست. در صورت پرداخت، دوباره واریز نکن.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (pendingPurchase != null) item {
                TextButton(onClick = checkoutModel::leaveInvoice, enabled = !checkoutBusy) { Text("بازگشت به محصولات") }
            }

            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
    delivery?.let { result ->
        GhajarDeliveryDialog(result, onDismiss = { checkoutModel.delivery.value = null },
            onRetry = { checkoutModel.importOwned(result.service.username) }, busy = checkoutBusy)
    }
    confirmation?.let { request ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("۲. تأیید سفارش") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(confirmationTitle, fontWeight = FontWeight.Bold)
                    Text(confirmationPrice?.let { "قیمت پایه: ${formatPrice(it)} تومان" } ?: "قیمت در دسترس نیست")
                    Text("با تأیید، خرید با موجودی کیف پول انجام می‌شود؛ اگر کافی نباشد مرحلهٔ پرداخت باز می‌شود.", style = MaterialTheme.typography.bodySmall)
                    PurchaseExtras(customUsername, selectedPanel?.usernameRequired == true, customNote,
                        selectedPanel?.customUsername == true, selectedPanel?.noteEnabled == true, discountCode,
                        { customUsername = it.take(40) }, { customNote = it.take(120) }, { discountCode = it.take(60) })
                }
            },
            confirmButton = {
                Button(enabled = !busy && !checkoutBusy && confirmationPrice != null &&
                    (selectedPanel?.usernameRequired != true || customUsername.isNotBlank()),
                    onClick = {
                        confirmation = null
                        checkoutModel.buy(request.copy(customUsername = customUsername, note = customNote, discountCode = discountCode))
                    }) { Text("تأیید و ادامه") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("بازگشت") } }
        )
    }
}

private fun asciiDigits(value: String): String = GhajarUiRules.asciiDigits(value)

@Composable
private fun ShopHeader(linked: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ghajar_treasury), contentDescription = "وزیر خزانه‌داری قاجار با سبد خرید",
            contentScale = ContentScale.Fit, modifier = Modifier.size(80.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("خزانهٔ قاجار", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (linked) "حساب متصل و همگام است" else "برای خرید، حساب ربات را یک‌بار متصل کن", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (linked) IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "بروزرسانی") }
    }
}

@Composable
private fun LinkAccountCard(session: GhajarLinkSession?, busy: Boolean, state: GhajarLinkState, verification: Boolean,
    checking: Boolean, remainingSeconds: Int, onBegin: () -> Unit, onOpenBot: () -> Unit,
    onCheck: () -> Unit, onCancel: () -> Unit, onCopyCommand: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B211C)),
        border = BorderStroke(1.dp, Color(0x66D6B45F))
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Security, null, tint = Color(0xFFD6B45F), modifier = Modifier.size(40.dp))
            Text("اتصال امن حساب", color = Color(0xFFF7F2E8), fontWeight = FontWeight.Bold)
            Text("ورود با ربات؛ اطلاعات اتصال در گوشی رمزگذاری می‌شود.", color = Color(0xFF91BCC7), textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            if (session == null) {
                Button(onClick = onBegin, enabled = !busy) { Icon(Icons.Filled.Link, null); Spacer(Modifier.width(7.dp)); Text("اتصال با تلگرام") }
            } else {
                if (!verification) {
                    TextButton(onClick = onOpenBot, enabled = remainingSeconds > 0) {
                        Text(session.code, style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFD6B45F), fontWeight = FontWeight.Black)
                    }
                    Text("۱. «تأیید اتصال در تلگرام» را بزن.\n۲. پایین چت ربات، «شروع / Start» را بزن.\n۳. به قاجار برگرد؛ حساب خودکار متصل می‌شود.",
                        color = Color(0xFFF7F2E8), textAlign = TextAlign.Center)
                    Text("کد از قبل داخل لینک است؛ آن را تایپ یا اصلاح نکن.", color = Color(0xFF91BCC7), textAlign = TextAlign.Center)
                }
                Text("زمان باقی‌مانده: ${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')}",
                    color = Color(0xFFD6B45F), modifier = Modifier.padding(vertical = 8.dp))
                Text(GhajarLinkFlow.message(state), color = Color(0xFFF7F2E8), textAlign = TextAlign.Center)
                if (checking) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Button(onClick = onOpenBot, enabled = remainingSeconds > 0,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.OpenInNew, null); Spacer(Modifier.width(7.dp))
                    Text(if (verification) "تکمیل تأیید در ربات" else "تأیید اتصال در تلگرام")
                }
                OutlinedButton(onClick = onCheck, enabled = !checking && remainingSeconds > 0,
                    modifier = Modifier.fillMaxWidth()) { Text("تأیید کردم؛ بررسی دوباره") }
                if (!verification) TextButton(onClick = onCopyCommand, enabled = remainingSeconds > 0) { Text("کپی فرمان کامل اتصال") }
                TextButton(onClick = onCancel) { Text("لغو درخواست ورود") }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: GhajarNotice) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (notice.important) Color(0xFF4B211B) else Color(0xFF143A32)),
        border = BorderStroke(1.dp, if (notice.important) Color(0xFFE57373) else Color(0xFF91BCC7))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Notifications, null, tint = Color(0xFFD6B45F))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(notice.title, color = Color(0xFFF7F2E8), fontWeight = FontWeight.Bold)
                Text(notice.message, color = Color(0xFFE6E0D4))
                notice.meta?.let { meta ->
                    val remainingGb = meta.remainingBytes?.div(1024.0 * 1024 * 1024)
                    val details = listOfNotNull(
                        remainingGb?.let { "باقی‌مانده ${"%.2f".format(Locale.US, it)} گیگ" },
                        meta.daysRemaining?.let { "$it روز باقی‌مانده" }
                    ).joinToString(" · ")
                    if (details.isNotBlank()) Text(details, color = Color(0xFFD6B45F), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column { Text(title, fontWeight = FontWeight.ExtraBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun OwnedServiceCard(service: GhajarOwnedService, onImport: () -> Unit) {
    Card(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(service.productName, fontWeight = FontWeight.Bold)
                Text("\u2066${service.username}\u2069", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(service.location, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val active = service.status.lowercase() in setOf("active", "enabled", "فعال")
            Text(if (active) "فعال" else when(service.status.lowercase()) { "expired" -> "منقضی"; "disabled", "inactive" -> "غیرفعال"; else -> service.status },
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.AddCircle, "افزودن")
        }
    }
}

@Composable
private fun <T> FilterRow(items: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(selected?.let(label) ?: "انتخاب لوکیشن", maxLines = 2)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item -> DropdownMenuItem(text = { Text(label(item)) }, onClick = { onSelect(item); open = false }) }
        }
    }
}

@Composable
private fun <T> NullableFilterRow(allLabel: String, items: List<T>, selected: T?, label: (T) -> String, onSelect: (T?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(selected?.let(label) ?: allLabel, maxLines = 2)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelect(null); open = false })
            items.forEach { item -> DropdownMenuItem(text = { Text(label(item)) }, onClick = { onSelect(item); open = false }) }
        }
    }
}

@Composable
private fun ProductCard(product: GhajarProduct, enabled: Boolean, onBuy: () -> Unit) {
    var details by remember(product.id) { mutableStateOf(false) }
    Card(onClick = { details = true }, enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(product.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(product.trafficGb?.let { "${it.toBigDecimal().stripTrailingZeros().toPlainString()} گیگ" },
                    product.days?.let { "$it روز" }).joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(product.price?.let { if (it == 0L) "رایگان" else "${formatPrice(it)} تومان" } ?: "قیمت در دسترس نیست",
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Filled.OpenInNew, "مشاهدهٔ محصول", modifier = Modifier.size(20.dp))
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false },
        title = { Text(product.name) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(product.price?.let { "${formatPrice(it)} تومان" } ?: "قیمت در دسترس نیست", fontWeight = FontWeight.Bold)
            Text(product.description.ifBlank { "توضیح بیشتری از پنل ارسال نشده است." })
        } },
        confirmButton = { Button(onClick = { details = false; onBuy() }, enabled = enabled && product.price != null) { Text("انتخاب و ادامه") } },
        dismissButton = { TextButton(onClick = { details = false }) { Text("بازگشت") } })
}

@Composable
private fun CustomServiceCard(
    traffic: String,
    days: String,
    quote: GhajarCustomQuote?,
    onTrafficChange: (String) -> Unit,
    onDaysChange: (String) -> Unit,
    onQuote: () -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("سرویس سفارشی", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(traffic, onTrafficChange, label = { Text("حجم (گیگ)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(days, onDaysChange, label = { Text("مدت (روز)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            quote?.let {
                Text(
                    if (it.price != null) "قیمت لحظه‌ای: ${formatPrice(it.price)} تومان" else "سرویس سفارشی برای این پنل فعال نیست",
                    color = Color(0xFFD6B45F), fontWeight = FontWeight.Bold
                )
                Text("حجم ${it.trafficMin} تا ${it.trafficMax} گیگ · زمان ${it.timeMin} تا ${it.timeMax} روز", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onQuote, modifier = Modifier.fillMaxWidth()) { Text("محاسبه قیمت از پنل") }
        }
    }
}

@Composable
private fun PurchaseExtras(
    username: String,
    usernameRequired: Boolean,
    note: String,
    showUsername: Boolean,
    showNote: Boolean,
    discount: String,
    onUsername: (String) -> Unit,
    onNote: (String) -> Unit,
    onDiscount: (String) -> Unit
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (showUsername) OutlinedTextField(username, onUsername, label = { Text(if (usernameRequired) "نام کاربری دلخواه (ضروری)" else "نام کاربری دلخواه") }, modifier = Modifier.fillMaxWidth())
            if (showNote) OutlinedTextField(note, onNote, label = { Text("یادداشت اختیاری") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(discount, onDiscount, label = { Text("کد تخفیف اختیاری") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PaymentSummary(purchase: GhajarPurchaseResult, walletTopUp: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF143A32))) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(if (walletTopUp) "شارژ کیف پول" else "پرداخت مبلغ کسری", color = Color(0xFFD6B45F), fontWeight = FontWeight.ExtraBold)
            Text("موجودی: ${formatPrice(purchase.balance)} تومان", color = Color.White)
            if (!walletTopUp) Text("قیمت سرویس: ${formatPrice(purchase.price)} تومان", color = Color.White)
            Text("قابل پرداخت: ${formatPrice(purchase.amountDue)} تومان", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PaymentMethodCard(method: GhajarPaymentMethod, amount: Long, enabled: Boolean, onClick: () -> Unit) {
    val allowed = amount >= method.minimum && (method.maximum <= 0 || amount <= method.maximum)
    Card(onClick = onClick, enabled = allowed && enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CreditCard, null, tint = Color(0xFFD6B45F))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(method.label, fontWeight = FontWeight.Bold)
                Text("محدوده ${formatPrice(method.minimum)} تا ${if (method.maximum > 0) formatPrice(method.maximum) else "نامحدود"} تومان", style = MaterialTheme.typography.bodySmall)
            }
            Text(if (allowed) "انتخاب" else "نامعتبر", color = if (allowed) Color(0xFF0E8067) else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusCard(text: String, error: Boolean, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (error) GhajarCommerceRules.publicMessage(text) else text, modifier = Modifier.weight(1f), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    }
}

private fun formatPrice(price: Long): String = NumberFormat.getIntegerInstance(Locale("fa", "IR")).format(price)

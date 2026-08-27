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
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Fully native storefront backed by the API in Ghajar_vpnbot_-3-1.zip. */
@Composable
fun GhajarShopScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { ConfigStore.get(context.applicationContext) }
    val api = remember { GhajarStoreApi(context) }
    val scope = rememberCoroutineScope()

    var linked by remember { mutableStateOf(api.isLinked) }
    var linkSession by remember { mutableStateOf(api.pendingLink()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
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

    var pendingPurchase by remember { mutableStateOf<GhajarPurchaseResult?>(null) }
    var paymentOptions by remember { mutableStateOf<GhajarPaymentOptions?>(null) }
    var paymentInit by remember { mutableStateOf<GhajarPaymentInit?>(null) }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    var trialOptions by remember { mutableStateOf<GhajarTrialOptions?>(null) }

    suspend fun refreshOwnedAndNotices() {
        owned = api.ownedServices()
        notices = api.notices()
    }

    suspend fun importIssuedService(username: String?): Int {
        if (username.isNullOrBlank()) return 0
        val service = api.service(username)
        return api.importServiceOnce(store, service)
    }

    val checkout = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                busy = true
                delay(700)
                val count = runCatching { importIssuedService(pendingPurchase?.username) }.getOrDefault(0)
                runCatching { refreshOwnedAndNotices() }
                message = if (count > 0) "$count پیکربندی خریداری‌شده خودکار اضافه شد" else "پرداخت ثبت شد؛ وضعیت سرویس بروزرسانی شد"
                busy = false
            }
        }
    }

    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        receiptUri = uri
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
        val opened = GhajarUiRules.launchBotLogin(session.botUsername, session.code) { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE))
            }.isSuccess
        }
        if (opened) {
            message = "لینک تلگرام با کد اتصال آماده باز شد؛ «Start / شروع» را بزن و برگرد. نیازی به تایپ کد نیست."
        } else {
            error = "تلگرام یا مرورگر در دسترس نیست؛ فرمان کامل اتصال را کپی کن."
        }
    }

    suspend fun beginPurchase(request: GhajarPurchaseRequest) {
        if (busy) return
        busy = true
        error = null
        message = null
        runCatching { api.purchase(request) }
            .onSuccess { result ->
                if (result.requiresPayment) {
                    pendingPurchase = result
                    paymentOptions = api.paymentOptions()
                    message = "موجودی کیف پول کافی نیست؛ روش پرداخت را انتخاب کن"
                } else {
                    pendingPurchase = null
                    paymentInit = null
                    val count = importIssuedService(result.username)
                    refreshOwnedAndNotices()
                    message = if (count > 0) "$count پیکربندی سرویس خودکار اضافه شد" else "سرویس با موفقیت ساخته شد"
                }
            }
            .onFailure { error = BrandConfig.sanitizePublicText(it.message ?: "خطا در خرید") }
        busy = false
    }

    LaunchedEffect(section, pendingPurchase) { listState.scrollToItem(0) }

    LaunchedEffect(linked, refreshKey) {
        if (!linked) return@LaunchedEffect
        busy = true
        error = null
        runCatching {
            panels = api.countries()
            if (selectedPanel == null || panels.none { it.id == selectedPanel?.id }) selectedPanel = panels.firstOrNull()
            refreshOwnedAndNotices()
        }.onFailure { error = BrandConfig.sanitizePublicText(it.message ?: "خطا در دریافت فروشگاه") }
        busy = false
    }

    LaunchedEffect(selectedPanel?.id, selectedCategory?.id, selectedTime?.days, customMode) {
        val panel = selectedPanel ?: return@LaunchedEffect
        busy = true
        runCatching {
            if (loadedPanelId != panel.id) {
                categories = api.categories(panel.id)
                timeRanges = api.timeRanges(panel.id)
                selectedCategory = null
                selectedTime = null
                customMode = false
                customQuote = null
                loadedPanelId = panel.id
            }
            products = if (customMode) emptyList() else api.products(panel.id, selectedCategory?.id, selectedTime?.days)
        }.onFailure { error = BrandConfig.sanitizePublicText(it.message ?: "خطا در دریافت پلن‌ها") }
        busy = false
    }

    LaunchedEffect(linkSession?.sessionToken) {
        val session = linkSession ?: return@LaunchedEffect
        while (System.currentTimeMillis() < session.expiresAtMillis) {
            val linkedNow = try { api.pollLink(session.sessionToken) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
            if (linkedNow) {
                linked = true
                linkSession = null
                message = "حساب با موفقیت و به‌صورت امن متصل شد"
                GhajarNotificationMonitor.refresh(context.applicationContext)
                return@LaunchedEffect
            }
            delay(2_000)
        }
        api.clearPendingLink()
        error = "زمان کد اتصال تمام شد؛ دوباره کد بساز"
        linkSession = null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 14.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ShopHeader(linked = linked, onRefresh = { refreshKey++ })
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        message?.let { text -> item { StatusCard(text, error = false, onDismiss = { message = null }) } }
        error?.let { text -> item { StatusCard(text, error = true, onDismiss = { error = null }) } }

        if (!linked) {
            item {
                LinkAccountCard(
                    session = linkSession,
                    busy = busy,
                    onBegin = {
                        scope.launch {
                            busy = true
                            error = null
                            runCatching { api.beginLink() }
                                .onSuccess { linkSession = it; openBot(it) }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
                    onOpenBot = { openBot() },
                    onCopyCommand = {
                        linkSession?.let { clipboard.setText(AnnotatedString("/link ${it.code}")) }
                        message = "فرمان اتصال کپی شد؛ آن را بدون ویرایش در ربات بفرست."
                    }
                )
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("خرید", "سرویس‌های من", "پیام‌ها").forEachIndexed { index, label ->
                        FilterChip(selected = section == index, onClick = { section = index },
                            label = { Text(label, maxLines = 1) }, modifier = Modifier.weight(1f))
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
                    OwnedServiceCard(service) {
                        scope.launch {
                            busy = true
                            runCatching { importIssuedService(service.username) }
                                .onSuccess { count ->
                                    message = if (count > 0) "$count پیکربندی اضافه شد" else "این سرویس قبلاً اضافه شده یا هنوز خروجی ندارد"
                                }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    }
                }
            }

            if (section == 0) {
            if (pendingPurchase == null) {
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
                                runCatching {
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
                    ProductCard(product, enabled = !busy) {
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
                item { SectionTitle("۳. پرداخت", "برای همین سفارش یک روش پرداخت انتخاب کن") }
                item { PaymentSummary(purchase) }
                paymentOptions?.methods?.let { methods ->
                    items(methods, key = { "pay:${it.id}" }) { method ->
                        PaymentMethodCard(method, purchase.amountDue) {
                            scope.launch {
                                if (method.directUrl?.contains("t.me/") == true) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(method.directUrl)))
                                    return@launch
                                }
                                if (purchase.amountDue < method.minimum || (method.maximum > 0 && purchase.amountDue > method.maximum)) {
                                    error = "مبلغ این خرید خارج از محدودهٔ روش ${method.label} است"
                                    return@launch
                                }
                                busy = true
                                runCatching { api.beginPayment(method.id, purchase.amountDue, purchase.username) }
                                    .onSuccess { result ->
                                        paymentInit = result
                                        receiptUri = null
                                        if (result.url != null) openCheckout(result.url)
                                        else message = result.message.ifBlank { "درخواست پرداخت ثبت شد" }
                                    }
                                    .onFailure { error = it.message }
                                busy = false
                            }
                        }
                    }
                }
            }

            paymentInit?.takeIf { it.kind == "carttocart" }?.let { payment ->
                item {
                    CardToCardCard(
                        payment = payment,
                        receiptSelected = receiptUri != null,
                        onCopyCard = {
                            clipboard.setText(AnnotatedString(payment.cardNumber.orEmpty()))
                            message = "شماره کارت کپی شد"
                        },
                        onPickReceipt = { receiptPicker.launch("image/*") },
                        onUpload = {
                            val uri = receiptUri ?: return@CardToCardCard
                            scope.launch {
                                busy = true
                                runCatching { api.uploadReceipt(payment.orderId, uri) }
                                    .onSuccess { message = it; receiptUri = null }
                                    .onFailure { error = it.message }
                                busy = false
                            }
                        }
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching { api.trialOptions() }
                                .onSuccess { trialOptions = it }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
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
                            scope.launch {
                                busy = true
                                runCatching { api.createTrial(panel.code, customUsername) }
                                    .onSuccess { service ->
                                        val count = api.importServiceOnce(store, service)
                                        refreshOwnedAndNotices()
                                        trialOptions = null
                                        message = if (count > 0) "سرویس تست ساخته و خودکار اضافه شد" else "سرویس تست ساخته شد"
                                    }
                                    .onFailure { error = it.message }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تست ${panel.name}") }
                }
            }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
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
                Button(enabled = !busy && confirmationPrice != null &&
                    (selectedPanel?.usernameRequired != true || customUsername.isNotBlank()),
                    onClick = {
                        confirmation = null
                        scope.launch { beginPurchase(request.copy(customUsername = customUsername, note = customNote, discountCode = discountCode)) }
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
private fun LinkAccountCard(session: GhajarLinkSession?, busy: Boolean, onBegin: () -> Unit, onOpenBot: () -> Unit, onCopyCommand: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B211C)),
        border = BorderStroke(1.dp, Color(0x66D6B45F))
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Security, null, tint = Color(0xFFD6B45F), modifier = Modifier.size(40.dp))
            Text("اتصال امن حساب", color = Color(0xFFF7F2E8), fontWeight = FontWeight.Bold)
            Text("توکن فقط به‌صورت AES-GCM داخل Android Keystore ذخیره می‌شود.", color = Color(0xFF91BCC7), textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            if (session == null) {
                Button(onClick = onBegin, enabled = !busy) { Icon(Icons.Filled.Link, null); Spacer(Modifier.width(7.dp)); Text("اتصال با تلگرام") }
            } else {
                TextButton(onClick = onOpenBot) {
                    Text(session.code, style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFFD6B45F), fontWeight = FontWeight.Black)
                }
                Text("۱. «تأیید اتصال در تلگرام» را بزن.\n۲. پایین چت ربات، «شروع / Start» را بزن.\n۳. به قاجار برگرد؛ حساب خودکار متصل می‌شود.",
                    color = Color(0xFFF7F2E8), textAlign = TextAlign.Center)
                Text("کد از قبل داخل لینک است؛ آن را تایپ یا اصلاح نکن.", color = Color(0xFF91BCC7), textAlign = TextAlign.Center)
                Button(onClick = onOpenBot) { Icon(Icons.Filled.OpenInNew, null); Spacer(Modifier.width(7.dp)); Text("تأیید اتصال در تلگرام") }
                TextButton(onClick = onCopyCommand) { Text("کپی فرمان کامل اتصال") }
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
    var expanded by remember(product.id) { mutableStateOf(false) }
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ShoppingCart, null, tint = Color(0xFFD6B45F))
                Spacer(Modifier.width(8.dp))
                Text(product.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(product.price?.let { if (it == 0L) "رایگان" else "${formatPrice(it)} تومان" } ?: "قیمت در دسترس نیست",
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(product.trafficGb?.let { "$it گیگ" }, product.days?.let { "$it روز" }).joinToString(" · "))
            if (product.description.isNotBlank()) {
                Text(product.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "بستن توضیحات" else "جزئیات سرویس") }
            }
            Button(onClick = onBuy, enabled = enabled && product.price != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("انتخاب سرویس") }
        }
    }
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
private fun PaymentSummary(purchase: GhajarPurchaseResult) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF143A32))) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("پرداخت مبلغ کسری", color = Color(0xFFD6B45F), fontWeight = FontWeight.ExtraBold)
            Text("موجودی: ${formatPrice(purchase.balance)} تومان", color = Color.White)
            Text("قیمت سرویس: ${formatPrice(purchase.price)} تومان", color = Color.White)
            Text("قابل پرداخت: ${formatPrice(purchase.amountDue)} تومان", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PaymentMethodCard(method: GhajarPaymentMethod, amount: Long, onClick: () -> Unit) {
    val allowed = amount >= method.minimum && (method.maximum <= 0 || amount <= method.maximum)
    Card(onClick = onClick, enabled = allowed, modifier = Modifier.fillMaxWidth()) {
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
private fun CardToCardCard(
    payment: GhajarPaymentInit,
    receiptSelected: Boolean,
    onCopyCard: () -> Unit,
    onPickReceipt: () -> Unit,
    onUpload: () -> Unit
) {
    Card(border = BorderStroke(1.dp, Color(0xFFD6B45F)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Filled.ReceiptLong, null, tint = Color(0xFFD6B45F), modifier = Modifier.size(38.dp))
            Text("کارت‌به‌کارت", fontWeight = FontWeight.ExtraBold)
            Text(payment.cardNumber.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(payment.cardHolder.orEmpty())
            Text("مبلغ دقیق: ${formatPrice(payment.amount)} تومان", color = Color(0xFF0E8067), fontWeight = FontWeight.Bold)
            TextButton(onClick = onCopyCard) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("کپی شماره کارت") }
            OutlinedButton(onClick = onPickReceipt, modifier = Modifier.fillMaxWidth()) { Text(if (receiptSelected) "تغییر عکس رسید" else "انتخاب عکس رسید") }
            Button(onClick = onUpload, enabled = receiptSelected && payment.orderId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("ارسال رسید برای ادمین") }
            Text("حداکثر حجم عکس ۸ مگابایت است.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusCard(text: String, error: Boolean, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    }
}

private fun formatPrice(price: Long): String = NumberFormat.getIntegerInstance(Locale("fa", "IR")).format(price)

package net.gozar.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val deliveryRevision by checkoutModel.revision
    val delivery by checkoutModel.delivery
    val receiptSent by checkoutModel.receiptSent
    val walletTopUp by checkoutModel.walletTopUp
    var walletAmount by remember { mutableStateOf("") }
    val requestedUrl by checkoutModel.openUrl

    var linked by remember { mutableStateOf(api.isLinked) }
    GhajarNotificationPermissionEffect(linked && active)
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
    var section by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val sectionState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var signOutConfirm by remember { mutableStateOf(false) }
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
    // The panel's plan list with no category/duration filter, kept so
    // returning to "all plans" does not re-hit the server.
    var unfilteredProducts by remember { mutableStateOf<List<GhajarProduct>>(emptyList()) }
    var owned by remember { mutableStateOf<List<GhajarOwnedService>>(emptyList()) }
    // Whether the owned list has been fetched at all, as opposed to being
    // empty. A renew request that names an invoice id has to wait for the
    // list to translate it, and "no services" and "not asked yet" are the
    // same empty list - telling them apart is what stops that wait from
    // being forever on an account with nothing in it.
    var ownedLoaded by remember { mutableStateOf(false) }
    var notices by remember { mutableStateOf<List<GhajarNotice>>(emptyList()) }
    var loadedPanelId by remember { mutableStateOf<String?>(null) }

    var customMode by remember { mutableStateOf(false) }
    var comparePlans by remember { mutableStateOf(false) }
    var customTraffic by remember { mutableStateOf("") }
    var customDays by remember { mutableStateOf("") }
    var customQuote by remember { mutableStateOf<GhajarCustomQuote?>(null) }
    var customUsername by remember { mutableStateOf("") }
    var customNote by remember { mutableStateOf("") }
    var discountCode by remember { mutableStateOf("") }

    val checkoutVisible by checkoutModel.checkoutVisible
    val pendingPurchase = checkoutModel.purchase.value.takeIf { checkoutVisible }
    val serverPending by checkoutModel.pendingPayments
    val paymentOptions by checkoutModel.methods
    val paymentInit by checkoutModel.payment
    var receiptUri by checkoutModel.receipt
    var trialOptions by remember { mutableStateOf<GhajarTrialOptions?>(null) }
    var renewUsername by remember { mutableStateOf<String?>(null) }

    // ownedServices() and notices() are two independent HTTP round trips with
    // no data dependency between them; awaiting them one after the other
    // doubles the worst-case wait (each already pays up to CONNECT_TIMEOUT +
    // READ_TIMEOUT on its own, twice if the direct attempt fails and the
    // local-proxy retry kicks in) for no reason. Running them concurrently
    // caps the wait at whichever one is slower instead of their sum.
    suspend fun refreshOwnedAndNotices() = coroutineScope {
        val ownedDeferred = async { api.ownedServices() }
        val noticesDeferred = async { api.notices() }
        owned = ownedDeferred.await()
        ownedLoaded = true
        notices = noticesDeferred.await()
    }

    val renewRequest by GhajarRenewRequest.requested.collectAsState()
    LaunchedEffect(renewRequest, active, owned, ownedLoaded) {
        val requested = renewRequest ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        // A notice may name the service by its invoice id instead of its
        // username - every warning written before the server was updated does,
        // and those rows sit in the inbox for days. The owned list carries both,
        // so the id is translated here rather than sent to a server that can
        // only answer "not found". Waiting for that list is deliberate: acting
        // on an id before it arrives is the 404 this fixes.
        val username = when {
            owned.any { it.username == requested } -> requested
            !ownedLoaded -> return@LaunchedEffect
            else -> owned.firstOrNull { it.invoiceId == requested }?.username ?: requested
        }
        section = 1
        renewUsername = username
        GhajarRenewRequest.consume()
    }

    val checkout = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkoutModel.checkPayment()
        refreshKey++
    }

    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        receiptUri = uri
        if (uri != null) checkoutModel.receiptSent.value = false
        if (uri != null) storeResult { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun openCheckout(url: String) {
        // The invoice is durable before launch; the payment page must stay in the
        // Ghajar task and a renderer crash must never eject the user to home.
        GhajarLog.d("Payment", "openCheckout requested")
        val launch: () -> Unit = {
            val intent = StoreLinkRouter.securePaymentIntent(context, url)
                ?: throw IllegalArgumentException("untrusted payment URL")
            checkout.launch(intent)
        }
        storeResult { launch() }.onFailure {
            GhajarLog.e("Payment", "openCheckout failed: ${it.javaClass.simpleName}: ${it.message}")
            error = "صفحهٔ پرداخت امن باز نشد؛ «ادامهٔ همین پرداخت» را دوباره بزن."
        }
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

    LaunchedEffect(deliveryRevision) {
        if (deliveryRevision > 0) storeResult { refreshOwnedAndNotices() }
    }
    LaunchedEffect(requestedUrl, active) {
        if (active && requestedUrl != null) {
            // Capture before clearing the observable event: delegated reads see
            // the new null immediately and previously crashed the Activity.
            val url = requestedUrl ?: return@LaunchedEffect
            checkoutModel.openUrl.value = null
            openCheckout(url)
        }
    }
    LaunchedEffect(active, paymentInit?.orderId, lifecycle) {
        if (!active || paymentInit == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // The moment that matters is the return from the payment page, and
            // this block restarts on every resume - so ask often at first and
            // only then back off, instead of making the user stare at a spinner
            // for a flat fifteen seconds after they have already paid.
            val ladder = longArrayOf(2_000, 2_000, 3_000, 4_000, 6_000, 9_000)
            var step = 0
            while (true) {
                checkoutModel.checkPayment()
                delay(ladder.getOrElse(step) { 15_000 })
                step++
            }
        }
    }

    LaunchedEffect(linked, active, lifecycle) {
        if (linked && active) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { checkoutModel.refreshPending(); delay(30_000) }
        }
    }

    LaunchedEffect(section, checkoutVisible) {
        listState.scrollToItem(0)
        if (section == 3 && linked) checkoutModel.refreshMethods()
    }

    LaunchedEffect(linked, refreshKey) {
        if (!linked) return@LaunchedEffect
        busy = true
        error = null
        // countries() and refreshOwnedAndNotices() are independent too - the
        // owned/notices fetch doesn't need the panel list at all.
        storeResult {
            coroutineScope {
                val panelsDeferred = async { api.countries() }
                val ownedNoticesDeferred = async { refreshOwnedAndNotices() }
                panels = panelsDeferred.await()
                if (selectedPanel == null || panels.none { it.id == selectedPanel?.id }) selectedPanel = panels.firstOrNull()
                ownedNoticesDeferred.await()
            }
        }.onFailure { error = BrandConfig.sanitizePublicText(it.message ?: "خطا در دریافت فروشگاه") }
        busy = false
    }

    // Loading the store used to take three sequential server round-trips
    // before a single plan appeared: countries, then categories+timeRanges,
    // then products. The third wave waited on the second for no reason - the
    // view opens with no category and no duration selected, so the product
    // list it needs is the unfiltered one, which only depends on the panel.
    // Fetching it alongside the filters removes a whole round-trip from the
    // critical path.
    LaunchedEffect(selectedPanel?.id) {
        val panel = selectedPanel ?: return@LaunchedEffect
        loadedPanelId = null
        selectedCategory = null
        selectedTime = null
        customMode = false
        customQuote = null
        products = emptyList()
        busy = true
        try {
            // A tunnel coming up or going down mid-request fails the whole
            // wave once and then works. One quiet retry costs a second and
            // removes most of the "the shop just errors" reports; only the
            // second failure is worth telling the user about.
            suspend fun load() = storeResult {
                coroutineScope {
                    val categoriesDeferred = async { api.categories(panel.id) }
                    val timeRangesDeferred = async { api.timeRanges(panel.id) }
                    val productsDeferred = async { api.products(panel.id, null, null) }
                    categories = categoriesDeferred.await()
                    timeRanges = timeRangesDeferred.await()
                    products = productsDeferred.await()
                    unfilteredProducts = products
                    loadedPanelId = panel.id
                }
            }
            load().onFailure {
                delay(900)
                load().onFailure { second -> error = GhajarCommerceRules.publicMessage(second) }
            }
        } finally { busy = false }
    }
    LaunchedEffect(loadedPanelId, selectedCategory?.id, selectedTime?.days, customMode) {
        val panel = selectedPanel ?: return@LaunchedEffect
        if (loadedPanelId != panel.id) return@LaunchedEffect
        // The unfiltered list was already fetched with the filters above, so
        // the opening view costs no extra request; only an actual filter
        // choice goes back to the server.
        if (!customMode && selectedCategory == null && selectedTime == null) {
            products = unfilteredProducts
            return@LaunchedEffect
        }
        busy = true
        try {
            storeResult {
                products = if (customMode) emptyList() else api.products(panel.id, selectedCategory?.id, selectedTime?.days)
            }.onFailure { error = GhajarCommerceRules.publicMessage(it) }
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

    // Published by the notice monitor, which polls the same feed that carries
    // the notices - so the shop's state arrives without a call of its own.
    val shopOpen by GhajarShopStatus.enabled.collectAsState()
    val shopClosedMessage by GhajarShopStatus.message.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 14.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "shop-block-0") {
            ShopHeader(linked = linked, onRefresh = { refreshKey++ })
        }

        // The shop switched off by the operator. A standing strip rather than
        // a dismissible notice, because it is a condition and not an event -
        // it should be true on screen for exactly as long as it is true on the
        // server. Nothing below it is hidden: the plans and the services stay
        // readable, since being unable to buy is not a reason to be unable to
        // look at what you already own.
        if (!shopOpen) {
            item(key = "shop-closed") {
                Slab(accent = ghajarColors.warning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = ghajarColors.warning,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(GhajarSpacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "فروشگاه موقتاً غیرفعال است",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ghajarColors.textPrimary
                            )
                            if (shopClosedMessage.isNotBlank()) {
                                Text(
                                    shopClosedMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ghajarColors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
        if (busy || checkoutBusy) item(key = "shop-block-1") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        message?.let { text -> item(key = "shop-block-2") { StatusCard(text, error = false, onDismiss = { message = null }) } }
        error?.let { text ->
            item(key = "shop-block-3") {
                // A dead error message was the whole complaint: some tunnels
                // break the call, and the page then offered nothing but the
                // text. It offers a retry now, and says plainly when the plans
                // underneath it are the last ones that did load rather than
                // pretending they are fresh.
                StatusCard(
                    text,
                    error = true,
                    onDismiss = { error = null },
                    onRetry = { error = null; refreshKey++ },
                    footnote = if (unfilteredProducts.isNotEmpty())
                        "پلن‌های پایین آخرین فهرستی است که دریافت شده؛ ممکن است قیمت‌ها تازه نباشد."
                    else null
                )
            }
        }

        if (!linked) {
            item(key = "shop-block-4") {
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
                            } catch (failure: IOException) {
                                // A real network cause (DNS/TLS/timeout/refused, already
                                // retried once through the active tunnel's local proxy in
                                // GhajarStoreApi) gets its own specific, logged message
                                // instead of a blanket "check your internet" regardless of
                                // what actually failed.
                                error = GhajarCommerceRules.publicMessage(failure)
                            } catch (failure: Exception) {
                                error = failure.message ?: "ساخت کد اتصال انجام نشد؛ دوباره تلاش کن."
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
            item(key = "shop-header") {
                ScreenHeader(
                    title = Strings.get(store.lang.value, "shop"),
                    context = Strings.get(store.lang.value, "shop_header_sub")
                ) {
                    // Sign out of the shop.
                    //
                    // There was no way off an account once this phone was
                    // linked: the pairing code is one-way, so a second person
                    // on the same handset, or anyone who linked the wrong
                    // Telegram account, was stuck with it forever. Confirmed
                    // first, because the way back in is a code from the bot,
                    // not a password anyone can retype on the spot.
                    IconButton(onClick = { signOutConfirm = true }) {
                        Icon(
                            Icons.Filled.LinkOff,
                            contentDescription = "خروج از حساب فروشگاه",
                            tint = ghajarColors.textSecondary
                        )
                    }
                }
            }
            item(key = "shop-block-5") {
                StoreSectionTabs(
                    section = section,
                    noticeCount = notices.size,
                    pendingCount = serverPending.size,
                    serviceCount = owned.size,
                    onSelect = { section = it }
                )
            }
            item(key = "shop-status-center") {
                OrderStatusCenter(
                    balanceText = paymentOptions?.let { "${formatPrice(it.balance)} ${it.currency}" },
                    pendingCount = serverPending.size,
                    activeServiceCount = owned.count { it.status.lowercase() in setOf("active", "enabled", "فعال") },
                    totalServiceCount = owned.size,
                    onOpenWallet = { section = 3 },
                    onOpenPending = { section = 0 },
                    onOpenServices = { section = 1 }
                )
            }
            if (section == 4) {
                item(key = "shop-block-6") { sectionState.SaveableStateProvider("tickets") { GhajarTickets(api) } }
            }
            if (section == 5) item(key = "shop-block-7") { GhajarTransactionHistory(api, refreshKey + deliveryRevision, store.lang.value) }
            // An unfinished payment, shown on every section rather than only on
            // the two it used to hide behind.
            //
            // The owner could not find the continue button at all, and this is
            // why: the cards were drawn under the buy tab and the wallet tab,
            // so an order abandoned halfway was invisible from the four other
            // places a person actually lands. Money already committed is the
            // most urgent thing on this screen wherever you are standing, and
            // the one thing nobody should have to go looking for.
            run {
                val entries = serverPending.toMutableList()
                paymentInit?.let { local ->
                    if (entries.none { it.orderId == local.orderId }) entries.add(0,
                        GhajarPendingPayment(local.orderId, local.method, local.methodLabel.ifBlank { "پرداخت" },
                            local.amount, local.expiresAt, "pending"))
                }
                items(entries, key = { "pending:${it.orderId}" }) { item ->
                    GhajarPendingPaymentCard(item, checkoutBusy, store.lang.value,
                        onResume = { checkoutModel.resumePayment(item); section = 0 },
                        onCancel = { checkoutModel.cancelPayment(item.orderId) })
                }
            }
            if (section == 3) {
                item(key = "shop-block-9") {
                    val walletColors = ghajarColors
                    Slab(padding = 18.dp, spacing = GhajarSpacing.md) {
                        Rail("کیف پول قاجار")
                        // The balance is the one number this page exists for,
                        // so it takes the brightest brand tone and the largest
                        // type on the screen - nothing else competes with it.
                        Text(
                            paymentOptions?.let { formatPrice(it.balance) } ?: "…",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = walletColors.highlight
                        )
                        Text(
                            paymentOptions?.currency ?: "در حال دریافت موجودی",
                            style = MaterialTheme.typography.labelMedium,
                            color = walletColors.textSecondary
                        )
                        // Four amounts cover almost every top-up; typing is
                        // still there for the rest.
                        val presets = listOf(50_000L, 100_000L, 200_000L, 500_000L)
                        Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                            presets.forEach { amount ->
                                val on = walletAmount == amount.toString()
                                Text(
                                    formatPrice(amount),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    color = if (on) walletColors.onPrimary else walletColors.textSecondary,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(GhajarRadius.pill))
                                        .background(if (on) walletColors.primary else walletColors.secondaryCard)
                                        .clickable { walletAmount = amount.toString() }
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                        SkinField(
                            value = walletAmount,
                            onValueChange = { walletAmount = asciiDigits(it).filter(Char::isDigit).take(12) },
                            label = "مبلغ شارژ به تومان",
                            placeholder = "مثلاً ۱۰۰۰۰۰",
                            helper = "شارژ پس از تأیید پنل به موجودی اضافه می‌شود؛ برای شارژ سرویس جدید ساخته نمی‌شود.",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        PillButton(
                            "شارژ کیف پول",
                            { checkoutModel.topUp(walletAmount.toLongOrNull() ?: 0); section = 0 },
                            enabled = !checkoutBusy && (walletAmount.toLongOrNull() ?: 0) > 0,
                            icon = Icons.Filled.AccountBalanceWallet
                        )
                        GhostPill(
                            "بروزرسانی موجودی",
                            checkoutModel::refreshMethods,
                            enabled = !checkoutBusy,
                            icon = Icons.Filled.Refresh
                        )
                    }
                }
            }
            if (section == 2) {
                if (notices.isEmpty() && !busy) item(key = "shop-block-10") { Text("پیام تازه‌ای ندارید", modifier = Modifier.padding(16.dp)) }
                items(notices, key = { "notice:${it.id}" }) { NoticeCard(it) }
            }

            if (section == 1) {
                item(key = "shop-block-11") { SectionTitle("سرویس‌های من", "برای دریافت خودکار کانفیگ روی سرویس بزن") }
                if (owned.isEmpty() && !busy) item(key = "shop-block-12") { Text("هنوز سرویسی برای این حساب ثبت نشده است.") }
                items(owned, key = { "owned:${it.username}" }) { service ->
                    OwnedServiceCard(service, onImport = { checkoutModel.importOwned(service.username) },
                        onRenew = { renewUsername = service.username })
                }
            }

            if (section == 0) {
            if (pendingPurchase == null) {
            item(key = "shop-block-13") {
                GhostPill(
                    "دریافت سرویس تست رایگان",
                    {
                        scope.launch {
                            busy = true
                            storeResult { api.trialOptions() }
                                .onSuccess { trialOptions = it }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
                    enabled = !busy && !checkoutBusy,
                    icon = Icons.Filled.CardGiftcard,
                    accent = ghajarColors.premium
                )
            }
            trialOptions?.let { options ->
                if (!options.canRequest) item(key = "shop-block-14") { Text("سهمیهٔ سرویس تست در دسترس نیست", color = MaterialTheme.colorScheme.error) }
                items(options.panels, key = { "trial:${it.code}" }) { panel ->
                    Slab(spacing = 0.dp) {
                        SlabRow(
                            title = panel.name,
                            subtitle = panel.remaining?.let { "باقی‌مانده: $it" } ?: "سرویس آزمایشی",
                            icon = Icons.Filled.CardGiftcard,
                            accent = ghajarColors.premium,
                            chevron = true,
                            enabled = options.canRequest && (panel.remaining == null || panel.remaining > 0) && !checkoutBusy,
                            onClick = {
                                checkoutModel.trial(panel.code, customUsername)
                                trialOptions = null
                            }
                        )
                    }
                }
            }
            item(key = "shop-block-15") { SectionTitle("۱. انتخاب سرویس", "قیمت و موجودی مستقیماً از پنل دریافت می‌شود") }
            if (panels.isEmpty() && !busy) {
                item(key = "shop-block-16") {
                    SkinEmpty(
                        title = "هیچ سرویسی از پنل دریافت نشد",
                        hint = "چند لحظه بعد دوباره بررسی کن؛ اگر ادامه داشت، از پشتیبانی بپرس.",
                        icon = Icons.Filled.ShoppingCart,
                        actionText = "تلاش دوباره",
                        onAction = { refreshKey++ }
                    )
                }
            }
            if (panels.isNotEmpty()) {
                item(key = "shop-block-17") {
                    ServiceTypeGrid(
                        items = panels,
                        selected = selectedPanel,
                        label = { it.name },
                        icon = { panelIcon(it.name) },
                        onSelect = { selectedPanel = it }
                    )
                }
            }
            if (categories.isNotEmpty()) {
                item(key = "shop-block-18") {
                    ChipFlowRow(
                        allLabel = "همه دسته‌ها",
                        items = categories,
                        selected = selectedCategory,
                        label = { it.name },
                        onSelect = { selectedCategory = it }
                    )
                }
            }
            if (timeRanges.isNotEmpty()) {
                item(key = "shop-block-19") {
                    ChipFlowRow(
                        allLabel = "همه مدت‌ها",
                        items = timeRanges,
                        selected = selectedTime,
                        label = { it.name },
                        onSelect = { selectedTime = it }
                    )
                }
            }

            selectedPanel?.takeIf { it.custom }?.let {
                item(key = "shop-block-20") {
                    // Two mutually exclusive modes, so the skin's sliding
                    // segmented control rather than two independent chips.
                    SlidingSegments(
                        labels = listOf("پلن‌های آماده", "سرویس سفارشی"),
                        selected = if (customMode) 1 else 0,
                        onSelect = { customMode = it == 1 }
                    )
                }
            }

            if (customMode) {
                item(key = "shop-block-21") {
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
                if (products.size > 1) {
                    item(key = "shop-block-compare") {
                        GhostPill("مقایسهٔ پلن‌ها", { comparePlans = true })
                    }
                }
                // The cheapest gigabyte in the visible list, computed here so
                // the badge on the card is a fact about these plans rather
                // than a label from the panel.
                val bestValueId = products
                    .mapNotNull { p ->
                        val price = p.price ?: return@mapNotNull null
                        val gb = p.trafficGb ?: return@mapNotNull null
                        if (price <= 0L || gb <= 0.0) null else p.id to (price / gb)
                    }
                    .minByOrNull { it.second }
                    ?.first
                    ?.takeIf { products.size > 1 }
                if (products.isEmpty() && !busy) {
                    item(key = "shop-plans-empty") {
                        SkinEmpty(
                            title = "پلنی با این فیلترها پیدا نشد",
                            hint = "دستهٔ دیگری انتخاب کن یا فیلتر مدت را بردار.",
                            icon = Icons.Filled.ShoppingCart,
                            actionText = if (selectedCategory != null || selectedTime != null) "برداشتن فیلترها" else null,
                            onAction = if (selectedCategory != null || selectedTime != null) {
                                { selectedCategory = null; selectedTime = null }
                            } else null
                        )
                    }
                }
                if (products.isEmpty() && busy) {
                    item(key = "shop-plans-loading") { SkinLoading("در حال دریافت پلن‌ها از پنل…") }
                }
                items(products, key = { "product:${it.id}" }) { product ->
                    ProductCard(
                        product,
                        enabled = !busy && !checkoutBusy,
                        bestValue = product.id == bestValueId
                    ) {
                        confirmationTitle = product.name
                        confirmationPrice = product.price
                        confirmation = GhajarPurchaseRequest(countryId = product.countryId, serviceId = product.id)
                    }
                }
            }
            if (comparePlans) {
                item(key = "shop-block-compare-dialog") {
                    PlanComparisonDialog(products) { comparePlans = false }
                }
            }

            selectedPanel?.let { panel ->
                if (customMode) {
                    item(key = "shop-block-22") {
                        PillButton(
                            "خرید سرویس سفارشی",
                            {
                                confirmationTitle = "سرویس سفارشی · $customTraffic گیگ · $customDays روز"
                                confirmationPrice = customQuote?.price
                                confirmation = GhajarPurchaseRequest(countryId = panel.id,
                                    customTrafficGb = customTraffic.toIntOrNull(), customTimeDays = customDays.toIntOrNull())
                            },
                            enabled = customQuote?.price != null && !busy,
                            icon = Icons.Filled.ShoppingCart
                        )
                    }
                }
            }
            }

            pendingPurchase?.takeIf { it.requiresPayment }?.let { purchase ->
                item(key = "shop-block-23") { SectionTitle("۳. پرداخت", "فاکتور روی گوشی حفظ می‌شود؛ وضعیت را از سرور بررسی کن") }
                item(key = "shop-block-24") { PaymentSummary(purchase, walletTopUp, paymentInit?.takeIf { GhajarCommerceRules.cardPayment(it.kind, it.cardNumber) }?.amount) }
                if (paymentInit == null) {
                    if (paymentOptions == null) item(key = "shop-block-25") {
                        if (checkoutBusy) SkinLoading("در حال دریافت روش‌های پرداخت…")
                        else GhostPill(
                            "دریافت روش‌های پرداخت",
                            checkoutModel::refreshMethods,
                            icon = Icons.Filled.CreditCard
                        )
                    }
                    items(paymentOptions?.methods.orEmpty(), key = { "pay:${it.id}" }) { method ->
                        PaymentMethodCard(method, purchase.amountDue, !checkoutBusy) { checkoutModel.beginPayment(method) }
                    }
                }
            }
            paymentInit?.takeIf { checkoutVisible }?.let { payment ->
                if (GhajarCommerceRules.cardPayment(payment.kind, payment.cardNumber)) item(key = "shop-block-26") {
                    CardToCardCard(payment, receiptUri, checkoutBusy, receiptSent,
                        onPickReceipt = { receiptPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                        onUpload = checkoutModel::uploadReceipt)
                }
                item(key = "shop-block-27") {
                    Slab(spacing = GhajarSpacing.md) {
                        SlabRow(
                            title = "کد پیگیری",
                            value = payment.orderId,
                            icon = Icons.Filled.ReceiptLong,
                            accent = ghajarColors.info
                        )
                        // While the app is waiting on the panel it says so, in
                        // place of a dead button - the old screen gave no sign
                        // that anything was happening between taps.
                        if (checkoutBusy) {
                            Text(
                                "در حال بررسی وضعیت پرداخت…",
                                style = MaterialTheme.typography.labelLarge,
                                color = ghajarColors.highlight
                            )
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = ghajarColors.primary,
                                trackColor = ghajarColors.border
                            )
                        } else {
                            payment.url?.let { url ->
                                PillButton(
                                    "ادامهٔ همین پرداخت",
                                    { openCheckout(url) },
                                    icon = Icons.Filled.OpenInNew
                                )
                            }
                            GhostPill(
                                if (checkoutModel.deliveryFailed) "تلاش مجدد برای تحویل سرویس"
                                else "پرداخت کردم؛ بررسی و دریافت سرویس",
                                checkoutModel::checkPayment,
                                icon = Icons.Filled.Autorenew
                            )
                        }
                        if (checkoutModel.deliveryFailed) {
                            Text(
                                "پرداخت تأیید شده؛ تحویل سرویس یک‌بار ناموفق بود. دوباره پرداخت نکن، فقط تلاش مجدد را بزن.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ghajarColors.error
                            )
                        }
                        Text(
                            "وضعیت پرداخت به‌صورت خودکار بررسی می‌شود. بستن صفحه به معنی لغو تراکنش نیست؛ در صورت پرداخت، دوباره واریز نکن.",
                            style = MaterialTheme.typography.labelSmall,
                            color = ghajarColors.textMuted
                        )
                    }
                }
            }
            if (pendingPurchase != null) item(key = "shop-block-28") {
                TextButton(onClick = checkoutModel::leaveInvoice, enabled = !checkoutBusy) { Text("بازگشت به محصولات") }
            }

            }

            // The escape hatch to the full web panel used to sit at the top,
            // between the tabs and the first plan. It is a fallback, not a
            // destination, so it goes last.
            item(key = "shop-block-8") {
                GhostPill(
                    if (section == 4) "پنل کامل پشتیبانی و پیوست‌ها" else "پنل کامل خدمات حساب",
                    {
                        // The panel authenticates with Telegram's initData,
                        // which a browser never has, so this used to open a
                        // page that did not know whose account it was. A
                        // one-time ticket is fetched first and carried in the
                        // URL; the page exchanges it for this same session and
                        // strips it from the address bar. If the server has not
                        // been updated yet the ticket is null and the plain URL
                        // opens exactly as before.
                        scope.launch {
                            val ticket = api.webPanelTicket()
                            val fragment = if (section == 4) "#/tickets" else "#/account"
                            val url = if (ticket != null) {
                                BrandConfig.STORE_URL + "?ticket=" + ticket + fragment
                            } else BrandConfig.STORE_URL + fragment
                            StoreLinkRouter.browserIntent(context, url)
                                ?.let { context.startActivity(it) }
                        }
                    },
                    Modifier.fillMaxWidth()
                )
            }
        }

        item(key = "shop-block-29") { Spacer(Modifier.height(28.dp)) }
    }
    delivery?.let { result ->
        GhajarDeliveryDialog(result, onDismiss = { checkoutModel.delivery.value = null },
            onRetry = {
                if (paymentInit != null && pendingPurchase?.username == result.service.username) checkoutModel.checkPayment()
                else checkoutModel.importOwned(result.service.username)
            }, busy = checkoutBusy, failed = checkoutModel.deliveryFailed)
    }
    if (signOutConfirm) {
        AlertDialog(
            onDismissRequest = { signOutConfirm = false },
            title = { Text("خروج از حساب فروشگاه") },
            text = {
                Text(
                    "این گوشی از حساب فعلی جدا می‌شود و می‌توانی با حساب دیگری وارد شوی.\n\n" +
                        "سرورها و کانفیگ‌هایی که الان روی گوشی داری پاک نمی‌شوند و اتصالت قطع نمی‌شود؛ " +
                        "فقط خرید، تمدید، کیف پول و اعلان‌های فروشگاه تا ورود دوباره در دسترس نیستند.\n\n" +
                        "برای ورود دوباره به یک کد تازه از ربات نیاز داری."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    signOutConfirm = false
                    api.signOut()
                    // Everything the old account put on this screen goes with
                    // it, in one pass, rather than being left on screen under
                    // a sign-in card that now says "not linked".
                    linked = false
                    linkSession = null
                    linkState = GhajarLinkState.PENDING
                    panels = emptyList(); selectedPanel = null
                    categories = emptyList(); selectedCategory = null
                    timeRanges = emptyList(); selectedTime = null
                    products = emptyList(); unfilteredProducts = emptyList()
                    owned = emptyList(); ownedLoaded = false; notices = emptyList()
                    trialOptions = null; loadedPanelId = null
                    section = 0
                    checkoutModel.reset()
                    message = "از حساب فروشگاه خارج شدی. برای ورود با حساب دیگر، کد تازه بگیر."
                    error = null
                }) { Text("خروج") }
            },
            dismissButton = { TextButton(onClick = { signOutConfirm = false }) { Text("انصراف") } }
        )
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
    renewUsername?.let { username ->
        RenewServiceDialog(
            username = username,
            api = api,
            onDismiss = { renewUsername = null },
            onRenewed = {
                renewUsername = null
                scope.launch { storeResult { refreshOwnedAndNotices() } }
            }
        )
    }
}

private fun asciiDigits(value: String): String = GhajarUiRules.asciiDigits(value)

@Composable
private fun ShopHeader(linked: Boolean, onRefresh: () -> Unit) {
    // The 80dp illustration is gone: it took the first eighty pixels of the
    // store and said nothing the two lines beside it did not already say.
    val c = ghajarColors
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.xs)) {
            Text(
                "خزانهٔ قاجار",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // A state pip instead of a picture: it says the one thing the
                // header is actually reporting.
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (linked) c.good else c.warning)
                )
                Text(
                    if (linked) "حساب متصل و همگام است" else "برای خرید، حساب ربات را یک‌بار متصل کن",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
            }
        }
        if (linked) IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "بروزرسانی") }
    }
}

@Composable
private fun LinkAccountCard(session: GhajarLinkSession?, busy: Boolean, state: GhajarLinkState, verification: Boolean,
    checking: Boolean, remainingSeconds: Int, onBegin: () -> Unit, onOpenBot: () -> Unit,
    onCheck: () -> Unit, onCancel: () -> Unit, onCopyCommand: () -> Unit) {
    val c = ghajarColors
    Card(
        shape = RoundedCornerShape(GhajarRadius.lg),
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.border)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(GhajarSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The accent square is the shared "section icon" shape used by the
            // settings cards, so this reads as the same design system.
            Box(
                Modifier
                    .clip(RoundedCornerShape(GhajarRadius.sm))
                    .background(c.primary.copy(alpha = 0.14f))
                    .padding(GhajarSpacing.md)
            ) {
                Icon(Icons.Filled.Security, null, tint = c.primary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(GhajarSpacing.md))
            Text("اتصال امن حساب", color = c.textPrimary, fontWeight = FontWeight.Bold)
            Text(
                "ورود با ربات؛ اطلاعات اتصال در گوشی رمزگذاری می‌شود.",
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            if (session == null) {
                Button(onClick = onBegin, enabled = !busy) { Icon(Icons.Filled.Link, null); Spacer(Modifier.width(7.dp)); Text("اتصال با تلگرام") }
            } else {
                if (!verification) {
                    TextButton(onClick = onOpenBot, enabled = remainingSeconds > 0) {
                        Text(session.code, style = MaterialTheme.typography.headlineMedium,
                            color = c.highlight, fontWeight = FontWeight.Black)
                    }
                    Text("۱. «تأیید اتصال در تلگرام» را بزن.\n۲. پایین چت ربات، «شروع / Start» را بزن.\n۳. به قاجار برگرد؛ حساب خودکار متصل می‌شود.",
                        color = c.textPrimary, textAlign = TextAlign.Center)
                    Text("کد از قبل داخل لینک است؛ آن را تایپ یا اصلاح نکن.", color = c.textSecondary, textAlign = TextAlign.Center)
                }
                Text("زمان باقی‌مانده: ${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')}",
                    color = c.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
                Text(GhajarLinkFlow.message(state), color = c.textPrimary, textAlign = TextAlign.Center)
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
    val c = ghajarColors
    // An important notice takes the error surface and accent; an ordinary one
    // stays on the secondary card, so urgency is a colour decision and not a
    // different card design.
    val accent = if (notice.important) c.error else c.primary
    Card(
        shape = RoundedCornerShape(GhajarRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (notice.important) c.errorSurface else c.secondaryCard
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Notifications, null, tint = accent)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(notice.title, color = c.textPrimary, fontWeight = FontWeight.Bold)
                Text(notice.message, color = c.textSecondary)
                notice.meta?.let { meta ->
                    val remainingGb = meta.remainingBytes?.div(1024.0 * 1024 * 1024)
                    val details = listOfNotNull(
                        remainingGb?.let { "باقی‌مانده ${"%.2f".format(Locale.US, it)} گیگ" },
                        meta.daysRemaining?.let { "$it روز باقی‌مانده" }
                    ).joinToString(" · ")
                    if (details.isNotBlank()) Text(details, color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Renewal dialog for one already-owned service, styled like the existing
 * purchase-confirmation AlertDialog so it does not introduce a new visual
 * language: same AlertDialog shell, same RadioButton/OutlinedTextField/Button
 * components, same colors and spacing. */
@Composable
private fun RenewServiceDialog(
    username: String,
    api: GhajarStoreApi,
    onDismiss: () -> Unit,
    onRenewed: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var loading by remember(username) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var options by remember(username) { mutableStateOf<GhajarRenewOptions?>(null) }
    var loadError by remember(username) { mutableStateOf<String?>(null) }
    var actionError by remember(username) { mutableStateOf<String?>(null) }
    var selectedCode by remember(username) { mutableStateOf<String?>(null) }
    var useCustom by remember(username) { mutableStateOf(false) }
    var customVolume by remember(username) { mutableStateOf("") }
    var customTime by remember(username) { mutableStateOf("") }

    // What the service currently is, alongside what it can be renewed to. The
    // dialog used to open on a bare username and a price list, which is the
    // one place a user needs to be told what they are renewing: how big the
    // plan is, how much of it is left, and when it runs out. A failure here
    // never blocks the renewal - the summary is simply not shown.
    var current by remember(username) { mutableStateOf<GhajarServiceDetails?>(null) }

    LaunchedEffect(username) {
        loading = true; loadError = null
        runCatching { api.renewOptions(username) }
            .onSuccess { result ->
                options = result
                selectedCode = result.currentPlanCode ?: result.products.firstOrNull()?.code
                useCustom = result.custom.forced || (result.products.isEmpty() && result.custom.enabled)
            }
            .onFailure { loadError = it.message ?: "دریافت گزینه‌های تمدید ناموفق بود" }
        loading = false
    }

    LaunchedEffect(username) {
        current = runCatching { api.service(username) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("تمدید سرویس") },
        text = {
            Column(
                // No fixed cap: the dialog already bounds its body to the
                // window, and a second, smaller cap here is what stopped the
                // list scrolling all the way to its last option.
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(username, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                current?.let { CurrentServiceSummary(it) }
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    loadError != null -> Text(loadError!!, color = MaterialTheme.colorScheme.error)
                    options != null -> {
                        val opt = options!!
                        opt.products.forEach { product ->
                            Row(
                                Modifier.fillMaxWidth().clickable { useCustom = false; selectedCode = product.code },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = !useCustom && selectedCode == product.code,
                                    onClick = { useCustom = false; selectedCode = product.code })
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        listOfNotNull(
                                            product.volumeGb.takeIf { it > 0 }?.let { "$it گیگ" },
                                            product.timeDays.takeIf { it > 0 }?.let { "$it روز" }
                                        ).joinToString("  •  "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    if (product.showPrice) "${formatPrice(product.price)} تومان" else "قیمت پس از تأیید",
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (opt.custom.enabled) {
                            Row(
                                Modifier.fillMaxWidth().clickable { useCustom = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = useCustom, onClick = { useCustom = true })
                                Text("حجم/زمان دلخواه", fontWeight = FontWeight.Bold)
                            }
                            if (useCustom) {
                                OutlinedTextField(
                                    customVolume, { customVolume = asciiDigits(it).filter(Char::isDigit).take(6) },
                                    label = { Text("حجم (گیگابایت) بین ${opt.custom.minVolumeGb} و ${opt.custom.maxVolumeGb}") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                if (opt.custom.maxTimeDays > opt.custom.minTimeDays) {
                                    OutlinedTextField(
                                        customTime, { customTime = asciiDigits(it).filter(Char::isDigit).take(4) },
                                        label = { Text("زمان (روز) بین ${opt.custom.minTimeDays} و ${opt.custom.maxTimeDays}") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true, modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        if (opt.products.isEmpty() && !opt.custom.enabled) {
                            Text("گزینه‌ای برای تمدید این سرویس در دسترس نیست.")
                        }
                        Text("موجودی کیف پول: ${formatPrice(opt.balance)} تومان", style = MaterialTheme.typography.bodySmall)
                    }
                }
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            val opt = options
            val customVolumeInt = customVolume.toIntOrNull()
            val customValid = opt != null && customVolumeInt != null &&
                customVolumeInt in opt.custom.minVolumeGb..opt.custom.maxVolumeGb &&
                (opt.custom.maxTimeDays <= opt.custom.minTimeDays || (customTime.toIntOrNull() ?: -1) in opt.custom.minTimeDays..opt.custom.maxTimeDays)
            val canConfirm = !busy && !loading && opt != null &&
                (if (useCustom) customValid else selectedCode != null)
            Button(enabled = canConfirm, onClick = click@{
                opt ?: return@click
                busy = true; actionError = null
                scope.launch {
                    runCatching {
                        if (useCustom) api.confirmRenew(
                            username, customVolumeGb = customVolumeInt,
                            customTimeDays = customTime.toIntOrNull()
                        ) else api.confirmRenew(username, productCode = selectedCode)
                    }.onSuccess { result ->
                        busy = false
                        if (result.requiresPayment) {
                            actionError = "موجودی کیف پول کافی نیست؛ ${formatPrice(result.amountDue)} تومان کسری دارید. " +
                                "ابتدا از تب «کیف پول» شارژ کن، سپس دوباره تمدید کن."
                        } else if (result.completed) {
                            onRenewed()
                        } else {
                            actionError = "تمدید تأیید نشد؛ دوباره تلاش کن."
                        }
                    }.onFailure {
                        busy = false
                        actionError = it.message ?: "تمدید ناموفق بود"
                    }
                    Unit
                }
            }) { Text(if (busy) "در حال تمدید…" else "تأیید تمدید") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("بازگشت") } }
    )
}

/**
 * What the service being renewed currently is.
 *
 * Only values the panel actually returned are shown - a plan with no declared
 * volume simply has no volume row, rather than a zero that reads as "you have
 * nothing left". The remaining figure is the panel's own; nothing here
 * recomputes it from the other two.
 */
@Composable
private fun CurrentServiceSummary(service: GhajarServiceDetails) {
    val c = ghajarColors
    val lang = LocalLang.current
    fun gb(value: Double) = localizeDigits("%.2f".format(java.util.Locale.US, value), lang) + " گیگابایت"

    val rows = buildList {
        service.productName.takeIf { it.isNotBlank() }?.let { add("پلن فعلی" to it) }
        service.totalGb?.takeIf { it > 0 }?.let { add("حجم پلن" to gb(it)) }
        service.usedGb?.let { add("مصرف‌شده" to gb(it)) }
        service.remainingGb?.let { add("باقی‌مانده" to gb(it)) }
        service.expiresAt.takeIf { it.isNotBlank() }?.let { add("انقضا" to localizeDigits(it, lang)) }
        service.status.takeIf { it.isNotBlank() }?.let { add("وضعیت" to it) }
    }
    if (rows.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(c.secondaryCard)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "این سرویس الان چیست",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = c.textSecondary
        )
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    mixedText(value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = c.textPrimary
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    // The skin's heading: a brand rail for the step, the explanation under it
    // as secondary text rather than a second bold line competing with it.
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Rail(title)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = ghajarColors.textSecondary
        )
    }
}

@Composable
private fun OwnedServiceCard(service: GhajarOwnedService, onImport: () -> Unit, onRenew: () -> Unit) {
    val c = ghajarColors
    Card(
        onClick = onImport,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GhajarRadius.md),
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.border)
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(service.productName, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Text("⁦${service.username}⁩", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(service.location, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val active = service.status.lowercase() in setOf("active", "enabled", "فعال")
                Text(if (active) "فعال" else when(service.status.lowercase()) { "expired" -> "منقضی"; "disabled", "inactive" -> "غیرفعال"; else -> service.status },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                IconButton(onClick = onRenew) {
                    Icon(Icons.Filled.Autorenew, "تمدید سرویس", tint = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.Filled.AddCircle, "افزودن")
            }
            // What is actually left of it. The row above says only what the
            // service is called and whether it is switched on, which is the
            // one question a user does not have about a service they bought:
            // they want to know how much of it is still theirs.
            ServiceRemaining(service)
        }
    }
}

/**
 * Remaining volume and remaining days, each with the share still unspent.
 *
 * Built from figures the services list already carries, so it costs no extra
 * request - one per service would have been 52 round trips on this account. A
 * meter is left out rather than guessed when its cap is missing: an unlimited
 * plan has no percentage, and inventing one would report a brand-new service
 * as nearly finished.
 */
@Composable
private fun ServiceRemaining(service: GhajarOwnedService) {
    val c = ghajarColors
    val lang = LocalLang.current
    fun pct(value: Float) = localizeDigits("${(value * 100).toInt()}٪", lang)
    fun gb(bytes: Long) =
        localizeDigits("%.2f".format(java.util.Locale.US, bytes / 1_073_741_824.0), lang)

    val spentVolume = service.volumeFraction
    val days = service.daysRemaining
    if (spentVolume == null && days == null) return

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (spentVolume != null) {
            MeterRow(
                label = "حجم باقی‌مانده",
                value = mixedText(
                    "${gb(service.remainingBytes ?: 0L)} از ${gb(service.dataLimitBytes ?: 0L)} گیگابایت"),
                share = pct(1f - spentVolume),
                fraction = 1f - spentVolume,
                tint = if (spentVolume >= 0.9f) MaterialTheme.colorScheme.error else c.primary
            )
        }
        if (days != null) {
            val elapsed = service.timeFraction
            MeterRow(
                label = "روز باقی‌مانده",
                value = mixedText(
                    localizeDigits(days.toString(), lang) + " روز" +
                        (service.planDays?.takeIf { it > 0 }
                            ?.let { " از " + localizeDigits(it.toString(), lang) } ?: "")
                ),
                share = elapsed?.let { pct(1f - it) } ?: "",
                fraction = elapsed?.let { 1f - it } ?: 1f,
                tint = if (days <= 3) MaterialTheme.colorScheme.error else c.primary
            )
        }
    }
}

@Composable
private fun MeterRow(label: String, value: String, share: String, fraction: Float, tint: Color) {
    val c = ghajarColors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textMuted,
                modifier = Modifier.weight(1f))
            if (share.isNotBlank()) {
                Text(share, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = tint)
            }
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = c.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(GhajarRadius.pill))
                .background(c.secondaryCard)
        ) {
            // A guard, not a micro-optimisation: fillMaxWidth(0f) still lays
            // out a zero-width box whose rounded ends paint as a visible dot,
            // so a fully spent service would show a stub of colour where it
            // has nothing left.
            if (fraction > 0.01f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(GhajarRadius.pill))
                        .background(tint)
                )
            }
        }
    }
}

/** Store tabs: exact labels, horizontally and vertically centered, uniform metrics. */
@Composable
private fun StoreSectionTabs(
    section: Int,
    noticeCount: Int,
    pendingCount: Int,
    serviceCount: Int,
    onSelect: (Int) -> Unit
) {
    // One scrolling rail, not two stacked segmented controls: six sections on
    // two rows of chrome pushed the first plan below the fold on a phone. Each
    // tab carries its own number, so the counts no longer need a second block
    // of pills underneath repeating the same three words.
    TabRail(
        tabs = listOf(
            RailTab("خرید", Icons.Filled.ShoppingCart),
            RailTab("سرویس‌ها", Icons.Filled.Dns, serviceCount),
            RailTab("پیام‌ها", Icons.Filled.Notifications, noticeCount),
            RailTab("کیف پول", Icons.Filled.AccountBalanceWallet, pendingCount),
            RailTab("پشتیبانی", Icons.Filled.SupportAgent),
            RailTab("تراکنش‌ها", Icons.Filled.SwapHoriz)
        ),
        selected = section.coerceIn(0, 5),
        onSelect = onSelect
    )
}

/** Service categories are never hidden behind a dropdown; the full list is visible at once. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ServiceTypeGrid(items: List<T>, selected: T?, label: (T) -> String,
    icon: (T) -> String, onSelect: (T) -> Unit) {
    // Was a grid of 132dp-wide outlined cards, which pushed the plans below
    // the fold on a phone before you had chosen anything. A service family is
    // one choice out of a handful, so it is a chip: glyph, name, and a filled
    // brand pill for the one you are on.
    val c = ghajarColors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(GhajarRadius.pill))
                    .background(if (isSelected) c.primary else c.secondaryCard)
                    .clickable { onSelect(item) }
                    .padding(horizontal = GhajarSpacing.md, vertical = GhajarSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(icon(item), style = MaterialTheme.typography.bodyLarge)
                Text(
                    label(item),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) c.onPrimary else c.textPrimary
                )
            }
        }
    }
}

/** Secondary filters (category, duration) as directly visible chips, centered text. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipFlowRow(allLabel: String, items: List<T>, selected: T?, label: (T) -> String, onSelect: (T?) -> Unit) {
    // Material's FilterChip brought its own outline and check mark; the skin
    // says a chosen chip is filled and nothing else needs marking.
    val c = ghajarColors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
    ) {
        @Composable
        fun chip(text: String, active: Boolean, onClick: () -> Unit) {
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) c.onPrimary else c.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(GhajarRadius.pill))
                    .background(if (active) c.primary else c.secondaryCard)
                    .clickable { onClick() }
                    .padding(horizontal = GhajarSpacing.md, vertical = 7.dp)
            )
        }
        chip(allLabel, selected == null) { onSelect(null) }
        items.forEach { item ->
            chip(label(item), item == selected) { onSelect(item) }
        }
    }
}

/** Stable icon per service family, matched to the panel names used by the panel. */
private fun panelIcon(name: String): String = when {
    name.contains("مولتی") || name.contains("چند لوکیشن") || name.contains("لوکیشن") -> "📍"
    name.contains("قبله") || name.contains("ویژه") && !name.contains("ایرانسل") -> "👑"
    name.contains("ایرانسل") || name.contains("اقتصادی") -> "📉"
    name.contains("آمریکا") || name.contains("usa", true) -> "🇺🇸"
    name.contains("آلمان") || name.contains("germany", true) -> "🇩🇪"
    name.contains("سوئد") || name.contains("sweden", true) -> "🇸🇪"
    name.contains("ترکیه") || name.contains("turkey", true) -> "🇹🇷"
    name.contains("هلند") || name.contains("netherlands", true) -> "🇳🇱"
    name.contains("فنلاند") || name.contains("finland", true) -> "🇫🇮"
    name.contains("انگلیس") || name.contains("uk", true) || name.contains("england", true) -> "🇬🇧"
    name.contains("کانادا") || name.contains("canada", true) -> "🇨🇦"
    name.contains("امارات") || name.contains("uae", true) -> "🇦🇪"
    name.contains("سنگاپور") || name.contains("singapore", true) -> "🇸🇬"
    name.contains("ژاپن") || name.contains("japan", true) -> "🇯🇵"
    name.contains("فرانسه") || name.contains("france", true) -> "🇫🇷"
    name.contains("روسیه") || name.contains("russia", true) -> "🇷🇺"
    name.contains("چین") || name.contains("china", true) -> "🇨🇳"
    name.contains("هند") || name.contains("india", true) -> "🇮🇳"
    name.contains("تست") || name.contains("trial", true) -> "🎁"
    name.contains("گیم") || name.contains("بازی") -> "🎮"
    else -> "🌐"
}

@Composable
private fun ProductCard(
    product: GhajarProduct,
    enabled: Boolean,
    bestValue: Boolean = false,
    onBuy: () -> Unit
) {
    var details by remember(product.id) { mutableStateOf(false) }
    val c = ghajarColors
    // A plan is the thing this screen exists to sell, so it gets a real card:
    // the name, what you actually get as a two-cell strip, the price large
    // enough to read at a glance, and its own buy action. The old version was
    // a single row where the price was the same size as the name and the only
    // affordance was a tiny "open" glyph.
    Slab(accent = if (bestValue) c.highlight else null, spacing = GhajarSpacing.md) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background((if (enabled) c.primary else c.onDisabled).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Shield,
                    null,
                    tint = if (enabled) c.primary else c.onDisabled,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) c.textPrimary else c.onDisabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (bestValue) {
                    // Computed from the visible list's price per gigabyte, not
                    // a label anyone typed in.
                    Text(
                        "بهترین ارزش در این فهرست",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.highlight,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (product.description.isNotBlank()) {
                Icon(
                    Icons.Filled.OpenInNew,
                    "توضیح این پلن",
                    tint = c.textMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(GhajarRadius.sm))
                        .clickable(enabled = enabled) { details = true }
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
        }

        StatStrip(
            listOfNotNull(
                product.trafficGb?.let {
                    StatCell(
                        "حجم",
                        "${it.toBigDecimal().stripTrailingZeros().toPlainString()} گیگ",
                        c.info
                    )
                },
                product.days?.let { StatCell("مدت", "$it روز", c.premium) },
                product.price?.let {
                    StatCell(
                        "قیمت",
                        if (it == 0L) "رایگان" else "${formatPrice(it)} تومان",
                        if (enabled) c.highlight else c.onDisabled
                    )
                }
            )
        )

        PillButton(
            text = if (product.price == null) "قیمت در دسترس نیست" else "خرید این پلن",
            onClick = onBuy,
            enabled = enabled && product.price != null,
            icon = Icons.Filled.ShoppingCart
        )
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
    Slab(padding = 15.dp, spacing = GhajarSpacing.sm) {
        Text("سرویس سفارشی", fontWeight = FontWeight.Bold, color = ghajarColors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(traffic, onTrafficChange, label = { Text("حجم (گیگ)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(days, onDaysChange, label = { Text("مدت (روز)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        quote?.let {
            Text(
                if (it.price != null) "قیمت لحظه‌ای: ${formatPrice(it.price)} تومان" else "سرویس سفارشی برای این پنل فعال نیست",
                color = if (it.price != null) ghajarColors.highlight else ghajarColors.textSecondary,
                fontWeight = FontWeight.Bold
            )
            Text("حجم ${it.trafficMin} تا ${it.trafficMax} گیگ · زمان ${it.timeMin} تا ${it.timeMax} روز", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onQuote, modifier = Modifier.fillMaxWidth()) { Text("محاسبه قیمت از پنل") }
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
    Slab(padding = 14.dp, spacing = GhajarSpacing.sm) {
        if (showUsername) OutlinedTextField(username, onUsername, label = { Text(if (usernameRequired) "نام کاربری دلخواه (ضروری)" else "نام کاربری دلخواه") }, modifier = Modifier.fillMaxWidth())
        if (showNote) OutlinedTextField(note, onNote, label = { Text("یادداشت اختیاری") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(discount, onDiscount, label = { Text("کد تخفیف اختیاری") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PaymentSummary(purchase: GhajarPurchaseResult, walletTopUp: Boolean, exactCardAmount: Long?) {
    val c = ghajarColors
    Card(
        shape = RoundedCornerShape(GhajarRadius.md),
        colors = CardDefaults.cardColors(containerColor = c.secondaryCard),
        border = BorderStroke(1.dp, c.border)
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                if (walletTopUp) "شارژ کیف پول" else "پرداخت مبلغ کسری",
                color = c.textPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Text("موجودی: ${formatPrice(purchase.balance)} تومان", color = c.textSecondary)
            if (!walletTopUp) Text("قیمت سرویس: ${formatPrice(purchase.price)} تومان", color = c.textSecondary)
            // The amount actually owed is the one number that must not be
            // missed, so it gets the highlight tone and the extra weight.
            Text(
                "قابل پرداخت: ${formatPrice(exactCardAmount ?: purchase.amountDue)} تومان",
                color = c.highlight,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PaymentMethodCard(method: GhajarPaymentMethod, amount: Long, enabled: Boolean, onClick: () -> Unit) {
    val allowed = amount >= method.minimum && (method.maximum <= 0 || amount <= method.maximum)
    Card(onClick = onClick, enabled = allowed && enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CreditCard, null, tint = if (allowed) ghajarColors.primary else ghajarColors.onDisabled)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(method.label, fontWeight = FontWeight.Bold)
                Text("محدوده ${formatPrice(method.minimum)} تا ${if (method.maximum > 0) formatPrice(method.maximum) else "نامحدود"} تومان", style = MaterialTheme.typography.bodySmall)
            }
            Text(if (allowed) "انتخاب" else "نامعتبر", color = if (allowed) ghajarColors.primary else ghajarColors.error)
        }
    }
}

@Composable
private fun StatusCard(
    text: String,
    error: Boolean,
    onDismiss: () -> Unit,
    /** Offered on a failure, so the message is a way out and not a dead end. */
    onRetry: (() -> Unit)? = null,
    /** Said under the message when the content below it is stale, not fresh. */
    footnote: String? = null
) {
    val c = ghajarColors
    // A left rule in the state's colour instead of a fully tinted block, so a
    // long error stays readable and an info message stays quiet.
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(if (error) c.errorSurface else c.secondaryCard)
            .padding(start = GhajarSpacing.md, end = GhajarSpacing.xs, top = GhajarSpacing.sm, bottom = GhajarSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (error) GhajarCommerceRules.publicMessage(text) else text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (error) c.error else c.textPrimary
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(
                        "تلاش مجدد",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.primary
                    )
                }
            }
            TextButton(onClick = onDismiss) {
                Text("بستن", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
        }
        if (!footnote.isNullOrBlank()) {
            Text(
                footnote,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                modifier = Modifier.padding(end = GhajarSpacing.sm, bottom = GhajarSpacing.xs)
            )
        }
    }
}

private fun formatPrice(price: Long): String = NumberFormat.getIntegerInstance(Locale("fa", "IR")).format(price)

/** One glance at everything the checkout/wallet tabs already track
 * separately - balance, pending payments, active services - built from
 * the same state this screen already fetched, no new API calls. */
@Composable
private fun OrderStatusCenter(
    balanceText: String?,
    pendingCount: Int,
    activeServiceCount: Int,
    totalServiceCount: Int,
    onOpenWallet: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenServices: () -> Unit
) {
    // Three readings of one account in one object. Each reading is its own tap
    // target now, so the row of ghost pills that used to sit underneath -
    // repeating the same three words as buttons - is gone.
    val c = ghajarColors
    StatStrip(
        listOf(
            StatCell("موجودی", balanceText ?: "…", c.premium, onClick = onOpenWallet),
            StatCell(
                "در انتظار پرداخت",
                pendingCount.toString(),
                if (pendingCount > 0) c.warning else c.textPrimary,
                onClick = onOpenPending
            ),
            StatCell(
                "سرویس‌های فعال",
                "$activeServiceCount/$totalServiceCount",
                c.good,
                onClick = onOpenServices
            )
        )
    )
}

/** A real side-by-side comparison built from the same GhajarProduct list the
 * store already fetched from the panel - no separate numbers, no guessing. */
@Composable
private fun PlanComparisonDialog(products: List<GhajarProduct>, onDismiss: () -> Unit) {
    val rows = remember(products) {
        products.sortedWith(compareBy(nullsLast()) { p -> p.price?.let { price ->
            p.trafficGb?.takeIf { it > 0 }?.let { price / it }
        } })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مقایسهٔ پلن‌ها") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { p ->
                    Column(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(
                            p.trafficGb?.let { "${it.toBigDecimal().stripTrailingZeros().toPlainString()} گیگ" },
                            p.days?.let { "$it روز" }
                        ).joinToString("  •  "), style = MaterialTheme.typography.bodySmall)
                        Text(p.price?.let { if (it == 0L) "رایگان" else "${formatPrice(it)} تومان" } ?: "قیمت در دسترس نیست",
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        val perGb = p.price?.let { price -> p.trafficGb?.takeIf { it > 0 }?.let { price / it } }
                        perGb?.let {
                            Text("هر گیگ: ${formatPrice(it.toLong())} تومان", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

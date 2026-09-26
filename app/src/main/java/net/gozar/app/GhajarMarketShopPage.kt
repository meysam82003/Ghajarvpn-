package net.gozar.app

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * One marketplace shop, as its own storefront with six tabs.
 *
 * The same six the Ghajar shop has - buy, services, messages, wallet,
 * support, transactions - so moving between shops never changes where
 * anything is. Everything a tab shows comes from market.php and, through it,
 * from the seller's own server; nothing here decides a price or a payment.
 */

// ------------------------------------------------------------------- logos

/** Decoded logos, so scrolling the list back up does not decode them again. */
internal object MarketLogoCache {
    private val cache = LruCache<String, Bitmap>(32)
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

/**
 * A shop's profile picture, or a quiet placeholder.
 *
 * The seller sets it in the bot; a shop without one gets a shield (verified)
 * or a bag, never an empty box.
 */
@Composable
internal fun MarketLogo(api: GhajarStoreApi, shopId: Int, version: Int, verified: Boolean, size: Dp = 48.dp) {
    val c = ghajarColors
    val key = "$shopId-$version"
    val bitmap by produceState(initialValue = MarketLogoCache.get(key), key) {
        if (value == null && version > 0) {
            runCatching { api.marketLogo(shopId, version) }.getOrNull()?.let {
                MarketLogoCache.put(key, it)
                value = it
            }
        }
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(c.card),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(size))
        } else {
            Icon(
                if (verified) Icons.Filled.Shield else Icons.Filled.ShoppingBag,
                contentDescription = null,
                tint = if (verified) c.primary else c.textMuted,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

/** The Ghajar crest in the same frame as a seller's logo. */
@Composable
internal fun GhajarCrestLogo(size: Dp = 48.dp) {
    val version by GhajarShopLogo.version.collectAsState()
    if (version > 0) {
        val context = LocalContext.current
        val api = remember { GhajarStoreApi(context.applicationContext) }
        MarketLogo(api, 0, version, verified = true, size = size)
        return
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(ghajarColors.card),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(R.drawable.ghajar_widget_mark), contentDescription = null,
            modifier = Modifier.size(size - 8.dp))
    }
}

// ------------------------------------------------------------------- dates

/**
 * Unix seconds as a Persian calendar date and time, "۱۴۰۵/۰۷/۰۴ ۱۲:۳۰".
 *
 * The standard arithmetic conversion (the one jdf.php uses on the server),
 * so the app and the bot's messages print the same date for the same moment.
 */
internal fun marketJalali(epochSeconds: Long, withTime: Boolean = true): String {
    if (epochSeconds <= 0) return "—"
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran"))
    cal.timeInMillis = epochSeconds * 1000
    val gy = cal.get(java.util.Calendar.YEAR)
    val gm = cal.get(java.util.Calendar.MONTH) + 1
    val gd = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm = if (days < 186) 1 + (days / 31) else 7 + ((days - 186) / 30)
    val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
    val date = "%04d/%02d/%02d".format(java.util.Locale.US, jy, jm, jd)
    return if (!withTime) date else date + " " + "%02d:%02d".format(java.util.Locale.US,
        cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}

private fun gbText(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 10) "%.0f".format(java.util.Locale.US, gb) else "%.1f".format(java.util.Locale.US, gb)
}

// -------------------------------------------------------------- the page

private val MARKET_TABS = listOf("خرید", "سرویس‌ها", "پیام‌ها", "کیف پول", "پشتیبانی", "تراکنش‌ها")

@Composable
internal fun MarketShopHome(
    api: GhajarStoreApi,
    store: ConfigStore,
    shopId: Int,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    onOrdered: (GhajarMarketOrder) -> Unit
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val context = LocalContext.current
    var home by remember(shopId) { mutableStateOf<GhajarMarketHome?>(null) }
    var error by remember(shopId) { mutableStateOf<String?>(null) }
    var busy by remember(shopId) { mutableStateOf(true) }
    var reload by remember(shopId) { mutableIntStateOf(0) }
    var tab by rememberSaveable(shopId) { mutableIntStateOf(0) }

    LaunchedEffect(shopId, reload) {
        busy = true
        runCatching { api.marketHome(shopId) }
            .onSuccess { home = it; error = null }
            .onFailure { error = it.message ?: "این فروشگاه باز نشد" }
        busy = false
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
        GhostPill("همهٔ فروشگاه‌ها", onBack, icon = Icons.Filled.ChevronLeft)

        val loaded = home
        if (loaded == null) {
            if (busy) SkinLoading("در حال باز کردن فروشگاه")
            else SkinError(error ?: "این فروشگاه باز نشد", retryText = "تلاش دوباره", onRetry = { reload++ })
            return@Column
        }
        val shop = loaded.shop

        // The shop's own header: picture, name, line, score and the full
        // description the seller wrote in the bot.
        Slab(spacing = GhajarSpacing.sm) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MarketLogo(api, shop.id, shop.logoVersion, shop.verified, 56.dp)
                Spacer(Modifier.width(GhajarSpacing.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(shop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        if (shop.verified) {
                            Spacer(Modifier.width(GhajarSpacing.xs))
                            Icon(Icons.Filled.Shield, "تأییدشده", tint = c.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (shop.tagline.isNotBlank()) {
                        Text(shop.tagline, style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarRow(shop.stars)
                        Spacer(Modifier.width(GhajarSpacing.xs))
                        Text(
                            mixedText(if (shop.reviewCount > 0)
                                localizeDigits("%.1f".format(java.util.Locale.US, shop.stars), lang) + " · " +
                                    localizeDigits(shop.reviewCount.toString(), lang) + " نظر"
                            else "بدون نظر"),
                            style = MaterialTheme.typography.labelSmall, color = c.textMuted
                        )
                    }
                }
            }
            if (shop.description.isNotBlank()) {
                Text(shop.description, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            val links = listOfNotNull(
                shop.telegramBot.takeIf { it.isNotBlank() }?.let { "ربات" to it },
                shop.telegramChannel.takeIf { it.isNotBlank() }?.let { "کانال" to it },
                shop.supportContact.takeIf { it.isNotBlank() }?.let { "پشتیبانی" to it }
            )
            links.forEach { (label, handle) ->
                Row(
                    Modifier.fillMaxWidth().clickable { openTelegram(context, handle) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SupportAgent, null, tint = c.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(GhajarSpacing.sm))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = c.textMuted,
                        modifier = Modifier.weight(1f))
                    Text(mixedText(if (handle.startsWith("http")) handle else "@" + handle.trimStart('@')),
                        style = MaterialTheme.typography.labelMedium, color = c.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(GhajarSpacing.xs))
                    Icon(Icons.Filled.OpenInNew, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
                }
            }
        }

        TabRail(
            tabs = MARKET_TABS.mapIndexed { index, label ->
                RailTab(label, badge = if (index == 2 && loaded.unread > 0) loaded.unread else null)
            },
            selected = tab,
            onSelect = { tab = it }
        )

        if (tab != 0 && !signedIn) {
            MarketSignInSlab(onSignIn)
            return@Column
        }
        when (tab) {
            0 -> MarketBuyTab(api, loaded, signedIn, onSignIn, onOrdered)
            1 -> MarketServicesTab(api, store, loaded, onOrdered)
            2 -> MarketMessagesTab(api, shopId) { reload++ }
            3 -> MarketWalletTab(api, loaded, onOrdered)
            4 -> MarketSupportTab(api, shopId)
            else -> MarketTransactionsTab(api, shopId)
        }
    }
}

@Composable
private fun MarketSignInSlab(onSignIn: () -> Unit) {
    val c = ghajarColors
    Slab(accent = c.primary, spacing = GhajarSpacing.xs, onClick = onSignIn) {
        Text("برای این بخش، حساب را یک‌بار متصل کن", fontWeight = FontWeight.Bold, color = c.textPrimary)
        Text("دیدن فروشگاه و پلن‌ها بدون اتصال هم ممکن است؛ خرید، سرویس‌ها، کیف پول و پشتیبانی به حساب نیاز دارند.",
            style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}

// ---------------------------------------------------------------- buy tab

/**
 * Buying at a marketplace shop, laid out exactly like the Ghajar shop: the
 * free-test pill on top, "1. choose a service" with the seller's servers as
 * chips, category and duration chips, ready plans or a custom size, and the
 * same plan cards. Only the source of the numbers differs - they come from
 * the seller's own server.
 */
@Composable
private fun MarketBuyTab(
    api: GhajarStoreApi,
    home: GhajarMarketHome,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onOrdered: (GhajarMarketOrder) -> Unit
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val shop = home.shop
    val catalog = home.catalog
    var panelCode by remember(shop.id) { mutableStateOf(catalog.panels.firstOrNull()?.code) }
    var category by remember(shop.id) { mutableStateOf<GhajarCategory?>(null) }
    var duration by remember(shop.id) { mutableStateOf<GhajarTimeRange?>(null) }
    var customMode by remember(shop.id) { mutableStateOf(false) }
    var showTests by remember(shop.id) { mutableStateOf(false) }
    var gb by remember(shop.id) { mutableStateOf("") }
    var days by remember(shop.id) { mutableStateOf("") }
    var quote by remember(shop.id) { mutableStateOf<GhajarCustomQuote?>(null) }
    // What is about to be bought, waiting for a payment method.
    var pending by remember(shop.id) { mutableStateOf<MarketPending?>(null) }
    var method by remember(shop.id) { mutableStateOf<String?>(null) }
    var discountCode by remember(shop.id) { mutableStateOf("") }
    var discounted by remember(shop.id) { mutableStateOf<Long?>(null) }
    var discountNote by remember(shop.id) { mutableStateOf<String?>(null) }
    var starting by remember(shop.id) { mutableStateOf(false) }
    var actionError by remember(shop.id) { mutableStateOf<String?>(null) }

    val panel = catalog.panels.firstOrNull { it.code == panelCode }
    val testPanels = if (home.testEnabled) catalog.panels.filter { it.test } else emptyList()
    val customOn = panel?.custom == true && panel.gbPrice > 0
    if (!customOn && customMode) customMode = false

    fun start(order: suspend () -> GhajarMarketOrder) {
        if (!signedIn) { onSignIn(); return }
        starting = true
        actionError = null
        scope.launch {
            runCatching { order() }
                .onSuccess { pending = null; onOrdered(it) }
                .onFailure { actionError = it.message ?: "ثبت سفارش انجام نشد" }
            starting = false
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!shop.canSell) SkinError(shop.closedReason.ifBlank { "این فروشگاه فعلاً فروش جدید ندارد." })
        if (!home.reachable) {
            SkinError("سرور این فروشگاه همین حالا جواب نداد؛ پلن‌ها ممکن است کامل نباشند. چند لحظه بعد دوباره باز کن.")
        }
        if (home.blocked) SkinError("این فروشگاه امکان خرید را برای حساب شما بسته است.")
        actionError?.let { Text(it, color = c.error, style = MaterialTheme.typography.labelMedium) }

        if (testPanels.isNotEmpty()) {
            GhostPill(
                if (home.testUsed) "سرویس تست این فروشگاه را گرفته‌اید" else "دریافت سرویس تست رایگان",
                { if (!signedIn) onSignIn() else showTests = !showTests },
                enabled = !home.testUsed && !starting,
                icon = Icons.Filled.CardGiftcard,
                accent = c.premium
            )
            if (showTests) {
                testPanels.forEach { p ->
                    Slab(spacing = 0.dp) {
                        SlabRow(
                            title = listOf(p.flag, p.name).filter { it.isNotBlank() }.joinToString(" "),
                            subtitle = listOfNotNull(
                                p.testMb.takeIf { it > 0 }?.let {
                                    if (it >= 1024) localizeDigits(gbText(it * 1_048_576L), lang) + " گیگ"
                                    else localizeDigits(it.toString(), lang) + " مگ"
                                },
                                p.testHours.takeIf { it > 0 }?.let { localizeDigits(it.toString(), lang) + " ساعت" }
                            ).joinToString(" · ").ifBlank { "سرویس آزمایشی" },
                            icon = Icons.Filled.CardGiftcard,
                            accent = c.premium,
                            chevron = true,
                            enabled = !starting,
                            onClick = {
                                showTests = false
                                start { api.marketOrderStart(shop.id, "", p.code, "free", kind = "test") }
                            }
                        )
                    }
                }
            }
        }

        SectionTitle("۱. انتخاب سرویس", "قیمت و موجودی مستقیماً از پنل فروشنده دریافت می‌شود")
        if (catalog.panels.isEmpty()) {
            SkinEmpty("هیچ سرویسی از پنل فروشنده دریافت نشد",
                hint = "چند لحظه بعد دوباره بررسی کن؛ اگر ادامه داشت، از پشتیبانی فروشگاه بپرس.",
                icon = Icons.Filled.ShoppingBag)
        } else {
            ServiceTypeGrid(
                items = catalog.panels,
                selected = panel,
                label = { listOf(it.name, it.flag).filter { s -> s.isNotBlank() }.joinToString(" ") },
                icon = { panelIcon(it.name) },
                onSelect = { panelCode = it.code; quote = null }
            )
        }

        // The plans on the chosen server; "/all" and blank mean every server.
        val onPanel = catalog.products.filter {
            it.location.isBlank() || it.location == "/all" || it.location == panel?.name
        }.ifEmpty { catalog.products }
        val usedCategories = catalog.categories.filter { cat ->
            onPanel.any { it.category == cat.name || it.category == cat.id }
        }
        val durations = onPanel.map { it.timeDays }.filter { it > 0 }.distinct().sorted().map { d ->
            GhajarTimeRange(d, when (d) {
                30 -> "⏳ یک ماه"; 60 -> "⏳ دو ماه"; 90 -> "⏳ سه ماه"; 180 -> "⏳ شش ماه"; 365 -> "⏳ یک سال"
                else -> "⏳ " + localizeDigits(d.toString(), lang) + " روز"
            })
        }
        if (usedCategories.isNotEmpty() && !customMode) {
            ChipFlowRow("همه دسته‌ها", usedCategories, category, { it.name }) { category = it }
        }
        if (durations.size > 1 && !customMode) {
            ChipFlowRow("همه مدت‌ها", durations, duration, { it.name }) { duration = it }
        }
        if (customOn) {
            SlidingSegments(
                labels = listOf("پلن‌های آماده", "سرویس سفارشی"),
                selected = if (customMode) 1 else 0,
                onSelect = { customMode = it == 1 }
            )
        }

        if (customMode && panel != null) {
            CustomServiceCard(
                traffic = gb,
                days = days,
                quote = quote,
                onTrafficChange = { gb = GhajarUiRules.asciiDigits(it).filter(Char::isDigit).take(5); quote = null },
                onDaysChange = { days = GhajarUiRules.asciiDigits(it).filter(Char::isDigit).take(4); quote = null },
                onQuote = {
                    // The seller's own tariff, applied the way their bot does:
                    // per gigabyte plus per day, within their limits.
                    val g = gb.toIntOrNull() ?: 0
                    val d = days.toIntOrNull() ?: 0
                    val inRange = g > 0 && d > 0 &&
                        (panel.minGb <= 0 || g >= panel.minGb) && (panel.maxGb <= 0 || g <= panel.maxGb) &&
                        (panel.minDays <= 0 || d >= panel.minDays) && (panel.maxDays <= 0 || d <= panel.maxDays)
                    quote = GhajarCustomQuote(
                        price = if (inRange) panel.gbPrice * g + panel.dayPrice * d else null,
                        trafficMin = panel.minGb, trafficMax = panel.maxGb,
                        timeMin = panel.minDays, timeMax = panel.maxDays
                    )
                }
            )
            Text(
                mixedText("هر گیگ " + localizeDigits(formatToman(panel.gbPrice), lang) + " تومان" +
                    (if (panel.dayPrice > 0) " · هر روز " + localizeDigits(formatToman(panel.dayPrice), lang) + " تومان" else "")),
                style = MaterialTheme.typography.labelMedium, color = c.textSecondary
            )
            PillButton(
                "خرید سرویس سفارشی",
                {
                    val price = quote?.price ?: return@PillButton
                    pending = MarketPending(
                        title = "سرویس سفارشی · " + localizeDigits(gb, lang) + " گیگ · " + localizeDigits(days, lang) + " روز",
                        price = price, productCode = "customvolume", custom = true,
                        volumeGb = gb.toIntOrNull() ?: 0, timeDays = days.toIntOrNull() ?: 0
                    )
                    method = null
                    discounted = null
                    discountNote = null
                },
                enabled = quote?.price != null && !starting && shop.canSell,
                icon = Icons.Filled.ShoppingCart
            )
        } else {
            val shown = onPanel.filter { p ->
                (category == null || p.category == category?.name || p.category == category?.id) &&
                    (duration == null || p.timeDays == duration?.days)
            }
            val bestValue = shown.filter { it.price > 0 && it.volumeGb > 0 }
                .minByOrNull { it.price.toDouble() / it.volumeGb }?.code?.takeIf { shown.size > 1 }
            if (shown.isEmpty()) {
                SkinEmpty(
                    "پلنی با این فیلترها پیدا نشد",
                    hint = "دستهٔ دیگری انتخاب کن یا فیلتر مدت را بردار.",
                    icon = Icons.Filled.ShoppingCart,
                    actionText = if (category != null || duration != null) "برداشتن فیلترها" else null,
                    onAction = if (category != null || duration != null) { { category = null; duration = null } } else null
                )
            }
            shown.forEach { product ->
                ProductCard(
                    GhajarProduct(
                        id = product.code,
                        name = product.name,
                        price = product.price,
                        trafficGb = product.volumeGb.takeIf { it > 0 }?.toDouble(),
                        days = product.timeDays.takeIf { it > 0 },
                        description = product.note,
                        countryId = panelCode.orEmpty()
                    ),
                    enabled = !starting && shop.canSell && !home.blocked,
                    bestValue = product.code == bestValue
                ) {
                    pending = MarketPending(title = product.name, price = product.price, productCode = product.code)
                    method = null
                    discounted = null
                    discountNote = null
                }
            }
        }

        MarketReviews(api, shop, signedIn, onSignIn)
    }

    // "2. confirm the order": how to pay, then go - the same step the Ghajar
    // shop shows before it takes money.
    pending?.let { order ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!starting) pending = null },
            title = { Text("۲. تأیید سفارش") },
            text = {
                Column(
                    Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(order.title, fontWeight = FontWeight.Bold)
                    val finalPrice = discounted
                    if (finalPrice != null && finalPrice < order.price) {
                        Text("قیمت: " + localizeDigits(formatToman(order.price), lang) + " ← " +
                            localizeDigits(formatToman(finalPrice), lang) + " تومان", fontWeight = FontWeight.Bold,
                            color = c.primary)
                    } else {
                        Text("قیمت: " + localizeDigits(formatToman(order.price), lang) + " تومان")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkinField(
                            value = discountCode,
                            onValueChange = { discountCode = it.take(40); discounted = null; discountNote = null },
                            label = "کد تخفیف (اختیاری)",
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.TextButton(
                            enabled = discountCode.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    runCatching { api.marketDiscountCheck(shop.id, discountCode, order.price) }
                                        .onSuccess { (price, msg) -> discounted = price; discountNote = msg }
                                        .onFailure { discounted = null; discountNote = it.message ?: "کد تخفیف معتبر نیست." }
                                }
                            }
                        ) { Text("اعمال") }
                    }
                    discountNote?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = if (discounted != null) c.primary else c.error)
                    }
                    Text("روش پرداخت", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    home.wallet?.let { wallet ->
                        ChoiceRow(
                            title = "کیف پول این فروشگاه",
                            subtitle = "موجودی: " + localizeDigits(formatToman(wallet), lang) + " تومان",
                            selected = method == "wallet",
                            onSelect = { method = "wallet" }
                        )
                    }
                    catalog.methods.forEach { m ->
                        ChoiceRow(title = m.label, subtitle = m.note.takeIf { it.isNotBlank() },
                            selected = method == m.id, onSelect = { method = m.id })
                    }
                    if (catalog.methods.isEmpty() && home.wallet == null) {
                        Text("این فروشگاه هنوز روش پرداختی متصل نکرده است.", color = c.error)
                    }
                    Text("پرداخت به همین فروشگاه انجام می‌شود، نه به قاجار؛ رسید برای خودِ فروشنده می‌رود.",
                        style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    actionError?.let { Text(it, color = c.error, style = MaterialTheme.typography.labelMedium) }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    enabled = !starting && method != null,
                    onClick = {
                        val m = method ?: return@Button
                        start {
                            if (order.custom) api.marketOrderStart(shop.id, "customvolume", panelCode.orEmpty(), m,
                                plan = "custom", volumeGb = order.volumeGb, timeDays = order.timeDays,
                                discountCode = discountCode.trim())
                            else api.marketOrderStart(shop.id, order.productCode, panelCode.orEmpty(), m,
                                discountCode = discountCode.trim())
                        }
                    }
                ) { Text(if (starting) "در حال ثبت…" else "تأیید و ادامه") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pending = null }) { Text("بازگشت") }
            }
        )
    }
}

private data class MarketPending(
    val title: String,
    val price: Long,
    val productCode: String,
    val custom: Boolean = false,
    val volumeGb: Int = 0,
    val timeDays: Int = 0
)

private fun rangeHint(min: Int, max: Int, unit: String, lang: Lang): String? = when {
    min > 0 && max > 0 -> "از " + localizeDigits(min.toString(), lang) + " تا " + localizeDigits(max.toString(), lang) + " " + unit
    min > 0 -> "حداقل " + localizeDigits(min.toString(), lang) + " " + unit
    max > 0 -> "حداکثر " + localizeDigits(max.toString(), lang) + " " + unit
    else -> null
}

@Composable
private fun MarketReviews(api: GhajarStoreApi, shop: GhajarMarketShop, signedIn: Boolean, onSignIn: () -> Unit) {
    val c = ghajarColors
    val scope = rememberCoroutineScope()
    var myStars by remember(shop.id) { mutableIntStateOf(0) }
    var note by remember(shop.id) { mutableStateOf("") }
    var result by remember(shop.id) { mutableStateOf<String?>(null) }

    Rail("نظرها")
    if (shop.reviews.isEmpty()) {
        Text("هنوز نظری ثبت نشده است.", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    }
    shop.reviews.forEach { review ->
        Slab(spacing = GhajarSpacing.xs) {
            StarRow(review.stars.toDouble())
            if (review.body.isNotBlank()) {
                Text(review.body, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
    if (!signedIn) {
        Slab(accent = c.primary, spacing = GhajarSpacing.xs, onClick = onSignIn) {
            Text("برای امتیاز دادن، حساب را متصل کن", fontWeight = FontWeight.Bold, color = c.textPrimary)
            Text("نظر فقط از کسی پذیرفته می‌شود که از همین فروشگاه خرید کرده باشد.",
                style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
        return
    }
    Slab(spacing = GhajarSpacing.sm) {
        Text("امتیاز شما", fontWeight = FontWeight.Bold, color = c.textPrimary)
        Row {
            repeat(5) { index ->
                Icon(
                    if (index < myStars) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "امتیاز " + (index + 1),
                    tint = if (index < myStars) c.warning else c.textMuted,
                    modifier = Modifier.size(28.dp).padding(2.dp).clickable { myStars = index + 1 }
                )
            }
        }
        SkinField(value = note, onValueChange = { note = it.take(500) }, label = "توضیح (اختیاری)")
        result?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = c.textSecondary) }
        GhostPill("ثبت نظر", {
            if (myStars <= 0) { result = "اول ستاره را انتخاب کنید."; return@GhostPill }
            scope.launch {
                runCatching { api.marketReview(shop.id, myStars, note) }
                    .onSuccess { result = it.ifBlank { "نظر شما ثبت شد." } }
                    .onFailure { result = it.message ?: "ثبت نظر انجام نشد" }
            }
        }, icon = Icons.Filled.Star)
    }
}

// ----------------------------------------------------------- services tab

@Composable
private fun MarketServicesTab(
    api: GhajarStoreApi,
    store: ConfigStore,
    home: GhajarMarketHome,
    onOrdered: (GhajarMarketOrder) -> Unit
) {
    val c = ghajarColors
    val shopId = home.shop.id
    var services by remember(shopId) { mutableStateOf<List<GhajarMarketService>?>(null) }
    var error by remember(shopId) { mutableStateOf<String?>(null) }
    var reload by remember(shopId) { mutableIntStateOf(0) }
    var renewing by remember(shopId) { mutableStateOf<GhajarMarketService?>(null) }
    var delivery by remember(shopId) { mutableStateOf<GhajarMarketOrderStatus?>(null) }
    val scope = rememberCoroutineScope()
    var sort by rememberSaveable(shopId) { mutableStateOf(ServiceSort.NEWEST) }
    var importing by remember(shopId) { mutableStateOf<String?>(null) }
    var notice by remember(shopId) { mutableStateOf<String?>(null) }

    LaunchedEffect(shopId, reload) {
        runCatching { api.marketServices(shopId) }
            .onSuccess { services = it; error = null }
            .onFailure { error = it.message ?: "سرویس‌ها خوانده نشد" }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
        val list = services
        val target = renewing
        when {
            target != null -> MarketRenewPanel(api, home, target, onCancel = { renewing = null }, onOrdered = onOrdered)
            list == null && error == null -> SkinLoading("در حال گرفتن سرویس‌ها")
            list == null -> SkinError(error!!, retryText = "تلاش دوباره", onRetry = { reload++ })
            list.isEmpty() -> SkinEmpty("از این فروشگاه هنوز سرویسی نخریده‌اید",
                hint = "از تب «خرید» یک پلن یا تست بگیرید.", icon = Icons.Filled.ShoppingBag)
            else -> {
                // The same card and the same chips as the Ghajar shop's own
                // services list, so a service looks the same wherever it was
                // bought.
                val asOwned = list.map { it.toOwned() }
                ServiceSortChips(asOwned, sort) { sort = it }
                val shown = asOwned.sortedFor(sort)
                if (shown.isEmpty()) {
                    Text("سرویسی در این دسته نیست.", color = c.textMuted)
                }
                shown.forEach { owned ->
                    val service = list.first { it.username == owned.username && it.invoiceId == owned.invoiceId }
                    OwnedServiceCard(owned,
                        onImport = {
                            importing = service.username
                            scope.launch {
                                // One tap, like the Ghajar shop: fetched and
                                // added to the servers list straight away.
                                runCatching {
                                    val d = api.marketServiceDelivery(shopId, service.invoiceId, service.username)
                                    delivery = d
                                    if (d.subscription.isBlank() && d.configs.isEmpty()) {
                                        throw GhajarApiException("فروشنده هنوز کانفیگی برای این سرویس برنگردانده است؛ چند لحظه بعد دوباره بزنید.")
                                    }
                                    api.importServiceOnce(store, marketServiceDetails(d, service.productName.ifBlank { "سرویس فروشگاه" }))
                                }.onSuccess { count ->
                                    notice = if (count > 0) "✅ به لیست سرورها اضافه شد." else "این سرویس قبلاً اضافه شده است."
                                    error = null
                                }.onFailure { error = it.message ?: "کانفیگ‌ها دریافت نشد" }
                                importing = null
                            }
                        },
                        onRenew = { if (!service.isTest) renewing = service })
                    if (!service.reachable) {
                        Text("پنل فروشنده برای این سرویس جواب نداد؛ مصرف نمایش داده نمی‌شود.",
                            style = MaterialTheme.typography.labelSmall, color = c.warning)
                    }
                }
                importing?.let { SkinLoading("در حال دریافت کانفیگ‌های $it") }
                notice?.let { Text(it, color = c.primary, style = MaterialTheme.typography.labelMedium) }
                delivery?.let { MarketDelivery(it, api, store) }
                error?.let { Text(it, color = c.error, style = MaterialTheme.typography.labelMedium) }
                GhostPill("بازخوانی", { reload++ }, icon = Icons.Filled.Refresh)
            }
        }
    }
}

/** A marketplace service in the Ghajar shop's own shape. */
private fun GhajarMarketService.toOwned() = GhajarOwnedService(
    username = username,
    productName = productName.ifBlank { username },
    status = status.ifBlank { if (reachable) "active" else "" },
    location = panelName,
    invoiceId = invoiceId,
    dataLimitBytes = dataLimit.takeIf { it > 0 },
    usedBytes = used,
    expireTimestamp = expire.takeIf { it > 0 },
    planGb = volumeGb.takeIf { it > 0 }?.toDouble(),
    planDays = timeDays.takeIf { it > 0 },
    soldAt = boughtAt.takeIf { it > 0 }
)

@Composable
private fun MarketServiceCard(service: GhajarMarketService, onImport: () -> Unit, onRenew: () -> Unit) {
    val c = ghajarColors
    val lang = LocalLang.current
    val context = LocalContext.current
    Slab(spacing = GhajarSpacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(service.productName.ifBlank { service.username }, fontWeight = FontWeight.Bold,
                    color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(mixedText(listOf(service.username, service.panelName).filter { it.isNotBlank() }.joinToString(" · ")),
                    style = MaterialTheme.typography.labelSmall, color = c.textMuted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (service.isTest) {
                Text("تست", style = MaterialTheme.typography.labelSmall, color = c.premium)
            }
        }
        if (!service.reachable) {
            Text("پنل فروشنده همین حالا جواب نداد؛ آمار مصرف نمایش داده نمی‌شود.",
                style = MaterialTheme.typography.labelSmall, color = c.warning)
        } else {
            if (service.dataLimit > 0) {
                val fraction = (service.used.toFloat() / service.dataLimit.toFloat()).coerceIn(0f, 1f)
                MarketMeter(
                    label = "حجم",
                    value = localizeDigits(gbText(service.used), lang) + " از " +
                        localizeDigits(gbText(service.dataLimit), lang) + " گیگ",
                    fraction = fraction,
                    warn = fraction > 0.85f
                )
            } else if (service.volumeGb == 0) {
                InfoLine("حجم", "نامحدود")
            }
            if (service.expire > 0) {
                val now = System.currentTimeMillis() / 1000
                val left = ((service.expire - now) / 86_400).coerceAtLeast(0)
                val total = service.timeDays.takeIf { it > 0 }?.toLong() ?: left.coerceAtLeast(1)
                MarketMeter(
                    label = "زمان",
                    value = localizeDigits(left.toString(), lang) + " روز مانده · " + localizeDigits(marketJalali(service.expire, false), lang),
                    fraction = (1f - left.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                    warn = left <= 3
                )
            }
            if (service.status.isNotBlank()) InfoLine("وضعیت", serviceStatusLabel(service.status))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
            GhostPill("افزودن به برنامه", onImport, Modifier.weight(1f), icon = Icons.Filled.Download, minHeight = 42.dp)
            if (!service.isTest) {
                GhostPill("تمدید", onRenew, Modifier.weight(1f), icon = Icons.Filled.Autorenew, minHeight = 42.dp)
            }
        }
        if (service.subscription.isNotBlank()) {
            GhostPill("کپی لینک اشتراک", { copyToClipboard(context, service.subscription) },
                icon = Icons.Filled.ContentCopy, minHeight = 40.dp)
        }
    }
}

private fun serviceStatusLabel(status: String): String = when (status.lowercase()) {
    "active" -> "فعال"
    "disabled", "disable" -> "غیرفعال"
    "limited" -> "حجم تمام شده"
    "expired" -> "منقضی"
    "on_hold" -> "در انتظار اولین اتصال"
    else -> status
}

@Composable
private fun MarketMeter(label: String, value: String, fraction: Float, warn: Boolean) {
    val c = ghajarColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textMuted, modifier = Modifier.weight(1f))
            Text(mixedText(value), style = MaterialTheme.typography.labelSmall, color = c.textPrimary)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(GhajarRadius.pill)),
            color = if (warn) c.warning else c.primary,
            trackColor = c.border
        )
    }
}

/** Renewing one service: pick the plan, pick how to pay. */
@Composable
private fun MarketRenewPanel(
    api: GhajarStoreApi,
    home: GhajarMarketHome,
    service: GhajarMarketService,
    onCancel: () -> Unit,
    onOrdered: (GhajarMarketOrder) -> Unit
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val products = home.catalog.products.filter {
        it.location.isBlank() || it.location == "/all" || it.location == service.panelName
    }.ifEmpty { home.catalog.products }
    var productCode by remember(service.invoiceId) {
        mutableStateOf(products.firstOrNull { it.name == service.productName }?.code ?: products.firstOrNull()?.code)
    }
    var method by remember(service.invoiceId) { mutableStateOf(home.catalog.methods.firstOrNull()?.id) }
    var busy by remember(service.invoiceId) { mutableStateOf(false) }
    var error by remember(service.invoiceId) { mutableStateOf<String?>(null) }

    Slab(accent = c.primary, spacing = GhajarSpacing.sm) {
        Text("تمدید " + service.productName.ifBlank { service.username }, fontWeight = FontWeight.Bold,
            color = c.textPrimary)
        Text(mixedText(service.username), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
    }
    Text("پلن تمدید", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    products.forEach { product ->
        ChoiceRow(
            title = product.name,
            subtitle = listOfNotNull(
                product.volumeGb.takeIf { it > 0 }?.let { localizeDigits(it.toString(), lang) + " گیگ" },
                product.timeDays.takeIf { it > 0 }?.let { localizeDigits(it.toString(), lang) + " روز" }
            ).joinToString("  •  ").takeIf { it.isNotBlank() },
            value = localizeDigits(formatToman(product.price), lang) + " تومان",
            selected = productCode == product.code,
            onSelect = { productCode = product.code }
        )
    }
    Text("روش پرداخت", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    home.wallet?.let { wallet ->
        ChoiceRow(title = "کیف پول این فروشگاه",
            subtitle = "موجودی: " + localizeDigits(formatToman(wallet), lang) + " تومان",
            selected = method == "wallet", onSelect = { method = "wallet" })
    }
    home.catalog.methods.forEach { m ->
        ChoiceRow(title = m.label, subtitle = m.note.takeIf { it.isNotBlank() },
            selected = method == m.id, onSelect = { method = m.id })
    }
    error?.let { Text(it, color = c.error, style = MaterialTheme.typography.labelMedium) }
    PillButton(
        if (busy) "در حال ثبت تمدید…" else "تمدید همین سرویس",
        {
            busy = true; error = null
            scope.launch {
                runCatching {
                    api.marketOrderStart(home.shop.id, productCode.orEmpty(), "", method.orEmpty(), kind = "renew",
                        invoiceId = service.invoiceId, username = service.username)
                }.onSuccess { onOrdered(it) }.onFailure { error = it.message ?: "تمدید ثبت نشد" }
                busy = false
            }
        },
        enabled = !busy && productCode != null && method != null && home.shop.canSell,
        icon = Icons.Filled.Autorenew
    )
    GhostPill("انصراف", onCancel)
}

// ----------------------------------------------------------- messages tab

@Composable
private fun MarketMessagesTab(api: GhajarStoreApi, shopId: Int, onRead: () -> Unit) {
    val c = ghajarColors
    val lang = LocalLang.current
    var messages by remember(shopId) { mutableStateOf<List<GhajarMarketMessage>?>(null) }
    var error by remember(shopId) { mutableStateOf<String?>(null) }
    var reload by remember(shopId) { mutableIntStateOf(0) }

    LaunchedEffect(shopId, reload) {
        runCatching { api.marketMessages(shopId) }
            .onSuccess { list ->
                messages = list; error = null
                if (list.any { !it.read }) {
                    runCatching { api.marketMessagesRead(shopId) }.onSuccess { onRead() }
                }
            }
            .onFailure { error = it.message ?: "پیام‌ها خوانده نشد" }
    }

    val list = messages
    when {
        list == null && error == null -> SkinLoading("در حال گرفتن پیام‌ها")
        list == null -> SkinError(error!!, retryText = "تلاش دوباره", onRetry = { reload++ })
        list.isEmpty() -> SkinEmpty("پیامی از این فروشگاه ندارید",
            hint = "تأیید پرداخت، ساخت سرویس، پاسخ تیکت و اطلاعیه‌های فروشگاه اینجا می‌آید.")
        else -> Column(verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
            list.forEach { m ->
                Slab(accent = if (m.read) null else c.primary, spacing = GhajarSpacing.xs) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.title, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text(localizeDigits(marketJalali(m.createdAt), lang), style = MaterialTheme.typography.labelSmall,
                            color = c.textMuted)
                    }
                    if (m.body.isNotBlank()) {
                        Text(m.body, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------- wallet tab

@Composable
private fun MarketWalletTab(api: GhajarStoreApi, home: GhajarMarketHome, onOrdered: (GhajarMarketOrder) -> Unit) {
    val c = ghajarColors
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val shopId = home.shop.id
    var wallet by remember(shopId) { mutableStateOf<GhajarMarketWallet?>(null) }
    var error by remember(shopId) { mutableStateOf<String?>(null) }
    var amount by remember(shopId) { mutableStateOf("") }
    var method by remember(shopId) { mutableStateOf(home.catalog.methods.firstOrNull()?.id) }
    var busy by remember(shopId) { mutableStateOf(false) }
    var reload by remember(shopId) { mutableIntStateOf(0) }

    LaunchedEffect(shopId, reload) {
        runCatching { api.marketWallet(shopId) }
            .onSuccess { wallet = it; error = null }
            .onFailure { error = it.message ?: "کیف پول خوانده نشد" }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
        Slab(padding = 18.dp, spacing = GhajarSpacing.sm) {
            Rail("کیف پول " + home.shop.name)
            if (wallet == null && error != null) {
                SkinError(error!!, retryText = "تلاش دوباره", onRetry = { error = null; reload++ })
            }
            Text(
                wallet?.let { localizeDigits(formatToman(it.balance), lang) } ?: "…",
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = c.highlight
            )
            Text("تومان · فقط برای خرید و تمدید از همین فروشگاه", style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary)
            val presets = listOf(50_000L, 100_000L, 200_000L, 500_000L)
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                presets.forEach { preset ->
                    val on = amount == preset.toString()
                    Text(
                        localizeDigits(formatToman(preset), lang),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) c.onPrimary else c.textSecondary,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(GhajarRadius.pill))
                            .background(if (on) c.primary else c.card)
                            .clickable { amount = preset.toString() }
                            .padding(vertical = 8.dp)
                    )
                }
            }
            SkinField(
                value = amount,
                onValueChange = { amount = GhajarUiRules.asciiDigits(it).filter(Char::isDigit).take(10) },
                label = "مبلغ شارژ (تومان)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            home.catalog.methods.forEach { m ->
                ChoiceRow(title = m.label, subtitle = m.note.takeIf { it.isNotBlank() },
                    selected = method == m.id, onSelect = { method = m.id })
            }
            error?.let { Text(it, color = c.error, style = MaterialTheme.typography.labelMedium) }
            PillButton(
                if (busy) "در حال ثبت…" else "شارژ کیف پول",
                {
                    busy = true; error = null
                    scope.launch {
                        runCatching {
                            api.marketOrderStart(shopId, "", "", method.orEmpty(), kind = "wallet",
                                amount = amount.toLongOrNull() ?: 0)
                        }.onSuccess { onOrdered(it) }.onFailure { error = it.message ?: "شارژ ثبت نشد" }
                        busy = false
                    }
                },
                enabled = !busy && (amount.toLongOrNull() ?: 0) >= 1000 && method != null,
                icon = Icons.Filled.AccountBalanceWallet
            )
        }
        MarketGiftRedeem(api, shopId, onRedeemed = { reload++ })
        if (home.mine) {
            MarketOwnerCodes(api, shopId)
        }
        val history = wallet?.history.orEmpty()
        if (history.isNotEmpty()) {
            Rail("گردش کیف پول")
            history.forEach { entry ->
                Slab(spacing = GhajarSpacing.xs) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.title.ifBlank { entry.kind }, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            mixedText((if (entry.amount >= 0) "+" else "") + localizeDigits(formatToman(entry.amount), lang)),
                            fontWeight = FontWeight.Bold,
                            color = if (entry.amount >= 0) c.primary else c.error
                        )
                    }
                    Text(
                        mixedText(localizeDigits(marketJalali(entry.createdAt), lang) + " · مانده " +
                            localizeDigits(formatToman(entry.balanceAfter), lang)),
                        style = MaterialTheme.typography.labelSmall, color = c.textMuted
                    )
                }
            }
        }
    }
}

/** A gift code into this shop's wallet: the shop's own codes and the seller bot's. */
@Composable
private fun MarketGiftRedeem(api: GhajarStoreApi, shopId: Int, onRedeemed: () -> Unit) {
    val c = ghajarColors
    val scope = rememberCoroutineScope()
    var code by remember(shopId) { mutableStateOf("") }
    var busy by remember(shopId) { mutableStateOf(false) }
    var result by remember(shopId) { mutableStateOf<Pair<Boolean, String>?>(null) }
    Slab(padding = 18.dp, spacing = GhajarSpacing.sm, accent = c.highlight) {
        Rail("🎁 کد هدیه")
        SkinField(value = code, onValueChange = { code = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' }.take(40) },
            label = "کد هدیه این فروشگاه", placeholder = "مثلاً NOROOZ")
        PillButton(if (busy) "در حال بررسی…" else "افزودن به کیف پول", {
            busy = true; result = null
            scope.launch {
                result = runCatching { api.marketGiftRedeem(shopId, code.trim()) }
                    .getOrElse { false to (it.message ?: "کد ثبت نشد") }
                if (result?.first == true) { code = ""; onRedeemed() }
                busy = false
            }
        }, enabled = !busy && code.trim().length >= 3, icon = Icons.Filled.CardGiftcard)
        result?.let { (ok, msg) -> Text(msg, color = if (ok) c.primary else c.error, style = MaterialTheme.typography.labelMedium) }
    }
}

/**
 * The owner's own discount and gift codes, with the same fields as the web
 * panel. Codes made in the seller's own bot are listed too: they work in the
 * app, and are managed where they were made.
 */
@Composable
private fun MarketOwnerCodes(api: GhajarStoreApi, shopId: Int) {
    val c = ghajarColors
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var codes by remember(shopId) { mutableStateOf<GhajarMarketCodes?>(null) }
    var message by remember(shopId) { mutableStateOf<Pair<Boolean, String>?>(null) }
    var gift by remember(shopId) { mutableStateOf(false) }
    var busy by remember(shopId) { mutableStateOf(false) }
    var code by remember(shopId) { mutableStateOf("") }
    var value by remember(shopId) { mutableStateOf("") }
    var days by remember(shopId) { mutableStateOf("") }
    var hours by remember(shopId) { mutableStateOf("") }
    var maxUses by remember(shopId) { mutableStateOf("") }
    var perUser by remember(shopId) { mutableStateOf("") }
    var firstOnly by remember(shopId) { mutableStateOf(false) }
    var product by remember(shopId) { mutableStateOf("") }
    var panel by remember(shopId) { mutableStateOf("") }

    fun run(action: String, fields: Map<String, String> = emptyMap()) {
        busy = true
        scope.launch {
            runCatching { api.marketCodes(shopId, action, fields) }
                .onSuccess { (list, msg) -> codes = list; message = if (msg.isNotBlank()) true to msg else null
                    if (action == "code_add") { code = ""; value = ""; days = ""; hours = ""; maxUses = ""; perUser = ""; firstOnly = false; product = ""; panel = "" } }
                .onFailure { message = false to (it.message ?: "انجام نشد") }
            busy = false
        }
    }
    LaunchedEffect(shopId) { run("shop_codes") }
    val digits: (String) -> String = { GhajarUiRules.asciiDigits(it).filter(Char::isDigit).take(9) }

    Slab(padding = 18.dp, spacing = GhajarSpacing.sm, accent = c.primary) {
        Rail("🛠 مدیریت کدهای فروشگاه شما")
        Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
            listOf(false to "🎟 کد تخفیف", true to "🎁 کد هدیه").forEach { (isGift, label) ->
                val on = gift == isGift
                Text(label, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) c.onPrimary else c.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(GhajarRadius.pill))
                        .background(if (on) c.primary else c.card).clickable { gift = isGift }.padding(vertical = 10.dp))
            }
        }
        val list = if (gift) codes?.gifts.orEmpty() else codes?.discounts.orEmpty()
        if (codes == null && busy) SkinLoading("در حال خواندن کدها")
        if (codes != null && list.isEmpty()) Text("هنوز کدی در این بخش نیست.", color = c.textMuted)
        list.forEach { item ->
            val expired = item.expiresAt in 1 until System.currentTimeMillis() / 1000
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GhajarRadius.md)).background(c.card)
                .padding(horizontal = GhajarSpacing.md, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (item.active && !expired) "🟢 " else "🔴 ") + item.code, fontWeight = FontWeight.Bold,
                        color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text(if (gift) localizeDigits(formatToman(item.value.toLong()), lang) + " تومان"
                        else localizeDigits(item.value.toBigDecimal().stripTrailingZeros().toPlainString(), lang) + "٪",
                        color = c.highlight, fontWeight = FontWeight.Bold)
                }
                val facts = buildList {
                    add("استفاده " + item.used + (if (item.maxUses > 0) "/" + item.maxUses else ""))
                    add(if (item.expiresAt > 0) "تا " + marketJalali(item.expiresAt) else "بدون انقضا")
                    if (item.perUser > 0) add("هر نفر ${item.perUser} بار")
                    if (item.firstOnly) add("فقط خرید اول")
                    if (item.product.isNotBlank()) add("پلن ${item.product}")
                    if (item.panel.isNotBlank()) add("سرور ${item.panel}")
                    add(if (item.source == "seller") "🤖 از ربات خودتان" else "قاجار")
                }
                Text(mixedText(localizeDigits(facts.joinToString(" · "), lang)), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                if (item.source != "seller" && item.id > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                        GhostPill(if (item.active) "غیرفعال" else "فعال", {
                            run("code_toggle", mapOf("kind" to if (gift) "gift" else "discount", "id" to item.id.toString()))
                        }, modifier = Modifier.weight(1f), enabled = !busy, minHeight = 36.dp)
                        GhostPill("حذف", {
                            run("code_delete", mapOf("kind" to if (gift) "gift" else "discount", "id" to item.id.toString()))
                        }, modifier = Modifier.weight(1f), enabled = !busy, accent = c.error, minHeight = 36.dp)
                    }
                }
            }
        }
        Rail(if (gift) "کد هدیهٔ تازه" else "کد تخفیف تازه")
        SkinField(code, { code = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' }.take(40).uppercase() }, "کد")
        SkinField(value, { value = digits(it) }, if (gift) "مبلغ هدیه (تومان)" else "درصد تخفیف",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
            SkinField(days, { days = digits(it) }, "روز", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            SkinField(hours, { hours = digits(it) }, "ساعت", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            SkinField(maxUses, { maxUses = digits(it) }, "سقف کل", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        if (!gift) {
            SkinField(perUser, { perUser = digits(it) }, "سقف برای هر نفر (۰ = بی‌نهایت)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                SkinField(product, { product = it.take(100) }, "کد پلن (اختیاری)", modifier = Modifier.weight(1f))
                SkinField(panel, { panel = it.take(100) }, "کد سرور (اختیاری)", modifier = Modifier.weight(1f))
            }
            ChoiceRow(title = "فقط برای اولین خرید", selected = firstOnly, onSelect = { firstOnly = !firstOnly })
        }
        Text("روز و ساعت خالی یعنی بدون انقضا؛ سقف خالی یعنی بی‌نهایت." + if (gift) " هر خریدار یک بار." else "",
            style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        message?.let { (ok, msg) -> Text(msg, color = if (ok) c.primary else c.error, style = MaterialTheme.typography.labelMedium) }
        PillButton(if (busy) "در حال ثبت…" else "ثبت کد", {
            run("code_add", mapOf("kind" to if (gift) "gift" else "discount", "code" to code,
                (if (gift) "amount" else "percent") to value, "days" to days.ifBlank { "0" }, "hours" to hours.ifBlank { "0" },
                "max_uses" to maxUses.ifBlank { "0" }, "per_user" to perUser.ifBlank { "0" },
                "first_only" to if (firstOnly) "1" else "0", "product" to product.trim(), "panel" to panel.trim()))
        }, enabled = !busy && code.length >= 3 && value.isNotBlank(), icon = Icons.Filled.CardGiftcard)
    }
}

// ------------------------------------------------------------ support tab

@Composable
private fun MarketSupportTab(api: GhajarStoreApi, shopId: Int) {
    val c = ghajarColors
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var tickets by remember(shopId) { mutableStateOf<List<GhajarMarketTicket>?>(null) }
    var error by remember(shopId) { mutableStateOf<String?>(null) }
    var reload by remember(shopId) { mutableIntStateOf(0) }
    var open by remember(shopId) { mutableStateOf<GhajarMarketTicketThread?>(null) }
    var subject by remember(shopId) { mutableStateOf("") }
    var body by remember(shopId) { mutableStateOf("") }
    var reply by remember(shopId) { mutableStateOf("") }
    var busy by remember(shopId) { mutableStateOf(false) }

    LaunchedEffect(shopId, reload) {
        runCatching { api.marketTickets(shopId) }
            .onSuccess { tickets = it; error = null }
            .onFailure { error = it.message ?: "تیکت‌ها خوانده نشد" }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
        val thread = open
        if (thread != null) {
            GhostPill("همهٔ تیکت‌ها", { open = null; reload++ }, icon = Icons.Filled.ChevronLeft)
            Rail(thread.subject)
            thread.messages.forEach { m ->
                Slab(accent = if (m.fromSeller) c.primary else c.border, spacing = GhajarSpacing.xs) {
                    Row {
                        Text(if (m.fromSeller) "فروشنده" else "شما", fontWeight = FontWeight.Bold,
                            color = if (m.fromSeller) c.primary else c.textPrimary, modifier = Modifier.weight(1f))
                        Text(localizeDigits(marketJalali(m.createdAt), lang), style = MaterialTheme.typography.labelSmall,
                            color = c.textMuted)
                    }
                    Text(m.body, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            if (thread.status == "closed") {
                Text("این تیکت بسته شده است.", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            } else {
                SkinField(value = reply, onValueChange = { reply = it.take(2000) }, label = "پاسخ شما",
                    singleLine = false, minLines = 3)
                error?.let { Text(it, color = c.error, style = MaterialTheme.typography.labelMedium) }
                PillButton(if (busy) "در حال ارسال…" else "ارسال", {
                    busy = true
                    scope.launch {
                        runCatching { api.marketTicketReply(thread.id, reply) }
                            .onSuccess { open = it; reply = ""; error = null }
                            .onFailure { error = it.message ?: "ارسال نشد" }
                        busy = false
                    }
                }, enabled = !busy && reply.isNotBlank(), icon = Icons.Filled.Send)
                GhostPill("بستن تیکت", {
                    scope.launch { runCatching { api.marketTicketClose(thread.id) }.onSuccess { open = it } }
                }, icon = Icons.Filled.Lock)
            }
            return@Column
        }

        Slab(spacing = GhajarSpacing.sm) {
            Text("تیکت تازه به پشتیبانی این فروشگاه", fontWeight = FontWeight.Bold, color = c.textPrimary)
            SkinField(value = subject, onValueChange = { subject = it.take(120) }, label = "موضوع")
            SkinField(value = body, onValueChange = { body = it.take(2000) }, label = "متن پیام",
                singleLine = false, minLines = 3)
            error?.let { Text(it, color = c.error, style = MaterialTheme.typography.labelMedium) }
            PillButton(if (busy) "در حال ارسال…" else "ارسال تیکت", {
                busy = true
                scope.launch {
                    runCatching { api.marketTicketCreate(shopId, subject, body) }
                        .onSuccess { id ->
                            subject = ""; body = ""; error = null; reload++
                            runCatching { api.marketTicketThread(id) }.onSuccess { open = it }
                        }
                        .onFailure { error = it.message ?: "تیکت ثبت نشد" }
                    busy = false
                }
            }, enabled = !busy && subject.isNotBlank() && body.isNotBlank(), icon = Icons.Filled.Send)
            Text("پیام مستقیم برای فروشنده فرستاده می‌شود و پاسخش در همین‌جا و در «پیام‌ها» می‌آید.",
                style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }

        val list = tickets
        when {
            list == null && error == null -> SkinLoading("در حال گرفتن تیکت‌ها")
            list.isNullOrEmpty() -> Unit
            else -> {
                Rail("تیکت‌های شما")
                list.orEmpty().forEach { t ->
                    Slab(spacing = GhajarSpacing.xs, onClick = {
                        scope.launch { runCatching { api.marketTicketThread(t.id) }.onSuccess { open = it } }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.subject, fontWeight = FontWeight.Bold, color = c.textPrimary,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (t.status) { "answered" -> "پاسخ داده شد"; "closed" -> "بسته"; else -> "منتظر پاسخ" },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (t.status) { "answered" -> c.primary; "closed" -> c.textMuted; else -> c.warning }
                            )
                        }
                        Text(localizeDigits(marketJalali(t.updatedAt), lang), style = MaterialTheme.typography.labelSmall,
                            color = c.textMuted)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------- transactions tab

@Composable
private fun MarketTransactionsTab(api: GhajarStoreApi, shopId: Int) {
    val c = ghajarColors
    val lang = LocalLang.current
    var rows by remember(shopId) { mutableStateOf<List<GhajarMarketTransaction>?>(null) }
    var error by remember(shopId) { mutableStateOf<String?>(null) }
    var reload by remember(shopId) { mutableIntStateOf(0) }

    LaunchedEffect(shopId, reload) {
        runCatching { api.marketTransactions(shopId) }
            .onSuccess { rows = it; error = null }
            .onFailure { error = it.message ?: "تراکنش‌ها خوانده نشد" }
    }
    val list = rows
    when {
        list == null && error == null -> SkinLoading("در حال گرفتن تراکنش‌ها")
        list == null -> SkinError(error!!, retryText = "تلاش دوباره", onRetry = { reload++ })
        list.isEmpty() -> SkinEmpty("هنوز تراکنشی در این فروشگاه ندارید")
        else -> Column(verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
            list.forEach { t ->
                Slab(spacing = GhajarSpacing.xs) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.productName.ifBlank {
                                when (t.kind) {
                                    "wallet" -> "شارژ کیف پول"; "renew" -> "تمدید"; "test" -> "سرویس تست"; else -> "خرید سرویس"
                                }
                            },
                            fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(localizeDigits(formatToman(t.amount), lang) + " تومان", fontWeight = FontWeight.Bold,
                            color = c.textPrimary)
                    }
                    Row {
                        Text(
                            when (t.status) {
                                MARKET_STATUS_PAID -> "پرداخت‌شده"
                                MARKET_STATUS_REVIEW -> "منتظر تأیید فروشنده"
                                MARKET_STATUS_AWAITING -> "منتظر پرداخت"
                                MARKET_STATUS_REJECTED -> "رد شده"
                                MARKET_STATUS_FAILED -> "ناموفق"
                                "expired" -> "منقضی"
                                else -> t.status
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (t.status) {
                                MARKET_STATUS_PAID -> c.primary
                                MARKET_STATUS_REJECTED, MARKET_STATUS_FAILED -> c.error
                                else -> c.warning
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text(localizeDigits("#" + t.id + " · " + marketJalali(t.createdAt), lang),
                            style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                    }
                    if (t.rejectReason.isNotBlank()) {
                        Text(t.rejectReason, style = MaterialTheme.typography.labelSmall, color = c.error)
                    }
                }
            }
        }
    }
}

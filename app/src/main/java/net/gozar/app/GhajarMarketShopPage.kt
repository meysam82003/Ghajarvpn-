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

private enum class BuyMode { READY, CUSTOM, TEST }

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
    var mode by remember(shop.id) { mutableStateOf(BuyMode.READY) }
    var productCode by remember(shop.id) { mutableStateOf<String?>(null) }
    var method by remember(shop.id) { mutableStateOf(catalog.methods.firstOrNull()?.id) }
    var gb by remember(shop.id) { mutableStateOf("") }
    var days by remember(shop.id) { mutableStateOf("") }
    var starting by remember(shop.id) { mutableStateOf(false) }
    var actionError by remember(shop.id) { mutableStateOf<String?>(null) }

    val panel = catalog.panels.firstOrNull { it.code == panelCode }
    val modes = buildList {
        add(BuyMode.READY)
        if (panel?.custom == true && panel.gbPrice > 0) add(BuyMode.CUSTOM)
        if (panel?.test == true && home.testEnabled) add(BuyMode.TEST)
    }
    if (mode !in modes) mode = BuyMode.READY

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
        if (!shop.canSell) {
            SkinError(shop.closedReason.ifBlank { "این فروشگاه فعلاً فروش جدید ندارد." })
        }
        if (!home.reachable) {
            SkinError("سرور این فروشگاه همین حالا جواب نداد؛ پلن‌ها ممکن است کامل نباشند. چند لحظه بعد دوباره باز کن.")
        }
        if (home.blocked) {
            SkinError("این فروشگاه امکان خرید را برای حساب شما بسته است.")
        }

        if (catalog.panels.size > 1) {
            Text("سرور", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            catalog.panels.forEach { p ->
                ChoiceRow(
                    title = listOf(p.flag, p.name).filter { it.isNotBlank() }.joinToString(" "),
                    subtitle = listOfNotNull(
                        p.country.takeIf { it.isNotBlank() },
                        if (p.custom && p.gbPrice > 0) "پلن دلخواه" else null,
                        if (p.test && home.testEnabled) "تست رایگان" else null
                    ).joinToString(" · ").takeIf { it.isNotBlank() },
                    selected = panelCode == p.code,
                    onSelect = { panelCode = p.code; productCode = null }
                )
            }
        }

        if (modes.size > 1) {
            SlidingSegments(
                labels = modes.map {
                    when (it) {
                        BuyMode.READY -> "پلن‌های آماده"
                        BuyMode.CUSTOM -> "پلن دلخواه"
                        BuyMode.TEST -> "تست رایگان"
                    }
                },
                selected = modes.indexOf(mode),
                onSelect = { mode = modes[it] }
            )
        }

        var amount: Long? = null
        when (mode) {
            BuyMode.READY -> {
                // Plans for the chosen server; a plan on "/all" or with no
                // location is sold on every server. If the seller labelled
                // nothing, every plan is shown rather than none.
                val forPanel = catalog.products.filter {
                    it.location.isBlank() || it.location == "/all" || it.location == panel?.name
                }.ifEmpty { catalog.products }
                if (forPanel.isEmpty()) {
                    SkinEmpty("این فروشگاه پلنی برای فروش ندارد", icon = Icons.Filled.ShoppingBag)
                }
                forPanel.forEach { product ->
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
                amount = forPanel.firstOrNull { it.code == productCode }?.price
            }

            BuyMode.CUSTOM -> {
                val p = panel!!
                Slab(spacing = GhajarSpacing.sm) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tune, null, tint = c.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(GhajarSpacing.sm))
                        Text("پلن دلخواه", fontWeight = FontWeight.Bold, color = c.textPrimary)
                    }
                    Text(
                        mixedText("هر گیگ " + localizeDigits(formatToman(p.gbPrice), lang) + " تومان" +
                            (if (p.dayPrice > 0) " · هر روز " + localizeDigits(formatToman(p.dayPrice), lang) + " تومان" else "")),
                        style = MaterialTheme.typography.labelMedium, color = c.textSecondary
                    )
                    SkinField(
                        value = gb,
                        onValueChange = { gb = GhajarUiRules.asciiDigits(it).filter(Char::isDigit).take(5) },
                        label = "حجم (گیگ)",
                        helper = rangeHint(p.minGb, p.maxGb, "گیگ", lang),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    SkinField(
                        value = days,
                        onValueChange = { days = GhajarUiRules.asciiDigits(it).filter(Char::isDigit).take(4) },
                        label = "مدت (روز)",
                        helper = rangeHint(p.minDays, p.maxDays, "روز", lang),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    val g = gb.toIntOrNull() ?: 0
                    val d = days.toIntOrNull() ?: 0
                    if (g > 0 && d > 0) {
                        amount = p.gbPrice * g + p.dayPrice * d
                        InfoLine("قیمت", localizeDigits(formatToman(amount!!), lang) + " تومان")
                    }
                }
            }

            BuyMode.TEST -> {
                val p = panel!!
                Slab(accent = c.premium, spacing = GhajarSpacing.sm) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CardGiftcard, null, tint = c.premium, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(GhajarSpacing.sm))
                        Text("سرویس تست رایگان", fontWeight = FontWeight.Bold, color = c.textPrimary)
                    }
                    Text(
                        mixedText(listOfNotNull(
                            p.testMb.takeIf { it > 0 }?.let {
                                if (it >= 1024) localizeDigits(gbText(it * 1_048_576L), lang) + " گیگ"
                                else localizeDigits(it.toString(), lang) + " مگ"
                            },
                            p.testHours.takeIf { it > 0 }?.let { localizeDigits(it.toString(), lang) + " ساعت" }
                        ).joinToString(" · ").ifBlank { "اندازهٔ تست را فروشنده تعیین می‌کند" }),
                        style = MaterialTheme.typography.labelMedium, color = c.textSecondary
                    )
                    if (home.testUsed) {
                        Text("تست این فروشگاه را قبلاً گرفته‌اید.", style = MaterialTheme.typography.labelSmall,
                            color = c.warning)
                    }
                }
            }
        }

        if (mode != BuyMode.TEST) {
            Text("روش پرداخت", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            val wallet = home.wallet
            if (signedIn && wallet != null) {
                ChoiceRow(
                    title = "کیف پول این فروشگاه",
                    subtitle = "موجودی: " + localizeDigits(formatToman(wallet), lang) + " تومان",
                    selected = method == "wallet",
                    onSelect = { method = "wallet" }
                )
            }
            if (catalog.methods.isEmpty() && wallet == null) {
                SkinError("این فروشگاه هنوز روش پرداختی متصل نکرده است.")
            }
            catalog.methods.forEach { m ->
                ChoiceRow(
                    title = m.label,
                    subtitle = m.note.takeIf { it.isNotBlank() },
                    selected = method == m.id,
                    onSelect = { method = m.id }
                )
            }
            Slab(accent = c.warning, spacing = GhajarSpacing.xs) {
                Text(
                    "پرداخت شما به همین فروشگاه انجام می‌شود، نه به قاجار. رسید هم برای خودِ فروشنده می‌رود و تأیید یا رد آن با اوست.",
                    style = MaterialTheme.typography.labelSmall, color = c.textSecondary
                )
            }
        }

        actionError?.let { Text(it, color = c.error, style = MaterialTheme.typography.labelMedium) }

        val ready = when (mode) {
            BuyMode.READY -> productCode != null && method != null
            BuyMode.CUSTOM -> amount != null && method != null
            BuyMode.TEST -> !home.testUsed
        }
        PillButton(
            text = when {
                !signedIn -> "اتصال حساب برای خرید"
                starting -> "در حال ثبت سفارش…"
                mode == BuyMode.TEST -> "دریافت تست رایگان"
                amount != null -> "ثبت سفارش · " + localizeDigits(formatToman(amount!!), lang) + " تومان"
                else -> "ثبت سفارش"
            },
            onClick = {
                if (!signedIn) { onSignIn(); return@PillButton }
                starting = true
                actionError = null
                scope.launch {
                    runCatching {
                        when (mode) {
                            BuyMode.READY -> api.marketOrderStart(shop.id, productCode.orEmpty(), panelCode.orEmpty(),
                                method.orEmpty())
                            BuyMode.CUSTOM -> api.marketOrderStart(shop.id, "customvolume", panelCode.orEmpty(),
                                method.orEmpty(), plan = "custom", volumeGb = gb.toIntOrNull() ?: 0,
                                timeDays = days.toIntOrNull() ?: 0)
                            BuyMode.TEST -> api.marketOrderStart(shop.id, "", panelCode.orEmpty(), "free",
                                kind = "test")
                        }
                    }
                        .onSuccess { onOrdered(it) }
                        .onFailure { actionError = it.message ?: "ثبت سفارش انجام نشد" }
                    starting = false
                }
            },
            enabled = !signedIn || (!starting && ready && shop.canSell && !home.blocked),
            icon = if (mode == BuyMode.TEST) Icons.Filled.CardGiftcard else Icons.Filled.ShoppingBag
        )

        MarketReviews(api, shop, signedIn, onSignIn)
    }
}

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
                list.forEach { service ->
                    MarketServiceCard(service,
                        onImport = {
                            scope.launch {
                                runCatching { api.marketServiceDelivery(shopId, service.invoiceId, service.username) }
                                    .onSuccess { delivery = it }
                                    .onFailure { error = it.message ?: "کانفیگ‌ها دریافت نشد" }
                            }
                        },
                        onRenew = { renewing = service })
                }
                delivery?.let { MarketDelivery(it, api, store) }
                error?.let { Text(it, color = c.error, style = MaterialTheme.typography.labelMedium) }
                GhostPill("بازخوانی", { reload++ }, icon = Icons.Filled.Refresh)
            }
        }
    }
}

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

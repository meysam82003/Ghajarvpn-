package net.gozar.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The marketplace: other people's shops, inside this app.
 *
 * ## What the buyer never has to do
 *
 * Open Telegram. A shop here is somebody else's Faoxima installation, but the
 * buyer picks a plan, pays, sends their receipt and gets their config without
 * leaving this screen. The seller's bot and channel are shown under "about
 * this shop" as a way to reach them, not as a step in buying.
 *
 * ## Who the money goes to, which the screen has to be honest about
 *
 * The seller. Card-to-card shows the *seller's* card and forwards the receipt
 * to the *seller*, who approves it - it is their bank account, so it is their
 * decision. A gateway order opens the seller's own gateway. Nothing on this
 * screen takes a payment on our behalf, and the copy says so rather than
 * leaving a buyer to assume we are holding their money.
 *
 * ## The one thing this screen refuses to do
 *
 * Provision anything itself. A service appears only after the seller has
 * approved the payment and the server has created it on the seller's panel.
 * So "receipt sent" and "service ready" are two different states with two
 * different screens, and the second one never appears early.
 */

internal const val MARKET_STATUS_AWAITING = "awaiting_receipt"
internal const val MARKET_STATUS_REVIEW = "review"
internal const val MARKET_STATUS_PAID = "paid"
internal const val MARKET_STATUS_REJECTED = "rejected"
internal const val MARKET_STATUS_FAILED = "failed"

/** Where the marketplace currently is. One value, so back is unambiguous. */
private sealed interface MarketRoute {
    object List : MarketRoute
    data class Shop(val id: Int) : MarketRoute
    data class Order(val shopId: Int, val order: GhajarMarketOrder) : MarketRoute
    object Register : MarketRoute
}

/**
 * How the list is ordered, and what it leaves out.
 *
 * Every one of these runs on figures the list already carries, so switching
 * chips costs no request. `minPrice`/`maxPrice` are zero for a shop whose
 * catalogue has not been read yet, and the price sorts push those to the end
 * rather than calling them free or infinitely expensive.
 */
private enum class MarketSort(val label: String) {
    BEST("بهترین‌ها"),
    MOST_REVIEWED("پرنظرترین"),
    WORST("منفورترین"),
    DISCOUNTED("در تخفیف"),
    CHEAPEST("ارزان‌ترین"),
    PRICIEST("گران‌ترین")
}

private fun List<GhajarMarketShop>.sortedFor(sort: MarketSort): List<GhajarMarketShop> = when (sort) {
    // "Best" is not the raw average: a single five-star review would beat a
    // shop with two hundred ratings at 4.8, which is how rating lists get
    // gamed. Shops with no ratings at all rank below any rated shop instead
    // of sitting in the middle.
    MarketSort.BEST -> sortedWith(
        compareByDescending<GhajarMarketShop> { it.reviewCount > 0 }
            .thenByDescending { it.stars * kotlin.math.min(it.reviewCount, 20) / 20.0 }
            .thenByDescending { it.reviewCount }
    )
    MarketSort.MOST_REVIEWED -> sortedWith(
        compareByDescending<GhajarMarketShop> { it.reviewCount }.thenByDescending { it.stars }
    )
    // Only rated shops can be "most disliked". A shop nobody has reviewed is
    // unknown, not bad, and putting it at the top of this list would be a
    // claim about a seller that no buyer ever made.
    MarketSort.WORST -> filter { it.reviewCount > 0 }
        .sortedWith(compareBy<GhajarMarketShop> { it.stars }.thenByDescending { it.reviewCount })
    MarketSort.DISCOUNTED -> filter { it.discountCount > 0 }
        .sortedWith(compareByDescending<GhajarMarketShop> { it.discountCount }
            .thenByDescending { it.stars })
    MarketSort.CHEAPEST -> sortedWith(
        compareByDescending<GhajarMarketShop> { it.minPrice > 0 }.thenBy { it.minPrice }
    )
    MarketSort.PRICIEST -> sortedWith(compareByDescending { it.maxPrice })
}

@Composable
fun GhajarMarketScreen(
    api: GhajarStoreApi,
    store: ConfigStore,
    active: Boolean,
    /**
     * Whether this phone is linked to an account.
     *
     * The whole screen works without one: browsing shops, reading ratings and
     * seeing plan prices need no account, because a shop window nobody can
     * look into sells nothing. Only the three things that write something -
     * ordering, reviewing, registering a shop - ask for the account, at the
     * moment they are tapped rather than in front of the list.
     */
    signedIn: Boolean = true,
    onSignIn: () -> Unit = {},
    /**
     * The Ghajar shop's own entry, drawn first in the list and never sorted
     * away. The list is every shop, and Ghajar is one of them - not a default
     * the others hide behind.
     */
    ghajarEntry: (@Composable () -> Unit)? = null
) {
    var route by remember { mutableStateOf<MarketRoute>(MarketRoute.List) }
    var feed by remember { mutableStateOf<GhajarMarketFeed?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(active, refreshKey) {
        if (!active) return@LaunchedEffect
        busy = true
        error = null
        runCatching { api.marketShops() }
            .onSuccess { feed = it }
            .onFailure { error = it.message ?: "فهرست فروشگاه‌ها در دسترس نیست" }
        busy = false
    }

    val current = feed
    val onRegister: () -> Unit = { if (signedIn) route = MarketRoute.Register else onSignIn() }
    when {
        // Ghajar's entry never waits on the marketplace: it is on screen
        // while the list loads, when the list fails, and when the owner has
        // the marketplace switched off.
        route is MarketRoute.List && (current == null || !current.enabled) ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
                if (current?.enabled != false) AddShopCard(onRegister)
                Rail("فروشگاه‌ها")
                ghajarEntry?.invoke()
                when {
                    busy && current == null -> SkinLoading("در حال گرفتن فهرست فروشگاه‌ها")
                    error != null && current == null ->
                        SkinError(error!!, retryText = "تلاش دوباره", onRetry = { refreshKey++ })
                    current == null -> SkinLoading("فروشگاه‌ها")
                    else -> Unit
                }
            }
        current == null -> SkinLoading("فروشگاه‌ها")
        else -> when (val where = route) {
            is MarketRoute.List -> MarketList(
                api = api,
                feed = current,
                signedIn = signedIn,
                onOpen = { route = MarketRoute.Shop(it) },
                onRegister = onRegister,
                onRefresh = { refreshKey++ },
                ghajarEntry = ghajarEntry
            )

            is MarketRoute.Shop -> MarketShopHome(
                api = api,
                store = store,
                shopId = where.id,
                signedIn = signedIn,
                onSignIn = onSignIn,
                onBack = { route = MarketRoute.List; refreshKey++ },
                onOrdered = { order -> route = MarketRoute.Order(where.id, order) }
            )

            is MarketRoute.Order -> MarketOrderPage(
                api = api,
                store = store,
                order = where.order,
                onBack = { route = MarketRoute.Shop(where.shopId) }
            )

            is MarketRoute.Register -> MarketRegisterPage(
                api = api,
                onBack = { route = MarketRoute.List }
            )
        }
    }
}

// ----------------------------------------------------------------- the list

@Composable
private fun AddShopCard(onRegister: () -> Unit) {
    Slab(onClick = onRegister, accent = ghajarColors.primary) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AddBusiness, null, tint = ghajarColors.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(GhajarSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text("افزودن فروشگاه", fontWeight = FontWeight.Bold, color = ghajarColors.textPrimary)
                Text("فروشگاه خودت را ثبت کن: ثبت‌نام، تأیید و مدیریت از ربات",
                    style = MaterialTheme.typography.labelMedium, color = ghajarColors.textSecondary)
            }
            Icon(Icons.Filled.ChevronLeft, null, tint = ghajarColors.textMuted)
        }
    }
}

@Composable
private fun MarketList(
    api: GhajarStoreApi,
    feed: GhajarMarketFeed,
    signedIn: Boolean,
    onOpen: (Int) -> Unit,
    onRegister: () -> Unit,
    onRefresh: () -> Unit,
    ghajarEntry: (@Composable () -> Unit)?
) {
    var sort by remember { mutableStateOf(MarketSort.BEST) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
        // First, above everything: the way in for a seller.
        AddShopCard(onRegister)

        Rail("فروشگاه‌ها")
        Text(
            "همهٔ فروشگاه‌ها، داخل همین برنامه. خرید، پرداخت و تحویل کانفیگ بدون رفتن به تلگرام.",
            style = MaterialTheme.typography.labelMedium,
            color = ghajarColors.textSecondary
        )

        if (feed.shops.isNotEmpty()) {
            TabRail(
                tabs = MarketSort.entries.map { RailTab(it.label) },
                selected = MarketSort.entries.indexOf(sort),
                onSelect = { index -> sort = MarketSort.entries[index] }
            )
        }

        ghajarEntry?.invoke()

        if (feed.shops.isNotEmpty()) {
            val shown = feed.shops.sortedFor(sort)
            if (shown.isEmpty()) {
                // A filter with no matches says which filter, not "nothing
                // here": the list itself is not empty and the way out is one
                // chip away.
                SkinEmpty(
                    when (sort) {
                        MarketSort.DISCOUNTED -> "هیچ فروشگاهی کد تخفیف فعال ندارد"
                        MarketSort.WORST -> "هنوز هیچ فروشگاهی نظر ثبت‌شده ندارد"
                        else -> "چیزی با این فیلتر پیدا نشد"
                    },
                    hint = "یکی از فیلترهای بالا را عوض کنید.",
                    icon = Icons.Filled.ShoppingBag
                )
            }
            shown.forEach { shop -> MarketShopCard(api, shop) { onOpen(shop.id) } }
        }

        if (!signedIn) {
            // Said once, under the list, not over it. Browsing needs no
            // account; this is here so the buy button later is not a surprise.
            Slab(accent = ghajarColors.primary, spacing = GhajarSpacing.xs) {
                Text("برای خرید از فروشگاه‌ها، حساب را یک‌بار متصل کن",
                    fontWeight = FontWeight.Bold, color = ghajarColors.textPrimary)
                Text("دیدن فروشگاه‌ها، قیمت‌ها و نظرها نیازی به اتصال ندارد.",
                    style = MaterialTheme.typography.labelSmall, color = ghajarColors.textSecondary)
            }
        }

        GhostPill("بازخوانی فهرست", onRefresh, icon = Icons.Filled.Refresh)
    }
}

/**
 * One shop, as the mock asked for it: name, a verified badge, the star score,
 * the satisfaction share and how many ratings it rests on.
 *
 * The rating count is always shown next to the score. "4.7 out of 5" from two
 * reviews and from two hundred are very different claims, and a score without
 * its sample size is the oldest way to mislead with a true number.
 */
@Composable
private fun MarketShopCard(api: GhajarStoreApi, shop: GhajarMarketShop, onOpen: () -> Unit) {
    val c = ghajarColors
    val lang = LocalLang.current
    Slab(onClick = onOpen, spacing = GhajarSpacing.sm) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MarketLogo(api, shop.id, shop.logoVersion, shop.verified)
            Spacer(Modifier.width(GhajarSpacing.sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shop.name, fontWeight = FontWeight.Bold, color = c.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (shop.verified) {
                        Spacer(Modifier.width(GhajarSpacing.xs))
                        Icon(Icons.Filled.Shield, "تأییدشده", tint = c.primary, modifier = Modifier.size(14.dp))
                    }
                }
                if (shop.tagline.isNotBlank()) {
                    Text(shop.tagline, style = MaterialTheme.typography.labelSmall, color = c.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Filled.ChevronLeft, null, tint = c.textMuted)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StarRow(shop.stars)
            Spacer(Modifier.width(GhajarSpacing.sm))
            Text(
                if (shop.reviewCount > 0) mixedText(
                    localizeDigits("%.1f".format(java.util.Locale.US, shop.stars), lang) +
                        " از ۵ · " + localizeDigits(shop.satisfaction.toString(), lang) +
                        "٪ رضایت · " + localizeDigits(shop.reviewCount.toString(), lang) + " نظر"
                ) else mixedText("هنوز نظری ثبت نشده"),
                style = MaterialTheme.typography.labelSmall,
                color = if (shop.reviewCount > 0) c.textSecondary else c.textMuted,
                modifier = Modifier.weight(1f)
            )
        }

        // The prices, as the owner asked for them on the card: a gigabyte's
        // base price, the cheapest and dearest plan, and the range across the
        // seller's servers. Only figures the server actually has are printed.
        val toman = { v: Long -> localizeDigits(formatToman(v), lang) }
        val lines = listOfNotNull(
            shop.gbPrice.takeIf { it > 0 }?.let { "💾 هر گیگ " + toman(it) + " تومان" },
            shop.minPrice.takeIf { it > 0 }?.let {
                "🏷 ارزان‌ترین " + toman(it) +
                    (if (shop.maxPrice > it) " · گران‌ترین " + toman(shop.maxPrice) else "") + " تومان" +
                    (if (shop.productCount > 0) " · " + localizeDigits(shop.productCount.toString(), lang) + " پلن" else "")
            },
            shop.panelMinPrice.takeIf { it > 0 && shop.panelCount > 1 }?.let {
                "🌐 " + localizeDigits(shop.panelCount.toString(), lang) + " سرور · از " + toman(it) +
                    (if (shop.panelMaxPrice > it) " تا " + toman(shop.panelMaxPrice) else "") + " تومان"
            }
        )
        lines.forEach {
            Text(mixedText(it), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
        if (shop.testAvailable || shop.discountCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                if (shop.testAvailable) {
                    Text("🎁 تست رایگان", style = MaterialTheme.typography.labelSmall, color = c.premium)
                }
                if (shop.discountCount > 0) {
                    Text(mixedText(localizeDigits(shop.discountCount.toString(), lang) + " کد تخفیف"),
                        style = MaterialTheme.typography.labelSmall, color = c.primary)
                }
            }
        }

        if (!shop.canSell && shop.closedReason.isNotBlank()) {
            Text(shop.closedReason, style = MaterialTheme.typography.labelSmall, color = c.warning)
        }
    }
}

@Composable
internal fun StarRow(stars: Double) {
    val c = ghajarColors
    // Whole stars only, deliberately: a half-star glyph at this size is a
    // smudge, and the exact figure is printed next to it anyway.
    val filled = stars.toInt().coerceIn(0, 5)
    Row {
        repeat(5) { index ->
            Icon(
                if (index < filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (index < filled) c.warning else c.textMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ----------------------------------------------------------------- the order

@Composable
private fun MarketOrderPage(
    api: GhajarStoreApi,
    store: ConfigStore,
    order: GhajarMarketOrder,
    onBack: () -> Unit
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var status by remember(order.id) { mutableStateOf<GhajarMarketOrderStatus?>(null) }
    var message by remember(order.id) { mutableStateOf<String?>(null) }
    var sending by remember(order.id) { mutableStateOf(false) }
    var note by remember(order.id) { mutableStateOf("") }
    var pollKey by remember(order.id) { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        sending = true
        message = null
        scope.launch {
            runCatching { api.marketSubmitReceipt(order.id, uri, note) }
                .onSuccess { message = it; pollKey++ }
                .onFailure { message = it.message ?: "ارسال رسید انجام نشد" }
            sending = false
        }
    }

    /**
     * Polls the order while it is with the seller.
     *
     * Every twelve seconds, and only while the status is one that can still
     * change. A finished order stops the loop, so a buyer who leaves this page
     * open does not keep a request going for an answer that has arrived.
     */
    LaunchedEffect(order.id, pollKey) {
        while (true) {
            runCatching { api.marketOrderStatus(order.id) }.onSuccess { status = it }
            val now = status?.status
            if (now == MARKET_STATUS_PAID || now == MARKET_STATUS_REJECTED || now == MARKET_STATUS_FAILED) break
            delay(12_000)
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
        GhostPill("بازگشت به فروشگاه", onBack, icon = Icons.Filled.ChevronLeft)
        Rail("پرداخت سفارش")

        Slab(spacing = GhajarSpacing.xs) {
            InfoLine("شمارهٔ سفارش", localizeDigits(order.id.toString(), lang))
            if (order.productName.isNotBlank()) InfoLine("پلن", order.productName)
            InfoLine("مبلغ", localizeDigits(formatToman(order.amount), lang) + " تومان")
            InfoLine("روش", order.methodLabel.ifBlank { order.method })
        }

        val live = status
        when (live?.status) {
            MARKET_STATUS_PAID -> MarketDelivery(live, api, store, onRetry = { pollKey++ })

            MARKET_STATUS_REJECTED -> SkinError(
                "فروشنده این پرداخت را رد کرد."
                    + (live.rejectReason.takeIf { it.isNotBlank() }?.let { "\n" + it } ?: ""),
                retryText = "بازگشت",
                onRetry = onBack
            )

            MARKET_STATUS_FAILED -> SkinError(
                "پرداخت شما ثبت شد اما ساخت سرویس انجام نشد."
                    + (live.rejectReason.takeIf { it.isNotBlank() }?.let { "\n" + it } ?: "")
                    + "\nشمارهٔ سفارش " + localizeDigits(order.id.toString(), lang)
                    + " را به پشتیبانی فروشگاه بدهید."
            )

            MARKET_STATUS_REVIEW -> Slab(accent = c.warning, spacing = GhajarSpacing.sm) {
                Text("رسید برای فروشنده ارسال شد", fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text(
                    "تأیید با فروشنده است. به‌محض تأیید، سرویس روی پنل خودش ساخته می‌شود و "
                        + "کانفیگ همین‌جا می‌آید. این صفحه خودش بررسی می‌کند.",
                    style = MaterialTheme.typography.labelMedium, color = c.textSecondary
                )
                GhostPill("بررسی دوباره", { pollKey++ }, icon = Icons.Filled.Refresh)
            }

            null, MARKET_STATUS_AWAITING -> when (order.needs) {
                // A gateway: the buyer leaves for the provider's page and the
                // return is verified server-side. The button is all this
                // screen does, because a gateway result that this app decided
                // would be a payment this app could be told to fake.
                "secret" -> Slab(spacing = GhajarSpacing.sm) {
                    Text("پرداخت با درگاه فروشگاه", fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Text(
                        "با زدن دکمه به درگاه خودِ فروشنده می‌روید. پس از پرداخت، برگردید؛ "
                            + "نتیجه روی سرور بررسی و سرویس ساخته می‌شود.",
                        style = MaterialTheme.typography.labelMedium, color = c.textSecondary
                    )
                    if (order.gatewayUrl.isBlank()) {
                        Text("آدرس درگاه از سرور نیامد.", color = c.error,
                            style = MaterialTheme.typography.labelMedium)
                    } else {
                        PillButton("رفتن به درگاه", {
                            openLink(context, order.gatewayUrl)
                        }, icon = Icons.Filled.OpenInNew)
                    }
                    GhostPill("پرداخت کردم، بررسی کن", { pollKey++ }, icon = Icons.Filled.Refresh)
                }

                // Card to support: the seller handles it in their own chat.
                "contact" -> Slab(spacing = GhajarSpacing.sm) {
                    Text("پرداخت با هماهنگی پشتیبانی", fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Text(
                        "این فروشگاه کارت‌به‌کارت را از طریق حساب پشتیبانی خودش انجام می‌دهد.",
                        style = MaterialTheme.typography.labelMedium, color = c.textSecondary
                    )
                    if (order.contact.isNotBlank()) {
                        PillButton("@" + order.contact.trimStart('@'), {
                            openTelegram(context, order.contact)
                        }, icon = Icons.Filled.SupportAgent)
                    }
                }

                // Card to card: the seller's own card, and the receipt goes to
                // the seller. This is the path that needs no credential from
                // anybody and is the default for a new shop.
                else -> Slab(spacing = GhajarSpacing.sm) {
                    Text("کارت به کارت", fontWeight = FontWeight.Bold, color = c.textPrimary)
                    if (order.cardNumber.isBlank()) {
                        Text("شمارهٔ کارت این فروشگاه ثبت نشده است.", color = c.error,
                            style = MaterialTheme.typography.labelMedium)
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CreditCard, null, tint = c.primary,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(GhajarSpacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(mixedText(spacedCard(order.cardNumber)),
                                    fontWeight = FontWeight.Bold, color = c.textPrimary)
                                if (order.cardHolder.isNotBlank()) {
                                    Text(order.cardHolder,
                                        style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                                }
                            }
                            Icon(
                                Icons.Filled.ContentCopy, "کپی شمارهٔ کارت", tint = c.textMuted,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { copyToClipboard(context, order.cardNumber) }
                            )
                        }
                        Text(
                            "مبلغ را به همین کارت واریز کنید، بعد تصویر رسید را بفرستید. "
                                + "رسید مستقیم برای فروشنده می‌رود.",
                            style = MaterialTheme.typography.labelMedium, color = c.textSecondary
                        )
                        SkinField(value = note, onValueChange = { note = it },
                            label = "توضیح برای فروشنده (اختیاری)")
                        PillButton(
                            if (sending) "در حال ارسال رسید…" else "انتخاب و ارسال تصویر رسید",
                            { picker.launch(arrayOf("image/*")) },
                            enabled = !sending,
                            icon = Icons.Filled.UploadFile
                        )
                    }
                }
            }

            else -> SkinError(
                "وضعیت این سفارش برای این نسخهٔ برنامه ناشناخته است: " + (live?.status ?: "-")
                    + "\nبرنامه را به‌روز کنید یا از پشتیبانی فروشگاه بپرسید.",
                retryText = "بررسی دوباره",
                onRetry = { pollKey++ }
            )
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        }
    }
}

/**
 * A finished order: the username, the subscription link and the configs, with
 * one button that imports them the same way the rest of the app does.
 */
/** A marketplace delivery in the shape the Ghajar shop's import and dialog take. */
internal fun marketServiceDetails(status: GhajarMarketOrderStatus, productName: String) = GhajarServiceDetails(
    username = status.username,
    productName = productName,
    status = "active",
    usedGb = null,
    totalGb = null,
    remainingGb = null,
    expiresAt = "",
    subscriptionUrl = status.subscription.takeIf { it.isNotBlank() },
    outputs = status.configs
)

@Composable
internal fun MarketDelivery(
    status: GhajarMarketOrderStatus,
    api: GhajarStoreApi,
    store: ConfigStore,
    /** Asks the server again; offered while the configs have not arrived. */
    onRetry: (() -> Unit)? = null
) {
    val c = ghajarColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imported by remember(status.id) { mutableStateOf<String?>(null) }
    var importing by remember(status.id) { mutableStateOf(false) }
    var showQr by remember(status.id) { mutableStateOf(false) }
    var synced by remember(status.id) { mutableStateOf(false) }
    // Like the Ghajar shop: a delivered service goes straight into the
    // servers list, without a second tap.
    LaunchedEffect(status.id, status.subscription, status.configs.size) {
        if (!synced && (status.subscription.isNotBlank() || status.configs.isNotEmpty())) {
            importing = true
            runCatching { api.importServiceOnce(store, marketServiceDetails(status, "سرویس فروشگاه")) }
                .onSuccess { count -> synced = true; imported = if (count > 0) "به لیست سرورها اضافه شد." else "این سرویس در لیست سرورها هست." }
                .onFailure { imported = it.message ?: "افزودن انجام نشد" }
            importing = false
        }
    }
    if (showQr) {
        GhajarDeliveryDialog(
            GhajarDelivery(marketServiceDetails(status, "سرویس فروشگاه"), imported = status.configs.size, synced = synced),
            onDismiss = { showQr = false }, onRetry = { onRetry?.invoke() }, busy = importing)
    }

    Slab(accent = c.primary, spacing = GhajarSpacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(GhajarSpacing.sm))
            Text("سرویس شما آماده است", fontWeight = FontWeight.Bold, color = c.textPrimary)
        }
        if (status.username.isNotBlank()) {
            InfoLine("نام سرویس", status.username)
        }
        val payload = status.subscription.takeIf { it.isNotBlank() }
            ?: status.configs.joinToString("\n")
        if (payload.isBlank()) {
            Text(
                "فروشنده پرداخت را تأیید کرده اما کانفیگی برنگشت. چند لحظه بعد دوباره بررسی کنید.",
                style = MaterialTheme.typography.labelMedium, color = c.warning
            )
            onRetry?.let { GhostPill("دریافت دوبارهٔ کانفیگ", it, icon = Icons.Filled.Refresh) }
        } else {
            Text(
                "لینک و کانفیگ‌ها آماده‌اند. با «افزودن به برنامه» وارد لیست سرورها می‌شوند.",
                style = MaterialTheme.typography.labelMedium, color = c.textSecondary
            )
            PillButton(
                if (importing) "در حال افزودن…" else "افزودن به برنامه",
                {
                    // importServiceOnce is the path the shop's own checkout
                    // already uses: it registers a subscription, fetches it,
                    // and de-duplicates against previous deliveries. Writing a
                    // second import for the marketplace would be a second thing
                    // to keep correct for no gain.
                    importing = true
                    imported = null
                    scope.launch {
                        val details = marketServiceDetails(status, "سرویس فروشگاه")
                        imported = runCatching { api.importServiceOnce(store, details) }
                            .fold(
                                { count ->
                                    synced = true
                                    if (count > 0) "به لیست سرورها اضافه شد."
                                    else "این سرویس در لیست سرورها هست."
                                },
                                { it.message ?: "افزودن انجام نشد" }
                            )
                        importing = false
                    }
                },
                enabled = !importing,
                icon = Icons.Filled.Check
            )
            GhostPill("QR و کانفیگ‌ها", { showQr = true }, icon = Icons.Filled.QrCode2)
            GhostPill("کپی", { copyToClipboard(context, payload) }, icon = Icons.Filled.ContentCopy)
        }
        imported?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = c.textSecondary) }
    }
}

// -------------------------------------------------------------- registration

@Composable
private fun MarketRegisterPage(api: GhajarStoreApi, onBack: () -> Unit) {
    val c = ghajarColors
    val lang = LocalLang.current
    val context = LocalContext.current
    var terms by remember { mutableStateOf<GhajarMarketTerms?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        busy = true
        runCatching { api.marketTerms() }
            .onSuccess { terms = it }
            .onFailure { error = it.message ?: "قوانین خوانده نشد" }
        busy = false
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
        GhostPill("بازگشت به فهرست", onBack, icon = Icons.Filled.ChevronLeft)
        Rail("ثبت فروشگاه")

        val loaded = terms
        when {
            busy && loaded == null -> SkinLoading("در حال گرفتن قوانین")
            loaded == null -> SkinError(error ?: "قوانین خوانده نشد", retryText = "بازگشت", onRetry = onBack)
            else -> {
                Slab(spacing = GhajarSpacing.xs) {
                    InfoLine("هزینهٔ ثبت",
                        localizeDigits(formatToman(loaded.fee), lang) + " تومان")
                    InfoLine("کمیسیون فروش",
                        localizeDigits("%.0f".format(java.util.Locale.US, loaded.commissionPercent), lang) + "٪")
                    InfoLine("طول دورهٔ مالی",
                        localizeDigits(loaded.cycleDays.toString(), lang) + " روز")
                    InfoLine("جریمهٔ روزانهٔ تأخیر",
                        localizeDigits(formatToman(loaded.penaltyPerDay), lang) + " تومان")
                    InfoLine("حذف از فهرست",
                        localizeDigits(loaded.graceDays.toString(), lang) + " روز پس از سررسید")
                }

                if (loaded.terms.isNotBlank()) {
                    Slab { Text(loaded.terms, style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary) }
                }

                // What the seller has to do, in order and without hidden steps.
                Slab(spacing = GhajarSpacing.sm) {
                    Text("چه چیزی لازم است", fontWeight = FontWeight.Bold, color = c.textPrimary)
                    listOf(
                        "ربات فاکسیمای خودتان، روی دامنهٔ خودتان",
                        "در ربات خودتان بزنید /token2 و توکن API را کپی کنید",
                        "شمارهٔ کارت یا درگاه خودتان، برای دریافت پول",
                    ).forEachIndexed { index, line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                localizeDigits((index + 1).toString(), lang),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = c.primary,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(line, style = MaterialTheme.typography.labelMedium,
                                color = c.textSecondary, modifier = Modifier.weight(1f))
                        }
                    }
                    Text(
                        "سورس ربات شما تغییر نمی‌کند، فایلی آپلود نمی‌شود و هیچ مهاجرت دیتابیسی لازم نیست.",
                        style = MaterialTheme.typography.labelSmall, color = c.textMuted
                    )
                }

                if (loaded.myShops.isEmpty()) {
                    // The registration form itself is filled in the bot, not
                    // here, and this says so instead of showing a form that
                    // would need the URL and token typed on a phone keyboard.
                    Slab(accent = c.primary, spacing = GhajarSpacing.sm) {
                        Text("ثبت‌نام در ربات انجام می‌شود", fontWeight = FontWeight.Bold,
                            color = c.textPrimary)
                        Text(
                            "برای ثبت فروشگاه، در ربات قاجار «🏪 ثبت فروشگاه» را بزنید. "
                                + "آدرس و توکن آنجا گرفته می‌شود، چون تایپ کردنشان روی گوشی سخت است "
                                + "و توکن نباید در جایی بماند.",
                            style = MaterialTheme.typography.labelMedium, color = c.textSecondary
                        )
                        PillButton("رفتن به ربات", {
                            openLink(context, BrandConfig.TELEGRAM_BOT_URL)
                        }, icon = Icons.Filled.OpenInNew)
                    }
                } else {
                    Rail("فروشگاه‌های من")
                    loaded.myShops.forEach { own ->
                        Slab(spacing = GhajarSpacing.xs) {
                            Text(own.name, fontWeight = FontWeight.Bold, color = c.textPrimary)
                            InfoLine("وضعیت", marketStatusLabel(own.status))
                            if (own.lastError.isNotBlank()) {
                                Text(own.lastError, style = MaterialTheme.typography.labelSmall,
                                    color = c.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------- small parts

@Composable
internal fun InfoLine(label: String, value: String) {
    val c = ghajarColors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textMuted,
            modifier = Modifier.weight(1f))
        Text(mixedText(value), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium, color = c.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ChoiceRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    subtitle: String? = null,
    value: String? = null
) {
    val c = ghajarColors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(if (selected) c.card else c.secondaryCard)
            .clickable { onSelect() }
            .padding(horizontal = GhajarSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(GhajarRadius.pill))
                .background(if (selected) c.primary else c.border),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, null, tint = c.onPrimary, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.width(GhajarSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = c.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(mixedText(subtitle), style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!value.isNullOrBlank()) {
            Text(mixedText(value), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = c.primary)
        }
    }
}

internal fun marketStatusLabel(status: String): String = when (status) {
    "active"    -> "فعال"
    "pending"   -> "منتظر پرداخت هزینهٔ ثبت"
    "review"    -> "منتظر بررسی"
    "suspended" -> "معلق (بدهی)"
    "delisted"  -> "خارج از فهرست"
    "rejected"  -> "رد شده"
    else        -> status
}

/** Groups a card number into fours so a person can read it back. */
internal fun spacedCard(number: String): String =
    number.filter { it.isDigit() }.chunked(4).joinToString(" ")

internal fun formatToman(value: Long): String =
    java.text.DecimalFormat("#,###").format(value)

internal fun openTelegram(context: android.content.Context, handle: String) {
    val clean = handle.trimStart('@').trim()
    if (clean.isBlank()) return
    openLink(context, if (clean.startsWith("http")) clean else "https://t.me/$clean")
}

internal fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal fun copyToClipboard(context: android.content.Context, value: String) {
    runCatching {
        val manager = context.getSystemService(android.content.ClipboardManager::class.java)
        manager?.setPrimaryClip(android.content.ClipData.newPlainText("ghajar", value))
    }
}

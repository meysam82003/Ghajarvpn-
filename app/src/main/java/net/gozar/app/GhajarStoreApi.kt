package net.gozar.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

data class GhajarLinkSession(
    val code: String,
    val sessionToken: String,
    val botUsername: String,
    val expiresInSeconds: Int,
    val expiresAtMillis: Long = GhajarUiRules.linkExpiresAt(System.currentTimeMillis(), expiresInSeconds)
)

data class GhajarPanel(
    val id: String,
    val name: String,
    val custom: Boolean,
    val customUsername: Boolean,
    val usernameRequired: Boolean,
    val noteEnabled: Boolean
)

data class GhajarCategory(val id: String, val name: String)
data class GhajarTimeRange(val days: Int, val name: String)

data class GhajarProduct(
    val id: String,
    val name: String,
    val price: Long?,
    val trafficGb: Double?,
    val days: Int?,
    val description: String,
    val countryId: String
)

data class GhajarCustomQuote(
    val price: Long?,
    val trafficMin: Int,
    val trafficMax: Int,
    val timeMin: Int,
    val timeMax: Int
)

data class GhajarOwnedService(
    val username: String,
    val productName: String,
    val status: String,
    val location: String,
    /**
     * The invoice this service was sold on. Carried so a renew request that
     * names an invoice id - which is what every notice written before the
     * reference became the username says - can still be matched to the
     * service it is about, without another round trip.
     */
    val invoiceId: String = "",
    /** Live panel figures, from the `user_info` the invoice row already carries. */
    val dataLimitBytes: Long? = null,
    val usedBytes: Long? = null,
    val expireTimestamp: Long? = null,
    /** What the plan was sold as, used when the panel reports no limit at all. */
    val planGb: Double? = null,
    val planDays: Int? = null,
    val soldAt: Long? = null
) {
    /** Bytes still available, or null when this service has no volume cap. */
    val remainingBytes: Long?
        get() {
            val limit = dataLimitBytes?.takeIf { it > 0 } ?: return null
            return (limit - (usedBytes ?: 0L)).coerceAtLeast(0L)
        }

    /** 0..1 of the volume already spent, or null when there is no cap. */
    val volumeFraction: Float?
        get() {
            val limit = dataLimitBytes?.takeIf { it > 0 } ?: return null
            return ((usedBytes ?: 0L).toFloat() / limit.toFloat()).coerceIn(0f, 1f)
        }

    /**
     * Whole days left, or null when the service never expires.
     *
     * Falls back to the sale date plus the plan length: a panel that has not
     * been asked since the sale reports no expiry, and "unlimited" is the
     * wrong thing to tell someone about a 30-day plan.
     */
    val daysRemaining: Int?
        get() {
            val expiry = expireTimestamp?.takeIf { it > 0 }
                ?: soldAt?.takeIf { it > 0 }?.let { sold ->
                    planDays?.takeIf { it > 0 }?.let { sold + it * 86_400L }
                }
                ?: return null
            val now = System.currentTimeMillis() / 1000
            return (((expiry - now) + 86_399L) / 86_400L).coerceAtLeast(0L).toInt()
        }

    /** 0..1 of the subscription window already elapsed, or null when open-ended. */
    val timeFraction: Float?
        get() {
            val total = planDays?.takeIf { it > 0 } ?: return null
            val left = daysRemaining ?: return null
            return ((total - left).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

data class GhajarServiceDetails(
    val username: String,
    val productName: String,
    val status: String,
    val usedGb: Double?,
    val totalGb: Double?,
    val remainingGb: Double?,
    val expiresAt: String,
    val subscriptionUrl: String?,
    val outputs: List<String>
)

/**
 * The marketplace: shops that are not ours, sold through this app.
 *
 * `enabled` is a first-class field rather than an absence, because "the owner
 * has not switched the marketplace on" and "the request failed" need to look
 * different to the screen - one hides a tab, the other shows an error.
 */
data class GhajarMarketFeed(
    val enabled: Boolean,
    val registerFee: Long,
    val shops: List<GhajarMarketShop>
)

/**
 * The Ghajar shop's own picture, set by the owner from the bot. Published by
 * every shops-list load so the list card and the Ghajar page both follow it.
 */
object GhajarShopLogo {
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)
}

data class GhajarMarketShop(
    val id: Int,
    val name: String,
    val description: String,
    val telegramBot: String,
    val telegramChannel: String,
    val supportContact: String,
    val verified: Boolean,
    val status: String,
    /** Whether it may take a new order right now. */
    val canSell: Boolean,
    /** Why not, when it may not. Shown as-is: the server wrote it for a buyer. */
    val closedReason: String,
    val stars: Double,
    val reviewCount: Int,
    /** Share of ratings at four stars or better - not the average rescaled. */
    val satisfaction: Int,
    /**
     * The catalogue summary the sort and filter chips run on.
     *
     * Server-side figures, refreshed nightly and whenever a shop is approved
     * or tested, so ordering the whole list by price costs no extra request.
     * Zero means "not synced yet", which is why the price sorts push those
     * shops to the end instead of treating them as free.
     */
    val minPrice: Long = 0,
    val maxPrice: Long = 0,
    val productCount: Int = 0,
    val discountCount: Int = 0,
    val sales30d: Int = 0,
    val reviews: List<GhajarMarketReview> = emptyList(),
    /** One line under the name, set by the seller. */
    val tagline: String = "",
    /**
     * Non-zero when the seller set a profile picture; changes whenever they
     * replace it, so it doubles as the cache key for the image.
     */
    val logoVersion: Int = 0,
    /** Base price of one gigabyte on the seller's custom plan; 0 when none. */
    val gbPrice: Long = 0,
    val dayPrice: Long = 0,
    /** Cheapest and dearest ready plan across the seller's panels. */
    val panelMinPrice: Long = 0,
    val panelMaxPrice: Long = 0,
    val panelCount: Int = 0,
    val testAvailable: Boolean = false
)

data class GhajarMarketReview(val stars: Int, val body: String, val createdAt: Long)

data class GhajarMarketPanel(
    val code: String,
    val name: String,
    val country: String,
    val flag: String,
    /** Whether a custom (pick your own size) plan is sold on this panel. */
    val custom: Boolean = false,
    val gbPrice: Long = 0,
    val dayPrice: Long = 0,
    val minGb: Int = 0,
    val maxGb: Int = 0,
    val minDays: Int = 0,
    val maxDays: Int = 0,
    /** Whether the seller offers a free test on this panel, and how big. */
    val test: Boolean = false,
    val testHours: Int = 0,
    val testMb: Int = 0
)

data class GhajarMarketProduct(
    val code: String,
    val name: String,
    val price: Long,
    val volumeGb: Int,
    val timeDays: Int,
    val note: String,
    /** The seller's panel this plan is sold on; "/all" or blank for every panel. */
    val location: String = "",
    /** The seller's category for this plan, as their product row stores it. */
    val category: String = ""
)

/**
 * One payment method a seller connected.
 *
 * `needs` says what the buyer will be shown: `card` puts a card number and a
 * holder on screen, `contact` opens the seller's support account, `secret`
 * means a gateway and the order carries a URL. Nothing here ever carries a
 * credential - the server refuses to send one.
 */
data class GhajarMarketMethod(
    val id: String,
    val label: String,
    val needs: String,
    val note: String,
    val cardNumber: String,
    val cardHolder: String,
    val contact: String
)

data class GhajarMarketCatalog(
    val panels: List<GhajarMarketPanel>,
    val products: List<GhajarMarketProduct>,
    val methods: List<GhajarMarketMethod>,
    val categories: List<GhajarCategory> = emptyList()
)

data class GhajarMarketOrder(
    val id: Int,
    val method: String,
    val methodLabel: String,
    val needs: String,
    val amount: Long,
    val cardNumber: String,
    val cardHolder: String,
    val contact: String,
    /** Set only for a gateway order: where to send the buyer. */
    val gatewayUrl: String,
    val productName: String,
    /**
     * "paid" when the order finished on the spot - a free test, or a plan
     * paid from the wallet at this shop - so there is nothing left to pay.
     */
    val status: String = "",
    val message: String = ""
)

data class GhajarMarketOrderStatus(
    val id: Int,
    val shopId: Int,
    val status: String,
    val rejectReason: String,
    val amount: Long,
    val username: String,
    val configs: List<String>,
    val subscription: String
)

data class GhajarMarketOwnShop(
    val id: Int,
    val name: String,
    val status: String,
    val lastError: String
)

data class GhajarMarketTerms(
    val terms: String,
    val fee: Long,
    val commissionPercent: Double,
    val cycleDays: Int,
    val penaltyPerDay: Long,
    val graceDays: Int,
    val myShops: List<GhajarMarketOwnShop>
)

/** Everything one shop's page needs, from one request. */
data class GhajarMarketHome(
    val shop: GhajarMarketShop,
    /** False when the seller's own server did not answer this time. */
    val reachable: Boolean,
    val catalog: GhajarMarketCatalog,
    val testEnabled: Boolean,
    val testUsed: Boolean,
    /** The buyer's balance at this shop; null when not signed in. */
    val wallet: Long?,
    val unread: Int,
    val blocked: Boolean,
    /** True when the signed-in user owns this shop. */
    val mine: Boolean = false
)

/** One discount or gift code, made in Ghajar's bot ("ghajar") or in the seller's own bot ("seller"). */
data class GhajarMarketCode(
    val source: String,
    val id: Int,
    val code: String,
    /** Percent for a discount, toman for a gift. */
    val value: Double,
    val maxUses: Int,
    val used: Int,
    val expiresAt: Long,
    val active: Boolean,
    val perUser: Int,
    val firstOnly: Boolean,
    val product: String,
    val panel: String
)

data class GhajarMarketCodes(val discounts: List<GhajarMarketCode>, val gifts: List<GhajarMarketCode>)

data class GhajarMarketService(
    val invoiceId: String,
    val username: String,
    val productName: String,
    val panelName: String,
    val isTest: Boolean,
    val boughtAt: Long,
    /** False when the seller's panel did not answer for this service. */
    val reachable: Boolean,
    val status: String,
    /** Bytes, as the seller's panel reports them; 0 when unlimited. */
    val dataLimit: Long,
    val used: Long,
    /** Unix seconds; 0 when it does not expire. */
    val expire: Long,
    val volumeGb: Int,
    val timeDays: Int,
    val subscription: String,
    val configCount: Int
)

data class GhajarMarketMessage(
    val id: Int,
    val kind: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean
)

data class GhajarMarketWalletEntry(
    val amount: Long,
    val kind: String,
    val title: String,
    val balanceAfter: Long,
    val createdAt: Long
)

data class GhajarMarketWallet(val balance: Long, val history: List<GhajarMarketWalletEntry>)

data class GhajarMarketTransaction(
    val id: Int,
    val kind: String,
    val status: String,
    val amount: Long,
    val method: String,
    val productName: String,
    val rejectReason: String,
    val createdAt: Long
)

data class GhajarMarketTicket(
    val id: Int,
    val subject: String,
    val status: String,
    val updatedAt: Long
)

data class GhajarMarketTicketMessage(val fromSeller: Boolean, val body: String, val createdAt: Long)

data class GhajarMarketTicketThread(
    val id: Int,
    val subject: String,
    val status: String,
    val messages: List<GhajarMarketTicketMessage>
)

data class GhajarNoticeMeta(
    val dataLimitBytes: Long?,
    val usedBytes: Long?,
    val remainingBytes: Long?,
    val daysRemaining: Int?,
    val expireTimestamp: Long?
)

data class GhajarNotice(
    val id: String,
    val title: String,
    val message: String,
    val important: Boolean,
    val serviceAlert: Boolean,
    val serviceUsername: String? = null,
    val meta: GhajarNoticeMeta? = null,
    /**
     * What the server says should be done about this one, and what it is
     * about. `renew` with a non-blank [actionRef] is the expiry and volume
     * warning the bot's cron produces; before the feed existed those reached
     * Telegram and nowhere else.
     */
    val action: String = "none",
    val actionRef: String = "",
    /**
     * Whether it is due to be shown *now*, decided by the server.
     *
     * Deliberately not computed here. The same notice is read by this app, the
     * mini app and the browser page, and a "every three hours" repeat worked
     * out against three different device clocks is three different answers -
     * including on a phone whose clock is simply wrong.
     */
    val shouldFloat: Boolean = true,
    /** Seconds between repeats; 0 means every time the app is opened. */
    val repeatAfterSec: Int = 0,
    val seen: Boolean = false
)

/**
 * A feed read, which is more than a list of notices.
 *
 * The shop's state rides along because the app polls this anyway: it learns
 * that the shop was switched off in the same round trip that brings it the
 * notice saying so, instead of needing a second call to find out whether to
 * cover its shop tab.
 */
data class GhajarNoticeFeed(
    val notices: List<GhajarNotice>,
    val unseen: Int,
    val shopEnabled: Boolean,
    val shopMessage: String,
    /** "force_app", "shop_off", or empty. The app is never gated by either -
     *  it reads this only so it can explain the state, not obey it. */
    val gate: String,
    val gateMessage: String
)

data class GhajarPaymentMethod(
    val id: String,
    val label: String,
    val kind: String,
    val directUrl: String?,
    val minimum: Long,
    val maximum: Long
)

data class GhajarPaymentOptions(
    val methods: List<GhajarPaymentMethod>,
    val balance: Long,
    val currency: String
)

data class GhajarPaymentInit(
    val kind: String,
    val orderId: String,
    val url: String?,
    val cardNumber: String?,
    val cardHolder: String?,
    val amount: Long,
    val amountRial: Long,
    val message: String,
    val method: String = "",
    val methodLabel: String = "",
    val expiresAt: Long = 0
)

data class GhajarPurchaseRequest(
    val countryId: String,
    val serviceId: String? = null,
    val customTrafficGb: Int? = null,
    val customTimeDays: Int? = null,
    val customUsername: String? = null,
    val note: String? = null,
    val discountCode: String? = null
)

data class GhajarPurchaseResult(
    val completed: Boolean,
    val requiresPayment: Boolean,
    val username: String?,
    val amountDue: Long,
    val balance: Long,
    val price: Long,
    val service: GhajarServiceDetails?
)

data class GhajarTrialPanel(val code: String, val name: String, val remaining: Int? = null)
data class GhajarTrialOptions(val panels: List<GhajarTrialPanel>, val remaining: Int?, val canRequest: Boolean)

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
    val maxTimeDays: Int
)

data class GhajarRenewOptions(
    val username: String,
    val panelName: String,
    val products: List<GhajarRenewProduct>,
    val currentPlanCode: String?,
    val showPrice: Boolean,
    val discountPercent: Int,
    val balance: Long,
    val custom: GhajarRenewCustomOptions
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

class GhajarApiException(message: String, val httpCode: Int = 0) : IllegalStateException(message)

/** Native client matched to the API shipped in Ghajar_vpnbot_-3-1.zip. */
class GhajarStoreApi(context: Context) {
    private val appContext = context.applicationContext
    private val account = GhajarAccountStore(appContext)

    val isLinked: Boolean get() = account.token().isNotBlank()

    fun pendingLink(): GhajarLinkSession? = account.pendingLink()

    fun clearPendingLink() = account.clearPendingLink()

    /**
     * Unlinks this phone from the shop account, so a different one can sign in.
     *
     * Everything derived from the token goes with it. The notice inbox and the
     * delivery ledger are keyed by a hash of the token, so leaving them behind
     * would mean the next account inherits the previous one's "already shown"
     * and "already installed" marks - it would silently miss its own warnings
     * and its own first delivery. They are wiped by prefix rather than by key
     * because this side does not know which account was signed in.
     *
     * The configs already on the device are deliberately untouched. They are a
     * VPN client's working data, not the shop's: signing out of a store must
     * not take away servers the user is using, and a service bought under the
     * old account keeps working until the panel says otherwise.
     */
    fun signOut() {
        account.clear()
        runCatching {
            val dir = java.io.File(appContext.applicationInfo.dataDir, "shared_prefs")
            dir.listFiles()?.forEach { file ->
                val name = file.name.removeSuffix(".xml")
                if (name.startsWith("ghajarvpn_notices_")) {
                    appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
                }
            }
            appContext.getSharedPreferences("ghajarvpn_deliveries_v2", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        GhajarNoticeBus.reset()
    }

    suspend fun beginLink(): GhajarLinkSession = withContext(Dispatchers.IO) {
        // Two attempts, not one.
        //
        // "سرور به‌موقع پاسخ نداد" on the sign-in card was reported from a
        // phone with a live tunnel: issuing a code is the first request this
        // install makes to its own server, so it pays for a cold DNS lookup, a
        // fresh TLS handshake and whatever the bot host was doing at that
        // second, and a single timeout at the twenty-second read mark turned
        // all of that into a dead end with a retry the user had to find.
        // The second attempt costs nothing when the first one works and
        // nothing is created twice: a code that was issued and abandoned
        // expires on its own in five minutes.
        val root = try {
            requestJson(
                URL("${BrandConfig.WEBLINK_API_URL}?action=generate"),
                method = "POST",
                bearer = null,
                body = null
            )
        } catch (timedOut: java.net.SocketTimeoutException) {
            GhajarLog.w("Store", "link code request timed out; retrying once")
            requestJson(
                URL("${BrandConfig.WEBLINK_API_URL}?action=generate"),
                method = "POST",
                bearer = null,
                body = null
            )
        }
        val code = root.optString("code")
        val session = root.optString("session_token")
        if (code.isBlank() || session.isBlank()) throw GhajarApiException("سرور کد اتصال صادر نکرد")
        val link = GhajarLinkSession(
            code = code,
            sessionToken = session,
            botUsername = root.optString("bot_username", "Ghajar_vpnbot"),
            expiresInSeconds = root.optInt("expires_in", 300)
        )
        // Complete durable, encrypted storage before leaving for Telegram.
        if (!account.savePendingLink(link)) throw GhajarApiException("ذخیرهٔ امن کد اتصال ناموفق بود؛ دوباره تلاش کن")
        link
    }

    internal suspend fun pollLink(sessionToken: String): GhajarLinkState = withContext(Dispatchers.IO) {
        if (account.pendingLink()?.sessionToken != sessionToken) return@withContext GhajarLinkState.SUPERSEDED
        val encoded = URLEncoder.encode(sessionToken, Charsets.UTF_8.name())
        val root = requestJson(
            URL("${BrandConfig.WEBLINK_API_URL}?action=status&session_token=$encoded"),
            method = "GET",
            bearer = null,
            body = null,
            allowLinkGate = true
        )
        currentCoroutineContext().ensureActive()
        // JSONObject.NULL must never become the literal bearer token "null".
        val issued = GhajarLinkFlow.bearer(root.opt("token") as? String)
        val state = GhajarLinkFlow.responseState(root.optString("link_status"),
            root.opt("gate") as? String, issued)
        if (state != GhajarLinkState.LINKED) return@withContext state
        // A response to a cancelled/older session must not replace a newer one.
        account.completePendingLink(sessionToken, requireNotNull(issued))
    }

    fun unlink() = account.clear()

    /**
     * A one-time ticket for opening the web panel as this same account.
     *
     * The panel authenticates with Telegram's initData, which a browser never
     * has, so "the full account panel" used to open a page that did not know
     * who had tapped it. This asks the server - authenticated with the bearer
     * this app already holds - for a ticket the browser can exchange for the
     * same session. Null when the server is too old to know the action, in
     * which case the caller opens the plain URL exactly as before.
     */
    suspend fun webPanelTicket(): String? = withContext(Dispatchers.IO) {
        runCatching {
            requestJson(
                url = URL("${BrandConfig.WEBLINK_API_URL}?action=web_ticket"),
                method = "POST",
                bearer = requireToken(),
                body = null
            ).optString("ticket").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun countries(): List<GhajarPanel> = action("countries").payloadArray().objects().mapNotNull { row ->
        val id = row.optString("id")
        if (id.isBlank()) return@mapNotNull null
        GhajarPanel(
            id = id,
            name = visible(row.optString("name", "سرور قاجار")),
            custom = row.optBoolean("is_custom"),
            customUsername = row.optBoolean("is_username"),
            usernameRequired = row.optBoolean("is_username_required"),
            noteEnabled = row.optBoolean("is_note")
        )
    }

    suspend fun categories(countryId: String): List<GhajarCategory> =
        action("categories", params = mapOf("country_id" to countryId)).payloadArray().objects().mapNotNull { row ->
            val id = row.optString("id")
            if (id.isBlank()) null else GhajarCategory(id, visible(row.optString("name")))
        }

    suspend fun timeRanges(countryId: String): List<GhajarTimeRange> =
        action("time_ranges", params = mapOf("country_id" to countryId)).payloadArray().objects().map { row ->
            GhajarTimeRange(row.optInt("day"), visible(row.optString("name")))
        }

    suspend fun products(
        countryId: String,
        categoryId: String? = null,
        timeDays: Int? = null
    ): List<GhajarProduct> {
        val params = linkedMapOf("country_id" to countryId)
        categoryId?.takeIf { it.isNotBlank() }?.let { params["category_id"] = it }
        // time_range_day is deliberately not sent: the server's own range
        // buckets don't line up with the exact-match filter it applies to
        // them, so the range is applied here instead. See GhajarTimeBuckets.
        val all = action("services", params = params).payloadArray().objects().mapNotNull { row ->
            val id = row.optString("id")
            if (id.isBlank()) return@mapNotNull null
            GhajarProduct(
                id = id,
                name = visible(row.optString("name", "سرویس قاجار")),
                price = row.optNullableDouble("price")?.takeIf { it.isFinite() && it >= 0 }?.toLong(),
                trafficGb = row.optNullableDouble("traffic_gb")?.takeIf { it >= 0 },
                days = row.optInt("time_days", -1).takeIf { it >= 0 },
                description = visible(row.optString("description")),
                countryId = row.optString("country_id", countryId)
            )
        }
        if (timeDays == null) return all
        val inRange = all.filter { GhajarTimeBuckets.matches(timeDays, it.days) }
        GhajarLog.d("Store", "time range $timeDays: ${inRange.size} of ${all.size} plan(s) in bucket")
        return inRange
    }

    suspend fun customQuote(countryId: String, trafficGb: Int, timeDays: Int): GhajarCustomQuote {
        val payload = action(
            "custom_price",
            params = mapOf(
                "country_id" to countryId,
                "traffic_gb" to trafficGb.toString(),
                "time_days" to timeDays.toString()
            )
        ).payloadObject()
        return GhajarCustomQuote(
            price = payload.optNullableDouble("price")?.toLong(),
            trafficMin = payload.optInt("traffic_min"),
            trafficMax = payload.optInt("traffic_max"),
            timeMin = payload.optInt("time_min"),
            timeMax = payload.optInt("time_max")
        )
    }

    suspend fun ownedServices(maxPages: Int = 10): List<GhajarOwnedService> {
        val collected = mutableListOf<GhajarOwnedService>()
        var page = 1
        var pages = 1
        do {
            val payload = action(
                "invoices",
                params = mapOf("page" to page.toString(), "limit" to "10")
            ).payloadObject()
            payload.optJSONArray("items").orEmpty().objects().mapNotNullTo(collected) { row ->
                val username = row.optString("username")
                if (username.isBlank()) return@mapNotNullTo null
                // `user_info` is the panel's own answer, cached on the invoice
                // row by the server. Reading it here is what lets the list show
                // real remaining volume and days without one extra request per
                // service - 52 services would have been 52 round trips.
                val info = row.optString("user_info").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                GhajarOwnedService(
                    username = username,
                    productName = visible(row.optString("name_product", row.optString("product_name", "سرویس قاجار"))),
                    status = visible(row.optString("status", row.optString("Status", "unknown"))),
                    location = visible(row.optString("Service_location")),
                    invoiceId = row.optString("id_invoice"),
                    dataLimitBytes = info?.optNullableLong("data_limit"),
                    usedBytes = info?.optNullableLong("used_traffic"),
                    expireTimestamp = info?.optNullableLong("expire"),
                    planGb = row.optNullableDouble("Volume"),
                    planDays = row.optNullableInt("Service_time"),
                    soldAt = row.optNullableLong("time_sell")
                )
            }
            pages = payload.optInt("total_pages", 1).coerceAtLeast(1).coerceAtMost(maxPages)
            page++
        } while (page <= pages)
        return collected.distinctBy { it.username }
    }

    suspend fun service(username: String): GhajarServiceDetails {
        val payload = action("service", params = mapOf("username" to username)).payloadObject()
        return serviceFrom(payload, username)
    }

    /** Renewal offer for one already-owned service, from `service_renew_options`. */
    suspend fun renewOptions(username: String): GhajarRenewOptions {
        val payload = action("service_renew_options", params = mapOf("username" to username)).payloadObject()
        val products = payload.optJSONArray("products").orEmpty().objects().mapNotNull { row ->
            val code = row.optString("code")
            if (code.isBlank()) return@mapNotNull null
            GhajarRenewProduct(
                code = code,
                name = visible(row.optString("name", "پلن قاجار")),
                volumeGb = row.optInt("volume_gb"),
                timeDays = row.optInt("time_days"),
                price = row.optNullableDouble("price")?.toLong() ?: 0,
                showPrice = row.optBoolean("show_price", true),
                note = visible(row.optString("note"))
            )
        }
        val currentPlan = payload.optJSONObject("current_plan")
        val currentCode = currentPlan?.optString("code")?.takeIf { it.isNotBlank() }
        val custom = payload.optJSONObject("custom")
        return GhajarRenewOptions(
            username = payload.optString("username", username),
            panelName = visible(payload.optJSONObject("panel")?.optString("name").orEmpty()),
            products = products.map { it.copy(isCurrentPlan = it.code == currentCode) },
            currentPlanCode = currentCode,
            showPrice = payload.optBoolean("show_price", true),
            discountPercent = payload.optInt("discount"),
            balance = payload.optNullableDouble("balance")?.toLong() ?: 0,
            custom = GhajarRenewCustomOptions(
                enabled = custom?.optBoolean("enabled") ?: false,
                forced = custom?.optBoolean("force") ?: false,
                pricePerGb = custom?.optNullableLong("price_per_gb") ?: 0,
                pricePerDay = custom?.optNullableLong("price_per_day") ?: 0,
                minVolumeGb = custom?.optInt("min_volume_gb") ?: 0,
                maxVolumeGb = custom?.optInt("max_volume_gb") ?: 0,
                minTimeDays = custom?.optInt("min_time_days") ?: 0,
                maxTimeDays = custom?.optInt("max_time_days") ?: 0
            )
        )
    }

    /**
     * Confirms renewal of [username]'s service with either a catalog [productCode]
     * or a custom volume/time pair, mirroring [purchase]'s payment-required shape:
     * `service_renew_confirm` answers with `{kind: "requires_payment", ...}` inside
     * `obj` when the wallet balance falls short, exactly like the purchase flow.
     */
    suspend fun confirmRenew(
        username: String,
        productCode: String? = null,
        customVolumeGb: Int? = null,
        customTimeDays: Int? = null,
        discountCode: String? = null
    ): GhajarRenewResult {
        val body = JSONObject().put("username", username)
        if (productCode != null) {
            body.put("product_code", productCode)
        } else {
            body.put(
                "custom",
                JSONObject()
                    .put("traffic_gb", customVolumeGb ?: 0)
                    .put("time_days", customTimeDays ?: 0)
            )
        }
        discountCode?.takeIf { it.isNotBlank() }?.let { body.put("discount_code", it) }

        val root = action("service_renew_confirm", method = "POST", body = body, allowPaymentRequired = true)
        val payload = root.payloadObject()
        val paymentObject = when {
            root.optBoolean("requires_payment") -> root
            payload.optString("kind") == "requires_payment" -> payload
            else -> null
        }
        if (paymentObject != null) {
            return GhajarRenewResult(
                completed = false,
                requiresPayment = true,
                username = paymentObject.optString("username", username),
                amountDue = paymentObject.optNullableDouble("amount_due")?.toLong() ?: 0,
                balance = paymentObject.optNullableDouble("balance")?.toLong() ?: 0,
                price = paymentObject.optNullableDouble("price")?.toLong() ?: 0,
                orderId = paymentObject.optString("order_id").takeIf { it.isNotBlank() }
            )
        }
        return GhajarRenewResult(
            completed = root.optBoolean("status", true) && payload.optBoolean("success", true),
            requiresPayment = false,
            username = username,
            amountDue = 0,
            balance = payload.optNullableDouble("balance")?.toLong() ?: 0,
            price = 0,
            orderId = null
        )
    }

    /**
     * The whole feed: this user's notices, and whether the shop is open.
     *
     * Reads `notices.php`, which is where the expiry and volume warnings now
     * live. Before it existed, those warnings were Telegram messages and
     * nothing else - so a user who read their services here, or who had the
     * bot muted, was simply never told a service was about to end, and the
     * renew button existed nowhere but inside a Telegram chat.
     *
     * Falls back to the two old broadcast endpoints when the feed is not
     * there. That is not defensive padding: the bot and the app ship
     * separately, and an app updated before its shop would otherwise lose the
     * notifications it already had.
     */
    suspend fun noticeFeed(): GhajarNoticeFeed {
        val raw = try {
            withContext(Dispatchers.IO) {
                requestJson(
                    url = URL("${BrandConfig.NOTICES_API_URL}?action=feed&client=${BrandConfig.CLIENT_ID}"),
                    method = "GET",
                    bearer = requireToken(),
                    body = null
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            return legacyFeed()
        }

        if (!raw.optBoolean("status")) return legacyFeed()

        val rows = raw.optJSONArray("notices").orEmpty().objects()
        val shop = raw.optJSONObject("shop")
        return GhajarNoticeFeed(
            notices = rows.mapNotNull(::feedNotice),
            unseen = raw.optInt("unseen"),
            shopEnabled = shop?.optBoolean("enabled", true) ?: true,
            shopMessage = shop?.optString("message").orEmpty(),
            gate = raw.optString("gate"),
            gateMessage = raw.optString("gate_message")
        )
    }

    /** Kept so existing callers that only want the list still compile and work. */
    suspend fun notices(forDelivery: Boolean = false): List<GhajarNotice> {
        val feed = noticeFeed()
        return if (forDelivery) feed.notices.filter { it.shouldFloat } else feed.notices
    }

    private fun feedNotice(row: JSONObject): GhajarNotice? {
        val body = visible(row.optString("body")).trim()
        if (body.isBlank()) return null
        val kind = row.optString("kind")
        val serverId = row.optInt("id")
        return GhajarNotice(
            // Prefixed and signed by the row id, including the negative ids the
            // server uses for the global broadcast, so the two id spaces cannot
            // collide in the acknowledged set.
            id = "notice:$serverId",
            title = visible(row.optString("title")).ifBlank {
                when (kind) {
                    "service_time" -> "مهلت سرویس رو به پایان است"
                    "service_volume" -> "حجم سرویس رو به پایان است"
                    "shop_status" -> "وضعیت فروشگاه"
                    else -> "اعلان قاجار وی پی ان"
                }
            },
            message = body,
            important = kind == "shop_status" || kind == "service_time" || kind == "service_volume",
            serviceAlert = kind == "service_time" || kind == "service_volume",
            // The renew action's reference is the service username, which is
            // what `service_renew_options` is keyed on. Notices written before
            // the server was updated carry the invoice id instead, and the
            // shop screen translates those against the owned list rather than
            // asking the server about a service it cannot find.
            serviceUsername = row.optString("action_ref").takeIf {
                it.isNotBlank() && row.optString("action") == "renew"
            },
            action = row.optString("action", "none"),
            actionRef = row.optString("action_ref"),
            shouldFloat = row.optBoolean("should_float", true),
            repeatAfterSec = row.optInt("repeat_after"),
            seen = row.optBoolean("seen")
        )
    }

    /** The pre-feed endpoints, for a shop that has not been updated yet. */
    private suspend fun legacyFeed(): GhajarNoticeFeed {
        var failure: Exception? = null
        var fetched = 0
        suspend fun fetchNotice(actionName: String): JSONObject? = try {
            action(actionName).payloadObject().also { fetched++ }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
          catch (e: Exception) { failure = e; null }
        val recent = fetchNotice("notification_recent")?.optJSONArray("notifications").orEmpty().objects()
        val active = fetchNotice("notification_info")?.optJSONObject("notification")
        if (fetched == 0) throw (failure ?: GhajarApiException("دریافت اعلان ناموفق بود"))
        val now = System.currentTimeMillis() / 1000
        fun deliverable(row: JSONObject) =
            !row.optBoolean("seen") && (row.optLong("expires_at") <= 0 || row.optLong("expires_at") > now)
        val list = buildList {
            active?.takeIf(::deliverable)?.let { noticeFrom(it, "notification", false) }
                ?.let { add(it.copy(important = true)) }
            recent.filter(::deliverable).mapNotNullTo(this) { noticeFrom(it, "notification", false) }
        }.distinctBy { it.id }
        // The old endpoints know nothing about the shop being switched off, and
        // reporting it as closed on no evidence would hide a working store.
        return GhajarNoticeFeed(list, list.count { !it.seen }, true, "", "", "")
    }

    /**
     * Records that notices were put in front of the user.
     *
     * `shown` rather than `dismiss`: it marks them seen *and* moves the repeat
     * clock, so a warning with an interval comes back later. Only the close
     * button is a dismissal.
     */
    suspend fun markNoticesShown(ids: List<String>) = stampNotices("shown", ids)

    suspend fun dismissNotice(id: String) {
        if (id.startsWith("notice:")) {
            stampNotices("dismiss", listOf(id))
            return
        }
        val serverId = id.removePrefix("notification:").toLongOrNull() ?: return
        action("notification_dismiss", "POST", body = JSONObject().put("id", serverId))
    }

    private suspend fun stampNotices(what: String, ids: List<String>) {
        val serverIds = ids.mapNotNull { it.removePrefix("notice:").toIntOrNull() }
        if (serverIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                requestJson(
                    url = URL("${BrandConfig.NOTICES_API_URL}?action=$what&client=${BrandConfig.CLIENT_ID}"),
                    method = "POST",
                    bearer = requireToken(),
                    body = JSONObject().put("ids", JSONArray(serverIds))
                )
            }
        }
    }

    suspend fun purchase(request: GhajarPurchaseRequest): GhajarPurchaseResult {
        val body = JSONObject().put("country_id", request.countryId)
        if (request.serviceId != null) {
            body.put("service_id", request.serviceId)
        } else {
            body.put(
                "custom_service",
                JSONObject()
                    .put("traffic_gb", request.customTrafficGb ?: 0)
                    .put("time_days", request.customTimeDays ?: 0)
            )
        }
        request.customUsername?.takeIf { it.isNotBlank() }?.let { body.put("custom_username", it) }
        request.note?.takeIf { it.isNotBlank() }?.let { body.put("custom_note", it) }
        request.discountCode?.takeIf { it.isNotBlank() }?.let { body.put("discount_code", it) }

        val root = action("purchase", method = "POST", body = body, allowPaymentRequired = true)
        val payload = root.payloadObject()
        val paymentObject = when {
            root.optBoolean("requires_payment") -> root
            payload.optBoolean("requires_payment") -> payload
            else -> null
        }
        if (paymentObject != null) {
            return GhajarPurchaseResult(
                completed = false,
                requiresPayment = true,
                username = paymentObject.optString("username").takeIf { it.isNotBlank() },
                amountDue = paymentObject.optDouble("amount_due", 0.0).toLong(),
                balance = paymentObject.optDouble("balance", 0.0).toLong(),
                price = paymentObject.optDouble("price", 0.0).toLong(),
                service = null
            )
        }
        val serviceObject = payload.optJSONObject("service") ?: root.optJSONObject("service")
        return GhajarPurchaseResult(
            completed = root.optBoolean("status", true) && payload.optBoolean("success", true),
            requiresPayment = false,
            username = serviceObject?.optString("username")?.takeIf { it.isNotBlank() }
                ?: payload.optString("username").takeIf { it.isNotBlank() },
            amountDue = 0,
            balance = payload.optDouble("balance", 0.0).toLong(),
            price = 0,
            service = serviceObject?.let { serviceFrom(it, it.optString("username")) }
        )
    }

    suspend fun paymentOptions(): GhajarPaymentOptions {
        val payload = action("payment_methods").payloadObject()
        val methods = payload.optJSONArray("methods").orEmpty().objects().mapNotNull { row ->
            val id = row.optString("id")
            if (id.isBlank()) return@mapNotNull null
            GhajarPaymentMethod(
                id = id,
                label = visible(row.optString("label", id)),
                kind = row.optString("kind", "form"),
                directUrl = row.optString("url").takeIf(::isHttps),
                minimum = row.optLong("min"),
                maximum = row.optLong("max")
            )
        }
        return GhajarPaymentOptions(
            methods = methods,
            balance = payload.optDouble("balance", 0.0).toLong(),
            currency = visible(payload.optString("currency", "تومان"))
        )
    }

    suspend fun beginPayment(method: String, amount: Long, purchaseUsername: String?): GhajarPaymentInit {
        val body = JSONObject().put("method", method).put("amount", amount)
        purchaseUsername?.takeIf { it.isNotBlank() }?.let { body.put("purchase_username", it) }
        val payload = action("payment_init", method = "POST", body = body).payloadObject()
        return GhajarPaymentInit(
            kind = payload.optString("kind", "manual"),
            orderId = payload.optString("order_id"),
            url = payload.optString("url").takeIf(::isHttps),
            cardNumber = payload.optString("card_number").takeIf { it.isNotBlank() },
            cardHolder = payload.optString("name_card").takeIf { it.isNotBlank() },
            amount = payload.optDouble("amount", amount.toDouble()).toLong(),
            amountRial = payload.optDouble("amount_rial", amount * 10.0).toLong(),
            message = visible(payload.optString("message")),
            method = method,
            expiresAt = payload.optLong("expires_at")
        )
    }

    suspend fun pendingPayments(): List<GhajarPendingPayment> {
        val items = action("pending_payments").payloadObject().optJSONArray("pending") ?: JSONArray()
        return (0 until items.length()).mapNotNull { i -> items.optJSONObject(i)?.let(GhajarPendingPayment::from) }
    }

    suspend fun cancelPayment(orderId: String): JSONObject =
        action("crypto_cancel_invoice", method = "POST", body = JSONObject().put("order_id", orderId)).payloadObject()

    suspend fun transactions(page: Int): JSONObject =
        action("transactions", params = mapOf("page" to page.toString(), "limit" to "20")).payloadObject()

    suspend fun paymentStatus(orderId: String): JSONObject =
        action("payment_status", params = mapOf("order_id" to orderId)).payloadObject()

    /**
     * Asks the panel to credit the wallet for a confirmed payment whose service
     * delivery failed. The panel remains the financial authority and must treat
     * repeated requests for the same order as a no-op; the client enforces its
     * own idempotency with GhajarOrderFlow and only records WALLET_REFUNDED when
     * the server explicitly confirms `wallet_credited`.
     */
    suspend fun requestWalletFallback(orderId: String): Boolean {
        val payload = action(
            "payment_fallback_wallet",
            method = "POST",
            body = JSONObject().put("order_id", orderId)
        ).payloadObject()
        return payload.optBoolean("wallet_credited", payload.optBoolean("credited", false)) ||
            payload.optBoolean("success", false) && payload.optString("stage") == "wallet_refunded"
    }

    suspend fun uploadReceipt(orderId: String, photo: Uri): String = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val size = resolver.openAssetFileDescriptor(photo, "r")?.use { it.length } ?: -1L
        if (size > MAX_RECEIPT_BYTES) throw GhajarApiException("حجم رسید نباید بیشتر از ۸ مگابایت باشد")
        val fileName = resolver.query(photo, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "receipt.jpg"
        val mime = resolver.getType(photo)?.takeIf { it in setOf("image/jpeg", "image/png", "image/webp") }
            ?: throw GhajarApiException("رسید باید تصویر JPEG، PNG یا WebP باشد")
        val boundary = "Ghajarvpn-${UUID.randomUUID()}"
        val connection = (URL("${BrandConfig.MINIAPP_API_URL}?actions=payment_receipt").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = UPLOAD_TIMEOUT
            doOutput = true
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${requireToken()}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("User-Agent", userAgent())
        }
        try {
            DataOutputStream(connection.outputStream).use { output ->
                output.writeUtf8("--$boundary\r\n")
                output.writeUtf8("Content-Disposition: form-data; name=\"order_id\"\r\n\r\n")
                output.writeUtf8(orderId)
                output.writeUtf8("\r\n--$boundary\r\n")
                output.writeUtf8("Content-Disposition: form-data; name=\"photo\"; filename=\"$fileName\"\r\n")
                output.writeUtf8("Content-Type: $mime\r\n\r\n")
                resolver.openInputStream(photo)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_RECEIPT_BYTES) throw GhajarApiException("حجم رسید نباید بیشتر از ۸ مگابایت باشد")
                        output.write(buffer, 0, read)
                    }
                }
                    ?: throw GhajarApiException("فایل رسید قابل خواندن نیست")
                output.writeUtf8("\r\n--$boundary--\r\n")
            }
            val envelope = readResponse(connection, authenticated = true)
            visible(envelope.payloadObject().optString("message", "رسید ارسال شد"))
        } finally {
            connection.disconnect()
        }
    }

    suspend fun trialOptions(): GhajarTrialOptions {
        val payload = action("test_account_info").payloadObject()
        return GhajarTrialOptions(
            panels = payload.optJSONArray("panels").orEmpty().objects().mapNotNull { row ->
                val code = row.optString("id")
                if (code.isBlank()) null else GhajarTrialPanel(code, visible(row.optString("name")), row.opt("limit_left").takeUnless { it == null || it == JSONObject.NULL }?.toString()?.toIntOrNull())
            },
            remaining = payload.opt("limit_left").takeUnless { it == null || it == JSONObject.NULL }?.toString()?.toIntOrNull(),
            canRequest = payload.optBoolean("available")
        )
    }

    suspend fun createTrial(panelCode: String, username: String?): GhajarServiceDetails {
        val body = JSONObject().put("country_id", panelCode)
        username?.takeIf { it.isNotBlank() }?.let { body.put("custom_username", it) }
        val payload = action("test_account_create", method = "POST", body = body).payloadObject()
        val issued = payload.optJSONObject("service") ?: payload
        val issuedUsername = issued.optString("username")
        return serviceFrom(issued, issuedUsername)
    }

    suspend fun importServiceOnce(store: ConfigStore, service: GhajarServiceDetails): Int = deliveryMutex.withLock {
        importServiceLocked(store, service)
    }

    private suspend fun importServiceLocked(store: ConfigStore, service: GhajarServiceDetails): Int {
        val joined = service.outputs.filter { it.isNotBlank() }.joinToString("\n")
        val payload = service.subscriptionUrl?.takeIf { it.isNotBlank() } ?: joined
        if (payload.isBlank()) return 0
        val fingerprint = sha256("${service.username}:$payload")
        val prefs = appContext.getSharedPreferences("ghajarvpn_deliveries_v2", Context.MODE_PRIVATE)
        val installed = prefs.getStringSet("installed", emptySet()).orEmpty().toMutableSet()
        // A known URL still needs an immediate refresh; a previous import may
        // have registered the subscription without any usable configurations.

        // The subscription is tried first; when the phone cannot read it (a
        // seller panel blocked from this network, or not answering yet) the
        // configs the server already sent are imported instead, so a paid
        // service is never left undelivered.
        val url = service.subscriptionUrl?.takeIf { it.startsWith("https://") }
        val fetched = url?.let { runCatching { SubscriptionFetcher.fetchFull(it) }.getOrNull() }
            ?.takeIf { it.configs.isNotEmpty() }
        if (url != null && fetched == null && joined.isBlank()) {
            throw GhajarApiException("سرویس صادر شد، اما ساب هنوز کانفیگ ندارد؛ از «سرویس‌های من» دوباره دریافت کن.")
        }
        val imported = if (url != null && fetched != null) {
            withContext(Dispatchers.Main) {
                val existing = store.subscriptions.value.firstOrNull { it.url == url }
                val subscription = existing ?: Subscription(name = service.productName.ifBlank { "سرویس قاجار" }, url = url)
                val info = fetched.userInfo
                store.upsertSubscription(subscription.copy(
                    used = info?.used ?: subscription.used,
                    total = info?.total ?: subscription.total,
                    expire = info?.expire ?: subscription.expire,
                    lastUpdated = System.currentTimeMillis(),
                    // Recorded here and nowhere else: this is the only moment
                    // the app knows which panel service a subscription is,
                    // and it is what makes one-tap renewal possible later.
                    serviceUsername = service.username
                ), fetched.configs)
                fetched.configs.size
            }
        } else {
            val configs = ConfigParser.parseBundle(joined)
            withContext(Dispatchers.Main) {
                val current = store.configs.value
                val missing = configs.filter { candidate -> current.none { existing ->
                    existing.protocol == candidate.protocol && existing.address == candidate.address &&
                        existing.port == candidate.port && existing.uuid == candidate.uuid && existing.password == candidate.password
                } }
                if (missing.isNotEmpty()) store.addToLocalSub(service.productName.ifBlank { "سرویس قاجار" }, missing)
            }
            configs.size
        }
        if (imported > 0) {
            installed += fingerprint
            prefs.edit().putStringSet("installed", installed.toList().takeLast(200).toSet()).apply()
        }
        return imported
    }

    internal suspend fun support(name: String, body: JSONObject? = null, params: Map<String, String> = emptyMap()): JSONObject {
        require(name in setOf("tickets", "ticket_thread", "ticket_departments", "ticket_create", "ticket_reply", "ticket_close"))
        return action(name, if (body == null) "GET" else "POST", params, body).payloadObject()
    }

    private suspend fun action(
        name: String,
        method: String = "GET",
        params: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        allowPaymentRequired: Boolean = false
    ): JSONObject = withContext(Dispatchers.IO) {
        val query = linkedMapOf("actions" to name).apply { putAll(params) }.entries.joinToString("&") {
            URLEncoder.encode(it.key, Charsets.UTF_8.name()) + "=" +
                URLEncoder.encode(it.value, Charsets.UTF_8.name())
        }
        val payload = if (method == "GET" || method == "HEAD") null else
            JSONObject(body?.toString() ?: "{}").put("actions", name)
        val result = requestJson(
            url = URL("${BrandConfig.MINIAPP_API_URL}?$query"),
            method = method,
            bearer = requireToken(),
            body = payload,
            allowPaymentRequired = allowPaymentRequired
        )
        if (name in DIAGNOSTIC_ACTIONS) logResultShape(name, params, result)
        result
    }

    /**
     * One call against the marketplace endpoint.
     *
     * Deliberately the same transport as [action] - the same bearer, the same
     * direct-then-tunnel fallback, the same error mapping - because a
     * marketplace call is reaching this shop's server first and inherits every
     * reason the ordinary shop can be unreachable. Only the URL differs.
     */
    private suspend fun marketAction(
        name: String,
        method: String = "GET",
        params: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        /**
         * True for the three read-only browse calls, which work with or
         * without an account.
         *
         * The shops list is a shop window. Demanding a linked Telegram
         * account before showing it meant a new user saw nothing at all where
         * the shops were, which is the complaint this exists to answer. The
         * token is still sent when there is one - the server uses it to fill
         * in a seller's own shops and the unredacted card details - so being
         * signed in is never worse, only unnecessary.
         */
        allowAnonymous: Boolean = false
    ): JSONObject = withContext(Dispatchers.IO) {
        val query = linkedMapOf("actions" to name).apply { putAll(params) }.entries.joinToString("&") {
            URLEncoder.encode(it.key, Charsets.UTF_8.name()) + "=" +
                URLEncoder.encode(it.value, Charsets.UTF_8.name())
        }
        val payload = if (method == "GET" || method == "HEAD") null else
            JSONObject(body?.toString() ?: "{}").put("actions", name)
        requestJson(
            url = URL("${BrandConfig.MARKET_API_URL}?$query"),
            method = method,
            bearer = if (allowAnonymous) account.token().takeIf { it.isNotBlank() } else requireToken(),
            body = payload
        )
    }

    /** Whether this phone is linked to an account, for screens that work either way. */
    fun isSignedIn(): Boolean = account.token().isNotBlank()

    /**
     * The shop list, and whether the marketplace is on at all.
     *
     * A disabled marketplace is a success with an empty list, not a failure:
     * the app asks on every open, and turning that into an error would put a
     * red banner on a feature the owner simply has not switched on.
     */
    suspend fun marketShops(): GhajarMarketFeed {
        val payload = marketAction("shops", allowAnonymous = true).payloadObject()
        GhajarShopLogo.version.value = payload.optInt("ghajar_logo_version")
        return GhajarMarketFeed(
            enabled = payload.optBoolean("enabled", true),
            registerFee = payload.optNullableDouble("terms_fee")?.toLong() ?: 0,
            shops = payload.optJSONArray("shops").orEmpty().objects().map { marketShopFrom(it) }
        )
    }

    suspend fun marketShop(shopId: Int): GhajarMarketShop {
        val payload = marketAction("shop", params = mapOf("shop_id" to shopId.toString()),
            allowAnonymous = true).payloadObject()
        return marketShopFrom(payload).copy(
            reviews = payload.optJSONArray("reviews_list").orEmpty().objects().map { row ->
                GhajarMarketReview(
                    stars = row.optInt("stars"),
                    body = visible(row.optString("body")),
                    createdAt = row.optNullableLong("created_at") ?: 0L
                )
            }
        )
    }

    suspend fun marketCatalog(shopId: Int): GhajarMarketCatalog {
        val payload = marketAction("shop_catalog",
            params = mapOf("shop_id" to shopId.toString()), allowAnonymous = true).payloadObject()
        return marketCatalogFrom(payload)
    }

    private fun marketCatalogFrom(payload: JSONObject) = GhajarMarketCatalog(
        panels = payload.optJSONArray("panels").orEmpty().objects().map { row ->
            GhajarMarketPanel(
                code = row.optString("code"),
                name = visible(row.optString("name")),
                country = visible(row.optString("country")),
                flag = visible(row.optString("flag")),
                custom = row.optBoolean("custom"),
                gbPrice = (row.optNullableDouble("gb_price") ?: 0.0).toLong(),
                dayPrice = (row.optNullableDouble("day_price") ?: 0.0).toLong(),
                minGb = row.optInt("min_gb"),
                maxGb = row.optInt("max_gb"),
                minDays = row.optInt("min_days"),
                maxDays = row.optInt("max_days"),
                test = row.optBoolean("test"),
                testHours = row.optInt("test_hours"),
                testMb = row.optInt("test_mb")
            )
        }.filter { it.code.isNotBlank() },
        products = payload.optJSONArray("products").orEmpty().objects().map { row ->
            GhajarMarketProduct(
                code = row.optString("code"),
                name = visible(row.optString("name")),
                price = row.optNullableDouble("price")?.toLong() ?: 0,
                volumeGb = row.optInt("volume_gb"),
                timeDays = row.optInt("time_days"),
                note = visible(row.optString("note")),
                location = visible(row.optString("location")),
                category = visible(row.optString("category"))
            )
        }.filter { it.code.isNotBlank() },
        methods = payload.optJSONArray("payment").orEmpty().objects().map { marketMethodFrom(it) },
        categories = payload.optJSONArray("categories").orEmpty().objects().map { row ->
            GhajarCategory(row.optString("id"), visible(row.optString("name")))
        }.filter { it.name.isNotBlank() }
    )

    /**
     * One shop's page: the card, reviews, catalogue with custom and test
     * pricing, payment methods, and this buyer's balance and unread count.
     */
    suspend fun marketHome(shopId: Int): GhajarMarketHome {
        val payload = marketAction("shop_home", params = mapOf("shop_id" to shopId.toString()),
            allowAnonymous = true).payloadObject()
        val catalog = payload.optJSONObject("catalog") ?: JSONObject()
        val test = catalog.optJSONObject("test") ?: JSONObject()
        val me = payload.optJSONObject("me")
        return GhajarMarketHome(
            shop = marketShopFrom(payload).copy(
                reviews = payload.optJSONArray("reviews_list").orEmpty().objects().map { row ->
                    GhajarMarketReview(row.optInt("stars"), visible(row.optString("body")),
                        row.optNullableLong("created_at") ?: 0L)
                }
            ),
            reachable = catalog.optBoolean("reachable", true),
            catalog = marketCatalogFrom(catalog),
            testEnabled = test.optBoolean("enabled"),
            testUsed = test.optBoolean("used"),
            wallet = me?.let { (it.optNullableDouble("wallet") ?: 0.0).toLong() },
            unread = me?.optInt("unread") ?: 0,
            blocked = me?.optBoolean("blocked") ?: false,
            mine = me?.optBoolean("mine") ?: false
        )
    }

    suspend fun marketOrderStart(
        shopId: Int,
        productCode: String,
        panelCode: String,
        method: String,
        /** purchase, renew, wallet (a top-up) or test. */
        kind: String = "purchase",
        /** "custom" for a size the buyer picked, with [volumeGb] and [timeDays]. */
        plan: String = "",
        volumeGb: Int = 0,
        timeDays: Int = 0,
        /** For a renewal: which of this buyer's services at the shop. */
        invoiceId: String = "",
        username: String = "",
        /** For a top-up: how much. */
        amount: Long = 0,
        /** The shop's discount code, if the buyer entered one. */
        discountCode: String = ""
    ): GhajarMarketOrder {
        val envelope = marketAction("order_start", method = "POST", body = JSONObject()
            .put("shop_id", shopId)
            .put("kind", kind)
            .put("product_code", productCode)
            .put("panel_code", panelCode)
            .put("method", method)
            .put("plan", plan)
            .put("volume_gb", volumeGb)
            .put("time_days", timeDays)
            .put("invoice_id", invoiceId)
            .put("username", username)
            .put("amount", amount)
            .put("discount_code", discountCode))
        val payload = envelope.payloadObject()
        return GhajarMarketOrder(
            id = payload.optInt("id"),
            method = payload.optString("method"),
            methodLabel = visible(payload.optString("method_label")),
            needs = payload.optString("needs"),
            amount = payload.optNullableDouble("amount")?.toLong() ?: 0,
            cardNumber = payload.optString("card_number"),
            cardHolder = visible(payload.optString("card_holder")),
            contact = payload.optString("contact"),
            gatewayUrl = payload.optString("gateway_url"),
            productName = visible(payload.optString("product_name")),
            status = payload.optString("status"),
            message = visible(envelope.optString("msg"))
        )
    }

    /**
     * Sends a receipt on to the seller, re-encoded for the trip.
     *
     * Base64 in a JSON body rather than multipart, because the far side of
     * this is the *seller's* own bot and its `send_message` takes base64. Our
     * server is only the courier: it forwards the photo and keeps no copy, and
     * neither does the app once this returns.
     *
     * The image is decoded and re-compressed first. A modern phone camera
     * produces 4-12 MB per shot, base64 adds a third on top, and a bank
     * receipt is legible at 1600px - so sending the original would mean
     * uploads that fail on a mobile connection to prove something a 200 KB
     * JPEG proves just as well.
     */
    suspend fun marketSubmitReceipt(paymentId: Int, photo: Uri, note: String): String =
        withContext(Dispatchers.IO) {
            val encoded = encodeReceipt(photo)
            val payload = marketAction("order_receipt", method = "POST", body = JSONObject()
                .put("payment_id", paymentId)
                .put("receipt", encoded)
                .put("note", note))
            visible(payload.optString("msg", "رسید ارسال شد."))
        }

    /**
     * Reads an image, shrinks it to at most [RECEIPT_MAX_EDGE] on its long
     * side, and returns it as base64 JPEG.
     *
     * Two passes over the file: the first reads only the header for the real
     * dimensions, so the second can ask BitmapFactory for a subsampled decode
     * and never hold the full-size bitmap in memory. Decoding a 12MP photo at
     * full size to then throw most of it away is how an image picker turns
     * into an OutOfMemoryError on a cheap phone.
     */
    private fun encodeReceipt(photo: Uri): String {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(photo)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw GhajarApiException("این فایل تصویر خوانده نشد.")
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= RECEIPT_MAX_EDGE ||
            bounds.outHeight / (sample * 2) >= RECEIPT_MAX_EDGE) {
            sample *= 2
        }
        val decoded = resolver.openInputStream(photo)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inSampleSize = sample
            })
        } ?: throw GhajarApiException("این فایل تصویر خوانده نشد.")

        return try {
            ByteArrayOutputStream().use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 82, out)
                val bytes = out.toByteArray()
                if (bytes.size.toLong() > MAX_RECEIPT_BYTES) {
                    throw GhajarApiException("حجم رسید نباید بیشتر از ۸ مگابایت باشد")
                }
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        } finally {
            // Recycled explicitly: this runs on a shared IO dispatcher and the
            // bitmap is several megabytes that GC has no urgency to reclaim.
            decoded.recycle()
        }
    }

    suspend fun marketOrderStatus(paymentId: Int): GhajarMarketOrderStatus {
        val payload = marketAction("order_status",
            params = mapOf("payment_id" to paymentId.toString())).payloadObject()
        return GhajarMarketOrderStatus(
            id = payload.optInt("id"),
            shopId = payload.optInt("shop_id"),
            status = payload.optString("status"),
            rejectReason = visible(payload.optString("reject_reason")),
            amount = payload.optNullableDouble("amount")?.toLong() ?: 0,
            username = payload.optString("username"),
            configs = payload.optJSONArray("configs").orEmpty().let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
            },
            subscription = payload.optString("subscription")
        )
    }

    suspend fun marketReview(shopId: Int, stars: Int, body: String): String {
        val payload = marketAction("review_submit", method = "POST", body = JSONObject()
            .put("shop_id", shopId)
            .put("stars", stars)
            .put("body", body))
        return visible(payload.optString("msg"))
    }

    suspend fun marketTerms(): GhajarMarketTerms {
        val payload = marketAction("register_terms", allowAnonymous = true).payloadObject()
        return GhajarMarketTerms(
            terms = visible(payload.optString("terms")),
            fee = payload.optNullableDouble("fee")?.toLong() ?: 0,
            commissionPercent = payload.optNullableDouble("commission_percent") ?: 0.0,
            cycleDays = payload.optInt("cycle_days", 30),
            penaltyPerDay = payload.optNullableDouble("penalty_day")?.toLong() ?: 0,
            graceDays = payload.optInt("grace_days", 15),
            myShops = payload.optJSONArray("my_shops").orEmpty().objects().map { row ->
                GhajarMarketOwnShop(
                    id = row.optInt("id"),
                    name = visible(row.optString("name")),
                    status = row.optString("status"),
                    lastError = visible(row.optString("last_error"))
                )
            }
        )
    }

    private fun shopParam(shopId: Int) = mapOf("shop_id" to shopId.toString())

    /** What a shop's discount code does to a price: (new price, message). */
    suspend fun marketDiscountCheck(shopId: Int, code: String, amount: Long): Pair<Long, String> {
        val envelope = marketAction("discount_check", params = shopParam(shopId) +
            mapOf("code" to code, "amount" to amount.toString()), allowAnonymous = true)
        val payload = envelope.payloadObject()
        return (payload.optNullableDouble("amount") ?: amount.toDouble()).toLong() to visible(envelope.optString("msg"))
    }

    /** Redeems a gift code into the wallet at this shop: (ok, message). */
    suspend fun marketGiftRedeem(shopId: Int, code: String): Pair<Boolean, String> {
        val envelope = marketAction("gift_redeem", method = "POST",
            body = JSONObject().put("shop_id", shopId).put("code", code))
        return envelope.optBoolean("status") to visible(envelope.optString("msg"))
    }

    /**
     * The owner's codes at their shop. [action] is shop_codes, code_add,
     * code_toggle or code_delete; [fields] carries the form or the id.
     */
    suspend fun marketCodes(shopId: Int, action: String = "shop_codes", fields: Map<String, String> = emptyMap()): Pair<GhajarMarketCodes, String> {
        require(action in setOf("shop_codes", "code_add", "code_toggle", "code_delete"))
        val body = JSONObject().put("shop_id", shopId)
        fields.forEach { (k, v) -> body.put(k, v) }
        val envelope = marketAction(action, method = "POST", body = body)
        val payload = envelope.optJSONObject("obj") ?: JSONObject()
        fun list(key: String, gift: Boolean) = payload.optJSONArray(key).orEmpty().objects().map { row ->
            GhajarMarketCode(
                source = row.optString("source"),
                id = row.optInt("id"),
                code = row.optString("code"),
                value = row.optNullableDouble(if (gift) "amount" else "percent") ?: 0.0,
                maxUses = row.optInt("max_uses"),
                used = row.optInt("used"),
                expiresAt = row.optNullableLong("expires_at") ?: 0L,
                active = row.optBoolean("active", true),
                perUser = row.optInt("per_user"),
                firstOnly = row.optBoolean("first_only"),
                product = row.optString("product"),
                panel = row.optString("panel")
            )
        }
        val msg = visible(envelope.optString("msg"))
        if (!envelope.optBoolean("status", true) && msg.isNotBlank()) throw GhajarApiException(msg)
        return GhajarMarketCodes(list("discounts", false), list("gifts", true)) to msg
    }

    /** What this buyer bought at one shop, with live usage from the seller's panel. */
    suspend fun marketServices(shopId: Int): List<GhajarMarketService> =
        marketAction("my_services", params = shopParam(shopId)).payloadObject()
            .optJSONArray("services").orEmpty().objects().map { row ->
                val usage = row.optJSONObject("usage") ?: JSONObject()
                GhajarMarketService(
                    invoiceId = row.optString("invoice_id"),
                    username = row.optString("username"),
                    productName = visible(row.optString("product_name")),
                    panelName = visible(row.optString("panel_name")),
                    isTest = row.optBoolean("is_test"),
                    boughtAt = row.optNullableLong("bought_at") ?: 0L,
                    reachable = row.optBoolean("reachable"),
                    status = usage.optString("status"),
                    dataLimit = (usage.optNullableDouble("data_limit") ?: 0.0).toLong(),
                    used = (usage.optNullableDouble("used") ?: 0.0).toLong(),
                    expire = usage.optNullableLong("expire") ?: 0L,
                    volumeGb = usage.optInt("volume_gb"),
                    timeDays = usage.optInt("time_days"),
                    subscription = row.optString("subscription"),
                    configCount = row.optInt("config_count")
                )
            }

    /** One owned service's configs and link, for importing into the app. */
    suspend fun marketServiceDelivery(shopId: Int, invoiceId: String, username: String): GhajarMarketOrderStatus {
        val payload = marketAction("service_detail", params = shopParam(shopId) +
            mapOf("invoice_id" to invoiceId, "username" to username)).payloadObject()
        return GhajarMarketOrderStatus(
            id = 0,
            shopId = shopId,
            status = "paid",
            rejectReason = "",
            amount = 0,
            username = payload.optString("username", username),
            configs = payload.optJSONArray("configs").orEmpty().let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
            },
            subscription = payload.optString("subscription")
        )
    }

    suspend fun marketMessages(shopId: Int): List<GhajarMarketMessage> =
        marketAction("messages", params = shopParam(shopId)).payloadObject()
            .optJSONArray("messages").orEmpty().objects().map { row ->
                GhajarMarketMessage(
                    id = row.optInt("id"),
                    kind = row.optString("kind"),
                    title = visible(row.optString("title")),
                    body = visible(row.optString("body")),
                    createdAt = row.optNullableLong("created_at") ?: 0L,
                    read = row.optBoolean("read")
                )
            }

    suspend fun marketMessagesRead(shopId: Int) {
        marketAction("messages_read", method = "POST", body = JSONObject().put("shop_id", shopId))
    }

    suspend fun marketWallet(shopId: Int): GhajarMarketWallet {
        val payload = marketAction("wallet", params = shopParam(shopId)).payloadObject()
        return GhajarMarketWallet(
            balance = (payload.optNullableDouble("balance") ?: 0.0).toLong(),
            history = payload.optJSONArray("history").orEmpty().objects().map { row ->
                GhajarMarketWalletEntry(
                    amount = (row.optNullableDouble("amount") ?: 0.0).toLong(),
                    kind = row.optString("kind"),
                    title = visible(row.optString("title")),
                    balanceAfter = (row.optNullableDouble("balance_after") ?: 0.0).toLong(),
                    createdAt = row.optNullableLong("created_at") ?: 0L
                )
            }
        )
    }

    suspend fun marketTransactions(shopId: Int): List<GhajarMarketTransaction> =
        marketAction("transactions", params = shopParam(shopId)).payloadObject()
            .optJSONArray("transactions").orEmpty().objects().map { row ->
                GhajarMarketTransaction(
                    id = row.optInt("id"),
                    kind = row.optString("kind"),
                    status = row.optString("status"),
                    amount = (row.optNullableDouble("amount") ?: 0.0).toLong(),
                    method = row.optString("method"),
                    productName = visible(row.optString("product_name")),
                    rejectReason = visible(row.optString("reject_reason")),
                    createdAt = row.optNullableLong("created_at") ?: 0L
                )
            }

    suspend fun marketTickets(shopId: Int): List<GhajarMarketTicket> =
        marketAction("tickets", params = shopParam(shopId)).payloadObject()
            .optJSONArray("tickets").orEmpty().objects().map { row ->
                GhajarMarketTicket(
                    id = row.optInt("id"),
                    subject = visible(row.optString("subject")),
                    status = row.optString("status"),
                    updatedAt = row.optNullableLong("updated_at") ?: 0L
                )
            }

    private fun ticketThreadFrom(payload: JSONObject) = GhajarMarketTicketThread(
        id = payload.optInt("id"),
        subject = visible(payload.optString("subject")),
        status = payload.optString("status"),
        messages = payload.optJSONArray("messages").orEmpty().objects().map { row ->
            GhajarMarketTicketMessage(
                fromSeller = row.optString("sender") == "seller",
                body = visible(row.optString("body")),
                createdAt = row.optNullableLong("created_at") ?: 0L
            )
        }
    )

    suspend fun marketTicketThread(ticketId: Int): GhajarMarketTicketThread =
        ticketThreadFrom(marketAction("ticket_thread",
            params = mapOf("ticket_id" to ticketId.toString())).payloadObject())

    suspend fun marketTicketReply(ticketId: Int, body: String): GhajarMarketTicketThread =
        ticketThreadFrom(marketAction("ticket_reply", method = "POST",
            body = JSONObject().put("ticket_id", ticketId).put("body", body)).payloadObject())

    suspend fun marketTicketClose(ticketId: Int): GhajarMarketTicketThread =
        ticketThreadFrom(marketAction("ticket_close", method = "POST",
            body = JSONObject().put("ticket_id", ticketId)).payloadObject())

    suspend fun marketTicketCreate(shopId: Int, subject: String, body: String): Int =
        marketAction("ticket_create", method = "POST", body = JSONObject()
            .put("shop_id", shopId).put("subject", subject).put("body", body))
            .payloadObject().optInt("id")

    /**
     * A shop's profile picture, or null.
     *
     * Kept on disk by shop and version, so the list costs one download per
     * logo change and nothing after that. Direct first and then through the
     * tunnel, like every other call to this server.
     */
    suspend fun marketLogo(shopId: Int, version: Int): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        if (shopId < 0 || version <= 0) return@withContext null
        val dir = java.io.File(appContext.cacheDir, "market-logos").apply { mkdirs() }
        val file = java.io.File(dir, "$shopId-$version.img")
        if (file.isFile && file.length() > 0) {
            BitmapFactory.decodeFile(file.path)?.let { return@withContext it }
        }
        val url = URL("${BrandConfig.MARKET_API_URL}?actions=shop_logo&shop_id=$shopId&v=$version")
        fun fetch(proxy: Proxy?): ByteArray? {
            val connection = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
            return try {
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.setRequestProperty("User-Agent", userAgent())
                connection.setRequestProperty(BrandConfig.CLIENT_HEADER, BrandConfig.CLIENT_ID)
                if (connection.responseCode != 200 ||
                    connection.contentType?.startsWith("image/") != true) null
                else connection.inputStream.use { it.readBytes() }.takeIf { it.size in 1..(2 * 1024 * 1024) }
            } finally {
                connection.disconnect()
            }
        }
        val bytes = runCatching { fetch(null) }.getOrNull()
            ?: runCatching { fetch(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", MixedPort.value))) }.getOrNull()
            ?: return@withContext null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        dir.listFiles()?.filter { it.name.startsWith("$shopId-") }?.forEach { it.delete() }
        runCatching { file.writeBytes(bytes) }
        bitmap
    }

    private fun marketShopFrom(row: JSONObject) = GhajarMarketShop(
        id = row.optInt("id"),
        name = visible(row.optString("name", "فروشگاه")),
        description = visible(row.optString("description")),
        telegramBot = row.optString("telegram_bot"),
        telegramChannel = row.optString("telegram_channel"),
        supportContact = row.optString("support_contact"),
        verified = row.optBoolean("verified"),
        status = row.optString("status"),
        canSell = row.optBoolean("can_sell", true),
        closedReason = visible(row.optString("closed_reason")),
        stars = row.optNullableDouble("stars") ?: 0.0,
        reviewCount = row.optInt("reviews"),
        satisfaction = row.optInt("satisfaction"),
        minPrice = (row.optNullableDouble("min_price") ?: 0.0).toLong(),
        maxPrice = (row.optNullableDouble("max_price") ?: 0.0).toLong(),
        productCount = row.optInt("product_count"),
        discountCount = row.optInt("discount_count"),
        sales30d = row.optInt("sales_30d"),
        tagline = visible(row.optString("tagline")),
        logoVersion = row.optInt("logo_version"),
        gbPrice = (row.optNullableDouble("gb_price") ?: 0.0).toLong(),
        dayPrice = (row.optNullableDouble("day_price") ?: 0.0).toLong(),
        panelMinPrice = (row.optNullableDouble("panel_min_price") ?: 0.0).toLong(),
        panelMaxPrice = (row.optNullableDouble("panel_max_price") ?: 0.0).toLong(),
        panelCount = row.optInt("panel_count"),
        testAvailable = row.optBoolean("test_available")
    )

    private fun marketMethodFrom(row: JSONObject) = GhajarMarketMethod(
        id = row.optString("id"),
        label = visible(row.optString("label")),
        needs = row.optString("needs"),
        note = visible(row.optString("note")),
        cardNumber = row.optString("card_number"),
        cardHolder = visible(row.optString("card_holder")),
        contact = row.optString("contact")
    )

    /**
     * Catalog/wallet endpoints the shop screen's plan list depends on. Logging
     * only the returned item count (never a row's contents, never the bearer
     * token) lets an exported log answer "why are the plans empty" without
     * guessing: 0 here means the server genuinely returned nothing for these
     * exact params (a real empty catalog, or a filter combination the panel
     * doesn't have); a non-zero count logged here but nothing shown in the UI
     * means the bug is client-side (parsing or state), not the network or the
     * server.
     */
    private val DIAGNOSTIC_ACTIONS = setOf(
        "countries", "categories", "time_ranges", "services", "custom_price", "invoices", "payment_methods"
    )

    private fun logResultShape(name: String, params: Map<String, String>, result: JSONObject) {
        val shape = result.payload()
        val count = when (shape) {
            is JSONArray -> shape.length().toString()
            is JSONObject -> shape.optJSONArray("items")?.length()?.toString() ?: "object"
            else -> "empty"
        }
        GhajarLog.d("Store", "action=$name params=$params -> $count")
    }

    /**
     * GhajarVPN's own store/bot-link requests never go through its own VPN
     * tunnel: GozarVpnService.applyPerApp() always excludes this app's own
     * traffic from the tunnel (otherwise the app's outbound connection to
     * its own proxy server would route back into itself). That's correct
     * and necessary - but it means that if the store's domain is filtered
     * on the device's raw connection while the tunnel is actively bypassing
     * that exact kind of filtering for every *other* app, the user sees
     * "internet is fine, VPN is connected" while the store still can't be
     * reached, because its own requests never benefit from the tunnel they
     * are sitting right next to.
     *
     * A real, already-running fix for that: whenever the active tunnel is
     * one of the Xray-core protocols (vless/vmess/trojan/ss/etc., not
     * OpenVPN/IKEv2 which use separate engines), it always exposes a plain
     * local SOCKS5 inbound at 127.0.0.1:MixedPort (the same inbound VPN
     * Share re-exposes on the LAN) whether or not VPN Share is on. A direct
     * request that fails with a network-level IOException (DNS failure,
     * TLS failure, timeout, connection refused - never an application-level
     * GhajarApiException from an actual HTTP response) is retried once
     * through that local proxy before giving up. If nothing is listening
     * there (OpenVPN/IKEv2 active, or the VPN is off), the retry fails fast
     * with its own connection-refused and the original error is what
     * surfaces - never a silently swallowed cause.
     */
    private fun requestJson(
        url: URL,
        method: String,
        bearer: String?,
        body: JSONObject?,
        allowPaymentRequired: Boolean = false,
        allowLinkGate: Boolean = false
    ): JSONObject {
        fun viaProxy() = performRequest(
            url, method, bearer, body, allowPaymentRequired, allowLinkGate,
            proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", MixedPort.value))
        )

        // Opening the store is several requests to the same host. When the
        // direct route is being blocked, each one used to spend its own full
        // connect timeout discovering that again before falling back, so the
        // shop took timeout x requests to appear. One failure is remembered for
        // a minute and the tunnel is tried first during it; any success there
        // is no slower than before, and the memo is dropped the moment a direct
        // request works again, so a route that comes back is picked up at once.
        if (System.currentTimeMillis() < directBlockedUntil) {
            try {
                return viaProxy()
            } catch (throughTunnel: IOException) {
                GhajarLog.w("Store", "tunnel-first attempt for ${url.host} failed " +
                    "(${throughTunnel.javaClass.simpleName}); trying direct again")
                directBlockedUntil = 0L
            }
        }

        return try {
            performRequest(url, method, bearer, body, allowPaymentRequired, allowLinkGate, proxy = null)
                .also { directBlockedUntil = 0L }
        } catch (direct: IOException) {
            GhajarLog.w("Store", "direct request to ${url.host} failed " +
                "(${direct.javaClass.simpleName}: ${direct.message}); retrying via local tunnel proxy")
            directBlockedUntil = System.currentTimeMillis() + DIRECT_BLOCK_MEMO_MS
            try {
                viaProxy()
            } catch (viaProxy: IOException) {
                GhajarLog.e("Store", "local-proxy retry for ${url.host} also failed: " +
                    "${viaProxy.javaClass.simpleName}: ${viaProxy.message}")
                throw direct
            }
        }
    }

    /**
     * When the direct route last failed at the network level, in wall-clock
     * millis. Zero means "no reason to doubt it". Plain volatile rather than a
     * lock: a stale read costs one redundant attempt, never correctness.
     */
    @Volatile
    private var directBlockedUntil: Long = 0L

    private fun performRequest(
        url: URL,
        method: String,
        bearer: String?,
        body: JSONObject?,
        allowPaymentRequired: Boolean,
        allowLinkGate: Boolean,
        proxy: Proxy?
    ): JSONObject {
        val connection = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
        connection.apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", userAgent())
            // Identifies this as the app rather than the mini app. The server
            // can be put into a mode that shows every other client "install the
            // app" and nothing else; this is what keeps the app itself working.
            setRequestProperty(BrandConfig.CLIENT_HEADER, BrandConfig.CLIENT_ID)
            bearer?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            readResponse(connection, allowPaymentRequired, allowLinkGate, bearer != null)
        } catch (t: Throwable) {
            // Only a request that ended badly gives up its socket. A clean one
            // is left to the keep-alive pool: loading the store is a handful of
            // calls to the same host, and disconnecting after each one made
            // every single one of them pay for a fresh TCP + TLS handshake.
            connection.disconnect()
            throw t
        }
    }

    private fun readResponse(connection: HttpURLConnection, allowPaymentRequired: Boolean = false,
        allowLinkGate: Boolean = false, authenticated: Boolean = false): JSONObject {
        val code = connection.responseCode
        val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val parsed = runCatching { JSONObject(raw) }.getOrNull()
        // A 404 is only "the install is out of date" when the *web server*
        // produced it - an unknown path, so an HTML error page. The API uses
        // 404 as an ordinary answer too ("service not found", "panel not
        // found"), and swallowing its message here told users to update a bot
        // that was perfectly up to date while hiding what actually went wrong.
        if (code == 404 && parsed?.has("msg") != true) {
            throw GhajarApiException(
                "مسیر ورود یا فروشگاه روی سرور موجود نیست (۴۰۴)؛ نصب ربات باید بروزرسانی شود.", code)
        }
        val envelope = parsed ?: throw GhajarApiException("پاسخ فروشگاه قابل خواندن نیست", code)
        val paymentRequired = envelope.optBoolean("requires_payment") ||
            envelope.optJSONObject("obj")?.optBoolean("requires_payment") == true
        if (allowPaymentRequired && paymentRequired) return envelope
        // An unauthenticated login request must not erase an existing account/session.
        if (GhajarLinkFlow.invalidatesAccount(authenticated, code)) account.clear()
        if (allowLinkGate && code in 200..299 && envelope.optString("link_status") == "linked" &&
            envelope.optString("gate") in setOf("force_join", "phone_required")) return envelope
        if (code !in 200..299 || !envelope.optBoolean("status", true)) {
            throw GhajarApiException(visible(envelope.optString("msg", "خطای فروشگاه ($code)")), code)
        }
        return envelope
    }

    private fun requireToken(): String = account.token().takeIf { it.isNotBlank() }
        ?: throw GhajarApiException("حساب قاجار وی پی ان هنوز متصل نشده است", 401)

    internal fun serviceFrom(row: JSONObject, fallbackUsername: String): GhajarServiceDetails {
        val outputs = row.optJSONArray("service_output").orEmpty().values().flatMap { value ->
            when (value) {
                is JSONObject -> when (val entry = value.opt("value")) {
                    is JSONArray -> entry.values().map { it.toString() }
                    null, JSONObject.NULL -> emptyList()
                    else -> listOf(entry.toString())
                }
                is JSONArray -> value.values().map { it.toString() }
                else -> listOf(value.toString())
            }
        } + row.optJSONArray("configs").orEmpty().values().map { it.toString() }
        return GhajarServiceDetails(
            username = row.optString("username", fallbackUsername),
            productName = visible(row.optString("product_name", row.optString("name_product", "سرویس قاجار"))),
            status = visible(row.optString("status", "active")),
            usedGb = row.optNullableDouble("used_traffic_gb"),
            totalGb = row.optNullableDouble("total_traffic_gb"),
            remainingGb = row.optNullableDouble("remaining_traffic_gb"),
            expiresAt = visible(row.optString("expiration_time")),
            subscriptionUrl = row.optString("subscription_url").takeIf(::isHttps),
            outputs = outputs.filter { it.isNotBlank() }.distinct()
        )
    }

    private fun noticeFrom(row: JSONObject, source: String, personal: Boolean): GhajarNotice? {
        val message = visible(
            row.optString("body").ifBlank { row.optString("message").ifBlank { row.optString("text") } }
        ).trim()
        if (message.isBlank()) return null
        val type = row.optString("type")
        val metaObject = row.optJSONObject("meta")
        val meta = metaObject?.let {
            GhajarNoticeMeta(
                dataLimitBytes = it.optNullableLong("data_limit"),
                usedBytes = it.optNullableLong("used_traffic"),
                remainingBytes = it.optNullableLong("remaining_bytes"),
                daysRemaining = it.optNullableInt("days_remaining"),
                expireTimestamp = it.optNullableLong("expire_ts")
            )
        }
        val id = "$source:${row.optString("id", row.optString("created_at", sha256(message).take(12)))}"
        return GhajarNotice(
            id = id,
            title = visible(row.optString("title").ifBlank { if (personal) "پیام سرویس قاجار" else "اعلان قاجار وی پی ان" }),
            message = message,
            important = source == "floating" || row.optBoolean("important") || row.optInt("priority") > 0 || type in setOf("volume", "time"),
            serviceAlert = personal || type in setOf("volume", "time", "service"),
            serviceUsername = row.optString("service").takeIf { it.isNotBlank() },
            meta = meta
        )
    }

    private fun userAgent(): String = "Ghajarvpn-Android/${BuildConfig.VERSION_NAME}"
    private fun visible(value: String): String = BrandConfig.sanitizePublicText(value)
    private fun isHttps(value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun JSONObject.payload(): Any? = when {
        has("obj") && !isNull("obj") -> opt("obj")
        has("data") && !isNull("data") -> opt("data")
        else -> this
    }

    private fun JSONObject.payloadObject(): JSONObject = when (val value = payload()) {
        is JSONObject -> value
        else -> JSONObject()
    }

    private fun JSONObject.payloadArray(): JSONArray = when (val value = payload()) {
        is JSONArray -> value
        is JSONObject -> value.optJSONArray("items") ?: JSONArray()
        else -> JSONArray()
    }

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull(::optJSONObject)
    private fun JSONArray.values(): List<Any> = (0 until length()).map { opt(it) }.filterNot { it == null || it == JSONObject.NULL }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        opt(key).takeUnless { it == null || it == JSONObject.NULL || it.toString().isBlank() }
            ?.toString()?.toDoubleOrNull()

    private fun JSONObject.optNullableLong(key: String): Long? =
        opt(key).takeUnless { it == null || it == JSONObject.NULL || it.toString().isBlank() }
            ?.toString()?.toDoubleOrNull()?.toLong()

    private fun JSONObject.optNullableInt(key: String): Int? =
        opt(key).takeUnless { it == null || it == JSONObject.NULL || it.toString().isBlank() }
            ?.toString()?.toDoubleOrNull()?.toInt()

    private fun DataOutputStream.writeUtf8(value: String) = write(value.toByteArray(Charsets.UTF_8))

    companion object {
        private val deliveryMutex = Mutex()
        /** How long one direct-route failure steers later requests to the
         *  tunnel first. Short enough that a route which comes back is used
         *  again quickly, long enough to cover one page load. */
        private const val DIRECT_BLOCK_MEMO_MS = 60_000L
        private const val CONNECT_TIMEOUT = 12_000
        private const val READ_TIMEOUT = 20_000
        private const val UPLOAD_TIMEOUT = 60_000
        private const val MAX_RECEIPT_BYTES = 8L * 1024 * 1024

        /**
         * The long edge a marketplace receipt is shrunk to before it is sent.
         *
         * 1600px keeps a bank slip's reference number readable while turning a
         * 12MP photo into a couple of hundred kilobytes. The seller has to read
         * it, not enlarge it.
         */
        private const val RECEIPT_MAX_EDGE = 1600
        private const val BYTES_PER_GB = 1024.0 * 1024 * 1024
    }
}

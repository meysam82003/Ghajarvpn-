package net.gozar.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GhajarProduct(
    val id: String,
    val name: String,
    val price: Long,
    val trafficGb: Double?,
    val days: Int?,
    val description: String,
    val checkoutUrl: String?
)

data class GhajarOwnedService(
    val username: String,
    val productName: String,
    val status: String,
    val usedGb: Double,
    val totalGb: Double,
    val remainingGb: Double,
    val expiresAt: String,
    val subscription: String?
)

data class GhajarStoreSnapshot(
    val products: List<GhajarProduct>,
    val services: List<GhajarOwnedService>,
    val announcement: String?
)

data class GhajarNotice(
    val id: String,
    val title: String,
    val message: String,
    val important: Boolean,
    val serviceAlert: Boolean
)

/** Native, tolerant client for the existing Telegram Mini App backend. */
object GhajarStoreApi {
    private const val TIMEOUT = 15_000

    suspend fun loadSnapshot(token: String? = null): GhajarStoreSnapshot = withContext(Dispatchers.IO) {
        val products = unwrapArray(request("services", token = token))
            .mapNotNull(::productFrom)
        val serviceRows = unwrapArray(request("user_services_list", token = token))
            .mapNotNull(::ownedServiceFrom)
        val announcement = unwrapArray(request("announcements_list", token = token))
            .firstOrNull()?.let(::jsonObjectOrNull)
            ?.let { BrandConfig.sanitizePublicText(it.optString("message", it.optString("text"))) }
            ?.takeIf { it.isNotBlank() }
        GhajarStoreSnapshot(products, serviceRows, announcement)
    }

    suspend fun requestTrial(token: String?): JSONObject? = withContext(Dispatchers.IO) {
        jsonObjectOrNull(request("trial_create", method = "POST", token = token, body = JSONObject()))
    }

    suspend fun loadNotices(token: String?): List<GhajarNotice> = withContext(Dispatchers.IO) {
        val sources = listOf(
            "announcements_list" to false,
            "user_notifications_list" to true,
            "floating_broadcast_active" to false
        )
        sources.flatMap { (action, personal) ->
            runCatching { unwrapArray(request(action, token = token)) }.getOrDefault(emptyList())
                .mapNotNull { value ->
                    val o = jsonObjectOrNull(value) ?: return@mapNotNull null
                    val message = BrandConfig.sanitizePublicText(
                        o.optString("message").ifBlank { o.optString("text").ifBlank { o.optString("body") } }
                    ).trim()
                    if (message.isBlank()) return@mapNotNull null
                    GhajarNotice(
                        id = "$action:${o.optString("id", message.hashCode().toString())}",
                        title = BrandConfig.sanitizePublicText(
                            o.optString("title").ifBlank { if (personal) "پیام سرویس قاجار" else "اعلان قاجار وی پی ان" }
                        ),
                        message = message,
                        important = action == "floating_broadcast_active" || o.optBoolean("important") || o.optInt("priority") > 0,
                        serviceAlert = personal || o.optString("type").contains("service", true)
                    )
                }
        }.distinctBy { it.id }
    }

    suspend fun createCheckout(productId: String, token: String?): String? = withContext(Dispatchers.IO) {
        val response = jsonObjectOrNull(
            request(
                action = "purchase_create",
                method = "POST",
                token = token,
                body = JSONObject().put("service_id", productId)
            )
        ) ?: return@withContext null
        val data = response.optJSONObject("data") ?: response
        data.optString("checkout_url")
            .ifBlank { data.optString("payment_url") }
            .ifBlank { data.optString("url") }
            .takeIf { it.startsWith("https://") }
    }

    fun importDeliveredService(store: ConfigStore, service: GhajarOwnedService): Int {
        val raw = service.subscription?.trim().orEmpty()
        if (raw.isEmpty()) return 0
        if (raw.startsWith("https://")) {
            val sub = Subscription(
                name = BrandConfig.sanitizePublicText(service.productName).ifBlank { "سرویس قاجار" },
                url = raw,
                used = (service.usedGb * 1024 * 1024 * 1024).toLong(),
                total = (service.totalGb * 1024 * 1024 * 1024).toLong(),
                lastUpdated = System.currentTimeMillis()
            )
            store.upsertSubscription(sub, emptyList())
            return 1
        }
        val parsed = ConfigParser.parseBundle(raw)
        if (parsed.isEmpty()) return 0
        store.addToLocalSub("سرویس قاجار", parsed)
        return parsed.size
    }

    /** Installs only newly delivered subscriptions after a purchase/trial refresh. */
    fun importNewDeliveredServices(
        context: Context,
        store: ConfigStore,
        services: List<GhajarOwnedService>
    ): Int {
        val prefs = context.getSharedPreferences("ghajar_deliveries", Context.MODE_PRIVATE)
        val installed = prefs.getStringSet("installed", emptySet()).orEmpty().toMutableSet()
        var imported = 0
        services.forEach { service ->
            val payload = service.subscription?.trim().orEmpty()
            if (payload.isEmpty()) return@forEach
            val fingerprint = "${service.username}:${payload.hashCode()}"
            if (fingerprint in installed) return@forEach
            val count = importDeliveredService(store, service)
            if (count > 0) {
                imported += count
                installed += fingerprint
            }
        }
        if (installed.size > 200) {
            val keep = installed.toList().takeLast(160).toSet()
            prefs.edit().putStringSet("installed", keep).apply()
        } else {
            prefs.edit().putStringSet("installed", installed).apply()
        }
        return imported
    }

    private fun request(
        action: String,
        method: String = "GET",
        token: String? = null,
        body: JSONObject? = null
    ): String {
        var lastError: Throwable? = null
        val encoded = URLEncoder.encode(action, "UTF-8")
        val candidates = listOf(
            "${BrandConfig.STORE_BASE_URL}api.php?action=$encoded",
            "${BrandConfig.STORE_BASE_URL}api/?action=$encoded",
            "${BrandConfig.STORE_BASE_URL}?action=$encoded"
        )
        for (candidate in candidates) {
            try {
                val conn = URL(candidate).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = TIMEOUT
                    conn.readTimeout = TIMEOUT
                    conn.requestMethod = method
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("User-Agent", "Ghajarvpn-Android/3.0")
                    token?.takeIf { it.isNotBlank() }?.let {
                        conn.setRequestProperty("Authorization", "Bearer $it")
                        conn.setRequestProperty("X-Telegram-Init-Data", it)
                    }
                    if (method == "POST") {
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                            it.write((body ?: JSONObject()).toString())
                        }
                    }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (code in 200..299 && text.isNotBlank()) return text
                    lastError = IllegalStateException("HTTP $code")
                } finally {
                    conn.disconnect()
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Store unavailable")
    }

    private fun unwrapArray(raw: String): List<Any> {
        val trimmed = raw.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("data")
                    ?: root.optJSONObject("data")?.optJSONArray("items")
                    ?: root.optJSONArray("items")
                    ?: JSONArray()
            }
            else -> JSONArray()
        }
        return (0 until array.length()).map { array.get(it) }
    }

    private fun productFrom(value: Any): GhajarProduct? {
        val o = jsonObjectOrNull(value) ?: return null
        val id = o.optString("id").ifBlank { o.optString("service_id") }
        if (id.isBlank()) return null
        return GhajarProduct(
            id = id,
            name = BrandConfig.sanitizePublicText(o.optString("name", o.optString("title", "سرویس قاجار"))),
            price = o.optLong("price", o.optLong("amount", 0)),
            trafficGb = o.optDouble("traffic_gb").takeUnless { it.isNaN() || it <= 0 },
            days = o.optInt("time_days", o.optInt("days", 0)).takeIf { it > 0 },
            description = BrandConfig.sanitizePublicText(o.optString("description")),
            checkoutUrl = o.optString("checkout_url").takeIf { it.startsWith("https://") }
        )
    }

    private fun ownedServiceFrom(value: Any): GhajarOwnedService? {
        val o = jsonObjectOrNull(value) ?: return null
        val username = o.optString("username")
        if (username.isBlank()) return null
        return GhajarOwnedService(
            username = username,
            productName = BrandConfig.sanitizePublicText(o.optString("product_name", "سرویس قاجار")),
            status = BrandConfig.sanitizePublicText(o.optString("status", "unknown")),
            usedGb = o.optDouble("used_traffic_gb", 0.0),
            totalGb = o.optDouble("total_traffic_gb", 0.0),
            remainingGb = o.optDouble("remaining_traffic_gb", 0.0),
            expiresAt = o.optString("expiration_time"),
            subscription = o.optString("service_output").takeIf { it.isNotBlank() }
        )
    }

    private fun jsonObjectOrNull(value: Any?): JSONObject? = when (value) {
        is JSONObject -> value
        is String -> runCatching { JSONObject(value) }.getOrNull()
        else -> null
    }
}

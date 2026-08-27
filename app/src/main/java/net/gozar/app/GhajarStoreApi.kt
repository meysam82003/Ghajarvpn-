package net.gozar.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

data class GhajarLinkSession(
    val code: String,
    val sessionToken: String,
    val botUsername: String,
    val expiresInSeconds: Int
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
    val location: String
)

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
    val meta: GhajarNoticeMeta? = null
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
    val message: String
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

data class GhajarTrialPanel(val code: String, val name: String)
data class GhajarTrialOptions(val panels: List<GhajarTrialPanel>, val remaining: Int?, val canRequest: Boolean)

class GhajarApiException(message: String, val httpCode: Int = 0) : IllegalStateException(message)

/** Native client matched to the API shipped in Ghajar_vpnbot_-3-1.zip. */
class GhajarStoreApi(context: Context) {
    private val appContext = context.applicationContext
    private val account = GhajarAccountStore(appContext)

    val isLinked: Boolean get() = account.token().isNotBlank()

    suspend fun beginLink(): GhajarLinkSession = withContext(Dispatchers.IO) {
        val root = requestJson(
            URL("${BrandConfig.WEBLINK_API_URL}?action=generate"),
            method = "POST",
            bearer = null,
            body = null
        )
        val code = root.optString("code")
        val session = root.optString("session_token")
        if (code.isBlank() || session.isBlank()) throw GhajarApiException("سرور کد اتصال صادر نکرد")
        GhajarLinkSession(
            code = code,
            sessionToken = session,
            botUsername = root.optString("bot_username", "Ghajar_vpnbot"),
            expiresInSeconds = root.optInt("expires_in", 300)
        )
    }

    suspend fun pollLink(sessionToken: String): Boolean = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(sessionToken, Charsets.UTF_8.name())
        val root = requestJson(
            URL("${BrandConfig.WEBLINK_API_URL}?action=status&session_token=$encoded"),
            method = "GET",
            bearer = null,
            body = null
        )
        if (!root.optString("link_status").equals("linked", true)) return@withContext false
        val issued = root.optString("token")
        if (issued.isBlank()) return@withContext false
        if (!account.saveToken(issued)) throw GhajarApiException("ذخیرهٔ امن حساب در گوشی ناموفق بود")
        true
    }

    fun unlink() = account.clear()

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
        timeDays?.let { params["time_range_day"] = it.toString() }
        return action("services", params = params).payloadArray().objects().mapNotNull { row ->
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
                GhajarOwnedService(
                    username = username,
                    productName = visible(row.optString("name_product", row.optString("product_name", "سرویس قاجار"))),
                    status = visible(row.optString("status", row.optString("Status", "unknown"))),
                    location = visible(row.optString("Service_location"))
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

    suspend fun notices(): List<GhajarNotice> {
        val general = runCatching { action("announcements_list").payloadArray().objects() }.getOrDefault(emptyList())
        val personalPayload = runCatching { action("user_notifications_list").payloadObject() }.getOrNull()
        val personal = personalPayload?.optJSONArray("items").orEmpty().objects()
        val floatingPayload = runCatching { action("floating_broadcast_active").payloadObject() }.getOrNull()
        val floating = floatingPayload?.optJSONObject("item")?.let(::listOf).orEmpty()
        return buildList {
            floating.mapNotNullTo(this) { noticeFrom(it, source = "floating", personal = false) }
            personal.mapNotNullTo(this) { noticeFrom(it, source = "personal", personal = true) }
            general.mapNotNullTo(this) { noticeFrom(it, source = "general", personal = false) }
        }.distinctBy { it.id }
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
            message = visible(payload.optString("message"))
        )
    }

    suspend fun paymentStatus(orderId: String): JSONObject =
        action("payment_status", params = mapOf("order_id" to orderId)).payloadObject()

    suspend fun uploadReceipt(orderId: String, photo: Uri): String = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val size = resolver.openAssetFileDescriptor(photo, "r")?.use { it.length } ?: -1L
        if (size > MAX_RECEIPT_BYTES) throw GhajarApiException("حجم رسید نباید بیشتر از ۸ مگابایت باشد")
        val fileName = resolver.query(photo, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "receipt.jpg"
        val mime = resolver.getType(photo)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
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
                resolver.openInputStream(photo)?.use { input -> input.copyTo(output, 64 * 1024) }
                    ?: throw GhajarApiException("فایل رسید قابل خواندن نیست")
                output.writeUtf8("\r\n--$boundary--\r\n")
            }
            val envelope = readResponse(connection)
            visible(envelope.payloadObject().optString("message", "رسید ارسال شد"))
        } finally {
            connection.disconnect()
        }
    }

    suspend fun trialOptions(): GhajarTrialOptions {
        val payload = action("trial_panels").payloadObject()
        return GhajarTrialOptions(
            panels = payload.optJSONArray("panels").orEmpty().objects().mapNotNull { row ->
                val code = row.optString("code")
                if (code.isBlank()) null else GhajarTrialPanel(code, visible(row.optString("name")))
            },
            remaining = payload.opt("remaining").takeUnless { it == null || it == JSONObject.NULL }?.toString()?.toIntOrNull(),
            canRequest = payload.optBoolean("can_request")
        )
    }

    suspend fun createTrial(panelCode: String, username: String?): GhajarServiceDetails {
        val body = JSONObject().put("code_panel", panelCode)
        username?.takeIf { it.isNotBlank() }?.let { body.put("custom_username", it) }
        val payload = action("trial_create", method = "POST", body = body).payloadObject()
        val issuedUsername = payload.optString("username")
        return serviceFrom(payload, issuedUsername)
    }

    fun importServiceOnce(store: ConfigStore, service: GhajarServiceDetails): Int {
        val joined = service.outputs.filter { it.isNotBlank() }.joinToString("\n")
        val payload = service.subscriptionUrl?.takeIf { it.isNotBlank() } ?: joined
        if (payload.isBlank()) return 0
        val fingerprint = sha256("${service.username}:$payload")
        val prefs = appContext.getSharedPreferences("ghajarvpn_deliveries_v2", Context.MODE_PRIVATE)
        val installed = prefs.getStringSet("installed", emptySet()).orEmpty().toMutableSet()
        if (fingerprint in installed) return 0

        val imported = if (service.subscriptionUrl?.startsWith("https://") == true) {
            store.upsertSubscription(
                Subscription(
                    name = service.productName.ifBlank { "سرویس قاجار" },
                    url = service.subscriptionUrl,
                    used = ((service.usedGb ?: 0.0) * BYTES_PER_GB).toLong(),
                    total = ((service.totalGb ?: 0.0) * BYTES_PER_GB).toLong(),
                    lastUpdated = System.currentTimeMillis()
                ),
                emptyList()
            )
            1
        } else {
            val configs = ConfigParser.parseBundle(joined)
            if (configs.isNotEmpty()) store.addToLocalSub(service.productName.ifBlank { "سرویس قاجار" }, configs)
            configs.size
        }
        if (imported > 0) {
            installed += fingerprint
            prefs.edit().putStringSet("installed", installed.toList().takeLast(200).toSet()).apply()
        }
        return imported
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
        requestJson(
            url = URL("${BrandConfig.MINIAPP_API_URL}?$query"),
            method = method,
            bearer = requireToken(),
            body = payload,
            allowPaymentRequired = allowPaymentRequired
        )
    }

    private fun requestJson(
        url: URL,
        method: String,
        bearer: String?,
        body: JSONObject?,
        allowPaymentRequired: Boolean = false
    ): JSONObject {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", userAgent())
            bearer?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            readResponse(connection, allowPaymentRequired)
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponse(connection: HttpURLConnection, allowPaymentRequired: Boolean = false): JSONObject {
        val code = connection.responseCode
        val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val envelope = runCatching { JSONObject(raw) }.getOrElse {
            throw GhajarApiException("پاسخ فروشگاه قابل خواندن نیست", code)
        }
        val paymentRequired = envelope.optBoolean("requires_payment") ||
            envelope.optJSONObject("obj")?.optBoolean("requires_payment") == true
        if (allowPaymentRequired && paymentRequired) return envelope
        if (code == 401 || code == 403) account.clear()
        if (code !in 200..299 || !envelope.optBoolean("status", true)) {
            throw GhajarApiException(visible(envelope.optString("msg", "خطای فروشگاه ($code)")), code)
        }
        return envelope
    }

    private fun requireToken(): String = account.token().takeIf { it.isNotBlank() }
        ?: throw GhajarApiException("حساب قاجار وی پی ان هنوز متصل نشده است", 401)

    private fun serviceFrom(row: JSONObject, fallbackUsername: String): GhajarServiceDetails {
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
        private const val CONNECT_TIMEOUT = 12_000
        private const val READ_TIMEOUT = 20_000
        private const val UPLOAD_TIMEOUT = 60_000
        private const val MAX_RECEIPT_BYTES = 8L * 1024 * 1024
        private const val BYTES_PER_GB = 1024.0 * 1024 * 1024
    }
}

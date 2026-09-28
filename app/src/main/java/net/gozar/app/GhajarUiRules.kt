package net.gozar.app

/** Pure contracts shared by native UI and regression tests. */
internal object GhajarUiRules {
    private const val MAX_LINK_LIFETIME_MS = 15 * 60 * 1000L

    fun linkExpiresAt(nowMillis: Long, ttlSeconds: Int): Long =
        nowMillis + ttlSeconds.coerceIn(0, (MAX_LINK_LIFETIME_MS / 1000).toInt()) * 1000L

    /**
     * Whether a freshly issued pairing code is one this app will keep.
     *
     * Both patterns were wrong against the bot that actually issues them, and
     * either one alone made signing in impossible - the app refused the code
     * before it ever reached storage, and said "ذخیرهٔ امن کد اتصال ناموفق بود",
     * which pointed at the phone's keystore instead of at this line:
     *
     *  - The code is six characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`
     *    (`FaoximaAppLink::ALPHABET` - no 0/1/I/O, so it can be read aloud),
     *    not six digits. `K7P2QX` is a valid code and `[0-9]{6}` rejected it.
     *  - The session token is `bin2hex(random_bytes(32))`, so 64 hex
     *    characters, stored in a `CHAR(64)` column. The rule demanded exactly
     *    48 and rejected every real one.
     *
     * Now bounded rather than exact. The app's job here is to refuse something
     * malformed or absurdly long before it goes into storage and into a bot
     * command; the precise length and alphabet are the server's business, and
     * pinning them here is how one change on that side locked everybody out.
     */
    fun validPendingLink(code: String, token: String, expiresAtMillis: Long, nowMillis: Long): Boolean =
        code.matches(LINK_CODE) && token.matches(Regex("[a-fA-F0-9]{32,128}")) &&
            expiresAtMillis > nowMillis && expiresAtMillis - nowMillis <= MAX_LINK_LIFETIME_MS

    fun isLegacyAutomaticFreeFeed(name: String, url: String): Boolean =
        name in setOf("Free Configs", "کانفیگ‌های رایگان") &&
            url.trimEnd('/').equals("https://t.me/s/ConfigsHUB", ignoreCase = true)

    fun asciiDigits(value: String): String = value.mapNotNull { char ->
        char.digitToIntOrNull()?.let { ('0'.code + it).toChar() }
    }.joinToString("")

    /** Display-only branding: never mutates the underlying imported config name. */
    fun brandedConfigName(name: String): String {
        val clean = name.trim().ifBlank { "VPN" }
        return if (clean.startsWith("Ghajarvpn", ignoreCase = true)) clean else "Ghajarvpn • $clean"
    }

    /** Subscription headings always start with Ghajarvpn and prefer the server-reported total quota. */
    fun brandedSubscriptionTitle(totalBytes: Long, fallbackName: String): String {
        if (totalBytes <= 0L) return brandedConfigName(fallbackName)
        val gib = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val nearest = kotlin.math.round(gib)
        val quota = if (kotlin.math.abs(gib - nearest) < 0.005) {
            nearest.toLong().toString()
        } else {
            java.lang.String.format(java.util.Locale.US, "%.1f", gib).trimEnd('0').trimEnd('.')
        }
        return "Ghajarvpn $quota GB"
    }

    private val isoCountries by lazy {
        java.util.Locale.getISOCountries().map { it.lowercase(java.util.Locale.US) }.toSet()
    }

    /** OVPN titles stay branded while exposing the country encoded by common server hostnames. */
    fun ovpnDisplayName(host: String): String {
        val code = host.lowercase(java.util.Locale.US)
            .split(Regex("[^a-z]+"))
            .firstOrNull { it.length == 2 && it in isoCountries }
        val flag = code?.uppercase(java.util.Locale.US)?.map { letter ->
            String(Character.toChars(0x1F1E6 + (letter.code - 'A'.code)))
        }?.joinToString("") ?: "🌐"
        return "Ghajarvpn $flag"
    }

    private fun botUsername(username: String?): String = username?.trim()?.removePrefix("@")
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_]{5,32}")) } ?: "Ghajar_vpnbot"

    /** OVPN engine states the UI may only show after a real management handshake. */
    const val OVPN_MANAGEMENT_CONNECTED_STATE = "CONNECTED"

    /** Pure mapping of raw engine messages to the safe Persian text shown in the UI. */
    fun ovpnEngineMessage(raw: String): String {
        val clean = BrandConfig.sanitizePublicText(raw).replace(Regex("\\s+"), " ").trim()
        return when {
            clean.contains("Unable to start service Intent", ignoreCase = true) ->
                "موتور OpenVPN اندروید اجرا نشد؛ سرویس داخلی قاجار در دسترس نیست"
            clean.contains("AUTH_FAILED", ignoreCase = true) -> "نام کاربری یا رمز OpenVPN رد شد"
            else -> ovpnExitMessage(clean)
        }
    }

    private fun ovpnExitMessage(clean: String): String {
        val exit = Regex("NOPROCESS.*?exit code (\\d+)", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)
        return when {
            exit != null -> when (exit) {
                "1", "6" -> "پردازش OpenVPN با خطای پیکربندی بسته شد (کد $exit)"
                else -> "پردازش OpenVPN بسته شد (کد $exit)"
            }
            clean.contains("OPTIONS ERROR", ignoreCase = true) ->
                "پیکربندی فایل OVPN پذیرفته نشد؛ گزینه‌ای در آن پشتیبانی نمی‌شود"
            clean.contains("TLS Error", ignoreCase = true) || clean.contains("TLS handshake failed", ignoreCase = true) ->
                "دست‌دهی امن با سرور برقرار نشد؛ آدرس یا فیلتر را بررسی کن"
            clean.contains("Connection refused", ignoreCase = true) ->
                "سرور اتصال را رد کرد؛ پورت یا آدرس را بررسی کن"
            clean.length > 180 -> clean.take(177) + "…"
            else -> clean
        }
    }

    /** True when the engine can answer a real connection test with saved credentials. */
    fun ovpnNeedsSavedCredentials(username: String?, password: String?): Boolean =
        username.isNullOrBlank() || password.isNullOrBlank()

    /**
     * The one shape a pairing code may have on its way into a URL.
     *
     * Same correction as [validPendingLink], and it had the same consequence
     * in a quieter way: a code with a letter in it produced no deep link at
     * all, so "open the bot" did nothing and there was no error to read. A
     * deep-link payload must stay strictly alphanumeric - that is what keeps
     * it out of Telegram's start-parameter grammar - but it is not digits.
     */
    private val LINK_CODE = Regex("[A-Za-z0-9]{4,12}")

    /**
     * The start-parameter prefix the bot actually matches.
     *
     * `applink_`, from `rx_app_link_try_claim`'s
     * `/^\/start\s+applink[_-]([A-Za-z0-9]{4,16})$/`. This used to send
     * `link_`, which the bot ignores - so the deep link opened the bot,
     * handed it a payload it had no pattern for, and the code was never
     * claimed. Deliberately matched on a prefix on that side, so it has to be
     * exactly this word here.
     */
    private const val LINK_PAYLOAD_PREFIX = "applink_"

    fun botLink(username: String?, code: String?): String {
        val bot = botUsername(username)
        val payload = code?.takeIf { it.matches(LINK_CODE) }
            ?.let { "?start=$LINK_PAYLOAD_PREFIX$it" }.orEmpty()
        return "https://t.me/$bot$payload"
    }

    /** Login must never silently degrade into opening a bot without its code. */
    fun botLoginUrls(username: String?, code: String?): List<String> {
        if (code == null || !code.matches(LINK_CODE)) return emptyList()
        val bot = botUsername(username)
        return listOf(
            "tg://resolve?domain=$bot&start=$LINK_PAYLOAD_PREFIX$code",
            botLink(bot, code)
        )
    }

    fun launchBotLogin(username: String?, code: String?, launch: (String) -> Boolean): Boolean =
        botLoginUrls(username, code).any(launch)

    /** A claimed one-time code must not be claimed again while completing bot gates. */
    fun botVerificationUrls(username: String?): List<String> {
        val bot = botUsername(username)
        return listOf("tg://resolve?domain=$bot&start=start", "https://t.me/$bot?start=start")
    }
}

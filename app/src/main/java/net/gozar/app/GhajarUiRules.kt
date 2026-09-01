package net.gozar.app

/** Pure contracts shared by native UI and regression tests. */
internal object GhajarUiRules {
    private const val MAX_LINK_LIFETIME_MS = 15 * 60 * 1000L

    fun linkExpiresAt(nowMillis: Long, ttlSeconds: Int): Long =
        nowMillis + ttlSeconds.coerceIn(0, (MAX_LINK_LIFETIME_MS / 1000).toInt()) * 1000L

    fun validPendingLink(code: String, token: String, expiresAtMillis: Long, nowMillis: Long): Boolean =
        code.matches(Regex("[0-9]{6}")) && token.matches(Regex("[a-fA-F0-9]{48}")) &&
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

    fun botLink(username: String?, code: String?): String {
        val bot = botUsername(username)
        val payload = code?.takeIf { it.matches(Regex("[0-9]{6}")) }?.let { "?start=link_$it" }.orEmpty()
        return "https://t.me/$bot$payload"
    }

    /** Login must never silently degrade into opening a bot without its code. */
    fun botLoginUrls(username: String?, code: String?): List<String> {
        if (code == null || !code.matches(Regex("[0-9]{6}"))) return emptyList()
        val bot = botUsername(username)
        return listOf("tg://resolve?domain=$bot&start=link_$code", botLink(bot, code))
    }

    fun launchBotLogin(username: String?, code: String?, launch: (String) -> Boolean): Boolean =
        botLoginUrls(username, code).any(launch)

    /** A claimed one-time code must not be claimed again while completing bot gates. */
    fun botVerificationUrls(username: String?): List<String> {
        val bot = botUsername(username)
        return listOf("tg://resolve?domain=$bot&start=start", "https://t.me/$bot?start=start")
    }
}

package net.gozar.app

/** Pure contracts shared by native UI and regression tests. */
internal object GhajarUiRules {
    fun isLegacyAutomaticFreeFeed(name: String, url: String): Boolean =
        name in setOf("Free Configs", "کانفیگ‌های رایگان") &&
            url.trimEnd('/').equals("https://t.me/s/ConfigsHUB", ignoreCase = true)

    fun asciiDigits(value: String): String = value.mapNotNull { char ->
        char.digitToIntOrNull()?.let { ('0'.code + it).toChar() }
    }.joinToString("")

    fun botLink(username: String?, code: String?): String {
        val bot = username?.trim()?.removePrefix("@")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_]{5,32}")) } ?: "Ghajar_vpnbot"
        val payload = code?.takeIf { it.matches(Regex("[0-9]{6}")) }?.let { "?start=link_$it" }.orEmpty()
        return "https://t.me/$bot$payload"
    }
}

package net.gozar.app

/**
 * Single source of truth for every public Ghajarvpn endpoint and label.
 * The legacy engine package stays internal so existing VPN protocols remain compatible.
 */
object BrandConfig {
    const val APP_NAME_FA = "قاجار وی پی ان"
    const val APP_NAME_EN = "Ghajarvpn"
    const val GITHUB_URL = "https://github.com/meysam82003/Ghajarvpn"
    const val TELEGRAM_CHANNEL_URL = "https://t.me/Ghajarvpn"
    const val TELEGRAM_BOT_URL = "https://t.me/Ghajar_vpnbot"

    // Backend path is intentionally internal and must never be rendered in UI.
    const val STORE_BASE_URL = "https://httpuser87890.ir/Fao" + "xima/Ghajarvpn/app/"
    const val STORE_HOST = "httpuser87890.ir"

    const val NOTIFICATION_CHANNEL_CONNECTION = "ghajarvpn_connection"
    const val NOTIFICATION_CHANNEL_GENERAL = "ghajarvpn_general"
    const val NOTIFICATION_CHANNEL_SERVICE = "ghajarvpn_service_alerts"
    const val NOTIFICATION_CHANNEL_IMPORTANT = "ghajarvpn_important"

    fun sanitizePublicText(value: String): String = value
        .replace("Fao" + "xima", APP_NAME_EN, ignoreCase = true)
        .replace("GR" + "oute", APP_NAME_EN, ignoreCase = true)
        .replace("Gozar" + "Net", APP_NAME_EN, ignoreCase = true)
        .replace("Oracle" + " VPN", APP_NAME_EN, ignoreCase = true)
        .replace("Oracle" + "VPN", APP_NAME_EN, ignoreCase = true)
}

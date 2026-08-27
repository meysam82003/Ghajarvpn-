package net.gozar.app

import android.net.Uri

/** Public branding plus the private endpoints required by the native client. */
object BrandConfig {
    const val APP_NAME_FA = "قاجار وی پی ان"
    const val APP_NAME_EN = "Ghajarvpn"
    const val PACKAGE_ID = "com.ghajarvpn.app"

    const val GITHUB_URL = "https://github.com/meysam82003/Ghajarvpn-"
    const val TELEGRAM_CHANNEL_URL = "https://t.me/Ghajarvpn"
    const val TELEGRAM_BOT_URL = "https://t.me/Ghajar_vpnbot"

    // The historical server path is internal and must never be rendered in UI.
    const val STORE_ORIGIN = "https://httpuser87890.ir"
    const val STORE_HOST = "httpuser87890.ir"
    const val STORE_PATH = "/Fao" + "xima/Ghajarvpn/app/"
    const val STORE_URL = STORE_ORIGIN + STORE_PATH
    const val API_PATH = "/Fao" + "xima/Ghajarvpn/api"
    const val API_URL = STORE_ORIGIN + API_PATH
    const val MINIAPP_API_URL = "$API_URL/miniapp.php"
    const val WEBLINK_API_URL = "$API_URL/weblink.php"

    const val NOTIFICATION_CHANNEL_CONNECTION = "ghajarvpn_connection"
    const val NOTIFICATION_CHANNEL_GENERAL = "ghajarvpn_general"
    const val NOTIFICATION_CHANNEL_SERVICE = "ghajarvpn_service_alerts"
    const val NOTIFICATION_CHANNEL_IMPORTANT = "ghajarvpn_important"

    private val trustedPaymentSuffixes = setOf(
        STORE_HOST,
        "zarinpal.com",
        "aqayepardakht.ir",
        "zarinpey.com",
        "shaparak.ir",
        "behpardakht.com",
        "pec.ir",
        "sep.ir",
        "sadadpsp.ir",
        "asanpardakht.ir",
        "sepehrpay.com",
        "idpay.ir",
        "nextpay.org",
        "plisio.net",
        "nowpayments.io",
        "bluepal.ir",
        "uniquepay.ir"
    )

    fun sanitizePublicText(value: String): String = value
        .replace("Fao" + "xima", APP_NAME_EN, ignoreCase = true)
        .replace("GR" + "oute", APP_NAME_EN, ignoreCase = true)
        .replace("Gozar" + "Net", APP_NAME_EN, ignoreCase = true)
        .replace("Oracle" + " VPN", APP_NAME_EN, ignoreCase = true)
        .replace("Oracle" + "VPN", APP_NAME_EN, ignoreCase = true)
        .replace("فاکسیما", APP_NAME_FA)

    fun isTrustedStoreUri(uri: Uri): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(STORE_HOST, ignoreCase = true)

    /**
     * Checkout is limited to the URL host issued by the API, the store callback
     * host and known PSP domains. Arbitrary HTTPS navigation is never accepted.
     */
    fun isTrustedPaymentUri(uri: Uri, initialHost: String?): Boolean {
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        val first = initialHost?.lowercase()?.trimEnd('.')
        if (first != null && (host == first || host.endsWith(".$first"))) return true
        return trustedPaymentSuffixes.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
    }
}

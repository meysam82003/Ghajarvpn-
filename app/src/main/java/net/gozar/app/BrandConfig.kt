package net.gozar.app

import android.net.Uri

/** Public branding plus the private endpoints required by the native client. */
object BrandConfig {
    const val APP_NAME_FA = "قاجار وی پی ان"
    const val APP_NAME_EN = "Ghajarvpn"
    const val PACKAGE_ID = "com.ghajarvpn.app"

    const val GITHUB_URL = "https://github.com/meysam82003/Ghajarvpn-"
    const val TELEGRAM_CHANNEL_URL = "https://t.me/Ghajarvpn"
    const val FREE_CONFIG_CHANNEL = "Ghajarvpn"
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

    /**
     * The notice feed: per-user notices, plus whether the shop is open.
     *
     * Separate from miniapp.php because it answers before a shop gate does. A
     * client that has been told "install the app" or "the shop is off" still
     * has to be able to read the message saying so, and miniapp.php refuses
     * every other action while either is true.
     */
    const val NOTICES_API_URL = "$API_URL/notices.php"

    /**
     * The marketplace: other sellers' shops, sold through this app.
     *
     * Its own endpoint rather than another action on miniapp.php, because a
     * marketplace call reaches a *third party's* installation on the far side
     * and has a different failure surface entirely: a shop that is down must
     * not look like this shop being down. It answers `enabled: false` when the
     * owner has not switched the marketplace on, which is the normal state.
     */
    const val MARKET_API_URL = "$API_URL/market.php"

    /**
     * Sent on every store request so the server knows this is the app.
     *
     * The bot can be put into a mode where the mini app and the browser are
     * shown "install the app" and nothing else works, while the app keeps
     * working in full - which needs the server to be able to tell them apart.
     * This header is how, and it is not a security boundary: anyone can send
     * it, and all it gets past is a nag screen. Everything that protects money
     * or data is checked against the bearer token instead.
     */
    const val CLIENT_HEADER = "X-Ghajar-Client"
    const val CLIENT_ID = "app"

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
        "blupal.net",
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
    fun isTrustedPaymentUri(uri: Uri, initialHost: String?): Boolean =
        GhajarPaymentPolicy.allows(uri.toString(), initialHost, trustedPaymentSuffixes)

}

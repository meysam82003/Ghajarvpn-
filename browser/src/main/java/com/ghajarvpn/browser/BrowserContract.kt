package com.ghajarvpn.browser

import android.content.Context
import android.content.Intent

object BrowserContract {
    const val ACTION_NETWORK_STATE = "com.ghajarvpn.browser.NETWORK_STATE"
    const val ACTION_ENQUEUE_DOWNLOAD = "com.ghajarvpn.downloads.ENQUEUE"
    const val EXTRA_URL = "url"
    const val EXTRA_COOKIES = "cookies"
    const val EXTRA_REFERER = "referer"
    const val EXTRA_USER_AGENT = "user_agent"
    const val EXTRA_CONTENT_DISPOSITION = "content_disposition"
    const val EXTRA_CONTENT_TYPE = "content_type"
    const val EXTRA_PRIVATE = "private"
    const val EXTRA_PROXY_PORT = "proxy_port"
    const val EXTRA_REQUIRE_PROXY = "require_proxy"
    const val EXTRA_VPN_CONNECTED = "vpn_connected"
    const val EXTRA_SERVER_LABEL = "server_label"

    fun open(
        context: Context,
        url: String? = null,
        private: Boolean = false,
        proxyPort: Int = DEFAULT_PROXY_PORT,
        vpnConnected: Boolean = false,
        serverLabel: String = ""
    ) {
        context.startActivity(Intent(context, GhajarBrowserActivity::class.java).apply {
            url?.let { putExtra(EXTRA_URL, it) }
            putExtra(EXTRA_PRIVATE, private)
            putExtra(EXTRA_PROXY_PORT, proxyPort.coerceIn(1024, 65535))
            putExtra(EXTRA_VPN_CONNECTED, vpnConnected)
            putExtra(EXTRA_SERVER_LABEL, serverLabel.take(80))
        })
    }

    /** Sends only non-sensitive route metadata to the isolated browser process. */
    fun notifyNetworkState(
        context: Context,
        proxyPort: Int,
        vpnConnected: Boolean,
        serverLabel: String = ""
    ) {
        context.sendBroadcast(Intent(ACTION_NETWORK_STATE).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_PROXY_PORT, proxyPort.coerceIn(1024, 65535))
            putExtra(EXTRA_VPN_CONNECTED, vpnConnected)
            putExtra(EXTRA_SERVER_LABEL, serverLabel.take(80))
        })
    }

    const val DEFAULT_PROXY_PORT = 10626
}

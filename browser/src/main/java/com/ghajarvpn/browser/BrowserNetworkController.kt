package com.ghajarvpn.browser

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class BrowserNetworkRoute(
    val mode: BrowserNetworkMode,
    val proxyPort: Int,
    val vpnConnected: Boolean,
    val requireLocalProxy: Boolean
)

data class BrowserRouteDecision(
    val ready: Boolean,
    val useLocalProxy: Boolean,
    val message: String
)

/** Pure fail-closed policy shared by the WebView and Media3 hand-off. */
object BrowserNetworkPolicy {
    fun decide(
        mode: BrowserNetworkMode,
        vpnConnected: Boolean,
        localProxyReachable: Boolean,
        proxyOverrideSupported: Boolean
    ): BrowserRouteDecision = when (mode) {
        BrowserNetworkMode.DIRECT -> BrowserRouteDecision(true, false, "مستقیم")
        BrowserNetworkMode.BROWSER_ONLY -> when {
            !vpnConnected -> BrowserRouteDecision(false, false, "ابتدا اتصال قاجار را برقرار کنید")
            !proxyOverrideSupported -> BrowserRouteDecision(false, false, "WebView این دستگاه از پراکسی اختصاصی پشتیبانی نمی‌کند")
            !localProxyReachable -> BrowserRouteDecision(false, false, "ابتدا اتصال قاجار را برقرار کنید")
            else -> BrowserRouteDecision(true, true, "VPN فقط مرورگر")
        }
        BrowserNetworkMode.FULL_DEVICE -> when {
            vpnConnected && localProxyReachable && proxyOverrideSupported -> BrowserRouteDecision(true, true, "VPN کل دستگاه")
            vpnConnected && !localProxyReachable -> BrowserRouteDecision(true, false, "VPN کل دستگاه")
            else -> BrowserRouteDecision(false, false, "VPN کل دستگاه متصل نیست")
        }
    }
}

/**
 * Owns Chromium's process-wide proxy override. Browser activities run in the
 * isolated :browser process so Store/Payment WebViews cannot inherit it.
 */
class BrowserNetworkController(context: Context) {
    private val main = ContextCompat.getMainExecutor(context)
    private val io: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ghajar-browser-route").apply { isDaemon = true }
    }

    fun apply(
        mode: BrowserNetworkMode,
        proxyPort: Int,
        vpnConnected: Boolean,
        callback: (BrowserRouteDecision) -> Unit
    ) {
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
        io.execute {
            val reachable = localProxyReachable(proxyPort)
            val decision = BrowserNetworkPolicy.decide(mode, vpnConnected, reachable, supported)
            main.execute {
                if (!supported) {
                    callback(decision)
                } else if (decision.useLocalProxy) {
                    val config = ProxyConfig.Builder()
                        .addProxyRule("socks://127.0.0.1:$proxyPort")
                        .build()
                    ProxyController.getInstance().setProxyOverride(config, main) { callback(decision) }
                } else {
                    ProxyController.getInstance().clearProxyOverride(main) { callback(decision) }
                }
            }
        }
    }

    fun clear() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride(main) {}
        }
        io.shutdownNow()
    }

    private fun localProxyReachable(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 600) }
        true
    }.getOrDefault(false)
}

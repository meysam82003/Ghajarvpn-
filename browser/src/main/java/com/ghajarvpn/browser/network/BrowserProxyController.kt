package com.ghajarvpn.browser.network

import android.webkit.WebView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor

/**
 * Applies the browser route to real WebView traffic using the AndroidX WebKit
 * proxy controller (per-webview runtime proxy). When the feature is missing on a
 * device the route is reported unavailable instead of pretending to be connected.
 */
object BrowserProxyController {

    private val directExecutor: Executor = Executor { command -> command.run() }

    fun supported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    fun apply(endpoint: BrowserRouteEndpoint, onError: (String) -> Unit) {
        if (!supported()) {
            onError("پیکربندی مسیر روی این دستگاه پشتیبانی نمی‌شود")
            return
        }
        val applied = runCatching {
            val config: ProxyConfig = ProxyConfig.Builder().addProxyRule(endpoint.proxyRule()).build()
            ProxyController.getInstance().setProxyOverride(config, directExecutor, Runnable { })
            true
        }.getOrDefault(false)
        if (!applied) onError("اعمال مسیر روی WebView ناموفق بود")
    }

    fun clear(onError: (String) -> Unit) {
        if (!supported()) return
        val done = runCatching {
            ProxyController.getInstance().clearProxyOverride(directExecutor, Runnable { })
            true
        }.getOrDefault(false)
        if (!done) onError("حذف مسیر WebView ناموفق بود")
    }

    /** Fallback for WebViews without PROXY_OVERRIDE support (best effort, JVM-level). */
    fun setLegacySystemProxy(endpoint: BrowserRouteEndpoint?) {
        runCatching {
            System.setProperty("http.proxyHost", endpoint?.host ?: "")
            System.setProperty("http.proxyPort", endpoint?.port?.toString() ?: "")
            System.setProperty("https.proxyHost", endpoint?.host ?: "")
            System.setProperty("https.proxyPort", endpoint?.port?.toString() ?: "")
        }
    }

    @Suppress("unused")
    private fun unused(webView: WebView) = Unit
}

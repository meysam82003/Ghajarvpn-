package com.ghajarvpn.browser

import android.net.Uri

object BrowserRequestPolicy {
    private val trackerHosts = setOf(
        "google-analytics.com", "googletagmanager.com", "doubleclick.net", "app-measurement.com",
        "facebook.net", "connect.facebook.net", "branch.io", "appsflyer.com", "adjust.com",
        "hotjar.com", "clarity.ms", "fullstory.com", "mouseflow.com"
    )
    private val adHosts = setOf(
        "googlesyndication.com", "googleadservices.com", "adnxs.com", "taboola.com", "outbrain.com",
        "criteo.com", "amazon-adsystem.com", "adsrvr.org", "adivery.com", "tapsell.ir", "yektanet.com"
    )

    fun blocked(url: String, settings: BrowserSettings): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        fun matches(list: Set<String>) = list.any { host == it || host.endsWith(".$it") }
        return settings.trackerBlocking && matches(trackerHosts) || settings.adBlocking && matches(adHosts)
    }

    fun normalizeInput(input: String, settings: BrowserSettings): String {
        val value = input.trim()
        if (value.isBlank()) return BrowserTab.HOME_URL
        if (value.contains(' ') || !value.contains('.')) {
            return settings.searchEngine.replace("%s", Uri.encode(value))
        }
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(value)) value else "https://$value"
        return if (settings.httpsPreference && withScheme.startsWith("http://", true)) "https://" + withScheme.substring(7) else withScheme
    }

    fun safeExternal(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") && uri.userInfo == null
    }
}

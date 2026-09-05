package com.ghajarvpn.browser

import android.content.Context
import android.content.Intent
import java.net.URI

/**
 * Embedded store browsing mode. Store/Wallet web destinations open inside the
 * Ghajar browser with the address bar hidden and navigation restricted to the
 * launching https origin: no raw URL display, no copy/share/open-external, no
 * fallback to any other browser. Payment links never route here.
 */
object StoreEmbeddedPolicy {

    /** Hosts allowed to be launched as embedded store pages. */
    private val trustedLaunchHosts = setOf(
        "httpuser87890.ir", "www.httpuser87890.ir"
    )

    data class Decision(val allowed: Boolean, val uri: String? = null, val reason: String = "")

    /** Validates the launch URL exactly once, at entry. */
    fun decide(raw: String): Decision {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return Decision(false, reason = "invalid")
        if (uri.scheme?.lowercase() != "https") return Decision(false, reason = "scheme")
        if (!uri.userInfo.isNullOrEmpty()) return Decision(false, reason = "userinfo")
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return Decision(false, reason = "host")
        if (host !in trustedLaunchHosts) return Decision(false, reason = "untrusted")
        return Decision(true, uri.toASCIIString())
    }

    /** Navigation guard while the embedded page is open. */
    fun navigationAllowed(from: String?, to: String?): Boolean {
        val next = runCatching { URI(to.orEmpty()) }.getOrNull() ?: return false
        if (next.scheme?.lowercase() !in setOf("https")) return false
        if (!next.userInfo.isNullOrEmpty()) return false
        val host = next.host?.lowercase()?.trimEnd('.') ?: return false
        val origin = from?.let { runCatching { URI(it) }.getOrNull() }
        val originHost = origin?.host?.lowercase()?.trimEnd('.') ?: return host in trustedLaunchHosts
        // Same host always allowed; subdomains of the origin host allowed.
        return host == originHost || host.endsWith(".$originHost") ||
            (host in trustedLaunchHosts && originHost in trustedLaunchHosts)
    }

    fun launchIntent(context: Context, url: String): Intent =
        Intent(context, GhajarBrowserActivity::class.java)
            .putExtra(BrowserContract.EXTRA_URL, url)
            .putExtra(BrowserContract.EXTRA_EMBEDDED_STORE, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

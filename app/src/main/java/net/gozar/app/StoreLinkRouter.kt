package net.gozar.app

import android.content.Context
import android.content.Intent
import android.net.Uri

enum class StoreLinkKind { PAYMENT, TERMS, HELP, USER_PANEL, WEB_SUPPORT, REJECTED }

data class StoreRoute(val kind: StoreLinkKind, val uri: Uri)

/** One audited entry point for every web destination opened by Store/Wallet. */
object StoreLinkRouter {
    fun classify(raw: String, payment: Boolean = false): StoreRoute {
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
            ?: return StoreRoute(StoreLinkKind.REJECTED, Uri.EMPTY).also {
                GhajarLog.w("Payment", "classify: unparsable url")
            }
        if (!uri.scheme.equals("https", true) || uri.userInfo != null) {
            GhajarLog.w("Payment", "classify: rejected scheme/userinfo host=${uri.host}")
            return StoreRoute(StoreLinkKind.REJECTED, uri)
        }
        if (payment) {
            return if (BrandConfig.isTrustedPaymentUri(uri, null)) StoreRoute(StoreLinkKind.PAYMENT, uri)
            else {
                GhajarLog.w("Payment", "classify: untrusted payment host=${uri.host}")
                StoreRoute(StoreLinkKind.REJECTED, uri)
            }
        }
        val path = uri.path.orEmpty().lowercase()
        val kind = when {
            path.contains("terms") || path.contains("privacy") -> StoreLinkKind.TERMS
            path.contains("support") || path.contains("contact") -> StoreLinkKind.WEB_SUPPORT
            path.contains("help") || path.contains("faq") -> StoreLinkKind.HELP
            BrandConfig.isTrustedStoreUri(uri) -> StoreLinkKind.USER_PANEL
            else -> StoreLinkKind.REJECTED
        }
        return StoreRoute(kind, uri)
    }

    fun securePaymentIntent(context: Context, raw: String): Intent? {
        val route = classify(raw, payment = true)
        if (route.kind != StoreLinkKind.PAYMENT) return null
        return Intent(context, SecurePaymentActivity::class.java)
            .putExtra(SecurePaymentActivity.EXTRA_URL, route.uri.toString())
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun browserIntent(context: Context, raw: String): Intent? {
        val route = classify(raw)
        if (route.kind !in setOf(StoreLinkKind.TERMS, StoreLinkKind.HELP, StoreLinkKind.USER_PANEL, StoreLinkKind.WEB_SUPPORT)) return null
        // Store web pages open in a locked-down in-app WebView: no address bar,
        // no URL exposure, navigation locked to the store origin.
        val title = when (route.kind) {
            StoreLinkKind.TERMS -> "قوانین و مقررات"
            StoreLinkKind.HELP -> "راهنما"
            StoreLinkKind.WEB_SUPPORT -> "پشتیبانی"
            else -> "پنل کاربری قاجار"
        }
        return Intent(context, GhajarStoreWebActivity::class.java)
            .putExtra(GhajarStoreWebActivity.EXTRA_URL, route.uri.toString())
            .putExtra(GhajarStoreWebActivity.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

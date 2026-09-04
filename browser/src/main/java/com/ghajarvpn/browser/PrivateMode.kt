package com.ghajarvpn.browser

import android.webkit.WebStorage
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Private-mode hardening. Private tabs never touch persistent storage:
 *  - their WebView runs on a separate throwaway profile (separate cookies and
 *    WebStorage) when MULTI_PROFILE is available on the device,
 *  - history/tab persistence is skipped by BrowserRepository (kept),
 *  - when the last private tab closes, the profile is deleted, wiping its
 *    cookies, storage and service workers in one call.
 */
object PrivateMode {

    private const val PROFILE_NAME = "ghajar_private_session"

    fun separateProfileSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    /** Switches the WebView to the ephemeral private profile, if supported. */
    fun attach(webView: android.webkit.WebView): Boolean {
        if (!separateProfileSupported()) return false
        return runCatching {
            val profile: Profile = ProfileStore.getInstance().getOrCreateProfile(PROFILE_NAME)
            profile.cookieManager.removeAllCookies(null)
            profile.cookieManager.setAcceptCookie(false)
            profile.webStorage.deleteAllData()
            WebViewCompat.setProfile(webView, PROFILE_NAME)
            true
        }.getOrDefault(false)
    }

    /** Best-effort cleanup for devices without MULTI_PROFILE support. */
    fun detach(webView: android.webkit.WebView) {
        runCatching { WebViewCompat.setProfile(webView, Profile.DEFAULT_PROFILE_NAME) }
    }

    /** Called when the last private tab closes: erases all private session data. */
    fun clearSession() {
        if (separateProfileSupported()) {
            runCatching { ProfileStore.getInstance().deleteProfile(PROFILE_NAME) }
            runCatching { WebStorage.getInstance().deleteAllData() }
        }
        // Without MULTI_PROFILE there is no isolated store; private tabs already run
        // with cookies disabled per settings and DOM storage off, and we must never
        // wipe the normal session's data, so nothing else is cleared here.
    }
}

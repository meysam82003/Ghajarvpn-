package com.ghajarvpn.browser.translate

/**
 * Provider-neutral page translation contract. The first provider ships without
 * credentials: the free google translate web endpoint. No fake API keys — a
 * provider that needs credentials simply reports itself unavailable until the
 * host app supplies them.
 */
interface TranslationProvider {
    val id: String
    val displayName: String
    val requiresCredentials: Boolean

    fun isAvailable(): Boolean

    /** Returns a URL that translates [url] into [targetLanguage]. */
    fun translatedUrl(url: String, targetLanguage: String): String?

    /** Returns the HTML of a translated document when the provider fetches inline. */
    suspend fun translateHtml(html: String, targetLanguage: String): String? = null
}

/**
 * Credential-free provider backed by the Google Translate web UI. The browser
 * loads the translated page in a normal tab; no private key material involved.
 */
class GoogleTranslateWebProvider : TranslationProvider {
    override val id = "google_web"
    override val displayName = "مترجم وب"
    override val requiresCredentials = false

    override fun isAvailable(): Boolean = true

    override fun translatedUrl(url: String, targetLanguage: String): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
        val language = targetLanguage.ifBlank { "fa" }
        return "https://$language.translate.goog/?sl=auto&tl=$language&u=$encoded"
    }
}

/** Registry resolved at runtime; ordering decides the active provider. */
object TranslationRegistry {
    private val providers = mutableListOf<TranslationProvider>(GoogleTranslateWebProvider())

    @Volatile var preferredLanguage: String = "fa"

    fun register(provider: TranslationProvider) {
        providers.removeAll { it.id == provider.id }
        providers += provider
    }

    fun active(): TranslationProvider? = providers.firstOrNull { it.isAvailable() }

    fun all(): List<TranslationProvider> = providers.toList()
}

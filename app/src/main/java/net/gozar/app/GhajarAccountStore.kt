package net.gozar.app

import android.content.Context
import org.json.JSONObject

/**
 * Keeps the Mini App bearer token encrypted by Android Keystore (AES-GCM).
 * Plaintext keys from early builds are migrated once and removed immediately.
 */
class GhajarAccountStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pendingLink(): GhajarLinkSession? = synchronized(LOCK) {
        val encrypted = prefs.getString(KEY_PENDING_LINK, null) ?: return null
        val link = runCatching {
            val plain = Crypto.decrypt(encrypted) ?: return@runCatching null
            val json = JSONObject(plain)
            if (json.optInt("version") != 1) return@runCatching null
            GhajarLinkSession(json.getString("code"), json.getString("session_token"),
                json.optString("bot_username", "Ghajar_vpnbot"), json.getInt("expires_in"),
                json.getLong("expires_at_ms"))
        }.getOrNull()?.takeIf { GhajarUiRules.validPendingLink(it.code, it.sessionToken,
            it.expiresAtMillis, System.currentTimeMillis()) }
        if (link == null) clearPendingLink()
        return link
    }

    /** Called on the API's IO dispatcher before the external bot is opened. */
    fun savePendingLink(link: GhajarLinkSession): Boolean = synchronized(LOCK) {
        if (!GhajarUiRules.validPendingLink(link.code, link.sessionToken,
                link.expiresAtMillis, System.currentTimeMillis())) return false
        val plain = JSONObject().put("version", 1).put("code", link.code)
            .put("session_token", link.sessionToken).put("bot_username", link.botUsername)
            .put("expires_in", link.expiresInSeconds).put("expires_at_ms", link.expiresAtMillis).toString()
        val encrypted = Crypto.encrypt(plain) ?: return false
        return prefs.edit().putString(KEY_PENDING_LINK, encrypted).commit()
    }

    fun clearPendingLink() = synchronized(LOCK) { prefs.edit().remove(KEY_PENDING_LINK).apply() }

    fun token(): String = synchronized(LOCK) {
        prefs.getString(KEY_TOKEN_ENCRYPTED, null)
            ?.let(Crypto::decrypt)
            ?.let(GhajarLinkFlow::bearer)
            ?.let { return it }

        val legacy = sequenceOf(
            prefs.getString(KEY_TOKEN_PLAINTEXT, null),
            appContext.getSharedPreferences("ghajar_store", Context.MODE_PRIVATE)
                .getString("telegram_init_data", null),
            appContext.getSharedPreferences("ghajarvpn_store", Context.MODE_PRIVATE)
                .getString("account_token", null)
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

        if (legacy.isNotBlank() && saveToken(legacy)) return legacy
        return ""
    }

    internal fun completePendingLink(sessionToken: String, token: String): GhajarLinkState = synchronized(LOCK) {
        if (pendingLink()?.sessionToken != sessionToken) GhajarLinkState.SUPERSEDED
        else if (saveToken(token)) GhajarLinkState.LINKED else GhajarLinkState.STORAGE_ERROR
    }

    fun saveToken(token: String): Boolean = synchronized(LOCK) {
        val clean = GhajarLinkFlow.bearer(token) ?: return false
        val encrypted = Crypto.encrypt(clean) ?: return false
        val previous = listOf(KEY_TOKEN_ENCRYPTED, KEY_TOKEN_PLAINTEXT, KEY_PENDING_LINK)
            .associateWith { prefs.getString(it, null) }
        val saved = prefs.edit()
            .putString(KEY_TOKEN_ENCRYPTED, encrypted)
            .remove(KEY_TOKEN_PLAINTEXT)
            .remove(KEY_PENDING_LINK)
            .commit()
        if (!saved) {
            // commit() can change the in-memory map even when the disk write fails.
            // Do not report that unpersisted token as a linked account on resume.
            prefs.edit().apply {
                previous.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
            }.apply()
            return false
        }
        clearLegacyPlaintext()
        return true
    }

    fun clear() = synchronized(LOCK) {
        prefs.edit().remove(KEY_TOKEN_ENCRYPTED).remove(KEY_TOKEN_PLAINTEXT).remove(KEY_PENDING_LINK).apply()
        clearLegacyPlaintext()
    }

    private fun clearLegacyPlaintext() {
        appContext.getSharedPreferences("ghajar_store", Context.MODE_PRIVATE)
            .edit().remove("telegram_init_data").apply()
        appContext.getSharedPreferences("ghajarvpn_store", Context.MODE_PRIVATE)
            .edit().remove("account_token").apply()
    }

    companion object {
        private val LOCK = Any()
        const val PREFS = "ghajarvpn_account_v2"
        private const val KEY_TOKEN_ENCRYPTED = "account_token_aes_gcm"
        private const val KEY_TOKEN_PLAINTEXT = "account_token"
        private const val KEY_PENDING_LINK = "pending_link_aes_gcm"
    }
}

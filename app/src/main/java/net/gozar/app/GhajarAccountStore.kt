package net.gozar.app

import android.content.Context

/**
 * Keeps the Mini App bearer token encrypted by Android Keystore (AES-GCM).
 * Plaintext keys from early builds are migrated once and removed immediately.
 */
class GhajarAccountStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(): String {
        prefs.getString(KEY_TOKEN_ENCRYPTED, null)
            ?.let(Crypto::decrypt)
            ?.takeIf { it.isNotBlank() }
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

    fun saveToken(token: String): Boolean {
        val clean = token.trim()
        if (clean.isEmpty()) return false
        val encrypted = Crypto.encrypt(clean) ?: return false
        prefs.edit()
            .putString(KEY_TOKEN_ENCRYPTED, encrypted)
            .remove(KEY_TOKEN_PLAINTEXT)
            .apply()
        clearLegacyPlaintext()
        return true
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN_ENCRYPTED).remove(KEY_TOKEN_PLAINTEXT).apply()
        clearLegacyPlaintext()
    }

    private fun clearLegacyPlaintext() {
        appContext.getSharedPreferences("ghajar_store", Context.MODE_PRIVATE)
            .edit().remove("telegram_init_data").apply()
        appContext.getSharedPreferences("ghajarvpn_store", Context.MODE_PRIVATE)
            .edit().remove("account_token").apply()
    }

    companion object {
        const val PREFS = "ghajarvpn_account_v2"
        private const val KEY_TOKEN_ENCRYPTED = "account_token_aes_gcm"
        private const val KEY_TOKEN_PLAINTEXT = "account_token"
    }
}

package com.ghajarvpn.downloads

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Persists session headers only as Android-Keystore AES-GCM ciphertext. */
internal class DownloadHeaderVault(context: Context) {
    private val prefs = context.getSharedPreferences("ghajar_download_headers", Context.MODE_PRIVATE)

    fun put(taskId: String, source: Map<String, String>): String {
        val headers = DownloadHeaderPolicy.sanitize(source)
        if (headers.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val clear = JSONObject(headers).toString().toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(clear)
        clear.fill(0)
        val token = taskId
        prefs.edit().putString(token, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
        return token
    }

    fun get(token: String): Map<String, String> {
        if (token.isBlank()) return emptyMap()
        val packed = runCatching { Base64.decode(prefs.getString(token, null), Base64.NO_WRAP) }.getOrNull() ?: return emptyMap()
        if (packed.size <= IV_BYTES) return emptyMap()
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, IV_BYTES)))
            }
            val clear = cipher.doFinal(packed.copyOfRange(IV_BYTES, packed.size))
            try {
                val json = JSONObject(String(clear, Charsets.UTF_8))
                json.keys().asSequence().associateWith { json.getString(it) }
            } finally { clear.fill(0) }
        }.getOrDefault(emptyMap())
    }

    fun remove(token: String) { if (token.isNotBlank()) prefs.edit().remove(token).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    companion object {
        private const val ALIAS = "ghajar_download_headers_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}

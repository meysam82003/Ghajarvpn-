package net.gozar.app

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Safebox: configs kept out of the server list in a vault only the user's
 * password opens. The vault is one file: "GSB1" | salt(16) | iv(12) |
 * AES-256-GCM(JSON array of profiles), the key from PBKDF2-HMAC-SHA256
 * (200 000 rounds). The password is never stored; a wrong one fails the GCM
 * tag. The same file is what export/import move between devices.
 */
object Safebox {

    private const val MAGIC = "GSB1"
    private const val ROUNDS = 200_000

    private fun file(ctx: Context) = File(ctx.filesDir, "safebox.gsb")

    fun exists(ctx: Context) = file(ctx).exists()

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ROUNDS, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    fun seal(configs: List<ProxyConfig>, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val arr = JSONArray().apply { configs.forEach { put(it.toJson()) } }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC.toByteArray())
        return MAGIC.toByteArray() + salt + iv + c.doFinal(arr.toString().toByteArray(Charsets.UTF_8))
    }

    /** The profiles, or null for a wrong password or a file that is not a vault. */
    fun open(bytes: ByteArray, password: CharArray): List<ProxyConfig>? = runCatching {
        if (bytes.size < 4 + 16 + 12 + 16 || String(bytes, 0, 4, Charsets.US_ASCII) != MAGIC) return null
        val salt = bytes.copyOfRange(4, 20)
        val iv = bytes.copyOfRange(20, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC.toByteArray())
        val arr = JSONArray(String(c.doFinal(bytes, 32, bytes.size - 32), Charsets.UTF_8))
        (0 until arr.length()).mapNotNull { ProxyConfig.fromJson(arr.getJSONObject(it)) }
    }.getOrNull()

    fun isVault(bytes: ByteArray) = bytes.size > 4 && String(bytes, 0, 4, Charsets.US_ASCII) == MAGIC

    fun load(ctx: Context, password: CharArray): List<ProxyConfig>? =
        if (!exists(ctx)) emptyList() else open(file(ctx).readBytes(), password)

    fun save(ctx: Context, configs: List<ProxyConfig>, password: CharArray) {
        val tmp = File(ctx.filesDir, "safebox.gsb.tmp")
        tmp.writeBytes(seal(configs, password))
        if (!tmp.renameTo(file(ctx))) { tmp.copyTo(file(ctx), overwrite = true); tmp.delete() }
    }

    fun raw(ctx: Context): ByteArray? = if (exists(ctx)) file(ctx).readBytes() else null

    fun delete(ctx: Context) { file(ctx).delete() }
}

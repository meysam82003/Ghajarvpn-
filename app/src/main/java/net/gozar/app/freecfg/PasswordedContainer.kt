package net.gozar.app.freecfg

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * Documented, open decryption for password-protected containers the free
 * ecosystem actually distributes (AES-128/256-CBC with SHA-256 key derivation
 * from the caption password, as used by common tunnel packers). Never guesses:
 * a wrong password simply fails MAC/magic checks and reports LOCKED.
 */
object PasswordedContainer {

    fun tryDecrypt(bytes: ByteArray, password: String): ByteArray? {
        if (password.isBlank() || bytes.size < 32) return null
        // Container layouts seen in the wild: [16-byte salt][16-byte IV][ciphertext]
        val salt = bytes.copyOfRange(0, 16)
        val iv = bytes.copyOfRange(16, 32)
        val body = bytes.copyOfRange(32, bytes.size)
        for (keyLength in intArrayOf(32, 16)) {
            val key = derive(password, salt, keyLength)
            val result = runCatching {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                cipher.doFinal(body)
            }.getOrNull() ?: continue
            val text = runCatching { result.toString(Charsets.UTF_8) }.getOrDefault("")
            if (text.contains("://") || text.contains("{") || text.contains("proxies:")) return result
        }
        return null
    }

    private fun derive(password: String, salt: ByteArray, length: Int): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(password.toByteArray(Charsets.UTF_8))
        md.update(salt)
        val digest = md.digest()
        return digest.copyOf(length)
    }
}

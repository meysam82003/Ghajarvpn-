package net.gozar.app.gsb2

import net.gozar.app.ProxyConfig
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * GSB2: Ghajar's secure share. One file carries one config, a set of servers
 * or a subscription, with a display name, a note, an optional password, an
 * expiry, a data quota and the choice to hide the config from the receiver.
 *
 * File: "GSB2" | version(1) | flags(1: bit0 = password) | salt(16) | iv(12) |
 * AES-256-GCM(JSON envelope). With a password the key is PBKDF2-HMAC-SHA256
 * (200 000 rounds) of it; without one the key is a random 32 bytes stored in
 * the salt+key block of the header - that is concealment, not secrecy, and
 * the UI says so. The envelope is {"payload": <json string>, "sig": base64,
 * "pub": base64}: an ECDSA P-256 signature by the issuer over the exact
 * payload bytes, so the limits inside cannot be edited without the signature
 * failing.
 *
 * What is enforced, and where: expiry and quota are enforced by this app on
 * the receiving device (connect is refused, and a running tunnel is stopped,
 * once either runs out; a clock moved backwards does not buy time). A device
 * limit, remote revoke and a remote password change need an issuer server
 * and are not part of this build.
 */
object Gsb2 {
    const val MAGIC = "GSB2"
    const val VERSION = 1
    const val EXTENSION = "gsb2"
    private const val ROUNDS = 200_000
    private const val FLAG_PASSWORD = 1
    const val MAX_FILE = 4 * 1024 * 1024

    data class Share(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val note: String = "",
        val createdAt: Long,
        /** Epoch ms, 0 = never. */
        val expiresAt: Long = 0L,
        /** Validity counted from the moment the receiver imports it (ms), 0 = none. */
        val durationMs: Long = 0L,
        /** Bytes up+down allowed, 0 = unlimited. */
        val quotaBytes: Long = 0L,
        /** Always hidden: a received share is for connecting only (kept in the format for compatibility). */
        val hidden: Boolean = true,
        val configs: List<ProxyConfig> = emptyList(),
        val subscriptions: List<String> = emptyList(),
        val issuerKey: String = ""
    )

    /** What a received config carries in its extra JSON under "gsb2". */
    data class Meta(val shareId: String, val shareName: String, val expiresAt: Long, val quotaBytes: Long, val issuer: String) {
        fun toJson(): JSONObject = JSONObject().put("id", shareId).put("name", shareName)
            .put("expiresAt", expiresAt).put("quota", quotaBytes).put("issuer", issuer)
        companion object {
            fun of(c: ProxyConfig): Meta? = c.extraJson().optJSONObject("gsb2")?.let { o ->
                Meta(o.optString("id"), o.optString("name"), o.optLong("expiresAt"), o.optLong("quota"), o.optString("issuer"))
                    .takeIf { it.shareId.isNotBlank() }
            }
        }
    }

    sealed class OpenResult {
        data class Ok(val share: Share) : OpenResult()
        object NeedsPassword : OpenResult()
        object WrongPassword : OpenResult()
        data class Invalid(val reason: String) : OpenResult()
    }

    // ---- issuer key ----

    fun newIssuerKey(): Pair<String, String> {
        val g = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val kp = g.generateKeyPair()
        return b64(kp.private.encoded) to b64(kp.public.encoded)
    }

    private fun privateKey(pkcs8: String): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(unb64(pkcs8)))
    private fun publicKey(x509: String): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(x509)))

    /** Short, human-comparable issuer id. */
    fun fingerprint(publicKey: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(unb64(publicKey)).take(6).joinToString("") { "%02x".format(it) }

    // ---- seal / open ----

    fun seal(share: Share, issuerPrivate: String, issuerPublic: String, password: CharArray?): ByteArray {
        val payload = payloadJson(share).toString().toByteArray(Charsets.UTF_8)
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(privateKey(issuerPrivate)); update(payload); sign() }
        val envelope = JSONObject().put("payload", String(payload, Charsets.UTF_8)).put("sig", b64(sig)).put("pub", issuerPublic)
            .toString().toByteArray(Charsets.UTF_8)
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val usePassword = password != null && password.isNotEmpty()
        val rawKey = if (usePassword) null else ByteArray(32).also(rnd::nextBytes)
        val key = if (usePassword) derive(password!!, salt) else SecretKeySpec(rawKey, "AES")
        val header = MAGIC.toByteArray(Charsets.US_ASCII) + byteArrayOf(VERSION.toByte(), (if (usePassword) FLAG_PASSWORD else 0).toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)); updateAAD(header) }
        val body = cipher.doFinal(envelope)
        return header + salt + iv + (rawKey ?: ByteArray(0)) + body
    }

    fun looksLike(bytes: ByteArray): Boolean = bytes.size > 6 && String(bytes, 0, 4, Charsets.US_ASCII) == MAGIC

    fun needsPassword(bytes: ByteArray): Boolean = looksLike(bytes) && (bytes[5].toInt() and FLAG_PASSWORD) != 0

    fun open(bytes: ByteArray, password: CharArray?): OpenResult {
        if (!looksLike(bytes) || bytes.size > MAX_FILE) return OpenResult.Invalid("این فایل GSB2 نیست.")
        if (bytes[4].toInt() != VERSION) return OpenResult.Invalid("نسخهٔ این فایل GSB2 پشتیبانی نمی‌شود.")
        val pw = (bytes[5].toInt() and FLAG_PASSWORD) != 0
        if (pw && (password == null || password.isEmpty())) return OpenResult.NeedsPassword
        val header = bytes.copyOfRange(0, 6)
        var off = 6
        val salt = bytes.copyOfRange(off, off + 16); off += 16
        val iv = bytes.copyOfRange(off, off + 12); off += 12
        val key = if (pw) derive(password!!, salt) else {
            if (bytes.size < off + 32 + 16) return OpenResult.Invalid("فایل GSB2 ناقص است.")
            SecretKeySpec(bytes.copyOfRange(off, off + 32), "AES").also { off += 32 }
        }
        val envelope = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)); updateAAD(header)
                doFinal(bytes.copyOfRange(off, bytes.size))
            }
        } catch (e: javax.crypto.AEADBadTagException) {
            return if (pw) OpenResult.WrongPassword else OpenResult.Invalid("فایل GSB2 خراب یا دستکاری شده است.")
        } catch (e: Exception) {
            return OpenResult.Invalid("فایل GSB2 خراب است.")
        }
        return try {
            val env = JSONObject(String(envelope, Charsets.UTF_8))
            val payload = env.getString("payload").toByteArray(Charsets.UTF_8)
            val pub = env.getString("pub")
            val ok = Signature.getInstance("SHA256withECDSA").run { initVerify(publicKey(pub)); update(payload); verify(unb64(env.getString("sig"))) }
            if (!ok) OpenResult.Invalid("امضای سازندهٔ این اشتراک معتبر نیست.")
            else OpenResult.Ok(parsePayload(JSONObject(String(payload, Charsets.UTF_8)), pub))
        } catch (e: Exception) {
            OpenResult.Invalid("محتوای فایل GSB2 معتبر نیست.")
        }
    }

    /** The configs to store on the receiving device, each marked with the share's limits. */
    fun receivedConfigs(share: Share, importedAt: Long = System.currentTimeMillis()): List<ProxyConfig> {
        // A duration starts on import; the earlier of it and the fixed date wins.
        val byDuration = if (share.durationMs > 0) importedAt + share.durationMs else 0L
        val expires = listOf(share.expiresAt, byDuration).filter { it > 0 }.minOrNull() ?: 0L
        val meta = Meta(share.id, share.name, expires, share.quotaBytes, fingerprint(share.issuerKey))
        return share.configs.map { c ->
            val extra = c.extraJson().put("gsb2", meta.toJson())
            c.copy(id = UUID.randomUUID().toString(), subId = "", locked = true, extra = extra.toString(),
                name = c.name.ifBlank { share.name })
        }
    }

    // ---- policy ----

    sealed class Verdict {
        object Allowed : Verdict()
        data class Blocked(val reason: String) : Verdict()
    }

    /** [now] must already be guarded against a clock moved backwards (see Gsb2Usage). */
    fun check(meta: Meta, usedBytes: Long, now: Long): Verdict = when {
        meta.expiresAt in 1..now -> Verdict.Blocked("اعتبار اشتراک «${meta.shareName}» تمام شده است.")
        meta.quotaBytes > 0 && usedBytes >= meta.quotaBytes -> Verdict.Blocked("حجم اشتراک «${meta.shareName}» تمام شده است.")
        else -> Verdict.Allowed
    }

    // ---- internals ----

    private fun payloadJson(s: Share): JSONObject = JSONObject()
        .put("v", 2).put("id", s.id).put("name", s.name).put("note", s.note).put("createdAt", s.createdAt)
        .put("expiresAt", s.expiresAt).put("duration", s.durationMs).put("quota", s.quotaBytes).put("hidden", s.hidden)
        .put("configs", JSONArray().apply { s.configs.forEach { put(it.toJson()) } })
        .put("subscriptions", JSONArray(s.subscriptions))

    private fun parsePayload(o: JSONObject, pub: String): Share {
        val configs = o.optJSONArray("configs")?.let { a -> (0 until a.length()).map { ProxyConfig.fromJson(a.getJSONObject(it)) } }.orEmpty()
        val subs = o.optJSONArray("subscriptions")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        require(configs.isNotEmpty() || subs.isNotEmpty())
        return Share(o.getString("id"), o.optString("name"), o.optString("note"), o.optLong("createdAt"),
            o.optLong("expiresAt"), o.optLong("duration"), o.optLong("quota"), o.optBoolean("hidden", true), configs, subs, pub)
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ROUNDS, 256)
        try { return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = Base64.getDecoder().decode(s)
}

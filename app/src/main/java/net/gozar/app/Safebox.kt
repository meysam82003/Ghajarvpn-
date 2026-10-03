package net.gozar.app

import android.content.Context
import org.json.JSONArray
import java.io.File
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Compatibility facade: read GSB1, write authenticated item-based GSB2.
 * The richer policy-aware repository never flattens locked entries through this legacy facade. */
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

    fun seal(configs: List<ProxyConfig>, password: CharArray): ByteArray =
        net.gozar.app.security.vault.VaultCrypto.seal(configs.map(::entry),password)

    private fun entry(c: ProxyConfig): net.gozar.app.security.vault.VaultEntry {
        val now = System.currentTimeMillis()
        return net.gozar.app.security.vault.VaultEntry(displayName=c.name,protocol=c.protocol,payload=c.toJson().toString(),createdAt=now)
    }

    fun open(bytes: ByteArray, password: CharArray): List<ProxyConfig>? = runCatching { openChecked(bytes,password) }.getOrNull()

    fun openChecked(bytes: ByteArray, password: CharArray): List<ProxyConfig> {
        if (!isVault(bytes)) throw net.gozar.app.security.vault.VaultException(net.gozar.app.security.vault.VaultException.Kind.CORRUPT)
        if (!net.gozar.app.security.vault.VaultFormat.isV2(bytes)) return openLegacy(bytes,password)
            ?: throw net.gozar.app.security.vault.VaultException(net.gozar.app.security.vault.VaultException.Kind.WRONG_PASSWORD_OR_DAMAGED_KEY)
        return net.gozar.app.security.vault.VaultCrypto.open(bytes,password).map { entry ->
            if(entry.policy != net.gozar.app.security.vault.VaultPolicy() || entry.quota != net.gozar.app.security.vault.VaultQuota() || entry.expiresAt != null)
                throw net.gozar.app.security.vault.VaultException(net.gozar.app.security.vault.VaultException.Kind.POLICY)
            ProxyConfig.fromJson(net.gozar.app.configtoolkit.BoundedJson.objectValue(entry.payload))
                ?: throw net.gozar.app.security.vault.VaultException(net.gozar.app.security.vault.VaultException.Kind.INVALID_CONFIG)
        }
    }

    fun loadChecked(ctx: Context,password: CharArray): List<ProxyConfig> =
        if(!exists(ctx)) emptyList() else openChecked(net.gozar.app.security.vault.VaultFileStore.read(file(ctx)),password)

    /** The profiles, or null for a wrong password or a file that is not a vault. */
    internal fun openLegacy(bytes: ByteArray, password: CharArray): List<ProxyConfig>? = runCatching {
        if (bytes.size > net.gozar.app.security.vault.VaultFormat.MAX_FILE) return null
        if (bytes.size < 4 + 16 + 12 + 16 || String(bytes, 0, 4, Charsets.US_ASCII) != MAGIC) return null
        val salt = bytes.copyOfRange(4, 20)
        val iv = bytes.copyOfRange(20, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC.toByteArray())
        val plain=c.doFinal(bytes,32,bytes.size-32)
        try {
            val arr=net.gozar.app.configtoolkit.BoundedJson.objectValue("{\"entries\":"+net.gozar.app.security.vault.VaultFormat.utf8(plain)+"}").getJSONArray("entries")
            require(arr.length()<=net.gozar.app.security.vault.VaultFormat.MAX_ENTRIES)
            (0 until arr.length()).map { ProxyConfig.fromJson(arr.getJSONObject(it)) ?: error("Invalid config") }
        } finally { plain.fill(0) }
    }.getOrNull()

    fun isVault(bytes: ByteArray) = bytes.size >= 4 && (String(bytes, 0, 4, Charsets.US_ASCII) == MAGIC || net.gozar.app.security.vault.VaultFormat.isV2(bytes))

    fun load(ctx: Context, password: CharArray): List<ProxyConfig>? =
        if (!exists(ctx)) emptyList() else open(net.gozar.app.security.vault.VaultFileStore.read(file(ctx)), password)

    fun save(ctx: Context, configs: List<ProxyConfig>, password: CharArray) {
        val bytes=seal(configs,password)
        net.gozar.app.security.vault.VaultFileStore.save(file(ctx),bytes) { written ->
            val reopened=open(written,password) ?: error("Vault verification failed")
            require(reopened.map { it.toJson().toString() } == configs.map { it.toJson().toString() })
        }
    }

    /** Explicit, non-destructive migration: old file is replaced only after the new file reopens. */
    fun migrate(ctx: Context, password: CharArray) {
        val old=net.gozar.app.security.vault.VaultFileStore.read(file(ctx))
        require(!net.gozar.app.security.vault.VaultFormat.isV2(old))
        val configs=openLegacy(old,password) ?: throw net.gozar.app.security.vault.VaultException(net.gozar.app.security.vault.VaultException.Kind.WRONG_PASSWORD_OR_DAMAGED_KEY)
        save(ctx,configs,password)
    }

    fun raw(ctx: Context): ByteArray? = if (exists(ctx)) net.gozar.app.security.vault.VaultFileStore.read(file(ctx)) else null

    fun delete(ctx: Context) { file(ctx).delete() }
}

package net.gozar.app.security.vault

import net.gozar.app.ProxyConfig
import java.security.MessageDigest

object VaultFingerprint {
    /** Compare normalized connection semantics, excluding display/history and source bookkeeping. */
    fun of(config: ProxyConfig): String {
        val o=config.toJson()
        listOf("id","name","source","ping","lastUsed","addedAt","createdAt","updatedAt","country","favorite").forEach(o::remove)
        return MessageDigest.getInstance("SHA-256").digest(VaultFormat.canonical(o).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

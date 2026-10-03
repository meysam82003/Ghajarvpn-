package net.gozar.app.security.vault

import net.gozar.app.ProxyConfig
import net.gozar.app.Safebox
import java.io.File
import java.util.UUID

/** All commits verify decrypted contents before replacement. Caller owns its unlock session/password. */
class VaultRepository(private val file: File) {
    enum class DuplicateChoice { KEEP_EXISTING, REPLACE, KEEP_BOTH }
    fun exists()=file.exists()
    fun legacy()=exists() && !VaultFormat.isV2(VaultFileStore.read(file))
    fun encryptedBackup(): ByteArray? = if(exists()) VaultFileStore.read(file) else null
    fun open(password: CharArray): List<VaultEntry> = if(exists()) decode(VaultFileStore.read(file),password) else emptyList()
    fun decode(bytes: ByteArray,password: CharArray): List<VaultEntry> {
        if(VaultFormat.isV2(bytes)) return VaultCrypto.open(bytes,password).also { it.forEach(::config) }
        if(!Safebox.isVault(bytes)) throw VaultException(VaultException.Kind.CORRUPT)
        val old=Safebox.openLegacy(bytes,password) ?: throw VaultException(VaultException.Kind.WRONG_PASSWORD_OR_DAMAGED_KEY)
        val now=System.currentTimeMillis()
        return old.map { c -> VaultEntry(id=UUID.nameUUIDFromBytes(("GSB1/"+c.id).toByteArray()).toString(),displayName=c.name,protocol=c.protocol,payload=c.toJson().toString(),createdAt=now) }
    }
    @Synchronized fun save(entries: List<VaultEntry>,password: CharArray) {
        entries.forEach(::config)
        val bytes=VaultCrypto.seal(entries,password)
        VaultFileStore.save(file,bytes) { stored ->
            val verified=VaultCrypto.open(stored,password)
            require(verified.map { it.id to VaultFormat.canonical(it.plain()) } == entries.map { it.id to VaultFormat.canonical(it.plain()) })
        }
    }
    fun migrate(password: CharArray) { require(legacy()); val entries=open(password); save(entries,password) }
    fun export(entries: List<VaultEntry>,password: CharArray): ByteArray {
        if(entries.any { !it.policy.allowReExport }) throw VaultException(VaultException.Kind.POLICY)
        entries.forEach(::config)
        return VaultCrypto.seal(entries,password).also { require(VaultCrypto.open(it,password).map { e->e.id }==entries.map { e->e.id }) }
    }
    fun merge(existing: List<VaultEntry>,incoming: List<VaultEntry>,choose:(VaultEntry,VaultEntry)->DuplicateChoice): List<VaultEntry> {
        val result=existing.toMutableList()
        incoming.forEach { entry ->
            val fingerprint=VaultFingerprint.of(config(entry))
            val idx=result.indexOfFirst { it.id==entry.id || VaultFingerprint.of(config(it))==fingerprint }
            if(idx<0) result+=entry else when(choose(result[idx],entry)) {
                DuplicateChoice.KEEP_EXISTING -> Unit
                DuplicateChoice.REPLACE -> {
                    require(!result[idx].policy.readOnly) { "Read-only entry cannot be replaced" }
                    result[idx]=entry
                }
                DuplicateChoice.KEEP_BOTH -> result+=copyIdentity(entry,UUID.randomUUID().toString())
            }
        }
        require(result.size<=VaultFormat.MAX_ENTRIES)
        return result
    }
    /** Caller invokes source deletion only after this returns successfully. */
    fun saveThenDeleteSources(entries: List<VaultEntry>,password: CharArray,deleteSources:()->Unit) { save(entries,password); deleteSources() }
    companion object {
        fun config(entry: VaultEntry): ProxyConfig = try {
            ProxyConfig.fromJson(net.gozar.app.configtoolkit.BoundedJson.objectValue(entry.payload))
                ?: throw VaultException(VaultException.Kind.INVALID_CONFIG)
        } catch(e: VaultException) { throw e } catch(_:Exception) { throw VaultException(VaultException.Kind.INVALID_CONFIG) }
        private fun copyIdentity(e: VaultEntry,id: String)=VaultEntry(id,e.displayName,e.protocol,e.payload,e.createdAt,e.updatedAt,e.expiresAt,e.note,e.tags,e.favorite,e.sourceType,e.privacy,e.policy,e.quota)
    }
}

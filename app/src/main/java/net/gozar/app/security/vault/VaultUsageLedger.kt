package net.gozar.app.security.vault

import java.io.File
import org.json.JSONObject

/** Device-local accounting only. Private storage, atomic updates; clear-data/root can reset it. */
class VaultUsageLedger(private val file: File) {
    // Different callers may open separate repositories for the same file. The complete
    // read/modify/verify/replace transaction, not just rename, must share a process lock.
    companion object { private val transactionLock=Any() }
    fun read(id: String): VaultQuotaPolicy.Usage = synchronized(transactionLock) {
        readAll().optJSONObject(id)?.let(::usage) ?: VaultQuotaPolicy.Usage()
    }
    fun record(id: String, uploadDelta: Long, downloadDelta: Long, now: Long,
        imported: Boolean = false, connected: Boolean = false): VaultQuotaPolicy.Usage = synchronized(transactionLock) {
        require(uploadDelta>=0 && downloadDelta>=0 && now>=0 && id.length in 1..128)
        val all=readAll(); val old=all.optJSONObject(id)?.let(::usage) ?: VaultQuotaPolicy.Usage()
        val next=VaultQuotaPolicy.Usage(VaultQuotaPolicy.saturatedAdd(old.upload,uploadDelta),VaultQuotaPolicy.saturatedAdd(old.download,downloadDelta),
            old.firstImportAt ?: now.takeIf { imported },old.firstConnectAt ?: now.takeIf { connected })
        all.put(id,JSONObject().put("tx",next.upload).put("rx",next.download).put("imported",next.firstImportAt).put("connected",next.firstConnectAt).put("updatedAt",now))
        val raw=VaultFormat.canonical(all).toByteArray(Charsets.UTF_8)
        VaultFileStore.save(file,raw) { require(VaultFormat.canonical(VaultFormat.json(it))==VaultFormat.canonical(all)) }
        next
    }
    private fun readAll(): JSONObject = if(file.exists()) VaultFormat.json(VaultFileStore.read(file)) else JSONObject()
    private fun usage(o: JSONObject) = VaultQuotaPolicy.Usage(o.exactLong("tx"),o.exactLong("rx"),o.longOrNull("imported"),o.longOrNull("connected"))
}

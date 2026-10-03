package net.gozar.app.security.vault

import java.io.File
import org.json.JSONObject

/** Device-local accounting only. Private storage, atomic updates; clear-data/root can reset it. */
class VaultUsageLedger(private val file: File) {
    // Different callers may open separate repositories for the same file. The complete
    // read/modify/verify/replace transaction, not just rename, must share a process lock.
    companion object {
        private val transactionLock=Any()
        private val processIdentity=java.util.UUID.randomUUID().toString()
        private val activeSessions=mutableSetOf<String>()
    }
    private fun sessionKey(id: String)=file.absolutePath+":"+id
    fun begin(id: String) = synchronized(transactionLock) {
        val all=readAll();val o=all.optJSONObject(id) ?: JSONObject().put("tx",0).put("rx",0)
        require(!usage(o,id).uncertain && sessionKey(id) !in activeSessions) { "Accounting recovery required" }
        o.put("sessionOwner",processIdentity);all.put(id,o)
        VaultFileStore.save(file,VaultFormat.canonical(all).toByteArray()) { require(VaultFormat.canonical(VaultFormat.json(it))==VaultFormat.canonical(all)) }
        activeSessions.add(sessionKey(id))
    }
    fun finish(id: String, verified: Boolean) = synchronized(transactionLock) {
        try {
            val all=readAll();val o=all.optJSONObject(id) ?: error("Missing accounting record")
            o.remove("sessionOwner");o.put("uncertain",!verified);all.put(id,o)
            VaultFileStore.save(file,VaultFormat.canonical(all).toByteArray()) { require(VaultFormat.canonical(VaultFormat.json(it))==VaultFormat.canonical(all)) }
        } finally { activeSessions.remove(sessionKey(id)) }
    }
    fun read(id: String): VaultQuotaPolicy.Usage = synchronized(transactionLock) {
        readAll().optJSONObject(id)?.let { usage(it,id) } ?: VaultQuotaPolicy.Usage()
    }
    fun record(id: String, uploadDelta: Long, downloadDelta: Long, now: Long,
        imported: Boolean = false, connected: Boolean = false): VaultQuotaPolicy.Usage = synchronized(transactionLock) {
        require(uploadDelta>=0 && downloadDelta>=0 && now>=0 && id.length in 1..128)
        val all=readAll(); val old=all.optJSONObject(id)?.let { usage(it,id) } ?: VaultQuotaPolicy.Usage()
        val next=VaultQuotaPolicy.Usage(VaultQuotaPolicy.saturatedAdd(old.upload,uploadDelta),VaultQuotaPolicy.saturatedAdd(old.download,downloadDelta),
            old.firstImportAt ?: now.takeIf { imported },old.firstConnectAt ?: now.takeIf { connected },old.uncertain)
        all.put(id,JSONObject().put("tx",next.upload).put("rx",next.download).put("imported",next.firstImportAt).put("connected",next.firstConnectAt).put("updatedAt",now).put("uncertain",next.uncertain).put("sessionOwner",all.optJSONObject(id)?.opt("sessionOwner")))
        val raw=VaultFormat.canonical(all).toByteArray(Charsets.UTF_8)
        VaultFileStore.save(file,raw) { require(VaultFormat.canonical(VaultFormat.json(it))==VaultFormat.canonical(all)) }
        next
    }
    fun snapshot(): JSONObject = synchronized(transactionLock) { readAll().also { all -> all.keys().forEach { id ->
        val o=all.getJSONObject(id);val verified=usage(o,id);o.remove("sessionOwner");o.put("uncertain",verified.uncertain)
    } } }
    /** Restore may only increase totals and move activation earlier, never renew quota. */
    fun mergeSnapshot(incoming: JSONObject) = synchronized(transactionLock) {
        val all = readAll()
        incoming.keys().forEach { id ->
            require(id.length in 1..128)
            val src = usage(incoming.getJSONObject(id),id); val old = all.optJSONObject(id)?.let { usage(it,id) } ?: VaultQuotaPolicy.Usage()
            fun earliest(a: Long?, b: Long?) = listOfNotNull(a,b).minOrNull()
            all.put(id, JSONObject().put("tx", maxOf(old.upload,src.upload)).put("rx",maxOf(old.download,src.download))
                .put("imported",earliest(old.firstImportAt,src.firstImportAt)).put("connected",earliest(old.firstConnectAt,src.firstConnectAt)).put("uncertain",old.uncertain || src.uncertain))
        }
        val bytes = VaultFormat.canonical(all).toByteArray(Charsets.UTF_8)
        VaultFileStore.save(file,bytes) { require(VaultFormat.canonical(VaultFormat.json(it)) == VaultFormat.canonical(all)) }
    }
    private fun readAll(): JSONObject = if(file.exists()) VaultFormat.json(VaultFileStore.read(file)) else JSONObject()
    private fun usage(o: JSONObject,id: String) = VaultQuotaPolicy.Usage(o.exactLong("tx"),o.exactLong("rx"),o.longOrNull("imported"),o.longOrNull("connected"),
        o.optBoolean("uncertain") || (o.has("sessionOwner") && !o.isNull("sessionOwner") &&
            (o.optString("sessionOwner")!=processIdentity || sessionKey(id) !in activeSessions)))
}

package net.gozar.app.security.vault

import java.io.File
import java.util.Base64
import org.json.JSONObject

/** Carried inside the authenticated, password-protected full backup. No vault password is serialized. */
object VaultBackup {
    fun capture(vault: File, ledger: VaultUsageLedger): JSONObject? {
        if (!vault.exists()) return null
        val blob = VaultFileStore.read(vault)
        require(net.gozar.app.Safebox.isVault(blob))
        return JSONObject().put("version",1).put("blob",Base64.getEncoder().encodeToString(blob)).put("usage",ledger.snapshot())
    }
    fun verify(snapshot: JSONObject, password: CharArray): ByteArray {
        require(snapshot.getInt("version") == 1)
        val encoded = snapshot.getString("blob")
        require(encoded.length <= (VaultFormat.MAX_FILE.toLong()*4/3+4))
        val blob = Base64.getDecoder().decode(encoded)
        VaultRepository(File("unused")).decode(blob,password)
        val usage = snapshot.getJSONObject("usage")
        usage.keys().forEach { id ->
            require(id.length in 1..128)
            val o=usage.getJSONObject(id)
            require(o.exactLong("tx")>=0 && o.exactLong("rx")>=0)
            require(o.longOrNull("imported")?.let { it>=0 } != false && o.longOrNull("connected")?.let { it>=0 } != false)
        }
        return blob
    }
    /** Verify all input first; durable monotonic ledger precedes the atomic blob replacement.
     * A crash can conservatively increase usage but cannot restore a lower quota balance.
     */
    fun restore(snapshot: JSONObject, password: CharArray, vault: File, ledger: VaultUsageLedger) {
        val blob = verify(snapshot,password)
        ledger.mergeSnapshot(snapshot.getJSONObject("usage"))
        VaultFileStore.save(vault,blob) { require(it.contentEquals(blob)); VaultRepository(vault).decode(it,password) }
    }
}

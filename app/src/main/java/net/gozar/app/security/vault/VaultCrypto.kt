package net.gozar.app.security.vault

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Independent GSB2 implementation. Does not import ZSX's public/open key or its format. */
object VaultCrypto {
    private val random = SecureRandom()
    internal fun randomBytes(n: Int) = ByteArray(n).also(random::nextBytes)
    internal fun crypt(encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, bytes: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 12)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(if(encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce))
            updateAAD(aad); doFinal(bytes)
        }
    }
    fun seal(entries: List<VaultEntry>, password: CharArray, parameters: VaultKdf.Parameters = VaultKdf.Parameters()): ByteArray {
        require(entries.size <= VaultFormat.MAX_ENTRIES && entries.map { it.id }.distinct().size == entries.size)
        val master=randomBytes(32); val salt=randomBytes(16); val wrapNonce=randomBytes(12)
        val vaultId=UUID.randomUUID().toString()
        val header=JSONObject().put("vaultId",vaultId).put("cipher","AES-256-GCM").put("kdf","Argon2id-19")
            .put("salt",VaultFormat.encode(salt)).put("memoryKiB",parameters.memoryKiB).put("iterations",parameters.iterations)
            .put("parallelism",parameters.parallelism).put("wrapNonce",VaultFormat.encode(wrapNonce))
        val rawHeader=VaultFormat.canonical(header).toByteArray(Charsets.UTF_8)
        val prefix=VaultFormat.magic+byteArrayOf(1)+ByteBuffer.allocate(4).putInt(rawHeader.size).array()+rawHeader
        var kek: ByteArray? = null
        try {
            val wrappingKey=VaultKdf.passwordKey(password,salt,parameters).also { kek=it }
            val wrapped=crypt(true,wrappingKey,wrapNonce,prefix,master)
            val rows=JSONArray(); val nonces=hashSetOf<String>()
            entries.forEach { entry ->
                val descriptor=JSONObject().put("id",entry.id).put("version",1).put("privacy",entry.privacy.name)
                    .put("metadata",if(entry.privacy==MetadataPrivacy.STANDARD) entry.metadata() else JSONObject())
                var nonce: ByteArray
                do { nonce=randomBytes(12) } while(!nonces.add(VaultFormat.encode(nonce)))
                val key=VaultKdf.derive(master,vaultId.toByteArray(),"GSB2/entry/v1/"+entry.id)
                val plain=VaultFormat.canonical(entry.plain()).toByteArray(Charsets.UTF_8)
                try {
                    require(plain.size+16 <= 2*1024*1024) { "Encoded vault entry size limit" }
                    val aad=entryAad(vaultId,descriptor)
                    rows.put(descriptor.put("nonce",VaultFormat.encode(nonce)).put("cipher",VaultFormat.encode(crypt(true,key,nonce,aad,plain))))
                } finally { key.fill(0); plain.fill(0) }
            }
            val body=VaultFormat.canonical(JSONObject().put("entries",rows)).toByteArray(Charsets.UTF_8)
            require(body.size <= net.gozar.app.configtoolkit.BoundedJson.MAX_BYTES)
            val authenticated=prefix+wrapped+body
            require(authenticated.size+32 <= VaultFormat.MAX_FILE)
            val macKey=VaultKdf.derive(master,vaultId.toByteArray(),"GSB2/manifest/v1")
            return try { authenticated+VaultKdf.hmac(macKey,authenticated) } finally { macKey.fill(0) }
        } finally { master.fill(0); kek?.fill(0); salt.fill(0) }
    }
    fun open(bytes: ByteArray, password: CharArray): List<VaultEntry> {
        val envelope=VaultFormat.parse(bytes); val h=envelope.header
        var master: ByteArray?=null
        try {
            val id=h.getString("vaultId"); require(UUID.fromString(id).toString()==id)
            val params=VaultKdf.Parameters(h.exactInt("memoryKiB"),h.exactInt("iterations"),h.exactInt("parallelism"))
            params.validate()
            val salt=VaultFormat.decode(h.getString("salt"),16).also { require(it.size==16) }
            val nonce=VaultFormat.decode(h.getString("wrapNonce"),12).also { require(it.size==12) }
            val kek=VaultKdf.passwordKey(password,salt,params)
            master=try { crypt(false,kek,nonce,envelope.prefix,envelope.wrapped) }
                catch (_: AEADBadTagException) { throw VaultException(VaultException.Kind.WRONG_PASSWORD_OR_DAMAGED_KEY) }
                finally { kek.fill(0) }
            val macKey=VaultKdf.derive(master,id.toByteArray(),"GSB2/manifest/v1")
            val ok=try { MessageDigest.isEqual(envelope.mac,VaultKdf.hmac(macKey,envelope.prefix+envelope.wrapped+envelope.body)) } finally { macKey.fill(0) }
            if(!ok) throw VaultException(VaultException.Kind.TAMPERED)
            val entryMaster=master
            val rows=VaultFormat.json(envelope.body).getJSONArray("entries"); require(rows.length() <= VaultFormat.MAX_ENTRIES)
            val ids=hashSetOf<String>(); val nonces=hashSetOf<String>()
            return (0 until rows.length()).map { i ->
                val row=rows.getJSONObject(i); val entryId=row.getString("id")
                require(ids.add(entryId) && row.exactInt("version")==1)
                val privacy=MetadataPrivacy.valueOf(row.getString("privacy"))
                val rowNonce=VaultFormat.decode(row.getString("nonce"),12).also { require(it.size==12 && nonces.add(VaultFormat.encode(it))) }
                val cipher=VaultFormat.decode(row.getString("cipher"),2*1024*1024)
                val descriptor=JSONObject(row.toString()).apply { remove("nonce"); remove("cipher") }
                val key=VaultKdf.derive(entryMaster,id.toByteArray(),"GSB2/entry/v1/"+entryId)
                val plain=try { crypt(false,key,rowNonce,entryAad(id,descriptor),cipher) } finally { key.fill(0) }
                try {
                    val entry=VaultEntry.read(entryId,privacy,VaultFormat.json(plain))
                    require(if(privacy==MetadataPrivacy.PRIVATE) row.getJSONObject("metadata").length()==0
                        else VaultFormat.canonical(row.getJSONObject("metadata"))==VaultFormat.canonical(entry.metadata()))
                    entry
                } finally { plain.fill(0) }
            }
        } catch (e: VaultException) { throw e }
        catch (_: AEADBadTagException) { throw VaultException(VaultException.Kind.TAMPERED) }
        catch (_: Exception) { throw VaultException(VaultException.Kind.CORRUPT) }
        finally { master?.fill(0) }
    }
    private fun entryAad(vaultId: String, descriptor: JSONObject) =
        ("GSB2/1/AES-256-GCM/"+vaultId+"/"+VaultFormat.canonical(descriptor)).toByteArray(Charsets.UTF_8)
}

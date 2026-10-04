package net.gozar.app.security.vault

import net.gozar.app.ConfigParser
import net.gozar.app.Safebox
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

class VaultV2Test {
    private val password="unit-test-password".toCharArray()
    private val params=VaultKdf.Parameters(32768,2,1)
    private fun entry(privacy: MetadataPrivacy=MetadataPrivacy.PRIVATE, quota: VaultQuota=VaultQuota(),expires:Long?=null)=VaultEntry(
        displayName="نمونهٔ محرمانه",protocol="vless",payload="{\"credential\":\"synthetic-fixture\"}",createdAt=1000,privacy=privacy,quota=quota,expiresAt=expires)
    private fun fail(block:()->Unit): Throwable = runCatching(block).exceptionOrNull() ?: throw AssertionError("Expected rejection")
    @Test fun emptyAndMultipleEntriesReopenWithIndependentNoncesAndSalts() {
        assertTrue(VaultCrypto.open(VaultCrypto.seal(emptyList(),password,params),password).isEmpty())
        val entries=listOf(entry(),entry());val a=VaultCrypto.seal(entries,password,params);val b=VaultCrypto.seal(entries,password,params)
        assertFalse(a.contentEquals(b));val opened=VaultCrypto.open(a,password)
        assertEquals(entries.map { it.payload },opened.map { it.payload }); assertEquals(entries.map { it.id },opened.map { it.id })
        val ea=VaultFormat.parse(a);val eb=VaultFormat.parse(b)
        assertNotEquals(ea.header.getString("salt"),eb.header.getString("salt"))
        val rows=VaultFormat.json(ea.body).getJSONArray("entries")
        assertNotEquals(rows.getJSONObject(0).getString("nonce"),rows.getJSONObject(1).getString("nonce"))
    }
    @Test fun wrongPasswordTamperingTruncationVersionAndKdfBounds() {
        val bytes=VaultCrypto.seal(listOf(entry()),password,params)
        assertEquals(VaultException.Kind.WRONG_PASSWORD_OR_DAMAGED_KEY,(fail { VaultCrypto.open(bytes,"wrong".toCharArray()) } as VaultException).kind)
        val bad=bytes.clone();bad[bad.size-40]=(bad[bad.size-40].toInt() xor 1).toByte()
        assertEquals(VaultException.Kind.TAMPERED,(fail { VaultCrypto.open(bad,password) } as VaultException).kind)
        fail { VaultCrypto.open(bytes.copyOf(20),password) }
        val version=bytes.clone();version[4]=99
        assertEquals(VaultException.Kind.UNSUPPORTED_VERSION,(fail { VaultCrypto.open(version,password) } as VaultException).kind)
        fail { VaultKdf.Parameters(Int.MAX_VALUE,Int.MAX_VALUE,8).validate() }
        val header=String(bytes,Charsets.ISO_8859_1).replace("32768","32769").toByteArray(Charsets.ISO_8859_1)
        fail { VaultCrypto.open(header,password) }
    }
    @Test fun maximumPlaintextPayloadSurvivesBase64Expansion() {
        val entry=VaultEntry(displayName="large",protocol="test",payload="x".repeat(1024*1024),createdAt=0)
        assertEquals(entry.payload,VaultCrypto.open(VaultCrypto.seal(listOf(entry),password,params),password).single().payload)
        fail { net.gozar.app.configtoolkit.BoundedJson.objectValue("{\"x\":\""+"x".repeat(1024*1024+1)+"\"}") }
    }
    @Test fun privateAndStandardMetadataNeverExposePayloadAndPolicySurvives() {
        for(privacy in MetadataPrivacy.entries) {
            val e=entry(privacy);val bytes=VaultCrypto.seal(listOf(e),password,params)
            val wire=String(bytes,Charsets.UTF_8)
            assertFalse(wire.contains("synthetic-fixture"))
            assertEquals(privacy==MetadataPrivacy.STANDARD,wire.contains(e.displayName))
            assertEquals(privacy,VaultCrypto.open(bytes,password).single().privacy)
        }
    }
    @Test fun aadAndPerEntryKeyIsolation() {
        val master=ByteArray(32){it.toByte()};val a=VaultKdf.derive(master,byteArrayOf(1),"GSB2/entry/v1/a");val b=VaultKdf.derive(master,byteArrayOf(1),"GSB2/entry/v1/b")
        assertFalse(a.contentEquals(b));val nonce=ByteArray(12){it.toByte()};val cipher=VaultCrypto.crypt(true,a,nonce,"a".toByteArray(),"fixture".toByteArray())
        fail { VaultCrypto.crypt(false,b,nonce,"a".toByteArray(),cipher) }
        fail { VaultCrypto.crypt(false,a,nonce,"b".toByteArray(),cipher) }
    }
    @Test fun atomicSaveVerifiesBeforeReplacingAndFailedMigrationKeepsGsb1() {
        val dir=Files.createTempDirectory("vault-test").toFile();val file=java.io.File(dir,"vault.gsb")
        try {
            val old=legacy();file.writeBytes(old)
            val configs=Safebox.open(old,password)!!;val upgraded=Safebox.seal(configs,password)
            fail { VaultFileStore.save(file,upgraded) { error("simulated verify failure") } }
            assertArrayEquals(old,file.readBytes())
            VaultFileStore.save(file,upgraded) { assertEquals(configs,Safebox.open(it,password)) }
            assertTrue(VaultFormat.isV2(file.readBytes()));assertEquals(configs,Safebox.open(file.readBytes(),password))
            assertEquals(1,dir.listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }
    @Test fun gsb1StillOpensButWrongPasswordAndTamperFail() {
        val old=legacy();assertNotNull(Safebox.open(old,password));assertNull(Safebox.open(old,"wrong".toCharArray()))
        old[old.lastIndex]=(old.last().toInt() xor 1).toByte();assertNull(Safebox.open(old,password))
    }
    @Test fun localQuotaExactBoundariesOverflowAndTimeModes() {
        val gib=1024L*1024*1024;val e=entry(quota=VaultQuota(QuotaMode.LOCAL,gib))
        for(n in listOf(0L,gib/2,gib-1))assertTrue(VaultQuotaPolicy.local(e,VaultQuotaPolicy.Usage(n),1001).connectable)
        for(n in listOf(gib,gib+1,Long.MAX_VALUE))assertEquals(EntitlementStatus.QUOTA_EXHAUSTED,VaultQuotaPolicy.local(e,VaultQuotaPolicy.Usage(n,1),1001).status)
        for(mode in ActivationMode.entries) {
            val timed=entry(quota=VaultQuota(QuotaMode.LOCAL,gib,1000,mode))
            val usage=VaultQuotaPolicy.Usage(firstImportAt=2000,firstConnectAt=3000)
            val end=when(mode){ActivationMode.CREATED->2000L;ActivationMode.IMPORTED->3000L;ActivationMode.FIRST_CONNECT->4000L}
            assertTrue(VaultQuotaPolicy.local(timed,usage,end-1).connectable)
            assertEquals(EntitlementStatus.EXPIRED,VaultQuotaPolicy.local(timed,usage,end).status)
        }
        assertEquals(EntitlementStatus.EXPIRED,VaultQuotaPolicy.local(entry(expires=2000),VaultQuotaPolicy.Usage(),2000).status)
    }
    @Test fun ledgerSurvivesRestartAndNeverResetsFirstActivation() {
        val dir=Files.createTempDirectory("quota-test").toFile();val file=java.io.File(dir,"ledger.json")
        try {
            VaultUsageLedger(file).record("a",90,0,1000,imported=true)
            VaultUsageLedger(file).record("a",10,0,2000,connected=true)
            val usage=VaultUsageLedger(file).record("a",0,0,3000,imported=true,connected=true)
            assertEquals(100,usage.upload);assertEquals(1000L,usage.firstImportAt);assertEquals(2000L,usage.firstConnectAt)
            assertEquals(EntitlementStatus.QUOTA_EXHAUSTED,VaultQuotaPolicy.local(entry(quota=VaultQuota(QuotaMode.LOCAL,100)),usage,3000).status)
        } finally { dir.deleteRecursively() }
    }
    @Test fun concurrentLedgerWritersCannotLoseUsageAndFractionsAreRejected() {
        val dir=Files.createTempDirectory("quota-concurrency").toFile();val file=java.io.File(dir,"ledger.json")
        try {
            val writers=(1..4).map { Thread { repeat(8) { VaultUsageLedger(file).record("entry",1,2,1000) } } }
            writers.forEach { it.start() };writers.forEach { it.join() }
            assertEquals(32,VaultUsageLedger(file).read("entry").upload)
            assertEquals(64,VaultUsageLedger(file).read("entry").download)
            val quota=VaultQuota(QuotaMode.LOCAL,100).toJson()
            for(value in listOf<Any>(1.5,"100",java.math.BigDecimal("9223372036854775808"))) {
                quota.put("bytes",value);fail { VaultQuota.fromJson(quota) }
            }
            fail { VaultQuota(QuotaMode.NONE,100) }
        } finally { dir.deleteRecursively() }
    }
    @Test fun parentReservationDoesNotInventIndependentServerQuota() {
        val g=1024L*1024*1024;var p=ParentAllowance(20*g,0,0)
        repeat(3){p=p.reserve(5*g)};assertEquals(5*g,p.availableToAllocate);fail { p.reserve(10*g) }
        assertEquals(0,p.reserve(5*g).availableToAllocate)
        assertFalse(VaultQuotaPolicy.local(entry(quota=VaultQuota(QuotaMode.SERVER,5*g,entitlementId="fixture")),VaultQuotaPolicy.Usage(),1000).connectable)
    }
    @Test fun signedServerStatusBindingReplayAndTamper() {
        val private=Ed25519PrivateKeyParameters(java.security.SecureRandom());val public=private.generatePublicKey().encoded
        val verifier=EntitlementVerifier(mapOf("fixture" to public))
        fun signed(o:JSONObject):SignedEntitlement {
            val data=VaultFormat.canonical(o).toByteArray();val signer=Ed25519Signer().apply{init(true,private)}
            val bound="GhajarEntitlement/v1\u0000".toByteArray()+data;signer.update(bound,0,bound.size)
            return SignedEntitlement("fixture",data,signer.generateSignature())
        }
        val o=JSONObject().put("version",1).put("entitlementId","e").put("configFingerprint","c").put("deviceFingerprint","d")
            .put("issuedAt",1000).put("validUntil",2000).put("sequence",3).put("quotaBytes",100).put("usedBytes",99).put("status","ACTIVE")
        assertEquals(EntitlementStatus.ACTIVE,verifier.verify(signed(o),"e","c","d",1500).status)
        for(status in EntitlementStatus.entries) { o.put("status",status.name);assertEquals(status,verifier.verify(signed(o),"e","c","d",1500).status) }
        o.put("status","ACTIVE").put("usedBytes",100);assertEquals(EntitlementStatus.QUOTA_EXHAUSTED,verifier.verify(signed(o),"e","c","d",1500).status)
        val s=signed(o);fail { verifier.verify(s,"e","c","other-device",1500) };fail { verifier.verify(s,"e","c","d",2000) };fail { verifier.verify(s,"e","c","d",1500,4) }
        for(field in listOf("quotaBytes","usedBytes","expiresAt")) {
            val forged=JSONObject(String(s.payload)).put(field,999999)
            fail { verifier.verify(SignedEntitlement(s.keyId,VaultFormat.canonical(forged).toByteArray(),s.signature),"e","c","d",1500) }
        }
    }
    @Test fun fingerprintIgnoresNamesButNotCredentials() {
        val a=ConfigParser.parse("vless://00000000-0000-0000-0000-000000000001@example.org:443#one")!!
        assertEquals(VaultFingerprint.of(a),VaultFingerprint.of(a.copy(id="other",name="two")))
        assertNotEquals(VaultFingerprint.of(a),VaultFingerprint.of(a.copy(uuid="different")))
    }
    @Test fun repositoryMergeReadOnlyAndVerifiedDeletion() {
        val dir=Files.createTempDirectory("vault-repo").toFile()
        try {
            val repo=VaultRepository(java.io.File(dir,"vault.gsb"))
            val cfg=ConfigParser.parse("ssh://u:fixture@example.org#one")!!
            val a=VaultEntry(displayName="one",protocol="ssh",payload=cfg.toJson().toString(),createdAt=1000)
            val b=VaultEntry(displayName="two",protocol="ssh",payload=cfg.copy(name="two",id="other").toJson().toString(),createdAt=1000)
            assertEquals(a.id,repo.merge(listOf(a),listOf(b)){_,_->VaultRepository.DuplicateChoice.KEEP_EXISTING}.single().id)
            assertEquals(b.id,repo.merge(listOf(a),listOf(b)){_,_->VaultRepository.DuplicateChoice.REPLACE}.single().id)
            assertEquals(2,repo.merge(listOf(a),listOf(b)){_,_->VaultRepository.DuplicateChoice.KEEP_BOTH}.size)
            var deleted=false;repo.saveThenDeleteSources(listOf(a),password){deleted=true};assertTrue(deleted)
            assertEquals(a.id,repo.open(password).single().id)
            assertEquals(a.id,repo.decode(repo.export(listOf(a),"different".toCharArray()),"different".toCharArray()).single().id)
            val locked=VaultEntry(displayName="locked",protocol="ssh",payload=cfg.toJson().toString(),createdAt=1000,policy=VaultPolicy(readOnly=true,allowReExport=false))
            fail { repo.export(listOf(locked),password) }
            fail { repo.merge(listOf(locked),listOf(a)){_,_->VaultRepository.DuplicateChoice.REPLACE} }
            repo.save(emptyList(),password);assertTrue(repo.open(password).isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun memorySessionExpiresAndProcessRestartStartsLocked() {
        var now=0L;val session=VaultSession { now };session.autoLock=VaultSession.AutoLock.SECONDS_30;session.unlock(listOf(entry()))
        now=29999;assertEquals(1,session.snapshot().size);now=30000;fail { session.snapshot() }
        session.unlock(listOf(entry()));session.background();fail { session.snapshot() }
        session.autoLock=VaultSession.AutoLock.SCREEN_LOCK;session.unlock(listOf(entry()));session.background();assertEquals(1,session.snapshot().size)
        session.screenLocked();fail { session.snapshot() };fail { VaultSession().snapshot() }
    }
    private fun legacy():ByteArray {
        // Historical writer exists only in tests; production never writes GSB1.
        val salt=ByteArray(16){it.toByte()};val nonce=ByteArray(12){(it+16).toByte()}
        val spec=PBEKeySpec(password,salt,200000,256);val key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded;spec.clearPassword()
        val cfg=ConfigParser.parse("ssh://fixture:synthetic@example.org#legacy")!!
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD("GSB1".toByteArray())
        return "GSB1".toByteArray()+salt+nonce+cipher.doFinal(JSONArray().put(cfg.toJson()).toString().toByteArray())
    }
}

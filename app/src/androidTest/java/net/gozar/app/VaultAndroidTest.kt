package net.gozar.app
import androidx.test.platform.app.InstrumentationRegistry
import net.gozar.app.security.vault.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class VaultAndroidTest {
 @Test fun legacyMigrationBackupAndQuotaOnAndroidStorage() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val directory=File(context.cacheDir,"vault-test-"+java.util.UUID.randomUUID()).apply { mkdirs() }
  try {
   val password="instrumentation-only".toCharArray()
   val salt=ByteArray(16){it.toByte()};val nonce=ByteArray(12){(it+16).toByte()}
   val spec=PBEKeySpec(password,salt,200000,256)
   val key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded;spec.clearPassword()
   val config=ProxyConfig(name="fixture",protocol="socks",address="192.0.2.1",port=1080)
   val cipher=Cipher.getInstance("AES/GCM/NoPadding")
   cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD("GSB1".toByteArray())
   val file=File(directory,"vault.gsb")
   file.writeBytes("GSB1".toByteArray()+salt+nonce+cipher.doFinal(JSONArray().put(config.toJson()).toString().toByteArray()))
   val repo=VaultRepository(file);assertTrue(repo.legacy());repo.migrate(password);assertFalse(repo.legacy())
   assertEquals(config, VaultRepository.config(repo.open(password).single()))
   val entry=VaultEntry(displayName="quota",protocol="socks",payload=config.toJson().toString(),createdAt=0,quota=VaultQuota(QuotaMode.LOCAL,100))
   repo.save(listOf(entry),password)
   val ledger=VaultUsageLedger(File(directory,"usage"));val session=VaultMeteredSession(entry,ledger,1000)
   assertTrue(session.sample(40,59,1000,generation="first").connectable)
   assertEquals(EntitlementStatus.QUOTA_EXHAUSTED,session.sample(41,59,1000,generation="first").status)
   session.close();assertTrue(runCatching {VaultRuntime.issue(entry,ledger,1000)}.isFailure)
   val backup=VaultBackup.capture(file,ledger)!!
   VaultBackup.restore(backup,password,file,ledger)
   assertEquals(100L,ledger.read(entry.usageId).bytes(Accounting.TOTAL))
   assertTrue(runCatching {VaultRuntime.issue(entry,ledger,1000)}.isFailure)
  } finally { directory.deleteRecursively() }
 }
}

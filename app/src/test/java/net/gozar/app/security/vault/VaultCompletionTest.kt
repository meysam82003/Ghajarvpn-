package net.gozar.app.security.vault

import net.gozar.app.ProxyConfig
import net.gozar.app.engine.SingBoxUsage
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneId

class VaultCompletionTest {
    private fun entry()=VaultEntry(displayName="test",protocol="socks",payload=ProxyConfig(name="test",protocol="socks",address="192.0.2.1",port=1080).toJson().toString(),createdAt=1000,quota=VaultQuota(QuotaMode.LOCAL,10000))
    @Test fun reconnectPolicyAlsoAppliesToManualNewGrants() {
        val d=Files.createTempDirectory("vault-reconnect").toFile()
        try {
            val original=entry()
            val e=VaultEntry(displayName=original.displayName,protocol=original.protocol,payload=original.payload,createdAt=1000,quota=original.quota,policy=VaultPolicy(allowReconnect=false))
            val ledger=VaultUsageLedger(File(d,"usage"))
            val first=VaultRuntime.issue(e,ledger,1000);VaultRuntime.forget(first.id)
            ledger.record(e.usageId,1,1,1001,connected=true)
            assertTrue(runCatching { VaultRuntime.issue(e,ledger,1002) }.isFailure)
        } finally { d.deleteRecursively() }
    }
    @Test fun uncleanProcessDeathLocksInsteadOfResettingQuota() {
        val d=Files.createTempDirectory("vault-recovery").toFile()
        try {
            val e=entry();val file=File(d,"usage");val ledger=VaultUsageLedger(file)
            ledger.record(e.usageId,10,20,1000,true,true)
            val raw=org.json.JSONObject(file.readText())
            raw.getJSONObject(e.usageId).put("sessionOwner","dead-process")
            file.writeText(raw.toString())
            assertTrue(ledger.read(e.usageId).uncertain)
            assertEquals(30L,ledger.read(e.usageId).bytes(Accounting.TOTAL))
            assertTrue(runCatching { VaultMeteredSession(e,ledger,1000) }.isFailure)
        } finally { d.deleteRecursively() }
    }
    @Test fun byteUnitsAndOverflow() {
        assertEquals(1L shl 30,VaultInputs.bytes("1","GB"));assertEquals(2L shl 40,VaultInputs.bytes("2","TB"))
        assertNull(VaultInputs.bytes("","Unlimited"))
        assertTrue(runCatching { VaultInputs.bytes(Long.MAX_VALUE.toString(),"TB") }.isFailure)
        assertTrue(runCatching { VaultInputs.bytes("-1","MB") }.isFailure)
    }
    @Test fun timeUnitsAndStrictDate() {
        assertEquals(7200000L,VaultInputs.duration("2","Hours"));assertEquals(2592000000L,VaultInputs.duration("1","Months"))
        assertTrue(runCatching { VaultInputs.expiry("2027-02-30 12:00","Custom date/time",0,ZoneId.of("UTC")) }.isFailure)
        assertNotNull(VaultInputs.expiry("2027-02-28 12:00","Custom date/time",0,ZoneId.of("UTC")))
    }
    @Test fun endpointRejectsOverflowAndNonIntegerCounters() {
        assertTrue(runCatching { SingBoxUsage.decode("{\"version\":1,\"upload\":-1,\"download\":0}","g") }.isFailure)
        assertTrue(runCatching { SingBoxUsage.decode("{\"version\":1,\"upload\":1.5,\"download\":0}","g") }.isFailure)
    }
    @Test fun largerNewGenerationStillChargesFullAmountAndPersistsAcrossRestart() {
        val d=Files.createTempDirectory("vault-test").toFile()
        try {
            val e=entry();val f=File(d,"usage");val ledger=VaultUsageLedger(f);val session=VaultMeteredSession(e,ledger,1000)
            session.sample(10,20,1000,generation="a");session.sample(50,60,1000,generation="b");session.close()
            val reopened=VaultUsageLedger(f);assertEquals(140L,reopened.read(e.usageId).bytes(Accounting.TOTAL))
            val next=VaultMeteredSession(e,reopened,1000);next.sample(2,3,1000,generation="c")
            assertEquals(145L,reopened.read(e.usageId).bytes(Accounting.TOTAL))
        } finally { d.deleteRecursively() }
    }
    @Test fun backupRejectsWrongPasswordWithoutDamagingVaultAndNeverReducesUsage() {
        val d=Files.createTempDirectory("vault-backup-test").toFile()
        try {
            val f=File(d,"vault");val ledger=VaultUsageLedger(File(d,"usage"));val e=entry();val pw="long-password".toCharArray()
            VaultRepository(f).save(listOf(e),pw);ledger.record(e.usageId,10,20,1000,true,true)
            val backup=VaultBackup.capture(f,ledger)!!;val before=f.readBytes()
            assertTrue(runCatching { VaultBackup.restore(backup,"wrong".toCharArray(),f,ledger) }.isFailure)
            assertArrayEquals(before,f.readBytes())
            ledger.record(e.usageId,100,200,1100)
            VaultBackup.restore(backup,pw,f,ledger)
            assertEquals(330L,ledger.read(e.usageId).bytes(Accounting.TOTAL))
            assertEquals(e.usageId,VaultRepository(f).open(pw).single().usageId)
        } finally { d.deleteRecursively() }
    }
    @Test fun independentPasswordExportPreservesFullPolicyAndIdentity() {
        val e=entry();val repo=VaultRepository(File("unused"));val pw="export-password".toCharArray()
        val bytes=repo.export(listOf(e),pw);val copy=repo.decode(bytes,pw).single()
        assertEquals(e.usageId,copy.usageId);assertEquals(e.policy,copy.policy);assertEquals(e.quota,copy.quota)
        assertTrue(runCatching { repo.decode(bytes,"vault-password".toCharArray()) }.isFailure)
    }
}

package net.gozar.app.security.vault

import net.gozar.app.ProxyConfig
import net.gozar.app.ConfigBuilder
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VaultRuntimeTest {
    private fun entry(quota: VaultQuota = VaultQuota(), policy: VaultPolicy = VaultPolicy(),
        protocol: String = "socks") = VaultEntry(displayName="Synthetic", protocol=protocol,
        payload=ProxyConfig(name="Synthetic",protocol=protocol,address="192.0.2.1",port=443).toJson().toString(),
        createdAt=1000,quota=quota,policy=policy)
    private inline fun ledger(block:(VaultUsageLedger,File)->Unit) {
        val dir=Files.createTempDirectory("vault-runtime-test").toFile()
        try { block(VaultUsageLedger(File(dir,"usage.json")),dir) }
        finally { VaultRuntime.lock();dir.deleteRecursively() }
    }
    private fun rejected(block:()->Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun ninetyToHundredStopsAndPreventsAnotherStart() = ledger { ledger,_ ->
        val e=entry(VaultQuota(QuotaMode.LOCAL,100))
        ledger.record(e.usageId,90,0,1000)
        val session=VaultMeteredSession(e,ledger,1000)
        assertEquals(1L,session.sample(4,5,1001).remainingBytes)
        val exhausted=session.sample(4,6,1002)
        assertEquals(EntitlementStatus.QUOTA_EXHAUSTED,exhausted.status)
        rejected { VaultRuntime.requireAllowed(exhausted) }
        session.close()
        rejected { session.sample(100,100,1003) }
        rejected { VaultRuntime.issue(e,ledger,1004) }
        assertEquals(100L,ledger.read(e.usageId).bytes(Accounting.TOTAL))
    }
    @Test fun cumulativeSamplesResetAndFinalFlushNeverSubtractUsage() = ledger { ledger,_ ->
        val e=entry();val session=VaultMeteredSession(e,ledger,1000)
        session.sample(10,20,1001);session.sample(10,20,1002)
        session.sample(3,4,1003);session.sample(5,7,1004)
        assertEquals(15L,ledger.read(e.usageId).upload)
        assertEquals(27L,ledger.read(e.usageId).download)
        session.close();rejected { session.sample(99,99,1005,true) }
        assertEquals(1001L,ledger.read(e.usageId).firstConnectAt)
    }
    @Test fun firstConnectRequiresSuccessfulDataAndDoesNotResetOnReconnect() = ledger { ledger,_ ->
        val e=entry(VaultQuota(validityMillis=1000,activationMode=ActivationMode.FIRST_CONNECT))
        val s=VaultMeteredSession(e,ledger,1000)
        assertNull(s.sample(0,0,1100).expiresAt)
        assertEquals(2200L,s.sample(5,5,1200,false).expiresAt)
        s.close()
        val next=VaultMeteredSession(e,ledger,1500)
        assertEquals(2200L,next.sample(0,0,1600,true).expiresAt)
        assertEquals(EntitlementStatus.EXPIRED,next.check(2200).status)
    }
    @Test fun backwardsWallClockDoesNotFreezeActiveSessionTime() = ledger { ledger,_ ->
        var elapsed=0L
        val e=entry(VaultQuota(validityMillis=1000))
        val s=VaultMeteredSession(e,ledger,1000) { elapsed }
        elapsed=1000
        assertEquals(EntitlementStatus.EXPIRED,s.check(1).status)
    }
    @Test fun singleUseGrantBindsPayloadAndIsInvalidAfterForget() = ledger { ledger,_ ->
        val e=entry();val cfg=VaultRuntime.issue(e,ledger,1000)
        rejected { VaultRuntime.check(cfg.copy(password="modified"),1001) }
        rejected { VaultRuntime.claim(cfg.id,1001) }
        VaultRuntime.prepare(cfg,VaultRuntime.Prepared("synthetic","synthetic"),1001)
        val (_,session)=VaultRuntime.claim(cfg.id,1002)
        assertTrue(session.active);assertEquals(cfg.id,VaultRuntime.activeReference(e.id));rejected { VaultRuntime.claim(cfg.id,1003) }
        VaultRuntime.lock() // page lock must not interrupt the already running engine
        assertTrue(VaultRuntime.testAllowed(cfg.id))
        VaultRuntime.forget(cfg.id)
        assertFalse(session.active);assertFalse(VaultRuntime.testAllowed(cfg.id))
        rejected { VaultRuntime.check(cfg,1004) }
    }
    @Test fun lockedStaleOrMissingGrantCannotStartAndCannotSpawnTestCore() = ledger { ledger,_ ->
        val cfg=VaultRuntime.issue(entry(),ledger,1000)
        rejected { ConfigBuilder.buildForTest(cfg) }
        rejected { VaultRuntime.check(cfg,121001) }
        val next=VaultRuntime.issue(entry(),ledger,130000)
        VaultRuntime.lock();rejected { VaultRuntime.check(next,130001) }
        rejected { VaultRuntime.claim("vault:missing",130001) }
    }
    @Test fun serverQuotaLocalAuthAndUnsupportedAccountingNeverBecomeActive() = ledger { ledger,_ ->
        rejected { VaultRuntime.issue(entry(VaultQuota(QuotaMode.SERVER,100,entitlementId="synthetic")),ledger,1000) }
        rejected { VaultRuntime.issue(entry(policy=VaultPolicy(requireLocalAuthentication=true)),ledger,1000) }
        rejected { VaultRuntime.issue(entry(policy=VaultPolicy(allowConnect=false)),ledger,1000) }
        rejected { VaultRuntime.issue(entry(protocol="ikev2"),ledger,1000) }
        val metered=VaultRuntime.issue(entry(VaultQuota(QuotaMode.LOCAL,100),protocol="openconnect"),ledger,1000)
        assertTrue(VaultRuntime.isReference(metered.id))
    }
    @Test fun policyDeniesTestAndReconnectEvenInActiveSession() = ledger { ledger,_ ->
        val cfg=VaultRuntime.issue(entry(policy=VaultPolicy(allowTest=false,allowReconnect=false)),ledger,1000)
        VaultRuntime.prepare(cfg,VaultRuntime.Prepared("synthetic",null),1000)
        VaultRuntime.claim(cfg.id,1000)
        assertFalse(VaultRuntime.testAllowed(cfg.id))
        assertFalse(VaultRuntime.reconnectAllowed(cfg.id,1001))
        VaultRuntime.forget(cfg.id)
    }
    @Test fun ioFailureDoesNotAdvanceCountersOrAuthorizeStart() = ledger { ledger,dir ->
        val e=entry();val s=VaultMeteredSession(e,ledger,1000)
        val original=File(dir,"usage.json").readBytes()
        check(File(dir,"usage.json").delete())
        check(File(dir,"usage.json").mkdir())
        rejected { s.sample(10,20,1001) }
        rejected { VaultRuntime.issue(e,ledger,1001) }
        File(dir,"usage.json").delete()
        File(dir,"usage.json").writeBytes(original)
        assertEquals(30L,s.sample(10,20,1002).usedBytes)
    }
    @Test fun keepBothAndReplaceKeepConsumptionIdentityAndReopenIt() = ledger { ledger,dir ->
        val existing=entry(VaultQuota(QuotaMode.LOCAL,100))
        ledger.record(existing.usageId,100,0,1000)
        val incoming=entry(VaultQuota(QuotaMode.LOCAL,100))
        val repository=VaultRepository(File(dir,"vault.gsb"))
        val both=repository.merge(listOf(existing),listOf(incoming)) { _,_ -> VaultRepository.DuplicateChoice.KEEP_BOTH }
        assertNotEquals(both[0].id,both[1].id)
        assertEquals(both[0].usageId,both[1].usageId)
        rejected { VaultRuntime.issue(both[1],ledger,1001) }
        val replaced=repository.merge(listOf(existing),listOf(incoming)) { _,_ -> VaultRepository.DuplicateChoice.REPLACE }
        assertEquals(existing.usageId,replaced.single().usageId)
        val pass="synthetic password".toCharArray()
        try {
            repository.save(both,pass)
            assertEquals(both.map { it.usageId },repository.open(pass).map { it.usageId })
        } finally { pass.fill('\u0000') }
    }
}

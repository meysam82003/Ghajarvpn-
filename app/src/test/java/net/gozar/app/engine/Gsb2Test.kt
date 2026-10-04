package net.gozar.app.engine

import net.gozar.app.ConfigParser
import net.gozar.app.gsb2.Gsb2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gsb2Test {
    private val cfg = ConfigParser.parse("vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=tls#a")!!
    private val keys = Gsb2.newIssuerKey()
    private val share = Gsb2.Share(name = "دوستان", note = "n", createdAt = 1000, expiresAt = 5000, quotaBytes = 2048, configs = listOf(cfg))

    @Test fun roundTripWithPassword() {
        val bytes = Gsb2.seal(share, keys.first, keys.second, "pw".toCharArray())
        assertTrue(Gsb2.needsPassword(bytes))
        assertEquals(Gsb2.OpenResult.NeedsPassword, Gsb2.open(bytes, null))
        assertEquals(Gsb2.OpenResult.WrongPassword, Gsb2.open(bytes, "nope".toCharArray()))
        val ok = Gsb2.open(bytes, "pw".toCharArray()) as Gsb2.OpenResult.Ok
        assertEquals("دوستان", ok.share.name); assertEquals(2048, ok.share.quotaBytes)
        val received = Gsb2.receivedConfigs(ok.share).single()
        assertTrue(received.locked)
        val meta = Gsb2.Meta.of(received)!!
        assertEquals(share.id, meta.shareId); assertEquals(5000, meta.expiresAt)
        assertEquals("a.example.com", received.address)
    }

    @Test fun noPasswordOpensDirectly() {
        val bytes = Gsb2.seal(share.copy(hidden = false), keys.first, keys.second, null)
        assertFalse(Gsb2.needsPassword(bytes))
        val ok = Gsb2.open(bytes, null) as Gsb2.OpenResult.Ok
        assertFalse(Gsb2.receivedConfigs(ok.share).single().locked)
    }

    @Test fun tamperingIsDetected() {
        val bytes = Gsb2.seal(share, keys.first, keys.second, null)
        bytes[bytes.size - 5] = (bytes[bytes.size - 5] + 1).toByte()
        assertTrue(Gsb2.open(bytes, null) is Gsb2.OpenResult.Invalid)
    }

    @Test fun policyBlocksExpiryAndQuota() {
        val meta = Gsb2.Meta("id", "s", expiresAt = 5000, quotaBytes = 100, issuer = "x")
        assertEquals(Gsb2.Verdict.Allowed, Gsb2.check(meta, 50, 4000))
        assertTrue(Gsb2.check(meta, 50, 6000) is Gsb2.Verdict.Blocked)
        assertTrue(Gsb2.check(meta, 100, 4000) is Gsb2.Verdict.Blocked)
        assertEquals(Gsb2.Verdict.Allowed, Gsb2.check(meta.copy(expiresAt = 0, quotaBytes = 0), Long.MAX_VALUE / 2, Long.MAX_VALUE / 2))
    }

    @Test fun durationStartsOnImportAndTheEarlierLimitWins() {
        val d = share.copy(expiresAt = 0, durationMs = 1000)
        assertEquals(11_000, Gsb2.Meta.of(Gsb2.receivedConfigs(d, importedAt = 10_000).single())!!.expiresAt)
        val both = share.copy(expiresAt = 5000, durationMs = 100_000)
        assertEquals(5000, Gsb2.Meta.of(Gsb2.receivedConfigs(both, importedAt = 10).single())!!.expiresAt)
    }
}

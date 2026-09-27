package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeboxTest {
    @Test
    fun sealOpensOnlyWithThePassword() {
        val cfgs = listOf(ConfigParser.parse("sstp://u:p@s.example.com?mtu=1300#S")!!, ConfigParser.parse("ssh://bob:pw@h.example.com#H")!!)
        val blob = Safebox.seal(cfgs, "correct horse".toCharArray())
        assertTrue(Safebox.isVault(blob))
        assertEquals(cfgs, Safebox.open(blob, "correct horse".toCharArray()))
        assertNull(Safebox.open(blob, "wrong".toCharArray()))
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertNull("a modified vault must not open", Safebox.open(blob, "correct horse".toCharArray()))
        assertNull(Safebox.open("not a vault".toByteArray(), "x".toCharArray()))
    }
}

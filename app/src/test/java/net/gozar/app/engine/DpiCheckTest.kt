package net.gozar.app.engine

import net.gozar.app.ConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiCheckTest {

    @Test
    fun relayCopyKeepsTheServerName() {
        val c = ConfigParser.parse("sstp://u:p@vpn.example.com:443#s")!!
        val r = DpiCheck.viaRelay(c, 31000)
        assertEquals("127.0.0.1", r.address); assertEquals(31000, r.port)
        assertEquals("vpn.example.com", r.sni); assertEquals("dpi:" + c.id, r.id)
        val ip = DpiCheck.viaRelay(ConfigParser.parse("sstp://u:p@203.0.113.9#s")!!, 1)
        assertEquals("", ip.sni)
    }

    @Test
    fun dnsTunnelsAndProxiedSshAreNotChecked() {
        val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        assertNotNull(DpiCheck.unsupported(ConfigParser.parse("dnstt://t.example.com?pubkey=$key&resolver=8.8.8.8#D")!!))
        assertNull(DpiCheck.unsupported(ConfigParser.parse("ssh://bob:pw@ssh.example.com#S")!!))
    }

    @Test
    fun classifierOutputIsParsedAfterNotices() {
        val out = "Dissector (D)TLS not loaded: incompatible license\n" +
            "{\"protocol\":\"TLS\",\"master\":\"Unknown\",\"app\":\"TLS\",\"category\":\"Web\",\"confidence\":\"Match by port\",\"packets\":29,\"risks\":[\"Susp Entropy\"]}"
        val r = DpiCheck.parse(out)
        assertEquals("TLS", r.protocol); assertEquals(listOf("Susp Entropy"), r.risks)
        assertTrue(r.note.orEmpty().contains("port"))
        val ssh = DpiCheck.parse("{\"protocol\":\"SSH\",\"category\":\"RemoteAccess\",\"confidence\":\"DPI\",\"packets\":16,\"risks\":[]}")
        assertEquals("SSH", ssh.protocol); assertNull(ssh.note)
        assertNotNull(DpiCheck.parse("garbage").error)
    }
}

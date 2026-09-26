package net.gozar.app.engine

import net.gozar.app.ConfigParser
import net.gozar.app.ProxyConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTesterTest {

    @Test
    fun traceBodyGivesIpAndCountry() {
        val m = EngineTester.parseTrace("fl=1\nh=www.cloudflare.com\nip=203.0.113.9\nts=1\nloc=DE\ncolo=FRA\n")
        assertEquals("203.0.113.9", m["ip"]); assertEquals("DE", m["loc"])
    }

    @Test
    fun reachTargetsTheFirstHop() {
        val tuic = ConfigParser.parse("tuic://u:p@t.example.com:443#t")!!
        assertTrue(Reach.target(tuic).udp)
        val anytls = ConfigParser.parse("anytls://pw@a.example.com:8443?sni=cdn.example.com#a")!!
        Reach.target(anytls).let { assertFalse(it.udp); assertTrue(it.tls); assertEquals("cdn.example.com", it.sni); assertEquals(8443, it.port) }
        val dns = ProxyConfig(name = "d", protocol = "dnstt", address = "8.8.8.8", port = 53, mode = "udp")
        Reach.target(dns).let { assertEquals("8.8.8.8", it.host); assertTrue(it.udp) }
        val ssh = ProxyConfig(name = "s", protocol = "ssh", address = "ssh.example.com", port = 22,
            extra = JSONObject().put("transport", JSONObject().put("mode", "http-proxy").put("proxyHost", "p.example.com").put("proxyPort", 8080)).toString())
        Reach.target(ssh).let { assertEquals("p.example.com", it.host); assertEquals(8080, it.port) }
    }

    @Test
    fun resultRoundTripsThroughJson() {
        val r = EngineTestResult(EngineId.SINGBOX, true, true, 120, null, testedAt = 5, jitterMs = 7, lossPct = 20, handshakeMs = 300,
            exitIp = "203.0.113.9", exitCountry = "DE", steps = listOf(TestStep("dns", true, "1.2.3.4", 3), TestStep("tcp", null, "udp")))
        assertEquals(r, EngineTestResult.fromJson(r.toJson()))
    }
}

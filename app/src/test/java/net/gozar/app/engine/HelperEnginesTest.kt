package net.gozar.app.engine

import net.gozar.app.ConfigParser
import net.gozar.app.ConfigShare
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperEnginesTest {

    private fun spec(link: String) = JSONObject(SingBoxConfig.spec(ConfigParser.parse(link)!!)!!)

    @Test
    fun plainSshHasNoSidecarAndKeepsCredentials() {
        val s = spec("ssh://bob:pw@ssh.example.com:22#s")
        assertTrue(!s.has("sidecar"))
        assertEquals("bob", s.getJSONObject("outbound").getString("user"))
        assertEquals("pw", s.getJSONObject("outbound").getString("password"))
    }

    @Test
    fun sshOverWebSocketRunsTheHelperUnderSshWithCredentials() {
        val s = spec("ssh://bob:pw@ssh.example.com:22?mode=wss&proxy=cdn.example.com:443&sni=front.example.com&wspath=%2Fssh#s")
        val out = s.getJSONObject("outbound")
        assertEquals("127.0.0.1", out.getString("server"))
        assertEquals("bob", out.getString("user"))
        assertEquals("pw", out.getString("password"))
        val l = Sidecars.launch(s.getJSONObject("sidecar"))
        assertEquals(Sidecars.HELPER, l.binary)
        assertEquals(listOf("sshtransport", "-listen", "{port}", "-mode", "wss", "-host", "ssh.example.com", "-port", "22",
            "-proxy", "cdn.example.com:443", "-sni", "front.example.com", "-ws-path", "/ssh"), l.args)
    }

    @Test
    fun sshPayloadLinkRoundTrips() {
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("GET / HTTP/1.1[crlf]Host: [host][crlf][crlf]".toByteArray())
        val c = ConfigParser.parse("ssh://bob:pw@ssh.example.com:2222?mode=payload&proxy=10.0.0.1:8080&payload=$payload#p")!!
        assertEquals("GET / HTTP/1.1[crlf]Host: [host][crlf][crlf]", c.extraJson().getJSONObject("transport").getString("payload"))
        assertEquals(c.copy(id = "x"), ConfigParser.parse(ConfigShare.toLink(c))!!.copy(id = "x"))
    }

    @Test
    fun amneziaConfIsDetectedAndRunsOnTheHelper() {
        val conf = "[Interface]\nPrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=\nAddress = 10.8.0.2/32\nJc = 4\nH1 = 1234\n\n" +
            "[Peer]\nPublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=\nEndpoint = vpn.example.com:51820\nAllowedIPs = 0.0.0.0/0\n"
        val c = ConfigParser.parseWireguardConf(conf)!!
        assertEquals("amneziawg", c.protocol)
        assertEquals(EngineId.SINGBOX, EngineRouting.engineFor(c))
        val l = Sidecars.launch(JSONObject(SingBoxConfig.spec(c)!!).getJSONObject("sidecar"))
        assertTrue(l.socks)
        assertEquals(conf.trim(), l.files.getValue("awg.conf"))
        assertEquals(c.copy(id = "x"), ConfigParser.parse(ConfigShare.toLink(c))!!.copy(id = "x", name = c.name))
        // Plain WireGuard stays on Xray.
        val wg = ConfigParser.parseWireguardConf(conf.replace("Jc = 4\nH1 = 1234\n", ""))!!
        assertEquals("wireguard", wg.protocol)
        assertNull(SingBoxConfig.spec(wg))
    }

    @Test
    fun mieruAndBrookLinks() {
        val m = ConfigParser.parse("mierus://u:p@1.2.3.4?port=6666&protocol=TCP&profile=default#m")!!
        assertEquals("mieru", m.protocol); assertEquals("1.2.3.4", m.address); assertEquals(6666, m.port)
        val ml = Sidecars.launch(JSONObject(SingBoxConfig.spec(m)!!).getJSONObject("sidecar"))
        assertEquals(listOf("mieru", "-listen", "{port}", "-rpc", "{port2}", "-url", "mierus://u:p@1.2.3.4?port=6666&protocol=TCP&profile=default", "-dir", "{dir}"), ml.args)
        val b = ConfigParser.parse("brook://wsserver?password=pw&wsserver=ws%3A%2F%2Fb.example.com%3A8080#b")!!
        assertEquals("brook", b.protocol); assertEquals("b.example.com", b.address); assertEquals(8080, b.port)
        assertEquals("brook://wsserver?password=pw&wsserver=ws%3A%2F%2Fb.example.com%3A8080#b", ConfigShare.toLink(b))
    }
}

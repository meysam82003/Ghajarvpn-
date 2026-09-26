package net.gozar.app.engine

import net.gozar.app.ConfigParser
import net.gozar.app.ConfigShare
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigTest {

    private fun full(link: String): JSONObject {
        val c = ConfigParser.parse(link)
        assertNotNull("$link did not parse", c)
        val spec = SingBoxConfig.spec(c!!)
        assertNotNull("$link is not routed to sing-box", spec)
        return JSONObject(SingBoxConfig.full(spec!!, 20808))
    }

    private fun proxy(root: JSONObject): JSONObject {
        val outs = root.getJSONArray("outbounds")
        for (i in 0 until outs.length()) if (outs.getJSONObject(i).optString("tag") == "proxy") return outs.getJSONObject(i)
        val eps = root.getJSONArray("endpoints")
        return eps.getJSONObject(0)
    }

    @Test
    fun tuicLinkBecomesATuicOutbound() {
        val root = full("tuic://11111111-2222-3333-4444-555555555555:pw@tuic.example.com:8443?sni=cdn.example.com&alpn=h3&congestion_control=bbr&udp_relay_mode=native&allow_insecure=1#T")
        val o = proxy(root)
        assertEquals("tuic", o.getString("type"))
        assertEquals("tuic.example.com", o.getString("server"))
        assertEquals(8443, o.getInt("server_port"))
        assertEquals("11111111-2222-3333-4444-555555555555", o.getString("uuid"))
        assertEquals("pw", o.getString("password"))
        assertEquals("bbr", o.getString("congestion_control"))
        assertEquals("native", o.getString("udp_relay_mode"))
        val tls = o.getJSONObject("tls")
        assertTrue(tls.getBoolean("enabled"))
        assertTrue(tls.getBoolean("insecure"))
        assertEquals("cdn.example.com", tls.getString("server_name"))
        assertEquals("h3", tls.getJSONArray("alpn").getString(0))
    }

    @Test
    fun localSocksInboundAndResolverAreAlwaysPresent() {
        val root = full("anytls://secret@a.example.com:443?sni=a.example.com#A")
        val inbound = root.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("socks", inbound.getString("type"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals(20808, inbound.getInt("listen_port"))
        assertEquals("proxy", root.getJSONObject("route").getString("final"))
        assertEquals("local", root.getJSONObject("route").getJSONObject("default_domain_resolver").getString("server"))
        assertEquals("local", root.getJSONObject("dns").getJSONArray("servers").getJSONObject(0).getString("type"))
    }

    @Test
    fun hysteriaV1KeepsAuthAndObfs() {
        val o = proxy(full("hysteria://h.example.com:36712?auth=tok&peer=h.example.com&upmbps=20&downmbps=100&obfs=xplus&obfsParam=salt#H"))
        assertEquals("hysteria", o.getString("type"))
        assertEquals("tok", o.getString("auth_str"))
        assertEquals(20, o.getInt("up_mbps"))
        assertEquals(100, o.getInt("down_mbps"))
        assertEquals("salt", o.getString("obfs"))
    }

    @Test
    fun sshWithoutPortDefaultsTo22() {
        val o = proxy(full("ssh://bob:pa%40ss@ssh.example.com#S"))
        assertEquals("ssh", o.getString("type"))
        assertEquals(22, o.getInt("server_port"))
        assertEquals("bob", o.getString("user"))
        assertEquals("pa@ss", o.getString("password"))
    }

    @Test
    fun openConnectIsAnEndpointNotAnOutbound() {
        val root = full("openconnect://u:p@vpn.example.com?flavor=gp#O")
        val ep = root.getJSONArray("endpoints").getJSONObject(0)
        assertEquals("openconnect", ep.getString("type"))
        assertEquals("proxy", ep.getString("tag"))
        assertEquals("vpn.example.com", ep.getString("server"))
        assertEquals("gp", ep.getString("flavor"))
        assertEquals("u", ep.getString("username"))
        // Only the direct outbound; the proxy tag belongs to the endpoint.
        assertEquals(1, root.getJSONArray("outbounds").length())
    }

    @Test
    fun snellAlwaysCarriesAValidVersion() {
        val c = net.gozar.app.ProxyConfig(name = "s", protocol = "snell", address = "s.example.com", port = 1, password = "psk")
        val o = SingBoxConfig.proxy(c)
        assertEquals(4, o.getInt("version"))
        val v6 = SingBoxConfig.proxy(c.copy(alterId = 6, hyObfs = "tls"))
        assertEquals(6, v6.getInt("version"))
        assertFalse(v6.has("obfs_mode"))
    }

    @Test
    fun xrayProtocolsAreNotRoutedToSingBox() {
        val vless = ConfigParser.parse("vless://11111111-1111-1111-1111-111111111111@9.9.9.9:443?security=tls&type=ws#V")!!
        assertNull(SingBoxConfig.spec(vless))
        assertEquals(EngineId.XRAY, EngineRouting.engineFor(vless))
        val hy2 = ConfigParser.parse("hysteria2://pw@9.9.9.9:443#H2")!!
        assertEquals(EngineId.XRAY, EngineRouting.engineFor(hy2))
    }

    @Test
    fun shareLinksRoundTrip() {
        listOf(
            "tuic://11111111-2222-3333-4444-555555555555:pw@tuic.example.com:8443?sni=cdn.example.com&congestion_control=bbr#T",
            "hysteria://h.example.com:36712?auth=tok&peer=h.example.com&upmbps=20#H",
            "anytls://secret@a.example.com:443?sni=a.example.com&fp=chrome#A",
            "ssh://bob:pw@ssh.example.com:2222#S",
            "openconnect://u:p@vpn.example.com:8443?flavor=fortinet#O"
        ).forEach { link ->
            val a = ConfigParser.parse(link)!!
            val back = ConfigParser.parse(ConfigShare.toLink(a))
            assertNotNull("share link of $link did not parse back", back)
            assertEquals(a.copy(id = "x"), back!!.copy(id = "x"))
        }
    }
}

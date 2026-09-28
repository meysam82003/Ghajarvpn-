package net.gozar.app.engine

import net.gozar.app.ConfigParser
import net.gozar.app.ConfigShare
import net.gozar.app.TorBridges
import net.gozar.app.TorController
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreEnginesTest {

    @Test
    fun naiveLink() {
        val c = ConfigParser.parse("naive+https://u:p@n.example.com:8443#n")!!
        val o = JSONObject(SingBoxConfig.spec(c)!!).getJSONObject("outbound")
        assertEquals("naive", o.getString("type")); assertEquals("u", o.getString("username"))
        assertEquals("n.example.com", o.getJSONObject("tls").getString("server_name"))
        assertEquals(c.copy(id = "x"), ConfigParser.parse(ConfigShare.toLink(c))!!.copy(id = "x"))
    }

    @Test
    fun shadowTlsPluginBecomesTwoOutbounds() {
        val user = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("2022-blake3-aes-128-gcm:k".toByteArray())
        val plugin = java.net.URLEncoder.encode("shadow-tls;host=www.apple.com;password=pw;version=3", "UTF-8")
        val c = ConfigParser.parse("ss://$user@1.2.3.4:443?plugin=$plugin#s")!!
        assertEquals("shadowtls", c.protocol)
        val root = JSONObject(SingBoxConfig.full(SingBoxConfig.spec(c)!!, 1080))
        val outs = root.getJSONArray("outbounds")
        val ss = outs.getJSONObject(0); val st = outs.getJSONObject(1)
        assertEquals("shadowsocks", ss.getString("type")); assertEquals("shadowtls-out", ss.getString("detour"))
        assertEquals("shadowtls", st.getString("type")); assertEquals(3, st.getInt("version"))
        assertEquals("www.apple.com", st.getJSONObject("tls").getString("server_name"))
        assertEquals(c.copy(id = "x"), ConfigParser.parse(ConfigShare.toLink(c))!!.copy(id = "x"))
    }

    @Test
    fun juicityLinkAndConfig() {
        val c = ConfigParser.parse("juicity://11111111-2222-3333-4444-555555555555:pw@j.example.com:443?congestion_control=bbr&sni=j.example.com#j")!!
        val l = Sidecars.launch(JSONObject(SingBoxConfig.spec(c)!!).getJSONObject("sidecar"))
        assertEquals("libjuicity.so", l.binary)
        val cfg = JSONObject(l.files.getValue("juicity.json"))
        assertEquals("127.0.0.1:{port}", cfg.getString("listen")); assertEquals("j.example.com:443", cfg.getString("server"))
        assertEquals(c.copy(id = "x"), ConfigParser.parse(ConfigShare.toLink(c))!!.copy(id = "x"))
    }

    @Test
    fun torBridgeLinesImportAndReachTorrc() {
        val c = ConfigParser.parse("obfs4 192.0.2.10:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=AAAA iat-mode=0\nobfs4 192.0.2.11:443 0123456789ABCDEF0123456789ABCDEF01234568 cert=BBBB iat-mode=0")!!
        assertEquals("tor", c.protocol)
        val b = TorBridges.from(c)
        assertEquals("obfs4", b.transport); assertEquals(2, b.lines.size)
        assertEquals(b, TorBridges.fromSpec(TorController.spec(c).split("|")))
        val rc = b.torrc("/x/liblyrebird.so")
        assertTrue(rc.contains("UseBridges 1\n")); assertTrue(rc.contains("ClientTransportPlugin obfs4,meek_lite,webtunnel,snowflake exec /x/liblyrebird.so"))
        assertTrue(rc.contains("Bridge obfs4 192.0.2.11:443"))
        val snow = TorBridges.from(c.copy(extra = JSONObject().put("pt", "snowflake").toString()))
        assertEquals(TorBridges.SNOWFLAKE_DEFAULT, snow.lines)
    }

    @Test
    fun sstpLinkRunsTheHelperWithThePasswordInAFile() {
        val pin = "6ecdebdb14974ab295edaea132cd91c1cfbfa7812c386093b0d7eecc3b66ebac"
        val c = ConfigParser.parse("sstp://ghtest:p%40ss@vpn.example.com?auth=mschapv2&pin=$pin&mtu=1350#S")!!
        assertEquals("sstp", c.protocol); assertEquals(443, c.port); assertEquals("p@ss", c.password)
        assertEquals(EngineId.SINGBOX, EngineRouting.engineFor(c))
        val spec = JSONObject(SingBoxConfig.spec(c)!!)
        assertEquals("socks", spec.getJSONObject("outbound").getString("type"))
        val l = Sidecars.launch(spec.getJSONObject("sidecar"))
        assertEquals(Sidecars.HELPER, l.binary)
        assertEquals(listOf("sstp", "-listen", "{port}", "-server", "vpn.example.com:443", "-user", "ghtest", "-auth", "mschapv2",
            "-mtu", "1350", "-pin", pin), l.args)
        assertTrue("password must not be on the command line", l.args.none { it.contains("p@ss") })
        assertEquals("p@ss", l.files.getValue("sstp.pass")); assertTrue("sstp.pass" in l.secretFiles)
        assertEquals("{dir}/sstp.pass", l.env.getValue("SSTP_PASSWORD_FILE"))
        assertEquals(c.copy(id = "x"), ConfigParser.parse(ConfigShare.toLink(c))!!.copy(id = "x"))
        val bad = runCatching { Sidecars.launch(spec.getJSONObject("sidecar").put("pin", "zz")) }.exceptionOrNull()
        assertTrue(bad?.message.orEmpty().contains("SHA-256"))
    }

    @Test
    fun softEtherLinkCarriesHubAndStaticAddress() {
        val c = ConfigParser.parse("softether://bob:pw@se.example.com:5555?hub=VPN&ip=10.0.0.9%2F24&gw=10.0.0.1&dns=1.1.1.1&auth=plain#SE")!!
        assertEquals("softether", c.protocol); assertEquals(5555, c.port)
        assertEquals(EngineId.SINGBOX, EngineRouting.engineFor(c))
        val l = Sidecars.launch(JSONObject(SingBoxConfig.spec(c)!!).getJSONObject("sidecar"))
        assertEquals(listOf("softether", "-listen", "{port}", "-server", "se.example.com:5555", "-hub", "VPN", "-user", "bob",
            "-mtu", "1400", "-plain", "-ip", "10.0.0.9/24", "-gw", "10.0.0.1", "-dns", "1.1.1.1"), l.args)
        assertTrue(l.args.none { it == "pw" }); assertEquals("pw", l.files.getValue("se.pass"))
        assertEquals(c.copy(id = "x"), ConfigParser.parse(ConfigShare.toLink(c))!!.copy(id = "x"))
        val d = ConfigParser.parse("softether://u:p@h.example.com#d")!!
        assertEquals(443, d.port); assertEquals("DEFAULT", d.extraJson().getString("hub"))
    }
}

package net.gozar.app.engine

import net.gozar.app.ConfigParser
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsTunnelPrefsTest {

    private val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @After fun reset() { DnsTunnelPrefs.current = DnsTunnelPrefs.Values() }

    private fun sidecar(link: String) = JSONObject(SingBoxConfig.spec(ConfigParser.parse(link)!!)!!).getJSONObject("sidecar")

    @Test
    fun resolverOverrideReachesDnstt() {
        DnsTunnelPrefs.current = DnsTunnelPrefs.Values(overrideResolver = "9.9.9.9:853", overrideTransport = "dot")
        val l = Sidecars.launch(sidecar("dnstt://t.example.com?pubkey=$key&resolver=8.8.8.8#D"))
        assertEquals(listOf("-dot", "9.9.9.9:853"), l.args.take(2))
    }

    @Test
    fun slipstreamKeepsItsResolverForDoT() {
        DnsTunnelPrefs.current = DnsTunnelPrefs.Values(overrideResolver = "9.9.9.9:853", overrideTransport = "dot")
        val out = DnsTunnelPrefs.applyTo("slipstream", JSONObject().put("resolver", "1.1.1.1:53"))
        assertEquals("1.1.1.1:53", out.getString("resolver"))
    }

    @Test
    fun poolWorkersAndDuplicationReachMasterDnsToml() {
        DnsTunnelPrefs.current = DnsTunnelPrefs.Values(pool = listOf("1.1.1.1", "8.8.8.8"), workers = 8, duplication = 5, keepSlowResolvers = true)
        val masterLaunch = Sidecars.launch(JSONObject().put("kind", "masterdns").put("domain", "v.example.com").put("key", "k").put("resolvers", "4.4.4.4"))
        assertEquals("1.1.1.1\n8.8.8.8\n", masterLaunch.files.getValue("resolvers.txt"))
        val toml = masterLaunch.files.getValue("client.toml")
        assertTrue(toml.contains("RX_TX_WORKERS = 8")); assertTrue(toml.contains("PACKET_DUPLICATION_COUNT = 5"))
        assertTrue(toml.contains("AUTO_DISABLE_TIMEOUT_SERVERS = false"))
        val storm = Sidecars.launch(JSONObject().put("kind", "stormdns").put("domain", "v.example.com").put("key", "k").put("resolvers", "4.4.4.4"))
        assertTrue(storm.files.getValue("client.toml").contains("UPLOAD_PACKET_DUPLICATION_COUNT = 5"))
    }

    @Test
    fun remoteDnsHijacksLookupsThroughTheProxy() {
        DnsTunnelPrefs.current = DnsTunnelPrefs.Values(remoteDns = "1.1.1.1")
        val root = JSONObject(SingBoxConfig.full(SingBoxConfig.spec(ConfigParser.parse("anytls://s@a.example.com:443#A")!!)!!, 1080))
        val dns = root.getJSONObject("dns")
        assertEquals("remote", dns.getString("final"))
        assertEquals("proxy", dns.getJSONArray("servers").getJSONObject(1).getString("detour"))
        assertEquals("hijack-dns", root.getJSONObject("route").getJSONArray("rules").getJSONObject(1).getString("action"))
        DnsTunnelPrefs.current = DnsTunnelPrefs.Values(remoteDns = "not an ip")
        assertFalse(JSONObject(SingBoxConfig.full(SingBoxConfig.spec(ConfigParser.parse("anytls://s@a.example.com:443#A")!!)!!, 1080)).getJSONObject("dns").has("final"))
    }

    @Test
    fun valuesRoundTrip() {
        val v = DnsTunnelPrefs.Values("1.1.1.1:53", "udp", listOf("a", "b"), 4, 3, true, "8.8.8.8")
        assertEquals(v, DnsTunnelPrefs.Values.fromJson(v.toJson()))
    }
}

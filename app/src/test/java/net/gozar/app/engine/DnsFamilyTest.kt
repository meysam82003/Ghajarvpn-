package net.gozar.app.engine

import net.gozar.app.ConfigParser
import net.gozar.app.ConfigShare
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFamilyTest {

    private val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private fun side(link: String): JSONObject = JSONObject(SingBoxConfig.spec(ConfigParser.parse(link)!!)!!).getJSONObject("sidecar")

    @Test
    fun vaydnsCommandLine() {
        val l = Sidecars.launch(side("vaydns://v.example.com?pubkey=$key&resolver=1.1.1.1&record=null&qname=120#v"))
        assertEquals("libvaydns.so", l.binary)
        assertEquals(listOf("-udp", "1.1.1.1:53", "-pubkey", key, "-domain", "v.example.com", "-listen", "127.0.0.1:{port}",
            "-record-type", "null", "-max-qname-len", "120"), l.args)
    }

    @Test
    fun noizdnsCommandLineWithAndWithoutNoiz() {
        assertEquals(listOf("-dot", "dns.example:853", "-pubkey", key, "-noiz", "-stealth", "n.example.com", "127.0.0.1:{port}"),
            Sidecars.launch(side("noizdns://n.example.com?pubkey=$key&transport=dot&resolver=dns.example:853&stealth=1#n")).args)
        assertEquals(listOf("-udp", "8.8.8.8:53", "-pubkey", key, "n.example.com", "127.0.0.1:{port}"),
            Sidecars.launch(side("noizdns://n.example.com?pubkey=$key&resolver=8.8.8.8&noiz=0#n")).args)
    }

    @Test
    fun masterFamilyWritesTomlAndResolvers() {
        listOf("masterdns", "stormdns", "cottendns").forEach { p ->
            val c = ConfigParser.parse("$p://s3cr%22et@m.example.com?resolver=8.8.8.8,1.1.1.1:5353&enc=3#m")!!
            val spec = JSONObject(SingBoxConfig.spec(c)!!)
            assertEquals("socks", spec.getJSONObject("outbound").getString("type"))
            val l = Sidecars.launch(spec.getJSONObject("sidecar"))
            assertTrue(l.socks)
            val toml = l.files.getValue("client.toml")
            assertTrue(toml, toml.contains("DOMAINS = [\"m.example.com\"]"))
            assertTrue(toml, toml.contains("DATA_ENCRYPTION_METHOD = 3"))
            assertTrue(toml, toml.contains("ENCRYPTION_KEY = \"s3cr\\\"et\""))
            assertTrue(toml, toml.contains("LISTEN_PORT = {port}"))
            assertEquals("8.8.8.8:53\n1.1.1.1:5353\n", l.files.getValue("resolvers.txt"))
            assertEquals(setOf("client.toml"), l.secretFiles)
            val back = ConfigParser.parse(ConfigShare.toLink(c))!!
            assertEquals(c.copy(id = "x"), back.copy(id = "x"))
        }
    }

    @Test
    fun dnsttFamilyLinksRoundTrip() {
        listOf(
            "vaydns://v.example.com?pubkey=$key&transport=udp&resolver=1.1.1.1:53&upstream=ssh&record=null&compat=1#v",
            "noizdns://u:p@n.example.com?pubkey=$key&transport=doh&doh=https%3A%2F%2Fdns.example%2Fdns-query&upstream=socks&noiz=1&stealth=0#n"
        ).forEach { link ->
            val c = ConfigParser.parse(link)!!
            assertEquals(c.copy(id = "x"), ConfigParser.parse(ConfigShare.toLink(c))!!.copy(id = "x"))
        }
    }
}

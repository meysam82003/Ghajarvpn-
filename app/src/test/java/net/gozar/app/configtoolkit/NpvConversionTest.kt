package net.gozar.app.configtoolkit

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An NPV file is converted into ordinary Ghajar profiles: after import the
 * original file is not needed, and every supported entry is a normal server.
 */
class NpvConversionTest {

    private fun npvt(lock: JSONObject? = null): ByteArray {
        val configs = org.json.JSONArray()
            .put(JSONObject().put("name", "DE vless").put("v2rayProfile", JSONObject()
                .put("configType", 5).put("server", "de.example.com").put("serverPort", 443)
                .put("password", "11111111-2222-3333-4444-555555555555").put("network", "ws").put("path", "/ws")
                .put("host", "cdn.example.com").put("security", "tls").put("sni", "cdn.example.com")))
            .put(JSONObject().put("name", "SSH payload").put("sshConfig", JSONObject()
                .put("sshHost", "ssh.example.com").put("sshPort", 22).put("sshUsername", "u").put("sshPassword", "p")
                .put("payload", "GET / HTTP/1.1[crlf]Host: bug.example.com[crlf]Upgrade: websocket[crlf][crlf]")
                .put("httpProxy", "10.0.0.1:8080").put("sshConfigType", "SSH-PROXY-PAYLOAD")))
            .put(JSONObject().put("name", "Home socks").put("socksConfig", JSONObject()
                .put("server", "1.2.3.4").put("serverPort", 1080).put("username", "a").put("password", "b")))
            .put(JSONObject().put("name", "Office http").put("httpConfig", JSONObject()
                .put("server", "5.6.7.8").put("serverPort", 3128)))
        val root = JSONObject().put("configs", configs)
        if (lock != null) root.put("lockConfig", lock)
        return ("NPVT1\n" + root.toString()).toByteArray()
    }

    @Test fun readableNpvtBecomesNormalProfiles() {
        val out = ImportRouter.decode(npvt(), "mine.npvt")
        assertTrue("got $out", out is ImportRouter.Outcome.Imported)
        val configs = (out as ImportRouter.Outcome.Imported).configs
        val vless = configs.single { it.protocol == "vless" }
        assertEquals("DE vless", vless.name); assertEquals("de.example.com", vless.address); assertEquals("ws", vless.network)
        val ssh = configs.single { it.protocol == "ssh" }
        assertEquals("SSH payload", ssh.name); assertEquals("ssh.example.com", ssh.address); assertEquals(22, ssh.port)
        assertEquals("u", ssh.uuid); assertEquals("p", ssh.password)
        val t = JSONObject(ssh.extra).getJSONObject("transport")
        assertEquals("http-proxy", t.getString("mode")); assertEquals("10.0.0.1", t.getString("proxyHost"))
        assertEquals(8080, t.getInt("proxyPort")); assertTrue(t.getString("payload").startsWith("GET / HTTP/1.1"))
        val socks = configs.single { it.protocol == "socks" }
        assertEquals("Home socks", socks.name); assertEquals(1080, socks.port)
        val http = configs.single { it.protocol == "http" }
        assertEquals("Office http", http.name); assertEquals(3128, http.port)
    }

    @Test fun authorLockPasswordIsAskedOnceAndChecked() {
        val bytes = npvt(JSONObject().put("isLocked", true).put("password", "s3cret"))
        assertEquals(ImportRouter.Outcome.NeedsPasskey, ImportRouter.decode(bytes, "locked.npvt"))
        assertEquals(ImportRouter.Outcome.WrongPasskey, ImportRouter.decode(bytes, "locked.npvt", "nope".toCharArray()))
        val ok = ImportRouter.decode(bytes, "locked.npvt", "s3cret".toCharArray())
        assertTrue("got $ok", ok is ImportRouter.Outcome.Imported && ok.configs.size == 4)
    }

    @Test fun oneBrokenEntryDoesNotCostTheOthers() {
        val root = JSONObject(String(npvt(), Charsets.UTF_8).removePrefix("NPVT1\n"))
        root.getJSONArray("configs").put(JSONObject().put("name", "bad").put("socksConfig",
            JSONObject().put("server", "bad host name").put("serverPort", 1080)))
        val out = ImportRouter.decode(("NPVT1\n" + root).toByteArray(), "mixed.npvt")
        assertTrue("got $out", out is ImportRouter.Outcome.Imported && out.configs.size == 4)
    }

    @Test fun sshTypesMapToGhajarTransportModes() {
        fun mode(o: JSONObject): String {
            val link = NpvContainer.link(JSONObject().put("name", "x").put("sshConfig", o.put("sshHost", "h.example.com").put("sshUsername", "u")))
            return Regex("[?&]mode=([a-z-]+)").find(link)?.groupValues?.get(1) ?: "direct"
        }
        assertEquals("direct", mode(JSONObject()))
        assertEquals("payload", mode(JSONObject().put("payload", "GET / HTTP/1.1[crlf][crlf]")))
        assertEquals("tls", mode(JSONObject().put("sni", "front.example.com")))
        assertEquals("payload-tls", mode(JSONObject().put("payload", "x").put("sshConfigType", "SSH-TLS-PAYLOAD")))
        assertEquals("https-proxy", mode(JSONObject().put("httpProxy", "p:443").put("sni", "s")))
        assertEquals("wss", mode(JSONObject().put("sshConfigType", "SSH-WS-TLS")))
    }
}

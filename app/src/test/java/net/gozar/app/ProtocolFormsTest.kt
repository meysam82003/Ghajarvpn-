package net.gozar.app

import net.gozar.app.engine.SingBoxConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolFormsTest {

    private fun ok(id: String, v: Map<String, String>) = ProtocolForms.build(id, v).getOrThrow()

    @Test
    fun openConnectFormLikeTheScreenshot() {
        val pem = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----"
        val c = ok("openconnect", mapOf("name" to "Office", "server" to "vpn.example.com", "port" to "8443", "flavor" to "gp",
            "user" to "bob", "pass" to "pw", "authgroup" to "Staff", "mtu" to "1300", "reconnect" to "60", "ua" to "AnyConnect",
            "os" to "win", "nodtls" to "true", "noipv6" to "true", "cert" to pem))
        assertEquals("openconnect", c.protocol); assertEquals("gp", c.mode); assertEquals(8443, c.port); assertEquals(1300, c.mtu)
        val ep = JSONObject(SingBoxConfig.full(SingBoxConfig.spec(c)!!, 1080)).getJSONArray("endpoints").getJSONObject(0)
        assertEquals("Staff", ep.getString("auth_group")); assertEquals("win", ep.getString("reported_os"))
        assertEquals("AnyConnect", ep.getString("user_agent")); assertEquals("60s", ep.getString("reconnect_timeout"))
        assertTrue(ep.getBoolean("no_udp")); assertTrue(ep.getBoolean("ipv6_disabled")); assertEquals(1300, ep.getInt("mtu"))
        assertEquals(pem, ep.getJSONObject("tls").getJSONArray("client_certificate").getString(0))
        // The share link carries every option except the certificate and key.
        val back = ConfigParser.parse(ConfigShare.toLink(c))!!
        assertEquals("Staff", back.extraJson().getString("authGroup")); assertTrue(!back.extraJson().has("clientCert"))
    }

    @Test
    fun everyFormBuildsAParsableProfile() {
        val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val wgKey = "aGVsbG8taGVsbG8taGVsbG8taGVsbG8taGVsbG8tMTI="
        val cases = mapOf(
            "sstp" to mapOf("server" to "s.example.com", "user" to "u", "pass" to "p", "auth" to "pap"),
            "softether" to mapOf("server" to "se.example.com", "hub" to "VPN", "user" to "u", "pass" to "p"),
            "ssh" to mapOf("server" to "h.example.com", "port" to "22", "user" to "u", "pass" to "p", "mode" to "payload-tls",
                "sni" to "cdn.example.com", "payload" to "GET / HTTP/1.1[crlf][crlf]"),
            "dnstt" to mapOf("variant" to "vaydns", "domain" to "t.example.com", "pubkey" to key, "transport" to "dot", "resolver" to "1.1.1.1:853"),
            "masterdns" to mapOf("variant" to "stormdns", "domain" to "v.example.com", "key" to "k", "resolvers" to "8.8.8.8\n1.1.1.1"),
            "tuic" to mapOf("server" to "t.example.com", "uuid" to "11111111-2222-3333-4444-555555555555", "pass" to "p"),
            "hysteria2" to mapOf("server" to "h.example.com", "pass" to "p", "obfs" to "o"),
            "anytls" to mapOf("server" to "a.example.com", "pass" to "p"),
            "juicity" to mapOf("server" to "j.example.com", "uuid" to "11111111-2222-3333-4444-555555555555", "pass" to "p"),
            "naive" to mapOf("server" to "n.example.com", "user" to "u", "pass" to "p", "quic" to "true"),
            "mieru" to mapOf("server" to "m.example.com", "port" to "2999", "user" to "u", "pass" to "p"),
            "wireguard" to mapOf("privkey" to wgKey, "address" to "10.0.0.2/32", "peerkey" to wgKey, "endpoint" to "198.51.100.7:51820", "jc" to "4", "s1" to "30"),
            "tor" to mapOf("bridges" to "obfs4 192.0.2.10:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=AAAA iat-mode=0")
        )
        val expect = mapOf("dnstt" to "vaydns", "masterdns" to "stormdns", "ssh" to "ssh", "naive" to "naive", "wireguard" to "amneziawg", "hysteria2" to "hysteria2")
        cases.forEach { (id, v) ->
            val c = ProtocolForms.build(id, v).getOrElse { throw AssertionError("$id: ${it.message}") }
            assertEquals(id, expect[id] ?: id, c.protocol)
        }
        assertEquals("k", ok("masterdns", cases.getValue("masterdns")).password)
        assertEquals("payload-tls", ok("ssh", cases.getValue("ssh")).extraJson().getJSONObject("transport").getString("mode"))
    }

    @Test
    fun unknownFormIdIsSafe() {
        assertEquals(null, ProtocolForms.formOrNull("__missing__"))
        val result = ProtocolForms.build("__missing__", emptyMap())
        assertTrue(result.isFailure)
        assertEquals("f_invalid", result.exceptionOrNull()?.message)
    }

    @Test
    fun missingRequiredFieldIsNamed() {
        val err = ProtocolForms.build("sstp", mapOf("server" to "s.example.com")).exceptionOrNull()
        assertEquals("f_user", err?.message)
    }
}

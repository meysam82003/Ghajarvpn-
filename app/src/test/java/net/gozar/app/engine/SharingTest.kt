package net.gozar.app.engine

import net.gozar.app.ConfigParser
import net.gozar.app.ProxyConfig
import net.gozar.app.sharing.DirectShare
import net.gozar.app.sharing.LocalSharePortal
import net.gozar.app.sharing.PhoneShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class SharingTest {

    private fun get(url: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 3000; c.readTimeout = 3000
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
        c.disconnect(); return code to body
    }

    @Test fun portalServesOnceWithTokenAndRefusesEverythingElse() {
        val p = LocalSharePortal("vless://x", allowLoopback = true)
        val url = p.start(InetAddress.getByName("127.0.0.1"))
        val base = url.substringBeforeLast('/')
        assertEquals(404, get("$base/wrong").first)
        val (code, body) = get(url)
        assertEquals(200, code); assertEquals("vless://x", body)
        Thread.sleep(200)
        assertFalse(p.isOpen)
        assertTrue(runCatching { get(url) }.let { it.isFailure || it.getOrNull()?.first != 200 })
    }

    @Test fun portalRefusesPublicAddressesAndLocksOutGuessing() {
        assertTrue(runCatching { LocalSharePortal("x").start(InetAddress.getByName("8.8.8.8")) }.isFailure)
        assertTrue(runCatching { LocalSharePortal("x").start(InetAddress.getByName("127.0.0.1")) }.isFailure)
        val p = LocalSharePortal("x", oneTime = false, maxBadAttempts = 3, allowLoopback = true)
        val url = p.start(InetAddress.getByName("127.0.0.1"))
        repeat(3) { runCatching { get(url.substringBeforeLast('/') + "/guess$it") } }
        Thread.sleep(200)
        assertFalse(p.isOpen)
    }

    @Test fun portalExpires() {
        var t = 1_000L
        val p = LocalSharePortal("x", ttlMs = 1000, oneTime = false, now = { t }, allowLoopback = true)
        val url = p.start(InetAddress.getByName("127.0.0.1"))
        assertEquals(200, get(url).first)
        t += 2000
        assertFalse(p.isOpen)
        p.revoke()
    }

    @Test fun phoneShareOnlyWhereXrayCarriesTheSession() {
        assertTrue(PhoneShare.supports(ConfigParser.parse("vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=tls#a")!!))
        assertFalse(PhoneShare.supports(ConfigParser.parse("tuic://u:p@t.example.com:443#t")!!))
        assertFalse(PhoneShare.supports(ProxyConfig(name = "i", protocol = "ikev2", address = "h", port = 500)))
        assertFalse(PhoneShare.supports(ProxyConfig(name = "j", protocol = "juicity", address = "h", port = 443)))
    }

    @Test fun directShareOffersOnlyRealFormats() {
        val vless = ConfigParser.parse("vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=tls#a")!!
        val kinds = DirectShare.exports(vless, DirectShare.Target.IPHONE).map { it.kind }
        assertTrue(DirectShare.Kind.LINK in kinds)
        assertFalse(DirectShare.Kind.SINGBOX_JSON in kinds)
        val tuic = ConfigParser.parse("tuic://11111111-2222-3333-4444-555555555555:p@t.example.com:443?sni=t.example.com#t")!!
        val desk = DirectShare.exports(tuic, DirectShare.Target.WINDOWS)
        val json = desk.first { it.kind == DirectShare.Kind.SINGBOX_JSON }.text
        assertTrue(json.contains("\"tuic\"") && json.contains("2080"))
        val wgReserved = ProxyConfig(name = "w", protocol = "wireguard", address = "198.51.100.7", port = 51820,
            privateKey = "k", publicKey = "p", localAddress = "10.0.0.2/32", reserved = "1,2,3")
        assertFalse(DirectShare.exports(wgReserved, DirectShare.Target.ANDROID).any { it.kind == DirectShare.Kind.WIREGUARD_CONF })
        assertTrue(DirectShare.exports(vless.copy(locked = true), DirectShare.Target.ANDROID).isEmpty())
    }
}

package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The options added from the other clients are additive, and "additive" is a
 * claim worth testing: a config saved before they existed has to round-trip
 * and behave exactly as it did.
 */
class AdditiveOptionsTest {

    private fun config() = ProxyConfig(
        name = "s", protocol = "vless", address = "example.com", port = 443
    )

    @Test
    fun `the new fields default to off and blank`() {
        val c = config()
        assertEquals("", c.cipherSuites)
        assertFalse(c.randomSubdomain)
    }

    /** A config written by an older version has neither key. */
    @Test
    fun `a config json without the new keys decodes to the defaults`() {
        val json = config().toJson()
        json.remove("cipherSuites")
        json.remove("randomSubdomain")
        val back = ProxyConfig.fromJson(json)
        assertEquals("", back.cipherSuites)
        assertFalse(back.randomSubdomain)
    }

    @Test
    fun `the new fields survive a round trip`() {
        val c = config().copy(
            cipherSuites = "TLS_AES_128_GCM_SHA256:TLS_AES_256_GCM_SHA384",
            randomSubdomain = true
        )
        val back = ProxyConfig.fromJson(c.toJson())
        assertEquals(c.cipherSuites, back.cipherSuites)
        assertTrue(back.randomSubdomain)
    }

    @Test
    fun `a random label is a valid dns label in front of the host`() {
        val out = ConfigBuilder.randomLabel("example.com")
        assertTrue("got $out", out.endsWith(".example.com"))
        val label = out.substringBefore('.')
        assertTrue("label length ${label.length}", label.length in 5..9)
        assertTrue("label chars: $label", label.all { it.isLetterOrDigit() && it.lowercaseChar() == it })
    }

    @Test
    fun `a random label differs between connects`() {
        // Two draws of at least 36^5 possibilities colliding would be a bug in
        // the generator rather than bad luck.
        val a = ConfigBuilder.randomLabel("example.com")
        val b = ConfigBuilder.randomLabel("example.com")
        assertNotEquals(a, b)
    }

    @Test
    fun `a blank host gets no label`() {
        assertEquals("", ConfigBuilder.randomLabel(""))
    }

    /** The subscription's new field is additive in the same way. */
    @Test
    fun `a subscription without a service username decodes blank`() {
        val sub = Subscription(name = "n", url = "https://example.com/sub")
        val json = sub.toJson()
        json.remove("serviceUsername")
        assertEquals("", Subscription.fromJson(json).serviceUsername)
    }

    @Test
    fun `a subscription service username survives a round trip`() {
        val sub = Subscription(name = "n", url = "https://e.com/s", serviceUsername = "abc_123")
        assertEquals("abc_123", Subscription.fromJson(sub.toJson()).serviceUsername)
    }
}

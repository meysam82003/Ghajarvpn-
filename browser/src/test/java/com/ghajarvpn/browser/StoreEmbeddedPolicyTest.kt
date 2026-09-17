package com.ghajarvpn.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreEmbeddedPolicyTest {

    @Test
    fun `trusted https store urls are allowed`() {
        val decision = StoreEmbeddedPolicy.decide("https://httpuser87890.ir/terms")
        assertTrue(decision.allowed)
        assertTrue(decision.uri!!.startsWith("https://httpuser87890.ir"))
    }

    @Test
    fun `untrusted hosts are rejected`() {
        assertFalse(StoreEmbeddedPolicy.decide("https://evil.example.com/terms").allowed)
    }

    @Test
    fun `http and userinfo are rejected`() {
        assertFalse(StoreEmbeddedPolicy.decide("http://httpuser87890.ir/terms").allowed)
        assertFalse(StoreEmbeddedPolicy.decide("https://user:pass@httpuser87890.ir/terms").allowed)
    }

    @Test
    fun `non-https schemes are rejected outright`() {
        for (raw in listOf("file:///etc/passwd", "content://media/x", "javascript:alert(1)", "about:blank", "data:text/html,x")) {
            val decision = StoreEmbeddedPolicy.decide(raw)
            assertFalse("scheme rejected: $raw", decision.allowed)
            assertNull(decision.uri)
        }
    }

    @Test
    fun `navigation stays inside the store origin`() {
        val entry = "https://httpuser87890.ir/help"
        assertTrue(StoreEmbeddedPolicy.navigationAllowed(entry, "https://httpuser87890.ir/faq"))
        assertTrue(StoreEmbeddedPolicy.navigationAllowed(entry, "https://support.httpuser87890.ir/contact"))
        assertTrue(StoreEmbeddedPolicy.navigationAllowed(entry, "https://panel.httpuser87890.ir/account"))
    }

    @Test
    fun `navigation to a foreign host is blocked`() {
        val entry = "https://httpuser87890.ir/help"
        assertFalse(StoreEmbeddedPolicy.navigationAllowed(entry, "https://evil.example.com/"))
        assertFalse(StoreEmbeddedPolicy.navigationAllowed(entry, "http://httpuser87890.ir/insecure"))
        assertFalse(StoreEmbeddedPolicy.navigationAllowed(entry, "https://user@httpuser87890.ir/phish"))
        assertFalse(StoreEmbeddedPolicy.navigationAllowed(entry, "javascript:alert(1)"))
    }

    @Test
    fun `invalid navigation targets are blocked`() {
        assertFalse(StoreEmbeddedPolicy.navigationAllowed("https://httpuser87890.ir", "::::"))
        assertFalse(StoreEmbeddedPolicy.navigationAllowed("https://httpuser87890.ir", null))
    }

}

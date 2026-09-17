package com.ghajarvpn.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRequestPolicyExtraTest {

    private val settings = BrowserSettings(trackerBlocking = true, adBlocking = true)

    @Test
    fun `normalizeInput adds https and upgrades http`() {
        assertEquals("https://example.com", BrowserRequestPolicy.normalizeInput("example.com", settings))
        assertEquals("https://example.com", BrowserRequestPolicy.normalizeInput("http://example.com", settings))
    }

    @Test
    fun `normalizeInput keeps explicit scheme`() {
        assertEquals("ftp://files.example.com", BrowserRequestPolicy.normalizeInput("ftp://files.example.com", settings))
    }

    @Test
    fun `normalizeInput sends phrases to search engine`() {
        val url = BrowserRequestPolicy.normalizeInput("گوشی قاجار", settings)
        assertTrue(url.startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun `normalizeInput blank goes home`() {
        assertEquals(BrowserTab.HOME_URL, BrowserRequestPolicy.normalizeInput("   ", settings))
    }

    @Test
    fun `safeExternal rejects userinfo and non-http schemes`() {
        assertFalse(BrowserRequestPolicy.safeExternal("javascript:alert(1)"))
        assertFalse(BrowserRequestPolicy.safeExternal("file:///etc/passwd"))
        assertFalse(BrowserRequestPolicy.safeExternal("https://user:pass@example.com/"))
        assertFalse(BrowserRequestPolicy.safeExternal(""))
        assertTrue(BrowserRequestPolicy.safeExternal("https://example.com/page?x=1"))
    }

    @Test
    fun `blocked matches trackers and ads on subdomains`() {
        assertTrue(BrowserRequestPolicy.blocked("https://www.google-analytics.com/collect.js", settings))
        assertTrue(BrowserRequestPolicy.blocked("https://stream.yektanet.com/x", BrowserSettings(adBlocking = true, trackerBlocking = false)))
        assertFalse(BrowserRequestPolicy.blocked("https://example.com/google-analytics.com.js", settings))
        assertFalse(BrowserRequestPolicy.blocked("https://example.com", BrowserSettings(trackerBlocking = false, adBlocking = false)))
    }

    @Test
    fun `invalid urls are treated as blocked`() {
        assertTrue(BrowserRequestPolicy.blocked("::::", settings))
    }
}

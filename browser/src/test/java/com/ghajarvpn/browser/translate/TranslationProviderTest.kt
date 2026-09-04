package com.ghajarvpn.browser.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationProviderTest {

    @Test
    fun `web provider builds translate url with target language`() {
        val url = GoogleTranslateWebProvider().translatedUrl("https://example.com/page?a=1", "fa")
        assertTrue(url!!.startsWith("https://fa.translate.goog/?sl=auto&tl=fa&u="))
        assertTrue(url.endsWith("https%3A%2F%2Fexample.com%2Fpage%3Fa%3D1"))
    }

    @Test
    fun `web provider rejects unsafe urls`() {
        val provider = GoogleTranslateWebProvider()
        assertNull(provider.translatedUrl("file:///etc/passwd", "fa"))
        assertNull(provider.translatedUrl("javascript:alert(1)", "fa"))
        assertNull(provider.translatedUrl("", "fa"))
    }

    @Test
    fun `web provider is credential free and available`() {
        val provider = GoogleTranslateWebProvider()
        assertFalse(provider.requiresCredentials)
        assertTrue(provider.isAvailable())
    }

    @Test
    fun `registry returns the first available provider`() {
        val registry = TranslationRegistry
        assertEquals("google_web", registry.active()?.id)
        val before = registry.all().size
        registry.register(object : TranslationProvider {
            override val id = "unavailable"
            override val displayName = "غیرفعال"
            override val requiresCredentials = true
            override fun isAvailable() = false
            override fun translatedUrl(url: String, targetLanguage: String) = null
        })
        assertEquals(before + 1, registry.all().size)
        assertEquals("google_web", registry.active()?.id)
    }

    @Test
    fun `default preferred language is persian`() {
        assertEquals("fa", TranslationRegistry.preferredLanguage)
    }
}

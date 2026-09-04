package com.ghajarvpn.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistence contracts of BrowserRepository expressed against a fake
 * SharedPreferences-like JSON document so both normal and private behaviour
 * can be verified on the JVM.
 */
class BrowserRepositoryPolicyTest {

    private class FakeStorage : BrowserDocumentStore {
        private val values = mutableMapOf<String, String>()
        override fun load(key: String): String? = values[key]
        override fun save(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private fun repository() = BrowserRepository(FakeStorage())

    @Test
    fun `private tabs are never persisted`() {
        val repo = repository()
        val normal = BrowserTab(url = "https://example.com", title = "Example")
        val private = BrowserTab(url = "https://secret.example", title = "Secret", private = true)
        repo.saveTabs(listOf(normal, private))
        val restored = repo.restoreTabs()
        assertEquals(1, restored.size)
        assertFalse(restored.any { it.private })
        assertEquals(normal.id, restored.first().id)
    }

    @Test
    fun `normal tabs survive round-trip`() {
        val repo = repository()
        val tabs = listOf(
            BrowserTab(url = "https://a.example", title = "A"),
            BrowserTab(url = "https://b.example", title = "B")
        )
        repo.saveTabs(tabs)
        val restored = repo.restoreTabs()
        assertEquals(listOf("https://a.example", "https://b.example"), restored.map { it.url })
    }

    @Test
    fun `history deduplicates and keeps newest first`() {
        val repo = repository()
        repo.addHistory("first", "https://example.com")
        repo.addHistory("second", "https://example2.com")
        repo.addHistory("updated", "https://example.com")
        val history = repo.history()
        assertEquals(2, history.size)
        assertEquals("https://example.com", history.first().url)
        assertEquals("updated", history.first().title)
    }

    @Test
    fun `history ignores non-http urls`() {
        val repo = repository()
        repo.addHistory("local", "file:///etc/passwd")
        assertTrue(repo.history().isEmpty())
    }

    @Test
    fun `bookmarks toggle on and off`() {
        val repo = repository()
        assertTrue(repo.toggleBookmark("https://example.com"))
        assertTrue(repo.bookmarks().contains("https://example.com"))
        assertFalse(repo.toggleBookmark("https://example.com"))
        assertTrue(repo.bookmarks().isEmpty())
    }

    @Test
    fun `settings round-trip`() {
        val repo = repository()
        repo.saveSettings(BrowserSettings(javaScript = false, adBlocking = true, darkPages = true))
        val restored = repo.settings()
        assertFalse(restored.javaScript)
        assertTrue(restored.adBlocking)
        assertTrue(restored.darkPages)
    }

    @Test
    fun `clearBrowsingData removes history tabs and bookmarks`() {
        val repo = repository()
        repo.addHistory("t", "https://example.com")
        repo.toggleBookmark("https://example.com")
        repo.saveTabs(listOf(BrowserTab(url = "https://example.com")))
        repo.clearBrowsingData()
        assertTrue(repo.history().isEmpty())
        assertTrue(repo.bookmarks().isEmpty())
        // restoreTabs re-seeds an empty default tab.
        assertEquals(1, repo.restoreTabs().size)
    }
}

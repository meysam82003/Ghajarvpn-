package com.ghajarvpn.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePermissionsStoreTest {

    private class FakeStore : BrowserDocumentStore {
        val values = mutableMapOf<String, String>()
        override fun load(key: String): String? = values[key]
        override fun save(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private fun store() = SitePermissionsStore(FakeStore())

    @Test
    fun `default state is ask`() {
        assertEquals(SitePermissionState.ASK, store().state("example.com", SitePermission.CAMERA))
    }

    @Test
    fun `remembered allow and block persist`() {
        val s = store()
        s.remember("example.com", SitePermission.CAMERA, SitePermissionState.ALLOW)
        s.remember("other.com", SitePermission.MICROPHONE, SitePermissionState.BLOCK)
        assertEquals(SitePermissionState.ALLOW, s.state("example.com", SitePermission.CAMERA))
        assertEquals(SitePermissionState.BLOCK, s.state("other.com", SitePermission.MICROPHONE))
    }

    @Test
    fun `forget removes all resources of one origin`() {
        val s = store()
        s.remember("example.com", SitePermission.CAMERA, SitePermissionState.ALLOW)
        s.remember("example.com", SitePermission.MICROPHONE, SitePermissionState.BLOCK)
        s.forget("example.com")
        assertEquals(SitePermissionState.ASK, s.state("example.com", SitePermission.CAMERA))
        assertEquals(SitePermissionState.ASK, s.state("example.com", SitePermission.MICROPHONE))
    }

    @Test
    fun `decisions lists explicit choices only`() {
        val s = store()
        s.remember("example.com", SitePermission.CAMERA, SitePermissionState.ALLOW)
        val decisions = s.decisions()
        assertEquals(SitePermissionState.ALLOW, decisions["example.com"]?.get(SitePermission.CAMERA))
        assertTrue(SitePermission.GEOLOCATION !in (decisions["example.com"] ?: emptyMap()))
    }

    @Test
    fun `originOf keeps https hosts and drops the rest`() {
        val s = store()
        assertEquals("example.com", s.originOf("https://example.com/page?q=1"))
        assertEquals("", s.originOf("http://insecure.example.com/"))
        assertEquals("", s.originOf("file:///tmp/x"))
        assertEquals("", s.originOf(""))
    }

    @Test
    fun `os permission mapping is total`() {
        for (resource in SitePermission.entries) {
            assertTrue(!SitePermissionsStore.osPermissionFor(resource).isNullOrBlank())
        }
    }
}

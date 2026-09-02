package com.ghajarvpn.browser.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MediaHeaderProviderTest {
    @Test fun keepsOnlyPlaybackHeadersAndRejectsInjection() {
        val result = MediaHeaderProvider.sanitize(mapOf(
            "Cookie" to "session=memory-only",
            "Referer" to "https://example.com/watch",
            "X-Unsafe" to "not-forwarded",
            "Authorization" to "Bearer value\r\nInjected: yes"
        ))
        assertEquals("session=memory-only", result["Cookie"])
        assertEquals("https://example.com/watch", result["Referer"])
        assertFalse(result.keys.any { it.equals("X-Unsafe", true) })
        assertFalse(result.keys.any { it.equals("Authorization", true) })
    }
}

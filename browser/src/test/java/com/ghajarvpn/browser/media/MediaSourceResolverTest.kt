package com.ghajarvpn.browser.media

import org.junit.Assert.*
import org.junit.Test

class MediaSourceResolverTest {
    @Test fun resolvesStandardMedia() {
        assertEquals(MediaKind.PROGRESSIVE, MediaSourceResolver.resolve("https://cdn.example/video.mp4")?.kind)
        assertEquals(MediaKind.HLS, MediaSourceResolver.resolve("https://cdn.example/live.m3u8")?.kind)
        assertEquals(MediaKind.DASH, MediaSourceResolver.resolve("https://cdn.example/manifest.mpd")?.kind)
        assertEquals(MediaKind.PROGRESSIVE, MediaSourceResolver.resolve("https://cdn.example/id", "video/webm")?.kind)
    }

    @Test fun rejectsUnsafeOrProtectedMedia() {
        assertNull(MediaSourceResolver.resolve("file:///secret.mp4"))
        assertNull(MediaSourceResolver.resolve("https://user:pass@example.com/video.mp4"))
        assertNull(MediaSourceResolver.resolve("https://example.com/widevine/video.mpd"))
        assertNull(MediaSourceResolver.resolve("https://example.com/video.mp4\n"))
        assertNull(MediaSourceResolver.resolve("https://example.com/" + "a".repeat(MediaSourceResolver.MAX_MEDIA_URL_LENGTH)))
    }

    @Test fun discoveryObservesDynamicMediaWithoutInterceptingPageRequests() {
        val script = BrowserMediaBridge.DISCOVERY_SCRIPT
        assertTrue(script.contains("new MutationObserver"))
        assertTrue(script.contains("new PerformanceObserver"))
        assertTrue(script.contains("addEventListener('play'"))
        assertFalse(script.contains("window.fetch ="))
        assertFalse(script.contains("XMLHttpRequest.prototype"))
    }
}

package com.ghajarvpn.downloads

import org.junit.Assert.*
import org.junit.Test

class DownloadPlanningTest {
    @Test fun rangesCoverEveryByteExactlyOnce() {
        val ranges = DownloadPlanning.ranges(101, 6)
        assertEquals(0L, ranges.first().start)
        assertEquals(100L, ranges.last().endInclusive)
        assertEquals(101L, ranges.sumOf { it.length })
        ranges.zipWithNext().forEach { (left, right) -> assertEquals(left.endInclusive + 1, right.start) }
    }

    @Test fun connectionCountIsBoundedAndSizeAware() {
        assertEquals(1, DownloadPlanning.smartConnections(1024))
        assertEquals(2, DownloadPlanning.smartConnections(16L * 1024 * 1024))
        assertEquals(4, DownloadPlanning.smartConnections(128L * 1024 * 1024))
        assertEquals(6, DownloadPlanning.smartConnections(512L * 1024 * 1024))
        assertEquals(8, DownloadPlanning.ranges(80, 99).size)
    }

    @Test fun parsesOnlyValidContentRangeTotals() {
        assertEquals(900L, DownloadPlanning.totalFromContentRange("bytes 0-0/900"))
        assertNull(DownloadPlanning.totalFromContentRange("bytes */900"))
        assertNull(DownloadPlanning.totalFromContentRange("900"))
    }

    @Test fun filenamesArePortableAndCollisionFree() {
        assertEquals("video_clip.mp4", DownloadNames.sanitize("video:clip.mp4"))
        assertEquals("video (2).mp4", DownloadNames.collisionFree("video.mp4", setOf("video.mp4", "video (1).mp4")))
    }

    @Test fun rejectsCredentialsAndNonHttpUrls() {
        assertTrue(DownloadPlanning.safeHttpUrl("https://example.test/file"))
        assertFalse(DownloadPlanning.safeHttpUrl("https://user:pass@example.test/file"))
        assertFalse(DownloadPlanning.safeHttpUrl("file:///tmp/file"))
    }

    @Test fun acceptsOnlyRequiredHeadersAndRejectsInjection() {
        val clean = DownloadHeaderPolicy.sanitize(mapOf(
            "Cookie" to "sid=secret",
            "Authorization" to "Bearer secret",
            "X-Debug" to "leak",
            "Referer" to "https://example.test/\r\nInjected: yes"
        ))
        assertEquals(setOf("Cookie", "Authorization"), clean.keys)
        assertFalse(clean.toString().contains("X-Debug"))
    }
}

package com.ghajarvpn.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlannerTest {

    @Test
    fun `segment count respects range support and size`() {
        assertEquals(1, DownloadPlanner.segmentCount(false, 100L * 1024 * 1024))
        assertEquals(1, DownloadPlanner.segmentCount(true, 1024L))
        assertEquals(8, DownloadPlanner.segmentCount(true, 512L * 1024 * 1024))
        assertEquals(1, DownloadPlanner.segmentCount(true, 2L * 1024 * 1024))
    }

    @Test
    fun `segments tile the whole file without gaps`() {
        val total = 10_000_003L
        val segments = DownloadPlanner.planSegments(total, 4)
        assertEquals(4, segments.size)
        assertEquals(0L, segments.first().start)
        assertEquals(total - 1, segments.last().end)
        segments.reduce { acc, seg ->
            assertEquals(acc.end + 1, seg.start)
            seg
        }
        val summed = segments.sumOf { it.end - it.start + 1 }
        assertEquals(total, summed)
    }

    @Test
    fun `single stream plan when unknown size`() {
        val segments = DownloadPlanner.planSegments(-1L, 8)
        assertEquals(1, segments.size)
        assertEquals(0L, segments.first().start)
    }

    @Test
    fun `progress percent clamps`() {
        val item = DownloadItem("1", "https://x/f.bin", "f.bin", totalBytes = 200, downloadedBytes = 100)
        assertEquals(50, DownloadPlanner.progressPercent(item))
        val overflow = item.copy(downloadedBytes = 500)
        assertEquals(100, DownloadPlanner.progressPercent(overflow))
    }

    @Test
    fun `eta handles zero speed`() {
        assertEquals(-1L, DownloadPlanner.etaSeconds(0, 1000))
        assertEquals(10L, DownloadPlanner.etaSeconds(100, 1000))
        assertEquals(0L, DownloadPlanner.etaSeconds(100, 0))
    }

    @Test
    fun `duplicate names get numbered suffixes`() {
        assertEquals("a.bin", DownloadPlanner.resolveDuplicateName(emptySet(), "a.bin"))
        assertEquals("a (2).bin", DownloadPlanner.resolveDuplicateName(setOf("a.bin"), "a.bin"))
        assertEquals("a (3).bin", DownloadPlanner.resolveDuplicateName(setOf("a.bin", "a (2).bin"), "a.bin"))
        assertEquals("file (2)", DownloadPlanner.resolveDuplicateName(setOf("file"), "file"))
    }

    @Test
    fun `duplicate resolution strips path separators`() {
        val name = DownloadPlanner.resolveDuplicateName(emptySet(), "../evil.bin")
        assertNotEquals("../evil.bin", name)
        assertTrue(!name.contains('/'))
    }

    @Test
    fun `state machine is safe`() {
        assertEquals(DownloadState.PAUSED, DownloadPlanner.nextState(DownloadState.RUNNING, DownloadState.PAUSED))
        assertEquals(DownloadState.RUNNING, DownloadPlanner.nextState(DownloadState.PAUSED, DownloadState.RUNNING))
        // COMPLETED and CANCELLED are absorbing: finished downloads cannot change state.
        assertEquals(DownloadState.COMPLETED, DownloadPlanner.nextState(DownloadState.COMPLETED, DownloadState.CANCELLED))
        assertEquals(DownloadState.RUNNING, DownloadPlanner.nextState(DownloadState.FAILED, DownloadState.RUNNING))
        // WAITING_WIFI -> RUNNING is a real transition (Wi-Fi became available).
        assertEquals(DownloadState.RUNNING, DownloadPlanner.nextState(DownloadState.WAITING_WIFI, DownloadState.RUNNING))
    }

    @Test
    fun `range reply parser`() {
        val parsed = DownloadPlanner.parseRangeReply("bytes 100-199/500", 206)
        assertEquals(Triple(100L, 199L, 500L), parsed)
        assertNull(DownloadPlanner.parseRangeReply("bytes 0-1/2", 200))
        assertNull(DownloadPlanner.parseRangeReply(null, 206))
        assertNull(DownloadPlanner.parseRangeReply("garbage", 206))
    }

    @Test
    fun `sha256 hex is stable`() {
        val digest = DownloadPlanner.sha256Hex("ghajar".toByteArray())
        assertEquals(64, digest.length)
        assertEquals(digest, DownloadPlanner.sha256Hex("ghajar".toByteArray()))
        assertNotEquals(digest, DownloadPlanner.sha256Hex("ghajar2".toByteArray()))
    }
}

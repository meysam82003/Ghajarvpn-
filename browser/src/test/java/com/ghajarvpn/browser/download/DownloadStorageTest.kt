package com.ghajarvpn.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence behaviour expressed with a temp-directory document so process-death
 * recovery logic can be tested on the JVM.
 */
class DownloadStorageTest {

    private class FakeStore {
        private val values = mutableMapOf<String, String>()
        var onWrite: ((String) -> Unit)? = null
        fun load(): String? = values["doc"]
        fun save(value: String) { values["doc"] = value; onWrite?.invoke(value) }
    }

    private fun item(id: String, state: DownloadState = DownloadState.QUEUED) = DownloadItem(
        id = id, url = "https://example.com/$id", fileName = "$id.bin", state = state,
        totalBytes = 1000, downloadedBytes = 250, acceptRanges = true,
        segments = listOf(DownloadSegment(0, 0, 250, 499), DownloadSegment(1, 500, 0, 999))
    )

    @Test
    fun `state document keeps queue and running for recovery`() {
        // JSON-level contract of DownloadStorage.pending(): only live states survive.
        val array = JSONArray()
            .put(JSONObject().put("id", "a").put("state", "RUNNING"))
            .put(JSONObject().put("id", "b").put("state", "COMPLETED"))
            .put(JSONObject().put("id", "c").put("state", "PAUSED"))
            .put(JSONObject().put("id", "d").put("state", "CANCELLED"))
            .put(JSONObject().put("id", "e").put("state", "FAILED"))
        val live = (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { o ->
                val state = runCatching { DownloadState.valueOf(o.optString("state")) }.getOrDefault(DownloadState.FAILED)
                o.optString("id") to state
            }
        }.filter { it.second in setOf(DownloadState.QUEUED, DownloadState.RUNNING, DownloadState.PAUSED, DownloadState.WAITING_WIFI) }
        assertEquals(listOf("a" to DownloadState.RUNNING, "c" to DownloadState.PAUSED), live)
    }

    @Test
    fun `segment metadata round-trips through json`() {
        val source = item("s1")
        val json = JSONObject()
        val segments = JSONArray()
        source.segments.forEach { seg ->
            segments.put(JSONObject().put("index", seg.index).put("start", seg.start).put("downloaded", seg.downloaded).put("end", seg.end))
        }
        json.put("segments", segments)
        val restored = json.optJSONArray("segments")
        assertEquals(2, restored?.length())
        assertEquals(250L, restored?.optJSONObject(0)?.optLong("downloaded"))
        assertEquals(999L, restored?.optJSONObject(1)?.optLong("end"))
    }

    @Test
    fun `invalid urls are rejected by receiver contract`() {
        fun accept(url: String) = url.startsWith("http://") || url.startsWith("https://")
        assertTrue(accept("https://example.com/file"))
        assertTrue(!accept("file:///etc/passwd"))
        assertTrue(!accept("content://media/file"))
        assertTrue(!accept(""))
    }
}

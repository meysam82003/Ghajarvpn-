package com.ghajarvpn.browser.download

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persists download metadata as a JSON document file. One file, atomic rename
 * writes, easy recovery after process death or reboot; richer than scattered
 * SharedPreferences entries but without pulling a database into the module.
 */
class DownloadStorage(context: Context) {
    private val file = File(context.applicationContext.filesDir, "ghajar_downloads.json")
    private val lock = Any()

    fun load(): MutableList<DownloadItem> = synchronized(lock) {
        runCatching {
            val array = JSONArray(file.takeIf { it.exists() }?.readText() ?: "[]")
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.toItem() }
        }.getOrDefault(emptyList()).toMutableList()
    }

    fun save(items: List<DownloadItem>) = synchronized(lock) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(array.toString())
            tmp.delete()
        }
    }

    fun pending(): List<DownloadItem> = load().filter {
        it.state in setOf(DownloadState.QUEUED, DownloadState.RUNNING, DownloadState.PAUSED, DownloadState.WAITING_WIFI)
    }

    private fun JSONObject.toItem(): DownloadItem? {
        val id = optString("id") ?: return null
        if (id.isBlank()) return null
        val segments = mutableListOf<DownloadSegment>()
        optJSONArray("segments")?.let { array ->
            for (i in 0 until array.length()) {
                val s = array.optJSONObject(i) ?: continue
                segments += DownloadSegment(s.optInt("index"), s.optLong("start"), s.optLong("downloaded"), s.optLong("end"))
            }
        }
        return DownloadItem(
            id = id,
            url = optString("url", ""),
            fileName = optString("fileName", "download"),
            mimeType = optString("mime", ""),
            cookies = optString("cookies", ""),
            referer = optString("referer", ""),
            userAgent = optString("ua", ""),
            directory = optString("directory", ""),
            totalBytes = optLong("total", -1L),
            downloadedBytes = optLong("downloaded", 0L),
            state = runCatching { DownloadState.valueOf(optString("state", "QUEUED")) }.getOrDefault(DownloadState.QUEUED),
            segments = segments,
            acceptRanges = optBoolean("ranges", false),
            eTag = optString("etag", ""),
            lastModified = optString("lastModified", ""),
            sha256 = optString("sha256", ""),
            error = optString("error", ""),
            private = optBoolean("private", false),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
            completedAt = optLong("completedAt", 0L)
        )
    }

    private fun DownloadItem.toJson(): JSONObject {
        val segments = JSONArray()
        this.segments.forEach { segment ->
            segments.put(JSONObject()
                .put("index", segment.index)
                .put("start", segment.start)
                .put("downloaded", segment.downloaded)
                .put("end", segment.end))
        }
        return JSONObject()
            .put("id", id)
            .put("url", url)
            .put("fileName", fileName)
            .put("mime", mimeType)
            .put("cookies", cookies)
            .put("referer", referer)
            .put("ua", userAgent)
            .put("directory", directory)
            .put("total", totalBytes)
            .put("downloaded", downloadedBytes)
            .put("state", state.name)
            .put("segments", segments)
            .put("ranges", acceptRanges)
            .put("etag", eTag)
            .put("lastModified", lastModified)
            .put("sha256", sha256)
            .put("error", error)
            .put("private", private)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("completedAt", completedAt)
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

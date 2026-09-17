package com.ghajarvpn.browser.download

import java.security.MessageDigest

/** Lifecycle of a Ghajar download. States move only through [DownloadPlanner.nextState]. */
enum class DownloadState { QUEUED, RUNNING, PAUSED, WAITING_WIFI, COMPLETED, FAILED, CANCELLED }

data class DownloadSegment(
    val index: Int,
    val start: Long,
    var downloaded: Long,
    val end: Long
)

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String = "",
    val cookies: String = "",
    val referer: String = "",
    val userAgent: String = "",
    val directory: String = "",
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val state: DownloadState = DownloadState.QUEUED,
    val segments: List<DownloadSegment> = emptyList(),
    val acceptRanges: Boolean = false,
    val eTag: String = "",
    val lastModified: String = "",
    val sha256: String = "",
    val error: String = "",
    val private: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L
)

/**
 * Pure planning logic for the download engine: range detection, segment layout,
 * resume targets, ETA and duplicate naming. Everything here is JVM-testable.
 */
object DownloadPlanner {

    val SEGMENT_CHOICES = intArrayOf(1, 2, 4, 8)

    fun segmentCount(acceptRanges: Boolean, totalBytes: Long): Int {
        if (!acceptRanges || totalBytes <= 0) return 1
        val usable = SEGMENT_CHOICES.filter { totalBytes >= it * 8L * 1024 * 1024 }
        return usable.maxOrNull() ?: 1
    }

    fun planSegments(totalBytes: Long, count: Int): List<DownloadSegment> {
        if (totalBytes <= 0 || count <= 1) return listOf(DownloadSegment(0, 0, 0, totalBytes - 1))
        val base = totalBytes / count
        val remainder = totalBytes % count
        var start = 0L
        return (0 until count).map { index ->
            val length = base + if (index < remainder) 1 else 0
            val segment = DownloadSegment(index, start, 0, start + length - 1)
            start += length
            segment
        }
    }

    fun progressPercent(item: DownloadItem): Int {
        if (item.totalBytes <= 0) return 0
        return ((item.downloadedBytes * 100) / item.totalBytes).toInt().coerceIn(0, 100)
    }

    fun etaSeconds(bytesPerSecond: Long, remaining: Long): Long {
        if (bytesPerSecond <= 0 || remaining < 0) return -1
        if (remaining == 0L) return 0
        return remaining / bytesPerSecond
    }

    /** Duplicate names get "name (2).ext" style suffixes; null bytes are stripped. */
    fun resolveDuplicateName(existing: Set<String>, requested: String): String {
        val safe = requested.replace(Regex("[\\x00/\\\\]"), "_").ifBlank { "download" }
        if (safe !in existing) return safe
        val dot = safe.lastIndexOf('.')
        val (stem, ext) = if (dot > 0) safe.substring(0, dot) to safe.substring(dot) else safe to ""
        var candidate: String
        var counter = 2
        do { candidate = "$stem ($counter)$ext"; counter++ } while (candidate in existing)
        return candidate
    }

    fun nextState(current: DownloadState, request: DownloadState): DownloadState = when {
        current == DownloadState.CANCELLED -> DownloadState.CANCELLED
        current == DownloadState.COMPLETED -> DownloadState.COMPLETED
        request == DownloadState.CANCELLED -> DownloadState.CANCELLED
        request == DownloadState.PAUSED && current == DownloadState.RUNNING -> DownloadState.PAUSED
        request == DownloadState.RUNNING && current in setOf(DownloadState.PAUSED, DownloadState.QUEUED, DownloadState.FAILED, DownloadState.WAITING_WIFI) -> DownloadState.RUNNING
        request == DownloadState.WAITING_WIFI && current in setOf(DownloadState.QUEUED, DownloadState.PAUSED) -> DownloadState.WAITING_WIFI
        request == DownloadState.FAILED -> DownloadState.FAILED
        else -> current
    }

    fun isTerminal(state: DownloadState) = state in setOf(DownloadState.COMPLETED, DownloadState.FAILED, DownloadState.CANCELLED)

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    /** Validates a Range header answer: 206 with a parseable "bytes a-b/total" reply. */
    fun parseRangeReply(header: String?, statusCode: Int): Triple<Long, Long, Long>? {
        if (statusCode != 206) return null
        val value = header?.trim()?.lowercase() ?: return null
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").find(value) ?: return null
        val (start, end, total) = match.destructured
        return Triple(start.toLong(), end.toLong(), total.toLong())
    }
}

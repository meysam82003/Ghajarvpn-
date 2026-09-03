package com.ghajarvpn.downloads

import java.net.URI

data class ByteRange(val start: Long, val endInclusive: Long) {
    init { require(start >= 0 && endInclusive >= start) }
    val length: Long get() = endInclusive - start + 1
}

object DownloadPlanning {
    private val contentRange = Regex("(?i)^bytes\\s+\\d+-\\d+/(\\d+)$")

    fun smartConnections(size: Long): Int = when {
        size <= 0 || size < 8L * 1024 * 1024 -> 1
        size < 64L * 1024 * 1024 -> 2
        size < 256L * 1024 * 1024 -> 4
        else -> 6
    }

    fun ranges(size: Long, requestedConnections: Int): List<ByteRange> {
        require(size > 0)
        val count = requestedConnections.coerceIn(1, 8).coerceAtMost(size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val base = size / count
        val remainder = size % count
        var cursor = 0L
        return List(count) { index ->
            val length = base + if (index < remainder) 1 else 0
            ByteRange(cursor, cursor + length - 1).also { cursor += length }
        }
    }

    fun totalFromContentRange(value: String?): Long? = value?.trim()?.let { raw ->
        contentRange.matchEntire(raw)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
    }

    fun safeHttpUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw.trim())
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}

object DownloadNames {
    fun sanitize(input: String, fallback: String = "download.bin"): String {
        val cleaned = input.substringAfterLast('/').substringBefore('?')
            .replace(Regex("[\\u0000-\\u001f\\u007f/\\\\:*?\"<>|]"), "_")
            .trim().trim('.').take(160)
        return cleaned.ifBlank { fallback }
    }

    fun collisionFree(name: String, existing: Set<String>): String {
        if (name !in existing) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val suffix = name.substring(dot)
        var index = 1
        while ("$base ($index)$suffix" in existing) index++
        return "$base ($index)$suffix"
    }
}

object DownloadHeaderPolicy {
    private val allowed = setOf("cookie", "referer", "user-agent", "authorization", "origin", "accept-language")

    fun sanitize(source: Map<String, String>): Map<String, String> = source.mapNotNull { (name, value) ->
        val normalized = name.trim().lowercase()
        if (normalized !in allowed || value.isBlank() || value.length > 16_384 || '\n' in value || '\r' in value) null
        else canonical(normalized) to value
    }.toMap()

    private fun canonical(value: String) = when (value) {
        "user-agent" -> "User-Agent"
        "accept-language" -> "Accept-Language"
        else -> value.replaceFirstChar { it.uppercase() }
    }
}

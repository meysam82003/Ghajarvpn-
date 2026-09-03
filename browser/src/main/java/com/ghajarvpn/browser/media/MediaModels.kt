package com.ghajarvpn.browser.media

import com.ghajarvpn.browser.BrowserNetworkRoute
import java.security.MessageDigest
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class MediaKind { PROGRESSIVE, HLS, DASH, UNKNOWN }

data class SubtitleCandidate(val url: String, val label: String = "", val language: String = "")

data class MediaCandidate(
    val url: String,
    val title: String = "",
    val mimeType: String = "",
    val kind: MediaKind = MediaKind.UNKNOWN,
    val subtitles: List<SubtitleCandidate> = emptyList()
)

data class MediaPlaybackRequest(
    val media: MediaCandidate,
    val headers: Map<String, String>,
    val private: Boolean,
    val route: BrowserNetworkRoute,
    val createdAt: Long = System.currentTimeMillis()
)

data class VideoPlaybackState(
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = 1f
)

enum class PictureInPictureControl { SEEK_BACK, PLAY, PAUSE, SEEK_FORWARD }

object PictureInPictureControlPolicy {
    fun controls(isPlaying: Boolean): List<PictureInPictureControl> = listOf(
        PictureInPictureControl.SEEK_BACK,
        if (isPlaying) PictureInPictureControl.PAUSE else PictureInPictureControl.PLAY,
        PictureInPictureControl.SEEK_FORWARD
    )
}

object PlaybackLifecyclePolicy {
    fun shouldPause(
        backgroundAllowed: Boolean,
        inPictureInPicture: Boolean,
        returningToBrowser: Boolean,
        continueOnNavigate: Boolean
    ): Boolean = !backgroundAllowed && !inPictureInPicture && !(returningToBrowser && continueOnNavigate)
}

/** Sensitive request headers stay in process memory and never enter an Intent or Bundle. */
object MediaSessionVault {
    private const val MAX_AGE_MS = 30 * 60 * 1000L
    private val sessions = ConcurrentHashMap<String, MediaPlaybackRequest>()

    fun put(request: MediaPlaybackRequest): String {
        prune()
        return UUID.randomUUID().toString().also { sessions[it] = request }
    }

    fun take(id: String): MediaPlaybackRequest? {
        val request = sessions.remove(id) ?: return null
        return request.takeIf { System.currentTimeMillis() - it.createdAt <= MAX_AGE_MS }
    }

    fun clear() = sessions.clear()
    private fun prune() = sessions.entries.removeAll { System.currentTimeMillis() - it.value.createdAt > MAX_AGE_MS }
}

object MediaSourceResolver {
    const val MAX_MEDIA_URL_LENGTH = 8192
    private val mediaExtensions = Regex("(?i)\\.(mp4|m4v|webm|mkv|m3u8|mpd)(?:$|[?#])")

    fun resolve(raw: String, mime: String = ""): MediaCandidate? {
        val clean = raw.trim()
        if (clean.isEmpty() || clean.length > MAX_MEDIA_URL_LENGTH || raw.any(Char::isISOControl)) return null
        val uri = runCatching { URI(clean) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        if (clean.contains("drm", true) || clean.contains("widevine", true) || mime.contains("encrypted", true)) return null
        val kind = when {
            uri.path.orEmpty().endsWith(".m3u8", true) || mime.contains("mpegurl", true) -> MediaKind.HLS
            uri.path.orEmpty().endsWith(".mpd", true) || mime.contains("dash", true) -> MediaKind.DASH
            mediaExtensions.containsMatchIn(clean) || mime.startsWith("video/", true) -> MediaKind.PROGRESSIVE
            else -> MediaKind.UNKNOWN
        }
        return MediaCandidate(uri.toASCIIString(), mimeType = mime, kind = kind)
    }

    fun looksLikeMedia(raw: String, mime: String = "") = resolve(raw, mime)?.kind != MediaKind.UNKNOWN

    fun resumeKey(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(32)
}

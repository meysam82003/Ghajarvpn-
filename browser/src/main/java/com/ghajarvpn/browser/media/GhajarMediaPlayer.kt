package com.ghajarvpn.browser.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@UnstableApi
class GhajarMediaPlayer(context: Context, request: MediaPlaybackRequest) {
    private val headers = MediaHeaderProvider.sanitize(request.headers)
    private val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(false)
        .setDefaultRequestProperties(headers)
        .setUserAgent(headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value ?: "GhajarBrowser")

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
        .build()

    fun prepare(candidate: MediaCandidate, startPositionMs: Long = 0L) {
        val subtitles = candidate.subtitles.mapNotNull { subtitle ->
            val uri = runCatching { Uri.parse(subtitle.url) }.getOrNull() ?: return@mapNotNull null
            val mime = when {
                uri.path.orEmpty().endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
                uri.path.orEmpty().endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
                else -> return@mapNotNull null
            }
            MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime)
                .setLabel(subtitle.label.takeIf(String::isNotBlank))
                .setLanguage(subtitle.language.takeIf(String::isNotBlank)).build()
        }
        val item = MediaItem.Builder().setUri(candidate.url)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(candidate.title.ifBlank { "ویدیوی قاجار" }).build())
            .setSubtitleConfigurations(subtitles).build()
        player.setMediaItem(item, startPositionMs)
        player.prepare()
    }

    fun release() = player.release()
}

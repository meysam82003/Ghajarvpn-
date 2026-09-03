package com.ghajarvpn.browser.media

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Owns exactly one Media3 player in the isolated browser process. Player,
 * full-screen activity, mini-player and notification all control this instance.
 */
@OptIn(UnstableApi::class)
object GhajarPlaybackCoordinator {
    private var engine: GhajarMediaPlayer? = null
    private var mediaSession: GhajarMediaSession? = null
    private val listeners = CopyOnWriteArraySet<(Player?) -> Unit>()

    var currentRequest: MediaPlaybackRequest? = null
        private set
    @Volatile var playerSurfaceVisible: Boolean = false
        internal set

    fun start(context: Context, request: MediaPlaybackRequest): Player {
        releasePlayback()
        val next = GhajarMediaPlayer(context.applicationContext, request)
        engine = next
        mediaSession = GhajarMediaSession(context.applicationContext, next.player)
        currentRequest = request
        next.prepare(request.media)
        next.player.playWhenReady = true
        notifyChanged(next.player)
        GhajarPlaybackService.start(context.applicationContext)
        return next.player
    }

    fun player(): Player? = engine?.player

    fun addListener(listener: (Player?) -> Unit) {
        listeners += listener
        listener(player())
    }

    fun removeListener(listener: (Player?) -> Unit) {
        listeners -= listener
    }

    internal fun releasePlayback() {
        mediaSession?.release()
        mediaSession = null
        engine?.release()
        engine = null
        currentRequest = null
        playerSurfaceVisible = false
        notifyChanged(null)
    }

    private fun notifyChanged(player: Player?) = listeners.forEach { listener ->
        runCatching { listener(player) }
    }
}

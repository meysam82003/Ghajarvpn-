package com.ghajarvpn.browser.media

import android.content.Context
import androidx.media3.common.PlaybackException
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
    private var recoveryController: MediaRecoveryController? = null
    private val listeners = CopyOnWriteArraySet<(Player?) -> Unit>()
    private val errorListeners = CopyOnWriteArraySet<(PlaybackException) -> Unit>()
    private var lastTerminalError: PlaybackException? = null
    private var resumeAfterSafeRoute = false

    var currentRequest: MediaPlaybackRequest? = null
        private set
    @Volatile var playerSurfaceVisible: Boolean = false
        internal set

    fun start(context: Context, request: MediaPlaybackRequest): Player {
        releasePlayback()
        val next = GhajarMediaPlayer(context.applicationContext, request)
        engine = next
        mediaSession = GhajarMediaSession(context.applicationContext, next.player)
        recoveryController = MediaRecoveryController(
            context.applicationContext,
            next.player,
            onTerminalError = ::notifyTerminalError,
            onRecovered = { lastTerminalError = null }
        )
        currentRequest = request
        lastTerminalError = null
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

    fun addErrorListener(listener: (PlaybackException) -> Unit) {
        errorListeners += listener
        lastTerminalError?.let(listener)
    }

    fun removeErrorListener(listener: (PlaybackException) -> Unit) {
        errorListeners -= listener
    }

    fun onBrowserRouteStateChanged(ready: Boolean) {
        val active = player() ?: return
        if (!ready) {
            resumeAfterSafeRoute = resumeAfterSafeRoute || active.playWhenReady
            active.pause()
            return
        }
        if (resumeAfterSafeRoute) {
            resumeAfterSafeRoute = false
            active.playWhenReady = true
        }
        recoveryController?.onNetworkPathChanged()
    }

    fun retryPlayback() {
        lastTerminalError = null
        recoveryController?.retryNow()
    }

    internal fun releasePlayback() {
        recoveryController?.release()
        recoveryController = null
        mediaSession?.release()
        mediaSession = null
        engine?.release()
        engine = null
        currentRequest = null
        lastTerminalError = null
        resumeAfterSafeRoute = false
        playerSurfaceVisible = false
        notifyChanged(null)
    }

    private fun notifyChanged(player: Player?) = listeners.forEach { listener ->
        runCatching { listener(player) }
    }

    private fun notifyTerminalError(error: PlaybackException) {
        lastTerminalError = error
        errorListeners.forEach { listener -> runCatching { listener(error) } }
    }
}

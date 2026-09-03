package com.ghajarvpn.browser.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/** Pure bounds shared by the runtime controller and JVM tests. */
object MediaRecoveryPolicy {
    private val delaysMs = longArrayOf(500L, 1_500L, 3_000L)
    const val STALL_TIMEOUT_MS = 10_000L

    fun retryDelay(attemptsAlreadyMade: Int): Long? = delaysMs.getOrNull(attemptsAlreadyMade)

    fun shouldRecoverAfterPathChange(playWhenReady: Boolean, playbackState: Int, hasError: Boolean): Boolean =
        playWhenReady && (hasError || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING)

    fun isRetryable(errorCode: Int): Boolean = errorCode in setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    )
}

/**
 * Owns bounded retry independently of an Activity, so mini-player and
 * background playback recover too. It observes network transitions but never
 * changes the selected VPN route or bypasses the browser's proxy policy.
 */
class MediaRecoveryController(
    context: Context,
    private val player: Player,
    private val onTerminalError: (PlaybackException) -> Unit,
    private val onRecovered: () -> Unit
) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var lastNetwork: Network? = connectivity.activeNetwork
    private var networkWasLost = false
    private var attempts = 0
    private var pending: Runnable? = null
    private var released = false
    private var registered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            if (network == lastNetwork) {
                lastNetwork = null
                networkWasLost = true
            }
        }

        override fun onAvailable(network: Network) {
            val pathChanged = networkWasLost || (lastNetwork != null && network != lastNetwork)
            lastNetwork = network
            networkWasLost = false
            if (pathChanged) handler.post(::onNetworkPathChanged)
        }
    }

    init {
        player.addListener(this)
        registered = runCatching {
            connectivity.registerDefaultNetworkCallback(networkCallback)
            true
        }.getOrDefault(false)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                attempts = 0
                cancelPending()
                onRecovered()
            }
            Player.STATE_BUFFERING -> if (player.playWhenReady && pending == null) {
                scheduleRecovery(MediaRecoveryPolicy.STALL_TIMEOUT_MS, null)
            }
            else -> Unit
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (MediaRecoveryPolicy.isRetryable(error.errorCode)) scheduleRecovery(null, error)
        else onTerminalError(error)
    }

    fun onNetworkPathChanged() {
        if (released || !MediaRecoveryPolicy.shouldRecoverAfterPathChange(
                player.playWhenReady,
                player.playbackState,
                player.playerError != null
            )) return
        attempts = 0
        scheduleRecovery(0L, player.playerError)
    }

    fun retryNow() {
        attempts = 0
        player.playWhenReady = true
        scheduleRecovery(0L, null)
    }

    fun release() {
        released = true
        cancelPending()
        player.removeListener(this)
        if (registered) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        registered = false
    }

    private fun scheduleRecovery(delayOverrideMs: Long?, cause: PlaybackException?) {
        if (released) return
        val boundedDelay = MediaRecoveryPolicy.retryDelay(attempts)
        if (boundedDelay == null) {
            cause?.let(onTerminalError)
            return
        }
        val delay = delayOverrideMs ?: boundedDelay
        cancelPending()
        val task = Runnable {
            pending = null
            if (released || !player.playWhenReady) return@Runnable
            if (player.playbackState == Player.STATE_READY && player.playerError == null) return@Runnable
            val position = player.currentPosition.coerceAtLeast(0L)
            attempts++
            player.prepare()
            if (position > 0L) player.seekTo(position)
            player.play()
        }
        pending = task
        handler.postDelayed(task, delay)
    }

    private fun cancelPending() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }
}

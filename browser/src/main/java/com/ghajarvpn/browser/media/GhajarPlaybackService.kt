package com.ghajarvpn.browser.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteAction
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import androidx.media3.common.Player

/** Foreground owner for user-started playback and real media controls. */
class GhajarPlaybackService : Service() {
    private var observed: Player? = null
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
        override fun onPlaybackStateChanged(playbackState: Int) = publish()
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> GhajarPlaybackCoordinator.player()?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_SEEK_BACK -> seekBy(-MediaPreferences(this).seekSeconds * 1000L)
            ACTION_SEEK_FORWARD -> seekBy(MediaPreferences(this).seekSeconds * 1000L)
            ACTION_STOP -> {
                stopPlayback()
                return START_NOT_STICKY
            }
        }
        val player = GhajarPlaybackCoordinator.player()
        if (player == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observed !== player) {
            observed?.removeListener(listener)
            observed = player
            player.addListener(listener)
        }
        startForeground(NOTIFICATION_ID, notification(player))
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!MediaPreferences(this).backgroundPlayback) stopPlayback()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        observed?.removeListener(listener)
        observed = null
        if (GhajarPlaybackCoordinator.player() != null) GhajarPlaybackCoordinator.releasePlayback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun seekBy(delta: Long) = GhajarPlaybackCoordinator.player()?.let { player ->
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + delta).coerceIn(0L, duration))
    }

    private fun stopPlayback() {
        GhajarPlaybackCoordinator.releasePlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publish() {
        val player = GhajarPlaybackCoordinator.player() ?: return
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(player))
    }

    private fun notification(player: Player): Notification {
        val title = GhajarPlaybackCoordinator.currentRequest?.media?.title.orEmpty().ifBlank { "Ghajar Player" }
        val content = PendingIntent.getActivity(
            this,
            40,
            Intent(this, GhajarPlayerActivity::class.java)
                .putExtra(GhajarPlayerActivity.EXTRA_ATTACH, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingFlags()
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title.take(100))
            .setContentText(if (player.isPlaying) "در حال پخش با قاجار" else "پخش متوقف است")
            .setContentIntent(content)
            .setOnlyAlertOnce(true)
            .setOngoing(player.isPlaying)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(android.R.drawable.ic_media_rew, "عقب", serviceAction(ACTION_SEEK_BACK, 41))
            .addAction(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (player.isPlaying) "مکث" else "پخش",
                serviceAction(ACTION_PLAY_PAUSE, 42)
            )
            .addAction(android.R.drawable.ic_media_ff, "جلو", serviceAction(ACTION_SEEK_FORWARD, 43))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "توقف", serviceAction(ACTION_STOP, 44))
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 3))
            .build()
    }

    private fun serviceAction(action: String, requestCode: Int) = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, GhajarPlaybackService::class.java).setAction(action),
        pendingFlags()
    )

    private fun pendingFlags() = PendingIntent.FLAG_UPDATE_CURRENT or
        if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "پخش رسانه قاجار", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "کنترل پخش رسانه‌ای که کاربر در مرورگر قاجار شروع کرده است"
                    setSound(null, null)
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "ghajar_media_playback"
        private const val NOTIFICATION_ID = 7341
        internal const val ACTION_PLAY_PAUSE = "com.ghajarvpn.browser.media.PLAY_PAUSE"
        internal const val ACTION_SEEK_BACK = "com.ghajarvpn.browser.media.SEEK_BACK"
        internal const val ACTION_SEEK_FORWARD = "com.ghajarvpn.browser.media.SEEK_FORWARD"
        private const val ACTION_STOP = "com.ghajarvpn.browser.media.STOP"

        fun start(context: Context) {
            val intent = Intent(context, GhajarPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun requestStop(context: Context) {
            context.startService(Intent(context, GhajarPlaybackService::class.java).setAction(ACTION_STOP))
        }

        fun pictureInPictureActions(context: Context, player: Player): List<RemoteAction> =
            PictureInPictureControlPolicy.controls(player.isPlaying).mapIndexed { index, control ->
                val (icon, label, action) = when (control) {
                    PictureInPictureControl.SEEK_BACK -> Triple(android.R.drawable.ic_media_rew, "عقب", ACTION_SEEK_BACK)
                    PictureInPictureControl.PLAY -> Triple(android.R.drawable.ic_media_play, "پخش", ACTION_PLAY_PAUSE)
                    PictureInPictureControl.PAUSE -> Triple(android.R.drawable.ic_media_pause, "مکث", ACTION_PLAY_PAUSE)
                    PictureInPictureControl.SEEK_FORWARD -> Triple(android.R.drawable.ic_media_ff, "جلو", ACTION_SEEK_FORWARD)
                }
                val pending = PendingIntent.getService(
                    context,
                    80 + index,
                    Intent(context, GhajarPlaybackService::class.java).setAction(action),
                    PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
                )
                RemoteAction(Icon.createWithResource(context, icon), label, label, pending)
            }
    }
}

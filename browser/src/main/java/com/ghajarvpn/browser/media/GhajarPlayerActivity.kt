package com.ghajarvpn.browser.media

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs

class GhajarPlayerActivity : Activity() {
    private lateinit var request: MediaPlaybackRequest
    private lateinit var engine: GhajarMediaPlayer
    private lateinit var mediaSession: GhajarMediaSession
    private lateinit var playerView: PlayerView
    private lateinit var root: FrameLayout
    private lateinit var stats: TextView
    private lateinit var buffering: ProgressBar
    private val preferences by lazy { MediaPreferences(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var aspectIndex = 0
    private var screenLocked = false
    private var mutedVolume = 1f
    private var retryCount = 0
    private var resumePrompted = false
    private var initialY = 0f
    private var initialX = 0f
    private var initialPosition = 0L
    private var initialBrightness = .5f
    private var initialVolume = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val token = intent.getStringExtra(EXTRA_SESSION).orEmpty()
        val resolved = MediaSessionVault.take(token)
        if (resolved == null) {
            Toast.makeText(this, "نشست امن ویدیو منقضی شده است؛ از صفحه دوباره پلیر را باز کنید.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        request = resolved
        engine = GhajarMediaPlayer(this, request)
        mediaSession = GhajarMediaSession(this, engine.player)
        engine.player.playbackParameters = androidx.media3.common.PlaybackParameters(preferences.speed)
        setContentView(buildUi())
        engine.player.addListener(PlayerListener())
        engine.prepare(request.media)
        engine.player.playWhenReady = true
        handler.post(statsUpdater)
    }

    private fun buildUi(): View {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            player = engine.player
            useController = true
            controllerShowTimeoutMs = 3500
            setShowSubtitleButton(true)
            setShowNextButton(false); setShowPreviousButton(false)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        buffering = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(buffering, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER))

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.argb(145, 0, 0, 0))
            addView(icon(android.R.drawable.ic_media_previous, "بازگشت به سایت") { finish() })
            addView(TextView(this@GhajarPlayerActivity).apply {
                text = request.media.title.ifBlank { "شاه قاجار · پخش امن" }
                setTextColor(Color.WHITE); textSize = 16f; maxLines = 1; setPadding(dp(8), 0, dp(8), 0)
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(icon(android.R.drawable.ic_menu_manage, "تنظیمات پخش") { showPlayerSettings(it) })
            addView(icon(android.R.drawable.ic_menu_more, "کیفیت، صدا و زیرنویس") { showTrackMenu(it) })
            if (Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                addView(icon(android.R.drawable.ic_menu_slideshow, "تصویر در تصویر") { enterPip() })
            }
        }
        root.addView(top, FrameLayout.LayoutParams(-1, dp(60), Gravity.TOP))

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            addView(icon(android.R.drawable.ic_media_rew, "${preferences.seekSeconds} ثانیه عقب") { seekBy(-preferences.seekSeconds * 1000L) })
            addView(icon(android.R.drawable.ic_media_ff, "${preferences.seekSeconds} ثانیه جلو") { seekBy(preferences.seekSeconds * 1000L) })
            addView(icon(android.R.drawable.ic_lock_silent_mode_off, "بی‌صدا") { toggleMute() })
            addView(icon(android.R.drawable.ic_menu_crop, "نسبت تصویر") { cycleAspect() })
            addView(icon(android.R.drawable.ic_lock_lock, "قفل صفحه") { toggleLock() })
            addView(icon(android.R.drawable.ic_menu_always_landscape_portrait, "چرخش صفحه") { toggleOrientation() })
        }
        root.addView(side, FrameLayout.LayoutParams(dp(54), -2, Gravity.END or Gravity.CENTER_VERTICAL))

        stats = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 12f; setPadding(dp(8), dp(5), dp(8), dp(5))
            setBackgroundColor(Color.argb(135, 0, 0, 0)); visibility = View.GONE
        }
        root.addView(stats, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply { setMargins(dp(8), 0, 0, dp(8)) })
        installGestures()
        return root
    }

    private fun icon(drawable: Int, description: String, action: (View) -> Unit) = ImageButton(this).apply {
        setImageResource(drawable); setColorFilter(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
        contentDescription = description; setOnClickListener { if (!screenLocked || description == "قفل صفحه") action(it) }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
    }

    private fun showPlayerSettings(anchor: View) {
        PopupMenu(this, anchor).apply {
            val speed = menu.addSubMenu("سرعت پخش · ${preferences.speed}x")
            SPEEDS.forEachIndexed { index, value -> speed.add(1, 100 + index, index, "${value}x") }
            val seek = menu.addSubMenu("پرش دوضربه‌ای · ${preferences.seekSeconds}s")
            SEEK_SECONDS.forEachIndexed { index, value -> seek.add(2, 200 + index, index, "$value ثانیه") }
            menu.add(3, 300, 0, if (preferences.gestures) "خاموش‌کردن Gestureها" else "روشن‌کردن Gestureها")
            menu.add(3, 301, 1, if (preferences.backgroundPlayback) "توقف پخش پس‌زمینه" else "اجازهٔ پخش پس‌زمینه")
            menu.add(3, 302, 2, if (stats.visibility == View.VISIBLE) "بستن آمار فنی" else "نمایش آمار فنی")
            setOnMenuItemClickListener { item ->
                when {
                    item.itemId in 100 until 100 + SPEEDS.size -> {
                        preferences.speed = SPEEDS[item.itemId - 100]
                        engine.player.playbackParameters = androidx.media3.common.PlaybackParameters(preferences.speed)
                    }
                    item.itemId in 200 until 200 + SEEK_SECONDS.size -> preferences.seekSeconds = SEEK_SECONDS[item.itemId - 200]
                    item.itemId == 300 -> preferences.gestures = !preferences.gestures
                    item.itemId == 301 -> preferences.backgroundPlayback = !preferences.backgroundPlayback
                    item.itemId == 302 -> stats.visibility = if (stats.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }; true
            }; show()
        }
    }

    private fun showTrackMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val actions = mutableMapOf<Int, () -> Unit>()
        var id = 500
        fun addAuto(type: Int, label: String) {
            val current = id++
            popup.menu.add(type, current, 0, label)
            actions[current] = {
                engine.player.trackSelectionParameters = engine.player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(type).setTrackTypeDisabled(type, false).build()
            }
        }
        addAuto(C.TRACK_TYPE_VIDEO, "کیفیت: خودکار / اصلی")
        addAuto(C.TRACK_TYPE_AUDIO, "صدا: خودکار")
        val subtitleOff = id++
        popup.menu.add(C.TRACK_TYPE_TEXT, subtitleOff, 0, "زیرنویس: خاموش")
        actions[subtitleOff] = { engine.player.trackSelectionParameters = engine.player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build() }
        engine.player.currentTracks.groups.forEach { group ->
            val type = group.type
            if (type !in setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT) || !group.isSupported) return@forEach
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val label = when (type) {
                    C.TRACK_TYPE_VIDEO -> "کیفیت: ${format.height.takeIf { it > 0 }?.let { "${it}p" } ?: format.label ?: "مسیر ویدیو"}"
                    C.TRACK_TYPE_AUDIO -> "صدا: ${format.label ?: format.language ?: "مسیر ${index + 1}"}"
                    else -> "زیرنویس: ${format.label ?: format.language ?: "مسیر ${index + 1}"}"
                }
                val current = id++
                popup.menu.add(type, current, index + 1, label)
                actions[current] = {
                    engine.player.trackSelectionParameters = engine.player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index))).build()
                }
            }
        }
        popup.setOnMenuItemClickListener { actions[it.itemId]?.invoke(); true }
        popup.show()
    }

    @Suppress("ClickableViewAccessibility")
    private fun installGestures() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                initialX = e.x; initialY = e.y; initialPosition = engine.player.currentPosition
                initialBrightness = window.attributes.screenBrightness.takeIf { it >= 0 } ?: .5f
                initialVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!preferences.gestures || screenLocked) return false
                seekBy(if (e.x > root.width / 2f) preferences.seekSeconds * 1000L else -preferences.seekSeconds * 1000L)
                return true
            }

            override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (!preferences.gestures || screenLocked || first == null) return false
                val dx = current.x - initialX; val dy = current.y - initialY
                if (abs(dx) > abs(dy) * 1.25f) {
                    val delta = (dx / root.width * (engine.player.duration.takeIf { it > 0 } ?: 60_000L)).toLong()
                    engine.player.seekTo((initialPosition + delta).coerceIn(0L, engine.player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
                } else if (initialX < root.width / 2f) {
                    val value = (initialBrightness - dy / root.height).coerceIn(.05f, 1f)
                    window.attributes = window.attributes.apply { screenBrightness = value }
                } else {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (initialVolume - dy / root.height * max).toInt().coerceIn(0, max), 0)
                }
                return true
            }
        })
        playerView.setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
    }

    private fun seekBy(delta: Long) {
        val limit = engine.player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        engine.player.seekTo((engine.player.currentPosition + delta).coerceIn(0L, limit))
    }

    private fun toggleMute() {
        if (engine.player.volume > 0f) { mutedVolume = engine.player.volume; engine.player.volume = 0f }
        else engine.player.volume = mutedVolume.coerceAtLeast(.1f)
    }

    private fun cycleAspect() {
        val modes = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)
        aspectIndex = (aspectIndex + 1) % modes.size; playerView.resizeMode = modes[aspectIndex]
    }

    private fun toggleLock() { screenLocked = !screenLocked; playerView.useController = !screenLocked; toast(if (screenLocked) "صفحه قفل شد؛ برای بازکردن از کلید بازگشت استفاده کنید" else "قفل باز شد") }

    private fun toggleOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26 || !engine.player.isPlaying) { toast("ابتدا ویدیو را پخش کنید"); return }
        val ratio = engine.player.videoSize.let { if (it.width > 0 && it.height > 0) Rational(it.width, it.height) else Rational(16, 9) }
        enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= 26 && preferences.backgroundPlayback && engine.player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        playerView.useController = !inPip && !screenLocked
    }

    override fun onPause() {
        saveResume()
        if (!preferences.backgroundPlayback && !(Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode)) engine.player.pause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::engine.isInitialized) { saveResume(); mediaSession.release(); engine.release() }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (screenLocked) { toggleLock(); return }
        super.onBackPressed()
    }

    private fun saveResume() {
        if (::request.isInitialized && !request.private && ::engine.isInitialized) preferences.saveResume(request.media.url, engine.player.currentPosition)
    }

    private fun offerResume() {
        if (resumePrompted || request.private) return
        resumePrompted = true
        val position = preferences.resumePosition(request.media.url)
        if (position < 30_000 || position >= engine.player.duration - 10_000) return
        AlertDialog.Builder(this).setTitle("ادامهٔ پخش؟")
            .setMessage("از ${formatTime(position)} ادامه داده شود؟")
            .setPositiveButton("ادامه") { _, _ -> engine.player.seekTo(position) }
            .setNegativeButton("از ابتدا", null).show()
    }

    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            buffering.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            if (state == Player.STATE_READY) { retryCount = 0; offerResume() }
            if (state == Player.STATE_ENDED && !request.private) preferences.saveResume(request.media.url, 0)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (retryCount++ < 2 && error.errorCode in RETRYABLE_ERRORS) {
                val position = engine.player.currentPosition; engine.player.prepare(); engine.player.seekTo(position); engine.player.play(); return
            }
            val message = when (error.errorCode) {
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "کُدک این ویدیو روی دستگاه پشتیبانی نمی‌شود."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "دسترسی Media رد شد یا نشست آن منقضی شده است."
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "ساختار Media معتبر نیست."
                PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
                PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> "این Media با DRM محافظت شده و در پلیر قاجار باز نمی‌شود."
                else -> "پخش ویدیو ممکن نشد؛ Player اصلی سایت همچنان قابل استفاده است."
            }
            AlertDialog.Builder(this@GhajarPlayerActivity).setTitle("شهربان · خطای پخش").setMessage(message)
                .setPositiveButton("تلاش دوباره") { _, _ -> retryCount = 0; engine.player.prepare(); engine.player.play() }
                .setNegativeButton("بازگشت به سایت") { _, _ -> finish() }.show()
        }
    }

    private val statsUpdater = object : Runnable {
        override fun run() {
            if (::engine.isInitialized && ::stats.isInitialized && stats.visibility == View.VISIBLE) {
                val format = engine.player.videoFormat
                stats.text = "${format?.width ?: 0}×${format?.height ?: 0}  •  ${(format?.bitrate ?: 0) / 1000} kbps  •  buffer ${engine.player.totalBufferedDuration / 1000}s"
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SESSION = "media_session"
        private val SPEEDS = floatArrayOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        private val SEEK_SECONDS = intArrayOf(5, 10, 15, 30)
        private val RETRYABLE_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        )
    }
}

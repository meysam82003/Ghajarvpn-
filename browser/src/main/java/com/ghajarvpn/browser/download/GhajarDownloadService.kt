package com.ghajarvpn.browser.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.ghajarvpn.browser.BrowserContract
import com.ghajarvpn.browser.BrowserUi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service running the download queue. Survives backgrounding,
 * exposes pause/resume/retry/cancel actions in the notification and persists
 * state after every transition so process death and reboots can recover.
 */
class GhajarDownloadService : Service(), DownloadEngine.Listener {

    private lateinit var storage: DownloadStorage
    private lateinit var engine: DownloadEngine
    private val jobs = ConcurrentHashMap<String, DownloadEngine.Job>()
    private val lastBytes = ConcurrentHashMap<String, Long>()
    private val lastTime = ConcurrentHashMap<String, Long>()
    @Volatile private var activeCount = 0

    override fun onCreate() {
        super.onCreate()
        storage = DownloadStorage(this)
        engine = DownloadEngine(getExternalFilesDir(null) ?: filesDir)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_ID)?.let { jobs[it]?.pause() }
            ACTION_RESUME -> intent.getStringExtra(EXTRA_ID)?.let { id -> resume(id) }
            ACTION_RETRY -> intent.getStringExtra(EXTRA_ID)?.let { id -> retry(id) }
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_ID)?.let { id -> cancel(id) }
            ACTION_ENQUEUE -> enqueueFromIntent(intent)
            else -> recoverPending()
        }
        return START_REDELIVER_INTENT
    }

    private fun recoverPending() {
        val items = storage.pending()
        val queue = items.filter { it.state in setOf(DownloadState.QUEUED, DownloadState.RUNNING) }
        items.forEach { item ->
            if (item.state == DownloadState.RUNNING) {
                persist(item.copy(state = DownloadState.QUEUED, error = ""))
            }
        }
        if (queue.isNotEmpty()) startForegroundCompat()
        queue.forEach { item -> startJob(persist(item.copy(state = DownloadState.QUEUED, segments = emptyList(), error = ""))) }
        stopIfIdle()
    }

    private fun enqueueFromIntent(intent: Intent) {
        val item = itemFromIntent(intent) ?: return
        val existing = storage.load()
        val name = DownloadPlanner.resolveDuplicateName(existing.map { it.fileName }.toSet(), item.fileName)
        val queued = item.copy(id = DownloadStorage.newId(), fileName = name, state = DownloadState.QUEUED)
        persist(queued)
        startForegroundCompat()
        startJob(queued)
    }

    private fun startJob(item: DownloadItem) {
        if (activeCount >= MAX_CONCURRENT) return
        val job = engine.Job(item, this)
        jobs[item.id] = job
        activeCount++
        job.start()
    }

    private fun resume(id: String) {
        val item = storage.load().firstOrNull { it.id == id } ?: return
        if (item.state != DownloadState.PAUSED) return
        jobs[id]?.pausedFlag()?.set(false)
        startJob(persist(item.copy(state = DownloadState.QUEUED)))
    }

    private fun retry(id: String) {
        val item = storage.load().firstOrNull { it.id == id } ?: return
        if (item.state != DownloadState.FAILED) return
        cleanupPart(item)
        startJob(persist(item.copy(state = DownloadState.QUEUED, downloadedBytes = 0, segments = emptyList(), error = "")))
    }

    private fun cancel(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        val item = storage.load().firstOrNull { it.id == id } ?: return
        cleanupPart(item)
        persist(item.copy(state = DownloadState.CANCELLED, error = ""))
        notifyChanged(item.copy(state = DownloadState.CANCELLED))
        stopIfIdle()
    }

    private fun cleanupPart(item: DownloadItem) {
        val directory = File(item.directory.ifBlank {
            getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath
        })
        File(directory, item.fileName + ".ghajar-part").delete()
    }

    override fun onUpdate(item: DownloadItem, speedBps: Long) {
        persist(item)
        notifyChanged(item, speedBps)
    }

    override fun onTerminal(item: DownloadItem) {
        jobs.remove(item.id)
        activeCount = (activeCount - 1).coerceAtLeast(0)
        lastBytes.remove(item.id); lastTime.remove(item.id)
        storage.pending().filter { it.state == DownloadState.QUEUED }.firstOrNull()?.let { next ->
            startJob(next)
        } ?: stopIfIdle()
    }

    private fun persist(item: DownloadItem): DownloadItem {
        val items = storage.load()
        val index = items.indexOfFirst { it.id == item.id }
        val updated = item.copy(updatedAt = System.currentTimeMillis())
        if (index >= 0) items[index] = updated else items += updated
        storage.save(items)
        return updated
    }

    private fun notifyChanged(item: DownloadItem, speedBps: Long = 0) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = when (item.state) {
            DownloadState.RUNNING -> "${DownloadPlanner.progressPercent(item)}٪ · ${BrowserUi.formatBytes(item.downloadedBytes)} / ${DownloadUi.formatTotal(item)} · ${BrowserUi.formatBytes(speedBps)}/s"
            DownloadState.COMPLETED -> "کامل شد · ${BrowserUi.formatBytes(item.totalBytes)}"
            DownloadState.FAILED -> "ناموفق: ${item.error.ifBlank { "خطای شبکه" }}"
            DownloadState.PAUSED -> "متوقف شده"
            DownloadState.CANCELLED -> "لغو شد"
            DownloadState.WAITING_WIFI -> "در انتظار Wi-Fi"
            DownloadState.QUEUED -> "در صف"
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item.fileName)
            .setContentText(text)
            .setOngoing(item.state in setOf(DownloadState.RUNNING, DownloadState.QUEUED))
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this@GhajarDownloadService, item.id.hashCode(),
                    Intent(this@GhajarDownloadService, GhajarDownloadsActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        if (item.state == DownloadState.RUNNING) {
            builder.setProgress(100, DownloadPlanner.progressPercent(item), false)
            builder.addAction(0, "توقف", actionIntent(ACTION_PAUSE, item.id))
            builder.addAction(0, "لغو", actionIntent(ACTION_CANCEL, item.id))
        } else if (item.state == DownloadState.PAUSED) {
            builder.addAction(0, "ادامه", actionIntent(ACTION_RESUME, item.id))
            builder.addAction(0, "لغو", actionIntent(ACTION_CANCEL, item.id))
        } else if (item.state == DownloadState.FAILED) {
            builder.addAction(0, "تلاش دوباره", actionIntent(ACTION_RETRY, item.id))
        }
        val notification = builder.build()
        manager.notify(NOTIF_BASE + (item.id.hashCode() and 0xFFFF), notification)
        if (DownloadPlanner.isTerminal(item.state)) manager.cancel(NOTIF_BASE + (item.id.hashCode() and 0xFFFF))
    }

    private fun actionIntent(action: String, id: String): PendingIntent = PendingIntent.getService(
        this, (action + id).hashCode(),
        Intent(this, GhajarDownloadService::class.java).setAction(action).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun startForegroundCompat() {
        if (activeCount == 0) {
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("مدیر دانلود قاجار")
                .setContentText("دانلودها فعال‌اند")
                .build()
            if (Build.VERSION.SDK_INT >= 26) startForeground(FOREGROUND_ID, notification)
            else startForeground(FOREGROUND_ID, notification)
        }
    }

    private fun stopIfIdle() {
        if (activeCount == 0 && jobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun itemFromIntent(intent: Intent): DownloadItem? {
        val url = intent.getStringExtra(BrowserContract.EXTRA_URL) ?: return null
        if (!url.startsWith("http")) return null
        val disposition = intent.getStringExtra(BrowserContract.EXTRA_CONTENT_DISPOSITION).orEmpty()
        val mime = intent.getStringExtra(BrowserContract.EXTRA_CONTENT_TYPE).orEmpty()
        val fileName = android.webkit.URLUtil.guessFileName(url, disposition, mime)
        return DownloadItem(
            id = "",
            url = url,
            fileName = fileName,
            mimeType = mime,
            cookies = intent.getStringExtra(BrowserContract.EXTRA_COOKIES).orEmpty(),
            referer = intent.getStringExtra(BrowserContract.EXTRA_REFERER).orEmpty(),
            userAgent = intent.getStringExtra(BrowserContract.EXTRA_USER_AGENT).orEmpty()
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "دانلودهای قاجار", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_ENQUEUE = BrowserContract.ACTION_ENQUEUE_DOWNLOAD
        const val ACTION_PAUSE = "com.ghajarvpn.downloads.PAUSE"
        const val ACTION_RESUME = "com.ghajarvpn.downloads.RESUME"
        const val ACTION_RETRY = "com.ghajarvpn.downloads.RETRY"
        const val ACTION_CANCEL = "com.ghajarvpn.downloads.CANCEL"
        const val EXTRA_ID = "download_id"
        private const val CHANNEL_ID = "ghajar_downloads"
        private const val FOREGROUND_ID = 42001
        private const val NOTIF_BASE = 42100
        private const val MAX_CONCURRENT = 3
    }
}

package com.ghajarvpn.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class GhajarDownloadService : Service() {
    private val coordinator = Executors.newSingleThreadExecutor()
    private val workers = Executors.newFixedThreadPool(8)
    private val controls = ConcurrentHashMap<String, AtomicBoolean>()
    private val active = ConcurrentHashMap.newKeySet<String>()
    private lateinit var repository: DownloadRepository
    private lateinit var notifications: NotificationManager
    private lateinit var connectivity: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            repository.all().filter { it.state == DownloadState.PAUSED && it.error in setOf(WAIT_NETWORK, WAIT_WIFI) }.forEach { task ->
                if (task.error == WAIT_NETWORK || onUnmeteredWifi()) repository.update(task.id) { it.copy(state = DownloadState.QUEUED, error = "") }
            }
            kick()
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository.get(this)
        notifications = getSystemService(NotificationManager::class.java)
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        createChannel()
        startForeground(SUMMARY_ID, summaryNotification("صف دانلود آماده است"))
        // A process death while transferring is restart-safe: completed part files are retained.
        repository.all().filter { it.state in setOf(DownloadState.PROBING, DownloadState.DOWNLOADING) }
            .forEach { task -> repository.update(task.id) { it.copy(state = DownloadState.QUEUED, error = "") } }
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(DownloadContract.EXTRA_ID).orEmpty()
        when (intent?.action) {
            DownloadContract.ACTION_PAUSE -> pause(id)
            DownloadContract.ACTION_CANCEL -> cancel(id)
            DownloadContract.ACTION_RESUME -> repository.update(id) { it.copy(state = DownloadState.QUEUED, error = "") }
        }
        kick()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        controls.values.forEach { it.set(true) }
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        coordinator.shutdownNow(); workers.shutdownNow()
        super.onDestroy()
    }

    private fun pause(id: String) {
        controls[id]?.set(true)
        repository.update(id) { if (it.state == DownloadState.COMPLETED) it else it.copy(state = DownloadState.PAUSED, speedBytesPerSecond = 0) }
        notifyTask(repository.get(id))
    }

    private fun cancel(id: String) {
        controls[id]?.set(true)
        val task = repository.get(id)
        repository.update(id) { if (it.state == DownloadState.COMPLETED) it else it.copy(state = DownloadState.CANCELED, speedBytesPerSecond = 0) }
        task?.takeIf { it.state != DownloadState.COMPLETED }?.let(repository::clearHeaders)
        tempDirectory(id).deleteRecursively()
        notifyTask(repository.get(id))
    }

    private fun kick() = coordinator.execute {
        while (active.size < MAX_CONCURRENT) {
            val next = repository.all().filter { it.state == DownloadState.QUEUED }
                .sortedWith(compareByDescending<DownloadTask> { it.priority }.thenBy { it.createdAt }).firstOrNull() ?: break
            if (!active.add(next.id)) continue
            coordinator.executeTask(next)
        }
        updateSummary()
    }

    private fun java.util.concurrent.ExecutorService.executeTask(task: DownloadTask) {
        workers.execute {
            try { transfer(task.id) }
            finally { active.remove(task.id); controls.remove(task.id); kick() }
        }
    }

    private fun transfer(id: String) {
        var task = repository.get(id) ?: return
        if (task.wifiOnly && !onUnmeteredWifi()) {
            repository.update(id) { it.copy(state = DownloadState.PAUSED, error = WAIT_WIFI) }; notifyTask(repository.get(id)); return
        }
        val stop = AtomicBoolean(false); controls[id] = stop
        try {
            repository.update(id) { it.copy(state = DownloadState.PROBING, error = "") }; notifyTask(repository.get(id))
            val probe = probe(task)
            val requested = if (task.requestedConnections == 0) DownloadPlanning.smartConnections(probe.total) else task.requestedConnections
            val connections = if (probe.rangeSupported && probe.total > 0) requested.coerceIn(1, 8) else 1
            task = repository.update(id) { it.copy(totalBytes = probe.total, activeConnections = connections, state = DownloadState.DOWNLOADING) } ?: return
            notifyTask(task)
            val dir = tempDirectory(id).apply { mkdirs() }
            if (connections > 1) {
                try { downloadSegments(task, DownloadPlanning.ranges(probe.total, connections), dir, stop) }
                catch (failure: Throwable) {
                    if (stop.get()) return
                    // A few servers advertise Range on the probe but reject parallel ranges.
                    // Wait for every segment first, then safely restart once in single mode.
                    dir.deleteRecursively(); dir.mkdirs()
                    task = repository.update(id) { it.copy(activeConnections = 1, downloadedBytes = 0, speedBytesPerSecond = 0) } ?: return
                    downloadSingle(task, dir, stop)
                }
            } else downloadSingle(task, dir, stop)
            if (stop.get()) return
            task = repository.get(id) ?: return
            if (task.state != DownloadState.DOWNLOADING) return
            val assembled = assemble(task, dir)
            if (task.expectedSha256.isNotBlank() && sha256(assembled) != task.expectedSha256) {
                throw DownloadFailure("هش SHA-256 فایل با مقدار مورد انتظار یکسان نیست")
            }
            val output = publish(task, assembled)
            repository.update(id) { it.copy(state = DownloadState.COMPLETED, downloadedBytes = assembled.length(), totalBytes = assembled.length(), speedBytesPerSecond = 0, outputUri = output.toString(), error = "") }
            repository.clearHeaders(task)
            dir.deleteRecursively(); notifyTask(repository.get(id))
        } catch (failure: Throwable) {
            if (stop.get()) return
            val current = repository.get(id) ?: return
            val cause = generateSequence(failure) { it.cause }.last()
            val message = when (cause) {
                is AccessDenied -> "دسترسی دانلود رد شد یا نشست منقضی شده است"
                is DownloadFailure -> cause.message.orEmpty()
                else -> "ارتباط دانلود قطع شد"
            }
            if (!hasValidatedNetwork()) {
                repository.update(id) { it.copy(state = DownloadState.PAUSED, speedBytesPerSecond = 0, error = WAIT_NETWORK) }
            } else if (current.retries < MAX_RETRIES) {
                repository.update(id) { it.copy(state = DownloadState.QUEUED, retries = it.retries + 1, speedBytesPerSecond = 0, error = "تلاش دوباره ${it.retries + 1} از $MAX_RETRIES") }
            } else repository.update(id) { it.copy(state = DownloadState.FAILED, speedBytesPerSecond = 0, error = message) }
            notifyTask(repository.get(id))
        }
    }

    private data class Probe(val total: Long, val rangeSupported: Boolean)

    private fun probe(task: DownloadTask): Probe {
        val connection = connection(task, "bytes=0-0")
        return try {
            val code = connection.responseCode
            if (code == 401 || code == 403) throw AccessDenied()
            if (code !in 200..299) throw DownloadFailure("سرور با خطای HTTP $code پاسخ داد")
            val rangeTotal = DownloadPlanning.totalFromContentRange(connection.getHeaderField("Content-Range"))
            val length = rangeTotal ?: connection.contentLengthLong
            Probe(length, code == HttpURLConnection.HTTP_PARTIAL && rangeTotal != null)
        } finally { connection.disconnect() }
    }

    private fun downloadSegments(task: DownloadTask, ranges: List<ByteRange>, dir: File, stop: AtomicBoolean) {
        val futures = ranges.mapIndexed { index, range -> workers.submit { downloadRange(task, range, File(dir, "part-$index"), stop) } }
        var failure: Throwable? = null
        futures.forEach { future -> runCatching { future.get() }.onFailure { if (failure == null) failure = it } }
        failure?.let { throw it }
    }

    private fun downloadRange(task: DownloadTask, range: ByteRange, part: File, stop: AtomicBoolean) {
        val existing = part.length().coerceAtMost(range.length)
        if (existing == range.length) return
        val requested = ByteRange(range.start + existing, range.endInclusive)
        val connection = connection(task, "bytes=${requested.start}-${requested.endInclusive}")
        try {
            if (connection.responseCode == 401 || connection.responseCode == 403) throw AccessDenied()
            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) throw DownloadFailure("سرور ادامهٔ چندبخشی را نپذیرفت")
            FileOutputStream(part, true).use { output -> copy(task.id, connection, output, stop) }
        } finally { connection.disconnect() }
    }

    private fun downloadSingle(task: DownloadTask, dir: File, stop: AtomicBoolean) {
        val part = File(dir, "part-0")
        var existing = part.length()
        var connection = connection(task, existing.takeIf { it > 0 }?.let { "bytes=$it-" })
        if (existing > 0 && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect(); part.delete(); existing = 0
            connection = connection(task, null)
        }
        try {
            if (connection.responseCode == 401 || connection.responseCode == 403) throw AccessDenied()
            if (connection.responseCode !in 200..299) throw DownloadFailure("سرور با خطای HTTP ${connection.responseCode} پاسخ داد")
            FileOutputStream(part, existing > 0).use { output -> copy(task.id, connection, output, stop) }
        } finally { connection.disconnect() }
    }

    private fun copy(id: String, connection: HttpURLConnection, output: FileOutputStream, stop: AtomicBoolean) {
        val buffer = ByteArray(64 * 1024)
        var lastUpdate = System.currentTimeMillis(); var intervalBytes = 0L
        connection.inputStream.use { input ->
            while (!stop.get()) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count); intervalBytes += count
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 700) {
                    val speed = intervalBytes * 1000 / max(1, now - lastUpdate)
                    val downloaded = tempDirectory(id).listFiles()?.sumOf(File::length) ?: 0
                    repository.update(id) { it.copy(downloadedBytes = downloaded, speedBytesPerSecond = speed) }
                    notifyTask(repository.get(id)); lastUpdate = now; intervalBytes = 0
                }
            }
        }
        output.fd.sync()
    }

    private fun connection(task: DownloadTask, range: String?): HttpURLConnection {
        val original = URI(task.url)
        var current = task.url
        repeat(6) { redirectCount ->
            val target = URI(current)
            val sameOrigin = original.scheme.equals(target.scheme, true) && original.host.equals(target.host, true) && effectivePort(original) == effectivePort(target)
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000; connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept-Encoding", "identity")
            repository.headers(task).forEach { (name, value) ->
                val sensitive = name.equals("Cookie", true) || name.equals("Authorization", true) || name.equals("Origin", true)
                if (sameOrigin || !sensitive) connection.setRequestProperty(name, value)
            }
            range?.let { connection.setRequestProperty("Range", it) }
            val code = connection.responseCode
            if (code !in setOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location") ?: throw DownloadFailure("تغییر مسیر دانلود معتبر نیست")
            if (redirectCount == 5) throw DownloadFailure("تعداد تغییر مسیرهای دانلود بیش از حد است")
            val resolved = URL(URL(current), location).toString()
            connection.disconnect()
            if (!DownloadPlanning.safeHttpUrl(resolved)) throw DownloadFailure("تغییر مسیر دانلود امن نیست")
            current = resolved
        }
        throw DownloadFailure("تغییر مسیر دانلود کامل نشد")
    }

    private fun effectivePort(uri: URI): Int = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80

    private fun assemble(task: DownloadTask, dir: File): File {
        val parts = dir.listFiles { file -> file.name.startsWith("part-") }.orEmpty().sortedBy { it.name.substringAfter('-').toIntOrNull() ?: 0 }
        if (parts.isEmpty()) throw DownloadFailure("داده‌ای برای ذخیره دریافت نشد")
        val target = File(dir, "assembled")
        FileOutputStream(target).use { output -> parts.forEach { part -> FileInputStream(part).use { it.copyTo(output, 128 * 1024) } } }
        if (task.totalBytes > 0 && target.length() != task.totalBytes) throw DownloadFailure("اندازهٔ فایل دریافت‌شده کامل نیست")
        return target
    }

    private fun publish(task: DownloadTask, source: File): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, task.fileName); put(MediaStore.Downloads.MIME_TYPE, task.mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GhajarVPN"); put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw DownloadFailure("فضای ذخیره‌سازی در دسترس نیست")
            try {
                contentResolver.openOutputStream(uri, "w")?.use { output -> source.inputStream().use { it.copyTo(output, 128 * 1024) } }
                    ?: throw DownloadFailure("نوشتن فایل ممکن نشد")
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); contentResolver.update(uri, values, null, null)
                return uri
            } catch (error: Throwable) { contentResolver.delete(uri, null, null); throw error }
        }
        val root = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val folder = File(root, "GhajarVPN").apply { mkdirs() }
        val target = File(folder, DownloadNames.collisionFree(task.fileName, folder.list()?.toSet().orEmpty()))
        source.inputStream().use { input -> target.outputStream().use { input.copyTo(it, 128 * 1024) } }
        return Uri.fromFile(target)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(128 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun onUnmeteredWifi(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun hasValidatedNetwork(): Boolean = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    private fun tempDirectory(id: String) = File(cacheDir, "ghajar-downloads/$id")

    private fun notifyTask(task: DownloadTask?) {
        task ?: return
        val progress = if (task.totalBytes > 0) ((task.downloadedBytes * 100 / task.totalBytes).coerceIn(0, 100)).toInt() else 0
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.fileName).setOnlyAlertOnce(true).setContentIntent(openManagerIntent())
            .setContentText(notificationText(task)).setProgress(100, progress, task.totalBytes <= 0)
        when (task.state) {
            DownloadState.DOWNLOADING, DownloadState.PROBING -> builder.setOngoing(true).addAction(0, "توقف", actionIntent(task.id, DownloadContract.ACTION_PAUSE, 1))
            DownloadState.PAUSED, DownloadState.FAILED -> builder.addAction(0, "ادامه", actionIntent(task.id, DownloadContract.ACTION_RESUME, 2))
            DownloadState.COMPLETED -> builder.setSmallIcon(android.R.drawable.stat_sys_download_done).setProgress(0, 0, false)
            else -> Unit
        }
        if (task.state !in setOf(DownloadState.COMPLETED, DownloadState.CANCELED)) builder.addAction(0, "لغو", actionIntent(task.id, DownloadContract.ACTION_CANCEL, 3))
        notifications.notify(task.id.hashCode(), builder.build())
    }

    private fun notificationText(task: DownloadTask) = when (task.state) {
        DownloadState.QUEUED -> "در صف"
        DownloadState.PROBING -> "بررسی پشتیبانی سرور"
        DownloadState.DOWNLOADING -> "${formatBytes(task.speedBytesPerSecond)}/s · ${task.activeConnections} اتصال"
        DownloadState.PAUSED -> task.error.ifBlank { "متوقف شده" }
        DownloadState.COMPLETED -> "دانلود کامل شد"
        DownloadState.FAILED -> task.error.ifBlank { "دانلود ناموفق بود" }
        DownloadState.CANCELED -> "لغو شد"
    }

    private fun updateSummary() {
        val tasks = repository.all()
        val waitingForNetwork = tasks.any { it.state == DownloadState.PAUSED && it.error in setOf(WAIT_NETWORK, WAIT_WIFI) }
        if (active.isEmpty() && tasks.none { it.state == DownloadState.QUEUED } && !waitingForNetwork) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else notifications.notify(SUMMARY_ID, summaryNotification(if (active.isEmpty()) "در انتظار شبکه" else "${active.size} دانلود فعال"))
    }
    private fun summaryNotification(text: String) = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("مدیر دانلود قاجار").setContentText(text).setOngoing(active.isNotEmpty()).setContentIntent(openManagerIntent()).build()
    private fun openManagerIntent() = PendingIntent.getActivity(this, 0, Intent(this, DownloadManagerActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun actionIntent(id: String, action: String, suffix: Int) = PendingIntent.getBroadcast(this, id.hashCode() + suffix,
        Intent(this, DownloadEnqueueReceiver::class.java).setAction(action).putExtra(DownloadContract.EXTRA_ID, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(NotificationChannel(CHANNEL, "دانلودهای قاجار", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        private const val CHANNEL = "ghajar_downloads"
        private const val SUMMARY_ID = 72340
        private const val MAX_CONCURRENT = 2
        private const val MAX_RETRIES = 3
        private const val WAIT_NETWORK = "در انتظار شبکه"
        private const val WAIT_WIFI = "در انتظار Wi-Fi"
        fun start(context: Context, action: String = DownloadContract.ACTION_RUN, id: String = "") {
            val intent = Intent(context, GhajarDownloadService::class.java).setAction(action).putExtra(DownloadContract.EXTRA_ID, id)
            ContextCompat.startForegroundService(context, intent)
        }
        fun formatBytes(value: Long): String = when {
            value < 1024 -> "$value B"
            value < 1024 * 1024 -> "%.1f KB".format(value / 1024.0)
            value < 1024L * 1024 * 1024 -> "%.1f MB".format(value / 1024.0 / 1024)
            else -> "%.1f GB".format(value / 1024.0 / 1024 / 1024)
        }
    }

    private class DownloadFailure(message: String) : Exception(message)
    private class AccessDenied : Exception()
}

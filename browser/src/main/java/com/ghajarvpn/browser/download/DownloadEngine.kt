package com.ghajarvpn.browser.download

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Real HTTP download engine: HEAD/Range probing, segmented parallel downloads
 * with graceful single-stream fallback, cooperative pause/cancel, resume and
 * SHA-256 verification. Runs on worker threads; progress is reported through
 * [Listener] and persisted by the caller so process death can be recovered.
 * [defaultDirectory] is where part/completed files land (real usage passes the
 * app's external files dir; tests pass a temp directory).
 */
class DownloadEngine(private val defaultDirectory: File) {

    interface Listener {
        fun onUpdate(item: DownloadItem, speedBps: Long)
        fun onTerminal(item: DownloadItem)
    }

    private val globalPause = AtomicBoolean(false)

    /** A started download; controls are cooperative and safe from any thread. */
    inner class Job internal constructor(
        @Volatile var item: DownloadItem,
        private val listener: Listener,
        private val cancelled: AtomicBoolean = AtomicBoolean(false),
        private val paused: AtomicBoolean = AtomicBoolean(false)
    ) {
        private val threads = mutableListOf<Thread>()
        private val completed = AtomicBoolean(false)

        fun start() {
            val worker = Thread {
                try { run() } catch (error: Exception) { fail(error) }
            }
            worker.isDaemon = false
            threads += worker
            worker.start()
        }

        fun pause() { paused.set(true) }
        fun cancel() { cancelled.set(true) }
        fun pausedFlag(): AtomicBoolean = paused
        fun threads(): List<Thread> = threads.toList()

        private fun run() {
            var current = if (item.totalBytes <= 0) probe(item) else item
            if (current.segments.isEmpty()) {
                current = current.copy(
                    segments = DownloadPlanner.planSegments(current.totalBytes, DownloadPlanner.segmentCount(current.acceptRanges, current.totalBytes))
                )
            }
            current = current.copy(state = DownloadState.RUNNING, updatedAt = System.currentTimeMillis())
            item = current
            listener.onUpdate(current, 0)

            val directory = File(current.directory.ifBlank { defaultDirectory.absolutePath })
            directory.mkdirs()
            val target = File(directory, current.fileName)
            val part = File(directory, current.fileName + ".ghajar-part")
            if (current.totalBytes > 0 && (!part.exists() || part.length() != current.totalBytes)) {
                RandomAccessFile(part, "rw").use { it.setLength(current.totalBytes) }
            }

            val workers = current.segments.map { segment ->
                Thread { downloadSegment(current, part, segment, paused, cancelled) }.also { it.start() }
            }
            workers.forEach { it.join() }

            if (cancelled.get()) {
                finish(DownloadState.CANCELLED)
                return
            }
            if (paused.get()) {
                finish(DownloadState.PAUSED)
                return
            }
            val downloaded = part.length()
            if (current.totalBytes > 0 && downloaded < current.totalBytes) throw IOException("incomplete")
            val digest = sha256(part)
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IOException("move failed")
            item = current.copy(state = DownloadState.COMPLETED, downloadedBytes = target.length(), totalBytes = target.length(), sha256 = digest, completedAt = System.currentTimeMillis())
            completed.set(true)
            listener.onUpdate(item, 0)
            listener.onTerminal(item)
        }

        private fun finish(state: DownloadState) {
            completed.set(true)
            item = item.copy(state = state, updatedAt = System.currentTimeMillis())
            listener.onUpdate(item, 0)
            listener.onTerminal(item)
        }

        private fun fail(error: Exception) {
            if (completed.get()) return
            completed.set(true)
            item = item.copy(state = DownloadState.FAILED, error = error.javaClass.simpleName, updatedAt = System.currentTimeMillis())
            android.util.Log.w(TAG, "download failed: ${error.message}")
            listener.onUpdate(item, 0)
            listener.onTerminal(item)
        }
    }

    fun probe(item: DownloadItem): DownloadItem {
        val connection = open(item.url, item, method = "HEAD") ?: throw IOException("open failed")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("http $code")
            val acceptRanges = "bytes".equals(connection.getHeaderField("Accept-Ranges"), true)
            val length = connection.getHeaderFieldLong("Content-Length", -1L)
            item.copy(
                acceptRanges = acceptRanges,
                totalBytes = if (length > 0) length else item.totalBytes,
                eTag = connection.getHeaderField("ETag").orEmpty(),
                lastModified = connection.getHeaderField("Last-Modified").orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadSegment(item: DownloadItem, part: File, segment: DownloadSegment, paused: AtomicBoolean, cancelled: AtomicBoolean) {
        while (segment.start + segment.downloaded <= segment.end) {
            if (cancelled.get() || paused.get()) return
            val from = segment.start + segment.downloaded
            val connection = open(item.url, item, method = "GET") ?: throw IOException("open failed")
            try {
                connection.setRequestProperty("Range", "bytes=$from-${segment.end}")
                val code = connection.responseCode
                if (code == 416) return
                if (code !in 200..299) throw IOException("http $code")
                connection.inputStream.use { input ->
                    RandomAccessFile(part, "rw").use { output ->
                        output.seek(from)
                        copyCapped(input, output, from, segment.end, segment, paused, cancelled)
                    }
                }
                if (segment.start + segment.downloaded > segment.end) return
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun copyCapped(input: InputStream, output: RandomAccessFile, from: Long, end: Long, segment: DownloadSegment, paused: AtomicBoolean, cancelled: AtomicBoolean) {
        val buffer = ByteArray(BUFFER_SIZE)
        var position = from
        while (position <= end) {
            if (cancelled.get() || paused.get()) return
            val read = input.read(buffer, 0, minOf(BUFFER_SIZE, (end - position + 1).toInt().coerceAtLeast(1)))
            if (read < 0) return
            output.write(buffer, 0, read)
            position += read
            segment.downloaded += read
        }
    }

    private fun open(raw: String, item: DownloadItem, method: String): HttpURLConnection? {
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) return null
        val connection = URL(raw).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        item.userAgent.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("User-Agent", it) }
        item.referer.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Referer", it) }
        item.cookies.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
        return connection
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "GhajarDownload"
        private const val BUFFER_SIZE = 64 * 1024
    }
}

package net.gozar.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** Public story media only. No bearer, cookies or checkout state enter this downloader. */
internal object GhajarPublicMedia {
    private val mutex = Mutex()
    suspend fun file(context: Context, input: String, video: Boolean = false): File = withContext(Dispatchers.IO) {
        val url = GhajarStoryRules.mediaUrl(input) ?: throw IOException("Invalid story media URL")
        val limit = if (video) 40L * 1024 * 1024 else 12L * 1024 * 1024
        mutex.withLock {
            val folder = File(context.cacheDir, "ghajar-story-media").apply { mkdirs() }
            val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val target = File(folder, key)
            if (target.isFile && target.length() in 1..limit) {
                target.setLastModified(System.currentTimeMillis())
                return@withLock target
            }
            val partial = File(folder, "$key.part")
            var next = url
            try {
                for (redirect in 0..3) {
                    currentCoroutineContext().ensureActive()
                    val connection = (URL(next).openConnection() as HttpsURLConnection).apply {
                        connectTimeout = 7000; readTimeout = 15000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "Ghajarvpn")
                    }
                    try {
                        val status = connection.responseCode
                        if (status in listOf(301, 302, 303, 307, 308)) {
                            val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                            next = GhajarStoryRules.mediaUrl(URL(URL(next), location).toString())
                                ?: throw IOException("Unsafe media redirect")
                            continue
                        }
                        if (status != 200 || connection.contentLengthLong > limit) throw IOException("Story media unavailable or too large")
                        connection.inputStream.use { source -> partial.outputStream().use { destination ->
                            val buffer = ByteArray(16384); var total = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > limit) throw IOException("Story media too large")
                                destination.write(buffer, 0, count)
                            }
                        } }
                        if (partial.length() == 0L || !partial.renameTo(target)) throw IOException("Unable to store story media")
                        var bytes = folder.listFiles().orEmpty().sumOf { it.length() }
                        folder.listFiles().orEmpty().filter { it != target && !it.name.endsWith(".part") }.sortedBy { it.lastModified() }.forEach {
                            if (bytes > 96L * 1024 * 1024) { val size = it.length(); if (it.delete()) bytes -= size }
                        }
                        return@withLock target
                    } finally { connection.disconnect() }
                }
                throw IOException("Too many media redirects")
            } finally { partial.delete() }
        }
    }

    suspend fun image(context: Context, url: String, maxEdge: Int = 1440): Bitmap = withContext(Dispatchers.IO) {
        val file = file(context, url)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported story image")
        var sample = 1
        while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) sample *= 2
        currentCoroutineContext().ensureActive()
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Unable to decode story image")
    }
}

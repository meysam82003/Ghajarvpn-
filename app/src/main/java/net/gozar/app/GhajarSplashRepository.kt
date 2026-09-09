package net.gozar.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object GhajarSplashRepository {
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "ghajar_live_splash.img")
        runCatching { fetchLivePoster(cache) }.getOrNull()
            ?: runCatching { BitmapFactory.decodeFile(cache.absolutePath) }.getOrNull()
    }

    private fun fetchLivePoster(cache: File): Bitmap? {
        val html = readBytes(BrandConfig.STORE_URL, 1_500_000).toString(Charsets.UTF_8)
        val match = Regex(
            "(?i)(https://[^\\\"' ]+/assets/boot-media/[^\\\"'<> ]+|/[^\\\"' ]*assets/boot-media/[^\\\"'<> ]+)"
        ).find(html)?.value ?: return null
        val absolute = URL(URL(BrandConfig.STORE_URL), match).toString()
        val uri = URI(absolute)
        if (uri.scheme != "https" || uri.host != BrandConfig.STORE_HOST) return null
        if (!uri.path.contains("/assets/boot-media/")) return null
        val bytes = readBytes(absolute, MAX_IMAGE_BYTES)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val temp = File(cache.parentFile, cache.name + ".tmp")
        temp.outputStream().use { it.write(bytes) }
        if (!temp.renameTo(cache)) {
            cache.outputStream().use { it.write(bytes) }
            temp.delete()
        }
        return bitmap
    }

    private fun readBytes(url: String, max: Int): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Ghajarvpn-Android/3.0")
            require(conn.responseCode in 200..299)
            val declared = conn.contentLength
            require(declared <= max || declared < 0)
            return conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= max)
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }
}

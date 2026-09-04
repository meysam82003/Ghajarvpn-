package com.ghajarvpn.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * Safe in-app PDF handling. Remote PDF URLs are downloaded into a private,
 * name-derived file inside the app's external files dir and opened through the
 * app FileProvider with an explicit read grant. Nothing is passed to arbitrary
 * external apps beyond a read-only content URI.
 */
object BrowserPdf {

    private const val MAX_BYTES = 256L * 1024 * 1024

    fun fileNameFor(url: String): String {
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        val name = runCatching { Uri.parse(url).lastPathSegment.orEmpty() }
            .getOrDefault("").substringAfterLast('/').substringBefore('?')
        val base = if (name.endsWith(".pdf", true) && name.isNotBlank()) name else "document.pdf"
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(10)
        val clean = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "pdf-$digest-${clean.ifBlank { "document.pdf" }}"
    }

    fun downloadAndOpen(context: Context, url: String, headers: Map<String, String>): Boolean {
        if (!url.startsWith("https://")) return false
        val directory = context.getExternalFilesDir(null) ?: context.filesDir
        directory.mkdirs()
        val target = File(directory, fileNameFor(url))
        return runCatching {
            val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) { connection.disconnect(); return false }
            val type = connection.contentType.orEmpty().lowercase()
            if (type.isNotBlank() && !type.contains("pdf") && !type.contains("octet-stream")) { connection.disconnect(); return false }
            connection.inputStream.use { input ->
                var total = 0L
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) throw IllegalStateException("too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            connection.disconnect()
            // PDF magic: %PDF
            val head = target.inputStream().use { stream ->
                val magic = ByteArray(5); stream.read(magic); String(magic, Charsets.ISO_8859_1)
            }
            if (!head.startsWith("%PDF")) { target.delete(); return false }
            open(context, target)
            true
        }.getOrDefault(false)
    }

    fun open(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.ghajar_downloads", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "نمایشگر PDF پیدا نشد", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

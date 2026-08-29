package net.gozar.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object SpeedTest {

    private const val PROXY_HOST = "127.0.0.1"
    private val PROXY_PORT get() = MixedPort.value
    private const val DELAY_URL = "https://www.gstatic.com/generate_204"
    private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=26214400"
    private const val UPLOAD_URL = "https://speed.cloudflare.com/__up"
    private const val UPLOAD_BYTES = 4 * 1024 * 1024

    private fun proxy() =
        if (IkeController.active || VpnState.activeId.value?.startsWith("ovpn:") == true) Proxy.NO_PROXY
        else Proxy(Proxy.Type.SOCKS, InetSocketAddress(PROXY_HOST, PROXY_PORT))

    private fun measureOnce(timeoutMs: Int): Int? = try {
        val conn = (URL(DELAY_URL).openConnection(proxy()) as HttpURLConnection).apply {
            connectTimeout = timeoutMs; readTimeout = timeoutMs; requestMethod = "GET"
            setRequestProperty("User-Agent", "Ghajarvpn")
        }
        val start = System.currentTimeMillis()
        conn.connect()
        conn.responseCode
        val ms = (System.currentTimeMillis() - start).toInt()
        conn.disconnect()
        ms
    } catch (e: Exception) { null }

    suspend fun delay(): Int? = withContext(Dispatchers.IO) {
        measureOnce(6000) ?: return@withContext null
        val first = measureOnce(5000)
        val second = measureOnce(5000)
        when {
            first != null && second != null -> minOf(first, second)
            else -> first ?: second
        }
    }

    suspend fun download(): Double? = withContext(Dispatchers.IO) {
        try {
            var peakBytesPerSec = 0.0
            var sliceBytes = 0L
            var sliceStart = 0L
            var overallStart = 0L
            val sliceMs = 500L
            val maxDuration = 12000L

            outer@ while (true) {
                val conn = (URL(DOWNLOAD_URL).openConnection(proxy()) as HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 20000; requestMethod = "GET"
                    setRequestProperty("User-Agent", "Ghajarvpn")
                }
                conn.connect()
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        val now = System.currentTimeMillis()
                        if (overallStart == 0L) { overallStart = now; sliceStart = now }
                        sliceBytes += n

                        val elapsed = now - sliceStart
                        if (elapsed >= sliceMs) {
                            val rate = sliceBytes * 1000.0 / elapsed
                            if (rate > peakBytesPerSec) peakBytesPerSec = rate
                            sliceBytes = 0
                            sliceStart = now
                        }
                        if (now - overallStart > maxDuration) { conn.disconnect(); break@outer }
                    }
                }
                conn.disconnect()
                if (overallStart != 0L && System.currentTimeMillis() - overallStart > maxDuration) break
            }

            if (peakBytesPerSec <= 0) null
            else peakBytesPerSec * 8.0 / 1_000_000.0
        } catch (e: Exception) { null }
    }

    /** Independent upload fallback for native cores that return a zero upload
     * result. It uses the same tunnel-aware SOCKS route as latency/download. */
    suspend fun upload(): Double? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(UPLOAD_URL).openConnection(proxy()) as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                setFixedLengthStreamingMode(UPLOAD_BYTES)
                setRequestProperty("User-Agent", "Ghajarvpn")
                setRequestProperty("Content-Type", "application/octet-stream")
            }
            val chunk = ByteArray(64 * 1024) { index -> (index and 0xFF).toByte() }
            var written = 0
            val started = System.nanoTime()
            conn.outputStream.use { output ->
                while (written < UPLOAD_BYTES) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(chunk.size, UPLOAD_BYTES - written)
                    output.write(chunk, 0, count)
                    written += count
                }
                output.flush()
            }
            val code = conn.responseCode
            val elapsed = System.nanoTime() - started
            runCatching { conn.inputStream.close() }
            if (code !in 200..299) null else rateMbps(written.toLong(), elapsed)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun rateMbps(bytes: Long, elapsedNanos: Long): Double? =
        if (bytes <= 0L || elapsedNanos <= 0L) null
        else bytes * 8.0 * 1_000.0 / elapsedNanos
}

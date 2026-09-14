package net.gozar.app.freecfg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One deadline includes redirects and the entire body, including slow-drip responses. */
object FreeFeedHttp {
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "free-feed-deadline").apply { isDaemon = true } }
    suspend fun read(url: String, proxy: Proxy): String = runInterruptible(Dispatchers.IO) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        val secureStart = URL(url).protocol == "https"
        var current = URL(url)
        var result: String? = null
        for (hop in 0..3) {
            require(current.protocol == "https" || (!secureStart && current.protocol == "http"))
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).toInt()
            require(remaining > 0) { "Feed request timed out" }
            val conn = (current.openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = remaining; readTimeout = remaining; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0 Ghajarvpn/1.0.0")
            }
            val timeout = watchdog.schedule({ conn.disconnect() }, remaining.toLong(), TimeUnit.MILLISECONDS)
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: error("Redirect without location")
                    current = URL(current, location)
                    continue
                }
                check(code in 200..299) { "HTTP $code" }
                result = conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        check(!Thread.currentThread().isInterrupted && System.nanoTime() < deadline) { "Feed request timed out" }
                        val n = input.read(buffer)
                        if (n < 0) break
                        require(out.size() + n <= 4 * 1024 * 1024) { "Feed is too large" }
                        out.write(buffer, 0, n)
                    }
                    out.toString("UTF-8")
                }
                break
            } finally { timeout.cancel(false); conn.disconnect() }
        }
        result ?: error("Too many redirects")
    }
}

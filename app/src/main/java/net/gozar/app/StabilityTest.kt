package net.gozar.app

import gozarcore.Gozarcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
object StabilityTest {

    enum class Phase { PING, DOWNLOAD, UPLOAD, DONE }

    data class Result(
        val downloadMbps: Double,
        val uploadMbps: Double,
        val idleLatency: Double,
        val jitter: Double,
        val downloadLatency: Double,
        val uploadLatency: Double
    )

    private fun phaseOf(s: String): Phase = when (s) {
        "download" -> Phase.DOWNLOAD
        "upload" -> Phase.UPLOAD
        "ping" -> Phase.PING
        else -> Phase.DONE
    }
    suspend fun run(testConfigJson: String, onProgress: (Phase, Double) -> Unit): Result? =
        coroutineScope {
            val poller = launch(Dispatchers.IO) {
                try {
                    while (isActive) {
                        val ph = phaseOf(Gozarcore.speedTestPhase())
                        if (ph != Phase.DONE) onProgress(ph, Gozarcore.speedTestLive())
                        delay(100)
                    }
                } catch (_: Exception) {}
            }
            val json = withContext(Dispatchers.IO) {
                try { Gozarcore.runSpeedTest(testConfigJson) } catch (e: Exception) { "" }
            }
            poller.cancel()
            parse(json)
        }

    private fun parse(json: String): Result? {
        if (json.isBlank()) return null
        return try {
            val o = JSONObject(json)
            if (o.has("error")) return null
            Result(
                downloadMbps = o.optDouble("download", 0.0),
                uploadMbps = o.optDouble("upload", 0.0),
                idleLatency = o.optDouble("idle", 0.0),
                jitter = o.optDouble("jitter", 0.0),
                downloadLatency = o.optDouble("dlLatency", 0.0),
                uploadLatency = o.optDouble("ulLatency", 0.0)
            )
        } catch (e: Exception) { null }
    }
    fun toJson(r: Result): String = JSONObject()
        .put("download", r.downloadMbps).put("upload", r.uploadMbps)
        .put("idle", r.idleLatency).put("jitter", r.jitter)
        .put("dlLatency", r.downloadLatency).put("ulLatency", r.uploadLatency)
        .toString()

    fun fromJson(json: String): Result? = parse(json)
}
/**
 * Bufferbloat grade: how much latency grows while the line is saturated
 * (loaded minus idle latency), the way dedicated speed tests grade it.
 */
fun bufferbloatGrade(r: StabilityTest.Result): String {
    val growth = maxOf(r.downloadLatency, r.uploadLatency) - r.idleLatency
    return when {
        growth < 30 -> "A"
        growth < 60 -> "B"
        growth < 200 -> "C"
        growth < 400 -> "D"
        else -> "F"
    }
}

/** The last speed/quality results on this device, newest first. */
object QualityHistory {
    data class Entry(val timeMs: Long, val target: String, val result: StabilityTest.Result)

    private const val PREFS = "ghajar_quality_history"
    private const val KEY = "entries"
    private const val MAX = 20

    fun list(context: android.content.Context): List<Entry> = runCatching {
        val arr = org.json.JSONArray(context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            StabilityTest.fromJson(o.optString("r"))?.let { Entry(o.optLong("t"), o.optString("s"), it) }
        }
    }.getOrDefault(emptyList())

    fun add(context: android.content.Context, target: String, r: StabilityTest.Result, timeMs: Long) {
        val all = listOf(Entry(timeMs, target, r)) + list(context)
        val arr = org.json.JSONArray()
        all.take(MAX).forEach { e ->
            arr.put(JSONObject().put("t", e.timeMs).put("s", e.target).put("r", StabilityTest.toJson(e.result)))
        }
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun clear(context: android.content.Context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

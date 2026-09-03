package net.gozar.app

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ln

enum class SmartMode { AUTO, FASTEST, STABLE, GAMING, DOWNLOAD, BROWSING, STREAMING, EMERGENCY }

data class ServerHealth(
    val latencyMs: Double = -1.0,
    val jitterMs: Double = -1.0,
    val packetLossPercent: Double = 100.0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val recentFailures: Int = 0,
    val throughputBytesPerSecond: Long = -1,
    val lastSuccessfulAt: Long = 0,
    val unhealthyUntil: Long = 0,
    val updatedAt: Long = 0
) {
    val successRate: Double get() = successCount.toDouble() / (successCount + failureCount).coerceAtLeast(1)
    fun unavailable(now: Long) = unhealthyUntil > now
}

data class ServerCandidate(
    val id: String,
    val protocolAvailable: Boolean = true,
    val serverCountry: String = "",
    val userCountry: String = ""
)

data class RankedServer(val candidate: ServerCandidate, val health: ServerHealth, val score: Double)

object SmartConnectScorer {
    fun rank(candidates: List<ServerCandidate>, health: Map<String, ServerHealth>, mode: SmartMode, now: Long = System.currentTimeMillis()): List<RankedServer> {
        val ranked = candidates.map { candidate ->
            val state = health[candidate.id] ?: ServerHealth()
            RankedServer(candidate, state, score(candidate, state, mode, now))
        }
        val healthy = ranked.filterNot { it.health.unavailable(now) }
        return (if (healthy.isEmpty() || mode == SmartMode.EMERGENCY) ranked else healthy).sortedByDescending { it.score }
    }

    fun score(candidate: ServerCandidate, health: ServerHealth, mode: SmartMode, now: Long): Double {
        if (!candidate.protocolAvailable) return -1.0
        val latency = if (health.latencyMs >= 0) (1.0 - health.latencyMs / 2_000.0).coerceIn(0.0, 1.0) else 0.15
        val jitter = if (health.jitterMs >= 0) (1.0 - health.jitterMs / 500.0).coerceIn(0.0, 1.0) else 0.15
        val loss = (1.0 - health.packetLossPercent / 100.0).coerceIn(0.0, 1.0)
        val success = health.successRate
        val throughput = if (health.throughputBytesPerSecond > 0) (ln(1.0 + health.throughputBytesPerSecond) / ln(1.0 + 50_000_000.0)).coerceIn(0.0, 1.0) else 0.0
        val recent = if (health.lastSuccessfulAt <= 0) 0.0 else (1.0 - (now - health.lastSuccessfulAt).coerceAtLeast(0) / (7.0 * 24 * 60 * 60 * 1000)).coerceIn(0.0, 1.0)
        val sameCountry = if (candidate.userCountry.isNotBlank() && candidate.userCountry.equals(candidate.serverCountry, true)) 1.0 else 0.0
        val weights = when (mode) {
            SmartMode.FASTEST -> Weights(.58, .10, .08, .12, .04, .04, .04)
            SmartMode.STABLE -> Weights(.18, .23, .23, .24, .02, .08, .02)
            SmartMode.GAMING -> Weights(.38, .29, .17, .10, .00, .04, .02)
            SmartMode.DOWNLOAD -> Weights(.16, .12, .13, .18, .34, .05, .02)
            SmartMode.BROWSING -> Weights(.27, .14, .14, .24, .08, .09, .04)
            SmartMode.STREAMING -> Weights(.13, .15, .18, .18, .29, .05, .02)
            SmartMode.EMERGENCY -> Weights(.11, .08, .13, .52, .02, .12, .02)
            SmartMode.AUTO -> Weights(.25, .16, .16, .24, .08, .08, .03)
        }
        val failurePenalty = health.recentFailures.coerceAtMost(5) * if (mode == SmartMode.EMERGENCY) .025 else .075
        return weights.latency * latency + weights.jitter * jitter + weights.loss * loss + weights.success * success +
                weights.throughput * throughput + weights.recent * recent + weights.location * sameCountry - failurePenalty
    }

    private data class Weights(val latency: Double, val jitter: Double, val loss: Double, val success: Double, val throughput: Double, val recent: Double, val location: Double)
}

class SmartConnectPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ghajar_smart_connect", Context.MODE_PRIVATE)
    var mode: SmartMode
        get() = runCatching { SmartMode.valueOf(prefs.getString("mode", SmartMode.AUTO.name).orEmpty()) }.getOrDefault(SmartMode.AUTO)
        set(value) { prefs.edit().putString("mode", value.name).apply() }
}

class ServerHealthRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ghajar_server_health", Context.MODE_PRIVATE)
    private val lock = Any()

    fun get(configId: String): ServerHealth = synchronized(lock) { read(key(configId)) }
    fun snapshot(configIds: Collection<String>): Map<String, ServerHealth> = configIds.associateWith(::get)

    fun recordProbe(configId: String, samples: List<Long?>) = update(configId) { old ->
        val valid = samples.filterNotNull().filter { it >= 0 }
        val loss = (samples.size - valid.size) * 100.0 / samples.size.coerceAtLeast(1)
        val jitter = valid.zipWithNext().map { (a, b) -> abs(a - b).toDouble() }.averageDoubleOr(-1.0)
        val failures = if (valid.isEmpty()) (old.recentFailures + 1).coerceAtMost(10) else (old.recentFailures - 1).coerceAtLeast(0)
        old.copy(
            latencyMs = valid.averageLongOr(old.latencyMs), jitterMs = jitter, packetLossPercent = loss,
            recentFailures = failures, unhealthyUntil = if (failures >= 3) System.currentTimeMillis() + UNHEALTHY_MS else 0,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun recordConnectionSuccess(configId: String) = update(configId) { it.copy(successCount = capped(it.successCount + 1), recentFailures = 0, lastSuccessfulAt = System.currentTimeMillis(), unhealthyUntil = 0, updatedAt = System.currentTimeMillis()) }
    fun recordConnectionFailure(configId: String) = update(configId) {
        val failures = (it.recentFailures + 1).coerceAtMost(10)
        it.copy(failureCount = capped(it.failureCount + 1), recentFailures = failures, unhealthyUntil = if (failures >= 3) System.currentTimeMillis() + UNHEALTHY_MS else it.unhealthyUntil, updatedAt = System.currentTimeMillis())
    }
    fun recordThroughput(configId: String, bytesPerSecond: Long) {
        if (bytesPerSecond > 0) update(configId) { it.copy(throughputBytesPerSecond = bytesPerSecond, updatedAt = System.currentTimeMillis()) }
    }

    private fun update(configId: String, transform: (ServerHealth) -> ServerHealth) = synchronized(lock) {
        val storageKey = key(configId); prefs.edit().putString(storageKey, transform(read(storageKey)).toJson().toString()).apply()
    }
    private fun read(storageKey: String): ServerHealth = runCatching { JSONObject(prefs.getString(storageKey, "{}")).toHealth() }.getOrDefault(ServerHealth())
    private fun key(id: String) = "server_" + MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    private fun capped(value: Int) = if (value > 10_000) value / 2 else value
    private fun List<Long>.averageLongOr(fallback: Double) = if (isEmpty()) fallback else average()
    private fun List<Double>.averageDoubleOr(fallback: Double) = if (isEmpty()) fallback else average()
    private fun ServerHealth.toJson() = JSONObject().put("latency", latencyMs).put("jitter", jitterMs).put("loss", packetLossPercent)
        .put("success", successCount).put("failure", failureCount).put("recentFailures", recentFailures).put("throughput", throughputBytesPerSecond)
        .put("lastSuccess", lastSuccessfulAt).put("unhealthyUntil", unhealthyUntil).put("updatedAt", updatedAt)
    private fun JSONObject.toHealth() = ServerHealth(optDouble("latency", -1.0), optDouble("jitter", -1.0), optDouble("loss", 100.0),
        optInt("success"), optInt("failure"), optInt("recentFailures"), optLong("throughput", -1), optLong("lastSuccess"), optLong("unhealthyUntil"), optLong("updatedAt"))

    private companion object { const val UNHEALTHY_MS = 10 * 60 * 1000L }
}

package net.gozar.app.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ranks servers for AutoSelect from their recent real tests, not from one
 * sample.
 *
 * Each sample is one real test on the engine that carries the server
 * (EngineTester): did the protocol carry a request at all, latency, jitter,
 * packet loss and the engine's handshake time. History is kept per network
 * type (Wi-Fi and mobile rank servers differently), over the last [WINDOW]
 * tests.
 *
 * score (lower is better) =
 *   median latency
 * + median jitter
 * + mean loss% x [LOSS_PENALTY_MS]
 * + failure rate x [FAILURE_PENALTY_MS]
 * + median handshake / 4
 * + instability (standard deviation of latency) / 2
 *
 * A server with no success in the window has no score and is never picked.
 * [shouldSwitch] adds hysteresis so two servers of equal quality do not
 * trade places every minute.
 */
class AutoSelectScore {

    data class Sample(
        val ok: Boolean,
        val latencyMs: Int? = null,
        val jitterMs: Int? = null,
        val lossPct: Int? = null,
        val handshakeMs: Int? = null
    )

    private val history = HashMap<String, ArrayDeque<Sample>>()

    /** Network type the next samples belong to ("WIFI", "CELLULAR", …). */
    @Volatile var network: String = ""

    private fun key(id: String) = "$network|$id"

    @Synchronized
    fun record(id: String, sample: Sample) {
        val h = history.getOrPut(key(id)) { ArrayDeque() }
        h.addLast(sample)
        while (h.size > WINDOW) h.removeFirst()
    }

    /** Records a bare delay test: a delay in ms, or null for a failure. */
    fun record(id: String, ms: Int?) = record(id, Sample(ok = ms != null, latencyMs = ms))

    @Synchronized
    fun forget(keep: Set<String>) { history.keys.retainAll { k -> k.substringAfter('|') in keep } }

    @Synchronized
    fun score(id: String): Double? {
        val h = history[key(id)] ?: return null
        val ok = h.filter { it.ok && it.latencyMs != null }
        if (ok.isEmpty()) return null
        val lat = ok.map { it.latencyMs!!.toDouble() }
        val median = median(lat)
        val jitter = ok.mapNotNull { it.jitterMs?.toDouble() }.let { if (it.isEmpty()) lat.map { v -> abs(v - median) }.average() else median(it) }
        val loss = h.mapNotNull { it.lossPct?.toDouble() }.let { if (it.isEmpty()) 0.0 else it.average() }
        val failureRate = (h.size - ok.size).toDouble() / h.size
        val handshake = ok.mapNotNull { it.handshakeMs?.toDouble() }.let { if (it.isEmpty()) 0.0 else median(it) }
        val mean = lat.average()
        val instability = sqrt(lat.map { (it - mean) * (it - mean) }.average())
        return median + jitter + loss * LOSS_PENALTY_MS + failureRate * FAILURE_PENALTY_MS + handshake / 4 + instability / 2
    }

    /** The two most recent tests of [id] both failed. */
    @Synchronized
    fun failingNow(id: String): Boolean {
        val h = history[key(id)] ?: return false
        return h.size >= 2 && !h.last().ok && !h[h.size - 2].ok
    }

    /** Best candidate among [ids], or null when none has a score. */
    fun best(ids: Collection<String>): Pair<String, Double>? =
        ids.mapNotNull { id -> score(id)?.let { id to it } }.minByOrNull { it.second }

    /**
     * Switch from [current] to [candidate] only when the candidate is clearly
     * better, or the current one has stopped answering.
     */
    fun shouldSwitch(current: String?, candidate: String): Boolean {
        if (current == null) return true
        if (current == candidate) return false
        if (failingNow(current)) return true
        val cur = score(current) ?: return true
        val cand = score(candidate) ?: return false
        return cur - cand >= maxOf(SWITCH_MARGIN_MS, cur * SWITCH_MARGIN_RATIO)
    }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    companion object {
        const val WINDOW = 5
        const val FAILURE_PENALTY_MS = 1500.0
        const val LOSS_PENALTY_MS = 15.0
        const val SWITCH_MARGIN_MS = 40.0
        const val SWITCH_MARGIN_RATIO = 0.15
    }
}

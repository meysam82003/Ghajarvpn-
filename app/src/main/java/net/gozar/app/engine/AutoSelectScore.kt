package net.gozar.app.engine

import kotlin.math.abs

/**
 * Ranks servers for AutoSelect from their recent real tests, not from one
 * sample. One lucky ping used to be enough to win and one slow one enough to
 * lose; a server that answers fast half the time and not at all the other
 * half is worse than a steady one, and the score says so.
 *
 * score = median latency + jitter + failure penalty (lower is better)
 *  - median of the last [WINDOW] successful delays
 *  - jitter = mean absolute deviation of those delays
 *  - failure penalty = failure rate x [FAILURE_PENALTY_MS]
 *
 * A server with no success in the window has no score and is never picked.
 * [shouldSwitch] adds hysteresis so two servers of equal quality do not
 * trade places every minute.
 */
class AutoSelectScore {

    private val history = HashMap<String, ArrayDeque<Int?>>()

    /** Records one test: a delay in ms, or null for a failure. */
    @Synchronized
    fun record(id: String, ms: Int?) {
        val h = history.getOrPut(id) { ArrayDeque() }
        h.addLast(ms)
        while (h.size > WINDOW) h.removeFirst()
    }

    @Synchronized
    fun forget(keep: Set<String>) { history.keys.retainAll(keep) }

    @Synchronized
    fun score(id: String): Double? {
        val h = history[id] ?: return null
        val ok = h.filterNotNull()
        if (ok.isEmpty()) return null
        val sorted = ok.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble()
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        val jitter = ok.map { abs(it - median) }.average()
        val failureRate = (h.size - ok.size).toDouble() / h.size
        return median + jitter + failureRate * FAILURE_PENALTY_MS
    }

    /** The two most recent tests of [id] both failed. */
    @Synchronized
    fun failingNow(id: String): Boolean {
        val h = history[id] ?: return false
        return h.size >= 2 && h.last() == null && h[h.size - 2] == null
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

    companion object {
        const val WINDOW = 5
        const val FAILURE_PENALTY_MS = 1500.0
        const val SWITCH_MARGIN_MS = 40.0
        const val SWITCH_MARGIN_RATIO = 0.15
    }
}

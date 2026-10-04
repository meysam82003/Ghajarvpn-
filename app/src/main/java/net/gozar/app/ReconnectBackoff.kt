package net.gozar.app

/** Monotonic time supplied by the caller. A stable minute resets network-flap backoff. */
internal class ReconnectBackoff {
    private var lastAttempt: Long? = null
    private var attempts = 0
    @Synchronized fun delayMs(now: Long): Long {
        val last = lastAttempt ?: return 1_200L
        if (now - last >= 60_000L) return 1_200L
        val interval = (1_200L shl attempts.coerceAtMost(5)).coerceAtMost(30_000L)
        return (interval - (now - last).coerceAtLeast(0)).coerceAtLeast(1_200L)
    }
    @Synchronized fun attempted(now: Long) {
        if (lastAttempt == null || now - lastAttempt!! >= 60_000L) attempts = 0
        attempts = (attempts + 1).coerceAtMost(5)
        lastAttempt = now
    }
}

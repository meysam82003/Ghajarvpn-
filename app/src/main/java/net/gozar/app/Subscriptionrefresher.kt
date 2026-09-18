package net.gozar.app

import java.util.concurrent.atomic.AtomicBoolean


object SubscriptionRefresher {

    // Two independent callers can ask for a refresh around the same moment -
    // Gozarapplication's onActivityStarted (every foreground return) and
    // MainActivity's own 30-minute loop (which also fires immediately on
    // first composition). With no guard both ran their own full sequential
    // sweep over every subscription at once, doubling the network fetches/
    // parsing for that whole pass. A concurrent call is redundant work on
    // the same store.subscriptions.value list, so it's skipped, not queued.
    private val running = AtomicBoolean(false)

    /**
     * When the last completed sweep finished, so a forced refresh can still
     * decline to run twice in quick succession.
     *
     * Entering the app is meant to bring subscriptions up to date, but
     * onActivityStarted fires on every return from the background - bouncing
     * to another app and straight back should not refetch every subscription
     * again. A minute is long enough to stop that and short enough that
     * "opening the app" always means fresh data.
     */
    @Volatile private var lastRunAt = 0L

    const val ENTRY_MIN_INTERVAL_MS = 60_000L

    /** True when this subscription is a network one a refresh can refetch. */
    fun refreshable(sub: Subscription): Boolean =
        sub.url == FreeConfigs.SOURCE_URL ||
            sub.url.startsWith("http://", ignoreCase = true) ||
            sub.url.startsWith("https://", ignoreCase = true)

    /**
     * @param force When true, refresh every subscription regardless of the
     * auto-refresh interval - used on app open so all subs (usage/quota, expiry,
     * server list) are current the moment the user looks at them. When false,
     * only subscriptions older than [ConfigStore.autoRefreshHours] are touched.
     */
    suspend fun refreshStale(
        store: ConfigStore,
        force: Boolean = false,
        minIntervalMs: Long = 0L
    ) {
        val now = System.currentTimeMillis()
        if (minIntervalMs > 0L && lastRunAt != 0L && now - lastRunAt < minIntervalMs) return
        if (!running.compareAndSet(false, true)) return
        try {
            refreshStaleLocked(store, force)
            lastRunAt = System.currentTimeMillis()
        } finally {
            running.set(false)
        }
    }

    private suspend fun refreshStaleLocked(store: ConfigStore, force: Boolean) {
        val targets = if (force) {
            store.subscriptions.value
        } else {
            val hours = store.autoRefreshHours.value
            if (hours <= 0) return
            val cutoff = System.currentTimeMillis() - hours * 3_600_000L
            store.subscriptions.value.filter { it.lastUpdated <= cutoff }
        }

        targets.forEach { sub ->
                try {
                    if (sub.url == FreeConfigs.SOURCE_URL) {
                        FreeConfigs.refresh(store, FreeConfigs.CONFIG_NAME)
                        return@forEach
                    }
                    if (sub.url.isBlank()) return@forEach
                    val result = SubscriptionFetcher.fetchFull(sub.url)
                    if (result.configs.isNotEmpty()) {
                        val info = result.userInfo
                        store.upsertSubscription(
                            sub.copy(
                                used = info?.used ?: sub.used,
                                total = info?.total ?: sub.total,
                                expire = info?.expire ?: sub.expire,
                                lastUpdated = System.currentTimeMillis()
                            ),
                            result.configs
                        )
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                  catch (_: Exception) { /* Preserve an unavailable subscription. */ }
            }
    }
}
package net.gozar.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object SubscriptionRefresher {
    private val refreshMutex = Mutex()

    suspend fun refreshStale(store: ConfigStore, force: Boolean = false) = refreshMutex.withLock {
        // A cold foreground event can arrive before preferences have loaded.
        store.awaitReady()
        val hours = store.autoRefreshHours.value
        if (hours <= 0 && !force) return@withLock
        val cutoff = System.currentTimeMillis() - hours * 3_600_000L

        store.subscriptions.value
            .filter { (force || it.lastUpdated <= cutoff) && (it.url.startsWith("https://") || it.url.startsWith("http://")) }
            .forEach { sub ->
                storeResult {
                    val result = SubscriptionFetcher.fetchFull(sub.url)
                    if (result.configs.isNotEmpty()) {
                        val info = result.userInfo
                        withContext(Dispatchers.Main) {
                            val current = store.subscriptions.value.firstOrNull { it.id == sub.id }
                                ?: return@withContext
                            // Do not resurrect a deleted feed, overwrite a delivery refresh,
                            // or undo a rename performed while the network request was running.
                            if (current.url != sub.url || current.lastUpdated != sub.lastUpdated) return@withContext
                            store.upsertSubscription(
                                current.copy(
                                    used = info?.used ?: current.used,
                                    total = info?.total ?: current.total,
                                    expire = info?.expire ?: current.expire,
                                    lastUpdated = System.currentTimeMillis()
                                ),
                                result.configs
                            )
                        }
                    }
                }
            }
    }
}

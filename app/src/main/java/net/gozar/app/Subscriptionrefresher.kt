package net.gozar.app

object SubscriptionRefresher {

    suspend fun refreshStale(store: ConfigStore, force: Boolean = false) {
        val hours = store.autoRefreshHours.value
        if (hours <= 0 && !force) return
        val cutoff = System.currentTimeMillis() - hours.coerceAtLeast(0) * 3_600_000L

        store.subscriptions.value
            .filter { force || it.lastUpdated <= cutoff }
            .forEach { sub ->
                runCatching {
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
                }
            }
    }
}

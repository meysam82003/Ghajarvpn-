package net.gozar.app

object SubscriptionRefresher {

    data class Summary(val attempted: Int, val updated: Int, val configs: Int, val failed: Int)

    suspend fun refreshStale(store: ConfigStore, force: Boolean = false) {
        val hours = store.autoRefreshHours.value
        if (hours <= 0 && !force) return
        val cutoff = System.currentTimeMillis() - hours * 3_600_000L

        val targets = store.subscriptions.value
            .filter { (force || it.lastUpdated <= cutoff) && (it.url.startsWith("https://") || it.url.startsWith("http://")) }
        refresh(store, targets)
    }

    suspend fun refreshAll(store: ConfigStore): Summary = refresh(
        store,
        store.subscriptions.value.filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
    )

    private suspend fun refresh(store: ConfigStore, targets: List<Subscription>): Summary {
        var updated = 0
        var configs = 0
        var failed = 0
        targets.forEach { sub ->
            if (sub.url == FreeConfigs.SOURCE_URL) {
                val result = FreeConfigs.refresh(store, sub.name)
                if (result >= 0) { updated++; configs += result } else failed++
                return@forEach
            }
            storeResult {
                val result = SubscriptionFetcher.fetchFull(sub.url)
                require(result.configs.isNotEmpty()) { "empty subscription" }
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
                result.configs.size
            }.onSuccess { count -> updated++; configs += count }
                .onFailure { failed++ }
        }
        return Summary(targets.size, updated, configs, failed)
    }
}

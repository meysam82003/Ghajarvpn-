package net.gozar.app

object SubscriptionRefresher {

    /**
     * @param force When true, refresh every subscription regardless of the
     * auto-refresh interval - used on app open so all subs (usage/quota, expiry,
     * server list) are current the moment the user looks at them. When false,
     * only subscriptions older than [ConfigStore.autoRefreshHours] are touched.
     */
    suspend fun refreshStale(store: ConfigStore, force: Boolean = false) {
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
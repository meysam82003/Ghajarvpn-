package net.gozar.app

import android.content.Context
import gozarcore.Gozarcore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AutoSelector(
    private val appContext: Context,
    private val store: ConfigStore,
    private val onSwitch: ((ProxyConfig) -> Unit)? = null
) {

    private var loopJob: Job? = null

    private val _results = MutableStateFlow<Map<String, PingResult>>(emptyMap())
    val results: StateFlow<Map<String, PingResult>> = _results.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (loopJob?.isActive == true) {
            android.util.Log.d(TAG, "start() ignored, already running")
            return
        }
        android.util.Log.d(TAG, "start()")
        loopJob = scope.launch {
            while (isActive) {
                runCatching { runOnce() }
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        android.util.Log.d(TAG, "stop()")
        loopJob?.cancel()
        loopJob = null
    }

    private fun selectable(all: List<ProxyConfig>): List<ProxyConfig> =
        all.filter { it.protocol.trim().lowercase() !in SKIP_PROTOCOLS }

    private suspend fun measureAll(configs: List<ProxyConfig>) = coroutineScope {
        val marking = _results.value.toMutableMap()
        configs.forEach { marking[it.id] = PingResult.Testing }
        _results.value = marking.toMap()

        val sem = Semaphore(MAX_CONCURRENCY)
        configs.map { cfg ->
            launch {
                sem.withPermit {
                    val r = if (cfg.protocol.trim().lowercase() == "ikev2") {
                        Pinger.pingIke(cfg.address)
                    } else {
                        val ms = withContext(Dispatchers.IO) {
                            runCatching { Gozarcore.measureDelay(ConfigBuilder.buildForTest(cfg)) }
                                .getOrDefault(-1L)
                        }
                        if (ms >= 0) PingResult.Ok(ms.toInt()) else PingResult.Failed
                    }
                    _results.value = _results.value.toMutableMap().apply { put(cfg.id, r) }
                }
            }
        }.joinAll()
    }

    suspend fun pickFastest(timeoutMs: Long = 6_000L): ProxyConfig? {
        store.awaitReady()
        val configs = selectable(store.configs.value)
        android.util.Log.d(TAG, "pickFastest: ${configs.size} candidates")
        if (configs.isEmpty()) return null
        if (configs.size == 1) return configs.first()

        withTimeoutOrNull(timeoutMs) { measureAll(configs) }

        val snap = _results.value
        val best = configs
            .mapNotNull { c -> (snap[c.id] as? PingResult.Ok)?.let { c to it.ms } }
            .minByOrNull { it.second }
        android.util.Log.d(TAG, "pickFastest: ${best?.first?.name} at ${best?.second}ms")
        return best?.first
    }

    private suspend fun runOnce() = coroutineScope {
        store.awaitReady()
        val configs = selectable(store.configs.value)
        android.util.Log.d(TAG, "runOnce: ${configs.size} configs")
        if (configs.isEmpty()) return@coroutineScope

        measureAll(configs)

        val snapshot = _results.value
        val best = configs
            .mapNotNull { c -> (snapshot[c.id] as? PingResult.Ok)?.let { c to it.ms } }
            .minByOrNull { it.second }
        if (best == null) {
            android.util.Log.w(TAG, "no config responded, nothing to switch to")
            return@coroutineScope
        }
        android.util.Log.d(TAG, "best=${best.first.name} ${best.second}ms")

        val selectedId = store.selectedId.value
        if (selectedId == null) {
            store.setSelectedId(best.first.id)
            return@coroutineScope
        }
        if (best.first.id == selectedId) {
            android.util.Log.d(TAG, "best is already selected")
            return@coroutineScope
        }

        val currentPing = (snapshot[selectedId] as? PingResult.Ok)?.ms
        val shouldSwitch = currentPing == null || currentPing - best.second >= SWITCH_MARGIN_MS
        if (!shouldSwitch) {
            android.util.Log.d(TAG, "margin too small: current=${currentPing}ms best=${best.second}ms")
            return@coroutineScope
        }

        android.util.Log.d(TAG, "switching to ${best.first.name}")
        store.setSelectedId(best.first.id)
        val handler = onSwitch
        if (handler != null) {
            withContext(Dispatchers.Main) { handler(best.first) }
        } else {
            reconnectIfConnected(best.first)
        }
    }

    /**
     * The launch itself lives in VpnLauncher, which the per-network rules use
     * too - one connect path outside the UI, not two that drift apart.
     */
    private suspend fun reconnectIfConnected(config: ProxyConfig) {
        if (VpnState.state.value != Connection.CONNECTED) return
        VpnLauncher.relaunch(appContext, store, config)
    }

    private companion object {
        val SKIP_PROTOCOLS = setOf("tor", "aether")
        const val TAG = "GhajarAuto"
        const val INTERVAL_MS = 60_000L
        const val MAX_CONCURRENCY = 4
        const val SWITCH_MARGIN_MS = 40
    }
}
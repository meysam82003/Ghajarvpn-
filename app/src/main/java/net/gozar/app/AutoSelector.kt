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
    private val scores = net.gozar.app.engine.AutoSelectScore()

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

    private fun selectable(all: List<ProxyConfig>): List<ProxyConfig> = interchangeable(all)

    private suspend fun measureAll(configs: List<ProxyConfig>) = coroutineScope {
        val marking = _results.value.toMutableMap()
        configs.forEach { marking[it.id] = PingResult.Testing }
        _results.value = marking.toMap()

        scores.network = NetworkWatcher.kind.value?.name.orEmpty()
        val sem = Semaphore(MAX_CONCURRENCY)
        configs.map { cfg ->
            launch {
                sem.withPermit {
                    val r = if (cfg.protocol.trim().lowercase() == "ikev2") {
                        Pinger.pingIke(cfg.address)
                    } else {
                        val ms = withContext(Dispatchers.IO) {
                            runCatching { net.gozar.app.engine.EngineTester.realDelay(cfg) }
                                .getOrDefault(-1L)
                        }
                        if (ms >= 0) PingResult.Ok(ms.toInt()) else PingResult.Failed
                    }
                    if (r !is PingResult.Testing) {
                        // The engine test recorded loss, jitter and handshake time
                        // alongside the delay; use all of it, not just the number.
                        val full = net.gozar.app.engine.EngineTestStore.get(cfg.id)
                            ?.takeIf { System.currentTimeMillis() - it.testedAt < 30_000 }
                        scores.record(cfg.id, net.gozar.app.engine.AutoSelectScore.Sample(
                            ok = r is PingResult.Ok, latencyMs = (r as? PingResult.Ok)?.ms,
                            jitterMs = full?.jitterMs, lossPct = full?.lossPct, handshakeMs = full?.handshakeMs))
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
        scores.forget(configs.map { it.id }.toSet())

        // Ranked by recent history (median, jitter, failures), not one sample.
        val ranked = scores.best(configs.map { it.id })
        val best = ranked?.let { r -> configs.firstOrNull { it.id == r.first }?.let { it to r.second.toInt() } }
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

        val shouldSwitch = scores.shouldSwitch(selectedId, best.first.id)
        if (!shouldSwitch) {
            android.util.Log.d(TAG, "margin too small: current score=${scores.score(selectedId)} best=${best.second}")
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

    companion object {
        /**
         * The servers that can stand in for one another.
         *
         * Tor and Aether are engines rather than endpoints - swapping one for
         * the other is not "a faster server", it is a different product. This
         * is public because the autopilot card ranks the same set, and two
         * definitions of "candidate" is how a screen ends up promising to
         * measure something the selector will never pick.
         */
        fun interchangeable(all: List<ProxyConfig>): List<ProxyConfig> =
            all.filter { it.protocol.trim().lowercase() !in SKIP_PROTOCOLS }

        private val SKIP_PROTOCOLS = setOf("tor", "aether") + net.gozar.app.engine.RemovedCores.PROTOCOLS
        private const val TAG = "GhajarAuto"
        private const val INTERVAL_MS = 60_000L
        private const val MAX_CONCURRENCY = 4
    }
}
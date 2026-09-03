package net.gozar.app

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import gozarcore.Gozarcore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val health = ServerHealthRepository(appContext)
    private val preferences = SmartConnectPreferences(appContext)
    private val _scores = MutableStateFlow<Map<String, Double>>(emptyMap())
    val scores: StateFlow<Map<String, Double>> = _scores.asStateFlow()

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
                    val samples = List(PROBE_SAMPLES) {
                        if (cfg.protocol.trim().lowercase() == "ikev2") {
                            (Pinger.pingIke(cfg.address) as? PingResult.Ok)?.ms?.toLong()
                        } else withContext(Dispatchers.IO) {
                            runCatching { Gozarcore.measureDelay(ConfigBuilder.buildForTest(cfg)) }.getOrNull()?.takeIf { it >= 0 }
                        }
                    }
                    health.recordProbe(cfg.id, samples)
                    val valid = samples.filterNotNull()
                    val r = if (valid.isNotEmpty()) PingResult.Ok(valid.average().toInt()) else PingResult.Failed
                    _results.value = _results.value.toMutableMap().apply { put(cfg.id, r) }
                }
            }
        }.joinAll()
    }

    suspend fun pickFastest(timeoutMs: Long = 15_000L): ProxyConfig? {
        store.awaitReady()
        val configs = selectable(store.configs.value)
        android.util.Log.d(TAG, "pickFastest: ${configs.size} candidates")
        if (configs.isEmpty()) return null
        if (configs.size == 1) return configs.first()

        withTimeoutOrNull(timeoutMs) { measureAll(configs) }

        if (preferences.mode != SmartMode.EMERGENCY && _results.value.values.none { it is PingResult.Ok }) return null

        val ranked = rank(configs)
        val best = ranked.firstOrNull()?.let { item -> configs.firstOrNull { it.id == item.candidate.id } }
        android.util.Log.d(TAG, "smart pick: ${best?.name} mode=${preferences.mode}")
        return best
    }

    private suspend fun runOnce() = coroutineScope {
        store.awaitReady()
        val configs = selectable(store.configs.value)
        android.util.Log.d(TAG, "runOnce: ${configs.size} configs")
        if (configs.isEmpty()) return@coroutineScope

        measureAll(configs)

        val snapshot = _results.value
        if (preferences.mode != SmartMode.EMERGENCY && snapshot.values.none { it is PingResult.Ok }) return@coroutineScope
        val ranked = rank(configs)
        val best = ranked.firstOrNull()?.let { item -> configs.firstOrNull { it.id == item.candidate.id } }?.let { config ->
            config to ((snapshot[config.id] as? PingResult.Ok)?.ms ?: Int.MAX_VALUE)
        }
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

        val bestScore = _scores.value[best.first.id] ?: return@coroutineScope
        val currentScore = _scores.value[selectedId]
        val shouldSwitch = currentScore == null || bestScore - currentScore >= SWITCH_SCORE_MARGIN
        if (!shouldSwitch) {
            android.util.Log.d(TAG, "smart score margin too small")
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

    private fun rank(configs: List<ProxyConfig>): List<RankedServer> {
        val states = health.snapshot(configs.map { it.id })
        val ranked = SmartConnectScorer.rank(configs.map { ServerCandidate(it.id) }, states, preferences.mode)
        _scores.value = ranked.associate { it.candidate.id to it.score }
        return ranked
    }

    private suspend fun reconnectIfConnected(config: ProxyConfig) {
        if (VpnState.state.value != Connection.CONNECTED) return
        if (VpnService.prepare(appContext) != null) return

        runCatching {
            appContext.startService(
                Intent(appContext, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_STOP)
            )
        }
        withTimeoutOrNull(6000) {
            VpnState.state.first { it == Connection.DISCONNECTED || it == Connection.ERROR }
        }
        delay(400)

        val json = ConfigBuilder.build(
            config, store.fragment.value, store.splitRouting.value,
            store.sniffing.value, store.sniffTypes.value,
            adBlock = store.adBlock.value,
            fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            onionRouting = store.onionRouting.value
        )
        VpnState.setConnecting(config.id)
        val intent = Intent(appContext, GozarVpnService::class.java)
            .putExtra(GozarVpnService.EXTRA_CONFIG, json)
            .putExtra(GozarVpnService.EXTRA_AETHER, AetherSpec.from(config)?.toJson())
            .putExtra(
                GozarVpnService.EXTRA_TOR,
                if (config.protocol == "tor")
                    config.torCountry + "|" + (if (config.torThroughVpn) "1" else "0") else null
            )
            .putExtra(GozarVpnService.EXTRA_NAME, config.name)
            .putExtra(GozarVpnService.EXTRA_STOP_LABEL, Strings.get(store.lang.value, "disconnect"))
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
            .onFailure { VpnState.setDisconnected() }
    }

    private companion object {
        val SKIP_PROTOCOLS = setOf("tor", "aether")
        const val TAG = "GhajarAuto"
        const val INTERVAL_MS = 60_000L
        const val MAX_CONCURRENCY = 4
        const val PROBE_SAMPLES = 3
        const val SWITCH_SCORE_MARGIN = 0.08
    }
}

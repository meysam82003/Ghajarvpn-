package net.gozar.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the autopilot card is doing right now.
 */
sealed interface AutoPilotPhase {

    /** Not engaged. The user's own choice of server is in force. */
    data object Off : AutoPilotPhase

    /** Measuring. [done] of [total] have answered or timed out. */
    data class Measuring(val done: Int, val total: Int) : AutoPilotPhase

    /** Measured, best picked, connecting to it. */
    data class Connecting(val name: String, val ms: Int) : AutoPilotPhase

    /** Connected through a server the autopilot chose. */
    data class Engaged(val name: String, val ms: Int) : AutoPilotPhase

    /**
     * Could not engage, with the reason as a string key.
     *
     * A key rather than a sentence so both languages stay in Strings.kt, and a
     * reason rather than a bare failure because "nothing answered" and "you
     * have no servers" need different things from the user.
     */
    data class Failed(val reasonKey: String) : AutoPilotPhase
}

/**
 * One tap: measure every server you have, connect to the best one, and keep
 * moving off it when it stops working.
 *
 * This is what the server list was missing. Thirty configs and no idea which
 * works today is the normal state of this app's use, and the honest answer to
 * "which one" is a measurement - so the card measures before it connects
 * rather than taking the top of the list and hoping.
 *
 * The staying-connected half is not new code, deliberately.
 * [ConfigStore.autoSelect] already drives an AutoSelector inside
 * GozarVpnService that re-measures on a timer and switches when the current
 * server is beaten by a real margin. Engaging turns that on; disengaging turns
 * it off. A second failover loop here would mean two of them fighting over one
 * tunnel.
 */
object ServerAutoPilot {

    private const val TAG = "GhajarAutoPilot"

    /** The whole measuring pass, after which it picks from whatever answered. */
    private const val MEASURE_TIMEOUT_MS = 12_000L

    private val _phase = MutableStateFlow<AutoPilotPhase>(AutoPilotPhase.Off)
    val phase: StateFlow<AutoPilotPhase> = _phase.asStateFlow()

    /**
     * The last measuring pass, per config id.
     *
     * Published so the list can show the numbers the choice was made on,
     * instead of a spinner that ends in an unexplained pick. The picker keeps
     * its own ping map and this does not overwrite it.
     */
    private val _measured = MutableStateFlow<Map<String, PingResult>>(emptyMap())
    val measured: StateFlow<Map<String, PingResult>> = _measured.asStateFlow()

    /**
     * Measures everything, connects to the fastest, and turns failover on.
     *
     * [connect] is the caller's own connect path - the picker hands over the
     * same one its rows use - so a tap here goes through the VPN permission
     * prompt and the state machine exactly as a tap on a server does, rather
     * than through a second connect path that drifts from it.
     */
    suspend fun engage(
        context: Context,
        store: ConfigStore,
        connect: suspend (ProxyConfig) -> Unit
    ) {
        val app = context.applicationContext
        store.awaitReady()
        val candidates = AutoSelector.interchangeable(store.configs.value)
        if (candidates.isEmpty()) {
            _phase.value = AutoPilotPhase.Failed("autopilot_no_servers")
            return
        }

        _phase.value = AutoPilotPhase.Measuring(0, candidates.size)
        val selector = AutoSelector(app, store)
        val ids = candidates.map { it.id }.toSet()

        val best = coroutineScope {
            // Progress is read from the selector's own results flow rather than
            // a counter kept here: it is the map the measurement writes into,
            // so the number on screen cannot drift from what finished. The job
            // is cancelled before the connect, or it would keep overwriting
            // the phase that connect sets.
            val progress = trackProgress(this, selector, ids, candidates.size)
            val picked = selector.pickFastest(timeoutMs = MEASURE_TIMEOUT_MS)
            progress.cancel()
            picked
        }
        _measured.value = selector.results.value.filterKeys { it in ids }

        if (best == null) {
            _phase.value = AutoPilotPhase.Failed("autopilot_none_answered")
            return
        }
        val ms = (_measured.value[best.id] as? PingResult.Ok)?.ms ?: 0
        GhajarLog.i(TAG, "engaging on ${best.name} at ${ms}ms of ${candidates.size} candidates")

        // Order matters. The selection is stored before the connect, so a
        // restart mid-connect comes back to the server the autopilot chose;
        // and autoSelect goes on before the tunnel exists, so the service
        // picks it up as it starts rather than on its next tick.
        store.setSelectedId(best.id)
        store.setAutoSelect(true)
        store.setAutoPilot(true)
        _phase.value = AutoPilotPhase.Connecting(best.name, ms)
        connect(best)
    }

    /**
     * Hands control back to the user.
     *
     * Turns the failover loop off too: leaving it running would move the tunnel
     * off the server the user just picked by hand, and surviving a reconnect is
     * the whole point of picking by hand.
     */
    fun disengage(store: ConfigStore) {
        store.setAutoPilot(false)
        store.setAutoSelect(false)
        _phase.value = AutoPilotPhase.Off
        _measured.value = emptyMap()
    }

    /**
     * Reflects a tunnel that came up while engaged.
     *
     * Called from the picker as the connection state changes, so the card says
     * "connected through X" instead of sitting on "connecting" until the screen
     * is reopened.
     */
    fun onConnected(name: String) {
        val current = _phase.value
        if (current is AutoPilotPhase.Connecting) {
            _phase.value = AutoPilotPhase.Engaged(name, current.ms)
        }
    }

    /** Clears a failure once it has been read, returning the card to rest. */
    fun clearFailure() {
        if (_phase.value is AutoPilotPhase.Failed) _phase.value = AutoPilotPhase.Off
    }

    /**
     * Restores the card on a cold open.
     *
     * The flag is persisted; the phase is not. So after a restart the app knows
     * it is meant to be flying the tunnel, and reports engaged only when a
     * tunnel is genuinely up - claiming otherwise would be the card lying about
     * the connection, which is the one thing it must never do.
     */
    fun restore(store: ConfigStore, connectedName: String?) {
        if (!store.autoPilot.value) return
        _phase.value = if (connectedName == null) AutoPilotPhase.Off else {
            val ms = _measured.value.values.filterIsInstance<PingResult.Ok>()
                .minByOrNull { it.ms }?.ms ?: 0
            AutoPilotPhase.Engaged(connectedName, ms)
        }
    }

    private fun trackProgress(
        scope: CoroutineScope,
        selector: AutoSelector,
        ids: Set<String>,
        total: Int
    ): Job = scope.launch {
        selector.results.collect { map ->
            val done = ids.count { id ->
                val r = map[id]
                r is PingResult.Ok || r is PingResult.Failed
            }
            if (_phase.value is AutoPilotPhase.Measuring) {
                _phase.value = AutoPilotPhase.Measuring(done, total)
            }
        }
    }
}

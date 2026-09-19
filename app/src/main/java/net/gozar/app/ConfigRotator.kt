package net.gozar.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Moves a live tunnel between the servers you already have, on a timer.
 *
 * A single endpoint held open for hours is the easiest thing on a network to
 * notice and to throttle. Rotating between servers spreads that out, which is
 * what MahsaNG calls rotating configs.
 *
 * Deliberately narrow about what it will touch:
 *
 * - Off unless [ConfigStore.rotateMinutes] is set. Zero changes nothing.
 * - It only moves between servers that are already in the list. It never adds,
 *   removes, reorders or edits one.
 * - It only acts on a CONNECTED tunnel. It will not start a tunnel the user
 *   did not ask for, and it will not interrupt one that is still connecting.
 * - OpenVPN and IKEv2 sessions are left alone: they are separate engines with
 *   their own reconnect behaviour, and rotating them would mean tearing down a
 *   tunnel this class does not own.
 * - The built-in engines are skipped, for the same reason the auto-selector
 *   skips them - Tor and Aether are not interchangeable endpoints.
 *
 * Round-robin from the current server rather than random, so every server gets
 * used and the sequence is predictable when reading a log.
 */
object ConfigRotator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var appContext: Context? = null

    /** Engines that are not interchangeable endpoints. */
    private val SKIP = setOf("tor", "aether", "psiphon", "ikev2")

    fun initialize(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        job = scope.launch { loop(app) }
    }

    private suspend fun loop(app: Context) {
        val store = ConfigStore.get(app)
        store.awaitReady()
        // Re-read the interval every tick rather than caching it, so changing
        // the setting takes effect without a restart - and so turning it off
        // takes effect immediately.
        while (scope.isActive) {
            val minutes = store.rotateMinutes.value
            if (minutes <= 0) {
                delay(CHECK_MS)
                continue
            }
            delay(minutes * 60_000L)
            if (store.rotateMinutes.value <= 0) continue
            runCatching { rotateOnce(app, store) }
                .onFailure { GhajarLog.e(TAG, "rotation failed: ${it.javaClass.simpleName}") }
        }
    }

    private suspend fun rotateOnce(app: Context, store: ConfigStore) {
        if (VpnState.state.value != Connection.CONNECTED) return
        val activeId = VpnState.activeId.value ?: return
        // Not ours to rotate: OpenVPN's id carries its own prefix, and an
        // IKEv2 session is held by IkeController.
        if (activeId.startsWith("ovpn:") || IkeController.active) return

        val candidates = store.configs.value.filter { it.protocol.trim().lowercase() !in SKIP }
        if (candidates.size < 2) return
        val current = candidates.indexOfFirst { it.id == activeId }
        if (current < 0) return
        val next = candidates[(current + 1) % candidates.size]
        if (next.id == activeId) return

        GhajarLog.i(TAG, "rotating after ${store.rotateMinutes.value}m")
        store.setSelectedId(next.id)
        // The same launcher the auto-selector and the network rules use, so
        // there is one way a tunnel is replaced outside the UI.
        VpnLauncher.relaunch(app, store, next)
    }

    private const val TAG = "GhajarRotate"

    /** How often to look again while rotation is switched off. */
    private const val CHECK_MS = 60_000L
}

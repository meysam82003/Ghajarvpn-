package net.gozar.app

import android.content.Context
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Applies the per-network rules when the device changes network.
 *
 * What it does and does not do, precisely, because an auto-connect that is
 * vague about this is worse than none:
 *
 * - It never disconnects. OFF for a network means "do not bring a tunnel up
 *   here", not "tear the user's tunnel down".
 * - It never touches an OpenVPN session. ics-openvpn has its own
 *   reconnect-on-network-change setting (on by default, and exposed in the
 *   OpenVPN settings screen); racing it would only produce two reconnects.
 * - It cannot always start a tunnel while the app is in the background.
 *   Android 12+ refuses a foreground-service start from the background and a
 *   network change is not one of the exemptions. When that happens the refusal
 *   is recorded with its real exception class rather than reported as success.
 */
object NetworkAutoConnect {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appContext: Context? = null
    private var job: Job? = null
    private var lastKind: NetKind? = null

    fun initialize(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        NetworkWatcher.initialize(app)
        lastKind = NetworkWatcher.kind.value
        NetworkWatcher.addListener(::onNetwork)
    }

    private fun onNetwork(kind: NetKind?, network: Network?) {
        val app = appContext ?: return
        val previous = lastKind
        lastKind = kind
        // Losing the network entirely is not something to act on: there is no
        // route to build a tunnel over. The next onNetwork with a kind is.
        if (kind == null) return
        if (kind == previous) return

        val rules = NetworkRules.read(app)
        if (rules.idle) return

        // OpenVPN handles its own network changes; see the class comment.
        if (VpnState.activeId.value?.startsWith("ovpn:") == true) return

        val state = VpnState.state.value
        val live = state == Connection.CONNECTED || state == Connection.CONNECTING
        val action = rules.actionFor(kind)

        val decision = when {
            // The tunnel was built over a network that is no longer the one the
            // device uses. Rebuild it - with the new network's own rule
            // deciding which server, since "suitable for this network" is the
            // whole point of having rules per network.
            live && rules.recoverOnChange ->
                if (action == NetRuleAction.OFF) NetRuleAction.LAST else action
            live -> return
            action != NetRuleAction.OFF -> action
            else -> return
        }

        GhajarLog.i(TAG, "network became $kind, applying $decision (was $previous, live=$live)")
        job?.cancel()
        job = scope.launch { apply(app, decision, live) }
    }

    private suspend fun apply(app: Context, action: NetRuleAction, wasLive: Boolean) {
        // A network change arrives before the new network can carry a
        // handshake; measuring a server one millisecond after the switch just
        // measures the gap.
        delay(SETTLE_MS)

        val store = ConfigStore.get(app)
        store.awaitReady()

        val config = when (action) {
            NetRuleAction.FASTEST -> AutoSelector(app, store).pickFastest()
            else -> {
                val id = VpnState.activeId.value ?: store.selectedId.value
                store.configs.value.firstOrNull { it.id == id }
            }
        } ?: run {
            GhajarLog.i(TAG, "no server to use for $action")
            return
        }

        when (VpnLauncher.relaunch(app, store, config)) {
            LaunchOutcome.STARTED ->
                GhajarLog.i(TAG, "started ${if (wasLive) "recovery" else "auto-connect"}")
            LaunchOutcome.NO_PERMISSION ->
                GhajarLog.i(TAG, "no VPN permission, the user has to connect once by hand")
            LaunchOutcome.REFUSED ->
                GhajarLog.i(TAG, "the system refused a background start; nothing was connected")
        }
    }

    private const val TAG = "GhajarNetRules"
    private const val SETTLE_MS = 1_200L
}

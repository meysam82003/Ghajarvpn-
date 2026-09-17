package net.gozar.app

internal enum class ConnectAction { CONNECT, SWITCH, IGNORE }

/**
 * Pure routing decision for "the user asked to connect to server X" - used
 * by MainActivity.connectTo() so the actual bug (play-button connects never
 * updated store.selectedId, so the home screen's main button reconnected to
 * a stale, different server after a disconnect) has a decision path that
 * can be unit tested without a Context/Activity.
 *
 * store.setSelectedId(requestedId) is applied by the caller unconditionally
 * before consulting this - selecting a server is never in question here,
 * only whether/how to act on the tunnel.
 */
internal object ConnectDecision {
    fun resolve(state: Connection, activeId: String?, requestedId: String): ConnectAction = when {
        state == Connection.DISCONNECTING -> ConnectAction.IGNORE
        state == Connection.CONNECTED || state == Connection.CONNECTING ->
            if (activeId == requestedId) ConnectAction.IGNORE else ConnectAction.SWITCH
        else -> ConnectAction.CONNECT
    }
}

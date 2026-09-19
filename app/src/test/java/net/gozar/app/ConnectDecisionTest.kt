package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down the fix for the reported bug: tapping the play button next to a
 * server in the picker connected to it, but never updated store.selectedId
 * (only tapping the row body did) - so the home screen's main button, which
 * always reconnects to store.selectedId, would reconnect to the *previous*
 * selection after a disconnect instead of the server the user actually
 * played. MainActivity.connectTo() now always calls store.setSelectedId()
 * first and then asks ConnectDecision what to do with the tunnel itself;
 * these tests cover that routing decision.
 */
class ConnectDecisionTest {
    @Test fun disconnectedStateAlwaysConnectsDirectly() {
        assertEquals(ConnectAction.CONNECT, ConnectDecision.resolve(Connection.DISCONNECTED, null, "A"))
        assertEquals(ConnectAction.CONNECT, ConnectDecision.resolve(Connection.DISCONNECTED, "A", "B"))
    }

    @Test fun errorStateAlwaysConnectsDirectly() {
        assertEquals(ConnectAction.CONNECT, ConnectDecision.resolve(Connection.ERROR, "A", "A"))
        assertEquals(ConnectAction.CONNECT, ConnectDecision.resolve(Connection.ERROR, "A", "B"))
    }

    @Test fun disconnectingStateIsAlwaysIgnored() {
        assertEquals(ConnectAction.IGNORE, ConnectDecision.resolve(Connection.DISCONNECTING, "A", "A"))
        assertEquals(ConnectAction.IGNORE, ConnectDecision.resolve(Connection.DISCONNECTING, "A", "B"))
        assertEquals(ConnectAction.IGNORE, ConnectDecision.resolve(Connection.DISCONNECTING, null, "A"))
    }

    @Test fun connectedToTheSameServerIsIgnored_neverRestartsTheTunnel() {
        assertEquals(ConnectAction.IGNORE, ConnectDecision.resolve(Connection.CONNECTED, "A", "A"))
        assertEquals(ConnectAction.IGNORE, ConnectDecision.resolve(Connection.CONNECTING, "A", "A"))
    }

    @Test fun connectedToADifferentServerAlwaysSwitches() {
        assertEquals(ConnectAction.SWITCH, ConnectDecision.resolve(Connection.CONNECTED, "A", "B"))
        assertEquals(ConnectAction.SWITCH, ConnectDecision.resolve(Connection.CONNECTING, "A", "B"))
    }

    @Test fun connectingWithNoActiveIdYetStillSwitchesRatherThanSilentlyIgnoring() {
        // CONNECTING with a null activeId (engine hasn't confirmed which
        // profile yet) must not be treated as "already on this server" -
        // that would silently swallow the tap.
        assertEquals(ConnectAction.SWITCH, ConnectDecision.resolve(Connection.CONNECTING, null, "A"))
    }
}

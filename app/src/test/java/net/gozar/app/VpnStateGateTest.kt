package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VpnStateGateTest {
    private var now = 1_000_000L

    @Before
    fun setUp() {
        VpnState.resetForTests()
        VpnState.clock = { now }
    }

    @Test fun rapidDoubleConnectIsDebounced() {
        VpnState.setConnecting("a")
        now += 100
        VpnState.setConnecting("b")
        assertEquals(Connection.CONNECTING, VpnState.state.value)
        assertEquals("a", VpnState.activeId.value)
    }

    @Test fun connectAfterDebounceWindowSwitchesTarget() {
        VpnState.setConnecting("a")
        now += VpnState.TRANSITION_DEBOUNCE_MS + 1
        VpnState.setConnecting("b")
        assertEquals(Connection.CONNECTING, VpnState.state.value)
        assertEquals("b", VpnState.activeId.value)
    }

    @Test fun reconnectRightAfterDisconnectIsLocked() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setDisconnected()
        now += 100
        VpnState.setConnecting("b")
        assertEquals(Connection.DISCONNECTED, VpnState.state.value)
        now += VpnState.RECONNECT_LOCK_MS
        VpnState.setConnecting("b")
        assertEquals(Connection.CONNECTING, VpnState.state.value)
        assertEquals("b", VpnState.activeId.value)
    }

    @Test fun staleConnectedEventCannotReviveDisconnectedState() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setDisconnected()
        VpnState.setConnected()
        assertEquals(Connection.DISCONNECTED, VpnState.state.value)
        assertEquals(0L, VpnState.connectedAt.value)
    }

    @Test fun fastConnectedConfirmationLandsImmediately() {
        // A tunnel that confirms within the transition debounce window must
        // still flip the UI to CONNECTED — the old debounce silently dropped
        // the confirmation and left the app on "connecting" forever.
        VpnState.setConnecting("a")
        now += 50
        VpnState.setConnected()
        assertEquals(Connection.CONNECTED, VpnState.state.value)
        assertTrue(VpnState.connectedAt.value > 0)
    }

    @Test fun staleErrorCannotOverwriteConnectedState() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setConnected()
        VpnState.setError("نباید ثبت شود")
        assertEquals(Connection.CONNECTED, VpnState.state.value)
        assertEquals(null, VpnState.error.value)
    }

    @Test fun connectedOnlyTransitionsFromConnecting() {
        VpnState.setConnected()
        assertEquals(Connection.DISCONNECTED, VpnState.state.value)
        VpnState.setConnecting("a")
        now += VpnState.TRANSITION_DEBOUNCE_MS + 1
        VpnState.setConnected()
        assertEquals(Connection.CONNECTED, VpnState.state.value)
        assertTrue(VpnState.connectedAt.value > 0L)
    }

    @Test fun lookupGenerationBumpsOnConnectAndDisconnect() {
        val initial = VpnState.lookupGeneration.value
        VpnState.setConnecting("a")
        val afterConnect = VpnState.lookupGeneration.value
        assertEquals(initial + 1, afterConnect)
        now += 1_000
        VpnState.setDisconnected()
        assertEquals(afterConnect + 1, VpnState.lookupGeneration.value)
    }

    @Test fun repeatedDisconnectIsIdempotentAndDoesNotBumpGeneration() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setDisconnected()
        val generation = VpnState.lookupGeneration.value
        VpnState.setDisconnected()
        assertEquals(generation, VpnState.lookupGeneration.value)
        assertEquals(Connection.DISCONNECTED, VpnState.state.value)
    }

    @Test fun connectingAgainToSameIdWhileConnectedIsNoOp() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setConnected()
        val generation = VpnState.lookupGeneration.value
        now += 1_000
        VpnState.setConnecting("a")
        assertEquals(Connection.CONNECTED, VpnState.state.value)
        assertEquals("a", VpnState.activeId.value)
        assertEquals(generation, VpnState.lookupGeneration.value)
    }

    @Test fun errorStateAllowsImmediateReconnectAttempt() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setError("شبکه در دسترس نیست")
        assertEquals(Connection.ERROR, VpnState.state.value)
        VpnState.setConnecting("b")
        assertEquals(Connection.CONNECTING, VpnState.state.value)
        assertNotEquals("شبکه در دسترس نیست", VpnState.error.value)
    }

    @Test fun beginDisconnectingShowsDisconnectingAndServiceConfirmClearsIt() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setConnected()
        VpnState.beginDisconnecting()
        assertEquals(Connection.DISCONNECTING, VpnState.state.value)
        VpnState.setDisconnected()
        assertEquals(Connection.DISCONNECTED, VpnState.state.value)
    }

    @Test fun connectIsBlockedWhileDisconnecting() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setConnected()
        now += 1_000
        VpnState.beginDisconnecting()
        now += 2_000
        VpnState.setConnecting("b")
        assertEquals(Connection.DISCONNECTING, VpnState.state.value)
    }

    @Test fun setConnectedCannotLandDuringDisconnecting() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.beginDisconnecting()
        VpnState.setConnected()
        assertEquals(Connection.DISCONNECTING, VpnState.state.value)
    }

    @Test fun repeatedDisconnectingIsIdempotent() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setConnected()
        VpnState.beginDisconnecting()
        now += 500
        VpnState.beginDisconnecting()
        assertEquals(Connection.DISCONNECTING, VpnState.state.value)
        VpnState.setDisconnected()
        assertEquals(Connection.DISCONNECTED, VpnState.state.value)
    }

    @Test fun disconnectingNeverPersistsAcrossProcessRestart() {
        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setConnected()
        VpnState.beginDisconnecting()
        // simulate the next process reading the persisted state
        VpnState.resetForTests()
        assertEquals(Connection.DISCONNECTED, VpnState.state.value)
    }

    @Test fun disconnectFromConnectingOrErrorAlsoEntersDisconnecting() {
        VpnState.setConnecting("a")
        VpnState.beginDisconnecting()
        assertEquals(Connection.DISCONNECTING, VpnState.state.value)
        VpnState.setDisconnected()

        VpnState.setConnecting("a")
        now += 1_000
        VpnState.setError("شبکه در دسترس نیست")
        VpnState.beginDisconnecting()
        assertEquals(Connection.DISCONNECTING, VpnState.state.value)
        VpnState.setDisconnected()
        assertEquals(Connection.DISCONNECTED, VpnState.state.value)
    }
}

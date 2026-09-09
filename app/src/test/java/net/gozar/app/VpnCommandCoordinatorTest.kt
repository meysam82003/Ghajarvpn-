package net.gozar.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnCommandCoordinatorTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        VpnCommandCoordinator.resetForTest()
        VpnCommandCoordinator.logger = { }
        VpnState.setDisconnected()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `disconnect during connecting cancels and reconciles disconnected`() = runTest {
        VpnCommandCoordinator.onConnectRequested("c1") { }
        advanceTimeBy(50)
        assertEquals(Connection.CONNECTING, VpnState.state.value)
        VpnCommandCoordinator.onDisconnectRequested { }
        advanceTimeBy(50)
        assertEquals(Connection.DISCONNECTING, VpnState.state.value)
        VpnCommandCoordinator.onTunnelTeardown()
        assertEquals(Connection.DISCONNECTED, VpnState.state.value)
    }

    @Test
    fun `stale connected broadcast after disconnect is rejected`() = runTest {
        VpnCommandCoordinator.onConnectRequested("c1") { }
        advanceTimeBy(50)
        VpnCommandCoordinator.onDisconnectRequested { }
        advanceTimeBy(50)
        assertFalse(VpnCommandCoordinator.acceptBroadcast(Connection.CONNECTED))
        assertTrue(VpnCommandCoordinator.acceptBroadcast(Connection.DISCONNECTED))
    }

    @Test
    fun `stale disconnected broadcast during connect attempt is rejected`() = runTest {
        VpnCommandCoordinator.onConnectRequested("c1") { }
        advanceTimeBy(50)
        assertFalse(VpnCommandCoordinator.acceptBroadcast(Connection.DISCONNECTED))
        assertTrue(VpnCommandCoordinator.acceptBroadcast(Connection.CONNECTED))
    }

    @Test
    fun `connect watchdog errors out instead of hanging forever`() = runTest {
        VpnCommandCoordinator.onConnectRequested("c1") { }
        advanceTimeBy(50)
        assertEquals(Connection.CONNECTING, VpnState.state.value)
        advanceTimeBy(46_000)
        assertEquals(Connection.ERROR, VpnState.state.value)
    }

    @Test
    fun `disconnect watchdog reconciles disconnected`() = runTest {
        VpnState.setConnected()
        VpnCommandCoordinator.onDisconnectRequested { }
        advanceTimeBy(50)
        assertEquals(Connection.DISCONNECTING, VpnState.state.value)
        advanceTimeBy(11_000)
        assertEquals(Connection.DISCONNECTED, VpnState.state.value)
    }

    @Test
    fun `rapid intents last one wins`() = runTest {
        repeat(20) { index ->
            if (index % 2 == 0) VpnCommandCoordinator.onConnectRequested("c$index") { }
            else VpnCommandCoordinator.onDisconnectRequested { }
            advanceTimeBy(10)
        }
        VpnCommandCoordinator.onConnectRequested("final") { }
        advanceTimeBy(50)
        assertEquals(Connection.CONNECTING, VpnState.state.value)
        assertTrue(VpnCommandCoordinator.acceptBroadcast(Connection.CONNECTED))
    }
}

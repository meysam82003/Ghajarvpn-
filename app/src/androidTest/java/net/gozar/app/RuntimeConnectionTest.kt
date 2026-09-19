package net.gozar.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/** Opt-in real tunnel test: supply realProxyHost/realProxyPort and Android VPN consent.
 * The supplied SOCKS endpoint must forward real traffic; no responses are simulated. */
@RunWith(AndroidJUnit4::class)
class RuntimeConnectionTest {
    @Test fun storeReachesRealBackendAndPersistsLinkSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("realBackend") == "true")
        val api = GhajarStoreApi(instrumentation.targetContext)
        assumeTrue("Use a clean test account state", !api.isLinked && api.pendingLink() == null)
        try {
            val session = api.beginLink()
            assertTrue("Backend did not issue a link code", session.code.isNotBlank())
            assertTrue("Backend did not issue a session", session.sessionToken.isNotBlank())
            assertTrue("Real session was not persisted", api.pendingLink() != null)
        } finally { api.clearPendingLink() }
    }

    @Test fun connectTransferDisconnectReconnectUsesExplicitServer() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("realProxyHost")
        assumeTrue("A real reachable SOCKS test endpoint is required", !host.isNullOrBlank())
        val port = args.getString("realProxyPort")?.toIntOrNull() ?: 1080
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ConfigStore.get(context)
        store.awaitReady()
        val configs = store.configs.value
        val subs = store.subscriptions.value
        val settings = store.settingsSnapshot()
        val chosen = ProxyConfig("Runtime validation endpoint", "socks", requireNotNull(host), port)
        val other = ProxyConfig("Unselected validation endpoint", "socks", "127.0.0.1", 1)
        suspend fun awaitState(wanted: Connection) {
            val state = withTimeout(120_000) { VpnState.state.first { it == wanted || it == Connection.ERROR } }
            assertEquals("VPN failed: ${VpnState.error.value}", wanted, state)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                store.restoreBackup(listOf(other, chosen), emptyList(), settings)
                store.selectExplicitly(chosen.id)
                assertEquals(QuickConnectResult.STARTED, QuickConnect.start(context))
                awaitState(Connection.CONNECTED)
                assertEquals(chosen.id, VpnState.activeId.value)
                // Explicitly traverse the running core because the app itself is
                // intentionally excluded from the Android VPN interface.
                Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", MixedPort.value))).use { socket ->
                    socket.soTimeout = 30_000
                    socket.connect(InetSocketAddress.createUnresolved("example.com", 80), 30_000)
                    socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n".toByteArray())
                    val status = socket.getInputStream().bufferedReader().readLine().orEmpty()
                    assertTrue("No real HTTP response through the core: $status", Regex("^HTTP/1\\.[01] [23][0-9]{2}.*").matches(status))
                }
                QuickConnect.stop(context) {}
                awaitState(Connection.DISCONNECTED)
                assertEquals(chosen.id, store.selectedId.value)
                assertEquals(QuickConnectResult.STARTED, QuickConnect.start(context))
                awaitState(Connection.CONNECTED)
                assertEquals(chosen.id, VpnState.activeId.value)
                QuickConnect.stop(context) {}
                awaitState(Connection.DISCONNECTED)
            } finally {
                QuickConnect.stop(context) {}
                store.restoreBackup(configs, subs, settings)
            }
        }
    }
}

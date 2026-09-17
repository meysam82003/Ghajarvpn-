package com.ghajarvpn.browser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRouteManagerTest {

    private class FakeStorage : BrowserRouteManager.RouteStorage {
        private val values = mutableMapOf<String, String>()
        override fun load(): String? = values["route"]
        override fun save(value: String) { values["route"] = value }
    }

    private class FakeEngine(
        var fullDevice: Boolean = false,
        var endpoint: BrowserRouteEndpoint? = BrowserRouteEndpoint("127.0.0.1", 10627)
    ) : BrowserRouteEngine {
        var started = 0
        var stopped = 0
        override fun startBrowserOnly(): BrowserRouteEndpoint {
            started++
            return endpoint ?: throw IllegalStateException("NO_PROFILE")
        }

        override fun stopBrowserOnly() { stopped++ }
        override fun isFullDeviceConnected(): Boolean = fullDevice
        override fun availabilityDetail(): String = if (fullDevice) "full device active" else "ready"
    }

    @Test
    fun `selected mode persists and reloads`() {
        val manager = BrowserRouteManager(FakeStorage())
        manager.select(BrowserNetworkMode.BROWSER_ONLY) {
            BrowserRouteSnapshot(mode = it, state = BrowserRouteState.CONNECTED, endpoint = BrowserRouteEndpoint("127.0.0.1", 10627))
        }
        val reloaded = BrowserRouteManager(FakeStorage().also { it.save("{\"mode\":\"BROWSER_ONLY\"}") })
        assertEquals(BrowserNetworkMode.BROWSER_ONLY, reloaded.selectedMode())
    }

    @Test
    fun `direct is always connected honestly`() {
        val manager = BrowserRouteManager(FakeStorage())
        val snapshot = manager.select(BrowserNetworkMode.DIRECT) {
            BrowserRouteSnapshot(mode = it, state = BrowserRouteState.CONNECTED)
        }
        assertEquals(BrowserRouteState.CONNECTED, snapshot.state)
    }

    @Test
    fun `full device reflects real engine state only`() {
        val storage = FakeStorage()
        val engine = FakeEngine(fullDevice = false)
        val manager = BrowserRouteManager(storage) { engine }
        manager.select(BrowserNetworkMode.FULL_DEVICE) { mode ->
            if (engine.isFullDeviceConnected()) BrowserRouteSnapshot(mode = mode, state = BrowserRouteState.CONNECTED)
            else BrowserRouteSnapshot(mode = mode, state = BrowserRouteState.UNAVAILABLE)
        }
        assertEquals(BrowserRouteState.UNAVAILABLE, manager.snapshot().state)

        engine.fullDevice = true
        val refreshed = manager.refresh()
        assertEquals(BrowserRouteState.CONNECTED, refreshed.state)
    }

    @Test
    fun `browser only records connecting then connected`() {
        val engine = FakeEngine()
        val manager = BrowserRouteManager(FakeStorage()) { engine }
        manager.select(BrowserNetworkMode.BROWSER_ONLY) { mode ->
            manager.recordConnecting()
            manager.recordResult(engine.startBrowserOnly())
            manager.snapshot()
        }
        val snapshot = manager.snapshot()
        assertEquals(BrowserRouteState.CONNECTED, snapshot.state)
        assertEquals(10627, snapshot.endpoint?.port)
    }

    @Test
    fun `browser only failure becomes honest error`() {
        val engine = FakeEngine(endpoint = null)
        val manager = BrowserRouteManager(FakeStorage()) { engine }
        manager.select(BrowserNetworkMode.BROWSER_ONLY) { mode ->
            manager.recordConnecting()
            runCatching { engine.startBrowserOnly() }.fold(
                onSuccess = { manager.recordResult(it) },
                onFailure = { manager.recordResult(null) }
            )
            manager.snapshot()
        }
        assertEquals(BrowserRouteState.ERROR, manager.snapshot().state)
    }

    @Test
    fun `refresh never fakes a connected browser-only route`() {
        val engine = FakeEngine()
        val manager = BrowserRouteManager(FakeStorage()) { engine }
        manager.select(BrowserNetworkMode.BROWSER_ONLY) { mode -> BrowserRouteSnapshot(mode = mode, state = BrowserRouteState.CONNECTED) }
        // Fresh manager without a started engine must not claim connected.
        val fresh = BrowserRouteManager(FakeStorage().also { it.save("{\"mode\":\"BROWSER_ONLY\"}") }) { FakeEngine() }
        val refreshed = fresh.refresh()
        assertFalse(refreshed.state == BrowserRouteState.CONNECTED)
        assertTrue(refreshed.state == BrowserRouteState.UNAVAILABLE)
    }

    @Test
    fun `corrupt persisted mode falls back to direct`() {
        val fresh = BrowserRouteManager(FakeStorage().also { it.save("not-json") })
        assertEquals(BrowserNetworkMode.DIRECT, fresh.selectedMode())
    }

    @Test
    fun `endpoint proxy rule format`() {
        assertEquals("127.0.0.1:10627", BrowserRouteEndpoint("127.0.0.1", 10627).proxyRule())
    }
}

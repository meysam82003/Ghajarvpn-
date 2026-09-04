package com.ghajarvpn.browser.network

/** Traffic route chosen for the browser. Never faked: each state reflects a real transport. */
enum class BrowserNetworkMode { DIRECT, BROWSER_ONLY, FULL_DEVICE }

/** Live state of the selected route. */
enum class BrowserRouteState { IDLE, CONNECTING, CONNECTED, UNAVAILABLE, ERROR }

data class BrowserRouteEndpoint(val host: String, val port: Int) {
    fun proxyRule(): String = "$host:$port"
}

data class BrowserRouteSnapshot(
    val mode: BrowserNetworkMode = BrowserNetworkMode.DIRECT,
    val state: BrowserRouteState = BrowserRouteState.IDLE,
    val endpoint: BrowserRouteEndpoint? = null,
    val detail: String = "",
    val updatedAt: Long = 0L
)

/**
 * Contract implemented by the host app (which owns the tunnel core).
 * The browser module stays independent of gozarcore; the app registers an engine
 * during startup. All implementations must report honest states only.
 */
interface BrowserRouteEngine {
    /** Starts (or joins) a real browser-scoped transport and returns its local endpoint. */
    fun startBrowserOnly(): BrowserRouteEndpoint

    /** Stops the browser-scoped transport started by [startBrowserOnly]. */
    fun stopBrowserOnly()

    /** True while the device-wide VPN tunnel of the host app is actually connected. */
    fun isFullDeviceConnected(): Boolean

    /** Human-readable explanation, e.g. why BROWSER_ONLY is unavailable. */
    fun availabilityDetail(): String
}

/** Registry bridging the browser module to the host app engine. */
object BrowserRouteEngineHost {
    @Volatile private var engine: BrowserRouteEngine? = null

    fun register(implementation: BrowserRouteEngine) { engine = implementation }

    fun get(): BrowserRouteEngine? = engine
}

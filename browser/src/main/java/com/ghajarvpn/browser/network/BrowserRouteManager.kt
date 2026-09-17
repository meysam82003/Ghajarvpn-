package com.ghajarvpn.browser.network

import org.json.JSONObject

/**
 * Persists the selected route mode behind a tiny storage contract so it can be
 * unit-tested without Android. The live state is never persisted; it is always
 * recomputed from the actual engine, so a fake "Connected" can never survive.
 */
class BrowserRouteManager(
    private val storage: RouteStorage,
    private val engineProvider: () -> BrowserRouteEngine? = { BrowserRouteEngineHost.get() }
) {
    interface RouteStorage {
        fun load(): String?
        fun save(value: String)
    }

    @Volatile private var selected: BrowserNetworkMode = loadSelected()
    @Volatile private var live: BrowserRouteSnapshot = BrowserRouteSnapshot(mode = selected)

    /** Last observed snapshot; call [refresh] or [select] to update it. */
    fun snapshot(): BrowserRouteSnapshot = live

    fun selectedMode(): BrowserNetworkMode = selected

    /** Re-reads the real engine state and reconciles it with the selected mode. */
    fun refresh(): BrowserRouteSnapshot {
        val engine = engineProvider()
        val next = when (selected) {
            BrowserNetworkMode.DIRECT -> BrowserRouteSnapshot(
                mode = BrowserNetworkMode.DIRECT,
                state = BrowserRouteState.CONNECTED,
                detail = "ترافیک مرورگر مستقیم و بدون تونل است",
                updatedAt = now()
            )
            BrowserNetworkMode.FULL_DEVICE -> {
                val connected = engine?.isFullDeviceConnected() == true
                BrowserRouteSnapshot(
                    mode = BrowserNetworkMode.FULL_DEVICE,
                    state = if (connected) BrowserRouteState.CONNECTED else BrowserRouteState.UNAVAILABLE,
                    detail = if (connected) "ترافیک مرورگر از تونل VPN کل دستگاه می‌گذرد"
                    else "VPN کل دستگاه وصل نیست؛ از صفحهٔ اصلی برنامه اتصال را وصل کنید",
                    updatedAt = now()
                )
            }
            BrowserNetworkMode.BROWSER_ONLY -> {
                val current = live
                if (current.mode == BrowserNetworkMode.BROWSER_ONLY &&
                    current.state == BrowserRouteState.CONNECTED && current.endpoint != null
                ) {
                    current.copy(updatedAt = now())
                } else if (current.mode == BrowserNetworkMode.BROWSER_ONLY && current.state == BrowserRouteState.CONNECTING) {
                    current.copy(updatedAt = now())
                } else {
                    BrowserRouteSnapshot(
                        mode = BrowserNetworkMode.BROWSER_ONLY,
                        state = BrowserRouteState.UNAVAILABLE,
                        detail = engine?.availabilityDetail().orEmpty().ifBlank { "مسیر مرورگر آماده نیست" },
                        updatedAt = now()
                    )
                }
            }
        }
        live = next
        return next
    }

    /**
     * Switches the route. The heavy lifting (core start/stop) happens on the caller
     * side through [transition]; this only records intent and the resulting state.
     */
    fun select(mode: BrowserNetworkMode, apply: (BrowserNetworkMode) -> BrowserRouteSnapshot): BrowserRouteSnapshot {
        selected = mode
        val json = JSONObject().put("mode", mode.name)
        storage.save(json.toString())
        live = apply(mode).copy(mode = mode, updatedAt = now())
        return live
    }

    fun recordConnecting() {
        if (selected == BrowserNetworkMode.BROWSER_ONLY) {
            live = BrowserRouteSnapshot(mode = selected, state = BrowserRouteState.CONNECTING, updatedAt = now())
        }
    }

    fun recordResult(endpoint: BrowserRouteEndpoint?) {
        if (selected != BrowserNetworkMode.BROWSER_ONLY) return
        live = if (endpoint != null) BrowserRouteSnapshot(
            mode = selected, state = BrowserRouteState.CONNECTED, endpoint = endpoint,
            detail = "ترافیک مرورگر از تونل اختصاصی قاجار می‌گذرد", updatedAt = now()
        ) else BrowserRouteSnapshot(
            mode = selected, state = BrowserRouteState.ERROR,
            detail = "شروع مسیر مرورگر ناموفق بود", updatedAt = now()
        )
    }

    fun recordUnavailable(detail: String) {
        live = BrowserRouteSnapshot(
            mode = selected,
            state = if (selected == BrowserNetworkMode.FULL_DEVICE) BrowserRouteState.UNAVAILABLE else BrowserRouteState.ERROR,
            detail = detail, updatedAt = now()
        )
    }

    private fun loadSelected(): BrowserNetworkMode = runCatching {
        JSONObject(storage.load() ?: "{}").optString("mode").let { raw ->
            raw.takeIf { it.isNotBlank() }?.let { runCatching { BrowserNetworkMode.valueOf(it) }.getOrNull() }
        } ?: BrowserNetworkMode.DIRECT
    }.getOrDefault(BrowserNetworkMode.DIRECT)

    private fun now() = System.currentTimeMillis()
}

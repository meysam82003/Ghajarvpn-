package net.gozar.app

import android.content.Context
import android.util.Log
import com.ghajarvpn.browser.network.BrowserRouteEndpoint
import com.ghajarvpn.browser.network.BrowserRouteEngine
import gozarcore.Gozarcore
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real browser-only transport: runs the Ghajar core with a localhost SOCKS inbound
 * only (no tun device) so the browser, player and downloads can route through it
 * while the rest of the device keeps its normal network. The core is a singleton,
 * so this engine refuses to start while the device-wide VPN is connected instead
 * of killing it; the state is always reported honestly.
 */
class GozarBrowserRouteEngine(private val appContext: Context) : BrowserRouteEngine {

    @Volatile private var ownedByBrowser = false

    override fun startBrowserOnly(): BrowserRouteEndpoint {
        synchronized(this) {
            check(!isFullDeviceConnected()) {
                "FULL_DEVICE_ACTIVE: تونل VPN کل دستگاه فعال است؛ مسیر مرورگر در حال حاضر از همان تونل می‌گذرد."
            }
            val store = ConfigStore.get(appContext)
            val activeId = store.selectedId.value
            val config = store.configs.value.firstOrNull { it.id == activeId }
                ?: throw IllegalStateException("NO_PROFILE: هیچ کانکشن فعالی برای مسیر مرورگر انتخاب نشده است.")

            val json = browserOnlyConfig(config)
            setupGeoAssets()
            runCatching { Gozarcore.stop() }
            Gozarcore.start(json, -1L)
            ownedByBrowser = true
            return BrowserRouteEndpoint("127.0.0.1", BROWSER_PORT)
        }
    }

    override fun stopBrowserOnly() {
        synchronized(this) {
            if (!ownedByBrowser) return
            ownedByBrowser = false
            runCatching { Gozarcore.stop() }
                .onFailure { Log.w(TAG, "stop failed: ${it.javaClass.simpleName}") }
        }
    }

    override fun isFullDeviceConnected(): Boolean = VpnState.state.value == Connection.CONNECTED

    override fun availabilityDetail(): String = when {
        isFullDeviceConnected() -> "تونل کل دستگاه فعال است؛ برای مسیر مستقل مرورگر ابتدا VPN را قطع کنید."
        ConfigStore.get(appContext).selectedId.value == null -> "ابتدا از صفحهٔ اصلی برنامه یک کانکشن انتخاب کنید."
        else -> "هستهٔ مسیر مرورگر آماده است."
    }

    /** Strips the tun inbound from the standard config so only the local SOCKS inlet remains. */
    private fun browserOnlyConfig(config: ProxyConfig): String {
        val full = JSONObject(ConfigBuilder.build(config, adBlock = false, sniffing = true))
        val inbounds = full.optJSONArray("inbounds") ?: JSONArray()
        val kept = JSONArray()
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            if (inbound.optString("tag") == "tun-in") continue
            if (inbound.optString("tag") == "socks-in") {
                inbound.put("port", BROWSER_PORT)
            }
            kept.put(inbound)
        }
        if (kept.length() == 0) throw IllegalStateException("NO_INBOUND: پیکربندی مسیر مرورگر معتبر نیست.")
        full.put("inbounds", kept)
        val routing = full.optJSONObject("routing")
        routing?.let { route ->
            val rules = route.optJSONArray("rules")
            if (rules != null) {
                val filtered = JSONArray()
                for (i in 0 until rules.length()) {
                    val rule = rules.optJSONObject(i) ?: continue
                    val tags = rule.optJSONArray("inboundTag") ?: continue
                    var referencesTun = false
                    for (j in 0 until tags.length()) if (tags.optString(j) == "tun-in") referencesTun = true
                    if (!referencesTun) filtered.put(rule)
                }
                route.put("rules", filtered)
            }
        }
        return full.toString()
    }

    private fun setupGeoAssets() {
        val dir = appContext.filesDir
        runCatching {
            listOf("geoip.dat", "geosite.dat").forEach { name ->
                val out = File(dir, name)
                if (!out.exists() || out.length() == 0L) {
                    appContext.assets.open(name).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "geo assets not bundled: ${it.message}") }
        Gozarcore.setAssetPath(dir.absolutePath)
    }

    companion object {
        private const val TAG = "GozarBrowserRoute"
        const val BROWSER_PORT = 10627
    }
}

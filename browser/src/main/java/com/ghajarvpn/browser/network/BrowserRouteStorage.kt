package com.ghajarvpn.browser.network

import android.content.Context
import org.json.JSONObject

/** SharedPreferences-backed storage for the selected browser route mode. */
class BrowserRouteStorage(context: Context) : BrowserRouteManager.RouteStorage {
    private val prefs = context.applicationContext.getSharedPreferences("ghajar_browser_route_v1", Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString("route", null)

    override fun save(value: String) {
        prefs.edit().putString("route", value).apply()
    }

    fun readMode(): BrowserNetworkMode = runCatching {
        BrowserNetworkMode.valueOf(JSONObject(prefs.getString("route", null) ?: "{}").optString("mode", "DIRECT"))
    }.getOrDefault(BrowserNetworkMode.DIRECT)
}

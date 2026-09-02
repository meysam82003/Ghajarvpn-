package com.ghajarvpn.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class BrowserNetworkMode { DIRECT, BROWSER_ONLY, FULL_DEVICE }

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var url: String = HOME_URL,
    var title: String = "قاجار",
    val private: Boolean = false,
    var group: String = "",
    var lastUsed: Long = System.currentTimeMillis()
) {
    companion object { const val HOME_URL = "https://home.ghajar.invalid/" }
}

data class BrowserSettings(
    val javaScript: Boolean = true,
    val cookies: Boolean = true,
    val trackerBlocking: Boolean = true,
    val adBlocking: Boolean = false,
    val httpsPreference: Boolean = true,
    val desktopMode: Boolean = false,
    val darkPages: Boolean = false,
    val searchEngine: String = "https://www.google.com/search?q=%s"
)

data class BrowserHistory(val title: String, val url: String, val at: Long)

class BrowserRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ghajar_browser_v1", Context.MODE_PRIVATE)

    fun settings(): BrowserSettings = runCatching {
        val o = JSONObject(prefs.getString("settings", "{}") ?: "{}")
        BrowserSettings(
            javaScript = o.optBoolean("js", true), cookies = o.optBoolean("cookies", true),
            trackerBlocking = o.optBoolean("trackers", true), adBlocking = o.optBoolean("ads", false),
            httpsPreference = o.optBoolean("https", true), desktopMode = o.optBoolean("desktop", false),
            darkPages = o.optBoolean("dark", false),
            searchEngine = o.optString("search", "https://www.google.com/search?q=%s")
        )
    }.getOrDefault(BrowserSettings())

    fun saveSettings(value: BrowserSettings) {
        val o = JSONObject().put("js", value.javaScript).put("cookies", value.cookies)
            .put("trackers", value.trackerBlocking).put("ads", value.adBlocking)
            .put("https", value.httpsPreference).put("desktop", value.desktopMode)
            .put("dark", value.darkPages).put("search", value.searchEngine)
        prefs.edit().putString("settings", o.toString()).apply()
    }

    fun restoreTabs(): MutableList<BrowserTab> = runCatching {
        val array = JSONArray(prefs.getString("tabs", "[]"))
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { o ->
            BrowserTab(o.optString("id"), o.optString("url", BrowserTab.HOME_URL), o.optString("title", "قاجار"),
                false, o.optString("group"), o.optLong("last", System.currentTimeMillis()))
        } }.takeLast(MAX_TABS).toMutableList()
    }.getOrDefault(mutableListOf()).also { if (it.isEmpty()) it += BrowserTab() }

    fun saveTabs(tabs: List<BrowserTab>) {
        val array = JSONArray()
        tabs.filterNot(BrowserTab::private).takeLast(MAX_TABS).forEach { tab ->
            array.put(JSONObject().put("id", tab.id).put("url", tab.url).put("title", tab.title)
                .put("group", tab.group).put("last", tab.lastUsed))
        }
        prefs.edit().putString("tabs", array.toString()).apply()
    }

    fun addHistory(title: String, url: String) {
        if (!url.startsWith("http")) return
        val items = history().toMutableList()
        items.removeAll { it.url == url }
        items.add(0, BrowserHistory(title.take(160), url, System.currentTimeMillis()))
        val array = JSONArray()
        items.take(MAX_HISTORY).forEach { array.put(JSONObject().put("title", it.title).put("url", it.url).put("at", it.at)) }
        prefs.edit().putString("history", array.toString()).apply()
    }

    fun history(): List<BrowserHistory> = runCatching {
        val array = JSONArray(prefs.getString("history", "[]"))
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { BrowserHistory(it.optString("title"), it.optString("url"), it.optLong("at")) } }
    }.getOrDefault(emptyList())

    fun bookmarks(): Set<String> = prefs.getStringSet("bookmarks", emptySet()).orEmpty()
    fun toggleBookmark(url: String): Boolean {
        val set = bookmarks().toMutableSet()
        val added = if (url in set) { set.remove(url); false } else { set.add(url); true }
        prefs.edit().putStringSet("bookmarks", set).apply()
        return added
    }

    fun clearBrowsingData() {
        prefs.edit().remove("history").remove("tabs").remove("bookmarks").apply()
    }

    companion object { const val MAX_TABS = 24; const val MAX_HISTORY = 500 }
}

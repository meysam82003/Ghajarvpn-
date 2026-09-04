package com.ghajarvpn.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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

/** Key-value document seam so persistence is testable without Android. */
interface BrowserDocumentStore {
    fun load(key: String): String?
    fun save(key: String, value: String)
    fun remove(key: String)
}

class SharedPrefsDocumentStore(context: Context) : BrowserDocumentStore {
    private val prefs = context.applicationContext.getSharedPreferences("ghajar_browser_v1", Context.MODE_PRIVATE)
    override fun load(key: String): String? = prefs.getString(key, null)
    override fun save(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}

class BrowserRepository(store: BrowserDocumentStore) {
    constructor(context: Context) : this(SharedPrefsDocumentStore(context))

    private val store: BrowserDocumentStore = store

    fun settings(): BrowserSettings = runCatching {
        val o = JSONObject(store.load("settings") ?: "{}")
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
        store.save("settings", o.toString())
    }

    fun restoreTabs(): MutableList<BrowserTab> = runCatching {
        val array = JSONArray(store.load("tabs") ?: "[]")
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
        store.save("tabs", array.toString())
    }

    fun addHistory(title: String, url: String) {
        if (!url.startsWith("http")) return
        val items = history().toMutableList()
        items.removeAll { it.url == url }
        items.add(0, BrowserHistory(title.take(160), url, System.currentTimeMillis()))
        val array = JSONArray()
        items.take(MAX_HISTORY).forEach { array.put(JSONObject().put("title", it.title).put("url", it.url).put("at", it.at)) }
        store.save("history", array.toString())
    }

    fun history(): List<BrowserHistory> = runCatching {
        val array = JSONArray(store.load("history") ?: "[]")
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { BrowserHistory(it.optString("title"), it.optString("url"), it.optLong("at")) } }
    }.getOrDefault(emptyList())

    fun bookmarks(): Set<String> = runCatching {
        val array = JSONArray(store.load("bookmarks") ?: "[]")
        (0 until array.length()).mapNotNull { i -> array.optString(i).takeIf(String::isNotBlank) }.toSet()
    }.getOrDefault(emptySet())

    fun toggleBookmark(url: String): Boolean {
        val set = bookmarks().toMutableSet()
        val added = if (url in set) { set.remove(url); false } else { set.add(url); true }
        val array = JSONArray()
        set.forEach { array.put(it) }
        store.save("bookmarks", array.toString())
        return added
    }

    fun clearBrowsingData() {
        store.remove("history"); store.remove("tabs"); store.remove("bookmarks")
    }

    companion object { const val MAX_TABS = 24; const val MAX_HISTORY = 500 }
}

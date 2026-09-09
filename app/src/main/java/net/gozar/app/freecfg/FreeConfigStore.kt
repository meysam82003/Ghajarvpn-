package net.gozar.app.freecfg

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists sources + items + health in one JSON document (atomic rename), plus
 * an in-memory quarantine for dead configs awaiting retest.
 */
class FreeConfigStore(context: Context) {

    private val file = java.io.File(context.applicationContext.filesDir, "ghajar_free_configs.json")
    private val lock = Any()

    data class ItemRecord(
        val link: String,
        val hash: String,
        val protocol: String,
        val country: String,
        val name: String,
        val sourceId: String,
        val fetchedAt: Long,
        val lastChecked: Long,
        val ok: Boolean,
        val pingMs: Long = -1,
        val failureStreak: Int = 0,
        val favorite: Boolean = false
    )

    fun loadSources(): MutableList<FreeSource> = synchronized(lock) {
        runCatching {
            val root = JSONObject(read())
            val array = root.optJSONArray("sources") ?: JSONArray()
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { FreeSource.fromJson(it) } }
        }.getOrDefault(FreeSourceRegistry.DEFAULT_SOURCES).toMutableList()
    }

    fun loadItems(): MutableList<ItemRecord> = synchronized(lock) {
        runCatching {
            val root = JSONObject(read())
            val array = root.optJSONArray("items") ?: JSONArray()
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { o ->
                    ItemRecord(
                        link = o.optString("link"), hash = o.optString("hash"),
                        protocol = o.optString("protocol"), country = o.optString("country"),
                        name = o.optString("name"), sourceId = o.optString("sourceId"),
                        fetchedAt = o.optLong("fetchedAt"), lastChecked = o.optLong("lastChecked"),
                        ok = o.optBoolean("ok"), pingMs = o.optLong("pingMs", -1),
                        failureStreak = o.optInt("failureStreak"), favorite = o.optBoolean("favorite")
                    )
                }
            }
        }.getOrDefault(mutableListOf()).toMutableList()
    }

    fun save(sources: List<FreeSource>, items: List<ItemRecord>) = synchronized(lock) {
        val root = JSONObject()
        val srcArray = JSONArray(); sources.forEach { srcArray.put(it.toJson()) }
        val itemArray = JSONArray()
        items.forEach {
            itemArray.put(JSONObject()
                .put("link", it.link).put("hash", it.hash).put("protocol", it.protocol)
                .put("country", it.country).put("name", it.name).put("sourceId", it.sourceId)
                .put("fetchedAt", it.fetchedAt).put("lastChecked", it.lastChecked)
                .put("ok", it.ok).put("pingMs", it.pingMs)
                .put("failureStreak", it.failureStreak).put("favorite", it.favorite))
        }
        root.put("sources", srcArray).put("items", itemArray)
        val tmp = java.io.File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) { file.writeText(root.toString()); tmp.delete() }
    }

    private fun read(): String = file.takeIf { it.exists() }?.readText() ?: "{}"
}

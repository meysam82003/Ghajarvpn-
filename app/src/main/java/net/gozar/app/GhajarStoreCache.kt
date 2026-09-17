package net.gozar.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Composite cache key + freshness rules, kept separate from the actual
 * SharedPreferences-backed store below so this logic can be unit tested
 * without an Android Context.
 *
 * The bug this fixes: the cache used to be keyed by panel id alone, while
 * the real fetch is scoped by panel + category + time-range too. Switching
 * category or time range while offline silently showed a *different*
 * filter's cached plans under the current one. The key must include every
 * parameter the fetch itself varies by - including the linked account,
 * since two different accounts can see different catalogs/prices.
 */
internal object GhajarStoreCacheKey {
    /** Cached plans older than this are never served as "offline" data -
     * treated exactly like no cache exists, never as stale-but-shown data. */
    const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000L

    fun of(accountHash: String, panelId: String, categoryId: String?, days: Int?): String =
        "$accountHash|$panelId|${categoryId.orEmpty()}|${days ?: 0}"

    fun isFresh(savedAtMs: Long, nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean =
        savedAtMs in 1..nowMs && nowMs - savedAtMs <= maxAgeMs
}

internal data class GhajarCachedProducts(val products: List<GhajarProduct>, val savedAtMs: Long)

/**
 * Last-known plan list per (account, panel, category, time-range), shown
 * read-only when the live fetch fails so the store isn't just a blank error
 * screen with no signal. Never a source for an actual purchase - callers
 * must disable buy/payment actions whenever data came from here instead of
 * a fresh fetch, and must never show one filter's cache under a different
 * filter (see GhajarStoreCacheKey).
 */
internal object GhajarStoreCache {
    private fun prefs(context: Context) = context.getSharedPreferences("ghajar_store_cache", 0)

    fun saveProducts(
        context: Context,
        key: String,
        products: List<GhajarProduct>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val arr = JSONArray()
        products.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name)
                    .put("price", p.price ?: JSONObject.NULL)
                    .put("trafficGb", p.trafficGb ?: JSONObject.NULL)
                    .put("days", p.days ?: JSONObject.NULL)
                    .put("description", p.description).put("countryId", p.countryId)
            )
        }
        val root = JSONObject().put("savedAt", nowMs).put("items", arr)
        prefs(context).edit().putString("products_$key", root.toString()).apply()
    }

    /**
     * Null when there is no cache for this exact key, or what's stored
     * decoded but is older than [GhajarStoreCacheKey.MAX_AGE_MS] - both are
     * treated as "nothing usable for this filter", never as a reason to
     * fall back to some other filter's cached data.
     */
    fun loadProducts(context: Context, key: String, nowMs: Long = System.currentTimeMillis()): GhajarCachedProducts? {
        val raw = prefs(context).getString("products_$key", null) ?: return null
        val decoded = runCatching {
            val root = JSONObject(raw)
            val savedAt = root.optLong("savedAt", 0L)
            val arr = root.getJSONArray("items")
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GhajarProduct(
                    id = o.getString("id"), name = o.getString("name"),
                    price = o.optLong("price").takeIf { !o.isNull("price") },
                    trafficGb = o.optDouble("trafficGb").takeIf { !o.isNull("trafficGb") },
                    days = o.optInt("days").takeIf { !o.isNull("days") },
                    description = o.optString("description"), countryId = o.optString("countryId")
                )
            }
            GhajarCachedProducts(list, savedAt)
        }.getOrNull() ?: return null
        return decoded.takeIf { GhajarStoreCacheKey.isFresh(it.savedAtMs, nowMs) }
    }
}

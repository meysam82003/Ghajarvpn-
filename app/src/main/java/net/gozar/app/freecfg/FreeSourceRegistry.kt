package net.gozar.app.freecfg

import org.json.JSONArray
import org.json.JSONObject

/**
 * Registry of free-config sources. Sources live in data, never hard-coded in
 * UI, so adding one later is a data change. Health bookkeeping (streaks, rates)
 * drives quarantine and ranking.
 */
data class FreeSource(
    val id: String,
    val type: String,          // telegram_channel / telegram_group / url
    val endpoint: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    var lastFetch: Long = 0L,
    var lastSuccess: Long = 0L,
    var lastFailure: Long = 0L,
    var failureStreak: Int = 0,
    var itemsFound: Int = 0,
    var validItems: Int = 0,
    var duplicateRate: Double = 0.0
) {
    val healthy: Boolean get() = failureStreak < 5

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type).put("endpoint", endpoint)
        .put("enabled", enabled).put("priority", priority)
        .put("lastFetch", lastFetch).put("lastSuccess", lastSuccess)
        .put("lastFailure", lastFailure).put("failureStreak", failureStreak)
        .put("itemsFound", itemsFound).put("validItems", validItems)
        .put("duplicateRate", duplicateRate)

    companion object {
        fun fromJson(o: JSONObject): FreeSource = FreeSource(
            id = o.optString("id"), type = o.optString("type", "telegram_channel"),
            endpoint = o.optString("endpoint"), enabled = o.optBoolean("enabled", true),
            priority = o.optInt("priority"), lastFetch = o.optLong("lastFetch"),
            lastSuccess = o.optLong("lastSuccess"), lastFailure = o.optLong("lastFailure"),
            failureStreak = o.optInt("failureStreak"), itemsFound = o.optInt("itemsFound"),
            validItems = o.optInt("validItems"), duplicateRate = o.optDouble("duplicateRate", 0.0)
        )
    }
}

object FreeSourceRegistry {
    val DEFAULT_SOURCES = listOf(
        FreeSource("tg_prrofile_purple", "telegram_channel", "https://t.me/s/prrofile_purple", priority = 10),
        FreeSource("tg_v2rayngvpn", "telegram_channel", "https://t.me/s/v2rayngvpn", priority = 9),
        FreeSource("tg_meliproxyy", "telegram_channel", "https://t.me/s/meliproxyy", priority = 8),
        FreeSource("tg_v2ray_tz", "telegram_channel", "https://t.me/s/V2Ray_Tz", priority = 7),
        FreeSource("tg_mitivpn", "telegram_channel", "https://t.me/s/mitivpn", priority = 6),
        FreeSource("tg_kingoclub", "telegram_group", "https://t.me/s/kingoclub", priority = 5),
        FreeSource("tg_vpn_click", "telegram_group", "https://t.me/s/vpn_Click", priority = 4),
        FreeSource("tg_hiddify_nexttt", "telegram_channel", "https://t.me/s/Hiddify_Nexttt", priority = 3)
    )
}

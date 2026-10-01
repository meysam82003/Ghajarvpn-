package net.gozar.app.freecfg

import org.json.JSONObject

/** Only source preferences, not credentials, binaries, or trust grants. Missing = legacy defaults. */
object FreeSourcePolicy {
    fun normalize(raw: JSONObject): JSONObject = JSONObject().apply {
        FreeSourceRegistry.DEFAULT_SOURCES.forEach { source ->
            raw.optString(source.id).takeIf { it in setOf("enabled", "disabled", "removed") }?.let { put(source.id, it) }
        }
    }
    fun active(raw: JSONObject) = FreeSourceRegistry.DEFAULT_SOURCES.filter { raw.optString(it.id, "enabled") == "enabled" }
}

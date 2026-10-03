package net.gozar.app.plugins

import net.gozar.app.ConfigSource
import net.gozar.app.ProxyConfig
import org.json.JSONObject

/** Lossless payload, separate from address/port of a single legacy server. */
data class PluginProfile(val id: String, val format: String, val payload: String, val version: String, val settings: JSONObject)
object PluginProfiles {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun isPlugin(config: ProxyConfig) = config.protocol == "plugin"
    fun read(config: ProxyConfig): PluginProfile? {
        if (!isPlugin(config)) return null
        val p = config.extraJson().getJSONObject("plugin")
        require(p.getString("payload").isNotBlank() && p.getString("payload").toByteArray().size <= MAX_BYTES)
        require((p.optJSONObject("settings")?.toString()?.toByteArray()?.size ?: 0) <= 65536)
        val candidate = requireNotNull(PluginCatalog.candidate(p.getString("id")))
        require(p.getString("format") in candidate.formats)
        if (candidate.id == "mihomo") net.gozar.plugin.api.MihomoFiles.decode(p.optJSONObject("settings") ?: JSONObject())
        return PluginProfile(p.getString("id"), p.getString("format"), p.getString("payload"), p.optString("version"), p.optJSONObject("settings") ?: JSONObject())
    }
    fun create(id: String, format: String, original: String, name: String = "", source: ConfigSource = ConfigSource.PERSONAL): ProxyConfig {
        val candidate = requireNotNull(PluginCatalog.candidate(id))
        require(format in candidate.formats && original.toByteArray().size <= MAX_BYTES && original.isNotBlank())
        if (id == "shadowquic") net.gozar.plugin.api.ShadowQuicProfile.parse(original, format)
        return ProxyConfig(name = name.ifBlank { candidate.name }, protocol = "plugin", address = "", port = 0, source = source,
            extra = JSONObject().put("plugin", JSONObject().put("id", id).put("format", format).put("payload", original)
                .put("version", "").put("settings", JSONObject())).toString())
    }
    /** Detection only; never parse/rewrite YAML, resolve providers, or execute config during import. */
    fun import(text: String, source: ConfigSource = ConfigSource.PERSONAL): ProxyConfig? {
        if (text.toByteArray().size > MAX_BYTES) return null
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (Regex("(?m)^(proxies|proxy-providers|proxy-groups|rule-providers|rules|mixed-port)\\s*:").containsMatchIn(trimmed))
            return create("mihomo", "mihomo-yaml", text, source = source)
        if (trimmed.startsWith("{")) {
            val o = runCatching { JSONObject(trimmed) }.getOrNull()
            o?.optJSONObject("ghajarPlugin")?.let { p ->
                val created = create(p.getString("id"), p.getString("format"), p.getString("payload"), source = source)
                val restored = created.copy(extra = JSONObject().put("plugin", p).toString())
                read(restored)
                return restored
            }
            if (o != null && listOf("proxies", "proxy-providers", "proxy-groups", "rule-providers", "rules", "mixed-port", "socks-port").any { o.has(it) })
                return create("mihomo", "mihomo-json", text, source = source)
        }
        if ((trimmed.trim().startsWith("shadowquic://", true) || trimmed.trim().startsWith("sq://", true)) && !trimmed.trim().contains(Regex("[\\r\\n]"))) return create("shadowquic", "shadowquic-uri", text, source = source)
        return null
    }
    fun export(config: ProxyConfig): String {
        val profile = requireNotNull(read(config))
        return if (profile.format == "shadowquic-json") JSONObject().put("ghajarPlugin", config.extraJson().getJSONObject("plugin")).toString() else profile.payload
    }
    fun identity(config: ProxyConfig): String = "plugin|" + PluginTrust.sha256(config.extra.toByteArray())
    fun requirement(config: ProxyConfig): String? = runCatching { read(config) }.getOrNull()?.let { "برای استفاده از این اتصال، افزونه ${PluginCatalog.candidate(it.id)?.name ?: it.id} باید نصب شود." } ?: if (isPlugin(config)) "کانفیگ افزونه حفظ شده است؛ مشخصات آن در این نسخه تأیید نشد." else null
}

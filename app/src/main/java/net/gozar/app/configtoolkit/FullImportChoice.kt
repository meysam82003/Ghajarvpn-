package net.gozar.app.configtoolkit

import net.gozar.app.ProxyConfig
import net.gozar.app.ForeignImport
import net.gozar.app.ConfigParser
import org.json.JSONObject

object FullImportChoice {
    fun hasFull(configs: List<ProxyConfig>) = configs.any { it.protocol in setOf("singbox-full","xray-full") }
    /** Explicit extraction preserves creator policies; never apply this to runtime implicitly. */
    fun extract(configs: List<ProxyConfig>): List<ProxyConfig> = configs.flatMap { full ->
        if(full.protocol !in setOf("singbox-full","xray-full")) listOf(full) else {
            val extra=JSONObject(full.extra); val raw=extra.getString("rawConfig")
            val nodes=if(full.protocol=="singbox-full") ForeignImport.singBoxNodes(BoundedJson.objectValue(raw),full.source).let {
                require(it.warnings.isEmpty()) { "برخی گزینه‌ها قابل استخراج نیستند؛ Full Config را نگه دارید." };it.configs
            } else ConfigParser.parseJsonOutbounds(raw,full.source)
            require(nodes.isNotEmpty()) { "سرور قابل استخراج پیدا نشد." }
            nodes.map { node ->
                val e=runCatching { JSONObject(node.extra) }.getOrDefault(JSONObject())
                extra.optJSONObject("npvContainer")?.let { e.put("npvContainer",it).put("npvProfileIndex",extra.optInt("npvProfileIndex")) }
                node.copy(extra=e.toString(),locked=full.locked)
            }
        }
    }
}

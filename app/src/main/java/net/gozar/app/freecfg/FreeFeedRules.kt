package net.gozar.app.freecfg

import net.gozar.app.ProxyConfig
import java.net.URI

/** Message extraction and identity are independent of transport and display names. */
object FreeFeedRules {
    const val LIMIT = 350
    private val direct = Regex("(?:vless|vmess|trojan|ss|hysteria2|hy2|tuic|wireguard|wg|socks5)://[^\\s\"'<>\\\\]+", RegexOption.IGNORE_CASE)
    private val http = Regex("https?://[^\\s\"'<>\\\\]+", RegexOption.IGNORE_CASE)
    data class Links(val configs: List<String>, val subscriptions: List<String>)

    fun extract(html: String): Links {
        val clean = html.replace(Regex("(?i)<wbr\\s*/?>"), "")
            .filterNot { it in "\u200b\u200c\u200d\u200e\u200f\ufeff" }
        val bodies = Regex("""(?s)<div\s+class=["']tgme_widget_message_text[^"']*["'][^>]*>(.*?)</div>""")
            .findAll(clean).map { decode(it.groupValues[1]) }.toList()
        val text = bodies.joinToString("\n")
        val urls = http.findAll(text).map { it.value.trimEnd('.', ',', ')', '،', '؛') }
            .filter { url ->
                val u = runCatching { URI(url) }.getOrNull()
                val host = u?.host?.lowercase().orEmpty()
                host.isNotBlank() && u?.userInfo == null &&
                    host !in setOf("t.me", "telegram.me", "telegram.org", "www.t.me") &&
                    !url.substringBefore('?').matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|mp4|svg)$", RegexOption.IGNORE_CASE))
            }.distinct().sortedByDescending { url ->
                // Opaque /sub/<token> URLs must not lose their slots to ads.
                Regex("(?i)/(sub|subscription|api|s)/|[?&](token|sub)=").containsMatchIn(url)
            }.toList()
        return Links(direct.findAll(text).map { it.value }.distinct().toList(), urls)
    }

    private fun decode(value: String): String {
        var result = value.replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        result = Regex("&#(x[0-9a-fA-F]+|[0-9]+);").replace(result) {
            val s = it.groupValues[1]
            val n = if (s.startsWith("x")) s.drop(1).toIntOrNull(16) else s.toIntOrNull()
            if (n != null && Character.isValidCodePoint(n)) String(Character.toChars(n)) else it.value
        }
        return result
    }

    fun signature(config: ProxyConfig): String {
        val json = config.toJson()
        return json.keys().asSequence().filter { it !in setOf("id", "name", "subId", "source", "locked") }
            .sorted().joinToString("|") { key -> "$key=${json.get(key)}" }
    }

    fun select(groups: List<List<ProxyConfig>>, limit: Int = LIMIT): List<ProxyConfig> {
        val unique = linkedMapOf<String, ProxyConfig>()
        // Round-robin keeps one large subscription from crowding out all channels.
        for (i in 0 until (groups.maxOfOrNull { it.size } ?: 0)) {
            groups.forEach { group -> group.getOrNull(i)?.let { unique.putIfAbsent(signature(it), it) } }
            if (unique.size >= limit) break
        }
        return unique.values.take(limit)
    }
}

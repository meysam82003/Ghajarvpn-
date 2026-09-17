package net.gozar.app.configtoolkit

import java.util.Locale

data class FormatDetection(
    val format: ConfigFormat,
    val confidence: Int,
    val evidence: Set<String>
)

/** Bounded content sniffing; detection never trusts a filename by itself. */
object FormatDetector {
    private const val SNIFF_LIMIT = 64 * 1024

    fun detect(input: ConfigInput): FormatDetection {
        val head = input.bytes.copyOfRange(0, minOf(input.bytes.size, SNIFF_LIMIT))
        val text = head.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val lower = text.lowercase(Locale.ROOT)
        val ext = input.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val evidence = linkedSetOf<String>()

        fun result(format: ConfigFormat, base: Int, magic: String? = null): FormatDetection {
            if (ext in format.extensions) evidence += "extension:$ext"
            if (magic != null) evidence += "signature:$magic"
            val extensionBonus = if (ext in format.extensions) 10 else 0
            return FormatDetection(format, (base + extensionBonus).coerceAtMost(100), evidence)
        }

        if (text.startsWith("NPVT1", true)) return result(ConfigFormat.NPVT, 90, "NPVT1")
        if (text.startsWith("NPVS", true)) return result(ConfigFormat.NPVS, 90, "NPVS")
        if (lower.startsWith("happ://crypt")) return result(ConfigFormat.HAPP, 95, "happ://crypt")
        if (lower.startsWith("happ://") || lower.startsWith("happ-proxy://")) {
            return result(ConfigFormat.HAPP, 90, "happ-uri")
        }
        if (lower.startsWith("nm-") || lower.startsWith("nm://")) return result(ConfigFormat.NETMOD, 85, "nm-")

        val jsonLike = (text.startsWith("{") && text.endsWith("}")) ||
            (text.startsWith("[") && text.endsWith("]"))
        if (jsonLike) {
            val hinted = when {
                ext == "npvt" || lower.contains("\"v2rayprofile\"") || lower.contains("\"lockconfig\"") -> ConfigFormat.NPVT
                ext == "npvs" -> ConfigFormat.NPVS
                ext == "happ" -> ConfigFormat.HAPP
                ext == "nm" -> ConfigFormat.NETMOD
                ext == "ehi" -> ConfigFormat.EHI
                ext == "slip" -> ConfigFormat.SLIPNET
                ext == "hat" -> ConfigFormat.HAT
                ext == "dark" -> ConfigFormat.DARK
                else -> ConfigFormat.JSON
            }
            return result(hinted, if (hinted == ConfigFormat.JSON) 80 else 75, "json")
        }

        val standardLink = Regex("(?im)^\\s*(vless|vmess|trojan|ss|socks|socks5)://").containsMatchIn(text)
        if (standardLink) return result(ConfigFormat.TEXT, 90, "standard-link")

        val extensionFormat = ConfigFormat.entries.firstOrNull { ext in it.extensions }
        if (extensionFormat != null) return result(extensionFormat, 25)
        return FormatDetection(ConfigFormat.UNKNOWN, 0, emptySet())
    }
}

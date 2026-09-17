package net.gozar.app.configcenter

/**
 * Generic extraction pipeline: dig every proxy link out of arbitrary text,
 * nested base64, JSON and YAML containers. Recurses one level into decoded
 * payloads so subscriptions-in-subscriptions still yield links.
 */
object ConfigExtractor {

    private val LINK_REGEX = Regex(
        "(?im)\\b(?:vless|vmess|trojan|ss|socks5?|hysteria2?|hy2|tuic)://\\S+"
    )

    data class Extraction(
        val links: List<String> = emptyList(),
        val rawText: String = "",
        val locked: Boolean = false,
        val lockedHint: String = ""
    ) {
        /** Compact status line for UI summaries. */
        fun summary(): String = when {
            locked -> "LOCKED"
            links.isNotEmpty() -> "links=" + links.size
            rawText.isNotBlank() -> "text"
            else -> "empty"
        }
    }

    fun extract(fileName: String?, bytes: ByteArray): Extraction {
        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
        return extractText(text)
    }

    fun extractText(text: String): Extraction {
        val links = LINK_REGEX.findAll(text).map { it.value.trim().removeSuffix(",").removeSuffix(";") }.toList()
        if (links.isNotEmpty()) return Extraction(links.distinct(), text)

        // One-level base64 unwrap (V2Ray subscriptions)
        val compact = text.replace(Regex("\\s"), "")
        if (compact.length >= 16 && compact.length % 4 >= 0 &&
            compact.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        ) {
            val decoded = runCatching {
                java.util.Base64.getMimeDecoder().decode(compact).toString(Charsets.UTF_8)
            }.getOrDefault("")
            val decodedLinks = LINK_REGEX.findAll(decoded)
                .map { it.value.trim() }.toList()
            if (decodedLinks.isNotEmpty()) return Extraction(decodedLinks.distinct(), decoded)
        }

        // Locked payloads announce themselves instead of being brute-forced.
        if (text.contains(Regex("(?i)(password|passkey|encrypted)\\s*[:=]")) &&
            text.contains("base64") == false && links.isEmpty()
        ) {
            val hint = Regex("(?i)(password|passkey)\\s*[:=]\\s*\\S{0,3}")
                .find(text)?.value ?: "locked"
            return Extraction(rawText = text, locked = true, lockedHint = hint)
        }

        return Extraction(rawText = text)
    }
}

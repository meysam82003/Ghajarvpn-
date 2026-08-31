package net.gozar.app

/**
 * Multi-source free-config ingestion rules.
 * Public surface is always branded @Ghajarvpn; the technical origin of a
 * source is kept as an internal tag for abuse handling only and must never
 * reach user-visible text. Locked NPVT payloads are never broken open:
 * an open-text payload is accepted, anything else requires the owner key.
 */
internal object GhajarChannelRules {
    const val PUBLIC_BRAND = "@Ghajarvpn"

    val SUPPORTED_SCHEMES = setOf(
        "vless", "vmess", "trojan", "ss", "ssr", "hysteria", "hysteria2", "hy2",
        "tuic", "juicity", "anytls", "snell", "mieru", "wireguard", "wg",
        "socks", "socks5", "http", "https"
    )

    private val NOISE_HTTP_HOSTS = setOf("t.me", "telegram.me", "telegram.org", "github.com", "play.google.com")

    private val LINK_REGEX = Regex("[A-Za-z][A-Za-z0-9+.-]*://[^\\s\"'<>،؛\\u200c]+")

    fun publicBrand(source: String?): String = PUBLIC_BRAND

    fun internalOriginTag(source: String?): String {
        val normalized = source?.trim()?.lowercase()
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.removePrefix("www.")
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.trim('/')
            .orEmpty()
        return if (normalized.isBlank()) "src:internal" else "src:$normalized"
    }

    fun extractProxyLinks(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        // Fragments are display remarks. Keep the first visible label while
        // deduplicating the underlying transport URI.
        val seen = LinkedHashMap<String, String>()
        LINK_REGEX.findAll(text).forEach { match ->
            val raw = match.value.trimEnd('.', ',', ')', ']', '}', ':')
            val scheme = raw.substringBefore("://", "").lowercase()
            if (scheme !in SUPPORTED_SCHEMES) return@forEach
            if (scheme == "http" || scheme == "https") {
                val host = raw.substringAfter("://").substringBefore('/').substringBefore(':')
                    .removePrefix("www.").lowercase()
                if (host in NOISE_HTTP_HOSTS) return@forEach
            }
            if (raw.substringAfter("://", "").isBlank()) return@forEach
            val transportKey = raw.substringBefore('#')
            seen.putIfAbsent(transportKey, raw)
        }
        return seen.values.toList()
    }

    fun isAcceptableChannelText(text: String?): Boolean = extractProxyLinks(text).isNotEmpty()

    fun npvtPayload(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val links = extractProxyLinks(text)
        links.firstOrNull { !it.substringBefore("://").lowercase().let { s -> s == "http" || s == "https" } }
            ?.let { return it }
        links.firstOrNull { it.substringBefore("://").lowercase().let { s -> s == "http" || s == "https" } }
            ?.let { return it }
        return null
    }

    fun npvtIsLocked(text: String?): Boolean =
        !text.isNullOrBlank() && npvtPayload(text) == null
}

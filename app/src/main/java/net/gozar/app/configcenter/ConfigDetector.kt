package net.gozar.app.configcenter

/**
 * Content-first config format detection. Extension is a hint, never the
 * authority: bytes/text are sniffed for magic markers, structures and links.
 */
object ConfigDetector {

    enum class Format {
        V2RAY_SUB,       // base64 blob of links
        RAW_LINKS,       // plain text with vless://... lines
        VLESS, VMESS, TROJAN, SHADOWSOCKS, SOCKS, HYSTERIA2, TUIC,
        CLASH_YAML,
        SINGBOX_JSON,
        JSON_ARRAY,
        OPENVPN,
        WIREGUARD,
        SSH,
        NPVT, NPVS, NPVTSUB, EHI, NM, HAPP, DARK, TNL, SLIP,
        UNKNOWN
    }

    enum class Confidence { FULL, PARTIAL, UNSUPPORTED }

    data class Detection(val format: Format, val confidence: Confidence, val hint: String = "")

    private val PROXY_SCHEMES = listOf(
        "vless://", "vmess://", "trojan://", "ss://", "socks://", "socks5://",
        "hysteria2://", "hy2://", "tuic://"
    )

    private val SCHEME_TO_FORMAT = mapOf(
        "vless://" to Format.VLESS, "vmess://" to Format.VMESS,
        "trojan://" to Format.TROJAN, "ss://" to Format.SHADOWSOCKS,
        "socks://" to Format.SOCKS, "socks5://" to Format.SOCKS,
        "hysteria2://" to Format.HYSTERIA2, "hy2://" to Format.HYSTERIA2,
        "tuic://" to Format.TUIC
    )

    fun detect(fileName: String?, bytes: ByteArray): Detection {
        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
        val printable = text.count { it.code in 9..13 || it.code in 32..126 || it.code > 127 }
        val looksText = bytes.isNotEmpty() && printable >= bytes.size * 0.85

        val byContent = detectText(text, looksText)
        if (byContent != null && byContent.confidence == Confidence.FULL) return byContent

        val ext = fileName.orEmpty().substringAfterLast('.', "").lowercase()
        val byExt = when (ext) {
            "npvt" -> Format.NPVT; "npvs" -> Format.NPVS; "npvtsub" -> Format.NPVTSUB
            "ehi" -> Format.EHI; "nm" -> Format.NM; "happ" -> Format.HAPP
            "dark" -> Format.DARK; "tnl" -> Format.TNL; "slip" -> Format.SLIP
            "ovpn" -> Format.OPENVPN; "json" -> Format.SINGBOX_JSON
            "yaml", "yml" -> Format.CLASH_YAML; "txt" -> Format.RAW_LINKS
            else -> null
        }
        if (byContent != null) return byContent
        if (byExt != null && looksText) return Detection(byExt, Confidence.PARTIAL, "extension")
        if (byExt != null) return Detection(byExt, Confidence.UNSUPPORTED, "binary")
        return Detection(Format.UNKNOWN, Confidence.UNSUPPORTED)
    }

    private fun detectText(text: String, looksText: Boolean): Detection? {
        if (text.isBlank()) return null
        val lower = text.lowercase()

        // Direct single link
        SCHEME_TO_FORMAT.forEach { (scheme, format) ->
            if (lower.startsWith(scheme)) return Detection(format, Confidence.FULL, "scheme")
        }

        // Aggregate links anywhere in the text
        val linkCount = PROXY_SCHEMES.count { lower.contains(it) }
        if (linkCount >= 1) return Detection(Format.RAW_LINKS, Confidence.FULL, "links=$linkCount")

        // Base64 subscription blob (decodes into proxy links)
        if (looksText && isMostlyBase64(text)) {
            val decoded = runCatching {
                java.util.Base64.getMimeDecoder().decode(text.replace("\n", ""))
                    .toString(Charsets.UTF_8).lowercase()
            }.getOrDefault("")
            if (PROXY_SCHEMES.any { decoded.contains(it) }) {
                return Detection(Format.V2RAY_SUB, Confidence.FULL, "base64")
            }
        }

        // Clash YAML
        if (lower.contains("proxies:") && (lower.contains("type:") || lower.contains("server:"))) {
            return Detection(Format.CLASH_YAML, if (lower.contains("port:") && lower.contains("cipher:")) Confidence.FULL else Confidence.PARTIAL, "yaml")
        }

        // sing-box JSON
        if (lower.contains("\"outbounds\"") && lower.contains("\"type\"")) {
            return Detection(Format.SINGBOX_JSON, Confidence.FULL, "singbox")
        }

        // Generic JSON array of profiles
        if (text.trimStart().startsWith("[") && lower.contains("\"server\"")) {
            return Detection(Format.JSON_ARRAY, Confidence.PARTIAL, "json-array")
        }

        // OpenVPN
        if (lower.contains("<ca>") || Regex("(?im)^\\s*(client|remote)\\b").containsMatchIn(text)) {
            return Detection(Format.OPENVPN, if (lower.contains("<ca>")) Confidence.FULL else Confidence.PARTIAL, "openvpn")
        }

        // WireGuard
        if (lower.contains("[interface]") && lower.contains("privatekey")) {
            return Detection(Format.WIREGUARD, Confidence.FULL, "wg")
        }

        // SSH container (dark/tnl/slip family)
        if (lower.contains("sshcore:") || lower.contains("sshtype:") || lower.contains("slipnet")) {
            return Detection(Format.SLIP, Confidence.PARTIAL, "ssh-container")
        }

        // App-specific text containers (ehi/nm/happ/dark/tnl store base64 or header blobs)
        if (looksText && (lower.contains("http-inject") || lower.contains("[ssh]") ||
                lower.contains("payload=") || lower.contains("v2core=") ||
                lower.contains("ssl=true") || lower.contains("proxygroup"))) {
            return Detection(Format.EHI, Confidence.PARTIAL, "container-text")
        }

        return null
    }

    private fun isMostlyBase64(text: String): Boolean {
        val sample = text.take(4096).replace(Regex("\\s"), "")
        if (sample.length < 16) return false
        val valid = sample.count { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        return valid >= sample.length * 0.95
    }
}

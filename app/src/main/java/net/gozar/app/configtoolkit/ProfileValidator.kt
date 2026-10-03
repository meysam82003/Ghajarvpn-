package net.gozar.app.configtoolkit

import java.net.IDN
import java.util.UUID

object ProfileValidator {
    private val supported = setOf("vless", "vmess", "trojan", "shadowsocks", "ss", "socks", "socks5")
    private val networks = setOf("tcp", "ws", "grpc", "http", "h2", "kcp", "quic", "httpupgrade", "xhttp")

    fun validateAll(profiles: List<NormalizedProfile>): ValidationResult {
        if (profiles.isEmpty()) return ValidationResult(listOf(ValidationIssue("profiles", "هیچ پروفایل قابل اتصالی پیدا نشد.")))
        return ValidationResult(profiles.flatMapIndexed { index, profile ->
            validate(profile).issues.map { it.copy(field = "profiles[$index].${it.field}") }
        })
    }

    /**
     * Profiles the engines outside Xray carry (sing-box, helpers, DNS tunnels,
     * WireGuard, Tor, …). ConfigParser already checked their own fields; here
     * only the address is checked, and only where the profile has one.
     */
    private val engineProtocols = setOf("hysteria2", "hysteria", "tuic", "anytls", "ssh", "snell", "openconnect", "naive",
        "shadowtls", "juicity", "sstp", "softether", "amneziawg", "wireguard", "mieru", "brook", "http", "tor", "ikev2",
        "dnstt", "vaydns", "noizdns", "slipstream", "masterdns", "stormdns", "cottendns", "masque", "tailscale", "tailcat")
    private val linkCarried = setOf("tor", "mieru", "brook", "masterdns", "stormdns", "cottendns")

    fun validate(profile: NormalizedProfile): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val protocol = profile.protocol.lowercase()
        if (protocol == net.gozar.app.engine.FullSingBoxProfile.PROTOCOL) {
            val ok = runCatching { net.gozar.app.engine.FullSingBoxProfile.root(profile.toProxyConfig()) }.isSuccess
            return ValidationResult(if (ok) emptyList() else listOf(ValidationIssue("fullConfig", "JSON کامل نامعتبر است.")))
        }
        if (protocol in setOf("xray-full", "npv-preserved")) {
            val ok = runCatching { org.json.JSONObject(profile.toProxyConfig().extra) }.isSuccess
            return ValidationResult(if (ok) emptyList() else listOf(ValidationIssue("container", "ساختار نگه‌داری پروفایل نامعتبر است.")))
        }
        if (protocol == "plugin") {
            val valid = runCatching { net.gozar.app.plugins.PluginProfiles.read(profile.toProxyConfig()) != null }.getOrDefault(false)
            return ValidationResult(if (valid) emptyList() else listOf(ValidationIssue("plugin", "ساختار کانفیگ افزونه نامعتبر است.")))
        }
        if (protocol in engineProtocols) {
            if (protocol !in linkCarried || profile.server.isNotBlank()) {
                if (!validHost(profile.server)) issues += ValidationIssue("server", "آدرس سرور معتبر نیست.")
                if (profile.port !in 1..65535 && protocol !in linkCarried) issues += ValidationIssue("port", "پورت باید بین 1 و 65535 باشد.")
            }
            return ValidationResult(issues)
        }
        if (protocol !in supported) issues += ValidationIssue("protocol", "پروتکل ${profile.protocol} پشتیبانی نمی‌شود.")
        if (!validHost(profile.server)) issues += ValidationIssue("server", "آدرس سرور معتبر نیست.")
        if (profile.port !in 1..65535) issues += ValidationIssue("port", "پورت باید بین 1 و 65535 باشد.")
        if (protocol in setOf("vless", "vmess") && !validUuid(profile.uuid)) {
            issues += ValidationIssue("uuid", "UUID معتبر نیست.")
        }
        if (protocol == "trojan" && profile.password.isBlank()) issues += ValidationIssue("password", "رمز Trojan خالی است.")
        if (protocol in setOf("shadowsocks", "ss")) {
            if (profile.method.isBlank()) issues += ValidationIssue("method", "روش رمزنگاری Shadowsocks خالی است.")
            if (profile.password.isBlank()) issues += ValidationIssue("password", "رمز Shadowsocks خالی است.")
        }
        if (profile.network.lowercase() !in networks) issues += ValidationIssue("network", "Transport ناشناخته است.")
        if (profile.network.equals("ws", true) && profile.path.isNotEmpty() && !profile.path.startsWith('/')) {
            issues += ValidationIssue("path", "مسیر WebSocket باید با / شروع شود.")
        }
        if (profile.security.equals("reality", true)) {
            if (profile.publicKey.isBlank()) issues += ValidationIssue("publicKey", "PublicKey مربوط به REALITY خالی است.")
            if (profile.sni.isBlank()) issues += ValidationIssue("sni", "SNI مربوط به REALITY خالی است.")
        }
        return ValidationResult(issues)
    }

    private fun validUuid(value: String): Boolean = runCatching { UUID.fromString(value); true }.getOrDefault(false)

    private fun validHost(value: String): Boolean {
        val host = value.trim().removePrefix("[").removeSuffix("]")
        if (host.isBlank() || host.any(Char::isWhitespace)) return false
        if (host.contains(':')) {
            // Syntax-only IPv6 check: validation must stay offline and never leak a hostname to DNS.
            return host.count { it == ':' } >= 2 && host.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it in ":." }
        }
        return runCatching {
            val ascii = IDN.toASCII(host)
            ascii.length in 1..253 && ascii.split('.').all { it.isNotBlank() && it.length <= 63 }
        }.getOrDefault(false)
    }
}

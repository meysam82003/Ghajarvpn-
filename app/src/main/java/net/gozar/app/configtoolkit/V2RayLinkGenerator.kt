package net.gozar.app.configtoolkit

import net.gozar.app.ConfigParser
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

object V2RayLinkGenerator {
    fun generate(profile: NormalizedProfile): String = when (profile.protocol.lowercase()) {
        "vless" -> userInfoLink("vless", profile.uuid, profile)
        "trojan" -> userInfoLink("trojan", profile.password, profile)
        "vmess" -> vmess(profile)
        "shadowsocks", "ss" -> shadowsocks(profile)
        "socks", "socks5" -> userInfoLink(
            "socks", listOf(profile.uuid, profile.password).joinToString(":") { pct(it) }, profile, alreadyEncoded = true
        )
        else -> throw ConfigToolkitException.InvalidConfig("برای این پروتکل لینک استاندارد ساخته نمی‌شود.")
    }

    private fun userInfoLink(
        scheme: String,
        credential: String,
        profile: NormalizedProfile,
        alreadyEncoded: Boolean = false
    ): String {
        val user = if (alreadyEncoded) credential else pct(credential)
        return "$scheme://$user@${hostPort(profile.server, profile.port)}${query(profile)}#${pct(profile.name)}"
    }

    private fun query(p: NormalizedProfile): String {
        val values = linkedMapOf<String, String>()
        values["type"] = p.network.ifBlank { "tcp" }
        if (p.security.isNotBlank() && p.security != "none") values["security"] = p.security
        if (p.sni.isNotBlank()) values["sni"] = p.sni
        if (p.host.isNotBlank()) values["host"] = p.host
        if (p.path.isNotBlank()) values["path"] = p.path
        if (p.alpn.isNotBlank()) values["alpn"] = p.alpn
        if (p.fingerprint.isNotBlank()) values["fp"] = p.fingerprint
        if (p.flow.isNotBlank()) values["flow"] = p.flow
        if (p.publicKey.isNotBlank()) values["pbk"] = p.publicKey
        if (p.shortId.isNotBlank()) values["sid"] = p.shortId
        if (p.spiderX.isNotBlank()) values["spx"] = p.spiderX
        if (p.grpcServiceName.isNotBlank()) values["serviceName"] = p.grpcServiceName
        if (p.authority.isNotBlank()) values["authority"] = p.authority
        if (p.allowInsecure) values["allowInsecure"] = "1"
        if (p.protocol.equals("vless", true)) values["encryption"] = p.encryption.ifBlank { "none" }
        return if (values.isEmpty()) "" else values.entries.joinToString("&", prefix = "?") { "${pct(it.key)}=${pct(it.value)}" }
    }

    private fun vmess(p: NormalizedProfile): String {
        val json = JSONObject()
            .put("v", "2")
            .put("ps", p.name)
            .put("add", p.server)
            .put("port", p.port.toString())
            .put("id", p.uuid)
            .put("aid", p.alterId.toString())
            .put("scy", p.encryption.ifBlank { "auto" })
            .put("net", p.network)
            .put("type", "none")
            .put("host", p.host.ifBlank { p.authority })
            .put("path", p.path.ifBlank { p.grpcServiceName })
            .put("tls", if (p.security == "none") "" else p.security)
            .put("sni", p.sni)
            .put("alpn", p.alpn)
            .put("fp", p.fingerprint)
        return "vmess://" + Base64.getEncoder().encodeToString(json.toString().toByteArray())
    }

    private fun shadowsocks(p: NormalizedProfile): String {
        val credentials = "${p.method}:${p.password}"
        val user = Base64.getUrlEncoder().withoutPadding().encodeToString(credentials.toByteArray())
        return "ss://$user@${hostPort(p.server, p.port)}#${pct(p.name)}"
    }

    private fun hostPort(host: String, port: Int): String {
        val bare = host.removePrefix("[").removeSuffix("]")
        return if (bare.contains(':')) "[$bare]:$port" else "$bare:$port"
    }

    private fun pct(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

object RoundTripVerifier {
    private val critical = listOf<(NormalizedProfile) -> Any?>(
        { it.protocol.lowercase() }, { it.server.removePrefix("[").removeSuffix("]").lowercase() }, { it.port },
        { it.uuid }, { it.password }, { it.method }, { it.network.lowercase() },
        { it.security.lowercase() }, { it.sni }, { it.host.ifBlank { it.authority } },
        { it.path.ifBlank { it.grpcServiceName } }, { it.flow }, { it.publicKey }, { it.shortId }
    )

    fun verify(profile: NormalizedProfile): Result<String> = runCatching {
        val validation = ProfileValidator.validate(profile)
        require(validation.valid) { validation.issues.joinToString("; ") { it.message } }
        val link = V2RayLinkGenerator.generate(profile)
        val reparsed = ConfigParser.parse(link)
            ?: throw ConfigToolkitException.InvalidConfig("Parser قاجار لینک تولیدشده را نپذیرفت.")
        val normalized = NormalizedProfile.from(reparsed, profile.sourceFormat)
        val mismatch = critical.indices.firstOrNull { critical[it](profile) != critical[it](normalized) }
        if (mismatch != null) throw ConfigToolkitException.InvalidConfig("Round-trip برای یکی از فیلدهای حیاتی یکسان نبود.")
        link
    }
}

package net.gozar.app

import org.json.JSONArray
import org.json.JSONObject

/** Direction names describe the application-facing engine first, not startup order. */
object AetherTorPolicy {
    const val TOR_PORT = 19150
    const val CONTROL_PORT = 19151
    val modes = setOf("tor-over-aether", "aether-over-tor")
    fun active(raw: String): Boolean = raw.isNotBlank() && runCatching { JSONObject(raw).optString("core") in modes }.getOrDefault(false)
    fun validate(o: OblivionOptions) {
        if (o.core !in modes) return
        require(o.text("routingMode") == "vpn" && !o.flag("allowLan") && !o.flag("bypassSelected")) { "زنجیره به VPN و listener محلی بدون bypass نیاز دارد" }
        require(o.text("routeDirect").isBlank()) { "زنجیره مسیر مستقیم نمی‌پذیرد" }
        require(o.text("torCountry").isBlank() || o.text("torCountry").matches(Regex("[A-Za-z]{2}"))) { "کد کشور Tor نامعتبر است" }
        bridgeLines(o)
        require(setOf(o.aetherPort, o.aetherPort + 1).intersect(setOf(TOR_PORT, CONTROL_PORT, TorController.SOCKS_PORT, TorController.CONTROL_PORT, TorController.BRIDGE_PORT, net.gozar.app.sharing.PhoneSharing.XRAY_PORT)).isEmpty()) { "تداخل پورت زنجیره" }
        require(o.core != "aether-over-tor") { "Aether H2 روی Tor فعلاً مسدود است: apifront در باینری پین‌شده DNS سیستم دارد؛ patch موتور و تأیید باینری بدون نشت لازم است" }
    }
    fun bridgeLines(o: OblivionOptions): List<String> {
        val raw=o.text("torBridges"); require(raw.length <= 16384) { "Bridge size limit" }
        val lines=raw.lines().map { it.trim().removePrefix("Bridge ") }.filter { it.isNotBlank() }
        require(lines.size <= 32)
        lines.forEach { line ->
            val parts=line.split(Regex("\\s+"))
            require(parts.size in 1..2 && (parts.size == 1 || parts[1].matches(Regex("[A-Fa-f0-9]{40}")))) { "فقط bridge ساده با IP:port و fingerprint معتبر در این زنجیره مجاز است؛ PTها تأیید نشده‌اند" }
            val endpoint=parts[0]; val host=endpoint.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
            val port=endpoint.substringAfterLast(':').toIntOrNull()
            require(port in 1..65535 && (host.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) && host.split('.').all { it.toInt() in 0..255 } || host.contains(':') && endpoint.startsWith("[") && endpoint.contains("]:") && host.all { it in "0123456789abcdefABCDEF:" } && runCatching { java.net.InetAddress.getByName(host) is java.net.Inet6Address }.getOrDefault(false))) { "Bridge باید آدرس IP عددی معتبر داشته باشد" }
        }
        return lines
    }
    fun finalPort(o: OblivionOptions) = if (o.core == "tor-over-aether") TOR_PORT else o.aetherPort
    /** Keep user settings stored, but reject incompatible intent before producing a chain.
     * TCP DNS travels through the same SOCKS; general UDP is explicitly blocked. */
    fun constrain(root: JSONObject, o: OblivionOptions) {
        validate(o)
        root.remove("fakedns")
        root.put("dns", JSONObject().put("tag", "chain-dns").put("queryStrategy", "UseIP")
            .put("servers", JSONArray().put("tcp://1.1.1.1:53")))
        root.put("outbounds", JSONArray()
            .put(JSONObject().put("tag", "proxy").put("protocol", "socks").put("settings", JSONObject().put("servers", JSONArray()
                .put(JSONObject().put("address", "127.0.0.1").put("port", finalPort(o))))))
            .put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
            .put(JSONObject().put("tag", "block").put("protocol", "blackhole")))
        root.put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", JSONArray()
            .put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("chain-dns")).put("outboundTag", "proxy"))
            .put(JSONObject().put("type", "field").put("port", "53").put("outboundTag", "dns-out"))
            .put(JSONObject().put("type", "field").put("network", "udp").put("outboundTag", "block"))
            .put(JSONObject().put("type", "field").put("network", "tcp").put("outboundTag", "proxy"))))
    }
}

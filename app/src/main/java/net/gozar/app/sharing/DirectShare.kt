package net.gozar.app.sharing

import net.gozar.app.ConfigShare
import net.gozar.app.ProxyConfig
import net.gozar.app.engine.RemovedCores
import net.gozar.app.engine.SingBoxConfig
import org.json.JSONObject

/**
 * Sharing one saved config with another device, in a format that device's
 * apps really read. Nothing here re-encodes a protocol into a format that
 * cannot carry it: a protocol without a standard link gets no link, a
 * WireGuard profile with Xray-only "reserved" bytes gets no standard .conf,
 * and every export says which apps open it.
 */
object DirectShare {
    enum class Target(val label: String) {
        ANDROID("Android"), IPHONE("iPhone"), IPAD("iPad"), WINDOWS("Windows"),
        MACOS("macOS"), LINUX("Linux"), ROUTER("Router (OpenWrt)")
    }

    enum class Kind { LINK, WIREGUARD_CONF, AMNEZIA_CONF, SINGBOX_JSON, GHAJAR_LINK }

    data class Export(
        val kind: Kind,
        val title: String,
        val text: String,
        val filename: String,
        val mime: String,
        /** Short enough and a single line: offered as a QR code. */
        val qr: Boolean,
        val apps: List<String>,
        val steps: List<String>
    )

    /** Protocols whose share link is a de-facto standard other clients read. */
    private val STANDARD_LINKS = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic", "hysteria", "anytls")

    fun exports(c: ProxyConfig, target: Target): List<Export> {
        // Locked configs and configs received through GSB2 are not passed on.
        if (c.locked || c.extraJson().has("gsb2")) return emptyList()
        if (RemovedCores.isRemoved(c)) return listOfNotNull(ghajarLink(c))
        val out = mutableListOf<Export>()
        val link = runCatching { ConfigShare.toLink(c) }.getOrDefault("")
        if (c.protocol in STANDARD_LINKS && link.isNotBlank() && c.chainId.isBlank()) {
            out += Export(Kind.LINK, "لینک کانفیگ", link, "${safe(c.name)}.txt", "text/plain", link.length <= QR_MAX,
                linkApps(target, c.protocol), linkSteps(target))
        }
        if (c.protocol == "wireguard" && c.reserved.isBlank()) wireguardConf(c)?.let { conf ->
            out += Export(Kind.WIREGUARD_CONF, "فایل WireGuard", conf, "${safe(c.name)}.conf", "text/plain", conf.length <= QR_MAX,
                listOf(if (target == Target.ROUTER) "OpenWrt: luci-proto-wireguard" else "WireGuard (رسمی)"),
                listOf("برنامهٔ WireGuard را باز کن.", "Import from file یا Scan from QR code را بزن.", "این کلید را هم‌زمان روی دو دستگاه به کار نبر؛ برای دستگاه دوم از مدیر سرور کلید جدا بگیر."))
        }
        if (c.protocol == "amneziawg") c.extraJson().optString("conf").takeIf { it.isNotBlank() }?.let { conf ->
            out += Export(Kind.AMNEZIA_CONF, "فایل AmneziaWG", conf, "${safe(c.name)}.conf", "text/plain", false,
                listOf("AmneziaWG", "AmneziaVPN"),
                listOf("WireGuard معمولی این فایل را نمی‌فهمد؛ برنامهٔ AmneziaWG لازم است.", "در برنامه Import from file را بزن و این فایل را انتخاب کن."))
        }
        if (target in setOf(Target.WINDOWS, Target.MACOS, Target.LINUX, Target.ROUTER)) singBoxJson(c)?.let { json ->
            out += Export(Kind.SINGBOX_JSON, "کانفیگ کامل sing-box", json, "${safe(c.name)}.singbox.json", "application/json", false,
                listOf("sing-box (CLI یا GUI)"),
                listOf("فایل را ذخیره کن.", "با sing-box اجرا کن: sing-box run -c ${safe(c.name)}.singbox.json", "پراکسی SOCKS5 روی 127.0.0.1:2080 باز می‌شود؛ برنامه‌ها یا سیستم را روی آن تنظیم کن."))
        }
        ghajarLink(c)?.let { out += it }
        return out
    }

    private fun ghajarLink(c: ProxyConfig): Export? {
        val link = runCatching { ConfigShare.toLink(c) }.getOrDefault("")
        if (link.isBlank()) return null
        return Export(Kind.GHAJAR_LINK, "برای قاجار VPN در دستگاه دیگر", link, "${safe(c.name)}.txt", "text/plain",
            link.length <= QR_MAX, listOf("قاجار VPN"),
            listOf("در قاجار VPN دستگاه دیگر، افزودن سرور ← اسکن QR یا چسباندن از کلیپ‌بورد."))
    }

    private fun linkApps(t: Target, protocol: String): List<String> = when (t) {
        Target.ANDROID -> listOf("v2rayNG", "Hiddify", "NekoBox")
        Target.IPHONE, Target.IPAD -> listOf("Streisand", "V2Box", "Hiddify")
        Target.WINDOWS -> listOf("v2rayN", "Hiddify")
        Target.MACOS -> listOf("Hiddify", "V2Box", "FoXray")
        Target.LINUX -> listOf("Hiddify", "v2rayN (Linux)")
        Target.ROUTER -> listOf("OpenWrt Passwall / Passwall2")
    }.let { apps -> if (protocol in setOf("tuic", "anytls", "hysteria")) apps.filter { it in setOf("Hiddify", "NekoBox", "Streisand", "V2Box", "OpenWrt Passwall / Passwall2") } else apps }

    private fun linkSteps(t: Target): List<String> = when (t) {
        Target.ROUTER -> listOf("در LuCI به Services ← Passwall ← Node List برو.", "Add the node via link را بزن و لینک را بچسبان.", "Node را در Basic Settings انتخاب و Save & Apply کن.")
        else -> listOf("لینک را کپی کن یا QR را با دستگاه دیگر اسکن کن.", "در برنامهٔ مقصد «Import from clipboard» یا «+» ← «اسکن QR» را بزن.", "سرور اضافه‌شده را انتخاب و وصل شو.")
    }

    /** A standard WireGuard .conf, only when every field is standard. */
    fun wireguardConf(c: ProxyConfig): String? {
        if (c.privateKey.isBlank() || c.publicKey.isBlank() || c.localAddress.isBlank() || c.port !in 1..65535) return null
        if (listOf(c.address, c.localAddress, c.privateKey, c.publicKey, c.password).any { v -> v.any { it == '\n' || it == '\r' } }) return null
        val host = if (c.address.contains(':')) "[${c.address}]" else c.address
        return buildString {
            append("[Interface]\nPrivateKey = ${c.privateKey}\nAddress = ${c.localAddress}\n")
            if (c.mtu > 0) append("MTU = ${c.mtu}\n")
            append("DNS = 1.1.1.1\n\n[Peer]\nPublicKey = ${c.publicKey}\n")
            if (c.password.isNotBlank()) append("PresharedKey = ${c.password}\n")
            append("Endpoint = $host:${c.port}\nAllowedIPs = 0.0.0.0/0, ::/0\n")
        }
    }

    /** A runnable sing-box config, for protocols sing-box carries alone (no helper process). */
    fun singBoxJson(c: ProxyConfig): String? {
        if (!SingBoxConfig.handles(c)) return null
        val spec = runCatching { SingBoxConfig.spec(c) }.getOrNull() ?: return null
        if (JSONObject(spec).has("sidecar")) return null
        return runCatching { JSONObject(SingBoxConfig.full(spec, 2080)).toString(2) }.getOrNull()
    }

    /** Same ceiling as ConfigShare.QR_MAX_CHARS: past it a phone camera cannot read the code. */
    const val QR_MAX = 2300

    private fun safe(name: String) = name.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").take(40).ifBlank { "ghajar" }
}

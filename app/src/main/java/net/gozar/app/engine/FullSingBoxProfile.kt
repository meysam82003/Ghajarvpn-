package net.gozar.app.engine

import net.gozar.app.ProxyConfig
import net.gozar.app.ConfigSource
import net.gozar.app.configtoolkit.BoundedJson
import org.json.JSONArray
import org.json.JSONObject

/** Full text + metadata are data in the normal encrypted profile/backup model. */
object FullSingBoxProfile {
    const val PROTOCOL = "singbox-full"
    fun isFull(c: ProxyConfig) = c.protocol == PROTOCOL
    fun create(raw: String, name: String = "", metadata: JSONObject = JSONObject(), source: ConfigSource = ConfigSource.PERSONAL): ProxyConfig {
        BoundedJson.objectValue(raw)
        return ProxyConfig(name = name.ifBlank { "پروفایل sing-box" }, protocol = PROTOCOL, address = "", port = 0,
            extra = JSONObject().put("fullConfigVersion", 1).put("rawConfig", raw).put("profileMetadata", metadata).toString(), source = source)
    }
    fun raw(c: ProxyConfig): String { require(isFull(c)); return JSONObject(c.extra).getString("rawConfig") }
    fun root(c: ProxyConfig) = BoundedJson.objectValue(raw(c))
    fun details(c: ProxyConfig): String {
        val r = root(c); val a = r.optJSONArray("inbounds") ?: JSONArray()
        return "پروفایل sing-box · نوع: Full Config\nتعداد Outbound: ${r.optJSONArray("outbounds")?.length() ?: 0}\n" +
            "DNS: ${r.has("dns")} · TUN: ${(0 until a.length()).any { a.optJSONObject(it)?.optString("type") == "tun" }} · Routing: ${r.has("route")}" +
            (blockReason(c)?.let { "\nقابل ذخیره؛ اتصال آماده نیست: $it" } ?: "\nاعتبارسنجی موتور و آزمون اتصال لازم است.")
    }
    /** Fail closed before spawning: this CLI has no Android platform TUN/FD contract. */
    fun blockReason(c: ProxyConfig): String? {
        val r = try { root(c) } catch (_: Exception) { return "ساختار Full Config نامعتبر است." }
        val inbound = r.optJSONArray("inbounds") ?: JSONArray()
        if ((0 until inbound.length()).any { inbound.optJSONObject(it)?.optString("type") == "tun" })
            return "TUN این پروفایل به تحویل FD و PlatformInterface اندروید در sing-box نیاز دارد؛ CLI فعلی این قرارداد را ندارد. TUN یا قوانین آن حذف نشده‌اند."
        if (inbound.length() > 1) return "چند inbound نیاز به تخصیص پورت و مالکیت نشست مستقل دارد."
        if (inbound.length() == 1) {
            val i = inbound.optJSONObject(0) ?: return "inbound نامعتبر است."
            if (i.optString("type") !in setOf("socks", "mixed") || i.optString("listen") !in setOf("127.0.0.1", "::1") || (i.optJSONArray("users")?.length() ?: 0) != 0)
                return "رابط میزبان فقط یک SOCKS/mixed روی loopback بدون حساب inbound را پشتیبانی می‌کند."
        }
        // Files and controllers must first be imported into a dedicated sandbox. No arbitrary path opens.
        fun unsafe(v: Any?): Boolean = when (v) {
            is JSONObject -> v.keys().asSequence().any { k ->
                ((k in setOf("certificate_path", "key_path", "client_certificate_path", "client_key_path", "directory", "state_directory", "external_ui", "external_ui_download_url", "external_controller", "output", "include", "script", "command") ||
                    k == "path" && v.optString("type") == "local") && v.opt(k)?.toString()?.isNotBlank() == true) || unsafe(v.opt(k))
            }
            is JSONArray -> (0 until v.length()).any { unsafe(v.opt(it)) }
            else -> false
        }
        if ((r.optJSONArray("services")?.length() ?: 0) > 0) return "سرویس‌های جانبی Full Config به قرارداد منابع مستقل نیاز دارند."
        if (unsafe(r)) return "وابستگی فایل یا کنترلر خارجی به sandbox مستقل نیاز دارد؛ مسیر دلخواه اجرا نمی‌شود."
        if (r.optJSONObject("experimental")?.optJSONObject("cache_file")?.optBoolean("enabled") == true)
            return "cache_file به فضای ذخیرهٔ اختصاصی نشست نیاز دارد."
        return null
    }
    fun spec(c: ProxyConfig): String {
        require(blockReason(c) == null) { blockReason(c).orEmpty() }
        return JSONObject().put("fullConfig", raw(c)).toString()
    }
    fun runtime(raw: String, port: Int, sharingPort: Int): String {
        val c = create(raw); require(blockReason(c) == null) { blockReason(c).orEmpty() }
        require(sharingPort == 0) { "Full Config has no tunnel-only sharing contract" }
        val r = root(c); val inbounds = r.optJSONArray("inbounds") ?: JSONArray().also { r.put("inbounds", it) }
        if (inbounds.length() == 0) inbounds.put(JSONObject().put("type", "socks").put("tag", "ghajar-host"))
        // Only the host-owned socket binding changes; outbound/DNS/route/group semantics stay intact.
        inbounds.getJSONObject(0).put("listen", "127.0.0.1").put("listen_port", port)
        return r.toString()
    }
}

package net.gozar.app.engine

import net.gozar.app.ProxyConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * A complete sing-box configuration kept as one profile (from a `.bpf`
 * profile file). Nothing in it is thrown away: DNS, outbounds, endpoints,
 * selector/urltest groups, route rules and rule sets all run as written.
 *
 * Only what cannot work inside this app is adjusted at run time, never in the
 * stored profile: the inbounds become this app's own local SOCKS5 inbound
 * (zeptun owns the device's tun, so a `tun` inbound cannot open a second one),
 * `experimental` (Clash API, cache file paths outside the app's directory) is
 * dropped, and `auto_detect_interface` is turned off because the app is
 * already excluded from its own VPN.
 */
object SingBoxFull {
    const val PROTOCOL = "singbox-full"
    const val MAX_CONFIG_CHARS = 2 * 1024 * 1024
    private const val INBOUND_TAG = "socks-in"

    class Invalid(message: String) : IllegalArgumentException(message)

    /** Builds the stored profile, or throws [Invalid] with the reason. */
    fun profile(name: String, config: String): ProxyConfig {
        if (config.length > MAX_CONFIG_CHARS) throw Invalid("پیکربندی sing-box بیش از حد بزرگ است.")
        val root = runCatching { JSONObject(config) }.getOrNull() ?: throw Invalid("پیکربندی sing-box یک JSON معتبر نیست.")
        val proxies = proxies(root)
        if (proxies.isEmpty()) throw Invalid("پیکربندی sing-box هیچ outbound یا endpoint قابل اتصالی ندارد.")
        val first = proxies.firstOrNull { it.optString("server").isNotBlank() }
        return ProxyConfig(
            name = name.ifBlank { "sing-box" },
            protocol = PROTOCOL,
            address = first?.optString("server").orEmpty(),
            port = first?.optInt("server_port", 0) ?: 0,
            extra = JSONObject().put("fullConfig", root.toString()).toString()
        )
    }

    fun configOf(c: ProxyConfig): JSONObject? =
        c.extraJson().optString("fullConfig").takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** The stored config with this app's inbound in place of the file's own. */
    fun runnable(full: JSONObject, socksPort: Int, logLevel: String): String {
        val root = JSONObject(full.toString())
        root.put("log", JSONObject().put("level", logLevel).put("timestamp", false))
        root.put("inbounds", JSONArray().put(JSONObject().put("type", "socks").put("tag", INBOUND_TAG)
            .put("listen", "127.0.0.1").put("listen_port", socksPort)))
        root.remove("experimental")
        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        route.remove("auto_detect_interface")
        route.remove("default_interface")
        if (root.optJSONObject("dns") == null) {
            root.put("dns", JSONObject().put("servers", JSONArray().put(JSONObject().put("type", "local").put("tag", "local"))))
            if (!route.has("default_domain_resolver")) route.put("default_domain_resolver", JSONObject().put("server", "local"))
        }
        return root.toString()
    }

    private val NON_PROXY = setOf("direct", "block", "dns", "selector", "urltest")

    private fun proxies(root: JSONObject): List<JSONObject> = listOf("outbounds", "endpoints").flatMap { key ->
        val a = root.optJSONArray(key) ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("type") !in NON_PROXY && it.optString("type").isNotBlank() }
    }
}

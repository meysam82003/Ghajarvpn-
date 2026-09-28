package net.gozar.app.engine

import net.gozar.app.ProxyConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns a [ProxyConfig] into a sing-box configuration.
 *
 * Only the protocols Xray cannot carry are routed here; everything Xray
 * already connects keeps connecting through Xray. Every field name below was
 * checked against the option structs of the pinned sing-box source
 * (scripts/build-singbox.sh, commit 8330820, v1.15.0-alpha.6):
 * option/tuic.go, hysteria.go, anytls.go, ssh.go, snell.go, openconnect.go.
 *
 * The result is a local SOCKS5 proxy on 127.0.0.1:[socksPort]; zeptun owns
 * the tun and forwards into it (docs/VPN_ENGINE_ARCHITECTURE.md rule 1).
 */
object SingBoxConfig {

    /** Protocols this app sends to sing-box. ShadowsocksR is not here: the pinned source registers it only as a removed stub. */
    val PROTOCOLS = setOf("tuic", "hysteria", "anytls", "ssh", "snell", "openconnect",
        "dnstt", "vaydns", "noizdns", "masterdns", "stormdns", "cottendns", "slipstream",
        "amneziawg", "mieru", "brook", "juicity", "naive", "shadowtls", "sstp", "softether")

    /** DNS tunnels whose server forwards to a SOCKS5 or SSH upstream. */
    val DNSTT_FAMILY = setOf("dnstt", "vaydns", "noizdns", "slipstream")

    /** DNS tunnels whose client serves SOCKS5 itself. */
    val MASTERDNS_FAMILY = setOf("masterdns", "stormdns", "cottendns")

    /** Protocols carried as sing-box endpoints rather than outbounds. */
    private val ENDPOINTS = setOf("openconnect")

    fun handles(config: ProxyConfig): Boolean = config.protocol in PROTOCOLS

    /**
     * The proxy part only, as passed to the service in an intent extra:
     * `{"outbound": {...}}` or `{"endpoint": {...}}`. Null when [config] is not
     * a sing-box protocol.
     */
    fun spec(config: ProxyConfig): String? {
        if (!handles(config)) return null
        val proxy = proxy(config)
        val out = JSONObject().put(if (config.protocol in ENDPOINTS) "endpoint" else "outbound", proxy)
        if (config.protocol == "shadowtls") out.put("extraOutbounds", org.json.JSONArray().put(shadowTlsOut(config)))
        // Engines that run a helper process first (see Sidecars): the runner
        // starts it and points the outbound at its local port.
        sidecar(config)?.let { out.put("sidecar", it) }
        return out.toString()
    }

    /** The complete configuration for `sing-box run`, with the SOCKS inbound on [socksPort]. */
    fun full(spec: String, socksPort: Int, logLevel: String = "info"): String {
        val s = JSONObject(spec)
        val root = JSONObject()
            .put("log", JSONObject().put("level", logLevel).put("timestamp", false))
            // A server given by name needs a resolver in this sing-box
            // version (common/dialer/dialer.go: "missing domain resolver").
            // The local resolver is the system one, outside the tun because
            // this app is excluded from its own VPN.
            .put("dns", JSONObject().put("servers", JSONArray().put(JSONObject().put("type", "local").put("tag", "local"))))
            .put("inbounds", JSONArray().put(
                JSONObject().put("type", "socks").put("tag", "socks-in")
                    .put("listen", "127.0.0.1").put("listen_port", socksPort)
            ))
        val outbounds = JSONArray()
        s.optJSONObject("outbound")?.let { outbounds.put(it) }
        s.optJSONArray("extraOutbounds")?.let { a -> for (i in 0 until a.length()) outbounds.put(a.get(i)) }
        outbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))
        root.put("outbounds", outbounds)
        s.optJSONObject("endpoint")?.let { root.put("endpoints", JSONArray().put(it)) }
        root.put("route", JSONObject()
            .put("final", "proxy")
            .put("default_domain_resolver", JSONObject().put("server", "local")))
        remoteDns(root, DnsTunnelPrefs.current.remoteDns)
        return root.toString()
    }

    /**
     * Settings -> DNS protocols -> remote DNS: name lookups coming through the
     * tunnel are answered by this resolver, reached through the proxy, instead
     * of wherever the app sent them. Server names are still resolved locally.
     */
    internal fun remoteDns(root: JSONObject, server: String) {
        val ip = server.trim()
        if (!Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$").matches(ip) && !(ip.contains(':') && ip.all { it.isLetterOrDigit() || it == ':' })) return
        val dns = root.getJSONObject("dns")
        dns.getJSONArray("servers").put(JSONObject().put("type", "udp").put("tag", "remote").put("server", ip).put("detour", "proxy"))
        dns.put("final", "remote")
        root.getJSONObject("route").put("rules", JSONArray()
            .put(JSONObject().put("action", "sniff"))
            .put(JSONObject().put("protocol", "dns").put("action", "hijack-dns")))
    }

    internal fun proxy(c: ProxyConfig): JSONObject {
        val o = JSONObject().put("tag", "proxy")
        when (c.protocol) {
            "tuic" -> {
                o.put("type", "tuic").server(c)
                    .put("uuid", c.uuid)
                    .putIf("password", c.password)
                    .putIf("congestion_control", c.method.takeIf { it in setOf("cubic", "new_reno", "bbr") })
                    .putIf("udp_relay_mode", c.mode.takeIf { it in setOf("native", "quic") })
                    .put("tls", tls(c, forceOn = true))
            }
            "hysteria" -> {
                o.put("type", "hysteria").server(c)
                    .putIf("auth_str", c.password)
                    .put("up_mbps", c.hyUpMbps.takeIf { it > 0 } ?: 10)
                    .put("down_mbps", c.hyDownMbps.takeIf { it > 0 } ?: 50)
                    .putIf("obfs", c.hyObfsPassword.ifBlank { c.hyObfs.takeIf { it != "xplus" }.orEmpty() })
                    .put("tls", tls(c, forceOn = true))
            }
            "anytls" -> {
                o.put("type", "anytls").server(c)
                    .put("password", c.password)
                    .put("tls", tls(c, forceOn = true))
            }
            "naive" -> {
                // Needs sing-box built with with_naive_outbound (Cronet); see scripts/build-singbox.sh.
                o.put("type", "naive").server(c)
                    .putIf("username", c.uuid)
                    .putIf("password", c.password)
                    .put("tls", JSONObject().put("enabled", true).put("server_name", c.sni.ifBlank { c.address }))
                if (c.mode == "quic") o.put("quic", true)
            }
            "shadowtls" -> {
                // The Shadowsocks stream rides inside ShadowTLS: the proxy
                // outbound is Shadowsocks with the ShadowTLS outbound as its
                // detour (sing-box's documented pairing).
                val ss = c.extraJson().optJSONObject("ss") ?: JSONObject()
                o.put("type", "shadowsocks").put("method", ss.optString("method")).put("password", ss.optString("password"))
                    .put("detour", "shadowtls-out")
            }
            "amneziawg", "mieru", "brook", "juicity", "sstp", "softether" -> {
                o.put("type", "socks").put("server", "127.0.0.1").put("server_port", 0).put("version", "5")
            }
            "ssh" -> {
                o.put("type", "ssh").server(c)
                    .put("user", c.uuid.ifBlank { "root" })
                    .putIf("password", c.password)
                // Through a disguise the helper carries the bytes; sing-box
                // still speaks SSH (and checks the host key) end to end.
                if (sshTransport(c) != null) o.put("server", "127.0.0.1").put("server_port", 0)
                if (c.privateKey.isNotBlank()) o.put("private_key", JSONArray().put(c.privateKey))
                if (c.publicKey.isNotBlank()) o.put("host_key", JSONArray().put(c.publicKey))
            }
            "snell" -> {
                o.put("type", "snell").server(c)
                    .put("psk", c.password)
                // option/snell.go: a client is version 4 (with obfs) or 6; no other value parses.
                val version = if (c.alterId == 6) 6 else 4
                o.put("version", version)
                if (version == 4) {
                    o.putIf("obfs_mode", c.hyObfs.takeIf { it == "http" || it == "tls" })
                    o.putIf("obfs_host", c.host)
                }
            }
            "openconnect" -> {
                val server = if (c.port == 443 || c.port <= 0) c.address else "${c.address}:${c.port}"
                o.put("type", "openconnect")
                    .put("server", server)
                    .put("flavor", c.mode.takeIf { it in setOf("anyconnect", "gp", "fortinet", "f5", "pulse", "nc") } ?: "anyconnect")
                    .putIf("username", c.uuid)
                    .putIf("password", c.password)
                val x = c.extraJson()
                o.putIf("auth_group", x.optString("authGroup"))
                    .putIf("reported_os", x.optString("reportedOs").takeIf { it in setOf("linux", "linux-64", "win", "mac-intel", "android", "apple-ios") })
                    .putIf("user_agent", x.optString("userAgent"))
                if (c.mtu in 576..9000) o.put("mtu", c.mtu)
                x.optInt("reconnect", 0).takeIf { it > 0 }?.let { o.put("reconnect_timeout", "${it}s") }
                if (x.optBoolean("noUdp")) o.put("no_udp", true)
                if (x.optBoolean("ipv6Off")) o.put("ipv6_disabled", true)
                val t = JSONObject()
                if (c.allowInsecure) t.put("insecure", true)
                if (c.sni.isNotBlank()) t.put("server_name", c.sni)
                if (c.pinnedCertSha256.isNotBlank()) t.put("peer_fingerprint", JSONArray().put(c.pinnedCertSha256))
                x.optString("clientCert").takeIf { it.contains("BEGIN CERTIFICATE") }?.let { t.put("client_certificate", JSONArray().put(it)) }
                x.optString("clientKey").takeIf { it.contains("PRIVATE KEY") }?.let { t.put("client_key", JSONArray().put(it)) }
                if (t.length() > 0) o.put("tls", t)
            }
            "masterdns", "stormdns", "cottendns" -> {
                o.put("type", "socks").put("server", "127.0.0.1").put("server_port", 0).put("version", "5")
            }
            "dnstt", "vaydns", "noizdns", "slipstream" -> {
                // What the tunnel server forwards to: an SSH server (the
                // common setup) or a SOCKS5 proxy.
                if (c.method == "ssh") {
                    o.put("type", "ssh").put("server", "127.0.0.1").put("server_port", 0)
                        .put("user", c.uuid.ifBlank { "root" })
                        .putIf("password", c.password)
                    if (c.privateKey.isNotBlank()) o.put("private_key", JSONArray().put(c.privateKey))
                } else {
                    o.put("type", "socks").put("server", "127.0.0.1").put("server_port", 0).put("version", "5")
                        .putIf("username", c.uuid)
                        .putIf("password", c.password)
                }
            }
            else -> throw IllegalArgumentException("not a sing-box protocol: ${c.protocol}")
        }
        return o
    }

    /** The helper process a profile needs in front of sing-box, or null. */
    internal fun sidecar(c: ProxyConfig): JSONObject? = when (c.protocol) {
        in DNSTT_FAMILY -> dnstt(c).put("kind", c.protocol).apply {
            val x = c.extraJson()
            listOf("recordType", "dnsttCompat", "maxQnameLen", "clientIdSize", "noiz", "stealth", "authoritative", "cc", "cert").forEach { k -> if (x.has(k)) put(k, x.get(k)) }
        }
        "ssh" -> sshTransport(c)?.let { t ->
            JSONObject(t.toString()).put("kind", "sshtransport").put("host", c.address).put("port", c.port)
        }
        "amneziawg" -> JSONObject().put("kind", "awg").put("conf", c.extraJson().optString("conf"))
        "mieru", "brook" -> JSONObject().put("kind", c.protocol).put("url", c.extraJson().optString("url"))
        "softether" -> c.extraJson().let { x ->
            JSONObject().put("kind", "softether").put("server", (if (c.address.contains(':')) "[${c.address}]" else c.address) + ":" + c.port)
                .put("hub", x.optString("hub").ifBlank { "DEFAULT" }).put("user", c.uuid).put("password", c.password)
                .put("plain", x.optBoolean("plain")).put("sni", c.sni).put("allowInsecure", c.allowInsecure).put("pin", c.pinnedCertSha256)
                .put("ip", x.optString("ip")).put("gw", x.optString("gw")).put("dns", x.optString("dns"))
                .put("mtu", c.mtu.takeIf { it in 576..1500 } ?: 1400)
        }
        "sstp" -> JSONObject().put("kind", "sstp").put("server", (if (c.address.contains(':')) "[${c.address}]" else c.address) + ":" + c.port)
            .put("user", c.uuid).put("password", c.password).put("sni", c.sni).put("auth", c.method.ifBlank { "auto" })
            .put("allowInsecure", c.allowInsecure).put("pin", c.pinnedCertSha256).put("mtu", c.mtu.takeIf { it in 576..1500 } ?: 1400)
        "juicity" -> JSONObject().put("kind", "juicity").put("server", (if (c.address.contains(':')) "[${c.address}]" else c.address) + ":" + c.port)
            .put("uuid", c.uuid).put("password", c.password).put("sni", c.sni).put("allowInsecure", c.allowInsecure)
            .put("cc", c.method).put("pin", c.pinnedCertSha256)
        in MASTERDNS_FAMILY -> {
            val x = c.extraJson()
            val first = if (c.address.isBlank()) "" else (if (c.address.contains(':')) "[${c.address}]" else c.address) + ":" + (if (c.port in 1..65535) c.port else 53)
            JSONObject().put("kind", c.protocol).put("domain", c.host).put("key", c.password)
                .put("enc", x.optInt("enc", 1)).put("transport", c.mode)
                .put("resolvers", (listOf(first) + x.optString("resolvers").split(',', '\n', ' ')).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(","))
        }
        else -> null
    }

    private fun shadowTlsOut(c: ProxyConfig): JSONObject {
        val t = JSONObject().put("enabled", true).put("server_name", c.sni.ifBlank { c.address })
        if (c.allowInsecure) t.put("insecure", true)
        if (c.fingerprint.isNotBlank()) t.put("utls", JSONObject().put("enabled", true).put("fingerprint", c.fingerprint))
        return JSONObject().put("type", "shadowtls").put("tag", "shadowtls-out").server(c)
            .put("version", if (c.alterId in 1..3) c.alterId else 3).putIf("password", c.password).put("tls", t)
    }

    /** The SSH disguise, or null for plain SSH. */
    internal fun sshTransport(c: ProxyConfig): JSONObject? =
        c.extraJson().optJSONObject("transport")?.takeIf { it.optString("mode") in Sidecars.SSH_MODES }

    /** DNS tunnel fields. For DNS tunnel profiles, [ProxyConfig.host] is the tunnel domain. */
    internal fun dnstt(c: ProxyConfig): JSONObject {
        val transport = c.mode.takeIf { it == "doh" || it == "dot" } ?: "udp"
        val resolver = when (transport) {
            "doh" -> c.path.ifBlank { "https://${c.address}/dns-query" }
            else -> {
                val port = if (c.port in 1..65535) c.port else if (transport == "dot") 853 else 53
                val host = if (c.address.contains(':')) "[${c.address}]" else c.address
                "$host:$port"
            }
        }
        return JSONObject().put("transport", transport).put("resolver", resolver)
            .put("domain", c.host).put("pubkey", c.publicKey)
    }

    private fun tls(c: ProxyConfig, forceOn: Boolean): JSONObject {
        val t = JSONObject().put("enabled", forceOn || c.security == "tls")
        t.putIf("server_name", c.sni.ifBlank { c.host })
        if (c.allowInsecure) t.put("insecure", true)
        val alpn = c.alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (alpn.isNotEmpty()) t.put("alpn", JSONArray(alpn))
        if (c.protocol == "anytls" && c.fingerprint.isNotBlank()) {
            t.put("utls", JSONObject().put("enabled", true).put("fingerprint", c.fingerprint))
        }
        return t
    }

    private fun JSONObject.server(c: ProxyConfig): JSONObject =
        put("server", c.address).put("server_port", c.port)

    private fun JSONObject.putIf(key: String, value: Any?): JSONObject {
        if (value == null) return this
        if (value is String && value.isBlank()) return this
        return put(key, value)
    }
}

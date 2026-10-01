package net.gozar.app

import java.net.URLEncoder
import java.util.Base64

/**
 * The add-server forms, one per protocol: which fields it asks for, which of
 * them sit under "Advanced", and how the filled form becomes a profile. The
 * profile is built as the protocol's own share link and parsed by
 * [ConfigParser], so a form can never produce something the link path would
 * not; values links do not carry (client certificate / key) are added after.
 * Pure; unit-tested.
 */
object ProtocolForms {

    enum class Kind { TEXT, NUMBER, PASSWORD, SELECT, SWITCH, MULTILINE, PEM }

    data class Field(
        val key: String,
        val label: String,
        val kind: Kind = Kind.TEXT,
        val advanced: Boolean = false,
        val default: String = "",
        val options: List<String> = emptyList(),
        val required: Boolean = false,
        val hint: String = ""
    )

    data class Form(val id: String, val title: String, val group: String, val fields: List<Field>)

    private val NAME = Field("name", "f_name")
    private fun server(def: String = "") = Field("server", "f_server", required = true, hint = def)
    private fun port(def: String) = Field("port", "f_port", Kind.NUMBER, default = def)
    private val USER = Field("user", "f_user")
    private val PASS = Field("pass", "f_password", Kind.PASSWORD)
    private val SNI = Field("sni", "f_sni", advanced = true)
    private val PIN = Field("pin", "f_pin", advanced = true, hint = "SHA-256")
    private val INSECURE = Field("insecure", "f_insecure", Kind.SWITCH, advanced = true)
    private val MTU = Field("mtu", "f_mtu", Kind.NUMBER, advanced = true)

    val forms: List<Form> = listOf(
        Form("openconnect", "OpenConnect", "vpn", listOf(
            NAME, server(), port("443"),
            Field("flavor", "f_oc_protocol", Kind.SELECT, default = "anyconnect", options = listOf("anyconnect", "gp", "fortinet", "f5", "pulse", "nc")),
            USER, PASS, Field("authgroup", "f_oc_authgroup"), Field("pin", "f_pin", hint = "SHA-256"),
            SNI, MTU, Field("reconnect", "f_oc_reconnect", Kind.NUMBER, advanced = true),
            Field("ua", "f_oc_ua", advanced = true),
            Field("os", "f_oc_os", Kind.SELECT, advanced = true, options = listOf("", "linux", "linux-64", "win", "mac-intel", "android", "apple-ios")),
            Field("cert", "f_oc_cert", Kind.PEM, advanced = true), Field("key", "f_oc_key", Kind.PEM, advanced = true),
            Field("nodtls", "f_oc_nodtls", Kind.SWITCH, advanced = true), Field("noipv6", "f_oc_noipv6", Kind.SWITCH, advanced = true),
            INSECURE)),
        Form("sstp", "SSTP", "vpn", listOf(
            NAME, server(), port("443"), USER.copy(required = true), PASS,
            Field("auth", "f_auth", Kind.SELECT, default = "auto", options = listOf("auto", "mschapv2", "pap")),
            SNI, PIN, INSECURE, MTU)),
        Form("softether", "SoftEther", "vpn", listOf(
            NAME, server(), port("443"), Field("hub", "f_se_hub", default = "DEFAULT"), USER.copy(required = true), PASS,
            Field("plain", "f_se_plain", Kind.SWITCH, advanced = true), SNI, PIN, INSECURE,
            Field("ip", "f_se_ip", advanced = true, hint = "10.0.0.2/24"), Field("gw", "f_se_gw", advanced = true),
            Field("dns", "f_dns", advanced = true), MTU)),
        Form("wireguard", "WireGuard / AmneziaWG", "vpn", listOf(
            NAME, Field("privkey", "f_wg_private", Kind.PASSWORD, required = true), Field("address", "f_wg_address", required = true, hint = "10.0.0.2/32"),
            Field("peerkey", "f_wg_peer", required = true), Field("endpoint", "f_wg_endpoint", required = true, hint = "host:51820"),
            Field("psk", "f_wg_psk", Kind.PASSWORD, advanced = true), Field("dns", "f_dns", advanced = true), MTU,
            Field("allowed", "f_wg_allowed", advanced = true, default = "0.0.0.0/0, ::/0"),
            Field("jc", "Jc", Kind.NUMBER, advanced = true), Field("jmin", "Jmin", Kind.NUMBER, advanced = true),
            Field("jmax", "Jmax", Kind.NUMBER, advanced = true), Field("s1", "S1", Kind.NUMBER, advanced = true),
            Field("s2", "S2", Kind.NUMBER, advanced = true), Field("h1", "H1", advanced = true), Field("h2", "H2", advanced = true),
            Field("h3", "H3", advanced = true), Field("h4", "H4", advanced = true))),
        Form("ssh", "SSH", "proxy", listOf(
            NAME, server(), port("22"), USER.copy(required = true), PASS,
            Field("mode", "f_ssh_mode", Kind.SELECT, default = "direct", options = listOf("direct", "payload", "tls", "payload-tls", "ws", "wss", "http-proxy", "https-proxy")),
            Field("sni", "f_sni"), Field("payload", "f_payload", Kind.MULTILINE, default = "CONNECT [host_port] [protocol][crlf]Host: [host][crlf][crlf]"),
            Field("wspath", "f_ws_path", default = "/"), Field("wshost", "f_ws_host"), Field("proxy", "f_front_proxy", hint = "host:port"),
            Field("hostkey", "f_ssh_hostkey", advanced = true), Field("pk", "f_ssh_pk", Kind.PEM, advanced = true))),
        Form("dnstt", "DNSTT / VayDNS / NoizDNS / Slipstream", "dns", listOf(
            NAME, Field("variant", "f_dns_engine", Kind.SELECT, default = "dnstt", options = listOf("dnstt", "vaydns", "noizdns", "slipstream")),
            Field("domain", "f_dns_domain", required = true), Field("pubkey", "f_dns_pubkey", hint = "64 hex"),
            Field("transport", "f_dns_transport", Kind.SELECT, default = "udp", options = listOf("udp", "dot", "doh")),
            Field("resolver", "f_dns_resolver", default = "8.8.8.8:53", hint = "host:port / https://…/dns-query"),
            Field("upstream", "f_dns_upstream", Kind.SELECT, advanced = true, default = "socks", options = listOf("socks", "ssh")),
            Field("user", "f_user", advanced = true), Field("pass", "f_password", Kind.PASSWORD, advanced = true))),
        Form("masterdns", "MasterDNS / StormDNS / CottenDNS", "dns", listOf(
            NAME, Field("variant", "f_dns_engine", Kind.SELECT, default = "masterdns", options = listOf("masterdns", "stormdns", "cottendns")),
            Field("domain", "f_dns_domain", required = true), Field("key", "f_dns_key", Kind.PASSWORD, required = true),
            Field("resolvers", "f_dns_resolvers", Kind.MULTILINE, required = true, hint = "8.8.8.8:53"),
            Field("enc", "f_dns_enc", Kind.NUMBER, advanced = true, default = "1"))),
        Form("shadowquic", "ShadowQUIC · نیازمند افزونه", "proxy", listOf(NAME, server(), port("443"),
            USER.copy(required = true), PASS.copy(required = true), SNI.copy(required = true, advanced = false),
            Field("udpMode", "حالت UDP", Kind.SELECT, options = listOf("datagram", "stream"), default = "datagram", advanced = true),
            Field("congestion", "کنترل ازدحام", Kind.SELECT, options = listOf("bbr", "cubic", "new-reno"), default = "bbr", advanced = true),
            Field("mtu", "MTU", Kind.NUMBER, default = "1280", advanced = true), Field("alpn", "ALPN", default = "h3", advanced = true))),
        Form("tuic", "TUIC v5", "proxy", listOf(NAME, server(), port("443"), Field("uuid", "UUID", required = true), PASS,
            Field("cc", "f_cc", Kind.SELECT, advanced = true, default = "bbr", options = listOf("bbr", "cubic", "new_reno")),
            Field("alpn", "ALPN", advanced = true, default = "h3"), SNI, INSECURE)),
        Form("hysteria2", "Hysteria 2", "proxy", listOf(NAME, server(), port("443"), PASS.copy(required = true),
            Field("obfs", "f_obfs_password", Kind.PASSWORD, advanced = true), SNI, INSECURE)),
        Form("masque", "MASQUE (CONNECT-IP)", "proxy", listOf(NAME, server(), port("443"), USER, PASS,
            Field("version", "f_http_version", Kind.SELECT, default = "3", options = listOf("3", "2", "1")),
            Field("path", "f_masque_path", advanced = true, hint = "/.well-known/masque/ip/{target}/{ipproto}/"),
            SNI, Field("alpn", "ALPN", advanced = true), Field("fp", "f_utls_fp", Kind.SELECT, advanced = true,
                options = listOf("", "chrome", "firefox", "safari", "ios", "android", "edge", "random")),
            PIN, INSECURE, MTU)),
        Form("tailscale", "Tailscale / Headscale", "vpn", listOf(NAME,
            Field("pass", "f_ts_authkey", Kind.PASSWORD, required = true, hint = "tskey-auth-…"),
            Field("exit", "f_ts_exit", hint = "100.64.0.1 / exit-node-name"),
            Field("control", "f_ts_control", advanced = true, hint = "https://headscale.example.com"),
            Field("hostname", "f_ts_hostname", advanced = true, hint = "ghajar-android"),
            Field("ephemeral", "f_ts_ephemeral", Kind.SWITCH, advanced = true, default = "1"),
            Field("routes", "f_ts_routes", Kind.SWITCH, advanced = true),
            Field("lan", "f_ts_lan", Kind.SWITCH, advanced = true))),
        Form("tailcat", "Tailcat (DERP)", "vpn", listOf(NAME,
            Field("pubkey", "f_tc_pub", required = true), Field("disco", "f_tc_disco", required = true),
            Field("psk", "f_tc_psk", Kind.PASSWORD, advanced = true), Field("privkey", "f_tc_key", Kind.PASSWORD, advanced = true),
            Field("derp", "f_tc_derp", advanced = true, hint = "https://tailcat.dev/derpmap.json"),
            Field("region", "f_tc_region", Kind.NUMBER, advanced = true))),
        Form("anytls", "AnyTLS", "proxy", listOf(NAME, server(), port("443"), PASS.copy(required = true), SNI, INSECURE)),
        Form("juicity", "Juicity", "proxy", listOf(NAME, server(), port("443"), Field("uuid", "UUID", required = true), PASS,
            Field("cc", "f_cc", Kind.SELECT, advanced = true, default = "bbr", options = listOf("bbr", "cubic", "new_reno")), SNI, PIN, INSECURE)),
        Form("naive", "NaiveProxy", "proxy", listOf(NAME, server(), port("443"), USER, PASS,
            Field("quic", "f_naive_quic", Kind.SWITCH, advanced = true))),
        Form("mieru", "Mieru", "proxy", listOf(NAME, server(), Field("port", "f_port", Kind.NUMBER, required = true), USER.copy(required = true),
            PASS.copy(required = true), Field("transport", "f_transport", Kind.SELECT, default = "TCP", options = listOf("TCP", "UDP")))),
        Form("tor", "Tor bridges", "core", listOf(NAME,
            Field("bridges", "f_tor_bridges", Kind.MULTILINE, required = true, hint = "obfs4 1.2.3.4:443 FINGERPRINT cert=… iat-mode=0")))
    ).map { form -> form.copy(fields = form.fields + EngineSettings.supported(form.id).map { setting ->
        Field(setting.key, setting.label, when (setting.type) {
            EngineSettings.Type.BOOL -> Kind.SWITCH
            EngineSettings.Type.SECONDS, EngineSettings.Type.COUNT -> Kind.NUMBER
            EngineSettings.Type.SECRET, EngineSettings.Type.PROXY -> Kind.PASSWORD
            EngineSettings.Type.PEM -> Kind.PEM
            else -> Kind.TEXT
        }, advanced = true, hint = setting.hint)
    }) }

    /** Add-server sections, in display order. A form shows only those it has fields in. */
    val SECTIONS = listOf("basic", "auth", "transport", "tls", "network", "dns", "routing", "advanced")

    /** Which section a field belongs to, by what it configures (not by protocol). */
    fun sectionOf(f: Field): String = when (f.key) {
        "name", "server", "port", "variant", "flavor", "domain", "hub", "bridges", "version" -> "basic"
        "cookie", "token_mode", "token_secret", "key_password", "mca_key_password", "user", "pass", "uuid", "key", "privkey", "psk", "pubkey", "authgroup", "auth", "pk", "hostkey", "plain", "disco" -> "auth"
        "server_ports", "hop_interval", "udp_over_stream", "mode", "transport", "wspath", "wshost", "payload", "proxy", "cc", "quic", "path", "upstream", "enc" -> "transport"
        "ech_config", "ca", "mca_cert", "mca_key", "tls_min", "sni", "pin", "insecure", "alpn", "fp", "cert", "nodtls", "os", "ua" -> "tls"
        "mtu", "ip", "gw", "address", "endpoint", "reconnect", "noipv6", "peerkey", "control", "hostname", "derp", "region", "ephemeral" -> "network"
        "dns", "resolver", "resolvers" -> "dns"
        "allowed", "exit", "routes", "lan" -> "routing"
        else -> "advanced"
    }

    /** The form's fields grouped by section, empty sections dropped. */
    fun sections(form: Form): List<Pair<String, List<Field>>> =
        SECTIONS.map { sec -> sec to form.fields.filter { sectionOf(it) == sec } }.filter { it.second.isNotEmpty() }

    fun formOrNull(id: String): Form? = forms.firstOrNull { it.id == id }
    fun form(id: String): Form = requireNotNull(formOrNull(id)) { "Unknown protocol form: $id" }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun hostPort(host: String, port: String): String {
        val h = host.trim().let { if (it.contains(':') && !it.startsWith("[")) "[$it]" else it }
        return if (port.isBlank()) h else "$h:${port.trim()}"
    }
    private fun query(vararg kv: Pair<String, String?>) =
        kv.filter { !it.second.isNullOrBlank() }.joinToString("&") { it.first + "=" + enc(it.second!!.trim()) }.let { if (it.isEmpty()) "" else "?$it" }
    private fun userInfo(u: String?, p: String?) =
        if (u.isNullOrEmpty() && p.isNullOrEmpty()) "" else enc(u.orEmpty()) + (if (p.isNullOrEmpty()) "" else ":" + enc(p)) + "@"

    /** The profile, or a reason naming the first missing field. */
    fun build(id: String, input: Map<String, String>): Result<ProxyConfig> {
        val f = formOrNull(id) ?: return Result.failure(IllegalArgumentException("f_invalid"))
        // A field left empty takes its default (e.g. port 443).
        val v = f.fields.filter { it.default.isNotEmpty() }.associate { it.key to it.default } +
            input.filterValues { it.isNotBlank() }
        f.fields.firstOrNull { it.required && v[it.key].isNullOrBlank() }?.let {
            return Result.failure(IllegalArgumentException(it.label))
        }
        if (f.fields.any { it.key == "port" } && v["port"]?.toIntOrNull() !in 1..65535)
            return Result.failure(IllegalArgumentException("پورت باید بین ۱ تا ۶۵۵۳۵ باشد"))
        if (id == "openconnect") {
            val cert = v["cert"].orEmpty(); val key = v["key"].orEmpty()
            if ((cert.isNotBlank() && !cert.contains("BEGIN CERTIFICATE")) || (key.isNotBlank() && !key.contains("PRIVATE KEY")))
                return Result.failure(IllegalArgumentException("قالب گواهی یا کلید خصوصی معتبر نیست"))
        }
        if (id == "shadowquic") return runCatching {
            val p = org.json.JSONObject().put("server", v["server"]).put("port", v["port"]?.toInt() ?: 443)
                .put("username", v["user"]).put("password", v["pass"]).put("sni", v["sni"])
                .put("udpMode", v["udpMode"]).put("congestion", v["congestion"]).put("mtu", v["mtu"]?.toInt() ?: 1280).put("alpn", v["alpn"])
            net.gozar.app.plugins.PluginProfiles.create("shadowquic", "shadowquic-json", p.toString(), v["name"].orEmpty())
        }
        val name = v["name"].orEmpty().ifBlank { f.title }
        fun on(k: String) = v[k] == "true" || v[k] == "1"
        val tail = "#" + enc(name)
        val link: String = when (id) {
            "openconnect" -> "openconnect://" + userInfo(v["user"], v["pass"]) + hostPort(v["server"]!!, v["port"].orEmpty()) +
                query("flavor" to v["flavor"], "authgroup" to v["authgroup"], "pin" to v["pin"], "sni" to v["sni"], "mtu" to v["mtu"],
                    "reconnect" to v["reconnect"], "ua" to v["ua"], "os" to v["os"], "nodtls" to if (on("nodtls")) "1" else null,
                    "noipv6" to if (on("noipv6")) "1" else null, "insecure" to if (on("insecure")) "1" else null) + tail
            "sstp" -> "sstp://" + userInfo(v["user"], v["pass"]) + hostPort(v["server"]!!, v["port"].orEmpty()) +
                query("auth" to v["auth"]?.takeIf { it != "auto" }, "sni" to v["sni"], "pin" to v["pin"], "mtu" to v["mtu"],
                    "allow_insecure" to if (on("insecure")) "1" else null) + tail
            "softether" -> "softether://" + userInfo(v["user"], v["pass"]) + hostPort(v["server"]!!, v["port"].orEmpty()) +
                query("hub" to v["hub"], "auth" to if (on("plain")) "plain" else null, "sni" to v["sni"], "pin" to v["pin"],
                    "allow_insecure" to if (on("insecure")) "1" else null, "ip" to v["ip"], "gw" to v["gw"], "dns" to v["dns"], "mtu" to v["mtu"]) + tail
            "ssh" -> "ssh://" + userInfo(v["user"], v["pass"]) + hostPort(v["server"]!!, v["port"].orEmpty()) + query(
                "mode" to v["mode"]?.takeIf { it != "direct" },
                "sni" to v["sni"].takeIf { v["mode"] != "direct" },
                "payload" to v["payload"]?.takeIf { v["mode"].orEmpty().startsWith("payload") && it.isNotBlank() }
                    ?.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) },
                "wspath" to v["wspath"].takeIf { v["mode"] == "ws" || v["mode"] == "wss" },
                "wshost" to v["wshost"].takeIf { v["mode"] == "ws" || v["mode"] == "wss" },
                "proxy" to v["proxy"].takeIf { v["mode"] != "direct" },
                "hostkey" to v["hostkey"],
                "pk" to v["pk"]?.takeIf { it.contains("PRIVATE KEY") }?.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }
            ) + tail
            "dnstt" -> {
                val doh = v["transport"] == "doh"
                (v["variant"] ?: "dnstt") + "://" + userInfo(v["user"], v["pass"]) + v["domain"]!!.trim() + query(
                    "pubkey" to v["pubkey"], "transport" to v["transport"],
                    "resolver" to v["resolver"].takeIf { !doh }, "doh" to v["resolver"].takeIf { doh },
                    "upstream" to v["upstream"]?.takeIf { it == "ssh" }) + tail
            }
            "masterdns" -> {
                val resolvers = v["resolvers"]!!.split('\n', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                (v["variant"] ?: "masterdns") + "://" + enc(v["key"]!!.trim()) + "@" + v["domain"]!!.trim() +
                    query("resolver" to resolvers.joinToString(","), "enc" to v["enc"]) + tail
            }
            "tuic" -> "tuic://" + enc(v["uuid"].orEmpty()) + ":" + enc(v["pass"].orEmpty()) + "@" + hostPort(v["server"]!!, v["port"].orEmpty()) +
                query("congestion_control" to v["cc"], "alpn" to v["alpn"], "sni" to v["sni"], "allow_insecure" to if (on("insecure")) "1" else null) + tail
            "hysteria2" -> "hysteria2://" + enc(v["pass"].orEmpty()) + "@" + hostPort(v["server"]!!, v["port"].orEmpty()) +
                query("obfs" to if (v["obfs"].isNullOrBlank()) null else "salamander", "obfs-password" to v["obfs"], "sni" to v["sni"],
                    "insecure" to if (on("insecure")) "1" else null) + tail
            "anytls" -> "anytls://" + enc(v["pass"].orEmpty()) + "@" + hostPort(v["server"]!!, v["port"].orEmpty()) +
                query("sni" to v["sni"], "insecure" to if (on("insecure")) "1" else null) + tail
            "juicity" -> "juicity://" + enc(v["uuid"].orEmpty()) + ":" + enc(v["pass"].orEmpty()) + "@" + hostPort(v["server"]!!, v["port"].orEmpty()) +
                query("congestion_control" to v["cc"], "sni" to v["sni"], "pinned_certchain_sha256" to v["pin"],
                    "allow_insecure" to if (on("insecure")) "1" else null) + tail
            "masque" -> "masque://" + userInfo(v["user"], v["pass"]) + hostPort(v["server"]!!, v["port"].orEmpty()) +
                query("version" to v["version"]?.takeIf { it != "3" }, "path" to v["path"], "sni" to v["sni"], "alpn" to v["alpn"],
                    "fp" to v["fp"], "pin" to v["pin"], "mtu" to v["mtu"], "insecure" to if (on("insecure")) "1" else null) + tail
            "tailscale" -> {
                val control = v["control"]?.trim().orEmpty()
                val host = runCatching { java.net.URI(control).host }.getOrNull().orEmpty().ifBlank { ConfigParser.TAILSCALE_CONTROL }
                "tailscale://" + enc(v["pass"].orEmpty().trim()) + "@" + host +
                    query("control" to control.ifBlank { null }, "exit" to v["exit"]?.trim(), "hostname" to v["hostname"]?.trim(),
                        "flags" to listOf("ephemeral", "routes", "lan").filter { on(it) }.joinToString(",").ifBlank { null }) + tail
            }
            "tailcat" -> "tailcat://tailcat.dev" + query("pub" to v["pubkey"]?.trim(), "disco" to v["disco"]?.trim(), "psk" to v["psk"],
                "key" to v["privkey"], "derp" to v["derp"]?.trim(), "region" to v["region"]) + tail
            "naive" -> (if (on("quic")) "naive+quic://" else "naive+https://") + userInfo(v["user"], v["pass"]) + hostPort(v["server"]!!, v["port"].orEmpty()) + tail
            "mieru" -> "mierus://" + userInfo(v["user"], v["pass"]) + v["server"]!!.trim() + query("port" to v["port"], "protocol" to v["transport"]) + tail
            "wireguard" -> return buildWireGuard(v, name)
            "tor" -> return ConfigParser.parse(v["bridges"]!!.trim())?.let { Result.success(it.copy(name = name)) }
                ?: Result.failure(IllegalArgumentException("f_tor_bridges"))
            else -> return Result.failure(IllegalArgumentException(id))
        }
        val parsed = ConfigParser.parse(link) ?: return Result.failure(IllegalArgumentException("f_invalid"))
        val c = runCatching { EngineSettings.merge(parsed, v) }.getOrElse { return Result.failure(it) }
        if (id == "openconnect" && (!v["cert"].isNullOrBlank() || !v["key"].isNullOrBlank())) {
            val x = c.extraJson()
            v["cert"]?.takeIf { it.contains("BEGIN CERTIFICATE") }?.let { x.put("clientCert", it.trim()) }
            v["key"]?.takeIf { it.contains("PRIVATE KEY") }?.let { x.put("clientKey", it.trim()) }
            return Result.success(c.copy(extra = x.toString()))
        }
        return Result.success(c)
    }

    private fun buildWireGuard(v: Map<String, String>, name: String): Result<ProxyConfig> {
        val conf = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${v["privkey"]!!.trim()}")
            appendLine("Address = ${v["address"]!!.trim()}")
            v["dns"]?.takeIf { it.isNotBlank() }?.let { appendLine("DNS = ${it.trim()}") }
            v["mtu"]?.takeIf { it.isNotBlank() }?.let { appendLine("MTU = ${it.trim()}") }
            listOf("jc" to "Jc", "jmin" to "Jmin", "jmax" to "Jmax", "s1" to "S1", "s2" to "S2", "h1" to "H1", "h2" to "H2", "h3" to "H3", "h4" to "H4")
                .forEach { (k, label) -> v[k]?.takeIf { it.isNotBlank() }?.let { appendLine("$label = ${it.trim()}") } }
            appendLine("[Peer]")
            appendLine("PublicKey = ${v["peerkey"]!!.trim()}")
            v["psk"]?.takeIf { it.isNotBlank() }?.let { appendLine("PresharedKey = ${it.trim()}") }
            appendLine("AllowedIPs = ${v["allowed"].orEmpty().ifBlank { "0.0.0.0/0, ::/0" }}")
            appendLine("Endpoint = ${v["endpoint"]!!.trim()}")
        }
        return ConfigParser.parseWireguardConf(conf)?.let { Result.success(it.copy(name = name)) }
            ?: Result.failure(IllegalArgumentException("f_invalid"))
    }
}

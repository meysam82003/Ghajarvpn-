package net.gozar.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

/**
 * Importers for other clients' full configurations: Clash / Clash Meta /
 * Mihomo YAML (`proxies:`) and sing-box JSON (`outbounds` / `endpoints` with
 * `type`). Each foreign proxy is turned into the share link Ghajar already
 * parses (or a WireGuard .conf), so there is one normalisation path; what
 * cannot be expressed is skipped with a named warning, never guessed.
 */
object ForeignImport {

    data class Result(val configs: List<ProxyConfig>, val warnings: List<String>)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun host(h: String) = if (h.contains(':') && !h.startsWith("[")) "[$h]" else h
    private fun query(vararg kv: Pair<String, String?>) =
        kv.filter { !it.second.isNullOrEmpty() }.joinToString("&") { it.first + "=" + enc(it.second!!) }
            .let { if (it.isEmpty()) "" else "?$it" }

    private fun link(scheme: String, user: String?, server: String, port: Int, name: String, vararg q: Pair<String, String?>) =
        "$scheme://" + (if (user.isNullOrEmpty()) "" else "$user@") + host(server) + ":" + port + query(*q) + "#" + enc(name)

    private fun parseLink(l: String, source: ConfigSource) = ConfigParser.parse(l, source)

    // ---------------------------------------------------------------- Clash

    fun looksLikeClash(text: String): Boolean {
        val t = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        return !t.startsWith("{") && !t.startsWith("[") && Regex("(?m)^\\s*proxies\\s*:").containsMatchIn(t)
    }

    fun clash(text: String, source: ConfigSource = ConfigSource.PERSONAL): Result = Result(
        listOf(net.gozar.app.plugins.PluginProfiles.create("mihomo", "mihomo-yaml", text, source = source)),
        listOf("کانفیگ کامل حفظ شد؛ برای اتصال افزونه Mihomo لازم است."))

    /** Explicit node extraction remains available; never the default full-config import. */
    fun clashNodes(text: String, source: ConfigSource = ConfigSource.PERSONAL): Result {
        val root = runCatching { MiniYaml.parse(text) }.getOrNull() as? Map<*, *>
            ?: return Result(emptyList(), listOf("the YAML could not be read"))
        val proxies = root["proxies"] as? List<*> ?: return Result(emptyList(), listOf("no proxies: list"))
        val out = mutableListOf<ProxyConfig>()
        val warn = mutableListOf<String>()
        for (p in proxies) {
            val m = p as? Map<*, *> ?: continue
            val name = s(m, "name").ifBlank { "Clash" }
            val type = s(m, "type").lowercase()
            val c = runCatching { clashProxy(m, type, name, source) }.getOrNull()
            if (c != null) out += c else warn += "$name: type \"$type\" skipped (not supported or incomplete)"
        }
        return Result(out, warn)
    }

    private fun s(m: Map<*, *>?, k: String): String = when (val v = m?.get(k)) {
        null -> ""
        is List<*> -> v.joinToString(",") { it.toString() }
        else -> v.toString()
    }
    private fun b(m: Map<*, *>?, k: String) = s(m, k).lowercase() in setOf("true", "1", "yes")
    private fun sub(m: Map<*, *>?, k: String) = m?.get(k) as? Map<*, *>

    /** Transport query parameters shared by vmess/vless/trojan. */
    private fun clashTransport(m: Map<*, *>): List<Pair<String, String?>> {
        val net = s(m, "network").ifBlank { "tcp" }
        val ws = sub(m, "ws-opts"); val grpc = sub(m, "grpc-opts"); val h2 = sub(m, "h2-opts"); val http = sub(m, "http-opts")
        return when (net) {
            "ws" -> listOf("type" to "ws", "path" to s(ws, "path"), "host" to s(sub(ws, "headers"), "Host").ifBlank { s(sub(ws, "headers"), "host") })
            "grpc" -> listOf("type" to "grpc", "serviceName" to s(grpc, "grpc-service-name"))
            "h2" -> listOf("type" to "http", "path" to s(h2, "path"), "host" to s(h2, "host"))
            "http" -> listOf("type" to "tcp", "headerType" to "http", "path" to s(http, "path"), "host" to s(sub(http, "headers"), "Host"))
            "httpupgrade" -> listOf("type" to "httpupgrade", "path" to s(ws, "path"), "host" to s(sub(ws, "headers"), "Host"))
            else -> listOf("type" to "tcp")
        }
    }

    private fun clashProxy(m: Map<*, *>, type: String, name: String, src: ConfigSource): ProxyConfig? {
        val server = s(m, "server"); val port = s(m, "port").toIntOrNull() ?: 0
        val sni = s(m, "servername").ifBlank { s(m, "sni") }
        val insecure = if (b(m, "skip-cert-verify")) "1" else null
        val fp = s(m, "client-fingerprint")
        val alpn = s(m, "alpn")
        return when (type) {
            "ss" -> {
                val plugin = s(m, "plugin"); val po = sub(m, "plugin-opts")
                val pluginStr = when (plugin) {
                    "obfs" -> "obfs-local;obfs=${s(po, "mode")};obfs-host=${s(po, "host")}"
                    "v2ray-plugin" -> "v2ray-plugin;mode=${s(po, "mode").ifBlank { "websocket" }}" +
                        (if (b(po, "tls")) ";tls" else "") + ";host=${s(po, "host")};path=${s(po, "path")}"
                    "shadow-tls" -> "shadow-tls;host=${s(po, "host")};password=${s(po, "password")};version=${s(po, "version").ifBlank { "3" }}"
                    "" -> null
                    else -> return null
                }
                val user = Base64.getUrlEncoder().withoutPadding().encodeToString("${s(m, "cipher")}:${s(m, "password")}".toByteArray())
                parseLink(link("ss", user, server, port, name, "plugin" to pluginStr), src)
            }
            "vmess" -> {
                val t = clashTransport(m).toMap()
                val j = JSONObject().put("v", "2").put("ps", name).put("add", server).put("port", port.toString())
                    .put("id", s(m, "uuid")).put("aid", s(m, "alterId").ifBlank { "0" }).put("scy", s(m, "cipher").ifBlank { "auto" })
                    .put("net", t["type"] ?: "tcp").put("type", t["headerType"] ?: "none").put("host", t["host"].orEmpty())
                    .put("path", t["path"] ?: t["serviceName"].orEmpty()).put("tls", if (b(m, "tls")) "tls" else "")
                    .put("sni", sni).put("alpn", alpn).put("fp", fp)
                parseLink("vmess://" + Base64.getEncoder().encodeToString(j.toString().toByteArray()), src)
            }
            "vless" -> {
                val reality = sub(m, "reality-opts")
                val security = when { reality != null -> "reality"; b(m, "tls") -> "tls"; else -> "none" }
                parseLink(link("vless", s(m, "uuid"), server, port, name,
                    *(clashTransport(m) + listOf("security" to security, "sni" to sni, "fp" to fp, "alpn" to alpn,
                        "flow" to s(m, "flow"), "pbk" to s(reality, "public-key"), "sid" to s(reality, "short-id"),
                        "allowInsecure" to insecure, "encryption" to "none")).toTypedArray()), src)
            }
            "trojan" -> {
                val reality = sub(m, "reality-opts")
                parseLink(link("trojan", enc(s(m, "password")), server, port, name,
                    *(clashTransport(m) + listOf("security" to if (reality != null) "reality" else "tls", "sni" to sni, "fp" to fp,
                        "alpn" to alpn, "pbk" to s(reality, "public-key"), "sid" to s(reality, "short-id"),
                        "allowInsecure" to insecure)).toTypedArray()), src)
            }
            "hysteria2", "hy2" -> parseLink(link("hysteria2", enc(s(m, "password")), server, port, name,
                "sni" to sni, "obfs" to s(m, "obfs"), "obfs-password" to s(m, "obfs-password"), "insecure" to insecure,
                "up" to s(m, "up").filter(Char::isDigit), "down" to s(m, "down").filter(Char::isDigit), "alpn" to alpn), src)
            "hysteria" -> parseLink(link("hysteria", null, server, port, name,
                "auth" to s(m, "auth-str").ifBlank { s(m, "auth") }, "peer" to sni, "obfs" to s(m, "obfs"),
                "upmbps" to s(m, "up").filter(Char::isDigit), "downmbps" to s(m, "down").filter(Char::isDigit),
                "alpn" to alpn, "insecure" to insecure), src)
            "tuic" -> parseLink(link("tuic", enc(s(m, "uuid")) + ":" + enc(s(m, "password")), server, port, name,
                "sni" to sni, "alpn" to alpn, "congestion_control" to s(m, "congestion-controller"),
                "udp_relay_mode" to s(m, "udp-relay-mode"), "allow_insecure" to insecure), src)
            "anytls" -> parseLink(link("anytls", enc(s(m, "password")), server, port, name, "sni" to sni, "fp" to fp,
                "alpn" to alpn, "insecure" to insecure), src)
            "socks5" -> parseLink(link("socks5", userPass(m), server, port, name), src)
            "http" -> if (b(m, "tls")) null else parseLink(link("http", userPass(m), server, port, name), src)
            "ssh" -> parseLink(link("ssh", userPass(m, "username"), server, port.takeIf { it > 0 } ?: 22, name,
                "pk" to s(m, "private-key").takeIf { it.contains("PRIVATE KEY") },
                "hostkey" to s(m, "host-key")), src)
            "mieru" -> parseLink(link("mierus", userPass(m), server, 0, name,
                "port" to s(m, "port").ifBlank { s(m, "port-range") }, "protocol" to s(m, "transport").ifBlank { "TCP" })
                .replace(":0?", "?"), src)
            "snell" -> ProxyConfig(name = name, protocol = "snell", address = server, port = port, password = s(m, "psk"),
                alterId = s(m, "version").toIntOrNull() ?: 4, hyObfs = s(sub(m, "obfs-opts"), "mode"),
                host = s(sub(m, "obfs-opts"), "host"), source = src).takeIf { server.isNotBlank() && port > 0 }
            "wireguard" -> {
                val awg = sub(m, "amnezia-wg-option")
                val conf = buildString {
                    appendLine("[Interface]")
                    appendLine("PrivateKey = ${s(m, "private-key")}")
                    val addrs = listOf(s(m, "ip"), s(m, "ipv6")).filter { it.isNotBlank() }
                    if (addrs.isNotEmpty()) appendLine("Address = " + addrs.joinToString(", ") { if (it.contains('/')) it else if (it.contains(':')) "$it/128" else "$it/32" })
                    s(m, "mtu").takeIf { it.isNotBlank() }?.let { appendLine("MTU = $it") }
                    awg?.forEach { (k, v) -> appendLine("${k.toString().replaceFirstChar { it.uppercase() }} = $v") }
                    appendLine("[Peer]")
                    appendLine("PublicKey = ${s(m, "public-key")}")
                    s(m, "pre-shared-key").takeIf { it.isNotBlank() }?.let { appendLine("PresharedKey = $it") }
                    appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
                    appendLine("Endpoint = ${host(server)}:$port")
                }
                ConfigParser.parseWireguardConf(conf, src)?.copy(name = name)
            }
            else -> null
        }
    }

    private fun userPass(m: Map<*, *>, userKey: String = "username"): String? {
        val u = s(m, userKey); val p = s(m, "password")
        return if (u.isEmpty() && p.isEmpty()) null else enc(u) + ":" + enc(p)
    }

    // ------------------------------------------------------------- sing-box

    /** True when a JSON config uses sing-box's shape (typed outbounds/endpoints). */
    fun looksLikeSingBox(root: JSONObject): Boolean {
        val arr = root.optJSONArray("outbounds") ?: root.optJSONArray("endpoints") ?: return false
        return (0 until arr.length()).any { arr.optJSONObject(it)?.let { o -> o.has("type") && !o.has("protocol") } == true }
    }

    fun singBox(root: JSONObject, source: ConfigSource = ConfigSource.PERSONAL): Result {
        val all = mutableListOf<JSONObject>()
        listOf("outbounds", "endpoints").forEach { k ->
            root.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(all::add) }
        }
        val byTag = all.associateBy { it.optString("tag") }
        val out = mutableListOf<ProxyConfig>()
        val warn = mutableListOf<String>()
        val skipTypes = setOf("direct", "block", "dns", "selector", "urltest", "tun")
        // ShadowTLS outbounds are only the carriers of a Shadowsocks detour.
        val carriers = all.filter { it.optString("type") == "shadowtls" }.map { it.optString("tag") }.toSet()
        for (o in all) {
            val type = o.optString("type")
            if (type in skipTypes || o.optString("tag") in carriers) continue
            val name = o.optString("tag").ifBlank { type }
            val c = runCatching { singBoxOutbound(o, byTag, source) }.getOrNull()
            if (c != null) out += c else warn += "$name: type \"$type\" skipped (not supported or incomplete)"
        }
        return Result(out, warn)
    }

    private fun sbTls(o: JSONObject): List<Pair<String, String?>> {
        val t = o.optJSONObject("tls") ?: return emptyList()
        if (!t.optBoolean("enabled")) return emptyList()
        val reality = t.optJSONObject("reality")?.takeIf { it.optBoolean("enabled") }
        val alpn = t.optJSONArray("alpn")?.let { a -> (0 until a.length()).joinToString(",") { a.getString(it) } }
        return listOf("security" to if (reality != null) "reality" else "tls", "sni" to t.optString("server_name"),
            "alpn" to alpn, "fp" to t.optJSONObject("utls")?.optString("fingerprint"),
            "allowInsecure" to if (t.optBoolean("insecure")) "1" else null,
            "pbk" to reality?.optString("public_key"), "sid" to reality?.optString("short_id"))
    }

    private fun sbTransport(o: JSONObject): List<Pair<String, String?>> {
        val t = o.optJSONObject("transport") ?: return listOf("type" to "tcp")
        return when (t.optString("type")) {
            "ws" -> listOf("type" to "ws", "path" to t.optString("path"), "host" to t.optJSONObject("headers")?.optString("Host"))
            "grpc" -> listOf("type" to "grpc", "serviceName" to t.optString("service_name"))
            "http" -> listOf("type" to "http", "path" to t.optString("path"),
                "host" to t.optJSONArray("host")?.let { a -> (0 until a.length()).joinToString(",") { a.getString(it) } })
            "httpupgrade" -> listOf("type" to "httpupgrade", "path" to t.optString("path"), "host" to t.optString("host"))
            else -> listOf("type" to "tcp")
        }
    }

    private fun singBoxOutbound(o: JSONObject, byTag: Map<String, JSONObject>, src: ConfigSource): ProxyConfig? {
        val name = o.optString("tag").ifBlank { o.optString("type") }
        val server = o.optString("server"); val port = o.optInt("server_port")
        val tls = sbTls(o).toMap()
        return when (o.optString("type")) {
            "vless" -> parseLink(link("vless", o.optString("uuid"), server, port, name,
                *(sbTransport(o) + sbTls(o) + listOf("flow" to o.optString("flow"), "encryption" to "none")).toTypedArray()), src)
            "trojan" -> parseLink(link("trojan", enc(o.optString("password")), server, port, name,
                *(sbTransport(o) + sbTls(o)).toTypedArray()), src)
            "vmess" -> {
                val t = sbTransport(o).toMap()
                val j = JSONObject().put("v", "2").put("ps", name).put("add", server).put("port", port.toString())
                    .put("id", o.optString("uuid")).put("aid", o.optInt("alter_id").toString()).put("scy", o.optString("security").ifBlank { "auto" })
                    .put("net", t["type"] ?: "tcp").put("host", t["host"].orEmpty()).put("path", t["path"] ?: t["serviceName"].orEmpty())
                    .put("tls", if (tls["security"] == "tls") "tls" else "").put("sni", tls["sni"].orEmpty()).put("alpn", tls["alpn"].orEmpty())
                    .put("fp", tls["fp"].orEmpty())
                parseLink("vmess://" + Base64.getEncoder().encodeToString(j.toString().toByteArray()), src)
            }
            "shadowsocks" -> {
                val user = Base64.getUrlEncoder().withoutPadding().encodeToString("${o.optString("method")}:${o.optString("password")}".toByteArray())
                val detour = byTag[o.optString("detour")]?.takeIf { it.optString("type") == "shadowtls" }
                if (detour != null) {
                    val plugin = "shadow-tls;host=${detour.optJSONObject("tls")?.optString("server_name").orEmpty()};" +
                        "password=${detour.optString("password")};version=${detour.optInt("version", 3)}"
                    parseLink(link("ss", user, detour.optString("server"), detour.optInt("server_port"), name, "plugin" to plugin), src)
                } else {
                    val plugin = o.optString("plugin").takeIf { it.isNotBlank() }?.let { it + ";" + o.optString("plugin_opts") }
                    parseLink(link("ss", user, server, port, name, "plugin" to plugin), src)
                }
            }
            "hysteria2" -> parseLink(link("hysteria2", enc(o.optString("password")), server, port, name,
                "sni" to tls["sni"], "insecure" to tls["allowInsecure"], "alpn" to tls["alpn"],
                "obfs" to o.optJSONObject("obfs")?.optString("type"), "obfs-password" to o.optJSONObject("obfs")?.optString("password"),
                "up" to o.optInt("up_mbps").takeIf { it > 0 }?.toString(), "down" to o.optInt("down_mbps").takeIf { it > 0 }?.toString()), src)
            "hysteria" -> parseLink(link("hysteria", null, server, port, name,
                "auth" to o.optString("auth_str"), "peer" to tls["sni"], "obfs" to o.optString("obfs"),
                "upmbps" to o.optInt("up_mbps").takeIf { it > 0 }?.toString(), "downmbps" to o.optInt("down_mbps").takeIf { it > 0 }?.toString(),
                "alpn" to tls["alpn"], "insecure" to tls["allowInsecure"]), src)
            "tuic" -> parseLink(link("tuic", enc(o.optString("uuid")) + ":" + enc(o.optString("password")), server, port, name,
                "sni" to tls["sni"], "alpn" to tls["alpn"], "congestion_control" to o.optString("congestion_control"),
                "udp_relay_mode" to o.optString("udp_relay_mode"), "allow_insecure" to tls["allowInsecure"]), src)
            "anytls" -> parseLink(link("anytls", enc(o.optString("password")), server, port, name,
                "sni" to tls["sni"], "fp" to tls["fp"], "alpn" to tls["alpn"], "insecure" to tls["allowInsecure"]), src)
            "tailscale" -> {
                val control = o.optString("control_url")
                val host = runCatching { java.net.URI(control).host }.getOrNull().orEmpty().ifBlank { ConfigParser.TAILSCALE_CONTROL }
                val flags = listOfNotNull("ephemeral".takeIf { o.optBoolean("ephemeral") }, "routes".takeIf { o.optBoolean("accept_routes") },
                    "lan".takeIf { o.optBoolean("exit_node_allow_lan_access") }).joinToString(",")
                parseLink(link("tailscale", enc(o.optString("auth_key")).takeIf { it.isNotEmpty() }, host, 443, name,
                    "control" to control.takeIf { it.isNotBlank() }, "exit" to o.optString("exit_node").takeIf { it.isNotBlank() },
                    "hostname" to o.optString("hostname").takeIf { it.isNotBlank() }, "flags" to flags.takeIf { it.isNotEmpty() }), src)
            }
            "tailcat" -> parseLink(link("tailcat", null, "tailcat.dev", 443, name,
                "pub" to o.optString("server_public_key"), "disco" to o.optString("server_disco_key"),
                "psk" to o.optString("pre_shared_key").takeIf { it.isNotBlank() }, "key" to o.optString("private_key").takeIf { it.isNotBlank() },
                "derp" to o.optString("derp_map_url").takeIf { it.isNotBlank() },
                "region" to o.optInt("derp_region", 0).takeIf { it > 0 }?.toString()), src)
            "masque-client" -> parseLink(link("masque", sbUser(o), server, port.takeIf { it > 0 } ?: 443, name,
                "version" to o.optInt("version", 0).takeIf { it in 1..2 }?.toString(), "path" to o.optString("path").takeIf { it.isNotBlank() },
                "sni" to tls["sni"], "fp" to tls["fp"], "alpn" to tls["alpn"], "insecure" to tls["allowInsecure"],
                "pin" to o.optJSONObject("tls")?.optJSONArray("certificate_sha256")?.optString(0),
                "mtu" to o.optInt("mtu", 0).takeIf { it > 0 }?.toString()), src)
            "socks" -> parseLink(link("socks5", sbUser(o), server, port, name), src)
            "http" -> if (tls.isNotEmpty()) null else parseLink(link("http", sbUser(o), server, port, name), src)
            "ssh" -> parseLink(link("ssh", sbUser(o, "user"), server, port.takeIf { it > 0 } ?: 22, name,
                "pk" to (o.optJSONArray("private_key")?.optString(0) ?: o.optString("private_key").takeIf { it.isNotBlank() }),
                "hostkey" to o.optJSONArray("host_key")?.optString(0)), src)
            "naive" -> parseLink(link(if (o.optBoolean("quic")) "naive+quic" else "naive+https", sbUser(o), server, port, name,
                "sni" to tls["sni"]), src)
            "snell" -> ProxyConfig(name = name, protocol = "snell", address = server, port = port, password = o.optString("psk"),
                alterId = o.optInt("version", 4), hyObfs = o.optString("obfs_mode"), host = o.optString("obfs_host"), source = src)
                .takeIf { server.isNotBlank() && port > 0 }
            "wireguard" -> {
                // 1.11+ endpoint shape, or the legacy outbound.
                val peer = o.optJSONArray("peers")?.optJSONObject(0)
                val addr = (o.optJSONArray("address") ?: o.optJSONArray("local_address"))?.let { a -> (0 until a.length()).joinToString(", ") { a.getString(it) } }.orEmpty()
                val pServer = peer?.optString("address") ?: server
                val pPort = peer?.optInt("port") ?: port
                val conf = buildString {
                    appendLine("[Interface]"); appendLine("PrivateKey = ${o.optString("private_key")}")
                    if (addr.isNotBlank()) appendLine("Address = $addr")
                    o.optInt("mtu").takeIf { it > 0 }?.let { appendLine("MTU = $it") }
                    appendLine("[Peer]")
                    appendLine("PublicKey = ${peer?.optString("public_key") ?: o.optString("peer_public_key")}")
                    (peer?.optString("pre_shared_key") ?: o.optString("pre_shared_key")).takeIf { it.isNotBlank() }?.let { appendLine("PresharedKey = $it") }
                    appendLine("AllowedIPs = 0.0.0.0/0, ::/0"); appendLine("Endpoint = ${host(pServer)}:$pPort")
                }
                ConfigParser.parseWireguardConf(conf, src)?.copy(name = name)
            }
            else -> null
        }
    }

    private fun sbUser(o: JSONObject, userKey: String = "username"): String? {
        val u = o.optString(userKey); val p = o.optString("password")
        return if (u.isEmpty() && p.isEmpty()) null else enc(u) + ":" + enc(p)
    }
}

/**
 * The YAML subset Clash / Mihomo files use: block mappings and sequences by
 * indentation, flow `{…}` / `[…]`, quoted and plain scalars, comments.
 * Anchors, tags and multi-line scalars are not needed for `proxies:`.
 * Scalars stay strings; callers convert.
 */
object MiniYaml {

    private class Line(val indent: Int, val text: String)

    fun parse(text: String): Any? {
        val lines = join(text.replace("\t", "  ").lines().map(::stripComment))
            .filter { it.isNotBlank() && it.trim() != "---" && it.trim() != "..." }
            .map { Line(it.length - it.trimStart().length, it.trim()) }
        if (lines.isEmpty()) return null
        val p = P(lines.toMutableList())
        return p.node(lines[0].indent)
    }

    private fun stripComment(l: String): String {
        var q: Char? = null
        for (i in l.indices) {
            val c = l[i]
            if (q != null) { if (c == q) q = null; continue }
            if (c == '"' || c == '\'') q = c
            else if (c == '#' && (i == 0 || l[i - 1] == ' ')) return l.substring(0, i).trimEnd()
        }
        return l.trimEnd()
    }

    /** Joins flow collections that span several lines. */
    private fun join(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var acc: StringBuilder? = null
        var depth = 0
        for (l in lines) {
            val d = balance(l)
            if (acc != null) { acc.append(' ').append(l.trim()); depth += d; if (depth <= 0) { out += acc.toString(); acc = null }; continue }
            if (d > 0) { acc = StringBuilder(l); depth = d } else out += l
        }
        acc?.let { out += it.toString() }
        return out
    }

    private fun balance(l: String): Int {
        var q: Char? = null; var d = 0
        for (c in l) {
            if (q != null) { if (c == q) q = null; continue }
            when (c) { '"', '\'' -> q = c; '{', '[' -> d++; '}', ']' -> d-- }
        }
        return d
    }

    private class P(val ls: MutableList<Line>) {
        var i = 0
        fun node(indent: Int): Any? {
            if (i >= ls.size) return null
            val l = ls[i]
            return if (l.text == "-" || l.text.startsWith("- ")) seq(l.indent) else map(l.indent)
        }

        fun seq(indent: Int): List<Any?> {
            val out = mutableListOf<Any?>()
            while (i < ls.size && ls[i].indent == indent && (ls[i].text == "-" || ls[i].text.startsWith("- "))) {
                val rest = ls[i].text.removePrefix("-").trimStart()
                if (rest.isEmpty()) { i++; out += if (i < ls.size && ls[i].indent > indent) node(ls[i].indent) else null; continue }
                if (keyOf(rest) != null && !rest.startsWith("{") && !rest.startsWith("[") && !rest.startsWith("\"") && !rest.startsWith("'")) {
                    // "- key: value" opens a mapping whose keys align with "key".
                    val inner = indent + (ls[i].text.length - rest.length)
                    ls[i] = Line(inner, rest)
                    out += map(inner)
                } else { out += inline(rest); i++ }
            }
            return out
        }

        fun map(indent: Int): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            while (i < ls.size && ls[i].indent == indent && !(ls[i].text == "-" || ls[i].text.startsWith("- "))) {
                val (k, v) = keyOf(ls[i].text) ?: break
                i++
                out[k] = when {
                    v.isNotEmpty() -> inline(v)
                    i < ls.size && ls[i].indent > indent -> node(ls[i].indent)
                    i < ls.size && ls[i].indent == indent && ls[i].text.startsWith("- ") -> seq(indent)
                    else -> null
                }
            }
            return out
        }
    }

    /** "key: value" split outside quotes; null when the line is not a pair. */
    private fun keyOf(t: String): Pair<String, String>? {
        var q: Char? = null
        for (j in t.indices) {
            val c = t[j]
            if (q != null) { if (c == q) q = null; continue }
            if ((c == '"' || c == '\'') && j == 0) { q = c; continue }
            if (c == ':' && (j == t.length - 1 || t[j + 1] == ' ')) return unquote(t.substring(0, j).trim()) to t.substring(j + 1).trim()
            if (c == '{' || c == '[') return null
        }
        return null
    }

    private fun unquote(s: String): String = when {
        s.length >= 2 && s.startsWith("\"") && s.endsWith("\"") -> s.substring(1, s.length - 1)
            .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
        s.length >= 2 && s.startsWith("'") && s.endsWith("'") -> s.substring(1, s.length - 1).replace("''", "'")
        else -> s
    }

    fun inline(s: String): Any? {
        val t = s.trim()
        if (t.startsWith("{") || t.startsWith("[")) return Flow(t).value()
        if (t == "~" || t == "null") return null
        return unquote(t)
    }

    private class Flow(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i] == ' ') i++ }
        fun value(): Any? {
            ws()
            if (i >= s.length) return null
            return when (s[i]) {
                '{' -> { i++; val m = LinkedHashMap<String, Any?>(); ws()
                    while (i < s.length && s[i] != '}') {
                        val k = scalar(stopAtColon = true); ws()
                        if (i < s.length && s[i] == ':') i++
                        ws(); val v = if (i < s.length && (s[i] == ',' || s[i] == '}')) null else value()
                        m[k.toString()] = v; ws(); if (i < s.length && s[i] == ',') i++; ws()
                    }
                    i++; m }
                '[' -> { i++; val l = mutableListOf<Any?>(); ws()
                    while (i < s.length && s[i] != ']') { l += value(); ws(); if (i < s.length && s[i] == ',') i++; ws() }
                    i++; l }
                else -> scalar(stopAtColon = false).let { if (it == "~" || it == "null") null else it }
            }
        }
        fun scalar(stopAtColon: Boolean): String {
            ws()
            if (i < s.length && (s[i] == '"' || s[i] == '\'')) {
                val q = s[i]; val start = i; i++
                while (i < s.length) { if (s[i] == q) { if (q == '\'' && i + 1 < s.length && s[i + 1] == '\'') { i += 2; continue }; break }; if (s[i] == '\\' && q == '"') i++; i++ }
                i++
                return unquote(s.substring(start, minOf(i, s.length)))
            }
            val start = i
            while (i < s.length && s[i] != ',' && s[i] != '}' && s[i] != ']' &&
                !(stopAtColon && s[i] == ':' && (i + 1 >= s.length || s[i + 1] == ' '))) i++
            return s.substring(start, i).trim()
        }
    }
}

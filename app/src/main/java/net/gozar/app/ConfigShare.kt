package net.gozar.app

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.net.URLEncoder

object ConfigShare {

    fun toLink(c: ProxyConfig): String = when (c.protocol) {
        "vless" -> userLink("vless", c.uuid, c, includeEncryption = true)
        "trojan" -> userLink("trojan", c.password, c, includeEncryption = false)
        "vmess" -> vmessLink(c)
        "shadowsocks" -> ssLink(c)
        "hysteria2" -> hysteria2Link(c)
        "tuic" -> simpleLink("tuic", enc(c.uuid) + ":" + enc(c.password), c, listOf(
            "sni" to c.sni, "alpn" to c.alpn, "congestion_control" to c.method,
            "udp_relay_mode" to c.mode, "allow_insecure" to if (c.allowInsecure) "1" else ""))
        "hysteria" -> simpleLink("hysteria", "", c, listOf(
            "auth" to c.password, "peer" to c.sni, "alpn" to c.alpn,
            "upmbps" to c.hyUpMbps.takeIf { it > 0 }?.toString().orEmpty(),
            "downmbps" to c.hyDownMbps.takeIf { it > 0 }?.toString().orEmpty(),
            "obfs" to c.hyObfs, "obfsParam" to c.hyObfsPassword, "insecure" to if (c.allowInsecure) "1" else ""))
        "anytls" -> simpleLink("anytls", enc(c.password), c, listOf(
            "sni" to c.sni, "fp" to c.fingerprint, "insecure" to if (c.allowInsecure) "1" else ""))
        // The private key is never put in a share link.
        "ssh" -> {
            val t = c.extraJson().optJSONObject("transport")
            val tp = if (t == null) emptyList() else listOf(
                "mode" to t.optString("mode"),
                "proxy" to (t.optString("proxyHost").takeIf { it.isNotBlank() }?.let { it + ":" + t.optInt("proxyPort") } ?: ""),
                "sni" to t.optString("sni"),
                "payload" to t.optString("payload").takeIf { it.isNotEmpty() }?.let { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }.orEmpty(),
                "wspath" to t.optString("wsPath"), "wshost" to t.optString("wsHost"), "ua" to t.optString("ua"),
                "wsframing" to if (t.optBoolean("wsFraming")) "1" else "", "verify" to if (t.optBoolean("verify")) "1" else "")
            simpleLink("ssh", enc(c.uuid) + (if (c.password.isNotEmpty()) ":" + enc(c.password) else ""), c, listOf("hostkey" to c.publicKey) + tp)
        }
        "naive" -> simpleLink(if (c.mode == "quic") "naive+quic" else "naive+https",
            enc(c.uuid) + (if (c.password.isNotEmpty()) ":" + enc(c.password) else ""), c,
            listOf("sni" to c.sni.takeIf { it != c.address }.orEmpty()))
        "softether" -> c.extraJson().let { x ->
            simpleLink("softether", enc(c.uuid) + ":" + enc(c.password), c, listOf(
                "hub" to x.optString("hub"), "sni" to c.sni, "pin" to c.pinnedCertSha256,
                "allow_insecure" to if (c.allowInsecure) "1" else "", "auth" to if (x.optBoolean("plain")) "plain" else "",
                "ip" to x.optString("ip"), "gw" to x.optString("gw"), "dns" to x.optString("dns"),
                "mtu" to c.mtu.takeIf { it > 0 }?.toString().orEmpty()))
        }
        "sstp" -> simpleLink("sstp", enc(c.uuid) + ":" + enc(c.password), c, listOf(
            "sni" to c.sni, "auth" to c.method.takeIf { it == "pap" || it == "mschapv2" }.orEmpty(),
            "allow_insecure" to if (c.allowInsecure) "1" else "", "pin" to c.pinnedCertSha256,
            "mtu" to c.mtu.takeIf { it > 0 }?.toString().orEmpty()))
        "juicity" -> simpleLink("juicity", enc(c.uuid) + ":" + enc(c.password), c, listOf(
            "congestion_control" to c.method, "sni" to c.sni, "allow_insecure" to if (c.allowInsecure) "1" else "",
            "pinned_certchain_sha256" to c.pinnedCertSha256))
        "shadowtls" -> {
            val ss = c.extraJson().optJSONObject("ss") ?: org.json.JSONObject()
            val user = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString((ss.optString("method") + ":" + ss.optString("password")).toByteArray())
            val plugin = "shadow-tls;host=${c.sni};password=${c.password};version=${if (c.alterId in 1..3) c.alterId else 3}"
            val host = if (c.address.contains(':')) "[${c.address}]" else c.address
            "ss://$user@$host:${c.port}?plugin=" + enc(plugin) + "#" + enc(c.name)
        }
        "mieru", "brook" -> c.extraJson().optString("url").takeIf { it.isNotBlank() }?.let { it + "#" + enc(c.name) }.orEmpty()
        "amneziawg" -> c.extraJson().optString("conf").takeIf { it.isNotBlank() }?.let {
            "amneziawg://" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) + "#" + enc(c.name)
        }.orEmpty()
        "openconnect" -> simpleLink("openconnect", enc(c.uuid) + (if (c.password.isNotEmpty()) ":" + enc(c.password) else ""), c, listOf(
            "flavor" to c.mode, "sni" to c.sni, "pin" to c.pinnedCertSha256, "insecure" to if (c.allowInsecure) "1" else "",
            "mtu" to c.mtu.takeIf { it > 0 }?.toString().orEmpty(),
            "authgroup" to c.extraJson().optString("authGroup"), "os" to c.extraJson().optString("reportedOs"),
            "ua" to c.extraJson().optString("userAgent"),
            "reconnect" to c.extraJson().optInt("reconnect", 0).takeIf { it > 0 }?.toString().orEmpty(),
            "nodtls" to if (c.extraJson().optBoolean("noUdp")) "1" else "",
            "noipv6" to if (c.extraJson().optBoolean("ipv6Off")) "1" else ""))
        // The client certificate and key stay on this device: they are not put in share links.
        "masterdns", "stormdns", "cottendns" -> {
            val x = c.extraJson()
            val first = (if (c.address.contains(':')) "[${c.address}]" else c.address) + ":" + c.port
            val resolvers = (listOf(first) + x.optString("resolvers").split(',')).map { it.trim() }.filter { it.isNotEmpty() }
            val params = listOf("resolver" to resolvers.joinToString(","), "enc" to x.optInt("enc", 1).toString(),
                "transport" to c.mode.ifEmpty { "udp" })
            c.protocol + "://" + enc(c.password) + "@" + c.host + "?" + params.joinToString("&") { it.first + "=" + enc(it.second) } + "#" + enc(c.name)
        }
        "dnstt", "vaydns", "noizdns", "slipstream" -> {
            val user = enc(c.uuid) + (if (c.password.isNotEmpty()) ":" + enc(c.password) else "")
            val params = listOf("pubkey" to c.publicKey, "transport" to c.mode.ifEmpty { "udp" },
                if (c.mode == "doh") "doh" to c.path else "resolver" to (if (c.address.contains(':')) "[${c.address}]" else c.address) + ":" + c.port,
                "upstream" to c.method.ifEmpty { "socks" })
            val x = c.extraJson()
            val opts = listOfNotNull(
                x.optString("recordType").takeIf { it.isNotEmpty() }?.let { "record" to it },
                if (x.has("dnsttCompat")) "compat" to (if (x.optBoolean("dnsttCompat")) "1" else "0") else null,
                x.optInt("maxQnameLen", 0).takeIf { it > 0 }?.let { "qname" to it.toString() },
                x.optInt("clientIdSize", 0).takeIf { it > 0 }?.let { "clientid" to it.toString() },
                if (x.has("noiz")) "noiz" to (if (x.optBoolean("noiz")) "1" else "0") else null,
                if (x.has("stealth")) "stealth" to (if (x.optBoolean("stealth")) "1" else "0") else null,
                x.optString("authoritative").takeIf { it.isNotEmpty() }?.let { "authoritative" to it },
                x.optString("cc").takeIf { it.isNotEmpty() }?.let { "cc" to it })
            val query = (params + opts).filter { it.second.isNotEmpty() }.joinToString("&") { it.first + "=" + enc(it.second) }
            c.protocol + "://" + (if (user.isEmpty() || user == ":") "" else "$user@") + c.host + "?" + query + "#" + enc(c.name)
        }
        else -> ""
    }

    private fun simpleLink(scheme: String, userInfo: String, c: ProxyConfig, params: List<Pair<String, String>>): String {
        val query = params.filter { it.second.isNotEmpty() }.joinToString("&") { it.first + "=" + enc(it.second) }
        val host = if (c.address.contains(':')) "[" + c.address + "]" else c.address
        val user = if (userInfo.isEmpty() || userInfo == ":") "" else "$userInfo@"
        return "$scheme://$user$host:${c.port}" + (if (query.isEmpty()) "" else "?$query") + "#" + enc(c.name)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun hysteria2Link(c: ProxyConfig): String {
        val params = ArrayList<Pair<String, String>>()
        if (c.sni.isNotEmpty()) params.add("sni" to c.sni)
        if (c.alpn.isNotEmpty()) params.add("alpn" to c.alpn)
        if (c.allowInsecure) params.add("insecure" to "1")
        if (c.hyObfsPassword.isNotEmpty()) {
            params.add("obfs" to c.hyObfs.ifEmpty { "salamander" })
            params.add("obfs-password" to c.hyObfsPassword)
        }
        if (c.hyUpMbps > 0) params.add("upmbps" to c.hyUpMbps.toString())
        if (c.hyDownMbps > 0) params.add("downmbps" to c.hyDownMbps.toString())
        val query = params.joinToString("&") { it.first + "=" + enc(it.second) }
        val host = if (c.address.contains(':')) "[" + c.address + "]" else c.address
        return "hysteria2://" + enc(c.password) + "@" + host + ":" + c.port +
                (if (query.isEmpty()) "" else "?" + query) + "#" + enc(c.name)
    }

    private fun userLink(
        scheme: String,
        userInfo: String,
        c: ProxyConfig,
        includeEncryption: Boolean
    ): String {
        val params = ArrayList<Pair<String, String>>()
        params.add("type" to c.network)
        params.add("security" to c.security)
        if (includeEncryption && c.encryption.isNotEmpty()) params.add("encryption" to c.encryption)
        if (c.flow.isNotEmpty()) params.add("flow" to c.flow)
        if (c.sni.isNotEmpty()) params.add("sni" to c.sni)
        if (c.publicKey.isNotEmpty()) params.add("pbk" to c.publicKey)
        if (c.shortId.isNotEmpty()) params.add("sid" to c.shortId)
        if (c.fingerprint.isNotEmpty()) params.add("fp" to c.fingerprint)
        if (c.path.isNotEmpty()) params.add("path" to c.path)
        if (c.host.isNotEmpty()) params.add("host" to c.host)
        if (c.serviceName.isNotEmpty()) params.add("serviceName" to c.serviceName)
        if (c.mode.isNotEmpty()) params.add("mode" to c.mode)
        if (c.alpn.isNotEmpty()) params.add("alpn" to c.alpn)
        if (c.headerType.isNotEmpty()) params.add("headerType" to c.headerType)
        // The finalmask and ECH settings, each only when it is set. These are
        // not standard share-link keys, so a client that does not know them
        // ignores them and gets the same server it always did - while this app
        // reading its own link back gets the whole config.
        if (c.maskType.isNotEmpty()) params.add("mask" to c.maskType)
        if (c.maskDomain.isNotEmpty()) params.add("maskDomain" to c.maskDomain)
        if (c.maskPassword.isNotEmpty()) params.add("maskPass" to c.maskPassword)
        if (c.echConfigList.isNotEmpty()) params.add("ech" to c.echConfigList)
        val query = params.joinToString("&") { "${it.first}=${enc(it.second)}" }
        return "$scheme://${enc(userInfo)}@${c.address}:${c.port}?$query#${enc(c.name)}"
    }

    private fun vmessLink(c: ProxyConfig): String {
        val o = JSONObject()
        o.put("v", "2")
        o.put("ps", c.name)
        o.put("add", c.address)
        o.put("port", c.port.toString())
        o.put("id", c.uuid)
        o.put("aid", c.alterId.toString())
        o.put("scy", c.encryption.ifEmpty { "auto" })
        o.put("net", c.network)
        o.put("type", c.headerType.ifEmpty { "none" })
        o.put("host", c.host)
        o.put("path", c.path)
        o.put("tls", if (c.security == "tls") "tls" else "")
        o.put("sni", c.sni)
        o.put("fp", c.fingerprint)
        val b64 = Base64.encodeToString(o.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "vmess://$b64"
    }

    private fun ssLink(c: ProxyConfig): String {
        val userInfo = Base64.encodeToString(
            "${c.method}:${c.password}".toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return "ss://$userInfo@${c.address}:${c.port}#${enc(c.name)}"
    }

    const val QR_MAX_CHARS = 2300

    fun qrBitmap(
        text: String,
        size: Int = 720,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        if (text.isEmpty() || text.length > QR_MAX_CHARS) return null
        return runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    pixels[row + x] = if (matrix.get(x, y)) darkColor else lightColor
                }
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, w, 0, 0, w, h)
            }
        }.getOrNull()
    }
}
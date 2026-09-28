package net.gozar.app.configtoolkit

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * NPV Tunnel (NapsternetV) export containers: .npvs and the NPVO1 open export.
 *
 * Ported from Pantegnos (MIT, Copyright (c) 2026 FrontierTM,
 * https://github.com/FrontierTM/Pantegnos, internal/modules/impl/npvs*.go),
 * limited on purpose to what the person holding the file is entitled to open:
 *
 *  - NPVO1 open export: plain JSON, no key material at all.
 *  - NPVS v1 and v5 ("gen2") sealed with a **passphrase** the sharer gave the
 *    user: PBKDF2-HMAC-SHA256 + ChaCha20-Poly1305 (+ HKDF-SHA256 for v5).
 *
 * Deliberately NOT supported, and reported as protected instead:
 *
 *  - NPVS sealed with the vendor's embedded app key ("appKey", whitebox AES),
 *  - NPVS sealed to recipient public keys (needs the recipient's private key),
 *  - NPVT1 / NPVTSUB1 legacy sealed exports (whitebox AES with an app key).
 *
 * Those are locks the config's author chose; opening them would mean using a
 * key extracted from someone else's app, which this app does not do.
 */
object NpvContainer {

    sealed class Result {
        /** Share links / text lines, plus the author's message if there is one. */
        data class Opened(val lines: List<String>, val creatorMessage: String) : Result()
        object NeedsPassphrase : Result()
        object WrongPassphrase : Result()
        /** Sealed with a key the user does not hold; [why] is shown as is. */
        data class Protected(val why: String) : Result()
        data class Invalid(val why: String) : Result()
    }

    private const val OPEN_MARKER = "NPVO1"
    private const val SENTINEL_PREFIX = "npvs1:"
    private const val SENTINEL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=_-"
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MIN_ITERS = 1
    private const val MAX_ITERS = 10_000_000

    // v5 compact envelope constants (npvs_gen2.go).
    private const val GEN2_VERSION = 5
    private const val GEN2_HEADER_FIXED = 135
    private const val GEN2_KEY_BLOCK_OFFSET = 53
    private const val GEN2_RECIPIENT_PK = 32
    private const val GEN2_RECIPIENT_WRAP = 0x5d
    private const val GEN2_PASS_BLOCK = 80
    private const val GEN2_APPKEY_BLOCK = 78
    private const val GEN2_SIG = 64
    private const val GEN2_SENTINEL_SEQ = 0xFFFF
    private const val METHOD_RECIPIENT = 0
    private const val METHOD_PASS = 1
    private const val METHOD_APPKEY = 2

    fun isContainer(bytes: ByteArray): Boolean {
        val head = String(bytes.copyOfRange(0, minOf(bytes.size, 16)), Charsets.ISO_8859_1).trimStart()
        return head.startsWith("NPVS") || head.startsWith(OPEN_MARKER) ||
            head.startsWith("NPVT1") || head.startsWith("NPVTSUB1")
    }

    fun open(bytes: ByteArray, passphrase: String?): Result {
        if (bytes.size > MAX_BYTES) return Result.Invalid("حجم فایل بیش از حد مجاز است.")
        val head = String(bytes.copyOfRange(0, minOf(bytes.size, 16)), Charsets.ISO_8859_1).trimStart()
        return when {
            head.startsWith(OPEN_MARKER) -> openExport(bytes)
            head.startsWith("NPVTSUB1") || head.startsWith("NPVT1") ->
                Result.Protected("این فایل NPVT قدیمی است که با کلید داخلی خود اپ NPV قفل شده؛ باز کردنش یعنی دور زدن قفل سازنده و پشتیبانی نمی‌شود. از سازنده لینک یا خروجی باز (NPVO1) بخواهید.")
            bytes.size >= 9 && bytes[0] == 'N'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'V'.code.toByte() && bytes[3] == 'S'.code.toByte() ->
                if (bytes[4].toInt() == GEN2_VERSION) openGen2(bytes, passphrase) else openV1(bytes, passphrase)
            else -> Result.Invalid("این فایل NPV شناخته نشد.")
        }
    }

    // ------------------------------------------------------------ NPVO1 open

    private fun openExport(bytes: ByteArray): Result {
        val body = String(bytes, Charsets.UTF_8).replace("$OPEN_MARKER\n", "").replace(OPEN_MARKER, "")
        if (body.isBlank()) return Result.Invalid("فایل NPVO1 خالی است.")
        val configs = runCatching { JSONObject(body).getJSONArray("configs") }.getOrNull()
            ?: return Result.Invalid("محتوای NPVO1 فهرست کانفیگ نیست.")
        if (configs.length() == 0) return Result.Invalid("فایل NPVO1 کانفیگی ندارد.")
        val lines = (0 until configs.length()).map { link(decodeSentinels(configs.get(it))) }
        return Result.Opened(lines.filter { it.isNotBlank() }, "")
    }

    // ---------------------------------------------------------------- NPVS v1

    private fun openV1(b: ByteArray, passphrase: String?): Result {
        if (b.size < 89) return Result.Invalid("فایل NPVS ناقص است.")
        if (b[5] > 1) return Result.Invalid("نسخهٔ این فایل NPVS پشتیبانی نمی‌شود.")
        val hdrLen = ByteBuffer.wrap(b, 5, 4).int
        if (hdrLen < 0 || 9 + hdrLen > b.size) return Result.Invalid("سرآیند NPVS خراب است.")
        val headerRaw = b.copyOfRange(9, 9 + hdrLen)
        val hdr = runCatching { JSONObject(String(headerRaw, Charsets.UTF_8)) }.getOrNull()
            ?: return Result.Invalid("سرآیند NPVS خوانده نشد.")
        var off = 9 + hdrLen
        if (off + 16 > b.size) return Result.Invalid("فایل NPVS ناقص است.")
        val nonce = b.copyOfRange(off, off + 12)
        val bodyLen = ByteBuffer.wrap(b, off + 12, 4).int
        off += 16
        if (bodyLen < 16 || off + bodyLen + 64 > b.size) return Result.Invalid("بدنهٔ NPVS خراب است.")
        val body = b.copyOfRange(off, off + bodyLen)
        val message = creatorMessage(hdr.optJSONObject("policy"))

        val pass = hdr.optJSONObject("passphrase")
        if (hdr.optJSONObject("appKey") != null) return Result.Protected(appKeyWhy())
        if (pass == null) {
            return Result.Protected(if ((hdr.optJSONArray("recipients")?.length() ?: 0) > 0)
                "این فایل برای گیرندهٔ مشخصی رمز شده و فقط با کلید خصوصی همان گیرنده باز می‌شود."
            else "روش قفل این فایل شناخته نشد.")
        }
        if (passphrase.isNullOrEmpty()) return Result.NeedsPassphrase
        if (pass.optString("kdf") != "pbkdf2-hmac-sha256") return Result.Invalid("روش رمز این فایل پشتیبانی نمی‌شود.")
        val iters = pass.optInt("iters")
        if (iters !in MIN_ITERS..MAX_ITERS) return Result.Invalid("تعداد تکرار رمز نامعتبر است.")
        val salt = b64Url(pass.optString("salt")) ?: return Result.Invalid("salt نامعتبر است.")
        val wrap = b64Url(pass.optString("wrap")) ?: return Result.Invalid("wrap نامعتبر است.")
        if (wrap.size != 60) return Result.Invalid("wrap نامعتبر است.")
        val kek = pbkdf2(passphrase.toByteArray(Charsets.UTF_8), salt, iters, 32)
        val dek = ChaCha20Poly1305.open(kek, wrap.copyOfRange(0, 12), wrap.copyOfRange(12, wrap.size), salt)
            ?: return Result.WrongPassphrase
        val pt = ChaCha20Poly1305.open(dek, nonce, body, headerRaw)
            ?: return Result.Invalid("محتوای فایل باز نشد (فایل دستکاری شده است).")
        val text = decodeSentinels(String(pt, Charsets.UTF_8))
        return Result.Opened(linesFromPayload(text), message)
    }

    // ------------------------------------------------------ NPVS v5 (gen2)

    private fun openGen2(b: ByteArray, passphrase: String?): Result {
        val hdrLen = ByteBuffer.wrap(b, 5, 4).int
        if (hdrLen < GEN2_HEADER_FIXED || 9 + hdrLen > b.size) return Result.Invalid("سرآیند NPVS خراب است.")
        val h = b.copyOfRange(9, 9 + hdrLen)
        if (h[0].toInt() != 1) return Result.Invalid("نسخهٔ سرآیند NPVS پشتیبانی نمی‌شود.")
        val method = h[50].toInt() and 0xFF
        val recipients = ByteBuffer.wrap(h, 51, 2).short.toInt() and 0xFFFF
        if (recipients > 0x400) return Result.Invalid("سرآیند NPVS خراب است.")
        when (method) {
            METHOD_APPKEY -> return Result.Protected(appKeyWhy())
            METHOD_RECIPIENT -> return Result.Protected("این فایل برای گیرندهٔ مشخصی رمز شده و فقط با کلید خصوصی همان گیرنده باز می‌شود.")
            METHOD_PASS -> Unit
            else -> return Result.Invalid("روش قفل این فایل شناخته نشد.")
        }
        var off = GEN2_KEY_BLOCK_OFFSET + recipients * (GEN2_RECIPIENT_PK + GEN2_RECIPIENT_WRAP)
        if (off + GEN2_PASS_BLOCK + 4 > h.size) return Result.Invalid("فایل NPVS ناقص است.")
        val iters = ByteBuffer.wrap(h, off, 4).int
        if (iters !in MIN_ITERS..MAX_ITERS) return Result.Invalid("تعداد تکرار رمز نامعتبر است.")
        val salt = h.copyOfRange(off + 4, off + 20)
        val wrap = h.copyOfRange(off + 20, off + GEN2_PASS_BLOCK)
        off += GEN2_PASS_BLOCK
        if (off + 4 > h.size) return Result.Invalid("فایل NPVS ناقص است.")
        val metaLen = ByteBuffer.wrap(h, off, 4).int
        if (metaLen < 16 || off + 4 + metaLen > h.size) return Result.Invalid("فایل NPVS ناقص است.")
        val prefix = h.copyOfRange(0, off)
        val metaBlob = h.copyOfRange(off + 4, off + 4 + metaLen)

        var boff = 9 + hdrLen
        if (boff + 16 > b.size) return Result.Invalid("فایل NPVS ناقص است.")
        val nonce = b.copyOfRange(boff, boff + 12)
        val bodyLen = ByteBuffer.wrap(b, boff + 12, 4).int
        boff += 16
        if (bodyLen < 32 || boff + bodyLen + GEN2_SIG > b.size) return Result.Invalid("بدنهٔ NPVS خراب است.")
        val body = b.copyOfRange(boff, boff + bodyLen)

        if (passphrase.isNullOrEmpty()) return Result.NeedsPassphrase
        val kdk = pbkdf2(passphrase.toByteArray(Charsets.UTF_8), salt, iters, 32)
        val dek = ChaCha20Poly1305.open(kdk, wrap.copyOfRange(0, 12), wrap.copyOfRange(12, wrap.size), salt)
            ?: return Result.WrongPassphrase
        val metadata = ChaCha20Poly1305.open(hkdf(dek, nonce, "NPVS-v5/metadata"), nonce, metaBlob, prefix)
            ?: return Result.Invalid("محتوای فایل باز نشد (فایل دستکاری شده است).")

        // NPF body: magic, contentId(32), count(2), rows of seq(2) flags(2) len(2) blob.
        if (body.size < 66 || String(body, 0, 4, Charsets.ISO_8859_1) != "NPF\u0001") return Result.Invalid("بدنهٔ NPVS خراب است.")
        val contentId = body.copyOfRange(4, 36)
        val count = ByteBuffer.wrap(body, 36, 2).short.toInt() and 0xFFFF
        var p = 38
        val fields = HashMap<Int, ByteArray>()
        repeat(count) {
            if (p + 6 > body.size) return Result.Invalid("بدنهٔ NPVS ناقص است.")
            val seq = ByteBuffer.wrap(body, p, 2).short.toInt() and 0xFFFF
            val len = ByteBuffer.wrap(body, p + 4, 2).short.toInt() and 0xFFFF
            if (p + 6 + len > body.size) return Result.Invalid("بدنهٔ NPVS ناقص است.")
            val blob = body.copyOfRange(p + 6, p + 6 + len)
            p += 6 + len
            val ptLen = blob.size - 16
            if (ptLen < 0) return@repeat
            val key = hkdf(dek, contentId, "NPV-fields-v1/field/", be16(seq))
            val aad = "NPV-fields-v1/record/".toByteArray() + contentId + be16(seq) + be32(ptLen)
            ChaCha20Poly1305.open(key, ByteArray(12), blob, aad)?.let { fields[seq] = it }
        }
        val table = fields[GEN2_SENTINEL_SEQ] ?: return Result.Invalid("فهرست کانفیگ در فایل پیدا نشد.")
        val configs = runCatching { JSONObject(String(table, Charsets.UTF_8)).getJSONArray("configs") }.getOrNull()
            ?: return Result.Invalid("فهرست کانفیگ در فایل خوانده نشد.")
        val lines = (0 until configs.length()).map { link(substitute(configs.get(it), fields)) }
        val meta = runCatching { JSONObject(String(metadata, Charsets.UTF_8)).optJSONObject("policy") }.getOrNull()
        return Result.Opened(lines.filter { it.isNotBlank() }, creatorMessage(meta))
    }

    private fun appKeyWhy() =
        "این فایل با کلید داخلی خود اپ NPV قفل شده و رمزی برای شما ندارد؛ باز کردنش یعنی دور زدن قفل سازنده و پشتیبانی نمی‌شود. " +
            "از سازنده بخواهید خروجی با رمز عبور یا لینک مستقیم بدهد."

    private fun creatorMessage(policy: JSONObject?): String = listOfNotNull(
        policy?.optString("displayMessage"), policy?.optString("customServerMessage")
    ).map { it.replace("\r\n", "\n").trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    private fun linesFromPayload(text: String): List<String> {
        val t = text.trim()
        val configs = runCatching { JSONObject(t).optJSONArray("configs") }.getOrNull()
            ?: runCatching { JSONArray(t) }.getOrNull()
        if (configs != null) return (0 until configs.length()).map { link(decodeSentinels(configs.get(it))) }.filter { it.isNotBlank() }
        return t.lines().map { it.trim() }.filter { Regex("^[a-z0-9]+://", RegexOption.IGNORE_CASE).containsMatchIn(it) }
    }

    // ------------------------------------------------ config -> share link

    internal fun link(cfg: Any?): String {
        val obj = cfg as? JSONObject ?: return text(cfg)
        val remarks = text(obj.opt("name"))
        val address = text(obj.opt("address"))
        obj.optJSONObject("v2rayProfile")?.let { return v2rayLink(remarks, address, flat(it)) }
        obj.optJSONObject("sshConfig")?.let { return sshLink(remarks, flat(it)) }
        for (kind in listOf("socksConfig", "socksProfile", "httpConfig", "httpProfile", "proxyConfig")) {
            obj.optJSONObject(kind)?.let { return proxyLink(remarks, address, kind, flat(it)) }
        }
        return ""
    }

    private fun v2rayLink(remarks: String, address: String, p: Map<String, String>): String {
        val host = or(p["server"], address)
        val port = int(p["serverPort"]).takeIf { it > 0 } ?: int(p["port"])
        return when (int(p["configType"])) {
            1 -> vmessLink(host, port, remarks, p)
            5 -> "vless://${p["password"].orEmpty()}@$host:$port?" +
                query(streamQuery(p) + listOfNotNull("encryption" to or(p["method"], "none"), p["flow"]?.takeIf { it.isNotBlank() }?.let { "flow" to it })) +
                "#" + esc(remarks)
            6 -> "trojan://${esc(p["password"].orEmpty())}@$host:$port?" +
                query(streamQuery(p) + listOfNotNull(p["flow"]?.takeIf { it.isNotBlank() }?.let { "flow" to it })) + "#" + esc(remarks)
            3 -> "ss://" + Base64.getEncoder().encodeToString("${p["method"].orEmpty()}:${p["password"].orEmpty()}".toByteArray()) +
                "@$host:$port#" + esc(remarks)
            else -> ""
        }
    }

    private fun vmessLink(host: String, port: Int, remarks: String, p: Map<String, String>): String {
        var headerType = or(p["headerType"], "none")
        var hostHeader = p["host"].orEmpty()
        var path = p["path"].orEmpty()
        when (network(p)) {
            "kcp" -> path = or(path, p["seed"])
            "grpc" -> { headerType = p["mode"].orEmpty(); path = or(path, p["serviceName"]); hostHeader = or(hostHeader, p["authority"]) }
        }
        val security = p["security"].orEmpty()
        val o = JSONObject()
            .put("v", "2").put("ps", remarks).put("add", host).put("port", port.toString())
            .put("id", p["password"].orEmpty()).put("aid", int(p["alterId"]).toString())
            .put("scy", or(p["method"], "auto")).put("net", or(p["network"], "tcp")).put("type", headerType)
            .put("host", hostHeader).put("path", path)
            .put("tls", if (security == "tls" || security == "reality") "tls" else "")
            .put("sni", p["sni"].orEmpty()).put("fp", p["fingerPrint"].orEmpty()).put("alpn", p["alpn"].orEmpty())
            .put("insecure", if (security != "tls") "" else if (allowInsecure(p)) "1" else "0")
        return "vmess://" + Base64.getEncoder().encodeToString(o.toString().toByteArray())
    }

    private fun network(p: Map<String, String>): String {
        val n = p["network"].orEmpty().trim().lowercase()
        return if (n in setOf("tcp", "kcp", "ws", "httpupgrade", "xhttp", "http", "h2", "grpc")) n else "tcp"
    }

    private fun streamQuery(p: Map<String, String>): List<Pair<String, String>> {
        val network = or(p["network"], "tcp")
        val security = or(p["security"], "none")
        val q = mutableListOf("type" to network, "security" to security)
        fun set(k: String, v: String?) { if (!v.isNullOrEmpty()) q += k to v }
        when (network) {
            "ws" -> { set("path", p["path"]); set("host", p["host"]) }
            "grpc" -> { set("serviceName", p["serviceName"]); set("mode", or(p["mode"], p["xhttpMode"], "gun")); set("authority", p["authority"]) }
            "kcp" -> { set("headerType", p["headerType"]); set("seed", p["seed"]) }
            "quic" -> { set("key", p["key"]); set("headerType", p["headerType"]) }
            "xhttp", "httpupgrade" -> { set("path", p["path"]); set("host", p["host"]); set("mode", or(p["xhttpMode"], p["mode"])); set("extra", p["xhttpExtra"]) }
            "tcp", "raw" -> p["headerType"]?.takeIf { it.isNotEmpty() && it != "none" }?.let {
                q += "headerType" to it; set("host", p["host"]); set("path", p["path"])
            }
        }
        when (security) {
            "tls" -> { set("sni", p["sni"]); set("fp", p["fingerPrint"]); set("alpn", p["alpn"]); if (allowInsecure(p)) q += "allowInsecure" to "1" }
            "reality" -> { set("sni", p["sni"]); set("fp", p["fingerPrint"]); set("pbk", p["publicKey"]); set("sid", p["shortId"]); set("spx", p["spiderX"]) }
        }
        return q
    }

    private fun sshLink(remarks: String, s: Map<String, String>): String {
        val target = s["sshHost"].orEmpty() + (s["sshPort"]?.takeIf { it.isNotEmpty() }?.let { ":$it" } ?: "")
        val q = listOfNotNull(
            remarks.takeIf { it.isNotEmpty() }?.let { "remarks" to it },
            s["sshConfigType"]?.takeIf { it.isNotEmpty() }?.let { "sshConfigType" to it },
            s["httpProxy"]?.takeIf { it.isNotEmpty() }?.let { "httpProxy" to it }
        )
        return "ssh://${esc(s["sshUsername"].orEmpty())}:${esc(s["sshPassword"].orEmpty())}@$target?${query(q)}#${s["payload"].orEmpty()}"
    }

    private fun proxyLink(remarks: String, address: String, kind: String, p: Map<String, String>): String {
        val scheme = kind.removeSuffix("Config").removeSuffix("Profile")
        var target = or(p["server"], or(p["host"], address))
        or(p["serverPort"], or(p["port"], p["localPort"])).takeIf { it.isNotEmpty() }?.let { target += ":$it" }
        val skip = setOf("remarks", "server", "host", "port", "serverPort", "localPort", "username", "password")
        val q = listOfNotNull(remarks.takeIf { it.isNotEmpty() }?.let { "remarks" to it }) +
            p.keys.sorted().filter { it !in skip && p[it].orEmpty().isNotEmpty() }.map { it to p[it]!! }
        val user = p["username"].orEmpty()
        val auth = when {
            user.isNotEmpty() -> "${esc(user)}:${esc(p["password"].orEmpty())}@"
            p["password"].orEmpty().isNotEmpty() -> "${esc(p["password"]!!)}@"
            else -> ""
        }
        return "$scheme://$auth$target" + (if (q.isNotEmpty()) "?" + query(q) else "")
    }

    // ------------------------------------------------------------- helpers

    private fun substitute(v: Any?, fields: Map<Int, ByteArray>): Any? = when (v) {
        is Number -> decodeSentinels(fieldText(fields[v.toInt()]))
        is JSONObject -> JSONObject().also { out -> v.keys().forEach { k -> out.put(k, substitute(v.opt(k), fields)) } }
        is JSONArray -> JSONArray().also { out -> for (i in 0 until v.length()) out.put(substitute(v.opt(i), fields)) }
        else -> v
    }

    private fun fieldText(raw: ByteArray?): String {
        val s = raw?.toString(Charsets.UTF_8)?.trim().orEmpty()
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
            return runCatching { JSONArray("[$s]").getString(0) }.getOrElse { s.substring(1, s.length - 1) }
        }
        return s
    }

    private fun decodeSentinels(v: Any?): Any? = when (v) {
        is String -> decodeSentinels(v)
        is JSONObject -> JSONObject().also { out -> v.keys().forEach { k -> out.put(k, decodeSentinels(v.opt(k))) } }
        is JSONArray -> JSONArray().also { out -> for (i in 0 until v.length()) out.put(decodeSentinels(v.opt(i))) }
        else -> v
    }

    internal fun decodeSentinels(s: String): String {
        val sb = StringBuilder()
        var rest = s
        while (true) {
            val i = rest.indexOf(SENTINEL_PREFIX)
            if (i < 0) { sb.append(rest); return sb.toString() }
            sb.append(rest, 0, i)
            rest = rest.substring(i + SENTINEL_PREFIX.length)
            var j = 0
            while (j < rest.length && SENTINEL_ALPHABET.indexOf(rest[j]) >= 0) j++
            val tok = rest.substring(0, j)
            rest = rest.substring(j)
            val dec = sentinel(tok)
            if (dec == null) sb.append(SENTINEL_PREFIX).append(tok) else sb.append(String(dec, Charsets.UTF_8))
        }
    }

    private fun sentinel(tok: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(tok) }.getOrNull()
            ?: runCatching { Base64.getUrlDecoder().decode(tok) }.getOrNull()
            ?: runCatching { Base64.getUrlDecoder().decode(tok + "=".repeat((4 - tok.length % 4) % 4)) }.getOrNull()

    private fun flat(o: JSONObject): Map<String, String> = o.keys().asSequence().associateWith { text(o.opt(it)) }

    private fun text(v: Any?): String = when (v) {
        null, JSONObject.NULL -> ""
        is String -> v
        is Double -> if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
        is Number, is Boolean -> v.toString()
        else -> v.toString()
    }

    private fun int(s: String?): Int = s?.trim()?.toIntOrNull() ?: 0
    private fun or(vararg values: String?): String = values.firstOrNull { !it.isNullOrEmpty() }.orEmpty()
    private fun allowInsecure(p: Map<String, String>): Boolean {
        for (k in listOf("tlsAllowInsecure", "insecure")) {
            when (p[k].orEmpty().trim().lowercase()) { "true", "1", "yes" -> return true; "false", "0", "no" -> return false }
        }
        return false
    }
    private fun esc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun query(q: List<Pair<String, String>>) = q.sortedBy { it.first }.joinToString("&") { (k, v) -> "${esc(k)}=${esc(v)}" }
    private fun be16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())
    private fun be32(v: Int) = ByteBuffer.allocate(4).putInt(v).array()

    private fun b64Url(s: String): ByteArray? = runCatching {
        Base64.getUrlDecoder().decode(s + "=".repeat((4 - s.length % 4) % 4))
    }.getOrNull()

    /** PBKDF2-HMAC-SHA256, identical to Pantegnos' customPBKDF2HmacSha256. */
    internal fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        require(password.isNotEmpty()) { "empty passphrase" }
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password, "HmacSHA256")) }
        val out = ByteArray(dkLen)
        var written = 0
        var block = 1
        while (written < dkLen) {
            mac.update(salt)
            mac.update(be32(block))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 2..iterations) {
                u = mac.doFinal(u)
                for (k in t.indices) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
            }
            val n = minOf(32, dkLen - written)
            System.arraycopy(t, 0, out, written, n)
            written += n
            block++
        }
        return out
    }

    /** HKDF-SHA256 (RFC 5869), 32-byte output; an empty salt is 32 zero bytes. */
    internal fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, extra: ByteArray = ByteArray(0)): ByteArray {
        val s = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(s, "HmacSHA256")); doFinal(ikm) }
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(prk, "HmacSHA256"))
            update(info.toByteArray(Charsets.ISO_8859_1)); update(extra); update(1)
            doFinal()
        }
    }
}

/**
 * ChaCha20-Poly1305 AEAD (RFC 8439), open only.
 *
 * Written out because the platform cipher needs API 28 and this app supports
 * 26; unit tests check it against the JDK's own implementation.
 */
internal object ChaCha20Poly1305 {

    /** Plaintext, or null when the tag does not verify. [ctTag] is ciphertext || 16-byte tag. */
    fun open(key: ByteArray, nonce: ByteArray, ctTag: ByteArray, aad: ByteArray): ByteArray? {
        if (key.size != 32 || nonce.size != 12 || ctTag.size < 16) return null
        val ct = ctTag.copyOfRange(0, ctTag.size - 16)
        val tag = ctTag.copyOfRange(ctTag.size - 16, ctTag.size)
        val polyKey = block(key, 0, nonce).copyOfRange(0, 32)
        val expected = poly1305(polyKey, macData(aad, ct))
        if (!MessageDigest.isEqual(expected, tag)) return null
        return xor(key, nonce, ct, 1)
    }

    /** Used by tests to build fixtures. */
    fun seal(key: ByteArray, nonce: ByteArray, pt: ByteArray, aad: ByteArray): ByteArray {
        val ct = xor(key, nonce, pt, 1)
        val polyKey = block(key, 0, nonce).copyOfRange(0, 32)
        return ct + poly1305(polyKey, macData(aad, ct))
    }

    private fun macData(aad: ByteArray, ct: ByteArray): ByteArray {
        fun pad(n: Int) = ByteArray((16 - n % 16) % 16)
        val lens = ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putLong(aad.size.toLong()).putLong(ct.size.toLong()).array()
        return aad + pad(aad.size) + ct + pad(ct.size) + lens
    }

    private fun xor(key: ByteArray, nonce: ByteArray, input: ByteArray, counter0: Int): ByteArray {
        val out = ByteArray(input.size)
        var counter = counter0
        var off = 0
        while (off < input.size) {
            val ks = block(key, counter++, nonce)
            val n = minOf(64, input.size - off)
            for (i in 0 until n) out[off + i] = (input[off + i].toInt() xor ks[i].toInt()).toByte()
            off += n
        }
        return out
    }

    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun block(key: ByteArray, counter: Int, nonce: ByteArray): ByteArray {
        val s = IntArray(16)
        s[0] = 0x61707865; s[1] = 0x3320646e; s[2] = 0x79622d32; s[3] = 0x6b206574
        for (i in 0 until 8) s[4 + i] = le32(key, i * 4)
        s[12] = counter
        for (i in 0 until 3) s[13 + i] = le32(nonce, i * 4)
        val x = s.copyOf()
        fun qr(a: Int, b: Int, c: Int, d: Int) {
            x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 16)
            x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 12)
            x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 8)
            x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 7)
        }
        repeat(10) {
            qr(0, 4, 8, 12); qr(1, 5, 9, 13); qr(2, 6, 10, 14); qr(3, 7, 11, 15)
            qr(0, 5, 10, 15); qr(1, 6, 11, 12); qr(2, 7, 8, 13); qr(3, 4, 9, 14)
        }
        val out = ByteArray(64)
        for (i in 0 until 16) {
            val v = x[i] + s[i]
            out[i * 4] = v.toByte(); out[i * 4 + 1] = (v ushr 8).toByte()
            out[i * 4 + 2] = (v ushr 16).toByte(); out[i * 4 + 3] = (v ushr 24).toByte()
        }
        return out
    }

    private fun poly1305(key: ByteArray, msg: ByteArray): ByteArray {
        val p = java.math.BigInteger.ONE.shiftLeft(130).subtract(java.math.BigInteger.valueOf(5))
        val rBytes = key.copyOfRange(0, 16).also {
            it[3] = (it[3].toInt() and 15).toByte(); it[7] = (it[7].toInt() and 15).toByte()
            it[11] = (it[11].toInt() and 15).toByte(); it[15] = (it[15].toInt() and 15).toByte()
            it[4] = (it[4].toInt() and 252).toByte(); it[8] = (it[8].toInt() and 252).toByte(); it[12] = (it[12].toInt() and 252).toByte()
        }
        fun leInt(b: ByteArray) = java.math.BigInteger(1, b.reversedArray())
        val r = leInt(rBytes)
        val s = leInt(key.copyOfRange(16, 32))
        var acc = java.math.BigInteger.ZERO
        var i = 0
        while (i < msg.size) {
            val n = minOf(16, msg.size - i)
            val chunk = msg.copyOfRange(i, i + n) + byteArrayOf(1)
            acc = acc.add(leInt(chunk)).multiply(r).mod(p)
            i += n
        }
        val tag = acc.add(s).toByteArray().reversedArray()
        return ByteArray(16) { if (it < tag.size) tag[it] else 0 }
    }
}

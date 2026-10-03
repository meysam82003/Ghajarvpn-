package net.gozar.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Versioned, additive profile settings. Absent keys retain the 1.0.10 core defaults.
 * Evidence: sing-box 132b38e9 option/{anytls,tuic,http,hysteria2,openconnect,tls}.go.
 * This table is the settings capability contract used by forms, URI import and generators.
 */
object EngineSettings {
    enum class Type { SECONDS, COUNT, BOOL, PORTS, TEXT, SECRET, PEM, ECH, TOKEN, PROXY, DNS, TLS_MIN, MTU, COMPRESSION, FORM_ENTRIES }
    data class Setting(val key: String, val label: String, val type: Type, val protocols: Set<String>, val path: String = key,
        val share: Boolean = true, val hint: String = "")
    private val quic = setOf("tuic", "hysteria2")
    private val ech = setOf("anytls", "tuic", "hysteria2")
    val all = listOf(
        Setting("http_proxy", "پروکسی HTTP CONNECT", Type.PROXY, setOf("sstp"), share = false, hint = "http://user:pass@host:port یا https://host:port"),
        Setting("dns_fallback", "DNS جایگزین سرور", Type.DNS, setOf("sstp"), hint = "فقط اگر سرور DNS ندهد؛ IPها با کاما"),
        Setting("tls_min", "حداقل نسخهٔ TLS", Type.TLS_MIN, setOf("sstp"), hint = "1.2 یا 1.3"),
        Setting("idle_session_check_interval", "فاصلهٔ بررسی نشست بیکار (ثانیه)", Type.SECONDS, setOf("anytls")),
        Setting("idle_session_timeout", "مهلت نشست بیکار (ثانیه)", Type.SECONDS, setOf("anytls")),
        Setting("min_idle_session", "حداقل نشست آماده", Type.COUNT, setOf("anytls")),
        Setting("udp_over_stream", "ارسال UDP روی stream", Type.BOOL, setOf("tuic"), hint = "با udp_relay_mode هم‌زمان قابل استفاده نیست؛ سرور باید پشتیبانی کند."),
        Setting("heartbeat", "فاصلهٔ heartbeat (ثانیه)", Type.SECONDS, setOf("tuic")),
        Setting("idle_timeout", "مهلت اتصال بیکار (ثانیه)", Type.SECONDS, quic),
        Setting("server_ports", "پورت‌های چرخشی", Type.PORTS, setOf("hysteria2"), hint = "443,20000-21000؛ سرور باید این بازه را به سرویس هدایت کند."),
        Setting("hop_interval", "فاصلهٔ تعویض پورت (ثانیه)", Type.SECONDS, setOf("hysteria2")),
        Setting("ech_config", "پیکربندی ECH", Type.ECH, ech, "tls.ech.config", hint = "ECHConfigList به صورت Base64؛ نیازمند پشتیبانی سرور. در صورت رد ECH اتصال بدون آن ادامه نمی‌یابد."),
        Setting("dpd_interval", "فاصلهٔ DPD (ثانیه)", Type.SECONDS, setOf("openconnect"), "dpd_interval", share = true, hint = ""),
        Setting("trojan_interval", "فاصلهٔ ارزیابی Trojan (ثانیه)", Type.SECONDS, setOf("openconnect"), "trojan_interval", share = true, hint = ""),
        Setting("base_mtu", "MTU پایه", Type.MTU, setOf("openconnect"), "base_mtu", share = true, hint = ""),
        Setting("queue_length", "طول صف", Type.COUNT, setOf("openconnect"), "queue_length", share = true, hint = ""),
        Setting("tcp_keep_alive_enabled", "TCP keepalive", Type.BOOL, setOf("openconnect"), "tcp_keep_alive_enabled", share = true, hint = ""),
        Setting("compression_disabled", "غیرفعال کردن فشرده‌سازی", Type.BOOL, setOf("openconnect"), "compression_disabled", share = true, hint = ""),
        Setting("http_keepalive_disabled", "غیرفعال کردن HTTP keep-alive", Type.BOOL, setOf("openconnect"), "http_keepalive_disabled", share = true, hint = ""),
        Setting("xml_post_disabled", "غیرفعال کردن XML POST", Type.BOOL, setOf("openconnect"), "xml_post_disabled", share = true, hint = ""),
        Setting("external_auth_disabled", "غیرفعال کردن احراز هویت خارجی", Type.BOOL, setOf("openconnect"), "external_auth_disabled", share = true, hint = ""),
        Setting("password_authentication_disabled", "غیرفعال کردن احراز هویت با رمز", Type.BOOL, setOf("openconnect"), "password_authentication_disabled", share = true, hint = ""),
        Setting("pfs", "Perfect Forward Secrecy", Type.BOOL, setOf("openconnect"), "pfs", share = true, hint = ""),
        Setting("allow_insecure_crypto", "رمزنگاری قدیمی و ناامن", Type.BOOL, setOf("openconnect"), "allow_insecure_crypto", share = true, hint = "فقط برای درگاه قدیمی؛ امنیت اتصال را کاهش می‌دهد."),
        Setting("compression_mode", "نوع فشرده‌سازی", Type.COMPRESSION, setOf("openconnect"), "compression_mode", share = true, hint = "stateless یا all"),
        Setting("client_version", "نسخهٔ کلاینت گزارش‌شده", Type.TEXT, setOf("openconnect"), "version", share = true, hint = ""),
        Setting("local_hostname", "نام میزبان محلی", Type.TEXT, setOf("openconnect"), "local_hostname", share = false, hint = ""),
        Setting("token_pin", "PIN توکن", Type.SECRET, setOf("openconnect"), "token.pin", share = false, hint = ""),
        Setting("token_password", "رمز توکن", Type.SECRET, setOf("openconnect"), "token.password", share = false, hint = ""),
        Setting("token_device_id", "شناسهٔ دستگاه توکن", Type.SECRET, setOf("openconnect"), "token.device_id", share = false, hint = ""),
        Setting("form_entries", "فیلدهای احراز هویت درگاه (JSON)", Type.FORM_ENTRIES, setOf("openconnect"), "form_entries", share = false, hint = "آرایهٔ form_id، submission_key، name، value و promote؛ مقادیر محرمانه اشتراک عمومی نمی‌شوند."),
        Setting("cookie", "کوکی احراز هویت سازمانی", Type.SECRET, setOf("openconnect"), share = false),
        Setting("token_mode", "روش توکن", Type.TOKEN, setOf("openconnect"), "token.mode", share = false),
        Setting("token_secret", "کلید یا توکن احراز هویت", Type.SECRET, setOf("openconnect"), "token.secret", share = false),
        Setting("ca", "گواهی CA سازمان", Type.PEM, setOf("openconnect"), "tls.certificate_authority", share = false),
        Setting("key_password", "رمز کلید خصوصی", Type.SECRET, setOf("openconnect"), "tls.client_key_password", share = false),
        Setting("mca_cert", "گواهی دوم سازمان (MCA)", Type.PEM, setOf("openconnect"), "tls.mca_certificate", share = false),
        Setting("mca_key", "کلید خصوصی MCA", Type.PEM, setOf("openconnect"), "tls.mca_key", share = false),
        Setting("mca_key_password", "رمز کلید MCA", Type.SECRET, setOf("openconnect"), "tls.mca_key_password", share = false)
    )
    fun supported(protocol: String) = all.filter { protocol in it.protocols }
    fun read(c: ProxyConfig): Map<String, String> {
        val o = c.extraJson().optJSONObject("engineSettings") ?: return emptyMap()
        require(o.optInt("version") == 1) { "نسخهٔ تنظیمات این اتصال پشتیبانی نمی‌شود" }
        return supported(c.protocol).filter { o.has(it.key) }.associate { it.key to o.getString(it.key) }
    }
    fun merge(c: ProxyConfig, values: Map<String, String>): ProxyConfig {
        val settings = read(c).toMutableMap()
        supported(c.protocol).forEach { s -> values[s.key]?.let { if (it.isBlank()) settings.remove(s.key) else settings[s.key] = it.trim() } }
        validate(c, settings)
        if (settings.isEmpty()) { val extra = c.extraJson(); extra.remove("engineSettings"); return c.copy(extra = if (extra.length() == 0) "" else extra.toString()) }
        val o = JSONObject().put("version", 1)
        settings.forEach { (k, v) -> o.put(k, v) }
        return c.copy(extra = c.extraJson().put("engineSettings", o).toString())
    }
    fun validate(c: ProxyConfig, values: Map<String, String> = read(c)) {
        supported(c.protocol).forEach { s -> values[s.key]?.let { raw ->
            val valid = when(s.type) {
                Type.SECONDS -> raw.toIntOrNull() in 1..86400
                Type.COUNT -> raw.toIntOrNull() in 0..1024
                Type.BOOL -> raw in setOf("true", "false", "1", "0")
                Type.PORTS -> runCatching { ports(raw) }.isSuccess
                Type.PEM -> raw.length <= 65536 && raw.contains(if (s.key.endsWith("key")) "PRIVATE KEY" else "BEGIN CERTIFICATE")
                Type.ECH -> runCatching { validateEch(raw) }.isSuccess
                Type.PROXY -> runCatching { val u = java.net.URI(raw); u.scheme in setOf("http", "https") && !u.host.isNullOrBlank() && u.port in -1..65535 && u.port != 0 && (u.path.isNullOrBlank() || u.path == "/") && u.query == null && u.fragment == null }.getOrDefault(false)
                Type.DNS -> raw.split(',').all { ip -> val v = ip.trim(); (Regex("[0-9.]+").matches(v) || Regex("[0-9a-fA-F:]+").matches(v)) && runCatching { java.net.InetAddress.getByName(v) }.isSuccess }
                Type.TLS_MIN -> raw in setOf("1.2", "1.3")
                Type.TOKEN -> raw in setOf("totp", "stoken", "oidc") // HOTP requires durable engine counter callbacks.
                Type.MTU -> raw.toIntOrNull() in 576..9000
                Type.COMPRESSION -> raw in setOf("stateless", "all")
                Type.FORM_ENTRIES -> runCatching { formEntries(raw) }.isSuccess
                else -> raw.length <= 65536 && !raw.contains('\u0000')
            }
            require(valid) { "مقدار نامعتبر: ${s.label}" }
        } }
        if (c.protocol == "openconnect") {
            require(c.mode in setOf("", "anyconnect", "gp", "fortinet", "f5", "pulse", "nc")) { "نوع درگاه OpenConnect در موتور فعلی پشتیبانی نمی‌شود" }
            require(values.keys.none { it.startsWith("token_") && it != "token_mode" && it != "token_secret" } || values.containsKey("token_mode")) { "ابتدا روش توکن را انتخاب کنید" }
        }
        require(values["udp_over_stream"] !in setOf("true", "1") || c.mode.isBlank()) { "UDP over stream با udp_relay_mode قابل ترکیب نیست" }
        require(!values.containsKey("hop_interval") || values.containsKey("server_ports")) { "ابتدا پورت‌های چرخشی را مشخص کنید" }
        require(values.containsKey("token_mode") == values.containsKey("token_secret")) { "روش توکن و کلید آن باید با هم وارد شوند" }
        require(values.containsKey("mca_cert") == values.containsKey("mca_key")) { "گواهی و کلید MCA باید با هم وارد شوند" }
        require(!values.containsKey("ech_config") || (!c.allowInsecure && c.sni.isNotBlank() && (c.protocol != "anytls" || c.fingerprint.isBlank()))) { "ECH به نام سرور، بررسی گواهی فعال و TLS بدون uTLS نیاز دارد" }
    }
    private fun formEntries(raw: String): JSONArray {
        require(raw.length <= 65536)
        val entries = net.gozar.app.configtoolkit.BoundedJson.objectValue("{\"entries\":" + raw + "}").getJSONArray("entries")
        require(entries.length() <= 64)
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            require(entry.length() > 0)
            for (key in entry.keys()) {
                require(key in setOf("form_id", "submission_key", "name", "value", "promote"))
                val value = entry.get(key)
                require(if (key == "promote") value is Boolean else value is String && value.length <= 8192 && !value.contains('\u0000'))
            }
        }
        return entries
    }
    fun ports(raw: String): List<String> {
        val parts = raw.split(',')
        require(parts.size in 1..128)
        return parts.map { part ->
            val r = part.trim().split('-', ':')
            require(r.size in 1..2)
            val n = r.map { it.toIntOrNull() ?: error("Invalid port") }
            require(n.all { it in 1..65535 } && n.last() >= n.first())
            n.joinToString(":")
        }.distinct()
    }
    private fun validateEch(raw: String) {
        val b = Base64.getDecoder().decode(raw)
        require(b.size in 6..65537)
        fun u16(i: Int) = ((b[i].toInt() and 255) shl 8) or (b[i+1].toInt() and 255)
        require(u16(0) == b.size - 2)
        var p = 2
        while (p < b.size) { require(p + 4 <= b.size); val n = u16(p + 2); require(n > 0 && p + 4 + n <= b.size); p += 4 + n }
        require(p == b.size)
    }
    fun apply(c: ProxyConfig, target: JSONObject) {
        val values = read(c); validate(c, values)
        supported(c.protocol).forEach { s -> values[s.key]?.let { v ->
            var node = target
            val path = s.path.split('.')
            for (key in path.dropLast(1)) {
                val child = node.optJSONObject(key) ?: JSONObject().also { node.put(key, it) }
                node = child
            }
            val value: Any = when (s.type) {
                Type.SECONDS -> "${v}s"
                Type.COUNT, Type.MTU -> v.toInt()
                Type.FORM_ENTRIES -> formEntries(v)
                Type.BOOL -> v == "true" || v == "1"
                Type.PORTS -> JSONArray(ports(v))
                Type.PEM -> JSONArray().put(v)
                Type.ECH -> JSONArray().put("-----BEGIN ECH CONFIGS-----\n" + v.chunked(64).joinToString("\n") + "\n-----END ECH CONFIGS-----")
                else -> v
            }
            node.put(path.last(), value)
            if (s.type == Type.ECH) node.put("enabled", true)
        } }
    }
    /** Reverse the same schema for sing-box JSON node import; never silently discard these options. */
    fun fromUpstream(c: ProxyConfig, source: JSONObject): ProxyConfig {
        val values = mutableMapOf<String, String>()
        val echOptions = source.optJSONObject("tls")?.optJSONObject("ech")
        if (supported(c.protocol).any { it.type == Type.ECH } && echOptions?.optBoolean("enabled") == true)
            require(echOptions.has("config") && !echOptions.isNull("config")) { "Inline ECH config required for this importer" }
        supported(c.protocol).forEach { setting ->
            var node: Any? = source
            for (part in setting.path.split('.')) node = (node as? JSONObject)?.opt(part)
            val value = node
            if (value != null && value != JSONObject.NULL) {
                if (setting.type == Type.SECONDS && value.toString() in setOf("0", "0s")) return@forEach
                if (setting.type == Type.ECH && echOptions?.optBoolean("enabled") != true) return@forEach
                fun strings() = if (value is JSONArray) (0 until value.length()).map { value.getString(it) } else listOf(value.toString())
                values[setting.key] = when (setting.type) {
                    Type.SECONDS -> {
                        val match = Regex("([0-9]+)(s|m|h)").matchEntire(value.toString()) ?: error("Unsupported duration precision")
                        val factor = when (match.groupValues[2]) { "m" -> 60; "h" -> 3600; else -> 1 }
                        Math.multiplyExact(match.groupValues[1].toInt(), factor).toString()
                    }
                    Type.PORTS -> strings().joinToString(",")
                    Type.PEM -> strings().joinToString("\n")
                    Type.ECH -> strings().joinToString("").removePrefix("-----BEGIN ECH CONFIGS-----")
                        .removeSuffix("-----END ECH CONFIGS-----").filterNot { it.isWhitespace() }
                    else -> value.toString()
                }
            }
        }
        return merge(c, values)
    }
    fun share(c: ProxyConfig): List<Pair<String, String>> = read(c).filterKeys { key -> supported(c.protocol).any { it.key == key && it.share } }.toList()
    fun usesSingBox(c: ProxyConfig) = c.protocol == "hysteria2" && read(c).isNotEmpty()
}

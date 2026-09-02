package net.gozar.app.configtoolkit

import net.gozar.app.ConfigParser
import net.gozar.app.GhajarCompatibilityImport
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.Locale

class DecoderRegistry(
    private val decoders: List<ConfigDecoder> = listOf(
        NpvtDecoder(), NpvsDecoder(), HappDecoder(), NetModDecoder(),
        SlipNetDecoder(), EhiDecoder(), HatDecoder(), DarkDecoder(),
        GenericJsonDecoder(), TextLinkDecoder()
    )
) {
    fun decoderFor(detection: FormatDetection): ConfigDecoder? = decoders.firstOrNull { it.supports(detection) }

    fun decode(input: ConfigInput): ParsedConfig {
        require(input.bytes.size <= MAX_FILE_BYTES) { throw ConfigToolkitException.TooLarge(MAX_FILE_BYTES) }
        val detection = FormatDetector.detect(input)
        val decoder = decoderFor(detection) ?: throw ConfigToolkitException.UnsupportedFormat(detection.format)
        val parsed = decoder.decode(input)
        val validation = decoder.validate(parsed)
        if (!validation.valid) throw ConfigToolkitException.InvalidConfig(
            validation.issues.take(4).joinToString("\n") { it.message }
        )
        return parsed
    }

    companion object { const val MAX_FILE_BYTES = 16L * 1024L * 1024L }
}

internal object ReadablePayload {
    fun extract(input: ConfigInput, signature: String? = null): String? {
        val raw = input.bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val body = if (signature != null && raw.startsWith(signature, true)) {
            raw.substring(signature.length).trimStart(':', '\r', '\n', ' ', '\t')
        } else raw
        if (looksReadable(body)) return body
        val compact = body.filterNot(Char::isWhitespace)
        if (compact.length < 8) return null
        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
        return sequenceOf(Base64.getUrlDecoder(), Base64.getDecoder()).mapNotNull { decoder ->
            runCatching { decoder.decode(padded).toString(Charsets.UTF_8) }.getOrNull()
        }.firstOrNull(::looksReadable)
    }

    private fun looksReadable(value: String): Boolean {
        val t = value.trim()
        return t.startsWith('{') || t.startsWith('[') ||
            Regex("(?i)^(vless|vmess|trojan|ss|socks|socks5|happ|happ-proxy)://").containsMatchIn(t)
    }
}

open class GenericJsonDecoder(override val format: ConfigFormat = ConfigFormat.JSON) : ConfigDecoder {
    override fun decode(input: ConfigInput): ParsedConfig {
        val text = ReadablePayload.extract(input) ?: throw ConfigToolkitException.UnsupportedFormat(format)
        return parseJson(text, format)
    }

    internal fun parseJson(text: String, source: ConfigFormat): ParsedConfig {
        val clean = text.trim()
        val root = runCatching { JSONObject(clean) }.getOrNull()
        val array = if (root == null) runCatching { JSONArray(clean) }.getOrNull() else null
        if (root == null && array == null) throw ConfigToolkitException.InvalidConfig("ساختار JSON معتبر نیست.")

        val rawCandidates = linkedSetOf<String>()
        val directProfiles = mutableListOf<NormalizedProfile>()

        fun walk(value: Any?, inheritedName: String = "", depth: Int = 0) {
            if (depth > 12) return
            when (value) {
                is JSONObject -> {
                    val name = first(value, "remarks", "remark", "name", "ps").ifBlank { inheritedName }
                    value.optJSONObject("v2rayJson")?.let { rawCandidates += it.toString() }
                    value.optJSONObject("v2rayProfile")?.let { profile ->
                        profile.optJSONObject("v2rayJson")?.let { rawCandidates += it.toString() }
                    }
                    if (value.has("outbounds") || value.has("protocol") && value.has("settings")) {
                        rawCandidates += value.toString()
                    }
                    direct(value, name, source)?.let(directProfiles::add)
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key !in setOf("rawJson", "v2rayJson")) walk(value.opt(key), name, depth + 1)
                    }
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i), inheritedName, depth + 1)
            }
        }
        walk(root ?: array)

        val parsed = rawCandidates.flatMap { candidate ->
            ConfigParser.parseJsonOutbounds(candidate).map { NormalizedProfile.from(it, source, candidate) }
        } + directProfiles
        val profiles = parsed.distinctBy(::criticalSignature)
        val lock = root?.let(::readLock)
        return ParsedConfig(source, profiles, root?.toString(2) ?: array?.toString(2), lock)
    }

    private fun direct(o: JSONObject, inheritedName: String, source: ConfigFormat): NormalizedProfile? {
        val protocol = first(o, "protocol", "type").lowercase(Locale.ROOT).let {
            when (it) { "shadowsocks", "ss" -> "shadowsocks"; "socks5" -> "socks"; else -> it }
        }
        if (protocol !in setOf("vless", "vmess", "trojan", "shadowsocks", "socks")) return null
        val server = first(o, "server", "address", "add", "host")
        val port = sequenceOf("serverPort", "port").map { o.opt(it) }.mapNotNull {
            when (it) { is Number -> it.toInt(); is String -> it.toIntOrNull(); else -> null }
        }.firstOrNull() ?: 0
        if (server.isBlank() || port <= 0) return null
        val stream = o.optJSONObject("streamSettings") ?: JSONObject()
        val network = first(o, "network", "net").ifBlank { stream.optString("network", "tcp") }
        val tls = stream.optJSONObject("tlsSettings")
        val reality = stream.optJSONObject("realitySettings")
        val sec = reality ?: tls ?: JSONObject()
        val ws = stream.optJSONObject("wsSettings") ?: JSONObject()
        val grpc = stream.optJSONObject("grpcSettings") ?: JSONObject()
        return NormalizedProfile(
            name = inheritedName.ifBlank { "$server:$port" }, protocol = protocol,
            server = server, port = port,
            uuid = first(o, "uuid", "id"), password = first(o, "password", "pass"),
            method = first(o, "method", "cipher"), network = network.ifBlank { "tcp" },
            security = first(o, "security", "tls").ifBlank {
                when { reality.length() > 0 -> "reality"; tls != null -> "tls"; else -> "none" }
            },
            sni = first(o, "sni", "serverName").ifBlank { sec.optString("serverName") },
            host = first(o, "host").ifBlank { ws.optJSONObject("headers")?.optString("Host").orEmpty() },
            path = first(o, "path").ifBlank { ws.optString("path") },
            alpn = first(o, "alpn").ifBlank { jsonStrings(sec.optJSONArray("alpn")).joinToString(",") },
            fingerprint = first(o, "fingerprint", "fp").ifBlank { sec.optString("fingerprint", "chrome") },
            flow = first(o, "flow"), publicKey = first(o, "publicKey", "pbk").ifBlank { reality.optString("publicKey") },
            shortId = first(o, "shortId", "sid").ifBlank { reality.optString("shortId") },
            spiderX = first(o, "spiderX", "spx").ifBlank { reality.optString("spiderX") },
            grpcServiceName = first(o, "serviceName").ifBlank { grpc.optString("serviceName") },
            authority = first(o, "authority").ifBlank { grpc.optString("authority") },
            allowInsecure = o.optBoolean("allowInsecure", sec.optBoolean("allowInsecure", false)),
            encryption = first(o, "encryption", "scy").ifBlank { if (protocol == "vmess") "auto" else "none" },
            alterId = o.optInt("alterId", o.optInt("aid", 0)), rawJson = o.toString(), sourceFormat = source
        )
    }

    private fun readLock(root: JSONObject): LockConfigInfo? {
        val lock = root.optJSONObject("lockConfig") ?: root.optJSONObject("lock") ?: return null
        val deviceValue = lock.opt("deviceIds")
        val devices = when (deviceValue) {
            is JSONArray -> jsonStrings(deviceValue)
            is String -> deviceValue.split(',', '\n').map(String::trim).filter(String::isNotEmpty)
            else -> emptyList()
        }
        return LockConfigInfo(
            isLocked = lock.optBoolean("isLocked", lock.optBoolean("locked", false)),
            password = lock.optString("password").takeIf(String::isNotEmpty),
            onlyMobileNetwork = lock.optBoolean("onlyMobileNetwork"),
            blockRootedAndJailbroken = lock.optBoolean("blockRootedAndJailbroken"),
            onlyOfficialStores = lock.optBoolean("onlyOfficialStores"),
            expiryDate = lock.optString("expiryDate").takeIf(String::isNotEmpty),
            deviceIds = devices,
            message = lock.optString("message").takeIf(String::isNotEmpty),
            customServerMessage = lock.optString("customServerMessage").takeIf(String::isNotEmpty)
        )
    }

    private fun first(o: JSONObject, vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        o.optString(key).takeIf { it.isNotBlank() && it != "null" }
    }.orEmpty()

    private fun jsonStrings(array: JSONArray?): List<String> = if (array == null) emptyList()
    else (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }

    private fun criticalSignature(p: NormalizedProfile) = listOf(
        p.protocol, p.server.lowercase(), p.port, p.uuid, p.password, p.method,
        p.network, p.security, p.sni, p.host, p.path, p.publicKey, p.shortId
    ).joinToString("\u0000")
}

class TextLinkDecoder : ConfigDecoder {
    override val format = ConfigFormat.TEXT
    override fun decode(input: ConfigInput): ParsedConfig {
        val text = ReadablePayload.extract(input) ?: input.bytes.toString(Charsets.UTF_8)
        val profiles = ConfigParser.parseBundle(text).map { NormalizedProfile.from(it, format) }
        if (profiles.isEmpty()) throw ConfigToolkitException.InvalidConfig("لینک استاندارد قابل استفاده‌ای پیدا نشد.")
        return ParsedConfig(format, profiles)
    }
}

class NpvtDecoder : GenericJsonDecoder(ConfigFormat.NPVT) {
    override fun decode(input: ConfigInput): ParsedConfig {
        val text = ReadablePayload.extract(input, "NPVT1")
            ?: throw protectedOrUnsupported(input)
        return parseJson(text, format)
    }
}

class NpvsDecoder : GenericJsonDecoder(ConfigFormat.NPVS) {
    override fun decode(input: ConfigInput): ParsedConfig {
        val text = ReadablePayload.extract(input, "NPVS") ?: throw protectedOrUnsupported(input)
        return parseJson(text, format)
    }
}

class HappDecoder : ConfigDecoder {
    override val format = ConfigFormat.HAPP
    override fun decode(input: ConfigInput): ParsedConfig {
        val text = input.bytes.toString(Charsets.UTF_8).trim()
        if (text.startsWith("happ://crypt", true)) throw ConfigToolkitException.PasskeyRequired()
        val configs = if (text.startsWith("happ://", true) || text.startsWith("happ-proxy://", true)) {
            GhajarCompatibilityImport.parseDeepLink(text)
        } else {
            val readable = ReadablePayload.extract(input)
                ?: throw ConfigToolkitException.UnsupportedFormat(format)
            ConfigParser.parseBundle(readable)
        }
        if (configs.isEmpty()) throw ConfigToolkitException.UnsupportedFormat(format)
        return ParsedConfig(format, configs.map { NormalizedProfile.from(it, format) })
    }
}

abstract class ReadableLegacyDecoder(final override val format: ConfigFormat) : ConfigDecoder {
    override fun decode(input: ConfigInput): ParsedConfig {
        val readable = ReadablePayload.extract(input) ?: throw protectedOrUnsupported(input)
        return if (readable.trimStart().startsWith('{') || readable.trimStart().startsWith('[')) {
            GenericJsonDecoder(format).parseJson(readable, format)
        } else {
            val profiles = ConfigParser.parseBundle(readable).map { NormalizedProfile.from(it, format) }
            if (profiles.isEmpty()) throw ConfigToolkitException.UnsupportedFormat(format)
            ParsedConfig(format, profiles)
        }
    }
}

class NetModDecoder : ReadableLegacyDecoder(ConfigFormat.NETMOD)
class SlipNetDecoder : ReadableLegacyDecoder(ConfigFormat.SLIPNET)
class EhiDecoder : ReadableLegacyDecoder(ConfigFormat.EHI)
class HatDecoder : ReadableLegacyDecoder(ConfigFormat.HAT)
class DarkDecoder : ReadableLegacyDecoder(ConfigFormat.DARK)

private fun protectedOrUnsupported(input: ConfigInput): ConfigToolkitException {
    // A passkey is never guessed or persisted. Formats without a documented,
    // credential-based envelope stay unsupported instead of using extracted app keys.
    return if (input.passkey == null || input.passkey.isEmpty()) ConfigToolkitException.PasskeyRequired()
    else ConfigToolkitException.UnsupportedFormat(FormatDetector.detect(input).format)
}

package net.gozar.app.configtoolkit

import net.gozar.app.ConfigSource
import net.gozar.app.ProxyConfig
import org.json.JSONObject
import java.util.UUID

enum class ConfigFormat(val extensions: Set<String>) {
    NPVT(setOf("npvt")),
    NPVS(setOf("npvs")),
    HAPP(setOf("happ")),
    NETMOD(setOf("nm")),
    EHI(setOf("ehi")),
    SLIPNET(setOf("slip")),
    HAT(setOf("hat")),
    DARK(setOf("dark")),
    JSON(setOf("json")),
    TEXT(setOf("txt", "conf", "yaml", "yml")),
    UNKNOWN(emptySet())
}

data class ConfigInput(
    val bytes: ByteArray,
    val displayName: String = "config",
    val mimeType: String? = null,
    val passkey: CharArray? = null
)

data class LockConfigInfo(
    val isLocked: Boolean = false,
    val password: String? = null,
    val onlyMobileNetwork: Boolean = false,
    val blockRootedAndJailbroken: Boolean = false,
    val onlyOfficialStores: Boolean = false,
    val expiryDate: String? = null,
    val deviceIds: List<String> = emptyList(),
    val message: String? = null,
    val customServerMessage: String? = null
) {
    val hasEmbeddedPassword: Boolean get() = !password.isNullOrEmpty()
}

data class NormalizedProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: String,
    val server: String,
    val port: Int,
    val uuid: String = "",
    val password: String = "",
    val method: String = "",
    val network: String = "tcp",
    val security: String = "none",
    val sni: String = "",
    val host: String = "",
    val path: String = "",
    val alpn: String = "",
    val fingerprint: String = "chrome",
    val flow: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "/",
    val grpcServiceName: String = "",
    val authority: String = "",
    val allowInsecure: Boolean = false,
    val encryption: String = "none",
    val alterId: Int = 0,
    val rawJson: String = "",
    val sourceFormat: ConfigFormat,
    /**
     * The parsed profile this came from. Fields NormalizedProfile does not
     * model (extra, MTU, keys, pins, DNS-tunnel mode, …) are kept from it, so
     * importing a file never loses what the link or .conf carried.
     */
    val original: ProxyConfig? = null
) {
    fun toProxyConfig(): ProxyConfig = (original ?: ProxyConfig(name = name, protocol = protocol, address = server, port = port)).copy(
        id = id,
        name = name,
        protocol = protocol.lowercase(),
        address = server,
        port = port,
        uuid = uuid,
        password = password,
        method = method,
        network = network,
        security = security,
        sni = sni,
        host = authority.ifBlank { host },
        path = path,
        spiderX = spiderX,
        alpn = alpn,
        fingerprint = fingerprint,
        flow = flow,
        publicKey = publicKey,
        shortId = shortId,
        serviceName = grpcServiceName,
        allowInsecure = allowInsecure,
        encryption = encryption,
        alterId = alterId,
        source = ConfigSource.PERSONAL
    )

    companion object {
        fun from(config: ProxyConfig, format: ConfigFormat, rawJson: String = "") = NormalizedProfile(
            id = config.id,
            name = config.name,
            protocol = config.protocol,
            server = config.address,
            port = config.port,
            uuid = config.uuid,
            password = config.password,
            method = config.method,
            network = config.network,
            security = config.security,
            sni = config.sni,
            host = config.host,
            path = config.path,
            alpn = config.alpn,
            fingerprint = config.fingerprint,
            flow = config.flow,
            publicKey = config.publicKey,
            shortId = config.shortId,
            spiderX = config.spiderX,
            grpcServiceName = config.serviceName,
            authority = if (config.network.equals("grpc", true)) config.host else "",
            allowInsecure = config.allowInsecure,
            encryption = config.encryption,
            alterId = config.alterId,
            rawJson = rawJson,
            sourceFormat = format,
            original = config
        )
    }
}

data class ParsedConfig(
    val sourceFormat: ConfigFormat,
    val profiles: List<NormalizedProfile>,
    val rawJson: String? = null,
    val lockConfig: LockConfigInfo? = null,
    val warnings: List<String> = emptyList()
)

sealed class ConfigToolkitException(message: String) : Exception(message) {
    class UnsupportedFormat(format: ConfigFormat) : ConfigToolkitException(
        if (format == ConfigFormat.UNKNOWN) "این نوع کانفیگ شناسایی نشد."
        else "این نوع کانفیگ در حال حاضر کامل پشتیبانی نمی‌شود."
    )

    class PasskeyRequired : ConfigToolkitException("این فایل با passkey محافظت شده است.")
    /**
     * Sealed with the issuing app's own key, not a password the user holds.
     * Never shown as a password prompt: there is no password to type.
     */
    class VendorLocked(format: ConfigFormat) : ConfigToolkitException(
        "این فایل ${format.name} با قفل اختصاصی برنامهٔ سازنده رمز شده و رمزی برای وارد کردن ندارد. " +
            "باز کردنش یعنی دور زدن قفل سازنده و پشتیبانی نمی‌شود؛ از سازنده لینک یا خروجی باز بخواهید."
    )
    class WrongPasskey : ConfigToolkitException("passkey واردشده صحیح نیست.")
    /** Sealed to a key the user does not hold (app key or recipient key); [message] says which. */
    class Locked(message: String) : ConfigToolkitException(message)
    class InvalidConfig(message: String) : ConfigToolkitException(message)
    class TooLarge(limit: Long) : ConfigToolkitException("حجم فایل از سقف امن ${limit / 1024 / 1024} مگابایت بیشتر است.")
}

data class ValidationIssue(val field: String, val message: String)

data class ValidationResult(val issues: List<ValidationIssue>) {
    val valid: Boolean get() = issues.isEmpty()
}

interface ConfigDecoder {
    val format: ConfigFormat
    fun supports(detection: FormatDetection): Boolean = detection.format == format
    fun decode(input: ConfigInput): ParsedConfig
    fun validate(parsed: ParsedConfig): ValidationResult = ProfileValidator.validateAll(parsed.profiles)
    fun export(parsed: ParsedConfig): ByteArray = (parsed.rawJson ?: JSONObject().apply {
        put("format", parsed.sourceFormat.name)
        put("profiles", org.json.JSONArray(parsed.profiles.map { it.toProxyConfig().toJson() }))
    }.toString(2)).toByteArray(Charsets.UTF_8)
}

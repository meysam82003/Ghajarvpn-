package net.gozar.app.security.vault

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class VaultException(val kind: Kind) : Exception(kind.name) {
    enum class Kind { WRONG_PASSWORD_OR_DAMAGED_KEY, TAMPERED, CORRUPT, UNSUPPORTED_VERSION, INVALID_CONFIG, EXPIRED, DUPLICATE, IO, LOCKED, POLICY, ENGINE_UNAVAILABLE }
}

enum class MetadataPrivacy { STANDARD, PRIVATE }
enum class QuotaMode { NONE, LOCAL, SERVER }
enum class ActivationMode { CREATED, IMPORTED, FIRST_CONNECT }
enum class Accounting { TOTAL, DOWNLOAD, UPLOAD }
enum class EntitlementStatus { ACTIVE, EXPIRED, QUOTA_EXHAUSTED, DEVICE_LIMIT, REVOKED, SUSPENDED, UNKNOWN }

data class VaultPolicy(val readOnly: Boolean = false, val allowTest: Boolean = true, val allowConnect: Boolean = true,
    val allowReExport: Boolean = true, val allowReconnect: Boolean = true, val requireLocalAuthentication: Boolean = false) {
    fun toJson() = JSONObject().put("readOnly",readOnly).put("test",allowTest).put("connect",allowConnect)
        .put("export",allowReExport).put("reconnect",allowReconnect).put("localAuth",requireLocalAuthentication)
    companion object { fun fromJson(o: JSONObject) = VaultPolicy(o.getBoolean("readOnly"),o.getBoolean("test"),o.getBoolean("connect"),o.getBoolean("export"),o.getBoolean("reconnect"),o.getBoolean("localAuth")) }
}

data class VaultQuota(val mode: QuotaMode = QuotaMode.NONE, val quotaBytes: Long? = null,
    val validityMillis: Long? = null, val activationMode: ActivationMode = ActivationMode.CREATED,
    val activatedAt: Long? = null, val deviceLimit: Int? = null, val entitlementId: String? = null,
    val accounting: Accounting = Accounting.TOTAL, val graceMillis: Long = 0) {
    init {
        require(quotaBytes == null || quotaBytes > 0); require(validityMillis == null || validityMillis > 0)
        require(mode != QuotaMode.NONE || quotaBytes == null)
        require(activatedAt == null || activatedAt >= 0); require(deviceLimit == null || deviceLimit in 1..10000)
        require(graceMillis in setOf(0L,300000L,1800000L,3600000L))
        require(mode == QuotaMode.SERVER || (deviceLimit == null && entitlementId == null && graceMillis == 0L))
        require(mode != QuotaMode.SERVER || !entitlementId.isNullOrBlank())
    }
    fun toJson() = JSONObject().put("mode",mode.name).put("bytes",quotaBytes).put("validityMs",validityMillis)
        .put("activation",activationMode.name).put("activatedAt",activatedAt).put("deviceLimit",deviceLimit)
        .put("entitlementId",entitlementId).put("accounting",accounting.name).put("graceMs",graceMillis)
    companion object { fun fromJson(o: JSONObject) = VaultQuota(QuotaMode.valueOf(o.getString("mode")),o.longOrNull("bytes"),o.longOrNull("validityMs"),ActivationMode.valueOf(o.getString("activation")),o.longOrNull("activatedAt"),o.longOrNull("deviceLimit")?.let { Math.toIntExact(it) },o.optString("entitlementId").takeIf { it.isNotBlank() },Accounting.valueOf(o.getString("accounting")),o.exactLong("graceMs")) }
}

/** Payload is plaintext only in an unlocked, short-lived object; never include it in toString/logs. */
class VaultEntry(val id: String = UUID.randomUUID().toString(), val displayName: String, val protocol: String,
    val payload: String, val createdAt: Long, val updatedAt: Long = createdAt, val expiresAt: Long? = null,
    val note: String = "", val tags: List<String> = emptyList(), val favorite: Boolean = false,
    val sourceType: String = "personal", val privacy: MetadataPrivacy = MetadataPrivacy.PRIVATE,
    val policy: VaultPolicy = VaultPolicy(), val quota: VaultQuota = VaultQuota(), val usageId: String = id) {
    init {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        require(runCatching { UUID.fromString(usageId).toString() == usageId }.getOrDefault(false))
        require(displayName.length <= 512 && protocol.length <= 64 && note.length <= 16384 && tags.size <= 64 && tags.all { it.length <= 128 })
        require(createdAt >= 0 && updatedAt >= createdAt && (expiresAt == null || expiresAt >= 0))
        require(payload.toByteArray(Charsets.UTF_8).size <= 1024 * 1024)
    }
    override fun toString() = "VaultEntry(redacted)"
    fun metadata() = JSONObject().put("name",displayName).put("protocol",protocol).put("createdAt",createdAt)
        .put("updatedAt",updatedAt).put("expiresAt",expiresAt).put("note",note).put("tags",JSONArray(tags))
        .put("favorite",favorite).put("source",sourceType).put("policy",policy.toJson()).put("quota",quota.toJson()).put("usageId",usageId)
    internal fun plain() = JSONObject().put("metadata",metadata()).put("payload",payload)
    companion object {
        fun read(id: String, privacy: MetadataPrivacy, o: JSONObject): VaultEntry {
            val m=o.getJSONObject("metadata"); val tags=m.getJSONArray("tags")
            return VaultEntry(id,m.getString("name"),m.getString("protocol"),o.getString("payload"),m.exactLong("createdAt"),m.exactLong("updatedAt"),m.longOrNull("expiresAt"),m.getString("note"),(0 until tags.length()).map { tags.getString(it) },m.getBoolean("favorite"),m.getString("source"),privacy,VaultPolicy.fromJson(m.getJSONObject("policy")),VaultQuota.fromJson(m.getJSONObject("quota")),m.optString("usageId",id))
        }
    }
}
/** JSONObject.getLong/getInt coerce strings/floats and can truncate security parameters. */
internal fun JSONObject.exactLong(key: String): Long {
    val value=get(key)
    require(value is Long || value is Int) { "Integer required" }
    return (value as Number).toLong()
}
internal fun JSONObject.exactInt(key: String): Int = Math.toIntExact(exactLong(key))
internal fun JSONObject.longOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else exactLong(key)

package net.gozar.app.plugins

import org.json.JSONArray
import org.json.JSONObject

/** Packaging and runtime layer are deliberately separate. */
enum class ComponentKind { CORE, PROTOCOL, TRANSPORT, METHOD, PLUGIN, TUN_ENGINE }
enum class PluginState { NOT_INSTALLED, AVAILABLE, DOWNLOADING, VERIFYING, INSTALLING, INSTALLED, UPDATE_AVAILABLE, INCOMPATIBLE, BROKEN, FAILED }
enum class PluginOperation { INSTALL, UPDATE, REMOVE, REPAIR, RETRY, ROLLBACK }

data class CapabilityContract(
    val supportsTcp: Boolean = false, val supportsUdp: Boolean = false,
    val supportsIPv6: Boolean = false, val supportsTun: Boolean = false,
    val supportsSocks: Boolean = false, val supportsDns: Boolean = false,
    val supportsPortHopping: Boolean = false, val supportsEch: Boolean = false,
    val supportsMasque: Boolean = false, val supportsReality: Boolean = false,
    val supportsXhttp: Boolean = false, val supportsFullConfig: Boolean = false,
    val supportsSubscription: Boolean = false, val supportsAdvancedAuth: Boolean = false
) {
    fun names(): Set<String> = values().filterValues { it }.keys
    private fun values() = linkedMapOf("supportsTcp" to supportsTcp, "supportsUdp" to supportsUdp,
        "supportsIPv6" to supportsIPv6, "supportsTun" to supportsTun, "supportsSocks" to supportsSocks,
        "supportsDns" to supportsDns, "supportsPortHopping" to supportsPortHopping, "supportsEch" to supportsEch,
        "supportsMasque" to supportsMasque, "supportsReality" to supportsReality, "supportsXhttp" to supportsXhttp,
        "supportsFullConfig" to supportsFullConfig, "supportsSubscription" to supportsSubscription,
        "supportsAdvancedAuth" to supportsAdvancedAuth)
    fun json() = JSONObject(values() as Map<*, *>)
    companion object {
        fun parse(o: JSONObject): CapabilityContract {
            val known = CapabilityContract().values().keys
            require(o.keys().asSequence().all { it in known && o.get(it) is Boolean }) { "Unknown or malformed capability" }
            return CapabilityContract(o.optBoolean("supportsTcp"), o.optBoolean("supportsUdp"),
            o.optBoolean("supportsIPv6"), o.optBoolean("supportsTun"), o.optBoolean("supportsSocks"), o.optBoolean("supportsDns"),
            o.optBoolean("supportsPortHopping"), o.optBoolean("supportsEch"), o.optBoolean("supportsMasque"),
            o.optBoolean("supportsReality"), o.optBoolean("supportsXhttp"), o.optBoolean("supportsFullConfig"),
            o.optBoolean("supportsSubscription"), o.optBoolean("supportsAdvancedAuth"))
        }
    }
}

data class PluginDependency(val id: String, val minVersionCode: Long)
data class PluginRelease(
    val id: String, val name: String, val version: String, val versionCode: Long,
    val apiVersion: Int, val minGhajarVersion: String, val minGhajarVersionCode: Long,
    val abis: Set<String>, val capabilities: CapabilityContract,
    val sourceRepository: String, val sourceCommit: String, val sourceTag: String,
    val downloadUrl: String, val size: Long, val sha256: String,
    val publisher: String, val certificateSha256: String, val license: String,
    val packageName: String, val serviceClass: String, val dependencies: List<PluginDependency>,
    /** Original signed bytes, not re-serialized JSON. Persist for revalidation after restart. */
    val signedEnvelope: String
) {
    companion object {
        const val API = 1
        fun parse(o: JSONObject, envelope: String) = PluginRelease(
            o.getString("id"), o.getString("name"), o.getString("version"), exactLong(o, "versionCode"),
            Math.toIntExact(exactLong(o, "apiVersion")), o.getString("minGhajarVersion"), exactLong(o, "minGhajarVersionCode"),
            strings(o.getJSONArray("abis")), CapabilityContract.parse(o.getJSONObject("capabilities")),
            o.getString("sourceRepository"), o.getString("sourceCommit"), o.optString("sourceTag"),
            o.getString("downloadUrl"), exactLong(o, "size"), o.getString("sha256"), o.getString("publisher"),
            o.getString("certificateSha256"), o.getString("license"), o.getString("packageName"),
            o.getString("serviceClass"), o.getJSONArray("dependencies").let { a -> (0 until a.length()).map {
                a.getJSONObject(it).let { d -> PluginDependency(d.getString("id"), exactLong(d, "minVersionCode")) }
            } }, envelope)
        private fun exactLong(o: JSONObject, key: String) = o.get(key).toString().toLongOrNull() ?: error("Non-integer $key")
        fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }.toSet()
    }
}

data class PluginCandidate(val id: String, val name: String, val kind: ComponentKind,
    val formats: Set<String>, val repository: String, val commit: String, val sourceVersion: String,
    val evidence: String, val supported: CapabilityContract)

/** Audited source capabilities; these are NOT installed/usable releases. */
object PluginCatalog {
    val candidates = listOf(
        PluginCandidate("shadowquic", "ShadowQUIC", ComponentKind.PROTOCOL, setOf("shadowquic-uri", "shadowquic-json"),
            "https://github.com/spongebob888/shadowquic", "5540e3a32ca73c85af125723e4262e02cf28ebcd", "0.4.0",
            "audit-1.1.1 F006; Husi plugin gitlink; TLS/QUIC dependencies and Ghajar adapter remain release gates",
            CapabilityContract(supportsTcp = true, supportsUdp = true, supportsSocks = true)),
        PluginCandidate("mihomo", "Mihomo full-config", ComponentKind.CORE, setOf("mihomo-yaml", "mihomo-json"),
            "https://github.com/appshubcc/Bettbox", "3189346611caeba73aa87feaf708e4fd65115d16", "vendored snapshot",
            "audit-1.1.1 F015; core/Clash.Meta; full config must remain opaque to host",
            CapabilityContract(supportsTcp = true, supportsUdp = true, supportsIPv6 = true, supportsTun = true,
                supportsSocks = true, supportsDns = true, supportsFullConfig = true, supportsSubscription = true)),
        PluginCandidate("trusttunnel", "TrustTunnel", ComponentKind.PROTOCOL, setOf("trusttunnel-json"),
            "https://github.com/xchacha20-poly1305/sing-trusttunnel", "", "candidate",
            "audit-1.1.1 registry adapter; concrete need and release pin required",
            CapabilityContract(supportsTcp = true, supportsUdp = true)),
        PluginCandidate("easyconnect", "EasyConnect", ComponentKind.PROTOCOL, setOf("easyconnect-json"),
            "https://github.com/xchacha20-poly1305/sing-easyconnect", "", "candidate",
            "audit-1.1.1; enterprise authentication and Android adapter require validation",
            CapabilityContract(supportsTcp = true, supportsAdvancedAuth = true))
    )
    fun candidate(id: String) = candidates.firstOrNull { it.id == id }
}

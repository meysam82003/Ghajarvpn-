package net.gozar.app.plugins

import java.net.URI
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

/** Publisher identity is compiled into the host; never imported from backups/configs/catalogs. */
data class TrustedPublisher(val id: String, val manifestPublicKey: String, val apkCertificates: Set<String>,
    val pluginIds: Set<String>, val downloadHosts: Set<String>)

object ProductionPublishers {
    // Intentionally empty until an actual Ghajar API-compatible APK is signed and reviewed.
    // No fake fingerprints, trust-on-first-use, custom authorities, or arbitrary key enrollment.
    val all: List<TrustedPublisher> = emptyList()
}

class PluginIncompatible(message: String) : IllegalArgumentException(message)

class PluginTrust(private val publishers: List<TrustedPublisher>, private val hostVersionCode: Long,
    private val deviceAbis: Set<String>) {
    fun verify(envelope: String): PluginRelease {
        require(envelope.toByteArray().size <= 128 * 1024) { "Manifest too large" }
        val wrapper = JSONObject(envelope)
        val publisher = publishers.singleOrNull { it.id == wrapper.getString("publisher") }
            ?: error("Publisher is not explicitly trusted")
        val bytes = Base64.getDecoder().decode(wrapper.getString("payload"))
        require(bytes.size <= 64 * 1024)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publisher.manifestPublicKey)))
        require((key as java.security.interfaces.RSAPublicKey).modulus.bitLength() >= 3072) { "Weak publisher key" }
        require(Signature.getInstance("SHA256withRSA").run {
            initVerify(key); update(bytes); verify(Base64.getDecoder().decode(wrapper.getString("signature")))
        }) { "Manifest digital signature is invalid" }
        val release = PluginRelease.parse(JSONObject(String(bytes, Charsets.UTF_8)), envelope)
        require(release.publisher == publisher.id && release.id in publisher.pluginIds) { "Publisher scope mismatch" }
        val candidate = PluginCatalog.candidate(release.id) ?: error("Unknown plugin ID")
        require(release.certificateSha256 in publisher.apkCertificates) { "Untrusted APK signing certificate" }
        require(release.sha256.matches(Regex("[0-9a-f]{64}")) && release.certificateSha256.matches(Regex("[0-9a-f]{64}")))
        require(release.versionCode > 0 && release.minGhajarVersionCode > 0)
        if (release.apiVersion != PluginRelease.API) throw PluginIncompatible("Incompatible plugin API")
        if (release.minGhajarVersionCode > hostVersionCode) throw PluginIncompatible("Requires a newer Ghajar version")
        if (release.abis.isEmpty() || release.abis.none { it in deviceAbis }) throw PluginIncompatible("Incompatible ABI")
        require(release.size in 1..(512L * 1024 * 1024)) { "Invalid APK size" }
        require(release.name.isNotBlank() && release.version.isNotBlank() && release.license.isNotBlank())
        require(release.sourceRepository == candidate.repository && release.sourceCommit.matches(Regex("[0-9a-f]{40}"))) { "Unreviewed source provenance" }
        // New source pins/capabilities require a host catalog review, not publisher claims alone.
        require(candidate.commit.isNotEmpty() && release.sourceCommit == candidate.commit) { "Source pin is not approved" }
        require(candidate.supported.names().containsAll(release.capabilities.names())) { "Unreviewed capabilities" }
        require(release.packageName == "net.ghajar.plugin.${release.id}.v${release.versionCode}") { "A version must have an independent APK package" }
        require(release.serviceClass.startsWith(release.packageName + ".") && release.serviceClass.matches(Regex("[A-Za-z0-9_.$]+")))
        val url = URI(release.downloadUrl)
        require(url.scheme == "https" && url.host in publisher.downloadHosts && url.userInfo == null && url.fragment == null && url.port in setOf(-1, 443)) { "Download origin is not trusted" }
        require(release.dependencies.map { it.id }.distinct().size == release.dependencies.size)
        require(release.dependencies.all { it.id != release.id && PluginCatalog.candidate(it.id) != null && it.minVersionCode > 0 })
        return release
    }
    companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** Pure activation policy: an installed APK is not active until the probe succeeds. */
data class PluginSlots(val active: String? = null, val previous: String? = null) {
    fun activate(candidate: String, healthy: Boolean): PluginSlots {
        require(healthy) { "Unhealthy version must not replace the active slot" }
        return if (candidate == active) this else PluginSlots(candidate, active)
    }
    fun rollback(healthy: Boolean): PluginSlots {
        require(previous != null && healthy) { "No healthy rollback version" }
        return PluginSlots(previous, active)
    }
}

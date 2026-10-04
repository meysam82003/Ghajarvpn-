package net.gozar.app.plugins

import net.gozar.app.*
import net.gozar.app.configtoolkit.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PluginContractsTest {
    companion object {
        private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        private val certificate = "ab".repeat(32)
        private val publisher = TrustedPublisher("test-publisher", Base64.getEncoder().encodeToString(key.public.encoded),
            setOf(certificate), setOf("shadowquic"), setOf("downloads.example.org"))
    }
    private fun manifest() = JSONObject().put("id", "shadowquic").put("name", "ShadowQUIC")
        .put("version", "0.4.0-ghajar.1").put("versionCode", 1).put("apiVersion", 1)
        .put("minGhajarVersion", "1.0.10").put("minGhajarVersionCode", 30025)
        .put("abis", JSONArray(listOf("arm64-v8a"))).put("capabilities", CapabilityContract(supportsTcp = true, supportsUdp = true, supportsSocks = true).json())
        .put("sourceRepository", PluginCatalog.candidate("shadowquic")!!.repository)
        .put("sourceCommit", PluginCatalog.candidate("shadowquic")!!.commit).put("sourceTag", "v0.4.0")
        .put("downloadUrl", "https://downloads.example.org/shadowquic-1.apk").put("size", 1024)
        .put("sha256", "cd".repeat(32)).put("publisher", publisher.id).put("certificateSha256", certificate)
        .put("license", "MIT").put("packageName", "net.ghajar.plugin.shadowquic.v1")
        .put("serviceClass", "net.ghajar.plugin.shadowquic.v1.EngineService").put("dependencies", JSONArray())
    private fun signed(payload: JSONObject = manifest()): String {
        val bytes = payload.toString().toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(key.private); update(bytes) }.sign()
        return JSONObject().put("publisher", publisher.id).put("payload", Base64.getEncoder().encodeToString(bytes))
            .put("signature", Base64.getEncoder().encodeToString(signature)).toString()
    }
    private fun trust(abis: Set<String> = setOf("arm64-v8a")) = PluginTrust(listOf(publisher), 30025, abis)
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (e: IllegalArgumentException) { } catch (e: IllegalStateException) { } }
    @Test fun validSignedManifestRetainsAllIdentityFields() {
        val envelope = signed(); val release = trust().verify(envelope)
        assertEquals(envelope, release.signedEnvelope); assertEquals(certificate, release.certificateSha256)
        assertEquals("shadowquic", release.id); assertEquals(1L, release.versionCode)
        assertTrue(release.capabilities.supportsSocks)
    }
    @Test fun unknownPublisherNeverBecomesTrustedFromManifest() {
        rejected { PluginTrust(emptyList(), 30025, setOf("arm64-v8a")).verify(signed()) }
        val envelope = JSONObject(signed()).put("publisher", "custom.authority.suffix")
        rejected { trust().verify(envelope.toString()) }
        assertTrue(ProductionPublishers.all.isEmpty()) // No production credentials manufactured in this phase.
    }
    @Test fun alteredSignedBytesCannotChangeDownloadOrCapabilities() {
        val wrapper = JSONObject(signed()); val altered = manifest().put("downloadUrl", "https://evil.example/a.apk")
        wrapper.put("payload", Base64.getEncoder().encodeToString(altered.toString().toByteArray()))
        rejected { trust().verify(wrapper.toString()) }
    }
    @Test fun certificatePackageAndServiceAreIndependentTrustGates() {
        for ((field, value) in listOf("certificateSha256" to "ef".repeat(32), "packageName" to "net.ghajar.plugin.shadowquic",
            "serviceClass" to "other.app.Service", "publisher" to "other", "sha256" to "short")) {
            rejected { trust().verify(signed(manifest().put(field, value))) }
        }
    }
    @Test fun apiAbiAndMinimumHostAreEnforced() {
        rejected { trust().verify(signed(manifest().put("apiVersion", 2))) }
        rejected { trust(setOf("armeabi-v7a")).verify(signed()) }
        rejected { trust().verify(signed(manifest().put("minGhajarVersionCode", 30026))) }
    }
    @Test fun sourceAndCapabilitiesCannotExpandBeyondAuditedPin() {
        rejected { trust().verify(signed(manifest().put("sourceCommit", "a".repeat(40)))) }
        rejected { trust().verify(signed(manifest().put("sourceRepository", "https://github.com/unknown/core"))) }
        rejected { trust().verify(signed(manifest().put("capabilities", CapabilityContract(supportsReality = true).json()))) }
    }
    @Test fun originsSizesAndSelfDependenciesFailClosed() {
        for (url in listOf("http://downloads.example.org/a.apk", "https://downloads.example.org.evil/a.apk", "https://user@downloads.example.org/a.apk", "https://downloads.example.org:444/a.apk"))
            rejected { trust().verify(signed(manifest().put("downloadUrl", url))) }
        for (size in listOf(-1L, 0L, 536870913L)) rejected { trust().verify(signed(manifest().put("size", size))) }
        rejected { trust().verify(signed(manifest().put("dependencies", JSONArray().put(JSONObject().put("id", "shadowquic").put("minVersionCode", 1))))) }
    }
    @Test fun fractionalVersionsAndUnknownCapabilityFieldsAreRejected() {
        rejected { trust().verify(signed(manifest().put("versionCode", 1.5))) }
        rejected { trust().verify(signed(manifest().put("capabilities", JSONObject().put("supportsEverything", true)))) }
        rejected { trust().verify(signed(manifest().put("capabilities", JSONObject().put("supportsSocks", "true")))) }
    }
    @Test fun failedHealthCheckNeverReplacesWorkingSlot() {
        val old = PluginSlots("healthy-v1", "healthy-v0")
        rejected { old.activate("broken-v2", false) }
        assertEquals("healthy-v1", old.active)
        val updated = old.activate("healthy-v2", true)
        assertEquals("healthy-v1", updated.previous)
        assertEquals(PluginSlots("healthy-v1", "healthy-v2"), updated.rollback(true))
        rejected { updated.rollback(false) }
    }
    private val yaml = "\uFEFF# فارسی — unchanged\r\nproxy-providers:\r\n  remote: &provider {type: http, url: 'https://example.org/sub'}\r\nproxy-groups:\r\n  - {name: auto, type: select, use: [remote]}\r\nrules:\r\n  - MATCH,auto\r\ntun:\r\n  enable: true\r\n  stack: mixed\r\ndns:\r\n  enhanced-mode: fake-ip\r\n"
    @Test fun fullYamlSurvivesImportValidationBackupAndNormalizationExactly() {
        val result = ImportRouter.decode(yaml.toByteArray(), "mihomo.yaml") as ImportRouter.Outcome.Imported
        assertEquals(1, result.configs.size)
        val config = result.configs.single()
        assertEquals("plugin", config.protocol)
        val roundtrip = ProxyConfig.fromJson(config.toJson())
        val normalized = NormalizedProfile.from(roundtrip, ConfigFormat.TEXT)
        assertTrue(ProfileValidator.validate(normalized).valid)
        assertEquals(yaml, PluginProfiles.read(normalized.toProxyConfig())!!.payload)
        assertEquals("mihomo", PluginProfiles.read(config)!!.id)
        assertTrue(result.warnings.single().contains("Mihomo"))
    }
    @Test fun independentFullConfigsDoNotCollapseIntoAddressPortZeroDuplicate() {
        val first = PluginProfiles.create("mihomo", "mihomo-yaml", yaml)
        val second = PluginProfiles.create("mihomo", "mihomo-yaml", yaml.replace("MATCH,auto", "MATCH,DIRECT"))
        assertNotEquals(PluginProfiles.identity(first), PluginProfiles.identity(second))
        assertEquals(PluginProfiles.identity(first), PluginProfiles.identity(first.copy(name = "renamed")))
    }
    @Test fun jsonAndUnknownPluginAvailabilityDoNotDiscardConfig() {
        val raw = " {\"proxy-providers\":{\"a\":{\"type\":\"http\"}},\"rules\":[\"MATCH,DIRECT\"],\"tun\":{\"enable\":true}} \n"
        assertEquals(raw, PluginProfiles.read(PluginProfiles.import(raw)!!)!!.payload)
        val sq = "shadowquic://user:secret@example.org:443?sni=camouflage.example#name"
        assertEquals(sq, PluginProfiles.read(ConfigParser.parseBundle(sq).single())!!.payload)
        assertEquals(2, ConfigParser.parseBundle("$sq\n$sq").size)
    }
    @Test fun pluginConfigCannotSilentlyBecomeXrayFreedomOutbound() {
        val config = PluginProfiles.create("mihomo", "mihomo-yaml", yaml)
        assertEquals(0, config.port)
        assertNull(PluginProfiles.import("vless://id@example.org:443"))
        rejected { PluginProfiles.create("unknown-authority", "json", "{}") }
        rejected { PluginProfiles.create("shadowquic", "mihomo-yaml", yaml) }
    }
}

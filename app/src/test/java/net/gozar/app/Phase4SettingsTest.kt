package net.gozar.app

import net.gozar.app.engine.SingBoxConfig
import net.gozar.app.plugins.PluginProfiles
import net.gozar.plugin.api.ShadowQuicProfile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Phase4SettingsTest {
    private fun parsed(link: String) = requireNotNull(ConfigParser.parse(link))
    private fun out(c: ProxyConfig) = JSONObject(SingBoxConfig.spec(c)!!).let { it.optJSONObject("outbound") ?: it.getJSONObject("endpoint") }
    private fun rejected(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun legacyHysteriaKeepsXrayAndHoppingUsesExistingSingBox() {
        val old = parsed("hysteria2://pw@example.org:443?sni=example.org")
        assertFalse(SingBoxConfig.handles(old))
        val c = EngineSettings.merge(old, mapOf("server_ports" to "443,20000-21000", "hop_interval" to "15"))
        val result = out(c)
        assertEquals("hysteria2", result.getString("type"))
        assertEquals("20000:21000", result.getJSONArray("server_ports").getString(1))
        assertEquals("15s", result.getString("hop_interval"))
        assertEquals(EngineSettings.read(c), EngineSettings.read(parsed(ConfigShare.toLink(c))))
        val cleared = EngineSettings.merge(c, mapOf("server_ports" to "", "hop_interval" to ""))
        assertFalse(SingBoxConfig.handles(cleared))
    }
    @Test fun hysteriaPortAuthorityAndCommonAliasesPreserveRanges() {
        val c = parsed("hysteria2://pw@[2001:db8::1]:443,20000-21000?sni=example.org&hopInterval=10s")
        assertEquals(443, c.port); assertEquals("443,20000-21000", EngineSettings.read(c)["server_ports"])
        assertEquals("10s", out(c).getString("hop_interval"))
        val alias = parsed("hy2://pw@example.org:443?mport=20000-21000&hopInterval=15s")
        assertEquals("20000:21000", out(alias).getJSONArray("server_ports").getString(0))
    }
    @Test fun rejectsInvalidHoppingBeforeCoreStart() {
        for (ports in listOf("0", "65536", "500-400", "1,,2", "443:x")) rejected { EngineSettings.ports(ports) }
        val c = parsed("hysteria2://pw@example.org:443")
        rejected { EngineSettings.merge(c, mapOf("hop_interval" to "15")) }
    }
    @Test fun anyTlsSettingsReachCoreAndRoundTrip() {
        val c = parsed("anytls://pw@example.org:443?idle_session_check_interval=12&idle_session_timeout=45&min_idle_session=3")
        val o = out(c); assertEquals("12s", o.getString("idle_session_check_interval")); assertEquals("45s", o.getString("idle_session_timeout")); assertEquals(3, o.getInt("min_idle_session"))
        assertEquals(EngineSettings.read(c), EngineSettings.read(parsed(ConfigShare.toLink(c))))
        assertFalse(EngineSettings.supported("anytls").any { it.key.contains("padding") })
    }
    @Test fun upstreamJsonKeepsAdvancedFieldsAndRejectsUnrepresentablePrecision() {
        val json = JSONObject("""{"outbounds":[{"type":"anytls","server":"example.org","server_port":443,"password":"pw","idle_session_timeout":"2m","min_idle_session":3}]}""")
        val c = ForeignImport.singBox(json, ConfigSource.PERSONAL).configs.single()
        assertEquals("120s", out(c).getString("idle_session_timeout"))
        json.getJSONArray("outbounds").getJSONObject(0).put("idle_session_timeout", "500ms")
        val invalid = ForeignImport.singBox(json, ConfigSource.PERSONAL)
        assertTrue(invalid.configs.isEmpty()); assertTrue(invalid.warnings.isNotEmpty())
    }
    @Test fun sourceChoicesDoNotCreateImplicitTrustedSources() {
        val first = net.gozar.app.freecfg.FreeSourceRegistry.DEFAULT_SOURCES.first().id
        val policy = net.gozar.app.freecfg.FreeSourcePolicy.normalize(JSONObject().put(first, "removed").put("unreviewed", "enabled"))
        assertFalse(policy.has("unreviewed"))
        assertFalse(net.gozar.app.freecfg.FreeSourcePolicy.active(policy).any { it.id == first })
        assertEquals(policy.toString(), JSONObject(policy.toString()).toString())
    }
    @Test fun tuicStreamHeartbeatAndTimeoutAndConflict() {
        val c = parsed("tuic://11111111-2222-3333-4444-555555555555:pw@example.org:443?udp_over_stream=true&heartbeat=8&idle_timeout=60")
        val o = out(c); assertTrue(o.getBoolean("udp_over_stream")); assertEquals("8s", o.getString("heartbeat")); assertEquals("60s", o.getString("idle_timeout")); assertFalse(o.optBoolean("zero_rtt_handshake"))
        rejected { EngineSettings.merge(c.copy(mode="quic"), emptyMap()) }
    }
    @Test fun echIsPemForPinnedCoreAndFailsClosedWithInsecure() {
        val encoded = java.util.Base64.getEncoder().encodeToString(byteArrayOf(0,5,0xfe.toByte(),13,0,1,0))
        val c = EngineSettings.merge(parsed("hysteria2://pw@example.org:443?sni=example.org"), mapOf("ech_config" to encoded))
        val ech = out(c).getJSONObject("tls").getJSONObject("ech")
        assertTrue(ech.getBoolean("enabled")); assertTrue(ech.getJSONArray("config").getString(0).startsWith("-----BEGIN ECH CONFIGS-----\n"))
        rejected { EngineSettings.validate(c.copy(allowInsecure=true)) }
        rejected { EngineSettings.merge(c, mapOf("ech_config" to "not base64")) }
    }
    @Test fun openConnectSecretsStayInProfileNotShareLink() {
        val old = parsed("openconnect://user:pw@example.org:443?authgroup=staff&reconnect=30&nodtls=1")
        val c = EngineSettings.merge(old, mapOf("cookie" to "secret-cookie", "token_mode" to "totp", "token_secret" to "TESTSECRET"))
        val o = out(c); assertEquals("secret-cookie", o.getString("cookie")); assertEquals("staff", o.getString("auth_group")); assertTrue(o.getBoolean("no_udp")); assertEquals("30s", o.getString("reconnect_timeout")); assertEquals("totp", o.getJSONObject("token").getString("mode"))
        assertFalse(ConfigShare.toLink(c).contains("secret-cookie"))
        assertTrue(c.extra.contains("secret-cookie")); assertEquals("staff", c.extraJson().getString("authGroup"))
    }
    @Test fun formUsesSameValidationAndCapabilityFields() {
        assertFalse(ProtocolForms.form("ssh").fields.any { it.key == "server_ports" })
        assertTrue(ProtocolForms.form("hysteria2").fields.any { it.key == "server_ports" })
        assertTrue(ProtocolForms.build("anytls", mapOf("server" to "example.org", "pass" to "pw", "min_idle_session" to "-1")).isFailure)
    }
    @Test fun sstpExtrasGoOnlyToHelper() {
        val c = EngineSettings.merge(parsed("sstp://u:p@example.org:443"), mapOf("dns_fallback" to "1.1.1.1", "tls_min" to "1.3", "http_proxy" to "https://u:secret@proxy.example:443"))
        val spec = JSONObject(SingBoxConfig.spec(c)!!)
        assertEquals("1.3", spec.getJSONObject("sidecar").getString("tls_min"))
        assertFalse(spec.getJSONObject("outbound").has("http_proxy")); assertFalse(ConfigShare.toLink(c).contains("secret"))
    }
    @Test fun aetherMimAndExitFilterHaveRealArguments() {
        val o = OblivionOptions("""{"core":"aether","protocol":"mim","mimOuter":"1.1.1.1:443","mimInner":"1.0.0.1:443","exitLoc":"!IR"}""")
        o.validate(); assertTrue(o.aetherArgs().contains("--mim")); assertTrue(o.aetherArgs().contains("--mim-outer")); assertTrue(o.aetherArgs().contains("--exit-loc")); assertFalse(o.aetherArgs().contains("--masque"))
    }
    @Test fun shadowQuicTypedConfigPreservesCredentialsAndNoUnsafeModes() {
        val raw = "shadowquic://user:p%40ss@example.org:443?sni=camouflage.example&udp_mode=stream"
        val p = ShadowQuicProfile.parse(raw, "shadowquic-uri")
        val full = JSONObject(ShadowQuicProfile.config(p, 1080)); val outbound = full.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("p@ss", outbound.getString("password")); assertTrue(outbound.getBoolean("over-stream")); assertFalse(outbound.getBoolean("zero-rtt"))
        assertEquals(raw, PluginProfiles.read(PluginProfiles.import(raw)!!)!!.payload)
        rejected { ShadowQuicProfile.parse(raw + "&exec=bad", "shadowquic-uri") }
        rejected { ShadowQuicProfile.parse("shadowquic://user:pw@example.org:443", "shadowquic-uri") }
    }
    @Test fun shadowQuicJsonExportImportsWithoutPlugin() {
        val c = ProtocolForms.build("shadowquic", mapOf("server" to "example.org", "user" to "u", "pass" to "p", "sni" to "cover.example")).getOrThrow()
        val exported = ConfigShare.toLink(c); val restored = PluginProfiles.import(exported)!!
        assertEquals(PluginProfiles.read(c)!!.payload, PluginProfiles.read(restored)!!.payload)
    }
    @Test fun absentSettingsMigrationLeavesLegacyExtrasUntouched() {
        val c = parsed("anytls://p@example.org:443").copy(extra="""{"legacy":"keep"}""")
        val updated = EngineSettings.merge(c, mapOf("min_idle_session" to "0"))
        assertEquals(updated.extra, ProxyConfig.fromJson(updated.toJson()).extra)
        assertEquals("keep", updated.extraJson().getString("legacy"))
        assertEquals("keep", EngineSettings.merge(updated, mapOf("min_idle_session" to "")).extraJson().getString("legacy"))
    }
}

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
    @Test fun openConnectPublicOptionsReachEndpointAndSecretsStayLocal() {
        val privateValues = mapOf("token_mode" to "stoken", "token_secret" to "fixture-token", "token_pin" to "1234", "token_password" to "fixture-password", "token_device_id" to "fixture-device", "form_entries" to """[{"form_id":"login","name":"realm","value":"fixture-realm","promote":true}]""")
        val publicValues = mapOf("dpd_interval" to "30", "trojan_interval" to "60", "base_mtu" to "1400", "queue_length" to "64", "tcp_keep_alive_enabled" to "true", "compression_mode" to "stateless", "xml_post_disabled" to "true", "client_version" to "1.2.3", "pfs" to "true")
        for (flavor in listOf("anyconnect", "gp", "fortinet", "f5", "pulse", "nc")) {
            val c = EngineSettings.merge(parsed("openconnect://u:p@example.org?flavor=$flavor"), privateValues + publicValues)
            val o = out(c)
            assertEquals(flavor, o.getString("flavor")); assertEquals(1400, o.getInt("base_mtu")); assertEquals("30s", o.getString("dpd_interval"))
            assertEquals("1234", o.getJSONObject("token").getString("pin")); assertTrue(o.getBoolean("pfs"))
            assertEquals("fixture-realm", o.getJSONArray("form_entries").getJSONObject(0).getString("value"))
            val link = ConfigShare.toLink(c)
            assertFalse(link.contains("fixture")); assertFalse(link.contains("1234"))
            assertEquals(publicValues, EngineSettings.read(parsed(link)))
        }
        val c = parsed("openconnect://u:p@example.org")
        rejected { EngineSettings.merge(c, mapOf("token_mode" to "oidc", "token_secret" to "fixture")) }
        rejected { EngineSettings.merge(c, mapOf("token_mode" to "hotp", "token_secret" to "fixture")) }
        rejected { EngineSettings.merge(c, mapOf("base_mtu" to "1")) }
        rejected { EngineSettings.merge(c, mapOf("form_entries" to "[{hook:'run'}]")) }
        rejected { out(c.copy(mode = "array")) }
    }
    @Test fun strictExtendedJsonRejectsJavascriptButRetainsComments() {
        for (bad in listOf("{\"x\":1,\"x\":2}", "{\"x\":1,\"\\u0078\":2}", "{,}", "{\"a\":[,]}", "{\"a\":,}", "{unquoted:1}", "{\"n\":01}", "{\"n\":NaN}", "{\"n\":+1}", "{\"n\":'text'}"))
            rejected { net.gozar.app.configtoolkit.BoundedJson.objectValue(bad) }
        assertEquals(1, net.gozar.app.configtoolkit.BoundedJson.objectValue("{/**/\"n\":1,}").getInt("n"))
    }
    @Test fun chainNeverFallsBackWhenCarrierMissingOrRecursive() {
        val exit=parsed("vless://00000000-0000-0000-0000-000000000001@example.org:443")
        val carrier=parsed("trojan://fixture@carrier.example:443")
        val linked=exit.copy(chainId=carrier.id)
        rejected { ConfigBuilder.buildForTest(linked) }
        rejected { net.gozar.app.engine.ChainPlan.validate(exit,exit) }
        rejected { net.gozar.app.engine.ChainPlan.validate(linked,carrier.copy(chainId=exit.id)) }
        val http=parsed("http://carrier.example:8080")
        rejected { net.gozar.app.engine.ChainPlan.validate(exit.copy(network="kcp",chainId=http.id),http) }
        val restored=ProxyConfig.fromJson(linked.toJson())!!
        val plan=net.gozar.app.engine.ChainPlan.resolve(restored,listOf(carrier,restored))!!
        assertEquals(carrier.id,plan.carrierId);assertEquals(restored.id,plan.exitId)
        assertEquals("chain",JSONObject(ConfigBuilder.buildForTest(restored,carrier)).getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy"))
    }
    @Test fun vayTuningFlowsFromFormToSpecAndRealCliNames() {
        val c=ProtocolForms.build("dnstt",mapOf("variant" to "vaydns","domain" to "t.example","pubkey" to "aa".repeat(32),"resolver" to "1.1.1.1:53","rps" to "12.5","idle_timeout" to "30s","keepalive" to "2s","resolver_timeout" to "500ms","max_labels" to "4")).getOrThrow()
        val side=JSONObject(SingBoxConfig.spec(c)!!).getJSONObject("sidecar")
        assertEquals(listOf("-rps","12.5","-idle-timeout","30s","-keepalive","2s","-udp-timeout","500ms","-max-num-labels","4").toSet(),net.gozar.app.engine.DnsTunnelTuning.args(side).toSet())
        val again=parsed(ConfigShare.toLink(c));assertEquals(side.getJSONObject("tuning").toString(),JSONObject(SingBoxConfig.spec(again)!!).getJSONObject("sidecar").getJSONObject("tuning").toString())
        assertNull(ConfigParser.parse("dnstt://t.example?pubkey=${"aa".repeat(32)}&transport=tcp&resolver=1.1.1.1"))
        rejected { net.gozar.app.engine.DnsTunnelTuning.validate(mapOf("rps" to "NaN")) }
        rejected { net.gozar.app.engine.DnsTunnelTuning.validate(mapOf("idle_timeout" to "1s","keepalive" to "2s")) }
    }
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
        val c = ForeignImport.singBoxNodes(json, ConfigSource.PERSONAL).configs.single()
        assertEquals("120s", out(c).getString("idle_session_timeout"))
        json.getJSONArray("outbounds").getJSONObject(0).put("idle_session_timeout", "500ms")
        val invalid = ForeignImport.singBoxNodes(json, ConfigSource.PERSONAL)
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

package net.gozar.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Phase6RegressionTest {
    private val base = ProxyConfig("test", "vless", "example.org", 443, uuid="00000000-0000-4000-8000-000000000001")
    private fun outbound(c: ProxyConfig) = JSONObject(ConfigBuilder.build(c)).getJSONArray("outbounds").getJSONObject(0)

    @Test fun realityPreservesCrawlerPathThroughEveryTextPath() {
        val config=base.copy(security="reality",publicKey="test-public",shortId="0123",spiderX="/crawler?a=one%20two",flow="xtls-rprx-vision")
        assertEquals(config,ProxyConfig.fromJson(config.toJson()))
        assertEquals(config.spiderX,ConfigParser.parse(ConfigShare.toLink(config))!!.spiderX)
        assertEquals(config.spiderX,outbound(config).getJSONObject("streamSettings").getJSONObject("realitySettings").getString("spiderX"))
        assertEquals(config.spiderX,net.gozar.app.configtoolkit.NormalizedProfile.from(config,net.gozar.app.configtoolkit.ConfigFormat.JSON).toProxyConfig().spiderX)
        assertEquals(config.spiderX,ConfigParser.parseBundle(ConfigBuilder.build(config)).first().spiderX)
        assertEquals("/",ProxyConfig.fromJson(config.toJson().apply { remove("spiderX") }).spiderX)
    }
    @Test fun wireguardKeepsIpv6AndPresharedKey() {
        val peer=outbound(base.copy(protocol="wireguard",address="2001:db8::1",port=51820,password="synthetic-psk"))
            .getJSONObject("settings").getJSONArray("peers").getJSONObject(0)
        assertEquals("[2001:db8::1]:51820",peer.getString("endpoint"))
        assertEquals("synthetic-psk",peer.getString("preSharedKey"))
        assertFalse(outbound(base.copy(protocol="wireguard")).getJSONObject("settings").getJSONArray("peers").getJSONObject(0).has("preSharedKey"))
    }
    @Test fun mkcpMigratesWireSchemaWithoutResettingStoredProfile() {
        for (header in listOf("", "none", "srtp", "utp", "wechat", "dtls", "wireguard", "dns")) {
            for(seed in listOf("", "synthetic-seed")) {
                val config=base.copy(network="kcp",headerType=header,path=seed)
                val stream=outbound(config).getJSONObject("streamSettings")
                assertEquals(0,stream.getJSONObject("kcpSettings").length())
                val masks=stream.getJSONObject("finalmask").getJSONArray("udp")
                assertEquals(if(header in listOf("", "none")) 1 else 2,masks.length())
                val cipher=masks.getJSONObject(masks.length()-1)
                assertEquals(if(seed.isEmpty()) "mkcp-original" else "mkcp-aes128gcm",cipher.getString("type"))
                if(seed.isNotEmpty()) assertEquals(seed,cipher.getJSONObject("settings").getString("password"))
                assertEquals(config,ProxyConfig.fromJson(config.toJson()))
                assertEquals(seed,ConfigParser.parseBundle(ConfigBuilder.build(config)).first().path)
            }
        }
    }
    @Test fun sharingOffHasNoDedicatedListenerAndPatchingPreservesRestOfConfig() {
        val off=JSONObject(ConfigBuilder.build(base))
        val on=JSONObject(ConfigBuilder.withPhoneSharing(off.toString(),true))
        assertEquals(2,off.getJSONArray("inbounds").length())
        assertEquals(3,on.getJSONArray("inbounds").length())
        assertEquals(off.getJSONArray("outbounds").toString(),on.getJSONArray("outbounds").toString())
        assertEquals(off.getJSONObject("routing").toString(),on.getJSONObject("routing").toString())
        assertEquals(off.toString(),JSONObject(ConfigBuilder.withPhoneSharing(on.toString(),false)).toString())
    }
    @Test fun networkFlapsBackOffAndStableNetworkResets() {
        val backoff=ReconnectBackoff()
        assertEquals(1200L,backoff.delayMs(0))
        backoff.attempted(1200)
        assertEquals(2400L,backoff.delayMs(1200))
        backoff.attempted(3600)
        assertEquals(4800L,backoff.delayMs(3600))
        repeat(10) { backoff.attempted(5000L+it) }
        assertEquals(30000L,backoff.delayMs(5009))
        assertEquals(1200L,backoff.delayMs(65009))
    }
}

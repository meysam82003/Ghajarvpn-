package net.gozar.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PsiphonConfigTest {
    @Test fun ephemeralPortIsBoundOnlyToThePsiphonOutbound() {
        val raw = """{"outbounds":[{"tag":"proxy","protocol":"socks","settings":{"servers":[{"address":"127.0.0.1","port":0}]}},{"tag":"direct","protocol":"freedom"}]}"""
        val root = JSONObject(PsiphonConfig.bindSocksPort(raw, 42421))
        assertEquals(42421, root.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings").getJSONArray("servers").getJSONObject(0).getInt("port"))
        assertEquals("freedom", root.getJSONArray("outbounds").getJSONObject(1).getString("protocol"))
    }
    @Test(expected = IllegalArgumentException::class) fun zeroPortCannotStartATunnel() {
        PsiphonConfig.bindSocksPort("{}", 0)
    }
    @Test fun cdnSettingsRejectInvalidAddresses() {
        assertEquals(listOf("1.2.3.4", "104.16.0.0/13"), PsiphonConfig.ipCandidates("1.2.3.4, 999.1.2.3; 104.16.0.0/13 1.2.3.4"))
        assertEquals(listOf("example.com"), PsiphonConfig.sniCandidates("https://example.com/path; bad@host example.com"))
    }
    @Test fun settingsSurviveProcessSerialization() {
        val spec = PsiphonSpec("cdn", "DE", "1.2.3.4", "example.com")
        assertEquals(spec, PsiphonSpec.parse(spec.toJson()))
        val config = ProxyConfig(name = "Psiphon", protocol = "psiphon", address = "127.0.0.1", port = 1,
            psiphonCdnIps = "1.2.3.4", psiphonCdnSni = "example.com")
        assertEquals("1.2.3.4", PsiphonSpec.from(config)?.cdnIps)
    }
}

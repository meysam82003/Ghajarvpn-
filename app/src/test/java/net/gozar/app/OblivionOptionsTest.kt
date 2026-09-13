package net.gozar.app
import org.junit.Assert.*
import org.junit.Test

class OblivionOptionsTest {
    @Test fun chainUsesDistinctLocalPortsAndOnlyTcpProtocols() {
        val options=OblivionOptions("""{"core":"chain","socksPort":"1819"}""")
        assertEquals(1829,options.aetherPort)
        assertTrue(options.aetherArgs().contains("127.0.0.1:1829"))
        for(mode in listOf("auto","cdn","direct"))assertTrue(PsiphonConfig.chainedProtocols(mode).none{it.contains("QUIC")||it.startsWith("INPROXY")})
        assertTrue(PsiphonConfig.chainedProtocols("direct").none{it.startsWith("FRONTED")})
    }
    @Test fun advancedSettingsArePassedAsSeparateArguments() {
        val options=OblivionOptions("""{"core":"aether","transport":"h2","fragment":"true","fragmentSize":"12-24","team":"example","accessToken":"token with spaces","dnsPrimary":"9.9.9.9"}""")
        val args=options.aetherArgs()
        assertTrue(args.contains("--fragment"));assertEquals("12-24",args[args.indexOf("--fragment-size")+1])
        assertEquals("token with spaces",args[args.indexOf("--access-token")+1])
        assertEquals("9.9.9.9,1.0.0.1",args[args.indexOf("--dns")+1])
    }
    @Test fun allSettingsSurviveProfileAndProcessBoundaries() {
        val raw="""{"core":"chain","routeBlock":"example.com","socksPort":"8088"}"""
        val config=ProxyConfig(name="Oblivion",protocol="psiphon",address="127.0.0.1",port=0,oblivionJson=raw)
        val restored=ProxyConfig.fromJson(config.toJson())
        assertEquals(raw,restored.oblivionJson)
        assertEquals(raw,AetherSpec.parse(AetherSpec.from(restored)!!.toJson())!!.oblivionJson)
        assertEquals(raw,PsiphonSpec.parse(PsiphonSpec.from(restored)!!.toJson())!!.oblivionJson)
        assertNull(PsiphonSpec.from(config.copy(oblivionJson="""{"core":"aether"}""")))
    }
}

package net.gozar.app.configtoolkit

import net.gozar.app.ConfigParser
import net.gozar.app.ForeignImport
import net.gozar.app.MiniYaml
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignImportTest {

    private val clash = """
        # Mihomo profile
        mixed-port: 7890
        proxies:
          - name: "ss-plain"
            type: ss
            server: 203.0.113.1
            port: 8388
            cipher: aes-128-gcm
            password: "p#ss"
          - {name: vless-reality, type: vless, server: r.example.com, port: 443, uuid: 11111111-2222-3333-4444-555555555555,
             network: tcp, tls: true, flow: xtls-rprx-vision, servername: www.apple.com, client-fingerprint: chrome,
             reality-opts: {public-key: PUBKEY123, short-id: ab12}}
          - name: vmess-ws
            type: vmess
            server: v.example.com
            port: 443
            uuid: 11111111-2222-3333-4444-555555555555
            alterId: 0
            cipher: auto
            tls: true
            network: ws
            ws-opts:
              path: /ray
              headers:
                Host: cdn.example.com
          - name: hy2
            type: hysteria2
            server: h.example.com
            port: 8443
            password: pw
            sni: h.example.com
            skip-cert-verify: true
          - name: wg
            type: wireguard
            server: 198.51.100.7
            port: 51820
            ip: 10.0.0.2
            private-key: aGVsbG8taGVsbG8taGVsbG8taGVsbG8taGVsbG8tMTI=
            public-key: d29ybGQtd29ybGQtd29ybGQtd29ybGQtd29ybGQtMTI=
            mtu: 1280
          - name: legacy
            type: ssr
            server: x.example.com
            port: 1
        proxy-groups:
          - name: auto
            type: url-test
            proxies: [ss-plain, hy2]
    """.trimIndent()

    @Test
    fun miniYamlReadsBlockAndFlowStyles() {
        val root = MiniYaml.parse(clash) as Map<*, *>
        val proxies = root["proxies"] as List<*>
        assertEquals(6, proxies.size)
        assertEquals("p#ss", (proxies[0] as Map<*, *>)["password"])
        val reality = (proxies[1] as Map<*, *>)["reality-opts"] as Map<*, *>
        assertEquals("ab12", reality["short-id"])
        val ws = (proxies[2] as Map<*, *>)["ws-opts"] as Map<*, *>
        assertEquals("cdn.example.com", (ws["headers"] as Map<*, *>)["Host"])
    }

    @Test
    fun clashProxiesBecomeProfiles() {
        val r = ForeignImport.clash(clash)
        val by = r.configs.associateBy { it.name }
        assertEquals(setOf("ss-plain", "vless-reality", "vmess-ws", "hy2", "wg"), by.keys)
        assertEquals("shadowsocks", by.getValue("ss-plain").protocol); assertEquals("p#ss", by.getValue("ss-plain").password)
        val v = by.getValue("vless-reality")
        assertEquals("reality", v.security); assertEquals("PUBKEY123", v.publicKey); assertEquals("ab12", v.shortId)
        assertEquals("www.apple.com", v.sni); assertEquals("xtls-rprx-vision", v.flow)
        val m = by.getValue("vmess-ws")
        assertEquals("ws", m.network); assertEquals("/ray", m.path); assertEquals("cdn.example.com", m.host)
        assertTrue(by.getValue("hy2").allowInsecure)
        assertEquals(1280, by.getValue("wg").mtu)
        assertTrue(r.warnings.single().contains("ssr"))
        // The common entry point takes the YAML as a subscription body.
        assertEquals(5, ConfigParser.parseBundle(clash).size)
    }

    @Test
    fun singBoxOutboundsAndShadowTlsDetour() {
        val sb = JSONObject("""
          {"outbounds":[
            {"type":"vless","tag":"v","server":"a.example.com","server_port":443,"uuid":"11111111-2222-3333-4444-555555555555",
             "tls":{"enabled":true,"server_name":"a.example.com","utls":{"enabled":true,"fingerprint":"chrome"}},
             "transport":{"type":"grpc","service_name":"gs"}},
            {"type":"shadowsocks","tag":"ss","method":"2022-blake3-aes-128-gcm","password":"k","detour":"st"},
            {"type":"shadowtls","tag":"st","server":"1.2.3.4","server_port":443,"version":3,"password":"pw","tls":{"enabled":true,"server_name":"www.apple.com"}},
            {"type":"tuic","tag":"t","server":"t.example.com","server_port":8443,"uuid":"11111111-2222-3333-4444-555555555555","password":"p",
             "congestion_control":"bbr","tls":{"enabled":true,"server_name":"t.example.com","alpn":["h3"]}},
            {"type":"direct","tag":"direct"},{"type":"selector","tag":"sel","outbounds":["v"]}
          ],
          "endpoints":[{"type":"wireguard","tag":"wg","address":["10.0.0.2/32"],"private_key":"aGVsbG8taGVsbG8taGVsbG8taGVsbG8taGVsbG8tMTI=",
             "peers":[{"address":"198.51.100.7","port":51820,"public_key":"d29ybGQtd29ybGQtd29ybGQtd29ybGQtd29ybGQtMTI="}]}]}
        """.trimIndent())
        val list = ConfigParser.parseJsonOutbounds(sb.toString())
        val by = list.associateBy { it.name }
        assertEquals(setOf("v", "ss", "t", "wg"), by.keys)
        assertEquals("grpc", by.getValue("v").network); assertEquals("gs", by.getValue("v").serviceName)
        assertEquals("chrome", by.getValue("v").fingerprint)
        val st = by.getValue("ss")
        assertEquals("shadowtls", st.protocol); assertEquals("1.2.3.4", st.address); assertEquals("www.apple.com", st.sni)
        assertEquals("tuic", by.getValue("t").protocol)
        assertEquals("198.51.100.7", by.getValue("wg").address)
    }

    @Test
    fun fileImportKeepsFieldsTheToolkitDoesNotModel() {
        val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val links = "dnstt://t.example.com?pubkey=$key&transport=dot&resolver=1.1.1.1#D\nsstp://u:p@s.example.com?mtu=1300#S"
        val out = ImportRouter.decode(links.toByteArray(), "list.txt")
        assertTrue(out.toString(), out is ImportRouter.Outcome.Imported)
        val cfgs = (out as ImportRouter.Outcome.Imported).configs.associateBy { it.protocol }
        val direct = ConfigParser.parseBundle(links).associateBy { it.protocol }
        assertEquals(direct.getValue("dnstt").copy(id = "x"), cfgs.getValue("dnstt").copy(id = "x"))
        assertEquals(1300, cfgs.getValue("sstp").mtu)
    }
}

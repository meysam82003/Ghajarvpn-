package net.gozar.app.sharing

import net.gozar.app.*
import net.gozar.app.configtoolkit.ImportRouter
import net.gozar.app.plugins.PluginProfiles
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.*
import java.io.*
import java.util.Base64
import java.util.concurrent.*

class SharingTest {
    private val user="ghajar"; private val password="secret-session-0123456789"
    private fun profile()=ProxyConfig("sample","vless","example.org",443,uuid="abc",security="tls",sni="example.org")
    @Test fun packageKeepsSecretsOptionsAndRemapsDependencies() {
        val base=profile(); val c=profile().copy(chainId=base.id,extra=JSONObject().put("engineSettings",JSONObject().put("version",1)).toString())
        val text=DirectShare.packageOf(c,listOf(c,base))
        val result=ImportRouter.decode(text.toByteArray(),"share.ghajar.json") as ImportRouter.Outcome.Imported
        assertEquals(2,result.configs.size);assertNotEquals(c.id,result.configs.last().id)
        assertEquals(result.configs.first().id,result.configs.last().chainId)
        assertEquals(c.extra,result.configs.last().extra)
    }
    @Test fun cyclicAndMissingDependenciesAreRejected() {
        val c=profile();assertTrue(runCatching { DirectShare.packageOf(c.copy(chainId=c.id),listOf(c.copy(chainId=c.id))) }.isFailure)
        assertTrue(runCatching { DirectShare.packageOf(c.copy(chainId="absent"),listOf(c)) }.isFailure)
    }
    @Test fun unknownSchemaIsNotPasswordPrompt() {
        val text=JSONObject(DirectShare.packageOf(profile(),listOf(profile()))).put("schemaVersion",2).toString()
        assertTrue(ImportRouter.decode(text.toByteArray()) is ImportRouter.Outcome.Invalid)
    }
    @Test fun pluginFullYamlPreservedWithoutInstalledBinary() {
        val yaml="mixed-port: 7890\nproxies: []\nrules:\n - MATCH,DIRECT\n"
        val c=PluginProfiles.create("mihomo","mihomo-yaml",yaml)
        val imported=DirectShare.importPackage(DirectShare.packageOf(c,listOf(c)),ConfigSource.PERSONAL)!!.single()
        assertEquals(yaml,PluginProfiles.read(imported)!!.payload)
        assertEquals(yaml,DirectShare.exports(c,DirectShare.Device.LINUX).first().text)
    }
    @Test fun wireguardOriginalKeepsDnsAllowedIpsAndKeepalive() {
        val key=Base64.getEncoder().encodeToString(ByteArray(32){1})
        val raw="[Interface]\nPrivateKey = $key\nAddress = 10.0.0.2/32\nDNS = 1.1.1.1\n[Peer]\nPublicKey = $key\nEndpoint = [2001:db8::1]:51820\nAllowedIPs = 10.0.0.0/8\nPersistentKeepalive = 25"
        val c=ConfigParser.parseWireguardConf(raw)!!;assertEquals(raw,DirectShare.wireguard(c))
        assertTrue(runCatching { DirectShare.wireguard(c.copy(port=1234)) }.isFailure)
    }
    @Test fun mobileconfigEscapesXmlAndDoesNotEmbedPassword() {
        val c=profile().copy(protocol="ikev2",port=500,uuid="a&b",name="<vpn>",password="never-export-this")
        val xml=DirectShare.ikev2(c)
        assertTrue(xml.contains("a&amp;b"));assertTrue(xml.contains("&lt;vpn&gt;"));assertFalse(xml.contains(c.password));assertFalse(xml.contains("AuthPassword"))
        val f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false)
        f.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
    }
    @Test fun mobileconfigNeedsRemoteIdentity() { assertTrue(runCatching { DirectShare.ikev2(profile().copy(protocol="ikev2",port=500,sni="")) }.isFailure) }
    @Test fun standardLinksBracketIpv6() { assertTrue(ConfigShare.toLink(profile().copy(address="2001:db8::1")).contains("@[2001:db8::1]:443")) }
    @Test fun lockedProfilesCannotEscapeThroughPackage() { assertTrue(runCatching { DirectShare.packageOf(profile().copy(locked=true),emptyList()) }.isFailure) }
    @Test fun openvpnPortableRejectsExternalFilesAndHooks() {
        OpenVpnExport.validate("client\nremote example.org 443\nauth-user-pass\n<ca>\nCERT\n</ca>")
        assertTrue(runCatching { OpenVpnExport.validate("client\nca /private/ca.pem") }.isFailure)
        assertTrue(runCatching { OpenVpnExport.validate("client\nup run-me") }.isFailure)
    }
    @Test fun httpWithoutAuthGets407AndNoUpstreamConnection() {
        ServerSocket(0).use { backend ->
            AuthenticatedRelay(backend.localPort,user,password).use { relay ->
                relay.start(InetAddress.getLoopbackAddress(),0)
                Socket("127.0.0.1",relay.port).use { client -> client.soTimeout=2000
                    client.getOutputStream().write("CONNECT example.org:443 HTTP/1.1\r\n\r\n".toByteArray())
                    assertTrue(client.getInputStream().bufferedReader().readLine().contains("407"))
                }
                backend.soTimeout=150;assertTrue(runCatching { backend.accept() }.exceptionOrNull() is SocketTimeoutException)
            }
        }
    }
    private fun read(i:InputStream,n:Int):ByteArray { val b=ByteArray(n);var p=0;while(p<n){val c=i.read(b,p,n-p);if(c<0)throw EOFException();p+=c};return b }
    @Test fun httpConnectUsesRemoteDnsCountsBytesAndStopClosesClient() {
        ServerSocket(0).use { backend ->
            val dest=CompletableFuture<String>();val executor=Executors.newSingleThreadExecutor()
            val task=executor.submit { backend.accept().use { s -> s.soTimeout=4000;val i=s.getInputStream();val o=s.getOutputStream()
                assertArrayEquals(byteArrayOf(5,1,0),read(i,3));o.write(byteArrayOf(5,0))
                assertArrayEquals(byteArrayOf(5,1,0,3),read(i,4));dest.complete(String(read(i,i.read())))
                assertArrayEquals(byteArrayOf(1,-69),read(i,2));o.write(byteArrayOf(5,0,0,1,0,0,0,0,0,0))
                val b=read(i,4);o.write(b);o.flush();i.read()
            } }
            try {
                AuthenticatedRelay(backend.localPort,user,password).use { relay -> relay.start(InetAddress.getLoopbackAddress(),0)
                    Socket("127.0.0.1",relay.port).use { client -> client.soTimeout=3000
                        val auth=Base64.getEncoder().encodeToString("$user:$password".toByteArray())
                        client.getOutputStream().write("CONNECT no-local-dns.invalid:443 HTTP/1.1\r\nProxy-Authorization: Basic $auth\r\n\r\n".toByteArray())
                        val i=client.getInputStream();val header=ByteArrayOutputStream()
                        while(!header.toString().endsWith("\r\n\r\n")) header.write(i.read().also { require(it>=0) })
                        assertTrue(header.toString().startsWith("HTTP/1.1 200"));assertEquals("no-local-dns.invalid",dest.get(2,TimeUnit.SECONDS))
                        client.getOutputStream().write("PING".toByteArray());assertEquals("PING",String(read(i,4)))
                        assertEquals(4,relay.clients().single().uploaded.toInt());assertEquals(4,relay.clients().single().downloaded.toInt())
                        relay.close();assertEquals(-1,i.read());assertTrue(relay.clients().isEmpty())
                    }
                };task.get(3,TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }
    @Test fun sharingCapabilityExcludesUnprovenEnginePaths() {
        assertTrue(net.gozar.app.engine.CapabilityRegistry.supportsPhoneSharing(profile()))
        assertFalse(net.gozar.app.engine.CapabilityRegistry.supportsPhoneSharing(profile().copy(protocol="wireguard")))
        assertFalse(net.gozar.app.engine.CapabilityRegistry.supportsPhoneSharing(profile().copy(protocol="openconnect")))
        val hopping=EngineSettings.merge(profile().copy(protocol="hysteria2"),mapOf("server_ports" to "443:445"))
        assertFalse(net.gozar.app.engine.CapabilityRegistry.supportsPhoneSharing(hopping))
        val bundle=JSONObject(DirectShare.packageOf(profile(),emptyList()))
        assertEquals("XRAY",bundle.getString("coreRequirement"))
    }
    @Test fun sharedInboundAlwaysRoutesBeforeDirectAndDnsRules() {
        val json=JSONObject(ConfigBuilder.build(profile(),false,true,true,setOf("http","tls"),youtubeDirect=true,encryptedDns=true,
            shareOnLan=true,shareUser="u",sharePass="p",shareListenAddress="192.168.43.1"))
        val first=json.getJSONObject("routing").getJSONArray("rules").getJSONObject(0)
        assertEquals("phone-share-in",first.getJSONArray("inboundTag").getString(0));assertEquals("proxy",first.getString("outboundTag"))
        val inbounds=json.getJSONArray("inbounds")
        for(i in 0 until inbounds.length()) {
            val inbound=inbounds.getJSONObject(i)
            if(inbound.optString("protocol")=="socks") assertEquals("127.0.0.1",inbound.getString("listen"))
            assertNotEquals("http-share-in",inbound.optString("tag"))
        }
    }
    @Test fun openvpnExportStripsOldInlineAccountPassword() {
        val exported=OpenVpnExport.portable("client\n<auth-user-pass>\nold-user\nold-password\n</auth-user-pass>\n")
        assertFalse(exported.contains("old-password"));assertTrue(exported.contains("auth-user-pass"))
        assertTrue(runCatching { OpenVpnExport.validate("<connection>\nup dangerous\n</connection>") }.isFailure)
    }
    @Test fun socksConnectForwardsIpv6AndClosesOnStop() {
        ServerSocket(0).use { backend ->
            val worker=Executors.newSingleThreadExecutor()
            val destination=byteArrayOf(5,1,0,4)+InetAddress.getByName("2001:db8::1").address+byteArrayOf(1,-69)
            val task=worker.submit { backend.accept().use { remote ->
                remote.soTimeout=3000;val i=remote.getInputStream();val o=remote.getOutputStream()
                assertArrayEquals(byteArrayOf(5,1,0),read(i,3));o.write(byteArrayOf(5,0))
                assertArrayEquals(destination,read(i,destination.size));o.write(byteArrayOf(5,0,0,1,0,0,0,0,0,0))
                o.write(read(i,4));o.flush()
            } }
            try { AuthenticatedRelay(backend.localPort,user,password).use { relay ->
                relay.start(InetAddress.getLoopbackAddress(),0)
                Socket("127.0.0.1",relay.port).use { client ->
                    client.soTimeout=3000;val i=client.getInputStream();val o=client.getOutputStream()
                    o.write(byteArrayOf(5,1,2));assertArrayEquals(byteArrayOf(5,2),read(i,2))
                    o.write(byteArrayOf(1,user.length.toByte())+user.toByteArray()+byteArrayOf(password.length.toByte())+password.toByteArray())
                    assertArrayEquals(byteArrayOf(1,0),read(i,2));o.write(destination)
                    assertEquals(0,read(i,10)[1].toInt());o.write("PING".toByteArray());assertEquals("PING",String(read(i,4)))
                    relay.close();assertEquals(-1,i.read())
                }
            };task.get(3,TimeUnit.SECONDS) } finally { worker.shutdownNow() }
        }
    }
    @Test fun socksUdpCannotFallbackDirect() {
        AuthenticatedRelay(12345,user,password).use { relay -> relay.start(InetAddress.getLoopbackAddress(),0)
            Socket("127.0.0.1",relay.port).use { client -> client.soTimeout=2000
                val i=client.getInputStream();val o=client.getOutputStream()
                o.write(byteArrayOf(5,1,2));read(i,2)
                o.write(byteArrayOf(1,user.length.toByte())+user.toByteArray()+byteArrayOf(password.length.toByte())+password.toByteArray());read(i,2)
                o.write(byteArrayOf(5,3,0,1,0,0,0,0,0,0));assertEquals(-1,i.read())
            }
        }
    }
    @Test fun socksRejectsNoAuthMethod() {
        AuthenticatedRelay(12345,user,password).use { relay -> relay.start(InetAddress.getLoopbackAddress(),0)
            Socket("127.0.0.1",relay.port).use { s -> s.soTimeout=2000;s.getOutputStream().write(byteArrayOf(5,1,0));assertEquals(-1,s.getInputStream().read()) }
        }
    }
    @Test fun socksWrongPasswordCannotOpenUpstream() {
        AuthenticatedRelay(12345,user,password).use { relay -> relay.start(InetAddress.getLoopbackAddress(),0)
            Socket("127.0.0.1",relay.port).use { s -> s.soTimeout=2000;val o=s.getOutputStream();val i=s.getInputStream();o.write(byteArrayOf(5,1,2));assertArrayEquals(byteArrayOf(5,2),read(i,2));o.write(byteArrayOf(1,1,120,1,120));assertArrayEquals(byteArrayOf(1,1),read(i,2)) }
        }
    }
}

package net.gozar.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ImportAndUpdateRegressionTest {
    private val key=Base64.getEncoder().encodeToString(ByteArray(32){0xfb.toByte()})
    private fun config(extra: String="")=ProxyConfig("wg","wireguard","engage.cloudflareclient.com",51821,
        privateKey=key,publicKey=key,localAddress="10.23.0.2/32",extra=extra)
    @Test fun wireguardUriPreservesUnescapedPlusAndConfiguredWarpPort() {
        val c=ConfigParser.parse("wireguard://$key@engage.cloudflareclient.com:51821?publickey=$key&address=10.23.0.2%2F32")!!
        assertEquals(key,c.privateKey);assertEquals(key,c.publicKey)
        val o=WireGuardProfile.settings(c)
        assertEquals("engage.cloudflareclient.com:51821",o.getJSONArray("peers").getJSONObject(0).getString("endpoint"))
        assertTrue(o.getBoolean("noKernelTun"));assertEquals("ForceIPv4",o.getString("domainStrategy"))
    }
    @Test fun wireguardMultiplePeersDnsAllowedIpsAndKeepaliveReachGenerator() {
        val text="""[Interface]
PrivateKey = $key
Address = 10.23.0.2/32
DNS = 10.23.0.1
MTU = 1280
[Peer]
PublicKey = $key
Endpoint = [2001:db8::1]:51820
AllowedIPs = 10.0.0.0/8
PersistentKeepalive = 25
[Peer]
PublicKey = $key
PresharedKey = $key
Endpoint = vpn.example.invalid:51821
AllowedIPs = 192.168.0.0/16
"""
        val c=ConfigParser.parseWireguardConf(text)!!
        val root=JSONObject(ConfigBuilder.build(c));val o=root.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
        val peers=o.getJSONArray("peers");assertEquals(2,peers.length())
        assertEquals(25,peers.getJSONObject(0).getInt("keepAlive"));assertEquals("10.0.0.0/8",peers.getJSONObject(0).getJSONArray("allowedIPs").getString(0))
        assertEquals("192.168.0.0/16",peers.getJSONObject(1).getJSONArray("allowedIPs").getString(0));assertEquals(key,peers.getJSONObject(1).getString("preSharedKey"))
        assertEquals("10.23.0.1",root.getJSONObject("dns").getJSONArray("servers").getString(0));assertTrue(root.getJSONObject("dns").getBoolean("disableFallback"))
        assertEquals(1280,o.getInt("mtu"))
        assertEquals(root.getJSONObject("dns").toString(),JSONObject(ConfigBuilder.buildForTest(c)).getJSONObject("dns").toString())
    }
    @Test fun malformedWireguardCannotAppearReady() {
        for(c in listOf(config().copy(privateKey="short"),config().copy(publicKey="short"),config().copy(password="short"),config().copy(localAddress=""),config().copy(reserved="1,2,999")))
            assertTrue(runCatching {WireGuardProfile.settings(c)}.isFailure)
    }
    @Test fun storeFailuresNeverContainExceptionHostOrUrl() {
        val secrets=listOf("Unable to resolve host \"private.example\"", "خطای https://host.internal/api?token=secret", "TLS handshake failed at 192.0.2.8:443", "host: internal-only")
        for(text in secrets)for(e in listOf(Exception(text),java.net.UnknownHostException(text),java.net.SocketTimeoutException(text),javax.net.ssl.SSLException(text))){
            val shown=StorePublicError.message(e)
            assertFalse(shown.contains("private"));assertFalse(shown.contains("host"));assertFalse(shown.contains("192.0.2.8"));assertFalse(shown.contains("internal"));assertFalse(shown.contains("secret"))
        }
    }
    @Test fun releaseAbiSelectionNeverUsesWrongArchitecture() {
        val a=listOf(UpdateChecker.ReleaseAsset("Ghajar-arm64-v8a.apk","",1),UpdateChecker.ReleaseAsset("Ghajar-x86_64.apk","",1))
        assertNull(UpdateChecker.selectApk(a,listOf("armeabi-v7a")))
        assertEquals("Ghajar-x86_64.apk",UpdateChecker.selectApk(a,listOf("x86_64","x86"))!!.name)
    }
    @Test fun updateVersionsUrlsAndHashesAreValidated() {
        assertTrue(UpdateChecker.isNewer("1.10.0","1.9.9"));assertFalse(UpdateChecker.isNewer("1.1.1","1.1.1"))
        assertFalse(UpdateChecker.releaseUrl("https://github.com.evil.invalid/meysam82003/Ghajarvpn-/releases/a"))
        assertFalse(UpdateChecker.releaseUrl("https://github.com/other/repo/releases/a"))
        assertFalse(UpdateChecker.releaseUrl("http://github.com/meysam82003/Ghajarvpn-/releases/a"))
        assertNull(UpdateChecker.parseSha256Sums("invalid  app.apk","app.apk"))
        val hash="a".repeat(64);assertEquals(hash,UpdateChecker.parseSha256Sums("$hash  app.apk","app.apk"))
    }
}

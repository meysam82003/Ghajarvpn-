package net.gozar.app
import net.gozar.app.engine.FullXrayProfile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
class FinalImportTest {
 @Test fun fullXrayPreservesOutboundRoutingDnsAndUnknownFields() {
  val raw="""{"outbounds":[{"protocol":"freedom","tag":"out"}],"dns":{"servers":["localhost"]},"routing":{"domainStrategy":"AsIs"},"future":{"value":1}}"""
  val c=ProxyConfig(name="full",protocol="xray-full",address="",port=0,extra=JSONObject().put("rawConfig",raw).toString())
  assertNull(FullXrayProfile.blockReason(c))
  val result=JSONObject(FullXrayProfile.runtime(c));val original=JSONObject(raw)
  for(key in listOf("outbounds","dns","routing","future"))assertEquals(original.get(key).toString(),result.get(key).toString())
  assertEquals("tun",result.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))
  assertNotNull(FullXrayProfile.blockReason(c.copy(extra=JSONObject().put("rawConfig",JSONObject(raw).put("inbounds",org.json.JSONArray("[{\"protocol\":\"socks\"}]")).toString()).toString())))
 }
 @Test fun remoteBpfRejectsPrivateTargetsAndInvalidInterval() {
  for(host in listOf("127.0.0.1","192.168.1.1","10.0.0.1","169.254.169.254","100.64.0.1","::1","fc00::1"))assertFalse(BpfRefresh.publicAddress(InetAddress.getByName(host)))
  assertTrue(BpfRefresh.publicAddress(InetAddress.getByName("93.184.216.34")))
  val meta=JSONObject().put("profileType",2).put("autoUpdate",true).put("updateInterval",15)
  assertEquals(900000L,BpfRefresh.intervalMillis(meta));assertNull(BpfRefresh.intervalMillis(meta.put("updateInterval",14)))
  for(url in listOf("http://example.org/x","https://u:p@example.org/x","https://example.local/x","https://example.org:444/x"))assertTrue(runCatching {BpfRefresh.remote(JSONObject().put("remotePath",url))}.isFailure)
 }
}

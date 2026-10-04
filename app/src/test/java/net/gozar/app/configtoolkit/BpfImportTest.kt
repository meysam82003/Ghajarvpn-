package net.gozar.app.configtoolkit

import net.gozar.app.engine.SingBoxConfig
import net.gozar.app.engine.SingBoxFull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.GZIPOutputStream

/** Byte layout written exactly as sing-box's ProfileContent.Encode does. */
class BpfImportTest {

    private val config = JSONObject()
        .put("dns", JSONObject().put("servers", org.json.JSONArray().put(JSONObject().put("type", "https").put("tag", "doh").put("server", "1.1.1.1"))))
        .put("inbounds", org.json.JSONArray().put(JSONObject().put("type", "tun").put("tag", "tun-in")))
        .put("outbounds", org.json.JSONArray()
            .put(JSONObject().put("type", "selector").put("tag", "proxy").put("outbounds", org.json.JSONArray().put("a").put("b")))
            .put(JSONObject().put("type", "vless").put("tag", "a").put("server", "a.example.com").put("server_port", 443).put("uuid", "11111111-2222-3333-4444-555555555555"))
            .put(JSONObject().put("type", "hysteria2").put("tag", "b").put("server", "b.example.com").put("server_port", 8443).put("password", "p"))
            .put(JSONObject().put("type", "direct").put("tag", "direct")))
        .put("route", JSONObject().put("auto_detect_interface", true).put("final", "proxy")
            .put("rules", org.json.JSONArray().put(JSONObject().put("domain_suffix", org.json.JSONArray().put(".ir")).put("outbound", "direct"))))
        .put("experimental", JSONObject().put("clash_api", JSONObject().put("external_controller", "127.0.0.1:9090")))
        .toString()

    private fun uvarint(o: DataOutputStream, v: Long) { var x = v; while (x >= 0x80) { o.write(((x and 0x7f) or 0x80).toInt()); x = x ushr 7 }; o.write(x.toInt()) }
    private fun str(o: DataOutputStream, s: String) { val b = s.toByteArray(); uvarint(o, b.size.toLong()); o.write(b) }

    private fun bpf(name: String, type: Int, cfg: String, version: Int = 1, remote: String = "https://example.com/p.json"): ByteArray {
        val out = ByteArrayOutputStream(); out.write(3); out.write(version)
        val gz = GZIPOutputStream(out); val d = DataOutputStream(gz)
        str(d, name); d.writeInt(type); str(d, cfg)
        if (type != 0) str(d, remote)
        if (type == 2) { d.writeBoolean(true); if (version >= 1) d.writeInt(60); d.writeLong(1700000000L) }
        d.flush(); gz.finish()
        return out.toByteArray()
    }

    @Test fun localProfileImportsAsOneFullConfig() {
        val bytes = bpf("My SFA", 0, config)
        assertEquals(ConfigFormat.BPF, FormatDetector.detect(ConfigInput(bytes, "x.bin", "application/octet-stream")).format)
        val out = ImportRouter.decode(bytes, "x.bin")
        assertTrue(out.toString(), out is ImportRouter.Outcome.Imported)
        val c = (out as ImportRouter.Outcome.Imported).configs.single()
        assertEquals(SingBoxFull.PROTOCOL, c.protocol)
        assertEquals("My SFA", c.name)
        assertEquals("a.example.com", c.address)
        // Nothing dropped from the stored profile.
        assertEquals(JSONObject(config).toString(), SingBoxFull.configOf(c).toString())
    }

    @Test fun runnableKeepsRoutingButUsesTheAppInbound() {
        val c = SingBoxFull.profile("p", config)
        val run = JSONObject(SingBoxConfig.full(SingBoxConfig.spec(c)!!, 10808))
        val inbound = run.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("socks", inbound.getString("type")); assertEquals(10808, inbound.getInt("listen_port"))
        assertEquals(1, run.getJSONArray("inbounds").length())
        assertFalse(run.has("experimental"))
        assertFalse(run.getJSONObject("route").has("auto_detect_interface"))
        assertEquals("proxy", run.getJSONObject("route").getString("final"))
        assertEquals(4, run.getJSONArray("outbounds").length())
        assertEquals("https", run.getJSONObject("dns").getJSONArray("servers").getJSONObject(0).getString("type"))
    }

    @Test fun remoteVersion0And1Decode() {
        listOf(0, 1).forEach { v ->
            val c = SingBoxProfileFile.decode(bpf("R", 2, config, version = v))
            assertEquals(2, c.type); assertEquals("https://example.com/p.json", c.remotePath); assertTrue(c.autoUpdate)
            assertEquals(if (v >= 1) 60 else 0, c.autoUpdateInterval); assertEquals(1700000000L, c.lastUpdated)
        }
    }

    @Test fun errorsAreDistinct() {
        val good = bpf("x", 0, config)
        val badVersion = good.copyOf().also { it[1] = 9 }
        val r1 = ImportRouter.decode(badVersion, "x.bpf")
        assertTrue(r1.toString(), r1 is ImportRouter.Outcome.Invalid && r1.toString().contains("نسخه"))
        val truncated = good.copyOfRange(0, good.size / 2)
        val r2 = runCatching { SingBoxProfileFile.decode(truncated) }.exceptionOrNull()
        assertTrue(r2?.message.orEmpty().contains("خراب"))
        val noProxy = ImportRouter.decode(bpf("x", 0, "{\"outbounds\":[{\"type\":\"direct\",\"tag\":\"d\"}]}"), "x.bpf")
        assertTrue(noProxy.toString(), noProxy is ImportRouter.Outcome.Invalid)
    }
}

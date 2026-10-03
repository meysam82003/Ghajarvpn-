package net.gozar.app

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import net.gozar.app.engine.*
import net.gozar.plugin.api.MihomoFiles
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RemainingWorkTest {
    private fun profile(protocol: String, address: String="", port: Int=0, password: String="", oblivionJson: String="") = ProxyConfig(name="test",protocol=protocol,address=address,port=port,password=password,oblivionJson=oblivionJson)
    private fun options(vararg values: Pair<String,String>) = OblivionOptions(JSONObject().put("core","tor-over-aether").apply { values.forEach { put(it.first,it.second) } }.toString())
    private fun reject(block:()->Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun chainRejectsLeaksAndPortCollisionsWithoutMutatingPreferences() {
        options().validate()
        for (p in listOf("routeDirect" to "private", "allowLan" to "true", "bypassSelected" to "true", "routingMode" to "proxy", "socksPort" to "19150", "core" to "aether-over-tor")) reject { options(p).validate() }
        val c=profile(protocol="psiphon",oblivionJson=options().changed("torCountry","de"))
        assertEquals(c.oblivionJson,ProxyConfig.fromJson(c.toJson()).oblivionJson)
    }
    @Test fun chainAcceptsOnlyValidatedVanillaBridges() {
        options("torBridges" to "Bridge 1.2.3.4:443 0123456789abcdef0123456789abcdef01234567").validate()
        for(v in listOf("obfs4 1.2.3.4:443 abc", "1.2.3.4:443\nSocksPort 0.0.0.0:90", "999.2.3.4:80", "example.com:443")) reject { options("torBridges" to v).validate() }
    }
    @Test fun chainGeneratorHasNoDirectAndUsesTcpDns() {
        val c=profile(protocol="psiphon",oblivionJson=options().changed("torCountry","de"))
        val root=JSONObject(ConfigBuilder.build(c,false,false,false,emptySet()))
        val outbound=root.getJSONArray("outbounds")
        assertFalse((0 until outbound.length()).any { outbound.getJSONObject(it).getString("protocol")=="freedom" })
        assertEquals(19150,outbound.getJSONObject(0).getJSONObject("settings").getJSONArray("servers").getJSONObject(0).getInt("port"))
        assertEquals("tcp://1.1.1.1:53",root.getJSONObject("dns").getJSONArray("servers").getString(0))
        reject { ConfigBuilder.build(c,false,true,false,emptySet()) }
        reject { ConfigBuilder.build(c,false,false,false,emptySet(),youtubeDirect=true) }
    }
    private class Fake(val name:String,val events:MutableList<String>,@Volatile var live:Boolean=true,val onStart:()->Unit={}) : ChainSession.Step {
        override fun start() { events.add("start:$name"); onStart() }
        override fun ready():Boolean { events.add("ready:$name"); return live }
        override fun alive()=live
        override fun stop() { events.add("stop:$name"); live=false }
    }
    @Test fun chainStopsInReverseAndAcquiresBeforePartialStart() {
        val events=mutableListOf<String>(); val a=Fake("a",events);val b=Fake("b",events)
        val s=ChainSession(listOf(a,b));s.start();assertTrue(s.healthy());s.close();s.close();reject { s.start() }
        assertEquals(listOf("start:a","ready:a","start:b","ready:b","stop:b","stop:a"),events)
        events.clear();val partial=ChainSession(listOf(Fake("a",events),Fake("b",events,onStart={error("start failed")})))
        reject { partial.start() };assertEquals(listOf("stop:b","stop:a"),events.takeLast(2));assertFalse(partial.healthy())
    }
    @Test fun chainDeathInterruptsDependentStartup() {
        val events=java.util.Collections.synchronizedList(mutableListOf<String>()); val latch=CountDownLatch(1)
        val a=Fake("a",events);val b=Fake("b",events,onStart={latch.countDown();Thread.sleep(10000)})
        val session=ChainSession(listOf(a,b));val failure=java.util.concurrent.atomic.AtomicReference<Throwable>();val thread=Thread { runCatching { session.start() }.onFailure { failure.set(it) } }; thread.start()
        assertTrue(latch.await(2,TimeUnit.SECONDS));a.live=false;thread.join(2000)
        assertFalse(thread.isAlive);assertTrue(failure.get() is IllegalStateException);assertFalse(session.healthy());assertEquals(listOf("stop:b","stop:a"),events.takeLast(2))
    }
    @Test fun chainTimeoutClosesAcquiredResources() {
        val events=mutableListOf<String>();val s=ChainSession(listOf(Fake("a",events,onStart={Thread.sleep(10000)})),System.nanoTime()+100_000_000)
        val t=Thread { runCatching { s.start() } };t.start();t.join(2000);assertFalse(t.isAlive);assertEquals("stop:a",events.last());assertFalse(s.healthy())
    }
    @Test fun singboxSharingPinsDnsAndRouteAheadOfGeneralRules() {
        val spec=SingBoxConfig.spec(profile(protocol="anytls",address="server.example",port=443,password="test-only"))!!
        val root=JSONObject(SingBoxConfig.full(spec,1080,sharingPort=1081));val rules=root.getJSONObject("route").getJSONArray("rules")
        assertEquals("reject",rules.getJSONObject(0).getString("action"))
        assertEquals("share-dns",rules.getJSONObject(1).getString("server"))
        assertEquals("proxy",rules.getJSONObject(2).getString("outbound"))
        val servers=root.getJSONObject("dns").getJSONArray("servers");val remote=servers.getJSONObject(servers.length()-1)
        assertEquals("tcp",remote.getString("type"));assertEquals("proxy",remote.getString("detour"))
        assertEquals(1,JSONObject(SingBoxConfig.full(spec,1080)).getJSONArray("inbounds").length())
    }
    @Test fun sharingEligibilityReportsRootlessAndDirectLimits() {
        for(protocol in listOf("ikev2","openvpn","wireguard","plugin","tailscale")) assertNotNull(CapabilityRegistry.phoneSharingReason(profile(protocol=protocol)))
        for(protocol in listOf("anytls","psiphon","aether")) assertNull(CapabilityRegistry.phoneSharingReason(profile(protocol=protocol)))
        assertNotNull(CapabilityRegistry.phoneSharingReason(profile(protocol="aether",oblivionJson="{\"routeDirect\":\"private\"}")))
    }
    @Test fun mihomoFilesRoundTripAndHashTamper() {
        val settings=MihomoFiles.put(JSONObject(),"providers/nodes.yaml","proxies: []".toByteArray())
        assertEquals("proxies: []",String(MihomoFiles.decode(settings).getValue("providers/nodes.yaml")))
        val root=Files.createTempDirectory("ghajar-files").toFile()
        try { MihomoFiles.materialize(root,settings);assertEquals("proxies: []",root.resolve("providers/nodes.yaml").readText()) }
        finally { root.deleteRecursively() }
        settings.getJSONArray("files").getJSONObject(0).put("sha256","0".repeat(64));reject { MihomoFiles.decode(settings) }
    }
    @Test fun mihomoFilesRejectTraversalSymlinkAndMemoryAbuse() {
        for(path in listOf("../private","/sdcard/key","a/../../b","a\\b","config.yaml")) reject { MihomoFiles.put(JSONObject(),path,byteArrayOf(1)) }
        reject { MihomoFiles.put(JSONObject(),"big",ByteArray(32769)) }
        val root=Files.createTempDirectory("ghajar-files");val external=Files.createTempDirectory("ghajar-outside")
        try { Files.createSymbolicLink(root.resolve("certs"),external);reject { MihomoFiles.materialize(root.toFile(),MihomoFiles.put(JSONObject(),"certs/key",byteArrayOf(1))) };assertFalse(Files.exists(external.resolve("key"))) }
        finally { Files.deleteIfExists(root.resolve("certs"));root.toFile().deleteRecursively();external.toFile().deleteRecursively() }
    }
    @Test fun ownedChainMovesBytesAcrossBothLoopbackResourcesAndClosesOldGeneration() {
        // TCP fixtures exercise the actual session owner, not real Aether/Tor binaries.
        val transfers=java.util.concurrent.atomic.AtomicInteger()
        val echo=java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())
        val echoThread=kotlin.concurrent.thread(isDaemon=true) { while(!echo.isClosed) runCatching { echo.accept().use { peer -> val data=peer.getInputStream().read(); if(data>=0) { transfers.incrementAndGet(); peer.getOutputStream().write(data) } } } }
        class Relay(val upstream:()->Int):ChainSession.Step {
            lateinit var listener:java.net.ServerSocket
            lateinit var worker:Thread
            val port get()=listener.localPort
            override fun start() {
                listener=java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())
                worker=kotlin.concurrent.thread(isDaemon=true) { while(!listener.isClosed) runCatching { listener.accept().use { input -> input.soTimeout=1000; java.net.Socket("127.0.0.1",upstream()).use { output -> output.soTimeout=1000; val b=input.getInputStream().read(); if(b>=0) { output.getOutputStream().write(b); val reply=output.getInputStream().read(); if(reply>=0) input.getOutputStream().write(reply) } } } } }
            }
            override fun ready()=java.net.Socket("127.0.0.1",port).use { it.soTimeout=1000;it.getOutputStream().write(79);it.getInputStream().read()==79 }
            override fun alive()=::listener.isInitialized && !listener.isClosed && worker.isAlive
            override fun stop() { listener.close();worker.join(1500);check(!worker.isAlive) }
        }
        val a=Relay { echo.localPort }; val b=Relay { a.port };val session=ChainSession(listOf(a,b))
        try { session.start();assertTrue(session.healthy());assertTrue(b.ready());assertEquals(3,transfers.get());a.stop();assertFalse(session.healthy());session.close();reject { java.net.Socket("127.0.0.1",b.port).close() };assertEquals(3,transfers.get()) }
        finally { session.close();echo.close();echoThread.join(1500) }
    }

}

package net.gozar.app.configtoolkit

import net.gozar.app.ConfigParser
import net.gozar.app.ProxyConfig
import net.gozar.app.engine.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.GZIPOutputStream
import java.nio.file.Files
import java.nio.file.Paths

class BinaryProfileTest {
    private val full = """{"dns":{"servers":[]},"inbounds":[{"type":"tun","tag":"tun"}],"outbounds":[{"type":"selector","tag":"select","outbounds":["auto"]},{"type":"urltest","tag":"auto","outbounds":["p"]},{"type":"direct","tag":"p"}],"route":{"final":"select"},"unknown":{"kept":true}}"""
    private fun bpf(raw: String = full, type: Int = 0, version: Int = 1): ByteArray {
        val bytes = ByteArrayOutputStream(); bytes.write(3); bytes.write(version)
        DataOutputStream(GZIPOutputStream(bytes)).use { o ->
            fun str(v: String) { val b=v.toByteArray();var n=b.size;while(n>=128){o.writeByte((n and 127) or 128);n=n ushr 7};o.writeByte(n);o.write(b) }
            str("پروفایل 🌱");o.writeInt(type);str(raw)
            if(type!=0)str("https://example.invalid/profile")
            if(type==2 || version==0&&type!=0){o.writeBoolean(true);if(version>=1)o.writeInt(3600);o.writeLong(123)}
        };return bytes.toByteArray()
    }
    private fun failure(bytes: ByteArray, expected: BpfParser.Error) {
        val e=runCatching{BpfParser.parse(bytes)}.exceptionOrNull()
        assertTrue(e is BpfParser.Failure);assertEquals(expected,(e as BpfParser.Failure).code)
    }
    @Test fun localPreservesFullDocumentAndEngine() {
        val c=DecoderRegistry().decode(ConfigInput(bpf(),"unknown.bin","application/octet-stream")).profiles.single().toProxyConfig()
        assertEquals("پروفایل 🌱",c.name);assertTrue(FullSingBoxProfile.raw(c)==full)
        assertEquals(EngineId.SINGBOX,EngineRouting.engineFor(c));assertNotNull(FullSingBoxProfile.blockReason(c))
        assertTrue(runCatching{SingBoxConfig.spec(c)}.isFailure)
        assertTrue(FullSingBoxProfile.raw(ProxyConfig.fromJson(c.toJson()))==full)
    }
    @Test fun remoteAndAppleMetadataVersions() {
        for(v in 0..1)for(t in 0..2){val p=BpfParser.parse(bpf(type=t,version=v));assertEquals(t,p.type);assertEquals(v,p.version)
            if(t!=0)assertEquals("https://example.invalid/profile",p.remotePath)
            if(t==2){assertTrue(p.autoUpdate);assertEquals(if(v==1)3600 else 0,p.interval);assertEquals(123,p.lastUpdated)}
        }
    }
    @Test fun typedBpfFailures() {
        failure(byteArrayOf(2,1),BpfParser.Error.INVALID_HEADER)
        failure(byteArrayOf(3,9),BpfParser.Error.UNSUPPORTED_VERSION)
        failure(byteArrayOf(3,1,0),BpfParser.Error.CORRUPT_GZIP)
        failure(bpf().dropLast(4).toByteArray(),BpfParser.Error.CORRUPT_GZIP)
        failure(bpf(raw="{broken"),BpfParser.Error.INVALID_JSON)
        failure(bpf().also{it[it.size-8]=(it[it.size-8].toInt() xor 1).toByte()},BpfParser.Error.CORRUPT_GZIP)
    }
    @Test fun gzipBombAndJsonDepthAreBounded() {
        failure(bpf(raw=" ".repeat(BpfParser.MAX_PAYLOAD+1)),BpfParser.Error.SIZE_LIMIT)
        failure(bpf(raw="{\"x\":"+"[".repeat(65)+"0"+"]".repeat(65)+"}"),BpfParser.Error.INVALID_JSON)
    }
    @Test fun extendedJsonAndUnknownFieldsRemainRaw() {
        val text="""{/* comment */"outbounds":[{"type":"direct",},],"unknown":true,}"""
        val p=BpfParser.parse(bpf(raw=text));assertEquals(text,p.config)
        assertTrue(BoundedJson.objectValue(p.config).getBoolean("unknown"))
    }
    @Test fun supportedProxyFullRuntimeKeepsRoutingAndGroups() {
        val text="""{"outbounds":[{"type":"selector","tag":"pick","outbounds":["proxy"]},{"type":"socks","tag":"proxy","server":"127.0.0.1","server_port":20000}],"route":{"final":"pick"}}"""
        val c=FullSingBoxProfile.create(text);assertNull(FullSingBoxProfile.blockReason(c))
        val r=JSONObject(SingBoxConfig.full(SingBoxConfig.spec(c)!!,19001))
        assertEquals("pick",r.getJSONObject("route").getString("final"));assertEquals(2,r.getJSONArray("outbounds").length())
        assertEquals(19001,r.getJSONArray("inbounds").getJSONObject(0).getInt("listen_port"))
        assertFalse(CapabilityRegistry.supportsPhoneSharing(c))
    }
    @Test fun npvtContainerErrorsAreNotPassphrasePrompts() {
        for(raw in listOf("NPVT1\nabc,def","NPVT1\n"+"!".repeat(32)+","+"a".repeat(32)+","+"b".repeat(32))){assertTrue(NpvContainer.open(raw.toByteArray(),null) is NpvContainer.Result.Invalid)}
        assertTrue(NpvContainer.open("NPVTSUB1\nunknown".toByteArray(),null) is NpvContainer.Result.Invalid)
    }
    @Test fun npvIpv6LinksAndUninterpretedFieldsRemainIntact() {
        for(type in listOf(3,5,6)) {
            val profile=JSONObject().put("server","2001:db8::12").put("serverPort",443).put("configType",type)
                .put("password",if(type==5)"00000000-0000-4000-8000-000000000001" else "synthetic-password")
                .put("method",if(type==3)"aes-128-gcm" else "none").put("futureField","kept")
            val root=JSONObject().put("name","IPv6 fixture").put("v2rayProfile",profile)
            val decoded=NpvDecodedContainer("NPVT",1,"legacy",root)
            assertEquals("2001:db8::12",decoded.profiles().single().address.removePrefix("[").removeSuffix("]"))
            assertEquals("kept",decoded.json().getJSONObject("rawDecodedProfile").getJSONObject("v2rayProfile").getString("futureField"))
            assertFalse(decoded.toString().contains("synthetic-password"))
        }
    }
    @Test fun v1AppKeyMatchesPublishedMitVectorsAndAuthenticatesBothVariants() {
        // Published, synthetic salt/vector from MIT Pantegnos 09ae0699 npvs_test.go.
        val salt="843cc2c901ce91516c38cc6605b92e47".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val expected=listOf("a9c9058dca50d178b64e0318ad5362be8f8d4103dc83ce2bd0ccd7e404584e3d","438a04b521a5952156441a76bee5d8377a67257ff7e8291b220023614d7933d5")
        val keys=NpvWhitebox.appV1Kdks(salt)
        assertEquals(expected,keys.map { b -> b.joinToString("") { "%02x".format(it) } })
        for(key in keys) {
            val raw=v1Fixture(key,salt,false)
            val result=NpvContainer.open(raw,null) as NpvContainer.Result.Opened
            val config=result.decoded!!.profiles().single()
            assertEquals("xray-full",config.protocol)
            assertTrue(JSONObject(config.extra).getString("rawConfig").contains("routing"))
            val bad=raw.clone();bad[bad.size-65]=(bad[bad.size-65].toInt() xor 1).toByte()
            assertTrue(NpvContainer.open(bad,null) is NpvContainer.Result.Invalid)
        }
    }
    @Test fun v1PassphraseAndOpenContainerPreserveCompleteProfile() {
        val salt=ByteArray(16){it.toByte()}
        val spec=javax.crypto.spec.PBEKeySpec("synthetic-password".toCharArray(),salt,1000,256)
        val key=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val raw=v1Fixture(key,salt,true)
        assertTrue(NpvContainer.open(raw,null) is NpvContainer.Result.NeedsPassphrase)
        assertTrue(NpvContainer.open(raw,"wrong") is NpvContainer.Result.WrongPassphrase)
        val result=NpvContainer.open(raw,"synthetic-password") as NpvContainer.Result.Opened
        assertEquals("xray-full",result.decoded!!.profiles().single().protocol)
        val open="NPVO1\n"+result.decoded.rawDecodedProfile.toString()
        assertEquals("xray-full",(NpvContainer.open(open.toByteArray(),null) as NpvContainer.Result.Opened).decoded!!.profiles().single().protocol)
    }
    private fun v1Fixture(key: ByteArray,salt: ByteArray,pass: Boolean): ByteArray {
        // Independent JCA writer, not the decoder's own ChaCha implementation.
        fun encrypt(k: ByteArray,n:ByteArray,plain:ByteArray,aad:ByteArray): ByteArray {
            val c=javax.crypto.Cipher.getInstance("ChaCha20-Poly1305")
            c.init(javax.crypto.Cipher.ENCRYPT_MODE,javax.crypto.spec.SecretKeySpec(k,"ChaCha20"),javax.crypto.spec.IvParameterSpec(n))
            c.updateAAD(aad);return c.doFinal(plain)
        }
        val dek=ByteArray(32){(it+1).toByte()};val wrapNonce=ByteArray(12){it.toByte()};val nonce=ByteArray(12){(it+20).toByte()}
        val enc=java.util.Base64.getUrlEncoder().withoutPadding()
        val block=JSONObject().put("salt",enc.encodeToString(salt)).put("wrap",enc.encodeToString(wrapNonce+encrypt(key,wrapNonce,dek,salt)))
        if(pass)block.put("kdf","pbkdf2-hmac-sha256").put("iters",1000) else block.put("kdf","wbaes-ctr-sha256").put("keyId",1)
        val header=JSONObject().put("v",1).put(if(pass)"passphrase" else "appKey",block).put("policy",JSONObject().put("onlyMobileNetwork",true)).toString().toByteArray()
        val plain="""{"configs":[{"name":"synthetic","v2rayProfile":{"v2rayJson":{"outbounds":[{"protocol":"blackhole"}],"routing":{"rules":[]},"dns":{"servers":[]}}}}]}""".toByteArray()
        val body=encrypt(dek,nonce,plain,header)
        return "NPVS".toByteArray()+byteArrayOf(1)+java.nio.ByteBuffer.allocate(4).putInt(header.size).array()+header+nonce+java.nio.ByteBuffer.allocate(4).putInt(body.size).array()+body+ByteArray(64)
    }
    /** Private user fixtures are optional external inputs; never committed or printed. */
    @Test fun privateRegressionFixtures() {
        val dir=System.getenv("GHAJAR_PRIVATE_FIXTURES") ?: return
        val files=Files.list(Paths.get(dir)).use{it.toList()}.filter{it.toString().endsWith(".npvt")||it.toString().endsWith(".npvs")||it.toString().endsWith(".bpf")}
        assertEquals(4,files.size)
        for(file in files){val bytes=Files.readAllBytes(file)
            if(file.toString().endsWith(".bpf")){
                val p=BpfParser.parse(bytes);assertEquals(40,p.name.codePointCount(0,p.name.length)) // Unicode flags/symbols are UTF-16 surrogate pairs.
                val r=BoundedJson.objectValue(p.config);assertTrue(r.has("dns")&&r.has("route"));assertEquals(4,r.getJSONArray("outbounds").length())
            }else{
                val result=NpvContainer.open(bytes,null);assertTrue("Private container did not open: " + file.fileName.toString().substringAfterLast('.') + ": " + (result as? NpvContainer.Result.Invalid)?.why.orEmpty(),result is NpvContainer.Result.Opened)
                val d=(result as NpvContainer.Result.Opened).decoded;assertNotNull(d);assertEquals(1,d!!.profiles().size)
                if(file.toString().endsWith(".npvs")){
                    assertEquals(17,d.unknownFields.getJSONObject("records").length())
                    assertEquals(17,d.unknownFields.getJSONArray("recordHeaders").length())
                    assertEquals(44,d.unknownFields.getString("contentId").length)
                    val bad=bytes.copyOf();val header=java.nio.ByteBuffer.wrap(bad,5,4).int; val row=9+header+16+38; val rowLen=java.nio.ByteBuffer.wrap(bad,row+4,2).short.toInt() and 65535; val tag=row+6+rowLen-1;bad[tag]=(bad[tag].toInt() xor 1).toByte()
                    assertTrue("Every record tag must verify",NpvContainer.open(bad,null) is NpvContainer.Result.Invalid)
                }else{assertEquals("xray-full",d.profiles().single().protocol);assertNotNull(NpvPolicy.reason(d.profiles().single()))}
            }
        }
    }
}

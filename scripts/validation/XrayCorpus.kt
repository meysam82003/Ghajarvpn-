package net.gozar.app.validation

import net.gozar.app.*
import net.gozar.app.configtoolkit.ImportRouter
import org.json.*
import java.io.File
import java.util.Base64

/** Synthetic, non-production credentials. Run against the actual Ghajar model/parser/generator classes. */
fun main(args: Array<String>) {
    val root=File(args.single()).apply { mkdirs() }
    val records=JSONArray()
    val key=Base64.getEncoder().encodeToString(ByteArray(32) { (it+1).toByte() })
    val pub=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { (it+1).toByte() })
    val base=ProxyConfig("corpus", "vless", "127.0.0.1", 24443, uuid="00000000-0000-4000-8000-000000000001", sni="example.org")
    fun emit(name: String, config: ProxyConfig=base, make: (ProxyConfig)->String={ConfigBuilder.build(it)}) {
        val record=JSONObject().put("id",name).put("protocol",config.protocol).put("network",config.network)
        try {
            val restored=ProxyConfig.fromJson(config.toJson())
            check(restored==config) { "profile persistence changed" }
            val json=make(restored)
            File(root,"$name.json").writeText(json)
            record.put("generated",true)
        } catch(e: Exception) { record.put("generated",false).put("error",e.message.orEmpty()) }
        records.put(record)
    }
    for(protocol in listOf("vless","vmess","trojan","shadowsocks")) {
        val c=base.copy(protocol=protocol,password="synthetic-password",method="aes-128-gcm",encryption=if(protocol=="vmess") "auto" else "none")
        for(network in listOf("tcp","ws","grpc","httpupgrade","xhttp","splithttp","kcp","quic","http")) {
            for(security in listOf("none","tls")) emit("$protocol-$network-$security",c.copy(network=network,security=security,path="/corpus",serviceName="corpus",host="example.org"))
        }
    }
    for(network in listOf("tcp","grpc","xhttp")) emit("reality-$network",base.copy(network=network,security="reality",publicKey=pub,shortId="0123456789abcdef",flow=if(network=="tcp") "xtls-rprx-vision" else "",path="/corpus",serviceName="corpus",spiderX="/crawler?audit=1"))
    for(header in listOf("none","srtp","utp","wechat","dtls","wireguard","dns")) for(seed in listOf("", "synthetic-seed"))
        emit("mkcp-$header-${if(seed.isEmpty()) "original" else "seed"}",base.copy(network="kcp",headerType=header,path=seed))
    for(method in listOf("2022-blake3-aes-128-gcm","2022-blake3-aes-256-gcm","2022-blake3-chacha20-poly1305"))
        emit("ss-$method",base.copy(protocol="shadowsocks",method=method,password=if(method.contains("128")) Base64.getEncoder().encodeToString(ByteArray(16){1}) else key))
    emit("wireguard-v4",base.copy(protocol="wireguard",port=51820,privateKey=key,publicKey=key,localAddress="10.0.0.2/32",mtu=1280))
    emit("wireguard-v6-psk",base.copy(protocol="wireguard",address="2001:db8::1",port=51820,privateKey=key,publicKey=key,password=key,localAddress="10.0.0.2/32, fd00::2/128",mtu=1280))
    emit("mux") { ConfigBuilder.build(it,mux=true) }
    emit("dns") { ConfigBuilder.build(it,customDns="1.1.1.1") }
    emit("doh") { ConfigBuilder.build(it,encryptedDns=true) }
    emit("fakedns") { ConfigBuilder.build(it,fakeDns=true,sniffing=true) }
    emit("routing-geodata") { ConfigBuilder.build(it,splitRouting=true,adBlock=true,youtubeDirect=true) }
    emit("fragment",base.copy(security="tls")) { ConfigBuilder.build(it,fragment=true) }
    for(noise in listOf("light","standard","aggressive","quic")) emit("noise-$noise") { ConfigBuilder.build(it,noiseSpec=noise) }
    for(mask in listOf("sudoku","salamander","noise","xdns")) emit("mask-$mask",base.copy(network="kcp",maskType=mask,maskPassword="synthetic-mask",maskDomain="tunnel.example.org"))
    emit("hy2",base.copy(protocol="hysteria2",security="tls",password="synthetic-password"))
    emit("hy2-obfs",base.copy(protocol="hysteria2",security="tls",password="synthetic-password",hyObfs="salamander",hyObfsPassword="synthetic-obfs"))
    emit("socks",base.copy(protocol="socks",uuid="synthetic-user",password="synthetic-password"))
    emit("http-proxy",base.copy(protocol="http",uuid="synthetic-user",password="synthetic-password"))
    emit("sharing-routing") { ConfigBuilder.build(it,shareOnLan=true,splitRouting=true,encryptedDns=true,youtubeDirect=true) }
    emit("chain") { ConfigBuilder.build(it,chainBase=base.copy(id="chain",protocol="trojan",security="tls",password="synthetic-password")) }
    val links=listOf(base,base.copy(protocol="vmess",encryption="auto"),base.copy(protocol="trojan",security="tls",password="synthetic-password"),base.copy(protocol="shadowsocks",method="aes-128-gcm",password="synthetic-password"),base.copy(security="reality",publicKey=pub,shortId="0123",flow="xtls-rprx-vision"))
    for((index,c) in links.withIndex()) {
        val uri=ConfigShare.toLink(c)
        // Clipboard and QR scanner both deliver this text to the shared parser; camera/image processing is a separate device gate.
        ConfigParser.parseBundle(uri).forEach { emit("clipboard-qr-$index",it) }
    }
    ConfigParser.parseBundle(links.joinToString("\n") { ConfigShare.toLink(it) }).forEachIndexed { i,c -> emit("subscription-$i",c) }
    val imported=ImportRouter.decode(ConfigBuilder.build(base).toByteArray(),"corpus.json")
    check(imported is ImportRouter.Outcome.Imported)
    imported.configs.forEachIndexed { i,c -> emit("json-import-$i",c) }
    File(root,"manifest.json").writeText(JSONObject().put("synthetic",true).put("cases",records).toString(2))
    println("Corpus: ${records.length()} cases")
}

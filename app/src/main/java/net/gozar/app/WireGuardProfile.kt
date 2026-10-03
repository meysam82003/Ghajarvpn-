package net.gozar.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** WireGuard .conf is a multi-peer document; never reduce it to the last peer. */
object WireGuardProfile {
    fun parse(text: String): JSONObject {
        require(text.length <= 1024*1024) { "WireGuard configuration too large" }
        val root=JSONObject();val peers=JSONArray();root.put("peers",peers)
        var section: JSONObject? = null
        for(raw in text.lineSequence()) {
            val line=raw.substringBefore('#').trim(); if(line.isEmpty())continue
            if(line.startsWith('[')) {
                section=when(line.lowercase()) {
                    "[interface]" -> root
                    "[peer]" -> JSONObject().also { peers.put(it);require(peers.length()<=128) }
                    else -> throw IllegalArgumentException("Unsupported WireGuard section")
                };continue
            }
            val eq=line.indexOf('=');require(eq>0&&section!=null) { "Invalid WireGuard field" }
            val key=line.substring(0,eq).trim().lowercase();val value=line.substring(eq+1).trim()
            val old=section.optString(key)
            if(old.isNotEmpty() && key in setOf("address","dns","allowedips")) section.put(key,"$old,$value")
            else {require(!section.has(key)) { "Duplicate WireGuard field: $key" };section.put(key,value)}
        }
        require(peers.length()>0) { "WireGuard requires a peer" };return root
    }
    fun validKey(value: String): Boolean = value.matches(Regex("[0-9a-fA-F]{64}")) ||
        runCatching { Base64.getDecoder().decode(value.replace('-','+').replace('_','/')).size == 32 }.getOrDefault(false)
    private fun list(s: String)=s.split(',').map(String::trim).filter(String::isNotEmpty)
    private fun cidr(v: String): Boolean {
        val ip=v.substringBefore('/');val bits=v.substringAfter('/',"").toIntOrNull()
        val ipv6=ip.contains(':')
        val address=if(ipv6) ip.count{it==':'}>=2&&ip.all{it in "0123456789abcdefABCDEF:."}
            else ip.split('.').let{it.size==4&&it.all{p->p.toIntOrNull() in 0..255}}
        return address && (bits==null&&!v.contains('/') || bits!=null&&bits in 0..if(ipv6)128 else 32)
    }
    fun settings(c: ProxyConfig): JSONObject {
        require(validKey(c.privateKey)) { "WireGuard private key must be 32 bytes" }
        val raw=runCatching{JSONObject(c.extra).optString("wireguardOriginal")}.getOrDefault("")
        val doc=if(raw.isNotBlank())parse(raw) else null
        if(doc!=null) {
            val allowed=setOf("privatekey","address","dns","mtu","reserved","name","peers")
            require(doc.keys().asSequence().all{it in allowed}) { "Unsupported WireGuard interface option; original retained" }
        }
        val address=list(c.localAddress);require(address.isNotEmpty()&&address.all(::cidr)) { "WireGuard requires valid local address(es)" }
        val peers=doc?.optJSONArray("peers") ?: JSONArray().put(JSONObject().put("publickey",c.publicKey)
            .put("presharedkey",c.password).put("endpoint","${if(c.address.contains(':')) "[${c.address}]" else c.address}:${c.port}"))
        val normalized=JSONArray()
        for(i in 0 until peers.length()) {
            val p=peers.getJSONObject(i)
            require(p.keys().asSequence().all{it in setOf("publickey","presharedkey","endpoint","allowedips","persistentkeepalive")}) { "Unsupported WireGuard peer option; original retained" }
            require(validKey(p.optString("publickey"))) { "WireGuard public key must be 32 bytes" }
            val psk=p.optString("presharedkey");require(psk.isBlank()||validKey(psk)) { "WireGuard preshared key must be 32 bytes" }
            val ep=p.optString("endpoint");val port=ep.substringAfterLast(':').toIntOrNull()
            require(port!=null&&port in 1..65535&&ep.substringBeforeLast(':').isNotBlank()) { "Invalid WireGuard endpoint" }
            val allowed=list(p.optString("allowedips","0.0.0.0/0,::/0"));require(allowed.isNotEmpty()&&allowed.all(::cidr)) { "Invalid WireGuard AllowedIPs" }
            val out=JSONObject().put("publicKey",p.getString("publickey")).put("endpoint",ep).put("allowedIPs",JSONArray(allowed))
            if(psk.isNotBlank())out.put("preSharedKey",psk)
            if(p.has("persistentkeepalive")){val k=p.getString("persistentkeepalive").toIntOrNull();require(k!=null&&k in 0..65535);out.put("keepAlive",k)}
            normalized.put(out)
        }
        val result=JSONObject().put("secretKey",c.privateKey).put("address",JSONArray(address)).put("peers",normalized)
            .put("noKernelTun",true).put("domainStrategy",when{address.all{!it.contains(':')}->"ForceIPv4";address.all{it.contains(':')}->"ForceIPv6";else->"ForceIP"})
        if(c.mtu!=0){require(c.mtu in 576..65535){"Invalid WireGuard MTU"};result.put("mtu",c.mtu)}
        if(c.reserved.isNotBlank()){val a=list(c.reserved);require(a.size==3&&a.all{it.toIntOrNull() in 0..255}){"WireGuard reserved must be three bytes"};result.put("reserved",JSONArray(a.map{it.toInt()}))}
        return result
    }
    fun applyDns(c: ProxyConfig, root: JSONObject) {
        if(c.protocol!="wireguard")return
        val raw=runCatching{JSONObject(c.extra).optString("wireguardOriginal")}.getOrDefault("")
        if(raw.isBlank())return
        val dns=list(parse(raw).optString("dns"));if(dns.isEmpty())return
        require(dns.all{cidr(it)&&!it.contains('/')}) { "WireGuard DNS search domains require explicit host support; original retained" }
        root.put("dns",JSONObject().put("servers",JSONArray(dns)).put("disableFallback",true))
    }
}

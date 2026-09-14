package net.gozar.app

import org.json.JSONObject

/** Android-applicable settings and CLI arguments from the supplied Oblivion source. */
class OblivionOptions(raw: String = "") {
    private val data = if (raw.isBlank()) JSONObject() else JSONObject(raw)
    fun text(key: String): String = data.optString(key, defaults[key].orEmpty())
    fun flag(key: String): Boolean = text(key).toBoolean()
    fun number(key: String): Int = text(key).toIntOrNull() ?: defaults[key]?.toIntOrNull() ?: 0
    fun changed(key: String, value: String): String = JSONObject(data.toString()).put(key,value).toString()
    val core get() = text("core")
    val aether get() = core == "aether" || core == "chain"
    val psiphon get() = core != "aether"
    val proxyOnly get() = text("routingMode") == "proxy"
    val socksPort get() = number("socksPort")
    val aetherPort get() = if (core == "chain") socksPort + 10 else socksPort
    val bindHost get() = if (flag("allowLan") && core != "chain") "0.0.0.0" else "127.0.0.1"
    fun validate() {
        require(core in listOf("psiphon","aether","chain")) { "روش اتصال نامعتبر است" }
        require(socksPort in 1024..65524 && socksPort != MixedPort.value && socksPort+1 != MixedPort.value) { "پورت پروکسی نامعتبر یا اشغال است" }
        require(number("mtu") in 1280..9000) { "MTU باید بین ۱۲۸۰ تا ۹۰۰۰ باشد" }
        require(number("validateSeconds") in 1..120 && number("reconnectSeconds") in 1..120) { "زمان انتظار باید بین ۱ تا ۱۲۰ ثانیه باشد" }
        require(number("wgKeepalive") in 0..120) { "Keepalive نامعتبر است" }
        for(key in listOf("fragmentSize","fragmentDelay")) {
            val r=text(key).split('-').map { it.toIntOrNull() ?: 0 }
            require(r.size in 1..2 && r.all { it in 1..65535 } && r.last()>=r.first()) { "بازهٔ فرگمنت نامعتبر است" }
        }
        require(text("wiwOuter").isBlank() || text("wiwOuter")!=text("wiwInner")) { "دو سرور gool باید متفاوت باشند" }
    }
    fun aetherArgs(): List<String> {
        val out=mutableListOf("--bind","$bindHost:$aetherPort","--http-proxy","$bindHost:${aetherPort+1}",
            "--scan",text("scanMode"),"--noize",text("obfuscation"),"--log-level",text("logLevel"),
            "--ip",text("ipVersion"),"--validate-secs",text("validateSeconds"),"--reconnect-secs",text("reconnectSeconds"))
        val protocol=text("protocol")
        out += when(protocol) {"wg"->"--wg";"gool"->"--gool";else->"--masque"}
        fun option(key:String,arg:String){if(text(key).isNotBlank())out.addAll(listOf(arg,text(key).trim()))}
        if(protocol=="masque" && text("transport")=="h2") {
            out+="--h2";option("h2Endpoint","--h2-peer")
            if(flag("fragment")){out+="--fragment";option("fragmentSize","--fragment-size");option("fragmentDelay","--fragment-delay")}
        }
        if(protocol=="masque")option("echMode","--ech")
        if(protocol in listOf("wg","gool")) {
            option("wgKeepalive","--keepalive");if(!flag("wgProfileRetry"))out+="--no-profile-retry"
            if(protocol=="wg")option("wgEndpoint","--wg-peer")
        }
        if(protocol=="gool") {
            option("wiwOuter","--wiw-outer");option("wiwInner","--wiw-inner")
            if(text("wiwOuter").isBlank()&&text("wiwInner").isBlank())out+="--wiw-scan"
        } else option("endpoint","--peer")
        if(flag("overrideDns")) {
            val dns=listOf(text("dnsPrimary"),text("dnsSecondary")).filter { it.isNotBlank() }
            if(dns.isNotEmpty())out+=listOf("--dns",dns.joinToString(","))
        }
        option("tlsGroups","--tls-groups");option("perfProfile","--perf")
        if(!flag("dataCheck"))out+="--no-data-check"
        out+=if(flag("quickReconnect"))"--quick-reconnect" else "--no-quick-reconnect"
        for((key,arg) in listOf("routeBlock" to "--route-block","routeDirect" to "--route-direct")) {
            val rules=text(key).split(Regex("[\\s,;]+")).filter { it.isNotBlank() };if(rules.isNotEmpty())out+=listOf(arg,rules.joinToString(","))
        }
        if(text("team").isNotBlank()) {
            option("team","--team")
            when {
                text("accessToken").isNotBlank()->option("accessToken","--access-token")
                text("accessId").isNotBlank() && text("accessSecret").isNotBlank()->{option("accessId","--access-id");option("accessSecret","--access-secret")}
                else->option("accessEmail","--access-email")
            }
            if(flag("gatewayProxy"))out+="--gateway"
        }
        return out
    }
    companion object {
        val defaults=mapOf("core" to "psiphon","protocol" to "masque","transport" to "h3","scanMode" to "balanced",
            "obfuscation" to "balanced","ipVersion" to "v4","logLevel" to "info","perfProfile" to "","echMode" to "",
            "socksPort" to "1819","allowLan" to "false","routingMode" to "vpn","mtu" to "1500","overrideDns" to "true",
            "dnsPrimary" to "1.1.1.1","dnsSecondary" to "1.0.0.1","fragment" to "false","fragmentSize" to "16-32",
            "fragmentDelay" to "2-10","quickReconnect" to "true","dataCheck" to "true","validateSeconds" to "10",
            "reconnectSeconds" to "2","wgKeepalive" to "5","wgProfileRetry" to "true","gatewayProxy" to "false","bypassSelected" to "false")
    }
}

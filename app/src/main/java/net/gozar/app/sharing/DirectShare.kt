package net.gozar.app.sharing

import net.gozar.app.*
import net.gozar.app.plugins.PluginProfiles
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object DirectShare {
    enum class Device(val label: String) { IOS("iPhone / iPad"), ANDROID("Android"), WINDOWS("Windows"), MAC("macOS"), LINUX("Linux"), OTHER("Other") }
    data class Export(val title: String, val filename: String, val mime: String, val text: String, val qr: Boolean = false, val note: String = "")
    private val uriProtocols=setOf("vless","vmess","trojan","shadowsocks","hysteria","hysteria2","tuic","anytls","juicity","mieru","naive","shadowtls","brook")
    fun exports(c: ProxyConfig, device: Device, all: List<ProxyConfig> = listOf(c)): List<Export> {
        require(!c.locked) { "این پروفایل اجازهٔ خروجی متنی ندارد." }
        val result=mutableListOf<Export>()
        if(c.chainId.isNotBlank() || c.torBaseId.isNotBlank()) {
            return if(device in setOf(Device.ANDROID,Device.OTHER)) listOf(Export("بستهٔ اتصال و وابستگی‌ها", "connection.ghajar.json", "application/json", packageOf(c,all), note="این اتصال زنجیره دارد؛ خروجی تک‌سرور معادل آن نیست. بسته را در Ghajar وارد کنید.")) else emptyList()
        }
        if(c.protocol in uriProtocols && c.chainId.isBlank() && c.torBaseId.isBlank()) {
            ConfigShare.toLink(c).takeIf { it.isNotBlank() }?.let {
                result += Export("لینک برای برنامهٔ سازگار", "connection.txt", "text/plain", it, true,
                    "برنامهٔ مقصد باید ${c.protocol} و تنظیمات همین اتصال را پشتیبانی کند؛ گزینه‌های اختصاصی مانند ECH و mask ممکن است در آن شناخته نشوند.")
            }
        }
        if(c.protocol=="wireguard" && c.chainId.isBlank()) result += Export("WireGuard configuration", "ghajar.conf", "text/plain", wireguard(c), true,
            "در WireGuard از Import from file یا Scan QR استفاده کنید. یک کلید را هم‌زمان روی دو دستگاه به کار نبرید؛ از مدیر سرور کلید جدا بگیرید.")
        if(c.protocol=="amneziawg") c.extraJson().optString("conf").takeIf { it.isNotBlank() }?.let {
            result += Export("AmneziaWG configuration", "ghajar-awg.conf", "text/plain", it, false, "به کلاینت AmneziaWG سازگار با نسخهٔ این کانفیگ نیاز دارد؛ WireGuard عادی کافی نیست.")
        }
        if(c.protocol=="ikev2") {
            if(device in setOf(Device.IOS,Device.MAC) && c.sni.isNotBlank() && c.uuid.isNotBlank() && c.port==500)
                result += Export("پروفایل IKEv2 بدون رمز", "ghajar.mobileconfig", "application/x-apple-aspen-config", ikev2(c), note="پروفایل امضانشده است؛ قبل از نصب نام سرور و Remote ID را بررسی کنید. رمز در فایل نیست. فقط ورود EAP نام کاربری/رمز؛ CA اختصاصی یا گواهی دستگاه باید جدا و از مدیر معتبر نصب شود.")
            result += Export("راهنمای تنظیم دستی IKEv2", "ikev2-setup.txt", "text/plain",
                "Type: IKEv2\nServer: ${single(c.address)}\nRemote ID: ${single(c.sni)}\nUsername: ${single(c.uuid)}\nAuthentication: EAP username/password\nPassword: enter separately\n",
                note="Remote ID و گواهی سرور را با مدیر سرویس بررسی کنید. رمز را روی دستگاه مقصد جدا وارد کنید.")
        }
        if(c.protocol=="openconnect") result += Export("اطلاعات OpenConnect", "openconnect-setup.txt", "text/plain",
            "Server: https://${single(c.address)}:${c.port}\nProtocol: ${single(c.mode)}\nUsername: ${single(c.uuid)}\nAuth group: ${single(c.extraJson().optString("authGroup"))}\n",
            note="در کلاینت سازگار با نوع سرور وارد کنید. رمز، token و کلید خصوصی جدا لازم‌اند. در iOS فقط اگر کلاینت سازمانی همان نوع سرور را پشتیبانی کند؛ OpenConnect یک پروتکل بومی iOS نیست.")
        if(PluginProfiles.isPlugin(c)) {
            val p=requireNotNull(PluginProfiles.read(c))
            if(p.id=="mihomo") result += Export("کانفیگ کامل Mihomo", if(p.format.endsWith("json")) "mihomo.json" else "mihomo.yaml", "text/plain", p.payload,
                note="کل فایل، providers، rules و DNS حفظ شده‌اند. کلاینت full-config سازگار لازم است؛ فایل‌های خارجی و مسیرهای محلی باید روی مقصد فراهم شوند.")
            if(p.id=="shadowquic" && device in setOf(Device.ANDROID,Device.OTHER)) result += Export("کانفیگ ShadowQUIC برای Ghajar", "shadowquic.ghajar.json", "application/json", ConfigShare.toLink(c),
                note="این مدل مخصوص importer افزونهٔ Ghajar است؛ به‌عنوان قالب عمومی iOS یا سرور upstream معرفی نمی‌شود.")
        }
        if(device in setOf(Device.ANDROID,Device.OTHER)) result += Export("بستهٔ کامل Ghajar", "connection.ghajar.json", "application/json", packageOf(c,all), note="در Ghajar: کانفیگ دارم ← واردکردن فایل. تنظیمات اختصاصی و نیازمندی افزونه حفظ می‌شوند. تنظیمات سراسری گوشی منتقل نمی‌شوند.")
        return result
    }
    private fun single(v: String): String { require(v.none { it=='\r'||it=='\n'||it=='\u0000' }); return v }
    fun wireguard(c: ProxyConfig): String {
        c.extraJson().optString("wireguardOriginal").takeIf { it.isNotBlank() }?.let { original ->
            val parsed=ConfigParser.parseWireguardConf(original) ?: error("فایل اصلی WireGuard نامعتبر است.")
            require(listOf(c.address,c.port,c.privateKey,c.publicKey,c.password,c.localAddress,c.mtu,c.reserved)==
                listOf(parsed.address,parsed.port,parsed.privateKey,parsed.publicKey,parsed.password,parsed.localAddress,parsed.mtu,parsed.reserved)) {
                "این پروفایل پس از import ویرایش شده؛ فایل اصلی دیگر معادل آن نیست. بستهٔ کامل Ghajar را استفاده کنید یا فایل جدید از مدیر سرور بگیرید."
            }
            require(!Regex("(?im)^\\s*(PreUp|PostUp|PreDown|PostDown)\\s*=").containsMatchIn(original)) { "فایل WireGuard دستور اجرایی دارد؛ خروجی خودکار استاندارد مجاز نیست." }
            return original
        }
        require(c.reserved.isBlank()) { "این اتصال reserved اختصاصی دارد و فایل WireGuard استاندارد معادل آن نیست." }
        fun key(s: String) { require(runCatching { java.util.Base64.getDecoder().decode(s).size==32 }.getOrDefault(false)) { "کلید WireGuard معتبر نیست." } }
        key(c.privateKey);key(c.publicKey);if(c.password.isNotBlank()) key(c.password)
        require(c.localAddress.isNotBlank() && c.port in 1..65535)
        val host=single(c.address).let { if(it.contains(':')) "[$it]" else it }
        return buildString {
            append("[Interface]\nPrivateKey = ${c.privateKey}\nAddress = ${single(c.localAddress)}\n")
            if(c.mtu>0) append("MTU = ${c.mtu}\n")
            val dns=c.extraJson().optString("dns");if(dns.isNotBlank()) append("DNS = ${single(dns)}\n")
            append("\n[Peer]\nPublicKey = ${c.publicKey}\n")
            if(c.password.isNotBlank()) append("PresharedKey = ${c.password}\n")
            append("Endpoint = $host:${c.port}\nAllowedIPs = ${single(c.extraJson().optString("allowed", "0.0.0.0/0, ::/0"))}\n")
        }
    }
    fun packageOf(c: ProxyConfig, all: List<ProxyConfig>): String {
        val seen=linkedMapOf<String,ProxyConfig>()
        fun visit(p: ProxyConfig, stack: Set<String>) {
            require(!p.locked && p.id !in stack && seen.size<100) { "وابستگی قفل‌شده، حلقوی یا بیش‌ازحد است." }
            if(p.id in seen) return
            for(id in listOf(p.chainId,p.torBaseId).filter { it.isNotBlank() }) visit(all.firstOrNull { it.id==id } ?: error("کانفیگ وابسته در دسترس نیست."),stack+p.id)
            seen[p.id]=p
        }
        visit(c,emptySet())
        val a=JSONArray();seen.values.forEach { p -> a.put(p.toJson().apply { put("subId","");put("source",ConfigSource.PERSONAL.name);put("favorite",false) }) }
        val plugin=if(PluginProfiles.isPlugin(c)) requireNotNull(PluginProfiles.read(c)).id else ""
        return JSONObject().put("ghajarShare",true).put("schemaVersion",1).put("protocol",c.protocol)
            .put("server",c.address).put("port",c.port).put("rootId",c.id)
            .put("authentication",JSONObject().put("location","profiles")).put("transport",c.network)
            .put("TLS",c.security).put("DNS",JSONObject().put("scope","profile-only"))
            .put("routing",JSONObject().put("scope","profile-only")).put("pluginRequirement",plugin)
            .put("coreRequirement",if(plugin.isNotEmpty()) "plugin:$plugin" else net.gozar.app.engine.EngineRouting.engineFor(c).name).put("profiles",a).toString(2).also { require(it.toByteArray().size <= 16*1024*1024) { "بسته بزرگ‌تر از حد مجاز است." } }
    }
    fun importPackage(text: String, source: ConfigSource): List<ProxyConfig>? {
        if(!text.trimStart().startsWith("{")) return null
        val o=runCatching { JSONObject(text) }.getOrNull() ?: return null
        if(!o.has("ghajarShare")) return null
        require(text.toByteArray().size<=16*1024*1024 && o.getBoolean("ghajarShare") && o.get("schemaVersion")==1) { "نسخه یا اندازهٔ بستهٔ Ghajar پشتیبانی نمی‌شود." }
        val a=o.getJSONArray("profiles"); require(a.length() in 1..100)
        val profiles=(0 until a.length()).map { ProxyConfig.fromJson(a.getJSONObject(it)) }
        val ids=profiles.associate { it.id to UUID.randomUUID().toString() }; require(ids.size==profiles.size && o.getString("rootId") in ids)
        return profiles.map { p ->
            require(p.protocol.isNotBlank() && (p.chainId.isBlank() || p.chainId in ids) && (p.torBaseId.isBlank() || p.torBaseId in ids))
            if(PluginProfiles.isPlugin(p)) PluginProfiles.read(p)
            EngineSettings.validate(p)
            p.copy(id=ids.getValue(p.id),subId="",source=source,locked=false,chainId=ids[p.chainId].orEmpty(),torBaseId=ids[p.torBaseId].orEmpty())
        }.also { imported -> imported.forEach { packageOf(it,imported) } } // Reject cyclic dependencies before storing anything.
    }
    private fun xml(s:String)=single(s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    fun ikev2(c:ProxyConfig): String {
        require(c.protocol=="ikev2" && c.sni.isNotBlank() && c.uuid.isNotBlank() && c.port==500)
        val id=UUID.randomUUID().toString()
        fun pair(k:String,v:String)="<key>$k</key><string>${xml(v)}</string>"
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>${pair("PayloadType","Configuration")}${pair("PayloadIdentifier","net.ghajar.share.$id")}${pair("PayloadUUID",id)}<key>PayloadVersion</key><integer>1</integer>${pair("PayloadDisplayName",c.name)}<key>PayloadContent</key><array><dict>${pair("PayloadType","com.apple.vpn.managed")}${pair("PayloadIdentifier","net.ghajar.share.$id.vpn")}${pair("PayloadUUID",UUID.randomUUID().toString())}<key>PayloadVersion</key><integer>1</integer>${pair("UserDefinedName",c.name)}${pair("VPNType","IKEv2")}<key>IKEv2</key><dict>${pair("RemoteAddress",c.address)}${pair("RemoteIdentifier",c.sni)}${pair("AuthenticationMethod","None")}<key>ExtendedAuthEnabled</key><integer>1</integer>${pair("AuthName",c.uuid)}</dict></dict></array></dict></plist>"""
    }
}

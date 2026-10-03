package net.gozar.app.engine

import net.gozar.app.ProxyConfig
import net.gozar.app.configtoolkit.BoundedJson
import org.json.JSONArray
import org.json.JSONObject

/** Full Xray through the existing XRAY_TUN_FD contract; no outbound/routing conversion. */
object FullXrayProfile {
    fun root(c: ProxyConfig): JSONObject { require(c.protocol=="xray-full"); return BoundedJson.objectValue(JSONObject(c.extra).getString("rawConfig")) }
    fun blockReason(c: ProxyConfig): String? = try {
        val r=root(c); val ins=r.optJSONArray("inbounds") ?: JSONArray()
        when {
            (r.optJSONArray("outbounds")?.length() ?: 0)==0 -> "Full Xray فاقد outbound است."
            ins.length()>1 -> "Full Xray چند inbound به مالکیت منابع مستقل نیاز دارد."
            ins.length()==1 && ins.getJSONObject(0).optString("protocol")!="tun" -> "برای حفظ قواعد inbound، این Full Xray باید inbound از نوع TUN داشته باشد."
            listOf("api","reverse","observatory","burstObservatory").any { r.has(it) } -> "سرویس جانبی Full Xray به قرارداد مستقل نیاز دارد."
            unsafe(r) -> "وابستگی فایل، socket یا رابط شنود خارجی پشتیبانی نمی‌شود."
            else -> null
        }
    } catch (_: Exception) { "Full Xray نامعتبر است." }
    private fun unsafe(value: Any?): Boolean = when(value) {
        is JSONObject -> value.keys().asSequence().any { k ->
            (k in setOf("certificateFile","keyFile","masterKeyLog","sockoptFile","domainSocket","acceptProxyProtocol") && value.opt(k)?.toString()?.isNotBlank()==true) ||
            (k in setOf("access","error") && value.opt(k)?.toString() !in setOf(null,"","none")) || unsafe(value.opt(k)) }
        is JSONArray -> (0 until value.length()).any { unsafe(value.opt(it)) }
        else -> false
    }
    fun runtime(c: ProxyConfig): String {
        require(blockReason(c)==null) { blockReason(c).orEmpty() }
        val r=root(c)
        if((r.optJSONArray("inbounds")?.length() ?: 0)==0) r.put("inbounds",JSONArray().put(JSONObject().put("tag","tun-in").put("protocol","tun").put("port",0).put("settings",JSONObject().put("name","xray0").put("MTU",1500))))
        return r.toString()
    }
}

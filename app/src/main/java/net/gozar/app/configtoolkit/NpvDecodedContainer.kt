package net.gozar.app.configtoolkit

import net.gozar.app.ProxyConfig
import org.json.JSONArray
import org.json.JSONObject

/** Lossless intermediate representation; never logged or exported as public share text. */
data class NpvDecodedContainer(
    val sourceFormat: String, val sourceVersion: Int, val containerGeneration: String,
    val rawDecodedProfile: JSONObject, val metadata: JSONObject = JSONObject(),
    val creator: String = "", val signature: String = "", val unknownFields: JSONObject = JSONObject()
) {
    override fun toString() = "NpvDecodedContainer(redacted)"
    fun json() = JSONObject().put("sourceFormat",sourceFormat).put("sourceVersion",sourceVersion)
        .put("containerGeneration",containerGeneration).put("rawDecodedProfile",rawDecodedProfile)
        .put("metadata",metadata).put("creator",creator).put("signature",signature)
        .put("authenticity","unverified").put("unknownFields",unknownFields)

    fun profiles(): List<ProxyConfig> {
        val configs = rawDecodedProfile.optJSONArray("configs") ?: JSONArray().put(rawDecodedProfile)
        require(configs.length() in 1..1024) { "Profile count limit" }
        return (0 until configs.length()).map { index ->
            val p = configs.getJSONObject(index)
            val profile = p.optJSONObject("v2rayProfile") ?: p
            val raw = profile.toString().takeIf { profile.has("outbounds") || profile.has("inbounds") }
                ?: sequenceOf("v2rayJson", "persistJson").mapNotNull { key -> when(val v=profile.opt(key)) {
                is JSONObject -> v.toString()
                is String -> v.takeIf { it.trimStart().startsWith('{') }
                else -> null
            }}.firstOrNull()
            val name = p.optString("name").ifBlank { profile.optString("remarks", "NPV") }
            val c = if (raw != null) {
                val root = BoundedJson.objectValue(raw)
                if (net.gozar.app.ForeignImport.looksLikeSingBox(root)) net.gozar.app.engine.FullSingBoxProfile.create(raw,name)
                else ProxyConfig(name=name, protocol="xray-full", address="",port=0,
                    extra=JSONObject().put("rawConfig",raw).put("fullConfigVersion",1).toString())
            } else {
                val link = NpvContainer.link(p)
                net.gozar.app.ConfigParser.parseBundle(link).singleOrNull()
                    ?: ProxyConfig(name=name,protocol="npv-preserved",address="",port=0)
            }
            val extra = runCatching { JSONObject(c.extra) }.getOrDefault(JSONObject())
            extra.put("npvContainer",json()).put("npvProfileIndex",index)
            c.copy(extra=extra.toString())
        }
    }
}

/** No import may strip a creator restriction to obtain a successful connection. */
object NpvPolicy {
    fun reason(c: ProxyConfig, now: Long = System.currentTimeMillis()): String? {
        val extra = runCatching { JSONObject(c.extra) }.getOrNull()
        val container = extra?.optJSONObject("npvContainer")
        if (container != null) {
            if(container.optJSONObject("rawDecodedProfile")?.toString()?.contains("npvs1:")==true)
                return "Unknown NPVS field encoding: محتوای اصلی حفظ شده؛ تبدیل معتبر این فیلد هنوز پشتیبانی نمی‌شود."
            val policies = mutableListOf<JSONObject>()
            container.optJSONObject("rawDecodedProfile")?.optJSONObject("lockConfig")?.let(policies::add)
            container.optJSONObject("rawDecodedProfile")?.optJSONObject("policy")?.let(policies::add)
            container.optJSONObject("metadata")?.optJSONObject("policy")?.let(policies::add)
            container.optJSONObject("rawDecodedProfile")?.optJSONArray("configs")?.optJSONObject(extra.optInt("npvProfileIndex"))?.optJSONObject("lockConfig")?.let(policies::add)
            for (p in policies) {
                if (p.optBoolean("isLocked") || p.optBoolean("blockRootedAndJailbroken") || p.optBoolean("onlyOfficialStores") ||
                    p.optBoolean("onlyMobileNetwork") || (p.optJSONArray("deviceIds")?.length() ?: 0) > 0)
                    return "Policy سازنده حفظ شده است؛ اتصال به اجرای معتبر محدودیت دستگاه/شبکه/قفل نیاز دارد."
                for (key in listOf("expiresAt","expiryDate")) {
                    val v = p.opt(key)
                    if (v != null && v != JSONObject.NULL && v.toString().isNotBlank() && v.toString() != "0") {
                        val expiry = v.toString().toLongOrNull()?.let { if(it < 100000000000L) it*1000 else it }
                            ?: runCatching { java.time.Instant.parse(v.toString()).toEpochMilli() }.getOrNull()
                            ?: return "قالب انقضای Policy نیاز به تطبیق دارد."
                        if(now >= expiry) return "Expired policy: اعتبار پروفایل تمام شده است."
                    }
                }
            }
        }
        if(c.protocol == "npv-preserved") return "Decoded but not connectable: نوع یا semantics پروفایل هنوز نگاشت معتبر ندارد."
        if(c.protocol == "xray-full") return net.gozar.app.engine.FullXrayProfile.blockReason(c)
        return null
    }
    fun requireConnectable(c: ProxyConfig) { val r=reason(c); require(r==null) { r.orEmpty() } }
}

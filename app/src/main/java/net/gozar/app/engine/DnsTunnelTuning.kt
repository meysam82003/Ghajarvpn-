package net.gozar.app.engine

import org.json.JSONObject

/** Public VayDNS v0.2.8 CLI, not zeddns JSON. Values keep the upstream duration units. */
object DnsTunnelTuning {
    val flags = linkedMapOf("rps" to "-rps", "idle_timeout" to "-idle-timeout", "keepalive" to "-keepalive",
        "resolver_timeout" to "-udp-timeout", "max_labels" to "-max-num-labels")
    fun validate(values: Map<String, String>) {
        values.forEach { (key, value) ->
            require(key in flags) { "Unsupported DNS tuning" }
            when(key) {
                "rps" -> require(value.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..10000.0 } == true) { "نرخ query نامعتبر است" }
                "max_labels" -> require(value.toIntOrNull() in 0..127) { "تعداد label نامعتبر است" }
                else -> durationMillis(value)
            }
        }
        if (values.containsKey("keepalive") && values.containsKey("idle_timeout"))
            require(durationMillis(values.getValue("keepalive")) < durationMillis(values.getValue("idle_timeout"))) { "keepalive باید کمتر از idle timeout باشد" }
    }
    private fun durationMillis(value: String): Long {
        val m = Regex("([0-9]{1,8})(ms|s|m)").matchEntire(value) ?: error("زمان DNS باید با ms، s یا m مشخص شود")
        val n = Math.multiplyExact(m.groupValues[1].toLong(), when(m.groupValues[2]) { "s" -> 1000L; "m" -> 60000L; else -> 1L })
        require(n in 1..86400000) { "زمان DNS خارج از بازه است" }; return n
    }
    fun fromQuery(protocol: String, query: Map<String,String>): JSONObject? {
        val values = query.filterKeys { it in flags }.filterValues { it.isNotBlank() }
        if (values.isEmpty()) return null
        require(protocol == "vaydns") { "این تنظیمات CLI فقط برای VayDNS پین‌شده تأیید شده‌اند" }
        validate(values); return JSONObject(values)
    }
    fun args(spec: JSONObject): List<String> {
        val o = spec.optJSONObject("tuning") ?: return emptyList()
        val values = o.keys().asSequence().associateWith { o.getString(it) }; validate(values)
        return values.flatMap { (k,v) -> listOf(flags.getValue(k),v) }
    }
}

package net.gozar.app.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Global settings for the DNS-based protocols (Settings -> DNS protocols),
 * applied on top of each profile when its engine starts:
 *
 * - resolver override: every DNS tunnel uses this resolver (and transport)
 *   instead of the one in its profile;
 * - resolver pool: MasterDNS / StormDNS / CottenDNS spread across these
 *   resolvers (their resolvers.txt) instead of the profile's list;
 * - workers, packet duplication, keep slow resolvers: the MasterDNS-family
 *   client options RX_TX_WORKERS / TUNNEL_PROCESS_WORKERS,
 *   (UPLOAD_)PACKET_DUPLICATION_COUNT and AUTO_DISABLE_TIMEOUT_SERVERS;
 * - remote DNS: the resolver sing-box uses for name lookups through the
 *   tunnel, for every sing-box-carried profile.
 *
 * Empty / zero means "the profile's own value".
 */
object DnsTunnelPrefs {

    data class Values(
        val overrideResolver: String = "",
        val overrideTransport: String = "udp",
        val pool: List<String> = emptyList(),
        val workers: Int = 0,
        val duplication: Int = 0,
        val keepSlowResolvers: Boolean = false,
        val remoteDns: String = ""
    ) {
        fun toJson(): JSONObject = JSONObject().put("r", overrideResolver).put("t", overrideTransport)
            .put("pool", JSONArray(pool)).put("w", workers).put("d", duplication)
            .put("k", keepSlowResolvers).put("dns", remoteDns)

        companion object {
            fun fromJson(o: JSONObject) = Values(
                o.optString("r"), o.optString("t", "udp").ifBlank { "udp" },
                o.optJSONArray("pool")?.let { a -> (0 until a.length()).map { a.getString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                o.optInt("w").coerceIn(0, 32), o.optInt("d").coerceIn(0, 10), o.optBoolean("k"), o.optString("dns")
            )
        }
    }

    private const val PREFS = "ghajar_dns_protocols"

    @Volatile var current = Values()

    fun load(ctx: Context) {
        current = runCatching {
            Values.fromJson(JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("v", "{}") ?: "{}"))
        }.getOrDefault(Values())
    }

    fun save(ctx: Context, v: Values) {
        current = v
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("v", v.toJson().toString()).apply()
    }

    private val DNSTT_KINDS = setOf("dnstt", "vaydns", "noizdns", "slipstream")
    private val MASTER_KINDS = setOf("masterdns", "stormdns", "cottendns")

    /** The sidecar spec with the global overrides applied. Pure; unit-tested. */
    fun applyTo(kind: String, spec: JSONObject, v: Values = current): JSONObject {
        val out = JSONObject(spec.toString())
        if (kind in DNSTT_KINDS && v.overrideResolver.isNotBlank()) {
            // Slipstream speaks plain UDP DNS only; it keeps its own resolver
            // when the override is DoT/DoH.
            if (kind != "slipstream" || v.overrideTransport == "udp") {
                out.put("resolver", v.overrideResolver).put("transport", v.overrideTransport)
            }
        }
        if (kind in MASTER_KINDS) {
            when {
                v.pool.isNotEmpty() -> out.put("resolvers", v.pool.joinToString(","))
                v.overrideResolver.isNotBlank() && v.overrideTransport == "udp" -> out.put("resolvers", v.overrideResolver)
            }
        }
        return out
    }

    /** Extra client.toml lines for the MasterDNS family. */
    fun masterTomlLines(kind: String, v: Values = current): List<String> {
        val lines = mutableListOf<String>()
        if (v.workers > 0) {
            lines += "RX_TX_WORKERS = ${v.workers}"
            lines += "TUNNEL_PROCESS_WORKERS = ${v.workers}"
        }
        if (v.duplication > 0) {
            lines += if (kind == "masterdns") "PACKET_DUPLICATION_COUNT = ${v.duplication}"
                else "UPLOAD_PACKET_DUPLICATION_COUNT = ${v.duplication}"
        }
        if (v.keepSlowResolvers) lines += "AUTO_DISABLE_TIMEOUT_SERVERS = false"
        return lines
    }
}

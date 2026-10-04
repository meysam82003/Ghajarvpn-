package net.gozar.app.gsb2

import android.content.Context
import net.gozar.app.ProxyConfig

/**
 * This device's side of GSB2: the issuer key it signs its own shares with,
 * and the usage and clock record for shares it received. Usage is counted
 * from the tunnel's own byte counters while a GSB2 config is connected.
 */
object Gsb2Store {
    private const val PREFS = "ghajar_gsb2"

    fun issuerKeys(context: Context): Pair<String, String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val priv = p.getString("issuer_priv", null); val pub = p.getString("issuer_pub", null)
        if (priv != null && pub != null) return priv to pub
        return Gsb2.newIssuerKey().also { (a, b) -> p.edit().putString("issuer_priv", a).putString("issuer_pub", b).apply() }
    }

    fun used(context: Context, shareId: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("used_$shareId", 0L)

    @Synchronized
    fun addUsage(context: Context, shareId: String, bytes: Long) {
        if (bytes <= 0) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putLong("used_$shareId", p.getLong("used_$shareId", 0L) + bytes).apply()
    }

    /** Wall clock, never earlier than the latest time this app has seen: moving the clock back buys no time. */
    @Synchronized
    fun now(context: Context): Long {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = p.getLong("max_seen", 0L)
        val t = maxOf(System.currentTimeMillis(), seen)
        if (t > seen + 60_000L) p.edit().putLong("max_seen", t).apply()
        return t
    }

    /** Why [config] may not connect now, or null. */
    fun gate(context: Context, config: ProxyConfig): String? {
        val meta = Gsb2.Meta.of(config) ?: return null
        return (Gsb2.check(meta, used(context, meta.shareId), now(context)) as? Gsb2.Verdict.Blocked)?.reason
    }
}

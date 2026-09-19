package net.gozar.app

import android.content.Context
import org.json.JSONObject

/** What the app should do when a given kind of network becomes the default. */
enum class NetRuleAction {
    /** Nothing. Never disconnects an existing tunnel - only declines to start one. */
    OFF,

    /** Connect to the server that was last used, or the selected one. */
    LAST,

    /** Measure every server and connect to the fastest that answers. */
    FASTEST;

    companion object {
        fun from(name: String?): NetRuleAction =
            values().firstOrNull { it.name == name } ?: OFF
    }
}

/**
 * The kinds of network the rules distinguish.
 *
 * Deliberately transport-level rather than per-SSID: reading a Wi-Fi network's
 * SSID needs ACCESS_FINE_LOCATION on Android 10+, which this app does not ask
 * for and should not start asking for to power a convenience feature. So "a
 * different Wi-Fi" is not a rule this can honestly offer.
 */
enum class NetKind { WIFI, CELLULAR, OTHER }

/**
 * Per-network auto-connect rules.
 *
 * Kept in its own preferences file and read at event time rather than cached,
 * so turning the feature off takes effect on the next network change without
 * restarting anything.
 */
object NetworkRules {

    private const val PREFS = "ghajar_network_rules"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WIFI = "wifi"
    private const val KEY_CELLULAR = "cellular"
    private const val KEY_OTHER = "other"
    private const val KEY_RECOVER = "recover"

    data class Snapshot(
        /** The master switch. Off means this file decides nothing at all. */
        val enabled: Boolean,
        val wifi: NetRuleAction,
        val cellular: NetRuleAction,
        val other: NetRuleAction,
        /**
         * When the network the tunnel was built on goes away, rebuild the
         * tunnel on the new one instead of leaving it on a dead route.
         */
        val recoverOnChange: Boolean
    ) {
        fun actionFor(kind: NetKind): NetRuleAction = when (kind) {
            NetKind.WIFI -> wifi
            NetKind.CELLULAR -> cellular
            NetKind.OTHER -> other
        }

        /** Nothing in here can fire, so there is no reason to react at all. */
        val idle: Boolean
            get() = !enabled ||
                (wifi == NetRuleAction.OFF && cellular == NetRuleAction.OFF &&
                    other == NetRuleAction.OFF && !recoverOnChange)
    }

    fun read(context: Context): Snapshot {
        val p = prefs(context)
        return Snapshot(
            enabled = p.getBoolean(KEY_ENABLED, false),
            wifi = NetRuleAction.from(p.getString(KEY_WIFI, null)),
            cellular = NetRuleAction.from(p.getString(KEY_CELLULAR, null)),
            other = NetRuleAction.from(p.getString(KEY_OTHER, null)),
            recoverOnChange = p.getBoolean(KEY_RECOVER, true)
        )
    }

    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun setRecoverOnChange(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_RECOVER, value).apply()

    fun setAction(context: Context, kind: NetKind, action: NetRuleAction) {
        val key = when (kind) {
            NetKind.WIFI -> KEY_WIFI
            NetKind.CELLULAR -> KEY_CELLULAR
            NetKind.OTHER -> KEY_OTHER
        }
        prefs(context).edit().putString(key, action.name).apply()
    }

    fun toJson(context: Context): JSONObject {
        val s = read(context)
        return JSONObject()
            .put(KEY_ENABLED, s.enabled)
            .put(KEY_WIFI, s.wifi.name)
            .put(KEY_CELLULAR, s.cellular.name)
            .put(KEY_OTHER, s.other.name)
            .put(KEY_RECOVER, s.recoverOnChange)
    }

    /** Restores from a backup. Missing keys are left alone, never reset. */
    fun restore(context: Context, o: JSONObject?) {
        o ?: return
        if (o.has(KEY_ENABLED)) setEnabled(context, o.getBoolean(KEY_ENABLED))
        if (o.has(KEY_RECOVER)) setRecoverOnChange(context, o.getBoolean(KEY_RECOVER))
        if (o.has(KEY_WIFI)) setAction(context, NetKind.WIFI, NetRuleAction.from(o.getString(KEY_WIFI)))
        if (o.has(KEY_CELLULAR)) setAction(context, NetKind.CELLULAR, NetRuleAction.from(o.getString(KEY_CELLULAR)))
        if (o.has(KEY_OTHER)) setAction(context, NetKind.OTHER, NetRuleAction.from(o.getString(KEY_OTHER)))
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

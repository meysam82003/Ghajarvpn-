package net.gozar.app

import android.content.Context

/**
 * Ghajar-facing view of the real preference keys consumed by ics-openvpn.
 * Keeping these keys identical to upstream means the engine reads the values
 * directly; the settings UI is not a disconnected cosmetic layer.
 */
object GhajarOpenVpnSettings {
    private const val KEY_RECONNECT_NETWORK = "netchangereconnect"
    private const val KEY_SYSTEM_PROXY = "usesystemproxy"
    private const val KEY_SCREEN_OFF = "screenoff"
    private const val KEY_ENCRYPT_PROFILES = "preferencryption"

    data class Snapshot(
        val reconnectOnNetworkChange: Boolean,
        val useSystemProxy: Boolean,
        val pauseOnScreenOff: Boolean,
        val encryptProfiles: Boolean,
    )

    fun ensureDefaults(context: Context) {
        val prefs = prefs(context)
        val edit = prefs.edit()
        var changed = false
        if (!prefs.contains(KEY_RECONNECT_NETWORK)) { edit.putBoolean(KEY_RECONNECT_NETWORK, true); changed = true }
        if (!prefs.contains(KEY_SYSTEM_PROXY)) { edit.putBoolean(KEY_SYSTEM_PROXY, true); changed = true }
        if (!prefs.contains(KEY_SCREEN_OFF)) { edit.putBoolean(KEY_SCREEN_OFF, false); changed = true }
        if (!prefs.contains(KEY_ENCRYPT_PROFILES)) { edit.putBoolean(KEY_ENCRYPT_PROFILES, true); changed = true }
        if (changed) edit.apply()
    }

    fun read(context: Context): Snapshot {
        ensureDefaults(context)
        val prefs = prefs(context)
        return Snapshot(
            reconnectOnNetworkChange = prefs.getBoolean(KEY_RECONNECT_NETWORK, true),
            useSystemProxy = prefs.getBoolean(KEY_SYSTEM_PROXY, true),
            pauseOnScreenOff = prefs.getBoolean(KEY_SCREEN_OFF, false),
            encryptProfiles = prefs.getBoolean(KEY_ENCRYPT_PROFILES, true),
        )
    }

    fun setReconnectOnNetworkChange(context: Context, value: Boolean) = put(context, KEY_RECONNECT_NETWORK, value)
    fun setUseSystemProxy(context: Context, value: Boolean) = put(context, KEY_SYSTEM_PROXY, value)
    fun setPauseOnScreenOff(context: Context, value: Boolean) = put(context, KEY_SCREEN_OFF, value)
    fun setEncryptProfiles(context: Context, value: Boolean) = put(context, KEY_ENCRYPT_PROFILES, value)

    private fun put(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        "${context.applicationContext.packageName}_preferences",
        Context.MODE_MULTI_PROCESS or Context.MODE_PRIVATE,
    )
}

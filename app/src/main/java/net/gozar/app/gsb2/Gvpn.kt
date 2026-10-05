package net.gozar.app.gsb2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import net.gozar.app.BrandConfig
import net.gozar.app.ConfigStore
import net.gozar.app.ProxyConfig
import net.gozar.app.R

/**
 * «ساخت اشتراک قاجار» (GVPN) on the receiving device: a received share lives
 * in the server list as its own group (a subscription whose url is
 * "gvpn:<share id>"), so its quota and time draw like any subscription's.
 * This keeps that group's numbers current, warns at 50 / 25 / 10 percent
 * left, and removes the whole group once the share has run out.
 */
object Gvpn {
    const val URL_PREFIX = "gvpn:"
    private const val PREFS = "ghajar_gsb2"
    private const val CHANNEL = BrandConfig.NOTIFICATION_CHANNEL_SERVICE
    private val STEPS = listOf(50, 25, 10)

    fun urlFor(shareId: String) = URL_PREFIX + shareId
    fun isGvpnUrl(url: String) = url.startsWith(URL_PREFIX)

    /** Remaining share as 0..100 of whichever limit is closer to running out; null when neither is set. */
    fun percentLeft(meta: Gsb2.Meta, used: Long, now: Long, importedAt: Long): Int? {
        val byQuota = if (meta.quotaBytes > 0) ((meta.quotaBytes - used).coerceAtLeast(0) * 100 / meta.quotaBytes).toInt() else null
        val byTime = if (meta.expiresAt > 0 && meta.expiresAt > importedAt) {
            (((meta.expiresAt - now).coerceAtLeast(0)) * 100 / (meta.expiresAt - importedAt)).toInt()
        } else null
        return listOfNotNull(byQuota, byTime).minOrNull()
    }

    /** Called every second while a GVPN config carries the tunnel. */
    fun tick(context: Context, meta: Gsb2.Meta) {
        val used = Gsb2Store.used(context, meta.shareId)
        val now = Gsb2Store.now(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val importedAt = p.getLong("imported_${meta.shareId}", 0L).takeIf { it > 0 } ?: now.also {
            p.edit().putLong("imported_${meta.shareId}", it).apply()
        }
        val left = percentLeft(meta, used, now, importedAt)
        if (left != null) {
            STEPS.filter { left <= it }.minOrNull()?.let { step ->
                val key = "warned_${meta.shareId}_$step"
                if (!p.getBoolean(key, false)) {
                    p.edit().putBoolean(key, true).apply()
                    notify(context, meta.shareId.hashCode() + step,
                        "اشتراک «${meta.shareName}»", "$step٪ از اشتراک باقی مانده است.")
                }
            }
        }
        // The group's quota bar: written at most every few seconds.
        val last = p.getLong("synced_${meta.shareId}", 0L)
        if (now - last >= 5_000L) {
            p.edit().putLong("synced_${meta.shareId}", now).apply()
            runCatching { ConfigStore.get(context).updateGvpnUsage(meta.shareId, used) }
        }
    }

    /** The share has run out: tell the user and take its configs out of the list. */
    fun finish(context: Context, meta: Gsb2.Meta, reason: String) {
        notify(context, meta.shareId.hashCode(), "اشتراک «${meta.shareName}» تمام شد", reason)
        runCatching { ConfigStore.get(context).deleteGvpnShare(meta.shareId) }
    }

    /** On app start: remove every received share that ran out while the app was closed. */
    fun sweep(context: Context) {
        val store = ConfigStore.get(context)
        val now = Gsb2Store.now(context)
        store.configs.value.mapNotNull { Gsb2.Meta.of(it) }.distinctBy { it.shareId }.forEach { meta ->
            val verdict = Gsb2.check(meta, Gsb2Store.used(context, meta.shareId), now)
            if (verdict is Gsb2.Verdict.Blocked) finish(context, meta, verdict.reason)
        }
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "هشدار حجم و زمان سرویس", NotificationManager.IMPORTANCE_HIGH))
            }
            val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, CHANNEL)
                else @Suppress("DEPRECATION") android.app.Notification.Builder(context)
            nm.notify(0x6A00 + (id and 0xFF), b.setSmallIcon(R.drawable.ic_stat_ghajar)
                .setContentTitle(title).setContentText(text)
                .setStyle(android.app.Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).build())
        }
    }
}

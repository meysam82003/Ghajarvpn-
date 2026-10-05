package net.gozar.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * The notification that stays in the shade while the VPN is off, with a
 * Connect button, so the user can reconnect without opening the app. It is
 * replaced by the live connection notification while a tunnel is up and
 * comes back the moment it goes down. Turned off only from the app's
 * notification settings.
 */
object GhajarIdleNotification {
    private const val CHANNEL = "ghajar_vpn_idle"
    const val ID = 4712
    private const val PREFS = "ghajar_notif_prefs"
    private const val KEY_ENABLED = "idle_notification"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) post(context) else cancel(context)
    }

    fun post(context: Context, reason: String? = null) {
        if (!enabled(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "قاجار VPN خاموش", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false); setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val connect = PendingIntent.getActivity(context, 11,
            Intent(context, GhajarWidgetConnectActivity::class.java)
                .putExtra(GhajarWidgetConnectActivity.EXTRA_STOP, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val name = runCatching {
            val store = ConfigStore.get(context)
            store.configs.value.firstOrNull { it.id == store.selectedId.value }?.name
        }.getOrNull()
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL)
            else @Suppress("DEPRECATION") Notification.Builder(context)
        val text = reason?.let { GhajarLog.redact(it).take(160) } ?: name?.let { "سرور: $it" } ?: "برای اتصال، «اتصال» را بزن"
        runCatching {
            nm.notify(ID, b.setSmallIcon(R.drawable.ic_stat_ghajar)
                .setContentTitle("قاجار VPN خاموش است")
                .setContentText(text)
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(android.R.drawable.ic_media_play, "اتصال", connect).build())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build())
        }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(ID) }
    }
}

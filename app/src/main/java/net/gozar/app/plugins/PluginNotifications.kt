package net.gozar.app.plugins

import android.app.*
import android.content.Context
import android.content.Intent
import net.gozar.app.R

object PluginNotifications {
    private const val CHANNEL = "ghajar_plugins"
    private fun manager(context: Context): NotificationManager = requireNotNull(context.getSystemService(NotificationManager::class.java)).also {
        it.createNotificationChannel(NotificationChannel(CHANNEL, "افزونه‌های VPN", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun confirm(context: Context, intent: Intent, session: Int) {
        val manager = manager(context)
        val action = PendingIntent.getActivity(context, session, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { manager.notify(session, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentTitle("تأیید نصب افزونه").setContentText("برای ادامه، نصب را در Android تأیید کنید.")
            .setContentIntent(action).setAutoCancel(true).build()) }
    }
    fun tunnel(context: Context): Notification {
        manager(context)
        val stop = PendingIntent.getService(context, 7410, Intent(context, PluginVpnService::class.java).setAction(PluginVpnService.STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, 7411, Intent(context, PluginActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentTitle("Ghajar VPN · افزونه").setContentText("مدیریت اتصال افزونه")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "قطع اتصال", stop).build()).build()
    }
}

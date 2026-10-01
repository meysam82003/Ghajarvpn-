package net.gozar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/** Persistent service channels never carry transient errors. Existing channel choices are never reset. */
object GhajarNotificationChannels {
    const val ERROR = "ghajarvpn_errors_v1"
    const val PLUGIN_SERVICE = "ghajar_plugin_vpn_v1"
    const val PLUGIN_ACTION = "ghajar_plugins" // Retain the user's existing update/install channel settings.
    private var lastErrorAt = -30_000L
    val settings = listOf(
        "gozarnet_vpn" to "سرویس VPN",
        BrandConfig.NOTIFICATION_CHANNEL_CONNECTION to "رویداد اتصال",
        ERROR to "خطای اتصال",
        PLUGIN_SERVICE to "سرویس VPN افزونه",
        PLUGIN_ACTION to "نصب و به‌روزرسانی افزونه",
        BrandConfig.NOTIFICATION_CHANNEL_GENERAL to "اعلان‌های عمومی",
        BrandConfig.NOTIFICATION_CHANNEL_SERVICE to "هشدار حجم و زمان سرویس",
        BrandConfig.NOTIFICATION_CHANNEL_IMPORTANT to "اعلان‌های مهم و شناور"
    )
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val importance = mapOf("gozarnet_vpn" to NotificationManager.IMPORTANCE_LOW,
            PLUGIN_SERVICE to NotificationManager.IMPORTANCE_LOW,
            ERROR to NotificationManager.IMPORTANCE_HIGH,
            BrandConfig.NOTIFICATION_CHANNEL_SERVICE to NotificationManager.IMPORTANCE_HIGH,
            BrandConfig.NOTIFICATION_CHANNEL_IMPORTANT to NotificationManager.IMPORTANCE_HIGH)
        settings.forEach { (id, name) ->
            if(manager.getNotificationChannel(id)==null) {
                val channel=NotificationChannel(id,name,importance[id] ?: NotificationManager.IMPORTANCE_DEFAULT)
                // A user who disabled the previous mixed plugin channel must not get a newly enabled service channel.
                if(id==PLUGIN_SERVICE && manager.getNotificationChannel(PLUGIN_ACTION)?.importance==NotificationManager.IMPORTANCE_NONE)
                    channel.importance=NotificationManager.IMPORTANCE_NONE
                manager.createNotificationChannel(channel)
            }
        }
    }
    @Synchronized fun transition(context: Context, previous: Connection, current: Connection) {
        if(previous==current) return
        val title=when {
            current==Connection.ERROR -> "اتصال VPN ناموفق بود"
            current==Connection.CONNECTED -> "قاجار VPN روشن شد"
            current==Connection.DISCONNECTED && previous in setOf(Connection.CONNECTED,Connection.DISCONNECTING) -> "قاجار VPN خاموش شد"
            else -> return
        }
        val error=current==Connection.ERROR
        if(error) { val now=SystemClock.elapsedRealtime();if(now-lastErrorAt<30_000L)return;lastErrorAt=now }
        ensure(context)
        val open=PendingIntent.getActivity(context,7421,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val channel=if(error) ERROR else BrandConfig.NOTIFICATION_CHANNEL_CONNECTION
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(if(error) 7422 else 7421,
            NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_stat_ghajar)
                .setContentTitle(title).setContentText(if(error) "برای بررسی وضعیت و راهنمای رفع خطا، برنامه را باز کنید." else title)
                .setContentIntent(open).setAutoCancel(true).setPriority(if(error) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT).build()) }
    }
}

package net.gozar.app

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Watches owned-service statuses for the real end_of_time/end_of_volume/send_on_hold/
 * sendedwarn states the panel's cron sets (see NoticationsService.php in the Faoxima
 * source) and surfaces them as a real system notification with a "renew" action,
 * plus feeds the in-app banner in GhajarShopScreen. Renews per (username, status) so
 * a service already renewed and expiring again later is notified again, but the same
 * still-expired state isn't repeated on every refresh.
 */
object GhajarServiceExpiryNotifier {

    private const val PREFS = "ghajarvpn_service_expiry"
    private const val KEY_NOTIFIED = "notified"

    fun checkAndNotify(context: Context, services: List<GhajarOwnedService>): List<GhajarOwnedService> {
        val needing = services.filter { it.needsRenewal }
        if (needing.isEmpty()) return needing

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previouslyNotified = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        val liveKeys = needing.map { "${it.username}:${it.status.lowercase()}" }.toSet()

        val stillNotified = mutableSetOf<String>()
        needing.forEach { service ->
            val key = "${service.username}:${service.status.lowercase()}"
            val alreadyPosted = key in previouslyNotified
            if (alreadyPosted || post(context, service)) stillNotified += key
        }
        // Only keep keys for statuses that are still current, so a future recurrence
        // of the same status on the same service notifies again instead of staying silent.
        prefs.edit().putStringSet(KEY_NOTIFIED, stillNotified.filter { it in liveKeys }.toSet()).apply()
        return needing
    }

    private fun reasonText(status: String): String = when (status.lowercase()) {
        "end_of_time" -> "زمان سرویس شما به پایان رسیده"
        "end_of_volume" -> "حجم سرویس شما تمام شده"
        "send_on_hold" -> "سرویس شما موقتاً متوقف شده"
        "sendedwarn" -> "سرویس شما به‌زودی منقضی می‌شود"
        else -> "سرویس شما نیاز به تمدید دارد"
    }

    private fun post(context: Context, service: GhajarOwnedService): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = BrandConfig.NOTIFICATION_CHANNEL_SERVICE
        if (Build.VERSION.SDK_INT >= 26 && context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return false

        val notifyId = ("expiry:" + service.username + ":" + service.status).hashCode()
        // Opens the app plainly (no deep-link into a specific screen/dialog): wiring that
        // would require touching MainActivity's navigation, which is out of scope here.
        // The in-app renew button lives on the Store > My Services banner/card instead.
        val open = PendingIntent.getActivity(
            context,
            notifyId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val message = "${service.productName}: ${reasonText(service.status)}. برای تمدید لمس کن."
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentTitle("نیاز به تمدید سرویس")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(notifyId, notification)
            true
        }.getOrDefault(false)
    }
}

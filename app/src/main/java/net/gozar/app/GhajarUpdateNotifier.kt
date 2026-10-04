package net.gozar.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * «یادآوری بروزرسانی‌ها»: on by default. Checks at most every 12 hours (from
 * the existing background job and at app start), posts one notification for a
 * newer release, reminds again every 24 hours until it is installed, and
 * removes it once the installed version caught up.
 */
object GhajarUpdateNotifier {
    private const val PREFS = "ghajar_update_reminder"
    private const val CHANNEL = "ghajar_updates"
    private const val NOTIF_ID = 0x51A7
    private const val CHECK_EVERY = 12L * 60 * 60 * 1000
    private const val REMIND_EVERY = 24L * 60 * 60 * 1000
    const val EXTRA_OPEN_UPDATE = "net.gozar.app.OPEN_UPDATE"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()
        if (!on) cancel(context)
    }

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Background entry point. Never throws. */
    suspend fun periodic(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val installed = installedVersion(app)
        val notified = prefs.getString("version", "").orEmpty()
        if (notified.isNotEmpty() && !UpdateChecker.isNewer(notified, installed)) cancel(app)
        if (!enabled(app)) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("checkedAt", 0L) < CHECK_EVERY) return
        prefs.edit().putLong("checkedAt", now).apply()
        val r = runCatching { UpdateChecker.check(installed) }.getOrNull() as? UpdateChecker.Result.Available ?: return
        GhajarUpdateFlow.offer(r, open = false)
        if (r.version == notified && now - prefs.getLong("notifiedAt", 0L) < REMIND_EVERY) return
        post(app, r.version)
        prefs.edit().putString("version", r.version).putLong("notifiedAt", now).apply()
    }

    private fun post(context: Context, version: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "یادآوری بروزرسانی‌ها", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 7, Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentTitle("بروزرسانی جدید قاجار VPN")
            .setContentText("نسخهٔ $version آمادهٔ نصب است.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID) }
    }
}

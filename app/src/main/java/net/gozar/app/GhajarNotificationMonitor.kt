package net.gozar.app

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object GhajarNoticeBus {
    private val _notice = MutableStateFlow<GhajarNotice?>(null)
    val notice = _notice.asStateFlow()
    fun publish(value: GhajarNotice) { _notice.value = value }
    fun clear() { _notice.value = null }
}

class GhajarNotificationJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            GhajarNotificationMonitor.refresh(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

object GhajarNotificationMonitor {
    private const val JOB_ID = 0x514A52

    fun initialize(context: Context) {
        createChannels(context)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) == null) {
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, GhajarNotificationJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(15 * 60 * 1000L)
                .build()
            scheduler.schedule(info)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { refresh(context.applicationContext) }
    }

    suspend fun refresh(context: Context) {
        val api = GhajarStoreApi(context)
        if (!api.isLinked) return
        val prefs = context.getSharedPreferences("ghajarvpn_notices_v2", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen_notices", emptySet()).orEmpty().toMutableSet()
        val notices = runCatching { api.notices() }.getOrDefault(emptyList())
        notices.filterNot { it.id in seen }.forEach { notice ->
            val enriched = notice.withUsageSummary()
            GhajarNoticeBus.publish(enriched)
            post(context, enriched)
            seen += notice.id
        }
        if (seen.size > 250) {
            val keep = seen.toList().takeLast(200).toSet()
            prefs.edit().putStringSet("seen_notices", keep).apply()
        } else {
            prefs.edit().putStringSet("seen_notices", seen).apply()
        }

    }

    /** Uses the panel-provided meta values; no guessed quota or expiry is emitted. */
    private fun GhajarNotice.withUsageSummary(): GhajarNotice {
        val values = meta ?: return this
        val remainingGb = values.remainingBytes?.div(1024.0 * 1024 * 1024)
        val suffix = listOfNotNull(
            remainingGb?.let { "حجم باقی‌مانده: ${"%.2f".format(java.util.Locale.US, it)} گیگابایت" },
            values.daysRemaining?.let { "زمان باقی‌مانده: $it روز" }
        ).joinToString(" • ")
        if (suffix.isBlank() || message.contains(suffix)) return this
        return copy(message = "$message\n$suffix")
    }

    private fun post(context: Context, notice: GhajarNotice) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val channel = when {
            notice.important -> BrandConfig.NOTIFICATION_CHANNEL_IMPORTANT
            notice.serviceAlert -> BrandConfig.NOTIFICATION_CHANNEL_SERVICE
            else -> BrandConfig.NOTIFICATION_CHANNEL_GENERAL
        }
        val open = PendingIntent.getActivity(
            context,
            notice.id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentTitle(notice.title)
            .setContentText(notice.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.message))
            .setPriority(if (notice.important) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(notice.id.hashCode(), notification)
    }

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(BrandConfig.NOTIFICATION_CHANNEL_GENERAL, "اعلان‌های قاجار", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(BrandConfig.NOTIFICATION_CHANNEL_SERVICE, "هشدار حجم و زمان سرویس", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(BrandConfig.NOTIFICATION_CHANNEL_IMPORTANT, "اعلان‌های مهم و شناور", NotificationManager.IMPORTANCE_HIGH)
            )
        )
    }
}

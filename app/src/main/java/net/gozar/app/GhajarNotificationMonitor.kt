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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/** A tapped "renew this service" action (notification or in-app banner) asks the
 * store screen to open the renewal dialog for exactly that service username. */
object GhajarRenewRequest {
    private val _requested = MutableStateFlow<String?>(null)
    val requested = _requested.asStateFlow()
    fun request(username: String) { _requested.value = username }
    fun consume() { _requested.value = null }
}

/**
 * Whether the shop is open, as the server last reported it.
 *
 * The app is never *blocked* by the shop being switched off - its tunnel has
 * nothing to do with the store, and breaking a working VPN because an operator
 * is doing maintenance would be punishing users for someone else's window. It
 * covers the store and says why.
 */
object GhajarShopStatus {
    private val _enabled = MutableStateFlow(true)
    val enabled = _enabled.asStateFlow()
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()
    fun publish(open: Boolean, why: String) {
        _enabled.value = open
        _message.value = why
    }
}

object GhajarNoticeBus {
    private val _notice = MutableStateFlow<GhajarNotice?>(null)
    val notice = _notice.asStateFlow()
    private var account = ""
    private var pending = emptyList<GhajarNotice>()
    private val dismissedInSession = mutableSetOf<String>()
    @Synchronized fun publish(accountKey: String, values: List<GhajarNotice>, acknowledged: Set<String>) {
        if (account != accountKey) { pending = emptyList(); account = accountKey; dismissedInSession.clear() }
        pending = values.distinctBy { it.id }.filterNot { it.id in acknowledged || it.id in dismissedInSession }
            .sortedByDescending { if (it.important) 2 else if (it.serviceAlert) 1 else 0 }
        _notice.value = pending.firstOrNull()
    }
    @Synchronized fun dismiss(id: String) {
        dismissedInSession += id
        pending = pending.filterNot { it.id == id }
        _notice.value = pending.firstOrNull()
    }
    @Synchronized fun reset() { pending = emptyList(); account = ""; dismissedInSession.clear(); _notice.value = null }
}

class GhajarNotificationJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            val success = GhajarNotificationMonitor.refresh(applicationContext)
            jobFinished(params, !success)
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

class GhajarNotificationBootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GhajarNotificationMonitor.initialize(context.applicationContext)
    }
}

/**
 * The "خوندم" button on a notification in the shade.
 *
 * Marking a notice read had to be done inside the app, which meant the way to
 * get rid of one from the shade was to swipe it - and a swipe tells the server
 * nothing, so the same warning came back on the next repeat and the user's
 * "I have read this" never left the phone. This carries the acknowledgement
 * all the way: locally, so the floating banner stops, and to the shop, so the
 * mini app and the web panel stop showing it too.
 *
 * It is a broadcast rather than an activity on purpose: acknowledging a notice
 * is not a reason to drag someone into the app.
 */
class GhajarNoticeAckReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_NOTICE_ID)?.takeIf { it.isNotBlank() } ?: return
        val app = context.applicationContext
        // Taken off the shade immediately. The acknowledgement round-trip may
        // take a moment, and a button that appears to do nothing gets pressed
        // again.
        runCatching { NotificationManagerCompat.from(app).cancel(id.hashCode()) }
        val pending = goAsync()
        GhajarNotificationMonitor.acknowledgeAsync(app, id) { pending.finish() }
    }

    companion object {
        const val ACTION_ACK = "net.gozar.app.action.NOTICE_ACK"
        const val EXTRA_NOTICE_ID = "net.gozar.app.extra.NOTICE_ID"
    }
}

object GhajarNotificationMonitor {
    private const val JOB_ID = 0x514A52
    private val deliveryLock = Mutex()
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun accountKey(context: Context): String? = GhajarAccountStore(context).token()
        .takeIf { it.isNotBlank() }?.let { token ->
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
                .take(12).joinToString("") { "%02x".format(it) }
        }

    fun acknowledge(context: Context, id: String) {
        acknowledgeAsync(context, id) {}
    }

    /**
     * The same acknowledgement, with a hook for callers that must stay alive
     * until the server has been told - a broadcast receiver is killed the
     * moment onReceive returns, which would leave the round-trip unfinished.
     */
    fun acknowledgeAsync(context: Context, id: String, onDone: () -> Unit) {
        val key = accountKey(context)
        if (key != null) {
            val prefs = context.getSharedPreferences("ghajarvpn_notices_$key", Context.MODE_PRIVATE)
            val acknowledged = prefs.getStringSet("acknowledged", emptySet()).orEmpty().toMutableSet()
            acknowledged += id
            prefs.edit().putStringSet("acknowledged", acknowledged.toList().takeLast(500).toSet()).apply()
        }
        GhajarNoticeBus.dismiss(id)
        if (key == null) { onDone(); return }
        monitorScope.launch {
            try {
                runCatching { GhajarStoreApi(context.applicationContext).dismissNotice(id) }
            } finally { onDone() }
        }
    }

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
        monitorScope.launch { refresh(context.applicationContext) }
    }

    suspend fun refresh(context: Context): Boolean {
        if (!deliveryLock.tryLock()) return true
        try {
            val key = accountKey(context) ?: run { GhajarNoticeBus.reset(); return true }
            val api = GhajarStoreApi(context)
            val feed = try { api.noticeFeed() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { return false }
            if (accountKey(context) != key) return true
            createChannels(context)

            // The shop's state, so the store screen can cover itself rather
            // than letting someone start a purchase into a closed shop and
            // discover it at the payment step.
            GhajarShopStatus.publish(feed.shopEnabled, feed.shopMessage)

            val prefs = context.getSharedPreferences("ghajarvpn_notices_$key", Context.MODE_PRIVATE)
            val notified = prefs.getStringSet("notified", emptySet()).orEmpty().toMutableSet()
            val acknowledged = prefs.getStringSet("acknowledged", emptySet()).orEmpty()

            // Only what the server says is due. A repeating warning - "your
            // service ends tomorrow", every three hours until it is closed -
            // comes back because the server's clock decides, not because this
            // app kept its own timer.
            val due = feed.notices.filter { it.shouldFloat }.map { it.withUsageSummary() }
            GhajarNoticeBus.publish(key, due, acknowledged)

            val posted = mutableListOf<String>()
            due.forEach { notice ->
                val repeating = notice.id.startsWith("notice:")
                // A repeating notice is gated by the server, which is why the
                // local fingerprint is skipped for it: keeping both would mean
                // the second showing is suppressed here after the server has
                // already decided it is due, and the repeat would never happen.
                val fingerprint = notice.id + ":" + MessageDigest.getInstance("SHA-256")
                    .digest((notice.title + "\n" + notice.message).toByteArray())
                    .take(12).joinToString("") { "%02x".format(it) }
                val alreadySeen = !repeating && fingerprint in notified
                if (!alreadySeen && post(context, notice)) {
                    notified += fingerprint
                    if (repeating) posted += notice.id
                }
            }
            prefs.edit().putStringSet("notified", notified.toList().takeLast(500).toSet()).apply()

            // Tells the server they were shown, which both clears the unread
            // badge and starts the repeat interval running. Done after posting
            // so a notification that failed to post does not start the clock.
            if (posted.isNotEmpty()) {
                runCatching { api.markNoticesShown(posted) }
            }
            return true
        } finally { deliveryLock.unlock() }
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

    private fun post(context: Context, notice: GhajarNotice): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = when {
            notice.important -> BrandConfig.NOTIFICATION_CHANNEL_IMPORTANT
            notice.serviceAlert -> BrandConfig.NOTIFICATION_CHANNEL_SERVICE
            else -> BrandConfig.NOTIFICATION_CHANNEL_GENERAL
        }
        if (Build.VERSION.SDK_INT >= 26 && context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val open = PendingIntent.getActivity(
            context,
            notice.id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentTitle(notice.title)
            .setContentText(notice.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.message))
            .setPriority(if (notice.important) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
        notice.serviceUsername?.takeIf { it.isNotBlank() }?.let { username ->
            val renew = PendingIntent.getActivity(
                context,
                (notice.id + ":renew").hashCode(),
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_RENEW_SERVICE_USERNAME, username),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "تمدید همین سرویس", renew)
        }
        // "I have read this", answerable from the shade. Without it the only
        // way to stop a repeating warning was to open the app and find the
        // banner, so the repeat kept firing at people who had already acted.
        val ack = PendingIntent.getBroadcast(
            context,
            (notice.id + ":ack").hashCode(),
            Intent(context, GhajarNoticeAckReceiver::class.java)
                .setAction(GhajarNoticeAckReceiver.ACTION_ACK)
                .setPackage(context.packageName)
                .putExtra(GhajarNoticeAckReceiver.EXTRA_NOTICE_ID, notice.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(0, "خوندم", ack)
        val notification = builder.build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(notice.id.hashCode(), notification)
            true
        }.getOrDefault(false)
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

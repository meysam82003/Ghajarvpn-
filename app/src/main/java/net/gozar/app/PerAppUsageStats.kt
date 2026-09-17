package net.gozar.app

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real per-app data-usage breakdown over the VPN interface, via the platform's
 * own NetworkStatsManager (per-UID summary). This is Android's own accounting,
 * not an estimate: it only works for traffic that actually passed through the
 * VpnService's tun interface, and only once the user has granted the special
 * "Usage access" permission (there is no runtime prompt for it — the OS only
 * allows granting it from Settings). Every result is honest about which of
 * those two conditions blocked it rather than showing invented numbers.
 */
object PerAppUsageStats {

    data class AppUsage(
        val packageName: String,
        val label: String,
        val rxBytes: Long,
        val txBytes: Long
    ) {
        val totalBytes: Long get() = rxBytes + txBytes
    }

    sealed interface Result {
        data class Ok(val apps: List<AppUsage>) : Result
        data object PermissionRequired : Result
        data class Unavailable(val reason: String) : Result
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** [windows] are the concrete [startMillis, endMillis) spans during which the
     * config being inspected was actually the active tunnel (hour-bucket
     * granularity, matching UsageStore's own per-config attribution), so an
     * app's usage is only attributed here for time it could plausibly have
     * gone through that specific config. */
    suspend fun queryForWindows(context: Context, windows: List<Pair<Long, Long>>, topN: Int = 8): Result =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess(context)) return@withContext Result.PermissionRequired
            if (windows.isEmpty()) return@withContext Result.Ok(emptyList())
            val manager = context.getSystemService(NetworkStatsManager::class.java)
                ?: return@withContext Result.Unavailable("NetworkStatsManager روی این دستگاه در دسترس نیست.")
            val totals = HashMap<Int, LongArray>() // uid -> [rx, tx]
            try {
                for ((start, end) in windows) {
                    if (end <= start) continue
                    val stats = runCatching {
                        manager.querySummary(ConnectivityManager.TYPE_VPN, null, start, end)
                    }.getOrNull() ?: continue
                    stats.use { s ->
                        val bucket = NetworkStats.Bucket()
                        while (s.hasNextBucket()) {
                            s.getNextBucket(bucket)
                            val uid = bucket.uid
                            if (uid < 0) continue
                            val cur = totals[uid] ?: longArrayOf(0L, 0L)
                            totals[uid] = longArrayOf(cur[0] + bucket.rxBytes, cur[1] + bucket.txBytes)
                        }
                    }
                }
            } catch (e: SecurityException) {
                return@withContext Result.PermissionRequired
            } catch (e: Exception) {
                return@withContext Result.Unavailable(e.message ?: "خطا در خواندن آمار مصرف اپلیکیشن‌ها")
            }
            if (totals.isEmpty()) return@withContext Result.Ok(emptyList())
            val pm = context.packageManager
            val apps = totals.entries.mapNotNull { (uid, v) ->
                val packages = runCatching { pm.getPackagesForUid(uid) }.getOrNull()
                val pkg = packages?.firstOrNull() ?: return@mapNotNull null
                val label = runCatching {
                    val info: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                }.getOrDefault(pkg)
                AppUsage(pkg, label, v[0], v[1])
            }.sortedByDescending { it.totalBytes }.take(topN)
            Result.Ok(apps)
        }
}

package net.gozar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.sync.Mutex

/** Runs in the existing persisted JobScheduler job, independently of a store account. */
object GhajarUpdateMonitor {
    private const val CHANNEL = "ghajar_app_updates"
    private const val ID = 11311
    private const val INTERVAL = 6L * 60 * 60 * 1000
    private val lock = Mutex()
    suspend fun refresh(context: Context, force: Boolean = false, showDialog: Boolean = false) {
        if(!lock.tryLock())return
        try {
            val prefs=context.getSharedPreferences("ghajar_app_updates",0)
            val version=context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty()
            val cached=prefs.getString("version",null)
            if(cached!=null && !UpdateChecker.isNewer(cached,version)) {
                prefs.edit().clear().apply();GhajarUpdateFlow.upToDate();NotificationManagerCompat.from(context).cancel(ID)
            }
            if(cached != null && UpdateChecker.isNewer(cached,version)) runCatching {
                val o=org.json.JSONObject(prefs.getString("release", "{}")!!)
                val apk=o.optJSONObject("apk")?.let { UpdateChecker.ReleaseAsset(it.getString("name"),it.getString("url"),it.getLong("size")) }
                GhajarUpdateFlow.found(UpdateChecker.Result.Available(cached,o.getString("url"),o.optString("notes"),apk,o.optString("sha").takeIf(String::isNotBlank)))
            }
            val now=System.currentTimeMillis()
            if(!force && now-prefs.getLong("checked",0)<INTERVAL)return
            when(val result=UpdateChecker.check(version)) {
                is UpdateChecker.Result.Available -> {
                    val saved=org.json.JSONObject().put("url",result.url).put("notes",result.changelog).put("sha",result.apkSha256)
                    result.apk?.let { saved.put("apk",org.json.JSONObject().put("name",it.name).put("url",it.url).put("size",it.sizeBytes)) }
                    prefs.edit().putLong("checked",now).putString("version",result.version).putString("release",saved.toString()).apply()
                    GhajarUpdateFlow.found(result)
                    if(showDialog)GhajarUpdateFlow.offer(result)
                    if(now-prefs.getLong("notified",0)>=INTERVAL && notify(context,result.version))prefs.edit().putLong("notified",now).apply()
                }
                UpdateChecker.Result.UpToDate -> {prefs.edit().clear().putLong("checked",now).apply();GhajarUpdateFlow.upToDate();NotificationManagerCompat.from(context).cancel(ID)}
                UpdateChecker.Result.Failed -> Unit // Network failure never means up to date.
            }
        } finally {lock.unlock()}
    }
    private fun notify(context: Context, version: String): Boolean {
        if(!NotificationManagerCompat.from(context).areNotificationsEnabled())return false
        val manager=context.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel(CHANNEL,"بروزرسانی قاجار",NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(context,ID,Intent(context,MainActivity::class.java).putExtra("ghajar_show_update",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return runCatching { NotificationManagerCompat.from(context).notify(ID,NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ghajar).setContentTitle("نسخهٔ $version قاجار آماده است")
            .setContentText("برای مشاهده تغییرات و نصب امن بزن").setContentIntent(open).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION).build());true }.getOrDefault(false)
    }
}

package net.gozar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Optional control panel; the engine's mandatory foreground notification remains owned by the engine. */
object GhajarQuickControls {
    const val CHANNEL="ghajar_quick_controls"
    private const val ID=11312
    fun enabled(context: Context)=context.getSharedPreferences(CHANNEL,0).getBoolean("enabled",true)
    fun setEnabled(context: Context,value: Boolean){context.getSharedPreferences(CHANNEL,0).edit().putBoolean("enabled",value).apply();refresh(context)}
    private fun action(context: Context,command: String,code: Int)=PendingIntent.getActivity(context,code,
        Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("ghajar_control",command),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun refresh(context: Context) {
        val manager=NotificationManagerCompat.from(context)
        if(!enabled(context)){manager.cancel(ID);return}
        if(!manager.areNotificationsEnabled())return
        val app=context.applicationContext;val store=ConfigStore.get(app);val state=VpnState.state.value
        val id=VpnState.activeId.value?:store.selectedId.value
        val config=store.configs.value.firstOrNull{it.id==id}
        val title=when(state){Connection.CONNECTED->"متصل";Connection.CONNECTING->"در حال اتصال";Connection.DISCONNECTING->"در حال قطع";Connection.ERROR->"اتصال ناموفق";else->"آمادهٔ اتصال"}
        val server=config?.name?.let(BrandConfig::sanitizePublicText)?.take(80) ?: if(id?.startsWith("ovpn:")==true)"OpenVPN" else "سروری انتخاب نشده"
        val location=LocationFetcher.tunnelLocation.value?.country?.take(40) ?: "نامشخص"
        val palette=ghajarPaletteFor(app)
        val view=RemoteViews(app.packageName,R.layout.notification_quick_controls)
        view.setTextViewText(R.id.quick_state,"قاجار VPN · $title")
        view.setTextViewText(R.id.quick_server,server)
        view.setTextViewText(R.id.quick_location,"لوکیشن: $location")
        view.setTextViewText(R.id.quick_toggle,if(state==Connection.CONNECTED)"قطع اتصال" else "اتصال")
        view.setInt(R.id.quick_logo,"setColorFilter",palette.primary.toArgb())
        for(v in intArrayOf(R.id.quick_state,R.id.quick_server,R.id.quick_location))view.setTextColor(v,palette.textPrimary.toArgb())
        for(v in intArrayOf(R.id.quick_toggle,R.id.quick_next,R.id.quick_open))view.setTextColor(v,palette.primary.toArgb())
        view.setInt(R.id.quick_root,"setBackgroundColor",palette.surface.toArgb())
        view.setOnClickPendingIntent(R.id.quick_toggle,action(app,"toggle",11320))
        view.setOnClickPendingIntent(R.id.quick_next,action(app,"next",11321))
        view.setOnClickPendingIntent(R.id.quick_open,action(app,"open",11322))
        if(Build.VERSION.SDK_INT>=26)app.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"کنترل سریع قاجار",NotificationManager.IMPORTANCE_LOW))
        runCatching {manager.notify(ID,NotificationCompat.Builder(app,CHANNEL).setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentTitle("قاجار VPN · $title").setContentText(server).setContentIntent(action(app,"open",11322))
            .setCustomBigContentView(view).setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setOngoing(true).build())}
    }
}

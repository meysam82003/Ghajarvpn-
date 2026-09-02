package net.gozar.app

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class GozarApplication : org.strongswan.android.logic.StrongSwanApplication() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val foreground = MutableStateFlow(false)
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        ensureOpenVpnNotificationChannels()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                foreground.value = startedActivities > 0
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                foreground.value = startedActivities > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

    }

    /**
     * ics-openvpn normally creates these channels from ICSOpenVPNApplication.
     * Ghajar has its own Application class, so create the exact channel IDs here
     * before OpenVPNService calls startForeground() on Android 8+.
     */
    private fun ensureOpenVpnNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val appName = getString(R.string.app_name)

        val background = NotificationChannel(
            OPENVPN_CHANNEL_BACKGROUND,
            "$appName · OpenVPN",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "اتصال فعال OpenVPN"
            enableLights(false)
        }

        val status = NotificationChannel(
            OPENVPN_CHANNEL_STATUS,
            "$appName · وضعیت OpenVPN",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "تغییرات وضعیت اتصال OpenVPN"
        }

        val userRequest = NotificationChannel(
            OPENVPN_CHANNEL_USER_REQUEST,
            "$appName · درخواست OpenVPN",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "درخواست‌های ورود و احراز هویت OpenVPN"
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(background, status, userRequest))
    }

    private companion object {
        const val OPENVPN_CHANNEL_BACKGROUND = "openvpn_bg"
        const val OPENVPN_CHANNEL_STATUS = "openvpn_newstat"
        const val OPENVPN_CHANNEL_USER_REQUEST = "openvpn_userreq"
    }
}

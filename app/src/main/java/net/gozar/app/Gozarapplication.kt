package net.gozar.app

import android.app.Activity
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
        // Android creates the same Application class in every app process.
        // The :openvpn process must stay minimal: no Ghajar UI lifecycle, refresh jobs,
        // or other main-process work should run before OpenVPNService is created.
        if (currentProcessName().endsWith(OPENVPN_PROCESS_SUFFIX)) return

        GhajarOpenVpnSettings.ensureDefaults(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val enteringApp = startedActivities == 0
                startedActivities++
                foreground.value = startedActivities > 0
                if (enteringApp) scope.launch {
                    SubscriptionRefresher.refreshStale(ConfigStore.get(this@GozarApplication), force = true)
                }
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


    private fun currentProcessName(): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return android.app.Application.getProcessName().orEmpty()
        }
        val pid = android.os.Process.myPid()
        val manager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        return manager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName.orEmpty()
    }

    private companion object {
        const val OPENVPN_PROCESS_SUFFIX = ":openvpn"
    }
}
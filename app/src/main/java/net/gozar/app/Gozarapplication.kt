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
        GhajarLog.i("Startup", "phase: application create begin")
        // GozarApplication extends StrongSwanApplication, not ICSOpenVPNApplication,
        // so the OpenVPN library's own restriction/preferences bootstrap never ran.
        // OpenVPNService (":openvpn" process) calls GlobalPreferences.getInstance()
        // on every connect and throws "Global preferences instance is not set" if
        // this is skipped. Application.onCreate() runs once per process, so doing
        // this here covers both the main process and the ":openvpn" process.
        de.blinkt.openvpn.api.AppRestrictions.getInstance(this).checkRestrictions(this)
        GhajarLog.i("Startup", "phase: app restrictions done")
        GhajarLog.init(this)
        GhajarLog.installCrashHandler(this)
        GhajarLog.i("Startup", "phase: logger ready")
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName()
            else (getSystemService(android.app.ActivityManager::class.java).runningAppProcesses.orEmpty()
                .firstOrNull { it.pid == android.os.Process.myPid() }?.processName)
        if (processName == packageName) GhajarNotificationMonitor.initialize(this)
        GhajarLog.i("Startup", "phase: notification monitor ready")
        GhajarOpenVpnBridge.initialize(this)
        GhajarLog.i("Startup", "phase: openvpn bridge ready")

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val enteringApp = startedActivities == 0
                startedActivities++
                foreground.value = startedActivities > 0
                if (enteringApp) scope.launch {
                    val configStore = ConfigStore.get(this@GozarApplication)
                    configStore.awaitReady()
                    SubscriptionRefresher.refreshStale(configStore, force = false)
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
}

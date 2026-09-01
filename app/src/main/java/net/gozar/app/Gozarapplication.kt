package net.gozar.app

import android.app.Activity
import android.app.Application
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

    private val crashFile by lazy { java.io.File(getFilesDir(), "ghajar-crash.txt") }
    private val systemHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val header = "\n===== $stamp  thread=${thread.name}  proc=${android.os.Process.myPid()} =====\n"
        val trace = android.util.Log.getStackTraceString(throwable)
        val text = header + trace + "\n"
        java.io.FileWriter(crashFile, true).use { it.write(text) }
        // Keep only the most recent tail so the file can never grow unbounded.
        val max = 96 * 1024
        if (crashFile.length() > max) {
            val bytes = crashFile.readBytes()
            java.io.FileWriter(crashFile, false).use {
                it.write(String(bytes, Charsets.UTF_8).takeLast(max / 2))
            }
        }
    }

    private fun previousCrash(): String =
        if (crashFile.isFile) crashFile.readText().takeLast(16 * 1024) else ""

    override fun onCreate() {
        super.onCreate()
        // Persist any fatal crash so a silent process death (e.g. the reported
        // checkout exit to the home screen) leaves a readable trace in the
        // next launch instead of nothing.
        previousCrashText.value = runCatching { previousCrash() }.getOrNull().orEmpty()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(thread, throwable) }
            systemHandler?.uncaughtException(thread, throwable)
        }
        VpnState.initialize(this)
        GhajarNotificationMonitor.initialize(this)
        GhajarOpenVpnBridge.initialize(this)
        if (Application.getProcessName() == packageName) scope.launch {
            runCatching {
                GhajarOpenVpnBridge.syncSavedProfiles(
                    this@GozarApplication,
                    ConfigStore.get(this@GozarApplication)
                )
            }
        }

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

    companion object {
        /** Tail of the previous fatal crash, surfaced for diagnostics. */
        val previousCrashText = MutableStateFlow("")
    }
}

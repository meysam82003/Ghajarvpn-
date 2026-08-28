package net.gozar.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import gozarcore.Gozarcore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class GozarVpnService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private var aetherSpec: AetherSpec? = null
    private var torSpec: String? = null
    private var blockFd: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO +
        kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
            // No raw exception text: it may contain a subscription credential.
            Log.e(TAG, error.javaClass.simpleName)
            die("شروع اتصال ناموفق بود؛ مجوز VPN و تنظیمات سرویس را بررسی کن.")
        })
    private var startJob: Job? = null
    private var pollJob: Job? = null
    private var configName: String = "VPN"
    private var stopLabel: String = "Disconnect"
    private var lastPingLabel: String = "پینگ: —"
    @Volatile private var tearingDown = false
    private var autoSelector: AutoSelector? = null
    private var autoJob: Job? = null

    private var nativeInitializationFailed = false

    override fun onCreate() {
        super.onCreate()
        try {
            Gozarcore.setLogger(object : gozarcore.Logger {
                override fun log(line: String?) { Log.i("XrayCore", line ?: "") }
            })
            TorLog.sink = { line -> Log.i("XrayCore", line) }
        } catch (failure: LinkageError) {
            nativeInitializationFailed = true
            Log.e(TAG, failure.javaClass.simpleName)
        } catch (failure: Exception) {
            nativeInitializationFailed = true
            Log.e(TAG, failure.javaClass.simpleName)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                runCatching { blockFd?.close() }; blockFd = null
                die(null)
                return START_NOT_STICKY
            }
            ACTION_PING -> {
                refreshPing()
                return START_STICKY
            }
            ACTION_WARM -> {
                if (tunFd != null) {
                    VpnBridge.sendConnected(applicationContext)
                    return START_STICKY
                }
                return START_NOT_STICKY
            }
            else -> {
                val configJson = intent?.getStringExtra(EXTRA_CONFIG)
                aetherSpec = AetherSpec.parse(intent?.getStringExtra(EXTRA_AETHER))
                torSpec = intent?.getStringExtra(EXTRA_TOR)
                configName = intent?.getStringExtra(EXTRA_NAME) ?: "VPN"
                stopLabel = intent?.getStringExtra(EXTRA_STOP_LABEL) ?: "Disconnect"
                if (configJson.isNullOrEmpty()) {
                    die("No config provided")
                    return START_NOT_STICKY
                }
                startTunnel(configJson)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(configJson: String) {
        if (tunFd != null || startJob?.isActive == true) return
        tearingDown = false
        try { startForeground(NOTIF_ID, buildNotification()) }
        catch (error: Exception) {
            VpnBridge.sendError(applicationContext, "اعلان اتصال شروع نشد؛ مجوزهای برنامه را بررسی کن.")
            stopSelf()
            return
        }

        startJob = scope.launch {
            if (nativeInitializationFailed) {
                die("هستهٔ اتصال بارگذاری نشد؛ نسخهٔ سازگار با گوشی را نصب کن.")
                return@launch
            }
            val builder = Builder()
                .setSession(BrandConfig.APP_NAME_FA)
                .setMtu(1500)
                .addAddress("10.10.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)

            applyPerApp(builder)

            val pfd = builder.establish()
            if (pfd == null) {
                die("VPN permission not granted")
                return@launch
            }
            tunFd = pfd

            try {
                setupGeoAssets()
                val spec = aetherSpec
                if (spec != null) {
                    if (!AetherController.available(applicationContext)) {
                        die(if (ConfigStore.get(applicationContext).lang.value == Lang.FA)
                            "هستهٔ Aether برای معماری این نسخه موجود نیست؛ سرویس دیگری انتخاب کن."
                            else "Aether is unavailable for this build's architecture; select another service.")
                        return@launch
                    }
                    val up = withContext(Dispatchers.IO) {
                        AetherController.start(applicationContext, spec)
                    }
                    if (!up) {
                        AetherController.stop()
                        TorController.stop()
                        die("Aether failed to start")
                        return@launch
                    }
                }
                runCatching { Gozarcore.stop() }
                Gozarcore.start(configJson, pfd.detachFd().toLong())
                Log.i(TAG, "Xray core started, tunnel up")
                val tor = torSpec
                if (tor != null) {
                    if (!TorController.available(applicationContext)) {
                        die("Tor engine is not bundled in this build")
                        return@launch
                    }
                    val parts = tor.split("|")
                    val up = withContext(Dispatchers.IO) {
                        TorController.start(
                            applicationContext,
                            parts.getOrElse(0) { "" },
                            parts.getOrElse(1) { "0" } == "1"
                        )
                    }
                    if (!up) {
                        TorController.stop()
                        die("Tor failed to start")
                        return@launch
                    }
                }
                VpnBridge.sendConnected(applicationContext)
                startPolling()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, e.javaClass.simpleName)
                die("هستهٔ اتصال شروع نشد؛ تنظیمات سرویس را بررسی کن.")
            }
        }
    }

    private fun startAutoSelect() {
        if (autoJob?.isActive == true) return
        val store = ConfigStore.get(applicationContext)
        val selector = AutoSelector(applicationContext, store) { target ->
            switchTunnel(target)
        }
        autoSelector = selector
        autoJob = scope.launch {
            store.autoSelect.collect { on ->
                if (on) selector.start(this) else selector.stop()
            }
        }
    }

    private fun stopAutoSelect() {
        autoJob?.cancel(); autoJob = null
        autoSelector?.stop(); autoSelector = null
    }

    private fun switchTunnel(config: ProxyConfig) {
        if (tearingDown) return
        val store = ConfigStore.get(applicationContext)
        val json = ConfigBuilder.build(
            config, store.fragment.value, store.splitRouting.value,
            store.sniffing.value, store.sniffTypes.value,
            adBlock = store.adBlock.value,
            fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            onionRouting = store.onionRouting.value
        )
        Log.d(TAG, "switching tunnel to ${config.name}")
        pollJob?.cancel(); pollJob = null
        runCatching { Gozarcore.stop() }
        AetherController.stop()
        TorController.stop()
        runCatching { tunFd?.close() }; tunFd = null
        aetherSpec = AetherSpec.from(config)
        torSpec = if (config.protocol == "tor")
            config.torCountry + "|" + (if (config.torThroughVpn) "1" else "0") else null
        configName = config.name
        VpnState.setConnecting(config.id)
        startTunnel(json)
    }

    private fun setupGeoAssets() {
        val dir = filesDir
        runCatching {
            listOf("geoip.dat", "geosite.dat").forEach { name ->
                val out = File(dir, name)
                if (!out.exists() || out.length() == 0L) {
                    assets.open(name).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "geo assets not bundled: ${it.message}") }
        Gozarcore.setAssetPath(dir.absolutePath)
    }

    private fun applyPerApp(builder: Builder) {
        val store = ConfigStore.get(applicationContext)
        val mode = store.perAppMode.value
        val list = store.perAppList.value

        when (mode) {
            PerAppMode.ALLOWLIST -> {
                if (list.isEmpty()) {
                    runCatching { builder.addDisallowedApplication(packageName) }
                } else {
                    list.forEach { pkg ->
                        runCatching { builder.addAllowedApplication(pkg) }
                    }
                }
            }
            PerAppMode.BLOCKLIST -> {
                (list + packageName).forEach { pkg ->
                    runCatching { builder.addDisallowedApplication(pkg) }
                }
            }
            PerAppMode.OFF -> {
                runCatching { builder.addDisallowedApplication(packageName) }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var lastUp = 0L
            var lastDown = 0L
            while (isActive && !tearingDown) {
                val up = Gozarcore.queryUplink()
                val down = Gozarcore.queryDownlink()
                val upSpeed = (up - lastUp).coerceAtLeast(0L)
                val downSpeed = (down - lastDown).coerceAtLeast(0L)
                lastUp = up; lastDown = down

                VpnBridge.sendCounters(applicationContext, up, down, upSpeed, downSpeed)

                if (!tearingDown) {
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIF_ID, buildNotification(downSpeed, upSpeed))
                }

                delay(1000)
            }
        }
    }

    private fun die(error: String?) {
        if (tearingDown) return
        startJob?.cancel(); startJob = null
        val killOn = runCatching { ConfigStore.get(applicationContext).killSwitch.value }.getOrDefault(false)
        if (error != null && killOn) {
            AetherController.stop()
            TorController.stop()
            enterKillSwitch(error)
            return
        }
        tearingDown = true
        stopAutoSelect()
        pollJob?.cancel()
        pollJob = null
        runCatching { Gozarcore.stop() }
        AetherController.stop()
        TorController.stop()
        if (error != null) VpnBridge.sendError(applicationContext, error)
        else VpnBridge.sendDisconnected(applicationContext)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID) }
        stopSelf()
        // Lifecycle owns cleanup. A delayed process kill can kill a subsequent reconnect.
    }

    private fun enterKillSwitch(reason: String) {
        pollJob?.cancel(); pollJob = null
        runCatching { Gozarcore.stop() }
        runCatching { tunFd?.close() }; tunFd = null
        val b = Builder()
            .setSession("${BrandConfig.APP_NAME_FA} — مسدود")
            .setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
        applyPerApp(b)
        blockFd = runCatching { b.establish() }.getOrNull()
        VpnBridge.sendError(applicationContext, reason)
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIF_ID, buildBlockedNotification())
        }
    }

    override fun onDestroy() {
        tearingDown = true
        scope.coroutineContext[Job]?.cancel()
        stopAutoSelect()
        runCatching { Gozarcore.stop() }
        runCatching { AetherController.stop() }
        runCatching { TorController.stop() }
        runCatching { tunFd?.close() }; tunFd = null
        runCatching { blockFd?.close() }; blockFd = null
        runCatching { getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID) }
        super.onDestroy()
    }

    private fun buildNotification(downSpeed: Long = 0, upSpeed: Long = 0): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "اتصال قاجار وی پی ان", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, GozarVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val pingPi = PendingIntent.getService(
            this, 2, Intent(this, GozarVpnService::class.java).setAction(ACTION_PING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val speedLine = "$lastPingLabel   ↓ ${fmt(downSpeed)}/s   ↑ ${fmt(upSpeed)}/s"
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("قاجار وی پی ان · $configName")
            .setContentText(speedLine)
            .setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentIntent(pi)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_info_details, "بررسی پینگ", pingPi
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, stopLabel, stopPi
                ).build()
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun buildBlockedNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "اتصال قاجار وی پی ان", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, GozarVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("محافظ اتصال قاجار فعال است")
            .setContentText("اتصال قطع شد؛ اینترنت برای جلوگیری از نشت مسدود است")
            .setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentIntent(pi)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, stopLabel, stopPi
                ).build()
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun fmt(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    private fun refreshPing() {
        lastPingLabel = "پینگ: در حال بررسی…"
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification())
        scope.launch {
            val store = ConfigStore.get(applicationContext)
            val id = VpnState.activeId.value ?: store.selectedId.value
            val cfg = store.configs.value.firstOrNull { it.id == id }
            lastPingLabel = when (val result = cfg?.let { Pinger.ping(it.address, it.port, 3000) }) {
                is PingResult.Ok -> "پینگ: ${result.ms} ms"
                else -> "پینگ: ناموفق"
            }
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification())
        }
    }

    companion object {
        private const val TAG = "GozarVpnService"
        private const val CHANNEL_ID = BrandConfig.NOTIFICATION_CHANNEL_CONNECTION
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "net.gozar.app.STOP"
        const val ACTION_PING = "net.gozar.app.PING"
        const val ACTION_WARM = "net.gozar.app.WARM"
        const val EXTRA_CONFIG = "net.gozar.app.CONFIG"
        const val EXTRA_AETHER = "net.gozar.app.AETHER"
        const val EXTRA_TOR = "net.gozar.app.TOR"
        const val EXTRA_NAME = "net.gozar.app.NAME"
        const val EXTRA_STOP_LABEL = "net.gozar.app.STOP_LABEL"
    }
}

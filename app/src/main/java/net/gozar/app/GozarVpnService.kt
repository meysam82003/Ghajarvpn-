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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import java.io.File

class GozarVpnService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private var aetherSpec: AetherSpec? = null
    private var psiphonSpec: PsiphonSpec? = null
    private var oblivionOptions: OblivionOptions? = null
    @Volatile private var enginesReady = false
    private var torSpec: String? = null
    private var blockFd: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pollJob: Job? = null
    private var startJob: Job? = null
    private val engineLock = kotlinx.coroutines.sync.Mutex()
    private var configName: String = "VPN"
    private var stopLabel: String = "Disconnect"
    @Volatile private var tearingDown = false
    private var autoSelector: AutoSelector? = null
    private var autoJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Gozarcore.setLogger(object : gozarcore.Logger {
            override fun log(line: String?) {
                Log.i("XrayCore", line ?: "")
                GhajarLog.i("XrayCore", line ?: "")
            }
        })
        TorLog.sink = { line -> Log.i("XrayCore", line); GhajarLog.i("Tor", line) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                runCatching { blockFd?.close() }; blockFd = null
                die(null)
                return START_NOT_STICKY
            }
            ACTION_WARM -> {
                if (enginesReady && !tearingDown) {
                    VpnBridge.sendConnected(applicationContext)
                    return START_STICKY
                }
                return START_NOT_STICKY
            }
            else -> {
                val configJson = intent?.getStringExtra(EXTRA_CONFIG)
                if (startJob?.isActive == true || enginesReady || tunFd != null) return START_STICKY
                aetherSpec = AetherSpec.parse(intent?.getStringExtra(EXTRA_AETHER))
                psiphonSpec = PsiphonSpec.parse(intent?.getStringExtra(EXTRA_PSIPHON))
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
        // startForeground() must run unconditionally and first: this service is
        // launched with startForegroundService(), and Android requires
        // startForeground() within a few seconds of that call regardless of what
        // happens next. The old code returned early here (tunFd != null) before
        // ever reaching startForeground(), so a rapid retry storm from the UI
        // (see MainActivity.launchConnect) could start this service several
        // times without ever promoting it - triggering
        // ForegroundServiceDidNotStartInTimeException and crashing the process,
        // which is what forced a full device reboot to recover from.
        startForeground(NOTIF_ID, buildNotification())
        if (tunFd != null) return
        tearingDown = false

        startJob = scope.launch {
          engineLock.withLock {
            try {
            ensureActive()
            val optionJson = psiphonSpec?.oblivionJson ?: aetherSpec?.oblivionJson.orEmpty()
            val options = if (optionJson.isBlank()) null else OblivionOptions(optionJson).also { it.validate() }
            oblivionOptions = options
            val builder = Builder()
                .setSession("GozarNet")
                .setMtu(options?.number("mtu") ?: 1500)
                .addAddress("10.10.0.2", 32)
                .addRoute("0.0.0.0", 0)

            val resolvers = if (options?.flag("overrideDns") == true)
                listOf(options.text("dnsPrimary"),options.text("dnsSecondary")).filter { it.isNotBlank() }
                else listOf("1.1.1.1")
            resolvers.forEach { builder.addDnsServer(it) }
            if (options != null && options.text("ipVersion") != "v4") builder.addAddress("fd00::2",128).addRoute("::",0)
            applyPerApp(builder)

            val pfd = if (options?.proxyOnly == true) null else builder.establish()
            if (pfd == null && options?.proxyOnly != true) {
                die("VPN permission not granted")
                return@launch
            }
            tunFd = pfd

                setupGeoAssets()
                val spec = aetherSpec
                if (spec != null) {
                    if (!AetherController.available(applicationContext)) {
                        die("Aether engine is not bundled in this build")
                        return@launch
                    }
                    val up = kotlinx.coroutines.runInterruptible(Dispatchers.IO) {
                        AetherController.start(applicationContext, spec)
                    }
                    if (!up) {
                        AetherController.stop()
                        TorController.stop()
                        die("Aether failed to start")
                        return@launch
                    }
                }
                val psi = psiphonSpec
                if (psi != null) {
                    if (!PsiphonController.available()) {
                        die("Psiphon engine is not bundled in this build")
                        return@launch
                    }
                    // Runs in-process and needs VpnService.protect(), unlike
                    // Aether's subprocess - pass this service itself.
                    val up = kotlinx.coroutines.runInterruptible(Dispatchers.IO) {
                        PsiphonController.start(this@GozarVpnService, psi)
                    }
                    if (!up) {
                        PsiphonController.stop()
                        die("Psiphon failed to start")
                        return@launch
                    }
                }
                runCatching { Gozarcore.stop() }
                ensureActive()
                val readyJson = if (psi != null) PsiphonConfig.bindSocksPort(configJson, PsiphonController.SOCKS_PORT) else configJson
                if (pfd != null) Gozarcore.start(readyJson, pfd.detachFd().toLong())
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
                ensureActive()
                enginesReady = true
                VpnBridge.sendConnected(applicationContext)
                startPolling()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Xray core failed to start", e)
                die(e.message ?: "Engine failed to start")
            }
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
        PsiphonController.stop()
        TorController.stop()
        runCatching { tunFd?.close() }; tunFd = null
        aetherSpec = AetherSpec.from(config)
        psiphonSpec = PsiphonSpec.from(config)
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
        val options = oblivionOptions
        if (options?.flag("bypassSelected") == true) {
            val packages = options.text("bypassedApps").split(Regex("[\\s,;]+")) + packageName
            packages.filter { it.isNotBlank() }.distinct().forEach { runCatching { builder.addDisallowedApplication(it) } }
            return
        }
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
                val up = if (oblivionOptions?.proxyOnly == true) 0L else Gozarcore.queryUplink()
                val down = if (oblivionOptions?.proxyOnly == true) 0L else Gozarcore.queryDownlink()
                val upSpeed = (up - lastUp).coerceAtLeast(0L)
                val downSpeed = (down - lastDown).coerceAtLeast(0L)
                lastUp = up; lastDown = down

                VpnBridge.sendCounters(applicationContext, up, down, upSpeed, downSpeed)

                if (!tearingDown) {
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIF_ID, buildNotification(down, up, downSpeed, upSpeed))
                }

                delay(1000)
            }
        }
    }

    private fun die(error: String?) {
        if (tearingDown) return
        tearingDown = true
        enginesReady = false
        startJob?.cancel()
        scope.launch {
            engineLock.withLock {
                stopAutoSelect()
                pollJob?.cancel(); pollJob = null
                runCatching { Gozarcore.stop() }
                PsiphonController.stop()
                AetherController.stop()
                TorController.stop()
                runCatching { tunFd?.close() }; tunFd = null
                val killOn = ConfigStore.get(applicationContext).killSwitch.value
                if (error != null && killOn && oblivionOptions?.proxyOnly != true) {
                    enterKillSwitch(error)
                    tearingDown = false
                } else {
                    if (error != null) VpnBridge.sendError(applicationContext, error)
                    else VpnBridge.sendDisconnected(applicationContext)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
                    stopSelf()
                }
            }
        }
    }

    private fun enterKillSwitch(reason: String) {
        pollJob?.cancel(); pollJob = null
        runCatching { Gozarcore.stop() }
        runCatching { tunFd?.close() }; tunFd = null
        val b = Builder()
            .setSession("GozarNet (blocked)")
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
        startJob?.cancel()
        pollJob?.cancel()
        runCatching { Gozarcore.stop() }
        runCatching { tunFd?.close() }; tunFd = null
        stopAutoSelect()
        AetherController.stop()
        PsiphonController.stop()
        TorController.stop()
        runCatching { blockFd?.close() }; blockFd = null
        runCatching { getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID) }
        super.onDestroy()
    }

    private fun buildNotification(
        totalDown: Long = 0, totalUp: Long = 0,
        downSpeed: Long = 0, upSpeed: Long = 0
    ): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "GozarNet", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, GozarVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        // First line: live instantaneous speed (updates every second). Second
        // line: total data used this session so far. Both, not one replacing
        // the other.
        val speedLine = "↓ ${fmt(downSpeed)}/s   ↑ ${fmt(upSpeed)}/s"
        val usageLine = "${fmt(totalDown)} دانلود  •  ${fmt(totalUp)} آپلود"
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(configName)
            .setContentText(speedLine)
            .setStyle(Notification.BigTextStyle().bigText("$speedLine\n$usageLine"))
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

    private fun buildBlockedNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "GozarNet", NotificationManager.IMPORTANCE_LOW)
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
            .setContentTitle("Kill switch active")
            .setContentText("Connection lost — internet is blocked to prevent leaks")
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

    companion object {
        private const val TAG = "GozarVpnService"
        private const val CHANNEL_ID = "gozarnet_vpn"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "net.gozar.app.STOP"
        const val ACTION_WARM = "net.gozar.app.WARM"
        const val EXTRA_CONFIG = "net.gozar.app.CONFIG"
        const val EXTRA_AETHER = "net.gozar.app.AETHER"
        const val EXTRA_PSIPHON = "net.gozar.app.PSIPHON"
        const val EXTRA_TOR = "net.gozar.app.TOR"
        const val EXTRA_NAME = "net.gozar.app.NAME"
        const val EXTRA_STOP_LABEL = "net.gozar.app.STOP_LABEL"
    }
}
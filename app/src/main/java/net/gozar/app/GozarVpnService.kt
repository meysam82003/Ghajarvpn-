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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import java.io.File

class GozarVpnService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private var aetherSpec: AetherSpec? = null
    private var psiphonSpec: PsiphonSpec? = null
    private var oblivionOptions: OblivionOptions? = null
    @Volatile private var enginesReady = false
    @Volatile private var pendingEnd = false
    private var endError: String? = null
    private var torSpec: String? = null
    private var blockFd: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pollJob: Job? = null
    private var startJob: Job? = null
    private var configName: String = "VPN"
    private var configAddress: String = ""
    private var configPort: Int = 0
    @Volatile private var lastPingMs: Int? = null
    @Volatile private var pinging = false
    private var stopLabel: String = "Disconnect"
    @Volatile private var tearingDown = false
    private var autoSelector: AutoSelector? = null
    private var autoJob: Job? = null
    private var underlyingListener: ((NetKind?, android.net.Network?) -> Unit)? = null
    /** True when the zeptun engine, not the Xray core, owns this session's tun. */
    @Volatile private var zeptunOwnsTun = false

    // Shop notices while the tunnel is up. The scheduled job runs at most
    // every fifteen minutes (Android's floor for periodic work); a running
    // VPN is already a foreground service, so it checks every two minutes
    // and warnings and announcements arrive close to when they are sent.
    private var noticeJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        noticeJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(120_000)
                runCatching { GhajarNotificationMonitor.refresh(applicationContext) }
            }
        }
        // Nothing in here may throw. A throw from onCreate() aborts service
        // creation, and two aborts in a row make ActivityManager flag the
        // hosting process "bad" - after which every startForegroundService()
        // for this service is refused with SecurityException until the app is
        // force-stopped or the device reboots. That is exactly the state a
        // user's phone was found in (see the 2026-09-18 log: two connects,
        // both "Unable to launch app ...: process is bad").
        runCatching {
            Gozarcore.setLogger(object : gozarcore.Logger {
                override fun log(line: String?) {
                    Log.i("XrayCore", line ?: "")
                    GhajarLog.i("XrayCore", line ?: "")
                }
            })
        }.onFailure { GhajarLog.e(TAG, "core logger not attached: ${it.javaClass.name}") }
        runCatching {
            TorLog.sink = { line -> Log.i("XrayCore", line); GhajarLog.i("Tor", line) }
        }
        GhajarLog.i(TAG, "service created in process ${currentProcessName()}")
    }

    /** Recorded on every start so an exported log proves where the tunnel ran. */
    private fun currentProcessName(): String =
        if (android.os.Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName()
        else packageName

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_AETHER_CODE -> {
                val code = intent.getStringExtra(EXTRA_AETHER_CODE).orEmpty()
                if (code.matches(Regex("[0-9]{6}"))) scope.launch { runCatching { AetherController.submitEmailCode(code) } }
                return START_STICKY
            }
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
            ACTION_PING -> {
                if (enginesReady && !tearingDown) runPing()
                return START_STICKY
            }
            else -> {
                val configJson = intent?.getStringExtra(EXTRA_CONFIG)
                if (startJob?.isActive == true || enginesReady || tunFd != null) return START_STICKY
                aetherSpec = AetherSpec.parse(intent?.getStringExtra(EXTRA_AETHER))
                psiphonSpec = PsiphonSpec.parse(intent?.getStringExtra(EXTRA_PSIPHON))
                torSpec = intent?.getStringExtra(EXTRA_TOR)
                configName = intent?.getStringExtra(EXTRA_NAME) ?: "VPN"
                configAddress = intent?.getStringExtra(EXTRA_ADDRESS).orEmpty()
                configPort = intent?.getIntExtra(EXTRA_PORT, 0) ?: 0
                lastPingMs = null
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

            val store = ConfigStore.get(applicationContext)
            // In zeptun's fake-IP mode the engine runs its own resolver and
            // hands out synthetic addresses, so the tun's DNS server has to be
            // that resolver - pointing apps at a real one instead would send
            // every query past the thing meant to answer it. The address is
            // inside the tun's own 0.0.0.0/0 route, so nothing else changes.
            val fakeIpDns = options?.proxyOnly == true &&
                store.zeptunTunnel.value &&
                ZeptunEngine.available &&
                store.zeptunDns.value == ZeptunEngine.DnsMode.FAKE_IP
            val resolvers = if (fakeIpDns) listOf(ZeptunEngine.FAKE_DNS_ADDRESS)
                else if (options?.flag("overrideDns") == true)
                listOf(options.text("dnsPrimary"),options.text("dnsSecondary")).filter { it.isNotBlank() }
                else listOf("1.1.1.1")
            resolvers.forEach { builder.addDnsServer(it) }
            if (options != null && options.text("ipVersion") != "v4") builder.addAddress("fd00::2",128).addRoute("::",0)
            applyPerApp(builder)

            // Proxy-only modes (Aether or Psiphon with routingMode = proxy)
            // deliberately establish no tun: those engines publish a local
            // SOCKS5 proxy and the user points apps at it by hand. With the
            // zeptun engine present and switched on, that proxy can carry the
            // whole device instead - so a tun is established for zeptun to
            // own. Everything about the Xray path below is unchanged, and when
            // the setting is off or the engine is not in this build, pfd stays
            // null exactly as before.
            val wantZeptun = options?.proxyOnly == true &&
                store.zeptunTunnel.value &&
                ZeptunEngine.available
            val pfd = if (options?.proxyOnly == true) {
                if (wantZeptun) builder.establish() else null
            } else builder.establish()
            if (pfd == null && options?.proxyOnly != true) {
                die("VPN permission not granted")
                return@launch
            }
            if (wantZeptun && pfd == null) {
                // Asked for, and Android refused the tun. Say so rather than
                // carrying on as a proxy the user is not expecting.
                GhajarLog.e(TAG, "zeptun asked for but no tun was granted")
            }
            zeptunOwnsTun = wantZeptun && pfd != null
            tunFd = pfd
            if (pfd != null) { runCatching { blockFd?.close() }; blockFd = null }
            if (pfd != null) trackUnderlyingNetwork()

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
                // zeptun's tun is not Xray's to take: in this mode Xray is not
                // started at all (it never was in proxy-only mode), and the
                // engine forwards the tun to whichever local SOCKS proxy the
                // proxy-only engine published.
                if (zeptunOwnsTun && pfd != null) {
                    val socksPort = when {
                        psi != null -> PsiphonController.SOCKS_PORT
                        else -> AetherController.SOCKS_PORT
                    }
                    if (socksPort <= 0) {
                        die("the proxy engine published no SOCKS port")
                        return@launch
                    }
                    val failure = ZeptunEngine.start(
                        this@GozarVpnService,
                        pfd.fd,
                        ZeptunEngine.socksConfig(
                            socksPort = socksPort,
                            mtu = options?.number("mtu") ?: 1500,
                            ipv6 = options != null && options.text("ipVersion") != "v4",
                            dnsMode = store.zeptunDns.value,
                            dnsUpstream = store.zeptunDnsUpstream.value,
                            profile = store.zeptunProfile.value
                        )
                    )
                    if (failure != null) {
                        // No silent degrade to a proxy nobody is pointed at.
                        die("zeptun did not start: $failure")
                        return@launch
                    }
                }
                if (pfd != null && !zeptunOwnsTun) {
                    val fd = pfd.detachFd().toLong()
                    var bindAttempt = 0
                    while (true) {
                        try {
                            Gozarcore.start(readyJson, fd)
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // engineLock now serializes this against any other instance's
                            // teardown, but Xray-core's own socket close can still finish
                            // a beat after Gozarcore.stop() returns. Retry a couple of
                            // times only for that specific failure before giving up.
                            val portBusy = e.message?.contains("address already in use", ignoreCase = true) == true
                            if (!portBusy || bindAttempt >= 2) throw e
                            bindAttempt++
                            Log.w(TAG, "mixed-inbound port still held, retry $bindAttempt/2 in 400ms")
                            runCatching { Gozarcore.stop() }
                            delay(400)
                        }
                    }
                }
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
        val sharing = store.vpnShareEnabled.value
        val shareCredential = if (sharing) store.ensureVpnShareCredential() else null
        val json = ConfigBuilder.build(
            config, store.fragment.value, store.splitRouting.value,
            store.sniffing.value, store.sniffTypes.value,
            adBlock = store.adBlock.value,
            fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            customDns = store.customDns.value,
            youtubeDirect = store.youtubeDirect.value,
            noiseSpec = store.noiseSpec.value,
            fragmentPackets = store.fragmentPackets.value,
            fragmentLength = store.fragmentLength.value,
            fragmentInterval = store.fragmentInterval.value,
            onionRouting = store.onionRouting.value,
            shareOnLan = sharing,
            shareUser = shareCredential?.first.orEmpty(),
            sharePass = shareCredential?.second.orEmpty(),
            shareListenAddress = if (sharing) hotspotInterfaceAddress() ?: "127.0.0.1" else "127.0.0.1"
        )
        Log.d(TAG, "switching tunnel to ${config.name}")
        pollJob?.cancel(); pollJob = null
        if (zeptunOwnsTun) { ZeptunEngine.stop(); zeptunOwnsTun = false }
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
        configAddress = config.address
        configPort = config.port
        lastPingMs = null
        GhajarWidget.lastPingMs = null
        GhajarWidget.sessionDownBytes = 0L
        GhajarWidget.sessionUpBytes = 0L
        VpnState.setConnecting(config.id)
        startTunnel(json)
    }

    /** A real TCP-connect round trip to the active server (same mechanism
     * Pinger already uses elsewhere in the app), triggered by the notification's
     * "پینگ" action. Never fabricated: a failed probe clears the shown value
     * instead of keeping a stale number on screen. */
    private fun runPing() {
        if (pinging || configAddress.isBlank() || configPort <= 0) return
        pinging = true
        scope.launch {
            val result = Pinger.ping(configAddress, configPort)
            lastPingMs = (result as? PingResult.Ok)?.ms
            // The widget shows a ping only when one was actually measured, so
            // it is fed from here rather than measuring on its own - a
            // RemoteViews update has no business doing network work.
            GhajarWidget.lastPingMs = lastPingMs
            GhajarWidget.refresh(applicationContext)
            pinging = false
            if (!tearingDown) {
                runCatching {
                    getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification())
                }
            }
        }
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

                // The widget's session totals come from here, where they are
                // already measured. It is fed rather than measuring for itself
                // because the only figure a RemoteViews update could reach on
                // its own is TrafficStats, which is device-wide - every other
                // app's traffic reported as this tunnel's.
                GhajarWidget.sessionDownBytes = down
                GhajarWidget.sessionUpBytes = up

                if (!tearingDown) {
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIF_ID, buildNotification(down, up, downSpeed, upSpeed))
                }
                // Once every ten seconds, not every one: a widget update is an
                // IPC to the launcher, and sixty of them a minute for a number
                // nobody is watching is a real battery cost.
                if (widgetTick++ % 10 == 0) GhajarWidget.refresh(applicationContext)

                delay(1000)
            }
        }
    }

    private fun die(error: String?) {
        if (tearingDown) return
        tearingDown = true
        enginesReady = false
        startJob?.cancel()
        untrackUnderlyingNetwork()
        scope.launch {
            engineLock.withLock {
                stopAutoSelect()
                pollJob?.cancel(); pollJob = null
                // Before the descriptor is closed: the engine is still reading
                // from it, and closing it underneath a running engine is how a
                // teardown turns into a crash.
                if (zeptunOwnsTun) { ZeptunEngine.stop(); zeptunOwnsTun = false }
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
                    endError = error
                    pendingEnd = true
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
                    stopSelf()
                }
            }
        }
    }

    /**
     * Keeps the tunnel pointed at the network that actually carries it.
     *
     * A VpnService that never calls setUnderlyingNetworks leaves the framework
     * to guess, and its guess is the network that was default when the tunnel
     * was built. So when Wi-Fi dropped and the SIM took over, the tun's
     * accounting, its connectivity reporting and its socket binding all still
     * referred to a network that no longer existed - the tunnel looked up and
     * carried nothing, which is what "the app does not recover when Wi-Fi
     * drops" actually was.
     *
     * Registered once per established tunnel and removed on teardown. Null
     * means "no opinion, use the default", which is the right answer while the
     * device is between networks.
     */
    private fun trackUnderlyingNetwork() {
        if (underlyingListener != null) return
        val listener: (NetKind?, android.net.Network?) -> Unit = { kind, network ->
            runCatching {
                setUnderlyingNetworks(network?.let { arrayOf(it) })
                GhajarLog.i(TAG, "tunnel now rides $kind")
            }.onFailure { GhajarLog.e(TAG, "underlying network not set: ${it.javaClass.simpleName}") }
        }
        underlyingListener = listener
        NetworkWatcher.initialize(applicationContext)
        NetworkWatcher.addListener(listener)
        // Apply whatever is current right now, not only the next change.
        listener(NetworkWatcher.kind.value, NetworkWatcher.currentNetwork())
    }

    private fun untrackUnderlyingNetwork() {
        underlyingListener?.let { NetworkWatcher.removeListener(it) }
        underlyingListener = null
    }

    private fun enterKillSwitch(reason: String) {
        pollJob?.cancel(); pollJob = null
        if (zeptunOwnsTun) { ZeptunEngine.stop(); zeptunOwnsTun = false }
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
        noticeJob?.cancel()
        tearingDown = true
        enginesReady = false
        startJob?.cancel()
        pollJob?.cancel()
        stopAutoSelect()
        untrackUnderlyingNetwork()
        if (zeptunOwnsTun) { ZeptunEngine.stop(); zeptunOwnsTun = false }
        // Report completion only after native teardown. A new UI retry cannot
        // start a tunnel that this older service instance is still stopping.
        scope.launch {
            engineLock.withLock {
                runCatching { Gozarcore.stop() }
                AetherController.stop()
                PsiphonController.stop()
                TorController.stop()
                runCatching { tunFd?.close() }; tunFd = null
                runCatching { blockFd?.close() }; blockFd = null
                runCatching { getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID) }
            }
            if (pendingEnd && endError != null) VpnBridge.sendError(applicationContext, endError!!)
            else VpnBridge.sendDisconnected(applicationContext)
            scope.cancel()
        }
        super.onDestroy()
    }

    /** Counts the one-second ticks, so the widget is refreshed on every tenth. */
    private var widgetTick = 0

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
        val pingPi = PendingIntent.getService(
            this, 2, Intent(this, GozarVpnService::class.java).setAction(ACTION_PING),
            PendingIntent.FLAG_IMMUTABLE
        )
        // First line: live instantaneous speed (updates every second). Second
        // line: total data used this session so far. Both, not one replacing
        // the other.
        val speedLine = "↓ ${fmt(downSpeed)}/s   ↑ ${fmt(upSpeed)}/s"
        val usageLine = "${fmt(totalDown)} دانلود  •  ${fmt(totalUp)} آپلود"
        val titleWithPing = lastPingMs?.let { "$configName · ${it}ms" } ?: configName
        val pingLabel = if (pinging) "در حال تست…" else "پینگ"
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(titleWithPing)
            .setContentText(speedLine)
            .setStyle(Notification.BigTextStyle().bigText("$speedLine\n$usageLine"))
            .setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentIntent(pi)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_rotate, pingLabel, pingPi
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
        // Shared across every GozarVpnService instance in the process, not
        // per-instance. Android creates a brand-new instance (fresh onCreate())
        // for each startForegroundService() call once the previous one has been
        // stopSelf()'d - a new instance's own field would be a fresh, unrelated
        // Mutex that provides zero exclusion against a still-in-flight teardown
        // from the instance being replaced. That gap let a reconnect/server-switch
        // call Gozarcore.start() while the old instance's onDestroy() coroutine
        // was still calling Gozarcore.stop(), producing the observed
        // "bind: address already in use" on the mixed-inbound port (10626).
        private val engineLock = kotlinx.coroutines.sync.Mutex()
        const val ACTION_AETHER_CODE = "net.gozar.app.AETHER_CODE"
        const val EXTRA_AETHER_CODE = "net.gozar.app.AETHER_EMAIL_CODE"
        const val ACTION_STOP = "net.gozar.app.STOP"
        const val ACTION_WARM = "net.gozar.app.WARM"
        const val ACTION_PING = "net.gozar.app.PING"
        const val EXTRA_CONFIG = "net.gozar.app.CONFIG"
        const val EXTRA_AETHER = "net.gozar.app.AETHER"
        const val EXTRA_PSIPHON = "net.gozar.app.PSIPHON"
        const val EXTRA_TOR = "net.gozar.app.TOR"
        const val EXTRA_NAME = "net.gozar.app.NAME"
        const val EXTRA_STOP_LABEL = "net.gozar.app.STOP_LABEL"
        const val EXTRA_ADDRESS = "net.gozar.app.ADDRESS"
        const val EXTRA_PORT = "net.gozar.app.PORT"
    }
}
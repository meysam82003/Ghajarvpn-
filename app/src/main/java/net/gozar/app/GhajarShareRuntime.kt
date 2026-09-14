package net.gozar.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** Runtime for Ghajar VPN Share. No client-side app is required. */
enum class GhajarShareMode { VPN_ONLY, HYBRID, SHARE_ONLY }

data class GhajarHotspotInfo(
    val active: Boolean = false,
    val ssid: String = "",
    val password: String = "",
    val gateway: String = "",
    val error: String? = null
)

data class GhajarShareClient(
    val ip: String,
    val uploaded: Long = 0L,
    val downloaded: Long = 0L,
    val connectedAt: Long = 0L,
    val activeConnections: Int = 0,
    val blocked: Boolean = false,
    val quotaBytes: Long = 0L,
    val timeLimitMs: Long = 0L,
    val speedLimitKbps: Int = 0
) {
    val usedBytes: Long get() = uploaded + downloaded
    val remainingBytes: Long get() = if (quotaBytes <= 0L) Long.MAX_VALUE else (quotaBytes - usedBytes).coerceAtLeast(0)
    val remainingMs: Long get() = if (timeLimitMs <= 0L || connectedAt <= 0L) Long.MAX_VALUE else
        (connectedAt + timeLimitMs - System.currentTimeMillis()).coerceAtLeast(0)
    val expired: Boolean get() = blocked || (quotaBytes > 0L && usedBytes >= quotaBytes) ||
        (timeLimitMs > 0L && connectedAt > 0L && System.currentTimeMillis() >= connectedAt + timeLimitMs)
}

data class GhajarShareState(
    val running: Boolean = false,
    val mode: GhajarShareMode = GhajarShareMode.VPN_ONLY,
    val proxyHost: String = "",
    val proxyPort: Int = 8787,
    val setupPort: Int = 8788,
    val startedAt: Long = 0L,
    val clients: Map<String, GhajarShareClient> = emptyMap(),
    val error: String? = null
)

object GhajarShareRuntime {
    private val _state = MutableStateFlow(GhajarShareState())
    val state: StateFlow<GhajarShareState> = _state.asStateFlow()

    internal fun update(transform: (GhajarShareState) -> GhajarShareState) { _state.value = transform(_state.value) }
    internal fun setClients(clients: Map<String, GhajarShareClient>) = update { it.copy(clients = clients) }

    fun startProxy(context: Context, mode: GhajarShareMode) {
        val i = Intent(context, GhajarShareProxyService::class.java)
            .setAction(GhajarShareProxyService.ACTION_START)
            .putExtra(GhajarShareProxyService.EXTRA_MODE, mode.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, GhajarShareProxyService::class.java).setAction(GhajarShareProxyService.ACTION_STOP))
        GhajarLocalHotspot.stop()
        GhajarShareRouting.restore(context)
    }

    fun openTetherSettings(context: Context) {
        val i = Intent(Settings.ACTION_TETHER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun privateGateway(): String {
        val preferred = listOf("ap", "softap", "swlan", "wlan", "p2p")
        val candidates = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { ni ->
                Collections.list(ni.inetAddresses).mapNotNull { a ->
                    if (a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress)
                        Triple(ni.name.lowercase(), a.hostAddress.orEmpty(), scoreInterface(ni.name.lowercase(), preferred))
                    else null
                }
            }
        }.getOrDefault(emptyList())
        return candidates.maxByOrNull { it.third }?.second.orEmpty()
    }

    private fun scoreInterface(name: String, preferred: List<String>): Int {
        var score = 0
        preferred.forEachIndexed { idx, p -> if (name.contains(p)) score = max(score, 100 - idx * 10) }
        return score
    }
}

object GhajarSharePrefs {
    private const val NAME = "ghajar_share"
    private fun p(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun proxyPort(context: Context) = p(context).getInt("proxy_port", 8787).coerceIn(1024, 65535)
    fun setupPort(context: Context) = p(context).getInt("setup_port", 8788).coerceIn(1024, 65535)
    fun maxClients(context: Context) = p(context).getInt("max_clients", 3).coerceIn(1, 32)
    fun setMaxClients(context: Context, v: Int) = p(context).edit().putInt("max_clients", v.coerceIn(1, 32)).apply()
    fun defaultQuota(context: Context) = p(context).getLong("default_quota", 0L).coerceAtLeast(0L)
    fun defaultTime(context: Context) = p(context).getLong("default_time", 0L).coerceAtLeast(0L)
    fun defaultSpeed(context: Context) = p(context).getInt("default_speed", 0).coerceAtLeast(0)
    fun setDefaults(context: Context, quota: Long, time: Long, speed: Int) = p(context).edit()
        .putLong("default_quota", quota.coerceAtLeast(0L)).putLong("default_time", time.coerceAtLeast(0L))
        .putInt("default_speed", speed.coerceAtLeast(0)).apply()

    fun policy(context: Context, ip: String): Triple<Long, Long, Int> {
        val raw = p(context).getString("policy_$ip", null) ?: return Triple(defaultQuota(context), defaultTime(context), defaultSpeed(context))
        return runCatching {
            val o = JSONObject(raw)
            Triple(o.optLong("quota", defaultQuota(context)), o.optLong("time", defaultTime(context)), o.optInt("speed", defaultSpeed(context)))
        }.getOrDefault(Triple(defaultQuota(context), defaultTime(context), defaultSpeed(context)))
    }

    fun setPolicy(context: Context, ip: String, quota: Long, time: Long, speed: Int) {
        val o = JSONObject().put("quota", quota.coerceAtLeast(0L)).put("time", time.coerceAtLeast(0L)).put("speed", speed.coerceAtLeast(0))
        p(context).edit().putString("policy_$ip", o.toString()).apply()
    }

    fun blocked(context: Context, ip: String) = p(context).getBoolean("blocked_$ip", false)
    fun setBlocked(context: Context, ip: String, v: Boolean) = p(context).edit().putBoolean("blocked_$ip", v).apply()
}

/** Local-only hotspot for fail-closed sharing. */
object GhajarLocalHotspot {
    private val _info = MutableStateFlow(GhajarHotspotInfo())
    val info: StateFlow<GhajarHotspotInfo> = _info.asStateFlow()
    @Volatile private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    @Suppress("DEPRECATION")
    fun start(context: Context) {
        if (reservation != null) return
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        _info.value = GhajarHotspotInfo(error = null)
        val cb = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                reservation = r
                val legacy = runCatching { r.wifiConfiguration }.getOrNull()
                val ssid: String
                val pass: String
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val c = runCatching { r.softApConfiguration }.getOrNull()
                    ssid = c?.ssid ?: legacy?.SSID.orEmpty().trim('"')
                    pass = c?.passphrase ?: legacy?.preSharedKey.orEmpty().trim('"')
                } else {
                    ssid = legacy?.SSID.orEmpty().trim('"')
                    pass = legacy?.preSharedKey.orEmpty().trim('"')
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    _info.value = GhajarHotspotInfo(true, ssid, pass, GhajarShareRuntime.privateGateway())
                }, 550)
            }
            override fun onStopped() { reservation = null; _info.value = GhajarHotspotInfo() }
            override fun onFailed(reason: Int) {
                reservation = null
                _info.value = GhajarHotspotInfo(error = "Local hotspot failed ($reason)")
            }
        }
        runCatching { wifi.startLocalOnlyHotspot(cb, Handler(Looper.getMainLooper())) }
            .onFailure { _info.value = GhajarHotspotInfo(error = it.message ?: "Hotspot unavailable") }
    }

    fun stop() { runCatching { reservation?.close() }; reservation = null; _info.value = GhajarHotspotInfo() }
}

/** Temporarily excludes all apps on phone A while keeping the Ghajar core alive for Share clients. */
object GhajarShareRouting {
    private const val PREF = "ghajar_share_routing"
    fun enterShareOnly(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!sp.getBoolean("saved", false)) {
            val store = ConfigStore.get(context)
            sp.edit().putBoolean("saved", true)
                .putString("mode", store.perAppMode.value.name)
                .putStringSet("list", store.perAppList.value)
                .apply()
        }
        val all = runCatching { context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet() }
            .getOrDefault(setOf(context.packageName))
        ConfigStore.get(context).setPerAppList(all)
        ConfigStore.get(context).setPerAppMode(PerAppMode.BLOCKLIST)
    }

    fun restore(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!sp.getBoolean("saved", false)) return
        val store = ConfigStore.get(context)
        val mode = runCatching { PerAppMode.valueOf(sp.getString("mode", "OFF") ?: "OFF") }.getOrDefault(PerAppMode.OFF)
        store.setPerAppMode(mode)
        store.setPerAppList(sp.getStringSet("list", emptySet())?.toSet() ?: emptySet())
        sp.edit().clear().apply()
    }
}

class GhajarShareProxyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var setupJob: Job? = null
    private var server: ServerSocket? = null
    private var setupServer: ServerSocket? = null
    private val clients = ConcurrentHashMap<String, MutableClient>()
    @Volatile private var mode = GhajarShareMode.VPN_ONLY

    private data class MutableClient(
        val ip: String,
        var uploaded: Long = 0L,
        var downloaded: Long = 0L,
        var connectedAt: Long = System.currentTimeMillis(),
        var activeConnections: Int = 0,
        var blocked: Boolean = false,
        var quotaBytes: Long = 0L,
        var timeLimitMs: Long = 0L,
        var speedLimitKbps: Int = 0
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopShare()
            else -> {
                mode = runCatching { GhajarShareMode.valueOf(intent?.getStringExtra(EXTRA_MODE) ?: GhajarShareMode.VPN_ONLY.name) }
                    .getOrDefault(GhajarShareMode.VPN_ONLY)
                startForeground(NOTIF_ID, notification())
                startServers()
            }
        }
        return START_STICKY
    }

    private fun startServers() {
        if (acceptJob?.isActive == true) return
        val proxyPort = GhajarSharePrefs.proxyPort(this)
        val setupPort = GhajarSharePrefs.setupPort(this)
        acceptJob = scope.launch {
            try {
                server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress("0.0.0.0", proxyPort)) }
                GhajarShareRuntime.update { it.copy(true, mode, GhajarShareRuntime.privateGateway(), proxyPort, setupPort, System.currentTimeMillis(), error = null) }
                while (isActive) {
                    val client = server?.accept() ?: break
                    launch { handle(client) }
                }
            } catch (t: Throwable) {
                GhajarShareRuntime.update { it.copy(running = false, error = t.message ?: "Share proxy failed") }
            }
        }
        setupJob = scope.launch {
            try {
                setupServer = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress("0.0.0.0", setupPort)) }
                while (isActive) {
                    val s = setupServer?.accept() ?: break
                    launch { serveSetup(s) }
                }
            } catch (_: Throwable) { }
        }
        scope.launch {
            while (isActive) {
                publish()
                updateNotification()
                delay(1000)
            }
        }
    }

    private fun allowedSource(a: InetAddress?): Boolean = a != null && (a.isSiteLocalAddress || a.isLinkLocalAddress || a.isLoopbackAddress)

    private suspend fun handle(socket: Socket) {
        socket.use { client ->
            if (!allowedSource(client.inetAddress)) return
            if (VpnState.state.value != Connection.CONNECTED) {
                runCatching { client.getOutputStream().write("HTTP/1.1 503 VPN Required\r\nConnection: close\r\n\r\n".toByteArray()) }
                return
            }
            val ip = client.inetAddress.hostAddress ?: return
            val mc = clients.computeIfAbsent(ip) {
                val (q, t, s) = GhajarSharePrefs.policy(this, ip)
                MutableClient(ip, quotaBytes = q, timeLimitMs = t, speedLimitKbps = s, blocked = GhajarSharePrefs.blocked(this, ip))
            }
            if (!canUse(mc)) {
                runCatching { client.getOutputStream().write("HTTP/1.1 403 Share Limit Reached\r\nConnection: close\r\n\r\n".toByteArray()) }
                return
            }
            val activeIps = clients.values.count { it.activeConnections > 0 && !it.blocked }
            if (mc.activeConnections == 0 && activeIps >= GhajarSharePrefs.maxClients(this)) {
                client.getOutputStream().write("HTTP/1.1 429 Too Many Devices\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            mc.activeConnections++
            try {
                val input = BufferedInputStream(client.getInputStream())
                val header = readHeader(input) ?: return
                val lines = header.split("\r\n")
                val first = lines.firstOrNull()?.split(' ') ?: return
                if (first.size < 2) return
                val method = first[0].uppercase()
                if (method == "CONNECT") {
                    val hp = first[1].split(':', limit = 2)
                    val host = hp[0]
                    val port = hp.getOrNull(1)?.toIntOrNull() ?: 443
                    val upstream = socksConnect(host, port) ?: run {
                        client.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray()); return
                    }
                    upstream.use { up ->
                        client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\nProxy-Agent: GhajarVPN\r\n\r\n".toByteArray())
                        relayBoth(client, up, mc)
                    }
                } else {
                    val uri = runCatching { URI(first[1]) }.getOrNull()
                    val hostHeader = lines.firstOrNull { it.startsWith("Host:", true) }?.substringAfter(':')?.trim().orEmpty()
                    val host = uri?.host ?: hostHeader.substringBefore(':')
                    val port = if (uri?.port != null && uri.port > 0) uri.port else hostHeader.substringAfter(':', "80").toIntOrNull() ?: 80
                    if (host.isBlank()) return
                    val upstream = socksConnect(host, port) ?: return
                    upstream.use { up ->
                        val path = uri?.rawPath?.ifBlank { "/" } ?: first[1]
                        val query = uri?.rawQuery?.let { "?$it" }.orEmpty()
                        val rebuilt = buildString {
                            append(method).append(' ').append(path).append(query).append(' ').append(first.getOrElse(2) { "HTTP/1.1" }).append("\r\n")
                            lines.drop(1).filter { it.isNotBlank() && !it.startsWith("Proxy-Connection:", true) }.forEach { append(it).append("\r\n") }
                            append("\r\n")
                        }.toByteArray(StandardCharsets.ISO_8859_1)
                        up.getOutputStream().write(rebuilt)
                        mc.uploaded += rebuilt.size
                        relayBoth(client, up, mc)
                    }
                }
            } finally {
                mc.activeConnections = (mc.activeConnections - 1).coerceAtLeast(0)
                publish()
            }
        }
    }

    private fun canUse(c: MutableClient): Boolean {
        c.blocked = c.blocked || GhajarSharePrefs.blocked(this, c.ip)
        val (q, t, s) = GhajarSharePrefs.policy(this, c.ip)
        c.quotaBytes = q; c.timeLimitMs = t; c.speedLimitKbps = s
        if (c.blocked) return false
        if (q > 0 && c.uploaded + c.downloaded >= q) return false
        if (t > 0 && System.currentTimeMillis() >= c.connectedAt + t) return false
        return true
    }

    private fun readHeader(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        var last4 = 0
        while (out.size() < 32 * 1024) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            last4 = ((last4 shl 8) or (b and 0xff))
            if (last4 == 0x0d0a0d0a) break
        }
        return out.toString(StandardCharsets.ISO_8859_1.name())
    }

    private fun socksConnect(host: String, port: Int): Socket? = runCatching {
        val s = Socket()
        s.connect(InetSocketAddress("127.0.0.1", MixedPort.value), 8000)
        s.soTimeout = 15000
        val input = BufferedInputStream(s.getInputStream())
        val output = BufferedOutputStream(s.getOutputStream())
        output.write(byteArrayOf(5, 1, 0)); output.flush()
        if (input.read() != 5 || input.read() == 0xff) error("SOCKS auth failed")
        val hostBytes = host.toByteArray(StandardCharsets.UTF_8)
        require(hostBytes.size <= 255)
        output.write(byteArrayOf(5, 1, 0, 3, hostBytes.size.toByte()))
        output.write(hostBytes)
        output.write(byteArrayOf((port shr 8).toByte(), port.toByte()))
        output.flush()
        if (input.read() != 5 || input.read() != 0) error("SOCKS connect failed")
        input.read()
        when (input.read()) {
            1 -> repeat(4) { input.read() }
            3 -> { val len = input.read(); repeat(len) { input.read() } }
            4 -> repeat(16) { input.read() }
            else -> error("Bad SOCKS reply")
        }
        input.read(); input.read()
        s.soTimeout = 0
        s
    }.getOrNull()

    private suspend fun relayBoth(client: Socket, upstream: Socket, c: MutableClient) {
        val a = scope.launch { copyLimited(client, upstream, c, upload = true) }
        val b = scope.launch { copyLimited(upstream, client, c, upload = false) }
        a.join(); runCatching { upstream.shutdownOutput() }
        b.join(); runCatching { client.shutdownOutput() }
    }

    private suspend fun copyLimited(from: Socket, to: Socket, c: MutableClient, upload: Boolean) {
        val input = from.getInputStream(); val output = to.getOutputStream(); val buf = ByteArray(16 * 1024)
        while (scope.isActive && canUse(c) && VpnState.state.value == Connection.CONNECTED) {
            val n = runCatching { input.read(buf) }.getOrElse { -1 }
            if (n <= 0) break
            output.write(buf, 0, n); output.flush()
            if (upload) c.uploaded += n else c.downloaded += n
            val kbps = c.speedLimitKbps
            if (kbps > 0) {
                val delayMs = ((n * 8_000L) / (kbps * 1000L)).coerceAtMost(2_000L)
                if (delayMs > 0) delay(delayMs)
            }
        }
    }

    private fun publish() {
        val snapshot = clients.mapValues { (_, c) ->
            GhajarShareClient(c.ip, c.uploaded, c.downloaded, c.connectedAt, c.activeConnections, c.blocked,
                c.quotaBytes, c.timeLimitMs, c.speedLimitKbps)
        }
        GhajarShareRuntime.setClients(snapshot)
    }

    private suspend fun serveSetup(s: Socket) {
        s.use { socket ->
            if (!allowedSource(socket.inetAddress)) return
            val input = BufferedInputStream(socket.getInputStream())
            val header = readHeader(input) ?: return
            val path = header.lineSequence().firstOrNull()?.split(' ')?.getOrNull(1) ?: "/"
            val hot = GhajarLocalHotspot.info.value
            val host = GhajarShareRuntime.privateGateway().ifBlank { GhajarShareRuntime.state.value.proxyHost }
            val proxyPort = GhajarSharePrefs.proxyPort(this)
            if (path.startsWith("/ghajar.mobileconfig")) {
                val profile = mobileConfig(hot.ssid, hot.password, host, proxyPort)
                respond(socket, "application/x-apple-aspen-config", profile)
            } else {
                val html = """<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{background:#050807;color:#eefbf5;font-family:sans-serif;padding:28px}main{max-width:620px;margin:auto}.c{background:#101816;border:1px solid #1e3a31;border-radius:20px;padding:18px;margin:14px 0}.g{color:#24D98B}a{display:block;background:#00A86B;color:white;padding:14px;border-radius:14px;text-align:center;text-decoration:none;font-weight:700}</style></head><body><main><h1>Ghajar VPN Share</h1><div class='c'><b class='g'>Wi‑Fi</b><p>${escape(hot.ssid)}</p><b>Password</b><p>${escape(hot.password)}</p></div><div class='c'><b class='g'>Proxy</b><p>Host: $host</p><p>Port: $proxyPort</p></div><a href='/ghajar.mobileconfig'>Install iPhone / iPad profile</a><div class='c'><b>Android / Windows / macOS / Linux</b><p>Connect to the Wi‑Fi above and set a manual HTTP/HTTPS proxy to <b>$host:$proxyPort</b>.</p></div></main></body></html>"""
                respond(socket, "text/html; charset=utf-8", html)
            }
        }
    }

    private fun mobileConfig(ssid: String, password: String, host: String, port: Int): String {
        val uuid = UUID.randomUUID().toString().uppercase()
        return """<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict><key>PayloadContent</key><array><dict><key>AutoJoin</key><true/><key>EncryptionType</key><string>WPA</string><key>Password</key><string>${xml(password)}</string><key>SSID_STR</key><string>${xml(ssid)}</string><key>ProxyType</key><string>Manual</string><key>ProxyServer</key><string>${xml(host)}</string><key>ProxyServerPort</key><integer>$port</integer><key>PayloadDescription</key><string>Ghajar VPN Share Wi-Fi</string><key>PayloadDisplayName</key><string>Ghajar VPN Share</string><key>PayloadIdentifier</key><string>com.ghajarvpn.share.wifi</string><key>PayloadType</key><string>com.apple.wifi.managed</string><key>PayloadUUID</key><string>$uuid</string><key>PayloadVersion</key><integer>1</integer></dict></array><key>PayloadDisplayName</key><string>Ghajar VPN Share</string><key>PayloadIdentifier</key><string>com.ghajarvpn.share</string><key>PayloadRemovalDisallowed</key><false/><key>PayloadType</key><string>Configuration</string><key>PayloadUUID</key><string>${UUID.randomUUID().toString().uppercase()}</string><key>PayloadVersion</key><integer>1</integer></dict></plist>"""
    }

    private fun respond(s: Socket, type: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val head = "HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()
        s.getOutputStream().write(head); s.getOutputStream().write(bytes); s.getOutputStream().flush()
    }
    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun xml(s: String) = escape(s).replace("\"", "&quot;").replace("'", "&apos;")

    private fun stopShare() {
        acceptJob?.cancel(); setupJob?.cancel(); acceptJob = null; setupJob = null
        runCatching { server?.close() }; runCatching { setupServer?.close() }
        server = null; setupServer = null
        clients.clear()
        GhajarLocalHotspot.stop()
        GhajarShareRouting.restore(this)
        GhajarShareRuntime.update { GhajarShareState() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        acceptJob?.cancel(); setupJob?.cancel()
        runCatching { server?.close() }; runCatching { setupServer?.close() }
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Ghajar VPN Share", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
    private fun notification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, PremiumMainActivity::class.java).putExtra("open_share", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, GhajarShareProxyService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentTitle("Ghajar VPN Share").setContentText("VPN sharing is active")
            .setContentIntent(pi).setOngoing(true).addAction(0, "Stop Share", stop).build()
    }
    private fun updateNotification() {
        val count = clients.values.count { it.activeConnections > 0 }
        val used = clients.values.sumOf { it.uploaded + it.downloaded }
        val text = "$count device${if (count == 1) "" else "s"} • ${fmt(used)}"
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_ghajar)
                .setContentTitle("Ghajar VPN Share").setContentText(text).setOngoing(true).build())
    }
    private fun fmt(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    }

    companion object {
        const val ACTION_START = "net.gozar.app.SHARE_START"
        const val ACTION_STOP = "net.gozar.app.SHARE_STOP"
        const val EXTRA_MODE = "mode"
        private const val CHANNEL = "ghajar_share"
        private const val NOTIF_ID = 7070
    }
}

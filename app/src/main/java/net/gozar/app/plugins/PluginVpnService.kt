package net.gozar.app.plugins

import android.app.*
import android.content.Intent
import android.net.*
import android.os.*
import kotlinx.coroutines.*
import net.gozar.app.*
import net.gozar.plugin.api.PluginWire
import java.net.InetSocketAddress
import java.net.Socket

/** Host owns VPN permission/TUN/notification; an explicitly trusted APK owns only its engine. */
class PluginVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var tun: ParcelFileDescriptor? = null
    private var rpc: PluginRpc? = null
    private var zeptun = false
    private val token = Binder()
    private val stopping = java.util.concurrent.atomic.AtomicBoolean(false)
    private val cleaned = java.util.concurrent.atomic.AtomicBoolean(false)
    private var blackhole: (() -> ParcelFileDescriptor?)? = null
    private var network: ConnectivityManager.NetworkCallback? = null
    override fun onCreate() { super.onCreate(); PluginRuntime.attach(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A queued foreground start may race with STOP: always satisfy Android's foreground deadline.
        startForeground(7410, PluginNotifications.tunnel(this))
        if (intent?.action == STOP || intent?.getStringExtra("configId") == null || !PluginRuntime.isCurrent(intent.getLongExtra("generation", -1))) { requestStop(); return START_NOT_STICKY }
        if (job != null || stopping.get()) return START_NOT_STICKY
        val id = intent.getStringExtra("configId")!!
        job = scope.launch {
            var failure: String? = null
            try {
                val store = ConfigStore.get(applicationContext); store.awaitReady()
                val config = store.configs.value.singleOrNull { it.id == id } ?: error("Config missing")
                val profile = requireNotNull(PluginProfiles.read(config))
                require(PluginRuntime.isUsing(profile.id)) { "Plugin lease missing" }
                val release = PluginManager.get(this@PluginVpnService).active(profile.id) ?: error("Plugin missing")
                val client = PluginRpc(this@PluginVpnService, release); rpc = client
                GhajarLog.i("Plugin", "Opening verified ${release.id} ${release.version}")
                client.open(); client.health()
                val plan = client.configCall(PluginWire.PREPARE, profile)
                val mode = plan.getString("mode")
                require(mode == "tun" || mode == "socks") { "Invalid engine mode" }
                val builder = Builder().setSession("Ghajar · ${release.name}")
                val mtu = plan.getInt("mtu", 1500); require(mtu in 1280..9000); builder.setMtu(mtu)
                if (mode == "tun") {
                    require(release.capabilities.supportsTun && release.capabilities.supportsFullConfig)
                    applyPlan(builder, plan, release.capabilities)
                } else {
                    require(release.capabilities.supportsSocks && ZeptunEngine.available)
                    // TCP-only SOCKS cannot promise system DNS over UDP.
                    require(release.capabilities.supportsUdp) { "API 1 SOCKS VPN requires UDP support" }
                    builder.addAddress("10.10.0.2", 32).addRoute("0.0.0.0", 0)
                    if (release.capabilities.supportsIPv6) builder.addAddress("fd00::2", 128).addRoute("::", 0)
                    builder.addDnsServer(if (store.zeptunDns.value == ZeptunEngine.DnsMode.FAKE_IP) ZeptunEngine.FAKE_DNS_ADDRESS else "1.1.1.1")
                }
                applyApps(builder, store, release.packageName)
                // No allowBypass/allowFamily fallback: unsupported families remain blocked by Android.
                blackhole = { builder.establish() }
                tun = builder.establish() ?: error("VPN permission revoked")
                val started = if (mode == "tun") {
                    ParcelFileDescriptor.dup(tun!!.fileDescriptor).use { fd -> client.configCall(PluginWire.START, profile, fd, token) }
                } else client.configCall(PluginWire.START, profile, hostToken = token)
                require(started.getBoolean("ready")) { "Engine not ready" }
                if (mode == "socks") {
                    require(started.getString("socksHost") == "127.0.0.1") { "SOCKS must be loopback only" }
                    val port = started.getInt("socksPort"); require(port in 1..65535)
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", port), 3000); socket.soTimeout = 3000
                        socket.getOutputStream().write(byteArrayOf(5, 1, 0))
                        require(socket.getInputStream().read() == 5 && socket.getInputStream().read() == 0) { "SOCKS handshake failed" }
                    }
                    zeptun = true // cleanup even if native start partially fails
                    val error = ZeptunEngine.start(this@PluginVpnService, tun!!.fd, ZeptunEngine.socksConfig(
                        socksPort = port, mtu = mtu, ipv6 = release.capabilities.supportsIPv6,
                        dnsMode = store.zeptunDns.value, dnsUpstream = store.zeptunDnsUpstream.value, profile = store.zeptunProfile.value))
                    require(error == null) { "TUN engine failed" }
                }
                GhajarLog.i("Plugin", "${release.id}: local engine ready ($mode)")
                ensureActive(); VpnState.setConnected(); VpnCommandCoordinator.onTunnelConfirmed()
                val cm = requireNotNull(getSystemService(ConnectivityManager::class.java))
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(n: Network) { scope.launch { runCatching { client.call(PluginWire.NETWORK_CHANGED) }.onFailure { requestStop() } } }
                    override fun onLost(n: Network) { onAvailable(n) }
                }
                network = callback
                cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
                while (isActive) { delay(5000); require(client.call(PluginWire.STATUS).getBoolean("ready")) { "Engine stopped" } }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = "اتصال افزونه متوقف شد: ${e.javaClass.simpleName}" }
            finally { withContext(NonCancellable) { cleanup(failure) } }
        }
        return START_NOT_STICKY
    }
    private fun applyPlan(b: Builder, plan: Bundle, caps: CapabilityContract) {
        fun entries(key: String): List<String> = plan.getStringArrayList(key)?.also { require(it.size in 1..64) } ?: error("Missing TUN $key")
        fun literal(s: String): String {
            require(s.length <= 64 && (s.matches(Regex("[0-9.]+")) || s.matches(Regex("[0-9a-fA-F:]+"))))
            require(caps.supportsIPv6 || !s.contains(':')); return s
        }
        fun cidr(s: String): Pair<String, Int> { val p = s.split('/'); require(p.size == 2)
            val ip = literal(p[0]); val prefix = p[1].toInt(); require(prefix in 0..if (ip.contains(':')) 128 else 32); return ip to prefix }
        entries("addresses").forEach { val (ip, bits) = cidr(it); b.addAddress(ip, bits) }
        entries("routes").forEach { val (ip, bits) = cidr(it); b.addRoute(ip, bits) }
        require(caps.supportsDns); entries("dns").forEach { b.addDnsServer(literal(it)) }
        // Adapter must honor all full-config routing/providers/TUN semantics or PREPARE must fail.
        require(plan.getBoolean("fullConfigPreserved")) { "Lossy full-config adapter refused" }
    }
    private fun applyApps(b: Builder, store: ConfigStore, pluginPackage: String) {
        val excluded = setOf(packageName, pluginPackage)
        if (store.perAppMode.value == PerAppMode.ALLOWLIST && store.perAppList.value.isNotEmpty()) {
            val selected = store.perAppList.value.filterNot { it in excluded }
            require(selected.isNotEmpty()) { "No eligible applications" }
            selected.forEach { b.addAllowedApplication(it) }
        } else {
            (excluded + if (store.perAppMode.value == PerAppMode.BLOCKLIST) store.perAppList.value else emptySet()).forEach {
                runCatching { b.addDisallowedApplication(it) }.getOrElse { error("Cannot enforce application route") }
            }
        }
    }
    suspend fun test(): Long {
        require(!stopping.get() && VpnState.state.value == Connection.CONNECTED)
        return requireNotNull(rpc).call(PluginWire.TEST).getLong("latencyMs", -1)
    }
    fun requestStop() {
        if (stopping.compareAndSet(false, true)) {
            VpnState.setDisconnecting()
            val current = job
            current?.cancel()
            scope.launch { current?.join(); withContext(NonCancellable) { cleanup(null) } }
        }
    }
    private suspend fun cleanup(error: String?) {
        if (!cleaned.compareAndSet(false, true)) return
        GhajarLog.i("Plugin", if (error == null) "Engine stopped" else "Engine stopped after failure")
        stopping.set(true)
        val keepBlocked = error != null && ConfigStore.get(applicationContext).killSwitch.value && tun != null
        val blocked = if (keepBlocked) runCatching { blackhole?.invoke() }.getOrNull() else null
        network?.let { runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) } }; network = null
        if (zeptun) { runCatching { ZeptunEngine.stop() }; zeptun = false }
        runCatching { tun?.close() }; tun = null
        withTimeoutOrNull(3000) { runCatching { rpc?.call(PluginWire.STOP) } }; rpc?.close(); rpc = null
        if (blocked != null) {
            tun = blocked; job = null; VpnState.setError(error ?: "Plugin stopped"); VpnCommandCoordinator.onTunnelFailed()
            cleaned.set(false); stopping.set(false)
            return // Keep a fresh, unread TUN until the user explicitly disconnects.
        }
        PluginRuntime.release(this)
        if (error == null) { VpnState.setDisconnected(); VpnCommandCoordinator.onTunnelTeardown() }
        else { VpnState.setError(error); VpnCommandCoordinator.onTunnelFailed() }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); scope.cancel()
    }
    override fun onRevoke() { requestStop(); super.onRevoke() }
    override fun onDestroy() { requestStop(); super.onDestroy() }
    companion object { const val STOP = "net.gozar.plugin.STOP" }
}

package net.gozar.app.plugins

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.gozar.app.*

/** A lease excludes activation/rollback for the entire lifetime of the bound engine. */
object PluginRuntime {
    private val slots = Mutex()
    private val launchLock = Mutex()
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private val lease = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    @Volatile private var leaseToken = 0L
    private var leasedId: String?
        get() = lease.value
        set(value) { lease.value = value }
    @Volatile private var service: PluginVpnService? = null
    suspend fun test(id: String): Long { require(isUsing(id)); return requireNotNull(service).test() }
    val running: Boolean get() = leasedId != null
    fun isUsing(id: String) = leasedId == id
    suspend fun <T> whileInactive(id: String, block: suspend () -> T): T = slots.withLock {
        require(!isUsing(id)) { "ابتدا اتصال افزونه را قطع کنید." }; block()
    }
    private suspend fun acquire(id: String, ticket: Long): Unit = slots.withLock {
        require(leasedId == null) { "یک افزونه در حال اتصال است." }; leasedId = id; leaseToken = ticket
    }
    internal fun attach(value: PluginVpnService) { service = value }
    internal suspend fun release(value: PluginVpnService) = slots.withLock {
        if (service === value) { service = null; leasedId = null }
    }
    fun cancelPending() { generation.incrementAndGet() }
    internal fun isCurrent(ticket: Long) = ticket == leaseToken && ticket == generation.get()
    fun stop(context: Context) {
        cancelPending()
        stopCurrent(context)
    }
    private fun stopCurrent(context: Context) {
        service?.requestStop()
        // Also covers the interval between reserving the lease and onCreate.
        if (running && service == null) context.startService(Intent(context, PluginVpnService::class.java).setAction(PluginVpnService.STOP))
    }
    suspend fun launch(context: Context, config: ProxyConfig): LaunchOutcome {
      val ticket = generation.incrementAndGet()
      return launchLock.withLock { withContext(Dispatchers.IO) {
        if (android.net.VpnService.prepare(context) != null) return@withContext LaunchOutcome.NO_PERMISSION
        var ownsLease = false
        try {
            require(generation.get() == ticket)
            val profile = requireNotNull(PluginProfiles.read(config))
            val manager = PluginManager.get(context)
            val release = manager.active(profile.id) ?: error(PluginProfiles.requirement(config).orEmpty())
            manager.verifyInstalled(release) // verify BEFORE dropping the user's working tunnel
            require(generation.get() == ticket)
            if (running) { stopCurrent(context); withTimeout(10000) { lease.first { it == null } } }
            require(generation.get() == ticket)
            if (IkeController.active) withContext(Dispatchers.Main) { IkeController.disconnect(context) }
            // The normal UI already sequences OpenVPN shutdown; background launch refuses a competing OpenVPN.
            require(GhajarOpenVpnBridge.status.value == GhajarOvpnState.DISCONNECTED) { "ابتدا OpenVPN را قطع کنید." }
            acquire(profile.id, ticket); ownsLease = true
            try {
                GozarVpnService.stopForPlugin(context)
                require(isUsing(profile.id) && isCurrent(ticket)) { "Connection cancelled" }
                // Re-read under the reserved lease: activation cannot change it during connection.
                require(manager.active(profile.id)?.packageName == release.packageName) { "Plugin slot changed; retry" }
                VpnState.setConnecting(config.id)
                ContextCompat.startForegroundService(context, Intent(context, PluginVpnService::class.java).putExtra("configId", config.id).putExtra("generation", ticket))
            } catch (e: Exception) { withContext(NonCancellable) { slots.withLock { if (leaseToken == ticket) leasedId = null } }; throw e }
            LaunchOutcome.STARTED
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (generation.get() == ticket) {
                VpnState.setError("اتصال افزونه آغاز نشد: ${e.javaClass.simpleName}")
                VpnCommandCoordinator.onTunnelFailed()
            } else if (ownsLease && !running && VpnState.state.value == Connection.DISCONNECTING) {
                VpnState.setDisconnected(); VpnCommandCoordinator.onTunnelTeardown()
            }
            LaunchOutcome.REFUSED
        }
      } }
    }
}

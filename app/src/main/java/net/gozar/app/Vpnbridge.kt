package net.gozar.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VpnCounters(
    val totalUp: Long = 0L,
    val totalDown: Long = 0L,
    val upSpeed: Long = 0L,
    val downSpeed: Long = 0L
)

object VpnBridge {
    private const val ACTION = "net.gozar.app.VPN_UPDATE"
    private const val EX_STATE = "state"
    private const val EX_ERROR = "error"
    private const val EX_TOTAL_UP = "tup"
    private const val EX_TOTAL_DOWN = "tdown"
    private const val EX_DELTA_UP = "dup"
    private const val EX_DELTA_DOWN = "ddown"

    private const val S_CONNECTED = "connected"
    private const val S_ERROR = "error"
    private const val S_DISCONNECTED = "disconnected"
    private const val S_COUNTERS = "counters"

    private val _counters = MutableStateFlow(VpnCounters())
    val counters: StateFlow<VpnCounters> = _counters.asStateFlow()

    @Volatile private var registered = false

    // ContextCompat.registerReceiver with no Handler delivers onReceive on
    // the main thread. That used to be where all the real work happened too
    // - every second while connected, S_COUNTERS ran UsageStore.add(), which
    // copies five maps and (every 5th call) serializes/writes them to
    // SharedPreferences - all on main, on every screen, for as long as the
    // VPN stayed connected. onReceive now only reads the (cheap) Intent
    // extras and hands them to this background scope; nothing it touches
    // downstream (VpnState/VpnCommandCoordinator's StateFlow and volatile
    // fields, UsageStore's own internal locking) requires the main thread.
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun register(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val state = intent.getStringExtra(EX_STATE)
                val error = intent.getStringExtra(EX_ERROR)
                val tup = intent.getLongExtra(EX_TOTAL_UP, 0L)
                val tdown = intent.getLongExtra(EX_TOTAL_DOWN, 0L)
                val dup = intent.getLongExtra(EX_DELTA_UP, 0L)
                val ddown = intent.getLongExtra(EX_DELTA_DOWN, 0L)
                bridgeScope.launch { handle(state, error, tup, tdown, dup, ddown) }
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun handle(state: String?, error: String?, tup: Long, tdown: Long, dup: Long, ddown: Long) {
        // Rapid connect/disconnect: stale engine reports must never
        // overwrite the state the user's latest intent established.
        val reported = when (state) {
            S_CONNECTED -> Connection.CONNECTED
            S_DISCONNECTED -> Connection.DISCONNECTED
            S_ERROR -> Connection.ERROR
            else -> null
        }
        if (reported != null && !VpnCommandCoordinator.acceptBroadcast(reported)) return
        GhajarLog.d("VpnBridge", "broadcast state=$state")
        when (state) {
            // While IKEv2 owns the tunnel the Xray service is not the authority on
            // connection state: IkeController drives VpnState from strongSwan's own
            // callbacks. Honouring a late disconnect from the stopped Xray service
            // here is what produced the connect/disconnect flicker on IKEv2 connect.
            S_CONNECTED -> if (!IkeController.active) { VpnState.setConnected(); VpnCommandCoordinator.onTunnelConfirmed() }
            S_ERROR -> if (!IkeController.active) {
                VpnState.setError(error ?: "Connection failed")
                VpnCommandCoordinator.onTunnelFailed()
                _counters.value = VpnCounters()
            }
            S_DISCONNECTED -> {
                if (!IkeController.active) VpnState.setDisconnected()
                VpnCommandCoordinator.onTunnelTeardown()
                _counters.value = VpnCounters()
                UsageStore.flush()
            }
            S_COUNTERS -> {
                _counters.value = VpnCounters(tup, tdown, dup, ddown)
                UsageStore.add(dup, ddown)
            }
        }
    }

    private fun send(ctx: Context, state: String, error: String?, tup: Long, tdown: Long, dup: Long, ddown: Long) {
        val i = Intent(ACTION).setPackage(ctx.packageName)
            .putExtra(EX_STATE, state)
            .putExtra(EX_ERROR, error)
            .putExtra(EX_TOTAL_UP, tup)
            .putExtra(EX_TOTAL_DOWN, tdown)
            .putExtra(EX_DELTA_UP, dup)
            .putExtra(EX_DELTA_DOWN, ddown)
        ctx.sendBroadcast(i)
    }

    fun sendConnected(ctx: Context) = send(ctx, S_CONNECTED, null, 0L, 0L, 0L, 0L)
    fun sendError(ctx: Context, message: String) = send(ctx, S_ERROR, message, 0L, 0L, 0L, 0L)
    fun sendDisconnected(ctx: Context) = send(ctx, S_DISCONNECTED, null, 0L, 0L, 0L, 0L)
    fun sendCounters(ctx: Context, totalUp: Long, totalDown: Long, upSpeed: Long, downSpeed: Long) =
        send(ctx, S_COUNTERS, null, totalUp, totalDown, upSpeed, downSpeed)
}
package net.gozar.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes VPN connect/disconnect commands and makes the latest user intent
 * win no matter how fast it is tapped. Stale service callbacks (from a command
 * the user already superseded) are filtered out, and a watchdog guarantees the
 * app can never stay stuck in CONNECTING/DISCONNECTING: on timeout it stops the
 * tunnel, cleans up and reconciles the real state.
 */
object VpnCommandCoordinator {

    enum class Kind { CONNECT, DISCONNECT }

    /** Test/JVM seam: overridden to a no-op so the object stays JVM-testable. */
    @Volatile var logger: (String) -> Unit = { android.util.Log.d("VpnCoordinator", it) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()

    @Volatile private var generation: Long = 0L
    @Volatile private var lastCommand: Kind? = null
    @Volatile private var lastCommandAt: Long = 0L
    @Volatile private var connectWatchdog: Job? = null
    @Volatile private var disconnectWatchdog: Job? = null

    /** Sanity window for stale service broadcasts; watchdog covers beyond it. */
    private const val STALE_MS = 6_000L
    private const val CONNECT_TIMEOUT_MS = 45_000L
    private const val DISCONNECT_TIMEOUT_MS = 10_000L

    /**
     * Applies a user connect intent: cancels any pending disconnect reconciles,
     * bumps the generation and runs [launch] under the command mutex so two
     * rapid connects cannot interleave their start sequences.
     */
    fun onConnectRequested(configId: String, launch: () -> Unit) {
        scope.launch {
            mutex.withLock {
                generation++
                val gen = generation
                lastCommand = Kind.CONNECT
                lastCommandAt = System.currentTimeMillis()
                disconnectWatchdog?.cancel(); disconnectWatchdog = null
                VpnState.setConnecting(configId)
                logger("connect#$gen")
                launch()
                armConnectWatchdog(gen)
            }
        }
    }

    /**
     * Applies a user disconnect intent. The tunnel stop always runs; the state
     * flips to DISCONNECTING and a watchdog reconciles DISCONNECTED even when
     * the engine never answers.
     */
    fun onDisconnectRequested(stop: () -> Unit) {
        scope.launch {
            mutex.withLock {
                generation++
                val gen = generation
                lastCommand = Kind.DISCONNECT
                lastCommandAt = System.currentTimeMillis()
                connectWatchdog?.cancel(); connectWatchdog = null
                when (VpnState.state.value) {
                    Connection.CONNECTED, Connection.CONNECTING -> VpnState.setDisconnecting()
                    else -> Unit
                }
                logger("disconnect#$gen")
                runCatching { stop() }
                armDisconnectWatchdog(gen)
            }
        }
    }

    /**
     * Broadcast filter used by VpnBridge. Returns false for service reports that
     * contradict the latest user intent inside the sanity window, so a delayed
     * "connected" from an already-cancelled attempt can never fake the state.
     */
    fun acceptBroadcast(report: Connection): Boolean {
        val kind = lastCommand ?: return true
        val age = System.currentTimeMillis() - lastCommandAt
        if (age > STALE_MS) return true
        return when (kind) {
            Kind.DISCONNECT -> report != Connection.CONNECTED
            Kind.CONNECT -> report != Connection.DISCONNECTED
        }
    }

    /** Called when a real tunnel confirmation arrives; disarms its watchdog. */
    fun onTunnelConfirmed() {
        connectWatchdog?.cancel(); connectWatchdog = null
        lastCommand = null
    }

    /** Called when a real teardown confirmation arrives; disarms its watchdog. */
    fun onTunnelTeardown() {
        disconnectWatchdog?.cancel(); disconnectWatchdog = null
        lastCommand = null
        if (VpnState.state.value == Connection.DISCONNECTING) VpnState.setDisconnected()
    }

    /**
     * Watchdogs: if the engine neither confirms connect nor teardown in time,
     * force a stop and reconcile the state so the UI can never hang forever.
     */
    private fun armConnectWatchdog(gen: Long) {
        connectWatchdog?.cancel()
        connectWatchdog = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (generation != gen) return@launch
            logger("W connect watchdog fired")
            if (VpnState.state.value == Connection.CONNECTING) {
                VpnState.setError("زمان اتصال تمام شد؛ دوباره تلاش کنید.")
            }
            lastCommand = null
        }
    }

    private fun armDisconnectWatchdog(gen: Long) {
        disconnectWatchdog?.cancel()
        disconnectWatchdog = scope.launch {
            delay(DISCONNECT_TIMEOUT_MS)
            if (generation != gen) return@launch
            logger("W disconnect watchdog fired")
            VpnState.setDisconnected()
            lastCommand = null
        }
    }

    /** Test hook: resets all bookkeeping. */
    fun resetForTest() {
        connectWatchdog?.cancel(); disconnectWatchdog?.cancel()
        connectWatchdog = null; disconnectWatchdog = null
        generation = 0L; lastCommand = null; lastCommandAt = 0L
    }
}

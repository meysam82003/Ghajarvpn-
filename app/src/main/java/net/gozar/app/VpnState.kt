package net.gozar.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask

enum class Connection { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

object VpnState {
    internal var clock: () -> Long = { System.currentTimeMillis() }

    internal const val TRANSITION_DEBOUNCE_MS = 450L
    internal const val RECONNECT_LOCK_MS = 700L
    internal const val DISCONNECT_WATCHDOG_MS = 3_000L

    @Volatile private var appContext: Context? = null
    private val gate = Any()
    private var watchdog: Timer? = null

    private val _state = MutableStateFlow(Connection.DISCONNECTED)
    val state: StateFlow<Connection> = _state.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _picking = MutableStateFlow(false)
    val picking: StateFlow<Boolean> = _picking.asStateFlow()

    private val _connectedAt = MutableStateFlow(0L)
    val connectedAt: StateFlow<Long> = _connectedAt.asStateFlow()

    private val _lookupGeneration = MutableStateFlow(0)
    val lookupGeneration: StateFlow<Int> = _lookupGeneration.asStateFlow()

    private var lastTransitionAt = 0L
    private var lastDisconnectAt = 0L

    internal fun resetForTests() {
        synchronized(gate) {
            cancelWatchdogLocked()
            _state.value = Connection.DISCONNECTED
            _activeId.value = null
            _error.value = null
            _picking.value = false
            _connectedAt.value = 0L
            _lookupGeneration.value = 0
            lastTransitionAt = 0L
            lastDisconnectAt = 0L
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val saved = VpnConnectionStore.read(context.applicationContext)
        // A "disconnecting" snapshot never means anything to the next process:
        // the watchdog decision belongs to this process only.
        _state.value = if (saved.state == Connection.DISCONNECTING) Connection.DISCONNECTED else saved.state
        _activeId.value = saved.activeId
        _error.value = saved.error
        if (saved.state == Connection.CONNECTED && _connectedAt.value == 0L) _connectedAt.value = saved.updatedAt
    }

    private fun persist() {
        appContext?.let { VpnConnectionStore.write(it, _state.value, _activeId.value, _error.value) }
    }

    private fun bumpGenerationLocked() {
        _lookupGeneration.value = _lookupGeneration.value + 1
    }

    fun setPicking(value: Boolean) { _picking.value = value }

    fun setConnecting(id: String) {
        synchronized(gate) {
            val now = clock()
            if (_state.value == Connection.DISCONNECTING) return
            if (_state.value == Connection.CONNECTED && _activeId.value == id) return
            if (now - lastTransitionAt < TRANSITION_DEBOUNCE_MS) return
            if (_state.value == Connection.DISCONNECTED &&
                lastDisconnectAt != 0L && now - lastDisconnectAt < RECONNECT_LOCK_MS) return
            lastTransitionAt = now
            _activeId.value = id
            _error.value = null
            _connectedAt.value = 0L
            _state.value = Connection.CONNECTING
            bumpGenerationLocked()
            persist()
        }
    }

    fun setConnected() {
        synchronized(gate) {
            val now = clock()
            if (_state.value != Connection.CONNECTING) return
            if (now - lastTransitionAt < TRANSITION_DEBOUNCE_MS) return
            lastTransitionAt = now
            _connectedAt.value = System.currentTimeMillis()
            _state.value = Connection.CONNECTED
            persist()
        }
    }

    fun setError(message: String) {
        synchronized(gate) {
            if (_state.value == Connection.CONNECTED || _state.value == Connection.DISCONNECTING) return
            _picking.value = false
            _error.value = message
            _connectedAt.value = 0L
            _state.value = Connection.ERROR
            persist()
        }
    }

    /** User intent to disconnect: show "disconnecting" instantly and arm the
     * watchdog so a silent service can never leave the UI stuck. */
    fun beginDisconnecting() {
        synchronized(gate) {
            when (_state.value) {
                Connection.CONNECTED, Connection.CONNECTING, Connection.ERROR -> {
                    lastTransitionAt = clock()
                    _state.value = Connection.DISCONNECTING
                    persist()
                    armDisconnectWatchdogLocked()
                }
                Connection.DISCONNECTING -> if (watchdog == null) armDisconnectWatchdogLocked()
                Connection.DISCONNECTED -> Unit
            }
        }
    }

    /** The tunnel itself is the source of truth; if a disconnect never
     * confirms within the watchdog window, force the state back so the UI
     * can never stay stuck on "disconnecting". */
    private fun armDisconnectWatchdogLocked() {
        cancelWatchdogLocked()
        val timer = Timer("ghajar-disconnect-watchdog", true)
        watchdog = timer
        timer.schedule(object : TimerTask() {
            override fun run() {
                synchronized(gate) {
                    if (_state.value == Connection.DISCONNECTING) {
                        val now = clock()
                        lastTransitionAt = now
                        lastDisconnectAt = now
                        _picking.value = false
                        _activeId.value = null
                        _connectedAt.value = 0L
                        _error.value = null
                        _state.value = Connection.DISCONNECTED
                        cancelWatchdogLocked()
                        bumpGenerationLocked()
                        persist()
                    }
                }
            }
        }, DISCONNECT_WATCHDOG_MS)
    }

    private fun cancelWatchdogLocked() {
        watchdog?.cancel()
        watchdog = null
    }

    fun setDisconnected() {
        synchronized(gate) {
            val now = clock()
            val wasActive = _state.value != Connection.DISCONNECTED
            lastTransitionAt = now
            lastDisconnectAt = if (wasActive) now else lastDisconnectAt
            _picking.value = false
            _activeId.value = null
            _connectedAt.value = 0L
            _error.value = null
            _state.value = Connection.DISCONNECTED
            cancelWatchdogLocked()
            if (wasActive) {
                bumpGenerationLocked()
                persist()
            }
        }
    }
}

object SecureScreen {
    private val holders = mutableSetOf<String>()
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    @Synchronized
    fun acquire(key: String) {
        holders.add(key)
        _on.value = holders.isNotEmpty()
    }

    @Synchronized
    fun release(key: String) {
        holders.remove(key)
        _on.value = holders.isNotEmpty()
    }

    @Synchronized
    fun clear() {
        holders.clear()
        _on.value = false
    }
}

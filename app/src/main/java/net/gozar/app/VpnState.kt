package net.gozar.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Connection { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

object VpnState {
    internal var clock: () -> Long = { System.currentTimeMillis() }

    internal const val TRANSITION_DEBOUNCE_MS = 450L
    internal const val RECONNECT_LOCK_MS = 700L

    @Volatile private var appContext: Context? = null
    private val gate = Any()

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
        _state.value = saved.state
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
            if (_state.value == Connection.CONNECTED) return
            _picking.value = false
            _error.value = message
            _connectedAt.value = 0L
            _state.value = Connection.ERROR
            persist()
        }
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

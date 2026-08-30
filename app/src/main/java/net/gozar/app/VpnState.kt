package net.gozar.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Connection { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

object VpnState {
    @Volatile private var appContext: Context? = null
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

    fun setPicking(value: Boolean) { _picking.value = value }
    fun setConnecting(id: String) {
        _activeId.value = id; _error.value = null; _connectedAt.value = 0L
        _state.value = Connection.CONNECTING; persist()
    }
    fun setConnected() {
        _connectedAt.value = System.currentTimeMillis(); _state.value = Connection.CONNECTED; persist()
    }
    fun setError(message: String) {
        _picking.value = false; _error.value = message; _connectedAt.value = 0L
        _state.value = Connection.ERROR; persist()
    }
    fun setDisconnected() {
        _picking.value = false; _activeId.value = null; _connectedAt.value = 0L
        _state.value = Connection.DISCONNECTED; persist()
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

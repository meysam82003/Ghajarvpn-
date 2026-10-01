package net.gozar.app
import kotlinx.coroutines.flow.MutableStateFlow
class ConfigStore {
 val vpnShareEnabled=MutableStateFlow(false)
 fun setVpnShareEnabled(v:Boolean) {vpnShareEnabled.value=v}
 fun ensureVpnShareCredential()="user" to "0123456789abcdef"
 fun regenerateVpnShareCredential()=ensureVpnShareCredential()
}
enum class Connection { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }
object VpnState { val state=MutableStateFlow(Connection.DISCONNECTED) }

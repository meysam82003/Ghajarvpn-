package net.gozar.app
import kotlinx.coroutines.flow.MutableStateFlow
// Android/state collaborators only; VaultConnection, ConfigBuilder, VaultRuntime and models are real.
class ConfigStore {
 val fragment=MutableStateFlow(false);val splitRouting=MutableStateFlow(false);val sniffing=MutableStateFlow(false)
 val sniffTypes=MutableStateFlow(emptySet<String>());val mux=MutableStateFlow(false);val muxConcurrency=MutableStateFlow(8)
 val adBlock=MutableStateFlow(false);val fakeDns=MutableStateFlow(false);val encryptedDns=MutableStateFlow(false)
 val customDns=MutableStateFlow("");val youtubeDirect=MutableStateFlow(false);val noiseSpec=MutableStateFlow("")
 val fragmentPackets=MutableStateFlow("");val fragmentLength=MutableStateFlow("");val fragmentInterval=MutableStateFlow("")
 val configs=MutableStateFlow(emptyList<ProxyConfig>());val onionRouting=MutableStateFlow(false);val coreLogLevel=MutableStateFlow("")
 val vpnShareEnabled=MutableStateFlow(false);val vpnSharePassword=MutableStateFlow("");val lang=MutableStateFlow("")
 fun ensureVpnShareCredential()="" to ""
}
object VpnState { fun setConnecting(id:String) {} }
object Strings { fun get(lang:String,key:String)="" }
class GozarVpnService { companion object { const val EXTRA_VAULT_REF="ref";const val EXTRA_NAME="name";const val EXTRA_STOP_LABEL="stop" } }

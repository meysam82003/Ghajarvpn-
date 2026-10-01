package net.gozar.app.sharing

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.gozar.app.*
import java.net.Inet4Address
import java.net.NetworkInterface

/** Owned by the running VPN service, never by a screen or a startup worker. */
object PhoneSharing {
    const val XRAY_PORT = 18789
    enum class State { OFF, STARTING, ACTIVE, VPN_REQUIRED, CLIENT_CONNECTED, VPN_DISCONNECTED, ERROR }
    data class View(val state: State = State.OFF, val address: String = "", val port: Int = 0,
        val username: String = "", val password: String = "", val clients: List<AuthenticatedRelay.Client> = emptyList(), val error: String = "")
    private val mutable = MutableStateFlow(View())
    val view = mutable.asStateFlow()
    private var relay: AuthenticatedRelay? = null
    private var job: Job? = null
    private var generation = 0L
    private var backend = 0
    private var store: ConfigStore? = null
    private var bound = ""
    private var credentials: Pair<String,String>? = null
    private var chosenAddress: String? = null
    fun addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence().filter { it.isUp && !it.isLoopback &&
            (it.name.startsWith("ap") || it.name.startsWith("wlan") || it.name.startsWith("swlan") || it.name.startsWith("eth")) }
            .flatMap { it.inetAddresses.asSequence() }.filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }.mapNotNull { it.hostAddress }.distinct().toList()
    }.getOrDefault(emptyList())
    @Synchronized fun sessionReady(configStore: ConfigStore, port: Int) {
        // ACTION_WARM may repeat while the engine is already alive; never disconnect guests just for a UI refresh.
        if (backend==port && job?.isActive==true && store===configStore) { refresh(); return }
        invalidate(); store=configStore; backend=port
        val token=generation
        job=CoroutineScope(Dispatchers.IO).launch {
            while(isActive) { synchronized(this@PhoneSharing) { if(generation==token) refresh() }; delay(1000) }
        }
    }
    @Synchronized fun invalidate() {
        generation++; job?.cancel(); job=null; backend=0; relay?.close(); relay=null; bound=""; credentials=null
        mutable.value=View(if(store?.vpnShareEnabled?.value==true) State.VPN_DISCONNECTED else State.OFF)
    }
    @Synchronized fun configure(configStore: ConfigStore, enabled: Boolean, address: String? = null, regenerate: Boolean = false) {
        store=configStore
        if(address!=null) chosenAddress=address
        configStore.setVpnShareEnabled(enabled)
        if(regenerate) { relay?.close(); relay=null; credentials=null; configStore.regenerateVpnShareCredential() }
        refresh()
    }
    @Synchronized private fun refresh() {
        val s=store ?: return
        if(!s.vpnShareEnabled.value || backend==0 || VpnState.state.value!=Connection.CONNECTED) {
            relay?.close(); relay=null; bound=""
            mutable.value=View(if(!s.vpnShareEnabled.value) State.OFF else State.VPN_REQUIRED)
            return
        }
        val available=addresses()
        val address=chosenAddress?.takeIf { it in available } ?: if(chosenAddress==null) hotspotInterfaceAddress()?.takeIf { it in available } else null
        if(address==null) { relay?.close(); relay=null; bound=""; mutable.value=View(State.ERROR,error="هات‌اسپات را روشن کنید یا آدرس شبکهٔ محلی را انتخاب کنید."); return }
        val cred=s.ensureVpnShareCredential()
        if(relay!=null && (bound!=address || credentials!=cred)) { relay?.close(); relay=null }
        if(relay==null) {
            mutable.value=View(State.STARTING)
            val candidate=AuthenticatedRelay(backend,cred.first,cred.second)
            try { candidate.start(java.net.InetAddress.getByName(address)); relay=candidate; bound=address; credentials=cred }
            catch(_:Exception) { candidate.close(); mutable.value=View(State.ERROR,error="بازکردن درگاه اشتراک ممکن نشد؛ آدرس یا برنامهٔ استفاده‌کننده از درگاه را بررسی کنید."); return }
        }
        val clients=relay!!.clients()
        mutable.value=View(if(clients.isEmpty()) State.ACTIVE else State.CLIENT_CONNECTED,address,relay!!.port,cred.first,cred.second,clients)
    }
}

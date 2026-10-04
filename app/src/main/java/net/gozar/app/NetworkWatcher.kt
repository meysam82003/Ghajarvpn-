package net.gozar.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place that knows which physical network the device is on.
 *
 * Nothing in this app watched connectivity before: the home screen polled two
 * IPs on a timer to guess whether the internet was up, and the VPN service
 * never learned that the network under its tunnel had changed at all.
 *
 * The request asks for NET_CAPABILITY_NOT_VPN on purpose. Once a tunnel is up
 * the VPN itself becomes the default network, so a default-network callback
 * would report the tunnel rather than the Wi-Fi or SIM carrying it - which is
 * exactly the information a rule about Wi-Fi versus mobile data needs.
 */
object NetworkWatcher {

    private val _kind = MutableStateFlow<NetKind?>(null)

    /** The current physical network's kind, or null when there is none. */
    val kind: StateFlow<NetKind?> = _kind.asStateFlow()

    @Volatile
    private var current: Network? = null

    /** The network itself, for VpnService.setUnderlyingNetworks. */
    fun currentNetwork(): Network? = current

    private val listeners = mutableSetOf<(NetKind?, Network?) -> Unit>()
    private var registered = false

    /**
     * Called on every change of the physical network, including its loss.
     *
     * Listeners are keyed by identity and removed by [removeListener], so a
     * service registering on start and removing on teardown cannot leak.
     */
    @Synchronized
    fun addListener(listener: (NetKind?, Network?) -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: (NetKind?, Network?) -> Unit) {
        listeners.remove(listener)
    }

    @Synchronized
    private fun notifyAll(kind: NetKind?, network: Network?) {
        // A copy, because a listener may remove itself while being called.
        listeners.toList().forEach { l ->
            runCatching { l(kind, network) }
                .onFailure { GhajarLog.e(TAG, "listener failed: ${it.javaClass.simpleName}") }
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (registered) return
        val cm = context.applicationContext
            .getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val ok = runCatching { cm.registerNetworkCallback(request, callback) }.isSuccess
        if (!ok) {
            GhajarLog.e(TAG, "network callback not registered")
            return
        }
        registered = true
        GhajarLog.i(TAG, "watching physical networks")
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            // Capabilities arrive in their own callback; this only records that
            // a network exists, so a listener is not told about a kind that has
            // not been read yet.
            current = network
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val kind = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.CELLULAR
                else -> NetKind.OTHER
            }
            val changed = kind != _kind.value || network != current
            current = network
            _kind.value = kind
            // Capabilities fire repeatedly for the same network (signal
            // strength, metered state); only a real change is worth acting on.
            if (changed) {
                GhajarLog.i(TAG, "network is now $kind")
                notifyAll(kind, network)
            }
        }

        override fun onLost(network: Network) {
            if (network != current) return
            current = null
            _kind.value = null
            GhajarLog.i(TAG, "network lost")
            notifyAll(null, null)
        }
    }

    private const val TAG = "GhajarNet"
}

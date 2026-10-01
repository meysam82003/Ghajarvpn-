package net.gozar.app.plugins

import android.content.*
import android.os.*
import kotlinx.coroutines.*
import net.gozar.plugin.api.PluginWire
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Explicit, re-verified service binding; no Core initialized during discovery. */
class PluginRpc(private val context: Context, private val release: PluginRelease) : Closeable {
    @Volatile private var remote: Messenger? = null
    private var bound = false
    private val connected = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Bundle>>()
    private val reply = Messenger(Handler(Looper.getMainLooper()) { m ->
        val b = m.data; val request = pending.remove(b.getString(PluginWire.REQUEST_ID))
        if (request != null) {
            if (b.getString(PluginWire.ID) == release.id && b.getLong(PluginWire.VERSION) == release.versionCode &&
                b.getInt(PluginWire.API_VERSION) == PluginWire.API && b.getBoolean(PluginWire.OK)) request.complete(b)
            else request.completeExceptionally(IllegalStateException("Plugin API request failed"))
        }; true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) { remote = Messenger(binder); connected.complete(Unit) }
        override fun onServiceDisconnected(name: ComponentName) { lost() }
        override fun onBindingDied(name: ComponentName) { lost() }
        override fun onNullBinding(name: ComponentName) { lost() }
    }
    private fun lost() { remote = null; val e = IllegalStateException("Plugin service disconnected")
        connected.completeExceptionally(e); pending.values.forEach { it.completeExceptionally(e) }; pending.clear() }
    suspend fun open() {
        PluginManager.get(context).verifyInstalled(release)
        val intent = Intent(PluginWire.ACTION).setComponent(ComponentName(release.packageName, release.serviceClass))
        withContext(Dispatchers.Main) { bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
        require(bound) { "Plugin service refused binding" }
        withTimeout(15000) { connected.await() }
    }
    suspend fun call(operation: Int, args: Bundle = Bundle()): Bundle {
        val id = UUID.randomUUID().toString(); val result = CompletableDeferred<Bundle>(); pending[id] = result
        args.putString(PluginWire.REQUEST_ID, id); args.putString(PluginWire.ID, release.id); args.putInt(PluginWire.API_VERSION, PluginWire.API)
        try {
            (remote ?: error("Plugin is not bound")).send(Message.obtain().apply { what = operation; data = args; replyTo = reply })
            return withTimeout(30000) { result.await() }
        } finally { pending.remove(id) }
    }
    suspend fun health() { require(call(PluginWire.HEALTH).getBoolean("healthy")) { "Plugin self-test failed" } }
    suspend fun configCall(operation: Int, profile: PluginProfile, tun: ParcelFileDescriptor? = null, hostToken: IBinder? = null): Bundle {
        val file = File.createTempFile("plugin-config-", ".tmp", context.cacheDir)
        try {
            file.writeText(profile.payload)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                return call(operation, Bundle().apply { putParcelable(PluginWire.CONFIG, fd); putString("format", profile.format); putString("settings", profile.settings.toString())
                    if (tun != null) putParcelable(PluginWire.TUN, tun)
                    if (hostToken != null) putBinder("hostToken", hostToken) })
            }
        } finally { file.delete() }
    }
    override fun close() {
        lost()
        if (bound) { runCatching { context.unbindService(connection) }; bound = false }
    }
}

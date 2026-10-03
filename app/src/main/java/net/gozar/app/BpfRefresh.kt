package net.gozar.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import net.gozar.app.configtoolkit.BoundedInput
import net.gozar.app.configtoolkit.BoundedJson
import net.gozar.app.engine.FullSingBoxProfile
import org.json.JSONObject
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Dns
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Remote profile v1 interval is minutes (SFA TypedProfile/UpdateProfileWork).
 * Fetch only the explicitly imported HTTPS origin. No redirects, local paths, or URL changes.
 */
object BpfRefresh {
    private val lock=Mutex()
    fun intervalMillis(metadata: JSONObject): Long? {
        if(metadata.optInt("profileType")!=2 || !metadata.optBoolean("autoUpdate")) return null
        val minutes=metadata.optLong("updateInterval",0)
        if(minutes !in 15..525600) return null
        return Math.multiplyExact(minutes,60_000L)
    }
    fun remote(metadata: JSONObject): URI {
        val uri=URI(metadata.getString("remotePath"))
        require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.rawUserInfo==null && uri.fragment==null && uri.port in listOf(-1,443))
        require(uri.host.contains('.') && !uri.host.endsWith(".local",true) && !uri.host.endsWith(".localhost",true))
        return uri
    }
    internal fun publicAddress(a: InetAddress): Boolean {
        if(a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return false
        val b=a.address
        if(b.size==16) return (b[0].toInt() and 0xfe)!=0xfc
        val first=b[0].toInt() and 255;val second=b[1].toInt() and 255
        // CGNAT, unspecified/reserved and benchmarking networks are not public origins.
        return first!=0 && first<224 && !(first==100 && second in 64..127) && !(first==198 && second in 18..19)
    }
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        if(!lock.tryLock()) return@withContext
        try {
            val store=ConfigStore.get(context);store.awaitReady()
            val prefs=context.getSharedPreferences("ghajar_bpf_updates",0)
            for(c in store.configs.value.filter { FullSingBoxProfile.isFull(it) && !it.locked }) {
                val extra=runCatching { JSONObject(c.extra) }.getOrNull() ?: continue
                // NPV policy updates require a signed creator contract, not an unsigned URL.
                if(extra.has("npvContainer"))continue
                val m=extra.optJSONObject("profileMetadata") ?: continue
                val interval=intervalMillis(m) ?: continue
                val now=System.currentTimeMillis()
                val last=m.optLong("lastUpdated",0)
                if(now>=last && now-last<interval)continue
                if(now-prefs.getLong(c.id+".attempt",0)<15*60_000L)continue
                prefs.edit().putLong(c.id+".attempt",now).putString(c.id,"stale").apply()
                try {
                    val uri=remote(m)
                    // The very addresses validated by Dns are passed to the socket connector:
                    // a second DNS lookup cannot rebind a public hostname to a private endpoint.
                    val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
                        .dns(object : Dns { override fun lookup(hostname: String): List<InetAddress> = InetAddress.getAllByName(hostname).toList().also { addresses ->
                            require(addresses.isNotEmpty() && addresses.all(::publicAddress))
                        } }).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                        .connectTimeout(15,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
                        .callTimeout(30,TimeUnit.SECONDS).build()
                    val raw=try {
                        client.newCall(Request.Builder().url(uri.toString()).header("User-Agent","GhajarVPN/1.1.1").build()).execute().use { response ->
                            require(response.code==200)
                            response.body!!.byteStream().use { BoundedInput.read(it,8L*1024*1024) }.toString(Charsets.UTF_8)
                        }
                    } finally { client.connectionPool.evictAll();client.dispatcher.executorService.shutdown() }
                    BoundedJson.objectValue(raw)
                    m.put("lastUpdated",now)
                    extra.put("rawConfig",raw)
                    // Do not overwrite an edit/delete/import that happened while the request ran.
                    withContext(Dispatchers.Main) {
                        if(store.configs.value.firstOrNull { it.id==c.id }?.extra==c.extra) store.update(c.copy(extra=extra.toString()))
                    }
                    prefs.edit().putString(c.id,"updated").apply()
                } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(_: Exception) { prefs.edit().putString(c.id,"error").apply() }
            }
        } finally { lock.unlock() }
    }
}

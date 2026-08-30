package net.gozar.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal object LocationFetcher {
    private val _lastIp = MutableStateFlow("")
    val lastIp = _lastIp.asStateFlow()
    fun showIp(ip: String) { _lastIp.value = ip }

    suspend fun fetch(throughProxy: Boolean, onIp: suspend (String) -> Unit): IpLocation? =
        withContext(Dispatchers.IO) {
            val proxy = if (throughProxy) Proxy(Proxy.Type.SOCKS,
                InetSocketAddress("127.0.0.1", MixedPort.value)) else Proxy.NO_PROXY
            val ip = listOf("https://api.ipify.org", "https://api64.ipify.org")
                .firstNotNullOfOrNull { GhajarLocationRules.numericIp(httpGet(proxy, it, 128).orEmpty()) }
            if (ip != null) onIp(ip)
            val suffix = ip.orEmpty()
            val providers = listOf(
                "https://ipwho.is/$suffix" to false,
                "https://free.freeipapi.com/api/json/$suffix" to true
            )
            for ((url, freeIp) in providers) {
                currentCoroutineContext().ensureActive()
                val body = httpGet(proxy, url, 32768) ?: continue
                val parsed = parse(body, freeIp, ip) ?: continue
                onIp(parsed.ip)
                return@withContext parsed
            }
            null
        }

    internal fun parse(body: String, freeIp: Boolean, expectedIp: String?): IpLocation? = try {
        val json = JSONObject(body)
        if (json.optBoolean("error", false) || !json.optBoolean("success", true)) null
        else GhajarLocationRules.validate(IpLocation(
            ip = json.optString(if (freeIp) "ipAddress" else "ip"),
            city = json.optString(if (freeIp) "cityName" else "city"),
            country = json.optString(if (freeIp) "countryName" else "country"),
            countryCode = json.optString(if (freeIp) "countryCode" else "country_code"),
            lat = json.optDouble("latitude", Double.NaN),
            lon = json.optDouble("longitude", Double.NaN)
        ), expectedIp)
    } catch (_: Exception) { null }

    private suspend fun httpGet(proxy: Proxy, url: String, limit: Int): String? {
        currentCoroutineContext().ensureActive()
        var connection: HttpsURLConnection? = null
        return try {
            connection = (URL(url).openConnection(proxy) as HttpsURLConnection).apply {
                connectTimeout = 5000; readTimeout = 5000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Ghajarvpn")
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (connection.responseCode != 200 || connection.contentLengthLong > limit) return null
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > limit) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            currentCoroutineContext().ensureActive()
            bytes.toString(Charsets.UTF_8)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { null }
        finally { connection?.disconnect() }
    }
}

internal object GhajarLocationMonitor {
    private val _snapshot = MutableStateFlow(GhajarLocationSnapshot())
    val snapshot = _snapshot.asStateFlow()
    val refresh = MutableStateFlow(0)
    fun retry() { refresh.value += 1 }
    fun publish(value: GhajarLocationSnapshot) {
        _snapshot.value = value
        LocationFetcher.showIp(value.ip)
    }
}

@Composable
private fun currentLocationSession(): GhajarLocationSession {
    val connection by VpnState.state.collectAsState()
    val id by VpnState.activeId.collectAsState()
    val at by VpnState.connectedAt.collectAsState()
    val locked by ConfigStore.get(LocalContext.current).killSwitch.collectAsState()
    return GhajarLocationSession(
        connection,
        id,
        at,
        locked,
        IkeController.active || id?.startsWith("ovpn:") == true
    )
}

/** One lifecycle owner, shared by all four home styles. No stale result survives a reconnect. */
@Composable
internal fun ObserveGhajarLocation() {
    val session = currentLocationSession()
    val refresh by GhajarLocationMonitor.refresh.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(session, refresh, lifecycle) {
        GhajarLocationMonitor.publish(GhajarLocationSnapshot(session))
        if (session.connection !in listOf(Connection.CONNECTED, Connection.DISCONNECTED) ||
            (session.connection == Connection.DISCONNECTED && session.locked)) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var result = GhajarLocationSnapshot(session, loading = true)
            GhajarLocationMonitor.publish(result)
            val connected = session.connection == Connection.CONNECTED
            if (connected) delay(1000)
            repeat(if (connected) 3 else 1) { attempt ->
                val location = LocationFetcher.fetch(connected && !session.nativeTunnel) { ip ->
                    currentCoroutineContext().ensureActive()
                    result = result.copy(ip = ip)
                    GhajarLocationMonitor.publish(result)
                }
                currentCoroutineContext().ensureActive()
                if (location != null) {
                    GhajarLocationMonitor.publish(result.copy(ip = location.ip, location = location, loading = false))
                    return@repeatOnLifecycle
                }
                if (connected && attempt < 2) delay(2000L * (attempt + 1))
            }
            GhajarLocationMonitor.publish(result.copy(loading = false))
        }
    }
}

@Composable
internal fun rememberGhajarLocation(): GhajarLocationSnapshot {
    val session = currentLocationSession()
    val snapshot by GhajarLocationMonitor.snapshot.collectAsState()
    return snapshot.forSession(session)
}

@Composable
internal fun GhajarLocationStatus(snapshot: GhajarLocationSnapshot, modifier: Modifier = Modifier) {
    val fa = LocalLang.current == Lang.FA
    val connection = snapshot.session?.connection
    val label = when {
        connection == Connection.CONNECTING -> if (fa) "در انتظار اتصال…" else "Waiting for connection…"
        snapshot.session?.locked == true && connection != Connection.CONNECTED ->
            if (fa) "اینترنت با قطع اضطراری مسدود است" else "Traffic blocked by kill switch"
        snapshot.loading -> if (fa) "در حال بررسی IP و کشور…" else "Checking IP and country…"
        connection == Connection.ERROR -> if (fa) "اتصال برقرار نشد" else "Connection unavailable"
        else -> if (fa) "موقعیت IP دریافت نشد" else "IP location unavailable"
    }
    Column(modifier.padding(horizontal = 12.dp).testTag("ghajar_location_status"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (snapshot.ip.isNotBlank()) Text(snapshot.ip, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!snapshot.loading && connection in listOf(Connection.CONNECTED, Connection.DISCONNECTED) &&
                !(snapshot.session?.locked == true && connection != Connection.CONNECTED)) {
                TextButton(onClick = GhajarLocationMonitor::retry) { Text(if (fa) "بررسی دوباره" else "Retry") }
            }
        }
    }
}

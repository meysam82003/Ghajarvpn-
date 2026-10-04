package net.gozar.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where the app's own public IP and its country come from.
 *
 * This used to live in Globe.kt beside the rotating globe renderer. The globe
 * is gone; the lookup it fed is still needed - the home screen and the
 * connection hub show the live IP and country - so it moved out intact rather
 * than being deleted with its old host.
 */
data class IpLocation(
    val ip: String,
    val city: String,
    val country: String,
    val countryCode: String,
    val lat: Double,
    val lon: Double
)

internal val TehranFallback = IpLocation("\u2014", "Tehran", "Iran", "IR", 35.6892, 51.3890)

object LocationFetcher {
    private const val GEO_TAG = "GhajarGeo"

    private val _lastIp = MutableStateFlow("")
    val lastIp: StateFlow<String> = _lastIp.asStateFlow()

    suspend fun fetch(throughProxy: Boolean): IpLocation? = withContext(Dispatchers.IO) {
        val proxy = if (throughProxy)
            java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", MixedPort.value))
        else java.net.Proxy.NO_PROXY
        android.util.Log.d(GEO_TAG, "fetch throughProxy=" + throughProxy +
                " via=" + (if (throughProxy) "127.0.0.1:" + MixedPort.value else "direct"))
        val ip = fetchPlainIp(proxy, "https://api4.ipify.org")
            ?: fetchPlainIp(proxy, "https://api6.ipify.org")
        if (ip == null) {
            android.util.Log.w(GEO_TAG, "ipify returned nothing")
        } else {
            android.util.Log.d(GEO_TAG, "ipify -> " + ip)
        }
        var out = fromIpWhoIs(proxy, ip)
            ?: fromFreeIpApi(proxy, ip)
            ?: fromIpApiCo(proxy, ip)
        if (out == null && ip != null && throughProxy) {
            android.util.Log.d(GEO_TAG, "proxied lookup refused, retrying direct for " + ip)
            val direct = java.net.Proxy.NO_PROXY
            out = fromIpWhoIs(direct, ip)
                ?: fromFreeIpApi(direct, ip)
                        ?: fromIpApiCo(direct, ip)
        }
        if (out == null) android.util.Log.w(GEO_TAG, "all geo providers failed for " + ip)
        else android.util.Log.d(GEO_TAG,
            "geo -> " + out.ip + " " + out.city + ", " + out.country +
                    " (" + out.lat + "," + out.lon + ")")
        out?.ip?.takeIf { it.isNotBlank() && it != "\u2014" }?.let { _lastIp.value = it }
        out
    }

    private fun httpGet(proxy: java.net.Proxy, url: String, timeout: Int): String? = try {
        val conn = (java.net.URL(url).openConnection(proxy)
                as javax.net.ssl.HttpsURLConnection).apply {
            connectTimeout = timeout; readTimeout = timeout; requestMethod = "GET"
            setRequestProperty("User-Agent", "GozarNet")
        }
        val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        conn.disconnect()
        body
    } catch (e: Exception) { null }

    private fun fromIpWhoIs(proxy: java.net.Proxy, ip: String?): IpLocation? {
        return try {
            val url = if (ip != null) "https://ipwho.is/$ip" else "https://ipwho.is/"
            val body = httpGet(proxy, url, 8000) ?: return null
            val o = JSONObject(body)
            if (!o.optBoolean("success", true)) {
                android.util.Log.w(GEO_TAG, "ipwho.is: " + o.optString("message", "failed"))
                return null
            }
            val lat = o.optDouble("latitude", Double.NaN)
            val lon = o.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) {
                android.util.Log.w(GEO_TAG, "ipwho.is: no coordinates in response")
                return null
            }
            IpLocation(
                ip = ip ?: o.optString("ip", "\u2014"),
                city = o.optString("city", "\u2014"),
                country = o.optString("country", "\u2014"),
                countryCode = o.optString("country_code", ""),
                lat = lat,
                lon = lon
            )
        } catch (e: Exception) { null }
    }

    private fun fromFreeIpApi(proxy: java.net.Proxy, ip: String?): IpLocation? {
        return try {
            val url = if (ip != null) "https://freeipapi.com/api/json/$ip"
            else "https://freeipapi.com/api/json"
            val body = httpGet(proxy, url, 8000) ?: return null
            val o = JSONObject(body)
            val lat = o.optDouble("latitude", Double.NaN)
            val lon = o.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null
            IpLocation(
                ip = ip ?: o.optString("ipAddress", "\u2014"),
                city = o.optString("cityName", "\u2014"),
                country = o.optString("countryName", "\u2014"),
                countryCode = o.optString("countryCode", ""),
                lat = lat,
                lon = lon
            )
        } catch (e: Exception) { null }
    }

    private fun fromIpApiCo(proxy: java.net.Proxy, ip: String?): IpLocation? {
        return try {
            val url = if (ip != null) "https://ipapi.co/$ip/json/" else "https://ipapi.co/json/"
            val body = httpGet(proxy, url, 8000) ?: return null
            val o = JSONObject(body)
            if (o.optBoolean("error", false)) return null
            val lat = o.optDouble("latitude", Double.NaN)
            val lon = o.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null
            IpLocation(
                ip = ip ?: o.optString("ip", "\u2014"),
                city = o.optString("city", "\u2014"),
                country = o.optString("country_name", "\u2014"),
                countryCode = o.optString("country_code", ""),
                lat = lat,
                lon = lon
            )
        } catch (e: Exception) { null }
    }

    private fun fetchPlainIp(proxy: java.net.Proxy, url: String): String? = try {
        val c = (java.net.URL(url).openConnection(proxy)
                as javax.net.ssl.HttpsURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000; requestMethod = "GET"
            setRequestProperty("User-Agent", "GozarNet")
        }
        val s = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.trim()
        c.disconnect()
        s.takeIf { it.isNotEmpty() && it.length <= 45 && it.none(Char::isWhitespace) && ('.' in it || ':' in it) }
    } catch (e: Exception) { null }
}

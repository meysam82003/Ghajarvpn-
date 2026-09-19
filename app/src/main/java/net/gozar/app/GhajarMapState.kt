package net.gozar.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Where you are and where you come out, for the map.
 *
 * The interesting problem this solves is that "your real location" cannot be
 * looked up while a tunnel is running - every lookup goes out through the
 * tunnel and comes back as the exit. So the real one is recorded the last time
 * the app saw the network with no tunnel, and kept. That is why the map labels
 * it "recorded without a VPN" rather than "your location": it is the truth as
 * of a moment, and saying which moment is the honest version.
 *
 * Nothing here asks Android for a location permission. The app has no business
 * with GPS, and a VPN app asking for your physical position is exactly the
 * thing a user should refuse. Both pins come from how the network sees the
 * address, which is also the only thing that matters for "where do I appear to
 * be".
 */
object GhajarMapState {

    /**
     * A place on the map.
     *
     * [lat] and [lon] are nullable because a lookup can name a country and
     * stop there. A Place with no coordinates still draws - it falls back to
     * the country centre - and one with no country at all does not draw,
     * because a pin in the wrong place is worse than no pin on a screen whose
     * entire job is telling the user where they appear to be.
     */
    data class Place(
        val ip: String = "",
        val city: String = "",
        val countryCode: String = "",
        val lat: Double? = null,
        val lon: Double? = null,
        val atMs: Long = 0L
    ) {
        /** The coordinates to draw at, or null when there is nothing truthful. */
        val point: Pair<Double, Double>?
            get() {
                if (lat != null && lon != null) return lat to lon
                return GhajarWorldMap.countryCentre(countryCode)
            }

        /** True when the coordinates are the country's middle, not the city's. */
        val approximate: Boolean get() = lat == null || lon == null

        val label: String
            get() = when {
                city.isNotBlank() && countryCode.isNotBlank() -> "$city, $countryCode"
                city.isNotBlank() -> city
                countryCode.isNotBlank() -> countryCode
                else -> ""
            }

        val usable: Boolean get() = point != null

        fun toJson(): JSONObject = JSONObject()
            .put("ip", ip).put("city", city).put("cc", countryCode)
            .apply {
                if (lat != null) put("lat", lat)
                if (lon != null) put("lon", lon)
            }
            .put("at", atMs)

        companion object {
            fun fromJson(o: JSONObject) = Place(
                ip = o.optString("ip", ""),
                city = o.optString("city", ""),
                countryCode = o.optString("cc", ""),
                lat = if (o.has("lat")) o.optDouble("lat").takeIf { !it.isNaN() } else null,
                lon = if (o.has("lon")) o.optDouble("lon").takeIf { !it.isNaN() } else null,
                atMs = o.optLong("at", 0L)
            )
        }
    }

    /** Your own location, as last seen with no tunnel running. */
    private val _home = MutableStateFlow(Place())
    val home: StateFlow<Place> = _home.asStateFlow()

    /** Where traffic currently comes out, or a blank Place when it does not. */
    private val _exit = MutableStateFlow(Place())
    val exit: StateFlow<Place> = _exit.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The last failure, for the screen to show instead of an empty map. */
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    @Volatile
    private var loadedHome = false

    private fun prefs(context: Context) =
        context.getSharedPreferences("ghajar_map", Context.MODE_PRIVATE)

    private fun restoreHome(context: Context) {
        if (loadedHome) return
        loadedHome = true
        val raw = prefs(context).getString(KEY_HOME, null) ?: return
        runCatching { Place.fromJson(JSONObject(raw)) }
            .onSuccess { if (it.usable) _home.value = it }
    }

    private fun persistHome(context: Context, place: Place) {
        runCatching {
            prefs(context).edit().putString(KEY_HOME, place.toJson().toString()).apply()
        }
    }

    /**
     * Looks up the current address and files it as home or as the exit.
     *
     * [connected] decides which, and it has to be passed in rather than read
     * here: this is also called from the moment a tunnel comes up or goes
     * down, and VpnState is the thing that knows.
     */
    suspend fun refresh(context: Context, connected: Boolean) {
        restoreHome(context)
        if (_busy.value) return
        _busy.value = true
        _error.value = ""
        try {
            val intel = withContext(Dispatchers.IO) {
                // Empty query means "tell me about the address I am calling
                // from", which is exactly the question being asked.
                IpIntelligence.lookup("")
            }
            if (intel == null) {
                _error.value = "lookup_failed"
                return
            }
            val place = Place(
                ip = intel.ip,
                city = intel.city,
                countryCode = intel.countryCode,
                lat = intel.latitude,
                lon = intel.longitude,
                atMs = System.currentTimeMillis()
            )
            if (connected) {
                _exit.value = place
            } else {
                // No tunnel, so this is genuinely where the user is. Keep the
                // old one when the new one has nothing to draw, rather than
                // replacing a known location with a blank.
                if (place.usable) {
                    _exit.value = Place()
                    _home.value = place
                    persistHome(context, place)
                } else {
                    _error.value = "no_location"
                }
            }
        } finally {
            _busy.value = false
        }
    }

    /** Called when a tunnel goes down: the exit no longer exists. */
    fun clearExit() {
        _exit.value = Place()
    }

    private const val KEY_HOME = "home_place"
}

package net.gozar.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The countries Psiphon will actually let you exit from.
 *
 * The exit country used to be a two-character text field, which meant knowing
 * that DE is Germany and - worse - guessing whether Psiphon even has a server
 * there. Typing a region it does not serve is not rejected: the tunnel simply
 * never establishes, which looks exactly like the network being blocked.
 *
 * So the list is not hardcoded. Psiphon emits an `AvailableEgressRegions`
 * notice as it starts, PsiphonTunnel already turns that into
 * `onAvailableEgressRegions(List<String>)`, and this object is where it lands
 * and is kept. The list is therefore always the truth for this client, this
 * build, and today's network - none of which a hardcoded list can promise.
 *
 * [FALLBACK] covers the one gap in that: before a first successful connect
 * there is nothing to report. It is only used until real regions arrive, and
 * [fromEngine] tells the two apart so the screen can say which it is showing.
 */
object PsiphonRegions {

    /**
     * Where the current list came from.
     *
     * Worth surfacing rather than hiding: a fallback list can offer a country
     * that turns out to have no server, and a user who connects and waits
     * deserves to know the list was a guess.
     */
    private val _fromEngine = MutableStateFlow(false)
    val fromEngine: StateFlow<Boolean> = _fromEngine.asStateFlow()

    private val _regions = MutableStateFlow(FALLBACK)
    val regions: StateFlow<List<String>> = _regions.asStateFlow()

    /**
     * Regions Psiphon has reported to this client at some point.
     *
     * Only used before the engine speaks for itself. Taken from Psiphon's own
     * published region list; a country here that has no server today shows up
     * as a tunnel that will not establish, which is why the screen says the
     * list is provisional until the engine replaces it.
     */
    val FALLBACK = listOf(
        "AT", "AU", "BE", "BG", "CA", "CH", "CZ", "DE", "DK", "EE", "ES",
        "FI", "FR", "GB", "HR", "HU", "IE", "IN", "IT", "JP", "LT", "LV",
        "MX", "NL", "NO", "PL", "PT", "RO", "RS", "SE", "SG", "SI", "SK",
        "US"
    )

    private const val KEY = "psiphon_regions"

    private fun prefs(context: Context) =
        context.getSharedPreferences("ghajar_psiphon", Context.MODE_PRIVATE)

    /**
     * Restores the last list the engine reported.
     *
     * Without this the picker would fall back to the static list every cold
     * start, even for someone who has connected a hundred times - the engine
     * only reports regions once a tunnel is coming up, which is far too late
     * for a screen the user is looking at now.
     */
    fun restore(context: Context) {
        if (_fromEngine.value) return
        val saved = runCatching { prefs(context).getString(KEY, null) }.getOrNull()
        val parsed = saved?.split(",")?.map { it.trim().uppercase() }?.filter { it.length == 2 }
        if (!parsed.isNullOrEmpty()) {
            _regions.value = parsed.sorted()
            _fromEngine.value = true
        }
    }

    /**
     * Called from the Psiphon host service when the engine reports its list.
     *
     * An empty report is ignored rather than written: Psiphon sends this
     * notice more than once while establishing, and an early empty one would
     * otherwise wipe a good list and leave the picker with nothing.
     */
    fun report(context: Context?, reported: List<String>) {
        val clean = reported.map { it.trim().uppercase() }
            .filter { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } }
            .distinct()
            .sorted()
        if (clean.isEmpty()) return
        _regions.value = clean
        _fromEngine.value = true
        GhajarLog.i(TAG, "psiphon offers ${clean.size} exit regions")
        if (context != null) {
            runCatching {
                prefs(context).edit().putString(KEY, clean.joinToString(",")).apply()
            }
        }
    }

    /**
     * The country's name in the user's language, or the bare code.
     *
     * Uses the platform's own locale data rather than a table in this app:
     * Android already ships every country name in every language it supports,
     * so a table here would be a worse copy that goes stale.
     */
    fun displayName(code: String, lang: Lang): String {
        val trimmed = code.trim().uppercase()
        if (trimmed.length != 2) return trimmed
        val locale = java.util.Locale(if (lang == Lang.FA) "fa" else "en", trimmed)
        val name = runCatching { locale.getDisplayCountry(locale) }.getOrNull()
        return if (name.isNullOrBlank() || name == trimmed) trimmed else name
    }

    private const val TAG = "Psiphon"
}

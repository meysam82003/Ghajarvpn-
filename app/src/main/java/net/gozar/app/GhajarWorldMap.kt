package net.gozar.app

import android.content.Context
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The world's coastlines and borders, as flat arrays ready to draw.
 *
 * Why an asset and not a map service: this app is used where network access is
 * the thing being fought over. A map that needs its own connection would be
 * blank exactly when the user most wants to see where their traffic goes, and
 * it would add a second party watching them look. The outlines are Natural
 * Earth 110m (public domain), simplified and quantised into
 * assets/worldmap.bin by scripts/build-worldmap.py - 268 rings, about 17 KB.
 *
 * Coordinates come back already projected to 0..1 on both axes, equirectangular
 * (plate carree): x = (lon+180)/360, y = (90-lat)/180. That projection is
 * chosen because it is the one where a point can be placed from a latitude and
 * longitude with two divisions and no trigonometry, which is what actually
 * matters here - every pin on this map comes from a geo lookup, and a
 * projection whose inverse needs solving is a projection that gets pins wrong.
 */
object GhajarWorldMap {

    /** Each ring as [x0, y0, x1, y1, ...] in 0..1 space. */
    @Volatile
    private var rings: List<FloatArray>? = null

    /** Null until [load] has been called and succeeded. */
    val outlines: List<FloatArray>? get() = rings

    private const val MAGIC = "GJWM"
    private const val ASSET = "worldmap.bin"

    /**
     * Reads the asset. Safe to call repeatedly; the work happens once.
     *
     * Returns false rather than throwing when the asset is missing or
     * malformed: a map that cannot draw its land should leave the screen
     * showing the pins and the text, not take the screen down.
     */
    fun load(context: Context): Boolean {
        rings?.let { return true }
        return synchronized(this) {
            rings?.let { return true }
            val parsed = runCatching { parse(context) }
                .onFailure { GhajarLog.w(TAG, "map asset unusable: ${it.javaClass.simpleName}") }
                .getOrNull()
            rings = parsed
            parsed != null
        }
    }

    private fun parse(context: Context): List<FloatArray> {
        val bytes = context.assets.open(ASSET).use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(4).also { buf.get(it) }.toString(Charsets.US_ASCII)
        require(magic == MAGIC) { "not a world map asset" }
        val version = buf.short.toInt()
        require(version == 1) { "world map version $version" }
        val count = buf.int
        require(count in 1..20_000) { "implausible ring count $count" }

        val out = ArrayList<FloatArray>(count)
        repeat(count) {
            val points = buf.short.toInt() and 0xFFFF
            require(points in 3..100_000) { "implausible ring length $points" }
            val ring = FloatArray(points * 2)
            for (i in 0 until points) {
                // Stored as hundredths of a degree; see the writer.
                val lon = buf.short.toInt() / 100f
                val lat = buf.short.toInt() / 100f
                ring[i * 2] = (lon + 180f) / 360f
                ring[i * 2 + 1] = (90f - lat) / 180f
            }
            out.add(ring)
        }
        return out
    }

    /** A latitude and longitude as a 0..1 point on the same projection. */
    fun project(lat: Double, lon: Double): Pair<Float, Float> {
        val x = ((lon + 180.0) / 360.0).coerceIn(0.0, 1.0).toFloat()
        val y = ((90.0 - lat) / 180.0).coerceIn(0.0, 1.0).toFloat()
        return x to y
    }

    private const val TAG = "GhajarMap"

    /**
     * Where a country is, for when a geo lookup gives a country but no city.
     *
     * Deliberately small and deliberately approximate: it exists so the map
     * can show *something* truthful rather than nothing, and a pin in the
     * middle of the right country is a better answer than an empty map. The
     * ones here are the countries this app's servers and users actually sit
     * in; anything else falls through to null and the map says it does not
     * know instead of guessing.
     */
    private val CENTRES: Map<String, Pair<Double, Double>> = mapOf(
        "IR" to (32.4 to 53.7), "DE" to (51.2 to 10.4), "NL" to (52.1 to 5.3),
        "FR" to (46.6 to 2.3), "GB" to (54.0 to -2.0), "US" to (39.8 to -98.6),
        "CA" to (56.1 to -106.3), "TR" to (39.0 to 35.2), "AE" to (24.0 to 54.0),
        "RU" to (61.5 to 105.3), "SE" to (60.1 to 18.6), "FI" to (64.0 to 26.0),
        "NO" to (60.5 to 8.5), "DK" to (56.3 to 9.5), "PL" to (51.9 to 19.1),
        "CZ" to (49.8 to 15.5), "AT" to (47.5 to 14.6), "CH" to (46.8 to 8.2),
        "IT" to (41.9 to 12.6), "ES" to (40.5 to -3.7), "PT" to (39.4 to -8.2),
        "RO" to (45.9 to 25.0), "BG" to (42.7 to 25.5), "HU" to (47.2 to 19.5),
        "UA" to (48.4 to 31.2), "LT" to (55.2 to 23.9), "LV" to (56.9 to 24.6),
        "EE" to (58.6 to 25.0), "IE" to (53.4 to -8.2), "BE" to (50.5 to 4.5),
        "LU" to (49.8 to 6.1), "JP" to (36.2 to 138.3), "SG" to (1.35 to 103.8),
        "HK" to (22.3 to 114.2), "KR" to (35.9 to 127.8), "IN" to (20.6 to 79.0),
        "AU" to (-25.3 to 133.8), "BR" to (-14.2 to -51.9), "ZA" to (-30.6 to 22.9),
        "AM" to (40.1 to 45.0), "GE" to (42.3 to 43.4), "AZ" to (40.1 to 47.6),
        "IQ" to (33.2 to 43.7), "QA" to (25.4 to 51.2), "KW" to (29.3 to 47.5),
        "OM" to (21.5 to 55.9), "SA" to (23.9 to 45.1), "MD" to (47.4 to 28.4),
        "RS" to (44.0 to 21.0), "HR" to (45.1 to 15.2), "SI" to (46.2 to 15.0),
        "SK" to (48.7 to 19.7), "GR" to (39.1 to 21.8), "CY" to (35.1 to 33.4),
        "IL" to (31.0 to 34.9), "EG" to (26.8 to 30.8), "KZ" to (48.0 to 66.9),
        "NZ" to (-40.9 to 174.9), "MX" to (23.6 to -102.6), "AR" to (-38.4 to -63.6),
        "CL" to (-35.7 to -71.5), "TW" to (23.7 to 121.0), "MY" to (4.2 to 101.9),
        "TH" to (15.9 to 101.0), "VN" to (14.1 to 108.3), "ID" to (-0.8 to 113.9),
        "PH" to (12.9 to 121.8), "PK" to (30.4 to 69.3), "BD" to (23.7 to 90.4),
        "AF" to (33.9 to 67.7), "TM" to (38.9 to 59.6), "UZ" to (41.4 to 64.6)
    )

    /**
     * The middle of a country by its ISO code, or null when it is not listed.
     *
     * Null is a real answer here. A wrong pin is worse than no pin: the whole
     * point of this screen is telling the user where they appear to be.
     */
    fun countryCentre(code: String): Pair<Double, Double>? =
        CENTRES[code.trim().uppercase()]
}

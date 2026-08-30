package net.gozar.app

import android.content.Context

/** Offline artwork only: no host, bot gallery or remote boot request. */
internal object GhajarWelcomeAssets {
    data class Poster(val name: String, val resourceId: Int, val dark: Boolean)
    val posters = listOf(
        Poster("ghajar_welcome_world", R.drawable.ghajar_welcome_world, false),
        Poster("ghajar_welcome_connection", R.drawable.ghajar_welcome_connection, false),
        Poster("ghajar_welcome_locations", R.drawable.ghajar_welcome_locations, false),
        Poster("ghajar_welcome_royal", R.drawable.ghajar_welcome_royal, true),
        Poster("ghajar_welcome_queen_phone", R.drawable.ghajar_welcome_queen_phone, false),
        Poster("ghajar_welcome_king_night", R.drawable.ghajar_welcome_king_night, true),
        Poster("ghajar_welcome_king_light", R.drawable.ghajar_welcome_king_light, false),
        Poster("ghajar_welcome_queen_shield", R.drawable.ghajar_welcome_queen_shield, true),
        Poster("ghajar_welcome_queen_night", R.drawable.ghajar_welcome_queen_night, true),
        Poster("ghajar_welcome_king_world", R.drawable.ghajar_welcome_king_world, false),
        Poster("ghajar_welcome_queen_light", R.drawable.ghajar_welcome_queen_light, false),
        Poster("ghajar_welcome_extra_01", R.drawable.ghajar_welcome_extra_01, true),
        Poster("ghajar_welcome_extra_02", R.drawable.ghajar_welcome_extra_02, true),
        Poster("ghajar_welcome_extra_03", R.drawable.ghajar_welcome_extra_03, true),
        Poster("ghajar_welcome_extra_04", R.drawable.ghajar_welcome_extra_04, true),
        Poster("ghajar_welcome_extra_05", R.drawable.ghajar_welcome_extra_05, false),
        Poster("ghajar_welcome_extra_06", R.drawable.ghajar_welcome_extra_06, true),
        Poster("ghajar_welcome_extra_07", R.drawable.ghajar_welcome_extra_07, true),
        Poster("ghajar_welcome_extra_08", R.drawable.ghajar_welcome_extra_08, false),
        Poster("ghajar_welcome_extra_09", R.drawable.ghajar_welcome_extra_09, true),
        Poster("ghajar_welcome_extra_10", R.drawable.ghajar_welcome_extra_10, true),
        Poster("ghajar_welcome_extra_11", R.drawable.ghajar_welcome_extra_11, false),
        Poster("ghajar_welcome_extra_12", R.drawable.ghajar_welcome_extra_12, true),
        Poster("ghajar_welcome_extra_13", R.drawable.ghajar_welcome_extra_13, true),
        Poster("ghajar_welcome_extra_14", R.drawable.ghajar_welcome_extra_14, true),
        Poster("ghajar_welcome_extra_15", R.drawable.ghajar_welcome_extra_15, true),
        Poster("ghajar_welcome_extra_16", R.drawable.ghajar_welcome_extra_16, false),
        Poster("ghajar_welcome_extra_17", R.drawable.ghajar_welcome_extra_17, false),
        Poster("ghajar_welcome_extra_18", R.drawable.ghajar_welcome_extra_18, false),
        Poster("ghajar_welcome_extra_19", R.drawable.ghajar_welcome_extra_19, false),
        Poster("ghajar_welcome_extra_20", R.drawable.ghajar_welcome_extra_20, false),
        Poster("ghajar_welcome_extra_21", R.drawable.ghajar_welcome_extra_21, true),
        Poster("ghajar_welcome_extra_22", R.drawable.ghajar_welcome_extra_22, false)
    )

    @Synchronized
    fun reserve(context: Context): String {
        val prefs = context.getSharedPreferences("ghajar_welcome", Context.MODE_PRIVATE)
        val pick = requireNotNull(GhajarWelcomeRotation.next(posters.map { it.name },
            prefs.getStringSet("seen_in_cycle", emptySet()).orEmpty(), prefs.getString("last_name", null)))
        // The poster must be available for the first frame; rotation persistence
        // can finish asynchronously without delaying launch rendering.
        prefs.edit().putStringSet("seen_in_cycle", pick.seen).putString("last_name", pick.name).apply()
        return pick.name
    }
}

package net.gozar.app

import android.content.Context
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Everything the Personalization page controls, in one value.
 *
 * The base theme (the four palettes and "system") stays where it always was,
 * in [ConfigStore.uiTheme]; this sits on top of it: a preset, an accent, AMOLED
 * black, per-element colours, motion, the navigation bar, the connect button
 * and the card layout. Stored as one JSON object so export/import and "reset
 * all" are a single write.
 */
@Immutable
data class GhajarLook(
    val preset: String = "default",
    /** ARGB of the accent, or null for the preset's own. */
    val accent: Long? = null,
    val amoled: Boolean = false,
    val dynamic: Boolean = false,
    /** Per-element overrides, keyed by [LookElement.key]. */
    val colors: Map<String, Long> = emptyMap(),
    val transition: String = "default",
    val speed: String = "normal",
    val navStyle: String = "floating",
    val navIconSize: Int = 23,
    val navLabels: Boolean = true,
    val navRadius: Int = 28,
    val orbStyle: String = "circle",
    val columns: Int = 1,
    val density: String = "comfortable",
    val cardRadius: Int = 24,
    val elevation: Int = 0,
    val fontScale: Float = 1f,
    val boldTitles: Boolean = true,
    // --- Settings (tile grid) ---
    /** Tile ids in the order the user arranged them; ids not listed keep their default place. */
    val settingsOrder: List<String> = emptyList(),
    /** Hidden tiles. Critical tiles (see SettingsTiles.CRITICAL) are never hidden. */
    val settingsHidden: Set<String> = emptySet(),
    val tileSize: String = "normal",
    val settingsLayout: String = "comfortable",
    val iconStyle: String = "tinted",
    val gridGap: Int = 10,
    // --- Home ---
    val homeOrder: List<String> = emptyList(),
    val homeHidden: Set<String> = emptySet(),
    /** Per-section size: compact / normal / large. */
    val homeSizes: Map<String, String> = emptyMap(),
    // --- Store ---
    val storeCardStyle: String = "card",
    val storeButtonStyle: String = "filled",
    val storeTabStyle: String = "pill",
    val storeShow: Set<String> = StoreParts.toSet(),
    // --- Server selector ---
    val serverView: String = "list",
    val serverFields: Set<String> = ServerFieldsDefault,
    // --- Add server ---
    val addServerStyle: String = "sections",
    // --- Nav indicator shape ---
    val navIndicator: String = "rounded",
    val navSpacing: Int = 0,
    /** User-saved presets: name -> exported JSON. */
    val customPresets: Map<String, String> = emptyMap()
) {
    fun color(key: String): Color? = colors[key]?.let { Color(it.toInt()) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1)
        put("preset", preset)
        accent?.let { put("accent", it) }
        put("amoled", amoled)
        put("dynamic", dynamic)
        put("colors", JSONObject().apply { colors.forEach { (k, v) -> put(k, v) } })
        put("transition", transition)
        put("speed", speed)
        put("navStyle", navStyle)
        put("navIconSize", navIconSize)
        put("navLabels", navLabels)
        put("navRadius", navRadius)
        put("orbStyle", orbStyle)
        put("columns", columns)
        put("density", density)
        put("cardRadius", cardRadius)
        put("elevation", elevation)
        put("fontScale", fontScale.toDouble())
        put("boldTitles", boldTitles)
        put("settingsOrder", org.json.JSONArray(settingsOrder))
        put("settingsHidden", org.json.JSONArray(settingsHidden.toList()))
        put("tileSize", tileSize)
        put("settingsLayout", settingsLayout)
        put("iconStyle", iconStyle)
        put("gridGap", gridGap)
        put("homeOrder", org.json.JSONArray(homeOrder))
        put("homeHidden", org.json.JSONArray(homeHidden.toList()))
        put("homeSizes", JSONObject().apply { homeSizes.forEach { (k, v) -> put(k, v) } })
        put("storeCardStyle", storeCardStyle)
        put("storeButtonStyle", storeButtonStyle)
        put("storeTabStyle", storeTabStyle)
        put("storeShow", org.json.JSONArray(storeShow.toList()))
        put("serverView", serverView)
        put("serverFields", org.json.JSONArray(serverFields.toList()))
        put("addServerStyle", addServerStyle)
        put("navIndicator", navIndicator)
        put("navSpacing", navSpacing)
        put("customPresets", JSONObject().apply { customPresets.forEach { (k, v) -> put(k, v) } })
    }

    /**
     * The versioned export: the look plus the schema and app version it came
     * from, grouped the way the Personalization page groups it. [fromJson]
     * reads both this and the flat v1 shape.
     */
    fun toExport(appVersion: String): JSONObject {
        val flat = toJson()
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("appVersion", appVersion)
            .put("kind", "ghajar-appearance")
            .put("theme", JSONObject().put("preset", preset).put("accent", accent ?: JSONObject.NULL)
                .put("amoled", amoled).put("dynamic", dynamic))
            .put("colors", flat.getJSONObject("colors"))
            .put("typography", JSONObject().put("fontScale", fontScale.toDouble()).put("boldTitles", boldTitles))
            .put("animations", JSONObject().put("transition", transition).put("speed", speed))
            .put("homeLayout", JSONObject().put("order", flat.get("homeOrder")).put("hidden", flat.get("homeHidden"))
                .put("sizes", flat.get("homeSizes")).put("columns", columns).put("density", density))
            .put("storeLayout", JSONObject().put("cardStyle", storeCardStyle).put("buttonStyle", storeButtonStyle)
                .put("tabStyle", storeTabStyle).put("show", flat.get("storeShow")))
            .put("settingsLayout", JSONObject().put("order", flat.get("settingsOrder")).put("hidden", flat.get("settingsHidden"))
                .put("tileSize", tileSize).put("layout", settingsLayout).put("iconStyle", iconStyle).put("gridGap", gridGap))
            .put("serverSelectorLayout", JSONObject().put("view", serverView).put("fields", flat.get("serverFields")))
            .put("addServerLayout", JSONObject().put("style", addServerStyle))
            .put("navigationStyle", JSONObject().put("style", navStyle).put("iconSize", navIconSize).put("labels", navLabels)
                .put("radius", navRadius).put("indicator", navIndicator).put("spacing", navSpacing))
            .put("connectionButtonStyle", orbStyle)
            .put("componentPreferences", JSONObject().put("cardRadius", cardRadius).put("elevation", elevation))
            .put("presets", flat.get("customPresets"))
    }

    companion object {
        const val SCHEMA_VERSION = 2
        val Default = GhajarLook()

        /** Reads a versioned export (schemaVersion 2) or the flat v1 object; never throws. */
        fun fromAny(o: JSONObject): GhajarLook = if (o.has("schemaVersion")) fromExport(o) else fromJson(o)

        private fun fromExport(e: JSONObject): GhajarLook {
            // Flatten the grouped shape into the v1 keys and read it through
            // the same validating path.
            val f = JSONObject()
            e.optJSONObject("theme")?.let { t ->
                f.put("preset", t.optString("preset"))
                if (t.has("accent") && !t.isNull("accent")) f.put("accent", t.optLong("accent"))
                f.put("amoled", t.optBoolean("amoled")); f.put("dynamic", t.optBoolean("dynamic"))
            }
            e.optJSONObject("colors")?.let { f.put("colors", it) }
            e.optJSONObject("typography")?.let { f.put("fontScale", it.optDouble("fontScale", 1.0)); f.put("boldTitles", it.optBoolean("boldTitles", true)) }
            e.optJSONObject("animations")?.let { f.put("transition", it.optString("transition")); f.put("speed", it.optString("speed")) }
            e.optJSONObject("homeLayout")?.let { h ->
                h.optJSONArray("order")?.let { f.put("homeOrder", it) }; h.optJSONArray("hidden")?.let { f.put("homeHidden", it) }
                h.optJSONObject("sizes")?.let { f.put("homeSizes", it) }
                if (h.has("columns")) f.put("columns", h.optInt("columns")); if (h.has("density")) f.put("density", h.optString("density"))
            }
            e.optJSONObject("storeLayout")?.let { s ->
                f.put("storeCardStyle", s.optString("cardStyle")); f.put("storeButtonStyle", s.optString("buttonStyle"))
                f.put("storeTabStyle", s.optString("tabStyle")); s.optJSONArray("show")?.let { f.put("storeShow", it) }
            }
            e.optJSONObject("settingsLayout")?.let { s ->
                s.optJSONArray("order")?.let { f.put("settingsOrder", it) }; s.optJSONArray("hidden")?.let { f.put("settingsHidden", it) }
                f.put("tileSize", s.optString("tileSize")); f.put("settingsLayout", s.optString("layout"))
                f.put("iconStyle", s.optString("iconStyle")); if (s.has("gridGap")) f.put("gridGap", s.optInt("gridGap"))
            }
            e.optJSONObject("serverSelectorLayout")?.let { s -> f.put("serverView", s.optString("view")); s.optJSONArray("fields")?.let { f.put("serverFields", it) } }
            e.optJSONObject("addServerLayout")?.let { f.put("addServerStyle", it.optString("style")) }
            e.optJSONObject("navigationStyle")?.let { n ->
                f.put("navStyle", n.optString("style")); if (n.has("iconSize")) f.put("navIconSize", n.optInt("iconSize"))
                if (n.has("labels")) f.put("navLabels", n.optBoolean("labels")); if (n.has("radius")) f.put("navRadius", n.optInt("radius"))
                f.put("navIndicator", n.optString("indicator")); if (n.has("spacing")) f.put("navSpacing", n.optInt("spacing"))
            }
            f.put("orbStyle", e.optString("connectionButtonStyle"))
            e.optJSONObject("componentPreferences")?.let { c ->
                if (c.has("cardRadius")) f.put("cardRadius", c.optInt("cardRadius")); if (c.has("elevation")) f.put("elevation", c.optInt("elevation"))
            }
            e.optJSONObject("presets")?.let { f.put("customPresets", it) }
            return fromJson(f)
        }

        /** Unknown or out-of-range values fall back to the default, never throw. */
        fun fromJson(o: JSONObject): GhajarLook {
            val d = Default
            fun pick(v: String, allowed: List<String>, def: String) = if (v in allowed) v else def
            val colors = mutableMapOf<String, Long>()
            o.optJSONObject("colors")?.let { c ->
                c.keys().forEach { k -> if (LookElement.byKey(k) != null) colors[k] = c.optLong(k) }
            }
            return GhajarLook(
                preset = pick(o.optString("preset"), LookPreset.entries.map { it.key }, d.preset),
                accent = if (o.has("accent")) o.optLong("accent") else null,
                amoled = o.optBoolean("amoled", d.amoled),
                dynamic = o.optBoolean("dynamic", d.dynamic),
                colors = colors,
                transition = pick(o.optString("transition"), LookTransitions, d.transition),
                speed = pick(o.optString("speed"), listOf("slow", "normal", "fast"), d.speed),
                navStyle = pick(o.optString("navStyle"), LookNavStyles, d.navStyle),
                navIconSize = o.optInt("navIconSize", d.navIconSize).coerceIn(18, 30),
                navLabels = o.optBoolean("navLabels", d.navLabels),
                navRadius = o.optInt("navRadius", d.navRadius).coerceIn(0, 40),
                orbStyle = pick(o.optString("orbStyle"), LookOrbStyles, d.orbStyle),
                columns = o.optInt("columns", d.columns).coerceIn(1, 2),
                density = pick(o.optString("density"), listOf("compact", "comfortable", "spacious"), d.density),
                cardRadius = o.optInt("cardRadius", d.cardRadius).coerceIn(4, 36),
                elevation = o.optInt("elevation", d.elevation).coerceIn(0, 12),
                fontScale = o.optDouble("fontScale", d.fontScale.toDouble()).toFloat().let { if (it.isNaN()) 1f else it }.coerceIn(0.85f, 1.25f),
                boldTitles = o.optBoolean("boldTitles", d.boldTitles),
                settingsOrder = strings(o, "settingsOrder").take(64),
                settingsHidden = strings(o, "settingsHidden").toSet(),
                tileSize = pick(o.optString("tileSize"), listOf("compact", "normal", "large"), d.tileSize),
                settingsLayout = pick(o.optString("settingsLayout"), listOf("compact", "comfortable"), d.settingsLayout),
                iconStyle = pick(o.optString("iconStyle"), listOf("tinted", "filled", "outline", "plain"), d.iconStyle),
                gridGap = o.optInt("gridGap", d.gridGap).coerceIn(4, 20),
                homeOrder = strings(o, "homeOrder").filter { it in HomeParts }.distinct(),
                homeHidden = strings(o, "homeHidden").filter { it in HomeParts && it !in HomeCritical }.toSet(),
                homeSizes = o.optJSONObject("homeSizes")?.let { m ->
                    m.keys().asSequence().filter { it in HomeParts }
                        .associateWith { pick(m.optString(it), listOf("compact", "normal", "large"), "normal") }
                } ?: emptyMap(),
                storeCardStyle = pick(o.optString("storeCardStyle"), listOf("card", "compact", "large"), d.storeCardStyle),
                storeButtonStyle = pick(o.optString("storeButtonStyle"), listOf("filled", "outline", "tonal"), d.storeButtonStyle),
                storeTabStyle = pick(o.optString("storeTabStyle"), listOf("pill", "underline", "boxed"), d.storeTabStyle),
                storeShow = if (o.has("storeShow")) strings(o, "storeShow").filter { it in StoreParts }.toSet() else d.storeShow,
                serverView = pick(o.optString("serverView"), listOf("list", "compact", "grid", "large"), d.serverView),
                serverFields = if (o.has("serverFields")) strings(o, "serverFields").filter { it in ServerFieldsAll }.toSet() + "name" else d.serverFields,
                addServerStyle = pick(o.optString("addServerStyle"), listOf("sections", "tabs", "compact"), d.addServerStyle),
                navIndicator = pick(o.optString("navIndicator"), listOf("rounded", "filled", "outline", "dot"), d.navIndicator),
                navSpacing = o.optInt("navSpacing", d.navSpacing).coerceIn(0, 16),
                customPresets = o.optJSONObject("customPresets")?.let { m ->
                    m.keys().asSequence().take(20).associateWith { m.optString(it) }.filterValues { it.isNotBlank() && it.length < 64_000 }
                } ?: emptyMap()
            )
        }
    }
}

private fun strings(o: JSONObject, key: String): List<String> =
    o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } } } ?: emptyList()

/** Home sections that can be arranged, in their default order. */
val HomeParts = listOf("orb", "session", "route", "quota", "traffic", "facts")
/** The connect control and the chosen server can never be hidden. */
val HomeCritical = setOf("orb", "route")
val StoreParts = listOf("logo", "title", "desc", "rating", "links", "report", "discounts", "services_count", "prices", "tick")
val ServerFieldsAll = listOf("flag", "name", "country", "protocol", "core", "ping", "quality", "favorite", "last", "traffic", "test")
val ServerFieldsDefault = setOf("flag", "name", "protocol", "ping", "favorite", "test")

val LookTransitions = listOf(
    "default", "none", "fade", "slide_h", "slide_v", "scale", "axis_x", "axis_y", "axis_z", "fade_through"
)
val LookNavStyles = listOf("floating", "standard", "minimal", "filled", "outline")
val LookOrbStyles = listOf(
    "circle", "pill", "capsule_glow", "soft_square", "double_ring", "neon", "minimal", "segmented", "shield", "power"
)

/** Colourable elements. [group] is the Personalization category they are listed in. */
enum class LookElement(val key: String, val group: String) {
    TILE_DOWN_ICON("tile_down_icon", "tiles"),
    TILE_DOWN_BG("tile_down_bg", "tiles"),
    TILE_UP_ICON("tile_up_icon", "tiles"),
    TILE_UP_BG("tile_up_bg", "tiles"),
    CONNECT("connect", "orb"),
    DISCONNECT("disconnect", "orb"),
    CONFIG_CARD("config_card", "cards"),
    CONFIG_TEXT("config_text", "cards"),
    SERVER_CARD("server_card", "cards"),
    SERVER_ACTIVE("server_active", "cards"),
    NAV_BG("nav_bg", "nav"),
    NAV_INDICATOR("nav_indicator", "nav"),
    NAV_ICON_ACTIVE("nav_icon_active", "nav"),
    NAV_ICON_INACTIVE("nav_icon_inactive", "nav"),
    NAV_LABEL("nav_label", "nav"),
    SWITCH("switch", "appearance"),
    SECONDARY_BUTTON("secondary_button", "appearance"),
    TITLE("title", "typography"),
    SUBTITLE("subtitle", "typography"),
    BORDER("border", "appearance"),
    DIALOG("dialog", "appearance"),
    // The palette itself: surfaces and state colours.
    BACKGROUND("background", "palette"),
    SURFACE("surface", "palette"),
    CARD("card", "palette"),
    SECONDARY_SURFACE("secondary_surface", "palette"),
    SELECTED("selected", "palette"),
    UNSELECTED("unselected", "palette"),
    ICON("icon", "palette"),
    SUCCESS("success", "palette"),
    WARNING("warning", "palette"),
    ERROR("error", "palette"),
    PREMIUM("premium", "palette"),
    BADGE("badge", "palette");

    /** What the element is when nothing overrides it, in [p]. */
    fun default(p: GhajarPalette): Color = when (this) {
        TILE_DOWN_ICON -> p.info
        TILE_DOWN_BG, TILE_UP_BG, CONFIG_CARD -> p.secondaryCard
        TILE_UP_ICON -> p.premium
        CONNECT -> p.primary
        DISCONNECT -> p.successGlow
        CONFIG_TEXT, TITLE -> p.textPrimary
        SERVER_CARD -> Color.Transparent
        SERVER_ACTIVE -> p.primary
        NAV_BG -> p.card
        NAV_INDICATOR, SWITCH -> p.primary
        NAV_ICON_ACTIVE -> p.onPrimary
        NAV_ICON_INACTIVE, NAV_LABEL -> p.textMuted
        SECONDARY_BUTTON -> p.primary
        SUBTITLE -> p.textSecondary
        BORDER -> p.border
        DIALOG -> p.surface
        BACKGROUND -> p.background
        SURFACE -> p.surface
        CARD -> p.card
        SECONDARY_SURFACE -> p.secondaryCard
        SELECTED -> p.highlight
        UNSELECTED -> p.textMuted
        ICON -> p.primary
        SUCCESS -> p.good
        WARNING -> p.warning
        ERROR -> p.error
        PREMIUM -> p.premium
        BADGE -> p.error
    }

    companion object {
        fun byKey(k: String) = entries.firstOrNull { it.key == k }
    }
}

/**
 * Ready-made looks. Each is a full palette: background ramp plus accent, so a
 * preset changes the whole screen, not only the buttons.
 */
enum class LookPreset(
    val key: String,
    val fa: String,
    val en: String,
    val accent: Long,
    val bg: Long,
    val surface: Long,
    val card: Long,
    val secondaryCard: Long,
    val border: Long
) {
    DEFAULT("default", "پیش‌فرض", "Default", 0xFF00A86B, 0xFF050807, 0xFF0B1512, 0xFF101816, 0xFF14231F, 0xFF1B2A26),
    AMOLED("amoled", "AMOLED مشکی", "AMOLED Black", 0xFF24D98B, 0xFF000000, 0xFF050505, 0xFF0A0A0A, 0xFF111111, 0xFF1C1C1C),
    EMERALD("emerald", "زمرد / فیروزه‌ای", "Emerald / Teal", 0xFF14B8A6, 0xFF03100F, 0xFF071A18, 0xFF0B211F, 0xFF10302C, 0xFF173A36),
    OCEAN("ocean", "آبی اقیانوسی", "Ocean Blue", 0xFF3D7BFF, 0xFF04070F, 0xFF0A111F, 0xFF0F1929, 0xFF152238, 0xFF1A2540),
    PURPLE("purple", "شب بنفش", "Purple Night", 0xFFA855F7, 0xFF08050F, 0xFF110A1D, 0xFF170F27, 0xFF1F1533, 0xFF2A1D42),
    ROSE("rose", "صورتی رز", "Rose Pink", 0xFFF43F72, 0xFF0F0508, 0xFF1A0A10, 0xFF220E16, 0xFF2C131D, 0xFF3A1A27),
    SUNSET("sunset", "نارنجی غروب", "Sunset Orange", 0xFFF97316, 0xFF0F0804, 0xFF1A0F07, 0xFF22140A, 0xFF2D1B0E, 0xFF3A2414),
    FOREST("forest", "سبز جنگلی", "Forest Green", 0xFF4CAF50, 0xFF050A05, 0xFF0B150B, 0xFF101C10, 0xFF162616, 0xFF1F331F),
    DYNAMIC("dynamic", "داینامیک / Material You", "Dynamic / Material You", 0xFF00A86B, 0xFF050807, 0xFF0B1512, 0xFF101816, 0xFF14231F, 0xFF1B2A26),
    GOLD("gold", "گرافیت طلایی", "Graphite Gold", 0xFFD8B15C, 0xFF07070A, 0xFF111114, 0xFF17171B, 0xFF1F1E24, 0xFF26242B),
    CRIMSON("crimson", "قرمز یاقوتی", "Crimson", 0xFFEF4444, 0xFF0D0505, 0xFF180909, 0xFF200D0D, 0xFF2A1212, 0xFF381919),
    CYBER("cyber", "سایبر نئون", "Cyber Neon", 0xFF22D3EE, 0xFF03060A, 0xFF070E15, 0xFF0B141D, 0xFF101C28, 0xFF172636),
    MONO("mono", "خاکستری مینیمال", "Monochrome", 0xFFE5E7EB, 0xFF070707, 0xFF101010, 0xFF161616, 0xFF1E1E1E, 0xFF2A2A2A),
    LAVENDER("lavender", "یاسی", "Lavender", 0xFFA5B4FC, 0xFF06060E, 0xFF0D0D1A, 0xFF131324, 0xFF1A1A30, 0xFF24243E),
    MINIMAL("minimal", "مینیمال", "Minimal", 0xFFB8C4CC, 0xFF0B0C0D, 0xFF121315, 0xFF17181A, 0xFF1C1E20, 0xFF26282B),
    HIGH_CONTRAST("high_contrast", "کنتراست بالا", "High contrast", 0xFFFFE600, 0xFF000000, 0xFF000000, 0xFF0A0A0A, 0xFF141414, 0xFFFFFFFF);

    companion object {
        fun byKey(k: String) = entries.firstOrNull { it.key == k } ?: DEFAULT
    }
}

private fun Color.mix(other: Color, t: Float) = Color(
    red + (other.red - red) * t,
    green + (other.green - green) * t,
    blue + (other.blue - blue) * t,
    alpha
)

/** The accent's three tones and a readable ink on top of it. */
fun GhajarPalette.withAccent(a: Color): GhajarPalette {
    val light = !dark
    return copy(
        primary = a,
        premium = if (light) a.mix(Color.Black, 0.12f) else a.mix(Color.White, 0.10f),
        highlight = if (light) a else a.mix(Color.White, 0.25f),
        successGlow = if (light) a else a.mix(Color.White, 0.18f),
        onPrimary = if (a.luminance() > 0.45f) Color(0xFF07100C) else Color.White
    )
}

fun GhajarPalette.amoled(): GhajarPalette = if (!dark) this else copy(
    background = Color.Black,
    surface = Color(0xFF050505),
    card = Color(0xFF0A0A0A),
    secondaryCard = Color(0xFF111111),
    border = Color(0xFF1C1C1C),
    borderStrong = Color(0xFF262626),
    disabled = Color(0xFF161616)
)

/** [base] is the palette from the stored base theme; the look is laid over it. */
fun GhajarLook.apply(base: GhajarPalette, dynamicColor: Color?): GhajarPalette {
    val p = LookPreset.byKey(preset)
    var out = when (p) {
        LookPreset.DEFAULT, LookPreset.DYNAMIC -> base
        else -> base.takeIf { !it.dark }?.withAccent(Color(p.accent.toInt())) ?: base.copy(
            background = Color(p.bg.toInt()),
            surface = Color(p.surface.toInt()),
            card = Color(p.card.toInt()),
            secondaryCard = Color(p.secondaryCard.toInt()),
            border = Color(p.border.toInt()),
            borderStrong = Color(p.border.toInt()).mix(Color.White, 0.06f),
            disabled = Color(p.secondaryCard.toInt()),
            textMuted = Color(0xFF7A807E)
        ).withAccent(Color(p.accent.toInt()))
    }
    if (p == LookPreset.HIGH_CONTRAST) out = out.copy(textPrimary = Color.White, textSecondary = Color(0xFFE6E6E6),
        textMuted = Color(0xFFBDBDBD), borderStrong = Color.White)
    if (amoled || p == LookPreset.AMOLED) out = out.amoled()
    if ((dynamic || p == LookPreset.DYNAMIC) && dynamicColor != null) out = out.withAccent(dynamicColor)
    accent?.let { out = out.withAccent(Color(it.toInt())) }
    color(LookElement.BACKGROUND.key)?.let { out = out.copy(background = it) }
    color(LookElement.SURFACE.key)?.let { out = out.copy(surface = it) }
    color(LookElement.CARD.key)?.let { out = out.copy(card = it) }
    color(LookElement.SECONDARY_SURFACE.key)?.let { out = out.copy(secondaryCard = it) }
    color(LookElement.SELECTED.key)?.let { out = out.copy(highlight = it) }
    color(LookElement.UNSELECTED.key)?.let { out = out.copy(textMuted = it) }
    color(LookElement.SUCCESS.key)?.let { out = out.copy(good = it, successGlow = it) }
    color(LookElement.WARNING.key)?.let { out = out.copy(warning = it) }
    color(LookElement.ERROR.key)?.let { out = out.copy(error = it) }
    color(LookElement.PREMIUM.key)?.let { out = out.copy(premium = it) }
    color(LookElement.TITLE.key)?.let { out = out.copy(textPrimary = it) }
    color(LookElement.SUBTITLE.key)?.let { out = out.copy(textSecondary = it) }
    color(LookElement.BORDER.key)?.let { out = out.copy(border = it) }
    color(LookElement.DIALOG.key)?.let { out = out.copy(surface = it) }
    return out
}

val LocalGhajarLook = staticCompositionLocalOf { GhajarLook.Default }

/** The element's colour: the user's override, else its theme default. */
@Composable
fun lookColor(e: LookElement): Color =
    LocalGhajarLook.current.color(e.key) ?: e.default(ghajarColors)

/** Persisted look. One JSON string in its own prefs file. */
object GhajarLookStore {
    private const val PREFS = "ghajar_look"
    private const val KEY = "look"
    private val _look = MutableStateFlow(GhajarLook.Default)
    val look: StateFlow<GhajarLook> = _look.asStateFlow()
    @Volatile private var loaded = false

    fun load(ctx: Context): StateFlow<GhajarLook> {
        if (!loaded) synchronized(this) {
            if (!loaded) {
                _look.value = runCatching {
                    ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(KEY, null)?.let { GhajarLook.fromJson(JSONObject(it)) }
                }.getOrNull() ?: GhajarLook.Default
                loaded = true
            }
        }
        return look
    }

    fun set(ctx: Context, value: GhajarLook) {
        _look.value = value
        runCatching {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, value.toJson().toString()).apply()
        }
    }

    fun update(ctx: Context, f: (GhajarLook) -> GhajarLook) = set(ctx, f(_look.value))

    fun export(appVersion: String): String = _look.value.toExport(appVersion).toString(2)

    /** Returns false (and changes nothing) when [text] is not a look export. */
    fun import(ctx: Context, text: String): Boolean {
        val o = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return false
        if (!looksLikeExport(o)) return false
        set(ctx, GhajarLook.fromAny(o))
        return true
    }
}

/** A look export: the versioned shape or the flat v1 one. */
fun looksLikeExport(o: JSONObject): Boolean =
    o.optString("kind") == "ghajar-appearance" || o.has("schemaVersion") || o.has("preset") || o.has("colors")

/**
 * WCAG contrast ratio of two opaque colours (1..21). Personalization warns
 * under 3:1 for large text and icons and 4.5:1 for body text.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

fun colorToLong(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

private val LookDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val LookAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The page transition the user chose, or null for the screen's own default.
 * [forward] is whether the new page is deeper; [rtl] mirrors horizontal travel.
 */
fun GhajarLook.pageTransition(
    forward: Boolean,
    rtl: Boolean,
    reduceMotion: Boolean
): ContentTransform? {
    if (reduceMotion) return fadeIn(tween(90)) togetherWith fadeOut(tween(60))
    if (transition == "default") return null
    val k = when (speed) { "slow" -> 1.6f; "fast" -> 0.6f; else -> 1f }
    fun d(ms: Int) = (ms * k).toInt()
    val sign = if (forward != rtl) 1 else -1
    val vsign = if (forward) 1 else -1
    return when (transition) {
        "none" -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
        "fade" -> fadeIn(tween(d(260))) togetherWith fadeOut(tween(d(180)))
        "slide_h" -> slideInHorizontally(tween(d(340), easing = Standard)) { sign * it } togetherWith
            slideOutHorizontally(tween(d(340), easing = Standard)) { -sign * it }
        "slide_v" -> slideInVertically(tween(d(340), easing = Standard)) { vsign * it } togetherWith
            slideOutVertically(tween(d(340), easing = Standard)) { -vsign * it / 3 } + fadeOut(tween(d(200)))
        "scale" -> (scaleIn(tween(d(320), easing = LookDecel), initialScale = 0.9f) + fadeIn(tween(d(240)))) togetherWith
            (scaleOut(tween(d(200)), targetScale = 1.05f) + fadeOut(tween(d(160))))
        "axis_x" -> (slideInHorizontally(tween(d(300), easing = LookDecel)) { sign * it / 8 } +
            fadeIn(tween(d(210), delayMillis = d(90)))) togetherWith
            (slideOutHorizontally(tween(d(300), easing = LookAccel)) { -sign * it / 8 } + fadeOut(tween(d(90))))
        "axis_y" -> (slideInVertically(tween(d(300), easing = LookDecel)) { vsign * it / 8 } +
            fadeIn(tween(d(210), delayMillis = d(90)))) togetherWith
            (slideOutVertically(tween(d(300), easing = LookAccel)) { -vsign * it / 8 } + fadeOut(tween(d(90))))
        "axis_z" -> (scaleIn(tween(d(300), easing = LookDecel), initialScale = if (forward) 0.8f else 1.1f) +
            fadeIn(tween(d(210), delayMillis = d(90)))) togetherWith
            (scaleOut(tween(d(300), easing = LookAccel), targetScale = if (forward) 1.1f else 0.8f) + fadeOut(tween(d(90))))
        "fade_through" -> (fadeIn(tween(d(210), delayMillis = d(90))) +
            scaleIn(tween(d(210), delayMillis = d(90)), initialScale = 0.92f)) togetherWith fadeOut(tween(d(90)))
        else -> null
    }
}

/** Convenience for an AnimatedContent transitionSpec with its own fallback. */
fun <S> AnimatedContentTransitionScope<S>.lookOr(
    look: GhajarLook,
    forward: Boolean,
    rtl: Boolean,
    reduceMotion: Boolean,
    fallback: () -> ContentTransform
): ContentTransform = look.pageTransition(forward, rtl, reduceMotion) ?: fallback()

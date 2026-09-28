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
    val boldTitles: Boolean = true
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
    }

    companion object {
        val Default = GhajarLook()

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
                fontScale = o.optDouble("fontScale", d.fontScale.toDouble()).toFloat().coerceIn(0.85f, 1.25f),
                boldTitles = o.optBoolean("boldTitles", d.boldTitles)
            )
        }
    }
}

val LookTransitions = listOf(
    "default", "none", "fade", "slide_h", "slide_v", "scale", "axis_x", "axis_y", "axis_z", "fade_through"
)
val LookNavStyles = listOf("floating", "standard", "minimal", "filled", "outline")
val LookOrbStyles = listOf(
    "circle", "pill", "capsule_glow", "soft_square", "double_ring", "neon", "minimal", "segmented"
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
    DIALOG("dialog", "appearance");

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
    LAVENDER("lavender", "یاسی", "Lavender", 0xFFA5B4FC, 0xFF06060E, 0xFF0D0D1A, 0xFF131324, 0xFF1A1A30, 0xFF24243E);

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
    if (amoled || p == LookPreset.AMOLED) out = out.amoled()
    if ((dynamic || p == LookPreset.DYNAMIC) && dynamicColor != null) out = out.withAccent(dynamicColor)
    accent?.let { out = out.withAccent(Color(it.toInt())) }
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

    fun export(): String = _look.value.toJson().toString(2)

    /** Returns false (and changes nothing) when [text] is not a look export. */
    fun import(ctx: Context, text: String): Boolean {
        val o = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return false
        if (!o.has("preset") && !o.has("colors")) return false
        set(ctx, GhajarLook.fromJson(o))
        return true
    }
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

package net.gozar.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The single source of colour for the whole app.
 *
 * Components read semantic tokens - "the card colour", "muted text" - never a
 * literal hex. That is what makes a theme switch a one-line change instead of
 * a sweep through fifty files, and what keeps a new theme from missing half
 * the screens.
 *
 * Every palette is also projected onto a Material 3 [ColorScheme] (see
 * [toColorScheme]), so screens still written against MaterialTheme.colorScheme
 * follow the active theme automatically rather than being stranded on the old
 * identity.
 */
@Immutable
data class GhajarPalette(
    val id: GhajarThemeId,
    val dark: Boolean,
    /** App canvas, behind everything. */
    val background: Color,
    /** Raised sheet: bars, sheets, dialogs. */
    val surface: Color,
    /** Standard content card. */
    val card: Color,
    /** Nested/secondary card, inputs, chips. */
    val secondaryCard: Color,
    /** Brand colour for primary actions. */
    val primary: Color,
    /** Richer brand tone for emphasis and gradients. */
    val premium: Color,
    /** Brightest brand tone: active states, accents, selected rows. */
    val highlight: Color,
    /** Glow/halo tone for the connected state only. */
    val successGlow: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val error: Color,
    val onError: Color,
    val errorSurface: Color,
    val warning: Color,
    val disabled: Color,
    val onDisabled: Color,
    val scrim: Color
)

/**
 * Selectable themes. Values are persisted by name, so renaming one is a
 * migration - add instead.
 */
enum class GhajarThemeId {
    PREMIUM_GREEN_DARK,
    PREMIUM_GREEN_LIGHT,
    MIDNIGHT_BLUE,
    GRAPHITE_GOLD,
    SYSTEM;

    companion object {
        /** First install, and the fallback for any unreadable stored value. */
        val Default = PREMIUM_GREEN_DARK

        fun parse(value: String?): GhajarThemeId =
            entries.firstOrNull { it.name == value } ?: Default
    }
}

/**
 * The mandated brand values, defined once. Themes reference these; components
 * never do.
 */
object GhajarBrand {
    val Background = Color(0xFF050807)
    val Surface = Color(0xFF0B1512)
    val Card = Color(0xFF101816)
    val SecondaryCard = Color(0xFF14231F)

    val PrimaryGreen = Color(0xFF00A86B)
    val PremiumGreen = Color(0xFF00B978)
    val HighlightGreen = Color(0xFF24D98B)
    val SuccessGlow = Color(0xFF0CE6A0)
}

private val PremiumGreenDark = GhajarPalette(
    id = GhajarThemeId.PREMIUM_GREEN_DARK,
    dark = true,
    background = GhajarBrand.Background,
    surface = GhajarBrand.Surface,
    card = GhajarBrand.Card,
    secondaryCard = GhajarBrand.SecondaryCard,
    primary = GhajarBrand.PrimaryGreen,
    premium = GhajarBrand.PremiumGreen,
    highlight = GhajarBrand.HighlightGreen,
    successGlow = GhajarBrand.SuccessGlow,
    onPrimary = Color(0xFF02120B),
    textPrimary = Color(0xFFE9F4EF),
    textSecondary = Color(0xFF9DB3AA),
    textMuted = Color(0xFF6B807A),
    border = Color(0xFF1B2A26),
    borderStrong = Color(0xFF263A34),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0A0A),
    errorSurface = Color(0xFF2B1416),
    warning = Color(0xFFF2B23E),
    disabled = Color(0xFF1E2C28),
    onDisabled = Color(0xFF5B6E68),
    scrim = Color(0xE6000000)
)

private val PremiumGreenLight = GhajarPalette(
    id = GhajarThemeId.PREMIUM_GREEN_LIGHT,
    dark = false,
    background = Color(0xFFF3F8F6),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    secondaryCard = Color(0xFFEAF3EF),
    // Darkened so white sits on it at AA; the brand green stays the accent.
    primary = Color(0xFF00794E),
    premium = Color(0xFF00925E),
    highlight = GhajarBrand.PrimaryGreen,
    successGlow = Color(0xFF00B978),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF07130F),
    textSecondary = Color(0xFF44564F),
    textMuted = Color(0xFF6C7E77),
    border = Color(0xFFDBE7E2),
    borderStrong = Color(0xFFC3D5CE),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorSurface = Color(0xFFFCE9E7),
    warning = Color(0xFF8A5A00),
    disabled = Color(0xFFE2EAE7),
    onDisabled = Color(0xFF93A29C),
    scrim = Color(0x99000000)
)

private val MidnightBlue = GhajarPalette(
    id = GhajarThemeId.MIDNIGHT_BLUE,
    dark = true,
    background = Color(0xFF04070F),
    surface = Color(0xFF0A111F),
    card = Color(0xFF0F1929),
    secondaryCard = Color(0xFF152238),
    primary = Color(0xFF3D7BFF),
    premium = Color(0xFF5A8CFF),
    highlight = Color(0xFF82AAFF),
    successGlow = Color(0xFF4DD6FF),
    onPrimary = Color(0xFF03081A),
    textPrimary = Color(0xFFE7ECF7),
    textSecondary = Color(0xFF9BABC7),
    textMuted = Color(0xFF6B7B94),
    border = Color(0xFF1A2540),
    borderStrong = Color(0xFF243352),
    error = Color(0xFFFF7A85),
    onError = Color(0xFF2A0A0E),
    errorSurface = Color(0xFF2A1420),
    warning = Color(0xFFE8B44A),
    disabled = Color(0xFF1A2335),
    onDisabled = Color(0xFF5D6C86),
    scrim = Color(0xE6000000)
)

private val GraphiteGold = GhajarPalette(
    id = GhajarThemeId.GRAPHITE_GOLD,
    dark = true,
    background = Color(0xFF07070A),
    surface = Color(0xFF111114),
    card = Color(0xFF17171B),
    secondaryCard = Color(0xFF1F1E24),
    primary = Color(0xFFD8B15C),
    premium = Color(0xFFE4C173),
    highlight = Color(0xFFF0D394),
    successGlow = Color(0xFF7FD6A8),
    onPrimary = Color(0xFF130F06),
    textPrimary = Color(0xFFF1EFEA),
    textSecondary = Color(0xFFAEA89C),
    textMuted = Color(0xFF7B766C),
    border = Color(0xFF26242B),
    borderStrong = Color(0xFF34313A),
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF2A0A0A),
    errorSurface = Color(0xFF2A1614),
    warning = Color(0xFFE0A94B),
    disabled = Color(0xFF1D1C21),
    onDisabled = Color(0xFF6E6961),
    scrim = Color(0xE6000000)
)

/** Every selectable palette, in the order the picker shows them. */
val GhajarPalettes: List<GhajarPalette> =
    listOf(PremiumGreenDark, PremiumGreenLight, MidnightBlue, GraphiteGold)

/**
 * SYSTEM follows the OS between the two Premium Green palettes, so following
 * the system never drops the brand identity.
 */
fun ghajarPaletteFor(theme: GhajarThemeId, systemDark: Boolean): GhajarPalette = when (theme) {
    GhajarThemeId.PREMIUM_GREEN_DARK -> PremiumGreenDark
    GhajarThemeId.PREMIUM_GREEN_LIGHT -> PremiumGreenLight
    GhajarThemeId.MIDNIGHT_BLUE -> MidnightBlue
    GhajarThemeId.GRAPHITE_GOLD -> GraphiteGold
    GhajarThemeId.SYSTEM -> if (systemDark) PremiumGreenDark else PremiumGreenLight
}

object GhajarSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object GhajarRadius {
    val sm = 10.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
    val pill = 999.dp
}

/** Short on purpose: motion should confirm an action, never gate it. */
object GhajarMotion {
    const val Fast = 140
    const val Base = 220
    const val Slow = 320
}

val LocalGhajarPalette = staticCompositionLocalOf { PremiumGreenDark }

/** Tokens for the current theme. Preferred over MaterialTheme.colorScheme. */
val ghajarColors: GhajarPalette
    @Composable get() = LocalGhajarPalette.current

/**
 * The "connected / healthy" accent. It lived in DotGlobe.kt and was hardcoded
 * to two greens; it now follows the active theme, so it reads correctly in
 * Midnight Blue and Graphite Gold instead of staying green there. Call sites
 * are unchanged.
 */
internal val AppGreen: Color
    @Composable get() = LocalGhajarPalette.current.successGlow

/**
 * Projects the tokens onto Material 3 so existing MaterialTheme-based screens
 * inherit the active theme instead of keeping the old palette.
 */
fun GhajarPalette.toColorScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = secondaryCard,
        onPrimaryContainer = textPrimary,
        inversePrimary = highlight,
        secondary = premium,
        onSecondary = onPrimary,
        secondaryContainer = secondaryCard,
        onSecondaryContainer = textPrimary,
        tertiary = highlight,
        onTertiary = onPrimary,
        tertiaryContainer = secondaryCard,
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = secondaryCard,
        onSurfaceVariant = textSecondary,
        surfaceTint = primary,
        surfaceBright = secondaryCard,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = card,
        surfaceContainerHigh = secondaryCard,
        surfaceContainerHighest = secondaryCard,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        error = error,
        onError = onError,
        errorContainer = errorSurface,
        onErrorContainer = error,
        outline = borderStrong,
        outlineVariant = border,
        scrim = scrim
    )
}

/**
 * Installs the design system. [theme] comes from the user's stored choice, so
 * a change applies everywhere at once.
 */
@Composable
fun GhajarTheme(
    theme: GhajarThemeId,
    typography: androidx.compose.material3.Typography,
    shapes: androidx.compose.material3.Shapes,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val palette = remember(theme, systemDark) { ghajarPaletteFor(theme, systemDark) }
    CompositionLocalProvider(LocalGhajarPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

/**
 * Entry point for screens that live in their own Activity (the log/debug
 * viewer, the config toolkit). They each used to hardcode a one-off palette,
 * which is how a "full redesign" ends up with two screens still wearing the
 * old colours. This reads the same stored theme and language as the main
 * window so they follow the user's choice too.
 */
@Composable
fun GhajarAppTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ConfigStore.get(context) }
    val theme by store.uiTheme.collectAsState()
    val lang by store.lang.collectAsState()
    GhajarTheme(
        theme = theme,
        typography = if (lang == Lang.FA) VazirTypography else LexendTypography,
        shapes = GhajarSoftShapes
    ) {
        CompositionLocalProvider(
            LocalLang provides lang,
            androidx.compose.ui.platform.LocalLayoutDirection provides
                if (lang == Lang.FA) androidx.compose.ui.unit.LayoutDirection.Rtl
                else androidx.compose.ui.unit.LayoutDirection.Ltr,
            content = content
        )
    }
}

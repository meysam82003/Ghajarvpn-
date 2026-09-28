package net.gozar.app

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/*
 * Personalization: presets, accent, AMOLED, dynamic colour, a colour for each
 * element, motion, the navigation bar, the connect button and the cards.
 *
 * Everything on this page edits a draft. The whole page, and the live preview
 * pinned at its top, is drawn with the draft, so every change is visible before
 * it is saved; "Save" writes it, "Cancel" drops it.
 */

@Composable
private fun tr(fa: String, en: String): String = if (LocalLang.current == Lang.FA) fa else en

private data class LookCategory(val key: String, val fa: String, val en: String, val icon: ImageVector)

private val Categories = listOf(
    LookCategory("theme", "تم", "Theme", Icons.Filled.Palette),
    LookCategory("appearance", "ظاهر", "Appearance", Icons.Filled.Brightness2),
    LookCategory("nav", "ناوبری", "Navigation", Icons.Filled.SpaceDashboard),
    LookCategory("orb", "دکمه اتصال", "Connect button", Icons.Filled.PowerSettingsNew),
    LookCategory("cards", "کارت‌ها", "Cards", Icons.Filled.ViewAgenda),
    LookCategory("tiles", "کاشی ترافیک", "Traffic tiles", Icons.Filled.Speed),
    LookCategory("typography", "متن", "Typography", Icons.Filled.FormatSize),
    LookCategory("animation", "انیمیشن", "Animation", Icons.Filled.Animation),
    LookCategory("advanced", "پیشرفته", "Advanced", Icons.Filled.Tune),
    LookCategory("reset", "بازنشانی", "Reset", Icons.Filled.RestartAlt)
)

private fun elementLabel(e: LookElement, fa: Boolean): String = when (e) {
    LookElement.TILE_DOWN_ICON -> if (fa) "آیکن و عدد دانلود" else "Download icon & number"
    LookElement.TILE_DOWN_BG -> if (fa) "پس‌زمینه کاشی دانلود" else "Download tile background"
    LookElement.TILE_UP_ICON -> if (fa) "آیکن و عدد آپلود" else "Upload icon & number"
    LookElement.TILE_UP_BG -> if (fa) "پس‌زمینه کاشی آپلود" else "Upload tile background"
    LookElement.CONNECT -> if (fa) "دکمه اتصال" else "Connect button"
    LookElement.DISCONNECT -> if (fa) "دکمه قطع اتصال" else "Disconnect button"
    LookElement.CONFIG_CARD -> if (fa) "کارت کانفیگ" else "Config card"
    LookElement.CONFIG_TEXT -> if (fa) "متن کارت کانفیگ" else "Config card text"
    LookElement.SERVER_CARD -> if (fa) "کارت سرورها" else "Server cards"
    LookElement.SERVER_ACTIVE -> if (fa) "کارت سرور فعال" else "Active server card"
    LookElement.NAV_BG -> if (fa) "پس‌زمینه نوار ناوبری" else "Navigation background"
    LookElement.NAV_INDICATOR -> if (fa) "نشانگر انتخاب" else "Selection indicator"
    LookElement.NAV_ICON_ACTIVE -> if (fa) "آیکن فعال" else "Active icon"
    LookElement.NAV_ICON_INACTIVE -> if (fa) "آیکن غیرفعال" else "Inactive icon"
    LookElement.NAV_LABEL -> if (fa) "برچسب‌ها" else "Labels"
    LookElement.SWITCH -> if (fa) "سوییچ‌ها" else "Switches"
    LookElement.SECONDARY_BUTTON -> if (fa) "دکمه‌های ثانویه" else "Secondary buttons"
    LookElement.TITLE -> if (fa) "عنوان‌ها" else "Titles"
    LookElement.SUBTITLE -> if (fa) "متن توضیحی" else "Subtitle text"
    LookElement.BORDER -> if (fa) "حاشیه‌ها" else "Borders"
    LookElement.DIALOG -> if (fa) "دیالوگ‌ها" else "Dialogs"
}

private fun GhajarLook.withColor(key: String, c: Color?): GhajarLook =
    copy(colors = if (c == null) colors - key else colors + (key to colorToLong(c)))

private fun GhajarLook.resetCategory(key: String): GhajarLook {
    val d = GhajarLook.Default
    val clean = colors.filterKeys { k -> LookElement.byKey(k)?.group != key }
    return when (key) {
        "theme" -> copy(preset = d.preset, accent = null)
        "appearance" -> copy(accent = null, amoled = d.amoled, dynamic = d.dynamic, colors = clean)
        "nav" -> copy(navStyle = d.navStyle, navIconSize = d.navIconSize, navLabels = d.navLabels,
            navRadius = d.navRadius, colors = clean)
        "orb" -> copy(orbStyle = d.orbStyle, colors = clean)
        "cards" -> copy(columns = d.columns, density = d.density, cardRadius = d.cardRadius,
            elevation = d.elevation, colors = clean)
        "tiles" -> copy(colors = clean)
        "typography" -> copy(fontScale = d.fontScale, boldTitles = d.boldTitles, colors = clean)
        "animation" -> copy(transition = d.transition, speed = d.speed)
        else -> this
    }
}

/**
 * Draws [content] as if [look] were saved: its palette, its element colours,
 * and its font size relative to the one already applied.
 */
@Composable
fun LookScope(look: GhajarLook, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ConfigStore.get(ctx) }
    val theme by store.uiTheme.collectAsState()
    val sysDark = isSystemInDarkTheme()
    val saved = LocalGhajarLook.current
    val palette = remember(theme, sysDark, look) {
        look.apply(ghajarPaletteFor(theme, sysDark), dynamicAccent(ctx))
    }
    val density = LocalDensity.current
    val scaled = remember(density, look.fontScale, saved.fontScale) {
        Density(density.density, density.fontScale * look.fontScale / saved.fontScale.coerceAtLeast(0.5f))
    }
    CompositionLocalProvider(
        LocalGhajarPalette provides palette,
        LocalGhajarLook provides look,
        LocalDensity provides scaled
    ) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}

@Composable
fun PersonalizeScreen(store: ConfigStore, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val saved by remember { GhajarLookStore.load(ctx) }.collectAsState()
    val storeDyn by store.dynamicAccent.collectAsState()
    var draft by remember { mutableStateOf(saved.copy(dynamic = saved.dynamic || storeDyn)) }
    LaunchedEffect(saved) { draft = saved.copy(dynamic = saved.dynamic || storeDyn) }
    val dirty = draft != saved.copy(dynamic = saved.dynamic || storeDyn)
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var previewOpen by rememberSaveable { mutableStateOf(true) }
    var previewState by remember { mutableStateOf(Connection.DISCONNECTED) }

    fun save() {
        GhajarLookStore.set(ctx, draft)
        store.setDynamicAccent(draft.dynamic && dynamicAccentSupported)
    }

    LookScope(draft) {
        val c = ghajarColors
        Column(modifier.fillMaxSize().background(c.background)) {
            // Live preview, pinned.
            Column(
                Modifier.fillMaxWidth().padding(horizontal = GhajarSpacing.lg, vertical = GhajarSpacing.sm)
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(GhajarRadius.md))
                        .clickable { previewOpen = !previewOpen }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("پیش‌نمایش زنده", "Live preview"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (previewOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = c.textMuted
                    )
                }
                AnimatedVisibility(previewOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    LivePreview(previewState, onCycle = {
                        previewState = when (previewState) {
                            Connection.DISCONNECTED -> Connection.CONNECTING
                            Connection.CONNECTING -> Connection.CONNECTED
                            Connection.CONNECTED -> Connection.DISCONNECTING
                            else -> Connection.DISCONNECTED
                        }
                    })
                }
            }
            TabRail(
                tabs = Categories.map { RailTab(tr(it.fa, it.en), it.icon) },
                selected = cat,
                onSelect = { cat = it },
                modifier = Modifier.padding(horizontal = GhajarSpacing.lg)
            )
            Spacer(Modifier.height(GhajarSpacing.sm))
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = cat,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "lookCat"
                ) { index ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = GhajarSpacing.lg)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
                    ) {
                        val set: (GhajarLook) -> Unit = { draft = it }
                        when (Categories[index].key) {
                            "theme" -> ThemeCategory(store, draft, set, onEdit = { cat = 1 })
                            "appearance" -> AppearanceCategory(draft, set)
                            "nav" -> NavCategory(draft, set)
                            "orb" -> OrbCategory(draft, set, previewState)
                            "cards" -> CardsCategory(draft, set)
                            "tiles" -> TilesCategory(draft, set)
                            "typography" -> TypographyCategory(draft, set)
                            "animation" -> AnimationCategory(store, draft, set)
                            "advanced" -> AdvancedCategory(draft, set)
                            "reset" -> ResetCategory(draft, set)
                        }
                    }
                }
            }
                // Save bar: only while the draft differs from what is saved.
                AnimatedVisibility(
                    visible = dirty,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(c.surface)
                            .padding(horizontal = GhajarSpacing.lg, vertical = GhajarSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
                    ) {
                        GhostPill(
                            tr("لغو", "Cancel"),
                            onClick = { draft = saved.copy(dynamic = saved.dynamic || storeDyn) },
                            modifier = Modifier.weight(1f)
                        )
                        PillButton(
                            tr("ذخیره تغییرات", "Save changes"),
                            onClick = { save() },
                            icon = Icons.Filled.Check,
                            modifier = Modifier.weight(1f),
                            minHeight = 48.dp
                        )
                    }
                }
        }
    }
}

@Composable
private fun LivePreview(state: Connection, onCycle: () -> Unit) {
    val c = ghajarColors
    var nav by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(GhajarRadius.lg)).background(c.background)
            .border(1.dp, c.border, RoundedCornerShape(GhajarRadius.lg)).padding(GhajarSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
            ConnectOrb(
                state = state, picking = false, enabled = true, tunnelDead = false, netOffline = false,
                onClick = onCycle, diameter = 92.dp
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    tr("روی دکمه بزن تا حالت‌ها را ببینی", "Tap the button to cycle states"),
                    style = MaterialTheme.typography.labelSmall, color = c.textSecondary
                )
                Slab(spacing = 0.dp, padding = 6.dp, color = lookColor(LookElement.CONFIG_CARD)) {
                    SlabRow(
                        title = "Ghajar • DE-1",
                        subtitle = "VLESS · Reality",
                        titleColor = lookColor(LookElement.CONFIG_TEXT),
                        icon = Icons.Filled.Shield,
                        accent = if (state == Connection.CONNECTED) c.successGlow else c.primary
                    )
                }
            }
        }
        TrafficTiles(
            downValue = "2.4 MB/s", downTotal = "1.2 GB",
            upValue = "310 KB/s", upTotal = "180 MB",
            modifier = Modifier.fillMaxWidth()
        )
        SkinNavBar(
            items = listOf(
                SkinNavItem(R.drawable.ic_royal_home, tr("خانه", "Home")) { nav = 0 },
                SkinNavItem(R.drawable.ic_royal_shop, tr("فروشگاه", "Shop")) { nav = 1 },
                SkinNavItem(R.drawable.ic_royal_settings, tr("تنظیمات", "Settings")) { nav = 2 }
            ),
            selected = nav,
            systemInsets = false
        )
    }
}

// ---------------------------------------------------------------- categories

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = ghajarColors.textPrimary,
        modifier = Modifier.padding(top = GhajarSpacing.xs)
    )
}

@Composable
private fun ChoiceChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = ghajarColors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
        items(options, key = { it.first }) { (key, label) ->
            val on = key == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) c.onPrimary else c.textPrimary,
                modifier = Modifier.clip(RoundedCornerShape(GhajarRadius.pill))
                    .background(if (on) c.primary else c.secondaryCard)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onChange: (Float) -> Unit
) {
    val c = ghajarColors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(display(value), style = MaterialTheme.typography.labelLarge, color = c.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = c.primary,
                activeTrackColor = c.primary,
                inactiveTrackColor = c.border
            )
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, icon: ImageVector, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    SlabRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        trailing = { SkinSwitch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled) }
    )
}

@Composable
private fun ColorRows(draft: GhajarLook, set: (GhajarLook) -> Unit, group: String) {
    val elements = LookElement.entries.filter { it.group == group }
    if (elements.isEmpty()) return
    SectionTitle(tr("رنگ بخش‌ها", "Element colours"))
    Slab(spacing = 0.dp) {
        elements.forEachIndexed { i, e ->
            if (i > 0) SlabDivider()
            ColorRow(draft, set, e)
        }
    }
}

@Composable
private fun ColorRow(draft: GhajarLook, set: (GhajarLook) -> Unit, e: LookElement) {
    val c = ghajarColors
    val fa = LocalLang.current == Lang.FA
    val current = draft.color(e.key) ?: e.default(c)
    val overridden = draft.colors.containsKey(e.key)
    var open by remember { mutableStateOf(false) }
    SlabRow(
        title = elementLabel(e, fa),
        subtitle = if (overridden) hexOf(current) else tr("پیش‌فرض تم", "Theme default"),
        onClick = { open = true },
        trailing = {
            if (overridden) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = tr("بازنشانی", "Reset"),
                    tint = c.textMuted,
                    modifier = Modifier.size(34.dp).clip(CircleShape).clickable { set(draft.withColor(e.key, null)) }
                        .padding(7.dp)
                )
            }
            Swatch(current)
        }
    )
    if (open) {
        ColorPickerDialog(
            title = elementLabel(e, fa),
            initial = current,
            themeDefault = e.default(c),
            preview = { candidate -> LookScope(draft.withColor(e.key, candidate)) { ElementPreview(e) } },
            onSave = { set(draft.withColor(e.key, it)); open = false },
            onThemeDefault = { set(draft.withColor(e.key, null)); open = false },
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun Swatch(color: Color, size: androidx.compose.ui.unit.Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).checker().background(color)
            .border(1.dp, ghajarColors.borderStrong, CircleShape)
    )
}

/** A chequerboard under a swatch, so a transparent colour reads as transparent. */
private fun Modifier.checker(): Modifier = this.then(
    Modifier.background(Color(0xFF888888)).padding(0.dp)
)

private fun hexOf(c: Color): String = "#%08X".format(c.toArgb())

@Composable
private fun ElementPreview(e: LookElement) {
    val c = ghajarColors
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(GhajarRadius.md)).background(c.background).padding(GhajarSpacing.sm),
        contentAlignment = Alignment.Center) {
        when (e.group) {
            "tiles" -> TrafficTiles("2.4 MB/s", "1.2 GB", "310 KB/s", "180 MB", Modifier.fillMaxWidth())
            "orb" -> ConnectOrb(
                state = if (e == LookElement.DISCONNECT) Connection.CONNECTED else Connection.DISCONNECTED,
                picking = false, enabled = true, tunnelDead = false, netOffline = false,
                onClick = {}, diameter = 110.dp
            )
            "nav" -> SkinNavBar(
                items = listOf(
                    SkinNavItem(R.drawable.ic_royal_home, tr("خانه", "Home")) {},
                    SkinNavItem(R.drawable.ic_royal_shop, tr("فروشگاه", "Shop")) {},
                    SkinNavItem(R.drawable.ic_royal_settings, tr("تنظیمات", "Settings")) {}
                ),
                selected = 0,
                systemInsets = false
            )
            "cards" -> if (e == LookElement.CONFIG_CARD || e == LookElement.CONFIG_TEXT) {
                Slab(spacing = 0.dp, padding = GhajarSpacing.sm, color = lookColor(LookElement.CONFIG_CARD)) {
                    SlabRow(title = "Ghajar • DE-1", subtitle = "VLESS · Reality", icon = Icons.Filled.Shield,
                        titleColor = lookColor(LookElement.CONFIG_TEXT), chevron = true)
                }
            } else ServerRowsMock()
            "typography" -> Column(Modifier.fillMaxWidth()) {
                Text(tr("عنوان نمونه", "Sample title"), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text(tr("این یک متن توضیحی نمونه است.", "This is sample subtitle text."),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                when (e) {
                    LookElement.SWITCH -> Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
                        SkinSwitch(checked = true, onCheckedChange = {}); SkinSwitch(checked = false, onCheckedChange = {})
                    }
                    LookElement.SECONDARY_BUTTON -> GhostPill(tr("دکمه ثانویه", "Secondary"), onClick = {})
                    LookElement.DIALOG -> Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(GhajarRadius.lg)).background(c.surface).padding(GhajarSpacing.md)
                    ) {
                        Text(tr("عنوان دیالوگ", "Dialog title"), fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Text(tr("متن دیالوگ", "Dialog body"), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Box(
                        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(GhajarRadius.md))
                            .border(1.5.dp, c.border, RoundedCornerShape(GhajarRadius.md))
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRowsMock() {
    val c = ghajarColors
    val active = lookColor(LookElement.SERVER_ACTIVE)
    val card = lookColor(LookElement.SERVER_CARD)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(true to "Ghajar • DE-1", false to "Ghajar • NL-2").forEach { (on, name) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(GhajarRadius.lg))
                    .background(if (on) active.copy(alpha = 0.16f) else card)
                    .padding(horizontal = GhajarSpacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).height(22.dp).clip(CircleShape).background(if (on) active else Color.Transparent))
                Spacer(Modifier.width(8.dp))
                Text(name, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                Text("86 ms", color = c.good, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ThemeCategory(store: ConfigStore, draft: GhajarLook, set: (GhajarLook) -> Unit, onEdit: () -> Unit) {
    val c = ghajarColors
    val fa = LocalLang.current == Lang.FA
    val base by store.uiTheme.collectAsState()
    SectionTitle(tr("تم‌های آماده", "Preset themes"))
    Text(
        tr("با یک لمس اعمال می‌شود؛ بعد از انتخاب، رنگ‌ها قابل ویرایش‌اند.", "One tap to apply; every colour stays editable."),
        style = MaterialTheme.typography.bodySmall, color = c.textSecondary
    )
    LookPreset.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
            row.forEach { p ->
                val look = draft.copy(preset = p.key, accent = null)
                val on = draft.preset == p.key
                val unsupported = p == LookPreset.DYNAMIC && !dynamicAccentSupported
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(GhajarRadius.lg))
                        .background(if (on) c.primary.copy(alpha = 0.14f) else c.secondaryCard)
                        .border(if (on) 1.5.dp else 0.dp, if (on) c.primary else Color.Transparent, RoundedCornerShape(GhajarRadius.lg))
                        .clickable(enabled = !unsupported) { set(look) }
                        .padding(GhajarSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LookScope(look) { PresetThumb() }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (fa) p.fa else p.en,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (unsupported) c.textMuted else c.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (on) Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                    if (unsupported) {
                        Text(tr("نیازمند اندروید ۱۲+", "Needs Android 12+"),
                            style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                    }
                    if (on) {
                        Text(
                            tr("ویرایش رنگ‌ها ←", "Edit colours →"),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.primary,
                            modifier = Modifier.clip(RoundedCornerShape(GhajarRadius.sm)).clickable { onEdit() }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    SectionTitle(tr("پایهٔ تم (پیش‌فرض)", "Base theme (for Default)"))
    Text(
        tr("پیش‌فرض از این پایه ساخته می‌شود؛ روشن، تیره یا طبق سیستم.", "Default is built on this: dark, light or follow the system."),
        style = MaterialTheme.typography.bodySmall, color = c.textSecondary
    )
    ChoiceChips(
        listOf(
            GhajarThemeId.PREMIUM_GREEN_DARK.name to tr("سبز تیره", "Green dark"),
            GhajarThemeId.PREMIUM_GREEN_LIGHT.name to tr("سبز روشن", "Green light"),
            GhajarThemeId.MIDNIGHT_BLUE.name to tr("آبی نیمه‌شب", "Midnight blue"),
            GhajarThemeId.GRAPHITE_GOLD.name to tr("گرافیت طلایی", "Graphite gold"),
            GhajarThemeId.SYSTEM.name to tr("طبق سیستم", "System")
        ),
        base.name
    ) { store.setUiTheme(GhajarThemeId.parse(it)) }
}

@Composable
private fun PresetThumb() {
    val c = ghajarColors
    Column(
        Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(GhajarRadius.md)).background(c.background).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(c.secondaryCard).border(3.dp, c.primary, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(3.dp)).background(c.card))
                Box(Modifier.fillMaxWidth(0.6f).height(7.dp).clip(RoundedCornerShape(3.dp)).background(c.highlight))
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(c.card).padding(3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(22.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.primary))
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.textMuted))
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.textMuted))
        }
    }
}

private val QuickAccents = listOf(
    0xFF00A86B, 0xFF14B8A6, 0xFF22D3EE, 0xFF3D7BFF, 0xFF6366F1, 0xFFA855F7,
    0xFFEC4899, 0xFFF43F5E, 0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308,
    0xFF84CC16, 0xFF4CAF50, 0xFFD8B15C, 0xFFE5E7EB
)

@Composable
private fun AppearanceCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    val c = ghajarColors
    var pick by remember { mutableStateOf(false) }
    SectionTitle(tr("رنگ اصلی (Accent)", "Accent colour"))
    Text(
        tr("روی دکمه‌ها، آیتم فعال، سوییچ‌ها، ناوبری انتخاب‌شده، هایلایت‌ها و عنوان‌های مهم اثر می‌گذارد.",
            "Buttons, active items, switches, the selected tab, highlights and key titles."),
        style = MaterialTheme.typography.bodySmall, color = c.textSecondary
    )
    Slab {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Swatch(c.primary, 40.dp)
            Spacer(Modifier.width(GhajarSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(hexOf(c.primary), color = c.textPrimary, fontWeight = FontWeight.Bold)
                Text(if (draft.accent == null) tr("رنگ تم", "Theme's own") else tr("سفارشی", "Custom"),
                    style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            GhostPill(tr("انتخاب", "Pick"), onClick = { pick = true }, modifier = Modifier.width(96.dp), minHeight = 38.dp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val per = 8
            val size = (maxWidth - 6.dp * (per - 1)) / per
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickAccents.chunked(per).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { v ->
                            val col = Color(v.toInt())
                            val on = draft.accent == v
                            Box(
                                Modifier.size(size).clip(CircleShape).background(col)
                                    .border(if (on) 3.dp else 0.dp, c.textPrimary, CircleShape)
                                    .clickable { set(draft.copy(accent = v)) }
                            )
                        }
                    }
                }
            }
        }
        if (draft.accent != null) {
            Text(
                tr("برگشت به رنگ تم", "Back to the theme's accent"),
                color = c.primary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(GhajarRadius.sm)).clickable { set(draft.copy(accent = null)) }.padding(4.dp)
            )
        }
    }
    if (pick) {
        ColorPickerDialog(
            title = tr("رنگ اصلی", "Accent colour"),
            initial = c.primary,
            themeDefault = LookScopeDefaultAccent(draft),
            preview = { cand ->
                LookScope(draft.copy(accent = colorToLong(cand))) {
                    Column(verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                        PillButton(tr("اتصال", "Connect"), onClick = {}, icon = Icons.Filled.PowerSettingsNew, minHeight = 42.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
                            SkinSwitch(checked = true, onCheckedChange = {})
                            Text(tr("عنوان فعال", "Active title"), color = ghajarColors.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            onSave = { set(draft.copy(accent = colorToLong(it))); pick = false },
            onThemeDefault = { set(draft.copy(accent = null)); pick = false },
            onDismiss = { pick = false }
        )
    }

    SectionTitle(tr("حالت نمایش", "Display"))
    Slab(spacing = 0.dp) {
        SwitchRow(
            tr("AMOLED مشکی", "AMOLED black"),
            tr("پس‌زمینه کاملاً مشکی؛ روی نمایشگرهای OLED باتری کمتری مصرف می‌کند.", "Pure black canvas; saves battery on OLED screens."),
            Icons.Filled.Brightness2, draft.amoled
        ) { set(draft.copy(amoled = it)) }
        SlabDivider()
        SwitchRow(
            tr("رنگ پویا (Material You)", "Dynamic colour (Material You)"),
            if (dynamicAccentSupported) tr("رنگ اصلی از والپیپر گوشی گرفته می‌شود.", "The accent follows your wallpaper.")
            else tr("این گوشی پشتیبانی نمی‌کند: رنگ پویا از اندروید ۱۲ به بعد در دسترس است.",
                "Not available on this phone: dynamic colour needs Android 12 or later."),
            Icons.Filled.Wallpaper, draft.dynamic && dynamicAccentSupported, enabled = dynamicAccentSupported
        ) { set(draft.copy(dynamic = it)) }
    }
    ColorRows(draft, set, "appearance")
}

@Composable
private fun LookScopeDefaultAccent(draft: GhajarLook): Color {
    val ctx = LocalContext.current
    val store = remember { ConfigStore.get(ctx) }
    val theme by store.uiTheme.collectAsState()
    val dark = isSystemInDarkTheme()
    return remember(theme, dark, draft) { draft.copy(accent = null).apply(ghajarPaletteFor(theme, dark), dynamicAccent(ctx)).primary }
}

@Composable
private fun NavCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    val c = ghajarColors
    val names = mapOf(
        "floating" to tr("کپسول شناور", "Floating pill"),
        "standard" to tr("استاندارد گرد", "Standard rounded"),
        "minimal" to tr("مینیمال", "Minimal"),
        "filled" to tr("پُر", "Filled"),
        "outline" to tr("خطی", "Outline")
    )
    SectionTitle(tr("سبک نوار ناوبری", "Navigation style"))
    LookNavStyles.forEach { st ->
        val on = draft.navStyle == st
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(GhajarRadius.lg))
                .background(if (on) c.primary.copy(alpha = 0.12f) else c.secondaryCard)
                .border(if (on) 1.5.dp else 0.dp, if (on) c.primary else Color.Transparent, RoundedCornerShape(GhajarRadius.lg))
                .clickable { set(draft.copy(navStyle = st)) }
                .padding(GhajarSpacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(names[st] ?: st, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.weight(1f))
                if (on) Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(18.dp))
            }
            LookScope(draft.copy(navStyle = st)) {
                var sel by remember { mutableIntStateOf(0) }
                SkinNavBar(
                    items = listOf(
                        SkinNavItem(R.drawable.ic_royal_home, tr("خانه", "Home")) { sel = 0 },
                        SkinNavItem(R.drawable.ic_royal_shop, tr("فروشگاه", "Shop")) { sel = 1 },
                        SkinNavItem(R.drawable.ic_royal_settings, tr("تنظیمات", "Settings")) { sel = 2 }
                    ),
                    selected = sel,
                    systemInsets = false
                )
            }
        }
    }
    SectionTitle(tr("تنظیمات نوار", "Bar settings"))
    Slab {
        LabeledSlider(tr("اندازه آیکن", "Icon size"), draft.navIconSize.toFloat(), 18f..30f, 11, { "${it.toInt()}dp" }) {
            set(draft.copy(navIconSize = it.toInt()))
        }
        LabeledSlider(tr("گردی گوشه‌ها", "Corner radius"), draft.navRadius.toFloat(), 0f..40f, 9, { "${it.toInt()}dp" }) {
            set(draft.copy(navRadius = it.toInt()))
        }
    }
    Slab(spacing = 0.dp) {
        SwitchRow(tr("نمایش برچسب‌ها", "Show labels"), null, Icons.Filled.FormatSize, draft.navLabels) {
            set(draft.copy(navLabels = it))
        }
    }
    ColorRows(draft, set, "nav")
}

@Composable
private fun OrbCategory(draft: GhajarLook, set: (GhajarLook) -> Unit, previewState: Connection) {
    val c = ghajarColors
    var state by remember(previewState) { mutableStateOf(previewState) }
    val names = mapOf(
        "circle" to tr("دایره (فعلی)", "Circle (current)"),
        "pill" to tr("قرص گرد", "Rounded pill"),
        "capsule_glow" to tr("کپسول درخشان", "Capsule glow"),
        "soft_square" to tr("مربع نرم", "Soft square"),
        "double_ring" to tr("حلقه دوتایی", "Double ring"),
        "neon" to tr("حلقه نئونی", "Neon ring"),
        "minimal" to tr("مینیمال تخت", "Minimal flat"),
        "segmented" to tr("بخش‌بخش", "Segmented")
    )
    SectionTitle(tr("سبک دکمه اتصال", "Connect button style"))
    Text(
        tr("جای دکمه عوض نمی‌شود؛ فقط ظاهرش. حالت پیش‌نمایش را انتخاب کن:", "The button stays where it is; only its look changes. Preview state:"),
        style = MaterialTheme.typography.bodySmall, color = c.textSecondary
    )
    ChoiceChips(
        listOf(
            Connection.DISCONNECTED.name to tr("آماده", "Idle"),
            Connection.CONNECTING.name to tr("در حال اتصال", "Connecting"),
            Connection.CONNECTED.name to tr("وصل", "Connected"),
            Connection.DISCONNECTING.name to tr("در حال قطع", "Disconnecting")
        ),
        state.name
    ) { v -> state = Connection.valueOf(v) }
    LookOrbStyles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
            row.forEach { st ->
                val on = draft.orbStyle == st
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(GhajarRadius.lg))
                        .background(if (on) c.primary.copy(alpha = 0.12f) else c.secondaryCard)
                        .border(if (on) 1.5.dp else 0.dp, if (on) c.primary else Color.Transparent, RoundedCornerShape(GhajarRadius.lg))
                        .clickable { set(draft.copy(orbStyle = st)) }
                        .padding(vertical = GhajarSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ConnectOrb(
                        state = state, picking = false, enabled = true, tunnelDead = false, netOffline = false,
                        onClick = { set(draft.copy(orbStyle = st)) }, styleOverride = st, diameter = 104.dp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(names[st] ?: st, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        if (on) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
    ColorRows(draft, set, "orb")
}

@Composable
private fun CardsCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    SectionTitle(tr("چیدمان صفحه", "Layout"))
    Slab {
        Text(tr("ستون‌های لیست سرورها", "Server list columns"), color = ghajarColors.textPrimary)
        ChoiceChips(listOf("1" to tr("یک ستون", "One column"), "2" to tr("دو ستون", "Two columns")), draft.columns.toString()) {
            set(draft.copy(columns = it.toInt()))
        }
        Text(tr("تراکم", "Density"), color = ghajarColors.textPrimary)
        ChoiceChips(
            listOf("compact" to tr("فشرده", "Compact"), "comfortable" to tr("راحت", "Comfortable"), "spacious" to tr("باز", "Spacious")),
            draft.density
        ) { set(draft.copy(density = it)) }
        LabeledSlider(tr("گردی کارت‌ها", "Card radius"), draft.cardRadius.toFloat(), 4f..36f, 15, { "${it.toInt()}dp" }) {
            set(draft.copy(cardRadius = it.toInt()))
        }
        LabeledSlider(tr("سایه / ارتفاع", "Shadow / elevation"), draft.elevation.toFloat(), 0f..12f, 11, { "${it.toInt()}dp" }) {
            set(draft.copy(elevation = it.toInt()))
        }
    }
    SectionTitle(tr("پیش‌نمایش کارت‌ها", "Card preview"))
    Slab(spacing = 0.dp, padding = GhajarSpacing.sm, color = lookColor(LookElement.CONFIG_CARD)) {
        SlabRow(title = "Ghajar • DE-1", subtitle = "VLESS · Reality", icon = Icons.Filled.Shield,
            titleColor = lookColor(LookElement.CONFIG_TEXT), chevron = true)
    }
    ServerRowsMock()
    ColorRows(draft, set, "cards")
}

@Composable
private fun TilesCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    SectionTitle(tr("کاشی‌های دانلود و آپلود", "Download and upload tiles"))
    TrafficTiles("2.4 MB/s", "1.2 GB", "310 KB/s", "180 MB", Modifier.fillMaxWidth())
    ColorRows(draft, set, "tiles")
}

@Composable
private fun TypographyCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    val c = ghajarColors
    SectionTitle(tr("اندازه و وزن متن", "Size and weight"))
    Slab {
        LabeledSlider(tr("اندازه متن", "Text size"), draft.fontScale, 0.85f..1.25f, 7, { "${(it * 100).toInt()}%" }) {
            set(draft.copy(fontScale = (it * 20).toInt() / 20f))
        }
        Text(tr("نمونه متن فارسی و English 123", "Sample text فارسی 123"), color = c.textPrimary,
            style = MaterialTheme.typography.bodyLarge)
    }
    Slab(spacing = 0.dp) {
        SwitchRow(tr("عنوان‌های پررنگ", "Bold titles"), null, Icons.Filled.Style, draft.boldTitles) {
            set(draft.copy(boldTitles = it))
        }
    }
    ColorRows(draft, set, "typography")
}

@Composable
private fun AnimationCategory(store: ConfigStore, draft: GhajarLook, set: (GhajarLook) -> Unit) {
    val c = ghajarColors
    val reduce by store.reduceMotion.collectAsState()
    val names = listOf(
        "default" to tr("پیش‌فرض اپ", "App default"),
        "none" to tr("بدون انیمیشن", "None"),
        "fade" to tr("محو", "Fade"),
        "slide_h" to tr("لغزش افقی", "Slide horizontal"),
        "slide_v" to tr("لغزش عمودی", "Slide vertical"),
        "scale" to tr("بزرگ‌نمایی", "Scale"),
        "axis_x" to "Shared Axis X",
        "axis_y" to "Shared Axis Y",
        "axis_z" to "Shared Axis Z",
        "fade_through" to "Fade Through"
    )
    SectionTitle(tr("جابه‌جایی بین صفحه‌ها", "Page transitions"))
    Slab(spacing = 0.dp) {
        names.forEachIndexed { i, (key, label) ->
            if (i > 0) SlabDivider()
            SlabRow(
                title = label,
                onClick = { set(draft.copy(transition = key)) },
                trailing = {
                    if (draft.transition == key) Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(20.dp))
                }
            )
        }
    }
    SectionTitle(tr("سرعت", "Speed"))
    ChoiceChips(
        listOf("slow" to tr("آهسته", "Slow"), "normal" to tr("معمولی", "Normal"), "fast" to tr("سریع", "Fast")),
        draft.speed
    ) { set(draft.copy(speed = it)) }
    SectionTitle(tr("پیش‌نمایش حرکت", "Motion preview"))
    TransitionDemo(draft, reduce)
    Slab(spacing = 0.dp) {
        SwitchRow(
            tr("کاهش حرکت", "Reduce motion"),
            tr("انیمیشن‌ها را حداقل می‌کند (فوری ذخیره می‌شود).", "Keeps animation to a minimum (saved immediately)."),
            Icons.Filled.TimerOff, reduce
        ) { store.setReduceMotion(it) }
    }
}

@Composable
private fun TransitionDemo(draft: GhajarLook, reduce: Boolean) {
    val c = ghajarColors
    var page by remember { mutableIntStateOf(0) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(draft.transition, draft.speed) {
        while (true) { delay(1400); page = (page + 1) % 3 }
    }
    Box(
        Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(GhajarRadius.lg)).background(c.card)
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                draft.pageTransition(targetState > initialState || (initialState == 2 && targetState == 0), rtl, reduce)
                    ?: (fadeIn(tween(260)) togetherWith fadeOut(tween(180)))
            },
            label = "lookDemo"
        ) { p ->
            val tones = listOf(c.primary, c.info, c.accentAlt)
            Box(Modifier.fillMaxSize().padding(GhajarSpacing.md), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(GhajarRadius.md)).background(tones[p].copy(alpha = 0.18f))
                        .padding(GhajarSpacing.md),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(tr("صفحه ${p + 1}", "Page ${p + 1}"), fontWeight = FontWeight.Bold, color = tones[p])
                }
            }
        }
    }
}

@Composable
private fun AdvancedCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    val ctx = LocalContext.current
    val c = ghajarColors
    var importOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val okCopied = tr("در کلیپ‌بورد کپی شد.", "Copied to the clipboard.")
    SectionTitle(tr("خروجی و ورودی", "Export and import"))
    Slab(spacing = 0.dp) {
        SlabRow(
            title = tr("خروجی گرفتن از تنظیمات ظاهری", "Export appearance"),
            subtitle = tr("کپی و اشتراک به صورت JSON (پیش‌نویس فعلی)", "Copy and share as JSON (current draft)"),
            icon = Icons.Filled.Download,
            onClick = {
                val text = draft.toJson().toString(2)
                copyToClipboard(ctx, text)
                note = okCopied
                runCatching {
                    ctx.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        )
        SlabDivider()
        SlabRow(
            title = tr("وارد کردن تنظیمات ظاهری", "Import appearance"),
            subtitle = tr("JSON خروجی گرفته‌شده را جای‌گذاری کن", "Paste an exported JSON"),
            icon = Icons.Filled.FileUpload,
            onClick = { importOpen = true }
        )
    }
    note?.let { Text(it, color = c.good, style = MaterialTheme.typography.labelMedium) }
    SectionTitle(tr("همه رنگ‌های سفارشی", "All custom colours"))
    Slab(spacing = 0.dp) {
        LookElement.entries.forEachIndexed { i, e ->
            if (i > 0) SlabDivider()
            ColorRow(draft, set, e)
        }
    }
    if (importOpen) {
        var text by remember { mutableStateOf("") }
        var err by remember { mutableStateOf(false) }
        val cm = androidx.compose.ui.platform.LocalClipboardManager.current
        SkinDialog(title = tr("وارد کردن", "Import"), onDismiss = { importOpen = false }) {
            OutlinedTextField(
                value = text, onValueChange = { text = it; err = false },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                isError = err
            )
            if (err) Text(tr("این متن خروجی تنظیمات ظاهری نیست.", "That is not an appearance export."), color = c.error,
                style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                GhostPill(tr("از کلیپ‌بورد", "Paste"), onClick = { text = cm.getText()?.text.orEmpty() },
                    icon = Icons.Filled.ContentPaste, modifier = Modifier.weight(1f), minHeight = 42.dp)
                PillButton(tr("اعمال", "Apply"), onClick = {
                    val o = runCatching { org.json.JSONObject(text.trim()) }.getOrNull()
                    if (o == null || (!o.has("preset") && !o.has("colors"))) err = true
                    else { set(GhajarLook.fromJson(o)); importOpen = false }
                }, modifier = Modifier.weight(1f), minHeight = 42.dp)
            }
        }
    }
}

@Composable
private fun ResetCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    val c = ghajarColors
    var confirmAll by remember { mutableStateOf(false) }
    SectionTitle(tr("بازنشانی هر بخش", "Reset a category"))
    Text(
        tr("بازنشانی‌ها هم در پیش‌نمایش اعمال می‌شوند و با «ذخیره» ثبت می‌شوند.", "Resets land in the preview first; Save commits them."),
        style = MaterialTheme.typography.bodySmall, color = c.textSecondary
    )
    Slab(spacing = 0.dp) {
        Categories.filter { it.key !in setOf("advanced", "reset") }.forEachIndexed { i, cat ->
            if (i > 0) SlabDivider()
            SlabRow(
                title = tr(cat.fa, cat.en),
                icon = cat.icon,
                onClick = { set(draft.resetCategory(cat.key)) },
                trailing = { Icon(Icons.Filled.RestartAlt, null, tint = c.textMuted, modifier = Modifier.size(20.dp)) }
            )
        }
    }
    PillButton(
        tr("بازنشانی همه به پیش‌فرض", "Reset everything to default"),
        onClick = { confirmAll = true },
        icon = Icons.Filled.RestartAlt,
        accent = c.error
    )
    if (confirmAll) {
        SkinDialog(title = tr("بازنشانی همه؟", "Reset everything?"), onDismiss = { confirmAll = false }) {
            Text(tr("همه تنظیمات شخصی‌سازی به حالت اولیه برمی‌گردند.", "Every personalization setting goes back to default."),
                color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                GhostPill(tr("انصراف", "Cancel"), onClick = { confirmAll = false }, modifier = Modifier.weight(1f), minHeight = 42.dp)
                PillButton(tr("بازنشانی", "Reset"), onClick = { set(GhajarLook.Default); confirmAll = false },
                    accent = c.error, modifier = Modifier.weight(1f), minHeight = 42.dp)
            }
        }
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun SkinDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = ghajarColors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = GhajarSpacing.lg).fillMaxWidth()
                .clip(RoundedCornerShape(GhajarRadius.xl)).background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(GhajarRadius.xl))
                .padding(GhajarSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
            content()
        }
    }
}

private val PickerSwatches = listOf(
    0xFFFFFFFF, 0xFFB0B0B0, 0xFF5C5C5C, 0xFF000000, 0xFF00A86B, 0xFF24D98B, 0xFF14B8A6, 0xFF22D3EE,
    0xFF3D7BFF, 0xFF6366F1, 0xFFA855F7, 0xFFEC4899, 0xFFF43F5E, 0xFFEF4444, 0xFFF97316, 0xFFF59E0B
)

/**
 * HSV colour picker: saturation/value square, hue bar, alpha bar, swatches,
 * hex field, and a live preview of the element being coloured.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Color,
    themeDefault: Color,
    preview: @Composable (Color) -> Unit,
    onSave: (Color) -> Unit,
    onThemeDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = ghajarColors
    val start = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var alpha by remember { mutableFloatStateOf(initial.alpha) }
    val color = Color.hsv(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
    var hex by remember { mutableStateOf(hexOf(initial)) }
    var hexFocused by remember { mutableStateOf(false) }
    LaunchedEffect(color) { if (!hexFocused) hex = hexOf(color) }

    fun setFrom(col: Color) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(col.toArgb(), it) }
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]; alpha = col.alpha
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = GhajarSpacing.md).fillMaxWidth()
                .clip(RoundedCornerShape(GhajarRadius.xl)).background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(GhajarRadius.xl))
                .verticalScroll(rememberScrollState())
                .padding(GhajarSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = c.textPrimary, modifier = Modifier.weight(1f))
                Swatch(color, 32.dp)
            }
            // Live preview of the element itself.
            preview(color)

            // Pickers are drawn left-to-right in both languages: a hue bar does
            // not have a reading direction.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                    SvSquare(hue, sat, value) { s, v -> sat = s; value = v; hexFocused = false }
                    HueBar(hue) { hue = it; hexFocused = false }
                    AlphaBar(color.copy(alpha = 1f), alpha) { alpha = it; hexFocused = false }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val per = 8
                        val size = (maxWidth - 6.dp * (per - 1)) / per
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PickerSwatches.chunked(per).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { v ->
                                        val col = Color(v.toInt())
                                        Box(
                                            Modifier.size(size).clip(CircleShape).background(col)
                                                .border(1.dp, c.borderStrong, CircleShape)
                                                .clickable { setFrom(col); hexFocused = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { raw ->
                            hexFocused = true
                            val clean = raw.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }.take(9)
                            hex = clean
                            parseHex(clean)?.let { setFrom(it) }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                GhostPill(tr("بازنشانی", "Reset"), onClick = { setFrom(initial); hex = hexOf(initial); hexFocused = false },
                    modifier = Modifier.weight(1f), minHeight = 40.dp)
                GhostPill(tr("پیش‌فرض تم", "Theme default"), onClick = { setFrom(themeDefault); onThemeDefault() },
                    modifier = Modifier.weight(1f), minHeight = 40.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                GhostPill(tr("لغو", "Cancel"), onClick = onDismiss, modifier = Modifier.weight(1f), minHeight = 44.dp)
                PillButton(tr("ذخیره", "Save"), onClick = { onSave(color) }, modifier = Modifier.weight(1f), minHeight = 44.dp)
            }
        }
    }
}

private fun parseHex(s: String): Color? {
    val h = s.removePrefix("#")
    val v = when (h.length) {
        6 -> h.toLongOrNull(16)?.let { 0xFF000000 or it }
        8 -> h.toLongOrNull(16)
        else -> null
    } ?: return null
    return Color(v.toInt())
}

@Composable
private fun SvSquare(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }
    fun at(p: Offset) = onChange((p.x / w).coerceIn(0f, 1f), 1f - (p.y / h).coerceIn(0f, 1f))
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(GhajarRadius.md))
            .pointerInput(Unit) { detectTapGestures { at(it) } }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { at(it) }) { ch, _ -> at(ch.position) } }
    ) {
        w = size.width; h = size.height
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val p = Offset(sat * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 11.dp.toPx(), center = p, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = 13.dp.toPx(), center = p, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    var w by remember { mutableFloatStateOf(1f) }
    fun at(x: Float) = onChange((x / w).coerceIn(0f, 1f) * 360f)
    val hues = remember { (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }.let { it.dropLast(1) + Color.hsv(359.9f, 1f, 1f) } }
    Canvas(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(GhajarRadius.pill))
            .pointerInput(Unit) { detectTapGestures { at(it.x) } }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { at(it.x) }) { ch, _ -> at(ch.position.x) } }
    ) {
        w = size.width
        drawRect(Brush.horizontalGradient(hues))
        val x = hue / 360f * size.width
        drawCircle(Color.White, radius = size.height / 2.4f, center = Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun AlphaBar(color: Color, alpha: Float, onChange: (Float) -> Unit) {
    var w by remember { mutableFloatStateOf(1f) }
    fun at(x: Float) = onChange((x / w).coerceIn(0f, 1f))
    Canvas(
        Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(GhajarRadius.pill))
            .pointerInput(Unit) { detectTapGestures { at(it.x) } }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { at(it.x) }) { ch, _ -> at(ch.position.x) } }
    ) {
        w = size.width
        val cell = size.height / 2
        var i = 0
        var x = 0f
        while (x < size.width) {
            drawRect(if (i % 2 == 0) Color(0xFF9E9E9E) else Color(0xFFE0E0E0), Offset(x, 0f), androidx.compose.ui.geometry.Size(cell, cell))
            drawRect(if (i % 2 == 0) Color(0xFFE0E0E0) else Color(0xFF9E9E9E), Offset(x, cell), androidx.compose.ui.geometry.Size(cell, cell))
            x += cell; i++
        }
        drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color)))
        drawCircle(Color.White, radius = size.height / 2.4f, center = Offset(alpha * size.width, size.height / 2), style = Stroke(3.dp.toPx()))
    }
}

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
    LookCategory("palette", "رنگ‌های پایه", "Base colours", Icons.Filled.Palette),
    LookCategory("home", "خانه", "Home", Icons.Filled.Home),
    LookCategory("orb", "دکمه اتصال", "Connect button", Icons.Filled.PowerSettingsNew),
    LookCategory("nav", "ناوبری", "Navigation", Icons.Filled.SpaceDashboard),
    LookCategory("store", "فروشگاه", "Store", Icons.Filled.Storefront),
    LookCategory("settings", "تنظیمات", "Settings", Icons.Filled.Settings),
    LookCategory("servers", "انتخاب سرور", "Server list", Icons.Filled.Dns),
    LookCategory("addserver", "افزودن سرور", "Add server", Icons.Filled.PlaylistAdd),
    LookCategory("cards", "کارت‌ها", "Cards", Icons.Filled.ViewAgenda),
    LookCategory("tiles", "کاشی ترافیک", "Traffic tiles", Icons.Filled.Speed),
    LookCategory("typography", "متن", "Typography", Icons.Filled.FormatSize),
    LookCategory("animation", "انیمیشن", "Animation", Icons.Filled.Animation),
    LookCategory("presets", "پریست‌ها", "Presets", Icons.Filled.Bookmark),
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
    LookElement.BACKGROUND -> if (fa) "پس‌زمینه" else "Background"
    LookElement.SURFACE -> if (fa) "سطح (Surface)" else "Surface"
    LookElement.CARD -> if (fa) "کارت" else "Card"
    LookElement.SECONDARY_SURFACE -> if (fa) "سطح ثانویه" else "Secondary surface"
    LookElement.SELECTED -> if (fa) "انتخاب‌شده" else "Selected"
    LookElement.UNSELECTED -> if (fa) "انتخاب‌نشده" else "Unselected"
    LookElement.ICON -> if (fa) "آیکن‌ها" else "Icons"
    LookElement.SUCCESS -> if (fa) "موفق" else "Success"
    LookElement.WARNING -> if (fa) "هشدار" else "Warning"
    LookElement.ERROR -> if (fa) "خطا" else "Error"
    LookElement.PREMIUM -> if (fa) "ویژه (Premium)" else "Premium"
    LookElement.BADGE -> if (fa) "نشان (Badge)" else "Badge"
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
            navRadius = d.navRadius, navIndicator = d.navIndicator, navSpacing = d.navSpacing, colors = clean)
        "orb" -> copy(orbStyle = d.orbStyle, colors = clean)
        "cards" -> copy(columns = d.columns, density = d.density, cardRadius = d.cardRadius,
            elevation = d.elevation, colors = clean)
        "tiles" -> copy(colors = clean)
        "typography" -> copy(fontScale = d.fontScale, boldTitles = d.boldTitles, colors = clean)
        "animation" -> copy(transition = d.transition, speed = d.speed)
        "palette" -> copy(colors = clean)
        "home" -> copy(homeOrder = d.homeOrder, homeHidden = d.homeHidden, homeSizes = d.homeSizes)
        "store" -> copy(storeCardStyle = d.storeCardStyle, storeButtonStyle = d.storeButtonStyle,
            storeTabStyle = d.storeTabStyle, storeShow = d.storeShow)
        "settings" -> copy(settingsOrder = d.settingsOrder, settingsHidden = d.settingsHidden, tileSize = d.tileSize,
            settingsLayout = d.settingsLayout, iconStyle = d.iconStyle, gridGap = d.gridGap)
        "servers" -> copy(serverView = d.serverView, serverFields = d.serverFields)
        "addserver" -> copy(addServerStyle = d.addServerStyle)
        "presets" -> copy(customPresets = d.customPresets)
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
                    CategoryPreview(Categories[cat].key, previewState, onCycle = {
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
                            "palette" -> PaletteCategory(draft, set)
                            "home" -> HomeCategory(draft, set)
                            "store" -> StoreCategory(draft, set)
                            "settings" -> SettingsLayoutCategory(draft, set)
                            "servers" -> ServersCategory(draft, set)
                            "addserver" -> AddServerCategory(draft, set)
                            "presets" -> PresetsCategory(draft, set)
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
            onDismiss = { open = false },
            contrastWith = contrastPartner(e, c)
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
    SectionTitle(tr("نشانگر انتخاب", "Selection indicator"))
    ChoiceChips(
        listOf("rounded" to tr("گرد", "Rounded"), "filled" to tr("پُر", "Filled"), "outline" to tr("خطی", "Outline"),
            "dot" to tr("نقطه", "Dot")),
        draft.navIndicator
    ) { set(draft.copy(navIndicator = it)) }
    Slab {
        LabeledSlider(tr("فاصله آیتم‌ها", "Item spacing"), draft.navSpacing.toFloat(), 0f..12f, 11, { "${it.toInt()}dp" }) {
            set(draft.copy(navSpacing = it.toInt()))
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
        "circle" to tr("کلاسیک گرد", "Classic round"),
        "ring" to tr("حلقه", "Ring"),
        "compact" to tr("فشرده", "Compact"),
        "pill" to tr("قرص گرد", "Rounded pill"),
        "capsule_glow" to tr("کپسول درخشان", "Capsule glow"),
        "soft_square" to tr("مربع نرم", "Soft square"),
        "double_ring" to tr("حلقه دوتایی", "Double ring"),
        "neon" to tr("حلقه نئونی", "Neon ring"),
        "minimal" to tr("مینیمال تخت", "Minimal flat"),
        "segmented" to tr("بخش‌بخش", "Segmented"),
        "shield" to tr("سپر", "Shield"),
        "power" to tr("پاور", "Power")
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
                val text = draft.toExport(BuildConfig.VERSION_NAME).toString(2)
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
                    // Unknown fields are ignored and invalid values fall back
                    // to defaults inside fromAny; a broken paste never crashes.
                    val look = if (o == null || !looksLikeExport(o)) null else runCatching { GhajarLook.fromAny(o) }.getOrNull()
                    if (look == null) err = true
                    else { set(look); importOpen = false }
                }, modifier = Modifier.weight(1f), minHeight = 42.dp)
            }
        }
    }
}

@Composable
private fun ResetCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    val c = ghajarColors
    var confirmAll by remember { mutableStateOf(false) }
    var confirmCat by remember { mutableStateOf<LookCategory?>(null) }
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
                onClick = { confirmCat = cat },
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
    confirmCat?.let { cat ->
        SkinDialog(title = tr("بازنشانی «${cat.fa}»؟", "Reset ${cat.en}?"), onDismiss = { confirmCat = null }) {
            Text(tr("فقط تنظیمات همین بخش به پیش‌فرض برمی‌گردد.", "Only this section goes back to default."),
                color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                GhostPill(tr("انصراف", "Cancel"), onClick = { confirmCat = null }, modifier = Modifier.weight(1f), minHeight = 42.dp)
                PillButton(tr("بازنشانی", "Reset"), onClick = { set(draft.resetCategory(cat.key)); confirmCat = null },
                    accent = c.error, modifier = Modifier.weight(1f), minHeight = 42.dp)
            }
        }
    }
    if (confirmAll) {
        SkinDialog(title = tr("بازنشانی همه؟", "Reset everything?"), onDismiss = { confirmAll = false }) {
            Text(tr("همه تنظیمات شخصی‌سازی به حالت اولیه برمی‌گردند.", "Every personalization setting goes back to default."),
                color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                GhostPill(tr("انصراف", "Cancel"), onClick = { confirmAll = false }, modifier = Modifier.weight(1f), minHeight = 42.dp)
                PillButton(tr("بازنشانی", "Reset"), onClick = { set(GhajarLook.Default.copy(customPresets = draft.customPresets)); confirmAll = false },
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
    onDismiss: () -> Unit,
    /** The colour this one sits on or under; a low WCAG ratio shows a warning. */
    contrastWith: Color? = null
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
            contrastWith?.let { other ->
                val ratio = contrastRatio(color.copy(alpha = 1f), other.copy(alpha = 1f))
                if (ratio < 3f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = c.warning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tr("کنتراست کم (%.1f:1): ممکن است متن یا آیکن خوانا نباشد.".format(ratio),
                                "Low contrast (%.1f:1): text or icons may be hard to read.".format(ratio)),
                            style = MaterialTheme.typography.labelMedium, color = c.warning
                        )
                    }
                }
            }

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
        Modifier.fillMaxWidth().aspectRatio(1.6f)
            // TV: left/right adjusts saturation (the hex field sets any colour).
            .tvDpadAdjust(
                onLeft = { onChange((sat - 0.05f).coerceIn(0f, 1f), value) },
                onRight = { onChange((sat + 0.05f).coerceIn(0f, 1f), value) }
            )
            .clip(RoundedCornerShape(GhajarRadius.md))
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
        Modifier.fillMaxWidth().height(28.dp)
            // TV: left/right moves the hue with the remote.
            .tvDpadAdjust(
                onLeft = { onChange((hue - 6f).coerceIn(0f, 360f)) },
                onRight = { onChange((hue + 6f).coerceIn(0f, 360f)) },
                radius = GhajarRadius.pill
            )
            .clip(RoundedCornerShape(GhajarRadius.pill))
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
        Modifier.fillMaxWidth().height(22.dp)
            .tvDpadAdjust(
                onLeft = { onChange((alpha - 0.05f).coerceIn(0f, 1f)) },
                onRight = { onChange((alpha + 0.05f).coerceIn(0f, 1f)) },
                radius = GhajarRadius.pill
            )
            .clip(RoundedCornerShape(GhajarRadius.pill))
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

// ------------------------------------------------ layout categories (v1.0.10)

/**
 * A reorderable list with a drag handle (long list rows of a fixed height, so
 * the drag distance maps to a row index) plus up/down buttons for TalkBack and
 * for people who do not drag. Critical rows cannot be hidden.
 */
@Composable
private fun ReorderList(
    items: List<Pair<String, String>>,
    hidden: Set<String>,
    critical: Set<String>,
    onOrder: (List<String>) -> Unit,
    onHidden: (Set<String>) -> Unit
) {
    val c = ghajarColors
    val rowH = 56.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val ids = items.map { it.first }
    fun move(from: Int, to: Int) {
        if (from == to || to !in ids.indices) return
        onOrder(ids.toMutableList().also { it.add(to, it.removeAt(from)) })
    }
    Slab(spacing = 0.dp) {
        items.forEachIndexed { i, (id, label) ->
            if (i > 0) SlabDivider()
            val isHidden = id in hidden
            val locked = id in critical
            Row(
                Modifier.fillMaxWidth().height(rowH)
                    .offset { androidx.compose.ui.unit.IntOffset(0, if (dragging == id) dragDy.toInt() else 0) }
                    .background(if (dragging == id) c.primary.copy(alpha = 0.10f) else Color.Transparent),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.DragHandle, tr("جابه‌جایی", "Drag"), tint = c.textMuted,
                    modifier = Modifier.size(40.dp).padding(8.dp).pointerInput(ids) {
                        detectDragGestures(
                            onDragStart = { dragging = id; dragDy = 0f },
                            onDragEnd = {
                                val from = ids.indexOf(id)
                                val to = (from + (dragDy / rowPx).let { kotlin.math.round(it).toInt() }).coerceIn(0, ids.lastIndex)
                                dragging = null; dragDy = 0f
                                move(from, to)
                            },
                            onDragCancel = { dragging = null; dragDy = 0f }
                        ) { ch, d -> ch.consume(); dragDy += d.y }
                    }
                )
                Text(
                    label, color = if (isHidden) c.textMuted else c.textPrimary,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.KeyboardArrowUp, tr("بالا", "Up"), tint = if (i > 0) c.textSecondary else c.border,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(enabled = i > 0) { move(i, i - 1) }.padding(6.dp))
                Icon(Icons.Filled.KeyboardArrowDown, tr("پایین", "Down"), tint = if (i < ids.lastIndex) c.textSecondary else c.border,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(enabled = i < ids.lastIndex) { move(i, i + 1) }.padding(6.dp))
                if (locked) {
                    Icon(Icons.Filled.Lock, tr("همیشه نمایش", "Always shown"), tint = c.textMuted,
                        modifier = Modifier.size(36.dp).padding(8.dp))
                } else {
                    Icon(
                        if (isHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (isHidden) tr("نمایش", "Show") else tr("پنهان", "Hide"),
                        tint = if (isHidden) c.textMuted else c.primary,
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .clickable { onHidden(if (isHidden) hidden - id else hidden + id) }.padding(7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleChips(options: List<Pair<String, String>>, on: Set<String>, locked: Set<String> = emptySet(), onChange: (Set<String>) -> Unit) {
    val c = ghajarColors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val per = if (maxWidth < 360.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.chunked(per).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (key, label) ->
                        val sel = key in on
                        val fixed = key in locked
                        Row(
                            Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(GhajarRadius.pill))
                                .background(if (sel) c.primary.copy(alpha = 0.16f) else c.secondaryCard)
                                .border(1.dp, if (sel) c.primary else Color.Transparent, RoundedCornerShape(GhajarRadius.pill))
                                .clickable(enabled = !fixed) { onChange(if (sel) on - key else on + key) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (sel) Icons.Filled.Check else Icons.Filled.Add, null,
                                tint = if (sel) c.primary else c.textMuted, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium,
                                color = if (fixed) c.textMuted else c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(per - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun homePartLabel(id: String): String = when (id) {
    "orb" -> tr("دکمه اتصال", "Connect button")
    "session" -> tr("زمان و وضعیت اتصال", "Session status")
    "route" -> tr("سرور انتخاب‌شده", "Selected server")
    "quota" -> tr("حجم و اعتبار اشتراک", "Subscription quota")
    "traffic" -> tr("کاشی دانلود/آپلود", "Traffic tiles")
    "facts" -> tr("IP، موقعیت و پینگ", "IP, location and ping")
    else -> id
}

@Composable
private fun HomeCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    val c = ghajarColors
    SectionTitle(tr("ترتیب و نمایش بخش‌های خانه", "Home sections: order and visibility"))
    Text(
        tr("با دستگیره بکش یا با فلش‌ها جابه‌جا کن. دکمه اتصال و سرور انتخاب‌شده همیشه نمایش داده می‌شوند.",
            "Drag the handle or use the arrows. The connect button and the selected server always stay visible."),
        style = MaterialTheme.typography.bodySmall, color = c.textSecondary
    )
    val order = (draft.homeOrder.filter { it in HomeParts } + HomeParts).distinct()
    ReorderList(
        items = order.map { it to homePartLabel(it) },
        hidden = draft.homeHidden,
        critical = HomeCritical,
        onOrder = { set(draft.copy(homeOrder = it)) },
        onHidden = { set(draft.copy(homeHidden = it - HomeCritical)) }
    )
    SectionTitle(tr("اندازهٔ دکمه اتصال", "Connect button size"))
    ChoiceChips(
        listOf("compact" to tr("کوچک", "Small"), "normal" to tr("معمولی", "Normal"), "large" to tr("بزرگ", "Large")),
        draft.homeSizes["orb"] ?: "normal"
    ) { set(draft.copy(homeSizes = draft.homeSizes + ("orb" to it))) }
    SectionTitle(tr("اندازهٔ کاشی‌های ترافیک", "Traffic tile size"))
    ChoiceChips(
        listOf("compact" to tr("فشرده", "Compact"), "normal" to tr("معمولی", "Normal"), "large" to tr("بزرگ", "Large")),
        draft.homeSizes["traffic"] ?: "normal"
    ) { set(draft.copy(homeSizes = draft.homeSizes + ("traffic" to it))) }
}

@Composable
private fun StoreCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    SectionTitle(tr("کارت فروشگاه", "Shop card"))
    ChoiceChips(
        listOf("compact" to tr("فشرده", "Compact"), "card" to tr("کارت", "Card"), "large" to tr("بزرگ", "Large")),
        draft.storeCardStyle
    ) { set(draft.copy(storeCardStyle = it)) }
    SectionTitle(tr("دکمه‌های خرید (CTA)", "Buy buttons (CTA)"))
    ChoiceChips(
        listOf("filled" to tr("پُر", "Filled"), "tonal" to tr("ملایم", "Tonal"), "outline" to tr("خطی", "Outline")),
        draft.storeButtonStyle
    ) { set(draft.copy(storeButtonStyle = it)) }
    SectionTitle(tr("تب‌ها", "Tabs"))
    ChoiceChips(
        listOf("pill" to tr("کپسولی", "Pill"), "underline" to tr("زیرخط", "Underline"), "boxed" to tr("قاب‌دار", "Boxed")),
        draft.storeTabStyle
    ) { set(draft.copy(storeTabStyle = it)) }
    SectionTitle(tr("اجزای نمایش داده‌شده", "Shown parts"))
    Text(
        tr("نام فروشگاه و قیمت‌ها همیشه دیده می‌شوند؛ خرید بدون آن‌ها ممکن نیست.",
            "The shop name and prices always show: buying needs them."),
        style = MaterialTheme.typography.bodySmall, color = ghajarColors.textSecondary
    )
    val locked = setOf("title", "prices")
    ToggleChips(
        listOf(
            "logo" to tr("لوگو", "Logo"), "title" to tr("نام", "Name"), "desc" to tr("توضیحات", "Description"),
            "rating" to tr("امتیاز", "Rating"), "tick" to tr("تیک اعتماد", "Trust tick"), "links" to tr("لینک‌ها", "Links"),
            "report" to tr("دکمه گزارش", "Report button"), "discounts" to tr("کارت کد تخفیف", "Discount cards"),
            "services_count" to tr("تعداد سرویس‌ها", "Service count"), "prices" to tr("قیمت‌ها", "Prices")
        ),
        draft.storeShow + locked, locked
    ) { set(draft.copy(storeShow = it + locked)) }
}

@Composable
private fun SettingsLayoutCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    val t = stringsFn()
    SectionTitle(tr("ترتیب و نمایش کاشی‌های تنظیمات", "Settings tiles: order and visibility"))
    Text(
        tr("کاشی‌های حیاتی (شخصی‌سازی، پشتیبان، درباره، اتصال، هسته‌ها) پنهان نمی‌شوند.",
            "Critical tiles (personalization, backup, about, connection, cores) cannot be hidden."),
        style = MaterialTheme.typography.bodySmall, color = ghajarColors.textSecondary
    )
    val all = SettingsTiles.ALL.map { it.first }
    val order = (draft.settingsOrder.filter { it in all } + all).distinct()
    val labels = SettingsTiles.ALL.toMap()
    ReorderList(
        items = order.map { it to t(labels[it] ?: it) },
        hidden = draft.settingsHidden,
        critical = SettingsTiles.CRITICAL,
        onOrder = { set(draft.copy(settingsOrder = it)) },
        onHidden = { set(draft.copy(settingsHidden = it - SettingsTiles.CRITICAL)) }
    )
    SectionTitle(tr("اندازه کاشی", "Tile size"))
    ChoiceChips(listOf("compact" to tr("کوچک", "Small"), "normal" to tr("معمولی", "Normal"), "large" to tr("بزرگ", "Large")),
        draft.tileSize) { set(draft.copy(tileSize = it)) }
    SectionTitle(tr("چیدمان", "Layout"))
    ChoiceChips(listOf("compact" to tr("فشرده", "Compact"), "comfortable" to tr("راحت", "Comfortable")),
        draft.settingsLayout) { set(draft.copy(settingsLayout = it)) }
    SectionTitle(tr("سبک آیکن", "Icon style"))
    ChoiceChips(listOf("tinted" to tr("رنگی", "Tinted"), "filled" to tr("پُر", "Filled"), "outline" to tr("خطی", "Outline"),
        "plain" to tr("ساده", "Plain")), draft.iconStyle) { set(draft.copy(iconStyle = it)) }
    Slab {
        LabeledSlider(tr("فاصله کاشی‌ها", "Grid gap"), draft.gridGap.toFloat(), 4f..20f, 7, { "${it.toInt()}dp" }) {
            set(draft.copy(gridGap = it.toInt()))
        }
    }
}

@Composable
private fun ServersCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    SectionTitle(tr("نمای لیست سرورها", "Server list view"))
    ChoiceChips(
        listOf("list" to tr("لیست", "List"), "compact" to tr("لیست فشرده", "Compact list"),
            "grid" to tr("شبکه‌ای", "Grid"), "large" to tr("کارت بزرگ", "Large card")),
        draft.serverView
    ) { set(draft.copy(serverView = it)) }
    SectionTitle(tr("اطلاعات هر سرور", "Fields on each server"))
    Text(
        tr("مرتب‌سازی و گروه‌بندی فعلی دست نمی‌خورد. نام همیشه دیده می‌شود.",
            "Sorting and grouping stay as they are. The name always shows."),
        style = MaterialTheme.typography.bodySmall, color = ghajarColors.textSecondary
    )
    ToggleChips(
        listOf(
            "flag" to tr("پرچم", "Flag"), "name" to tr("نام", "Name"), "country" to tr("کشور", "Country"),
            "protocol" to tr("پروتکل", "Protocol"), "core" to tr("هسته", "Core"), "ping" to tr("پینگ", "Ping"),
            "quality" to tr("کیفیت", "Quality"), "favorite" to tr("علاقه‌مندی", "Favorite"),
            "last" to tr("آخرین اتصال", "Last connected"), "traffic" to tr("ترافیک", "Traffic"),
            "test" to tr("نتیجه تست", "Test result")
        ),
        draft.serverFields + "name", setOf("name")
    ) { set(draft.copy(serverFields = it + "name")) }
    ColorRows(draft, set, "cards")
}

@Composable
private fun AddServerCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    SectionTitle(tr("فرم افزودن سرور", "Add-server form"))
    Text(
        tr("بخش‌ها (پایه، احراز هویت، انتقال، TLS، شبکه، DNS، مسیریابی، پیشرفته) برای هر پروتکل فقط وقتی فیلد داشته باشند نمایش داده می‌شوند.",
            "Sections (basic, auth, transport, TLS, network, DNS, routing, advanced) appear only when the protocol has fields for them."),
        style = MaterialTheme.typography.bodySmall, color = ghajarColors.textSecondary
    )
    ChoiceChips(
        listOf("sections" to tr("بخش‌بندی", "Sections"), "tabs" to tr("تب‌ها", "Tabs"), "compact" to tr("فشرده (قدیمی)", "Compact (classic)")),
        draft.addServerStyle
    ) { set(draft.copy(addServerStyle = it)) }
}

@Composable
private fun PaletteCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    SectionTitle(tr("رنگ‌های پایه برنامه", "App base colours"))
    Text(
        tr("اگر کنتراست متن و پس‌زمینه کم باشد، در انتخابگر رنگ هشدار داده می‌شود.",
            "The picker warns when text and background contrast is too low."),
        style = MaterialTheme.typography.bodySmall, color = ghajarColors.textSecondary
    )
    ColorRows(draft, set, "palette")
    ColorRows(draft, set, "typography")
    ColorRows(draft, set, "tiles")
}

@Composable
private fun PresetsCategory(draft: GhajarLook, set: (GhajarLook) -> Unit) {
    val c = ghajarColors
    var saveOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    SectionTitle(tr("پریست‌های آماده", "Built-in presets"))
    Slab(spacing = 0.dp) {
        listOf(LookPreset.DEFAULT, LookPreset.AMOLED, LookPreset.MINIMAL, LookPreset.HIGH_CONTRAST).forEachIndexed { i, p ->
            if (i > 0) SlabDivider()
            SlabRow(
                title = tr(p.fa, p.en),
                icon = Icons.Filled.Palette,
                onClick = { set(draft.copy(preset = p.key, accent = null, amoled = p == LookPreset.AMOLED || draft.amoled && p != LookPreset.DEFAULT)) },
                trailing = { if (draft.preset == p.key) Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(20.dp)) }
            )
        }
    }
    SectionTitle(tr("پریست‌های من", "My presets"))
    if (draft.customPresets.isEmpty()) {
        Text(tr("هنوز پریستی ذخیره نکرده‌ای.", "No saved presets yet."),
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    } else {
        Slab(spacing = 0.dp) {
            draft.customPresets.keys.sorted().forEachIndexed { i, name ->
                if (i > 0) SlabDivider()
                SlabRow(
                    title = name,
                    subtitle = tr("لمس برای اعمال", "Tap to apply"),
                    icon = Icons.Filled.Bookmark,
                    onClick = {
                        val o = runCatching { org.json.JSONObject(draft.customPresets.getValue(name)) }.getOrNull()
                        if (o != null) set(GhajarLook.fromAny(o).copy(customPresets = draft.customPresets))
                    },
                    trailing = {
                        Icon(Icons.Filled.Delete, tr("حذف", "Delete"), tint = c.textMuted,
                            modifier = Modifier.size(36.dp).clip(CircleShape).clickable { deleting = name }.padding(7.dp))
                    }
                )
            }
        }
    }
    GhostPill(tr("ذخیره ظاهر فعلی به عنوان پریست", "Save the current look as a preset"), onClick = { saveOpen = true },
        icon = Icons.Filled.Save, minHeight = 44.dp)
    if (saveOpen) {
        var name by remember { mutableStateOf("") }
        SkinDialog(title = tr("نام پریست", "Preset name"), onDismiss = { saveOpen = false }) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                GhostPill(tr("انصراف", "Cancel"), onClick = { saveOpen = false }, modifier = Modifier.weight(1f), minHeight = 42.dp)
                PillButton(tr("ذخیره", "Save"), enabled = name.isNotBlank(), onClick = {
                    // A preset holds the look only, never other presets.
                    val body = draft.copy(customPresets = emptyMap()).toJson().toString()
                    set(draft.copy(customPresets = (draft.customPresets + (name.trim() to body)).entries.take(20).associate { it.key to it.value }))
                    saveOpen = false
                }, modifier = Modifier.weight(1f), minHeight = 42.dp)
            }
        }
    }
    deleting?.let { name ->
        SkinDialog(title = tr("حذف پریست؟", "Delete preset?"), onDismiss = { deleting = null }) {
            Text(name, color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                GhostPill(tr("انصراف", "Cancel"), onClick = { deleting = null }, modifier = Modifier.weight(1f), minHeight = 42.dp)
                PillButton(tr("حذف", "Delete"), accent = c.error, onClick = {
                    set(draft.copy(customPresets = draft.customPresets - name)); deleting = null
                }, modifier = Modifier.weight(1f), minHeight = 42.dp)
            }
        }
    }
}

// ------------------------------------------------ context-aware previews

@Composable
private fun CategoryPreview(key: String, state: Connection, onCycle: () -> Unit) {
    when (key) {
        "store" -> StorePreview()
        "settings" -> SettingsPreview()
        "servers", "cards" -> ServersPreview()
        "addserver" -> AddServerPreview()
        else -> LivePreview(state, onCycle)
    }
}

@Composable
private fun PreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    val c = ghajarColors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(GhajarRadius.lg)).background(c.background)
            .border(1.dp, c.border, RoundedCornerShape(GhajarRadius.lg)).padding(GhajarSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm),
        content = content
    )
}

@Composable
private fun StorePreview() {
    val c = ghajarColors
    val look = LocalGhajarLook.current
    var tab by remember { mutableIntStateOf(0) }
    PreviewFrame {
        Slab(spacing = 4.dp, padding = if (look.storeCardStyle == "compact") 8.dp else 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if ("logo" in look.storeShow) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.2f)))
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("فروشگاه نمونه", "Sample shop"), fontWeight = FontWeight.Bold, color = c.textPrimary)
                        if ("tick" in look.storeShow) Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(15.dp))
                    }
                    if ("rating" in look.storeShow) Text("★★★★☆ 4.2", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                }
            }
            if ("desc" in look.storeShow) Text(tr("توضیح کوتاه فروشگاه", "A short shop description"),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        if ("discounts" in look.storeShow) {
            Slab(spacing = 2.dp, padding = 8.dp) {
                Text("SAVE20", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = c.primary,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                    maxLines = 1, softWrap = false)
                Text(tr("۲۰٪ تخفیف • ۵ بار باقی‌مانده", "20% off • 5 uses left"), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
        TabRail(tabs = listOf(RailTab(tr("پلن‌ها", "Plans")), RailTab(tr("سرویس‌ها", "Services"))),
            selected = tab, onSelect = { tab = it }, style = look.storeTabStyle)
        StorePill(tr("خرید", "Buy"), onClick = {}, minHeight = 40.dp)
    }
}

@Composable
private fun SettingsPreview() {
    val t = stringsFn()
    val look = LocalGhajarLook.current
    val tiles = remember(look.settingsOrder, look.settingsHidden) {
        val all = SettingsTiles.ALL.map { it.first }
        (look.settingsOrder.filter { it in all } + all).distinct()
            .filter { it !in look.settingsHidden || it in SettingsTiles.CRITICAL }.take(4)
    }
    val labels = SettingsTiles.ALL.toMap()
    PreviewFrame {
        TileGrid(
            tiles.map { id ->
                SettingsTileSpec(id, t(labels[id] ?: id), null, Icons.Filled.Widgets, null, null, null, null,
                    id in SettingsTiles.CRITICAL) {}
            }
        )
    }
}

@Composable
private fun ServersPreview() {
    val c = ghajarColors
    val look = LocalGhajarLook.current
    val f = look.serverFields
    val big = look.serverView == "large"
    val compact = look.serverView == "compact"
    val rows = listOf(Triple("🇩🇪", "Ghajar • DE-1", 86), Triple("🇳🇱", "Ghajar • NL-2", 142))
    @Composable
    fun Cell(row: Triple<String, String, Int>, on: Boolean, modifier: Modifier) {
        Column(
            modifier.clip(RoundedCornerShape(GhajarRadius.lg))
                .background(if (on) lookColor(LookElement.SERVER_ACTIVE).copy(alpha = 0.16f) else lookColor(LookElement.SERVER_CARD))
                .padding(horizontal = GhajarSpacing.md, vertical = if (big) 14.dp else if (compact) 6.dp else 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if ("flag" in f) { Text(row.first); Spacer(Modifier.width(6.dp)) }
                Text(row.second, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                if ("favorite" in f && on) Icon(Icons.Filled.Bookmark, null, tint = c.primary, modifier = Modifier.size(14.dp))
                if ("ping" in f || "test" in f) Text("${row.third} ms", color = c.good, style = MaterialTheme.typography.labelMedium)
            }
            val meta = listOfNotNull(
                "Germany".takeIf { "country" in f }, "VLESS".takeIf { "protocol" in f }, "Xray".takeIf { "core" in f },
                tr("عالی", "Excellent").takeIf { "quality" in f }, "1.2 GB".takeIf { "traffic" in f },
                tr("۲ ساعت پیش", "2h ago").takeIf { "last" in f }
            )
            if (meta.isNotEmpty() && !compact) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    PreviewFrame {
        if (look.serverView == "grid" || look.columns == 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEachIndexed { i, r -> Cell(r, i == 0, Modifier.weight(1f)) }
            }
        } else rows.forEachIndexed { i, r -> Cell(r, i == 0, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun AddServerPreview() {
    val c = ghajarColors
    val look = LocalGhajarLook.current
    val secs = listOf(tr("پایه", "Basic"), tr("احراز هویت", "Auth"), tr("انتقال", "Transport"), "TLS")
    var sel by remember { mutableIntStateOf(0) }
    PreviewFrame {
        when (look.addServerStyle) {
            "tabs" -> {
                TabRail(tabs = secs.map { RailTab(it) }, selected = sel, onSelect = { sel = it })
                Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(GhajarRadius.md)).border(1.dp, c.border, RoundedCornerShape(GhajarRadius.md)))
            }
            "compact" -> repeat(3) {
                Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(GhajarRadius.md)).border(1.dp, c.border, RoundedCornerShape(GhajarRadius.md)))
            }
            else -> secs.take(2).forEach { s ->
                Text(s, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.primary)
                Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(GhajarRadius.md)).border(1.dp, c.border, RoundedCornerShape(GhajarRadius.md)))
            }
        }
    }
}

/** What an element is read against: foreground colours against the card, surfaces against the text. */
private fun contrastPartner(e: LookElement, c: GhajarPalette): Color = when (e) {
    LookElement.TITLE, LookElement.SUBTITLE, LookElement.CONFIG_TEXT, LookElement.NAV_LABEL, LookElement.NAV_ICON_ACTIVE,
    LookElement.NAV_ICON_INACTIVE, LookElement.ICON, LookElement.TILE_DOWN_ICON, LookElement.TILE_UP_ICON,
    LookElement.SUCCESS, LookElement.WARNING, LookElement.ERROR, LookElement.SELECTED, LookElement.UNSELECTED -> c.card
    else -> c.textPrimary
}

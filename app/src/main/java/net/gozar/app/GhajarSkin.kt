package net.gozar.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's skin, built from scratch.
 *
 * The previous look was "every element is its own bordered card": a screen was
 * a stack of eight identical outlined boxes, which reads as a list of eight
 * equally important things - i.e. as nothing. This replaces it with a different
 * idea:
 *
 *  - **One slab holds many rows.** A group of related controls is a single
 *    filled surface with inset separators between rows, not N separate cards.
 *    Far fewer edges, so the eye follows content instead of counting borders.
 *  - **Light catches the top edge.** Every slab draws a 1px brand-tinted
 *    gradient along its top edge, with a low-contrast border and a subtle
 *    surface gradient for depth.
 *  - **Headings are rails, not text.** A short vertical brand bar plus a rule
 *    that runs to the edge of the screen.
 *  - **Numbers live in strips.** Live values sit in one slab split by thin
 *    rules - one object, several readings - instead of one tile each.
 *  - **The selected thing slides.** Segmented controls animate a filled
 *    indicator to the active cell rather than repainting cells.
 *
 * Colours, spacing, radii and durations all come from GhajarDesign.kt; nothing
 * here carries a literal.
 */

/** Slab radius - larger than the old cards on purpose, so the shape reads as new. */
private val SlabRadius = GhajarRadius.lg

/**
 * The shared rounded container with a thin border and light along the top.
 *
 * [accent] tints that top edge for a state (pending, failed, active) without
 * changing the shape, so a slab never becomes a different kind of object.
 */
@Composable
fun Slab(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    padding: Dp = GhajarSpacing.lg,
    spacing: Dp = GhajarSpacing.md,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = ghajarColors
    val edge = accent ?: c.primary
    val shape = RoundedCornerShape(SlabRadius)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.card, c.surface)))
            .border(1.dp, accent?.copy(alpha = .38f) ?: c.borderStrong, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(
            Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
        // The whole treatment: one hairline of light along the top edge,
        // brightest in the middle, gone at the corners.
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to edge.copy(alpha = 0.55f),
                    1f to Color.Transparent
                )
            )
        }
    }
}

/**
 * One row inside a slab: a tinted glyph tile, a title, an optional subtitle,
 * and either a value, a chevron, or whatever trailing content the caller
 * supplies. This is the app's only list row.
 */
@Composable
fun SlabRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    accent: Color? = null,
    value: String? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val c = ghajarColors
    val tint = accent ?: c.primary
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.md))
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = GhajarSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        if (icon != null || iconRes != null) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(tint.copy(alpha = if (enabled) 0.14f else 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (enabled) tint else c.onDisabled,
                        modifier = Modifier.size(19.dp)
                    )
                } else if (iconRes != null) {
                    Icon(
                        androidx.compose.ui.res.painterResource(iconRes),
                        contentDescription = null,
                        tint = if (enabled) tint else c.onDisabled,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // mixedText() applies per-script font runs, so a Persian title with
            // Latin words inside it (protocol names, hostnames) keeps the right
            // face for each run instead of one font fighting both scripts.
            Text(
                mixedText(title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) c.textPrimary else c.onDisabled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    mixedText(subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1
            )
        }
        trailing?.invoke(this)
        if (chevron) {
            // Auto-mirrored: points the way "forward" goes in the active layout
            // direction, which under RTL is to the left.
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = c.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** The separator between rows of one slab: inset, so rows read as one object. */
@Composable
fun SlabDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 50.dp)
            .height(1.dp)
            .background(ghajarColors.border)
    )
}

/**
 * A section heading: a short brand bar, the label, then a rule to the edge.
 * Replaces the old bold brand-coloured title text.
 */
@Composable
fun Rail(label: String, modifier: Modifier = Modifier) {
    val c = ghajarColors
    Row(
        modifier.fillMaxWidth().padding(top = GhajarSpacing.sm, bottom = GhajarSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
    ) {
        Box(
            Modifier
                .size(width = 2.dp, height = 13.dp)
                .clip(RoundedCornerShape(GhajarRadius.pill))
                .background(c.primary)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = c.textSecondary,
            maxLines = 1
        )
        Box(Modifier.weight(1f).height(1.dp).background(c.border))
    }
}

/**
 * The top of a screen: a brand tick, the title, and one line of live context.
 * No app bar - the title is part of the content and scrolls with it.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    context: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val c = ghajarColors
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.xs)) {
            Box(
                Modifier
                    .size(width = 26.dp, height = 3.dp)
                    .clip(RoundedCornerShape(GhajarRadius.pill))
                    .background(c.primary)
            )
            Text(
                mixedText(title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!context.isNullOrBlank()) {
                Text(
                    mixedText(context),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** One reading inside a [StatStrip]. */
@Immutable
data class StatCell(
    val label: String,
    val value: String,
    val accent: Color? = null,
    val sub: String? = null,
    /** A reading that is also a destination: the cell itself becomes the tap
     *  target, so a strip never needs a row of buttons repeating its labels. */
    val onClick: (() -> Unit)? = null
)

/**
 * Several live readings in one slab, split by thin rules. One object with
 * several numbers, instead of one small card per number.
 */
@Composable
fun StatStrip(cells: List<StatCell>, modifier: Modifier = Modifier) {
    val c = ghajarColors
    if (cells.isEmpty()) return
    Slab(modifier, padding = GhajarSpacing.md, spacing = 0.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            cells.forEachIndexed { index, cell ->
                if (index > 0) {
                    Box(Modifier.width(1.dp).height(34.dp).background(c.border))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(GhajarRadius.md))
                        .then(
                            cell.onClick?.let { go -> Modifier.clickable { go() } } ?: Modifier
                        )
                        .padding(horizontal = GhajarSpacing.sm, vertical = GhajarSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        cell.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        cell.value,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = cell.accent ?: c.textPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (!cell.sub.isNullOrBlank()) {
                        Text(
                            cell.sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** The primary action: a tall filled capsule. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color? = null
) {
    val c = ghajarColors
    val tint = if (enabled) (accent ?: c.primary) else c.disabled
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(GhajarRadius.pill))
            .background(tint)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = GhajarSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) c.onPrimary else c.onDisabled,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(GhajarSpacing.sm))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled) c.onPrimary else c.onDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The secondary action: same capsule, outline only. */
@Composable
fun GhostPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color? = null
) {
    val c = ghajarColors
    val tint = if (enabled) (accent ?: c.primary) else c.onDisabled
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(GhajarRadius.pill))
            .border(1.5.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(GhajarRadius.pill))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = GhajarSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(GhajarSpacing.sm))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A square action: a glyph over its label, filled, no outline.
 *
 * For the handful of equally-weighted entry points a screen offers at once -
 * paste, type it in, open a file, scan a code. As outlined buttons they read as
 * a form; as tiles they read as a choice.
 */
@Composable
fun GlyphTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    enabled: Boolean = true
) {
    val c = ghajarColors
    val tint = if (enabled) (accent ?: c.primary) else c.onDisabled
    Column(
        modifier
            .clip(RoundedCornerShape(GhajarRadius.lg))
            .background(c.secondaryCard)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = GhajarSpacing.md, horizontal = GhajarSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(tint.copy(alpha = if (enabled) 0.16f else 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) c.textPrimary else c.onDisabled,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The skin's switch.
 *
 * Material's Switch carries its own outline, its own thumb shadow and its own
 * palette, so every settings page ended up looking like stock Android no matter
 * what the rows around it did. This is a plain capsule: the track takes the
 * brand tone when on, the thumb slides, and nothing is stroked.
 */
@Composable
fun SkinSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = ghajarColors
    val track = when {
        !enabled -> c.disabled
        checked -> c.primary
        else -> c.border
    }
    val offset by animateDpAsState(
        if (checked) 20.dp else 2.dp,
        tween(GhajarMotion.Fast, easing = FastOutSlowInEasing),
        label = "switchThumb"
    )
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(GhajarRadius.pill))
            .background(track)
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else Modifier
            )
    ) {
        Box(
            Modifier
                // padding(start=) is direction-aware, so the thumb travels the
                // correct way under RTL without mirroring the number here.
                .padding(start = offset)
                .align(Alignment.CenterStart)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) c.onPrimary else c.card)
        )
    }
}

/**
 * The skin's text field: filled, edgeless, with the label above the box rather
 * than floating through its outline.
 *
 * Material's outlined field brings a rounded stroke and a notched label, which
 * put a second border inside every slab and made a form read as a stack of
 * boxes inside a box. Here the field is the same tone as a nested surface, the
 * label is a plain caption above it, and focus is a brand-tinted underline.
 */
@Composable
fun SkinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = ghajarColors
    var focused by remember { mutableStateOf(false) }
    val underline = when {
        isError -> c.error
        focused -> c.primary
        else -> Color.Transparent
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GhajarSpacing.xs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (isError) c.error else c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GhajarRadius.md))
                .background(if (enabled) c.secondaryCard else c.disabled.copy(alpha = 0.25f))
                .drawBehind {
                    if (underline != Color.Transparent) {
                        val h = 2.dp.toPx()
                        drawRect(
                            color = underline,
                            topLeft = Offset(0f, size.height - h),
                            size = Size(size.width, h)
                        )
                    }
                }
                .padding(horizontal = GhajarSpacing.md, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .onFocusChanged { focused = it.isFocused }
                )
            }
            trailing?.invoke()
        }
        if (!helper.isNullOrBlank()) {
            Text(
                helper,
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) c.error else c.textMuted
            )
        }
    }
}

/** One destination in a [TabRail]. */
@Immutable
data class RailTab(
    val label: String,
    val icon: ImageVector? = null,
    /** A number worth seeing before you open the tab - unread, pending, owned. */
    val badge: Int? = null
)

/**
 * A single scrolling row of destinations.
 *
 * Six Persian labels never fit one readable segmented control, and stacking two
 * controls of three turned the top of a screen into two rows of chrome before
 * any content. A rail keeps them on one line: the active tab is filled, the
 * rest are quiet, and the rail scrolls itself so the active one is in view.
 */
@Composable
fun TabRail(
    tabs: List<RailTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = ghajarColors
    val state = rememberLazyListState()
    LaunchedEffect(selected) {
        runCatching { state.animateScrollToItem(selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))) }
    }
    LazyRow(
        modifier.fillMaxWidth(),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
    ) {
        itemsIndexed(tabs, key = { index, tab -> "${index}:${tab.label}" }) { index, tab ->
            val active = index == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(GhajarRadius.pill))
                    .background(if (active) c.primary else c.secondaryCard)
                    .clickable { onSelect(index) }
                    .padding(horizontal = GhajarSpacing.md, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (tab.icon != null) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (active) c.onPrimary else c.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) c.onPrimary else c.textSecondary,
                    maxLines = 1
                )
                if (tab.badge != null && tab.badge > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(GhajarRadius.pill))
                            .background(
                                if (active) c.onPrimary.copy(alpha = 0.22f)
                                else c.warning.copy(alpha = 0.18f)
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            tab.badge.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (active) c.onPrimary else c.warning,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * A segmented control whose filled indicator slides to the active cell.
 *
 * Laid out by measuring the track and animating the indicator's offset, so
 * cells never repaint on selection - which is what made the old tab strip feel
 * like six separate buttons.
 */
@Composable
fun SlidingSegments(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = ghajarColors
    var trackWidth by remember { mutableStateOf(0) }
    val count = labels.size.coerceAtLeast(1)
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Measured from the row of cells, not the padded track, so the indicator
    // lines up exactly with the cell it is under.
    val cellWidth = with(density) { (trackWidth / count).toDp() }
    // padding(start = ...) is already layout-direction aware, so this offset
    // must NOT be mirrored for RTL - doing both would cancel out.
    val offset by animateDpAsState(
        cellWidth * selected,
        tween(GhajarMotion.Base, easing = FastOutSlowInEasing),
        label = "segmentSlide"
    )
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.pill))
            .background(c.secondaryCard)
            .padding(4.dp)
    ) {
        if (trackWidth > 0) {
            Box(
                Modifier
                    .padding(start = offset)
                    .width(cellWidth)
                    .height(38.dp)
                    .clip(RoundedCornerShape(GhajarRadius.pill))
                    .background(c.primary)
            )
        }
        Row(Modifier.fillMaxWidth().onSizeChanged { trackWidth = it.width }) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(GhajarRadius.pill))
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) c.onPrimary else c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * The three content states, as real slabs rather than a bare line of text.
 * Every list in the app uses these so "nothing here", "still loading" and
 * "this failed" always look the same.
 */
@Composable
fun SkinLoading(label: String, modifier: Modifier = Modifier) {
    val c = ghajarColors
    Slab(modifier, spacing = GhajarSpacing.md) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        // A slim travelling bar: one animation, read only inside the draw
        // lambda, so it cannot resnapshot the screen around it.
        val move = rememberInfiniteTransition(label = "loadBar")
        val head by move.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = ghajarEndless(infiniteRepeatable(
                tween(1100, easing = FastOutSlowInEasing),
                RepeatMode.Restart
            )),
            label = "loadHead"
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(GhajarRadius.pill))
                .background(c.border)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width * 0.32f
                val x = (size.width + w) * head - w
                drawRoundRect(
                    color = c.primary,
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                )
            }
        }
    }
}

@Composable
fun SkinEmpty(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val c = ghajarColors
    Slab(modifier, padding = GhajarSpacing.xl, spacing = GhajarSpacing.md) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(c.card),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(24.dp))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                textAlign = TextAlign.Center
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (actionText != null && onAction != null) {
            PillButton(actionText, onAction)
        }
    }
}

@Composable
fun SkinError(
    message: String,
    modifier: Modifier = Modifier,
    retryText: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val c = ghajarColors
    Slab(modifier, accent = c.error, spacing = GhajarSpacing.md) {
        Text(
            message,
            style = MaterialTheme.typography.labelLarge,
            color = c.error
        )
        if (retryText != null && onRetry != null) {
            GhostPill(retryText, onRetry, accent = c.error)
        }
    }
}

/** One destination in [SkinNavBar]. */
@Immutable
data class SkinNavItem(val iconRes: Int, val label: String, val onSelect: () -> Unit)

/**
 * The bottom navigation: a floating capsule with one filled indicator that
 * slides between destinations.
 *
 * Built by hand rather than with NavigationBar because the Material bar cannot
 * do this - its indicator fades in place per item, and on the near-black
 * Premium Green canvas its surface dissolved into the background. This sits on
 * the card tone, clears the system navigation bar itself, and keeps all three
 * labels permanently visible so the bar never becomes a row of guesses.
 */
@Composable
fun SkinNavBar(items: List<SkinNavItem>, selected: Int, modifier: Modifier = Modifier) {
    val c = ghajarColors
    if (items.isEmpty()) return
    var trackWidth by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cellWidth = with(density) { (trackWidth / items.size).toDp() }
    // padding(start = ...) is layout-direction aware, so this must not be
    // mirrored for RTL by hand.
    val offset by animateDpAsState(
        cellWidth * selected,
        tween(GhajarMotion.Base, easing = FastOutSlowInEasing),
        label = "navSlide"
    )
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = GhajarSpacing.md, vertical = GhajarSpacing.sm)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GhajarRadius.xl))
                .background(c.surface)
                .border(1.dp, c.borderStrong, RoundedCornerShape(GhajarRadius.xl))
                .padding(5.dp)
        ) {
            if (trackWidth > 0) {
                Box(
                    Modifier
                        .padding(start = offset)
                        .width(cellWidth)
                        .height(54.dp)
                        .clip(RoundedCornerShape(GhajarRadius.lg))
                        .background(c.primary.copy(alpha = .14f))
                        .border(1.dp, c.primary.copy(alpha = .28f), RoundedCornerShape(GhajarRadius.lg))
                )
            }
            Row(Modifier.fillMaxWidth().onSizeChanged { trackWidth = it.width }) {
                items.forEachIndexed { index, item ->
                    val active = index == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(GhajarRadius.lg))
                            .testTag("root-nav-$index")
                            .clickable { item.onSelect() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            androidx.compose.ui.res.painterResource(item.iconRes),
                            contentDescription = item.label,
                            tint = if (active) c.highlight else c.textSecondary,
                            modifier = Modifier.size(23.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) c.highlight else c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


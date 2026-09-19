package net.gozar.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * "نقشه" — where you connect from and where you come out.
 *
 * The screen answers one question, and it answers it differently in the two
 * states rather than showing the same picture with a dead line in it: with no
 * tunnel there is one pin and a sentence saying that this is the location the
 * whole internet currently sees; with a tunnel there are two pins, an arc, and
 * the exit named.
 *
 * Every coordinate comes from how the network sees the address. Nothing here
 * touches Android's location APIs - a VPN app asking for GPS is the thing a
 * user should refuse, and physical position is not the question anyway.
 */
@Composable
fun GhajarMapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }

    val scope = rememberCoroutineScope()
    val state by VpnState.state.collectAsState()
    val home by GhajarMapState.home.collectAsState()
    val exit by GhajarMapState.exit.collectAsState()
    val busy by GhajarMapState.busy.collectAsState()
    val error by GhajarMapState.error.collectAsState()

    val connected = state == Connection.CONNECTED
    // The asset is read once per process; load() is cheap after that.
    val mapReady = remember { GhajarWorldMap.load(context) }

    // Re-ask whenever the tunnel state changes, because that is exactly when
    // the answer changes - and on first open.
    LaunchedEffect(connected) {
        if (!connected) GhajarMapState.clearExit()
        GhajarMapState.refresh(context, connected)
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = GhajarSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        Column(Modifier.padding(horizontal = GhajarSpacing.lg, vertical = GhajarSpacing.md)) {
            Text(
                t("map_title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ghajarColors.textPrimary
            )
            Text(
                t("map_subtitle"),
                style = MaterialTheme.typography.bodySmall,
                color = ghajarColors.textSecondary
            )
        }

        if (!mapReady) {
            // The outlines are missing. Say so and keep the readings, rather
            // than showing a blank rectangle that looks like a bug.
            SkinError(t("map_asset_missing"))
        }

        WorldCanvas(
            home = home,
            exit = if (connected) exit else GhajarMapState.Place(),
            connected = connected,
            outlines = if (mapReady) GhajarWorldMap.outlines else null
        )

        Column(
            Modifier.padding(horizontal = GhajarSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
        ) {
            if (error.isNotEmpty() && !home.usable) {
                SkinError(
                    if (error == "no_location") t("map_no_location") else t("map_lookup_failed")
                )
            }

            // Your own location. Labelled with when it was recorded, because
            // it cannot be re-checked while a tunnel is up and pretending
            // otherwise would be the one dishonest thing on this screen.
            PlaceRow(
                dotColor = ghajarColors.warning,
                title = if (connected) t("map_your_real") else t("map_you_are_here"),
                value = home.label.ifBlank { t("map_unknown") },
                note = when {
                    !home.usable -> null
                    home.approximate -> t("map_country_only")
                    else -> null
                }
            )

            if (connected && exit.usable) {
                PlaceRow(
                    dotColor = ghajarColors.primary,
                    title = t("map_exit"),
                    value = exit.label.ifBlank { t("map_unknown") },
                    note = if (exit.approximate) t("map_country_only") else null
                )
            }

            Text(
                if (connected) t("map_note_connected") else t("map_note_disconnected"),
                style = MaterialTheme.typography.bodySmall,
                color = ghajarColors.textMuted
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
            ) {
                GhostPill(
                    text = t("map_recheck"),
                    icon = Icons.Filled.Refresh,
                    onClick = {
                        // refresh() guards itself against overlapping calls, so
                        // a double tap costs nothing.
                        scope.launch { GhajarMapState.refresh(context, connected) }
                    }
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ghajarColors.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(dotColor: Color, title: String, value: String, note: String?) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(color = dotColor, radius = size.minDimension / 2f)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = ghajarColors.textSecondary
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ghajarColors.textPrimary
            )
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = ghajarColors.textMuted
                )
            }
        }
    }
}

/**
 * The map itself.
 *
 * Drawn rather than composed: 268 rings is far too many composables, and a
 * Canvas draws them in one pass. The animation values are read only inside the
 * draw lambda, so a moving dot invalidates the drawing and nothing else -
 * reading them in the composable body would recompose this whole subtree sixty
 * times a second.
 */
@Composable
private fun WorldCanvas(
    home: GhajarMapState.Place,
    exit: GhajarMapState.Place,
    connected: Boolean,
    outlines: List<FloatArray>?
) {
    val land = ghajarColors.secondaryCard
    val border = ghajarColors.border
    val homeColor = ghajarColors.warning
    val exitColor = ghajarColors.primary

    val transition = rememberInfiniteTransition(label = "map")
    // Only animated when there is actually a route to animate along.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow"
    )

    // The outlines are in 0..1 on an equirectangular projection, so the canvas
    // keeps a 2:1 box: anything else stretches the world.
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            outlines?.forEach { ring ->
                val path = Path()
                path.moveTo(ring[0] * size.width, ring[1] * size.height)
                var i = 2
                while (i < ring.size) {
                    path.lineTo(ring[i] * size.width, ring[i + 1] * size.height)
                    i += 2
                }
                path.close()
                drawPath(path, color = land)
                drawPath(path, color = border, style = Stroke(width = 0.7f))
            }

            val homePoint = home.point?.let { (lat, lon) ->
                GhajarWorldMap.project(lat, lon).let { (x, y) ->
                    Offset(x * size.width, y * size.height)
                }
            }
            val exitPoint = exit.point?.let { (lat, lon) ->
                GhajarWorldMap.project(lat, lon).let { (x, y) ->
                    Offset(x * size.width, y * size.height)
                }
            }

            if (connected && homePoint != null && exitPoint != null) {
                drawRoute(homePoint, exitPoint, exitColor, phase, size)
            }
            homePoint?.let { pin(it, homeColor, home.countryCode) }
            exitPoint?.let { pin(it, exitColor, exit.countryCode) }
        }

        // The flags sit above the canvas as text, because an emoji is a font
        // glyph and Canvas has no business laying one out.
        FlagBadges(home, exit, connected)
    }
}

/**
 * An arc from one pin to the other, with a few dots travelling along it.
 *
 * A curve rather than a straight line for a practical reason as much as a
 * pretty one: on an equirectangular map a straight line between two points
 * often runs through a third pin or off the edge, and the bow makes the
 * direction of travel readable at a glance.
 */
private fun DrawScope.drawRoute(
    from: Offset,
    to: Offset,
    color: Color,
    phase: Float,
    canvas: Size
) {
    val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (length < 1f) return
    // Bow perpendicular to the line, a fixed fraction of its length, capped so
    // a long route does not arc off the top of the map.
    val lift = (length * 0.22f).coerceAtMost(canvas.height * 0.28f)
    val control = Offset(mid.x - dy / length * lift, mid.y + dx / length * lift)

    // Flattened into line segments rather than a Path quadratic: the Compose
    // API for that has been renamed between versions, and evaluating the curve
    // here uses the very same maths the dots below already rely on.
    val path = Path().apply {
        moveTo(from.x, from.y)
        val steps = 28
        for (step in 1..steps) {
            val tt = step / steps.toFloat()
            val inv = 1f - tt
            lineTo(
                inv * inv * from.x + 2f * inv * tt * control.x + tt * tt * to.x,
                inv * inv * from.y + 2f * inv * tt * control.y + tt * tt * to.y
            )
        }
    }
    drawPath(path, color = color.copy(alpha = 0.55f), style = Stroke(width = 2f))

    // Four dots, evenly spaced around the loop, so the flow reads as
    // continuous rather than as one dot restarting.
    repeat(4) { index ->
        val tt = ((phase + index / 4f) % 1f)
        val inv = 1f - tt
        val x = inv * inv * from.x + 2f * inv * tt * control.x + tt * tt * to.x
        val y = inv * inv * from.y + 2f * inv * tt * control.y + tt * tt * to.y
        drawCircle(color = color, radius = 3f, center = Offset(x, y))
    }
}

/** A pin: a soft halo, a ring, and a filled centre. */
private fun DrawScope.pin(at: Offset, color: Color, countryCode: String) {
    drawCircle(color = color.copy(alpha = 0.18f), radius = 16f, center = at)
    drawCircle(color = color.copy(alpha = 0.45f), radius = 9f, center = at)
    drawCircle(color = color, radius = 4.5f, center = at)
}

/**
 * The two endpoint chips under the map.
 *
 * Flag emoji rather than bundled images: every code point is already in the
 * system font, so this costs nothing in the APK and is right for any country
 * the lookup returns rather than only the ones someone remembered to draw.
 */
@Composable
private fun BoxScope.FlagBadges(
    home: GhajarMapState.Place,
    exit: GhajarMapState.Place,
    connected: Boolean
) {
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(GhajarSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (home.usable) EndpointChip(home)
        if (connected && exit.usable) {
            Text("→", color = ghajarColors.textMuted, fontSize = 16.sp)
            EndpointChip(exit)
        }
    }
}

@Composable
private fun EndpointChip(place: GhajarMapState.Place) {
    Slab(padding = GhajarSpacing.sm) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val flag = flagEmoji(place.countryCode)
            if (flag != null) {
                Text(flag, fontSize = 18.sp)
            } else {
                androidx.compose.material3.Icon(
                    Icons.Filled.Public,
                    contentDescription = null,
                    tint = ghajarColors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                place.city.ifBlank { place.countryCode },
                style = MaterialTheme.typography.labelMedium,
                color = ghajarColors.textPrimary
            )
        }
    }
}

/**
 * A two-letter country code as its flag emoji, or null.
 *
 * Regional indicator symbols are the letters A-Z offset to U+1F1E6, and a pair
 * of them is a flag. Anything that is not exactly two ASCII letters comes back
 * null rather than as two stray boxes.
 */
internal fun flagEmoji(countryCode: String): String? {
    val code = countryCode.trim().uppercase()
    if (code.length != 2) return null
    if (code.any { it !in 'A'..'Z' }) return null
    val base = 0x1F1E6
    return String(Character.toChars(base + (code[0] - 'A'))) +
        String(Character.toChars(base + (code[1] - 'A')))
}

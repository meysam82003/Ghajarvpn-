package net.gozar.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The home screen's own pieces, on the [Slab] skin.
 *
 * The connect control is the screen. It sits dead centre, it is the largest
 * object on the page, and everything else is arranged around it: what it will
 * connect to, how long it has been connected, and what it is currently moving.
 */

/**
 * The connect control: one circle that carries the whole connection state.
 *
 * The ring is the state, the glyph is the action, and the label under the glyph
 * says what a tap will do. Tapping connects, disconnects, or cancels an
 * in-flight auto-pick - the three behaviours the old full-width bar had, in one
 * place and one shape.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConnectOrb(
    state: Connection,
    picking: Boolean,
    /** A selected server exists (or a tunnel is up), so a tap can do something. */
    enabled: Boolean,
    /** Connected, but the health probe says nothing is getting through. */
    tunnelDead: Boolean,
    netOffline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Long press while a tunnel is up: drop it and redial the same server.
     *
     * A tunnel that is connected but carrying nothing is the one fault a user
     * cannot fix from this screen - disconnect, then find the server again,
     * then connect. This is that, in one gesture, without leaving home.
     */
    onReconnect: (() -> Unit)? = null,
    /** Personalization's preview draws a style before it is saved. */
    styleOverride: String? = null,
    diameter: androidx.compose.ui.unit.Dp = 236.dp,
    /** Android TV: lets the home screen hand the remote's focus to this control. */
    orbFocus: androidx.compose.ui.focus.FocusRequester? = null
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }

    val connectedish = state == Connection.CONNECTED || state == Connection.CONNECTING
    val working = picking || state == Connection.CONNECTING || state == Connection.DISCONNECTING
    val faulted = netOffline || tunnelDead || state == Connection.ERROR

    val look = LocalGhajarLook.current
    val style = styleOverride ?: look.orbStyle
    val idleTint = lookColor(LookElement.CONNECT)
    val onTint = lookColor(LookElement.DISCONNECT)
    val tint by animateColorAsState(
        when {
            faulted -> c.error
            state == Connection.CONNECTED -> onTint
            working -> c.highlight
            else -> idleTint
        },
        tween(420),
        label = "orbTint"
    )

    // Ring fill: empty when off, a short travelling arc while working, full
    // once the tunnel is actually up.
    val fill by animateFloatAsState(
        when {
            state == Connection.CONNECTED -> 1f
            working -> 0.22f
            else -> 0f
        },
        tween(GhajarMotion.Slow),
        label = "orbFill"
    )

    // ghajarPulse schedules nothing while its condition is false: the
    // travelling arc exists while a connection is being made, the breath while
    // the tunnel is up. Off and idle costs zero frames.
    val sweepState = ghajarPulse(
        active = working,
        durationMillis = 1500,
        from = -90f,
        to = 270f,
        reverse = false
    )
    val breathState = ghajarPulse(
        active = state == Connection.CONNECTED,
        durationMillis = 2600,
        easing = FastOutSlowInEasing
    )

    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        if (pressed && (enabled || picking)) 0.96f else 1f,
        tween(GhajarMotion.Fast, easing = FastOutSlowInEasing),
        label = "orbPress"
    )

    val k = diameter.value / 236f * (if (style == "compact") 0.72f else 1f)
    val wide = style == "pill" || style == "capsule_glow"
    val shape = when (style) {
        "pill", "capsule_glow" -> RoundedCornerShape(50)
        "soft_square" -> RoundedCornerShape((52 * k).dp)
        "shield" -> ShieldShape
        else -> CircleShape
    }
    val (w, h) = when (style) {
        "pill", "capsule_glow" -> diameter * 1.08f to diameter * 0.44f
        "soft_square" -> diameter * 0.82f to diameter * 0.82f
        "compact" -> diameter * 0.72f to diameter * 0.72f
        else -> diameter to diameter
    }
    val filled = style == "pill"
    val ink = if (filled) (if (tint.luminance() > 0.45f) Color(0xFF07100C) else Color.White) else tint
    val textInk = when {
        filled -> ink
        enabled || picking -> c.textPrimary
        else -> c.onDisabled
    }

    // The outer box keeps the control's footprint the same in every style, so
    // the rest of home never moves when the style changes.
    Box(
        modifier
            .size(width = maxOf(diameter, w), height = diameter)
            .drawBehind {
                // Halo outside the shape: breath when connected, and the
                // capsule/neon styles glow at rest too.
                val glowBase = when (style) {
                    "capsule_glow", "neon" -> 0.10f
                    else -> 0f
                }
                val breath = if (state == Connection.CONNECTED) breathState.value else 0f
                val a = glowBase + if (state == Connection.CONNECTED) 0.14f + 0.10f * breath else 0f
                if (a > 0f) {
                    if (wide || style == "soft_square") {
                        val grow = 10.dp.toPx() * k * (1f + breath)
                        val ww = w.toPx() + grow * 2
                        val hh = h.toPx() + grow * 2
                        drawRoundRect(
                            color = tint.copy(alpha = a * 0.7f),
                            topLeft = Offset((size.width - ww) / 2f, (size.height - hh) / 2f),
                            size = Size(ww, hh),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                if (wide) hh / 2f else 60.dp.toPx() * k
                            )
                        )
                    } else {
                        drawCircle(
                            brush = Brush.radialGradient(listOf(tint.copy(alpha = a + 0.04f), Color.Transparent)),
                            radius = size.minDimension * (0.47f + 0.04f * breath)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // TV focus: a ring that follows the control's own outline (a circle,
        // a capsule, a rounded square), so the remote's focus is unmistakable.
        val focusRadius = when (style) {
            "soft_square" -> (52 * k).dp
            "shield" -> (40 * k).dp
            else -> 999.dp
        }
        Box(
            Modifier
                .size(width = w, height = h)
                .then(
                    if (orbFocus != null) Modifier.focusRequester(orbFocus) else Modifier
                )
                .tvFocusGlow(focusRadius, ring = true, scaleTo = 1.04f)
                .graphicsLayer { scaleX = press; scaleY = press }
                .pointerInput(enabled, picking) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        waitForUpOrCancellation()
                        pressed = false
                    }
                }
                .clip(shape)
                .combinedClickable(
                    enabled = enabled || picking,
                    onClick = onClick,
                    onLongClick = onReconnect?.takeIf { state == Connection.CONNECTED }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Animation values are read inside the draw lambda only, so an
            // animating ring never recomposes this subtree.
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.028f
                val inset = stroke * 2.6f
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                val connectedNow = state == Connection.CONNECTED
                val start = if (working) sweepState.value else -90f
                when (style) {
                    "pill", "capsule_glow", "soft_square" -> {
                        val fillA = when (style) {
                            "pill" -> if (enabled || picking) 1f else 0.35f
                            else -> 1f
                        }
                        drawRect(if (style == "pill") tint.copy(alpha = fillA) else c.secondaryCard)
                        if (style != "pill") {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    listOf(tint.copy(alpha = if (connectedNow) 0.26f else 0.12f), Color.Transparent)
                                )
                            )
                        } else {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)
                                )
                            )
                        }
                        // Working: a bar travels along the bottom edge.
                        if (working) {
                            val t = (sweepState.value + 90f) / 360f
                            val bw = size.width * 0.3f
                            drawRect(
                                color = if (style == "pill") ink.copy(alpha = 0.55f) else tint,
                                topLeft = Offset((size.width + bw) * t - bw, size.height - 4.dp.toPx()),
                                size = Size(bw, 4.dp.toPx())
                            )
                        }
                    }
                    "shield" -> {
                        // A shield: the slab tone, washed with the state colour,
                        // with a travelling edge while working (see the border
                        // drawn over the shape below).
                        drawRect(c.secondaryCard)
                        drawRect(brush = Brush.verticalGradient(
                            listOf(tint.copy(alpha = if (connectedNow) 0.30f else 0.14f), Color.Transparent)))
                        if (working) {
                            val t = (sweepState.value + 90f) / 360f
                            drawRect(tint.copy(alpha = 0.18f), topLeft = Offset(0f, size.height * (1f - t)),
                                size = Size(size.width, size.height * t))
                        }
                    }
                    "power" -> {
                        // The power symbol itself: a ring open at the top and a
                        // bar through the gap. The ring fills as the tunnel comes up.
                        drawCircle(c.secondaryCard, radius = size.minDimension / 2f - inset)
                        val gap = 40f
                        drawArc(c.border, -90f + gap, 360f - gap * 2, false, topLeft, arcSize,
                            style = Stroke(stroke * 1.4f, cap = StrokeCap.Round))
                        val sweep = when {
                            connectedNow -> 360f - gap * 2
                            working -> (360f - gap * 2) * ((sweepState.value + 90f) / 360f)
                            else -> 0f
                        }
                        if (sweep > 0f) drawArc(tint, -90f + gap, sweep, false, topLeft, arcSize,
                            style = Stroke(stroke * 1.6f, cap = StrokeCap.Round))
                        drawLine(if (enabled || picking) tint else c.onDisabled,
                            Offset(size.width / 2f, inset - stroke), Offset(size.width / 2f, size.height * 0.30f),
                            strokeWidth = stroke * 1.6f, cap = StrokeCap.Round)
                    }
                    "neon" -> {
                        drawCircle(c.background, radius = size.minDimension / 2f - inset)
                        for (i in 3 downTo 1) {
                            drawArc(
                                color = tint.copy(alpha = (if (connectedNow) 0.16f else 0.08f) * i),
                                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = Stroke(width = stroke * (1f + i * 1.3f))
                            )
                        }
                        drawArc(
                            color = tint.copy(alpha = if (enabled || picking) 1f else 0.4f),
                            startAngle = start, sweepAngle = if (working) 110f else 360f, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    "minimal" -> {
                        drawCircle(
                            tint.copy(alpha = if (connectedNow) 0.22f else 0.12f),
                            radius = size.minDimension / 2f - inset
                        )
                        if (working) drawArc(
                            color = tint, startAngle = start, sweepAngle = 70f, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke * 0.6f, cap = StrokeCap.Round)
                        )
                    }
                    "segmented" -> {
                        drawCircle(c.secondaryCard, radius = size.minDimension / 2f - inset)
                        val n = 36
                        val step = 360f / n
                        val head = ((sweepState.value + 90f) / step).toInt()
                        for (i in 0 until n) {
                            val lit = when {
                                connectedNow -> true
                                working -> ((head - i + n) % n) < 8
                                else -> false
                            }
                            drawArc(
                                color = if (lit) tint else c.border,
                                startAngle = -90f + i * step + 1.5f,
                                sweepAngle = step - 3f,
                                useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = Stroke(width = stroke * 1.6f)
                            )
                        }
                    }
                    "ring" -> {
                        // Ring: no disc, just the track and the state arc.
                        drawArc(c.border, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke * 1.2f))
                        drawArc(
                            tint.copy(alpha = if (enabled || picking) 1f else 0.4f), start,
                            if (fill > 0f) 360f * fill else 0f, false, topLeft, arcSize,
                            style = Stroke(stroke * 2f, cap = StrokeCap.Round)
                        )
                    }
                    "double_ring" -> {
                        drawCircle(c.secondaryCard, radius = size.minDimension / 2f - inset)
                        val inner = inset + stroke * 3.2f
                        val innerSize = Size(size.width - inner * 2, size.height - inner * 2)
                        drawArc(c.border, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                        drawArc(c.border, 0f, 360f, false, Offset(inner, inner), innerSize, style = Stroke(stroke * 0.7f))
                        if (fill > 0f) {
                            drawArc(
                                tint, start, 360f * fill, false, topLeft, arcSize,
                                style = Stroke(stroke * 1.4f, cap = StrokeCap.Round)
                            )
                            drawArc(
                                tint.copy(alpha = 0.7f), if (working) -start else -90f, 360f * fill, false,
                                Offset(inner, inner), innerSize,
                                style = Stroke(stroke * 0.9f, cap = StrokeCap.Round)
                            )
                        }
                    }
                    else -> {
                        // Circle: the disc, lifted towards the state colour, and the ring.
                        drawCircle(color = c.secondaryCard, radius = size.minDimension / 2f - inset)
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(tint.copy(alpha = if (connectedNow) 0.20f else 0.10f), Color.Transparent)
                            ),
                            radius = size.minDimension / 2f - inset
                        )
                        drawArc(
                            color = c.border, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        if (fill > 0f) {
                            drawArc(
                                color = tint, startAngle = start, sweepAngle = 360f * fill, useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
            if (style == "capsule_glow" || style == "soft_square" || style == "shield") {
                Box(
                    Modifier.fillMaxSize().border(
                        1.5.dp, tint.copy(alpha = if (enabled || picking) 0.8f else 0.3f), shape
                    )
                )
            }

            val icon = when {
                picking -> Icons.Filled.Close
                state == Connection.CONNECTED -> Icons.Filled.PowerSettingsNew
                state == Connection.CONNECTING -> Icons.Filled.Close
                else -> Icons.Filled.Bolt
            }
            val label = when {
                picking -> t("finding_fastest")
                state == Connection.CONNECTED -> t("disconnect")
                state == Connection.CONNECTING -> t("connecting_cancel")
                state == Connection.DISCONNECTING -> t("hub_disconnecting")
                else -> t("connect")
            }
            if (wide) {
                Row(
                    Modifier.padding(horizontal = (18 * k).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((10 * k).dp)
                ) {
                    androidx.compose.material3.Icon(
                        icon, contentDescription = null, tint = ink, modifier = Modifier.size((30 * k).dp)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textInk,
                        fontSize = (17 * k).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(
                    // The label lives inside the shape, bounded by the square
                    // that fits in the circle - no string can spill past the ring.
                    Modifier.widthIn(max = (156 * k).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy((GhajarSpacing.sm.value * k).dp)
                ) {
                    androidx.compose.material3.Icon(
                        icon, contentDescription = null, tint = tint, modifier = Modifier.size((44 * k).dp)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textInk,
                        fontSize = (16 * k).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (!enabled && !picking && !connectedish && k > 0.8f) {
                        Text(
                            t("hub_no_server"),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Session length, shown under the orb only while a tunnel is actually up. */
@Composable
fun SessionLine(startMs: Long?, state: Connection) {
    val c = ghajarColors
    val lang = LocalLang.current
    if (state != Connection.CONNECTED || startMs == null || startMs <= 0L) {
        Spacer(Modifier.height(1.dp))
        return
    }
    val now = rememberSecondTick()
    val elapsed = ((now - startMs) / 1000).coerceAtLeast(0L)
    Text(
        localizeDigits(
            "%02d:%02d:%02d".format(elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60),
            lang
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = c.highlight,
        fontSize = 19.sp
    )
}

/**
 * Measured facts about the tunnel, as rows of one slab: the public IP we
 * present, where it looks like, and the live ping to the active server. All
 * three are real measurements, taken once per settled state - not on every
 * frame, and not while the tunnel is still coming up.
 *
 * [extra] lets the caller append its own rows (the home screen puts the
 * real-delay test there) so they share one slab instead of adding another.
 */
@Composable
fun ConnectionFacts(
    state: Connection,
    serverAddress: String?,
    serverPort: Int?,
    modifier: Modifier = Modifier,
    /**
     * Whether the IP/location lookup should go through the tunnel's local SOCKS
     * inbound. True for the Xray engines, which publish one; false for OpenVPN
     * and IKEv2, which route the whole device instead, so a plain request is
     * already inside the tunnel and a proxied one reaches nothing.
     */
    throughLocalProxy: Boolean = true,
    // The ping row is the only latency reading on this screen. It shows the
    // passive TCP handshake by default and, once the user taps it, whatever the
    // real-delay test measured - one row, not two saying the same word.
    measuredDelay: String? = null,
    delayRunning: Boolean = false,
    onMeasureDelay: (() -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }

    val connected = state == Connection.CONNECTED
    val busy = state == Connection.CONNECTING || state == Connection.DISCONNECTING

    var location by remember { mutableStateOf<IpLocation?>(null) }
    LaunchedEffect(connected, throughLocalProxy) {
        if (busy) return@LaunchedEffect
        // Let a fresh tunnel's routes settle before asking who we look like.
        if (connected) delay(1200)
        location = runCatching {
            LocationFetcher.fetch(throughProxy = connected && throughLocalProxy)
        }.getOrNull()
    }

    var pingMs by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(connected, serverAddress, serverPort) {
        val host = serverAddress?.takeIf { it.isNotBlank() }
        val port = serverPort?.takeIf { it > 0 }
        if (!connected || host == null || port == null) {
            pingMs = null
            return@LaunchedEffect
        }
        delay(800)
        pingMs = (runCatching { Pinger.ping(host, port) }.getOrNull() as? PingResult.Ok)?.ms
    }

    Slab(modifier, spacing = 0.dp) {
        SlabRow(
            title = t("hub_ip"),
            icon = Icons.Filled.Public,
            value = location?.ip?.takeIf { it.isNotBlank() && it != "—" } ?: "—",
            accent = c.info
        )
        SlabDivider()
        SlabRow(
            title = t("hub_location"),
            icon = Icons.Filled.Place,
            value = location?.country?.takeIf { it.isNotBlank() } ?: "—",
            accent = c.premium
        )
        SlabDivider()
        SlabRow(
            title = t("hub_ping"),
            subtitle = if (connected && onMeasureDelay != null) t("ping_tap_hint") else null,
            icon = Icons.Filled.NetworkCheck,
            value = when {
                delayRunning -> "…"
                measuredDelay != null -> measuredDelay
                pingMs != null -> localizeDigits("$pingMs", lang) + " " + t("unit_ms")
                else -> "—"
            },
            accent = c.good,
            enabled = connected && !delayRunning,
            onClick = onMeasureDelay
        )
        extra()
    }
}

/** One shared once-per-second clock, ticking only while something reads it. */
@Composable
private fun rememberSecondTick(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}

/**
 * Download and upload as two tiles. Each tile's icon/number colour and
 * background come from Personalization (Traffic Tiles), defaulting to the
 * theme's info and premium tones on the slab colour.
 */
@Composable
fun TrafficTiles(
    downValue: String,
    downTotal: String,
    upValue: String,
    upTotal: String,
    modifier: Modifier = Modifier,
    /** compact / normal / large, from Personalization > Home. */
    size: String = "normal"
) {
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
        TrafficTile(
            Icons.Filled.ArrowDownward, t("download"), downValue, downTotal,
            lookColor(LookElement.TILE_DOWN_ICON), lookColor(LookElement.TILE_DOWN_BG),
            Modifier.weight(1f), size
        )
        TrafficTile(
            Icons.Filled.ArrowUpward, t("upload"), upValue, upTotal,
            lookColor(LookElement.TILE_UP_ICON), lookColor(LookElement.TILE_UP_BG),
            Modifier.weight(1f), size
        )
    }
}

@Composable
private fun TrafficTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    total: String,
    ink: Color,
    bg: Color,
    modifier: Modifier,
    size: String = "normal"
) {
    val c = ghajarColors
    val radius = LocalGhajarLook.current.cardRadius.dp
    val pad = when (size) { "compact" -> 6.dp; "large" -> 16.dp; else -> 10.dp }
    val big = size == "large"
    Row(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .padding(horizontal = GhajarSpacing.md, vertical = pad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(ink.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textMuted, maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = ink,
                fontSize = if (big) 19.sp else if (size == "compact") 13.sp else 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (size != "compact") Text(
                total,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A heraldic shield: flat top with rounded corners, curving to a point. */
private val ShieldShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(w * 0.10f, h * 0.08f)
    quadraticTo(w * 0.50f, h * -0.02f, w * 0.90f, h * 0.08f)
    lineTo(w * 0.90f, h * 0.48f)
    cubicTo(w * 0.90f, h * 0.76f, w * 0.68f, h * 0.90f, w * 0.50f, h * 0.98f)
    cubicTo(w * 0.32f, h * 0.90f, w * 0.10f, h * 0.76f, w * 0.10f, h * 0.48f)
    close()
}

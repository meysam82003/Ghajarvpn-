package net.gozar.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The connect button *is* the home screen.
 *
 * Everything else on this screen is subordinate to it: the server it will use
 * sits directly beneath it, the numbers it produces sit below that. One
 * primary action, centred, impossible to miss - rather than a full-width bar
 * competing with a card above it and a globe below it.
 *
 * The orb carries the whole connection state in one object: the ring is the
 * state, the glyph is the action, and the label under the glyph says what a
 * tap will do. Tapping it connects, disconnects, or cancels an in-flight
 * auto-pick - the same three behaviours the old bar had, in one place.
 */
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
    modifier: Modifier = Modifier
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }

    val connectedish = state == Connection.CONNECTED || state == Connection.CONNECTING
    val working = picking || state == Connection.CONNECTING || state == Connection.DISCONNECTING
    val faulted = netOffline || tunnelDead || state == Connection.ERROR

    val tint by animateColorAsState(
        when {
            faulted -> c.error
            state == Connection.CONNECTED -> c.successGlow
            working -> c.highlight
            else -> c.primary
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

    val spin = rememberInfiniteTransition(label = "orb")
    val sweepState = spin.animateFloat(
        initialValue = -90f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "orbSweep"
    )
    val breathState = spin.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbBreath"
    )

    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        if (pressed && (enabled || picking)) 0.96f else 1f,
        tween(GhajarMotion.Fast, easing = FastOutSlowInEasing),
        label = "orbPress"
    )

    Box(
        modifier
            .size(232.dp)
            .graphicsLayer { scaleX = press; scaleY = press }
            .pointerInput(enabled, picking) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .clip(CircleShape)
            .clickable(enabled = enabled || picking, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // State ring + halo. Animation values are read inside the draw lambda
        // only, so an animating ring never recomposes this subtree.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.035f
            val inset = stroke * 2.2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            if (state == Connection.CONNECTED) {
                val breath = breathState.value
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.18f + 0.10f * breath), Color.Transparent)
                    ),
                    radius = size.minDimension * (0.44f + 0.04f * breath)
                )
            }

            // The button face.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        tint.copy(alpha = if (state == Connection.CONNECTED) 0.22f else 0.14f),
                        tint.copy(alpha = 0.04f)
                    )
                ),
                radius = size.minDimension / 2f - inset
            )

            drawArc(
                color = c.border,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (fill > 0f) {
                drawArc(
                    color = tint,
                    startAngle = if (working) sweepState.value else -90f,
                    sweepAngle = 360f * fill,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
        ) {
            Icon(
                when {
                    picking -> Icons.Filled.Close
                    state == Connection.CONNECTED -> Icons.Filled.PowerSettingsNew
                    state == Connection.CONNECTING -> Icons.Filled.Close
                    else -> Icons.Filled.Bolt
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(46.dp)
            )
            Text(
                when {
                    picking -> t("finding_fastest")
                    state == Connection.CONNECTED -> t("disconnect")
                    state == Connection.CONNECTING -> t("connecting_cancel")
                    state == Connection.DISCONNECTING -> t("hub_disconnecting")
                    else -> t("connect")
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled || picking) c.textPrimary else c.onDisabled,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
            if (!enabled && !picking && !connectedish) {
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

/**
 * The server the orb will use. Directly under the orb because that is the one
 * decision that changes what the button does, and one tap from the picker.
 */
@Composable
fun ServerPill(
    name: String?,
    subtitle: String?,
    state: Connection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val live = state == Connection.CONNECTED
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.pill))
            .background(c.card)
            .border(1.dp, if (live) c.primary.copy(alpha = 0.45f) else c.border, RoundedCornerShape(GhajarRadius.pill))
            .clickable { onClick() }
            .padding(horizontal = GhajarSpacing.lg, vertical = GhajarSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (live) c.successGlow else c.textMuted)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                name?.takeIf { it.isNotBlank() } ?: Strings.get(lang, "hub_no_server"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Filled.SwapHoriz,
            contentDescription = Strings.get(lang, "change_server"),
            tint = c.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * One live number. Label above, value below, an optional second line under it,
 * accent only on the glyph. Every tile on the home screen is this shape, so a
 * speed, a total, a ping and an IP all read the same way.
 */
@Composable
fun MetricTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    onClick: (() -> Unit)? = null
) {
    val c = ghajarColors
    Column(
        modifier
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(GhajarRadius.md))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = GhajarSpacing.md, vertical = GhajarSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!sub.isNullOrBlank()) {
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The state sentence, above the orb. The orb's own label says what a tap will
 * *do*; this says what the tunnel *is* - including the two faults that matter
 * (no device internet, and a tunnel that is up but carrying nothing).
 */
@Composable
fun StatusLine(state: Connection, picking: Boolean, netOffline: Boolean, tunnelDead: Boolean) {
    val c = ghajarColors
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    val (text, tone) = when {
        netOffline -> t("home_offline") to c.error
        picking -> t("finding_fastest") to c.highlight
        state == Connection.CONNECTED && tunnelDead -> t("conn_no_data") to c.error
        state == Connection.CONNECTED -> t("status_connected") to c.successGlow
        state == Connection.CONNECTING -> t("status_connecting") to c.highlight
        state == Connection.DISCONNECTING -> t("hub_disconnecting") to c.highlight
        state == Connection.ERROR -> t("status_error") to c.error
        else -> t("home_ready") to c.textSecondary
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(GhajarRadius.pill))
            .background(tone.copy(alpha = 0.12f))
            .border(1.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(GhajarRadius.pill))
            .padding(horizontal = GhajarSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(tone))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp)
        )
    }
}

/**
 * Measured facts about the tunnel: the public IP we present, where it looks
 * like, and the live ping to the active server. All three are real
 * measurements, taken once per settled state - not on every frame, and not
 * while the tunnel is still coming up.
 */
@Composable
fun ConnectionFacts(
    state: Connection,
    serverAddress: String?,
    serverPort: Int?,
    modifier: Modifier = Modifier
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }

    val connected = state == Connection.CONNECTED
    val busy = state == Connection.CONNECTING || state == Connection.DISCONNECTING

    var location by remember { mutableStateOf<IpLocation?>(null) }
    LaunchedEffect(connected) {
        if (busy) return@LaunchedEffect
        // Let a fresh tunnel's routes settle before asking who we look like.
        if (connected) delay(1200)
        location = runCatching { LocationFetcher.fetch(throughProxy = connected) }.getOrNull()
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

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
        MetricTile(
            icon = Icons.Filled.Public,
            label = t("hub_ip"),
            value = location?.ip?.takeIf { it.isNotBlank() && it != "—" } ?: "—",
            accent = c.primary,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            icon = Icons.Filled.Place,
            label = t("hub_location"),
            value = location?.country?.takeIf { it.isNotBlank() } ?: "—",
            accent = c.premium,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            icon = Icons.Filled.NetworkCheck,
            label = t("hub_ping"),
            value = pingMs?.let { localizeDigits("$it", lang) + " " + t("unit_ms") } ?: "—",
            accent = c.highlight,
            modifier = Modifier.weight(1f)
        )
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
        style = MaterialTheme.typography.labelLarge,
        color = c.highlight,
        fontSize = 15.sp
    )
}

/** One shared once-per-second clock, ticking only while something reads it. */
@Composable
private fun rememberSecondTick(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    return now
}

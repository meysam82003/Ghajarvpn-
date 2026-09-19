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
    onReconnect: (() -> Unit)? = null
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
        animationSpec = ghajarEndless(infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart)),
        label = "orbSweep"
    )
    val breathState = spin.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = ghajarEndless(infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse)),
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
            .size(236.dp)
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
            .combinedClickable(
                enabled = enabled || picking,
                onClick = onClick,
                onLongClick = onReconnect?.takeIf { state == Connection.CONNECTED }
            ),
        contentAlignment = Alignment.Center
    ) {
        // State ring, disc and halo. Animation values are read inside the draw
        // lambda only, so an animating ring never recomposes this subtree.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.028f
            val inset = stroke * 2.6f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            if (state == Connection.CONNECTED) {
                val breath = breathState.value
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.16f + 0.09f * breath), Color.Transparent)
                    ),
                    radius = size.minDimension * (0.45f + 0.04f * breath)
                )
            }

            // The disc: the skin's slab tone, lifted towards the state colour.
            drawCircle(
                color = c.secondaryCard,
                radius = size.minDimension / 2f - inset
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        tint.copy(alpha = if (state == Connection.CONNECTED) 0.20f else 0.10f),
                        Color.Transparent
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
                    style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            // The label lives inside the disc, so it is bounded by the square
            // that fits in the circle - no string can spill past the ring.
            Modifier.widthIn(max = 156.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
        ) {
            androidx.compose.material3.Icon(
                when {
                    picking -> Icons.Filled.Close
                    state == Connection.CONNECTED -> Icons.Filled.PowerSettingsNew
                    state == Connection.CONNECTING -> Icons.Filled.Close
                    else -> Icons.Filled.Bolt
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(44.dp)
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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

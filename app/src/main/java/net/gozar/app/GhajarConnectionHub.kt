package net.gozar.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The home screen's centrepiece, in place of the rotating globe.
 *
 * The globe cost a 56,000-iteration trigonometric build, a per-pixel mask
 * sample across a worker pool, and a permanently running animation that kept
 * the whole subtree recomposing. This draws two arcs and one text column, and
 * animates only while the tunnel is actually connecting or connected - so an
 * idle home screen costs nothing.
 *
 * Everything shown here is real: the ring reflects [state], the timer counts
 * the live session, and the address line is the measured public IP. Nothing is
 * decorative-only.
 */
@Composable
fun ConnectionHub(
    state: Connection,
    serverName: String?,
    serverAddress: String?,
    serverPort: Int?,
    sessionStartMs: Long?,
    modifier: Modifier = Modifier
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val t = stringsFn()

    val connected = state == Connection.CONNECTED
    val busy = state == Connection.CONNECTING || state == Connection.DISCONNECTING
    val failed = state == Connection.ERROR

    // Resolved here so the caller stays a plain layout. Both are keyed on the
    // settled state, so they run on a real transition - not on every frame,
    // and not while the tunnel is still coming up.
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

    val ringColor = when {
        connected -> c.successGlow
        busy -> c.highlight
        failed -> c.error
        else -> c.textMuted
    }

    // Only spins/pulses while something is actually happening.
    val transition = rememberInfiniteTransition(label = "hub")
    val sweepState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing)),
        label = "sweep"
    )
    val breathState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breath"
    )
    // Reading .value only inside the draw lambda keeps the animation from
    // resnapshotting this composable - and everything under it - every frame.
    val fillTarget = if (connected) 1f else if (busy) 0.35f else 0.08f
    val fill by animateFloatAsState(fillTarget, tween(GhajarMotion.Slow), label = "hubFill")

    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val stroke = size.minDimension * 0.045f
                val inset = stroke * 1.6f
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)

                // Halo: only visible when connected, and it breathes slowly.
                if (connected) {
                    val breath = breathState.value
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(ringColor.copy(alpha = 0.16f + 0.10f * breath), Color.Transparent)
                        ),
                        radius = size.minDimension * (0.46f + 0.03f * breath)
                    )
                }

                drawArc(
                    color = c.border,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = if (busy) sweepState.value else -90f,
                    sweepAngle = 360f * fill,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GhajarSpacing.xs)
            ) {
                Text(
                    when {
                        connected -> t("status_connected")
                        state == Connection.CONNECTING -> t("status_connecting")
                        state == Connection.DISCONNECTING -> t("hub_disconnecting")
                        failed -> t("status_error")
                        else -> t("status_disconnected")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (failed) c.error else c.textPrimary,
                    textAlign = TextAlign.Center
                )
                if (connected && sessionStartMs != null) {
                    SessionTimer(sessionStartMs, lang)
                } else {
                    Text(
                        serverName?.takeIf { it.isNotBlank() } ?: t("hub_no_server"),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
        ) {
            HubFact(
                label = t("hub_ip"),
                value = location?.ip?.takeIf { it.isNotBlank() && it != "—" } ?: "—",
                modifier = Modifier.weight(1f)
            )
            HubFact(
                label = t("hub_location"),
                value = location?.country?.takeIf { it.isNotBlank() } ?: "—",
                modifier = Modifier.weight(1f)
            )
            HubFact(
                label = t("hub_ping"),
                value = pingMs?.let { localizeDigits("$it", lang) + " " + t("unit_ms") } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Counts the live session once per second, and only while it is shown. */
@Composable
private fun SessionTimer(startMs: Long, lang: Lang) {
    var elapsed by remember(startMs) { mutableStateOf(0L) }
    LaunchedEffect(startMs) {
        while (true) {
            elapsed = ((System.currentTimeMillis() - startMs) / 1000).coerceAtLeast(0L)
            delay(1000)
        }
    }
    val h = elapsed / 3600
    val m = (elapsed % 3600) / 60
    val s = elapsed % 60
    Text(
        localizeDigits("%02d:%02d:%02d".format(h, m, s), lang),
        style = MaterialTheme.typography.labelLarge,
        color = ghajarColors.highlight,
        fontSize = 15.sp
    )
}

@Composable
private fun HubFact(label: String, value: String, modifier: Modifier = Modifier) {
    val c = ghajarColors
    Column(
        modifier
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(GhajarRadius.md))
            .padding(horizontal = GhajarSpacing.md, vertical = GhajarSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted,
            maxLines = 1
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

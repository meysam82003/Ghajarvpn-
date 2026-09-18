package net.gozar.app

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal val GhajarSoftShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Soft native surfaces, with readable foregrounds and no bitmap controls. */
@Composable
internal fun ghajarSoftSurface(shape: Shape, enabled: Boolean = true): Modifier {
    val colors = MaterialTheme.colorScheme
    return Modifier.shadow(if (enabled) 3.dp else 0.dp, shape, clip = false)
        .background(Brush.verticalGradient(listOf(colors.surface, colors.surfaceContainerLow)), shape)
}

@Composable
internal fun GhajarWordmark(modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.ghajar_wordmark),
        contentDescription = "قاجار VPN",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

/**
 * The intro: a short motion graphic, drawn rather than decoded.
 *
 * What this replaces: a full-screen poster picked at random from
 * thirty-three JPEGs totalling 8.7MB, cropped to fill the display. That cost
 * a large bitmap decode on the very first frame, and the app deliberately
 * waited 1100ms before it even began composing behind it - so the slowest
 * moment in the whole app was opening it.
 *
 * This draws instead: one Canvas, no bitmaps, no image decode, nothing to
 * load from disk. A single animation drives everything and its value is read
 * only inside the draw lambda, so the ring animating never recomposes
 * anything. The whole thing is 900ms and the app composes *behind* it from
 * the first frame, so by the time it fades the app is already interactive.
 *
 * Tapping skips it.
 */
@Composable
internal fun GhajarIntro(onDone: () -> Unit) {
    val c = ghajarColors
    val finish by rememberUpdatedState(onDone)

    // One driver, 0f -> 1f. Everything below is a function of it.
    val progress = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    val fade by animateFloatAsState(
        if (leaving) 0f else 1f,
        tween(220),
        label = "introFade"
    )

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        leaving = true
        delay(220)
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .graphicsLayer { alpha = fade }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { finish() },
        contentAlignment = Alignment.Center
    ) {
      // The mark under the animation, not instead of it: the rings, ring and
      // shield are unchanged above, and the wordmark fades and lifts in once
      // the shield is in place.
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Canvas(Modifier.size(220.dp)) {
            val p = progress.value
            val mid = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f

            // Three rings breathing outward, each a third of a cycle apart, so
            // there is always one arriving as another leaves.
            for (i in 0 until 3) {
                val phase = ((p * 1.6f) + i / 3f) % 1f
                val alpha = (1f - phase) * 0.28f * p
                if (alpha > 0.01f) {
                    drawCircle(
                        color = c.primary.copy(alpha = alpha),
                        radius = r * (0.30f + 0.70f * phase),
                        center = mid,
                        style = Stroke(width = r * 0.035f)
                    )
                }
            }

            // The brand ring drawing itself, then holding.
            val sweep = (p / 0.72f).coerceAtMost(1f)
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(c.primary, c.highlight, c.premium, c.primary),
                    center = mid
                ),
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = Offset(r * 0.30f, r * 0.30f),
                size = Size(r * 1.40f, r * 1.40f),
                style = Stroke(width = r * 0.075f, cap = StrokeCap.Round)
            )

            // A shield mark that scales up inside the ring once it has closed.
            val markIn = ((p - 0.42f) / 0.42f).coerceIn(0f, 1f)
            if (markIn > 0f) {
                val s = r * 0.40f * markIn
                val path = Path().apply {
                    moveTo(mid.x, mid.y - s)
                    lineTo(mid.x + s * 0.80f, mid.y - s * 0.46f)
                    lineTo(mid.x + s * 0.80f, mid.y + s * 0.26f)
                    // Straight edges to the point rather than a Bezier: the
                    // quadratic helpers have been renamed across Compose
                    // versions and this shape does not need the curve.
                    lineTo(mid.x + s * 0.46f, mid.y + s * 0.76f)
                    lineTo(mid.x, mid.y + s)
                    lineTo(mid.x - s * 0.46f, mid.y + s * 0.76f)
                    lineTo(mid.x - s * 0.80f, mid.y + s * 0.26f)
                    lineTo(mid.x - s * 0.80f, mid.y - s * 0.46f)
                    close()
                }
                drawPath(
                    path,
                    brush = Brush.verticalGradient(
                        listOf(c.highlight.copy(alpha = markIn), c.primary.copy(alpha = markIn)),
                        startY = mid.y - s,
                        endY = mid.y + s
                    )
                )
                // The tick, drawn on once the shield is fully in.
                val tick = ((p - 0.70f) / 0.26f).coerceIn(0f, 1f)
                if (tick > 0f) {
                    val a = Offset(mid.x - s * 0.34f, mid.y + s * 0.02f)
                    val b = Offset(mid.x - s * 0.06f, mid.y + s * 0.30f)
                    val d = Offset(mid.x + s * 0.40f, mid.y - s * 0.30f)
                    val firstLeg = (tick / 0.45f).coerceAtMost(1f)
                    drawLine(
                        color = c.onPrimary,
                        start = a,
                        end = Offset(a.x + (b.x - a.x) * firstLeg, a.y + (b.y - a.y) * firstLeg),
                        strokeWidth = s * 0.15f,
                        cap = StrokeCap.Round
                    )
                    if (tick > 0.45f) {
                        val secondLeg = ((tick - 0.45f) / 0.55f).coerceAtMost(1f)
                        drawLine(
                            color = c.onPrimary,
                            start = b,
                            end = Offset(b.x + (d.x - b.x) * secondLeg, b.y + (d.y - b.y) * secondLeg),
                            strokeWidth = s * 0.15f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        // Read only inside graphicsLayer's lambda, like every other value the
        // intro animates: this places the wordmark without ever recomposing it.
        Image(
            painter = painterResource(R.drawable.ghajar_wordmark),
            contentDescription = BrandConfig.APP_NAME_FA,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(200.dp)
                .graphicsLayer {
                    val markIn = ((progress.value - 0.52f) / 0.40f).coerceIn(0f, 1f)
                    alpha = markIn
                    translationY = (1f - markIn) * 14.dp.toPx()
                }
        )
      }
    }
}

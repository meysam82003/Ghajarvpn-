package net.gozar.app

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Android TV support: how the app knows it is on a television, and the focus
 * treatment that makes every control usable with a D-pad remote.
 *
 * Nothing here changes a phone or a tablet. [LocalIsTv] is false there, every
 * modifier below returns an empty [Modifier], and [GhajarTheme] only installs
 * the TV indication when the device is a television.
 */

/** True when the app is running on a television (Android TV / Google TV). */
val LocalIsTv = staticCompositionLocalOf { false }

object GhajarTv {
    @Volatile
    private var cached: Boolean? = null

    /**
     * A television, by the platform's own answer first (the UI mode the TV
     * launcher runs in), then by the leanback / television features, and as a
     * last resort a device with no touch screen that navigates with a D-pad -
     * the no-name Android boxes that declare neither.
     */
    fun isTv(context: Context): Boolean = cached ?: detect(context).also { cached = it }

    @Suppress("DEPRECATION")
    private fun detect(context: Context): Boolean = runCatching {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return@runCatching true
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        ) return@runCatching true
        !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
            context.resources.configuration.navigation == Configuration.NAVIGATION_DPAD
    }.getOrDefault(false)
}

/**
 * Installs the TV focus treatment for everything below it:
 *
 *  - every `Modifier.clickable` that uses the default indication draws a
 *    bright ring in the theme's highlight tone while it has focus (and a wash
 *    while OK is held down), and
 *  - Material's own components (buttons, icon buttons, menu items, cards),
 *    which always draw a ripple, get a much stronger focus state layer than
 *    the 10% wash that is invisible from a sofa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TvFocusTheme(palette: GhajarPalette, content: @Composable () -> Unit) {
    val ring = palette.highlight
    val indication = remember(ring) { TvFocusIndication(ring) }
    val stateColor = palette.textPrimary
    val ripple = remember(stateColor) {
        RippleConfiguration(
            color = stateColor,
            rippleAlpha = RippleAlpha(
                draggedAlpha = 0.16f,
                focusedAlpha = 0.30f,
                hoveredAlpha = 0.12f,
                pressedAlpha = 0.24f
            )
        )
    }
    CompositionLocalProvider(
        LocalIndication provides indication,
        LocalRippleConfiguration provides ripple,
        content = content
    )
}

/**
 * The default indication on TV: a focus ring plus a press wash. It replaces
 * the ripple rather than wrapping it - a node that draws and also delegates
 * to another drawing node only gets its own draw called.
 */
private class TvFocusIndication(private val ring: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TvFocusIndicationNode(interactionSource, ring)

    override fun equals(other: Any?): Boolean = other is TvFocusIndication && other.ring == ring

    override fun hashCode(): Int = ring.hashCode()
}

private class TvFocusIndicationNode(
    private val interactionSource: InteractionSource,
    private val ring: Color
) : Modifier.Node(), DrawModifierNode {
    private var focused = false
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            var focusCount = 0
            var pressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount = (focusCount - 1).coerceAtLeast(0)
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release -> pressCount = (pressCount - 1).coerceAtLeast(0)
                    is PressInteraction.Cancel -> pressCount = (pressCount - 1).coerceAtLeast(0)
                }
                val nowFocused = focusCount > 0
                val nowPressed = pressCount > 0
                if (nowFocused != focused || nowPressed != pressed) {
                    focused = nowFocused
                    pressed = nowPressed
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused && !pressed) return
        val w = 3.dp.toPx()
        if (size.width <= w * 2 || size.height <= w * 2) return
        val r = minOf(size.minDimension / 2f, 16.dp.toPx())
        drawRoundRect(
            color = ring.copy(alpha = if (pressed) 0.30f else 0.14f),
            cornerRadius = CornerRadius(r)
        )
        if (focused) {
            drawRoundRect(
                color = ring,
                topLeft = Offset(w / 2f, w / 2f),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius((r - w / 2f).coerceAtLeast(0f)),
                style = Stroke(w)
            )
        }
    }
}

/**
 * The focus glow for the shared components: a slight scale and a soft halo in
 * the highlight tone around the element while it is focused, and with [ring]
 * a crisp outline as well (for controls whose own indication draws nothing,
 * such as Material buttons or clickables with `indication = null`).
 *
 * Put it in front of the element's clip/background/clickable so the halo is
 * drawn outside the shape. [radius] is the shape's corner; anything larger
 * than half the element is clamped, so a pill or a circle can pass a huge one.
 * A no-op on phones and tablets.
 */
fun Modifier.tvFocusGlow(
    radius: Dp = GhajarRadius.md,
    ring: Boolean = false,
    scaleTo: Float = 1.03f
): Modifier = composed {
    if (!LocalIsTv.current) return@composed Modifier
    val c = ghajarColors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) scaleTo else 1f,
        tween(GhajarMotion.Fast),
        label = "tvFocusScale"
    )
    Modifier
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                val o = 3.dp.toPx()
                val r = minOf(radius.toPx(), size.minDimension / 2f)
                // Soft halo, outside the element.
                drawRoundRect(
                    color = c.highlight.copy(alpha = 0.28f),
                    topLeft = Offset(-o * 2f, -o * 2f),
                    size = Size(size.width + o * 4f, size.height + o * 4f),
                    cornerRadius = CornerRadius(r + o * 2f),
                    style = Stroke(o * 2f)
                )
                if (ring) {
                    drawRoundRect(
                        color = c.highlight,
                        topLeft = Offset(-o, -o),
                        size = Size(size.width + o * 2f, size.height + o * 2f),
                        cornerRadius = CornerRadius(r + o),
                        style = Stroke(2.5.dp.toPx())
                    )
                }
            }
        }
}

/**
 * Makes an element that only reacts to touch gestures (a `pointerInput` tap)
 * reachable and pressable with the remote. A no-op on phones and tablets,
 * where the gesture keeps working exactly as before.
 */
fun Modifier.tvClickable(radius: Dp = GhajarRadius.md, onClick: () -> Unit): Modifier = composed {
    if (!LocalIsTv.current) return@composed Modifier
    Modifier
        .tvFocusGlow(radius, ring = true, scaleTo = 1f)
        .clickable { onClick() }
}

/**
 * Left/right on the D-pad adjusts a value that is otherwise dragged with a
 * finger (a colour bar, a chart cursor). Up/down are left alone so focus can
 * still leave the control. A no-op on phones and tablets.
 */
fun Modifier.tvDpadAdjust(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    radius: Dp = GhajarRadius.md
): Modifier = composed {
    if (!LocalIsTv.current) return@composed Modifier
    Modifier
        .tvFocusGlow(radius, ring = true, scaleTo = 1f)
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onLeft(); true }
                Key.DirectionRight -> { onRight(); true }
                else -> false
            }
        }
        .focusable()
}

/**
 * On TV a single-line field whose caller did not choose an IME action gets
 * "Next", so the keyboard's action key moves focus on to the next control
 * (Compose's default handling of Next) instead of only closing the keyboard.
 * Unchanged on phones and tablets.
 */
@Composable
internal fun tvImeOptions(options: KeyboardOptions, singleLine: Boolean): KeyboardOptions {
    if (!LocalIsTv.current || !singleLine) return options
    val unset = options.imeAction == ImeAction.Default || options.imeAction == ImeAction.Unspecified
    return if (unset) options.copy(imeAction = ImeAction.Next) else options
}

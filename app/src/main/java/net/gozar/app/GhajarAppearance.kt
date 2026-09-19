package net.gozar.app

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Appearance choices that are not the palette: motion, the wallpaper accent,
 * and how dense the server list is.
 *
 * Each of these is here because it changes something a user can feel, not
 * because a settings screen looked sparse.
 */

/**
 * Whether the app should stop animating things that never stop on their own.
 *
 * This app has nineteen infinite transitions - pulsing dots, a travelling arc
 * on the map, a breathing connect button. Every one of them redraws forever
 * while its screen is visible. For most people that is the app feeling alive;
 * for someone with vestibular sensitivity it is unusable.
 *
 * What this switch does and does not do, precisely: it stops the visible
 * movement. It does not stop Compose from invalidating those frames - an
 * infinite transition keeps ticking whether or not its value changes - so it
 * is an accessibility setting, not a battery setting, and it is not described
 * as one in the UI.
 *
 * Read through [ghajarEndless] and [ghajarMotionSpec] rather than directly,
 * so switching it on cannot be half-applied: a site that forgets is a site
 * that keeps moving after the user asked it to stop.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * A finite animation, honoured or collapsed.
 *
 * [snap] rather than a shorter duration: reduced motion means the state
 * change still happens, just without the travel. A faster animation is still
 * animation.
 */
@Composable
fun <T> ghajarMotionSpec(spec: AnimationSpec<T>): AnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else spec

/**
 * An endless animation, or a specification that never advances.
 *
 * Returns a repeatable that holds its start value forever instead of null, so
 * a call site does not have to branch - the pulsing dot simply stops pulsing
 * and stays drawn, which is what "reduce motion" should look like rather than
 * the element disappearing.
 */
@Composable
fun ghajarInfinite(
    durationMillis: Int,
    repeatMode: RepeatMode = RepeatMode.Reverse
): InfiniteRepeatableSpec<Float> =
    if (LocalReduceMotion.current) {
        // A very long hold reads as "stopped" without needing a second code
        // path, and costs one interpolation per frame at most.
        infiniteRepeatable(tween(Int.MAX_VALUE), RepeatMode.Restart)
    } else {
        infiniteRepeatable(tween(durationMillis), repeatMode)
    }

/**
 * An existing endless animation, honoured or frozen.
 *
 * Takes the spec the call site already wrote so easing, repeat mode and
 * duration are not retyped at seventeen call sites - wrapping is a one-token
 * change there, which is the only reason every one of them actually got
 * wrapped.
 *
 * Frozen means held at the initial value forever: [tween] over the longest
 * duration the type allows. The element stays drawn and stops moving, which
 * is what reduced motion should look like; making it vanish instead would be
 * removing information, not motion.
 */
@Composable
fun <T> ghajarEndless(spec: InfiniteRepeatableSpec<T>): InfiniteRepeatableSpec<T> =
    if (LocalReduceMotion.current) {
        infiniteRepeatable(tween(Int.MAX_VALUE), RepeatMode.Restart)
    } else {
        spec
    }

/**
 * An endless animation that stops existing when it has nothing to say.
 *
 * This is the difference between an animation that is invisible and one that
 * is not running, and on this app it is most of why the UI felt like a
 * twenty-four frame video.
 *
 * `rememberInfiniteTransition` keeps a frame callback scheduled for as long as
 * it is in the composition, whatever its value is doing and whether or not
 * anything reads it. The connect button's two transitions were created on
 * every composition of the home screen, so the app held the frame loop open
 * from launch: idle, disconnected, nothing moving, still waking the main
 * thread every 16ms to interpolate two numbers nobody was drawing. Any scroll
 * or gesture then had to share the frame with that, which is exactly what
 * "the screen is 30Hz when it could be 144" feels like.
 *
 * An [Animatable] driven from a gated [LaunchedEffect] has the property the
 * transition does not: when [active] is false the effect's body returns, no
 * coroutine is suspended on a frame, and the frame loop goes back to sleep.
 * When it turns true the animation starts from [from] with no jump.
 *
 * Reduced motion holds the value at [from] by the same mechanism - nothing
 * scheduled at all - which is stricter than the frozen-spec path above.
 */
@Composable
fun ghajarPulse(
    active: Boolean,
    durationMillis: Int,
    from: Float = 0f,
    to: Float = 1f,
    reverse: Boolean = true,
    easing: Easing = LinearEasing
): State<Float> {
    val reduce = LocalReduceMotion.current
    val value = remember { Animatable(from) }
    LaunchedEffect(active, reduce, durationMillis, from, to, reverse, easing) {
        if (!active || reduce) {
            value.snapTo(from)
            return@LaunchedEffect
        }
        val spec = tween<Float>(durationMillis, easing = easing)
        while (true) {
            value.snapTo(from)
            value.animateTo(to, spec)
            if (reverse) value.animateTo(from, spec)
        }
    }
    return value.asState()
}

/**
 * The accent pulled from the system wallpaper, or null.
 *
 * Android calls this Material You. It only exists from Android 12, and it is
 * deliberately applied as *one* colour rather than a whole scheme: this app's
 * palettes carry meaning beyond decoration - the warning amber, the error
 * red, the "connected" green all have to stay what they are - and handing the
 * whole surface to the wallpaper would make a red error message green on
 * somebody's phone.
 */
fun dynamicAccent(context: Context): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return runCatching {
        // system_accent1_300 is the mid tone of the wallpaper's primary ramp:
        // bright enough to read on a dark surface, dark enough on a light one.
        Color(context.getColor(android.R.color.system_accent1_300))
    }.getOrNull()
}

/** True when this device can offer a wallpaper accent at all. */
val dynamicAccentSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * How tall a server row is.
 *
 * ONE is the original two-line row. TWO is a single line - the endpoint is
 * dropped and the padding halved - which fits roughly twice as many servers
 * on screen. That matters for someone holding thirty of them with no idea
 * which works today: the point of the list is comparing them, and comparing
 * needs them visible at once.
 *
 * The names are ONE and TWO because the first design was one column versus
 * two. Two columns lost: the picker maps a drag's y position to a row id for
 * paint-selection, and a second column makes it select the wrong servers
 * without saying so. The names stayed to keep stored values valid.
 */
enum class ListDensity { ONE, TWO }

val LocalListDensity = staticCompositionLocalOf { ListDensity.ONE }

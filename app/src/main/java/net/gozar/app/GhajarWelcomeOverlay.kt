package net.gozar.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal object GhajarWelcomeOverlayRules {
    const val AUTOCLOSE_MS = 3_200L
    const val FRAME_STROKE_DP = 0.5f
    const val FRAME_GAP_DP = 1f

    fun autocloseMs(): Long = AUTOCLOSE_MS

    fun resolvePoster(name: String): GhajarWelcomeAssets.Poster =
        GhajarWelcomeAssets.posters.firstOrNull { it.name == name }
            ?: GhajarWelcomeAssets.posters.first()

    fun reservesPoster(context: android.content.Context): String =
        GhajarWelcomeAssets.reserve(context)
}

@Composable
fun GhajarWelcomeOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    var posterName by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        posterName = GhajarWelcomeOverlayRules.reservesPoster(context)
        visible = true
        delay(GhajarWelcomeOverlayRules.autocloseMs())
        visible = false
        delay(280)
        onDone()
    }

    val chosen = posterName?.let { GhajarWelcomeOverlayRules.resolvePoster(it) }
    val fade by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(280),
        label = "welcomeFade"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04070C))
            .alpha(fade),
        contentAlignment = Alignment.Center
    ) {
        if (chosen != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f)
                    .padding(GhajarWelcomeOverlayRules.FRAME_GAP_DP.dp)
                    .border(
                        GhajarWelcomeOverlayRules.FRAME_STROKE_DP.dp,
                        Color(0xFFC79E48).copy(alpha = 0.45f),
                        RoundedCornerShape(2.dp)
                    )
            ) {
                Image(
                    painter = painterResource(chosen.resourceId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visible) {
                    if (!visible) return@pointerInput
                    detectTapGestures { onDone() }
                }
        )
    }
}

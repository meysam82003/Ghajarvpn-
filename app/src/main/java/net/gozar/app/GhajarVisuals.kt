package net.gozar.app

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag

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

/** One locally bundled picture per launch: the screen stays fully covered by
 * a crop backdrop of the same artwork while the original poster keeps its
 * aspect ratio with ContentScale.Fit. No enter button and no panel — the
 * overlay auto-closes and dismisses on any tap. */
@Composable
internal fun GhajarWelcomeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("ghajar_welcome", Context.MODE_PRIVATE)
    }
    GhajarWelcomeOverlay(
        onDone = {
            prefs.edit().putBoolean("soft_intro_seen", true).apply()
            onDone()
        }
    )
}

package net.gozar.app

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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

/** One locally bundled picture per launch, with no carousel or network access. */
@Composable
internal fun GhajarWelcomeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ghajar_welcome", Context.MODE_PRIVATE) }
    val returning = remember { prefs.getBoolean("soft_intro_seen", false) }
    // Complete the tiny preference lookup during the first composition. Deferring
    // this selection to an IO effect produced a blank frame on cold starts.
    val selectedName = rememberSaveable { GhajarWelcomeAssets.reserve(context) }
    var closed by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onDone)
    val selected = GhajarWelcomeAssets.posters.firstOrNull { it.name == selectedName }
    fun close() {
        if (closed) return
        closed = true
        prefs.edit().putBoolean("soft_intro_seen", true).apply()
        finish()
    }
    // Auto-close only after the poster is actually composed (slow emulators must not lose the welcome).
    LaunchedEffect(returning, selected) {
        if (returning && selected != null) { delay(1800); close() }
    }
    val backdrop = if (selected?.dark != false) Color(0xFF061226) else Color(0xFFEAF1FB)
    Box(Modifier.fillMaxSize().background(backdrop).clickable(onClick = ::close)) {
        selected?.let { poster ->
            // The cropped backdrop fills unusual aspect ratios while the fitted
            // foreground preserves every part of the complete 9:16 artwork.
            Image(painterResource(poster.resourceId), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .42f })
            Image(painterResource(poster.resourceId),
                contentDescription = "تصویر خوش‌آمدگویی قاجار VPN",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().testTag("ghajar_welcome_poster")
                    .graphicsLayer { alpha = 1f })
        }
        if (!returning) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = Color(0xCC061226), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("وضعیت واقعی اتصال در صفحهٔ خانه نمایش داده می‌شود",
                            style = MaterialTheme.typography.labelSmall, color = Color.White,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 7.dp))
                        Button(onClick = ::close, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(18.dp)) { Text("ورود به قاجار VPN") }
                    }
                }
            }
        }
    }
}

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

/** One locally bundled picture per launch, with no carousel or network access. */
@Composable
internal fun GhajarWelcomeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ghajar_welcome", Context.MODE_PRIVATE) }
    // Complete the tiny preference lookup during the first composition. Deferring
    // this selection to an IO effect produced a blank frame on cold starts.
    val selectedName = rememberSaveable { GhajarWelcomeAssets.reserve(context) }
    var closed by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onDone)
    val selected = remember(selectedName) {
        GhajarWelcomeAssets.posters.firstOrNull { it.name == selectedName }
            ?: GhajarWelcomeAssets.posters.firstOrNull()
    }
    val content = remember(selectedName) { GhajarWelcomeAssets.contentFor(selectedName) }
    fun close() {
        if (closed) return
        closed = true
        prefs.edit().putBoolean("soft_intro_seen", true).apply()
        finish()
    }
    val backdrop = if (selected?.dark != false) Color(0xFF061226) else Color(0xFFEAF1FB)
    // Tag the stable full-screen welcome root, not the Image semantics node. On Android 14
    // the image semantics can be merged while the artwork is decoding, which made CI flaky.
    Box(Modifier.fillMaxSize().background(backdrop).testTag("ghajar_welcome_poster")) {
        selected?.let { poster ->
            Image(painterResource(poster.resourceId),
                contentDescription = "${content.title}؛ تصویر خوش‌آمدگویی قاجار VPN",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Transparent, Color.Transparent, Color(0xB3061226), Color(0xF2061226)
        ))))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Color(0xF20B211C), shape = RoundedCornerShape(26.dp),
                tonalElevation = 8.dp, shadowElevation = 10.dp, modifier = Modifier.testTag("ghajar_welcome_panel")) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(content.title, style = MaterialTheme.typography.titleLarge, color = Color(0xFFD6B45F),
                        textAlign = TextAlign.Center, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
                    Text(content.body, style = MaterialTheme.typography.bodyMedium, color = Color.White,
                        textAlign = TextAlign.Center)
                    Button(onClick = ::close, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        .testTag("ghajar_welcome_enter"), shape = RoundedCornerShape(18.dp)) {
                        Text("ورود به برنامه")
                    }
                }
            }
        }
    }
}

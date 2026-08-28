package net.gozar.app

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var closed by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onDone)
    val reveal by animateFloatAsState(if (entered) 1f else 0f, tween(400), label = "welcome-reveal")
    val selected = GhajarWelcomeAssets.posters.firstOrNull { it.name == selectedName }
    fun close() {
        if (closed) return
        closed = true
        prefs.edit().putBoolean("soft_intro_seen", true).apply()
        finish()
    }
    LaunchedEffect(Unit) {
        if (selectedName == null) selectedName = withContext(Dispatchers.IO) {
            GhajarWelcomeAssets.reserve(context)
        }
    }
    LaunchedEffect(selectedName) {
        if (selectedName != null) {
            entered = true
            if (returning) { delay(1800); close() }
        }
    }
    val backdrop = if (selected?.dark != false) Color(0xFF061226) else Color(0xFFEAF1FB)
    Column(Modifier.fillMaxSize().background(backdrop).safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (selected != null) Image(painterResource(selected.resourceId),
            contentDescription = "تصویر خوش‌آمدگویی قاجار VPN",
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("ghajar_welcome_poster")
                .clickable(onClick = ::close).graphicsLayer { alpha = reveal })
        else Spacer(Modifier.weight(1f))
        if (!returning) Surface(color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("تصویر معرفی • وضعیت واقعی اتصال در صفحهٔ خانه نمایش داده می‌شود",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                Button(onClick = ::close, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(20.dp), enabled = selected != null) {
                    Text("ورود به قاجار VPN")
                }
            }
        }
    }
}

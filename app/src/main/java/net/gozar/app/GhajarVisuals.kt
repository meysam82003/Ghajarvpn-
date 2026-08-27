package net.gozar.app

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
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

/** Posters are intro illustrations, never a substitute for live VPN state. */
@Composable
internal fun GhajarWelcomeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ghajar_welcome", Context.MODE_PRIVATE) }
    val returning = remember { prefs.getBoolean("soft_intro_seen", false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Stable ordering also prevents a repeated image when the theme changes between launches.
    val posters = remember {
        listOf(R.drawable.ghajar_welcome_world, R.drawable.ghajar_welcome_connection,
            R.drawable.ghajar_welcome_locations, R.drawable.ghajar_welcome_royal)
    }
    val selectedPoster = remember { GhajarCommerceRules.nextPoster(prefs.getInt("last_poster", -1), posters.size) }
    val pager = rememberPagerState(initialPage = if (returning) selectedPoster else if (dark) 3 else 0, pageCount = { posters.size })
    val finish by rememberUpdatedState(onDone)
    var entered by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(if (entered) 1f else 0f, tween(450), label = "welcome-reveal")
    fun close() {
        prefs.edit().putBoolean("soft_intro_seen", true)
            .putInt("last_poster", if (returning) selectedPoster else pager.currentPage).apply()
        finish()
    }
    LaunchedEffect(Unit) {
        entered = true
        if (returning) {
            prefs.edit().putInt("last_poster", selectedPoster).apply()
            delay(1500)
            close()
        }
    }
    val posterDark = posters[pager.currentPage] == R.drawable.ghajar_welcome_royal
    val backdrop = if (posterDark) Color(0xFF061226) else Color(0xFFEAF1FB)
    Column(
        Modifier.fillMaxSize().background(backdrop).safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (returning) {
            Image(painterResource(posters[selectedPoster]), contentDescription = "خوش آمدی به قاجار VPN",
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).fillMaxWidth().clickable(onClick = ::close).graphicsLayer { alpha = reveal })
        } else HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 0
        ) { page ->
            Image(
                painterResource(posters[page]),
                contentDescription = "تصویر معرفی قاجار VPN، صفحه ${page + 1}",
                // FIT is deliberate: hats, feet, captions and all four edges stay visible.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp).graphicsLayer {
                    alpha = reveal
                    scaleX = 0.985f + reveal * 0.015f
                    scaleY = scaleX
                }
            )
        }
        if (!returning) Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { contentDescription = "صفحه ${pager.currentPage + 1} از ${posters.size}" }) {
                    posters.indices.forEach { index ->
                        Box(Modifier.size(if (index == pager.currentPage) 8.dp else 6.dp)
                            .background(if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    }
                }
                Text("تصاویر معرفی • وضعیت واقعی اتصال در صفحهٔ خانه نمایش داده می‌شود", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp))
                Button(onClick = ::close, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(20.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)) {
                    Text("ورود به قاجار VPN")
                }
            }
        }
    }
}

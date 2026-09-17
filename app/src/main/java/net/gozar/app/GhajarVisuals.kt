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

/** Posters are intro illustrations, never a substitute for live VPN state. */
@Composable
internal fun GhajarWelcomeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ghajar_welcome", Context.MODE_PRIVATE) }
    val returning = remember { prefs.getBoolean("soft_intro_seen", false) }

    // The full 3.0.4 poster set; only ONE image is shown per launch, randomly.
    val posters = remember {
        listOf(
            R.drawable.ghajar_welcome_world,
            R.drawable.ghajar_welcome_connection,
            R.drawable.ghajar_welcome_locations,
            R.drawable.ghajar_welcome_royal,
            R.drawable.ghajar_welcome_queen_phone,
            R.drawable.ghajar_welcome_king_night,
            R.drawable.ghajar_welcome_king_light,
            R.drawable.ghajar_welcome_queen_shield,
            R.drawable.ghajar_welcome_queen_night,
            R.drawable.ghajar_welcome_king_world,
            R.drawable.ghajar_welcome_queen_light,
            R.drawable.ghajar_welcome_extra_01,
            R.drawable.ghajar_welcome_extra_02,
            R.drawable.ghajar_welcome_extra_03,
            R.drawable.ghajar_welcome_extra_04,
            R.drawable.ghajar_welcome_extra_05,
            R.drawable.ghajar_welcome_extra_06,
            R.drawable.ghajar_welcome_extra_07,
            R.drawable.ghajar_welcome_extra_08,
            R.drawable.ghajar_welcome_extra_09,
            R.drawable.ghajar_welcome_extra_10,
            R.drawable.ghajar_welcome_extra_11,
            R.drawable.ghajar_welcome_extra_12,
            R.drawable.ghajar_welcome_extra_13,
            R.drawable.ghajar_welcome_extra_14,
            R.drawable.ghajar_welcome_extra_15,
            R.drawable.ghajar_welcome_extra_16,
            R.drawable.ghajar_welcome_extra_17,
            R.drawable.ghajar_welcome_extra_18,
            R.drawable.ghajar_welcome_extra_19,
            R.drawable.ghajar_welcome_extra_20,
            R.drawable.ghajar_welcome_extra_21,
            R.drawable.ghajar_welcome_extra_22
        )
    }
    // One random poster per launch; the previous launch's poster is not repeated.
    val selectedPoster = remember {
        GhajarCommerceRules.randomPoster(prefs.getInt("last_poster", -1), posters.size, kotlin.random.Random.Default)
    }
    val selectedTip = remember {
        GhajarCommerceRules.randomWelcomeTip(prefs.getInt("last_tip", -1), kotlin.random.Random.Default)
    }
    val finish by rememberUpdatedState(onDone)
    var entered by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(if (entered) 1f else 0f, tween(450), label = "welcome-reveal")
    LaunchedEffect(Unit) {
        // Persist immediately so a killed process still never repeats the poster/tip.
        prefs.edit()
            .putInt("last_poster", selectedPoster)
            .putInt("last_tip", GhajarCommerceRules.welcomeTipIndex(selectedTip))
            .putBoolean("soft_intro_seen", true)
            .apply()
        entered = true
        if (returning) {
            // Existing auto-close behavior for users who have seen the intro once.
            delay(1500)
            finish()
        }
    }
    // The navy info card is part of the brand and must always frame the poster.
    val backdrop = Color(0xFF061226)
    Column(
        Modifier.fillMaxSize().background(backdrop).safeDrawingPadding().clickable(onClick = { finish() }),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(posters[selectedPoster]), contentDescription = "خوش آمدی به قاجار VPN",
            contentScale = ContentScale.Crop,
            modifier = Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = reveal })
        Surface(color = Color(0xFF0B1F3A), shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💡 نکتهٔ قاجار", style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD6B45F), fontWeight = FontWeight.Bold)
                Text(selectedTip, style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF2F6FC), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

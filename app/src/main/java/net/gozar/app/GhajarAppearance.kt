package net.gozar.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal object GhajarColorRules {
    fun normalize(value: String): String? = value.trim().removePrefix("#")
        .takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.uppercase(Locale.ROOT)?.let { "#$it" }
}

internal class GhajarAppearance private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("ghajar_appearance", Context.MODE_PRIVATE)
    private val _accent = MutableStateFlow(prefs.getString("accent", null)?.let(GhajarColorRules::normalize))
    val accent = _accent.asStateFlow()
    fun setAccent(value: String?) {
        val normalized = value?.let(GhajarColorRules::normalize)
        if (value != null && normalized == null) return
        prefs.edit().putString("accent", normalized).apply()
        _accent.value = normalized
    }
    companion object {
        @Volatile private var instance: GhajarAppearance? = null
        fun get(context: Context): GhajarAppearance = instance ?: synchronized(this) {
            instance ?: GhajarAppearance(context.applicationContext).also { instance = it }
        }
    }
}

internal fun ghajarContrast(a: Color, b: Color): Float {
    val x = a.luminance(); val y = b.luminance()
    return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
}

private fun legibleAccent(color: Color, background: Color): Color {
    val target = if (background.luminance() < .5f) Color.White else Color.Black
    for (step in 0..100) {
        val candidate = lerp(color, target, step / 100f)
        if (ghajarContrast(candidate, background) >= 4.5f) return candidate
    }
    return target
}

internal fun ghajarColorScheme(base: ColorScheme, hex: String?): ColorScheme {
    val normalized = hex?.let(GhajarColorRules::normalize) ?: return base
    val selected = Color(android.graphics.Color.parseColor(normalized))
    val accent = legibleAccent(selected, base.background)
    val onAccent = if (ghajarContrast(accent, Color.White) >= ghajarContrast(accent, Color.Black)) Color.White else Color.Black
    val dark = base.background.luminance() < .5f
    val container = lerp(selected, if (dark) Color.Black else Color.White, if (dark) .72f else .86f)
    val tertiary = lerp(accent, if (dark) Color.White else Color.Black, .18f)
    val background = lerp(base.background, selected, if (dark) .085f else .035f)
    val surface = lerp(base.surface, selected, if (dark) .070f else .025f)
    val surfaceVariant = lerp(base.surfaceVariant, selected, if (dark) .14f else .075f)
    val surfaceLow = lerp(base.surfaceContainerLow, selected, if (dark) .075f else .030f)
    val surfaceMid = lerp(base.surfaceContainer, selected, if (dark) .095f else .045f)
    val surfaceHigh = lerp(base.surfaceContainerHigh, selected, if (dark) .12f else .060f)
    val outline = lerp(base.outline, selected, if (dark) .18f else .13f)
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = legibleAccent(selected, container),
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = legibleAccent(selected, container),
        tertiary = tertiary,
        onTertiary = if (ghajarContrast(tertiary, Color.White) >= ghajarContrast(tertiary, Color.Black)) Color.White else Color.Black,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = surfaceMid,
        surfaceContainerHigh = surfaceHigh,
        outline = outline,
        outlineVariant = lerp(base.outlineVariant, selected, if (dark) .12f else .08f),
        surfaceTint = selected
    )
}

@Composable
internal fun GhajarAppearanceSettings() {
    val settings = GhajarAppearance.get(LocalContext.current)
    val saved by settings.accent.collectAsState()
    var hex by rememberSaveable { mutableStateOf(saved ?: "#C79E48") }
    val normalized = GhajarColorRules.normalize(hex)
    val fa = LocalLang.current == Lang.FA
    val presets = listOf("قاجاری" to "#C79E48", "طلایی" to "#E2C97C", "آبی" to "#398BCB", "قرمز" to "#D95864",
        "بنفش" to "#8252CF", "زرد" to "#F2CA4C", "سبز" to "#359F78", "نارنجی" to "#EE9852")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (fa) "رنگ دلخواه" else "Accent color", style = MaterialTheme.typography.titleMedium)
        Text(if (fa) "رنگ اصلی را انتخاب کن؛ متن و دکمه‌ها برای خوانایی با تم هماهنگ می‌شوند."
            else "Choose an accent. Text and buttons adapt to keep their contrast.", style = MaterialTheme.typography.bodySmall)
        presets.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { hex = value; settings.setAccent(value) }
                        .semantics { selected = saved == value; contentDescription = if (fa) label else value }
                        .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(38.dp).background(Color(android.graphics.Color.parseColor(value)), CircleShape)
                            .border(if (saved == value) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), CircleShape))
                        Text(if (fa) label else value, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
        GhajarColorWheel(hex, onChange = { hex = it })
        OutlinedTextField(value = hex, onValueChange = { if (it.length <= 7) hex = it },
            label = { Text("HEX · #RRGGBB") }, singleLine = true,
            isError = normalized == null, modifier = Modifier.fillMaxWidth().testTag("ghajar_accent_hex"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { normalized?.let(settings::setAccent) }, enabled = normalized != null,
                modifier = Modifier.weight(1f).testTag("ghajar_accent_apply")) { Text(if (fa) "اعمال رنگ" else "Apply") }
            OutlinedButton(onClick = { settings.setAccent(null); hex = "#C79E48" }, modifier = Modifier.weight(1f)) {
                Text(if (fa) "رنگ اصلی" else "Reset")
            }
        }
    }
}

@Composable
private fun GhajarColorWheel(hex: String, onChange: (String) -> Unit) {
    val hsv = remember(hex) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(
            android.graphics.Color.parseColor(GhajarColorRules.normalize(hex) ?: "#C79E48"), it) }
    }
    val current by rememberUpdatedState(hsv)
    val change by rememberUpdatedState(onChange)
    val description = if (LocalLang.current == Lang.FA) "حلقهٔ رنگ؛ کد HEX نیز قابل ویرایش است" else "Color wheel; HEX can also be edited"
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(244.dp).semantics { contentDescription = description }.pointerInput(Unit) {
            fun pick(point: Offset) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f
                val offset = point - c
                val values = current.copyOf()
                if (offset.getDistance() >= radius * .70f) {
                    values[0] = ((Math.toDegrees(atan2(offset.y, offset.x).toDouble()) + 360.0) % 360.0).toFloat()
                } else {
                    val half = radius * .46f
                    values[1] = ((offset.x + half) / (half * 2)).coerceIn(0f, 1f)
                    values[2] = (1f - (offset.y + half) / (half * 2)).coerceIn(0f, 1f)
                }
                change(String.format(Locale.ROOT, "#%06X", android.graphics.Color.HSVToColor(values) and 0xFFFFFF))
            }
            detectDragGestures(onDragStart = ::pick) { event, _ -> event.consume(); pick(event.position) }
        }) {
            val radius = size.minDimension / 2f
            val thickness = radius * .20f
            val hueColors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
            drawCircle(Brush.sweepGradient(hueColors), radius - thickness / 2, style = Stroke(thickness))
            val half = radius * .46f
            val origin = center - Offset(half, half)
            val square = Size(half * 2, half * 2)
            val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
            drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue), origin.x, origin.x + square.width), origin, square)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), origin.y, origin.y + square.height), origin, square)
            val position = origin + Offset(hsv[1] * square.width, (1f - hsv[2]) * square.height)
            drawCircle(Color.White, 6.dp.toPx(), position, style = Stroke(2.dp.toPx()))
            drawCircle(Color.Black, 7.dp.toPx(), position, style = Stroke(1.dp.toPx()))
            val angle = Math.toRadians(hsv[0].toDouble())
            val dot = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (radius - thickness / 2)
            drawCircle(Color.White, thickness / 2.5f, dot, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
internal fun GhajarRoyalHome(style: String, modifier: Modifier = Modifier) {
    val connection by VpnState.state.collectAsState()
    val geo = rememberGhajarLocation()
    val fa = LocalLang.current == Lang.FA
    Column(modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (style == "royal") Image(painterResource(R.drawable.ghajar_royal_characters),
                contentDescription = if (fa) "شاه و ملکهٔ قاجار" else "Qajar king and queen",
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().testTag("ghajar_royal_home"))
            else {
                Box(Modifier.size(150.dp).background(Brush.radialGradient(listOf(
                    MaterialTheme.colorScheme.primaryContainer, Color.Transparent)), CircleShape))
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(100.dp))
            }
        }
        val label = when (connection) {
            Connection.CONNECTED -> if (fa) "اتصال برقرار است" else "Connected"
            Connection.CONNECTING -> if (fa) "در حال اتصال…" else "Connecting…"
            Connection.DISCONNECTING -> if (fa) "در حال قطع…" else "Disconnecting…"
            Connection.ERROR -> if (fa) "اتصال برقرار نشد" else "Connection failed"
            else -> if (fa) "آمادهٔ اتصال" else "Ready to connect"
        }
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
        geo.location?.let { location ->
            Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${GhajarLocationRules.flag(location.countryCode)} ${location.country}", style = MaterialTheme.typography.labelLarge)
                    Text(location.ip, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } ?: GhajarLocationStatus(geo)
    }
}

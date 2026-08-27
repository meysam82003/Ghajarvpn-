package net.gozar.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** One glass-like banner across every tab, physically swiped left-to-right in RTL too. */
@Composable
internal fun GhajarNoticeBanner() {
    val notice by GhajarNoticeBus.notice.collectAsState()
    val context = LocalContext.current
    var expandedId by remember { mutableStateOf<String?>(null) }
    val current = notice ?: return
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 90.dp.toPx() }
    var drag by remember(current.id) { mutableFloatStateOf(0f) }
    val shine = remember(current.id) { Animatable(-1f) }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(current.id) { shine.animateTo(2f, tween(1400)) }
    fun dismiss() { GhajarNotificationMonitor.acknowledge(context, current.id) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
            .graphicsLayer { translationX = drag; alpha = (1f - drag / (threshold * 3)).coerceIn(.15f, 1f) }
            .pointerInput(current.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, delta -> change.consume(); drag = (drag + delta).coerceAtLeast(0f) },
                    onDragEnd = { if (drag > threshold) dismiss() else scope.launch {
                        val offset = Animatable(drag)
                        offset.animateTo(0f, tween(160)) { drag = value }
                    } },
                    onDragCancel = { drag = 0f }
                )
            }
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = .2f)),
        shadowElevation = 2.dp,
        color = colors.surface.copy(alpha = .94f)
    ) {
        Row(Modifier.clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = .10f), colors.surface.copy(alpha = .4f))))
            .drawWithContent {
                drawContent()
                val x = size.width * shine.value
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .12f), Color.Transparent),
                    start = Offset(x - size.width / 3, 0f), end = Offset(x, size.height)))
            }, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { expandedId = current.id }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Notifications, null, tint = if (current.important) colors.tertiary else colors.primary,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(current.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current.message != current.title) Text(current.message, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = ::dismiss) { Icon(Icons.Filled.Close, "بستن این اعلان") }
        }
    }
    if (expandedId == current.id) {
        AlertDialog(
            onDismissRequest = { expandedId = null },
            title = { Text(current.title) },
            text = { Text(current.message, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { dismiss(); expandedId = null }) { Text("خواندم") } },
            dismissButton = { TextButton(onClick = { expandedId = null }) { Text("بازگشت") } }
        )
    }
}

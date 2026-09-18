package net.gozar.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class GhajarConnectionEvent(val timeMs: Long, val state: String, val targetId: String?)

private fun parseConnectionEvent(entry: GhajarLogEntry): GhajarConnectionEvent? {
    if (entry.tag != "VpnState") return null
    val state = Regex("state -> (\\w+)").find(entry.message)?.groupValues?.getOrNull(1) ?: return null
    val id = Regex("id(?: was)? ?= ?([^\\s(),]+)").find(entry.message)?.groupValues?.getOrNull(1)
        ?.takeUnless { it == "null" }
    return GhajarConnectionEvent(entry.timeMs, state, id)
}

private fun stateLabel(state: String): Pair<String, Boolean> = when (state) {
    "CONNECTED" -> "وصل شد" to false
    "CONNECTING" -> "در حال اتصال" to false
    "DISCONNECTED" -> "قطع شد" to false
    "DISCONNECTING" -> "در حال قطع" to false
    "ERROR" -> "خطای اتصال" to true
    else -> state to false
}

/**
 * Real, local-only history of this device's own connect/disconnect/error
 * transitions - built by filtering the existing in-memory log ring buffer
 * (GhajarLog.entries) for the "VpnState" tag that every real state change
 * already logs, rather than a separate tracking mechanism. Nothing here
 * leaves the device; there is no share/export action on purpose, unlike
 * the full log export in GhajarLogActivity.
 */
@Composable
fun ConnectionHistoryDialog(onDismiss: () -> Unit) {
    val entries by GhajarLog.entries.collectAsState()
    val events = remember(entries) {
        entries.mapNotNull(::parseConnectionEvent).sortedByDescending { it.timeMs }.take(100)
    }
    val timeFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تاریخچهٔ اتصال") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (events.isEmpty()) {
                    Text("هنوز رویداد اتصالی ثبت نشده است.", style = MaterialTheme.typography.bodySmall)
                } else {
                    events.forEach { event ->
                        val (label, isError) = stateLabel(event.state)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(label, fontWeight = FontWeight.Bold,
                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                event.targetId?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(timeFormat.format(Date(event.timeMs)), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = ghajarColors.border)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

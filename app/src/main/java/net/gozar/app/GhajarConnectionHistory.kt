package net.gozar.app

import androidx.compose.foundation.background
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
 * Local-only connect/disconnect/error history, read from the log that every
 * real VpnState transition writes (tag "VpnState"). Shown as a table: time,
 * the profile by name with its flag, and what happened. Nothing leaves the
 * device.
 */
@Composable
fun ConnectionHistoryDialog(onDismiss: () -> Unit) {
    val entries by GhajarLog.entries.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val configs by remember { ConfigStore.get(context) }.configs.collectAsState()
    val names = remember(configs) { configs.associate { it.id to it.name } }
    val events = remember(entries) {
        entries.mapNotNull(::parseConnectionEvent).sortedByDescending { it.timeMs }.take(100)
    }
    val errors = remember(entries) {
        entries.filter { it.tag == "VpnState" && it.message.contains("state -> ERROR") }
            .associate { it.timeMs to it.message.substringAfter("msg=", "").substringBefore(" (was").trim() }
    }
    val timeFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.US) }
    val c = ghajarColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("تاریخچهٔ اتصال", fontWeight = FontWeight.Bold, color = c.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (events.isEmpty()) {
                    Text("هنوز رویداد اتصالی ثبت نشده است.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                } else {
                    // Header row, then one row per event with the same column widths.
                    Row(
                        Modifier.fillMaxWidth().background(c.primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("زمان", Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Text("کانفیگ", Modifier.weight(1.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Text("وضعیت", Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    }
                    events.forEach { event ->
                        val (label, isError) = stateLabel(event.state)
                        val name = event.targetId?.let { names[it] ?: "(حذف‌شده)" } ?: "—"
                        HorizontalDivider(color = c.border.copy(alpha = 0.5f))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(timeFormat.format(Date(event.timeMs)), Modifier.weight(0.9f),
                                style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                            Column(Modifier.weight(1.6f)) {
                                // Names carry their flag emoji; flagRuns renders them as flags.
                                Text(flagRuns(GhajarUiRules.brandedConfigName(name), androidx.compose.ui.text.font.FontFamily.Default),
                                    inlineContent = flagInlineContent(name, MaterialTheme.typography.labelMedium.fontSize),
                                    style = MaterialTheme.typography.labelMedium, color = c.textPrimary, maxLines = 2)
                                if (isError) errors[event.timeMs]?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.error, maxLines = 2)
                                }
                            }
                            Text(label, Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when { isError -> c.error; event.state == "CONNECTED" -> c.good; else -> c.textSecondary })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن", color = c.primary) } }
    )
}

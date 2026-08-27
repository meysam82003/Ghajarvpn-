package net.gozar.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One shared banner below the app bar, on every tab and nested screen. */
@Composable
internal fun GhajarNoticeBanner() {
    val notice by GhajarNoticeBus.notice.collectAsState()
    val context = LocalContext.current
    var expandedId by remember { mutableStateOf<String?>(null) }
    val current = notice ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = if (current.important) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { expandedId = current.id }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Notifications, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(current.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(current.message, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = { GhajarNotificationMonitor.acknowledge(context, current.id) }) {
                Icon(Icons.Filled.Close, "بستن این اعلان")
            }
        }
    }
    if (expandedId == current.id) {
        AlertDialog(
            onDismissRequest = { expandedId = null },
            title = { Text(current.title) },
            text = { Text(current.message) },
            confirmButton = { TextButton(onClick = { GhajarNotificationMonitor.acknowledge(context, current.id); expandedId = null }) { Text("خواندم") } },
            dismissButton = { TextButton(onClick = { expandedId = null }) { Text("بازگشت") } }
        )
    }
}

package net.gozar.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Preview of the ACTUAL ConfigStore entries, not invented services or fake ping.
 * Connected-server switching is delegated to the existing ConfigPicker because
 * that flow owns the live onSwitch callback and its connection lifecycle.
 */
@Composable
internal fun GhajarHomeServices(
    configs: List<ProxyConfig>,
    selectedId: String?,
    activeId: String?,
    connection: Connection,
    persian: Boolean,
    onSelectDisconnected: (ProxyConfig) -> Unit,
    onOpenAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (persian) "سرویس‌ها و سرورها" else "Services & servers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenAll) { Text(if (persian) "مشاهدهٔ همه" else "See all") }
        }
        if (configs.isEmpty()) {
            Text(
                if (persian) "هنوز سروری اضافه نشده است؛ برای افزودن یا واردکردن سرور وارد فهرست شوید."
                else "No servers yet. Open the list to add or import one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                configs.take(6).forEach { server ->
                    FilterChip(
                        selected = server.id == selectedId,
                        onClick = {
                            if (connection == Connection.DISCONNECTED || connection == Connection.ERROR) {
                                onSelectDisconnected(server)
                            } else {
                                // Never merely relabel a live tunnel as another server.
                                onOpenAll()
                            }
                        },
                        label = {
                            val actual = server.id == activeId && connection == Connection.CONNECTED
                            Text(
                                server.name + if (actual) (if (persian) " · متصل" else " · Active") else "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }
    }
}

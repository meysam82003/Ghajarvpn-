package net.gozar.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Compact "your connection route" card: config name, protocol/engine and the
 * live connection state in one tight premium row that leaves room for home.
 */
@Composable
internal fun GhajarSelectedServerCard(config: ProxyConfig?, connection: Connection, onChoose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val stateColor = when (connection) {
        Connection.CONNECTED -> colors.primary
        Connection.ERROR -> colors.error
        else -> colors.onSurfaceVariant
    }
    Card(onClick = onChoose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = .20f))) {
        Row(
            Modifier.background(Brush.linearGradient(listOf(colors.primary.copy(alpha = .07f), colors.surface)))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.primary.copy(alpha = .10f)) {
                Icon(Icons.Filled.Shield, null, modifier = Modifier.padding(6.dp).size(17.dp), tint = colors.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(config?.name?.let(BrandConfig::sanitizePublicText) ?: "یک سرویس انتخاب کن",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (config != null) {
                    val engine = when {
                        config.locked -> "قفل‌شده"
                        config.protocol in setOf("aether", "tor") -> config.protocol.uppercase(Locale.ROOT)
                        else -> config.protocol.uppercase(Locale.ROOT)
                    }
                    val endpoint = when {
                        config.locked -> "اطلاعات اتصال محفوظ"
                        config.protocol in setOf("aether", "tor") -> "موتور داخلی"
                        else -> "\u2066${config.address}:${config.port}\u2069"
                    }
                    Text("$engine • $endpoint", style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(when (connection) {
                    Connection.CONNECTED -> "متصل"
                    Connection.CONNECTING -> "در حال اتصال…"
                    Connection.ERROR -> "اتصال برقرار نشد"
                    else -> "آمادهٔ اتصال"
                }, style = MaterialTheme.typography.labelSmall, color = stateColor)
            }
            Icon(Icons.Filled.ChevronRight, "انتخاب یا تغییر سرویس",
                tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

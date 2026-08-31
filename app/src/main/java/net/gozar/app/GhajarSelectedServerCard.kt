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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun GhajarSelectedServerCard(config: ProxyConfig?, connection: Connection, onChoose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(onClick = onChoose, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = .18f))) {
        Row(Modifier.background(Brush.linearGradient(listOf(colors.primary.copy(alpha = .08f), colors.surface)))
            .padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(17.dp), color = colors.primary.copy(alpha = .10f)) {
                Icon(Icons.Filled.Shield, null, modifier = Modifier.padding(12.dp).size(28.dp), tint = colors.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("مسیر اتصال شما", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(config?.name?.let(BrandConfig::sanitizePublicText) ?: "یک سرویس انتخاب کن",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (config != null) {
                    val endpoint = when {
                        config.locked -> "اطلاعات اتصال محفوظ"
                        config.protocol in setOf("aether", "tor") -> "موتور داخلی • ${config.protocol.uppercase(Locale.ROOT)}"
                        else -> "\u2066${config.address}:${config.port}\u2069"
                    }
                    Text(endpoint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(when (connection) {
                    Connection.CONNECTED -> "متصل"
                    Connection.CONNECTING -> "در حال اتصال…"
                    Connection.DISCONNECTING -> "در حال قطع…"
                    Connection.ERROR -> "اتصال برقرار نشد"
                    else -> "آمادهٔ اتصال"
                }, style = MaterialTheme.typography.labelMedium,
                    color = if (connection == Connection.ERROR) colors.error else colors.primary)
            }
            Icon(Icons.Filled.ChevronRight, "انتخاب یا تغییر سرویس", tint = colors.onSurfaceVariant)
        }
    }
}

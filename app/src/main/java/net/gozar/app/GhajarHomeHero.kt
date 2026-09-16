package net.gozar.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Real plan/expiry status card for Home (doc: "پلن یا اشتراک فعال، زمان باقی‌مانده").
 * Only renders when the account is linked and the backend actually returns an
 * owned service — no placeholder plan/expiry text is shown otherwise.
 */
@Composable
internal fun GhajarPlanStatusCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val api = remember { GhajarStoreApi(context) }
    var status by remember { mutableStateOf<GhajarServiceDetails?>(null) }

    LaunchedEffect(Unit) {
        if (!api.isLinked) return@LaunchedEffect
        val owned = runCatching { api.ownedServices() }.getOrNull().orEmpty()
        val first = owned.firstOrNull() ?: return@LaunchedEffect
        status = runCatching { api.service(first.username) }.getOrNull()
    }

    AnimatedVisibility(visible = status != null, enter = fadeIn(), exit = fadeOut()) {
        val s = status
        if (s != null) {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(AppGreen.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.WorkspacePremium,
                            contentDescription = null,
                            tint = AppGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(s.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${s.status} • ${s.expiresAt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Real, navigable quick actions on Home (doc: shortcuts to Free Config / server list / scan). */
@Composable
internal fun GhajarQuickActionsRow(
    onFastest: () -> Unit,
    onAllServers: () -> Unit,
    onFreeConfigs: () -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickActionCell(Icons.Filled.Bolt, "سریع‌ترین", onFastest, Modifier.weight(1f))
        QuickActionCell(Icons.Filled.Workspaces, "انتخاب سرور", onAllServers, Modifier.weight(1f))
        QuickActionCell(Icons.Filled.CardGiftcard, "کانفیگ رایگان", onFreeConfigs, Modifier.weight(1f))
        QuickActionCell(Icons.Filled.QrCodeScanner, "اسکن QR", onScanQr, Modifier.weight(1f))
    }
}

@Composable
private fun QuickActionCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = AppGreen, modifier = Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

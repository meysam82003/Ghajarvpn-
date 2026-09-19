package net.gozar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** State labels only accept caller-supplied observations, never example metrics. */
@Composable
internal fun PremiumStatus(label: String, healthy: Boolean = false) {
    val c = ghajarColors
    val tint = if (healthy) c.successGlow else c.textSecondary
    Text(mixedText(label), color = tint, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(GhajarRadius.pill))
            .background(tint.copy(alpha = .08f))
            .border(1.dp, tint.copy(alpha = .22f), RoundedCornerShape(GhajarRadius.pill))
            .padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
internal fun HomeSubscriptionSummary(store: ConfigStore, selectedId: String?, onOpen: () -> Unit) {
    val configs by store.configs.collectAsState()
    val subscriptions by store.subscriptions.collectAsState()
    val subscription = subscriptions.firstOrNull { sub ->
        sub.id == configs.firstOrNull { it.id == selectedId }?.subId &&
            (sub.total > 0 || sub.expire > 0)
    } ?: return
    val lang = LocalLang.current
    val days = subscription.expire.takeIf { it > 0 }?.let {
        ((it * 1000 - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
    }
    Slab(accent = ghajarColors.primary, onClick = onOpen) {
        SlabRow(title = BrandConfig.sanitizePublicText(subscription.name),
            subtitle = listOfNotNull(
                days?.let { if (lang == Lang.FA) "${localizeDigits(it.toString(), lang)} روز باقی‌مانده" else "$it days remaining" },
                if (subscription.total > 0) {
                    val remaining = ((subscription.total - subscription.used).coerceAtLeast(0) / 1073741824.0)
                    val value = localizeDigits(String.format(java.util.Locale.US, "%.1f", remaining), lang)
                    if (lang == Lang.FA) "$value گیگابایت باقی‌مانده" else "$value GB remaining"
                } else null
            ).joinToString(" · "),
            icon = Icons.Filled.WorkspacePremium, chevron = true, onClick = onOpen)
    }
}

@Composable
internal fun HomeShortcuts(onServers: () -> Unit, onFavorites: () -> Unit,
    onFree: () -> Unit, onFastest: () -> Unit, fastestBusy: Boolean, hasServers: Boolean) {
    val fa = LocalLang.current == Lang.FA
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeShortcut(if (fa) "سرورها" else "Servers", Icons.Filled.Public, onServers, Modifier.weight(1f))
        HomeShortcut(if (fa) "علاقه‌مندی" else "Favorites", Icons.Filled.StarOutline, onFavorites, Modifier.weight(1f))
        HomeShortcut(if (fa) "رایگان" else "Free", Icons.Filled.CloudDownload, onFree, Modifier.weight(1f))
        HomeShortcut(if (fastestBusy) "…" else if (fa) "سریع‌ترین" else "Fastest",
            Icons.Filled.Bolt, onFastest, Modifier.weight(1f), hasServers && !fastestBusy)
    }
}

@Composable
private fun HomeShortcut(label: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = ghajarColors
    Column(modifier.clip(RoundedCornerShape(GhajarRadius.md))
        .background(c.card).border(1.dp, c.borderStrong, RoundedCornerShape(GhajarRadius.md))
        .clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = 78.dp).padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = if (enabled) c.highlight else c.onDisabled, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
            color = if (enabled) c.textPrimary else c.onDisabled, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

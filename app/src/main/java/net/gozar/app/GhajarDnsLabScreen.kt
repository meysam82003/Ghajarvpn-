package net.gozar.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The DNS lab: measure resolvers for real, see which ones lie, pick one.
 *
 * Nothing here replaces the tunnel's DNS settings. Encrypted DNS and fake DNS
 * are still their own switches and still work exactly as before; choosing a
 * resolver here puts it in front of the built-in ones, and clearing the choice
 * restores precisely the previous behaviour.
 *
 * The scan asks each resolver for a name that is blocked where this app is
 * used, because that is the only question whose answer distinguishes the three
 * states worth knowing: it works, it is dead, or it answers quickly with a
 * sinkhole address. A latency ranking alone would recommend the poisoned ones,
 * which are usually the fastest of all.
 */
@Composable
fun DnsLabScreen(store: ConfigStore, modifier: Modifier = Modifier) {
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    val c = ghajarColors
    val scope = rememberCoroutineScope()
    val chosen by store.customDns.collectAsState()

    val results = remember { mutableStateMapOf<String, DnsProbeResult>() }
    var scanning by remember { mutableStateOf(false) }
    var onlyBlockedHost by remember { mutableStateOf(true) }

    val host = if (onlyBlockedHost) GhajarDnsLab.BLOCKED_PROBE_HOST
    else GhajarDnsLab.NEUTRAL_PROBE_HOST

    fun runScan() {
        if (scanning) return
        scanning = true
        results.clear()
        scope.launch {
            runCatching {
                GhajarDnsLab.scan(host = host) { resolver, result ->
                    results[resolver.id] = result
                }
            }
            scanning = false
        }
    }

    LazyColumn(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Slab {
                Text(
                    t("dnslab_intro"),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
                // Read low, inside its own item: the result map is a snapshot
                // map, so reading it higher up would subscribe the whole
                // screen to every entry and recompose all of it per answer.
                DnsLabSummary(results)
                if (scanning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = c.primary,
                            strokeWidth = 2.dp
                        )
                        Text(t("dnslab_running"), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    }
                } else {
                    PillButton(t("dnslab_scan"), onClick = { runScan() }, icon = Icons.Filled.Refresh)
                }
                SettingRowLite(
                    title = t("dnslab_probe_blocked"),
                    subtitle = t("dnslab_probe_blocked_sub").format(host),
                    checked = onlyBlockedHost,
                    onCheckedChange = { onlyBlockedHost = it }
                )
            }
        }

        item {
            Slab {
                Text(
                    t("dnslab_chosen"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = c.textSecondary
                )
                Text(
                    if (chosen.isBlank()) t("dnslab_chosen_default") else chosen,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chosen.isBlank()) c.textMuted else c.primary
                )
                Text(
                    t("dnslab_chosen_note"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted
                )
                if (chosen.isNotBlank()) {
                    GhostPill(t("dnslab_clear"), onClick = { store.setCustomDns("") })
                }
            }
        }

        items(GhajarDnsLab.Catalogue, key = { it.id }) { resolver ->
            DnsResolverRow(
                resolver = resolver,
                result = results[resolver.id] ?: DnsProbeResult.Pending,
                isChosen = chosen == resolver.address,
                lang = lang,
                onChoose = { store.setCustomDns(resolver.address) }
            )
        }

        item {
            Text(
                t("dnslab_footer"),
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

/** How many answered, how many lied, and the best honest time. */
@Composable
private fun DnsLabSummary(results: Map<String, DnsProbeResult>) {
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    val ok by remember { derivedStateOf { results.values.filterIsInstance<DnsProbeResult.Ok>() } }
    val clean = ok.filter { !it.poisoned }
    StatStrip(
        listOf(
            StatCell(t("dnslab_answered"), localizeDigits("${ok.size}", lang)),
            StatCell(
                t("dnslab_poisoned"),
                localizeDigits("${ok.count { it.poisoned }}", lang),
                accent = if (ok.any { it.poisoned }) ghajarColors.error else null
            ),
            StatCell(
                t("dnslab_best"),
                clean.minByOrNull { it.ms }?.let { localizeDigits("${it.ms}", lang) + " " + t("unit_ms") } ?: "—"
            )
        )
    )
}

@Composable
private fun DnsResolverRow(
    resolver: DnsResolver,
    result: DnsProbeResult,
    isChosen: Boolean,
    lang: Lang,
    onChoose: () -> Unit
) {
    val t: (String) -> String = { Strings.get(lang, it) }
    val c = ghajarColors
    val tint = when (result) {
        is DnsProbeResult.Ok -> if (result.poisoned) c.error else c.successGlow
        is DnsProbeResult.Refused -> c.warning
        is DnsProbeResult.Failed -> c.error
        DnsProbeResult.Testing -> c.info
        DnsProbeResult.Pending -> c.textMuted
    }
    val background by animateColorAsState(
        if (isChosen) c.primary.copy(alpha = 0.14f) else c.secondaryCard,
        tween(220),
        label = "dnsRow"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(background)
            // Only a resolver that answered honestly can be chosen: offering
            // the poisoned ones as a setting would be offering the bug.
            .clickable(enabled = result is DnsProbeResult.Ok && !result.poisoned) { onChoose() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                result == DnsProbeResult.Testing -> CircularProgressIndicator(
                    modifier = Modifier.size(15.dp), color = tint, strokeWidth = 2.dp
                )
                isChosen -> Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(17.dp))
                result is DnsProbeResult.Ok && result.poisoned ->
                    Icon(Icons.Filled.Warning, null, tint = tint, modifier = Modifier.size(17.dp))
                else -> Icon(Icons.Filled.Dns, null, tint = tint, modifier = Modifier.size(17.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    resolver.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = c.textPrimary,
                    maxLines = 1
                )
                Text(
                    resolver.transport.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.textMuted.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Text(
                mixedText(resolver.address),
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // What the measurement actually found, in words rather than a
            // colour alone - a "fast" resolver that answers with a sinkhole
            // has to say so.
            val detail = when (result) {
                DnsProbeResult.Pending -> resolver.note
                DnsProbeResult.Testing -> t("dnslab_testing")
                is DnsProbeResult.Ok -> when {
                    result.poisoned -> t("dnslab_result_poisoned").format(result.addresses.firstOrNull().orEmpty())
                    result.mismatched -> t("dnslab_result_mismatch")
                    else -> localizeDigits("${result.ms}", lang) + " " + t("unit_ms") +
                        " · " + result.addresses.first()
                }
                is DnsProbeResult.Refused -> t("dnslab_result_refused").format(result.rcode)
                is DnsProbeResult.Failed -> t("dnslab_result_failed").format(result.reason)
            }
            if (detail.isNotBlank()) {
                Text(
                    mixedText(detail),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    maxLines = 2
                )
            }
            if (resolver.local && result is DnsProbeResult.Ok && !result.poisoned) {
                Text(
                    t("dnslab_local_note"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.warning,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * A switch row for a value that lives only in this screen.
 *
 * SettingRow in MainActivity is private to that file; this is the same shape
 * for a local toggle rather than a reason to widen that one's visibility.
 */
@Composable
private fun SettingRowLite(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val c = ghajarColors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.md))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = GhajarSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = c.textPrimary
            )
            Text(
                mixedText(subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary
            )
        }
        SkinSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

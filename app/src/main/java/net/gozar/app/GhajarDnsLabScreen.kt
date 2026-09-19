package net.gozar.app

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** How the list is ordered. */
private enum class DnsSort { FASTEST, STEADIEST, HEALTHY, TUNNEL, NAME }

/** Which resolvers are shown. */
private enum class DnsFilter { ALL, GOOD, BAD, UNTESTED, TUNNEL }

/**
 * The DNS laboratory: bring in resolvers, measure them for real, keep the ones
 * that work.
 *
 * Two things this screen is careful to keep apart, because conflating them is
 * the single most misleading thing a DNS feature can do:
 *
 * - **Choosing a resolver** puts that resolver in front of the tunnel's
 *   built-in DNS. It is a real change and it is what the "connect" action here
 *   does. It is not a VPN, and a healthy resolver does not by itself mean
 *   unblocked internet - plenty of them resolve everything correctly and still
 *   sit behind the same filtering.
 * - **A DNS tunnel** carries traffic through DNS and needs a server, a domain
 *   and a key. That is a separate profile, and this screen never reports a
 *   tunnel as connected.
 *
 * Nothing here replaces the existing DNS settings. Encrypted DNS and fake DNS
 * remain their own switches and behave exactly as before; clearing the choice
 * restores precisely the previous behaviour.
 */
@Composable
fun DnsLabScreen(store: ConfigStore, modifier: Modifier = Modifier) {
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    val n: (String) -> String = { localizeDigits(it, lang) }
    val c = ghajarColors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val labStore = remember { DnsResolverStore.get(context) }
    LaunchedEffectOnce { labStore.load() }

    val resolvers by labStore.resolvers.collectAsState()
    val verdicts by labStore.verdicts.collectAsState()
    val progress by DnsScanEngine.progress.collectAsState()
    val customDns by store.customDns.collectAsState()
    val dnsPhase by DnsOnlyState.phase.collectAsState()
    val dnsResolverInUse by DnsOnlyState.resolver.collectAsState()
    val dnsServed by DnsOnlyState.served.collectAsState()
    val privateDnsConflict by DnsOnlyState.privateDnsConflict.collectAsState()
    val tunnelState by VpnState.state.collectAsState()
    val tunnelPhase by DnsTunnelController.phase.collectAsState()
    val tunnelDetail by DnsTunnelController.detail.collectAsState()
    val failPolicy by store.dnsFailPolicy.collectAsState()

    var sort by remember { mutableStateOf(DnsSort.FASTEST) }
    var filter by remember { mutableStateOf(DnsFilter.ALL) }
    var status by remember { mutableStateOf("") }
    var manualOpen by remember { mutableStateOf(false) }
    // The address DNS-only mode should start on once consent comes back. Held
    // rather than recomputed, because between asking and being granted the
    // user may have changed the chosen resolver.
    var pendingDnsOnly by remember { mutableStateOf<String?>(null) }

    val vpnConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val address = pendingDnsOnly
        pendingDnsOnly = null
        if (result.resultCode == android.app.Activity.RESULT_OK && address != null) {
            GhajarDnsOnlyService.start(context, address)
        }
    }

    fun startDnsOnly(address: String) {
        val consent = GhajarDnsOnlyService.prepareOrNull(context)
        if (consent != null) {
            pendingDnsOnly = address
            vpnConsent.launch(consent)
        } else {
            GhajarDnsOnlyService.start(context, address)
        }
    }

    // The tunnel domain, if the user has a profile with one. It is what makes
    // the tunnel-path test possible at all: with no domain there is nothing to
    // aim a TXT query at, and the column stays empty rather than guessing.
    val tunnelDomain by store.dnsTunnelDomain.collectAsState()

    fun scan(subset: List<DnsResolver>) {
        if (progress.running || subset.isEmpty()) return
        scope.launch {
            status = t("dnslab_getting_baseline")
            val baseline = DnsScanEngine.baseline()
            status = if (baseline.isEmpty()) t("dnslab_no_baseline") else ""
            DnsScanEngine.run(
                resolvers = subset,
                baseline = baseline,
                tunnelDomain = tunnelDomain.takeIf { it.isNotBlank() },
                onBatch = { labStore.putVerdicts(it) }
            )
        }
    }

    fun importText(text: String, source: String) {
        scope.launch {
            val report = withContext(Dispatchers.Default) {
                DnsResolverImport.parse(text, labStore.resolvers.value)
            }
            labStore.add(report.resolvers)
            status = t("dnslab_import_report").format(
                n("${report.added}"), n("${report.duplicates}"),
                n("${report.invalid}"), n("${report.skipped}")
            ) + " · " + source
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.reader().readText()
                    }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) status = t("dnslab_import_unreadable")
            else importText(text, t("dnslab_from_file"))
        }
    }

    val shown = remember(resolvers, verdicts, sort, filter) {
        val filtered = resolvers.filter { r ->
            val v = verdicts[r.id]
            when (filter) {
                DnsFilter.ALL -> true
                DnsFilter.GOOD -> v?.health == DnsHealth.GOOD
                DnsFilter.BAD -> v?.health == DnsHealth.BAD
                DnsFilter.UNTESTED -> v == null || v.health == DnsHealth.UNTESTED
                DnsFilter.TUNNEL -> v?.tunnelReady == true
            }
        }
        when (sort) {
            DnsSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            // Untested sorts last in every measured order, rather than first
            // by accident of a null: a list that puts a thousand unmeasured
            // rows above the six that answered is unusable.
            DnsSort.FASTEST -> filtered.sortedBy { verdicts[it.id]?.latencyMs ?: Int.MAX_VALUE }
            DnsSort.STEADIEST -> filtered.sortedByDescending { verdicts[it.id]?.successPercent ?: -1 }
            DnsSort.HEALTHY -> filtered.sortedBy { healthRank(verdicts[it.id]) }
            DnsSort.TUNNEL -> filtered.sortedBy {
                when (verdicts[it.id]?.tunnelReady) {
                    true -> 0
                    false -> 2
                    null -> 1
                }
            }
        }
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "intro") {
            Spacer(Modifier.height(6.dp))
            Slab {
                Text(
                    t("dnslab_intro"),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
                // Said plainly and near the top, because it is the single
                // thing people get wrong about this screen.
                Text(
                    t("dnslab_not_a_vpn"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.warning
                )
            }
        }

        item(key = "stats") {
            DnsLabStats(
                total = resolvers.size,
                verdicts = verdicts,
                progress = progress,
                lang = lang
            )
        }

        item(key = "add") {
            Rail(t("dnslab_add"))
            Slab(spacing = GhajarSpacing.sm) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlyphTileLite(
                        icon = Icons.Filled.ContentPaste,
                        label = t("dnslab_paste"),
                        modifier = Modifier.weight(1f)
                    ) {
                        val text = clipboard.getText()?.text
                        if (text.isNullOrBlank()) status = t("clipboard_empty")
                        else importText(text, t("dnslab_from_clipboard"))
                    }
                    GlyphTileLite(
                        icon = Icons.Filled.FileOpen,
                        label = t("dnslab_file"),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Every type, because a resolver list arrives as
                        // text/plain, text/csv, application/json or
                        // application/octet-stream depending on which app
                        // wrote it, and filtering on type hides the file the
                        // user can plainly see.
                        filePicker.launch(arrayOf("*/*"))
                    }
                    GlyphTileLite(
                        icon = Icons.Filled.Edit,
                        label = t("dnslab_manual"),
                        modifier = Modifier.weight(1f)
                    ) { manualOpen = true }
                }
                Text(
                    t("dnslab_add_note"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted
                )
            }
        }

        item(key = "actions") {
            Rail(t("dnslab_measure"))
            Slab(spacing = GhajarSpacing.sm) {
                if (progress.running) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = c.primary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            t("dnslab_progress").format(
                                n("${progress.done}"), n("${progress.total}"),
                                n("${progress.answered}")
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    GhostPill(
                        text = if (progress.stopping) t("dnslab_stopping") else t("dnslab_stop"),
                        onClick = { DnsScanEngine.stop() },
                        icon = Icons.Filled.Stop,
                        accent = c.error,
                        enabled = !progress.stopping
                    )
                } else {
                    PillButton(
                        text = t("dnslab_scan_all").format(n("${resolvers.size}")),
                        onClick = { scan(resolvers) },
                        icon = Icons.Filled.Refresh,
                        enabled = resolvers.isNotEmpty()
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GhostPill(
                            text = t("dnslab_retest_failed"),
                            onClick = {
                                scan(resolvers.filter { verdicts[it.id]?.health == DnsHealth.BAD })
                            },
                            modifier = Modifier.weight(1f),
                            enabled = verdicts.values.any { it.health == DnsHealth.BAD }
                        )
                        GhostPill(
                            text = t("dnslab_pick_best"),
                            onClick = {
                                val best = labStore.bestCandidate()
                                if (best == null) status = t("dnslab_no_candidate")
                                else {
                                    // manual = false: this was the app's pick,
                                    // so failover is allowed to move off it
                                    // later. A row's own "use this one" is the
                                    // manual case and keeps the default.
                                    labStore.choose(best.id, manual = false)
                                    store.setCustomDns(best.address)
                                    status = t("dnslab_chose").format(best.name)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GhostPill(
                            text = t("dnslab_keep_healthy"),
                            onClick = {
                                val before = resolvers.size
                                labStore.keepHealthy()
                                status = t("dnslab_kept").format(
                                    n("${labStore.resolvers.value.size}"), n("$before")
                                )
                            },
                            icon = Icons.Filled.Save,
                            modifier = Modifier.weight(1f)
                        )
                        GhostPill(
                            text = t("dnslab_export"),
                            onClick = {
                                scope.launch {
                                    val ok = shareExport(context, labStore)
                                    if (!ok) status = t("dnslab_export_failed")
                                }
                            },
                            icon = Icons.Filled.Download,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (status.isNotBlank()) {
                    Text(
                        mixedText(status),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary
                    )
                }
            }
        }

        item(key = "chosen") {
            Rail(t("dnslab_chosen"))
            Slab {
                Text(
                    if (customDns.isBlank()) t("dnslab_chosen_default") else mixedText(customDns).text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (customDns.isBlank()) c.textMuted else c.primary
                )
                Text(
                    t("dnslab_chosen_note"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted
                )
                if (customDns.isNotBlank()) {
                    GhostPill(
                        t("dnslab_clear"),
                        onClick = { store.setCustomDns(""); labStore.choose(null) }
                    )
                }
            }
        }

        item(key = "dnsonly") {
            Rail(t("dnsonly_title"))
            DnsOnlyCard(
                phase = dnsPhase,
                resolverInUse = dnsResolverInUse,
                served = dnsServed,
                privateDnsConflict = privateDnsConflict,
                tunnelUp = tunnelState != Connection.DISCONNECTED &&
                    tunnelState != Connection.ERROR,
                chosenAddress = customDns,
                lang = lang,
                onStart = { startDnsOnly(it) },
                onStop = { GhajarDnsOnlyService.stop(context) }
            )
        }

        item(key = "tunnel") {
            Rail(t("dnstun_title"))
            DnsTunnelSection(
                store = store,
                labStore = labStore,
                phase = tunnelPhase,
                detail = tunnelDetail,
                engineAvailable = remember { DnsTunnelController.available(context) },
                lang = lang
            )
        }

        item(key = "policy") {
            Rail(t("dnspolicy_title"))
            Slab(spacing = GhajarSpacing.sm) {
                Text(
                    t("dnspolicy_explain"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Chip(
                        label = t("dnspolicy_stay"),
                        selected = failPolicy == DnsFailPolicy.STAY,
                        onClick = {
                            store.setDnsFailPolicy(DnsFailPolicy.STAY)
                            DnsSmartSelect.reset()
                        }
                    )
                    Chip(
                        label = t("dnspolicy_next"),
                        selected = failPolicy == DnsFailPolicy.NEXT_HEALTHY,
                        onClick = {
                            store.setDnsFailPolicy(DnsFailPolicy.NEXT_HEALTHY)
                            DnsSmartSelect.reset()
                        }
                    )
                }
                Text(
                    when (failPolicy) {
                        DnsFailPolicy.STAY -> t("dnspolicy_stay_sub")
                        DnsFailPolicy.NEXT_HEALTHY -> t("dnspolicy_next_sub")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted
                )
            }
        }

        item(key = "filters") {
            Rail(t("dnslab_list").format(n("${shown.size}"), n("${resolvers.size}")))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DnsFilter.entries.forEach { f ->
                        val count = when (f) {
                            DnsFilter.ALL -> resolvers.size
                            DnsFilter.GOOD -> verdicts.values.count { it.health == DnsHealth.GOOD }
                            DnsFilter.BAD -> verdicts.values.count { it.health == DnsHealth.BAD }
                            DnsFilter.UNTESTED -> resolvers.size - verdicts.count {
                                it.value.health != DnsHealth.UNTESTED
                            }
                            DnsFilter.TUNNEL -> verdicts.values.count { it.tunnelReady == true }
                        }
                        Chip(
                            label = t(filterKey(f)) + "  " + n("$count"),
                            selected = filter == f,
                            onClick = { filter = f }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DnsSort.entries.forEach { s ->
                        Chip(
                            label = t(sortKey(s)),
                            selected = sort == s,
                            onClick = { sort = s }
                        )
                    }
                }
            }
        }

        items(shown, key = { it.id }) { resolver ->
            DnsResolverRow(
                resolver = resolver,
                verdict = verdicts[resolver.id],
                isChosen = customDns == resolver.address,
                lang = lang,
                onChoose = {
                    labStore.choose(resolver.id)
                    store.setCustomDns(resolver.address)
                },
                onForget = { labStore.remove(setOf(resolver.id)) }
            )
        }

        item(key = "footer") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (resolvers.isEmpty()) {
                    GhostPill(
                        t("dnslab_restore_catalogue"),
                        onClick = { labStore.restoreCatalogue() }
                    )
                } else {
                    GhostPill(
                        t("dnslab_clear_all"),
                        onClick = { labStore.clearAll(); store.setCustomDns("") },
                        icon = Icons.Filled.DeleteSweep,
                        accent = c.error
                    )
                }
                Text(
                    t("dnslab_footer"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }

    if (manualOpen) {
        DnsManualDialog(
            onDismiss = { manualOpen = false },
            onAdd = { resolver ->
                manualOpen = false
                status = if (labStore.addManual(resolver)) {
                    t("dnslab_added_one").format(resolver.address)
                } else {
                    t("dnslab_already_have")
                }
            }
        )
    }
}

private fun filterKey(f: DnsFilter): String = when (f) {
    DnsFilter.ALL -> "dnslab_f_all"
    DnsFilter.GOOD -> "dnslab_f_good"
    DnsFilter.BAD -> "dnslab_f_bad"
    DnsFilter.UNTESTED -> "dnslab_f_untested"
    DnsFilter.TUNNEL -> "dnslab_f_tunnel"
}

private fun sortKey(s: DnsSort): String = when (s) {
    DnsSort.FASTEST -> "dnslab_s_fastest"
    DnsSort.STEADIEST -> "dnslab_s_steadiest"
    DnsSort.HEALTHY -> "dnslab_s_healthy"
    DnsSort.TUNNEL -> "dnslab_s_tunnel"
    DnsSort.NAME -> "dnslab_s_name"
}

private fun healthRank(v: DnsVerdict?): Int = when (v?.health) {
    DnsHealth.GOOD -> 0
    DnsHealth.FAIR -> 1
    null, DnsHealth.UNTESTED -> 2
    DnsHealth.BAD -> 3
}

/**
 * Writes the export to a file the user's own share sheet can pick up.
 *
 * Through FileProvider, and started by the user - nothing is uploaded. The
 * addresses someone has collected are their own, and a screen that posted
 * them anywhere would be doing exactly what a VPN app must not.
 */
private suspend fun shareExport(
    context: android.content.Context,
    labStore: DnsResolverStore
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "ghajar-dns-resolvers.txt")
        file.writeText(labStore.exportText())
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrElse {
        GhajarLog.e("GhajarDnsLab", "export failed: ${it.javaClass.simpleName}")
        false
    }
}

@Composable
private fun DnsLabStats(
    total: Int,
    verdicts: Map<String, DnsVerdict>,
    progress: DnsScanProgress,
    lang: Lang
) {
    val t: (String) -> String = { Strings.get(lang, it) }
    val good = verdicts.values.count { it.health == DnsHealth.GOOD }
    val poisoned = verdicts.values.count { it.poisoned }
    val best = verdicts.values
        .filter { it.health == DnsHealth.GOOD && !it.poisoned }
        .mapNotNull { it.latencyMs }
        .minOrNull()
    StatStrip(
        listOf(
            StatCell(t("dnslab_count"), localizeDigits("$total", lang), ghajarColors.primary),
            StatCell(
                t("dnslab_healthy"),
                localizeDigits("$good", lang),
                if (good > 0) ghajarColors.good else ghajarColors.textMuted
            ),
            StatCell(
                t("dnslab_poisoned"),
                localizeDigits("$poisoned", lang),
                if (poisoned > 0) ghajarColors.error else ghajarColors.textMuted
            ),
            StatCell(
                t("dnslab_best"),
                best?.let { localizeDigits("$it", lang) + " " + t("unit_ms") } ?: "—",
                ghajarColors.highlight,
                sub = if (progress.running) t("dnslab_live") else null
            )
        )
    )
}

@Composable
private fun DnsResolverRow(
    resolver: DnsResolver,
    verdict: DnsVerdict?,
    isChosen: Boolean,
    lang: Lang,
    onChoose: () -> Unit,
    onForget: () -> Unit
) {
    val t: (String) -> String = { Strings.get(lang, it) }
    val c = ghajarColors
    var expanded by remember { mutableStateOf(false) }

    val tint = when (verdict?.health) {
        DnsHealth.GOOD -> if (verdict.poisoned) c.error else c.successGlow
        DnsHealth.FAIR -> c.warning
        DnsHealth.BAD -> c.error
        null, DnsHealth.UNTESTED -> c.textMuted
    }
    val background by animateColorAsState(
        if (isChosen) c.primary.copy(alpha = 0.14f) else c.secondaryCard,
        tween(220),
        label = "dnsRow"
    )

    // Choosing is allowed only for a resolver that answered honestly. Offering
    // the poisoned ones as a setting would be offering the bug.
    val choosable = verdict != null &&
        verdict.health != DnsHealth.BAD &&
        verdict.health != DnsHealth.UNTESTED &&
        !verdict.poisoned && !verdict.mismatched

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(background)
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
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
                    isChosen -> Icon(
                        Icons.Filled.Check, null, tint = c.primary,
                        modifier = Modifier.size(17.dp)
                    )
                    verdict?.poisoned == true -> Icon(
                        Icons.Filled.Warning, null, tint = tint,
                        modifier = Modifier.size(17.dp)
                    )
                    else -> Icon(
                        Icons.Filled.Dns, null, tint = tint,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        resolver.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                    if (verdict?.tunnelReady == true) {
                        Text(
                            t("dnslab_tag_tunnel"),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.info,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.info.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    mixedText(resolver.address),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    mixedText(summaryLine(verdict, resolver, t, lang)),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    maxLines = 2
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SlabDivider()
                DetailLine(t("dnslab_d_latency"), verdict?.latencyMs?.let {
                    localizeDigits("$it", lang) + " " + t("unit_ms")
                })
                DetailLine(t("dnslab_d_success"), verdict?.let {
                    localizeDigits("${it.successPercent}", lang) + "٪"
                })
                DetailLine(t("dnslab_d_loss"), verdict?.let {
                    localizeDigits("${it.lossPercent}", lang) + "٪"
                })
                // Three-state on purpose: "not tested" is not "no".
                DetailLine(t("dnslab_d_recursive"), triText(verdict?.recursive, t))
                DetailLine(t("dnslab_d_udp"), triText(verdict?.udpOk, t))
                DetailLine(t("dnslab_d_tcp"), triText(verdict?.tcpOk, t))
                DetailLine(t("dnslab_d_tunnel"), triText(verdict?.tunnelReady, t))
                DetailLine(t("dnslab_d_note"), verdict?.note?.takeIf { it.isNotBlank() })
                DetailLine(t("dnslab_d_when"), verdict?.lastTestedAt?.takeIf { it > 0 }?.let {
                    minutesAgo(it, t, lang)
                })
                if (resolver.local) {
                    Text(
                        t("dnslab_local_note"),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.warning
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GhostPill(
                        text = if (isChosen) t("dnslab_in_use") else t("dnslab_use"),
                        onClick = onChoose,
                        enabled = choosable && !isChosen,
                        modifier = Modifier.weight(1f)
                    )
                    GhostPill(
                        text = t("dnslab_forget"),
                        onClick = onForget,
                        accent = c.error,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!choosable && verdict != null && verdict.health != DnsHealth.UNTESTED) {
                    Text(
                        t("dnslab_why_not_usable"),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textMuted
                    )
                }
            }
        }
    }
}

private fun summaryLine(
    verdict: DnsVerdict?,
    resolver: DnsResolver,
    t: (String) -> String,
    lang: Lang
): String = when {
    verdict == null || verdict.health == DnsHealth.UNTESTED ->
        resolver.note.ifBlank { t("dnslab_untested") }
    verdict.poisoned -> t("dnslab_says_poisoned")
    verdict.mismatched -> t("dnslab_result_mismatch")
    verdict.health == DnsHealth.BAD -> t("dnslab_result_failed").format(verdict.note)
    else -> buildString {
        append(localizeDigits("${verdict.latencyMs ?: 0}", lang))
        append(' ').append(t("unit_ms"))
        append(" · ").append(localizeDigits("${verdict.successPercent}", lang)).append("٪")
        if (verdict.recursive == false) append(" · ").append(t("dnslab_tag_norecurse"))
    }
}

private fun triText(value: Boolean?, t: (String) -> String): String = when (value) {
    true -> t("dnslab_yes")
    false -> t("dnslab_no")
    // The third state is the point: a resolver whose recursion was never
    // tested and one that refused to recurse are different findings, and this
    // is the line that keeps them apart on screen.
    null -> t("dnslab_not_tested")
}

/**
 * How long ago, in the coarsest unit that is still true.
 *
 * Written here rather than reaching for a shared helper because there isn't
 * one - and a wall-clock timestamp would be the wrong answer anyway: what the
 * reader wants to know is whether this measurement is stale, not when it
 * happened.
 */
private fun minutesAgo(at: Long, t: (String) -> String, lang: Lang): String {
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> t("dnslab_just_now")
        minutes < 60 -> t("dnslab_minutes_ago").format(localizeDigits("$minutes", lang))
        minutes < 1440 -> t("dnslab_hours_ago").format(localizeDigits("${minutes / 60}", lang))
        else -> t("dnslab_days_ago").format(localizeDigits("${minutes / 1440}", lang))
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val c = ghajarColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            mixedText(value),
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary
        )
    }
}

/** A selectable chip, sized for a scrolling row of them. */
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = ghajarColors
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) c.onPrimary else c.textSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(GhajarRadius.pill))
            .background(if (selected) c.primary else c.secondaryCard)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/** A square icon-over-label tile, for the three ways in. */
@Composable
private fun GlyphTileLite(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = ghajarColors
    Column(
        modifier
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(c.card)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Runs [block] once for the lifetime of this composition.
 *
 * LaunchedEffect(Unit) directly in the screen body would restart on every
 * configuration change and re-read the file each time; this is the same shape
 * with the intent in its name.
 */
@Composable
private fun LaunchedEffectOnce(block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}

/**
 * Adding one resolver by hand.
 *
 * The transport is chosen here rather than inferred, and the form refuses the
 * combinations that cannot work: DoT needs a hostname because a certificate
 * has to be bound to a name, and DoH needs an https URL. The alternative -
 * accepting an IP for either and turning verification off - is not offered,
 * and the helper text says which is missing rather than just greying the
 * button out.
 */
@Composable
private fun DnsManualDialog(
    onDismiss: () -> Unit,
    onAdd: (DnsResolver) -> Unit
) {
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    val c = ghajarColors
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf(DnsTransport.UDP) }

    val candidate = DnsResolverImport.manual(address, port, transport, name)
    val problem = when {
        address.isBlank() -> null
        candidate != null -> null
        transport == DnsTransport.DOT -> t("dnslab_need_hostname")
        transport == DnsTransport.DOH -> t("dnslab_need_url")
        port.isNotBlank() && port.toIntOrNull() !in 1..65535 -> t("dnslab_bad_port")
        else -> t("dnslab_bad_address")
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            color = c.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(GhajarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
            ) {
                Text(
                    t("dnslab_manual_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DnsTransport.entries.forEach { tr ->
                        Chip(
                            label = tr.name,
                            selected = transport == tr,
                            onClick = { transport = tr }
                        )
                    }
                }
                SkinField(
                    value = address,
                    onValueChange = { address = it },
                    label = when (transport) {
                        DnsTransport.DOH -> t("dnslab_f_url")
                        DnsTransport.DOT -> t("dnslab_f_hostname")
                        else -> t("dnslab_f_address")
                    },
                    placeholder = when (transport) {
                        DnsTransport.DOH -> "https://dns.example/dns-query"
                        DnsTransport.DOT -> "dns.example"
                        else -> "1.1.1.1"
                    },
                    isError = problem != null,
                    helper = problem
                )
                if (transport == DnsTransport.UDP || transport == DnsTransport.TCP) {
                    SkinField(
                        value = port,
                        onValueChange = { port = it.filter { ch -> ch.isDigit() } },
                        label = t("dnslab_f_port"),
                        placeholder = "53",
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                }
                SkinField(
                    value = name,
                    onValueChange = { name = it },
                    label = t("dnslab_f_name"),
                    placeholder = t("dnslab_f_name_hint")
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GhostPill(t("cancel"), onClick = onDismiss, modifier = Modifier.weight(1f))
                    PillButton(
                        t("add"),
                        onClick = { candidate?.let(onAdd) },
                        enabled = candidate != null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * The DNS-only card: use the chosen resolver for lookups, with no tunnel.
 *
 * Every word here is chosen so nobody can read it as a VPN. It says "DNS
 * active", it names the resolver, it counts the queries it actually answered -
 * and the count is the honest part, because a mode that claims to be on while
 * serving nothing is indistinguishable from one that works until you look.
 */
@Composable
private fun DnsOnlyCard(
    phase: DnsOnlyPhase,
    resolverInUse: String?,
    served: Long,
    privateDnsConflict: Boolean,
    tunnelUp: Boolean,
    chosenAddress: String,
    lang: Lang,
    onStart: (String) -> Unit,
    onStop: () -> Unit
) {
    val t: (String) -> String = { Strings.get(lang, it) }
    val c = ghajarColors
    val accent = when (phase) {
        DnsOnlyPhase.ACTIVE -> if (privateDnsConflict) c.warning else c.good
        DnsOnlyPhase.FAILED -> c.error
        DnsOnlyPhase.STARTING -> c.highlight
        DnsOnlyPhase.OFF -> c.primary
    }

    Slab(accent = accent, spacing = GhajarSpacing.sm) {
        Text(
            when (phase) {
                DnsOnlyPhase.ACTIVE -> t("dnsonly_active").format(resolverInUse.orEmpty())
                DnsOnlyPhase.STARTING -> t("dnsonly_starting")
                DnsOnlyPhase.FAILED -> t("dnsonly_failed")
                DnsOnlyPhase.OFF -> t("dnsonly_off")
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            t("dnsonly_explain"),
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary
        )
        if (phase == DnsOnlyPhase.ACTIVE) {
            // The number that makes the claim checkable. Zero while active
            // means something is wrong - most often the Private DNS below.
            Text(
                t("dnsonly_served").format(localizeDigits("$served", lang)),
                style = MaterialTheme.typography.labelSmall,
                color = if (served > 0) c.good else c.warning
            )
        }
        if (privateDnsConflict) {
            Text(
                t("dnsonly_private_dns"),
                style = MaterialTheme.typography.labelSmall,
                color = c.warning
            )
        }
        when {
            // The tunnel owns the tun. Said rather than attempted: Android
            // gives the tun to whoever established it last, so trying would
            // take down the tunnel the user is relying on.
            tunnelUp -> Text(
                t("dnsonly_tunnel_first"),
                style = MaterialTheme.typography.labelSmall,
                color = c.warning
            )
            phase == DnsOnlyPhase.ACTIVE || phase == DnsOnlyPhase.STARTING -> GhostPill(
                text = t("dnsonly_stop"),
                onClick = onStop,
                accent = c.error
            )
            chosenAddress.isBlank() -> Text(
                t("dnsonly_choose_first"),
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted
            )
            else -> PillButton(
                text = t("dnsonly_start"),
                onClick = { onStart(chosenAddress) },
                icon = Icons.Filled.Dns
            )
        }
    }
}

/**
 * The DNS Tunnel profile, and an honest account of its state.
 *
 * This section exists in full even though this build ships no tunnel engine,
 * and that is deliberate: the profile, its validation, the per-resolver
 * tunnel-path test and the selection rules are all real, and the one missing
 * piece is named rather than papered over. See third_party/dnstt/README.md for
 * exactly what has to be added and why it could not be added here.
 *
 * What it will not do, ever: show a tunnel as connected on the strength of a
 * saved profile. "Configured" and "carrying traffic" are different facts.
 */
@Composable
private fun DnsTunnelSection(
    store: ConfigStore,
    labStore: DnsResolverStore,
    phase: DnsTunnelPhase,
    detail: String,
    engineAvailable: Boolean,
    lang: Lang
) {
    val t: (String) -> String = { Strings.get(lang, it) }
    val c = ghajarColors
    val domain by store.dnsTunnelDomain.collectAsState()
    val key by store.dnsTunnelKey.collectAsState()
    val resolverId by store.dnsTunnelResolver.collectAsState()
    val name by store.dnsTunnelName.collectAsState()
    val reconnect by store.dnsTunnelAutoReconnect.collectAsState()
    val resolvers by labStore.resolvers.collectAsState()
    val verdicts by labStore.verdicts.collectAsState()
    var open by remember { mutableStateOf(false) }

    val chosenResolver = resolvers.firstOrNull { it.id == resolverId }
    val complete = domain.isNotBlank() && key.isNotBlank() && chosenResolver != null

    Slab(
        accent = if (engineAvailable) c.primary else c.warning,
        spacing = GhajarSpacing.sm
    ) {
        // The headline state, and the order matters: a missing engine is
        // reported before an incomplete profile, because filling the profile
        // in would not help until the engine is there.
        Text(
            when {
                !engineAvailable -> t("dnstun_engine_missing")
                !complete -> t("dnstun_incomplete")
                phase == DnsTunnelPhase.CARRYING -> t("dnstun_carrying")
                phase == DnsTunnelPhase.LISTENING -> t("dnstun_listening")
                phase == DnsTunnelPhase.FAILED -> t("dnstun_failed").format(detail)
                else -> t("dnstun_ready")
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (engineAvailable && complete) c.primary else c.warning
        )
        Text(
            t("dnstun_explain"),
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary
        )
        if (!engineAvailable) {
            Text(
                t("dnstun_engine_note"),
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted
            )
        }

        GhostPill(
            text = if (open) t("dnstun_hide") else t("dnstun_edit"),
            onClick = { open = !open }
        )

        AnimatedVisibility(visible = open) {
            Column(verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                SlabDivider()
                SkinField(
                    value = name,
                    onValueChange = { store.setDnsTunnelName(it) },
                    label = t("dnstun_f_name"),
                    placeholder = t("dnslab_f_name_hint")
                )
                SkinField(
                    value = domain,
                    onValueChange = { store.setDnsTunnelDomain(it) },
                    label = t("dnstun_f_domain"),
                    placeholder = "t.example.com",
                    helper = t("dnstun_f_domain_help"),
                    isError = domain.isNotBlank() && !DnsResolverImport.looksHostname(domain)
                )
                SkinField(
                    value = key,
                    onValueChange = { store.setDnsTunnelKey(it) },
                    label = t("dnstun_f_key"),
                    helper = t("dnstun_f_key_help"),
                    singleLine = false,
                    minLines = 2
                )

                Text(
                    t("dnstun_f_resolver"),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary
                )
                Text(
                    chosenResolver?.let { "${it.name} · ${it.address}" }
                        ?: t("dnstun_no_resolver"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chosenResolver == null) c.textMuted else c.primary
                )
                // Only resolvers whose tunnel path was actually tested and
                // worked are offered. Answering an ordinary query proves
                // nothing about carrying a tunnel, and offering the rest here
                // would be offering a list that mostly cannot work.
                val usable = remember(resolvers, verdicts) {
                    resolvers.filter { DnsTunnelController.resolverLooksUsable(verdicts[it.id]) }
                }
                if (usable.isEmpty()) {
                    Text(
                        t("dnstun_none_tested"),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.warning
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        usable.take(24).forEach { r ->
                            Chip(
                                label = r.address,
                                selected = r.id == resolverId,
                                onClick = { store.setDnsTunnelResolver(r.id) }
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            t("dnstun_reconnect"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary
                        )
                        Text(
                            t("dnstun_reconnect_sub"),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textSecondary
                        )
                    }
                    SkinSwitch(
                        checked = reconnect,
                        onCheckedChange = { store.setDnsTunnelAutoReconnect(it) }
                    )
                }

                Text(
                    t("dnstun_key_not_backed_up"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted
                )
            }
        }
    }
}

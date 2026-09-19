package net.gozar.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Choosing where Psiphon comes out.
 *
 * This replaces a two-character text field, which asked the user to know both
 * that DE means Germany and - the part that actually bites - whether Psiphon
 * has a server there at all. It does not reject a region it cannot serve: the
 * tunnel simply never establishes, which looks identical to the network being
 * blocked. So the list comes from the engine (see PsiphonRegions) and typing
 * is no longer how you say it.
 */
@Composable
fun PsiphonCountryRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    var open by remember { mutableStateOf(false) }

    // The engine only reports its regions while a tunnel is coming up, which
    // is far too late for a screen being looked at now - so the last reported
    // list is restored from disk on the way in.
    LaunchedEffect(Unit) { PsiphonRegions.restore(context) }

    val code = selected.trim().uppercase()
    SlabRow(
        modifier = modifier,
        title = t("psi_exit_country"),
        subtitle = if (code.isEmpty()) t("psi_exit_auto")
        else (flagEmoji(code).let { if (it.isEmpty()) "" else "$it " } +
            PsiphonRegions.displayName(code, lang)),
        icon = if (code.isEmpty()) Icons.Filled.Bolt else Icons.Filled.Public,
        accent = if (code.isEmpty()) ghajarColors.textMuted else ghajarColors.primary,
        chevron = true,
        onClick = { open = true }
    )

    if (open) {
        PsiphonCountryDialog(
            selected = code,
            onPick = { onSelect(it); open = false },
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun PsiphonCountryDialog(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    val regions by PsiphonRegions.regions.collectAsState()
    val fromEngine by PsiphonRegions.fromEngine.collectAsState()
    var query by remember { mutableStateOf("") }

    // Sorted by the name the user reads, not by the two-letter code: an
    // alphabetical list of codes puts Germany under D and Austria under A in
    // English but scatters them meaninglessly in Persian.
    val named = remember(regions, lang) {
        regions.map { it to PsiphonRegions.displayName(it, lang) }
            .sortedBy { it.second }
    }
    val shown = remember(named, query) {
        val needle = query.trim()
        if (needle.isEmpty()) named
        else named.filter { (code, name) ->
            name.contains(needle, ignoreCase = true) ||
                code.contains(needle, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = ghajarColors.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            // BoxWithConstraints so the list is bounded by the dialog's own
            // window rather than a guess at screen height - the same mistake
            // that put the renewal dialog's buttons on top of its last row.
            BoxWithConstraints {
                val maxListHeight = (maxHeight * 0.62f)
                Column(Modifier.padding(GhajarSpacing.md)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t("psi_pick_country"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ghajarColors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = t("cancel"),
                                tint = ghajarColors.textMuted
                            )
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(t("psi_search"), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = GhajarSpacing.sm)
                    )

                    if (!fromEngine) {
                        // Said out loud rather than hidden: until Psiphon has
                        // reported once, this list is this app's guess, and a
                        // country on it may simply have no server.
                        Text(
                            t("psi_regions_provisional"),
                            style = MaterialTheme.typography.labelSmall,
                            color = ghajarColors.warning,
                            modifier = Modifier.padding(bottom = GhajarSpacing.sm)
                        )
                    }

                    LazyColumn(Modifier.heightIn(max = maxListHeight)) {
                        item {
                            CountryRow(
                                flag = null,
                                label = t("psi_exit_auto"),
                                note = t("psi_exit_auto_note"),
                                checked = selected.isEmpty(),
                                onClick = { onPick("") }
                            )
                        }
                        items(shown, key = { it.first }) { (code, name) ->
                            CountryRow(
                                flag = flagEmoji(code).ifEmpty { null },
                                label = name,
                                note = code,
                                checked = selected == code,
                                onClick = { onPick(code) }
                            )
                        }
                        if (shown.isEmpty() && query.isNotBlank()) {
                            item {
                                Text(
                                    t("psi_no_country"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ghajarColors.textMuted,
                                    modifier = Modifier.padding(GhajarSpacing.md)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(
    flag: String?,
    label: String,
    note: String?,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
    ) {
        if (flag != null) {
            Text(flag, fontSize = 20.sp)
        } else {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = ghajarColors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = ghajarColors.textPrimary
            )
            if (!note.isNullOrBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = ghajarColors.textMuted
                )
            }
        }
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = ghajarColors.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

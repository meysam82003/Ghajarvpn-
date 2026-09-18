package net.gozar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The diagnosis, shown as findings rather than as a verdict.
 *
 * The user pressed this because the app already told them something unhelpful.
 * So the dialog shows every check it ran and how each one came out - a person
 * who can see that the internet passed and the server port failed does not
 * need to be told what to conclude - and then names the one cause worth acting
 * on, with the remedy for it.
 *
 * The checks live in [ConnectDoctor]; nothing here decides a verdict.
 */
@Composable
fun ConnectDoctorDialog(
    config: ProxyConfig?,
    ovpnProfile: GhajarOvpnProfile?,
    engineError: String?,
    tunnelUp: Boolean,
    onDismiss: () -> Unit
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }

    var report by remember { mutableStateOf<DoctorReport?>(null) }
    var run by remember { mutableStateOf(0) }

    LaunchedEffect(run) {
        report = null
        report = ConnectDoctor.run(config, ovpnProfile, engineError, tunnelUp)
    }

    val current = report
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("doc_title")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
            ) {
                if (current == null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = c.primary,
                            strokeWidth = 2.dp
                        )
                        Text(t("doc_running"), color = c.textSecondary)
                    }
                } else {
                    current.findings.forEach { f -> DoctorRow(f, t) }

                    // The answer the user came for: one cause, one remedy.
                    val cause = current.causeKey
                        ?.let { key -> current.findings.firstOrNull { it.titleKey == key } }
                    if (cause == null) {
                        Text(
                            t("doc_all_ok"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary
                        )
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(GhajarRadius.lg))
                                .background(verdictColor(cause.verdict, c).copy(alpha = 0.10f))
                                .padding(GhajarSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                t("doc_cause"),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textMuted
                            )
                            Text(
                                t(cause.titleKey),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = verdictColor(cause.verdict, c)
                            )
                            cause.remedyKey?.let { key ->
                                Text(
                                    t("doc_remedy"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.textMuted,
                                    modifier = Modifier.padding(top = GhajarSpacing.sm)
                                )
                                Text(
                                    t(key),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { PillButton(t("doc_close"), onDismiss) },
        dismissButton = {
            GhostPill(
                t("doc_again"),
                onClick = { run++ },
                enabled = current != null
            )
        }
    )
}

/** One check: its glyph, its name, and whatever it measured. */
@Composable
private fun DoctorRow(finding: DoctorFinding, t: (String) -> String) {
    val c = ghajarColors
    val tint = verdictColor(finding.verdict, c)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                verdictIcon(finding.verdict),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            t(finding.titleKey),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            modifier = Modifier.weight(1f)
        )
        // Detail is either a measured value or a key for a fixed phrase; a key
        // never contains a space or a dot, which is how the two are told apart.
        val detail = finding.detail
        val shown = if (detail.startsWith("doc_")) t(detail) else detail
        Text(
            mixedText(shown),
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary
        )
    }
}

private fun verdictColor(verdict: DoctorVerdict, c: GhajarPalette): Color = when (verdict) {
    DoctorVerdict.PASS -> c.successGlow
    DoctorVerdict.WARN -> c.warning
    DoctorVerdict.FAIL -> c.error
    DoctorVerdict.SKIPPED -> c.textMuted
}

private fun verdictIcon(verdict: DoctorVerdict): ImageVector = when (verdict) {
    DoctorVerdict.PASS -> Icons.Filled.Check
    DoctorVerdict.WARN -> Icons.Filled.PriorityHigh
    DoctorVerdict.FAIL -> Icons.Filled.Close
    DoctorVerdict.SKIPPED -> Icons.Filled.Remove
}

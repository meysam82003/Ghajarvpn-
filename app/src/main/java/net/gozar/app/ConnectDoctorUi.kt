package net.gozar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Build
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    onDismiss: () -> Unit,
    /** Dials a config; given, the dialog offers "fix it for me". */
    onConnect: ((ProxyConfig) -> Unit)? = null
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    val fa = lang == Lang.FA
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var report by remember { mutableStateOf<DoctorReport?>(null) }
    var run by remember { mutableStateOf(0) }
    var fixing by remember { mutableStateOf(false) }
    var fixNote by remember { mutableStateOf<String?>(null) }

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
                DoctorBody(current, allOkKey = "doc_all_ok")
                if (current != null && current.causeKey != null && onConnect != null) {
                    PillButton(
                        if (fixing) (if (fa) "در حال رفع مشکل…" else "Fixing…")
                        else (if (fa) "رفع خودکار مشکل" else "Fix it for me"),
                        onClick = {
                            if (fixing) return@PillButton
                            fixing = true
                            fixNote = null
                            scope.launch {
                                fixNote = ConnectAutoFix.apply(context, current, config, fa, onConnect)
                                fixing = false
                                // Let the redial settle, then measure again.
                                kotlinx.coroutines.delay(4_000)
                                run++
                            }
                        },
                        enabled = !fixing,
                        icon = androidx.compose.material.icons.Icons.Filled.Build,
                        minHeight = 46.dp
                    )
                }
                fixNote?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.primary)
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

/**
 * A report's findings and its one cause, shared by every diagnosis in the app.
 *
 * A null [report] is the running state, so a caller never has to render the
 * spinner itself.
 */
@Composable
fun ColumnScope.DoctorBody(report: DoctorReport?, allOkKey: String) {
    val c = ghajarColors
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }

    if (report == null) {
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
        return
    }

    report.findings.forEach { f -> DoctorRow(f, t) }

    // The answer the user came for: one cause, one remedy.
    val cause = report.causeKey
        ?.let { key -> report.findings.firstOrNull { it.titleKey == key } }
    if (cause == null) {
        Text(
            t(allOkKey),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary
        )
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.lg))
            .background(verdictColor(cause.verdict, c).copy(alpha = 0.10f))
            .padding(GhajarSpacing.md),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(t("doc_cause"), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
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
            Text(t(key), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        }
    }
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

/**
 * Acts on a diagnosis instead of only describing it. Each cause gets the one
 * remedy the app can apply by itself; the rest get the system screen that
 * fixes them. Returns what was done, in the user's language.
 */
object ConnectAutoFix {
    suspend fun apply(
        context: android.content.Context,
        report: DoctorReport,
        config: ProxyConfig?,
        fa: Boolean,
        onConnect: (ProxyConfig) -> Unit
    ): String {
        val store = ConfigStore.get(context)
        val cause = report.causeKey
        val engine = report.findings.firstOrNull { it.titleKey == "doc_engine" }
        fun tr(a: String, b: String) = if (fa) a else b
        return when (cause) {
            "doc_internet" -> {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                tr("اینترنت گوشی وصل نیست؛ تنظیمات شبکه باز شد. وای‌فای یا دیتا را روشن کن.",
                    "The phone has no internet; network settings opened. Turn on Wi-Fi or mobile data.")
            }
            "doc_dns" -> {
                store.setCustomDns("1.1.1.1")
                store.setEncryptedDns(true)
                config?.let(onConnect)
                tr("DNS روی 1.1.1.1 رمزنگاری‌شده تنظیم شد و اتصال دوباره برقرار می‌شود.",
                    "DNS switched to encrypted 1.1.1.1 and the tunnel is redialling.")
            }
            "doc_server_dns", "doc_server_port" -> switchToFastest(store, config, fa, onConnect)
            "doc_engine" -> when (engine?.remedyKey) {
                "doc_engine_fix_auth" -> tr(
                    "نام کاربری یا رمز این سرور رد شد؛ این مورد خودکار قابل رفع نیست. اشتراک را به‌روزرسانی کن.",
                    "This server rejected the credentials; that cannot be fixed automatically. Update the subscription."
                )
                "doc_engine_fix_timeout" -> switchToFastest(store, config, fa, onConnect)
                else -> {
                    config?.let(onConnect)
                    tr("هسته از نو راه‌اندازی شد و اتصال دوباره برقرار می‌شود.", "The engine restarted and is redialling.")
                }
            }
            else -> {
                config?.let(onConnect)
                tr("اتصال از نو برقرار می‌شود.", "Redialling.")
            }
        }
    }

    /** Pings the other servers of the same subscription and dials the fastest. */
    private suspend fun switchToFastest(
        store: ConfigStore,
        current: ProxyConfig?,
        fa: Boolean,
        onConnect: (ProxyConfig) -> Unit
    ): String = kotlinx.coroutines.coroutineScope {
        val pool = store.configs.value
            .filter { it.id != current?.id }
            .filter { current?.subId.isNullOrBlank() || it.subId == current?.subId }
            .ifEmpty { store.configs.value.filter { it.id != current?.id } }
            .take(40)
        val results = pool.map { cfg ->
            async(kotlinx.coroutines.Dispatchers.IO) {
                cfg to (runCatching { Pinger.ping(cfg.address, cfg.port) }.getOrNull() as? PingResult.Ok)?.ms
            }
        }.awaitAll()
        val best = results.filter { it.second != null }.minByOrNull { it.second!! }
        if (best == null) {
            current?.let(onConnect)
            if (fa) "سرور دیگری در دسترس نبود؛ همین سرور دوباره امتحان می‌شود."
            else "No other server answered; retrying this one."
        } else {
            store.setSelectedId(best.first.id)
            onConnect(best.first)
            if (fa) "به سرور سریع‌تر «${BrandConfig.sanitizePublicText(best.first.name)}» (${best.second} ms) وصل می‌شود."
            else "Switching to the faster server \"${BrandConfig.sanitizePublicText(best.first.name)}\" (${best.second} ms)."
        }
    }
}

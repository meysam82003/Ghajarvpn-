package net.gozar.app.plugins

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.gozar.app.*

class PluginActivity : ComponentActivity() {
    private var connectId: String? = null
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) connectId?.let { connect(it) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectId = savedInstanceState?.getString("connectId") ?: intent.getStringExtra("configId")
        setContent { MaterialTheme { Surface { PluginScreen(this, connectId, ::connect) } } }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("connectId", connectId); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); PluginManager.get(this).reconcile() }
    private fun connect(id: String) {
        connectId = id
        val config = ConfigStore.get(this).configs.value.firstOrNull { it.id == id } ?: return
        val main = ConfigQuickConnectBridge.activity
        if (main != null) { main.quickConnect(config); finish(); return }
        val consent = android.net.VpnService.prepare(this)
        if (consent != null) { permission.launch(consent); return }
        lifecycleScope.launch {
            val outcome = PluginRuntime.launch(applicationContext, config)
            if (outcome == LaunchOutcome.STARTED) finish()
            else Toast.makeText(this@PluginActivity, "اتصال آغاز نشد؛ وضعیت افزونه و اتصال فعلی را بررسی کنید.", Toast.LENGTH_LONG).show()
        }
    }
    companion object {
        fun open(context: Context, config: ProxyConfig? = null) {
            context.startActivity(Intent(context, PluginActivity::class.java).putExtra("configId", config?.id))
        }
    }
}

/** Always visible, even with an empty release catalog. No package enumeration or engine binding here. */
@Composable
fun PluginEntryButton() {
    val context = LocalContext.current
    OutlinedButton(onClick = { PluginActivity.open(context) }, modifier = Modifier.fillMaxWidth()) { Text("افزونه‌ها · ShadowQUIC / Mihomo") }
}

@Composable
private fun PluginScreen(activity: PluginActivity, configId: String?, connect: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val manager = remember { PluginManager.get(activity) }
    val revision by manager.revision.collectAsState()
    val store = remember { ConfigStore.get(activity) }
    val configs by store.configs.collectAsState()
    val requested = configs.firstOrNull { it.id == configId }
    var autoConnect by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<String?>(null) }
    var payload by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("") }
    val requestedProfile = requested?.let { runCatching { PluginProfiles.read(it) }.getOrNull() }
    LaunchedEffect(revision, autoConnect) {
        if (autoConnect && requestedProfile != null && manager.active(requestedProfile.id) != null) {
            autoConnect = false; configId?.let(connect)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("مدیریت افزونه‌ها", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = { activity.finish() }) { Text("بازگشت") }
        Text("هسته‌های داخلی فعلی بدون نصب افزونه کار می‌کنند. فقط APK تأییدشده با رضایت شما در Android نصب می‌شود.")
        if (requested != null) {
            Text(if (requestedProfile != null && manager.active(requestedProfile.id) != null) "کانفیگ ذخیره‌شده آمادهٔ اتصال با افزونه است." else PluginProfiles.requirement(requested).orEmpty())
            TextButton(onClick = { requestedProfile?.let { editor = it.id; payload = it.payload; editingId = requested.id } }) { Text("ویرایش کانفیگ کامل") }
            Button(enabled = requestedProfile != null && (manager.latest(requestedProfile.id) != null || manager.active(requestedProfile.id) != null), onClick = {
                requestedProfile?.let { p ->
                    if (manager.active(p.id) != null) configId?.let(connect)
                    else { autoConnect = true; manager.requestInstall(activity, p.id) }
                }
            }) { Text(if (requestedProfile != null && manager.active(requestedProfile.id) != null) "اتصال" else "نصب و اتصال") }
        }
        if (notice.isNotBlank()) Text(notice)
        PluginCatalog.candidates.forEach { candidate ->
            val view = remember(revision, candidate.id) { manager.view(candidate.id) }
            val active = remember(revision, candidate.id) { manager.active(candidate.id) }
            val busy = view.state in setOf(PluginState.DOWNLOADING, PluginState.VERIFYING, PluginState.INSTALLING)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var helpOpen by remember(candidate.id) { mutableStateOf(false) }
                    Text(candidate.name, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { helpOpen = !helpOpen }) { Text("راهنمای استفاده") }
                    if (helpOpen) Text(PluginHelp.text[candidate.id] ?: "هنوز برای استفادهٔ عمومی آماده نیست.")
                    Text(stateLabel(view.state) + (view.activeVersion?.let { " · v$it" } ?: ""))
                    view.release?.let { Text("${it.size} بایت · API ${it.apiVersion} · ${it.abis.joinToString()}") }
                        ?: Text("کاندیدا؛ هنوز APK سازگار و امضاشدهٔ تأییدشده منتشر نشده است. اندازه پس از بسته‌بندی مشخص می‌شود.")
                    if (view.error.isNotBlank()) Text(view.error, color = MaterialTheme.colorScheme.error)
                    // Only verified installed capabilities determine available settings; no protocol-name if/else tree.
                    val capabilities = active?.capabilities
                    if (capabilities != null) Text(capabilities.names().joinToString(" · ") { it.removePrefix("supports") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = view.release != null && !busy && !PluginRuntime.isUsing(candidate.id), onClick = { manager.requestInstall(activity, candidate.id) }) {
                            Text(when (view.state) { PluginState.UPDATE_AVAILABLE -> "بروزرسانی"; PluginState.FAILED -> "تلاش مجدد"; else -> "نصب" })
                        }
                        OutlinedButton(onClick = { editor = candidate.id; payload = ""; editingId = null }) { Text(if (active != null) "افزودن سرور" else "ذخیرهٔ کانفیگ") }
                    }
                    if (view.state == PluginState.INSTALLING) TextButton(onClick = { manager.continueInstall(activity, candidate.id) }) { Text("ادامهٔ تأیید نصب در Android") }
                    if (active?.capabilities?.supportsConnectionTest == true) {
                        TextButton(enabled = PluginRuntime.isUsing(candidate.id), onClick = { scope.launch {
                            notice = runCatching { "زمان اتصال از داخل تونل: ${PluginRuntime.test(candidate.id)} ms" }
                                .getOrElse { "آزمون عبور اینترنت موفق نبود؛ تنظیمات سرور و شبکه را بررسی کنید." }
                        } }) { Text("آزمون اتصال فعلی") }
                    }
                    if (active != null) {
                        Row {
                            TextButton(enabled = !busy && !PluginRuntime.isUsing(candidate.id), onClick = { manager.repair(candidate.id) }) { Text("ترمیم") }
                            TextButton(enabled = !busy && !PluginRuntime.isUsing(candidate.id), onClick = { manager.rollback(candidate.id) }) { Text("نسخهٔ قبلی") }
                            TextButton(enabled = !busy && !PluginRuntime.isUsing(candidate.id), onClick = { runCatching { manager.remove(activity, candidate.id) }.onFailure { notice = it.message.orEmpty() } }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }
    editor?.let { id ->
        val candidate = PluginCatalog.candidate(id)!!
        var format by remember(id, editingId) { mutableStateOf(if (editingId != null) requestedProfile?.format ?: candidate.formats.first() else candidate.formats.first()) }
        AlertDialog(onDismissRequest = { editor = null }, title = { Text(candidate.name) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                candidate.formats.forEach { f -> TextButton(onClick = { format = f }) { Text(if (format == f) "✓ $f" else f) } }
                Text("کانفیگ اصلی بدون تغییر ذخیره می‌شود؛ نبودن افزونه مانع ذخیره نیست.")
                val capabilities = manager.active(id)?.capabilities ?: candidate.supported
                if (capabilities.supportsFullConfig) Text("قوانین، providerها، DNS و TUN را در همین کانفیگ کامل تنظیم کنید.")
                if (capabilities.supportsAdvancedAuth) Text("تنظیمات احراز هویت پیشرفتهٔ این افزونه در کانفیگ اصلی نگه‌داری می‌شود.")
                if (format == "mihomo-yaml") TextButton(onClick = {
                    val nodes = ForeignImport.clashNodes(payload)
                    if (nodes.configs.isNotEmpty()) { store.addImported(nodes.configs); notice = "${nodes.configs.size} سرور جداگانه ذخیره شد؛ قوانین کامل فقط در کانفیگ اصلی حفظ می‌شوند." }
                    else notice = "سرور قابل استخراجی یافت نشد."
                }) { Text("استخراج جداگانهٔ سرورها (بدون rules و providers)") }
                OutlinedTextField(value = payload, onValueChange = { payload = it }, label = { Text("کانفیگ کامل") }, minLines = 5, maxLines = 12)
            }
        }, confirmButton = { TextButton(onClick = {
            runCatching {
                val original = configs.firstOrNull { it.id == editingId }
                val created = PluginProfiles.create(id, format, payload)
                if (original == null) store.add(created) else {
                    val extra = original.extraJson()
                    extra.getJSONObject("plugin").put("format", format).put("payload", payload)
                    store.update(original.copy(extra = extra.toString()))
                }
            }
                .onSuccess { editor = null; notice = "کانفیگ ذخیره شد." }
                .onFailure { notice = "کانفیگ نامعتبر است؛ قالب انتخابی و فیلدهای اجباری را بررسی کنید." }
        }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = { editor = null }) { Text("بازگشت") } })
    }
}

private fun stateLabel(state: PluginState) = when (state) {
    PluginState.NOT_INSTALLED -> "نصب نشده"
    PluginState.AVAILABLE -> "آمادهٔ نصب"
    PluginState.DOWNLOADING -> "در حال دریافت"
    PluginState.VERIFYING -> "در حال بررسی امضا و فایل"
    PluginState.INSTALLING -> "در انتظار نصب Android"
    PluginState.INSTALLED -> "آماده"
    PluginState.UPDATE_AVAILABLE -> "بروزرسانی موجود"
    PluginState.INCOMPATIBLE -> "ناسازگار"
    PluginState.BROKEN -> "نیازمند ترمیم"
    PluginState.FAILED -> "عملیات ناموفق؛ کانفیگ محفوظ است"
}

/** Availability is additive to the server row; absent plugins never hide the user's profile. */
@Composable
fun PluginConfigEntry(config: ProxyConfig) {
    val context = LocalContext.current
    val manager = remember { PluginManager.get(context) }
    val revision by manager.revision.collectAsState()
    val profile = remember(config.extra) { runCatching { PluginProfiles.read(config) }.getOrNull() }
    val active = remember(revision, profile?.id) { profile?.let { manager.active(it.id) } }
    TextButton(onClick = { PluginActivity.open(context, config) }) {
        Text(if (active != null) "${active.name} · v${active.version} · آماده" else "${profile?.let { PluginCatalog.candidate(it.id)?.name } ?: "افزونه"} · نصب نشده · نصب و اتصال")
    }
}

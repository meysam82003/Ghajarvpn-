package net.gozar.app.configtoolkit

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.gozar.app.ConfigStore
import java.io.ByteArrayOutputStream
import java.util.UUID

enum class BatchStatus { QUEUED, READING, DETECTING, DECRYPTING, PARSING, VALIDATING, DONE, FAILED, CANCELLED }

data class ToolkitFileState(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val displayName: String,
    val status: BatchStatus = BatchStatus.QUEUED,
    val format: ConfigFormat = ConfigFormat.UNKNOWN,
    val parsed: ParsedConfig? = null,
    val error: String? = null,
    val sourceBytes: ByteArray? = null
)

class ConfigToolkitViewModel(application: Application) : AndroidViewModel(application) {
    private val registry = DecoderRegistry()
    private val _files = MutableStateFlow<List<ToolkitFileState>>(emptyList())
    val files: StateFlow<List<ToolkitFileState>> = _files.asStateFlow()
    private var batch: Job? = null

    fun enqueue(uris: List<Uri>) {
        val known = _files.value.map { it.uri }.toSet()
        val additions = uris.distinct().filterNot { it in known }.map { uri ->
            ToolkitFileState(uri = uri, displayName = displayName(uri))
        }
        if (additions.isEmpty()) return
        _files.value = _files.value + additions
        processQueued()
    }

    fun cancel() {
        batch?.cancel()
        _files.value = _files.value.map { if (it.status in activeStatuses) it.copy(status = BatchStatus.CANCELLED) else it }
    }

    fun retryWithPasskey(id: String, passkey: CharArray) {
        val item = _files.value.firstOrNull { it.id == id } ?: return
        update(id) { it.copy(status = BatchStatus.DECRYPTING, error = null) }
        viewModelScope.launch {
            try {
                val bytes = item.sourceBytes ?: readBounded(item.uri)
                val parsed = withContext(Dispatchers.Default) {
                    registry.decode(ConfigInput(bytes, item.displayName, passkey = passkey))
                }
                update(id) { it.copy(status = BatchStatus.DONE, parsed = parsed, format = parsed.sourceFormat, sourceBytes = bytes) }
            } catch (failure: Exception) {
                update(id) { it.copy(status = BatchStatus.FAILED, error = publicError(failure)) }
            } finally {
                passkey.fill('\u0000')
            }
        }
    }

    fun importProfiles(ids: Set<String>? = null, onDone: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val parsed = _files.value.mapNotNull(ToolkitFileState::parsed)
            val profiles = parsed.flatMap(ParsedConfig::profiles)
            val synthetic = ParsedConfig(ConfigFormat.UNKNOWN, profiles)
            val result = withContext(Dispatchers.IO) {
                GhajarImporter(ConfigStore.get(getApplication())).import(synthetic, ids)
            }
            onDone(result)
        }
    }

    fun editableCopy(id: String, clearPassword: Boolean): ByteArray? {
        val item = _files.value.firstOrNull { it.id == id } ?: return null
        return item.sourceBytes?.let { runCatching { EditableNpvtCopy.create(it, clearPassword) }.getOrNull() }
    }

    private fun processQueued() {
        if (batch?.isActive == true) return
        batch = viewModelScope.launch {
            val ids = _files.value.filter { it.status == BatchStatus.QUEUED }.map { it.id }
            for (id in ids) {
                try {
                    val item = _files.value.firstOrNull { it.id == id } ?: continue
                    update(id) { it.copy(status = BatchStatus.READING) }
                    val bytes = readBounded(item.uri)
                    update(id) { it.copy(status = BatchStatus.DETECTING, sourceBytes = bytes) }
                    val detection = withContext(Dispatchers.Default) {
                        FormatDetector.detect(ConfigInput(bytes, item.displayName))
                    }
                    update(id) { it.copy(format = detection.format, status = BatchStatus.PARSING) }
                    val parsed = withContext(Dispatchers.Default) {
                        registry.decode(ConfigInput(bytes, item.displayName))
                    }
                    update(id) { it.copy(status = BatchStatus.VALIDATING) }
                    update(id) { it.copy(status = BatchStatus.DONE, parsed = parsed) }
                } catch (cancelled: CancellationException) {
                    update(id) { it.copy(status = BatchStatus.CANCELLED) }
                    throw cancelled
                } catch (failure: Exception) {
                    update(id) { it.copy(status = BatchStatus.FAILED, error = publicError(failure)) }
                }
            }
            if (_files.value.any { it.status == BatchStatus.QUEUED }) processQueued()
        }
    }

    private suspend fun readBounded(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val input = resolver.openInputStream(uri) ?: throw ConfigToolkitException.InvalidConfig("فایل باز نشد.")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > DecoderRegistry.MAX_FILE_BYTES) throw ConfigToolkitException.TooLarge(DecoderRegistry.MAX_FILE_BYTES)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment ?: "config" }
    }

    private fun update(id: String, transform: (ToolkitFileState) -> ToolkitFileState) {
        _files.value = _files.value.map { if (it.id == id) transform(it) else it }
    }

    private fun publicError(failure: Exception): String = when (failure) {
        is ConfigToolkitException -> failure.message.orEmpty()
        else -> "پردازش فایل ناموفق بود؛ فایل اصلی تغییری نکرد."
    }

    companion object {
        private val activeStatuses = setOf(
            BatchStatus.QUEUED, BatchStatus.READING, BatchStatus.DETECTING,
            BatchStatus.DECRYPTING, BatchStatus.PARSING, BatchStatus.VALIDATING
        )
    }
}

class ConfigToolkitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[ConfigToolkitViewModel::class.java]
        initialUris(intent).takeIf(List<Uri>::isNotEmpty)?.let(model::enqueue)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFC9A54B), secondary = Color(0xFF62D5B1))) {
                ToolkitScreen(model, onBack = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        initialUris(intent).takeIf(List<Uri>::isNotEmpty)?.let {
            ViewModelProvider(this)[ConfigToolkitViewModel::class.java].enqueue(it)
        }
    }

    private fun initialUris(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)
        Intent.ACTION_SEND -> listOfNotNull(streamUri(intent))
        Intent.ACTION_SEND_MULTIPLE -> if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> emptyList()
    }

    private fun streamUri(intent: Intent): Uri? = if (android.os.Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolkitScreen(model: ConfigToolkitViewModel, onBack: () -> Unit) {
    val files by model.files.collectAsState()
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var passkeyTarget by remember { mutableStateOf<ToolkitFileState?>(null) }
    var passkey by remember { mutableStateOf("") }
    var viewer by remember { mutableStateOf<ParsedConfig?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> model.enqueue(uris) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val data = pendingSave
        if (uri != null && data != null) runCatching {
            model.getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(data) }
        }.onSuccess { notice = "نسخهٔ جدید ذخیره شد؛ فایل اصلی دست‌نخورده ماند." }
        pendingSave = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ابزار کانفیگ", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.FileOpen, "انتخاب فایل") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("انتخاب فایل‌ها") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("پردازش کاملاً روی گوشی انجام می‌شود. هیچ فایل، رمز یا لینک کانفیگی ارسال نمی‌شود.", style = MaterialTheme.typography.bodySmall)
            notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            if (files.isEmpty()) {
                Card(Modifier.fillMaxWidth()) { Text("یک یا چند فایل انتخاب کن؛ پردازش دسته‌ای مستقل است و خرابی یک فایل بقیه را متوقف نمی‌کند.", Modifier.padding(20.dp)) }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(files, key = ToolkitFileState::id) { file ->
                        ToolkitFileCard(
                            file = file,
                            selected = file.parsed?.profiles?.all { it.id in selected } == true,
                            onToggle = {
                                val ids = file.parsed?.profiles?.map { it.id }.orEmpty().toSet()
                                selected = if (ids.all { it in selected }) selected - ids else selected + ids
                            },
                            onPassword = { passkeyTarget = file },
                            onView = { viewer = file.parsed },
                            onEditable = {
                                pendingSave = model.editableCopy(file.id, clearPassword = true)
                                if (pendingSave != null) saver.launch("Ghajar_unlocked_${file.displayName.substringBeforeLast('.')}.npvt")
                                else notice = "ساخت نسخهٔ قابل ویرایش برای این فایل ممکن نیست."
                            }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = model::cancel, modifier = Modifier.weight(1f)) { Text("لغو پردازش") }
                    Button(
                        onClick = {
                            val ids = selected.takeIf(Set<String>::isNotEmpty)
                            model.importProfiles(ids) { result -> notice = "${result.imported} پروفایل افزوده شد؛ ${result.rejected} مورد رد شد." }
                        },
                        enabled = files.any { it.status == BatchStatus.DONE }, modifier = Modifier.weight(1f)
                    ) { Text(if (selected.isEmpty()) "افزودن همه" else "افزودن انتخاب‌شده") }
                }
            }
        }
    }

    passkeyTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { passkey = ""; passkeyTarget = null },
            title = { Text("passkey فایل") },
            text = { OutlinedTextField(passkey, { passkey = it }, singleLine = true, label = { Text("رمز فقط در حافظه") }) },
            confirmButton = { TextButton(onClick = {
                val chars = passkey.toCharArray(); passkey = ""; passkeyTarget = null
                model.retryWithPasskey(target.id, chars)
            }, enabled = passkey.isNotEmpty()) { Text("Decode") } },
            dismissButton = { TextButton(onClick = { passkey = ""; passkeyTarget = null }) { Text("انصراف") } }
        )
    }
    viewer?.let { parsed -> JsonViewerDialog(parsed, onDismiss = { viewer = null }) }
}

@Composable
private fun ToolkitFileCard(
    file: ToolkitFileState,
    selected: Boolean,
    onToggle: () -> Unit,
    onPassword: () -> Unit,
    onView: () -> Unit,
    onEditable: () -> Unit
) {
    val profiles = file.parsed?.profiles.orEmpty()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected, onCheckedChange = { onToggle() }, enabled = profiles.isNotEmpty())
                Column(Modifier.weight(1f)) {
                    Text(file.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${file.format.name} • ${statusLabel(file.status)}", style = MaterialTheme.typography.labelSmall)
                }
                if (file.status == BatchStatus.DONE) Icon(Icons.Filled.Security, "اعتبارسنجی شد", tint = MaterialTheme.colorScheme.secondary)
            }
            if (profiles.isNotEmpty()) Text("${profiles.size} پروفایل معتبر", style = MaterialTheme.typography.bodySmall)
            file.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (file.error?.contains("passkey", true) == true) TextButton(onClick = onPassword) { Text("ورود passkey") }
                if (file.parsed?.rawJson != null) TextButton(onClick = onView) { Text("نمایش JSON") }
                if (file.format == ConfigFormat.NPVT && file.parsed != null) TextButton(onClick = onEditable) { Icon(Icons.Filled.Save, null); Text("نسخهٔ قابل ویرایش") }
            }
        }
    }
}

@Composable
private fun JsonViewerDialog(parsed: ParsedConfig, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    val raw = parsed.rawJson.orEmpty()
    val masked = remember(raw, reveal) { if (reveal) raw else maskSecrets(raw) }
    val shown = remember(masked, query) {
        if (query.isBlank()) masked else masked.lineSequence().filter { it.contains(query, true) }.joinToString("\n")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("JSON Viewer") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("جست‌وجو: server, UUID, SNI, Host") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(reveal, onCheckedChange = { reveal = it })
                    Text("نمایش مقادیر حساس")
                }
                Text(shown, Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

private fun maskSecrets(value: String): String = value.replace(
    Regex("(?i)(\\\"(?:uuid|id|password|pass|token|authorization|publicKey|privateKey)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")"),
    "$1••••••••$2"
)

private fun statusLabel(status: BatchStatus): String = when (status) {
    BatchStatus.QUEUED -> "در صف"
    BatchStatus.READING -> "خواندن"
    BatchStatus.DETECTING -> "تشخیص فرمت"
    BatchStatus.DECRYPTING -> "Decode"
    BatchStatus.PARSING -> "Parse"
    BatchStatus.VALIDATING -> "اعتبارسنجی"
    BatchStatus.DONE -> "آماده"
    BatchStatus.FAILED -> "ناموفق"
    BatchStatus.CANCELLED -> "لغوشده"
}

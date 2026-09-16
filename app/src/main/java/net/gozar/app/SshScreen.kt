package net.gozar.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

/**
 * Real SSH tool screen backed by SshStore/SshManager/SshShell/SftpBrowser
 * (previously only a backend, with no UI at all — see docs/INVENTORY.md).
 * Host list -> connect -> interactive shell, with a lightweight SFTP browser.
 */
@Composable
internal fun SshScreen(
    store: SshStore,
    onSubScreenChange: (Boolean) -> Unit = {}
) {
    var openHostId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openHostId) { onSubScreenChange(openHostId != null) }
    BackHandler(enabled = openHostId != null) { openHostId = null }

    val hostId = openHostId
    if (hostId != null) {
        SshHostDetailScreen(store = store, hostId = hostId, onBack = { openHostId = null })
    } else {
        SshHostListScreen(store = store, onOpenHost = { openHostId = it })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SshHostListScreen(store: SshStore, onOpenHost: (String) -> Unit) {
    val hosts by store.hosts.collectAsState()
    val statuses by SshManager.status.collectAsState()
    var editing by remember { mutableStateOf<SshHost?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن سرور SSH")
            }
        }
    ) { padding ->
        if (hosts.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(48.dp), tint = AppGreen)
                Spacer(Modifier.height(12.dp))
                Text("هنوز سروری اضافه نشده", style = MaterialTheme.typography.titleMedium)
                Text(
                    "با دکمهٔ + یک سرور SSH اضافه کن تا بتونی مستقیم به آن وصل شوی",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(hosts, key = { it.id }) { host ->
                    val status = statuses[host.id] ?: SshStatus.Idle
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpenHost(host.id) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val (dot, label) = when (status) {
                                is SshStatus.Up -> AppGreen to (if (status.viaTunnel) "متصل • از طریق تانل" else "متصل • مستقیم")
                                is SshStatus.Connecting -> Color(0xFFFFA94D) to "در حال اتصال…"
                                is SshStatus.Failed -> Color(0xFFE0413C) to "خطا در اتصال"
                                SshStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant to "متصل نیست"
                            }
                            Box(Modifier.size(10.dp).background(dot, CircleShape))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(host.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(host.endpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(label, style = MaterialTheme.typography.labelSmall, color = dot)
                            }
                            IconButton(onClick = { editing = host; showForm = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "ویرایش")
                            }
                            IconButton(onClick = { confirmDeleteId = host.id }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        SshHostFormDialog(
            initial = editing,
            onDismiss = { showForm = false },
            onSave = { host ->
                if (editing == null) store.add(host) else store.update(host)
                showForm = false
            }
        )
    }

    confirmDeleteId?.let { id ->
        val host = store.find(id)
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("حذف سرور") },
            text = { Text("سرور «${host?.title.orEmpty()}» حذف شود؟ اتصال فعال آن قطع خواهد شد.") },
            confirmButton = {
                TextButton(onClick = { store.remove(id); confirmDeleteId = null }) {
                    Text("حذف", color = Color(0xFFE0413C))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun SshHostFormDialog(
    initial: SshHost?,
    onDismiss: () -> Unit,
    onSave: (SshHost) -> Unit
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var direct by remember { mutableStateOf(initial?.direct ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "افزودن سرور SSH" else "ویرایش سرور") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("نام (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("آدرس / IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    port, { port = it.filter { c -> c.isDigit() } }, label = { Text("پورت") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(username, { username = it }, label = { Text("نام کاربری") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it }, label = { Text("رمز عبور") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("همیشه مستقیم (بدون تانل VPN)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(checked = direct, onCheckedChange = { direct = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = address.isNotBlank() && username.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: SshHost()).copy(
                            label = label.trim(),
                            address = address.trim(),
                            port = port.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
                            username = username.trim(),
                            password = password,
                            direct = direct
                        )
                    )
                }
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SshHostDetailScreen(store: SshStore, hostId: String, onBack: () -> Unit) {
    val host = store.find(hostId)
    var showSftp by remember { mutableStateOf(false) }
    val statuses by SshManager.status.collectAsState()
    val status = statuses[hostId] ?: SshStatus.Idle
    val scope = rememberCoroutineScope()

    LaunchedEffect(hostId) {
        if (host != null && status !is SshStatus.Up && status !is SshStatus.Connecting) {
            SshManager.connect(host)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(host?.title ?: "SSH", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { showSftp = !showSftp }) {
                        Icon(Icons.Filled.Folder, contentDescription = "مرور فایل‌ها")
                    }
                    IconButton(onClick = {
                        scope.launch { host?.let { SshManager.connect(it) } }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "اتصال مجدد")
                    }
                    IconButton(onClick = { SshManager.disconnect(hostId) }) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = "قطع اتصال")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SshStatusBanner(status)
            AnimatedContent(
                targetState = showSftp,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "sshSubTab"
            ) { sftp ->
                if (sftp) {
                    SshSftpBrowser(hostId, Modifier.fillMaxSize())
                } else {
                    SshTerminal(hostId, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun SshStatusBanner(status: SshStatus) {
    val (bg, text) = when (status) {
        is SshStatus.Up -> AppGreen.copy(alpha = 0.14f) to (if (status.viaTunnel) "متصل از طریق تانل VPN" else "متصل مستقیم")
        is SshStatus.Connecting -> Color(0xFFFFA94D).copy(alpha = 0.14f) to "در حال برقراری اتصال…"
        is SshStatus.Failed -> Color(0xFFE0413C).copy(alpha = 0.14f) to "اتصال ناموفق: ${status.detail.ifBlank { status.messageKey }}"
        SshStatus.Idle -> MaterialTheme.colorScheme.surfaceVariant to "غیرمتصل"
    }
    Row(
        Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status is SshStatus.Connecting) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SshTerminal(hostId: String, modifier: Modifier = Modifier) {
    val shell = remember(hostId) { SshManager.shell(hostId) }
    var command by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    if (shell == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("هنوز به این سرور وصل نشدی", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val lines by shell.lines.collectAsState()
    val running by shell.running.collectAsState()
    val partial by shell.partial.collectAsState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(Color(0xFF050807))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(lines) { line ->
                Text(
                    line.text,
                    color = when (line.kind) {
                        ShellLineKind.INPUT -> AppGreen
                        ShellLineKind.ERROR -> Color(0xFFE0413C)
                        ShellLineKind.SYSTEM -> Color(0xFFFFA94D)
                        ShellLineKind.OUTPUT -> Color(0xFFE6F2ED)
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (partial.isNotEmpty()) {
                item {
                    Text(partial, color = Color(0xFFE6F2ED), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().imePadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("دستور را وارد کن…") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
            IconButton(onClick = {
                if (command.isNotBlank()) { shell.send(command); command = "" }
            }) { Icon(Icons.Filled.Send, contentDescription = "ارسال", tint = AppGreen) }
            IconButton(onClick = { shell.interrupt() }) {
                Text("^C", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!running) {
                IconButton(onClick = { shell.reconnect() }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "اتصال مجدد شل", tint = AppGreen)
                }
            }
        }
    }
}

@Composable
private fun SshSftpBrowser(hostId: String, modifier: Modifier = Modifier) {
    var path by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<SftpEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        loading = true; error = null
        scope.launch {
            val result = SftpBrowser.list(hostId, path)
            result.onSuccess { entries = it }.onFailure { error = it.message ?: "خطا" }
            loading = false
        }
    }

    LaunchedEffect(hostId) { path = SftpBrowser.home(hostId); reload() }
    LaunchedEffect(path) { reload() }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { path = SftpBrowser.parent(path) }, enabled = path != "/") {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بالاتر")
            }
            Text(path, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { reload() }) { Icon(Icons.Filled.Refresh, contentDescription = "بارگذاری مجدد") }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = Color(0xFFE0413C))
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(entries, key = { it.name }) { entry ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = entry.isDir) { path = SftpBrowser.join(path, entry.name) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (entry.isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = if (entry.isDir) AppGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                            if (!entry.isDir) {
                                Text(
                                    SftpBrowser.humanSize(entry.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                SftpBrowser.delete(hostId, SftpBrowser.join(path, entry.name), entry.isDir)
                                reload()
                            }
                        }) { Icon(Icons.Filled.Delete, contentDescription = "حذف") }
                    }
                }
            }
        }
    }
}

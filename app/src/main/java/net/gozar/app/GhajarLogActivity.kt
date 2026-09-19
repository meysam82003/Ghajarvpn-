package net.gozar.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

/**
 * "لاگ و اشکال‌زدایی" — the live ring buffer, with the two things that make a
 * log usable when something is actually broken: a way to narrow it, and a way
 * to get it out of the phone.
 *
 * Narrowing matters more than it sounds. The buffer runs to thousands of lines
 * and the interesting one is usually a single error buried among startup
 * chatter from a component you are not debugging. Without a filter the screen
 * is a wall that technically contains the answer.
 *
 * Everything leaving here - the clipboard, the shared file - goes through
 * GhajarLog's redaction, so a log handed to someone for help does not hand
 * them the subscription token with it.
 */
class GhajarLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GhajarAppTheme {
                LogScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun LogScreen(onBack: () -> Unit) {
    val entries by GhajarLog.entries.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }

    var confirmClear by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(GhajarLogFilter.Source.ALL) }
    var floor by remember { mutableStateOf(GhajarLogFilter.Floor.ALL) }

    // derivedStateOf so a new log line does not re-filter thousands of entries
    // on a frame where nothing about the filter changed.
    val shown by remember(entries, source, floor, query) {
        derivedStateOf { GhajarLogFilter.apply(entries, source, floor, query) }
    }
    val counts by remember(entries) {
        derivedStateOf { GhajarLogFilter.countsBySource(entries) }
    }

    // Follow the tail only while the user is already at it. Yanking the list
    // back down while they are reading something further up is the single
    // most annoying thing a log view can do.
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last == null || last.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(shown.size) {
        if (shown.isNotEmpty() && atBottom) listState.scrollToItem(shown.size - 1)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = t("back"),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(
                        t("log_title"),
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    // Both numbers, because "142 of 1281" answers "is my filter
                    // hiding things?" and a single number does not.
                    Text(
                        if (shown.size == entries.size)
                            t("log_count").format(localizeDigits("${entries.size}", lang))
                        else t("log_count_filtered").format(
                            localizeDigits("${shown.size}", lang),
                            localizeDigits("${entries.size}", lang)
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java)
                    clip?.setPrimaryClip(
                        ClipData.newPlainText("log", GhajarLogFilter.asText(shown))
                    )
                }) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = t("log_copy"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = {
                    scope.launch {
                        val file = GhajarLog.exportFile(context)
                        val uri = FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider", file
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, t("log_share")))
                    }
                }) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = t("log_share"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { confirmClear = true }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = t("log_clear"),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(t("log_search"), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = t("clear"))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )

            // Sources, with their counts. A chip that would show nothing says
            // so with its own zero rather than looking identical to one that
            // has lines waiting behind it.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GhajarLogFilter.Source.entries.forEach { candidate ->
                    val count = counts[candidate] ?: 0
                    FilterChip(
                        selected = source == candidate,
                        onClick = { source = candidate },
                        label = {
                            Text(
                                t("log_src_${candidate.id}") + "  " +
                                    localizeDigits("$count", lang),
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    t("log_level"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GhajarLogFilter.Floor.entries.forEach { candidate ->
                    FilterChip(
                        selected = floor == candidate,
                        onClick = { floor = candidate },
                        label = { Text(t("log_lvl_${candidate.id}"), fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider(color = ghajarColors.border)

            Box(Modifier.fillMaxSize()) {
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            // Two different empty states. "Nothing matches" and
                            // "nothing logged yet" call for opposite actions.
                            if (entries.isEmpty()) t("log_empty") else t("log_no_match"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(shown, key = { it.timeMs.toString() + it.message.hashCode() }) {
                                LogRow(it)
                            }
                        }
                    }
                }

                // Only offered when it would do something.
                AnimatedVisibility(
                    visible = !atBottom && shown.isNotEmpty(),
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { scope.launch { listState.scrollToItem(shown.size - 1) } }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            t("log_jump_latest"),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(t("log_clear")) },
            text = { Text(t("log_clear_body")) },
            confirmButton = {
                TextButton(onClick = { GhajarLog.clear(); confirmClear = false }) {
                    Text(t("log_clear_do"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(t("cancel")) }
            }
        )
    }
}

@Composable
private fun LogRow(entry: GhajarLogEntry) {
    val color = when (entry.level) {
        GhajarLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        GhajarLogLevel.INFO -> MaterialTheme.colorScheme.onBackground
        GhajarLogLevel.WARN -> ghajarColors.warning
        GhajarLogLevel.ERROR -> MaterialTheme.colorScheme.error
        GhajarLogLevel.CRASH -> ghajarColors.error
    }
    Text(
        text = entry.formatted(),
        color = color,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    )
}

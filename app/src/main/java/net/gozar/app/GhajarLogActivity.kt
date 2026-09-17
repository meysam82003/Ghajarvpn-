package net.gozar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * "لاگ و اشکال‌زدایی" — live view of GhajarLog's ring buffer plus export as a
 * downloadable/shareable .txt (crash traces included, since the crash handler
 * appends straight into the same file GhajarLog reads from).
 */
class GhajarLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD9B15C),
                    secondary = Color(0xFFB48B32),
                    background = Color(0xFF071B2E),
                    onBackground = Color(0xFFF6F1E4),
                    surface = Color(0xFF0E2C49),
                    onSurface = Color(0xFFF6F1E4),
                    onSurfaceVariant = Color(0xFF9DB0C2),
                    error = Color(0xFFE0654A)
                )
            ) {
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
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    "لاگ و اشکال‌زدایی",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(start = 6.dp)
                )
                Text(
                    "${entries.size} خط",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                IconButton(onClick = {
                    scope.launch {
                        val file = GhajarLog.exportFile(context)
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری فایل لاگ"))
                    }
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "دانلود / اشتراک‌گذاری لاگ", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "پاک کردن لاگ", tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surface)

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("هنوز لاگی ثبت نشده", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(entries) { entry -> LogRow(entry) }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("پاک کردن لاگ") },
            text = { Text("همه‌ی لاگ‌های ذخیره‌شده روی این گوشی پاک می‌شوند. ادامه می‌دهید؟") },
            confirmButton = {
                TextButton(onClick = { GhajarLog.clear(); confirmClear = false }) {
                    Text("پاک کن", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun LogRow(entry: GhajarLogEntry) {
    val color = when (entry.level) {
        GhajarLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        GhajarLogLevel.INFO -> MaterialTheme.colorScheme.onBackground
        GhajarLogLevel.WARN -> Color(0xFFE0B84A)
        GhajarLogLevel.ERROR -> MaterialTheme.colorScheme.error
        GhajarLogLevel.CRASH -> Color(0xFFFF4D4D)
    }
    Text(
        text = entry.formatted(),
        color = color,
        fontSize = 11.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    )
}

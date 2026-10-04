package net.gozar.app.freecfg

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.gozar.app.ConfigStore

@Composable
fun FreeSourceControls(store: ConfigStore) {
    var open by remember { mutableStateOf(false) }
    val raw by store.freeSourcePolicy.collectAsState()
    val policy = remember(raw) { org.json.JSONObject(raw) }
    TextButton(onClick = { open = !open }) { Text("انتخاب و مدیریت منابع رایگان") }
    if (open) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("فقط با درخواست بروزرسانی شما دریافت می‌شوند. عمومی بودن منبع تضمین امنیت سرورها نیست.")
        FreeSourceRegistry.DEFAULT_SOURCES.forEach { source ->
            val state = policy.optString(source.id, "enabled")
            if (state != "removed") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(source.endpoint.substringAfterLast('/')); Text(source.endpoint, style = MaterialTheme.typography.labelSmall) }
                Switch(state == "enabled", onCheckedChange = { store.setFreeSource(source.id, if (it) "enabled" else "disabled") })
                TextButton(onClick = { store.setFreeSource(source.id, "removed") }) { Text("حذف منبع") }
            }
        }
        if (FreeSourceRegistry.DEFAULT_SOURCES.any { policy.optString(it.id) == "removed" })
            TextButton(onClick = { FreeSourceRegistry.DEFAULT_SOURCES.filter { policy.optString(it.id) == "removed" }.forEach { store.setFreeSource(it.id, "disabled") } }) { Text("بازگرداندن منابع حذف‌شده (غیرفعال)") }
    }
}

package net.gozar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real "clean IP" tool: registers a free Cloudflare WARP account (Warp.kt) and
 * surfaces its WireGuard edge endpoints as importable configs, with an IP
 * reputation lookup (Ipintelligence.kt) on the chosen edge. Previously called
 * from Settings ("scan_warp") with no screen behind it at all — see
 * docs/INVENTORY.md. No fabricated ping numbers: latency for the imported
 * configs comes from the same real ping path every other server uses once
 * they're in the list, not from a bespoke measurement invented here.
 */
@Composable
internal fun CleanIpScreen() {
    val context = LocalContext.current
    val store = remember { ConfigStore.get(context) }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var configs by remember { mutableStateOf<List<ProxyConfig>>(emptyList()) }
    var intel by remember { mutableStateOf<IpIntel?>(null) }
    var addedAll by remember { mutableStateOf(false) }
    val addedIds = remember { mutableStateOf(setOf<String>()) }

    fun register() {
        loading = true; error = null; configs = emptyList(); intel = null; addedAll = false
        addedIds.value = emptySet()
        scope.launch {
            val result = withContext(Dispatchers.IO) { Warp.register() }
            when (result) {
                is Warp.Result.Success -> {
                    configs = result.configs
                    intel = withContext(Dispatchers.IO) {
                        result.configs.firstOrNull()?.address?.let { IpIntelligence.lookup(it) }
                    }
                }
                is Warp.Result.Failure -> error = result.message
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDone, contentDescription = null, tint = AppGreen)
                    Text(
                        "دریافت IP تمیز از Cloudflare WARP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    "یک حساب رایگان WARP ثبت می‌شود و آدرس‌های واقعی edge کلادفلر به‌عنوان کانفیگ WireGuard قابل افزودن هستند. پینگ/کیفیت هر کانفیگ بعد از افزودن، مثل هر سرور دیگر با تست واقعی سنجیده می‌شود؛ اینجا عددسازی نمی‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { register() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("در حال ثبت…")
                    } else {
                        Text(if (configs.isEmpty()) "دریافت" else "دریافت دوباره")
                    }
                }
            }
        }

        error?.let {
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0xFFE0413C).copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Error, contentDescription = null, tint = Color(0xFFE0413C))
                Text(it, color = Color(0xFFE0413C), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        intel?.let { i ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("اطلاعات IP اصلی (${i.ip})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("سازمان: ${i.org.ifBlank { "—" }} • ${i.kind}", style = MaterialTheme.typography.bodySmall)
                    Text("اعتبار شبکه: ${i.reputation} (${i.repBand})", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (configs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${configs.size} کانفیگ WARP آماده", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(
                    enabled = !addedAll,
                    onClick = {
                        store.addToLocalSub("Cloudflare WARP", configs)
                        addedAll = true
                        addedIds.value = configs.map { it.id }.toSet()
                    }
                ) { Text(if (addedAll) "همه اضافه شد" else "افزودن همه") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(configs, key = { it.id }) { cfg ->
                    val added = cfg.id in addedIds.value
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(cfg.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("${cfg.address}:${cfg.port}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (added) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "افزوده شد", tint = AppGreen)
                        } else {
                            TextButton(onClick = {
                                store.addToLocalSub("Cloudflare WARP", listOf(cfg))
                                addedIds.value = addedIds.value + cfg.id
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("افزودن")
                            }
                        }
                    }
                }
            }
        } else if (!loading && error == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "هنوز چیزی دریافت نشده",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

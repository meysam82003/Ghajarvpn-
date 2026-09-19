package net.gozar.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.gozar.app.freecfg.FreeFeedRules

/** Activity lifetime keeps a user-started refresh alive when leaving this page. */
class GhajarFreeConfigsModel(application: Application) : AndroidViewModel(application) {
    var result by mutableStateOf<String?>(null)
        private set
    fun refresh(store: ConfigStore) {
        if (FreeConfigs.busy.value) return
        result = null
        viewModelScope.launch {
            try {
                val count = FreeConfigs.refresh(store, FreeConfigs.CONFIG_NAME)
                result = when (count) {
                    FreeConfigs.BUSY -> null
                    FreeConfigs.UNREACHABLE -> "منابع پاسخ ندادند؛ فهرست قبلی حفظ شد."
                    FreeConfigs.NO_CONFIGS -> "کانفیگ تازه‌ای قابل دریافت نبود؛ دوباره تلاش کنید."
                    else -> if (FreeConfigs.incomplete.value) "بررسی بعضی منابع کامل نشد؛ فهرست قبلی حفظ شده است."
                        else if (count == 0) "در این بررسی کانفیگ قابل اتصال پیدا نشد."
                        else "بررسی و به‌روزرسانی پایان یافت."
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { result = "به‌روزرسانی انجام نشد؛ اتصال اینترنت را بررسی و دوباره تلاش کنید." }
        }
    }
}

@Composable
internal fun GhajarFreeConfigsScreen(store: ConfigStore, onBrowse: () -> Unit,
    onConnect: (ProxyConfig) -> Unit, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val model = remember(context) { ViewModelProvider(context as ComponentActivity)[GhajarFreeConfigsModel::class.java] }
    val configs by store.configs.collectAsState()
    val subscriptions by store.subscriptions.collectAsState()
    val busy by FreeConfigs.busy.collectAsState()
    val progress by FreeConfigs.progress.collectAsState()
    val incomplete by FreeConfigs.incomplete.collectAsState()
    val measured by FreeConfigs.measuredLatency.collectAsState()
    val selectedId by store.selectedId.collectAsState()
    val activeId by VpnState.activeId.collectAsState()
    val connection by VpnState.state.collectAsState()
    val sub = subscriptions.firstOrNull { it.url == FreeConfigs.SOURCE_URL }
    val rows = remember(configs, sub?.id, measured) {
        configs.filter { sub != null && it.subId == sub.id }
            .sortedBy { measured[FreeFeedRules.signature(it)] ?: Int.MAX_VALUE }
    }
    val fa = LocalLang.current == Lang.FA
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Slab(accent = ghajarColors.primary) {
                SlabRow(title = if (fa) "کانفیگ‌های رایگان" else "Free configs",
                    subtitle = "@${FreeConfigs.CHANNEL}", icon = Icons.Filled.CloudSync)
                PremiumStatus(when {
                    busy -> if (fa) "در حال دریافت و بررسی" else "Fetching and testing"
                    incomplete -> if (fa) "بررسی ناقص" else "Incomplete refresh"
                    sub?.lastUpdated?.let { it > 0L } == true -> {
                        val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                            .format(java.util.Date(sub.lastUpdated))
                        if (fa) "آخرین بررسی: $date" else "Last checked: $date"
                    }
                    else -> if (fa) "هنوز بررسی نشده" else "Not checked yet"
                })
                Text(if (fa) "منابع ۷۲ ساعت اخیر • حداکثر ۳۵ کانفیگ • حذف تکراری‌ها"
                    else "Last 72 hours · up to 35 configs · duplicates removed",
                    style = MaterialTheme.typography.bodySmall, color = ghajarColors.textSecondary)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { PillButton(if (fa) "به‌روزرسانی" else "Refresh",
                    { model.refresh(store) }, enabled = !busy, icon = Icons.Filled.Refresh) }
                Box(Modifier.weight(1f)) { GhostPill(if (fa) "افزودن کانفیگ" else "Add configs",
                    onBrowse, icon = Icons.Filled.Add) }
            }
        }
        if (busy) item {
            SkinLoading(progress?.let {
                if (fa) "بررسی ${it.tested} از ${it.total} • پاسخ‌گو: ${it.alive}"
                else "Tested ${it.tested}/${it.total} · Responding: ${it.alive}"
            } ?: if (fa) "در حال دریافت منابع…" else "Fetching sources…")
        }
        model.result?.let { result -> item { Slab { Text(result, style = MaterialTheme.typography.bodyMedium) } } }
        item { Rail(if (fa) "${rows.size} کانفیگ ذخیره‌شده · مرتب‌شده با پینگ اندازه‌گیری‌شده"
            else "${rows.size} saved configs · measured ping order") }
        if (rows.isEmpty() && !busy) item {
            SkinEmpty(if (fa) "کانفیگی دریافت نشده" else "No configs yet",
                hint = if (fa) "برای دریافت و تست منابع، به‌روزرسانی را بزنید." else "Refresh to fetch and validate sources.",
                icon = Icons.Filled.CloudDownload, actionText = if (fa) "دریافت" else "Fetch",
                onAction = { model.refresh(store) })
        }
        items(rows, key = { it.id }, contentType = { "free-server" }) { config ->
            val active = activeId == config.id && connection == Connection.CONNECTED
            Slab(accent = if (active || selectedId == config.id) ghajarColors.primary else null,
                padding = 12.dp, spacing = 4.dp) {
                SlabRow(title = config.name, subtitle = config.protocol.uppercase(java.util.Locale.ROOT),
                    icon = Icons.Filled.Dns, onClick = { store.selectExplicitly(config.id) },
                    trailing = {
                        val latency = measured[FreeFeedRules.signature(config)]
                        Text(latency?.let { "$it ms" } ?: "—", style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { store.update(config.copy(favorite = !config.favorite)) }) {
                            Icon(if (config.favorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                                if (fa) "علاقه‌مندی" else "Favorite", tint = ghajarColors.highlight)
                        }
                    })
                if (active) PremiumStatus(if (fa) "متصل" else "Connected", true)
                else if (selectedId == config.id) PremiumStatus(if (fa) "انتخاب‌شده" else "Selected")
                GhostPill(if (active) { if (fa) "قطع اتصال" else "Disconnect" }
                    else { if (fa) "اتصال" else "Connect" }, onClick = {
                    if (active) onDisconnect() else { store.selectExplicitly(config.id); onConnect(config) }
                }, enabled = connection != Connection.DISCONNECTING, icon = Icons.Filled.PowerSettingsNew)
            }
        }
        item { GhostPill(if (fa) "مدیریت همهٔ سرورها و ابزارها" else "Manage servers and tools", onBrowse) }
    }
}

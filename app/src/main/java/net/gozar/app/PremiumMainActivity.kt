package net.gozar.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PremiumBg = Color(0xFF050807)
private val PremiumSurface = Color(0xFF0B1512)
private val PremiumCard = Color(0xFF101816)
private val PremiumCard2 = Color(0xFF14231F)
private val PremiumGreen = Color(0xFF00B978)
private val PremiumGreen2 = Color(0xFF24D98B)
private val PremiumText = Color(0xFFF2F8F5)
private val PremiumMuted = Color(0xFF91A59D)
private val PremiumGold = Color(0xFFD6B45F)
private val PremiumDanger = Color(0xFFFF6B6B)

private val PremiumScheme = darkColorScheme(
    primary = PremiumGreen,
    onPrimary = Color(0xFF001F12),
    primaryContainer = Color(0xFF083D2B),
    onPrimaryContainer = Color(0xFFBDF5D9),
    secondary = PremiumGold,
    onSecondary = Color(0xFF211800),
    background = PremiumBg,
    onBackground = PremiumText,
    surface = PremiumSurface,
    onSurface = PremiumText,
    surfaceVariant = PremiumCard2,
    onSurfaceVariant = PremiumMuted,
    outline = Color(0xFF274238),
    error = PremiumDanger
)

class PremiumMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        VpnBridge.register(applicationContext)
        GhajarOpenVpnBridge.initialize(applicationContext)
        setContent {
            MaterialTheme(colorScheme = PremiumScheme, shapes = GhajarSoftShapes) {
                PremiumRoot(openShareInitially = intent?.getBooleanExtra("open_share", false) == true)
            }
        }
    }
}

private object PremiumVpnEngine {
    fun start(context: android.content.Context, config: ProxyConfig): Result<Unit> = runCatching {
        val store = ConfigStore.get(context)
        if (config.protocol.equals("ikev2", true) || config.protocol.equals("ike", true)) {
            check(IkeController.connect(context, config)) { "IKEv2 engine did not start" }
            return@runCatching
        }
        val all = store.configs.value
        val torBase = all.firstOrNull { it.id == config.torBaseId }
        val chainBase = all.firstOrNull { it.id == config.chainId }
        val json = ConfigBuilder.build(
            config = config,
            fragment = store.fragment.value,
            splitRouting = store.splitRouting.value,
            sniffing = store.sniffing.value,
            sniffTypes = store.sniffTypes.value,
            fragmentPackets = store.fragmentPackets.value,
            fragmentLength = store.fragmentLength.value,
            fragmentInterval = store.fragmentInterval.value,
            mux = store.mux.value,
            muxConcurrency = store.muxConcurrency.value,
            adBlock = store.adBlock.value,
            fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            torBase = torBase,
            chainBase = chainBase,
            onionRouting = store.onionRouting.value,
            coreLogLevel = store.coreLogLevel.value
        )
        val intent = Intent(context, GozarVpnService::class.java)
            .putExtra(GozarVpnService.EXTRA_CONFIG, json)
            .putExtra(GozarVpnService.EXTRA_NAME, config.name)
            .putExtra(GozarVpnService.EXTRA_STOP_LABEL, "قطع اتصال")
        AetherSpec.from(config)?.let { intent.putExtra(GozarVpnService.EXTRA_AETHER, it.toJson()) }
        if (config.protocol == "tor") intent.putExtra(GozarVpnService.EXTRA_TOR,
            config.torCountry + "|" + if (config.torThroughVpn) "1" else "0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    suspend fun stop(context: android.content.Context) {
        when {
            IkeController.active -> IkeController.disconnect(context)
            VpnState.activeId.value.orEmpty().startsWith("ovpn:") -> GhajarOpenVpnBridge.disconnect(context)
            else -> runCatching { context.startService(Intent(context, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_STOP)) }
        }
    }
}

private enum class PremiumTab { HOME, STORE, SETTINGS }

@Composable
private fun PremiumRoot(openShareInitially: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ConfigStore.get(context) }
    val vpnState by VpnState.state.collectAsState()
    val counters by VpnBridge.counters.collectAsState()
    val configs by store.configs.collectAsState()
    val selectedId by store.selectedId.collectAsState()
    val selected = configs.firstOrNull { it.id == selectedId } ?: configs.firstOrNull()

    var tab by remember { mutableStateOf(if (openShareInitially) PremiumTab.SETTINGS else PremiumTab.HOME) }
    var showServerPicker by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(openShareInitially) }
    var pendingConfig by remember { mutableStateOf<ProxyConfig?>(null) }
    var pendingShareMode by remember { mutableStateOf<GhajarShareMode?>(null) }
    var reconnectShareOnly by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cfg = pendingConfig
        pendingConfig = null
        if (result.resultCode == Activity.RESULT_OK && cfg != null) {
            VpnCommandCoordinator.onConnectRequested(cfg.id) {
                PremiumVpnEngine.start(context, cfg).onFailure { VpnState.setError(it.message ?: "اتصال آغاز نشد") }
            }
        } else if (cfg != null) error = "مجوز VPN داده نشد."
    }

    fun connect(cfg: ProxyConfig?) {
        if (cfg == null) { error = "ابتدا یک سرویس یا کانفیگ انتخاب کن."; return }
        if (cfg.protocol.equals("ikev2", true) || cfg.protocol.equals("ike", true)) {
            VpnCommandCoordinator.onConnectRequested(cfg.id) {
                PremiumVpnEngine.start(context, cfg).onFailure { VpnState.setError(it.message ?: "IKEv2 متصل نشد") }
            }
            return
        }
        val prep = VpnService.prepare(context)
        if (prep == null) {
            VpnCommandCoordinator.onConnectRequested(cfg.id) {
                PremiumVpnEngine.start(context, cfg).onFailure { VpnState.setError(it.message ?: "اتصال آغاز نشد") }
            }
        } else {
            pendingConfig = cfg
            vpnPermission.launch(prep)
        }
    }

    fun launchShareServices(mode: GhajarShareMode) {
        if (mode != GhajarShareMode.HYBRID) GhajarLocalHotspot.start(context)
        else GhajarShareRuntime.openTetherSettings(context)
        GhajarShareRuntime.startProxy(context, mode)
    }

    fun beginShare(mode: GhajarShareMode) {
        val unsupported = selected?.protocol?.lowercase() in setOf("ike", "ikev2", "openvpn")
        if (unsupported) {
            error = "VPN Share برای این موتور سیستم‌محور از Proxy داخلی قاجار استفاده نمی‌کند. یک کانفیگ VLESS/VMess/Trojan/SS/WireGuard/Hysteria/Tor/Aether انتخاب کن."
            return
        }
        pendingShareMode = mode
        if (mode == GhajarShareMode.SHARE_ONLY) {
            GhajarShareRouting.enterShareOnly(context)
            if (vpnState == Connection.CONNECTED) {
                reconnectShareOnly = true
                scope.launch { PremiumVpnEngine.stop(context) }
                return
            }
        }
        if (vpnState == Connection.CONNECTED) {
            launchShareServices(mode); pendingShareMode = null
        } else connect(selected)
    }

    LaunchedEffect(vpnState, pendingShareMode, reconnectShareOnly) {
        val mode = pendingShareMode ?: return@LaunchedEffect
        if (reconnectShareOnly && vpnState == Connection.DISCONNECTED) {
            reconnectShareOnly = false
            delay(250)
            connect(selected)
        } else if (!reconnectShareOnly && vpnState == Connection.CONNECTED) {
            launchShareServices(mode)
            pendingShareMode = null
        }
    }

    BackHandler(enabled = showShare) { showShare = false }

    Scaffold(
        containerColor = PremiumBg,
        bottomBar = { if (!showShare) PremiumBottomBar(tab) { tab = it } }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(PremiumBg)) {
            when {
                showShare -> GhajarShareScreen(
                    onBack = { showShare = false },
                    onStart = ::beginShare,
                    onStop = { GhajarShareRuntime.stop(context) }
                )
                tab == PremiumTab.HOME -> PremiumHome(
                    selected = selected,
                    vpnState = vpnState,
                    counters = counters,
                    onPower = {
                        if (vpnState == Connection.CONNECTED || vpnState == Connection.CONNECTING) {
                            VpnCommandCoordinator.onDisconnectRequested { scope.launch { PremiumVpnEngine.stop(context) } }
                        } else connect(selected)
                    },
                    onServer = { showServerPicker = true },
                    onShare = { showShare = true },
                    onSettings = { tab = PremiumTab.SETTINGS },
                    onConfigs = { context.startActivity(Intent(context, ConfigCenterActivity::class.java)) }
                )
                tab == PremiumTab.STORE -> GhajarShopScreen(modifier = Modifier.fillMaxSize(), active = true)
                else -> PremiumSettings(onShare = { showShare = true })
            }
            error?.let { msg ->
                AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton(onClick = { error = null }) { Text("باشه") } },
                    title = { Text("قاجار VPN") }, text = { Text(msg) })
            }
        }
    }

    if (showServerPicker) {
        ServerPickerDialog(configs, selectedId, onSelect = { store.setSelectedId(it.id); showServerPicker = false }, onDismiss = { showServerPicker = false })
    }
}

@Composable
private fun PremiumBottomBar(tab: PremiumTab, onTab: (PremiumTab) -> Unit) {
    NavigationBar(containerColor = Color(0xF20A100E), tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding()) {
        NavigationBarItem(selected = tab == PremiumTab.HOME, onClick = { onTab(PremiumTab.HOME) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("خانه") })
        NavigationBarItem(selected = tab == PremiumTab.STORE, onClick = { onTab(PremiumTab.STORE) }, icon = { Icon(Icons.Default.ShoppingBag, null) }, label = { Text("فروشگاه") })
        NavigationBarItem(selected = tab == PremiumTab.SETTINGS, onClick = { onTab(PremiumTab.SETTINGS) }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("تنظیمات") })
    }
}

@Composable
private fun PremiumHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        GhajarWordmark(Modifier.width(175.dp).height(52.dp))
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = Color(0xFF0D2019), border = BorderStroke(1.dp, Color(0xFF234C3D))) {
            Icon(Icons.Default.Shield, null, tint = PremiumGreen2, modifier = Modifier.padding(10.dp).size(22.dp))
        }
    }
}

@Composable
private fun PremiumHome(
    selected: ProxyConfig?, vpnState: Connection, counters: VpnCounters,
    onPower: () -> Unit, onServer: () -> Unit, onShare: () -> Unit,
    onSettings: () -> Unit, onConfigs: () -> Unit
) {
    val connected = vpnState == Connection.CONNECTED
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { PremiumHeader() }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (vpnState) {
                        Connection.CONNECTED -> "متصل و امن"
                        Connection.CONNECTING -> "در حال اتصال…"
                        Connection.DISCONNECTING -> "در حال قطع…"
                        Connection.ERROR -> "اتصال ناموفق"
                        else -> "برای اتصال لمس کن"
                    },
                    color = if (connected) PremiumGreen2 else PremiumMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(14.dp))
                PowerOrb(vpnState, onPower)
            }
        }
        item {
            PremiumCard(modifier = Modifier.padding(horizontal = 16.dp).clickable(onClick = onServer)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color(0xFF123226)) { Icon(Icons.Default.Hub, null, tint = PremiumGreen2, modifier = Modifier.padding(10.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selected?.name ?: "انتخاب سرویس", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(selected?.let { "${it.protocol.uppercase()} • ${it.address}:${it.port}" } ?: "سرورها و سرویس‌ها", color = PremiumMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("تغییر", color = PremiumGreen2, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("دانلود", fmtRate(counters.downSpeed), "↓", Modifier.weight(1f))
                MetricCard("آپلود", fmtRate(counters.upSpeed), "↑", Modifier.weight(1f))
                MetricCard("مصرف", fmtBytes(counters.totalDown + counters.totalUp), "◉", Modifier.weight(1f))
            }
        }
        item {
            Text("دسترسی سریع", Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Default.Share, "VPN Share", onShare, Modifier.weight(1f))
                QuickAction(Icons.Default.Router, "کانفیگ‌ها", onConfigs, Modifier.weight(1f))
                QuickAction(Icons.Default.Tune, "تنظیمات", onSettings, Modifier.weight(1f))
            }
        }
        item {
            PremiumCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, tint = PremiumGold)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Ghajar Premium Security", fontWeight = FontWeight.Bold)
                        Text("اتصال، DNS، مسیریابی و اشتراک VPN در یک مرکز", color = PremiumMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun PowerOrb(state: Connection, onClick: () -> Unit) {
    val active = state == Connection.CONNECTED
    val glow by animateColorAsState(if (active) PremiumGreen2 else Color(0xFF24453A), label = "power")
    Box(
        Modifier.size(178.dp).shadow(if (active) 28.dp else 8.dp, CircleShape, ambientColor = glow, spotColor = glow)
            .background(Brush.radialGradient(listOf(glow.copy(alpha = .32f), Color.Transparent)), CircleShape)
            .padding(18.dp).border(2.dp, glow, CircleShape).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = CircleShape, color = Color(0xFF09120F), border = BorderStroke(1.dp, glow.copy(alpha = .65f)), modifier = Modifier.size(118.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (state == Connection.CONNECTING || state == Connection.DISCONNECTING) CircularProgressIndicator(color = PremiumGreen2, modifier = Modifier.size(66.dp))
                else Icon(Icons.Default.PowerSettingsNew, null, tint = if (active) PremiumGreen2 else PremiumMuted, modifier = Modifier.size(52.dp))
            }
        }
    }
}

@Composable
private fun PremiumCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumCard),
        border = BorderStroke(1.dp, Color(0xFF1D382F))
    ) { Box(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun MetricCard(label: String, value: String, mark: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PremiumCard2), border = BorderStroke(1.dp, Color(0xFF1D382F))) {
        Column(Modifier.padding(vertical = 13.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$mark $value", fontWeight = FontWeight.Bold, color = PremiumText, maxLines = 1)
            Text(label, color = PremiumMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PremiumCard), border = BorderStroke(1.dp, Color(0xFF234C3D))) {
        Column(Modifier.padding(vertical = 15.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = Color(0xFF0D2C20)) { Icon(icon, null, tint = PremiumGreen2, modifier = Modifier.padding(9.dp).size(20.dp)) }
            Spacer(Modifier.height(7.dp)); Text(label, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ServerPickerDialog(configs: List<ProxyConfig>, selectedId: String?, onSelect: (ProxyConfig) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
        title = { Text("انتخاب سرویس") },
        text = {
            LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(configs, key = { it.id }) { c ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(c) }, shape = RoundedCornerShape(14.dp),
                        color = if (c.id == selectedId) Color(0xFF123B2B) else PremiumCard2,
                        border = BorderStroke(1.dp, if (c.id == selectedId) PremiumGreen else Color(0xFF243A32))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${c.protocol.uppercase()} • ${c.address}:${c.port}", color = PremiumMuted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun PremiumSettings(onShare: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ConfigStore.get(context) }
    val kill by store.killSwitch.collectAsState()
    val split by store.splitRouting.collectAsState()
    val ad by store.adBlock.collectAsState()
    val encDns by store.encryptedDns.collectAsState()
    val fakeDns by store.fakeDns.collectAsState()
    val onion by store.onionRouting.collectAsState()
    val auto by store.autoSelect.collectAsState()

    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PremiumHeader(); Text("تنظیمات", Modifier.padding(horizontal = 18.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        item {
            SettingsSection("شبکه و اشتراک") {
                SettingsAction(Icons.Default.WifiTethering, "VPN Share", "هات‌اسپات VPN، سهمیه و مدیریت دستگاه‌ها", onShare)
                SettingsToggle(Icons.Default.Lock, "Kill Switch", "جلوگیری از نشت اینترنت هنگام قطع VPN", kill) { store.setKillSwitch(it) }
                SettingsToggle(Icons.Default.Router, "Split Routing", "مسیریابی انتخابی", split) { store.setSplitRouting(it) }
                SettingsToggle(Icons.Default.Speed, "انتخاب خودکار سریع‌ترین", "انتخاب سرویس با کیفیت بهتر", auto) { store.setAutoSelect(it) }
            }
        }
        item {
            SettingsSection("حریم خصوصی") {
                SettingsToggle(Icons.Default.Security, "Ad Block", "مسدودسازی دامنه‌های تبلیغاتی", ad) { store.setAdBlock(it) }
                SettingsToggle(Icons.Default.Dns, "DNS رمزگذاری‌شده", "DoH داخل تونل", encDns) { store.setEncryptedDns(it) }
                SettingsToggle(Icons.Default.Hub, "Fake DNS", "مسیریابی پیشرفته دامنه", fakeDns) { store.setFakeDns(it) }
                SettingsToggle(Icons.Default.Shield, "Onion Routing", "مسیریابی دامنه‌های onion", onion) { store.setOnionRouting(it) }
            }
        }
        item {
            SettingsSection("ابزارها و مدیریت") {
                SettingsAction(Icons.Default.Router, "مرکز کانفیگ", "Import / QR / فایل / Subscription") { context.startActivity(Intent(context, ConfigCenterActivity::class.java)) }
                SettingsAction(Icons.Default.BugReport, "لاگ و دیباگر", "گزارش موتور و اشکال‌زدایی") { context.startActivity(Intent(context, GhajarLogActivity::class.java)) }
                SettingsAction(Icons.Default.Terminal, "SSH / SFTP / ابزارهای تخصصی", "همه ابزارهای فعلی بدون حذف") { context.startActivity(Intent(context, MainActivity::class.java)) }
                SettingsAction(Icons.Default.Apps, "تمام تنظیمات پیشرفته فعلی", "Per‑App، تست پایداری، Network Monitor، CheckHost و سایر ابزارها") { context.startActivity(Intent(context, MainActivity::class.java)) }
            }
        }
        item {
            PremiumCard(Modifier.padding(horizontal = 16.dp)) {
                Column {
                    Text("Premium Dark • Ghajar VPN", color = PremiumGold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("پوسته جدید هیچ قابلیت فعلی را حذف نمی‌کند؛ ابزارهای تخصصی موجود تا مهاجرت کامل UI از Workspace فعلی نیز قابل دسترسی‌اند.", color = PremiumMuted, fontSize = 12.sp)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, Modifier.padding(horizontal = 18.dp, vertical = 5.dp), color = PremiumMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        PremiumCard(Modifier.padding(horizontal = 16.dp)) { Column { content() } }
    }
}

@Composable
private fun SettingsAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = PremiumGreen2); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = PremiumMuted, fontSize = 11.sp) }
        Text("‹", color = PremiumMuted, fontSize = 24.sp)
    }
}

@Composable
private fun SettingsToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = PremiumGreen2); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = PremiumMuted, fontSize = 11.sp) }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun GhajarShareScreen(onBack: () -> Unit, onStart: (GhajarShareMode) -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by GhajarShareRuntime.state.collectAsState()
    val hotspot by GhajarLocalHotspot.info.collectAsState()
    var selectedMode by remember { mutableStateOf(state.mode) }
    var showClient by remember { mutableStateOf<GhajarShareClient?>(null) }
    var maxClientsText by remember { mutableStateOf(GhajarSharePrefs.maxClients(context).toString()) }
    var pendingMode by remember { mutableStateOf<GhajarShareMode?>(null) }

    fun hasHotspotPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        else ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val m = pendingMode; pendingMode = null
        if (m != null && grants.values.all { it }) onStart(m)
    }
    fun start(mode: GhajarShareMode) {
        if (mode == GhajarShareMode.HYBRID || hasHotspotPermission()) onStart(mode)
        else {
            pendingMode = mode
            val p = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            permission.launch(p)
        }
    }

    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("‹", fontSize = 32.sp) }
                GhajarWordmark(Modifier.width(150.dp).height(44.dp))
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(99.dp), color = if (state.running) Color(0xFF0E3C2A) else PremiumCard2) {
                    Text(if (state.running) "فعال" else "خاموش", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = if (state.running) PremiumGreen2 else PremiumMuted, fontSize = 12.sp)
                }
            }
            Text("VPN Share", Modifier.padding(horizontal = 18.dp), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("اشتراک امن VPN بدون نصب برنامه روی دستگاه مقابل", Modifier.padding(horizontal = 18.dp), color = PremiumMuted, fontSize = 12.sp)
        }
        item {
            Text("نوع اشتراک", Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ShareModeCard(GhajarShareMode.VPN_ONLY, selectedMode, "فقط VPN", "Local‑Only Hotspot • اینترنت مستقیم A منتقل نمی‌شود", Icons.Default.Lock) { selectedMode = it }
                ShareModeCard(GhajarShareMode.HYBRID, selectedMode, "VPN + اینترنت عادی", "هات‌اسپات معمولی + مسیر Proxy قاجار", Icons.Default.Wifi) { selectedMode = it }
                ShareModeCard(GhajarShareMode.SHARE_ONLY, selectedMode, "VPN فقط برای دستگاه‌های متصل", "خود گوشی A مستقیم؛ B/C از VPN", Icons.Default.Share) { selectedMode = it }
            }
        }
        item {
            PremiumCard(Modifier.padding(horizontal = 16.dp)) {
                Column {
                    Text("وضعیت Share", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    ShareStatusRow("VPN", if (VpnState.state.value == Connection.CONNECTED) "متصل" else VpnState.state.value.name)
                    ShareStatusRow("Relay", if (state.running) "${state.proxyHost.ifBlank { GhajarShareRuntime.privateGateway() }}:${state.proxyPort}" else "خاموش")
                    ShareStatusRow("Hotspot", if (selectedMode == GhajarShareMode.HYBRID) "هات‌اسپات سیستم" else if (hotspot.active) hotspot.ssid else "خاموش")
                    ShareStatusRow("Devices", state.clients.values.count { it.activeConnections > 0 }.toString())
                    Spacer(Modifier.height(12.dp))
                    if (state.running) OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("توقف VPN Share") }
                    else Button(onClick = { start(selectedMode) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.WifiTethering, null); Spacer(Modifier.width(8.dp)); Text("شروع اشتراک") }
                    if (selectedMode == GhajarShareMode.HYBRID) {
                        Spacer(Modifier.height(7.dp)); Text("در این حالت قاجار صفحه Hotspot سیستم را باز می‌کند؛ Hotspot را روشن کن و سپس Proxy نمایش‌داده‌شده را روی B تنظیم کن.", color = PremiumMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        if (hotspot.active || state.running) {
            item {
                PremiumCard(Modifier.padding(horizontal = 16.dp)) {
                    Column {
                        Text("اطلاعات اتصال", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(9.dp))
                        if (hotspot.ssid.isNotBlank()) CopyLine("Wi‑Fi", hotspot.ssid) { clipboard.setText(AnnotatedString(hotspot.ssid)) }
                        if (hotspot.password.isNotBlank()) CopyLine("Password", hotspot.password) { clipboard.setText(AnnotatedString(hotspot.password)) }
                        val host = state.proxyHost.ifBlank { hotspot.gateway.ifBlank { GhajarShareRuntime.privateGateway() } }
                        CopyLine("Proxy Host", host) { clipboard.setText(AnnotatedString(host)) }
                        CopyLine("Proxy Port", state.proxyPort.toString()) { clipboard.setText(AnnotatedString(state.proxyPort.toString())) }
                        if (host.isNotBlank()) CopyLine("Setup Page", "http://$host:${state.setupPort}") { clipboard.setText(AnnotatedString("http://$host:${state.setupPort}")) }
                        if (hotspot.ssid.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            val data = "WIFI:T:WPA;S:${escapeQr(hotspot.ssid)};P:${escapeQr(hotspot.password)};;"
                            val qr = remember(data) { qrBitmap(data, 480) }
                            qr?.let { Image(it.asImageBitmap(), "Wi-Fi QR", Modifier.size(190.dp).align(Alignment.CenterHorizontally).background(Color.White).padding(8.dp)) }
                            Text("QR اتصال Wi‑Fi", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = PremiumMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            PremiumCard(Modifier.padding(horizontal = 16.dp)) {
                Column {
                    Text("محدودیت‌های عمومی", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(maxClientsText, { maxClientsText = it.filter(Char::isDigit).take(2) }, label = { Text("حداکثر دستگاه فعال") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { GhajarSharePrefs.setMaxClients(context, maxClientsText.toIntOrNull() ?: 3) }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره محدودیت کاربران") }
                    Text("حجم، زمان و سرعت هر کاربر از کارت همان دستگاه قابل تنظیم است.", color = PremiumMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item { Text("دستگاه‌ها", Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold) }
        if (state.clients.isEmpty()) item {
            PremiumCard(Modifier.padding(horizontal = 16.dp)) { Text("هنوز دستگاهی از Proxy استفاده نکرده است.", color = PremiumMuted) }
        } else items(state.clients.values.toList(), key = { it.ip }) { c ->
            PremiumCard(Modifier.padding(horizontal = 16.dp).clickable { showClient = c }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color(0xFF0D2C20)) { Icon(Icons.Default.Router, null, tint = PremiumGreen2, modifier = Modifier.padding(10.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.ip, fontWeight = FontWeight.Bold)
                        Text("${fmtBytes(c.usedBytes)}${if (c.quotaBytes > 0) " / ${fmtBytes(c.quotaBytes)}" else ""} • ${c.activeConnections} اتصال", color = PremiumMuted, fontSize = 11.sp)
                    }
                    Text(if (c.expired) "محدود" else "فعال", color = if (c.expired) PremiumDanger else PremiumGreen2, fontSize = 12.sp)
                }
            }
        }
        item {
            Text("راهنمای اتصال", Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideAccordion("Android", "Wi‑Fi → شبکه Ghajar → Edit/Modify → Advanced → Proxy: Manual → Host و Port بالا را وارد کن.")
                GuideAccordion("iPhone", "QR Setup Page را باز کن و پروفایل را Download/Install کن؛ یا Wi‑Fi → (i) → Configure Proxy → Manual و Host/Port را وارد کن.")
                GuideAccordion("iPad", "مثل iPhone: Profile از Setup Page یا Configure Proxy → Manual برای همان Wi‑Fi.")
                GuideAccordion("Windows", "Settings → Network & Internet → Proxy → Manual proxy setup → Address/Port بالا → Save.")
                GuideAccordion("macOS / MacBook", "System Settings → Network → Wi‑Fi → Details → Proxies → Web Proxy و Secure Web Proxy → Server/Port.")
                GuideAccordion("Linux", "GNOME/KDE: Network/Proxy → Manual → HTTP و HTTPS را روی Host/Port بالا قرار بده. برای CLI می‌توان HTTP_PROXY و HTTPS_PROXY را هم تنظیم کرد.")
            }
        }
        item { Spacer(Modifier.height(25.dp)) }
    }

    showClient?.let { c -> ClientPolicyDialog(c, onDismiss = { showClient = null }) }
}

@Composable
private fun ShareModeCard(mode: GhajarShareMode, selected: GhajarShareMode, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (GhajarShareMode) -> Unit) {
    val on = mode == selected
    Surface(
        Modifier.fillMaxWidth().clickable { onClick(mode) }, shape = RoundedCornerShape(18.dp),
        color = if (on) Color(0xFF0D3023) else PremiumCard,
        border = BorderStroke(1.dp, if (on) PremiumGreen else Color(0xFF1D382F))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (on) PremiumGreen2 else PremiumMuted)
            Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = PremiumMuted, fontSize = 11.sp) }
            if (on) Text("✓", color = PremiumGreen2, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShareStatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(label, color = PremiumMuted); Spacer(Modifier.weight(1f)); Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun CopyLine(label: String, value: String, copy: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, color = PremiumMuted, fontSize = 11.sp); Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        IconButton(onClick = copy) { Icon(Icons.Default.ContentCopy, null, tint = PremiumGreen2) }
    }
}

@Composable
private fun ClientPolicyDialog(client: GhajarShareClient, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var quotaMb by remember(client.ip) { mutableStateOf(if (client.quotaBytes > 0) (client.quotaBytes / 1024 / 1024).toString() else "") }
    var minutes by remember(client.ip) { mutableStateOf(if (client.timeLimitMs > 0) (client.timeLimitMs / 60_000).toString() else "") }
    var speed by remember(client.ip) { mutableStateOf(if (client.speedLimitKbps > 0) client.speedLimitKbps.toString() else "") }
    var blocked by remember(client.ip) { mutableStateOf(GhajarSharePrefs.blocked(context, client.ip)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("محدودیت ${client.ip}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مصرف فعلی: ${fmtBytes(client.usedBytes)}", color = PremiumGreen2)
                OutlinedTextField(quotaMb, { quotaMb = it.filter(Char::isDigit).take(7) }, label = { Text("حجم مجاز (MB) — خالی = نامحدود") }, singleLine = true)
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(6) }, label = { Text("زمان مجاز (دقیقه) — خالی = نامحدود") }, singleLine = true)
                OutlinedTextField(speed, { speed = it.filter(Char::isDigit).take(7) }, label = { Text("حد سرعت (Kbps) — خالی = نامحدود") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("مسدود / Pause", Modifier.weight(1f)); Switch(blocked, { blocked = it }) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val quota = (quotaMb.toLongOrNull() ?: 0L) * 1024L * 1024L
                val time = (minutes.toLongOrNull() ?: 0L) * 60_000L
                GhajarSharePrefs.setPolicy(context, client.ip, quota, time, speed.toIntOrNull() ?: 0)
                GhajarSharePrefs.setBlocked(context, client.ip, blocked)
                onDismiss()
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun GuideAccordion(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().clickable { open = !open }, shape = RoundedCornerShape(16.dp), color = PremiumCard, border = BorderStroke(1.dp, Color(0xFF1D382F))) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold); Icon(Icons.Default.ExpandMore, null, tint = PremiumMuted) }
            AnimatedVisibility(open) { Text(text, color = PremiumMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

private fun qrBitmap(text: String, size: Int): android.graphics.Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until size) for (x in 0 until size) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}.getOrNull()

private fun escapeQr(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
private fun fmtRate(v: Long) = fmtBytes(v) + "/s"
private fun fmtBytes(v: Long): String = when {
    v < 1024 -> "$v B"
    v < 1024L * 1024 -> "%.1f KB".format(v / 1024.0)
    v < 1024L * 1024 * 1024 -> "%.1f MB".format(v / 1024.0 / 1024.0)
    else -> "%.2f GB".format(v / 1024.0 / 1024.0 / 1024.0)
}

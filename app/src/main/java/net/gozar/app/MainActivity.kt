package net.gozar.app

import android.app.Activity
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import dev.chrisbanes.haze.HazeState
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import android.Manifest
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.FilterQuality
import android.content.ContextWrapper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import gozarcore.Gozarcore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URL
import java.time.LocalDate
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.roundToInt

// The two measurement accents. They were a pair of hardcoded cyans chosen by a
// luminance test; each palette now defines its own, so they read correctly on
// white and in Graphite Gold instead of staying cyan everywhere.
private val AppCyan: Color
    @Composable get() = ghajarColors.info

private val AppAqua: Color
    @Composable get() = ghajarColors.highlight

internal val LocalLang = compositionLocalOf { Lang.EN }

// Three tabs only. Declared in reading order, so under RTL the bar reads
// خانه | فروشگاه | تنظیمات from the right. Home is index 0, which also makes
// it the launch page and the target of every "back out of everything".
// SSH and the debugger are not tabs any more - they live in Settings.
private const val PAGE_HOME = 0
private const val PAGE_SHOP = 1
private const val PAGE_SETTINGS = 2
private const val PAGE_COUNT = 3

@Composable
private fun stringsFn(): (String) -> String {
    val lang = LocalLang.current
    return { Strings.get(lang, it) }
}


internal val LocalHazeState = compositionLocalOf<HazeState?> { null }

object WindscribeBrand {

    const val SUB_NAME = "Windscribe"

    fun isWindscribe(sub: Subscription): Boolean {
        val n = sub.name.trim()
        return n.equals(SUB_NAME, ignoreCase = true) ||
                n.startsWith("$SUB_NAME -", ignoreCase = true) ||
                n.startsWith("$SUB_NAME-", ignoreCase = true)
    }

    fun displayName(sub: Subscription, lang: Lang): String =
        if (lang == Lang.FA && isWindscribe(sub)) Strings.get(lang, "ws_title") else sub.name

}

// The Windscribe surfaces used to carry three hand-picked blue ramps chosen by
// a luminance test on the old palette. They now derive from the active theme's
// own card tones, so that screen belongs to the same design system as the rest
// of the app instead of being the one page still wearing blue.
@Composable
private fun windscribeCardBrush(): Brush {
    val c = ghajarColors
    return Brush.linearGradient(listOf(c.card, c.secondaryCard, c.card))
}

@Composable
private fun windscribeRowColor(): Color = ghajarColors.secondaryCard


object ImportBus {
    private val _pending = kotlinx.coroutines.flow.MutableStateFlow<ByteArray?>(null)
    val pending: kotlinx.coroutines.flow.StateFlow<ByteArray?> = _pending
    fun offer(bytes: ByteArray) { _pending.value = bytes }
    fun clear() { _pending.value = null }

    private val _scanned = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val scanned: kotlinx.coroutines.flow.StateFlow<String?> = _scanned
    fun offerScan(text: String) { _scanned.value = text }
    fun clearScan() { _scanned.value = null }
}

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RENEW_SERVICE_USERNAME = "ghajar_renew_service_username"
    }

    private lateinit var store: ConfigStore
    private var afterPermission: (() -> Unit)? = null

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val continuation = afterPermission
            afterPermission = null
            if (result.resultCode == Activity.RESULT_OK) guardedConnect { continuation?.invoke() }
            else VpnState.setDisconnected()
        }

    private var pendingConnect: (() -> Unit)? = null

    /**
     * The notification permission, and what happens the moment it is granted.
     *
     * Two things run here rather than one. The connect that was waiting for it
     * carries on as before - but a grant is also the first moment this app is
     * *allowed* to put anything in the shade, and there is usually something
     * waiting: an expiry warning that arrived while the permission was still
     * missing was fetched, stored, and silently not posted. So a grant kicks
     * the monitor immediately instead of leaving those until the next
     * fifteen-minute pass, which is what made the first notification after
     * granting take a quarter of an hour to show up.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingConnect?.invoke()
            pendingConnect = null
            if (granted) {
                lifecycleScope.launch {
                    GhajarNotificationMonitor.refresh(applicationContext)
                }
            }
        }

    /**
     * Asks for the notification permission when the app is first opened.
     *
     * It used to be asked only on the first connect, which is the wrong
     * moment twice over: a user who opens the app to look at the shop is
     * never asked at all, and the one thing that most needs the permission -
     * being told a service is about to expire - has nothing to do with
     * connecting.
     *
     * Asked once. Android stops showing the dialog after two refusals and
     * returns "denied" instantly from then on, so re-launching it on every
     * cold start would be an invisible no-op that still costs a frame; and a
     * user who said no should be asked again from Settings, on purpose, not
     * by the app repeating itself. The Settings screen already has that row.
     */
    private fun requestNotificationPermissionOnce() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = getSharedPreferences("ghajarvpn_perm", MODE_PRIVATE)
        if (prefs.getBoolean("asked_post_notifications", false)) return
        prefs.edit().putBoolean("asked_post_notifications", true).apply()
        runCatching {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }.onFailure { GhajarLog.e("Startup", "notification permission request failed: ${it.javaClass.simpleName}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GhajarLog.i("Startup", "phase: main activity onCreate begin")
        ConfigQuickConnectBridge.activity = this
        store = ConfigStore.get(applicationContext)
        GhajarLog.i("Startup", "phase: config store loaded")
        UsageStore.init(applicationContext)
        VpnBridge.register(applicationContext)
        GhajarOpenVpnBridge.initialize(applicationContext)
        GhajarLog.i("Startup", "phase: bridges registered")
        handleImportIntent(intent)
        handleRenewIntent(intent)
        IkeController.bind(this)
        watchTunnel()
        // At first launch, not at first connect. The warning this app most
        // needs to deliver - "your service ends tomorrow" - has nothing to do
        // with connecting, and a user who only opens the shop was never asked.
        requestNotificationPermissionOnce()
        GhajarLog.i("Startup", "phase: services bound")
        lifecycleScope.launch {
            VpnState.state.collect { s ->
                if (s == Connection.DISCONNECTED && !IkeController.active) {
                    delay(500)
                    if (VpnState.state.value == Connection.DISCONNECTED) warm()
                }
            }
        }
        lifecycleScope.launch(Dispatchers.Default) {
            Gozarcore.setLogger(object : gozarcore.Logger {
                override fun log(line: String?) {
                    android.util.Log.i("XrayCore", line ?: "")
                }
            })
            withContext(Dispatchers.Main) { warm() }
        }
        startAutoSwitch()
        lifecycleScope.launch {
            SecureScreen.on.collect { secure ->
                if (secure) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent {
            val uiTheme by store.uiTheme.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val palette = ghajarPaletteFor(uiTheme, systemDark)
            val dark = palette.dark
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            androidx.compose.runtime.SideEffect {
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
                // Driven by the active theme instead of two hardcoded colours,
                // so the system bars match every palette, not just the old one.
                @Suppress("DEPRECATION")
                window.navigationBarColor = palette.background.toArgb()
                if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            }
            val lang by store.lang.collectAsState()
            val direction = if (lang == Lang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

            val reduceMotion by store.reduceMotion.collectAsState()
            val useDynamicAccent by store.dynamicAccent.collectAsState()
            val listDensity by store.listDensity.collectAsState()

            GhajarTheme(
                theme = uiTheme,
                typography = if (lang == Lang.FA) VazirTypography else LexendTypography,
                shapes = GhajarSoftShapes,
                reduceMotion = reduceMotion,
                useDynamicAccent = useDynamicAccent,
                listDensity = listDensity
            ) {
                CompositionLocalProvider(
                    LocalLang provides lang,
                    LocalLayoutDirection provides direction
                ) {
                    var showWelcome by remember { mutableStateOf(true) }
                    val pendingOvpn by GhajarOpenVpnBridge.pending.collectAsState()
                    LaunchedEffect(Unit) {
                        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                            while (true) {
                                GhajarNotificationMonitor.refresh(applicationContext)
                                delay(60_000)
                            }
                        }
                    }
                    Box {
                        // The app composes from the first frame now. It used to
                        // wait 1100ms behind the poster before it even started,
                        // which made opening the app the slowest thing in it;
                        // the intro is a 900ms overlay on a screen that is
                        // already built and already interactive underneath.
                        GozarApp(
                            store = store,
                            onConnect = ::connectTo,
                            onDisconnect = ::disconnect,
                            onSwitch = ::switchTo,
                            onCancelPick = ::cancelPick,
                            onConnectOpenVpn = ::connectSavedOpenVpn,
                            onDisconnectOpenVpn = ::disconnectOpenVpn,
                            onTestOpenVpn = ::testSavedOpenVpn
                        )
                        if (showWelcome) {
                            GhajarIntro(onDone = { showWelcome = false })
                        }
                        pendingOvpn?.let { profile ->
                            var ovpnUser by remember(profile) { mutableStateOf(profile.embeddedUsername) }
                            var ovpnPass by remember(profile) { mutableStateOf(profile.embeddedPassword) }
                            var ovpnPing by remember(profile) { mutableStateOf("در حال بررسی پینگ…") }
                            LaunchedEffect(profile) {
                                ovpnPing = when (val result = Pinger.ping(profile.host, profile.port, 3000)) {
                                    is PingResult.Ok -> "پینگ پیش از اتصال: ${result.ms} ms"
                                    else -> "پینگ پیش از اتصال: ناموفق"
                                }
                            }
                            AlertDialog(
                                onDismissRequest = GhajarOpenVpnBridge::dismiss,
                                title = { Text("افزودن OVPN به قاجار") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("${profile.name}\n${profile.host}:${profile.port}\n$ovpnPing")
                                        if (profile.needsCredentials) {
                                            OutlinedTextField(
                                                value = ovpnUser,
                                                onValueChange = { ovpnUser = it },
                                                label = { Text("نام کاربری") },
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = ovpnPass,
                                                onValueChange = { ovpnPass = it },
                                                label = { Text("رمز عبور") },
                                                singleLine = true,
                                                visualTransformation = PasswordVisualTransformation()
                                            )
                                        }
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = GhajarOpenVpnBridge::dismiss) { Text("انصراف") }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { connectImportedOpenVpn(profile, ovpnUser, ovpnPass) },
                                        enabled = !profile.needsCredentials || (ovpnUser.isNotBlank() && ovpnPass.isNotBlank())
                                    ) { Text("اتصال") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
        handleRenewIntent(intent)
    }

    /** Notification's "renew this service" action asks the store screen to open
     * the renewal dialog for exactly that username. */
    private fun handleRenewIntent(intent: Intent?) {
        val username = intent?.getStringExtra(EXTRA_RENEW_SERVICE_USERNAME)?.takeIf { it.isNotBlank() } ?: return
        intent.removeExtra(EXTRA_RENEW_SERVICE_USERNAME)
        GhajarRenewRequest.request(username)
    }

    private fun handleImportIntent(intent: Intent?) {
        intent ?: return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri)
            else -> null
        } ?: return
        lifecycleScope.launch {
            if (uri.scheme.equals("happ", true) || uri.scheme.equals("happ-proxy", true)) {
                store.awaitReady()
                val imported = GhajarCompatibilityImport.parseDeepLink(uri.toString())
                val count = store.addImported(imported)
                Toast.makeText(
                    this@MainActivity,
                    if (count > 0) "$count پیکربندی سازگار به قاجار اضافه شد" else "این لینک رمزگذاری اختصاصی و ناسازگار دارد",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes != null && bytes.isNotEmpty()) {
                val isOvpn = uri.lastPathSegment?.endsWith(".ovpn", true) == true ||
                    bytes.toString(Charsets.UTF_8).contains(Regex("(?im)^\\s*(client|remote)\\b"))
                if (isOvpn) {
                    GhajarOpenVpnBridge.offer(bytes).onFailure {
                        Toast.makeText(this@MainActivity, it.message ?: "فایل OVPN معتبر نیست", Toast.LENGTH_LONG).show()
                    }
                } else ImportBus.offer(bytes)
            }
        }
    }

    private fun connectImportedOpenVpn(profile: PendingOpenVpnImport, username: String, password: String) {
        val start: () -> Unit = {
            lifecycleScope.launch {
                // A bridge failure must never take the whole app down; report it and
                // reset the global tunnel state instead.
                runCatching {
                    GhajarOpenVpnBridge.connect(this@MainActivity, profile, username, password)
                }.onSuccess { result ->
                    result.onSuccess {
                        Toast.makeText(this@MainActivity, "اتصال OVPN آغاز شد", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this@MainActivity, it.message ?: "اتصال OVPN ناموفق بود", Toast.LENGTH_LONG).show()
                    }
                }.onFailure { error ->
                    VpnState.setDisconnected()
                    Toast.makeText(this@MainActivity, error.message ?: "اتصال OVPN ناموفق بود", Toast.LENGTH_LONG).show()
                }
            }
            Unit
        }
        val permission = VpnService.prepare(this)
        if (permission == null) start()
        else {
            afterPermission = start
            vpnPermission.launch(permission)
        }
    }

    private suspend fun stopOtherTunnelBeforeOpenVpn() {
        val activeId = VpnState.activeId.value.orEmpty()
        if (activeId.startsWith("ovpn:")) return
        if (VpnState.state.value != Connection.CONNECTED && VpnState.state.value != Connection.CONNECTING) return
        disconnect()
        withTimeoutOrNull(6_000L) {
            VpnState.state.first { it == Connection.DISCONNECTED || it == Connection.ERROR }
        }
        if (VpnState.state.value != Connection.DISCONNECTED) VpnState.setDisconnected()
        delay(250)
    }

    /** Reverse guard: a core (Xray/IKE) tunnel must not start while OpenVPN owns the tun. */
    private suspend fun stopOpenVpnBeforeCoreTunnel() {
        if (!VpnState.activeId.value.orEmpty().startsWith("ovpn:")) return
        runCatching { GhajarOpenVpnBridge.disconnect(this@MainActivity) }
        withTimeoutOrNull(6_000L) {
            while (VpnState.activeId.value.orEmpty().startsWith("ovpn:")) delay(150)
        }
        delay(250)
    }

    private fun connectSavedOpenVpn(uuid: String) {
        // Routed through the same coordinator as the core tunnel: this is what
        // gives OpenVPN a connect watchdog (so a hung/crashed engine can never
        // leave the UI stuck on "در حال اتصال…" forever) and a mutex so rapid
        // repeated taps on connect/disconnect can't race each other.
        VpnCommandCoordinator.onConnectRequested("ovpn:$uuid") {
            val start: () -> Unit = {
                lifecycleScope.launch {
                    runCatching {
                        stopOtherTunnelBeforeOpenVpn()
                        GhajarOpenVpnBridge.connectSaved(this@MainActivity, uuid)
                    }.fold(
                        onSuccess = { launched ->
                            if (launched.isFailure) {
                                VpnState.setError(launched.exceptionOrNull()?.message ?: "اتصال OVPN آغاز نشد")
                                VpnCommandCoordinator.onTunnelFailed()
                                Toast.makeText(this@MainActivity,
                                    launched.exceptionOrNull()?.message ?: "اتصال OVPN آغاز نشد",
                                    Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@MainActivity, "اتصال OVPN آغاز شد", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onFailure = { error ->
                            VpnState.setError(error.message ?: "اتصال OVPN آغاز نشد")
                            VpnCommandCoordinator.onTunnelFailed()
                            Toast.makeText(this@MainActivity, error.message ?: "اتصال OVPN آغاز نشد", Toast.LENGTH_LONG).show()
                        }
                    )
                }
                Unit
            }
            val permission = VpnService.prepare(this)
            if (permission == null) start()
            else {
                afterPermission = start
                vpnPermission.launch(permission)
            }
        }
    }

    private fun disconnectOpenVpn() {
        VpnCommandCoordinator.onDisconnectRequested {
            lifecycleScope.launch {
                runCatching { GhajarOpenVpnBridge.disconnect(this@MainActivity) }
                    .onFailure { VpnCommandCoordinator.onTunnelFailed() }
            }
        }
    }

    private fun testSavedOpenVpn(uuid: String) {
        val start: () -> Unit = {
            lifecycleScope.launch {
                val result = runCatching {
                    stopOtherTunnelBeforeOpenVpn()
                    GhajarOpenVpnBridge.testSaved(this@MainActivity, uuid)
                }.getOrElse {
                    VpnState.setDisconnected()
                    GhajarOvpnTestResult(ok = false, message = it.message ?: "تست اجرا نشد")
                }
                Toast.makeText(
                    this@MainActivity,
                    if (result.ok == true) "تست واقعی موفق: ${result.connectMs ?: 0}ms"
                    else "تست OVPN ناموفق: ${result.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            Unit
        }
        val permission = VpnService.prepare(this)
        if (permission == null) start() else {
            afterPermission = start
            vpnPermission.launch(permission)
        }
    }

    private fun launchConnect(config: ProxyConfig, attempt: Int = 0) {
        if (VpnState.state.value == Connection.DISCONNECTING) return
        // OpenVPN owns the tun while it is actively connecting/connected; tear it
        // down first so the core tunnel does not fight the engine for the VPN
        // interface. NOTE: the guard used to be `!= DISCONNECTED`, which also
        // matched ERROR (OpenVPN's resting state after any failed/crashed
        // connect). Since stopOpenVpnBeforeCoreTunnel() never changes a resting
        // ERROR status, that made this recurse into itself with zero delay
        // forever any time OpenVPN had previously failed once - flooding
        // VpnCommandCoordinator.onConnectRequested (seen in logs as connect#7..
        // #25 within ~3s) and ultimately crashing GozarVpnService. Only actually
        // wait when OpenVPN is mid-flight.
        val ovpnBusy = GhajarOpenVpnBridge.status.value == GhajarOvpnState.CONNECTING ||
            GhajarOpenVpnBridge.status.value == GhajarOvpnState.CONNECTED
        if (ovpnBusy) {
            if (attempt >= 20) {
                VpnState.setError("موتور OpenVPN پاسخ نمی‌دهد؛ برنامه را ببند و دوباره باز کن.")
                return
            }
            lifecycleScope.launch {
                runCatching { stopOpenVpnBeforeCoreTunnel() }
                delay(150)
                launchConnect(config, attempt + 1)
            }
            return
        }
        VpnCommandCoordinator.onConnectRequested(config.id, if (config.protocol == "psiphon") { when (OblivionOptions(config.oblivionJson).core) { "chain" -> 290_000L; "aether" -> 200_000L; else -> 100_000L } } else if (config.protocol == "aether") 200_000L else 45_000L) {
            if (config.allowInsecure && !CertPin.isValid(config.pinnedCertSha256) &&
                config.security.trim().lowercase() == "tls"
            ) {
                lifecycleScope.launch {
                    val pin = CertPin.fetch(config.address, config.port, config.sni)
                    val ready = if (pin.isNullOrBlank()) config
                    else config.copy(pinnedCertSha256 = pin).also { store.update(it) }
                    proceedLaunch(ready)
                }
                return@onConnectRequested
            }
            proceedLaunch(config)
        }
    }

    private fun proceedLaunch(config: ProxyConfig) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingConnect = { proceedConnect(config) }
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            proceedConnect(config)
        }
    }

    private fun connectTo(config: ProxyConfig) {
        // Any request to connect to a specific server - the picker's play
        // button, the home screen's main button, a quick-connect shortcut -
        // makes that server the selection, exactly like tapping the row
        // itself already does via ConfigPickerScreen's onSelect. This used
        // to only happen for the row tap: connecting via the play button
        // left the previous selection in store.selectedId untouched, so the
        // home button (which always reads store.selectedId) reconnected to
        // a stale, different server after the next disconnect.
        store.setSelectedId(config.id)

        when (ConnectDecision.resolve(VpnState.state.value, VpnState.activeId.value, config.id)) {
            ConnectAction.IGNORE -> return
            // Already on a tunnel to a different server: switch through the
            // same disconnect-then-connect sequencing switchTo() already
            // uses for a row tap while connected, instead of silently doing
            // nothing.
            ConnectAction.SWITCH -> { switchTo(config); return }
            ConnectAction.CONNECT -> Unit
        }

        if (!store.autoSelect.value) {
            launchConnect(config)
            return
        }

        pickJob?.cancel()
        pickJob = lifecycleScope.launch {
            VpnState.setPicking(true)
            val best = try {
                AutoSelector(applicationContext, store).pickFastest()
            } catch (e: CancellationException) {
                VpnState.setPicking(false)
                throw e
            } catch (e: Exception) {
                null
            }
            ensureActive()
            val chosen = best ?: config
            if (chosen.id != config.id) store.setSelectedId(chosen.id)
            VpnState.setPicking(false)
            launchConnect(chosen)
        }
    }

    private fun cancelPick() {
        pickJob?.cancel()
        pickJob = null
        VpnState.setPicking(false)
    }

    private fun watchTunnel() {
        lifecycleScope.launch {
            VpnState.state.collect { state ->
                if (state == Connection.CONNECTED && !IkeController.active) {
                    // OpenVPN drives VpnState through its own bridge; the Xray-based
                    // health probe cannot run through an OpenVPN tun and only produces
                    // false "not alive" verdicts there.
                    if (VpnState.activeId.value.orEmpty().startsWith("ovpn:")) return@collect
                    TunnelHealth.check()
                    RadarRunner.start(true)
                    val id = VpnState.activeId.value
                    store.configs.value.find { it.id == id }?.let {
                        DebugRunner.start(it, store)
                    }
                } else if (state == Connection.DISCONNECTED) {
                    TunnelHealth.reset()
                    RadarRunner.start(false)
                } else {
                    TunnelHealth.reset()
                }
            }
        }
    }

    private fun t2(key: String): String = Strings.get(store.lang.value, key)


    private var pickJob: Job? = null
    private var autoSwitchJob: Job? = null
    private val AUTO_SWITCH_MS = 60_000L
    private val AUTO_SWITCH_MARGIN_MS = 40
    private val AUTO_SWITCH_PROBE_MS = 25_000L

    private fun startAutoSwitch() {
        if (autoSwitchJob?.isActive == true) return
        autoSwitchJob = lifecycleScope.launch {
            val selector = AutoSelector(applicationContext, store)
            while (isActive) {
                delay(AUTO_SWITCH_MS)
                val tag = "GhajarAuto"
                if (!store.autoSelect.value) {
                    android.util.Log.d(tag, "skip: smart connect is off"); continue
                }
                if (VpnState.state.value != Connection.CONNECTED) {
                    android.util.Log.d(tag, "skip: state is ${VpnState.state.value}"); continue
                }
                if (VpnState.picking.value) {
                    android.util.Log.d(tag, "skip: a pick is already running"); continue
                }

                val activeId = VpnState.activeId.value ?: store.selectedId.value
                if (activeId == null) {
                    android.util.Log.d(tag, "skip: no active config id"); continue
                }
                if (activeId.startsWith("ovpn:")) {
                    android.util.Log.d(tag, "skip: OpenVPN owns the active tunnel"); continue
                }
                val activeCfg = store.configs.value.find { it.id == activeId }
                if (activeCfg?.protocol?.trim()?.lowercase() in setOf("tor", "aether")) {
                    android.util.Log.d(tag, "skip: active is ${activeCfg?.protocol}"); continue
                }

                val best = runCatching { selector.pickFastest(AUTO_SWITCH_PROBE_MS) }
                    .onFailure { android.util.Log.w(tag, "probe threw", it) }
                    .getOrNull()
                if (best == null) {
                    android.util.Log.d(tag, "skip: no server responded to the probe"); continue
                }

                val results = selector.results.value
                val bestMs = (results[best.id] as? PingResult.Ok)?.ms
                val currentMs = (results[activeId] as? PingResult.Ok)?.ms
                android.util.Log.d(
                    tag,
                    "active=${activeCfg?.name} ${currentMs}ms  best=${best.name} ${bestMs}ms"
                )

                if (best.id == activeId) {
                    android.util.Log.d(tag, "skip: already on the fastest"); continue
                }
                if (bestMs == null) {
                    android.util.Log.d(tag, "skip: best has no timing"); continue
                }
                if (currentMs != null && currentMs - bestMs < AUTO_SWITCH_MARGIN_MS) {
                    android.util.Log.d(
                        tag,
                        "skip: gain ${currentMs - bestMs}ms under margin $AUTO_SWITCH_MARGIN_MS"
                    ); continue
                }

                android.util.Log.d(tag, "SWITCHING to ${best.name}")
                store.setSelectedId(best.id)
                switchTo(best)
            }
        }
    }

    private fun guardedConnect(block: () -> Unit) {
        try { block() }
        catch (error: LinkageError) {
            GhajarLog.e("GhajarConnect", "LinkageError: ${error.message}")
            VpnState.setError("هستهٔ اتصال بارگذاری نشد؛ نسخهٔ سازگار با گوشی را نصب کن.")
            VpnCommandCoordinator.onTunnelFailed()
        } catch (error: Exception) {
            // Previously only the exception's class name went to plain Logcat
            // (android.util.Log), which never reaches the exported "لاگ و
            // اشکال‌زدایی" screen - so every failure here looked identical in
            // the log with no way to tell what actually broke. Route the real
            // class + message through GhajarLog so it's actually diagnosable
            // from an exported log next time this fires.
            GhajarLog.e("GhajarConnect", "${error.javaClass.name}: ${error.message}")
            android.util.Log.e("GhajarConnect", "connect failed", error)
            VpnState.setError("شروع اتصال ناموفق بود؛ مجوز VPN و تنظیمات سرویس را بررسی کن.")
            VpnCommandCoordinator.onTunnelFailed()
        }
    }

    private fun proceedConnect(config: ProxyConfig) {
        guardedConnect { proceedConnectChecked(config) }
    }

    /** Entry for ConfigQuickConnectActivity: launches through the guarded path. */
    fun quickConnect(config: ProxyConfig) {
        connectTo(config)
    }

    private fun proceedConnectChecked(config: ProxyConfig) {
        if (VpnState.state.value == Connection.CONNECTED) return
        if (config.protocol == "ikev2") {
            val xrayWasUp = VpnState.state.value != Connection.DISCONNECTED
            IkeController.claim(config)
            if (xrayWasUp) startService(
                Intent(this, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_STOP)
            )
            val startIke = {
                if (!IkeController.connect(this, config)) {
                    Toast.makeText(this, t2("ikev2_bad_config"), Toast.LENGTH_LONG).show()
                    VpnState.setDisconnected()
                }
            }
            val consent = runCatching { VpnService.prepare(this) }.getOrNull()
            if (consent != null) {
                afterPermission = startIke
                vpnPermission.launch(consent)
            } else startIke()
            return
        }
        if (IkeController.active) IkeController.disconnect(this)
        val json = ConfigBuilder.build(config, store.fragment.value, store.splitRouting.value, store.sniffing.value, store.sniffTypes.value, mux = store.mux.value, muxConcurrency = store.muxConcurrency.value, adBlock = store.adBlock.value, fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            customDns = store.customDns.value,
            youtubeDirect = store.youtubeDirect.value,
            noiseSpec = store.noiseSpec.value,
            fragmentPackets = store.fragmentPackets.value,
            fragmentLength = store.fragmentLength.value,
            fragmentInterval = store.fragmentInterval.value,
            torBase = if (config.protocol == "tor" && config.torBaseId.isNotEmpty())
                store.configs.value.find { it.id == config.torBaseId } else null,
            chainBase = if (config.chainId.isNotEmpty())
                store.configs.value.find { it.id == config.chainId } else null,
            onionRouting = store.onionRouting.value,
            coreLogLevel = store.coreLogLevel.value,
            shareOnLan = store.vpnShareEnabled.value,
            shareUser = if (store.vpnShareEnabled.value) store.ensureVpnShareCredential().first else "",
            sharePass = store.vpnSharePassword.value,
            shareListenAddress = if (store.vpnShareEnabled.value) hotspotInterfaceAddress() ?: "127.0.0.1" else "127.0.0.1")
        VpnState.setConnecting(config.id)
        val aether = AetherController.spec(config)
        val psiphon = PsiphonSpec.from(config)?.toJson()
        val intent = VpnService.prepare(this)
        val tor = when {
            config.protocol == "tor" ->
                config.torCountry + "|" + (if (config.torThroughVpn) "1" else "0")
            store.onionRouting.value -> "|1"
            else -> null
        }
        if (intent != null) { afterPermission = { startTunnel(json, config.name, aether, tor, psiphon, config.address, config.port) }; vpnPermission.launch(intent) }
        else startTunnel(json, config.name, aether, tor, psiphon, config.address, config.port)
    }

    private fun startTunnel(
        configJson: String, name: String, aether: String, tor: String?, psiphon: String? = null,
        address: String = "", port: Int = 0, attempt: Int = 0
    ) {
        guardedConnect {
            try {
                androidx.core.content.ContextCompat.startForegroundService(this,
                    Intent(this, GozarVpnService::class.java)
                        .putExtra(GozarVpnService.EXTRA_CONFIG, configJson)
                        .putExtra(GozarVpnService.EXTRA_NAME, name)
                        .putExtra(GozarVpnService.EXTRA_AETHER, aether)
                        .putExtra(GozarVpnService.EXTRA_TOR, tor)
                        .putExtra(GozarVpnService.EXTRA_PSIPHON, psiphon)
                        .putExtra(GozarVpnService.EXTRA_STOP_LABEL, Strings.get(store.lang.value, "disconnect"))
                        .putExtra(GozarVpnService.EXTRA_ADDRESS, address)
                        .putExtra(GozarVpnService.EXTRA_PORT, port)
                )
            } catch (error: SecurityException) {
                // "process is bad" is ActivityManager refusing to launch a
                // process it flagged after repeated crashes. The old code here
                // assumed the flag clears "within a second or two" and retried
                // three times; the 2026-09-18 log disproves that - two separate
                // connects, eight seconds apart, both refused. The flag lives
                // until the app is force-stopped, updated or the device
                // reboots, so retrying cannot clear it.
                //
                // The service no longer runs in its own :vpn process (see the
                // manifest), so the process being started is the one the user
                // just launched and cannot be in that state. This branch is now
                // only a last resort: one retry for a genuinely transient
                // refusal, then an error that says what to actually do.
                val processBad = error.message?.contains("process is bad", ignoreCase = true) == true
                GhajarLog.e("GhajarConnect", "service start refused: ${error.message}")
                if (processBad && attempt < 1) {
                    lifecycleScope.launch {
                        delay(700L)
                        startTunnel(configJson, name, aether, tor, psiphon, address, port, attempt + 1)
                    }
                } else if (processBad) {
                    VpnState.setError(Strings.get(store.lang.value, "err_process_bad"))
                } else {
                    throw error
                }
            }
        }
    }

    private fun startBlockOnly() {
        if (!store.adBlock.value || !store.blockWhenOff.value) return
        if (VpnService.prepare(this) != null) return
        val json = ConfigBuilder.build(
            ProxyConfig(name = "adblock", protocol = "freedom", address = "127.0.0.1", port = 1),
            splitRouting = store.splitRouting.value,
            sniffing = store.sniffing.value,
            sniffTypes = store.sniffTypes.value,
            adBlock = true,
            directOnly = true,
            fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            customDns = store.customDns.value,
            youtubeDirect = store.youtubeDirect.value,
            noiseSpec = store.noiseSpec.value,
            fragmentPackets = store.fragmentPackets.value,
            fragmentLength = store.fragmentLength.value,
            fragmentInterval = store.fragmentInterval.value,
            coreLogLevel = store.coreLogLevel.value
        )
        startTunnel(json, Strings.get(store.lang.value, "adblock_notif"), "", null)
    }

    private fun disconnect() {
        // Serialized through the coordinator: rapid connect/disconnect taps always
        // resolve with the latest intent winning and watchdogs reconciling hangs.
        VpnCommandCoordinator.onDisconnectRequested {
            // Ask the embedded engine to stop even when the app process was recreated
            // and no longer remembers the ovpn: id, otherwise its notification lingers.
            if (VpnState.activeId.value.orEmpty().startsWith("ovpn:") ||
                GhajarOpenVpnBridge.status.value != GhajarOvpnState.DISCONNECTED
            ) {
                lifecycleScope.launch { runCatching { GhajarOpenVpnBridge.disconnect(this@MainActivity) } }
                if (VpnState.activeId.value.orEmpty().startsWith("ovpn:")) return@onDisconnectRequested
            }
            if (IkeController.active) {
                IkeController.disconnect(this)
                VpnState.setDisconnected()
                return@onDisconnectRequested
            }
            startService(Intent(this, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_STOP))
        }
    }

    private fun switchTo(config: ProxyConfig) {
        val s = VpnState.state.value
        if (s != Connection.CONNECTED && s != Connection.CONNECTING) { launchConnect(config); return }
        lifecycleScope.launch {
            disconnect()
            withTimeoutOrNull(6000) {
                VpnState.state.first { it == Connection.DISCONNECTED || it == Connection.ERROR }
            }
            if (VpnState.state.value == Connection.CONNECTED) VpnState.setDisconnected()
            delay(400)
            launchConnect(config)
        }
    }

    private fun warm() {
        if (IkeController.active) return
        val s = VpnState.state.value
        if (s == Connection.CONNECTING || s == Connection.CONNECTED || s == Connection.DISCONNECTING) return
        runCatching {
            startService(Intent(this, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_WARM))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GozarApp(
    store: ConfigStore,
    onConnect: (ProxyConfig) -> Unit,
    onDisconnect: () -> Unit,
    onSwitch: (ProxyConfig) -> Unit,
    onCancelPick: () -> Unit = {},
    onConnectOpenVpn: (String) -> Unit = {},
    onDisconnectOpenVpn: () -> Unit = {},
    onTestOpenVpn: (String) -> Unit = {}
) {
    val t = stringsFn()
    val scope = rememberCoroutineScope()
    val effectiveDark = ghajarColors.dark
    val pagerState = rememberPagerState(initialPage = PAGE_HOME, pageCount = { PAGE_COUNT })
    val settingsScroll = rememberScrollState()

    var showPicker by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showProjects by remember { mutableStateOf(false) }
    var showTorNodes by remember { mutableStateOf(false) }
    var showWindscribe by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showOpenVpnHub by remember { mutableStateOf(false) }
    var showPsiphonHub by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<ProxyConfig?>(null) }
    val updateCtx = LocalContext.current
    /**
     * The update check, on opening the app rather than once a day.
     *
     * It used to be gated to twenty-four hours, which meant a user could open
     * the app repeatedly on the day a release went out and never be told - the
     * owner's complaint exactly. The floor is fifteen minutes now, which is
     * "every time you open it" for anybody who is not re-opening it in a loop,
     * and still keeps a phone well inside GitHub's unauthenticated rate limit
     * of sixty requests an hour per address.
     *
     * A note on what "automatic" can mean here: an app that is not the device
     * owner cannot install a package without the system installer's own
     * confirmation. So the automatic part is everything up to that - finding
     * the release, downloading it, checking its SHA-256 and that its signature
     * matches the installed app - and the last step is one tap on a button
     * that says نصب. Claiming a silent install would be claiming something
     * Android does not allow.
     */
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - store.lastUpdateCheck() >= 15L * 60 * 1000L) {
            val ver = runCatching {
                updateCtx.packageManager.getPackageInfo(updateCtx.packageName, 0).versionName
            }.getOrNull() ?: ""
            val r = UpdateChecker.check(ver)
            store.markUpdateChecked()
            if (r is UpdateChecker.Result.Available) GhajarUpdateFlow.offer(r)
        }
    }
    val pendingUpdate by GhajarUpdateFlow.available.collectAsState()
    pendingUpdate?.let { upd -> UpdateFlowDialog(upd, onDismiss = { GhajarUpdateFlow.clear() }) }
    var usageDetail by remember { mutableStateOf(false) }
    var perAppDetail by remember { mutableStateOf(false) }
    var logsDetail by remember { mutableStateOf(false) }
    var stabilityDetail by remember { mutableStateOf(false) }
    var aboutDetail by remember { mutableStateOf(false) }
    var themeDetail by remember { mutableStateOf(false) }
    var cleanIpDetail by remember { mutableStateOf(false) }
    var dnsLabDetail by remember { mutableStateOf(false) }
    var mapDetail by remember { mutableStateOf(false) }
    var netMonDetail by remember { mutableStateOf(false) }
    var netCatDetail by remember { mutableStateOf(false) }
    var netCatIndex by remember { mutableStateOf(-1) }
    var checkHostDetail by remember { mutableStateOf(false) }
    var toolsDetail by remember { mutableStateOf(false) }
    var connDetail by remember { mutableStateOf(false) }
    var prefsDetail by remember { mutableStateOf(false) }
    // Notification settings used to be buried inside the shop's third
    // section. The brief puts notifications in categorized Settings, so they
    // get a page of their own here.
    var notifDetail by remember { mutableStateOf(false) }
    var exportConfigs by remember { mutableStateOf<List<ProxyConfig>?>(null) }
    val sortMode by store.sortMode.collectAsState()
    val selectedId by store.selectedId.collectAsState()
    val pings = remember { mutableStateMapOf<String, PingResult>() }

    LaunchedEffect(Unit) {
        store.awaitReady()
        store.removeLegacyDefaultAetherSeed()
        while (true) {
            SubscriptionRefresher.refreshStale(store)
            delay(30 * 60 * 1000L)
        }
    }

    val importContext = LocalContext.current
    val pendingImport by ImportBus.pending.collectAsState()
    var importNeedsPassword by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf("") }
    var importBusy by remember { mutableStateOf(false) }

    LaunchedEffect(pendingImport) {
        val bytes = pendingImport ?: return@LaunchedEffect
        importPassword = ""
        importError = ""
        val plain = withContext(Dispatchers.Default) {
            runCatching { ConfigParser.parseBundle(String(bytes, Charsets.UTF_8)) }
                .getOrDefault(emptyList())
        }
        if (plain.isNotEmpty()) {
            val added = store.addImported(plain)
            android.widget.Toast.makeText(
                importContext, t("import_success").format(added),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            ImportBus.clear()
            return@LaunchedEffect
        }
        // OpenVPN profiles are managed by the Ghajar OVPN bridge, not the core parser.
        val looksOvpn = runCatching {
            String(bytes, Charsets.UTF_8).contains(Regex("(?im)^\\s*(client|remote|<connection>)\\b"))
        }.getOrDefault(false)
        if (looksOvpn) {
            GhajarOpenVpnBridge.offer(bytes)
                .onSuccess { ImportBus.clear() }
                .onFailure {
                    android.widget.Toast.makeText(
                        importContext, it.message ?: "فایل OVPN معتبر نیست",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    ImportBus.clear()
                }
            return@LaunchedEffect
        }
        importNeedsPassword = runCatching { ConfigFile.isPasswordProtected(bytes) }.getOrDefault(false)
        if (!importNeedsPassword) {
            val configs = withContext(Dispatchers.Default) {
                runCatching { ConfigFile.decode(importContext, bytes, null) }.getOrNull()
            }
            if (configs != null) {
                val n = store.addImported(configs)
                android.widget.Toast.makeText(importContext, t("import_success").format(n), android.widget.Toast.LENGTH_SHORT).show()
                ImportBus.clear()
            } else {
                importNeedsPassword = true
            }
        }
    }

    if (pendingImport != null && importNeedsPassword) {
        GlassDialog(
            onDismiss = { if (!importBusy) ImportBus.clear() },
            title = t("import_title"),
            confirmLabel = t("import_button"),
            dismissLabel = t("cancel"),
            onConfirm = {
                val bytes = pendingImport
                if (bytes != null && !importBusy && importPassword.isNotEmpty()) {
                    importBusy = true
                    scope.launch {
                        val configs = withContext(Dispatchers.Default) {
                            runCatching { ConfigFile.decode(importContext, bytes, importPassword) }
                        }
                        importBusy = false
                        configs.onSuccess { list ->
                            val n = store.addImported(list)
                            android.widget.Toast.makeText(importContext, t("import_success").format(n), android.widget.Toast.LENGTH_SHORT).show()
                            ImportBus.clear()
                            importNeedsPassword = false
                        }.onFailure { e ->
                            importError = when (e) {
                                is ConfigFile.WrongPassword -> t("import_wrong_password")
                                is ConfigFile.ForeignApp -> t("import_foreign_app")
                                else -> t("import_bad_file")
                            }
                        }
                    }
                }
            }
        ) {
            Text(t("import_needs_password"), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                importPassword, { importPassword = it; importError = "" },
                label = { Text(t("import_password")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (importError.isNotEmpty())
                Text(importError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    var sshSubScreen by remember { mutableStateOf(false) }
    // SSH and the debugger moved out of the tab bar into Settings; they keep
    // their own screens and every capability, just reached from there.
    var sshDetail by remember { mutableStateOf(false) }
    var debugDetail by remember { mutableStateOf(false) }
    val page = pagerState.currentPage
    val onSettingsTab = page == PAGE_SETTINGS
    val subScreenOpen = (page == PAGE_HOME && (showPicker || showManual || showProjects || showTorNodes || showWindscribe || showScanner || showOpenVpnHub || showPsiphonHub || exportConfigs != null)) || (onSettingsTab && (usageDetail || perAppDetail || logsDetail || stabilityDetail || aboutDetail || cleanIpDetail || dnsLabDetail || mapDetail || themeDetail || toolsDetail || connDetail || prefsDetail || netMonDetail || netCatDetail || netCatIndex >= 0 || checkHostDetail || sshDetail || debugDetail))

    val screenKey = when {
        page == PAGE_SHOP -> "shop"
        page == PAGE_HOME && exportConfigs != null -> "export"
        page == PAGE_HOME && showManual -> "manual"
        page == PAGE_HOME && showTorNodes -> "tornodes"
        page == PAGE_HOME && showScanner -> "scanqr"
        page == PAGE_HOME && showWindscribe -> "windscribe"
        page == PAGE_HOME && showProjects -> "projects"
        page == PAGE_HOME && showOpenVpnHub -> "openvpnhub"
        page == PAGE_HOME && showPsiphonHub -> "psiphonhub"
        page == PAGE_HOME && showPicker -> "picker"
        page == PAGE_HOME -> "connection"
        onSettingsTab && sshDetail -> "ssh"
        onSettingsTab && debugDetail -> "debugger"
        onSettingsTab && usageDetail -> "usage"
        onSettingsTab && perAppDetail -> "perapp"
        onSettingsTab && logsDetail -> "logs"
        onSettingsTab && stabilityDetail -> "stability"
        onSettingsTab && aboutDetail -> "about"
        onSettingsTab && themeDetail -> "theme"
        onSettingsTab && cleanIpDetail -> "cleanip"
        onSettingsTab && dnsLabDetail -> "dnslab"
        onSettingsTab && mapDetail -> "map"
        onSettingsTab && checkHostDetail -> "checkhost"
        onSettingsTab && netCatIndex >= 0 -> "netcatone"
        onSettingsTab && netCatDetail -> "netcat"
        onSettingsTab && netMonDetail -> "netmon"
        onSettingsTab && toolsDetail -> "tools"
        onSettingsTab && connDetail -> "connection_settings"
        onSettingsTab && notifDetail -> "notifications"
        onSettingsTab && prefsDetail -> "preferences"
        else -> "settings"
    }

    fun pop() {
        when {
            exportConfigs != null -> exportConfigs = null
            showManual -> { showManual = false; editingConfig = null }
            showWindscribe -> showWindscribe = false
            showScanner -> showScanner = false
            showTorNodes -> showTorNodes = false
            showProjects -> showProjects = false
            showOpenVpnHub -> showOpenVpnHub = false
            showPsiphonHub -> showPsiphonHub = false
            showPicker -> showPicker = false
            usageDetail -> usageDetail = false
            perAppDetail -> perAppDetail = false
            logsDetail -> logsDetail = false
            stabilityDetail -> stabilityDetail = false
            aboutDetail -> aboutDetail = false
            themeDetail -> themeDetail = false
            cleanIpDetail -> cleanIpDetail = false
            dnsLabDetail -> dnsLabDetail = false
            mapDetail -> mapDetail = false
            checkHostDetail -> checkHostDetail = false
            netCatIndex >= 0 -> netCatIndex = -1
            netCatDetail -> netCatDetail = false
            netMonDetail -> netMonDetail = false
            toolsDetail -> toolsDetail = false
            connDetail -> connDetail = false
            notifDetail -> notifDetail = false
            prefsDetail -> prefsDetail = false
            // SSH owns its own inner navigation; let it handle its own back.
            sshDetail && sshSubScreen -> Unit
            sshDetail -> sshDetail = false
            debugDetail -> debugDetail = false
            page != PAGE_HOME -> scope.launch { pagerState.animateScrollToPage(PAGE_HOME) }
        }
    }

    val canGoBack = subScreenOpen || page != PAGE_HOME
    var backProgress by remember { mutableStateOf(0f) }

    PredictiveBackHandler(enabled = canGoBack) { progress ->
        try {
            progress.collect { event -> backProgress = event.progress }
            backProgress = 0f
            pop()
        } catch (e: CancellationException) {
            backProgress = 0f
        }
    }

    val contentScale = 1f - backProgress * 0.08f
    val contentAlpha = 1f - backProgress * 0.25f

    val gradBg = MaterialTheme.colorScheme.background
    // The canvas wash follows the theme's brand tone; it used to be a fixed
    // blue, which is why every theme still had a blue cast at the top.
    val gradAccent = ghajarColors.primary
    val gradDark = gradBg.luminance() < 0.5f
    val gradient = remember(gradBg, gradAccent, gradDark) {
        if (gradDark) Brush.verticalGradient(
            0f to lerp(gradBg, gradAccent, 0.12f),
            0.45f to lerp(gradBg, gradAccent, 0.05f),
            1f to gradBg
        ) else SolidColor(gradBg)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.background(gradient),
        topBar = {
            Column {
            CenterAlignedTopAppBar(
                // The bar paints nothing of its own: the Scaffold's canvas wash
                // shows straight through, so the header and the page under it
                // are one continuous tone instead of two stacked panels.
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    if (screenKey == "connection") {
                        GhajarWordmark(Modifier.height(48.dp).width(164.dp))
                    } else {
                        Text(
                            mixedText(when (screenKey) {
                                "manual" -> if (editingConfig != null) t("edit_config_title") else t("add_config_title")
                                "export" -> t("export_title")
                                "picker" -> t("choose_server")
                                "projects" -> t("free_projects")
                                "tornodes" -> t("tor_nodes")
                                "windscribe" -> t("ws_title")
                                "openvpnhub" -> "OpenVPN"
                                "psiphonhub" -> "Psiphon"
                                "scanqr" -> t("scan_qr")
                                "usage" -> t("data_usage")
                                "perapp" -> t("per_app")
                                "logs" -> t("xray_logs")
                                "stability" -> t("stab_title")
                                "about" -> t("about")
                                "theme" -> t("theme_settings")
                                "cleanip" -> t("scan_title")
                                "netmon" -> t("netmon_title")
                                "netcat" -> t("netcat_title")
                                "checkhost" -> t("chk_title")
                                "netcatone" -> t(NetMonitor.Categories.getOrNull(netCatIndex)?.key ?: "netcat_title")
                                "tools" -> t("tools")
                                "connection_settings" -> t("connection_settings")
                                "preferences" -> t("preferences")
                                "shop" -> t("shop")
                                "ssh" -> t("ssh")
                                "debugger" -> t("debugger_title")
                                else -> t("settings")
                            }),
                            fontFamily = AntaFont
                        )
                    }
                },
                navigationIcon = {
                    when (screenKey) {
                        "manual" -> BounceIconButton(onClick = { showManual = false; editingConfig = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "export" -> BounceIconButton(onClick = { exportConfigs = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "picker" -> BounceIconButton(onClick = { showPicker = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "projects" -> BounceIconButton(onClick = { showProjects = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "tornodes" -> BounceIconButton(onClick = { showTorNodes = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "windscribe" -> BounceIconButton(onClick = { showWindscribe = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "openvpnhub" -> BounceIconButton(onClick = { showOpenVpnHub = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "psiphonhub" -> BounceIconButton(onClick = { showPsiphonHub = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "scanqr" -> BounceIconButton(onClick = { showScanner = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "usage" -> BounceIconButton(onClick = { usageDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "perapp" -> BounceIconButton(onClick = { perAppDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "logs" -> BounceIconButton(onClick = { logsDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "stability" -> BounceIconButton(onClick = { stabilityDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "about" -> BounceIconButton(onClick = { aboutDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "theme" -> BounceIconButton(onClick = { themeDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "cleanip" -> BounceIconButton(onClick = { cleanIpDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "dnslab" -> BounceIconButton(onClick = { dnsLabDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "map" -> BounceIconButton(onClick = { mapDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "netmon" -> BounceIconButton(onClick = { netMonDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "netcat" -> BounceIconButton(onClick = { netCatDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "checkhost" -> BounceIconButton(onClick = { checkHostDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "netcatone" -> BounceIconButton(onClick = { netCatIndex = -1 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "tools" -> BounceIconButton(onClick = { toolsDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "connection_settings" -> BounceIconButton(onClick = { connDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "preferences" -> BounceIconButton(onClick = { prefsDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = {
                    BounceIconButton(onClick = {
                        // Quick light/dark flip within the brand identity; the
                        // full theme list lives in Settings -> Appearance.
                        store.setUiTheme(
                            if (effectiveDark) GhajarThemeId.PREMIUM_GREEN_LIGHT
                            else GhajarThemeId.PREMIUM_GREEN_DARK
                        )
                    }) {
                        Icon(
                            if (effectiveDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle theme"
                        )
                    }
                }
            )
            GhajarNoticeBanner()
            }
        },
        bottomBar = {
            // A floating capsule with one filled indicator that slides between
            // the three destinations. Same three destinations, same reset
            // behaviour on tap.
            SkinNavBar(
                selected = page,
                items = listOf(
                    SkinNavItem(R.drawable.ic_royal_home, t("home")) {
                        showPicker = false; showManual = false; showProjects = false
                        showTorNodes = false; showWindscribe = false; editingConfig = null
                        scope.launch { pagerState.animateScrollToPage(PAGE_HOME) }
                    },
                    SkinNavItem(R.drawable.ic_royal_shop, t("shop")) {
                        scope.launch { pagerState.animateScrollToPage(PAGE_SHOP) }
                    },
                    SkinNavItem(R.drawable.ic_royal_settings, t("settings")) {
                        usageDetail = false
                        perAppDetail = false
                        logsDetail = false
                        stabilityDetail = false
                        aboutDetail = false
                        themeDetail = false
                        cleanIpDetail = false
                        dnsLabDetail = false
                        mapDetail = false
                        netMonDetail = false
                        netCatDetail = false
                        netCatIndex = -1
                        checkHostDetail = false
                        toolsDetail = false
                        connDetail = false
                        prefsDetail = false
                        sshDetail = false
                        debugDetail = false
                        notifDetail = false
                        scope.launch { pagerState.animateScrollToPage(PAGE_SETTINGS) }
                    }
                )
            )
        }
    ) { padding ->
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val layoutDir = LocalLayoutDirection.current
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !subScreenOpen,
            modifier = Modifier
                .padding(
                    start = padding.calculateStartPadding(layoutDir),
                    end = padding.calculateEndPadding(layoutDir),
                    top = padding.calculateTopPadding(),
                    bottom = maxOf(padding.calculateBottomPadding(), imeBottom)
                )
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                    alpha = contentAlpha
                }
        ) { p ->
            if (p == PAGE_SHOP) {
                GhajarShopScreen(active = pagerState.settledPage == PAGE_SHOP)
            } else if (p == PAGE_HOME) {
                val connKey = when {
                    exportConfigs != null -> "export"
                    showManual -> "manual"
                    showScanner -> "scanqr"
                    showWindscribe -> "windscribe"
                    showTorNodes -> "tornodes"
                    showProjects -> "projects"
                    showOpenVpnHub -> "openvpnhub"
                    showPsiphonHub -> "psiphonhub"
                    showPicker -> "picker"
                    else -> "connection"
                }
                AnimatedContent(
                    targetState = connKey,
                    transitionSpec = {
                        (scaleIn(tween(220), initialScale = 0.92f) + fadeIn(tween(220))) togetherWith
                                (scaleOut(tween(180), targetScale = 0.92f) + fadeOut(tween(180)))
                    },
                    label = "connTab"
                ) { key ->
                    when (key) {
                        "export" -> ExportConfigScreen(
                            configs = exportConfigs ?: emptyList(),
                            onCancel = { exportConfigs = null }
                        )
                        "manual" -> ManualConfigScreen(
                            existing = editingConfig,
                            onSave = { cfg ->
                                if (editingConfig != null) store.update(cfg) else store.add(cfg)
                                showManual = false; editingConfig = null
                            },
                            onCancel = { showManual = false; editingConfig = null }
                        )
                        "picker" -> ConfigPickerScreen(
                            store = store,
                            selectedId = selectedId,
                            sortMode = sortMode,
                            pings = pings,
                            onSelect = { id ->
                                store.setSelectedId(id)
                                showPicker = false
                                val st = VpnState.state.value
                                if ((st == Connection.CONNECTED || st == Connection.CONNECTING) && id != VpnState.activeId.value) {
                                    store.configs.value.find { c -> c.id == id }?.let(onSwitch)
                                }
                            },
                            onEdit = { editingConfig = it; showManual = true },
                            onAddManually = { showManual = true },
                            onFreeProjects = { showProjects = true },
                            onWindscribe = { showWindscribe = true },
                            onScanQr = { showScanner = true },
                            onShareFile = { exportConfigs = it },
                            onOpenVpnHub = { showOpenVpnHub = true },
                            onPsiphonHub = { showPsiphonHub = true },
                            onTor = { showPicker = false; showTorNodes = true },
                            // SSH and the DNS laboratory are screens on the
                            // settings tab. Adding a server is the moment
                            // somebody wants them, so the entry points are in
                            // the add panel and the navigation crosses tabs
                            // rather than duplicating either screen.
                            onSsh = {
                                showPicker = false
                                sshDetail = true
                                scope.launch { pagerState.animateScrollToPage(PAGE_SETTINGS) }
                            },
                            onDnsLab = {
                                showPicker = false
                                dnsLabDetail = true
                                scope.launch { pagerState.animateScrollToPage(PAGE_SETTINGS) }
                            },
                            onConnectOpenVpn = onConnectOpenVpn,
                            onDisconnectOpenVpn = onDisconnectOpenVpn,
                            onTestOpenVpn = onTestOpenVpn,
                            onConnect = onConnect,
                            onDisconnect = onDisconnect,
                            // A subscription delivered by the shop or the bot
                            // can be renewed; the shop already listens for the
                            // request, the servers list just never made one.
                            onRenewService = { username ->
                                GhajarRenewRequest.request(username)
                                showPicker = false
                                scope.launch { pagerState.animateScrollToPage(PAGE_SHOP) }
                            }
                        )
                        "openvpnhub" -> OpenVpnHubScreen(
                            onConnect = onConnectOpenVpn,
                            onDisconnect = onDisconnectOpenVpn,
                            onTest = onTestOpenVpn
                        )
                        "psiphonhub" -> PsiphonHubScreen(
                            store = store,
                            onConnect = onConnect,
                            onDisconnect = onDisconnect
                        )
                        "projects" -> FreeProjectsScreen(
                            store = store,
                            onOpenTor = { showTorNodes = true },
                            onAddManually = { showProjects = false; showManual = true }
                        )
                        "windscribe" -> WindscribeScreen(store = store)
                        "scanqr" -> QrScannerScreen(
                            onResult = { text ->
                                showScanner = false
                                ImportBus.offerScan(text)
                            }
                        )
                        "tornodes" -> TorNodesScreen(store = store)
                        else -> ConnectionScreen(
                            store = store,
                            selectedId = selectedId,
                            onOpenPicker = { showPicker = true },
                            onConnect = onConnect,
                            onDisconnect = onDisconnect,
                            onCancelPick = onCancelPick
                        )
                    }
                }
            } else {
                val setKey = when {
                    sshDetail -> "ssh"
                    debugDetail -> "debugger"
                    usageDetail -> "usage"
                    perAppDetail -> "perapp"
                    logsDetail -> "logs"
                    stabilityDetail -> "stability"
                    aboutDetail -> "about"
                    themeDetail -> "theme"
                    cleanIpDetail -> "cleanip"
                    dnsLabDetail -> "dnslab"
                    mapDetail -> "map"
                    checkHostDetail -> "checkhost"
                    netCatIndex >= 0 -> "netcatone"
                    netCatDetail -> "netcat"
                    netMonDetail -> "netmon"
                    toolsDetail -> "tools"
                    connDetail -> "connection_settings"
                    notifDetail -> "notifications"
                    prefsDetail -> "preferences"
                    else -> "settings"
                }
                AnimatedContent(
                    targetState = setKey,
                    transitionSpec = {
                        if (settingsDepth(targetState) > settingsDepth(initialState)) {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(250)) togetherWith
                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(250))
                        } else {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250)) togetherWith
                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250))
                        }
                    },
                    label = "setTab"
                ) { key ->
                    when (key) {
                        "ssh" -> SshScreen(
                            store = SshStore.get(LocalContext.current),
                            onSubScreenChange = { sshSubScreen = it }
                        )
                        "debugger" -> ConfigDebuggerScreen(
                            store = store,
                            onSwitch = onSwitch,
                            active = pagerState.settledPage == PAGE_SETTINGS && !pagerState.isScrollInProgress
                        )
                        "usage" -> DataUsageScreen()
                        "perapp" -> AppProxyScreen(store = store)
                        "logs" -> LogsScreen(store = store)
                        "stability" -> StabilityTestScreen(store = store)
                        "about" -> AboutScreen()
                        "theme" -> ThemeSettingsScreen(store = store)
                        "cleanip" -> CleanIpScreen()
                        "netmon" -> NetMonitorScreen(onOpenCategories = { netCatDetail = true })
                        "netcat" -> NetCategoriesScreen(onOpen = { netCatIndex = it })
                        "checkhost" -> CheckHostScreen()
                        "netcatone" -> NetCategoryScreen(index = netCatIndex)
                        "dnslab" -> DnsLabScreen(store = store)
                        "map" -> GhajarMapScreen()
                        "tools" -> ToolsScreen(
                            store = store,
                            onOpenCheckHost = { checkHostDetail = true },
                            onOpenStability = { stabilityDetail = true },
                            onOpenCleanIp = { cleanIpDetail = true },
                            onOpenDnsLab = { dnsLabDetail = true },
                            onOpenMap = { mapDetail = true },
                            onSwitch = onSwitch
                        )
                        "connection_settings" -> ConnectionSettingsScreen(
                            store = store,
                            onOpenPerApp = { perAppDetail = true },
                            onOpenLogs = { logsDetail = true }
                        )
                        "preferences" -> PreferencesScreen(
                            store = store,
                            onOpenTheme = { themeDetail = true },
                            onOpenNotifications = { notifDetail = true }
                        )
                        "notifications" -> NotificationSettingsScreen()
                        else -> SettingsScreen(
                            store = store,
                            scrollState = settingsScroll,
                            onOpenSsh = { sshDetail = true },
                            onOpenDebugger = { debugDetail = true },
                            onOpenUsage = { usageDetail = true },
                            onOpenTools = { toolsDetail = true },
                            onOpenConnection = { connDetail = true },
                            onOpenPreferences = { prefsDetail = true },
                            onOpenAbout = { aboutDetail = true },
                            onOpenNetMon = { netMonDetail = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecureWhile(active: Boolean, key: String) {
    DisposableEffect(active, key) {
        if (active) SecureScreen.acquire(key)
        onDispose { SecureScreen.release(key) }
    }
}

@Composable
private fun ConnectionScreen(
    store: ConfigStore,
    selectedId: String?,
    onOpenPicker: () -> Unit,
    onConnect: (ProxyConfig) -> Unit,
    onDisconnect: () -> Unit,
    onCancelPick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val n: (String) -> String = { localizeDigits(it, lang) }
    val context = LocalContext.current
    val configs by store.configs.collectAsState()
    val subscriptions by store.subscriptions.collectAsState()
    val conn by VpnState.state.collectAsState()
    val activeCfgId by VpnState.activeId.collectAsState()
    val picking by VpnState.picking.collectAsState()

    val mixedPortValue by store.mixedPort.collectAsState()
    LaunchedEffect(mixedPortValue) { MixedPort.value = mixedPortValue }

    LaunchedEffect(activeCfgId, configs) {
        UsageStore.currentConfigKey = configs.find { it.id == activeCfgId }?.name
    }

    LaunchedEffect(conn) {
        val off = conn != Connection.CONNECTED && conn != Connection.CONNECTING
        if (android.net.TrafficStats.getTotalRxBytes() == android.net.TrafficStats.UNSUPPORTED.toLong())
            return@LaunchedEffect
        UsageStore.syncDirect(
            android.net.TrafficStats.getTotalRxBytes(),
            android.net.TrafficStats.getTotalTxBytes(),
            off
        )
        if (!off) return@LaunchedEffect
        while (isActive) {
            delay(5000)
            UsageStore.syncDirect(
                android.net.TrafficStats.getTotalRxBytes(),
                android.net.TrafficStats.getTotalTxBytes(),
                true
            )
        }
    }
    val error by VpnState.error.collectAsState()
    val scope = rememberCoroutineScope()

    var totalUp by remember { mutableStateOf(0L) }
    var totalDown by remember { mutableStateOf(0L) }
    var upSpeed by remember { mutableStateOf(0L) }
    var downSpeed by remember { mutableStateOf(0L) }
    var delayResult by remember { mutableStateOf<String?>(null) }
    var delayRunning by remember { mutableStateOf(false) }
    var showDoctor by remember { mutableStateOf(false) }

    // OpenVPN owns the tunnel whenever the active id carries its prefix. Its
    // engine is a separate process that never broadcasts to VpnBridge, so a
    // session showed live traffic in its own notification and a flat zero here.
    val ovpnActiveUuid by GhajarOpenVpnBridge.activeUuid.collectAsState()
    val ovpnCounters by GhajarOpenVpnBridge.counters.collectAsState()
    val onOpenVpn = activeCfgId.orEmpty().startsWith("ovpn:")
    val ovpnProfile = remember(ovpnActiveUuid, conn) {
        ovpnActiveUuid?.let { uuid ->
            runCatching { GhajarOpenVpnBridge.profiles(context).find { it.uuid == uuid } }.getOrNull()
        }
    }

    LaunchedEffect(Unit) {
        VpnBridge.counters.collect { c ->
            totalUp = c.totalUp; totalDown = c.totalDown
            upSpeed = c.upSpeed; downSpeed = c.downSpeed
        }
    }
    LaunchedEffect(onOpenVpn, ovpnCounters) {
        if (!onOpenVpn) return@LaunchedEffect
        totalUp = ovpnCounters.totalUp; totalDown = ovpnCounters.totalDown
        upSpeed = ovpnCounters.upSpeed; downSpeed = ovpnCounters.downSpeed
    }
    LaunchedEffect(conn) {
        if (conn != Connection.CONNECTED) delayResult = null
    }

    val selectedConfig = configs.find { it.id == selectedId }
    val connected = conn == Connection.CONNECTED || conn == Connection.CONNECTING

    val connectedAt by VpnState.connectedAt.collectAsState()
    val activeConfig = configs.find { it.id == activeCfgId } ?: selectedConfig
    val netOffline = rememberInternetOffline()
    val alive by TunnelHealth.alive.collectAsState()
    val deadTunnel = conn == Connection.CONNECTED && alive == false
    // A tap does something when a tunnel is up (disconnect) or when a server is
    // selected (connect); cancelling an auto-pick is handled on its own.
    val canAct = conn != Connection.DISCONNECTING && (connected || selectedConfig != null)
    val c = ghajarColors

    // One column centred on the connect control. It scrolls only when it must
    // - a short screen, or a large system font - so the orb stays centred
    // everywhere else and nothing is ever clipped.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val floor = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = floor)
                .padding(horizontal = GhajarSpacing.lg, vertical = GhajarSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConnectOrb(
                state = conn,
                picking = picking,
                enabled = canAct,
                tunnelDead = deadTunnel,
                netOffline = netOffline,
                onClick = {
                    when {
                        picking -> onCancelPick()
                        connected -> onDisconnect()
                        else -> selectedConfig?.let { onConnect(it) }
                    }
                },
                // Long press on a live tunnel redials the same server. The
                // OpenVPN path has no ProxyConfig to hand back, so it drops the
                // tunnel and the engine's own reconnect takes it from there.
                onReconnect = {
                    when {
                        onOpenVpn -> onDisconnect()
                        else -> activeConfig?.let { onConnect(it) } ?: onDisconnect()
                    }
                }
            )

            SessionLine(connectedAt.takeIf { it > 0L }, conn)

            // A gesture nobody is told about does not exist.
            if (conn == Connection.CONNECTED) {
                Text(
                    t("orb_hold_reconnect"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted,
                    textAlign = TextAlign.Center
                )
            }

            // The route: one slab, one row, one tap to the picker. Locked
            // configs never reveal their endpoint and the built-in engines have
            // none, exactly as before.
            Slab(spacing = 0.dp) {
                // While OpenVPN owns the tunnel, the route is that profile -
                // not whichever Xray config happens to still be selected. The
                // active id is "ovpn:<uuid>", which is never in `configs`, so
                // this row used to fall back to the selection and name a server
                // that was not carrying a single byte.
                val routeSubtitle = when {
                    onOpenVpn -> ovpnProfile?.let { p ->
                        "OPENVPN · ⁦${p.host}:${p.port}⁩"
                    } ?: "OPENVPN"
                    else -> selectedConfig?.let { cfg ->
                        val engine = cfg.protocol.uppercase(java.util.Locale.ROOT)
                        val endpoint = when {
                            cfg.locked -> t("locked_endpoint")
                            cfg.protocol in setOf("aether", "tor") -> t("builtin_engine")
                            else -> "⁦${cfg.address}:${cfg.port}⁩"
                        }
                        "$engine · $endpoint"
                    }
                    // Nothing selected means nothing to say. The row's title
                    // already reads "no server chosen"; a paragraph explaining
                    // where OpenVPN lives was a manual printed on the dashboard.
                    // SlabRow skips a blank subtitle, so the row collapses to
                    // one line instead of holding space for it.
                }
                SlabRow(
                    title = when {
                        onOpenVpn -> ovpnProfile?.name?.let(BrandConfig::sanitizePublicText)
                            ?: "OpenVPN"
                        else -> selectedConfig?.name?.let(BrandConfig::sanitizePublicText)
                            ?: t("hub_no_server")
                    },
                    subtitle = routeSubtitle,
                    icon = if (onOpenVpn) Icons.Filled.Security else Icons.Filled.Shield,
                    accent = if (conn == Connection.CONNECTED) c.successGlow else c.primary,
                    chevron = true,
                    onClick = onOpenPicker
                )
            }

            // What is left of the service this server came from.
            //
            // Every config that arrived from a subscription carries its subId,
            // and the subscription carries the quota and the expiry the panel
            // reported. Until now that pair was only readable by opening the
            // picker and finding the right header - so the number people check
            // most often was two screens from the connect button.
            //
            // Keyed on the server actually carrying traffic, falling back to
            // the selected one, because "how much is left" is a question about
            // the service in use. It draws nothing at all when the config is a
            // hand-pasted one (no subId) or when the panel reported neither a
            // quota nor an expiry: a card with two dashes on it is a decoration.
            val routeSub = remember(activeConfig, subscriptions) {
                activeConfig?.subId?.takeIf { it.isNotBlank() }
                    ?.let { id -> subscriptions.firstOrNull { it.id == id } }
            }
            if (routeSub != null && (routeSub.total > 0 || routeSub.expire > 0)) {
                SubscriptionQuotaCard(routeSub)
            }

            // Throughput: one object, two readings, totals underneath.
            val downParts = formatBytesParts(downSpeed, lang)
            val upParts = formatBytesParts(upSpeed, lang)
            StatStrip(
                listOf(
                    StatCell(
                        label = t("download"),
                        value = "‪${downParts.first}‬ ${downParts.second}${t("unit_per_sec")}",
                        accent = c.info,
                        sub = t("home_total").format(formatBytes(totalDown, lang))
                    ),
                    StatCell(
                        label = t("upload"),
                        value = "‪${upParts.first}‬ ${upParts.second}${t("unit_per_sec")}",
                        accent = c.premium,
                        sub = t("home_total").format(formatBytes(totalUp, lang))
                    )
                )
            )

            // Measured facts. The latency row doubles as the real-delay test:
            // tapping it replaces the passive handshake reading with a measured
            // one, so there is a single row about latency, not two.
            ConnectionFacts(
                state = conn,
                serverAddress = if (onOpenVpn) ovpnProfile?.host else activeConfig?.address,
                serverPort = if (onOpenVpn) ovpnProfile?.port else activeConfig?.port,
                // ics-openvpn routes the whole device, so a plain request is
                // already inside the tunnel. Asking through 127.0.0.1:MixedPort
                // would reach an inbound only the Xray engine publishes, which
                // is why the IP and location rows sat on a dash for an OpenVPN
                // session that was carrying traffic perfectly well.
                throughLocalProxy = !onOpenVpn,
                measuredDelay = delayResult,
                delayRunning = delayRunning,
                onMeasureDelay = {
                    delayRunning = true
                    delayResult = null
                    scope.launch {
                        // SpeedTest.delay() measures through gozarcore, which
                        // has no part in an OpenVPN session; that path gets a
                        // real TCP handshake against the profile's endpoint.
                        val ms: Int? = if (onOpenVpn) {
                            ovpnProfile?.let { p ->
                                (Pinger.ping(p.host, p.port) as? PingResult.Ok)?.ms
                            }
                        } else SpeedTest.delay()
                        delayResult =
                            if (ms != null) "${n("$ms")} ${t("unit_ms")}" else t("delay_failed")
                        delayRunning = false
                    }
                }
            )

            // A failure the user can act on. The engine's message is whatever it
            // happened to produce; the diagnose button is how that becomes a
            // cause and a remedy instead of a sentence to screenshot.
            val faulted = error?.takeIf { it.isNotBlank() && conn != Connection.CONNECTED }
            if (faulted != null) {
                SkinError(
                    faulted,
                    retryText = t("doc_action"),
                    onRetry = { showDoctor = true }
                )
            } else if (deadTunnel) {
                // Connected, carrying nothing, and no error string exists to
                // explain it - the case the diagnosis is most useful for.
                GhostPill(t("doc_action"), onClick = { showDoctor = true })
            }
        }
    }

    if (showDoctor) {
        ConnectDoctorDialog(
            config = activeConfig,
            ovpnProfile = if (onOpenVpn) ovpnProfile else null,
            engineError = error,
            tunnelUp = conn == Connection.CONNECTED,
            onDismiss = { showDoctor = false }
        )
    }
}

/**
 * What is left of the service the current server belongs to: its name, the
 * data remaining and the days remaining, on the home screen.
 *
 * Every number here comes from the panel's own reply to the subscription
 * fetch - `total`, `used` and `expire` on the [Subscription] - and nothing is
 * derived beyond the subtraction. A field the panel did not send reads
 * "unlimited" rather than a made-up figure, because an unmetered service and
 * one whose quota failed to parse must not look the same.
 *
 * The caller only draws this when at least one of the two is real, so there
 * is no empty state to design.
 */
@Composable
private fun SubscriptionQuotaCard(sub: Subscription) {
    val t = stringsFn()
    val lang = LocalLang.current
    val c = ghajarColors

    val remaining = if (sub.total > 0) (sub.total - sub.used).coerceAtLeast(0L) else 0L
    val daysLeft = if (sub.expire > 0) {
        (sub.expire * 1000L - System.currentTimeMillis()) / 86_400_000L
    } else null

    // The accent is the worse of the two readings, so one glance at the card's
    // top edge says whether anything is about to run out.
    val volumeLevel = if (sub.total > 0) remaining.toFloat() / sub.total else 1f
    val timeLevel = when {
        daysLeft == null -> 1f
        daysLeft <= 1L -> 0f
        daysLeft <= 3L -> 0.2f
        daysLeft <= 7L -> 0.5f
        else -> 1f
    }
    val worst = minOf(volumeLevel, timeLevel)
    val accent = when {
        worst <= 0.10f -> c.error
        worst <= 0.30f -> c.warning
        else -> c.premium
    }

    Slab(accent = accent, spacing = GhajarSpacing.md) {
        SlabRow(
            title = GhajarUiRules.brandedSubscriptionTitle(
                sub.total,
                BrandConfig.sanitizePublicText(sub.name)
            ),
            subtitle = t("home_sub_card"),
            icon = Icons.Filled.DataUsage,
            accent = accent
        )
        StatStrip(
            listOf(
                StatCell(
                    label = t("home_sub_data_left"),
                    value = if (sub.total > 0) formatBytes(remaining, lang)
                    else t("home_sub_unlimited"),
                    accent = if (sub.total > 0) usageLevelColor(remaining, sub.total) else c.premium,
                    sub = if (sub.total > 0) t("home_total").format(formatBytes(sub.total, lang))
                    else null
                ),
                StatCell(
                    label = t("home_sub_time_left"),
                    value = when {
                        daysLeft == null -> t("home_sub_unlimited")
                        daysLeft < 0L -> t("home_sub_expired")
                        else -> t("home_sub_days").format(localizeDigits("$daysLeft", lang))
                    },
                    accent = when {
                        daysLeft == null -> c.premium
                        daysLeft < 0L -> c.error
                        daysLeft <= 3L -> c.error
                        daysLeft <= 7L -> c.warning
                        else -> c.info
                    }
                )
            )
        )
        if (sub.total > 0) {
            UsageBar(used = sub.used, total = sub.total)
        } else {
            Text(
                t("home_sub_unmetered"),
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigPickerScreen(
    store: ConfigStore,
    selectedId: String?,
    sortMode: String,
    pings: SnapshotStateMap<String, PingResult>,
    onSelect: (String) -> Unit,
    onEdit: (ProxyConfig) -> Unit,
    onAddManually: () -> Unit,
    onFreeProjects: () -> Unit,
    onWindscribe: () -> Unit,
    onScanQr: () -> Unit,
    onShareFile: (List<ProxyConfig>) -> Unit,
    onOpenVpnHub: () -> Unit = {},
    onPsiphonHub: () -> Unit = {},
    onTor: () -> Unit = {},
    onSsh: () -> Unit = {},
    onDnsLab: () -> Unit = {},
    onConnectOpenVpn: (String) -> Unit = {},
    onDisconnectOpenVpn: () -> Unit = {},
    onTestOpenVpn: (String) -> Unit = {},
    onConnect: (ProxyConfig) -> Unit = {},
    onDisconnect: () -> Unit = {},
    /** Opens the shop on this panel service's renewal. */
    onRenewService: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val n: (String) -> String = { localizeDigits(it, lang) }
    val configs by store.configs.collectAsState()
    val subscriptions by store.subscriptions.collectAsState()
    val activeId by VpnState.activeId.collectAsState()
    val conn by VpnState.state.collectAsState()
    fun toggleConnection(cfg: ProxyConfig) {
        if (cfg.id == activeId && conn != Connection.DISCONNECTED) onDisconnect() else onConnect(cfg)
    }
    val clipboard = LocalClipboardManager.current
    val pickerContext = LocalContext.current
    val pickerScope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickerScope.launch {
                val bytes: ByteArray? = withContext(Dispatchers.IO) {
                    runCatching {
                        pickerContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes != null && bytes.isNotEmpty()) ImportBus.offer(bytes)
            }
        }
    }

    var subStatus by remember { mutableStateOf("") }

    // Reading a QR out of a saved image, without the camera.
    //
    // The scanner screen can already do this, but only after it has opened
    // and been granted camera permission - so the way most configs actually
    // arrive, as a screenshot from a chat, required granting access to the
    // camera first and then pointing it at nothing. This path asks for no
    // permission at all.
    val qrImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pickerScope.launch {
                val text = withContext(Dispatchers.IO) {
                    decodeQrFromGallery(pickerContext, uri)
                }
                if (!text.isNullOrBlank()) ImportBus.offerScan(text)
                else subStatus = t("scan_qr_image_none")
            }
        }
    }

    var addBusy by remember { mutableStateOf(false) }
    var addDone by remember { mutableStateOf("") }
    var testAllState by remember { mutableStateOf(0) }
    var updateSubsState by remember { mutableStateOf(0) }
    var addMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var purgeMenu by remember { mutableStateOf(false) }
    var confirmPurgeManual by remember { mutableStateOf(false) }
    var confirmPurgeDupes by remember { mutableStateOf(false) }
    var confirmPurgeAll by remember { mutableStateOf(false) }
    var confirmPurgeDead by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var pingingSubs by remember { mutableStateOf(emptySet<String>()) }
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var pickingFastest by remember { mutableStateOf(false) }
    var protocolFilter by remember { mutableStateOf<String?>(null) }
    var protocolMenu by remember { mutableStateOf(false) }
    val expandedSubs by store.expandedSubs.collectAsState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var selectionMode by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val painting = remember { booleanArrayOf(false) }
    val paintSelect = remember { booleanArrayOf(true) }
    val anchorIdx = remember { intArrayOf(-1) }
    val lastIdx = remember { intArrayOf(-1) }
    val orderedSnapshot = remember { mutableListOf<String>() }
    val base = remember { hashSetOf<String>() }
    var viewportH by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragY by remember { mutableStateOf<Float?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var chainFor by remember { mutableStateOf<ProxyConfig?>(null) }
    var openActionsId by remember { mutableStateOf<String?>(null) }
    // Adding a subscription by its link. The clipboard path already handled a
    // URL, but only if you had first put one on the clipboard and knew the app
    // would treat it as a subscription rather than a config - which is not a
    // thing the panel said anywhere.
    var subDialog by remember { mutableStateOf(false) }
    var subDraftUrl by remember { mutableStateOf("") }

    val allIds = remember(configs) { configs.map { it.id }.toSet() }

    val newestFirst by store.newestFirst.collectAsState()

    // Newest-first is applied on top of the sort rather than as a fourth sort
    // mode, because it answers a different question: the sort is how you want
    // the list read, this is where the one you just added went. Only the
    // stored order can be reversed - reversing "fastest" would put the slowest
    // server under the thumb, which is the opposite of the point.
    fun sortMaybe(list: List<ProxyConfig>): List<ProxyConfig> = when (sortMode) {
        ConfigStore.SORT_FASTEST -> list.sortedBy { pingRank(pings[it.id]) }
        ConfigStore.SORT_ALPHA -> list.sortedBy { it.name.lowercase() }
        else -> if (newestFirst) list.asReversed() else list
    }
    // The single worst thing this screen did per frame.
    //
    // It was `remember(configs, pings.toList()) { configs.joinToString(...) }`,
    // and every part of that was expensive in a different way. `pings.toList()`
    // ran on every composition of the picker - a full copy of the map, which on
    // an imported list is nine hundred entries - purely to serve as a remember
    // key. Building it read every entry, which subscribed *the whole screen* to
    // the whole map, so one ping result arriving invalidated the picker rather
    // than the row it belonged to. Then the body built a comma-joined string of
    // nine hundred "id:rank" pairs, about twenty kilobytes, and threw it away.
    // Test-all fires a few hundred of those results in a couple of seconds, and
    // each one bought a map copy, a 20KB string, two re-sorts and two
    // re-groupings of the entire list. That is the twenty-four-frames feel.
    //
    // The same job is an Int hash computed inside derivedStateOf: the map reads
    // happen in the derived scope, so a ping invalidates that and nothing else,
    // and the screen only recomposes when the ordering key actually changes
    // value - which is the amount of work the sort genuinely needs.
    val pingSortKey by remember(sortMode) {
        derivedStateOf {
            if (sortMode != ConfigStore.SORT_FASTEST) 0
            else configs.fold(7) { acc, cfg -> acc * 31 + pingRank(pings[cfg.id]) }
        }
    }
    val q = query.trim()
    fun matchesFilters(cfg: ProxyConfig): Boolean =
        (!favoritesOnly || cfg.favorite) && (protocolFilter == null || cfg.protocol == protocolFilter)
    // Search used to match the name only, which is the one field a subscription
    // controls and often truncates. Matching the host and the protocol too is
    // what makes "arazmta" or "vless" find anything.
    fun matchesQuery(cfg: ProxyConfig): Boolean = q.isEmpty() ||
        cfg.name.contains(q, true) ||
        cfg.address.contains(q, true) ||
        cfg.protocol.contains(q, true)
    val grouped = remember(configs, subscriptions, sortMode, newestFirst, pingSortKey, q, favoritesOnly, protocolFilter) {
        subscriptions.map { sub ->
            val all = sortMaybe(configs.filter { it.subId == sub.id && matchesFilters(it) })
            sub to when {
                q.isEmpty() || sub.name.contains(q, true) -> all
                else -> all.filter { matchesQuery(it) }
            }
        }.filter { (sub, list) -> q.isEmpty() || list.isNotEmpty() || sub.name.contains(q, true) }
            .sortedByDescending { (sub, _) -> WindscribeBrand.isWindscribe(sub) }
    }
    val loose = remember(configs, sortMode, newestFirst, pingSortKey, q, favoritesOnly, protocolFilter) {
        sortMaybe(configs.filter {
            it.subId.isEmpty() && matchesFilters(it) && matchesQuery(it)
        })
    }
    fun displayedOrder(): List<String> = buildList {
        grouped.forEach { (sub, cfgs) -> if (sub.id in expandedSubs || q.isNotEmpty()) cfgs.forEach { add(it.id) } }
        loose.forEach { add(it.id) }
    }

    fun idAt(y: Float): String? {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            y >= it.offset && y < it.offset + it.size
        } ?: return null
        val key = item.key as? String ?: return null
        return if (key in allIds) key else null
    }
    fun toggle(id: String) {
        if (selected.remove(id) == null) selected[id] = true
        selectionMode = selected.isNotEmpty()
    }
    fun applyRange(currentIdx: Int) {
        if (currentIdx < 0 || anchorIdx[0] < 0) return
        val lo = minOf(anchorIdx[0], currentIdx)
        val hi = maxOf(anchorIdx[0], currentIdx)
        orderedSnapshot.forEachIndexed { i, id ->
            val want = if (i in lo..hi) paintSelect[0] else (id in base)
            val have = selected.containsKey(id)
            if (want && !have) selected[id] = true
            else if (!want && have) selected.remove(id)
        }
    }
    fun beginPaint(id: String) {
        orderedSnapshot.clear(); orderedSnapshot.addAll(displayedOrder())
        base.clear(); base.addAll(selected.keys)
        anchorIdx[0] = orderedSnapshot.indexOf(id)
        lastIdx[0] = anchorIdx[0]
        paintSelect[0] = !(id in base)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        applyRange(anchorIdx[0])
        painting[0] = true
        dragging = true
    }
    fun paintAt(id: String?) {
        if (id == null) return
        val idx = orderedSnapshot.indexOf(id)
        if (idx < 0 || idx == lastIdx[0]) return
        lastIdx[0] = idx
        applyRange(idx)
    }
    fun endPaint() {
        painting[0] = false
        dragging = false
        dragY = null
        anchorIdx[0] = -1
        lastIdx[0] = -1
        selectionMode = selected.isNotEmpty()
    }
    fun clearSel() { selected.clear(); selectionMode = false }

    BackHandler(enabled = selectionMode) { clearSel() }

    LaunchedEffect(dragging) {
        while (dragging) {
            val y = dragY
            if (y != null && viewportH > 0) {
                val delta = when {
                    y < 72f -> -14f
                    y > viewportH - 72f -> 14f
                    else -> 0f
                }
                if (delta != 0f) {
                    listState.scrollBy(delta)
                    paintAt(idAt(y))
                }
            }
            delay(16)
        }
    }

    LaunchedEffect(subStatus) {
        if (subStatus.isNotEmpty()) { delay(3000); subStatus = "" }
    }
    LaunchedEffect(addDone) { if (addDone.isNotEmpty()) { delay(3000); addDone = "" } }
    LaunchedEffect(testAllState) { if (testAllState == 2) { delay(2500); testAllState = 0 } }

    fun doAdd(raw: String) {
        val text = raw.trim()
        when {
            text.isEmpty() -> {}
            (text.startsWith("http://") || text.startsWith("https://")) && !text.contains('\n') -> {
                addBusy = true; addDone = ""
                scope.launch {
                    try {
                        val result = SubscriptionFetcher.fetchFull(text)
                        if (result.configs.isEmpty()) {
                            addDone = t("no_configs")
                        } else {
                            val name = runCatching { URL(text).host }.getOrDefault("Subscription")
                            val info = result.userInfo
                            store.upsertSubscription(
                                Subscription(
                                    name = name, url = text,
                                    used = info?.used ?: 0,
                                    total = info?.total ?: 0,
                                    expire = info?.expire ?: 0,
                                    lastUpdated = System.currentTimeMillis()
                                ),
                                result.configs
                            )
                            addDone = n(t("added_sub").format(result.configs.size))
                        }
                    } catch (e: SubscriptionError) {
                        addDone = when (e.kind) {
                            SubscriptionError.Kind.HTTP ->
                                n(t("sub_err_http").format(e.code))
                            SubscriptionError.Kind.EMPTY -> t("sub_err_empty")
                            SubscriptionError.Kind.CLASH -> t("sub_err_clash")
                            SubscriptionError.Kind.NOT_CONFIG -> t("sub_err_notconfig")
                        }
                    } catch (e: Exception) {
                        addDone = t("fetch_failed")
                    } finally {
                        addBusy = false
                    }
                }
            }
            else -> {
                val parsed = ConfigParser.parseBundle(text)
                if (parsed.isEmpty()) {
                    addDone = t("parse_none")
                } else {
                    parsed.forEach { store.add(it) }
                    addDone = n(t("added_configs").format(parsed.size))
                }
            }
        }
    }

    val scanned by ImportBus.scanned.collectAsState()
    LaunchedEffect(scanned) {
        scanned?.let { text ->
            ImportBus.clearScan()
            if (ConfigParser.parseBundle(text).isEmpty() &&
                !text.startsWith("http://") && !text.startsWith("https://")
            ) {
                addDone = t("qr_invalid")
            } else {
                doAdd(text)
            }
        }
    }

    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // While the panel is open it IS the screen.
        //
        // The previous attempt at this wrapped it in weight(1f, fill = false)
        // and called that "bounded by what is left". It is not: weight hands
        // out a share of the column, and the config list below keeps its own
        // share whether or not it has anything in it. So the panel got a few
        // hundred pixels with its own scrollbar - two rows visible - while the
        // bottom half of the screen sat empty. Scrolling was there; the space
        // was not.
        //
        // Giving it weight(1f) and hiding the strip, the toolbar and the list
        // while it is open is the fix. Those three are behind it and cannot be
        // used anyway, so nothing is lost by not drawing them, and the panel
        // gets the entire column instead of a slice of it.
        Box(
            if (addMenu) Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
            else Modifier
        ) {
        AddServerPanel(
            expanded = addMenu,
            busy = addBusy,
            onToggle = { addMenu = !addMenu },
            onPaste = {
                addMenu = false
                val clip = clipboard.getText()?.text
                if (clip.isNullOrBlank()) subStatus = t("clipboard_empty")
                else if (!addBusy) doAdd(clip)
            },
            onManual = { addMenu = false; onAddManually() },
            onImport = { addMenu = false; filePicker.launch(arrayOf("*/*")) },
            onProjects = { addMenu = false; onFreeProjects() },
            onWindscribe = { addMenu = false; onWindscribe() },
            onScanQr = { addMenu = false; onScanQr() },
            onQrFromImage = { addMenu = false; qrImagePicker.launch("image/*") },
            onOpenVpn = { addMenu = false; onOpenVpnHub() },
            onPsiphon = { addMenu = false; onPsiphonHub() },
            onTor = { addMenu = false; onTor() },
            onSsh = { addMenu = false; onSsh() },
            onDnsLab = { addMenu = false; onDnsLab() },
            onSubscription = { addMenu = false; subDialog = true }
        )
        }

        val favouriteCount = remember(configs) { configs.count { it.favorite } }
        // The toolbar collapses to nothing while the add panel is open, so the
        // panel above can have the whole column. It is behind the panel and
        // unreachable anyway, and its height is part of what was starving it.
        //
        // The three-reading strip that used to sit here (config count, how many
        // answered, best time) is gone at the owner's request. Nothing else
        // read it, and the numbers it showed are all still on the rows.
        //
        // The toolbar, rebuilt around what fits.
        //
        // It was three rows of mixed-width outlined buttons: a labelled button
        // sharing its row with four 42dp glyphs, then one labelled button on a
        // row of its own, then two more. At Persian text width that put
        // "اتصال به سریع‌ترین" through an ellipsis and left a half-empty row
        // above it. Labels and glyphs are separated now - two labelled jobs on
        // one row, the connect action full width beneath them, and the five
        // list tools as one evenly spread glyph rail - so nothing is truncated
        // and every glyph is the same size.
        AnimatedVisibility(visible = !addMenu) {
        // Thinner than it was, at the owner's request: the slab's own padding
        // and the gaps between its three rows were most of its height, not the
        // controls. Every touch target here is still at or above 38dp.
        Slab(padding = GhajarSpacing.md, spacing = GhajarSpacing.xs) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
            BounceOutlinedButton(
                onClick = {
                    val snapshot = configs
                    if (testAllState != 1 && snapshot.isNotEmpty()) {
                        snapshot.forEach { pings[it.id] = PingResult.Testing }
                        testAllState = 1
                        scope.launch {
                            val sem = Semaphore(4)
                            val jobs = snapshot.map { cfg ->
                                launch {
                                    sem.withPermit {
                                        pings[cfg.id] = if (cfg.protocol.trim().lowercase() == "ikev2") {
                                            Pinger.pingIke(cfg.address)
                                        } else {
                                            val ms = withContext(Dispatchers.IO) {
                                                Gozarcore.measureDelay(ConfigBuilder.buildForTest(cfg))
                                            }
                                            if (ms >= 0) PingResult.Ok(ms.toInt())
                                            else PingResult.Failed
                                        }
                                    }
                                }
                            }
                            jobs.joinAll()
                            testAllState = 2
                            // Testing every server and then leaving the list in
                            // its old order made you re-read all of it to find
                            // the winner. The results are in; order by them.
                            if (snapshot.any { pings[it.id] is PingResult.Ok }) {
                                store.setSortMode(ConfigStore.SORT_FASTEST)
                            }
                        }
                    }
                },
                minHeight = 38.dp,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Icon(painterResource(R.drawable.signal), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when (testAllState) {
                        1 -> t("testing")
                        2 -> t("test_completed")
                        else -> t("test_all")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            BounceOutlinedButton(
                onClick = {
                    if (updateSubsState != 1) {
                        updateSubsState = 1
                        scope.launch {
                            // The same refresher the app-entry path uses, forced
                            // and with no floor: a button press means refresh
                            // now. It handles the free-configs source and a dead
                            // URL per subscription, which the copy that used to
                            // live here did not.
                            val before = store.subscriptions.value
                                .associate { it.id to it.lastUpdated }
                            SubscriptionRefresher.refreshStale(store, force = true)
                            updateSubsState = 0
                            val after = store.subscriptions.value
                            val refreshable = after.count { SubscriptionRefresher.refreshable(it) }
                            val updated = after.count { (before[it.id] ?: 0L) < it.lastUpdated }
                            when {
                                refreshable == 0 -> subStatus = "ساب اینترنتی برای بروزرسانی وجود ندارد"
                                updated >= refreshable -> addDone = n("همهٔ ساب‌ها بروزرسانی شد ($updated)")
                                updated > 0 -> addDone = n("$updated از $refreshable ساب بروزرسانی شد")
                                else -> subStatus = "${t("fetch_failed")}: هیچ سابی بروزرسانی نشد"
                            }
                        }
                    }
                },
                minHeight = 38.dp,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                if (updateSubsState == 1) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    when (updateSubsState) {
                        1 -> t("fetching_sub")
                        else -> "آپدیت ساب‌ها"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Connect to the fastest server without first reading the list.
        // AutoSelector already measures and ranks every candidate for the
        // auto-connect path; this is the same call, on demand. Full width
        // because its label is the longest one here and it is the row's point.
        PillButton(
            text = if (pickingFastest) t("finding_fastest") else t("picker_connect_fastest"),
            icon = Icons.Filled.Bolt,
            minHeight = 44.dp,
            enabled = configs.isNotEmpty() && !pickingFastest,
            onClick = {
                if (!pickingFastest) {
                    pickingFastest = true
                    scope.launch {
                        val best = runCatching {
                            AutoSelector(context, store).pickFastest()
                        }.getOrNull()
                        pickingFastest = false
                        if (best != null) onConnect(best)
                        else subStatus = t("picker_no_fastest")
                    }
                }
            }
        )

        // The five list tools, all the same size, evenly spread. Each one is a
        // toggle or a menu, none of them needs a word, and putting them on a
        // rail of their own is what stopped them squeezing the labels above.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PickerTool(
                icon = if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                label = t("search_servers"),
                active = searchOpen,
                onClick = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                }
            )
            PickerTool(
                icon = if (favoritesOnly) Icons.Filled.Star else Icons.Filled.StarBorder,
                label = t("picker_favourites") +
                    if (favouriteCount > 0) " (" + n("$favouriteCount") + ")" else "",
                active = favoritesOnly,
                enabled = favouriteCount > 0 || favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly }
            )
            // Newest at the top, under the thumb. A toggle rather than a sort
            // option because it is a layout preference that survives whichever
            // sort you are reading the list in, and it is here rather than in
            // Settings because the moment you want it is the moment you have
            // just pasted a config in and cannot find it.
            PickerTool(
                icon = Icons.Filled.ArrowUpward,
                label = t("newest_first"),
                active = newestFirst,
                onClick = { store.setNewestFirst(!newestFirst) }
            )
            Box {
                PickerTool(
                    icon = Icons.Filled.SwapVert,
                    label = t("sort"),
                    onClick = { sortMenu = true }
                )
                DropdownMenu(
                    expanded = sortMenu,
                    onDismissRequest = { sortMenu = false },
                    offset = DpOffset(0.dp, 8.dp),
                    shape = RoundedCornerShape(GhajarRadius.lg),
                    containerColor = ghajarColors.card,
                    border = null
                ) {
                    listOf(
                        ConfigStore.SORT_ALPHA to t("sort_alpha"),
                        ConfigStore.SORT_FASTEST to t("sort_fastest"),
                        ConfigStore.SORT_ADDED to t("sort_added")
                    ).forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = {
                                if (sortMode == mode)
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.height(40.dp),
                            onClick = { store.setSortMode(mode); sortMenu = false }
                        )
                    }
                }
            }
            Box {
                PickerTool(
                    icon = Icons.Filled.DeleteSweep,
                    label = t("delete_all"),
                    destructive = true,
                    onClick = { purgeMenu = true }
                )
                DropdownMenu(
                    expanded = purgeMenu,
                    onDismissRequest = { purgeMenu = false },
                    offset = DpOffset(0.dp, 8.dp),
                    shape = RoundedCornerShape(GhajarRadius.lg),
                    containerColor = ghajarColors.card,
                    border = null
                ) {
                    DropdownMenuItem(
                        text = { Text(t("delete_manual_configs"), style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Filled.EditOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(40.dp),
                        onClick = {
                            purgeMenu = false
                            if (configs.none { it.subId.isBlank() }) addDone = t("no_manual")
                            else confirmPurgeManual = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(t("delete_timed_out"), style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Filled.TimerOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(40.dp),
                        onClick = {
                            purgeMenu = false
                            if (configs.none { pings[it.id] == PingResult.Failed }) {
                                addDone = t("no_timed_out")
                            } else confirmPurgeDead = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(t("delete_duplicates"), style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(40.dp),
                        onClick = {
                            purgeMenu = false
                            if (store.duplicateIds().isEmpty()) addDone = t("no_duplicates")
                            else confirmPurgeDupes = true
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                t("delete_everything"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(40.dp),
                        onClick = { purgeMenu = false; confirmPurgeAll = true }
                    )
                }
            }
        }
        }
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            // The favourites toggle used to be repeated here as well. It lives
            // on the tool rail now, where it is visible without opening search
            // first, so this row is the query and the protocol filter only.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(t("search_servers")) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { protocolMenu = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "فیلتر پروتکل",
                            tint = if (protocolFilter != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = protocolMenu, onDismissRequest = { protocolMenu = false }) {
                        DropdownMenuItem(text = { Text("همهٔ پروتکل‌ها") }, onClick = {
                            protocolFilter = null; protocolMenu = false
                        })
                        configs.map { it.protocol }.distinct().sorted().forEach { proto ->
                            DropdownMenuItem(text = { Text(proto) }, onClick = {
                                protocolFilter = proto; protocolMenu = false
                            })
                        }
                    }
                }
            }
        }

        val statusLine = when {
            addBusy -> t("adding")
            addDone.isNotEmpty() -> addDone
            else -> subStatus
        }
        val badLines = remember(lang) {
            setOf(
                t("fetch_failed"), t("parse_none"), t("no_configs"), t("clipboard_empty"),
                t("qr_invalid"), t("no_timed_out"), t("import_bad_file"),
                t("import_wrong_password"), t("import_foreign_app"), t("ws_fetch_failed")
            )
        }
        val isBad = statusLine.isNotEmpty() &&
                badLines.any { it.isNotEmpty() && statusLine.startsWith(it) }
        val statusAccent =
            if (isBad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

        AnimatedVisibility(
            visible = statusLine.isNotEmpty(),
            enter = fadeIn(tween(220)) + expandVertically(tween(260, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 3 },
            exit = fadeOut(tween(160)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing)) +
                    slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 3 }
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(statusAccent.copy(alpha = 0.10f))
                        .border(1.dp, statusAccent.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (addBusy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = statusAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            if (isBad) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = statusAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        mixedText(statusLine),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusAccent,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        val wsRowColor = windscribeRowColor()

        LazyColumn(
            state = listState,
            // Zero height, not merely hidden: a weighted child claims its
            // share of the column even with nothing in it, and that share is
            // what the add panel above needs.
            modifier = (if (addMenu) Modifier.height(0.dp) else Modifier.weight(1f))
                .onSizeChanged { viewportH = it.height }
                .pointerInput(allIds) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var painted = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            if (painting[0]) {
                                painted = true
                                c.consume()
                                dragY = c.position.y
                                paintAt(idAt(c.position.y))
                            }
                        }
                        if (painting[0] || painted) endPaint()
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The OpenVPN summary tile used to sit here, above the servers, as a
            // second kind of thing in a list of one kind. OpenVPN is a way of
            // adding a server, so it is managed from "افزودن سرور" with the
            // other providers - the same screen, one entry point instead of two.
            grouped.forEach { (sub, subConfigs) ->
                val wsRow = if (WindscribeBrand.isWindscribe(sub)) wsRowColor else null
                item(key = "sub-${sub.id}") {
                    SubscriptionHeader(
                        sub = sub,
                        configCount = subConfigs.size,
                        isOpen = sub.id in expandedSubs || q.isNotEmpty(),
                        onToggle = { store.toggleSubExpanded(sub.id) },
                        onRefresh = {
                            subStatus = t("fetching_sub")
                            scope.launch {
                                if (sub.url == FreeConfigs.SOURCE_URL) {
                                    val kept = FreeConfigs.refresh(store, sub.name)
                                    subStatus = when {
                                        kept > 0 -> t("proj_free_added").format(kept) + if (FreeConfigs.incomplete.value) "؛ بعضی منابع در دسترس نبودند، موارد قبلی حفظ شدند." else ""
                                        kept == FreeConfigs.UNREACHABLE -> t("proj_free_unreachable")
                                        kept == FreeConfigs.NO_CONFIGS -> t("proj_free_nocfg")
                                        kept == FreeConfigs.BUSY -> t("proj_free_working")
                                        else -> t("proj_free_none")
                                    }
                                    return@launch
                                }
                                try {
                                    val result = SubscriptionFetcher.fetchFull(sub.url)
                                    val info = result.userInfo
                                    store.upsertSubscription(
                                        sub.copy(
                                            used = info?.used ?: sub.used,
                                            total = info?.total ?: sub.total,
                                            expire = info?.expire ?: sub.expire,
                                            lastUpdated = System.currentTimeMillis()
                                        ),
                                        result.configs
                                    )
                                    subStatus = n("${sub.name}: ${result.configs.size}")
                                } catch (e: Exception) {
                                    subStatus = "${t("fetch_failed")}: ${e.message ?: ""}"
                                }
                            }
                        },
                        onRename = { newName -> store.renameSubscription(sub.id, newName) },
                        onRemove = { store.deleteSubscription(sub.id) },
                        timedOutCount = subConfigs.count { pings[it.id] == PingResult.Failed },
                        onRemoveTimedOut = {
                            val dead = subConfigs.filter { pings[it.id] == PingResult.Failed }
                                .map { it.id }.toSet()
                            store.deleteConfigsByIds(dead)
                            dead.forEach { pings.remove(it); selected.remove(it) }
                            addDone = n(t("deleted_n").format(dead.size))
                        },
                        onRenew = sub.serviceUsername.takeIf { it.isNotBlank() }
                            ?.let { username -> { onRenewService(username) } },
                        pinging = sub.id in pingingSubs,
                        onPing = {
                            if (sub.id !in pingingSubs && subConfigs.isNotEmpty()) {
                                pingingSubs = pingingSubs + sub.id
                                subConfigs.forEach { pings[it.id] = PingResult.Testing }
                                scope.launch {
                                    val sem = Semaphore(4)
                                    subConfigs.map { cfg ->
                                        launch {
                                            sem.withPermit {
                                                pings[cfg.id] = if (cfg.protocol.trim().lowercase() == "ikev2") {
                                                    Pinger.pingIke(cfg.address)
                                                } else {
                                                    val ms = withContext(Dispatchers.IO) {
                                                        Gozarcore.measureDelay(
                                                            ConfigBuilder.buildForTest(cfg)
                                                        )
                                                    }
                                                    if (ms >= 0) PingResult.Ok(ms.toInt())
                                                    else PingResult.Failed
                                                }
                                            }
                                        }
                                    }.joinAll()
                                    pingingSubs = pingingSubs - sub.id
                                }
                            }
                        },
                        modifier = Modifier.animateItem(fadeInSpec = tween(300), placementSpec = tween(300), fadeOutSpec = tween(200))
                    )
                }
                if (sub.id in expandedSubs || q.isNotEmpty()) {
                    items(subConfigs, key = { it.id }) { cfg ->
                        ConfigRow(
                            config = cfg,
                            isSelected = cfg.id == selectedId,
                            isActive = cfg.id == activeId,
                            ping = pings[cfg.id],
                            selectionMode = selectionMode,
                            isChecked = { selected.containsKey(cfg.id) },
                            onClick = { if (selectionMode) toggle(cfg.id) else onSelect(cfg.id) },
                            onLongPress = { beginPaint(cfg.id) },
                            onEdit = { onEdit(cfg) },
                            onDelete = { store.delete(cfg.id); pings.remove(cfg.id) },
                            onShareFile = { onShareFile(listOf(cfg)) },
                            onChain = { chainFor = cfg },
                            actionsOpen = openActionsId == cfg.id,
                            onToggleActions = {
                                openActionsId = if (openActionsId == cfg.id) null else cfg.id
                            },
                            modifier = Modifier.animateItem(fadeInSpec = tween(300), placementSpec = tween(300), fadeOutSpec = tween(200)),
                            containerColor = wsRow,
                            conn = conn,
                            onToggleConnection = { toggleConnection(cfg) },
                            onToggleFavorite = { store.setFavorite(cfg.id, !cfg.favorite) }
                        )
                    }
                }
            }

            if (loose.isNotEmpty()) {
                item(key = "loose-header") {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)
                            .animateItem(
                                fadeInSpec = tween(300),
                                placementSpec = tween(300),
                                fadeOutSpec = tween(200)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            Modifier.clip(RoundedCornerShape(GhajarRadius.md))
                                .background(ghajarColors.secondaryCard)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                n("${t("manual_configs")} (${loose.size})"),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                items(loose, key = { it.id }) { cfg ->
                    ConfigRow(
                        config = cfg,
                        isSelected = cfg.id == selectedId,
                        isActive = cfg.id == activeId,
                        ping = pings[cfg.id],
                        selectionMode = selectionMode,
                        isChecked = { selected.containsKey(cfg.id) },
                        onClick = { if (selectionMode) toggle(cfg.id) else onSelect(cfg.id) },
                        onLongPress = { beginPaint(cfg.id) },
                        onEdit = { onEdit(cfg) },
                        onDelete = { store.delete(cfg.id); pings.remove(cfg.id) },
                        onShareFile = { onShareFile(listOf(cfg)) },
                        onChain = { chainFor = cfg },
                        actionsOpen = openActionsId == cfg.id,
                        onToggleActions = {
                            openActionsId = if (openActionsId == cfg.id) null else cfg.id
                        },
                        modifier = Modifier.animateItem(fadeInSpec = tween(300), placementSpec = tween(300), fadeOutSpec = tween(200)),
                        conn = conn,
                        onToggleConnection = { toggleConnection(cfg) },
                        onToggleFavorite = { store.setFavorite(cfg.id, !cfg.favorite) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
        ) {
            SelectionActionBar(
                count = selected.size,
                onClose = { clearSel() },
                onCopy = {
                    val text = configs.filter { selected.containsKey(it.id) }
                        .joinToString("\n") { ConfigShare.toLink(it) }
                    clipboard.setText(AnnotatedString(text))
                    android.widget.Toast.makeText(context, t("copied"), android.widget.Toast.LENGTH_SHORT).show()
                },
                onShareApp = {
                    val text = configs.filter { selected.containsKey(it.id) }
                        .joinToString("\n") { ConfigShare.toLink(it) }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, t("share")))
                },
                onShareFile = {
                    onShareFile(configs.filter { selected.containsKey(it.id) })
                    clearSel()
                },
                onDelete = { confirmDelete = true }
            )
        }
    }

    if (subDialog) {
        GlassDialog(
            onDismiss = { subDialog = false },
            title = t("add_sub_row"),
            confirmLabel = t("add"),
            dismissLabel = t("cancel"),
            onConfirm = {
                val url = subDraftUrl.trim()
                subDialog = false
                if (url.isNotEmpty()) {
                    subDraftUrl = ""
                    // The same import path the clipboard uses, so a link
                    // behaves identically however it arrived - including the
                    // per-error messages for an HTTP failure, an empty list
                    // and a Clash file.
                    doAdd(url)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                Text(
                    t("add_sub_row_sub"),
                    style = MaterialTheme.typography.bodySmall,
                    color = ghajarColors.textSecondary
                )
                SkinField(
                    value = subDraftUrl,
                    onValueChange = { subDraftUrl = it },
                    label = t("add_sub_url"),
                    placeholder = "https://",
                    singleLine = true
                )
            }
        }
    }

    chainFor?.let { target ->
        ChainPickerDialog(
            store = store,
            config = configs.find { it.id == target.id } ?: target,
            onDismiss = { chainFor = null }
        )
    }

    if (confirmPurgeManual) {
        val manual = remember(configs) {
            configs.filter { it.subId.isBlank() }.map { it.id }.toSet()
        }
        GlassDialog(
            onDismiss = { confirmPurgeManual = false },
            title = t("delete_manual_configs"),
            confirmLabel = t("delete"),
            dismissLabel = t("cancel"),
            destructive = true,
            onConfirm = {
                store.deleteConfigsByIds(manual)
                manual.forEach { pings.remove(it); selected.remove(it) }
                addDone = n(t("deleted_n").format(manual.size))
                confirmPurgeManual = false
            }
        ) {
            Text(
                n(t("delete_manual_q").format(manual.size)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (confirmPurgeDupes) {
        val dupes = remember(configs) { store.duplicateIds() }
        GlassDialog(
            onDismiss = { confirmPurgeDupes = false },
            title = t("delete_duplicates"),
            confirmLabel = t("delete"),
            dismissLabel = t("cancel"),
            destructive = true,
            onConfirm = {
                store.deleteConfigsByIds(dupes)
                dupes.forEach { pings.remove(it); selected.remove(it) }
                addDone = n(t("deleted_n").format(dupes.size))
                confirmPurgeDupes = false
            }
        ) {
            Text(
                n(t("delete_duplicates_q").format(dupes.size)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (confirmPurgeAll) {
        GlassDialog(
            onDismiss = { confirmPurgeAll = false },
            title = t("delete_everything"),
            confirmLabel = t("delete"),
            destructive = true,
            onConfirm = {
                val removed = configs.size
                store.deleteAllConfigs()
                selected.clear()
                selectionMode = false
                addDone = n(t("deleted_n").format(removed))
                confirmPurgeAll = false
            }
        ) {
            Text(
                n(t("delete_everything_q").format(configs.size)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (confirmPurgeDead) {
        val dead = remember(configs, pings.toMap()) {
            configs.filter { pings[it.id] == PingResult.Failed }.map { it.id }.toSet()
        }
        GlassDialog(
            onDismiss = { confirmPurgeDead = false },
            title = t("delete_timed_out"),
            confirmLabel = t("delete"),
            destructive = true,
            onConfirm = {
                store.deleteConfigsByIds(dead)
                dead.forEach { pings.remove(it); selected.remove(it) }
                addDone = n(t("deleted_n").format(dead.size))
                confirmPurgeDead = false
            }
        ) {
            Text(
                n(t("delete_timeout_q").format(dead.size)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (confirmDelete) {
        GlassDialog(
            onDismiss = { confirmDelete = false },
            title = t("delete"),
            confirmLabel = t("delete"),
            dismissLabel = t("cancel"),
            destructive = true,
            onConfirm = {
                configs.filter { selected.containsKey(it.id) }
                    .forEach { store.delete(it.id); pings.remove(it.id) }
                clearSel()
                confirmDelete = false
            }
        ) {
            Text(t("delete_selected_q"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExportConfigScreen(
    configs: List<ProxyConfig>,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val multi = configs.size > 1

    val defaultName = if (multi) "Ghajarvpn-configs" else (configs.firstOrNull()?.name?.ifBlank { "config" } ?: "config")
    var fileName by remember { mutableStateOf(defaultName) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var lockDetails by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoBox(t("export_encrypted_note"))

        if (multi) {
            InfoBox(localizeDigits(t("export_count").format(configs.size), lang))
        }

        SettingsGroup {
            OutlinedTextField(
                fileName,
                { fileName = it },
                label = { Text(t("export_file_name")) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = monoLatinFont()),
                trailingIcon = {
                    Text(
                        ".grt",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 14.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                password,
                { password = it },
                label = { Text(t("export_password")) },
                placeholder = { Text(t("export_password_hint"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = monoLatinFont()),
                trailingIcon = {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = t("show"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(CircleShape)
                            .clickable { showPassword = !showPassword }
                            .padding(8.dp)
                            .size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            SettingRow(
                title = t("export_lock_details"),
                subtitle = if (lockDetails) t("export_locked_note") else t("export_unlocked_note"),
                checked = lockDetails,
                onCheckedChange = { lockDetails = it },
                icon = Icons.Filled.Lock
            )
        }

        AnimatedVisibility(
            visible = error.isNotEmpty(),
            enter = fadeIn(tween(200)) + expandVertically(tween(240, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(200, easing = FastOutSlowInEasing))
        ) {
            InfoBox(error, accent = MaterialTheme.colorScheme.error, centered = true)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BounceOutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(t("cancel")) }
            BounceButton(
                onClick = {
                    if (busy) return@BounceButton
                    busy = true
                    error = ""
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.Default) {
                                val bytes = ConfigFile.encode(
                                    context, configs, password.ifBlank { null }, lockDetails
                                )
                                ConfigFile.writeToCache(context, fileName, bytes)
                            }
                        }
                        busy = false
                        result.onSuccess { file ->
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = ConfigFile.MIME
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, t("export_continue")))
                            onCancel()
                        }.onFailure {
                            error = t("import_bad_file")
                        }
                    }
                },
                enabled = !busy && fileName.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        Icons.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(t("export_continue"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ManualConfigScreen(
    existing: ProxyConfig? = null,
    onSave: (ProxyConfig) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    var name by remember { mutableStateOf(existing?.name ?: "") }

    if (existing?.locked == true) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("locked_config"), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                t("locked_note"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SkinField(
                value = name,
                onValueChange = { name = it },
                label = t("name_optional")
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                GhostPill(t("cancel"), onCancel, Modifier.weight(1f))
                PillButton(
                    t("save"),
                    { onSave(existing.copy(name = name.ifBlank { existing.name })) },
                    Modifier.weight(1f)
                )
            }
        }
        return
    }

    var protocol by remember { mutableStateOf(existing?.protocol ?: "vless") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var port by remember { mutableStateOf(existing?.port?.takeIf { it > 0 }?.toString() ?: "") }
    var uuid by remember { mutableStateOf(existing?.uuid ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var method by remember { mutableStateOf(existing?.method?.ifEmpty { "aes-256-gcm" } ?: "aes-256-gcm") }
    var flow by remember { mutableStateOf(existing?.flow ?: "") }
    var network by remember { mutableStateOf(existing?.network ?: "tcp") }
    var security by remember { mutableStateOf(existing?.security ?: "none") }
    var sni by remember { mutableStateOf(existing?.sni ?: "") }
    var publicKey by remember { mutableStateOf(existing?.publicKey ?: "") }
    var shortId by remember { mutableStateOf(existing?.shortId ?: "") }
    var path by remember { mutableStateOf(existing?.path ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var serviceName by remember { mutableStateOf(existing?.serviceName ?: "") }
    var mode by remember { mutableStateOf(existing?.mode ?: "") }
    var alpn by remember { mutableStateOf(existing?.alpn ?: "") }
    var fingerprint by remember { mutableStateOf(existing?.fingerprint ?: "chrome") }
    var cipherSuites by remember { mutableStateOf(existing?.cipherSuites ?: "") }
    var randomSubdomain by remember { mutableStateOf(existing?.randomSubdomain ?: false) }
    var allowInsecure by remember { mutableStateOf(existing?.allowInsecure ?: false) }
    var pinnedCert by remember { mutableStateOf(existing?.pinnedCertSha256 ?: "") }
    var pinning by remember { mutableStateOf(false) }
    val pinScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (allowInsecure && !CertPin.isValid(pinnedCert) && address.isNotBlank()) {
            pinning = true
            pinnedCert = CertPin.fetch(
                address.trim(), port.toIntOrNull() ?: 0, sni.trim()
            ).orEmpty()
            pinning = false
        }
    }
    var hyObfsPassword by remember { mutableStateOf(existing?.hyObfsPassword ?: "") }
    var hyUp by remember { mutableStateOf(if ((existing?.hyUpMbps ?: 0) > 0) "${existing?.hyUpMbps}" else "") }
    var hyDown by remember { mutableStateOf(if ((existing?.hyDownMbps ?: 0) > 0) "${existing?.hyDownMbps}" else "") }
    var error by remember { mutableStateOf("") }

    // The form used to be twenty identical outlined fields in one flat column:
    // the name of the server, the cryptography and the transport all looked
    // equally important and equally unrelated. It is now grouped - identity,
    // endpoint, credentials, transport, security - each group one slab under
    // its own rail, and each field the skin's own filled box.
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = GhajarSpacing.lg, vertical = GhajarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        Rail(t("manual_sec_identity"))
        Slab {
            SkinField(
                value = name,
                onValueChange = { name = it },
                label = t("name_optional"),
                placeholder = t("manual_name_hint")
            )
            LabeledDropdown(t("protocol"), listOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2", "wireguard", "ikev2", "socks", "http"), protocol) { protocol = it }
        }

        Rail(t("manual_sec_endpoint"))
        Slab {
            SkinField(
                value = address,
                onValueChange = { address = it },
                label = t("address"),
                placeholder = "example.com"
            )
            if (protocol != "ikev2") SkinField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = t("port"),
                placeholder = "443",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        val needsCredentials = protocol in setOf("vless", "vmess", "ikev2", "trojan", "shadowsocks", "hysteria2")
        if (needsCredentials) {
            Rail(t("manual_sec_credentials"))
            Slab {
                if (protocol == "vless" || protocol == "vmess")
                    SkinField(value = uuid, onValueChange = { uuid = it }, label = t("uuid"))
                if (protocol == "ikev2") {
                    SkinField(value = uuid, onValueChange = { uuid = it }, label = t("ikev2_user"))
                    SkinField(value = password, onValueChange = { password = it }, label = t("password"))
                    SkinField(
                        value = sni,
                        onValueChange = { sni = it },
                        label = t("ikev2_remote_id"),
                        helper = t("ikev2_note")
                    )
                }
                if (protocol == "trojan" || protocol == "shadowsocks" || protocol == "hysteria2")
                    SkinField(value = password, onValueChange = { password = it }, label = t("password"))
                if (protocol == "shadowsocks")
                    LabeledDropdown(t("enc_method"),
                        listOf("aes-256-gcm", "aes-128-gcm", "chacha20-ietf-poly1305", "2022-blake3-aes-256-gcm"), method) { method = it }
                if (protocol == "vless")
                    SkinField(value = flow, onValueChange = { flow = it }, label = t("flow_optional"))
            }
        }

        if (protocol == "hysteria2") {
            Rail(t("manual_sec_tuning"))
            Slab {
                SkinField(value = hyObfsPassword, onValueChange = { hyObfsPassword = it }, label = t("hy_obfs"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)) {
                    SkinField(
                        value = hyUp,
                        onValueChange = { hyUp = it.filter { c -> c.isDigit() } },
                        label = t("hy_up"),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    SkinField(
                        value = hyDown,
                        onValueChange = { hyDown = it.filter { c -> c.isDigit() } },
                        label = t("hy_down"),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
            Rail(t("manual_sec_security"))
            Slab {
                SkinField(value = sni, onValueChange = { sni = it }, label = t("sni"))
                SkinField(value = alpn, onValueChange = { alpn = it }, label = t("alpn"))
            }
        }

        if (protocol !in setOf("shadowsocks", "hysteria2", "wireguard", "ikev2")) {
            Rail(t("manual_sec_transport"))
            Slab {
            LabeledDropdown(t("network"), listOf("tcp", "ws", "grpc", "http", "httpupgrade", "xhttp"), network) { network = it }
            if (network == "ws" || network == "httpupgrade" || network == "http" || network == "xhttp") {
                SkinField(value = path, onValueChange = { path = it }, label = t("ws_path"), placeholder = "/")
                SkinField(value = host, onValueChange = { host = it }, label = t("ws_host"))
            }
            if (network == "xhttp")
                LabeledDropdown(t("mode"), listOf("auto", "packet-up", "stream-up", "stream-one"), mode.ifEmpty { "auto" }) { mode = it }
            if (network == "grpc") {
                SkinField(value = serviceName, onValueChange = { serviceName = it }, label = t("service_name"))
                LabeledDropdown(t("mode"), listOf("gun", "multi"), mode.ifEmpty { "gun" }) { mode = it }
            }
            }

            Rail(t("manual_sec_security"))
            Slab {
            LabeledDropdown(t("security"), listOf("none", "tls", "reality"), security) { security = it }
            if (security == "tls" || security == "reality") {
                SkinField(value = sni, onValueChange = { sni = it }, label = t("sni"))
                LabeledDropdown(t("fingerprint"), listOf("chrome", "firefox", "safari", "ios", "android", "edge", "random"), fingerprint.ifEmpty { "chrome" }) { fingerprint = it }
            }
            if (security == "tls")
                SkinField(value = alpn, onValueChange = { alpn = it }, label = t("alpn"))
            // From PattNG and MahsaNG: the exact cipher list and a varying
            // hostname are both things a network fingerprints a client by.
            // Both are blank/off on every existing config, so nothing changes
            // until they are filled in.
            if (security == "tls")
                SkinField(
                    value = cipherSuites,
                    onValueChange = { cipherSuites = it },
                    label = t("cipher_suites")
                )
            if (security == "tls" || security == "reality") {
                SettingRow(
                    title = t("random_subdomain"),
                    subtitle = t("random_subdomain_sub"),
                    checked = randomSubdomain,
                    onCheckedChange = { randomSubdomain = it },
                    icon = Icons.Filled.Shuffle
                )
            }
            if (security == "reality") {
                SkinField(value = publicKey, onValueChange = { publicKey = it }, label = t("public_key"))
                SkinField(value = shortId, onValueChange = { shortId = it }, label = t("short_id"))
            }
            if (security == "tls") {
                    SettingRow(
                        title = t("allow_insecure"),
                        subtitle = when {
                            pinning -> t("pin_fetching")
                            allowInsecure && CertPin.isValid(pinnedCert) -> t("pin_ready")
                            allowInsecure -> t("pin_failed")
                            else -> t("allow_insecure_sub")
                        },
                        checked = allowInsecure,
                        enabled = !pinning,
                        onCheckedChange = { on ->
                            allowInsecure = on
                            if (!on) {
                                pinnedCert = ""
                            } else {
                                pinning = true
                                pinScope.launch {
                                    pinnedCert = CertPin.fetch(
                                        address.trim(),
                                        port.toIntOrNull() ?: 0,
                                        sni.trim()
                                    ).orEmpty()
                                    pinning = false
                                }
                            }
                        },
                        icon = Icons.Filled.Warning
                    )
                }
            }
        }

        if (error.isNotEmpty()) SkinError(error)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
            GhostPill(t("cancel"), onCancel, Modifier.weight(1f))
            PillButton(
                t("save"),
                {
                    val p = if (protocol == "ikev2") 500 else port.toIntOrNull()
                    when {
                        address.isBlank() -> error = t("err_address")
                        p == null || p !in 1..65535 -> error = t("err_port")
                        (protocol == "vless" || protocol == "vmess") && uuid.isBlank() -> error = t("err_uuid")
                        protocol == "ikev2" && uuid.isBlank() -> error = t("err_uuid")
                        (protocol == "trojan" || protocol == "shadowsocks" || protocol == "hysteria2" ||
                                protocol == "ikev2") && password.isBlank() -> error = t("err_password")
                        else -> onSave(
                            (existing ?: ProxyConfig(name = "", protocol = "", address = "", port = 0)).copy(
                                name = name.ifBlank { "$protocol $address" },
                                protocol = protocol,
                                address = address.trim(),
                                port = p,
                                uuid = uuid.trim(),
                                password = password.trim(),
                                method = method.trim(),
                                encryption = if (protocol == "vmess") "auto" else "none",
                                flow = flow.trim(),
                                network = when (protocol) {
                                    "shadowsocks" -> "tcp"
                                    "hysteria2" -> "hysteria"
                                    "ikev2" -> "ikev2"
                                    else -> network
                                },
                                security = when (protocol) {
                                    "shadowsocks" -> "none"
                                    "hysteria2" -> "tls"
                                    "ikev2" -> "none"
                                    else -> security
                                },
                                sni = sni.trim(),
                                publicKey = publicKey.trim(),
                                shortId = shortId.trim(),
                                path = path.trim(),
                                host = host.trim(),
                                serviceName = serviceName.trim(),
                                mode = mode.trim(),
                                alpn = alpn.trim(),
                                fingerprint = fingerprint.trim(),
                                allowInsecure = allowInsecure,
                                pinnedCertSha256 = pinnedCert,
                                cipherSuites = cipherSuites.trim(),
                                randomSubdomain = randomSubdomain,
                                hyObfs = if (hyObfsPassword.isBlank()) "" else "salamander",
                                hyObfsPassword = hyObfsPassword.trim(),
                                hyUpMbps = hyUp.toIntOrNull() ?: 0,
                                hyDownMbps = hyDown.toIntOrNull() ?: 0
                            )
                        )
                    }
                },
                Modifier.weight(1f),
                icon = Icons.Filled.Save
            )
        }
    }
}

@Composable
private fun AddServerPanel(
    expanded: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onPaste: () -> Unit,
    onManual: () -> Unit,
    onImport: () -> Unit,
    onProjects: () -> Unit,
    onWindscribe: () -> Unit,
    onScanQr: () -> Unit,
    onQrFromImage: () -> Unit = {},
    onOpenVpn: () -> Unit = {},
    onPsiphon: () -> Unit = {},
    onTor: () -> Unit = {},
    onSsh: () -> Unit = {},
    onDnsLab: () -> Unit = {},
    onSubscription: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val rot by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "addRot"
    )

    // Two kinds of thing were stacked as seven identical outlined buttons: four
    // ways to bring in a config you already have, and four providers that fetch
    // one for you. They are now told apart - the four inputs are a grid of
    // tiles, the four providers are rows of one slab - inside a single
    // borderless slab instead of a bordered card full of bordered buttons.
    val c = ghajarColors
    Slab(modifier, spacing = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GhajarRadius.md))
                .clickable(enabled = !busy) { onToggle() }
                .padding(vertical = GhajarSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = t("add_server"),
                    tint = c.primary,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rot }
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    mixedText(t("add_server")),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    t("add_server_sub"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Column(
                Modifier.padding(top = GhajarSpacing.md),
                verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
            ) {
                // The order the app's own users asked for, top to bottom:
                // where to put a config you already have, then the providers
                // that hand you one - Psiphon first among them, because it is
                // the only engine that needs no config at all and the only one
                // nobody finds by looking for somewhere to paste something.
                //
                // "I have a config" is one collapsed row rather than a grid of
                // four tiles, so all five entries are on screen at once. The
                // panel scrolls now, so opening it shows all four tiles.
                var haveOpen by remember { mutableStateOf(false) }
                // Grouped rather than a flat run of rows. The entries fall
                // into three genuinely different kinds of thing, and a list
                // that does not say so makes the user read all of it to find
                // out which kind they wanted: a config you already have, an
                // engine that needs no config at all, and a provider that
                // fetches configs for you.
                //
                // No group here is decorative. Every row below is wired to a
                // handler that exists - a labelled section leading to rows
                // that do nothing would be worse than the flat list it
                // replaced.
                Rail(t("add_group_have"))
                SlabRow(
                    title = t("add_have_config"),
                    subtitle = t("add_have_config_sub"),
                    icon = Icons.Filled.ContentPaste,
                    accent = c.highlight,
                    chevron = true,
                    enabled = !busy,
                    onClick = { haveOpen = !haveOpen }
                )
                AnimatedVisibility(
                    visible = haveOpen,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
                ) {
                    Column(
                        Modifier.padding(top = GhajarSpacing.sm, bottom = GhajarSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                            GlyphTile(Icons.Filled.Add, t("add_manually"), onManual, Modifier.weight(1f), enabled = !busy)
                            GlyphTile(Icons.Filled.ContentPaste, t("paste_clipboard"), onPaste, Modifier.weight(1f), enabled = !busy)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                            GlyphTile(Icons.Filled.UploadFile, t("import_from_file"), onImport, Modifier.weight(1f), enabled = !busy)
                            GlyphTile(Icons.Filled.QrCodeScanner, t("scan_qr"), onScanQr, Modifier.weight(1f), enabled = !busy)
                        }
                        // A QR in a screenshot or a saved photo is how most
                        // configs actually arrive - through a chat app, not a
                        // poster - and until now the only way to read one was
                        // to point the camera at another screen.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                            GlyphTile(Icons.Filled.QrCode2, t("scan_qr_image"), onQrFromImage, Modifier.weight(1f), enabled = !busy)
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                // Engines that carry traffic without a config of their own.
                // Psiphon first because nobody finds it by looking for
                // somewhere to paste something.
                Rail(t("add_group_tunnels"))
                SlabRow(
                    title = "Psiphon",
                    subtitle = t("add_psiphon_sub"),
                    icon = Icons.Filled.Public,
                    accent = c.good,
                    chevron = true,
                    enabled = !busy,
                    onClick = onPsiphon
                )
                SlabDivider()
                SlabRow(
                    title = "Tor",
                    subtitle = t("add_tor_sub"),
                    iconRes = R.drawable.tor,
                    accent = c.premium,
                    chevron = true,
                    enabled = !busy,
                    onClick = onTor
                )
                SlabDivider()
                SlabRow(
                    title = "SSH",
                    subtitle = t("add_ssh_sub"),
                    icon = Icons.Filled.Terminal,
                    accent = c.highlight,
                    chevron = true,
                    enabled = !busy,
                    onClick = onSsh
                )

                // DNS is its own kind of thing: the laboratory measures
                // resolvers and the tunnel rides on one, and neither is a
                // server you paste in.
                Rail(t("add_group_dns"))
                SlabRow(
                    title = t("dnslab_title"),
                    subtitle = t("add_dnslab_sub"),
                    icon = Icons.Filled.Dns,
                    accent = c.info,
                    chevron = true,
                    enabled = !busy,
                    onClick = onDnsLab
                )

                // The two full VPN protocols. OpenVPN reads a profile file;
                // IKEv2 is typed in, and the manual form already has it in its
                // protocol list - this is the entry point that says so.
                Rail(t("add_group_vpn"))
                SlabRow(
                    title = "OpenVPN",
                    subtitle = t("add_ovpn_sub"),
                    icon = Icons.Filled.Security,
                    accent = c.accentAlt,
                    chevron = true,
                    enabled = !busy,
                    onClick = onOpenVpn
                )
                SlabDivider()
                SlabRow(
                    title = "IKEv2 / IPsec",
                    subtitle = t("add_ikev2_sub"),
                    icon = Icons.Filled.Lock,
                    accent = c.warning,
                    chevron = true,
                    enabled = !busy,
                    onClick = onManual
                )

                // A link that maintains its own list. It was only reachable by
                // pasting one into the clipboard path and hoping the app
                // recognised it as a subscription rather than a config.
                Rail(t("add_group_sub"))
                SlabRow(
                    title = t("add_sub_row"),
                    subtitle = t("add_sub_row_sub"),
                    icon = Icons.Filled.Hub,
                    accent = c.primary,
                    chevron = true,
                    enabled = !busy,
                    onClick = onSubscription
                )

                Rail(t("add_group_providers"))
                SlabRow(
                    title = t("free_projects"),
                    subtitle = t("add_free_sub"),
                    icon = Icons.Filled.CardGiftcard,
                    accent = c.premium,
                    chevron = true,
                    enabled = !busy,
                    onClick = onProjects
                )
                SlabDivider()
                SlabRow(
                    title = t("ws_title"),
                    subtitle = t("add_ws_sub"),
                    icon = Icons.Filled.Shield,
                    accent = c.info,
                    chevron = true,
                    enabled = !busy,
                    onClick = onWindscribe
                )
            }
        }
    }
}

@Composable
private fun FreeProjectsScreen(
    store: ConfigStore,
    onOpenTor: () -> Unit,
    onAddManually: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val n: (String) -> String = { localizeDigits(it, lang) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var statusOwner by remember { mutableStateOf("") }
    var aetherMode by remember { mutableStateOf("masque") }
    var aetherH2 by remember { mutableStateOf(true) }
    val c = ghajarColors

    LaunchedEffect(status) {
        if (status.isNotEmpty()) { delay(4000); status = ""; statusOwner = "" }
    }

    Column(
        modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(GhajarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        // Free configs first, and rebuilt.
        //
        // This screen was the last one still made of Material cards with
        // borders inside a skin that has none, and the free-config block was
        // the part people came here for buried third. It says what it will do
        // before it does it - the window it reads, the cap, the latency it
        // accepts - because every one of those is a real rule in FreeConfigs
        // and not knowing them made "I got 6 configs" look like a failure.
        val freeBusy by FreeConfigs.busy.collectAsState()
        val freeProgress by FreeConfigs.progress.collectAsState()
        val freeIncomplete by FreeConfigs.incomplete.collectAsState()
        val subs by store.subscriptions.collectAsState()
        val configs by store.configs.collectAsState()
        val freeSub = remember(subs) { subs.firstOrNull { it.url == FreeConfigs.SOURCE_URL } }
        val savedCount = remember(configs, freeSub) {
            freeSub?.let { s -> configs.count { it.subId == s.id } } ?: 0
        }

        Slab(accent = if (freeBusy) c.highlight else c.premium, spacing = GhajarSpacing.md) {
            SlabRow(
                title = t("proj_free"),
                subtitle = "@" + FreeConfigs.CHANNEL,
                icon = Icons.Filled.CardGiftcard,
                accent = c.premium
            )
            // The state of the source, as one chip. "Not checked yet" and
            // "checked, found nothing" are different things and used to read
            // the same.
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                QuotaChip(
                    label = when {
                        freeBusy -> t("proj_free_working")
                        freeSub == null || freeSub.lastUpdated <= 0L -> t("free_never")
                        else -> t("sub_updated_at").format(formatStamp(freeSub.lastUpdated, lang))
                    },
                    level = if (freeSub == null || freeSub.lastUpdated <= 0L) 1 else 0
                )
            }
            // The rules this fetch runs under, each one read from the code that
            // enforces it rather than typed out here as a claim.
            Text(
                listOf(
                    t("free_src_window"),
                    t("free_src_cap").format(n("${net.gozar.app.freecfg.FreeFeedRules.MAX_MANAGED_CONFIGS}")),
                    t("free_src_latency").format(n("${FreeConfigs.MAX_LATENCY_MS}")),
                    t("free_src_dedupe")
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted
            )
            val p = freeProgress
            if (p != null && freeBusy) {
                Text(
                    (if (p.collecting) n("دریافت پیام‌ها (${p.pages} صفحه)") + " · " else "") +
                        localizeDigits(t("proj_free_testing").format(p.tested, p.total, p.alive), lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.highlight
                )
            }
            PillButton(
                text = when {
                    freeBusy -> t("proj_free_working")
                    freeSub != null -> t("refresh")
                    else -> t("add")
                },
                icon = Icons.Filled.Autorenew,
                enabled = !freeBusy,
                onClick = {
                    scope.launch {
                        val kept = FreeConfigs.refreshMultiSource(store, t("proj_free"))
                        statusOwner = "free"
                        status = when {
                            kept > 0 -> localizeDigits(t("proj_free_added").format(kept), lang) +
                                if (FreeConfigs.incomplete.value) "؛ بعضی منابع در دسترس نبودند، موارد قبلی حفظ شدند." else ""
                            kept == FreeConfigs.UNREACHABLE -> t("proj_free_unreachable")
                            kept == FreeConfigs.NO_CONFIGS -> t("proj_free_nocfg")
                            kept == FreeConfigs.BUSY -> t("proj_free_working")
                            else -> t("proj_free_none")
                        }
                    }
                }
            )
            GhostPill(
                text = t("free_add_config"),
                icon = Icons.Filled.Add,
                onClick = onAddManually
            )
            if (statusOwner == "free" && status.isNotEmpty()) {
                Text(
                    mixedText(status),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.primary
                )
            }
            if (freeIncomplete) {
                Text(
                    t("free_needs_tunnel"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.warning
                )
            }
        }

        Rail(
            n(t("free_saved").format("$savedCount")) +
                if (savedCount > 0) " · " + t("free_sorted") else ""
        )
        if (savedCount == 0) {
            SkinEmpty(
                title = t("free_empty_title"),
                hint = t("free_empty_hint"),
                icon = Icons.Filled.FileDownload
            )
        }

        Rail(t("proj_aether"))
        Slab(spacing = GhajarSpacing.md) {
            SlabRow(
                title = t("proj_aether_title"),
                subtitle = t("proj_aether_desc"),
                icon = Icons.Filled.Bolt,
                accent = c.info
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                listOf("masque" to "MASQUE", "wg" to "WireGuard", "gool" to "gool").forEach { (key, label) ->
                    val on = aetherMode == key
                    GhostPill(
                        text = label,
                        accent = if (on) c.highlight else c.textSecondary,
                        onClick = {
                            aetherMode = key
                            aetherH2 = key == "masque"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            SlabRow(
                title = t("proj_aether_h2"),
                subtitle = t("proj_aether_h2_sub"),
                icon = Icons.Filled.Bolt,
                accent = c.highlight,
                enabled = aetherMode == "masque",
                trailing = {
                    SkinSwitch(
                        checked = aetherH2 && aetherMode == "masque",
                        onCheckedChange = { aetherH2 = it },
                        enabled = aetherMode == "masque"
                    )
                }
            )
            PillButton(
                text = if (statusOwner == "aether" && status.isNotEmpty()) status else t("add"),
                icon = Icons.Filled.Add,
                onClick = {
                    if (!AetherController.available(context)) {
                        status = t("proj_aether_missing"); statusOwner = "aether"
                    } else {
                    val cfg = ProxyConfig(
                        name = "Aether (${aetherMode.uppercase()})",
                        protocol = "aether",
                        address = "127.0.0.1",
                        port = AetherController.SOCKS_PORT,
                        aetherMode = aetherMode,
                        aetherScan = "balanced",
                        aetherHttp2 = aetherH2,
                        source = ConfigSource.COMMUNITY
                    )
                    store.add(cfg)
                    status = t("proj_aether_added"); statusOwner = "aether"
                    }
                }
            )
        }

        Rail(t("legacy_warp"))
        Slab(spacing = GhajarSpacing.md) {
            SlabRow(
                title = t("legacy_warp"),
                subtitle = t("proj_warp_desc"),
                icon = Icons.Filled.Public,
                accent = c.warning
            )
            PillButton(
                text = when {
                    busy -> t("adding")
                    statusOwner == "warp" && status.isNotEmpty() -> status
                    else -> t("add_warp")
                },
                icon = Icons.Filled.Add,
                enabled = !busy,
                onClick = {
                    if (!busy) {
                    busy = true; status = ""; statusOwner = "warp"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { Warp.register() }
                        status = when (result) {
                            is Warp.Result.Success -> {
                                result.configs.forEach { store.add(it) }
                                t("warp_added")
                            }
                            is Warp.Result.Failure -> t("warp_failed")
                        }
                        busy = false
                    }
                    }
                }
            )
        }

        Rail("Tor")
        Slab(spacing = 0.dp) {
            SlabRow(
                title = "Tor",
                subtitle = t("proj_tor_desc"),
                iconRes = R.drawable.tor,
                accent = c.premium,
                chevron = true,
                onClick = onOpenTor
            )
        }

        Text(
            t("proj_more_soon"),
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompactMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(12.dp))
        Text(mixedText(label), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Modifier.appearOnce(delayMillis: Int = 0, offsetY: Float = 26f): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val spec = tween<Float>(360, delayMillis = delayMillis, easing = FastOutSlowInEasing)
    val a by animateFloatAsState(if (shown) 1f else 0f, spec, label = "appearA")
    val ty by animateFloatAsState(if (shown) 0f else offsetY, spec, label = "appearY")
    val sc by animateFloatAsState(if (shown) 1f else 0.96f, spec, label = "appearS")
    return this.graphicsLayer {
        alpha = a
        translationY = ty
        scaleX = sc
        scaleY = sc
    }
}

@Composable
private fun LabeledDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    // Every choice in every form comes through here. A handful of options is a
    // row of chips - you see them all and pick in one tap; a long list stays a
    // menu, but on the skin's filled box instead of Material's outlined button,
    // so a form is no longer a column of strokes.
    val c = ghajarColors
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(GhajarSpacing.xs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = c.textSecondary
        )
        if (options.size <= 4) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
            ) {
                options.forEach { opt ->
                    val on = opt == selected
                    Text(
                        opt,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = scriptFont(opt),
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) c.onPrimary else c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(GhajarRadius.pill))
                            .background(if (on) c.primary else c.secondaryCard)
                            .clickable { onSelect(opt) }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        } else {
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GhajarRadius.md))
                        .background(c.secondaryCard)
                        .clickable { open = true }
                        .padding(horizontal = GhajarSpacing.md, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
                ) {
                    Text(
                        selected,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = scriptFont(selected),
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = c.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    offset = DpOffset(0.dp, 8.dp),
                    shape = RoundedCornerShape(GhajarRadius.lg),
                    containerColor = c.card,
                    border = null
                ) {
                    options.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    opt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = scriptFont(opt),
                                    fontWeight = if (opt == selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (opt == selected) c.primary else c.textPrimary
                                )
                            },
                            trailingIcon = {
                                if (opt == selected) Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = c.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.height(42.dp),
                            onClick = { onSelect(opt); open = false }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun settingsDepth(key: String): Int = when (key) {
    "settings" -> 0
    "stability", "cleanip", "dnslab", "map", "perapp", "theme", "netcat" -> 2
    "checkhost" -> 3
    "netcatone" -> 3
    // Reached directly from the Settings list, like usage or tools.
    "ssh", "debugger" -> 1
    // One level below preferences, like the theme picker.
    "notifications" -> 2
    else -> 1
}

@Composable
private fun InfoBox(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    centered: Boolean = false
) {
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            mixedText(text),
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .clip(RoundedCornerShape(GhajarRadius.md))
                .background(accent.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun GlassDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    destructive: Boolean = false,
    accentOverride: Color? = null,
    body: @Composable ColumnScope.() -> Unit
) {
    // Every dialog in the app comes through here, so it is the skin's sheet:
    // a slab raised onto the surface tone, the title carried by a rail, and
    // the two actions as the skin's pills - confirm filled, dismiss ghost -
    // rather than two identical outlined buttons where nothing says which one
    // is the action you came for.
    val c = ghajarColors
    val accent = accentOverride ?: if (destructive) c.error else c.primary
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GhajarRadius.xl))
                .background(c.surface)
        ) {
            Column(
                Modifier.padding(GhajarSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 3.dp, height = 18.dp)
                            .clip(RoundedCornerShape(GhajarRadius.pill))
                            .background(accent)
                    )
                    Spacer(Modifier.width(GhajarSpacing.sm))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                }
                body()
                PillButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    accent = accent
                )
                if (dismissLabel != null) {
                    GhostPill(
                        text = dismissLabel,
                        onClick = onDismiss,
                        accent = c.textSecondary
                    )
                }
            }
            // The same top-edge light every slab has, in the dialog's accent.
            Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to accent.copy(alpha = 0.55f),
                        1f to Color.Transparent
                    )
                )
            }
        }
    }
}

/** One parsed block of a release note, in the skin's own type and colours. */
@Composable
private fun ReleaseNoteBlock(block: NoteBlock) {
    val c = ghajarColors
    when (block) {
        is NoteBlock.Heading -> Text(
            mixedText(block.text),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = c.primary,
            modifier = Modifier.padding(top = 4.dp)
        )

        is NoteBlock.Bullet -> Row(verticalAlignment = Alignment.Top) {
            Text("•", color = c.primary, modifier = Modifier.padding(end = 6.dp))
            Text(mixedText(block.text), style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
        }

        is NoteBlock.Paragraph -> Text(
            mixedText(block.text),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )

        // A real table: one clipped surface, the header on the brand tone, and
        // every row the same column widths so the columns line up.
        is NoteBlock.Table -> Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GhajarRadius.md))
                .background(c.secondaryCard)
        ) {
            val columns = maxOf(
                block.header?.size ?: 0,
                block.rows.maxOfOrNull { it.size } ?: 0
            ).coerceAtLeast(1)
            block.header?.let { header ->
                ReleaseNoteRow(header, columns, header = true)
                HorizontalDivider(color = c.border)
            }
            block.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = c.border.copy(alpha = 0.5f))
                ReleaseNoteRow(row, columns, header = false)
            }
        }
    }
}

@Composable
private fun ReleaseNoteRow(cells: List<String>, columns: Int, header: Boolean) {
    val c = ghajarColors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (header) Modifier.background(c.primary.copy(alpha = 0.12f)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Ragged rows are padded out here, in the layout, rather than in the
        // parser - the note said what it said.
        repeat(columns) { i ->
            Text(
                mixedText(cells.getOrElse(i) { "" }),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                color = if (header) c.textPrimary else c.textSecondary,
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            )
        }
    }
}

/** Full update flow (changelog, download+progress+cancel, checksum + signature
 * verification, install) for a release UpdateChecker found, reached from both
 * the periodic background check and the manual "check for updates" button.
 * Reuses GlassDialog's exact card/typography/button styling at every stage. */
@Composable
private fun UpdateFlowDialog(upd: UpdateChecker.Result.Available, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var stage by remember(upd.version) { mutableStateOf(0) } // 0 offer, 1 downloading, 2 verifying, 3 ready, 4 error
    var progress by remember(upd.version) { mutableStateOf(0f) }
    var errorText by remember(upd.version) { mutableStateOf<String?>(null) }
    var readyFile by remember(upd.version) { mutableStateOf<java.io.File?>(null) }
    var downloadJob by remember(upd.version) { mutableStateOf<Job?>(null) }

    fun startDownload() {
        val apk = upd.apk
        if (apk == null) { runCatching { uriHandler.openUri(upd.url) }; onDismiss(); return }
        stage = 1; progress = 0f; errorText = null
        downloadJob = scope.launch {
            when (val result = GhajarUpdateInstaller.download(context, apk) { read, total ->
                progress = if (total > 0) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            }) {
                is GhajarUpdateInstaller.DownloadResult.Cancelled -> stage = 0
                is GhajarUpdateInstaller.DownloadResult.Failed -> {
                    errorText = "دانلود ناموفق بود: ${result.reason}"
                    stage = 4
                }
                is GhajarUpdateInstaller.DownloadResult.Success -> {
                    stage = 2
                    val checksum = GhajarUpdateInstaller.verifySha256(result.file, upd.apkSha256)
                    if (checksum is GhajarUpdateInstaller.VerifyResult.ChecksumMismatch) {
                        result.file.delete()
                        errorText = "فایل دانلودشده با نسخهٔ منتشرشده مطابقت ندارد؛ ممکن است دانلود خراب شده باشد. دوباره تلاش کن."
                        stage = 4
                        return@launch
                    }
                    val signature = GhajarUpdateInstaller.verifySignatureMatchesInstalled(context, result.file)
                    if (signature is GhajarUpdateInstaller.VerifyResult.SignatureMismatch) {
                        result.file.delete()
                        errorText = signature.reason
                        stage = 4
                        return@launch
                    }
                    // A checksum/signature Unavailable is reported, not hidden — the file is still
                    // safe to install (Android's own installer re-verifies the APK signature).
                    errorText = listOfNotNull(
                        (checksum as? GhajarUpdateInstaller.VerifyResult.Unavailable)?.reason,
                        (signature as? GhajarUpdateInstaller.VerifyResult.Unavailable)?.reason
                    ).joinToString("\n").takeIf { it.isNotBlank() }
                    readyFile = result.file
                    stage = 3
                }
            }
        }
    }

    GlassDialog(
        onDismiss = {
            if (stage == 1) downloadJob?.cancel()
            onDismiss()
        },
        title = when (stage) {
            1 -> "در حال دانلود نسخهٔ ${upd.version}"
            2 -> "در حال بررسی فایل"
            3 -> "نسخهٔ ${upd.version} آماده نصب است"
            4 -> "بروزرسانی ناموفق بود"
            else -> "نسخهٔ جدید ${upd.version} موجود است"
        },
        confirmLabel = when (stage) {
            1 -> "لغو دانلود"
            2 -> "لطفاً صبر کن…"
            3 -> "نصب"
            4 -> "تلاش دوباره"
            else -> "دانلود و بروزرسانی"
        },
        dismissLabel = if (stage == 0) "بعداً" else if (stage == 1) null else "بستن",
        onConfirm = {
            when (stage) {
                0 -> startDownload()
                1 -> downloadJob?.cancel()
                2 -> Unit
                3 -> readyFile?.let { GhajarUpdateInstaller.install(context, it) }
                4 -> startDownload()
                else -> Unit
            }
        }
    ) {
        when (stage) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (upd.changelog.isNotBlank()) {
                    Text("تغییرات این نسخه:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    // The release body is Markdown. Rendering it as one flat
                    // bulleted list turned headings into bullets and a table
                    // into a row of pipe characters; ReleaseNotes parses the
                    // three shapes a release note actually uses.
                    val blocks = remember(upd.changelog) { ReleaseNotes.parse(upd.changelog) }
                    Column(
                        // The dialog bounds its own body now, so no second cap
                        // here: a long release note scrolls instead of being
                        // cut at a fixed height.
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
                    ) {
                        blocks.forEach { block -> ReleaseNoteBlock(block) }
                    }
                }
                if (upd.apk == null) {
                    Text("فایل APK متناسب با این دستگاه در این نسخه پیدا نشد؛ صفحهٔ ریلیز باز می‌شود.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (upd.apkSha256 == null) {
                    Text("این نسخه فایل SHA256SUMS.txt منتشر نکرده؛ صحت فایل پس از دانلود قابل تأیید کامل نیست.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            2 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("بررسی SHA-256 و امضای بستهٔ دانلودشده…", style = MaterialTheme.typography.bodySmall)
            }
            3 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("فایل دانلود و بررسی شد. تنظیمات و اطلاعات فعلی برنامه در نصب حفظ می‌شود.",
                    style = MaterialTheme.typography.bodySmall)
                errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (!GhajarUpdateInstaller.canInstallPackages(context)) {
                    Text("برای نصب باید اجازهٔ «نصب از منابع ناشناس» را برای قاجار وی‌پی‌ان فعال کنی.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    BounceOutlinedButton(onClick = {
                        runCatching { context.startActivity(GhajarUpdateInstaller.unknownSourcesSettingsIntent(context)) }
                    }, minHeight = 38.dp, modifier = Modifier.fillMaxWidth()) { Text("باز کردن تنظیمات") }
                }
            }
            4 -> Text(errorText ?: "خطای نامشخص", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ShopScreen(modifier: Modifier = Modifier) {
    val t = stringsFn()
    val lang = LocalLang.current
    val plans = listOf(
        Triple("۱ ماهه · 30 GB", "۱۲۰,۰۰۰", "1 month · 30 GB"),
        Triple("۲ ماهه · 80 GB", "۲۲۰,۰۰۰", "2 months · 80 GB"),
        Triple("۳ ماهه · 150 GB", "۳۲۰,۰۰۰", "3 months · 150 GB"),
        Triple("۶ ماهه · 400 GB", "۵۹۰,۰۰۰", "6 months · 400 GB"),
        Triple("۱ ساله · نامحدود", "۹۹۰,۰۰۰", "1 year · Unlimited"),
        Triple("۲ ساله · نامحدود", "۱,۷۹۰,۰۰۰", "2 years · Unlimited"),
        Triple("اشتراک خانواده", "۱,۲۹۰,۰۰۰", "Family plan"),
        Triple("اشتراک نمایندگی", "۲,۹۹۰,۰۰۰", "Reseller plan")
    )

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().blur(14.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            plans.forEach { (fa, price, en) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (lang == Lang.FA) fa else en,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (lang == Lang.FA) "تحویل آنی" else "Instant delivery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            price + if (lang == Lang.FA) " تومان" else " T",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Box(
            Modifier.fillMaxSize().background(
                MaterialTheme.colorScheme.background.copy(alpha = 0.35f)
            )
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.ShoppingBag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
                Text(
                    t("shop_soon"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    t("shop_soon_sub"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WindscribeScreen(store: ConfigStore, modifier: Modifier = Modifier) {
    val t = stringsFn()
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var nodes by remember { mutableStateOf<List<WindscribeNode>>(emptyList()) }
    val picked = remember { mutableStateMapOf<String, Boolean>() }
    var status by remember { mutableStateOf("") }
    var statusOwner by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (nodes.isEmpty()) {
            loading = true
            nodes = WindscribeFetcher.fetch()
            loading = false
            if (nodes.isEmpty()) status = t("ws_fetch_failed")
        }
    }

    val shown = remember(nodes, query) {
        if (query.isBlank()) nodes
        else nodes.filter {
            it.label.contains(query, true) || it.hostname.contains(query, true) ||
                    it.country.contains(query, true)
        }
    }
    val grouped = remember(shown) {
        shown.groupBy { it.country.ifBlank { "?" } }.toList().sortedBy { it.first }
    }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    var locationsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val count = picked.count { it.value }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "intro") {
                Text(
                    mixedText(t("ws_intro")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item(key = "creds") {
                TorCountryGroup(t("ws_credentials")) {
                    OutlinedTextField(
                        user, { user = it },
                        label = { Text(t("ikev2_user")) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        pass, { pass = it },
                        label = { Text(t("password")) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        accentText(
                            t("ws_creds_hint"),
                            "windscribe.com/myaccount",
                            "Config Generators",
                            "IKEv2"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (loading) item(key = "loading") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(t("ws_loading"), style = MaterialTheme.typography.bodySmall)
                }
            }

            item(key = "locations") {
                androidx.compose.animation.AnimatedVisibility(
                    visible = nodes.isNotEmpty(),
                    enter = fadeIn(tween(320)) +
                            expandVertically(tween(400, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 6 },
                    exit = fadeOut(tween(180)) + shrinkVertically(tween(260))
                ) {
                    WindscribeLocationsHeader(
                        title = t("ws_locations"),
                        total = nodes.size,
                        chosen = count,
                        expanded = locationsOpen,
                        searchOpen = searchOpen,
                        allSelected = shown.isNotEmpty() &&
                                shown.all { picked[it.hostname] == true },
                        onSelectAll = {
                            val target = !(shown.isNotEmpty() &&
                                    shown.all { picked[it.hostname] == true })
                            shown.forEach { node ->
                                if (target) picked[node.hostname] = true
                                else picked.remove(node.hostname)
                            }
                        },
                        onToggle = { locationsOpen = !locationsOpen },
                        onToggleSearch = {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        },
                        query = query,
                        onQuery = { query = it },
                        searchLabel = t("search_servers")
                    )
                }
            }

            if (locationsOpen && nodes.isNotEmpty()) {
                items(grouped, key = { "c-" + it.first }) { (country, servers) ->
                    val open2 = open[country] == true || query.isNotBlank()
                    val chosen2 = servers.count { picked[it.hostname] == true }
                    WindscribeCountryCard(
                        country = country,
                        total = servers.size,
                        chosen = chosen2,
                        expanded = open2,
                        onToggle = { open[country] = !open2 },
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = tween(220),
                                placementSpec = tween(220, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(140)
                            )
                            .padding(horizontal = 12.dp)
                    ) {
                        servers.forEach { node ->
                            WindscribeServerRow(
                                label = node.label,
                                selected = picked[node.hostname] == true,
                                accent = FlagColors.of(country)
                                    ?: MaterialTheme.colorScheme.primary,
                                onClick = {
                                    picked[node.hostname] = picked[node.hostname] != true
                                }
                            )
                        }
                    }
                }
            }

            if (nodes.isEmpty() && !loading) item(key = "retry") {
                BounceOutlinedButton(
                    onClick = {
                        scope.launch {
                            loading = true
                            status = ""
                            nodes = WindscribeFetcher.fetch()
                            loading = false
                            if (nodes.isEmpty()) status = t("ws_fetch_failed")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t("netmon_recheck"), maxLines = 1)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (status.isNotEmpty()) Text(
                mixedText(status),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            BounceButton(
                onClick = {
                    val chosen = nodes.filter { picked[it.hostname] == true }
                    if (user.isBlank() || pass.isBlank()) {
                        status = t("ws_need_creds")
                        return@BounceButton
                    }
                    if (chosen.isEmpty()) {
                        status = t("ws_need_host")
                        return@BounceButton
                    }
                    store.addToLocalSub(
                        WindscribeBrand.SUB_NAME,
                        chosen.map { node ->
                            ProxyConfig(
                                name = node.label,
                                protocol = "ikev2",
                                address = node.ip.ifBlank { node.hostname },
                                port = 500,
                                uuid = user.trim(),
                                password = pass.trim(),
                                sni = node.hostname,
                                network = "ikev2",
                                security = "none",
                                source = ConfigSource.COMMUNITY
                            )
                        }
                    )
                    picked.clear()
                    status = t("ws_added").format(chosen.size)
                },
                enabled = count > 0 && user.isNotBlank() && pass.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (count > 0) t("ws_add_n").format(localizeDigits("$count", lang))
                    else t("add"),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun WindscribeLocationsHeader(
    title: String,
    total: Int,
    chosen: Int,
    expanded: Boolean,
    searchOpen: Boolean,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onToggle: () -> Unit,
    onToggleSearch: () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    searchLabel: String
) {
    val lang = LocalLang.current
    val rot by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "wsOuterChevron"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggle() }
                    .padding(
                        start = 16.dp,
                        end = 8.dp,
                        top = 14.dp,
                        bottom = if (expanded && searchOpen) 2.dp else 14.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    mixedText(title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (chosen > 0)
                        localizeDigits("$chosen", lang) + " / " + localizeDigits("$total", lang)
                    else localizeDigits("$total", lang),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (chosen > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(200)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160))
                ) {
                    Icon(
                        if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                        contentDescription = null,
                        tint = if (allSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                            .clip(CircleShape)
                            .clickable { onSelectAll() }
                            .padding(6.dp)
                            .size(19.dp)
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(200)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160))
                ) {
                    Icon(
                        if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = null,
                        tint = if (searchOpen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onToggleSearch() }
                            .padding(6.dp)
                            .size(19.dp)
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp)
                        .size(20.dp)
                        .graphicsLayer { rotationZ = rot }
                )
            }

            AnimatedVisibility(
                visible = expanded && searchOpen,
                enter = fadeIn(tween(180)) +
                        expandVertically(tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(120)) +
                        shrinkVertically(tween(180, easing = FastOutSlowInEasing))
            ) {
                OutlinedTextField(
                    query, onQuery,
                    label = { Text(searchLabel) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun WindscribeServerRow(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val onAccent = Color.White
    val shape = RoundedCornerShape(14.dp)
    var center by remember { mutableStateOf(Offset.Zero) }
    var sz by remember { mutableStateOf(IntSize.Zero) }

    val maxR = remember(center, sz) {
        val dx = maxOf(center.x, sz.width - center.x)
        val dy = maxOf(center.y, sz.height - center.y)
        sqrt(dx * dx + dy * dy)
    }
    val radius by animateFloatAsState(
        targetValue = if (selected) maxR else 0f,
        animationSpec = tween(if (selected) 480 else 300, easing = FastOutSlowInEasing),
        label = "wsRowFill"
    )
    val frac = if (maxR > 0f) (radius / maxR).coerceIn(0f, 1f) else 0f
    val textColor = lerp(MaterialTheme.colorScheme.onSurface, onAccent, frac)

    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .onGloballyPositioned {
                sz = it.size
                if (center == Offset.Zero) center = Offset(it.size.width / 2f, it.size.height / 2f)
            }
            .drawBehind {
                if (radius > 0.5f) drawCircle(color = accent, radius = radius, center = center)
            }
            .border(
                1.dp,
                lerp(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    accent.copy(alpha = 0.65f),
                    frac
                ),
                shape
            )
            .pointerInput(Unit) {
                detectTapGestures(onPress = { center = it }, onTap = { onClick() })
            }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                mixedText(label),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = textColor.copy(alpha = frac),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun WindscribeCountryCard(
    country: String,
    total: Int,
    chosen: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val lang = LocalLang.current
    val rot by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "wsChevron"
    )
    val accent = FlagColors.of(country) ?: MaterialTheme.colorScheme.primary
    val border by animateColorAsState(
        targetValue = if (chosen > 0) accent.copy(alpha = 0.75f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "wsBorder"
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountryFlag(country, height = 16.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    mixedText(countryName(country).ifBlank { country.uppercase() }),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (chosen > 0)
                        localizeDigits("$chosen", lang) + " / " + localizeDigits("$total", lang)
                    else localizeDigits("$total", lang),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (chosen > 0) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rot }
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) +
                        expandVertically(tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(120)) +
                        shrinkVertically(tween(180, easing = FastOutSlowInEasing))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun TorNodesScreen(store: ConfigStore, modifier: Modifier = Modifier) {
    val t = stringsFn()
    val context = LocalContext.current
    val ready = remember { TorController.available(context) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val configs by store.configs.collectAsState()
    val selectedId by store.selectedId.collectAsState()
    val base = configs.find { it.id == selectedId && it.protocol != "tor" }
        ?: configs.firstOrNull { it.protocol != "tor" && it.protocol != "aether" }
    val baseId = base?.id ?: ""
    var throughVpn by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var statusOwner by remember { mutableStateOf("") }

    LaunchedEffect(status) { if (status.isNotEmpty()) { delay(3000); status = "" } }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            t("tor_nodes_intro"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!ready) {
            InfoBox(
                t("proj_tor_missing"),
                accent = MaterialTheme.colorScheme.error,
                centered = true
            )
        }

        SettingsGroup {
            SettingRow(
                title = t("tor_through_vpn"),
                subtitle = t("tor_through_vpn_sub"),
                checked = throughVpn,
                onCheckedChange = { throughVpn = it },
                icon = Icons.Filled.Hub
            )
            if (throughVpn) {
                Text(
                    if (base != null) t("tor_base_is").format(base.name) else t("tor_base_none"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (base != null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )
            }
        }

        TorCountryGroup(t("tor_exit_country")) {
            TorController.Countries.filter { it.first.isNotEmpty() }.forEach { (code, label) ->
                val on = code in picked
                val boxTint by animateColorAsState(
                    targetValue = if (on) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "torCountryTint"
                )
                val boxFill by animateFloatAsState(
                    targetValue = if (on) 1f else 0f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "torCountryFill"
                )
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(GhajarRadius.md))
                        .background(ghajarColors.secondaryCard)
                        .clickable {
                            picked = if (on) picked - code else picked + code
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CountryFlag(code, height = 14.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    SmoothCheckbox(checked = on)
                }
            }
        }

        BounceButton(
            onClick = {
                if (!ready) { status = t("proj_tor_missing"); return@BounceButton }
                val list = if (picked.isEmpty()) listOf("") else picked.toList()
                list.forEach { code ->
                    val label = TorController.Countries.firstOrNull { it.first == code }?.second
                    store.add(
                        ProxyConfig(
                            name = if (code.isEmpty()) "Tor" else "Tor - " + (label ?: code),
                            protocol = "tor",
                            address = "127.0.0.1",
                            port = TorController.SOCKS_PORT,
                            torCountry = code,
                            torThroughVpn = throughVpn,
                            torBaseId = if (throughVpn) baseId else "",
                            source = ConfigSource.COMMUNITY
                        )
                    )
                }
                status = t("proj_tor_added")
            },
            enabled = ready && (!throughVpn || base != null),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (picked.isEmpty()) t("tor_add_auto")
                else t("tor_add_n").format(localizeDigits("${picked.size}", LocalLang.current)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false
            )
        }

        AnimatedVisibility(
            visible = status.isNotEmpty(),
            enter = fadeIn(tween(220)) + expandVertically(tween(260, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing))
        ) {
            InfoBox(status, centered = true)
        }
    }
}

@Composable
private fun SmoothCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val mark = MaterialTheme.colorScheme.onPrimary
    val p by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "checkboxFill"
    )
    Canvas(modifier.size(22.dp)) {
        val stroke = 1.6.dp.toPx()
        val radius = 7.dp.toPx()
        drawRoundRect(
            color = lerp(idle.copy(alpha = 0.50f), tint, p),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = stroke)
        )
        if (p > 0.004f) {
            val grow = size.minDimension * p
            drawRoundRect(
                color = tint.copy(alpha = p),
                topLeft = Offset((size.width - grow) / 2f, (size.height - grow) / 2f),
                size = Size(grow, grow),
                cornerRadius = CornerRadius(radius * p, radius * p)
            )
            val tick = Path().apply {
                moveTo(size.width * 0.27f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.70f)
                lineTo(size.width * 0.75f, size.height * 0.32f)
            }
            val pm = PathMeasure().apply { setPath(tick, false) }
            val seg = Path()
            pm.getSegment(0f, pm.length * p, seg, true)
            drawPath(
                seg,
                color = mark.copy(alpha = p),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun CheckHostScreen(modifier: Modifier = Modifier) {
    val t = stringsFn()
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var host by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("ping") }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val currentIp by LocationFetcher.lastIp.collectAsState()
    var touched by remember { mutableStateOf(false) }

    LaunchedEffect(currentIp) {
        if (!touched && currentIp.isNotBlank()) host = currentIp
    }
    var nodes by remember { mutableStateOf(listOf<CheckHost.Node>()) }
    var info by remember { mutableStateOf<CheckHost.IpInfo?>(null) }
    val results = remember { mutableStateMapOf<String, CheckHost.NodeResult>() }

    fun run() {
        val target = host.trim()
        if (running || target.isEmpty()) return
        focus.clearFocus()
        running = true
        error = ""
        nodes = emptyList()
        info = null
        results.clear()
        scope.launch {
            launch { info = CheckHost.ipInfo(target) }
            val ok = CheckHost.run(
                host = target,
                kind = kind,
                onNodes = { list ->
                    nodes = list
                    list.forEach { results[it.id] = CheckHost.NodeResult.Pending }
                },
                onResults = { map -> map.forEach { (k, v) -> results[k] = v } }
            )
            if (!ok) error = t("chk_failed")
            running = false
        }
    }

    Column(
        modifier.fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focus.clearFocus() }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it; touched = true },
            singleLine = true,
            label = { Text(t("chk_host")) },
            placeholder = {
                Text(
                    mixedText(currentIp.ifEmpty { "example.com" }),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            textStyle = LocalTextStyle.current.copy(
                fontFamily = if (LocalLang.current == Lang.FA) VazirFont else LexendFont
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { run() }, onDone = { focus.clearFocus() }),
            trailingIcon = {
                if (host.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.clickable { host = ""; touched = true; focus.clearFocus() }
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ping" to t("chk_ping"), "http" to t("chk_http"), "tcp" to t("chk_tcp")).forEach { (k, label) ->
                val on = kind == k
                BounceOutlinedButton(
                    onClick = { kind = k },
                    minHeight = 40.dp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    accent = if (on) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(mixedText(label), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        BounceButton(
            onClick = { run() },
            enabled = !running && host.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (running) t("chk_running") else t("chk_start"),
                maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false
            )
        }

        if (error.isNotEmpty()) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        info?.let { i ->
            SettingsGroup(t("chk_info")) {
                listOf(
                    t("chk_ip") to i.ip,
                    t("chk_asn") to i.asn,
                    t("chk_org") to i.org,
                    t("chk_country") to i.country,
                    t("chk_region") to i.region,
                    t("chk_city") to i.city,
                    t("chk_tz") to i.timezone
                ).filter { it.second.isNotBlank() && it.second != " ()" && it.second != ", " }
                    .forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                k,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(96.dp)
                            )
                            if (k == t("chk_country") && i.countryCode.length == 2) {
                                CountryFlag(i.countryCode, height = 12.dp)
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(
                                monoText(v),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
            }
        }

        if (nodes.isNotEmpty()) {
            SettingsGroup {
                nodes.forEach { node ->
                    val res = results[node.id] ?: CheckHost.NodeResult.Pending
                    val tint = when (res) {
                        is CheckHost.NodeResult.Ok -> AppGreen
                        is CheckHost.NodeResult.Failed -> ghajarColors.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadarDot(tint, res is CheckHost.NodeResult.Pending)
                        Spacer(Modifier.width(8.dp))
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (node.countryCode.length == 2) {
                                CountryFlag(node.countryCode, height = 13.dp)
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(
                                mixedText(node.city.ifEmpty { node.country.ifEmpty { node.id } }),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (node.city.isNotEmpty() && node.country.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    mixedText(node.country),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(tint.copy(alpha = 0.14f))
                                .border(1.dp, tint.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .animateContentSize(tween(320, easing = FastOutSlowInEasing)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (res) {
                                    is CheckHost.NodeResult.Ok -> localizeDigits(
                                        String.format(
                                            java.util.Locale.US,
                                            if (res.avgMs < 10) "%.1f" else "%.0f",
                                            res.avgMs
                                        ), lang
                                    ) + " " + t("unit_ms")
                                    is CheckHost.NodeResult.Failed -> t("netmon_down")
                                    else -> t("testing")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = tint,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetRadarRow(site: NetMonitor.Site, st: NetMonitor.State) {
    val t = stringsFn()
    val lang = LocalLang.current
    val target = when (st) {
        is NetMonitor.State.Reachable -> AppGreen
        is NetMonitor.State.Sanctioned -> ghajarColors.warning
        is NetMonitor.State.Unreachable -> ghajarColors.error
        is NetMonitor.State.Testing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tint by animateColorAsState(target, tween(400), label = "radarTint")
    val label = when (st) {
        is NetMonitor.State.Reachable -> t("netmon_up")
        is NetMonitor.State.Sanctioned -> t("netmon_sanctioned")
        is NetMonitor.State.Unreachable -> t("netmon_down")
        is NetMonitor.State.Testing -> t("testing")
        else -> "\u2014"
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadarDot(tint, st is NetMonitor.State.Testing)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(mixedText(site.name), style = MaterialTheme.typography.bodyMedium)
            Text(
                scriptRuns(site.host, MonoFont),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.12f))
                .border(1.dp, tint.copy(alpha = 0.38f), RoundedCornerShape(9.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .animateContentSize(tween(320, easing = FastOutSlowInEasing)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = st is NetMonitor.State.Reachable,
                enter = fadeIn(tween(280)) + expandHorizontally(tween(320)),
                exit = fadeOut(tween(160)) + shrinkHorizontally(tween(240))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (st is NetMonitor.State.Reachable)
                            localizeDigits("${st.ms}", lang) + " " + t("unit_ms") else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
            Crossfade(targetState = label, animationSpec = tween(300), label = "radarLabel") { l ->
                Text(
                    l,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RadarDot(tint: Color, pulsing: Boolean) {
    // The ripple was created whether or not this dot was pulsing, and these
    // dots sit in lists - the site monitor draws one per row. That was a frame
    // callback per visible row, forever, for a ripple most of them were not
    // drawing. Now the animation exists only while the row is actually being
    // tested.
    val ripple by ghajarPulse(
        active = pulsing,
        durationMillis = 1700,
        reverse = false
    )
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        val sc = 0.40f + ripple * 0.60f
                        scaleX = sc; scaleY = sc
                        alpha = (1f - ripple) * 0.6f
                    }
                    .background(Brush.radialGradient(listOf(tint, Color.Transparent)), CircleShape)
            )
        }
        Box(
            Modifier
                .size(16.dp)
                .background(
                    Brush.radialGradient(listOf(tint.copy(alpha = 0.40f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(Modifier.size(9.dp).clip(CircleShape).background(tint))
    }
}

@Composable
private fun NetMonitorScreen(onOpenCategories: () -> Unit, modifier: Modifier = Modifier) {
    val t = stringsFn()
    val lang = LocalLang.current
    val states by RadarRunner.states.collectAsState()
    val running by RadarRunner.running.collectAsState()
    val conn by VpnState.state.collectAsState()
    val viaTunnel = conn == Connection.CONNECTED

    fun run() {
        if (!running) RadarRunner.start(viaTunnel)
    }

    LaunchedEffect(Unit) {
        if (states.isEmpty() && !running) RadarRunner.start(viaTunnel)
    }

    val reachable = states.values.count { it is NetMonitor.State.Reachable }
    val done = states.values.count { it !is NetMonitor.State.Testing }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InfoBox(
                if (viaTunnel) t("netmon_via_tunnel") else t("netmon_via_direct"),
                accent = if (viaTunnel) AppGreen else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            val spin = rememberInfiniteTransition(label = "radarSpin")
            val angle by spin.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = ghajarEndless(infiniteRepeatable(
                    animation = tween(1100, easing = LinearEasing)
                )),
                label = "radarSpinAngle"
            )
            BounceIconButton(onClick = { run() }, enabled = !running) {
                Icon(
                    Icons.Filled.Autorenew,
                    contentDescription = t("netmon_recheck"),
                    tint = if (running) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                        .graphicsLayer { rotationZ = if (running) angle else 0f }
                )
            }
        }

        SettingsGroup {
            NetMonitor.Essential.forEach { site ->
                NetRadarRow(site, states[site.host] ?: NetMonitor.State.Idle)
            }
        }

        Text(
            t("netmon_summary").format(
                localizeDigits("$reachable", lang),
                localizeDigits("${NetMonitor.Essential.size}", lang)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        SettingsHubCard(
            icon = Icons.Filled.Apps,
            title = t("netcat_title"),
            subtitle = t("netcat_sub"),
            onClick = onOpenCategories
        )
    }
}

@Composable
private fun NetCategoriesScreen(onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    val t = stringsFn()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NetMonitor.Categories.forEachIndexed { i, cat ->
            SettingsHubCard(
                icon = when (cat.icon) {
                    "ai" -> Icons.Filled.SmartToy
                    "social" -> Icons.Filled.Groups
                    "gaming" -> Icons.Filled.SportsEsports
                    "trading" -> Icons.Filled.TrendingUp
                    "news" -> Icons.AutoMirrored.Filled.Article
                    else -> null
                },
                iconRes = if (cat.icon == "iranian") R.drawable.iran else null,
                title = t(cat.key),
                subtitle = t("netcat_count").format(
                    localizeDigits("${cat.sites.size}", LocalLang.current)
                ),
                onClick = { onOpen(i) }
            )
        }
    }
}

@Composable
private fun NetCategoryScreen(index: Int, modifier: Modifier = Modifier) {
    val t = stringsFn()
    val scope = rememberCoroutineScope()
    val cat = NetMonitor.Categories.getOrNull(index) ?: return
    val states = remember(index) { mutableStateMapOf<String, NetMonitor.State>() }
    val conn by VpnState.state.collectAsState()
    val viaTunnel = conn == Connection.CONNECTED

    LaunchedEffect(index) {
        cat.sites.forEach { states[it.host] = NetMonitor.State.Testing }
        NetMonitor.probeAll(viaTunnel, cat.sites) { site, state -> states[site.host] = state }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (viaTunnel) t("netmon_via_tunnel") else t("netmon_via_direct"),
            style = MaterialTheme.typography.bodySmall,
            color = if (viaTunnel) AppGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsGroup {
            cat.sites.forEach { site ->
                NetRadarRow(site, states[site.host] ?: NetMonitor.State.Idle)
            }
        }
    }
}

@Composable
private fun SettingsHubCard(
    icon: ImageVector? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color? = null,
    iconRes: Int? = null,
    accents: List<String> = emptyList()
) {
    // Kept as one entry point so the screens that still call it adopt the new
    // skin with no edit: a hub card is now a one-row slab. The accents overload
    // (highlighted words inside the subtitle) is preserved.
    val c = ghajarColors
    val accent = tint ?: c.primary
    Slab(spacing = 0.dp, padding = GhajarSpacing.md) {
        if (accents.isEmpty()) {
            SlabRow(
                title = title,
                subtitle = subtitle,
                icon = icon,
                iconRes = iconRes,
                accent = accent,
                chevron = true,
                onClick = onClick
            )
        } else {
            SlabRow(
                title = title,
                icon = icon,
                iconRes = iconRes,
                accent = accent,
                chevron = true,
                onClick = onClick
            )
            Text(
                accentText(subtitle, *accents.toTypedArray()),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(start = 50.dp, bottom = GhajarSpacing.sm)
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    store: ConfigStore,
    scrollState: ScrollState,
    onOpenUsage: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenNetMon: () -> Unit,
    onOpenSsh: () -> Unit,
    onOpenDebugger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val usage by UsageStore.usage.collectAsState()
    val allTime = remember(usage) { UsageStore.totalAll(usage) }
    val configs by store.configs.collectAsState()
    val subscriptions by store.subscriptions.collectAsState()
    val c = ghajarColors

    // Rails name the categories; each category is ONE slab holding its rows.
    // The old page was eight separate outlined cards in a column, which read as
    // eight equally important things.
    Column(
        modifier.fillMaxSize().verticalScroll(scrollState)
            .padding(horizontal = GhajarSpacing.lg, vertical = GhajarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        ScreenHeader(
            title = t("settings"),
            context = t("settings_header_sub")
        )

        // The page used to open as nothing but a list of doors. These are the
        // three numbers that say what this install actually holds, read from
        // the same stores the pages behind those doors read.
        StatStrip(
            listOf(
                StatCell(t("count_configs"), localizeDigits("${configs.size}", lang), c.primary),
                StatCell(t("count_subs"), localizeDigits("${subscriptions.size}", lang), c.info),
                StatCell(t("data_usage"), formatBytes(allTime[0] + allTime[1], lang), c.premium, onClick = onOpenUsage)
            )
        )

        Rail(t("sec_connection"))
        Slab(spacing = 0.dp) {
            SlabRow(
                title = t("connection_settings"),
                subtitle = t("connection_settings_sub"),
                icon = Icons.Filled.Router,
                chevron = true,
                onClick = onOpenConnection
            )
            SlabDivider()
            SlabRow(
                title = t("tools"),
                subtitle = t("tools_sub"),
                icon = Icons.Filled.Build,
                chevron = true,
                onClick = onOpenTools
            )
        }

        Rail(t("sec_diagnostics"))
        // The debugger and SSH used to be top-level tabs. Same screens, same
        // capabilities, reached from here so the bar can stay at three.
        Slab(spacing = 0.dp) {
            SlabRow(
                title = t("debugger"),
                subtitle = t("debugger_settings_sub"),
                iconRes = R.drawable.ic_royal_tools,
                chevron = true,
                onClick = onOpenDebugger
            )
            SlabDivider()
            SlabRow(
                title = t("netmon_title"),
                subtitle = t("netmon_sub"),
                icon = Icons.Filled.TravelExplore,
                chevron = true,
                onClick = onOpenNetMon
            )
            SlabDivider()
            SlabRow(
                title = t("ssh"),
                subtitle = t("ssh_settings_sub"),
                iconRes = R.drawable.ic_royal_tunnel,
                chevron = true,
                onClick = onOpenSsh
            )
        }

        Rail(t("sec_usage"))
        Slab(spacing = 0.dp) {
            SlabRow(
                title = t("data_usage"),
                subtitle = formatBytes(allTime[0] + allTime[1], lang),
                icon = Icons.Filled.DataUsage,
                chevron = true,
                onClick = onOpenUsage
            )
        }

        Rail(t("sec_app"))
        Slab(spacing = 0.dp) {
            SlabRow(
                title = t("preferences"),
                subtitle = t("preferences_sub"),
                icon = Icons.Filled.Tune,
                chevron = true,
                onClick = onOpenPreferences
            )
            SlabDivider()
            SlabRow(
                title = t("about"),
                subtitle = t("about_sub"),
                icon = Icons.Filled.Info,
                chevron = true,
                onClick = onOpenAbout
            )
        }

        Rail(t("sec_data"))
        BackupRow(store)
    }
}

@Composable
private fun BackupRow(store: ConfigStore) {
    val t = stringsFn()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("") }
    var statusOwner by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    // A password-protected backup can't be told apart from "not a backup at
    // all" without the password first - isBackup()/decodeBackup() both throw
    // NeedsPassword() for it. That used to be swallowed as a flat, wrong
    // "این فایل کانفیگ اشتراکی است، نه بکاپ" regardless of the real cause.
    var needsPassword by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordError by remember { mutableStateOf("") }

    // The password the *export* is sealed with, kept apart from the one an
    // import is unlocked with. Sharing one field between the two meant the
    // password you had just typed to open somebody's file was still sitting
    // there as the password for the next file you wrote.
    var newPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    // Which password the file being written should carry. Set at the moment the
    // button is pressed, because the file picker comes back later and the field
    // may have been cleared by then.
    var exportPassword by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(status) {
        if (status.isNotEmpty()) { delay(3500); status = "" }
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConfigFile.MIME)
    ) { uri ->
        if (uri != null) {
            busy = true
            val password = exportPassword
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val data = ConfigFile.encodeBackup(
                            context,
                            store.configs.value,
                            store.subscriptions.value,
                            store.settingsSnapshot(),
                            password
                        )
                        context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                        true
                    }.getOrDefault(false)
                }
                status = if (ok) t("backup_done") else t("backup_failed")
                busy = false
                exportPassword = null
            }
        } else {
            exportPassword = null
        }
    }

    val opener = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                when {
                    bytes == null || bytes.isEmpty() -> status = t("import_bad_file")
                    runCatching { ConfigFile.isPasswordProtected(bytes) }.getOrDefault(false) -> {
                        needsPassword = true
                        backupPassword = ""
                        backupPasswordError = ""
                        pending = bytes
                    }
                    else -> {
                        // Every failure used to collapse into "this is a shared
                        // config, not a backup", which was wrong for every cause
                        // except one. The real ones are distinguishable.
                        val outcome = runCatching { ConfigFile.isBackup(context, bytes, null) }
                        when (outcome.getOrNull()) {
                            true -> { needsPassword = false; pending = bytes }
                            false -> status = t("backup_not_backup")
                            else -> status = when (outcome.exceptionOrNull()) {
                                is ConfigFile.ForeignBuild -> t("backup_foreign_build")
                                is ConfigFile.ForeignApp -> t("import_foreign_app")
                                else -> t("import_bad_file")
                            }
                        }
                    }
                }
            }
        }
    }

    // Backup used to be two small outlined chips squeezed under the settings
    // list. It is the feature people reach for when something has gone wrong,
    // so it now reads as a real section: what the file contains, a full-width
    // primary action to write one, a ghost action to read one back, and the
    // outcome as a proper state rather than a grey caption.
    val c = ghajarColors
    val lang = store.lang.value
    // What a written file actually carries, counted from live state so the
    // numbers are never a guess. The OpenVPN count is read here rather than
    // assumed: a backup that silently carried no profiles used to be
    // indistinguishable from one that carried them all.
    val configCount = store.configs.value.size
    val subCount = store.subscriptions.value.size
    val ovpnCount = remember { runCatching { GhajarOpenVpnBridge.exportProfiles(context).size }.getOrDefault(0) }

    Slab(spacing = GhajarSpacing.md) {
        SlabRow(
            title = t("backup_title"),
            subtitle = t("backup_header_sub"),
            icon = Icons.Filled.Inventory2,
            accent = c.premium
        )
        StatStrip(
            listOf(
                StatCell(t("count_configs"), localizeDigits("$configCount", lang), c.info),
                StatCell(t("count_subs"), localizeDigits("$subCount", lang), c.premium),
                StatCell(t("count_ovpn"), localizeDigits("$ovpnCount", lang), c.accentAlt)
            )
        )
        // Two sentences that used to be nowhere: what is in the file, and the
        // one thing people assume is in it and is not. Someone who restores a
        // backup expecting their wallet balance back has lost nothing, but they
        // have spent an evening looking for it.
        Text(
            t("backup_contents"),
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary
        )
        Text(
            t("backup_excludes"),
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted
        )

        SlabDivider()

        // The password. It is the file's only protection: a backup is not
        // bound to the signing certificate any more (that change is what made
        // old backups portable at all), so an unsealed file is readable by any
        // build of this app that can open it. Hence a real field, the minimum
        // spelled out, and the unprotected path kept but demoted to a ghost
        // action with the consequence written next to it.
        SkinField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = t("backup_pw_label"),
            helper = t("backup_pw_note"),
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailing = {
                Icon(
                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showPassword = !showPassword }
                        .padding(6.dp)
                        .size(20.dp)
                )
            }
        )

        val strongEnough = newPassword.length >= 8
        fun writeBackup(password: String?) {
            if (busy) return
            exportPassword = password
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                .format(java.util.Date())
            saver.launch("ghajarvpn-backup-$stamp.${ConfigFile.EXTENSION}")
        }

        PillButton(
            text = if (strongEnough || newPassword.isEmpty()) t("backup_export")
            else t("backup_pw_short"),
            icon = Icons.Filled.FileUpload,
            enabled = !busy && strongEnough,
            onClick = { writeBackup(newPassword) }
        )
        // Still offered, because removing it would break every existing
        // workflow that restores a file without a password, and because the
        // password is the user's risk to take.
        GhostPill(
            text = t("backup_no_pw"),
            enabled = !busy,
            accent = c.textSecondary,
            onClick = { writeBackup(null) }
        )
        Text(
            t("backup_no_pw_warn"),
            style = MaterialTheme.typography.labelSmall,
            color = c.warning
        )

        SlabDivider()

        GhostPill(
            text = t("backup_import"),
            icon = Icons.Filled.FileDownload,
            onClick = { opener.launch(arrayOf("*/*")) }
        )
        AnimatedVisibility(visible = status.isNotEmpty()) {
            Text(
                mixedText(status),
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    pending?.let { bytes ->
        var preview by remember(bytes) { mutableStateOf<ConfigFile.Backup?>(null) }
        var previewFailed by remember(bytes) { mutableStateOf(false) }
        var passwordSubmitted by remember(bytes) { mutableStateOf(false) }
        LaunchedEffect(bytes, passwordSubmitted) {
            if (needsPassword && !passwordSubmitted) return@LaunchedEffect
            val pw = if (needsPassword) backupPassword else null
            val result = withContext(Dispatchers.IO) {
                runCatching { ConfigFile.decodeBackup(context, bytes, pw) }
            }
            result.onSuccess { preview = it }.onFailure { e ->
                if (needsPassword && e is ConfigFile.WrongPassword) {
                    backupPasswordError = t("import_wrong_password")
                    passwordSubmitted = false
                } else {
                    previewFailed = true
                }
            }
        }
        val awaitingPassword = needsPassword && preview == null && !previewFailed
        GlassDialog(
            onDismiss = { pending = null; needsPassword = false },
            title = t("backup_import"),
            confirmLabel = if (awaitingPassword) t("import_button") else "جایگزینی کامل",
            dismissLabel = t("cancel"),
            accentOverride = AppGreen,
            onConfirm = {
                if (awaitingPassword) {
                    if (backupPassword.isNotEmpty()) { backupPasswordError = ""; passwordSubmitted = true }
                } else {
                    val result = preview
                    pending = null
                    needsPassword = false
                    if (result != null) {
                        store.restoreBackup(result.configs, result.subs, result.settings)
                        GhajarOpenVpnSettings.restore(context, result.openVpnSettings)
                        NetworkRules.restore(context, result.networkRules)
                        val ovpnOutcome = GhajarOpenVpnBridge.importProfiles(context, result.openVpnProfiles, merge = false)
                        status = localizeDigits(
                            t("backup_restored").format(result.configs.size, result.subs.size) +
                                if (result.openVpnProfiles.isNotEmpty()) " + ${ovpnOutcome.added} پروفایل OpenVPN" else "",
                            store.lang.value
                        )
                    } else status = t("import_bad_file")
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    awaitingPassword -> {
                        Text(t("import_needs_password"), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            backupPassword, { backupPassword = it; backupPasswordError = "" },
                            label = { Text(t("import_password")) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (backupPasswordError.isNotEmpty()) {
                            Text(backupPasswordError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    previewFailed -> Text(t("import_bad_file"), color = MaterialTheme.colorScheme.error)
                    preview == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("در حال خواندن فایل بکاپ…", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        val p = preview!!
                        Text(
                            "این فایل شامل ${p.configs.size} کانفیگ و ${p.subs.size} اشتراک است.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (p.openVpnProfiles.isEmpty()) {
                            Text(
                                "توجه: این بکاپ پروفایل OpenVPN ندارد (بکاپ قدیمی یا بدون پروفایل ذخیره‌شده).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "شامل ${p.openVpnProfiles.size} پروفایل OpenVPN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(mixedText(t("backup_restore_q")), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(color = ghajarColors.border)
                        BounceOutlinedButton(
                            onClick = {
                                pending = null
                                val report = store.mergeBackup(p.configs, p.subs)
                                val ovpnOutcome = GhajarOpenVpnBridge.importProfiles(context, p.openVpnProfiles, merge = true)
                                status = localizeDigits(
                                    "افزوده شد: ${report.addedConfigs} کانفیگ، ${report.addedSubscriptions} اشتراک، ${ovpnOutcome.added} پروفایل OpenVPN" +
                                        " (تکراری نادیده گرفته شد: ${report.duplicateConfigs} کانفیگ، ${report.duplicateSubscriptions} اشتراک، ${ovpnOutcome.duplicates} پروفایل)",
                                    store.lang.value
                                )
                            },
                            minHeight = 38.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("در عوض فقط افزودن (بدون حذف موارد فعلی)") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsScreen(
    store: ConfigStore,
    onOpenStability: () -> Unit,
    onOpenCleanIp: () -> Unit,
    onOpenCheckHost: () -> Unit,
    onOpenDnsLab: () -> Unit,
    onOpenMap: () -> Unit,
    onSwitch: (ProxyConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val context = LocalContext.current
    val autoSelect by store.autoSelect.collectAsState()
    val onionRouting by store.onionRouting.collectAsState()
    val adBlock by store.adBlock.collectAsState()
    val blockWhenOff by store.blockWhenOff.collectAsState()
    var vpnShareOpen by remember { mutableStateOf(false) }
    var connectionHistoryOpen by remember { mutableStateOf(false) }
    // Nine unrelated entries in one flat stack: sharing next to ad-blocking
    // next to a Cloudflare scanner. Grouped by what they are for, same
    // entries and same destinations.
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = GhajarSpacing.lg, vertical = GhajarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        ScreenHeader(title = t("tools"), context = t("tools_header_sub"))

        Rail(t("sec_sharing"))
        Slab(spacing = 0.dp) {
            SlabRow(
                title = "اشتراک‌گذاری VPN",
                subtitle = "اتصال دستگاه‌های دیگر از طریق هات‌اسپات همین گوشی",
                icon = Icons.Filled.Wifi,
                chevron = true,
                onClick = { vpnShareOpen = true }
            )
            SlabDivider()
            SlabRow(
                title = "تاریخچهٔ اتصال",
                subtitle = "زمان و وضعیت آخرین اتصال‌ها، قطعی‌ها و خطاها",
                icon = Icons.Filled.History,
                chevron = true,
                onClick = { connectionHistoryOpen = true }
            )
            SlabDivider()
            SlabRow(
                title = "لاگ و اشکال‌زدایی",
                subtitle = "مشاهده و دانلود گزارش کامل رویدادها و خطاها",
                icon = Icons.Filled.BugReport,
                chevron = true,
                onClick = { context.startActivity(Intent(context, GhajarLogActivity::class.java)) }
            )
        }

        Rail(t("sec_measure"))
        Slab(spacing = 0.dp) {
            SlabRow(
                title = t("stab_title"),
                subtitle = t("stab_sub"),
                icon = Icons.Filled.NetworkCheck,
                chevron = true,
                onClick = onOpenStability
            )
            SlabDivider()
            SlabRow(
                title = t("chk_title"),
                subtitle = t("chk_sub"),
                icon = Icons.Filled.Dns,
                chevron = true,
                onClick = onOpenCheckHost
            )
            SlabDivider()
            SlabRow(
                title = t("scan_warp"),
                subtitle = t("scan_sub"),
                iconRes = R.drawable.cloudflare,
                chevron = true,
                onClick = onOpenCleanIp
            )
            SlabDivider()
            SlabRow(
                title = t("dnslab_title"),
                subtitle = t("dnslab_sub"),
                icon = Icons.Filled.Dns,
                chevron = true,
                onClick = onOpenDnsLab
            )
            SlabDivider()
            SlabRow(
                title = t("map_title"),
                subtitle = t("map_subtitle"),
                icon = Icons.Filled.Public,
                chevron = true,
                onClick = onOpenMap
            )
        }

        Rail(t("sec_privacy"))
        SettingsGroup {
            SettingRow(
                title = t("adblock_title"),
                subtitle = t("adblock_sub"),
                checked = adBlock,
                onCheckedChange = { store.setAdBlock(it) },
                icon = Icons.Filled.Shield
            )
            AnimatedVisibility(visible = adBlock) {
                // A dependent sub-setting, so it sits on the nested card tone
                // with the brand hairline rather than a tinted block.
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(GhajarRadius.sm))
                        .background(ghajarColors.secondaryCard)
                        .border(1.dp, ghajarColors.primary.copy(alpha = 0.30f), RoundedCornerShape(GhajarRadius.sm))
                        .padding(horizontal = GhajarSpacing.md, vertical = GhajarSpacing.sm)
                ) {
                    SettingRow(
                        title = t("adblock_always_title"),
                        subtitle = t("adblock_always_sub"),
                        checked = blockWhenOff,
                        onCheckedChange = { store.setBlockWhenOff(it) },
                        icon = Icons.Filled.Shield
                    )
                }
            }
            SettingRow(
                title = t("onion_title"),
                subtitle = t("onion_sub"),
                checked = onionRouting,
                onCheckedChange = { store.setOnionRouting(it) },
                icon = Icons.Filled.Hub
            )
            SettingRow(
                title = t("smart_connect"),
                subtitle = t("smart_connect_sub"),
                checked = autoSelect,
                onCheckedChange = { store.setAutoSelect(it) },
                icon = Icons.Filled.Bolt
            )
        }
    }
    if (vpnShareOpen) VpnShareDialog(store = store, onSwitch = onSwitch, onDismiss = { vpnShareOpen = false })
    if (connectionHistoryOpen) ConnectionHistoryDialog(onDismiss = { connectionHistoryOpen = false })
}

/**
 * "VPN Only" sharing: exposes the engine's own local SOCKS5 inbound (already
 * bound to 127.0.0.1 for every connection, see ConfigBuilder's socksIn) on
 * this device's hotspot interface instead. A device connected to this
 * phone's hotspot can point its Wi-Fi proxy settings at this phone's
 * hotspot IP and the shown port to route through the exact same tunnel this
 * phone uses. Fail-closed by construction: the SOCKS listener lives inside
 * the same Xray core process as the tunnel itself, so disconnecting or
 * losing the VPN tears the listener down with it — there is no path for a
 * connected device to fall through to this phone's raw internet.
 *
 * The other two requested modes are NOT implemented, and not faked:
 * - "VPN + Internet" (mixed, chosen routes) would need to selectively
 *   redirect only some destinations from hotspot clients while leaving
 *   others direct. VpnService only ever intercepts this device's own
 *   per-UID-selected traffic; it has no API to inspect or redirect packets
 *   arriving from other devices over the hotspot interface at all.
 * - "VPN only for connected devices, host stays direct" needs the same
 *   thing in reverse (redirect guest traffic, leave the host alone) and
 *   hits the identical wall: without root-level netfilter rules on the
 *   hotspot interface, Android gives this app no hook into hotspot client
 *   traffic. The SOCKS relay above is the only mechanism available without
 *   root, and it only ever affects a device that explicitly configures
 *   itself to use it — it cannot make that separation automatic.
 */
@Composable
private fun VpnShareDialog(store: ConfigStore, onSwitch: (ProxyConfig) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val enabled by store.vpnShareEnabled.collectAsState()
    val connected by VpnState.state.collectAsState()
    val activeId by VpnState.activeId.collectAsState()
    val configs by store.configs.collectAsState()
    // Toggling the switch only takes effect on the next connect, exactly
    // like every other tunnel-affecting setting in this app (adBlock,
    // splitRouting, ...). But leaving an open (no-password) proxy running
    // after the user pressed "stop sharing" would be a real leak, not just
    // a UI inconsistency - so this one setting forces a live rebuild of the
    // current tunnel through the same switchTo() sequencing a manual server
    // switch already uses, instead of waiting for the next reconnect.
    fun applyLiveIfConnected() {
        if (connected == Connection.CONNECTED) {
            configs.find { it.id == activeId }?.let(onSwitch)
        }
    }
    val socksPort = MixedPort.value
    val httpPort = HttpSharePort.value
    // Re-read every few seconds instead of once: switching Wi-Fi/hotspot
    // while this dialog stays open must not keep showing a stale address.
    var hotspotIp by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            hotspotIp = withContext(Dispatchers.IO) { hotspotInterfaceAddress() }
            delay(3000)
        }
    }
    val shareUser by store.vpnShareUsername.collectAsState()
    val sharePass by store.vpnSharePassword.collectAsState()
    var showGuide by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (enabled) store.ensureVpnShareCredential() }
    // Only the Xray-core engine (ConfigBuilder's socks-in/http-share-in)
    // actually exposes the shared proxy - OpenVPN and IKEv2 run through
    // completely separate engines with no such inbound at all, so telling
    // the user it's active there would be a real IP/port that never works.
    val activeProtocol = configs.find { it.id == activeId }?.protocol
    val xraySupported = !activeId.orEmpty().startsWith("ovpn:") && activeProtocol != "ikev2"
    val live = enabled && connected == Connection.CONNECTED && xraySupported

    fun copy(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        android.widget.Toast.makeText(context, "$label کپی شد", android.widget.Toast.LENGTH_SHORT).show()
    }

    GlassDialog(
        onDismiss = onDismiss,
        title = "VPN Share",
        confirmLabel = "بستن",
        onConfirm = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("اشتراک‌گذاری اتصال VPN با دستگاه دیگر", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(GhajarRadius.sm))
                    .background(ghajarColors.secondaryCard)
                    .border(1.dp, ghajarColors.border, RoundedCornerShape(GhajarRadius.sm))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "فعال‌سازی اشتراک‌گذاری", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                SkinSwitch(checked = enabled, onCheckedChange = { store.setVpnShareEnabled(it); applyLiveIfConnected() })
            }
            if (enabled && connected != Connection.CONNECTED) {
                Text(
                    "وضعیت: غیرفعال (برای شروع، اول از صفحهٔ اصلی به یک سرور وصل شو)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
            } else if (enabled && connected == Connection.CONNECTED && !xraySupported) {
                Text(
                    "وضعیت: غیرفعال (این قابلیت فقط برای پروتکل‌های Xray کار می‌کند؛ اتصال فعلی OpenVPN یا IKEv2 است)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
            }
            if (live) {
                Text("وضعیت: فعال", fontWeight = FontWeight.Bold, color = AppGreen)
                val ip = hotspotIp
                if (ip == null) {
                    Text("ابتدا هات‌اسپات همین گوشی را روشن کن.", color = MaterialTheme.colorScheme.error)
                } else {
                    ShareAddressRow("آدرس پراکسی (HTTP، برای تنظیمات Wi-Fi)", ip, httpPort.toString(), ::copy)
                    Text(
                        "بدون رمز؛ هر دستگاهی در همین شبکه می‌تواند از این آدرس استفاده کند.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error
                    )
                    HorizontalDivider(color = ghajarColors.border)
                    ShareAddressRow("آدرس SOCKS5 (امن‌تر؛ برای اپ/مرورگری که SOCKS را پشتیبانی کند)",
                        ip, socksPort.toString(), ::copy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("کاربری: $shareUser", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { copy("نام کاربری", shareUser) },
                            contentPadding = PaddingValues(4.dp)) { Text("کپی") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("رمز: $sharePass", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { copy("رمز", sharePass) },
                            contentPadding = PaddingValues(4.dp)) { Text("کپی") }
                    }
                    // Typing a 24-character hex password into a second phone by
                    // hand is how this feature stopped being used. The QR
                    // carries the whole SOCKS5 endpoint - address, port, user
                    // and password - in the standard URI form, so a client that
                    // reads proxy QRs is configured in one scan. It is only
                    // offered while sharing is genuinely live, so the code can
                    // never encode an address that is not listening.
                    TextButton(onClick = { showQr = true }) {
                        Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("نمایش QR اتصال")
                    }
                    TextButton(onClick = { store.regenerateVpnShareCredential(); applyLiveIfConnected() }) {
                        Text("تولید رمز SOCKS5 جدید")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showGuide = true }, modifier = Modifier.weight(1f)) {
                        Text("راهنمای اتصال")
                    }
                    OutlinedButton(
                        onClick = { store.setVpnShareEnabled(false); applyLiveIfConnected() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("توقف اشتراک‌گذاری") }
                }
            }
            HorizontalDivider(color = ghajarColors.border)
            Text(
                "این پراکسی فقط ترافیکی را که خودت به آن دستگاه اجازه می‌دهی از VPN رد می‌کند، نه کل دستگاه دوم را؛ بستگی به این دارد که خود آن دستگاه یا برنامه‌اش پراکسی را رعایت کند. اشتراک‌گذاری کامل ترافیک دستگاه دوم بدون دسترسی روت روی اندروید ممکن نیست.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Reuses the app's existing QR sheet rather than drawing a second one. The
    // address is re-read here, so a hotspot that changed while the dialog was
    // open cannot produce a code pointing at the old one.
    // Sharing stopping while the sheet is open closes it, in an effect rather
    // than a branch: clearing the flag during composition would write state
    // from the composition that reads it.
    LaunchedEffect(live) { if (!live) showQr = false }
    val qrIp = hotspotIp
    if (showQr && live && qrIp != null && shareUser.isNotBlank() && sharePass.isNotBlank()) {
        QrDialog(
            link = "socks5://$shareUser:$sharePass@$qrIp:$socksPort",
            title = "VPN Share",
            onDismiss = { showQr = false }
        )
    }

    if (showGuide) {
        VpnShareSetupDialog(
            sharingOn = enabled,
            tunnelUp = connected == Connection.CONNECTED,
            engineSupported = xraySupported,
            hotspotIp = hotspotIp,
            socksPort = socksPort,
            httpPort = httpPort,
            shareUser = shareUser,
            sharePass = sharePass,
            onCopy = ::copy,
            onDismiss = { showGuide = false }
        )
    }
}

/**
 * VPN Share, checked rather than described.
 *
 * The old guide was six sentences with no values in them and no idea whether
 * any of it was true. This runs [ShareDoctor] first - is sharing on, is a
 * tunnel up, does this engine even publish the inbound, is the hotspot's own
 * interface up, and is anything actually accepting a connection on the address
 * the user is about to type into a second phone - and only then lays out the
 * steps, with that device's real address, port and credential in them.
 */
@Composable
private fun VpnShareSetupDialog(
    sharingOn: Boolean,
    tunnelUp: Boolean,
    engineSupported: Boolean,
    hotspotIp: String?,
    socksPort: Int,
    httpPort: Int,
    shareUser: String,
    sharePass: String,
    onCopy: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = ghajarColors
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    var report by remember { mutableStateOf<DoctorReport?>(null) }
    var run by remember { mutableStateOf(0) }

    LaunchedEffect(run, sharingOn, tunnelUp, hotspotIp) {
        report = null
        report = ShareDoctor.run(
            sharingOn = sharingOn,
            tunnelUp = tunnelUp,
            engineSupported = engineSupported,
            hotspotIp = hotspotIp,
            socksPort = socksPort,
            httpPort = httpPort,
            credentialSet = shareUser.isNotBlank() && sharePass.isNotBlank()
        )
    }

    val current = report
    // The steps are only worth showing once there is something for the second
    // device to connect to; otherwise they would send the user to type an
    // address that nothing is listening on.
    val usable = current != null && current.worst != DoctorVerdict.FAIL && hotspotIp != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("share_setup_title")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
            ) {
                DoctorBody(current, allOkKey = "share_all_ok")
                if (usable && hotspotIp != null) {
                    HorizontalDivider(color = c.border)
                    Text(
                        t("share_steps_title"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = c.textSecondary
                    )
                    ShareStep("1", t("share_step_join"))
                    ShareStep("2", t("share_step_wifi"))
                    ShareStep("3", t("share_step_manual"))
                    ShareStep("4", t("share_step_host").format(hotspotIp))
                    ShareStep("5", t("share_step_port").format(httpPort.toString()))
                    ShareStep("6", t("share_step_save"))
                    HorizontalDivider(color = c.border)
                    Text(
                        t("share_step_socks_title"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = c.textSecondary
                    )
                    ShareStep("1", t("share_step_socks_addr").format("$hotspotIp:$socksPort"))
                    ShareStep("2", t("share_step_socks_cred"))
                    GhostPill(
                        t("share_copy_socks"),
                        onClick = {
                            onCopy(
                                t("share_setup_title"),
                                "socks5://$shareUser:$sharePass@$hotspotIp:$socksPort"
                            )
                        }
                    )
                }
            }
        },
        confirmButton = { PillButton(t("doc_close"), onDismiss) },
        dismissButton = {
            GhostPill(t("doc_again"), onClick = { run++ }, enabled = current != null)
        }
    )
}

/**
 * The leak-protection report.
 *
 * Two of the five findings are Android's to grant, not this app's, so the
 * dialog's own action is the shortcut to the screen where the user grants
 * them - always-on VPN and "block connections without VPN".
 */
@Composable
private fun LeakGuardDialog(store: ConfigStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    var report by remember { mutableStateOf<DoctorReport?>(null) }
    var run by remember { mutableStateOf(0) }

    LaunchedEffect(run) {
        report = null
        report = LeakGuard.run(context, store)
    }

    val current = report
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("leak_title")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
            ) {
                DoctorBody(current, allOkKey = "leak_all_ok")
                Text(
                    t("leak_note"),
                    style = MaterialTheme.typography.bodySmall,
                    color = ghajarColors.textMuted
                )
            }
        },
        confirmButton = {
            PillButton(t("leak_open_settings"), onClick = {
                // The OEM-specific screen first, the generic one as a
                // fallback - the same pair the kill-switch card already uses.
                runCatching {
                    context.startActivity(
                        Intent("android.net.vpn.SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            })
        },
        dismissButton = {
            GhostPill(t("doc_again"), onClick = { run++ }, enabled = current != null)
        }
    )
}

@Composable
private fun ShareStep(number: String, text: String) {
    val c = ghajarColors
    Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
        Text(
            localizeDigits(number, LocalLang.current),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = c.primary
        )
        Text(mixedText(text), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
    }
}

@Composable
private fun ShareAddressRow(label: String, ip: String, port: String, onCopy: (String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("آدرس: $ip", modifier = Modifier.weight(1f))
            TextButton(onClick = { onCopy("آدرس", ip) }, contentPadding = PaddingValues(4.dp)) { Text("کپی آدرس") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("پورت: $port", modifier = Modifier.weight(1f))
            TextButton(onClick = { onCopy("پورت", port) }, contentPadding = PaddingValues(4.dp)) { Text("کپی پورت") }
        }
    }
}

@Composable
private fun TorCountryGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            content()
        }
    }
}

@Composable
private fun ConfigDebuggerScreen(
    store: ConfigStore,
    onSwitch: (ProxyConfig) -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val configs by store.configs.collectAsState()
    val selectedId by store.selectedId.collectAsState()
    val config = configs.find { it.id == selectedId }

    if (config == null) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsGroup {
                Text(
                    t("dbg_no_config"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val staticChecks = remember(config) { ConfigDebug.inspect(config) }

    var tick by remember { mutableStateOf(0) }
    var connecting by remember(config.id) { mutableStateOf(false) }
    val allResults by DebugRunner.results.collectAsState()
    val runningIds by DebugRunner.running.collectAsState()
    val result = allResults[config.id]
    val testing = runningIds.contains(config.id)

    LaunchedEffect(config.id, tick, active) {
        if (!active) return@LaunchedEffect
        if (tick == 0) return@LaunchedEffect
        if (VpnState.activeId.value != config.id ||
            VpnState.state.value != Connection.CONNECTED
        ) {
            connecting = true
            onSwitch(config)
            withTimeoutOrNull(30000) {
                VpnState.state.first {
                    (it == Connection.CONNECTED && VpnState.activeId.value == config.id) ||
                            it == Connection.ERROR
                }
            }
            connecting = false
        }
        DebugRunner.start(config, store)
    }

    val info = result?.info
    var shownInfo by remember(config.id) { mutableStateOf<ProbeInfo?>(null) }
    if (info != null) shownInfo = info

    val checks = staticChecks + (result?.findings ?: emptyList())
    val problems = checks.filter { it.level != DebugLevel.OK }
    val broken = checks.count { it.level == DebugLevel.BAD }
    val warned = checks.count { it.level == DebugLevel.WARN }
    val state = result?.state ?: DebugState.TIMEOUT
    val pingMs = result?.pingMs ?: -1
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val stateColor = when {
        testing || result == null -> MaterialTheme.colorScheme.primary
        state == DebugState.HEALTHY -> AppGreen
        state == DebugState.TIMEOUT -> ghajarColors.warning
        state == DebugState.BLOCKED -> ghajarColors.error
        state == DebugState.OFFLINE -> ghajarColors.textMuted
        else -> MaterialTheme.colorScheme.error
    }
    val stateLabel = when {
        connecting -> t("dbg_connecting")
        testing || result == null -> t("dbg_testing")
        state == DebugState.HEALTHY -> t("dbg_healthy")
        state == DebugState.TIMEOUT -> t("dbg_timeout")
        state == DebugState.BLOCKED -> t("dbg_blocked")
        state == DebugState.OFFLINE -> t("dbg_offline")
        else -> t("dbg_broken")
    }
    val tint by animateColorAsState(stateColor, tween(400), label = "dbgState")

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsGroup {
            Row(
                Modifier.animateContentSize(tween(320, easing = FastOutSlowInEasing)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadarDot(tint, testing)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    if (config.locked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.weight(1f)) {
                                MarqueeName(GhajarUiRules.brandedConfigName(config.name), MaterialTheme.typography.titleSmall)
                            }
                        }
                    } else {
                        MarqueeName(GhajarUiRules.brandedConfigName(config.name), MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        if (config.locked) AnnotatedString(t("locked_config"))
                        else monoText(
                            localizeDigits(
                                "${config.protocol} \u00b7 ${config.address}:${config.port}", lang
                            )
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BounceIconButton(onClick = { if (!testing) tick++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = t("dbg_recheck"))
                }
            }
        }

        SettingsGroup(t("dbg_state")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = stateLabel,
                    transitionSpec = {
                        (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 2 } +
                                fadeIn(tween(320))) togetherWith
                                (slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { -it / 2 } +
                                        fadeOut(tween(180)))
                    },
                    label = "dbgStateLabel",
                    modifier = Modifier.weight(1f)
                ) { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(9.dp))
                        .background(tint.copy(alpha = 0.12f))
                        .border(1.dp, tint.copy(alpha = 0.38f), RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        monoText(
                            t("dbg_ping") + "  " + localizeDigits("$pingMs", lang) +
                                    if (pingMs >= 0) " " + t("unit_ms") else ""
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        maxLines = 1
                    )
                }
            }
            Text(
                if (broken == 0 && warned == 0) t("dbg_all_ok")
                else t("dbg_issues").format(
                    localizeDigits("$broken", lang),
                    localizeDigits("$warned", lang)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = info != null,
            enter = fadeIn(tween(340)) +
                    slideInVertically(tween(340, easing = FastOutSlowInEasing)) { it / 4 } +
                    expandVertically(tween(340, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(220)) + shrinkVertically(tween(280, easing = FastOutSlowInEasing))
        ) {
            shownInfo?.let { info ->
                SettingsGroup(t("dbg_info")) {
                    DebugInfoRow(t("dbg_part_transport"), info.method)
                    if (info.entryIp.isNotBlank() && !info.entryIp.equals(info.exitIp, true)) DebugInfoRow(
                        t("dbg_part_entry"),
                        listOf(info.entryIp, countryName(info.entryCountry), info.entryIsp)
                            .filter { it.isNotBlank() }
                            .joinToString(" \u00b7 ")
                    )
                    DebugInfoRow(
                        t("dbg_part_ip"),
                        if (info.exitIp.isBlank()) t("dbg_exit_offline")
                        else listOf(info.exitIp, countryName(info.exitCountry), info.exitIsp)
                            .filter { it.isNotBlank() }
                            .joinToString(" \u00b7 ")
                    )
                    DebugInfoRow(
                        t("dbg_part_iptype"),
                        info.kind.ifBlank { t("dbg_rep_unavailable") },
                        if (info.flagged) MaterialTheme.colorScheme.error else null
                    )
                    DebugInfoRow(
                        t("dbg_part_risk"),
                        if (info.reputation < 0) t("dbg_rep_unavailable")
                        else localizeDigits("${info.reputation} / 100", lang) + " \u00b7 " + info.repBand,
                        when {
                            info.reputation < 0 -> null
                            info.reputation >= 60 -> AppGreen
                            info.reputation >= 40 -> ghajarColors.warning
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    if (info.flags.isNotBlank()) DebugInfoRow(
                        t("dbg_part_flags"), info.flags, MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        SettingsGroup(t("dbg_client_checks")) {
            AnimatedVisibility(
                visible = problems.isEmpty(),
                enter = fadeIn(tween(280)) + expandVertically(tween(280, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing))
            ) {
                Text(
                    t("dbg_all_ok"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            problems.forEach { check ->
                val color = when (check.level) {
                    DebugLevel.OK -> AppGreen
                    DebugLevel.WARN -> ghajarColors.warning
                    DebugLevel.BAD -> MaterialTheme.colorScheme.error
                }
                val icon = when (check.level) {
                    DebugLevel.OK -> Icons.Filled.CheckCircle
                    DebugLevel.WARN -> Icons.Filled.Warning
                    DebugLevel.BAD -> Icons.Filled.Cancel
                }
                Row(
                    Modifier.fillMaxWidth()
                        .animateContentSize(tween(300, easing = FastOutSlowInEasing)),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.padding(top = 2.dp).size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t(check.partKey),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (check.level == DebugLevel.OK) check.value.ifBlank { "\u2014" }
                            else t(check.noteKey),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (check.level == DebugLevel.OK)
                                MaterialTheme.colorScheme.onSurfaceVariant else color
                        )
                        if (check.level != DebugLevel.OK && check.value.isNotBlank()) Text(
                            monoText(check.value),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        ServerChecksGroup(config)
        PanelChecksGroup(config)
    }
}

@Composable
private fun CollapsibleGroup(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    mixedText(title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                val angle by animateFloatAsState(
                    targetValue = if (expanded) -90f else 0f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    label = "groupChevron"
                )
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = angle }
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(280, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(200, delayMillis = 60)),
                exit = shrinkVertically(tween(240, easing = FastOutSlowInEasing)) +
                        fadeOut(tween(120))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(2.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun ProbeCheckRow(check: DebugCheck, index: Int, stamp: Any?) {
    val t = stringsFn()
    val color = when (check.level) {
        DebugLevel.OK -> AppGreen
        DebugLevel.WARN -> ghajarColors.warning
        DebugLevel.BAD -> MaterialTheme.colorScheme.error
    }
    val icon = when (check.level) {
        DebugLevel.OK -> Icons.Filled.CheckCircle
        DebugLevel.WARN -> Icons.Filled.Warning
        DebugLevel.BAD -> Icons.Filled.Cancel
    }
    var shown by remember(stamp, index) { mutableStateOf(false) }
    LaunchedEffect(stamp, index) {
        delay(index * 45L)
        shown = true
    }
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "probeFade"
    )
    val rise by animateFloatAsState(
        targetValue = if (shown) 0f else 14f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "probeRise"
    )
    Row(
        Modifier.fillMaxWidth().graphicsLayer { alpha = fade; translationY = rise },
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = 2.dp).size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t(check.partKey), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (check.noteKey.isBlank()) check.value.ifBlank { "\u2014" } else t(check.noteKey),
                style = MaterialTheme.typography.bodySmall,
                color = if (check.level == DebugLevel.OK)
                    MaterialTheme.colorScheme.onSurfaceVariant else color
            )
            if (check.noteKey.isNotBlank() && check.value.isNotBlank()) Text(
                monoText(check.value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PanelChecksGroup(config: ProxyConfig) {
    val t = stringsFn()
    val context = LocalContext.current
    val store = remember { SshStore.get(context) }
    val scope = rememberCoroutineScope()

    var expanded by remember(config.id) { mutableStateOf(false) }
    var kind by remember(config.id) {
        mutableStateOf(store.panelKind(config.id).ifBlank { "3x-ui" })
    }
    var url by remember(config.id) {
        mutableStateOf(store.panelUrl(config.id).ifBlank { config.address })
    }
    var user by remember(config.id) { mutableStateOf(store.panelUser(config.id)) }
    var pass by remember(config.id) { mutableStateOf(store.panelPass(config.id)) }
    var showPass by remember(config.id) { mutableStateOf(false) }
    var report by remember(config.id) { mutableStateOf<PanelReport?>(null) }
    var probing by remember(config.id) { mutableStateOf(false) }

    CollapsibleGroup(t("pnl_checks"), expanded, { expanded = !expanded }) {
        LabeledDropdown(t("pnl_kind"), listOf("3x-ui", "pasarguard"), kind) { kind = it }
        OutlinedTextField(
            url, { url = it },
            label = { Text(t("pnl_url")) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = monoFont()),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            user, { user = it },
            label = { Text(t("pnl_user")) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            pass, { pass = it },
            label = { Text(t("pnl_pass")) },
            singleLine = true,
            visualTransformation = if (showPass) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(
                        if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        report?.checks?.forEachIndexed { i, check -> ProbeCheckRow(check, i, report) }

        BounceOutlinedButton(
            onClick = {
                store.savePanel(config.id, kind, url, user, pass)
                probing = true
                scope.launch {
                    report = runCatching {
                        PanelProbe.run(
                            if (kind == "pasarguard") PanelKind.PASARGUARD else PanelKind.XUI,
                            url, user, pass, config
                        )
                    }.getOrNull()
                    probing = false
                }
            },
            enabled = !probing && url.isNotBlank() && user.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (probing) t("pnl_running") else t("pnl_run"))
        }
    }
}

@Composable
private fun ServerChecksGroup(config: ProxyConfig) {
    val t = stringsFn()
    val context = LocalContext.current
    val sshStore = remember { SshStore.get(context) }
    val hosts by sshStore.hosts.collectAsState()
    val statuses by SshManager.status.collectAsState()
    val scope = rememberCoroutineScope()

    var hostId by remember(config.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(config.id, hosts) {
        if (hosts.isEmpty()) {
            hostId = null
            return@LaunchedEffect
        }
        val saved = sshStore.linkedHostId(config.id)?.takeIf { id -> hosts.any { it.id == id } }
        hostId = saved ?: ServerProbe.bestMatch(sshStore, config)?.id
    }

    val host = hosts.firstOrNull { it.id == hostId }
    val connected = host != null && statuses[host.id] is SshStatus.Up

    var report by remember(hostId) { mutableStateOf<ServerReport?>(null) }
    var probing by remember(hostId) { mutableStateOf(false) }

    var expanded by remember(config.id) { mutableStateOf(false) }
    if (hosts.isEmpty() || host == null) return

    CollapsibleGroup(t("srv_checks"), expanded, { expanded = !expanded }) {
        if (hosts.size > 1) {
            LabeledDropdown(
                label = t("srv_host"),
                options = hosts.map { it.title },
                selected = host.title,
                onSelect = { title ->
                    hosts.firstOrNull { it.title == title }?.let {
                        hostId = it.id
                        sshStore.link(config.id, it.id)
                    }
                }
            )
        } else {
            Text(
                t("srv_via").format(host.title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        report?.checks?.forEachIndexed { i, check -> ProbeCheckRow(check, i, report) }

        BounceOutlinedButton(
            onClick = {
                probing = true
                scope.launch {
                    if (!connected) SshManager.connect(host)
                    report = if (SshManager.isUp(host.id))
                        runCatching { ServerProbe.run(host.id, config) }.getOrNull() else null
                    probing = false
                }
            },
            enabled = !probing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (probing) t("srv_running") else t("srv_run"))
        }
    }
}

@Composable
private fun DebugInfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(118.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            monoText(value),
            style = MaterialTheme.typography.labelSmall,
            color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Every grouped control in the app comes through here, so this one change
    // puts all of them on the slab skin.
    val c = ghajarColors
    Slab {
        if (title != null) {
            Text(
                mixedText(title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = c.textSecondary
            )
        }
        content()
    }
}

@Composable
private fun ConnectionSettingsScreen(
    store: ConfigStore,
    onOpenPerApp: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val fragment by store.fragment.collectAsState()
    val fragmentPackets by store.fragmentPackets.collectAsState()
    val fragmentLength by store.fragmentLength.collectAsState()
    val fragmentInterval by store.fragmentInterval.collectAsState()
    val rotateMinutes by store.rotateMinutes.collectAsState()
    val zeptunTunnel by store.zeptunTunnel.collectAsState()
    val zeptunDns by store.zeptunDns.collectAsState()
    val zeptunDnsUpstream by store.zeptunDnsUpstream.collectAsState()
    val zeptunProfile by store.zeptunProfile.collectAsState()
    val youtubeDirect by store.youtubeDirect.collectAsState()
    val noiseSpec by store.noiseSpec.collectAsState()
    val splitRouting by store.splitRouting.collectAsState()
    val sniffing by store.sniffing.collectAsState()
    val sniffTypes by store.sniffTypes.collectAsState()
    val killSwitch by store.killSwitch.collectAsState()
    val mux by store.mux.collectAsState()
    val muxConcurrency by store.muxConcurrency.collectAsState()
    val perAppMode by store.perAppMode.collectAsState()
    val perAppList by store.perAppList.collectAsState()
    val mixedPort by store.mixedPort.collectAsState()
    val fakeDns by store.fakeDns.collectAsState()
    val encryptedDns by store.encryptedDns.collectAsState()
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val openVpnDefaults = remember(settingsContext) { GhajarOpenVpnSettings.read(settingsContext) }
    var ovpnReconnectOnNetworkChange by remember { mutableStateOf(openVpnDefaults.reconnectOnNetworkChange) }
    var ovpnUseSystemProxy by remember { mutableStateOf(openVpnDefaults.useSystemProxy) }
    var ovpnPauseOnScreenOff by remember { mutableStateOf(openVpnDefaults.pauseOnScreenOff) }
    var ovpnEncryptProfiles by remember { mutableStateOf(openVpnDefaults.encryptProfiles) }
    val netRuleDefaults = remember(settingsContext) { NetworkRules.read(settingsContext) }
    var netRulesOn by remember { mutableStateOf(netRuleDefaults.enabled) }
    var netRuleWifi by remember { mutableStateOf(netRuleDefaults.wifi) }
    var netRuleCellular by remember { mutableStateOf(netRuleDefaults.cellular) }
    var netRuleOther by remember { mutableStateOf(netRuleDefaults.other) }
    var netRuleRecover by remember { mutableStateOf(netRuleDefaults.recoverOnChange) }
    var showLeakGuard by remember { mutableStateOf(false) }

    if (showLeakGuard) {
        LeakGuardDialog(store = store, onDismiss = { showLeakGuard = false })
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsGroup(t("routing")) {
            SettingRow(
                title = t("fakedns_title"),
                subtitle = t("fakedns_sub"),
                checked = fakeDns,
                onCheckedChange = { store.setFakeDns(it) },
                icon = Icons.Filled.Dns
            )
            SettingRow(
                title = t("encdns_title"),
                subtitle = t("encdns_sub"),
                checked = encryptedDns,
                onCheckedChange = { store.setEncryptedDns(it) },
                icon = Icons.Filled.Lock
            )
            SettingRow(
                title = t("split_title"),
                subtitle = t("split_sub"),
                checked = splitRouting,
                onCheckedChange = { store.setSplitRouting(it) },
                icon = Icons.Filled.CallSplit
            )
            SettingRow(
                title = t("fragment_title"),
                subtitle = t("fragment_sub"),
                checked = fragment,
                onCheckedChange = { store.setFragment(it) },
                icon = Icons.Filled.Shuffle
            )
            // The fragmentor's own parameters. They were already in the store
            // with setters and a backup entry, but nothing showed them and
            // nothing passed them to the core - so the fragmentor always ran
            // on the built-in "tlshello / 10-20 / 10-20" whatever was saved.
            // Both halves are fixed now: these are wired through every connect
            // path, and the presets are the ones worth having.
            AnimatedVisibility(visible = fragment) {
                Column(verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                    Text(
                        t("fragment_tune"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = ghajarColors.textSecondary
                    )
                    val presets = remember {
                        listOf(
                            Triple("tlshello", "10-20", "10-20"),
                            Triple("tlshello", "40-60", "30-50"),
                            Triple("1-3", "10-20", "10-20"),
                            Triple("1-5", "1-3", "1-3")
                        )
                    }
                    val labels = listOf(
                        t("fragment_preset_default"),
                        t("fragment_preset_wide"),
                        t("fragment_preset_first"),
                        t("fragment_preset_tiny")
                    )
                    presets.forEachIndexed { index, preset ->
                        val (packets, length, interval) = preset
                        val active = fragmentPackets == packets &&
                            fragmentLength == length && fragmentInterval == interval
                        SlabRow(
                            title = labels[index],
                            subtitle = "packets $packets · length $length · interval $interval",
                            icon = if (active) Icons.Filled.Check else Icons.Filled.Shuffle,
                            accent = if (active) ghajarColors.primary else ghajarColors.textMuted,
                            onClick = {
                                store.setFragmentPackets(packets)
                                store.setFragmentLength(length)
                                store.setFragmentInterval(interval)
                            }
                        )
                    }
                    Text(
                        t("fragment_tune_note"),
                        style = MaterialTheme.typography.labelSmall,
                        color = ghajarColors.textMuted
                    )
                }
            }
            // The zeptun tun engine. The row reports what the library on
            // this device actually says about itself rather than whether the
            // build was supposed to include it - a version string here is
            // proof it loaded, and its absence is proof it did not.
            val zeptunVersion = remember { ZeptunEngine.version() }
            SettingRow(
                title = t("zeptun_title"),
                subtitle = when {
                    zeptunVersion == null -> t("zeptun_absent")
                    zeptunTunnel -> t("zeptun_on").format(zeptunVersion)
                    else -> t("zeptun_off").format(zeptunVersion)
                },
                checked = zeptunTunnel && zeptunVersion != null,
                enabled = zeptunVersion != null,
                onCheckedChange = { store.setZeptunTunnel(it) },
                icon = Icons.Filled.Dns
            )
            Text(
                t("zeptun_note"),
                style = MaterialTheme.typography.labelSmall,
                color = ghajarColors.textMuted
            )
            // The engine's own options, shown only while it is actually in
            // charge of a tunnel. Offering DNS modes for an engine that is
            // switched off, or not in the build, would be four rows that
            // cannot do anything.
            AnimatedVisibility(visible = zeptunTunnel && zeptunVersion != null) {
                Column(verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                    SlabRow(
                        title = t("zeptun_dns_title"),
                        subtitle = when (zeptunDns) {
                            ZeptunEngine.DnsMode.FORWARD -> t("zeptun_dns_forward")
                            ZeptunEngine.DnsMode.HIJACK ->
                                if (zeptunDnsUpstream.isBlank()) t("zeptun_dns_hijack_unset")
                                else t("zeptun_dns_hijack").format(zeptunDnsUpstream)
                            ZeptunEngine.DnsMode.FAKE_IP -> t("zeptun_dns_fake")
                        },
                        icon = Icons.Filled.Dns,
                        accent = if (zeptunDns == ZeptunEngine.DnsMode.FORWARD)
                            ghajarColors.textMuted else ghajarColors.primary,
                        chevron = true,
                        onClick = {
                            val modes = ZeptunEngine.DnsMode.entries
                            store.setZeptunDns(modes[(modes.indexOf(zeptunDns) + 1) % modes.size])
                        }
                    )
                    // Only asked for in the one mode that reads it: hijack
                    // does nothing at all without an upstream.
                    AnimatedVisibility(visible = zeptunDns == ZeptunEngine.DnsMode.HIJACK) {
                        SkinField(
                            value = zeptunDnsUpstream,
                            onValueChange = { store.setZeptunDnsUpstream(it) },
                            label = t("zeptun_dns_upstream"),
                            placeholder = "1.1.1.1"
                        )
                    }
                    SlabRow(
                        title = t("zeptun_profile_title"),
                        subtitle = when (zeptunProfile) {
                            ZeptunEngine.Profile.BALANCED -> t("zeptun_profile_balanced")
                            ZeptunEngine.Profile.THROUGHPUT -> t("zeptun_profile_throughput")
                            ZeptunEngine.Profile.BATTERY -> t("zeptun_profile_battery")
                        },
                        icon = Icons.Filled.Speed,
                        accent = if (zeptunProfile == ZeptunEngine.Profile.BALANCED)
                            ghajarColors.textMuted else ghajarColors.primary,
                        chevron = true,
                        onClick = {
                            val all = ZeptunEngine.Profile.entries
                            store.setZeptunProfile(all[(all.indexOf(zeptunProfile) + 1) % all.size])
                        }
                    )
                    Text(
                        t("zeptun_dns_note"),
                        style = MaterialTheme.typography.labelSmall,
                        color = ghajarColors.textMuted
                    )
                }
            }
            // Noise packets on the direct outbound, from MahsaNG. Off by
            // default: a server that will not tolerate an unexpected leading
            // packet fails rather than degrades.
            SlabRow(
                title = t("noise_title"),
                subtitle = when (noiseSpec) {
                    "", "off" -> t("noise_off")
                    "light" -> t("noise_light")
                    "standard" -> t("noise_standard")
                    "aggressive" -> t("noise_aggressive")
                    "quic" -> t("noise_quic")
                    else -> t("noise_custom")
                },
                icon = Icons.Filled.GraphicEq,
                accent = if (noiseSpec.isBlank() || noiseSpec == "off")
                    ghajarColors.textMuted else ghajarColors.primary,
                chevron = true,
                onClick = {
                    // Cycles the presets only. A hand-written spec is kept as
                    // it is until the user taps, which then moves to "off"
                    // rather than silently rewriting what they typed.
                    val steps = listOf("off", "light", "standard", "aggressive", "quic")
                    val here = steps.indexOf(noiseSpec.ifBlank { "off" })
                    store.setNoiseSpec(steps[(here.coerceAtLeast(0) + 1) % steps.size])
                }
            )
            // Youtube Direct, from MahsaNG. A bandwidth decision, not a
            // censorship one - which is why the note says what it costs.
            SettingRow(
                title = t("youtube_direct_title"),
                subtitle = if (youtubeDirect) t("youtube_direct_on") else t("youtube_direct_off"),
                checked = youtubeDirect,
                onCheckedChange = { store.setYoutubeDirect(it) },
                icon = Icons.Filled.PlayArrow
            )
            // Rotating configs: off unless an interval is set, and it only
            // moves between servers that are already in the list.
            SlabRow(
                title = t("rotate_title"),
                subtitle = if (rotateMinutes <= 0) t("rotate_off")
                else t("rotate_every").format(localizeDigits("$rotateMinutes", lang)),
                icon = Icons.Filled.Autorenew,
                accent = if (rotateMinutes > 0) ghajarColors.primary else ghajarColors.textMuted,
                chevron = true,
                onClick = {
                    // 0 -> 15 -> 30 -> 60 -> 120 -> off again.
                    val steps = listOf(0, 15, 30, 60, 120)
                    val next = steps[(steps.indexOf(rotateMinutes).coerceAtLeast(0) + 1) % steps.size]
                    store.setRotateMinutes(next)
                }
            )
            SettingRow(
                title = t("sniffing_title"),
                subtitle = t("sniffing_sub"),
                checked = sniffing,
                onCheckedChange = { store.setSniffing(it) },
                icon = Icons.Filled.TravelExplore
            )
            AnimatedVisibility(visible = sniffing) {
                Column {
                    Text(
                        t("sniffing_type"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    SniffTypeSelector(
                        selected = sniffTypes,
                        onToggle = { store.toggleSniffType(it) }
                    )
                }
            }

            SettingRow(
                title = t("mux_title"),
                subtitle = t("mux_sub"),
                checked = mux,
                onCheckedChange = { store.setMux(it) },
                icon = Icons.Filled.Layers
            )
            AnimatedVisibility(visible = mux) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t("mux_concurrency"), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { store.setMuxConcurrency(muxConcurrency - 1) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "-")
                    }
                    Text("$muxConcurrency", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                    IconButton(onClick = { store.setMuxConcurrency(muxConcurrency + 1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "+")
                    }
                }
            }

            SettingRow(
                title = t("kill_switch_title"),
                subtitle = t("kill_switch_sub"),
                checked = killSwitch,
                onCheckedChange = { store.setKillSwitch(it) },
                icon = Icons.Filled.Block
            )
            // The toggles above say what this app was asked to do. This says
            // what would actually happen to traffic if the tunnel dropped,
            // including the two halves of it that only Android can grant.
            SlabRow(
                title = t("leak_title"),
                subtitle = t("leak_sub"),
                icon = Icons.Filled.Shield,
                chevron = true,
                onClick = { showLeakGuard = true }
            )
            AnimatedVisibility(visible = killSwitch) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            runCatching {
                                settingsContext.startActivity(
                                    Intent("android.net.vpn.SETTINGS")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure {
                                runCatching {
                                    settingsContext.startActivity(
                                        Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("always_on_title"), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(t("always_on_sub"), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }

        }

        SettingsGroup(t("advanced")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("mixed_port"), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        t("mixed_port_sub"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                val focus = LocalFocusManager.current
                var portText by remember(mixedPort) { mutableStateOf(mixedPort.toString()) }
                BasicTextField(
                    value = portText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(5)
                        portText = digits
                        digits.toIntOrNull()?.let { if (it in 1024..65535) store.setMixedPort(it) }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = monoFont(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.width(78.dp).height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 8.dp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
                    }
                )
            }
        }

        SettingsGroup(t("netrule_title")) {
            Text(
                t("netrule_sub"),
                style = MaterialTheme.typography.bodySmall,
                color = ghajarColors.textSecondary
            )
            SettingRow(
                title = t("netrule_enabled"),
                subtitle = t("netrule_enabled_sub"),
                checked = netRulesOn,
                onCheckedChange = { value ->
                    netRulesOn = value
                    NetworkRules.setEnabled(settingsContext, value)
                }
            )
            // Three states, so a tap cycles rather than opening a menu for what
            // is effectively one of three words.
            NetRuleRow(t("netrule_wifi"), netRuleWifi, enabled = netRulesOn) { next ->
                netRuleWifi = next
                NetworkRules.setAction(settingsContext, NetKind.WIFI, next)
            }
            NetRuleRow(t("netrule_cellular"), netRuleCellular, enabled = netRulesOn) { next ->
                netRuleCellular = next
                NetworkRules.setAction(settingsContext, NetKind.CELLULAR, next)
            }
            NetRuleRow(t("netrule_other"), netRuleOther, enabled = netRulesOn) { next ->
                netRuleOther = next
                NetworkRules.setAction(settingsContext, NetKind.OTHER, next)
            }
            SettingRow(
                title = t("netrule_recover"),
                subtitle = t("netrule_recover_sub"),
                checked = netRuleRecover,
                onCheckedChange = { value ->
                    netRuleRecover = value
                    NetworkRules.setRecoverOnChange(settingsContext, value)
                },
                enabled = netRulesOn
            )
            Text(
                t("netrule_note"),
                style = MaterialTheme.typography.bodySmall,
                color = ghajarColors.textMuted
            )
        }

        SettingsGroup("OpenVPN") {
            Text(
                if (lang == Lang.FA)
                    "این گزینه‌ها مستقیماً به موتور رسمی OpenVPN for Android وصل هستند."
                else
                    "These options are wired directly to the OpenVPN for Android engine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingRow(
                title = if (lang == Lang.FA) "اتصال مجدد با تغییر شبکه" else "Reconnect on network change",
                subtitle = if (lang == Lang.FA)
                    "با جابه‌جایی بین Wi‑Fi و دیتای موبایل، OpenVPN اتصال را دوباره برقرار می‌کند."
                else "Reconnect OpenVPN when Android switches between Wi-Fi and mobile data.",
                checked = ovpnReconnectOnNetworkChange,
                onCheckedChange = { value ->
                    ovpnReconnectOnNetworkChange = value
                    GhajarOpenVpnSettings.setReconnectOnNetworkChange(settingsContext, value)
                }
            )
            SettingRow(
                title = if (lang == Lang.FA) "استفاده از پروکسی سیستم" else "Use system proxy",
                subtitle = if (lang == Lang.FA)
                    "تنظیمات HTTP Proxy اندروید را هنگام ساخت کانفیگ OpenVPN اعمال می‌کند."
                else "Honor Android's HTTP proxy when OpenVPN builds the runtime config.",
                checked = ovpnUseSystemProxy,
                onCheckedChange = { value ->
                    ovpnUseSystemProxy = value
                    GhajarOpenVpnSettings.setUseSystemProxy(settingsContext, value)
                }
            )
            SettingRow(
                title = if (lang == Lang.FA) "مکث هنگام خاموش بودن صفحه" else "Pause when screen is off",
                subtitle = if (lang == Lang.FA)
                    "برای صرفه‌جویی باتری؛ خاموش باشد تا اتصال پایدارتر بماند."
                else "Battery-saving mode. Keep this off for the most stable connection.",
                checked = ovpnPauseOnScreenOff,
                onCheckedChange = { value ->
                    ovpnPauseOnScreenOff = value
                    GhajarOpenVpnSettings.setPauseOnScreenOff(settingsContext, value)
                }
            )
            SettingRow(
                title = if (lang == Lang.FA) "رمزگذاری پروفایل‌های OpenVPN" else "Encrypt OpenVPN profiles",
                subtitle = if (lang == Lang.FA)
                    "در صورت پشتیبانی اندروید، اطلاعات پروفایل‌های ذخیره‌شده محافظت می‌شوند."
                else "Prefer encrypted profile storage when Android supports it.",
                checked = ovpnEncryptProfiles,
                onCheckedChange = { value ->
                    ovpnEncryptProfiles = value
                    GhajarOpenVpnSettings.setEncryptProfiles(settingsContext, value)
                }
            )
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    runCatching {
                        settingsContext.startActivity(
                            Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (lang == Lang.FA) "Always-on VPN و قطع اینترنت بدون VPN" else "Always-on VPN & block without VPN",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (lang == Lang.FA) "باز کردن تنظیمات VPN خود اندروید" else "Open Android VPN settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }

        SettingsHubCard(
            icon = Icons.Filled.Apps,
            title = t("per_app"),
            subtitle = perAppSummary(perAppMode, perAppList.size, lang),
            onClick = onOpenPerApp
        )

        SettingsHubCard(
            icon = Icons.AutoMirrored.Filled.Article,
            title = t("xray_logs"),
            subtitle = t("xray_logs_sub"),
            onClick = onOpenLogs
        )

        Text(
            t("takes_effect"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreferencesScreen(
    store: ConfigStore,
    onOpenTheme: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val curLang by store.lang.collectAsState()
    var langOpen by remember { mutableStateOf(false) }
    val autoRefreshHours by store.autoRefreshHours.collectAsState()
    var autoRefreshOpen by remember { mutableStateOf(false) }
    val coreLogLevel by store.coreLogLevel.collectAsState()
    var coreLogOpen by remember { mutableStateOf(false) }

    fun refreshLabel(h: Int): String =
        if (h <= 0) t("auto_refresh_off")
        else if (h == 1) t("every_hour").format(localizeDigits("$h", lang))
        else t("every_hours").format(localizeDigits("$h", lang))

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsHubCard(
            icon = Icons.Filled.Palette,
            title = t("theme_settings"),
            subtitle = t("theme_settings_sub"),
            onClick = onOpenTheme
        )
        SettingsHubCard(
            icon = Icons.Filled.Notifications,
            title = t("notif_settings"),
            subtitle = t("notif_settings_sub"),
            onClick = onOpenNotifications
        )

        SettingsGroup {
            Text(t("language"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Box {
                OutlinedButton(
                    onClick = { langOpen = true },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (curLang == Lang.FA) "فارسی" else "English",
                        fontFamily = if (curLang == Lang.FA) VazirFont else LexendFont,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = langOpen,
                    onDismissRequest = { langOpen = false },
                    offset = DpOffset(0.dp, 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = ghajarColors.surface,
                    border = BorderStroke(1.dp, ghajarColors.border)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "English",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = LexendFont
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(40.dp),
                        onClick = { store.setLang(Lang.EN); langOpen = false }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "فارسی",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = VazirFont
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(40.dp),
                        onClick = { store.setLang(Lang.FA); langOpen = false }
                    )
                }
            }

            Text(t("auto_refresh"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Box {
                OutlinedButton(
                    onClick = { autoRefreshOpen = true },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(refreshLabel(autoRefreshHours), modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = autoRefreshOpen,
                    onDismissRequest = { autoRefreshOpen = false },
                    offset = DpOffset(0.dp, 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = ghajarColors.surface,
                    border = BorderStroke(1.dp, ghajarColors.border)
                ) {
                    listOf(0, 1, 6, 12, 24).forEach { h ->
                        DropdownMenuItem(
                            text = { Text(refreshLabel(h), style = MaterialTheme.typography.bodyMedium) },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.height(40.dp),
                            onClick = { store.setAutoRefreshHours(h); autoRefreshOpen = false }
                        )
                    }
                }
            }

            Text(t("core_log_level"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Box {
                OutlinedButton(
                    onClick = { coreLogOpen = true },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        coreLogLevel,
                        fontFamily = LexendFont,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = coreLogOpen,
                    onDismissRequest = { coreLogOpen = false },
                    offset = DpOffset(0.dp, 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = ghajarColors.surface,
                    border = BorderStroke(1.dp, ghajarColors.border)
                ) {
                    listOf("none", "error", "warning", "info", "debug").forEach { level ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    level,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = LexendFont
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.height(40.dp),
                            onClick = { store.setCoreLogLevel(level); coreLogOpen = false }
                        )
                    }
                }
            }
            Text(
                t("core_log_level_sub"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val TelegramIcon: ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).run {
        addPath(
            pathData = PathParser().parsePathString(
                "M9.78,18.65L10.06,14.42L17.74,7.5C18.08,7.19 17.67,7.04 17.22,7.31L7.74,13.3L3.64,12C2.76,11.75 2.75,11.14 3.84,10.7L19.81,4.54C20.54,4.21 21.24,4.72 20.96,5.84L18.24,18.65C18.05,19.55 17.5,19.77 16.74,19.35L12.6,16.3L10.61,18.23C10.38,18.46 10.19,18.65 9.78,18.65Z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
        build()
    }

/**
 * Notification settings as a settings page rather than a block inside the
 * shop's third section, where they were effectively unfindable. The controls
 * themselves are unchanged - same permission request, same channel entry
 * points, same per-category toggles.
 */
@Composable
private fun NotificationSettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = GhajarSpacing.lg, vertical = GhajarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        SettingsGroup { GhajarNotificationSettings() }
    }
}

@Composable
private fun ThemeSettingsScreen(store: ConfigStore, modifier: Modifier = Modifier) {
    val t = stringsFn()
    val selected by store.uiTheme.collectAsState()
    val c = ghajarColors

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(GhajarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        Text(t("appearance_title"), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text(
            t("appearance_sub"),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
        Spacer(Modifier.height(GhajarSpacing.xs))

        GhajarPalettes.forEach { palette ->
            val (label, sub) = when (palette.id) {
                GhajarThemeId.PREMIUM_GREEN_DARK -> t("theme_green_dark") to t("theme_green_dark_sub")
                GhajarThemeId.PREMIUM_GREEN_LIGHT -> t("theme_green_light") to t("theme_green_light_sub")
                GhajarThemeId.MIDNIGHT_BLUE -> t("theme_midnight") to t("theme_midnight_sub")
                GhajarThemeId.GRAPHITE_GOLD -> t("theme_graphite") to t("theme_graphite_sub")
                GhajarThemeId.SYSTEM -> t("theme_system") to t("theme_system_sub")
            }
            ThemeChoiceRow(
                label = label,
                subtitle = sub,
                preview = palette,
                selected = selected == palette.id,
                onClick = { store.setUiTheme(palette.id) }
            )
        }

        ThemeChoiceRow(
            label = t("theme_system"),
            subtitle = t("theme_system_sub"),
            // Shows whichever Premium Green palette the phone would pick.
            preview = ghajarPaletteFor(GhajarThemeId.SYSTEM, isSystemInDarkTheme()),
            selected = selected == GhajarThemeId.SYSTEM,
            onClick = { store.setUiTheme(GhajarThemeId.SYSTEM) }
        )

        Rail(t("appearance_more"))
        Slab {
            val reduceMotion by store.reduceMotion.collectAsState()
            val useDynamicAccent by store.dynamicAccent.collectAsState()
            val density by store.listDensity.collectAsState()

            SlabRow(
                title = t("reduce_motion"),
                subtitle = t("reduce_motion_sub"),
                icon = Icons.Filled.TimerOff,
                onClick = { store.setReduceMotion(!reduceMotion) },
                trailing = {
                    SkinSwitch(
                        checked = reduceMotion,
                        onCheckedChange = { store.setReduceMotion(it) }
                    )
                }
            )
            SlabDivider()
            // Disabled rather than hidden below Android 12: a setting that
            // appears on one phone and not another reads as a missing feature,
            // and the subtitle can say why it is unavailable here.
            SlabRow(
                title = t("dynamic_accent"),
                subtitle = if (dynamicAccentSupported) t("dynamic_accent_sub")
                else t("dynamic_accent_unsupported"),
                icon = Icons.Filled.Palette,
                enabled = dynamicAccentSupported,
                onClick = if (dynamicAccentSupported) {
                    { store.setDynamicAccent(!useDynamicAccent) }
                } else null,
                trailing = {
                    SkinSwitch(
                        checked = useDynamicAccent && dynamicAccentSupported,
                        onCheckedChange = if (dynamicAccentSupported) {
                            { store.setDynamicAccent(it) }
                        } else null,
                        enabled = dynamicAccentSupported
                    )
                }
            )
            SlabDivider()
            SlabRow(
                title = t("list_density"),
                subtitle = when (density) {
                    ListDensity.ONE -> t("list_density_one_sub")
                    ListDensity.TWO -> t("list_density_two_sub")
                },
                icon = Icons.Filled.Apps,
                value = when (density) {
                    ListDensity.ONE -> t("list_density_one")
                    ListDensity.TWO -> t("list_density_two")
                },
                onClick = {
                    store.setListDensity(
                        if (density == ListDensity.ONE) ListDensity.TWO else ListDensity.ONE
                    )
                }
            )
        }
    }
}

/**
 * A theme row previews the theme with its own colours, so the choice is made
 * by looking rather than by reading a name.
 */
@Composable
private fun ThemeChoiceRow(
    label: String,
    subtitle: String,
    preview: GhajarPalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = ghajarColors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.md))
            .background(if (selected) c.primary.copy(alpha = 0.14f) else c.secondaryCard)
            .clickable { onClick() }
            .padding(GhajarSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        // Selection is a leading accent bar plus a tinted fill, the same way
        // the connected server row is marked. No outlines anywhere in the skin.
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(GhajarRadius.pill))
                .background(if (selected) c.primary else Color.Transparent)
        )
        ThemeSwatch(preview)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                mixedText(label),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                maxLines = 2
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Background, card and the brand tone of one theme, drawn in that theme. */
@Composable
private fun ThemeSwatch(palette: GhajarPalette) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(GhajarRadius.sm))
            .background(palette.background)
            .border(1.dp, palette.border, RoundedCornerShape(GhajarRadius.sm)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.card)
            )
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.primary)
            )
            Box(
                Modifier
                    .fillMaxWidth(0.45f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.highlight)
            )
        }
    }
}

@Composable
private fun AboutScreen(modifier: Modifier = Modifier) {
    val t = stringsFn()
    val lang = LocalLang.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "\u2014"
    }
    val xrayVersion = remember { xrayCoreVersion() }
    var privacyOpen by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val logoRes = R.drawable.ghajar_wordmark
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = 2.dp)) {
            Image(
                painter = painterResource(logoRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.78f).height(104.dp)
            )
        }

        Text(
            mixedText(t("about_tagline")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Row(
            Modifier.padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AboutChip(t("app_version"), appVersion)
            AboutChip(t("xray_version"), xrayVersion)
        }

        AboutCard(
            icon = Icons.Filled.Hub,
            title = t("source_code"),
            value = BrandConfig.GITHUB_URL.removePrefix("https://"),
            onClick = { runCatching { uriHandler.openUri(BrandConfig.GITHUB_URL) } }
        )

        AboutCard(
            icon = Icons.Filled.Refresh,
            title = t("check_updates"),
            value = updateStatus,
            busy = checking,
            onClick = {
                if (checking) return@AboutCard
                val url = updateUrl
                if (url != null) {
                    runCatching { uriHandler.openUri(url) }
                } else {
                    checking = true
                    updateStatus = t("checking_updates")
                    scope.launch {
                        when (val r = UpdateChecker.check(appVersion)) {
                            is UpdateChecker.Result.Available -> {
                                updateStatus = t("update_available").format(r.version)
                                updateUrl = r.url
                                GhajarUpdateFlow.offer(r)
                            }
                            UpdateChecker.Result.UpToDate -> updateStatus = t("up_to_date")
                            UpdateChecker.Result.Failed -> updateStatus = t("update_failed")
                        }
                        checking = false
                    }
                }
            }
        )

        Card(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { privacyOpen = !privacyOpen },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AboutIconTile(Icons.Filled.Lock)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        mixedText(t("privacy_policy")),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    val turn by animateFloatAsState(
                        targetValue = if (privacyOpen) 180f else 0f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "privacyChevron"
                    )
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = turn }
                    )
                }
                AnimatedVisibility(
                    visible = privacyOpen,
                    enter = fadeIn(tween(260)) + expandVertically(tween(300, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(240, easing = FastOutSlowInEasing))
                ) {
                    Text(
                        mixedText(if (lang == Lang.FA) PRIVACY_FA else PRIVACY_EN),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        }

        AboutCard(
            iconVector = TelegramIcon,
            title = t("telegram_support"),
            value = "@Ghajarvpn",
            onClick = { runCatching { uriHandler.openUri(BrandConfig.TELEGRAM_CHANNEL_URL) } }
        )

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AboutIconTile(icon: ImageVector) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AboutChip(label: String, value: String) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            mixedText(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.width(7.dp))
        Text(
            mixedText(localizeDigits(value, LocalLang.current)),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = if (LocalLang.current == Lang.FA) VazirFont else LexendFont,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun AboutCard(
    title: String,
    value: String?,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconVector: ImageVector? = null,
    busy: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AboutIconTile(icon ?: iconVector ?: Icons.Filled.Info)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(mixedText(title), style = MaterialTheme.typography.bodyLarge)
                Crossfade(targetState = value, animationSpec = tween(300), label = "aboutValue") { v ->
                    if (!v.isNullOrBlank()) {
                        Text(
                            if (v.contains("github.com/") || v.startsWith("@")) AnnotatedString("\u2066$v\u2069")
                            else mixedText(localizeDigits(v, LocalLang.current)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun xrayCoreVersion(): String = runCatching {
    Class.forName("gozarcore.Gozarcore")
        .getMethod("xrayVersion")
        .invoke(null) as String
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "—"

private val PRIVACY_EN = """
Ghajarvpn privacy information

Account and store: If you link your account, the app sends its access token to the Ghajar service to load your account, subscriptions, orders and notifications. Purchase requests include the options you enter. If you upload a payment receipt, the selected image is sent to the service and may contain personal or banking information.

On your device: The app stores configurations and settings in its private storage. The account access token is encrypted using Android Keystore. Clearing app data removes local records; it does not delete records held by the service.

Network requests: Account sync, store content and the live welcome image contact the Ghajar service. Network-status features may contact third parties such as ipwho.is and ipify.org. These services can see your connection's IP address.

VPN and payments: The selected VPN server handles your tunnel traffic. A selected payment gateway handles the payment in its own page. This Android client cannot determine or guarantee the logging and retention practices of those services.

Permissions: VPN access starts the tunnel you select. Camera access is used for scanning codes; notification access is used for connection and service alerts.

Questions about data held by the service or deletion requests: contact @Ghajarvpn.
""".trimIndent()

private val PRIVACY_FA = """
اطلاعات حریم خصوصی قاجار وی پی ان

حساب و فروشگاه: اگر حسابتان را متصل کنید، برنامه توکن دسترسی را برای دریافت حساب، اشتراک‌ها، سفارش‌ها و اعلان‌ها به سرویس قاجار می‌فرستد. درخواست خرید شامل گزینه‌هایی است که وارد می‌کنید. اگر رسید پرداخت بارگذاری کنید، تصویر انتخاب‌شده به سرویس ارسال می‌شود و ممکن است اطلاعات شخصی یا بانکی داشته باشد.

روی گوشی: برنامه کانفیگ‌ها و تنظیمات را در حافظهٔ خصوصی خود نگه می‌دارد. توکن دسترسی حساب با Android Keystore رمزگذاری می‌شود. پاک‌کردن دادهٔ برنامه، اطلاعات محلی را حذف می‌کند؛ اطلاعات نگهداری‌شده در سرویس با این کار حذف نمی‌شوند.

درخواست‌های شبکه: همگام‌سازی حساب، محتوای فروشگاه و تصویر زندهٔ ورود به سرویس قاجار متصل می‌شوند. امکانات نمایش وضعیت شبکه ممکن است با سرویس‌هایی مانند ipwho.is و ipify.org تماس بگیرند. این سرویس‌ها نشانی IP اتصال شما را می‌بینند.

وی‌پی‌ان و پرداخت: سرور وی‌پی‌ان انتخاب‌شده ترافیک تونل شما را مدیریت می‌کند. درگاه انتخاب‌شده پرداخت را در صفحهٔ خودش انجام می‌دهد. این برنامهٔ اندروید نمی‌تواند شیوهٔ ثبت لاگ و مدت نگهداری اطلاعات در آن سرویس‌ها را مشخص یا تضمین کند.

دسترسی‌ها: دسترسی وی‌پی‌ان برای شروع تونل انتخابی، دوربین برای اسکن کد و اعلان برای نمایش وضعیت اتصال و هشدار سرویس استفاده می‌شود.

برای پرسش دربارهٔ اطلاعات نگهداری‌شده در سرویس یا درخواست حذف آن‌ها، با @Ghajarvpn تماس بگیرید.
""".trimIndent()

@Composable
private fun LogsScreen(store: ConfigStore, modifier: Modifier = Modifier) {
    val t = stringsFn()
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var logs by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }
    val configs by store.configs.collectAsState()

    fun load() {
        loading = true
        scope.launch {
            val secrets = configs.filter { it.locked }
            val out = withContext(Dispatchers.IO) { redactSecrets(readLogcat(), secrets) }
            logs = out
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(toast) { if (toast.isNotEmpty()) { delay(1800); toast = "" } }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            t("xray_logs"),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1
                        )
                        Crossfade(
                            targetState = if (toast.isNotEmpty()) toast else
                                localizeDigits("${logs.count { it == '\n' }.let { if (logs.isBlank()) 0 else it + 1 }}", lang) +
                                        " " + t("no_logs").takeIf { logs.isBlank() }.orEmpty(),
                            animationSpec = tween(260),
                            label = "logMeta"
                        ) { line ->
                            Text(
                                line.trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                    LogAction(Icons.Filled.Refresh, t("refresh")) { load() }
                    LogAction(Icons.Filled.ContentCopy, t("copy")) {
                        if (logs.isNotBlank()) {
                            clipboard.setText(AnnotatedString(logs))
                            toast = t("copied")
                        }
                    }
                    LogAction(Icons.Filled.Delete, t("clear")) {
                        runCatching { Runtime.getRuntime().exec(arrayOf("logcat", "-c")) }
                        logs = ""
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
                )
                if (logs.isBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (loading) t("testing") else t("no_logs"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    SelectionContainer {
                        Text(
                            logs,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFont,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .padding(start = 6.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .clickable {
                scope.launch {
                    scale.animateTo(0.88f, tween(90))
                    scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun redactSecrets(text: String, secrets: List<ProxyConfig>): String {
    if (secrets.isEmpty() || text.isEmpty()) return text
    var out = text
    val tokens = LinkedHashSet<String>()
    secrets.forEach { c ->
        if (c.address.isNotBlank()) {
            tokens.add("${c.address}:${c.port}")
            tokens.add(c.address)
        }
        listOf(c.uuid, c.password, c.publicKey, c.shortId, c.privateKey, c.sni, c.host, c.serviceName)
            .filter { it.length >= 4 }
            .forEach { tokens.add(it) }
    }
    tokens.sortedByDescending { it.length }.forEach { token ->
        out = out.replace(token, "[hidden]", ignoreCase = true)
    }
    return out
}

private fun readLogcat(): String = try {
    val proc = Runtime.getRuntime().exec(arrayOf(
        "logcat", "-d", "-v", "time",
        "XrayCore:V", "GoLog:V", "GozarVpnService:V",
        "Aether:V", "Tor:V", "GhajarIke:V",
        "charon:V", "CharonVpnService:V",
        "GhajarAuto:V", "GhajarQr:V", "GhajarHaptic:V", "GhajarGeo:V",
        "*:S"
    ))
    val lines = proc.inputStream.bufferedReader().readLines()
        .filterNot { it.startsWith("---------") }
    if (lines.isEmpty()) "" else lines.takeLast(400).joinToString("\n")
} catch (e: Exception) {
    e.message ?: "Unable to read logs"
}

@Composable
private fun StabilityTestScreen(store: ConfigStore, modifier: Modifier = Modifier) {
    val t = stringsFn()
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()

    val configs by store.configs.collectAsState()
    val selectedId by store.selectedId.collectAsState()
    val conn by VpnState.state.collectAsState()
    val activeId by VpnState.activeId.collectAsState()
    val target = if (conn == Connection.CONNECTED)
        configs.find { it.id == activeId } ?: configs.find { it.id == selectedId }
    else null

    var phase by remember { mutableStateOf(StabilityTest.Phase.DONE) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(store.lastTestJson()?.let { StabilityTest.fromJson(it) }) }
    var lastTestTime by remember { mutableStateOf(store.lastTestTime()) }
    var failed by remember { mutableStateOf(false) }
    var dlLive by remember { mutableStateOf(result?.downloadMbps ?: 0.0) }
    var ulLive by remember { mutableStateOf(result?.uploadMbps ?: 0.0) }
    var livePing by remember { mutableStateOf(0.0) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    fun start() {
        val cfg = target
        running = true; failed = false; result = null
        dlLive = 0.0; ulLive = 0.0; livePing = 0.0
        phase = StabilityTest.Phase.PING
        val testJson =
            if (cfg != null && cfg.protocol.trim().lowercase() != "ikev2")
                ConfigBuilder.buildForTest(cfg)
            else ConfigBuilder.buildForTestDirect()
        testJob = scope.launch {
            val r = StabilityTest.run(testJson) { ph, v ->
                phase = ph
                when (ph) {
                    StabilityTest.Phase.PING -> if (v > 0) livePing = v
                    StabilityTest.Phase.DOWNLOAD -> if (v > 0) dlLive = if (dlLive <= 0) v else dlLive * 0.6 + v * 0.4
                    StabilityTest.Phase.UPLOAD -> if (v > 0) ulLive = if (ulLive <= 0) v else ulLive * 0.6 + v * 0.4
                    else -> {}
                }
            }
            if (r != null) {
                dlLive = r.downloadMbps; ulLive = r.uploadMbps
                val now = System.currentTimeMillis()
                store.saveLastTest(StabilityTest.toJson(r), now)
                lastTestTime = now
            }
            result = r; failed = r == null; running = false
            phase = StabilityTest.Phase.DONE
            testJob = null
        }
    }

    fun cancel() {
        testJob?.cancel(); testJob = null
        running = false; failed = false
        phase = StabilityTest.Phase.DONE
        dlLive = result?.downloadMbps ?: 0.0
        ulLive = result?.uploadMbps ?: 0.0
        livePing = 0.0
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        AnimatedVisibility(
            visible = running,
            enter = fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(340, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(340, easing = FastOutSlowInEasing)) { -it / 2 },
            exit = fadeOut(tween(160, easing = FastOutSlowInEasing)) +
                    shrinkVertically(tween(280, easing = FastOutSlowInEasing))
        ) {
            val phaseIcon = when (phase) {
                StabilityTest.Phase.PING -> Icons.Filled.Schedule
                StabilityTest.Phase.DOWNLOAD -> Icons.Filled.ArrowDownward
                else -> Icons.Filled.ArrowUpward
            }
            val phaseLabel = when (phase) {
                StabilityTest.Phase.PING -> t("stab_ping")
                StabilityTest.Phase.DOWNLOAD -> t("download")
                StabilityTest.Phase.UPLOAD -> t("upload")
                else -> ""
            }
            val phaseValue = when (phase) {
                StabilityTest.Phase.PING ->
                    localizeDigits("${livePing.toInt()}", lang) + " " + t("unit_ms")
                StabilityTest.Phase.DOWNLOAD ->
                    localizeDigits(String.format(java.util.Locale.US, "%.1f", dlLive), lang) + " " + t("unit_mbps")
                StabilityTest.Phase.UPLOAD ->
                    localizeDigits(String.format(java.util.Locale.US, "%.1f", ulLive), lang) + " " + t("unit_mbps")
                else -> ""
            }
            val phaseTint = when (phase) {
                StabilityTest.Phase.PING -> AppCyan
                StabilityTest.Phase.DOWNLOAD -> ghajarColors.accentAlt
                else -> AppAqua
            }
            Crossfade(targetState = phase, animationSpec = tween(300), label = "phaseText") { ph ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(phaseTint.copy(alpha = 0.10f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ph == StabilityTest.Phase.PING) {
                        PingLine(color = phaseTint, size = 26.dp)
                    } else {
                        Icon(phaseIcon, contentDescription = null, tint = phaseTint,
                            modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        phaseLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        phaseValue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = phaseTint
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !running && result != null && lastTestTime > 0L,
            enter = fadeIn(tween(340, delayMillis = 120, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(340, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
        ) {
            Text(
                t("stab_last_test") + " " + formatTestTime(lastTestTime, lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().appearOnce(60),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpeedTile(
                    icon = Icons.Filled.ArrowDownward,
                    label = t("download"),
                    mbps = dlLive,
                    active = running && phase == StabilityTest.Phase.DOWNLOAD,
                    tint = ghajarColors.accentAlt,
                    modifier = Modifier.weight(1f)
                )
                SpeedTile(
                    icon = Icons.Filled.ArrowUpward,
                    label = t("upload"),
                    mbps = ulLive,
                    active = running && phase == StabilityTest.Phase.UPLOAD,
                    tint = AppAqua,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AnimatedVisibility(
            visible = result != null,
            enter = fadeIn(tween(420, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(420, easing = FastOutSlowInEasing)) +
                    scaleIn(tween(420, easing = FastOutSlowInEasing), initialScale = 0.92f),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            result?.let { r ->
                val ms: (Double) -> String = { localizeDigits("${it.toInt()}", lang) + " " + t("unit_ms") }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricItem(Icons.Filled.Schedule, t("stab_idle_latency"), ms(r.idleLatency), Modifier.weight(1f))
                            MetricItem(Icons.Filled.GraphicEq, t("stab_jitter"), ms(r.jitter), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricItem(Icons.Filled.ArrowDownward, t("stab_dl_latency"), ms(r.downloadLatency), Modifier.weight(1f))
                            MetricItem(Icons.Filled.ArrowUpward, t("stab_ul_latency"), ms(r.uploadLatency), Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        QualityStartButton(
            running = running,
            onClick = { if (running) cancel() else start() },
            modifier = Modifier.fillMaxWidth().appearOnce(140)
        )

        InfoBox(
            if (target != null) t("stab_testing_server") + " " + target.name
            else t("stab_direct"),
            centered = true,
            modifier = Modifier.appearOnce(200)
        )

        if (failed) {
            Text(t("stab_failed"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error)
        }

        AnimatedVisibility(
            visible = result != null,
            enter = fadeIn(tween(450, delayMillis = 120, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(450, easing = FastOutSlowInEasing)) +
                    scaleIn(tween(450, delayMillis = 120, easing = FastOutSlowInEasing), initialScale = 0.92f),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            result?.let { r ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    RevealOnScroll { shown ->
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            RevealText(t("stab_quality"), MaterialTheme.typography.labelLarge, shown, 0)
                            val score = overallScore(r)
                            val tint = qualityColor(score)
                            val sweep by animateFloatAsState(
                                targetValue = if (shown) (score / 100.0).toFloat() else 0f,
                                animationSpec = tween(900, easing = FastOutSlowInEasing),
                                label = "qualityArc"
                            )
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(148.dp)) {
                                    val stroke = 14.dp.toPx()
                                    val inset = stroke / 2f
                                    drawArc(
                                        color = tint.copy(alpha = 0.16f),
                                        startAngle = 135f,
                                        sweepAngle = 270f,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = Size(size.width - stroke, size.height - stroke),
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = tint,
                                        startAngle = 135f,
                                        sweepAngle = 270f * sweep,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = Size(size.width - stroke, size.height - stroke),
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        localizeDigits("${(sweep * 100).toInt()}", lang),
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tint
                                    )
                                    Text(
                                        t(qualityLabelKey(score)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun overallScore(r: StabilityTest.Result): Double {
    val latency = (100.0 - (r.idleLatency - 40.0) * 0.45).coerceIn(0.0, 100.0)
    val jitter = (100.0 - r.jitter * 3.0).coerceIn(0.0, 100.0)
    val loaded = (100.0 - (maxOf(r.downloadLatency, r.uploadLatency) - r.idleLatency) * 0.5)
        .coerceIn(0.0, 100.0)
    val down = (r.downloadMbps / 25.0 * 100.0).coerceIn(0.0, 100.0)
    val up = (r.uploadMbps / 10.0 * 100.0).coerceIn(0.0, 100.0)
    return (latency * 0.25 + jitter * 0.2 + loaded * 0.2 + down * 0.25 + up * 0.1)
        .coerceIn(0.0, 100.0)
}

private fun qualityLabelKey(score: Double): String = when {
    score >= 80 -> "stab_q_excellent"
    score >= 60 -> "stab_q_good"
    score >= 40 -> "stab_q_fair"
    else -> "stab_q_poor"
}

@Composable
private fun qualityColor(score: Double): Color = when {
    score >= 80 -> AppGreen
    score >= 60 -> AppCyan
    score >= 40 -> ghajarColors.warning
    else -> ghajarColors.error
}

private fun formatTestTime(millis: Long, lang: Lang): String {
    val sdf = java.text.SimpleDateFormat("yyyy/MM/dd  HH:mm", java.util.Locale.US)
    return localizeDigits(sdf.format(java.util.Date(millis)), lang)
}

@Composable
internal fun rememberInternetOffline(): Boolean {
    var offline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val hosts = listOf("8.8.8.8" to 443, "1.1.1.1" to 443)
            var reached = false
            for (h in hosts) {
                if (Pinger.ping(h.first, h.second, 2000) is PingResult.Ok) {
                    reached = true
                    break
                }
                delay(120)
            }
            offline = !reached
            delay(if (offline) 5000 else 15000)
        }
    }
    return offline
}

@Composable
private fun SpeedTile(
    icon: ImageVector,
    label: String,
    mbps: Double,
    active: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val frac by animateFloatAsState(
        sqrt((mbps / 100.0).coerceIn(0.0, 1.0)).toFloat(),
        tween(600, easing = FastOutSlowInEasing),
        label = "speedTile"
    )
    val glow by rememberInfiniteTransition(label = "speedGlow").animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = ghajarEndless(infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )),
        label = "speedGlowA"
    )
    val border = if (active) tint.copy(alpha = glow) else tint.copy(alpha = 0.22f)

    Column(
        modifier
            .clip(RoundedCornerShape(GhajarRadius.lg))
            .background(ghajarColors.secondaryCard)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                localizeDigits(String.format(java.util.Locale.US, "%.1f", mbps), lang),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (active) tint else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            Text(
                t("unit_mbps"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(tint.copy(alpha = 0.15f))
        ) {
            Box(
                Modifier.fillMaxWidth(frac).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint)
            )
        }
    }
}

@Composable
private fun SpeedBar(
    label: String,
    mbps: Double,
    active: Boolean,
    accent: List<Color>
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val track = ghajarColors.secondaryCard

    val targetFrac = sqrt((mbps / 100.0).coerceIn(0.0, 1.0)).toFloat()
    val frac by animateFloatAsState(targetFrac, tween(600), label = "speedBar")

    val barStart = accent.first()
    val barEnd = accent.last()
    var trackPx by remember { mutableStateOf(1) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val sweep by shimmer.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = ghajarEndless(infiniteRepeatable(tween(1100, easing = LinearEasing))),
        label = "sweep"
    )

    val accentBrush = Brush.horizontalGradient(
        if (isDark) accent else accent.map { lerp(it, Color.Black, 0.34f) }
    )
    val chip = ghajarColors.secondaryCard

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(chip)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall.copy(brush = accentBrush))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(chip)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    localizeDigits("%.2f".format(mbps), lang) + " " + t("unit_mbps"),
                    style = MaterialTheme.typography.titleLarge.copy(brush = accentBrush)
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(22.dp)
                .onSizeChanged { trackPx = it.width }
                .clip(RoundedCornerShape(50)).background(track)
        ) {
            val fillFrac = frac.coerceIn(0f, 1f)
            val tp = trackPx.toFloat().coerceAtLeast(1f)
            val brush = if (isRtl)
                Brush.horizontalGradient(
                    colors = listOf(barEnd, barStart),
                    startX = fillFrac * tp - tp,
                    endX = fillFrac * tp
                )
            else
                Brush.horizontalGradient(
                    colors = listOf(barStart, barEnd),
                    startX = 0f,
                    endX = tp
                )
            Box(
                Modifier.fillMaxWidth(fillFrac).fillMaxHeight()
                    .clip(RoundedCornerShape(50)).background(brush)
            ) {
                if (active) {
                    val fw = (fillFrac * tp).coerceAtLeast(1f)
                    val band = fw * 0.4f
                    val pos = sweep * (fw + band) - band
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                startX = pos,
                                endX = pos + band
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(mixedText(label), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun QualityStartButton(running: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = stringsFn()
    val tint by animateColorAsState(
        targetValue = if (running) ghajarColors.warning else MaterialTheme.colorScheme.primary,
        animationSpec = tween(420),
        label = "qualityBtnTint"
    )
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "qualityBtnPress"
    )
    Box(
        modifier
            .height(58.dp)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        tint.copy(alpha = 0.16f),
                        tint.copy(alpha = 0.30f),
                        tint.copy(alpha = 0.16f)
                    )
                )
            )
            .border(1.6.dp, tint.copy(alpha = 0.70f), RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        ConnectSweep(color = tint, active = running, modifier = Modifier.matchParentSize())
        AnimatedContent(
            targetState = running,
            transitionSpec = {
                (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.9f)) togetherWith
                        (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.9f))
            },
            label = "qualityBtnLabel"
        ) { busy ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (busy) Icons.Filled.Close else Icons.Filled.Speed,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (busy) t("cancel") else t("stab_start"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ConnectSweep(color: Color, active: Boolean, modifier: Modifier = Modifier) {
    // It already knew whether it was wanted - `active` - and animated anyway,
    // returning early from the draw once the fade reached zero. The band was
    // invisible; the frame callback was not.
    val phase = ghajarPulse(active = active, durationMillis = 1500, reverse = false)
    val fade by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "connSweepFade"
    )
    if (fade <= 0.004f) return
    Spacer(
        modifier.drawWithCache {
            val bandW = (size.width * 0.42f).coerceAtLeast(1f)
            val brush = Brush.horizontalGradient(
                0.00f to color.copy(alpha = 0f),
                0.30f to color.copy(alpha = 0.14f),
                0.50f to color.copy(alpha = 0.42f),
                0.70f to color.copy(alpha = 0.14f),
                1.00f to color.copy(alpha = 0f),
                startX = 0f,
                endX = bandW
            )
            val travel = size.width + bandW
            val band = Size(bandW, size.height)
            onDrawBehind {
                val x = phase.value * travel - bandW
                translate(left = x) {
                    drawRect(brush = brush, topLeft = Offset.Zero, size = band, alpha = fade)
                }
            }
        }
    )
}

@Composable
private fun ConnectGlow(color: Color, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val tr = rememberInfiniteTransition(label = "connectBeam")
    val progress by tr.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = ghajarEndless(infiniteRepeatable(tween(2600, easing = LinearEasing))),
        label = "beam"
    )
    Spacer(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .drawWithCache {
                val radius = 16.dp.toPx()
                val inset = 1.dp.toPx()
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(inset, inset, size.width - inset, size.height - inset),
                            CornerRadius(radius, radius)
                        )
                    )
                }
                val pm = PathMeasure().apply { setPath(path, true) }
                val len = pm.length
                onDrawBehind {
                    if (len <= 0f) return@onDrawBehind
                    val head = ((progress % 1f) + 1f) % 1f * len
                    val tailLen = len * 0.16f
                    val blobs = 16
                    val step = tailLen / blobs
                    fun at(dist: Float) = pm.getPosition(((dist % len) + len) % len)
                    fun glow(c: Offset, r: Float, peak: Float) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to color.copy(alpha = peak),
                                    0.40f to color.copy(alpha = peak * 0.45f),
                                    0.75f to color.copy(alpha = peak * 0.12f),
                                    1.0f to color.copy(alpha = 0f)
                                ),
                                center = c, radius = r
                            ),
                            radius = r, center = c
                        )
                    }
                    for (k in blobs downTo 1) {
                        val frac = 1f - (k - 1f) / blobs
                        val a = frac * frac
                        if (a <= 0.01f) continue
                        glow(at(head - k * step), 5.dp.toPx() + 7.dp.toPx() * frac, 0.6f * a)
                    }
                    val hp = at(head)
                    glow(hp, 12.dp.toPx(), 0.85f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White,
                                0.45f to Color.White.copy(alpha = 0.5f),
                                1.0f to Color.White.copy(alpha = 0f)
                            ),
                            center = hp, radius = 4.5.dp.toPx()
                        ),
                        radius = 4.5.dp.toPx(), center = hp
                    )
                }
            }
    )
}

@Composable
private fun PulseHalo(color: Color, size: Dp, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "halo")
    val breath by tr.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = ghajarEndless(infiniteRepeatable(
            tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )),
        label = "haloBreath"
    )
    val strength by tr.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = ghajarEndless(infiniteRepeatable(
            tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )),
        label = "haloStrength"
    )

    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val glowR = (this.size.minDimension / 2f) * breath

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.22f * strength),
                    color.copy(alpha = 0.07f * strength),
                    color.copy(alpha = 0f)
                ),
                center = c,
                radius = glowR
            ),
            radius = glowR,
            center = c
        )
    }
}

@Composable
private fun PingLine(color: Color, size: Dp = 96.dp, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "ping")
    val t by tr.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = ghajarEndless(infiniteRepeatable(tween(1800, easing = LinearEasing))),
        label = "pingT"
    )
    val core by tr.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = ghajarEndless(infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )),
        label = "pingCore"
    )

    Canvas(modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val maxR = this.size.minDimension / 2f - 2.dp.toPx()

        for (i in 0 until 3) {
            val p = (t + i / 3f) % 1f
            val r = maxR * p
            val fade = (1f - p).coerceIn(0f, 1f)
            if (r > 1f) {
                drawCircle(
                    color = color.copy(alpha = 0.45f * fade * fade),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        val coreR = maxR * 0.22f * core
        drawCircle(color.copy(alpha = 0.18f), radius = coreR * 2.4f, center = Offset(cx, cy))
        drawCircle(color.copy(alpha = 0.40f), radius = coreR * 1.5f, center = Offset(cx, cy))
        drawCircle(color, radius = coreR, center = Offset(cx, cy))
    }
}

@Composable
private fun RevealOnScroll(content: @Composable (shown: Boolean) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    Box(
        Modifier.onGloballyPositioned { c ->
            if (!shown) {
                val b = c.boundsInWindow()
                if (b.height > 0f && b.top < screenH * 0.9f && b.bottom > 0f) shown = true
            }
        }
    ) {
        content(shown)
    }
}

@Composable
private fun RevealText(text: String, style: TextStyle, shown: Boolean, order: Int) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        if (shown) { delay(order * 90L); appear.animateTo(1f, tween(450)) }
    }
    val p = appear.value
    Text(text, style = style, modifier = Modifier.graphicsLayer { alpha = p; translationX = (1f - p) * 24f })
}

private enum class RangeMode(val key: String) {
    TODAY("today"), WEEK("range_7d"), MONTH("range_30d"), CUSTOM("custom_range")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataUsageScreen(modifier: Modifier = Modifier) {
    val t = stringsFn()
    val lang = LocalLang.current
    val daily by UsageStore.usage.collectAsState()
    val hourly by UsageStore.hourly.collectAsState()
    val dailyCfg by UsageStore.dailyCfg.collectAsState()
    val hourlyCfg by UsageStore.hourlyCfg.collectAsState()
    val context = LocalContext.current
    var mode by remember { mutableStateOf(RangeMode.TODAY) }
    var menuOpen by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf(LocalDate.now().minusDays(6)) }
    var toDate by remember { mutableStateOf(LocalDate.now()) }
    var fromHour by remember { mutableStateOf(0) }
    var toHour by remember { mutableStateOf(23) }
    var detailConfig by remember { mutableStateOf<String?>(null) }

    val bars = remember(daily, hourly, mode, fromDate, toDate, fromHour, toHour) {
        when (mode) {
            RangeMode.TODAY -> UsageStore.hourlyToday(hourly)
            RangeMode.WEEK -> UsageStore.dailyBars(daily, 7)
            RangeMode.MONTH -> UsageStore.dailyBars(daily, 30)
            RangeMode.CUSTOM -> {
                val lo = if (fromDate.isAfter(toDate)) toDate else fromDate
                val hi = if (fromDate.isAfter(toDate)) fromDate else toDate
                val span = java.time.temporal.ChronoUnit.DAYS.between(lo, hi)
                if (span <= 2) {
                    val loH = minOf(fromHour, toHour)
                    val hiH = maxOf(fromHour, toHour)
                    UsageStore.hourlyBarsRange(hourly, lo, hi).filter { bar ->
                        val h = bar.short.toIntOrNull()
                        h == null || h in loH..hiH
                    }
                } else UsageStore.dailyBarsRange(daily, lo, hi)
            }
        }
    }
    val total = remember(bars) { UsageStore.sum(bars) }
    val hourlyMode = mode == RangeMode.TODAY ||
            (mode == RangeMode.CUSTOM &&
                    java.time.temporal.ChronoUnit.DAYS.between(
                        if (fromDate.isAfter(toDate)) toDate else fromDate,
                        if (fromDate.isAfter(toDate)) fromDate else toDate
                    ) <= 2)
    val directOf: (UsageStore.Bar) -> Long = { bar ->
        val src = if (hourlyMode) hourlyCfg else dailyCfg
        src[bar.key]?.get(UsageStore.DIRECT_KEY)?.let { it[0] + it[1] } ?: 0L
    }
    val rangeDirect = remember(bars, dailyCfg, hourlyCfg, hourlyMode) { bars.sumOf(directOf) }
    val rangeVpn = (total[0] + total[1] - rangeDirect).coerceAtLeast(0L)

    val ranged = remember(dailyCfg, hourlyCfg, bars, hourlyMode) {
        UsageStore.configTotalsRange(dailyCfg, hourlyCfg, bars, hourlyMode)
    }
    val configDirect = ranged.firstOrNull { it.first == UsageStore.DIRECT_KEY }?.second
        ?: longArrayOf(0L, 0L)
    val perConfig = ranged.filter { it.first != UsageStore.DIRECT_KEY }
    val grand = configDirect[0] + configDirect[1] + perConfig.sumOf { it.second[0] + it.second[1] }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                t("range"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t(mode.key), modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    offset = DpOffset(0.dp, 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = ghajarColors.surface,
                    border = BorderStroke(1.dp, ghajarColors.border)
                ) {
                    RangeMode.values().forEach { m ->
                        DropdownMenuItem(
                            text = { Text(t(m.key), style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = {
                                if (mode == m) Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.height(40.dp),
                            onClick = { mode = m; menuOpen = false }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = mode == RangeMode.CUSTOM,
            enter = fadeIn(tween(280)) +
                    slideInVertically(tween(320, easing = FastOutSlowInEasing)) { -it / 3 } +
                    expandVertically(tween(320, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(260, easing = FastOutSlowInEasing))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(GhajarRadius.md))
                        .background(ghajarColors.secondaryCard),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RangeCell(
                        label = t("from"),
                        date = fromDate,
                        hour = fromHour,
                        lang = lang,
                        onDate = { showDatePicker(context, fromDate) { fromDate = it } },
                        onHour = { fromHour = it },
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier.width(1.dp).height(38.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                    )
                    RangeCell(
                        label = t("to"),
                        date = toDate,
                        hour = toHour,
                        lang = lang,
                        onDate = { showDatePicker(context, toDate) { toDate = it } },
                        onHour = { toHour = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    t("custom_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = {
                val csv = buildString {
                    append("label,upload_bytes,download_bytes\n")
                    bars.forEach { bar -> append("${bar.label},${bar.up},${bar.down}\n") }
                }
                runCatching {
                    val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
                    val file = java.io.File(dir, "ghajar-usage.csv")
                    file.writeText(csv, Charsets.UTF_8)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "خروجی CSV مصرف"))
                }
            },
            enabled = bars.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("خروجی CSV همین بازه") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TransferTile(
                        icon = Icons.Filled.ArrowDownward,
                        label = t("download"),
                        bytes = total[1],
                        tint = ghajarColors.info,
                        lang = lang,
                        modifier = Modifier.weight(1f)
                    )
                    TransferTile(
                        icon = Icons.Filled.ArrowUpward,
                        label = t("upload"),
                        bytes = total[0],
                        tint = ghajarColors.accentAlt,
                        lang = lang,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            t("total"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatBytes(total[0] + total[1], lang),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = AppGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                t("via_vpn"),
                                style = MaterialTheme.typography.labelMedium,
                                color = AppGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            formatBytes(rangeVpn, lang),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppGreen,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (bars.isEmpty()) {
            Text(t("no_data_range"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            var chartVisible by remember(mode, fromDate, toDate) { mutableStateOf(false) }
            LaunchedEffect(mode, fromDate, toDate) { chartVisible = true }
            AnimatedVisibility(
                visible = chartVisible,
                enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.92f)
            ) {
                UsageBarChart(bars)
            }
        }

        if (grand > 0L) {
            SettingsGroup(t("usage_by_config")) {
                UsageShareRow(
                    name = t("usage_direct"),
                    bytes = configDirect[0] + configDirect[1],
                    grand = grand,
                    tint = DirectBarColor,
                    lang = lang
                )
                perConfig.take(8).forEachIndexed { i, (name, v) ->
                    UsageShareRow(
                        name = name,
                        bytes = v[0] + v[1],
                        grand = grand,
                        tint = ServerPalette[i % ServerPalette.size],
                        lang = lang,
                        onClick = { detailConfig = name }
                    )
                }
            }
        }
    }

    detailConfig?.let { name ->
        val windows = if (hourlyMode) bars.mapNotNull { bar ->
            val perCfg = (if (hourlyMode) hourlyCfg else dailyCfg)[bar.key]?.get(name)
            if (perCfg == null || (perCfg[0] + perCfg[1]) <= 0L) null
            else UsageStore.hourKeyToEpochRange(bar.key)
        } else emptyList()
        val entry = perConfig.firstOrNull { it.first == name }?.second ?: longArrayOf(0L, 0L)
        ConfigUsageDetailDialog(
            name = name,
            upBytes = entry[0],
            downBytes = entry[1],
            windows = windows,
            longRange = !hourlyMode,
            lang = lang,
            onDismiss = { detailConfig = null }
        )
    }
}

// Per-app and per-server series colours. They were a fixed six-colour array
// plus a fixed grey; both now come from the active theme, so the charts belong
// to the same palette as everything else.
private val DirectBarColor: Color
    @Composable get() = ghajarColors.neutralBar

private val ServerPalette: List<Color>
    @Composable get() = ghajarColors.chart

@Composable
private fun TransferTile(
    icon: ImageVector,
    label: String,
    bytes: Long,
    tint: Color,
    lang: Lang,
    modifier: Modifier = Modifier
) {
    val parts = formatBytesParts(bytes, lang)
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                parts.first,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            Text(
                parts.second,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

@Composable
private fun RangeCell(
    label: String,
    date: java.time.LocalDate,
    hour: Int,
    lang: Lang,
    onDate: () -> Unit,
    onHour: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            mixedText(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                localizeDigits("${date.year}/${date.monthValue}/${date.dayOfMonth}", lang),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (lang == Lang.FA) VazirFont else LexendFont,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onDate() }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(6.dp))
            Box {
                Text(
                    localizeDigits(String.format(java.util.Locale.US, "%02d:00", hour), lang),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (lang == Lang.FA) VazirFont else LexendFont,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { open = true }
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                )
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    offset = DpOffset(0.dp, 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = ghajarColors.surface,
                    border = BorderStroke(1.dp, ghajarColors.border)
                ) {
                    (0..23).forEach { h ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    localizeDigits(String.format(java.util.Locale.US, "%02d:00", h), lang),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = if (lang == Lang.FA) VazirFont else LexendFont
                                )
                            },
                            trailingIcon = {
                                if (h == hour) Icon(
                                    Icons.Filled.Check, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.height(38.dp),
                            onClick = { onHour(h); open = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageShareRow(
    name: String,
    bytes: Long,
    grand: Long,
    tint: Color,
    lang: Lang,
    onClick: (() -> Unit)? = null
) {
    val frac = if (grand > 0L) (bytes.toFloat() / grand.toFloat()).coerceIn(0f, 1f) else 0f
    val width by animateFloatAsState(frac, tween(500), label = "usageShare")
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                flagRuns(name, LexendFont),
                inlineContent = flagInlineContent(name, MaterialTheme.typography.bodyMedium.fontSize),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                formatBytes(bytes, lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f))
        ) {
            Box(
                Modifier.fillMaxWidth(width).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint)
            )
        }
    }
}

/** Per-config usage detail, opened by tapping a row in the "usage by config"
 * list. Reuses GlassDialog styling. The per-app breakdown is real Android
 * NetworkStatsManager data restricted to the hour-buckets this exact config
 * was actually active in (per UsageStore's own per-config attribution) — it
 * is only offered for hourly-precision ranges (today, or a short custom
 * range), since day-granularity buckets can't be windowed precisely enough
 * to attribute to one config without also mixing in other configs' traffic
 * from the same day. */
@Composable
private fun ConfigUsageDetailDialog(
    name: String,
    upBytes: Long,
    downBytes: Long,
    windows: List<Pair<Long, Long>>,
    longRange: Boolean,
    lang: Lang,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var perApp by remember(name, windows) { mutableStateOf<PerAppUsageStats.Result?>(null) }
    LaunchedEffect(name, windows) {
        perApp = if (longRange || windows.isEmpty()) null
        else PerAppUsageStats.queryForWindows(context, windows)
    }
    GlassDialog(
        onDismiss = onDismiss,
        title = name,
        confirmLabel = "بستن",
        onConfirm = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("دانلود", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatBytes(downBytes, lang), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("آپلود", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatBytes(upBytes, lang), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("مجموع", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatBytes(upBytes + downBytes, lang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(color = ghajarColors.border)
            Text("مصرف به تفکیک برنامه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            when {
                longRange -> Text(
                    "تفکیک برنامه‌ای فقط برای «امروز» یا یک بازهٔ سفارشی کوتاه (حداکثر ۲ روز) در دسترس است؛ برای بازه‌های هفتگی و ماهانه فقط دقت روزانه ذخیره می‌شود.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                windows.isEmpty() -> Text("در این بازه مصرفی برای این سرویس ثبت نشده است.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                perApp == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("در حال خواندن آمار برنامه‌ها…", style = MaterialTheme.typography.bodySmall)
                }
                else -> when (val r = perApp) {
                    is PerAppUsageStats.Result.PermissionRequired -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "برای دیدن مصرف هر برنامه، دسترسی «Usage access» را برای قاجار وی‌پی‌ان فعال کن. اندروید این دسترسی را فقط از تنظیمات می‌دهد.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                        )
                        BounceOutlinedButton(
                            onClick = { runCatching { context.startActivity(PerAppUsageStats.usageAccessSettingsIntent(context)) } },
                            minHeight = 38.dp, modifier = Modifier.fillMaxWidth()
                        ) { Text("باز کردن تنظیمات دسترسی") }
                    }
                    is PerAppUsageStats.Result.Unavailable -> Text(r.reason,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is PerAppUsageStats.Result.Ok -> if (r.apps.isEmpty()) {
                        Text("اندروید هنوز ترافیکی را به برنامهٔ خاصی نسبت نداده است.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val grand = r.apps.sumOf { it.totalBytes }.coerceAtLeast(1L)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            r.apps.forEachIndexed { i, app ->
                                UsageShareRow(
                                    name = app.label,
                                    bytes = app.totalBytes,
                                    grand = grand,
                                    tint = ServerPalette[i % ServerPalette.size],
                                    lang = lang
                                )
                            }
                        }
                    }
                    null -> Unit
                }
            }
        }
    }
}

private fun showDatePicker(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    android.app.DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth
    ).show()
}

@Composable
private fun UsageBarChart(bars: List<UsageStore.Bar>) {
    val t = stringsFn()
    val lang = LocalLang.current
    val maxVal = (bars.maxOfOrNull { it.total } ?: 0L).coerceAtLeast(1L)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val labelEvery = (bars.size / 6).coerceAtLeast(1)
    var focused by remember { mutableStateOf<Int?>(null) }

    val animKey = remember(bars) { bars.hashCode() }
    var appeared by remember(animKey) { mutableStateOf(false) }
    LaunchedEffect(animKey) { appeared = true }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val f = focused
            AnimatedVisibility(
                visible = f != null && f in bars.indices,
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(150))
            ) {
                val bar = bars[f ?: 0]
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(localizeDigits(bar.label, lang),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${t("download")} ${formatBytes(bar.down, lang)}   ${t("upload")} ${formatBytes(bar.up, lang)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            if (f == null) {
                Text(t("peak_per_bar").format(formatBytes(maxVal, lang)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            var rowWidth by remember { mutableStateOf(1) }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .onSizeChanged { rowWidth = it.width }
                        .pointerInput(bars.size) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown()
                                    fun idxAt(x: Float): Int =
                                        ((x / rowWidth) * bars.size).toInt().coerceIn(0, bars.lastIndex)
                                    focused = idxAt(down.position.x)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pos = event.changes.first().position
                                        focused = idxAt(pos.x)
                                    } while (event.changes.any { it.pressed })
                                    focused = null
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    bars.forEachIndexed { i, bar ->
                        val frac = (bar.total.toFloat() / maxVal).coerceIn(0f, 1f)
                        val isFocused = focused == i
                        val targetFrac = if (bar.total > 0 && appeared) frac.coerceAtLeast(0.03f) else 0f
                        val animatedFrac by animateFloatAsState(
                            targetValue = targetFrac,
                            animationSpec = tween(durationMillis = 600),
                            label = "bar"
                        )
                        val focusColor = MaterialTheme.colorScheme.primaryContainer
                        val barColor by animateColorAsState(
                            targetValue = if (isFocused) focusColor else primary,
                            animationSpec = tween(180),
                            label = "barColor"
                        )
                        val barScale by animateFloatAsState(
                            targetValue = if (isFocused) 1.12f else 1f,
                            animationSpec = tween(180),
                            label = "barScale"
                        )
                        Box(
                            Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight()
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(track.copy(alpha = 0.4f))
                            )
                            if (animatedFrac > 0f) {
                                Box(
                                    Modifier.fillMaxWidth().fillMaxHeight(animatedFrac)
                                        .graphicsLayer {
                                            scaleX = barScale; scaleY = 1f
                                            transformOrigin = TransformOrigin(0.5f, 1f)
                                        }
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(barColor)
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    bars.forEachIndexed { i, bar ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (i % labelEvery == 0) {
                                Text(
                                    localizeDigits(bar.short, lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SniffTypeSelector(selected: Set<String>, onToggle: (String) -> Unit) {
    val types = listOf("http", "tls", "quic", "fakedns", "fakedns+others")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { type ->
            val on = type in selected
            val bg by animateColorAsState(
                targetValue = if (on) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(200), label = "chipBg"
            )
            val fg by animateColorAsState(
                targetValue = if (on) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(200), label = "chipFg"
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onToggle(type) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(type, color = fg, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * One network's rule, as a row whose value cycles on tap.
 *
 * There are exactly three choices and each is a short phrase, so a dropdown
 * would add a menu to read one of three words.
 */
@Composable
private fun NetRuleRow(
    title: String,
    action: NetRuleAction,
    enabled: Boolean,
    onChange: (NetRuleAction) -> Unit
) {
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    val c = ghajarColors
    val label = when (action) {
        NetRuleAction.OFF -> t("netrule_off")
        NetRuleAction.LAST -> t("netrule_last")
        NetRuleAction.FASTEST -> t("netrule_fastest")
    }
    SlabRow(
        title = title,
        subtitle = label,
        icon = when (action) {
            NetRuleAction.OFF -> Icons.Filled.Block
            NetRuleAction.LAST -> Icons.Filled.History
            NetRuleAction.FASTEST -> Icons.Filled.Speed
        },
        accent = if (action == NetRuleAction.OFF) c.textMuted else c.primary,
        enabled = enabled,
        chevron = true,
        onClick = {
            val all = NetRuleAction.values()
            onChange(all[(action.ordinal + 1) % all.size])
        }
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    // Every toggle in the app comes through here. It is the skin's row, not a
    // label next to a Material switch: a state pip that takes the brand tone
    // when the option is on, and the whole row as the tap target so the switch
    // is confirmation rather than the only thing you are allowed to hit.
    val c = ghajarColors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.md))
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(vertical = GhajarSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background((if (checked) c.primary else c.textMuted).copy(alpha = if (enabled) 0.14f else 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (!enabled) c.onDisabled else if (checked) c.primary else c.textMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                mixedText(title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) c.textPrimary else c.onDisabled
            )
            if (subtitle.isNotBlank()) {
                Text(
                    mixedText(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
            }
        }
        SkinSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}


private fun Modifier.pressBounce(
    scale: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope
): Modifier = this
    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            scope.launch {
                scale.animateTo(0.97f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
            }
            waitForUpOrCancellation()
            scope.launch {
                scale.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    }

@Composable
private fun FillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** A primary action carries the accent; a secondary one sits on the
     *  nested-surface tone. They used to differ only by border width, which
     *  meant the page never said which button it wanted you to press. */
    filled: Boolean = false,
    minHeight: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit
) {
    // Every Bounce*Button in the app lands here, so this is where the old
    // "outlined, glassy, slightly raised" look lived. On the skin a secondary
    // action is a filled capsule with no stroke and no blur; the press still
    // floods it with the accent, which is the one part of the old button worth
    // keeping.
    val c = ghajarColors
    val primary = accent
    val onPrimary = c.onPrimary
    val disabled = c.onDisabled
    val shape = RoundedCornerShape(GhajarRadius.pill)

    val interaction = remember { MutableInteractionSource() }
    var center by remember { mutableStateOf(Offset.Zero) }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "fillScale"
    )
    val maxR = remember(center, sz) {
        val dx = maxOf(center.x, sz.width - center.x)
        val dy = maxOf(center.y, sz.height - center.y)
        sqrt(dx * dx + dy * dy)
    }
    val radius by animateFloatAsState(
        targetValue = if (pressed) maxR else 0f,
        animationSpec = tween(durationMillis = if (pressed) 550 else 300),
        label = "fillRadius"
    )
    val fillFrac = if (maxR > 0f) (radius / maxR).coerceIn(0f, 1f) else 0f
    val restColor = when {
        !enabled -> disabled
        filled -> onPrimary
        else -> primary
    }
    val contentColor = if (filled) restColor else lerp(restColor, onPrimary, fillFrac)

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                when {
                    !enabled -> c.disabled.copy(alpha = 0.25f)
                    filled -> primary
                    else -> c.secondaryCard
                }
            )
            .drawBehind {
                if (radius > 0.5f) drawCircle(color = primary, radius = radius, center = center)
            }
            .defaultMinSize(minWidth = 56.dp, minHeight = minHeight)
            .onSizeChanged { sz = it }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    center = down.position
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = androidx.compose.ui.semantics.Role.Button) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
private fun BounceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) = FillButton(onClick, modifier, enabled, filled = true, content = content)

@Composable
private fun BounceOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit
) = FillButton(onClick, modifier, enabled,
    minHeight = minHeight, contentPadding = contentPadding, accent = accent, content = content)

@Composable
private fun BounceTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) = FillButton(
    onClick, modifier, enabled,
    minHeight = 40.dp,
    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content = content
)

@Composable
private fun BounceIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    // Flat: the header it mostly lives in is now one continuous tone, and a
    // raised, gradient-filled tile in the corner of it re-drew the seam this
    // release just removed.
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.pressBounce(scale, scope),
        content = content
    )
}

private fun pingRank(p: PingResult?): Int = when (p) {
    is PingResult.Ok -> p.ms
    PingResult.Testing -> 1_000_000
    null -> 2_000_000
    PingResult.Failed -> 3_000_000
}

private fun statusText(conn: Connection, error: String?, lang: Lang): String = when (conn) {
    Connection.DISCONNECTED -> Strings.get(lang, "status_disconnected")
    Connection.CONNECTING -> Strings.get(lang, "status_connecting")
    Connection.CONNECTED -> Strings.get(lang, "status_connected")
    Connection.DISCONNECTING -> Strings.get(lang, "status_disconnected")
    Connection.ERROR -> localizeDigits("${Strings.get(lang, "status_error")}: ${error ?: ""}", lang)
}

private fun formatBytes(bytes: Long, lang: Lang): String {
    val unit: String
    val num: String
    when {
        bytes < 1024 -> { num = "$bytes"; unit = Strings.get(lang, "unit_b") }
        bytes < 1024 * 1024 -> { num = "%.1f".format(bytes / 1024.0); unit = Strings.get(lang, "unit_kb") }
        bytes < 1024L * 1024 * 1024 -> { num = "%.1f".format(bytes / (1024.0 * 1024)); unit = Strings.get(lang, "unit_mb") }
        else -> { num = "%.2f".format(bytes / (1024.0 * 1024 * 1024)); unit = Strings.get(lang, "unit_gb") }
    }
    return "\u202A${localizeDigits(num, lang)}\u202C $unit"
}

@Composable
private fun SpeedText(bytes: Long) {
    val t = stringsFn()
    val lang = LocalLang.current
    val parts = formatBytesParts(bytes, lang)
    Text(
        buildAnnotatedString {
            append("\u202A${parts.first}\u202C ")
            withStyle(SpanStyle(fontSize = 12.sp)) {
                append(parts.second + t("unit_per_sec"))
            }
        },
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1
    )
}

private fun formatBytesParts(bytes: Long, lang: Lang): Pair<String, String> {
    val unit: String
    val num: String
    when {
        bytes < 1024 -> { num = "$bytes"; unit = Strings.get(lang, "unit_b") }
        bytes < 1024 * 1024 -> { num = "%.1f".format(bytes / 1024.0); unit = Strings.get(lang, "unit_kb") }
        bytes < 1024L * 1024 * 1024 -> { num = "%.1f".format(bytes / (1024.0 * 1024)); unit = Strings.get(lang, "unit_mb") }
        else -> { num = "%.2f".format(bytes / (1024.0 * 1024 * 1024)); unit = Strings.get(lang, "unit_gb") }
    }
    return localizeDigits(num, lang) to unit
}

/** Visible OpenVPN card: saved profiles, state, pre-connect ping, connect/disconnect, edit/delete. */
@Composable
private fun GhajarOpenVpnSummaryTile(onOpen: () -> Unit) {
    // A slim status row instead of the full OpenVPN card: the full profile
    // list/connect/import controls now live in their own full-size screen
    // (OpenVpnHubScreen, reachable from "افزودن سرور") so they always have
    // the whole screen to render in, rather than being squeezed into this
    // shared list alongside every other config group.
    val status by GhajarOpenVpnBridge.status.collectAsState()
    val stateLabel = when (status) {
        GhajarOvpnState.CONNECTED -> "متصل"
        GhajarOvpnState.CONNECTING -> "در حال اتصال…"
        GhajarOvpnState.ERROR -> "اتصال برقرار نشد"
        GhajarOvpnState.DISCONNECTED -> "متصل نیست"
    }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        color = ghajarColors.card,
        border = BorderStroke(1.dp, ghajarColors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Security, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("OpenVPN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stateLabel, style = MaterialTheme.typography.labelMedium,
                    color = when (status) {
                        GhajarOvpnState.CONNECTED -> MaterialTheme.colorScheme.primary
                        GhajarOvpnState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
            }
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "باز کردن",
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 180f },
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PsiphonHubScreen(
    store: ConfigStore,
    onConnect: (ProxyConfig) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Psiphon is a first-class "core" protocol (like Aether/Tor): it rides
    // through Gozarcore/Xray for the actual TUN<->socks forwarding, so unlike
    // OpenVPN, home-screen traffic stats/IP and the normal connect pipeline
    // all work for it out of the box. This screen is just a settings+connect
    // surface for the one singleton Psiphon config, in its own full screen
    // per the same reasoning as OpenVpnHubScreen.
    val configs by store.configs.collectAsState()
    var configId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { configId = store.ensurePsiphonConfig().id }
    val config = configs.firstOrNull { it.id == configId } ?: return

    val conn by VpnState.state.collectAsState()
    val activeId by VpnState.activeId.collectAsState()
    val isActive = activeId == config.id
    val statusLabel = when {
        isActive && conn == Connection.CONNECTED -> "متصل"
        isActive && conn == Connection.CONNECTING -> "در حال اتصال…"
        isActive && conn == Connection.ERROR -> "اتصال برقرار نشد"
        else -> "متصل نیست"
    }

    var mode by remember(config.id) { mutableStateOf(config.psiphonMode) }
    var country by remember(config.id) { mutableStateOf(config.psiphonCountry) }
    var cdnIps by remember(config.id) { mutableStateOf(config.psiphonCdnIps) }
    var cdnSni by remember(config.id) { mutableStateOf(config.psiphonCdnSni) }
    var oblivion by remember(config.id) { mutableStateOf(config.oblivionJson) }
    var settingsError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ghajarColors.card,
            border = BorderStroke(1.dp, ghajarColors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Psiphon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "شبکه‌ی متن‌باز Psiphon برای عبور از سانسور - بدون نیاز به آدرس یا پورت سرور.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(statusLabel, style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isActive && conn == Connection.CONNECTED -> MaterialTheme.colorScheme.primary
                        isActive && conn == Connection.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
            }
        }

        OblivionSettings(oblivion) {
            oblivion = it
            store.update(config.copy(oblivionJson = it))
        }
        settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("حالت اتصال سایفون", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PsiphonConfig.MODE_AUTO to "خودکار",
                PsiphonConfig.MODE_CDN to "CDN",
                PsiphonConfig.MODE_DIRECT to "مستقیم"
            ).forEach { (value, label) ->
                val selected = mode == value
                BounceOutlinedButton(
                    onClick = {
                        mode = value
                        store.updatePsiphonSettings(config.id, mode, country)
                    },
                    modifier = Modifier.weight(1f),
                    accent = if (selected) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.outline
                ) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
        }

        // Was a two-character text field. That asked the user to know both
        // that DE means Germany and - the part that actually bites - whether
        // Psiphon has a server there, which it does not tell you: an
        // unserved region is not rejected, the tunnel just never establishes,
        // and that is indistinguishable from the network being blocked. The
        // list now comes from the engine's own AvailableEgressRegions notice.
        PsiphonCountryRow(
            selected = country,
            onSelect = {
                country = it
                store.updatePsiphonSettings(config.id, mode, country)
            }
        )

        OutlinedTextField(cdnIps, {
            cdnIps = it
            store.update(config.copy(psiphonMode = mode, psiphonCountry = country, psiphonCdnIps = it, psiphonCdnSni = cdnSni))
        }, label = { Text("IP یا CIDR دلخواه CDN (اختیاری)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cdnSni, {
            cdnSni = it
            store.update(config.copy(psiphonMode = mode, psiphonCountry = country, psiphonCdnIps = cdnIps, psiphonCdnSni = it))
        }, label = { Text("SNI دلخواه CDN (اختیاری)") }, modifier = Modifier.fillMaxWidth())
        Text("حالت Conduit در این نسخه در دسترس نیست.", style = MaterialTheme.typography.bodySmall)
        BounceButton(
            onClick = {
                if (isActive && conn != Connection.DISCONNECTED && conn != Connection.ERROR) {
                    onDisconnect()
                } else {
                    val error = runCatching { OblivionOptions(oblivion).validate() }.exceptionOrNull()
                    settingsError = error?.message
                    if (error == null) onConnect(config.copy(psiphonMode = mode, psiphonCountry = country, psiphonCdnIps = cdnIps, psiphonCdnSni = cdnSni, oblivionJson = oblivion))
                }
            },
            enabled = conn != Connection.DISCONNECTING,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isActive && conn != Connection.DISCONNECTED && conn != Connection.ERROR) "قطع اتصال" else "اتصال",
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun OpenVpnHubScreen(
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onTest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Dedicated, full-size destination for OpenVPN - previously this section
    // was squeezed inside the shared server-picker list alongside every other
    // config, which is why it could render half cut off. Here it gets the
    // same full Column(fillMaxSize) + scroll treatment as WindscribeScreen /
    // FreeProjectsScreen, so it always has the whole screen to itself.
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GhajarOpenVpnSection(onConnect = onConnect, onDisconnect = onDisconnect, onTest = onTest)
        }
    }
}

@Composable
private fun GhajarOpenVpnSection(onConnect: (String) -> Unit, onDisconnect: () -> Unit, onTest: (String) -> Unit) {
    val context = LocalContext.current
    val status by GhajarOpenVpnBridge.status.collectAsState()
    val activeUuid by GhajarOpenVpnBridge.activeUuid.collectAsState()
    val engineMessage by GhajarOpenVpnBridge.lastMessage.collectAsState()
    val testResults by GhajarOpenVpnBridge.tests.collectAsState()
    val pingResults = remember { mutableStateMapOf<String, PingResult>() }
    var tick by remember { mutableIntStateOf(0) }
    var profiles by remember { mutableStateOf(GhajarOpenVpnBridge.profiles(context)) }
    LaunchedEffect(tick) {
        profiles = withContext(Dispatchers.IO) { GhajarOpenVpnBridge.profiles(context) }
    }
    var expanded by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<GhajarOvpnProfile?>(null) }
    var editUser by remember { mutableStateOf("") }
    var editPass by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<GhajarOvpnProfile?>(null) }
    val scope = rememberCoroutineScope()
    var bulkImports by remember { mutableStateOf<List<PendingOpenVpnImport>?>(null) }
    var bulkBad by remember { mutableIntStateOf(0) }
    var sharedCredentials by remember { mutableStateOf(true) }
    var sharedUser by remember { mutableStateOf("") }
    var sharedPass by remember { mutableStateOf("") }
    val bulkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                var bad = 0
                val good = uris.mapNotNull { uri ->
                    val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    if (bytes == null) { bad++; null }
                    else if (bytes.isEmpty()) { bad++; null }
                    else GhajarOpenVpnBridge.inspect(bytes).getOrElse { bad++; null }
                }
                good to bad
            }
            bulkBad = parsed.second
            if (parsed.first.isNotEmpty()) bulkImports = parsed.first
            else Toast.makeText(context, "هیچ فایل OVPN معتبری پیدا نشد", Toast.LENGTH_LONG).show()
        }
    }

    fun pingOvpn(profile: GhajarOvpnProfile) {
        pingResults[profile.uuid] = PingResult.Testing
        scope.launch {
            pingResults[profile.uuid] = withContext(Dispatchers.IO) { Pinger.ping(profile.host, profile.port, 3500) }
        }
    }
    val stateLabel = when (status) {
        GhajarOvpnState.CONNECTED -> "متصل"
        GhajarOvpnState.CONNECTING -> "در حال اتصال…"
        GhajarOvpnState.ERROR -> "اتصال برقرار نشد"
        GhajarOvpnState.DISCONNECTED -> "متصل نیست"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("OpenVPN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stateLabel, style = MaterialTheme.typography.labelMedium,
                        color = when (status) {
                            GhajarOvpnState.CONNECTED -> MaterialTheme.colorScheme.primary
                            GhajarOvpnState.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                    if ((status == GhajarOvpnState.ERROR || status == GhajarOvpnState.CONNECTING) && engineMessage.isNotBlank())
                        Text(engineMessage, style = MaterialTheme.typography.labelSmall,
                            color = if (status == GhajarOvpnState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Filled.ExpandMore, if (expanded) "بستن" else "باز کردن",
                        modifier = Modifier.size(20.dp))
                }
            }
            if (expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BounceOutlinedButton(
                        onClick = { bulkPicker.launch(arrayOf("application/x-openvpn-profile", "application/octet-stream", "text/plain", "*/*")) },
                        minHeight = 36.dp, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
                    ) { Text("افزودن چند OVPN", style = MaterialTheme.typography.labelMedium) }
                    BounceOutlinedButton(
                        onClick = { profiles.forEach(::pingOvpn) }, enabled = profiles.isNotEmpty(),
                        minHeight = 36.dp, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
                    ) { Text("پینگ همه", style = MaterialTheme.typography.labelMedium) }
                }
                if (profiles.isEmpty()) {
                    Text("فایل .ovpn را ایمپورت کن تا همین‌جا قابل اتصال شود؛ از «افزودن» یا باز کردن فایل استفاده کن.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                profiles.forEach { profile ->
                    val isActive = activeUuid == profile.uuid && status == GhajarOvpnState.CONNECTED
                    val isBusy = activeUuid == profile.uuid && status == GhajarOvpnState.CONNECTING
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ghajarColors.secondaryCard
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(profile.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("\u2066${profile.host}:${profile.port}\u2069",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (profile.needsCredentials) Text("نام کاربری/رمز لازم دارد", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                                when (val ping = pingResults[profile.uuid]) {
                                    is PingResult.Ok -> Text("پینگ: ${ping.ms}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    PingResult.Testing -> Text("پینگ: در حال تست…", style = MaterialTheme.typography.labelSmall)
                                    PingResult.Failed -> Text("پینگ: ناموفق", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    null -> Unit
                                }
                                testResults[profile.uuid]?.let { tr ->
                                    Text(
                                        when {
                                            tr.running -> "تست اتصال واقعی: در حال اجرا…"
                                            tr.ok == true -> "تست واقعی: موفق (${tr.connectMs ?: 0}ms)"
                                            else -> "تست واقعی: ناموفق — ${tr.message}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (tr.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                when {
                                    isActive -> "متصل"
                                    isBusy -> "…"
                                    else -> ""
                                },
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(onClick = {
                                editUser = ""; editPass = ""; editing = profile
                            }) { Icon(Icons.Filled.Edit, "ویرایش اطلاعات OVPN", modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { confirmDelete = profile }) {
                                Icon(Icons.Filled.Delete, "حذف پروفایل OVPN", tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp))
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    BounceOutlinedButton(
                                        onClick = { pingOvpn(profile) }, minHeight = 32.dp,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) { Text("پینگ", style = MaterialTheme.typography.labelSmall) }
                                }
                                if (isActive || isBusy) {
                                    BounceOutlinedButton(
                                        onClick = onDisconnect, minHeight = 34.dp,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                    ) { Text("قطع", style = MaterialTheme.typography.labelMedium) }
                                } else {
                                    Button(
                                        onClick = { onConnect(profile.uuid) }, enabled = !profile.needsCredentials,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) { Text("اتصال", style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    bulkImports?.let { imports ->
        val needsCredentials = imports.any { it.needsCredentials }
        GlassDialog(
            onDismiss = { bulkImports = null },
            title = "افزودن گروهی OVPN",
            confirmLabel = "ذخیره ${imports.size} فایل",
            dismissLabel = "انصراف",
            onConfirm = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        var ok = 0
                        var bad = bulkBad
                        imports.forEach { item ->
                            val user = if (sharedCredentials) sharedUser.trim() else item.embeddedUsername
                            val pass = if (sharedCredentials) sharedPass else item.embeddedPassword
                            GhajarOpenVpnBridge.saveImported(context, item, user, pass)
                                .onSuccess { ok++ }.onFailure { bad++ }
                        }
                        ok to bad
                    }
                    tick++
                    bulkImports = null
                    Toast.makeText(context, "${result.first} پروفایل ذخیره شد" + if (result.second > 0) " — ${result.second} ناموفق" else "", Toast.LENGTH_LONG).show()
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${imports.size} فایل معتبر" + if (bulkBad > 0) "، $bulkBad فایل نامعتبر" else "")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("نام کاربری و رمز همه یکی است", modifier = Modifier.weight(1f))
                    SkinSwitch(checked = sharedCredentials, onCheckedChange = { sharedCredentials = it })
                }
                if (sharedCredentials) {
                    OutlinedTextField(sharedUser, { sharedUser = it }, label = { Text("نام کاربری مشترک") }, singleLine = true)
                    OutlinedTextField(sharedPass, { sharedPass = it }, label = { Text("رمز عبور مشترک") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (needsCredentials && (sharedUser.isBlank() || sharedPass.isBlank()))
                        Text("فایل‌های دارای احراز هویت بدون این اطلاعات فقط ذخیره می‌شوند و تا تکمیل حساب قابل تست نیستند.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                } else Text("پروفایل‌ها ذخیره می‌شوند و می‌توانی حساب هرکدام را جدا با مداد وارد کنی.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    editing?.let { profile ->
        GlassDialog(
            onDismiss = { editing = null },
            title = "ویرایش حساب OVPN",
            confirmLabel = "ذخیره",
            dismissLabel = "انصراف",
            onConfirm = {
                scope.launch {
                    // Profile deserialisation touches disk; keep it off the main thread.
                    runCatching { GhajarOpenVpnBridge.updateCredentials(context, profile.uuid, editUser.trim(), editPass.trim()) }
                        .onSuccess { tick++ }
                        .onFailure { message ->
                            Toast.makeText(context, message.message ?: "ذخیره نشد", Toast.LENGTH_LONG).show()
                        }
                }
                editing = null
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("فیلد خالی مقدار قبلی را تغییر نمی‌دهد.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(editUser, { editUser = it }, label = { Text("نام کاربری") }, singleLine = true)
                OutlinedTextField(editPass, { editPass = it }, label = { Text("رمز عبور") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            }
        }
    }
    confirmDelete?.let { profile ->
        GlassDialog(
            onDismiss = { confirmDelete = null },
            title = "حذف پروفایل OVPN",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = {
                scope.launch {
                    runCatching { GhajarOpenVpnBridge.delete(context, profile.uuid) }
                        .onSuccess { tick++ }
                        .onFailure { message ->
                            Toast.makeText(context, message.message ?: "حذف نشد", Toast.LENGTH_LONG).show()
                        }
                }
                confirmDelete = null
            }
        ) {
            Text("«${profile.name}» از فهرست OpenVPN حذف شود؟ این کار کانفیگ‌های دیگر را عوض نمی‌کند.")
        }
    }
}

@Composable
private fun SubscriptionHeader(
    sub: Subscription,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onRemoveTimedOut: () -> Unit,
    timedOutCount: Int,
    onPing: () -> Unit,
    pinging: Boolean,
    /**
     * Renew this service, or null when there is nothing to renew.
     *
     * Only a subscription delivered for a panel account carries a service
     * username, so a hand-pasted link never shows the action - the button
     * exists exactly where it would work.
     */
    onRenew: (() -> Unit)? = null,
    /** How many configs this subscription holds, shown under its name. */
    configCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var renaming by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf(sub.name) }

    if (renaming) {
        GlassDialog(
            onDismiss = { renaming = false },
            title = t("edit_sub_name"),
            confirmLabel = t("save"),
            dismissLabel = t("cancel"),
            onConfirm = {
                val nm = draftName.trim()
                if (nm.isNotEmpty()) onRename(nm)
                renaming = false
            }
        ) {
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val ws = WindscribeBrand.isWindscribe(sub)
    val brandBrush = if (ws) windscribeCardBrush() else null

    // Was a Material card with six 21dp icon buttons crowded into one row -
    // share, speed, edit, refresh, delete and the chevron - which is most of
    // why this block read as unfinished. Three glyphs now carry what is used
    // often; the rest moved into one overflow menu. The entrance animation is
    // gone too: this is a LazyColumn item, so it re-ran every time the header
    // scrolled back into view.
    val c = ghajarColors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GhajarRadius.lg))
            .then(
                if (brandBrush != null) Modifier.background(brandBrush)
                else Modifier.background(c.secondaryCard)
            )
            .clickable { onToggle() }
            // Thinner than it was, at the owner's request. The height was in
            // the padding and the gaps between five stacked pieces - the name,
            // the action rail, the bar, the chips and the renew pill - so that
            // is what came down, not the type size or the touch targets.
            .padding(horizontal = GhajarSpacing.md, vertical = GhajarSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val chevron by animateFloatAsState(
                targetValue = if (!isOpen) 0f else if (ws) 180f else 90f,
                animationSpec = tween(360, easing = FastOutSlowInEasing),
                label = "subChevron"
            )
            if (ws) {
                Image(
                    painter = painterResource(R.drawable.windscribe),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(end = 8.dp).size(24.dp)
                        .graphicsLayer { rotationZ = chevron }
                )
            } else {
                // The chevron sits in its own tinted tile, like every other
                // leading glyph in the skin, instead of floating bare.
                Box(
                    Modifier
                        .padding(end = GhajarSpacing.sm)
                        .size(30.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(c.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = chevron }
                    )
                }
            }
            // The name gets the row. Three glyphs used to sit on this line
            // beside it, so a subscription called after its panel host - which
            // is what every delivered one is called - arrived truncated or
            // crawling. The actions moved to their own line below, where they
            // cost height rather than the name's width.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Two lines rather than a marquee, for the same reason as the
                // config rows: the whole name at once, and no endless animation
                // per header in a scrolling list.
                val subTitle = remember(sub.total, sub.name, lang) {
                    GhajarUiRules.brandedSubscriptionTitle(
                        sub.total,
                        WindscribeBrand.displayName(sub, lang)
                    )
                }
                Text(
                    flagRuns(subTitle, LexendFont),
                    inlineContent = flagInlineContent(subTitle, 17.sp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    localizeDigits("$configCount", lang) + " " + t("count_configs"),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    maxLines = 1
                )
            }
            if (pinging) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = c.primary,
                    modifier = Modifier.padding(6.dp).size(20.dp)
                )
            }
        }

        // The actions, on their own row at a real touch size.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
        ) {
            if (!pinging) {
                SubHeaderGlyph(Icons.Filled.Speed, t("test_all")) { onPing() }
            }
            SubHeaderGlyph(Icons.Filled.Refresh, t("refresh")) { onRefresh() }
            Spacer(Modifier.weight(1f))

            Box {
                SubHeaderGlyph(Icons.Filled.MoreVert, t("more")) { shareMenu = true }
                DropdownMenu(
                    expanded = shareMenu,
                    onDismissRequest = { shareMenu = false },
                    offset = DpOffset(0.dp, 4.dp),
                    shape = RoundedCornerShape(GhajarRadius.lg),
                    containerColor = c.card,
                    border = null
                ) {
                    CompactMenuItem(Icons.Filled.ContentCopy, t("share_clipboard")) {
                        shareMenu = false
                        clipboard.setText(AnnotatedString(sub.url))
                        android.widget.Toast.makeText(context, t("copied"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                    CompactMenuItem(Icons.Filled.Share, t("share_app")) {
                        shareMenu = false
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, sub.url)
                        }
                        context.startActivity(Intent.createChooser(send, sub.name))
                    }
                    CompactMenuItem(Icons.Filled.Edit, t("edit_sub_name")) {
                        shareMenu = false
                        draftName = sub.name
                        renaming = true
                    }
                    HorizontalDivider(color = c.border)
                    DropdownMenuItem(
                        text = {
                            Text(
                                t("delete_all_configs"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = null,
                                tint = c.error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(40.dp),
                        onClick = { shareMenu = false; onRemove() }
                    )
                    DropdownMenuItem(
                        text = { Text(t("delete_timed_out"), style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Filled.TimerOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        enabled = timedOutCount > 0,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(40.dp),
                        onClick = { shareMenu = false; onRemoveTimedOut() }
                    )
                }
            }
        }

        // The bar had no number on it, so "how much is left" meant reading
        // two byte counts and dividing. The percentage sits on the bar's own
        // row, in the bar's own colour, so the warning colour and the number
        // say the same thing.
        if (sub.total > 0) {
            val remaining = (sub.total - sub.used).coerceAtLeast(0L)
            val percent = ((remaining.toDouble() / sub.total) * 100).roundToInt().coerceIn(0, 100)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
            ) {
                Box(Modifier.weight(1f)) { UsageBar(used = sub.used, total = sub.total) }
                Text(
                    localizeDigits("$percent", lang) + "٪",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = usageLevelColor(remaining, sub.total)
                )
            }
        }
        val quota = quotaChips(sub, lang)
        if (quota.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)) {
                quota.forEach { (label, level) -> QuotaChip(label, level) }
            }
        }
        // A service bought from the shop or the bot can be renewed without
        // leaving this list. It only appears on subscriptions that carry a
        // panel username, so it is never a button that leads nowhere. It is
        // emphasised once the quota or the clock is nearly out, which is the
        // moment it exists for.
        onRenew?.let { renew ->
            val nearlyOut = (sub.total > 0 &&
                (sub.total - sub.used).toFloat() / sub.total <= 0.15f) ||
                (sub.expire > 0 &&
                    (sub.expire * 1000 - System.currentTimeMillis()) / 86_400_000L <= 3L)
            if (nearlyOut) {
                PillButton(
                    t("sub_renew"),
                    onClick = renew,
                    icon = Icons.Filled.Autorenew,
                    minHeight = 42.dp
                )
            } else {
                GhostPill(
                    t("sub_renew"),
                    onClick = renew,
                    icon = Icons.Filled.Autorenew,
                    minHeight = 42.dp
                )
            }
        }

        // When the link was last fetched, to the second. A subscription that
        // silently stopped updating looks exactly like one whose numbers have
        // not changed, and this is the only thing that tells them apart.
        Text(
            if (sub.lastUpdated > 0)
                t("sub_updated_at").format(formatStamp(sub.lastUpdated, lang))
            else t("sub_never_updated"),
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted,
            maxLines = 1
        )
    }
}

/** The colour the usage bar is drawing itself in, so a number beside it agrees. */
@Composable
private fun usageLevelColor(remaining: Long, total: Long): Color {
    val frac = if (total > 0) (remaining.toFloat() / total).coerceIn(0f, 1f) else 0f
    return when {
        frac <= 0.10f -> ghajarColors.error
        frac <= 0.30f -> ghajarColors.warning
        else -> ghajarColors.primary
    }
}

/**
 * A local timestamp as date and clock, digits localised.
 *
 * Deliberately not a "2 hours ago" - the question this answers is whether the
 * refresh that just ran actually ran, and a relative label cannot say that.
 */
private fun formatStamp(millis: Long, lang: Lang): String {
    val date = java.util.Date(millis)
    val pattern = if (lang == Lang.FA) "yyyy/MM/dd - HH:mm:ss" else "yyyy-MM-dd HH:mm:ss"
    val text = java.text.SimpleDateFormat(pattern, java.util.Locale.US).format(date)
    return localizeDigits(text, lang)
}

/**
 * One action glyph in a subscription header: tinted tile, no outline.
 *
 * 42dp rather than 34: these now sit on their own row instead of stealing the
 * name's width, so there is no reason left for them to be undersized.
 */
@Composable
private fun SubHeaderGlyph(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val c = ghajarColors
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.primary.copy(alpha = 0.10f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = c.primary, modifier = Modifier.size(18.dp))
    }
}

private fun quotaChips(sub: Subscription, lang: Lang): List<Pair<String, Int>> {
    if (sub.total <= 0 && sub.expire <= 0) return emptyList()
    val parts = mutableListOf<Pair<String, Int>>()
    if (sub.total > 0) {
        val remaining = (sub.total - sub.used).coerceAtLeast(0)
        val frac = remaining.toFloat() / sub.total
        val level = when {
            frac <= 0.10f -> 2
            frac <= 0.30f -> 1
            else -> 0
        }
        parts.add(
            "${formatBytes(remaining, lang)} ${Strings.get(lang, "of")} " +
                    "${formatBytes(sub.total, lang)} ${Strings.get(lang, "left")}" to level
        )
    }
    if (sub.expire > 0) {
        val daysLeft = (sub.expire * 1000 - System.currentTimeMillis()) / 86_400_000L
        if (daysLeft >= 0) {
            val level = when {
                daysLeft <= 1L -> 2
                daysLeft <= 3L -> 1
                else -> 0
            }
            parts.add(
                "${Strings.get(lang, "expires_in")} " +
                        "${localizeDigits("$daysLeft", lang)}${Strings.get(lang, "unit_days")}" to level
            )
        }
    }
    return parts
}

@Composable
private fun QuotaChip(label: String, level: Int) {
    val accent = when (level) {
        2 -> ghajarColors.error
        1 -> ghajarColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        mixedText(label),
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
private fun UsageBar(used: Long, total: Long) {
    val remaining = (total - used).coerceAtLeast(0L)
    val frac = if (total > 0) (remaining.toFloat() / total).coerceIn(0f, 1f) else 0f
    val barColor = when {
        frac <= 0.10f -> ghajarColors.error
        frac <= 0.30f -> ghajarColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (frac > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun ChainPickerDialog(
    store: ConfigStore,
    config: ProxyConfig,
    onDismiss: () -> Unit
) {
    val t = stringsFn()
    val configs by store.configs.collectAsState()
    val options = configs.filter { it.id != config.id && it.protocol != "tor" }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        t("chain_through"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    t("chain_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TorCountryGroup(t("chain_carrier")) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item(key = "chain-none") {
                            ChainOptionRow(
                                label = t("chain_none"),
                                selected = config.chainId.isEmpty(),
                                onClick = {
                                    store.update(config.copy(chainId = ""))
                                    DebugRunner.clear(config.id)
                                    onDismiss()
                                }
                            )
                        }
                        items(options, key = { it.id }) { option ->
                            ChainOptionRow(
                                label = option.name,
                                selected = config.chainId == option.id,
                                onClick = {
                                    store.update(config.copy(chainId = option.id))
                                    DebugRunner.clear(config.id)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                BounceTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(t("cancel"))
                }
            }
        }
    }
}

@Composable
private fun ChainOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "chainRowTint"
    )
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "chainRowFill"
    )
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.05f + 0.09f * fill))
            .border(1.dp, tint.copy(alpha = 0.16f + 0.36f * fill), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            flagRuns(label, if (LocalLang.current == Lang.FA) VazirFont else LexendFont),
            inlineContent = flagInlineContent(
                label,
                MaterialTheme.typography.bodyMedium.fontSize
            ),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        SmoothCheckbox(checked = selected)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QrDialog(link: String, title: String, onDismiss: () -> Unit) {
    val t = stringsFn()
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    // Fixed on purpose - a camera has to read this, so it must not follow the
    // theme. See GhajarFixed for why this is the one documented exception.
    val qrBg = GhajarFixed.QrBackground
    val qrFg = lerp(GhajarFixed.QrForeground, accent, 0.06f)
    val bmp = remember(link, qrBg, qrFg) {
        ConfigShare.qrBitmap(link, darkColor = qrFg.toArgb(), lightColor = qrBg.toArgb())
    }

    val pulseTr = rememberInfiniteTransition(label = "qrPulse")
    val strokeAlpha by pulseTr.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.55f,
        animationSpec = ghajarEndless(infiniteRepeatable(
            tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )),
        label = "qrStroke"
    )

    fun shareImage() {
        val image = bmp ?: return
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "ghajarvpn-qr.png")
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, title))
        }
    }

    GlassDialog(
        onDismiss = onDismiss,
        title = t("qr_title"),
        confirmLabel = if (bmp == null) t("cancel") else t("share"),
        dismissLabel = if (bmp == null) null else t("cancel"),
        onConfirm = { if (bmp == null) onDismiss() else shareImage() }
    ) {
        if (bmp == null) {
            Text(
                mixedText(t("qr_too_long")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                mixedText(title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(240.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(qrBg)
                        .border(
                            1.dp,
                            accent.copy(alpha = strokeAlpha),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(12.dp)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                mixedText(t("qr_hint")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QrScannerScreen(onResult: (String) -> Unit) {
    val t = stringsFn()
    val context = LocalContext.current
    val lifecycleOwner = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<LifecycleOwner>()
            .firstOrNull()
    }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var handled by remember { mutableStateOf(false) }
    var galleryError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !handled) scope.launch {
            galleryError = null
            val text = withContext(Dispatchers.IO) { decodeQrFromGallery(context, uri) }
            if (!text.isNullOrBlank()) {
                handled = true
                onResult(text)
            } else {
                galleryError = "QR معتبری در این تصویر پیدا نشد"
                Toast.makeText(context, galleryError, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(Modifier.fillMaxSize()) {
        if (!granted) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(46.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    mixedText(t("camera_needed")),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                BounceOutlinedButton(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(t("camera_grant"))
                }
                Spacer(Modifier.height(10.dp))
                BounceOutlinedButton(onClick = { galleryPicker.launch("image/*") }) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("انتخاب QR از گالری")
                }
                galleryError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            return@Column
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val view = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    android.util.Log.d("GhajarQr", "factory: creating PreviewView")
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        runCatching {
                            val provider = providerFuture.get()
                            android.util.Log.d("GhajarQr", "provider ready, owner=$lifecycleOwner")
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(view.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            val reader = MultiFormatReader().apply {
                                setHints(
                                    mapOf(
                                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                        DecodeHintType.TRY_HARDER to true
                                    )
                                )
                            }
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                                if (!handled) {
                                    decodeQr(proxy, reader)?.let { text ->
                                        handled = true
                                        view.post { onResult(text) }
                                    }
                                }
                                proxy.close()
                            }
                            if (lifecycleOwner == null) {
                                android.util.Log.e("GhajarQr", "no LifecycleOwner found - cannot bind")
                            } else {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                                android.util.Log.d("GhajarQr", "camera bound")
                            }
                        }.onFailure {
                            android.util.Log.e("GhajarQr", "camera setup failed", it)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    view
                }
            )
            Box(
                Modifier.size(214.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
            )
        }

        Text(
            mixedText(t("scan_qr_hint")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
        )
        BounceOutlinedButton(
            onClick = { galleryPicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp)
        ) {
            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("انتخاب QR از گالری")
        }
        galleryError?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
    }
}

private fun decodeQrFromGallery(context: Context, uri: Uri): String? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (kotlin.math.max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, options)
    } ?: return@runCatching null
    try {
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
        }
        fun decodeBitmap(sourceBitmap: Bitmap): String? {
            val pixels = IntArray(sourceBitmap.width * sourceBitmap.height)
            sourceBitmap.getPixels(pixels, 0, sourceBitmap.width, 0, 0, sourceBitmap.width, sourceBitmap.height)
            val source = com.google.zxing.RGBLuminanceSource(sourceBitmap.width, sourceBitmap.height, pixels)
            reader.reset()
            return runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
        }
        decodeBitmap(bitmap) ?: sequenceOf(90f, 180f, 270f).mapNotNull { degrees ->
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                android.graphics.Matrix().apply { postRotate(degrees) }, true)
            try { decodeBitmap(rotated) } finally { if (rotated !== bitmap) rotated.recycle() }
        }.firstOrNull()
    } finally {
        bitmap.recycle()
    }
}.getOrNull()

private fun decodeQr(proxy: ImageProxy, reader: MultiFormatReader): String? {
    val buffer = proxy.planes[0].buffer
    val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val source = PlanarYUVLuminanceSource(
        data, proxy.planes[0].rowStride, proxy.height,
        0, 0, proxy.width, proxy.height, false
    )
    val attempt = { src: LuminanceSource ->
        runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text }
            .getOrNull()
    }
    reader.reset()
    return attempt(source) ?: run {
        reader.reset()
        attempt(source.invert())
    }
}

@Composable
private fun ConfigRow(
    config: ProxyConfig,
    isSelected: Boolean,
    isActive: Boolean,
    ping: PingResult?,
    selectionMode: Boolean,
    isChecked: () -> Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareFile: () -> Unit,
    onChain: () -> Unit,
    actionsOpen: Boolean,
    onToggleActions: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The entrance animation. Off by default on purpose.
     *
     * In a LazyColumn an item is composed when it scrolls in and disposed when
     * it scrolls out, so `remember` is lost and the animation fires again every
     * time a row comes back. That is three concurrent float animations plus a
     * graphicsLayer per visible row for the whole of every scroll - rows fading
     * and sliding while you drag, which reads as exactly the stutter it is.
     * A screen entering can ask for it; a recycled list item should not.
     */
    appear: Boolean = false,
    containerColor: Color? = null,
    conn: Connection = Connection.DISCONNECTED,
    onToggleConnection: (() -> Unit)? = null,
    onToggleFavorite: () -> Unit = {}
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var shareMenu by remember { mutableStateOf(false) }
    var qrFor by remember { mutableStateOf<String?>(null) }
    val checked by remember { derivedStateOf { isChecked() } }

    qrFor?.let { link ->
        QrDialog(link = link, title = GhajarUiRules.brandedConfigName(config.name), onDismiss = { qrFor = null })
    }

    val c = ghajarColors
    // Compact rows drop the endpoint line and tighten the padding, which is
    // where a row's height actually goes. Two columns were the other option
    // and were rejected: this list maps a drag's y position to a row id for
    // paint-selection, and a second column makes that mapping select the
    // wrong servers - silently, which is the worst way for it to be wrong.
    val compact = LocalListDensity.current == ListDensity.TWO
    // Selection is a low-alpha brand wash rather than a filled container, so a
    // long list of selected rows stays readable instead of turning into a block
    // of solid colour.
    val highlight by animateColorAsState(
        targetValue = when {
            checked || isSelected -> c.primary.copy(alpha = 0.16f)
            // The row actually carrying traffic. A 3dp accent bar is easy to
            // miss in a long list; the wash is the same signal at a glance,
            // and lighter than the selected one so the two stay distinct.
            isActive -> c.primary.copy(alpha = 0.09f)
            containerColor != null -> containerColor
            else -> Color.Transparent
        },
        animationSpec = tween(220),
        label = "rowHighlight"
    )

    val swipeRed = c.error
    var rowWidth by remember { mutableStateOf(1) }
    var dragX by remember { mutableStateOf(0f) }
    val dragEnabled = !selectionMode && !checked

    val swiping = dragX < -6f
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(swiping) {
        if (swiping) {
            runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            buzz(context)
        }
    }
    LaunchedEffect(dragEnabled) {
        if (!dragEnabled) dragX = 0f
    }

    val rowTint by animateColorAsState(
        targetValue = if (swiping) swipeRed else highlight,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "rowTint"
    )

    // The row is two tiers, and that is the whole of this rebuild.
    //
    // It used to be one horizontal run: status dot, name, protocol, endpoint,
    // ping, then up to seven icon buttons - all competing for the same width.
    // The name lost every time, so a server called "Ghajarvpn • Psiphon"
    // arrived as a marquee crawling through a 90dp gap, and opening the
    // actions squeezed it to nothing. The fix is not a smaller font.
    //
    // Tier one is the name and only the two controls that are always there.
    // Tier two is the protocol, the endpoint and the measurement. The five
    // occasional actions open as a third row underneath, so revealing them
    // costs height - which this list has - instead of the name's width, which
    // it does not.
    val rowShape = RoundedCornerShape(GhajarRadius.lg)
    Box(
        (if (appear) modifier.appearOnce() else modifier)
            .fillMaxWidth()
            .onSizeChanged { rowWidth = it.width }
            .offset { IntOffset(dragX.roundToInt(), 0) }
            .clip(rowShape)
            .background(containerColor ?: c.secondaryCard)
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = dragEnabled,
                state = rememberDraggableState { delta ->
                    dragX = (dragX + delta).coerceIn(-rowWidth.toFloat(), 0f)
                },
                onDragStopped = {
                    if (-dragX >= rowWidth * (1f / 3f)) {
                        runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                        buzz(context)
                        animate(
                            initialValue = dragX,
                            targetValue = -rowWidth.toFloat(),
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) { value, _ -> dragX = value }
                        onDelete()
                    } else {
                        animate(
                            initialValue = dragX,
                            targetValue = 0f,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) { value, _ -> dragX = value }
                    }
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .background(rowTint)
    ) {
        // The leading accent bar on the row carrying traffic: drawn, not laid
        // out. A Box child with fillMaxHeight() would have measured to zero
        // here, because a LazyColumn item's height constraint is unbounded and
        // fillMaxHeight has nothing to fill against - the bar would silently
        // not exist. drawBehind runs after measurement, so it knows the real
        // height of whatever the two or three tiers came to, and it mirrors
        // itself under RTL because "leading" is the right-hand edge there.
        val barColor = if (isActive) c.primary else Color.Transparent
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (barColor == Color.Transparent) return@drawBehind
                    val w = 3.dp.toPx()
                    val inset = 10.dp.toPx()
                    val h = (size.height - inset * 2).coerceAtLeast(w)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(
                            x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f,
                            y = (size.height - h) / 2f
                        ),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(w / 2f)
                    )
                }
                // Thinner than it was, at the owner's request. A row's height
                // is its vertical padding plus the gap between its two tiers,
                // and both were sized for a list read one row at a time rather
                // than one scrolled through; the name still gets two lines.
                .padding(
                    start = GhajarSpacing.md,
                    end = GhajarSpacing.sm,
                    top = if (compact) 6.dp else GhajarSpacing.sm,
                    bottom = if (compact) 6.dp else GhajarSpacing.sm
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 3.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
            ) {
                // The state glyph in its own tinted tile, like every other
                // leading glyph in this skin. In selection mode it becomes the
                // checkmark, in the same tile, so a checked row does not change
                // shape or shift its text.
                Box(
                    Modifier
                        .size(if (compact) 26.dp else 30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            (if (checked) c.primary else pingColor(ping)).copy(alpha = 0.14f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                        )
                    } else {
                        LivePingDot(ping)
                    }
                }
                // The name, with the row's width to itself. Locked configs keep
                // their padlock, inline, because it explains why the endpoint
                // line below says nothing.
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (config.locked) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    // Wrapped, not scrolling. A marquee shows a long name one
                    // chunk at a time and you have to wait for the rest, and it
                    // is an endless Animatable per row that overflows - which,
                    // with the old 90dp name box, was nearly every visible row
                    // animating at once while you scrolled. Two lines show the
                    // whole name at once and cost nothing per frame. Flags still
                    // render inline, which is the one thing MarqueeName was
                    // carrying that a plain Text would have dropped.
                    val shown = remember(config.name) {
                        GhajarUiRules.brandedConfigName(config.name)
                    }
                    Text(
                        flagRuns(shown, LexendFont),
                        inlineContent = flagInlineContent(shown, 15.sp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) c.primary else c.textPrimary,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Connect/disconnect stays on the name's line: it is the reason
                // the row exists, and it must not move when the actions open.
                if (!checked && !selectionMode && onToggleConnection != null) {
                    val connectedHere = isActive && conn == Connection.CONNECTED
                    val connectingHere = isActive && conn == Connection.CONNECTING
                    RowAction(
                        icon = when {
                            connectedHere -> Icons.Filled.Stop
                            connectingHere -> Icons.Filled.Autorenew
                            else -> Icons.Filled.PlayArrow
                        },
                        label = if (connectedHere) t("disconnect") else t("connect"),
                        tint = if (connectedHere) c.error else c.primary,
                        enabled = !connectingHere,
                        onClick = { onToggleConnection() }
                    )
                }
                if (!checked && !selectionMode) {
                    Box(
                        Modifier.size(if (compact) 30.dp else 34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = swiping,
                            enter = fadeIn(tween(150)) + scaleIn(tween(180), initialScale = 0.65f),
                            exit = fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.65f)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = t("delete"),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !swiping,
                            enter = fadeIn(tween(150)) + scaleIn(tween(180), initialScale = 0.65f),
                            exit = fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.65f)
                        ) {
                            RowAction(
                                icon = Icons.Filled.MoreVert,
                                label = t("more"),
                                tint = if (actionsOpen) c.primary else c.textSecondary,
                                onClick = onToggleActions
                            )
                        }
                    }
                }
            }

            // Tier two: what this server is and how it measured. Compact keeps
            // it - dropping the whole line was what made a compact row
            // indistinguishable from the one above it - but drops the endpoint,
            // which is the long half and one tap away in edit.
            Row(
                Modifier.fillMaxWidth().padding(start = if (compact) 36.dp else 42.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
            ) {
                ProtocolTag(config.protocol, isActive)
                if (!compact) {
                    Text(
                        if (config.locked) AnnotatedString(t("locked_config"))
                        else scriptRuns("${config.address}:${config.port}", LexendFont),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isActive) c.primary else c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(Modifier.weight(1f))
                PingChip(ping)
            }

            // Tier three, on demand. Five actions at a real touch size, evenly
            // spread, with the destructive one last and in the error colour.
            AnimatedVisibility(
                visible = actionsOpen && !checked && !selectionMode,
                enter = fadeIn(tween(200)) + expandVertically(tween(260, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing))
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp)
                            .height(1.dp)
                            .background(c.border)
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RowAction(
                            icon = if (config.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            label = t("picker_favourites"),
                            tint = if (config.favorite) c.premium else c.textSecondary,
                            onClick = onToggleFavorite
                        )
                        Box {
                            RowAction(
                                icon = Icons.Filled.Share,
                                label = t("share"),
                                tint = c.primary,
                                onClick = { shareMenu = true }
                            )
                            DropdownMenu(
                                expanded = shareMenu,
                                onDismissRequest = { shareMenu = false },
                                shape = RoundedCornerShape(GhajarRadius.lg),
                                containerColor = c.card,
                                border = null
                            ) {
                                if (!config.locked) {
                                    CompactMenuItem(Icons.Filled.ContentCopy, t("share_clipboard")) {
                                        shareMenu = false
                                        clipboard.setText(AnnotatedString(ConfigShare.toLink(config)))
                                        android.widget.Toast.makeText(context, t("copied"), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    CompactMenuItem(Icons.Filled.Share, t("share_app")) {
                                        shareMenu = false
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, ConfigShare.toLink(config))
                                        }
                                        context.startActivity(Intent.createChooser(send, config.name))
                                    }
                                    CompactMenuItem(Icons.Filled.QrCode2, t("qr_share")) {
                                        shareMenu = false
                                        qrFor = ConfigShare.toLink(config)
                                    }
                                }
                                CompactMenuItem(Icons.Filled.InsertDriveFile, t("share_file")) {
                                    shareMenu = false
                                    onShareFile()
                                }
                            }
                        }
                        RowAction(
                            icon = Icons.Filled.Layers,
                            label = t("chain_through"),
                            tint = if (config.chainId.isNotEmpty()) c.primary else c.textSecondary,
                            onClick = onChain
                        )
                        RowAction(
                            icon = Icons.Filled.Edit,
                            label = t("edit"),
                            tint = c.primary,
                            onClick = onEdit
                        )
                        RowAction(
                            icon = Icons.Filled.Delete,
                            label = t("delete"),
                            tint = c.error,
                            onClick = onDelete
                        )
                    }
                }
            }
        }
    }
}

/**
 * One tool on the picker's glyph rail: a square tinted tile, on when active.
 *
 * These were 42dp outlined buttons sharing a row with labelled ones, which is
 * what made the toolbar wrap into three ragged lines. Same size for all five,
 * and the tint is the state - a filled tile means the filter or the ordering
 * it stands for is currently on.
 */
@Composable
private fun PickerTool(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val c = ghajarColors
    val accent = when {
        !enabled -> c.onDisabled
        destructive -> c.error
        active -> c.highlight
        else -> c.primary
    }
    val fill by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "pickerToolFill"
    )
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(accent.copy(alpha = 0.10f + 0.16f * fill))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(19.dp))
    }
}

/**
 * One icon action in a config row, at a touch size that can actually be hit.
 *
 * The old rows drew these as a bare 21dp icon with 4dp of padding - a 29dp
 * target, under the 48dp minimum, five of them side by side. This is a 36dp
 * circle around a 20dp glyph, which is still compact and is no longer a game
 * of skill.
 */
@Composable
private fun RowAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) tint else ghajarColors.onDisabled,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onShareApp: () -> Unit,
    onShareFile: () -> Unit,
    onDelete: () -> Unit
) {
    val t = stringsFn()
    val lang = LocalLang.current
    var shareMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Close, contentDescription = t("cancel"),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.clip(CircleShape).clickable { onClose() }.padding(9.dp).size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "${localizeDigits("$count", lang)} ${t("selected")}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Box {
                Icon(Icons.Filled.Share, contentDescription = t("share"),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clip(CircleShape).clickable { shareMenu = true }.padding(9.dp).size(26.dp))
                DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                    CompactMenuItem(Icons.Filled.ContentCopy, t("share_clipboard")) { shareMenu = false; onCopy() }
                    CompactMenuItem(Icons.Filled.Share, t("share_app")) { shareMenu = false; onShareApp() }
                    CompactMenuItem(Icons.Filled.InsertDriveFile, t("share_file")) { shareMenu = false; onShareFile() }
                }
            }
            Icon(Icons.Filled.Delete, contentDescription = t("delete"),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.clip(CircleShape).clickable { onDelete() }.padding(9.dp).size(26.dp))
        }
    }
}

private val PersianRange = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")

internal fun scriptFont(text: String): FontFamily =
    if (PersianRange.containsMatchIn(text)) VazirFont else LexendFont

private fun isPersianChar(c: Char) =
    c in '\u0600'..'\u06FF' || c in '\u0750'..'\u077F' ||
            c in '\uFB50'..'\uFDFF' || c in '\uFE70'..'\uFEFF'

private fun buzz(context: Context) {
    android.util.Log.d("GhajarHaptic", "buzz() called")
    runCatching {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        if (!vibrator.hasVibrator()) {
            android.util.Log.w("GhajarHaptic", "device reports no vibrator")
            return
        }
        val amplitude = if (vibrator.hasAmplitudeControl()) 255
        else android.os.VibrationEffect.DEFAULT_AMPLITUDE
        val effect = android.os.VibrationEffect.createOneShot(50, amplitude)
        vibrator.cancel()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                android.os.VibrationAttributes.createForUsage(
                    android.os.VibrationAttributes.USAGE_HARDWARE_FEEDBACK
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
    }
}

@Composable
internal fun monoFont(): FontFamily =
    if (LocalLang.current == Lang.FA) VazirFont else MonoFont

@Composable
internal fun monoLatinFont(): FontFamily =
    if (LocalLang.current == Lang.FA) LexendFont else MonoFont

@Composable
internal fun monoText(text: String): AnnotatedString =
    scriptRuns(text, monoLatinFont())

@Composable
internal fun accentText(text: String, vararg terms: String): AnnotatedString {
    val base = mixedText(text)
    val accent = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(base)
        for (term in terms) {
            if (term.isEmpty()) continue
            var i = text.indexOf(term)
            while (i >= 0) {
                addStyle(
                    SpanStyle(color = accent, fontWeight = FontWeight.SemiBold),
                    i,
                    i + term.length
                )
                i = text.indexOf(term, i + term.length)
            }
        }
    }
}

@Composable
internal fun mixedText(text: String): AnnotatedString =
    if (LocalLang.current == Lang.FA) scriptRuns(text, LexendFont) else AnnotatedString(text)

private fun scriptOf(c: Char): Boolean? = when {
    c in '\u06F0'..'\u06F9' -> true
    c in '\u0660'..'\u0669' -> true
    c.isLetter() -> isPersianChar(c)
    else -> null
}

internal fun scriptRuns(text: String, latin: FontFamily): AnnotatedString = buildAnnotatedString {
    if (text.isEmpty()) return@buildAnnotatedString
    var persian = text.firstNotNullOfOrNull { scriptOf(it) } ?: false
    var start = 0
    for (i in text.indices) {
        val p = scriptOf(text[i]) ?: continue
        if (p != persian) {
            withStyle(SpanStyle(fontFamily = if (persian) VazirFont else latin)) {
                append(text.substring(start, i))
            }
            start = i
            persian = p
        }
    }
    withStyle(SpanStyle(fontFamily = if (persian) VazirFont else latin)) {
        append(text.substring(start))
    }
}

@Composable
private fun MarqueeName(text: String, style: TextStyle? = null, color: Color = Color.Unspecified) {
    var containerW by remember { mutableStateOf(0) }
    var textW by remember { mutableStateOf(0) }
    val scroll = remember { Animatable(0f) }
    val density = LocalDensity.current
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val speed = with(density) { 30.dp.toPx() }
    val overflow = (textW - containerW).coerceAtLeast(0)

    LaunchedEffect(overflow, text, ltr) {
        if (overflow <= 0) { scroll.snapTo(0f); return@LaunchedEffect }
        val target = if (ltr) -overflow.toFloat() else overflow.toFloat()
        val dur = ((overflow / speed) * 1000f).toInt().coerceIn(700, 7000)
        while (true) {
            scroll.snapTo(0f)
            delay(1500)
            scroll.animateTo(target, tween(dur, easing = LinearEasing))
            delay(2000)
            scroll.animateTo(0f, tween(dur, easing = LinearEasing))
            delay(1500)
        }
    }

    Box(
        Modifier.fillMaxWidth().clipToBounds().onSizeChanged { containerW = it.width }
    ) {
        Text(
            flagRuns(text, LexendFont),
            inlineContent = flagInlineContent(text, style?.fontSize ?: 14.sp),
            style = style ?: MaterialTheme.typography.titleSmall,
            color = color,
            fontSize = if (style == null) 14.sp else TextUnit.Unspecified,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .onSizeChanged { textW = it.width }
                .graphicsLayer { translationX = scroll.value }
        )
    }
}

@Composable
private fun LivePingDot(ping: PingResult?) {
    val color = pingColor(ping)
    // The ripple only runs while a measurement is actually running. It used to
    // run on every row forever: an infinite transition per visible item, each
    // driving a graphicsLayer every frame, for a number that had already
    // settled. A pulse that never stops also stops meaning anything - now it
    // is exactly the "this one is being tested" signal.
    val measuring = ping == PingResult.Testing
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (measuring) {
            val transition = rememberInfiniteTransition(label = "pingDot")
            val ripple by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = ghajarEndless(infiniteRepeatable(tween(1700, easing = LinearEasing))),
                label = "ripple"
            )
            Box(
                Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        val sc = 0.40f + ripple * 0.60f
                        scaleX = sc; scaleY = sc
                        alpha = (1f - ripple) * 0.6f
                    }
                    .background(Brush.radialGradient(listOf(color, Color.Transparent)), CircleShape)
            )
        }
        Box(
            Modifier
                .size(16.dp)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.40f), Color.Transparent)), CircleShape)
        )
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
    }
}

/**
 * The protocol, as a quiet tag beside the endpoint.
 *
 * A server list where every row reads "name / host:port" hides the one field
 * that decides whether a row will work at all on a given network. Built-in
 * engines say so instead of naming a transport they do not have.
 */
@Composable
private fun ProtocolTag(protocol: String, active: Boolean) {
    val c = ghajarColors
    val label = protocol.trim().uppercase(java.util.Locale.ROOT).ifBlank { return }
    val tint = if (active) c.primary else c.textMuted
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
private fun PingChip(ping: PingResult?) {
    if (ping == null) return
    val t = stringsFn()
    val lang = LocalLang.current
    val target = pingColor(ping)
    val color by animateColorAsState(target, tween(400), label = "pingChipTint")
    val text = when (ping) {
        is PingResult.Ok -> "${localizeDigits("${ping.ms}", lang)} ${t("unit_ms")}"
        PingResult.Testing -> t("testing")
        else -> t("delay_failed")
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(320, easing = FastOutSlowInEasing),
        label = "pingChipAppear"
    )
    Box(
        Modifier.graphicsLayer {
            alpha = appear
            val sc = 0.85f + 0.15f * appear
            scaleX = sc
            scaleY = sc
        }
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .animateContentSize(tween(320, easing = FastOutSlowInEasing))
    ) {
        Crossfade(targetState = text, animationSpec = tween(300), label = "pingChipText") { s ->
            Text(
                s,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = if (lang == Lang.FA) VazirFont else LexendFont,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun pingColor(ping: PingResult?): Color = when (ping) {
    is PingResult.Ok -> when {
        ping.ms <= 250 -> ghajarColors.good
        ping.ms <= 600 -> ghajarColors.warning
        else -> ghajarColors.error
    }
    PingResult.Failed -> ghajarColors.textMuted
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun PingBadge(ping: PingResult?) {
    val t = stringsFn()
    val lang = LocalLang.current
    when (ping) {
        is PingResult.Ok -> Text("${localizeDigits("${ping.ms}", lang)} ${t("unit_ms")}", style = MaterialTheme.typography.bodySmall, fontFamily = if (lang == Lang.FA) VazirFont else LexendFont, color = pingColor(ping))
        PingResult.Testing -> Text("…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PingResult.Failed -> Text(t("delay_failed"), style = MaterialTheme.typography.bodySmall, color = pingColor(ping))
        null -> {}
    }
}

private data class AppEntry(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap
)

private fun perAppSummary(mode: PerAppMode, count: Int, lang: Lang): String = when (mode) {
    PerAppMode.OFF -> Strings.get(lang, "per_app_off")
    PerAppMode.ALLOWLIST -> localizeDigits("${Strings.get(lang, "per_app_allow")} · $count", lang)
    PerAppMode.BLOCKLIST -> localizeDigits("${Strings.get(lang, "per_app_block")} · $count", lang)
}

@Composable
private fun AppProxyScreen(
    store: ConfigStore,
    modifier: Modifier = Modifier
) {
    val t = stringsFn()
    val lang = LocalLang.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val mode by store.perAppMode.collectAsState()
    val selected by store.perAppList.collectAsState()
    val c = ghajarColors

    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .filter { it.packageName != context.packageName }
                .map { ai ->
                    AppEntry(
                        pkg = ai.packageName,
                        label = runCatching { pm.getApplicationLabel(ai).toString() }
                            .getOrDefault(ai.packageName),
                        icon = runCatching {
                            pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
                        }.getOrElse {
                            android.graphics.Bitmap
                                .createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                                .asImageBitmap()
                        }
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        }
    }

    Column(
        modifier.fillMaxSize().padding(GhajarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        // The three modes, each with the sentence that says what it does.
        //
        // They were three words in a segmented capsule - "خاموش · فقط
        // انتخاب‌شده · همه به‌جز" - and which way round the list worked was
        // left to the reader. Getting that backwards means either the apps you
        // wanted protected are the only ones exposed, or the reverse, and
        // nothing on screen says which happened. Each mode now states its own
        // consequence in a full sentence, and only the chosen one is filled.
        Slab(spacing = 0.dp) {
            Text(
                t("per_app_pick_mode"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = c.textSecondary
            )
            Spacer(Modifier.height(GhajarSpacing.sm))
            listOf(
                Triple(PerAppMode.OFF, t("per_app_off"), t("per_app_off_desc")),
                Triple(PerAppMode.ALLOWLIST, t("per_app_allow"), t("per_app_allow_desc")),
                Triple(PerAppMode.BLOCKLIST, t("per_app_block"), t("per_app_block_desc"))
            ).forEachIndexed { index, (value, label, desc) ->
                if (index > 0) SlabDivider()
                val on = mode == value
                SlabRow(
                    title = label,
                    subtitle = desc,
                    icon = when (value) {
                        PerAppMode.OFF -> Icons.Filled.Public
                        PerAppMode.ALLOWLIST -> Icons.Filled.CheckCircle
                        PerAppMode.BLOCKLIST -> Icons.Filled.Block
                    },
                    accent = when (value) {
                        PerAppMode.OFF -> c.info
                        PerAppMode.ALLOWLIST -> c.primary
                        PerAppMode.BLOCKLIST -> c.warning
                    },
                    onClick = { store.setPerAppMode(value) },
                    trailing = {
                        SmoothCheckbox(checked = on)
                    }
                )
            }
        }

        if (mode == PerAppMode.OFF) {
            // Off is a real answer, not an empty state, so the screen spends
            // the space saying which engines would have honoured a rule - the
            // one question this page could never answer before, and the reason
            // the setting looked broken on an OpenVPN session.
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
            ) {
                PerAppCoverage()
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(t("search_apps")) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = c.textSecondary)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.clickable { query = ""; focus.clearFocus() }
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                shape = RoundedCornerShape(GhajarRadius.md),
                modifier = Modifier.fillMaxWidth()
            )

            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    SkinLoading(t("loading_apps"))
                }
            } else {
                val filtered = remember(list, query) {
                    if (query.isBlank()) list
                    else list.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
                }
                // The count, how many are ticked, and the two bulk actions that
                // were missing - ticking forty apps one at a time was the only
                // way to use the allowlist on a phone with forty apps.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
                ) {
                    Text(
                        localizeDigits("${selected.size}", lang) + " / " +
                            localizeDigits("${filtered.size}", lang),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = c.primary
                    )
                    GhostPill(
                        text = t("per_app_select_all"),
                        onClick = { store.setPerAppList(selected + filtered.map { it.pkg }) },
                        modifier = Modifier.weight(1f)
                    )
                    GhostPill(
                        text = t("per_app_clear"),
                        onClick = { store.setPerAppList(emptySet()) },
                        enabled = selected.isNotEmpty(),
                        accent = c.error,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (selected.isEmpty()) {
                    Text(
                        t("per_app_none_picked"),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.warning
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(GhajarSpacing.sm)
                ) {
                    items(filtered, key = { it.pkg }) { app ->
                        val checked = app.pkg in selected
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(GhajarRadius.md))
                                .background(c.secondaryCard)
                                .clickable { store.togglePerApp(app.pkg) }
                                .animateItem()
                                .padding(horizontal = GhajarSpacing.md, vertical = GhajarSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                            )
                            Spacer(Modifier.width(GhajarSpacing.md))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = scriptFont(app.label),
                                    color = if (checked) c.primary else c.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    app.pkg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(GhajarSpacing.sm))
                            SmoothCheckbox(checked = checked)
                        }
                    }
                    item(key = "per-app-coverage") {
                        Column(
                            Modifier.padding(top = GhajarSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
                        ) {
                            PerAppCoverage()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Which engines actually apply the per-app rules, and which cannot.
 *
 * This exists because the honest answer used to be "most of them", and the
 * screen said nothing at all. Everything that runs on this app's own
 * VpnService gets the rules from the tun builder; OpenVPN runs in its own
 * process with its own tun and now gets them written onto the profile before
 * each session; DNS-only mode is listed as not covered rather than left out,
 * because a mode missing from a list of engines reads like an oversight.
 */
@Composable
private fun PerAppCoverage() {
    val t = stringsFn()
    val c = ghajarColors
    Slab(spacing = 0.dp) {
        Rail(t("per_app_engines"))
        SlabRow(
            title = t("per_app_engines_tun"),
            subtitle = t("per_app_engines_tun_sub"),
            icon = Icons.Filled.CheckCircle,
            accent = c.good
        )
        SlabDivider()
        SlabRow(
            title = t("per_app_engines_ovpn"),
            subtitle = t("per_app_engines_ovpn_sub"),
            icon = Icons.Filled.CheckCircle,
            accent = c.good
        )
        SlabDivider()
        SlabRow(
            title = t("per_app_engines_dns"),
            subtitle = t("per_app_engines_dns_sub"),
            icon = Icons.Filled.Block,
            accent = c.textMuted
        )
        Spacer(Modifier.height(GhajarSpacing.sm))
        Text(
            t("per_app_restart_note"),
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary
        )
        Text(
            t("per_app_in_backup"),
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted
        )
    }
}


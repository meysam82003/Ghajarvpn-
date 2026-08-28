package net.gozar.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

internal object GhajarStoryNavigation {
    private val _pending = MutableStateFlow<GhajarStoryRoute?>(null)
    val pending = _pending.asStateFlow()
    fun open(route: GhajarStoryRoute) { _pending.value = route }
    fun consumed(route: GhajarStoryRoute) { _pending.compareAndSet(route, null) }
}

@Composable
internal fun GhajarStoriesStrip(active: Boolean, refreshKey: Int) {
    val context = LocalContext.current
    val api = remember { GhajarStoreApi(context) }
    val scope = rememberCoroutineScope()
    val token = GhajarAccountStore(context).token()
    val owner = remember(token) { MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) } }
    val prefs = remember { context.getSharedPreferences("ghajar_stories", Context.MODE_PRIVATE) }
    var stories by remember(owner) { mutableStateOf<List<GhajarStory>>(emptyList()) }
    var selectedId by remember(owner) { mutableStateOf<String?>(null) }
    var seen by remember(owner) { mutableStateOf(prefs.getStringSet(owner, emptySet()).orEmpty().toSet()) }
    var failed by remember(owner) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(active, owner, refreshKey, retry) {
        if (!active || token.isBlank()) return@LaunchedEffect
        storeResult { stories = api.stories(); failed = false }.onFailure { failed = true }
    }
    if (failed && stories.isEmpty()) TextButton(onClick = { retry++ }) { Text("استوری‌ها دریافت نشدند؛ تلاش دوباره") }
    if (stories.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("داستان‌های قاجار", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("ghajar_stories")) {
            items(stories, key = { it.id }) { story ->
                Column(Modifier.width(80.dp).clip(RoundedCornerShape(18.dp)).clickable { selectedId = story.id }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(62.dp).border(if (story.id in seen) 1.dp else 3.dp,
                        if (story.id in seen) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        val bitmap by produceState<Bitmap?>(null, story.iconMedia) {
                            value = null
                            story.iconMedia?.let { url -> storeResult { value = GhajarPublicMedia.image(context, url, 160) } }
                        }
                        if (bitmap == null) Text(story.icon, style = MaterialTheme.typography.headlineSmall)
                        else Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Text(story.label, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                    if (story.tag.isNotBlank()) {
                        val tagColor = Color(android.graphics.Color.parseColor(story.tagColor))
                        val tagText = if (ghajarContrast(tagColor, Color.White) >= ghajarContrast(tagColor, Color.Black)) Color.White else Color.Black
                        Surface(color = tagColor, contentColor = tagText, shape = RoundedCornerShape(8.dp)) {
                            Text(story.tag, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
    stories.firstOrNull { it.id == selectedId }?.let { story ->
        LaunchedEffect(story.id) {
            seen = (seen + story.id).toList().takeLast(200).toSet()
            prefs.edit().putStringSet(owner, seen).apply()
            storeResult { api.viewStory(story.id) }
        }
        key(story.id) {
            GhajarStoryViewer(story, onDismiss = { selectedId = null },
                onFinish = { selectedId = stories.getOrNull(stories.indexOf(story) + 1)?.id },
                onReaction = { reaction, result -> scope.launch {
                    storeResult { api.reactStory(story.id, reaction) }
                        .onSuccess { result(true) }.onFailure { result(false) }
                } })
        }
    }
}

@Composable
internal fun GhajarStoryViewer(story: GhajarStory, onDismiss: () -> Unit, onFinish: () -> Unit,
    onReaction: (String, (Boolean) -> Unit) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(true) }
    var reactionBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val slide = story.slides[index]
    var ready by remember(index) { mutableStateOf(slide.media == null) }
    var duration by remember(index) { mutableLongStateOf(10000L) }
    var elapsed by remember(index) { mutableLongStateOf(0L) }
    var advanced by remember(index) { mutableStateOf(false) }
    fun next() { if (advanced) return; advanced = true; if (index < story.slides.lastIndex) index++ else onFinish() }
    val nextCallback by rememberUpdatedState(::next)
    LaunchedEffect(index, ready, paused, resumed) {
        if (!ready || paused || !resumed) return@LaunchedEffect
        var before = SystemClock.elapsedRealtime()
        while (elapsed < duration) {
            delay(60)
            val now = SystemClock.elapsedRealtime(); elapsed += now - before; before = now
        }
        nextCallback()
    }
    fun openExternal(url: String) {
        val valid = GhajarStoryRules.externalLink(url)
        if (valid == null) { notice = "این پیوند قابل بازکردن نیست."; return }
        paused = true
        if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(valid)).addCategory(Intent.CATEGORY_BROWSABLE)) }.isFailure)
            notice = "برنامه‌ای برای بازکردن پیوند پیدا نشد."
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF071B20)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    story.slides.indices.forEach { position -> LinearProgressIndicator(
                        progress = { if (position < index) 1f else if (position == index) (elapsed.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.weight(1f).height(3.dp), color = Color(0xFFE3BF69), trackColor = Color.White.copy(alpha = .2f)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(story.label, color = Color.White, fontWeight = FontWeight.Bold)
                        if (story.sublabel.isNotBlank()) Text(story.sublabel, color = Color.White.copy(alpha = .8f),
                            style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { paused = !paused }) { Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        if (paused) "ادامهٔ استوری" else "توقف استوری", tint = Color.White) }
                    if (slide.video) IconButton(onClick = { muted = !muted }) { Icon(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        if (muted) "روشن‌کردن صدا" else "قطع صدا", tint = Color.White) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "بستن استوری", tint = Color.White) }
                }
                key(story.id, index) {
                    GhajarStoryMedia(slide, resumed && !paused, muted, onReady = { length -> ready = true; duration = length },
                        onComplete = { nextCallback() }, onOpen = { slide.media?.let(::openExternal) },
                        modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)))
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 210.dp).verticalScroll(rememberScrollState()).padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (slide.title.isNotBlank()) Text(slide.title, color = Color.White, fontWeight = FontWeight.Bold)
                    if (slide.body.isNotBlank()) Text(slide.body, color = Color(0xFFD6E5E1), style = MaterialTheme.typography.bodySmall)
                    if (slide.code.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(slide.code)); notice = "کد کپی شد" }) { Text("${slide.code} · کپی", color = Color(0xFFFFDE91)) }
                        if (slide.codeKind.isNotBlank()) TextButton(onClick = {
                            GhajarStoryNavigation.open(if (slide.codeKind == "gift") GhajarStoryRoute(3, giftCode = slide.code)
                                else GhajarStoryRoute(0, discountCode = slide.code))
                            onDismiss()
                        }) { Text(if (slide.codeKind == "gift") "استفاده از هدیه" else "خرید با تخفیف", color = Color.White) }
                    }
                    if (slide.ctaLabel.isNotBlank() && slide.ctaLink.isNotBlank()) {
                        val color = Color(android.graphics.Color.parseColor(slide.ctaColor))
                        val foreground = if (ghajarContrast(color, Color.White) >= ghajarContrast(color, Color.Black)) Color.White else Color.Black
                        Button(onClick = {
                            val route = GhajarStoryRules.route(slide.ctaLink)
                            if (route != null) { GhajarStoryNavigation.open(route); onDismiss() } else openExternal(slide.ctaLink)
                        }, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = foreground),
                            modifier = Modifier.fillMaxWidth()) { Text(slide.ctaLabel) }
                    }
                    slide.attachments.forEach { attachment -> TextButton(onClick = { openExternal(attachment.url) }) {
                        Text("دریافت ${attachment.name}", color = Color(0xFFFFDE91))
                    } }
                    notice?.let { Text(it, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("heart" to "❤️", "fire" to "🔥", "party" to "🎉", "gift" to "🎁").forEach { (key, label) ->
                        TextButton(enabled = !reactionBusy, onClick = {
                            reactionBusy = true
                            onReaction(key) { success -> reactionBusy = false; notice = if (success) "واکنش ثبت شد" else "واکنش ثبت نشد؛ دوباره تلاش کن." }
                        }) { Text(label, style = MaterialTheme.typography.titleLarge) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { if (index > 0) index-- }, enabled = index > 0) { Text("قبلی", color = Color.White) }
                    TextButton(onClick = ::next) { Text(if (index < story.slides.lastIndex) "بعدی" else "استوری بعد", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun GhajarStoryMedia(slide: GhajarStorySlide, playing: Boolean, muted: Boolean,
    onReady: (Long) -> Unit, onComplete: () -> Unit, onOpen: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var video by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    val ready by rememberUpdatedState(onReady)
    val complete by rememberUpdatedState(onComplete)
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    LaunchedEffect(slide.media) {
        val url = slide.media ?: return@LaunchedEffect
        try {
            if (slide.video) video = GhajarPublicMedia.file(context, url, video = true)
            else { bitmap = GhajarPublicMedia.image(context, url); ready(10000L) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
    }
    LaunchedEffect(player, muted, playing) {
        runCatching {
            player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
            if (playing) view?.start() else view?.pause()
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { view?.stopPlayback() }; player = null; view = null } }
    Box(modifier.background(Color(android.graphics.Color.parseColor(slide.background))), contentAlignment = Alignment.Center) {
        val mediaModifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = slide.scale; scaleY = slide.scale; rotationZ = slide.rotation
            translationX = size.width * slide.x / 100; translationY = size.height * slide.y / 100
        }
        bitmap?.let { Image(it.asImageBitmap(), slide.title, mediaModifier, contentScale = ContentScale.Fit) }
        video?.let { file -> AndroidView(modifier = mediaModifier, factory = { ctx -> VideoView(ctx).also { videoView ->
            view = videoView
            videoView.setOnPreparedListener { mp ->
                player = mp; mp.isLooping = false
                mp.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                ready(mp.duration.toLong().coerceAtLeast(1000L))
                if (playing) videoView.start()
            }
            videoView.setOnCompletionListener { complete() }
            videoView.setOnErrorListener { _, _, _ -> failed = true; true }
            videoView.setVideoPath(file.absolutePath)
        } }) }
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("رسانه در اپ پخش نشد یا حجمش زیاد است.", color = Color.White)
            TextButton(onClick = onOpen) { Text("بازکردن رسانه در مرورگر", color = Color.White) }
        } else if (slide.media != null && bitmap == null && video == null) CircularProgressIndicator(color = Color.White)
    }
}

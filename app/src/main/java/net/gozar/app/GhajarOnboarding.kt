package net.gozar.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * First-install setup: language, light/dark, theme, home cards, connect
 * button, navigation and server cards, then an optional one-minute tour.
 * Seven steps, all skippable, every choice saved the moment it is made and
 * all of them changeable later in Settings -> Personalization.
 *
 * Shown only on a fresh install (or after clearing data). Someone updating
 * from 1.0.10 already has an app the way they like it and is never sent
 * through it.
 */
object Onboarding {
    private const val PREFS = "ghajar_onboarding"
    const val STEPS = 7

    fun shouldShow(context: Context, store: ConfigStore): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("done", false)) return false
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val freshInstall = info == null || info.firstInstallTime == info.lastUpdateTime
        val untouched = store.configs.value.isEmpty() && store.subscriptions.value.isEmpty()
        if (!freshInstall || !untouched) { markDone(context); return false }
        return true
    }

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("done", true).remove("step").apply()
    }

    /** The step to resume at after process death. */
    fun savedStep(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("step", 0)
    fun saveStep(context: Context, step: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("step", step).apply()
}

private val TOUR = listOf(
    "اتصال" to "دکمهٔ بزرگ وسط صفحهٔ اصلی وصل و قطع می‌کند. نگه‌داشتنش وقتی وصل است، اتصال را از نو برقرار می‌کند.",
    "انتخاب سرور" to "کارت سرور زیر دکمه را بزن تا فهرست سرورها، پینگ و تست واقعی هر کدام را ببینی.",
    "افزودن سرور" to "در فهرست سرورها دکمهٔ + : لینک، QR، فایل، یا فرم پروتکل‌ها (OpenVPN، IKEv2، WireGuard، Psiphon، Tor و …).",
    "اشتراک (Subscription)" to "لینک اشتراک را یک بار اضافه کن؛ با باز شدن برنامه خودکار به‌روز می‌شود.",
    "فروشگاه" to "زبانهٔ فروشگاه: خرید، تمدید، کیف پول و پشتیبانی.",
    "اشتراک‌گذاری" to "تنظیمات ← اشتراک‌گذاری: کانفیگ برای دستگاه دیگر، یا اتصال همین گوشی از طریق هات‌اسپات با رمز.",
    "تنظیمات" to "همه چیز اینجا قابل تغییر است؛ از جستجوی بالای تنظیمات استفاده کن."
)

@Composable
fun OnboardingWizard(store: ConfigStore, onDone: () -> Unit) {
    val context = LocalContext.current
    val c = ghajarColors
    val look by GhajarLookStore.load(context).collectAsState()
    val lang by store.lang.collectAsState()
    val uiTheme by store.uiTheme.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(Onboarding.savedStep(context).coerceIn(0, Onboarding.STEPS - 1)) }
    var tour by rememberSaveable { mutableIntStateOf(-1) }
    fun go(to: Int) { step = to; Onboarding.saveStep(context, to) }
    fun finish() { Onboarding.markDone(context); onDone() }
    fun setLook(f: (GhajarLook) -> GhajarLook) = GhajarLookStore.update(context, f)

    BackHandler(enabled = step > 0 || tour >= 0) {
        if (tour > 0) tour-- else if (tour == 0) tour = -1 else go(step - 1)
    }

    Box(
        Modifier.fillMaxSize().background(c.background)
            // Swallow taps so nothing behind the wizard reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    repeat(Onboarding.STEPS) { i ->
                        Box(Modifier.size(if (i == step) 18.dp else 7.dp, 7.dp).clip(CircleShape)
                            .background(if (i <= step) c.primary else c.border))
                    }
                }
                Text("رد کردن", color = c.textSecondary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { finish() }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            AnimatedContent(targetState = if (tour >= 0) 100 + tour else step, label = "onboarding",
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.weight(1f)) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (s) {
                        0 -> {
                            Title("زبان برنامه", "بعداً هم در تنظیمات قابل تغییر است.")
                            val sys = if (java.util.Locale.getDefault().language == "fa") Lang.FA else Lang.EN
                            Options(listOf("system" to "زبان گوشی", "fa" to "فارسی", "en" to "English"),
                                when (lang) { Lang.FA -> "fa"; else -> "en" }.let { if (lang == sys) "system" else it }) {
                                store.setLang(when (it) { "fa" -> Lang.FA; "en" -> Lang.EN; else -> sys })
                            }
                        }
                        1 -> {
                            Title("روشن، تیره یا سیستم", "همین حالا روی کل برنامه اعمال می‌شود.")
                            val mode = when {
                                look.amoled -> "amoled"
                                uiTheme == GhajarThemeId.SYSTEM -> "system"
                                uiTheme == GhajarThemeId.PREMIUM_GREEN_LIGHT -> "light"
                                else -> "dark"
                            }
                            Options(listOf("system" to "سیستم", "light" to "روشن", "dark" to "تیره", "amoled" to "AMOLED مشکی"), mode) {
                                store.setUiTheme(when (it) { "system" -> GhajarThemeId.SYSTEM; "light" -> GhajarThemeId.PREMIUM_GREEN_LIGHT; else -> GhajarThemeId.PREMIUM_GREEN_DARK })
                                setLook { l -> l.copy(amoled = it == "amoled") }
                            }
                        }
                        2 -> {
                            Title("تم و رنگ‌ها", "پیش‌فرض قاجار یا یکی از تم‌های آماده.")
                            Options(listOf(LookPreset.DEFAULT, LookPreset.EMERALD, LookPreset.MINIMAL, LookPreset.MONO, LookPreset.OCEAN, LookPreset.GOLD, LookPreset.DYNAMIC)
                                .map { it.key to it.fa }, look.preset) { key ->
                                setLook { l -> l.copy(preset = key, dynamic = key == "dynamic", accent = null) }
                            }
                            MiniHomePreview()
                        }
                        3 -> {
                            Title("صفحهٔ اصلی و کارت‌های سرعت", "جای کارت سرعت، اندازه‌اش و آمارهای نمایش‌داده‌شده.")
                            val speedPos = when {
                                "traffic" in look.homeHidden -> "off"
                                (look.homeOrder.ifEmpty { HomeParts }).indexOf("traffic") < (look.homeOrder.ifEmpty { HomeParts }).indexOf("orb") -> "top"
                                else -> "bottom"
                            }
                            Label("کارت سرعت")
                            Options(listOf("top" to "بالا", "bottom" to "پایین", "off" to "خاموش"), speedPos) { pos ->
                                setLook { l ->
                                    val order = l.homeOrder.ifEmpty { HomeParts }.filter { it != "traffic" }.toMutableList()
                                    if (pos == "top") order.add(0, "traffic") else order.add("traffic")
                                    l.copy(homeOrder = order, homeHidden = if (pos == "off") l.homeHidden + "traffic" else l.homeHidden - "traffic")
                                }
                            }
                            Label("اندازه")
                            Options(listOf("compact" to "کوچک", "normal" to "معمولی", "large" to "بزرگ"), look.homeSizes["traffic"] ?: "normal") { sz ->
                                setLook { l -> l.copy(homeSizes = l.homeSizes + ("traffic" to sz)) }
                            }
                            Label("آمار")
                            Toggle("مدت و جزئیات اتصال", "session" !in look.homeHidden) { on -> setLook { l -> l.copy(homeHidden = if (on) l.homeHidden - "session" else l.homeHidden + "session") } }
                            Toggle("سرور، پروتکل و هسته", "facts" !in look.homeHidden) { on -> setLook { l -> l.copy(homeHidden = if (on) l.homeHidden - "facts" else l.homeHidden + "facts") } }
                            Toggle("حجم سرویس", "quota" !in look.homeHidden) { on -> setLook { l -> l.copy(homeHidden = if (on) l.homeHidden - "quota" else l.homeHidden + "quota") } }
                        }
                        4 -> {
                            Title("دکمهٔ اتصال", "جای دکمه ثابت است؛ فقط ظاهرش عوض می‌شود.")
                            Options(listOf("circle" to "کلاسیک گرد", "ring" to "حلقه", "compact" to "فشرده", "pill" to "قرص / کارت"), look.orbStyle) { st ->
                                setLook { l -> l.copy(orbStyle = st) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(Connection.DISCONNECTED, Connection.CONNECTING, Connection.CONNECTED).forEach { st ->
                                    ConnectOrb(state = st, picking = false, enabled = true, tunnelDead = false, netOffline = false,
                                        onClick = {}, styleOverride = look.orbStyle, diameter = 92.dp)
                                }
                            }
                        }
                        5 -> {
                            Title("نوار پایین و کارت سرورها", "")
                            Label("نوار پایین")
                            Options(listOf("floating" to "معمولی", "minimal" to "فشرده"), if (look.navStyle == "minimal") "minimal" else "floating") { st ->
                                setLook { l -> l.copy(navStyle = st) }
                            }
                            Toggle("نام زیر آیکن‌ها", look.navLabels) { on -> setLook { l -> l.copy(navLabels = on) } }
                            Label("کارت سرور")
                            Options(listOf("compact" to "فشرده", "list" to "معمولی", "large" to "با جزئیات"), look.serverView.takeIf { it != "grid" } ?: "list") { v ->
                                setLook { l -> l.copy(serverView = v) }
                            }
                            listOf("flag" to "پرچم", "protocol" to "پروتکل", "core" to "هسته", "ping" to "پینگ", "quality" to "وضعیت").forEach { (key, label) ->
                                Toggle(label, key in look.serverFields) { on ->
                                    setLook { l -> l.copy(serverFields = if (on) l.serverFields + key else l.serverFields - key) }
                                }
                            }
                        }
                        6 -> {
                            Title("آموزش را ببینم؟", "حدود یک دقیقه، هفت نکتهٔ کوتاه.")
                            Options(listOf("show" to "نشانم بده", "skip" to "رد کن"), "") { if (it == "show") tour = 0 else finish() }
                        }
                        else -> {
                            val i = s - 100
                            val (title, body) = TOUR[i]
                            Text(localizeDigits("${i + 1} از ${TOUR.size}", Lang.FA), color = c.textMuted, style = MaterialTheme.typography.labelMedium)
                            Title(title, "")
                            Text(body, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val canBack = step > 0 || tour >= 0
                if (canBack) GhostPill(text = "قبلی", fillWidth = false, onClick = {
                    if (tour > 0) tour-- else if (tour == 0) tour = -1 else go(step - 1)
                })
                Spacer(Modifier.weight(1f))
                GhostPill(
                    text = when { tour == TOUR.lastIndex -> "پایان"; tour >= 0 -> "بعدی"; step == Onboarding.STEPS - 1 -> "شروع"; else -> "بعدی" },
                    accent = c.primary, fillWidth = false,
                    onClick = {
                        when {
                            tour == TOUR.lastIndex -> finish()
                            tour >= 0 -> tour++
                            step == Onboarding.STEPS - 1 -> finish()
                            else -> go(step + 1)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Title(title: String, sub: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ghajarColors.textPrimary)
    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = ghajarColors.textSecondary)
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = ghajarColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Options(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = ghajarColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (on) c.primary.copy(alpha = 0.14f) else c.secondaryCard)
                    .clickable { onSelect(key) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, modifier = Modifier.weight(1f), color = if (on) c.primary else c.textPrimary,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) c.primary else Color.Transparent))
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = ghajarColors.textPrimary)
        SkinSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** A few blocks in the live palette, so a theme is seen before it is kept. */
@Composable
private fun MiniHomePreview() {
    val c = ghajarColors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.primary))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(c.card, c.secondaryCard).forEach { col ->
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(col))
            }
        }
    }
}

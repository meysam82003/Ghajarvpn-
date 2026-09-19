package net.gozar.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch

@Composable
fun GhajarNotificationSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var channelRevision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (enabled) scope.launch { GhajarNotificationMonitor.refresh(context.applicationContext) }
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        channelRevision++
        if (enabled) scope.launch { GhajarNotificationMonitor.refresh(context.applicationContext) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (enabled) "اعلان‌های گوشی فعال هستند" else "برای دریافت پیام‌ها خارج از اپ، اعلان‌های گوشی را فعال کن.")
        Text("اعلان‌های جدید و شناور اینجا و در نوار اعلانات نمایش داده می‌شوند. دریافت در پس‌زمینه ممکن است با تأخیر انجام شود.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            val prefs = context.getSharedPreferences("ghajar_notice_permission", 0)
            if (!enabled && Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("requested", false)) {
                prefs.edit().putBoolean("requested", true).apply()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else settings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, modifier = Modifier.fillMaxWidth()) { Text(if (enabled) "تنظیمات اعلان گوشی" else "فعال‌سازی اعلان گوشی") }

        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            HorizontalDivider(color = ghajarColors.border)
            Text("دسته‌های اعلان", style = MaterialTheme.typography.labelLarge)
            Text(
                "اندروید اجازه نمی‌دهد اپ‌ها به‌صورت مستقیم دسته‌ای از اعلان را خاموش کنند؛ روی هرکدام بزن تا تنظیمات همان دسته در گوشی باز شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val channels = remember(channelRevision) {
                val manager = context.getSystemService(NotificationManager::class.java)
                listOf(
                    BrandConfig.NOTIFICATION_CHANNEL_GENERAL to "اعلان‌های عمومی",
                    BrandConfig.NOTIFICATION_CHANNEL_SERVICE to "هشدار حجم و زمان سرویس",
                    BrandConfig.NOTIFICATION_CHANNEL_IMPORTANT to "اعلان‌های مهم و شناور"
                ).map { (id, label) ->
                    val on = manager?.getNotificationChannel(id)?.importance != NotificationManager.IMPORTANCE_NONE
                    Triple(id, label, on)
                }
            }
            channels.forEach { (channelId, label, on) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        settings.launch(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                        )
                    }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (on) "فعال" else "غیرفعال",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** Ask once after the account is linked, without tying message delivery to VPN use. */
@Composable
fun GhajarNotificationPermissionEffect(linked: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) scope.launch { GhajarNotificationMonitor.refresh(context.applicationContext) }
    }
    LaunchedEffect(linked) {
        if (linked && Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val prefs = context.getSharedPreferences("ghajar_notice_permission", 0)
            if (!prefs.getBoolean("requested", false)) {
                prefs.edit().putBoolean("requested", true).apply()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

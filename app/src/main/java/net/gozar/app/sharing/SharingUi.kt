package net.gozar.app.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import net.gozar.app.*
import java.io.File

@Composable
fun SharingDialog(store: ConfigStore, initial: ProxyConfig? = null, legacyExport: (() -> Unit)? = null, dismiss: () -> Unit) {
    var mode by remember { mutableStateOf(if(initial==null) 0 else 1) }
    var device by remember { mutableStateOf<DirectShare.Device?>(null) }
    var config by remember { mutableStateOf(initial) }
    var openVpnId by remember { mutableStateOf<String?>(null) }
    var subscription by remember { mutableStateOf<DirectShare.Export?>(null) }
    val configs by store.configs.collectAsState()
    val context=LocalContext.current
    val ovpn=remember { GhajarOpenVpnBridge.profiles(context) }
    var pending by remember { mutableStateOf<Pair<DirectShare.Export,String>?>(null) }
    var qr by remember { mutableStateOf<String?>(null) }
    var localQr by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val state by PhoneSharing.view.collectAsState()
    val enabled by store.vpnShareEnabled.collectAsState()
    LaunchedEffect(state.state,state.password) { if(localQr) { qr=null; localQr=false } }
    LaunchedEffect(Unit) { cleanExports(context) }
    var addresses by remember { mutableStateOf(emptyList<String>()) }
    val sharingLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(mode, sharingLifecycle) {
        if(mode>=2) sharingLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) { addresses=PhoneSharing.addresses(); delay(2000) }
        }
    }
    AlertDialog(onDismissRequest=dismiss, title={ Text("اشتراک‌گذاری اتصال") }, text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(mode==0) {
                Text("اتصال را به دستگاه دیگر منتقل کنید، یا از اتصال روشن این گوشی استفاده کنید.")
                Button(onClick={mode=1},modifier=Modifier.fillMaxWidth()) { Text("اتصال مستقیم روی دستگاه دیگر") }
                OutlinedButton(onClick={mode=2},modifier=Modifier.fillMaxWidth()) { Text("استفاده از VPN این گوشی") }
                TextButton(onClick={mode=3}) { Text("Gateway پیشرفته") }
            } else if(mode==1) {
                Text("VPN این گوشی لازم نیست روشن باشد. دستگاه مقصد خودش به سرور متصل می‌شود.")
                if(device==null) {
                    Text("دستگاه مقصد را انتخاب کنید:")
                    DirectShare.Device.entries.forEach { d -> OutlinedButton(onClick={device=d},modifier=Modifier.fillMaxWidth()) { Text(d.label) } }
                } else {
                    TextButton(onClick={device=null}) { Text("دستگاه مقصد: ${device!!.label} · تغییر") }
                    if(config==null && openVpnId==null && subscription==null) {
                        Text("کدام اتصال؟")
                        configs.filter { !it.locked }.forEach { c -> TextButton(onClick={config=c}) { Text(c.name+" · "+c.protocol) } }
                        ovpn.forEach { p -> TextButton(onClick={openVpnId=p.uuid}) { Text(p.name+" · OpenVPN") } }
                        val subs by store.subscriptions.collectAsState()
                        subs.forEach { sub -> TextButton(onClick={subscription=DirectShare.Export("Subscription", "subscription.txt", "text/plain",sub.url,true,
                            "در برنامهٔ سازگار مقصد، Add subscription را بزنید و URL را وارد کنید. لینک ممکن است token محرمانه داشته باشد.")}) { Text("اشتراک: ${sub.name}") } }
                    } else {
                        TextButton(onClick={config=null;openVpnId=null;subscription=null}) { Text("انتخاب اتصال دیگر") }
                        val exports=remember(config,openVpnId,device,configs,subscription) { runCatching {
                            config?.let { DirectShare.exports(it,device!!,configs) } ?: subscription?.let { listOf(it) } ?: listOf(DirectShare.Export("OpenVPN file", "ghajar.ovpn","application/x-openvpn-profile",
                                GhajarOpenVpnBridge.portableExport(context,openVpnId!!).getOrThrow(), note="در OpenVPN Connect یا کلاینت OpenVPN سازگار، Import file را بزنید. کلید/گواهی داخل فایل محرمانه است؛ رمز ورود ممکن است جدا لازم باشد."))
                        } }
                        exports.exceptionOrNull()?.let { Text(it.message ?: "خروجی در دسترس نیست.",color=MaterialTheme.colorScheme.error) }
                        if (exports.isFailure && config != null && device in setOf(DirectShare.Device.ANDROID,DirectShare.Device.OTHER)) {
                            TextButton(onClick = { runCatching {
                                pending=DirectShare.Export("بستهٔ کامل Ghajar", "connection.ghajar.json", "application/json", DirectShare.packageOf(config!!,configs)) to "file"
                            }.onFailure { message=it.message.orEmpty() } }) { Text("انتقال مدل کامل Ghajar بدون تبدیل استاندارد") }
                        }
                        Text(guide(device!!,config?.protocol ?: if(subscription!=null) "subscription" else "openvpn"))
                        ProtocolHelp.source(config?.protocol ?: if(subscription!=null) "subscription" else "openvpn")?.let { url ->
                            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))) } }) { Text("راهنما و دریافت کلاینت از منبع رسمی") }
                        }
                        if (legacyExport != null) TextButton(onClick = { dismiss(); legacyExport() }) { Text("خروجی فایل محافظت‌شدهٔ Ghajar") }
                        exports.getOrDefault(emptyList()).forEachIndexed { index, item ->
                            Card { Column(Modifier.padding(10.dp)) {
                                Text(item.title + if(index==0) " · پیشنهاد" else "")
                                Text(item.note,style=MaterialTheme.typography.bodySmall)
                                TextButton(onClick={pending=item to "file"}) { Text("اشتراک فایل") }
                                if(item.mime=="text/plain" && item.filename.endsWith(".txt")) TextButton(onClick={pending=item to "copy"}) { Text("کپی متن / لینک") }
                                if(item.qr) TextButton(onClick={qr=item.text}) { Text("نمایش QR در برنامهٔ سازگار") }
                            } }
                        }
                        if(exports.getOrDefault(emptyList()).isEmpty()) Text("برای این مقصد قالب قابل‌اعتماد ثبت نشده است. از مدیر سرویس فایل مخصوص کلاینت مقصد بگیرید؛ تغییر نام پسوند کافی نیست.")
                    }
                }
            } else {
                if(mode==3) Text("Gateway پیشرفته: همین relay احرازهویت‌شدهٔ TCP در دسترس است. هدایت خودکار تمام ترافیک هات‌اسپات/NAT: Requires Root / در دسترس نیست؛ به پیاده‌سازی جدا نیاز دارد و اینجا اجرا نمی‌شود. Wi-Fi Direct خودکار نیز پیاده نشده است.")
                Text("۱. VPN گوشی را وصل کنید. ۲. دستگاه دیگر را به هات‌اسپات یا همان شبکهٔ محلی وصل کنید. ۳. پراکسی را در برنامهٔ مقصد تنظیم کنید.")
                Text("وضعیت: "+stateLabel(if(enabled && state.state==PhoneSharing.State.OFF) PhoneSharing.State.VPN_REQUIRED else state.state))
                if(state.error.isNotBlank()) Text(state.error,color=MaterialTheme.colorScheme.error)
                Text("مسیرهای Xray، sing-box، Psiphon و Aether فقط با backend آماده و عبوری از تونل عرضه می‌شوند؛ OpenVPN/IKEv2 و افزونه API 1 از Direct Share استفاده می‌کنند.",style=MaterialTheme.typography.bodySmall)
                val activeProfile = configs.firstOrNull { it.id == VpnState.activeId.value }
                activeProfile?.let { net.gozar.app.engine.CapabilityRegistry.phoneSharingReason(it) }?.let { Text(it, color=MaterialTheme.colorScheme.error) }
                addresses.forEach { address -> TextButton(onClick={PhoneSharing.configure(store,enabled,address)}) { Text("انتخاب شبکه: $address") } }
                Button(onClick={PhoneSharing.configure(store,!enabled)}) { Text(if(enabled) "توقف اشتراک‌گذاری" else "شروع اشتراک‌گذاری") }
                if(state.state in setOf(PhoneSharing.State.ACTIVE,PhoneSharing.State.CLIENT_CONNECTED)) {
                    Text("IP: ${state.address}\nPort: ${state.port}\nSOCKS5 / HTTP CONNECT\nUsername: ${state.username}\nPassword: ${state.password}")
                    TextButton(onClick={ localQr=true; qr="socks5://${state.username}:${state.password}@${state.address}:${state.port}" }) { Text("QR برای Ghajar / کلاینت SOCKS5 سازگار") }
                    TextButton(onClick={PhoneSharing.configure(store,true,regenerate=true)}) { Text("تولید رمز جدید و قطع نشست‌های قبلی") }
                    Text("نشست‌های احرازهویت‌شده: ${state.clients.size} · دستگاه‌های یکتا: ${state.clients.map { it.ip }.distinct().size}")
                    state.clients.forEach { client -> Text("${client.ip} · ${(System.currentTimeMillis()-client.since)/1000}s · ↑${client.uploaded} B ↓${client.downloaded} B",style=MaterialTheme.typography.bodySmall) }
                }
                Text("نام مقصد را با Remote DNS / SOCKS5 hostname بفرستید. این پراکسی فقط TCP و HTTP CONNECT را عبور می‌دهد؛ UDP و HTTP ساده پذیرفته نمی‌شوند. تنظیم Wi-Fi اندروید معمولاً فیلد رمز ندارد؛ از برنامه‌ای با پشتیبانی احراز هویت استفاده کنید.")
                Text("قطع VPN، توقف Sharing یا تعویض سرور فوراً همهٔ نشست‌ها را می‌بندد. پس از اتصال مجدد همان موتور پشتیبانی‌شده، Sharing دوباره برقرار می‌شود. برنامهٔ مقصد نباید fallback مستقیم داشته باشد؛ ترافیک برنامه‌هایی که پراکسی را نادیده می‌گیرند تحت کنترل گوشی نیست.")
                Text("شروع یا توقف اشتراک، اتصال همین گوشی را برای اعمال درگاه به‌صورت کوتاه دوباره برقرار می‌کند.")
                Text("رمز اشتراک موقت است؛ با اتصال مجدد VPN یا پس از ۸ ساعت عوض می‌شود و نشست‌های قبلی بسته می‌شوند.")
                Text("فقط شبکهٔ محلی مورد اعتماد: احراز هویت SOCKS/HTTP خودِ ارتباط محلی را رمز نمی‌کند. از Wi-Fi با رمز WPA2/WPA3 استفاده کنید.")
                Text("آزمون روی دستگاه دوم: IP عمومی را قبل و بعد مقایسه کنید؛ باید خروجی VPN باشد. سپس VPN گوشی را قطع کنید: درخواست پراکسی باید شکست بخورد. DNS/IPv6 و برنامه‌های دیگر را جدا بررسی کنید؛ باز بودن درگاه به معنی قبولی این آزمون‌ها نیست.")
            }
            if(message.isNotBlank()) Text(message)
        }
    }, confirmButton={ TextButton(onClick=dismiss) { Text("بستن") } }, dismissButton={ if(mode!=0) TextButton(onClick={mode=0}) { Text("روش‌های اشتراک") } },
        properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
    pending?.let { (item, action) ->
        AlertDialog(onDismissRequest={pending=null},title={Text("اطلاعات محرمانه")},text={Text("این خروجی ممکن است رمز، کلید خصوصی یا token اتصال داشته باشد. فقط برای دستگاه و گیرندهٔ مورد اعتماد ارسال کنید. ${item.note}")},confirmButton={TextButton(onClick={
            runCatching {
                if(action=="copy") {
                    val clip=ClipData.newPlainText(item.title,item.text)
                    if(android.os.Build.VERSION.SDK_INT>=33) clip.description.extras=android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE",true) }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                } else shareFile(context,item)
            }.onFailure { message="اشتراک فایل انجام نشد؛ برنامهٔ دریافت‌کننده و فضای ذخیره را بررسی کنید." }
            pending=null
        }) { Text("تأیید و ادامه") }},dismissButton={TextButton(onClick={pending=null}) { Text("بازگشت") }})
    }
    qr?.let { SensitiveQr(it) { qr=null } }
}

private fun cleanExports(context: Context) {
    File(context.cacheDir,"shared").listFiles()?.filter {
        (it.name.startsWith("export-") || it.name.startsWith("qr-") || it.name=="ghajarvpn-qr.png") && System.currentTimeMillis()-it.lastModified()>3600000
    }?.forEach { it.deleteRecursively() }
}
internal fun shareFile(context: Context, item: DirectShare.Export) {
    val root=File(context.cacheDir,"shared").apply { mkdirs() }
    cleanExports(context)
    val dir=File(root,"export-"+java.util.UUID.randomUUID()).apply { mkdirs() }
    val file=File(dir,item.filename);file.writeText(item.text)
    val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type=item.mime;putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri(item.title,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },item.title))
}

@Composable
fun SensitiveQr(text:String, dismiss:()->Unit) {
    var consent by remember(text) { mutableStateOf(false) }
    var hideAfterMinute by remember { mutableStateOf(true) }
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_PAUSE) dismiss() }
        owner.lifecycle.addObserver(observer);onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(consent,hideAfterMinute) { if(consent && hideAfterMinute) { delay(60000);dismiss() } }
    AlertDialog(onDismissRequest=dismiss,title={Text("QR اتصال محرمانه")},text={Column {
        if(!consent) {
            Text("این QR شامل اطلاعات اتصال محرمانه است. فقط در برنامهٔ سازگار مقصد اسکن کنید؛ Camera آیفون آن را خودکار به تنظیمات VPN تبدیل نمی‌کند.")
            Row { Checkbox(hideAfterMinute,{hideAfterMinute=it});Text("پنهان‌کردن پس از ۶۰ ثانیه") }
        } else {
            val bmp=remember(text) { ConfigShare.qrBitmap(text) }
            if(bmp==null) Text("این داده برای QR مناسب نیست؛ فایل را انتقال دهید.") else {
                Image(bmp.asImageBitmap(),"QR",Modifier.size(260.dp))
                TextButton(onClick = {
                    runCatching {
                        val root=File(context.cacheDir,"shared").apply { mkdirs() }
                        val file=File(root,"qr-${java.util.UUID.randomUUID()}.png")
                        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                        val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="image/png";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("QR",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },"اشتراک QR محرمانه"))
                    }
                }) { Text("ارسال تصویر QR محرمانه") }
            }
        }
    }},confirmButton={TextButton(onClick={if(consent)dismiss() else consent=true}) {Text(if(consent)"بستن" else "نمایش QR")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
}
private fun stateLabel(s:PhoneSharing.State)=when(s) {
    PhoneSharing.State.OFF->"خاموش"; PhoneSharing.State.STARTING->"در حال شروع";PhoneSharing.State.ACTIVE->"فعال"
    PhoneSharing.State.VPN_REQUIRED->"VPN پشتیبانی‌شده لازم است";PhoneSharing.State.CLIENT_CONNECTED->"دستگاه متصل است"
    PhoneSharing.State.VPN_DISCONNECTED->"VPN قطع شد؛ نشست‌ها بسته شدند";PhoneSharing.State.ERROR->"خطا"
}
private fun guide(d:DirectShare.Device,p:String):String {
    val device=when(d) {
        DirectShare.Device.IOS->"iPhone/iPad: فایل را در Files دریافت و با برنامهٔ سازگار باز کنید؛ QR را از داخل همان برنامه اسکن کنید. mobileconfig فقط پس از بررسی محتوا در Settings نصب می‌شود."
        DirectShare.Device.ANDROID->"Android: در Ghajar یا کلاینت سازگار، Import file / Scan QR را انتخاب، ورود را تأیید و Connect را بزنید."
        DirectShare.Device.WINDOWS->"Windows: فایل را ذخیره، در کلاینت سازگار Import و سپس Connect را انتخاب کنید."
        DirectShare.Device.MAC->"macOS: فایل را در کلاینت سازگار Import کنید؛ پروفایل IKEv2 نیازمند تأیید نصب در تنظیمات سیستم است."
        DirectShare.Device.LINUX->"Linux: فایل را در کلاینت همان پروتکل Import کنید. دستور حاوی رمز در ترمینال پیشنهاد نمی‌شود."
        DirectShare.Device.OTHER->"از کلاینتی با پشتیبانی دقیق پروتکل و گزینه‌های این فایل استفاده کنید."
    }
    return "${ProtocolHelp.text(p)}\n$device\nاگر متصل نشد: سازگاری نسخهٔ کلاینت، اعتبار حساب، ساعت دستگاه، SNI/گواهی و تنظیمات سرور را بررسی کنید؛ بررسی گواهی را خاموش نکنید."
}

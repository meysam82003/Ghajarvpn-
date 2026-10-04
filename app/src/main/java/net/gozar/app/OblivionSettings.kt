package net.gozar.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun OblivionSettings(raw: String, onChange: (String)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val connection by VpnState.state.collectAsState()
    var emailCode by remember { mutableStateOf("") }
    val options=remember(raw){OblivionOptions(raw)}
    @Composable fun choice(label:String,key:String,values:List<Pair<String,String>>) {
        var open by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()) {
                Text("$label: ${values.firstOrNull { it.first==options.text(key) }?.second ?: options.text(key)}")
            }
            DropdownMenu(expanded=open,onDismissRequest={open=false}) {
                values.forEach { (v,name)->DropdownMenuItem(text={Text(name)},onClick={onChange(options.changed(key,v));open=false}) }
            }
        }
    }
    @Composable fun toggle(label:String,key:String) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(label,Modifier.weight(1f));SkinSwitch(options.flag(key),{onChange(options.changed(key,it.toString()))})
        }
    }
    @Composable fun field(label:String,key:String,secret:Boolean=false) {
        OutlinedTextField(options.text(key),{onChange(options.changed(key,it))},label={Text(label)},modifier=Modifier.fillMaxWidth(),
            visualTransformation=if(secret)PasswordVisualTransformation() else VisualTransformation.None)
    }
    @Composable fun section(title:String,content:@Composable ()->Unit) {
        var expanded by remember { mutableStateOf(false) }
        OutlinedButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()){Text(title+if(expanded)" ▴" else " ▾")}
        if(expanded)Column(verticalArrangement=Arrangement.spacedBy(10.dp)){content()}
    }
    choice("هسته","core",listOf("psiphon" to "سایفون","aether" to "Aether","chain" to "Aether سپس سایفون", "tor-over-aether" to "برنامه ← Tor روی Aether"))
    if(options.core in AetherTorPolicy.modes) {
        Text("زنجیره فقط TCP و DNS روی TCP دارد؛ UDP/H3/WireGuard روی Tor ارائه نمی‌شود. Bridge ساده مجاز است؛ pluggable transport و bypass مستقیم تأیید نشده‌اند. آزمون Android هنوز لازم است.")
        Text("جهت Aether H2 روی Tor به اصلاح DNS ثبت حساب در موتور و تأیید باینری نیاز دارد و فعلاً فعال نیست.")
        field("کشور خروجی Tor (اختیاری)", "torCountry")
        field("Bridge ساده (اختیاری، هر خط IP:port fingerprint)", "torBridges")
    }
    if(options.core=="chain")Text("در این حالت ابتدا Aether و سپس تونل سایفون برقرار می‌شود؛ پروتکل‌های TCP استفاده می‌شوند.")
    if(options.aether)section("پروتکل و پیدا کردن سرور") {
        choice("پروتکل","protocol",listOf("masque" to "MASQUE","wg" to "WireGuard","gool" to "gool — WireGuard تو در تو", "mim" to "mim — MASQUE تو در تو"))
        choice("انتقال MASQUE","transport",listOf("h3" to "HTTP/3","h2" to "HTTP/2"))
        choice("اسکن","scanMode",listOf("turbo","balanced","thorough","stealth","ironclad").map{it to it})
        choice("استتار","obfuscation",listOf("off","light","balanced","aggressive").map{it to it})
        choice("نسخه IP","ipVersion",listOf("v4" to "IPv4","v6" to "IPv6","both" to "هر دو"))
        field("سرور دستی (IP:port)","endpoint");field("سرور WireGuard","wgEndpoint");field("سرور HTTP/2","h2Endpoint")
        field("سرور بیرونی gool","wiwOuter");field("سرور درونی gool","wiwInner")
        field("سرور بیرونی mim", "mimOuter"); field("سرور درونی mim", "mimInner")
        field("فیلتر کشور خروجی؛ مثال DE,SE یا !IR", "exitLoc")
        Text("این فیلتر کشور خروجی را پس از اتصال بررسی می‌کند؛ پیدا شدن خروجی دلخواه تضمین نمی‌شود.")
    }
    if (options.psiphon) section("تنظیمات سایفون") {
        choice("ترجیح پروتکل", "psiphonProtocol", listOf("" to "خودکار") + PsiphonConfig.protocolChoices.map { it to it })
        field("مهلت برقراری تونل (ثانیه؛ خالی = پیش‌فرض)", "psiphonTimeout")
        toggle("گزارش عیب‌یابی", "psiphonDiagnostics")
        Text("Conduit / INPROXY در دسترس نیست: کلید معتبر اختصاصی Ghajar هنوز فراهم نشده است.")
    }
    section("شبکه، DNS و اتصال محلی") {
        choice("مسیر اتصال","routingMode",listOf("vpn" to "VPN دستگاه","proxy" to "فقط پروکسی محلی"))
        field("پورت SOCKS","socksPort");Text("پروکسی HTTP روی پورت بعدی قرار می‌گیرد.")
        toggle("دسترسی دستگاه‌های شبکه محلی","allowLan");field("MTU","mtu")
        toggle("DNS دلخواه","overrideDns");field("DNS اصلی","dnsPrimary");field("DNS دوم","dnsSecondary")
        toggle("عبور مستقیم برنامه‌های انتخابی","bypassSelected");field("نام بستهٔ برنامه‌ها؛ هرکدام یک خط","bypassedApps")
    }
    if(options.aether) {
        section("تنظیمات پیشرفتهٔ اتصال") {
            toggle("فرگمنت در HTTP/2","fragment");field("اندازه فرگمنت","fragmentSize");field("تأخیر فرگمنت","fragmentDelay")
            choice("ECH","echMode",listOf("" to "خاموش","auto" to "خودکار"));field("گروه‌های TLS","tlsGroups")
            toggle("اتصال مجدد سریع","quickReconnect");toggle("بررسی عبور داده","dataCheck")
            field("زمان بررسی داده (ثانیه)","validateSeconds");field("فاصلهٔ اتصال مجدد (ثانیه)","reconnectSeconds")
            field("WireGuard Keepalive","wgKeepalive");toggle("تلاش مجدد پروفایل WireGuard","wgProfileRetry")
            choice("کارایی","perfProfile",listOf("" to "خودکار","low" to "کم","medium" to "متوسط","high" to "زیاد"))
            choice("سطح گزارش","logLevel",listOf("error","warn","info","debug","trace").map{it to it})
            field("مسیرهای مسدود؛ هرکدام یک خط","routeBlock");field("مسیرهای مستقیم؛ هرکدام یک خط","routeDirect")
        }
        section("Cloudflare Zero Trust") {
            field("نام تیم","team");field("توکن دسترسی","accessToken",true)
            field("شناسه Service Token","accessId",true);field("رمز Service Token","accessSecret",true)
            field("ایمیل دسترسی","accessEmail")
            if(options.text("accessEmail").isNotBlank() && connection==Connection.CONNECTING) {
                OutlinedTextField(emailCode,{emailCode=it.filter(Char::isDigit).take(6)},label={Text("کد ۶ رقمی دریافت‌شده در ایمیل")},modifier=Modifier.fillMaxWidth())
                Button(enabled=emailCode.length==6,onClick={
                    context.startService(android.content.Intent(context,GozarVpnService::class.java)
                        .setAction(GozarVpnService.ACTION_AETHER_CODE).putExtra(GozarVpnService.EXTRA_AETHER_CODE,emailCode))
                    emailCode=""
                }){Text("تأیید کد ایمیل")}
            }
            toggle("Gateway Proxy","gatewayProxy")
        }
    }
}

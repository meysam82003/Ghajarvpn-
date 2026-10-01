package net.gozar.app.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.gozar.app.ProtocolForms

/** A catalog of actual entry points, with unavailable methods explicitly explained. */
@Composable
fun MethodCatalog(enabled:Boolean,onForm:(String)->Unit,onSsh:()->Unit,onDns:()->Unit,onPsiphon:()->Unit,onWarp:()->Unit) {
    var category by remember { mutableStateOf("Protocol") }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Protocol","Core","Transport","DNS","Anti-Censorship","WARP","Psiphon Methods","Experimental").chunked(2).forEach { row ->
            Row { row.forEach { name -> TextButton(onClick={category=name},modifier=Modifier.weight(1f)) { Text((if(category==name) "✓ " else "")+name) } } }
        }
        when(category) {
            "Protocol", "Core" -> {
                if(category=="Core") PluginEntryButton()
                ProtocolForms.forms.filter { if(category=="Core") it.group=="core" else it.group in setOf("vpn","proxy") }.forEach { form ->
                    OutlinedButton(enabled=enabled,onClick={onForm(form.id)},modifier=Modifier.fillMaxWidth()) {
                        Text(form.title + if(form.id=="shadowquic") " · Requires Plugin" else " · داخلی")
                    }
                }
            }
            "Transport" -> {
                Text("انتقال، نحوهٔ حمل ترافیک یک پروتکل است؛ هسته یا VPN مستقل نیست. TCP، WebSocket، gRPC و XHTTP را در تنظیمات اتصال سازگار انتخاب کنید.")
                OutlinedButton(enabled=enabled,onClick=onSsh) { Text("SSH · تنظیم Payload / TLS / WebSocket") }
                ProtocolForms.forms.filter { it.group=="dns" }.forEach { f -> OutlinedButton(enabled=enabled,onClick={onForm(f.id)}) { Text(f.title+" · تونل روی DNS") } }
            }
            "DNS" -> {
                Text("تنظیم resolver با تونل DNS تفاوت دارد. این ابزار برای resolver، آزمایش و انتخاب مسیر DNS است.")
                OutlinedButton(enabled=enabled,onClick=onDns) { Text("آزمایشگاه DNS · داخلی") }
            }
            "Anti-Censorship" -> {
                Text("ECH فقط در اتصال سازگار و با دادهٔ معتبر سرور؛ Port Hopping فقط روی سرور دارای بازهٔ پورت. Fragment موجود از تنظیمات اتصال انتخاب می‌شود. این‌ها پروتکل مستقل نیستند.")
                OutlinedButton(enabled=enabled,onClick={onForm("hysteria2")}) { Text("Hysteria2 · Port Hopping / ECH") }
                OutlinedButton(enabled=enabled,onClick={onForm("anytls")}) { Text("AnyTLS · ECH") }
            }
            "WARP" -> {
                Text("Aether داخلی: H2/H3، WireGuard، gool و mim از صفحهٔ پروژه‌های رایگان تنظیم می‌شوند. نصب افزونه لازم نیست.")
                OutlinedButton(enabled=enabled,onClick=onWarp) { Text("بازکردن پروژه‌های رایگان / Aether") }
            }
            "Psiphon Methods" -> {
                OutlinedButton(enabled=enabled,onClick=onPsiphon) { Text("Psiphon · داخلی / حالت خودکار و کشور") }
                Text("Conduit / INPROXY · Unavailable: کلید معتبر اختصاصی Ghajar موجود نیست؛ فعال‌سازی نمی‌شود.")
            }
            "Experimental" -> Text("xDNS · Unavailable: پذیرش امنیتی کامل نشده است. Aether↔Tor · Candidate: فقط طراحی، اتصال اجرایی ندارد. گزینهٔ اتصال نمایشی ارائه نمی‌شود.")
        }
    }
}

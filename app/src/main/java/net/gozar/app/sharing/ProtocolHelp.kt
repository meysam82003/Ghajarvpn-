package net.gozar.app.sharing

/** Plain-language, protocol-specific requirements. No promise of native OS support. */
object ProtocolHelp {
    private val explanations=mapOf(
        "subscription" to "اشتراک یک URL برای دریافت فهرست اتصال‌هاست، نه پروتکل VPN. token داخل URL ممکن است دسترسی به حساب بدهد؛ آن را عمومی نکنید.",
        "dnstt" to "تونل DNS؛ دامنه، کلید عمومی سرور و resolver سازگار لازم‌اند. کلید اشتباه با تعویض resolver درست نمی‌شود.",
        "masterdns" to "تونل خانوادهٔ MasterDNS؛ دامنه، کلید مشترک و resolverهای معتبر لازم‌اند. نام این روش به معنی DNS معمولی سیستم نیست.",
        "vless" to "اتصال به سرور VLESS؛ UUID، آدرس، پورت و تنظیمات TLS/Reality و انتقال باید دقیقاً با سرور یکی باشند.",
        "vmess" to "اتصال VMess؛ لینک کامل و ساعت درست دستگاه لازم است. تغییر دستی نوع انتقال ممکن است اتصال را خراب کند.",
        "trojan" to "اتصال با رمز و TLS؛ نام سرور در گواهی باید درست باشد.",
        "shadowsocks" to "پراکسی رمزگذاری‌شده؛ رمز و cipher باید با سرور مطابق باشند.",
        "wireguard" to "تونل WireGuard؛ کلید خصوصی دستگاه، کلید عمومی سرور و آدرس داخلی لازم‌اند. برای دستگاه جدید کلید جدا ترجیح دارد.",
        "amneziawg" to "WireGuard با تغییرات اختصاصی؛ تنظیمات obfuscation و نسخهٔ کلاینت باید با سرور یکی باشند.",
        "openvpn" to "VPN مبتنی بر فایل .ovpn؛ فایل می‌تواند گواهی و کلید خصوصی داشته باشد. فایل کامل و حساب معتبر لازم‌اند.",
        "ikev2" to "VPN قابل تنظیم در سیستم‌عامل‌های سازگار؛ آدرس، Remote ID و روش احراز هویت دقیق لازم‌اند. این خروجی فقط حساب EAP نام کاربری/رمز را مدل می‌کند.",
        "openconnect" to "کلاینت شبکهٔ سازمانی؛ نوع سرور مانند AnyConnect یا GlobalProtect، گروه ورود و حساب معتبر لازم است. MFA/SSO ممکن است مرحلهٔ جدا داشته باشد.",
        "hysteria" to "تونل مبتنی بر UDP؛ نسخهٔ Hysteria باید با سرور یکی باشد. پشتیبانی Hysteria2 به معنی پشتیبانی نسخهٔ ۱ نیست.",
        "hysteria2" to "تونل Hysteria2 روی UDP؛ رمز، SNI و obfuscation لازم را از مدیر بگیرید. Port Hopping فقط با بازهٔ پورت باز روی سرور کار می‌کند.",
        "tuic" to "تونل TUIC v5 روی UDP؛ UUID، رمز و TLS لازم‌اند. نسخهٔ متفاوت TUIC سازگار فرض نمی‌شود.",
        "anytls" to "پراکسی AnyTLS؛ رمز و نام صحیح TLS لازم است. ECH فقط با اطلاعات معتبر endpoint فعال می‌شود.",
        "mieru" to "پراکسی Mieru؛ حساب، بازهٔ پورت و نوع TCP/UDP را از مدیر سرور بگیرید.",
        "juicity" to "تونل Juicity؛ UUID، رمز، پورت UDP و TLS مطابق سرور لازم‌اند.",
        "shadowquic" to "پروتکل ShadowQUIC با ShadowTLS متفاوت است؛ افزونهٔ تأییدشده و سرور ShadowQUIC سازگار لازم‌اند. فعلاً APK production منتشر نشده است.",
        "plugin" to "کانفیگ وابسته به افزونه؛ فایل کامل ذخیره می‌شود، اما اتصال به افزونهٔ نصب‌شده و سازگار نیاز دارد.",
        "tor" to "شبکهٔ Tor؛ در شبکهٔ مسدود، bridge معتبر لازم است. همهٔ ترافیک UDP را پشتیبانی نمی‌کند.",
        "ssh" to "تونل SSH؛ حساب و اثرانگشت معتبر کلید میزبان لازم است. Payload یا WebSocket باید با سرور تنظیم شود.",
        "sstp" to "VPN روی TLS؛ حساب، پورت و گواهی معتبر SSTP لازم‌اند. رمزگذاری TLS جای بررسی هویت سرور را نمی‌گیرد.",
        "softether" to "اتصال SoftEther؛ حساب و نام Virtual Hub مطابق سرور لازم است.",
        "masque" to "تونل بر بستر HTTP؛ سرور باید CONNECT-IP و نسخهٔ HTTP انتخابی را پشتیبانی کند.",
        "tailscale" to "شبکهٔ خصوصی Tailscale/Headscale؛ دستگاه مقصد باید جدا عضو همان شبکه شود. کلید ثبت دستگاه را عمومی نکنید.",
        "tailcat" to "اتصال مبتنی بر DERP؛ کلیدها و تنظیمات peer مطابق سرویس لازم است.",
        "naive" to "NaiveProxy؛ سرور و حساب سازگار لازم‌اند. کلاینت معمولی HTTP لزوماً جایگزین آن نیست."
    )
    fun source(protocol:String): String? = when(protocol) {
        "wireguard" -> "https://www.wireguard.com/install/"
        "openvpn" -> "https://openvpn.net/connect-docs/import-profile.html"
        "ikev2" -> "https://support.apple.com/guide/deployment/dep4ce9487d/web"
        "vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic", "subscription" -> "https://hiddify.com/app/"
        "mieru" -> "https://github.com/enfein/mieru"
        "juicity" -> "https://github.com/juicity/juicity"
        else -> null
    }
    fun text(protocol:String):String = buildString {
        append(explanations[protocol] ?: "این روش به سرور و کلاینت سازگار خودش نیاز دارد؛ نام مشابه به معنی سازگاری نیست.")
        append("\nورود: فایل یا لینک اصلی مدیر سرویس را از «کانفیگ دارم» وارد کنید.\nانتقال: از Share دستگاه مقصد را انتخاب کنید؛ فقط قالب‌های ارائه‌شده را به برنامهٔ سازگار بدهید.\nاگر وصل نشد: اعتبار حساب، اینترنت، ساعت، پورت و گواهی سرور را بررسی کنید. بررسی گواهی را برای رفع خطا خاموش نکنید.")
    }
}

# گزارش نهایی انتشار قاجار VPN 1.1.1

| مورد | مقدار |
|---|---|
| نسخه | `1.1.1` · versionCode `30026` (قبلی: 1.0.10 · 30025) |
| شاخهٔ کار | `claude/website-design-project-files-lahgbm` |
| ABI | `arm64-v8a`، `armeabi-v7a` (APK جدا، بدون universal) |
| Xray | `xtls/xray-core v1.260327.0` (26.3.27)، طبق build info داخل `libgojni.so` |
| یادداشت انتشار | `docs/release-notes/v1.1.1.md` |

## ۱. فهرست هسته‌ها

| هسته | وضعیت در 1.1.1 |
|---|---|
| Xray | حفظ‌شده |
| sing-box | حفظ‌شده، به‌اضافهٔ پروفایل کامل `.bpf` |
| Psiphon | **حفظ‌شده.** AAR بدون تغییر است؛ انتخاب پروتکل واقعی اضافه شد. منشأ AAR در `docs/PSIPHON_AAR_PROVENANCE.md` آمده |
| Tor و PTها (obfs4، meek_lite، webtunnel، snowflake؛ lyrebird) | **حفظ‌شده** |
| Aether | **حفظ‌شده.** زنجیرهٔ Aether → Psiphon حفظ شد و QUIC در این زنجیره رد می‌شود |
| IKEv2 (strongSwan) | **حفظ‌شده** |
| OpenVPN | حفظ‌شده (فقط کتابخانهٔ تست سرعت OpenSSL حذف شد که در اپ استفاده نمی‌شد) |
| OpenConnect، WireGuard، AmneziaWG | حفظ‌شده |
| zeptun (TUN) | حفظ‌شده |
| ghajar-helper (SSH transport، AWG، Mieru، Brook، SSTP، SoftEther) | حفظ‌شده (باینری مشترک است و برای AmneziaWG لازم است) |

## ۲. اجزای حذف‌شده

DNSTT، VayDNS، NoizDNS، MasterDNS، StormDNS، CottenDNS، Slipstream، صفحهٔ DNS Lab، اسکنر و کاتالوگ DNS، حالت DNS-only، صفحهٔ «پروتکل‌های DNS»، Juicity، nDPI، `libosslspeedtest`.

موارد پاک‌شده: اسکریپت‌های build، مراحل CI، الگوهای ‎.gitignore، پوشه‌های third_party، رشته‌های متنی (۳۵۶ خط) و سرویس DNS-only از manifest. هیچ‌کدام از Psiphon، Tor/PTها، Aether و IKEv2 حذف نشدند. پروفایل‌های روش‌های حذف‌شده هم حفظ می‌شوند: ایمپورت، بکاپ و اشتراک‌گذاری‌شان کار می‌کند، اما اتصال و تست پیام «این روش در نسخه سبک 1.1.1 داخل برنامه اصلی موجود نیست.» را نشان می‌دهد.

## ۳. حجم APK

| | 1.0.10 | 1.1.1 (تخمین) |
|---|---|---|
| arm64-v8a | 114,788,659 بایت (۱۰۹٫۵ MiB) | حدود ۸۲ MiB |

حذف‌شده‌ها در arm64 بر اساس حجم‌های فشرده در APK نسخهٔ 1.0.10:

| جزء | حجم |
|---|---|
| ۷ کلاینت DNS | حدود ۱۹٫۴ MiB |
| juicity | ۴٫۲ MiB |
| osslspeedtest | ۱٫۶ MiB |
| nDPI | ۰٫۹ MiB |

**هدف ۵۰ تا ۶۰ MiB با نگه داشتن هسته‌های محافظت‌شده دست‌یافتنی نیست.** فقط libsingbox (۲۵٫۳)، libgojni با Xray و Psiphon (۱۴٫۹)، ghajar-helper (۸٫۰)، lyrebird (۵٫۸)، Aether (۴٫۰)، Tor (۳٫۸) و OpenVPN (۵٫۵) روی هم بیش از ۶۷ MiB می‌شوند. حجم دقیق بعد از build نهایی CI در همین سند به‌روز می‌شود.

## ۴. ماتریس ایمپورت

| فرمت | وضعیت |
|---|---|
| لینک‌ها (VLESS، VMess، Trojan، SS، Hysteria2، TUIC، AnyTLS، SSH و …) | پشتیبانی |
| اشتراک (Subscription) | پشتیبانی |
| Clash/Mihomo YAML، JSON سینگ‌باکس و Xray | پشتیبانی (استخراج سرورها) |
| `.bpf` پروفایل sing-box | **جدید.** کل کانفیگ (DNS، routing، selector/urltest، قوانین) حفظ و اجرا می‌شود. فرمت از سورس libbox خوانده شده و خطاهای سرآیند، نسخه، خرابی و محتوای نامعتبر از هم جدا تشخیص داده می‌شوند |
| NPVT / NPVTSUB1 / NPVS / NPVO1 (‎.npvt، .npvts، .npvs) | پشتیبانی (رمز فقط برای فایل رمزدار) |
| `.gsb2` | **جدید** (اشتراک امن قاجار) |
| WireGuard / AmneziaWG ‏`.conf`، `.ovpn` | پشتیبانی |
| روش‌های حذف‌شده | ایمپورت و حفظ، بدون اتصال |

## ۵. ماتریس اشتراک‌گذاری

| روش | وضعیت |
|---|---|
| لینک استاندارد برای Android، iPhone، iPad، Windows، macOS، Linux و Router | ✔ همراه فهرست برنامه‌ها و راهنمای مرحله‌به‌مرحله |
| فایل WireGuard ‏`.conf` | ✔ (فقط وقتی reserved اختصاصی ندارد) |
| فایل AmneziaWG | ✔ |
| کانفیگ کامل sing-box برای دسکتاپ و روتر | ✔ (برای پروتکل‌های sing-box بدون helper) |
| QR قاجار به قاجار | ✔ |
| پورتال شبکهٔ محلی | ✔ توکن ۱۲۸ بیتی، ۱۰ دقیقه اعتبار، یک‌بارمصرف، محدودیت تعداد درخواست، قفل‌شدن بعد از ۲۰ حدس اشتباه، فقط روی آدرس خصوصی، قابل لغو |
| اشتراک از طریق گوشی (SOCKS5 و HTTP) | ✔ هر دو با رمز، فقط روی آدرس هات‌اسپات، با پایان خودکار. Kill switch: بدون مسیر مستقیم و بدون دسترسی به geoip:private؛ قطع VPN = بسته‌شدن درگاه |
| GSB2 | ✔ فقط اتصال برای گیرنده؛ تاریخ پایان، مدت اعتبار و سقف حجم در گوشی گیرنده اعمال می‌شود. لغو، شمارش افراد متصل و تغییر رمز به خواست مالک محصول نیست |
| Full Gateway (همهٔ ترافیک دستگاه دیگر بدون تنظیم) | ✘ بدون روت در اندروید شدنی نیست |

اشتراک از طریق گوشی فقط برای اتصال‌های Xray، Psiphon، Tor و Aether فعال است. sing-box، OpenVPN و IKEv2 درگاه اشتراک ندارند و UI همین را صادقانه می‌گوید.

## ۶. مجوزها

حذف از manifest نهایی با `tools:node="remove"`: ACCESS_FINE/COARSE/BACKGROUND_LOCATION، RECORD_AUDIO، READ/WRITE/MANAGE_EXTERNAL_STORAGE.

مجوزهای باقی‌مانده: INTERNET، ACCESS_NETWORK_STATE، CAMERA (فقط برای QR)، POST_NOTIFICATIONS، FOREGROUND_SERVICE و SPECIAL_USE، RECEIVE_BOOT_COMPLETED، REQUEST_INSTALL_PACKAGES، PACKAGE_USAGE_STATS، QUERY_ALL_PACKAGES، VIBRATE.

## ۷. تست‌ها

- تست‌های JVM محلی (موتورها، configtoolkit، فرم‌ها، BPF، اشتراک‌گذاری، پورتال LAN با سوکت واقعی، GSB2، policy افزونه، Capability Registry): **۸۶ اجرا، ۰ خطا**.
- الگوهای redact در لاگ (Basic، لینک اشتراک، token و cookie) با اسکریپت جداگانه بررسی شدند. تست واحدشان در مجموعهٔ CI اجرا می‌شود.
- build، unit test کامل و crash-hunt شبیه‌ساز در یک چرخهٔ CI اجرا می‌شوند. نتیجه در بخش ۹ ثبت می‌شود.

## ۸. محدودیت‌های شناخته‌شده و آنچه آزمون دستگاهی نشد

- در این محیط دستگاه واقعی وجود نداشت. اتصال واقعی هر هسته، هات‌اسپات، نصب بروزرسانی و ویجت روی دستگاه **تست نشده‌اند** و PASS هم اعلام نمی‌شود. CI فقط اجرای شبیه‌ساز (crash-hunt) را دارد.
- GSB2 بدون سرور است، پس محدودیت‌هایش در خود برنامهٔ گیرنده اعمال می‌شوند.
- آیکن قابل تغییر لانچر (activity-alias) فعال نشد، چون آمادهٔ انتشار نبود. آیکن monochrome اندروید ۱۳ از قبل وجود دارد.
- افزونهٔ هسته منتشر نشده است. فقط سیاست امنیتی آن (پین گواهی ناشر، SHA-256 و rollback) پیاده و تست شده است.
- ZedSecure فقط ممیزی شد (`docs/zedsecure-audit/`) و کدی از آن وارد نشد.

## ۹. CI و انتشار

(پس از اجرای CI به‌روز می‌شود.)

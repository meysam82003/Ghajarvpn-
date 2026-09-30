# گزارش Audit نسخهٔ 1.0.10 — هسته‌ها، اندازه و کارایی

تاریخ بررسی: 2026-09-30. همهٔ مخزن‌ها با `git clone` در همین تاریخ گرفته و کد منبعشان خوانده شد؛ برای هر تصمیم commit دقیق ثبت شده است.

## ۱. ماتریس مقایسهٔ مخزن‌ها

| مخزن | قابلیت | نوع | وضعیت فعلی در قجر | نسخهٔ فعلی قجر | نسخهٔ upstream | کمبود / قدیمی / تکراری | سازگار با اندروید | مجوز | اثر روی حجم | نگهداری | تصمیم و دلیل |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Noisemux/zeptun | TUN → SOCKS (JNI) | هسته | فعال (sing-box و موتورهای SOCKS) | 2798fc0 | **5620e57 (v1.1.1)** | قدیمی بود؛ نسخهٔ جدید بودجهٔ حافظه و آزادسازی بافر بیکار دارد، رابط JNI تغییر نکرده | بله (arm64/armv7/x86_64) | MIT | ≈۰ | فعال (سپتامبر ۲۰۲۶) | **UPDATE** |
| SagerNet/sing-box (همان fork xchacha20-poly1305) | هستهٔ چندپروتکلی | هسته | فعال | 8330820 (v1.15.0-alpha.6) | **132b38e (v1.15.0-alpha.9)** | MASQUE کلاینت، HTTP/2 و HTTP/3 پروکسی، pin با certificate_sha256 نبود | بله | GPL-3.0+ | بسته به build (در CI اندازه‌گیری می‌شود) | فعال | **UPDATE** + **ADD** MASQUE (CONNECT-IP)، Tailscale endpoint و Tailcat outbound (تگ `with_tailscale`) |
| xchacha20-poly1305/sing-box | fork همان sing-box | هسته | — | — | e85872e | تکراری با upstream که حالا به alpha.9 رفته | بله | GPL-3.0+ | — | فعال | **SKIP** (تکراری؛ upstream انتخاب شد) |
| xchacha20-poly1305/sing-trusttunnel | پروتکل TrustTunnel | افزونهٔ sing-box | نیست | — | daacc80 | نیاز به patch ثبت outbound در خود sing-box (fork husi)؛ روی sing-box رسمی بدون fork کار نمی‌کند | بله | GPL-3.0 | متوسط | فعال | **SKIP** — افزودنش یعنی جایگزینی هستهٔ رسمی با fork؛ ریسک تکراری شدن موتور |
| xchacha20-poly1305/husi | کلاینت اندروید sing-box | اپ | — | — | 36c79ff | اپ کامل است نه کتابخانه؛ پروتکل‌های پلاگینش (Hysteria2, Juicity, Mieru, Naive) قبلاً در قجر هست | — | GPL-3.0 | — | فعال | **SKIP** (منبع ایده؛ همپوشانی کامل) |
| xchacha20-poly1305/sing-easyconnect | EasyConnect (Sangfor) | افزونهٔ sing-box | نیست | — | 50752d7 | فقط برای VPN سازمانی Sangfor؛ نیاز به fork sing-box | بله | GPL | متوسط | فعال | **SKIP** (کاربرد نامرتبط با کاربران قجر + fork) |
| xchacha20-poly1305/qtun-go | تونل QUIC سادهٔ SIP003 | پلاگین | نیست | — | bef5659 (۲۰۲۵-۰۶) | عملکرد با Hysteria/TUIC موجود پوشش داده شده | بله | MIT | کم | کم | **SKIP** (تکراری) |
| xchacha20-poly1305/anchor | کشف سرور LAN | ابزار | نیست | — | dd38b3b | ربطی به اتصال VPN ندارد | — | — | — | — | **SKIP** |
| CluvexStudio/Aether | WARP با MASQUE/WireGuard + اسکن | هسته | فعال (باینری کامیت‌شده 1.9.0) | 1.9.0 (باینری از پیش ساخته) | **21e7150 (v2.1.0)** | حالت MASQUE² (`--mim`)، اسکن verified، noize firewall/gfw/aggressive، `--exit-loc`، `--fragment` نبود؛ باینری بدون مسیر ساخت بود | بله (arm64/armv7/x86_64 با cargo-ndk، صفحهٔ 16KB) | AGPL-3.0 | در CI اندازه‌گیری می‌شود | فعال | **UPDATE** — حالا از سورس با commit ثابت ساخته می‌شود؛ سورس کامل 2.1.0 در `native/Aether` |
| CluvexStudio/MasterDnsVPN | تونل DNS | هسته | فعال (upstream masterking32) | acbf1c6 | acbf1c6 (= HEAD upstream) | fork فقط یک بستهٔ gomobile اضافه کرده | بله | MIT | — | فعال | **SKIP** (قجر روی آخرین upstream است) |
| CluvexStudio/psiphon-tunnel-core | Psiphon با پروفایل نویز QUIC | هسته | Psiphon رسمی داخل AAR | — | 2956a96 | تغییرات غیر-upstream؛ AAR مشترک Xray+Psiphon است | بله | GPL-3.0 | زیاد | fork | **SKIP** (جایگزینی Psiphon رسمی با fork ریسک پایداری دارد) |
| CluvexStudio/sing-openvpn | OpenVPN روی sing-box | هسته | OpenVPN با ics-openvpn/ovpn3 فعال | — | e060dda | تکراری | بله | GPL | متوسط | fork | **SKIP** (موتور تکراری) |
| bepass-org/warp-plus | WARP + Psiphon (Go) | هسته | پوشش با Aether و Psiphon | — | f70ea7e (۲۰۲۵-۱۱) | تکراری و قدیمی‌تر از Aether | بله | MIT | زیاد | کم | **SKIP** (تکراری) |
| bepass-org/Rustybird | کلاینت GTK دسکتاپ sing-box | اپ دسکتاپ | — | — | 2e1ef0e | لینوکس/GNOME، نه اندروید | خیر | BSD-3 | — | فعال | **SKIP** |
| bepass-org/bepass, smartSNI | پروکسی تکه‌تکه‌سازی SNI / سرور DNS | ابزار | fragment در Xray موجود | — | 38f7979 (۲۰۲۳) / 68fa5f8 (۲۰۲۳) | قدیمی؛ قابلیت در Xray fragment هست؛ smartSNI سمت سرور است | — | MIT | — | متوقف | **SKIP** |
| patterniha/Xray-core | fork Xray | هسته | Xray داخل AAR | v1.260327.0 | XTLS upstream v26.9.30 | fork تغییر مهمی جز merge upstream ندارد | بله | MPL-2.0 | — | فعال | **SKIP** fork؛ بروزرسانی Xray رسمی **DEFER** (پایین) |
| patterniha/dnstt, slipstream, paqet, phantun | forkهای تونل | هسته | dnstt رسمی (0c5c52a) + anonvector/dnstt، Mygod/slipstream-rust | — | 17aa1fe / 8d665fa / 94e89dd | paqet و phantun نیاز به raw socket/root دارند؛ forkهای dnstt/slipstream چیزی فراتر از نسخهٔ فعلی قجر ندارند | paqet/phantun: خیر (بدون root) | — | — | fork | **SKIP** |
| patterniha/SNI-Spoofing, QS-Tunnel, MMDF, MITM-DomainFronting | ابزارهای Python/Windows | ابزار | — | — | 13b78cf / 4497146 / a2853ec | پایتون + raw packet / WinDivert | خیر | — | — | — | **SKIP** |
| appshubcc/Bettbox | کلاینت mihomo (Flutter) | اپ | ورود Clash/Mihomo YAML موجود | — | 3189346 | اپ کامل؛ هستهٔ mihomo تکراری با sing-box/Xray | — | GPL-3.0 | زیاد | فعال | **SKIP** |

### بروزرسانی Xray (DEFER با دلیل)
Xray داخل `app/libs/ca.psiphon.aar` (gomobile مشترک با Psiphon، 186MB) قرار دارد و CI آن را نمی‌سازد. نسخهٔ فعلی v1.260327.0 و upstream v26.9.30 است. ساخت دوبارهٔ این AAR نیازمند زنجیرهٔ gomobile همراه Psiphon است که در CI وجود ندارد؛ جایگزینی بدون تست دستگاه ممکن است اتصال اصلی برنامه را بشکند. این مورد در این نسخه انجام نشد و ادعایی دربارهٔ آن نمی‌شود.

## ۲. یکپارچه‌سازی واقعی انجام‌شده

| مورد | Core Manager | فرم افزودن | Import/Export | تست | اتصال/قطع | لاگ | تنظیمات | Backup | About |
|---|---|---|---|---|---|---|---|---|---|
| MASQUE (sing-box `masque-client`) | قابلیت در SINGBOX | فرم کامل (HTTP/3·2·1، path، SNI، ALPN، uTLS، pin، MTU) | لینک `masque://` + ورود از JSON sing-box | تست واحد (فرم → endpoint، پیش‌فرض HTTP/3، ورود JSON) | از مسیر sing-box موجود | لاگ sing-box | بخش‌بندی فرم | مثل بقیهٔ کانفیگ‌ها | در لیست قابلیت‌های sing-box |
| Aether 2.1 | بدون تغییر رابط | MASQUE²، اسکن verified، noize جدید، کشور خروج، fragment | در JSON Aether | — | همان کنترلر | همان | صفحهٔ پروژه‌های رایگان | در JSON کانفیگ | نسخه/مجوز |
| zeptun 1.1.1 | همان JNI | — | — | build در CI | — | — | — | — | — |

## ۳. سازگاری اندروید
- ABI: arm64-v8a، armeabi-v7a، x86_64 (فقط build آزمایشی امولاتور). APK جدا برای هر ABI (از قبل `splits` فعال است).
- Aether: `cargo ndk --platform 26` و `-z max-page-size=16384`؛ همهٔ .soها در CI از نظر هم‌ترازی 16KB بررسی می‌شوند.
- zeptun: JNI بدون تغییر امضا؛ keep rule موجود.
- سازگاری SIGILL: Aether و sing-box با target عمومی هر ABI ساخته می‌شوند (بدون `target-cpu=native`).

## ۴. اندازه — اندازه‌گیری واقعی روی APK منتشرشدهٔ 1.0.9 (arm64-v8a)

فایل: `Ghajarvpn-1.0.9-arm64-v8a.apk` — 109,657,857 بایت، sha256 `9eb430ea47b3f11c…`

| بخش | حجم فشرده در APK | حجم خام |
|---|---|---|
| Native (.so) | 95.66 MB | 261.73 MB |
| Assets | 7.02 MB | 33.22 MB |
| DEX | 4.23 MB (یک فایل) | 8.39 MB |
| Resources | 2.57 MB | 3.42 MB |
| META-INF و غیره | 0.04 MB | 0.14 MB |

بزرگ‌ترین کتابخانه‌ها (فشرده / خام): libsingbox 21.79/61.60 · libgojni (Xray+Psiphon) 15.66/46.81 · libghajarhelper 8.37/24.55 · liblyrebird 6.10/17.35 · libjuicity 4.41/12.34 · libtor 3.96/8.38 · libaether 3.94/8.28 · libdnstt 3.48/9.08 · libnoizdns 3.42/9.09 · libvaydns 3.42/9.07 · libcottendns 3.33/8.60 · libovpn3 3.20/8.91 · libslipstream 2.87/6.09.

Assets: geoip (Tor) 2.66/9.48 · geosite.dat 2.36/7.65 · geoip6 (Tor) 1.95/15.99 · worldmap.bin 0.02/0.02.

یافته‌ها:
- **همهٔ 34 کتابخانهٔ native بدون `.symtab` و بدون بخش `.debug_*` هستند** (بررسی با readelf) — چیزی برای strip باقی نمانده.
- تقسیم ABI از قبل فعال است؛ نسخهٔ universal منتشر نمی‌شود.
- geoip/geoip6 برای انتخاب کشور خروجی Tor (`ExitNodes {cc}`) لازم‌اند؛ حذف یا دانلود اختیاری آن‌ها رفتار Tor را تغییر می‌دهد و در این نسخه انجام نشد (طرح: دانلود هنگام اولین انتخاب کشور خروج).
- کاهش واقعی این نسخه: حذف نقشه (worldmap.bin، سه فایل Kotlin و یک اسکریپت). عدد نهایی APK 1.0.10 بعد از build در CI از خروجی واقعی اندازه‌گیری و در یادداشت انتشار ثبت می‌شود.

## ۵. کارایی — بررسی حلقه‌ها و کارهای پس‌زمینه

| حلقه | فاصله | محدوده | وضعیت |
|---|---|---|---|
| اعلان‌های فروشگاه در Activity | 30 ثانیه | فقط در `STARTED` (repeatOnLifecycle) | درست |
| اعلان‌ها در سرویس VPN | 120 ثانیه | فقط وقتی تونل روشن است | درست |
| بررسی پرداخت | 2→15 ثانیه پلکانی | فقط صفحهٔ پرداخت باز | درست |
| سفارش‌های معلق | 30 ثانیه | فقط `RESUMED` | درست |
| IP هات‌اسپات در VPN Share | 3 ثانیه | فقط وقتی دیالوگ باز است | درست |
| همگام‌سازی مصرف بدون VPN | 5 ثانیه | فقط وقتی صفحه ترکیب شده | درست |
| ساعت ثانیه‌ای خانه/تاریخچه | 1 ثانیه | فقط وقتی خوانده می‌شود | درست |
| Live Monitor | نمونه‌برداری | فقط وقتی صفحه باز است | درست |

- تغییرات Personalization روی draft در حافظه انجام می‌شود و فقط با «ذخیره» یک بار نوشته می‌شود (نوشتن در هر حرکت اسلایدر وجود ندارد).
- لیست سرورها: اطلاعات جانبی (ترافیک، آخرین اتصال) یک بار با `remember` محاسبه و از `CompositionLocal` خوانده می‌شود، نه برای هر ردیف.
- پروفایل حرارتی/CPU روی دستگاه واقعی در این محیط ممکن نبود؛ عددی ادعا نمی‌شود.

# فاز ۴ — پیاده‌سازی در سورس و مرزهای تأیید

مبنا: commit فاز ۳ `50617926fab3982c172a4c3c173625976bb1339a` و چهار سند اصلی در `docs/audit-1.1.1/`. Audit از ابتدا تکرار نشده است. هیچ Build اپ/افزونه/Core، Gradle، CI، Tag یا Release در این فاز اجرا نشده است. تست‌های مستقل JVM/Java و چند تست Go بدون ساخت محصول اجرا شده‌اند.

**وضعیت کلی:** تغییرات زیر در سورس پیاده شده‌اند، اما تأیید اتصال روی Android و انتشار افزونه‌ها انجام نشده است. بنابراین این گزارش ادعای آماده‌بودن نسخهٔ نهایی یا تکمیل تمام پذیرش‌های فاز ۴ نیست. xDNS فعال نشده و زنجیرهٔ Aether/Tor هنوز طراحی است. کاتالوگ انتشار و فهرست ناشران production خالی مانده‌اند؛ دکمهٔ نصب برای APK منتشرنشده فعال نیست.

## ۱. تنظیمات Coreهای موجود

- قرارداد مرکزی `EngineSettings`، با schema داخلی `engineSettings.version=1`، ورودی فرم، ویرایش پروفایل، import لینک، validation و تولید JSON را به هم وصل می‌کند. `CapabilityRegistry.settingsFor` همین قرارداد را برای ویرایشگر ارائه می‌کند. تنظیمی برای پروتکل فاقد آن تولید نمی‌شود.
- AnyTLS: `idle_session_check_interval`، `idle_session_timeout` و `min_idle_session` به کلاینت واقعی sing-box می‌رسند. padding در سورس پین‌شده inbound/server است و به فرم کلاینت اضافه نشده است.
- TUIC: `udp_over_stream`، `heartbeat` و `idle_timeout` اضافه شده‌اند؛ congestion موجود حفظ شده است. ترکیب ناسازگار با `udp_relay_mode` رد می‌شود. 0-RTT خودکار فعال نشده است.
- Hysteria2: بازه/لیست پورت، `mport`، پورت‌های چندگانه در authority، `hopInterval` و `hop_interval` خوانده می‌شوند. پورت نامعتبر یا interval بدون لیست رد می‌شود. پروفایل معمولی روی مسیر Xray قبلی می‌ماند؛ استفادهٔ صریح از تنظیمات جدید، همان sing-box bundled را انتخاب می‌کند. Core جدیدی اضافه نشده است.
- ECH جدید فقط در endpointهای بررسی‌شدهٔ AnyTLS/TUIC/Hysteria2 عرضه می‌شود. Base64 با framing محدود بررسی و به PEM واقعی `ECH CONFIGS` نسخهٔ پین‌شده تبدیل می‌شود. SNI لازم است؛ insecure و ترکیب uTLS در AnyTLS پذیرفته نمی‌شود. گزینهٔ جعلی «fallback به TLS بدون ECH» وجود ندارد. retry/rejection تابع implementation پین‌شده است؛ اتصال واقعی ECH هنوز آزمون دستگاه لازم دارد. مسیر ECH موجود Xray تغییر نکرده است.
- OpenConnect: auth group، user/pass، flavor، UA، OS، MTU، reconnect، DTLS، IPv6، گواهی و private key قبلی حفظ شده‌اند. cookie، TOTP، CA سازمانی، رمز کلید و گواهی/کلید MCA اضافه شده‌اند. این اطلاعات در پروفایل/backup می‌مانند و وارد لینک اشتراک‌گذاری عمومی نمی‌شوند. URL سرور IPv6 اصلاح شده است. HOTP بدون persistence شمارنده، SSO browser callback و اجرای wrapperهای خارجی به‌دروغ پشتیبانی‌شده اعلام نشده‌اند.
- OpenVPN: مسیر اختصاصی و preferenceهای واقعی موجود حفظ شده‌اند. parser/VpnProfile فعلی از inline certificate/key، `tls-crypt-v2`، retry و `remote-cert-tls` پشتیبانی دارد. موتور جایگزین یا تنظیم تکراری اضافه نشده است. آزمون دستگاهِ challenge/auth در پذیرش نهایی باقی است.

## ۲. ShadowQUIC

سورس APK مستقل در `plugins/shadowquic` و مدل مشترک Java در SDK اضافه شده است. upstream دقیق: `spongebob888/shadowquic`، commit `5540e3a32ca73c85af125723e4262e02cf28ebcd`، نسخهٔ workspace برابر 0.4.0، MIT. QUIC/TLS آن `quinn-jls`/`quinn-proto-jls` 0.3.8 و `rustls-jls` 1.3.7 هستند؛ lockfile همان pin باید حفظ شود.

فرم افزودن، لینک `shadowquic://` و `sq://`، JSON کلاینت Ghajar، export/import، اعتبارسنجی host/SNI/port/ALPN/MTU/congestion، SOCKS5، start/stop، status و آزمون HTTPS از داخل SOCKS پیاده شده‌اند. 0-RTT پیش‌فرض خاموش است. raw config حاوی server/Lua/command/path دلخواه پذیرفته نمی‌شود. آماده‌شدن listener علاوه بر handshake با نشانگر stdout خودِ فرایند تأیید می‌شود. دادهٔ حساس native در Logcat کپی نمی‌شود؛ lifecycle در GhajarLog ثبت می‌شود.

نصب/update/remove/repair/retry/rollback از مدیر امن فاز ۳ استفاده می‌کند. توقف/reconnect از Orchestrator فعلی انجام می‌شود. نبود افزونه مانع ذخیرهٔ config نیست. راهنمای درون اپ می‌گوید سرور، credentials، SNI مطابق `jls-upstream` و ALPN سازگار لازم‌اند.

**محدودیت واقعی:** هیچ APK این adapter ساخته یا امضا نشده؛ نصب و اتصال واقعی روی arm64/armv7، بررسی QUIC/TLS و سازگاری سرور، هنوز تأیید نشده است. build script به pin دقیق و Cargo.lock متصل است و این فاز اجرا نشده است. Android SDK adapters فقط جداگانه type-check شده‌اند. نمایش «Installed» یا ادعای اندازهٔ APK نشده است.

## ۳. Mihomo

سورس APK مستقل و runner در `plugins/mihomo` و `native/plugin-mihomo` اضافه شده است؛ snapshot مصوب Bettbox `3189346611caeba73aa87feaf708e4fd65115d16/core/Clash.Meta` با GPL-3.0. نسخهٔ Bettbox به‌عنوان نسخهٔ Core جا زده نشده است.

فایل اصلی YAML/JSON کامل ذخیره، ویرایش، export و backup می‌شود؛ providers، proxy-providers، rules، rule-providers، groups، DNS و profile وارد `config.ParseRawConfig` و `executor.ApplyConfig` می‌شوند، نه مدل Node عمومی. در snapshot مذکور، `ApplyConfig` ساخت listener/TUN را کامنت کرده است؛ runner آن‌ها را صریح ایجاد و listenerهای حاصل را بررسی می‌کند.

- با `tun.enable=false`، یک socks-port یا mixed-port محلی معتبر لازم است؛ zeptun تنها مصرف‌کنندهٔ TUN میزبان است.
- با `tun.enable=true`، FD همان TUN میزبان از کانال خصوصی Unix/SCM_RIGHTS به Mihomo منتقل می‌شود؛ zeptun در این مسیر شروع نمی‌شود. native مجاز به ساخت TUN دوم یا نصب route سیستم نیست.
- روت‌ها، آدرس‌ها و DNS قبل از اتصال به میزبان ارائه می‌شوند. DNS interception باید واقعاً DNS انتخابی را بپوشاند.
- pathهای فایل provider/certificate محدود می‌شوند؛ path مربوط به WebSocket با مسیر فایل اشتباه گرفته نمی‌شود.
- گزینه‌های غیرقابل نمایش در API 1، از جمله policyهای UID/package/route-exclusion/set، MIPStack، listenerهای server-side، redirect/root-only، external controller/UI و binding به LAN، **خطا می‌دهند؛ بی‌صدا حذف یا تغییر نمی‌شوند**. برای TUN باید stack پشتیبانی‌شده مانند gvisor و auto-route متناسب با host انتخاب شود. فایل اصلی حتی در این حالت نگه داشته می‌شود.

**محدودیت واقعی:** این adapter هنوز native-build/type-check کامل با همهٔ dependencyهای Mihomo یا آزمون Android نشده است. script با tag واقعی `with_gvisor` آماده است، ولی اجرا نشده. profileهایی که فایل خارجی محلی لازم دارند تا تهیهٔ امن آن فایل‌ها قابل اجرا نیستند؛ import خودکار پوشه/credential خارجی اضافه نشده است. مدیریت تعاملی selector/group از طریق controller در این فاز اضافه نشده؛ config و رفتار داخلی Core حفظ می‌شوند. APK production موجود نیست؛ ادعای integration تأییدشدهٔ دستگاه نداریم.

## ۴. Psiphon

AAR و patchهای آن دست‌نخورده‌اند. country/automatic mode و reconnect موجود حفظ شدند. انتخاب پروتکل از فهرست واقعی NON_INPROXY، مهلت `EstablishTunnelTimeoutSeconds` و کنترل diagnostics به تولید config وصل شدند. انتخاب ناسازگار با زنجیرهٔ TCP موجود رد می‌شود؛ پروتکل QUIC به زور در proxy TCP قرار نمی‌گیرد. زنجیرهٔ Aether→Psiphon موجود باقی است.

SHA-256 فعلی `app/libs/ca.psiphon.aar`:
`213ce36cf9faa2f9149c568541ed178b987f83b5576719ff61b49fc8ad8805a1`

این hash با Inventory قبلی یکسان است؛ hash هویت artifact را ثابت می‌کند، **نه exact source commit باینری**. تطبیق rebuild با patchها و replaceهای QUIC/DTLS/qpack همچنان gate provenance است.

## ۵. Conduit / INPROXY

Unavailable مانده است. کلید معتبر اختصاصی Ghajar برای `ServerEntrySignaturePublicKey` در اختیار پروژه نیست. احتمال انتخاب INPROXY صفر و protocol whitelist بدون آن است. راهنما دلیل عدم دسترسی را نشان می‌دهد؛ کلید هیچ برنامهٔ دیگری کپی نشده است.

## ۶. Aether

mapping گمشدهٔ `mim`، `mim-outer`، `mim-inner`، `mim-scan` و `exit-loc` تکمیل شد. حالت‌های H3/H2/WireGuard/gool و scan موجود حفظ شده‌اند. UI توضیح می‌دهد فیلتر کشور خروجی تضمین پیدا شدن کشور نیست. Tor جدید در Aether لینک نشده است. طراحی دو جهت زنجیره و محدودیت TCP/UDP در `AETHER_TOR.md` ثبت شده؛ فعال‌سازی UI برای این طراحی انجام نشده است.

## ۷. SSTP

helper موجود حفظ شد. HTTP/HTTPS CONNECT با authentication، header محدود، deadline و خطاهای 407/پاسخ نامعتبر اضافه شد. credentials پروکسی در فایل خصوصی sidecar منتقل می‌شوند، نه آرگومان command line یا لینک اشتراک عمومی. گواهی HTTPS proxy با CA/hostname خودش بررسی می‌شود و از insecure/pin سرور SSTP تأثیر نمی‌گیرد.

TLS minimum فقط 1.2/1.3 است. pin دقیق SHA-256 حفظ شده و در حالت verification فعال، hostname نیز بررسی می‌شود. preference قدیمی insecure reset نشده و مسیر verification جدید ضعیف نشده است. DNS جایگزین از فرم به helper می‌رسد و فقط وقتی سرور DNS ندهد استفاده می‌شود.

## ۸. xDNS

**فعال/Integrate نشده است؛ فقط یک Method/PacketConn candidate باقی است.** شواهد جدید در pin Audit `bdc6f0e7767882daecb4e3c0eb3862fb4e2918e2`:

- `encode` بستهٔ ۲۲۴ بایتی و بزرگ‌تر را رد می‌کند؛ افزودن آن به QUIC با MTU معمول بدون framing سازگار قابل اتکا نیست.
- `WriteTo` در encode failure یا پر بودن صف `(0, nil)` می‌دهد؛ خطا باید صریح و قابل recovery شود.
- `closed` در Close/loop/WriteTo با synchronization یکسان خوانده/نوشته نمی‌شود.
- ReadFrom روی صف است، در حالی که deadline زیرین PacketConn لزوماً همین صف را unblock نمی‌کند.

صرف کپی این adapter معیارهای cancellation/framing/MTU/protocol protection درخواست‌شده را برآورده نمی‌کند. سورس نیاز به اصلاح و regression corpus مشترک client/server دارد. Xray فعلی برای این کار patch یا upgrade نشده و dnstt جایگزین xDNS معرفی نشده است. تصمیم MERGE_FEATURE در Audit یک پیشنهاد مشروط بود؛ این gateهای جدید در `SOURCE_EVIDENCE.json` این فاز ثبت شده‌اند.

## ۹. dnstt

sidecarهای فعلی حفظ شدند. adapter مبتنی بر `globalTunnel` وارد نشده است.

## ۱۰. Free Projects

هیچ منبع سالمی حذف نشده و منبع جدیدِ تأییدنشده‌ای اضافه نشده است. کنترل enable/disable/remove به حلقهٔ واقعی دریافت وصل شد؛ restore منابع حذف‌شده آن‌ها را ابتدا disabled برمی‌گرداند. رفتار قدیمی فقط با درخواست کاربر برای refresh ادامه دارد؛ startup دانلود نمی‌کند. انتخاب منابع در backup نگه داشته می‌شود. label منبع و provenance هر config تازه ذخیره می‌شود. عمومی بودن node هیچ trust خودکاری ایجاد نمی‌کند. پاک‌کردن منبع، دستور حذف کانفیگ ذخیره‌شدهٔ کاربر نیست.

## ۱۱. Mahsa / Nika

هیچ کد closed-source، reverse engineering یا کلید متعلق به سرویس دیگر استفاده نشده است. تنظیمات افزوده‌شده از schema نسخهٔ پین‌شدهٔ sing-box و سورس فعلی Ghajar مستقل پیاده شده‌اند. xDNS فقط source-review شد و به دلیل موانع بالا فعال نشد. قابلیت تکراری برای fragmentation/chaining/DNS اضافه نشده است.

## ۱۲. SKIP و تفکیک مفاهیم

paqet، phantun، QQ/QS، MMDF و روش‌های raw-packet فاقد integration rootless معتبر وارد نشده‌اند. ZedPass به‌عنوان Core جدید اضافه نشده است. nDPI همان classifier بخش Diagnostics مانده؛ خروجی فعلی محدود به dissectorهای موجود است و تحلیل کامل TLS/QUIC ادعا نمی‌شود. zeptun همان TUN engine و pin فعلی است؛ در پروتکل‌های UI قرار نگرفته و benchmark خیالی ذکر نشده است. بخش روش‌های بیشتر، نوع Core/Protocol/Transport/DNS را روشن‌تر نمایش می‌دهد. گزینهٔ اجراییِ جعلی برای Experimentalها ساخته نشده است.

## ۱۳. Persistence / Migration

Migration افزایشی است: نبود `engineSettings` یعنی default قبلی؛ اضافه/پاک‌کردن تنظیمات این envelope فیلدهای نامرتبط extra را تغییر نمی‌دهد. فیلدهای جدید در `ProxyConfig.toJson/fromJson` موجود و backup معمولی حمل می‌شوند. تنظیمات `oblivionJson` قدیمی با defaultهای قبلی خوانده می‌شوند؛ preference قدیمی reset نمی‌شود. `freeSourcePolicyV1` فقط در صورت وجود در restore اعمال می‌شود. binary افزونه همچنان در backup نیست؛ ID/version/settings/config حفظ و reinstall از مدیر افزونه انجام می‌شود. تست serialization جای آزمون واقعی process death Android را نمی‌گیرد.

## ۱۴. فایل‌ها و بررسی‌ها

فهرست کامل در `CHANGED_FILES.txt` است. تست‌ها: ۴۳ مورد JVM شامل parser، فرم، capability settings، تولید config، round-trip و trust؛ تست‌های Go مستقل برای CONNECT و محدودسازی مسیر provider؛ type-check Java SDK و serviceهای افزونه با reference Android و BuildConfig موقت؛ type-check بخش غیر Compose میزبان افزونه با stub وابستگی‌های اپ؛ PSI syntax برای Kotlinهای تغییرکرده. محدودیت هر بررسی در `VALIDATION.md` آمده است.

## ۱۵. باقی‌مانده‌ها و شرط بستن فاز

- ساخت نهایی مجاز در انتهای همهٔ فازها، با ساخت dependencyهای native، ABIهای arm64/armv7 و library alignment؛ سپس نصب/rollback/process-death/TUN race/سرور واقعی و corpus provider/rules/DNS.
- هویت ناشر واقعی، certificate، امضای APK و manifest، URL/size/hash واقعی و license/source bundle؛ هیچ‌کدام با مقدار ساختگی پر نشده‌اند.
- xDNS: اصلاح transport و آزمون client/server و تطبیق با Xray ثابت یا فاز اختصاصی؛ فعلاً unavailable.
- Aether/Tor: orchestration پیشنهادی هنوز پیاده/فعال نشده؛ runtime lease، readiness، cancellation و fail-closed باید آزمون شوند.
- OpenConnect browser SSO/HOTP و Mihomo policy/controller/file-dependencyهای خارج از API 1 صریحاً پشتیبانی کامل ندارند.
- provenance دقیق باینری Psiphon و آزمون regression دستگاه باقی است. Xray همچنان `v1.260327.0` / 26.3.27 است.

تا عبور از این gateها «همهٔ قابلیت‌های فاز ۴ کاملاً آماده‌اند» ادعا نمی‌شود.

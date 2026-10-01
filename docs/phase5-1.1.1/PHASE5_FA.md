# فاز ۵ — UX، انتقال اتصال و اشتراک محلی

مبنا: `1c5ea4a69e930a9981c60e2b5536b87035ab0d02`، Audit فاز ۲ و معماری/محدودیت‌های فازهای ۳ و ۴. هیچ Build محصول، Gradle، CI، Tag یا Release اجرا نشده است. Xray، sing-box، Aether، zeptun، Psiphon AAR و پین‌های native تغییر نکرده‌اند.

**مرز پذیرش:** پیاده‌سازی و تست مستقل relay و Import/Export انجام شده است. آزمون واقعی دو دستگاه، هات‌اسپات اندروید، IP خروجی، DNS/IPv6 روی شبکهٔ واقعی، نصب mobileconfig روی Apple و import کلاینت‌های مقصد انجام نشده‌اند. این سند ادعای قبولی آن آزمون‌ها یا آماده‌بودن APK انتشار ندارد. محدودیت APK امضاشدهٔ افزونه‌ها از فاز ۴ همچنان باقی است.

## ۱. اشتراک‌گذاری قبلی چه مشکلی داشت؟

- `http-share-in` بدون account روی هات‌اسپات باز می‌شد؛ نام کاربری/رمز SOCKS آن را محافظت نمی‌کرد.
- همان `socks-in` مصرف داخلی از loopback به آدرس هات‌اسپات منتقل می‌شد؛ این کار می‌توانست مصرف‌کنندگان محلی را نیز مختل کند.
- قواعد DNS، split routing و YouTube Direct قبل از catch-all پراکسی قرار داشتند؛ ادعای «همهٔ ترافیک مشترک از VPN» از ترتیب قواعد قابل اثبات نبود.
- UI فقط OpenVPN/IKEv2 را مستثنا می‌کرد؛ مسیرهای zeptun/sing-box یا plugin هم ممکن بود فعال به نظر برسند، بدون listener متناظر.
- آزمون ShareDoctor فقط TCP port-open بود؛ این آزمون، IP خروجی VPN یا DNS leak را ثابت نمی‌کند.
- Direct Share انتخاب دستگاه و خروجی مناسب WireGuard/IKEv2 نداشت. QR قبلی پیش از هشدار نمایش داده می‌شد و انقضای یک‌دقیقه‌ای نداشت.

## ۲. Direct Share چگونه کار می‌کند؟

در صفحهٔ اشتراک، «اتصال مستقیم روی دستگاه دیگر» اول قرار دارد. دستگاه مقصد انتخاب می‌شود، سپس پروفایل یا Subscription و قالب خروجی. روشن‌بودن VPN گوشی لازم نیست. فایل از `FileProvider` با دسترسی خواندن موقت به برنامهٔ انتخابی کاربر منتقل می‌شود؛ چیزی روی سرور عمومی آپلود نمی‌شود.

`DirectShare` خروجی‌ها را بر اساس مدل واقعی تعیین می‌کند. URI برای پروتکل‌های دارای exporter موجود، فایل WireGuard، `.ovpn` اصلی، IKEv2 profile محدود به EAP، اطلاعات دستی OpenConnect و full YAML/JSON Mihomo ارائه می‌شوند. گزینه‌های اختصاصی مقصد باید پشتیبانی شوند؛ وجود URI به معنی سازگاری همهٔ کلاینت‌ها نیست.

بستهٔ `connection.ghajar.json` با `ghajarShare=true` و `schemaVersion=1` شامل protocol/server/port، محل داده‌های authentication، transport/TLS، دامنهٔ DNS/routing، نیازمندی plugin/core و پروفایل‌های کامل است. dependencyهای chain و Tor base با هم صادر، هنگام ورود IDها remap و حلقه/وابستگی گمشده رد می‌شوند. محدودیت ۱۰۰ پروفایل و ۱۶ MiB دارد. دادهٔ نسخهٔ ناشناخته خطای قالب می‌گیرد، نه درخواست رمز. importer واقعی در `ConfigParser` و `DecoderRegistry` وصل شده است. تنظیمات سراسری دستگاه جزو این بسته نیستند.

بستهٔ اختصاصی فقط برای Android/Ghajar و Other عرضه می‌شود؛ برای خروجی استاندارد، استفاده از Ghajar اجباری نیست. فایل محافظت‌شدهٔ قدیمی Ghajar از همان مسیر قبلی همچنان قابل دسترسی است.

## ۳. iPhone / iPad چه قالب‌هایی می‌گیرند؟

- URI/QR و Subscription در کلاینتی که همان پروتکل و گزینه‌ها را پشتیبانی کند؛ Camera به‌صورت خودکار VPN سیستم نمی‌سازد.
- WireGuard: متن استاندارد `.conf` و QR قابل اسکن از داخل WireGuard.
- OpenVPN: `.ovpn` کامل، برای Import در OpenVPN Connect یا کلاینت سازگار.
- IKEv2: `.mobileconfig` **امضانشده، بدون AuthPassword**، فقط برای مدل فعلی EAP نام کاربری/رمز، پورت ۵۰۰ و Remote ID مشخص. `AuthenticationMethod=None` همراه `ExtendedAuthEnabled=1` است. هیچ bypass گواهی، CA ناشناس یا private key داخل آن اضافه نشده است. برای CA اختصاصی/احراز هویت گواهی‌محور باید مدیر سرویس فایل مناسب بدهد. تنظیم دستی نیز عرضه می‌شود.
- OpenConnect: اطلاعات دستی و توضیح نیاز به کلاینت سازمانی متناسب با نوع سرور؛ قالب نصب خودکار iOS جعل نشده است.
- Mihomo: full YAML/JSON فقط برای برنامهٔ واقعاً سازگار با full config؛ node extraction انجام نمی‌شود.
- JSON اختصاصی ShadowQUIC/Ghajar به‌عنوان فایل قابل import در iOS عرضه نمی‌شود.

## ۴. Android چه قالب‌هایی می‌گیرد؟

URI/QR سازگار، Subscription، WireGuard/AmneziaWG، `.ovpn` و بستهٔ کامل Ghajar. کانفیگ افزونه بدون نصب binary قابل ذخیره می‌ماند. QR پراکسی محلی `socks5://` با نام «برای Ghajar / کلاینت SOCKS5 سازگار» نمایش داده می‌شود؛ importer موجود Ghajar آن را می‌خواند. این QR ادعای تغییر تنظیمات Wi-Fi سیستم ندارد.

## ۵. Windows چه قالب‌هایی می‌گیرد؟

فایل/URI کلاینت سازگار، Subscription، WireGuard/AmneziaWG `.conf`، `.ovpn`، Mihomo full config، و اطلاعات دستی IKEv2/OpenConnect. دستور shell حاوی secret تولید نمی‌شود. راهنما: ذخیرهٔ فایل ← Import در کلاینت ← بررسی تنظیمات ← Connect.

## ۶. macOS چه قالب‌هایی می‌گیرد؟

قالب‌های کلاینت سازگار، WireGuard/AmneziaWG، `.ovpn`، full config و IKEv2 mobileconfig محدود فوق. نصب پروفایل نیاز به تأیید کاربر در تنظیمات سیستم دارد. قالب اختصاصی افزونهٔ Android به‌عنوان برنامهٔ macOS معرفی نمی‌شود.

## ۷. Linux چه قالب‌هایی می‌گیرد؟

URI/فایل کلاینت سازگار، WireGuard/AmneziaWG، `.ovpn`، full YAML/JSON Mihomo و اطلاعات دستی پروتکل‌های سازمانی. هیچ دستور اجرایی خودکار، ذخیرهٔ رمز در shell history یا Root setup نمایشی ارائه نمی‌شود.

## ۸. Share Through Phone چگونه کار می‌کند؟

`AuthenticatedRelay` یک listener TCP مختلط برای SOCKS5 CONNECT و HTTP CONNECT دارد. به آدرس خصوصی IPv4 مشخص از هات‌اسپات یا شبکهٔ Wi-Fi/Ethernet منتخب bind می‌شود؛ wildcard، cellular و TUN مجاز نیستند. Wi-Fi LAN فقط با انتخاب صریح آدرس فعال می‌شود. fallback به آدرس قبلی یا به شبکهٔ دیگری هنگام ناپدیدشدن انتخاب کاربر وجود ندارد.

تنها مقصد socket خروجی relay، یک SOCKS روی `127.0.0.1` با پورت ثابت متعلق به موتور فعال است. نام مقصد با SOCKS ATYP domain عبور می‌کند؛ در relay با DNS سیستم resolve نمی‌شود. IPv6 literal نیز به موتور ارسال می‌شود. relay هیچ dial مستقیم به مقصد کاربر ندارد.

در این فاز، انتشار session فقط پس از آماده‌شدن واقعی مسیر Xray، برای VLESS/VMess/Trojan/Shadowsocks/Hysteria/Hysteria2/Tor و فقط وقتی zeptun مالک TUN نیست انجام می‌شود. بعضی اعضای این فهرست در مسیر فعلی sing-box اجرا می‌شوند؛ شرط runtime مانع اشتراک آن مسیر می‌شود. OpenVPN، IKEv2، plugin، WireGuard، Psiphon/Aether و مسیرهای sing-box/zeptun به‌دروغ پشتیبانی‌شده اعلام نمی‌شوند؛ برای آن‌ها انتقال مستقیم در دسترس است. گسترش این فهرست به اثبات مسیر DNS و lifecycle هر موتور نیاز دارد.

## ۹. Local Proxy چگونه امن شده است؟

- هر دو SOCKS و HTTP CONNECT احراز هویت اجباری دارند؛ HTTP بدون رمز 407 می‌گیرد.
- username تصادفی و password با ۹۶ بیت entropy از تولیدکنندهٔ امن موجود؛ فقط در حافظهٔ فرایند، نه backup.
- تولید رمز جدید و Stop تمام socketهای قبلی را می‌بندند.
- حداکثر ۱۶ نشست، pool محدود، سقف header برابر ۸ KiB، deadline مطلق ده‌ثانیه‌ای handshake و timeoutهای اتصال/idle.
- فقط CONNECT؛ SOCKS BIND/UDP و HTTP معمولی رد می‌شوند. نه UDP fallback و نه پراکسی عمومی بدون رمز وجود دارد.
- header احراز هویت به مقصد forwarded نمی‌شود؛ مقصد و credentials در log نوشته نمی‌شوند.
- client list از socket واقعی و موفق استخراج می‌شود: IP، تعداد نشست/دستگاه یکتا، مدت هر نشست، upload/download واقعی. تعداد دستگاه‌های هات‌اسپاتِ خارج از این proxy حدس زده نمی‌شود.

احراز هویتِ پراکسی، لینک محلی را رمز نمی‌کند. UI استفاده از شبکهٔ مورد اعتماد با WPA2/WPA3 را توضیح می‌دهد؛ TLS مقصد همچنان مسئول محرمانگی محتوای HTTPS است.

## ۱۰. Kill Switch Sharing چگونه کار می‌کند؟

در `ConfigBuilder`، listener داخلی معمولی همیشه روی loopback می‌ماند. یک listener مجزای `phone-share-in` فقط روی loopback اضافه شده و **اولین قاعدهٔ routing** آن را به `proxy` می‌فرستد؛ split routing، YouTube Direct، resolver محلی یا fallback direct نمی‌توانند مقدم شوند.

`PhoneSharing` به lifecycle سرویس متصل است. هنگام Connecting/Disconnecting/Error/Disconnected، تعویض سرور، die و onDestroy، listener و همهٔ نشست‌ها بسته و backend باطل می‌شود. انتشار دوباره فقط پس از آماده‌شدن موتور مجاز است. بعد از process death، فرایند/سوکت‌هایش وجود ندارند و رمز جدید تولید می‌شود.

این حفاظت برای **ترافیک ارسال‌شده به proxy** است. جلوگیری از خروج مستقیم برنامه‌ای روی دستگاه دوم که proxy را نادیده می‌گیرد یا fallback خودش را فعال کرده، بدون کنترل همان دستگاه ممکن نیست. UI این محدودیت را صریح می‌گوید.

## ۱۱. چه پروتکل‌هایی QR دارند؟

در Direct Share: URIهای موجود VLESS، VMess، Trojan، Shadowsocks، Hysteria/Hysteria2، TUIC، AnyTLS، Juicity، Mieru، Naive، ShadowTLS و Brook، اگر خروجی موجود و وابستگی chain جدا نداشته باشند؛ WireGuard استاندارد؛ Subscription URL. سازگاری واقعی importer مقصد، به‌خصوص برای گزینه‌های اختصاصی، شرط است. ShadowQUIC JSON و Mihomo YAML به‌عنوان QR عمومی معرفی نمی‌شوند. payload بزرگ به فایل هدایت می‌شود.

تمام مسیرهای QR قبلی نیز اکنون consent دارند: هشدار «اطلاعات اتصال محرمانه»، secure window، انقضای پیش‌فرض ۶۰ ثانیه و بسته‌شدن هنگام رفتن اپ به پس‌زمینه. اشتراک تصویر QR بعد از هشدار حفظ شده است. QR محلی هنگام تغییر وضعیت/credential بسته می‌شود. خروجی cache ممکن است تا پاک‌شدن cache/ورود بعدی به Sharing بماند؛ فایل‌های تولیدشدهٔ قدیمی‌تر از یک ساعت در ورود بعدی پاک می‌شوند. دسترسی فایل فقط با رضایت Share کاربر اعطا می‌شود.

## ۱۲. چه پروتکل‌هایی File Export دارند؟

تمام URIهای فوق در فایل متن، Subscription، WireGuard/AmneziaWG `.conf`، OpenVPN `.ovpn`، IKEv2 `.mobileconfig` و راهنمای دستی، اطلاعات دستی OpenConnect، Mihomo YAML/JSON کامل، ShadowQUIC JSON مخصوص importer Ghajar، و بستهٔ Ghajar برای پروفایل‌های مجاز.

WireGuard واردشده متن اصلی DNS/AllowedIPs/keepalive و peerها را نگه می‌دارد؛ اگر فیلدهای مؤثر پس از Import تغییر کنند، فایل قدیمی به‌دروغ معادل پروفایل فعلی صادر نمی‌شود. کلیدهای تولیدشده اعتبارسنجی می‌شوند؛ reserved اختصاصی بدون معادل استاندارد و hook اجرایی خروجی خودکار نمی‌گیرند.

OpenVPN فایل runtime حاوی مسیرهای Android را صادر نمی‌کند. متن اصلی در فیلد افزودهٔ nullable مدل موجود و backup سریال‌شده حفظ می‌شود؛ serialVersionUID تغییر نکرده است. فایل‌های خارجی و hookهای اجرایی رد می‌شوند؛ auth-user-pass inline قدیمی حذف و درخواست ورود روی مقصد جایگزین می‌شود. برای پروفایل‌های قدیمی فاقد متن اصلی، Import مجدد لازم است. تنظیمات اتصال و preferenceهای قبلی reset نمی‌شوند.

## ۱۳. چه مواردی به دلیل محدودیت OS/پیاده‌سازی ممکن نیست؟

- دوربین iOS واردکنندهٔ عمومی VPN نیست؛ QR باید در برنامهٔ سازگار خوانده شود.
- Manual Proxy سیستم Android عموماً ورود نام کاربری/رمز ندارد؛ بازکردن پراکسی بی‌رمز برای دورزدن این محدودیت ممنوع است.
- این نسخه transparent NAT، Root gateway، ترافیک خودکار تمام دستگاه دوم، UDP relay یا Wi-Fi Direct provisioning ارائه نمی‌کند. «Gateway پیشرفته» همان relay احرازهویت‌شدهٔ قابل اجرا را توضیح می‌دهد، و NAT را Requires Root / unavailable معرفی می‌کند.
- DNS از قبل resolveشده در دستگاه دوم و برنامه‌های bypassکننده تحت کنترل این گوشی نیستند؛ Remote DNS و خاموش‌کردن fallback در کلاینت مقصد لازم است.
- ادعای برابری IP خروجی یا عدم leak واقعی روی دستگاه نشده؛ مراحل آزمون دو دستگاه در راهنما و `ACCEPTANCE.md` ثبت شده‌اند.

## UX/Core/Plugin و آموزش

انتخاب‌های اصلی صفحهٔ سرور حفظ شدند. Catalog دسته‌های Protocol، Core، Transport، DNS، Anti-Censorship، WARP، Psiphon Methods و Experimental دارد. موارد unavailable مانند INPROXY و xDNS روشن توضیح داده می‌شوند؛ دکمهٔ اتصال جعلی ندارند. zeptun پروتکل معرفی نشده است.

کارت افزونه state/version و فقط size واقعی release امضاشده را نشان می‌دهد؛ عدد نمونهٔ ۸ MB یا نسخهٔ خیالی درج نشده است. نصب/update/remove/repair/rollback همان API امن قبلی است. جزئیات capability و تنظیمات تخصصی پشت بخش پیشرفته‌اند. ویرایش پروفایل موجود، format همان پروفایل را حفظ می‌کند.

انتخاب EngineId/EngineRouting بدون تغییر رفتار به فایل مستقل منتقل شد تا metadata بستهٔ خروجی نام Core واقعی را داشته باشد، نه نام Protocol. eligibility اشتراک نیز از CapabilityRegistry خوانده می‌شود.

فرم‌ها از جدول تنظیمات پروتکل ساخته می‌شوند؛ بخش خالی حذف و گزینه‌های اختصاصی فقط برای protocol پشتیبانی‌شده ظاهر می‌شوند. فیلدهای جدید authentication، port hopping و ECH در بخش مناسب قرار گرفتند. آموزش سادهٔ protocol/device، لینک منابع رسمی و راهنمای خطای اتصال اضافه شدند. Settings قدیمی، هسته‌های bundled و فایل‌های audit دست‌نخورده‌اند.

## اعتبارسنجی

`VALIDATION.md` مرز تست‌های مستقل را ثبت می‌کند. `CHANGED_FILES.txt` فهرست کامل فایل‌هاست. تکمیل پذیرش دستگاه و build محصول طبق دستور کاربر به فاز مجاز نهایی موکول است؛ هیچ CI سبز یا اتصال موفق دستگاه ادعا نمی‌شود.

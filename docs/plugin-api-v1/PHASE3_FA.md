# گزارش فاز ۳ — معماری افزونهٔ Ghajar VPN

مبنای کار: commit `ace3937b487fd8ed1a24486b3e83887b6db5c335` و چهار سند Audit قبلی در `docs/audit-1.1.1`. ممیزی از ابتدا تکرار نشده است. هدف نهایی همچنان 1.1.1 است؛ نسخهٔ برنامه در این فاز تغییر نکرد.

**معماری میزبان، SDK، Trust، مدیریت نسخه‌ها، نگه‌داری کانفیگ و مسیر UI پیاده‌سازی شد. هیچ APK افزونهٔ واقعی ساخته یا منتشر نشده؛ هیچ Candidate به‌دروغ نصب‌پذیر یا تست‌شده معرفی نمی‌شود. Build اپ، Gradle، CI، Release و Tag اجرا/ساخته نشدند.**

## ۱. Plugin API

ماژول کوچک `plugin-api` با API=1 اضافه شد. افزونه یک **APK مستقل امضاشده** با سرویس مشخص است؛ کد اجرایی دلخواه یا فایل `.so` از URL کاربر بارگذاری نمی‌شود. ارتباط از طریق Messenger/Binder و عملیات HEALTH، PREPARE، START، STOP، STATUS و NETWORK_CHANGED است. فایل کانفیگ از FD فقط‌خواندنی و تنظیمات از JSON محدود عبور می‌کنند.

میزبان مالک مجوز VPN، اعلان و TUN است؛ APK افزونه مالک Engine خودش. مرگ Binder، لغو اتصال و توقف سرویس در قرارداد lifecycle پوشش داده شده‌اند. نمایش فهرست فقط metadata می‌خواند؛ Engine هنگام نیاز bind/initialize می‌شود. SDK و رفتار دقیق در [API.md](API.md) ثبت شده‌اند.

## ۲. Capability Contract

`CapabilityContract` شامل ۱۴ پرچم درخواست‌شده است: TCP، UDP، IPv6، TUN، SOCKS، DNS، port hopping، ECH، MASQUE، REALITY، XHTTP، full config، subscription و advanced auth.

`CapabilityRegistry` قراردادهای Coreهای داخلی را متمرکز می‌کند. `PluginCatalog` قابلیت **سورس ممیزی‌شده** را از قابلیت **نسخهٔ نصب‌شده و تأییدشده** جدا نگه می‌دارد. ادعای افزونه نباید از سقف سورس تأییدشده بیشتر باشد. پرچم ناشناخته/غیر Boolean رد می‌شود. UI جدید بر اساس قرارداد، اطلاعات قابلیت و راهنمای ویرایش full-config/احراز هویت را نشان می‌دهد. تنظیمات سالم قبلی حذف یا به خاطر این قرارداد محدود نشده‌اند.

Core، Protocol، Transport، Method، Plugin و TUN Engine نوع جدا دارند. zeptun همان TUN engine داخلی است و به پروتکل VPN جدید تبدیل نشده است.

## ۳. Built-inهایی که حفظ شدند

| مورد | نتیجه |
|---|---|
| Xray و کانفیگ‌های معمول | همان **26.3.27**؛ AAR، go.mod و pinها تغییر نکردند |
| Psiphon | bundled باقی ماند؛ AAR حذف/جدا نشد |
| OpenVPN | ماژول و مسیر اتصال اختصاصی حفظ شد |
| OpenConnect | مسیر داخلی موجود sing-box/sidecar حفظ شد |
| پروژه‌های رایگان | سرویس‌ها، دریافت کانفیگ و مسیرهای موجود حفظ شدند |
| Aether | بسته‌بندی و pin فعلی تغییر نکرد |
| zeptun/Noisemux | فایل پیاده‌سازی، باینری و pin فعلی تغییر نکردند |
| سایر مسیرهای سالم 1.0.10 | Core یا کتابخانه‌ای حذف نشده است |

اصلاحات فاز ۱ و فایل‌های Audit نیز دست‌نخورده باقی ماندند. تغییر سرویس فعلی فقط برای تحویل کنترل/توقف صحیح هنگام استفاده از مسیر **جدید** Plugin است.

## ۴. چه چیزهایی On-Demand شدند؟

**هیچ Core داخلی موجود از APK حذف یا منتقل نشد.** بسته‌بندی آیندهٔ ShadowQUIC و Mihomo full-config به مسیر On-Demand تخصیص داده شد: شناسه، فرم‌های قابل ذخیره، source pin، سقف قابلیت، صفحهٔ مدیریت، نصب/آپدیت/ترمیم/Retry/Rollback و اتصال مستقل آماده‌اند.

اما فهرست انتشار و ناشرهای production عمداً خالی‌اند: هنوز APK سازگار با API قاجار، امضای ناشرِ تأییدشده، SHA-256 و اندازهٔ واقعی نداریم. بنابراین **تعداد افزونه‌های production قابل نصب در این فاز: صفر**. عدد حجم یا نسخهٔ نصب‌شده جعل نشده است. این محدودیت با ممنوعیت ساخت/انتشار این فاز سازگار است.

## ۵. Candidateهای باقی‌مانده

ShadowQUIC و Mihomo از نظر **artifact قابل انتشار** هنوز Candidate هستند. TrustTunnel و EasyConnect نیز فقط Candidate مشروط به نیاز واقعی‌اند؛ نه pin انتخاب‌نشده به‌عنوان تأییدشده جا زده شده، نه SDK بیرونی/باینری نامطمئن نصب می‌شود. Core تخصصی دیگری بدون تصمیم صریح Audit اضافه نشده است.

[PHASE3_MATRIX.json](PHASE3_MATRIX.json) تفاوت «آمادگی معماری» و «آمادگی انتشار» را ثبت می‌کند.

## ۶. Mihomo

شناسهٔ `mihomo` برای **full-config engine** است. YAML/JSON اصلی، شامل comments، anchors، providers، rules، DNS، routing و TUN، در envelope جدا ذخیره می‌شود. Import، اعتبارسنجی اولیه، Config Center، تشخیص تکراری، ویرایش و Backup محتوای آن را نگه می‌دارند. نبود افزونه موجب حذف یا رد کانفیگ نمی‌شود.

سورس مبنا همان snapshot بررسی‌شدهٔ Bettbox با commit `3189346611caeba73aa87feaf708e4fd65115d16` است؛ نسخهٔ اپ Bettbox به‌عنوان نسخهٔ Mihomo اعلام نشده است. runtime adapter آینده باید full semantics را اجرا کند یا قبل از اتصال صریحاً شکست بخورد. تبدیل ناقص به پروفایل عمومی/Xray ممنوع و در ConfigBuilder مسدود شده است.

قابلیت سالم قبلی استخراج تک‌سرورهای Clash حذف نشده؛ به اقدام **اختیاری و صریح** با توضیح فقدان rules/providers تبدیل شده است. Import پیش‌فرض سند کامل را حفظ می‌کند.

## ۷. ShadowQUIC

شناسهٔ `shadowquic` و pin معتبر Audit، یعنی `5540e3a32ca73c85af125723e4262e02cf28ebcd` از `spongebob888/shadowquic`، ثبت شد. این همان pin واقعی افزونهٔ Husi برای v0.4.0 است، نه fork همنام یا HEAD جدیدتر.

URI و JSON آن بدون نصب افزونه ذخیره می‌شوند. اتصال API 1 می‌تواند SOCKS محلی را از طریق zeptun موجود حمل کند؛ **adapter بومی ShadowQUIC و APK امضاشده هنوز ساخته نشده‌اند**. قبل از فعال‌سازی production، بررسی دقیق lock/dependencyهای QUIC/TLS، خروجی client، تولید config، cleanup، ABI و 16 KiB، مجوزهای وابستگی و اتصال به سرور واقعی همچنان gate انتشار است. وجود plugin در upstream به معنای سازگاری خودکار با قاجار نیست.

## ۸. Trust model

ناشر فقط از allowlist کامپایل‌شدهٔ میزبان پذیرفته می‌شود. manifest امضاشده با RSA SHA-256 و کلید حداقل 3072 بیت، certificate فعلی APK، SHA-256/اندازهٔ فایل، ABI، API و حداقل نسخهٔ قاجار، pin سورس، namespace نسخه‌دار، service metadata، سطح permission و dependencyها بررسی می‌شوند. نصب نهایی با رضایت Android انجام می‌شود.

APK نصب‌شده پیش از bind دوباره بررسی می‌شود. SDK نیز package و certificate میزبان را برای هر IPC بررسی می‌کند. هیچ custom authority، prefix، TOFU، کلید واردشده از Backup یا مسیر دانلود executable دلخواه وجود ندارد. اسناد مجوزهای سورس و وابستگی‌ها شرط تأیید اولین artifact هستند؛ صرف پر کردن فیلد License مجوز انتشار محسوب نمی‌شود.

## ۹. Atomic update و Rollback

هر نسخه packageName مستقل دارد؛ PackageInstaller نسخهٔ سالم فعال را بازنویسی نمی‌کند. ترتیب عملیات: فایل موقت → بررسی → نصب Android → بررسی APK نصب‌شده → health check → ثبت اتمی/fsync و فعال‌سازی.

تا قبل از موفقیت مرحلهٔ آخر، pointer و APK قبلی باقی می‌مانند. خطا، لغو نصب یا مرگ فرایند کانفیگ را حذف نمی‌کند. session و nonce نصب در journal ثبت می‌شوند و هنگام بازگشت به مدیر افزونه reconcile می‌شوند. فعال‌سازی هنگام استفادهٔ زنده از افزونه مسدود است.

Rollback، APK قبلی را دوباره بررسی و health-check می‌کند و سپس جای active/previous را عوض می‌کند. Repair نسخهٔ staged/active را بررسی می‌کند؛ Retry برای نصب ناتمام/خراب وجود دارد. Remove با تأیید Android نسخهٔ فعال را حذف می‌کند؛ نسخهٔ قبلی برای Rollback و کانفیگ‌ها برای reinstall باقی می‌مانند. حذف نسخه‌های قدیمی‌تر از تنظیمات Android ممکن است؛ اندازه و سیاست پاک‌سازی چند نسخه در فاز اندازه‌گیری تعیین می‌شود.

Backup شامل ID، نسخهٔ فعال/درخواستی، settings و کانفیگ‌هاست. APK، کلید اعتماد و pointer نصب داخل Backup نمی‌روند. Restore فقط داده و راهنمای reinstall را برمی‌گرداند، نه اعتماد به یک APK.

## ۱۰. فایل‌ها و اعتبارسنجی

فهرست دقیق فایل‌های افزوده/تغییریافته در [CHANGED_FILES.txt](CHANGED_FILES.txt) ثبت شد. گروه‌های اصلی:

- `plugin-api/`: قرارداد Java و lifecycle امن سرویس.
- `app/.../plugins/`: مدل، Trust، Manager، IPC، Runtime، VPN service، UI، Notifications و حفظ کانفیگ.
- `engine/CapabilityRegistry.kt`، CoreManager و EngineTester: تفکیک Engine/Capability و جلوگیری از تست اشتباه پروفایل Plugin با Xray.
- ConfigParser، ForeignImport، DecoderRegistry، ProfileValidator، ConfigNormalizer، ConfigStore و ConfigBuilder: نگه‌داری full config، تشخیص تکراری مستقل و Backup.
- MainActivity، VpnLauncher، GozarVpnService و Vpnbridge: ورودی UI، نصب و اتصال و تحویل کنترل بدون دست‌کاری pin Coreها.
- manifest و تنظیمات Gradle: فقط معرفی API module و componentهای جدید؛ Gradle اجرا نشده است.
- تست‌های قرارداد و اسناد این پوشه.

۱۷ تست محدود JVM برای manifest/امضا/ناشر/ABI/API/pin/capability، حفظ نسخهٔ سالم در شکست health check، Rollback، import کامل YAML/JSON/ShadowQUIC و بقای استخراج قبلی Clash موفق بودند. SDK Java و بخش غیر Compose میزبان در یک بررسی **مجزا** با API مرجع Android 15 و stub وابستگی‌های اپ، از نظر type/API بررسی شدند. Kotlinهای تغییرکرده syntax-parse و XML/JSON بررسی شدند؛ `git diff --check` نیز بررسی شد.

این بررسی‌ها **Build اپ، آزمون واقعی PackageInstaller/Binder روی دستگاه، اندازه‌گیری APK، اثبات اتصال native یا regression کامل 1.1.1 نیستند**. gateهای Android/ABI/lifecycle در API.md ثبت‌اند و برای مرحلهٔ نهایی مجاز باقی می‌مانند. هیچ workflow یا tag/release برای این فاز درخواست نشد.

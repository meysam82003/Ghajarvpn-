# ادامهٔ صندوق و سهمیهٔ محلی — ۲۰۲۶-۱۰-۰۳

مبنای این نوبت `bbd8177c24ddfdfa8679035db69087df81391a89` روی همان `work/1.1.1-phase1` است. remote مجدداً بررسی شد؛ reset، تغییر AAR، build APK، CI، Tag یا Release انجام نشد. کد این نوبت مستقل و تحت مجوز GPL پروژه است؛ هیچ کد AGPL از ZedSecure اقتباس نشده است.

## تغییر واقعی

| تعهد | قبل | اکنون | فایل‌های اصلی | وضعیت |
|---|---|---|---|---|
| نگه‌داری سیاست صندوق در UI | صفحه با List<ProxyConfig>، facade پروفایل محدود را رد می‌کرد | صفحه مستقیماً VaultRepository/VaultEntry را باز/ذخیره/merge/export می‌کند؛ metadata و policy باقی می‌مانند | MainActivity.kt، VaultRepository.kt | پیاده‌سازی‌شده در سورس؛ UI کامل درخواست‌شده هنوز ناقص |
| اتصال بدون استخراج | تنها انتقال پروفایل به فهرست عادی | grant تصادفی کوتاه‌عمر در حافظه؛ Intent فقط شناسه دارد؛ CoreManager.prepare و ConfigBuilder موجود؛ سرویس grant را یک بار مصرف می‌کند | VaultRuntime.kt، VaultConnection.kt، MainActivity.kt، GozarVpnService.kt | مسیر اجرایی نوشته و بخش مستقل تست شد؛ Android اجرا نشده |
| سهمیهٔ محلی در نشست | تنها تصمیم‌گیرنده/ledger مستقل | بررسی قبل از start، نمونه‌برداری cumulative، ذخیرهٔ delta، قطع موتور در مرز سهمیه/زمان/خطای ledger، نمونهٔ پایانی قبل از stop | VaultRuntime.kt، GozarVpnService.kt | پیاده‌سازی‌شده برای قرارداد زیر؛ تست دستگاه باقی |
| جلوگیری از دورزدن از ورودی دیگر | اتصال صندوق هنوز وجود نداشت | Quick Connect، تست مستقل، انتخاب سریع‌ترین و reconnect بیرون از نشست صندوق رد می‌شوند؛ ConfigStore شناسهٔ runtime را persist نمی‌کند | QuickConnect.kt، NetworkAutoConnect.kt، VpnLauncher.kt، EngineTester.kt، CoreManager.kt، ConfigBuilder.kt، ConfigStore.kt | سورس بررسی شد؛ آزمون واقعی tile/widget/device باقی |
| فایل موقت sing-box | نام ثابت؛ برخی خروج زودهنگام ممکن بود فایل را نگه دارند | نام تصادفی، permission 0600، cleanup در finally و stop، حذف بقایای شناخته‌شده در startup، پاک‌کردن lastSpec در stop | engine/SingBoxController.kt | typecheck کنترلر واقعی پاس؛ filesystem/process Android باقی |
| duplicate و مصرف | Keep both می‌توانست ID مصرف تازه بگیرد | usageId جدا از entry ID و داخل دادهٔ authenticated؛ Keep both/Replace مصرف موجود را حفظ می‌کنند | VaultModels.kt، VaultRepository.kt | تست بازخوانی/مصرف پاس |

## قرارداد دقیق اتصال

- پشتیبانی runtime این نوبت برای مسیرهای Xray و sing-box است. OpenConnect و DNS tunnelهایی که EngineRouting به sing-box می‌فرستد همین مسیر را دارند؛ اعتبارسنجی و binary لازم همچنان توسط موتور بررسی می‌شوند. این جمله اثبات اتصال به gateway واقعی نیست.
- OpenVPN، IKEv2، Psiphon، Tor، Aether و افزونه‌ها هنوز adapter نشست صندوق ندارند و با دلیل مشخص رد می‌شوند. خود مسیرهای عادی این موتورها حذف نشده‌اند.
- chain وابسته به پروفایل بیرون صندوق، اشتراک گوشی، split/direct/YouTube-direct و onion سراسری در اتصال صندوق فعلاً پذیرفته نمی‌شوند؛ تنظیم کاربر بی‌صدا تغییر نمی‌کند.
- grant پس از ۱۲۰ ثانیهٔ انتظار منقضی می‌شود؛ قفل صفحه grant مصرف‌نشده را حذف می‌کند. اگر مجوز VPN باعث background و قفل شود، کاربر پس از اعطای مجوز باید صندوق را دوباره باز کند. نشست شروع‌شده با قفل UI قطع نمی‌شود؛ stop/process death مجوز حافظه‌ای را از بین می‌برد.
- بازگشت به صفحه، entry فعال را از شناسهٔ حافظه‌ای همان نشست پیدا می‌کند. حذف اصل پروفایل فعال پیش از قطع آن مجاز نیست. ساخت duplicate بی‌صدا سیاست جدید را دور نمی‌اندازد؛ خطای duplicate نشان می‌دهد.
- payload به ConfigStore/Intent منتقل نمی‌شود. موتور Xray از JSON حافظه‌ای استفاده می‌کند؛ sing-box به فایل خصوصی موقت نیاز دارد. کپی‌های String/JCA در JVM تضمین zeroization ندارند.

## سهمیه و زمان؛ مرز ادعا

سهمیه **Device-local limit** است. Xray از `outbound>>>proxy>>>traffic>>>uplink/downlink` استفاده می‌کند؛ نام و semantics منبع در `gozarcore.go` و تولید stats/policy در ConfigBuilder بررسی شد. این شمارش حجم محتوای خروجی است، نه الزاماً حجم صورتحساب پنل یا overhead لینک.

نمونه‌برداری سرویس هر یک ثانیه است. در فاصلهٔ دو نمونه و تأخیر scheduling امکان عبور از حد وجود دارد؛ «قطع دقیق در همان بایت» یا سهمیهٔ server enforced ادعا نمی‌شود. هنگام تشخیص اتمام، موتور متوقف می‌شود؛ مسیر blocking TUN برقرار می‌ماند تا Stop صریح. اگر Android TUN جایگزین را نپذیرد نیز forwarding متوقف و descriptor قبلی نگه داشته می‌شود. این رفتار هنوز آزمون دستگاه می‌خواهد.

شمارندهٔ zeptun فقط ترافیک TUN را می‌بیند؛ probe از SOCKS محلی sing-box آن را دور می‌زند. بنابراین **سهمیهٔ حجمی sing-box فعال نشده است**. پیش‌نیاز: stats قابل اتکای تمام outboundهای sing-box یا adapter حسابداری شامل همهٔ مسیرهای probe/data. محدودیت زمانی در همان نشست قابل اجراست. نبود این شمارنده را «منتظر Build» نمی‌نامیم؛ کار توسعه‌ای لازم است.

FIRST_CONNECT پس از پاسخ HTTPS 204 واقعی از SOCKS موتور فعال می‌شود؛ DNS مقصد از SOCKS، TLS با CA/hostname verification معمول انجام می‌شود. process started/readiness به‌تنهایی timer را شروع نمی‌کند. تست کاربر نیز از همین نشست است؛ موتور تست جدا با سهمیهٔ مستقل ساخته نمی‌شود. probe از پورت واقعی sing-box یا MixedPort مربوط به Xray استفاده می‌کند.

usageId، اولین import و اولین پاسخ موفق در ledger اتمیک می‌مانند؛ reconnect شمارنده را کم نمی‌کند. در نشست، ساعت monotonic از عقب‌بردن ساعت برای متوقف‌کردن timer جلوگیری می‌کند. clock rollback بین processها، clear-data، reinstall، root و client تغییریافته همچنان محدودیت ذاتی Local هستند. توقف ناگهانی process پیش از آخرین flush ممکن است بخشی از مصرف آخرین بازه را از دست بدهد.

Server mode به‌صورت UNKNOWN و غیرقابل اتصال باقی است. قرارداد و verifier موجود، adapter تولیدی/کلید معتبر/ثبت دستگاه/رزرو parent/قطع credential سمت سرور نیستند. برای چهار سهم مستقل ۵GB، API پنل برای child credential یا gateway حسابداری واقعی و کلید امضای snapshot لازم است؛ provider implementation و یکپارچه‌سازی آن نیز هنوز کار توسعه‌ای دارند.

## آزمون‌های این نوبت

- `run_host_checks.py` با private fixtures فعال: **۱۴۳ تست app + ۲۲ تست log/browser پاس**. ۱۰ تست جدید VaultRuntimeTest شامل مرز ۹۰→۱۰۰، deny شروع بعدی، cumulative/reset/final sample، first-success activation، clock rollback در نشست، grant یک‌بارمصرف و binding کانفیگ، expiry/lock/process-reference، policy/reconnect، خطای I/O و duplicate usage identity است. این‌ها اجرای Android نیستند.
- `check_remaining_sources.py`: **۱۷ تست renewal پاس**؛ Java API/Mihomo و کنترلرهای Kotlin واقعی، همچنین VaultConnection با state/Android collaboratorهای صریح host-fixture، typecheck شدند. Compose و سرویس صرفاً syntax-check شدند؛ full Android/Compose typecheck انجام نشده است.
- `wireguard_loopback.py` دوباره روی Xray رسمی 26.3.27 اجرا شد: **پنج سناریوی انتقال/شکست پاس**؛ TCP IPv4/IPv6 از دو peer، DNS از WireGuard، UDP و نبود fallback پس از مرگ peer. این regression مسیر عادی است؛ آزمون سهمیهٔ Android یا backend نیست.
- خروجی جدید: `test-results/vault-runtime-host.txt`، `vault-runtime-source.txt` و `wireguard-after-vault.json`. شمار آزمون checkpoint قبلی به‌عنوان نتیجهٔ تازه جمع نشده است.

## کارهای توسعه‌ای باقی‌مانده

Biometric/local wrap و password fallback آن؛ انتخاب کامل auto-lock؛ general file association/intent برای GSB؛ backup سراسری encrypted blobs و portable backup؛ export انتخابی و password مستقل export؛ wizard کامل با تاریخ سفارشی/واحدهای حجم/مدت/دستگاه، جست‌وجو و فیلتر و ویرایش metadata؛ نمایش خوش‌خوان واحدها و session/plan در همهٔ صفحات؛ adapter تمام موتورها؛ byte accounting کامل sing-box؛ server provider/registration/signed refresh/parent transaction و enforcement. امکانات این فهرست فقط با افزودن فیلد یا متن، تکمیل‌شده محسوب نمی‌شوند.

تست Android برای consent، process death، File permissions، navigation/قفل UI، قطع در سهمیه، blocking TUN، تعویض شبکه، tile/widget و اتصال locked Xray/sing-box/OpenConnect/DNS در فاز مجاز لازم است. هنوز نمی‌توان گفت «از فازهای ۱ تا ۶ کار توسعه‌ای باقی نمانده».

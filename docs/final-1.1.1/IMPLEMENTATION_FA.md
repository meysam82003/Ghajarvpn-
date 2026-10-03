# مرحلهٔ نهایی 1.1.1 — تغییرات و حدود اعتبارسنجی

مبنای توسعه: `72c82a3199d37f9836e3fd8e5a36df1b62c09076`؛ worktree قبلی دست‌نخورده نگه داشته شد. نسخهٔ رسمی 1.0.10 دارای code=30025 است؛ این نسخه code=30026 دارد. این سند ادعای انتشار یا PASS دستگاه نیست.

## سهمیهٔ sing-box

پین `132b38e9caaba1a1959354d518e54d2d08419afe` با patch قابل بازتولید در `third_party/sing-box/patches` ساخته می‌شود. byte counterهای اتمیک روی TCP/UDP data path هستند؛ اتصال‌های بسته‌شده یا پاک‌شدن history شمارنده را صفر نمی‌کنند. endpoint فقط loopback، دارای secret تصادفی هر process و احراز هویت Clash است. probe روی inbound جدا با SOCKS5 authentication اجرا و از quota حذف می‌شود. این secret در فایل موقت خصوصی 0600 موتور قرار می‌گیرد و بعد از startup حذف می‌شود؛ export/backup رمز plaintext موتور را نمی‌نویسد.

`POST /ghajar/freeze` I/O تازه را رد، socketهای جاری را می‌بندد، پایان عملیات شمارشِ در حال اجرا را صبر و snapshot نهایی می‌دهد. Android پیش از stop/reconnect آن را ثبت می‌کند. generation مستقل مانع اشتباه بین processهاست. FIRST_CONNECT با نخستین payload واقعیِ مسیر اندازه‌گیری‌شده یا پاسخ probe تأییدشده آغاز می‌شود؛ مسدود بودن endpoint تست نمی‌تواند مصرف واقعی را بدون شروع زمان نگه دارد. reset و جمع Long بدون overflow منفی مدیریت می‌شوند. quota از ledger پاک نمی‌شود. علامت session durable پیش از شروع نوشته می‌شود؛ پس از process death یا خطای final flush، entitlement به‌جای مصرف رایگان قفل می‌ماند. این حالت به اعتبار تازهٔ صادرکننده نیاز دارد، نه کلید «صفر کردن مصرف».

حجم، payload عبوری TCP/UDP است؛ سربار IP/TLS/DNS outer tunnel و صورتحساب سرور نیست. نمونه‌برداری هر یک ثانیه است و scheduling/timeout API می‌تواند overshoot ایجاد کند. قطع دقیق روی همان byte ادعا نمی‌شود. بعد از تشخیص quota/time، forwarding متوقف و مسیر blocking حفظ می‌شود. profile کامل یا Tailscale و adapterهای فاقد قرارداد quota در صندوق فعال نیستند؛ مسیرهای عادی آن‌ها حذف نشده‌اند.

## صندوق و پشتیبان

MB/GB/TB (توان دو)، Unlimited؛ Hours/Days/Months (۳۰ روز صریح)، زمان سفارشی محلی با رد ساعت مبهم DST و Unlimited؛ سه زمان آغاز، note/privacy/policy و حذف اصل پس از ذخیرهٔ verify شده. نمایش مصرف، باقی‌مانده، درصد، آغاز، انقضا و زمان باقی‌مانده اضافه شده است.

Export انتخابی/همه با رمز مستقل، سیاست و usageId را پس از decrypt دوباره مقایسه می‌کند؛ SAF خروجی ciphertext نیز دوباره خوانده می‌شود. Full Backup v6 فقط blob رمزدار صندوق و snapshot مصرف را در بکاپ رمزدار می‌گذارد؛ رمز صندوق ذخیره نمی‌شود. Restore ابتدا همه را verify می‌کند، ledger را فقط به‌صورت monotonic merge و سپس blob را atomic replace می‌کند. خطای جایگزینی blob، صندوق سالم قبلی را حفظ می‌کند؛ crash میان ledger و blob ممکن است محافظه‌کارانه مصرف بیشتری باقی بگذارد، هرگز کمتر. این قرارداد atomic مربوط به صندوق است؛ ادعای تراکنش واحد filesystem برای همهٔ storeهای قدیمی برنامه نیست.

## واردکننده‌ها

BPF type2 از قرارداد SFA `5c7b4ce969b926063737d059edf7b256c8f56ed0` پیروی می‌کند: URL پاسخ JSON خام می‌دهد؛ interval دقیقه و حداقل۱۵ است. فقط HTTPS بدون redirect/userinfo، DNS عمومی اعتبارسنجی‌شده و همان آدرس برای اتصال؛ حد۸MiB، timeout و stale/error/update state. خطا config قبلی را نگه می‌دارد. Full Config و Extract گزینه‌های جدا دارند؛ TUN sing-box بدون PlatformInterface همچنان واضح unsupported است و حذف نمی‌شود.

Full Xray از قرارداد موجود XRAY_TUN_FD استفاده می‌کند؛ outbounds/routing/DNS خام حفظ می‌شوند. config بدون inbound یا تک TUN پذیرفته می‌شود؛ listenerهای دیگر، فایل خارجی و API/reverse جانبی صریح رد می‌شوند. NPV lock/policy در استخراج باقی می‌ماند؛ این حفاظت اثبات امضای ناشر نیست.

## OpenConnect و موارد عمداً غیرفعال

گزینه‌های عمومی پین شامل cookie، CA/certificate/key/password، authgroup/pin/flavor، DPD/MTU/reconnect، keepalive/compression/HTTP/XML، user agent/OS/hostname/version، form entries و PFS/legacy با مدل و generator موجود تطبیق داده شدند. OIDC از گزینهٔ فعال UI حذف شد چون executable قرارداد authenticated challenge ندارد؛ TOTP/stoken و username/password باقی‌اند.

Biometric بدون qualification دستگاه فعال نشده؛ password مسیر اصلی است. NPVTSUB1 و signature/trailer بدون قرارداد معتبر، server-side quota بدون provider/credentials، xDNS بدون wire contract/server، Aether H2 over Tor بدون اصلاح DNS upstream و SSO/HOTP بدون bridge و gateway، امکانات فعال معرفی نمی‌شوند. Production pluginها بدون کلید ناشر جعل نمی‌شوند. Dynamic launcher icon و Desktop خارج این انتشارند.

## اعتبارسنجی

Instrumentation واقعی برای modal download/verify، APK خراب/هش/بسته/امضای اشتباه، notification toggle و RemoteViews، palette/actionهای دو widget و migration/backup/quota روی storage Android اضافه شده است. CI روی شاخهٔ phase1 باید آن‌ها و crawl/offline Store را اجرا کند؛ صرف وجود تست به معنی PASS نیست. signed artifact شاخه فقط candidate است؛ publish step برای این شاخه اجرا نمی‌شود.

چهار نمونهٔ خصوصی BPF/NPVT/NPVS از فایل‌های قبلی کاربر بازیابی و خارج از مخزن نگه‌داری شدند. suite مربوطه ۱۱ آزمون، شامل privateRegressionFixtures، با صفر failure و صفر skipped پاس شد. فایل‌ها یا secrets آن‌ها به GitHub فرستاده نشدند؛ CI عمومی همچنان نبود ورودی خصوصی را صریحاً skipped ثبت می‌کند. آمار نهایی و نتایج اجرا جدا ثبت می‌شوند.

## یافته‌های Build/lint و اصلاحات بعدی

خطاهای API 28 در DNS-only، permission اعلان Android 13، callback قدیمی Back در دو WebView، StateFlow غیرواکنشی و کاراکترهای کنترل جهت متن اصلاح شدند. خطاهای lint اکنون Build را fail می‌کنند. تنها استثنای موضعی TileService برای API 26–33 است که واقعاً overload PendingIntent ندارد؛ مسیر API34+ از PendingIntent استفاده می‌کند.

دانلود subscription قبلاً گواهی و hostname را بدون گزینهٔ صریح کاربر نادیده می‌گرفت؛ این مسیر حذف شد. TLS پیش‌فرض Android و منع redirect از HTTPS به HTTP برقرار است. تست مستقل با گواهی self-signed این رفتار را می‌سنجد. TrustManagerهای باقی‌مانده در ابزار مشاهدهٔ گواهی/handshake و pin inspector، تأیید امن اتصال فروشگاه یا دانلود subscription محسوب نمی‌شوند؛ خروجی lint آن‌ها همچنان قابل مشاهده است.

هم‌ترازی OpenVPN/strongSwan روی ARMv7 از4KiB به16KiB تغییر کرد. در artifact محلی هر دو ABI دارای34 ELF بودند؛ معماری، dependencyهای native، alignment و metadata/zipalign پاس شدند. این بررسی SIGILL یا اتصال دستگاه را ثابت نمی‌کند.

CI نخست در آزمون واقعی UDP، panic در ExtendHeader پاسخ SOCKS را آشکار کرد. wrapper مانع freeze عمداً Upstream را پنهان می‌کند، اما باید headroom/overhead/MTU packet را منتقل کند؛ این انتقال اضافه شد بدون بازکردن راه bypass شمارنده یا freeze. آزمون TCP/UDP/probe/freeze پس از اصلاح پاس شد. بازتولید محلی فقط monitor رابط سیستم را با PlatformInterface آزمون جایگزین می‌کند، زیرا میزبان netlink ندارد؛ socketها، core، API و شمارنده واقعی‌اند. CI همان executable عادی را بدون این جایگزینی اجرا می‌کند.

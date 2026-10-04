# فاز ۶ — بررسی نهایی توسعهٔ 1.1.1

مبنا: `8c12f6b57350170fc0e4e648bfad950d017e8179`، اسناد Audit فاز ۲ و گزارش‌های فازهای ۳ تا ۵. Source tag نسخهٔ منتشرشدهٔ `1.0.10` نیز برای تطبیق خوانده شد. هیچ APK، AAR یا Core محصولی ساخته نشد؛ Gradle/CI، Tag جدید و Release اجرا/ایجاد نشدند. اجرای CLI رسمی Xray، تست مستقل JVM و انتقال دادهٔ loopback انجام شد.

**تصمیم پیش‌فرض: Xray 26.3.27 حفظ شد.** شرط «قبولی کامل regression، شامل مسیر Android» برای ارتقا احراز نشده است. این تصمیم ادعای خرابی غیرقابل‌اصلاح upstream نیست. تفاوت schema که مربوط به Ghajar بود بررسی و اصلاح/آزمون شد؛ جایگزینی باینری فعلی صرفاً با تغییر go.mod انجام نشده است.

## Xray و Regression

- `go.mod` و metadata استخراج‌شده از `app/libs/ca.psiphon.aar` هر دو `github.com/xtls/xray-core v1.260327.0` را نشان می‌دهند. AAR مشترک **Xray و Psiphon** است؛ SHA-256 آن با Audit و نسخهٔ فعلی یکسان مانده است.
- هر سه باینری Linux x86-64 از Release رسمی دریافت و با digest رسمی تطبیق داده شدند؛ اطلاعات در `XRAY_BINARY_PROVENANCE.json` است. در زمان بررسی، **هر دو 26.7.28 و 26.9.30 prerelease** بودند.
- ۱۲۵ نمونهٔ مصنوعی با مدل، parser و generator واقعی برنامه ساخته شد؛ اطلاعات واقعی کاربران در دسترس نبود و ادعای تست همهٔ فایل‌های خصوصی کاربران وجود ندارد. پوشش شامل VLESS، REALITY/TLS، VMess، Trojan، SS/SS2022، WireGuard، TCP/WS/gRPC/HTTPUpgrade/XHTTP/SplitHTTP، موارد قدیمی QUIC/HTTP، mKCP و تمام headerهای آن، Mux، DNS/DoH/FakeDNS، routing/GeoIP/GeoSite، fragment/noise/mask، Hysteria2، chaining و مسیر مشترک متنی QR/clipboard/subscription/JSON است. تست پردازش تصویر دوربین از تست parser جداست.

| اجرای رسمی `xray run -test -config` | 26.3.27 | 26.7.28 | 26.9.30 |
|---|---:|---:|---:|
| JSON کامل Ghajar، بدون تغییر | ۰ قبول / ۱۲۵ رد | ۸۳ / ۴۲ | ۸۳ / ۴۲ |
| همان JSON با حذف فقط inbound اندرویدی TUN برای میزبان | ۱۰۹ / ۱۶ | ۸۳ / ۴۲ | ۸۳ / ۴۲ |
| بررسی schema کاندیدا + حذف فقط TUN میزبان | ۱۰۹ / ۱۶ | ۱۰۸ / ۱۷ | ۱۰۹ / ۱۶ |

این جدول **جدول اتصال موفق Android نیست**. در 26.3.27، دستور test نیز TUN را ایجاد می‌کند و میزبان `/dev/net/tun` ندارد؛ بنابراین رد شدن JSON کامل، شکست schema همهٔ ۱۲۵ نمونه نیست. نسخه‌های جدید مرحلهٔ test متفاوتی دارند و قبولی آن‌ها اثبات lifecycle/TUN نیست. خروجی خام هر اجرا نگه‌داری شده است.

۱۶ مورد QUIC transport قدیمی و HTTP transport قدیمی از قبل در موتور فعلی هم رد می‌شوند؛ QUIC مورد استفادهٔ Hysteria با این transport حذف‌شده یکی نیست. تغییر خودکار آن‌ها به XHTTP wire-compatible نیست و انجام نشد؛ پروفایل‌ها حذف یا reset نشدند. این موارد نیازمند کانفیگ سازگار سمت سرویس‌دهنده‌اند. مورد اضافی xDNS در 26.7.28 تغییر `domain` به `domains/resolvers` است؛ قابلیت xDNS در UI تولید فعلی از فاز ۴ تأیید/فعال نشده و این نمونه صرفاً بررسی source/schema است.

### اصلاحات واقعی کانفیگ

- **mKCP:** generator قبلی همیشه `kcpSettings.header` و گاهی `seed` می‌فرستاد؛ همین نسخهٔ bundled آن‌ها را رد می‌کند. اکنون مدل قدیمی بدون تغییر دادهٔ ذخیره‌شده به `finalmask.udp` شامل `header-*` و `mkcp-original` یا `mkcp-aes128gcm` تبدیل می‌شود. ماسک اضافی جایگزین این migration نمی‌شود. JSON import نیز seed/header جدید را دوباره می‌خواند.
- برای schema دو کاندیدا، adapter مخصوص آزمون نام‌های `mkcp-legacy` و ترتیب متفاوت اعمال maskها را تبدیل می‌کند. این adapter محصول فعال نیست. **۴۲ انتقال واقعی HTTP روی mKCP loopback**، با سرور 26.3.27 و کلاینت هر سه نسخه، موفق شد؛ ۷ header × دو حالت seed × سه نسخه. این شواهد نشان می‌دهد رد اولیهٔ کاندیداها در mKCP مشکل migration بوده، نه اثبات regression غیرقابل‌حل.
- **REALITY:** `spiderX` قبلاً در generator ثابت `/` بود و در toolkit با path transport مخلوط می‌شد. اکنون فیلد مستقل، default سازگار با backup قدیمی، فرم، URI `spx`، JSON، normalization و export دارد. publicKey/shortId/SNI/fingerprint/flow حفظ شده‌اند. اطلاعاتی که نسخه‌های گذشته پیش از ذخیره از دست داده‌اند قابل بازسازی حدسی نیستند.
- **WireGuard:** endpoint IPv6 با bracket ساخته می‌شود و PSK موجود در مدل به `preSharedKey` می‌رسد. کانفیگ فاقد PSK مقدار جدید دریافت نمی‌کند.

### قابلیت‌های جدید upstream

جدول جزئیات در `XRAY_FEATURE_REVIEW.md` است. MASQUE واقعاً outbound/transport کلاینت CONNECT-IP دارد؛ H3 و H2 بررسی شدند. H2 با انتخاب ALPN فعال می‌شود؛ fallback خودکار H3→H2 از روی وجود این دو مسیر ادعا نشده است. TLS/SNI، اعتبارسنجی certificate و user/pass→Basic/headers واقعی‌اند. چون باینری Android جدید هنوز تأیید نشده، MASQUE جدید Xray و maskهای تازه به‌عنوان قابلیت آماده به UI اضافه نشدند. MASQUE موجود Aether حفظ شد. xDNS، Noise/UDP Hop، TUN، GeoData و DNS جدید صرفاً به علت وجود فایل سورس فعال نشده‌اند.

## اعلان‌ها و مجوزها

- کانال سرویس VPN کم‌اهمیت باقی ماند. کانال جدا برای خطای اتصال با HIGH و رویداد عادی اتصال با DEFAULT ایجاد شد؛ خطاهای تکراری محدود می‌شوند و متن اعلان secret ندارد.
- کانال mixed افزونه تفکیک شد: نصب/به‌روزرسانی ID قبلی را حفظ می‌کند؛ سرویس افزونه `ghajar_plugin_vpn_v1` با LOW دارد. خاموش‌بودن کانال قبلی برای کانال سرویس جدید رعایت می‌شود.
- کانال‌های Store/Service alert با HIGH و اولویت سازگار کنترل شدند. OpenVPN: background/status LOW، درخواست امنیتی HIGH؛ IKEv2 و DNS service LOW؛ دانلود مرورگر LOW. انتخاب کاربر overwrite نشده؛ تنظیمات برنامه راه ورود به تنظیمات Android هر کانال را دارد. Heads-up با DND یا انتخاب کاربر تضمین نمی‌شود.
- Manifest ادغام‌شدهٔ **APK واقعی 1.0.10** نشان داد مرورگر `RECORD_AUDIO` و `ACCESS_FINE_LOCATION` تزریق کرده است. declarations آن حذف و merge guard برای هر چهار permission میکروفون/موقعیت افزوده شد. مسیر درخواست مرورگر و تصمیم قدیمی ALLOW نیز اجازهٔ دسترسی نمی‌دهد؛ callback مردهٔ geolocation حذف شد.
- `READ_EXTERNAL_STORAGE` نیز در host remove شد: UI قدیمی فایل‌مرورکن OpenVPN در source set محصول کامپایل نمی‌شود؛ مسیر Ghajar از document picker استفاده می‌کند. مجوزهای فنی VPN، اینترنت، foreground service، اعلان، دوربین و دسترسی‌های ویژهٔ موجود حفظ شدند. Sharing از interface موجود و Hotspot دستی استفاده می‌کند.
- Manifest ادغام‌شدهٔ **1.1.1 هنوز تولید نشده**؛ بررسی APK/manifest خروجی واقعی فاز ۷ یک gate الزامی است. دوربین تنها مجوز سخت‌افزاری runtime باقی‌مانده در سورس محصول است؛ اعلان برای Android مربوط همچنان درخواست می‌شود.

## حجم واقعی و بسته‌بندی

APKهای منتشرشدهٔ 1.0.10 دریافت و SHA-256 هر دو با SHA256SUMS انتشار تطبیق داده شد. اعداد زیر compressed داخل همان APKها و برحسب MiB هستند، نه اندازهٔ تخمینی 1.1.1:

| بخش | ARM64 | ARMv7 |
|---|---:|---:|
| کل APK | 109.47 | 108.85 |
| Native | 96.01 | 95.39 |
| DEX | 4.08 | 4.08 |
| Geo files | 6.66 | 6.66 |
| Images | 1.28 | 1.28 |
| Fonts | 0.73 | 0.73 |
| Resources | 0.45 | 0.45 |
| سایر assets | 0.02 | 0.02 |

ZIP/signature overhead و ریز هر entry در `SIZE_EVIDENCE.json` ثبت شده‌اند. `ca.psiphon.aar` برابر **68,297,940 bytes** و شامل چهار ABI است، اما APK ARM64 فقط arm64-v8a و APK ARMv7 فقط armeabi-v7a دارد. `libgojni.so` مشترک Xray/Psiphon در APK ARM64 برابر **15,655,447 bytes compressed** و **46,808,904 bytes raw** است؛ در ARMv7 **16,278,767 / 44,718,040 bytes**. نسبت‌دادن تمام این حجم به Psiphon غلط است؛ تفکیک اختصاصی Psiphon بدون build قابل‌بازتولید و مقایسهٔ کنترل‌شده فعلاً معلوم نیست.

ABI split فعلی حفظ شد؛ x86_64 فقط مسیر emulator اختیاری است، universal APK فعال نشد. Nativeهای بزرگ بررسی‌شده `.debug_*` و `.symtab` ندارند؛ strip کورکورانه سود اثبات‌شده‌ای ندارد. duplicate یکسان بزرگ‌تر از 64 KiB در APKها پیدا نشد. R8/minify/resource shrinking از قبل فعال‌اند؛ keep کلی همهٔ فیلدهای `net.gozar.app.**` حذف شد، چون serialization دستی JSONObject است. keepهای JNI/Go/strongSwan/zeptun و reflection واقعی JSch حفظ شدند. هیچ Core bundled یا asset سالمی برای عددسازی حجم حذف نشد. اندازه و صحت R8 نسخهٔ جدید فقط بعد از فاز ۷ قابل تأیید است.

## باتری، Sharing و امنیت

- sampler و Flowهای Live Monitor فقط در lifecycle STARTED جمع‌آوری می‌شوند؛ thermal headroom حداقل ۱۰ ثانیه cache دارد. حلقهٔ پیدا کردن interface در صفحهٔ Sharing نیز با background متوقف می‌شود.
- Sharing خاموش: relay عمومی، timer monitor و inbound اختصاصی `phone-share-in` ساخته نمی‌شوند. تغییر preference از UI یا restore با چرخهٔ teardown/start سریالی و TUN تازه اعمال می‌شود؛ snapshot همان JSON، chain و DNS/routing را نگه می‌دارد. پراکسی loopback عمومی خود برنامه، که کاربرد مستقل دارد، حذف نشده است.
- توقف sharing فوراً نشست‌های relay را می‌بندد. token/credential هر نشست VPN تازه است و بعد از ۸ ساعت هم rotate می‌شود؛ هنگام rotate نشست‌های قبلی قطع می‌شوند. انتخاب interface خصوصی، authentication، عدم fallback مستقیم، remote hostname و ممنوعیت UDP/BIND قبلی حفظ شدند.
- شبکهٔ ناپایدار: backoff از ۱٫۲ ثانیه با رشد نمایی تا ۳۰ ثانیه و reset بعد از یک دقیقه اضافه شد؛ callbackهای تکراری/از دست رفتن شبکه درخواست قدیمی را زنده نمی‌کنند. reconnect sing-box هم coalesce/backoff دارد. اشتباه observer در Wi-Fi→Wi-Fi که network را پیش از capabilities عوض می‌کرد رفع شد.
- لاگ در **ورودی** قبل از حافظه، فایل و Logcat پاک‌سازی می‌شود؛ Basic/Bearer، کلیدها، secret کوتاه، URI و encoded profile پوشش داده شدند. warning/errorهای first-party نیز از همین مسیر می‌گذرند؛ متن فرمان SSH از خطا حذف شد. release debug خاموش و Log.v/d/i خام در R8 حذف می‌شود. صف فایل ۲۵۶ entry، هر پیام محدود، ring محدود و فایل خصوصی/آینهٔ خارجی چرخشی‌اند؛ crash mirror دیگر append نامحدود نیست.
- Trust افزونه بازبینی شد: ناشر صریح، امضای manifest، certificate APK، hash/size، ABI/API، source pin/capability، HTTPS نهایی بدون redirect دلخواه، health check پیش از activation. rollback همان APK قبلی مستقل و مجدداً بررسی‌شده است. trust anchor از Backup وارد نمی‌شود. ناشر production هنوز خالی است؛ **ShadowQUIC/Mihomo بدون APK امضاشدهٔ معتبر نصب‌پذیر اعلام نشده‌اند**. کلید نمونه وارد محصول نشده است.

## Backup و بررسی ایستا

Backup فعلی server/subscription/core prefs، metadata/settings افزونه، appearance و home/store/settings layout و preference اشتراک را نگه می‌دارد. binary افزونه، active slot/session، رمز موقت و token داخل آن نیست. اکنون restore کامل حتی از backup قدیمی فاقد کلید sharing نیز نشست موقت را invalidate و credential را regenerate می‌کند. default `spiderX` افزایشی است و هیچ preference قدیمی reset نشده است. سیاست محافظت از backup تغییر نکرده است.

بازبینی first-party کد تغییرکرده، routes/اعلان‌ها، مجوزهای source set، trust، config persistence، logging و lifecycle انجام شد. موارد `placeholder` باقی‌مانده در first-party فیلد راهنما/تصویر جایگزین UI هستند؛ سورس محصول جدید دارای TODO اجرایی یا fake integration افزوده نیست. test fixtureها فقط زیر test/validation و با credential مصنوعی‌اند. سورس vendor/test upstream از این ادعا مستثنی است؛ پاک‌کردن بی‌دلیل TODO یا کلید fixture upstream انجام نشد. این بررسی یک ادعای اثبات نبودن هرگونه باگ در کل محصول نیست.

## نتیجهٔ آزمون و Gateهای فاز ۷

**۹۵ تست مستقل JVM** (۷۴ config/share/plugin + ۲۱ logging/permission)، **۴۲ انتقال واقعی mKCP**، بررسی syntax فایل‌های Kotlin تغییرکرده، type-check مستقل PhoneSharing/relay و XML/diff checks انجام شد. مرز و دستورهای بازتولید در `VALIDATION.md` است.

باقی‌مانده‌های الزامی پیش از تأیید محصول:

1. Build/merged manifest/R8 و native ABI/16 KiB واقعی؛ هیچ ادعای قبولی Android از test میزبان نتیجه نمی‌شود.
2. نصب/ارتقای 1.0.10→1.1.1، restore/process death، TUN ownership و stop/reconnect سریع؛ خصوصاً تغییر Sharing در اتصال زنده و هم‌زمان با قطع VPN.
3. دو دستگاه واقعی: خروجی IP VPN، DNS/IPv6، قطع VPN بدون fallback، interface change و rotate رمز؛ socket loopback جای این آزمون نیست.
4. اعلان‌ها در Android/OEMهای هدف، انتخاب قبلی کانال، DND و رد permission؛ دوربین و document picker/OpenVPN.
5. اتصال به endpoint واقعی REALITY/TLS/WireGuard/SS2022/XHTTP و سایر protocolها. corpus فقط بخشی از regression موردنیاز را پوشش داده است.
6. اگر ارتقای Xray مجدداً مطرح شد: provenance قابل‌بازتولید AAR مشترک با حفظ patchهای Psiphon، migration adapter واقعی نسخهٔ منتخب، تمام acceptanceهای Android و featureهای experimental. تا آن زمان **26.3.27 default** است.
7. Pluginهای تازه همچنان نیازمند build/امضای ناشر معتبر و acceptance روی دستگاه‌اند؛ محدودیت INPROXY/Conduit و xDNS/زنجیرهٔ Aether–Tor فاز ۴ دور زده نشده است.

فهرست فایل‌های این فاز: `CHANGED_FILES.txt`.

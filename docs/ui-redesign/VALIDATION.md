# گزارش بازطراحی UI — GhajarVPN

کد UI واقعی روی شاخهٔ `ui-redesign-gpt` پیاده شده است. Build، ۳۴۷ تست واحد و ۶ تست Android موفق‌اند. هر ۱۳ تصویر خروجی Android بازبینی شده است. حدود تأیید عملکرد و کارایی در پایین صریح آمده‌اند.

## ۱. Baseline و محدوده

- SHA اولیهٔ شاخه و `origin/main`: `eb34c4c609c4926a75710c3d5f1b7f33dfe1e6d8`.
- working tree اولیه پاک و diff نسبت به main خالی بود؛ کامیت اختصاصی یا نیاز به merge وجود نداشت.
- تگ annotated با نام `1.0.0` وجود دارد و به `a4292dc8cef1f9a5ca0b1c46036f7a79a8be4e3f` اشاره می‌کند.
- `main` تغییر داده نشد و در بررسی مجدد همچنان همان SHA اولیه را داشت. هیچ release یا tag جدیدی منتشر نشد.
- همهٔ متن‌های ضمیمه، ۱۳ اسلاید PowerPoint و هر ۹ PNG پیش از کدنویسی خوانده/دیده شدند. فایل‌های مستقل با نسخهٔ ZIP یکسان‌اند؛ تصاویر ۰۲ و ۰۹ یکسان‌اند.
- SHA کد Buildشده و آزموده‌شده: `81e6325ea09cd47976bc9103d40c02b8e212c436`. کامیت پس از آن صرفاً این گزارش را ثبت می‌کند.
- فهرست اولیهٔ قابلیت‌ها: [AUDIT.md](AUDIT.md).

## ۲. صفحات پیاده‌شده

| صفحه | پیاده‌سازی واقعی |
|---|---|
| خانه | لوگوی موجود، کنترل واقعی اتصال/قطع/reconnect، انتخاب سرور، نرخ و مجموع ترافیک، کارت metadata اشتراک در صورت وجود، میان‌بر سرورها/علاقه‌مندی/رایگان/سریع‌ترین |
| کانفیگ رایگان | مقصد مستقل؛ دریافت و اعتبارسنجی واقعی، پیشرفت/خطا، تعداد ذخیره‌شده، مرتب‌سازی با پینگ اندازه‌گیری‌شده، انتخاب/علاقه‌مندی/اتصال |
| سرورها | جست‌وجو، فیلتر ماندگار، علاقه‌مندی، انتخاب صریح و حفظ ابزارهای واردکردن، تست، مرتب‌سازی و مدیریت |
| فروشگاه | کیف پول از API، کارت‌های افقی پلن، حفظ انتخاب‌ها و ورودی‌ها، همان ViewModel و backend خرید؛ نمایش ورود در نبود حساب |
| تنظیمات | گروه‌بندی اتصال، ابزارها، عیب‌یابی، مصرف و برنامه؛ مسیرهای مستقیم OpenVPN، اشتراک‌گذاری، بکاپ، اعلان و ظاهر |
| بکاپ | مقصد مستقل، AES-GCM با رمز انتخابی، بررسی نسخه و حد حجم، پیش‌نمایش، اعتبارسنجی OpenVPN پیش از حذف و بازگرداندن وضعیت قبلی هنگام خطا |
| مصرف داده | بازه و پروفایل، مجموع و نمودار از نمونه‌های ذخیره‌شده، CSV موجود، تفکیک مصرف VPN و کل، توضیح محدودیت آمار هر برنامه |
| اشتراک‌گذاری VPN | HTTP/SOCKS موجود، وضعیت واقعی از IP و پورت‌های شنونده، تنظیمات شبکه، همان QR/دسترسی/start-stop؛ حالت غیرفعال روشن |

طراحی از توکن‌ها و کامپوننت‌های موجود استفاده می‌کند: زمینهٔ تیره، سطوح سبز، حاشیهٔ کم‌کنتراست، کنترل اصلی زمردی و سه مقصد خانه/فروشگاه/تنظیمات. در نمایشگر کوچک، صفحات بلند اسکرول می‌شوند. لوگوی طلایی واقعی مخزن حفظ شده است.

## ۳. حفظ قابلیت‌ها و وضعیت

- انتخاب صریح، autoPilot و autoSelect را کنار می‌گذارد؛ اتصال و reconnect از همان انتخاب استفاده می‌کنند. این رفتار با انتخاب یک endpoint واقعی در جایگاه دوم فهرست آزموده شد؛ endpoint اول انتخاب نشده بود.
- انتخاب OpenVPN برای خانه و اتصال سریع ذخیره می‌شود؛ سرور انتخاب‌شده از موتور فعالِ تأمین‌کنندهٔ آمار جداست.
- جست‌وجو، فیلتر و علاقه‌مندی از storage واقعی می‌آیند. وضعیت مقصدها ذخیره می‌شود؛ checkout هنگام تغییر صفحه از نو ساخته نمی‌شود.
- خانه و ابزارهای پنهان پس از جابه‌جایی از composition خارج می‌شوند؛ فروشگاه برای حفظ وضعیت عملیات باقی می‌ماند. ثبت مصرف مستقیم در ریشه و ثبت پروفایل مصرف Core/IKE در مسیر سرویس انجام می‌شود.
- قوانین کانفیگ رایگان حفظ شده‌اند: منابع ۷۲ ساعت اخیر، حداکثر ۳۵ مورد، حذف تکراری/نامعتبر و اعتبارسنجی با هم‌زمانی محدود.
- بکاپ، حساب/موجودی/حقیقت پرداخت backend را بازیابی نمی‌کند. نسخه، رمز، integrity و داده‌های OpenVPN پیش از اعمال بررسی می‌شوند؛ بازیابی کامل فقط با VPN قطع‌شده انجام می‌شود.

ممیزی سورس و مسیرهای UI نشان می‌دهد VLESS، VMess، Trojan، Shadowsocks، SOCKS، HTTP، Hysteria2، WireGuard، IKEv2، OpenVPN، Psiphon، Tor، Aether، Windscribe، واردکردن subscription/QR/file/clipboard/manual، چندانتخابی، chaining، SSH/SFTP، DNS/Clean IP، نقشه، لاگ، اعلان، ظاهر و per-app routing حفظ شده‌اند. مسیرهای wallet، pending payment، ادامهٔ پیگیری، انصراف، history، refund/credit، تمدید، سرویس‌های خریداری‌شده و پشتیبانی نیز به همان backend و قواعد قبلی وصل‌اند. این ممیزی، ادعای اتصال زندهٔ تک‌تک پروتکل‌ها یا اجرای تمام عملیات مالی نیست.

هیچ پینگ، سرعت، موجودی، پرداخت موفق، اشتراک فعال یا تعداد دستگاه ساختگی به محصول اضافه نشده است. endpoint آزمون تنها در androidTest ساخته، واقعاً استفاده و پس از آزمون پاک/وضعیت قبلی بازیابی می‌شود؛ در APK اصلی دادهٔ نمونه تزریق نمی‌شود.

## ۴. Build و آزمون

[اجرای نهایی CI — موفق](https://github.com/meysam82003/Ghajarvpn-/actions/runs/35452726713)

```sh
./gradlew --no-daemon --stacktrace -Pghajar.emulatorTest=true :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew --no-daemon --stacktrace test
bash scripts/ui-redesign/run_device_smoke.sh
```

فرمان سوم در job دستگاه، پس از دریافت APKها و راه‌اندازی emulator، اجرا شد. ابزارهای محلی: JDK 17، Gradle 9.4.1، SDK 36.1، NDK 28.2، CMake 3.22.1 و SWIG. zeptun و sing-box مطابق commitهای ثابت CI از سورس ساخته شدند. Gradle محلی با launcher محیط و proxy اجرا شد؛ آخرین Build محلی `BUILD SUCCESSFUL` بود.

| آزمون | نتیجه |
|---|---|
| app unit tests | ۲۵۷ موفق، صفر failure/error |
| browser unit tests | ۶۳ موفق، صفر failure/error |
| OpenVPN unit tests | ۲۷ موفق، صفر failure/error |
| Android package identity | موفق |
| Android انتخاب/فیلتر ماندگار | موفق؛ انتخاب صریح حالت خودکار را غیرفعال و تنظیمات را ذخیره می‌کند |
| Android بکاپ | round-trip رمزدار و رد رمز اشتباه موفق |
| Android ناوبری | ۱۳ مقصد، back، خروج از صفحه هنگام refresh واقعی، بررسی مقصد فعال و گرفتن تصویر موفق |
| Android backend | ایجاد نشست واقعی، دریافت کد/token غیرخالی و ذخیرهٔ نشست موفق؛ سپس پاک‌سازی. secret در خروجی ثبت نشده است |
| Android اتصال | کلیک کنترل خانه، اتصال موتور واقعی، پاسخ HTTP واقعی از example.com از داخل core، قطع و reconnect با همان selectedId موفق |
| مجموع Android | ۶ تست موفق، بدون skip |
| بسته‌بندی | هر دو ARM اصلی با ۲۳ کتابخانهٔ بومی؛ امضای هر دو با apksigner معتبر |

موتورهای ARM زیر ترجمهٔ شبیه‌ساز x86 کرش کردند؛ تست دستگاه با ABI بومی x86_64 و موتور واقعی Xray/Psiphon، sing-box، zeptun و strongSwan انجام شد. property آزمون، demo flag نیست. APKهای عادی همچنان ARM هستند. Tor و Aether باینری ARM دارند و در APK مخصوص آزمون x86_64 نیستند؛ هر دو در خروجی‌های ARM تحویلی وجود دارند.

## ۵. کارایی و حدود تأیید

حرکت مداوم کنترل اتصال در حالت ثابت متوقف شد؛ پردازش صفحات پنهان محدود شد؛ فهرست‌های سرور lazy هستند و کار شبکه/رمزگشایی خارج از UI اجرا می‌شود. refresh واقعی مانع ناوبری نشد.

فایل `navigation-frames.txt` از خود فرایند Android هنگام آزمون ذخیره شد: ۶۱ فریم، ۶۱ فریم janky، median برابر ۹۷ms و P95 برابر ۷۰۰ms. این اندازه‌گیری در Debug، آزمون Compose با ساعت مجازی و emulator/SwiftShader ثبت شده است. **این نتیجه تأیید روانی یا ۶۰fps نیست.** مقایسهٔ کارایی روی دستگاه ARM واقعی هنوز انجام نشده و نمی‌توان نبودِ افت فریم روی گوشی را از این اجرا نتیجه گرفت.

محدودیت‌های واقعی باقی‌مانده:

- نصب و benchmark روی گوشی ARM در این محیط انجام نشد؛ runtime اصلی روی Android x86_64 آزموده شد.
- اتصال زندهٔ همهٔ پروتکل‌ها، SSH/SFTP و اشتراک‌گذاری با دستگاه دوم به endpoint/اعتبارنامه/شبکهٔ واقعی مربوط نیاز دارد؛ این موارد همگی end-to-end آزموده نشده‌اند.
- خرید، پرداخت، بازپرداخت و تمدید احراز هویت‌شده به حساب آزمون واقعی نیاز دارند. هیچ عملیات مالی یا موفقیت ساختگی اجرا نشد؛ screenshot فروشگاه، ورود واقعیِ بدون حساب را نشان می‌دهد.
- server load، تعداد دستگاه مشترک و USB routed منبع قابل اتکا ندارند و به‌عنوان قابلیت فعال نمایش داده نشده‌اند.
- آمار per-app از دسترسی Android و بازهٔ آن می‌آید و انتساب دقیق همهٔ بایت‌ها به VPN نیست.

## ۶. خروجی‌ها و شواهد تصویری

دو فایل تحویلی، Debug محلی از SHA کد بالا هستند و امضای release رسمی ندارند. APK ویژهٔ x86_64 صرفاً هدف آزمون است. مسیرهای دریافت فایل‌های محلی همراه پاسخ نهایی ارائه شده‌اند.

| فایل | بایت | SHA-256 |
|---|---:|---|
| `GhajarVPN-ui-redesign-arm64-v8a-debug.apk` | 107073695 | `3e099d3f807cf1161291611d29717335d339939aa98b8b96923d466e899b2636` |
| `GhajarVPN-ui-redesign-armeabi-v7a-debug.apk` | 103731045 | `26f29e348f022cfb4a9c07073257e88fe51ded2006b6fb6211c59d97f3f3b3a2` |

[آرشیو تصاویر و خروجی آزمون دستگاه در CI](https://github.com/meysam82003/Ghajarvpn-/actions/runs/35452726713/artifacts/10586759393)

هر ۱۳ PNG جدید باز شد و محتوای آن با مقصد تطبیق داده شد. برای ثبت تصویر، پس از رسیدن به مقصد، پایان transition و ارائهٔ فریم به نمایشگر صبر می‌شود. تصاویر اولیه‌ای که فریم قبلی را نشان می‌دادند، جزو خروجی نهایی نیستند.

| فایل PNG در بستهٔ تحویل | مقصد/وضعیت واقعی |
|---|---|
| GhajarVPN-01-home.png | خانه؛ قطع و بدون سرور |
| GhajarVPN-02-free-configs.png | رایگان؛ پیش از دریافت |
| GhajarVPN-03-servers.png | سرورها؛ فهرست خالی و ابزارهای واقعی |
| GhajarVPN-04-backup.png | بکاپ؛ رمز هنوز وارد نشده |
| GhajarVPN-05-store.png | فروشگاه؛ ورود حساب لازم است |
| GhajarVPN-06-settings.png | تنظیمات و شمارنده‌های واقعی |
| GhajarVPN-07-data-usage.png | مصرف ثبت‌شدهٔ همین دستگاه؛ VPN صفر و کل از ترافیک واقعی |
| GhajarVPN-08-vpn-sharing.png | اشتراک‌گذاری غیرفعال |
| GhajarVPN-10-openvpn.png | OpenVPN؛ بدون پروفایل |
| GhajarVPN-11-notifications.png | وضعیت واقعی اعلان‌های Android |
| GhajarVPN-12-appearance.png | انتخاب پوستهٔ موجود |
| GhajarVPN-13-ssh.png | SSH؛ بدون سرور ذخیره‌شده |
| GhajarVPN-14-debugger.png | Debugger؛ انتخاب کانفیگ لازم است |

رنگ‌ها، کارت‌ها، سلسله‌مراتب و ناوبری با زبان طراحی مرجع تطبیق داده شدند. داده‌های نمایشی مرجع جایگزین دادهٔ واقعی نشدند؛ صفحهٔ حساب متصل/پلن‌های خریداری‌شده به‌صورت تصویری تأیید نشده است. کنترل‌ها و محتوای طولانی در گوشی کوچک از طریق اسکرول قابل دسترسی‌اند.

## ۷. کامیت‌ها و فایل‌های تغییرکرده

| SHA | پیام |
|---|---|
| `716cbceaa3a32ba5b82d2268339eb06d9647f23d` | ui: refine emerald surfaces and add reusable dashboard components |
| `442650a20cf7847c094d8af686d158fddc6f4f78` | ui: redesign home and settings destinations with persistent server selection |
| `b3f43448602cac613a2b09b33b232d6342e22f0b` | ui: redesign real wallet and plan selection while preserving checkout state |
| `9b06c86794ef26c596278477bbaba360e23be884` | test: verify backup validation and persistent selection; build redesign branch in CI |
| `ca2966623bd57dd9cbed93254639d7238df2bbed` | test: exercise real VPN and backend flows and capture Android UI in CI |
| `56dbfaaf3065b916327bf0ed021a27247189d823` | fix: keep selection, live metrics and accounting independent of hidden pages |
| `6ce19eae8bcaee28b95198b182b498c2a1f8a2ea` | test: isolate device checks and validate the production ARMv7 APK |
| `5fe654779591ef3c42d6192665d1122c2af4be8e` | fix: retain direct accounting offscreen and test native engines on emulator ABI |
| `719364b031c46751669dc8bd672634c5899bb72e` | test: include required strongSwan emulator library and wait for launch overlay |
| `11828db28a09b7b7ac93a2e03f34eb9d9aad0698` | test: exercise home connect control and require exported device screenshots |
| `81e6325ea09cd47976bc9103d40c02b8e212c436` | fix: clarify empty home state and capture fully rendered destinations |

فایل‌های تغییرکرده نسبت به baseline:

```text
.github/workflows/android.yml
app/build.gradle.kts
app/src/androidTest/java/net/gozar/app/ExampleInstrumentedTest.kt
app/src/androidTest/java/net/gozar/app/RedesignStateTest.kt
app/src/androidTest/java/net/gozar/app/RuntimeConnectionTest.kt
app/src/androidTest/java/net/gozar/app/UiRedesignNavigationTest.kt
app/src/main/java/net/gozar/app/ConfigFile.kt
app/src/main/java/net/gozar/app/ConfigStore.kt
app/src/main/java/net/gozar/app/FreeConfigs.kt
app/src/main/java/net/gozar/app/GhajarBackupRestore.kt
app/src/main/java/net/gozar/app/GhajarFreeConfigsScreen.kt
app/src/main/java/net/gozar/app/GhajarHome.kt
app/src/main/java/net/gozar/app/GhajarOpenVpnBridge.kt
app/src/main/java/net/gozar/app/GhajarPremium.kt
app/src/main/java/net/gozar/app/GhajarShopScreen.kt
app/src/main/java/net/gozar/app/GhajarSkin.kt
app/src/main/java/net/gozar/app/GhajarVisuals.kt
app/src/main/java/net/gozar/app/GozarVpnService.kt
app/src/main/java/net/gozar/app/IkeController.kt
app/src/main/java/net/gozar/app/MainActivity.kt
app/src/main/java/net/gozar/app/QuickConnect.kt
app/src/main/java/net/gozar/app/Strings.kt
app/src/test/java/net/gozar/app/BackupValidationTest.kt
docs/ui-redesign/AUDIT.md
scripts/build-singbox.sh
scripts/ui-redesign/real_socks_relay.py
scripts/ui-redesign/run_device_smoke.sh
strongswan/build.gradle.kts
docs/ui-redesign/VALIDATION.md
```

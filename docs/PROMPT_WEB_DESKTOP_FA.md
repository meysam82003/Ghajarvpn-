# پرامپت ساخت نسخهٔ وب (PWA) و دسکتاپ (ویندوز و مک) قاجار وی پی ان

> این فایل دو پرامپت مستقل دارد:
> - **بخش A:** وب‌اپ PWA برای iOS، اندروید، لپ‌تاپ و هر مرورگری.
> - **بخش B:** اپ واقعی ویندوز و مک با هسته‌های اتصال بومی.
>
> بخش «قواعد مشترک» جزء هر دو پرامپت است. برای هر بخش، «قواعد مشترک» به‌اضافهٔ همان بخش را به یک جلسهٔ جدا بدهید. اگر هر دو را به یک جلسه می‌دهید، اول A و بعد B انجام شود.

---

## قواعد مشترک (جزء هر دو پرامپت)

```
در مخزن meysam82003/Ghajarvpn- روی برنچی که برای این جلسه تعیین شده کار کن.

گزارش‌دهی:
- بعد از هر مرحله، یک خط گزارش بده: کار انجام‌شده، درصد پیشرفت کل، و زمان تقریبی باقی‌مانده.
- نظر، پیشنهاد جایگزین یا بحث دربارهٔ رویکرد نده. دقیقاً همین مشخصات را اجرا کن.
- فقط جایی که چیزی از نظر فنی غیرممکن است، آن را صریح در گزارش نهایی بنویس، به همراه دلیل دقیق و کاری که به جایش انجام شد.

مرجع حقیقت (قبل از شروع بخوان):
- اپ اندروید: app/src/main/java/net/gozar/app/ (Kotlin و Compose). رفتار، متن‌ها، ظاهر و جریان‌ها از همین‌جا گرفته شود.
- کلاینت API فروشگاه: GhajarStoreApi.kt، GhajarAccountStore.kt، GhajarLinkFlow.kt، BrandConfig.kt، GhajarPaymentPolicy.kt، SecurePaymentActivity.kt، StoreLinkRouter.kt، GhajarNotificationMonitor.kt، GhajarNoticeBanner.kt، GhajarNotificationSettings.kt.
- بک‌اند (PHP):
  - backend/Faoxima-1.0.0/api/ (index.php، handlers/*، weblink.php، verify.php، qr.php)
  - کران‌ها در backend/Faoxima-1.0.0/cronbot/
  - ارسال پیام تلگرام: تابع sendmessage در backend/Faoxima-1.0.0/botapi.php
  - جدول‌ها در table.php
- جریان اتصال حساب در weblink.php با actionهای generate، status، web_ticket و redeem. همین‌ها استفاده شوند و جریان موازی ساخته نشود.

قیدهای همیشگی:
- رفتار ربات تلگرام، مینی‌اپ فعلی و اپ اندروید نباید هیچ تغییری کند. همهٔ تست‌های فعلی و ./gradlew :app:assembleDebug باید پاس شوند.
- فارسی و RTL کامل، اعداد و تاریخ مثل اپ.
- پالت برند: سرمه‌ای، سبز قاجاری، آبی یخی، طلایی. بنفش فقط به‌عنوان انتخاب اختیاری کاربر، مثل اپ.
- هیچ کلید، توکن یا Secret در کد commit نشود. Secretها از config سرور یا GitHub Secrets خوانده شوند.
- migrationهای دیتابیس idempotent باشند و در table.php و مسیر نصب و آپدیت فعلی ثبت شوند.
- commitهای مرحله‌ای با پیام واضح و push به برنچ تعیین‌شده. PR نساز.

### پنهان‌سازی دامنه و هاست (برای A و B)

۱) دامنهٔ جلویی (Front Domain):
- کلاینت‌های جدید (PWA و دسکتاپ) هرگز مستقیم به STORE_ORIGIN یا دامنهٔ پنل‌ها وصل نشوند.
- یک دامنهٔ جلویی با مقدار قابل تنظیم FRONT_ORIGIN تعریف شود.
- یک reverse proxy به‌صورت کانفیگ nginx آماده در backend/Faoxima-1.0.0/docker/ یا deploy/، با نسخهٔ Cloudflare Worker به‌عنوان جایگزین.
- مسیرها روی دامنهٔ جلویی:
  /api/*، /link/*، /pay/*، /push/*، /s/* (ساب)، /dl/* (فایل کانفیگ)، /app/* (خود PWA)
- همه به origin واقعی منتقل شوند (forward). IP و دامنهٔ origin در هیچ پاسخ، هدر، ریدایرکت، لاگ سمت کلاینت، source map یا فایل JS دیده نشود.
- هدرهای Server و X-Powered-By و هر هدری که هاست را لو می‌دهد حذف شوند.
- Location در ریدایرکت‌ها و هر URL مطلق داخل JSON پاسخ‌ها، از origin به FRONT_ORIGIN بازنویسی شود. این بازنویسی سمت proxy یا یک لایهٔ PHP انجام شود و فهرست کلیدهای بازنویسی‌شده مستند شود.

۲) پروکسی لینک ساب:
- subscription_url واقعی که دامنهٔ پنل (Marzban، PasarGuard، Remnawave و غیره) را دارد، هرگز به کلاینت داده نشود.
- به جایش FRONT_ORIGIN/s/{token} داده شود. token تصادفی و غیرقابل‌حدس باشد، به سرویس کاربر نگاشت شود، و با تمدید یا تغییر لینک در پنل همچنان کار کند.
- endpoint /s/{token} محتوای ساب را از پنل بگیرد و برگرداند، همراه هدرهای مهم برای اپ‌های VPN:
  subscription-userinfo، profile-title، profile-update-interval، content-disposition، support-url، profile-web-page-url
- مقدار profile-title برابر «Ghajar VPN» باشد و support-url و profile-web-page-url به دامنهٔ جلویی اشاره کنند.
- اگر پنل لینک‌های کانفیگ با host پنل برمی‌گرداند، آن‌ها کانفیگ اتصال‌اند و باید دست‌نخورده بمانند. فقط URLهای مدیریتی و ساب پنهان شوند.
- rate limit و کش کوتاه (۶۰ ثانیه) داشته باشد.
- با «بازسازی لینک» یا revoke در اپ و ربات، token قبلی باطل شود.

۳) پرداخت:
- payment_init و callbackها روی FRONT_ORIGIN/pay/* باشند تا درگاه بانکی فقط دامنهٔ جلویی را ببیند.
- callback و return URL هر درگاه در کانفیگ پرداخت (PaySetting) به FRONT_ORIGIN تغییرپذیر شود. مستند کن کدام درگاه‌ها ثبت دامنهٔ callback جدید را لازم دارند.
- پرداخت‌های کارت به کارت، کیف پول، رمزارز و هدیه: کامل داخل اپ و PWA انجام شوند و آپلود رسید هم داخلی باشد. در این حالت هیچ دامنه‌ای دیده نمی‌شود.
- صفحهٔ درگاه بانکی (شاپرک و مشابه) طبق قوانین بانک فقط روی دامنهٔ خود بانک باز می‌شود و قرار دادنش در iframe ممنوع است.
  - در PWA: سیستم‌عامل یک نوار با دامنهٔ بانک نشان می‌دهد. این قابل حذف نیست و فقط دامنهٔ بانک است، نه دامنهٔ ما.
  - در دسکتاپ: صفحهٔ بانک در وب‌ویوی داخلی بدون نوار آدرس باز می‌شود و هیچ دامنه‌ای دیده نمی‌شود.

۴) کلاینت‌ها:
- در کد PWA و باینری دسکتاپ فقط FRONT_ORIGIN باشد.
- در دسکتاپ، رشته‌ها مثل BrandConfig فعلی تکه‌تکه یا مبهم شوند. obfuscation با ProGuard یا R8 برای JVM انجام شود.
- certificate pinning با الگوی CertPin.kt، با دو pin (فعلی و پشتیبان).
- source map در بیلد production منتشر نشود.

### اعلان‌ها (برای A و B)

تمام پیام‌هایی که امروز با sendmessage به کاربر تلگرام می‌رسند، باید به PWA (Web Push) و دسکتاپ (اعلان سیستمی) هم برسند. فهرست حداقلی:
- اعلان حجم و زمان و هشدار پایان: cronbot/NoticationsService.php، notification_expire.php، statusday.php
- غیرفعال یا on-hold یا حذف سرویس: disableconfig.php، on_hold.php، activeconfig.php
- کد تخفیف، فروش ویژه، انقضای تخفیف، هدیه و قرعه‌کشی: discount_expire.php، gift.php، lottery.php، DiscountSell، Giftcodeconsumed
- پیام همگانی ادمین: sendmessage.php، cronbot/sendmessage.php، pinned_messages
- اعلان‌های پنل فروشگاه: جدول notification، NotificationHandler، و noticeFeed که اپ اندروید می‌خواند
- وضعیت پرداخت و تأیید و رد رسید، و نتیجهٔ کران‌های درگاه‌ها: *check.php، payment_expire.php، croncard.php
- تمدید صف‌شده: QueuedRenewalProcessor.php
- پاسخ تیکت
- هر sendmessage دیگری که در گراف فراخوانی پیدا کردی و مقصدش کاربر است، نه ادمین یا گزارش.

پیاده‌سازی:
- لایهٔ Outbox: در تابع sendmessage در botapi.php (و نسخه‌های vpnbot/*/botapi.php)، بعد از ارسال تلگرام، اگر chat_id یک کاربر است، یک رکورد در جدول جدید user_outbox ثبت شود. مشخصات رکورد:
  - user_id، category (volume، time، service، discount، broadcast، payment، ticket، renewal، notice)
  - title، body (متن HTML تلگرام به متن ساده تبدیل شود و دکمه‌های inline keyboard به action داخلی اپ نگاشت شوند)
  - deep_link داخلی (مثلاً /services/{username}، /shop، /discount/{code}، /tickets/{id})
  - created_at
- خطای Outbox هرگز نباید ارسال تلگرام را خراب کند (try/catch و لاگ).
- یک endpoint با Bearer برای خواندن feed اعلان‌ها (به‌صورت cursor، با علامت خوانده‌شده و رد کردن) که با noticeFeed فعلی ادغام شود تا اپ اندروید هم همین را ببیند.
- Web Push:
  - جدول push_subscriptions (user_id، endpoint، p256dh، auth، ua، created_at، last_ok_at)
  - کلید VAPID در config
  - کتابخانهٔ minishlink/web-push با composer
  - یک کران جدید (هر دقیقه) Outbox را به Push ارسال کند، با retry، حذف subscriptionهای 404 و 410، و TTL و urgency مناسب.
- تنظیمات کاربر برای هر category (روشن و خاموش) مثل GhajarNotificationSettings، ذخیره در سرور.
- کلیک روی اعلان، PWA یا اپ دسکتاپ را دقیقاً روی deep_link باز کند.
- برای ادمین: دستور یا تنظیم «ارسال تست Push» برای عیب‌یابی.
```

---

## بخش A — وب‌اپ PWA (iOS، اندروید، لپ‌تاپ و همه‌جا)

```
## هدف
یک PWA بساز که:
- ظاهر و رفتارش دقیقاً مثل اپ اندروید قاجار باشد (نه مثل مینی‌اپ تلگرام).
- روی iOS (15 به بعد، Safari)، اندروید (Chrome، Samsung Internet، Firefox)، ویندوز، مک و لینوکس (Chrome، Edge، Safari، Firefox) کامل کار کند.
- قابلیت‌های آن: ورود با حساب تلگرام، خرید، تمدید، پرداخت، سرویس‌ها، تست رایگان، کیف پول، کد تخفیف و هدیه، تیکت، تاریخچه، اعلان‌ها، شخصی‌سازی.
- بعد از خرید، کانفیگ را با یک لمس به هر اپ VPN نصب‌شده روی همان دستگاه اضافه کند.

## ۱. ظاهر: کپی دقیق اپ اندروید
- توکن‌های طراحی را از GhajarDesign.kt، GhajarLook.kt، GhajarSkin.kt، GhajarAppearance.kt، GhajarVisuals.kt، Typography.kt، Flagcolors.kt، GhajarUiRules.kt و ParticleField.kt به CSS variables استخراج کن:
  رنگ‌ها و همهٔ skinها، تم روشن و تیره، تایپوگرافی، شعاع‌ها، فاصله‌ها، سایه‌ها، glass و blur (haze)، گرادیان‌ها، انیمیشن‌ها و مدت‌زمان‌ها، و ذرات.
- فونت‌ها و تصاویر را از app/src/main/res و assets/ بردار (self-host، بدون CDN خارجی).
- صفحه‌ها و ناوبری را دقیقاً مثل MainActivity.kt و GhajarHome.kt بساز: Home، مارکت و فروشگاه، Checkout، سرویس‌های من، جزئیات سرویس، تمدید، اعلان‌ها، تیکت، تاریخچهٔ پرداخت، تنظیمات، شخصی‌سازی، حساب.
- متن‌ها عیناً از Strings.kt و فایل‌های صفحه‌ها.
- حذف کامل (نه غیرفعال): هر چیزی که فقط با VPN داخل اپ معنا دارد، مثل DNS Lab، SSH و SFTP و ترمینال، Speed Test، Config Center، موتورها، لاگ و کانفیگ رایگان.
- دکمهٔ اصلی Home با همان ظاهر بماند. زدنش برای سرویس انتخاب‌شده، شیت «افزودن به اپ» را باز کند.
- آزمون تطابق:
  - با Roborazzi (Robolectric و Compose روی JVM) از هر صفحه و حالت اپ اندروید screenshot مرجع بگیر.
  - با Playwright از همان صفحه‌های PWA در viewport برابر screenshot بگیر.
  - با pixelmatch مقایسه کن و تا رفع اختلاف قابل‌توجه اصلاح کن.
  - خروجی کنار هم در docs/web-parity/.

## ۲. واکنش‌گرا: هیچ چیزی در هیچ دستگاهی بیرون نزند
- meta viewport: width=device-width, initial-scale=1, viewport-fit=cover. زوم کاربر غیرفعال نشود.
- safe area: env(safe-area-inset-*) برای ناچ، Dynamic Island، نوار خانهٔ iOS و نوار ناوبری اندروید.
- ارتفاع با 100dvh و svh/lvh، نه 100vh. هنگام باز شدن کیبورد iOS با visualViewport، فیلدها و دکمه‌ها زیر کیبورد نروند.
- box-sizing: border-box سراسری، و min-width: 0 روی فرزندهای flex و grid.
- عرض ثابت پیکسلی برای container ممنوع. از clamp() و max-width استفاده کن.
- لینک‌ها، کدها و رشته‌های طولانی: overflow-wrap:anywhere یا ellipsis با دکمهٔ کپی.
- تصاویر و QR: max-width:100%، با aspect-ratio ثابت.
- شیت‌ها و دیالوگ‌ها: حداکثر ارتفاع برابر ارتفاع viewport منهای safe area، با اسکرول داخلی و overscroll-behavior: contain.
- در عرض بیشتر از ۷۶۸ (تبلت و لپ‌تاپ): همان کامپوننت‌ها در ستون مرکزی با حداکثر عرض مناسب، به‌اضافهٔ navigation rail مثل layout تبلت اپ. کشیده نشوند.
- Landscape، foldable، تقسیم صفحه (split view) و Stage Manager پشتیبانی شوند.
- بزرگ‌نمایی متن سیستم تا ۲۰۰٪ و zoom مرورگر تا ۲۰۰٪ نباید چیزی را بشکند.
- تست خودکار الزامی در Playwright. روی همهٔ صفحه‌ها و شیت‌ها در این viewportها:
  320x568، 360x640، 375x667، 390x844، 393x873، 412x915، 430x932، 768x1024، 820x1180، 1024x1366، 1280x800، 1440x900، 1920x1080، و landscape همه‌ی موبایل‌ها
  شرط پاس شدن:
  - document.documentElement.scrollWidth <= innerWidth
  - هیچ عنصر قابل‌مشاهده‌ای bounding box بیرون از viewport افقی نداشته باشد
  - هیچ متنی بریده نشود (scrollWidth > clientWidth بدون ellipsis)
  هر نقض، تست را fail کند.
- تست روی WebKit (در Playwright) برای iOS، Chromium برای اندروید و دسکتاپ، و Firefox.

## ۳. ورود و حساب
- همان جریان اپ:
  - weblink?action=generate کد و session_token می‌دهد.
  - دکمهٔ «ورود با تلگرام»: لینک t.me ربات با start=کد.
  - poll با action=status تا LINKED شود و Bearer برگردد.
- همان حساب تلگرام است، پس هر سرویسی که از ربات یا مینی‌اپ یا اپ خریده شده، همین‌جا دیده شود.
- اگر کاربر از اپ اندروید با web_ticket و redeem آمد، مستقیم وارد شود.
- توکن در IndexedDB یا localStorage، هرگز در URL. خروج و unlink داشته باشد.

## ۴. API، خرید و پرداخت
- همهٔ actionهای GhajarStoreApi.kt پورت شوند، با همان retry، timeout و پیام‌های خطای فارسی:
  countries، categories، time_ranges، services و products، custom quote، service، service_renew_options، service_renew_confirm، purchase، payment_methods، payment_init، pending_payments، crypto_cancel_invoice، transactions، payment_status، test_account_info، test_account_create، اعلان‌ها و notification_dismiss، تیکت‌ها، کد تخفیف (DiscountValidate و DiscountEligible)، کد هدیه، کیف پول و انتقال، آپلود رسید (کارت و رمزارز، با همان سقف حجم و ابعاد).
- همهٔ درخواست‌ها به FRONT_ORIGIN/api/* (same-origin). CORS لازم نیست.
- پرداخت:
  - درگاه بانکی: top-level navigation از FRONT_ORIGIN/pay/* و بازگشت به FRONT_ORIGIN/pay/return. بعد از بازگشت، باز شدن خودکار PWA روی صفحهٔ نتیجه و پیگیری با payment_status و pending_payments.
  - در iOS standalone، بازگشت از درگاه باید داخل همان PWA انجام شود. این را روی WebKit تست کن و اگر لازم بود صفحهٔ return دکمهٔ «بازگشت به قاجار» داشته باشد.
  - کارت به کارت، رمزارز، کیف پول و هدیه: کامل داخل PWA.
  - رفتار و پیام‌ها مثل GhajarCheckoutViewModel و GhajarPaymentPolicy.

## ۵. «افزودن به اپ» (iOS، اندروید، ویندوز، مک، لینوکس)
- فایل web/src/app-links.ts: آرایه‌ای از
  { id, name, icon, platforms[], androidPackage?, store:{ios,android,windows,mac,linux}, build(subUrl,name) }
  افزودن اپ جدید فقط یک خط باشد.
- subUrl همیشه FRONT_ORIGIN/s/{token} است و در query با encodeURIComponent کد شود. NAME = "Ghajar VPN".
- سیستم‌عامل را تشخیص بده (userAgentData، و در نبودش userAgent). اپ‌های همان پلتفرم اول بیایند، با دکمهٔ «همهٔ اپ‌ها».
- روی اندروید، لینک‌ها به شکل intent باشند تا اگر اپ نصب نبود، خودکار به Play یا صفحهٔ دانلود برود:
  intent://...#Intent;scheme=X;package=Y;S.browser_fallback_url=Z;end
- روی iOS و دسکتاپ:
  - scheme مستقیم با تایمر ۱.۵ ثانیه
  - اگر صفحه هنوز visible بود، کارت «نصب این اپ» با لینک فروشگاه یا دانلود نمایش داده شود
- اپ‌ها:
  - قاجار اندروید (com.ghajarvpn.app): scheme happ در AndroidManifest ثبت شده. قالب happ://add/{URL} را با کد اپ (هندلر happ) تأیید کن و دکمهٔ «قاجار» اول فهرست اندروید باشد.
  - اپ دسکتاپ قاجار (بخش B): scheme اختصاصی ghajarvpn://import?url={URL}، اول فهرست ویندوز و مک.
  - iOS:
    - Streisand → streisand://import/{URL}#{NAME}
    - V2Box → v2box://install-sub?url={URL}&name={NAME}
    - Happ → happ://add/{URL}
    - Hiddify → hiddify://import/{URL}#{NAME}
    - FoXray → foxray://yiguo.dev/sub/add/?url={URL}#{NAME}
    - v2RayTun → v2raytun://import/{URL}
    - Shadowrocket → sub://{base64(URL)}#{NAME}
    - sing-box → sing-box://import-remote-profile?url={URL}#{NAME}
    - Karing → karing://install-config?url={URL}&name={NAME}
  - Android:
    - v2rayNG → v2rayng://install-sub?url={URL}&name={NAME}
    - Hiddify، Happ، v2RayTun، Karing، sing-box، NekoBox با قالب خودشان
  - Windows و macOS و Linux:
    - Hiddify، Happ، Karing، sing-box، Clash Verge (clash://install-config?url=) با قالب خودشان
    - v2rayN: کپی لینک
    - مک: V2Box، FoXray و Streisand هم
- قالب هر لینک را از سورس یا مستندات رسمی همان اپ تأیید کن و نتیجه را در web/APP_LINKS.md بنویس. اپی که قالبش تأیید نشد، فقط با «کپی لینک» نمایش داده شود.
- همیشه این‌ها هم در شیت باشند: QR بزرگ، کپی لینک ساب، navigator.share، و دانلود فایل.
- انواع دیگر سرویس:
  - WireGuard: QR متن کانفیگ و دانلود .conf از FRONT_ORIGIN/dl/*
  - OpenVPN: دانلود .ovpn
  - IKEv2: فایل .mobileconfig (com.apple.vpn.managed) برای iOS و مک. برای ویندوز یک اسکریپت PowerShell امضاشده (Add-VpnConnection) یا راهنمای تصویری.
  - برای اندروید: لینک import به اپ قاجار (که IKEv2 و OpenVPN دارد).

## ۶. PWA و نصب
- manifest.webmanifest:
  - name و short_name: «قاجار وی پی ان»
  - id، start_url و scope زیر FRONT_ORIGIN/app/
  - display: standalone، با display_override: ["window-controls-overlay","standalone"]
  - dir=rtl، lang=fa، theme_color و background_color از توکن‌ها
  - آیکن‌ها 192، 512، maskable و monochrome
  - screenshots، shortcuts (فروشگاه، سرویس‌ها، اعلان‌ها)
  - protocol_handlers برای web+ghajar
- iOS: apple-touch-icon، apple-mobile-web-app-capable، status-bar-style، و splash برای همهٔ اندازه‌های آیفون و آیپد.
- راهنمای نصب:
  - iOS Safari: بنر تصویری «Share ← Add to Home Screen». در Chrome یا Firefox iOS، راهنمای باز کردن در Safari.
  - اندروید و دسکتاپ: دکمهٔ نصب با beforeinstallprompt.
  - بعد از نصب، بنر دیگر نمایش داده نشود.
- همهٔ لینک‌های داخلی در scope بمانند تا در حالت standalone هرگز نوار آدرس ظاهر نشود. لینک‌های خارجی (تلگرام، بانک، فروشگاه اپ‌ها) صریح و کنترل‌شده باز شوند.
- Service Worker:
  - precache فایل‌های استاتیک و صفحهٔ آفلاین برند
  - API، پرداخت، /s/* و /dl/* هرگز کش نشوند
  - به‌روزرسانی با پیام «نسخهٔ جدید، بارگذاری مجدد»
- Web Push:
  - روی اندروید و دسکتاپ همیشه
  - روی iOS 16.4+ فقط بعد از نصب روی صفحهٔ اصلی. اگر نصب نشده، راهنمای نصب برای فعال شدن اعلان نمایش داده شود.
  - درخواست مجوز فقط بعد از کلیک کاربر روی «فعال‌سازی اعلان‌ها»
  - setAppBadge برای تعداد اعلان خوانده‌نشده
  - کلیک روی اعلان با clients.openWindow یا focus روی deep_link
- داخل PWA یک مرکز اعلان مثل اپ، با همان دسته‌بندی‌ها و تنظیمات روشن و خاموش.

## ۷. فنی
- پوشهٔ web/ در ریشهٔ مخزن، با Vite + TypeScript + Preact (یا vanilla). خروجی استاتیک بدون source map عمومی.
- CSP سخت‌گیرانه، Referrer-Policy: no-referrer (تا دامنه به درگاه و سایت‌های خارجی لو نرود)، Permissions-Policy، HSTS.
- Lighthouse: PWA پاس، performance موبایل بالای ۸۵، accessibility بالای ۹۰.
- اسکریپت deploy و کانفیگ nginx برای دامنهٔ جلویی (بخش «پنهان‌سازی دامنه»).

## ۸. تست و تحویل
- تست واحد: app-links (همهٔ اپ‌ها و پلتفرم‌ها)، پارسرها، فرمت‌کننده‌ها، بازنویسی URL در proxy، ساخت mobileconfig، Outbox و تبدیل HTML به متن.
- تست PHP برای /s/{token}، Outbox، Push sender و migrationها.
- E2E با Playwright (WebKit، Chromium، Firefox) با API ماک‌شده:
  ورود، خرید، پرداخت بانکی (شبیه‌سازی return)، کارت به کارت با آپلود رسید، کد تخفیف، تمدید، تست رایگان، شیت «افزودن به اپ» روی هر پلتفرم (شبیه‌سازی UA)، و دریافت Push و کلیک روی آن.
- تست واکنش‌گرایی بخش ۲ (الزامی و blocking).
- تست «عدم نشت دامنه»: در بیلد خروجی، پاسخ‌های ماک‌شده و هدرها grep کن که STORE_HOST و دامنهٔ پنل‌ها هیچ‌جا نباشند.
- workflow جدید در .github/workflows/web.yml برای build و همهٔ تست‌ها.
- مستندات: web/README.md (نصب، deploy، دامنهٔ جلویی، VAPID، کران Push) و web/APP_LINKS.md.
- گزارش نهایی:
  - فایل‌های تغییرکرده
  - لینک‌های تأییدشده و تأییدنشده
  - دستورهای لازم روی سرور (composer، migration، کران، nginx، DNS)
  - درگاه‌هایی که ثبت callback جدید لازم دارند
  - هر اختلاف بصری باقی‌مانده
```

---

## بخش B — اپ واقعی ویندوز و مک با هسته‌های بومی

```
## هدف
اپ کامل و واقعاً کارکنندهٔ قاجار وی پی ان برای:
- ویندوز ۱۰ و ۱۱ (x64 و arm64)
- macOS 12 به بعد (Apple Silicon و Intel)

با همان UI، فروشگاه، اعلان‌ها و قابلیت‌های اپ اندروید، و هسته‌های اتصال بومی هر سیستم‌عامل.

قانون هسته‌ها:
- هر هسته‌ای که روی یک سیستم‌عامل به‌طور واقعی build شود و تست E2E اتصالش در CI پاس شود، در آن سیستم‌عامل وجود دارد.
- هر هسته‌ای که نشود، در آن سیستم‌عامل «اصلاً وجود ندارد»: نه در UI، نه در تنظیمات، نه در بسته‌ی نصب، نه به‌صورت خاکستری یا «به‌زودی».
- کانفیگی که به هستهٔ ناموجود نیاز دارد، هنگام import پیام روشن «این نوع کانفیگ در این سیستم پشتیبانی نمی‌شود» بدهد.

## ۱. معماری کد (یک سورس برای اندروید و دسکتاپ)
- Kotlin Multiplatform با دو target: android و jvm("desktop"). Compose Multiplatform برای UI.
- source setها:
  - commonMain
  - jvmCommon (مشترک android و desktop، چون هر دو JVM هستند و HttpURLConnection، org.json و coroutines بدون بازنویسی کار می‌کنند)
  - androidMain
  - desktopMain
- ماژول‌ها:
  - shared
  - app (اندروید فعلی، که فقط از shared استفاده می‌کند)
  - desktopApp
  - desktopService (سرویس دارای دسترسی بالا)
- انتقال به shared:
  - همهٔ UI: GhajarHome، Market و Shop، Cards، Checkout، SettingsHub، Personalize، Skin، Look، Design، Visuals، Typography، ParticleField، NoticeBanner، Tickets، PaymentHistory، ConnectDoctorUi، DnsLabScreen، SftpScreen، TerminalScreen، Strings و بقیه
  - همهٔ منطق: ConfigParser، Subscription، SubscriptionFetcher و refresher، configtoolkit، configcenter، ConfigBuilder، SingBoxConfig، freecfg، GhajarStoreApi، اعلان‌ها، Pinger، ServerProbe، SpeedTest، AutoSelector، ConnectDecision، ConnectDoctor، DnsScanEngine، CleanIP، Checkhost، Ipintelligence و غیره
- abstraction با expect/actual یا interface برای:
  Context، SharedPreferences و EncryptedSharedPreferences (در دسکتاپ: Windows DPAPI و macOS Keychain)، Notification، Widget، VpnService، CameraX (در دسکتاپ: QR از تصویر، clipboard و screenshot)، WebView، Activity، BroadcastReceiver.
- یک interface مشترک VpnEngine (connect، disconnect، state Flow، stats، ping، logs)، با پیاده‌سازی Android (کد فعلی CoreManager و VpnLauncher بدون تغییر رفتار) و Desktop (از طریق IPC با سرویس).
- CoreManager در هر پلتفرم فقط هسته‌های موجود در همان بسته را ثبت کند. UI از روی همین فهرست ساخته شود تا هستهٔ ناموجود هرگز نمایش داده نشود.

## ۲. ظاهر
- همان کامپوننت‌های Compose. در پنجرهٔ باریک، دقیقاً layout موبایل. در پنجرهٔ عریض، navigation rail و ستون محتوا با حداکثر عرض.
- هیچ چیزی بیرون نزند:
  - حداقل اندازهٔ پنجره ۳۶۰ در ۶۴۰
  - تست screenshot در اندازه‌های ۳۶۰x۶۴۰، ۴۸۰x۸۰۰، ۱۰۲۴x۷۶۸، ۱۲۸۰x۸۰۰ و ۱۹۲۰x۱۰۸۰
  - مقیاس DPI ویندوز ۱۰۰، ۱۲۵، ۱۵۰ و ۲۰۰٪، و Retina مک
  - بزرگ‌نمایی متن تا ۲۰۰٪
  - هر برش یا بیرون‌زدگی، تست را fail کند
- دکمه‌های پنجره:
  - ویندوز: title bar سفارشی با رنگ برند و دکمه‌های استاندارد
  - مک: traffic lights بومی
- فارسی RTL و فونت‌های اپ.

## ۳. سرویس دارای دسترسی بالا و IPC
- ویندوز: Windows Service با LocalSystem، نصب‌شده توسط MSI. باینری Go یا Kotlin Native، یا JVM مینیمال.
- مک: launchd daemon با SMAppService، که با تأیید کاربر نصب می‌شود.
- IPC:
  - ویندوز: Named Pipe با ACL فقط برای کاربر نصب‌کننده
  - مک: Unix socket با مجوز ۰۶۰۰ و بررسی peer credential (getpeereid)
  - پروتکل JSON نسخه‌دار
- سرویس فقط فرمان‌های مشخص و اعتبارسنجی‌شده را اجرا کند. هیچ مسیر یا فرمان دلخواه از UI پذیرفته نشود.
- کانفیگ‌ها در پوشهٔ محافظت‌شدهٔ سرویس نوشته شوند.
- بعد از crash یا ری‌استارت، routeها، DNS و proxy سیستم بازگردانده شوند.

## ۴. هسته‌ها

لایهٔ TUN مشترک: sing-box با tun inbound.
- ویندوز: wintun داخلی. مک: utun.
- auto_route، strict_route (ویندوز)، DNS hijack، و جلوگیری از نشت IPv6 و DNS.
- هر هسته‌ای که SOCKS محلی می‌دهد، پشت همین TUN قرار می‌گیرد (همان الگوی zeptun در اندروید).

هسته‌ها، متناظر با EngineId در app/src/main/java/net/gozar/app/engine/CoreManager.kt:

| هسته | منبع | ویندوز | مک |
|---|---|---|---|
| XRAY (VLESS، VMess، Trojan، SS، SOCKS، HTTP، Hysteria2، WireGuard، Reality، XHTTP و غیره) | gozarcore.go و go.mod، build برای windows و darwin (binary یا c-shared) با همان ConfigBuilder و آمار | باید باشد | باید باشد |
| SINGBOX | scripts/build-singbox.sh گسترش‌یافته برای windows و darwin، با tags مثل with_wintun و with_utls | باید باشد | باید باشد |
| PSIPHON | native/Psiphon/ConsoleClient، با PsiphonConfig و انتخاب کشور | باید باشد | باید باشد |
| TOR + lyrebird | tor رسمی دسکتاپ (Expert Bundle) با نسخهٔ pin‌شده، و lyrebird از scripts/build-tor-pt.sh | باید باشد | باید باشد |
| AETHER (MASQUE، WARP، gool) | native/Aether (Rust، cargo) برای x86_64/aarch64-pc-windows-msvc و x86_64/aarch64-apple-darwin | باید باشد | باید باشد |
| DNS_TUNNEL (dnstt، slipstream، masterdns، stormdns، cottendns) | scripts/build-dnstt.sh، build-dns-tunnels.sh و build-slipstream.sh برای windows و darwin | هر کدام که build و تست شد | هر کدام که build و تست شد |
| OPENVPN | openvpn 2.6 (pin) با management interface. ویندوز: ovpn-dco-win، و در صورت نیاز tap-windows6. مک: utun | باید باشد | باید باشد |
| IKEV2 | ویندوز: VPN داخلی سیستم (Add-VpnConnection، rasdial، import گواهی). مک: NEVPNManager با Personal VPN از طریق helper Swift امضاشده با Developer ID؛ اگر entitlement با Developer ID ممکن نبود، charon-cmd از strongswan/ داخل مخزن؛ اگر هیچ‌کدام نشد، حذف از مک | باید باشد | در صورت امکان |
| SSH tunnel (jsch) | همان کد، روی JVM | باید باشد | باید باشد |
| WARP و Oblivion (Warp.kt، OblivionSettings) | از طریق Aether یا WireGuard در sing-box | باید باشد | باید باشد |

- اسکریپت‌های build: reproducible، نسخه یا commit pin‌شده، و بررسی SHA-256 برای هر باینری دانلودی.
- مجوز هر هسته در THIRD_PARTY_NOTICES.md ثبت شود.
- فایل docs/DESKTOP_ENGINES.md: جدول نهایی وضعیت واقعی هر هسته در هر سیستم‌عامل، بر اساس نتیجهٔ تست CI.

قابلیت‌های سیستمی:
- حالت TUN (پیش‌فرض) و حالت System Proxy:
  - ویندوز: WinINet و PAC
  - مک: networksetup
- Kill switch (ویندوز با WFP از طریق sing-box یا قوانین فایروال، مک با pf).
- Split tunneling بر اساس دامنه، IP و پروسه (process_name و process_path در sing-box).
- Auto-connect هنگام شروع سیستم و هنگام تغییر شبکه (NetworkAutoConnect).
- Auto select سرور (AutoSelector)، پینگ، تست پایداری و Connect Doctor.
- DNS Lab، اسکن IP تمیز، Speed Test و Check-host، همه روی دسکتاپ.

## ۵. قابلیت‌های مخصوص دسکتاپ
- Tray یا Menu bar: آواتار قاجار، وضعیت، پینگ زنده، اتصال و قطع، انتخاب سرور، و باز کردن اپ (معادل GhajarWidget).
- اعلان سیستمی برای همهٔ دسته‌های اعلان (بخش «اعلان‌ها» در قواعد مشترک):
  - ویندوز: Toast با AppUserModelID
  - مک: UNUserNotificationCenter
- کلیک روی اعلان، صفحهٔ مربوط را باز کند.
- اعلان‌ها با polling هوشمند از feed، و در صورت امکان یک اتصال SSE یا long-poll سبک روی FRONT_ORIGIN.
- پرداخت داخل اپ (معادل SecurePaymentActivity):
  - وب‌ویوی داخلی با KCEF یا JCEF بدون نوار آدرس، برای درگاه بانکی هم.
  - allowlist دامنه طبق GhajarPaymentPolicy.
  - بازگشت خودکار به صفحهٔ نتیجه.
  - هیچ دامنه‌ای به کاربر نمایش داده نشود.
- Deep link و file association:
  - schemeهای ghajarvpn://، vless://، vmess://، trojan://، ss://، hysteria2://، hy2://، tuic://، wireguard://، happ://
  - فایل‌های .ovpn، .conf و .json
  - همه با اپ قاجار باز شوند و import شوند.
- QR از تصویر، clipboard و screenshot ناحیه‌ای.
- به‌روزرسانی خودکار از GitHub Releases (بر اساس UpdateChecker.kt و GhajarUpdateInstaller.kt)، با بررسی امضا و SHA-256.
- تک‌نمونه (single instance): باز کردن دوباره، پنجرهٔ موجود را جلو بیاورد.
- لاگ با فیلتر (GhajarLogFilter)، بدون نوشتن توکن، لینک ساب یا دامنهٔ origin.

## ۶. بسته‌بندی و انتشار
- ویندوز:
  - MSI و EXE (jpackage یا WiX) با JRE داخلی (jlink مینیمال)
  - نصب سرویس، wintun و ovpn-dco
  - حذف کامل و تمیز (سرویس، درایور، route و proxy)
  - امضای کد با گواهی از GitHub Secrets، در صورت وجود
- مک:
  - DMG جدا برای arm64 و x64، یا universal
  - امضا با Developer ID، hardened runtime، entitlements حداقلی، notarization با notarytool و staple، از طریق Secrets
- اگر Secret امضا نبود، بیلد بدون امضا ساخته شود و در گزارش ثبت شود. بیلد نباید fail شود.
- اندازهٔ بسته بهینه: فقط هسته‌های همان سیستم‌عامل و معماری داخل بسته باشند.
- workflow جدید در .github/workflows/desktop.yml:
  - رانرها: windows-latest، windows-11-arm (در صورت دسترسی)، macos-14 (arm64)، macos-13 (x64)
  - build همهٔ هسته‌ها، تست‌ها، بسته‌بندی، و آپلود artifact
  - انتشار در Releases کنار APK، با الگوی android.yml
- android.yml و APK نباید بشکنند.

## ۷. تست واقعی (الزامی و blocking)
- تست واحد منطق مشترک روی اندروید (JVM)، ویندوز و مک.
- E2E اتصال در CI روی هر رانر ویندوز و مک:
  - داخل خود رانر سرورهای تست بالا بیاور:
    xray (VLESS+Reality، VMess+WS، Trojan، Shadowsocks، Hysteria2)، wireguard-go، openvpn server، dnstt server، و برای ویندوز یک سرور IKEv2 در صورت امکان (strongswan روی رانر لینوکس جانبی یا container).
  - برای هر هسته و هر سرور:
    - اتصال در حالت SOCKS و TUN
    - curl از داخل تونل و بررسی پاسخ
    - تست نشت DNS
    - قطع اتصال و بازگشت کامل شبکه
  - Psiphon، Tor و Aether: حداقل شروع موفق، ایجاد SOCKS و یک درخواست موفق از طریق آن، با timeout منطقی. اگر شبکهٔ رانر اجازه نداد، دلیل دقیق ثبت شود.
  - هسته‌ای که این تست را در یک سیستم‌عامل پاس نکند، از بستهٔ همان سیستم‌عامل حذف شود (قانون هسته‌ها).
- اجرای اپ دسکتاپ به‌صورت headless با smoke test و screenshot همهٔ صفحه‌ها در artifact.
- تست «عدم نشت دامنه»: grep روی باینری و بسته‌ها برای STORE_HOST و دامنهٔ پنل‌ها.
- ./gradlew :app:assembleDebug و همهٔ تست‌های اندروید باید پاس شوند.

## ۸. ترتیب کار و تحویل
ترتیب commitها:
۱) ساختار KMP و انتقال منطق
۲) انتقال UI
۳) سرویس و IPC
۴) هسته‌ها، یکی‌یکی با تست
۵) قابلیت‌های دسکتاپ
۶) بسته‌بندی
۷) CI و انتشار

تحویل:
- docs/DESKTOP_FA.md: معماری، build، Secretهای امضا و notarization، نصب برای کاربر، و عیب‌یابی.
- docs/DESKTOP_ENGINES.md: جدول نهایی هسته‌ها بر اساس CI.
- گزارش نهایی:
  - وضعیت هر هسته در هر سیستم‌عامل
  - فایل‌های تغییرکرده
  - Secretهای لازم
  - هر چیزی که انجام نشد و دلیل دقیقش
```

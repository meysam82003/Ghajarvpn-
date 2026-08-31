# دستور wiring باقی‌مانده — session بعدی (با شل سالم)

> وضعیت: کد آیتم‌های ۱، ۵، ۶ نوشته و تست‌گذاری شد (بدون شل). این فایل دقیقاً
> می‌گوید چه اتصال‌هایی در MainActivity و UI مانده است. پیش‌نیاز: `git pull` روی main.

## فایل‌های جدید (آماده، نیازی به کار ندارند)
| فایل | نقش |
|---|---|
| `app/src/main/java/net/gozar/app/VpnState.kt` | بازنویسی شد: debounce 450ms + قفل اتصال مجدد 700ms + رد رویدادهای کهنهٔ connect/error + `lookupGeneration` برای cancel کردن lookupها |
| `app/src/main/java/net/gozar/app/GhajarWelcomeOverlay.kt` | ولکام جدید: تمام‌صفحه 9:16 با ContentScale.Fit (هیچ برشی از تصویر)، کادر مویی طلایی 0.5dp، **بدون دکمه**، بسته‌شدن خودکار 3.2s، لمس هر‌جا = بستن، fade ورود/خروج |
| `app/src/main/java/net/gozar/app/GhajarChannelRules.kt` | آیتم ۶: استخراج لینک پروکسی از هر تعداد کانال منبع، برند عمومی همیشه `@Ghajarvpn`، برچسب داخلی منبع (هرگز UI)، پذیرش NPVT متنی باز، قفل NPVT شکسته نمی‌شود |
| `app/src/test/java/net/gozar/app/VpnStateGateTest.kt` | ۱۰ تست state machine |
| `app/src/test/java/net/gozar/app/GhajarWelcomeOverlayRulesTest.kt` | ۶ تست ولکام |
| `app/src/test/java/net/gozar/app/GhajarChannelRulesTest.kt` | ۱۰ تست چندمنبعی/npvt |

## ۱) wiring ولکام (الزامی)
در MainActivity جای کامپوزبل ولکام فعلی (که پنل آموزشی + دکمهٔ ورود دارد) فقط:
```kotlin
GhajarWelcomeOverlay(onDone = { showWelcome = false })
```
- کنترل نمایش: همان flag قبلی (`showWelcome`/معادلش) — شرط اولین اجرا هم مثل قبل
- پنل آموزشی و دکمهٔ ورود قدیمی را کامل حذف کن؛ overlay جدید خودش auto-close دارد
- overlay باید بالاتر از همه (بالای bottom bar و اعلان‌ها) رندر شود

## ۲) wiring cancel lookup (پیشنهادی، ۵ خط)
هر جا IP/کشور resolve می‌شود (GhajarLocation / کره)،snapshot بگیر:
```kotlin
val gen = VpnState.lookupGeneration.value
// بعد از پایان resolve:
if (VpnState.lookupGeneration.value != gen) return@…  // نتیجهٔ کهنه را دور بریز
```
و در نتیجه، بعد از disconnect خالی‌کردن IP/کشور نمایش‌داده‌شده.

## ۳) wiring چندمنبعی کانال‌ها (آیتم ۶)
- جایی که متن پیام کانال/کانفیگ رایگان import می‌شود: از `GhajarChannelRules.extractProxyLinks(text)` استفاده کن و هر لینک را به `ConfigParser.parseBundle` بده؛ هر تعداد منبع مجاز است
- لیبل نمایشی منبع: فقط `GhajarChannelRules.publicBrand(source)` = `@Ghajarvpn`؛ `internalOriginTag` فقط به لاگ/مدیریت سوءاستفاده برود
- پذیرش فایل `.npvt`: محتوای خوانده‌شده را به `GhajarChannelRules.npvtPayload` بده؛ `null` = قفل‌شده → پیام «نسخهٔ باز یا رمز مالک لازم است» و هیچ تلاشی برای شکستن قفل نشود

## ۴) مراحل بعد از wiring
1. `git checkout main && git pull` (رسیدن به `9709a4d`)
2. تغییرات این session را با کد merge‌شده ترکیب کن (فقط VpnState.kt ممکن است تداخل کند — نسخهٔ این session مبناست)
3. `:app:testDebugUnitTest` سبز شود (۲۶ تست جدید باید پاس شود)
4. snapshot sync + پچ incremental بعدی (0032) با `git format-patch`
5. push → CI → APK

## آیتم‌های منتظر شل (بازنویسی/بازبینی دستی)
- آیتم ۳ (globe): فیکس‌های ساختاری merge شده‌اند (`f5630f4` + `f8423af`)؛ پس از pull فقط رگرسیون بصری لازم است
- آیتم ۴ (فروشگاه): کد merge‌شدهٔ PR #9 را باید روی دستگاه دید؛ فایل ~هزاران خط است و بدون شل fetch کامل ممکن نبود — بعد از pull بازبینی RTL/کارت/قیمت زنده
- آیتم ۷ (R8): طبق تصمیم کاربر حذف شد

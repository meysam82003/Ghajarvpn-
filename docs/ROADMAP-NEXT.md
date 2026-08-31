# GhajarVPN — ادامهٔ کار (وضعیت + باقی‌مانده)

## وضعیت ثبت‌شده
- مبنای دسته = a144684 (PR #8 + پچ ۰۰۳۰ Globe)
- پچ ۰۰۳۰: کرهٔ صفحهٔ اصلی پایدار شد — texture و زوایای رندرشده در یک holder اتمیک، seed شدن فریم قبلی روی تغییر اندازهٔ رستر (بدون حفرهٔ خالی)، یک کلکتور رندر مادام‌العمر (تغییر تم/وضعیت کره را rebuild نمی‌کند)، چرخش وابسته به نمایشگر با withFrameNanos تا رسیدن IP و فرود نرم روی موقعیت IP تأییدشده، ظاهر شدن انیمیت‌شدهٔ نشانگر و کارت پرچم + جمع‌شدن نرم ردیف وضعیت، بازطراحی کارت پرچم (کاشی بزرگ‌تر، لبهٔ گرادیانی، اموجی کشور) و تست JVM اندازهٔ بافر
- دستهٔ c/e/f = پچ ۰۰۳۱ (PR #9، merge `9709a4d`): ویجت Small/Control با لوکیشن و تغییر مستقیم، ping واقعی و نتیجهٔ Update Sub؛ فروشگاه RTL بدون dropdown با کارت expand/collapse و سرویس سفارشی مرحله‌ای/قیمت زنده؛ Welcome در هر ورود با یکی از ۳۳ پوستر آفلاین 9:16
- رگرسیون Globe همراه همین دسته: spin انتظار IP پس از ۴ ثانیه متوقف می‌شود تا Compose و دکمهٔ Disconnect هرگز starve نشوند
- شواهد دسته: bootstrap با `git am --3way`، تطبیق ۶۹ snapshot، Android CI #99 و Android 14 regression #29 سبز
- APK جاری همیشه از لینک ثابت `releases/latest` دریافت شود

## بازنویسی session 2026-08-31 (بدون شل — در انتظار wiring)
- `VpnState.kt` بازنویسی شد: debounce 450ms، قفل اتصال مجدد 700ms پس از disconnect، رد رویدادهای کهنهٔ connect/error، `lookupGeneration` برای cancel کردن lookupها، reset داخلی تست
- `GhajarWelcomeOverlay.kt` جدید: پوستر تمام‌صفحه 9:16 با ContentScale.Fit (هیچ برشی)، کادر مویی 0.5dp، بدون دکمه و بدون پنل آموزشی (تصمیم کاربر)، auto-close 3.2s + لمس برای بستن
- `GhajarChannelRules.kt` جدید: چندمنبعی کانال‌ها، برند عمومی همیشه `@Ghajarvpn`، NPVT متنی باز پذیرفته می‌شود و قفل هرگز شکسته نمی‌شود
- تست‌های جدید: `VpnStateGateTest` (۱۰)، `GhajarWelcomeOverlayRulesTest` (۶)، `GhajarChannelRulesTest` (۱۰)
- راهنمای اتصال: `docs/REWRITE-WIRING-2026-08-31.md`

## بازنویسی session 2026-08-31 (پچ ۰۰۳۲)
- `VpnState.kt`: debounce 450ms + قفل اتصال مجدد 700ms + رد رویدادهای کهنه + `lookupGeneration`
- ولکام: تمام‌صفحه همیشه پُر (backdrop بِرش‌خورده از همان تصویر + پوستر Fit)، کادر مویی 0.5dp، بدون دکمه و پنل، auto-close 3.2s + لمس برای بستن
- `GhajarChannelRules.kt`: چندمنبعی + برند `@Ghajarvpn`؛ `FreeConfigs` از قوانین مشترک scrape می‌کند؛ NPVT باز از مسیر مشترک import می‌شود و قفل NPVT هرگز bypass نمی‌شود
- `ObserveGhajarLocation`: نتیجهٔ کهنه بعد از هر transition دور ریخته و IP/کشور/پرچم پاک می‌شود (`clearStale` در `disconnect`)
- تست‌های JVM جدید: VpnStateGate (۱۰)، WelcomeOverlayRules (۶)، ChannelRules (۱۰)؛ رگرسیون اندروید ۱۴ برای ولکام بدون دکمه به‌روز شد
- snapshot ها ۷۵ فایل تأیید شدند؛ آیتم ۷ (R8) حذف شد

## باقی‌مانده (به ترتیب اجرا)
1. merge پچ ۰۰۳۲ پس از CI سبز + تست runtime اندروید ۱۴
2. بازبینی دستگاهی فروشگاه (آیتم e) و کره
3. ورودی کاربر: فهرست کانال‌های رایگان مجاز برای `FreeConfigs.CHANNELS`

## قوانین
- هر تغییر: هم snapshot مخزن هم پچ incremental (بدون bypass git am / بدون حذف تست)
- پس از هر دسته: merge به main + انتشار APK با workflow release-apk.yml
- runtime test جدا از CI سبزی الزامی

# GhajarVPN — ادامهٔ کار (وضعیت + باقی‌مانده)

## وضعیت ثبت‌شده
- مبنای دسته = a144684 (PR #8 + پچ ۰۰۳۰ Globe)
- پچ ۰۰۳۰: کرهٔ صفحهٔ اصلی پایدار شد — texture و زوایای رندرشده در یک holder اتمیک، seed شدن فریم قبلی روی تغییر اندازهٔ رستر (بدون حفرهٔ خالی)، یک کلکتور رندر مادام‌العمر (تغییر تم/وضعیت کره را rebuild نمی‌کند)، چرخش وابسته به نمایشگر با withFrameNanos تا رسیدن IP و فرود نرم روی موقعیت IP تأییدشده، ظاهر شدن انیمیت‌شدهٔ نشانگر و کارت پرچم + جمع‌شدن نرم ردیف وضعیت، بازطراحی کارت پرچم (کاشی بزرگ‌تر، لبهٔ گرادیانی، اموجی کشور) و تست JVM اندازهٔ بافر
- دستهٔ c/e/f = پچ ۰۰۳۱: ویجت Small/Control با لوکیشن و تغییر مستقیم، ping واقعی و نتیجهٔ Update Sub؛ فروشگاه RTL بدون dropdown با کارت expand/collapse و سرویس سفارشی مرحله‌ای/قیمت زنده؛ Welcome در هر ورود با یکی از ۳۳ پوستر آفلاین 9:16 و پنل آموزشی مستقل
- رگرسیون Globe همراه همین دسته: spin انتظار IP پس از ۴ ثانیه متوقف می‌شود تا Compose و دکمهٔ Disconnect هرگز starve نشوند
- شواهد دسته: bootstrap با `git am --3way`، تطبیق ۶۹ snapshot، Android CI #99 و Android 14 regression #29 (چهار تست از چهار تست) سبز
- APK جاری همیشه از لینک ثابت `releases/latest` دریافت شود

## باقی‌مانده (به ترتیب اجرا — هر مورد: پچ incremental + snapshot sync + verify + build سبز + merge)
1. state machine اتصال: debounce/lock پشت‌سرهم connect-disconnect + cancel lookup
2. npvt + قفل‌شکن + پذیرش ساب + چندمنبعی کانال‌ها با برند @Ghajarvpn
3. بیلد Release با R8 (کاهش حجم از 81MB)

## قوانین
- هر تغییر: هم snapshot مخزن هم پچ incremental (بدون bypass git am / بدون حذف تست)
- پس از هر دسته: merge به main + انتشار APK با workflow release-apk.yml
- runtime test جدا از CI سبزی الزامی

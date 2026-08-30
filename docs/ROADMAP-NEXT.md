# GhajarVPN — ادامهٔ کار (وضعیت + باقی‌مانده)

## وضعیت ثبت‌شده
- main = 02f67b9 (PR #8 merge شده) | دستهٔ d = پچ ۰۰۳۰ «globe: stable renderer/texture lifecycle» ثبت شد
- پچ ۰۰۳۰: کرهٔ صفحهٔ اصلی پایدار شد — texture و زوایای رندرشده در یک holder اتمیک، seed شدن فریم قبلی روی تغییر اندازهٔ رستر (بدون حفرهٔ خالی)، یک کلکتور رندر مادام‌العمر (تغییر تم/وضعیت کره را rebuild نمی‌کند)، چرخش وابسته به نمایشگر با withFrameNanos تا رسیدن IP و فرود نرم روی موقعیت IP تأییدشده، ظاهر شدن انیمیت‌شدهٔ نشانگر و کارت پرچم + جمع‌شدن نرم ردیف وضعیت، بازطراحی کارت پرچم (کاشی بزرگ‌تر، لبهٔ گرادیانی، اموجی کشور) و تست JVM اندازهٔ بافر
- فیکس‌های منتشرشده: پرداخت t.me داخل‌اپ (۰۰۲۳/۰۰۲۸)، welcome autoclose (۰۰۲۷/۰۰۲۹)، فیکس سرور ربات (AgentPricing/HourlyBilling/Bootstrap)، workflow انتشار APK
- APK فعلی: releases/download/v3.0.4/app-arm64-v8a-debug.apk

## باقی‌مانده (به ترتیب اجرا — هر مورد: پچ incremental + snapshot sync + verify + build سبز + merge)
1. state machine اتصال: debounce/lock پشت‌سرهم connect-disconnect + cancel lookup
2. ویجت قاجاری: لوکیشن فعلی + تغییر/انتخاب از ویجت + ping واقعی + آپدیت ساب با نتیجه + connect/disconnect + ۴ وضعیت + UI حرفه‌ای
3. فروشگاه: متن خراب «سرویس‌ه»، RTL/alignment/typography، کارت جمع‌وجور + expand توضیحات، سرویس‌ها بدون dropdown از ابتدا可见، سرویس سفارشی مرحله‌ای با قیمت زنده
4. welcome: پنل آموزشی پایین (نکات چرخشی + دکمه ورود)، هر ورود پوستر تصادفی تمام‌صفحه 9:16
5. npvt + قفل‌شکن + پذیرش ساب + چندمنبعی کانال‌ها با برند @Ghajarvpn
6. بیلد Release با R8 (کاهش حجم از 81MB)

## قوانین
- هر تغییر: هم snapshot مخزن هم پچ incremental (بدون bypass git am / بدون حذف تست)
- پس از هر دسته: merge به main + انتشار APK با workflow release-apk.yml
- runtime test جدا از CI سبزی الزامی

# GhajarVPN — ادامهٔ کار (وضعیت + باقی‌مانده)

## وضعیت ثبت‌شده
- main = e9899c9 (پچ ۰۰۲۴ قدیمی revert شد) | PR #8 = codex/android14-bugfix-suite | CI: build سبز، regression در جریان
- فیکس‌های منتشرشده: پرداخت t.me داخل‌اپ (۰۰۲۳/۰۰۲۸)، welcome autoclose (۰۰۲۷)، فیکس سرور ربات (AgentPricing/HourlyBilling/Bootstrap)، workflow انتشار APK
- APK فعلی: releases/download/v3.0.4/app-arm64-v8a-debug.apk

## باقی‌مانده (به ترتیب اجرا — هر مورد: پچ incremental + snapshot sync + verify + build سبز + merge)
1. state machine اتصال: debounce/lock پشت‌سرهم connect-disconnect (طرح قبلی: vpnBusy در MainActivity خط ~967/1162) + پاک‌سازی IP/کشور بعد از disconnect + cancel lookup
2. ویجت قاجاری: لوکیشن فعلی + تغییر/انتخاب از ویجت + ping واقعی + آپدیت ساب با نتیجه + connect/disconnect + ۴ وضعیت + UI حرفه‌ای
3. globe: renderer/texture پایدار، بدون rebuild کل کره روی تغییر وضعیت، بدون فلیکر
4. فروشگاه: متن خراب «سرویس‌ه»، RTL/alignment/typography، کارت جمع‌وجور + expand توضیحات، سرویس‌ها بدون dropdown از ابتدا可见، سرویس سفارشی مرحله‌ای با قیمت زنده
5. welcome: پنل آموزشی پایین (نکات چرخشی + دکمه ورود)، هر ورود پوستر تصادفی تمام‌صفحه 9:16
6. npvt + قفل‌شکن + پذیرش ساب + چندمنبعی کانال‌ها با برند @Ghajarvpn
7. بیلد Release با R8 (کاهش حجم از 81MB)

## قوانین
- هر تغییر: هم snapshot مخزن هم پچ incremental (بدون bypass git am / بدون حذف تست)
- پس از هر دسته: merge به main + انتشار APK با workflow release-apk.yml
- runtime test جدا از CI سبزی الزامی

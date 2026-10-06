# قاجار وی پی ان — فایل‌های نصبی (ویندوز، مک، لینوکس، آیفون)

همان اپ وب قاجار (`web/`) به‌صورت برنامهٔ نصبی. کاربر فایل را دانلود و باز می‌کند؛ لینکی لازم نیست.

| خروجی | دستگاه |
|---|---|
| `GhajarVPN-win-x64.exe` | ویندوز ۱۰ و ۱۱ |
| `GhajarVPN-mac-arm64.dmg` / `GhajarVPN-mac-x64.dmg` | مک Apple silicon / اینتل |
| `GhajarVPN-linux-x86_64.AppImage` | لینوکس |
| `GhajarVPN-iPhone.mobileconfig` | آیفون و آیپد (آیکن تمام‌صفحه روی صفحهٔ اصلی) |

- ساخت و انتشار: `.github/workflows/ghajar-app.yml` روی هر تغییر این پوشه، همه را می‌سازد و در Release `ghajar-app-v<version>` می‌گذارد (بدون اینکه Release اندروید از «latest» بیفتد). برای نسخهٔ تازه، `version` را در `package.json` بالا ببر.
- آدرس اپ در `main.js` و `ios/make-profile.mjs` است (`APP_URL`)؛ داخل برنامه نوار آدرس دیده نمی‌شود.
- نسخهٔ کامپیوتر در سینی سیستم می‌ماند و با روشن شدن سیستم اجرا می‌شود تا اعلان‌های فروشگاه، هشدار حجم و زمان، کد تخفیف و پیام‌ها به‌صورت اعلان سیستم برسند. لینک‌های بیرونی (تلگرام، درگاه پرداخت، افزودن به اپ‌های VPN) در مرورگر یا برنامهٔ مربوط باز می‌شوند.
- امضا: برنامه‌ها گواهی توسعه‌دهنده ندارند؛ ویندوز (SmartScreen) و مک (Gatekeeper) بار اول هشدار می‌دهند. روش باز کردن در یادداشت Release آمده است.

آزمایش محلی:

```bash
npm ci
npm run dist:linux
ELECTRON_BIN=$PWD/dist/linux-unpacked/ghajar-app xvfb-run -a node test/smoke.mjs   # با mock سرور web/tests
```

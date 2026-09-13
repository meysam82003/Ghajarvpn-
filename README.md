::: {align="center"} <img src="assets/logo.png" width="500">{=html}
🚀 Ghajar VPN
سیستم حرفه‌ای مدیریت، فروش و کنترل سرویس‌های VPN
� � � :::
📌 معرفی
Ghajar VPN یک سیستم کامل مدیریت و فروش سرویس VPN است که برای مدیریت کاربران، اشتراک‌ها، کانفیگ‌ها و ارتباط با مشتریان طراحی شده است.
این پروژه شامل پنل مدیریتی، سیستم فروش، ربات تلگرام و ابزارهای مدیریت سرویس‌ها می‌باشد.
✨ امکانات
بخش                   توضیحات
🚀 مدیریت کانفیگ      مدیریت، بررسی و بروزرسانی کانفیگ‌ها ⚡ تست هوشمند         بررسی وضعیت اتصال و کیفیت کانفیگ‌ها 👤 مدیریت کاربران     مدیریت کاربران، اشتراک‌ها و دسترسی‌ها 💳 فروش سرویس         فروش، تمدید و مدیریت اشتراک VPN 🤖 ربات تلگرام        مدیریت سرویس‌ها و ارتباط با کاربران 💰 پرداخت             سیستم پرداخت و ثبت سفارش 🎫 پشتیبانی           سیستم تیکت و مدیریت درخواست‌ها 📊 گزارش‌ها            مشاهده وضعیت سرویس‌ها 🔄 بروزرسانی خودکار   هماهنگ‌سازی اطلاعات سرویس‌ها
🛠 تکنولوژی‌ها
PHP 8.x
MySQL
Telegram Bot API
Web Application
⚙ نصب
git clone https://github.com/meysam82003/Ghajarvpn-.git
تنظیمات دیتابیس و فایل‌های پیکربندی را انجام دهید.
🛣 Roadmap
نسخه 1.0.0
✅ مدیریت کاربران
✅ فروش سرویس
✅ مدیریت کانفیگ
✅ ربات تلگرام
✅ پرداخت
✅ پشتیبانی
📦 Release
نسخه فعلی:
Ghajar VPN v1.0.0
وضعیت: 🟢 Active Development
📄 License
GPL-3.0 License- Native dynamic store backed by the existing Ghajarvpn Mini App panel
- Embedded HTTPS checkout without exposing a browser address bar
- Automatic import of delivered subscriptions/configurations
- Full, uncropped Ghajar royal welcome posters with a native animated transition
- General, personal, floating, quota and expiry alerts in-app and in Android notifications
- Connection notification with Ghajar avatar, ping and disconnect actions

## Source layout

- `app/` — Ghajarvpn Android application and native store
- `openvpn/` — upstream `ics-openvpn` core integrated as a library
- `strongswan/` — IKEv2 engine
- `docs/` — brand assets, architecture and delivery roadmap

## Build

Requirements: JDK 17, Android SDK 36.1, NDK 28.2.13676358, CMake 3.22.1 and SWIG.

```bash
bash scripts/bootstrap-from-upstream.sh
bash .ghajarvpn-src/scripts/fetch-openvpn-native.sh .
cd .ghajarvpn-src
./gradlew --no-daemon -Pghajar.demo=true :app:testDebugUnitTest :app:assembleDebug
```

Release signing is read from CI secrets or a local untracked `keystore.properties`. Never commit the signing key.

Automatic CI APKs use ephemeral test keys and cannot be assumed to update earlier
installations. The optional main-only signed demo workflow uses a separate private
demo key from GitHub Secrets. See [3.0.2 build and login notes](docs/BUILD-3.0.2.md).

The [3.0.3 login follow-up](docs/BUILD-3.0.3.md) adds clear network/gate feedback,
original-expiry countdowns and lifecycle-aware retry without accepting null tokens.

When the repository is distributed as a compact patch series, run
`./scripts/bootstrap-from-upstream.sh` first. See
[`docs/GITHUB_BOOTSTRAP_FA.md`](docs/GITHUB_BOOTSTRAP_FA.md).

## Branding and backend safety

All public labels are sanitized through `BrandConfig`; legacy engine identifiers remain internal only where protocol compatibility requires them. Checkout allows HTTPS in the embedded view, blocks insecure HTTP/file navigation, cancels SSL errors and never falls back to an external browser.

## License

This derivative keeps the upstream GPL licensing. OpenVPN for Android is included under its GPLv2 terms and additional conditions; see `openvpn/doc/LICENSE.txt`.

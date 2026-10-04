<div align="center">

<img src="assets/banner.png" width="100%" alt="Ghajar VPN banner">

<img src="assets/logo.png" width="200" alt="Ghajar VPN logo">

# 👑 قاجار VPN

**کلاینت VPN چندهسته‌ای اندروید، همراه فروشگاه، ربات تلگرام و مینی‌اپ**

![Version](https://img.shields.io/badge/version-1.1.1-gold)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![License](https://img.shields.io/badge/license-GPL--3.0-blue)

[⬇️ دانلود](https://github.com/meysam82003/Ghajarvpn-/releases/latest) · [📝 یادداشت نسخه](docs/release-notes/v1.1.1.md) · [📣 کانال](https://t.me/Ghajarvpn) · [🤖 ربات](https://t.me/Ghajar_vpnbot)

</div>

---

## ✨ ویژگی‌ها

| | ویژگی | توضیح |
|:-:|---|---|
| 🧱 | چند هسته | Xray، sing-box، Psiphon، Tor، Aether، IKEv2، OpenVPN، OpenConnect، WireGuard و AmneziaWG |
| 📥 | ورود کانفیگ | لینک، اشتراک، QR، فایل، JSON، Clash، `.bpf`، `.ovpn`، NPVT/NPVS و GSB2 |
| ⚡ | انتخاب سرور | پینگ و تست واقعی، انتخاب خودکار سریع‌ترین سرور، علاقه‌مندی‌ها و گروه‌ها |
| 📤 | اشتراک‌گذاری | خروجی برای هر پلتفرم با راهنما، پورتال شبکهٔ محلی، اشتراک اتصال گوشی با رمز |
| 🔒 | GSB2 | اشتراک امن با رمز، تاریخ پایان و سقف حجم؛ گیرنده فقط وصل می‌شود |
| 🛍 | فروشگاه | خرید، تمدید، کیف پول، کد تخفیف، فروشگاه‌ها و پشتیبانی داخل اپ |
| 🎨 | شخصی‌سازی | تم‌ها، رنگ‌ها، دکمهٔ اتصال، چیدمان صفحهٔ اصلی و ویزارد شروع |
| 🔔 | اعلان و ویجت | وضعیت زنده، سرور بعدی، اتصال مجدد و ویجت صفحهٔ اصلی |
| ⬆️ | بروزرسانی | دانلود امن داخل اپ با تأیید SHA-256 و امضا |

| نیاز | مقدار |
|---|---|
| اندروید | ۸٫۰ (API 26) به بالا |
| معماری | `arm64-v8a` و `armeabi-v7a` |

## 🛠 ساخت

```bash
./gradlew :app:assembleDebug
```

JDK 17، Android SDK 36 و NDK `28.2.13676358`. خروجی Release برای هر ABI جداگانه ساخته می‌شود. کلید امضا هرگز در مخزن قرار نمی‌گیرد. جزئیات در [workflow](.github/workflows/android.yml).

| پوشه | محتوا |
|---|---|
| `app/` | اپ اندروید و فروشگاه |
| `openvpn/` · `strongswan/` | OpenVPN و IKEv2 |
| `native/` | سورس هسته‌های بومی |
| `backend/` | بک‌اند، پنل، ربات و مینی‌اپ |
| `docs/` | یادداشت نسخه‌ها و گزارش‌ها |

---

<details>
<summary><b>🇬🇧 English</b></summary>

## 👑 Ghajar VPN

A multi-core Android VPN client with a built-in store, Telegram bot and mini app.

| | Feature | Details |
|:-:|---|---|
| 🧱 | Multiple cores | Xray, sing-box, Psiphon, Tor, Aether, IKEv2, OpenVPN, OpenConnect, WireGuard and AmneziaWG |
| 📥 | Import | Links, subscriptions, QR, files, JSON, Clash, `.bpf`, `.ovpn`, NPVT/NPVS and GSB2 |
| ⚡ | Servers | Real ping and tests, auto-select the fastest server, favourites and groups |
| 📤 | Sharing | Per-platform exports with guides, a LAN share portal, password-protected phone sharing |
| 🔒 | GSB2 | Secure share with password, end date and data quota; the receiver can only connect |
| 🛍 | Store | Purchase, renewal, wallet, discount codes, shops and support inside the app |
| 🎨 | Personalization | Themes, colours, connect button, home layout and first-run setup |
| 🔔 | Notification & widget | Live status, next server, reconnect and a home-screen widget |
| ⬆️ | Updates | Secure in-app updates verified by SHA-256 and signature |

**Requirements:** Android 8.0 (API 26)+, `arm64-v8a` or `armeabi-v7a`.

**Build:** `./gradlew :app:assembleDebug` with JDK 17, Android SDK 36 and NDK `28.2.13676358`.

[Download](https://github.com/meysam82003/Ghajarvpn-/releases/latest) · [Release notes](docs/release-notes/v1.1.1.md)

</details>

---

## 📜 مجوز / License

این پروژه مجوزهای بالادستی را حفظ می‌کند. جزئیات در [LICENSE](LICENSE)، [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) و `openvpn/doc/LICENSE.txt`.

<div align="center">

Made with ❤️ for Ghajar VPN

</div>

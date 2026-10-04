# کاندیدهای نسخهٔ دسکتاپ (از ممیزی ZedSecure)

ZedSecure یک ماژول `desktop/` دارد (Compose Desktop، ۵۶ فایل Kotlin در commit `7c96399`) که Xray، sing-box، تونل‌های DNS و SSH را روی Linux، Windows و macOS اجرا می‌کند. قاجار VPN 1.1.1 فقط برای اندروید است. فهرست زیر فقط **ایده** است. کدی کپی نشده (مجوز آن AGPL-3.0 است) و هیچ‌کدام از موارد در این نسخه انجام نشده است.

| ایده | نکتهٔ فنی دیده‌شده در ZedSecure | ملاحظه برای قاجار |
|---|---|---|
| اجرای Xray و sing-box به‌صورت باینری جدا روی دسکتاپ | `DesktopXray`، `BundledEngines`، `LocalPortPicker` | می‌شود همان هسته‌های قاجار را روی دسکتاپ بیلد کرد (Go cross-compile) |
| حالت TUN با دسترسی ادمین | `TunMode`، `PolkitAgent` (لینوکس)، `AdminPassword` | امن‌ترین مسیر روی لینوکس polkit است؛ روی ویندوز wintun لازم است |
| منوی Tray و نمایش مصرف | `TrayMenu`، `DesktopMeter`، `DesktopStats` | برای تجربهٔ پس‌زمینه ضروری است |
| OpenConnect و IKEv2 روی دسکتاپ | `DesktopOpenConnect`، `DesktopIkev2` | به ابزارهای سیستم (openconnect، strongSwan) وابسته است |
| بسته‌بندی deb، rpm، AppImage، msi و dmg | تسک‌های Gradle با نام `package*` | برای macOS امضا و notarize لازم است |
| Tor و Psiphon روی دسکتاپ | `DesktopTorPsiphon` | Psiphon روی دسکتاپ کتابخانهٔ جدا می‌خواهد |

**پیش‌نیاز تصمیم:** انتخاب فریم‌ورک (Compose Desktop یا Flutter یا وب)، مدل همگام‌سازی حساب فروشگاه، و یک مسیر امضا و انتشار جدا برای هر سیستم‌عامل.

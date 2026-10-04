# ممیزی ZedSecure برای قاجار VPN 1.1.1

- **مخزن:** https://github.com/CluvexStudio/ZedSecure
- **commit ممیزی‌شده (pinned):** `7c9639933abe7033c9f44a4a52f13ec1e74f8b55` (2026-10-03)
- **مجوز:** AGPL-3.0. قاجار VPN با GPL-3.0 منتشر می‌شود. کپی کد AGPL در برنامه فقط با پذیرش شرط‌های AGPL ممکن است، از جمله ارائهٔ سورس به کاربرِ شبکه‌ای.
- **نتیجه:** در 1.1.1 **هیچ خطی از کد ZedSecure کپی یا اقتباس نشده است.** فقط ایده‌ها و رفتارها با پیاده‌سازی خود قاجار مقایسه شدند (فهرست فایل‌ها در `ZEDSECURE_CHANGED_FILES.txt`).
- **روش:** خواندن ساختار ماژول‌های `app/`، `shared/` و `desktop/` (۳۹۲ فایل Kotlin)، نام تست‌ها، `tools/core-sources.txt` و README. این ممیزی ایستا است؛ ZedSecure اجرا یا build نشد.

## جدول تصمیم

| قابلیت در ZedSecure | وضعیت در قاجار 1.1.1 | تصمیم |
|---|---|---|
| Xray (VLESS/Reality/XHTTP، VMess، Trojan، SS، Hysteria2، WG) | دارد (Xray 26.3.27) | بدون تغییر |
| sing-box (TUIC، Naive، AnyTLS، ShadowTLS، JSON) | دارد؛ در 1.1.1 ایمپورت کامل `.bpf` هم اضافه شد | بدون تغییر |
| Psiphon | دارد؛ در 1.1.1 انتخاب پروتکل واقعی از روی AAR اضافه شد | بدون تغییر |
| Tor (obfs4، Snowflake، Conjure) | دارد (obfs4، meek_lite، webtunnel، snowflake)؛ Conjure ندارد | Conjure در بیلد ما تست نشده، اضافه **نمی‌شود** |
| DNS tunnels (DNSTT، VayDNS، MasterDNS) | **در 1.1.1 عمداً حذف شد** | **رد**: طبق تصمیم محصول نسخهٔ سبک |
| OpenConnect، IKEv2، WireGuard، AmneziaWG | دارد | بدون تغییر |
| SSH مستقل و زنجیره‌ای | دارد (حالت‌های payload/TLS/WS) | بدون تغییر |
| Vault با فایل `.zsx` | — | **رد**: فرمت ZSX وارد قاجار نمی‌شود (GSB2 جداگانه و فعلاً NOT_READY) |
| Auto-select و failover | دارد (AutoSelector) | بدون تغییر |
| زنجیره در دو جهت (Xray روی Tor و برعکس) | Tor-base و chain دارد | بدون تغییر |
| Per-app، ویرایشگر قوانین geoip/geosite | per-app و routing دارد | ویرایشگر قانون: **کاندید بعدی**، در 1.1.1 نه |
| SNI spoofing روی دستگاه روت | ندارد | **رد**: نیازمند روت است و با سیاست بدون روت قاجار نمی‌خواند |
| ایمپورت Amnezia `vpn://` | ندارد | **کاندید بعدی**: فرمت عمومی است، باید از سورس Amnezia (نه ZedSecure) پیاده شود |
| هدر `profile-update-interval` اشتراک | فقط `subscription-userinfo` | **کاندید بعدی** (کوچک) |
| MTU finder | تنظیم MTU دستی دارد | **کاندید بعدی** |
| Speed test | تست پایداری و سرعت دارد | بدون تغییر |
| DNS resolver scanner | در 1.1.1 همراه DNS Lab **حذف شد** | **رد** (قابلیت مستقل DNS) |
| Live log | دارد (لاگ و Logcat) | بدون تغییر |
| Quick tile، ویجت، boot receiver | tile و ویجت دارد؛ boot فقط برای اعلان‌ها | auto-connect هنگام boot: **کاندید بعدی** (نیاز به تصمیم محصول) |
| Cloudflare Worker / Serverless chain | ندارد | **بررسی بیشتر**: ریسک سوءاستفاده و پایداری؛ در 1.1.1 نه |
| نسخهٔ دسکتاپ (Linux، Windows، macOS) | ندارد | سند جدا: `docs/ZEDSECURE_DESKTOP_CANDIDATES_FA.md` |
| Map / Location | قاجار Map و مجوز Location ندارد | **رد** |

## بندهای ممنوع (رعایت شده)

- Map، تونل‌های DNS، ZSX و هسته‌های حذف‌شده دوباره وارد نشدند.
- هیچ dependency یا باینری جدیدی از ZedSecure یا forkهای CluvexStudio به 1.1.1 اضافه نشد.

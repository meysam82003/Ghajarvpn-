# افزودن سرویس به برنامه‌های VPN

جدول در `src/lib/applinks.ts` است؛ افزودن یک برنامه یک خط است. روی اندروید لینک‌ها به `intent://…;package=…` تبدیل می‌شوند تا همان برنامه باز شود و اگر نصب نبود، صفحهٔ دانلودش.

| برنامه | دستگاه | لینک | بررسی |
|---|---|---|---|
| قاجار وی پی ان | اندروید | `happ://add?data=<base64 کانفیگ‌ها>` | از سورس همین اپ (`GhajarCompatibilityImport`) |
| v2rayNG | اندروید | `v2rayng://install-sub?url=&name=` | از سورس v2rayNG |
| Hiddify | همه | `hiddify://install-config?url=&name=` | از سورس hiddify-app |
| sing-box | iOS، اندروید، مک | `sing-box://import-remote-profile?url=#name` | مستندات sing-box |
| Streisand | iOS، مک | `streisand://import/<url>#name` | قالب منتشرشدهٔ برنامه |
| V2Box | iOS، مک | `v2box://install-sub?url=&name=` | قالب منتشرشدهٔ برنامه |
| Happ | همه | `happ://add/<url>` | قالب منتشرشدهٔ برنامه |
| v2RayTun | iOS، اندروید | `v2raytun://import/<url>` | قالب منتشرشدهٔ برنامه |
| FoXray | iOS، مک | `foxray://yiguo.dev/sub/add/?url=#name` | قالب منتشرشدهٔ برنامه |
| Shadowrocket | iOS، مک | `sub://<base64url(url)>#name` | قالب منتشرشدهٔ برنامه |
| Karing | همه | `karing://install-config?url=&name=` | قالب منتشرشدهٔ برنامه |
| NekoBox | اندروید | `sn://subscription?url=&name=` | قالب منتشرشدهٔ برنامه |
| Clash Verge | دسکتاپ | `clash://install-config?url=&name=` | قالب منتشرشدهٔ برنامه |
| قاجار دسکتاپ | دسکتاپ | `ghajarvpn://import?url=&name=` | برای بخش B |

برای برنامه‌ای که لینک ندارد یا نصب نیست، همان صفحه: QR، کپی لینک اشتراک، کپی کانفیگ‌ها، اشتراک‌گذاری، و دانلود فایل `.conf` / `.ovpn` برای WireGuard و OpenVPN.

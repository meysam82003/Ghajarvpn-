# تطبیق ZedSecure و قاجار — گزارش زندهٔ توسعه

مرجع: `CluvexStudio/ZedSecure@2c01da26bed98b883503850df1b7f793d28f7f54`، ۲۰۲۶-۱۰-۰۲.
شاخهٔ موجود قاجار حفظ شد؛ commit گزارش اولیه `f293b163` با کار محلی ادغام شد، بدون reset.
گزارش اولیه `ZEDSECURE_OSS_AUDIT_2026-10-02.md` کامل خوانده شد. این گزارش هنوز ادعای اتمام همهٔ درخواست‌ها ندارد.

## مجوز و روش

تمام سورس اپ ZedSecure صرفاً **Reference only — AGPL-3.0** است. هیچ کد اپ آن کپی نشده است.
تغییرات قاجار **Reimplemented independently — GPL-3.0** هستند؛ قراردادهای موتور با upstream پین‌شده تطبیق داده می‌شوند (**Public upstream used**).
موجودی مسیر/اندازه/SHA-256 در `zedsecure-1.1.1/REFERENCE_INVENTORY.json` ثبت شده؛ این فهرست به‌تنهایی اثبات بازبینی خط‌به‌خط یا اجرای تست نیست.
مجوزهای موجود قاجار از جمله موضوع مستقل ics-openvpn با این ممیزی حل‌شده تلقی نمی‌شوند.

## یافته‌های سورس، نه README

- OpenConnect: مدل، فرم، Controller، Auth/SSO dialog و workflow بررسی شدند. fork پین‌شدهٔ native `be071398541d9b6bf5c29a50b41c489f2905db7c` به CORE_TOKEN وابسته است. وجود `setTokenMode(HOTP)` اثبات شمارندهٔ ماندگار نیست. گواهی‌های موقت نام ثابت دارند و cleanup همهٔ خروج‌های اولیه را پوشش نمی‌دهد؛ این الگو اقتباس نشد.
- قاجار: `sing-box@132b38e9` و `sing-openconnect@098ce1337fbe` مرجع‌اند. شش flavor وجود دارد؛ Array Networks موجود در فرم Zed در این قرارداد نیست. callback شمارندهٔ HOTP فقط atomic داخل process را به‌روز می‌کند؛ CLI آن را به میزبان برنمی‌گرداند. فیلد cookie مسیر browser SSO نیست.
- DNS: مدل، generator، Android controller و Desktop CLI بررسی شدند. قرارداد `zeddns` با CLI موتورهای عمومی قاجار یکی نیست. کپی گزینهٔ `perQuery` به VayDNS قاجار کار نمی‌کند. Pool در Zed بعد از probe resolverها را انتخاب می‌کند؛ این متفاوت از اثبات عبور داده از تونل است.
- Vault: `ZsxCrypto`، مدل‌ها، import bus، UI، `ConfigRepository.importLocked` و Intent خوانده شدند. ZSX mode بدون رمز از کلید مشتق از یک ثابت عمومی استفاده می‌کند. Import رمزدار نیز در repository دوباره با mode بدون رمز seal می‌شود. GSB2 نباید این کاهش حفاظت را تکرار کند. `VaultImportHost` بسیاری از خطاها را wrong-password نشان می‌دهد؛ قاجار نباید همین خطا را تکرار کند. پارامترهای Argon در ZSX ذخیره می‌شوند ولی decrypt از ثابت‌های داخلی استفاده می‌کند.
- Chain: ProxyChain/CrossChain، lookup اعضا و UDP mismatch بررسی شدند؛ مدل مناسب مرجع است، ولی قابلیت هر ترکیب باید از موتور واقعی قاجار بیاید.
- Desktop: shared JVM/common، build distributions، runtime، SystemProxy، BundledBinary و TunMode بررسی شدند. `TunMode.supported` فقط Linux را مجاز می‌کند؛ وجود توابع Windows/macOS اثبات پشتیبانی shipped آن‌ها نیست. SystemProxy.clear فقط تنظیم را خاموش می‌کند و تنظیم قبلی را بازنمی‌گرداند؛ قاجار باید snapshot/restore مستقل بسازد. BundledBinary می‌تواند پس از خطای copy فایل ناقص موجود را برگرداند؛ این رفتار اقتباس نمی‌شود.
- Import شبکهٔ Zed پس از شکست درخواست از SOCKS به درخواست مستقیم می‌رود؛ این fallback وارد قاجار نمی‌شود.
- تست‌های Zed فهرست و assertions مرتبط بررسی شده‌اند؛ تست‌های آن اجرا نشده‌اند و شمارشان به نتیجهٔ تست قاجار اضافه نمی‌شود.

## ماتریس شکاف‌ها

| Feature | ZedSecure | Ghajar before | Ghajar after / Implementation | Licence / روش | Tests | Device verified? | Remaining limitation |
|---|---|---|---|---|---|---|---|
| Xray / sing-box | Controller + generator + binaries از build | مسیرهای مستقل موجود، Xray 26.3.27 منتخب | حفظ شد؛ تعویض موتور انجام نشد | Public upstream used | تست‌های مستقل فعلی قاجار | Not device-verified در این مرحله | انتقال واقعی و Android در فاز مجاز |
| OpenConnect شش gateway | native/private fork؛ فرم وسیع‌تر | sing-box endpoint، user/pass، TOTP، cert، MCA | افزودن options عمومی دقیق به قرارداد EngineSettings، فرم، parser/generator/share | Reimplemented independently / GPL-3.0؛ upstream GPL | Phase4SettingsTest؛ شش flavor و رد گزینهٔ نامعتبر/عدم اشتراک secret | Not device-verified | credential/gateway واقعی برای اتصال لازم |
| HOTP / browser SSO | native callback و webview | CLI محدود | Blocked؛ گزینهٔ آمادهٔ جعلی اضافه نشد | Reference only | سورس callback بررسی شد | خیر | خروجی شمارندهٔ ماندگار و کانال تعامل auth از موتور عمومی لازم |
| DNS خانوادهٔ قاجار | DNSTT/VayDNS از zeddns و MasterDNS | DNSTT/VayDNS/NoizDNS/MasterDNS/StormDNS/CottenDNS/Slipstream | همه حفظ شدند | Public upstream used؛ مجوز هر موتور در scripts | قراردادهای sidecar | خیر | fanout مشترک نیازمند adapter واقعی، plain TCP فعلاً Unsupported |
| Chain | مدل explicit + runtimeهای منتخب | chainId/Xray، Aether/Tor و lifecycle | ChainPlan نسخهٔ ۱ با carrier/exit و validation پیش از build؛ مسیر موجود حفظ شد | Reimplemented independently | lifecycle مستقل قبلی این مرحله | خیر | ترکیب‌های فاقد UDP نباید فعال شوند |
| Secure Vault | ZSX + locked profile؛ ضعف mode بدون رمز | GSB1، رمز ذخیره نمی‌شود | crypto/repository/ledger، UI مبتنی بر Entry و runtime محدود Xray/sing-box با سهمیهٔ محلی Xray؛ هنوز Ready کامل نیست | Independent؛ بدون کپی AGPL | ۱۵ آزمون VaultV2Test + Safebox؛ boundary/AEAD/migration/merge | خیر | biometric، UI کامل، سایر adapterها، سهمیه sing-box/server و تست Android باقی است |
| سهمیهٔ سرور | شاهد implementation این قرارداد در سورس بررسی‌شده نیست | قرارداد child entitlement موجود نیست | Blocked تا اتصال adapter معتبر؛ local با server یکی نیست | Independent | برنامهٔ تست مشخص در طراحی | خیر | API ساخت child، کلید امضای واقعی و enforcement پنل لازم |
| Desktop | JVM مشترک، packaging، runtime چند OS | Android-specific | Planned، فاز مستقل؛ استخراج shared باید compatibility را حفظ کند | Reference only | اجرا نشده | خیر | OS مستقل، restore proxy، TUN و packaging |
| فروشگاه و update | جایگزین موردنیاز نیست | دو باگ گزارش‌شدهٔ کاربر | خطای عمومی بدون hostname؛ modal دانلود غیرقابل dismiss؛ release history/اعلان/ویجت در سورس | Independent | ImportAndUpdateRegressionTest در اجرای ۱۳۳ تست app؛ UI اجرا نشده | خیر | تست لمس، notification و widget روی دستگاه |

## نیاز سهمیهٔ واقعی

Backend فعلی بدون قرارداد معتبر child credential/gateway و signed entitlement، مجوز نمایش «Server enforced» ندارد.
فقط خواندن مصرف parent برای چهار سهم مستقل کافی نیست. لازم است adapter پنل ایجاد child، رزرو اتمیک parent، مصرف authoritative، قطع credential در اتمام حجم/زمان، register/revoke دستگاه با کلید عمومی و امضای snapshot را فراهم کند. هیچ URL، کلید ناشر، token یا endpoint ساختگی اضافه نمی‌شود. اجازهٔ کار روی این ویژگی به معنی تغییر بی‌دلیل فروشگاه/ربات/Mini App نیست؛ تغییر احتمالی backend فقط در محدودهٔ قرارداد موردنیاز و با تست مستقل خواهد بود.

## مرز ادعا

Implemented، Import-only، Blocked و Not device-verified جدا ثبت می‌شوند. فعلاً هیچ ادعای «از فازهای ۱ تا ۶ کار توسعه‌ای باقی نمانده» مجاز نیست. Build نهایی، CI، Tag و Release اجرا نشده‌اند.

## شواهد checkpoint ۲۰۲۶-۱۰-۰۳

- `run_host_checks.py`: ۱۳۳ تست app و ۲۲ تست log/browser پاس؛ private fixtureهای BPF/NPVT/دو NPVS واقعاً در این اجرا فعال بودند.
- `check_remaining_sources.py`: ۱۷ تست renewal، typecheck Java API/Mihomo و Kotlin controllerهای واقعی پاس؛ ۷۹ فایل Kotlin از نظر syntax بررسی شدند. این full Android/Compose compile نیست.
- `wireguard_loopback.py`: پنج انتقال/خرابی واقعی روی Xray رسمی 26.3.27 پاس؛ دو peer، TCP v4/v6، DNS، UDP و قطع peer؛ AAR Android تغییری نکرد.
- خروجی‌ها در `remaining-1.1.1/test-results/*2026-10-03.txt` و `wireguard-loopback.json` ثبت‌اند. شمار قبلی جای نتیجهٔ اجرای جدید ننشسته است.

گزینه‌های جدید VayDNS شامل rps، idle timeout، keepalive، resolver timeout و max labels فقط برای موتور دارای این flagها تولید می‌شوند. کلیدهای DNS ناشناخته به UDP تغییر داده نمی‌شوند. OpenConnect از token modeهای عمومی TOTP/stoken/OIDC و گزینه‌های دقیق endpoint استفاده می‌کند؛ OIDC token به معنای browser SSO نیست. HOTP هنوز آماده نیست.

Secretهای JSON دارای فاصله/quote escaped، cookie کوتاه، master/DEK/KEK و diagnosticهای حاوی rawConfig یا ProxyConfig در GhajarLog پوشانده می‌شوند؛ crash/export همان redactor را دارند. متن exception به UI فروشگاه داده نمی‌شود. تضمین پاک‌سازی تمام کپی‌های String در JVM ادعا نشده است.

## ادامهٔ پس از bbd8177c

کد مستقل نشست صندوق به مسیر واقعی سرویس متصل شد؛ [VAULT_RUNTIME_FA](remaining-1.1.1/VAULT_RUNTIME_FA.md) فایل‌ها، رفتار، source counter، محدودیت sing-box و تست‌های جدید را ثبت می‌کند. ۱۴۳ app + ۲۲ log/browser + ۱۷ renewal پاس و پنج تست WireGuard دوباره اجرا و پاس شدند. این اعداد نتیجهٔ اجرای جدیدند؛ با اعداد checkpoint قبلی جمع نمی‌شوند. Runtime Android/Compose کامل و backend سهمیه هنوز تأیید نشده‌اند؛ هیچ کد AGPL جدیدی وارد نشده است.

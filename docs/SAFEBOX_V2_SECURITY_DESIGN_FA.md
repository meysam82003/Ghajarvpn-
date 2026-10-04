# طراحی امنیتی صندوق GSB2 — وضعیت پیاده‌سازی

این سند قرارداد پیاده‌سازی فعلی است، نه گواهی امنیت مستقل یا ادعای تکمیل UI/اتصال همهٔ موتور‌ها.
کد مستقل است؛ ZSX متعلق به ZedSecure با مجوز AGPL فقط Reference only بود و کپی نشده است.

## ادامهٔ runtime — ۲۰۲۶-۱۰-۰۳

[گزارش کامل نشست صندوق](remaining-1.1.1/VAULT_RUNTIME_FA.md) مرجع وضعیت جدید است. UI اکنون VaultEntry را نگه می‌دارد و اتصال Xray/sing-box با grant یک‌بارمصرف حافظه‌ای و Intent بدون payload به سرویس رسیده است. سهمیهٔ محلی Xray، زمان، final flush و blocking پس از failure در سرویس نوشته شده‌اند؛ این اثبات runtime روی Android نیست. UI کامل، biometric، تمام engine adapterها و Server enforced هنوز تکمیل نیستند.

`usageId` فیلد اختیاری افزوده در metadata authenticated است؛ برای فایل‌های قدیمی مقدار پیش‌فرض entry ID است. Keep both/Replace، usageId موجود را حفظ می‌کنند تا ساخت ID ذخیره‌سازی جدید، مصرف را صفر نکند. تغییر backward-compatible است و نیازمند دستکاری envelope version نیست.

آزمون این ادامه: ۱۴۳ app (شامل ۱۰ VaultRuntimeTest جدید)، ۲۲ log/browser و ۱۷ renewal پاس؛ typecheck مستقل VaultConnection/کنترلرها و syntax-check Compose/service. نتایج checkpoint پایین، تاریخی‌اند. زمان FIRST_CONNECT از پاسخ معتبر HTTPS/SOCKS آغاز می‌شود؛ local polling یک‌ثانیه‌ای hard cap سرور نیست و overshoot/آخرین flush هنگام مرگ process محدودیت شناخته‌شده است.

## Threat model

هدف: رمزگذاری در حالت ذخیره، حفاظت فایل گم‌شده/Export، تشخیص تغییر داده و پنهان‌ماندن payload در UI عادی.
مهاجم دارای root، process instrumentation، client تغییریافته یا دسترسی به runtimeِ بازشده خارج از مرز این حفاظت است. انقضا و سهمیهٔ Local، DRM و revocation واقعی نیستند. رمز ضعیف همچنان در معرض حدس آفلاین است.

## File format

GSB2: چهار بایت ASCII، یک بایت نسخهٔ envelope برابر ۱، طول چهار بایتی big-endian header، JSON header، master key رمز‌شدهٔ ۴۸ بایتی، JSON entries و HMAC-SHA256 سی‌ودوبایتی manifest.
Header شامل vaultId، cipher، KDF/version، salt شانزده‌بایتی، memoryKiB، iterations، parallelism و nonce دوازده‌بایتی wrapping است.
حد فایل ۱۶ MiB، header برابر ۴ KiB، body JSON برابر ۸ MiB، nesting برابر ۶۴، تعداد entry برابر ۲۰۴۸ و payload هر entry برابر ۱ MiB است؛ اندازهٔ encoded plaintext همراه metadata/tag حداکثر ۲ MiB. حد رشتهٔ body رمز‌شده برای Base64 برابر ۳ MiB است؛ حد رشتهٔ JSON کانفیگ همچنان ۱ MiB می‌ماند. parser قبل از KDF پارامترها را محدود می‌کند. فرمت ناشناخته به crypto دیگری fallback نمی‌کند.

## Crypto و KDF

کلید اصلی ۲۵۶ بیت تصادفی از SecureRandom؛ KEK با Argon2id v1.3 (شناسهٔ فایل `Argon2id-19`) از رمز ساخته می‌شود.
پیش‌فرض ۶۴ MiB، سه iteration و parallelism=1 است. محدودهٔ خواندن ۳۲–۱۲۸ MiB، iteration=2–6، parallelism=1–4؛ تخصیص هم‌زمان KDF در همان process با synchronized محدود است. این بازه قرار نیست جای benchmark دستگاه ضعیف را بگیرد.
وابستگی مستقیم lightweight API از Bouncy Castle `bcprov-jdk18on:1.83` است؛ MIT-style Bouncy Castle Licence. JAR عمومی Maven دریافت و در JVM اجرا شد؛ اندازهٔ JAR برابر ۸۴۹۲۴۵۸ بایت، SHA-256 برابر `82cf3a2af766c3bc874f6d36b9f20a8b99a8f09762dc776e8a227a45d8daaafb` است. این اندازه، اندازهٔ نهایی APK پس از R8 نیست. هیچ provider سراسری Android تعویض نمی‌شود؛ سازگاری نهایی R8/دستگاه در فاز ساخت باید تأیید شود.

## Master-key wrapping و per-entry

KEK کلید اصلی را با AES-256-GCM و nonce تصادفی wrap می‌کند. تمام prefix/header همان AAD است؛ تغییر KDF یا vaultId بدون شکست authentication پذیرفته نمی‌شود.
کلید هر entry با HKDF-SHA256 طبق RFC 5869 از master، vaultId و info مجزای `GSB2/entry/v1/<id>` ساخته می‌شود. nonce هر entry تازه و در فایل یکتا است.
AAD هر entry شامل فرمت/نسخه، الگوریتم، vaultId، entryId، privacy و metadata عمومی است. HMAC با کلید domain-separated مستقل کل فایل را پوشش می‌دهد تا حذف، جابه‌جایی یا افزودن row هم تشخیص داده شود.

## Metadata

STANDARD: نام، protocol، زمان‌ها، note و policy قابل مشاهده ولی تا unlock تأییدنشده‌اند. PRIVATE: descriptor فقط شناسهٔ تصادفی و privacy دارد؛ metadata همراه payload رمز می‌شود. UI قبل از unlock باید فقط «کانفیگ قفل‌شده» نشان دهد. payload در هیچ‌یک plaintext نیست.

## GSB1 و migration

GSB1 فقط خوانده می‌شود: PBKDF2-SHA256/200000 و AES-GCM مطابق قالب تاریخی. نوشتن جدید از Safebox.seal/save به GSB2 می‌رود.
Migration: decrypt موفق → seal GSB2 → نوشتن فایل موقت تصادفی هم‌دایرکتوری با 0600 → fsync → بازخوانی و مقایسهٔ همهٔ entryها → atomic move. هیچ copy-overwrite fallback وجود ندارد. اگر verify/rename شکست بخورد فایل اصلی حفظ می‌شود. همگام‌سازی directory پس از rename هنوز برای تضمین durability در قطع برق آزموده نشده است.

## Import، merge، export و backup

VaultRepository پارس bounded، حفظ policy، fingerprint از canonical config بدون نام/ID نمایشی، Keep existing/Replace/Keep both و verify-before-source-deletion دارد. نام یکسان ملاک duplicate نیست. Replace روی entry read-only رد می‌شود.
Export selected/entire با رمز مستقل دوباره seal و reopen می‌شود؛ policy منع re-export بررسی می‌شود. Safe backup فقط بایت‌های encrypted را منتقل می‌کند. اتصال این انتخاب‌ها به UI و Backup کلی هنوز باید تکمیل شود.
Facade قدیمی policyهای محدودشده را به ProxyConfig عادی تبدیل نمی‌کند؛ این کار از حذف بی‌صدای محدودیت‌ها جلوگیری می‌کند.

## Error model

Version، bounds/Corrupt، Invalid config، Policy، Expired، Locked و I/O جدا هستند. پس از بازکردن کلید، خرابی manifest/entry خطای Tampered است.
شکست tag در wrapped master از نظر رمزنگاری نمی‌تواند به‌طور قطعی «رمز غلط» را از «دستکاری بخش کلید» جدا کند؛ پیام باید هر دو احتمال را بگوید. checksum بدون کلید این تمایز امنیتی را ایجاد نمی‌کند. هیچ plaintext با tag نامعتبر پذیرفته نمی‌شود.

## Biometric model

طرح: یک نسخهٔ محلی master با Android Keystore غیرقابل استخراج و نیازمند user authentication wrap شود؛ AndroidX Biometric برای prompt سیستم. portable export فقط password wrapping دارد. با invalidation کلید دستگاه باید local wrap حذف و password fallback حفظ شود.
**هنوز پیاده‌سازی/تست دستگاه نشده است**؛ هیچ دکمهٔ Ready یا تنها مسیر unlock به نام biometric منتشر نشده است. master فعلی پس از decrypt wipe می‌شود؛ کانال local wrap باید با چرخهٔ save/rekey و lifecycle یکپارچه شود.

## Secret lifecycle و auto-lock

password ورودی CharArray است و caller مالک پاک‌کردن آن است. KEK/master/کلید entry و buffer plaintext در finally پاک می‌شوند؛ Stringهای JVM/Compose و کپی‌های JCA تضمین zeroization ندارند. session باید فقط process memory باشد و با process death از بین برود. VaultSession قرارداد auto-lock و background/screen lock را دارد؛ صفحهٔ فعلی هنگام ON_STOP/dispose قفل می‌شود. epoch از بازشدن مجدد توسط نتیجهٔ async قدیمی جلوگیری می‌کند؛ CharArrayهای عملیات IO حتی هنگام لغو پاک می‌شوند. انتخاب همهٔ timeoutها و binding کامل UI/biometric هنوز تکمیل نشده است. FLAG_SECURE فقط در صفحهٔ صندوق، با گزینهٔ کاربر و بازگرداندن وضعیت قبلی window اعمال می‌شود.
هیچ کلید ثابت، دستگاه-ID به‌عنوان کلید، CBC/ECB یا fallback بدون authentication وجود ندارد. هیچ shell/hook/path فایل واردشده اجرا نمی‌شود.

## Quota / entitlement

واحد داخلی Long byte؛ TOTAL پیش‌فرض است و arithmetic اشباع می‌شود. انقضا در مرز `now >= expiresAt` و اتمام حجم در `used >= quota` است. CREATED/IMPORTED/FIRST_CONNECT مجزا هستند.
VaultUsageLedger شمارندهٔ محلی و اولین activation را با atomic save حفظ می‌کند؛ read/modify/verify/replace بین instanceهای مختلف در همان process نیز قفل مشترک دارد. مقدار اعشاری، رشتهٔ عددی و overflow در فیلدهای امنیتی/KDF به عدد صحیح truncate نمی‌شوند. این فقط Device-local limit است؛ root/clear-data/reinstall قابل دورزدن‌اند.
QuotaProvider قرارداد child credential، register/remove device، revoke، renew، افزایش سهم و expiry دارد. هنوز adapter تولیدی فعال نیست. Server mode با counter محلی هرگز ACTIVE نمی‌شود.
EntitlementVerifier امضای Ed25519 با public key مورداعتماد میزبان (نه کلید داخل فایل)، domain separation، canonical payload، config/device fingerprint، issuedAt/validUntil و sequence را بررسی می‌کند. registry تولیدی کلید واقعی و snapshot معتبر هنوز لازم است. زمان اعتبار snapshot حداکثر یک ساعت و grace پیش‌فرض صفر است. ذخیرهٔ highest sequence و enforcement session باید در adapter/runtime کامل شود.
ParentAllowance قرارداد hard reservation با reserved و consumed غیرهمپوشان دارد؛ پنلی که consumed را داخل reserved حساب می‌کند باید در adapter تبدیل صحیح انجام دهد. pure calculator جای transaction سرور نیست.

## وضعیت تست و محدودیت صادقانه

اجرای مستقل checkpoint ۲۰۲۶-۱۰-۰۳: ۱۳۳ تست app (شامل ۱۵ تست VaultV2Test و تست Safebox قدیمی) و ۲۲ تست log/browser پاس شدند. ۱۷ تست renewal و typecheckهای مستقل قرارداد/کنترلر نیز پاس‌اند؛ سورس Compose فقط syntax-check شده است. خروجی دقیق در remaining-1.1.1/test-results ذخیره شده است.
آزمون‌های پایه شامل GSB1، wrong-password، tamper، migration شکست‌خورده، atomic replacement، metadata privacy، nonce/salt، AAD/key isolation، quota/time boundary/overflow، ledger restart، parent allocation و signature/replay/device binding بود.
این آزمون‌های checkpoint اثبات اتصال locked Xray/sing-box/OpenConnect/DNS، قطع session واقعی Android، پنل، Device registration یا biometric نیستند. ادامهٔ runtime در بخش بالای سند ثبت شده است؛ باقی‌ماندهٔ توسعه با آزمون دستگاه فاز ۷ یکی نیست.

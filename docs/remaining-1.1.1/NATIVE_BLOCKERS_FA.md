# پیش‌نیازهای دقیق موتور و سرویس — بازبینی ۲۰۲۶-۱۰-۰۱

این موارد با «فقط تست دستگاه» یکی نیستند. سورس فعلی، قرارداد سرور و artifact مشترک باید با هم تطبیق داده شوند؛ هیچ موتور سالمی جایگزین نشده است.

## xDNS

مراجع بررسی‌شده:

| درخت | شناسه دقیق | تفاوت قابل مشاهده |
|---|---|---|
| GFW-knocker/Xray-core | bdc6f0e7767882daecb4e3c0eb3862fb4e2918e2 | چند دامنه/resolver، qtype و قطعه‌های پاسخ DNS؛ محدودیت بسته/قرارداد صف و cancellation محل ایراد |
| XTLS/Xray-core v26.3.27 | d2758a023cd7f4174a5a5fa4ff66e487d4342ba0 | schema تک `domain`؛ xDNS قدیمی، شرط رد payload با طول >=224؛ مسیرهای `WriteTo` با `(0,nil)` |
| XTLS/Xray-core v26.7.28 | 5ca6f4b7d4dc20a881d4330e498892697627ec0c | `domains/resolvers` به شکل رشته؛ تغییرات record transport |
| XTLS/Xray-core v26.9.30 | b26a91de4f3294e26a0ad0a970b81a386a41f789 | domain ساختاری با len_limit/label_limit/types/edns0، resolver نوع‌دار TCP/UDP و extra_poll؛ بازنویسی framing و fragment ID |

هش فایل‌های خوانده‌شده در `TRANSPORT_SOURCE_HASHES.json` است؛ نام مشترک xDNS، سازگاری این چهار wire format را ثابت نمی‌کند. نسخهٔ module داخل AAR فعلی `v1.260327.0` است؛ این metadata به‌تنهایی اثبات درخت دقیق سازنده نیست.

**اکنون:** واردکننده/validator/generator/capability به‌دروغ xDNS فعال اعلام نمی‌کنند. DoH و dnstt جایگزین یا هم‌نام آن نشده‌اند. تست client/server xDNS اجرا نشده؛ parser یا `run -test` شاهد اتصال نیست.

**پیش‌نیاز ادغام:** مالک سرویس باید implementation و commit سرور، رکوردهای دامنه/delegation، qtypeهای مجاز، سقف QNAME/EDNS و پروتکل احرازشدهٔ داخل mask را مشخص کند و یک endpoint آزمایشی مجاز بدهد. در یک شاخهٔ سازگاری جدا باید:

1. framing دو سمت یکسان و versioned شود: سربار label/base encoding/شناسه/fragment و حد datagram از روی همان قرارداد محاسبه شود؛ صرف تغییر 224 به عدد بزرگ‌تر کافی نیست.
2. خطای بستهٔ بزرگ، صف پر و closed صریح باشد؛ ارسال‌نشده `(0,nil)` یا `len(payload),nil` برنگرداند.
3. وضعیت اتصال/شناسه‌ها synchronization داشته باشند؛ Close یک‌بار همهٔ reader/writerهای منتظر را بیدار کند؛ deadline قابل تغییر و cancellation واقعی باشد.
4. صف تعداد و بایت محدود، سقف reassembly و عمر fragment داشته باشد؛ IPv4/IPv6، truncation، query تکراری/خراب، timeout و MTU دو سمت با roundtrip باینری آزموده شوند.
5. تغییر مشترک Xray/Psiphon با provenance و TUN/Android سازگار تأیید شود، سپس import + settings + validation + generator + capability هم‌زمان فعال شوند.

این کار هنوز پیاده‌سازی و qualification موتور/سرور می‌خواهد؛ «فقط منتظر Build» نیست. نسخهٔ 26.9.30 صرف جدیدتر بودن انتخاب نشده است.

## OpenConnect

سورس دقیق sing-box: `132b38e9caaba1a1959354d518e54d2d08419afe` (پین موجود برنامه). در `option/openconnect.go` وجود hotp/oidc/counter دیده شد. `protocol/openconnect/status.go` متدهای وضعیت/challenge، browser URL، final URL، cookie/header و callback-prefix را دارد. APIهای `CompleteAuthChallenge` و `CancelAuthChallenge` در libbox/daemon در دسترس سورس‌اند؛ برنامه اکنون executable را با `run` اجرا می‌کند و به این APIها کانال IPC احراز‌شده ندارد. وجود cookie ذخیره‌شده SSO نیست.

HOTP در `protocol/openconnect/client.go`، callback `UpdateCounter` فقط شمارندهٔ atomic داخل process را به‌روز می‌کند. این شمارنده از status به میزبان صادر و روی دیسک commit نمی‌شود. افزودن فیلد counter در UI باعث مصرف مجدد OTP بعد از restart می‌شد و انجام نشده است.

**تغییر لازم:** یک wrapper پین‌شده با کانال private authenticated IPC و شناسهٔ generation/challenge، عملیات status/complete/cancel و timeout؛ callback HOTP باید counter جدید را *پیش از* اجازهٔ مصرف/ارسال بعدی در storage خصوصی اتمیک و durable ثبت کند، خطای ثبت مصرف را متوقف کند و قرارداد crash/retry با موتور روشن باشد. افزودن callback به libbox بدون اتصال executable محصول کافی نیست. نصب یک AAR gomobile دوم کنار AAR فعلی نیز راه‌حل مجاز نیست.

**ورودی سرویس‌دهنده برای SSO:** flavor/IdP واقعی، redirect URI ثبت‌شده، scheme/host/path مورد قبول، شرایط cookie/header و expiry، حساب آزمایش مجاز. callback باید با challenge زنده و state تصادفی bind شود؛ ورودی دلخواه browser یا لینک اپ دیگر credential محسوب نشود. لغو مرورگر/برگشت به برنامه/expiry باید challenge را باطل و engine wait را آزاد کند. token/cookie/password/key نباید log یا export عمومی شوند. user/password، TOTP، CA/certificate و MCA موجود تغییر نکرده‌اند؛ تست‌های تنظیمات دوباره اجرا شده‌اند، ورود واقعی سرور آزموده نشده است.

**وضعیت:** SSO و HOTP ماندگار هنوز پیاده‌سازی ناقص‌اند؛ هم توسعهٔ رابط اجرایی لازم دارند و هم qualification با سرویس‌دهنده. این گزارش آن‌ها را تکمیل‌شده یا صرفاً منتظر دستگاه نمی‌نامد.

## Aether H2 روی Tor

پین Aether: `21e7150ac2225caa01cbb96ac572b5c0cc1e1dc2`؛ هفت فایل بحرانی/lock/license با نسخهٔ upstream byte-identical بودند (`UPSTREAM_RECHECK.json`). مسیر `apifront::candidates` پیش از ارسال API، `tokio::net::lookup_host((host,443))` را صدا می‌زند؛ داشتن `--upstream` در مسیر dial این lookup را به Tor منتقل نمی‌کند.

پیش‌نیاز فعال‌سازی جهت `app → Aether H2 → Tor → Aether endpoint`: اصلاح resolver API-front برای upstream با remote-DNS یا حذف امن lookup خارج از upstream، بررسی تمام مسیرهای bootstrap/account refresh و fallback، محدودکردن transport به H2، آزمون با DNS سیستم ممنوع و شمارش ترافیک هر hop، سپس artifact Android سازگار. فرایند ثبت حساب و گواهی/hostname verification نباید دور زده شود. این جهت در validator بسته و در گزینه‌های قابل انتخاب UI عرضه نشده؛ توسعهٔ patch موتور و qualification هنوز باقی است. جهت معکوسِ Tor روی Aether اکنون orchestration مستقل دارد.

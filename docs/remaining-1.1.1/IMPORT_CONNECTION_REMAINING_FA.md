# Import و اتصال — بازبینی ۲۰۲۶-۱۰-۰۳

این گزارش بین «decode»، «ذخیرهٔ بدون حذف semantics»، «قابل راه‌اندازی در موتور» و «انتقال واقعی» تفاوت می‌گذارد. هیچ APK یا AAR جدید ساخته/جایگزین نشده است.

## BPF

علت قبلی: pipeline ورودی متن/URI/JSON بود؛ داشتن sing-box executable به معنی داشتن reader قالب باینری libbox نبود. `BpfDecoder.kt` اکنون header نوع ۳ و version صفر/یک، gzip با CRC/ISIZE و حد بازشدن، رشتهٔ UTF-8 با طول uvarint، نوع پروفایل و metadata مربوط به هر نسخه را می‌خواند. offset مخصوص نمونه وجود ندارد. مرجع قرارداد `sing-box@132b38e9`، `experimental/libbox/profile_import.go` است. پیاده‌سازی reader مستقل است.

`BoundedJson` قواعد JSON را پس از پردازش JSONC بررسی می‌کند؛ delimiter/value گمشده، کلید تکراری حتی با Unicode escape، عمق زیاد، رشته/فایل بزرگ و tokenهای JavaScript پذیرفته نمی‌شوند. متن اصلی config بدون تغییر در `FullSingBoxProfile` ذخیره می‌شود. BPF از DecoderRegistry، ForeignImport و مسیرهای SAF/Intent وارد همین مدل می‌شود؛ MIME octet-stream مانع تشخیص header نیست.

Full Config مسیر مجزای sing-box دارد. DNS، outbounds، groups، route و unknown fields به node تبدیل نمی‌شوند. نمونهٔ خصوصی کاربر decode می‌شود و چهار outbound و DNS/route آن باقی می‌مانند. نام UTF-8 نیز بررسی شد؛ نام/سرور/credential آن در گزارش درج نشده است.

**محدودیت توسعه‌ای:** Full Config دارای TUN فعلاً فقط قابل ذخیره است. CLI فعلی میزبان Android PlatformInterface/تحویل TUN fd sing-box را ندارد؛ حذف inbound TUN و تبدیل بی‌صدای semantics مجاز نیست. مسیر قابل اجرای فعلی یک SOCKS/mixed محلی یا config بدون inbound است؛ فایل خارجی، controller، cache و inboundهای ناسازگار قبل از اجرا رد می‌شوند. validation نهایی schema توسط همان core در runner انجام می‌شود؛ JSON معتبر معادل اتصال نیست.

Remote BPF نوع ۲ و metadata URL/auto-update/interval/last-update و نوع iCloud مطابق نسخه خوانده و نگه‌داری می‌شوند. **scheduler به‌روزرسانی Remote BPF هنوز پیاده نیست.** گزینهٔ جداگانهٔ UI برای استخراج سرورها نیز باقی مانده است.

Associationهای VIEW برای پسوندهای مشخص bpf/npvt/npvs اضافه شدند و VIEW عمومی تمام octet-streamها حذف شد. SEND با URI stream و permission موقت خوانده می‌شود؛ copy دائمی خودکار لازم نیست. در content URI فاقد پسوند/نام قابل‌تطبیق، محدودیت resolver اندروید باقی است و Share/File picker مسیر قابل اتکا است؛ تست File Manager/Telegram واقعی فاز۷ است.

## NPVT / NPVS

الگوریتم/دادهٔ مرجع: Pantegnos **MIT v9.4.3، commit 09ae0699f7d18717f1900643b703021344f6a9fe**. HEAD جدید AGPL است و مجوز آن به گذشته تعمیم داده نشده است. notice و hash جدول‌ها در `docs/licenses/PANTEGNOS_NPV_MIT.txt` و `NPV_SOURCE_PIN.json` ثبت‌اند. الگوریتم Kotlin مستقل است؛ جدول‌های عمومی format با مجوز MIT استفاده شده‌اند. کد نمایش secret/recovered-key ابزار مرجع اقتباس نشده است.

| قالب | رفتار کنونی | محدودیت |
|---|---|---|
| NPVT1 legacy | سه chunk دقیق، Base64 استاندارد/URL-safe، white-box CTR با counter big-endian، JSON bounded، raw profile/policy حفظ | sample واقعی full Xray و policy قفل دارد؛ اجرای بدون حذف policy هنوز تکمیل نیست |
| NPVTSUB1 | تشخیص مستقل، خطای generation دقیق | قرارداد/fixture معتبر این generation هنوز لازم است؛ NPVT1 فرض نمی‌شود |
| NPVO1 | JSON کامل و wrapper در NpvDecodedContainer | امضای ناشر ندارد؛ صرف نام open اثبات اصالت نیست |
| NPVS v1 v-envelope passphrase | PBKDF2-SHA256 محدود، wrap و body با AEAD، metadata/policy/raw کامل | recipient key در این مسیر تأمین نشده است |
| NPVS v1 app-key | KDF دقیق wbaes-ctr-sha256/keyId=1، دو variant منتشرشدهٔ جدول، فقط DEK دارای tag معتبر پذیرفته می‌شود | generation ناشناخته رد؛ هیچ key guessing وجود ندارد |
| NPVS v5 compact-v1 | methodهای recipient/passphrase/app-key جدا؛ app-key عمومی Gen2، metadata و هر record با tag اجباری | signature ناشر و trailer سی‌ودوبایتی هنوز تأیید نشده‌اند؛ UI صریحاً اصالت را تأییدنشده می‌نامد |

برای v5 ترتیب recordها، sequence، flags، طول ciphertext، content ID، دادهٔ record ناشناخته و trailer حفظ می‌شوند. flags در AAD هر record قرارداد مرجع نیست؛ اصالت آن بدون signature ادعا نمی‌شود. tamper در metadata/body/tag رد می‌شود. تغییر signature به‌دلیل نبود verifier معتبر، «تأییدشده» نمی‌شود؛ این بخش هنوز کار توسعه‌ای دارد.

IPv6 در URIهای VLESS/Trojan/Shadowsocks استخراجی bracket می‌شود. `wsSettings.host` در خواندن Xray JSON حفظ می‌شود. Full JSON در v2rayJson/persistJson اولویت دارد؛ در قالب‌های legacy/open هم به single-node تقلیل داده نمی‌شود. `xray-full` و نوع ناشناختهٔ حفظ‌شده تا ایجاد قرارداد runtime، connectable نیستند. روش قدیمی readable-wrapper، تمام transportهای اختصاصی و UI انتخاب «Import کامل/استخراج» هنوز به ممیزی و تکمیل نیاز دارند؛ وجود raw representation به‌تنهایی دلیل اتصال همهٔ NPVها نیست.

### Fixtureهای خصوصی

فایل‌ها در git قرار نگرفته‌اند. آزمون با `GHAJAR_PRIVATE_FIXTURES` آن‌ها را بیرون مخزن می‌خواند؛ هیچ credential در خروجی تست چاپ نشده است.

| قالب | اندازه | SHA-256 |
|---|---:|---|
| NPVT1 | 4564 | b78728e291da0deddeb1d03de471b02c32970b769071088c2dfb27e98af235a5 |
| NPVS5 | 1987 | cb18da73e0f9f4373ad2a76973e498e0ce69c08aad055b516967fafc9a1e93bf |
| NPVS5 | 1629 | 9a6622c7385b26e9ea7cc5ca3f9e3cd2ee564b7bdbebfb254a424d7cfe1cdf7e |
| BPF | 1155 | 543539b0a4c4f5e5faed52c1a9fa7922daf663c78cdcf4dcd5e92cd7241c155e |

## WireGuard

ایرادهای سورس قبلی: کاهش conf چند peer به یک peer، حذف تنظیمات peer/DNS، تغییر تحمیلی endpoint در مسیر Warp و اتکا به TUN kernel در محیط rootless. `WireGuardProfile` سند اصلی را نگه می‌دارد و همهٔ peerها، AllowedIPs، PSK، keepalive، Address، DNS و MTU معتبر را به generator می‌دهد؛ noKernelTun=true و domainStrategy متناسب با IPها تنظیم می‌شود. DNS فایل، fallback عمومی پیدا نمی‌کند. گزینهٔ محلی ناشناخته به دستور shell تبدیل نمی‌شود.

آزمون `scripts/validation/wireguard_loopback.py`، با خروجی واقعی `WireGuardCorpus.kt` (parse conf → serialize/restore ProxyConfig → ConfigBuilder.buildForTest)، روی باینری رسمی Xray 26.3.27 انجام شد. تنها تغییر test config، افزودن inbound SOCKS برای probe میزبان است؛ outbounds و DNS generator تغییر نکرده‌اند. پنج مورد پاس شدند: IPv4 TCP peer1، IPv6 TCP peer2، DNS+TCP عبوری، UDP، و عدم دسترسی پس از مرگ peer. مقصدهای آزمایش non-loopback و server-side redirect به echo محلی‌اند؛ آدرس loopback مستقیماً داخل netstack مصرف می‌شد و harness اولیه اصلاح شد. هیچ endpoint عمومی یا credential کاربر استفاده نشد.

هش archive رسمی با فایل digest upstream تطبیق داده شد: `23cd9af937744d97776ee35ecad4972cf4b2109d1e0fe6be9930467608f7c8ae`. version core برابر 26.3.27/d2758a0 است. شواهد در `test-results/wireguard-loopback.json`؛ این باینری Linux، AAR مشترک Android نیست. تست گوشی، UDP شبکهٔ کاربر، واقعی بودن credential، MTU مسیر و handshake سرور کاربر هنوز لازم‌اند. **حل کامل همهٔ اتصال‌های WireGuard/AmneziaWG روی گوشی اعلام نمی‌شود.**

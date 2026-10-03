# آماده‌سازی production و منشأ باینری مشترک

## افزونه‌ها

- ShadowQUIC: `spongebob888/shadowquic@5540e3a32ca73c85af125723e4262e02cf28ebcd`؛ Cargo.lock، manifest و LICENSE از همان pin دوباره خوانده و هش شدند. نام ShadowTLS جایگزین آن نشده است.
- Mihomo: درخت Bettbox `3189346611caeba73aa87feaf708e4fd65115d16`، ماژول `core/Clash.Meta` و replaceهای داخل همان checkout؛ `-mod=readonly` در آزمون native و بسته‌بندی حفظ شد. patch محلی جدید `native/plugin-mihomo/patches/profile-sandbox.patch` باید جزو source bundle و provenance باشد. خروجی `--version` هویت پایه است و به‌تنهایی تأیید patch یا باینری نیست.
- API میزبان همچنان ۱ است. `settings.filesVersion=1` یک extension دادهٔ profile است؛ Binder/Wire یا قرارداد TUN عوض نشده. میزبان/افزونهٔ قدیمی نمی‌تواند اجرای dependency محلی را تضمین کند؛ فایل کامل باید حفظ و هنگام prepare با خطای روشن متوقف شود.
- هیچ APK افزونه ساخته/امضا نشده؛ اندازه/هش واقعی APK و install/update/rollback هنوز وجود ندارد. native host test با build نهایی Android یکی نیست.

مسیر آماده‌شده:

1. `prepare-shadowquic-plugin.sh` و `prepare-mihomo-plugin.sh` checkout تمیز با commit دقیق را الزام می‌کنند؛ `--locked`/`-mod=readonly`، دو ABI واقعی ARM، API 26 و alignment 16 KiB حفظ شده. script Mihomo patch sandbox را اعمال و در خروج معکوس می‌کند. این اسکریپت‌های native Android اجرا نشده‌اند.
2. Gradle هر افزونه اکنون versionName و versionCode تأییدشدهٔ مالک را صریح می‌خواهد؛ نسخهٔ پیش‌فرض نمایشی نباید production محسوب شود. certificate واقعی میزبان و وجود هر دو native binary همچنان شرط preBuild است.
3. `plugin-source-bundle.py` بدون Build/امضا از upstream تمیزِ همان pin، source قرارداد و adapter، patchها، Gradle metadata و dependency source archiveهای هش‌شده tar قطعی می‌سازد. `SOURCES.json` باید برای هر dependency، module/version/license/archive/sha256 داشته باشد. مقایسهٔ closure با Cargo.lock یا go.mod/go.sum لازم است؛ صرف ارائهٔ یک آرشیو، کامل‌بودن source را ثابت نمی‌کند. این مرحله recipe دارد؛ source bundle کامل وابستگی‌های production هنوز تولید/ممیزی نشده است.
4. پس از اجازهٔ ساخت، `plugin-release-manifest.py` ابتدا **APK واقعی** را با apksigner verify و aapt بررسی می‌کند؛ package/version، service identity قراردادی، ARM ABIها، assets مجوز، certificate، اندازه و hash از فایل واقعی استخراج می‌شوند. payload محدود به 64 KiB با RSA حداقل 3072/SHA256 امضا و بلافاصله verify می‌شود. min host version/code و URL واقعی ورودی مالک‌اند. sourceBundle hash/size در همان payload امضاشده ثبت می‌شود. این ابزار ساخت/انتشار یا تغییر TrustedPublishers انجام نمی‌دهد و provenance native را از روی نام package اثبات نمی‌کند.
5. سه unit test امضای معتبر، tamper، رد کلید ضعیف و origin نامعتبر اجرا شدند؛ کلید موقت فقط در حافظهٔ test ساخته شد و در production/فایل config/Trust قرار نگرفت. validation واقعی apksigner/aapt روی APK افزونه به فاز مجاز ساخت منتقل است.

مجوزها: ShadowQUIC پایه MIT و Mihomo GPL-3.0؛ مجوز transitiveها مستقل است. کپی LICENSE و lockfile در packaging آماده است؛ archive تمام source وابستگی‌ها و notices نهایی باید در بستهٔ متناظر release حاضر باشد. کل bundle برنامه و upstream نباید صرفاً برچسب یک مجوز بگیرد.

### ورودی لازم مالک

`ProductionPublishers.all` عمداً خالی مانده است. لازم است publisher ID، public key واقعی manifest (RSA SPKI Base64)، certificate SHA-256 واقعی APK ناشر، plugin IDs و HTTPS download hostهای مجاز، certificate واقعی اپ میزبان و نسخه/حداقل نسخهٔ تأییدشده تعیین شوند. private key باید در مخزن امن امضا باقی بماند؛ وارد سورس یا گزارش نشود. URL، hash، certificate و نسخهٔ فرضی اضافه نشده است.

Workflow فعلی به `SIGNING_BUNDLE` یا `KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD` ارجاع می‌دهد؛ وجود ارجاع اثبات موجودبودن secret در GitHub نیست. در محیط این بررسی هیچ‌یک از این متغیرها و `GHAJAR_MANIFEST_KEY_PASSWORD` تنظیم نبود. مقادیر محرمانه چاپ/خوانده نشده‌اند و حضور secretهای remote قابل تأیید نبود. بدون هویت ناشر واقعی، پرکردن Trust مجاز نیست.

## AAR مشترک Xray/Psiphon

شواهد تازه، بدون اجرای خود باینری Android:

- `app/libs/ca.psiphon.aar`: اندازهٔ **68,297,940** بایت، SHA-256 `213ce36cf9faa2f9149c568541ed178b987f83b5576719ff61b49fc8ad8805a1`؛ بدون تغییر.
- `go version -m` روی چهار کتابخانهٔ داخل AAR اجرا شد. metadata ابزار سازنده `go1.26.3`، `gobind/gobind`، `-buildmode=c-shared`، `CGO_ENABLED=1` و flags alignment 16 KiB را ثبت می‌کند. Go 1.26.8 این میزبان فقط برای خواندن metadata و host test بود؛ نسخهٔ سازندهٔ AAR معرفی نشده است.
- Xray module: `github.com/xtls/xray-core v1.260327.0` با sum ثبت‌شده؛ Psiphon `v0.0.0 => .../psiphon-tunnel-core (devel)`؛ QUIC Psiphon به `third_party/quic-go-fork (devel)`؛ qpack/v4legacy به `third_party/qpack-v4legacy (devel)`؛ root wrapper به `.ghajarvpn-src (devel)` جایگزین شده‌اند. این مسیرها commit دقیق ندارند.
- `CURRENT_SOURCE_AND_AAR.json` hash تمام فایل‌های **درخت فعلی** Psiphon، root go.mod/go.sum و build-info چهار ABI را نگه می‌دارد. فایل‌های محلی QUIC/qpack و replace/dtls نیز در snapshot هستند. این inventory منشأ قطعی باینری محسوب نمی‌شود.
- دوازده فایل تغییرکردهٔ Psiphon با ref مقایسهٔ قبلی `shirokhorshid/psiphon-tunnel-core@df55f0ac0eed3d6846501744b7f086a42fcadfaf` دوباره دریافت/مقایسه شدند و diff واقعی در `PSIPHON_LOCAL_DELTA.patch` ثبت شد. فایل‌های فقط محلی (UDP، QUIC/qpack و غیره) در inventory باقی‌اند؛ patch دوازده فایل به‌تنهایی کل delta نیست. درخت فعلی نیز `pion/dtls/v2 => ./replace/dtls` دارد؛ آن را به‌اشتباه با module مستقل dtls/v3 داخل metadata یکی نمی‌گیریم.

### چه چیزی مجهول است؟

commit/محتوای دقیق چهار replace محلیِ **هنگام ساخت AAR**، merged go.mod/go.sum موقت gobind، نسخهٔ دقیق gomobile/gobind، همهٔ build tags، نسخهٔ واقعی NDK/clang سازنده، Java wrapperهای ادغام‌شده و دستور نهایی assembly. NDK `28.2.13676358` در workflow *فعلی* آمده، اما این workflow AAR را بازسازی نمی‌کند و صرفاً وجودش را بررسی می‌کند؛ آن نسخه را به‌عنوان NDK قطعی سازنده اعلام نمی‌کنیم.

### مسیر بازسازی فاز ۷

ابتدا مالک/سازندهٔ AAR باید archive یا commit درخت‌های replace و wrapper موقت، merged module files، نسخهٔ gomobile/NDK و log دستور اصلی را فراهم کند. سپس در checkout جدا، تمام ورودی‌ها با hash inventory و patchها قفل شوند؛ Go **1.26.3** یا تغییر toolchain صریحاً مصوب انتخاب شود؛ هر دو package واقعی `gozarcore` و `github.com/Psiphon-Labs/psiphon-tunnel-core/MobileLibrary/psi` در **همان** اجرای gomobile bind و همان runtime ساخته شوند. چهار ABI موجود و Java API با AAR مرجع مقایسه شوند، سپس حداقل ARMهای توزیع آزموده شوند.

شکل فرمان، نه ادعای بازیابی فرمان گمشده:

```sh
# فقط در فاز مجاز، از merged module دقیق بازیابی‌شده:
# go run golang.org/x/mobile/cmd/gomobile@<RECOVERED_PIN> bind \
#   -target=android -androidapi=26 -o <CANDIDATE_AAR> \
#   -ldflags='<RECOVERED_FLAGS>' <RECOVERED_GOZARCORE_PACKAGE> \
#   github.com/Psiphon-Labs/psiphon-tunnel-core/MobileLibrary/psi
```

placeholderها عمداً فرمان قابل اجرای production نیستند؛ جعل pin/toolchain جای provenance را نمی‌گیرد. اگر ورودی تاریخی بازیابی نشود، باید baseline جدید **به‌عنوان باینری جدید نیازمند qualification** ساخته شود؛ برابری hash با AAR قدیم وعده داده نمی‌شود. پیش از جایگزینی، Android/TUN، Psiphon، Xray config behavior، API/ABI و اتصال واقعی شرط‌اند. اکنون AAR سالم جایگزین نشده است.

# ساخت موتور Psiphon برای قاجار وی‌پی‌ان (AAR ترکیبی gozarcore + psiphon)

## معماری — چرا یک AAR «ترکیبی»؟

gomobile دقیقاً **یک runtime گو به‌ازای هر process** پشتیبانی می‌کند. اگر `gozarcore.aar`
(موتور Xray) و یک `ca.psiphon.aar` مستقل (فقط psi) هم‌زمان در اپ باشند، هر دو کلاس‌های
`go.Seq` و کتابخانه `libgojni.so` خودشان را می‌آورند؛ نتیجه:

- `:app:checkDebugDuplicateClasses` به‌خاطر کلاس‌های تکراری `go.*` fail می‌شود.
- merge کتابخانه‌های native هم برای `libgojni.so` تکراری شکست می‌خورد یا با
  `pickFirst` رانتایم اشتباه برای یکی از دو موتور انتخاب می‌شود (خرابی runtime).

بنابراین پکیج psi از psiphon-tunnel-core **داخل ماژول gozarcore لینک می‌شود** و یک bind
واحد، AAR واحدی به نام `app/libs/ca.psiphon.aar` تولید می‌کند که شامل:

- `gozarcore.*` — همان API قبلی Xray (بدون هیچ تغییر در کد Kotlin موجود)
- `gozarcore.PsiphonProvider` / `gozarcore.PsiphonProviderNetwork` / ... — پل Kotlin↔Go
- `gozarcore.Gozarcore.psiphonStart/psiphonStop/...` — فرانت psi

لایه رسمی `ca.psiphon.PsiphonTunnel` (با رابط `HostService`) یک فایل Java مستقل است که
از مخزن رسمی Psiphon گرفته و در مسیر
`app/src/main/java/ca/psiphon/PsiphonTunnel.java` داخل سورس اپ قرار دارد؛ برای همین
`PsiphonEngine.kt` بدون تغییر با `import ca.psiphon.PsiphonTunnel` کار می‌کند.

جریان ترافیک (بدون TUN bridge دوم):

```
Android VpnService (GozarVpnService)
        ↓
PsiphonController.start → PsiphonTunnel → psi (Go)
        ↓  onListeningSocksProxyPort (callback واقعی)
Local SOCKS 127.0.0.1:<port>
        ↓
Xray / Gozarcore (outbound socks)
        ↓
TUN
```

## پیش‌نیازها

- **Go** جدید (go.mod ماژول gozarcore نسخه ۱.۲۶ را می‌خواهد)
- **Android NDK** با متغیر `ANDROID_NDK_HOME` (یا `ANDROID_NDK_ROOT`)
- **Android SDK** با حداقل یک پلتفرم (`$ANDROID_HOME/platforms/android-XX`)
- `git`, `unzip`, و ترجیحاً `readelf` (برای گیت ۱۶KB alignment)

## مرحله ۱ — بازسازی درخت سورس کامل

درخت GitHub «توزیع پچی» است؛ اول درخت کامل را بسازید:

```bash
scripts/bootstrap-from-upstream.sh          # خروجی: .ghajarvpn-src/
```

## مرحله ۲ — کلون کردن فورک psiphon-tunnel-core

فورک مشخص را **کنار** ریپو (یا هر جای دیگر با `PSIPHON_DIR`) کلون کنید:

```bash
git clone -b shirokhorshid https://github.com/CluvexStudio/psiphon-tunnel-core.git ../psiphon-tunnel-core
```

کامیت مرجح (پین‌شده در CI): `83aa73b9b982e7421e00117f5b0c5aceb5dda452`

## مرحله ۳ — ساخت AAR ترکیبی

```bash
chmod +x scripts/build-psiphon-aar.sh
export ANDROID_NDK_HOME=/path/to/ndk
export ANDROID_HOME=/path/to/android-sdk
./scripts/build-psiphon-aar.sh .ghajarvpn-src ../psiphon-tunnel-core
```

اسکریپت:

1. `gomobile`/`gobind` را با نسخه پین‌شده نصب می‌کند (`GOMOBILE_VERSION`).
2. `psiphonbind.go` را موقتاً به ماژول gozarcore اضافه و `go.mod` را با
   `replace` به فورک وصل می‌کند (پس از build بازگردانی می‌شود).
3. `gomobile bind` با ۴ ABI (`android/arm,android/arm64,android/386,android/amd64`
   — با `PSIPHON_TARGETS` قابل تغییر) و `ANDROID_API` (پیش‌فرض ۳۵) اجرا می‌کند.
4. `-checklinkname=0` (لازمِ وابستگی in-proxy) و لینک‌فلگ‌های
   `max-page-size=16384` (الزام ۱۶KB page) را اعمال می‌کند.
5. خروجی را در `app/libs/ca.psiphon.aar` (و کپی داخل درخت build) می‌نویسد.
6. با `readelf -lW` بررسی می‌کند هر `libgojni.so` دقیقاً `0x4000` aligned باشد؛
   در غیر این صورت build را fail می‌کند.

## مرحله ۴ — Gradle

dependency در `app/build.gradle.kts` همین حالا وجود دارد و CI نیز هنگام build آن را
در درخت بازسازی‌شده جایگزین `gozarcore.aar` می‌کند:

```kotlin
implementation(files("libs/ca.psiphon.aar"))
```

⚠️ `gozarcore.aar` قدیمی باید از dependencyها حذف بماند (دو runtime گو ممنوع).
فایل قدیمی فقط به‌عنوان آرشیو در مخزن مانده و در build استفاده نمی‌شود.

## تست سریع

```kotlin
PsiphonController.available()  // true = کلاس‌های psi در AAR حاضرند
```

`PsiphonController.start()` قبل از `Gozarcore.start` باید صدا زده شود و SOCKS port
فقط از callback واقعی `onListeningSocksProxyPort` خوانده می‌شود (timeout آماده‌باش
۶۰ ثانیه؛ شکست سایفون هرگز اپ را crash نمی‌کند).

## نکات

- **AAR را commit نکنید** — در `.gitignore` هست؛ CI در هر اجرا می‌سازد.
- اگر alignment رد شد یعنی NDK/gomobile قدیمی است.
- این فایل شامل کد GPL (PsiphonTunnel.java از Psiphon Inc.) است؛ متن لایسنس در
  مخزن فورک موجود و در THIRD_PARTY_NOTICES به آن اشاره شده است.

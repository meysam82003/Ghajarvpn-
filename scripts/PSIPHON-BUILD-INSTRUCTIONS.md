# ساخت `ca.psiphon.aar` برای موتور Psiphon

کدهای Kotlin موتور Psiphon (`PsiphonEngine.kt`, `PsiphonConfig.kt` و بقیه‌ی سیم‌کشی‌ها) کامل و آماده‌ست، ولی برای کامپایل شدن به یک آرتیفکت باینری به اسم `ca.psiphon.aar` نیاز داره که کلاس‌های `ca.psiphon.PsiphonTunnel` و `psi.Psi` رو فراهم می‌کنه. این فایل یک باینری Go کامپایل‌شده (gomobile) است و **من نمی‌تونم اون رو بسازم** — نه دسترسی اینترنت دارم، نه Go/NDK/Android SDK روی محیطی که توش کار می‌کنم نصبه. این کار باید روی سیستم خودتون یا CI انجام بشه.

## پیش‌نیازها

روی ماشینی که این کار رو انجام می‌دید باید این‌ها نصب باشن:

- **Go** (نسخه‌ی جدید، هرچی `go.mod` سورس psiphon-tunnel-core می‌خواد)
- **Android NDK** — و متغیر محیطی `ANDROID_NDK_HOME` روش تنظیم شده باشه
- **Android SDK** با حداقل یک پلتفرم نصب‌شده (`$ANDROID_HOME/platforms/android-XX`) — متغیر `ANDROID_HOME` یا `ANDROID_SDK_ROOT` تنظیم بشه
- ابزارهای معمول: `git`, `unzip`, و ترجیحاً `readelf` (برای چک کردن alignment، اختیاریه)

## مرحله ۱ — کلون کردن سورس psiphon-tunnel-core

این فورک مشخص رو **کنار** ریپوی اصلی Ghajarvpn (نه داخلش) کلون کنید:

```bash
cd ..   # یک پوشه بالاتر از ریشه‌ی پروژه‌ی Ghajarvpn
git clone -b shirokhorshid https://github.com/CluvexStudio/psiphon-tunnel-core.git
```

اگه می‌خواید جای دیگه‌ای کلون کنید، بعداً متغیر `PSIPHON_DIR` رو به همون مسیر ست کنید.

## مرحله ۲ — اجرای اسکریپت ساخت

اسکریپت `build-psiphon-aar.sh` (همراه همین فایل، توی همین زیپ) رو به `scripts/build-psiphon-aar.sh` توی ریشه‌ی پروژه کپی کنید، بعد:

```bash
chmod +x scripts/build-psiphon-aar.sh
export ANDROID_NDK_HOME=/path/to/ndk
export ANDROID_HOME=/path/to/android-sdk
./scripts/build-psiphon-aar.sh
```

این اسکریپت:
1. `gomobile`/`gobind` رو نصب می‌کنه.
2. داخل `psiphon-tunnel-core` می‌ره و با `gomobile bind` باینری Android (arm, arm64, x86, x86_64) رو می‌سازه.
3. خروجی رو کپی می‌کنه به: **`app/libs/ca.psiphon.aar`**
4. (اختیاری) چک می‌کنه که `libgojni.so` داخلش روی هر ABI با alignment `16KB` ساخته شده باشه (لازمه‌ی نسخه‌های جدید Android/Google Play).

خروجی چیزی شبیه این می‌بینید:

```
[psiphon] wrote /path/to/Ghajarvpn/app/libs/ca.psiphon.aar
[psiphon] every libgojni.so is 16 KB aligned
```

## مرحله ۳ — اضافه کردن به Gradle

توی `app/build.gradle.kts`، داخل بلوک `dependencies { ... }` این خط رو اضافه کنید:

```kotlin
implementation(files("libs/ca.psiphon.aar"))
```

اگه پوشه‌ی `app/libs` وجود نداره، بسازیدش و مطمئن بشید `ca.psiphon.aar` همون‌جاست.

## مرحله ۴ — Sync و Build

پروژه رو Gradle Sync کنید. اگه همه‌چیز درست باشه، `PsiphonEngine.kt` باید بدون خطای "unresolved reference: ca.psiphon" کامپایل بشه. برای تست سریع اینکه AAR درست اضافه شده:

```kotlin
PsiphonController.available()  // باید true برگردونه
```

## نکات مهم

- **متغیرهای قابل تنظیم:** اگه می‌خواید ABI خاصی بسازید (مثلاً فقط arm64 برای تست سریع‌تر)، قبل از اجرای اسکریپت:
  ```bash
  export PSIPHON_TARGETS="android/arm64"
  ```
- **نسخه‌ی gomobile:** اسکریپت یک نسخه‌ی پیش‌فرض pin شده داره؛ اگه به مشکل خوردید، با `GOMOBILE_VERSION` می‌تونید نسخه‌ی دیگه‌ای امتحان کنید.
- این فایل AAR رو توی گیت commit نکنید مگر با Git LFS — حجمش (به‌خاطر چهار ABI) قابل توجهه. بهتره در CI ساخته بشه یا یک‌بار لوکال بسازید و مستقیم به‌عنوان artifact نگه دارید.
- اگه با خطای عدم alignment روبه‌رو شدید (`not 16 KB aligned`)، یعنی نسخه‌ی NDK یا gomobile‌تون قدیمیه؛ به‌روزش کنید.

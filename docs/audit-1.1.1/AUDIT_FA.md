# ممیزی Core / Protocol / Method برای Ghajar VPN 1.1.1

تاریخ snapshot: 2026-10-01. مبنای سورس: `a9983a1fbf6dfada4e38421e4f84f5e5b44ac51c` روی `work/1.1.1-phase1`، شامل اصلاحات فاز قبل. مبنای Release سالم 1.0.10: `a0867f8277c8bc5677d2bd2e462c58374d77d81d`.

این فاز فقط Inventory و ممیزی سورس است. هیچ کد اجرایی، backend، ربات یا Mini App تغییر نکرده؛ هیچ Build، Gradle task، اجرای Core، GitHub Actions، Release یا tag انجام نشده است. تصمیم‌های ماتریس، پیشنهاد برای فاز Integration هستند و به معنی نصب یا فعال‌سازی نیستند. نسخهٔ برنامه همچنان 1.0.10 / 30025 است.

## دامنه و خروجی قابل پیگیری

- همهٔ ۲۷٬۱۲۳ فایل tracked پروژه، همراه با Git blob ID، در [GHAJAR_TRACKED_TREE.txt](GHAJAR_TRACKED_TREE.txt) آمده‌اند. پوشهٔ backend نیز فقط Inventory شده است.
- تمامی صفحه‌های فهرست مخازن عمومی هفت حساب خوانده شد: xchacha20-poly1305: ۹۴، appshubcc: ۵، Noisemux: ۱، patterniha: ۳۱، CluvexStudio: ۲۸، bepass-org: ۲۲، shirokhorshid: ۵؛ مجموع ۱۸۶. بررسی محدود به pinned repository نبود.
- با MahsaNG، Xray، sing-box و ۸ upstream وابستهٔ مرتبط، کاتالوگ ۱۹۷ مخزن دارد. برای ۱۲۸ مخزن مرتبط snapshot سورس و بررسی فایل‌های پیاده‌سازی/manifest موجود است؛ Feint و sdk خالی‌اند. مخازن نامرتبط فقط غربال ارتباط موضوعی شده‌اند، نه ممیزی خط‌به‌خط.
- ۸٬۳۴۰ ref مربوط به branch/tag و ۱٬۹۸۴ رکورد Release گردآوری شد. default branch، branchهای مهم و تفاوت با release پایدار برای Coreهای اصلی مقایسه شده‌اند. وجود branch با نام dev به معنی جدیدتر بودن نیست؛ مثلاً dev/test در Bettbox از main عقب‌ترند.
- [REPOSITORY_MATRIX.json](REPOSITORY_MATRIX.json): ۱۹۷ ردیف با تمام ستون‌های درخواستی؛ [نمای خوانا](REPOSITORY_DECISIONS.md).
- [FEATURE_MATRIX.json](FEATURE_MATRIX.json): ۵۳ تصمیم در سطح قابلیت، با مسیر سورس Ghajar و permalink نسخهٔ بررسی‌شدهٔ upstream؛ [نمای خوانا](FEATURE_DECISIONS.md). تصمیم در سطح قابلیت بر برداشت کلی از مخزن اولویت دارد.
- [UPSTREAM_CATALOG.json](UPSTREAM_CATALOG.json): commitها، همهٔ refs، Releaseها، نتیجهٔ مقایسهٔ branchهای منتخب، gitlinkهای submodule، مجوز و فعالیت. جزئیات assetهای تاریخی در فایل فشردهٔ همراه نگه‌داری شده‌اند.
- [SOURCE_EVIDENCE.json](SOURCE_EVIDENCE.json): مسیر و SHA-256 فایل‌های انتخاب‌شده برای بررسی. این فهرست نمونهٔ شواهد است، نه ادعای خواندن تک‌تک خطوط همهٔ وابستگی‌ها. Coreها و قابلیت‌های اصلی تا مسیر تولید config، launch و پیاده‌سازی بررسی شدند؛ پروژه‌های فرعی با نمونه‌برداری سورس، manifest و مجوز تعیین تکلیف شدند.

این ممیزی ایستا است: سازگاری source-level، سلامت runtime روی گوشی، مقاومت در برابر DPI در شبکهٔ واقعی و امنیت کامل یک پروژه، ادعاهای متفاوتی‌اند. موارد دوم تا چهارم صرفاً با این گزارش تأیید نمی‌شوند. اندازهٔ افزوده و سرعت فرضی نیز عددسازی نشده‌اند.

## Inventory واقعی Ghajar

| لایه | وضعیت و نسخهٔ قابل اثبات | محل شاهد |
|---|---|---|
| Android | minSdk 26؛ target 36؛ نسخه 1.0.10، کد 30025؛ release برای arm64-v8a و armeabi-v7a؛ x86_64 فقط مسیر emulator | app/build.gradle.kts |
| Xray | `v1.260327.0`، هم در go.mod و هم metadata باینری AAR | go.mod؛ BINARY_MODULE_EVIDENCE.json |
| Psiphon | همان AAR مشترک؛ module replacement محلی `(devel)`؛ exact binary commit نامعلوم | app/libs/ca.psiphon.aar؛ native/Psiphon |
| sing-box | `v1.15.0-alpha.9`، pin `132b38e9caaba1a1959354d518e54d2d08419afe`؛ executable مستقل پشت SOCKS/zeptun | scripts/build-singbox.sh؛ engine/SingBoxConfig.kt |
| zeptun | `v1.1.1`، pin `5620e57cdbf1a4464567a404adbff7cffd9b4bb9`؛ مالک TUN | android.yml؛ ZeptunEngine.kt |
| Aether | `v2.1.0`، pin `21e7150ac2225caa01cbb96ac572b5c0cc1e1dc2`؛ بدون feature مربوط به Tor | scripts/build-aether.sh؛ Aethercontroller.kt |
| OpenVPN | سورس vendored `2.8_git`؛ نه ادعای نسخهٔ پایدار 2.8؛ مسیر اختصاصی OpenVPN | openvpn/.../version.m4؛ CoreManager.kt |
| IKEv2 | strongSwan `6.0.7` در configure.ac؛ مسیر مستقل | strongswan/configure.ac |
| Tor | دو ELF موجود؛ رشتهٔ نسخه `Tor 0.4.9.11`؛ این رشته provenance کامل build نیست | jniLibs/arm64-v8a و armeabi-v7a/libtor.so |
| Tor PT | lyrebird `75ef9b2c1f18`؛ obfs4، meek_lite، webtunnel، snowflake | scripts/build-tor-pt.sh |
| helper | AmneziaWG v3.1.20260828، Mieru v3.38.0، Brook، SSTP و SoftEther سفارشی | native/ghajar-helper/go.mod و سورس |
| Juicity | `88dbf4f8efc54d70caf319ec79d0b7f12d70faf1`؛ مطابق gitlink پلاگین Husi | scripts/build-juicity.sh |
| DNS tunnels | dnstt `v1.20260501.0`؛ VayDNS، NoizDNS، MasterDNS، StormDNS، CottenDNS؛ slipstream-rust `7de506b...` | scripts/build-dnstt.sh، build-dns-tunnels.sh، build-slipstream.sh |
| nDPI | classifier با ntop/nDPI 6.0، `1a529339...`؛ این pin متعلق به فورک CluvexStudio نیست | scripts/build-ndpi.sh |

درخت اصلی Ghajar gitlink فعال ندارد؛ بعضی سورس‌های vendored فایل .gitmodules خودشان را دارند. صرف وجود چنین فایلی به معنی دانلود یا build آن submodule در برنامه نیست.

مسیرهای Core از روی `engine/CoreManager.kt` و `SingBoxConfig.kt` تعیین شده‌اند: OpenVPN و IKEv2 اختصاصی؛ HY2 در Xray؛ HY1/TUIC/AnyTLS/Naive/OpenConnect/MASQUE و بعضی sidecarها از مسیر sing-box؛ Aether، Psiphon و Tor کنترل‌کنندهٔ خود را دارند. نام پروتکل در parser به‌تنهایی اثبات اجرای موفق نیست. فایل‌های native تولیدشونده در این فاز ساخته نشده‌اند.

`ca.psiphon.aar` موجود 68,297,940 بایت است، حدود 65.1 MiB. libgojni خام در ARM64 برابر 46,808,912 و ARMv7 برابر 44,718,044 بایت است. جمع باینری‌های همهٔ ABIها نه اندازهٔ دانلود هر APK است و نه RAM. libtor خام ARM64 برابر 8,379,672 و ARMv7 برابر 6,878,788 بایت است. اندازهٔ دقیق APK نهایی یا اثر افزودهٔ پلاگین‌ها تا Build مجاز بعدی نامعلوم می‌ماند.

## نتیجهٔ بررسی‌های اصلی

### Husi: معماری و تنظیمات، نه جایگزینی کورکورانه

snapshot شاخهٔ dev برابر `36c79ffe...` است؛ main و release پایدار v2.1.6 روی `91eedc10...` قرار دارند. نسخهٔ آزمایشی اپ v2.2.0-alpha.1 از releaseهای پلاگین جدا شده است.

`libcore/go.mod` از **sing-box رسمی alpha.8** استفاده می‌کند؛ Ghajar اکنون alpha.9 را pin کرده است. `libcore/plugin/trusttunnel/outbound.go` نشان می‌دهد قابلیت جدید می‌تواند در registry ثبت شود؛ ادعای نیاز قطعی به تعویض کل sing-box درست نیست. تصمیم: حفظ Core رسمی و adapter کوچک برای نیاز مشخص.

`Plugins.kt` گواهی امضای ناشران را بررسی می‌کند، اما مسیر customAuthoritiesPrefixes در همان فایل از این بررسی عبور می‌کند. آن استثنا نباید کپی شود. پیشنهاد پلاگین Ghajar: APK امضاشده، هویت ناشر دقیق، ABI و API version، قابلیت‌های اعلام‌شده، integrity، نصب صریح و rollback. executable دانلودشدهٔ ناشناس جایگزین این قرارداد نیست. پلاگین‌کردن آینده نباید قابلیت‌های سالم bundled در 1.0.10 را حذف کند.

Hysteria2، Juicity، Mieru، NaiveProxy، AnyTLS، TUIC، MASQUE، OpenConnect و OpenVPN همگی در سطح وجود پروتکل جدید نیستند. شکاف اصلی در تنظیمات است: port hopping، interval/range، AnyTLS idle session، TUIC UDP-over-stream و timeout، ECH مختص endpoint، و احراز هویت/گواهی‌های enterprise در OpenConnect. OpenVPN نیز مسیر سالم اختصاصی دارد؛ علاوه بر آن، build tag مربوط به OpenVPN در sing-box فعال است، هرچند routing اپ OpenVPN را به ماژول اختصاصی می‌فرستد.

**ShadowQUIC غایب است و با ShadowTLS یکی نیست.** upstream فعلی پلاگین Husi، `spongebob888/shadowquic` با gitlink `5540e3a...` و v0.4.0 است؛ فورک هم‌نام xchacha مبنای درست انتخاب نسخه نیست. پیشنهاد: ON_DEMAND_PLUGIN مشروط به بررسی dependencyهای QUIC/TLS سفارشی، pin، ABI، اتصال client/server و lifecycle. وجود plugin upstream به‌تنهایی مجوز فعال‌سازی پیش‌فرض یا تأیید امنیت رمزنگاری نیست.

مجوز Husi شامل GPL-3.0-or-later و شرط اضافی نام/association است؛ GPL ساده نامیدن آن کافی نیست. مجوز هر dependency نیز مستقل ثبت شده است.

### Bettbox: Mihomo واقعی و تفاوت semantics

در `core/go.mod`، Mihomo با `./Clash.Meta` جایگزین محلی شده است؛ `constant/version.go` نسخهٔ آمادهٔ قابل اتکا ندارد. بنابراین نسخهٔ اپ v1.19.3 را نباید نسخهٔ Mihomo نامید. snapshot vendored tree معیار است.

providers در `core/common.go` و `adapter/provider`، routing و rules، TUN و OpenVPN در `adapter/outbound/openvpn.go` و `transport/openvpn` واقعاً وجود دارند. این‌ها فقط متن README نیستند. node import فعلی Ghajar کل providers/rules یک YAML را نگه نمی‌دارد. تصمیم: **Mihomo اختیاری با حفظ full-config semantics**؛ نه تبدیل ناقص همهٔ YAMLها به یک پروفایل عمومی.

TUN/MIPStack نباید به «پروتکل VPN» تبدیل شود یا هم‌زمان با zeptun مالک رابط شود. branch main و release v1.19.3 در انتخاب خودکار stack تفاوت دارند. Flutter plugins این پروژه نیز platform bridge هستند و با پلاگین پروتکل قابل نصب Husi یک مفهوم نیستند.

### Noisemux: نسخهٔ فعلی درست است

pin Ghajar و snapshot upstream یکسان‌اند. TCP، UDP/SOCKS5، Fake-IP و Android protect callback در سورس وجود دارند. `config.icmpForward()` در حالت auto فقط برای direct handler true است؛ پاسخ محلی ICMP اثبات عبور ICMP از SOCKS نیست. package rules و Android users تنظیمات مسیر‌یابی‌اند، نه پروتکل.

بودجهٔ 64 MiB در Ghajar سقف تنظیم‌شده است؛ مصرف واقعی اندازه‌گیری نشده. هیچ ادعای برتری قطعی سرعت، باتری یا اندازه نسبت به engine دیگر بدون آزمون گوشی ثبت نشده است. تصمیم: BUILT_IN، بدون تعویض.

### Aether و ZedPass

Aether v2.1.0 فعلی مسیرهای H3، H2، WG، gool، mim، scan و exit-loc را دارد و Ghajar بیشتر آن‌ها را به آرگومان واقعی وصل کرده است. `AetherSpec` حالت mim دارد، ولی `OblivionOptions` تنها masque/wg/gool را نگاشت می‌کند؛ این تفاوت UI/config باید در فاز ادغام لحاظ شود.

Tor-only، WARP→Tor و Tor→WARP در Aether feature-gated هستند و `build-aether.sh` فعلی feature تور را فعال نمی‌کند. Tor مستقل Ghajar با این سه حالت یکسان نیست. پیشنهاد: ابتدا بررسی orchestration با Tor موجود؛ افزایش dependency native فقط با دلیل روشن و اندازه‌گیری بعدی.

ZedPass یک اپ Flutter با `sstp_flutter` است، نه Core SSTP تازه. Ghajar helper خودش SSTP، pin و DNS دارد. HTTP CONNECT proxy و بعضی TLS options شکاف قابل بررسی‌اند. `vpn_provider.dart` در ZedPass بررسی hostname و گواهی را به‌طور پیش‌فرض false می‌گذارد و password را در preferences نگه می‌دارد؛ این رفتارها نباید کپی شوند. helper فعلی Ghajar پیش‌فرض verification روشن دارد و نباید تضعیف شود.

### patterniha، CluvexStudio و روش‌های آزمایشی

PattNG و فورک‌های Xray تغییرات واقعی config/finalmask/ECH دارند؛ main، my-releases و branchهای feature یک snapshot یکسان نیستند. پیشنهاد فقط merge قابلیت مشخص با schema migration است؛ جایگزینی کامل Core باعث از دست رفتن کنترل compatibility می‌شود.

SNI-Spoofing از pydivert/WinDivert و تزریق sequence استفاده می‌کند؛ نسخهٔ مستقیم Android rootless نیست. Serverless-for-Iran تولیدکنندهٔ configهای direct/fragment است، نه یک پروتکل جدید یا تضمین VPN بی‌سرور برای همهٔ ترافیک. Free-Configs parser و تولید subscription دارد؛ پذیرش منبع باید opt-in، محدود، قابل حذف و مستقل از اعتماد به نودها باشد.

paqet، phantun، QQ/QS Tunnel، MMDF و روش‌های raw-packet/desktop به‌خاطر نام جذاب یا تعداد گزینه‌ها وارد برنامه نمی‌شوند. الگوریتم framing یا XOR در یک prototype به‌تنهایی امنیت تونل نیست. Workerها و relayهای سمت سرور نیز داخل Android کپی نمی‌شوند.

در CluvexStudio، branchهای MASQUE HTTP/2/WARP و finalmask در Xray، بازیابی panic/JNI و zeddns در AndroidLibXrayLite، و ترکیب Coreها در ZedSecure واقعاً سورس دارند. چون Ghajar Aether و sing-box دارد، فقط تفاوت لازم باید انتخاب شود. اضافه‌کردن AAR ترکیبی دیگر می‌تواند با go.Seq و lifecycle فعلی برخورد کند. nDPI classifier است و qpack کتابخانهٔ HTTP/3؛ هیچ‌کدام گزینهٔ VPN تازه نیستند.

### bepass، shirokhorshid و Mahsa

Oblivion فعلی فقط پوستهٔ قدیمی warp-plus نیست: سورس سپتامبر Tor را Core مستقل و chain را دوطرفه مدیریت می‌کند و readiness را با درخواست واقعی SOCKS بررسی می‌کند. Ghajar اکنون Aether→Psiphon را دارد؛ جهت‌های دیگر باید جدا بررسی شوند. warp-plus و tunl صرفاً برای افزودن نام Core بیشتر وارد نمی‌شوند. smartSNI یک مسیر DNS/server دارد؛ علاوه بر نبود root LICENSE، lifetime پاسخ buffer pool و substring matching برای اقتباس مستقیم مناسب نیستند.

در مقایسهٔ سورس vendored Psiphon با شاخۀ shirokhorshid، ۹۴۲ فایل Go/Java یکسان، ۱۲ فایل تغییرکرده و ۴۵۷ فایل محلیِ اضافه دیده شد. این شمارش شامل dependencyها هم هست و اندازهٔ یک patch واحد نیست. metadata باینری exact Psiphon commit را ثابت نمی‌کند. قبل از update باید commitها، patchها و replaceهای QUIC/DTLS/qpack ثبت و بازتولید شوند؛ حذف تغییرات محلی راه‌حل نیست. جزئیات در PSIPHON_SOURCE_COMPARISON.json است.

README خود MahsaNG ناقص‌بودن سورس مربوط به رمزگذاری config و احراز هویت اختصاصی را بیان می‌کند. این بخش‌ها SKIP هستند. NikaNG مرجع buildable معرفی‌شده است، اما snapshot عمومی آن قدیمی‌تر است و شاهد برابری با Mahsa v17 نیست.

- **Conduit** در `PsiphonVpnService.kt` انتخاب INPROXYهای Psiphon است؛ Core مستقل نیست. Ghajar در `PsiphonConfig.kt` به علت نبود ServerEntrySignaturePublicKey عمداً احتمال انتخاب INPROXY را صفر کرده است. بدون پیش‌نیاز معتبر نباید صرفاً دکمه فعال شود یا کلید سرویس دیگری برداشته شود.
- **xDNS** سورس باز در `GFW-knocker/Xray-core/transport/internet/finalmask/xdns` دارد. این ماسک DNS روی PacketConn است، با DoH یا dnstt موجود یکسان نیست. candidate برای merge محدود با validation، cancellation، framing، MTU و حفاظت پروتکل بالادست است؛ هنوز آمادهٔ فعال‌سازی نیست.
- **dnstt داخل فورک Xray** از متغیر package-level `globalTunnel` استفاده می‌کند؛ ownership چند پروفایل و هم‌زمانی در این adapter قابل اتکا نیست. کپی مستقیم آن رد شد؛ sidecar فعلی Ghajar حفظ می‌شود.
- **DNS scanner** layout عمومی دارد، اما controller/activity متناظر در snapshot عمومی پیدا نشد. از وجود layout یا release note، پشتیبانی کامل نتیجه گرفته نشده است.
- XHTTP/SplitHTTP، QUIC، WireGuard، UDP Noise، HY2، DoH، fragment، fake host و chaining در سطح schema/adapter بررسی شدند. بسیاری در Ghajar پایه دارند؛ اختلاف fork-specific option به معنی فقدان کل پروتکل نیست. port hopping جدا در ماتریس ثبت شده است.

### Xray و نسخه‌ها

بر اساس تمام رکوردهای Release خوانده‌شده، آخرین Release غیرآزمایشی XTLS برابر **v26.3.27** است و **v26.9.30 به‌صورت prerelease** علامت خورده است. نسخهٔ داخل AAR فعلی با اولی تطابق دارد. main بررسی‌شده با tag v26.9.30 یکسان است و نسبت به v26.3.27 تعداد ۷۰۵ مسیر تغییر کرده؛ این عدد به معنی ۷۰۵ قابلیت نیست.

بنابراین «آپدیت به آخرین stable» دلیل درستی برای تعویض فوری نیست. ردیف UPDATE_EXISTING برای ارزیابی مهاجرت prerelease مشروط است: ابتدا schemaهای ConfigBuilder، finalmask، XHTTP، ECH، WireGuard/QUIC و wrapper APIs باید حفظ یا مهاجرت داده شوند. شاخه‌های تاریخی مثل dev-shadowtls و bring-quic-back نیز خودکار انتخاب نمی‌شوند.

sing-box فعلی alpha.9 است؛ آخرین stable v1.14.2 است. برگشت کورکورانه به stable ممکن است قابلیت‌های جدید موجود را حذف کند. pin فعلی باید تا تصمیم سازگار بعدی حفظ شود.

## ترتیب پیشنهادی فاز بعد

1. تعیین قرارداد capability برای هر Engine و قرارداد پلاگین امضاشده؛ بدون حذف قابلیت‌های bundled سالم.
2. اصلاح fidelity تنظیمات موجود: hopping، AnyTLS/TUIC، ECH و enterprise auth، با حفظ import/export و سازگاری پروفایل‌های 1.0.10.
3. انتخاب محدود: Mihomo full-config اختیاری، ShadowQUIC اختیاری و در صورت نیاز TrustTunnel/EasyConnect؛ هرکدام جداگانه با مجوز، ABI و امنیت بررسی‌شده. ON_DEMAND_PLUGIN به معنی مجوز انتشار فوری نیست.
4. روش‌های Aether/Tor/Psiphon و DNS را در لایهٔ method/engine نگه‌دار؛ xDNS یا fork patches تنها با قرارداد مشخص. Conduit تا رفع پیش‌نیاز معتبر غیرفعال بماند.
5. در انتهای همهٔ فازها و فقط با دستور کاربر: یک CI/Build کامل 1.1.1 برای دو ABI، schema/runtime regression، DNS/IPv6 leak، reconnect/teardown، حافظه/باتری/اندازه و license notices. در این فاز هیچ‌کدام اجرا نشده است.

یک ناسازگاری مستنداتی هم ثبت شد: SOURCE-POLICY.md قدیمی fetch در CI را منع می‌کند، ولی اسکریپت‌های فعلی سالم برای چند Core از upstreamهای pin‌شده fetch می‌کنند. این ممیزی هیچ‌کدام را تغییر نداده است؛ فاز معماری باید سند و روش provenance را با حفظ مبنای سالم هماهنگ کند.

گزارش‌های قبلی مانند AUDIT_V5_FA.md حذف نشده‌اند، اما برای نسخه‌ها، اندازهٔ AAR، میزان پشتیبانی Aether Tor، معماری Husi و Mihomo باید شواهد این snapshot را مبنا گرفت.

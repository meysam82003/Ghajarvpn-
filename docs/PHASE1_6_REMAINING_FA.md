# باقی‌مانده‌های واقعی فازهای ۱ تا ۶ — 1.1.1

گزارش اولیهٔ ۲۰۲۶-۱۰-۰۱؛ ادامهٔ توسعه تا ۲۰۲۶-۱۰-۰۳، مخزن `meysam82003/Ghajarvpn-`، شاخهٔ `work/1.1.1-phase1`.

**نتیجه: توسعهٔ این مرحله انجام شده، اما نمی‌توان گفت از فازهای ۱ تا ۶ هیچ کار توسعه‌ای باقی نمانده است.** Tor روی Aether، مسیر اشتراک sing-box و وابستگی‌های امن Mihomo کد اجرایی تازه دارند؛ جهت معکوس Aether/Tor، xDNS، SSO/HOTP و provenance تاریخی/ناشر واقعی هنوز محدودیت‌های مشخص دارند. جزئیات زیر بین نقص کد، ورودی بیرونی و qualification فاز۷ فرق می‌گذارد.

ابتدای کار و بازبینی remote بعدی، HEAD همان `4fa72ebe6dde038d0641917c80cb18bf0ae8f867` بود. reset انجام نشد. گزارش‌های پیوست و هر ۹ سند خواسته‌شده پیش از تغییر کد خوانده شدند؛ چهار گزارش پیوست فاز ۳ تا ۶ با نسخهٔ مخزن یکسان بودند. Audit پیشنهاد است، نه حکم نهایی؛ تکمیل‌های فازهای بعدی دوباره ناقص اعلام نشده‌اند. Backend، ربات، Mini App، store و AAR سالم دست‌نخورده‌اند.

**هیچ Gradle/product APK/native Android build، CI، Tag یا Release اجرا نشده است.** کامپایل مستقل JVM/Java و آزمون Go میزبان انجام شد؛ این‌ها خروجی قابل نصب Android نیستند. هیچ فاز۷ خودکار شروع نشده است.

## ادامهٔ نشست صندوق — پس از checkpoint bbd8177c

جزئیات تغییر، فایل‌ها و محدودیت‌ها در [VAULT_RUNTIME_FA](remaining-1.1.1/VAULT_RUNTIME_FA.md) ثبت شده است. این نوبت ۱۴۳ تست app، ۲۲ log/browser و ۱۷ renewal پاس شدند؛ پنج انتقال WireGuard نیز دوباره پاس شدند. grant یک‌بارمصرف، کنترل runtime سهمیه/زمان، جلوگیری از fallback خودکار، UI مبتنی بر VaultEntry و فایل موقت امن‌تر sing-box اضافه شدند. سهمیهٔ حجمی sing-box و Server mode تا داشتن منبع حسابداری واقعی فعال نشده‌اند. رابط/سرویس Android اجرا یا full typecheck نشده‌اند؛ کارهای توسعه‌ای صریحاً در گزارش باقی‌اند.

## ادامهٔ ۲۰۲۶-۱۰-۰۳ — وضعیت جاری

مرجع جدید remote `f293b163` شامل ممیزی ZedSecure دریافت و با کار محلی ادغام شد؛ مرجع اولیه reset نشد. عبارت «store دست‌نخورده» در گزارش اولیه مربوط به دامنهٔ همان مرحله بود؛ در ادامه فقط اصلاحات UI/خطای فروشگاهِ صریحاً خواسته‌شده انجام شد. Backend/ربات/Mini App و AAR مشترک تغییر نکرده‌اند.

| کار ادامه | چه چیزی اضافه/اصلاح شد | وضعیت و باقی‌مانده |
|---|---|---|
| BPF | reader واقعی libbox، حدود gzip/JSON، full config/remote metadata، ورودی SAF/Intent و Details | پیاده‌سازی ناقص: TUN Full Config، scheduler remote و UI استخراج جدا باقی‌اند |
| NPVT/NPVS | NPVT1، v1 app-key/passphrase، v5 compact، NPVO1، raw/policy/unknown records، IPv6 | پیاده‌سازی ناقص: NPVTSUB1، signature، full Xray runtime و برخی mappingهای اختصاصی |
| WireGuard | حفظ conf چند peer، key/address/MTU validation، DNS بدون fallback، noKernelTun | انتقال واقعی میزبان در پنج سناریو پاس؛ Android/AAR و شبکهٔ واقعی هنوز فاز۷ |
| فروشگاه | پیام ثابت عمومی برای خطای شبکه/SSL/HTTP به جای exception دارای host | پیاده‌سازی‌شده در سورس؛ تست UI آفلاین روی دستگاه باقی است |
| Update/About | modal قفل هنگام دانلود/verify، hash+signature الزامی، تاریخچهٔ release و badge/notification شرطی | پیاده‌سازی‌شده در سورس؛ لمس خارج کادر، نصب و notification/device هنوز فاز۷ |
| اعلان/ویجت | کنترل اختیاری پیش‌فرض روشن، وضعیت/سرور/location عبوری؛ رنگ دو ویجت متناسب با تم | پیاده‌سازی‌شده در سورس؛ آزمون launcher/notification دستگاه باقی است |
| آیکون launcher متغیر | پیاده نشده | تصمیم مشروط کاربر برای عدم افزودن حالت ناقص؛ تغییر همهٔ رنگ‌ها/launcherها تضمین نمی‌شود |
| OpenConnect | گسترش گزینه‌های عمومی pin واقعی در فرم/parser/share/generator، secretها خارج Share عمومی | پیاده‌سازی ناقص: SSO مرورگر و شمارندهٔ HOTP نیازمند کانال API موتور؛ اتصال شش gateway روی دستگاه باقی است |
| DNS | تنظیمات واقعی VayDNS با validation تا CLI، رد transport ناشناخته | پیاده‌سازی ناقص: resolver pool/fanout مستقل؛ TCP گزینهٔ جعلی ندارد |
| Chain | Carrier→Exit و reject self/cycle/missing/UDP mismatch؛ pass-through chain در relaunch/switch | مسیرهای پشتیبانی‌شده تست مستقل دارند؛ cross-engine عمومی ناقص است |
| GSB2 | crypto/repository، UI مبتنی بر VaultEntry، grant حافظه‌ای و اتصال Xray/sing-box، کنترل زمان و سهمیهٔ محلی Xray در سرویس | پیاده‌سازی ناقص: biometric، wizard/backup کامل، سایر adapterها و حسابداری کامل sing-box/server؛ Android تأیید نشده |
| Quota | Long boundaries، ledger اتمیک، امضای snapshot، قرارداد provider/parent reservation | پیاده‌سازی ناقص: hook نشست زنده و UI؛ server enforcement علاوه بر کد به API/کلید/پنل واقعی نیاز دارد |
| Desktop | بررسی reference و تصمیم فاز مستقل | فقط طراحی‌شده؛ shared migration و تست سه OS انجام نشده‌اند |

جزئیات Import/اتصال: [IMPORT_CONNECTION_REMAINING_FA](remaining-1.1.1/IMPORT_CONNECTION_REMAINING_FA.md). مقایسه و مجوز ZedSecure: [ZEDSECURE_GHAJAR_INTEGRATION_REPORT_FA](ZEDSECURE_GHAJAR_INTEGRATION_REPORT_FA.md). قرارداد و محدودیت‌های صندوق: [SAFEBOX_V2_SECURITY_DESIGN_FA](SAFEBOX_V2_SECURITY_DESIGN_FA.md).

اعداد بخش «شواهد اجرا» در انتهای گزارش اولیه، تاریخی‌اند. نتیجهٔ آخرین اجرای checkpoint در `remaining-1.1.1/test-results/host-2026-10-03.txt` و `source-2026-10-03.txt` ثبت می‌شود؛ آزمون Linux WireGuard جدا است. هیچ‌یک اثبات تکمیل کل فازها یا همهٔ UIها نیست.

## تطبیق تعهدات

تمام ۵۳ ردیف Audit، بدون حذف ردیف‌های SKIP و پیشنهادهای مشروط، در [جدول تطبیق](remaining-1.1.1/COMMITMENT_MATRIX_FA.md) و [JSON](remaining-1.1.1/COMMITMENT_MATRIX.json) یک وضعیت از شش وضعیت خواسته‌شده دارند. موارد فازهای دیگر و تعهدات این درخواست:

| تعهد | وضعیت فعلی | تست تازه یا دلیل |
|---|---|---|
| فاز۱: محاسبه/تأیید مجدد قیمت تمدید و حفظ store | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | ۱۷ آزمون واقعی `GhajarRenewalTest` دوباره پاس شدند؛ هیچ backend تغییر نکرد |
| فاز۲: inventory و تصمیم نسخه/دامنه | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | source recheck و تطبیق ۵۳ ردیف؛ تست اتصال محسوب نمی‌شود |
| فاز۳: Trust/API/slot افزونه | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | `PluginContractsTest` و javac API واقعی؛ production publisher جداگانه مسدود است |
| فاز۴: تنظیمات protocol، free-source، SSTP و mappingهای Aether | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | suiteهای Phase4Settings/ProtocolForms/ForeignImport دوباره اجرا؛ اتصال هر سرور هنوز فاز۷ |
| فاز۵: relay احرازشده و Direct Share | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | SharingTest دوباره اجرا؛ sing-box TCP/DNS test تازه؛ دو دستگاه هنوز فاز۷ |
| فاز۶: mKCP/REALITY/WG و محافظت مرورگر/log | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | Phase6Regression و ۲۱ تست log/site permissions دوباره اجرا؛ ۴۲ انتقال قدیمی mKCP دوباره اجرا نشد |
| Tor روی Aether | صرفاً منتظر Build یا تست دستگاه در فاز۷ | orchestration و validator/generator واقعی اضافه؛ lifecycle و انتقال fixture میزبان پاس؛ خود دو باینری Android به شبکه واقعی وصل نشده‌اند |
| Aether H2 روی Tor | پیاده‌سازی ناقص | lookup سیستم در API-front موتور پین‌شده؛ patch و qualification لازم، در UI فعال نیست |
| xDNS | مسدود به پیش‌نیاز خارجی | انتخاب wire contract و سرور هدف لازم؛ سپس اصلاح/آزمون موتور مشترک، نه صرفاً Build |
| SSO/HOTP OpenConnect | پیاده‌سازی ناقص | bridge اجرایی auth و persistence counter نداریم؛ مشخصات IdP/سرور آزمایش هم لازم است |
| Mihomo قابل اجرای API۱ | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | ۶ آزمون native واقعی؛ پذیرش config، sandbox و TCP selector/توقف؛ زیرمجموعهٔ مجاز به‌صراحت مشخص است |
| Mihomo interactive selector و policy خارج API۱ | فقط طراحی‌شده | در تعهد مصوب قبلی interactive API نیامده؛ full text ذخیره می‌شود اما semantics ناسازگار رد می‌شوند؛ API۲ نیاز به طراحی/پیاده‌سازی جدا دارد |
| APK production افزونه‌ها | مسدود به پیش‌نیاز خارجی | ناشر/کلید/certificate/URL/نسخهٔ واقعی لازم؛ سپس build/install/update/rollback در فاز مجاز |
| منشأ کامل AAR | پیاده‌سازی ناقص | snapshot و patch فعلی تکمیل؛ درخت تاریخی producer/merged module/toolchain هنوز از مالک لازم |
| ارتقای Xray، Conduit/INPROXY و SKIPها | تصمیم مصوب برای عدم افزودن | 26.3.27 حفظ شد؛ شروط ارتقای 26.9.30 و پیش‌نیاز سرویس دور زده نشد |

## ۱. زنجیرهٔ Aether/Tor

**قبل:** `AETHER_TOR.md` فقط طراحی دو جهت را داشت؛ shared ownership وارد service نشده بود.

**اکنون:** `ChainSession` هر resource را پیش از start مالک می‌شود، readiness واقعی را قبل از موتور بعدی می‌سنجد، startup deadline و مرگ پیش‌نیاز را با interrupt متوقف می‌کند و در خطا/لغو با ترتیب معکوس می‌بندد. cleanup ناموفق مخفی نمی‌شود و resource برای retry تحت مالکیت می‌ماند. نسل قبلی پیش از نسل جدید باید بسته شود. controllerها پیش از رهاکردن مالکیت، پایان process را انتظار می‌کشند.

مسیر عرضه‌شده: **برنامه → Xray/TUN میزبان → Tor SOCKS 19150 → انتقال Tor با SOCKS Aether → شبکه Tor → مقصد**. Tor فقط پس از probe واقعی HTTPS با TLS معتبر از Aether شروع می‌شود؛ readiness نهایی نیز انتقال HTTPS از Tor است. Aether/Tor هیچ VpnService/TUN دیگری نمی‌سازند. پورت‌ها با listenerهای فعلی و relay تداخل‌سنجی می‌شوند؛ شکست bind خطا است. رزرو پورت پیش از spawn آزاد می‌شود، بنابراین خود bind موتور و probe همچنان شرط‌اند؛ ادعای رزرو اتمیک FD در subprocess نداریم.

DNS مقصد با TCP از همان زنجیره می‌رود؛ UDP عمومی صریحاً blackhole است. H3/WireGuard/UDP برای *عبور از Tor* ادعا نشده‌اند. در جهت Tor روی Aether، transport زیرین خود Aether ممکن است H3 باشد؛ این به معنی حمل UDP برنامه توسط Tor نیست. bridge بدون PT یا vanilla با IP/fingerprint معتبر پذیرفته می‌شود؛ obfs4/snowflake در این ترکیب بدون اثبات proxy propagation رد می‌شوند. مدل/Backup گزینه‌ها را حفظ می‌کنند؛ direct/split/fakeDNS/chain ناسازگار به‌جای تغییر بی‌صدا رد می‌شود.

در مرگ chain، service پیش از بستن مسیر forwarding یک TUN مسدودکننده نگه می‌دارد، حتی اگر kill switch اختیاری خاموش باشد. اگر Android جایگزین را نپذیرد، TUN قدیمی fail-closed حفظ می‌شود. تغییر تنظیم اشتراک chain نیز blocker را تا TUN جدید نگه می‌دارد. این wiring هنوز به آزمون واقعی Android نیاز دارد؛ مرگ process میزبان و revoke سیستم خارج از توان یک process برای تضمین دائمی‌اند و باید همراه Always-on/Block without VPN روی دستگاه ارزیابی شوند.

**فایل‌ها:** `AetherTorPolicy.kt`, `AetherTorRuntime.kt`, `ChainSession.kt`, `ProcessCleanup.kt`, `GozarVpnService.kt`, `Aethercontroller.kt`, `Torcontroller.kt`, `OblivionOptions.kt`, `OblivionSettings.kt`, `ConfigBuilder.kt`, `CapabilityRegistry.kt`.

**آزمون اجراشده:** lifecycle start/ready/reverse stop، partial start failure، مرگ upstream حین startup، timeout، حفظ تنظیمات، bridge/port/direct rejection، generator DNS/UDP، و عبور بایت از دو resource TCP loopback با مالک واقعی ChainSession. این resourceها fixture هستند، نه خود Aether/Tor. controller/runtime Kotlin واقعی type-check شد. اتصال خارجی Tor/Aether و جابه‌جایی شبکه/لغو/مرگ process روی Android به فاز۷ منتقل است.

**جهت دوم:** `app → Aether H2 → Tor → Aether endpoint` فعال نشده؛ علت و تغییر دقیق موتور در [NATIVE_BLOCKERS_FA](remaining-1.1.1/NATIVE_BLOCKERS_FA.md) است. این نقص توسعه است، نه چیزی که فقط با Build حل شود.

## ۲. xDNS و OpenConnect

**قبل:** گزارش فاز۴ به framing/MTU/queue/deadline xDNS و نبود browser SSO/HOTP durable اشاره داشت.

**اکنون:** سورس دقیق چهار variant xDNS و auth موتور پین‌شده دوباره خوانده، ref و SHA ثبت شدند. [NATIVE_BLOCKERS_FA](remaining-1.1.1/NATIVE_BLOCKERS_FA.md) تفاوت wire، تغییر دو سمت، API auth موجود در libbox ولی غایب در executable app، atomic counter غیرماندگار و ورودی دقیق سرور را شرح می‌دهد.

**فایل‌ها:** `TRANSPORT_SOURCE_HASHES.json`, `NATIVE_BLOCKERS_FA.md`, matrix و این گزارش. موتور، auth سالم و قابلیت محصول به‌صورت نمایشی تغییر نکردند.

**آزمون اجراشده:** source/hash comparison و تست‌های موجود تنظیمات/عدم export عمومی secret. **xDNS client/server، ورود SSO و HOTP durable اجرا یا تکمیل نشده‌اند.** وجود فایل/parser/cookie به‌عنوان اتصال گزارش نمی‌شود. انتظار برای owner server contract و توسعهٔ patch/IPC هر دو واقعی‌اند.

## ۳. اشتراک از گوشی

**قبل:** backend relay تنها بعضی مسیرهای Xray بود؛ حضور پروتکل در فهرست UI به معنی backend نبود.

**اکنون:** انتشار پورت backend پس از probe، invalidation نشست/credential پیش از restart، teardown زیر engineLock و readiness مجدد هنگام تعویض شبکه وصل شده‌اند. sing-box یک inbound مخصوص اشتراک دارد که قبل از قواعد عمومی، UDP را رد، DNS TCP را از `proxy` resolve و TCP را به همان outbound می‌فرستد. resolver سیستم فقط برای bootstrap خود سرور موجود است؛ DNS نام مقصدِ اشتراک به آن fallback ندارد. Aether دارای route-direct واجد اشتراک tunnel-only نیست.

| موتور | مسیر/وضعیت دقیق | راه جایگزین |
|---|---|---|
| sing-box | inbound اختصاصی SOCKS، DNS TCP detour به proxy؛ lifecycle مشترک service؛ tailscale بدون exit semantics اثبات‌شده رد | پروفایل سازگار مقصد |
| Psiphon | SOCKS واقعی نشست خود Psiphon، انتشار بعد از probe؛ پس از قطع/reconnect relay قبلی باطل | Direct Share سازگار؛ service credentials جدید ساخته نمی‌شود |
| Aether | SOCKS نشست بدون route-direct؛ اگر Xray مالک TUN باشد backend مجزای Xray به همان زنجیره، و در proxy/zeptun پورت Aether | بستهٔ Ghajar/کلاینت واقعاً سازگار |
| Tor | SOCKS TCP با DNS عبوری؛ هیچ ادعای UDP | پروفایل Tor/Ghajar متناسب |
| OpenVPN | TUN مستقل؛ خروجی SOCKS همان نشست ندارد، socket این UID مسیر امن tunnel-only ایجاد نمی‌کند | فایل ovpn اصلی |
| IKEv2 | strongSwan مالک TUN، relay UID خارج VPN؛ dial معمولی احتمال خروج مستقیم دارد | اطلاعات IKEv2 یا mobileconfig محدود موجود |
| WireGuard فعلی Xray | مدل/protocol موجود است ولی backend اشتراک با DNS و route تضمین‌شده در این مسیر پیاده نیست؛ صرف بزرگ‌کردن allowlist ممنوع | conf/QR استاندارد WireGuard |
| افزونه API۱ | SOCKS/TUN اعلام‌شده tunnel-only بودن full-config را تضمین نمی‌کند؛ rule DIRECT ممکن است وجود داشته باشد | full config/Direct Share؛ extension قرارداد و backend احرازشدهٔ نسل‌دار لازم |

**فایل‌ها:** `SingBoxConfig.kt`, `SingBoxController.kt`, `GozarVpnService.kt`, `CapabilityRegistry.kt`, `SharingUi.kt`, آزمون‌های Kotlin و `native/validation/singbox_sharing_test.go`.

**آزمون تازه:** config از generator واقعی Kotlin صادر شد؛ در sing-box پین‌شده، DNS مقصد آزمایشی با TCP داخل SOCKS upstream پاسخ گرفت و HTTP echo از همان upstream عبور کرد. پس از قطع تنها upstream، درخواست با cache DNS هم fail شد و مقصد ترافیک مستقیم نگرفت. netlink این محیط مجاز نیست؛ فقط پایش تغییر interface با PlatformInterface تستی جایگزین شد. socket/DNS/routing خود موتور واقعی بودند. این تست تأیید Android، TUN، WAN، هر پروتکل enterprise یا همهٔ sidecarها نیست.

relay همچنان **فقط TCP از SOCKS5 CONNECT و HTTP CONNECT** است؛ UDP، HTTP معمولی و gateway شفاف عمومی ندارد. راهنماهای Android/iOS/Windows/macOS/Linux باید کلاینت پذیرندهٔ همین proxy را استفاده کنند؛ proxy سیستم Wi-Fi لزوماً احراز هویت/CONNECT لازم را پشتیبانی نمی‌کند. همهٔ برنامه‌های مقصد تحت کنترل relay نیستند؛ برنامهٔ نادیده‌گیرندهٔ proxy تضمینی ندارد. Direct Share فاز۵ و expiry/auth/بستن نشست قدیمی حفظ و تست‌های مربوط دوباره اجرا شدند. آزمون دو دستگاه/هات‌اسپات/interface/IPv6 واقعی فاز۷ است.

## ۴. Mihomo

**قبل:** full YAML/JSON حفظ می‌شد، اما فایل‌های provider/certificate قابل ورود نبودند و native runner مستقل آزموده نشده بود.

**اکنون:** ورود SAF یک فایل با مسیر نسبی صریح، حداکثر ۱۶ فایل/مجموع ۳۲ KiB، base64+SHA256 در settings، حفظ Backup و materialization فقط داخل profile خصوصی اضافه شد. traversal، symlink، config overwrite و ورودی بیش‌ازحد رد می‌شوند. مسیر/شناسهٔ مقصد در لحظهٔ انتخاب فایل ثبت می‌شود تا بازگشت asynchronous به پروفایل اشتباه ننویسد. فایل‌های بزرگ‌تر این API فعلاً به‌صورت محلی وارد نمی‌شوند؛ ذخیره‌شدن متن config به معنی قابلیت اجرای آن‌ها نیست.

patch موتور bypass مربوط به Android/SAFE_PATHS را می‌بندد تا فایل‌های داخل provider نیز از sandbox خارج نشوند. خطاهای واقعی runner که آزمون آشکار کرد اصلاح شدند: default inert ExternalUIURL دیگر همهٔ configهای سالم را رد نمی‌کند؛ access policy listener مقداردهی می‌شود؛ shutdown علاوه بر TUN، HTTP/SOCKS/mixed و connectionهای فعال را می‌بندد. listenerها loopback هستند.

**واقعاً قابل اجرا در این قرارداد:** full rules/providers/DNS و selector داخلی core در config سازگار، SOCKS/mixed محلی یا TUN انحصاری میزبان. TUN فعال Mihomo descriptor همان VpnService را می‌گیرد و zeptun هم‌زمان مالک نیست. provider محلی باید با مسیر/import معتبر حاضر باشد؛ provider شبکه‌ای تابع URL/config و trust خود آن است. قوانین DIRECT کاربر در full-config به تونل‌اجباری تبدیل نمی‌شوند و به همین دلیل relay عمومی plugin فعال نشده است.

**فقط قابل ذخیره/غیرقابل اجرا:** config دارای listener عمومی/سیستم‌عامل، controller، TProxy/iptables، package/UID/interface policies یا route exclusions خارج قرارداد، فایل گمشده/خارج sandbox و local dependency بیش از سقف API. prepare آن‌ها را با خطا رد می‌کند؛ گزینه silently حذف یا reinterpret نمی‌شود. مدیریت تعاملی selector در API فعلی وجود ندارد؛ انتخاب داخلی/static با native test اثبات شد، UI controller یا تغییر انتخاب زنده ادعا نشده است. برای API۲ باید TunSpec شامل policyهای UID/package/exclusion، نسخه‌مذاکره و مهاجرت صریح config اضافه و روی میزبان قدیمی با خطای incompatible متوقف شود؛ downgrade بی‌صدا مجاز نیست. این extension هنوز پیاده نیست.

**فایل‌ها:** `MihomoFiles.java`, `PluginProfiles.kt`, `PluginActivity.kt`, `MihomoService.java`, `native/plugin-mihomo/{main.go,path_policy.go,patches/profile-sandbox.patch,prepare_test.go,transfer_test.go}`, `prepare-mihomo-plugin.sh`.

**آزمون:** ۶ تست native core پین‌شده با dependencies واقعی پاس: پذیرش سند، رد semantics ناسازگار، provider، path/symlink، selector/TCP و بستن listener. Unix socket در میزبان ممنوع بود؛ فقط کنترل تست SOCKS با `net.Pipe` جایگزین شد، production همچنان Unix خصوصی/SCM_RIGHTS است. TUN fd و Android Binder/SAF/installation واقعی هنوز آزموده نشده‌اند. javac روی API و MihomoService واقعی پاس شد؛ تست فایل‌ها نیز در JVM اجرا شد.

## ۵. Production و provenance

**قبل:** adapter بود ولی APK قابل نصب/TrustedPublisher واقعی و exact producer source مشترک نبود.

**اکنون:** ابزار manifest از artifact واقعی، gate نسخهٔ صریح، recipe source bundle، patch sandbox و inventory/patchهای منبع آماده شدند. [PRODUCTION_AND_PROVENANCE_FA](remaining-1.1.1/PRODUCTION_AND_PROVENANCE_FA.md) ورودی لازم مالک و آنچه هنوز مجهول است را دقیق ثبت می‌کند. AAR همان hash قبلی دارد؛ version string به اثبات تطابق source تبدیل نشده است.

**فایل‌ها:** دو `plugins/*/build.gradle.kts`، `plugin-release-manifest.py`, `plugin-source-bundle.py`, `source_provenance.py`, inventoryها و اسناد این پوشه.

**آزمون:** ۳ تست امضای موقت در حافظه/رد tamper و origin، syntax Python/shell، extraction build-info چهار ABI و SHA فایل‌ها. **production signing، اندازهٔ APK افزونه، source closure نهایی، install/update/rollback انجام نشده‌اند.** کلید/URL/hash/نسخهٔ فرضی یا Trust آزمایشی وارد production نشده است.

## شواهد اجرا و محدودیت اعداد

| اجرای همین نوبت | نتیجه | مرز |
|---|---|---|
| `run_host_checks.py` | ۸۵ تست app + ۲۱ log/browser، همگی پاس | actual source با collaborator fixtures؛ نه Android UI/شبکهٔ هر موتور |
| `check_remaining_sources.py` | ۱۷ تست renewal پاس؛ API/Mihomo Java و controller/runtime Kotlin type-check | syntax service/Compose بررسی می‌شود؛ full Android/Compose compile انجام نشده |
| Go Mihomo پین‌شده | ۶ تست پاس | انتقال TCP واقعی؛ control تست در حافظه، بدون TUN Android |
| Go sing-box پین‌شده | ۱ تست انتقال/DNS/failure پاس | interface monitor تستی، socket و routing واقعی |
| `test_plugin_manifest.py` | ۳ تست پاس | هیچ APK/ناشر production ساخته نشده |
| source provenance | SHA AAR، ۴ build-info و inventory current source ثبت | منشأ تاریخی کامل هنوز اثبات نشده |

دستور بازاجرای JVM در scripts/validation است. برای Go، فایل‌های `native/plugin-mihomo/*.go` در `core/Clash.Meta/cmd/ghajar-plugin` checkout دقیق Bettbox با patch sandbox، و تست sing-box در `cmd/ghajar-sharing` همان pin اجرا شدند؛ فرمان `go test -mod=readonly -count=1 -v` (برای Mihomo با `-tags=with_gvisor`) بود. corpus اشتراک با `SingBoxShareCorpus.kt` ساخته شد و مسیرش در `GHAJAR_SHARING_CORPUS` قرار گرفت. خروجی‌های این نوبت در پوشهٔ `remaining-1.1.1/test-results` ثبت شده‌اند.

عدد تاریخی ۹۵ یا ۴۲ انتقال mKCP به‌عنوان نتیجهٔ جدید تکرار نشده است. شمار آزمون‌های بالا نیز اثبات تکمیل همهٔ قابلیت‌ها نیست. به‌ویژه OpenConnect SSO/HOTP، xDNS، جهت دوم chain و source provenance تاریخی همچنان کار واقعی دارند؛ فهرست پیش‌نیاز/تغییر لازم در اسناد همراه صریح است.

# تطبیق تمام ۵۳ ردیف Audit

وضعیت‌ها دربارهٔ دامنهٔ مصوب‌اند؛ پیشنهاد مشروط به تعهد تبدیل نشده است. توضیح محدودیت و شاهد هر ردیف در JSON هم‌نام است. «منتظر فاز۷» برای قابلیت موجود به معنی آزمون تازه یا تضمین اتصال نیست.

| ردیف | قابلیت | وضعیت | شاهد/مرز |
|---|---|---|---|
| F001 | Verified on-demand Android plugin host | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | قرارداد امضا/API/ABI/slot میزبان پیاده است؛ APK production و publisher واقعی هنوز لازم‌اند. |
| F002 | sing-box registry extension architecture | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F003 | Hysteria2 port hopping | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F004 | Juicity | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F005 | Mieru | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F006 | ShadowQUIC | مسدود به پیش‌نیاز خارجی | adapter و pin/lock بررسی شده؛ APK امضاشده، هویت ناشر و qualification سرور هنوز نداریم. |
| F007 | NaiveProxy | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F008 | AnyTLS session settings | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F009 | TUIC advanced settings | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F010 | MASQUE CONNECT-IP | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F011 | ECH options per protocol | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F012 | uTLS fingerprint | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F013 | OpenConnect enterprise authentication | پیاده‌سازی ناقص | user/pass/TOTP/certificate/MCA حفظ و تنظیمات تست شدند؛ SSO IPC/callback و HOTP durable هنوز کد جدید و سرویس آزمایش می‌خواهند. |
| F014 | OpenVPN import/auth settings | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F015 | Full Mihomo providers/rules/config engine | پیاده‌سازی ناقص | full config و فایل‌های محدود خصوصی، sandbox، selector ثابت و TCP واقعی آزموده شدند؛ interactive controller اضافه نشده، policyهای خارج API 1 صریحاً رد می‌شوند. |
| F016 | Mihomo TUN / MIPStack | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: Do not start two VPN/TUN owners. Optional Mihomo can use local SOCKS or an explicitly exclusive TUN adapter. main and release 1.19.3 differ in auto stack selection. |
| F017 | Mihomo OpenVPN outbound | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: Real implementation exists in vendored adapter/transport; useful inside optional full Mihomo configs, no separate additional VPN menu item. |
| F018 | Flutter platform plugins | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: Bettbox Flutter plugins are platform bridges, not proof of Husi-style externally installable protocol plugins. |
| F019 | TCP + UDP through SOCKS5 | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F020 | ICMP forwarding vs local handling | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F021 | Fake-IP and DNS hijack | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F022 | Android package routing / protected sockets | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F023 | Performance/memory/size | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F024 | MASQUE HTTP/3 | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F025 | MASQUE HTTP/2 | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F026 | WireGuard | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F027 | WARP-in-WARP | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F028 | MASQUE-in-MASQUE | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F029 | Route discovery | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F030 | Tor-only / WARP→Tor / Tor→WARP | پیاده‌سازی ناقص | Tor روی Aether وارد service شد و lifecycle/fixture انتقال آزموده شد؛ H2 Aether روی Tor به‌علت DNS native مسدود و اتصال واقعی Android آزموده‌نشده است. |
| F031 | Certificate verification and TLS options | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: Do not copy upstream false verification defaults or plaintext preference passwords. TLS-version option changes must preserve certificate/hostname validation. |
| F032 | SSTP HTTP proxy | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | پیشنهاد اولیه مشروط بود؛ فاز۴ SSTP CONNECT/TLS/DNS را در helper تکمیل کرده است. دوباره ناقص طراحی محسوب نمی‌شود؛ آزمون دستگاه باقی است. |
| F033 | SSTP custom DNS | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F034 | Mahsa closed config/auth distribution | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: README declares config encryption and Mahsa authentication closed. No service credentials, closed source or release binaries copied. NikaNG does not establish parity with current releases. |
| F035 | Conduit / INPROXY | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: Public Mahsa code selects INPROXY protocol names. Do not invent a separate core or enable it without legitimate prerequisites and a compatible verified Psiphon build. |
| F036 | Psiphon CDN fronting and chaining | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F037 | xDNS finalmask | مسدود به پیش‌نیاز خارجی | xDNS ادغام نشده؛ wire contract/سرور هدف و patch موتور مشترک لازم است. SHA/source comparison جدید موجود؛ client/server xDNS اجرا نشده. |
| F038 | dnstt inside Xray | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: dialer.go uses unsynchronized package-level globalTunnel with no per-profile ownership visible. Avoid copying adapter; keep isolated existing sidecar. |
| F039 | DNS scanner | فقط طراحی‌شده | پیشنهاد Audit مشروط به تعیین رفتار است؛ تعهد مصوب پیاده‌سازی scanner کامل در فازهای بعدی یافت نشد. |
| F040 | XHTTP / SplitHTTP | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F041 | QUIC | صرفاً منتظر Build یا تست دستگاه در فاز ۷ | مسیر موجود حفظ شد؛ از روی سورس دوباره قابلیت حذف یا تکرار نشد. این ردیف ادعای تست عملی تازه ندارد؛ qualification Android یا سرور وابسته در فاز۷. |
| F042 | WireGuard + UDP Noise | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F043 | Hysteria2 | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F044 | DoH | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F045 | Fragmentation / fake host | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F046 | Free config systems | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F047 | Wrong-sequence packet injection | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: pydivert and WinDivert require Windows packet interception; ordinary rootless Android VPN cannot simply call this implementation. |
| F048 | Direct fragment presets | پیاده‌سازی‌شده؛ با ذکر تست انجام‌شده | سورس فعلی و تصمیم فازهای بعدی ملاک است؛ تست JVM تنظیمات/parser/generator شاهد اتصال واقعی نیست. |
| F049 | Tor/Psiphon/WARP chain controller | پیاده‌سازی ناقص | Tor روی Aether وارد service شد و lifecycle/fixture انتقال آزموده شد؛ H2 Aether روی Tor به‌علت DNS native مسدود و اتصال واقعی Android آزموده‌نشده است. |
| F050 | Additional WARP executable | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: Do not add another WARP binary just to increase option count; only specific non-overlapping fixes merit a merge. |
| F051 | smartSNI DNS substitution | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: Source serves DNS/TLS; missing root license plus pooled-buffer response lifetime and loose substring matching make verbatim adoption unsuitable. |
| F052 | Psiphon binary/source reproducibility | پیاده‌سازی ناقص | hash و snapshot و patchهای فعلی ثبت شدند؛ source/merged module و toolchain دقیق سازنده AAR از مالک لازم است. |
| F053 | Xray prerelease migration assessment | تصمیم مصوب برای عدم افزودن | تصمیم عدم افزودن/عدم ارتقا/نامرتبط قبلی حفظ شد: Candidate only: gate schema migration, XHTTP/finalmask/ECH/QUIC behavior, gomobile APIs and protocol regression tests. No update performed and no stable downgrade/revert. |

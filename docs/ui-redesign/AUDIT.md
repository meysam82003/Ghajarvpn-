# UI redesign audit

## Baseline

Inspected the supplied master prompt, SCREEN_MAP, handoff README, all nine PNGs,
and all 13 rendered PowerPoint slides before editing source. The standalone
prompt/deck match the copies in the archive by SHA-256. Images 02 and 09 are identical.

Baseline branch: `ui-redesign-gpt`.
Baseline and fetched `origin/main`: `eb34c4c609c4926a75710c3d5f1b7f33dfe1e6d8`.
The working tree was clean, `git diff --stat origin/main...HEAD` empty, and there
were no unique commits to preserve or merge. Annotated tag `1.0.0` exists and
peels to `a4292dc8cef1f9a5ca0b1c46036f7a79a8be4e3f`.

## Existing feature paths to preserve

| Destination | Real implementation / source of state |
| --- | --- |
| Home and server picker | MainActivity, ConfigStore, VpnState, ConnectDecision, VpnCommandCoordinator |
| Core tunnels | GozarVpnService, ConfigBuilder, gozarcore, sing-box, IKE controller, OpenVPN bridge |
| Free configs | FreeConfigs, FreeFeedRules, SubscriptionRefresher: 72-hour sources, bounded validation workers, max 35 |
| Import/export | clipboard, subscription URL, document picker, QR camera/gallery, manual config editor, ConfigFile |
| Server actions | selection, favorites, sorting, protocol filter, multi-select, deletion, sharing, chaining, tests, autopilot |
| Engine hubs | OpenVPN profiles/credentials/tests, Psiphon, Windscribe, Tor and free projects |
| Store | GhajarShopScreen, GhajarStoreApi, activity-owned GhajarCheckoutViewModel, account/link storage |
| Commerce | plans, custom quote, trial eligibility, wallet, pending payments, resume/cancel, receipts, history, renewals, owned services, support and notices |
| Settings | connection options, per-app routing, logs, preferences, appearance, notifications, language, about/update |
| Tools | VPN Share, connection history, diagnostics, stability test, clean IP, DNS lab, map, network monitor, CheckHost |
| SSH | existing SSH terminal/SFTP screens and stores |
| Backup | ConfigFile authenticated AES-GCM format, ConfigStore settings, OpenVPN profiles/settings, NetworkRules |
| Accounting | UsageStore persisted hourly/daily totals and config breakdown; PerAppUsageStats where Android permits |

These are source-audited paths, not a claim of successful device verification.

## Reference features that must remain conditional

No source of server load percentage or connected sharing-device count was found.
Do not fabricate them. Country is not universally structured in imported configs;
keep the real server name/protocol rather than invent country metadata.
VPN sharing is the existing HTTP/SOCKS proxy transport, not a new routed USB VPN.
QR configures that proxy and is not a separate tunnel engine.
Subscription quota/expiry comes from stored server metadata, not a claim about
live account entitlement. Wallet and payment state stay under the backend API.

## Build baseline

The first `./gradlew --no-daemon --stacktrace :app:assembleDebug` attempt failed
before compilation while downloading Gradle (`Network is unreachable`). No
Android SDK or emulator was preinstalled. A local toolchain setup is being
attempted; the final report must record actual outcomes, not treat this as a pass.

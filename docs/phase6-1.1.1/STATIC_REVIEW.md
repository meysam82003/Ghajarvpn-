# Static review record

Scope: phase6 diff plus the existing first-party paths it touches; phase2 evidence reused for the vendor/core inventory. This is not a formal proof that the entire product has no defects.

| Check | Result / action |
|---|---|
| Source version / native pins | go.mod/go.sum, ca.psiphon.aar, jniLibs, release workflow unchanged from phase6 baseline; Xray module is v1.260327.0. |
| Legacy profile persistence | New spiderX defaults to /; explicit JSON mapping and toolkit path separation; WG PSK preserved; mKCP migration is generated-only. Tests cover round trips. |
| Permissions | Actual 1.0.10 APK manifest read. Microphone/location injected by browser removed at source and host merge guards. READ_EXTERNAL_STORAGE removed at host because OpenVPN src/ui is not a compiled source set. Current merged output is deferred. |
| Active share without intent | Fixed: no share inbound by default; private inbound reconfigured under engineLock with fresh TUN; immediate public relay/session closure on off. Preference observer has no polling and exists only with the VPN service. |
| Startup plugin work | Metadata model retained; absent plugins do not start core/service/timer/native library. No new plugin startup job. |
| Plugin trust | Explicit publisher/cert/signature/hash/size/API/ABI/source pin and capability checks; redirects forbidden; health probe before active slot, prior version retained. Production publisher list stays empty pending genuine signed artifacts. |
| Reconnect / loops | Bounded delays/backoff/coalescing added for network recovery; live monitor/Sharing screen collection lifecycle-bound. Existing bounded auto-selector and active service stats retained. |
| Logging | Ingestion redaction, bounded queue/memory/file rotation, external mirror rotation, bounded crash report. First-party warnings/errors use sanitized sink. SSH command text removed from error logging. |
| Backup | Existing explicit allowlist/export model preserved; no binary, temporary share credentials or active slots. Complete restore invalidates temporary session even with old backup without sharing keys. |
| R8 | Whole-app field keep removed; native/reflection bridge rules retained. No blind native ABI/core removal. Release R8 acceptance still required. |
| TODO / placeholder | No executable TODO/FIXME/NotImplementedError in reviewed net.gozar.app, browser and plugin source. Five TODO comments in vendored ca.psiphon/PsiphonTunnel.java are upstream maintenance notes and were not deleted to manufacture a clean scan. UI placeholder parameters are legitimate hint text. |
| Secrets / debug endpoints | No new production key/token/test endpoint introduced. Synthetic localhost/example.org fixtures are test-only. Core CLI binaries are external qualification inputs, not committed product dependencies. Existing public/bootstrap identifiers are not treated as private signing keys. |
| Navigation / dead UI | Existing routes retained. Removed dead browser geolocation permission callback/constants. Plugin unavailable status remains honest; no new Xray feature tile tied to an absent runtime. |
| Bot / Mini App | No files changed. |

Official Android behavior consulted:
- https://developer.android.com/develop/ui/compose/notifications/channels — channel behavior belongs to the user after creation; HIGH may produce heads-up, LOW is appropriate for ongoing service.
- https://developer.android.com/reference/android/os/PowerManager#getThermalHeadroom(int) — avoid frequent headroom sampling; cache the reading.

See PHASE6_FA.md for explicit unresolved runtime/build acceptance. Retaining the stable core is an acceptance decision, not a claim that candidate upstream regressions are irreparable.

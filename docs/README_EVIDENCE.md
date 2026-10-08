# README evidence · شواهد معرفی قاجار ۱.۱.۲

Reviewed: **2026-10-08**. This record supports the Persian, English, Arabic and Russian README pages. It records source inspection and published files; it is not a new runtime certification of every protocol, device or installer.

## Inspected baselines

| Scope | Fixed reference |
|---|---|
| Android `main` before this documentation change | [`b05889127c49b0f9ddde703a6971260725ef253e`](https://github.com/meysam82003/Ghajarvpn-/commit/b05889127c49b0f9ddde703a6971260725ef253e) |
| Published desktop / iPhone / webOS packaging source | [`e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d`](https://github.com/meysam82003/Ghajarvpn-/commit/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d), on `claude/happy-ritchie-r51k5d` |
| Public download inventory | [Release 1.1.2](https://github.com/meysam82003/Ghajarvpn-/releases/tag/1.1.2), inspected through the GitHub releases API |
| Cross-platform packaging run | [Actions run 37819321326](https://github.com/meysam82003/Ghajarvpn-/actions/runs/37819321326), completed with conclusion `success` at the source commit above |

The separately published source was fetched and read without merging it into `main`. A successful packaging run establishes that packages were produced. Several optional build and end-to-end connection steps use `continue-on-error`; overall green CI alone does not establish that every optional engine or connection test passed.

## Android claims

All relative source links below refer to files in this repository. The baseline commit above fixes the version that was read.

| README subject | Primary source | What it supports / qualification |
|---|---|---|
| Version, Android minimum, architecture | [`app/build.gradle.kts`](../app/build.gradle.kts) | `versionName=1.1.2`, `versionCode=30027`, `minSdk=26`, phone splits ARM64/ARMv7 |
| App interface languages | [`Strings.kt`](../app/src/main/java/net/gozar/app/Strings.kt), Gradle locale filters | Persian and English UI; Arabic/Russian README translations do not imply those app UI languages |
| Engines and protocol routing | [`CoreManager.kt`](../app/src/main/java/net/gozar/app/engine/CoreManager.kt) | Engine registration, protocol families, availability checks; Aether experimental |
| sing-box and helper methods | [`SingBoxConfig.kt`](../app/src/main/java/net/gozar/app/engine/SingBoxConfig.kt), [`build-singbox.sh`](../scripts/build-singbox.sh), [`build-ghajar-helper.sh`](../scripts/build-ghajar-helper.sh) | sing-box profile construction, helper routes and build inputs; presence still depends on the actual build |
| Removed lightweight methods | [`RemovedCores.kt`](../app/src/main/java/net/gozar/app/engine/RemovedCores.kt) | DNSTT, VayDNS, NoizDNS, Slipstream, MasterDNS, StormDNS, CottenDNS, Juicity are blocked for connection/testing in base Android |
| Full `.bpf` profiles | [`SingBoxFull.kt`](../app/src/main/java/net/gozar/app/engine/SingBoxFull.kt) | Full stored config; runtime adapts inbounds and removes unsupported experimental/interface settings |
| File formats and locked variants | [`configtoolkit/`](../app/src/main/java/net/gozar/app/configtoolkit/), [`CONFIG_FORMAT_MATRIX.md`](CONFIG_FORMAT_MATRIX.md) | Recognised formats and distinctions between readable, user-password, vendor/recipient-locked and unsupported files |
| Notifications and same-service renewal | [`GhajarNotificationMonitor.kt`](../app/src/main/java/net/gozar/app/GhajarNotificationMonitor.kt) | Notification action passes the relevant service to renewal; acknowledgement path; network-dependent scheduled refresh |
| Connection notification and GVPN enforcement | [`GozarVpnService.kt`](../app/src/main/java/net/gozar/app/GozarVpnService.kt) | Session traffic, ping/connection actions, local share usage counting and stop on limit |
| Idle Connect notification | [`GhajarIdleNotification.kt`](../app/src/main/java/net/gozar/app/GhajarIdleNotification.kt) | Optional Connect action when disconnected |
| GVPN creation, current screens, Observatory, backup | [`MainActivity.kt`](../app/src/main/java/net/gozar/app/MainActivity.kt) | `GvpnCreateScreen`, `.gsb2` filename, ready-made/custom time and data choices; current navigation and backup exclusions |
| Share group updates and alerts | [`Gvpn.kt`](../app/src/main/java/net/gozar/app/gsb2/Gvpn.kt), [`Gsb2Store.kt`](../app/src/main/java/net/gozar/app/gsb2/Gsb2Store.kt) | Receiving-device local counters; 50/25/10% warning paths and removal of expired/exhausted groups; not a cross-device global commercial quota |
| Phone proxy sharing restrictions | [`PhoneShare.kt`](../app/src/main/java/net/gozar/app/sharing/PhoneShare.kt) | Supported Xray-carried sessions; OpenVPN/IKEv2 and incompatible proxy-only paths are excluded |
| Named per-app profiles | [`ConfigStore.kt`](../app/src/main/java/net/gozar/app/ConfigStore.kt) | Up to 10 named profiles |
| Shop/account/purchase handling | [`GhajarStoreApi.kt`](../app/src/main/java/net/gozar/app/GhajarStoreApi.kt), [`GhajarCheckoutViewModel.kt`](../app/src/main/java/net/gozar/app/GhajarCheckoutViewModel.kt), MainActivity | Backend-supplied shop content, service groups and server-confirmed payment handling |
| Android update verification | [`GhajarUpdateInstaller.kt`](../app/src/main/java/net/gozar/app/GhajarUpdateInstaller.kt), [`UpdateChecker.kt`](../app/src/main/java/net/gozar/app/UpdateChecker.kt) | ABI selection, SHA-256 and installed-certificate verification paths |
| Interactive permissions | [`app/src/main/AndroidManifest.xml`](../app/src/main/AndroidManifest.xml) | Camera/notifications requested; location, recording audio and legacy storage permissions removed during merge |
| Build prerequisites | [Android CI workflow](../.github/workflows/android.yml) | JDK 17, SDK 36, build-tools 36.0.0, NDK 28.2.13676358 and native prerequisites |

Earlier engine/phase documents are historical evidence. Their labels must be reconciled with current routing and `RemovedCores`, rather than copied wholesale as current product promises. The standalone Safebox and old share portal were removed from current navigation; GVPN still uses the GSB2 container and runtime.

## Desktop, iPhone and LG source

These links deliberately use the exact published source commit, because these directories are not present in the inspected Android `main` baseline.

| Subject | Source at `e4302f82` | Finding |
|---|---|---|
| Installer targets and resource inclusion | [web-desktop/package.json](https://github.com/meysam82003/Ghajarvpn-/blob/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d/web-desktop/package.json) | EXE x64, DMG x64/arm64, AppImage x64; version 1.1.2; local core resources; mac identity null |
| App window, payments, tray and notifications | [web-desktop/main.js](https://github.com/meysam82003/Ghajarvpn-/blob/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d/web-desktop/main.js) | Electron shell, bundled PWA interface, local engine integration, native notifications and tray behaviour |
| Connection modes and binary availability | [engine/manager.js](https://github.com/meysam82003/Ghajarvpn-/blob/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d/web-desktop/engine/manager.js) | Local proxy, system proxy, TUN, selected-app modes; capabilities reflect binaries; no blanket claim that all engines ship |
| TUN/per-app configuration and elevation | [engine/singbox.js](https://github.com/meysam82003/Ghajarvpn-/blob/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d/web-desktop/engine/singbox.js), [engine/elevate.js](https://github.com/meysam82003/Ghajarvpn-/blob/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d/web-desktop/engine/elevate.js) | OS-specific tunnel and process matching; administrator elevation required |
| iPhone file | [ios/make-profile.mjs](https://github.com/meysam82003/Ghajarvpn-/blob/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d/web-desktop/ios/make-profile.mjs) | `PayloadType=com.apple.webClip.managed`, full-screen shop URL; removable; no VPN payload |
| LG package | [web-webos/app/index.html](https://github.com/meysam82003/Ghajarvpn-/blob/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d/web-webos/app/index.html), [build.mjs](https://github.com/meysam82003/Ghajarvpn-/blob/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d/web-webos/build.mjs) | Redirects to the PWA with `tv=1`; IPK packaging; no bundled VPN engine |
| Packaging and Android TV provenance | [ghajar-app.yml](https://github.com/meysam82003/Ghajarvpn-/blob/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d/.github/workflows/ghajar-app.yml) | TV files copied from `Ghajarvpn-Android-preview` artifacts; installers unsigned; optional engines and E2E tests can fail without failing the whole run |
| PWA scope and web alerts | [web/README.md](https://github.com/meysam82003/Ghajarvpn-/blob/e4302f82aeb42cb8a4ba6de2095b91a57bcefe8d/web/README.md) | Shop/account interface, external VPN client handoff, web-push implementation needing server setup; not Android-like background delivery on every device |

The desktop directory's older README still describes an earlier proxy-only design. The inspected `main.js`, `engine/manager.js`, packaging and workflow are the current evidence for the implemented connection modes.

## Published file inventory and checksums

The release API contained ten platform binaries/profiles plus `SHA256SUMS.txt`. Sizes are bytes as returned by GitHub at review time.

| File | Bytes |
|---|---:|
| `Ghajarvpn-1.1.2-arm64-v8a.apk` | 87,406,153 |
| `Ghajarvpn-1.1.2-armeabi-v7a.apk` | 86,513,273 |
| `GhajarVPN-AndroidTV-arm64-v8a.apk` | 87,376,374 |
| `GhajarVPN-AndroidTV-armeabi-v7a.apk` | 86,483,110 |
| `GhajarVPN-win-x64.exe` | 194,226,345 |
| `GhajarVPN-mac-arm64.dmg` | 227,147,583 |
| `GhajarVPN-mac-x64.dmg` | 244,897,534 |
| `GhajarVPN-linux-x86_64.AppImage` | 239,688,458 |
| `GhajarVPN-iPhone.mobileconfig` | 30,078 |
| `GhajarVPN-webos.ipk` | 229,348 |
| `SHA256SUMS.txt` | 194 |

The actual [SHA256SUMS.txt](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/SHA256SUMS.txt) was downloaded and read. It lists only:

```text
bf3afaf61ee1c80541ce69c5e0dc39944700ac097d397d28fde6da5e8066f024  Ghajarvpn-1.1.2-arm64-v8a.apk
da6f0f8c1de3c0bad47ca320eddfe949d57f0dfac29e166d354dcae59048f585  Ghajarvpn-1.1.2-armeabi-v7a.apk
```

These are published checksums, not newly calculated hashes of independently downloaded APKs in this documentation task. The manifest does not cover the desktop/TV/iPhone/LG files.

## Artwork and screenshots

- `assets/banner.png`: new generated promotional artwork for 1.1.2, using the existing gold wordmark as the identity reference. Created with the built-in image-generation tool. Prompt: a premium black/emerald/gold landscape poster, preserve the supplied Persian crown wordmark, version `1.1.2`, exact copy `VPN • SERVICES • RENEWAL`, and separate platform lines `VPN: Android · Android TV · Windows · macOS · Linux` / `STORE: iPhone / iPad · LG webOS`; no mock app interface, speed/security guarantees or unlimited-data claims.
- `assets/screenshots/android-connected.jpg`: original upload `1000256303.jpg`.
- `assets/screenshots/android-renew-notifications.jpg`: original upload `1000256306.jpg`.
- `assets/screenshots/android-renew-dialog.jpg`: original upload `1000256307.jpg`.
- `assets/screenshots/android-service-traffic.jpg`: original upload `1000256308.jpg`.
- `assets/screenshots/android-expiry-banner.jpg`: original upload `1000256304.jpg`.

All five screenshots are unedited originals, not emulator captures or generated UI. Their account values are illustrative observations at capture time, not universal service allowances. The translations describe the same feature scope; they do not add runtime features or UI languages.

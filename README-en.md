<div align="center">

<img src="assets/banner.png" width="100%" alt="Ghajar VPN 1.1.2 — VPN, services and renewal; iPhone and LG store access">

# Ghajar VPN

**Multiple connection engines, configuration management, service purchases and renewal**

![Version](https://img.shields.io/badge/release-1.1.2-D4AF37)
![Android](https://img.shields.io/badge/Android-8.0%2B-00A86B)
![Desktop](https://img.shields.io/badge/Desktop-Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-62D6E8)
![Store](https://img.shields.io/badge/Store-iPhone%20%C2%B7%20iPad%20%C2%B7%20LG%20webOS-D4AF37)

[فارسی](README-fa.md) · [English](README-en.md) · [العربية](README-ar.md) · [Русский](README-ru.md)

[Download 1.1.2](https://github.com/meysam82003/Ghajarvpn-/releases/tag/1.1.2) · [Release notes](docs/release-notes/v1.1.2.md) · [Channel](https://t.me/Ghajarvpn) · [Bot and mini app](https://t.me/Ghajar_vpnbot)

</div>

Ghajar brings together VPN connections and service management. The native Android app imports configurations, routes them through compatible engines, displays connection information and provides access to the shop. Desktop packages combine a shop interface with local connection engines. The current iPhone, iPad and LG webOS packages provide **service purchases and account management**; their presence in the project does not imply that they contain a VPN tunnel.

This guide was reviewed on **8 October 2026** against Android source on `main` and the assets attached to release **1.1.2**. Desktop and web packaging source is currently on a separate branch. The [evidence record](docs/README_EVIDENCE.md) links to the inspected commits and distinguishes implemented code from published files and device testing. The four languages here are documentation languages; the Android interface currently has Persian and English resources.

## Platforms and direct downloads

| Device | Published 1.1.2 file | Actual scope |
|---|---|---|
| Android ARM64 phone/tablet | [64-bit APK](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/Ghajarvpn-1.1.2-arm64-v8a.apk) | Native VPN client and shop; Android 8.0+ |
| Android ARMv7 phone/tablet | [32-bit APK](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/Ghajarvpn-1.1.2-armeabi-v7a.apk) | ARM 32-bit devices; Android 8.0+ |
| Android TV / ARM64 box | [64-bit TV APK](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/GhajarVPN-AndroidTV-arm64-v8a.apk) | Android app packaged for TV installation; taken from a `preview` build |
| Android TV / ARMv7 box | [32-bit TV APK](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/GhajarVPN-AndroidTV-armeabi-v7a.apk) | Same Android path for 32-bit ARM TV devices; also a preview output |
| Windows x64 | [EXE installer](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/GhajarVPN-win-x64.exe) | Desktop app with local connection engines and shop |
| macOS / Apple silicon MacBook | [arm64 DMG](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/GhajarVPN-mac-arm64.dmg) | Apple silicon Macs |
| macOS / Intel MacBook | [x64 DMG](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/GhajarVPN-mac-x64.dmg) | Intel Macs; separate from the Apple silicon package |
| Linux x86_64 | [AppImage](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/GhajarVPN-linux-x86_64.AppImage) | Desktop app; grant execute permission first |
| iPhone / iPad | [mobileconfig profile](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/GhajarVPN-iPhone.mobileconfig) | Full-screen shop Web Clip on the home screen; **no built-in VPN engine** |
| LG webOS TV | [IPK package](https://github.com/meysam82003/Ghajarvpn-/releases/download/1.1.2/GhajarVPN-webos.ipk) | Shop web app in TV mode; **no built-in VPN tunnel**; Developer Mode installation |

Choose an Android file by the device's CPU architecture. The TV APK comes from the preview artifact and may have a different signing certificate from the official phone APK. It is the Android application offered for TV installation, rather than evidence of complete testing on every television and remote control.

Run the Windows installer. On macOS, open the DMG and move the app into Applications. Current desktop installers do not carry a developer signing certificate, so the operating system may request approval on first launch. On Linux, enable execution in file properties or run `chmod +x GhajarVPN-linux-x86_64.AppImage`. Full-device desktop tunnelling requires administrator privileges.

Download the iPhone profile in Safari, then install it from Settings → Profile Downloaded. It installs a removable full-screen Web Clip pointing to the shop; it does not configure an iOS VPN. Use a compatible VPN client on iOS to import a purchased service's configuration. Install the LG package with Developer Mode and tools such as webOS Dev Manager or `ares-install`; opening that package alone does not route the TV's internet traffic through a tunnel.

## Real Android screenshots

These are the original user-provided captures. The banner is promotional artwork; the screenshots show the actual Android interface. Server names, remaining data, days and measured latency belong to that account at capture time. They are not fixed product specifications or advertised plan allowances.

<table>
  <tr>
    <td align="center"><img src="assets/screenshots/android-connected.jpg" width="250" alt="Actual connected Android home screen"><br><b>Connection and active service</b></td>
    <td align="center"><img src="assets/screenshots/android-service-traffic.jpg" width="250" alt="Actual remaining data, time, upload, download and ping"><br><b>Allowance and traffic</b></td>
    <td align="center"><img src="assets/screenshots/android-expiry-banner.jpg" width="250" alt="Actual service expiry banner"><br><b>Expiry reminder</b></td>
  </tr>
  <tr>
    <td align="center"><img src="assets/screenshots/android-renew-notifications.jpg" width="250" alt="Android notification shade with a renew this service action"><br><b>Renew from notifications</b></td>
    <td align="center"><img src="assets/screenshots/android-renew-dialog.jpg" width="250" alt="Actual in-app renewal dialog"><br><b>In-app renewal</b></td>
    <td align="center"><b>Home · Shop · Settings</b><br>Dark interface, gold branding, Ghajar green<br>Original app captures</td>
  </tr>
</table>

## Everyday use on Android

The home screen combines connection state, the selected server, protocol and session duration with available service information. When a subscription or panel provides allowance and expiry metadata, the app can display remaining data and time. An ordinary configuration without that metadata cannot provide a real commercial quota figure.

Server groups, favourites, profile details, ping, connection tests and automatic selection help manage a collection of configurations. A test result describes that server and network at the time of measurement. Free sources and imported subscriptions depend on external availability; automatic selection cannot make an expired account or unavailable server work.

Version 1.1.2 redesigns the Ghajar Observatory with a speed display, separate upload and download, session traffic and readings for battery, temperature, CPU and memory. Server details, event history, speed and quality tests, logs and network tools provide ways to inspect connection behaviour. Hardware readings depend on what the device and operating system expose.

## Connection engines and protocols

Ghajar routes a profile to the appropriate engine. A protocol, engine, transport and file format are separate concepts: successfully reading a file does not establish that its engine is present or that the remote endpoint is reachable.

| Android 1.1.2 code path | Examples of implemented capabilities | Conditions |
|---|---|---|
| Xray | VLESS, VMess, Trojan, Shadowsocks, SOCKS, HTTP, Hysteria2, WireGuard; REALITY, XHTTP, WS, gRPC, HTTPUpgrade, KCP | Compatible profile and server; options depend on the protocol |
| sing-box | TUIC, Hysteria 1, AnyTLS, SSH, Snell, OpenConnect, NaiveProxy, ShadowTLS | Required binary and configuration must be present |
| sing-box with helper | AmneziaWG, Mieru, Brook, SSTP, SoftEther and multiple SSH tunnel methods | Requires the helper and the protocol's parameters |
| OpenVPN | `.ovpn` import and a dedicated connection path | Certificates, keys or account credentials as required by the server |
| IKEv2 / IPsec | strongSwan module and IKEv2 profiles | Compatible credentials and certificate settings |
| Psiphon | Built-in engine and tunnel-protocol selection | Network and service availability |
| Tor | Tor engine and bridge configuration | Valid bridge and the relevant transport components |
| Aether | MASQUE / WARP connection path | Marked experimental in the code |

This table describes implementation paths, not successful device testing of every method on every phone. Core availability checks report missing binaries. Special protocols still require a compatible profile, build and network.

The lightweight base Android app explicitly blocks connection through **DNSTT, VayDNS, NoizDNS, Slipstream, MasterDNS, StormDNS, CottenDNS and Juicity**. Some of their profiles remain editable, transferable and available for backup. Preserving a profile is not the same as shipping its connection engine. PPTP and L2TP/IPsec are not advertised as working connection paths here. Earlier development reports can describe components subsequently removed from the base APK.

## Configuration import and management

Import routes include links, clipboard text, QR codes, files and subscriptions. Compatible Xray and sing-box JSON, Clash-style YAML, WireGuard files and OpenVPN profiles have defined paths. Clash import converts supported entries into usable profiles; it does not mean that the app runs an independent Mihomo/Clash engine or reproduces every rule in a Clash file.

Full sing-box `.bpf` profiles also have an import path. NPVT/NPVS support depends on the format: readable copies and some user-passphrase-protected variants can be processed. Files locked with a vendor app key or recipient/device restrictions are reported as locked or unsupported. A promise to decrypt every protected injector file would be inaccurate.

Subscriptions can be grouped and refreshed, and profiles can be added, edited and exported. Cross-platform compatibility depends on the protocol and receiving client. A Ghajar-specific container is not automatically readable by other VPN apps.

## Shop, account and purchased services

The shop connects to the Ghajar backend, Telegram bot and mini app. Plans and prices are supplied by the panel. Purchases, trials when offered, wallet top-ups and receipts, discount codes, partner shops, account functions and support paths are part of the system; their availability depends on the shop's configuration and response.

A purchased service can be added as a subscription, with its servers shown in a separate group. Services carrying their purchase-origin information can open renewal for that same service. Extra data and renewal options must be supplied by the panel. Returning from a payment gateway does not prove payment: the app checks the server's result.

Shop error paths are designed to show understandable messages without exposing backend hostnames to the user. The shop requires backend connectivity. An offline screen does not provide offline purchasing or payment verification. VPN connection state and shop availability are separate.

## Notifications that open the right renewal

Low-data and approaching-expiry warnings appear in the Android notification shade as well as inside the app. **“Renew this service”** directs the shop to the relevant service identifier, rather than merely opening a generic purchase page. **“Read”** has an acknowledgement path. The same notice can appear as a home banner or an in-app dialog, as shown in the screenshots.

Connection notifications display session information and traffic. Version 1.1.2 adds a ping action, separate upload/download values and an optional Connect notification after disconnecting. A home-screen widget provides quick access. Shop alerts depend on a linked account, server responses, notification permission and channel settings. Background restrictions can affect delivery timing.

Desktop packages use native notifications and a tray icon, with a startup-with-system path. iOS and webOS should not be assumed to deliver background alerts exactly like Android. The iPhone Web Clip alone does not guarantee notifications while closed. Web Push depends on platform support, web-app installation, user permission and server configuration.

## Ghajar shares and phone connection sharing

**Ghajar share (GVPN)** lets the sender choose shareable configurations, set a name and note, assign time/data limits and optionally protect the file with a password. The current container is `.gsb2`. Received configurations form a separate group. The app measures local usage and has warning paths at 50%, 25% and 10% remaining. Expiry or quota exhaustion triggers the stop path and removes the received group. Presets include 10 minutes and 1 MB, with custom values and an unlimited option.

These are **local limits on the receiving device**. A local file counter does not guarantee dividing one 20 GB account into four globally enforced 5 GB allowances across devices. Shared commercial quotas need enforcement by the server or panel. File-share allowance and purchased-service allowance are distinct. Received restricted profiles are not offered as ordinary configurations for re-export.

Phone connection sharing is a separate feature: a receiving device uses the local network/hotspot and the supplied proxy settings. The current Android path supports Xray-compatible sessions and engines carried through Xray. OpenVPN, IKEv2 and unsuitable proxy-only sessions do not provide the same sharing listener. It is not a universal full-device tunnel for every client and engine.

The old standalone Safebox and share portal are not presented as active 1.1.2 interface features. Legacy source files and reports alone do not establish that their former screens remain available.

## Personalization, per-app routing and backup

First-run setup includes ready-made themes and previews of connection-button and bottom-navigation styles. Colours and layout settings allow personalization around the Home, Shop and Settings sections. Android supports Persian RTL and English.

Per-app proxy settings offer up to **10 named profiles**, selecting apps to use the VPN or bypass it. Behaviour depends on the connection engine and mode. Backup settings allow choosing which sections to export; received restricted shares are excluded from ordinary configuration backup.

The main Android manifest requests camera access for QR scanning and notification permission, while removing location and audio-recording permissions from the merged manifest. Technical network, VPN-service and update-install permissions are separate from these interactive permissions.

## Desktop packages

Windows, macOS and Linux packages have local engines. The inspected code implements local proxy, system proxy, full-device TUN and selected-app modes. TUN and selected-app modes require sing-box and administrator privileges. An engine or helper missing from a build is not an available capability. Some components, including Tor, Aether and Slipstream, are optional in the build workflow.

The desktop shell presents the PWA shop/account interface in an application window and runs connection processes locally. It is not the Android Compose interface. The complete Android protocol list should not be assumed to apply identically to every desktop installer.

## Updates and release history

Android has an update-checking path, release history, ABI-specific APK selection, SHA-256 validation and signing-certificate comparison against the installed app. Missing checksums and incompatible signatures are reported. The release's current `SHA256SUMS.txt` covers the two phone APKs; it must not be described as a checksum manifest for every desktop installer.

See [1.1.2 notes](docs/release-notes/v1.1.2.md) for changes and [all releases](https://github.com/meysam82003/Ghajarvpn-/releases) for older packages. Updating an existing installation requires a compatible package name and signing certificate.

## Building Android and repository layout

The current Android workflow uses JDK 17, Android SDK 36, build-tools 36.0.0, NDK `28.2.13676358` and native build prerequisites:

```bash
./gradlew :app:assembleDebug
```

| Path | Contents |
|---|---|
| `app/` | Native Android interface, connection management, account and shop |
| `browser/` | Embedded browser and related tools |
| `openvpn/`, `strongswan/` | OpenVPN and IKEv2 modules |
| `native/`, `third_party/` | Native engine source/provenance and licensing |
| `backend/Faoxima-1.0.0/` | Panel, API, bot and mini app |
| `docs/` | Technical reports, release notes and README evidence |
| `assets/` | Branding, new poster and real screenshots |

Desktop, iPhone and webOS source is on the separately inspected branch, linked at a fixed commit in the [evidence record](docs/README_EVIDENCE.md). This documentation update does not merge that implementation or build a new app release. Release signing keys belong outside Git or in repository Secrets.

## Licensing and contact

Read [LICENSE](LICENSE), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [core licences](docs/THIRD_PARTY_CORE_LICENSES.md). Upstream modules, including OpenVPN, retain their own terms; one badge cannot replace their individual notices.

[Ghajar channel](https://t.me/Ghajarvpn) · [Bot and mini app](https://t.me/Ghajar_vpnbot) · [Report an issue](https://github.com/meysam82003/Ghajarvpn-/issues)

<p align="center">
  <img src="docs/images/ghajarvpn-logo.png" width="180" alt="Ghajarvpn">
</p>

# Ghajarvpn · قاجار وی پی ان

Native Android VPN client branded for Ghajarvpn. The default visual system is royal navy, emerald, ice blue and gold; purple is not part of the default palette.

[فارسی](README-fa.md) · [Telegram channel](https://t.me/Ghajarvpn) · [Telegram bot](https://t.me/Ghajar_vpnbot)

## Android baseline

- Android 8.0 (API 26) through current Android releases — required by the
  committed engine AAR (`app/libs/ca.psiphon.aar`); see `SOURCE-POLICY.md`
- Native Kotlin/Compose interface with RTL Persian support
- VLESS, VMess, Trojan, Shadowsocks, SOCKS, HTTP, Hysteria2, WireGuard and IKEv2
- Integrated OpenVPN engine with `.ovpn` import, embedded auth support, credential prompt and pre-connect ping
- Public Happ/Xray-compatible deep-link and subscription import
- Native dynamic store backed by the existing Ghajarvpn Mini App panel
- Embedded HTTPS checkout without exposing a browser address bar
- Automatic import of delivered subscriptions/configurations
- Live Mini App boot poster with a bundled Ghajarvpn royal offline fallback
- General, personal, floating, quota and expiry alerts in-app and in Android notifications
- Connection notification with Ghajar avatar, ping and disconnect actions

## Source layout

- `app/` — Ghajarvpn Android application and native store
- `openvpn/` — upstream `ics-openvpn` core integrated as a library
- `strongswan/` — IKEv2 engine
- `browser/` — embedded browser module
- `native/Aether`, `native/Psiphon` — upstream native engine sources
- `backend/Faoxima-1.0.0` — Faoxima panel, bot and mini-app backend source
- `docs/` — brand assets, architecture and delivery roadmap
- `SOURCE-POLICY.md` — provenance, layout mapping, and documented deviations
  from the original release commit

This repository is self-contained: `main` is built directly from this
checkout. No step clones an upstream fork, applies a patch series, or
overlays sources from elsewhere.

## Build

Requirements: JDK 17, Android SDK platform 36 / build-tools 36.0.0, NDK
27.3.13750724, and SWIG/ninja-build — see `.github/workflows/android.yml`
for the exact, currently-used toolchain.

```bash
./gradlew :app:assembleDebug
```

Release signing is read from CI secrets or a local untracked `keystore.properties`. Never commit the signing key.

## Branding and backend safety

All public labels are sanitized through `BrandConfig`; legacy engine identifiers remain internal only where protocol compatibility requires them. Checkout allows HTTPS in the embedded view, blocks insecure HTTP/file navigation, cancels SSL errors and never falls back to an external browser.

## License

This derivative keeps the upstream GPL licensing. OpenVPN for Android is included under its GPLv2 terms and additional conditions; see `openvpn/doc/LICENSE.txt`.

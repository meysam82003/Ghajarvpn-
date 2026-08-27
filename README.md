<p align="center">
  <img src="docs/images/ghajarvpn-logo.png" width="180" alt="Ghajarvpn">
</p>

# Ghajarvpn · قاجار وی پی ان

Native Android VPN client branded for Ghajarvpn. The default visual system is royal navy, emerald, ice blue and gold; purple is not part of the default palette.

[فارسی](README-fa.md) · [Telegram channel](https://t.me/Ghajarvpn) · [Telegram bot](https://t.me/Ghajar_vpnbot)

## Android baseline

- Current demo: Android 8.0+ (API 26); Android 7 remains a target, not a verified build with the bundled core
- Native Kotlin/Compose interface with RTL Persian support
- VLESS, VMess, Trojan, Shadowsocks, SOCKS, HTTP, Hysteria2, WireGuard and IKEv2
- Integrated OpenVPN engine with `.ovpn` import, embedded auth support, credential prompt and pre-connect ping
- Public Happ/Xray-compatible deep-link and subscription import
- Native dynamic store backed by the existing Ghajarvpn Mini App panel
- Embedded HTTPS checkout without exposing a browser address bar
- Automatic import of delivered subscriptions/configurations
- Full, uncropped Ghajar royal welcome posters with a native animated transition
- General, personal, floating, quota and expiry alerts in-app and in Android notifications
- Connection notification with Ghajar avatar, ping and disconnect actions

## Source layout

- `app/` — Ghajarvpn Android application and native store
- `openvpn/` — upstream `ics-openvpn` core integrated as a library
- `strongswan/` — IKEv2 engine
- `docs/` — brand assets, architecture and delivery roadmap

## Build

Requirements: JDK 17, Android SDK 36.1, NDK 28.2.13676358, CMake 3.22.1 and SWIG.

```bash
bash scripts/bootstrap-from-upstream.sh
bash .ghajarvpn-src/scripts/fetch-openvpn-native.sh .
cd .ghajarvpn-src
./gradlew --no-daemon -Pghajar.demo=true :app:testDebugUnitTest :app:assembleDebug
```

Release signing is read from CI secrets or a local untracked `keystore.properties`. Never commit the signing key.

Automatic CI APKs use ephemeral test keys and cannot be assumed to update earlier
installations. The optional main-only signed demo workflow uses a separate private
demo key from GitHub Secrets. See [3.0.2 build and login notes](docs/BUILD-3.0.2.md).

The [3.0.3 login follow-up](docs/BUILD-3.0.3.md) adds clear network/gate feedback,
original-expiry countdowns and lifecycle-aware retry without accepting null tokens.

When the repository is distributed as a compact patch series, run
`./scripts/bootstrap-from-upstream.sh` first. See
[`docs/GITHUB_BOOTSTRAP_FA.md`](docs/GITHUB_BOOTSTRAP_FA.md).

## Branding and backend safety

All public labels are sanitized through `BrandConfig`; legacy engine identifiers remain internal only where protocol compatibility requires them. Checkout allows HTTPS in the embedded view, blocks insecure HTTP/file navigation, cancels SSL errors and never falls back to an external browser.

## License

This derivative keeps the upstream GPL licensing. OpenVPN for Android is included under its GPLv2 terms and additional conditions; see `openvpn/doc/LICENSE.txt`.

# Source basis

Baseline: phase4 commit 1c5ea4a69e930a9981c60e2b5536b87035ab0d02. Existing docs/audit-1.1.1 and docs/plugin-api-v1 decisions remain authoritative; audit was not restarted.

Local evidence inspected:
- ConfigBuilder.build: prior shareAuthed socks binding, unauthenticated http-share-in, route precedence. Dedicated loopback inbound and first routing rule added.
- GozarVpnService.startTunnel: zeptun path does not start Xray, so protocol-name guesses in old sharing UI were false. sessionReady only after actual supported core start.
- VpnState and GozarVpnService teardown/switch: synchronous relay invalidation before stopping native engines.
- ConfigParser/ConfigShare: existing URI import/export; new IPv6 bracket fix, original WireGuard preservation and Ghajar package importer.
- GhajarOpenVpnBridge/VpnProfile: original text was absent; getConfigFile is runtime-specific. Added nullable original text with existing serialVersionUID and no engine path replacement.
- PluginManager/PluginCatalog/CapabilityRegistry/ProtocolForms: metadata state retained; no production publishers/releases fabricated.

Primary reference pages consulted for help and export semantics (2026-10-01; not a claim of client acceptance tests):
- https://developer.apple.com/documentation/devicemanagement/vpn/ikev2-data.dictionary
- https://github.com/apple/device-management/blob/release/mdm/profiles/com.apple.vpn.managed.yaml
- https://support.apple.com/guide/deployment/dep4ce9487d/web
- https://www.wireguard.com/quickstart/
- https://www.wireguard.com/install/
- https://openvpn.net/connect-docs/import-profile.html
- https://openvpn.net/connect-docs/help-transferring-ovpn-ios.html
- https://hiddify.com/app/
- https://github.com/hiddify/hiddify-app/wiki/URL-Scheme
- https://github.com/enfein/mieru
- https://github.com/juicity/juicity/blob/main/README.md

No upstream executable, secret, signing identity, closed-source module or Xray prerelease patch was imported.


Follow-up at baseline 4cd7bd2, using bundled source only:
- `openvpn/src/main/cpp/openvpn/src/openvpn/options_parse.c::parse_line`: quoted option names, comment boundaries and allowed escapes.
- `openvpn/src/main/cpp/openvpn/src/openvpn/options.c::add_option`: `setenv opt` is interpreted as an optional directive; TLS hook/provider/file options must not bypass the portable export gate.
- `openvpn/src/main/java/de/blinkt/openvpn/core/ConfigParser.java`: quoted/inline syntax and profile conversion are separate from the original portable file. Runtime parser and binary were not replaced.
- `AuthenticatedRelay.start/serve` and `PhoneSharing.refresh`: accept-loop cleanup, explicit proxy errors, real listener liveness and latched listener-failure state. Socket fixture tests prove local failure behavior only, not Android hotspot/VPN exit acceptance.

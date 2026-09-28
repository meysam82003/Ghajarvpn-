# Protocol matrix

> وضعیت به‌روز مرحلهٔ سوم (SSTP، SoftEther، nDPI، ایمپورت Clash/sing-box، فرم‌های هر پروتکل، تصمیم PPTP/L2TP): [V3_REPORT_FA.md](V3_REPORT_FA.md)

One row per protocol. "Import" = link/file import exists; "Connect" = a
connect path exists; "Test" = the real-delay test runs on the engine that
carries it (`engine/EngineTester.kt`). Statuses as in ENGINE_MATRIX.md.

| Protocol | Import | Connect engine | Test | Share link | Status |
|---|---|---|---|---|---|
| VLESS (+REALITY, XHTTP, WS, gRPC, HTTPUpgrade, KCP) | vless:// , JSON | Xray | Xray measureDelay | yes | Shipped |
| VMess | vmess:// | Xray | Xray | yes | Shipped |
| Trojan | trojan:// | Xray | Xray | yes | Shipped |
| Shadowsocks (+obfs, v2ray-plugin) | ss:// | Xray | Xray | yes | Shipped |
| SOCKS5 / HTTP | socks:// socks5:// http:// | Xray | Xray | – | Shipped |
| Hysteria 2 | hysteria2:// hy2:// | Xray | Xray | yes | Shipped |
| WireGuard | wireguard:// wg:// , .conf | Xray | Xray | – | Shipped |
| TUIC v5 | tuic:// | sing-box | temp sing-box + HTTPS | yes | Experimental · Not device verified |
| Hysteria (v1) | hysteria:// | sing-box | temp sing-box + HTTPS | yes | Experimental · Not device verified |
| AnyTLS | anytls:// | sing-box | temp sing-box + HTTPS | yes | Experimental · Not device verified |
| SSH | ssh:// | sing-box | temp sing-box + HTTPS | yes (no private key) | Experimental · Not device verified |
| Snell v4 / v6 | (no common link) | sing-box | temp sing-box + HTTPS | – | Implemented (no import path) |
| OpenConnect (AnyConnect, GP, Fortinet, F5, Pulse, NC) | openconnect:// anyconnect:// | sing-box endpoint | temp sing-box + HTTPS | yes | Experimental · Not device verified |
| DNSTT (DNS tunnel, UDP/DoT/DoH; SOCKS5 or SSH upstream) | dnstt:// , DNS Lab | dnstt → sing-box | temp dnstt + sing-box + HTTPS | yes | Experimental · Not device verified |
| IKEv2 / IPsec | ikev2:// | strongSwan | IKE probe | – | Shipped |
| OpenVPN | .ovpn | ics-openvpn | – | – | Shipped |
| Psiphon | built-in | Psiphon | – | – | Shipped |
| Aether (MASQUE/WARP) | built-in | Aether | – | – | Shipped |
| Tor | built-in | Tor | – | – | Shipped |
| ShadowsocksR | ssr:// recognised | – | – | – | Blocked (removed in pinned sing-box) |
| NaiveProxy, Mieru, Juicity, ShadowTLS, AmneziaWG, SSTP, SoftEther, VayDNS, MasterDNS, CottenDNS | – | – | – | – | Planned (see ENGINE_MATRIX.md) |
| PPTP, L2TP | – | – | – | – | Legacy-disabled |

## Link formats defined by Ghajar

There is no common share link for these; the app reads and writes exactly this:

- `openconnect://USER:PASS@HOST[:PORT]?flavor=anyconnect|gp|fortinet|f5|pulse|nc&sni=&pin=&insecure=1#NAME`
- `dnstt://[USER[:PASS]@]DOMAIN?pubkey=HEX64&transport=udp|dot|doh&resolver=HOST[:PORT]&doh=URL&upstream=socks|ssh#NAME`
- `ssh://USER:PASS@HOST[:PORT]?hostkey=...&pk=BASE64URL(PEM)#NAME` (the private key is never written into a share link)

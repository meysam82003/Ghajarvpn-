# Engine matrix

> وضعیت به‌روز مرحلهٔ سوم (SSTP، SoftEther، nDPI، ایمپورت Clash/sing-box، فرم‌های هر پروتکل، تصمیم PPTP/L2TP): [V3_REPORT_FA.md](V3_REPORT_FA.md)

Status words: **Shipped** (in releases, used by users) · **Implemented** (code
complete, tested off-device) · **Experimental** (wired, behind honest
availability, not device verified) · **Import-only** · **Planned** ·
**Blocked** · **Legacy-disabled** · **Not device verified**.

Every engine reports `Missing` in `CoreManager` when its binary is not in the
APK; nothing claims to connect without it.

| Engine | Source / pin | Licence | How it runs | Owns tun? | Status |
|---|---|---|---|---|---|
| Xray | XTLS/Xray-core v1.260327.0 in `app/libs/ca.psiphon.aar` (gomobile `gozarcore`) | MPL-2.0 | in-process | yes | Shipped |
| Psiphon | psiphon-tunnel-core, same AAR | GPL-3.0 | in-process, local SOCKS5 | no (zeptun) | Shipped |
| sing-box | SagerNet/sing-box 132b38e (v1.15.0-alpha.9), `scripts/build-singbox.sh` | GPL-3.0-or-later | subprocess `libsingbox.so`, local SOCKS5 | no (zeptun) | Experimental · Not device verified |
| dnstt (DNS tunnel) | dnstt v1.20260501.0 / 0c5c52a, `scripts/build-dnstt.sh` | CC0-1.0 | subprocess `libdnstt.so` → sing-box | no (zeptun) | Experimental · Not device verified |
| zeptun tun2socks | Noisemux/zeptun 2798fc0 (CI) | MIT | JNI | yes | Shipped (proxy-only engines) |
| OpenVPN | ics-openvpn module `:openvpn` | GPL-2.0-only (see licence note) | own VpnService | yes | Shipped |
| IKEv2/IPsec | strongSwan module `:strongswan` | GPL-2.0-or-later | own VpnService | yes | Shipped |
| Tor | bundled executable | BSD-3 | subprocess, SOCKS5 | no | Shipped |
| Aether (MASQUE/WARP) | CluvexStudio/Aether 21e7150 | AGPL-3.0 | subprocess, SOCKS5 | no (zeptun) | Shipped (Experimental label) |

## Protocols per engine (what actually routes where)

`engine/CoreManager.kt → EngineRouting.engineFor(config)` is the single rule the
launch sites, filters and tests share:

| Protocol (ProxyConfig.protocol) | Engine |
|---|---|
| vless, vmess, trojan, shadowsocks, socks, http, hysteria2, wireguard (+REALITY, XHTTP, gRPC, WS, HTTPUpgrade, KCP, finalmask) | Xray |
| tuic, hysteria (v1), anytls, ssh, snell (v4/v6), openconnect (AnyConnect, GlobalProtect, Fortinet, F5, Pulse, NC) | sing-box |
| dnstt | dnstt → sing-box |
| psiphon | Psiphon (or Aether when its options say so) |
| aether | Aether |
| tor | Tor |
| ikev2 | strongSwan |
| .ovpn profiles | OpenVPN module |

## Not integrated, with the reason

| Candidate | Status | Reason |
|---|---|---|
| ZedSecure AndroidLibXrayLite / Xray fork | Blocked | build not reproducible (local `replace` to unpublished paths); Xray is not replaced blindly |
| sing-box OpenVPN client (`with_openvpn`) | Planned | compiled into libsingbox.so; `.ovpn` → sing-box translation not written; ics-openvpn stays |
| sing-box WireGuard endpoint | Planned | Xray already carries WireGuard; switching engines needs device comparison first |
| AmneziaWG | Planned | no AWG in the pinned sing-box; needs amneziawg-go executable |
| ShadowsocksR | Blocked | the pinned sing-box registers it only as a removed stub |
| NaiveProxy | Planned | needs `with_naive_outbound` (Cronet, large); not built |
| ShadowTLS | Planned | needs a detour chain (ShadowTLS + inner SS) in the profile model |
| VayDNS / MasterDnsVPN / CottenDNS / NoizDNS / Slipstream / StormDNS | Planned | dnstt covers the family first; each is another pinned executable behind the same DnsttRunner-style contract |
| SSTP | Planned | candidate pure-Kotlin module (sstp_flutter, BSD-3) |
| SoftEther, PPTP, L2TP | Planned / Legacy-disabled | no maintained Android userspace implementation reviewed; PPTP is broken crypto |
| Mieru, Juicity, Brook, Outline (SS), Geph, Lantern | Planned | Outline access keys are Shadowsocks (already Xray); others need their own executables |
| Tor pluggable transports | Planned | lyrebird/snowflake executables |
| nDPI | Planned | debug-only diagnostics, size |

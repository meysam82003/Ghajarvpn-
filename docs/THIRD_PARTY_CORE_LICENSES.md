# Third-party VPN cores — licence audit

Ghajar VPN itself is **GPL-3.0** (`LICENSE`). Every entry below was checked by
cloning the repository at the commit listed and reading its licence file on
2026-09-26. "Linking" is how the code would reach the APK.

| Repository | Commit / branch | Licence | Linking method | Copied code? | Modified? | Distribution obligation | Risk | Recommendation |
|---|---|---|---|---|---|---|---|---|
| XTLS/Xray-core (current) | v1.260327.0 (go.mod) | MPL-2.0 | gomobile AAR `ca.psiphon.aar` (package `gozarcore`) | no | no | Ship MPL notice; offer source of MPL files | Low | Keep |
| CluvexStudio/Xray-core | `zedsecure` @ 4c61a3d | MPL-2.0 | would replace Xray inside the AAR | no | – | as above | Medium (build not reproducible, see report) | Blocked until deps are public |
| CluvexStudio/AndroidLibXrayLite | `zedsecure` @ 3b47738 | LGPL-3.0 | gomobile AAR | no | – | LGPL: allow relinking; ship source/notice | Medium | Reference only for now |
| Psiphon-Labs / CluvexStudio psiphon-tunnel-core | `shirokhorshid` @ 83aa73b | GPL-3.0 | inside the same AAR (already shipped from upstream) | no | no | GPL-3 source offer (compatible with Ghajar GPL-3) | Low | Keep upstream; fork not adopted |
| SagerNet/sing-box | 132b38e (v1.15.0-alpha.9) (scripts/build-singbox.sh) | GPL-3.0-or-later | separate executable `libsingbox.so` | no | no | GPL-3 source offer | Low | **Wired** (TUIC, Hysteria v1, AnyTLS, SSH, Snell, OpenConnect, dnstt upstream, MASQUE CONNECT-IP); Experimental |
| Noisemux/zeptun | 5620e57 (v1.1.1, CI) | MIT | JNI `libzeptun*.so`, built in CI | no | no | Keep MIT notice | Low | Keep |
| CluvexStudio/Aether | 21e7150ac2225caa01cbb96ac572b5c0cc1e1dc2 (v2.1.0), built from source by `scripts/build-aether.sh` (sha256 printed in the CI log; LICENSE copied to `third_party/aether/`) | **AGPL-3.0** | separate executable, local SOCKS5 | source vendored under `native/Aether` | – | AGPL-3: source for the Aether program must be offered to users; GPL-3 §13 permits combination; network-use clause applies to modified Aether | **High** | Keep as separate program, unmodified, with source link; no merge into app code |
| CluvexStudio/sing-openvpn | `main` @ e060dda | GPL-3.0 | would be a Go executable | no | – | GPL-3 source offer | Medium (maturity) | Not adopted; OpenVPN already shipped via ics-openvpn |
| ics-openvpn (module `:openvpn`) | vendored | **GPL-2.0 only** + OpenSSL exception (`openvpn/doc/LICENSE.txt`) | Gradle module, same process | yes (module) | yes (existing) | GPL-2 source | **High (pre-existing)** — see note | Owner decision needed |
| strongSwan (module `:strongswan`) | vendored | GPL-2.0-or-later | Gradle module | yes (module) | yes (existing) | GPL source | Low (existing) | Keep |
| pengyue-polaron/oconnect-android | `master` @ 8cc4fa6 | GPL-2.0-or-later | reference only | **no** | – | – | – | Reference only (as required) |
| OpenConnect (libopenconnect) | gitlab upstream | LGPL-2.1 | would be JNI `.so` | no | – | LGPL: dynamic link, notice, source offer; deps (GnuTLS LGPL, libxml2 MIT, zlib, stoken LGPL) | Medium | Planned (Phase 4) |
| Hidden-Node/MasterDnsVPN-AndroidClient | `main` @ 17459d9 | MIT | Go executable (cannot be a 2nd gomobile AAR) | no | – | MIT notice | Low | Planned (Phase 6) |
| WhiteDNS/CottenDns | `main` @ cdf084f | MIT | Go executable `libcottendns_client.so` | no | – | MIT notice | Low | Planned (Phase 6) |
| net2share/vaydns | `main` @ a0ff701 | CC0-1.0 | Go library/executable | no | – | none (keep attribution anyway) | Low | Planned (Phase 6) |
| dnstt (upstream, bamsoftware) | v1.20260501.0 / 0c5c52a (Go module proxy, checksum-verified; scripts/build-dnstt.sh) | CC0-1.0 | executable `libdnstt.so` | no | no | none (COPYING kept in third_party/dnstt) | Low | **Adopted**; Experimental |
| FrontierTM/Pantegnos | `main` | MIT | **ported to Kotlin** (`configtoolkit/NpvContainer.kt`) | yes (port of npvs*.go logic) | yes (subset, no whitebox keys) | MIT notice kept in the file header | Low | **Adopted** (open + passphrase only) |
| CluvexStudio/ZedPass | `main` @ d6f66bd | GPL-3.0 | reference | no | – | – | – | Reference |
| NavidShokoufeh/sstp_flutter (ZedPass SSTP backend) | 1.3.0 (pub.dev) | BSD-3-Clause | pure-Kotlin SSTP/PPP (Open-SSTP-Client lineage) | no | – | BSD notice | Low | Candidate for an `:sstp` module (Phase 8) |
| CluvexStudio/PattNG | `my-releases` @ bc211af | GPL-3.0 | reference (hev-socks5-tunnel) | no | – | – | – | Reference |
| CluvexStudio/nDPI | `dev` @ eddf7be | LGPL-3.0 | optional debug `.so` | no | – | LGPL | Medium (size) | Later, debug build only |
| CluvexStudio/MehrON | `main` @ 52abc69 | GPL-3.0 | reference | no | – | – | – | Reference |
| CluvexStudio/defyxVPN | `main` @ 80d8453 | MIT | reference | no | – | – | – | Reference |

## ⚠️ Pre-existing finding: ics-openvpn is GPL-2.0-only

`openvpn/doc/LICENSE.txt` licenses OpenVPN for Android under "the GPL license
version 2" with no "or any later version", plus an OpenSSL exception, and
states that using any part of `de.blinkt.openvpn` creates a derivative work.
It is linked into the same app as GPL-3.0 code (Ghajar itself, and
psiphon-tunnel-core inside the engine AAR). GPL-2.0-only and GPL-3.0 are not
compatible licences for a combined work.

This was already the case before this work; nothing here changed it. It is a
legal decision, so it is reported rather than "fixed". Options, in order of
effort: (1) ask the author for GPL-3-compatible terms (the licence file invites
contact); (2) move OpenVPN to a separately distributed plugin APK; (3) replace
the module with an OpenVPN implementation under compatible terms (the sing-box
`with_openvpn` build tag, GPL-3, is already compiled into `libsingbox.so`).

## By licence family

- **AGPL-3.0**: Aether. Allowed next to GPL-3 code (GPL-3 §13), but the AGPL
  terms stay attached to Aether: users must be offered its corresponding
  source, and any *modified* Aether used over a network must offer source to
  its users. Ghajar ships Aether unmodified as a separate executable; keep it
  that way.
- **GPL-3.0 / GPL-2.0-or-later**: sing-box, psiphon-tunnel-core, strongSwan,
  sing-openvpn, ZedPass, PattNG, MehrON, OConnect (reference only). Compatible
  with Ghajar's GPL-3. GPL-2.0-*only* code would not be; none is linked.
- **LGPL (2.1 / 3.0)**: libopenconnect, AndroidLibXrayLite, nDPI. Dynamic
  linking plus notices and source offer.
- **MPL-2.0**: Xray-core. File-level copyleft; notices kept.
- **MIT / BSD-3**: zeptun, Pantegnos, MasterDnsVPN, CottenDNS, defyxVPN,
  sstp_flutter. Keep copyright notices.
- **CC0**: vaydns, dnstt. No obligation.

No copyright or licence notice has been removed from any vendored project.

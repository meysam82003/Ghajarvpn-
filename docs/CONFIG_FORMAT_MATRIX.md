# Config format matrix

All file imports go through one path: `configtoolkit/ImportRouter` →
`FormatDetector` → `DecoderRegistry`. The result is one of
**Imported / NeedsPasskey / WrongPasskey / Locked / Unsupported / Invalid**,
and only **NeedsPasskey** opens a password dialog (fix for the NPVS false
password prompt; tests in `ImportRouterTest`).

| Format | Detected by | Result | Password dialog? | Status |
|---|---|---|---|---|
| Share links (vless/vmess/trojan/ss/socks/http/hy2/hysteria/tuic/anytls/ssh/openconnect/dnstt/wg/ikev2) | scheme | Imported | no | Shipped / Experimental per protocol |
| Base64 / plain subscription | content | Imported | no | Shipped |
| Xray / v2ray JSON | `outbounds` | Imported | no | Shipped |
| WireGuard .conf | `[Interface]` + `[Peer]` | Imported | no | Shipped |
| OpenVPN .ovpn | content | Imported (OpenVPN module) | no | Shipped |
| Ghajar own container (GRT1) | magic `GRT1` | Imported / password when the file was saved with one | only for protected GRT1 | Shipped |
| NPVS, NPVO1 (open) | header | Imported | no | Implemented |
| NPVS passphrase-protected (method 1) | header | NeedsPasskey → Imported / WrongPasskey | **yes** | Implemented |
| NPVS app-key (method 2) / recipient (method 0) | header | Locked (reason shown) | **no** | Implemented |
| NPVT readable copy | JSON | Imported | no | Implemented |
| NPVT1 / NPVTSUB1 vendor-locked | magic | Locked | **no** | Import-only (refused honestly) |
| Happ `happ://` plain | scheme | Imported | no | Implemented |
| Happ `happ://crypt*` | scheme | Locked (vendor) | **no** | Blocked by design |
| NetMod .nm, SlipNet .slip, EHI, HAT, Dark | extension | readable copies only; locked files → Locked | **no** | Import-only |
| Anything else | – | Unsupported | **no** | – |

Rules this app keeps: no key is extracted from another app's APK, no vendor
key is hard-coded, and author/device/recipient locks are not bypassed; a file
sealed that way is reported as Locked with the reason.

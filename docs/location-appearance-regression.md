# Native appearance and exit-IP verification

The public API contracts are verified against [ipify](https://www.ipify.org/),
[ipwhois](https://ipwhois.io/documentation), and [FreeIPAPI](https://freeipapi.com/).
The old api4.ipify.org and freeipapi.com/api endpoints are no longer used.

- One lifecycle observer owns all home styles. Every reconnect clears old IP/country data.
- A response belongs to its connection ID and start time. Old or cancelled responses cannot update a new session.
- IP probes run through the local core SOCKS proxy for core connections; native IKE uses the VPN route.
- Coordinates and country codes must be valid and the geolocation response must match the observed numeric IP.
- Failed lookups show pending/unavailable text and retry; they never substitute Tehran or infer a country from a server name.
- Coordinates describe the provider's approximate IP location, not GPS. An exit-IP display is not a leak audit or proof that every app uses the tunnel.
- No full IP address or coordinates are written to debug logs by this feature. Public lookup providers receive the address being looked up.

Color presets, a hue ring with saturation/value square, HEX input, reset, and royal/shield home choices are native controls.
Custom accent foregrounds are adjusted to at least 4.5:1 against the app background. Payment data stays native, selectable by copy actions, and is not printed in the artwork.

## Android 14 regression (result pending)

The separate CI workflow builds x86_64 only for the disposable emulator; shipped APKs remain ARM32/ARM64.
It clicks the actual connect/disconnect button twice, passes test HTTP data through the bundled core to a local SOCKS fixture,
changes home style, and recreates the Activity. It records screenshots and process exit/logcat evidence.
No real account, purchase, payment, receipt upload, or public VPN service is used in this test.
This test does not replace verification of a live tunnel or payment on the user's phone.

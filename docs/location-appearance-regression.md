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

## Android 14 regression

The separate CI workflow builds x86_64 only for the disposable emulator; shipped APKs remain ARM32/ARM64.
It clicks the actual connect/disconnect button twice, passes test HTTP data through the bundled core to a local SOCKS fixture,
changes home style, and recreates the Activity. It records screenshots and process exit/logcat evidence.
No real account, purchase, payment, receipt upload, or public VPN service is used in this test.
This test does not replace verification of a live tunnel or payment on the user's phone.

The first emulator run [33146250926](https://github.com/meysam82003/Ghajarvpn-/actions/runs/33146250926)
completed both instrumentation tests: 2 tests, 0 failures, 0 errors, 0 skipped. The test exchanged fixture HTTP data through the real native engine.
The artifact SHA-256 was `9609ece656cea9d09b134711581f179619fa867b31b231ce3b4a39812839bdb7`.
Its screenshots were deleted during Gradle app uninstall; the follow-up stores public fixture screenshots in the disposable emulator Download folder.
The broader card/receipt-control, decoded QR, welcome, story and color-control checks are added in the next checkpoint; their result must be checked separately.

# Ghajarvpn 3.0.4 — integrated review

## Scope and source

The API contract is the user-provided `Ghajar_vpnbot_-3-1.zip`. Its private files are never included in this repository.
The inherited `net.gozar` namespace is Android engine code, not another bot.
This branch collects all changes before delivering one demo; a green compile alone is not phone/payment verification.

## Implemented

- Guard foreground VPN startup, native initialization and duplicate starts; remove the delayed process kill that could terminate a reconnect.
- Verified exit-IP lookup per connection session, no stale country or invented Tehran fallback. Globe/dots rotate to validated coordinates; the IP card shows that country flag. IP location is approximate, not GPS or a leak audit.
- 33 bundled JPEG welcome posters, one on every launch including first launch; no carousel or remote gallery. Persistent shuffle cycles avoid repeats until all posters have been shown, including the cycle boundary. Palette reference is excluded. Existing-image optimization saved about 5.7 MB before adding the new JPEGs.
- Qajar gold launcher, card frame with small characters, and transparent royal character home option. Native color presets, HEX, color wheel, reset, and globe/dots/royal/shield home styles.
- Compact catalog cards, product details on tap and trial before products.
- Activity-owned checkout with encrypted invoice recovery. Coroutine cancellation is not a red purchase error.
- Exact copyable card, cardholder, toman and rial amounts; receipt picker/preview/upload and pending-approval feedback.
- Wallet balance and independent top-up, plus explicit confirmation before redeeming a story/manual gift code.
- BluPal `blupal.net` public-token link handling; authenticated payment status on return. Neither a redirect nor a submitted receipt proves payment.
- Separate wallet-only credit, service preparation and confirmed service delivery. Native QR contains the actual selected subscription/configuration.
- Immediate subscription fetch when importing a trial or purchased service. Wait for persisted configurations before import/foreground refresh; keep a newer delivery refresh and user edits intact.
- Preserve unrelated pending invoices when importing trial/owned services.
- Glass notices, compact server card, theme-aware system bars and safe checkout insets.
- Native stories using the exact bot list/view/reaction APIs: image/video, pause/sound, progress, gift/discount codes, native shop/wallet routes, explicit external links and attachments. Media is HTTPS, bounded, cached and isolated from account credentials.

## Verification recorded

- Welcome-only Android CI run [33145440173](https://github.com/meysam82003/Ghajarvpn-/actions/runs/33145440173): success.
- Appearance/location Android CI run [33146250906](https://github.com/meysam82003/Ghajarvpn-/actions/runs/33146250906): success, ARM32/ARM64 builds and automated checks.
- Android 14 emulator run [33146250926](https://github.com/meysam82003/Ghajarvpn-/actions/runs/33146250926): 2 instrumentation tests, 0 failures/errors/skips. Connect, disconnect, reconnect, Activity recreation and real bundled-core SOCKS traffic to a local fixture passed.
- The story checkpoint ARM build [33168409525](https://github.com/meysam82003/Ghajarvpn-/actions/runs/33168409525) passed. Its Android 14 UI run [33168409557](https://github.com/meysam82003/Ghajarvpn-/actions/runs/33168409557) also passed: 10 tests, 0 failures/errors/skips. The downloaded unit reports contain 67 passed tests, plus all 8 native architecture checks.
- Follow-up native tests cover actual server-final amount parsing, globe projection/flag changes and clearing the prior country during reconnect. Receipt controls preserve a selection on picker cancellation and prevent resubmission while approval is pending.
- Source snapshots must match the complete reconstructed build. Binary brand assets are installed from `branding/`, not embedded in incremental patches.

## Limits and remaining release checks

- Inspect the next integrated build, complete test XML and actual emulator screenshots before marking this checkpoint verified.
- The emulator uses a disposable local fixture, not a paid account/public VPN, real receipt upload or gateway approval. The user's phone and live payment path still require a controlled user check.
- Native IKE uses its VPN route for IP probes; imported OpenVPN lifecycle integration has not been verified by the core fixture test.
- CI demo keys are ephemeral until a private stable key is configured. Do not promise an in-place upgrade over previous demo APKs.
- No production credentials, actual payment or bot source ZIP are published. Keep this PR in review until the integrated checks finish.

## APK inspection

The appearance checkpoint ARM64 APK is 84,357,573 bytes; all 33 welcome JPEGs total 8,963,166 bytes and match the reviewed source bytes. Native engines account for 33,399,951 compressed bytes; DEX accounts for 24,068,280. Image compression alone cannot remove that engine/code payload.

Direct ELF inspection found an inherited ARM64 Aether executable under `armeabi-v7a`. It cannot execute on a 32-bit ARM device. The follow-up excludes that misplaced executable from ARM32 and avoids automatically selecting an unavailable engine; ARM64 Aether is retained unchanged. The shipped-APK verifier now rejects an ABI mismatch and verifies every welcome image hash/count. Core/OpenVPN/IKE native libraries are not removed. The verifier passed the existing ARM64 APK (20 native libraries), and caught the known mismatch in the existing ARM32 APK before this correction.

## Native screenshot review

The card details/large-text receipt controls, decoded delivery QR, accent controls, single welcome poster and story actions were reviewed from emulator screenshots. Early connection/recreation captures occurred before Compose animations completed; the follow-up waits for the actual visible button label and rendered Activity content before taking evidence. This avoids reporting a transitional or blank capture as a finished screen. The follow-up also matches status/navigation/decor colors to light, dark or AMOLED mode, adds button wrapping for long Persian states, and guards the Activity's native logger initialization in addition to the VPN service guard.

The first runtime artifact SHA-256 for this integrated checkpoint is `3169ccad1d1d2dd84368860beb1a7f515bc9f9995f0ed4d853d94e49459def59`; unit report archive is `57f32018849f0309f51d448568d201782cbf3f953e266d95b0cdf0957630764b`.

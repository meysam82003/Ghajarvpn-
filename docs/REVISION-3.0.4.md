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
- Added instrumentation checks for the exact card copy/receipt controls with large Persian text, decoding the rendered delivery QR, one-poster welcome, story reaction/gift navigation and custom color controls/contrast. Integrated result pending.
- Source snapshots must match the complete reconstructed build. Binary brand assets are installed from `branding/`, not embedded in incremental patches.

## Limits and remaining release checks

- Inspect the next integrated build, complete test XML and actual emulator screenshots before marking this checkpoint verified.
- The emulator uses a disposable local fixture, not a paid account/public VPN, real receipt upload or gateway approval. The user's phone and live payment path still require a controlled user check.
- Native IKE uses its VPN route for IP probes; imported OpenVPN lifecycle integration has not been verified by the core fixture test.
- CI demo keys are ephemeral until a private stable key is configured. Do not promise an in-place upgrade over previous demo APKs.
- No production credentials, actual payment or bot source ZIP are published. Keep this PR in review until the integrated checks finish.

# Ghajarvpn 3.0.4 — review checkpoint

## Source of truth

The bot/API contract is the user-provided Ghajar_vpnbot_-3-1.zip.
The inherited net.gozar namespace is Android engine code, not an alternate bot.
Never publish that ZIP or private bot configuration in this repository.

## Implemented in this checkpoint

- Activity-owned checkout; encrypted invoice recovery across process recreation.
- Do not catch coroutine cancellation as a purchase failure.
- Separate catalog metadata/effect keys so loading no longer cancels itself.
- Compact product cards; all description appears only after opening a product.
- Free trial before products.
- Native receipt picker/preview/upload; exact copyable cardholder, card, toman and rial amounts.
- Wallet balance and independent wallet top-up using payment_methods/payment_init.
- Follow the exact public-token BluPal URL; allow blupal.net; safe user-initiated popup navigation.
- Return from payment always triggers authenticated payment_status. A redirect is never payment proof.
- Respect wallet_credited_only rather than pretending the service was delivered.
- Native delivery QR and immediate subscription fetch, reusing an existing subscription by URL.
- First-run onboarding retained. Returning launches show one full, nonrepeating poster with no onboarding footer.
- 10 new rule/cancellation/URL regression tests.

## Still open — do not label complete

- Device-reproduced Connect crash and native crash log. Local lifecycle edits were interrupted by workspace outage and are not part of this checkpoint.
- New generated launcher/card artwork, with small Qajar characters at the frame, not yet integrated.
- Entry-wide subscription refresh (this checkpoint refreshes on delivery).
- Glass notices, home server card, wider theme polish.
- Mini-app stories: exact ZIP contract still needs inspection after file access is restored.
- Real-device payment, receipt approval, VPN and RTL/large-font visual tests.
- Stable signing key is not configured; CI demo keys can differ between runs.

## References

BluPal API: https://blupal.net/documentation (payment_link and final_amount are server-owned).
Android VPN lifecycle: https://developer.android.com/develop/connectivity/vpn
WebView popup callback: https://developer.android.com/reference/android/webkit/WebChromeClient

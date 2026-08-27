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
- Subscription refresh whenever the app enters the foreground, including entry after payment.
- Glass-like notices with a single highlight pass and physical left-to-right swipe dismissal.
- Native, compact server selection card with static complete names and true connection state.
- Guard foreground VPN startup, native initialization and duplicate starts; remove the delayed process kill that could terminate a reconnect.
- Keep unrelated pending invoices while importing a trial/owned service.
- Exact invoice amount in the summary; no success state for unparseable configurations.

## Still open — do not label complete

- Device-reproduced Connect crash and native crash log: the defensive startup changes are not proof that the user's specific native crash is resolved.
- New generated launcher/card artwork, with small Qajar characters at the frame, not yet integrated.
- Wider theme/color polish and full RTL/large-font rendering review.
- Mini-app stories: exact ZIP contract still needs inspection after file access is restored.
- Real-device payment, receipt approval, VPN and RTL/large-font visual tests.
- Stable signing key is not configured; CI demo keys can differ between runs.

## Verification

- Checkpoint bf528753: clean patch reconstruction, 23 snapshot matches, native architecture checks, unit-test task and APK build passed in Actions run 33101220298.
- Follow-up lifecycle, notice, server-card and invoice changes require the next clean CI build.
- No real payment was performed. No production credentials or bot ZIP were uploaded. This PR remains a draft, not a final release.

## References

BluPal API: https://blupal.net/documentation (payment_link and final_amount are server-owned).
Android VPN lifecycle: https://developer.android.com/develop/connectivity/vpn
WebView popup callback: https://developer.android.com/reference/android/webkit/WebChromeClient

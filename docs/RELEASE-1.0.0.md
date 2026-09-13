# 1.0.0 release candidate

Base: a8ff7948c179a9991f45ae43cf7d6c2578ed0099 (includes the successful Android CI run 34657646907 at f805467).
Version code: 30006. Android API floor remains the existing Android 8+ CI build.

## Changes
- Restore the missing web-link endpoint and Telegram link command against the supplied Faoxima 1.0.0 source, without copying deployment credentials.
- Match Faoxima notification_info/notification_recent/notification_dismiss and test-account API contracts.
- Add native ticket list, creation, replies and closing; the full in-app Faoxima panel provides attachments and the remaining account actions after the server patch is installed.
- Limit automatic free imports to @Ghajarvpn, read direct links and subscription URLs in message bodies, name imported profiles Ghajarvpn followed by their number.
- Pass Psiphon settings across the VPN process boundary, wait for an established tunnel and actual SOCKS port, bind that port into the Xray outbound, and persist CDN IP/SNI settings.
- Add Android Oblivion settings for Aether/MASQUE/WireGuard/gool and Aether-to-Psiphon chaining; pass network, DNS, proxy, split-tunnel, fragmentation, routing, performance and Zero Trust settings to their engines.
- Add durable wallet/gateway funding and one-time refunds when provisioning fails; recover lost responses before refunding and reconcile uncertain purchases in the existing crypto cron.
- Cancel pending service starts and serialize native teardown; remove delayed process killing that could kill a retry; prevent connect taps during teardown.

## Required server action
The confirmed deployment is https://httpuser87890.ir/Faoxima/Ghajarvpn/.
The main mini-app responds, but api/weblink.php returned 404 during this investigation.
Apply server/faoxima-1.0.0 with its checked installer to that exact deployed source.
The installer refuses unexpected existing files and backs up replaced files.
It never changes config.php, bot tokens, payment credentials or the database configuration.

```
python3 install.py /path/to/Faoxima/Ghajarvpn
python3 install.py /path/to/Faoxima/Ghajarvpn --apply
```

## Release gates
- Successful corrected Android CI, unit tests, APK signature and ABI packaging checks.
- Install the backend compatibility patch; verify login with a real Telegram account, notification delivery, ticket exchange and shop access.
- Device test of Psiphon and repeated connect/cancel/disconnect on both supported ABIs.
- Confirm release signing: inherited CI uses an ephemeral debug key for release APKs unless owner signing is configured. Such APKs cannot be promised as updates to an earlier installation.

The supplied Oblivion source has no ServerEntrySignaturePublicKey: Conduit is unavailable in that source. It must not be represented as a working connection mode. Aether-to-Psiphon chaining is implemented; full device/network verification is not completed by the changes above.
No production release is claimed while these gates remain open.

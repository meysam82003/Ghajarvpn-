# Ghajar VPN — Feature Inventory (baseline audit)

Audited directly from source on branch `claude/website-design-project-files-lahgbm`
(forked from `origin/main`). This is a from-source inventory, not a memory/assumption
list, per the HARDLINE brief's requirement to audit before redesigning.

Format per feature: entry point → screen/composable → backend → storage/permissions → status.

## Navigation shell (current, pre-redesign)

- `MainActivity.kt` hosts a `HorizontalPager` with **5 pages**, not 3:
  `PAGE_SHOP=0, PAGE_SSH=1, PAGE_HOME=2, PAGE_DEBUG=3, PAGE_SETTINGS=4`
  (`MainActivity.kt:483-488`), rendered through a 5-item bottom nav using
  `ic_royal_shop/tunnel/home/tools/settings` (`MainActivity.kt:1640-1690`).
- **This violates the target design (doc section 7):** the final nav must be
  exactly 3 tabs (Home, Store, Settings) with SSH and Debugger moved inside
  Settings. Restructuring the pager is a required, separate migration step —
  not done in this pass, called out below as next work so it isn't rushed
  without a way to compile-check the result.

## Compile-blocking gaps found during audit (fixed unless noted)

| Symbol | Referenced at | Status |
|---|---|---|
| `R.drawable.ghajar_wordmark` | Home header, checkout card, About | **Fixed** — official logo asset added |
| `R.drawable.ghajar_welcome_*` (33 posters) | `GhajarVisuals.kt` welcome screen | **Fixed** — generated on-brand poster set |
| `R.drawable.ic_royal_home/shop/tunnel/tools/settings` | bottom nav | **Fixed** — vector icons added |
| `R.drawable.signal/tor/cloudflare/windscribe/iran` | Settings hub cards, MainActivity | **Fixed** — vector/raster icons added |
| `R.drawable.ghajar_treasury` | Shop header, checkout card | **Fixed** — raster illustration added |
| `DotGlobeSection(...)` | `MainActivity.kt:2151`, gated by `store.globeStyle == "dots"` | **Fixed** — implemented in `Globe.kt` using the same location/connection state as `EarthSection`, rendered as a flat dotted-map + glow marker |
| `SshScreen(...)` | `MainActivity.kt:1712` (PAGE_SSH) | **NOT FIXED — BLOCKED.** No composable named `SshScreen` exists anywhere in the module. Backend exists (`SshShell.kt`, `sshmanager.kt`, `sshhost.kt`, `SftpBrowser.kt`) but has no UI. Building a real SSH terminal/session-manager screen is a substantial feature-build, not a visual fix — flagged for a dedicated pass rather than a rushed stand-in. |
| `CleanIpScreen()` | `MainActivity.kt:1853` | **NOT FIXED — BLOCKED.** No composable named `CleanIpScreen` exists. `Warp.kt` / `Ipintelligence.kt` appear to hold related backend logic (Cloudflare WARP / clean-IP scanning) but there is no screen wired to it. Same reasoning as SshScreen. |

No Gradle/Android SDK is available in this execution environment, so these
findings come from a full grep-based cross-reference of every
`R.drawable.*`/composable call site against its definition, not from an
actual compiler run — real compilation is `NOT VERIFIED` until built on a
machine with the Android SDK.

## Feature map (from source)

| Feature | Entry point | Screen/Composable | Backend | Storage/permissions | Status |
|---|---|---|---|---|---|
| VPN connect/disconnect | Home hero button | `ConnectionScreen` (`MainActivity.kt:1901`) | `GozarVpnService`, `VpnState`, `VpnBridge` | VPN service permission, foreground service | Working baseline |
| Server selection | Home → picker | `ConfigPickerScreen` (`MainActivity.kt:2162`) | `ConfigStore` | local store | Working |
| Ping/latency test | Server cards, connect bar | `Pinger.kt`, `SpeedTest.kt` | network | INTERNET | Working |
| V2Ray/Xray configs | Config picker, manual add | `ConfigBuilder.kt`, `ConfigParser.kt` | xray core | local | Working |
| OpenVPN | Settings → OpenVPN | `GhajarOpenVpnSettings.kt`, `GhajarOpenVpnBridge.kt` | native OpenVPN bridge | local profiles | Working |
| SSH | PAGE_SSH (own tab, should move to Settings) | **missing `SshScreen`** | `SshShell.kt`, `sshmanager.kt`, `sshhost.kt`, `SftpBrowser.kt`, `ShellSession.kt` | local | **UI blocked** |
| Debugger | PAGE_DEBUG (own tab, should move to Settings) | present under `MainActivity.kt` debug page | `GhajarLog.kt`, `GhajarLogActivity.kt`, `Configdebug.kt` | local logs | Working, needs re-homing into Settings |
| Tor | Settings hub card | referenced via `R.drawable.tor`, `Torcontroller.kt`, `"proj_tor_desc"` | `Torcontroller.kt` | network | Present |
| Psiphon | Settings/hub | `PsiphonConfig.kt`, `PsiphonEngine.kt` | native | network | Present |
| Subscriptions | Config picker | `Subscriptionrefresher.kt`, `SubscriptionFetcher.kt` | remote sub URLs | network | Present |
| Free Configs | Config picker → Free Projects | `freecfg/` package, `FreeConfigs.kt` | `TelegramWebFetcher.kt` | network | Present — 35-cap rule described in brief needs a dedicated audit pass on `FreeConfigs.kt`, not yet re-verified against the new cap language |
| Config Center | `ConfigCenterActivity` | `configcenter/` package | `ConfigCenterRouter.kt` | local | Present |
| QR import | Home → scan QR | `showScanner` route in `MainActivity.kt` | camera | CAMERA permission | Present |
| Per-app routing | Settings → Per-app | `AppProxyScreen` (`"perapp"` route) | `ConfigStore` | QUERY_ALL_PACKAGES | Present |
| Logs | Settings → Logs | `GhajarLogActivity`, `"xray_logs"` route | `GhajarLog.kt` | local | Present |
| Network tools (checkhost/clean IP) | Settings hub | `Checkhost.kt`, `Warp.kt`, `Ipintelligence.kt` | network | INTERNET | Backend present, **screen for Clean IP missing** (see blockers) |
| Notifications | System | `GhajarNotificationJob.kt`, `GhajarNotificationMonitor.kt` | WorkManager/service | POST_NOTIFICATIONS | Present |
| Quick Settings tile | System | `QsTileService` (`Qstileservice.kt`) | `VpnState` | — | Present |
| Store / purchase | PAGE_SHOP | `GhajarShopScreen.kt` | `GhajarStoreApi.kt`, `GhajarPaymentPolicy.kt` | network | Present |
| Wallet / payment | Store → checkout | `GhajarCheckoutCards.kt`, `GhajarCheckoutViewModel.kt` | `SecurePaymentActivity`, `GhajarStoreApi.kt` | network | Present |
| Account linking | Store | `GhajarAccountStore.kt`, `GhajarLinkFlow.kt` | Telegram bot link | network | Present |
| Browser-adjacent | Store web view | `GhajarStoreWebActivity.kt` | WebView | INTERNET | Present |
| Data usage | Settings → Data Usage | `UsageStore.kt`, `"data_usage"` route | TrafficStats | local | Present, **not yet per-app/per-config split** per doc section 17 — needs dedicated pass |
| Backup/Restore | Settings | not yet located in this pass | — | — | To confirm in next audit slice |
| VPN Share | Settings/Home | not yet located in this pass | — | — | To confirm in next audit slice |

## Next steps (in order)

1. ~~Home screen visual redesign~~ — **done this session**: hero now sits on
   the shared `drawDotWorldMap` dot field with a state-colored glow ring
   (`MainActivity.kt` `ConnectionScreen`), a real plan/expiry card
   (`GhajarPlanStatusCard`) sourced from `GhajarStoreApi`, an always-visible
   download/upload stat row, and a `GhajarQuickActionsRow` wired to four real
   destinations (fastest sort, full list, Free Configs, QR scan). The
   connect/disconnect pill's own logic was left untouched and only wrapped,
   since there is no compiler in this environment to verify a riskier rewrite
   of the one control the app cannot get wrong.
2. Locate and verify Backup/Restore and VPN Share implementations (not yet
   confirmed present or absent).
3. Build real `SshScreen` and `CleanIpScreen` UIs against their existing
   backends (separate, sizeable pass).
4. Migrate the 5-page pager down to the required 3 tabs, moving SSH and
   Debugger into Settings, without deleting any of their functionality —
   the supplied reference screenshots also only ever show 3 bottom-nav
   items, confirming this is required, not optional polish.

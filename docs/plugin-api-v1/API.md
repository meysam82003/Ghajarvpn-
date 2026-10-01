# Ghajar plugin API 1

Status: host/SDK architecture, not a published plugin ABI guarantee. Target milestone 1.1.1. No candidate APK has been built, signed, released, or enabled in the production catalog during this phase. Device acceptance below is still required before shipping.

Source of truth: `../audit-1.1.1/AUDIT_FA.md`, `FEATURE_MATRIX.json`, `REPOSITORY_MATRIX.json`, `SOURCE_EVIDENCE.json`. Baseline host commit: `ace3937b487fd8ed1a24486b3e83887b6db5c335`. Existing bundled engines stay bundled, with identical source pins and binaries.

## Layers

| Concept | Meaning in this implementation |
|---|---|
| Core | Runtime implementation, e.g. Xray or a complete Mihomo engine |
| Protocol | Wire protocol, e.g. ShadowQUIC; not a synonym for APK |
| Transport | Carrier such as XHTTP or QUIC; described by the engine contract/config |
| Method | DNS tunnel, fragment, obfuscation or other method layered on a core |
| Plugin | Independently versioned, explicitly approved APK packaging and IPC boundary |
| TUN engine | Packet-to-proxy adapter, e.g. existing zeptun, never a new VPN protocol |

`CapabilityRegistry` describes current built-in adapters. `PluginCatalog` describes audited candidate source, not installed support. Only an accepted `PluginRelease` authorizes plugin runtime capabilities. Claims cannot exceed the audited candidate. New flags and unknown flag names fail validation rather than silently becoming UI features. Unwired capabilities remain false (including HY2 port hopping in the current host).

## SDK and IPC

`plugin-api` is a small Java Android library, minSdk 26, under the repository's existing license. It contains no native core, download client or binary loader. It is an API module, **not** a plugin APK. A future publisher implements `GhajarPluginService` and links its reviewed engine implementation. Copying Husi's authority-prefix discovery is unnecessary and unsupported.

- Explicit component binding with action `net.gozar.plugin.BIND_V1`.
- Independent package per release: `net.ghajar.plugin.<id>.v<versionCode>`; concrete service must be inside that package.
- Export only the engine service, with metadata `net.gozar.plugin.ID` and integer `net.gozar.plugin.API=1`.
- Single current APK signer, no shared UID, no split APK, no debuggable APK, no other exported component. API 1 permission allowlist: INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK. A wider surface requires host review.
- Host verifies the actual installed APK, not just PackageManager display metadata, before each bind.
- Service checks `Message.sendingUid`, unique package `com.ghajarvpn.app` and a publisher-embedded **explicit set of host signing certificate SHA-256 values** for every IPC request. Empty sets reject all callers. Certificate history, package prefixes and custom authorities do not grant trust.
- Every reply echoes requestId, pluginId, API and release versionCode. Requests have bounded timeouts. Stale/unmatched replies cannot complete a new request.
- Raw UTF-8 config travels through a read-only descriptor, max 8 MiB, rather than a giant Binder string. A bounded settings JSON (64 KiB) accompanies it. Host never turns a Mihomo config into Xray JSON. Temp config files stay inside private cache and are deleted on completion.
- SDK serializes operations on one worker. Host Binder death, unbind and shutdown call the idempotent engine cleanup. The adapter must implement stop safely even before start.

| Operation | Contract |
|---|---|
| HEALTH | Load/self-test the adapter, return `healthy=true`, release test resources; no tunnel/traffic from discovery |
| PREPARE | Validate original config and settings; return the complete host-TUN plan; never rewrite the stored original |
| START | Receive original config, settings, host Binder token, optional duplicated TUN FD; return `ready=true` only once operational |
| STATUS | Report actual engine readiness; no synthetic success |
| NETWORK_CHANGED | Re-evaluate physical routes/underlying network without rewriting the config |
| STOP | Stop engine and workers, close all owned descriptors/listeners, release transient secrets |

### Host VPN plan

A private `PluginVpnService` owns Android VPN permission, foreground notification and TUN lifecycle. It is deliberately not a second always-on service candidate. No guarantee of automatic plugin restart after process death is made in API 1; reconnect re-verifies the installed release. Existing always-on/bundled paths are unchanged.

PREPARE returns `mode=socks` or `mode=tun`, `mtu` in 1280..9000.

- SOCKS: requires real UDP and SOCKS support in API 1; plugin START returns **127.0.0.1 only**, `socksPort` 1..65535. Host verifies a SOCKS5 greeting and carries the TUN through the **existing unchanged zeptun implementation**. The greeting is not an Internet reachability test. IPv6 is only routed when the release actually supports it; no unsupported-family bypass is enabled.
- TUN/full config: requires supportsTun, supportsFullConfig, supportsDns. PREPARE supplies bounded `addresses`/`routes` CIDR lists and numeric `dns` addresses, and `fullConfigPreserved=true`. Android validates numeric addresses/prefixes. Adapter must reject unsupported route/provider/auth/file-reference semantics in PREPARE; a boolean alone is not release qualification. START receives a duplicated TUN FD; host retains its own descriptor.
- Host and plugin UIDs are excluded from recursive upstream capture. Existing per-app selection policy is applied to the host TUN. An impossible allowlist fails rather than silently tunnelling everything. A future full-config adapter must explicitly reconcile its per-package policy with this host contract or reject it.
- A plugin lease blocks update activation/rollback while connecting, connected or holding the kill-switch interface. Native teardown of the previous bundled engine is awaited directly, not guessed from delayed broadcasts. Connection generations discard superseded starts.
- Only the active plugin receives health/status/network calls. There is no startup bind of every installed plugin. Binder loss/failed status closes the session; with the existing kill-switch setting enabled, a fresh unread TUN is retained until explicit disconnect, when Android allows its establishment. OS always-on lockdown remains the platform mechanism against VPN permission revocation/process death.

## Signed release envelope and trust

Production publisher list and release catalog are intentionally empty. There is **no** enrollment UI, TOFU, unsigned debug route, remote authority discovery, or backup-imported trust key. Merely installing a third-party ShadowQUIC/Husi APK does not make it a Ghajar plugin.

Envelope fields: `publisher`, `payload` (base64 exact UTF-8 JSON bytes), `signature` (base64 RSA SHA-256 signature over those exact bytes). RSA key must be at least 3072 bits. Trusted publisher entries pin manifest public key, APK certificate hashes, plugin ID scope and final HTTPS download hosts. Catalog is host-bundled; it is not a user-editable URL feed.

Payload requires all of these:

| Fields | Validation |
|---|---|
| id, name, version, versionCode | Known ID, human names, exact positive integer version code |
| apiVersion, minGhajarVersion, minGhajarVersionCode | API 1, minimum installed host versionCode; readable version included |
| abis | Actual device match and native APK entries verified before installation |
| capabilities | Typed known boolean flags, subset of reviewed source capabilities |
| sourceRepository, sourceCommit, sourceTag | Approved exact repository and commit; optional tag retained for provenance |
| downloadUrl, size, sha256 | Final explicitly permitted HTTPS origin, no credentials/fragments/redirects; bounded exact file size and SHA-256 |
| publisher, certificateSha256, license | Publisher identity and exact current signer pinned; license metadata required; source/dependency licensing reviewed before publisher enrollment |
| packageName, serviceClass | Independent versioned package namespace and explicit service |
| dependencies | Known IDs, positive minimum versions, no self-reference/duplicates; recursive installed checks reject cycles |

HTTP redirects are refused deliberately; publishers must sign the final allowed distribution URL. Do not populate the production key list using a test key. A source update, broader capability, new license/dependency situation, or signer rotation needs another host-side review and approved catalog update. This conservative v1 policy is not an automatic upstream-release installer.

## Atomic installation, update and recovery

`NOT_INSTALLED → AVAILABLE → DOWNLOADING → VERIFYING → INSTALLING → INSTALLED`; compatible newer releases yield UPDATE_AVAILABLE. Invalid compatibility yields INCOMPATIBLE. Unhealthy installed/staged releases yield BROKEN/FAILED as appropriate. Failed operations may coexist with a still-active healthy older release; the UI preserves its version.

1. Download private UUID temp APK with a hard signed-size limit.
2. Verify envelope, hash, size, APK signer/identity/ABI/service/permissions and dependencies.
3. Create Android PackageInstaller session for an independent version package. Persist session ID + random callback nonce before commit.
4. Android asks the user to allow installation and confirm the package. Notification plus an in-app Continue Installation action cover the consent handoff.
5. Correlate callback nonce/session. Re-verify the **installed** package and run HEALTH through explicit IPC.
6. Under an inactive-plugin lease, fsync and atomically write/verify the registry; only then replace active and retain previous.

Old active APK is never the PackageInstaller update target. A failure at any pre-activation step leaves its pointer and APK intact. A failed newly installed APK is staged for retry/repair, not activated. Downloads interrupted by process death become retryable; UI resume reconciles PackageInstaller and verifies any installed pending package. Orphan temporary downloads/uncommitted owned plugin sessions are cleaned without touching the app updater.

Explicit Rollback re-verifies and health-checks previous before swapping slots. Repair health-checks staged or active. Retry can redownload a missing/corrupt inactive artifact. Remove requests Android uninstall of the active package, refuses use during a tunnel or by dependent plugins, and only observes removal on reconciliation. **Configs are never removed.** Previous APK is retained for rollback; obsolete older APKs can be removed through Android app settings. No silent OS downgrade or uninstall is attempted. Retained versions increase installed storage; APK size/retention policy will be measured in the deferred size phase.

## Preservation, UI and backup

- ProxyConfig `protocol=plugin`, with a separate opaque plugin envelope in `extra`: id/format/payload/version/settings. It is not mapped to a generic host/port server.
- YAML comments, anchors, provider definitions, rules, DNS and TUN stay byte-for-byte as imported UTF-8. ConfigParser, file DecoderRegistry, validator, Config Center, duplicate identity, editor and existing backup serializer preserve the envelope.
- ShadowQUIC URI/JSON and complete Mihomo YAML/JSON are recognized or editable without installation. TrustTunnel/EasyConnect raw configs are candidate storage only.
- The previous Clash node extraction remains an **explicit** optional action, with a warning that extracted nodes do not include full rules/providers. Default import preserves the complete document.
- Server selection and settings have additive plugin entry points. Missing plugins remain visible; install-and-connect does not reject/delete the config. With no approved APK, that action is visibly unavailable and the reason is shown. No fabricated 8.2 MB size/version or working-runtime claim appears.
- Backup's ordinary configs hold opaque plugin config/settings. Settings snapshot includes plugin IDs, active/requested versions and per-plugin settings. Binary APK, registry active pointers, publisher keys, signature allowlists and download authorities are excluded. Restore records reinstall hints only; an installed package must still be separately verified.

## Release/device gates (not executed in this phase)

Android 8/12/14/15/16 installation consent, callback delivery, notification denial, process death at every transition, native ABI/16-KiB-page compatibility, service/binder crash, rapid connect/stop/switch, VPN revoke, host upgrade, rollback with previous package missing, dependency loss, IPv4/IPv6 and UDP DNS routing, per-app rules and kill switch. Test with real signed Ghajar adapters, not arbitrary upstream APKs. Do one authorized full build/CI/regression cycle only after all upgrade phases are ready.

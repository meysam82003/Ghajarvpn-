# Focused validation, not a product build

- 69 independent Kotlin/JVM JUnit tests passed (43 retained phase4 tests + 26 phase5 tests), including the follow-up review after 4cd7bd2. Actual DirectShare, ConfigParser, ConfigShare, ConfigBuilder, import registry, settings, forms, plugin metadata/trust and AuthenticatedRelay source was used.
- Phase5 tests cover package dependency remapping/cycles/version errors, exact Mihomo YAML, original WireGuard DNS/AllowedIPs/keepalive, stale-file rejection, IKEv2 XML escaping/no password, IPv6 URI authority, locked profile refusal, OpenVPN external files/hooks/old inline password, route precedence/loopback isolation, unauthenticated HTTP, wrong/no-auth SOCKS, real authenticated CONNECT/SOCKS forwarding to a loopback fixture, destination hostname preservation, IPv6 target framing, byte accounting, stop closure and UDP refusal.
- Android reference classes and small stand-ins for unrelated app globals (SSH/DNS preferences, ports, logging and pin predicate) isolate the pure JVM test boundary. No native core/server was executed. Relay tests use real JVM TCP sockets against a SOCKS fixture, not a claim about an Android hotspot.
- PhoneSharing lifecycle/controller Kotlin type-checked separately against coroutines with explicit ConfigStore/VpnState stand-ins. Changed Kotlin parsed with Kotlin PSI. This does not type-check the entire Compose app.
- git diff --check clean. No Gradle, APK build, native product build, CI dispatch, tag or release.

Not run: complete Android/Compose build; installed APK tests; Android service scheduling/races/revocation and package lifecycle; real VPN exit, DNS/IPv6 leak, Wi-Fi/AP multi-device interoperability; Apple profile installation; compatible client imports on Windows/macOS/Linux/iOS; throughput/battery benchmarks. See ACCEPTANCE.md.

## Follow-up source review (2026-10-01)

Seven regression tests added: quoted/optional OpenVPN hook and external-file rejection; valid quoted inline TLS key direction retained; quoted/`--` inline account credentials stripped; malformed quotes/escapes/NUL rejected; relay lifecycle close/restart refusal; HTTP 502 on upstream rejection; SOCKS core failure response without success/fallback. Existing no-auth and UDP tests now require the explicit SOCKS failure response.

The same standalone JVM suite passed with real loopback sockets. PhoneSharing and AuthenticatedRelay also type-checked together with coroutines and ConfigStore/VpnState stand-ins. Unexpected Android listener failure and UI retry still require device scheduling checks; the source now closes sessions when the accept loop exits and the controller reports ERROR until an explicit retry or new VPN session.

A pre-existing ConfigBuilder warning about a redundant non-null condition remains; no unrelated cleanup or engine change was made. Product/native/Compose build and all deferred acceptance cases remain unexecuted.

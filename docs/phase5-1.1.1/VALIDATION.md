# Focused validation, not a product build

- 62 independent Kotlin/JVM JUnit tests passed (43 retained phase4 tests + 19 phase5 tests). Actual DirectShare, ConfigParser, ConfigShare, ConfigBuilder, import registry, settings, forms, plugin metadata/trust and AuthenticatedRelay source was used.
- Phase5 tests cover package dependency remapping/cycles/version errors, exact Mihomo YAML, original WireGuard DNS/AllowedIPs/keepalive, stale-file rejection, IKEv2 XML escaping/no password, IPv6 URI authority, locked profile refusal, OpenVPN external files/hooks/old inline password, route precedence/loopback isolation, unauthenticated HTTP, wrong/no-auth SOCKS, real authenticated CONNECT/SOCKS forwarding to a loopback fixture, destination hostname preservation, IPv6 target framing, byte accounting, stop closure and UDP refusal.
- Android reference classes and small stand-ins for unrelated app globals (SSH/DNS preferences, ports, logging and pin predicate) isolate the pure JVM test boundary. No native core/server was executed. Relay tests use real JVM TCP sockets against a SOCKS fixture, not a claim about an Android hotspot.
- PhoneSharing lifecycle/controller Kotlin type-checked separately against coroutines with explicit ConfigStore/VpnState stand-ins. Changed Kotlin parsed with Kotlin PSI. This does not type-check the entire Compose app.
- git diff --check clean. No Gradle, APK build, native product build, CI dispatch, tag or release.

Not run: complete Android/Compose build; installed APK tests; Android service scheduling/races/revocation and package lifecycle; real VPN exit, DNS/IPv6 leak, Wi-Fi/AP multi-device interoperability; Apple profile installation; compatible client imports on Windows/macOS/Linux/iOS; throughput/battery benchmarks. See ACCEPTANCE.md.

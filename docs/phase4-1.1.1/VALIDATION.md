# Validation boundary

Executed without Gradle, APK/native product builds, CI, tags or release:

- Standalone Kotlin/JVM: **43 tests passed**. Actual parser, forms, EngineSettings, SingBoxConfig, PluginProfiles/Trust, import toolkit and selected existing tests. Android reference JAR and small stand-ins for DNS preferences/SSH mode constants/MixedPort were used only to isolate pure configuration code. No native core was executed.
- SDK Java + ShadowQUIC/Mihomo service Java: checked using the Android 15 reference classes; temporary BuildConfig constants supplied for the plugin service check. This checks Java/API signatures, not Android lifecycle behavior or Gradle packaging.
- Host plugin Kotlin (non-Compose): checked against the Android reference and explicit temporary stand-ins for app dependencies. This is not a full application compile.
- Changed Kotlin files parsed with Kotlin PSI; no syntax errors. This is not Compose type checking.
- Isolated Go standard-library tests: CONNECT authority/basic-auth/tunnel bytes, refusal, bounded headers, invalid proxy schemes and header injection; provider path confinement, preservation of WebSocket URL paths. The Go toolchain was used only for these isolated tests and gofmt. No Mihomo/ShadowQUIC/helper/Xray product was built.
- XML manifests parsed, deferred scripts checked with `sh -n`, `git diff --check` clean.
- Psiphon AAR SHA-256 matches the prior inventory. Protected source pins/binaries/workflows were not edited.

Not performed: native dependency compilation/linking, Android ABI/16 KiB page acceptance, installation and signature enforcement on a device, rollback with installed APK versions, VPN permission/revocation/process death, real remote server connectivity, Mihomo providers and selector behavior on Android, real ECH retry, Psiphon/OpenConnect/OpenVPN enterprise authentication, battery/performance/size measurements. There is no CI-success or device-success claim.

# Validation boundary / reproduction

No Gradle, APK/AAR/native product build, CI, tag creation or release was performed.

## Host checks

`python scripts/validation/run_host_checks.py --dependencies /path/to/host-jars`

The dependency directory contains Kotlin compiler embeddable 2.1.0 and runtime dependencies, kotlin-stdlib 2.1.0, annotations 13.0, kotlinx-coroutines-core-jvm 1.6.4, JSON 20240303, android-all 15 reference classes, JUnit 4.13.2, Hamcrest 1.3, ZXing core. The script checks the actual Java ShadowQuicProfile contract with javac, compiles actual pure app sources with explicitly isolated collaborators in `host-fixtures`, runs 74 configuration/plugin/sharing tests and 21 permission/logging tests, type-checks actual PhoneSharing/AuthenticatedRelay, and compiles XrayCorpus.kt. It does not compile Compose or the complete Android application. Fixture credentials are synthetic; fixtures are outside production source sets.

The existing shared-relay tests perform real local authenticated SOCKS/CONNECT forwarding, hostname/IPv6 framing, wrong/no-auth refusal, backend failure, UDP refusal and shutdown. These are not hotspot/device tests.

Generate the corpus using the resulting corpus.jar, phase3-tests.jar, runtime/JSON/ZXing/Android reference classpath and sdk-check directory:

```
java -cp "$CHECK_JARS/corpus.jar:$CHECK_JARS/phase3-tests.jar:$CHECK_JARS/*:$CHECK_JARS/sdk-check" net.gozar.app.validation.XrayCorpusKt "$CORPUS"
python scripts/validation/run_xray_corpus.py --corpus "$CORPUS" --cores "$OFFICIAL_CORES" --assets "$REPO/app/src/main/assets" --output full.json
python scripts/validation/run_xray_corpus.py --corpus "$CORPUS" --cores "$OFFICIAL_CORES" --assets "$REPO/app/src/main/assets" --host-without-tun --output host.json
python scripts/validation/run_xray_corpus.py --corpus "$CORPUS" --cores "$OFFICIAL_CORES" --assets "$REPO/app/src/main/assets" --host-without-tun --candidate-schema --output candidate.json
python scripts/validation/xray_mkcp_loopback.py --corpus "$CORPUS" --cores "$OFFICIAL_CORES" --output mkcp-wire.json
```

`OFFICIAL_CORES` contains the verified official executables under `26.3.27/xray`, `26.7.28/xray`, `26.9.30/xray`; SHA-256/URLs are recorded. Always use trusted executables. Host mode removes only the TUN inbound. Candidate mode also applies the documented FinalMask adapter in `xray_candidate_schema.py`; it is a qualification script, not the production runtime. Full output is retained separately. Both newly introduced migration semantics and the absence of automatic protocol substitution are explicit.

Observed: 125 generated cases, zero generator exceptions/timeouts. Direct current host parse 109/125; candidate-adapted 7.28 108/125 and 9.30 109/125. Sixteen cases are already removed legacy HTTP/QUIC transports; the additional 7.28 xDNS case rejects its old domain schema. 42/42 real mKCP transfers succeeded against a 26.3.27 server with all three client versions. No real external VPN server credentials were used.

Checks also performed: Kotlin PSI parsing of changed source, XML parse, `git diff --check`, source-pinned AAR hash verification, actual published APK checksums/ABI/ZIP inventory, readelf sections/program headers for large native entries. APK inventory is reproducible with `scripts/validation/apk_inventory.py`.

## Remaining product gates

- New merged manifest, complete Android/Compose compilation, R8, native package alignment and both ARM APKs.
- Actual Android TUN descriptor ownership/lifecycle, stop/reconnect concurrency, network callbacks, battery/thermal measurement.
- Sharing start/stop during a live VPN, backup restore during a session, process death, real LAN/Hotspot, interface binding and DNS/IPv6/no-fallback behavior across devices.
- REALITY/TLS/WireGuard/SS2022/Mux/XHTTP and other remote endpoint interoperability; official CLI parsing is insufficient.
- Signed plugin APK install/update/repair/rollback and publisher provisioning, unchanged phase4/5 acceptance gates.
- Notification channel user choices, DND/heads-up, runtime permission denial and camera/document picker on target Android/OEMs.

The test harness cannot establish complete regression success. The bundled Xray version therefore remains 26.3.27.

# Ghajarvpn 1.0.0 — complete source

`Android/` is the complete Android project used by this release, including all modules, resources, native engine inputs, Gradle wrapper, and the combined Psiphon/Xray AAR. It is ready to open in Android Studio; it is not a source overlay.

`NativeSources/` contains the pinned Psiphon and Aether engine sources. The Android tree also contains the OpenVPN and strongSwan sources. `ReleaseTools/` includes the reviewed repository, patches, build scripts and server installer. `Backend/Faoxima-1.0.0/` contains the complete corrected bot and mini-app source supplied for this project. Original license files remain included.

For Android, install JDK 17 and the Android SDK/NDK versions declared in `ReleaseTools/.github/workflows/android.yml`. Set the SDK location locally and run from `Android/`:

```sh
./gradlew --no-daemon -Pghajar.demo=true :browser:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

The included AAR can be rebuilt with `ReleaseTools/scripts/build-psiphon-aar.sh`, passing the Android directory and `NativeSources/Psiphon`. Aether's pinned binary installer and exact release hashes are included in `ReleaseTools/scripts/fetch-aether-release.py`.

`SOURCE-MANIFEST.json` identifies the release commit and SHA-256 of every archived file. Only generated build caches, Git internals, local SDK settings and signing credentials are omitted. Application and server source files, modules and resources are included.

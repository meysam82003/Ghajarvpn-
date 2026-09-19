# zeptun

The userspace TUN engine from [Noisemux/zeptun](https://github.com/Noisemux/zeptun),
MIT licensed. Its copyright notice is in `LICENSE` beside this file, as the
licence requires.

Nothing of it is vendored here. It is built from source in CI, from the commit
pinned in `.github/workflows/android.yml` (`ZEPTUN_COMMIT`), with the Zig
version its own build declares as the minimum. The step produces
`libzeptun.so` and `libzeptun-jni.so` per ABI and copies them into
`app/src/main/jniLibs/`.

Two consequences worth knowing:

- A local Gradle build without that step produces an APK with no zeptun
  libraries in it. That is not a broken build. `ZeptunEngine` reports itself
  unavailable and every caller treats it as absent, exactly as it does on a
  device where the library fails to load.
- `dev.zeptun.Zeptun` in `app/src/main/java/dev/zeptun/` is not our code's
  natural home for a class - it is the exact class name the library's JNI
  bridge looks up in `JNI_OnLoad` (`ZEPTUN_JNI_CLASS` in its
  `src/jni/zeptun_jni.c`). Moving or renaming it stops the native methods
  binding.

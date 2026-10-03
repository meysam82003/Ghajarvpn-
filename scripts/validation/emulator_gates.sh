#!/bin/bash
set -euo pipefail
mkdir -p crash-hunt
apk=$(find apk -name '*x86_64*.apk' | head -n1)
testapk=$(find test-apk -name '*.apk' | head -n1)
adb install -r "$apk"
adb install -r "$testapk"
adb shell pm grant com.ghajarvpn.app android.permission.POST_NOTIFICATIONS
# Real wrong-signer fixture. This ephemeral test key never signs an RC artifact.
tools="$ANDROID_SDK_ROOT/build-tools/36.0.0"
keytool -genkeypair -keystore "$RUNNER_TEMP/test-key.jks" -alias test -storepass test-only-password -keypass test-only-password -dname CN=NegativeTest -keyalg RSA -validity 1
"$tools/apksigner" sign --ks "$RUNNER_TEMP/test-key.jks" --ks-key-alias test --ks-pass pass:test-only-password --out "$RUNNER_TEMP/wrong-signer.apk" "$apk"
adb shell mkdir -p /sdcard/Android/data/com.ghajarvpn.app/files
adb push "$RUNNER_TEMP/wrong-signer.apk" /sdcard/Android/data/com.ghajarvpn.app/files/wrong-signer.apk
adb shell am instrument -w -r com.ghajarvpn.app.test/androidx.test.runner.AndroidJUnitRunner | tee crash-hunt/instrumentation.txt
python3 - <<'PY'
from pathlib import Path
import re
s=Path('crash-hunt/instrumentation.txt').read_text()
assert re.search(r'OK \(\d+ tests?\)',s),s
assert not any(x in s for x in ('FAILURES!!!','INSTRUMENTATION_FAILED','Process crashed')),s
PY
adb shell rm /sdcard/Android/data/com.ghajarvpn.app/files/wrong-signer.apk

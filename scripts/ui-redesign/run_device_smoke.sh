#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-evidence
app_apk=$(find device-apks -name 'app-arm64-v8a-debug.apk' -print -quit)
test_apk=$(find device-apks -name 'app-debug-androidTest.apk' -print -quit)
[ -n "$app_apk" ] && [ -n "$test_apk" ]
adb install -r "$app_apk"
adb install -r "$test_apk"
adb shell appops set com.ghajarvpn.app ACTIVATE_VPN allow
python3 scripts/ui-redesign/real_socks_relay.py > device-evidence/relay.log 2>&1 &
relay_pid=$!
trap 'kill "$relay_pid" 2>/dev/null || true' EXIT
adb logcat -c
adb shell am instrument -w -r \
  -e realProxyHost 10.0.2.2 -e realProxyPort 18080 -e realBackend true \
  com.ghajarvpn.app.test/androidx.test.runner.AndroidJUnitRunner \
  | tee device-evidence/instrumentation.txt
adb pull /sdcard/Android/data/com.ghajarvpn.app/files/ui-redesign device-evidence/screenshots || true
adb logcat -b crash -d > device-evidence/crash.log
if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' device-evidence/instrumentation.txt; then
  exit 1
fi
grep -Eq 'OK \([0-9]+ tests?\)' device-evidence/instrumentation.txt

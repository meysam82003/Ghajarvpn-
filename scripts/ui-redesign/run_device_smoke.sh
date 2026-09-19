#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-evidence
# Both ARM translation paths crash inside native Go. Use the real x86_64
# Xray/Psiphon, sing-box and zeptun engines, without a mock or demo flag.
app_apk=$(find device-apks -name 'app-x86_64-debug.apk' -print -quit)
test_apk=$(find device-apks -name 'app-debug-androidTest.apk' -print -quit)
[ -n "$app_apk" ] && [ -n "$test_apk" ]
adb install -r "$app_apk"
adb install -r "$test_apk"
adb shell appops set com.ghajarvpn.app ACTIVATE_VPN allow
python3 scripts/ui-redesign/real_socks_relay.py > device-evidence/relay.log 2>&1 &
relay_pid=$!
trap 'kill "$relay_pid" 2>/dev/null || true' EXIT
failed=0
run_case() {
  local label="$1" target="$2"
  adb logcat -c
  if ! adb shell am instrument -w -r -e class "$target" \
      -e realProxyHost 10.0.2.2 -e realProxyPort 18080 -e realBackend true \
      com.ghajarvpn.app.test/androidx.test.runner.AndroidJUnitRunner \
      | tee "device-evidence/$label.txt"; then failed=1; fi
  adb logcat -b crash -d > "device-evidence/$label-crash.log"
  cat "device-evidence/$label-crash.log"
  if ! grep -Eq 'OK \([0-9]+ tests?\)' "device-evidence/$label.txt"; then failed=1; fi
}
# An engine crash must not prevent inspection of independent UI/storage flows.
run_case navigation net.gozar.app.UiRedesignNavigationTest
# Scoped storage blocks shell reads of Android/data. The debuggable app can
# export its own test-created files via run-as, without changing permissions.
if adb exec-out run-as com.ghajarvpn.app tar -C files -cf - ui-redesign > device-evidence/screenshots.tar; then
  mkdir -p device-evidence/screenshots
  tar -xf device-evidence/screenshots.tar -C device-evidence/screenshots
  rm device-evidence/screenshots.tar
else failed=1; fi
if [ "$(find device-evidence/screenshots -name '*.png' | wc -l)" -ne 13 ]; then
  echo 'Expected all 13 real Android screenshots.'
  failed=1
fi
run_case persistence net.gozar.app.RedesignStateTest,net.gozar.app.ExampleInstrumentedTest
run_case backend 'net.gozar.app.RuntimeConnectionTest#storeReachesRealBackendAndPersistsLinkSession'
run_case tunnel 'net.gozar.app.RuntimeConnectionTest#connectTransferDisconnectReconnectUsesExplicitServer'
exit "$failed"

#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "${project_root}/runtime-evidence"
collect_evidence() {
  adb logcat -d -v threadtime > "${project_root}/runtime-evidence/logcat.txt" || true
  adb shell dumpsys activity exit-info com.ghajarvpn.app > "${project_root}/runtime-evidence/process-exits.txt" || true
  adb pull /sdcard/Download/ghajar-ci "${project_root}/runtime-evidence/screenshots" || true
}
trap collect_evidence EXIT
adb logcat -c
cd "${project_root}/.ghajarvpn-src"
./gradlew --no-daemon -Pghajar.demo=true -Pghajar.testAbi=x86_64 :app:connectedDebugAndroidTest

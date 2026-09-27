#!/bin/sh
# Install the debug APK on a running emulator, drive it with monkey and print
# every crash (logcat AndroidRuntime FATAL EXCEPTION + the app's own
# ghajar-crash.log). Usage: crash-hunt.sh <apk>
set -u
apk=$1
pkg=com.ghajarvpn.app
[ -f "$apk" ] || { echo "::error::no APK at '$apk'"; exit 1; }
echo "APK: $apk"
adb wait-for-device
adb shell getprop ro.product.cpu.abilist
adb install -r -g "$apk" || { echo "::error::install failed"; exit 1; }
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c

adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 20
adb shell dumpsys activity activities | grep -m1 -i "topResumedActivity\|mResumedActivity" || true

for seed in 11 22 33 44 55 66 77 88; do
    echo "== monkey seed $seed"
    adb shell monkey -p "$pkg" -s "$seed" --pct-syskeys 0 --pct-appswitch 5 --pct-anyevent 0 \
        --throttle 250 --ignore-crashes --ignore-timeouts --ignore-security-exceptions \
        --monitor-native-crashes -v 1200 2>&1 | grep -E "CRASH|Long Msg|Short Msg|Events injected" || true
    # Monkey can leave the app (VPN dialog, share sheet, browser); bring it back.
    adb shell am force-stop com.android.chrome 2>/dev/null || true
    adb shell input keyevent KEYCODE_BACK
    adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 3
done

echo "================ logcat crashes ================"
adb logcat -d -b crash > crash.txt 2>/dev/null
adb logcat -d AndroidRuntime:E '*:S' >> crash.txt 2>/dev/null
grep -c "FATAL EXCEPTION" crash.txt || true
# One block per crash, de-duplicated by the exception + first app frame.
awk '/FATAL EXCEPTION/{if(b!="")print b"\n----";b=$0;next} b!=""{b=b"\n"$0} END{if(b!="")print b}' crash.txt | head -n 1500

echo "================ ghajar-crash.log ================"
adb shell run-as "$pkg" sh -c 'find files cache no_backup -name "*.log" 2>/dev/null' | while read -r f; do
    adb shell run-as "$pkg" cat "$f" | awk '/== CRASH ==/{p=1} p{print} /^====================/{p=0}' | head -n 400
done
adb shell ls /sdcard/Android/data/$pkg/files 2>/dev/null
adb shell 'cat /sdcard/Android/data/'"$pkg"'/files/*/ghajar-crash.log /sdcard/Android/data/'"$pkg"'/files/ghajar-crash.log 2>/dev/null' | head -n 400
exit 0

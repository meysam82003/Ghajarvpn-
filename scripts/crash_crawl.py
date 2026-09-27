#!/usr/bin/env python3
"""Crash hunt for CI: drive the installed app on an emulator and fail on any crash.

1. Launch MainActivity and prove it resumed (else fail: the app never reached UI).
2. Crawl the UI: on every screen, tap each clickable element once, go back,
   and descend into new screens up to MAX_DEPTH. Every tap is logged, so a
   crash is attributed to the exact path of taps that caused it.
3. Stress: rapid open/back cycles on the screens found, then a short monkey run.
4. Collect logcat, the crash buffer, tombstones and the app's own crash log
   into crash-hunt/ (uploaded as an artifact) and exit 1 on any crash or ANR
   in the app's process.

Usage: crash_crawl.py <apk>
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.ghajarvpn.app"
ACTIVITY = PKG + "/net.gozar.app.MainActivity"
OUT = "crash-hunt"
MAX_DEPTH = 3
PER_SCREEN = 30
TIME_BUDGET = 22 * 60
# Taps that would wipe the state the rest of the crawl needs, or leave the app.
SKIP = re.compile(r"(?i)(delete|remove all|حذف|logout|log out|sign out|خروج از حساب|reset|بازنشانی|uninstall|"
                  r"clear all|پاک کردن همه|factory|telegram|تلگرام)")

os.makedirs(OUT, exist_ok=True)
log_file = open(os.path.join(OUT, "crawl.log"), "w")
started = time.time()
crashes = []  # (path, summary)


def log(msg):
    line = f"[{time.time() - started:7.1f}s] {msg}"
    print(line, flush=True)
    log_file.write(line + "\n")
    log_file.flush()


def adb(*args, timeout=40):
    try:
        return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def pid():
    return adb("shell", "pidof", PKG).strip()


def crash_lines():
    return adb("logcat", "-d", "-b", "crash")


def foreground():
    out = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"(?:topResumedActivity|mResumedActivity)[^\n]*?\s(\S+/\S+)", out)
    return m.group(1) if m else ""


def launch():
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, timeout=60)


def wait_resumed(seconds=40):
    end = time.time() + seconds
    while time.time() < end:
        fg = foreground()
        if fg.startswith(PKG + "/") and "MainActivity" in fg:
            return True
        time.sleep(1)
    return False


def dump():
    """Clickable nodes of the current screen as (label, (x, y))."""
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/ui.xml")
        raw = adb("shell", "cat", "/sdcard/ui.xml")
        start = raw.find("<?xml")
        if start < 0:
            time.sleep(1)
            continue
        try:
            root = ET.fromstring(raw[start:])
        except ET.ParseError:
            time.sleep(1)
            continue
        nodes = []
        for n in root.iter("node"):
            if n.get("package") != PKG or n.get("clickable") != "true" or n.get("enabled") == "false":
                continue
            texts = [n.get("text") or "", n.get("content-desc") or ""]
            texts += [(c.get("text") or c.get("content-desc") or "") for c in n.iter("node")]
            label = " ".join(t.strip() for t in texts if t and t.strip())[:60]
            b = re.findall(r"\d+", n.get("bounds") or "")
            if len(b) != 4:
                continue
            x1, y1, x2, y2 = map(int, b)
            if x2 - x1 < 8 or y2 - y1 < 8:
                continue
            nodes.append((label or f"@{x1},{y1}", ((x1 + x2) // 2, (y1 + y2) // 2)))
        return nodes
    return []


def signature(nodes):
    return "|".join(sorted({l for l, _ in nodes}))[:400]


def check(path, before_pid, before_crash):
    """Records a crash if the process died or the crash buffer grew."""
    now_crash = crash_lines()
    now_pid = pid()
    grew = len(now_crash) > len(before_crash)
    died = before_pid and now_pid != before_pid
    anr = "ANR in " + PKG in adb("logcat", "-d", "-s", "ActivityManager:E")
    if grew or died or anr:
        new = now_crash[len(before_crash):] if grew else ""
        summary = next((l for l in new.splitlines() if "Exception" in l or "Error" in l or "Fatal signal" in l), "")
        summary = summary or ("ANR" if anr else "process died")
        crashes.append((" > ".join(path), summary.strip()))
        log(f"!!! CRASH after: {' > '.join(path)}\n    {summary.strip()}")
        with open(os.path.join(OUT, "crashes.txt"), "a") as f:
            f.write(f"===== after: {' > '.join(path)}\n{new}\n")
        return True
    return False


def back_to(sig, tries=3):
    for _ in range(tries):
        if signature(dump()) == sig:
            return True
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.8)
    return signature(dump()) == sig


def replay(path):
    """Relaunches and taps the labels of path again; True when it got there."""
    adb("shell", "am", "force-stop", PKG)
    launch()
    if not wait_resumed(30):
        return False
    time.sleep(2)
    for label in path:
        nodes = dict(dump())
        if label not in nodes:
            return False
        adb("shell", "input", "tap", *map(str, nodes[label]))
        time.sleep(1.2)
    return True


tried = set()
screens = []  # (path, signature) of every screen reached


def explore(path, depth):
    if time.time() - started > TIME_BUDGET:
        return
    nodes = dump()
    sig = signature(nodes)
    if (sig, ) in tried:
        return
    tried.add((sig, ))
    screens.append((list(path), sig))
    log(f"screen depth={depth} path={' > '.join(path) or '(home)'} clickables={len(nodes)}")
    count = 0
    for label, (x, y) in nodes:
        if count >= PER_SCREEN or time.time() - started > TIME_BUDGET:
            break
        if SKIP.search(label) or (sig, label) in tried:
            continue
        tried.add((sig, label))
        count += 1
        before_pid, before_crash = pid(), crash_lines()
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(1.3)
        step = path + [label]
        log(f"tap: {' > '.join(step)}")
        if check(step, before_pid, before_crash):
            if not replay(path):
                return
            continue
        fg = foreground()
        if not fg.startswith(PKG + "/"):
            # Left the app (VPN dialog, browser, share sheet): come back.
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(1)
            if not foreground().startswith(PKG + "/") and not replay(path):
                return
            continue
        new_sig = signature(dump())
        if new_sig != sig and depth < MAX_DEPTH:
            explore(step, depth + 1)
        if not back_to(sig):
            if not replay(path):
                return


def stress():
    """Open and leave each screen quickly, several times: exit-animation races."""
    for path, _ in screens[:40]:
        if not path or time.time() - started > TIME_BUDGET + 6 * 60:
            break
        for _ in range(3):
            before_pid, before_crash = pid(), crash_lines()
            nodes = dict(dump())
            if path[-1] not in nodes:
                break
            adb("shell", "input", "tap", *map(str, nodes[path[-1]]))
            time.sleep(0.15)
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(0.1)
            adb("shell", "input", "keyevent", "KEYCODE_BACK") if len(path) > 1 else None
            time.sleep(0.6)
            if check(path + ["(rapid open/back)"], before_pid, before_crash):
                replay(path[:-1])
                break
        replay(path[:-1]) if not foreground().startswith(PKG + "/") else None


def collect():
    with open(os.path.join(OUT, "logcat.txt"), "w") as f:
        f.write(adb("logcat", "-d", "-v", "threadtime", timeout=120))
    with open(os.path.join(OUT, "crash-buffer.txt"), "w") as f:
        f.write(adb("logcat", "-d", "-b", "crash", timeout=60))
    ts = adb("shell", "ls /data/tombstones/ 2>/dev/null").split()
    with open(os.path.join(OUT, "tombstones.txt"), "w") as f:
        for t in ts:
            if t.startswith("tombstone_") and not t.endswith(".pb"):
                f.write(f"==== {t}\n" + adb("shell", "cat", f"/data/tombstones/{t}")[:20000] + "\n")
    # The app's own crash log: only regular files, never a directory.
    files = adb("shell", "run-as", PKG, "find", "files", "no_backup", "cache", "-type", "f", "-name", "*.log").split()
    with open(os.path.join(OUT, "ghajar-crash.log"), "w") as f:
        for p in files:
            f.write(f"==== {p}\n" + adb("shell", "run-as", PKG, "cat", p) + "\n")
        ext = adb("shell", f"find /sdcard/Android/data/{PKG} -type f -name 'ghajar-crash.log' 2>/dev/null").split()
        for p in ext:
            f.write(f"==== {p}\n" + adb("shell", "cat", p) + "\n")


def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else ""
    if not os.path.isfile(apk):
        print(f"::error::no APK at '{apk}'")
        return 1
    adb("wait-for-device", timeout=300)
    adb("root")
    time.sleep(3)
    adb("wait-for-device", timeout=120)
    log("device ABIs: " + adb("shell", "getprop", "ro.product.cpu.abilist").strip())
    out = subprocess.run(["adb", "install", "-r", "-g", apk], capture_output=True, text=True).stdout
    if "Success" not in out:
        print("::error::install failed: " + out)
        return 1
    adb("logcat", "-c")
    adb("logcat", "-c", "-b", "crash")

    launch()
    if not wait_resumed(60):
        collect()
        print("::error::MainActivity never resumed - the app did not reach its UI")
        print(crash_lines()[:6000])
        return 1
    log("MainActivity resumed; process " + pid())
    time.sleep(4)
    # First-run screens (intro, permission prompts) are part of the crawl.
    explore([], 0)
    log(f"crawl done: {len(screens)} screens, {len(tried)} taps/signatures")
    stress()
    log("stress done")
    before_pid, before_crash = pid(), crash_lines()
    adb("shell", "monkey", "-p", PKG, "-s", "7", "--pct-syskeys", "0", "--pct-appswitch", "0",
        "--throttle", "120", "--ignore-security-exceptions", "-v", "1500", timeout=600)
    check(["(monkey 1500 events)"], before_pid, before_crash)
    collect()

    if crashes:
        print("::error::%d crash(es) in %s" % (len(crashes), PKG))
        for path, summary in crashes:
            print(f"::error::after [{path}]: {summary}")
        return 1
    log("no crash in the app process")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Crash hunt for CI: drive the installed app on an emulator and fail on any crash.

1. Launch MainActivity and prove it resumed (else fail: the app never reached UI).
2. Crawl the UI: on every screen, tap each clickable element once, go back,
   and descend into new screens up to MAX_DEPTH. Every tap is logged, so a
   crash is attributed to the exact path of taps that caused it.
3. Stress: rapid open/back cycles, then a focused touch/motion monkey run.
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
TIME_BUDGET = 32 * 60
# Taps that would wipe the state the rest of the crawl needs, or leave the app.
SKIP = re.compile(r"(?i)(delete|remove all|حذف|logout|log out|sign out|خروج از حساب|reset|بازنشانی|uninstall|"
                  r"clear all|پاک کردن همه|factory|telegram|تلگرام)")

# The bottom bar and top-bar buttons are on every screen; they are crawled
# once as roots, not again from inside every page.
CHROME = re.compile(r"^(Home|Shop|Settings|خانه|فروشگاه|تنظیمات)( \1)?$|^(Toggle theme|Back|بازگشت)$")
# Test profiles imported through the Config Center before the crawl, so the
# server list, details, edit and test flows have something to act on. The
# hosts are documentation addresses (RFC 5737): nothing is ever reached.
SEED = "\n".join([
    "vless://11111111-2222-3333-4444-555555555555@192.0.2.10:443?security=tls&sni=example.com&type=ws&path=%2F#crawl-vless",
    "trojan://pw@192.0.2.11:443?sni=example.com#crawl-trojan",
    "ss://YWVzLTI1Ni1nY206cHc@192.0.2.12:8388#crawl-ss",
    "hysteria2://pw@192.0.2.13:443?sni=example.com#crawl-hy2",
    "sstp://u:p@192.0.2.14:443#crawl-sstp",
])

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
    log("uiautomator dump failed: " + raw[:160].replace("\n", " "))
    return []


def signature(nodes):
    return "|".join(sorted({l for l, _ in nodes if not CHROME.match(l)}))[:400]


def same_screen(a, b):
    """Screens match when most of their labels do (counters and timers change)."""
    x, y = set(a.split("|")), set(b.split("|"))
    if not x and not y:
        return True
    return len(x & y) / max(1, len(x | y)) >= 0.6


SCREEN = [1080, 2400]


def read_screen_size():
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
    if m:
        SCREEN[0], SCREEN[1] = int(m.group(1)), int(m.group(2))


def swipe_up():
    w, h = SCREEN
    adb("shell", "input", "swipe", str(w // 2), str(h * 3 // 4), str(w // 2), str(h // 3), "300")
    time.sleep(0.8)


def animations(on):
    """uiautomator only reads a screen once it is idle, which endless
    animations never are: crawl with them off, stress with them on."""
    for k in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb("shell", "settings", "put", "global", k, "1" if on else "0")


def find_and_tap(label, scrolls=6):
    for _ in range(scrolls + 1):
        nodes = dict(dump())
        if label in nodes:
            adb("shell", "input", "tap", *map(str, nodes[label]))
            time.sleep(1.2)
            return True
        swipe_up()
    return False


def describe_screen(tag):
    """What is on screen when a crawl step finds nothing to tap: the foreground
    activity and the first texts of every package in the hierarchy."""
    log(f"{tag}: foreground {foreground() or '?'}")
    adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/ui.xml")
    raw = adb("shell", "cat", "/sdcard/ui.xml")
    start = raw.find("<?xml")
    if start < 0:
        log(f"{tag}: no hierarchy: " + raw[:200].replace("\n", " "))
        return
    try:
        root = ET.fromstring(raw[start:])
    except ET.ParseError as e:
        log(f"{tag}: unparsable hierarchy ({e})")
        return
    seen = []
    for n in root.iter("node"):
        t = (n.get("text") or n.get("content-desc") or "").strip()
        if t:
            seen.append(f"{n.get('package')}:{t[:30]}{'*' if n.get('clickable') == 'true' else ''}")
    log(f"{tag}: {len(seen)} labelled nodes: " + " | ".join(seen[:25]))


def print_anr():
    """The ANR record and the main thread's stack, into the job log itself."""
    am = adb("logcat", "-d", "-s", "ActivityManager:E", timeout=60)
    i = am.find("ANR in " + PKG)
    if i >= 0:
        print(am[i:i + 2500])
    traces = adb("shell", "ls /data/anr/ 2>/dev/null").split()
    for t in traces[-2:]:
        body = adb("shell", "cat", f"/data/anr/{t}", timeout=60)
        j = body.find('"main"')
        if j >= 0:
            print(f"==== /data/anr/{t} main thread\n" + body[j:j + 4000])


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
        if anr:
            print_anr()
        log(f"!!! CRASH after: {' > '.join(path)}\n    {summary.strip()}")
        with open(os.path.join(OUT, "crashes.txt"), "a") as f:
            f.write(f"===== after: {' > '.join(path)}\n{new}\n")
        return True
    return False


def back_to(sig, tries=3):
    for _ in range(tries):
        if same_screen(signature(dump()), sig):
            return True
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.9)
    return same_screen(signature(dump()), sig)


def replay(path):
    """Relaunches and taps the labels of path again; True when it got there."""
    adb("shell", "am", "force-stop", PKG)
    launch()
    if not wait_resumed(30):
        return False
    time.sleep(2)
    for label in path:
        if not find_and_tap(label):
            return False
    return True


screens = []  # (path, signature) of every screen reached


def explore(path, depth):
    if time.time() - started > TIME_BUDGET:
        return
    sig = signature(dump())
    if any(same_screen(sig, t) for t in seen_screens):
        return
    seen_screens.append(sig)
    screens.append((list(path), sig))
    log(f"screen depth={depth} path={' > '.join(path) or '(root)'}")
    if not sig:
        describe_screen("empty screen")
    count = 0
    for page in range(7):  # the visible part, then up to six scrolls down
        nodes = dump()
        cur = signature(nodes)
        fresh = [(l, xy) for l, xy in nodes if not CHROME.match(l) and not SKIP.search(l) and l not in done_labels.setdefault(sig, set())]
        for label, (x, y) in fresh:
            if count >= PER_SCREEN or time.time() - started > TIME_BUDGET:
                return
            done_labels[sig].add(label)
            count += 1
            before_pid, before_crash = pid(), crash_lines()
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(1.3)
            step = path + [label]
            log(f"tap: {' > '.join(step)}")
            if check(step, before_pid, before_crash):
                if not replay(path):
                    return
                break
            if not foreground().startswith(PKG + "/"):
                # Left the app (VPN dialog, browser, share sheet): come back.
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(1)
                if not foreground().startswith(PKG + "/") and not replay(path):
                    return
                break
            new_sig = signature(dump())
            if not same_screen(new_sig, cur):
                if depth < MAX_DEPTH:
                    explore(step, depth + 1)
                if not back_to(cur):
                    if not replay(path):
                        return
                    break
        before = signature(dump())
        swipe_up()
        if same_screen(signature(dump()), before) and signature(dump()) == before:
            break  # the end of the page


seen_screens = []
done_labels = {}


def explore_root(tab_label):
    """Explores a bottom-bar tab; replays reopen the tab first."""
    global replay
    plain = replay

    def via_tab(path):
        adb("shell", "am", "force-stop", PKG)
        launch()
        if not wait_resumed(30):
            return False
        time.sleep(2)
        nodes = dict(dump())
        if tab_label in nodes:
            adb("shell", "input", "tap", *map(str, nodes[tab_label]))
            time.sleep(1.5)
        for label in path:
            if not find_and_tap(label):
                return False
        return True

    replay = via_tab
    try:
        explore([], 0)
    finally:
        replay = plain


def seed():
    """Imports the test profiles through the Config Center, crawling it on the way."""
    path = f"/sdcard/Android/data/{PKG}/files/crawl-seed.txt"
    adb("shell", "mkdir", "-p", f"/sdcard/Android/data/{PKG}/files")
    local = os.path.join(OUT, "crawl-seed.txt")
    with open(local, "w") as f:
        f.write(SEED + "\n")
    adb("push", local, path)
    before_pid, before_crash = pid(), crash_lines()
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-t", "text/plain",
        "-d", "file://" + path, "-n", PKG + "/net.gozar.app.ConfigCenterActivity", timeout=60)
    time.sleep(3)
    if check(["(Config Center import)"], before_pid, before_crash):
        return
    log("Config Center: " + ", ".join(l for l, _ in dump())[:300])
    # Import whatever the Config Center offers, then crawl its screen.
    for l, xy in dump():
        if re.search(r"(?i)import|add|افزودن|وارد", l) and not SKIP.search(l):
            before_pid, before_crash = pid(), crash_lines()
            adb("shell", "input", "tap", *map(str, xy))
            time.sleep(2)
            check(["(Config Center)", l], before_pid, before_crash)
            break


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
            animations(True)
            adb("shell", "input", "tap", *map(str, nodes[path[-1]]))
            time.sleep(0.25)
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(0.1)
            adb("shell", "input", "keyevent", "KEYCODE_BACK") if len(path) > 1 else None
            time.sleep(0.6)
            animations(False)
            time.sleep(0.4)
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
    read_screen_size()
    animations(False)
    log(f"screen {SCREEN[0]}x{SCREEN[1]}")
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
    time.sleep(3)
    # Engines that load in-process report here (zeptun carries every sing-box profile).
    for line in adb("logcat", "-d", "-s", "GhajarZeptun:*", "GhajarSingBox:*").splitlines():
        if line.strip() and not line.startswith("-"):
            log("engine: " + line.strip()[:200])
    time.sleep(4)
    # First-run screens (intro, permission prompts) are part of the crawl.
    explore([], 0)
    seed()
    for tab in (["Home", "خانه"], ["Shop", "فروشگاه"], ["Settings", "تنظیمات"]):
        if time.time() - started > TIME_BUDGET:
            break
        adb("shell", "am", "force-stop", PKG)
        launch()
        wait_resumed(30)
        time.sleep(2)
        label = next((l for l, _ in dump() if CHROME.match(l) and l.split(" ")[0] in tab), None)
        if label is None:
            log(f"tab {tab[0]} not found")
            continue
        before_pid, before_crash = pid(), crash_lines()
        adb("shell", "input", "tap", *map(str, dict(dump())[label]))
        time.sleep(1.5)
        if check([label], before_pid, before_crash):
            continue
        explore_root(label)
    log(f"crawl done: {len(screens)} screens, {sum(len(v) for v in done_labels.values())} taps")
    stress()
    log("stress done")

    # Start random-input stress from a clean, focused MainActivity. The old
    # harness let Monkey inherit the launcher after force-stop/back races; on
    # the software-rendered emulator that produced a HardwareRenderer.setStopped
    # stall and an "Application does not have a focused window" ANR before
    # meaningful app input was exercised.
    adb("shell", "am", "force-stop", PKG)
    launch()
    if not wait_resumed(40):
        collect()
        print("::error::MainActivity did not resume before monkey stress")
        return 1
    time.sleep(2)
    animations(True)
    before_pid, before_crash = pid(), crash_lines()
    adb("shell", "monkey", "-p", PKG, "-s", "7",
        "--pct-touch", "60", "--pct-motion", "30", "--pct-pinchzoom", "10",
        "--pct-syskeys", "0", "--pct-appswitch", "0", "--pct-nav", "0",
        "--pct-majornav", "0", "--pct-trackball", "0", "--pct-anyevent", "0",
        "--throttle", "120", "--ignore-security-exceptions", "-v", "500", timeout=240)
    check(["(monkey 500 focused events)"], before_pid, before_crash)
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

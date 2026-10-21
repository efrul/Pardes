#!/usr/bin/env python3
"""Tap a UI element on the emulator by its text or content description (Hebrew works).

  emu-tap.py "הגדרות"        tap the first node whose text/content-desc equals the string
  emu-tap.py "~ערכת נושא"     '~' = substring match
  emu-tap.py "מפרשים" 1       tap the 2nd match
  emu-tap.py --list           print every labelled node with its bounds

Uses `uiautomator dump`, which can take 5-20 s under software emulation and fails while the app is busy
(ANR); retry after emu-wait.sh settle. Compose exposes Text() and contentDescription to uiautomator.
"""
import os, re, subprocess, sys

ADB = os.environ.get("ADB") or os.path.join(os.environ.get("ANDROID_HOME", "/opt/android-sdk"), "platform-tools", "adb")
want = sys.argv[1] if len(sys.argv) > 1 else "--list"
idx = int(sys.argv[2]) if len(sys.argv) > 2 else 0
subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
xml = subprocess.run([ADB, "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout.decode("utf-8", "replace")
if not xml:
    # Android 4.4 (the pardes19 AVD) has no `adb exec-out` ("error: closed"): pull the file instead.
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([ADB, "pull", "/sdcard/ui.xml", os.path.join(tmp, "ui.xml")], capture_output=True)
        if os.path.exists(os.path.join(tmp, "ui.xml")):
            xml = open(os.path.join(tmp, "ui.xml"), encoding="utf-8", errors="replace").read()
nodes = re.findall(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if want == "--list":
    for n in nodes:
        if n[0] or n[1]:
            print(repr(n[0]), repr(n[1]), n[2:])
    sys.exit(0)
contains = want.startswith("~")
if contains:
    want = want[1:]
hits = [n for n in nodes if want in (n[0], n[1]) or (contains and (want in n[0] or want in n[1]))]
if not hits:
    print("not found:", want, "(try --list, or wait for the screen to settle)")
    sys.exit(1)
t, d, x1, y1, x2, y2 = hits[min(idx, len(hits) - 1)]
x, y = (int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2
subprocess.run([ADB, "shell", "input", "tap", str(x), str(y)])
print("tapped", repr(t or d), x, y)

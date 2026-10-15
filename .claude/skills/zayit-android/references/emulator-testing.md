# Emulator testing in the cloud container

## Contents
- What to expect (timings)
- Start, wait, prep
- Putting a library on the emulator
- Screenshots, taps, deep links
- Navigation map (labels for emu-tap.py)
- Sizes: phone, Jelly Star, tablet
- A full "screenshot everything" pass
- Debugging crashes and slowness

## What to expect

The container has no `/dev/kvm`, so the x86_64 image runs under TCG (software CPU emulation).

| Step | Typical time |
|---|---|
| Cold boot (after `emulator-prep.sh` disabled the Google apps) | ~5 min (was 14 min before) |
| `adb install` of the 8 MB release APK | 30–90 s (dexopt speed-profile) |
| App cold start to home screen | 20–60 s |
| Opening a book / tab switch | 3–10 s |
| `uiautomator dump` | 5–20 s; fails while the app is busy |

Everything is 10–50× slower than a phone. Judge layout and behaviour on the emulator, not speed. If something is slow on the emulator, compare with how the code would run on a phone before "optimising" (but do fix real main-thread I/O).

The emulator runs as a background command, and background commands are killed after **2 hours**. Batch the screenshots you need. If it dies, tell the user and restart it.

## Start, wait, prep

```bash
S=.claude/skills/zayit-android/scripts
$S/emulator-start.sh                 # with run_in_background: true
```
Then wait with the **Monitor** tool. Foreground `sleep` is rejected, and Monitor streams the script's output and returns when it exits:
```
Monitor: .claude/skills/zayit-android/scripts/emu-wait.sh boot
```
Then:
```bash
$S/emulator-prep.sh                  # root, stay-on, no animations, no ANR dialogs, Google apps disabled
```
`adb` is at `/opt/android-sdk/platform-tools/adb`. Add it to PATH, or set `ADB=`.

## Putting a library on the emulator

The app looks for `seforim.db` in `files/library/` under external storage first, then internal storage. With `adb root` the cleanest route is internal storage owned by the app:

```bash
ADB=/opt/android-sdk/platform-tools/adb; P=io.github.efrul.pardes
$ADB push subset.db /data/local/tmp/seforim.db
U=$($ADB shell stat -c %u /data/data/$P | tr -d '\r')
$ADB shell "mkdir -p /data/data/$P/files/library && cp /data/local/tmp/seforim.db /data/data/$P/files/library/seforim.db \
  && chown -R $U:$U /data/data/$P/files && restorecon -R /data/data/$P/files && rm /data/local/tmp/seforim.db"
$ADB shell am force-stop $P
```

- **Index folders:** for Lucene search, copy them next to the DB the same way: `seforim.db.lucene/`, `seforim.db.lookup.lucene/`, `lexical.db`, `catalog.pb`. `adb push` handles directories.
- **The user's route:** `androidApp/tools/push-desktop-library.sh --source DIR` pushes to external storage and `chmod`s, with no root needed. Test it on the emulator with a subset library, without root, to cover the folder permissions and renames a real phone sees. That does not cover the copy step of the user's Windows script: `push-desktop-library.ps1` stages each file in `/data/local/tmp/zayit-push` with `adb push` and copies it into place with `cat`, because adb.exe cuts a stdin stream at the first 0x1A byte. Test changes to the `.ps1` on Windows (pitfalls.md › Copying into the app's folder without root).
- **Dev subset:** `androidApp/tools/make_subset_db.sh` builds the DB in ~1 min from the full DB. A matching subset Lucene index can be built with the desktop generator code; lucene-android.md says how.

## Screenshots, taps, deep links

- **Screenshots:** `emu-shot.sh <name>` saves to `$SHOTS` (default `/tmp/zayit-shots`). Read the PNG to look at it. Read downsizes large images and reports a factor; multiply by it to get the device pixels for `input tap`.
- **Tapping:** `emu-tap.py` beats coordinates. It finds Compose `Text` and `contentDescription` through uiautomator, so it survives layout changes and works with Hebrew labels. Use `--list` to see what is tappable.
- **Scrolling:** `adb shell input swipe 540 1600 540 600 300` (lists); a horizontal swipe for pagers.
- **Long-press** (line actions in the reader): `adb shell input swipe X Y X Y 800`.
- **Back:** `adb shell input keyevent KEYCODE_BACK`.
- **Typing Hebrew.** `adb shell input text` can't type Hebrew. Install the ADBKeyBoard test IME once per AVD:
  ```bash
  git clone --depth 1 https://github.com/senzhk/ADBKeyBoard /tmp/adbkb   # raw/ downloads are refused by the proxy, git clone works
  adb install -r /tmp/adbkb/ADBKeyboard.apk
  adb shell ime enable com.android.adbkeyboard/.AdbIME && adb shell ime set com.android.adbkeyboard/.AdbIME
  ```
  Then tap the field and type with `scripts/emu-type.sh "בראשית א ב"` (`--clear` deletes first, `--enter` sends the IME action). The script handles two traps:
  - `adb shell` joins its arguments into one device command line, so text with spaces must be quoted again for the device shell. Otherwise the broadcast silently types nothing.
  - ADBKeyBoard's InputConnection goes stale after the app restarts input (force-stop, a popup under the field). The script re-selects the IME first; if typing still does not land, tap the field again.

  A real keyboard (Gboard) is not affected; check it by tapping its keys when in doubt. Use `--ei code 66` with `ADB_INPUT_CODE` for Enter. If `ime enable` says "Unknown input method", wait a few seconds after the install and retry. Switch back with `ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`.
- **Deep links** (no keyboard needed):
  ```bash
  $ADB shell am start -a android.intent.action.VIEW -p io.github.efrul.pardes -d "zayit://book/1/line/5"
  q=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "בראשית ברא")
  $ADB shell am start -a android.intent.action.VIEW -p io.github.efrul.pardes -d "zayit://search?q=$q"
  ```
  Book and line ids are ids in `seforim.db` (`sqlite3 seforim.db "select id,title from book where title like '%ברכות%'"`). The subset keeps the full DB's ids.
- **Settling:** after any action, run `emu-wait.sh settle` (via Monitor) before the screenshot. Otherwise you capture a half-drawn frame.
  - The home screen never settles, because the search placeholder types itself in a loop.
  - Indeterminate progress bars can look settled while work is still running.
  - On those screens, give `settle` a short max (e.g. `settle 30`) or wait for something specific.

## Navigation map (labels for emu-tap.py)

Every label below is a Compose `Text` or a `contentDescription`, so `emu-tap.py "<label>"` finds it.

**Phone top bar.** "אפשרויות נוספות" (⋮) opens this menu:
- דף הבית
- חיפוש במאגר
- לשונית חדשה
- היסטוריה
- מועדפים
- ערכת נושא: … (cycles system → light → dark)
- כלים
- הגדרות

The tab counter (a number in a box) opens the tab switcher.

**Tablet (≥840dp).** The same actions are icons on the left of the tab strip, with the same descriptions.

**Reader, phone bottom toolbar:**
- תוכן (TOC drawer)
- מפרשים
- תרגומים
- מקור
- ניקוד
- עוד: an overflow with הגדל, הקטן, חפש בתוך העמוד, רשימת הספרים, הערות, פרטי הספר. Below 300dp, find-in-page moves here as well.

Long-press a line for the line actions (copy, link, note, "סמן" colours).

**Settings pages:**
- כללי
- תצוגה
- פרופיל
- גופנים
- ניהול נתונים
- אודות
- תנאים

**Deep links skip the navigation entirely.** Use `zayit://book/<id>/line/<id>` for a passage and `zayit://search?q=` for a search.

## Sizes

| Target | Commands | Notes |
|---|---|---|
| Pixel 6 phone (default) | `wm size reset; wm density reset` | 1080×2400 @420 ≈ 411dp wide |
| Jelly Star (3") | `wm size 480x854; wm density 320` | ≈240dp wide: `narrowPhone` (<300dp) kicks in |
| Tablet | `wm size 1920x1200; wm density 240` | 1280dp: wide desktop-like layout (≥840dp). Not 2560x1600: at that many pixels the SwiftShader renderer crashed qemu twice (segfault after "Failed to find ColorBuffer") |

All are `adb shell` commands. After a size change, Android may show "Viewing full screen", so dismiss it first. The activity is recreated; check that state (open tabs, scroll position) survives. Always reset the size at the end; the next person expects a phone.

## A full "screenshot everything" pass

This is the order that worked, with the screens the user expects to see. Pair each with the desktop shot (`contact-sheet.py`).

1. Onboarding: welcome, licence, disk, install type, download/extract progress, profile, region, done. These are seen only with no library: install fresh, or `pm clear`. Check the authorship texts, which differ from desktop on purpose. Where desktop says who made the app (the welcome step, the settings header, About, the terms), Pardes says instead that it is based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it; his copyright, AGPL and §7b notices are kept. The welcome step must say "ברוכים הבאים לפרדס!" and include the whole sentence "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה". The licence step, Settings › כללי and Settings › אודות also say he did not make it and show "Powered by the technologies that drive Zayit — https://zayitapp.com/".
2. Home: logo, search field, catalog row with a category dropdown open, zmanim cards. Do it in light and dark.
3. Library drawer (phone) / tree island (tablet).
4. Reader: Bereshit 1. Line selected; commentaries sheet with commentator chips and pager; targum; sources; notes; book info; long-press actions; find in page; TOC with the current chapter highlighted.
5. Search: results, filters, a result opened.
6. History and bookmarks.
7. Settings: each page.
8. Tools: each of the 10 tools.
9. Repeat the key screens at Jelly Star and tablet size.

Send the user contact sheets in batches as you go, not 40 separate files at the end.

## Debugging crashes and slowness

- **Crash:** `$ADB logcat -d -b crash | tail -50`. For a broader view, `$ADB logcat -d | grep -E "AndroidRuntime|FATAL|zayit|seforim" | tail -80`. Clear with `logcat -c` before reproducing.
- **ANR:** `$ADB shell ls /data/anr/`, then pull the latest trace and look at the `main` thread. Typical cause: DB or file I/O on the main thread.
- **Release-only crash with an obfuscated trace:** the mapping is at `androidApp/app/build/outputs/mapping/release/mapping.txt`. Decode with `retrace` from the build-tools/cmdline-tools. Usually it is a missing keep rule (pitfalls.md › Build and R8).
- **App data reset:** `$ADB shell pm clear io.github.efrul.pardes` wipes prefs and internal storage, including an internal library. External `files/` is wiped too.

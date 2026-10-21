# Emulator testing in the cloud container

## Contents
- What to expect (timings)
- Start, wait, prep
- Putting a library on the emulator
- Screenshots, taps, deep links
- Navigation map (labels for emu-tap.py)
- Sizes: phone, Jelly Star, tablet
- A full "screenshot everything" pass
- Testing the app update
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

**API 26 and 28.** AVDs `pardes26` and `pardes28` run `system-images;android-26;google_apis;x86_64` and `android-28` (google_apis, so `adb root` works): `AVD=pardes26 $S/emulator-start.sh`. Their `config.ini` sets `disk.dataPartition.path` to the AVD's own `userdata-qemu.img`, so the data survives restarts. Under TCG API 26 boots in under a minute. What differs there: `androidApp/docs/DEVELOPMENT.md` › Android 8 to 12.

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
- **The user's route:** `androidApp/tools/push-desktop-library.sh --source DIR` pushes to external storage and `chmod`s, with no root needed. Test it on the emulator with a subset library, without root, to cover the folder permissions and renames a real phone sees. That does not cover the copy step of the user's Windows script: `push-desktop-library.ps1` stages each file in `/data/local/tmp/zayit-push` with `adb push` and copies it into place with `cat`, because adb.exe cuts a stdin stream at the first 0x1A byte. Test changes to the `.ps1` on Windows (pitfalls.md › Copying into the app's folder without root). Below Android 7.0 (Pardes for Android 4.4, the `pardes19` AVD) both scripts `adb push` each file straight into the app's folder instead (there `adb shell` always has a terminal, which garbles a binary stream, and no `-T`), read sizes from toolbox `ls -l` and `df` (no stat, find or awk on 4.4), list cards from /proc/mounts and accept versionCode 1 for `--sdcard`.
- **Typing and deep links on the 4.4 AVD (pardes19):** the ADBKeyBoard APK of the repo registers its receiver only when its keyboard view is created, which Android 4.4 never does while the AVD's hardware keyboard is on: open the IME picker (`adb shell am broadcast -a android.settings.SHOW_INPUT_METHOD_PICKER`), switch "Hardware / Physical keyboard" off, tap the field, then `am broadcast -a ADB_INPUT_TEXT --es msg '...'` types Hebrew and `am broadcast -a ADB_EDITOR_CODE --ei code 3` presses Search (the DPAD keyevents keep working). 4.4's `am start` has no `-p`: open links with `-n io.github.efrul.pardes/io.github.kdroidfilter.seforimapp.legacy.ui.MainActivity -a android.intent.action.VIEW -d "zayit://search/<url-encoded>"`, which runs that search at once.
- **Install from files on the 4.4 edition (pardes19):** pack the dev subset as Zayit Export does (`release_info.txt` and `library_size.txt` first, then desktop's order, zstd, `.partNN` pieces with a format-1 manifest) and `adb push` it to `/sdcard/Download/<folder>`; the install screen lists it under "נמצאו במכשיר", or browse to it with DPAD keyevents and pick the `.part01` with DPAD_CENTER. A library folder pushed to `/sdcard/Download/databases` is moved in by renames (the app holds WRITE_EXTERNAL_STORAGE). After a run, `adb shell sync` before anything else: the AVD's card is a FAT image, and when the harness kills the emulator, files written or renamed in the last minutes can come back empty.
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

## Testing the app update

The updater (`update/AppUpdater`) looks for no update in a development build. Give it a test feed instead (the full recipe is in `androidApp/docs/DEVELOPMENT.md` › Testing the app update):

```bash
export MSYS_NO_PATHCONV=1          # Git Bash on Windows: keep /data/... paths as they are
P=io.github.efrul.pardes; T=pardes-android-0.3.1-99.apk
adb root && adb wait-for-device    # emulator; run-as cannot write there (the release build type is not debuggable)
adb shell am force-stop $P
adb push pardes-0.3.1.apk /data/local/tmp/$T
adb push feed.json /data/local/tmp/app-update-test-feed.json
adb shell "U=\$(stat -c %u /data/data/$P); D=/data/data/$P/files; mkdir -p \$D/app-update-test && mv /data/local/tmp/$T \$D/app-update-test/ && mv /data/local/tmp/app-update-test-feed.json \$D/ && chown -R \$U:\$U \$D && restorecon -R \$D"
# On a rooted phone (adbd not root): the same last command inside adb shell 'su -c "..."', and no adb root
```

`feed.json`, with the APK's exact size and sha256:

```json
{"releases":[{"tag_name":"android-0.3.1-99","prerelease":false,"assets":[{"name":"pardes-android-0.3.1-99.apk","size":25426050,"digest":"sha256:<hex>","browser_download_url":"file:///data/user/0/io.github.efrul.pardes/files/app-update-test/pardes-android-0.3.1-99.apk"}]}]}
```

The APK to update to needs a higher `versionName` and a `versionCode` not lower than the installed one. Expect: the snackbar "גרסה 0.3.1 של פרדס זמינה" (once per version: remove `announced_version` from `shared_prefs/app_update.xml` to see it again) and the badge at start; "הורד והתקן" in the dialog; the first time, the permission banner, whose "פתח הגדרות" opens the "install unknown apps" page (the install goes on after Back); then Android replaces the app with no question (`UPDATE_PACKAGES_WITHOUT_USER_ACTION`) and the app closes; on the next start "פרדס עודכן לגרסה 0.3.1". `adb shell dumpsys package io.github.efrul.pardes | grep -E "versionName|installerPackageName"` then shows the new version and Pardes as its installer. Remove the test files afterwards (`files/app-update-test*`); the build drops what it downloaded at its next start. Tested this way on the Jelly Star (Android 13), 2026-10-18: a refusal of a release-key APK, the permission page, cancel and approve in Android's confirmation, a silent update, and a downloaded APK offered again after the process was stopped.

On Android 11 (API 30) two steps differ: turning the permission on ends the app, so Back lands in a new process, which reopens the dialog and installs the checked APK by itself; and Android's confirmation ("Install") opens for every update, since `UPDATE_PACKAGES_WITHOUT_USER_ACTION` exists only from Android 12 (`androidApp/docs/DEVELOPMENT.md` › Android 8 to 12). On Android 8 to 10 only the second differs: the app keeps running when the permission is turned on.

## The API 30 emulator (Android 11)

`system-images;android-30;google_apis;x86_64`, AVD `zayit30` (`AVD=zayit30 scripts/emulator-start.sh`). What it took on 2026-10-18 under TCG:
- **system_server kept dying** with "Lost network stack": a debuggable build crashes on purpose when the network stack process is slow to answer. Run `adb shell device_config put connectivity always_ratelimit_networkstack_crash true` as soon as `service check device_config` answers (it persists).
- **Disable the Google apps only once the user is RUNNING_UNLOCKED** (`am get-started-user-state 0`): disabling them during boot crashed system_server (NPE in finishDisabledPackageActivities). emulator-prep.sh then works as on API 33.
- **Keep the data:** the AVD's config.ini had `disk.dataPartition.path=<temp>`; point it at the AVD's `userdata-qemu.img`. The data partition needs as much free disk as its size (`disk.dataPartition.size=6G` here).
- **Start the emulator with a 2-hour timeout** (Bash `run_in_background` with `timeout: 7200000`); without it the background command is killed at 30 minutes.
- **Typing:** with LatinIME disabled there is no keyboard; install ADBKeyBoard (above) and use emu-type.sh.
- **DocumentsUI ignores taps on files and folders** under TCG: move with keyevents (`61` Tab, `20` down, `66` Enter) and pick with Enter.
- **A subset bundle for "התקנה לא-מקוונת"** needs `library_size.txt` (`library=<bytes>`) as the tar's first entry, or the installer reserves the full library's 13 GB.
- **adb shell cannot reach `Android/data/<app>`** on 11 (not in `ext_data_rw`): the transfer scripts fail there; use root (`/data/media/0/Android/data/...`) or install from files.

## Debugging crashes and slowness

- **Crash:** `$ADB logcat -d -b crash | tail -50`. For a broader view, `$ADB logcat -d | grep -E "AndroidRuntime|FATAL|zayit|seforim" | tail -80`. Clear with `logcat -c` before reproducing.
- **ANR:** `$ADB shell ls /data/anr/`, then pull the latest trace and look at the `main` thread. Typical cause: DB or file I/O on the main thread.
- **Release-only crash with an obfuscated trace:** the mapping is at `androidApp/app/build/outputs/mapping/release/mapping.txt`. Decode with `retrace` from the build-tools/cmdline-tools. Usually it is a missing keep rule (pitfalls.md › Build and R8).
- **App data reset:** `$ADB shell pm clear io.github.efrul.pardes` wipes prefs and internal storage, including an internal library. External `files/` is wiped too.

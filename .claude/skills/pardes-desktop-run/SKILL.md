---
name: pardes-desktop-run
description: Running Pardes desktop (SeforimApp) built from this checkout, headless in the cloud container, and driving and screenshotting it: Xvfb, Mesa's software EGL for the Nucleus Tao window, a small library cut from the release or the whole one, the app's preferences set so it opens straight on Home (or on its install wizard), the Gradle wrapper every build here goes through, the click map, and the pitfalls already hit. Use it whenever a desktop change has to be seen in the real app, before any Gradle run in the container, or when the user asks to run, try or screenshot the desktop app, even in Hebrew ("תצלם את התוכנה", "יש צילומי מסך של התוכנה?", "תריץ את פרדס", "תראה לי איך זה נראה", "תבדוק בתוכנה"). Not for the released Zayit .deb (zayit-android's references/desktop-reference.md) or the Android app (zayit-android).
---

# Running Pardes desktop from this checkout

The app the user gets is the one built from this repository, so a desktop change is checked in it, not only in tests:
`./gradlew :SeforimApp:run` on a virtual display. Five things make that work in the cloud container, each found the
hard way; the scripts do them in order.

```bash
K=.claude/skills/pardes-desktop-run/scripts
$K/setup.sh                    # apt: Xvfb, xdotool, xclip, ImageMagick, sqlite3, zstd, Mesa EGL; Gradle's Central mirror
$K/make-library.sh 103 124     # the release, cut to Berakhot (103) and Bava Batra (124), into /home/user/pardes-lib
$K/start.sh                    # run_in_background: true. Xvfb :99 1440x900, the app, its window maximised
$K/shot.sh home                # -> /tmp/pardes-desktop-shots/home.png. Look at it before clicking on
$K/stop.sh                     # closes the app and waits for it to end: before any other Gradle run
$K/gradle.sh :SeforimApp:jvmTest ktlintCheck detekt     # every Gradle run here goes through this
```

## What each step is for

1. **EGL.** The main window is Nucleus's Tao window (`nucleus-fork/`, `TaoComposeSceneHostLinux`), which renders
   through EGL. Without `libEGL.so.1` the app shows a "Fatal Error" window with `Failed to create EGL context for
   XID=…`. Mesa's EGL with llvmpipe renders in software on Xvfb: `setup.sh` installs it, and `start.sh` sets
   `LIBGL_ALWAYS_SOFTWARE=1`. `eglinfo -B` on `DISPLAY=:99` should name `llvmpipe`.
2. **The library.** The full `seforim.db` is 8 GB. `make-library.sh` streams the release without the full-text index
   (zayit-android's `fetch-desktop-library.sh`), keeps the books given and their commentaries and targumim
   (`androidApp/tools/make_subset_db.sh`, about 50 MB a tractate), copies the lookup index, `lexical.db` and
   `catalog.pb`, and deletes the full database. The ids are the full database's:
   `sqlite3 seforim.db "select id, title, categoryId from book where title = 'ברכות'"` (the Bavli's tractates are
   in categories 14 to 19). It also copies `/home/user/pardes-rashi-ocr/layouts/*.json` into `pardes-daf/`, for the
   daf view. See "The whole library" for the work a cut cannot show.
3. **The preferences.** Without them the app opens on its onboarding. Pardes's preferences are java.util.prefs,
   node `io/github/efrul/pardes` (`Brand.settings()`), stored in `~/.java/.userPrefs/io/github/efrul/pardes/prefs.xml`.
   `start.sh` writes `onboarding_finished`, `database_path` (the library's `seforim.db`), a region in Israel, and
   turns off the semantic pack prompt and the update check, which would open windows over the main one. It replaces
   what an earlier run saved there. More preferences, or other values, go in `PREFS`, as `key=value` words:
   `PREFS="open_siddur=true daf_marks=true" $K/start.sh`. The keys are `AppSettings.kt`'s `KEY_*` constants.
4. **The locale.** The container's locale is POSIX. With it, Gradle cannot write test classes whose names have
   non-ASCII characters (`InvalidPathException: Malformed input`), xdotool cannot type Hebrew, and window titles
   read `(failure in conversion from UTF8_STRING to ANSI_X3.4-1968)`. The scripts set `LC_ALL=C.UTF-8`
   (`./gradlew --stop` once if a daemon was started without it).
5. **Gradle, through `gradle.sh`.** It sets the locale, puts a `gh` that fails first on `PATH` (`settings.gradle.kts`
   asks `gh` for a token to Zayit's private packages, which a Pardes build must never read), and refuses to run
   while the app runs. `setup.sh` also copies `central-mirror.init.gradle.kts` into `~/.gradle/init.d/`: Maven
   Central answers 429 through the container's shared address, and the script puts Google's mirror of it first, for
   this container only.

## The whole library

A cut library has few books, no author rows for some, links of two kinds only (`CONN_TYPES=1,3`), and the full
release's lookup index. Search, suggestions, the catalog's order, links of the other kinds, the siddur (it reads the
library's siddurim and machzorim) and anything measured need the whole release:

```bash
bash .claude/skills/zayit-android/scripts/fetch-desktop-library.sh --dest /home/user/lib-full   # with the full-text index
LIB=/home/user/lib-full $K/start.sh
```

- It takes about 12.5 GB (database 7.5, index 4.7), and Pardes's semantic pack beside it 2.7 more (installed from
  Settings › ניהול נתונים). The disk is a fixed allowance: check `df -h /` first. `/dev/shm` is 16 GB of memory, for a
  large file that need not last.
- `make-library.sh` deletes the full database it cut from: don't run it on a folder you mean to keep whole.
- Tests that read a real library take it from the environment and are skipped without it:
  `SEFORIM_DB=/home/user/lib-full/seforim.db $K/gradle.sh :SeforimApp:jvmTest --tests "*features.siddur*"`.
- Run SQL checks with `sqlite3 "file:/home/user/lib-full/seforim.db?mode=ro"`.

## Driving it

Coordinates on the maximised 1440x900 window:

| Target | x, y |
|---|---|
| Top bar, from the left: settings, theme, tools menu, history, notes, bookmarks, search, home | 20, 60, 100, 140, 180, 220, 260, 300 at y 20 |
| Close the window (`stop.sh` does it and waits) | 1420, 20 |
| Home's "תלמוד בבלי" chip, then its first tractate (ברכות) | 733, 156, then 758, 218 |
| The reader's end bar (left in RTL): zoom in / out, ניקוד (סימונים while a daf is shown), צורת הדף | 33, 78 / 33, 145 / 33, 215 / 33, 285 |
| The daf view's header: previous amud (right) / next amud (left) | 987, 66 / 93, 66 |
| Table of contents entries (right pane) | 1105, 100 + 33 a row |
| The tools menu's rows (the siddur first when it is on) | 127, 59 + 25 a row |
| Settings (a 700x500 window at +370+200): its pages from כללי down, its "סגור" | 1000, 270 + 34 a row / 422, 669 |
| The siddur: the day's name (opens the date picker), the first part in the contents | 1000, 67 / 1304, 99 |

- A row of controls moves with the width of the text before it (the siddur's day arrows, a settings card below an
  opened one): take a screenshot and read the place, don't reuse yesterday's.
- **The first click after the window appears is often lost**, and so is a click on the top bar right after typing in
  the search field: click once in the page (say 300, 400), then on the target, and look at a screenshot before the
  next step.
- Hebrew: `xdotool type --delay 100 "בבא בתרא"`; keys `xdotool key ctrl+t`, `Return`, `Escape`, `Down`, `Next`
  (PageDown), `Prior`. To replace a field's text, clear it with `BackSpace`s: `ctrl+a` then typing once gave
  `||||`.
- **`xdotool key ctrl+v` does not paste** (it types letters): type the text. Read the clipboard with
  `xclip -selection clipboard -o -t UTF8_STRING`, a few seconds after the copy (llvmpipe).
- **A new window gets no keyboard focus** (Xvfb has no window manager): `xdotool windowfocus <id>`, then
  `xdotool key --window <id> …` or `type --window <id> …`. `xwininfo -root -tree` lists the windows; the main one is
  `xdotool search --name "פרדס"`.
- Wheel: `xdotool mousemove x y` over the list, then `click 5` (down) or `click 4`, some 10 to 60 times.
- **Read small text only from a crop**: `CROP=700x500+370+200 $K/shot.sh name`. A status line misread from the whole
  screenshot once sent a check after a bug that was not there.
- Send what you show the user with SendUserFile, one Hebrew line of caption each.

## The install wizard

```bash
INSTALL_DIR=/dev/shm/pardes-install $K/onboarding.sh     # run_in_background: no library, the onboarding not done
$K/onb.sh to-disk                # welcome, the licence, on to the disk-space step
$K/onb.sh click 360 420 8        # the wizard's own coordinates; then onb.sh shot <name>
$K/stop.sh wizard
```

The wizard is a 720x460 window at +360+220. Its folder chooser is a Swing dialog here: `onb.sh pick <path>` types
into it. Only `/dev/shm` has room for a library the wizard installs. Every step needs its answer to go on (the region
step asks a country and a city), so a real user always has a region; a profile written by hand may not.

## Seeing a composable without the app

For the look of one composable (a dialog, a row, an icon), a scratch test renders it in seconds:
`runDesktopComposeUiTest { setContent { … }; onRoot().captureToImage().toAwtImage() }`, written out with
`ImageIO.write`. The theme there is Jewel's default (blue, not Pardes's gold). Delete the test before committing, and
its stale report (`rm -f SeforimApp/build/reports/ktlint/*/*.txt`) if ktlint then complains of a file that is gone.

## Pitfalls

- **Close the app before any Gradle run** (`stop.sh`, then `gradle.sh`): a build rewrites the jars the running app
  loads from, and the app then dies with `NoClassDefFoundError` at the next screen it opens, which looks like a bug of
  the change being tested.
- **A profile without a region is reckoned as abroad**, at Jerusalem's coordinates
  (`HomeUserLocationViewModel`: `inIsrael` is the stored country's). The siddur then shows the diaspora's calendar
  ("ותן ברכה" in Cheshvan, a second day of Yom Tov). `start.sh` writes Jerusalem; give another with `PREFS`.
- `start.sh` writes the preferences anew at every start: what a run changed in the settings is gone at the next,
  unless it is in `PREFS`. The session (the open tabs, the desktops) is kept in the data folder and does come back:
  close the tabs a check opened.
- Data made for a check (a sample Zayit under `~/.local/share/io.github.kdroidfilter.seforimapp`, an Otzaria under
  `~/.local/share/otzaria`, a `libraries-in-place.txt` record) is removed once the check is done.
- `pgrep -f "Xvfb :99"` (or any pattern) also matches the shell running it, whose command line holds the pattern:
  test the display with `xwininfo -root`, and use `pgrep -f "[a]lign_lines"`-style patterns.
- `pgrep -f` with Hebrew in the pattern finds nothing in the POSIX locale (the command lines read as `?`).
- Foreground `sleep` loops are refused by the harness: wait with `timeout N bash -c 'until …; do sleep 5; done'`
  or a background command. A background task has a time limit too: give a long run `timeout: 7200000`.
- llvmpipe is slow: a frame can take a second; give a screen 3 to 8 s after a click before the screenshot, 15 after
  opening a book, a search or the siddur. A menu item "missing" from a screenshot was twice only a frame not drawn yet.
- `shot.sh name 0 0 5` clicks at (0, 0): pass no coordinates to wait without a click.
- The cut library's lookup index is the full release's: the Home search suggests books the subset does not have,
  which fail to open. Open books through the catalog chips or the book tree instead.
- The working directory resets between commands: give absolute paths, or `cd` in the same command.
- A benchmark is run alone: mockk loaded in the same JVM slows the real repository's calls.

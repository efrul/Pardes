---
name: pardes-desktop-run
description: Running Pardes desktop (SeforimApp) built from this checkout, headless in the cloud container, and driving and screenshotting it: Xvfb, Mesa's software EGL for the Nucleus Tao window, a small library cut from the release, the app's preferences set so it opens straight on Home, the click map, and the pitfalls already hit. Use it whenever a desktop change has to be seen in the real app, or the user asks to run, try or screenshot the desktop app, even in Hebrew ("תצלם את התוכנה", "יש צילומי מסך של התוכנה?", "תריץ את פרדס", "תראה לי איך זה נראה"). Not for the released Zayit .deb (zayit-android's references/desktop-reference.md) or the Android app (zayit-android).
---

# Running Pardes desktop from this checkout

The app the user gets is the one built from this repository, so a desktop change is checked in it, not only in tests:
`./gradlew :SeforimApp:run` on a virtual display. Four things make that work in the cloud container, each found the
hard way; the scripts do them in order.

```bash
K=.claude/skills/pardes-desktop-run/scripts
$K/setup.sh                    # apt: Xvfb, xdotool, ImageMagick, sqlite3, zstd, Mesa EGL (idempotent)
$K/make-library.sh 103 124     # the release, cut to Berakhot (103) and Bava Batra (124), into /home/user/pardes-lib
$K/start.sh                    # run_in_background: true. Xvfb :99 1440x900, the app, its window maximised
$K/shot.sh home                # -> /tmp/pardes-desktop-shots/home.png. Look at it before clicking on
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
   daf view.
3. **The preferences.** Without them the app opens on its onboarding. Pardes's preferences are java.util.prefs,
   node `io/github/efrul/pardes` (`Brand.settings()`), stored in `~/.java/.userPrefs/io/github/efrul/pardes/prefs.xml`.
   `start.sh` writes `onboarding_finished`, `database_path` (the library's `seforim.db`) and turns off the semantic
   pack prompt and the update check, which would open windows over the main one. It replaces what an earlier run
   saved there.
4. **The locale.** The container's locale is POSIX. With it, Gradle cannot write test classes whose names have
   non-ASCII characters (`InvalidPathException: Malformed input`), xdotool cannot type Hebrew, and window titles
   read `(failure in conversion from UTF8_STRING to ANSI_X3.4-1968)`. The scripts set `LC_ALL=C.UTF-8`; set it for
   any Gradle run too (and `./gradlew --stop` once if a daemon was started without it).

## Driving it

Coordinates on the maximised 1440x900 window (the first click after a resize can miss: wait 3 s after it):

| Target | x, y |
|---|---|
| Settings / theme (click twice: light, then dark) | 20, 20 / 60, 20 |
| Home's "תלמוד בבלי" chip, then its first tractate (ברכות) | 733, 156, then 758, 218 |
| The reader's end bar (left in RTL): zoom in / out, ניקוד, צורת הדף | 33, 78 / 33, 145 / 33, 215 / 33, 285 |
| The daf view's header: previous amud (right) / next amud (left) | 987, 66 / 93, 66 |
| Table of contents entries (right pane) | 1105, 100 + 33 a row |

- Hebrew: `xdotool type --delay 100 "בבא בתרא"`; keys `xdotool key ctrl+t`, `Return`, `Escape`.
- Windows: `xwininfo -root -tree` lists them; the main one is found with `xdotool search --name "פרדס"`.
- Send what you show the user with SendUserFile, one Hebrew line of caption each.

## Pitfalls

- `pgrep -f "Xvfb :99"` (or any pattern) also matches the shell running it, whose command line holds the pattern:
  test the display with `xwininfo -root`, and use `pgrep -f "[a]lign_lines"`-style patterns.
- `pgrep -f` with Hebrew in the pattern finds nothing in the POSIX locale (the command lines read as `?`).
- Foreground `sleep` loops are refused by the harness: wait with `timeout N bash -c 'until …; do sleep 5; done'`
  or a background command.
- llvmpipe is slow: a frame can take a second; give a screen 3 to 8 s after a click before the screenshot.
- The library's lookup index is the full release's: the Home search suggests books the subset does not have, which
  fail to open. Open books through the catalog chips or the book tree instead.

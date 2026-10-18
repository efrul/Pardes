---
name: zayit-android
description: Working on Pardes (פרדס), the Android app based on Zayit, formerly the unofficial Android port "זית לאנדרואיד" (the `androidApp/` folder of this repo, Android 11+ / minSdk 30), which mirrors the desktop app's screens and reuses SeforimLibrary. Covers building the APK, setting up the SDK and a headless emulator in a cloud container, installing and screenshotting the app, getting a library onto a device, desktop/Android comparisons, offline Lucene search on Android, the desktop→phone transfer script, Jelly Star and tablet layouts, and RTL/Compose pitfalls already solved once. Use it for any task touching androidApp, an APK, adb, an emulator, an Android screenshot, a phone or tablet layout, "make it look like desktop", or Android search, even when the request doesn't say "Android" outright (e.g. "תצלם את המסך", "תתקין על האמולטור", "למה החיפוש לא עובד בטלפון", "Jelly Star").
---

# Pardes, the Android app based on Zayit

`androidApp/` is a standalone Gradle build that ports desktop Zayit to Android 11+ as Pardes, an app of its own based on Zayit (minSdk 30, lowered from 33 after 0.3.0; don't raise it). Android 12 and later must behave as they did with minSdk 33: calls newer than API 30 are guarded (Things that will bite you › API levels).
- **Shared code:** SeforimLibrary `core` and `dao` (plus `htmlparser` and the desktop `.sq` files) are compiled in unchanged through `kotlin.srcDir`.
- **UI:** a Jetpack Compose redraw of the desktop "Islands" layout, for phones from the 3" Unihertz Jelly Star up to tablets.
- **Language:** the UI is Hebrew and RTL, with strings copied from desktop, except the name and the authorship texts (see Authorship and name).

The goal throughout is **parity with desktop**. When unsure how something should look or behave, check desktop (code first, then a desktop screenshot) before inventing. One exception: the name and authorship (next section).

Where things are:
- [androidApp/README.md](../../../androidApp/README.md): build and library setup.
- [androidApp/DESIGN.md](../../../androidApp/DESIGN.md): the screen-by-screen design mapping.
- [androidApp/docs/DEVELOPMENT.md](../../../androidApp/docs/DEVELOPMENT.md): history and status of the port.
- This skill: how to work on the app quickly and without repeating solved problems.

## Authorship and name (don't regress)

Desktop Zayit and SeforimLibrary are by Elie (Elyahou) Gambache, under AGPL-3.0 with a §7b attribution term (`/LICENSE`). This Android app is not his. On 2026-10-15 he asked forks not to use Zayit's name or icon and to say instead that they are based on Zayit, so the app is **Pardes (פרדס)**, with its own icon: the word פרדס in two lines (פר / דס), gold on olive green.
- **Name:** "פרדס" / "Pardes" everywhere the app names itself: the launcher label (`app_name`), top bar, Recents, widgets, onboarding, settings, אודות, תנאים, notifications, script messages, release titles and images, the site and the READMEs. Never call it "זית", "Zayit" or "זית לאנדרואיד", and don't bring back Zayit's logo, olive branch or icon (the `zayit_*.png` drawables and olive mipmaps were deleted on purpose).
- **Icon:** vectors only, made by `scripts/pardes-icon.py` from Frank Ruhl Libre Black (SIL OFL) outlines: `drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` (the adaptive icon, with a themed-icon layer); `drawable/pardes_app_icon.xml`, both layers as one picture (the כללי header, the onboarding welcome, the search results bar); `androidApp/site/img/icon.svg` and its PNGs (the site). Its colours are the `pardes_*` entries in `values/colors.xml`. The Home logo is the word in the same font, painted like desktop's lettering.
- **Say:** every text about who made the app says Pardes is based on Zayit by Elie (Elyahou) Gambache and is not on his behalf: he did not make it, is not responsible for it and gives it no support. Reuse the existing sentences (`AndroidStrings.welcomeSubtitle`, `AppHeaderCard`, `ABOUT_MD`, `CONDITIONS_MARKDOWN`, `DEFAULT_FOOTER` in `scripts/release-frame.py`).
- **Zayit stays where it means Zayit:** "זית למחשב" and desktop Zayit (the desktop app, as in the transfer instructions), "מבוסס על זית", links to kdroidFilter's projects as credit to the original, the `zayit://` link scheme (the format desktop Zayit and the library data use, not this app's name), and the §7b line.
- **Never copy** desktop's "נוצר על ידי אליהו גמבש", "שנבנה באהבה על ידי אברך ומפתח חרדי", CONDITIONS' "הוענק לנו" / "הגרסאות הרשמיות כוללות", or his first-person ABOUT.md (it invites readers to contact him) as describing this app.
- **Keep** his copyright line (Copyright (C) 2025 Elie Gambache), the AGPL notice, and the §7b line "Powered by the technologies that drive Zayit — https://zayitapp.com/" word for word (כללי header, אודות, תנאים, READMEs, site, release notes). No search-and-replace may touch them.
- **Source (AGPL):** the app links its own source, https://github.com/efrul/Pardes (כללי header, אודות, תנאים), and every release attaches the complete source with the SeforimLibrary submodule (GitHub's own archives leave it out). Don't name the porter.
- **Rashi Amiti:** its licence covers the Zayit app only, so Pardes neither bundles nor downloads it, and on 2026-10-15 it was also deleted from the desktop copy in this repository (FontCatalog no longer offers it). Don't add it back.
- **Temple widget picture:** a crop of David Roberts's "The Siege and Destruction of Jerusalem" (1850, public domain), made by `scripts/temple-picture.py` (the still picture, desktop's three JPGs, and the animated WebP whose fires flicker). It replaced desktop Zayit's Shutterstock picture ("No use without permission") on 2026-10-15. Don't bring that one back; any new picture must be free to use and credited in רישיונות.
- **Licences (Settings › רישיונות, `ui/settings/LicencesSettings.kt`):** every third-party thing the APK carries is listed there with its licence text in `app/src/androidMain/assets/licenses/` (copied word for word; AGP drops the dependencies' own META-INF licence files). A new dependency, icon set, font or data file gets its credit and text there in the same change; after a font change run `scripts/font-notices.py androidApp`. The repository LICENSE and Lucene's LICENSE.txt/NOTICE.txt (`lucene-android/`) are copied in by the build, not duplicated. The AGPL link of תנאים and the onboarding opens that bundled LICENSE.
- **Unchanged:** the code's namespace and package names (`io.github.kdroidfilter…`) and internal identifiers nobody sees (`ZayitTheme`, `Zayit.colors`, the `zayit_*` preference files, the `.zayit-*` transfer markers), the holiday names, and the data sources (SeforimLibrary releases, `release_meta.json`, the Sefaria/Otzaria report addresses). Keep them working and credited.
- **applicationId:** `io.github.efrul.pardes` since 2026-10-15. Before it came `io.github.efrul.zayit`, and before that test builds in the original author's namespace. A device may still have one of those: it is a separate app with its own library, which is why the adb commands below name the package. Don't change the id back. Every build is signed with `androidApp/debug.keystore` (CN=Pardes Android Debug), public on purpose.
- **Repository:** `efrul/Pardes` (named `efrul/Zayit` until 2026-10-15; GitHub redirects the old repository and git URLs, but not the old Pages address), with the site at https://efrul.github.io/Pardes/.

## Talking to the user

- **Language:** answer in Hebrew. Keep technical identifiers in English.
- **Screenshots:** the user wants to see progress. Send screenshots (SendUserFile) as you go, not only at the end: after a new screen, after a visual fix, and as desktop-vs-Android sheets made with `scripts/contact-sheet.py`.
- **Emulator restarts:** the user has approved restarting the emulator whenever it stops (the 2-hour background limit). Restart it with `emulator-start.sh` and say so; there is no need to ask first.
- **Git:**
  - Commit with `androidApp: <what changed>` messages.
  - Push to the session's designated branch.
  - Open a PR only when asked.
  - **Download site:** `androidApp/site/` (Hebrew, one page) is deployed to https://efrul.github.io/Pardes/ by `.github/workflows/android-site.yml` on every push that changes it, with the transfer scripts copied to `tools/` and `push-desktop-library-windows.zip` (the `.ps1` with its `.cmd` launcher) built there. On every visit it reads the releases from the GitHub API (the version, the APK and the downloads of every release's APK), and falls back to `releases.json`, which the deploy writes, when the API can't be reached; a new release needs no site change. Its changelog is `androidApp/CHANGELOG.md`, deployed as `changelog.md`. The app's own updater (`update/`) reads the same three things: the API's release list, `releases.json` and `changelog.md`, so their shapes and the `## <version> · <date>` headings are a contract. Keep its texts under the Authorship and name rules above.
  - **CI release:** only by hand, never on a push (the user's choice): Run workflow on `main` (the default branch), or `gh workflow run android-release.yml -R efrul/Pardes --ref main` when the user asks for a build. It runs `.github/workflows/android-release.yml`. It signs the APK with Pardes's own key from the repository's secrets (PARDES_KEYSTORE_BASE64, PARDES_KEYSTORE_PASSWORD, PARDES_KEY_ALIAS, PARDES_KEY_PASSWORD) and stops unless the APK's certificate is RELEASE_CERT_SHA256; other builds keep the public debug key. Zayit's desktop workflows (deploy.yaml, pages.yaml, update-download-count.yaml) and FUNDING.yml were removed: they would publish Zayit-branded installers or Zayit's site from this repository, and the Sponsor button named the original author. Don't bring them back. The workflow does: release build, the check-api33 gate (must be 0 missing), then a release marked Latest (not a pre-release, which the repository sidebar never shows), `android-<versionName>-<run>`, with `pardes-<tag>.apk`, `pardes-<tag>-source.tar.gz` (the complete source), `push-desktop-library-windows.zip` (`.ps1` + `.cmd`) and `push-desktop-library.sh`. Before a run, add the version's section to `androidApp/CHANGELOG.md` (`## <versionName> · <date>`, in Hebrew, what changed for the user) and raise `versionName` and `versionCode` in `app/build.gradle.kts`: the release notes open with that section, and the run stops before the build without it. Installed releases update themselves from these releases, and offer one only when its `versionName` (digits and dots only) is higher: a second run of the same `versionName` reaches the site but no installed app. Never rename the tag, the APK (`pardes-<tag>.apk`) or the workflow. Prefer it to a local build; follow it with `gh run watch` (as below) and give the user the release link. Don't push a workflow-file change while a release run is in progress. The run would then tag a commit whose workflows no longer match any branch tip, and GitHub refuses GITHUB_TOKEN that tag (HTTP 403 "Resource not accessible by integration", as happened to run #2). Start a new run; a re-run would tag the same old commit.
  - The repository is `efrul/Pardes` (public: AGPL needs the source public wherever the APK is distributed). Commit as `efrul <342689225+efrul@users.noreply.github.com>`, and run gh as efrul.

## Fresh session: from zero to a running app

Everything below runs in a cloud container with no KVM, so the emulator is software-emulated and slow. Plan around the wait times; they are the main cost of this work.

Check `df -h /` first. Disk is a fixed allowance, not the size `df` suggests:
- SDK + system image: ~6 GB.
- Full library: 8 GB for `seforim.db`, or 13.1 GB with the 4.95 GB full-text index.
- Delete big intermediates (bundle parts, unused full DBs) once you have a subset.

1. **SDK.** Check `/opt/android-sdk` and `androidApp/local.properties`. If either is missing, run `scripts/setup-android-sdk.sh`. It is idempotent: it installs SDK 36, build-tools, the emulator and the API-33 google_apis image, then creates AVD `zayit33`.
2. **Emulator.** Start it early because boot takes 5–15 min.
   - Run `scripts/emulator-start.sh` with `run_in_background: true`.
   - Watch `scripts/emu-wait.sh boot` with the Monitor tool. Foreground `sleep` is blocked, and Monitor streams the result.
   - After boot, wait for `scripts/emu-wait.sh stable` too: under software emulation system_server usually crashes and restarts once right after boot, and an install during that window fails with "Can't find service: package". Then run `scripts/emulator-prep.sh` once.
3. **Build** while the emulator boots. It takes about 2 min warm and longer cold:
   ```bash
   cd androidApp && ./gradlew :app:assembleRelease --console=plain
   # -> app/build/outputs/apk/release/app-release.apk (R8, ~8 MB, signed with the debug key)
   ```
   Prefer release over debug for the emulator. The debug APK is ~68 MB and unoptimised, which made every screen take seconds under TCG.
4. **Install:** `adb install -r app/build/outputs/apk/release/app-release.apk`.
5. **Library.** Without one the app opens on onboarding (the desktop first-run flow). See "Library on the device" below.
6. **Launch:** `adb shell am start -n io.github.efrul.pardes/io.github.kdroidfilter.seforimapp.android.MainActivity`. Then run `scripts/emu-wait.sh settle` and take a shot with `scripts/emu-shot.sh home`.

Background commands are killed after 2 hours, and that includes the emulator. When it dies, say so and restart it with steps 2 and 6. The data on the AVD survives because `-no-snapshot-save` only skips snapshots.

## Inner loop on the emulator

| Need | How |
|---|---|
| Screenshot | `scripts/emu-shot.sh <name>` then Read the PNG. Read reports a scale factor; multiply by it to get device coordinates. |
| Tap by label (Hebrew OK) | `scripts/emu-tap.py "הגדרות"`; `"~substring"`; `--list` dumps every labelled node |
| Find a screen | Phone: "אפשרויות נוספות" (⋮) → היסטוריה / מועדפים / כלים / הגדרות / ערכת נושא. The full label map is in emulator-testing.md › Navigation map |
| Tap by coordinates | `adb shell input tap X Y`; swipe/scroll with `input swipe x1 y1 x2 y2 300` |
| Wait for the UI | `scripts/emu-wait.sh settle` (two identical screenshots 6 s apart) via Monitor |
| Open a passage | `adb shell am start -a android.intent.action.VIEW -p io.github.efrul.pardes -d "zayit://book/<bookId>/line/<lineId>"` |
| Search for Hebrew text | `adb shell am start -a android.intent.action.VIEW -p io.github.efrul.pardes -d "zayit://search?q=<url-encoded>"`. To type Hebrew into a focused field: `scripts/emu-type.sh [--clear] [--enter] "בראשית ברא"` (needs ADBKeyBoard, see emulator-testing.md). |
| Phone / Jelly Star / tablet | `adb shell wm size 480x854 && adb shell wm density 320` (Jelly Star ≈240dp wide); `wm size 1920x1200` + density 240 (tablet, 1280dp, ≥840dp = wide layout); `wm size reset; wm density reset` |
| Dark mode | Settings in the app (תצוגה › ערכת נושא), or the top-bar theme toggle |
| Crash | `adb logcat -d -b crash` and `adb logcat -d | grep -E "AndroidRuntime|FATAL|seforim"` |
| Restart the app | `adb shell am force-stop io.github.efrul.pardes` then `am start` |
| Release / store images | `scripts/emu-demo.sh on` (clean status bar, no ADB keyboard chip), shoot, `emu-demo.sh off`; then `scripts/release-frame.py manifest.json out/` frames each shot in a device under a Hebrew caption and writes `overview.png`. Keep the set in `androidApp/docs/release/`. A shot already taken without demo mode: `scripts/statusbar-clean.py in.png out.png` redraws its status bar (clock 10:00, battery). Every image carries the footer "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו" (release-frame.py draws it by default; the overview repeats it in large type), because images are shared one by one. No caption calls the app Zayit: it is פרדס, and desktop is "זית למחשב", not "גרסת המחשב". |

MainActivity is `singleTask`, so a deep link sent to a running app arrives through `onNewIntent` and opens a tab.

[references/emulator-testing.md](references/emulator-testing.md) has the details:
- timings
- why the Google apps are disabled
- uiautomator quirks
- a full-screen checklist for a "screenshot everything" pass
- how to verify a layout at three sizes

## Library on the device

The app reads the desktop library format: `seforim.db`, plus the Lucene index folders, `lexical.db` and `catalog.pb` next to it. LibraryStore picks whichever of these folders already holds `seforim.db`, the chosen location (prefs file `library_location`) first:
- `<external files>/library/`, which is `/sdcard/Android/data/io.github.efrul.pardes/files/library/`
- on a memory card, `/storage/<UUID>/Android/data/io.github.efrul.pardes/files/library/` (`LibraryLocation`)
- `<internal files>/library/`

On a FAT32 card the database is split in SQLite multiplex chunks (`seforim.db`, `seforim.db001`…, `SplitDatabase.kt`), read through requery's SQLite with `libpardes_multiplex.so`. To test cards on the emulator, give it a virtual SD card as root: `truncate -s 1536M /data/misc/vold/virtual_disk; sm set-virtual-disk true`, then `sm partition <disk id from sm list-disks> public` (vfat), `sm unmount`/`sm mount <volume id>` to take it out and put it back, `sm set-virtual-disk false` to remove it. Without the truncate the disk is 512 MB.

Pick one of three routes:

- **Dev subset (fast, ~150 MB).**
  - `androidApp/tools/make_subset_db.sh <full seforim.db> subset.db` keeps Bereshit, Berakhot, Mishnah Berakhot and their commentaries and targumim.
  - It needs a full DB. Get one with `scripts/fetch-desktop-library.sh`, which streams the 5.5 GB release with no stored archive. Pass `--no-fulltext` to skip the 4.95 GB index when disk is tight; check `df -h /` first.
  - On this machine the desktop library may already be in `~/.local/share/io.github.kdroidfilter.seforimapp/databases/`.
- **Transfer script.** `androidApp/tools/push-desktop-library.sh [--source DIR] [--dry-run]` (and `.ps1` for Windows, started by double-clicking `push-desktop-library.cmd`) copies an installed desktop library over adb and fixes permissions. This is what the user runs with a real phone. Test it without root (`adb unroot`): that is the user's case, and root hides the permission problems (pitfalls.md, "Copying into the app's folder without root").
- **On-device onboarding.** It downloads the same release over the network. That is slow on the emulator, so test it only when working on onboarding.

**Ownership gotcha.** `adb push` creates files owned by `shell` with mode 644. The app opens SQLite read-write (WAL), so it fails with "Could not open the database in read/write mode". Two fixes:
- `adb shell chmod 666` on the DB files. The transfer script does this.
- With root: `chown <app uid>` plus `restorecon`. See emulator-testing.md.

Also delete stale `-wal`/`-shm` files next to a pushed DB.

## Matching desktop

- **Source of truth is the 0.6.1 code**, not the fork's older copy. Clone upstream `kdroidFilter/zayit` at tag `v0.6.1` and SeforimLibrary at its pinned commit (commands in desktop-reference.md). The fork's `SeforimApp/` and `SeforimLibrary/` date from June 2026 and lack tools, favourites, authors and the current search UI.
  - Desktop UI: `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/**` (in the 0.6.1 checkout).
  - Strings: `SeforimApp/src/commonMain/composeResources/values*/strings.xml`. Hebrew is the default `values/`.
  - Library and search logic: `SeforimLibrary/**`.
  - Port logic rather than approximating it. Commentator grouping, catalog ids and zmanim were all wrong until ported verbatim.
- **Seeing desktop:**
  - `scripts/desktop-start.sh` (run in the background) installs the released `.deb` (0.6.1, the version this port matches) and runs it on Xvfb :99 at 1440x900.
  - `scripts/desktop-shot.sh <name> [x y]` clicks and captures.
  - Pair the shots with Android ones using `scripts/contact-sheet.py out.png desktop.png android.png`.
- [references/desktop-reference.md](references/desktop-reference.md) maps each desktop screen to its source file and its Android counterpart, and gives click coordinates for the desktop window.

## Architecture in brief

Sources live in `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/`:
- **`MainActivity`:** onboarding gate, then opens the repository off the main thread, then runs `ZayitApp`.
- **`ZayitApplication`:** app-wide singletons: `libraryStore`, `prefs`, `userData`, `history`, `search`.
- **`ui/shell/`:**
  - Tabs and overlays are driven by `ui/AppState.kt` (`AppViewModel`, `Destination`, `Overlay`).
  - Phone gets a top bar with a drawer; at or above `WideBreakpoint = 840.dp` it switches to the desktop-style tab strip and side islands.
- **`ui/home`, `ui/reader`, `ui/search`, `ui/settings`, `ui/tools`, `ui/onboarding`:** one folder per desktop feature.
- **`data/`:** library store and installer, preferences, user data (notes, highlights), history and bookmarks, and logic ported from desktop (commentator grouping, zmanim, calendar, dictionary).
- **`search/`:** desktop Lucene search adapted for Android (see below).
- **`update/`:** the app updating itself from its GitHub releases (desktop's Nucleus updater): `AppUpdater` checks at start, downloads the APK, checks its certificate and hands it to PackageInstaller. Only release-key builds update; a development build tests it with a feed file (`docs/DEVELOPMENT.md` › Testing the app update, and `references/emulator-testing.md`).

[references/architecture.md](references/architecture.md) has the full file map, data flow and conventions.

## Search (offline, Lucene)

Search must be fully offline; the user rejected server-side search. Android runs the **same Lucene search as desktop (10.5.2, the version that wrote the shipped index), on the same prebuilt index files**:
- `seforim.db.lucene`
- `seforim.db.lookup.lucene`
- `lexical.db`

Lucene 10.x does not run on Android 13 unmodified, so `androidApp/lucene-android/` builds a patched lucene-core:
- It replaces classes that use JDK-only APIs (ClassValue, Module, StackWalker, Runtime.version…).
- It rewrites API-34/35 call sites with ASM.
- `check-api33.sh` gates the jar with R8 TraceReferences against the API-33 platform.

Rules:
- Open indexes with `NIOFSDirectory`, never MMapDirectory.
- Don't re-research whether Lucene can work; that is settled.
- Read [references/lucene-android.md](references/lucene-android.md) before touching search, the patched jar or R8 rules for Lucene.

## Things that will bite you

The full list with fixes is in [references/pitfalls.md](references/pitfalls.md); read it before UI work. The ones that cost the most time:
- **RTL:**
  - In a `Row` the first child is drawn on the **right**.
  - `drawBehind` uses absolute coordinates, so check `layoutDirection` when drawing a start-side bar.
  - Bidi reorders "a / b" with numbers; write "a מתוך b".
- **Navigation and system UI:**
  - A `ModalNavigationDrawer` needs its own `BackHandler`, or Back closes the screen behind it.
  - Status and navigation bar icon colours must follow the app theme (`WindowCompat.getInsetsController`).
  - Tablets with a taskbar need `windowInsetsPadding(navigationBars)`.
- **Hebrew calendar:** use `JewishCalendar().apply { setGregorianDate(y, m0, d) }`. `JewishCalendar(Date)` uses the default timezone and shifted Shabbat/parasha.
- **API levels:** minSdk is 30, so a platform call newer than API 30 needs a `Build.VERSION.SDK_INT` check and a fallback that leaves 12+ as it was. Lint's NewApi misses some (it missed `InputStream.readNBytes(int)` and `RemoteViews(Map)`, probably because it cannot read Kotlin 2.4 metadata): look new calls up in `api-versions.xml`. Launcher widgets are off below Android 12. Emulator and differences: `androidApp/docs/DEVELOPMENT.md` › Android 11.
- **SQLite:** Android 11 ships SQLite 3.28 (12 and 13: 3.32): no FTS5, at most 999 bound parameters per statement (bind id lists in chunks, as `LineConnections` does), and row-returning PRAGMAs throw in `execute()`. That is why `PragmaSafeDriver` exists.
- **Release builds:** they are R8-minified. A missing keep rule shows up only in release, typically kotlinx-serialization `$$serializer`, ServiceLoader/SPI, or reflection. Check `proguard-rules.pro` when something works in debug but not in release.

## Delegating to agents

When fanning work out (Workflow or Agent tool), state these limits in each prompt:
- **One emulator.** Only the orchestrator runs adb. Agents that need the device return instructions instead.
- **One Gradle build of `:app` at a time.** Parallel builds in `androidApp/` corrupt each other's outputs. Agents type-check by building only their own subproject, or the orchestrator builds after integrating.
- **Desktop code stays untouched.** All Android changes live under `androidApp/` (and this skill).
- **Give agents the decisions already made** (Lucene approach, minSdk 30 with guarded fallbacks that keep 12+ unchanged, desktop parity, and the name and authorship rule: the app is Pardes (פרדס), never Zayit/זית, based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it; never desktop's "created by" texts, no porter name, and this repository linked only as Pardes's AGPL source) so they don't re-research them. The user notices and objects to repeated research.

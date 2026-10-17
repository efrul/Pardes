# Developing Pardes, the Android app based on Zayit

This file records where the Android port stands and how it is worked on. It is for whoever picks it up next, person or agent. Build steps are in [../README.md](../README.md), and the screen-by-screen design is in [../DESIGN.md](../DESIGN.md). Claude Code sessions also get the `zayit-android` skill (`.claude/skills/zayit-android/`). It has the scripts for the SDK, the emulator, screenshots and desktop comparison, plus a list of pitfalls that were already solved.

## Goal and constraints

- **Target:** Android 13 (minSdk 33), phones down to the 3" Unihertz Jelly Star, and tablets.
- **Parity with desktop Zayit 0.6.1:** the same screens, wording (except the authorship texts, see below), behaviour and data. Desktop is the reference whenever something is unclear. Four fixes from desktop 0.6.2 are ported on top of that base (see [PARITY.md](PARITY.md) › Later desktop releases); the rest of 0.6.2–0.6.4 is not.
- **Own app, based on Zayit:** Pardes is not an app by Zayit's author. User-facing text says Pardes is based on Zayit by Elie (Elyahou) Gambache (AGPL-3.0), who did not make it, is not responsible for it and does not support it. His copyright, licence and §7b attribution notices are kept; the porter is not named, and this repository is linked only as Pardes's AGPL source (the כללי header, אודות, תנאים). Where desktop's wording says who made the app (the welcome step, the settings header, About, the terms), Android's differs on purpose.
- **Offline, including search:** the app uses the desktop library and the desktop Lucene index. There is no server.
- **Shared code:** desktop code is reused unchanged where it compiles, and desktop sources are never edited for Android.

## Status

The app was rebuilt against the real desktop 0.6.1 sources in four waves on 2026-10-13; [PARITY.md](PARITY.md) tracks every gap.

| Area | State |
|---|---|
| Engine and data | SeforimLibrary 0.6.1 (submodule `upstream/SeforimLibrary`). Lucene search byte-identical to desktop on a test library. Desktop zmanim engine and city table. Incremental (delta) library updates. |
| Shell | Per-tab state, session restore, workspaces, pinned and reopenable tabs, history and favourites in desktop's `user_settings.db`, deep links, keyboard shortcuts. |
| Home and tools | Desktop widget grid, plus the tools ported from 0.6.1. |
| Search | 0.6.1 smart bar, typed references, in-book search, category tabs, grouping by book, knowledge panel, author page, tablet preview; plus the 0.6.2 search fixes 2126c238, 90552dbc and 51463d23. |
| Reader | Find in page over the whole book, range selection, highlights and notes, commentaries as desktop, alt TOC, diacritics per category, shnayim mikra mode. |
| Settings and onboarding | Desktop keys and pages, database update flow, desktop fonts, desktop-compatible backup. The app updates itself from its GitHub releases (badge, dialog, Settings › כללי banner). |
| Devices | Built for 240dp phones (Jelly Star), normal phones and tablets (desktop layout from 840dp, search split from 1000dp). |
| Testing | Waves 1–4 compiled, R8-built and gated against API 33. The waves after the first Lucene build still need a full emulator and device pass. |

## Parity checklist

[PARITY.md](PARITY.md) lists every known difference from desktop 0.6.1, found by comparing it with the real 0.6.1 sources. It is the to-do list for this port. Tick items as they land.

## Screenshots

Phone (Pixel 6 size): home, reader, targum pane, history, dark reader.

![Phone](screenshots/phone.jpg)

Onboarding on the Jelly Star (the desktop first-run flow): the welcome step and the licence step, top and the app's own licence. Both say Pardes is based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it. (The images are from before the rename to Pardes.)

![Onboarding](screenshots/onboarding.jpg)

Dark theme.

![Dark](screenshots/dark.jpg)

## How the work is done

1. **Find the desktop behaviour in code.**
   - UI: `SeforimApp/src/jvmMain/.../features`.
   - Strings: `SeforimApp/src/commonMain/composeResources/values/strings.xml`.
   - Desktop features newer than this fork's sources (the tools menu) exist only in the 0.6.1 binary. Run it headless and screenshot it.
2. **Port it** under `app/src/androidMain/kotlin/.../android/`, and say in a KDoc which desktop class each file mirrors.
3. **Build** `./gradlew :app:assembleRelease` and install it on the API-33 emulator.
4. **Screenshot** at phone, Jelly Star (`wm size 480x854`, density 320) and tablet (`wm size 1920x1200`, density 240: 1280dp) sizes. Compare side by side with desktop and fix the differences.
5. **Commit** as `androidApp: <change>`.

## Working on Windows

The port was started in a Linux cloud container, and it also builds and runs on a Windows PC. What differs:

- **JDK.** Gradle must run on JDK 21 (see the README). Android Studio's bundled runtime is enough:
  ```bash
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"    # Git Bash
  ./gradlew :app:assembleRelease
  ```
- **Checkout under a non-ASCII path** (for example a Hebrew folder name). The Android Gradle plugin refuses to configure the build unless it gets `-Pandroid.overridePathCheck=true`, or that line in `~/.gradle/gradle.properties`. With it, the build, R8 and the scripts work from such a path.
- **SDK.** `local.properties` takes forward slashes: `sdk.dir=C:/Android/android-sdk`. Gradle downloads a missing platform or build-tools package by itself when the SDK licences are accepted.
- **Line endings.** `.gitattributes` keeps `*.sh` and `gradlew` on LF, so they run in Git Bash and WSL whatever `core.autocrlf` says.
- **API 33 gate.** `lucene-android/check-api33.sh <jar>` runs from Git Bash and uses the newest `d8.jar` under the SDK's build-tools.
- **Memory.** On a PC with 8 GB of RAM the R8 step alone can take half an hour, and the emulator does not fit next to a running build. Stop the Gradle daemons (`./gradlew --stop`) before starting the emulator.
- **Emulator.** With Hyper-V/WHPX the API 33 x86_64 image is hardware-accelerated, so the cloud container's software-emulation waits do not apply.
- **Desktop reference.** The installed desktop app keeps its library in `%APPDATA%\io.github.kdroidfilter.seforimapp\databases`. That folder is the default source of `tools/push-desktop-library.ps1`.
- **Native code.** `app/src/androidMain/cpp` builds `libpardes_multiplex.so` (SQLite's `test_multiplex.c` as a loadable extension, with the 3.49.1 headers) with CMake 3.22.1 and NDK 27.3.13750724. Gradle installs a missing NDK when the SDK licences are accepted. The extension calls only the routines of the SQLite that loads it: requery `sqlite-android` 3.49.0, published on JitPack only (`settings.gradle.kts` restricts that repository to `com.github.requery`).
- **Transfer script.** adb.exe reads its standard input as text, so a stream through `adb shell -T` stops at the first 0x1A byte. `tools/push-desktop-library.ps1` therefore pushes each file to `/data/local/tmp` and copies it into place on the phone, where the `.sh` streams it. Test changes to the `.ps1` on Windows, not with pwsh on Linux. Windows 11's "Run with PowerShell" runs `powershell -file` under the user's execution policy, so it refuses a downloaded `.ps1` ("is not digitally signed" under RemoteSigned, nothing at all under the default Restricted); `tools/push-desktop-library.cmd` runs it with `-ExecutionPolicy Bypass` and pauses at the end, and is what users double-click.

## Signing

Local debug and release builds are both signed with `androidApp/debug.keystore` (the usual debug credentials: store and key password `android`, alias `androiddebugkey`). It is in the repository on purpose: every machine then produces the same signature, so a build from a cloud session and a build from a PC install over each other without uninstalling, which would delete the library on the phone. The key is public, so it is only fit for development builds.

Published releases are signed with Pardes's own key, which only the repository's secrets hold (`.github/workflows/android-release.yml` checks the APK's certificate, `RELEASE_CERT_SHA256`). A release and a development build therefore cannot be installed over each other. The in-app updater relies on this: it installs only an APK signed with the installed app's own certificate, and a development build (whose certificate is not the release one) looks for no update at all.

## Testing the app update

A development build can run the whole update on a device or an emulator from a test feed instead of GitHub:

1. Build the build to update from (it must be at least the installed `versionCode`) and a build to update to, with a higher `versionName` and a `versionCode` not lower. On a phone that keeps a real library, keep `versionCode` where it is: a higher one would stop later builds from installing over it without uninstalling, which deletes the library.
2. Copy the target APK into the app's private files, e.g. `files/app-update-test/pardes-android-0.3.1-99.apk`, and write `files/app-update-test-feed.json` in `releases.json`'s shape, one release with the tag `android-0.3.1-99`, the APK's `name`, its `size`, `"digest": "sha256:<sha256>"` and `"browser_download_url": "file:///data/user/0/io.github.efrul.pardes/files/app-update-test/pardes-android-0.3.1-99.apk"`. Optionally `files/app-update-test-changelog.md` (CHANGELOG.md's format) for what is new. The release build type is not debuggable, so `run-as` cannot write there: use `adb root` on an emulator image that allows it, or `su` on a rooted phone, then `chown` the files to the app's uid and `restorecon -R` the folder.
3. Stop the app (`am force-stop`: the check runs once per process) and start it. It finds the version at start (the snackbar once per version: remove `announced_version` from `shared_prefs/app_update.xml` to see it again, and the badge), downloads (copies) and checks the APK; the first time the banner asks for "install unknown apps" ("פתח הגדרות"); then Android installs it, usually with no question. A release-key APK in the feed shows the refusal path ("אינו חתום במפתח של פרדס המותקן"); the real API list (`curl https://api.github.com/repos/efrul/Pardes/releases`) as the feed checks the parsing.
4. Remove the three test files afterwards: while the feed is there, the build keeps offering its version. Without the feed the build drops what it downloaded at its next start.

The feed is read only by a build that is not signed with the release key, and its `file:` URLs only inside the app's files.

## Decisions and why

- **Separate Gradle build** (`androidApp/`): the desktop build stays untouched and builds without the Android SDK.
- **Compose redraw instead of the desktop UI:** desktop uses Jewel and desktop-only window APIs. The Android UI copies its look (Islands colours, fonts, gold accent) and adapts the layout to touch and small screens.
- **minSdk 33:** the user's target device runs Android 13. Everything that needs newer Java APIs is patched or avoided rather than raising minSdk.
- **Lucene on the phone:** the same index and engine as desktop means the same results, no on-device indexing and no server. Lucene 10.x needs JDK APIs that Android 13 lacks, so `lucene-android/` patches lucene-core and a TraceReferences check against the API-33 platform guards it.
- **Library on a memory card** (since 2026-10-16): in the app's own folder on the card, not a folder the user picks, so no storage permission (all-files access) is needed. A FAT32 card gets the database in multiplex chunks rather than a refusal, because Android formats cards of 32 GB and less as FAT32. Only a split database uses the bundled SQLite: every other library keeps Android's, so nothing changes for it.
- **Updating the app from GitHub releases** (since 2026-10-17): the releases are read as the download site reads them (the GitHub API's list, else the site's `releases.json`), with no server of our own, and compared by `versionName` (the tag carries it, not `versionCode`). The APK is trusted for its signing certificate, not for where it came from: the app trusts user CAs (for filtering proxies), so whoever holds one could change the list and the file alike; the sha256 only proves the download is whole. PackageInstaller sessions rather than an `ACTION_VIEW` of the file: no FileProvider, and with `UPDATE_PACKAGES_WITHOUT_USER_ACTION` the app replaces itself without a question once the user has let it install apps.
- **R8 release builds,** even for testing: the debug APK is many times larger and slow on a software emulator.
- **Own name, icon and applicationId:** Pardes (פרדס), an icon of its own (the word in gold on olive green) and `io.github.efrul.pardes` since 2026-10-15, at the request of Zayit's author (before it, "זית לאנדרואיד" as `io.github.efrul.zayit`, and earlier test builds in the original author's namespace). The code's namespace and package names stay `io.github.kdroidfilter…`, as in desktop. Test builds from before the change (never distributed) are a different app to Android, with their own library.

## Known gaps / ideas

- Incremental updates on a library split for a FAT32 card: the delta code opens the database with Android's SQLite. Porting it to the bundled SQLite (requery's API mirrors Android's) would lift that.
- Year cycle tool: 2D rendering only.
- Dictionary tool: headword match only.
- App updates are found only while the app runs (at start, or in Settings): there is no background check (WorkManager) or notification for a new version.
- No Play Store packaging, and no app bundle with split APKs. A store listing would also need text that says Pardes is based on Zayit by Elie (Elyahou) Gambache and is not on his behalf.

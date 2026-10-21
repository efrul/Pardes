# Architecture of androidApp

## Contents
- Build layout and shared code
- Runtime flow
- Source map
- State and navigation
- Data and storage
- Conventions

## Build layout and shared code

`androidApp/` is a separate Gradle build. It is **not** included from the root `settings.gradle.kts`, so the desktop build is unaffected.

- `settings.gradle.kts` sets up the repositories (Google, the GCS Maven mirror, Maven Central, JitPack for requery) and includes `:app`, `:lucene-android`, `:lucene-android-api19` (its minSdk 19 jar) and `:legacy` (Pardes for Android 4.4 to 7.1, legacy.md).
- `gradle/libs.versions.toml` is the Android build's own version catalog.
- `app/build.gradle.kts` is a KMP `androidTarget` plus `com.android.application`:
  - `commonMain` compiles SeforimLibrary `core` and `dao` **unchanged** from `androidApp/upstream/SeforimLibrary`. That is an Android-only git submodule pinned to `381fbea`, the commit desktop 0.6.1 was built with. The root `SeforimLibrary/` submodule is the fork's older copy for the desktop build. After cloning, run `git submodule update --init androidApp/upstream/SeforimLibrary`.
  - `androidMain` adds the dao `androidMain` sources and `../htmlparser/src/commonMain/kotlin`.
  - SQLDelight generates two databases:
    - `SeforimDb` from `upstream/SeforimLibrary/dao/src/commonMain/sqldelight` (the library).
    - `UserSettingsDb` from `SeforimApp/src/commonMain/sqldelight` (notes and highlights, the same schema as desktop), plus `app/src/androidMain/sqldelight-user-legacy` (desktop's two UPSERTs as Android 8-10's SQLite can run them).
  - The release build type uses R8 (`isMinifyEnabled`, `isShrinkResources`) with `proguard-rules.pro`, signed with the debug key.
- `lucene-android/` holds lucene-core 10.5.2 patched for API 26 (see lucene-android.md).
- `tools/` has developer and user scripts: `make_subset_db.sh` and `push-desktop-library.sh`/`.ps1`.

Identifiers:
- **namespace:** `io.github.kdroidfilter.seforimapp.android`
- **applicationId:** `io.github.efrul.pardes` since the rename to Pardes on 2026-10-15 (builds before it, `io.github.efrul.zayit` and earlier ones in the original author's namespace, are different apps to Android)
- **external files:** `/sdcard/Android/data/io.github.efrul.pardes/files/`

Rule: if desktop code needs a change to work on Android, make an Android copy under `androidApp/` and leave desktop alone. Reuse through `srcDir` only what compiles as-is.

## Runtime flow

1. `ZayitApplication` creates the singletons: `libraryStore`, `prefs`, `userData`, `history` and `search` (`SearchService`).
2. `MainActivity.onCreate` does three things:
   - Sets the edge-to-edge system bar colours from the theme.
   - If `!libraryStore.isInstalled()`, shows `OnboardingFlow` (desktop first-run). `LibraryInstaller` and `LibraryInstallService` (a foreground service) handle the download.
   - Otherwise opens the repository on `Dispatchers.IO` (`produceState`), shows a loading box, then runs `ZayitApp(link, onLinkHandled)`.
3. `ZayitApp` lays out one of two shells:
   - **Phone** (< 840dp): a top bar, the current tab's screen, a library drawer and full-screen overlays.
   - **Wide** (≥ `WideBreakpoint` 840dp): a tab strip and side islands like desktop.

## Source map

Paths below are relative to `app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/`. Desktop paths are in the 0.6.1 checkout (desktop-reference.md); ported files name their desktop file in a KDoc.

| Path | Role | Desktop counterpart |
|---|---|---|
| `MainActivity.kt` | startup routing (onboarding, library open, version gate → `ui/database/DatabaseUpdateScreen`), session restore, deep links (`singleTask`, `onNewIntent`), system bars, unfinished delta update recovery | `main.kt` |
| `ZayitApplication.kt` | app singletons: `libraryStore`, `prefs`, `userData`, `history`, `search` | `AppGraph` (Metro DI) |
| `ui/AppState.kt` | `AppViewModel`: workspaces and tabs (`Destination.Home/Book(findQuery)/Search(scope)/History/Favorites/Author`), pinned tabs, closed-tab stack, per-tab `TabState`, overlays | `TabsViewModel`, `TabsDestination` |
| `ui/shell/` | `ZayitApp` (layout per width, routing), `TabHost` (per-tab `ViewModelStore` + `LocalTabSession`, LRU of composed tabs, per-tab Back, each tab's Home kept composed ("parked", `TabSession.parked`) behind a book opened in its place, with its own `homeOwnerFor` ViewModels), `TopBar`, `TabMenu`, `WorkspaceSwitcher`, `ShellPopups` (history/favourites popups), `HistoryScreens`, `DeepLinks`, `KeyShortcuts`, `LauncherShortcuts` | `TabsContent`, `MainTitleBar`, features/history, favorites, framework/session |
| `ui/home/` | `HomeScreen` (wallpaper, `WideHome` with hideable tree), `HomeSearchBar`, `widgets/` (desktop's editable widget grid: 17 widgets, gallery, persistence) | features/home |
| `ui/search/` | `UnifiedSearchBar` + `SearchBarViewModel` (suggestions, references, in-book scope), `SearchScreen` + `SearchViewModel` (category tabs, base-books status line, grouping by book, knowledge panel), `PassagePreview` (tablet split from 1000dp), `SearchTocPane` | features/search |
| `ui/author/AuthorScreen.kt` | author page | features/author |
| `ui/reader/` | `ReaderScreen` + `ReaderViewModel`, `BookTextView`, `TextSelection` (range selection), `FindBar` + `BookFindMatches` (engine find-in-page), `ReaderPanes` (commentaries/targum/sources with `LineConnections`), `NotesPane`, `TocPane` + `TocSearch`, `Breadcrumb`, `BookDetailsPane`, `FastScroller`, `ReaderCopy`, `ReaderPrefs` (diacritics per root category) | features/bookcontent, notes |
| `ui/notes/NotesScreen.kt` | "ההערות שלי" | features/notes |
| `ui/library/LibraryTree.kt` | book tree with search (books before subcategories) | categorytree panel |
| `ui/settings/` | settings pages, `DbDeltaUpdateSection` | features/settings (+ dbupdate) |
| `ui/database/DatabaseUpdateScreen.kt` | outdated / missing / broken library flow (delta first, else full install) | features/database |
| `ui/onboarding/` | first-run flow | features/onboarding |
| `ui/tools/` | title-bar tools, ported from 0.6.1 | features/zmanim, siddur, luach, earthwidget |
| `search/` | Android variants of the 0.6.1 search module (`LuceneSearchEngine`, `MagicDictionaryIndex`, `LuceneLookupSearchService`, `AcronymFrequencyCache`), `SearchService` (lazy open, cooperative `countFacets`), vendored `RepositorySnippetSourceProvider`, `SearchEntityFinder`, `reference/` (typed references) | SeforimLibrary search, framework/search, features/search/domain |
| `delta/` | Android variants of SeforimLibrary `delta-updater` (patch download, in-transaction apply, Lucene/catalog update) + `LibraryDeltaService` | delta-updater, features/settings/dbupdate |
| `update/` | `AppUpdater` (check at start and on request, APK download into the cache, sha256 + package/version/certificate checks, PackageInstaller session, waiting confirmation, "פרדס עודכן"), `AppReleases` (API list / releases.json / changelog.md), `AppPackages`, `AppUpdateService` (dataSync FGS, notifications 4121/4122, `AppUpdateInstallReceiver`, `AppUpdatedReceiver` for MY_PACKAGE_REPLACED); UI in `ui/settings/AppUpdateSection.kt` (banner, dialog) and the title bars' badge | framework/update (`AppUpdateService`), features/update |
| `data/` | `LibraryStore`, `LibraryInstaller` (on `LibraryInstallerBase`, shared with the 4.4 edition: offline import, tar.zst streaming, space and version checks, staged names; `DatabaseChunks` writes and renames a FAT32 card's chunks) + `LibraryInstallService`, `AppPrefs` (desktop keys), `UserDataStore` (desktop user_settings.db schema: notes, highlights, diacritics), `HistoryStore`, `FavoritesStore`, `SessionStore`, `ReadingHistory` (facade), `ZmanimEngine` + `Cities` + `CandleLighting` (desktop's), tool data | various |

Shared sources compiled through `srcDir` (not copied): `upstream/SeforimLibrary` core, dao, search and delta-updater (with excludes for the Android variants). Vendored unchanged: the 0.6.1 htmlparser (`app/src/androidMain/htmlparser`) and user-settings schema (`app/src/androidMain/sqldelight-user`).

## State and navigation

- **No Navigation library.**
  - `AppViewModel` holds `tabs`, `selected` and an `overlays` stack.
  - Back pops an overlay first, then closes drawers or panes (`BackHandler`s in each screen).
- **Tabs.**
  - `openInNewTab(destination, title)`, `openInCurrentTab(...)`.
  - A tab's title is updated by the screen (`updateTabTitle`), e.g. the book title.
- **Persisted state:**
  - Preferences live in `AppPrefs` (`zayit_settings`).
  - History and bookmarks live in `ReadingHistory` (`zayit_history`, JSON).
  - Notes and highlights live in `UserDataStore` (SQLite, desktop schema), so they can be exchanged with desktop.
- **Deep links:**
  - `zayit://book/{bookId}/line/{lineId}` is desktop's "copy link" format.
  - `zayit://search?q=`.

## Data and storage

- **Library.** `seforim.db` is ~8 GB on a full install. Index files sit next to it: `seforim.db.lucene/`, `seforim.db.lookup.lucene/`, `lexical.db`, `catalog.pb`, plus `release_info.txt` (the version check).
- **Where it lives.** `LibraryStore.libraryDir` is `<external files>/library` or `<internal files>/library`, whichever already holds the DB. New installs go to external storage when it is mounted.
- **Install from files, both editions.** `LibraryInstallerBase` holds every step that does not depend on where an app keeps its library; `LibraryInstaller` (main app) and `legacy/.../data/LegacyInstaller.kt` (Android 4.4 to 7.1) only say where the library goes (`releaseLibrary`, `libraryDirs`, `sameVolume`, `onInstalled`). Change an install step there once, for both: `:app:compileDebugKotlinAndroid` must keep its 7 warnings and `:legacy:lintDebug` its "No issues". The 4.4 edition adds a keypad file browser (`ui/InstallActivity.kt`: its own folders, each storage root, Download; picking a `.part01` takes the bundle's other parts and manifest from its folder) and a library folder install, moved in by renames when it is on the library's volume (no room needed), else copied (seforim.db in chunks on FAT32). An install closes the library before it writes, and a run that fails or is cancelled leaves it closed under the screens it was started over (Settings is reachable from a book): every 4.4 screen that keeps a `Library.Opened` overrides `BaseActivity.heldLibrary`, and on resume it is opened again and the screen built again (`onLibraryReopened`: `recreate()` by default, a rebind for the trees and the search), or the screen gives way to the Home when no library opens.
- **Repository.** `SeforimRepository` from the desktop dao, on an Android SQLDelight driver wrapped by `PragmaSafeDriver`. Open it off the main thread.
- **SQLite constraints.** SQLite 3.18 on API 26 (3.19 on 27, 3.22 on 28–29, 3.28 on 30, 3.32 on 31–33): no FTS5, no `RETURNING`, no newer JSON functions, no UPSERT below 3.24, no `VACUUM INTO` below 3.27, and at most 999 bound parameters per statement below 3.32. Below Android 9 the library goes through the bundled requery SQLite 3.49 (`SplitDatabase.usesBundledSqlite`).

## Conventions

- Compose code is compact: small private composables in the same file, with Hebrew UI strings inline, copied from desktop `strings.xml`. The exception is authorship. Where desktop says who made the app (the welcome step, the settings header, About, the terms), Pardes says instead that it is based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it; his copyright, AGPL and §7b notices are kept. See SKILL.md › Authorship and name.
- Comments explain *why* (a desktop behaviour, an Android quirk), not what.
- Colours and sizes come from `Zayit.colors` / `Zayit.fonts`, never hard-coded, so dark mode and accents work.
- Every screen must work at about 240dp (Jelly Star), 411dp (phone) and 1280dp (tablet). Check all three after layout changes.
- New desktop behaviour: find the desktop code, port it, and say in a KDoc which desktop class it mirrors (as the existing files do).

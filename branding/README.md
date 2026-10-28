# Branding: Pardes for desktop over Zayit's code

The desktop app in this repository is upstream [Zayit](https://github.com/kdroidFilter/Zayit) (merged as released,
`git merge v<x.y.z>`) built as **Pardes for desktop** (פרדס למחשב). Pardes is based on Zayit by Elie (Elyahou)
Gambache and is not on his behalf: he did not make it, is not responsible for it and gives it no support. At his
request a fork does not use Zayit's name or icon, so this folder holds everything that makes the build Pardes, and
upstream's files keep Zayit's values. A new Zayit release then merges against a few hooks instead of a renamed app.

## How it applies

- `brand.gradle`, applied by the last line of `SeforimApp/build.gradle.kts`, picks the brand
  (`-Pseforimapp.brand=pardes`, the default, or `zayit`) and, for Pardes:
  - sets the packaging values of `nucleus.application` from `<brand>/brand.properties` (name, package and image name,
    vendor, the installers' copyright, homepage, release repository, Linux maintainer and WM class, the Linux launcher
    entry's English and Hebrew name and comment, Windows upgrade id, icons);
  - removes the OS URL scheme: `zayit://` belongs to Zayit, and two apps registering it would clash;
  - fails if one of Zayit's private add-ons (smart siddur, semantic search, quote links) would be compiled: their
    licence is for Zayit only;
  - lays `<brand>/composeResources` over `SeforimApp/src/commonMain/composeResources` and `<brand>/assets` over
    `src/jvmMain/assets` (task `BrandResources` in buildSrc). A file replaces the file at the same path; the brand's
    `values/strings.xml` holds only the strings that change, each replacing upstream's string of that name. The build
    fails when upstream no longer has a file or a string the brand replaces.
  - adds `<brand>/assetsExtra` to the package-time assets: files upstream does not have, which must stay so (the build
    fails if upstream adds one at the same path). `common/licenses` holds the licence texts of everything the app
    bundles, shipped as plain files in the install folder and indexed in its `README.md`; `brandLicences` copies in
    three texts upstream keeps elsewhere (`LICENSE`, `limud/NOTICE.txt`, the sky's `CREDITS.md`). A new desktop
    dependency, font, icon set or data file needs its entry and text there.
- `<brand>/kotlin` is compiled with the app: `Brand` (the data folder's id, the preferences node), `BrandInfo` (the
  source link, the error report's signature, the backup file name, the Linux launcher id, the User-Agent),
  `BrandUpdates` (where updates come from: Pardes's `desktop-*` releases, `PardesDesktopReleases`) and
  `BrandLibraries` (an installed Zayit's library, which Pardes offers to use in place). The app's code reads these
  where upstream has a literal; `zayit/kotlin` has upstream's values, so `-Pseforimapp.brand=zayit` builds Zayit as
  released.

## The hooks in upstream files

Keep this list short and exact; every line here is a possible merge conflict.

| File | Hook |
|---|---|
| `SeforimApp/build.gradle.kts` | last line: `apply(from = rootProject.file("branding/brand.gradle"))` |
| `SeforimApp/.../main.kt` | `val appId = Brand.APP_ID` (FileKit's data folder) |
| `SeforimApp/.../framework/di/modules/AppCoreBindings.kt` | `provideSettings()` returns `Brand.settings()` |
| `SeforimApp/.../framework/backup/PendingUserDataRestore.kt` | imports preferences into `Brand.settings()` |
| `SeforimApp/.../features/settings/ui/GeneralSettingsScreen.kt` | title `stringResource(Res.string.app_name)` (was "זית"); GitHub button `BrandInfo.SOURCE_URL` |
| `SeforimApp/.../core/BookErrorReport.kt` | `append(BrandInfo.ERROR_REPORT_SIGNATURE)` |
| `SeforimApp/.../features/settings/data/DataSettingsViewModel.kt` | export name `BrandInfo.BACKUP_FILE_PREFIX`; reset leaves a library used in place (`ForeignLibraries.isForeign`) |
| `SeforimApp/.../core/presentation/components/AppLinuxQuicklist.kt` | `DESKTOP_FILE_ID = BrandInfo.LINUX_DESKTOP_FILE_ID` |
| `SeforimApp/.../features/onboarding/download/HttpFileDownloader.kt` | `User-Agent` `BrandInfo.USER_AGENT` |
| `SeforimApp/.../framework/update/AppUpdateService.kt` | `?: BrandUpdates.provider` before upstream's `GitHubProvider` (Zayit's is null); `Platform.Linux -> linuxPackageFormat()` (that function and its `ExecutableRuntime`/`ExecutableType` imports), so an RPM install takes the .rpm: a fix for both brands, to drop once upstream has one |
| `SeforimApp/.../features/database/update/DatabaseCleanupUseCase.kt` | skips a folder `ForeignLibraries.isForeign` |
| `SeforimApp/.../framework/database/PendingDbCleanup.kt` | drops pending deletions in a foreign folder |
| `SeforimApp/.../features/onboarding/licence/LicenceScreen.kt` | offers `ExistingLibraryScreen` when `ForeignLibraries.find()` finds one |
| `SeforimApp/.../features/onboarding/navigation/OnBoardingDestination.kt`, `OnBoardingNavHost.kt` | the `ExistingLibraryScreen` destination |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | the `onboarding_existing_library_*` strings (one block) |

New files in upstream folders (no conflict, but generic code a Zayit merge must still compile with):
`framework/database/ForeignLibraries.kt` (the only reader of `BrandLibraries`),
`features/onboarding/existinglibrary/ExistingLibraryScreen.kt` and `jvmTest/.../ForeignLibrariesTest.kt`.

Pardes features that are not branding, written to fit Zayit as well, also touch upstream files. The library
location (install anywhere, move between drives, kept copies; `framework/database/LibraryLocation.kt`,
`PendingLibraryMove.kt`, `features/database/move/LibraryMoveWindow.kt`, `features/settings/library/`,
`features/onboarding/diskspace/InstallLocationRow.kt`, `jvmTest/.../LibraryLocationTest.kt`):

| File | Hook |
|---|---|
| `SeforimApp/.../main.kt` | `PendingLibraryMove.read()` before the graph (no `warmCatalog` then); `LibraryMoveWindow` before the onboarding |
| `SeforimApp/.../core/settings/AppSettings.kt` | `library_install_dir` (get/set) and `flush()` |
| `SeforimApp/.../features/onboarding/extract/ExtractUseCase.kt` | extracts to `LibraryLocation.installDir` |
| `SeforimApp/.../features/onboarding/download/DownloadUseCase.kt` | `targetDir` parameter |
| `SeforimApp/.../features/onboarding/diskspace/AvailableDiskSpaceUseCase.kt` | `targetDir` parameter (its drive is measured) |
| `SeforimApp/.../features/onboarding/diskspace/AvailableDiskSpaceScreen.kt` | `InstallLocationRow(...)` |
| `SeforimApp/.../framework/di/modules/OnboardingBindings.kt` | both use cases get `LibraryLocation.installDir` |
| `SeforimApp/.../features/database/update/DatabaseCleanupUseCase.kt` | also cleans the install folder; skips kept copies |
| `SeforimApp/.../features/settings/ui/DataSettingsScreen.kt` | `LibraryLocationCard()` |
| `SeforimApp/.../features/settings/data/DataSettingsViewModel.kt` | the reset deletes kept copies |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | the `library_*` strings (one block) |
| `branding/<brand>/kotlin/.../BrandInfo.kt` | `LIBRARY_FOLDER_NAME` |

Licence-restricted files are replaced in place, not overlaid, because they must not be in the repository at all:
the Rashi Amiti font (removed, with its `FontCatalog` entry; `FontCatalogTest` counts 27 fonts) and the temple
picture (`files/temple_jerusalem_in_fire.webp`, a public-domain painting by David Roberts instead of a Shutterstock
photo). When a Zayit merge conflicts there, keep Pardes's side.

## Merging a Zayit release

1. `git fetch --no-tags https://github.com/kdroidFilter/zayit tag v<x.y.z>` and `git merge v<x.y.z>` (never rebase,
   never push upstream's `v*` tags).
2. Keep removed what Pardes removed: Zayit's release, site and CLA workflows, FUNDING, dependabot, Rashi Amiti, the
   Shutterstock picture.
3. Build: `BrandResources` fails if a replaced file or string moved upstream; update `<brand>/` accordingly. It
   does not check sizes: if upstream changes a replaced picture's size, change it in `desktop()` of
   `.claude/skills/zayit-android/scripts/pardes-icon.py` and run `python3 .claude/skills/zayit-android/scripts/pardes-icon.py desktop .`,
   which writes all the pictures (AppIcon, LinuxIcon, WindowsIcon.ico, zayit_transparent, zayit_logo_branch,
   zayit_new_logo(_text), splash) at upstream's sizes.
4. Check that `-Pseforimapp.brand=zayit` still compiles.

## Texts

- `composeResources/files/ABOUT.md` and `CONDITIONS.md`: in both, the §7b line ends with a space before the closing
  direction mark (U+2069). Without it the autolinker puts the mark into the zayitapp.com link. Keep the line's words
  unchanged.
- Who made the app: the name and authorship rules of the `zayit-android` skill apply to every Pardes text.

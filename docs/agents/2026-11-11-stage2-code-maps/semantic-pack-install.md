# Code map: installing and importing a semantic pack, and what the settings show of it

- **For:** stage 2 of Pardes for desktop, request 36 (`docs/DESKTOP_STAGE2_PLAN.md`): "after a manual import the app
  went on showing the old model's details".
- **When:** 2026-11-11. Read-only: nothing was changed, built or run, and nothing below was reproduced in the app.
- **Code read:** efrul/Pardes at `abcb687` (main of that day), and Nucleus's `AppRestarter` and single-instance
  lock in the local checkout of efrul/Nucleus (`65ccd12`, which may not be to the line the release Pardes builds
  with, `3.0.0-dev-202610272317`). The line numbers are those commits'.
- **By:** the session that wrote the plan, reading the files whole (not an agent).

P = SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp

## The path of a pack

1. **Found.** `SemanticPackReleases.newest(library)` (P/features/search/semantic/pack/SemanticPackReleases.kt:64)
   reads the GitHub API's release list, or the site's `semantic.json` when the API cannot be read (:67-73), and
   takes the published `semantic-<pack>-<library>` release with the highest pack number that has a manifest
   (`select`, :83-101).
2. **Brought.** Downloaded (`SemanticPackInstaller.download`, SemanticPackInstaller.kt:129-159: each part checked
   against the manifest, a whole part kept from an earlier try) or picked from the user's files
   (`resolve`, :114-123): a bundle file, one of the two zips (the other found beside it by what it holds,
   `zipsBeside`, :232-247), or `pardes-model.json` of unzipped folders (`foldersFrom`, :261-268).
3. **Staged.** `stage` (:165-193) drops an earlier pending pack (`PendingSemanticPack.clear()`, `discardStaged()`),
   unpacks into `<library>/.pardes-semantic-staged/`, checks it (`verify`, :421-446: the model reads, its checksums,
   the index is the model's), writes `pardes-pack.json` into the staged model folder when the pack's info is known
   (:184), and marks both folders with `.pardes-staged` (:186).
4. **Scheduled.** `schedule` (:196) writes `pending-semantic-pack.txt` in the app's databases folder
   (PendingSemanticPack.kt:45-50). The view model then says `Work.Ready`
   (P/features/settings/semantic/SemanticPackViewModel.kt:191-199).
5. **Put in place, at the next start.** `main.kt:216` calls `PendingSemanticPack.runOnce()` before the graph is
   created. `apply` (PendingSemanticPack.kt:87-129) renames each old folder to `<name>.old`, moves the staged one
   in, then deletes the old ones and the marks.
6. **Shown.** The card's first line is `installedText(state.installed)` (SemanticPackCard.kt:225, :374-386), from
   `InstalledPack.read(libraryDir)` (InstalledPack.kt:33-52), read when the view model is made and at `refresh()`
   (SemanticPackViewModel.kt:113-129). The pack's number comes only from `pardes-pack.json` in the model's folder
   (:43-48).

## Four places that fit what the user saw

None is confirmed: each is what the code does, and a check in the app has to say which one happened.

1. **Until the restart the card shows the pack in use, and says of the new one only that files are ready.**
   `stage` never touches `state.installed`. With `Work.Ready` the card shows the old pack's line
   ("מותקן: גרסה N של הקבצים, עם … שורות.", strings.xml:227), under it the banner `semantic_pack_ready` ("הקבצים
   מוכנים, והחיפוש לפי משמעות יפעל מההפעלה הבאה של פרדס.", :320) and the button "הפעלה מחדש עכשיו". The banner
   names neither the version that waits nor that the line above is the old one's.
2. **A start that cannot put the pack in place throws the staged pack away.** On any exception `apply` restores
   the old folders and deletes the staged ones (PendingSemanticPack.kt:118-122); `runOnce` clears the request (:74)
   and keeps the message in `semantic-pack-failure.txt` (:78-81). The settings then show the old pack, and once,
   only when the card is opened, the banner `semantic_pack_start_failed` ("בהפעלה הקודמת הקבצים לא הותקנו, ומה שהיה
   מותקן נשאר. אפשר לנסות שוב.", strings.xml:322). The reason is in the log alone, and the 2.7 GB have to be brought
   again.
3. **"הפעלה מחדש עכשיו" starts the new app before the old one has ended.** `SemanticPackViewModel.restart` (:163)
   is Nucleus's `AppRestarter.restartApplication()`: `ProcessBuilder(applicationExecutablePath).start()` and then
   `exitProcess(0)` (core-runtime `AppRestarter.kt:37-44`). The new process reaches `runOnce()` early in its start.
   If the old one still holds the vector index or the model open at that moment, Windows refuses to rename their
   folders, and the start ends as in 2. Linux and macOS rename open folders, so this cannot be seen in the cloud
   container. The single-instance lock does not close the gap, and opens another:
   - the first process gives the lock up in a shutdown hook, before it has ended (core-runtime
     `SingleInstanceManager.kt:123-128`), so the new one can pass the lock while the old one's files are still open;
   - a process that finds the lock held exits at once (nucleus-application `SingleInstanceRestoreBus.kt:66-84`,
     off in a development run), so a new process that comes too early leaves no app running at all. The user then
     starts Pardes by hand, and that start puts the pack in place.
4. **A pack whose folders carry no `pardes-pack.json` is installed without its number.** The bundle gives the info
   from its manifest, and the release's `pardes-model.zip` carries a copy (`semantic-release.yml:96-98`). Unzipped
   folders that did not come from that zip (and zips made by hand) carry none: `stagedInfo()` (:415-418) is null,
   nothing is written at :184, and after the swap `InstalledPack.InUse.pack` is null. The card then says "מותקן, עם
   … שורות." with no version (strings.xml:228), and `offered()` (SemanticPackCard.kt:365-371) still offers the
   newest pack as an update, the very one just installed.

Ruled out by the code: importing the pack that is already installed is not a no-op. `foldersFrom` refuses only the
very folders in use (:264-266); anything else is staged and swapped as any pack.

## What else was noted

- `SemanticPack.kt`, `BundleParts.kt` and `BundleManifest.kt` are compiled by Pardes for Android as they are
  (`androidApp/app/build.gradle.kts:34-48`, `syncDesktopSemantic`): a change to them must still compile there.
- The newest pack on 2026-11-11 is 3 (`semantic-3-v3-20261012193827`, released that day), so a user of the testers'
  builds most likely went from pack 2 to pack 3.
- `PendingSemanticPack.apply` takes its `move` as a parameter "a test's can fail": a failing rename can be tested
  without Windows.

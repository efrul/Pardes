# Library location (מיקום הספרייה): plan

Status: **planned, not implemented** (2026-10-15). Desktop 0.6.1 has no location UI: it has only the `SEFORIMAPP_DATABASE_PATH` variable and a hidden `database_path` pref. Its two strings `settings_db_path_label` ("נתיב מסד הנתונים:") and `settings_db_path_not_set` are unused. So this is an Android-only feature; record it in PARITY.md as such when it lands.

Paths below are relative to `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/`.

## What Android allows (checked in the android13-release sources)

| Where | Can it hold the live library? | Why |
|---|---|---|
| Phone, `filesDir` or `getExternalFilesDir(null)` | yes (today) | The primary `Android/data` is bind-mounted past FUSE, so it is fast. |
| microSD card, its `Android/data/io.github.efrul.pardes/files` (an extra `getExternalFilesDirs(null)` entry) | **yes, no permission** | It appears only if the slot is "adoptable" in the device fstab (`StorageManagerService` visibility rule). Access goes through MediaProvider FUSE, so it is slower. |
| USB-C stick (OTG) | **no** | vold marks every non-MMC disk as USB and mounts it invisible (`/mnt/media_rw` only). It is reachable through SAF, so it can be an *import source*, not a live library. |
| A SAF folder (`ACTION_OPEN_DOCUMENT_TREE`) | no | SQLite needs a real path for `-wal`/`-shm`, and Lucene `NIOFSDirectory` needs a `Path`. SAF suits import or copy only. |
| Any folder with `MANAGE_EXTERNAL_STORAGE` | possible, not planned | A frightening system toggle, and other apps can write there. Play policy restricts it. At most a later experiment. |

Facts that shape the design:
- **FAT32 caps a file at 4 GiB − 1.** The full `seforim.db` is about 8 GB, so the full library needs an **exFAT** card. Android formats cards up to 32 GiB as FAT32, so use 64 GB or more, or format exFAT on a PC. The reduced library (every file under 4 GiB) fits any card.
- **Ejecting or removing the card kills the app.** vold's `KillProcessesUsingPath` runs before the unmount. So design for a clean restart, not a graceful close.
- **Uninstalling with the card inserted deletes the card copy.** installd clears `Android/data/<pkg>` on every mounted volume. `android:hasFragileUserData="true"` adds the "keep app data" checkbox.
- **`repository()` on a missing `seforim.db` creates an empty database.** `getDatabasePath` makes the parent folder, and `SeforimRepository` runs `Schema.create`. A missing card must never reach it.
- **adb `shell` cannot write another app's `Android/data` on a card** (MediaProvider returns ENOENT). So the transfer scripts keep targeting the phone.
- **SQLite on the card's FUSE:** WAL's `-shm` mmap and POSIX locks work in source (cached mode, kernel-local locks). Use `mmap_size=0` there, because an I/O error on a mapped file crashes the process. This still needs a device check.
- **The Unihertz Jelly Star has a hybrid tray: SIM 2 *or* microSD.** Whether apps see the card must be checked on the device (`getprop vold.has_adoptable`, `sm list-volumes all`).

## Behaviour (phase 1)

A card **"מיקום הספרייה"** in Settings › ניהול נתונים, right after the database-updates card. The page summary becomes "עדכוני מסד הנתונים, מיקום הספרייה, גיבוי, שחזור ואיפוס".

Place names: "זיכרון הטלפון" / "כרטיס הזיכרון (%1$s)". The label comes from `StorageVolume.getDescription`, in an LTR isolate when it is Latin. Each also has an "ב…" form and a "ל…" form.

**At rest:**
- "הספרייה שמורה {בזיכרון הטלפון | בכרטיס הזיכרון (…)}."
- "גודל הספרייה: %1$s · פנויים שם: %2$s"
- "נתיב מסד הנתונים:" with `LibraryPathText`.
- "ההערות, ההדגשות וההגדרות נשמרות תמיד בזיכרון הטלפון."
- On a card only: "הוצאת הכרטיס סוגרת את פרדס. הסרת פרדס מהטלפון כשהכרטיס בפנים מוחקת גם את הספרייה שבכרטיס, אלא אם תסמנו בחלון ההסרה לשמור את נתוני האפליקציה."
- The "העברה למיקום אחר" button appears only when a second location exists.
- When a USB stick is mounted: "דיסק און קי שמחובר לטלפון לא יכול לשמש מיקום לספרייה: אנדרואיד לא מאפשר לאפליקציות לעבוד ממנו ישירות."

**Chooser "לאן להעביר את הספרייה?":** one row per location with "פנויים %1$s מתוך %2$s". A card row adds "איטי יותר מזיכרון הטלפון".
- **Badges:** "המיקום הנוכחי", and "יש כאן כבר עותק של הספרייה (מהדורה …)", whose button is "עבור לעותק הזה". Phase 1 never overwrites a copy.
- **Disabled reasons:**
  - "אין מספיק מקום: נדרשים %1$s, פנויים %2$s"
  - "הכרטיס במצב קריאה בלבד"
  - "הכרטיס מפורמט כ־FAT32, שאינו יכול לשמור קובץ גדול מ־4 ג״ב, ומסד הנתונים שוקל %1$s. אפשר לפרמט את הכרטיס כ־exFAT במחשב (הפירמוט מוחק את כל מה שבכרטיס) או להשתמש בכרטיס של 64 ג״ב ומעלה."

**Confirm:** "להעביר את הספרייה (%1$s) לכרטיס הזיכרון?". It says the copy takes a few minutes and reading can go on, that Pardes restarts at the end, and that the old copy is deleted only after the library opened from the new place. Buttons: "כן, העבר" / "ביטול".

**Progress** (card plus the notification channel "העברת הספרייה"):
- "מעתיק את הספרייה… %1$s מתוך %2$s", then "בודק את העותק…"
- "ביטול ההעברה"
- When ready: "העותק מוכן. פרדס יופעל מחדש כדי לעבור למיקום החדש…". If the app is in the background, a notification "הספרייה מוכנה בכרטיס הזיכרון" instead, which restarts on tap.

**After the restart, one notice:**
- "הספרייה הועברה לכרטיס הזיכרון."
- "פונו %1$s בזיכרון הטלפון."
- "העברת הספרייה לא הסתיימה. הספרייה נשארה בזיכרון הטלפון, ולא נמחק ממנה דבר."
- A revert notice, if the new copy failed to open.

**Errors**, each ending with " הספרייה נשארה {…}.":
- no space
- FAT32 too small
- "כרטיס הזיכרון הוצא באמצע ההעברה."
- read-only card
- "העותק החדש אינו זהה למקור. ייתכן שהכרטיס פגום."
- busy during an update or install
- WAL in use
- cancelled

**Startup screen `LibraryUnavailableScreen`, for when the library's card is absent.** It never deletes, and never offers a download as its main action. It continues by itself on `StorageVolumeCallback`.
- **Waiting** (the card is still being checked): "ממתין לכרטיס הזיכרון…"
- **Missing:** "כרטיס הזיכרון לא נמצא" / "…הכניסו את הכרטיס, ופרדס ימשיך מעצמו. לא נמחק דבר." Actions: "נסה שוב"; "פתיחת העותק שבזיכרון הטלפון" (only when one exists); "יציאה".
- **ReadOnly:** "כרטיס הזיכרון במצב קריאה בלבד".
- **Unreadable:** "לא ניתן לקרוא את כרטיס הזיכרון", with "אל תפרמטו את הכרטיס אם אתם רוצים לשמור את הספרייה שבו."
- A link on these screens: "הכרטיס אבד או התקלקל? התקנת הספרייה מחדש בזיכרון הטלפון" (nothing on the card is deleted).

**Reset:** the warning adds " גם הספרייה שבכרטיס הזיכרון תימחק." when the card is present, or "…לא תימחק." when it is absent.

## Technical design (phase 1)

- **Pointer:** a separate prefs file `zayit_library`, written with `commit()`. It stays out of `PORTABLE_KEYS` and is cleared by reset's prefs sweep. Keys:
  - `location`: `phone` (= today's rule, unchanged) or `card` + `card_uuid` + `card_label`.
  - `job`: `{from, to, phase: copying|ready|switched, bytes}`.
  - `cleanup`: a queue for volumes that are absent.
  - `notice`.
- **`data/LibraryLocation.kt` (new):** resolves the card by `StorageVolume.uuid`.
  - `MOUNTED` → the matching `getExternalFilesDirs` entry + `/library`.
  - `MOUNTED_READ_ONLY` → ReadOnly; `CHECKING` → Waiting; `UNMOUNTABLE` → Unreadable; otherwise Missing.
  - Cached, refreshed at startup and by the volume callback.
  - On a card: never `mkdirs` outside the card's files dir, never `chmod`, never a fallback to the phone.
  - A write probe catches read-only cards.
  - `maxFileSize` is read by reflection on `StorageVolume` (hidden), and EFBIG while copying is the authoritative check.
- **`data/LibraryStore.kt`:**
  - `libraryDir` branches on the pointer; the phone branch is today's getter.
  - New: `availability()`, `refresh()`, `isCard`, `allLibraryRoots()`, `findCopies()`, `deleteLibraryAt(dir)` (database first, so a half-deleted folder never looks installed), `installedBytes(dir)`.
  - `repository()` throws `LibraryUnavailableException` when `seforim.db` is missing, and passes `mmapBytes = 0` on a card to `PragmaSafeDriver`.
  - Log the path, `journal_mode` and `mmap_size` once under the tag `ZayitLibrary`.
- **`data/LibraryMover.kt` + `LibraryMoveService.kt` (new):** a dataSync foreground service with a wake lock, modelled on `LibraryInstallService`, with `onTimeout` → cancel.
  1. Preflight refuses if: the installer or delta updater is busy; a job exists; the target already has a library; the write probe fails; the largest file exceeds `maxFileSize`; or free space < bytes + clamp(10 %, 256 MiB, 1 GiB).
  2. `PRAGMA wal_checkpoint(TRUNCATE)`; `-wal` must end up empty.
  3. Copy into `<target>/library.moving/`, `seforim.db` first (so FAT32 fails early) and `release_info.txt` last. Skip `-wal`/`-shm`/`write.lock`/`delta-cache`/`.part`/`.zayit-*`. Use a 1 MiB buffer, SHA-256, `force` every 256 MiB, and a cancel check per buffer. Reading goes on from the source meanwhile.
  4. Verify: sizes, a re-read hash, `checkDatabase` (moved from `LibraryInstaller` into `LibraryChecks`), and a `DirectoryReader` on both indexes. Then a manifest, then `phase=ready`.
  5. Restart (`restartApp`, moved out of `DataSettings`).
- **Never reuse `LibraryInstaller.prepareTarget`**: it deletes the old library before writing the new one.
- **Startup (`MainActivity.checkStartup`):** `LocationJobs.recover()` first.
  - `copying` → drop the staging folder.
  - `ready` → rename `library.moving` → `library` on the same volume, then one commit `{location=to, phase=switched}`.
  - Then `availability()`; anything but Ready → `Startup.Unavailable`.
  - After a successful open: delete the source, or queue it if its volume is absent.
  - If the new location fails to open while the source is intact → revert the pointer.
- **Guards:** the delta updater and the installer refuse while a move runs or a job exists. `LibraryInstaller.libraryDirs()` is limited to the active location on a card, so an install never deletes another location's copy. Import and reset are disabled during a move.
- **Delta on cards:** `PRAGMA synchronous=FULL` in `delta/DeltaSqlite.kt`.
- **Reset:** close search and the store, delete `library` and `library.moving` on every mounted root, and empty every `getExternalFilesDirs` entry.
- **Manifest:** the new service, plus `android:hasFragileUserData="true"`. No new permission.

About 1,650 lines.

New files:
- `data/LibraryLocation.kt`
- `data/LibraryChecks.kt`
- `data/LibraryMover.kt`
- `data/LocationJobs.kt`
- `data/LibraryMoveService.kt`
- `ui/database/LibraryUnavailableScreen.kt`
- `ui/settings/LibraryLocationSection.kt`
- `ui/AppRestart.kt`

Changed: `LibraryStore`, `PragmaSafeDriver`, `LibraryInstaller`, `DbDeltaUpdateService`, `DeltaSqlite`, `MainActivity`, `DataSettings`, `SettingsScreen`, the manifest, and the docs (this file, PARITY.md, the skill).

## Later phases

2. **Onboarding and the update screen.**
   - The disk step offers the location when two or more exist, and "נמצאה ספרייה בכרטיס הזיכרון (מהדורה …). להשתמש בה?".
   - `prepareTarget` checks space and FAT32 *before* deleting.
   - EFBIG/EROFS become install errors.
   - The scripts refuse in Hebrew while the library is on a card (the app writes `library-location.txt` on primary storage), and the site text is updated.
3. **"ייבוא ספרייה מתיקייה":** `ACTION_OPEN_DOCUMENT_TREE`, which may pick a USB stick's root. It copies a library folder through ContentResolver into staging, then follows the same verify → ready → switch path. No permission is needed.
4. **Experimental, opt-in:** a live library on a USB stick through `MANAGE_EXTERNAL_STORAGE` (`/mnt/media_rw/<UUID>`, allowed in source). Not for daily use: the stick takes the only port, and every knock kills the app.
5. **Jelly Star:**
   - probes;
   - the reduced library on a real microSD;
   - the full library on exFAT;
   - the FAT32 error on a 32 GB card;
   - cold-search timings.
   - A `journal_mode=TRUNCATE` fallback only if WAL fails there.

## Emulator test plan (reduced library)

- **A card:** `emulator\mksdcard.exe -l ZAYITSD 2048M sd.img`, then start the AVD with `-sdcard sd.img -no-snapshot`. Expect `sm list-volumes public` to show it mounted.
  - Fallback: as root, `truncate -s 2G /data/misc/vold/virtual_disk; sm set-virtual-disk true; sm partition disk:7,NN public`.
  - Never run `sm set-virtual-disk false` afterwards: it deletes the image.
- **Tests:**
  - **Move:** phone → card and back.
  - **Unmount while reading:** `sm unmount public:<id>`. Expect the Missing screen, not the update screen, and no new `seforim.db` on the phone. `sm mount` → the app continues.
  - **Interrupted moves:** force-stop mid-copy, and unmount mid-copy.
  - **Cancel** the move.
  - **Too little space:** a 768 MB card.
  - **A copy already on the card** (as root, `cp -r` into the card's `Android/data`).
  - **Script push while the library is on the card.**
  - **Reset** with the card in and with it out.
  - **Delta versus move:** each refuses while the other runs.
  - **USB simulation:** `sm set-force-adoptable off`, restored with `default`.
  - **Read-only remount.**
  - **Cold-search timings** with phone and card compared.
  - **A widget tap** while the card is out.
  - **Screenshots** at Jelly Star width.
- Check logcat `ZayitLibrary` for `journal_mode=wal` and `mmap_size=0`, and that no `SQLITE_IOERR_SHM*` appears.

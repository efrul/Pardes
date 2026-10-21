# Pardes on Zayit 0.6.7: plan

Written on 2026-10-20 from a read-only review of Zayit v0.6.1..v0.6.7, SeforimLibrary 381fbea..7ddf045 and this
repository. Phase 1 is done (the merge commit `Merge Zayit v0.6.7 into Pardes`), and so is Phase 2 (Pardes for
Android on 0.6.7, checked on the emulator with a test library on 2026-10-21; row 3, the "עוד N" chip, was dropped). Where the sections below disagree
with "Decisions taken after the review", that section wins.

## Decisions taken after the review (2026-10-20)

- **No pull request to Zayit**, but the code stays fit for Zayit: personal books follow Zayit's conventions so Zayit
  could adopt them as they are, and Pardes branding stays in one place so upstream merges stay easy.
- **Windows installer: an NSIS `.exe` like Zayit's** (not MSI), with Pardes's own id. Drop the MSI items below
  (`upgradeUuid`, `msi { }`, `packageGraalvmMsi`, `"msi"` in the updater); keep NSIS as upstream has it.
- **Platforms: exactly Zayit's, without macOS.** Windows x64 (GraalVM) and arm64 (JVM); Linux x64 and arm64 as deb,
  rpm and pacman. The release workflow takes an input to build Windows x64 only, for quick tests.
- **Updater: option A**, a Pardes provider that follows the `desktop-*` releases of efrul/Pardes.
- **Version: Pardes for desktop has its own line**, starting at 1.0.0, described as based on Zayit 0.6.7.
- **An installed Zayit's library is used in place, never copied.** On first run, Pardes for desktop looks for Zayit's
  library (Zayit's `database_path` preference, then Zayit's default folder) and, when it is recent enough, offers to
  point Pardes's own `database_path` at it, as efrul/zayit-move does for Zayit. Then:
  - Pardes never deletes a library in a folder it does not own: its reset and its full update must not run
    `DatabaseCleanupUseCase` on Zayit's folder. A full update of a shared library is installed in place, in that
    folder, or Pardes asks first.
  - Delta updates by either app are safe: the content hash check makes the second app see an up-to-date library.
  - Notes, settings and sessions stay separate (each app's own `settings/` folder).
- **No OS URL scheme for now.** `protocols.clear()`; in-app `zayit://` links keep working.
- **Android tablets keep the scrolling tab strip**; the "עוד N" chip stays a recorded deliberate difference
  (Phase 2 row 3 is dropped).
- **deb Maintainer:** `Pardes <342689225+efrul@users.noreply.github.com>`; vendor "Pardes".
- **Decided without asking** (change if needed): one SeforimLibrary submodule for desktop, Android and the shared
  module once Phase 2 lands; the `font_rashi_amiti` string stays deleted; desktop releases are not marked Latest
  (`--latest=false`), so the Android release stays the repository's Latest; data folder id `io.github.efrul.pardes`;
  `dco.yaml` stays as it is.
- **Still open:** the olive-grove wallpaper's licence (desktop and Android); a desktop download section on the site.

## Decisions (the user's, as given)

- **Base.** The desktop fork moves to Zayit's latest release, v0.6.7 (7549ca72). Pardes for Android is brought to 0.6.7 parity too, and this happens before personal books. Parity means the full update: SeforimLibrary, schema, search and every screen change since 0.6.1.
- **Desktop.** Ship "Pardes for desktop" (פרדס למחשב) for Windows (MSI) and Linux (deb), not macOS. It gets its own app id and its own data folder, so it installs side by side with Zayit.
- **Compatibility with Zayit (revised).** There is no pull request to Zayit, and no PR work is planned. The code must still fit Zayit:
  - The personal-books module and its integration follow Zayit's conventions, so Zayit could adopt them as they are.
  - All Pardes branding sits in one place, so later upstream merges stay easy.
- **Shared code.** A new Kotlin Multiplatform module in this repository (JVM + Android), depending only on SeforimLibrary core/dao.
  - Desktop includes it as a Gradle module. Android compiles it through srcDir, as it does core/dao.
  - SeforimLibrary itself stays unmodified.
- **Personal books.**
  - Formats: Otzaria .txt (lines, `<h1>`–`<h6>` headings), plain .txt, Word .docx, and a whole folder (folders become categories).
  - Search: always included in the regular search.
  - Tree: also inside existing library categories, not only under a "ספרים אישיים" root.
  - No backup or transfer: each device keeps its own personal books.
  - Re-import replaces in place and keeps the ids of unchanged lines, so their notes, highlights and bookmarks survive.
  - Otzaria-style links from personal books to library books, shown in the commentary pane.
- **Name and authorship.** The "Authorship and name" rules in `.claude/skills/zayit-android/SKILL.md` apply to every new text, desktop included:
  - Pardes is based on Zayit by Elie (Elyahou) Gambache and is not on his behalf.
  - Keep his copyright line, the AGPL notice and the §7b line word for word.
  - Link the source at https://github.com/efrul/Pardes. Don't name the porter.
  - Never bring back Rashi Amiti or the Shutterstock temple picture.

## Phase 1: Merge upstream v0.6.7 into the repository

**Facts.**
- HEAD is 1f68b307. Merge-base is e8331339 (2026-06-21), with 717 upstream commits since. 447 desktop files differ, so cherry-picking is not possible and the whole tree must be merged.
- A dry run (`GIT_LFS_SKIP_SMUDGE=1 git merge --no-commit --no-ff v0.6.7`) stopped with 7 conflicts and 509 staged paths. Nothing under `androidApp/` or `.claude/` changes.
- `FontCatalog.kt` and `strings.xml` auto-merge with Rashi Amiti still removed (scratchpad `merge-out.txt:12-13`).
- Evidence files: `/tmp/claude-0/-home-user-Pardes/d1d7460e-a5fd-51ff-b9e1-7b6493dbdaf0/scratchpad/merge-out.txt`, `/tmp/claude-0/-home-user-Pardes/d1d7460e-a5fd-51ff-b9e1-7b6493dbdaf0/scratchpad/merge-status.txt` and `/tmp/claude-0/-home-user-Pardes/d1d7460e-a5fd-51ff-b9e1-7b6493dbdaf0/scratchpad/img2/cmp.png`.

### Conflicts and resolutions

| Path | Kind | Resolution |
|---|---|---|
| `.github/dependabot.yml` | Deleted here, modified upstream (c7e25b46) | `git rm` (stays deleted) |
| `.github/workflows/deploy.yaml` | Deleted here, modified upstream: Nucleus pins, Zayit secrets, GPG, direct publish in 5a1b6068 | `git rm`. It would publish Zayit-branded installers and feeds. Pardes gets its own workflow in Phase 3. |
| `CLAUDE.md` (one hunk, about line 100) | Both sides add sections | Keep the Pardes section first, then upstream's "Multi-Window & Virtual Desktops" and "Open Core: the Smart Siddur" word for word. Add one line: Pardes builds only the community edition and never sets `siddur.token`, `SEFORIM_SIDDUR_TOKEN`, the semantic or quotelinks tokens, or `-P*.local`. Mark upstream's "Git & Pull Requests" section (DCO, CLA, PRs to master) as Zayit's own rules; Pardes sends no PRs. |
| `SeforimApp/build.gradle.kts` (about line 225) | Pardes's only edit was inside the commented `// android {}` block (`applicationId "io.github.efrul.zayit"`), which upstream deleted | `git checkout --theirs`. This also drops the stale id. All desktop identity goes into `branding/` (Phase 3), never into this file. |
| `SeforimApp/src/commonMain/composeResources/drawable/temple_jerusalem_in_fire_{large,medium,small}.jpg` | Modified here (Roberts crop), deleted upstream (0c8e6c3c) | `git rm` all three. Nothing in v0.6.7 references them. |

### Introduced cleanly by the merge, but must be handled in the merge commit

- **`SeforimApp/src/commonMain/composeResources/files/temple_jerusalem_in_fire.webp`.** This is the Shutterstock picture again (animated, 361 frames).
  - Overwrite it with `androidApp/app/src/androidMain/res/raw/temple_jerusalem_in_fire.webp` (Roberts crop, 400x320, 40 frames).
  - The replacement must stay animated. `TempleCountdownWidget.kt` around lines 199-211 indexes `codec.framesInfo`.
  - Then change `.claude/skills/zayit-android/scripts/temple-picture.py` to write this WebP and stop writing the three drawable JPGs.
- **`.github/workflows/cla.yaml`** (new; `pull_request_target` with write permissions; Zayit's CLA): `git rm`.
- **`CLA.md`**: keep it as an upstream document; the renamed Zayit READMEs link to it.
- **`CONTRIBUTING.md`**: leave unchanged so the diff stays at zero. Optionally fix its link at `:20` from `README.MD` to `README-zayit-desktop.md`.
- **`.github/workflows/ci.yaml`** and **`dco.yaml`**: keep unchanged. Without the secret, CI builds the community edition. Branches merged through PRs inside efrul/Pardes then need `git commit -s`; if that is unwanted, delete `dco.yaml` like `cla.yaml`.
- **`zayit_logo_branch.png`, `website/public/open.html`, `icons/.../WritingHand.kt` and `Share.kt`**: keep them. The logo is replaced at build time by the branding overlay. `WritingHand` is CC BY 4.0 and also enters the Android build through `../icons`.

### Things that must stay removed

- `.github/workflows/deploy.yaml`, `pages.yaml` and `update-download-count.yaml` (upstream deleted the last one too), plus `.github/FUNDING.yml`, `.github/dependabot.yml` and now `cla.yaml`.
- **Rashi Amiti**:
  - `composeResources/font/RashiAmiti.ttf`;
  - the `FontCatalog.kt` lines (v0.6.7 lines 62, 110, 148 and 225-228);
  - the `font_rashi_amiti` string. It is unused, so restoring it is optional and would make `strings.xml` identical to upstream. Do not restore the font or the catalog entry.
- The Shutterstock temple images, in any format.
- Zayit's secrets and private-package access. Never configure them in efrul/Pardes (see Phase 3).

### Submodules

- **Root `SeforimLibrary`.** Moves from 1e50301 to 7ddf045 as a fast-forward with no conflict (137 commits). Desktop 0.6.7 needs this, because `main.kt:150-178` uses `CliHost`.
- **`SeforimAcronymizer`.** Unchanged at dea83b3.
- **`androidApp/upstream/SeforimLibrary`.** Untouched by the merge and stays at 381fbea. Phase 2 bumps it.

### Fixes in the same branch, right after the merge

- `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/typography/FontCatalogTest.kt:18`: change `assertEquals(28, …)` to `27`.
  - v0.6.7 has 28 options including `rashiamiti`; Pardes has 27.
  - `:SeforimApp:jvmTest` already fails at HEAD for this reason.
- The merged desktop now requires library version `20261011170830` (`DatabaseVersionManager.kt:58`; HEAD required `20260601094341`). The schema gains author_alias, author_bio, license, edition and book_edition.
- No action is needed for that requirement: Pardes for desktop uses its own data folder and downloads a current library.

### Commands

1. `git merge --no-ff --no-commit v0.6.7`
2. `git rm .github/dependabot.yml .github/workflows/deploy.yaml .github/workflows/cla.yaml SeforimApp/src/commonMain/composeResources/drawable/temple_jerusalem_in_fire_{large,medium,small}.jpg`
3. `cp androidApp/app/src/androidMain/res/raw/temple_jerusalem_in_fire.webp SeforimApp/src/commonMain/composeResources/files/temple_jerusalem_in_fire.webp`
4. `git checkout --theirs SeforimApp/build.gradle.kts`, edit `CLAUDE.md` to keep both sides, fix `FontCatalogTest.kt:18`, then `git add` and commit.

Never push upstream `v*` tags to origin. Origin holds only `android-*` tags, and the desktop versioning depends on that (Phase 3).

## Phase 2: Update Pardes for Android to 0.6.7

Paths below are relative to `K = androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/`.

### Changes to port, or only partly present

| # | Change (upstream commits) | Android files | Effort |
|---|---|---|---|
| 1 | **Search API change in SeforimLibrary c3190c4.** This is the one compile break from the bump. `rangeSnippet` gains `lineStart`/`lineEnd`, and `LineHit` gains `lineStart`/`lineEnd`. Port the two upstream hunks. Leave `highlightsByMeaning` at its default of false. | `K/search/LuceneSearchEngine.kt`. **:332** becomes `override fun rangeSnippet(text, ranges, lineStart: Int, lineEnd: Int) = SnippetBuilder.build(text, ranges.map { Occurrence(it.first, it.last + 1, 0, EvidenceLevel.LITERAL) }, lineStart, lineEnd)`. **:277-284**: `hit.copy(…, lineStart = source.lineStart, lineEnd = source.lineEnd)`. **About :813-828**: fill the same fields where `LineHit` is built. Update the header at **:1-2**. | S |
| 2 | **Tabs slide to their new place** on pin, unpin, reorder and reopen; a new tab grows in place (a991be12, a009b0d4, 6ba92fb7, ce6e2b0c, f8057ed1) | `K/ui/shell/TopBar.kt`: add `Modifier.animateItem()` to `TabChip` in `TabStrip`'s `itemsIndexed` (about :318). Same for the phone list in `K/ui/shell/ZayitApp.kt` (about :519). | S |
| 3 | **Tab overflow chip "עוד N"** (ec91acdc, df4b7d12). Partly present: the tablet strip scrolls and the phone shows a list. `PARITY.md` records it as deliberately not ported, but the "all screen changes" decision means porting it to the tablet strip (≥840dp): a 112dp minimum tab width; a chip whose popup lists hidden tabs (reorderable, with ×, middle-click close, each tab's own menu); the chip's context menu (`close_hidden_tabs` "סגור את הלשוניות המוסתרות", `close_all_tabs`); and `+` moving into the chip's menu. The compact switcher is not needed. | `K/ui/shell/TopBar.kt` (TabStrip :301, TabChip :364), `K/ui/shell/TabMenu.kt`, `K/ui/shell/WorkspaceSwitcher.kt` (:96 only if space requires it), and the new strings `more_tabs` "עוד %1$d" and `close_hidden_tabs` beside the existing ones (`K/ui/onboarding/DesktopStrings.kt` or wherever the tab strings live) | M |
| 4 | **Preview's found-line bar is drawn, not measured** (25dde83d). Optional fidelity port. | `K/ui/search/PassagePreview.kt` (about :87-104): replace the `Row(height(IntrinsicSize.Min))` and `Box` with `Text` plus `Modifier.drawBehind{…}` on the RTL-aware side, and `.padding(start = 17.dp)` | S |
| 5 | **Preview highlights by meaning only when the engine says so** (4fe26694). Optional; it changes nothing visible on a lexical-only engine. | `K/ui/search/SearchViewModel.kt` `previewLines` (about :322-345): guard with `engine.highlightsByMeaning(query)` | S |
| 6 | **Pane shortcuts work when nothing has focus** (f96e98f3). Verify first. If Ctrl+B, Ctrl+K or Ctrl+J stop working after a sheet or dialog clears focus, route the reader's book shortcuts through the activity. | `K/MainActivity.kt:309` (`dispatchKeyEvent`), `K/ui/shell/KeyShortcuts.kt`, `K/ui/reader/ReaderScreen.kt` (:334, :381, :559-583) | S |
| 7 | **A single place where reader lines are loaded**, mirroring `LinesPagingSource.decorate` (90217b2b). Optional. It is the natural seam for personal books in Phase 4. | `K/ui/reader/ReaderViewModel.kt` :356, :379, :405: one `loadChunk()` helper with an optional decorator/router | S |
| 8 | **The modified-version notices name the base version.** Change only "0.6.1" to "0.6.7". Land the site and README together with or after the release that ships parity: `android-site.yml` deploys on every push. | `K/ui/settings/AboutSettings.kt:28`, `K/ui/onboarding/LicenceText.kt:38` (and the comment at :10), `androidApp/site/index.html:877`, `README.md:42`, `.github/workflows/android-release.yml:223`, `androidApp/README.md:91` | S |
| 9 | **The "not included" proprietary-module lists** gain "קישורי הפסוקים המצוטטים בתלמוד" (Talmud verse quote links, 90217b2b/17080195) | `K/ui/settings/AboutSettings.kt:30`, `K/ui/settings/SettingsPages.kt:139`, `K/ui/onboarding/LicenceText.kt:42`, `androidApp/README.md:84-87` and `:93`, `androidApp/docs/PARITY.md:8-10` | S |
| 10 | **Docs.** In `PARITY.md`: a 0.6.6-0.6.7 section (2a3a2f30 not applicable; aa87fa66 and 7380286b already the case; quote links private), baseline 0.6.7/7ddf045 (:3, :15), and a Compose 1.12 "to watch" note naming the plain `SelectionContainer`s rather than the reader: `AuthorScreen.kt:215,260`, `SearchParts.kt:163`, `SearchScreen.kt:513`. Fix `architecture.md` :18, :19 (vendored htmlparser) and :70 (htmlparser is 0.6.1 + f443277d/5e016dfa). `desktop-reference.md:15-16`: the reference worktree becomes 7ddf045. `androidApp/README.md:13` is already wrong: it lists `../htmlparser` and `../SeforimApp/.../sqldelight`, which are vendored; omits `../icons`, `../LICENSE`, delta-updater and `PatchTables.kt`; and its claim about the root submodule becomes false. Also update `:70`, `:81`, `DEVELOPMENT.md` (:8, :15, :19, :21, :22, :30, :51), `DESIGN.md:3`, `desktop-start.sh:7,9`, and add a new `androidApp/CHANGELOG.md` section ("### מזית 0.6.7 למחשב"; the workflow requires it). | `androidApp/docs/PARITY.md`, `.claude/skills/zayit-android/references/{architecture,desktop-reference}.md`, `androidApp/README.md`, `androidApp/docs/DEVELOPMENT.md`, `androidApp/DESIGN.md`, `androidApp/CHANGELOG.md` | S |
| 11 | **Drop vendored copies after Phase 1.** Optional, and only after the merge lands. `../htmlparser` at v0.6.7 equals the vendored copy apart from its header: change `app/build.gradle.kts:48-49` to `kotlin.srcDir(desktopRoot.resolve("htmlparser/src/commonMain/kotlin"))` and delete `app/src/androidMain/htmlparser/`. Delete `K/ui/search/WritingHand.kt` and use `io.github.kdroidfilter.seforimapp.icons.WritingHand` (same path data). The sqldelight-user comments become "0.6.7, unchanged since 0.6.1". | `androidApp/app/build.gradle.kts:48-49, 258`, `K/ui/search/UnifiedSearchBar.kt:1300`, `K/ui/search/SearchEntityPanel.kt:102`, `K/data/UserDataStore.kt:55` | S |
| 12 | **Provenance headers.** Re-check against v0.6.7 the headers whose desktop file changed: `K/data/{HistoryStore.kt:39, SearchScope.kt:8-9, SessionStore.kt:15}`, `K/search/{AcronymFrequencyCache, AuthorNames, LuceneLookupSearchService, SearchService:22}.kt`, `K/search/reference/*.kt`, `K/MainActivity.kt:75`, `K/ui/home/{HomeScreen.kt:118,209, HomeSearchBar.kt:31, widgets/LuachWidgets.kt:76, widgets/SolarSystemWidget.kt:80-81}`, `K/ui/reader/{BookTextView.kt:46, ReaderViewModel.kt:175}`, `K/ui/search/*` (PassagePreview :45, SearchParts :32, SearchScreen :195, SearchViewModel :143, SearchBarViewModel :76, UnifiedSearchBar :278), and `K/ui/shell/{KeyShortcuts.kt:28, ZayitApp.kt:133, TabMenu.kt:40, TopBar.kt:353,360, WorkspaceSwitcher.kt:65}`. Relabel the rest; `git grep -n "0\.6\.[1-7]" -- androidApp/app/src` finds 152 lines. | as listed | S–M |

**Already present (no work):**
- f443277d, 5e016dfa: accent-coloured links
- 51463d23, 1723de06, 9e3d19a8: a typed title opens the book; author scope; history key `search:a<id>:…`
- 411280b9: the tefila widget changes at צאת הכוכבים
- aa87fa66: closing the last tab leaves Home
- 7380286b: Alt+Home uses `KEYCODE_MOVE_HOME`
- 2126c238 and 90552dbc: reference resolver and `typedAsWordsOf`
- 46a1ecb8 and e3051846: preview accent colour and pinch zoom
- 1d50541d: pinned tabs at the start, with a pin mark
- 37748ba7: the `zayit://` scheme is BROWSABLE
- Library data from 9bcac65, 772151b, 7c8f42f and 651113a, which arrives through release v3

**Deliberate differences, kept as recorded in `PARITY.md`:**
- The author status line has no base/all link.
- After an author is picked, their books are filtered only by text typed after the pick.

### Library and submodule steps

1. **Bump the Android submodule.** `git -C androidApp/upstream/SeforimLibrary fetch origin 7ddf045b9a2ea9fe4ad5c6a24e0513c2022da67d && git -C androidApp/upstream/SeforimLibrary checkout 7ddf045b9a2ea9fe4ad5c6a24e0513c2022da67d && git add androidApp/upstream/SeforimLibrary`. Do this in the same commit as row 1.
   - CI already initializes it (`android-release.yml:59`), and the AGPL source archive (`:180-186`) follows.
2. **dao: no changes.**
   - Android never calls `insertLink`, `insertLinksBatch`, `insertLinkWithId`, `linkQueriesQueries`, `changes` or `selectLineKeyById` (grep finds nothing).
   - The two new `LinkQueries.sq` queries (`:147-151`) compile through `app/build.gradle.kts:248-254` (sqlite-3-24 dialect, SQLDelight 2.3.2).
3. **delta: no changes.** `git diff 381fbea 7ddf045 -- delta-updater generator/common core` is empty. Optionally reword the "(381fbea)" headers in `K/delta/*.kt`.
4. **Leave the library gate as it is:**
   - `K/data/LibraryInstaller.kt:98` `MINIMUM_REQUIRED_VERSION = "20261011170830"`;
   - `:919` `MIN_DB_VERSION`;
   - `:953-956` `REQUIRED_TABLES_QUERY`.
   - Only the comment at `:95` changes, to "Desktop 0.6.7".
5. **Update the texts that name the pin:** `androidApp/app/build.gradle.kts:12-13`, `.github/workflows/android-release.yml:56-57`, `androidApp/README.md:13`, `PARITY.md:3,15`, `architecture.md:18`, `desktop-reference.md:15-16`, `.claude/skills/zayit-android/SKILL.md:129`, and the headers of `K/search/LuceneSearchEngine.kt:2` and `MagicDictionaryIndex.kt:2`.
6. **One submodule or two.** After Phase 1 the root submodule is at the same 7ddf045. Recommended once Phase 1 has landed: point Android at `../SeforimLibrary` and remove `androidApp/upstream/SeforimLibrary`. Desktop, Android and the shared module then cannot drift apart. This means changing:
   - `app/build.gradle.kts:14`;
   - `.gitmodules`;
   - `android-release.yml:58` (init) and `:180-186` (source archive).

   Otherwise, always bump both together.
7. **Build check** (when builds are allowed): `cd androidApp && ./gradlew :app:assembleRelease`.

### Data compatibility for existing installs

- **seforim.db: nothing to do.**
  - There is no DDL change; `core/` and `Database.sq` are unchanged, and there are no `.sqm` migrations.
  - `SeforimDb.Schema.version` is unchanged, so `AndroidSqliteDriver` (`K/data/LibraryStore.kt:199-218`) never calls `onUpgrade`.
  - Every device keeps its current library.
- **Library releases.**
  - v3-20261012193827 is current, and Android's size constants already describe it (`LibraryInstaller.kt:896-910`).
  - A device on v2-20261011223929 gets `patch-v2-v3`.
  - A device on the withdrawn v2-20261011170830 passes the gate but gets the full bundle, as it does today.
  - `LogicalContentHasher` covers the same 31 tables and `PatchDbSchema` is still 1, so deltas work unchanged.
- **Lucene.** No index field changes; the changes are on the query side only, and Lucene stays 10.5.2. No re-index is needed.
- **user_settings.db.** The six user `.sq` files are identical in v0.6.1 and v0.6.7, so there is no migration. The history-key format equals desktop's.
- **Orphaned ids** (already true with v3; test that the UI tolerates them):
  - Notes, highlights and bookmarks on books blacklisted in 7c8f42f and 651113a.
  - `category_display_settings` (`K/data/UserDataStore.kt:170-176`) and saved `searchCategoryIds` (`K/ui/AppState.kt:558-560`) keyed by category ids that changed from v2 to v3 (9bcac65).

### Test checklist on the emulator (phone and tablet ≥840dp, RTL)

1. Install over the previous APK on a device that already has v3. The library opens with no reinstall flow, and the update check reports the library as current.
2. Search:
   - results and snippets come back;
   - the preview marks only the found line (`lineStart`/`lineEnd`) and keeps the accent colours;
   - a quoted phrase finds only those exact words;
   - Tab on an author scopes the search, and "חיפוש בספרי X: …" appears;
   - reopening that search from history keeps the author scope;
   - `בבא ק` puts the book first.
3. Reader:
   - find-in-page, and the find bar width on phone and tablet;
   - a citation link opens its line in a new tab;
   - the commentary pane shows newly flipped Otzaria commentaries (for example on זבחים).
4. Tabs:
   - pin, unpin and reopen slide (row 2);
   - a new tab grows in place;
   - on a tablet with many tabs: the overflow chip, its popup (reorder, ×), "סגור את הלשוניות המוסתרות" and `+` in its menu (row 3);
   - closing the last tab leaves Home;
   - Ctrl+Shift+T reopens.
5. Hardware keyboard: Ctrl+B, Ctrl+K and Ctrl+J after closing a dialog or sheet; Alt+Home; Ctrl+Alt+←/→.
6. Settings › אודות, תנאים and onboarding show "זית 0.6.7", the quote links in the "not included" list, the §7b line and the source link.
7. Notes, highlights and bookmarks made before the update still open. Ones on removed books or lines fail gracefully.
8. Delta update from a v2-20261011223929 library to v3.
9. If the Compose BOM ever reaches 1.12: text selection in Author and Search screens after switching tabs.
10. Small phone (Jelly Star) layout of the tab list and the search bar.

## Phase 3: Pardes for desktop

### How the branding is kept in one place

Everything below was read from the Compose Multiplatform 1.12.1 and Nucleus 3.0.0-dev-202610200221 sources, not built. The first task is a spike build that proves it.

- **`branding/` directory (new, Pardes-only).**
  - `branding/brand.gradle` is written in Groovy, because the root project has no Nucleus types on its classpath; it reads enums through `p.nucleus.class.classLoader`.
  - Presets live in `branding/zayit/` (upstream values) and `branding/pardes/`.
  - The Gradle property `seforimapp.brand` picks the preset; `gradle.properties` sets `pardes`.
  - The tree must still build as Zayit with `-Pseforimapp.brand=zayit`; CI checks that it compiles.
- **One hook line** at the end of the root `build.gradle.kts`: `apply(from = "branding/brand.gradle")`.
  - It registers `project(":SeforimApp").afterEvaluate { … }` before Nucleus's own `afterEvaluate` (the root is evaluated first), then reassigns the Nucleus values.
  - Root `build.gradle.kts` changed once upstream since the merge-base; `SeforimApp/build.gradle.kts` changed in about 10 commits. That is why the hook goes in the root script.
- **Compose resources.**
  - A same-name resource in `jvmMain` or `customDirectory("jvmMain", …)` does not work. Duplicate accessors are a compile error, and for `files/`, upstream's `commonMain` copy overwrites it.
  - What works: `customDirectory("commonMain", provider { generatedDir })`. A Pardes task copies `SeforimApp/src/commonMain/composeResources` and applies `branding/pardes/composeResources` on top: images and files replaced by path, `strings.xml` changed key by key. Accessor names do not change.
  - The task must fail when an overridden path or key no longer exists upstream. For example, v0.6.7 silently made the temple JPG overrides dead.
- **Kotlin values.** `brand.gradle` writes `BRAND_*` BuildConfig fields. The Zayit preset carries upstream's values, so each hook is one line under ktlint/detekt's 140 characters.
  - Alternative that matches upstream's addon convention: a `Brand` object compiled from `branding/<brand>/kotlin` through `srcDir`.
- **Edits that cannot be avoided** in upstream files:
  - the Rashi Amiti lines in `FontCatalog.kt`, the TTF deletion and `FontCatalogTest.kt:18`;
  - the in-place replacement of `files/temple_jerusalem_in_fire.webp`. The Shutterstock image must not be in the repository or the source tarball, so an overlay alone is not enough.

### Rebrand items

Classes: (a) = overlay or script, no edit to an upstream file; (b) = one-line hook; (c) = unavoidable edit. Line numbers refer to v0.6.7.

**Packaging identity** (all (a), set in `brand.gradle`; source lines are in `SeforimApp/build.gradle.kts`)
- **`graalvm.imageName` (231)**: `"pardes"`. The headless CLI then runs as `pardes cli …`. SeforimLibrary's CLI help still says "zayit cli"; accept that, since SeforimLibrary stays unmodified.
- **`appName` (240)**: `"פרדס"`.
- **`packageName` (241)**: `"pardes"`. It drives:
  - `NucleusApp.appId`;
  - the taskbar id `com.app.pardes`;
  - the single-instance lock `<tmp>/pardes.lock`;
  - the update cache and update marker;
  - `/opt/pardes`, `/usr/bin/pardes`, `pardes.desktop` and the deb `Package`.
- **`description` (242)**: Pardes's own Hebrew text, not Zayit's tagline.
- **publish owner/repo (245-253), `homepage` (258), `vendor` (277)**: efrul/Pardes, https://github.com/efrul/Pardes, `"Pardes"`.
- **`targetFormats` (269-276)**: `Msi, Deb`. Upstream ships NSIS.
- **`windows.upgradeUuid` (295)**: a new UUID made once with `uuidgen`, committed, and never changed.
  - It must never be Zayit's `d9f21975-4359-4818-a623-6e9a3f0a07ca`, or the MSI would upgrade Zayit away.
  - Set `msi { perMachine=false; createDesktopShortcut=true; createStartMenuShortcut=true; shortcutName="פרדס" }`.
  - The `.ico` comes from `branding/pardes/desktopAppIcons/`.
- **`linux`**:
  - `debMaintainer` (287) is his e-mail now and must change (open question).
  - `startupWMClass = "pardes"`. The default is the main class for both apps, which merges their dock icons.
  - A 512 px PNG `iconFile`; keep `menuGroup` Education.
- **`packageVersion` (286, 292)**: set from the desktop version (see Versioning).
- **`appResourcesRootDir` (256)**: point it at an overlay with a Pardes `splash.png`.
- **`protocol("זית","zayit")` (282)**: `protocols.clear()` (see Deep links).
- **macOS block (312-317)**: untouched and not built.
- **Sentry (35-40, 352-354)**: off without a DSN or token. Optionally set the plugin's telemetry to false under the Pardes preset.

**Data isolation**
- **Data folder, (b).** `main.kt:191` becomes `val appId = BuildConfig.BRAND_APP_ID`, which is `io.github.efrul.pardes`, the same id as the Android applicationId. FileKit then uses:
  - `%APPDATA%\io.github.efrul.pardes\databases` on Windows;
  - `~/.local/share/io.github.efrul.pardes/databases` on Linux.
  - Everything follows: `seforim.db`, the Lucene indexes, `settings/user_settings.db`, `session/`, `sync/`, and the pending restore and cleanup files.
- **Preferences, (b) ×2, critical.** `AppCoreBindings.kt:90` and `framework/backup/PendingUserDataRestore.kt:53` become `PreferencesSettings(Preferences.userRoot().node(BuildConfig.BRAND_PREFS_NODE))`.
  - Pardes uses `"io/github/efrul/pardes"`. Zayit's preset uses `""`, which is the root itself, so upstream behaviour is unchanged.
  - Without this, Pardes shares `database_path`, onboarding and sessions with Zayit. `DatabaseCleanupUseCase` would then delete Zayit's library during a Pardes reinstall.
- **Linux quicklist, (b).** `AppLinuxQuicklist.kt:37`: `DESKTOP_FILE_ID = "<packageName>.desktop"`. Line 36 needs nothing.
- **Nucleus runtime ids** follow from `packageName`. Check `NucleusApp.appId` in a packaged build.

**Strings and files** (all (a), through the resource overlay)
- **`strings.xml` keys** at v0.6.7 lines 2, 490, 493, 494, 530, 534, 653, 654, 655 and 662: `app_name` "פרדס", the onboarding title "מדריך ההגדרה של פרדס", "ברוכים הבאים לפרדס!", the did-you-know line, the finish screen, the update dialog, and the header lines.
  - Reuse the Android sentences (`AndroidStrings.welcomeSubtitle`, `AppHeaderCard`), adapted to "למחשב".
  - Never "נוצר על ידי אליהו גמבש" or "שנבנה באהבה…".
  - Keep line 471 (the olive-grove wallpaper description).
- **`files/ABOUT.md` and `files/CONDITIONS.md`**: Pardes versions adapted from `ABOUT_MD` (`K/ui/settings/AboutSettings.kt:25-45`) and `CONDITIONS_MARKDOWN` (`K/ui/onboarding/LicenceText.kt`). They must include:
  - "פרדס למחשב נבנה מהקוד הפתוח של זית 0.6.7";
  - the not-on-his-behalf text;
  - Copyright (C) 2025 Elie Gambache;
  - AGPL with the link https://github.com/efrul/Pardes;
  - the §7b line word for word;
  - the not-included modules (siddur, semantic search, quote links);
  - credits for Sefaria, Otzaria, SeforimLibrary, Emoji One (CC BY 4.0) and the Roberts painting.
- **Settings header, `GeneralSettingsScreen.kt:176-238`.**
  - Icon (177) and the strings at 200, 216 and 221: (a).
  - The literal "זית" at 191 becomes `app_name`: (b).
  - The GitHub link at 228 becomes efrul/Pardes: (b), required by AGPL.
  - The link at 201 to his site may stay, as credit.
  - Check while implementing that the header has room for all required lines: based on Zayit, not on his behalf, AGPL and source, not included, and §7b. If not, one more (b) slot is needed.

**Images** (all (a))
- **Drawables:** `AppIcon.png` (window and settings icon), `zayit_transparent.png` (onboarding, `InitScreen.kt:52`), `zayit_new_logo.png` and `zayit_new_logo_text.png` (Home, `HomeView.kt:671-672`; render the word פרדס in Frank Ruhl Libre as Android does), and `zayit_logo_branch.png` (`HomeView.kt:711`; same 82x88 size, no olive branch).
- **Packaging icons:** `desktopAppIcons/WindowsIcon.ico` and `LinuxIcon.png`.
- **Splash:** `src/jvmMain/assets/common/splash.png`.
- **Generation:** add a desktop mode to `.claude/skills/zayit-android/scripts/pardes-icon.py` that writes the `.ico` (16-256), the 512 px PNG, `AppIcon.png` and the logos.
- Leave `art/` and `installer/` (the Rust NSIS splash, unused with MSI) as they are.

**Other texts**
- **`core/BookErrorReport.kt:67`, (b):** "נשלח מתוך פרדס למחשב, המבוסס על זית".
- **`features/settings/data/DataSettingsViewModel.kt:68`, (b):** exports become `pardes_backup_<ts>.zip`. Importing Zayit backups keeps working.
- **`AppCoreBindings.kt:128` and `:288-294`:** no change. They are the Drive texts, hidden without a client id.
- **`HttpFileDownloader.kt:25`:** the User-Agent; optional (a/b).

**Licence-restricted (c)**
- Rashi Amiti, as in Phase 1.
- The temple WebP, replaced in place.
- If upstream changes either again, resolve the conflict by keeping Pardes's side.

**Private add-ons, telemetry, Drive** (no edit)
- Never provide `SEFORIM_SIDDUR_TOKEN`, `siddur.token` or the semantic and quote-links tokens.
- `brand.gradle` fails the build if `siddurEnabled`, `semanticEnabled` or `quoteLinksEnabled` is true. `settings.gradle.kts:48-85` would otherwise fall back to a developer's `gh` token.
- Keep the `addons/*/disabled` sources in every merge.
- No `SENTRY_*` and no `GOOGLE_DRIVE_*`; the local zip export stays.

**Licences (Pardes-only, additive files)**
- Ship the licence texts with the package; desktop Zayit has no licences screen. Copy them from `androidApp/app/src/androidMain/assets/licenses/` and add the desktop-only ones (Jewel/IntelliJ icons, Nucleus, sqlite-jdbc and others).
- Link them from ABOUT.md. LICENSE stays bundled through `licenseFile`.

### Data folder and app id (summary)

| Item | Value |
|---|---|
| FileKit id | `io.github.efrul.pardes` |
| Java preferences node | `io/github/efrul/pardes` |
| Package name, image name, WM class | `pardes` |
| Windows taskbar id (AUMID) | `com.app.pardes` |
| MSI upgrade code | new, made once, never changed |
| deb | no `Conflicts`/`Replaces` on zayit |

- The first run downloads its own library (about 5.5 GB compressed).
- The Kotlin packages (`io.github.kdroidfilter…`) and `mainClass` stay unchanged.

### Updater

- **The problem.** `framework/update/AppUpdateService.kt:430-468` uses `GitHubProvider(owner="kdroidFilter", repo="Zayit")` and forces `executableType="nsis"` on Windows (448). PATCH updates download at startup and install silently on quit (`main.kt:300-307`, `367-371`). Unchanged, Pardes would download and run Zayit's installer.
- Just pointing the provider at efrul/Pardes fails: `/releases/latest` there is the Android release, so `latest.yml` returns 404.
- **Option A (recommended).** A Pardes-only `UpdateProvider` at `branding/pardes/kotlin/…/PardesDesktopReleases.kt`.
  - It reads `api.github.com/repos/efrul/Pardes/releases?per_page=30`.
  - It takes the newest non-draft, non-prerelease release whose tag matches `^desktop-(\d+\.\d+\.\d+)-\d+(-r\d+)?$`.
  - It returns that tag's `latest.yml` or `latest-linux.yml`.
  - Hooks, (b): the provider factory at 432-433, and `"msi"` at 448. Nucleus installs MSIs with `msiexec /i … /passive`.
- **Option B.** A releases-only repository (for example `efrul/pardes-desktop-releases`) whose Latest is the desktop release. Only the owner, repo and `"msi"` hooks are needed.
- **Option C.** Ship the first release with update checks disabled (one hook).
- Linux: unsigned debs ask for a password through pkexec unless Pardes's own GPG key is used.

### Deep links

- **Why not `zayit://`.** v0.6.7 registers `zayit://` with the OS (`build.gradle.kts:282`). Side by side, this clashes:
  - Zayit's NSIS uninstaller deletes `HKCU\Software\Classes\zayit`;
  - Linux picks a handler arbitrarily.
- **Pardes registers no OS scheme.** `brand.gradle` calls `protocols.clear()`; an MSI registers nothing anyway, since Nucleus's registry macros exist only for NSIS.
- **`zayit://` stays the data and share-link format**, per the skill rule. Leave `core/deeplink/ZayitDeepLink.kt:19` untouched, and check that in-app `zayit://` links (author bios, `ContentDeepLinkHandler`) still open inside the app without OS registration.
- **Optional later:** a `pardes://` scheme. That needs `protocol("פרדס","pardes")`, an accepted-schemes hook in `ZayitDeepLink.kt` (b), and first-run registration of `HKCU\Software\Classes\pardes` through `reg.exe` for the MSI.
- The jump list's `seforim://tab|desktop` arguments are unaffected.

### Versioning

- `buildSrc/.../Versioning.kt` falls back to the newest repository tag, which here is `android-0.4.1-6`. That breaks the MSI ProductVersion and the deb version.
- The desktop version comes from its own file: `desktop/CHANGELOG.md` with `## <x.y.z> · <date>`. It must be numeric `x.y.z`.
- The workflow exports `GITHUB_REF=refs/tags/v<ver>` before Gradle, so `buildSrc` stays unchanged.

### Release workflow: `.github/workflows/desktop-release.yml` (new; never bring back deploy.yaml)

- **Trigger.** `workflow_dispatch` only, with inputs `prerelease` (bool) and `note` (string). `permissions: contents: write`, concurrency group `desktop-release`. Never on push or `v*` tags.
- **Gate.** Read the version from `desktop/CHANGELOG.md` and fail without a section for it.
  - `TAG=desktop-<ver>-<run_number>`, plus `-r<attempt>` on re-runs. It cannot collide with `android-*` or upstream `v*` tags.
  - Export `GITHUB_REF=refs/tags/v<ver>`.
- **Checkout.** `git submodule update --init SeforimLibrary` only; not `androidApp/upstream` or SeforimAcronymizer.
- **Build matrix** (unsigned):
  - `windows-latest`: `kdroidFilter/Nucleus/.github/actions/setup-nucleus@712f1e520c73fd68e1e7514b17700e577643802c` (GraalVM Oracle), then `.\gradlew.bat :SeforimApp:packageGraalvmMsi`. Output `SeforimApp/build/compose/binaries/main/graalvm-msi/*.msi`, renamed `pardes-desktop-<ver>-windows-x64.msi`.
  - Linux amd64 (consider `ubuntu-22.04` for older glibc): `./gradlew :SeforimApp:packageGraalvmDeb`, from `graalvm-deb/*.deb`, renamed `pardes-desktop_<ver>_amd64.deb`.
  - Optional: an arm64 deb on `ubuntu-24.04-arm`, and a Windows arm64 JVM MSI through `packageReleaseMsi`.
  - Unset `GH_TOKEN` for the Gradle step, so the token fallback in `settings.gradle.kts` has nothing to try.
- **Release job:**
  - Nucleus `generate-update-yml@712f1e5…` writes `latest.yml` and `latest-linux.yml`.
  - Build the AGPL complete source as `android-release.yml` does (about :180-187): `git archive` plus the SeforimLibrary submodule, giving `pardes-desktop-<tag>-source.tar.gz`.
  - Then: `gh release create "$TAG" --target "$GITHUB_SHA" --latest=false [--prerelease] --title "פרדס למחשב <ver> (בנייה N) – מבוסס על זית" --notes-file notes.md`, and upload the installers, the yml files and the source tarball.
  - Don't use Nucleus `publish-release`. It creates a Latest release titled with the bare tag and generated notes.
  - `notes.md` holds the changelog section, the based-on and not-on-his-behalf text, Copyright (C) 2025 Elie Gambache, AGPL, the §7b line word for word, and https://github.com/efrul/Pardes. No porter name.
  - `--latest=false` keeps the Android release as the repository's Latest. Android's updater (`K/update/AppReleases.kt`), `android-release.yml` and the site all filter `^android-` tags.
- **Secrets:**
  - Required: none beyond `GITHUB_TOKEN`.
  - Optional: `PARDES_LINUX_GPG_PRIVATE_KEY`, `PARDES_LINUX_GPG_KEY_ID` and `PARDES_LINUX_GPG_PASSPHRASE`, which give signed debs and password-free updates (the same `gradle.properties` lines as upstream). Later, a Windows code-signing certificate, to avoid SmartScreen warnings.
  - Never: `SEFORIM_SIDDUR_TOKEN` (and the semantic and quote-links tokens), `SENTRY_DSN`, `SENTRY_AUTH_TOKEN`, `SENTRY_ORG`, `SENTRY_PROJECT`, `GOOGLE_DRIVE_CLIENT_ID`, `GOOGLE_DRIVE_CLIENT_SECRET`, or Zayit's `LINUX_GPG_*`.

### Pardes texts outside the desktop app

- **`README.md`**:
  - `:3` and `:5` must cover both apps.
  - `:37`: the desktop tree is kdroidFilter/Zayit merged up to v0.6.7 (7549ca72), plus Pardes branding, the shared module and personal books. Also list what Pardes removed.
  - `:42`: base version 0.6.7.
  - `:30`: "זית למחשב או פרדס למחשב", once the scripts support it.
- **`CLAUDE.md:100-101`**: the heading "Pardes, the Android app" and the rule "keep Android changes under androidApp/" now conflict with the shared module and the desktop app. Reword both.
- **`.claude/skills/zayit-android/SKILL.md`**: "This Android app" becomes Pardes on both platforms. Add a short desktop section, or a new skill, covering `branding/`, the hooks and the desktop release.
- **Transfer scripts** `androidApp/tools/push-desktop-library.sh`, `push-desktop-library.ps1` and `push-desktop-library.cmd`:
  - Look for the library in this order: `SEFORIMAPP_DATABASE_PATH`, then Pardes's folder, then Zayit's.
  - Say which app's library is used. Where the scripts name the app, say "פרדס או זית".
  - Fix the notes fallback: sh `:67` and ps1 `:80` take notes from Zayit's folder even when the library is Pardes's.
  - Lines to change: sh `:2-3`, `:7-9`, `:21`, `:28`, `:57-61`, `:66-69`; ps1 `:2-3`, `:8-9`, `:21`, `:39`, `:65-68`, `:75`, `:80-81`, `:86`.
- **Same wording in other places**: `androidApp/site/index.html` :602, :721, :756, :769, :835; `.github/workflows/android-release.yml:239`; `androidApp/README.md:32`; `androidApp/docs/DEVELOPMENT.md:72`; `SKILL.md:117-118`; `desktop-reference.md:34`; `scripts/fetch-desktop-library.sh:7,16`.
- **In-app texts** pointing to the script (`DatabaseUpdateScreen.kt:588`, `SearchParts.kt:155-158`, `DbDeltaUpdateService.kt:146-149`) stay as they are.

### Keeping the code fit for Zayit and easy to merge (no PR)

1. **Branding stays isolated.** All branding lives in `branding/`, plus the one root line, the listed (b) hooks and the two (c) edits. A CI check, `branding/check-divergence.sh`, compares `git diff <last upstream tag> HEAD --name-only` for upstream-tracked desktop paths against an allowlist, and fails on any file outside it.
2. **Hooks are generic.** They read `BRAND_*` values whose Zayit preset equals upstream, so `-Pseforimapp.brand=zayit` builds Zayit unchanged. Feature code never references `BRAND_*` or Pardes names.
3. **Feature code follows Zayit's conventions:**
   - Metro DI (`@ContributesTo(AppScope::class) @BindingContainer`) and `LocalAppGraph`;
   - `SomethingView`/`SomethingViewModel` and `TabAwareViewModel`;
   - Hebrew `strings.xml` entries;
   - `addons/<x>/enabled|disabled` with a nullable `installedX`;
   - ktlint and detekt;
   - tests in `commonTest`/`jvmTest`;
   - packages `io.github.kdroidfilter.seforimapp.*`.
4. **Commits stay separate:** upstream merge, then branding, then features. Merge upstream tags with `git merge vX.Y.Z`, never a rebase. Run the resource-override guard after every merge.
5. **Don't edit upstream files without a reason.** `CONTRIBUTING.md`, `ci.yaml` and `dco.yaml` stay as upstream has them.

## Phase 4: Personal books (placeholder; the full design comes later)

Earlier findings that constrain the design:

- **Module location.**
  - The core/dao-only shared module can hold:
    - import and parsing;
    - the personal store;
    - a `LineSelectionRepository` router (dao, `LineSelectionRepository.kt:10-55`);
    - the catalog merge (`PrecomputedCatalog`, core);
    - TOC, book and commentary routers (`CommentaryWithText`, `CommentarySummary`).
  - It cannot hold:
    - the combined `SearchEngine`/`SearchSession`/`LineHit`/`SnippetProvider`, which live in SeforimLibrary's JVM-only `search` module;
    - the Lucene writer, `LuceneTextIndexWriter`, in `generator/searchindex`.
  - Those need app-side glue, or a sibling source set that depends on `search`. Android already compiles `search/src/jvmMain` (`androidApp/app/build.gradle.kts:53-57`).
- **Storage.** Never write into `seforim.db`. `LogicalContentHasher` covers book, line, link and category among its 31 tables. Any personal row breaks every delta and forces the full 5.5 GB bundle (`DeltaApplierClient.kt:82-95`, `DbDeltaUpdateService.kt:331`). Use a separate per-device database and index. The transfer scripts and `--with-notes` must not carry personal books across devices.
- **Ids.** Personal ids must be:
  - **positive**, since 0 and below mean "none" (`BookContentViewModel.kt:289, 446`; `NO_BOOK_ID = -1` in `SearchResultViewModel.kt:68`);
  - **at most `Int.MAX_VALUE`**, since Lucene stores them with `toInt()` (`LuceneTextIndexWriter.kt:105-121`);
  - **never overlapping library ids**, now or in future releases. `installedQuoteLinks.covers(bookId)` is keyed by library book ids.

  Re-import keeps line ids, so id order will not equal `lineIndex` order. Anything that lists lines must sort by `lineIndex`.
- **Search** ("always included"):
  - **Where to wrap.**
    - Desktop: wrap at `AppCoreBindings.provideSearchEngine:193`, outside `withSemanticSearch`/`HybridSearchEngine`.
    - Android: wrap at `K/search/SearchService.kt:81-87`. Keep `computeFacetsCancellable`, which `countFacets` uses at `:128-136`.
  - **What the 7ddf045 contract requires:**
    - forward every defaulted method (`highlightRanges`, `rangeSnippet(text, ranges, lineStart, lineEnd)`, `attachSnippets`, `findInBookCandidates`, `semanticFind`, `refinedSnippets`);
    - route by book id: `findInBookCandidates` returns `LongArray(0)` for an unknown book, which would leave find-in-page empty;
    - an empty filter means "no filter", so skip the side whose share of the filter is empty, and keep `[NO_BOOK_ID]` matching nothing;
    - index personal lines with `isBaseBook = true`, because the default search is base books only (`SearchResultViewModel.kt:1016-1017`, Android `SearchViewModel.kt:412`);
    - set `ancestorCategoryIds` to library and personal categories, and sum the facets;
    - interleave the two result lists by rank deterministically, because pages are replayed (`SearchResultViewModel.kt:836`).
  - **Results still pass through the repository.** These call sites need it to know personal books, or they drop personal hits: `BuildSearchTreeUseCase.kt:31, 71, 82, 87, 142`, `GetBreadcrumbPiecesUseCase.kt:22, 70`, `CategoryNavigationUseCase.kt:66`, `SearchResultViewModel.kt:1353, 1864`.
  - **`LuceneLookupSearchService` is final**, so personal titles are not found from the bar unless it is wrapped.
- **Lines, TOC and opening a book.**
  - Seam: `BookContentUseCaseFactory.kt:44-45` (a `LineSelectionRepository` router), and on Android `ReaderViewModel.kt:356, 379, 405` (Phase 2 row 7). `decorate` is not a seam.
  - About 25 desktop call-site files use the final `SeforimRepository` directly: `BookContentViewModel`, `TocUseCase`, `SessionManager`, `ContentDeepLinkHandler`, `SearchHome/ResultViewModel`, `RefLinkUriHandler`, `NotesScreen` and others. These will be merge hotspots.
  - Totals: 48 desktop files (40 in production) plus 5 `:pagination` sources; 41 on Android.
  - Hide `BookErrorReport` for personal books. Otherwise it would e-mail the user's private book to otzaria.200@gmail.com.
- **Catalog and tree.**
  - Merge personal books in `CatalogCache.kt:154-170`, which needs a new `invalidate()`.
  - Store a library category as its title path (as `categoryPath()` does since 9bcac65), not as a raw id: v2 and v3 category ids differ.
  - Android's `K/ui/library/LibraryTree.kt:136-170, 194` reads the repository directly.
- **Links.**
  - `canonicalOtzariaLink` is internal to the generator, so re-implement it: a "source" link is stored as COMMENTARY from the library line to the personal line.
  - Don't insert into the library `link` table. Since 9bcac65, `insertLink` with `id > 0` means "this id or fail", and `checkLinkIdInserted` uses a per-connection `changes()`.
  - Commentary pane: `CommentariesUseCase.kt` (created at `BookContentUseCaseFactory.kt:62-65`) and the 5 `:pagination` sources (`CommentsForLineOrTocPagingSource`, `LineCommentsPagingSource`, `LineTargumPagingSource`, `MultiLineCommentsPagingSource`, `MultiLineLinksPagingSource`). Use an app-side `CommentarySource` interface with an adapter over `SeforimRepository` (the 9b77f4c pattern), about 7 files. Android: `LineConnections.kt`, `CommentatorGrouping.kt:57`, `ReaderViewModel.kt:203, 614, 630-631`.
- **Packaging in Zayit's style.** `addons/personalbooks/enabled|disabled` with `installedPersonalBooks` as a nullable feature factory taking `(dataDir, SeforimRepository)`; Pardes enables it. It only reaches `SeforimApp/jvmMain`. `:pagination` and `:navigation` changes are ordinary edits.
- **Not recommended:** a driver-level attach of the personal DB plus TEMP views. It is tightly coupled to the dao schema.

## Risks and open questions

**Risks**
1. **Branding mechanism unproven.** The ordering of `afterEvaluate`, the `customDirectory("commonMain")` override and the Groovy root script were read from the plugin sources, not built. Spike before any other branding work. If the root hook fails, the fallback is an init script (`-I branding/pardes.init.gradle`), with the risk of a build that silently comes out branded as Zayit.
2. **Data isolation is critical.** If the `Settings()` hooks are forgotten, Pardes uses Zayit's library and could delete it during a reinstall. Test with both apps installed, on both OSes.
3. **The updater** must be changed before the first public build, or Pardes installs Zayit.
4. **Nucleus** is a dev build (3.0.0-dev-202610200221, published on the day of the tag). Pin it as v0.6.7 does.
5. **MSI with a Hebrew product name** is untested in WiX, and MSI gets no URL-protocol registration.
6. **The minimum library version** `20261011170830` names a withdrawn release. Its devices get no delta and still hold books later blacklisted. Raising the minimum would diverge from desktop.
7. **Compose 1.12** on Android (when the BOM moves): text selection after switching tabs, since `TabHost` also unplaces hidden tabs. The desktop has a bytecode patch; Android does not.
8. **Quote links** may later move into the library database. Check the next library bump for new tables or columns against `REQUIRED_TABLES_QUERY` and the hasher list.
9. **Compiling `../htmlparser` directly** (row 11) means later desktop edits reach Android untested. Keep htmlparser's `commonMain` free of JVM-only code.

**Open questions** (all but the wallpaper and the site were answered on 2026-10-20; see the top)
1. **Windows format.** MSI as decided, with no protocol registration and no NSIS hot-update layout? Or an NSIS `.exe` with its own id, as Zayit ships?
2. **Updater.** Option A (custom provider), B (separate releases repository) or C (disabled at first)?
3. **Deep links.** No OS scheme for now, or a `pardes://` scheme? Should share links stay `zayit://`?
4. **Desktop version line.** Follow the base (0.6.7; a Pardes-only fix would then look like upstream's 0.6.8), or Pardes's own line (for example 1.0.0, "based on Zayit 0.6.7")?
5. **Desktop releases** non-Latest (`--latest=false`)? Should the site get a desktop download section?
6. **Package metadata.** `vendor` "Pardes", and the deb Maintainer (Debian needs "Name <email>"): `Pardes <342689225+efrul@users.noreply.github.com>`?
7. **Existing Zayit library.** Offer in onboarding to copy (not share) an existing Zayit library into Pardes's folder?
8. **Architectures.** x64 only, or also Linux arm64 and Windows arm64? Build Linux on `ubuntu-22.04`?
9. **Data-folder id.** Confirm `io.github.efrul.pardes`.
10. **Tab overflow chip on Android tablets.** Port it (Phase 2 row 3, as "all screen changes" implies), or keep the recorded deliberate difference?
11. **Submodules.** One SeforimLibrary submodule (recommended after Phase 1) or two pinned together?
12. **`font_rashi_amiti`.** Restore the unused string, so `strings.xml` matches upstream exactly, or keep it deleted?
13. **Olive-grove wallpaper.** Keep `homepage_wallpaper_*.jpg` on desktop? Its licence is not stated anywhere, as on Android.
14. **`dco.yaml`.** Keep it (Pardes PR commits then need `-s`) or delete it like `cla.yaml`?
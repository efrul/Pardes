---
name: zayit-fidelity
description: Keeping Pardes as identical to Zayit as it can be, and writing down every departure. The mandatory rules for adding, changing or deleting any file of this repository (desktop SeforimApp and its modules, androidApp, semantic-model, branding, build, CI, docs, skills); Elie Gambache's own guidelines and coding style (comments, KDoc, naming, Metro DI, state and events, tests, Conventional Commits); the change map docs/ZAYIT_CHANGE_MAP.md (מפת השינויים), which holds every changed or added file with all its details, its check script and CI job; merging a Zayit release and Zayit's licence change after 0.6.12; why GitHub does not show Pardes as a fork. Use it before writing, changing or reviewing any file here, for every commit, and whenever Zayit, Eli, the code style, the change map or a merge comes up, even in Hebrew ("כמו בזית", "בסגנון של אלי", "מפת השינויים", "מה שונה מזית", "תמזג את זית החדשה", "למה אנחנו לא פורק").
---

# Pardes stays Zayit

The aim is to be as identical to Zayit as possible. Zayit is written entirely by one very skilled programmer, Elie
(Elyahou) Gambache, and Pardes's desktop is his code built under another name. So every file Pardes adds and every
file it changes is done with gravity: read his code first, write as he writes, keep the change as small as it can
be, and write down everything about it.

The rules below are mandatory for every change, in every session, desktop or Android.

## The rules

1. **First, no change.** Before touching a file, look for the way that leaves Zayit's code as released, in this
   order:
   1. nothing: Zayit already does it, or it can wait for Zayit;
   2. a value in `branding/` (the brand overlay; `branding/README.md`);
   3. a new Pardes file, reached from Zayit's code by a one-line hook;
   4. an edit inside a Zayit file: the smallest one, kept apart from his code (its own function or block), never
      reformatting, renaming or reordering his code around it.

   A fix of a bug in Zayit's code is written so that Zayit could take it as it is, and its entry says so ("a fix for
   both brands, to drop once upstream has one").
2. **Write as he writes.** His guidelines are in this repository: `AGENTS.md`, `CONTRIBUTING.md` › Code style, his
   sections of `CLAUDE.md`, `.editorconfig`, `detekt.yml`, `SeforimLibrary/AGENTS.md`.
   [references/eli-style.md](references/eli-style.md) sums them up with what his code and history show: structure,
   state and events, Metro, settings, comments, KDoc, tests, commits. Before writing, read the Zayit file you change
   and two or three of its neighbours, and copy their structure, names and comment voice.
   [references/lessons.md](references/lessons.md) holds what went wrong and what was found while Pardes's features
   were built (the linters, Compose and Jewel, the data, the map, an add-on of Pardes's own, someone else's code,
   Zayit's later code, a testers' build, what the user asked to be done again): read the part that fits the change
   before starting it, and add to it what a change teaches. In short:
   - comments in English, short, saying what the code is for;
   - every user-visible text in `strings.xml`;
   - tests beside the code;
   - nothing commented out or left unused.
3. **Every file that differs from Zayit has its entry in the change map, with all its details.** The map is
   [docs/ZAYIT_CHANGE_MAP.md](../../../docs/ZAYIT_CHANGE_MAP.md). The entry goes in the same commit as the change:
   - **A Zayit file changed, deleted or renamed:** a `#### \`path\`` entry with the fields Change, What (every
     change in the file, by function and key), Why, Kind, Merge risk and Tests, plus Renamed from for a rename.
     Smaller and Notes go in when there is something to say. The Change field holds the file's line counts against
     the base (`+4 −0`).
   - **A file added inside one of Zayit's modules** (SeforimApp, buildSrc, navigation, …): a `#### \`path\`` entry
     with the fields Feature, Size (`102 lines`, or `binary`), What, Reached from (the hook in Zayit's code), Follows
     (the Zayit code it copies or extends) and Tests.
   - **Any other added file** (androidApp/, semantic-model/, branding/, docs/, workflows, skills, …): its row in its
     folder's table: what the file is, and where it comes from (Pardes's own, a port or a copy of a named file, a
     third party's), with a note after the description when something needs saying.
   - **A file that already has an entry:** rewrite the entry to describe the file as it is now. The entry is not a
     log; the commits are.
   - **A file back to Zayit's text:** drop its entry.

   Then run the check, which must report that the map covers every file:
   ```bash
   python3 .claude/skills/zayit-fidelity/scripts/check_change_map.py
   ```
   It compares the working tree, untracked files included, with the map's base, and the line counts of every
   detailed entry with the file's: once a file changes, the check fails until its entry has been gone over again. CI
   runs the same check on every push and pull request (`.github/workflows/change-map.yml`, with `--committed`).
4. **Lint and test what you touched.** `./gradlew ktlintCheck detekt` on the module, the module's tests, and the app
   itself for a UI change (`pardes-desktop-run` for desktop, `zayit-android` for Android). ktlint must pass, as in
   his CI. detekt is his configured standard: a new file keeps to `detekt.yml`, and a change to his file adds no
   issue to it (his largest files are already past the size limits; don't make them grow more than the hook needs).
   - `androidApp/` has neither ktlint nor detekt yet (the map's section 4): keep its code to the same 140 columns
     and `detekt.yml` limits by hand.
   - Run Gradle through `.claude/skills/pardes-desktop-run/scripts/gradle.sh`: it sets `LC_ALL=C.UTF-8`, puts a `gh`
     that exits 1 first on `PATH`, so the build never reads Zayit's private packages, and refuses while the app
     runs.
   - In the cloud container, Maven Central often answers 429 through the proxy: `pardes-desktop-run`'s `setup.sh`
     gives Gradle a mirror of it.
   - Only the Change map check runs on a push to a branch. CI's lint and test jobs run on a pull request to `main`:
     run them here before every push, and open a pull request when the user asks.
5. **Commit as he commits:** `type(scope): summary`, signed off, as efrul (references/eli-style.md › How he works, and
   › Pardes in his style for Pardes's own scopes).
6. **Never**:
   - edit `SeforimLibrary/` or `SeforimAcronymizer/` (Zayit's submodules, at Zayit's pins);
   - push Zayit's `v*` tags, or open a pull request on Zayit;
   - set Zayit's private-package tokens (CLAUDE.md › Open Core);
   - merge a Zayit release without the user's decision (see Merging a Zayit release).

## The change map

`docs/ZAYIT_CHANGE_MAP.md` (מפת השינויים) describes every file that differs from the Zayit release Pardes is based
on, named by its `<!-- zayit-base: v<x.y.z> <sha> -->` line. The sections are:
1. the Zayit files Pardes changes, deletes or renames, area by area, each in full;
2. the files Pardes adds inside Zayit's modules, by feature;
3. Pardes's own trees (androidApp/ among them), in tables;
4. the departures from Zayit's style still to fix;
5. Zayit after the base (the releases not merged, and what a merge would meet);
6. why GitHub does not show Pardes as a fork.

Two entries as the map has them, and a row:

```markdown
#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/licence/LicenceScreen.kt`

- **Change:** modified, +4 −0 lines. Feature: Existing Zayit library.
- **What:** after the licence, with no usable library of Pardes's own, `ForeignLibraries.find()` finding one leads to
  `ExistingLibraryScreen` instead of the install.
- **Why:** an installed Zayit's library is offered for use in place, without a second download of 8 GB (`07e14dac`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small; one branch in the accept handler.
- **Smaller:** already minimal.
- **Tests:** `ForeignLibrariesTest` (the finder); the screen has none.
```

```markdown
#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/database/ForeignLibraries.kt`

- **Feature:** Existing Zayit library.
- **Size:** 102 lines.
- **What:** `ForeignLibraries`, another app's library on this machine that Pardes may use in place: `find` (the other
  app named by `BrandLibraries`: its `database_path` preference, then its default folder, when recent enough),
  `useInPlace` (points `database_path` at it, with no copy), and `isForeign` (a folder Pardes uses but must never delete
  from).
- **Reached from:** `LicenceScreen.kt`, `ExistingLibraryScreen.kt`, `DatabaseCleanupUseCase.kt`, `PendingDbCleanup.kt`,
  `DataSettingsViewModel.kt`, `LibraryLocation.kt`, `LibraryLocationViewModel.kt`.
- **Follows:** Zayit's `DatabaseVersionManager` for the version check; `branding/README.md` names it the only reader of
  `BrandLibraries`, which `LibraryLocationViewModel` now reads too.
- **Tests:** `ForeignLibrariesTest`.
```

```markdown
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/ArrangeLayout.kt` | The Home's "arrange" action: the widgets laid out again, resized within their limits, in the dealing into rows and columns with the fewest empty cells, then the fewest rows; pressed again, another order. *Note:* It uses Android's own `WidgetPlacement` and `CellPitch` (`HomeWidget.kt`, `CellLayout.kt`), so the build cannot take desktop's file as it takes the others. | Copy of desktop 0.6.10's features/home/widgets/ArrangeLayout.kt (the same in 0.6.12), identical but for the package and a header |
```

Kinds: branding hook; Pardes feature hook; Pardes feature in Zayit's file; fix of Zayit's code; licence removal;
removed Zayit file; renamed Zayit file; documentation; build or CI.

`scripts/mapedit.py` edits the map by entry, for a script written for one change: a file's line counts or size as
they are now, a text inside one entry, a row, a block before a heading. Run alone it prints each section's count of
entries, which the header's "(N files)" must say. Never edit the map with a global `sed`.

To see what a file's entry must describe:
```bash
B=$(sed -n 's/.*zayit-base: v[^ ]* \([0-9a-f]*\).*/\1/p' docs/ZAYIT_CHANGE_MAP.md)
git diff $B -- <path>                                     # the whole change against Zayit
git log --no-merges --format='%h %ad %s' --date=short $B..HEAD -- <path>
python3 .claude/skills/zayit-fidelity/scripts/check_change_map.py --stats
```

### Into main only through the check

main's ruleset (Settings › Rules › Rulesets, the user's decision of 2026-11-09) lets main point only at a commit on
which the Change map workflow's job, **Change map**, has passed, with the branch up to date with main; it also refuses
force pushes and deleting main. No one bypasses it, sessions included: they push as the user. So a change reaches
main:
- through a pull request, merged once its Change map check is green; when main has moved, merge main into the branch
  first and let the check run again; or
- by merging main into the branch, pushing the branch, waiting for its Change map check, then pushing that same
  commit to main, a fast-forward.

A push straight to main of a commit the check has not passed is refused (`Required status check "Change map" is
expected`): push it to its branch first. A branch from before 2026-11-09 has neither the map nor the workflow: merge
main into it, describe its files in the map, then merge it. Never rename the job: the ruleset requires it by name.

## Merging a Zayit release

1. **Read the release first, with its licence.** Zayit 0.6.12 and earlier are AGPL v3 with the §7b attribution
   term. On 2026-11-01, before 0.6.13 (2026-11-03), Zayit moved to the **Zayit Public License 1.0** (`LICENSE` of
   v0.6.13). It is the AGPL v3 plus three things:
   - a grant back (Section 3): whoever distributes a modified version gives Zayit's author a licence on its
     modifications, under any terms, proprietary included;
   - a wider attribution term;
   - a trademark clause.

   It is not an OSI licence. Merging 0.6.13 or later brings that licence into Pardes, so it is the user's decision:
   ask before merging, with the facts. On 2026-11-09 the user decided to stay on 0.6.12 for now; a later release is
   asked about again, its licence first. How to read Zayit's later code without merging it, and how to tell whether
   one commit is still under the AGPL: references/lessons.md › Zayit's later code.
2. **Merge** as `branding/README.md` › Merging a Zayit release says:
   - fetch the tag into a private ref (`git fetch --no-tags https://github.com/kdroidFilter/Zayit
     'refs/tags/v<x.y.z>:refs/zayit/v<x.y.z>'`) and never push it;
   - `git merge` it;
   - keep removed what Pardes removed;
   - build, and check that `-Pseforimapp.brand=zayit` still compiles.
3. **Update the map** in the merge's branch:
   - set the base line to the release (version and full sha);
   - run the check;
   - for every file Zayit itself changed, re-read the diff against the new base and rewrite the entry: What,
     Merge risk, and Smaller, which a merge often changes;
   - drop the entries of files that no longer differ.
4. Update the texts that name the base: `docs/PARDES_PLAN.md`'s header, `androidApp/docs/PARITY.md`, and the
   `zayit-android` skill › Matching desktop.

## Not a fork on GitHub

GitHub reports efrul/Pardes as `fork: false`. It was created on 2026-10-15 at 22:30:55 UTC as a new repository,
43 seconds after the first Pardes commit, and a clone of Zayit was pushed into it with that commit on top. GitHub
marks a repository as a fork only when its Fork button or API creates it. It offers no way to make an existing
repository a fork: it documents only the way out of a fork network, and a repository that leaves one cannot
rejoin it.

In git, Pardes is a fork all the same: it carries Zayit's whole history and merges each release, so `git diff`,
`git merge` and the map work as they would in a fork.

What it lacks is GitHub's own features:
- the "forked from kdroidFilter/Zayit" line;
- the ahead/behind count and Sync fork;
- comparisons across the two repositories.

Being a fork would also have costs:
- the web form and `gh pr create` would offer Zayit as a pull request's target;
- GitHub search leaves forks out unless asked.

Becoming one means a new fork with everything moved to it: issues, pull requests, releases (Pardes for Android
updates itself from this repository's releases), stars, Pages and secrets. The map's section 6 has the details.
The user decided on 2026-11-09 to keep it as it is: don't do it, and don't start it.

## Before every commit

- [ ] The change is the smallest that does the job (rule 1), in Pardes's places where it can be.
- [ ] It reads like the code around it (rule 2): comments, names, structure, strings, tests.
- [ ] Every file touched has its entry, true to the file as it is now, and `check_change_map.py` passes.
- [ ] ktlint and detekt pass on the module; the tests pass; a UI change was seen in the app.
- [ ] The commit message is `type(scope): summary`, its body says what the user sees and why, signed off as efrul.
- [ ] It reaches main only through the Change map check: a pull request, or a fast-forward to a checked commit.

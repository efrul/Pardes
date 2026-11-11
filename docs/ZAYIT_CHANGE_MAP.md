# The change map: Pardes against Zayit (מפת השינויים)

<!-- zayit-base: v0.6.12 d7777e2e6b1bc005f6be10350a88553286137faf -->

Pardes is based on Zayit by Elie (Elyahou) Gambache and is not on his behalf: he did not make it, is not
responsible for it and gives it no support. This repository carries Zayit's whole history and merges its releases,
and its desktop app is Zayit's code built as Pardes for desktop. This file describes **every file in which Pardes
differs from the Zayit release it is based on**: every file of Zayit's that Pardes changes, deletes or renames, and
every file Pardes adds.

- **Base:** Zayit v0.6.12 (`d7777e2e`, 2026-10-29), merged by `164a91f2` ("Merge Zayit v0.6.12 into Pardes").
  SeforimLibrary and SeforimAcronymizer are at Zayit's pins (`4f07145`, `dea83b3`), unchanged.
- **Written** from 2026-11-05 to 2026-11-09 from the whole diff of every file and its Pardes commits. Then
  876 files differed: 779 added, 89 modified, 6 deleted, 2 renamed.
  Today's numbers: `python3 .claude/skills/zayit-fidelity/scripts/check_change_map.py --stats`.
- **The rule** (the `zayit-fidelity` skill, mandatory): every file that differs has its entry here, describing the
  file as it is now, written in the same commit as the change. The check script compares the map with git, and CI
  runs it on every push (`.github/workflows/change-map.yml`). A detailed entry records its file's line counts, so
  the check fails as soon as the file changes again, until its entry has been gone over.

## How to read it

- **Section 1, Zayit's files that Pardes changes** (108 files): each in full.
  - **Change:** the status, the line counts against the base, the feature.
  - **What:** every change in the file.
  - **Why**, and the **Kind**: branding hook, Pardes feature hook, Pardes feature in Zayit's file, fix of Zayit's
    code, licence removal, removed or renamed Zayit file, documentation, build or CI.
  - **Merge risk:** what a merge of the next Zayit release will meet there.
  - **Smaller:** how the change could shrink, when it can.
  - **Tests**, and **Notes** where something needs saying.
- **Section 2, files Pardes adds inside Zayit's modules** (200 files, by feature): code that sits among
  Zayit's own and must read like it.
  - **Size**, **What**.
  - **Reached from:** the hook in Zayit's code.
  - **Follows:** the Zayit code it copies or extends.
  - **Tests**, and **Notes** where something needs saying.
- **Section 3, Pardes's own trees** (758 files: androidApp/, semantic-model/, branding/, siddur-open/, docs/,
  workflows, skills, …): a row each, what the file is and where it comes from.
- **Section 4** lists the departures from Zayit's style still to fix. **Section 5** is Zayit after the base,
  **section 6** why GitHub does not show Pardes as a fork.

To see a file's change: `git diff d7777e2e -- <path>`; its commits:
`git log --no-merges --format='%h %ad %s' --date=short d7777e2e..HEAD -- <path>`.

## Contents

1. [Zayit's files that Pardes changes](#1-zayits-files-that-pardes-changes)
2. [Files Pardes adds inside Zayit's modules](#2-files-pardes-adds-inside-zayits-modules)
3. [Pardes's own trees](#3-pardess-own-trees)
4. [Departures from Zayit's style still to fix](#4-departures-from-zayits-style-still-to-fix)
5. [Zayit after the base](#5-zayit-after-the-base)
6. [Not a fork on GitHub](#6-not-a-fork-on-github)

## 1. Zayit's files that Pardes changes

The entries of this section by kind:

| Kind | Files |
|---|---|
| Pardes feature hook | 42 |
| Pardes feature in Zayit's file | 29 |
| Fix of Zayit's code | 12 |
| Branding hook | 5 |
| Removed Zayit file | 5 |
| Branding hook with Pardes feature hooks or a fix | 6 |
| Licence removal | 4 |
| Documentation | 2 |
| Renamed Zayit file | 2 |
| Build or CI | 1 |

### Repository, build and CI

#### `.github/FUNDING.yml`

- **Change:** deleted. Feature: Branding.
- **What:** Zayit's two-line sponsor file (its GitHub Sponsors and Ko-fi accounts), which shows a Sponsor button on
  the repository.
- **Why:** the button would ask for money for Zayit's author on a repository that is not his
  (`5cfc56d6`, `docs/PARDES_PLAN.md` › Phase 1).
- **Kind:** removed Zayit file.
- **Merge risk:** none while Zayit leaves it unchanged; if Zayit changes it, the merge stops on a modify/delete
  conflict, and the file stays deleted (`branding/README.md` › Merging a Zayit release).
- **Tests:** none.

#### `.github/dependabot.yml`

- **Change:** deleted. Feature: Repository.
- **What:** Zayit's Dependabot configuration: weekly Gradle and GitHub Actions updates against Zayit's `dev` branch,
  with Zayit's labels and commit prefixes.
- **Why:** its pull requests would target a `dev` branch that efrul/Pardes does not have, and dependencies come with
  each merged Zayit release (`27da1951`, "Remove Zayit's Dependabot configuration from the fork").
- **Kind:** removed Zayit file.
- **Merge risk:** a modify/delete conflict when Zayit changes it (it did in `c7e25b46`); keep it deleted.
- **Tests:** none.

#### `.github/workflows/cla.yaml`

- **Change:** deleted. Feature: Repository.
- **What:** Zayit's CLA Assistant workflow (`pull_request_target` with write permissions), which asks every
  contributor to sign Zayit's `CLA.md` and stores the signatures on a `cla-signatures` branch.
- **Why:** the agreement grants rights to Zayit's maintainer, not to Pardes; Pardes takes no outside contributions
  under it. It came with the merge of Zayit 0.6.7 and was removed in that branch (`dbaff6a7`; `docs/PARDES_PLAN.md`
  › Introduced cleanly by the merge). Zayit itself deleted it in 0.6.13, when it dropped the CLA.
- **Kind:** removed Zayit file.
- **Merge risk:** a merge of 0.6.13 deletes it on both sides: no conflict.
- **Tests:** none.

#### `.github/workflows/ci.yaml`

- **Change:** modified, +3 −2 lines. Feature: Branding.
- **What:**
  - In the `build` job's Windows step, the NSIS installer is looked for as `*-nsis.exe` instead of `zayit-*-nsis.exe`,
    with a two-line comment: the file is named after the brand's package (`pardes-…` or `zayit-…`), as
    `desktop-release.yml` finds it, and the installer crate embeds it as `resources\zayit-nsis.exe` either way.
- **Why:** Pardes's NSIS installer is `pardes-…-nsis.exe`, so Zayit's pattern found nothing and the Windows build
  failed (`222925ec`).
- **Kind:** branding hook.
- **Merge risk:** one line inside the Windows step; a conflict only if Zayit rewrites that step.
- **Smaller:** already minimal; the pattern also finds Zayit's file.
- **Tests:** the workflow itself (the Windows legs of `build`).

#### `.github/workflows/dco.yaml`

- **Change:** modified, +27 −1 lines. Feature: Repository.
- **What:**
  - A `permissions:` block (contents and pull-requests: read), so the job's token may read the PR's commits.
  - The commits that a "Merge Zayit v<x.y.z> into Pardes" commit brings, found by walking its second parent's
    ancestry among the PR's commits, are left unchecked: they are Zayit's history as released.
  - Merge commits are skipped (their sign-off is not theirs to give); the success line counts Pardes's commits and
    the Zayit ones left out.
- **Why:** the check failed with 403 "Resource not accessible by integration" under this repository's default token
  permissions (`f193c904`); a PR that merges a Zayit release brings Zayit's own commits, which Pardes cannot sign off
  without rewriting them, and one of them (`d4aff54` in v0.6.12) has none (`7053b823`, `00e80408`).
- **Kind:** fix of Zayit's code (the permissions) and Pardes's own rule (Zayit's commits).
- **Merge risk:** the edits sit in the middle of the script; a Zayit change to the check's loop would conflict.
- **Smaller:** the Zayit-commit skipping could live in a Pardes workflow of its own, leaving Zayit's file with only
  the permissions block.
- **Tests:** none; the workflow runs on every PR.

#### `.github/workflows/deploy.yaml`

- **Change:** deleted. Feature: Branding.
- **What:** Zayit's release workflow (335 lines): on a `v*` tag it builds the official Zayit installers for every
  platform with Zayit's private packages and signing secrets, and publishes them and their update feeds.
- **Why:** run here it would publish Zayit-branded installers and feeds from a repository that is not Zayit's, and it
  needs Zayit's secrets and private packages, which Pardes must never have (`5cfc56d6`). Pardes for desktop has its
  own `desktop-release.yml`.
- **Kind:** removed Zayit file.
- **Merge risk:** a modify/delete conflict whenever Zayit changes it (often: 0.6.13 adds the Microsoft Store and the
  Linux tarball); keep it deleted, and look at what changed for `desktop-release.yml`.
- **Tests:** none.

#### `.github/workflows/pages.yaml`

- **Change:** deleted. Feature: Branding.
- **What:** Zayit's workflow that deploys `website/` (zayitapp.com's site) to GitHub Pages on a push to `master`.
- **Why:** it would publish Zayit's site from this repository; Pardes's site is `androidApp/site/`, deployed by
  `android-site.yml` (`5cfc56d6`).
- **Kind:** removed Zayit file.
- **Merge risk:** a modify/delete conflict when Zayit changes it; keep it deleted.
- **Tests:** none.

#### `.gitignore`

- **Change:** modified, +22 −1 lines. Feature: Repository.
- **What:**
  - Zayit ignores the whole `.claude/`; Pardes ignores `.claude/*` but keeps `.claude/skills/`, the project's skills.
  - Local binaries are ignored: databases (`*.db`, `-wal`, `-shm`), Lucene indexes (`*.lucene`, `*.luceneindex`,
    `*.lookup.lucene`, `*.lookupindex`), models (`*.onnx`, `*.i8`, `model/`), `standalone_semantic/` and `staging/`.
  - Python's caches and virtual environments for `semantic-model/` (`__pycache__/`, `*.pyc`, `.venv/`).
- **Why:** the skills are shared with every Claude Code session on the repository (`5cfc56d6`); the libraries,
  indexes and models of the semantic search must never be committed (`20e2fa81`).
- **Kind:** build or CI.
- **Merge risk:** the `.claude/` line conflicts if Zayit edits it; the rest is appended at the end.
- **Tests:** none.

#### `CLAUDE.md`

- **Change:** modified, +10 −0 lines. Feature: Repository, Pardes's rules.
- **What:**
  - A section "Pardes, the Android app (`androidApp/`)": the name and authorship rule (based on Zayit by Elie
    (Elyahou) Gambache, not made by him), the separate Gradle build, and never editing desktop sources for Android.
  - A section "Pardes stays Zayit: the change map": the `zayit-fidelity` skill, Zayit's guidelines, Conventional
    Commits, and this map in the same commit as every change, with `check_change_map.py`.
  - After Zayit's open-core paragraphs: Pardes builds only the community edition, never sets Zayit's private-package
    tokens or `-P*.local` flags, has its own open smart siddur (SeforimOpenSiddur's engine in `siddur-open/`, its
    screen in `addons/siddur/open`, experimental and off until turned on), its own links in the texts (`TextLinks`,
    handed over by `addons/quotelinks/disabled`), and its own open semantic search, whose pack comes from
    `semantic-*` releases.
  - Security: the Kaggle account is never named in the repository, its workflows or their logs.
  - Git: commits as `efrul <342689225+efrul@users.noreply.github.com>`, author and committer, signed off.
- **Why:** this file is what every Claude Code session reads first, so Pardes's rules go where they cannot be missed;
  Zayit's text is kept word for word around them (`docs/PARDES_PLAN.md` › Phase 1, `CLAUDE.md` conflict).
- **Kind:** documentation.
- **Merge risk:** each Zayit release edits this file (0.6.13 changes its licence line); the additions are separate
  paragraphs, so conflicts are small, but they happen.
- **Smaller:** the three Pardes paragraphs could become one pointer to a Pardes file; they stay here because they
  must be read by every session.
- **Tests:** none.
- **Notes:** Zayit's "PRs target `master`" line stays as Zayit wrote it; efrul/Pardes's default branch is `main`.

#### `LINE_SELECTION_README.md`

- **Change:** modified, +24 −0 lines. Feature: Commentator selection kept, Reader's default commentators.
- **What:** a section "Whole groups ("כל המפרשים", "ראשונים על התנ״ך")" before "Prefetching and Caching": a group's
  checkbox checks the group for the whole book (`commentatorGroupsByBook`), "כל המפרשים" checks all of them
  (`allCommentatorsBooks`), a group unchecked meanwhile is kept in `hiddenCommentatorGroupsByBook` and a commentator
  unchecked inside a checked group in `excludedCommentatorsByBook`; `CommentatorSelection` holds the rules, and a
  line's list is read with its groups (`listedCommentators`, `SelectedCommentatorsChanged.listed`,
  `CommentariesUseCase.commentatorGroupOf`). After it, "The reader's default": the pin of "כל המפרשים", what a book
  opens with, and how the default is kept by names for the books beside it.
- **Why:** Zayit documents the reader's line selection in this file, so the change of behaviour is documented in the
  same place (`3c6bae9b`, `49b456d4`).
- **Kind:** documentation.
- **Merge risk:** an inserted section; a conflict only if Zayit edits the lines around it.
- **Tests:** none (the behaviour's tests are listed under `CommentatorSelection.kt`).

#### `README-zayit-desktop.md`

- **Change:** renamed, +1 −1 lines. Feature: Branding.
- **Renamed from:** `README.MD`.
- **What:** Zayit's English README, kept word for word under a new name; its link to the Hebrew README follows that
  file's new name (`README_HE-zayit-desktop.md`).
- **Why:** the repository's front page (`README.md`) is Pardes's own; Zayit's README stays, as the description of the
  desktop code Pardes builds and as credit (`5cfc56d6`).
- **Kind:** renamed Zayit file.
- **Merge risk:** git follows the rename when Zayit edits `README.MD` (0.6.13 does); the one changed link may
  conflict.
- **Tests:** none.

#### `README_HE-zayit-desktop.md`

- **Change:** renamed, +1 −1 lines. Feature: Branding.
- **Renamed from:** `README_HE.md`.
- **What:** Zayit's Hebrew README, kept word for word under a new name; its link to the English README follows that
  file's new name.
- **Why:** as for `README-zayit-desktop.md`.
- **Kind:** renamed Zayit file.
- **Merge risk:** as for `README-zayit-desktop.md`.
- **Tests:** none.

#### `SeforimApp/build.gradle.kts`

- **Change:** modified, +29 −0 lines. Feature: Branding, Semantic search, Feedback mode, Incremental library updates.
- **What:**
  - `jvmTest`: `libs.ktor.client.mock`, for the feedback sender's tests.
  - `jvmMain`: `libs.lucene.analysis.common` (already on the runtime classpath through SeforimLibrary's search), for
    the delta's lines written with the release index's analyzer (`LuceneLineDocument`).
  - `jvmMain`: ONNX Runtime 1.17.1 (`com.microsoft.onnxruntime:onnxruntime`) and JSON-java (`org.json:json:20240303`),
    for Pardes's semantic search.
  - `buildConfig`: `FEEDBACK_URL`, from the `PARDES_FEEDBACK_URL` variable or the `feedback.url` property; without it
    feedback is only saved to files.
  - The task `buildVectorIndex` (a `JavaExec` of `VectorIndexBuilder` on the main compilation's classpath, with
    `-Xmx6g`), which builds `pardes.vec.lucene` from `semantic-model/embed_corpus.py`'s vectors.
  - The last line, `apply(from = rootProject.file("branding/brand.gradle"))`: everything that makes the build Pardes.
- **Why:** the brand overlay needs one hook at the end of Zayit's script (`branding/README.md`); the semantic search
  needs its runtime and its index task (`20e2fa81`); the feedback service's address is a build setting (`ddf604d8`);
  the fix of the delta's index lines needs Lucene's n-gram filter at compile time.
- **Kind:** branding hook and Pardes feature hooks.
- **Merge risk:** Zayit edits this file in most releases (0.6.13 does). The additions are separate lines in
  dependency blocks, in `buildConfig` and at the end, so conflicts stay small.
- **Smaller:** the two dependencies could go through `gradle/libs.versions.toml`, as all of Zayit's do; the
  `buildVectorIndex` task could move into `branding/brand.gradle` or a Pardes script applied at the end.
- **Tests:** the build itself; `VectorIndexBuilderTest` (the index), the feedback tests through
  `FeedbackTestDoubles.kt` (Ktor's `MockEngine`), and `DbDeltaLuceneSinkTest`.
- **Notes:** two dependencies are written as literal coordinates, not as `libs.` aliases from the version catalog:
  a departure from Zayit's way.

#### `SeforimApp/src/graalvm/reachability-metadata.json`

- **Change:** modified, +33 −0 lines. Feature: Semantic search, the open smart siddur.
- **What:** eight resource globs for ONNX Runtime's native libraries on `win-x64`, `linux-aarch64`, `osx-aarch64` and
  `osx-x64` (`onnxruntime` and `onnxruntime4j_jni`), beside the `linux-x64` ones Zayit already lists; and three for
  the open siddur's data in the `:siddur-open` jar (`open-siddur/amud-annotations.tsv`, `amud-inline.tsv`,
  `readings.tsv.gz`), before `org/sqlite/…`.
- **Why:** the GraalVM native image carries only the resources this file names, so without them Pardes's semantic
  search had no ONNX Runtime on those platforms (`20e2fa81`, `0c107b56`), and the open siddur's engine would find
  none of its tables, which it reads as resources (`AmudAnnotations`, `TorahReadings`).
- **Kind:** Pardes feature hook.
- **Merge risk:** two blocks inserted inside a 2,900-line generated list; a conflict if Zayit regenerates the file.
- **Smaller:** already minimal.
- **Tests:** the native builds of `desktop-release.yml`.

#### `buildSrc/src/main/kotlin/io/github/kdroidfilter/buildsrc/Versioning.kt`

- **Change:** modified, +10 −6 lines. Feature: Repository (versions from tags).
- **What:**
  - `resolveFromGitTag` asks `git describe` for the latest tag matching `v[0-9]*` only.
  - The three sources of a version (the GitHub ref, the latest tag, the GitHub API's latest tag) go through
    `String.asVersion()`, which drops a leading `v` and keeps the result only when it starts with a digit.
  - Two comments say why: a fork's other tags are not versions.
- **Why:** Pardes's `android-*`, `desktop-*` and `semantic-*` tags were read as the app's version, and Nucleus refused
  to configure with one (`e2e3da6b`); it also stopped CI's tests from running.
- **Kind:** fix of Zayit's code (it holds for any repository with other tags).
- **Merge risk:** small edits in three functions of a small file.
- **Smaller:** already minimal.
- **Tests:** none of its own; every build reads it.

#### `gradle/libs.versions.toml`

- **Change:** modified, +5 −1 lines. Feature: Commentators in their own panes.
- **What:**
  - A version `nucleusTao = "3.0.0-dev-202610272317-1-tearoff"`, with a three-line comment.
  - `nucleus-decorated-window-tao` takes `nucleusTao` instead of `nucleus`.
- **Why:** the commentator's pane torn off the reader needs `Modifier.satelliteTearOffHandle`, which only
  efrul/Nucleus's `commentator-tearoff` branch has; that one module comes from `nucleus-fork/maven` until Nucleus
  releases it (`831ad272`, `0447981b`, `nucleus-fork/README.md`).
- **Kind:** Pardes feature hook.
- **Merge risk:** Zayit bumps `nucleus` often (0.6.13 does): `nucleusTao` must then be rebuilt for the new release or
  dropped once Nucleus has the tear-off.
- **Smaller:** already minimal.
- **Tests:** the build; `CommentatorPaneIdTest` (the panes' ids; the tear-off itself is drawn by Nucleus).

#### `settings.gradle.kts`

- **Change:** modified, +7 −0 lines. Feature: Commentators in their own panes, the open smart siddur.
- **What:** in `dependencyResolutionManagement.repositories`, an `exclusiveContent` repository `NucleusFork`
  (`nucleus-fork/maven`) that serves only `dev.nucleusframework:nucleus.decorated-window-tao` at
  `3.0.0-dev-202610272317-1-tearoff`, with a two-line comment. And `include(":siddur-open")`, after Zayit's last
  `include`: the open siddur's engine as a module of this build.
- **Why:** as for `gradle/libs.versions.toml`: that one module from the repository, every other module from the
  release (`0447981b`). The engine's copy (`siddur-open/README.md`) is compiled here, with this build's Kotlin and
  versions, so it must be one of its modules.
- **Kind:** Pardes feature hook.
- **Merge risk:** a block at the top of the repositories; a conflict only if Zayit edits those lines.
- **Smaller:** already minimal.
- **Tests:** the build; `:siddur-open:jvmTest`.

### The application shell, DI, windows, update and platform

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/main.kt`

- **Change:** modified, +73 −3 lines. Feature: Branding, Library location, Semantic search packs, Feedback mode, Links in the texts, Search speed.
- **What:**
  - `val appId = Brand.APP_ID` instead of Zayit's literal `"io.github.kdroidfilter.seforimapp"` (FileKit's data folder).
  - Before the graph: `remember { PendingSemanticPack.runOnce() }` puts a pack unpacked in the settings in place of the
    old one, and `remember { PendingLibraryMove.read() }` reads a library move asked for in the settings.
  - Once the graph is made, `TextLinks.library = it.libraryLinks`: Pardes's links in the texts (the verses the Talmud
    quotes, the Chavruta's notes) read the library's links.
  - With a move pending, the catalog is not warmed (`if (pendingLibraryMove == null) warmCatalog(it)`) and the window
    shows `LibraryMoveWindow(pendingLibraryMove)` before the onboarding and the database update.
  - Once the library is open: `LaunchedEffect`s that warm the search (`warmSearch`) and, outside E2E runs, open the
    settings on the semantic search card when no pack is in use (`SemanticPackPrompt.openAtStart`), then let
    `feedbackAutoSender` ask whether to send feedback, after the session is restored, a window is open and the update
    dialog and the settings are closed.
  - `FeedbackConsentDialog(appGraph.feedbackAutoSender)` while its question is shown.
  - The private `warmSearch(graph)`: a minimum-priority daemon thread that first reads the bar's categories and the
    library's books (`libraryCategories.warmUp()`, some 1.2 s of the library's links), then waits up to
    `LEXICON_WAIT_MS` (2 minutes) for `PardesLexicon.awaitBuild` and runs `searchEngine.warmUp()`; failures are
    logged with `warnln`.
  - Imports for all of these, among them `kotlinx.coroutines.runBlocking` and `flow.first`.
- **Why:** each is the entry point of a Pardes feature: the data folder that keeps Pardes apart from an installed Zayit
  (`47626634`), the library location (`58586e1a`), the semantic packs (`20e2fa81`, `77f5349b`, `cb0f0a27`), Pardes's
  dictionary copy (`722af188`), the categories read ahead of the first letter typed in the bar, the feedback (`ddf604d8`) and the links in the texts, an object the reader reaches without
  the graph (Zayit's `installedQuoteLinks`). They must run before the graph or beside the main window,
  which only `main` can do.
- **Kind:** branding hook and Pardes feature hooks.
- **Merge risk:** high. `main.kt` is long and Zayit edits it in most releases; the hooks sit in five places (the
  `appId` line, before `createGraph`, in its `also`, the window's first `if`, the `LaunchedEffect`s after the update
  check).
- **Smaller:** the two `LaunchedEffect`s and the dialog could be one Pardes composable, `PardesStartup(appGraph, …)`,
  and the two pending steps one `PardesPending.runBeforeGraph()`, leaving three one-line hooks.
- **Tests:** none for `main`; the steps it calls have theirs (`PendingSemanticPackTest`, `LibraryLocationTest`,
  `FeedbackAutoSenderTest`, `SemanticPackPromptTest`).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/di/AppGraph.kt`

- **Change:** modified, +13 −0 lines. Feature: Reading time, Feedback mode, Dictionary window, More links, Search speed, Reader's default commentators.
- **What:**
  - `abstract val readingTimeStore: io.github.kdroidfilter.seforimapp.core.reading.ReadingTimeStore`.
  - `abstract val feedbackAutoSender: io.github.kdroidfilter.seforimapp.features.settings.feedback.FeedbackAutoSender`,
    with a KDoc line.
  - `abstract val dictionaryLookup: DictionaryLookup`, after Zayit's `toolWindows`, with its import.
  - `abstract val libraryLinks: io.github.kdroidfilter.seforimapp.features.bookcontent.references.LibraryLinks`, with a
    KDoc line.
  - `abstract val libraryCategories: LibraryCategories`, after Zayit's `searchEngine`, with its import, and
    `abstract val commentatorDefaults: CommentatorDefaults` after it, with its import.
- **Why:** composables outside a ViewModel reach the reading-time store (the learning split widget, `0083381e`) and the
  feedback sender (`main.kt`, `ddf604d8`) through the graph, as the reader's menu and the dictionary's window reach the
  lookup, and the references pane and its button the links of the other kinds; `main.kt` warms the categories up as the app
  starts, and the commentators list reads the reader's default.
- **Kind:** Pardes feature hook.
- **Merge risk:** six lines among Zayit's declarations, and three imports.
- **Smaller:** already minimal.
- **Tests:** `ReadingTimeStoreTest`, `FeedbackAutoSenderTest`, `DictionaryWindowUiTest`, `LibraryLinksTest`.
- **Notes:** the first two types and the last are written fully qualified instead of imported, unlike every other line of the file:
  fewer import lines to conflict, but not Zayit's way.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/di/modules/AppCoreBindings.kt`

- **Change:** modified, +36 −3 lines. Feature: Branding, Reading time, Feedback mode, Book and author titles search,
  Search in categories, Lexicon.
- **What:**
  - `provideSettings()` returns `Brand.settings()` (Pardes's own preferences node) instead of `Settings()`.
  - New providers: `provideSearchFeedbackStore`, `provideReadingTimeStore` (KDoc: the learning split's data) and
    `provideFeedbackService(client)`, at `BuildConfig.FEEDBACK_URL`.
  - The search engine's dictionary is `PardesLexicon.dictionaryFor(lexical.db, indexPath)`: `lexical.db` with its
    empty bases dropped and each word's commonest forms first.
  - `provideLuceneLookupSearchService` gains `catalogCache` and builds a `BookTitleIndexHolder` from the catalog
    (`BookTitleIndexLoader.load`), warmed at once, passed as `titleIndex = titles::get`; a build error is logged.
  - `provideDesktopManager` gains `libraryCategories: LibraryCategories` and passes it to each window's search.
- **Why:** an installed Zayit and Pardes must not share preferences (`47626634`); the feedback (`90f8a24a`,
  `ddf604d8`), the reading time (`0083381e`), Pardes's dictionary (`722af188`), books and authors found by name
  (`67fe471e`) and search in categories (`37d4fe60`) are bound here, as Zayit binds its own stores and services.
- **Kind:** branding hook and Pardes feature hooks.
- **Merge risk:** medium: edits inside three of Zayit's providers (`provideSettings`, the search engine, the lookup
  service, the desktop manager) besides three new ones.
- **Smaller:** the new providers could sit in a Pardes `@BindingContainer` (for example `PardesBindings`) included by
  the graph; the edits inside Zayit's providers stay.
- **Tests:** `SearchFeedbackStoreTest`, `ReadingTimeStoreTest`, `FeedbackServiceTest`, `PardesLexiconTest`,
  `BookTitleIndexTest`, `CategoryScopeSearchTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/di/modules/OnboardingBindings.kt`

- **Change:** modified, +8 −2 lines. Feature: Library location.
- **What:** `provideDownloadUseCase` takes `appSettings` and passes `targetDir = { LibraryLocation.installDir(appSettings) }`;
  `provideAvailableDiskSpaceUseCase` takes `appSettings` and measures `LibraryLocation.installDir(appSettings)`.
- **Why:** the library can be installed on another drive, chosen in the onboarding, so the download and the free-space
  check follow the chosen folder (`58586e1a`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small; two providers.
- **Smaller:** already minimal.
- **Tests:** `LibraryLocationTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/window/MainAppWindow.kt`

- **Change:** modified, +5 −0 lines. Feature: Reading time.
- **What:** the window's content gets `.noteInput()` (pointer input counts as presence) and its key handler calls
  `LastInput.now()` first; two imports from `core.reading`.
- **Why:** the reading time counts only while the user is at the window, and stops after five minutes without mouse or
  keyboard (`ReadingTimeStore`, `0083381e`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small, but Zayit edits this file (0.6.13 changes Ctrl+Tab here): the two lines sit in the window's
  modifier chain and key handler.
- **Smaller:** already minimal.
- **Tests:** `ReadingTimeStoreTest` (the store); the hook has none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/utils/WindowUtils.kt`

- **Change:** modified, +6 −6 lines. Feature: Daf view.
- **What:** in `processKeyShortcuts`, Ctrl with Plus, Equals or Minus calls `appSettings.zoomIn(tabId)` /
  `zoomOut(tabId)` instead of `increaseTextSize()` / `decreaseTextSize()`; the debug messages say "zooming".
- **Why:** when the selected tab shows the daf, the zoom keys zoom the daf (30% to 400%), not the text (`8d3825e7`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small; three branches of one `when`.
- **Smaller:** already minimal.
- **Tests:** `DafZoomTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/components/AppNativeMenuBar.kt`

- **Change:** modified, +10 −3 lines. Feature: Daf view.
- **What:** the macOS menu's zoom items call `appSettings.zoomIn/zoomOut(selectedTabId(tabsViewModel))`; a private
  `selectedTabId` reads the selected tab's id from the `TabsViewModel`.
- **Why:** as for `WindowUtils.kt`: the menu zooms the daf when the selected tab shows one (`8d3825e7`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** `DafZoomTest` (the zoom); the menu itself has none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/components/AppLinuxQuicklist.kt`

- **Change:** modified, +2 −1 lines. Feature: Branding.
- **What:** `DESKTOP_FILE_ID = BrandInfo.LINUX_DESKTOP_FILE_ID` instead of `"zayit.desktop"`.
- **Why:** Pardes's launcher entry is its own, so the quicklist must name it (`07e14dac`; `branding/README.md`).
- **Kind:** branding hook.
- **Merge risk:** one line.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/BookErrorReport.kt`

- **Change:** modified, +2 −1 lines. Feature: Branding.
- **What:** the Sefaria error report's signature is `BrandInfo.ERROR_REPORT_SIGNATURE` instead of "נשלח מתוך זית
  (https://zayitapp.com/)".
- **Why:** a report sent from Pardes must not say it comes from Zayit (`07e14dac`).
- **Kind:** branding hook.
- **Merge risk:** one line.
- **Smaller:** already minimal.
- **Tests:** none of Pardes's; Zayit's report tests do not check the signature.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/crashreporting/CrashReporting.kt`

- **Change:** modified, +3 −0 lines. Feature: No error reports.
- **What:** `val isAvailable: Boolean = BuildConfig.SENTRY_DSN.isNotBlank()`, with a KDoc line.
- **Why:** Pardes sends no error reports and has no DSN, so the settings hide the switch that would turn them off
  (`fabcf4e8`; `DataSettingsViewModel`, `DataSettingsScreen`).
- **Kind:** fix of Zayit's code (it holds for any community build).
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/backup/PendingUserDataRestore.kt`

- **Change:** modified, +2 −2 lines. Feature: Branding.
- **What:** a restored backup's preferences go into `Brand.settings()` instead of `Settings()`; the import changes.
- **Why:** Pardes's preferences live in their own node, so a restore must write there (`47626634`).
- **Kind:** branding hook.
- **Merge risk:** one line.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/database/PendingDbCleanup.kt`

- **Change:** modified, +1 −0 lines. Feature: Existing Zayit library.
- **What:** the pending deletions drop every path whose folder `ForeignLibraries.isForeign` (`.filterNot { … }`).
- **Why:** Pardes may use an installed Zayit's library in place, and must never delete its files (`07e14dac`).
- **Kind:** Pardes feature hook.
- **Merge risk:** one line in a chain.
- **Smaller:** already minimal.
- **Tests:** `ForeignLibrariesTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/update/AppUpdateService.kt`

- **Change:** modified, +16 −0 lines. Feature: Branding (updates), Linux packages.
- **What:**
  - The provider is `config.feedUrl → BrandUpdates.provider → GitHubProvider(Zayit)`: Pardes's `desktop-*` releases,
    while Zayit's preset leaves it null.
  - `Platform.Linux -> linuxPackageFormat()`, with a new `internal fun linuxPackageFormat()` that maps
    `ExecutableRuntime.type()` to `"deb"` or `"rpm"` (null otherwise), with a KDoc; one import.
- **Why:** Pardes updates from its own releases (`07e14dac`, `300e329d`); an RPM install received the feed's first
  Linux package, the .deb, so the format is now given (a fix for both brands, to drop once Zayit has one).
- **Kind:** branding hook and fix of Zayit's code.
- **Merge risk:** medium: Zayit edits this file (0.6.13 adds the Microsoft Store's updates in it), and the two hooks
  sit in the updater's set-up.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/update/DbDeltaUpdateService.kt`

- **Change:** modified, +31 −41 lines. Feature: Incremental library updates (a fix of Zayit's code).
- **What:** `defaultLuceneSinksProvider`, the sink that writes a delta's lines into the full-text index:
  - the writer analyzes with `LuceneLineDocument.analyzer()` (BuildLuceneIndex's: text_ng4's 4-grams) instead of a
    plain `StandardAnalyzer`;
  - the index's `FieldInfos` replace the one check of book_id's doc values, and each upserted line is
    `LuceneLineDocument.create(fields, line, book)`, the document SeforimLibrary's index writer writes, instead of
    Zayit's reduced one;
  - `ancestorIds`, a companion function, reads a category's ancestors from `category_closure` (cached per category);
  - the field constants and imports only the old document used are gone; `FIELD_LINE_ID` stays for the deletes.
- **Why:** Zayit's document gave `line_index` no point where the release's index has one, so Lucene refused the first
  line of every delta ("cannot change field line_index from points dimensionCount=1 ... to dimensionCount=0") and the
  index's step failed; and it indexed the raw HTML with nikud, without text_ng4 or the category ancestors, so such
  lines would not be found. `DbDeltaLuceneSinkTest` failed on Zayit's code with that exception.
- **Kind:** fix of Zayit's code (it holds for Zayit as well).
- **Merge risk:** small while Zayit leaves the sink alone; if Zayit rewrites it, keep its version and drop this fix
  once its lines match the index writer's (`DbDeltaLuceneSinkTest` says when).
- **Smaller:** already split: the document is a Pardes file (`LuceneLineDocument.kt`), the change here is the hooks.
- **Tests:** `DbDeltaLuceneSinkTest` (a line written into an index made as the release's is found by its words, inside
  a word, under its category ancestor, with a base book's order); `DbDeltaUpdateViewModelTest`.
- **Notes:** Zayit's code still has the fault; the user decided on 2026-11-09 not to report it upstream.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/platform/PlatformInfo.kt`

- **Change:** modified, +11 −2 lines. Feature: Tests on a CI runner.
- **What:** `isLowEnd` becomes `get() = lowEndForTests ?: detectedLowEnd`, with a `@Volatile internal var
  lowEndForTests: Boolean?` (KDoc: what a test sets) and the old lazy value renamed `detectedLowEnd`, which `warmUp`
  now computes.
- **Why:** the Home's widget tests depended on the machine's GPU; a CI runner's is a software one, where the 3D widgets
  are off, so the tests failed there (`8f85d914`).
- **Kind:** fix of Zayit's code (its tests).
- **Merge risk:** small; inside one property.
- **Smaller:** already minimal.
- **Tests:** `HomeWidgetsLayoutTest`, `WidgetDragUiTest`, `WidgetSizesScreenshotTest` set it.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/settings/AppSettings.kt`

- **Change:** modified, +261 −0 lines. Feature: Daf view, Library location, Semantic search packs, Feedback mode, the open smart siddur.
- **What:**
  - **Daf view:** flows and getters/setters for its zoom (`daf_zoom`, 0.3 to 4, browser-like `DAF_ZOOM_LEVELS`),
    lighter unsure lines (`daf_mark_unsure`), the review mode (`daf_review`) and its two fonts (`font_daf_gemara`,
    default Frank Ruehl CLM; `font_daf_commentary`, default Noto Rashi Hebrew). All are portable (`PORTABLE_KEYS`) and
    reset by `clearAll()`.
  - **Zoom of the selected tab:** `dafShown`/`dafHidden`/`showsDaf` count the views that show a daf per tab
    (`dafViewsFlow`), and `zoomIn`/`zoomOut(tabId)` zoom the text as before, and the daf with it to its next level
    when the tab shows one: the panes beside a daf (the commentaries, the references) are drawn in the text's size,
    and did not follow the zoom until the user asked on 2026-11-10. `zoomDaf(zoom)`, the daf's own pointer zoom (a
    pinch, Ctrl + wheel over it), zooms the text by the same ratio, as the same gestures over the text zoom the panes
    (asked on 2026-11-11); the text's size before its limits is kept (`dafText`), so zooming out and back gives the
    size it was.
  - **Library location:** `getLibraryInstallDir`/`setLibraryInstallDir` (`library_install_dir`, not portable) and
    `flush()`, which writes the preferences now, before a step that relies on them.
  - **Semantic packs:** `semantic_pack_prompt`, whether a start without a pack opens the settings on it.
  - **Feedback:** `search_feedback` (the grade buttons, with a flow), the random sender id (`feedback_sender`,
    `renewFeedbackSender`), the start question's answer (`feedback_asked`, `feedback_auto_send`), and per kind of
    feedback whether it is sent and up to when (`feedback_kind_*`, `feedback_sent_*`).
  - **The open smart siddur:** `isOpenSiddurEnabled`/`setOpenSiddurEnabled` (`open_siddur`, off by default), which
    also write `OpenSiddurSwitch.shown`, as the `init` block does at the start and `clearAll()` at a reset: the
    siddur's addon reads the switch where it has no graph to take the settings from. And the custom of the State of
    Israel's days in Iyar the open siddur asks on those days, with its flow (`siddur_israeli_prayer_custom`, an
    `IsraeliPrayerCustom`'s name), after Zayit's three siddur preferences. Both are portable.
  - Imports: `PreferencesSettings`, `flow.update`, `UUID`, `Preferences`, `OpenSiddurSwitch`.
- **Why:** Zayit keeps every preference here with its flow, so Pardes's features keep theirs here too (`8d3825e7` and
  the daf commits, `58586e1a`, `cb0f0a27`, `90f8a24a`, `ddf604d8`). The siddur's switch is the one preference here
  that is mirrored outside the class: `installedSiddur` is a top-level value Zayit's code reads in composition and
  in `main.kt`.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** high in size, low in conflicts: Zayit edits this file in most releases (0.6.12 added the search
  split), but Pardes's blocks are separate members, keys and list entries.
- **Smaller:** the feedback and daf settings could be Pardes classes over the same `Settings`, leaving only the
  portable keys and `clearAll()`'s resets here.
- **Tests:** `DafZoomTest`, `LibraryLocationTest`, `FeedbackAutoSenderTest`, `SemanticPackPromptTest`,
  `OpenSiddurSwitchTest`.

### Tabs, sessions and desktops

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/tabs/TabsContent.kt`

- **Change:** modified, +36 −2 lines. Feature: Search in categories, Feedback mode, Result tabs, Results in the library's order,
  Desktop link.
- **What:**
  - In `SearchTabContent`, a `LaunchedEffect` that collects `viewModel.scopeChanges` into `barViewModel.showScope(scope)`:
    when the search moves by itself (to a category's base books, or all its books), its bar says where it searches,
    and the tabs picked above the results are the bar's categories (`SearchScope.Global`, no tab picked any longer,
    clears them with `barViewModel.onClearCategory()`).
  - The search tab's own bar's `onRemoveScope` also calls `viewModel.unpickCategoryTab(...)` for a category: a category
    taken out of the bar is a tab picked no longer.
  - The search tab's own bar passes `onNewBarOpened = null` (a bar in a new tab opens empty, as in Zayit; this one opens
    where its search looked).
  - `feedback` is collected from `viewModel.feedbackFlow` and passed to `SearchResultInBookShellMvi`.
  - `rememberHomeSearchCallbacks` wires `onPickCategory`, `onClearCategory`, `onAddScope`, `onRemoveScope` and
    `onNewBarOpened` to `SearchHomeViewModel`.
  - `rememberSearchShellActions` adds `onScopeBaseBooksChange` (event `SetScopeBaseBooksOnly`), `onGrade` (event
    `Grade`) and `onCatalogOrderChange` (event `SetCatalogOrder`).
  - `navigate` takes `openDesktop` and gives it a search bar's `SearchHomeNavigationEvent.OpenDesktop`; the two places
    that collect a bar's navigation (the window's, and a results tab's own) pass `rememberDesktopOpener()`.
- **Why:** the search in a category and in several books and categories (`54180126`, `574f3d55`, `37d4fe60`), the new
  tab's bar (`03c84c1f`) and the grades of feedback mode (`90f8a24a`) reach the search screen through this file, where
  Zayit wires its own search callbacks; the tabs picked go into the search bar as the user asked on 2026-11-10 (no
  link of their own: a search made again from the bar searches in them). A desktop's link pasted into a bar opens
  its desktop from here, where the window and the desktops are at hand (the bar's view model has neither).
- **Kind:** Pardes feature hook.
- **Merge risk:** small to medium: one-line additions inside Zayit's callback and action builders, which Zayit extends
  when it adds a search action.
- **Smaller:** already minimal.
- **Tests:** `CategoryScopeSearchTest`, `ScopeItemsTest`, `SearchFeedbackButtonsUiTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/session/SessionModels.kt`

- **Change:** modified, +49 −5 lines. Feature: Commentators in their own panes, Commentator selection kept, Daf view,
  Search in categories, Results in the library's order.
- **What:**
  - `BookContentPersistedState` gains, appended last for ProtoBuf: `detachedCommentators` (by name; 1.0.5's slot list
    is still read), `allCommentatorsBooks`, `excludedCommentatorsByBook`, `commentatorGroupsByBook`,
    `hiddenCommentatorGroupsByBook` and `dafView`.
  - `SearchPersistedState` gains `fetchBookIds`, `fetchCategoryIds`, `fetchBaseOnlyCategoryIds` and, last,
    `catalogOrder` (the results listed in the library's order); the comment of
    `fetchCategoryId` says it is used again.
  - `scope` falls back to a new `wideScope`: the author's, the several books' and categories' (`SearchScope.Multi`), the
    category's (with `baseBooksOnly`), else everywhere. A search narrowed to one book goes back to it.
  - `withScope` writes `"category"` and `"multi"` scopes and their ids instead of turning a category into everywhere; a
    private `SearchScope.categoriesInBaseBooks()` lists the categories searched in their base books.
- **Why:** a tab restored at start must come back with its torn-off commentators (`831ad272`), its commentator
  selection (`3c6bae9b`, `49b456d4`), its daf view (`4efa3e3c`), its search scope (`54180126`, `574f3d55`,
  `37d4fe60`) and the order of its results.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** medium. The new fields are appended last, as Zayit's own comment asks, but a Zayit release that
  appends fields of its own to the same classes conflicts, and their ProtoBuf numbers would then clash with Pardes's
  saved sessions. The scope change reverses a choice of Zayit's: Zayit reads a saved category scope as "everywhere".
- **Smaller:** already minimal for the fields; the scope logic could move to a Pardes extension, but it reads private
  fields of the class.
- **Tests:** `SessionModelsTest` (changed with it), `DetachedCommentatorsTest`, `CommentatorSelectionTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/desktop/DesktopSession.kt`

- **Change:** modified, +13 −1 lines. Feature: Commentators in their own panes (Windows).
- **What:** `panesOf` creates each `SatelliteWorkspace` with `gripIcon` and `gripDraggingIcon` set to
  `PointerIcon.Hand` on Windows, with a comment: Windows has no open or closed hand, and Nucleus would show its
  four-arrow move cursor over every pane's grip, the floating windows' title bars included.
- **Why:** the four-arrow cursor over every pane header looked wrong on Windows (`9de93f98`, `0447981b`).
- **Kind:** fix of Zayit's code (it holds for Zayit's panes on Windows too).
- **Merge risk:** small; one function.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/desktop/DesktopManager.kt`

- **Change:** modified, +3 −0 lines. Feature: Desktop link.
- **What:** `createDesktop(windowId, name, tabs: WindowSnapshot? = null)`: the tabs given are kept as the new
  desktop's dormant snapshot before it opens, so it opens with them rather than a fresh Home.
- **Why:** a desktop's link opens as a new desktop holding its tabs, through the path a dormant desktop reopens by
  (its titles shown at once, each tab read only when shown), instead of opening and selecting forty tabs one by one.
- **Kind:** Pardes feature hook.
- **Merge risk:** small: a parameter with a default and one line.
- **Smaller:** already minimal.
- **Tests:** `DesktopManagerTest` (a desktop made with its tabs opens with them).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/components/DesktopSwitcher.kt`

- **Change:** modified, +1 −0 lines. Feature: Desktop link.
- **What:** `DesktopLinkRow(onDismiss)` at the end of the desktops' menu, under its "new desktop" row.
- **Why:** the menu is where a desktop is made, named and deleted: its link is copied there, as the user asked on
  2026-11-09.
- **Kind:** Pardes feature hook.
- **Merge risk:** one line.
- **Smaller:** already minimal.
- **Tests:** none of the menu; `DesktopLinkTest` for the link.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/session/SessionModelsTest.kt`

- **Change:** modified, +59 −8 lines. Feature: Search in categories, Results in the library's order.
- **What:**
  - Zayit's test `a search saved scoped to a category runs everywhere` is replaced by
    `a search scoped to a category keeps it, and a book scope drops it`.
  - New tests: several books and categories are kept and gone back to from one of their books; a category in its base
    books alone stays so, alone, with others and from one of its books; a search narrowed to one book goes back to its
    category's or author's books; a search listed in the library's order stays so. Each checks a ProtoBuf round
    trip.
- **Why:** the behaviour Zayit's test asserted is the one Pardes changed (`54180126`, `574f3d55`, `37d4fe60`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** if Zayit adds tests near the replaced one, a conflict; if it changes the category behaviour again,
  the tests disagree.
- **Smaller:** the new tests could live in a Pardes test file, but Zayit's assertion had to go.
- **Tests:** it is one.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/desktop/DesktopManagerTest.kt`

- **Change:** modified, +34 −1 lines. Feature: Search in categories, Desktop link.
- **What:** the test's `SearchHomeViewModel` takes a fifth argument, a relaxed `mockk` for `LibraryCategories`; the call
  is broken over lines. A test added at the end: a desktop made with its tabs (`createDesktop`'s third argument) opens
  with them in the window it was made from, a book at the line it opens at and its title, and the desktop left is kept.
- **Why:** `SearchHomeViewModel`'s constructor gained `libraryCategories` (`37d4fe60`); the desktop a link opens.
- **Kind:** Pardes feature hook (a test kept compiling, and one added).
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** it is one.

### The reader (book content)

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/BookContentEvent.kt`

- **Change:** modified, +41 −0 lines. Feature: Daf view, Commentators in their own panes, Commentator selection kept, Reader's default commentators.
- **What:**
  - `ToggleDafView`: shows a Talmud tractate as its Vilna page, or as its text again.
  - `DetachCommentator(name)`, `ReattachCommentator(name)` and `CloseCommentatorPane(name)`: a commentator out of the
    commentaries pane into a pane of its own, back, and its pane closed from the dock.
  - `SelectedCommentatorsChanged` gains `listed` (the commentators the line's list showed, each with its group, empty
    when unknown), so a line unchecks only what it showed.
  - `AllCommentatorsChanged(lineId, checked, listed)` ("כל המפרשים") and
    `CommentatorGroupChanged(lineId, group, checked, listed)` (a group's checkbox, "ראשונים על התנ״ך").
  - `CommentatorsDefaultSet` and `CommentatorsDefaultCleared`: the commentators the book shows set as the reader's
    default for it and the books beside it, and that default let go.
- **Why:** the daf view (`4efa3e3c`), the commentators' own panes (`d85afee7`, `831ad272`) and the selection kept from
  line to line (`3c6bae9b`, `49b456d4`) and the reader's default commentators are driven through the reader's events, as all of Zayit's reader actions are.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** small: new members of the sealed interface, and one defaulted field at the end of an existing event.
- **Smaller:** already minimal.
- **Tests:** `CommentatorSelectionTest`, `DetachedCommentatorsTest`, `DafViewStateTest`,
  `CommentariesUseCaseReaderDefaultTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/BookContentScreen.kt`

- **Change:** modified, +92 −10 lines. Feature: Daf view, Daf view (review mode), Reading time, Copy of the words alone, Dictionary window.
- **What:**
  - In `BookTextMenus`, `LocalLocatedSelection.current`: when the content locates its own selection in the library (the
    daf's printed words), the context menu's link, note and highlight take that location instead of searching the
    selected text in the book's lines; a highlight is saved per book (a comment's in its commentary's book) through
    `persistHighlights`.
  - The selection written to the `SelectionContext`: an area with nothing selected clears the tab's selection only if
    the text there is still the one it wrote (`written`), because the daf has an area per amud.
  - In `BookTextMenus`, the item "העתק טקסט בלבד" (`context_menu_copy_letters_only`) after "copy without nikud", when the
    copied text has anything but letters, digits and spaces (`hasMoreThanLetters`): it copies `lettersOnly(copiedText)`.
  - In `BookTextMenus`, the item "פתח במילון" (`context_menu_open_in_dictionary`) after "search the selected text", when
    the copied text is a word or up to four (`lookupWord`): it opens the dictionary's window on it
    (`dictionaryLookup.open`, read from the graph beside the repository).
  - In `contextItems`, a branch for `ContextMenuCheck` (the daf review's checkboxes): a passive item drawing
    `ContextMenuCheckRow`, so that a click on it leaves the menu open.
  - In `BookContentScreen`, a `LaunchedEffect` that tracks the book's reading time (`readingTimeStore.track`) while its
    tab is selected and its window focused (`windowInfo.isWindowFocused` read as a flow).
  - Imports, among them a redundant `androidx.compose.runtime.snapshotFlow` beside the file's `androidx.compose.runtime.*`.
- **Why:** highlights and notes work on the daf's printed words (`d334af46`), a selection survives while another amud
  is composed (`d9cb48b7`), the learning split widget counts the time a book is read (`0083381e`), and a text is copied
  without its nikud, teamim and punctuation, and a selected word is looked up in the dictionary, as the user asked on
  2026-11-09; the daf's review is said in two checkboxes, as the user asked the same day.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** medium: the edits sit inside Zayit's context-menu builder (`BookTextMenus`), which Zayit changes when
  it adds a menu action.
- **Smaller:** the reading-time effect could be a Pardes composable called by one line; the menu's branches need the
  menu's locals.
- **Tests:** `DafAnchorsTest` (the located words), `ReadingTimeStoreTest`, `LettersOnlyTest`, `DictionaryLookupTest`.
- **Notes:** comments here are marked "Pardes:", which Zayit's code does not do.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/BookContentViewModel.kt`

- **Change:** modified, +46 −5 lines. Feature: Daf view, Commentators in their own panes, Commentator selection kept, Reader's default commentators.
- **What:**
  - `onEvent` handles `ToggleDafView` (flips `dafView`), `DetachCommentator`, `ReattachCommentator` and
    `CloseCommentatorPane` (through `DetachedCommentators.detach/reattach`), `AllCommentatorsChanged`
    (`setAllCommentators`) and `CommentatorGroupChanged` (`setCommentatorGroup`), and passes `listed` on;
    `CommentatorsDefaultSet` (`setCommentatorsDefault`) and `CommentatorsDefaultCleared`
    (`clearCommentatorsDefault`).
  - The commentaries' prefetch reads the book's `commentatorSelection`: with whole groups checked, each line prefetches
    its own commentators of those groups (`shownOn`), not only those remembered by name.
- **Why:** as for `BookContentEvent.kt` (`4efa3e3c`, `d85afee7`, `831ad272`, `3c6bae9b`, `49b456d4`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** small to medium: new `when` branches, and an edit inside Zayit's prefetch function.
- **Smaller:** already minimal.
- **Tests:** `CommentatorSelectionTest`, `CommentariesUseCaseAllCommentatorsTest`, `DetachedCommentatorsTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/state/BookContentState.kt`

- **Change:** modified, +10 −0 lines. Feature: Daf view, Commentators in their own panes, Commentator selection kept.
- **What:** `ContentState` gains `dafView`, `detachedCommentators` (names, in the order they were split off),
  `allCommentatorsBooks`, `commentatorGroupsByBook`, `hiddenCommentatorGroupsByBook` and `excludedCommentatorsByBook`,
  each with a comment.
- **Why:** the reader's state holds what its views draw (`4efa3e3c`, `d85afee7`, `3c6bae9b`, `49b456d4`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** small; new fields with defaults.
- **Smaller:** already minimal.
- **Tests:** `CommentatorSelectionTest`, `DafViewStateTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/state/BookContentStateManager.kt`

- **Change:** modified, +12 −0 lines. Feature: Daf view, Commentators in their own panes, Commentator selection kept.
- **What:** the persisted state is read into and written from the new `ContentState` fields (`dafView`,
  `detachedCommentators` through `DetachedCommentators.names`, the four selection maps).
- **Why:** a tab restored at start comes back as it was (`SessionModels.kt`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** small; lines inside two long constructor calls that Zayit extends with each new field.
- **Smaller:** already minimal.
- **Tests:** `SessionModelsTest`, `DetachedCommentatorsTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/components/VerticalBars.kt`

- **Change:** modified, +41 −13 lines. Feature: Daf view, More links, Unflagged books.
- **What:**
  - The end bar shows a "צורת הדף" toggle (the `GemaraPage` icon, `daf_view` strings) for a Bavli tractate
    (`selectedBook.isBavli`), sending `ToggleDafView`.
  - `ZoomButtons(tabId)`: with the tab's view showing a daf (`dafViewsFlow`), the buttons zoom the daf and the text
    (`zoomIn/zoomOut`), are enabled while either can still be zoomed, and their tooltips say "400% max" / "30% min";
    otherwise the text, as before.
  - The references pane's button is enabled for a line with a link of the other kinds too:
    `repository.getReferencesForLines(placesLineIds).isNotEmpty() || libraryLinks.any(placesLineIds)`, with
    `val libraryLinks = LocalAppGraph.current.libraryLinks`.
  - The commentaries' and the sources' buttons are shown for a book whose flags miss its links:
    `commentaryEnabled = selectedBook?.hasCommentaryConnection == true || linked?.commentedOn == true`, and
    `sourcesEnabled` so with `linked?.comments`, where `linked` is `libraryLinks.connectionsOf(book.id)`, asked in a
    `produceState` for a book that lacks either flag.
- **Why:** the daf view is opened from the reader's bar, and has a zoom of its own (`4efa3e3c`, `8d3825e7`); a button
  is enabled exactly when its pane has something to show, as Zayit's comment there says. The library of 2026-10-12 has
  75 books with commentary links and no flag for them, the 38 books of חברותא and their 38 books of notes among them:
  their notes could not be opened as commentaries, nor the Gemara as their source, as the user reported on 2026-11-10.
- **Kind:** Pardes feature hook.
- **Merge risk:** small; one button among the bar's, `ZoomButtons`' body, the references' availability and the two
  `…Enabled` lines.
- **Smaller:** already minimal.
- **Tests:** `DafZoomTest`, `LibraryLinksTest` (`anyLinked`, `bookConnections`).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/references/LineReferences.kt`

- **Change:** modified, +10 −3 lines. Feature: More links.
- **What:**
  - `ReferenceKind.More(-3)`, after `Reference`: the kind of a link `LibraryLinks` reads (`in LibraryLinks.KINDS -> More`
    in `ReferenceKind.of`), the enum's KDoc saying so. `groupReferences` is untouched: a kind that is not `Reference`
    already has a filter tab of its own, and the kinds come in the enum's order, so these books come last.
  - `loadLinePlaces(…, more: LibraryLinks? = null)`: the references are the repository's and `more?.of(ids).orEmpty()`,
    its KDoc saying so.
- **Why:** the pane listed three of the library's link kinds (a reference, מסורת הש״ס, עין משפט), and four others, some
  1.7 million links, were in no pane; the user asked for them on 2026-11-09 (Otzaria lists every link that is no
  commentary).
- **Kind:** Pardes feature hook.
- **Merge risk:** small; an enum entry, a `when` branch and a parameter with a default.
- **Smaller:** already minimal.
- **Tests:** `LibraryLinksTest` (the tab of its own, after the references); Zayit's `LineReferencesTest` passes unchanged.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/LinePlacesView.kt`

- **Change:** modified, +3 −1 lines. Feature: More links.
- **What:** the pane loads its places with the links of the other kinds
  (`loadLinePlaces(kind, graph.repository, lines, graph.libraryLinks)`), and `ReferenceKind.More` has its label,
  `references_more` ("קישורים נוספים"), with its import: the tab's title, and the kind beside a place of it, as עין משפט's.
- **Why:** as `LineReferences.kt`.
- **Kind:** Pardes feature hook.
- **Merge risk:** small; an argument, a `when` branch and an import.
- **Smaller:** already minimal.
- **Tests:** none of its own (a composable); `LibraryLinksTest` for what it lists.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/BookSelection.kt`

- **Change:** modified, +43 −1 lines. Feature: Daf view.
- **What:**
  - `BookSelection.select(range)`: selects a range as a press and a Shift+press would.
  - `BookSelectionContainer(ownsSelection = true)`: with `false` it is no `SelectionContainer`, only a `Box` with
    `LocalBookSelection` null, for content that holds its own selection (the daf's runs of lines).
  - `BookSelection.selectInLine(line, start, end, diacritics)`: selects a span of a line's plain text at its displayed
    offsets, mapping through the diacritics stripping off the UI thread: the daf's word clicked, in the text.
- **Why:** the daf is drawn inside the text view's own frame (`e00754a4`), and a word clicked on the daf selects it in
  the library's text (`d334af46`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** small; additions beside Zayit's functions, and one defaulted parameter.
- **Smaller:** `selectInLine` could live in the daf package; it is here beside `highlightRangesOf`, which it mirrors.
- **Tests:** `DafAnchorsTest`.
- **Notes:** two KDocs start with "Pardes:", which Zayit's code does not do.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/usecases/BookContentUseCaseFactory.kt`

- **Change:** modified, +3 −1 lines. Feature: Reader's default commentators.
- **What:** a fifth constructor parameter, `commentatorDefaults: CommentatorDefaults`, injected and handed to
  `CommentariesUseCase` in `createCommentariesUseCase`, with its import.
- **Why:** each tab's `CommentariesUseCase` reads the reader's default commentators when a book opens, and this factory
  is where Zayit builds it.
- **Kind:** Pardes feature hook.
- **Merge risk:** small: a parameter at the end and an argument.
- **Smaller:** already minimal.
- **Tests:** `CommentariesUseCaseReaderDefaultTest`; Zayit's `BookContentUseCaseFactoryTest` unchanged.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/usecases/CommentariesUseCase.kt`

- **Change:** modified, +137 −41 lines. Feature: Commentator selection kept, Reader's default commentators.
- **What:**
  - `applyDefaultCommentatorsForBook` returns when the book has any selection, by name or by whole groups
    (`commentatorSelection(bookId).isEmpty`).
  - `updateSelectedCommentators(lineId, selectedIds, listed)`: what the line unchecks is limited to what its list
    showed, and the book's selection changes through `CommentatorSelection.changed` instead of Zayit's inline
    additions/removals.
  - New `setAllCommentators` ("כל המפרשים") and `setCommentatorGroup` (a group, counting the group's commentators that
    the book remembers or hides and the line does not list), `commentatorGroupOf` (a commentator's group label, cached)
    and `listedCommentators(lineId)`.
  - `reapplySelectedCommentators` and its multi-line twin go through a new `reapplyCommentators`, which reads the
    selection again and applies `selection.shownOf(listed)`, also when whole groups are checked and nothing is left.
  - Two caches (`commentatorGroupCache`, `groupCategoryCache`) and imports.
  - A fourth constructor parameter, `readerDefaults: CommentatorDefaults?` (none by default):
    `applyDefaultCommentatorsForBook` takes the reader's own default, also one that shows no commentator, before the
    library's; `setCommentatorsDefault()` keeps the book's selection as that default, and
    `clearCommentatorsDefault()` lets it go, leaving what the tab shows.
- **Why:** a commentator or group checked stays checked from line to line, and "כל המפרשים" checks every commentator of
  every line (`3c6bae9b`, `49b456d4`; `LINE_SELECTION_README.md` › Whole groups). The reader's default is the user's
  request of 2026-11-09: the library's default alone opened a book, and a choice lived only in its tab.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** medium: the edits replace the bodies of three of Zayit's functions; a Zayit change to the selection's
  rules there conflicts.
- **Smaller:** the rules already moved out to `CommentatorSelection.kt`; what is left are the calls.
- **Tests:** `CommentariesUseCaseAllCommentatorsTest`, `CommentatorSelectionTest`, `CommentatorsCheckStateTest`,
  `CommentariesUseCaseReaderDefaultTest`.
- **Notes:** the KDoc of `updateSelectedCommentators` is extended in French, as Zayit's own text there is, although
  `CONTRIBUTING.md` asks for English comments.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panes/ReaderPanes.kt`

- **Change:** modified, +166 −30 lines. Feature: Commentators in their own panes.
- **What:**
  - `ReaderPane` turns from an `enum class` into a `sealed class`: its eight entries become `data object`s, listed in
    `ReaderPane.standard`, and a `data class Commentator(name)` is a pane per split-off commentator, closed by
    `CloseCommentatorPane`.
  - `followEvent(opened)`: a commentator pane opened by the dock (torn off) sends `DetachCommentator`.
  - `commentatorPaneId(name)` / `commentatorNameOf(id)`: a stable id from the name's UTF-8 bytes in hex, and back.
  - `commentatorHome(index)`: where a pane opens when nothing placed it yet (left of the text, then right, then under
    it).
  - `WindowPanes` declares, besides the standard panes, a pane for each commentator the selected tab shows apart
    (`selectedTabDetachedCommentators`) or whose header is on screen to be torn off (`CommentatorTearOffs`, provided as
    `LocalCommentatorTearOffs`); the pane's title is the commentator's name.
  - `PaneSync` knows the commentator panes the dock has met (`knownPanes`), never sizes them by the tab's split
    percentages, and follows a pane opened or closed from the dock with `followEvent`.
  - `desiredPanes` adds each split-off commentator's pane; `BookPaneBody` draws it with `DetachedCommentatorPane`.
  - `ReaderPane.entries` becomes `ReaderPane.standard` in `innerWidthPx`; `writeBack` and `position` handle
    `Commentator`.
- **Why:** a commentator can be read in a pane of its own, beside the others, torn off by its header and placed
  anywhere in the dock (`d85afee7`, `d27e086b`, `831ad272`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** high: the enum's every entry was rewritten, so any Zayit change to a pane's declaration, or a new pane,
  conflicts; `PaneSync` and `WindowPanes` are edited in their core loops.
- **Smaller:** hard. The commentator panes could be a second list of satellites declared by a Pardes composable, leaving
  Zayit's enum as it is, but `PaneSync` needs to know them.
- **Tests:** `CommentatorPaneIdTest` (the ids), `DetachedCommentatorsTest`.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/PostSelectLineTest.kt`

- **Change:** modified, +7 −1 lines. Feature: Search in categories.
- **What:** the test's `SearchHomeViewModel` takes a fifth argument, a relaxed `mockk` for `LibraryCategories`; the call
  is broken over lines.
- **Why:** `SearchHomeViewModel`'s constructor gained `libraryCategories` (`37d4fe60`).
- **Kind:** Pardes feature hook (a test kept compiling).
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** it is one.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/BookContentPanel.kt`

- **Change:** modified, +189 −54 lines. Feature: Daf view, Commentators in their own panes, Commentator selection kept.
- **What:**
  - `detachedCommentatorIds`: the split-off commentators' book ids, from the names and the loaded line connections;
    their passages answer in the text as those of the commentaries pane's page do (`passageCommentatorIds`).
  - The hover prefetch of commentaries follows the book's `commentatorSelection`: with whole groups checked, each line
    prefetches its own commentators of those groups.
  - The call to `BookContentView` is wrapped in a `run { … }` that builds a `DafMode` for a Bavli tractate in daf view
    (its line clicks select the line, its passage clicks call `selectDafPassage`) and passes it as `daf`.
  - `onShowCommentaries` passed to `BookContentView`: opens the commentaries pane when it is closed and says whether
    it was, for a comment selected on the daf to be read there once the text is shown.
  - New private `selectDafPassage`: shows the passage's commentators in the commentaries pane's selection when they
    are missing, then selects the passage with anchors of its own kind (`"daf"`, `DAF_PASSAGE`), since the Bavli's text
    has none.
  - New `DetachedCommentatorPane(uiState, onEvent, name, lineConnections, diacritics)`, which `ReaderPanes` draws in a
    commentator's own pane.
- **Why:** the daf is drawn in the text view's frame and answers clicks as the text does, with the text's panes
  following (`4efa3e3c`, `b5ebdafd`, `e00754a4`, `8d3825e7`); the commentators' panes (`d85afee7`) and the selection kept
  (`3c6bae9b`, `49b456d4`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** high: the `run { … }` re-indents Zayit's whole `BookContentView(...)` call (50 lines), so a Zayit
  change to any of its arguments conflicts. Without whitespace the change is about 140 lines.
- **Smaller:** build `daf` before the call, without the `run` block, and pass it as one more argument: the call's 50
  lines would stay as Zayit wrote them.
- **Tests:** `DafViewStateTest`, `DafAnchorsTest`, `CommentatorSelectionTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/BookContentView.kt`

- **Change:** modified, +378 −219 lines. Feature: Daf view.
- **What:**
  - A `daf: DafMode?` parameter. With it, the view's frame (its zoom, keys, selection, context menu and find bar) shows
    `DafView` in place of the lines' `LazyColumn`.
  - The daf's own `LazyListState` and `DafControl`; its own zoom by the same gestures (`rememberTextZoom` over
    `dafZoomFlow`, in percent, `PERCENT = 100f`, given to `AppSettings.zoomDaf`, which zooms the text with it);
    `dafShown`/`dafHidden` while it is shown, so the zoom keys know.
  - The content is not hidden for initial positioning on the daf (its lines' list is not laid out there).
  - Find in page, Ctrl+Home/End and Page Up/Down act on the daf through `dafControl` (`scrollToLine`, `scrollToEdge`,
    `scrollPage`); the find bar's matches are drawn on the daf (`DafFind`).
  - The up and down arrows go through `stepLine`: on the daf they first ask `dafControl.stepComment`, which steps through
    Rashi's or Tosafot's comments when one is selected, and step through the lines as before when it says no.
  - A word clicked on the daf is selected in the book's text (`selectInLine`), and the right-clicked word's line is the
    context menu's (`onContextLine`).
  - On the daf the tab's place is the daf's: `maybeSave` saves nothing there, the daf's `onScroll` saves the Gemara's
    segment at its top as the tab's anchor (`dafAt`), and `DafView` opens at the tab's anchor (`openAt`).
  - Back from the daf (`wasDaf`, `backFromDaf`) the text opens in the daf's state (`dafControl.place()`): hidden until
    it is placed, the Gemara's segment at the daf's top scrolled to its own top, the selected line brought into view
    when it was in view on the daf, and, when a comment was selected on the daf by a click on itself, the commentaries
    pane opened (`onShowCommentaries`, a new parameter that says whether the pane was closed); the selected line is
    looked for again once the pane has taken its part of the view (`PANE_WAIT`, 3 seconds at most).
  - `BookSelectionContainer` gets `selection = null` and `ownsSelection = false` on the daf.
  - The lines' `LazyColumn` and the `ContentScrollbar` move into the `else` branch: re-indented, unchanged.
- **Why:** the daf view lives inside the text view's own frame, so that everything the reader does around the text
  works on the daf too (`e00754a4`, `8d3825e7`, `d334af46`, `efad35cc`); the user asked on 2026-11-09 that the arrows
  move within Rashi and Tosafot when the focus is on them, and that the text open from the daf in the same state (what
  is seen and what is focused).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** high: the re-indented block is the heart of Zayit's reader (some 220 lines), and every Zayit change
  there conflicts. Without whitespace the change is about 90 lines.
- **Smaller:** draw the daf and return before Zayit's `Box`, or put Zayit's block in a composable of its own called from
  an `if`, so that its lines keep their indentation.
- **Tests:** `DafViewStateTest`, `DafZoomTest`, `DafAnchorsTest`, `DafIndexTest` (the segment read at a place); the
  find marks have none, and the text's placing back from the daf is checked in the running app (it is scrolls and
  layout).
- **Notes:** a comment starts with "Pardes:", which Zayit's code does not do.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/CommentatorsGrid.kt`

- **Change:** modified, +216 −41 lines. Feature: Commentators in their own panes, Commentator kept.
- **What:**
  - The commentators' pages turn sideways: a `HorizontalPager` with its dots in a row above it
    (`HorizontalPagerIndicator`) replaces Zayit's `VerticalPager` with its dots in a column beside it. A dot's tooltip
    names its page's commentators, and the wheel over the dots turns the pages.
  - `Modifier.pageSwipe(pagerState)`: a touchpad's two-finger sideways swipe (a pan on Windows, a scroll elsewhere), a
    tilt wheel or Shift + wheel turns one page per gesture, in the reading direction; a page change cut short snaps to
    the nearest page. A mouse drag does not turn pages (it tears a commentator off).
  - `CommentatorHeader(tearOffs)`: while a header is on screen, the dock knows the commentator's pane
    (`tearOffs.show/hide`); the header is the pane's grip (`tearOffHandle`), and on hover a button opens the pane in a
    window of its own (the panes' own "open in a separate window", `AllIconsKeys.Actions.MoveToWindow`, `pane_float`).
  - `CommentatorCell` passes `tearOffs`; the constants of the swipe.
  - The reader's commentator is kept from line to line (`KeptCommentator.kt`): `CommentatorsGridScaffold` takes `kept`
    and `line`; the pager opens on his page when the line has him (`keptPage`); a page settled follows
    `kept.settledOn(...)` in place of the page's first commentator; a commentator who comes back with a line is no
    longer promoted as one just added (`kept.tickedOn(line)`), and one the reader ticks is his choice (`kept.chose`);
    `KeepCommentator(...)` follows him on each line; the dots, the wheel over them and `pageSwipe(pagerState, kept)`
    call `kept.turns()` before they turn a page.
- **Why:** commentators split off into panes, torn off by their header; with pages side by side, a column's vertical
  scroll never turns a page (`d85afee7`, `03c84c1f`, `5498e05f`, `831ad272`, `5c6c84f3`; a mouse-drag version was
  reverted in `ab469da8`). The commentator kept: the user's request of 2026-11-10 (stepping down the Gemara, the pages
  moved to another commentator on each line that lacked his, and did not come back to him).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** medium to high: Zayit's pager and indicator were replaced, so a Zayit change to the grid's paging
  conflicts, and the grid no longer looks like Zayit's (pages sideways, dots above).
- **Smaller:** the swipe and the tear-off could stay with Zayit's vertical pager, keeping its layout; turning the pages
  sideways is a design choice, not a need of the panes.
- **Tests:** `CommentatorsPageSwipeTest` (the swipe), `CommentatorPaneIdTest`, `KeptCommentatorTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/HomeView.kt`

- **Change:** modified, +292 −44 lines. Feature: Search in categories, Search in several books and categories, Book and
  author titles search.
- **What:**
  - `BookSuggestion`, `CatBookRow` and `catBookRows` become `internal`, for tests.
  - `CatBookRow.Category`: a category to search in. `catBookRows` orders references, books named by their title, a title
    being typed, the authors named exactly (with those of a book typed: `typedBooks`, `bookAuthors`), categories named
    exactly, books named only by an acronym, the text search (first when books or categories are picked already,
    `textSearchFirst`), the base books whose titles the text begins when they are five or fewer (`begunBooks`,
    `MAX_BEGUN_BOOKS`; `titleKey()` is the title as compared), then the other authors, categories and books. An author
    named as typed is so before any category or acronym (Zayit put the acronym's books first): Tab on the name typed
    picks the author and lists his books.
  - `tocAddRows`: in a book, the other books and categories to add to its search.
  - `HomeSearchCallbacks` gains `onPickCategory`, `onClearCategory`, `onAddScope`, `onRemoveScope` and `onNewBarOpened`
    (a new tab's bar opens empty, as Zayit's; null where it opens where its search looked).
  - `UnifiedSearchBar`: Tab on a book or category adds it as a chip (`ScopeItem`, `clearAfterAdding`); the chips show
    beside the text (`SCOPE_CHIP_MAX_WIDTH`), Backspace in the empty field drops the last; the text-search row names the
    scope (`search_text_row_in_category`, `…_in_scope`); a category in its base books alone is marked
    (`category_base_books_only`, `scope_base_books_label`); a category row's icon is a folder.
  - The focus returns to the bar after a book is picked only in the selected tab (`LocalTabSelected`): the window's
    tabs share their `SearchHomeViewModel`, and a hidden tab's bar took the letters typed next.
  - An author row says whose book it was found by (`author_of_book`) when it has no alias.
  - Ctrl+Enter and "search all books" are Enter when the scope's books are all searched (an author or a category).
- **Why:** a search in a whole category (`54180126`), in several books and categories together picked with Tab
  (`574f3d55`, `7eb98872`), in a category's base books alone (`37d4fe60`, `941bdfab`), books and authors found by name
  (`67fe471e`), and a new tab's bar empty as in Zayit (`03c84c1f`). The user asked on 2026-11-09 that an author no longer
  be pushed down when the book typed bears his name, and on 2026-11-10 that an author's name and Tab offer his books
  (`מהרשא` gave a book an acronym names so, `רשי` a category) and that `שמואל` not bring שמואל א after four authors and
  four categories.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** high: `HomeView.kt` is one of Zayit's largest files (some 1,900 lines), and the edits run through
  its search bar's key handling, its rows and its panels.
- **Smaller:** the category and scope rows could be drawn by a Pardes composable passed in; the key handling has to
  know them.
- **Tests:** `CatBookRowsTest`, `CategorySuggestionsE2ETest`, `ScopeItemsTest`, `CategorySuggestionsTest`,
  `BookSuggestionsE2ETest`.
- **Notes:** Zayit's search UI on purpose has no category scope (`SearchScope.Category` is "read back only" there,
  with category tabs over the results): this file brings back what Zayit chose to drop.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/LineCommentsView.kt`

- **Change:** modified, +362 −50 lines. Feature: Commentators in their own panes, Commentator selection kept, Commentator kept, Reader's default commentators.
- **What:**
  - `LineCommentsView` reads the book's `CommentatorSelection` (`bookCommentators`) instead of the line's
    `selectedCommentatorIds`, and the split-off commentators, and passes both to the single- and multi-line contents.
  - New `DetachedCommentatorView` (a split-off commentator's pane: a header with a "back to the commentaries" button,
    then its column on the selected line or lines) with `DetachedCommentatorColumn` and `ReattachCommentatorButton`.
  - Each line's checkboxes start from its own commentators that the book shows (`shownOf(listed)`) from the first
    frame, and report changes with `listed`.
  - `CommentatorsList`: a "כל המפרשים" row (`CommentatorsCheckAllRow`, a tri-state checkbox) and a checkbox per group
    header, replacing Zayit's plain `CommentatorGroupHeader`; a split-off commentator's row has a button to bring it
    back; `onSelectionChange` takes several names.
  - The commentaries pane leaves out the split-off commentators and says so when all the selected ones are elsewhere
    (`commentators_all_detached`).
  - `commentatorsCheckState(names, selected)`: on, off or indeterminate.
  - `CommentariesLayoutConfig.tearOffs`, from `LocalCommentatorTearOffs`, only while more commentators can be split off
    (`canDetach`).
  - The reader's commentator (`KeptCommentator`, remembered for the book in `LineCommentsView`) goes down in
    `CommentariesColumnScroll.kept` to the grid, with the line(s) shown (`line = selection`); the commentator of the
    passage selected in the text is the reader's when he comments on it, else its first as before.
  - The reader's default for the book (`rememberCommentatorsDefault`, in `LineCommentsView`) goes down to the list
    as `commentatorsDefault`, and "כל המפרשים", now shown for a single commentator too, ends with its pin
    (`CommentatorsDefaultPin`, clear of the scrollbar); `CommentatorsCheckAllRow` takes a `modifier` for it.
- **Why:** a commentator in a pane of its own (`d85afee7`, `831ad272`) and whole groups checked from line to line
  (`3c6bae9b`, `49b456d4`); the commentator the pages stay on from line to line, asked for on 2026-11-10; the pin of the reader's default,
  asked for on 2026-11-09 as "קבע כברירת מחדל".
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** high: the edits run through both content composables, the list and the layout config of one of
  Zayit's largest files (some 1,400 lines).
- **Smaller:** `DetachedCommentatorView` and its column could live in a Pardes file beside `CommentatorTearOffs.kt`;
  the rest is woven into Zayit's composables.
- **Tests:** `CommentatorsCheckStateTest`, `CommentatorSelectionTest`, `CommentariesUseCaseAllCommentatorsTest`,
  `KeptCommentatorTest`; the pin was tried in the app.

### Search and history

A note on the whole group: Zayit dropped the search in a category on purpose. Its `SearchScope.Category` is "read back
only", a saved category search runs everywhere, and the category tabs over the results narrow it. Pardes brings the
category scope back, adds several books and categories together (`SearchScope.Multi`) and a category's base books
alone. Every entry below that says "Search in categories" is part of that reversal.

#### `SeforimApp/addons/semantic/disabled/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/InstalledSemanticSearch.kt`

- **Change:** modified, +16 −2 lines. Feature: Semantic search.
- **What:** Zayit's community value `installedSemanticSearch = null` (lexical search only) becomes an object
  implementing `SemanticSearchFeature.hybrid(lexical, indexDir, resolveLine)` with Pardes's `HybridSearchEngine`, with a
  KDoc: Pardes's own semantic search, on Dicta's open model, depending on no proprietary package.
- **Why:** Zayit's open-core seam is where a build plugs its semantic search; Pardes's community build plugs its own
  there, so core code stays as Zayit wrote it (`20e2fa81`; `CLAUDE.md` › Open Core).
- **Kind:** Pardes feature hook.
- **Merge risk:** small: Zayit's file is two lines.
- **Smaller:** already minimal.
- **Tests:** `HybridSearchEngineDenseTest`, `HybridSearchEngineFallbackTest`, `HybridSearchEngineKeepLeadersTest`.

#### `SeforimApp/addons/quotelinks/disabled/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/quotelinks/InstalledQuoteLinks.kt`

- **Change:** modified, +5 −2 lines. Feature: Links in the texts.
- **What:** Zayit's community value `installedQuoteLinks = null` (the lines as they are in the DB) becomes
  `TextLinks`, with a KDoc: Pardes's own links in the texts, from the library's links, depending on no proprietary
  package.
- **Why:** Zayit's open-core seam is where a build plugs its quote links; Pardes's community build plugs its own there,
  so `ContentUseCase` and `LinesPagingSource` stay as Zayit wrote them (`CLAUDE.md` › Open Core). The user asked for
  verse links as Zayit's on 2026-11-09 (Zayit's are a private package Pardes may not use), and for the Chavruta's notes
  to be reached from their marks.
- **Kind:** Pardes feature hook.
- **Merge risk:** small: Zayit's file is two lines.
- **Smaller:** already minimal.
- **Tests:** `VerseQuotesTest`, `NoteLinksTest`, `LibraryLinksTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/SemanticSearchEngine.kt`

- **Change:** modified, +6 −4 lines. Feature: Long queries.
- **What:** `withSemanticSearch`'s parameter `lexical` is renamed `lucene`, and `val lexical = LongQuerySearchEngine(lucene)`
  wraps it, so a query too large for Lucene is searched as an exact quote, with or without the semantic search; the KDoc
  says so.
- **Why:** a long passage pasted in the bar made Lucene fail (`20e2fa81`; `LongQuerySearchEngine.kt`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small.
- **Smaller:** keep Zayit's parameter name and give the wrapper another (`val safe = LongQuerySearchEngine(lexical)`): the
  rename touches lines it did not need to.
- **Tests:** `LongQuerySearchEngineTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/LuceneLookupSearchService.kt`

- **Change:** modified, +40 −1 lines. Feature: Book and author titles search.
- **What:**
  - A constructor parameter `titleIndex: () -> BookTitleIndex?` (null without a catalog or before it is built).
  - `AuthorHit` gains `matchedByName` and `ofBook`; `ScoredBookHit` gains `namesBook`.
  - `suggestAuthors` returns nothing under the minimal length, then the title index's authors when there is one.
  - `suggestBooks` lists the title index's books first, then those only the lookup index finds (a partly typed acronym).
  - New `authorsOfBook(bookId)`.
- **Why:** books and authors are found by name as people type them, spellings, typos and word order included, after
  Klei Kodesh's Kitvei Hakodesh (`67fe471e`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** medium: the edits sit at the start of Zayit's two suggestion functions.
- **Smaller:** a Pardes wrapper around the service could do the merge of both lists, leaving Zayit's functions as they are.
- **Tests:** `BookTitleIndexTest`, `TitleSearchE2ETest`, `BookSuggestionsE2ETest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/SearchHomeViewModel.kt`

- **Change:** modified, +254 −15 lines. Feature: Search in categories, Book and author titles search, Desktop link.
- **What:**
  - `SearchHomeUiState` gains `categorySuggestions`, `selectedScopeCategory` and `scopeItems` (the books and categories
    picked, in order).
  - A constructor parameter `libraryCategories: LibraryCategories` (the app's categories with their paths and books).
  - The bar's suggestions add what the text names to search in (`categoriesNamed`, `LibraryCategories.named`: the
    commentaries on a book by their time, `ראשונים על בבא בתרא`, then the categories), and the authors of the books
    whose title is typed (`authorsOfBook`, `AuthorNames.withAuthorsOfBooks`); authors found by the title index skip the
    word-by-word check; no authors while books or categories are being added. An author found by name keeps the book
    typed that he wrote (`withTheirBooks`), for his row to come beside the book's.
  - In a book, the other books and categories the text names are offered to add (`booksAndCategoriesToAdd`).
  - `onPickCategory`, `onClearCategory`, `onAddScope`, `onRemoveScope` and the private `applyScopeItems`: one book
    alone puts the bar in it (its TOC), more books or categories search them together.
  - A restored results tab shows its category or several books and categories as chips (`showCategoryScope`,
    `showMultiScope`, searching at once before their names load).
  - `onNewTabBarOpened`: a new tab's bar opens empty, as Zayit's.
  - `barScope()` returns the picked items' scope (`ScopeItems.scopeOf`) or the one being restored.
  - Constants for the numbers of suggestions.
  - `SearchHomeNavigationEvent.OpenDesktop(name, tabs)`, and in `submitSearch`, before Zayit's own link: a desktop's
    link (`parseDesktopLink`) whose books the library has (`asWindow(libraryCategories.books())`) sends it instead
    of a search of the link's text.
- **Why:** search in a whole category (`54180126`), in several books and categories together (`574f3d55`), in a
  category's base books (`37d4fe60`), books and authors found by name (`67fe471e`), a new tab's bar empty as in Zayit
  (`03c84c1f`), the commentaries on a book by their time, which the user asked to type on 2026-11-09. A desktop's
  link is opened where Zayit opens a tab's, pasted into the bar, and by the bar's same navigation events.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** high: the edits run through Zayit's suggestion pipeline and its scope functions.
- **Smaller:** the category and scope state could be a Pardes helper class the ViewModel delegates to; the suggestion
  pipeline's edits stay.
- **Tests:** `CategorySuggestionsTest`, `ScopeItemsTest`, `CategoryScopeSearchTest`, `CategorySuggestionsE2ETest`,
  `AuthorBooksTest`, `CommentaryGroupsTest`, `DesktopLinkPasteTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/SearchResultViewModel.kt`

- **Change:** modified, +406 −52 lines. Feature: Search in categories, Feedback mode, Semantic search's category tabs, Result tabs, Results in the library's order, Search speed.
- **What:**
  - `SearchUiState` gains `scopeLabel` (the category, or the books and categories, searched) and `scopeBaseBooksOnly`.
  - New constructor parameters `feedbackStore: SearchFeedbackStore` and `libraryCategories: LibraryCategories`.
  - Events `SetScopeBaseBooksOnly` (the status line's choice in a category with base books and others) and `Grade`.
  - **Feedback mode:** `feedbackFlow` holds the grades of the search shown, read again when the search or the mode
    changes; `grade()` shows a grade at once and saves it in order (a `Mutex`) with the query, rank, scope and the
    semantic model in use (`InstalledPack`), or takes it back.
  - **Category scopes:** `scopeLabel()` names category and multi scopes (a group of commentaries searched as a
    category, which the repository doesn't have, by its label); `showScopeLabel`, `setScopeBaseBooksOnly`
    (searches again, and tells the bar through `scopeChanges`); `scopeBookIds()` replaces Zayit's `authorBookIds()` and
    adds a category's books (or its base books alone) and several books and categories; a search narrowed to a book or
    TOC entry keeps its wider scope to go back to (`wideScope`).
  - Zayit's start-up step that turned a saved category search into "everywhere" is deleted.
  - **Meaning search:** the category tabs count the lines found by meaning too (`withMeaningHits`, through
    `MeaningFacets` and `BuildSearchTreeUseCase.categoryIdsOf`), and the library's other top categories are tabs after
    the results' (`showOtherCategories`, `_otherTabCategories`).
  - `collectBookIdsUnderCategory` reads the app's catalog (`libraryCategories.bookIds`) instead of the database.
  - **Result tabs:** `showOnlyCategory` picks a tab with those already picked, or lets it go (Zayit's showed one at a
    time); `listed` keeps the search's session when its list can be shown in part (`SessionViews`), and
    `executeFilteredSearch` then shows the tabs' categories from that list with no new search (a category with no line
    in it is searched, as before); in a search of the whole library the tabs picked are the bar's categories
    (`showTabsInBar`, through `scopeChanges` and the file-level `scopeOfTabs(tabs, picked)`; `SearchScope.Global` when
    none is picked any longer), so the text searched again from the bar is searched in them, and `unpickCategoryTab`
    lets a tab go when its category is taken out of the bar.
  - **Results in the library's order:** the constructor's `lucene` is a plain parameter named `engine`, and the
    `lucene` the file searches with is `CatalogOrderEngine(engine, libraryCategories::bookOrder)`, which lists in
    the library's order while the tab's `catalogOrder` says so; `SearchUiState.catalogOrder` (restored from the
    tab's saved state), and the event `SetCatalogOrder`, which saves the choice and searches again.
  - **Search speed:** the results' tree takes its books from the catalog (`BuildSearchTreeUseCase(repository,
    libraryCategories::books)`); and the flow that builds a tree from the first results no longer sets it once the
    facets' tree is in (`if (!facetsComputed)` in its `collect`): one finished late replaced the full tree with
    the first page's.
- **Why:** the category scopes (`54180126`, `574f3d55`, `37d4fe60`), feedback mode (`90f8a24a`), and the meaning
  search's category tabs (`ac5447eb`, `d4711ac8`); the tabs as a filter of the list found: a search by meaning lists
  the first lines found, and a tab that searched its category again gave another list, slowly; the tabs picked as
  the bar's categories are the user's choice of 2026-11-10 over a link that searched in them. The results in the
  library's order are the user's request of 2026-11-09 (a sort by the catalog): wrapping the engine here leaves
  every search, filter and restore path of the file as it is. The tree's books from the catalog are the speed fix
  of `BuildSearchTreeUseCase.kt`; the guard is a fix of Zayit's code, seen once in the app when the first tree
  from the catalog came in after the facets'.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** high: Zayit's largest file (some 2,100 lines), edited through its search, filter and restore paths;
  the rename of `authorBookIds` to `scopeBookIds` touches every caller.
- **Smaller:** feedback mode could live in a Pardes class the ViewModel holds (its flow and `grade`), and the scope
  labels in `LibraryCategories`; keeping Zayit's name `authorBookIds` would shrink the diff.
- **Tests:** `CategoryScopeSearchTest`, `MeaningFacetsTest`, `SearchFeedbackStoreTest`, `AuthorScopeSearchTest`,
  `ResultTabsTest`, `HybridSearchEngineFallbackTest` (the views), `CatalogOrderSessionTest`,
  `ResultsTreeLibraryBooksTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/SearchResultScreen.kt`

- **Change:** modified, +89 −29 lines. Feature: Search in categories, Feedback mode, Preview highlights, Result tabs, Passage read as a book, Results in the library's order.
- **What:**
  - `SearchShellActions` gains `onScopeBaseBooksChange` and `onGrade`; `SearchResultInBookShellMvi` and
    `SearchResultContentMvi` take `feedback` (the grades shown).
  - With feedback mode on (`searchFeedbackFlow`), each result row ends with `SearchFeedbackButtons`.
  - The status line names the category or books searched (`state.scopeLabel`), and in a category with base books and
    others offers the other choice (`search_switch_scope_all` / `…_base`).
  - The preview's highlights come from `rememberPreviewHighlights(...)` instead of an inline `produceState`.
  - The category tabs are picked several at once: `FilterTabs` has a form taking the set picked (`selected: Set<Long>`,
    "הכל" the one picked when none is), Zayit's one-at-a-time form calling it; `CategoryTabs` marks every top
    category picked.
  - `PassagePreview` is read as a book is (`PassageReading.kt`): its lines are `rememberPassageReading(preview).lines`
    (more of the book past the passage's ends, the scroll container taking `readPastEnds`); Zayit's `Box` around the
    lines is `PassageText` (the text selected, the menu of a right click); a line's `clickable` and its hand pointer are
    `passageLine`, which still opens the book on a click of its own; and the view goes to the found line only until
    lines are added before the passage (`!passage.grownBefore`).
  - `SearchShellActions` gains `onCatalogOrderChange`; `SearchStatusLine` takes `catalogOrder` and ends with a link
    to the other order (`search_sort_catalog` / `search_sort_relevance`); and in the library's order every passage
    is listed (`flattenResults`' `oneBook` is `everyPassage`: no "more in this book" link, a book's lines stand
    together).
- **Why:** feedback mode (`90f8a24a`), the category scopes (`37d4fe60`), and the preview's highlights shared with
  Pardes's semantic search (`20e2fa81`); the tabs as the user asked on 2026-11-09: a filter of the results found,
  several at once; the categories picked go into the search bar from `SearchResultViewModel`, with nothing of this
  screen's. The passage read as a book is the user's request of 2026-11-10: its text selected and copied, the book's
  menu on a right click, and scrolling up and down past it. The link to the library's order is the user's request
  of 2026-11-09, beside the status line's other choice.
- **Kind:** Pardes feature hook.
- **Merge risk:** medium: Zayit edits this screen often (0.6.12 rebuilt its layout); the hooks are spread over some fifteen
  places, and `FilterTabs`' body is now the set's form.
- **Smaller:** already near minimal.
- **Tests:** `SearchFeedbackButtonsUiTest`, `PreviewHighlightsUiTest`, `ResultTabsTest`; the tabs and the link to the
  library's order were tried in the app.
- **Notes:** comments here are marked "Pardes:", which Zayit's code does not do.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/AuthorNames.kt`

- **Change:** modified, +19 −0 lines. Feature: Book and author titles search.
- **What:** `withAuthorsOfBooks(byName, ofBooks, max, id)`: the authors found by name, then the authors of the books the
  query names, each once, at most `max`, with room kept for two of the books' authors (`BOOK_AUTHORS_KEPT`).
- **Why:** typing a book's title offers its author too (`משנה ברורה` offers הרב ישראל מאיר הכהן) (`67fe471e`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** small; an added function.
- **Smaller:** could live in a Pardes file; it sits beside Zayit's name helpers it complements.
- **Tests:** `TitleSearchE2ETest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/BuildSearchTreeUseCase.kt`

- **Change:** modified, +26 −1 lines. Feature: Semantic search's category tabs, Search speed.
- **What:**
  - `categoryIdsOf(bookId)`: the ids of a book's category and every category above it, the root first, through
    the use case's caches.
  - A second constructor parameter, `libraryBooks` (the library's books by id, none by default), asked for once
    (`libraryBook(bookId)`), and looked in before the cache and the database in the three places a book is
    resolved (`resolveBook`, the facets' loop, `categoryIdsOf`).
- **Why:** the lines found by meaning alone are counted in their categories' tabs (`d4711ac8`). The tree of a search
  in all the books read each book from the database, one query a book, the first time in every tab: 0.2 to 0.8
  seconds for 1,400 to 7,400 books on the whole library, 5 to 58 ms with the catalog's books (the user's request
  of 2026-11-09 for a faster search).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** small; an added function, a parameter, and one line or operand at each of Zayit's two lookups.
- **Smaller:** already minimal.
- **Tests:** `MeaningFacetsTest`, `ResultsTreeLibraryBooksTest`; Zayit's `BuildSearchTreeUseCaseTest` unchanged.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/history/HistoryStore.kt`

- **Change:** modified, +39 −6 lines. Feature: Search in categories.
- **What:**
  - A category search is saved with its `searchCategoryId` and reopened as `SearchScope.Category`, base books or not.
  - `searchKey` becomes `internal`, and keys category (`c12`, `c12y` for base books alone) and multi scopes
    (`m12.34_56.78_78`); `baseBooksInKey` and `multiScopeInKey` read them back; `MULTI_KEY`, `BASE_BOOKS_KEY`.
- **Why:** a search in a category or in several books and categories reopens there from the history (`54180126`,
  `574f3d55`, `37d4fe60`); the feedback keys its grades the same way.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** medium: the edits change how Zayit's history reads its keys.
- **Smaller:** already minimal.
- **Tests:** `HistoryStoreTest` (two tests added).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/history/HistoryScreen.kt`

- **Change:** modified, +7 −1 lines. Feature: Reading time.
- **What:** "clear all" clears the reading time too (`readingTimeStore.clearAll()`), as on Android.
- **Why:** the learning split widget's time read is part of the history (`0083381e`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** `ReadingTimeStoreTest`.

#### `navigation/src/commonMain/kotlin/io/github/kdroidfilter/seforim/tabs/SearchScope.kt`

- **Change:** modified, +17 −3 lines. Feature: Search in categories.
- **What:**
  - `SearchScope.Category` is a real scope again: its KDoc no longer says "read back only", and it gains
    `baseBooksOnly` (appended last for ProtoBuf).
  - New `SearchScope.Multi(bookIds, categoryIds, baseOnlyCategoryIds)`.
  - The interface's KDoc names the new scopes.
- **Why:** the category scopes (`54180126`, `574f3d55`, `37d4fe60`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** medium: a shared module's sealed interface; a Zayit scope added there makes every `when` over it
  conflict or fail to compile.
- **Smaller:** already minimal for the feature; the feature itself reverses Zayit's choice.
- **Tests:** `SessionModelsTest`, `CategoryScopeSearchTest`, `HistoryStoreTest`.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/core/history/HistoryStoreTest.kt`

- **Change:** modified, +33 −0 lines. Feature: Search in categories.
- **What:** two tests: `a search in a category, or in several books and categories, reopens there`, and
  `a category searched in its base books alone reopens so, apart from the same category whole`.
- **Why:** the history keys of the new scopes (`574f3d55`, `37d4fe60`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** small; added tests.
- **Smaller:** could be a Pardes test file.
- **Tests:** it is one.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/AuthorScopeSearchTest.kt`

- **Change:** modified, +7 −1 lines. Feature: Feedback mode, Search in categories, Results in the library's order.
- **What:** the test's `SearchResultViewModel` gets a `feedbackStore` (an in-memory `SearchFeedbackStore`) and a
  `libraryCategories` (a `LibraryCategories` without a catalog), and its engine by the parameter's new name, `engine`.
- **Why:** the ViewModel's constructor gained both (`90f8a24a`, `37d4fe60`), and wraps the engine it is given
  (`CatalogOrderEngine`), which keeps the name `lucene` for the wrapped one.
- **Kind:** Pardes feature hook (a test kept compiling).
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** it is one.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/search/BookSuggestionsE2ETest.kt`

- **Change:** modified, +3 −9 lines. Feature: Book and author titles search.
- **What:** the test's lookup service is the app's, with its title index first (`titles.appLookup(dbPath)`), in place of
  Zayit's bare `LuceneLookupSearchService`; three imports go.
- **Why:** the suggestions the test checks are now the title index's (`67fe471e`).
- **Kind:** Pardes feature hook (a test kept true).
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** it is one (end to end on a real library).

### Resources: strings, fonts and pictures

#### `SeforimApp/src/commonMain/composeResources/values/strings.xml`

- **Change:** modified, +290 −1 lines. Feature: every Pardes feature with interface text.
- **What:** one string removed, `font_rashi_amiti` (the font is gone), and 272 Hebrew strings added in eighteen blocks,
  several under a comment naming their feature:
  - library location and move: `library_location_*` (18), `library_move_*` (14), `library_problem_*` (10),
    `library_install_*` (3, the last the library found in the folder chosen for the install);
  - semantic packs: `semantic_pack_*` (31), `semantic_problem_*` (6);
  - feedback: `feedback_send_*` (37), `feedback_consent_*` (20), `search_feedback_*` (6): among them the other apps'
    rows and the publication's, with what each means; and under a comment, after `onboarding_library_folder_*`, the
    onboarding's step: `onboarding_feedback_*` (7);
  - the daf view: `daf_layouts_*` (19), `daf_update_*` (2), `daf_review_*` (4), `daf_download_*` (6), `daf_import_*` (4), `daf_view` and
    `daf_view_tooltip`, `daf_missing`, `daf_missing_hint`, `daf_amud_missing`, `settings_daf_*` (4) and
    `settings_font_daf_gemara_label` / `…_commentary_label`;
  - the existing Zayit library: `onboarding_existing_library_*` (5); and under a comment, after Zayit's
    `onboarding_choose_files`, the offline install's folder that holds a library: `onboarding_library_folder_*` (8:
    the question, its link, the note, and why a folder is not used);
  - search in categories and by name: `search_text_row_in_category`, `search_text_row_in_scope`,
    `search_switch_scope_all`, `search_switch_scope_base`, `row_action_add_to_search`, `row_action_search_category`,
    `scope_base_books_label`, `category_base_books_only`, `author_of_book`;
  - the results' order: `search_sort_catalog` ("מיון לפי סדר הספרייה") and `search_sort_relevance`, after
    `search_switch_scope_base`;
  - the commentators' panes and selection: `commentator_pane`, `commentator_reattach`, `commentator_detached_hint`,
    `commentator_not_on_line`, `commentators_all_detached`, `commentators_select_all`, and under a comment the pin of
    the reader's default, `commentators_default_set`, `…_set_hint`, `…_replace`, `…_on` and `…_on_hint`;
  - a desktop's link: `desktop_copy_link` ("העתק קישור למרחב"), after Zayit's `desktop_open_in_new_window`;
  - the Home: `home_dictionary_synonyms`, `home_dictionary_spellings` and `home_widget_name_learning_split`;
  - the reader's menu: `context_menu_copy_letters_only`, `context_menu_open_in_dictionary`;
  - the references pane: `references_more` ("קישורים נוספים"), after Zayit's `references_mesorat_hashas`;
  - the open smart siddur, under a comment after Zayit's last siddur string (`siddur_open_siddur`): the 26 strings
    the open engine's screen adds to Zayit's (`siddur_reading_*` (6), `siddur_lulav_*` (6), `siddur_israeli_*` (7),
    `siddur_part_selichot`, `siddur_mincha_erev_*` (2), `siddur_hl_viduy_erev_yom_kippur`,
    `siddur_molad_announcement`, `siddur_choose_meal_food`, `siddur_meein_land_of_israel_note`), word for word from
    the engine's author's fork; `siddur_israeli_ordinary` ("כביום חול רגיל"), the custom Pardes adds, first among
    them; the three days of Iyar the engine names (`siddur_yom_hazikaron`, `…_haatzmaut`,
    `…_yerushalayim`); and the switch, `settings_open_siddur` ("סידור חכם (ניסיוני)") with its description, which
    says it is experimental and credits SeforimOpenSiddur, Amud and Hebcal.
- **Why:** Zayit keeps every interface text here, in Hebrew (`AGENTS.md`), so Pardes's features do too. Pardes's own
  texts that replace Zayit's (the app's name, the about texts) are not here but in the brand overlay
  (`branding/pardes/composeResources`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** medium: Zayit adds strings in most releases (0.6.13 does); eighteen insertion points (the onboarding's two blocks stand together), each a possible
  conflict, though a mechanical one.
- **Smaller:** the blocks could all sit at the end of the file, one comment each, so that a merge meets one place.
- **Tests:** the build (a missing key fails `generateComposeResClass`); the UI tests of each feature.

#### `SeforimApp/src/commonMain/composeResources/files/temple_jerusalem_in_fire.webp`

- **Change:** modified (binary). Feature: Licences.
- **What:** Zayit's animated picture behind the Temple countdown card, a Shutterstock photograph, is replaced by a crop of
  David Roberts's painting "The Siege and Destruction of Jerusalem" (1850, public domain), with flickering fires, made by
  `.claude/skills/zayit-android/scripts/temple-picture.py`.
- **Why:** the photograph's licence ("No use without permission") covers Zayit only; Pardes may not ship it. Zayit added
  the file in `0c8e6c3c` (0.6.7), and the merge of 0.6.7 replaced it (`ef658a50`, `dbaff6a7`; `branding/README.md` ›
  licence-restricted files).
- **Kind:** licence removal.
- **Merge risk:** a binary conflict whenever Zayit changes its picture: keep Pardes's.
- **Smaller:** already minimal (a replacement in place, since the file must not be in the repository at all).
- **Tests:** none.

#### `SeforimApp/src/commonMain/composeResources/font/RashiAmiti.ttf`

- **Change:** deleted. Feature: Licences.
- **What:** Zayit's Rashi Amiti font (a Rashi-script typeface offered in the font settings).
- **Why:** its licence covers the Zayit app only, so Pardes neither bundles nor downloads it (`5cfc56d6`; the
  `zayit-android` skill › Authorship and name).
- **Kind:** licence removal.
- **Merge risk:** a modify/delete conflict if Zayit changes the font; keep it deleted.
- **Smaller:** already minimal.
- **Tests:** `FontCatalogTest` counts 27 fonts.

### Settings and fonts

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/typography/FontCatalog.kt`

- **Change:** modified, +0 −7 lines. Feature: Licences.
- **What:** the Rashi Amiti option (`rashiamiti`), its family and its imports are removed.
- **Why:** the font is not in Pardes (see `RashiAmiti.ttf`).
- **Kind:** licence removal.
- **Merge risk:** small; a conflict if Zayit edits the lines around the option.
- **Smaller:** already minimal.
- **Tests:** `FontCatalogTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/database/update/DatabaseCleanupUseCase.kt`

- **Change:** modified, +7 −0 lines. Feature: Library location, Existing Zayit library.
- **What:** the cleanup also looks in the library's install folder (`LibraryLocation.installDir`), and skips a folder
  that is another app's library used in place (`ForeignLibraries.isForeign`) or a copy kept by a move
  (`LibraryLocation.isKeptCopy`).
- **Why:** a library update must never delete an installed Zayit's library or a copy the user chose to keep
  (`58586e1a`, `07e14dac`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small; three lines in Zayit's loop.
- **Smaller:** already minimal.
- **Tests:** `ForeignLibrariesTest`, `LibraryLocationTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/data/DataSettingsViewModel.kt`

- **Change:** modified, +10 −2 lines. Feature: Branding, Library location, Existing Zayit library, No error reports.
- **What:**
  - `isErrorReportingAvailable = crashReporting.isAvailable`.
  - The export's file name starts with `BrandInfo.BACKUP_FILE_PREFIX` instead of `zayit_backup_`.
  - The reset deletes the copies kept by library moves (`LibraryLocation.copies` / `deleteCopy`), and does not delete a
    custom library folder that belongs to another app (`ForeignLibraries.isForeign`).
- **Why:** a Pardes backup is named Pardes's (`07e14dac`); a reset forgets kept copies and never deletes Zayit's
  library (`58586e1a`); no error-report switch without a DSN (`fabcf4e8`).
- **Kind:** branding hook and Pardes feature hooks.
- **Merge risk:** small; lines inside `export` and `resetApp`.
- **Smaller:** already minimal.
- **Tests:** `ForeignLibrariesTest`, `LibraryLocationTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/ui/DataSettingsScreen.kt`

- **Change:** modified, +19 −7 lines. Feature: Library location, Semantic search packs, Feedback sending,
  No error reports.
- **What:**
  - Three Pardes cards before the reset: `LibraryLocationCard()`, `SemanticPackCard()` and `FeedbackSendCard()` (the
    daf's layouts card stood among them until 2026-11-10; it is in the daf's own settings page).
  - The error-reports card only when `isErrorReportingAvailable`, with a comment.
- **Why:** each card is the settings page of a Pardes feature (`58586e1a`, `77f5349b`, `3e365d16`, `e14ce828`); a build
  without a DSN sends no reports (`fabcf4e8`).
- **Kind:** Pardes feature hooks.
- **Merge risk:** small: one call per card in Zayit's column, and one `if` around its last card.
- **Smaller:** the three cards could be one `PardesDataCards()` call.
- **Tests:** `SemanticPackCardUiTest`, `FeedbackSendCardUiTest`; the other cards have none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/navigation/SettingsDestination.kt`

- **Change:** modified, +4 −0 lines. Feature: Daf view.
- **What:** `SettingsDestination.Daf`, the daf view's settings page, after `Data`, with a comment.
- **Why:** the daf view's settings stood in two pages (its marks and review among the fonts, its layouts among the
  data); the user asked on 2026-11-10 for an ordered category of their own. Its two fonts stay among the fonts.
- **Kind:** Pardes feature hook.
- **Merge risk:** small; one object at the end of Zayit's sealed interface.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/navigation/SettingsNavHost.kt`

- **Change:** modified, +4 −0 lines. Feature: Daf view.
- **What:** the `SettingsDestination.Daf` route draws `DafSettingsScreen()`; its import.
- **Why:** as for `SettingsDestination.kt`.
- **Kind:** Pardes feature hook.
- **Merge risk:** small; one route after Zayit's last.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/ui/SettingsSidebar.kt`

- **Change:** modified, +8 −0 lines. Feature: Daf view.
- **What:** a sidebar item "צורת הדף" (`daf_view`, the reader's own label, with its icon `GemaraPage`) between the fonts
  and the data, and its branch in the `when` that marks the page shown; two imports.
- **Why:** as for `SettingsDestination.kt`.
- **Kind:** Pardes feature hook.
- **Merge risk:** small; an item in Zayit's list and a branch in its `when`, which must stay exhaustive.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/ui/GeneralSettingsScreen.kt`

- **Change:** modified, +7 −2 lines. Feature: Branding, the open smart siddur.
- **What:** the header's title is `stringResource(Res.string.app_name)` instead of the literal "זית", and its GitHub
  button opens `BrandInfo.SOURCE_URL` instead of Zayit's repository; two imports. After the last of Zayit's three
  cards, `OpenSiddurCard()`, with its import: the experimental siddur's switch.
- **Why:** the settings name the app and link its AGPL source, which are Pardes's (`07e14dac`; `branding/README.md`).
  The siddur's switch is a general setting of the app, as the three cards above it are, and belongs to no other page.
- **Kind:** branding hook and Pardes feature hook.
- **Merge risk:** small.
- **Smaller:** already minimal; `app_name` itself is replaced by the brand overlay.
- **Tests:** none of its own; `OpenSiddurSwitchTest` (what the card switches).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/fonts/FontsSettingsEvents.kt`

- **Change:** modified, +16 −0 lines. Feature: Daf view.
- **What:** events `SetDafGemaraFont(code)`, `SetDafCommentaryFont(code)`, `SetDafMarkUnsure(enabled)` and
  `SetDafReview(enabled)`.
- **Why:** the daf view's fonts and its two switches are set on the fonts page (`e00754a4`, `1a350161`, `3c9ec937`).
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** `FontsSettingsEventsTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/fonts/FontsSettingsState.kt`

- **Change:** modified, +4 −0 lines. Feature: Daf view.
- **What:** `dafGemaraFontCode`, `dafCommentaryFontCode`, `dafMarkUnsure` and `dafReview`, with their defaults.
- **Why:** as for `FontsSettingsEvents.kt`.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** `FontsSettingsEventsTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/fonts/FontsSettingsViewModel.kt`

- **Change:** modified, +44 −18 lines. Feature: Daf view.
- **What:**
  - Four more flows (`dafGemaraFont`, `dafCommentaryFont`, `dafMarkUnsure`, `dafReview`) and a private `current()`.
  - `state` combines eight flows as `combine<Any, FontsSettingsState>(…) { current() }` instead of Zayit's typed
    four-flow lambda, and starts from `current()`.
  - `onEvent` handles the four new events; `ResetToDefaults` resets the daf's two fonts too (not its switches, which
    are in the daf's own settings page since 2026-11-10).
- **Why:** as for `FontsSettingsEvents.kt`.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** medium: Zayit's `state` builder was rewritten.
- **Smaller:** the daf's settings could be a second state of their own, leaving Zayit's typed `combine` as it was.
- **Tests:** `FontsSettingsEventsTest`.
- **Notes:** the `combine<Any, …>` over flows of different types reads their values by hand: it works, but it is not
  how Zayit's code builds a state.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/ui/FontsSettingsScreen.kt`

- **Change:** modified, +18 −0 lines. Feature: Daf view.
- **What:** two `FontSettingCard`s after Zayit's last, the daf's Gemara and its Rashi and Tosafot; their two imports.
  The daf's checkboxes that stood here too (the unsure lines, the review) are in `DafSettingsScreen.kt` since
  2026-11-10; the fonts stay here, with the other fonts, as the user asked on 2026-11-11.
- **Why:** as for `FontsSettingsEvents.kt`.
- **Kind:** Pardes feature hook.
- **Merge risk:** small; a block after Zayit's last card.
- **Smaller:** already near minimal.
- **Tests:** none of the screen.

### Home widgets

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/WidgetCard.kt`

- **Change:** modified, +5 −1 lines. Feature: Tests on a CI runner.
- **What:** `WIDGET_LOADING_TAG` (`"widget-loading"`), a test tag on `WidgetCardLoading`'s card.
- **Why:** the widget-size test waits for a card's content instead of a fixed pause (`022af5bb`).
- **Kind:** fix of Zayit's code (its tests).
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** `WidgetSizesScreenshotTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/LibraryWidgets.kt`

- **Change:** modified, +13 −4 lines. Feature: Home widgets loading.
- **What:** the history, favorites and notes widgets read their store into a nullable value and show
  `WidgetCardLoading` until it is read, instead of an empty list.
- **Why:** a list still loading showed "empty" for a moment, and the widget-size test measured that (`022af5bb`).
- **Kind:** fix of Zayit's code (it holds for Zayit as well).
- **Merge risk:** small; three widgets' first lines.
- **Smaller:** already minimal.
- **Tests:** `WidgetSizesScreenshotTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/DictionaryWidget.kt`

- **Change:** modified, +170 −22 lines. Feature: Klei Kodesh dictionary, Dictionary window.
- **What:**
  - `Dictionary` gains a `source` (`BOOK`, `INDEX`, `TEXT`) and an `attribution`, and six entries: four books of the
    library read through Klei Kodesh's index of headwords (מצודת ציון, the Malbim's ביאור המילות, מיקרופדיה תלמודית,
    מחברת מנחם) and two dictionaries of text (Torat Emet's Aramaic-Hebrew dictionary, from Klei Kodesh's data with
    their permission; Hebrew Wikipedia's acronyms, CC BY-SA), with `Dictionary.of(name, source)`.
  - `DictionaryEntry.text` for a word of no book.
  - Reading by source: `readBook` (Zayit's code, moved out of `readDictionaries`), `readIndexed` (each index entry
    checked against its line; the counts logged), `titleVariants` (באור/ביאור, " and ״), and the text dictionaries from
    `KleiKodeshPack.shipped`.
  - Each word's synonyms and spellings from Klei Kodesh (`RelatedWords`), under the field and on the entry's page; one
    clicked is looked up.
  - `ToolWindow` is overridden by `DictionaryToolWindow`, and the field's modifier starts from
    `Modifier.fillMaxWidth().focusedInItsWindow()` (a local `field`): in the dictionary's own window the field takes the
    focus. `LookupAskedFor(pickWord)` puts the word the window was opened on in the field, and the field's results come
    from `lookupAsRead` in place of `lookup`: a word no entry begins with is found without its prefix letters.
  - The entry page shows a text entry's text and attribution, and no "open in the book" button for it; the options page
    marks Klei Kodesh's dictionaries ("· כלי קודש").
- **Why:** more dictionaries and related words in the Home's dictionary card, from Klei Kodesh's Kitvei Hakodesh data
  (`66ae16e8`, `4f86ba1e`, `389f2438`); HaMichlol's definitions were taken out again before release (`f405bb7f`). The
  dictionary opened from the Tools menu is ready to type in, and the reader's menu opens it on a word, as the user asked
  on 2026-11-09.
- **Kind:** Pardes feature in Zayit's file.
- **Merge risk:** medium: Zayit's `readDictionaries` body was moved into `readBook`, so a Zayit change there conflicts.
- **Smaller:** `readIndexed`, `titleVariants` and `RelatedWords` could move to `KleiKodeshDictionary.kt`; keeping Zayit's
  reading code where it was would avoid the move.
- **Tests:** `KleiKodeshDictionaryTest`, `KleiKodeshDictionaryE2ETest`, `DictionaryWindowUiTest`, `DictionaryLookupTest`.
- **Notes:** the attributions and the "· כלי קודש" mark are Hebrew literals in Kotlin, as the file's dictionary labels
  already were in Zayit; ktlint left one `if (kind ==` broken over three lines.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/luach/LuachData.kt`

- **Change:** modified, +3 −2 lines. Feature: Zmanim card.
- **What:** `nextZmanim` takes the zmanim of the day after tomorrow too (`-1L..2L`), with the comment's reason.
- **Why:** from the evening, tonight's and tomorrow's zmanim are some fifteen, fewer than the next zman's card lists,
  so it showed half empty (`8f85d914`; the Android port the same).
- **Kind:** fix of Zayit's code.
- **Merge risk:** one line.
- **Smaller:** already minimal.
- **Tests:** none of its own.

### Onboarding

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/diskspace/AvailableDiskSpaceScreen.kt`

- **Change:** modified, +9 −0 lines. Feature: Library location, Existing Zayit library.
- **What:** `InstallLocationRow(onChange = { onEvent(AvailableDiskSpaceEvents.Refresh) }, onUseLibrary = onUseLibrary)`
  above the buttons; `AvailableDiskSpaceView` takes `onUseLibrary` (empty by default, as `onNext`), and the screen
  gives it the way to the user's details, this screen popped.
- **Why:** the onboarding lets the user install the library on another folder or drive, and measures that drive
  (`58586e1a`). A folder chosen there that already holds a library is used where it is: this screen stands before the
  offline install's, and lets no one past without room for an install, which a library used in place does not need.
- **Kind:** Pardes feature hook.
- **Merge risk:** small: the row's line, a parameter of the view and an argument of its call.
- **Smaller:** the row could find the navigation itself (a composition local of the onboarding's `navController`),
  which would leave this file its one line.
- **Tests:** `LibraryLocationTest`, `ForeignLibrariesTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/diskspace/AvailableDiskSpaceUseCase.kt`

- **Change:** modified, +12 −1 lines. Feature: Library location.
- **What:** a constructor parameter `targetDir: (() -> File)?`; when given, the free and total space are the chosen
  folder's drive's (`LibraryLocation.freeBytes/totalBytes`); imports.
- **Why:** as for `AvailableDiskSpaceScreen.kt`.
- **Kind:** Pardes feature hook.
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** `LibraryLocationTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/download/DownloadUseCase.kt`

- **Change:** modified, +3 −1 lines. Feature: Library location.
- **What:** a constructor parameter `targetDir: () -> File` (Zayit's databases folder by default), where the parts go.
- **Why:** the download goes to the folder the library is installed to, on the same drive (`58586e1a`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** none of its own.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/download/HttpFileDownloader.kt`

- **Change:** modified, +2 −1 lines. Feature: Branding.
- **What:** the download's `User-Agent` is `BrandInfo.USER_AGENT` instead of Zayit's `SeforimApp/1.0 (+…SeforimApp)`.
- **Why:** Pardes's requests say they are Pardes's (`07e14dac`).
- **Kind:** branding hook.
- **Merge risk:** one line.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/extract/ExtractUseCase.kt`

- **Change:** modified, +3 −5 lines. Feature: Library location.
- **What:** the library is extracted to `LibraryLocation.installDir(appSettings)` instead of FileKit's databases
  folder; two imports go.
- **Why:** as for `DownloadUseCase.kt` (`58586e1a`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** `LibraryLocationTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/licence/LicenceScreen.kt`

- **Change:** modified, +4 −0 lines. Feature: Existing Zayit library.
- **What:** after the licence, with no usable library of Pardes's own, `ForeignLibraries.find()` finding one leads to
  `ExistingLibraryScreen` instead of the install.
- **Why:** an installed Zayit's library is offered for use in place, without a second download of 8 GB (`07e14dac`).
- **Kind:** Pardes feature hook.
- **Merge risk:** small; one branch in the accept handler.
- **Smaller:** already minimal.
- **Tests:** `ForeignLibrariesTest` (the finder); the screen has none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/navigation/OnBoardingDestination.kt`

- **Change:** modified, +6 −0 lines. Feature: Existing Zayit library, Feedback from other apps.
- **What:** `@Serializable data object ExistingLibraryScreen`, and `FeedbackScreen` before `FinishScreen`.
- **Why:** the new onboarding steps' destinations (`07e14dac`; the feedback's question, 2026-11-11).
- **Kind:** Pardes feature hook.
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/navigation/OnBoardingNavHost.kt`

- **Change:** modified, +8 −0 lines. Feature: Existing Zayit library, Feedback from other apps.
- **What:** `noAnimatedComposable<OnBoardingDestination.ExistingLibraryScreen> { ExistingLibraryScreen(navController) }`
  and its import; the same for `FeedbackScreen` and `FeedbackStepScreen`.
- **Why:** as for `OnBoardingDestination.kt`.
- **Kind:** Pardes feature hook.
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/offline/OfflineFileSelectionScreen.kt`

- **Change:** modified, +3 −0 lines. Feature: Existing Zayit library.
- **What:** `LibraryFolderChoice(navController)` under the files' button, with its comment.
- **Why:** the offline install also takes a folder that already holds a library, and uses it where it is, with nothing
  extracted or copied: the user's request of 2026-11-09.
- **Kind:** Pardes feature hook.
- **Merge risk:** small; one call at the end of the screen's column.
- **Smaller:** already minimal.
- **Tests:** `ForeignLibrariesTest` (what a chosen folder holds); the screen has none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/region/RegionConfigScreen.kt`

- **Change:** modified, +3 −1 lines. Feature: Feedback from other apps.
- **What:** the step's "next" goes to `OnBoardingDestination.FeedbackScreen` while the feedback's question is open
  (`feedbackAutoSender.isQuestionOpen()`, read once above), and to `FinishScreen` as before otherwise.
- **Why:** the user asked on 2026-11-11 that the install itself ask whether to send the search data, to publish it,
  and from which apps; the region is the onboarding's last step before its end.
- **Kind:** Pardes feature hook.
- **Merge risk:** small: one value and one line of the "next" handler.
- **Smaller:** already minimal.
- **Tests:** `FeedbackStepTest`, `FeedbackAutoSenderTest` (the question open); the navigation has none.

### Zayit's tests that Pardes adjusts

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/typography/FontCatalogTest.kt`

- **Change:** modified, +2 −1 lines. Feature: Licences.
- **What:** the expected number of fonts is 27, not 28, with the reason in a comment (no Rashi Amiti).
- **Why:** the font was removed (see `RashiAmiti.ttf`); the line changed in the merge of 0.6.7 (`ef658a50`).
- **Kind:** licence removal.
- **Merge risk:** a conflict whenever Zayit adds a font and changes the count: then 1 less than Zayit's.
- **Smaller:** already minimal.
- **Tests:** it is one.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/HomeWidgetsLayoutTest.kt`

- **Change:** modified, +4 −1 lines. Feature: Tests on a CI runner.
- **What:** the default layout is checked for both kinds of machine (`defaultLayout(lowEnd = false/true)`), not as the
  test machine's.
- **Why:** on a CI runner, whose GPU is a software one, the default layout leaves out the 3D widgets, and the test
  failed (`8f85d914`).
- **Kind:** fix of Zayit's code (its tests).
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** it is one.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/WidgetDragUiTest.kt`

- **Change:** modified, +15 −0 lines. Feature: Tests on a CI runner.
- **What:** `@BeforeTest` sets `PlatformInfo.lowEndForTests = false` (the Earth, which the tests drag, is shown) and
  `@AfterTest` clears it; imports.
- **Why:** as for `HomeWidgetsLayoutTest.kt` (`8f85d914`).
- **Kind:** fix of Zayit's code (its tests).
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** it is one.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/WidgetSizesScreenshotTest.kt`

- **Change:** modified, +12 −2 lines. Feature: Learning split widget, Home widgets loading.
- **What:** it waits until no card shows `WIDGET_LOADING_TAG` (`OFF_MAIN_TIMEOUT_MS`, 10 s) before measuring, and leaves
  out the learning split widget, which reads its books' titles in the library, as the dictionary is left out; the class
  KDoc says so.
- **Why:** the cards read off the UI thread were measured while loading (`022af5bb`); the learning split widget has no
  library in the test (`0083381e`; `LearningSplitCardUiTest` draws it).
- **Kind:** fix of Zayit's code (its tests), and a Pardes widget left out.
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** it is one.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/SettingsWindowViewModelTest.kt`

- **Change:** modified, +7 −1 lines. Feature: Search in categories.
- **What:** the test's `SearchHomeViewModel` takes a fifth argument, a relaxed `mockk` for `LibraryCategories`.
- **Why:** `SearchHomeViewModel`'s constructor gained `libraryCategories` (`37d4fe60`).
- **Kind:** Pardes feature hook (a test kept compiling).
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** it is one.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/fonts/FontsSettingsEventsTest.kt`

- **Change:** modified, +8 −0 lines. Feature: Daf view.
- **What:** the four daf events are added to the test's list and to its exhaustive `when`.
- **Why:** the `when` over `FontsSettingsEvents` must cover the new events to compile (`e00754a4`, `1a350161`,
  `3c9ec937`).
- **Kind:** Pardes feature hook (a test kept compiling).
- **Merge risk:** small.
- **Smaller:** already minimal.
- **Tests:** it is one.

## 2. Files Pardes adds inside Zayit's modules

Files Pardes adds in Zayit's Gradle modules (SeforimApp, buildSrc, gradle, navigation, ...), by feature: code that
sits among Zayit's own and must read like it. Each feature first names the hooks in Zayit's files that reach it.

### The daf view (צורת הדף)

A Talmud tractate drawn as its Vilna page, inside the reader's own frame, from the layout files that the
pardes-rashi-ocr project makes from the Vilna Shas scans (`layouts/*.json`, read from `pardes-daf/` beside the
library). The hooks in Zayit's files are in `BookContentView.kt`, `BookContentPanel.kt`, `BookContentScreen.kt`,
`VerticalBars.kt`, `AppSettings.kt`, the font settings and `DataSettingsScreen.kt`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafView.kt`

- **Feature:** Daf view.
- **Size:** 1324 lines.
- **What:** the view itself. `DafView` draws every amud of the tractate's layout one below the other at its printed
  height, one scale and one size of letters for them all (`blockWidth`, `DafSizes`, kept with the layouts in
  `LayoutCache`), with its title, zoom, keys, selection, context menu, find marks and review mode; `DafMode` (what a click
  selects) and `DafControl` (what the keys and the find bar ask: page up or down, first or last amud, a line brought into
  view, a step to the next comment) are its two seams with the text view. A comment selected by a click on its own words
  is the one the arrows step from (`focused`): `stepComment` selects the comment read after it or before it in its
  commentary (`DafIndex.next`), on its Gemara line (`baseOf`), brings it into view, and waits for the tab's selection to
  follow (`awaitPassage`), one step at a time. The daf's place is the tab's: once it rests it tells the Gemara's
  segment read at its top (`onScroll`, `DafIndex.gemaraAt`), it opens there (`openAt`, `openOn`: the line the segment
  begins on at the view's very top, an amud's first line at the amud's own top), stays where it was left when the tab's
  place is still its own (back from the text, which was not scrolled), and keeps the place at its top when its scale
  changes (the zoom, the view's width, also while the text was shown: `DafScale`, `requestScrollToItem`).
  `DafControl.place` says where it is (`DafPlace`: that segment, whether the tab's selected line is in view, whether a
  comment is selected by a click on itself), for the text to open in the same state. Also the amud helpers (`isBavli`, `amudOf`, `amudIndex`, `amudTitle`,
  `firstLines`), the page's marks (`dafHighlight`: passage, segments, their comments), clicks
  (`clickOn`, `segmentOf`), the links read for a page (`loadLinks`), the layouts folder (`rememberDafLayouts`), a missing
  layout's page with its download (`MissingLayout`, `DownloadLayout`), the review's menu items and tints, `DafFind`, and a
  sideways pan as the commentaries' pages turn. The review's items (`reviewItems`, while the settings' review is on) are
  two checkboxes, "הקטע מעומד לא נכון" and "יש טעות במילים" (`ContextMenuCheck`), of the lines the right-clicked word's
  selection covers (`reviewedAt`: the reader's own selection when the word is in it, or the selected segment's or
  comment's words on the amud, else the word's line), each written through `DafReviews.say`, one write at a time
  (`reviewing`); a line marked before the two reasons has "ביטול הסימון של השורה" too. Over a tractate's pages,
  `DafUpdateLine` (`DafUpdate.kt`) offers a newer version of its layout.
- **Reached from:** `BookContentView.kt` (`DafView`, `DafControl`, `DafMode`, `DafFind`), `BookContentPanel.kt`
  (`DafMode`, `isBavli`), `VerticalBars.kt` (`isBavli`).
- **Follows:** the text view's own frame and conventions (Zayit's `BookContentView`): its zoom, keys, find bar and menu
  are reused, not redrawn; the page itself is Pardes's own.
- **Tests:** `DafLayoutsTest` (amud names, first lines), `DafLinksTest`, `DafViewStateTest`, `DafZoomTest`; the
  composables have no UI test.
- **Notes:** at 1,324 lines, with some 28 top-level and member functions, it is far beyond `detekt.yml`'s limits
  (600 lines for a class, 15 functions for a file), as some of Zayit's largest files are; it could be split by its
  parts (pages, review, missing layout, marks).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafLayout.kt`

- **Feature:** Daf view.
- **Size:** 210 lines.
- **What:** the layout file's model and folder. `DafLayoutFile` (a tractate's amudim, made by pardes-rashi-ocr's
  `export_layout.py`), `DafLibrary` (the library release a format-2 file's line ids are of), `DafAmud` (page and block
  size, lines), `DafLine` (column, box, words, sureness, dibbur words, each word's library line `u`), and `DafLayouts`:
  a folder's files found by the tractate their header names (English or Hebrew), watched for files put in or taken out,
  the last few read kept, and an import. A file's `version` is its tractate's own among the released layouts (one more
  in each release its layout changed in), read with the file or from its head alone (`versionOf`); a file of before
  the releases gave each tractate one has none.
- **Reached from:** `DafView.kt`, `DafPage.kt`, `DafSetting.kt`, `DafAnchors.kt`, `DafAmudLinks.kt`, `DafReview.kt`,
  `DafDownloads.kt`, `DafLayoutsCard.kt`.
- **Follows:** Pardes's own; kotlinx-serialization as Zayit uses it.
- **Tests:** `DafLayoutsTest`, `DafPageTest`, `DafDownloadsTest` (a file's version).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafSetting.kt`

- **Feature:** Daf view.
- **Size:** 690 lines.
- **What:** typesetting an amud: `layoutAmud` sets every line in runs (a column's lines one below the other at its
  pitch), one size of letters per column, made smaller for a line only as far as its words need, its spaces widened to
  fill its box, a big word's neighbours set beside it, an overfull line given its empty neighbour's room. `DafSizes`
  is a tractate's usual box height for the Gemara and for a commentary, a set share of its block's width
  (`GEMARA_BOX`, `COMMENTARY_BOX`): given to `layoutAmud`, every amud's letters are that one size, whatever the height
  of its own boxes, which follows each scan (without it, as the tests lay out a lone amud, a column's own usual box).
  In Tosafot the first word of each dibbur is set in the Gemara's square letters, bold, 1.07 of the Gemara's own
  (`LEAD_SIZE`, measured on the scans: 162 such words of 12 tractates, pardes-rashi-ocr's
  `docs/experiments/2026-11-10-tosafot-dibbur-size`): a word of a dibbur (`d`) after a word of none, but where the
  dibbur goes on from the line above or after a big first word (`firsts`, `Marks`); a big first word of Tosafot is
  such a word too, set that size and no longer by its box's height (the box is two lines high, the word is not).
  Rashi's dibbur stays bold in its own script. The private setters (`AmudSetter`, `ColumnSetter`, `LineSet`, `Piece`,
  `Marks`) and the constants of the printed page's proportions.
- **Reached from:** `DafView.kt` (`layoutAmud`, `DafSizes`).
- **Follows:** Pardes's own.
- **Tests:** `DafPageTest` (evenly spaced runs, words in order, one size of letters with a tractate's sizes, the
  first word of each dibbur of Tosafot and its size).
- **Notes:** with some 32 functions it passes `detekt.yml`'s 15 per file.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafPage.kt`

- **Feature:** Daf view.
- **Size:** 632 lines.
- **What:** drawing a set amud: `DafAmudText` draws each run of lines as one text in the text's own components (a square
  letter for the Gemara, Rashi script for the commentaries), with highlights behind words, notes underlined, selection by
  click and drag in the reading order (`DafRange`, `textOf`, `lineRangeOf`), a right-click that opens the menu on its
  word and selects nothing, as on the text's lines, and lighter unsure lines when asked (`UNSURE_INK`). A run's lines
  are each given whole pixels of height that follow its pitch (`TextRun.annotated`): the text draws a line whole pixels
  high, and at one height for all a long run ended up to half a line off, on the next run's first line. The first
  word of a dibbur of Tosafot (`RunLine.lead`) is drawn in the Gemara's square letters, bold and bigger than its line's
  (`LeadStyle`, `TextRun.lead`), as the Vilna Shas prints it; a line with one is drawn a pixel or two higher than its
  line height (the square letters stand higher on their line than Rashi script), so it is given the line height that
  is drawn as high as the line is (`leadLineHeight`, measured once for a run and a scale). `DafWord`, `DafHighlight`,
  `RunLine`, `TextRun`, `LeadStyle` and `AmudLayout` are its model.
- **Reached from:** `DafView.kt`, `DafSetting.kt`, `DafAnchors.kt`, `DafAmudLinks.kt`.
- **Follows:** the text view's highlight and note marks (Zayit's `applyUserHighlights`), drawn the same way.
- **Tests:** `DafPageTest` (a click reaches its word, a right-click its menu, a drag goes down a column, a range is
  copied as printed, a long run's lines on their pitch at any scale, with a dibbur's first word in each too, the page
  drawn to `build/reports/daf/`).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafIndex.kt`

- **Feature:** Daf view.
- **Size:** 102 lines.
- **What:** `DafIndex`, a tractate's printed lines of the library, the Gemara's and the commentaries': where each is
  printed first (`places`, what `DafView` scrolls to), and each commentary column's comments in the order they are read
  (`next`): amud by amud, each book's comments by the library's numbers (the lines list a comment a line early or late in
  a third of the amudim), a column's second book where it is printed (the Rashbam after Rashi, Bava Batra 29a). Also
  the Gemara's segment read at the top of a view scrolled to a place (`gemaraAt`): that of the first line printed there
  or below, the first segment that begins on that line, or the one that goes on through it when none does; so a view
  scrolled to a segment's own place reads that segment again, and a tab reopened on the daf does not creep up the
  page. `gemaraTop` is the top of an amud's first line of the Gemara.
- **Reached from:** `DafView.kt` (`reveal`, `openOn`, `stepComment`, the daf's place).
- **Follows:** Pardes's own; it takes the place of `DafView.kt`'s `placesOfLines`, which held the Gemara's lines alone.
- **Tests:** `DafIndexTest`.

#### `icons/src/commonMain/kotlin/io/github/kdroidfilter/seforimapp/icons/GemaraPage.kt`

- **Feature:** Daf view.
- **Size:** 69 lines.
- **What:** the daf view's icon, `GemaraPage`: a page of the Talmud as printed, the Gemara's lines in the middle and its
  two commentaries each a column beside them that turns over them at the top, where the two meet.
- **Reached from:** `VerticalBars.kt` (the end bar's "צורת הדף" toggle).
- **Follows:** Zayit's icons in the module (`ColumnsGap.kt`, which the toggle had): an `ImageVector` built once and
  kept, 16 units square, filled paths.
- **Tests:** none; the icon is seen in the reader's end bar.
- **Notes:** Android compiles the module's folder as it is, so the file stays a plain `ImageVector`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafAnchors.kt`

- **Feature:** Daf view.
- **Size:** 285 lines.
- **What:** each printed word found in the library's text, so that highlights, notes and links work on the daf:
  `DafAnchor` (a word's characters in its library line, diacritics included), `alignWords` (a line's printed words
  aligned with its library words, abbreviations standing for several), `anchorsOf`, and the marks read through them:
  `highlightedWords` (HighlightStore's), `notedWords` (NoteStore's), `foundWords` (the find bar's matches, the current
  one strongest), `wordsOfLines` (the smart find's lines); `locate` turns a selection into each library line's part for
  the text's menu.
- **Reached from:** `DafView.kt`, `DafAmudLinks.kt`, `BookContentView.kt` (`DafAnchor`).
- **Follows:** the text's own offsets of highlights and notes (Zayit's `HighlightStore`, `NoteStore`, `highlightRangesOf`).
- **Tests:** `DafAnchorsTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafAmudLinks.kt`

- **Feature:** Daf view.
- **Size:** 100 lines.
- **What:** one amud's words tied to the library for its clicks and marks: `DafAmudLinks` (each word's library line, each
  comment's Gemara line through the library's COMMENTARY links, read once a page, and which Gemara words each dibbur
  quotes) and `passageAt` (what a click or the pointer on a word stands for).
- **Reached from:** `DafView.kt`; `BookContentView.kt` (`passageAt`).
- **Follows:** the text view's passages (`SelectedPassage`) and the library's links.
- **Tests:** `DafLinksTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafLemmas.kt`

- **Feature:** Daf view.
- **Size:** 228 lines.
- **What:** which words of a Gemara segment each dibbur hamatchil quotes (`matchLemmas`): words compared by their letters
  (`dafKey`), alike with spelling variants, prefixes and plene spelling (`alike`, Levenshtein), abbreviations
  (`abbreviates`, `ABBREVIATION_MARKS`), ellipses, and the print's Mishnah and Gemara marks.
- **Reached from:** `DafAmudLinks.kt` (`matchLemmas`), `DafAnchors.kt`; `alike` is also used by Zayit's
  `BookDetailsLoader.kt`, `HomeView.kt` and `HomeWidgetsGrid.kt` under the same name, their own.
- **Follows:** Pardes's own.
- **Tests:** `DafLemmasTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafReview.kt`

- **Feature:** Daf view (review mode).
- **Size:** 287 lines.
- **What:** a reader's review of a tractate's layout, for pardes-rashi-ocr: `DafReviewFile` and `DafMark`, a line
  with what the reader said is wrong with it (`DafReason`: how it is set on the page, `layout`, or its words, `words`,
  with `ok` false), of which of its words when the passage reviewed starts or ends inside it (`from`, `to`); a mark of
  before the two reasons (a line right, wrong, or its first word moved: `isFormer`) is still read, its moves still made
  (`withMarks`, a mark whose line now holds other words left out). `reviewed` gives the lines a review of a word is of:
  the selection's when the word is in it, each with the words of it meant, else the word's own (`ReviewedLine`).
  `lineBefore`/`lineAfter` in a column's reading order, and `DafReviews`, kept in the layouts' folder under
  `feedback/`, one file a tractate, written at every mark (`say`: a reason said or unsaid of a passage's lines in one
  write, each from its mark as it is in the file, with the version of the layout it was made on, `version`), an
  unreadable file set aside. The format stays 1: the service
  refuses another, and takes fields it does not know.
- **Reached from:** `DafView.kt`, `DafReviewMenu.kt`, `DafLayout.kt`, `FeedbackPackage.kt` (sent with the feedback).
- **Follows:** Pardes's own; the file is read by pardes-rashi-ocr's `feedback.py`.
- **Tests:** `DafReviewTest`, `FeedbackPackageTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafReviewMenu.kt`

- **Feature:** Daf view (review mode).
- **Size:** 94 lines.
- **What:** `ContextMenuCheck`, an entry of the text's menu that is a checkbox (checked or not when the menu opens; a
  click checks or unchecks it and the menu stays open, for another to be checked too), and its row
  (`ContextMenuCheckRow`: Jewel's `Checkbox` and the label, lit under the pointer as the menu's items are); and
  `DafReviewsFolderLink`, the settings' link that opens the folder the reviews are kept in.
- **Reached from:** `BookContentScreen.kt` (`contextItems`, one branch), `DafView.kt` (`reviewItems`),
  `FontsSettingsScreen.kt` (the link).
- **Follows:** Zayit's `ContextMenuHighlightColorPicker` and its row in `BookContentScreen.kt` (a passive item of the
  menu, its own hover and content color); Jewel's `Checkbox` and `Link`.
- **Tests:** none of the composables; the marks they make are `DafReviewTest`'s.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafDownloads.kt`

- **Feature:** Daf layouts download.
- **Size:** 362 lines.
- **What:** the released layouts downloaded into a library's `pardes-daf` folder: `DafReleases` (the `daf-<n>` releases
  from the GitHub API, or the site's `daf.json` when it fails), `DafManifest` (each tractate's gzipped file with its
  sha256 and its layout's, and the tractate's own version, none in a release of before the versions), and
  `DafDownloads` (what the folder has as released, the version of each of its files, and each tractate downloaded,
  checked and imported as a picked file is, one alone or several). The sha256 still says whether a file is the
  release's; the versions are what the reader is shown.
- **Reached from:** `DafView.kt` (a missing tractate's download), `DafUpdate.kt`, `DafLayoutsCard.kt`.
- **Follows:** Zayit's library download (`GitHubReleaseFetcher`, `HttpFileDownloader`) in spirit; its own code.
- **Tests:** `DafDownloadsTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafUpdate.kt`

- **Feature:** Daf layouts download.
- **Size:** 85 lines.
- **What:** `newerFor`, the tractate's newer version among the released layouts than the folder's file (none when the
  file is the release's, a later one than it, or the release has no versions), and `DafUpdateLine`, a quiet line at
  the daf's bottom corner that names it with a link that downloads it, then has the daf read the folder again. The
  releases are read once a run, and only when the app checks for updates by itself (the settings' switch).
- **Reached from:** `DafView.kt` (`DafView`, over a tractate's pages).
- **Follows:** `DafView.kt`'s `DownloadLayout` (the same downloads and progress text); Jewel's `Link`.
- **Tests:** `DafDownloadsTest` (`newerFor`); the line itself has none.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/daf/DafLayoutsCard.kt`

- **Feature:** Daf layouts download.
- **Size:** 213 lines.
- **What:** Settings › צורת הדף's card for the daf's layouts (in Settings › ניהול נתונים until 2026-11-10): how many the library's folder has as released, and
  every one it lacks, or has otherwise, downloaded at once; the download goes on when the settings close. Each
  tractate has a row (`Tractates`, `tractateLine`): its name, its file's version in the folder and the release's, and
  for one to download a link that downloads it alone; those to download are always listed, the others when asked
  ("כל המסכתות והגרסאות שלהן").
- **Reached from:** `DafSettingsScreen.kt` (`DafLayoutsCard()`).
- **Follows:** the settings cards of Zayit's data page (their layout and components).
- **Tests:** `DafLayoutsCardUiTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/daf/DafSettingsScreen.kt`

- **Feature:** Daf view.
- **Size:** 82 lines.
- **What:** `DafSettingsScreen`, Settings › צורת הדף: the daf view's settings in one page, in order: the lighter unsure
  lines with their note, the review mode with its note and the link to its folder, then the layouts' card
  (`DafLayoutsCard`). They stood among the fonts and among the data until 2026-11-10, when the user asked for a
  category of their own; the daf's two fonts stay among the fonts (the user's word of 2026-11-11).
- **Reached from:** `SettingsNavHost.kt` (`SettingsDestination.Daf`), from `SettingsSidebar.kt`'s item.
- **Follows:** Zayit's `FontsSettingsScreen` (a screen over a view of state and events, the same column of cards), whose
  view model it reads: the daf's switches are kept there with its fonts (`FontsSettingsState`, `FontsSettingsEvents`).
- **Tests:** none of the screen; `DafLayoutsCardUiTest` covers its card.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/LocatedSelection.kt`

- **Feature:** Daf view.
- **Size:** 30 lines.
- **What:** `LocatedSelection` (a selection its content locates in the library itself: each library line's part of it,
  in that line's book), `LocatedRange`, and `LocalLocatedSelection`, which the text's menu reads in place of a search of
  the selected text.
- **Reached from:** `BookContentScreen.kt` (`LocalLocatedSelection`), `DafView.kt`, `DafAnchors.kt`.
- **Follows:** Zayit's `LocalBookSelection` beside it, the same kind of composition local.
- **Tests:** `DafAnchorsTest`.
- **Notes:** its KDocs start with "Pardes:".

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/core/settings/DafZoomTest.kt`

- **Feature:** Daf view.
- **Size:** 116 lines.
- **What:** the daf's own settings: the zoom keys, buttons and menu zoom the text, and a tab's daf with it while it
  shows one, level by level; the daf's own zoom (a pinch) zooms the text by the same ratio, the same text back at the
  same zoom, and goes on from the next level; the views' count; the unsure lines and the review mode off until asked and reset; the zoom kept and reset.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's settings tests (`testAppSettings()`).
- **Tests:** it is one (unit, `AppSettings`).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafAnchorsTest.kt`

- **Feature:** Daf view.
- **Size:** 132 lines.
- **What:** the printed words found in the library's text: abbreviations, a line begun on the amud before, a printed word
  the library lacks, a word spelled otherwise, highlights and notes on their words, the find's matches, a selection
  located line by line in each line's own book.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, `DafAnchors.kt`).
- **Notes:** its test names are camelCase (`anAbbreviationOfThePrintStandsForAllTheWordsItAbbreviates`), not Zayit's
  sentences in backticks.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafDownloadsTest.kt`

- **Feature:** Daf layouts download.
- **Size:** 231 lines.
- **What:** the newest published release with a manifest chosen, a tractate found by either name, downloaded, checked and
  put in the folder, a layout that is not the manifest's refused, a folder's own file seen as to be updated; a
  tractate's version read from its file's head, a newer one in the release offered and, downloaded, the folder's, a
  later file than the release's left, and a release of before the versions offering none.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test and coroutines' test dispatcher; the releases and files are faked in memory (gzipped, with
  their sha256).
- **Tests:** it is one (unit, `DafDownloads.kt`).
- **Notes:** camelCase test names.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/daf/DafLayoutsCardUiTest.kt`

- **Feature:** Daf layouts download.
- **Size:** 98 lines.
- **What:** the layouts' card drawn with three tractates (one the release's, one an older version, one missing): each
  to download with its row and its own link, all of them by the button, the release's own row only when the list is
  asked for; and a release of before the versions, its rows without one. The card's drawing is written to
  `build/reports/daf/layouts-card.png`.
- **Reached from:** the JVM tests.
- **Follows:** `FeedbackSendCardUiTest` (Compose UI test on the desktop, Jewel's `IntUiTheme`, right to left).
- **Tests:** it is one (Compose UI).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafLayoutsTest.kt`

- **Feature:** Daf view.
- **Size:** 127 lines.
- **What:** a folder's layouts found by either tractate name, a half-written copy not hiding the file, a file put in
  later seen, an import under the English name, format 2's word ties and format 1 still opening, amud names, and each
  amud's first line from the table of contents.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, `DafLayout.kt`, `DafView.kt`'s helpers).
- **Notes:** camelCase test names.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafLemmasTest.kt`

- **Feature:** Daf view.
- **Size:** 81 lines.
- **What:** Rashi's nine comments on Berakhot 2a placed, a print word between a lemma's first and second words, an
  ellipsis, a word spelled with its yod elsewhere, abbreviations and the print's marks, a word not in the segment, keys of
  letters only.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, `DafLemmas.kt`).
- **Notes:** camelCase test names.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafLinksTest.kt`

- **Feature:** Daf view.
- **Size:** 70 lines.
- **What:** on a small page, each dibbur quotes its words of the Gemara, a click selects the passage or the segment, and
  the passage is marked strongest, then the segment, then its comments.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, `DafAmudLinks.kt`, `DafView.kt`'s marks).
- **Notes:** camelCase test names.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafPageTest.kt`

- **Feature:** Daf view.
- **Size:** 433 lines.
- **What:** Berakhot 35b drawn from its layout (`berakhot-35b.json`): every line in a run of its column, evenly spaced, a
  run's text in order and searchable, the amud and a copy of it with lower boxes set in one size of letters by a
  tractate's sizes, a run of 52 lines each within a pixel of its pitch at five scales, a click reaching its callback, a right-click opening the menu on its word with nothing selected, a
  drag down a column, a range read in the amud's order and copied as printed; the drawing written to
  `build/reports/daf/`. And an amud made for the dibburim of Tosafot: the first word of each in the Gemara's letters,
  1.07 of the Gemara's size, but where a dibbur goes on from the line above or after a big first word (itself that
  size), Rashi's bold alone; 14 lines with such a word each within a pixel of their pitch at five scales.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's screenshot tests: an `ImageComposeScene` drawn to PNG, pointer events sent to it.
- **Tests:** it is one (Compose, off screen).
- **Notes:** camelCase test names.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafIndexTest.kt`

- **Feature:** Daf view.
- **Size:** 109 lines.
- **What:** over three amudim made for it: a column's comments read in the library's order, each once, though a line
  lists one early; a second book of the column read where it is printed; a column ending where its comments do, the
  Gemara's lines no comments; the Gemara's segment read at a place (the first that begins on the line there, or the
  one going on through it; past an amud's last line, the next amud's first), and a view scrolled to a segment's place
  reading that segment again; every line with the place it is printed at first.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test, backtick names.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafReviewTest.kt`

- **Feature:** Daf view (review mode).
- **Size:** 185 lines.
- **What:** a line's start moved a word on or back with its library line and bold, a move that would empty a line left
  out, a mark of a changed line or another amud moving nothing, the review kept in the feedback folder (the new mark in
  place of the old), written to the file it was read from, and an unreadable file set aside. The two reasons: a review
  is of the selection's lines when the word is in it, with the words it holds (its column's lines alone, no big word),
  else of the word's line; a reason said of a passage's lines in one write and unsaid again, the other reason kept, no
  mark left with neither, each mark with the version of the layout it was made on; a mark of before the reasons kept
  under them (its move still made) and told apart.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, `DafReview.kt`).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/DafViewStateTest.kt`

- **Feature:** Daf view.
- **Size:** 33 lines.
- **What:** a tab shown as its Vilna page is shown so again when the session is restored (`dafView` through the
  persisted state).
- **Reached from:** the JVM tests.
- **Follows:** Zayit's session tests.
- **Tests:** it is one (unit, `BookContentStateManager`, `SessionModels`).
- **Notes:** camelCase test name.

#### `SeforimApp/src/jvmTest/resources/daf/berakhot-35b.json`

- **Feature:** Daf view.
- **Size:** 1 line.
- **What:** the layout of Berakhot 35b in format 2 (20 KB, one line of JSON): the page and block sizes and every printed
  line's column, box, words, sureness and library lines; made by pardes-rashi-ocr's `export_layout.py`, from the Vilna
  Shas, a public-domain print, and Sefaria's text (`5718d676`).
- **Reached from:** `DafPageTest`.
- **Follows:** Pardes's own data.
- **Tests:** it is a fixture.

### Commentators in their own panes, and the commentator selection kept

A commentator can be split off the commentaries pane into a pane of its own (torn off by its header, placed anywhere in
the dock, or opened in a window), and a commentator, a group or "כל המפרשים" checked stays checked from line to line. The commentator the reader is on
in the pane's pages is kept from line to line too (`KeptCommentator.kt`).
The hooks in Zayit's files are in `ReaderPanes.kt`, `LineCommentsView.kt`, `CommentatorsGrid.kt`,
`CommentariesUseCase.kt`, `BookContentViewModel.kt`, the reader's state and `SessionModels.kt`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/state/CommentatorSelection.kt`

- **Feature:** Commentator selection kept.
- **Size:** 128 lines.
- **What:** `CommentatorSelection`, the commentators a book's lines show: those checked one by one (`remembered`), all of
  them ("כל המפרשים"), whole groups checked, the groups hidden while all are shown, and the commentators unchecked inside
  a checked group (`excluded`); `changed(...)` applies a line's change, `allChecked/allUnchecked`, `groupChecked/
  groupUnchecked`, and `shownOf(listed)` gives a line's commentators to show. Extensions: `listedCommentators()` (a
  line's list with each commentator's group), `ContentState.commentatorSelection(bookId)`,
  `withCommentatorSelection(bookId, selection)` and `Map.withLine(lineId, ids)`.
- **Reached from:** `CommentariesUseCase.kt`, `BookContentViewModel.kt`, `BookContentPanel.kt`, `LineCommentsView.kt`.
- **Follows:** Zayit's `selectedCommentatorsByBook` rule (a commentator checked on a line is remembered for the book),
  extended to whole groups; documented in `LINE_SELECTION_README.md` › Whole groups.
- **Tests:** `CommentatorSelectionTest`, `CommentariesUseCaseAllCommentatorsTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/state/DetachedCommentators.kt`

- **Feature:** Commentators in their own panes.
- **Size:** 37 lines.
- **What:** `DetachedCommentators`, the commentators a tab shows in panes of their own, by name, in the order they were
  split off, at most `MAX` (8): `names` (also reading desktop 1.0.5's list by slots), `detach`, `reattach`, `canDetach`.
- **Reached from:** `BookContentViewModel.kt`, `BookContentStateManager.kt`, `BookContentPanel.kt`, `ReaderPanes.kt`,
  `LineCommentsView.kt`.
- **Follows:** Pardes's own.
- **Tests:** `DetachedCommentatorsTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panes/CommentatorTearOffs.kt`

- **Feature:** Commentators in their own panes.
- **Size:** 86 lines.
- **What:** `CommentatorTearOffs`, the commentators of one window that can be torn off its commentaries pane (those whose
  header is on screen: `show`/`hide`), their satellites' ids, the header's grip (`tearOffHandle`, Nucleus's
  `Modifier.satelliteTearOffHandle` from the vendored tao module) and `openInWindow`; `LocalCommentatorTearOffs`.
- **Reached from:** `ReaderPanes.kt` (`WindowPanes`), `LineCommentsView.kt`, `CommentatorsGrid.kt`.
- **Follows:** Zayit's satellite panes (Nucleus's `SatelliteWorkspace`), as `ReaderPanes.kt` declares them.
- **Tests:** `CommentatorPaneIdTest`.
- **Notes:** it needs the tear-off of efrul/Nucleus's branch, vendored in `nucleus-fork/` until Nucleus releases it.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/state/CommentatorSelectionTest.kt`

- **Feature:** Commentator selection kept.
- **Size:** 195 lines.
- **What:** fifteen tests of `CommentatorSelection`: a list shows only what is remembered; "כל המפרשים" shows every
  commentator on every line, those no line before had too; a commentator unchecked or checked again under it; a group
  checked, unchecked, hidden while all are shown; a commentator checked in a hidden group; a list's change, an unchanged
  list; how a line's list is read.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test, sentences in backticks.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/state/DetachedCommentatorsTest.kt`

- **Feature:** Commentators in their own panes.
- **Size:** 49 lines.
- **What:** detach appends once and stops at the cap, reattach keeps the others in order, desktop 1.0.5's saved list
  reads as names, and what is written reads in 1.0.5 as its first slots.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/CommentatorsCheckStateTest.kt`

- **Feature:** Commentator selection kept.
- **Size:** 25 lines.
- **What:** a group's checkbox is checked when all its commentators are, half checked when some are, unchecked when none
  is (`commentatorsCheckState`).
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/CommentatorsPageSwipeTest.kt`

- **Feature:** Commentators in their own panes (pages turned sideways).
- **Size:** 145 lines.
- **What:** a touchpad's two-finger pan turns the commentators' page in the reading direction, one page a swipe; an
  up-and-down swipe, a short swipe and a mouse drag turn none (`Modifier.pageSwipe`).
- **Reached from:** the JVM tests.
- **Follows:** Zayit's screenshot tests: an `ImageComposeScene` (right to left) receiving pointer events.
- **Tests:** it is one (Compose, off screen).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panes/CommentatorPaneIdTest.kt`

- **Feature:** Commentators in their own panes.
- **Size:** 52 lines.
- **What:** a commentator's pane id is plain and gives the name back, the other panes' ids are none, a window's satellite
  id resolves to its commentator, the dock opening or closing a commentator's pane is followed on the tab, and the panes
  spread around the text by default.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, `ReaderPanes.kt`).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/usecases/CommentariesUseCaseAllCommentatorsTest.kt`

- **Feature:** Commentator selection kept.
- **Size:** 297 lines.
- **What:** "כל המפרשים" and the groups' checkboxes from line to line, as the commentaries pane drives the use case (a
  line selected, its list reporting its first frame, the reader checking): ten tests, among them a commentator no line
  had, one unchecked and seen again, all unchecked, a group checked or unchecked, a list not unchecking what it does not
  show, and the whole groups kept with the tab.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's use-case tests: the real `BookContentStateManager` and `CommentariesUseCase`, the repository and
  the lists mocked with MockK (`mockk`, `spyk`).
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/KeptCommentator.kt`

- **Feature:** Commentator kept.
- **Size:** 98 lines.
- **What:** `KeptCommentator`, the commentator the reader is on in the commentaries' pages, kept from line to line and
  from passage to passage: his `name`, set by the reader alone (`turns()` then `settledOn(...)`: a page he turned;
  `chose(...)`: a commentator he ticked, or the first passage he clicked); `settledOn(first, shown, focus)` says whom
  the pages follow once settled (the reader's turn, else the passage's commentator, else the reader's when the line
  has him, else the page's first); `among(shown)`; `listedOn(line)` and `tickedOn(line)` tell a commentator ticked
  from one who comes back with a line. `KeepCommentator(...)`, the composable that follows him on each line that has
  him while no passage is selected. A turn that never settled is forgotten after three seconds.
- **Reached from:** `CommentatorsGrid.kt` (`CommentatorsGridScaffold`, the dots, `pageSwipe`), `LineCommentsView.kt`
  (`CommentariesColumnScroll.kept`, the passage's commentator).
- **Follows:** Zayit's anchor in `CommentatorsGridScaffold` ("the commentator the user is focused on"), which it keeps
  beyond one line: the grid leaves the composition between two lines when a line's commentators are not ready, and
  came back on its page's number, another commentator's.
- **Tests:** `KeptCommentatorTest`; stepping down בבא בתרא's lines was tried in the app.
- **Notes:** on a line he does not comment on the pages show another commentator, Zayit's choice as before.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/KeptCommentatorTest.kt`

- **Feature:** Commentator kept.
- **Size:** 60 lines.
- **What:** the page the reader turns to is his choice and the page the pane leaves is not; a passage's commentator is
  followed without replacing the reader's; a turn that never settled is forgotten; a commentator ticked is told from
  one who comes back with a line.
- **Reached from:** the JVM tests.
- **Follows:** `CommentatorsCheckStateTest` beside it (Kotlin Test, backtick names).
- **Tests:** it is one (unit).

### The reader's default commentators

A pin at the end of "כל המפרשים" in the commentators list keeps the commentators checked as the reader's default, as
the user asked on 2026-11-09 ("קבע כברירת מחדל"): the book opens with them in a tab that chose none, and so do the books
beside it (its category's, then those under the category above: a default set on קידושין is every tractate's of the
Bavli), each by its own commentators of those names. It comes before the library's own default, also when it shows no
commentator. Pinned (the accent's colour on a wash of it) while the book shows it, and a click then lets it go, leaving
what the tab shows; the tooltip says what a click does and names the category that shares it. Kept by names in the
user's database, so in its backups, and whatever ids a new library gives its books. Hooks: `LineCommentsView.kt`,
`CommentariesUseCase.kt`, `BookContentUseCaseFactory.kt`, `BookContentEvent.kt`, `BookContentViewModel.kt`,
`AppGraph.kt`, `strings.xml`; in Pardes's `LibraryCategories.kt`, a book's place and commentaries.

#### `SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/CommentatorDefaults.sq`

- **Feature:** Reader's default commentators.
- **Size:** 20 lines.
- **What:** the user database's table `commentator_default` (`CREATE TABLE IF NOT EXISTS`, so made at the next start):
  a row a book, keyed by its categories' titles (JSON) and its title, with the default as JSON and when it was set;
  `upsert`, `selectAll` and `delete`.
- **Reached from:** `CommentatorDefaults.kt` (`database.commentatorDefaultsQueries`).
- **Follows:** `CategoryDisplaySettings.sq` beside it, a reader's setting kept by category, and `ReadingTime.sq`,
  Pardes's other table there.
- **Tests:** `CommentatorDefaultsTest` (an in-memory database).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/state/CommentatorDefaults.kt`

- **Feature:** Reader's default commentators.
- **Size:** 193 lines.
- **What:**
  - `CommentatorDefault`, a `CommentatorSelection` by names: the groups as the list names them, and the commentators
    checked or unchecked one by one by their names beside the book (`sanitizeCommentatorName`); `of(selection, nameOf)`
    and `selection(commentators)`, which leaves out a commentator the book does not have.
  - `KeptCommentatorDefault.forBook(kept, categoryPath, bookTitle)`: the default a book takes, its own, else its
    category's last one set, else the last one under the category above; none across the library's top categories.
  - `CommentatorDefaultState`: the category that shares a default set on the book, whether one applies, and whether it
    is what the book shows.
  - `CommentatorDefaults` (injected, one for the app): `of(bookId)`, `stateOf(bookId, selection)`, `set`, `clear` (the
    default the book takes, its own or another book's) and `revision`, over the table and the library's facts
    (`LibraryCategories.bookPlace`, `commentariesOn`, `books`).
- **Reached from:** `CommentariesUseCase.kt` (through `BookContentUseCaseFactory.kt`), `CommentatorsDefaultPin.kt`
  (through `AppGraph.kt`).
- **Follows:** `CommentatorSelection.kt` beside it, whose selection it keeps; Zayit's stores over `UserSettingsDb`
  (`CategoryDisplaySettingsStore`, `HistoryStore` and its `revision`).
- **Tests:** `CommentatorDefaultsTest`, `CommentariesUseCaseReaderDefaultTest`.
- **Notes:** a commentator named after its book without "על" (רי״ף בבא בתרא) keeps that name, and is not found beside
  another book; whole groups and "כל המפרשים" need no name.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/CommentatorsDefaultPin.kt`

- **Feature:** Reader's default commentators.
- **Size:** 134 lines.
- **What:** `rememberCommentatorsDefault(bookId, selection, onEvent)`, the default's state for the book shown, read
  again when the book, its commentators or a default changes; and `CommentatorsDefaultPin`, an icon button (Jewel's
  `General.Pin`, `PinSelected` in the accent's colour on a wash of it while pinned) that sends
  `CommentatorsDefaultSet` or `CommentatorsDefaultCleared`, under a tooltip of two lines; nothing for a book the catalog
  doesn't have.
- **Reached from:** `LineCommentsView.kt` (`LineCommentsView`, `CommentatorsList`).
- **Follows:** Zayit's `FavoriteStarButton` (`BreadcrumbActionsView.kt`): an icon that keeps something, lit while it is
  kept, under a tooltip; `ReattachCommentatorButton` for an icon button at a row's end of this list.
- **Tests:** none of its own (its state is `CommentatorDefaultsTest`'s); tried in the app, both states and the tooltip.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/state/CommentatorDefaultsTest.kt`

- **Feature:** Reader's default commentators.
- **Size:** 192 lines.
- **What:** a selection kept by names and read on a book by the ids it has for them; which default a book takes (its
  own, its category's last, one under the category above, none across top categories); over an in-memory database
  and a catalog of two categories, a default set on a book is that of the books beside it by their own commentators,
  the list is told whether one applies and is shown, a default of no commentator is one until let go, and a book's own
  default stays when one beside it lets its own go.
- **Reached from:** the JVM tests.
- **Follows:** `LibraryCategoriesTest` (a mocked catalog), `CommentatorSelectionTest` beside it.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/usecases/CommentariesUseCaseReaderDefaultTest.kt`

- **Feature:** Reader's default commentators.
- **Size:** 111 lines.
- **What:** a book opens with its tab's commentators, else the reader's default (also one of no commentator), else the
  library's; the default is set from what the book shows, and letting it go leaves what the tab shows.
- **Reached from:** the JVM tests.
- **Follows:** `CommentariesUseCaseAllCommentatorsTest` beside it (a state manager over a store, MockK).
- **Tests:** it is one (unit).

### Reading time and the learning split widget

How the learning splits between מקרא, משנה and תלמוד ("לעולם ישלש אדם שנותיו", Kiddushin 30a), counted by openings of a
book or by the time it was read, as Pardes for Android's widget. Hooks: `MainAppWindow.kt`, `BookContentScreen.kt`,
`AppGraph.kt`, `AppCoreBindings.kt`, `HistoryScreen.kt`, `strings.xml`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/reading/ReadingTimeStore.kt`

- **Feature:** Reading time.
- **Size:** 123 lines.
- **What:** `ReadingTimeStore`, how long each book was read, per day, in the user DB (table `reading_time`): a book's tab
  `track`s it while the tab is in front and its window focused, written every `TICK_MS` so a crash loses little, and it
  stops `IDLE_MS` (five minutes) after the last input; `totalsSince` and `clearAll`. `LastInput` and `Modifier.noteInput()`
  mark the user present on every pointer event; `monotonicMillis` is the clock.
- **Reached from:** `BookContentScreen.kt` (`track`), `MainAppWindow.kt` (`noteInput`, `LastInput`), `AppCoreBindings.kt`,
  `AppGraph.kt`, `HistoryScreen.kt` (`clearAll`), `LearningSplitWidget.kt`.
- **Follows:** Zayit's user-DB stores (`HistoryStore`: a SQLDelight table, a `revision` flow); ported back from Pardes for
  Android's `ReadingTimeStore`.
- **Tests:** `ReadingTimeStoreTest`.

#### `SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/ReadingTime.sq`

- **Feature:** Reading time.
- **Size:** 18 lines.
- **What:** the table `reading_time` (`bookId`, `day` as an epoch day, `seconds`; key `bookId, day`) and its queries
  `addSeconds`, `totalsSince` and `deleteAll`, as Pardes for Android's table of the same name.
- **Reached from:** `ReadingTimeStore.kt` (through SQLDelight's generated `UserSettingsDb`).
- **Follows:** Zayit's user-DB `.sq` files beside it, created with `IF NOT EXISTS`.
- **Tests:** `ReadingTimeStoreTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/LearningSplitWidget.kt`

- **Feature:** Learning split widget.
- **Size:** 796 lines.
- **What:** the Home widget `LearningSplitWidget` and its card: the books opened (from the history) or read (from
  `ReadingTimeStore`) over a period, each counted in its part by its place in the library (`DEFAULT_PARTS`, `unitOf`,
  `partOf`, the user's moves), the shares as bars against the thirds (`wholePercents`), each part's books opening where
  they were last read, and the options page (what is counted, over what time, each unit's part), saved in
  `LearningSplitSettings`.
- **Reached from:** the widget registry (`availableHomeWidgets`, generated by `:widgetprocessor` from every `HomeWidget`
  object).
- **Follows:** Zayit's library widgets in the same package (`HomeWidget`, `WidgetSettings`, `PanelCard`), and Pardes for
  Android's widget, ported back.
- **Tests:** `LearningSplitTest`, `LearningSplitCardUiTest`.
- **Notes:** some 30 Hebrew texts the user sees are literals in Kotlin (the options, headings, periods, empty states,
  `timeLabel`), not `strings.xml` keys as `AGENTS.md` asks; with about 31 functions it is past `detekt.yml`'s 15 a file.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/core/reading/ReadingTimeStoreTest.kt`

- **Feature:** Reading time.
- **Size:** 59 lines.
- **What:** a book read counts until its tab leaves and stops five minutes after the last input, by day, and is
  forgotten with the history.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's store tests (an in-memory `UserSettingsDb`).
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/LearningSplitTest.kt`

- **Feature:** Learning split widget.
- **Size:** 74 lines.
- **What:** each book in its part by its place, the most first, what no part holds left out; the time read alone or
  both; a unit moved or left out; whole percents adding up to a hundred; time read as people say it.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/LearningSplitCardUiTest.kt`

- **Feature:** Learning split widget.
- **Size:** 63 lines.
- **What:** the card shows each part's share and books, and opens a book where it was read.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's widget UI tests.
- **Tests:** it is one (Compose UI).

### The reader's menu: a copy of the words alone

"העתק טקסט בלבד" in the reader's context menu: the selected text without its nikud, teamim and punctuation, as the user
asked on 2026-11-09. Hooks: `BookContentScreen.kt`, `strings.xml`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/LettersOnly.kt`

- **Feature:** Copy of the words alone.
- **Size:** 50 lines.
- **What:** `lettersOnly(text)`, a text as its words alone: Zayit's `HebrewTextUtils.removeAllDiacritics`, then every
  character that is no letter or digit taken out, a space where it stood between two words (a maqaf, a dash, a slash),
  the lines kept and the spaces folded. A geresh after a letter and gershayim between two letters stay, as the word's
  own spelling (וגו׳, רש״י). `hasMoreThanLetters(text)` says whether such a copy would differ, for the menu to offer it.
- **Reached from:** `BookContentScreen.kt` (`BookTextMenus`).
- **Follows:** Zayit's `core/CopyWithSource.kt`: the copy actions' top-level functions, in `core`.
- **Tests:** `LettersOnlyTest`.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/core/LettersOnlyTest.kt`

- **Feature:** Copy of the words alone.
- **Size:** 44 lines.
- **What:** a verse loses its nikud, teamim and sof pasuq; a maqaf, a dash and a slash become a space; רש"י, ד"ה, וגו'
  and ר׳ keep their marks while the quotation marks around words go; lines and numbers stay; and a text of words alone
  is told from one with marks.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's `CopyWithSourceTest` beside it (Kotlin Test, backtick names).
- **Tests:** it is one (unit).

### The passage beside the results, read as a book is

The passage shown beside the search results (Zayit's `PassagePreview`: the found line among the lines around it, a click
on a line opening the book there) can be read without opening the book, as the user asked on 2026-11-10: its text is
selected with the mouse and copied; a right click opens the reader's menu, of what a passage not opened can do ("העתק
ללא ניקוד", "העתק טקסט בלבד", "העתק עם מקור", "העתק קישור לקטע זה", "חפש במאגר", "פתח במילון", and "פתח ספר" at the
line clicked); and a wheel turned past the passage's first or last line reads 30 more lines of the book that way, up
to 600. A click on a line still opens the book there, some 300 ms later (a second click selects its word instead).
Hook: `SearchResultScreen.kt`. No string is added: the labels are the reader's own, and Zayit's unused `open_book`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/PassageReading.kt`

- **Feature:** Passage read as a book.
- **Size:** 399 lines.
- **What:**
  - `PassageReading`, the state of one result's passage: its `lines` (the result's own, then those read before and
    after them, `more(before)`: 30 at a time by `lineIndex`, one reading at a time, none past the book's ends or 600
    lines); `pastEnds`, a nested scroll connection that asks for more when a scroll is left over at an end;
    `linesOf(selected)`, the first and last lines of the selected text, counted from the line its press began on (down
    when the selection begins in that line, up when it ends there); and `laidOut(height)` with `heldBack`, the height
    of lines just added before those read.
  - `rememberPassageReading(preview)`, anew for each result, reading the book through `SeforimRepository.getLines`.
  - `Modifier.readPastEnds(passage, scroll)`: the nested scroll connection, and the wheel's own events at an end (a
    passage shorter than its pane scrolls nothing).
  - `PassageText`: a `SelectionContainer` under a `TextContextMenu` that adds the menu's items (`PassageActions`) to the
    app's own (Jewel's "העתקה"), and the lines added before those read kept out of the way: drawn above the view
    (`offset`) until the scroll takes their height (`dispatchRawDelta`), so what is read stays in its place.
  - `Modifier.passageLine(passage, lineId, onOpen)`: a click of its own opens (not a drag, not a click that lets a
    selection go, not the first of two on a word, waited for `doubleTapTimeoutMillis`), nothing consumed, so the
    selection takes the same presses; the line pressed and the line right-clicked are kept.
- **Reached from:** `SearchResultScreen.kt` (`PassagePreview`).
- **Follows:** the reader's `BookTextMenus` (`BookContentScreen.kt`) for the menu's items, labels and icons, and its
  helpers (`buildCopyWithSourcePayload`, `bookShareLink`, `lettersOnly`, `lookupWord`); `PreviewHighlights.kt` beside
  it, the other part of the passage Pardes adds.
- **Tests:** `PassageReadingTest`; the selection, the menu, the clicks and the scroll were tried in the app.
- **Notes:** what needs the book open is left to the reader: a note, a highlight, the find bar, a report of an error.
  The lines are composed all of them (a `Column`, as Zayit's), which keeps a selection whole however far it is
  scrolled, and is why the reading stops at 600 lines.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/PassageReadingTest.kt`

- **Feature:** Passage read as a book.
- **Size:** 125 lines.
- **What:** more of the book read after the passage and before it; nothing read past the book's ends, an end asked
  once; one reading at a time; a selection's lines counted from the line pressed, down or up, or from the line of a
  right click; the lines added before the passage held back for the scroll, those after not.
- **Reached from:** the JVM tests.
- **Follows:** `SearchUiStateTest` beside it (Kotlin Test, backtick names), `BreadcrumbNavigatorTest` for the test scope.
- **Tests:** it is one (unit).

### The search's results in the library's order

A link in the results' status line, "מיון לפי סדר הספרייה", lists the lines found as the library has its books (the
Tanakh's before the Mishnah's, a book's lines as they stand in it) instead of by relevance, as the user asked on
2026-11-09; "מיון לפי רלוונטיות" goes back. Every line the words find is listed, a book's together, and with the meaning
search the lines it alone found stand among them in their place. The choice is the tab's, kept with it. Nothing of
SeforimLibrary's index changes: the books that have lines found are known from the facets, and are read a few at a time
in the library's order, each sorted by its lines' places. On the whole library the first page comes in 0.03 to 0.9
seconds, a search of 1.7 million lines included. Hooks: `SearchResultViewModel.kt`, `SearchResultScreen.kt`,
`TabsContent.kt`, `SessionModels.kt`, `strings.xml`; in Pardes's files, `LibraryCategories.kt`, `HybridSearchEngine.kt`
and `MeaningFacets.kt`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/CatalogOrderSearch.kt`

- **Feature:** Results in the library's order.
- **Size:** 198 lines.
- **What:**
  - `CatalogOrderEngine(engine, bookOrder, byCatalog)`, a `SearchEngine` that is `engine` in everything (`by engine`)
    but its sessions: while `byCatalog()` says so, `openSession` gives a `CatalogOrderSession` over the engine's words
    (`WordsEngine.words`, else the engine itself), with the engine's own session beside it when that one lists lines
    found by meaning (`MeaningFacets`).
  - `CatalogOrderSession`, a `SearchSession` and `MeaningFacets`: on its first page it counts the books that have lines
    found (`computeFacets`) and reads the engine's own list once (some 300 lines, and how many of each book's the words
    don't find); then it reads the books in the library's order (`bookOrder`, a book without a place last), as many as
    fill the page asked for (200 books at most in one reading), each group in one session of the words, sorted by book
    and `lineIndex`; the meaning's own lines of those books join them, each line once. Its pages show the words in bold
    (`attachSnippets`), a line the meaning alone found its text and then its passage closest in meaning
    (`refinedSnippets`, the engine's own session's); its total counts both.
- **Reached from:** `SearchResultViewModel.kt` (its `lucene`).
- **Follows:** Zayit's `SearchEngine` and `SearchSession` contract (pages asked one after the other, `snippets = false`
  to skip, a `Mutex` around the cursor as `LuceneSearchEngine`'s session), and `HybridSearchEngine`'s session beside
  it, the other session Pardes adds.
- **Tests:** `CatalogOrderSessionTest`; the order, the tabs in it and the choice kept over a restart were tried in the
  app on the whole library.
- **Notes:** nothing is ranked, so a book with thousands of lines found is read whole when its turn comes (2,000 lines
  at a time). The passage beside the results still highlights by the engine's rule (by meaning with a pack), while
  the list shows the words in bold.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/CatalogOrderSessionTest.kt`

- **Feature:** Results in the library's order.
- **Size:** 285 lines.
- **What:** the lines come book by book in the library's order, each book's as they stand in it; the books are read as
  the pages ask for them, those with a line or two together, and the last page says so; a line the meaning alone found
  stands in its place and is counted, one the words found too comes once; the words' lines show the words, the
  meaning's own its text and then its passage; a search in some lines reads them alone; the session closes the
  engine's own; and the engine gives its own session unless the library's order is asked for, reading the words of an
  engine that adds lines by meaning.
- **Reached from:** the JVM tests.
- **Follows:** `MeaningFacetsTest` and `HybridSearchEngineFallbackTest` (a fake `SearchEngine` listing given hits),
  Kotlin Test with `runTest`.
- **Tests:** it is one (unit).

### A desktop as one link

A whole desktop is copied as one link from the desktops' menu ("העתק קישור למרחב"), and pasted into a search bar it
opens as a new desktop with the same tabs, as the user asked on 2026-11-09: `zayit://desktop/<name>?t=<tab>&t=<tab>&s=<shown>`,
each tab its own Zayit link without the scheme (a book at a line, a search), up to forty. A tab is linked to where it
is read now (the book it shows, the line at the top of the screen), not where it was opened; the Home and the other
pages without a link are left out. A book the library lacks is left out when the link opens, and a name a desktop here
already has gives way to the next default one. Pardes registers no URL scheme (`branding/brand.gradle`), so the system
never hands it a link: the search bar is the way in, as for a tab's link. Hooks: `DesktopSwitcher.kt`,
`DesktopManager.kt`, `SearchHomeViewModel.kt`, `TabsContent.kt`, `strings.xml`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/deeplink/DesktopLink.kt`

- **Feature:** Desktop link.
- **Size:** 118 lines.
- **What:** the link's form and what stands on either side of it. `desktopShareLink(name, tabs, selected)` writes it
  from the tabs' own links (`toShareLink`), those without one left out and the tab shown found again among the rest;
  `parseDesktopLink` reads it back through `parseZayitDeepLink`, tab by tab, with fresh tab ids (`DesktopLink`: the
  name cut to the menu's eight letters, at most `MAX_LINKED_TABS`, the tab shown kept in range).
  `TabsDestination.asItStands(state)` turns a tab into what it shows now from its kept state (the book selected, the
  content's anchor line, else the line selected; a search's last words); `DesktopTabsSnapshot.shareLink(name)` is the
  link of a desktop's snapshot, its windows' tabs in order; `DesktopLink.asWindow(books)` is the window a new desktop
  opens with: the tabs whose books the library has, titled from the catalog.
- **Reached from:** `DesktopLinkRow.kt` (the link copied), `SearchHomeViewModel.kt` (the link pasted).
- **Follows:** Zayit's `ZayitDeepLink.kt`, in its package, for the scheme and each tab's link; the session's models
  (`WindowSnapshot`, `SerializableTabTitle`, `DesktopSession.titleFor`) for the window.
- **Tests:** `DesktopLinkTest`, `DesktopLinkPasteTest`.
- **Notes:** a search's scope and a book's view (the daf, its panes, its commentators) are not in the link, as they
  are not in Zayit's tab links. Hebrew is percent-encoded, six characters a letter: a desktop of ten tabs is some 450
  characters.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/components/DesktopLinkRow.kt`

- **Feature:** Desktop link.
- **Size:** 106 lines.
- **What:** `DesktopLinkRow`, the desktops' menu's last row: the link of this window's desktop, read as the menu opens
  (`DesktopManager.snapshotOpenDesktop(...).shareLink(name)`), copied to the window's clipboard on a click, the menu
  closed once it is copied; not shown for a desktop none of whose tabs has a link. `rememberDesktopOpener`, for
  `TabsContent`: a link's desktop made in this window (`DesktopManager.createDesktop` with its tabs), named as the
  link says unless a desktop here has the name, or the link has none: the next default name then.
- **Reached from:** `DesktopSwitcher.kt` (the row), `TabsContent.kt` (the opener).
- **Follows:** `DesktopSwitcher`'s own "new desktop" row for the row's look (its helper `HoverableRow` and its shape
  are private there, so the row repeats their few lines) and for the next default name; `Clipboard.setText`.
- **Tests:** none of the composables. Seen in the app on 2026-11-11: a desktop copied as its ten tabs that have a
  link (its Home left out), and the link typed into a new tab's bar opened "מרחב ב׳" with the ten tabs, titled, the
  one shown at its line.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/core/deeplink/DesktopLinkTest.kt`

- **Feature:** Desktop link.
- **Size:** 155 lines.
- **What:** eight tests: the link written and read back (the words of a search with `&`, `=` and `/`, Hebrew), tabs
  without a link left out and the tab shown followed, forty tabs at most both ways, what is no desktop's link and a
  careless one, a tab linked to where it is read, a snapshot of two windows, and the window a link opens with (a
  book the library lacks left out, titles, the tab shown).
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/DesktopLinkPasteTest.kt`

- **Feature:** Desktop link.
- **Size:** 75 lines.
- **What:** a desktop's link submitted in the search bar sends `OpenDesktop` with the tabs the library has (a
  catalog of one book), and one whose books it all lacks is searched as any text.
- **Reached from:** the JVM tests.
- **Follows:** `LibraryCategoriesTest` for the catalog, `DesktopManagerTest` for the bar's view model.
- **Tests:** it is one (unit, the view model with mocks).

### The links of the other kinds in the references pane

The references pane ("מראי מקומות") lists every link of a line that is no commentary: beside Zayit's three kinds (עין
משפט, מסורת הש״ס, a reference), a quotation, a Mishnah in the Talmud, a related passage and the links of no kind, under a
tab of their own, "קישורים נוספים", as the user asked on 2026-11-09 (Otzaria lists them). Hooks: `LineReferences.kt`,
`LinePlacesView.kt`, `VerticalBars.kt`, `AppGraph.kt`, `strings.xml`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/references/LibraryLinks.kt`

- **Feature:** More links.
- **Size:** 348 lines.
- **What:** `LibraryLinks`, the library's links of the kinds no pane listed (`KINDS`: QUOTATION, MISHNAH_IN_TALMUD,
  RELATED, OTHER; 1,738,497 links in the library of 2026-10-12). `of(lineIds)` gives the lines they reach as the
  repository's `LineReference`s, in both directions (such a link is stored once, with no direction of its own), a line
  once and the asked lines left out; `any(lineIds)` says whether there is one, from the links' indexes alone. Both read
  the database on a read-only connection opened for the call (`open_mode` 1, by a property and not a `file:` URI, which
  a `#` or a `%` in the folder's name would break), on `Dispatchers.IO`, and give nothing when it can't be read. The
  kind is told first (`connectionTypeId IN (…)`, on `idx_link_type_source_line` and `idx_link_type_target_line`), so
  that a line's commentaries, far more than these links, are never read.
  For the verse links, `linesIn(categories, lineIds)` gives the lines of the books right under some categories that each
  line is linked to, by such a link or a reference (the other line's book told from the link itself, before its line is
  read), `booksOf(categories)` the books right under them, read where it is called, and `lineIdsBefore(line, count)` the
  lines that come before a line in its book. For the note links, `notedBooks()` gives the books that have a book of
  notes on them, each with it (the notes' book named `הערות על` and the book's title, `NOTES_ON`: the 38 books of
  חברותא), and `startsIn(bookId, lineIds, chars)` the lines of a book each line is linked to, with the letters each
  starts with. `connectionsOf(bookId)` says what a book's links say of it (`BookConnections`: commented on by a
  COMMENTARY link, commenting by a link the SOURCE view reads backward), asked once a book, for the books whose flags
  in the library miss their links.
- **Reached from:** `LineReferences.kt` (`loadLinePlaces`, `ReferenceKind.of`), `VerticalBars.kt` (the pane's button),
  through `AppGraph.libraryLinks`; `VerseLinks` (`linesIn`, `booksOf`, `lineIdsBefore`) and `NoteLinks` (`notedBooks`, `startsIn`), given it by
  `TextLinks`, which `main.kt` gives it; `VerticalBars.kt` (`connectionsOf`).
- **Follows:** `CommentaryLinks` (an `@Inject` class reading the library on a JDBC connection of its own, the
  repository having no query); Zayit's `getReferencesForLines` for what a place is (a line once, the asked lines out).
- **Tests:** `LibraryLinksTest`.
- **Notes:** measured on the full library on 2026-11-10: a connection opens in 0.3 to 0.5 ms, `any` answers in 1 to 2 ms,
  a line's links are read in 3 to 15 ms, an amud's (17 lines, 190 places) in 10 ms, and 128 lines' (1,538 places, three
  million letters) in 0.6 s; a heading never selects more than 128 lines (Zayit's limit in `ContentUseCase.selectLine`).
  SeforimLibrary is not changed for it, so the SQL is here and not in `LinkQueries.sq`.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/references/LibraryLinksTest.kt`

- **Feature:** More links.
- **Size:** 196 lines.
- **What:** on a small library made for the test (the four tables the links are read by): a line's links are those
  that start from it and those that reach it, each with its book's title, category and order; the kinds the panes
  already list (a commentary, a reference, עין משפט, מסורת הש״ס) are left to them; a line reached by several links is
  listed once, and the selected lines not at all, lines linked to one another only having nothing to list; no lines, no
  links; and the other kinds group under `ReferenceKind.More`, a tab of its own after the references, a book listed
  once as a reference and once with its other links. For the verse links: the lines of some categories' books a line is
  linked to, either way and once, a commentary's link being none, the books right under some categories, the lines before a line in its book, a book's book of notes with the letters its linked notes start with, and what a book's links say of it (commented
  on, commenting, neither; a targum's link makes a book one that comments).
- **Reached from:** the JVM tests.
- **Follows:** Zayit's `LineReferencesTest` beside it (Kotlin Test, camel-case names); `LevelPrecedenceTest` for a
  SQLite file made in the test.
- **Tests:** it is one (unit).

### Links in the texts: the verses the Talmud quotes, and the Chavruta's notes

A verse quoted in the Bavli or the Yerushalmi is a link to it, as in Zayit's official builds, without Zayit's private
package: from the library's own links (a line of the Talmud linked to a verse) and the verse's words found in the line,
and in the lines after it that quote the verse again. On the library of 2026-10-12: 24,218 verses linked in 17,805
lines of the 76 tractates (22,611 in the lines the library links, 16,943 of them in the Bavli and 5,668 in the
Yerushalmi, and 1,607 quoted again; Zayit's package has 24,817). As the user asked on 2026-11-09, with two pictures of
Zayit on 2026-11-10 that set the rule: a quote alone is a link, not the Talmud's own words that a verse has too, and a
verse quoted again two lines on is one.

The marks of the Chavruta's notes, `(3)` in small letters, are links to their notes too, as Otzaria makes them and the
user asked on 2026-11-09: the library links each line of חברותא to its notes, and a note starts with its number. All
59,040 marks of the 38 books have their note among their line's (measured on the library of 2026-10-12).

Hooks: `addons/quotelinks/disabled/…/InstalledQuoteLinks.kt`, `main.kt`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/quotelinks/TextLinks.kt`

- **Feature:** Links in the texts.
- **Size:** 30 lines.
- **What:** `TextLinks`, Zayit's `QuoteLinksFeature` as Pardes's two kinds of links: `VerseLinks` and `NoteLinks`, made
  when `library` is given, once the app's graph is made. A book is covered when one of them covers it, and its lines
  are decorated by that one; until `library` is given no book is covered, and the reader loads the lines as they are.
- **Reached from:** `addons/quotelinks/disabled/…/InstalledQuoteLinks.kt` (`installedQuoteLinks`), which Zayit's
  `ContentUseCase.buildLinesPager` reads; `main.kt` sets `library`.
- **Follows:** Zayit's `TalmudQuoteLinks` in `addons/quotelinks/enabled` (an object implementing the feature); Pardes's
  `InstalledSemanticSearch` for a community build that plugs its own.
- **Tests:** none of its own; `VerseQuotesTest`, `NoteLinksTest` and `LibraryLinksTest` for what it joins.
- **Notes:** `library` is a global set at start, as the feature itself is one (`installedQuoteLinks`): the reader
  reaches it without the graph.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/quotelinks/VerseLinks.kt`

- **Feature:** Links in the texts.
- **Size:** 63 lines.
- **What:** `VerseLinks(library)`, Zayit's `QuoteLinksFeature` on the library's links, one of `TextLinks`' two. `covers(bookId)`: a tractate of the Bavli
  or the Yerushalmi (the books right under their sedarim, `CatalogPresets`' categories, read once). `decorate`: the
  verses each line of the page, and each of the ten lines before it (`lineIdsBefore`), is linked to
  (`LibraryLinks.linesIn`, the Torah's, the Neviim's and the Ketuvim's books), and each line with its quotes made links
  (`withVerseLinks`): of its own verses, then of the verses of the ten lines before it, the nearest first (the library
  links a verse to the line that quotes it first alone). A line that quotes nothing is the same object.
- **Reached from:** `TextLinks`.
- **Follows:** Zayit's `TalmudQuoteLinks` in `addons/quotelinks/enabled` (an object implementing the feature, a line
  left as it is when nothing is added); Pardes's `InstalledSemanticSearch` for a community build that plugs its own.
- **Tests:** `VerseQuotesTest` and `LibraryLinksTest` for its two halves; none of its own.
- **Notes:** a page of fifty lines costs 1.0 ms to read its verses and 1.3 ms to find and link their quotes (0.6 ms
  for its own verses, 0.7 ms for those of the lines before), measured over the 2,081 pages of the Talmud on the full
  library. Looking two lines back links 1,152 verses more, five 1,486, ten 1,607, twenty 1,670, forty 1,707: ten was
  kept. `linesByIds`, by which Zayit's package checks its verses, is
  not used: the verses are read with the links.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/quotelinks/VerseQuotes.kt`

- **Feature:** Links in the texts.
- **Size:** 269 lines.
- **What:** the finding and the writing of a verse's quote in a line, with no library.
  - `comparedLetters`: a word as it is compared, its letters without nikud and teamim and without ו and י, which a
    quote in the Talmud spells as it pleases (בשכבך for ובשכבך), the Name one word however written (ה׳, יי, אלקים).
  - `wordsOfVerse`: a verse's words, its number, notes and section mark left out; `wordsOfLine`: a line's words, each
    where it stands in its HTML, whether a quote opens before it or closes after it, the words of a link it already has
    left out.
  - `quoteOf`: the longest run of a verse's words in the line that is a quote: two words or more, of five compared
    letters or more; in a line that marks its quotes (״), the words before the quote opens or after it closes are left
    out, and two words alone are a quote only where one opens or closes.
  - `withVerseLinks`: each verse's quotes, the longer first (up to three passes, a word linked once), then a word quoted
    by itself that one verse alone has (״לָכֶם״), then the quotes of the `near` verses, those of the lines before, told by
    three words or more; written as `<a class="ref" href="seforim://line/<id>">`, the library's
    own citation, which the reader already draws and opens. A link crosses no tag and no quote mark.
- **Reached from:** `VerseLinks.decorate`.
- **Follows:** Zayit's `LineReferences.kt` (top-level `internal` functions, constants at the file's end).
- **Tests:** `VerseQuotesTest`.
- **Notes:** measured on the library's 36,224 links between the Talmud's lines and verses: 62% have a run of two words
  or more of the verse in the line, 23% one word alone (linked only when quoted by itself), 12% none (a link to a
  passage, kept on its first verse; a search of the forty verses after it linked 35 lines more, and was left out).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/quotelinks/VerseQuotesTest.kt`

- **Feature:** Links in the texts.
- **Size:** 169 lines.
- **What:** on lines and verses as the library keeps them: a verse's words are its letters alone, its note none of
  them; the Name is one word however written; a verse quoted in a line is a link to it; a quote spelled another way is
  still the verse's; the Name's geresh is inside the link; a line that quotes nothing is the same object, and one word
  in no quote is none; a word quoted by itself is a link when one verse alone has it; each verse has its quotes, the
  longer first; the Talmud's own words are no quote though the verse has them (before a quote opens, two words in no
  quote), while a line that marks no quote has its two words linked; a verse of the lines before is told by three words (the
  passage of Zayit's picture, ברכות ב:), not by two nor by one quoted alone, and the line's own verse comes first; a link crosses no tag and no quote mark; the words
  of a link the line already has are left to it.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's `LineReferencesTest` (Kotlin Test, camel-case names).
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/quotelinks/NoteLinks.kt`

- **Feature:** Links in the texts.
- **Size:** 68 lines.
- **What:** `NoteLinks(library)`: a book that has a book of notes on it is covered (`LibraryLinks.notedBooks`, read
  once), and each of its lines that has a mark (`<small>(`) is given its notes' numbers (`startsIn`, the 48 letters a
  linked note starts with) and its marks made links. `noteNumber(start)`: the number a note starts with, after its
  tags (`<b>(3)</b>`); `withNoteLinks(html, notes)`: each mark `<small>(3)</small>` wrapped in the library's citation
  link to its note, a mark with no note left, the same string when none has one.
- **Reached from:** `TextLinks`.
- **Follows:** `VerseLinks` beside it; the link itself is the library's own (`<a class="ref" href="seforim://line/…">`),
  which the reader draws and opens in a new tab.
- **Tests:** `NoteLinksTest`, `LibraryLinksTest` (`booksWithNotes`, `linkedStarts`).
- **Notes:** the marks are links in the text of חברותא itself. Where חברותא is shown in the commentaries pane, beside
  the Gemara, its marks are not: the pane's texts are loaded by Zayit's paging sources, which have no such seam.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/quotelinks/NoteLinksTest.kt`

- **Feature:** Links in the texts.
- **Size:** 44 lines.
- **What:** a note's number is what it starts with, after its tags, and a line that starts with none has none; a line
  of חברותא with two marks has each made a link to its note; a mark with no note is left, a line with none is the same
  object, and a number in the line's own words is no mark.
- **Reached from:** the JVM tests.
- **Follows:** `VerseQuotesTest` beside it.
- **Tests:** it is one (unit).

### Klei Kodesh's dictionaries in the dictionary card

More dictionaries in the Home's dictionary card and each word's synonyms and spellings, from Klei Kodesh's Kitvei
Hakodesh data, with their permission. Hooks: `DictionaryWidget.kt`, `strings.xml`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/KleiKodeshDictionary.kt`

- **Feature:** Klei Kodesh dictionary.
- **Size:** 236 lines.
- **What:** `KleiKodeshPack`, Klei Kodesh's data read from `kleikodesh/dictionary.tsv.gz` (the index of headwords of four
  books of the library, two text dictionaries, and the links between synonyms and spellings), `DictionarySource`,
  `RelationKind`, `RelatedWord`, `IndexedHeadword`; `indexedEntries` (an index entry kept only when its line in this
  library holds its headword, a heading read up to the next entry), `linesToRead`, `textEntries` (words of no book, with
  ids of their own), and `letters` (letters alone, final forms as the others).
- **Reached from:** `DictionaryWidget.kt`.
- **Follows:** Zayit's `DictionaryWidget` entries (`DictionaryEntry`), which it feeds.
- **Tests:** `KleiKodeshDictionaryTest`, `KleiKodeshDictionaryE2ETest`.

#### `SeforimApp/src/jvmMain/resources/kleikodesh/NOTICE.txt`

- **Feature:** Klei Kodesh dictionary.
- **Size:** 11 lines.
- **What:** the credit and terms of `dictionary.tsv.gz`: packed from Klei Kodesh's KitveiHakodeshDictionary (commit
  `994c4466`) by `scripts/kleikodesh_dictionary.py`; Klei Kodesh's index and links used with their permission; the
  acronyms from Hebrew Wikipedia under CC BY-SA 4.0; the Aramaic-Hebrew dictionary Torat Emet's, with Klei Kodesh's
  permission, credited to both.
- **Reached from:** shipped beside the data; the licences page (`branding/pardes/assetsExtra/common/licenses/`).
- **Follows:** Pardes's own.
- **Tests:** none.

#### `SeforimApp/src/jvmMain/resources/kleikodesh/dictionary.tsv.gz`

- **Feature:** Klei Kodesh dictionary.
- **Size:** binary.
- **What:** Klei Kodesh's data for the card, gzipped TSV (460 KB): the headword index, the text dictionaries and the
  related words, as `scripts/kleikodesh_dictionary.py` packs them (HaMichlol's definitions taken out before release,
  `f405bb7f`).
- **Reached from:** `KleiKodeshPack.shipped`.
- **Follows:** Pardes's own data; terms in `NOTICE.txt`.
- **Tests:** `KleiKodeshDictionaryTest` (the shipped pack has every dictionary and none of the Aruch).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/KleiKodeshDictionaryTest.kt`

- **Feature:** Klei Kodesh dictionary.
- **Size:** 147 lines.
- **What:** a pack read with its books, texts and links; the shipped pack's dictionaries; an entry kept only when its
  line holds its headword; a heading's lines; Machberet Menachem as the library has it; a line without marks; the lines
  to read; text entries' ids; titles as a library may spell them.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).
- **Notes:** camelCase test names, not Zayit's sentences in backticks.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/KleiKodeshDictionaryE2ETest.kt`

- **Feature:** Klei Kodesh dictionary.
- **Size:** 52 lines.
- **What:** over the installed library (`SEFORIMAPP_DATABASE_PATH`, skipped without one), every indexed dictionary is
  read and its entries are on their lines; on the October 2026 library every entry is found.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's `…E2ETest`s on a real library.
- **Tests:** it is one (end to end).

### The dictionary's own window

The dictionary opened from the Tools menu is ready to type in, its field focused, and the reader's context menu opens
it on the selected word ("פתח במילון"), as the user asked on 2026-11-09. Hooks: `DictionaryWidget.kt`,
`BookContentScreen.kt`, `AppGraph.kt`, `strings.xml`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/DictionaryWindow.kt`

- **Feature:** Dictionary window.
- **Size:** 107 lines.
- **What:** `DictionaryToolWindow`, the dictionary's tool window: Zayit's `DefaultToolWindow` under
  `LocalDictionaryWindow` set to true. `Modifier.focusedInItsWindow()` gives the field the focus there, when the window
  opens, each time it is asked for again (`ToolWindows.reopened`) and back from an entry's page; on the Home the local is
  false and the card takes no focus by itself. `DictionaryLookup` (app-scoped) opens that window on a word from outside
  it, or brings it back: the word waits in it until `LookupAskedFor`, in the window alone, hands it to the field.
  `lookupWord` is a selection as the dictionary is asked for it: its words alone (`lettersOnly`), or null when it is
  empty or longer than four words. `lookupAsRead` is Zayit's `lookup`, and for a word no entry begins with, the same
  word without the letters before it in a text (והלל: הלל, דאמר: אמר), at most two and down to three letters, when
  what is left is an entry.
- **Reached from:** `DictionaryWidget.kt` (`ToolWindow`, the field's modifier, `LookupAskedFor`, `lookupAsRead`),
  `BookContentScreen.kt` (`lookupWord`, `DictionaryLookup.open`), `AppGraph.kt`.
- **Follows:** Zayit's `SolarSystemWidget`, whose `ToolWindow` is a window of its own, and his composable modifiers
  (`Modifier.pageSwipe`, `Modifier.trackedDrag`); `DictionaryLookup` is injected as his `ToolWindows` is.
- **Tests:** `DictionaryWindowUiTest`, `DictionaryLookupTest`.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/DictionaryWindowUiTest.kt`

- **Feature:** Dictionary window.
- **Size:** 82 lines.
- **What:** in its own window the dictionary's field takes the focus and the word asked for from outside; on the Home
  its card takes no focus by itself and leaves that word to the window.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's widget UI tests (`WidgetMenuUiTest`: `runDesktopComposeUiTest`, the test graph).
- **Tests:** it is one (Compose UI).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/home/widgets/library/DictionaryLookupTest.kt`

- **Feature:** Dictionary window.
- **Size:** 55 lines.
- **What:** a selected word is asked for as its letters alone; no word, or more than four, is not looked up; a word of
  a text is found without the letters before it, a word of the dictionaries as itself; a lookup opens the dictionary's
  window and brings it back for the next word.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's `DictionaryWidgetTest` beside it (Kotlin Test, backtick names).
- **Tests:** it is one (unit).

### Library location, and an installed Zayit's library used in place

The library can be installed in any folder or drive, moved between drives (at the next start, before anything opens
it), with copies kept; and an installed Zayit's library can be used in place instead of a second download. So can a
library in a folder the user chooses in the onboarding (brought from another computer, on an external drive), as the
user asked on 2026-11-09: in the offline install, under its files' button, and where the install's folder is chosen,
which comes first and needs no free space for it. Hooks: `main.kt`,
`AppSettings.kt`, the onboarding's use cases and screens, `OnboardingBindings.kt`, `DatabaseCleanupUseCase.kt`,
`PendingDbCleanup.kt`, `DataSettingsViewModel.kt`, `DataSettingsScreen.kt`, `strings.xml` (`branding/README.md` lists them).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/database/LibraryLocation.kt`

- **Feature:** Library location.
- **Size:** 530 lines.
- **What:** `LibraryLocation`, where the library is and where it goes: `installDir` (the app's databases folder unless
  the user chose another), `chooseInstallDir` with its `Problem`s, `move` (a rename on one drive, a checked copy across
  drives, the semantic pack's folders carried along, `NAMES`, `LEFTOVERS`), the copies kept (`copies`, `isKeptCopy`,
  `deleteCopy`, switching back), a move killed half way undone or finished, `freeBytes`/`totalBytes`, `currentDir`.
- **Reached from:** `main.kt` (through `PendingLibraryMove`), the onboarding's `DownloadUseCase`, `ExtractUseCase`,
  `AvailableDiskSpaceUseCase`, `InstallLocationRow`, `DatabaseCleanupUseCase.kt`, `DataSettingsViewModel.kt`,
  `LibraryLocationViewModel.kt`, `SearchResultViewModel.kt` (the pack's model), `DafView.kt`, `BrandInfo.kt`.
- **Follows:** efrul/zayit-move, a small Windows tool that moves Zayit's library (`docs/PARDES_PLAN.md` › Library
  location on desktop); Zayit's `DatabasePathProvider` and `database_path` preference.
- **Tests:** `LibraryLocationTest`.
- **Notes:** with some 38 functions in one object it is far past `detekt.yml`'s 15.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/database/PendingLibraryMove.kt`

- **Feature:** Library location.
- **Size:** 44 lines.
- **What:** `PendingLibraryMove`, a move asked for in the settings and run at the next start before anything opens the
  library: a `Request` kept in a marker file of the app's own databases folder (`schedule`, `markStarted`, `read`,
  `clear`).
- **Reached from:** `main.kt`, `LibraryLocationViewModel.kt`, `LibraryMoveWindow.kt`, `PendingSemanticPack.kt`.
- **Follows:** Zayit's `PendingDbCleanup` and `PendingUserDataRestore`, the same pattern of a step left for the next
  start.
- **Tests:** `LibraryLocationTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/database/move/LibraryMoveWindow.kt`

- **Feature:** Library location.
- **Size:** 274 lines.
- **What:** `LibraryMoveWindow`, opened by a start instead of the app when a move is pending: it moves the library off the
  UI thread (`LibraryMover`), shows the progress and the paths, and restarts the app on the result.
- **Reached from:** `main.kt`.
- **Follows:** Zayit's `DatabaseUpdateWindow`, the window a start shows for a library update.
- **Tests:** none of the window; the move is tested in `LibraryLocationTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/diskspace/InstallLocationRow.kt`

- **Feature:** Library location, Existing Zayit library.
- **Size:** 106 lines.
- **What:** `InstallLocationRow`, the folder the library will be installed to, with a link to choose another folder or
  drive (`FileKit.openDirectoryPicker` off the UI thread, `LibraryLocation.chooseInstallDir`) and the problem if it is
  refused. When the folder chosen already holds a library Pardes can open (`ForeignLibraries.inFolder`), two lines say
  so, with its version and a link that uses it in place and calls `onUseLibrary`, instead of the problem. What the
  folder gave is the window's (`InstallLocationChoice`, a `ViewModel` of the window's store), not the row's.
- **Reached from:** `AvailableDiskSpaceScreen.kt`.
- **Follows:** the onboarding screens' components (Jewel `Link`, `InlineErrorBanner`); the link's text is
  `ExistingLibraryScreen`'s button's.
- **Tests:** none of the row; `ForeignLibrariesTest` for what a folder holds. Seen in the app on 2026-11-11.
- **Notes:** an inline banner for the offer, as first written, took the room of the screen's own banner under it, which
  was cut; two plain lines leave it whole. The problem and the offer were the row's own state at first, and were lost
  whenever a frame was drawn while the drive was measured again (the screen shows its spinner alone then, and the row
  comes back new): seen on 2026-11-11, the offer gone right after the folder was chosen.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/library/LibraryLocationCard.kt`

- **Feature:** Library location.
- **Size:** 240 lines.
- **What:** Settings › ניהול נתונים's card: where the library is, moving it, and the copies kept by earlier moves (switch
  back, delete); `isolatedPath` (a path isolated left to right inside a right-to-left line) and `problemText`, shared with
  the other Pardes cards.
- **Reached from:** `DataSettingsScreen.kt`; `isolatedPath` and `problemText` from `InstallLocationRow.kt`,
  `LibraryMoveWindow.kt`, `SemanticPackCard.kt`, `FeedbackSendCard.kt`.
- **Follows:** Zayit's data settings cards.
- **Tests:** none of the card.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/library/LibraryLocationViewModel.kt`

- **Feature:** Library location.
- **Size:** 135 lines.
- **What:** `LibraryLocationViewModel`: the library's place, a move asked for (written as a `PendingLibraryMove`, run at
  the next start), and the kept copies.
- **Reached from:** `LibraryLocationCard.kt`.
- **Follows:** Zayit's settings ViewModels.
- **Tests:** `LibraryLocationTest` (the moves it asks for).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/database/ForeignLibraries.kt`

- **Feature:** Existing Zayit library.
- **Size:** 154 lines.
- **What:** `ForeignLibraries`, another app's library on this machine that Pardes may use in place: `find` (the other
  app named by `BrandLibraries`: its `database_path` preference, then its default folder, when recent enough),
  `useInPlace` (points `database_path` at it, with no copy), and `isForeign` (a folder Pardes uses but must never delete
  from). `inFolder` says what a folder the user chose holds (`InFolder`): a library to use in place, in the folder
  itself or in the library's own folder inside it (`LibraryLocation.destination`), or why Pardes cannot open what is
  there: no `seforim.db`, one cut in pieces, one without its search index (a copy of the file alone), no release
  named, or a release older than the minimum.
- **Reached from:** `LicenceScreen.kt`, `ExistingLibraryScreen.kt`, `LibraryFolderChoice.kt`, `InstallLocationRow.kt`,
  `DatabaseCleanupUseCase.kt`, `PendingDbCleanup.kt`, `DataSettingsViewModel.kt`, `LibraryLocation.kt`,
  `LibraryLocationViewModel.kt`.
- **Follows:** Zayit's `DatabaseVersionManager` for the version check; `branding/README.md` names it the only reader of
  `BrandLibraries`, which `LibraryLocationViewModel` now reads too.
- **Tests:** `ForeignLibrariesTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/existinglibrary/ExistingLibraryScreen.kt`

- **Feature:** Existing Zayit library.
- **Size:** 130 lines.
- **What:** the onboarding step after the licence when another app's library is found: use it in place (straight to the
  user's details) or install a library of Pardes's own; the found library's folder and version shown; a preview.
- **Reached from:** `LicenceScreen.kt`, `OnBoardingDestination.kt`, `OnBoardingNavHost.kt`.
- **Follows:** Zayit's onboarding screens (`OnBoardingScaffold`, `ProgressBarState`, `PreviewContainer`).
- **Tests:** none of the screen.
- **Notes:** two comments in a row say the same about the path's direction, one left over from an earlier version.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/offline/LibraryFolderChoice.kt`

- **Feature:** Existing Zayit library.
- **Size:** 103 lines.
- **What:** `LibraryFolderChoice`, the offline install's other way, in the two lines left under its files' button: a
  question with a link that opens the folder picker (off the UI thread), and under it a note, or why the folder chosen
  is not used (`ForeignLibraries.inFolder`'s answer in the reader's words, the version as the onboarding shows it). A
  library found is used in place (`ForeignLibraries.useInPlace`) and the onboarding goes on to the user's details, the
  files' screen popped, as `ExistingLibraryScreen` does.
- **Reached from:** `OfflineFileSelectionScreen.kt`.
- **Follows:** `ExistingLibraryScreen` (the same use in place and the same way on); `pickDatabaseParts` for the picker
  off the Tao event loop; Jewel's `Link`.
- **Tests:** `ForeignLibrariesTest` (what a folder holds); none of the composable. Seen in the app on 2026-11-11: a
  folder with `seforim.db` alone says so, a whole library leads to the user's details.
- **Notes:** a button under the files' one, as first written, fell below the fixed window's edge (the screen's icon is
  192 dp); the link and its note fit without changing Zayit's layout.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/database/ForeignLibrariesTest.kt`

- **Feature:** Existing Zayit library.
- **Size:** 126 lines.
- **What:** a library used in place is foreign while Pardes's own and other folders are not, and a full install's cleanup
  leaves it alone and empties Pardes's own folder. A folder the user chooses holds a library to use, in it or in the
  library's own folder inside it, and using it records its folder; a folder with no library Pardes can open says why
  (none, the file alone, in pieces, no release, too old).
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, on temporary folders).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/database/LibraryLocationTest.kt`

- **Feature:** Library location.
- **Size:** 188 lines.
- **What:** a chosen folder holds the library in a folder of its own; a move on one drive renames and empties the old
  folder; the semantic pack goes with the library; a kept copy switched back to and deleted; a stopped copy leaves
  nothing; a move that cannot be is refused first; a killed move undone or finished; an install folder holding a library
  refused.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, on temporary folders).

### Pardes's semantic search

Pardes's own search by meaning, on Dicta's open model (neodictabert-bilingual-embed, CC BY 4.0, fine-tuned and distilled
in efrul/pardes-semantic), in place of Zayit's private package: ONNX Runtime for the query, a Lucene HNSW vector index
beside the library, fused with the words' search by RRF (`SEMANTIC_SEARCH.md`). The model and the index come as a pack,
downloaded or imported in the settings and put in place at the next start. Hooks: `InstalledSemanticSearch.kt` (Zayit's
open-core seam), `SemanticSearchEngine.kt`, `SearchResultScreen.kt`, `SearchResultViewModel.kt`, `main.kt`,
`DataSettingsScreen.kt`, `LibraryLocation.kt`, `build.gradle.kts`, `reachability-metadata.json`, `strings.xml`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HybridSearchEngine.kt`

- **Feature:** Semantic search.
- **Size:** 766 lines.
- **What:** `HybridSearchEngine`, a `SearchEngine` that fuses the words' search (Zayit's `LuceneSearchEngine`, through
  `LongQuerySearchEngine`) with the dense search (`IndependentSemanticSearch`): `fusedScores` is Reciprocal Rank Fusion
  with three weights measured in docs/fusion-compare (a lexical hit holding a query of three words or more word for word
  weighs 3; in a search of several books the dense side's lines of 8 words or fewer that the words did not find are left
  out; every line gains for the query's words close together in its best window, `coverage`), on the candidates' lines
  read once from the library (`candidateLines`); then it keeps the first lexical hits at places 2 and 4 (`keepLeaders`),
  recognizes a quote held word for word (`holdsQuote`), applies the earlier level's precedence (`LevelPrecedence`),
  highlights by meaning, counts the meaning's lines in the facets (`MeaningFacets`), and falls back to the words alone
  when the model or the index cannot load. Its session gives views of its list (`SessionViews`: the lines of some
  books, in the list's order, paged from the first with no search), for the category tabs; none when the list goes on
  past what it holds (the words alone, past their candidates). It is a `WordsEngine`: its words' search (`words`) can
  be asked apart, for a list of every line they find in the library's order (`CatalogOrderSession`). A dense side whose walk came back short is searched again from the words'
  first 64 hits (`DENSE_SEEDS`), and a debug line times the two sides, the candidates' lines and their ordering.
- **Reached from:** `InstalledSemanticSearch.kt` (`installedSemanticSearch.hybrid`).
- **Follows:** Zayit's `SemanticSearchFeature` interface and its private `HybridSearchEngine`, of the same name and role
  (not its code, which Pardes has not).
- **Tests:** `HybridSearchEngineDenseTest`, `HybridSearchEngineFallbackTest`, `HybridSearchEngineFusionTest`,
  `HybridSearchEngineKeepLeadersTest`, `LevelPrecedenceTest`, `MeaningFacetsTest`, `DenseTrapTest`.
- **Notes:** its KDoc opens with "Independent Hybrid Search Engine for Pardes Desktop:", a title rather than Zayit's
  plain sentence; with some 17 functions it passes `detekt.yml`'s 15. The fusion's measurements, before and after:
  `docs/fusion-compare/IMPLEMENTATION.md`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/SessionViews.kt`

- **Feature:** Semantic search (result tabs).
- **Size:** 16 lines.
- **What:** `SessionViews`, what a search session offers when its results can be shown in part with no new search:
  `view(bookIds)`, the lines of those books (all of them for null) in the list's order, or null when its list is not
  all it finds.
- **Reached from:** `HybridSearchEngine.kt` (its session), `SearchResultViewModel.kt` (`listed`, `executeFilteredSearch`).
- **Follows:** `MeaningFacets`, the other thing a session of the hybrid engine offers beside Zayit's `SearchSession`
  (SeforimLibrary's interface is not changed).
- **Tests:** `HybridSearchEngineFallbackTest`.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/ResultTabsTest.kt`

- **Feature:** Semantic search (result tabs).
- **Size:** 20 lines.
- **What:** `scopeOfTabs`: the tabs picked are where the text is searched again, one category or several together in
  the tabs' order, each once though a category may be a tab twice, a tab's subcategories picked with it not counted,
  none picked giving nothing.
- **Reached from:** the JVM tests.
- **Follows:** `SearchUiStateTest` beside it (Kotlin Test, backtick names).
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/IndependentSemanticSearch.kt`

- **Feature:** Semantic search.
- **Size:** 472 lines.
- **What:** `IndependentSemanticSearch`, the dense search: the query embedded (`EmbeddingProvider`), the nearest lines in
  the pack's vector index (`pardes.vec.lucene`), in the base books or a set of books, as `DenseHit`s; `bestPassages`, the
  passage of a found line closest in meaning; it opens the pack beside the library and checks that the index and the
  model share their id. A small scope is scored exactly (`exactSearch`); any other is walked on the graph by itself
  (`graphWalk`), without Lucene's exact search of every vector, and a walk that comes back short, fallen among a
  repeated line's identical vectors, starts again at the `seeds` it is given (`SeededKnnVectorQuery`) or tells its
  caller (`onShort`). The last queries' vectors are kept (`searchVector`), and a debug line times the model and each
  field.
- **Reached from:** `HybridSearchEngine.kt`, `InstalledPack.kt`.
- **Follows:** Pardes's own.
- **Tests:** `HybridSearchEngineDenseTest`, `DenseTrapTest`, `PassagesTest`; measured by `ScopeSearchBench`.
- **Notes:** the class has no KDoc. It opens its index with `FSDirectory.open`, where Zayit's own Lucene code uses
  `NIOFSDirectory` in a GraalVM native image ("GraalVM native image does not support MMapDirectory"); to check on the
  native builds.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/EmbeddingProvider.kt`

- **Feature:** Semantic search.
- **Size:** 20 lines.
- **What:** `EmbeddingProvider`, the interface between the model and the search: a query's vector, passages' vectors.
- **Reached from:** `IndependentSemanticSearch.kt`, `OnnxEmbeddingProvider.kt`.
- **Follows:** Pardes's own.
- **Tests:** `HybridSearchEngineDenseTest`, `PassagesTest`, `MeaningFacetsTest` (fakes of it).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/OnnxEmbeddingProvider.kt`

- **Feature:** Semantic search.
- **Size:** 140 lines.
- **What:** `OnnxEmbeddingProvider`, a query embedded with the ONNX model into the int8 vector the index is searched with:
  the model's cleaning, prefix and tokenizer (`HfTokenizer`; `HebrewNormalizer` and `WordPieceTokenizer` for the legacy
  model), ONNX Runtime's session, the sentence embedding's output.
- **Reached from:** `IndependentSemanticSearch.kt`, `VectorIndexBuilder.kt`.
- **Follows:** Pardes's own; ONNX Runtime's Java API.
- **Tests:** `VectorIndexBuilderTest`, `HfTokenizerParityTest` (its tokenizer).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HfTokenizer.kt`

- **Feature:** Semantic search.
- **Size:** 347 lines.
- **What:** `HfTokenizer`, a HuggingFace `tokenizer.json` read and applied in Kotlin, with no native library: the open
  model's normalizers, pre-tokenizers, WordPiece model and post-processor.
- **Reached from:** `OnnxEmbeddingProvider.kt`.
- **Follows:** HuggingFace's `tokenizers` library, matched case for case.
- **Tests:** `HfTokenizerParityTest` (ids equal to the `tokenizers` library's, from `parity.jsonl.gz`).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/WordPieceTokenizer.kt`

- **Feature:** Semantic search.
- **Size:** 91 lines.
- **What:** `WordPieceTokenizer`, a plain WordPiece tokenizer over a vocabulary (NFKC, greedy longest match, `[UNK]`), for
  the legacy model (384 dimensions, `HebrewNormalizer` before it), which the app loads only for a local comparison.
- **Reached from:** `OnnxEmbeddingProvider.kt`.
- **Follows:** Pardes's own.
- **Tests:** none of its own.
- **Notes:** no KDoc.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HebrewNormalizer.kt`

- **Feature:** Semantic search.
- **Size:** 68 lines.
- **What:** `HebrewNormalizer.normalize`, a line's text for the model: HTML tags and entities out, nikud, teamim, geresh
  and gershayim dropped, spaces collapsed.
- **Reached from:** `OnnxEmbeddingProvider.kt`, `OpenModelCleaner.kt`.
- **Follows:** Pardes's own.
- **Tests:** `HfTokenizerParityTest` (through the cleaner).
- **Notes:** no KDoc.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/OpenModelCleaner.kt`

- **Feature:** Semantic search.
- **Size:** 55 lines.
- **What:** `OpenModelCleaner`, the cleaning before the open model's tokenizer: pardes-semantic's `clean.py` in Kotlin, the
  same function; `ID` names it, and the corpus and a query must share it.
- **Reached from:** `OnnxEmbeddingProvider.kt`, `OpenModelSpec.kt`.
- **Follows:** pardes-semantic's `pardes_semantic/clean.py`.
- **Tests:** `HfTokenizerParityTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/OpenModelSpec.kt`

- **Feature:** Semantic search.
- **Size:** 64 lines.
- **What:** `OpenModelSpec`, the model as `pardes-model.json` describes it next to the ONNX file: its id (the weights and
  the cleaner), ONNX and tokenizer files, dimension, output, query and document prefixes, the query's token limit, the
  cleaner, the attribution, and the fusion's dense weight.
- **Reached from:** `IndependentSemanticSearch.kt`, `OnnxEmbeddingProvider.kt`, `InstalledPack.kt`,
  `SemanticPackInstaller.kt`.
- **Follows:** pardes-semantic's `export_onnx.py`, which writes the file.
- **Tests:** `PackFixtures`, `SemanticPackInstallerTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/Passages.kt`

- **Feature:** Semantic search.
- **Size:** 131 lines.
- **What:** `Passages`, a found line cut into the passages a search by meaning highlights one of: its sentences and
  clauses, each a verbatim part of the line, short pieces joined, long ones cut at commas, then into even pieces.
- **Reached from:** `IndependentSemanticSearch.kt`, `HybridSearchEngine.kt`, `OnnxEmbeddingProvider.kt`.
- **Follows:** pardes-semantic's `passages.py`, kept equal to it by a test there.
- **Tests:** `PassagesTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/LevelPrecedence.kt`

- **Feature:** Semantic search (order of results).
- **Size:** 169 lines.
- **What:** `LevelPrecedence.order`: among the fused page's lines that hold the query as a sentence, the earlier level
  first (the Gemara before the Acharonim quoting it), spelling, prefixes and the divine name aside.
- **Reached from:** `HybridSearchEngine.kt`.
- **Follows:** pardes-semantic's `eval-real/level_order.py`, which measured it.
- **Tests:** `LevelPrecedenceTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/BookLevels.kt`

- **Feature:** Semantic search (order of results).
- **Size:** 180 lines.
- **What:** `BookLevels`, a book's level for that precedence (by its authors, the Tanakh, a category named for an era, a
  compilation, Chazal); `LineText`; `LibraryLines`, the levels and lines read from the library's database on a read-only
  connection of its own.
- **Reached from:** `LevelPrecedence.kt`, `HybridSearchEngine.kt`, `CommentaryLinks.kt`.
- **Follows:** Pardes's own.
- **Tests:** `LevelPrecedenceTest`.
- **Notes:** a few Hebrew literals are data (category and era names), as Zayit's own data tables are.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/MeaningFacets.kt`

- **Feature:** Semantic search's category tabs.
- **Size:** 62 lines.
- **What:** `MeaningFacets`, a session whose list holds lines its facets don't count (the lines found by meaning alone);
  `plusBooks` counts them into their books and categories; `otherTabCategories` offers the library's other top
  categories as tabs when the search runs by meaning in the whole library. `WordsEngine`, an engine whose search of
  the words can be asked apart from the lines it adds by meaning (`HybridSearchEngine`), for `CatalogOrderSession`.
- **Reached from:** `HybridSearchEngine.kt`, `SearchResultViewModel.kt`, `CatalogOrderSearch.kt`.
- **Follows:** Zayit's `SearchFacets` and category tabs, which it extends.
- **Tests:** `MeaningFacetsTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/VectorIndexBuilder.kt`

- **Feature:** Semantic search.
- **Size:** 228 lines.
- **What:** `VectorIndexBuilder`, a command-line `main` that builds `pardes.vec.lucene` from pardes-semantic's
  `embed_corpus.py` vectors (`ids.i64`, `vecs.f32` or int8, `meta.txt`), each line with its book and whether it is a base
  book, the model's id committed with the index.
- **Reached from:** the Gradle task `:SeforimApp:buildVectorIndex` (`build.gradle.kts`), and the pack's release.
- **Follows:** the format SeforimLibrary's generator reads for Zayit's own vectors.
- **Tests:** `VectorIndexBuilderTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/pack/SemanticPack.kt`

- **Feature:** Semantic search packs.
- **Size:** 78 lines.
- **What:** `SemanticPack`, a pack's two folders beside the library (`pardes-model/`, `pardes.vec.lucene/`), its repository
  and names; `PackInfo`, what its manifest says beyond the bundle's files.
- **Reached from:** `InstalledPack.kt`, `PendingSemanticPack.kt`, `SemanticPackInstaller.kt`, `SemanticPackCard.kt`,
  `DafDownloads.kt`.
- **Follows:** pardes-semantic's `package_pack.py`, which makes the packs.
- **Tests:** `PackFixtures`, `SemanticPackInstallerTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/pack/InstalledPack.kt`

- **Feature:** Semantic search packs.
- **Size:** 54 lines.
- **What:** `InstalledPack`, the pack beside a library as the settings show it (none, in use, unusable, waiting for the
  next start), checked as `IndependentSemanticSearch` opens it.
- **Reached from:** `SemanticPackViewModel.kt`, `SemanticPackCard.kt`, `SemanticPackPrompt.kt`,
  `SearchResultViewModel.kt` (the model recorded with a grade).
- **Follows:** Pardes's own.
- **Tests:** `SemanticPackInstallerTest`, `SemanticPackCardUiTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/pack/SemanticPackInstaller.kt`

- **Feature:** Semantic search packs.
- **Size:** 513 lines.
- **What:** `SemanticPackInstaller`: a pack downloaded (`download`) or found among files the user downloaded (`resolve`:
  a bundle in parts with its manifest, zips a browser renamed, unzipped folders), unpacked into a staging folder,
  checked part by part and against the library and the model, then scheduled for the next start; `PackProblem`, why not.
- **Reached from:** `SemanticPackViewModel.kt`, `PendingSemanticPack.kt`, `LibraryLocation.kt` (its leftovers).
- **Follows:** Zayit's library install (`BundleManifest`, the parts and their checks) in spirit.
- **Tests:** `SemanticPackInstallerTest`.
- **Notes:** with some 21 functions it passes `detekt.yml`'s 15.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/pack/PendingSemanticPack.kt`

- **Feature:** Semantic search packs.
- **Size:** 165 lines.
- **What:** `PendingSemanticPack.runOnce`: at the next start, before anything opens the search, a staged pack takes the
  old one's place (each old folder renamed aside, the new one moved in, the old deleted), a failed move put back, a start
  stopped half way ended by the next, a failure told once.
- **Reached from:** `main.kt`, `SemanticPackInstaller.kt`, `SemanticPackCard.kt`.
- **Follows:** Zayit's `PendingDbCleanup`, a step left for the next start.
- **Tests:** `PendingSemanticPackTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/pack/SemanticPackReleases.kt`

- **Feature:** Semantic search packs.
- **Size:** 122 lines.
- **What:** `SemanticPackReleases`, the packs released with `semantic-*` tags: the GitHub API's list, or the site's copy
  when the API cannot be reached; the newest pack, and of its builds the installed library's, else the newest library's.
- **Reached from:** `SemanticPackViewModel.kt`, `SemanticPackCard.kt`, `SemanticPackInstaller.kt`,
  `PendingSemanticPack.kt`, `IndependentSemanticSearch.kt`.
- **Follows:** Zayit's `GitHubReleaseFetcher` use for library releases.
- **Tests:** `SemanticPackReleasesTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/semantic/SemanticPackCard.kt`

- **Feature:** Semantic search packs, Feedback mode.
- **Size:** 423 lines.
- **What:** Settings › ניהול נתונים's card for the semantic search: the pack in use or offered, its download or import,
  the work's progress and problems, how to install one with or without internet, and feedback mode's switch with the
  number of results graded; the card comes into view when the settings were opened for it.
- **Reached from:** `DataSettingsScreen.kt` (`SemanticPackCard()`).
- **Follows:** Zayit's data settings cards.
- **Tests:** `SemanticPackCardUiTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/semantic/SemanticPackViewModel.kt`

- **Feature:** Semantic search packs.
- **Size:** 200 lines.
- **What:** `SemanticPackViewModel`: the pack beside the library, the newest release for it, and installing one,
  downloaded or imported, with its progress.
- **Reached from:** `SemanticPackCard.kt`.
- **Follows:** Zayit's settings ViewModels.
- **Tests:** `SemanticPackCardUiTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/semantic/SemanticPackPrompt.kt`

- **Feature:** Semantic search packs.
- **Size:** 64 lines.
- **What:** `SemanticPackPrompt.openAtStart`: a start whose library has no usable pack opens the settings on the semantic
  card, once the session is restored and no update dialog is open, at every start until a pack is in use or the user
  turns it off.
- **Reached from:** `main.kt`.
- **Follows:** Pardes's own.
- **Tests:** `SemanticPackPromptTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/LongQuerySearchEngine.kt`

- **Feature:** Long queries.
- **Size:** 158 lines.
- **What:** `LongQuerySearchEngine`, a `SearchEngine` over Zayit's lexical one that searches a query too large for Lucene
  (each word's dictionary forms and ranking clauses overflow its clause limit) as an exact quote instead of failing; its
  facets count the quote, acronyms and quoted parts kept.
- **Reached from:** `SemanticSearchEngine.kt` (`withSemanticSearch`).
- **Follows:** Zayit's `SearchEngine` interface, as a decorator.
- **Tests:** `LongQuerySearchEngineTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/PreviewHighlights.kt`

- **Feature:** Preview highlights.
- **Size:** 35 lines.
- **What:** `rememberPreviewHighlights`, the ranges the passage preview highlights in a line: in the found line the
  search's own highlights (the passage closest in meaning, or the query's words), keyed by the line so that the next
  result's lines never show the last one's ranges.
- **Reached from:** `SearchResultScreen.kt` (`PassagePreview`).
- **Follows:** Zayit's inline `produceState` it replaced, with the line as a key.
- **Tests:** `PreviewHighlightsUiTest`.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HybridSearchEngineDenseTest.kt`

- **Feature:** Semantic search.
- **Size:** 183 lines.
- **What:** the dense search over a vector index of its own (in a few books, their closest lines, a heading repeated 40
  times among them), and the fused list as Zayit's (with lines by meaning the fused one, without any the words' list).
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, a small Lucene index).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/DenseTrapTest.kt`

- **Feature:** Semantic search.
- **Size:** 119 lines.
- **What:** the dense search over a vector index with a trap, a line repeated 40 times that is the closest to the query:
  the walk comes back with 33 lines and says so; started again where the words' hits are, it finds three in four of the
  closest at least; in a large scope with no seeds every vector of the scope is scored.
- **Reached from:** the JVM tests.
- **Follows:** `HybridSearchEngineDenseTest` beside it (a small Lucene index, an `EmbeddingProvider` of its own).
- **Tests:** it is one (unit, a small Lucene index).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/ScopeSearchBench.kt`

- **Feature:** Semantic search.
- **Size:** 152 lines.
- **What:** a `main` that measures where a search in a scope spends its time with the semantic pack, stage by stage (the
  query's vector, the dense side, the words' candidates, the facets, the first page as the app runs it, the result
  tree), for the whole library, two large categories, a tractate's commentators and one book, on the library
  `SEFORIMAPP_DATABASE_PATH` names.
- **Reached from:** run by hand.
- **Follows:** Zayit's `SearchBench` and Pardes's `CategorySearchBench`, which measure the words' search.
- **Tests:** a bench.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HybridSearchEngineFallbackTest.kt`

- **Feature:** Semantic search.
- **Size:** 198 lines.
- **What:** when the dense side cannot load (a model failing, an `Error` too), the search goes on with its words alone;
  past the fused candidates the lexical results go on, each once, with their total; a view shows the lines of some
  books of the same list, all of it for no books named, with no new search, none before the list is made, none of a
  list that goes on past what it holds.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HybridSearchEngineKeepLeadersTest.kt`

- **Feature:** Semantic search (order of results).
- **Size:** 110 lines.
- **What:** `keepLeaders` (the first lexical hits at places 2 and 4, nothing lost or repeated) and the quote test (a quote
  held word for word apart from niqqud, teamim, markup and punctuation, or from memory; scattered words and one word are
  none).
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HybridSearchEngineFusionTest.kt`

- **Feature:** Semantic search (order of results).
- **Size:** 79 lines.
- **What:** `fusedScores` and the window (seven tests: a query of three words held word for word weighs 3, one of two
  words does not; the dense side's short lines left out of a search of several books, kept in one book and when the
  words find them too; RRF alone without the library's lines; the dense side's order when the words find nothing; the
  window's share, with prefixes or without).
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test, as `HybridSearchEngineKeepLeadersTest`.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/LevelPrecedenceTest.kt`

- **Feature:** Semantic search (order of results).
- **Size:** 250 lines.
- **What:** the earlier level's precedence (thirteen tests: the Gemara before the Acharonim, holders' places, a verse
  holding two words, sentences held in order and spelling, skeletons and the divine name, books' levels, the library's
  levels read from the database).
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, a small SQLite database).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/MeaningFacetsTest.kt`

- **Feature:** Semantic search's category tabs.
- **Size:** 194 lines.
- **What:** a book's lines found by meaning count for it, its categories and the total; the other top categories in the
  library's order; a hybrid session counting only the meaning's own lines.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/PassagesTest.kt`

- **Feature:** Semantic search.
- **Size:** 165 lines.
- **What:** a line cut into sentences and clauses, each verbatim, covering the line in order; the passage closest in
  meaning picked; a passage embedded once; a very long line's passages; a model that cannot embed passages.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HfTokenizerParityTest.kt`

- **Feature:** Semantic search.
- **Size:** 63 lines.
- **What:** the Kotlin cleaner and tokenizer against the Python ones, on the cases pardes-semantic's
  `make_parity_fixtures` writes (`parity.jsonl.gz`, `tokenizer.json.gz`): the cleaning, and the query and document ids.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, fixtures).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/VectorIndexBuilderTest.kt`

- **Feature:** Semantic search.
- **Size:** 111 lines.
- **What:** float32 and int8 vectors give the same index, the base books apart and missing lines skipped.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/pack/PackFixtures.kt`

- **Feature:** Semantic search packs.
- **Size:** 185 lines.
- **What:** `PackFixtures`, small packs as `package_pack.py` and `semantic-release.yml` make them (bundles in parts, zips,
  manifests, a model description), and a library to put them beside.
- **Reached from:** `SemanticPackInstallerTest`, `PendingSemanticPackTest`, `SemanticPackReleasesTest`.
- **Follows:** Zayit's test helpers.
- **Tests:** a helper of the tests.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/pack/SemanticPackInstallerTest.kt`

- **Feature:** Semantic search packs.
- **Size:** 222 lines.
- **What:** fourteen tests: a bundle in parts unpacked and checked, any part finding the rest, damaged or missing parts,
  renamed zips found by what they hold, unzipped folders copied, a pack of another library release staged, an index of
  another model or a stray file refused, staging scheduled, the installed pack read as the search opens it.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, on temporary folders).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/pack/PendingSemanticPackTest.kt`

- **Feature:** Semantic search packs.
- **Size:** 153 lines.
- **What:** the staged pack taking the old one's place, a first pack, a failed move put back, a fresh pack whose second
  folder cannot go in, a start stopped half way, another release of the library, a failure told once.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, on temporary folders).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/pack/SemanticPackReleasesTest.kt`

- **Feature:** Semantic search packs.
- **Size:** 97 lines.
- **What:** the newest published pack chosen whatever its library release, the installed library's build preferred, the
  API's list read and the site's when the API cannot be, a library release the same in a tag and in `release_info`.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/semantic/SemanticPackCardUiTest.kt`

- **Feature:** Semantic search packs, Feedback mode.
- **Size:** 138 lines.
- **What:** the card says how to install a pack while none is in use (only the zips without a connection), offers nothing
  once one is ready or in use, turns feedback mode on and counts the graded results, and comes into view when opened for.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's Compose UI tests.
- **Tests:** it is one (Compose UI).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/semantic/SemanticPackPromptTest.kt`

- **Feature:** Semantic search packs.
- **Size:** 48 lines.
- **What:** a library without a pack, or with one the search cannot use, opens the card; no library, a pack in use or one
  waiting does not; the card takes the focus once.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/search/LongQuerySearchEngineTest.kt`

- **Feature:** Long queries.
- **Size:** 184 lines.
- **What:** a query too large for Lucene searched as a quote, its facets, in the base books only; a query Lucene runs passed
  on, a short one never probed; a failure after a page not hidden; acronyms and quoted parts kept; an empty query.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/PreviewHighlightsUiTest.kt`

- **Feature:** Preview highlights.
- **Size:** 128 lines.
- **What:** by meaning, a line drawn where the found line was shows none of its passage; by words, a line drawn where
  another was highlights its own words; a line no longer the found one loses its passage.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's Compose UI tests.
- **Tests:** it is one (Compose UI).

#### `SeforimApp/src/jvmTest/resources/semantic/parity.jsonl.gz`

- **Feature:** Semantic search.
- **Size:** binary.
- **What:** the parity cases (490 KB gzipped): texts, their cleaning by `clean.py` and their ids by the `tokenizers`
  library, written by pardes-semantic's `make_parity_fixtures`.
- **Reached from:** `HfTokenizerParityTest`.
- **Follows:** Pardes's own data.
- **Tests:** a fixture.

#### `SeforimApp/src/jvmTest/resources/semantic/tokenizer.json.gz`

- **Feature:** Semantic search.
- **Size:** binary.
- **What:** the open model's `tokenizer.json` (940 KB gzipped), Dicta's, CC BY 4.0, for the parity test.
- **Reached from:** `HfTokenizerParityTest`.
- **Follows:** Dicta's model files.
- **Tests:** a fixture.

### Books and authors found by name

The bar finds books and authors by name as people type them (spellings, typos, word order, acronyms), from an in-memory
index of the catalog, after Klei Kodesh's Kitvei Hakodesh; a book typed by its title has its author right after it.
Hooks: `LuceneLookupSearchService.kt`, `AppCoreBindings.kt`, `SearchHomeViewModel.kt`, `AuthorNames.kt`, `HomeView.kt`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/titles/BookTitleIndex.kt`

- **Feature:** Book and author titles search.
- **Size:** 477 lines.
- **What:** `BookTitleIndex`, the library's books and authors found by name, in memory: built once from the catalog, a
  query costs a few lookups in a sorted word list; titles found as typed, begun, in another order, with or without the
  article, by acronym, with other spellings and typos (an edit distance that counts a swap as one), with their author;
  authors by name or another name; a book's authors (`authorsOf`). `TitleBook` and `TitleAuthor` are what it reads.
  When a query finds nothing, typos forgiven too, every word may be the start of a word (`matchTokens(begun = true)`:
  `נביא שמואל` finds שמואל א of the נביאים). The base books of a category the query names whole, its own and those of
  the categories right under it (`ofNamedCategory`, `NAMED_CATEGORY_DEPTH`), rank after the titles typed whole or begun
  and before the titles that only hold the name, in the library's order: `תנך` lists בראשית before אור הישר (תנך), as
  the user asked on 2026-11-10.
- **Reached from:** `LuceneLookupSearchService.kt` (`titleIndex`), `BookTitleIndexLoader.kt`, `AppCoreBindings.kt`.
- **Follows:** Klei Kodesh's Kitvei Hakodesh search, in spirit; Zayit's lookup index stays behind it for what it misses.
- **Tests:** `BookTitleIndexTest`, `TitleSearchE2ETest`, `TitleSearchBench`.
- **Notes:** with some 20 functions it passes `detekt.yml`'s 15.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/titles/BookTitleIndexLoader.kt`

- **Feature:** Book and author titles search.
- **Size:** 127 lines.
- **What:** `BookTitleIndexLoader.load`, the index of a library from its catalog (`catalog.pb`, already in memory) with the
  categories above each book and the authors from the database; `BookTitleIndexHolder`, built once on first use or ahead
  of it (`warmUp`), null without a catalog (`onError` told why).
- **Reached from:** `AppCoreBindings.kt` (`provideLuceneLookupSearchService`).
- **Follows:** Zayit's catalog (`CatalogCache`) and repository.
- **Tests:** `TitleSearchE2ETest` (through `TestLookup.appLookup`).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/titles/TitleText.kt`

- **Feature:** Book and author titles search.
- **Size:** 161 lines.
- **What:** `TitleText`, how titles, category names, author names and a query are read alike: nikud and quote marks
  (`שו"ע`, `ש''ע`) aside, final letters, the article, honorifics, words split.
- **Reached from:** `BookTitleIndex.kt`, `TitleAbbreviations.kt`.
- **Follows:** Pardes's own.
- **Tests:** `BookTitleIndexTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/titles/TitleAbbreviations.kt`

- **Feature:** Book and author titles search.
- **Size:** 293 lines.
- **What:** `TitleAbbreviations`, a table of acronyms and short names of books and authors as people type them, each with
  its readings (ordered lists of words that must all be found).
- **Reached from:** `TitleText.kt`.
- **Follows:** Pardes's own data.
- **Tests:** `BookTitleIndexTest`, `TitleSearchE2ETest`.
- **Notes:** almost all of it is Hebrew data literals, as Zayit's own data tables are (`LimudCycles.kt`).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/search/titles/BookTitleIndexTest.kt`

- **Feature:** Book and author titles search.
- **Size:** 159 lines.
- **What:** a title found as typed, begun, in another order, with or without the article; acronyms with or without
  quotes; spellings and typos; a book with its author; one word not read twice; the base books of a category named
  whole before the titles that hold its name, a wide category's own and those right under it alone; every word as the
  start of a word when nothing is found; authors by any name; a book's authors; the books' authors after those found
  by name; the edit distance's swap.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/search/titles/TitleSearchCases.kt`

- **Feature:** Book and author titles search.
- **Size:** 194 lines.
- **What:** `TitleSearchCases`, searches for books and authors as people type them with what each should find, measured
  on the library of October 2026: the regression list of the E2E test and the bench.
- **Reached from:** `TitleSearchE2ETest`, `TitleSearchBench`.
- **Follows:** Pardes's own data.
- **Tests:** a helper of the tests.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/search/titles/TitleSearchE2ETest.kt`

- **Feature:** Book and author titles search.
- **Size:** 49 lines.
- **What:** the cases over the installed library, through the lookup service as the bar and the book tree call it:
  books, authors, and a book's title offering its author; skipped without a library.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's `…E2ETest`s on a real library.
- **Tests:** it is one (end to end).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/AuthorBooks.kt`

- **Feature:** Book and author titles search.
- **Size:** 20 lines.
- **What:** `withTheirBooks`: the authors found by name, each with the book he wrote among those the query names
  (`ofBook`), so that `catBookRows` puts his row beside the book's and the row says whose book it is (בן איש חי: יוסף
  חיים); an author whose name is the query itself gets none (רמבם).
- **Reached from:** `SearchHomeViewModel.kt` (the bar's author suggestions).
- **Follows:** `AuthorNames.withAuthorsOfBooks` beside it, which it feeds.
- **Tests:** `AuthorBooksTest`, `CatBookRowsTest`.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/AuthorBooksTest.kt`

- **Feature:** Book and author titles search.
- **Size:** 27 lines.
- **What:** an author found by name keeps the first book typed that he wrote; one named as typed (רמבם, רש"י) is offered
  for his name alone.
- **Reached from:** the JVM tests.
- **Follows:** `AuthorNamesTest` beside it (Kotlin Test, backtick names).
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/search/titles/TitleSearchBench.kt`

- **Feature:** Book and author titles search.
- **Size:** 142 lines.
- **What:** the cases run as the app runs them, the lookup index's suggestions against the title index's, with each query's
  time; writes its report to `TITLE_BENCH_OUT`, runs only when asked.
- **Reached from:** run by hand (a JVM test class gated by environment variables).
- **Follows:** Pardes's own bench.
- **Tests:** a bench.
- **Notes:** camelCase test name.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/search/titles/TestLookup.kt`

- **Feature:** Book and author titles search.
- **Size:** 16 lines.
- **What:** `appLookup(dbPath)`, the lookup service of a library as the app builds it: its lookup index, with the title
  index first.
- **Reached from:** `BookSuggestionsE2ETest`, `TitleSearchE2ETest`, `TitleSearchBench`.
- **Follows:** `AppCoreBindings.provideLuceneLookupSearchService`, which it repeats for the tests.
- **Tests:** a helper of the tests.

### Pardes's dictionary copy (lexicon)

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/PardesLexicon.kt`

- **Feature:** Lexicon.
- **Size:** 318 lines.
- **What:** `PardesLexicon`, Pardes's copy of the library's word dictionary (`lexical.db`), which the regular search reads in
  its place: the same words and forms without the 4,711 bases no surface points to (2,479 of them spelled like a form of
  another base, which left such words with no forms), each base's surfaces numbered by how many lines hold them, so the
  32 forms the search keeps are the common ones, and, from version 2, the defective spellings of its forms
  (`defectiveForms`: an inner ו or י left out) that some line holds and the dictionary lacks, as forms of the same base.
  Built in the background when the library changes (`VERSION` in the fingerprint rebuilds an older copy), until then the
  search reads `lexical.db`; `awaitBuild` for the search's warm-up.
- **Reached from:** `AppCoreBindings.kt` (`dictionaryFor`), `main.kt` (`awaitBuild`).
- **Follows:** SeforimLibrary's `lexical.db` and `MagicDictionaryIndex`, which read the copy as they read the original;
  `defectiveForms` follows the deletions of Otzaria's `generate_spelling_variations` (otzaria_search_engine, MIT), as its
  KDoc says.
- **Tests:** `PardesLexiconTest`.
- **Notes:** measured on eval-100 and eval-real (`docs/LEXICON.md`): the source in the first ten 13 → 16, a good line in the
  first three 42 → 47 of 153, some 10 ms more a search. The defective spellings (`docs/fusion-compare/SPELLING.md`):
  59,034 forms, the file 55 → 59 MB, built in some 4 s, small gains on four test sets; a spelling the dictionary already
  has is not joined to another base, which hurt. It opens the text index with `FSDirectory.open`, not Zayit's
  `NIOFSDirectory` in a native image. Android's copy is still at version 1 (`androidApp/docs/PARITY.md`).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/search/PardesLexiconTest.kt`

- **Feature:** Lexicon.
- **Size:** 148 lines.
- **What:** the empty base dropped and the commonest forms first; the copy rebuilt when the library changes; the
  defective-spelling rule (`defectiveForms`) and the spellings the build adds (a new form numbered by its lines, a word
  typed as the lines write it given its base's forms, a spelling the dictionary has left with its own base).
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, small dictionaries and indexes).
- **Notes:** camelCase test names.

### Search in categories, and in several books and categories

A text searched in a whole category, in its base books alone (the texts without their commentaries), or in several books
and categories picked with Tab. Zayit dropped the category scope on purpose; see section 1 › Search and history. Hooks:
`SearchScope.kt`, `SessionModels.kt`, `HistoryStore.kt`, `SearchHomeViewModel.kt`, `SearchResultViewModel.kt`,
`SearchResultScreen.kt`, `HomeView.kt`, `TabsContent.kt`, `AppCoreBindings.kt`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/LibraryCategories.kt`

- **Feature:** Search in categories.
- **Size:** 232 lines.
- **What:** `LibraryCategories`, the library's categories for the search: each with its path (what the bar suggests), the
  books it searches (all those under it, or its base books alone), whether it holds both kinds, and its chip (`hit`). It holds the commentaries on a book by their time too
  (`CommentaryGroups`, read with the categories from the same links): `named(query, limit)` offers them before the
  categories a text names, and `hit` and `bookIds` know them by their ids, so that one is searched, kept and restored as
  a category is. `bookOrder()` gives every book its place in the library's order (a category's own books, then its
  subcategories'), read once, for a list of results in that order. `books()` gives every book by its id as the
  catalog has it (`CatalogBook.toBook`), for the results' tree, and `warmUp()` reads the categories and the books
  ahead of their first use. `bookPlace(bookId)` gives a book's title and its categories' titles, and
  `commentariesOn(bookId)` the commentaries the library links to it, with their titles, for the reader's default
  commentators (`CommentatorDefaults`).
- **Reached from:** `AppCoreBindings.kt`, `SearchHomeViewModel.kt`, `SearchResultViewModel.kt`, `AppGraph.kt` and
  `main.kt` (the warm-up), `CommentatorDefaults.kt`.
- **Follows:** Zayit's catalog (`CatalogCache`) and `CategoryNavigationUseCase`, whose `collectBookIdsUnderCategory` it
  replaces in the results.
- **Tests:** `LibraryCategoriesTest`, `CategorySuggestionsE2ETest`, `CategoryScopeSearchTest`, `CatalogOrderSessionTest`,
  `CommentatorDefaultsTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/CommentaryGroups.kt`

- **Feature:** Search in categories (commentaries on a book).
- **Size:** 140 lines.
- **What:** `CommentaryGroups`: the commentaries on one book that stand under one category named for their time
  (גאונים, ראשונים, אחרונים, מחברי זמננו, מפרשים): "ראשונים על בבא בתרא" is the 18 books under תלמוד › בבלי › ראשונים that
  comment on בבא בתרא, which the library keeps each in its commentator's category.
  - `of(roots, links)`: the groups of the catalog. A commentary is on the book it has the most links to and on any
    other that has a tenth of its links (the רי״ף on בבא מציעא is not on בבא בתרא, which a few of its lines are linked
    to); the group's category must be in the root category of the book commented on, and a group has two books at
    least. 608 groups on the library of 2026-10-12.
  - `match(query, groups, limit)`: the groups a text names, a word of it beginning the time's name and the others the
    book's title (`ראשונים על בבא בתרא`, `מפרשי השולחן ערוך אורח חיים`); the book named in full first. The book alone or
    the time alone names none.
  - `idOf(categoryId, bookId)`: the id a group is searched under, ten million at least, where a category's is a few
    thousand: it is kept wherever a category's id is (`SearchScope.Category`, the tab's state, the history).
- **Reached from:** `LibraryCategories.kt`.
- **Follows:** `CategorySuggestions` (an object, `match` with a limit, `searchKey` words, `על` skipped) and `BaseBooks`
  (a walk of the catalog with the library's commentary links).
- **Tests:** `CommentaryGroupsTest`; `LibraryCategories.named` was run on the full library on 2026-11-10 (`ראשונים על
  בבא בתרא`: 18 books, `אחרונים על ברכות`: 19, `ראשונים על בראשית`: 27, `מפרשים על הלכות שבת`: 68; ten milliseconds a text).
- **Notes:** a group is no category of the library: code that asks the repository for a category by this id gets none
  (the history's label falls back to the group's own, `SearchResultViewModel.scopeLabel`).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/CommentaryGroupsTest.kt`

- **Feature:** Search in categories (commentaries on a book).
- **Size:** 118 lines.
- **What:** on a small catalog (two tractates, their ראשונים each in a category of his own, an אחרון, the Mishnah and
  its commentators): a group is the commentaries on a book under a category named for a time, without the רי״ף on
  another tractate, with its label and id, none of one book, the Mishnah's commentators on the Mishnah's tractate alone;
  and a text names a group by its time and its book, the book named in full first, in any order and by the start of a
  word, the book alone or the time alone naming none.
- **Reached from:** the JVM tests.
- **Follows:** `CategorySuggestionsTest` beside it.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/CategorySuggestions.kt`

- **Feature:** Search in categories.
- **Size:** 111 lines.
- **What:** `CategoryHit` (a category the bar can search in, its path, whole or its base books alone) and
  `CategorySuggestions.match`, the categories a text names (`תלמוד`, `רש"י על התורה`, `ש"ס` for the Bavli), a category with
  both kinds of books offered in its base books alone right after it.
- **Reached from:** `SearchHomeViewModel.kt`, `HomeView.kt`, `LibraryCategories.kt`.
- **Follows:** Zayit's book suggestions in the bar.
- **Tests:** `CategorySuggestionsTest`, `CatBookRowsTest`, `CategorySuggestionsE2ETest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/BaseBooks.kt`

- **Feature:** Search in categories (base books).
- **Size:** 112 lines.
- **What:** `BaseBooks`, the books a category is searched in "ספרי יסוד בלבד": the library's curated base-book flag where it
  has one (386 books), else the books told apart by the commentaries on them (`COMMENTARY_PAIRS_SQL`); a category with
  too few commentaries on its own books is not offered so.
- **Reached from:** `LibraryCategories.kt`, `CommentaryLinks.kt`.
- **Follows:** SeforimLibrary's `isBaseBook`, extended to categories without it.
- **Tests:** `BaseBooksTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/CommentaryLinks.kt`

- **Feature:** Search in categories (base books).
- **Size:** 61 lines.
- **What:** `CommentaryLinks`, the library's commentaries and the books they comment on, read from its database on a
  read-only connection of its own (the repository has no such query): `read()` the pairs, `counted()` each pair with
  its links, which say how much of a commentary is on each book (`CommentaryGroups`).
- **Reached from:** `LibraryCategories.kt`.
- **Follows:** `BookLevels.kt`'s `LibraryLines`, the same kind of connection.
- **Tests:** `CategorySuggestionsE2ETest`; `counted()` was run on the full library (12,897 pairs in 1.1 s).

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/ScopeItems.kt`

- **Feature:** Search in several books and categories.
- **Size:** 68 lines.
- **What:** `ScopeItem` (a book, or a category with its subcategories) and `ScopeItems`: picked once each, in order,
  removed by book or category, one alone searched alone and more together (`scopeOf`), a category picked again taking the
  new choice, a chip's label.
- **Reached from:** `SearchHomeViewModel.kt`, `HomeView.kt`.
- **Follows:** Pardes's own.
- **Tests:** `ScopeItemsTest`.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/LibraryCategoriesTest.kt`

- **Feature:** Search in categories.
- **Size:** 119 lines.
- **What:** a category's books are its subcategories' too, all or its base books alone; the categories in the library's
  order with their paths, those with both kinds marked; every book's place in the library's order, a category's own
  books before its subcategories'; the library's books known by id as the catalog has them.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/ResultsTreeLibraryBooksTest.kt`

- **Feature:** Search speed.
- **Size:** 55 lines.
- **What:** the results' tree takes its books from the library's own (`BuildSearchTreeUseCase`'s `libraryBooks`), none
  read from the database, a category's path too; a book that list lacks is read as before.
- **Reached from:** the JVM tests.
- **Follows:** `LibraryCategoriesTest` beside it (MockK, Kotlin Test with `runTest`).
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/CategorySuggestionsTest.kt`

- **Feature:** Search in categories.
- **Size:** 70 lines.
- **What:** a category typed by its title first; a commentator on a part of the Tanakh as its category under the
  commentator; `ש"ס` for the Bavli; a category's base books offered right after it; the words naming the category.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/BaseBooksTest.kt`

- **Feature:** Search in categories (base books).
- **Size:** 106 lines.
- **What:** a category without curated base books told apart by the commentaries on its books; a commentary on another
  category's book a base book here; curated base books below a category of commentaries left out; without the links only
  the curated ones; too few commentaries and no base-books offer; a commentary's title.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/ScopeItemsTest.kt`

- **Feature:** Search in several books and categories.
- **Size:** 60 lines.
- **What:** items added once in order, removed by book or category, one or more searched, a category picked again, a
  chip naming a category with its parent.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/domain/CategoryScopeSearchTest.kt`

- **Feature:** Search in categories (base books).
- **Size:** 105 lines.
- **What:** over the dev database (skipped without it), a category searched in its base books alone finds in its texts
  only, not in their commentaries.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's search integration tests (`SearchIntegrationTestBase`).
- **Tests:** it is one (end to end).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/CatBookRowsTest.kt`

- **Feature:** Search in categories.
- **Size:** 214 lines.
- **What:** the bar's rows: a category named as typed before the books whose titles begin so and before those an acronym
  names so, a book's own title before it; acronym books first with no category named; a title being typed first; the
  authors of the book typed, and those named as it, right after it; an author named as typed before the categories and
  acronyms when no book is titled so; the base books a word begins right after the text search, when five or fewer; in
  a book, a category before the other books to add (`catBookRows`, `tocAddRows`).
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit, `HomeView.kt`'s row order).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/panels/bookcontent/views/CategorySuggestionsE2ETest.kt`

- **Feature:** Search in categories.
- **Size:** 133 lines.
- **What:** over the installed library (skipped without it): the category typed first, then its base books alone; a
  category's base books are its texts; a category without curated base books searched without the commentaries on its
  books; the מהר״ל's books alone find no line of the notes on them.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's `…E2ETest`s on a real library.
- **Tests:** it is one (end to end).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/CategorySearchBench.kt`

- **Feature:** Search in categories.
- **Size:** 282 lines.
- **What:** a `main` that measures where a search scoped to a category spends its time against a search of the whole
  library (facets, then a session and its first page; the filters alone; a field of category ancestors), on the library
  `SEFORIMAPP_DATABASE_PATH` names.
- **Reached from:** run by hand.
- **Follows:** SeforimLibrary's engine and its filter caching (`StableFiltersCachingPolicy`), mimicked.
- **Tests:** a bench.

#### `SeforimApp/src/jvmTest/resources/search/common-sources-e100.json.gz`

- **Feature:** Common sources line (planned).
- **Size:** binary.
- **What:** the eval-100 questions' results and the common sources expected for them (215 KB gzipped), for a parity test
  of the common sources line above the results (`docs/COMMON_SOURCES_PLAN.md`), added with the semantic search
  (`20e2fa81`).
- **Reached from:** nothing yet: no test reads it, and `CommonSources` is not written.
- **Follows:** Pardes's own data.
- **Tests:** a fixture of a test still to write.

### Feedback: grades of search results, and sending them

Feedback mode puts grade buttons under each search result, to train the semantic search; the grades, the searches made
and the daf's line review are sent to Pardes's own service (`feedback-service/`, a Cloudflare Worker), from the settings
or by themselves once the user agrees. Since 2026-11-11, as the user asked, the question is a step of the onboarding
(a user from before it is still asked at start), and it offers two things more: the searches the user made in Zayit
and in Otzaria on the same computer, read from those apps' own files only once the user agreed, and the publication
of what is sent on the service's open site (`publish`). Everything is checked at first, the user's decision of that
day, and what is unchecked is neither read nor sent. Hooks: `SearchResultScreen.kt`, `SearchResultViewModel.kt`,
`TabsContent.kt`, `main.kt`, `AppGraph.kt`, `AppCoreBindings.kt`, `AppSettings.kt`, `DataSettingsScreen.kt`,
`build.gradle.kts` (`FEEDBACK_URL`), `strings.xml`, and the onboarding's `OnBoardingDestination.kt`,
`OnBoardingNavHost.kt` and `RegionConfigScreen.kt`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/feedback/SearchFeedbackStore.kt`

- **Feature:** Feedback mode.
- **Size:** 112 lines.
- **What:** `FeedbackGrade` (exactly this, relevant, not relevant, on the evaluations' scale), `SearchFeedback` (a result
  graded: its search keyed as the history keys it, line, book, place, scope, model, time) and `SearchFeedbackStore`, kept in
  the user database: save, clear, a search's grades, counts, and what was graded since a time.
- **Reached from:** `AppCoreBindings.kt`, `SearchResultViewModel.kt`, `FeedbackPackage.kt` (`FeedbackSource`).
- **Follows:** Zayit's `HistoryStore` (the user database, the history's search key).
- **Tests:** `SearchFeedbackStoreTest`.

#### `SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/SearchFeedback.sq`

- **Feature:** Feedback mode.
- **Size:** 76 lines.
- **What:** the table `search_feedback` (one grade per search key and line) with an index on its time, and its queries
  `upsertGrade`, `deleteGrade`, `gradesForSearch`, `countGrades`, `deleteAll`, `gradesSince` and `searchesSince`. And
  the table `other_app_feedback` (made at the next start, `CREATE TABLE IF NOT EXISTS`): a row of another app, by a
  hash of its key, with when Pardes first found it and whether it was sent (`otherAppRows`, `otherAppFound`,
  `otherAppSent`, `otherAppGone`).
- **Reached from:** `SearchFeedbackStore.kt`, `OtherAppLedger.kt` (SQLDelight's `UserSettingsDb`).
- **Follows:** Zayit's user-DB `.sq` files.
- **Tests:** `SearchFeedbackStoreTest`, `OtherAppSearchesTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/SearchFeedbackButtons.kt`

- **Feature:** Feedback mode.
- **Size:** 84 lines.
- **What:** `SearchFeedbackButtons`, the three grade chips under a result; the grade given is marked, and a second click
  takes it back.
- **Reached from:** `SearchResultScreen.kt` (`ResultView`).
- **Follows:** the results' own links and chips (Jewel).
- **Tests:** `SearchFeedbackButtonsUiTest`.
- **Notes:** its KDoc starts with "Pardes:".

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackPackage.kt`

- **Feature:** Feedback sending.
- **Size:** 419 lines.
- **What:** what a user sends, one JSON document: `FeedbackPackage` (grades, searches, the daf's marks, the sender's
  random id), `GradeRow`, `SearchRow`, `FeedbackContents`, `FeedbackChoice` (the kinds kept, the queries left out),
  `FeedbackSent` (each kind's last time); `feedbackPackage`, `toJson`, `parts` (under the service's `PART_BYTES`), queries
  longer than `MAX_QUERY_CHARS` left out, and `FeedbackSource`, which reads what is new from the user database and the daf
  reviews. For the other apps: `OtherApp` (Zayit, Otzaria, as a row's `source` names them), the rows' fields a row of
  theirs carries (`source`, `scope`, `ref`, `timeUnknown`; a grade's `bookTitle`, `heRef`, `docId`), `OtherAppRows`,
  `FeedbackContents.others`, `FeedbackChoice.others` and `publish` (the package's `publish`, left out unless true), and
  `FeedbackSource.read(sent, others, everything)`, which reads the apps the user sends: Zayit's searches since the
  last package, Otzaria's by the ledger. A package of Pardes's own rows alone is what it was, byte for byte.
- **Reached from:** `FeedbackSender.kt`, `FeedbackSendViewModel.kt`, `FeedbackAutoSender.kt`, `FeedbackConsentDialog.kt`,
  `FeedbackSendCard.kt`, `OtherAppSearches.kt`, `OtherAppLedger.kt`, `FeedbackStepScreen.kt`.
- **Follows:** the service's package format (`feedback-service/src/package.js`) and pardes-semantic's `collect.py`.
- **Tests:** `FeedbackPackageTest`, `OtherAppSearchesTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackService.kt`

- **Feature:** Feedback sending.
- **Size:** 61 lines.
- **What:** `FeedbackService`, the client of Pardes's service at the build's `FEEDBACK_URL` (none without it): send a
  package's parts, delete what a sender sent; `FeedbackServiceProblem`, a refusal with its status and reason.
- **Reached from:** `AppCoreBindings.kt`, `FeedbackSender.kt`, `FeedbackAutoSender.kt`.
- **Follows:** Zayit's Ktor client (`KtorConfig`).
- **Tests:** `FeedbackServiceTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackSender.kt`

- **Feature:** Feedback sending.
- **Size:** 250 lines.
- **What:** `FeedbackSender`, the sending shared by the card and the automatic sending: what there is since the last
  package, the kinds the user sends (in the settings), the package sent to the service or saved to a file, each kind
  marked sent up to its newest time. The other apps the user sends and `publish` are kept with the kinds
  (`AppSettings.isFeedbackKindSent`, by `OtherApp.source` and `PUBLISH`), as is whether the user was asked of them
  (`ASKED_OF_OTHERS`); `otherApps()` looks for the apps without reading them; Zayit's searches are marked sent up to
  their newest time, Otzaria's in the ledger. The readers are `internal var`s the tests replace, as
  `FeedbackAutoSender.connections` is.
- **Reached from:** `FeedbackSendViewModel.kt`, `FeedbackAutoSender.kt`.
- **Follows:** Pardes's own.
- **Tests:** `FeedbackSendViewModelTest`, `FeedbackAutoSenderTest`, `OtherAppSearchesTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackAutoSender.kt`

- **Feature:** Feedback sending.
- **Size:** 175 lines.
- **What:** `FeedbackAutoSender`: once, at a start of a build with the service, the user is asked whether to send feedback
  and which kinds (`askVisible`, the dialog); while they agree, what is new is sent at each start and each new network
  connection (`NetworkWatch`, the interfaces read every period); a build without the service neither asks nor sends.
  `isQuestionOpen` lets the onboarding ask the question first. `choice()` offers the other apps found here and
  publishing checked until the user answered of them; a user who agreed before they were offered is asked once more,
  when one of the apps is on the computer (`askOfOthers`), and one who said no is not.
- **Reached from:** `main.kt` (`run`), `AppGraph.kt`, `FeedbackConsentDialog.kt`, `FeedbackSendCard.kt`,
  `FeedbackStepScreen.kt`, `RegionConfigScreen.kt`.
- **Follows:** Pardes's own.
- **Tests:** `FeedbackAutoSenderTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackConsentDialog.kt`

- **Feature:** Feedback sending.
- **Size:** 212 lines.
- **What:** `FeedbackConsentDialog`, the question at start, once: send feedback by itself, which kinds (all checked at
  first), and the grade buttons under the results for whom has them off; closed without an answer, asked again next start.
  Under the kinds, a row for each other app found on the computer and one for publishing ("פרסום במאגר הפתוח"), each
  with what it means; the kinds scroll (Jewel's `VerticallyScrollableContainer`) over the buttons, which stay. Another
  app's searches alone are something to agree to. `OtherApp.label` and `.hint` are the card's too.
- **Reached from:** `main.kt`.
- **Follows:** Zayit's decorated dialogs (`JewelDecoratedDialog` in a `NucleusApplicationScope`).
- **Tests:** `FeedbackConsentUiTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackSendCard.kt`

- **Feature:** Feedback sending.
- **Size:** 444 lines.
- **What:** Settings › ניהול נתונים's card: each kind of feedback with how much of it is new, the queries to look over and
  leave out, send now or save to a file, the automatic sending, deleting from the service what this sender sent, and what is
  never sent with the sender's id to copy or renew. A row for each other app found on the computer: checked, it says
  how many of its searches (and grades) there are, which are read only then; and the publication's checkbox, by the
  automatic sending's.
- **Reached from:** `DataSettingsScreen.kt` (`FeedbackSendCard()`).
- **Follows:** Zayit's data settings cards.
- **Tests:** `FeedbackSendCardUiTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackSendViewModel.kt`

- **Feature:** Feedback sending.
- **Size:** 232 lines.
- **What:** `FeedbackSendViewModel`: what there is to send, what the user keeps of it (the kinds, kept in the settings;
  the queries left out), and the sending or saving with its result. `State.otherApps` (found off the UI thread),
  `setOtherApp` (kept, and what there is read again) and `setPublish`.
- **Reached from:** `FeedbackSendCard.kt`.
- **Follows:** Zayit's settings ViewModels.
- **Tests:** `FeedbackSendViewModelTest`, `FeedbackSendCardUiTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/OtherAppSearches.kt`

- **Feature:** Feedback from other apps.
- **Size:** 352 lines.
- **What:** the searches made in other apps on this computer, read from the apps' own files, never written to.
  `ZayitSearches`: its user database's `visit_history` (the table Pardes has from it), a row a search with its scope,
  count and time; the copies `BrandLibraries.otherAppUserDatabases` names, a search in two of them once; a Zayit from
  before a column reads without it; an author's scope from the row's key. `OtzariaSearches`: its history
  (`user_state.db`, table `lists`, the row `history`, a JSON list whose `isSearch` entries are searches, with
  Otzaria's title and facets) and its smart search's events (`search_feedback/*.jsonl`: a search a session, a vote
  only with the search of its session, the last vote on a result standing, a debug build's file skipped), each row
  with the key the ledger keeps it by (`Keyed`); `otzariaDataRoots()`, where Otzaria keeps its data on each system.
  `found()` only looks for the files.
- **Reached from:** `FeedbackPackage.kt` (`FeedbackSource`), `FeedbackSender.kt`.
- **Follows:** efrul/semantic-query-telemetry's readers (`SeforimAppReader.cs`, `OtzariaReader.cs`), whose facts and
  wording of a scope these keep, so that pardes-semantic's `collect.py` reads both alike; `LibraryLinks.readOnly` for
  a database opened read only by a property; Otzaria's `AppPaths` for its data folder (a fact, no code of it).
- **Tests:** `OtherAppSearchesTest`.
- **Notes:** a portable Otzaria, which keeps its data beside itself, is not looked for; the telemetry tool takes a
  folder for it.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/OtherAppLedger.kt`

- **Feature:** Feedback from other apps.
- **Size:** 54 lines.
- **What:** `OtherAppLedger`, which of another app's rows were found and sent, for an app that keeps no time for a
  search (Otzaria's history): `found` gives each key its entry, a new one found now, and forgets the rows the app no
  longer holds (a search back in the history was made again); `sent` marks them. A key is kept as its SHA-256.
- **Reached from:** `FeedbackPackage.kt` (`FeedbackSource`).
- **Follows:** `SearchFeedbackStore` (the user database's queries).
- **Tests:** `OtherAppSearchesTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/feedback/FeedbackStepScreen.kt`

- **Feature:** Feedback from other apps.
- **Size:** 190 lines.
- **What:** `FeedbackStepScreen`, the onboarding's step before its end, in a build that has the service and where the
  question was not answered: whether to send the search data by itself, from which apps (this one, and Zayit and
  Otzaria where they are), the grade buttons, and "פרסום במאגר הפתוח"; laid out as the steps beside it (an
  information banner, the choices, a large picture), everything checked at first. `feedbackStepAnswer` turns the
  boxes into `FeedbackAutoSender`'s answer: sending from no app is a no, the buttons count only with this app's
  grades.
- **Reached from:** `OnBoardingNavHost.kt`.
- **Follows:** `UserProfileScreen` and `RegionConfigScreen` for the layout (`OnBoardingScaffold`,
  `InlineInformationBanner`, an icon filling half the row); `FeedbackConsentDialog` for what is asked.
- **Tests:** `FeedbackStepTest` (the answer). Seen in the app on 2026-11-11.
- **Notes:** first written as a plain list of checkboxes, none checked; the user asked on 2026-11-11 for the app's
  design ("שיהיה בסגנון העיצוב של פרדס") and for everything checked at first ("ברירת מחדל הכל יהיה מסומן").

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/core/feedback/SearchFeedbackStoreTest.kt`

- **Feature:** Feedback mode.
- **Size:** 46 lines.
- **What:** a search's grades come back by its key, each line's last; the same query in another scope is another search;
  a grade taken back is gone.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's store tests (an in-memory `UserSettingsDb`).
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/SearchFeedbackButtonsUiTest.kt`

- **Feature:** Feedback mode.
- **Size:** 47 lines.
- **What:** each button gives its grade, and the grade given is taken back by a second click.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's Compose UI tests.
- **Tests:** it is one (Compose UI).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackPackageTest.kt`

- **Feature:** Feedback sending.
- **Size:** 236 lines.
- **What:** the source reads what is newer than each kind's last package (only searches of the history); a query longer
  than the service takes left out, a pasted passage not; a package not read again; the choice's kinds and left-out
  queries; a daf mark sent with what is wrong with its line and of which of its words; a big package in parts under
  the limit, in order; what a package and a grade say.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackServiceTest.kt`

- **Feature:** Feedback sending.
- **Size:** 58 lines.
- **What:** a package sent and its id, a sender's packages deleted, a refusal and why, no network as an `IOException`, a
  build without the address having no service.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test, over `ServiceDouble` (Ktor's `MockEngine`).
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackSendViewModelTest.kt`

- **Feature:** Feedback sending.
- **Size:** 138 lines.
- **What:** a package holds what the user kept and the next one only what came after; sent in the kinds kept, which the
  automatic sending keeps too; without a network nothing marked sent; nothing kept, nothing saved; the sender's id kept.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackAutoSenderTest.kt`

- **Feature:** Feedback sending.
- **Size:** 219 lines.
- **What:** asked once and sent when agreed, again at each connection; a no sends nothing and is not asked again; closed
  without an answer, asked again; no network at start, sent at the next connection; a build without the service. Whom
  agreed before another app's searches were offered is asked once more where the app is, and whom said no is not; the
  other apps and publishing are offered checked, and what the user unchecks stays so.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackConsentUiTest.kt`

- **Feature:** Feedback sending.
- **Size:** 119 lines.
- **What:** the answer carries the kinds checked and the buttons, agreeing needs a kind; the buttons not offered to whom
  has them; the buttons can be left off; another app's searches are offered where the app is, and with publishing go
  into the answer when checked.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's Compose UI tests.
- **Tests:** it is one (Compose UI).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackSendCardUiTest.kt`

- **Feature:** Feedback sending.
- **Size:** 146 lines.
- **What:** a query left out leaves the counts and saving waits for something kept; with the service, sending now, the
  automatic sending and deleting what was sent once the user says yes; nothing new says when the last package was.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's Compose UI tests.
- **Tests:** it is one (Compose UI).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackTestDoubles.kt`

- **Feature:** Feedback sending.
- **Size:** 133 lines.
- **What:** `ServiceDouble`, the feedback service as the Worker answers, in memory over Ktor's `MockEngine` (what it took,
  its moods), and helpers: an in-memory feedback database, a grade, a search, `eventually`; a Zayit user database
  with searches (`zayitDatabase`), and `withOtherApps`, which makes a sender look for the other apps where the test
  says rather than on the machine it runs on.
- **Reached from:** the feedback tests.
- **Follows:** the Worker's answers (`feedback-service/src/index.js`).
- **Tests:** a helper of the tests.

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/OtherAppSearchesTest.kt`

- **Feature:** Feedback from other apps.
- **Size:** 394 lines.
- **What:** eleven tests on temporary databases and files: Zayit's searches with their scope, count and time, two
  copies, an old table, no Zayit; Otzaria's history and its smart search's searches and votes, no Otzaria; an app
  read only when the user sends it; Otzaria's searches sent once each, at the time first found, and again when made
  again; a history that cannot be read forgetting nothing; the package's rows saying their app, and `publish`; the
  sender sending an app's searches once chosen, and not twice.
- **Reached from:** the JVM tests.
- **Follows:** `FeedbackPackageTest`.
- **Tests:** it is one (unit).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/feedback/FeedbackStepTest.kt`

- **Feature:** Feedback from other apps.
- **Size:** 41 lines.
- **What:** the step's answer: nothing checked or no app is a no; this app is its three kinds, with the buttons as
  checked; the apps checked are sent from, this one or not, and published only when checked.
- **Reached from:** the JVM tests.
- **Follows:** Kotlin Test.
- **Tests:** it is one (unit).

### The open smart siddur (experimental)

Zayit's smart siddur is its private package, which Pardes may not use. Pardes's own is built on an open engine,
SeforimOpenSiddur by Ariel Daniely (AGPL v3 with the §7b term), which he offered in efrul/Pardes#27: a copy of it at a
pinned commit is the `:siddur-open` module (`siddur-open/`, section 3), and its screen is a third variant of Zayit's
siddur addon, `SeforimApp/addons/siddur/open`, beside `enabled` (the private package's) and `disabled` (none).
`branding/brand.gradle` compiles it for Pardes where Zayit's script compiles `disabled`. The engine reads the tefilot
from the library's own siddurim, and its author says its coverage is partial, so the siddur is experimental: the app
shows none until the user turns it on in Settings › כללי. The hooks in Zayit's files are `OpenSiddurCard()` in
`GeneralSettingsScreen.kt`, the two preferences in `AppSettings.kt`, `include(":siddur-open")` in
`settings.gradle.kts`, three resource globs in `reachability-metadata.json` and a block of `strings.xml`. Zayit's
code that shows the siddur (`ToolsMenuButton`, `LuachWidgets`, `AppNativeMenuBar`, `TabsContent`) is unchanged: it
reads `installedSiddur`, as it does for its own.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/siddur/OpenSiddurSwitch.kt`

- **Feature:** the open smart siddur.
- **Size:** 20 lines.
- **What:** the object `OpenSiddurSwitch` with `shown`, a Compose snapshot state that mirrors the `open_siddur`
  preference and that only `AppSettings` writes (at its start, at a change, at a reset). The open addon's
  `installedSiddur` is null while it is false.
- **Reached from:** `AppSettings.kt` (which writes it), `addons/siddur/open`'s `InstalledSiddur.kt` and
  `OpenSiddurCard.kt` (which read it).
- **Follows:** Zayit's `SiddurFeature.kt` beside it, whose `installedSiddur` it serves. A global object is a
  departure from `CLAUDE.md`'s "use the Metro graph instead of global singletons": `installedSiddur` is a top-level
  value that Zayit's code reads in composition and in `main.kt`, with no graph at hand, and a snapshot state makes
  the menus and the widget that show the siddur follow the switch at once.
- **Tests:** `OpenSiddurSwitchTest`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/siddur/OpenSiddurCard.kt`

- **Feature:** the open smart siddur.
- **Size:** 26 lines.
- **What:** `OpenSiddurCard()`: Zayit's `SettingCard` with the title "סידור חכם (ניסיוני)", the description that says
  what the siddur is, that it is experimental and whose work it is built on, and the checkbox that calls
  `AppSettings.setOpenSiddurEnabled`. Nothing in a build without the open siddur (`Brand.OPEN_SIDDUR` false).
- **Reached from:** `GeneralSettingsScreen.kt` (`GeneralSettingsView`).
- **Follows:** the cards of Zayit's general settings (`SettingCard`); a card of its own beside the screen, as Pardes's
  `LibraryLocationCard` and `SemanticPackCard`.
- **Tests:** `OpenSiddurSwitchTest` (the preference and what it switches); the card itself was checked in the app.

#### `SeforimApp/addons/siddur/open/kotlin/io/github/kdroidfilter/seforimapp/features/siddur/InstalledSiddur.kt`

- **Feature:** the open smart siddur.
- **Size:** 30 lines.
- **What:** the addon's `installedSiddur`: a property whose getter gives the open siddur's `SiddurFeature` while
  `OpenSiddurSwitch.shown`, and null otherwise. The feature's `TabContent` is `SiddurTabContent`, as in Zayit's
  `enabled` variant; its `runE2e` does nothing (Zayit's siddur scenario walks the private package's templates).
- **Reached from:** `branding/brand.gradle`, which adds `addons/siddur/open/kotlin` to `jvmMain` and leaves
  `addons/siddur/disabled` out; then everything of Zayit's that reads `installedSiddur`.
- **Follows:** Zayit's `addons/siddur/enabled/…/InstalledSiddur.kt`, of which it is the third variant.
- **Tests:** `OpenSiddurSwitchTest`.
- **Notes:** a siddur tab left open when the switch is turned off is drawn empty until it is closed, as a siddur tab
  restored in a build without a siddur.

#### `SeforimApp/addons/siddur/open/kotlin/io/github/kdroidfilter/seforimapp/features/siddur/SiddurScreen.kt`

- **Feature:** the open smart siddur.
- **Size:** 1654 lines.
- **What:** the siddur's screen for the open engine: Zayit's screen (`addons/siddur/enabled/…/SiddurScreen.kt`, 1,449
  lines at 0.6.12) as Ariel Daniely adapted it in his fork of the app (arieldaniely/Zayit, AGPL v3 with the §7b term,
  `SeforimApp/src/jvmMain/…/features/siddur/SiddurScreen.kt` at `b9adccdc`, 2026-11-11). Against Zayit's screen (174
  lines out, 379 in):
  - the day's book comes from `buildDayBook(civil, prefs, options, showAll, source, afterHalachicMidnight)`, with the
    library's siddurim as its `source` (`LibrarySiddurSource`); a nusach whose siddur the library lacks shows the
    "not available" line instead of falling back to Ashkenaz;
  - the readings (Torah, haftarah, megillah) are the engine's `SiddurReading`s, drawn by `ReadingBlock` with the
    verses the source finds, each aliyah under its name, or "הקטע אינו זמין במאגר הספרים";
  - Selichot is a part of its own, opened at night in its season; Mincha of the eves of Rosh Hashana and Yom Kippur
    has its own title; the lulav's instructions, the molad's announcement and the notes of מעין שלוש are lines of the
    engine's, given their texts from `strings.xml`;
  - what is not said today, shown on request, is dimmed inside a line too (`SKIP_START`/`SKIP_END`), and turning
    "show all" on or off keeps the line being read in place (`retainedSiddurPosition`);
  - on the State of Israel's days in Iyar the day bar offers the customs (`IsraeliPrayerCustom`, kept by
    `AppSettings`): ordinary weekdays until one is picked (`israeliPrayerCustomOf`), then the engine's four. Under a
    custom that keeps the days, the bar and the tefilot's titles name the day; the titles go by the engine's flags
    of the days (`ISRAELI_DAY_FLAGS`), which a section lacks under the ordinary weekday;
  - the arrows and PageUp/PageDown scroll the text from wherever the focus is in it, as in a book
    (`rememberKeyScroll`): Zayit's own change to its screen after 0.6.12 (see the notes);
  - links in the text open through `rememberRefLinkUriHandler`; the slots the engine fills itself (the readings, the
    molad, the omer's day, Pirkei Avot) are gone from `SiddurSlot`, and so are the private package's
    `SiddurTemplates`, `readingOf`, `megilaOf`, `pirkeiAvos` and the E2E recorders.
- **Reached from:** `InstalledSiddur.kt` (`SiddurTabContent`).
- **Follows:** Zayit's `SiddurScreen.kt`, whose code and comments it keeps wherever the engine does not differ.
- **Tests:** `SiddurScrollTest`, `LibrarySiddurSourceTest`, `OpenSiddurSwitchTest` (the default custom); in the app, a
  weekday, Rosh Chodesh, Shabbat, a day of Chanukah with its Torah reading, and 5 Iyar as an ordinary weekday and
  under a custom were looked at on the full library (2026-11-11).
- **Notes:**
  - Two things differ from the author's file. His imports the days' names from the `luach` module, which he
    changed; here they are the addon's (`IsraeliOccasionNames.kt`), so Zayit's `luach` stays as it is. And his
    always names Yom HaZikaron, Yom HaAtzmaut and Yom Yerushalayim and leaves Tachanun out on the last two, under
    each of the engine's four customs. The user asked on 2026-11-11 for an ordinary weekday as the default: the
    engine's copy has a fifth custom for it (`siddur-open/README.md`), which this screen lists first, falls back to,
    and under which it names no day.
  - The keyboard scrolling is Zayit's `21659516` ("fix(siddur): scroll with the arrow keys and PageUp/PageDown",
    2026-11-01, 44 lines), which the author's file holds. It is after 0.6.12, the one piece of Zayit's later code in
    Pardes: first left out, as Pardes stays on 0.6.12, then taken at the user's word on 2026-11-11. The commit is
    still AGPL v3 with the §7b term: it is an ancestor of the licence change (`1b9f1f21`), nine hours before it.
  - The file was then formatted by ktlint, which his fork does not run on it.
  - It is a second copy of a 1,449-line screen of Zayit's: a change Zayit makes to its own must be made here by hand.

#### `SeforimApp/addons/siddur/open/kotlin/io/github/kdroidfilter/seforimapp/features/siddur/LibrarySiddurSource.kt`

- **Feature:** the open smart siddur.
- **Size:** 66 lines.
- **What:** `LibrarySiddurSource`, the engine's `SiddurSource` over the library: `book(title)` gives a book's lines
  with its table of contents as headings by line index, read once and kept; `passage(passage)` gives the verses of a
  reading between two addresses, from a map of the book's verses by chapter and verse read from each line's reference
  (its Hebrew numerals), or null when the library lacks the book or an end of the passage.
- **Reached from:** `SiddurScreen.kt` (`SiddurTabContent`, `ReadingBlock`).
- **Follows:** Ariel Daniely's file of the same name in his fork, unchanged but for ktlint's formatting; it reads the
  library through `SeforimRepository`, as Zayit's screen did for its readings (`passagesText`).
- **Tests:** `LibrarySiddurSourceTest`.

#### `SeforimApp/addons/siddur/open/kotlin/io/github/kdroidfilter/seforimapp/features/siddur/IsraeliOccasionNames.kt`

- **Feature:** the open smart siddur.
- **Size:** 20 lines.
- **What:** `IsraeliOccasion.localizedName()`: the names of Yom HaZikaron, Yom HaAtzmaut and Yom Yerushalayim, from
  `strings.xml`.
- **Reached from:** `SiddurScreen.kt` (the day bar, the tefilot's titles).
- **Follows:** the author's `luach/…/IsraeliOccasionNames.kt`, kept inside the addon so that Zayit's `luach` module
  and its strings are not changed.
- **Tests:** none of its own.

#### `SeforimApp/addons/siddur/open-test/kotlin/io/github/kdroidfilter/seforimapp/features/siddur/OpenSiddurSwitchTest.kt`

- **Feature:** the open smart siddur.
- **Size:** 57 lines.
- **What:** three tests: no siddur until the switch is turned on, and none once it is turned off; the switch kept
  for the next start, exported with the portable preferences and cleared by a reset; Iyar's days ordinary weekdays
  until a custom is picked, the custom kept by its name, and an unknown one read as the ordinary weekday.
- **Reached from:** the JVM tests (`branding/brand.gradle` adds `addons/siddur/open-test/kotlin` to `jvmTest`).
- **Follows:** Zayit's `addons/siddur/test` (the tests of the addon beside it); Kotlin Test, sentences in backticks,
  settings over `PropertiesSettings` as `TestAppSettings.kt`.
- **Tests:** it is one (unit).

#### `SeforimApp/addons/siddur/open-test/kotlin/io/github/kdroidfilter/seforimapp/features/siddur/SiddurScrollTest.kt`

- **Feature:** the open smart siddur.
- **Size:** 23 lines.
- **What:** one test of `retainedSiddurPosition`: turning the omitted text on or off keeps the same line at the same
  offset, and a line that goes away gives the next one that stays.
- **Reached from:** the JVM tests.
- **Follows:** Ariel Daniely's test of the same name in his fork, unchanged.
- **Tests:** it is one (unit).
- **Notes:** its test is named in camelCase, where Zayit's tests are sentences in backticks.

#### `SeforimApp/addons/siddur/open-test/kotlin/io/github/kdroidfilter/seforimapp/features/siddur/LibrarySiddurSourceTest.kt`

- **Feature:** the open smart siddur.
- **Size:** 137 lines.
- **What:** four tests on a real library (`SEFORIM_DB=/path/to/seforim.db`; skipped without it): the Selichot of the
  season and of the fasts in the three nusachim; the eves of festivals without Tachanun and Mincha of Erev Yom Kippur
  from its machzor; a haftarah and the readings of several days found verse by verse; and the core tefilot of a
  weekday, a Friday night, Rosh Hashana and Shabbat Rosh Chodesh.
- **Reached from:** the JVM tests.
- **Follows:** Ariel Daniely's test of the same name in his fork, unchanged but for ktlint's formatting.
- **Tests:** it is one (on a library). All four pass on the library release Pardes installs (2026-11-11).
- **Notes:** CI has no library, so it does not run there; its tests are named in camelCase.

### Branding

#### `buildSrc/src/main/kotlin/io/github/kdroidfilter/buildsrc/BrandResources.kt`

- **Feature:** Branding.
- **Size:** 113 lines.
- **What:** the Gradle task `BrandResources`: a resource folder with a brand's files laid over it: the base copied, every
  file of the overlay put in place of the base file at the same path, a brand's `values/strings.xml` replacing only the
  strings it names, every extra file added at its path; it fails when the base no longer has a file or a string the brand
  replaces, and when an extra file is already in the base (upstream added one there).
- **Reached from:** `branding/brand.gradle`.
- **Follows:** Zayit's `buildSrc` (its `Versioning` object beside it); a task class as Gradle's API has it.
- **Tests:** the build (every Pardes build runs it).

### The delta's lines in the full-text index (a fix of Zayit's code)

A library delta's upserted lines go into the full-text index as SeforimLibrary's own index writer writes the release's
lines, where Zayit's sink wrote a reduced document that the release's index refuses. The hook in Zayit's files is in
`DbDeltaUpdateService.kt`, with the n-gram analyzer's dependency in `SeforimApp/build.gradle.kts`.

#### `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/update/LuceneLineDocument.kt`

- **Feature:** Incremental library updates (a fix of Zayit's code).
- **Size:** 152 lines.
- **What:** `LuceneLineDocument`: `analyzer()`, BuildLuceneIndex's per-field analyzer (StandardAnalyzer, and on
  text_ng4 StandardTokenizer, LowerCaseFilter and 4-grams); `Book` (title, category, its ancestors, order, base book);
  `create(fields, line, book)`, the line's document with LuceneTextIndexWriter.addLine's fields (type, book_id with
  its doc values, category_id, book_title, ancestor_category_ids, line_id, line_index, order_index with base books
  brought forward, is_base_book, text and text_ng4 of the normalized text), each given only the points, doc values
  and indexing the index already has for it; and `normalize`, BuildLuceneIndex's normalizePostHtmlForIndex in one
  `when` (the same characters dropped, spaced and replaced, checked on 200,000 random strings).
- **Reached from:** `DbDeltaUpdateService.kt` (`defaultLuceneSinksProvider`).
- **Follows:** SeforimLibrary's `generator/searchindex` `LuceneTextIndexWriter.addLine` and `BuildLuceneIndex`
  (analyzer, normalization, base books' order), which wrote the index; Pardes for Android's `delta/LuceneDeltaSinks.kt`
  made the same fix first.
- **Tests:** `DbDeltaLuceneSinkTest`.
- **Notes:** the normalization is written as a `when` over a set, not copied line for line, to keep within
  `detekt.yml` (the copy was past its complexity and jump limits).

#### `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/update/DbDeltaLuceneSinkTest.kt`

- **Feature:** Incremental library updates (a fix of Zayit's code).
- **Size:** 152 lines.
- **What:** one test: an index with one line written as LuceneTextIndexWriter.addLine writes the release's, a small
  database of a book and its category ancestors, and a delta's line through `defaultLuceneSinksProvider`; the line
  must then be found by its words without nikud or HTML, inside a word (text_ng4), under the category above its own,
  by its line_index, with a base book's order brought forward. On Zayit's code it fails: Lucene refuses the line.
- **Reached from:** the JVM tests.
- **Follows:** Zayit's update tests (Kotlin Test, sentences in backticks); a temporary folder and a JDBC database as
  Pardes's `VectorIndexBuilderTest` makes them.
- **Tests:** it is one (unit).

## 3. Pardes's own trees

Every other file Pardes adds (758 files): Pardes for Android (`androidApp/`), branding, documents,
workflows, skills, the feedback service and the rest, a row each: what the file is, and where it comes from
(Pardes's own, a port or a copy of a named Zayit or Pardes for desktop file, or a third party's). A note follows
the description where something needs saying.

### Pardes's trees beside Zayit's

#### The repository's root

| File | What it is | Where it comes from |
|---|---|---|
| `README.md` | The repository's front page, in Hebrew with one English line: what Pardes is (based on Zayit by Elie (Elyahou) Gambache and not on his behalf), the downloads, what the app has, installing on Android with the three ways to bring the library, where the code is, and licence and credits (the §7b line, Dicta's model). *Note:* The English line says desktop is Windows and Linux; macOS is built since desktop 1.0.2, as the Hebrew text says. Its list of Pardes's additions lacks feedback-service/, nucleus-fork/, scripts/kleikodesh_dictionary.py and the daf layouts' release. | Pardes's own; takes the place of Zayit's README.MD, which Pardes renamed README-zayit-desktop.md (README_HE.md likewise) |
| `SEMANTIC_SEARCH.md` | Hebrew guide to Pardes for desktop's own semantic search on the app side: Dicta's open model fine-tuned on the library's links, ONNX Runtime and a Lucene HNSW vector index fused with BM25 by RRF, with the fusion's three weights (word for word, short lines out, the window); the files beside the library, the code of features/search/semantic/ file by file, installing packs, meaning highlights, warm-up, three correctness checks, known limits, the switch to the legacy model for comparison, and licensing. *Note:* Written in Hebrew, while Zayit's developer docs (TAB_SYSTEM_README.md, LINE_SELECTION_README.md, AGENTS.md) are in English. Zayit's semantic search is a private package with no doc here. | Pardes's own |

#### Claude Code: settings and skills

| File | What it is | Where it comes from |
|---|---|---|
| `.claude/settings.json` | Claude Code's project settings for this repository: `attribution.sessionUrl` false, which turns off the session link Claude Code adds to the commits it writes (f353d5dc's message). *Note:* Zayit has no .claude/ folder. Later Pardes commits still carry `Claude-Session:` trailers (e.g. 6be0f42b, 0c107b56). | Pardes's own |
| `.claude/skills/pardes-desktop-run/SKILL.md` | Claude Code skill for running Pardes for desktop built from this checkout, headless in the cloud container: Xvfb, Mesa's software EGL for Nucleus's Tao window, a small library cut from the release or the whole one (what needs it, and its size), preferences written so the app opens on Home with a region in Israel (more through `PREFS`), the UTF-8 locale, the Gradle wrapper every build here goes through, a click map of the 1440x900 window (the top bar, the tools menu, the settings, the siddur), how to type, paste, read the clipboard and focus a new window, the install wizard's scripts, a composable rendered in a scratch test, and the pitfalls met (the app closed before any Gradle run, a profile without a region reckoned as abroad, the first click lost, pgrep, llvmpipe's speed). *Note:* Reuses zayit-android's fetch-desktop-library.sh and androidApp/tools/make_subset_db.sh, and takes the daf layouts from a local checkout of the separate pardes-rashi-ocr repository. | Pardes's own |
| `.claude/skills/pardes-desktop-run/scripts/make-library.sh` | Skill script that makes the small library the headless app runs on (default /home/user/pardes-lib): the released library without its full-text index, cut to the base books given (default 103, Berakhot) with their commentaries and targumim, plus the lookup index, lexical.db, catalog.pb and release_info.txt; it then deletes the full seforim.db and copies the Talmud layouts into pardes-daf/. *Note:* Calls zayit-android's fetch-desktop-library.sh (`--no-fulltext`) and androidApp/tools/make_subset_db.sh (CONN_TYPES=1,3); needs about 8.2 GB free while it runs. | Pardes's own |
| `.claude/skills/pardes-desktop-run/scripts/setup.sh` | Skill script that apt-installs what the container lacks to run the desktop app headless: Xvfb, xdotool, x11-utils, ImageMagick, zstd, sqlite3, xclip, Mesa's EGL, GLES and DRI drivers, mesa-utils and Noto fonts, after an `apt-get update` (stale lists once hid libegl1); it then copies central-mirror.init.gradle.kts into ~/.gradle/init.d. Idempotent. | Pardes's own |
| `.claude/skills/pardes-desktop-run/scripts/shot.sh` | Skill script: a screenshot of display :99 (ImageMagick `import -window root`), optionally after an xdotool click at x y and a wait, into $SHOTS (default /tmp/pardes-desktop-shots); with `CROP=<geometry>` also that region alone, to read small text. *Note:* The same as zayit-android's desktop-shot.sh, which shoots the released Zayit instead. | Pardes's own |
| `.claude/skills/pardes-desktop-run/scripts/start.sh` | Skill script that writes Pardes's java.util.prefs file (onboarding_finished, database_path, a region in Israel, semantic_pack_prompt and auto_update_check off, and whatever `PREFS` adds as key=value words), starts Xvfb :99 at 1440x900, runs `:SeforimApp:run` through gradle.sh with LIBGL_ALWAYS_SOFTWARE=1, and maximises the window named פרדס once it appears (up to 20 minutes). *Note:* Replaces whatever an earlier run saved in Pardes's preferences node (`Brand.settings()`). | Pardes's own |
| `.claude/skills/pardes-desktop-run/scripts/central-mirror.init.gradle.kts` | Gradle init script for the cloud container alone, copied by setup.sh into ~/.gradle/init.d: it puts Google's mirror of Maven Central first among the repositories of the settings, the plugins and every project, since Maven Central answers 429 through the container's shared address. *Note:* It is not applied by the repository's build, and changes no build file. | Pardes's own |
| `.claude/skills/pardes-desktop-run/scripts/gradle.sh` | Skill script: `./gradlew` as a Pardes build must run in the container, with a UTF-8 locale and a `gh` that exits 1 first on PATH (settings.gradle.kts asks gh for a token to Zayit's private packages); it refuses to run while the app runs, since a build rewrites the jars the app loads. | Pardes's own |
| `.claude/skills/pardes-desktop-run/scripts/onb.sh` | Skill script that drives the install wizard opened by onboarding.sh: `to-disk` (welcome and licence, on to the disk-space step), `pick <path>` (types into the Swing folder chooser), `click x y` at the wizard's own coordinates, `shot <name>` (the display and the wizard alone). | Pardes's own |
| `.claude/skills/pardes-desktop-run/scripts/onboarding.sh` | Skill script: as start.sh, but with preferences of a first run (no library, the onboarding not done, `INSTALL_DIR` as the folder offered), so the app opens its install wizard; it sets a test address as the feedback service's, so the wizard shows its feedback step. | Pardes's own |
| `.claude/skills/pardes-desktop-run/scripts/stop.sh` | Skill script that closes the app's main window (or the wizard's, `stop.sh wizard`) and waits until its process has ended, after which Gradle may run again. | Pardes's own |
| `.claude/skills/pardes-semantic-search/SKILL.md` | Claude Code skill for Pardes's open semantic search: the app side here (features/search/semantic/, its packs), the model's pipeline in the efrul/pardes-semantic repository since 2026-11-05 (export, benchmark, fine-tuning, distillation, Kaggle runs, the vector index), the invariants (one cleaner and tokenizer for queries and corpus, the model id in the index, int8 with reduce_range, pooling in the ONNX graph), the pitfalls solved (among them the trap of a repeated line's vectors and the app's way out of it, the search measured by scope, the tabs as views of the list found, the library's order and its open question, other apps' searches in the feedback), the Android port, an outside question set kept for measuring only, and Klei Kodesh's data; and the zayit-fidelity skill's rules for every change of the app side. *Note:* Says never to use Zayit's private semantic package or its model for Pardes, and to name Kaggle datasets and kernels without their owner. | Pardes's own |
| `.claude/skills/zayit-android/SKILL.md` | Claude Code skill for Pardes for Android (androidApp/): the name and authorship rules every Pardes text follows, talking to the user, the site and the CI release, a fresh session to a running emulator, the inner loop, the library on the device, matching desktop Zayit 0.6.12, the architecture, offline Lucene and semantic search, and the pitfalls that cost the most. *Note:* Keeps the name zayit-android from before the app became Pardes (2026-10-15). Since 2026-11-05 it asks for Zayit's Conventional Commits (`feat(android): …`) and a change-map row for every file of androidApp/, as the zayit-fidelity skill does. | Pardes's own |
| `.claude/skills/zayit-android/evals/evals.json` | Four test prompts for the zayit-android skill, in skill-creator's format (prompt, expected output, expectations): a Jelly Star settings screenshot, the ClassValue search crash, fetching the library and a subset DB, and About and welcome parity that keeps the authorship texts. | Pardes's own |
| `.claude/skills/zayit-android/references/architecture.md` | zayit-android reference on androidApp's architecture: the separate Gradle build and the SeforimLibrary code it compiles unchanged (the submodule at 4f07145), the runtime flow, a source map, state and navigation, data and storage, and conventions. | Pardes's own |
| `.claude/skills/zayit-android/references/desktop-reference.md` | zayit-android reference on using desktop Zayit 0.6.12 as the Android UI's reference: reading its source in this repository, fetching Zayit's tags, running the released .deb headless, a click map of its window, and a table from each desktop screen to its source and its Android counterpart. *Note:* Says the repository holds only `android-*` tags; it now has `desktop-*` and `semantic-*` release tags too. | Pardes's own |
| `.claude/skills/zayit-android/references/emulator-testing.md` | zayit-android reference on testing in the software-emulated emulator of the cloud container: timings, start, wait and prep, putting a library on it, screenshots, taps and deep links, the label map for emu-tap.py, phone, Jelly Star and tablet sizes, a full screenshot pass, testing the app update, the API 30 emulator, debugging, and the app's network. | Pardes's own |
| `.claude/skills/zayit-android/references/lucene-android.md` | zayit-android reference on offline search on Android with desktop's Lucene 10.5.2 on the same prebuilt index: the settled decisions (offline only, the same engine, a patched lucene-core, NJA set aside), the index files, why stock Lucene fails, the patched jar, the app wiring, delta updates, R8, testing and troubleshooting. | Pardes's own |
| `.claude/skills/zayit-android/references/pitfalls.md` | zayit-android reference listing the pitfalls already hit, each as symptom, cause and fix: RTL and bidi, Compose layout, the Android platform, data and SQLite, desktop parity, build and R8, the emulator and tooling. | Pardes's own |
| `.claude/skills/zayit-android/scripts/contact-sheet.py` | zayit-android script that puts screenshots side by side at one height (20 px apart) in one PNG, for the desktop/Android comparisons sent to the user. *Note:* Needs Pillow. | Pardes's own |
| `.claude/skills/zayit-android/scripts/desktop-shot.sh` | zayit-android script: a screenshot of the released Zayit running headless on :99 (desktop-start.sh), optionally after a click, into $SHOTS (default /tmp/zayit-desktop-shots). | Pardes's own |
| `.claude/skills/zayit-android/scripts/desktop-start.sh` | zayit-android script that installs Zayit's released .deb (default 0.6.12, from Zayit's GitHub releases) if missing and runs it on Xvfb :99 at 1440x900 with its window maximised, as the visual reference for the Android UI. *Note:* Runs Zayit itself, not Pardes for desktop. Tests the display with `pgrep -f "Xvfb :99"`, which pardes-desktop-run's start.sh replaced by `xwininfo -root` (its pitfalls). | Pardes's own |
| `.claude/skills/zayit-android/scripts/emu-demo.sh` | zayit-android script that turns SystemUI's demo mode on (a fixed clock, full battery, Wi-Fi, no notification icons, Gboard instead of the ADB keyboard) or off, for clean release screenshots. | Pardes's own |
| `.claude/skills/zayit-android/scripts/emu-network.sh` | zayit-android script that gives the emulator's apps the container's network: the agent proxy as Android's global HTTP proxy (10.0.2.2) and its CA as a user CA, which Pardes trusts (network_security_config.xml); `off` undoes both. *Note:* Tied to the Claude Code cloud container (its proxy's CA file and port). | Pardes's own |
| `.claude/skills/zayit-android/scripts/emu-shot.sh` | zayit-android script that saves an emulator screenshot (`adb exec-out screencap`) as <dir>/<name>.png (default /tmp/zayit-shots). | Pardes's own |
| `.claude/skills/zayit-android/scripts/emu-tap.py` | zayit-android script that taps the UI node whose text or content description matches (exact, `~substring`, or the n-th match) through `uiautomator dump`, or lists every labelled node with `--list`. | Pardes's own |
| `.claude/skills/zayit-android/scripts/emu-type.sh` | zayit-android script that types text, Hebrew included, into the focused field through the ADBKeyBoard test IME, selecting it again first for a fresh input connection; `--enter` sends the IME action, `--clear` deletes up to 40 characters. | Pardes's own |
| `.claude/skills/zayit-android/scripts/emu-wait.sh` | zayit-android script that waits without a foreground sleep (for the Monitor tool) until the emulator has booted, is stable (services answer four times, the user unlocked, storage mounted), the screen settles, the app has focus, or a file exists. | Pardes's own |
| `.claude/skills/zayit-android/scripts/emulator-prep.sh` | zayit-android script for one-time tuning after boot: root, the screen always on, no animations or ANR and crash dialogs, speed-profile dexopt, and the heavy Google apps disabled (unless KEEP_GOOGLE_APPS=1). | Pardes's own |
| `.claude/skills/zayit-android/scripts/emulator-start.sh` | zayit-android script that starts AVD zayit33 headless (no window, audio or boot animation), in software (TCG) without /dev/kvm, with capped vCPUs (CORES overrides), the swiftshader_indirect GPU, 3 GB and no snapshot save. | Pardes's own |
| `.claude/skills/zayit-android/scripts/fetch-desktop-library.sh` | zayit-android script that streams the latest SeforimLibrary release, the library desktop Zayit installs, into a folder (parts from the manifest under releases/latest/download, stream-ranges.py \| zstd -d \| tar -x) without storing the 5.5 GB archive; `--no-fulltext` skips the 4.95 GB full-text index. *Note:* Its default target is desktop Zayit's data folder on Linux, not Pardes for desktop's; pardes-desktop-run passes `--dest`. | Pardes's own |
| `.claude/skills/zayit-android/scripts/font-notices.py` | zayit-android script that writes androidApp's assets/licenses/Fonts-NOTICE.txt from every bundled font's name table (IDs 0, 13 and 14), one section per family. *Note:* Reads androidApp's fonts only: branding/pardes's desktop Fonts-NOTICE.txt has the same layout over desktop's fonts, and no script for it is in the repository. | Pardes's own |
| `.claude/skills/zayit-android/scripts/pardes-icon.py` | zayit-android script that draws the Pardes icon (פרדס as פר / דס, Frank Ruhl Libre Black outlines, gold on olive green). `androidApp` mode writes the adaptive icon's vectors, its colours and the site's icon.svg; `desktop` mode writes every branding/pardes picture at Zayit's pixel size (AppIcon, LinuxIcon, WindowsIcon.ico, MacosIcon.icns, the logos and the splash). *Note:* The desktop pictures come from a script inside the Android skill; branding/README.md › Merging a Zayit release points to it. 458 lines. | Pardes's own |
| `.claude/skills/zayit-android/scripts/release-frame.py` | zayit-android script that frames screenshots in a phone, Jelly Star or tablet under a Hebrew caption, always with the footer "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו", and writes one PNG per shot plus overview.png. *Note:* Uses desktop's Open Sans from SeforimApp's fonts, and python-bidi when Pillow has no raqm. | Pardes's own |
| `.claude/skills/zayit-android/scripts/setup-android-sdk.sh` | zayit-android script that installs a JDK if missing, the Android cmdline-tools, platform-tools, platform 36, build-tools 36.0.0, the emulator and the API 33 google_apis x86_64 image, creates AVD zayit33 (Pixel 6) and writes androidApp/local.properties; idempotent. | Pardes's own |
| `.claude/skills/zayit-android/scripts/statusbar-clean.py` | zayit-android script that redraws the status bar of an emulator screenshot taken without demo mode (refilled from its top row, a 10:00 clock and a full battery), for release images. *Note:* Uses desktop's Open Sans font from SeforimApp. | Pardes's own |
| `.claude/skills/zayit-android/scripts/stream-ranges.py` | zayit-android script that streams URLs to stdout in order in 256 MB HTTP Range chunks, resuming a dropped chunk from the byte reached (12 tries), so a proxy reset does not break a pipe into zstd and tar. | Pardes's own |
| `.claude/skills/zayit-android/scripts/temple-picture.py` | zayit-android script that builds the Temple countdown widget's picture from a public-domain 1850 painting ("The Siege and Destruction of Jerusalem"): Android's still JPG, three desktop JPGs, and Android's animated WebP whose fires flicker. *Note:* Stale for desktop: it writes SeforimApp/.../drawable/temple_jerusalem_in_fire_{small,medium,large}.jpg, which Zayit deleted (0c8e6c3c) and nothing reads; desktop's files/temple_jerusalem_in_fire.webp is a hand copy of the Android WebP (docs/PARDES_PLAN.md › Phase 1). | Pardes's own; its picture replaced the Shutterstock one inherited from Zayit |
| `.claude/skills/zayit-fidelity/SKILL.md` | Claude Code skill, mandatory for every change: Pardes stays as identical to Zayit as it can be. The smallest change (nothing, a branding value, a new file behind a one-line hook, then an edit), written as Elie Gambache writes Zayit, committed as he commits, every file that differs from Zayit described in docs/ZAYIT_CHANGE_MAP.md in the same commit with check_change_map.py passing, and main reached only through the Change map check (main's ruleset: a pull request, or a fast-forward to a checked commit); merging a Zayit release (its licence first); why GitHub does not show Pardes as a fork. It points to its lessons (references/lessons.md), to the Gradle wrapper of pardes-desktop-run and to scripts/mapedit.py. | Pardes's own |
| `.claude/skills/zayit-fidelity/references/eli-style.md` | The zayit-fidelity skill's reference on writing as Zayit's author writes it: his guidelines (AGENTS.md, CONTRIBUTING.md, CLAUDE.md, .editorconfig, detekt.yml, SeforimLibrary/AGENTS.md, CI), what his code shows (structure, state and events, Metro, settings, errors, logging, Compose, comments, KDoc, tests), how he works (Conventional Commits with their counts, one topic a branch, docs and tests with the change), and Pardes in his style. | Pardes's own; measured on Zayit v0.6.12 and its history |
| `.claude/skills/zayit-fidelity/references/lessons.md` | The zayit-fidelity skill's record of what went wrong and what was found while Pardes's desktop features were built on Zayit 0.6.12 (2026-11-05 to 2026-11-11): building and checking (what CI runs where, ktlint's and detekt's findings, escapes turned into characters), Compose and Jewel, the data and the settings, the change map's mechanics, an add-on of Pardes's own and a third party's engine as a module, reading Zayit's later code and telling a commit's licence, a testers' build, and what the user asked to be done again. | Pardes's own |
| `.claude/skills/zayit-fidelity/scripts/check_change_map.py` | The change map's check: every file that differs from the map's Zayit base has its entry (a heading with Change, What, Why, Kind, Merge risk and Tests for a Zayit file changed; Feature, Size, What, Reached from, Follows and Tests for a file added in Zayit's modules; at least a row for the others), the line counts of detailed entries match the files, no entry is left for a file back to Zayit's, and the base is the last merged release. Compares the working tree (untracked files included), or HEAD with --committed (CI); --stats prints the counts. | Pardes's own |
| `.claude/skills/zayit-fidelity/scripts/mapedit.py` | A small Python class for scripts that edit the change map by entry: a file's line counts or size set to what they are now, a text replaced inside one entry or one row, a block put before a heading, each asserting it found its text once; run alone it prints each section's count of entries. *Note:* Written after a global sed hit two entries at once. | Pardes's own |

#### Workflows and CI scripts

| File | What it is | Where it comes from |
|---|---|---|
| `.github/scripts/daf_release.py` | Makes a daf-<n> release's files from pardes-rashi-ocr's layouts: each tractate's layout gzipped, pardes-daf.zip of them all to unpack beside the library, the manifest Pardes for desktop downloads them by (sha256 of each file and of its layout), and the release's tag, title and notes. Each tractate has a version of its own, written in its file's header and in the manifest: 1 at first, one more in a release where its layout is not the one of the release before (--previous, that release's manifest, whose contentSha256 is the layout's without its version), the same otherwise, so that a tractate that did not change is the same file byte for byte; the notes name the tractates that changed. | Pardes's own |
| `.github/scripts/test_daf_release.py` | The release script's tests, run by hand or by pytest and by daf-release.yml before it builds: a tractate's version goes up only in a release where its layout changed (the other's file the same bytes), the version at the head of the file, a layout that comes with a version of its own the same layout, and every tractate's first version after a release without versions or with none before. | Pardes's own |
| `.github/workflows/android-release.yml` | Workflow, run by hand only (inputs prerelease and note), that builds Pardes for Android's release APK signed with Pardes's key from the repository's secrets, checks its certificate, the lucene-android API 26 gate and the release dex, and publishes `android-<versionName>-<run>` as the Latest release with the APK, the complete source with SeforimLibrary and the desktop-to-phone transfer scripts. *Note:* The notes come from androidApp/CHANGELOG.md's section for the version, and the run stops without one. Zayit's release and site workflows (deploy.yaml, pages.yaml) are removed here. | Pardes's own |
| `.github/workflows/android-site.yml` | Workflow that deploys androidApp/site to GitHub Pages with the transfer scripts, both changelogs (changelog.md, changelog-desktop.md) and the releases.json, semantic.json, daf.json and downloads.json snapshots; runs on pushes to main that touch them, after every successful Android, Desktop, Semantic pack or Daf layouts release, and by hand. *Note:* The Android updater reads releases.json and changelog.md, Pardes for desktop semantic.json and daf.json (SemanticPackReleases, DafDownloads), so their shapes are a contract (its header). | Pardes's own; Zayit's pages.yaml, which deployed Zayit's website/, was removed |
| `.github/workflows/change-map.yml` | Workflow that runs check_change_map.py --committed on every push and pull request, with the whole history (the map's base must be there): a red run names the files to describe. Its job, Change map, is the check main's ruleset requires, by that name. | Pardes's own |
| `.github/workflows/daf-release.yml` | Workflow that publishes the daf view's layouts as a release daf-<n> (daf_release.py, after its tests), from this repository's branch daf-layouts, where pardes-rashi-ocr pushes its layouts/*.json, with the manifest of the release before it downloaded for each tractate's version; the notes say what the words are (Wikisource's Gemara under CC BY-SA through Sefaria, the Vilna Rashi and Tosafot) and the files go out under CC BY-SA. | Pardes's own |
| `.github/workflows/desktop-release.yml` | Workflow, run by hand only, that builds Pardes for desktop through branding/ (Windows x64 GraalVM and arm64 JVM as NSIS, Linux x64 and arm64 as deb, rpm and pacman, macOS Apple Silicon and Intel as dmg and zip), writes Nucleus's latest*.yml update feeds and the complete source, and publishes `desktop-<x.y.z>-<run>` with --latest=false. *Note:* Version and notes from desktop/CHANGELOG.md's top section; GITHUB_REF=refs/tags/v<version> set in the Gradle shell for Versioning. No Zayit secrets, add-ons or Rust installer wrapper; optional Linux GPG and PARDES_FEEDBACK_URL secrets. 600 lines. | Pardes's own, built as Zayit's deploy.yaml (removed here) builds its platforms |
| `.github/workflows/feedback-service.yml` | Workflow that tests the feedback service in Miniflare and, with its Cloudflare secrets, masks the account's name, e-mail and workers.dev subdomain, creates the D1 database the first time, applies the migrations, deploys the Worker and sets its admin token; runs by hand and on pushes to main that touch feedback-service/. *Note:* Without the secrets a push only tests. It deploys from main, the default branch (38ba9543), while CLAUDE.md keeps Zayit's line that PRs target master. | Pardes's own |
| `.github/workflows/semantic-notes.yml` | Workflow, run by hand with a pack's tag (default semantic-2-v3-20261012193827), that writes a released semantic pack's title and notes again from semantic-model/release_notes.py and the release's manifest, then makes the newest Android release the Latest again. | Pardes's own |
| `.github/workflows/semantic-release.yml` | Workflow, run by hand with a Kaggle dataset name (default pardes-semantic-pack-2), that downloads a semantic pack file by file from a private Kaggle dataset, checks it against its manifest (sizes, sha256), zips pardes-model/ and pardes.vec.lucene/ for installing by hand, and publishes `semantic-<pack>-<library>` with --latest=false, making the Android release the Latest again. *Note:* Secret KAGGLE_API_TOKEN; it refuses a dataset name with an owner and masks the token's account in the log. It installs six beside kaggle, which imports it without asking for it. | Pardes's own |

#### Branding: Pardes for desktop's name, pictures and texts

| File | What it is | Where it comes from |
|---|---|---|
| `branding/README.md` | English guide to the brand overlay: how brand.gradle and BrandResources make the build Pardes for desktop, the brand Kotlin objects, the open siddur's addon compiled for Pardes, the tables of every hook in Zayit's files (branding, library location, semantic search, fixes of Zayit's code), the licence-restricted files replaced in place, merging a Zayit release, and rules for ABOUT.md and CONDITIONS.md. *Note:* Says ForeignLibraries.kt is the only reader of BrandLibraries, but LibraryLocationViewModel.kt reads `OTHER_APP_NAME` too. Later features' hooks in Zayit files are not listed (e.g. `FeedbackSendCard()` in DataSettingsScreen.kt). | Pardes's own |
| `branding/brand.gradle` | Groovy script applied by the last line of SeforimApp/build.gradle.kts: picks the brand (`-Pseforimapp.brand`, default pardes) and adds branding/<brand>/kotlin to jvmMain; for Pardes it sets nucleus.application's packaging values from brand.properties, clears the zayit:// protocol, drops the AppX target, fails if a Zayit private add-on is on, compiles Pardes's open smart siddur in the place of Zayit's (it leaves `addons/siddur/disabled` out of jvmMain with an exclude on its path, adds `addons/siddur/open/kotlin`, the `:siddur-open` module and the tests of `addons/siddur/open-test/kotlin`), and registers brandLicences, brandComposeResources and brandAssets as the app's resources and package assets. *Note:* Groovy, while all of Zayit's build scripts are Kotlin DSL; its header says why (the Nucleus DSL is set by name, without its types on this script's classpath). brandLicences also copies the two notices of the open siddur's data (`siddur-open/COPYING.AMUD` as GPL-3.0.txt, `COPYING.HEBCAL` as Hebcal-LICENSE.txt). With the zayit brand it only adds branding/zayit/kotlin. | Pardes's own |
| `branding/pardes/assets/common/splash.png` | 800x533 splash picture laid over Zayit's package-time asset common/splash.png: Zayit's layout (a dark card with a gold border, the icon with a gold rim, the name under it) with the Pardes icon and the word פרדס in gold on dark olive. *Note:* Zayit's Rust installer wrapper (installer/), which also shows that splash, is not built for Pardes. | generated by .claude/skills/zayit-android/scripts/pardes-icon.py (desktop mode); replaces Zayit's SeforimApp/src/jvmMain/assets/common/splash.png (the olive branch and זית on black) |
| `branding/pardes/assetsExtra/common/licenses/ANGLE-LICENSE.txt` | BSD 3-Clause licence of ANGLE (libEGL and libGLESv2, also nucleus.angle-natives), which Nucleus's Tao window ships. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. *Note:* Zayit's packages carry no third-party licence texts; all of this folder is Pardes's. | copied from ANGLE (BSD-3-Clause); identical to META-INF/licenses/LICENSE-BSD-3-Clause-angle.txt in the Nucleus tao jar |
| `branding/pardes/assetsExtra/common/licenses/AccessKit-LICENSE-MIT.txt` | MIT licence of AccessKit, the accessibility library compiled into Nucleus's Tao window binary (MIT or Apache-2.0). Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from AccessKit (MIT); identical to the Nucleus tao jar's META-INF/licenses/LICENSE-MIT-accesskit.txt |
| `branding/pardes/assetsExtra/common/licenses/Apache-2.0.txt` | The Apache License 2.0, shared by the Apache-licensed components (Kotlin and kotlinx, Ktor, Compose Multiplatform, AndroidX, Jewel, Metro, SQLDelight, OkHttp, Filament, tao, Material icons, Tinos and others). Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from the Apache Software Foundation (Apache-2.0); same text as androidApp/app/src/androidMain/assets/licenses/Apache-2.0.txt |
| `branding/pardes/assetsExtra/common/licenses/Apache-Commons-NOTICE.txt` | The NOTICE texts of Apache Commons Compress, IO, Lang and Codec. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from the Apache Commons jars (Apache-2.0); same text as androidApp/app/src/androidMain/assets/licenses/Apache-Commons-NOTICE.txt |
| `branding/pardes/assetsExtra/common/licenses/Bitstream-Charter-LICENSE.txt` | Bitstream's permission notice for the Charter glyphs in some Culmus fonts. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from the Culmus fonts (Bitstream Charter licence); same text as androidApp/app/src/androidMain/assets/licenses/Bitstream-Charter-LICENSE.txt |
| `branding/pardes/assetsExtra/common/licenses/Bootstrap-Icons-LICENSE.txt` | MIT licence of Bootstrap Icons, drawn in the app's icons module. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Bootstrap Icons (MIT); same text as androidApp/app/src/androidMain/assets/licenses/Bootstrap-Icons-LICENSE.txt |
| `branding/pardes/assetsExtra/common/licenses/ConfettiKit-LICENSE.txt` | MIT licence of ConfettiKit. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from ConfettiKit (MIT) |
| `branding/pardes/assetsExtra/common/licenses/Culmus-LICENSE.txt` | Licence terms of the Culmus Hebrew fonts: GPL v2 with the font exception. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Culmus (GPL-2.0 with the font exception); same text as androidApp/app/src/androidMain/assets/licenses/Culmus-LICENSE.txt |
| `branding/pardes/assetsExtra/common/licenses/FileKit-LICENSE.txt` | MIT licence of FileKit (file dialogs and the app's data folder). Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from FileKit (MIT) |
| `branding/pardes/assetsExtra/common/licenses/Fonts-NOTICE.txt` | The copyright and licence notices embedded in each font Pardes for desktop bundles (name table IDs 0, 13 and 14), verbatim, one section per family and its files. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. *Note:* Inter and JetBrains Mono (Jewel's fonts) are not in it; their OFL texts are separate files here. | made from the desktop fonts' name tables in font-notices.py's layout (that script writes only androidApp's copy); differs from androidApp's in its header line, the file names, and the Caladings CLM and Open Sans sections |
| `branding/pardes/assetsExtra/common/licenses/GPL-2.0.txt` | The GNU GPL v2, for the Culmus and Taamey fonts (GPL v2 with the font exception). Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from the Free Software Foundation (GPL-2.0); same text as androidApp/app/src/androidMain/assets/licenses/GPL-2.0.txt |
| `branding/pardes/assetsExtra/common/licenses/JNA-LICENSE.txt` | JNA's licence notice (Apache-2.0 or LGPL-2.1-or-later); Pardes takes it under Apache-2.0 (licenses/README.md). Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from JNA (Apache-2.0 OR LGPL-2.1-or-later) |
| `branding/pardes/assetsExtra/common/licenses/KoalaPlot-LICENSE.txt` | MIT licence of Koala Plot (charts). Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Koala Plot (MIT) |
| `branding/pardes/assetsExtra/common/licenses/LGPL-2.1.txt` | The GNU LGPL v2.1, for KosherKotlin (the zmanim). Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from the Free Software Foundation (LGPL-2.1); same text as androidApp/app/src/androidMain/assets/licenses/LGPL-2.1.txt |
| `branding/pardes/assetsExtra/common/licenses/Lucene-LICENSE.txt` | Apache Lucene's LICENSE.txt: Apache-2.0 plus the licences of the code Lucene derives from. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Apache Lucene (Apache-2.0); same text as androidApp/lucene-android/LICENSE.txt |
| `branding/pardes/assetsExtra/common/licenses/Lucene-NOTICE.txt` | Apache Lucene's NOTICE.txt. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Apache Lucene (Apache-2.0); same text as androidApp/lucene-android/NOTICE.txt |
| `branding/pardes/assetsExtra/common/licenses/Lucide-LICENSE.txt` | ISC licence of Lucide icons, with Feather's MIT part. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Lucide (ISC); same text as androidApp/app/src/androidMain/assets/licenses/Lucide-LICENSE.txt |
| `branding/pardes/assetsExtra/common/licenses/Nucleus-LICENSE.txt` | MIT licence of Nucleus (Copyright (c) 2026 Elie Gambache), the window, packaging, update, notification and HTTP framework the desktop app is built on. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Nucleus (MIT) |
| `branding/pardes/assetsExtra/common/licenses/Nucleus-THIRD_PARTY_NOTICES.md` | Nucleus's notices of the third-party code it redistributes (the Compose Gradle plugin, tao, AccessKit, ANGLE, robius-share), its build-time tools and its Rust crates. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. *Note:* Older than the copy inside the Nucleus tao jar now used (base 3.0.0-dev-202610272317), which adds robius-speech and robius-authentication sections. | copied from Nucleus (MIT) |
| `branding/pardes/assetsExtra/common/licenses/OFL-Bona-Nova-SC.txt` | SIL Open Font License 1.1 of Bona Nova SC. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. *Note:* Its copyright line says 2020, while the font's own notice and licenses/README.md say 2017. | copied from Bona Nova (OFL-1.1); same text as androidApp/app/src/androidMain/assets/licenses/OFL-Bona-Nova-SC.txt |
| `branding/pardes/assetsExtra/common/licenses/OFL-Cardo.txt` | SIL Open Font License 1.1 of Cardo. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Cardo (OFL-1.1); same text as androidApp/app/src/androidMain/assets/licenses/OFL-Cardo.txt |
| `branding/pardes/assetsExtra/common/licenses/OFL-Frank-Ruhl-Libre.txt` | SIL Open Font License 1.1 of Frank Ruhl Libre, also the font of the Pardes icon. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Frank Ruhl Libre (OFL-1.1); same text as androidApp/app/src/androidMain/assets/licenses/OFL-Frank-Ruhl-Libre.txt |
| `branding/pardes/assetsExtra/common/licenses/OFL-Inter.txt` | SIL Open Font License 1.1 of Inter, one of Jewel's fonts. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Inter (OFL-1.1) |
| `branding/pardes/assetsExtra/common/licenses/OFL-JetBrains-Mono.txt` | SIL Open Font License 1.1 of JetBrains Mono, Jewel's monospace font. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from JetBrains Mono (OFL-1.1) |
| `branding/pardes/assetsExtra/common/licenses/OFL-Libertinus-Serif.txt` | SIL Open Font License 1.1 of Libertinus Serif. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Libertinus (OFL-1.1); same text as androidApp/app/src/androidMain/assets/licenses/OFL-Libertinus-Serif.txt |
| `branding/pardes/assetsExtra/common/licenses/OFL-Miriam-Libre.txt` | SIL Open Font License 1.1 of Miriam Libre. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Miriam Libre (OFL-1.1); same text as androidApp/app/src/androidMain/assets/licenses/OFL-Miriam-Libre.txt |
| `branding/pardes/assetsExtra/common/licenses/OFL-Noto-Hebrew.txt` | SIL Open Font License 1.1 of Noto Rashi Hebrew and Noto Serif Hebrew. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Noto Hebrew (OFL-1.1); same text as androidApp/app/src/androidMain/assets/licenses/OFL-Noto-Hebrew.txt |
| `branding/pardes/assetsExtra/common/licenses/OFL-Open-Sans.txt` | SIL Open Font License 1.1 of Open Sans. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Open Sans (OFL-1.1) |
| `branding/pardes/assetsExtra/common/licenses/ONNX-Runtime-LICENSE.txt` | MIT licence of ONNX Runtime, which runs the semantic search's model (1.17.1 on desktop). Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from ONNX Runtime (MIT); same text as androidApp/app/src/androidMain/assets/licenses/ONNX-Runtime-LICENSE.txt |
| `branding/pardes/assetsExtra/common/licenses/ONNX-Runtime-ThirdPartyNotices.txt` | ONNX Runtime's third-party notices (6,508 lines). Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. *Note:* Differs from androidApp's copy, which is ONNX Runtime 1.22.0's (a665f012). | copied from ONNX Runtime (MIT) |
| `branding/pardes/assetsExtra/common/licenses/README.md` | Hebrew index of the licences folder, shipped in the install folder: Pardes for desktop's own licence (AGPL v3 with §7b, based on Zayit and not on his behalf, its source) and every bundled library, the semantic model, icon set, font and data source with its copyright, licence and file here, the experimental smart siddur's engine and the two sources of its data (SeforimOpenSiddur, Amud, Hebcal Leyning), plus the five texts the build copies in (brandLicences). *Note:* Zayit's packages carry no third-party licence texts. A new desktop dependency, font, icon set or data file needs its entry and text here (branding/README.md). HaMichlol's line went with its definitions (2026-11-05). | Pardes's own |
| `branding/pardes/assetsExtra/common/licenses/Radix-Icons-LICENSE.txt` | MIT licence of Radix Icons, drawn in the app's icons module. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Radix Icons (MIT); same text as androidApp/app/src/androidMain/assets/licenses/Radix-Icons-LICENSE.txt |
| `branding/pardes/assetsExtra/common/licenses/Robius-LICENSE-MIT.txt` | MIT licence of Project Robius's robius-share, derived source in Nucleus's share module. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Project Robius (MIT); identical to the Nucleus tao jar's META-INF/licenses/LICENSE-MIT-robius.txt |
| `branding/pardes/assetsExtra/common/licenses/SLF4J-LICENSE.txt` | MIT licence of SLF4J. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from SLF4J (MIT) |
| `branding/pardes/assetsExtra/common/licenses/Sentry-LICENSE.txt` | MIT licence of the Sentry Java SDK, which Zayit's crash reporting bundles; Pardes has no DSN and sends no reports, but the SDK stays in the app. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from the Sentry Java SDK (MIT) |
| `branding/pardes/assetsExtra/common/licenses/Skia-LICENSE.txt` | BSD 3-Clause licence of Skia, the drawing engine inside Skiko. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Skia (BSD-3-Clause) |
| `branding/pardes/assetsExtra/common/licenses/Tabler-Icons-LICENSE.txt` | MIT licence of Tabler Icons, drawn in the app's icons module. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Tabler Icons (MIT); same text as androidApp/app/src/androidMain/assets/licenses/Tabler-Icons-LICENSE.txt |
| `branding/pardes/assetsExtra/common/licenses/UnrealEngineSkyAtmosphere-LICENSE.txt` | MIT licence of UnrealEngineSkyAtmosphere (Epic Games), whose constants the sky widget's atmosphere model uses (earthwidget's CREDITS.md). Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from UnrealEngineSkyAtmosphere (MIT) |
| `branding/pardes/assetsExtra/common/licenses/Zstandard-LICENSE.txt` | BSD licence of Zstandard, which unpacks the library bundle. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from Zstandard (BSD); same text as androidApp/app/src/androidMain/assets/licenses/Zstandard-LICENSE.txt |
| `branding/pardes/assetsExtra/common/licenses/autolink-java-LICENSE.txt` | MIT licence of autolink-java, used by Jewel's Markdown. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from autolink-java (MIT) |
| `branding/pardes/assetsExtra/common/licenses/commonmark-java-LICENSE.txt` | BSD 2-Clause licence of commonmark-java, Jewel's Markdown parser. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from commonmark-java (BSD-2-Clause) |
| `branding/pardes/assetsExtra/common/licenses/dbus-java-LICENSE.txt` | MIT licence of dbus-java. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from dbus-java (MIT) |
| `branding/pardes/assetsExtra/common/licenses/jsoup-LICENSE.txt` | MIT licence of jsoup. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. *Note:* androidApp's copy differs only in the copyright years (2009-2025 against 2009-2026). | copied from jsoup (MIT) |
| `branding/pardes/assetsExtra/common/licenses/sqlite-jdbc-Zentus-LICENSE.txt` | BSD-style licence of the Zentus code in Xerial's SQLite JDBC driver. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from sqlite-jdbc (BSD-style) |
| `branding/pardes/assetsExtra/common/licenses/zstd-jni-LICENSE.txt` | BSD 2-Clause licence of zstd-jni, Zstandard's Java binding. Shipped as a plain file in the install folder's licenses/ (brand.gradle's brandAssets adds assetsExtra at package time) and listed in its README.md. | copied from zstd-jni (BSD-2-Clause); same text as androidApp/app/src/androidMain/assets/licenses/zstd-jni-LICENSE.txt |
| `branding/pardes/brand.properties` | UTF-8 properties with Pardes for desktop's packaging values, which brand.gradle sets in place of Zayit's: app name פרדס, package and image name pardes, a Hebrew description and an English Linux name and comment (based on Zayit by Elie (Elyahou) Gambache, not on his behalf), vendor, homepage and release repository, the installers' copyright, the Linux maintainer and WM class, the Windows upgrade UUID and the macOS bundle id. *Note:* The copyright is Elie Gambache's line word for word. The upgrade UUID was made once for Pardes and must never be Zayit's, which would make the installer upgrade Zayit away. | Pardes's own; its keys mirror the nativeDistributions values of Zayit's SeforimApp/build.gradle.kts |
| `branding/pardes/composeResources/drawable/AppIcon.png` | 1024x1024 Pardes icon (פר / דס in gold Frank Ruhl Libre Black on a rounded olive-green square): the window and taskbar icon (MainAppWindow, InstallerWindow, SettingsWindow) and the Settings › כללי header's (GeneralSettingsScreen). *Note:* The same bytes as branding/pardes/desktopAppIcons/LinuxIcon.png. | generated by .claude/skills/zayit-android/scripts/pardes-icon.py (desktop mode); replaces Zayit's SeforimApp/src/commonMain/composeResources/drawable/AppIcon.png (a gold olive branch on a black rounded square with a gold rim) |
| `branding/pardes/composeResources/drawable/zayit_logo_branch.png` | 82x88 picture beside the search bar of the search results screen (`LogoBranch` in HomeView.kt, shown by SearchResultScreen): the Pardes icon at 82 px. *Note:* Keeps Zayit's file name: the overlay replaces by path, so Zayit's code stays as released. | generated by .claude/skills/zayit-android/scripts/pardes-icon.py; replaces Zayit's drawable/zayit_logo_branch.png (an olive branch) |
| `branding/pardes/composeResources/drawable/zayit_new_logo.png` | 1556x715 base layer of the Home logo (`LogoImage` in HomeView.kt): the word פרדס on one line, in gold. *Note:* Keeps Zayit's file name (the overlay replaces by path). | generated by .claude/skills/zayit-android/scripts/pardes-icon.py; replaces Zayit's drawable/zayit_new_logo.png (the lettering זית between olive branches) |
| `branding/pardes/composeResources/drawable/zayit_new_logo_text.png` | 1556x715 white mask of the word פרדס, which `LogoImage` paints over the base layer in gold in light mode and in the accent colour in dark mode. *Note:* Keeps Zayit's file name (the overlay replaces by path). | generated by .claude/skills/zayit-android/scripts/pardes-icon.py; replaces Zayit's drawable/zayit_new_logo_text.png (the mask of זית) |
| `branding/pardes/composeResources/drawable/zayit_transparent.png` | 645x746 picture of the onboarding's welcome (InitScreen): the Pardes icon, 560 px, centred on a transparent ground. *Note:* Keeps Zayit's file name (the overlay replaces by path). | generated by .claude/skills/zayit-android/scripts/pardes-icon.py; replaces Zayit's drawable/zayit_transparent.png (a gold olive branch) |
| `branding/pardes/composeResources/files/ABOUT.md` | Pardes for desktop's About page (Settings › אודות, AboutSettingsScreen), in Hebrew: based on Zayit by Elie Gambache and not on his behalf, built from Zayit 0.6.12's open source, his copyright, AGPL v3, the §7b line, Pardes's source, Zayit's private modules left out, and thanks (Zayit and SeforimLibrary, Sefaria and Otzaria, Klei Kodesh's book search and dictionary data, the libraries, fonts, Emoji One, the licences folder, the temple picture's painting). *Note:* The §7b line ends with a space before U+2069, so the autolinker keeps the mark out of the link (branding/README.md › Texts); Klei Kodesh's line, whose address stood right before the mark and was no link, has it as a Markdown link, and no longer says how Klei Kodesh came by Torat Emet's dictionary (the user's request of 2026-11-10; the licences index still does). It does not credit Dicta's model, which CONDITIONS.md and the licences index do. | Pardes's own; replaces Zayit's SeforimApp/src/commonMain/composeResources/files/ABOUT.md (the author's first-person About) |
| `branding/pardes/composeResources/files/CONDITIONS.md` | Pardes for desktop's terms (the onboarding's licence step and Settings › תנאים, through `ConditionsText`), in Hebrew: the books' sources and rights, Rashi Amiti left out, Culmus, Emoji One, the temple painting, the app's licence (based on Zayit and not on his behalf, his copyright, AGPL v3, §7b, built from 0.6.12), the private modules left out, Dicta's model (CC BY 4.0) and privacy: no error reports, and the feedback sent only with the user's consent (what, where to, the question asked at the first install with its boxes checked at first, the searches made in Zayit and in Otzaria on the same computer when the user leaves them checked, their files read only then and never changed; what the open site shows everyone: the figures and the daf marks, and the searches' words only when "פרסום במאגר הפתוח" stayed checked, else to the maintainer alone, never the sender's id; how to stop it and delete it, and that copies others downloaded stay). *Note:* Keeps Zayit's `<!-- if:self-update -->` and `<!-- if:update-helper -->` blocks. | Pardes's own; replaces Zayit's SeforimApp/src/commonMain/composeResources/files/CONDITIONS.md |
| `branding/pardes/composeResources/values/strings.xml` | The ten strings Pardes for desktop changes, each put by BrandResources in place of Zayit's string of the same name: app_name (פרדס), the onboarding's title bar, welcome title and subtitle, extraction tip and finish message, the Settings › כללי header's created-by, licence and private-modules lines (the §7b line in LRI…PDI), and update_dialog_message. *Note:* Never Zayit's "נוצר על ידי אליהו גמבש" or "שנבנה באהבה…" (the zayit-android skill's authorship rules). | Pardes's own; overrides those names in Zayit's SeforimApp/src/commonMain/composeResources/values/strings.xml |
| `branding/pardes/desktopAppIcons/LinuxIcon.png` | 1024x1024 Pardes icon of the Linux packages (deb, rpm, pacman), which brand.gradle sets as `linux.iconFile`. *Note:* Not an overlay: brand.gradle points Nucleus at it. The same bytes as the brand's AppIcon.png. | generated by .claude/skills/zayit-android/scripts/pardes-icon.py; used in place of Zayit's SeforimApp/desktopAppIcons/LinuxIcon.png (the olive branch icon) |
| `branding/pardes/desktopAppIcons/MacosIcon.icns` | macOS icon set (PNG entries from 32 to 1024 px) of the dmg and zip, which brand.gradle sets as `macOS.iconFile`. *Note:* Added with the macOS builds (0c107b56, desktop 1.0.2). | generated by .claude/skills/zayit-android/scripts/pardes-icon.py; used in place of Zayit's SeforimApp/desktopAppIcons/MacosIcon.icns (one 1024 px image) |
| `branding/pardes/desktopAppIcons/WindowsIcon.ico` | Windows icon (PNG entries at 16, 20, 24, 32, 40, 48, 64, 128 and 256 px) of the NSIS installer and the exe, which brand.gradle sets as `windows.iconFile`. | generated by .claude/skills/zayit-android/scripts/pardes-icon.py; used in place of Zayit's SeforimApp/desktopAppIcons/WindowsIcon.ico (one 256 px entry) |
| `branding/pardes/kotlin/io/github/kdroidfilter/seforimapp/branding/Brand.kt` | Pardes for desktop's `Brand`: `APP_ID`, FileKit's data-folder id (Pardes's own, as the Android applicationId), and `settings()`, java.util.prefs in a node of its own, so the library, notes and settings stay apart from an installed Zayit's; and `OPEN_SIDDUR`, true: the build carries the open smart siddur, so the settings offer its switch (`OpenSiddurCard`). *Note:* Read by main.kt, AppCoreBindings.provideSettings() and PendingUserDataRestore in place of Zayit's literals. | Pardes's own |
| `branding/pardes/kotlin/io/github/kdroidfilter/seforimapp/branding/BrandInfo.kt` | Pardes's names and links where Zayit has literals: SOURCE_URL (Pardes's GitHub source), ERROR_REPORT_SIGNATURE ("נשלח מתוך פרדס למחשב, המבוסס על זית"), BACKUP_FILE_PREFIX pardes_backup_, LINUX_DESKTOP_FILE_ID pardes.desktop, USER_AGENT, and LIBRARY_FOLDER_NAME "Pardes library". *Note:* Read by GeneralSettingsScreen, BookErrorReport, DataSettingsViewModel, AppLinuxQuicklist, HttpFileDownloader, SemanticPackReleases and LibraryLocation. LINUX_DESKTOP_FILE_ID must match brand.properties' packageName. | Pardes's own |
| `branding/pardes/kotlin/io/github/kdroidfilter/seforimapp/branding/BrandLibraries.kt` | Where an installed desktop Zayit keeps its seforim.db, which Pardes offers on first run to use in place: Zayit's `database_path` preference in the java.util.prefs root (a lexical.db path ignored), then Zayit's default databases folder beside Pardes's data folder; `OTHER_APP_NAME` is "זית למחשב". `otherAppUserDatabases()`: where Zayit keeps its user database, whose searches the user may send with Pardes's own (beside Pardes's data folder, and under a Microsoft Store package's roaming folder). *Note:* Read by ForeignLibraries, LibraryLocationViewModel and ZayitSearches. Its KDoc names the Windows and Linux folders only, though macOS is built since 1.0.2. Leaves out SEFORIMAPP_DATABASE_PATH, which Pardes reads as its own. | Pardes's own |
| `branding/pardes/kotlin/io/github/kdroidfilter/seforimapp/branding/BrandUpdates.kt` | Pardes's update source: `provider` is a `PardesDesktopReleases`, which AppUpdateService takes before Zayit's GitHubProvider, so a Pardes build never downloads Zayit's installer. | Pardes's own |
| `branding/pardes/kotlin/io/github/kdroidfilter/seforimapp/branding/PardesDesktopReleases.kt` | Nucleus `UpdateProvider` that reads the GitHub release list of Pardes's repository (unauthenticated API, per_page=100), takes the newest published `desktop-<x.y.z>-<run>[-r<attempt>]` release carrying this platform's latest*.yml (pre-releases only when Nucleus allows them), and gives its files' download URLs. *Note:* Needed because the repository's Latest release is Android's. Its KDoc says it was written against nucleus.updater-runtime 3.0.0-dev-202610200221; the app now uses 3.0.0-dev-202610272317. | Pardes's own |
| `branding/zayit/kotlin/io/github/kdroidfilter/seforimapp/branding/Brand.kt` | Zayit's `Brand`, for `-Pseforimapp.brand=zayit`: `APP_ID` is Zayit's own data-folder id and `settings()` is multiplatform-settings' `Settings()` over the java.util.prefs root, as Zayit's code has them; `OPEN_SIDDUR` is false, so a Zayit build offers no switch for a siddur it does not carry. | Pardes's own; holds the literals of Zayit's main.kt and AppCoreBindings.kt |
| `branding/zayit/kotlin/io/github/kdroidfilter/seforimapp/branding/BrandInfo.kt` | Zayit's `BrandInfo` values: its GitHub source link, error report signature, zayit_backup_ prefix, zayit.desktop launcher id and User-Agent, plus LIBRARY_FOLDER_NAME "Zayit library" for the library-location feature. *Note:* LIBRARY_FOLDER_NAME has no Zayit counterpart: the library location is a Pardes feature written for both brands. | Pardes's own; holds the literals of Zayit's GeneralSettingsScreen, BookErrorReport, DataSettingsViewModel, AppLinuxQuicklist and HttpFileDownloader |
| `branding/zayit/kotlin/io/github/kdroidfilter/seforimapp/branding/BrandLibraries.kt` | Zayit's `BrandLibraries`: no other app's library (`OTHER_APP_NAME` empty, `otherAppDatabases()` empty) and no other app's user database (`otherAppUserDatabases()` empty), so the onboarding and the library cleanup behave as Zayit's. | Pardes's own |
| `branding/zayit/kotlin/io/github/kdroidfilter/seforimapp/branding/BrandUpdates.kt` | Zayit's `BrandUpdates`: `provider` is null, so AppUpdateService builds Zayit's GitHubProvider as released. | Pardes's own |

#### Pardes for desktop's changelog

| File | What it is | Where it comes from |
|---|---|---|
| `desktop/CHANGELOG.md` | Pardes for desktop's user-facing changelog in Hebrew, one `## <x.y.z> · <date>` section per version, from 1.0.0 (2026-10-28) to 1.0.8 (2026-11-06): desktop-release.yml takes the version and the notes from its top section, and android-site.yml publishes it as changelog-desktop.md. *Note:* Zayit keeps no changelog file. The heading format is a contract with both workflows and the site. | Pardes's own |

#### Documents

| File | What it is | Where it comes from |
|---|---|---|
| `docs/COMMON_SOURCES_PLAN.md` | Hebrew build plan (2026-10-27) for the common sources line above the search results: up to three library lines that many of the first twenty results link to, the rule as measured, the link-table data read on a read-only connection, the code file by file, tests, the work order and open questions; not built yet. *Note:* Written in Hebrew (Zayit's developer docs are in English). Its code line numbers refer to a 2026-10-26 commit. | Pardes's own |
| `docs/DESKTOP_STAGE2.md` | Hebrew working document (2026-11-11) for the stage after the first 26 requests: the user's sixteen remaining requests in his numbers (an update of the daf layouts by lines, a daily-study widget of one's own, flexible docking, a daf-like view for other books, typing over the contents, fonts of one's own, PDF books, and 29 to 37: a screen that opens by itself when something can be downloaded, a settings page for feedback, the library's order and faint meaning hits, the dictionary item, the daf's review always in the menu with an icon and a third answer, a settings window of its own, a check of the pack's update and import, an index for a category's search), grouped by what relates to what, with what the code and two researches already say of each, a proposed order, and the decisions that wait for the user. *Note:* Not yet a plan with steps: `docs/DESKTOP_STAGE2_PLAN.md` is, and this document's header names the three statements of its own that the plan corrects. | Pardes's own |
| `docs/DESKTOP_STAGE2_PLAN.md` | Hebrew plan (2026-11-11) for stage 2 of Pardes for desktop, the document that follows DESKTOP_STAGE2.md: what reading the code at main (`abcb687`) found (four places in a semantic pack's install that fit request 36, no screen for the library's update on desktop, the category field the words' index already carries and what was measured of it, Zayit's tool windows as the way to a settings window of its own, `ok: true` still read, the pipeline's fourth and fifth runs done), how each step is worked, four waves in order (36, 31, 34 with 33, 32 and 30; then 35, 29 and 24; then 25, the docking's three steps and 37; then 19, 28, 21 and the layouts' update, each after a spike, mock-ups or a decision), every request's steps with its files and checks, the versions and testers' builds, the table of the user's decisions with a recommendation for each, and what is left out. *Note:* Written in Hebrew. Nothing in it is built yet; a decision marked as waiting follows the recommendation until the user answers. | Pardes's own |
| `docs/LEXICON.md` | Hebrew report (2026-11-03) on the regular search's dictionary: two defects of lexical.db (bases with no forms, the 32 forms cut in id order), Pardes's fix (`PardesLexicon` builds pardes-lexical.db beside it), the measurement, Klei Kodesh's materials tried, and how to run it again; an addition of 2026-11-09 names the defective spellings of version 2 (fusion-compare/SPELLING.md). *Note:* Written in Hebrew; docs/ZAYIT_LEXICAL_REPORT.md is the English report for Zayit. | Pardes's own |
| `docs/PARDES_PLAN.md` | English plan written 2026-10-20 and updated to 2026-10-29: the merges of Zayit 0.6.7, 0.6.10, 0.6.11 and 0.6.12 (decisions, conflicts, what stays removed), Pardes for Android's parity, Pardes for desktop (branding, data isolation, updater, deep links, versioning, release workflow), the library location, a personal-books placeholder and risks. *Note:* Its title still says "on Zayit 0.6.7". Parts of Phase 3 keep the first design (BuildConfig.BRAND_APP_ID, MSI) that the built branding replaced (Brand.APP_ID, NSIS); its header says the later decisions win. | Pardes's own |
| `docs/SEARCH_FUSION_RESEARCH.md` | Hebrew research note (2026-11-08) on merging the words' search and the meaning's: what plain RRF implies (a line only the words found, from their 11th place on, ranks below every line only the meaning found), rank against score fusion and their normalizations, calibration (ProbFuse, SSL, score distributions), weights by kind of query, a small learned ranker, and an offline measuring plan, which docs/fusion-compare/ carries out. *Note:* Written in Hebrew. | Pardes's own |
| `docs/SEARCH_ORDER_ISSUES.md` | Hebrew working document (from 2026-10-28) on the order of the combined search's results: how it was measured (search history, eval-real, eval-100), ten issues (short semantic lines on top, rank-only fusion, "(א)" numbers, the 32 forms, long lines, the switch to all books, commentaries before the source, no noise floor, the same text twice, identical lines in the graph) with fixes measured offline, and what was not measured; issues 1 and 2 are marked done (2026-11-09) with the window, as fusion-compare/IMPLEMENTATION.md measures them, and issue 11 eased in the app (2026-11-10, the walk started again at the words' hits). *Note:* Written in Hebrew. Its first numbers were measured in a separate working repository. | Pardes's own |
| `docs/SEARCH_OTZARIA_AND_LIBRARIES.md` | Hebrew research (2026-11-08), system by system, on how large libraries search: Otzaria read from its code (per-word options, short lines embedded with their neighbours, long lines in 512-character windows, vectors keyed by their text, an exact scan instead of HNSW, a fusion weight by kind of query), Wikipedia's CirrusSearch, CourtListener, PubMed, Semantic Scholar, TLG, Perseus and STEP Bible, and their licences (Otzaria's app and semantic engine are for personal use only; its lexical engine is MIT). *Note:* Written in Hebrew. | Pardes's own |
| `docs/SEARCH_PLAN.md` | English plan (2026-11-10) for the search after pull request #28: Pardes for desktop 1.0.9 from its Windows pre-release to the public release, per-word forms in the regular search (measure the spelling and ים↔ין forms in the measurement build, then the owner's decision on SeforimLibrary), the new eval-real grades into pardes-semantic, Android's lexicon and fusion behind desktop's, one re-embedding for short lines and the "(א)" numbers, and the open questions with what was measured and is not to do. | Pardes's own |
| `docs/SEARCH_SYNTAX_REPORT.md` | English report (measured 2026-11-03) on how the search reads quotes, geresh and gershayim, maqaf and niqqud, prefixes and suffixes, word distance (near = 5, which only ranks) and order, with line counts on the October 2026 library, the recommendations not built, and Klei Kodesh's search syntax for comparison. *Note:* Finds that Hebrew-keyboard gershayim (רש״י) search as רשי, typographic quotes are ignored, and Zayit's strings.xml keeps unused search_level_1..5 strings. | Pardes's own |
| `docs/SEARCH_WORLD_RESEARCH.md` | Hebrew research (2026-11-08) on how search systems outside Pardes handle each problem SEARCH_ORDER_ISSUES.md measures, with sources and what to try, cheapest first; it notes that the words' search adds each of a word's forms as a term of its own, twice, so scores add up across forms and favour long lines (Lucene's `SynonymQuery` is the known remedy). *Note:* Written in Hebrew. | Pardes's own |
| `docs/SEMANTIC_PLAN.md` | Hebrew plan (2026-10-28) for shipping the semantic search's model and vector index as packs: how Zayit ships its library, what is ready, publishing packs as `semantic-*` releases from a Kaggle dataset, the code and the in-app install (both done), incremental index updates (open), and open decisions. *Note:* Written in Hebrew. | Pardes's own |
| `docs/ZAYIT_CHANGE_MAP.md` | This map (מפת השינויים): every file in which Pardes differs from the Zayit release it is based on, Zayit's files changed in full, the files added in Zayit's modules by feature, Pardes's own trees in rows, the departures from Zayit's style still to fix, Zayit after the base, and why GitHub does not show Pardes as a fork. | Pardes's own |
| `docs/ZAYIT_LEXICAL_REPORT.md` | English report addressed to Zayit: lexical.db (release 20261012193827) has 4,711 bases with no forms, 2,479 of them spelled like another base's form, which `MagicDictionaryIndex.expansionFor` prefers, and the 32 forms kept are the first by id; the measured effect and a fix in the generator or at runtime. *Note:* Pardes applies the fix at runtime (PardesLexicon, docs/LEXICON.md) without changing SeforimLibrary. | Pardes's own |

#### Agents' reports (docs/agents/)

| File | What it is | Where it comes from |
|---|---|---|
| `docs/agents/2026-11-11-stage2-code-maps/daf-view.md` | English code map (2026-11-11, read-only, main at `abcb687`) for stage 2 of Pardes for desktop, requests 33, 34, 29, 21 and the layouts' update: where the daf view's review menu is built and what gates it, the marks' model and files, how unsure lines and marks are drawn, the reader's end bar button by button, the daf settings page, the layout files with their manifest, update check and download, each file of features/bookcontent/daf with what it does, and how the daf's icon is kept; with the brief the agent got. *Note:* Its line numbers are that commit's. | Pardes's own: an Explore agent's report as it came back, under a header by the session that ran it |
| `docs/agents/2026-11-11-stage2-code-maps/reader-fonts-limud-userdata.md` | English code map (2026-11-11, read-only, main at `abcb687`) for stage 2 of Pardes for desktop, requests 32, 24, 25 and 19: the dictionary item in both menus and the lookup behind it, the contents pane's search and how keys and hover reach the panes, the font catalog with its keys and pickers, how a Home widget is declared and stored and how the daily-study widget opens the library, the user database's tables and what the backup carries, and the fields of a contents entry; with the brief the agent got. *Note:* Not checked by hand; its last section says what it could not confirm. | Pardes's own: an Explore agent's report as it came back, under a header by the session that ran it |
| `docs/agents/2026-11-11-stage2-code-maps/search-order-scope.md` | English code map (2026-11-11, read-only, main at `abcb687`) for stage 2 of Pardes for desktop, requests 31 and 37: `CatalogOrderSession` step by step with the merge of the lines the meaning alone found, the sort in the UI and the results' list items, how a scope becomes a filter on each side, the whole-scope scoring when the words find nothing, the benches and debug lines, and the tabs' counts; its header quotes what `5c6a545` measured of the index's category field; with the brief the agent got. | Pardes's own: an Explore agent's report as it came back, under a header by the session that ran it |
| `docs/agents/2026-11-11-stage2-code-maps/semantic-pack-install.md` | English reading (2026-11-11, read-only, main at `abcb687` and efrul/Nucleus at `65ccd12`) of a semantic pack's path for request 36: found, brought, staged, scheduled, put in place at the next start and shown, and the four places that fit "the old model's details" after an import (the card until the restart, a failed swap that throws the staged pack away, a restart that starts the new app before the old one has ended, a pack with no pardes-pack.json installed without its number). *Note:* Nothing in it was reproduced in the app. | Pardes's own: the reading of the session that wrote the plan, not an agent's |
| `docs/agents/2026-11-11-stage2-code-maps/settings-feedback-startup.md` | English code map (2026-11-11, read-only, main at `abcb687`) for stage 2 of Pardes for desktop, requests 30, 35 and 29: the settings window (a dialog composed inside its owner window), its state, call sites, navigation and pages, Zayit's tool windows as the precedent for a window of its own, `SettingCard` and the cards not built from it, every feedback control with its key, and the order of the start in main.kt; its header records that the library's delta update has no screen on desktop; with the brief the agent got. | Pardes's own: an Explore agent's report as it came back, under a header by the session that ran it |
| `docs/agents/README.md` | Index of the reports that the agents of Claude Code sessions gave back, kept as they came so that a later session starts from them: a folder per run and a file per report (what it was for, the commit read, what was checked by hand, the report, the brief), a row per run; it points to the two earlier researches of stage 2 kept in the private daf repository. | Pardes's own |

#### The search's measurements (docs/fusion-compare/)

| File | What it is | Where it comes from |
|---|---|---|
| `docs/fusion-compare/ANALYSIS.md` | Hebrew analysis of the orderings on the feedback's 1,156 searches and its 89 graded ones: what the first ten are made of, the measures by the grades before and after Claude graded the missing lines, by query length and scope, examples, and what to do next (p12wl, proposals 1 and 2 with the window). | Pardes's own |
| `docs/fusion-compare/CASE_LIMONI.md` | Hebrew case study (2026-11-09) of one search, "לימוני דומים לפגעי": why the regular search misses Shibbolei HaLeket (דומים takes the verb's base, where דומין is cut at 32), why the fusion buried the words' hit, every ordering on six forms of the query, three fusion variants measured on four test sets and left out, and what to learn (ים/ין belongs with the per-word forms). | Pardes's own |
| `docs/fusion-compare/EXPANSIONS.md` | Hebrew report (2026-11-09): synonyms, the Aramaic dictionary and full/defective spelling given as extra forms inside Pardes's own regular search (a measurement hook), on every ordering, on the source test and the graded feedback; how much each side's order counts (shuffled, reversed); the recommendations. | Pardes's own |
| `docs/fusion-compare/IMPLEMENTATION.md` | Hebrew verification (2026-11-09) of p12wl in the app: its three weights, the app's first ten against the tool's on four test sets, the unit tests, before and after on the source test, eval-100, the feedback and eval-real (with the grades added in eval_real/), what it gains and loses, two variants for short seifim, and how to run it again. | Pardes's own |
| `docs/fusion-compare/OTZARIA_LEXICAL.md` | Hebrew report (2026-11-09): Pardes's regular search has no full/defective spelling rule; Otzaria's lexical search ported to Pardes's index in five modes and measured on the source test and the graded feedback with every ordering: spelling helps quotes, a phrase-only search hurts ideas. | Pardes's own |
| `docs/fusion-compare/README.md` | Hebrew index of the folder (from 2026-11-08): what was run (the feedback's searches through the app's engines, then every ordering of the research notes on the same candidates), the orderings, what the page's summary says and does not, how to run it again, the files, and links to the reports below. | Pardes's own |
| `docs/fusion-compare/SOURCE_TEST.md` | Hebrew report (2026-11-08) of the "where is it written" test: 157 quotes of the base books, each ordering measured strictly and leniently, the app losing the source from the first place, p12wl best on both parts, the regular search missing quotes in full spelling, the recommendations. | Pardes's own |
| `docs/fusion-compare/SPELLING.md` | Hebrew report (2026-11-09): defective spellings through PardesLexicon without touching SeforimLibrary, two versions measured on four test sets, why joining a spelling to another base hurts (homographs, the extra base's order), what went in (version 2), and why the full gain needs a per-word hook in LuceneSearchEngine. | Pardes's own |
| `docs/fusion-compare/claude_grades.tsv` | 361 grades (0 to 3) that Claude gave lines of the feedback's searches the feedback had not graded: query, line id, grade, book, reference. | Pardes's own; graded by Claude |
| `docs/fusion-compare/eval_real/grades_new.tsv` | 160 grades that Claude gave eval-real's lines, blind to the version that found them, in the format of pardes-semantic's eval-real/grades.tsv (qid, lineId, grade, ref), to be added there. | Pardes's own; graded by Claude |
| `docs/fusion-compare/expansions/feedback-condensed-10.txt` | graded_compare.py's condensed measures (judged lines only) in the first ten, for every ordering of each extra-forms run on the feedback. | generated by tools/graded_compare.py |
| `docs/fusion-compare/expansions/feedback-graded-10.txt` | graded_compare.py's measures in the first ten for every ordering of each extra-forms run on the feedback. | generated by tools/graded_compare.py |
| `docs/fusion-compare/expansions/feedback-graded-3.txt` | graded_compare.py's measures in the first three for the app and p12wl of each extra-forms run on the feedback. | generated by tools/graded_compare.py |
| `docs/fusion-compare/expansions/feedback-spelling-10.txt` | graded_compare.py's measures in the first ten for every ordering, the spelling run against the base run. | generated by tools/graded_compare.py |
| `docs/fusion-compare/expansions/feedback-spelling-3.txt` | The same in the first three. | generated by tools/graded_compare.py |
| `docs/fusion-compare/expansions/feedback_expansions.html` | The feedback's page with every extra-forms run's orderings as columns of their own. | generated by tools/merge_runs.py and tools/make_html.py |
| `docs/fusion-compare/expansions/order-feedback.txt` | order_shuffle.py on the feedback: how much of the app's first ten stays when one side's candidates are shuffled or reversed. | generated by tools/order_shuffle.py |
| `docs/fusion-compare/expansions/order-source.txt` | The same on the source test. | generated by tools/order_shuffle.py |
| `docs/fusion-compare/expansions/source-aramaic.txt` | source_eval.py's measures of the source test with the Aramaic dictionary's forms. | generated by tools/source_eval.py |
| `docs/fusion-compare/expansions/source-base.txt` | source_eval.py's measures of the source test with no extra forms. | generated by tools/source_eval.py |
| `docs/fusion-compare/expansions/source-lexical-shuffled.txt` | source_eval.py's measures of the source test with the lexical side's candidates shuffled. | generated by tools/source_eval.py |
| `docs/fusion-compare/expansions/source-spelling.txt` | source_eval.py's measures of the source test with the full/defective spelling forms. | generated by tools/source_eval.py |
| `docs/fusion-compare/expansions/source-synonyms.txt` | source_eval.py's measures of the source test with the synonyms. | generated by tools/source_eval.py |
| `docs/fusion-compare/expansions/source_expansions.html` | The source test's page with every extra-forms run's orderings as columns of their own. | generated by tools/merge_runs.py and tools/make_html.py |
| `docs/fusion-compare/fusion_compare.html` | Self-contained Hebrew page: for each of the 1,156 searches of the feedback packages up to 2026-11-08, every ordering's first ten side by side, with the feedback's grades and Claude's. *Note:* It holds the feedback's queries (no sender ids) and passages of the lines' text, here by the owner's decision. | generated by tools/make_html.py |
| `docs/fusion-compare/otzaria_test/feedback-empty.txt` | In how many of the feedback's 1,156 searches each lexical side (Pardes's and Otzaria's five modes) finds nothing, three lines or fewer, and its median. | generated by tools/run_otzaria.sh |
| `docs/fusion-compare/otzaria_test/feedback-graded.txt` | graded_compare.py with Otzaria's modes as the lexical side, every ordering, on the graded feedback. | generated by tools/graded_compare.py |
| `docs/fusion-compare/otzaria_test/source-all.txt` | source_eval.py's measures of the source test with Otzaria's `all` mode as the lexical side. | generated by tools/source_eval.py |
| `docs/fusion-compare/otzaria_test/source-exact.txt` | The same with the `exact` mode. | generated by tools/source_eval.py |
| `docs/fusion-compare/otzaria_test/source-gram.txt` | The same with the `gram` mode. | generated by tools/source_eval.py |
| `docs/fusion-compare/otzaria_test/source-pardes.txt` | The same with Pardes's own lexical side, for comparison. | generated by tools/source_eval.py |
| `docs/fusion-compare/otzaria_test/source-smart.txt` | The same with the `smart` mode. | generated by tools/source_eval.py |
| `docs/fusion-compare/otzaria_test/source-smartspell.txt` | The same with `smartspell` (Otzaria's smart search with spelling forms as a tier). | generated by tools/source_eval.py |
| `docs/fusion-compare/source_test/queries.tsv` | The test's 157 quotes: id, group, type, query, the target line and its book and reference, and its lexical and dense ranks. | Pardes's own; the queries written by Claude |
| `docs/fusion-compare/source_test/results-100.txt` | source_eval.py's measures on a random 100 of the quotes the lexical side finds. | generated by tools/source_eval.py |
| `docs/fusion-compare/source_test/results-137.txt` | source_eval.py's measures on the 137 quotes the lexical side finds in its first ten. | generated by tools/source_eval.py |
| `docs/fusion-compare/source_test/source_compare.html` | The source test's page: every quote with every ordering's first ten side by side, the source marked. | generated by tools/make_html.py |
| `docs/fusion-compare/tools/CategoryStub.kt` | Stand-in for SeforimLibrary core's `Category` without its annotations, which MeaningFacets.kt reads, for the harness build. | Pardes's own |
| `docs/fusion-compare/tools/ExtraForms.kt` | For the measurement build only: a query word's extra forms from a table, the ones some line holds, the most common first, placed before its dictionary forms (-DextraForms, -DextraFormsIndex, -DextraFormsKeep). | Pardes's own |
| `docs/fusion-compare/tools/FeedbackHistory.kt` | The harness: runs searches through the app's own engines (LuceneSearchEngine through PardesLexicon, HybridSearchEngine) and writes each search's lexical and dense candidates with their scores and the app's page as JSON lines. | Pardes's own |
| `docs/fusion-compare/tools/LoggerStub.kt` | The app's logger without Sentry, enough for the semantic package outside the app. | Pardes's own; as pardes-semantic's eval-100/harness/LoggerStub.kt |
| `docs/fusion-compare/tools/OtzariaLexical.kt` | Otzaria's lexical search on Pardes's text index in five modes (smart, exact, gram, all, and smartspell of its pieces), for measuring it in place of Pardes's. | Pardes's own; ports otzaria_search_engine's hebrew_query.rs (8ef29fd, MIT): generate_spelling_variations, affix_arms, suffix_sides |
| `docs/fusion-compare/tools/SpellingForms.kt` | The full/defective spelling forms of every word of the given searches, by Otzaria's rules, as an extra-forms table for ExtraForms.kt. | Pardes's own |
| `docs/fusion-compare/tools/analyze.py` | The numbers of ANALYSIS.md from fuse_all.py's output: what each list is made of and the measures by the feedback's grades. | Pardes's own |
| `docs/fusion-compare/tools/build.sh` | Builds the harness with a standalone Kotlin compiler, outside Gradle, against this checkout's search code (SeforimLibrary's search sources, the semantic package, LongQuerySearchEngine, PardesLexicon), fetching its jars from Maven Central. | Pardes's own; after pardes-semantic's search-order/build.sh |
| `docs/fusion-compare/tools/eval100_methods.py` | eval-100 (pardes-semantic's expected sources) over fuse_all.py's orderings: the source first, in three, in ten and MRR@10, exact and in the same siman. | Pardes's own |
| `docs/fusion-compare/tools/evalreal_score.py` | eval-real over fuse_all.py's orderings: the first line good, a good line in three, nDCG@3, the share judged, and the sign test against the app before p12wl. | Pardes's own |
| `docs/fusion-compare/tools/expansion_tables.py` | The Aramaic and synonym extra-forms tables, from Klei Kodesh's dictionary data as Pardes ships it. | Pardes's own |
| `docs/fusion-compare/tools/extra_forms_patch.py` | Patches the harness build's copy of LuceneSearchEngine.kt to take ExtraForms.kt's forms; fails loudly when the code it changes is gone. | Pardes's own |
| `docs/fusion-compare/tools/feedback_searches.py` | The searches and grades of a feedback export as the harness reads them, with claude_grades.tsv where the feedback has none. | Pardes's own |
| `docs/fusion-compare/tools/fetch_library.py` | Streams a SeforimLibrary release's bundle and keeps what the search needs (seforim.db, its index, lexical.db, release_info.txt), never storing the bundle. | Pardes's own |
| `docs/fusion-compare/tools/fetch_pack.py` | Streams a semantic pack of Pardes's releases (the model and the vector index) into the library's folder. | Pardes's own |
| `docs/fusion-compare/tools/fuse_all.py` | Every ordering measured (the app, RRF variants, score fusions, Otzaria's, p12 and its variants, and more) on the harness's candidates, writing each search's first ten and fifty for the pages and the measures. | Pardes's own; uses pardes-semantic's search-order/order_common.py and eval-real/level_order.py |
| `docs/fusion-compare/tools/graded_compare.py` | The feedback's graded searches, every lexical side and ordering side by side: the first line good, good lines, nDCG and MRR, the share judged, the sign test, and the lines left to grade. | Pardes's own |
| `docs/fusion-compare/tools/make_html.py` | The page of an orderings.json: every search, every ordering's first ten side by side. | Pardes's own |
| `docs/fusion-compare/tools/merge_runs.py` | Several fuse_all.py runs side by side on one page, each run's orderings as columns of their own. | Pardes's own |
| `docs/fusion-compare/tools/order_shuffle.py` | How much each side's order decides the app's ranking: its fusion recomputed with one side's candidates shuffled, reversed or cut. | Pardes's own |
| `docs/fusion-compare/tools/run.sh` | The whole run from a feedback export to the page. | Pardes's own |
| `docs/fusion-compare/tools/run_expansions.sh` | The extra-forms runs (Aramaic, synonyms, spelling) on the source test and the graded feedback, and the shuffles. | Pardes's own |
| `docs/fusion-compare/tools/run_otzaria.sh` | Otzaria's lexical search against Pardes's, on the source test and the graded feedback. | Pardes's own |
| `docs/fusion-compare/tools/run_source.sh` | The source test from its queries to the numbers and the page. | Pardes's own |
| `docs/fusion-compare/tools/run_tests.sh` | The search's tests that need no Compose (the semantic package's and PardesLexicon's), compiled with kotlinc against build.sh's build and run with JUnit 4, outside Gradle. | Pardes's own |
| `docs/fusion-compare/tools/source_eval.py` | Where every ordering puts each quote's source: strict and lenient, first, in three and in ten, MRR@10, by group, and the searches the lexical side misses. | Pardes's own |
| `docs/fusion-compare/tools/source_searches.py` | The source test's searches from its queries, for the harness and fuse_all.py. | Pardes's own |
| `docs/fusion-compare/tools/swap_lexical.py` | A harness run with another lexical side (one of OtzariaLexical.kt's modes) in place of Pardes's. | Pardes's own |

#### The feedback service

| File | What it is | Where it comes from |
|---|---|---|
| `feedback-service/.gitignore` | Keeps node_modules/, wrangler's .wrangler/ state and the local .dev.vars secrets out of git. | Pardes's own |
| `feedback-service/README.md` | Hebrew guide to Pardes's feedback service, a Cloudflare Worker with a D1 database: what it keeps (each package as sent, and whether its sender agreed to publish it) and what not (no IP, user agent, headers or request log), its routes and limits, the site at its root ("מאגר החיפושים הפתוח": what the packages add up to shown to everyone; the words of a package sent with "publish": true, as the search data sender tool, the site's uploads and Pardes with "פרסום במאגר הפתוח" checked send them, shown to everyone; the fields a row of another app carries, and their lengths; any other package's searches and graded results opaque ids on the public route, their words shown only to the maintainer after the admin sign-in; each sender an alias of a digest of its id; uploads of package files, published; /admin leads to it), how to delete a package that should not be there, the one-time Cloudflare setup (the account, its workers.dev subdomain, an API token, the repository's secrets) and how the maintainer reads the packages (pardes-semantic's fetch.py). *Note:* Written in Hebrew. Zayit has no counterpart. | Pardes's own |
| `feedback-service/migrations/0001_packages.sql` | First D1 migration: table `packages` (sender, received_at, app and library versions, counts of grades, searches and daf marks, size, the JSON body) with an index on sender and time, and table `deletions` (the senders whose packages were deleted, for the maintainer's fetch). | Pardes's own |
| `feedback-service/migrations/0002_public.sql` | Second D1 migration: column `public` of `packages` (0 or 1, 0 by default), whether the sender agreed to publish the package ("publish": true), which index.js sets on receiving it and reads for the public route. Packages from before stay 0. | Pardes's own |
| `feedback-service/package-lock.json` | npm lockfile (v3) of the service's dev dependencies, wrangler 4.151.2 and miniflare 5.20261019.0-beta, and their dependencies. *Note:* Miniflare is pinned at a beta version. | generated by npm |
| `feedback-service/package.json` | npm manifest of the private package pardes-feedback (ES modules, Node 22 or later): the scripts dev (wrangler dev), test (node --test) and deploy (wrangler deploy), and the dev dependencies wrangler and miniflare. | Pardes's own |
| `feedback-service/src/index.js` | The Worker: POST /v1/packages keeps a checked package (at most 1,000,000 bytes, 200 a day per sender, 20 packages or deletions a minute per address through the LIMITER binding), with `public` 1 when it says "publish": true; DELETE /v1/senders/:id deletes a sender's packages and records it; GET /v1/public/packages gives everyone the packages (at most 50 a request, 300 requests a minute per address through READ_LIMITER), each with `public` and each sender, in the package too, the first 6 bytes of a SHA-256 of its id in hex: a published package as published cuts it (the words, scope, options and graded place of its searches and grades, nothing else a sender added), any other as forEveryone cuts it (each search and graded result an opaque id, an HMAC-SHA-256 under a key from ADMIN_TOKEN, in place of its words, scope and place), every row with its opaque id (queryId, resultId) so the site counts a row sent both ways once, the daf reviews with each mark's own fields (its line and words, right or wrong, a move, what is wrong with it, `layout` or `words`, of which of its words, `from` and `to`, and the layout's `version` it was made on) and nothing a sender added to one; the maintainer's GET routes list the packages with `public` (with their bodies on request, at most 50) and return one with the ADMIN_TOKEN, and GET / serves the site (site.js), to which /admin leads. *Note:* Plain JavaScript, while Zayit's own web code (website/) is TypeScript. Any error answers a bare 500, with nothing logged. | Pardes's own |
| `feedback-service/src/package.js` | What a package must be: format 1 of app "Pardes", a UUID sender, sentAt, optional versions, an optional boolean "publish" (the sender's consent to show its words to everyone), and lists of grades (0, 2 or 3), searches (a pasted passage too) and daf reviews with their marks (a mark's two reasons true or false, the words they are of and the layout's version counts), a row of another app's with its `source`, `scope`, `ref`, `timeUnknown`, `bookTitle`, `heRef` and `docId` within their lengths, every field checked by type and length, and a refusal that names the row and the reason; exports MAX_BYTES, MAX_PER_DAY, MAX_QUERY and SENDER. | Pardes's own; follows the app's FeedbackPackage.kt format |
| `feedback-service/src/site.js` | The site's route (GET /): serves its page, script, style and font, which hold no data, with a CSP that runs no script but its own and reaches no other address, no framing and no referrer; the page and script uncached, the font for 30 days; /admin redirects to it. | Pardes's own; replaces the maintainer's page (admin.js) |
| `feedback-service/src/site/OFL-Frank-Ruhl-Libre.txt` | The SIL Open Font License 1.1 of Frank Ruhl Libre (Copyright 2015 The Frank Ruhl Libre Project Authors), the site's display font. | Frank Ruhl Libre's licence, as published |
| `feedback-service/src/site/app.js` | The search data site's script (1,131 lines). Everyone sees what the data adds up to, read page by page from the public route: a summary (searches, grades, senders, daf lines, the days of the last 30, the grades by search model, the app of each row: Pardes, and Zayit and Otzaria through the search telemetry tool); the common searches and the searches' and grades' tabs from published packages only (their tabs shown once there are any, with a word that the rest are counted but not shown); the daf marks' table (each line with what its reader said is wrong with it, its setting on the page or its words, of which of its words, and on which version of the layout; the tractates' counts of each) with search, sorting and a CSV download; the packages as the public route gives them; and an upload of package files, each posted with "publish": true under a line that says what is uploaded is published (split into parts of at most 900,000 bytes, waiting out the rate limit). Rows are counted once across senders, and across a published and an unpublished copy by their opaque ids, showing the words. The admin sign-in (the admin token, kept in the browser only if asked) reads the maintainer's route and shows every search. It shows no sender, alias or id, and every text from a package only as text. | Pardes's own; replaces the maintainer's page (admin/app.js) |
| `feedback-service/src/site/display.woff2` | Frank Ruhl Libre, subset to Hebrew, digits and punctuation (binary), the font of the site's headings and searches, served by the Worker itself. | Frank Ruhl Libre (SIL OFL 1.1), subset for the site |
| `feedback-service/src/site/index.html` | The open site's page, "מאגר החיפושים הפתוח" (Hebrew, right to left, light and dark): its masthead (the figures open to everyone, the searches only from those who agreed to publish them), the admin sign-in's button and dialog, the tabs and the sections the script fills, and a footer saying the same; no data in it. | Pardes's own; replaces the maintainer's page (admin/index.html) |
| `feedback-service/src/site/style.css` | The open site's styles, in no app's branding: an index of a library rather than an admin panel, a Hebrew serif title, figures between hairlines, sticky tabs, one techelet accent and a chart hue checked for contrast in light and dark, the admin sign-in's dialog, and the upload's note that what is uploaded is published. | Pardes's own; replaces the maintainer's page (admin/style.css) |
| `feedback-service/test/service.test.js` | node:test suite that runs the Worker in Miniflare with every migration applied in order: the site at the root and nothing else without a route, keeping and listing a package with its counts and bodies, the site with no data in it, its CSP, its upload posting with "publish": true and saying so, the public route without a search's words and with each sender an alias that does not give its id away, a package's words shown to everyone only with "publish": true (and the same opaque ids on both forms, a mixed pair from one sender, `public` for the maintainer), paging, refusing non-packages with the row and reason (a "publish" that is not true or false too, a daf mark whose reason is not true or false, whose word is below 0 or whose version is no number, and a row of another app whose scope or reference is too long or whose app, time flag or own id is of the wrong type), a daf mark's reasons and words shown to everyone without what a sender added to it, the token on the maintainer's routes, deleting a sender's packages (told to everyone by its alias), the daily limit and the per-address rate limit. | Pardes's own |
| `feedback-service/wrangler.toml` | Wrangler configuration of the Worker pardes-feedback: src/index.js, compatibility date 2026-10-01, workers.dev on, observability off (no request logs), the D1 binding DB with migrations/, the LIMITER rate limit of 20 packages or deletions a minute and READ_LIMITER's of 300 public reads a minute, and rules that bundle the site's page, script and style as text and its font as data. *Note:* Holds no account id; its D1 database id is all zeros, written in by feedback-service.yml for its run only. | Pardes's own |

#### The Nucleus fork (the commentators' tear-off)

| File | What it is | Where it comes from |
|---|---|---|
| `nucleus-fork/README.md` | Explains why Pardes for desktop takes one Nucleus module, nucleus.decorated-window-tao, from a fork of Nucleus on Pardes's GitHub account (branch commentator-tearoff) as 3.0.0-dev-202610272317-1-tearoff in maven/: `Modifier.satelliteTearOffHandle`, `SatelliteDragOrigin.HostElement` and pointer-icon parameters for the commentator tear-off; how to update it, and when to delete it. *Note:* The fork changes no native code, and every other Nucleus module stays Zayit's pin from Maven Central. To be deleted once Nucleus has the API. | Pardes's own |
| `nucleus-fork/maven/dev/nucleusframework/nucleus.decorated-window-tao/3.0.0-dev-202610272317-1-tearoff/nucleus.decorated-window-tao-3.0.0-dev-202610272317-1-tearoff.jar` | The fork's build of nucleus.decorated-window-tao (1,063 classes and the release's native libraries for macOS, Windows and Linux, 6.3 MB), read through the exclusive NucleusFork repository of settings.gradle.kts (`nucleusTao` in libs.versions.toml); as the higher version it wins over the tao the other Nucleus modules ask for. *Note:* A binary committed to git without LFS, in two versions (0447981b, then 9de93f98). | built from Pardes's fork of Nucleus (MIT) and copied in by nucleus-fork/vendor.py; replaces at build time Maven Central's nucleus.decorated-window-tao 3.0.0-dev-202610272317, Zayit's pin |
| `nucleus-fork/maven/dev/nucleusframework/nucleus.decorated-window-tao/3.0.0-dev-202610272317-1-tearoff/nucleus.decorated-window-tao-3.0.0-dev-202610272317-1-tearoff.module` | Gradle module metadata of the vendored tao: version 3.0.0-dev-202610272317-1-tearoff, apiElements and runtimeElements on the jar alone, and the Nucleus dependencies (decorated-window-core, core-runtime, energy-manager) at the release 3.0.0-dev-202610272317, with angle-natives 8037.1, Compose desktop 1.12.1 and Kotlin stdlib 2.4.20. | generated by the fork's Gradle publishing, rewritten by nucleus-fork/vendor.py (dependencies set back to the release, sources and javadoc variants dropped) |
| `nucleus-fork/maven/dev/nucleusframework/nucleus.decorated-window-tao/3.0.0-dev-202610272317-1-tearoff/nucleus.decorated-window-tao-3.0.0-dev-202610272317-1-tearoff.pom` | Maven POM of the vendored tao, with the fork's version and the same dependencies as its Gradle module, for tools that do not read Gradle metadata. | generated by the fork's Gradle publishing, rewritten by nucleus-fork/vendor.py |
| `nucleus-fork/vendor.py` | Copies the fork's tao jar, POM and Gradle module from Maven Local into nucleus-fork/maven after the fork's scripts/publish-tearoff-local.sh, setting every other Nucleus dependency back to the base release 3.0.0-dev-202610272317 and keeping only the jar's variants. *Note:* Deletes and rewrites nucleus-fork/maven on every run. | Pardes's own |

#### The open siddur's engine (`siddur-open/`)

A copy of SeforimOpenSiddur by Ariel Daniely (https://github.com/arieldaniely/SeforimOpenSiddur, AGPL v3 with the §7b
term) at commit `cbf530cc` (2026-11-08, version 0.1.0), which the app's experimental smart siddur is built on
(section 2, "The open smart siddur"). Its sources, tests, data and tools are as their author wrote them, but for the
custom of an ordinary weekday on the State of Israel's days, which Pardes added on 2026-11-11 (three files changed,
one test added; `siddur-open/README.md`).

| File | What it is | Where it comes from |
|---|---|---|
| `siddur-open/README.md` | English note on the copy: whose engine it is and that he offered it (efrul/Pardes#27), the pinned commit, date and version, what is copied as it is and what is left out, why a copy and not a submodule or a package, how the module is built (Pardes's Kotlin and catalog versions, ktlint and detekt off), what Pardes changed in the copy and when (the ordinary-weekday custom), where the app uses it, the three licences, and the steps of an update. It carries the line "Powered by the technologies that drive Zayit". | Pardes's own |
| `siddur-open/build.gradle.kts` | The `:siddur-open` module: Kotlin Multiplatform with a JVM target on the catalog's toolchain, `src/main/kotlin` added to `jvmMain` (where the engine's own build keeps its sources), kotlinx-datetime as `api` and KosherKotlin as `implementation` from the catalog, Kotlin Test and coroutines for the tests, and every ktlint and detekt task of the module turned off. *Note:* the engine's own build script (Kotlin 2.3.21, literal versions, `jvmToolchain(25)`) is not copied; the two dependencies are at the same versions in Zayit's catalog. | Pardes's own; follows `luach/build.gradle.kts` and the engine's build script |
| `siddur-open/UPSTREAM_README.md` | The engine's own README, in Hebrew: what it builds, its sources and rules (the three siddurim and the machzorim of the library, KosherKotlin for the calendar, Amud's conditions with their commit, Hebcal Leyning's table with its commit, the Selichot by nusach), how to run its tests and regenerate its two data sets, its coverage (a reconstruction, partial for local customs, not compared in full with Zayit's closed engine) and its licences. *Note:* it speaks of the author's own app, Zayita. | SeforimOpenSiddur's `README.md` at `cbf530cc`, renamed |
| `siddur-open/LICENSE` | The engine's licence: GNU AGPL v3 with the §7b attribution term and Elie Gambache's copyright line, the same bytes as this repository's `LICENSE`. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/COPYING.AMUD` | GNU GPL v3's text: the licence of the conditions that come from Amud (`amud-annotations.tsv`, `amud-inline.tsv`). The build copies it into the app's licences folder as `GPL-3.0.txt`. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/COPYING.HEBCAL` | Hebcal's BSD 2-Clause licence (Copyright (c) 2020, hebcal): the licence of the data the readings' table is made from. The build copies it into the app's licences folder as `Hebcal-LICENSE.txt`. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/jvmMain/resources/open-siddur/amud-annotations.tsv` | 342 rows of nusach, SHA-256 of a paragraph of the library's siddur, and the condition under which it is said (flags joined by `&` and `|`, such as `sat`, `minyan`, `torah_reading`): whole paragraphs. It holds no text of the siddur. | SeforimOpenSiddur at `cbf530cc`, unchanged; made by `tools/import_amud_annotations.py` from Amud's corpus (GPL v3) and the library |
| `siddur-open/src/jvmMain/resources/open-siddur/amud-inline.tsv` | 46 rows of the same kind for paragraphs said in part: ranges of characters inside the paragraph, each with its condition. | SeforimOpenSiddur at `cbf530cc`, unchanged; made by the same tool |
| `siddur-open/src/jvmMain/resources/open-siddur/readings.tsv.gz` | The table of readings, gzipped: by the kind of year (14), Israel or abroad, the day's offset and the service, the occasion's name and the passages of the Torah reading, the haftarah, the Sephardi haftarah and the megillah (book, first and last verse, aliyah). Binary. | SeforimOpenSiddur at `cbf530cc`, unchanged; made by `tools/generate_readings.mjs` from Hebcal Leyning (BSD 2-Clause) |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/SiddurBook.kt` | The engine's public model: `Nusach`, `SiddurPart` (24 parts with their options and `familyOf`), `SiddurPrefs`, `SiddurLine` (text, heading, note or slot, with its reading, molad announcement or lulav instruction), `BookSection`, `SourceBook`, `SourceHeading`, the `SiddurSource` the app implements, the marks of the day's additions and of what is skipped, `civilDayOf`, `siddurOfTheHour` and each nusach's siddur title. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/DayBook.kt` | `buildDayBook`: the civil day's parts (Selichot in season, Shacharit, the day's meal, Mincha, Neila) and the night's that follows (Chanukah candles, Kabbalat Shabbat, Maariv, Havdala, the omer, the night's meal, Kiddush Levana, the bedtime Shema), each a `BookSection` built from the siddur's outline or a machzor's, with its flags, readings and options. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/SiddurCalendar.kt` | `dayPartFor` (the weekday, Shabbat or festival form of a tefila) and `flagsFor`: the day's flags from KosherKotlin's calendar and `TefilaRules` (Shabbat, festival, Rosh Chodesh, fasts, Tachanun, Hallel, the season's insertions, the omer, Chanukah, the Israeli days and so on). | SeforimOpenSiddur at `cbf530cc`; changed by Pardes on 2026-11-11: in `flagsFor`, a custom that does not keep the State's days gives them none of their flags and leaves Tachanun as on any weekday, on the day and at Mincha before it |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/CorpusSections.kt` | `Outline`, a source book by its headings, and the keys that map each tefila to the headings of the library's siddur or machzor (`baseKeys`, `machzorTitle`, `machzorKeys`). | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/CorpusAnnotations.kt` | `annotateCorpus`: marks the siddur's lines as said, special or skipped by the instructions written in the text itself, and the choices among them (`selectMigdol`, `splitMealAdditions`, `selectFood`). | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/AmudAnnotations.kt` | `AmudAnnotations`: reads the two Amud tables and gives a paragraph's condition, or its ranges' conditions, by the SHA-256 of the library's text. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/CalendarPassages.kt` | The passages the calendar adds or chooses inside a tefila (`calendarPassages`), Hoshienu split by the day, and the bedtime Shema's passages. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/ShabbatTransitions.kt` | Kabbalat Shabbat by the day, Maariv of Motzaei Shabbat, Havdala by its flags, and the festival's Kiddush. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/ShabbatAnnouncements.kt` | The coming Rosh Chodesh and announced fast, and the announcements' lines filled in (the month, its days, the molad). | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/SukkotPrayers.kt` | The lulav's blessings, the day's Hoshanot and the cues of the shaking inside Hallel. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/Selichot.kt` | `hasSeasonalSelichot` and the Selichot book and section of a day of Elul or of the ten days, and of a minor fast, by nusach (Lita for Ashkenaz, Polin for Sefard, Edot HaMizrach). | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/TorahReadings.kt` | `readingsFor`: the Torah reading, maftir, haftarah and megillah of a service from the readings' table, with `ReadingKind`, `VerseAddress`, `ReadingPassage` and `SiddurReading`. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/IsraeliOccasion.kt` | `IsraeliOccasion` and `israeliOccasion`: Yom HaZikaron, Yom HaAtzmaut (moved as the Independence Day Act says, by the year) and Yom Yerushalayim. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/IsraeliPrayerCustom.kt` | `IsraeliPrayerCustom`: the customs of those days and when each says Hallel: the engine's four (no Tachanun alone, Hallel without blessings, Hallel with them by day, and by night too), and before them `ORDINARY_WEEKDAY`, under which the days are weekdays like any other (`keepsTheDays` false). | SeforimOpenSiddur at `cbf530cc`; changed by Pardes on 2026-11-11: the fifth custom, `keepsTheDays`, and `saysHallel` false under it |
| `siddur-open/src/main/kotlin/io/github/kdroidfilter/seforimapp/siddur/IsraeliHallel.kt` | Hallel and the added prayers of those days in Maariv and Shacharit, by the custom. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/siddur/CalendarPassagesTest.kt` | Ten tests of the passages chosen by the calendar, on small made-up source books. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/siddur/DayBookTest.kt` | Ten tests of the day's book: its parts and their order on ordinary and special days, on made-up source books. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/siddur/IsraeliOccasionTest.kt` | Four tests of the Israeli days' dates over the years and of the Hallel of the customs that keep them. | SeforimOpenSiddur at `cbf530cc`; changed by Pardes on 2026-11-11: the loop over the customs takes those that keep the days |
| `siddur-open/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/siddur/SelichotTest.kt` | Three tests of the season's and the fasts' Selichot by nusach. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/siddur/SukkotPrayersTest.kt` | Three tests of the lulav's blessings, the Hoshanot and the shaking's cues. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/siddur/TorahReadingsTest.kt` | Six tests of the readings: weekdays, Shabbat, festivals, the Sephardi haftarah and the walled city. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/siddur/OrdinaryWeekdayCustomTest.kt` | Three tests of the custom Pardes adds: under it Yom HaAtzmaut and Yom Yerushalayim have the flags of the weekdays beside them, with Tachanun, no Hallel and no added haftarah; Mincha of the day before keeps its Tachanun and the day before has no name, while the customs that keep the days are as they were; and the engine's own default is unchanged. | Pardes's own; written as the engine's tests beside it (on `flagsFor`), its tests named as Zayit names them |
| `siddur-open/tools/generate_readings.mjs` | Node script that makes `readings.tsv.gz` from pinned versions of @hebcal/leyning, @hebcal/core and @hebcal/hdate. *Note:* kept as the source of the table; the build never runs it. | SeforimOpenSiddur at `cbf530cc`, unchanged |
| `siddur-open/tools/import_amud_annotations.py` | Python script that makes the two Amud tables: it matches Amud's corpus to the paragraphs of the library's three siddurim and writes the hashes and conditions of those matched without doubt. *Note:* kept as the source of the tables; the build never runs it. | SeforimOpenSiddur at `cbf530cc`, unchanged |

#### Scripts

| File | What it is | Where it comes from |
|---|---|---|
| `scripts/kleikodesh_dictionary.py` | Packs Klei Kodesh's dictionary data, from a clone of its KitveiHakodeshDictionary repository, into SeforimApp/src/jvmMain/resources/kleikodesh/dictionary.tsv.gz for the Home's dictionary card (Android copies it): the headword indexes of Metzudat Tzion, Malbim's Biur HaMilot, the Mikropedia Talmudit and Machberet Menachem, the synonyms and spellings, Torat Emet's Aramaic-Hebrew dictionary and Hebrew Wikipedia's acronyms; not HaMichlol's definitions (left out on 2026-11-05) nor Otzaria's lists or sources of unknown origin. *Note:* Zayit's scripts/ holds font and test-feed helpers only. Reads the clone as data, runs none of its scripts, and writes a deterministic gzip (mtime 0). | Pardes's own; the data is used with Klei Kodesh's permission, some of it CC BY-SA |

#### What is left of semantic-model/

| File | What it is | Where it comes from |
|---|---|---|
| `semantic-model/NOTICE.md` | The credit of the semantic search's model: Dicta's neodictabert-bilingual-embed under CC BY 4.0, what Pardes changed (fine-tuned and distilled when `pardes-model.json` says derived, converted to ONNX with pooling and L2 in the graph, int8), and that the vector index was computed with it. Pardes for Android shows it in Settings › רישיונות. | Pardes's own; Dicta's attribution terms |
| `semantic-model/README.md` | Hebrew note that the model moved on 2026-11-05 to its own repository, efrul/pardes-semantic (its pipeline, tests, results and the models as releases), and what stays here: NOTICE.md and release_notes.py. | Pardes's own |
| `semantic-model/release_notes.py` | The title and notes of a semantic pack's release, from the pack's manifest: semantic-release.yml publishes a new pack with them and semantic-notes.yml writes them again; they say "the files of the search by meaning", never "a pack". | Pardes's own |

### Pardes for Android (`androidApp/`)

A separate Gradle build that compiles SeforimLibrary's core and dao, and named desktop files, unchanged, and draws
desktop's screens again in Jetpack Compose. "Port of desktop X" names the Zayit file a Kotlin file redraws or
rewrites for Android; "Android variant" a file kept in the same package and class name as desktop's, which the
build leaves out for it.

#### The build

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/.gitattributes` | Git attributes of the Android build: `*.sh` and `gradlew` keep LF and `*.cmd` keeps CRLF on every checkout, so the transfer scripts run in Git Bash and WSL, and the Windows launcher stays CRLF when a Linux checkout packages it. | Pardes's own |
| `androidApp/.gitignore` | Ignores the Android build's local outputs: `.gradle/`, `.kotlin/`, `build/`, `local.properties`, `*.apk` and `*.db`. | Pardes's own |
| `androidApp/app/build.gradle.kts` | Build script of the app: a Kotlin Multiplatform `androidTarget` with the Android application plugin that compiles SeforimLibrary's core and dao, its search and delta-updater sources (less the files that have Android variants here), desktop's htmlparser and icons from their folders as they are, and six Sync tasks that copy named desktop files unchanged (`syncPatchTables`, `syncDesktopSemantic`, `syncDesktopSearch` with the typed references and the snippets, `syncDesktopZmanim` with the region lists and candle lighting, `syncDesktopSemanticTests`, `syncParityFixtures`). It sets the id `io.github.efrul.pardes`, minSdk 26, compile and target SDK 36, version 0.9.0 (code 11), the CMake build of the multiplex extension, the debug key or the `PARDES_KEYSTORE` release key, R8, ONNX Runtime for the 64-bit ABIs only and compressed native libraries. `LicenceAssets` and `DesktopTextAssets` copy LICENSE, Lucene's licence and notice, the model's NOTICE, temple_count.txt, comment-anchors/ and kleikodesh/ into the assets; lucene-core is substituted by `:lucene-android`; and it declares the SQLDelight databases `SeforimDb` (the library's schema) and `UserSettingsDb` (desktop 0.6.10's user schema and Pardes's two folders). *Note:* It reads named files from desktop's SeforimApp/src and from SeforimLibrary, so moving or renaming one of them there breaks the Android build. | Pardes's own |
| `androidApp/app/lint.xml` | Android Lint configuration: ignores `NewApi` in desktop's `LuceneSearchEngine.kt`, which lint reads from SeforimLibrary's search folder although the build leaves it out for its Android variant. | Pardes's own |
| `androidApp/app/proguard-rules.pro` | R8 rules of the release build: file names and line numbers kept for crash reports; the optional dependencies of jsoup, KosherJava and commons-compress silenced; zstd-jni kept; the kotlinx.serialization serializers of the core and dao models kept; Lucene's classes found by name kept (codecs and formats through META-INF/services, analysis factories, attribute implementations, the class names in the index's attribute keys), each group checked with lucene-android/smoke; `AndroidCompatApi33` kept as a class of its own (Android 11's verifier rejected `GroupVIntUtil` once R8 had moved its VarHandle calls there); and `ai.onnxruntime.**`, which ONNX Runtime's JNI finds by name. | Pardes's own |
| `androidApp/build-logic/build.gradle.kts` | Build of the included build-logic: a `java-gradle-plugin` that registers `pardes.compose-vector-moves` (`ComposeVectorMovesPlugin`), compiled for Java 17 against AGP's API and ASM 9.9.1, both compile-only because AGP brings them at run time. | Pardes's own |
| `androidApp/build-logic/settings.gradle.kts` | Settings of build-logic: its repositories (Google limited to its own groups, Google's Maven Central mirror, Maven Central) and the Android build's version catalog. | Pardes's own |
| `androidApp/build-logic/src/main/java/io/github/kdroidfilter/seforimapp/buildlogic/ComposeVectorMovesFactory.java` | AGP `AsmClassVisitorFactory` that rewrites Compose's `ImageVector$Builder`: `addPath` and `addGroup` first pass each `List` parameter (the path and clip-path data) through `VectorPathCompat.absoluteMoves`; the build fails when `addPath` is gone (a Compose update renamed it). *Note:* Java, where Zayit's build logic (buildSrc) is Kotlin. | Pardes's own |
| `androidApp/build-logic/src/main/java/io/github/kdroidfilter/seforimapp/buildlogic/ComposeVectorMovesPlugin.java` | Gradle plugin `pardes.compose-vector-moves`: on every variant of the app it transforms all classes, the dependencies' too, with `ComposeVectorMovesFactory` (frames copied). Below Android 13 Skia moves a relative moveTo that follows close() from the contour's last point, so most Material Symbols icons were drawn askew (seen on Android 11). *Note:* Java, as its factory. | Pardes's own |
| `androidApp/build.gradle.kts` | Root build script of the Android Gradle build: the Kotlin Multiplatform, kotlinx-serialization, Compose compiler, Android application and SQLDelight plugins declared with `apply false`. | Pardes's own |
| `androidApp/debug.keystore` | The project's shared debug signing key (alias `androiddebugkey`, CN=Pardes Android Debug), which app/build.gradle.kts signs debug builds with instead of each machine's own, so builds from any machine install over each other. *Note:* Public by design (its password is the standard `android`); releases are signed with a key kept only in the repository's secrets (`PARDES_KEYSTORE_*`). | Pardes's own |
| `androidApp/gradle.properties` | Gradle properties of the Android build: a 3 GB Gradle heap and 2 GB Kotlin daemon, the build cache on, parallel off, AndroidX and non-transitive R classes, and `android.experimental.disableCompileSdkChecks=true`, because kosherkotlin-android 3.0.5 (desktop 0.6.10's zmanim engine) declares minCompileSdk 37 while AGP 8.12 stops at 36. | Pardes's own |
| `androidApp/gradle/libs.versions.toml` | Version catalog of the Android build: Kotlin 2.4.20 and SQLDelight 2.3.2 as desktop, AGP 8.12.3 (desktop 9.1.1), the Compose BOM 2026.03.00, desktop's kosherkotlin 3.0.5, hebrewnumerals, zstd-jni and commons-compress versions, KosherJava zmanim 2.5.0, requery's sqlite-android 3.49.0 (the multiplex VFS), ONNX Runtime 1.22.0 (desktop runs 1.17.1; 1.22.0 is the first whose two Android libraries are 16 KB aligned), org.json for the JVM tests and the test runners. *Note:* jsoup 1.22.1, kotlinx-serialization 1.10.0 and coroutines 1.10.2, where desktop has 1.23.2, 1.11.0 and 1.11.0. | Pardes's own |
| `androidApp/gradle/wrapper/gradle-wrapper.jar` | Gradle's wrapper jar of the Android build. *Note:* Not the root build's jar, which is Zayit's. | Gradle's wrapper (Apache-2.0) |
| `androidApp/gradle/wrapper/gradle-wrapper.properties` | Pins the Android build to Gradle 9.1.0 (the binary distribution), with the wrapper's default settings. *Note:* Desktop's build, in the repository root, runs Gradle 9.8.0. | Gradle's wrapper |
| `androidApp/gradlew` | Gradle's POSIX wrapper script for the Android build (kept LF by `.gitattributes`). *Note:* There is no `gradlew.bat`: on Windows the build runs from Git Bash. | Gradle's wrapper (Apache-2.0) |
| `androidApp/settings.gradle.kts` | Settings of the Android build: the root project `ZayitAndroid`; `includeBuild("build-logic")` for the `pardes.compose-vector-moves` plugin; the repositories (Google limited to its own groups, Google's Maven Central mirror, the plugin portal, Maven Central, and JitPack for requery's bundled SQLite only); and the modules `:app` and `:lucene-android`. *Note:* The Gradle project keeps the name ZayitAndroid from before the app became Pardes; users never see it. | Pardes's own |

#### Documents

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/CHANGELOG.md` | Pardes for Android's user-facing changelog in Hebrew, one `## <version> · <date>` section per release, eleven from 0.2.0 (2026-10-16) to 0.9.0 (2026-11-05). android-release.yml takes a version's section as its release notes and fails without one; android-site.yml publishes it as changelog.md, which the in-app updater (`AppReleases`) and the site read. *Note:* Hebrew by design: text for users. | Pardes's own |
| `androidApp/DESIGN.md` | Design spec of the Android app: desktop's Islands colour tokens (light and dark, the gold accent), shapes and fonts; how the desktop layout maps to a phone and a tablet by window size; the screens one by one (onboarding, Home, the library tree, the reader and its panes, search, settings, tools); and the data notes. | Pardes's own |
| `androidApp/README.md` | README of Pardes for Android: what it is (based on Zayit by Elie Gambache, not on his behalf), building and signing, every way to get a library onto a device, the library on a memory card, the in-app updater, the source layout, the `zayit://` links, the known differences from desktop, and the licences and credits. | Pardes's own |
| `androidApp/docs/DEVELOPMENT.md` | Developer notes of the Android app: the goal and constraints (Android 8.0+, parity with desktop Zayit 0.6.12, an app of its own based on Zayit, offline, desktop's code reused unchanged), the status by area, screenshots, how the work is done (committing as Zayit does, with the change map's rows), Windows, signing, testing the app update, Android 8 to 12, the semantic search, the decisions and why, and the known gaps. | Pardes's own |
| `androidApp/docs/LIBRARY-LOCATION.md` | The plan of the library's location (מיקום הספרייה): what Android allows (the phone, a memory card's app folder, not USB or SAF folders), the facts that shape it (FAT32's 4 GiB, a removed card ending the app, uninstalling), and the steps. *Note:* Implemented since 2026-10-16, as its status line says; the rest is the plan of 2026-10-15, kept for what it found. | Pardes's own |
| `androidApp/docs/PARITY.md` | The parity list against desktop Zayit 0.6.12: what was ported from each desktop release since 0.6.1, with the upstream commits and the Android files; Pardes for desktop's own features ported (the semantic search, categories, the commentators' groups, Klei Kodesh, the title index, the lexicon, which is behind desktop's version 2); what cannot be ported from public sources; what differs on purpose (the authorship texts); and the items by area and severity. | Pardes's own |

#### The code: entry points

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/MainActivity.kt` | The single activity: the startup check (`checkStartup`: stale files and downloads cleared, a missing memory card asked for, a staged semantic pack swapped in, the library checked, the session read, a pending incremental update resumed), then `OnboardingFlow`, `DatabaseUpdateScreen`, `LibraryCardMissingScreen` or the shell `ZayitApp`, and `SemanticPackPrompt` on a plain start. It takes `zayit://` links, launcher shortcuts, widget taps and the update notification once each (`take`, `handledBefore`, kept on disk per boot and task), hardware-keyboard shortcuts, the touches that keep the reading time counting, and saves the session in `onStop`. | Port of desktop 0.6.10's main.kt (`computeStartupState`, the session), with the Android entry points |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/PlatformCompat.kt` | Fallbacks for platform calls newer than API 26 made in several places: `showForegroundImmediately` (`setForegroundServiceBehavior`, API 31), `packageInfoCompat` and `packageArchiveInfoCompat` (the `PackageInfoFlags` overloads, API 33) and `InputStream.readAtMost` (`readNBytes`, API 33). | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/VectorPathCompat.kt` | `VectorPathCompat.absoluteMoves`: below Android 13, a relative moveTo that follows a close() becomes an absolute one to the same point, because the Skia of Android 11 and 12 moves it from the contour's last point and drew most Material Symbols icons out of place. The build makes Compose's `ImageVector.Builder` call it for every icon (build-logic). *Note:* Its one function is about 90 lines (detekt's LongMethod: 60). | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ZayitApplication.kt` | The `Application`: app-wide singletons (`libraryStore`, `prefs`, `userData`, `history`, `search`), the app scope, a memory-card receiver that closes the library and starts the app again when the card holding it goes (`mediaChanges`, `restart`), a foreground tracker, the flags `manualLibraryUpdate` and `semanticPackSwapPending`, and `SemanticSearchSupport.init`. *Note:* Singletons held by the Application instead of Zayit's Metro graph; the class keeps Zayit's name from before the rename. | Pardes's own: the Android counterpart of desktop's Metro AppGraph |

#### The code: launcher widgets (appwidget/)

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/appwidget/WidgetData.kt` | What the launcher widgets show: `WidgetDay` (the place's date, zmanim, Shabbat, coming events and limudim, worked out once a day), `WidgetCache`, `WidgetData` (one redraw: the clock, the next twelve zmanim, the limudim picked on the Home under desktop's `home_widget.limud.shown` key, the Temple count, the user's font size) and `WidgetPalette` with its RemoteViews colour helpers. | Pardes's own (Android only), from the same data/ functions as the Home's widgets |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/appwidget/WidgetLaunch.kt` | What a tap on a launcher widget opens, as the Home's widget would (`WidgetLaunch`: the Home, the widget's tool page, or a limud's passage in a new tab): its `PendingIntent`, `widgetLaunchOf(intent)`, and `open`, above whatever the app shows. | Pardes's own; the passage as desktop's LuachWidgets `rememberOpenInLibrary` opens it |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/appwidget/WidgetProviders.kt` | The six launcher widgets' `AppWidgetProvider`s (`ZayitWidgetProvider`: zmanim, next zman, luach, limud, Temple, search) and `WidgetRefreshReceiver`; each hands its broadcast to `ZayitWidgets`, with no polling. | Pardes's own (desktop has no system widgets) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/appwidget/WidgetRenderers.kt` | Draws each launcher widget as RemoteViews at the size the launcher gives (`WidgetKind.render`): the zmanim with the Shabbat lines, the next zman, the luach day and its events, the limudim, the Temple count and the search bar, each with a one-row form (`lineViews`, `fitLine`) that picks the fullest wording that fits; API 31. | Pardes's own: the wording, numbers and colours of desktop 0.6.10's Home widgets as Pardes ports them |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/appwidget/ZayitWidgets.kt` | The launcher widgets' engine: `WidgetKind` (provider, fallback size, whether it turns with the day), `WidgetRequest`, and `ZayitWidgets`, which draws on its own thread, merges the requests that arrive during a redraw, holds a broadcast open only within its time budget, draws only the widgets asked at the launcher's sizes, and sets one inexact alarm for the next change (midnight or the next zman); nothing below Android 12. | Pardes's own |

#### The code: data (data/)

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/AppPrefs.kt` | The user's preferences on SharedPreferences (`zayit_settings`) under desktop's AppSettings keys, stored values and defaults (theme, accent, fonts and sizes, region and community, commentators per book, the widgets, the search split...), with the portable export and import of backups, a one-time `migrate` of older Android keys, `resetAll`, a staged restore run first (`PendingUserDataRestore.runOnce`) and a listener that redraws the launcher widgets (`WIDGET_KEYS`); also the `Community` enum. | Port of desktop 0.6.10's core/settings/AppSettings.kt (its keys, values and defaults), on SharedPreferences |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/CatalogAccess.kt` | The Home catalog's dropdowns: per-category titles and book lists from catalog.pb, or from the database's tree, with desktop's display rules (`CatalogIds`), `BookRef`, read once per library off the main thread. | Port of desktop 0.6.10's core/catalog/CatalogAccess.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/CommentatorGrouping.kt` | Groups a line's commentators into desktop's buckets ("ראשונים על התנ״ך"...) in editorial order, chronologically inside each bucket (`group`, `resolveGroupLabel`), so the checklist reads as desktop's. | Port of the grouping in desktop 0.6.10's features/bookcontent/usecases/CommentariesUseCase.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/CommentatorSelection.kt` | `CommentatorSelection`, the commentators a book's lines show, kept for the book: checked one by one, whole groups, "כל המפרשים", the groups hidden under it, the ones unchecked inside a checked group; saved by `AppPrefs`. | Port of Pardes for desktop's features/bookcontent/state/CommentatorSelection.kt, without its ContentState extensions |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/DictionaryIndex.kt` | The Home dictionary card's data: the library's dictionaries, each parsed by its own rule (`Dictionary.parse`), normalised and looked up as desktop does (`normalizeWord`, `lookup`), with Klei Kodesh's dictionaries, a disk cache of the parsed entries (v3) and each indexed entry checked against its line. | Port of the data part of desktop 0.6.10's features/home/widgets/library/DictionaryWidget.kt, with Pardes for desktop's Klei Kodesh additions |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/FavoritesStore.kt` | Favourites in the `favorite` and `favorite_folder` tables of user_settings.db: add, remove, folders, `keyFor`, a `revision` flow, and `toggle` for the reader's star; the UPSERT goes through UpsertLegacy.sq on Android 8-10. *Note:* Its KDoc calls it a verbatim port, but desktop's multi-line declarations are folded onto single lines. | Port of desktop 0.6.10's core/favorites/FavoritesStore.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/HebrewText.kt` | `HebrewText`: a numeral's value in letters (`value`), a Gregorian date in Hebrew (`gregorian`) and `normalize`, which drops nikud, teamim and quotes so titles compare loosely, for the tools. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/HistoryStore.kt` | The visit history in the `visit_history` table of user_settings.db: entries deduplicated by target key (books, TOC entries, searches with their scope: author, category, base books only, several), `upsertVisit` with Android 8-10's two statements, listing and clearing. *Note:* Reformatted as FavoritesStore.kt. | Port of desktop 0.6.10's core/history/HistoryStore.kt (0.6.5's author scope) with Pardes for desktop's category keys |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/JewishDay.kt` | `JewishDay`, the day's calendar facts and prayer rules for the siddur fallback (KosherKotlin's `TefilaRules` and desktop's wordings: the sefira, yom tov and Rosh Chodesh names, the molad), and `ZmanimDay`, the day's zmanim in order. | Pardes's own: desktop's smart siddur is a private package |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/KleiKodeshDictionary.kt` | Klei Kodesh's dictionary data read from the APK's assets (`KleiKodeshPack`: kleikodesh/dictionary.tsv.gz): the headword indexes of the library's books, the text dictionaries, the synonyms and spellings, and the entries made from them (`indexedEntries`, `textEntries`, `linesToRead`). | Port of Pardes for desktop's features/home/widgets/library/KleiKodeshDictionary.kt, reading assets instead of resources |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/LibraryInstallService.kt` | Foreground data-sync service that keeps the process alive while `LibraryInstaller` downloads or unpacks the library, with its progress in a notification worded as the desktop window of the flow that started it (onboarding or the database update) and a cancel action. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/LibraryInstaller.kt` | The library's installation: SeforimLibrary's release bundle (`seforim_bundle.tar.zst` parts), or local parts, a .tar.zst or a bare seforim.db, streamed through sha256, zstd and tar into place with HTTP Range resume (`ResumingHttpInputStream`), written under `.part` names and renamed at the end; free space, the version (`DatabaseVersion`, desktop's minimum) and the result checked; also updates from files and split databases for FAT32 cards. | Pardes's own, in place of desktop's download and extract use cases; `DatabaseVersion` follows desktop's framework/database/DatabaseVersionManager.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/LibraryLocation.kt` | `LibraryLocation`: a place the library can be kept in (the phone or a memory card, always the app's own Android/data folder), with its label and free space. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/LibraryLookup.kt` | Finds books by title (exact, then loosely) and TOC sections by their heading path, for the siddur fallback and the Shnayim Mikra reader. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/LibraryMoveService.kt` | Foreground data-sync service that keeps the process alive while `LibraryMover` copies the library between the phone and a memory card, with its progress and a cancel action; `MoveUiStrings`. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/LibraryMover.kt` | Moves the library, and the semantic pack's two folders, between the phone and a memory card: seforim.db in chunks on FAT32, each copy checked by size and by the checksum of its end once its cached pages are dropped (`PageCache`, cache.c), the old copy deleted only then, and the app started again; also switching to, or deleting, a library found in the other place. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/LibraryStore.kt` | Finds the installed library (desktop's file names: seforim.db, the two Lucene indexes, lexical.db, catalog.pb, release_info.txt) among its places, remembers the one chosen, reports a missing card, adopts files pushed into the folder, and opens the shared `SeforimRepository` on Android's driver inside `PragmaSafeDriver`, or on requery's SQLite (`SplitDatabase.usesBundledSqlite`). | Pardes's own; the file names as desktop's AppCoreBindings |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/LimudCycles.kt` | The daily learning cycles of the limud widget (Mishnah Yomit, Perek Yomi, the Rambam, Sefer HaMitzvot, Tehillim, Nach Yomi, Pirkei Avot, the Kitzur, Aruch HaShulchan, Chofetz Chaim, Shmirat HaLashon): the unit of a date and its place in the library. | Copy of Zayit 0.6.10's features/home/widgets/luach/LimudCycles.kt, identical but for the package and `Math.ceilDiv` (Java 18) written with floorDiv |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/LimudSchedules.kt` | Reads limud.pb (hebcal-learning's schedules in one protobuf) for the limud cycles, from the raw resource the app hands in (`LimudResources`). | Port of desktop 0.6.10's features/home/widgets/luach/LimudSchedules.kt, from an Android raw resource instead of the class path |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/LuachData.kt` | What the luach widgets show: the Hebrew date, the day's limudim (`limudOfDay`) and tefila changes, the coming events, the named zmanim and the next ones, the countdowns, the molad and the Kiddush Levana window, and the library lookups that open a day's places (`resolvePlace`). | Port of desktop 0.6.10's features/home/widgets/luach/LuachData.kt and the library lookups of LuachWidgets.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/Measures.kt` | The measures converter's data: the Torah's lengths, the opinions of their size today and of a mil's walk, each with its source line in the library, and the number formats. | Port of the data part of desktop 0.6.10's features/home/widgets/measures/MeasuresWidget.kt, verbatim by its header |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/PragmaSafeDriver.kt` | Wraps Android's SQLDelight driver so the shared `SeforimRepository` runs unchanged: its row-returning PRAGMAs go through `executeQuery`, and desktop's cache and mmap sizes are cut for a phone. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/ReadingHistory.kt` | The reader's link to the history and favourites with desktop's recording rules (one history row per book opened in a tab, `ReadingEntry` titles such as "בראשית - פרק א"), the star's toggle, and a one-time move of the history, favourites and Shnayim Mikra ticks older Android builds kept elsewhere. | Pardes's own, after the recording rules of desktop 0.6.10's BookContentViewModel |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/ReadingTime.kt` | `LastTouch` (the last touch, so the reading time stops five minutes after it) and `ReadingTimeStore`, which counts each book's reading time per day in `reading_time` while its tab is in front, for the learning split card. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/SearchScope.kt` | `SearchScope`, where a search runs: the library, a category (base books only or not), an author, several books and categories (`Multi`), a book or a TOC entry. *Note:* The navigation module is not compiled into the Android app, hence the copy. | Copy of Pardes for desktop's navigation/.../tabs/SearchScope.kt (Zayit's, with Pardes's `Category` and `Multi`) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/SessionStore.kt` | `SavedSession` and `SessionStore`: every workspace with its tabs, pins, selection and reading positions in files/session/session_v1.json, written and read only while "שמור מצב בין הפעלות" is on, and the book tabs' titles filled from the library. *Note:* JSON, where desktop writes a protobuf file. | Pardes's own: the Android subset of desktop 0.6.10's framework/session/SessionModels.kt `DesktopsState` |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/ShnayimMikra.kt` | The week's parsha by aliyot (`mikraWeek`, in Israel or abroad), the aliyot's verses and ranges, and their places in the library, for the Shnayim Mikra card and tool. | Port of the data part of desktop 0.6.10's features/home/widgets/luach/ShnayimMikraWidget.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/ShnayimMikraStore.kt` | The aliyot read of each parsha by Hebrew year, in the `shnayim_mikra_aliya` table shared with desktop, with a `revision` flow. | Copy of desktop 0.6.10's core/shnayimmikra/ShnayimMikraStore.kt, identical but for its KDoc |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/SiddurData.kt` | The siddur fallback's data: `SiddurNusach` (the library's siddur books of each nusach), `SiddurPart` (the prayers and the TOC headings they are under), `SiddurSection`, and `SiddurData`, which finds a prayer's section for the day. | Pardes's own: desktop's smart siddur is a private package |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/SplitDatabase.kt` | A library database kept in multiplex chunks for a FAT32 memory card (`CHUNK_BYTES`, `ChunkedOutputStream`), opened through requery's SQLite with libpardes_multiplex.so; and `usesBundledSqlite`, which reads every library below Android 9 through that SQLite for its wide cursor windows. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/SqlVariableLimit.kt` | Keeps an id list bound into one `IN ?` within SQLite's 999 parameters before 3.32 (Android 8 to 11) by splitting it, and runs one statement as before on Android 12 and later. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/TorahText.kt` | The verses of an aliya with their Targum Onkelos (`VerseText`, `parseRef`), for the Shnayim Mikra tool's reader. | Pardes's own: desktop opens the Chumash in its Shnayim Mikra reading mode instead |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/UserDataBackup.kt` | The user's data as desktop's backup archive (a zip of manifest.json with the portable preferences and user_settings.db), exported as `pardes_backup_<date>.zip`, checked, and staged to replace the data at the next start (`PendingUserDataRestore`); below SQLite 3.27 the database is copied under the write lock instead of `VACUUM INTO`. | Port of desktop 0.6.10's framework/backup/UserDataBackup.kt and PendingUserDataRestore.kt, in the same format |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/UserDataStore.kt` | user_settings.db with desktop's schema on one connection: the notes and highlights (`UserNote`, `UserHighlight`, cached per book), the categories' diacritics, the history, favourites, Shnayim Mikra and reading-time stores, `guardedWrite`, `clearAll` and the adoption of an imported desktop database. | Pardes's own, after desktop 0.6.10's NoteStore, HighlightStore and CategoryDisplaySettingsStore |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/Zmanim.kt` | `City` and `userCity` (the region setting's place, Jerusalem when unset, as desktop's HomeUserLocationViewModel), and the Temple countdown: `TempleCount` (Rashi's, the Rambam's or Tosafot's count), `computeTempleCountdown`, `templeCountdownAt` and `millisUntilNextJerusalemSunset`. | Port of desktop's HomeUserLocationViewModel place rules and of features/home/widgets/temple/TempleCountdownWidget.kt (0.6.8's TempleCount) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/data/ZmanimEngine.kt` | The zmanim on KosherKotlin 3.0.5, to match desktop to the minute: `ZmanimTimes`, `ZmanimOpinion`, `computeZmanimTimes`, the Ohr HaChaim sunset, the Kiddush Levana opinions, Shabbat candle lighting and havdalah, and the luach each community follows. | Port, verbatim by its header, of desktop 0.6.10's EarthWidgetZmanimView.kt and KiddushLevanaData.kt (earthwidget), ZmanimWidget.kt and HomeWidgetsState.kt |

#### The code: incremental library updates (delta/)

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/CatalogUpdater.kt` | `CatalogUpdater`: takes the new catalog.pb out of patch.db's blobs with android.database and swaps it in atomically (temporary file, sync, rename). | Android variant of SeforimLibrary's delta-updater/src/jvmMain/.../deltaupdater/CatalogUpdater.kt (7ddf045, unchanged up to the pinned 4f07145), same package and class name, android.database instead of JDBC |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/DbDeltaUpdateService.kt` | Incremental library updates for the app: `DbDeltaUpdateState`, `DbDeltaUpdateEvents`, `DeltaStrings` (the section's texts in Hebrew) and `DbDeltaUpdateService`, one object for the process, which asks release_meta.json first and else SeforimLibrary's GitHub releases, offers an update with its size, downloads and applies it (`DeltaUpdaterClient`), updates from files picked offline, refuses where the phone cannot (`deltaRefusal`), and writes the release's timestamp into release_info.txt. *Note:* Desktop's section texts are English literals; `DeltaStrings` gives them in Hebrew, with strings.xml's wording where it has one. | Port of desktop 0.6.10's framework/update/DbDeltaUpdateService.kt with features/settings/dbupdate/DbDeltaUpdateViewModel.kt and DbDeltaUpdateState.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/DeltaApplierClient.kt` | `DeltaApplierClient`: the gates around `PatchApplier` (schema version, content hash before and inside the transaction, free space with `NotEnoughSpaceException`, a marker that is also a lock and records the stage); it applies on the live database, without desktop's 8 GB seforim.db.backup copy, and resumes forward after the commit. | Android variant of SeforimLibrary's delta-updater/src/jvmMain/.../deltaupdater/DeltaApplierClient.kt (7ddf045, unchanged up to the pinned 4f07145), same package and class name |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/DeltaDownloader.kt` | `DeltaDownloader`: downloads each patch file to `<file>.part` checked by sha256, keeps a file already right, resumes with HTTP Range and deletes a mismatch; the bytes come from the app's `ResumingHttpInputStream`. | Android variant of SeforimLibrary's delta-updater/src/jvmMain/.../deltaupdater/DeltaDownloader.kt (7ddf045, unchanged up to the pinned 4f07145), same package and class name, same contract |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/DeltaPaging.kt` | Keyset paging over android.database (`forEachByIntKey`, `forEachByRowValue`), so the delta code reads big tables page by page in their exact ORDER BY order rather than in one query a CursorWindow would replay. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/DeltaSqlite.kt` | android.database plumbing for the delta code: `WIDE_WINDOW_BYTES` (16 MB cursor windows), `queryWide` and `openLibraryDatabase` (a connection of its own beside the repository's). | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/DeltaUpdaterClient.kt` | `DeltaUpdaterClient`: the facade over the applier, the downloader, the Lucene and catalog updaters and the orchestrator (`recoverIfNeeded`, `checkForUpdate`, `applyChain`), with `fetchReleaseMeta` telling a missing file from up to date, `choosePath` preferring one direct patch of the release, and `pendingChain` for a half-applied delta. | Android variant of SeforimLibrary's delta-updater/src/jvmMain/.../deltaupdater/DeltaUpdaterClient.kt (7ddf045, unchanged up to the pinned 4f07145), same package and class name |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/LibraryDeltaService.kt` | Foreground data-sync service that keeps the process alive while `DbDeltaUpdateService` downloads and applies a delta, with its progress and, until the database part is committed, a cancel action. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/LogicalContentHasher.kt` | `LogicalContentHasher`: the logical sha256 of seforim.db (the tracked tables in key order, cells tagged by type, byte for byte as the JDBC version streams them) that must match the manifest's content hashes, read page by page with progress and cancellation. | Android variant of SeforimLibrary's generator/common/src/jvmMain/.../common/patch/LogicalContentHasher.kt (7ddf045, unchanged up to the pinned 4f07145), same package and class name |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/LuceneDeltaSinks.kt` | Where a delta's line changes go into seforim.db.lucene: one `IndexWriter` per delta on `NIOFSDirectory`, the documents written as SeforimLibrary's `LuceneTextIndexWriter.addLine` writes them (with `text_ng4` and `ancestor_category_ids`), segments over 512 MB left unmerged, committed only by the session. *Note:* Its header records that Zayit's sink writes a reduced document (no `text_ng4`, no `ancestor_category_ids`, no points on line_index), which the release's index refuses, and that Pardes for desktop writes the full one since 2026-11-09 (framework/update/LuceneLineDocument.kt). | Port of desktop 0.6.10's `defaultLuceneSinksProvider` (framework/update/DbDeltaUpdateService.kt), with the index writer's own documents |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/LuceneUpdater.kt` | `LuceneUpdater`: reads a patch's deleted and upserted lines, and every line of a book whose metadata alone changed, page by page into the sinks; its `SinkSession` gains `commit`, so a partial pass is rolled back, not committed on close. | Android variant of SeforimLibrary's delta-updater/src/jvmMain/.../deltaupdater/LuceneUpdater.kt (7ddf045, unchanged up to the pinned 4f07145), same package and class name |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/PatchApplier.kt` | `PatchApplier`: applies a patch.db onto the live seforim.db in one transaction (ATTACH, deferred foreign keys, migrations, upserts, deletes, foreign-key and content-hash checks, COMMIT) through android.database, with cancellation between statements. | Android variant of SeforimLibrary's generator/common/src/jvmMain/.../common/patch/PatchApplier.kt (7ddf045, unchanged up to the pinned 4f07145), same package and class name, same steps and SQL |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/PatchDbSchema.kt` | `PatchDbSchema`: the client side of the patch schema (`CURRENT_VERSION`, `readTableInfo` with `ColumnInfo.pk`) through android.database. | Android variant of SeforimLibrary's generator/common/src/jvmMain/.../common/patch/PatchDbSchema.kt (7ddf045, unchanged up to the pinned 4f07145), same package and class name |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/delta/UpdateOrchestrator.kt` | `UpdateOrchestrator`: turns an `UpdatePath.Chain` into applied deltas with desktop's steps and statuses (download into a stable `delta-v<from>-v<to>` folder, verify, decompress, apply, Lucene, catalog, record), resuming from the stage marker and able to finish a delta offline from its kept manifest. | Android variant of SeforimLibrary's delta-updater/src/jvmMain/.../deltaupdater/UpdateOrchestrator.kt (7ddf045, unchanged up to the pinned 4f07145), same package and class name |

#### The code: search (search/)

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/AcronymFrequencyCache.kt` | `AcronymFrequencyCache`: how many books use each acronym (the `book_acronym` table), read from seforim.db with the platform SQLite, read-only, or through requery's SQLite for a split library or below Android 9. | Android variant of Zayit 0.6.10's SeforimApp/src/jvmMain/.../framework/search/AcronymFrequencyCache.kt (same package and class), without JDBC |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/LibraryCategories.kt` | `LibraryCategories`: every category of the library with its path and its books, all of them or the base books alone, read once per library from the catalog and one query of the links. | Port of Pardes for desktop's features/search/domain/LibraryCategories.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/LuceneLookupSearchService.kt` | `LuceneLookupSearchService`: the book, TOC and author suggestions over seforim.db.lookup.lucene (title tiers, words typed once), asking Pardes's title index first, plus `authorsOfBook`; the index opened with `NIOFSDirectory` (Android has no MMapDirectory) and a smaller query cache. | Android variant of Zayit 0.6.10's SeforimApp/src/jvmMain/.../framework/search/LuceneLookupSearchService.kt (same package and class), with Pardes for desktop's title-index calls |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/LuceneSearchEngine.kt` | The full-text `LuceneSearchEngine` (Hebrew normalisation, the dictionary's expansions, snippets, facets, the found lines' ranges) with the Android changes its header lists: `NIOFSDirectory`, lexical.db looked up beside the index only, at most four searcher threads, facets that stop when cancelled (`computeFacetsCancellable`), lexical.db closed with it, and a clause limit of 16,384. | Android variant of SeforimLibrary's search/src/jvmMain/.../LuceneSearchEngine.kt at 4f07145 (same package and class; the build leaves the original out) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/MagicDictionaryIndex.kt` | `MagicDictionaryIndex`: the streaming dictionary of word forms over lexical.db (surface, variant, base) with its cache and prefetch, read with the platform SQLite, read-only, instead of JDBC connections per thread, and closed with the engine. | Android variant of SeforimLibrary's search/src/jvmMain/.../MagicDictionaryIndex.kt (unchanged upstream since 381fbea), same API, SQL and caches |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/PardesLexicon.kt` | `PardesLexicon`: builds pardes-lexical.db beside lexical.db in the background (bases without forms dropped, each base's forms ranked by how many indexed lines hold them); the search reads it in place of lexical.db once it is ready. *Note:* At version 1: desktop's is 2 since 2026-11-09 (the defective spellings), not ported yet (androidApp/docs/PARITY.md); a desktop copy on the phone is rebuilt at version 1. | Android variant of Pardes for desktop's framework/search/PardesLexicon.kt: the platform SQLite and NIOFSDirectory |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/ScopeItems.kt` | The search bar's picked books and categories (`ScopeItem`, `ScopeItems`): each once, in the order picked, and the scope they make, in Android's own `SearchScope`. | Android variant of Pardes for desktop's features/search/domain/ScopeItems.kt (same package and names) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/SearchEntityFinder.kt` | What a search names, for the panel beside its results (`SearchEntityFinder`: a book or an author named exactly, compared by desktop's `searchKey`), `BookDetailsLoader` (categories, authors and their years, main parts, editions), the authors' eras and years (`AUTHOR_ERAS`, `authorYears`) and `plainPersonLinks`. | Port of Zayit 0.6.10's features/search/domain SearchEntityFinder.kt and BookDetailsLoader.kt, and features/author/AuthorScreen.kt's eras |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/SearchService.kt` | `SearchService`: opens desktop's search on the installed library when first used (the full-text engine, the lookup, the acronyms, Pardes's title index and lexicon), inside `HybridSearchEngine` when a semantic pack is beside the library and the device can run it, opens again when the library changes, reports the index's state, and adds `SearchEngine.countFacets`, which stops when cancelled. | Pardes's own, after Pardes for desktop's AppCoreBindings (the search engine, the semantic search, the lookup) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/SemanticPackManager.kt` | `SemanticPackManager`: the state of the semantic pack card in Settings › ניהול נתונים (the pack beside the library, the newest release) and installing one, downloaded or from picked files, to go in at the next start; one per process, kept alive by `SemanticPackService`. | Port of Pardes for desktop's features/settings/semantic/SemanticPackViewModel |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/SemanticPackService.kt` | Foreground data-sync service that keeps the process alive while `SemanticPackManager` downloads or unpacks a pack of some 2 GB, with the card's wording in its notification. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/SemanticSearchSupport.kt` | `SemanticSearchSupport`: whether the device can run the semantic search (a 64-bit process, not a low-memory device), read once at start. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/TitleIndex.kt` | Builds Pardes for desktop's title index (books and authors by name) once per library, off the main thread, from the catalog and three queries, as desktop's AppCoreBindings builds it. | Pardes's own; the index's classes are desktop's, compiled unchanged (syncDesktopSearch) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/reference/RepositoryReferenceSource.kt` | `RepositoryReferenceSource`: the `ReferenceSource` over the repository; a TOC entry's lines are read in lists Android 8-11's SQLite can bind (`SqlVariableLimit`), a section reaching 9,400 lines. | Android variant of Zayit 0.6.10's SeforimApp/src/jvmMain/.../features/search/domain/reference/RepositoryReferenceSource.kt (same package and class) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/BookLevels.kt` | `BookLevels` (a book's level, from Mikra to the latest authors, for the precedence of the earlier level among the lines that hold the query as a sentence) and `LibraryLines`, read through the app's own `SqlDriver` instead of a JDBC connection. | Android variant of Pardes for desktop's features/search/semantic/BookLevels.kt (same package and names; changes marked "Android:") |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/HfTokenizer.kt` | `HfTokenizer`: the open model's HuggingFace tokenizer.json read and applied in Kotlin (normalizers, pre-tokenizers, WordPiece, post-processor); Oniguruma's Unicode `\w`, `\d` and `\s` spelled out, because Android's ICU regex refuses `UNICODE_CHARACTER_CLASS`. | Android variant of Pardes for desktop's features/search/semantic/HfTokenizer.kt (same package and names; changes marked "Android:") |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/HybridSearchEngine.kt` | `HybridSearchEngine`: the full-text engine fused with the dense search by RRF (the model's dense weight, the lexical leaders that hold a quote, the earlier level first), snippets refined and highlighted by meaning, the reader's smart find, the fallback to words alone, and `lexicalEngine` for the facets; it logs with android.util.Log and reads the library through the app's connection. *Note:* Its fusion is RRF alone: desktop's `fusedScores` (2026-11-09) is not ported. | Android variant of Pardes for desktop's features/search/semantic/HybridSearchEngine.kt (same package and names; changes marked "Android:") |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/IndependentSemanticSearch.kt` | `IndependentSemanticSearch` and `DenseHit`: the KNN search of pardes.vec.lucene (HNSW) opened with `NIOFSDirectory`, with the model looked for beside the index only, and no legacy model. | Android variant of Pardes for desktop's features/search/semantic/IndependentSemanticSearch.kt (same package and names; changes marked "Android:") |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/OnnxEmbeddingProvider.kt` | `OnnxEmbeddingProvider`: embeds a query, and passages of found lines, with the int8 ONNX model on onnxruntime-android into the vector the index is searched with; no legacy model. | Android variant of Pardes for desktop's features/search/semantic/OnnxEmbeddingProvider.kt (same package and names; changes marked "Android:") |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/OpenModelSpec.kt` | `OpenModelSpec`: the open model's pardes-model.json (its id, files, cleaner, prefixes and dense weight), read with `Files.readAllBytes`, since Android 8 has no `Files.readString`. | Android variant of Pardes for desktop's features/search/semantic/OpenModelSpec.kt (same package and names; changes marked "Android:") |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/VectorIndexBuilder.kt` | The read side of `VectorIndexBuilder`: the vector index's names and `readInfo` (the model it was embedded with, its lines), which the search and the pack installer check; building the index stays a desktop tool. | Android variant of Pardes for desktop's features/search/semantic/VectorIndexBuilder.kt (same package and names; changes marked "Android:"), without the builder |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/pack/InstalledPack.kt` | `InstalledPack`: the semantic pack beside a library as the settings show it, checked as the search opens it, plus `libraryVersion` from release_info.txt. | Android variant of Pardes for desktop's features/search/semantic/pack/InstalledPack.kt (same package and names; changes marked "Android:") |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/pack/PendingSemanticPack.kt` | `PendingSemanticPack`: puts a staged pack's two folders in place at the next start, before the search opens (the old moved aside, the new moved in, the old deleted, rolled back on failure), the request kept in the app's files folder. | Android variant of Pardes for desktop's features/search/semantic/pack/PendingSemanticPack.kt (same package and names; changes marked "Android:") |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/pack/SemanticPackInstaller.kt` | `SemanticPackInstaller` and `PackProblem`: installs a pack into the staging folder beside the library, read part by part as it downloads (sha256 of each) or from files picked with the system picker, with desktop's manifest, model and index checks and its estimate of the room needed. | Android variant of Pardes for desktop's features/search/semantic/pack/SemanticPackInstaller.kt (same package and names; changes marked "Android:") |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/semantic/pack/SemanticPackReleases.kt` | `SemanticPackReleases`: the released semantic packs, from the GitHub API or, when it cannot be reached, the site's semantic.json, read with Android's `HttpURLConnection` and the app's user agent. | Android variant of Pardes for desktop's features/search/semantic/pack/SemanticPackReleases.kt (same package and names; changes marked "Android:") |

#### The code: the app's own update (update/)

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/update/AppPackages.kt` | The installed app and a downloaded APK as Android sees them (`Identity`: package, version, signing certificates, with the signing lineage from Android 9), `sameSigner` (what makes an APK an update of this app) and `isReleaseBuild`, against the release certificate's SHA-256 (`RELEASE_CERT_SHA256`, as android-release.yml checks it). | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/update/AppReleases.kt` | `AppRelease` and where Pardes finds its newer versions, as the download site does: GitHub's release list, else the site's releases.json (`android-<version>-<run>` tags, `pardes-*.apk` assets with their sha256), `parse`, `versionOfTag`, `compareVersions`, what is new from the site's changelog.md (`changesSince`) and desktop's `needsDbWarning` rule (a minor or major update may need a newer library). | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/update/AppUpdateService.kt` | `AppUpdateService`, the foreground service that keeps the APK's download and check alive (progress, cancel); `AppUpdateNotifications` (ready, failed, confirm, updated); `AppUpdateInstallReceiver` (PackageInstaller's answer) and `AppUpdatedReceiver` (MY_PACKAGE_REPLACED: the new version says "פרדס עודכן"). | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/update/AppUpdateStrings.kt` | `AppUpdateStrings`, the update's Hebrew texts (badge, dialog, banner, the settings line, the checks and errors) with the app named פרדס, plus Android's own (the permission to install apps, the background download, the APK's checks). *Note:* Text for users in Kotlin constants, where Zayit keeps it in strings.xml. | Desktop 0.6.10's strings.xml `update_*` texts (and 0.6.12's `settings_auto_update_check`), named in comments, with Android's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/update/AppUpdater.kt` | `AppUpdater` and `AppUpdateState`: the app's own update, one object for the process. It finds the newest release (`AppReleases`), downloads its APK into the cache with the library downloads' HTTP stack (Range resume, reconnects), checks the sha256, the package, the version and the signer, and hands it to PackageInstaller (replaced without asking where `UPDATE_PACKAGES_WITHOUT_USER_ACTION` allows); the badge, the dialog, the permission across Android 11's restart, no update while the library is busy, and a test feed for development builds (`files/app-update-test-feed.json`). | Port of desktop 0.6.10's framework/update/AppUpdateService.kt (Nucleus's updater) for an APK |

#### The code: screens (ui/)

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/AppState.kt` | The shell's state: `Destination` (Home, a book with its find query and notes, a search with its scope, History, Favorites, an author), `Tab`, `Workspace`, `TabState`, `Overlay`, `ToolKind`, `AppUiState`, `ShellEvent`, `TabViewModelOwner`, and `AppViewModel`: tabs opened beside their opener, replaced, closed one or many, pinned, the last 25 closed to reopen, the workspaces, tabs moved between them, the overlays, and the session saved 2 s after a change and restored at start. | Port of desktop 0.6.10's navigation TabsViewModel.kt, framework/desktop/DesktopManager.kt and DesktopSession.kt, core/presentation/tabs/TabsContent.kt and PinnedTabLabel.kt, and framework/session/SessionManager.kt, in one Android ViewModel |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/author/AuthorScreen.kt` | An author's page: the name, era and years, the books in the library by category, the other names, the biography with its sources and licence, with side panes on a wide screen; the tab named after the author, the biography's links opening their passages. | Port of desktop 0.6.10's features/author/AuthorScreen.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/components/Components.kt` | Shared Compose pieces in desktop's Islands look: `Island`, `AccentButton`, `OutlineButton`, `RailButton`, `ZIconButton`, `ZChip`, `ZCheckRow`, `ZRadioRow`, `LoadingBox`, `EmptyHint`, `InfoBanner`, `SectionLabel`, spacers and `maxContentWidth`. | Pardes's own: desktop's Jewel look drawn again in Material 3 |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/components/PinchZoom.kt` | `Modifier.pinchTextZoom`: two fingers zoom the text, in the books and in the search's preview, step by step and only while two fingers are down. | Port of desktop 0.6.5's text zoom (rememberAppTextZoom) for touch |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/components/SearchableOptions.kt` | `SearchableOptions`: a combobox's open list that narrows as the user types (a bottom sheet on a phone, a drop-down on a wide screen), for the region pickers. | Pardes's own: the touch version of desktop's SpeedSearchArea around the region ListComboBoxes |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/database/DatabaseUpdateScreen.kt` | The library update flow shown when the library is missing, outdated or broken (`DatabaseUpdateReason`, `databaseUpdateReason`): the version check, the options, a full install online or from files, an incremental update first when one is offered (Android's addition), and the end, with the installer's and the delta service's progress. *Note:* `warningColor` writes its two colours out instead of taking them from the theme. | Port of desktop 0.6.10's features/database/update (DatabaseUpdateWindow, its NavHost and five screens) and main.kt's computeStartupState rules |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/database/LibraryCardMissingScreen.kt` | Shown when the library is on a memory card that is not in the device: it waits for the card (and opens once it is back), "נסה שוב", or installs anew on the phone after a warning. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/HomeScreen.kt` | The Home page: the wallpaper under its blending layers, the catalog row with its dropdowns, the פרדס logo, the search bar and the editable widget grid (one column on a narrow screen, desktop's grid on a wide one); on a tablet `WideHome` with its book tree that can be hidden, Ctrl+B, the edit mode and Back. *Note:* The logo is Pardes's own lettering. | Port of desktop 0.6.10's features/bookcontent/ui/panels/bookcontent/views/HomeView.kt, components/CatalogRow.kt and core/presentation/window/WindowBody.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/HomeSearchBar.kt` | The Home's search bar: `UnifiedSearchBar` with its `SearchBarViewModel` for the tab; what it opens replaces the Home in the tab (a book or a place, an author, a text search, a pasted `zayit://` link), and it takes the focus only from a touch, a keyboard or an accessibility service, so the soft keyboard does not cover the page. | Port of desktop 0.6.10's HomeView UnifiedSearchBar and SearchHomeViewModel |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/ArrangeLayout.kt` | The Home's "arrange" action: the widgets laid out again, resized within their limits, in the dealing into rows and columns with the fewest empty cells, then the fewest rows; pressed again, another order. *Note:* It uses Android's own `WidgetPlacement` and `CellPitch` (`HomeWidget.kt`, `CellLayout.kt`), so the build cannot take desktop's file as it takes the others. | Copy of desktop 0.6.10's features/home/widgets/ArrangeLayout.kt (the same in 0.6.12), identical but for the package and a header |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/CalendarWidget.kt` | `CalendarWidget`: the luach's month, Hebrew or civil; a tap on a day moves every widget to it. | Port of desktop 0.6.10's features/home/widgets/calendar/CalendarWidget.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/CellLayout.kt` | The Home grid's cell model, as Launcher3's (`HOME_GRID_COLUMNS`, `HOME_GRID_ROW_HEIGHT`, `COMPACT_GRID_WIDTH`): clamping, vacancy, the reading order, the nearest and the first vacant area, reordering and resizing; and Android's `flowedInOrder` for the compact page's edits. | Copy of desktop 0.6.10's features/home/widgets/CellLayout.kt, every line kept, with Android's additions |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/DictionaryWidget.kt` | `DictionaryWidget`, the dictionaries card (one dictionary or all): a word's entry read in the card and opened in its book, Klei Kodesh's dictionaries, and the word's synonyms and spellings as tags to tap; the tool shows the same card full size. | Port of the widget part of desktop 0.6.10's features/home/widgets/library/DictionaryWidget.kt, with Pardes for desktop's Klei Kodesh additions |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/EarthWidget.kt` | `EarthWidget`, desktop's Earth scene drawn flat: the globe lit by the Sun at the shared moment (an orthographic view of earthmap.jpg), the Moon's orbit with the Hebrew days (a tap picks a day), the Moon and the Kiddush Levana arc, with the date picker and the place menu. | 2D port of desktop 0.6.10's features/home/widgets/earth/EarthWidget.kt and earthwidget's EarthWidgetZmanimView.kt (desktop draws it in 3D with Filament) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/Ephemeris.kt` | The astronomy of the Earth, sky and solar-system widgets: NOAA's solar position and the Julian day, the Moon's and the Sun's ecliptic longitudes, the sky's directions and the stars' turn, the Hebrew day and the Moon's age, the Kiddush Levana window, and the Hebrew year's events (`computeHebrewYearEvents`). | Copied from the plain-math parts of desktop 0.6.10's earthwidget (SolarPositionCalculator.kt, EarthEphemeris.kt, SkyScene.kt, EarthWidgetZmanimView.kt, SolarSystemWidgetView.kt) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/HomeWidget.kt` | The `HomeWidget` interface (key, sizes, content, options, tool page, options size), `isLowEnd`, the list of every widget that can be placed (`availableHomeWidgets`, written by hand as there is no KSP) and the default layouts. | Port of desktop 0.6.10's features/home/widgets/HomeWidget.kt (desktop's generated list written by hand) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/HomeWidgetsGrid.kt` | The editable widget grid: `CellArea` placement, Launcher3's drag-over logic (the drop outline, the reorder alarm, widgets making room), the resize frames, long-press moves in the edit mode, and below `COMPACT_GRID_WIDTH` the one- or two-column compact page with its own edit list (`CompactWidgets`). | Port of desktop 0.6.10's features/home/widgets/HomeWidgetsGrid.kt for touch, with Android's compact edit mode |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/HomeWidgetsLayout.kt` | The saved Home layout (`home_widgets_layout`) encoded and decoded on `HomeWidgetStore`, and kept readable when widgets are added or gone. | Port of desktop 0.6.10's features/home/widgets/HomeWidgetsLayout.kt, on HomeWidgetStore in place of AppSettings |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/HomeWidgetsState.kt` | `HomeWidgetsState`, what the Home's widgets share (the day and moment shown, the place and community, `targetTime`, `selectDate`, `selectLocation`), opening a passage to learn (`rememberOpenInLibrary`), the solar system's full page, and one shared state for the tools (`rememberToolsState`). | Port of desktop 0.6.10's features/home/widgets/HomeWidgetsState.kt and ToolWindows' shared state |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/LearningSplitWidget.kt` | `LearningSplitWidget` (חלוקת הלימוד): how the user's learning splits between Mikra, Mishnah and Talmud ("לעולם ישלש אדם שנותיו"), by books opened (the history), by reading time (`ReadingTimeStore`) or both; each book counts in its root category's part, and the options move or leave out categories. | Pardes's own; Pardes for desktop has had a port of it since 2026-11-05 (features/home/widgets/library/LearningSplitWidget.kt) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/LibraryWidgets.kt` | The app's own lists on the Home: `HistoryWidget`, `FavoritesWidget` and `NotesWidget`, each opening its entry in a new tab and showing as many whole rows as fit. | Port of desktop 0.6.10's features/home/widgets/library/LibraryWidgets.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/LuachMonth.kt` | A month of the luach, Hebrew or civil, for the Calendar widget and as the day picker of the tools, the siddur and the Earth widget (a bottom sheet on a phone, a popup on a tablet), with each day's yom tov or Rosh Chodesh. | Port of desktop 0.6.10's luach module (LuachMonth.kt, LuachPopup.kt) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/LuachWidgets.kt` | The luach widgets that follow the Home's day: `LimudWidget`, `NextZmanWidget`, `UpcomingEventsWidget`, `TefilaWidget` (turning at tzeit hakochavim, `tefilaDayAt`) and `MoladWidget`; the tools show the same cards. | Port of desktop 0.6.10's features/home/widgets/luach/LuachWidgets.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/MeasuresWidget.kt` | `MeasuresWidget`: a length converted between the Torah's measures, with its size today and the walking time by the opinions picked, each figure linking to its source line. | Port of the widget part of desktop 0.6.10's features/home/widgets/measures/MeasuresWidget.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/ShnayimMikraWidget.kt` | `ShnayimMikraWidget`: the week's parsha by aliyot, each ticked once read (in the user database shared with desktop) and opened in the Chumash. | Port of the widget part of desktop 0.6.10's features/home/widgets/luach/ShnayimMikraWidget.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/SkyWidget.kt` | `SkyWidget`, desktop's sky drawn in 2D: the same aim (the Sun, the Moon at night, else the south), the HYG stars of sky_stars.csv turning with the night, and the Sun's path with its rise, noon and set, on a gradient sky. | 2D port of desktop 0.6.10's features/home/widgets/sky/SkyWidget.kt and earthwidget's SkyWidgetView.kt (desktop shades it with Skia over Filament) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/SolarSystemWidget.kt` | `SolarSystemWidget` (מעגל השנה): the Earth's orbit drawn flat with every festival, fast and Rosh Chodesh of the Hebrew year where the Earth is on that day, the year stepper, the play speeds and options, and the full page that plays the year. | 2D port of desktop 0.6.10's features/home/widgets/solarsystem (SolarSystemWidget, SolarSystemOptions, SolarSystemWindow) and earthwidget's SolarSystemWidgetView.kt, without the true-scale view |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/TempleCountdownWidget.kt` | `TempleCountdownWidget`: the years, months and days since the Temple's destruction (the day turning at Jerusalem's sunset) over the burning-Temple picture (animated, still on a low-memory device or Android 8), with the options page for Rashi's, the Rambam's or Tosafot's count, explained from temple_count.txt. | Port of desktop 0.6.10's features/home/widgets/temple/TempleCountdownWidget.kt (0.6.8 and 0.6.9's changes) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/WidgetFrame.kt` | `WidgetFrame`, a widget's frame for touch: a long press opens its menu (a sheet or a menu), and in the edit mode a remove badge, a long-press drag and a resize frame selected by a tap; around a widget of the compact page `EditHeader` and `RowsGrabber`. | Port of desktop 0.6.10's features/home/widgets/WidgetFrame.kt for touch |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/WidgetGallery.kt` | The widget gallery over the bottom of the Home (a tap adds a widget), the edit pencil, the undo bar, and a widget's options as a phone sheet or a tablet dialog of desktop's size; folded on a short screen. | Port of desktop 0.6.10's features/home/widgets/WidgetGallery.kt and WidgetOptionsDialog.kt for touch |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/WidgetParts.kt` | The widgets' building blocks: `WidgetShape`, `WidgetCard`, `WidgetCardLoading`, `PanelCard`, `WidgetTitle`, `FitColumn` (a list as long as the card holds) and `HoverBox` (an area lit on press, for desktop's hover). | Port of desktop 0.6.10's features/home/widgets/WidgetCard.kt and WidgetParts.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/WidgetSettings.kt` | Desktop's typed settings (`Setting`, `SettingCodec`, `SettingsGroup`) and `HomeWidgetStore` on SharedPreferences under desktop's keys (`home_widgets_layout`, `home_widget.<id>.<name>`), each value stored as desktop's string so backups carry them both ways. | Port of desktop 0.6.10's core/settings/Setting.kt, features/home/widgets/WidgetSettings.kt and AppSettings' Home part |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/home/widgets/ZmanimWidget.kt` | `ZmanimWidget`, the day's zmanim cards (moments, the dual day and night cards, mincha from 443 dp) in five or four columns with the rows shared out evenly, shortened titles, a tap pointing every widget at a time; under 300 dp a row holds two slots. | Port of desktop 0.6.10's features/home/widgets/zmanim/ZmanimWidget.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/library/LibraryTree.kt` | The "רשימת הספרים" tree: categories that unfold (a category's books before its subcategories) and books, a book search over the catalog (`buildBookFilter`, with Pardes's title index) showing the path to each hit, the open book revealed, a tap to open and a long press for a new tab. | Port of desktop 0.6.10's features/bookcontent/ui/panels/categorytree/CategoryTreePanel.kt, CategoryBookTreeView and usecases/BookTreeSearch.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/notes/NotesScreen.kt` | "ההערות שלי": the notes of every book, grouped by book in folding cards, newest first, searchable, each opening its book at its line with the notes pane; also `UserNotes` and `rememberUserNotes`, shared with the title bar's notes menu and the Home widget. | Port of desktop 0.6.10's features/notes/NotesScreen.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/onboarding/DesktopStrings.kt` | `DesktopStrings`: the onboarding's, the database update's and the shared texts as Kotlin constants under desktop's key names in camelCase, with Pardes in place of Zayit. *Note:* Text for users in Kotlin constants, where Zayit keeps it in strings.xml. | Desktop 0.6.10's composeResources/values/strings.xml (the onboarding and `db_update_*` keys), renamed for Pardes |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/onboarding/LicenceText.kt` | `PARDES_LICENSE_URL` and `CONDITIONS_MARKDOWN`, the terms the licence step and Settings › תנאים show, under Pardes's name: based on Zayit and not on its author's behalf, the font and licence paragraphs reworded (Rashi Amiti is not in Pardes), and a privacy section true of the Android app (no reports, the update check that can be turned off). | Adapted from Zayit's composeResources/files/CONDITIONS.md |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/onboarding/LocationPicker.kt` | `LocationPicker`: with a memory card in the device, a row per place for the library (the phone, then each card) with its free space, or why it cannot take the library. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/onboarding/OnboardingComponents.kt` | The onboarding's building blocks: `StepScaffold` (the title, the scrolling content, the actions), body text, illustrations, the progress bar, the error banner, labelled fields and dropdowns, the disk pie, the confetti, `MarkdownText` and the metric rows. *Note:* `ErrorBanner` and `MarkdownText` are written again in settings/SettingsComponents.kt. | Port of desktop 0.6.10's onboarding pieces (OnBoardingScaffold and the steps' components) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/onboarding/OnboardingFlow.kt` | `OnboardingFlow`, the first run (welcome, licence, disk space with the place picker, the kind of install, the download or the offline files, unpacking, profile, region, finish) in desktop's installer frame with its progress bar, full screen on a phone and a centred card from 840 dp. | Port of desktop 0.6.10's onboarding window (the OnBoardingDestination flow, InstallerWindow) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/onboarding/OnboardingFormat.kt` | Bytes, speeds and times left as desktop's `FormatUtils` formats them (powers of 1024, Hebrew units), and `AndroidStrings`, the onboarding's Android texts (the welcome line on Pardes and Zayit, the offline install, the disk, the memory card, a metered network). *Note:* Text for users in Kotlin constants, where Zayit keeps it in strings.xml. | Pardes's own; the formats after desktop's FormatUtils |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/onboarding/OnboardingIcons.kt` | `OnboardingIcons`: the two illustrations of the steps that desktop's icons module lacks as drawn here, `FoldedMap` and `Check2Circle`; the steps take their other icons from that module, which the build compiles. | Pardes's own drawings, after Zayit's icons module |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/onboarding/OnboardingSteps.kt` | The onboarding's step screens (`InitStep`, `LicenceStep`, `DiskSpaceStep`, `InstallTypeStep`, `DownloadStep`, `OfflineFilesStep`, `ExtractStep`, `ProfileStep`, `RegionStep`, `FinishStep`) and `installErrorMessage`, worded with desktop's strings. | Port of desktop 0.6.10's onboarding screens (download, extract, offline files, profile, region) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/onboarding/OnboardingViewModel.kt` | `OnboardingViewModel`: the steps (`OnboardingStep` with their progress anchors), going back, the disk space and the place, the install's state from `LibraryInstaller`, the profile with the community codes, and the region from desktop's `worldPlaces`, saved to `AppPrefs`. *Note:* Its `Community` enum repeats the codes of data/AppPrefs.kt's. | Port of desktop 0.6.10's onboarding state (OnBoardingDestination, RegionConfigUseCase, the community codes) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/BookDetailsPane.kt` | The book-details pane: the title, categories, authors (each linking to their page), editions, licence (`licenseLabel`) and source. | Port of desktop 0.6.10's features/bookcontent/ui/panels/bookdetails/BookDetails.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/BookFindMatches.kt` | `BookFindMatches`: the find-in-page matches over the whole book in reading order (line index, id, running counts), found through Lucene with a scan as fallback (`findInBook`), and the smart find by meaning (`ofLines`) as Pardes for desktop has it. *Note:* Its header calls it a verbatim port, but desktop's code is reformatted (declarations folded onto single lines). | Port of desktop 0.6.10's features/bookcontent/usecases/BookFindMatches.kt, with Pardes for desktop's smart find |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/BookTextView.kt` | `BookText`, the book's text on the reader's lines paged by index: the selection bars, the inline alternative headings, the marked passage, the Shnayim Mikra mode, highlights and notes, find matches brought to mid-screen, the text selection with desktop's menu, the commentary anchors' passages, pinch zoom and the fast scroller, with the visible lines' connections read ahead. | Port of desktop 0.6.10's features/bookcontent/ui/panels/bookcontent/views/BookContentView.kt, with 0.6.12's commentary anchors |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/Breadcrumb.kt` | The breadcrumb (`CrumbNode`, `crumbPath`): a bar under the text on a tablet with its actions (copy link, favourite star, error report) and a menu of each segment's children, a sheet from the top bar's subtitle on a phone, and the favourite's edit sheet. | Port of desktop 0.6.10's BreadcrumbView, BreadcrumbActionsView and CatalogBreadcrumbChildrenProvider |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/CommentSegments.kt` | The passages of a line that commentaries explain (`CommentSegment`) and where each base book's commentary anchors lead (`commentAnchorIndex`, read from the APK's comment-anchors/<bookId>.pb assets). | Port of desktop 0.6.12's features/bookcontent/segments/CommentSegments.kt and CommentAnchorTargets.kt, from assets instead of the class path |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/FastScroller.kt` | `FastScroller`, the text's scrollbar for long books: the thumb placed by the share of the book's characters above the first visible line, jumps throttled while dragging, an exact landing on release, and a bubble naming the place. | Port of desktop 0.6.10's features/bookcontent/ui/panels/bookcontent/views/ContentScrollbar.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/FindBar.kt` | `FindBar`, the find-in-page bar (the field, the "3/861" counter, previous and next, close) with the smart mode by meaning where the semantic search is installed. | Port of desktop 0.6.10's core/presentation/components/FindInPageBar.kt, with Pardes for desktop's smart find |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/HighlightIcons.kt` | The `Highlighter` and `Eraser` icons of the highlight menu. | Desktop 0.6.10's resources/icons/highlighter.svg and eraser.svg (0.6.8), the same paths as ImageVectors |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/Justify.kt` | Justified text whose selection, handles and taps match what is drawn (`rememberJustifiedText`): each line laid out start-aligned and its spare width spread over its spaces, because Android measures inter-word justified lines unstretched. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/LineConnections.kt` | A line's connections as desktop computes them: `CommentatorItem` (names cleaned), the commentators with their groups (`listedCommentators`, `groupOf`), the targumim and links, merged for several lines; the forward links read in chunks for SQLite before 3.32. | Port of desktop 0.6.11's features/bookcontent/usecases/CommentariesUseCase.kt (computeLineConnections, commentatorGroupOf, sanitizeCommentatorName) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/LinePlacesPane.kt` | `LinePlacesPane`: the places that refer to the selected line, or its sources, as search results (a book at a time, the passage cut around the quote, "עוד N מקומות", tabs by kind and category), a tap opening the book in a new tab. | Port of desktop 0.6.11's features/bookcontent/ui/panels/bookcontent/views/LinePlacesView.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/LineReferences.kt` | A line's references (REFERENCE and מסורת הש״ס both ways, עין משפט forward) grouped by book and category (`groupReferences`), with the quote found by the search engine; the lines read in chunks for SQLite before 3.32. | Port of desktop 0.6.11's features/bookcontent/references/LineReferences.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/LineText.kt` | A line as the reader draws it: its annotated HTML with the diacritics and the passages, the selection bar (`shouldUseThickBar`), the find matches, the passages' trays and taps (`PassageHit`, `PassageTrays`), the alternative headings, the marked end, the Shnayim Mikra targum and the placeholders. | Port of desktop 0.6.10's BookContentView's LineItem and views/SelectionBarLogic.kt, with 0.6.12's passages |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/ListSelection.kt` | `ListSelection`, `TextPoint` and `SelectableText`: a text selection over the texts of a lazy list (the book's lines, a pane's commentaries), kept by unit and offset so scrolling does not break it, with word snapping over Hebrew marks and the maqaf. | Pardes's own: desktop selects with Compose's SelectionContainer |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/NotesPane.kt` | The notes pane: the selected lines' notes with their quote and date; adding, editing and deleting a note. | Port of desktop 0.6.10's features/bookcontent/ui/panels/notes/NotesPanel.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/ReaderCopy.kt` | Copy with the source (`buildCopyWithSourcePayload`, `formatHebrewSourceReference`), the error report's mailto (`bookErrorReportMailto`) and a free selection's highlight ranges (`resolveHighlightRangesForSelection`). | Port of desktop 0.6.10's core/CopyWithSource.kt, core/BookErrorReport.kt and core/annotations/HighlightSelectionResolver.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/ReaderPanes.kt` | The reader's panes: `PaneActions` and `PaneHeader`, the phone's pane tabs, `LinkItem` (a commentary, tinted inside the selected passage), the commentaries pane with its checklist ("כל המפרשים", the groups' boxes), the tablet's grid of pages and the phone's pager, `ReportVisibleCommentators`, and `LinksPane` (the targumim). | Port of desktop's features/bookcontent/ui/panes/ReaderPanes.kt, PaneHeader.kt and the commentaries views, with 0.6.12's passages and Pardes for desktop's group checkboxes |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/ReaderPrefs.kt` | `ReaderPrefs`, the reader's switches kept for every tab: the book-details pane (off by default) and the commentaries pane's list of commentators. | Port of desktop 0.6.10's AppSettings.bookDetailsPaneFlow and isCommentatorsListVisible |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/ReaderScreen.kt` | `ReaderScreen`, a book tab's reader for touch: on a tablet desktop's panes around the text (the tree, the TOC and the notes; references, targumim and details; sources and commentaries under the text) with the rails and dividers; on a phone one pane at a time under the text, a drawer, a bottom toolbar and full-screen reading; the selection's actions, the book's keyboard shortcuts, the history and the tab's title. | Port of desktop 0.6.11's features/bookcontent/(BookContentScreen, ReaderPanes, VerticalBars, BookContentView) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/ReaderText.kt` | The reader's text helpers: `DiacriticsMode` (kept per category, the button's cycle), finding and removing nikud and teamim, Hebrew search matching, highlight colours, the note underlines, and inline images drawn with `BitmapFactory`. | Port of desktop 0.6.10's core/presentation/text (DiacriticsMode.kt, HebrewSearch.kt, Highlighting.kt, Notes.kt) and of htmlparser's SkiaHtmlImageBuilder |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/ReaderViewModel.kt` | `ReaderViewModel`, `ReaderUiState` and `ReaderPane`: a book's lines paged by index, the line and passage selection, the TOC and the alternative TOC, the commentators kept per book (`CommentatorSelection`), the panes and their shared queries, notes and highlights, the diacritics per category, find in page and smart find, the Shnayim Mikra mode and the scroll requests. | Port of desktop 0.6.11's BookContentViewModel and its use cases (Content, Toc, AltToc, Commentaries, Notes, CategoryDisplaySettings) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/TextSelection.kt` | `SelectionAction` (desktop's text menu, in its order) and the selectable list: the long press, the handles, the scroll at the edges, the selection bar (copy, highlight with its swatches, note, "עוד") and the full menu sheet; taps counted to select a word on a double tap. | Port of desktop 0.6.10's features/bookcontent/BookContentScreen.kt's BookTextMenus, on Pardes's own selection |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/TocPane.kt` | The TOC pane: the book's TOC tree and the alternative TOC under it, its title search, the selected entry brought into view once per selection, a tap to go there; and the panes' search field. | Port of desktop 0.6.10's features/bookcontent/ui/panels/booktoc/BookTocPanel.kt and BookTocView.kt, and ui/components/PaneSearch.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/reader/TocSearch.kt` | The TOC pane's title search (`buildTocFilter`, `TocFilterResult`): the matching entries, their ancestors and the ranges to highlight. *Note:* Its header calls it a verbatim port, but desktop's code is reformatted (declarations folded onto single lines). | Port of desktop 0.6.10's features/bookcontent/usecases/TocSearch.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/search/PassagePreview.kt` | `PassagePreviewPane`, the tablet's preview of the selected result: the passage around the found line, the line's bar drawn on its start side, links in the accent colour, and the highlights by meaning when the semantic search gives them. | Port of desktop 0.6.10's features/search/SearchResultScreen.kt PassagePreview (two panes only) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/search/SearchBarViewModel.kt` | `SearchBarViewModel` and its state, one per Home tab and per results tab: the smart bar's suggestions as the user types (books, authors, places, references, categories, the authors of the books named), the picked book's TOC, the picked author, categories and books (`scopeItems`), `resolvePickedReference` and `deepLinkTarget`, with the database's titles when the lookup index is missing. | Port of desktop 0.6.10's features/search/SearchHomeViewModel.kt, with Pardes for desktop's categories, several books and title index |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/search/SearchEntityPanel.kt` | The panel of the book or author a query names (what it is, buttons to go there, the book's details), beside the preview on wide screens and as the first card on narrower ones. | Port of desktop 0.6.10's search knowledge panel (SearchEntityPanel) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/search/SearchParts.kt` | Pieces shared by the results page, the preview, the knowledge panel and the reader's places panes: `card`, `ResultLink`, `ResultRow`, `ResultTitle`, `SnippetText`, `LocalResultTextScale`, `categoriesAndPlace`, the counts' format and matching without diacritics. | Port of desktop 0.6.11's features/search/SearchResultScreen.kt parts and core/presentation/text (HebrewSearch.kt, Highlighting.kt) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/search/SearchScreen.kt` | `SearchScreen`, the results page: the smart bar, the category tabs (with the meaning search's other categories), the way back from one book, the status line (base books or all), the results grouped by book with their "more" links, paging and find within the results; on a tablet the preview beside the list in a resizable split (`SplitHandle`) and the end rail, on a phone a tap opens the result. | Port of desktop 0.6.11's features/search/SearchResultScreen.kt and TabsContent's search tab, with 0.6.12's split |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/search/SearchTocPane.kt` | `SearchTocPane`, a book search's TOC pane on a tablet: the book's entries with results and their counts; checking entries narrows the results to them. | Port of desktop 0.6.10's BookTocPanel.kt SearchBookTocPanel |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/search/SearchViewModel.kt` | `SearchViewModel` and `SearchUiState`: a search's first page with its facets, pages of 25, the category tabs, "more from this book" and back, the author, category, base-books and several-books scopes, the meaning search's facets (`withMeaningHits`) and other categories, the tablet's preview and the knowledge panel. | Port of desktop 0.6.10's features/search/SearchResultViewModel.kt on the same Lucene engine, with Pardes for desktop's categories and meaning facets |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/search/UnifiedSearchBar.kt` | `UnifiedSearchBar`, the smart search bar and its suggestion rows (the text search in base books or all, books, places, TOC entries, authors, categories, the picked chips, "חיפוש בתוך"), keyboard navigation and Enter, the typewriter placeholder, and the suggestions panel above or below the field; `SearchBarActions`. *Note:* The app's largest file (1,741 lines); `UnifiedSearchBar` is one composable of about 800 lines. | Port of desktop 0.6.10's HomeView.kt UnifiedSearchBar, SearchBar and SuggestionsPanel, and texteffects' TypewriterPlaceholder, with Pardes for desktop's categories and several books |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/AboutSettings.kt` | Settings › אודות and תנאים: a section on Pardes (an Android app based on Zayit by Elie Gambache and not on his behalf, its source under the AGPL with the §7b terms) and desktop's thanks in the app's words (Klei Kodesh's address a Markdown link, as on desktop since 2026-11-10: `MarkdownText` makes no link of a bare address); the terms page shows `CONDITIONS_MARKDOWN`. | Pardes's own text in place of desktop 0.6.10's AboutSettingsScreen (whose ABOUT.md speaks in its author's first person) and ConditionsSettingsScreen |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/AppUpdateSection.kt` | The app update's UI: `AppUpdateBanner` atop the app's header card for the states that ask for an action (a new version, the download, the install permission, a failure), `AppUpdateStatusLine` under the version, and `AppUpdateDialog` (the version, its size, what is new, "הורד והתקן" or "מאוחר יותר"). | Port of desktop 0.6.10's GeneralSettingsScreen update banner and line (0.6.8) and features/update/UpdateDialog.kt, for Pardes's own updater |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/DataSettings.kt` | Settings › ניהול נתונים: the incremental library updates, the library's place, the semantic search card, then "ייצוא הנתונים שלי" (desktop's backup archive), "ייבוא נתונים" (staged for the next start), "איפוס האפליקציה", and updates from files or a folder; no Google Drive card. *Note:* A file-level `MainScope()` runs the jobs that outlive the screen. | Port of desktop 0.6.10's DataSettingsScreen and DataSettingsViewModel, with Android's sections |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/DbDeltaUpdateSection.kt` | `DbDeltaUpdateSection`, the incremental update card: its status, a progress bar while a phase runs, "check for updates", the update found with its size and the metered-network warning, "הורד עכשיו", cancel until the database part is committed, and dismiss. | Port of desktop 0.6.10's features/settings/dbupdate/DbDeltaUpdateSection.kt, its English literals in Hebrew |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/LibraryLocationSection.kt` | "מיקום הספרייה": where the library is (the phone or a memory card), moving it to the other place with its progress and cancel, and switching to, or deleting, a library found in the other place. | Pardes's own; sizes counted as desktop's LibraryLocation.size, with the semantic pack |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/LicencesSettings.kt` | Settings › רישיונות: Pardes's licence (the repository's AGPL-3.0 with §7b) and the credits of everything the APK carries (libraries, icons, fonts, the model, Klei Kodesh's and other data, the stars, the Temple painting), each licence text opened offline from assets/licenses (`LicenceDoc`, `Credit`), the Creative Commons ones online. *Note:* The Bona Nova SC credit says 2017; its OFL text says 2020. | Pardes's own (desktop has no such page) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/SemanticPackSection.kt` | `SemanticPackSection`, the semantic search card in Settings › ניהול נתונים: the files beside the library and their version, download or import (files picked together), progress, problems, the room an install needs, "לפתוח את המסך הזה בכל הפעלה", framed and scrolled into view when the settings open on it; hidden where the device cannot run it. | Port of Pardes for desktop's features/settings/semantic/SemanticPackCard |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/SemanticPackStrings.kt` | `SemanticPackStrings`, the semantic card's and its notification's texts (desktop's `semantic_pack_*` and `semantic_problem_*`), with Android's own lines for picking files and the library's storage. *Note:* Text for users in Kotlin constants, where Zayit keeps it in strings.xml. | Pardes for desktop's strings.xml semantic keys, with Android's additions |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/SettingsComponents.kt` | The settings pages' controls: the page column, `SettingsCard` (marked with an accent border when opened for it), `SettingCheckCard`, check rows, labelled controls, action rows, `ZDropdown`, `ZTextField`, the error banner, links and `MarkdownText`. *Note:* `ErrorBanner` and `MarkdownText` repeat onboarding/OnboardingComponents.kt's. | Port of desktop 0.6.10's settings components (SectionCard, SettingCard, the dropdowns) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/SettingsPages.kt` | The settings pages: כללי (the app's header card with the update's banner and line, the three checkbox cards, and Android's automatic update check at start), תצוגה, פרופיל, and גופנים (each font with a verse preview). | Port of desktop 0.6.10's features/settings/ui GeneralSettingsScreen, DisplaySettingsScreen, ProfileSettingsScreen and FontsSettingsScreen |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/SettingsScreen.kt` | Settings: `SettingsPage` (desktop's sidebar pages in its order with its icons, and רישיונות), a list of pages each opening full screen on a phone and desktop's two panes on a tablet, Back closing the settings from the page they opened on. | Port of desktop 0.6.10's features/settings/SettingsWindow.kt and SettingsSidebar.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/settings/semantic/SemanticPackPrompt.kt` | `SemanticPackPrompt`: while the library has no semantic pack in use, a plain start opens Settings › ניהול נתונים on the semantic card, until a pack is in use or the user unticks it. | Android variant of Pardes for desktop's features/settings/semantic/SemanticPackPrompt.kt (same package and names, so desktop's SemanticPackPromptTest runs on it unchanged) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/DeepLinks.kt` | The `zayit://` links: the scheme, book, line and search links built, and `parseZayitDeepLink`, which maps them, and the older `zayit://search?q=` form, to a `Destination`. | Port of desktop 0.6.10's core/deeplink/ZayitDeepLink.kt, mapped to Android's Destination |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/HistoryScreens.kt` | The History and Favorites tab pages: the history by day with search, delete and clear all, entries reopening their book's line or their search with its scope, and the favourites with their folders; the hover's × always shown, a long press for the right click, the popups as dialogs. | Port of desktop 0.6.10's features/history/HistoryScreen.kt, features/favorites/FavoritesScreen.kt, core/history/VisitEntryDestination.kt and ListPageComponents.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/KeyShortcuts.kt` | `KeyShortcuts`: hardware-keyboard shortcuts (tabs, workspaces, zoom, find, Home) dispatched before the views, a fallback for the keys no view used (the reader's book shortcuts), and their list for the system's shortcuts helper. | Port of desktop 0.6.10's core/presentation/window/MainAppWindow.kt shortcuts |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/KeyboardDismissal.kt` | `DismissKeyboardOnRequest`: drops the focused field and the soft keyboard when a link, a launcher shortcut or a widget tap comes in, in two passes that wait for the window's focus. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/LauncherShortcuts.kt` | `LauncherShortcuts`: the launcher's "לשונית חדשה" shortcut (a new Home tab) and the books read last as `zayit://book` dynamic shortcuts. | Pardes's own: the Android counterpart of desktop's jump list, dock menu and Linux quicklist |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/ShellPopups.kt` | The title bar's lists: the open tabs with the latest history (searchable, "הצג את כל ההיסטוריה"), the favourites and the notes menu (`NotesMenuContent`), as a popup under the button on a tablet (`TitleBarPopup`) and a bottom sheet on a phone (`ShellSheet`). | Port of desktop 0.6.10's core/presentation/components/TabSearchPopup.kt, FavoritesPopup.kt and NotesPopup.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/SystemBars.kt` | The system bars' colours: `SystemBarBackgrounds`, where screens and dialogs say what is drawn under the status and navigation bars (`SystemBarsOver`, `DialogSystemBarsOver`), `SystemBarsHost`, and the navigation bar's scrim below Android 10. | Pardes's own |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/TabHost.kt` | `TabHost` and `TabSession`: the tabs' screens hosted as desktop's TabsContent hosts them, a `ViewModelStoreOwner` per tab, an LRU of composed tabs, Back per tab, a Home kept composed behind a book opened in its place, and what a screen may ask of its tab (`LocalTabSession`, `isTabInFront`). | Port of desktop 0.6.10's core/presentation/tabs/TabsContent.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/TabMenu.kt` | A tab's context menu (`TabMenuItem`, `tabMenuItems`): new tab, duplicate, pin, close, close the others or those to one side, reopen the closed, move to another workspace; as a sheet or a dropdown. | Port of desktop 0.6.10's core/presentation/tabs/TabsView.kt TabActionsMenu |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/TopBar.kt` | The title bars: `PhoneTopBar` (the tab's title and subtitle, the open-tabs counter, home, search, and the overflow with the theme, settings, tools, notes and the update) and `TabletTitleBar` (desktop's title bar: the tab strip, pins, the slides of moved tabs, the history, favourites and notes buttons, the update badge); `TopBarActions`. | Port of desktop 0.6.10's core/presentation/components/MainTitleBar.kt and its tab strip |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/WorkspaceSwitcher.kt` | Workspaces ("מרחבים"): the title bar's trigger and dropdown on a tablet, a trigger and a bottom sheet on the phone's open-tabs page; switch, rename in place, delete, and "מרחב חדש". | Port of desktop 0.6.10's core/presentation/components/DesktopSwitcher.kt, without new windows and drag reordering |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/shell/ZayitApp.kt` | `ZayitApp`, the tabbed shell: on a phone the top bar, the tab's screen, the library drawer, the overlays and the open-tabs page; on a tablet desktop's title bar with the tab strip and the side islands; it routes links and launcher shortcuts, the tools, settings, history, favourites and notes, the update check after the session is restored, and the title Recents shows. | Port of desktop 0.6.10's main.kt, core/presentation/window/MainAppWindow.kt, core/presentation/tabs/TabsContent.kt and core/deeplink/ContentDeepLinkHandler.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/theme/ZayitTheme.kt` | The theme: `AccentColor` (desktop's accents), `ZayitColors`, the Islands colours for light and dark with the title bar's gradient, `ZayitFonts` (the defaults), `ZayitFont` (27 fonts with desktop's codes and Hebrew labels, `familyFor`), `ReaderFonts`, `Zayit.colors` and `Zayit.fonts`, and `ZayitTheme` over Material 3. *Note:* Rashi Amiti is left out (licensed to the Zayit app only); a stored `rashiamiti` falls back to the default. | Port of desktop 0.6.10's core/presentation/theme/AccentColor.kt, the Islands colours and FontCatalog |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/DailyPrayerTool.kt` | "תפילת היום": the Tefila widget alone as its tool (what changes in the day's tefila, the Hebrew date, "פתח סידור"), with Android's additions: the day's prayer times by the community's luach and the Molad widget. | Port of desktop 0.6.10's tool window of TefilaWidget (features/home/widgets/luach/LuachWidgets.kt) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/DailyStudyTool.kt` | "לימוד יומי": the Limud widget alone as its tool, the limudim picked in its options each opening its place in a new tab, with the day above. | Port of desktop 0.6.10's tool window of LimudWidget (features/home/widgets/luach/LuachWidgets.kt) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/DictionaryTool.kt` | "מילון": the Dictionary widget's card alone at full size, as its tool. | Port of desktop 0.6.10's tool window of features/home/widgets/library/DictionaryWidget.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/HebrewCalendarTool.kt` | "לוח עברי": the Calendar widget alone (a Hebrew or civil month; a tap picks the day for every tool), with Android's card of the picked day: its dates, occasion, parsha, daf and times by the community's luach. | Port of desktop 0.6.10's tool window of features/home/widgets/calendar/CalendarWidget.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/MeasuresTool.kt` | "ממיר מידות": the Measures widget alone as its tool, with Android's table of the units. | Port of desktop 0.6.10's tool window of features/home/widgets/measures/MeasuresWidget.kt |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/MoladTool.kt` | "מולד וקידוש לבנה": the Molad widget alone (the coming molad and the community's Kiddush Levana window), with Android's additions: the Moon of the day shown and the year's moladot. | Port of desktop 0.6.10's tool window of MoladWidget (features/home/widgets/luach/LuachWidgets.kt) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/ShnayimMikraTool.kt` | "שנים מקרא ואחד תרגום": the week's parsha by its seven aliyot, each ticked once read; on a tablet a tap opens the aliya in the Chumash, and Android adds a reader with each verse twice and then Onkelos. | Port of desktop 0.6.10's tool window of features/home/widgets/luach/ShnayimMikraWidget.kt, with Android's own reader |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/SiddurTool.kt` | The siddur fallback: the library's siddurim, their sections found by heading (`SiddurData`) and the day's lines by their conditions, with the parts of desktop's screen that carry over: the community's nusach, the `siddur_nusach`, `siddur_minyan` and `siddur_beit_avel` settings, the day navigator, the tefila of the hour, the Kiddush Levana window and the screen kept on. | Pardes's own, in place of Zayit's private smart siddur (addons/siddur/enabled), keeping its keys and rules |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/ToolsCommon.kt` | The tools' frame: `ToolNav`, a page on a phone or a panel of desktop's window size on a tablet (`ToolPanelDialog`, `ToolPanelHost`, `ToolPage`), the shared day and place (`rememberUserCity`, `rememberZmanimOpinion`, `rememberToolValue`), and the shared controls (`ToolCard`, `GearButton`, `DayNavigator`, `CityPicker`, `AdaptiveColumns`...). *Note:* Keeps a SharedPreferences file of its own (`zayit_tools`). | Port of desktop 0.6.10's features/home/widgets/ToolWindows.kt, with Pardes's own controls |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/ToolsScreen.kt` | The Tools menu (the siddur, then the tools in the widgets' order) as large rows or a tablet grid, each tool's subtitle ("תפילת היום"'s line by `tefilaDayAt`), and a tool opened as a page or a panel. | Port of desktop 0.6.10's core/presentation/components/ToolsMenuButton.kt, with Android's subtitles |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/YearCycleTool.kt` | "מעגל השנה": the solar system's full page (the Hebrew year's festivals on the Earth's orbit, played at its options' speed, with the Earth and the sky beside or under it). | 2D port of desktop 0.6.10's SolarSystemWindow (features/home/widgets/solarsystem) |
| `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/ui/tools/ZmanimTool.kt` | "זמני היום": the Zmanim widget's cards alone, with the day and place to look at (never saved), the coming zmanim and every zman of the day in a list, by the community's luach. | Port of desktop 0.6.10's tool window of features/home/widgets/zmanim/ZmanimWidget.kt, with Android's additions |

#### The manifest, native code and schemas

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/AndroidManifest.xml` | The app's manifest: its permissions (internet, notifications, foreground data-sync services, wake lock, network state, installing its own updates and replacing itself without asking each time); the application `ZayitApplication` (no backup, `Theme.Zayit`, the network security config); `MainActivity` (single task, the launcher, `zayit://` links); five foreground data-sync services (library install, library move, incremental update, semantic pack, app update); the update receivers (PackageInstaller's answer, MY_PACKAGE_REPLACED); and six launcher widget providers with their refresh receiver, enabled from Android 12 (`@bool/appwidget_enabled`). *Note:* The class and theme names keep Zayit from before the rename; users do not see them. The `zayit` scheme is the link format of desktop and the library's data, so it stays. | Pardes's own |
| `androidApp/app/src/androidMain/cpp/CMakeLists.txt` | CMake build of `libpardes_multiplex.so`: multiplex.c and cache.c, against the SQLite headers in sqlite/, hidden symbols, `-O2 -Wall`, and 16 KB page alignment for the Android 15 devices that use it. It links to libc only: the extension calls SQLite through the loading SQLite's routines. | Pardes's own |
| `androidApp/app/src/androidMain/cpp/cache.c` | One JNI function in the same library, `PageCache.nativeDropCache(path)`: drops a flushed file's pages from the page cache with `posix_fadvise(POSIX_FADV_DONTNEED)`, so the check of a moved library (`LibraryMover`) reads its end back from the storage and not from RAM. *Note:* Not about SQLite: it shares the library so the app ships one native file. | Pardes's own |
| `androidApp/app/src/androidMain/cpp/multiplex.c` | SQLite's multiplex VFS shim, which keeps one database as files of less than 4 GiB (seforim.db, seforim.db001, ...) for a library on a FAT32 memory card, built as a loadable extension of requery's SQLite (`SplitDatabase`). Pardes's changes are marked "Pardes": the calls go through `sqlite3ext.h`, the pending byte is the release builds' constant, and `sqlite3_multiplex_init` registers the VFS as the default and stays loaded. | SQLite's src/test_multiplex.c, version 3.49.1 (public domain), with Pardes's marked changes |
| `androidApp/app/src/androidMain/cpp/multiplex.h` | The multiplex VFS's header: `sqlite3_multiplex_initialize`, `sqlite3_multiplex_shutdown` and its file-control codes. | SQLite's src/test_multiplex.h, version 3.49.1 (public domain), no change marked |
| `androidApp/app/src/androidMain/cpp/sqlite/sqlite3.h` | SQLite's public header, for building the extension. | SQLite 3.49.1 (public domain), as released |
| `androidApp/app/src/androidMain/cpp/sqlite/sqlite3ext.h` | SQLite's loadable-extension header (`SQLITE_EXTENSION_INIT1/2`, the routine table). | SQLite 3.49.1 (public domain), as released |
| `androidApp/app/src/androidMain/sqldelight-user-legacy/io/github/kdroidfilter/seforimapp/db/UpsertLegacy.sq` | `upsertFavoriteLegacy` and `upsertVisitLegacy`: desktop's two UPSERTs as two statements in one transaction (update the existing row, insert a missing one), for Android 8-10, whose SQLite (3.18 to 3.22) predates UPSERT (3.24). No table of its own. | Pardes's own |
| `androidApp/app/src/androidMain/sqldelight-user-pardes/io/github/kdroidfilter/seforimapp/db/ReadingTime.sq` | The `reading_time` table (seconds a book was read per epoch day, for the Home's learning split) and its queries, written in two statements (`insertDay`, `addSeconds`) because Android 8-10's SQLite has no UPSERT. *Note:* Pardes for desktop has the same table, written with an UPSERT (its own ReadingTime.sq). | Pardes's own |
| `androidApp/app/src/androidMain/sqldelight-user/io/github/kdroidfilter/seforimapp/db/CategoryDisplaySettings.sq` | Desktop's user schema, part of `UserSettingsDb`: the `category_display_settings` table (a category's diacritics shown or hidden) and its two queries. *Note:* The six files of sqldelight-user are kept so that a desktop user_settings.db, imported with its notes, is read as is; desktop's folder now also holds Pardes's SearchFeedback.sq and its own ReadingTime.sq. | Zayit's SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/CategoryDisplaySettings.sq (0.6.10, the same in 0.6.12), copied as is |
| `androidApp/app/src/androidMain/sqldelight-user/io/github/kdroidfilter/seforimapp/db/Favorites.sq` | Desktop's favourites: the `favorite_folder` and `favorite` tables and their twelve queries (`upsertFavorite`, the search by title, the folders). *Note:* Android 8-10 run `upsertFavoriteLegacy` instead (UpsertLegacy.sq). | Zayit's SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/Favorites.sq (0.6.10, the same in 0.6.12), copied as is |
| `androidApp/app/src/androidMain/sqldelight-user/io/github/kdroidfilter/seforimapp/db/ShnayimMikra.sq` | Desktop's shnayim mikra: the `shnayim_mikra_aliya` table of the aliyot read, and its three queries. | Zayit's SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/ShnayimMikra.sq (0.6.10, the same in 0.6.12), copied as is |
| `androidApp/app/src/androidMain/sqldelight-user/io/github/kdroidfilter/seforimapp/db/UserHighlights.sq` | Desktop's highlights: the `user_highlights` table and its seven queries. | Zayit's SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/UserHighlights.sq (0.6.10, the same in 0.6.12), copied as is |
| `androidApp/app/src/androidMain/sqldelight-user/io/github/kdroidfilter/seforimapp/db/UserNotes.sq` | Desktop's notes: the `user_notes` table and its eight queries. | Zayit's SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/UserNotes.sq (0.6.10, the same in 0.6.12), copied as is |
| `androidApp/app/src/androidMain/sqldelight-user/io/github/kdroidfilter/seforimapp/db/VisitHistory.sq` | Desktop's visit history: the `visit_history` table and its five queries (`upsertVisit`, the recent visits, the search by title). *Note:* Android 8-10 run `upsertVisitLegacy` instead (UpsertLegacy.sq). | Zayit's SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/VisitHistory.sq (0.6.10, the same in 0.6.12), copied as is |

#### Tests

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidInstrumentedTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HfTokenizerParityTest.kt` | On-device test: the open model's cleaner and tokenizer in Kotlin against the Python ones, on Android's own ICU regex and normalizer (`cleanerMatchesCleanPy`, `queryIdsMatchTheTokenizersLibrary`, `documentIdsMatchTheTokenizersLibrary`), reading desktop's parity fixtures from the test's assets (`syncParityFixtures`). *Note:* Test names in camelCase, where Zayit's tests are sentences in backticks: Android's dex refuses spaces in names below API 30. | Android variant of Pardes for desktop's jvmTest HfTokenizerParityTest: the same cases and checks |
| `androidApp/app/src/androidInstrumentedTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/SemanticReferenceTest.kt` | On-device test: with semantic pack 2 installed beside the device's library (model `student@695cbe2/pardes-clean-1`), three reference queries find the five lines desktop's `IndependentSemanticSearch` found for each (measured on 2026-10-28) among the first ten, and the time and memory of each step are logged; skipped without the pack. *Note:* camelCase test name, as HfTokenizerParityTest. | Pardes's own |
| `androidApp/app/src/androidUnitTest/kotlin/io/github/kdroidfilter/seforimapp/android/data/CommentatorSelectionTest.kt` | JVM tests of `CommentatorSelection`: thirteen of Pardes for desktop's cases (a commentator kept for the book, whole groups, "כל המפרשים", hidden groups, exclusions), named in backticks. | Port of Pardes for desktop's CommentatorSelectionTest, without its ContentState and CommentatorGroup tests |
| `androidApp/app/src/androidUnitTest/kotlin/io/github/kdroidfilter/seforimapp/android/data/KleiKodeshDictionaryTest.kt` | JVM tests of the Klei Kodesh pack's reading on small packs written inline: its books, texts and related words, the entries made from the indexes and the text dictionaries. *Note:* camelCase test names, where Zayit's tests, and CommentatorSelectionTest beside it, are sentences in backticks. | Port of Pardes for desktop's KleiKodeshDictionaryTest |
| `androidApp/app/src/androidUnitTest/kotlin/io/github/kdroidfilter/seforimapp/android/search/LibraryCategoriesTest.kt` | JVM tests of `LibraryCategories` on a small catalog: a category's books are its subcategories' too, all of them or the base books alone; the categories come in the library's order with their paths. | Port of Pardes for desktop's LibraryCategoriesTest |
| `androidApp/app/src/androidUnitTest/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/LibraryLinesBeside.kt` | JVM test helper: `LibraryLines.beside(indexDir)`, the library database beside the text index read through a JDBC `SqlDriver`, for desktop's LevelPrecedenceTest when it runs on the port (`syncDesktopSemanticTests`). | Android variant of `LibraryLines.beside` in Pardes for desktop's BookLevels.kt |

#### Licence texts (assets/licenses/)

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/assets/licenses/Apache-2.0.txt` | The Apache License 2.0: commons-compress, requery's sqlite-android, HebrewNumerals, Kotlin and kotlinx, Jetpack Compose and AndroidX, SQLDelight, Kermit, the Material icons and the Tinos font. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/Apache-2.0.txt |
| `androidApp/app/src/androidMain/assets/licenses/Apache-Commons-NOTICE.txt` | Apache Commons Compress's NOTICE. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/Apache-Commons-NOTICE.txt |
| `androidApp/app/src/androidMain/assets/licenses/Bitstream-Charter-LICENSE.txt` | Bitstream Charter's licence, which the Culmus fonts carry. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/Bitstream-Charter-LICENSE.txt |
| `androidApp/app/src/androidMain/assets/licenses/Bootstrap-Icons-LICENSE.txt` | Bootstrap Icons' MIT licence (icons drawn after desktop's). Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/Bootstrap-Icons-LICENSE.txt |
| `androidApp/app/src/androidMain/assets/licenses/Culmus-LICENSE.txt` | The Culmus fonts' licence (GPL v2 with the font exception). Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/Culmus-LICENSE.txt |
| `androidApp/app/src/androidMain/assets/licenses/Fonts-NOTICE.txt` | The notices embedded in the bundled fonts' name tables (copyright, licence, URL), font by font, with the Android file names (`res/font`). Shown in Settings › רישיונות (`LicencesSettings`). | Generated from the fonts, as desktop's file of that name (which lists desktop's file names) |
| `androidApp/app/src/androidMain/assets/licenses/GPL-2.0.txt` | The GNU GPL v2, for the Culmus fonts and the Taamey and Keter Aram Tsova fonts. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/GPL-2.0.txt |
| `androidApp/app/src/androidMain/assets/licenses/LGPL-2.1.txt` | The GNU LGPL v2.1, for KosherJava Zmanim and KosherKotlin. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/LGPL-2.1.txt |
| `androidApp/app/src/androidMain/assets/licenses/Lucide-LICENSE.txt` | Lucide's ISC licence. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/Lucide-LICENSE.txt |
| `androidApp/app/src/androidMain/assets/licenses/OFL-Bona-Nova-SC.txt` | Bona Nova SC's SIL Open Font License. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/OFL-Bona-Nova-SC.txt |
| `androidApp/app/src/androidMain/assets/licenses/OFL-Cardo.txt` | Cardo's SIL Open Font License. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/OFL-Cardo.txt |
| `androidApp/app/src/androidMain/assets/licenses/OFL-Frank-Ruhl-Libre.txt` | Frank Ruhl Libre's SIL Open Font License. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/OFL-Frank-Ruhl-Libre.txt |
| `androidApp/app/src/androidMain/assets/licenses/OFL-Libertinus-Serif.txt` | Libertinus Serif's SIL Open Font License. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/OFL-Libertinus-Serif.txt |
| `androidApp/app/src/androidMain/assets/licenses/OFL-Miriam-Libre.txt` | Miriam Libre's SIL Open Font License. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/OFL-Miriam-Libre.txt |
| `androidApp/app/src/androidMain/assets/licenses/OFL-Noto-Hebrew.txt` | The SIL Open Font License of Noto Rashi Hebrew and Noto Serif Hebrew. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/OFL-Noto-Hebrew.txt |
| `androidApp/app/src/androidMain/assets/licenses/ONNX-Runtime-LICENSE.txt` | ONNX Runtime's MIT licence (the semantic search's model runtime). Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/ONNX-Runtime-LICENSE.txt |
| `androidApp/app/src/androidMain/assets/licenses/ONNX-Runtime-ThirdPartyNotices.txt` | ONNX Runtime 1.22.0's third-party notices. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text, from ONNX Runtime 1.22.0 (desktop's file is 1.17.1's) |
| `androidApp/app/src/androidMain/assets/licenses/Radix-Icons-LICENSE.txt` | Radix Icons' MIT licence. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/Radix-Icons-LICENSE.txt |
| `androidApp/app/src/androidMain/assets/licenses/Tabler-Icons-LICENSE.txt` | Tabler Icons' MIT licence. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/Tabler-Icons-LICENSE.txt |
| `androidApp/app/src/androidMain/assets/licenses/Zstandard-LICENSE.txt` | Zstandard's BSD licence (zstd-jni's native code). Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/Zstandard-LICENSE.txt |
| `androidApp/app/src/androidMain/assets/licenses/jsoup-LICENSE.txt` | jsoup's MIT licence. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; desktop's file of that name differs only by its copyright years |
| `androidApp/app/src/androidMain/assets/licenses/zstd-jni-LICENSE.txt` | zstd-jni's BSD 2-Clause licence. Shown in Settings › רישיונות (`LicencesSettings`). | Third-party text; the same file as desktop's branding/pardes/assetsExtra/common/licenses/zstd-jni-LICENSE.txt |

#### Fonts (res/font/)

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/res/font/aharoniclm_bold.otf` | Aharoni CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.aharoniclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/AharoniCLM-Bold.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/aharoniclm_boldoblique.otf` | Aharoni CLM, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.aharoniclm_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/AharoniCLM-BoldOblique.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/aharoniclm_book.otf` | Aharoni CLM, book: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.aharoniclm_book`. | Zayit's SeforimApp/src/commonMain/composeResources/font/AharoniCLM-Book.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/aharoniclm_bookoblique.otf` | Aharoni CLM, book oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.aharoniclm_bookoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/AharoniCLM-BookOblique.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/bonanovasc_bold.ttf` | Bona Nova SC, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.bonanovasc_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/BonaNovaSC-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/bonanovasc_italic.ttf` | Bona Nova SC, italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.bonanovasc_italic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/BonaNovaSC-Italic.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/bonanovasc_regular.ttf` | Bona Nova SC, regular: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.bonanovasc_regular`. | Zayit's SeforimApp/src/commonMain/composeResources/font/BonaNovaSC-Regular.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/cardo_bold.ttf` | Cardo, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.cardo_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/Cardo-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/cardo_italic.ttf` | Cardo, italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.cardo_italic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/Cardo-Italic.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/cardo_regular.ttf` | Cardo, regular: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.cardo_regular`. | Zayit's SeforimApp/src/commonMain/composeResources/font/Cardo-Regular.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/davidclm_bold.otf` | David CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.davidclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/DavidCLM-Bold.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/davidclm_bolditalic.otf` | David CLM, bold italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.davidclm_bolditalic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/DavidCLM-BoldItalic.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/davidclm_medium.otf` | David CLM, medium: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.davidclm_medium`. | Zayit's SeforimApp/src/commonMain/composeResources/font/DavidCLM-Medium.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/davidclm_mediumitalic.otf` | David CLM, medium italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.davidclm_mediumitalic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/DavidCLM-MediumItalic.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/drugulinclm_bold.otf` | Drugulin CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.drugulinclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/DrugulinCLM-Bold.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/elliniaclm_bold.otf` | Ellinia CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.elliniaclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/ElliniaCLM-Bold.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/elliniaclm_bolditalic.otf` | Ellinia CLM, bold italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.elliniaclm_bolditalic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/ElliniaCLM-BoldItalic.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/elliniaclm_light.otf` | Ellinia CLM, light: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.elliniaclm_light`. | Zayit's SeforimApp/src/commonMain/composeResources/font/ElliniaCLM-Light.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/elliniaclm_lightitalic.otf` | Ellinia CLM, light italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.elliniaclm_lightitalic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/ElliniaCLM-LightItalic.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/frankruehlclm_bold.otf` | Frank Ruehl CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.frankruehlclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/FrankRuehlCLM-Bold.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/frankruehlclm_boldoblique.otf` | Frank Ruehl CLM, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.frankruehlclm_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/FrankRuehlCLM-BoldOblique.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/frankruehlclm_medium.otf` | Frank Ruehl CLM, medium: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.frankruehlclm_medium`. | Zayit's SeforimApp/src/commonMain/composeResources/font/FrankRuehlCLM-Medium.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/frankruehlclm_mediumoblique.otf` | Frank Ruehl CLM, medium oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.frankruehlclm_mediumoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/FrankRuehlCLM-MediumOblique.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/frankruhllibre.ttf` | Frank Ruhl Libre: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.frankruhllibre`, and the Home's logo. | Zayit's SeforimApp/src/commonMain/composeResources/font/frankruhllibre.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/hadasimclm_bold.otf` | Hadasim CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.hadasimclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/HadasimCLM-Bold.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/hadasimclm_boldoblique.otf` | Hadasim CLM, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.hadasimclm_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/HadasimCLM-BoldOblique.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/hadasimclm_regular.otf` | Hadasim CLM, regular: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.hadasimclm_regular`. | Zayit's SeforimApp/src/commonMain/composeResources/font/HadasimCLM-Regular.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/hadasimclm_regularoblique.otf` | Hadasim CLM, regular oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.hadasimclm_regularoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/HadasimCLM-RegularOblique.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/keteraramtsova.ttf` | Keter Aram Tsova: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.keteraramtsova`. | Zayit's SeforimApp/src/commonMain/composeResources/font/KeterAramTsova.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/keteryg_bold.ttf` | Keter YG, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.keteryg_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/KeterYG-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/keteryg_boldoblique.ttf` | Keter YG, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.keteryg_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/KeterYG-BoldOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/keteryg_medium.ttf` | Keter YG, medium: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.keteryg_medium`. | Zayit's SeforimApp/src/commonMain/composeResources/font/KeterYG-Medium.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/keteryg_mediumoblique.ttf` | Keter YG, medium oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.keteryg_mediumoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/KeterYG-MediumOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/libertinusserif_bold.ttf` | Libertinus Serif, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.libertinusserif_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/LibertinusSerif-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/libertinusserif_bolditalic.ttf` | Libertinus Serif, bold italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.libertinusserif_bolditalic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/LibertinusSerif-BoldItalic.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/libertinusserif_italic.ttf` | Libertinus Serif, italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.libertinusserif_italic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/LibertinusSerif-Italic.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/libertinusserif_regular.ttf` | Libertinus Serif, regular: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.libertinusserif_regular`. | Zayit's SeforimApp/src/commonMain/composeResources/font/LibertinusSerif-Regular.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/libertinusserif_semibold.ttf` | Libertinus Serif, semi bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.libertinusserif_semibold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/LibertinusSerif-SemiBold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/libertinusserif_semibolditalic.ttf` | Libertinus Serif, semi bold italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.libertinusserif_semibolditalic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/LibertinusSerif-SemiBoldItalic.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/miriamclm_bold.otf` | Miriam CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.miriamclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/MiriamCLM-Bold.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/miriamclm_book.otf` | Miriam CLM, book: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.miriamclm_book`. | Zayit's SeforimApp/src/commonMain/composeResources/font/MiriamCLM-Book.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/miriamlibre_variablefont_wght.ttf` | Miriam Libre, variable font wght: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.miriamlibre_variablefont_wght`. | Zayit's SeforimApp/src/commonMain/composeResources/font/MiriamLibre-VariableFont_wght.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/miriammonoclm_bold.ttf` | Miriam Mono CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.miriammonoclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/MiriamMonoCLM-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/miriammonoclm_boldoblique.ttf` | Miriam Mono CLM, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.miriammonoclm_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/MiriamMonoCLM-BoldOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/miriammonoclm_book.ttf` | Miriam Mono CLM, book: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.miriammonoclm_book`. | Zayit's SeforimApp/src/commonMain/composeResources/font/MiriamMonoCLM-Book.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/miriammonoclm_bookoblique.ttf` | Miriam Mono CLM, book oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.miriammonoclm_bookoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/MiriamMonoCLM-BookOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/nachlieliclm_bold.otf` | Nachlieli CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.nachlieliclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/NachlieliCLM-Bold.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/nachlieliclm_boldoblique.otf` | Nachlieli CLM, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.nachlieliclm_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/NachlieliCLM-BoldOblique.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/nachlieliclm_light.otf` | Nachlieli CLM, light: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.nachlieliclm_light`. | Zayit's SeforimApp/src/commonMain/composeResources/font/NachlieliCLM-Light.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/nachlieliclm_lightoblique.otf` | Nachlieli CLM, light oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.nachlieliclm_lightoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/NachlieliCLM-LightOblique.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/notorashihebrew.ttf` | Noto Rashi Hebrew: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.notorashihebrew`. | Zayit's SeforimApp/src/commonMain/composeResources/font/notorashihebrew.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/notoserifhebrew.ttf` | Noto Serif Hebrew: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.notoserifhebrew`. | Zayit's SeforimApp/src/commonMain/composeResources/font/notoserifhebrew.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/shofardemi_bold.ttf` | Shofar Demi, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.shofardemi_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/ShofarDemi-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/shofardemi_boldoblique.ttf` | Shofar Demi, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.shofardemi_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/ShofarDemi-BoldOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/shofarregular.ttf` | Shofar Regular: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.shofarregular`. | Zayit's SeforimApp/src/commonMain/composeResources/font/ShofarRegular.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/shofarregularoblique.ttf` | Shofar Regular Oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.shofarregularoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/ShofarRegularOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/simpleclm_bold.ttf` | Simple CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.simpleclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/SimpleCLM-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/simpleclm_boldoblique.ttf` | Simple CLM, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.simpleclm_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/SimpleCLM-BoldOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/simpleclm_medium.ttf` | Simple CLM, medium: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.simpleclm_medium`. | Zayit's SeforimApp/src/commonMain/composeResources/font/SimpleCLM-Medium.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/simpleclm_mediumoblique.ttf` | Simple CLM, medium oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.simpleclm_mediumoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/SimpleCLM-MediumOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/stamashkenazclm.ttf` | Stam Ashkenaz CLM: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.stamashkenazclm`. | Zayit's SeforimApp/src/commonMain/composeResources/font/StamAshkenazCLM.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/stamsefaradclm.ttf` | Stam Sefarad CLM: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.stamsefaradclm`. | Zayit's SeforimApp/src/commonMain/composeResources/font/StamSefaradCLM.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameyashkenaz_bold.ttf` | Taamey Ashkenaz, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameyashkenaz_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyAshkenaz-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameyashkenaz_boldoblique.ttf` | Taamey Ashkenaz, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameyashkenaz_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyAshkenaz-BoldOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameyashkenaz_medium.ttf` | Taamey Ashkenaz, medium: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameyashkenaz_medium`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyAshkenaz-Medium.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameyashkenaz_mediumoblique.ttf` | Taamey Ashkenaz, medium oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameyashkenaz_mediumoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyAshkenaz-MediumOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameydavidclm_bold.ttf` | Taamey David CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameydavidclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyDavidCLM-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameydavidclm_boldoblique.ttf` | Taamey David CLM, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameydavidclm_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyDavidCLM-BoldOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameydavidclm_medium.ttf` | Taamey David CLM, medium: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameydavidclm_medium`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyDavidCLM-Medium.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameydavidclm_mediumoblique.ttf` | Taamey David CLM, medium oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameydavidclm_mediumoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyDavidCLM-MediumOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameyfrankclm_bold.ttf` | Taamey Frank CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameyfrankclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyFrankCLM-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameyfrankclm_boldoblique.ttf` | Taamey Frank CLM, bold oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameyfrankclm_boldoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyFrankCLM-BoldOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameyfrankclm_medium.ttf` | Taamey Frank CLM, medium: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameyfrankclm_medium`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyFrankCLM-Medium.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/taameyfrankclm_mediumoblique.ttf` | Taamey Frank CLM, medium oblique: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.taameyfrankclm_mediumoblique`. | Zayit's SeforimApp/src/commonMain/composeResources/font/TaameyFrankCLM-MediumOblique.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/tinos_bold.ttf` | Tinos, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.tinos_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/Tinos-Bold.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/tinos_bolditalic.ttf` | Tinos, bold italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.tinos_bolditalic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/Tinos-BoldItalic.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/tinos_italic.ttf` | Tinos, italic: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.tinos_italic`. | Zayit's SeforimApp/src/commonMain/composeResources/font/Tinos-Italic.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/tinos_regular.ttf` | Tinos, regular: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.tinos_regular`. | Zayit's SeforimApp/src/commonMain/composeResources/font/Tinos-Regular.ttf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/yehudaclm_bold.otf` | Yehuda CLM, bold: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.yehudaclm_bold`. | Zayit's SeforimApp/src/commonMain/composeResources/font/YehudaCLM-Bold.otf, byte for byte, renamed as an Android resource |
| `androidApp/app/src/androidMain/res/font/yehudaclm_light.otf` | Yehuda CLM, light: a font of the reader and of Settings › גופנים (`ZayitTheme`), as `R.font.yehudaclm_light`. | Zayit's SeforimApp/src/commonMain/composeResources/font/YehudaCLM-Light.otf, byte for byte, renamed as an Android resource |

#### Pictures, layouts and values (res/)

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/app/src/androidMain/res/drawable-nodpi/earthmap.jpg` | The 600×300 equirectangular world map that `EarthWidget` projects onto its flat globe. | Zayit's earlier earthwidget/src/commonMain/composeResources/drawable/earthmap.jpg, byte for byte (Zayit dropped it for NASA's 3D models on 2026-10-04, ec0c43c4) |
| `androidApp/app/src/androidMain/res/drawable-nodpi/homepage_wallpaper_dark_large.jpg` | The Home's dark wallpaper, large size, under its blending layers (`HomeScreen`). | Zayit's SeforimApp/src/commonMain/composeResources/drawable/homepage_wallpaper_dark_large.jpg, byte for byte |
| `androidApp/app/src/androidMain/res/drawable-nodpi/homepage_wallpaper_dark_medium.jpg` | The Home's dark wallpaper, medium size, under its blending layers (`HomeScreen`). | Zayit's SeforimApp/src/commonMain/composeResources/drawable/homepage_wallpaper_dark_medium.jpg, byte for byte |
| `androidApp/app/src/androidMain/res/drawable-nodpi/homepage_wallpaper_dark_small.jpg` | The Home's dark wallpaper, small size, under its blending layers (`HomeScreen`). | Zayit's SeforimApp/src/commonMain/composeResources/drawable/homepage_wallpaper_dark_small.jpg, byte for byte |
| `androidApp/app/src/androidMain/res/drawable-nodpi/homepage_wallpaper_large.jpg` | The Home's light wallpaper, large size, under its blending layers (`HomeScreen`). | Zayit's SeforimApp/src/commonMain/composeResources/drawable/homepage_wallpaper_large.jpg, byte for byte |
| `androidApp/app/src/androidMain/res/drawable-nodpi/homepage_wallpaper_medium.jpg` | The Home's light wallpaper, medium size, under its blending layers (`HomeScreen`). | Zayit's SeforimApp/src/commonMain/composeResources/drawable/homepage_wallpaper_medium.jpg, byte for byte |
| `androidApp/app/src/androidMain/res/drawable-nodpi/homepage_wallpaper_small.jpg` | The Home's light wallpaper, small size, under its blending layers (`HomeScreen`). | Zayit's SeforimApp/src/commonMain/composeResources/drawable/homepage_wallpaper_small.jpg, byte for byte |
| `androidApp/app/src/androidMain/res/drawable-nodpi/temple_small.jpg` | The still 400×320 picture of the Temple countdown card (a low-memory device, Android 8) and of its launcher widget: the burning city under the smoke, cropped from a public-domain painting (1850). | Pardes's own, made by the zayit-android skill's temple-picture.py; it replaced the stock picture inherited from Zayit, marked "No use without permission" |
| `androidApp/app/src/androidMain/res/drawable/appwidget_border.xml` | The launcher widgets' 1.5 dp panel border (the Home's PanelCard), white, tinted with the app's border colour. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/appwidget_chevron.xml` | The chevron of the limud widget's rows that open in the library, not mirrored. | Material's ChevronLeft icon (Apache-2.0) |
| `androidApp/app/src/androidMain/res/drawable/appwidget_fill.xml` | The launcher widgets' panel, white, tinted with the app's panel colour. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/appwidget_pill.xml` | A highlighted row of a launcher widget (the next zman, the Shabbat), white, tinted with the accent's tint. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/appwidget_search_icon.xml` | The search widget's magnifier. | Material's Search icon (Apache-2.0) |
| `androidApp/app/src/androidMain/res/drawable/appwidget_shape.xml` | A launcher widget's outline (the Home's 18 dp WidgetShape), which its root clips to. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/appwidget_tag.xml` | The tag of the limud widget's rows (6 dp corners), white, tinted with the accent at 16 %. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/appwidget_temple_dot.xml` | The Temple widget's title dot: a radial fire gradient with a light edge. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/appwidget_temple_glass.xml` | The Temple widget's number boxes: black glass with a light edge, 8 dp corners. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/appwidget_temple_scrim.xml` | The Temple widget's dark overlay over its picture, from 25 % black at the top to 45 % at the bottom. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/appwidget_temple_shape.xml` | The Temple widget's card: black under the picture, the widgets' 18 dp outline. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/ic_launcher_background.xml` | The launcher icon's background layer: olive green, lighter in the middle. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/ic_launcher_foreground.xml` | The launcher icon's foreground layer: the word פרדס in two lines in gold Frank Ruhl Libre Black outlines, inside the safe zone. | Pardes's own, made by the zayit-android skill's pardes-icon.py |
| `androidApp/app/src/androidMain/res/drawable/ic_launcher_monochrome.xml` | The launcher icon's themed layer (Android 13): the letters alone, tinted by the system. | Pardes's own |
| `androidApp/app/src/androidMain/res/drawable/pardes_app_icon.xml` | The launcher icon as one picture (both layers), for the כללי header, the onboarding and the search results bar. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_limud.xml` | The "לימוד יומי" launcher widget: the title and the Hebrew date, then the limudim in one column or two (rows added at run time); on one short row the limudim alone. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_limud_preview.xml` | The "לימוד יומי" widget in the launcher's widget list, with sample limudim. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_line.xml` | A launcher widget on a row too short for its own layout (a Jelly Star row is about 30 dp): one line, a label and a value, sized at run time. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_loading.xml` | A launcher widget before the app has drawn it (the providers' initial layout). | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_luach.xml` | The "לוח עברי" launcher widget in one pane: the Hebrew date, the day's festival or Rosh Chodesh, the Shabbat with its times, and the coming events on a tall widget. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_luach_preview.xml` | The "לוח עברי" widget in the launcher's widget list, with a sample day. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_luach_wide.xml` | The "לוח עברי" launcher widget in two panes: the date and the Shabbat, and the coming events beside them. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_next_zman.xml` | The "הזמן הבא" launcher widget: the coming zman and its time, and the following ones on a tall widget. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_next_zman_preview.xml` | The "הזמן הבא" widget in the launcher's widget list, with a sample zman. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_row_event.xml` | A coming event of the "לוח עברי" widget (the Home's EventRow): its name and day, its times at the end. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_row_limud.xml` | A limud of the "לימוד יומי" widget (the Home's LimudRow): its kind as a tag, the day's reading, and the chevron of those that open in the library. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_row_zman.xml` | A row of a widget's list (a zman and its time), sharing its column's height; the next zman's row shows the pill, and a row without text pads a column. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_search.xml` | The "חיפוש בפרדס" launcher widget: a search field's look, whose tap opens the Home; its own preview. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_temple.xml` | The "חורבן הבית" launcher widget: the years, months and days since the destruction over the still picture; its sample numbers make it its own preview. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_temple_line.xml` | The "חורבן הבית" widget one launcher row high: the still picture behind one line, the title's dot and the years since. | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_zmanim.xml` | The "זמני היום" launcher widget: the day's zmanim in one column or two (rows added at run time). | Pardes's own |
| `androidApp/app/src/androidMain/res/layout/appwidget_zmanim_preview.xml` | The "זמני היום" widget in the launcher's widget list, with sample rows. | Pardes's own |
| `androidApp/app/src/androidMain/res/mipmap-anydpi-v26/ic_launcher.xml` | The adaptive launcher icon: the background, foreground and monochrome layers. | Pardes's own |
| `androidApp/app/src/androidMain/res/mipmap-anydpi-v26/ic_launcher_round.xml` | The round launcher icon, the same three layers. | Pardes's own |
| `androidApp/app/src/androidMain/res/raw/keep_home_tools.xml` | Tells the resource shrinker to keep the raw files read by id (`limud`, `limud_notice`, `temple_jerusalem_in_fire`, `sky_stars`). | Pardes's own |
| `androidApp/app/src/androidMain/res/raw/limud.pb` | hebcal-learning's daily learning schedules in one protobuf, read by `LimudSchedules`. | Zayit's SeforimApp/src/jvmMain/resources/limud/limud.pb, byte for byte |
| `androidApp/app/src/androidMain/res/raw/limud_notice.txt` | The schedules' notice: hebcal-learning and hebcal-leyning under the BSD 2-Clause licence, a few Kitzur typos fixed. | Zayit's SeforimApp/src/jvmMain/resources/limud/NOTICE.txt, byte for byte |
| `androidApp/app/src/androidMain/res/raw/sky_stars.csv` | The HYG Database's stars to magnitude 4 (right ascension, declination, magnitude, colour), CC BY-SA 4.0, for the sky widget. | Zayit's earthwidget/src/commonMain/composeResources/files/stars.csv, byte for byte |
| `androidApp/app/src/androidMain/res/raw/temple_jerusalem_in_fire.webp` | The animated picture of the Temple countdown card: the painting's crop with the fires' glow flickering in a loop. | Pardes's own (temple-picture.py), the same file as Pardes for desktop's composeResources/files/temple_jerusalem_in_fire.webp |
| `androidApp/app/src/androidMain/res/values-night-v27/themes.xml` | `Theme.Zayit` at night from Android 8.1: light system bar icons, the navigation bar's included. | Pardes's own |
| `androidApp/app/src/androidMain/res/values-night/appwidget_colors.xml` | The launcher widgets' dark colours (zayitColors dark, the gold accent), for the previews and a widget's first frame. | Pardes's own |
| `androidApp/app/src/androidMain/res/values-night/colors.xml` | The dark window background (#1E1F22, desktop's Islands ground). | Pardes's own |
| `androidApp/app/src/androidMain/res/values-night/themes.xml` | `Theme.Zayit` at night on Android 8.0: light status bar icons on the dark window background. | Pardes's own |
| `androidApp/app/src/androidMain/res/values-v27/themes.xml` | `Theme.Zayit` from Android 8.1: dark system bar icons on the light window, the navigation bar's included (`windowLightNavigationBar` is API 27). | Pardes's own |
| `androidApp/app/src/androidMain/res/values-v31/appwidget_bools.xml` | `appwidget_enabled` true: the launcher widgets are offered from Android 12. | Pardes's own |
| `androidApp/app/src/androidMain/res/values/appwidget_bools.xml` | `appwidget_enabled` false: the widgets' receivers are off below Android 12, whose RemoteViews calls they need. | Pardes's own |
| `androidApp/app/src/androidMain/res/values/appwidget_colors.xml` | The launcher widgets' light colours (the Islands tokens of `ZayitTheme`, the gold accent), for the previews and a widget's first frame; a placed widget takes the app's own theme and accent. | Pardes's own |
| `androidApp/app/src/androidMain/res/values/appwidget_strings.xml` | The launcher widgets' names, as desktop's `home_widget_name_*`, and their descriptions in the launcher's list. | Pardes's own; the names after desktop's strings.xml |
| `androidApp/app/src/androidMain/res/values/colors.xml` | The launcher icon's olive and gold, and the light window background. | Pardes's own |
| `androidApp/app/src/androidMain/res/values/strings.xml` | The app's name, פרדס. *Note:* With the widgets' names, the app's only string resources: the screens' texts are Kotlin constants. | Pardes's own |
| `androidApp/app/src/androidMain/res/values/themes.xml` | `Theme.Zayit` on Android 8.0: the light window background and dark status bar icons, for the splash window and the moments before Compose sets the bars. *Note:* The theme keeps Zayit's name from before the rename; users do not see it. | Pardes's own |
| `androidApp/app/src/androidMain/res/xml/appwidget_limud_info.xml` | The "לימוד יומי" widget's provider: its sizes (two columns from 330 dp, down to one launcher row), resizing, layouts and preview. | Pardes's own |
| `androidApp/app/src/androidMain/res/xml/appwidget_luach_info.xml` | The "לוח עברי" widget's provider: its sizes (one line on a very short row), resizing, layouts and preview. | Pardes's own |
| `androidApp/app/src/androidMain/res/xml/appwidget_next_zman_info.xml` | The "הזמן הבא" widget's provider: its sizes (one line on a very short row), resizing, layouts and preview. | Pardes's own |
| `androidApp/app/src/androidMain/res/xml/appwidget_search_info.xml` | The "חיפוש בפרדס" widget's provider: one row, its layout as its preview. | Pardes's own |
| `androidApp/app/src/androidMain/res/xml/appwidget_temple_info.xml` | The "חורבן הבית" widget's provider: its sizes (one line on the picture on a very short row), its layout as its preview. | Pardes's own |
| `androidApp/app/src/androidMain/res/xml/appwidget_zmanim_info.xml` | The "זמני היום" widget's provider: 2×2 down to the Jelly Star's two narrow cells, resizing, layouts and preview. | Pardes's own |
| `androidApp/app/src/androidMain/res/xml/network_security_config.xml` | Trusts the certificates the user installed as well as the system's, for every host, so the library download and the release lookup work behind Israeli content filters that inspect HTTPS (NetFree, Rimon, Etrog); clear-text traffic stays off. | Pardes's own; desktop does the same through the OS store (network/TrustedRootsSSL.kt) |

#### Release images and screenshots

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/docs/release/01-home.png` | Store-style image: shots/home.png in a phone frame under the caption "הספרייה התורנית בטלפון", with the line "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו". | Pardes's own, made by the zayit-android skill's release-frame.py |
| `androidApp/docs/release/02-search.png` | Store-style image: shots/search.png in a phone frame under the caption "חיפוש מלא בכל הספרים", with the line "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו". | Pardes's own, made by the zayit-android skill's release-frame.py |
| `androidApp/docs/release/03-reader.png` | Store-style image: shots/reader.png in a phone frame under the caption "קריאה נוחה", with the line "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו". | Pardes's own, made by the zayit-android skill's release-frame.py |
| `androidApp/docs/release/04-dark.png` | Store-style image: shots/reader-dark.png in a phone, dark frame under the caption "גם במצב כהה", with the line "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו". | Pardes's own, made by the zayit-android skill's release-frame.py |
| `androidApp/docs/release/05-tools.png` | Store-style image: shots/molad.png in a phone frame under the caption "כלים ללימוד ולזמנים", with the line "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו". | Pardes's own, made by the zayit-android skill's release-frame.py |
| `androidApp/docs/release/06-year-cycle.png` | Store-style image: shots/year-cycle.png in a phone, dark frame under the caption "מעגל השנה", with the line "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו". | Pardes's own, made by the zayit-android skill's release-frame.py |
| `androidApp/docs/release/07-small-screen.png` | Store-style image: shots/js-reader.png in a Jelly Star frame under the caption "מותאם גם למסך קטן", with the line "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו". | Pardes's own, made by the zayit-android skill's release-frame.py |
| `androidApp/docs/release/08-widgets.png` | Store-style image: shots/launcher-widgets.png in a Jelly Star, dark frame under the caption "ווידג'טים למסך הבית", with the line "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו". | Pardes's own, made by the zayit-android skill's release-frame.py |
| `androidApp/docs/release/09-tablet.png` | Store-style image: shots/tablet-search.png in a tablet frame under the caption "בטאבלט: הכל לצד הכל", with the line "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו". | Pardes's own, made by the zayit-android skill's release-frame.py |
| `androidApp/docs/release/README.md` | How the release images are made: the shots and their sizes, manifest.json, the authorship line every image carries, and the release-frame.py command. | Pardes's own |
| `androidApp/docs/release/manifest.json` | The release images' list: each shot with its name, Hebrew title and subtitle, device frame and background. | Pardes's own |
| `androidApp/docs/release/overview.png` | All nine release images in a row (6096×1171), shown at the top of androidApp/README.md. | Pardes's own, made by the zayit-android skill's release-frame.py |
| `androidApp/docs/release/shots/home.png` | Screenshot of the Home on a phone (1080×2400), framed into a release image. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/release/shots/js-reader.png` | Screenshot of the reader on the Jelly Star (480×854), framed into a release image. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/release/shots/launcher-widgets.png` | Screenshot of the launcher widgets on the Jelly Star (480×854), framed into a release image. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/release/shots/molad.png` | Screenshot of the molad and Kiddush Levana tool on a phone, framed into a release image. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/release/shots/reader-dark.png` | Screenshot of the reader in the dark theme on a phone, framed into a release image. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/release/shots/reader.png` | Screenshot of the reader on a phone, framed into a release image. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/release/shots/search.png` | Screenshot of the search results on a phone, framed into a release image. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/release/shots/tablet-search.png` | Screenshot of the search with its preview on a tablet (1920×1200), framed into a release image. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/release/shots/year-cycle.png` | Screenshot of the year cycle (מעגל השנה) on a phone, framed into a release image. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/screenshots/dark.jpg` | Screenshot of the app in the dark theme, in docs/DEVELOPMENT.md. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/screenshots/onboarding.jpg` | Screenshot of the onboarding, in docs/DEVELOPMENT.md. | Pardes's own: a screenshot of the app (the emulator, the test library) |
| `androidApp/docs/screenshots/phone.jpg` | Screenshot of the app on a phone, in docs/DEVELOPMENT.md. | Pardes's own: a screenshot of the app (the emulator, the test library) |

#### lucene-android: Lucene for Android 8.0

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/lucene-android/LICENSE.txt` | Lucene's licence (the Apache License 2.0 with its third-party sections), which the APK carries for Settings › רישיונות (`LicenceAssets`). | Lucene's own, from lucene-core-10.5.2.jar's META-INF; the same file as desktop's branding/.../licenses/Lucene-LICENSE.txt |
| `androidApp/lucene-android/NOTICE.txt` | Lucene's NOTICE, which the APK carries for Settings › רישיונות (`LicenceAssets`). | Lucene's own, from lucene-core-10.5.2.jar's META-INF; the same file as desktop's branding/.../licenses/Lucene-NOTICE.txt |
| `androidApp/lucene-android/build.gradle.kts` | Build of lucene-core 10.5.2 that links and runs on Android 8.0 (API 26): the original jar with the classes rebuilt from src/main/java, the JDK calls Android lacks redirected by the rewriter (always for API 34/35 calls, below Android 13 for API 33 ones), the Panama, MMap and Module classes stripped, and three lucene-analysis-common classes (the generator's 4-gram analyzer, for the delta updater); compiled for Java 11, reproducible. *Note:* Lucene's version must stay the one that writes the data release's indexes (10.5.2, as SeforimLibrary). | Pardes's own |
| `androidApp/lucene-android/check-min-api.sh` | The jar's gate: dexes it with AGP's own D8 for minSdk 26 and fails on any reference to the platform Android 8.0 lacks (API levels from api-versions.xml, then TraceReferences against the API 33 and the min API's android.jar); `AndroidCompatApi33` is checked on its own against API 33; CI runs it. | Pardes's own |
| `androidApp/lucene-android/check-release-dex.sh` | The second gate, on the R8-built APK: fails unless every signature-polymorphic VarHandle call is in `AndroidCompatApi33`, the one class only Android 13+ loads (R8 once moved them into `GroupVIntUtil` and Android 11 rejected it); CI runs it. | Pardes's own |
| `androidApp/lucene-android/gate/DexApiLevels.java` | check-min-api.sh's helper (one-file Java program): `split` puts the classes of a prefix in a jar of their own, `levels` lists every platform type, field and method the dex takes with its API level and reports those above the limit. | Pardes's own |
| `androidApp/lucene-android/rewriter/LuceneAndroidRewriter.java` | The ASM rewriter that turns lucene-core's jar into one that links on Android 8.0: classes rebuilt from src/main/java replaced, the classes that can never load on ART stripped (`STRIPPED`: Panama, MemorySegment, MMapDirectory...), and the call sites of missing JDK methods retargeted to the compat classes. | Pardes's own |
| `androidApp/lucene-android/smoke/CompatFallbacks.java` | JVM test of the jar's Android 8.0-12 code (one-file program): every API 33 stand-in of `AndroidCompat` and `CompatVarHandle` against the JDK method with random and edge-case inputs, then CheckIndex on a small index; without the fallback property, the API 33+ path. | Pardes's own |
| `androidApp/lucene-android/smoke/LuceneSmoke.java` | JVM smoke test of the jar (one-file program): reads a copy of a desktop index through NIOFSDirectory with term, phrase and prefix queries and a doc-values sort, then writes, deletes and merges a small index; the same with the Android 8.0-12 fallbacks forced. | Pardes's own |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/analysis/AnalysisSPILoader.java` | Lucene's analysis SPI loader, reading the provider names from META-INF/services and loading the classes without instantiating them, because `ServiceLoader.stream()` exists only from API 35. | Apache Lucene 10.5.2's AnalysisSPILoader.java (Apache-2.0), edited at its "// Android:" comments |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/internal/tests/TestSecrets.java` | Lucene's TestSecrets without `MethodHandles.Lookup.ensureInitialized` (`Class.forName` instead) and without the StackWalker caller check (API 34), keeping the set-once rule. | Apache Lucene 10.5.2's TestSecrets.java (Apache-2.0), edited at its "// Android:" comments |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/internal/vectorization/VectorizationProvider.java` | Lucene's VectorizationProvider returning the scalar provider alone (ART has no Hotspot, jdk.incubator.vector or Module), without the StackWalker caller check. | Apache Lucene 10.5.2's VectorizationProvider.java (Apache-2.0), edited at its "// Android:" comments |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/store/FSDirectory.java` | Lucene's FSDirectory whose `open` always returns NIOFSDirectory (MMapDirectory needs java.lang.foreign and is stripped), and `listAll` without `List.toArray(IntFunction)` (API 33). | Apache Lucene 10.5.2's FSDirectory.java (Apache-2.0), edited at its "// Android:" comments |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/util/AndroidCompat.java` | Static stand-ins for the JDK methods lucene-core 10.5 calls and Android lacks (or has only from API 33), which the rewriter points the call sites to; on Android 13+ the API 33 ones call the JDK. | Pardes's own (Apache-2.0, as the Lucene jar it is built into) |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/util/AndroidCompatApi33.java` | The JDK methods lucene-core calls that ART has only from API 33, one static method each, in a class only Android 13+ loads (R8 keeps it whole: proguard-rules.pro). | Pardes's own (Apache-2.0, as the Lucene jar it is built into) |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/util/AttributeFactory.java` | Lucene's AttributeFactory creating attributes from their reflected no-arg constructor below API 30, because the ART of Android 8 and 9 builds a constructor handle's instance from the handle's type. | Apache Lucene 10.5.2's AttributeFactory.java (Apache-2.0), edited at its "// Android:" comments |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/util/CompatClassValue.java` | A stand-in for `java.lang.ClassValue` (API 34), which the rewriter retargets to. | Pardes's own (Apache-2.0, as the Lucene jar it is built into) |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/util/CompatHexFormat.java` | A stand-in for `java.util.HexFormat` (API 34), which the rewriter retargets to. | Pardes's own (Apache-2.0, as the Lucene jar it is built into) |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/util/CompatRuntimeVersion.java` | A stand-in for `Runtime.Version`, which ART does not have, for the rewriter. | Pardes's own (Apache-2.0, as the Lucene jar it is built into) |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/util/CompatVarHandle.java` | A stand-in for `java.lang.invoke.VarHandle`, public on ART only from API 33, for lucene-core's byte-array views. | Pardes's own (Apache-2.0, as the Lucene jar it is built into) |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/util/HotspotVMOptions.java` | Lucene's HotspotVMOptions logging only the generic warning, as ART has no `java.lang.Module` (`Class.getModule` threw outside the try). | Apache Lucene 10.5.2's HotspotVMOptions.java (Apache-2.0), edited at its "// Android:" comments |
| `androidApp/lucene-android/src/main/java/org/apache/lucene/util/Version.java` | Lucene's Version without the named module's manifest fallback (no `java.lang.Module` on ART). | Apache Lucene 10.5.2's Version.java (Apache-2.0), edited at its "// Android:" comments |

#### The site (GitHub Pages)

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/site/assets/site.css` | The site's one stylesheet for its four pages: the app's Home look (a dawn sky over sand, the name in the app's gold, Frank Ruhl Libre for names and titles, Assistant for the text), every colour a token, light and dark. | Pardes's own |
| `androidApp/site/assets/site.js` | The site's one script: fills the versions, sizes and links from releases.json and downloads.json (snapshots android-site.yml writes in the GitHub API's shape), asks the API only on the downloads page, and shows the changelogs. | Pardes's own |
| `androidApp/site/changelog.html` | "מה חדש": the changes of each version of Pardes for Android and Pardes for desktop, from the changelogs the deploy publishes. | Pardes's own |
| `androidApp/site/downloads.html` | "כל ההורדות": direct links to Pardes for Android and for desktop, the library's files, the semantic search's files, the tools that move the library to a phone, and the source code. | Pardes's own |
| `androidApp/site/guide.html` | "מדריך": installing Pardes on a phone, a tablet and a computer, and getting the books in (downloaded, from the computer, a small library, on a memory card), the meaning search on a phone and updating the library offline. | Pardes's own |
| `androidApp/site/img/app/home.webp` | Screenshot of the Home with the search and the zmanim, on a phone, on the site's home page. | Pardes's own: a screenshot of Pardes for Android |
| `androidApp/site/img/app/meaning.webp` | Screenshot of a meaning search ("לאהוב את החבר כמו את עצמך" brings "ואהבת לרעך כמוך" first), on the site's home page. | Pardes's own: a screenshot of Pardes for Android |
| `androidApp/site/img/app/molad.webp` | Screenshot of the molad and Kiddush Levana tool, on the site's home page. | Pardes's own: a screenshot of Pardes for Android |
| `androidApp/site/img/app/search.webp` | Screenshot of search results grouped by book, on the site's home page. | Pardes's own: a screenshot of Pardes for Android |
| `androidApp/site/img/app/small-screen.webp` | Screenshot of a chapter of Bereshit with nikud and teamim, on a small phone, on the site's home page. | Pardes's own: a screenshot of Pardes for Android |
| `androidApp/site/img/app/smart-find.webp` | Screenshot of the smart find by meaning inside a book, on the site's home page. | Pardes's own: a screenshot of Pardes for Android |
| `androidApp/site/img/app/tablet.webp` | Screenshot of a tablet with the results, the book and its details side by side, on the site's home page. | Pardes's own: a screenshot of Pardes for Android |
| `androidApp/site/img/app/widgets.webp` | Screenshot of the launcher widgets on a home screen, on the site's home page. | Pardes's own: a screenshot of Pardes for Android |
| `androidApp/site/img/app/year-cycle.webp` | Screenshot of the year cycle, the festivals around the Sun, on the site's home page. | Pardes's own: a screenshot of Pardes for Android |
| `androidApp/site/img/desk/search.jpg` | Screenshot of Pardes for desktop: a meaning search, the book open beside the results; on the site's home page. | Pardes's own: a screenshot of Pardes for desktop |
| `androidApp/site/img/icon-180.png` | The site's icon at 180 px (Apple touch icon). | Pardes's own |
| `androidApp/site/img/icon-64.png` | The site's icon at 64 px (favicon). | Pardes's own |
| `androidApp/site/img/icon-96.png` | The app's icon at 96 px, in the pages' header. | Pardes's own |
| `androidApp/site/img/icon.svg` | The app's icon as SVG (favicon): פרדס in gold on olive green. | Pardes's own |
| `androidApp/site/img/social.jpg` | The image link previews show for the site (`og:image`). | Pardes's own |
| `androidApp/site/img/tools/export-choose.webp` | Screenshot of Zayit Export: the desktop library, the file's place and its estimated size; in the guide. | Pardes's own: a screenshot of Zayit Export, Pardes's separate Windows tool (efrul/zayit-export) |
| `androidApp/site/img/tools/export-done.webp` | Screenshot of Zayit Export: the finished file: its parts and the steps to install it on the phone; in the guide. | Pardes's own: a screenshot of Zayit Export, Pardes's separate Windows tool (efrul/zayit-export) |
| `androidApp/site/img/tools/export-progress.webp` | Screenshot of Zayit Export: the library being packed, with a stop button; in the guide. | Pardes's own: a screenshot of Zayit Export, Pardes's separate Windows tool (efrul/zayit-export) |
| `androidApp/site/img/tools/subset-main.webp` | Screenshot of Zayit Subset: the book tree with checkboxes, what to include and the estimated size; in the guide. | Pardes's own: a screenshot of Zayit Subset, Pardes's separate Windows tool (efrul/zayit-subset) |
| `androidApp/site/img/tools/subset-settings.webp` | Screenshot of Zayit Subset: its settings: the source library, where to save, the limits for large commentaries; in the guide. | Pardes's own: a screenshot of Zayit Subset, Pardes's separate Windows tool (efrul/zayit-subset) |
| `androidApp/site/index.html` | The site's home page: Pardes on the computer, tablet and phone, its search by words and by meaning, the reader, the zmanim and daily learning, the downloads, and the line that it is based on Zayit by Elie Gambache and not on his behalf, with the licences. | Pardes's own |

#### Tools

| File | What it is | Where it comes from |
|---|---|---|
| `androidApp/tools/make_subset_db.sh` | Builds a small development copy of seforim.db: the given base books (by default Bereshit, Berakhot and Mishnah Berakhot), the commentaries and targumim linked to them (`CONN_TYPES`, up to `MAX_LINES` lines), their lines, TOCs and metadata, the links between kept books and the categories above them. *Note:* The pardes-desktop-run skill uses it for the headless desktop's library too. | Pardes's own |
| `androidApp/tools/push-desktop-library.cmd` | The Windows launcher of the transfer script: a double-click runs push-desktop-library.ps1 beside it with the execution policy bypassed for that run, passes its arguments on and keeps the window open. *Note:* Kept CRLF by .gitattributes. | Pardes's own |
| `androidApp/tools/push-desktop-library.ps1` | Copies desktop Zayit's library to Pardes on a phone over adb, from Windows (seforim.db, both Lucene indexes, lexical.db, catalog.pb, release_info.txt and the semantic pack when there is one), optionally to a memory card in chunks on FAT32, optionally with the notes and highlights, or as a dry run. | Pardes's own |
| `androidApp/tools/push-desktop-library.sh` | The same transfer from Linux and macOS (bash), with the same options (`--source`, `-s`, `--sdcard`, `--with-notes`, `--dry-run`). | Pardes's own |

## 4. Departures from Zayit's style still to fix

Found while this map was written (2026-11-05 to 2026-11-09). Each one is work for a later change, which then rewrites
the entries it touches. ktlint and detekt could not be run for this list: in the cloud container Maven Central answered
429 through the proxy, and the build was left to CI and to a PC (the `zayit-fidelity` skill, rule 4). The counts below
are a script's, not detekt's.

### In Zayit's modules (Pardes for desktop)

- **Commit messages.** Pardes's history writes `desktop: …`, `androidApp: …` or `Daf view: …`. Zayit writes
  Conventional Commits, `type(scope): summary`, and so does Pardes from 2026-11-05 on.
- **Interface text in Kotlin.** `AGENTS.md` says user-visible text never goes in code, always in `strings.xml`.
  `LearningSplitWidget.kt` has some 30 Hebrew literals that the user sees: its options, headings, periods and
  empty states. Zayit's own Hebrew literals are data, such as book names and calendar tables.
- **Size beyond `detekt.yml`.** detekt is not in Zayit's CI, and 23 of the 505 Kotlin files of Zayit's
  `SeforimApp/src` are longer than 600 lines. Still, these new files are past its limits: 600 lines for a class,
  15 functions for a file or a class.

  | File | Lines | Functions |
  |---|---|---|
  | DafView.kt | 1,106 | about 26 |
  | LearningSplitWidget.kt | 796 | about 31 |
  | DafSetting.kt | 574 | about 28 |
  | HybridSearchEngine.kt | 574 | about 17 |
  | DafPage.kt | 560 | about 16 |
  | LibraryLocation.kt | 530 | about 38 |
  | SemanticPackInstaller.kt | 513 | about 21 |
  | BookTitleIndex.kt | 442 | about 20 |
- **Zayit's lines re-indented.** Of the 516 changed lines of `BookContentView.kt`, some 210 differ only by their
  indentation (`git diff -w` shows 94 lines); of `BookContentPanel.kt`'s 235, some 50. Each such line is a conflict
  waiting for the next Zayit release that touches it.
- **Test names.** Zayit's tests are sentences in backticks, and so are 232 of the tests Pardes adds in
  `SeforimApp/src/jvmTest`; 49, in 10 files, are camelCase.
- **Versions outside the catalog.** `SeforimApp/build.gradle.kts` adds ONNX Runtime 1.17.1 and org.json 20240303 by
  literal coordinates; Zayit takes every dependency from `gradle/libs.versions.toml`.
- **Zayit's names changed.** `SearchResultViewModel`'s `authorBookIds` became `scopeBookIds` for the category and
  several-books scopes: right for what it now does, but every Zayit change near it will conflict.
- **Comments.** `CommentariesUseCase`'s French KDoc, Zayit's own, was extended in French, where `CONTRIBUTING.md`
  asks for English. `ExistingLibraryScreen.kt` has two comments in a row that say the same thing.
  `BookContentScreen.kt` imports `androidx.compose.runtime.snapshotFlow` beside `androidx.compose.runtime.*`.
- **Types given up.** `FontsSettingsViewModel` combines its eight flows with `combine<Any, FontsSettingsState>`,
  where Zayit's typed `combine` took five.
- **A choice of Zayit's reversed.** Zayit made `SearchScope.Category` a scope that history only reads back, and
  dropped the search in a category on purpose. Pardes makes it a real scope again (section 1, search).
- **Groovy among Kotlin build scripts.** Every build script of Zayit's is Kotlin DSL; `branding/brand.gradle` is
  Groovy. Its header gives the reason: the Nucleus DSL is set by name, without its types on that script's
  classpath.
- **Developer documents in Hebrew.** Zayit's developer documents are in English; only `README_HE.md` is Hebrew, and
  it is for users. Six of Pardes's are in Hebrew: `SEMANTIC_SEARCH.md`, `docs/COMMON_SOURCES_PLAN.md`,
  `docs/LEXICON.md`, `docs/SEARCH_ORDER_ISSUES.md`, `docs/SEMANTIC_PLAN.md` and `feedback-service/README.md`.

### Risks found in the code

- **Lucene in the native image.** Zayit opens `seforim.db.lookup.lucene` with `NIOFSDirectory` under GraalVM's
  native image, which it says cannot run `MMapDirectory` (`LuceneLookupSearchService`). Pardes's
  `IndependentSemanticSearch` and `PardesLexicon` call `FSDirectory.open` unconditionally. Checked by use: the semantic
  search works in the released Pardes for desktop (the user, 2026-11-09), so nothing is changed; the difference from
  Zayit's guard stays noted here.
- **Zayit's incremental updates and the full-text index (fixed in Pardes on 2026-11-09).** Zayit's
  `defaultLuceneSinksProvider` (`framework/update/DbDeltaUpdateService.kt`) wrote each line a delta changes as a reduced
  document: no point on `line_index`, the raw HTML with nikud, no `text_ng4`, no `ancestor_category_ids`. The release's
  index has those, and Lucene refuses the first such line ("cannot change field line_index from points
  dimensionCount=1 ... to dimensionCount=0"), so the index's step of every delta fails. Pardes for desktop now writes
  the index writer's own document (`LuceneLineDocument.kt`, section 2; `DbDeltaLuceneSinkTest` failed before the fix),
  as Pardes for Android did. Zayit's code still has it; the user decided on 2026-11-09 not to report it upstream.

### Pardes for Android (`androidApp/`)

- **No linter.** Neither ktlint nor detekt runs on `androidApp/` (its build and CI have neither). 965 lines are
  longer than 140 characters, in 139 of its 202 Kotlin source files; Zayit's 505 files have 9 such lines. 22 files are
  longer than 600 lines, the largest `UnifiedSearchBar.kt` (1,741 lines, one composable of about 800),
  `ReaderScreen.kt` (1,401), `LibraryInstaller.kt` (1,324), `LuceneSearchEngine.kt` (1,223, mostly upstream's) and
  `ReaderViewModel.kt` (1,189).
- **Interface text in Kotlin.** The screens' texts are Kotlin constants (`DesktopStrings`, `AndroidStrings`,
  `AppUpdateStrings`, `SemanticPackStrings`, `DeltaStrings`, and literals in the screens); the only string resources
  are the app's name and the launcher widgets' names.
- **Copies of desktop files that compile as they are.** Since 2026-11-09 the build takes desktop's region lists, candle
  lighting, typed references, snippets, htmlparser, `searchKey`, `WritingHand` and seven onboarding icons from desktop,
  and their copies are gone. Two kinds stay: `ui/home/widgets/ArrangeLayout.kt`, which needs Android's own
  `WidgetPlacement` and `CellPitch`, and the six `sqldelight-user` schemas, as desktop's folder now holds Pardes's own
  tables too.
- **"Verbatim" that is not.** `FavoritesStore.kt`, `HistoryStore.kt`, `BookFindMatches.kt` and `TocSearch.kt` call
  themselves verbatim ports, but desktop's code is reformatted in them (declarations folded onto single lines).
- **Written twice inside the app.** Two `Community` enums with the same codes (`data/AppPrefs.kt`,
  `ui/onboarding/OnboardingViewModel.kt`); `ErrorBanner` and `MarkdownText` in both `OnboardingComponents.kt` and
  `SettingsComponents.kt`.
- **Names from before the rename.** The log tag `Zayit` (13 files), `ZayitApplication`, `ZayitApp`, `ZayitTheme`,
  `Theme.Zayit`, `ZayitWidgets`, the Gradle project `ZayitAndroid` and the preference files `zayit_settings`,
  `zayit_tools` and `zayit_launch`. Users see none of them; the preference files would lose the users' settings if
  renamed.
- **Java build logic.** `build-logic/` is Java, where Zayit's `buildSrc` is Kotlin.
- **A text out of date:** `LicencesSettings.kt` credits Bona Nova SC to 2017, while the OFL text it opens says 2020.
- **`warningColor`** in `DatabaseUpdateScreen.kt` writes its colours out instead of taking them from the theme.

### Texts and documents out of date

- **`README.md`**: its English line says desktop is Windows and Linux, but macOS has been built since 1.0.2. Its list of
  what Pardes adds lacks `feedback-service/`, `nucleus-fork/`, `scripts/kleikodesh_dictionary.py`, the `pardes-*`
  skills and the semantic and feedback workflows.
- **`branding/README.md`**: its tables of hooks stop before the later features (feedback, the daf view, the
  commentators' panes, search in categories, the learning split). It also says `ForeignLibraries` is the only reader of
  `BrandLibraries`, but `LibraryLocationViewModel` reads `OTHER_APP_NAME` too. This map is now the complete list.
- **Tags:** `docs/PARDES_PLAN.md` says efrul/Pardes holds only `android-*` tags. It has `desktop-*`, `semantic-*` and
  `daf-*` tags as well.
- **`CLAUDE.md`** keeps Zayit's line that PRs target `master`, since it is Zayit's text. In efrul/Pardes the default
  branch is `main`.
- **Nucleus:**
  - `PardesDesktopReleases`'s KDoc names Nucleus 3.0.0-dev-202610200221, but the app is on 3.0.0-dev-202610272317.
  - The shipped `Nucleus-THIRD_PARTY_NOTICES.md` is older than the copy inside the vendored tao jar, which adds
    robius-speech and robius-authentication.
- **`OFL-Bona-Nova-SC.txt`** says 2020, but the font itself and the licences' README say 2017.
- **`temple-picture.py`** still writes three `drawable/temple_jerusalem_in_fire_*.jpg` files into SeforimApp. Zayit
  deleted them in `0c8e6c3c`, and nothing reads them.
- **Session links:** `.claude/settings.json` turns off the session link in commits, yet some later commits carry
  `Claude-Session:` lines.

### Weight and generators

- The vendored tao jar (`nucleus-fork/maven/…/nucleus.decorated-window-tao-…-tearoff.jar`) is a 6.3 MB binary
  committed without LFS, and the history holds two versions of it.
- No script makes desktop's `Fonts-NOTICE.txt`; `font-notices.py` writes Android's only.
- The feedback service pins Miniflare at a beta version.

## 5. Zayit after the base

Zayit has one release that Pardes has not merged: **v0.6.13** (`e999a27c`, 2026-11-03). It has 26 commits and 38
files (+1,127 −238). On 2026-11-09 Zayit's `master` was one pull request past it (`677d8cb0`, 2026-11-06), which
bumps filament-kmp to 0.7.12 in `gradle/libs.versions.toml`.

- **The licence changes.** On 2026-11-01 (`1b9f1f21`, "chore(license): move to the Zayit Public License, drop the
  CLA") Zayit left the AGPL v3 with the §7b term for the **Zayit Public License 1.0** (`LICENSE`, SPDX
  `LicenseRef-Zayit-PL-1.0`). Its submodules SeforimLibrary and SeforimAcronymizer moved to commits under it
  (`58cffe4a`). Its own text says it is not the AGPL and not an OSI licence. Its terms:
  - **Base:** the AGPL v3's text, unmodified, with no "or any later version".
  - **Section 3, grant back:** whoever conveys a modified version, offers it over a network, or submits a change
    gives the licensor (Elie Gambache) a perpetual, irrevocable licence on those modifications, "under any terms,
    including proprietary terms", and a patent licence. Nothing has to be sent to him; it covers any copy he
    obtains lawfully, a public repository included.
  - **Section 4, attribution:** the "Powered by the technologies that drive Zayit — https://zayitapp.com/" line, also
    on output made with an automated system or a language model that is based on the software.
  - **Section 5, trademarks:** no right to the names "Zayit" or "זית" or their logos, beyond the attribution and
    saying where the software comes from. A modified version must not be presented as an official build.
- **For Pardes:**
  - Everything Pardes has is Zayit 0.6.12 or earlier, released under the AGPL v3 with §7b, and stays under it. The
    one exception is the siddur's keyboard scrolling (`21659516`), which is later than 0.6.12 but was committed
    under the same licence, before `1b9f1f21`.
  - Merging 0.6.13 or a later release brings code under the new licence, and with it Section 3's grant back on
    Pardes's own modifications of that code.
  - That is the user's decision, not part of a routine merge (the `zayit-fidelity` skill › Merging a Zayit release).
  - **Decided on 2026-11-09:** Pardes stays on 0.6.12 for now. Each later release is asked about again, its licence
    first.
- **The rest of 0.6.13:**
  - the Microsoft Store install updates from the Store (`be6eae68`; `framework/update/StoreUpdates.kt`,
    `AppUpdateService.kt`);
  - Ctrl+Tab follows the tab strip's right-to-left order (`f1f41116`; `MainAppWindow.kt`, `CycledTabIndexTest.kt`);
  - a heart in the Windows title bar opens a dialog that supports the author's work (`b121a5b3`;
    `features/support/SupportDialog.kt`, `TitleBarActionsButtonsView.kt`, three `support_app_*.png`);
  - the private siddur scrolls with the keyboard (`21659516`, 2026-11-01, before the licence change and under the
    AGPL v3): Pardes's open siddur's screen has these 44 lines since 2026-11-11, at the user's word;
  - Nucleus 3.0.0-dev-202611011507 (`df678943`; `gradle/libs.versions.toml`);
  - the website, release CI and the Microsoft Store package.
- **Where a merge would meet Pardes's changes:** ten files Pardes also changes:
  - `CLAUDE.md`, `SeforimApp/build.gradle.kts`, `strings.xml`, `MainAppWindow.kt`, `AppUpdateService.kt` and
    `gradle/libs.versions.toml`;
  - `README.MD` and `README_HE.md`, which Pardes renamed;
  - `cla.yaml` and `deploy.yaml`, which Pardes deleted.

## 6. Not a fork on GitHub

GitHub reports efrul/Pardes as `fork: false`. It was created on 2026-10-15 at 22:30:55 UTC as a new repository,
43 seconds after the first Pardes commit (`5cfc56d6`), and a clone of Zayit was pushed into it with that commit on
top. It was first named efrul/Zayit, then renamed. GitHub marks a repository as a fork only when its Fork button or
API creates it. It offers no way to make an existing repository a fork: it documents only the way out of a fork
network, and a repository that leaves one cannot rejoin it.

In git, Pardes is a fork all the same. It holds Zayit's history up to `e8331339` (2026-06-21) and has merged its
releases since: v0.6.7, v0.6.10, v0.6.11 and v0.6.12. `git diff`, `git merge` and this map work as they would in a
fork.

What Pardes lacks is GitHub's own features:
- the "forked from kdroidFilter/Zayit" line under the name;
- the "ahead / behind" count and the Sync fork button;
- comparisons and pull requests across the two repositories.

Being a fork would also have costs:
- the web form and `gh pr create` would offer Zayit as a pull request's target;
- GitHub search leaves forks out unless asked.

Becoming one means a new fork of Zayit and everything moved to it:
- the issues and pull requests;
- the releases: Pardes for Android updates itself from this repository's releases, and their download counts would
  start again;
- the stars, the Pages site and the secrets (the signing key, Cloudflare, Kaggle).

**Decided on 2026-11-09:** Pardes stays as it is, a repository of its own that merges Zayit's releases.

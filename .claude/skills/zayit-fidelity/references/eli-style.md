# Writing as Elie Gambache writes Zayit

Zayit is written by one very skilled programmer, Elie (Elyahou) Gambache. Pardes's desktop is his code built under
another name, so code Pardes adds or changes is read next to his and must look as if he had written it. This file
holds what he wrote down (section 1), what his code and history show he does (sections 2 to 4), and how Pardes
applies it (section 5). Measured on Zayit v0.6.12 and its history up to v0.6.13 (2026-11-05); quote the file, not
this summary, when they disagree.

## 1. His written guidelines

They are in this repository, as Zayit releases them. Read them; this is a summary.

| Where | What it says |
|---|---|
| `AGENTS.md` | Kotlin + Compose, 4-space indent, lines about 120 (the linters allow 140). `PascalCase` types and composables, `camelCase` functions and properties, `UPPER_SNAKE_CASE` constants. `SomethingView` / `SomethingViewModel`. Shared logic in `commonMain`, no platform types leaking across source sets. DI through Metro (`AppGraph`, `@Provides`), reached with `LocalAppGraph`, no singletons. **Never hard-code user-visible text**: a key in `SeforimApp/src/commonMain/composeResources/values/strings.xml`, in Hebrew. Tests with Kotlin Test (and Compose UI tests), small and focused, named after the source (`BookContentViewModelTest.kt`). PRs say what changed, the modules touched, the linked issues, screenshots for UI, and how to verify. No secrets in the repository. |
| `CONTRIBUTING.md` › Code style | **Comments in code are written in English.** Kotlin follows Kotlin's conventions and idiomatic style. Run `./gradlew ktlintCheck detekt` before a PR. Every commit signed off (DCO, `git commit -s`). |
| `CLAUDE.md` (his sections) | The architecture (features under `features/`, `TabsDestination`, `TabAwareViewModel` with `saveState`/`getState`, ViewModels taken from the graph, never built by hand), the tab system's memory bounds (`TabsContent`, `MAX_RETAINED_TAB_COMPOSITIONS`), the open-core rule (core code reaches a private add-on only through its `…Feature` / `installed…` value, never by importing `addons/…`), commands, and "Important Conventions". |
| `.editorconfig` | `max_line_length = 140`; ktlint's function naming off for `@Composable`; wildcard imports allowed; the Compose rules on: content emitters, modifier reuse, multiple emitters, mutable state params, naming, parameter order, preview naming, `remember`, unstable collections, VM forwarding. |
| root `build.gradle.kts` | ktlint 1.5.0 with `io.nlopez.compose.rules:ktlint:0.5.3` on every module but `jewel` (JetBrains' fork keeps its style); detekt on every module, from `detekt.yml` over detekt's defaults. |
| `detekt.yml` | Long method 60 lines; large class 600 lines; 15 functions per file, class or object (10 per interface or enum); 6 parameters per function, 8 per constructor (defaults and data classes not counted); cyclomatic complexity 15; 3 conditions in one `if`; nesting 4 deep; 4 `return`s (guard clauses not counted); no `!!` on nullables (`UnsafeCallOnNullableType`), no unsafe casts, `check`/`error`/`require` over throwing by hand, no generic `catch` of `Throwable`/`RuntimeException`/`Error` unless named `ignore…`/`expected…`, no `GlobalScope`, no unused imports or private members, newline at end of file. Magic numbers and TODO comments are allowed. |
| `SeforimLibrary/AGENTS.md` | Public API documented with KDoc; immutable `data class`es preferred; SQLDelight queries in `dao/src/commonMain/sqldelight/…`, files `*Queries.sq`; prefer Conventional Commits `type(scope): summary`. SeforimLibrary is his library: Pardes never edits it (it is a submodule at Zayit's pin). |
| `.github/workflows/ci.yaml` | On a PR: `ktlintCheck`, the JVM tests with Kover coverage, the website build, and the installers for Linux, Windows x64/arm64 and macOS. `dco.yaml` checks every commit's sign-off. detekt is not in CI, and his own largest files go past its size limits: of v0.6.12's 350 desktop files, 22 have more than 600 lines and about 35 more than 15 functions (`SearchResultViewModel.kt`: 2,073 lines). ktlint is the gate he keeps. |
| `README-zayit-desktop.md` › Contributing | PRs bring the roadmap's bug fixes and features; a feature not on the roadmap will likely be declined. |

## 2. His code

### Structure

- A feature lives in `features/<feature>/` with `ui/`, `state/`, `usecases/` or `domain/` as it needs; app-wide
  machinery in `framework/` (DI, database, search, session, update, platform); shared pieces in `core/`
  (settings, presentation components, coroutines helpers).
- **State and events.** A screen's state is an `@Immutable` or `@Stable` `data class` with defaults for every
  field and `ImmutableList`/`ImmutableMap` for collections (kotlinx.collections.immutable). Events are a
  `sealed interface …Event(s)`; the ViewModel takes them in one `onEvent` and exposes `StateFlow`s.
- **ViewModels** are Metro-assisted:
  ```kotlin
  @AssistedInject
  class SearchResultViewModel(
      @Assisted savedStateHandle: SavedStateHandle,
      private val repository: SeforimRepository,
      // ...
  ) : ViewModel() {
      @AssistedFactory
      @ViewModelAssistedFactoryKey(SearchResultViewModel::class)
      @ContributesIntoMap(AppScope::class)
      fun interface Factory : ViewModelAssistedFactory { /* ... */ }
  ```
  App-scoped services are `@Inject` / `@SingleIn(AppScope::class)`; bindings sit in `framework/di/modules/*Bindings.kt`.
- **Use cases** are small classes over the repository with `suspend operator fun invoke(...)`, checking
  `currentCoroutineContext().ensureActive()` in long loops.
- **Settings** go through `AppSettings`: `getX()` / `setX()` and, when the UI follows it, a private
  `MutableStateFlow` with a public `xFlow: StateFlow`. Keys are `private const val KEY_…` in the companion, grouped,
  with a comment when a key is not portable (not carried by a backup): `// Not portable: a pane size, as the docked panes'`.
- **Errors** in suspend code go through `runSuspendCatching` (`core/coroutines`), which keeps cancellation;
  `runCatching` only outside coroutines.
- **Logging** through the `logger` module: `debugln { … }`, `infoln`, `warnln`, `errorln`, never `println`.
- **Private add-ons** (siddur, semantic search, quote links) sit behind `addons/<name>/enabled|disabled`; core code
  sees only the `…Feature` interface and the nullable `installed…` value.
- **Persistence**: SQLDelight `.sq` files under `commonMain/sqldelight`; tab state through `TabStateManager` /
  `TabPersistedStateStore`; preferences through `AppSettings` (multiplatform-settings).

### Compose

- Jewel's components and `JewelTheme` colours, not Material; the app's own pieces when one exists
  (`EnhancedHorizontalSplitPane`, `asStable()`, the `card()` modifier, the island layout).
- Text from `stringResource(Res.string.…)`. Hebrew literals in Kotlin are data (book and category names, calendar
  tables, abbreviations), not interface text.
- Compose rules hold (`.editorconfig`): a `modifier` parameter first among the optional ones and used once,
  `remember` around what is kept, no mutable state passed down, stable collections.
- Expensive work leaves the UI thread (`Dispatchers.IO` / `Default`, `LaunchedEffect`, `snapshotFlow` with
  `debounce` for what is saved as the user drags).

### Comments

- In English. A short `//` line above the code it explains, written as a plain statement of what the code is
  for, often a noun phrase and without a closing period:
  ```kotlin
  // The list's share of the width, resized as a docked pane and kept for every search tab
  // Up and down move the selection through the list, Enter opens it
  // No book has it: a filter on it finds nothing
  ```
  He compares with what the reader knows ("as a mail client", "as Google's", "as the docked panes'").
- KDoc `/** … */`, usually one sentence, on classes, objects and functions whose purpose is not obvious, with
  `[name]` links: `/** Keeps the body of each <!-- if:NAME -->…<!-- endif --> block whose NAME [applies], drops the others. */`.
  On Zayit v0.6.12's desktop code: 1,379 KDoc blocks and 2,656 line comments over 69,333 lines.
- Comments say what and why now. They don't tell the history of the change, name people, or keep old code
  commented out (he deletes it: `chore(reader): drop the commented-out links button`).

### Tests

- Kotlin Test (`@Test`, `assertEquals`, `assertTrue`), under `src/jvmTest/kotlin` in the source's package, class
  `<Source>Test`. Names in backticks, a lower-case sentence of the behaviour: `` `drops the blocks that do not apply` ``
  (1,119 backtick names in v0.6.12's tests).
- Small inline fixtures (`"""…""".trimMargin()`), fakes over mocks (`TestAppGraph`, `TestAppSettings`),
  `…E2ETest` for tests on a real library, Compose UI tests and screenshot tests where the UI is the point.

## 3. How he works

- **One topic, one branch, one PR**: `feat/<topic>`, `fix/<topic>`, `chore/<topic>`, `refactor/…`, `perf/…`, `ci/…`
  (since 2026-06: 76 feat, 60 fix, 26 chore). Merged as "Merge pull request #N from kdroidFilter/<branch>".
- **Commits are Conventional Commits** `type(scope): summary`. Since 2026-06: feat 203, fix 153, chore 53,
  refactor 22, perf 15, ci 15, build 10, docs 7, test 6. Scopes name the area: search (103), home (68), deps (36),
  reader (30), tabs (24), website, earthwidget, settings, author, bookcontent, onboarding, notes, siddur, …
  - The summary is lower case, says what the app now does, without a closing period, about 65 characters
    (90% under 85): `fix(tabs): Ctrl+Tab follows the strip's right-to-left order`,
    `feat(search): always show the preview, resizable results pane`.
  - The body, wrapped at about 72, says what the user sees and why, with the numbers when something was measured
    ("305,182 anchors linked, 85.6% of those whose commentary is in the DB"), `Closes #N` for an issue, and the
    `Signed-off-by:` line.
- **Docs move with the code**: `CLAUDE.md`, the READMEs and the topic READMEs (`TAB_SYSTEM_README.md`,
  `LINE_SELECTION_README.md`) are updated in the change that makes them true.
- **Tests come with fixes and features** (about a third of his feat/fix commits since August touch tests), and a
  red CI is fixed at once (`test: fix the failing tests and the ktlint violation`).
- **Dependencies** move in their own commits (`chore(deps): bump Nucleus to …`).
- **Dead code goes**: what a change makes unused is deleted in the same change (the narrow-window path of the
  search results went with `feat(search): always show the preview`).

## 4. Measuring a change against him

Before writing, read the Zayit file being changed and two or three of its neighbours: copy their structure, names
and comment voice. Then ask of the result:
- Would he have written it this way? Same layer (use case, ViewModel, view), same helpers, same naming.
- Is it the smallest change to his file? A hook calling a new Pardes file beats an edit spread through his function.
- Does it pass `./gradlew ktlintCheck detekt` on the module, and the module's tests?
- Is every user-visible text in `strings.xml`?

## 5. Pardes in his style

- Code, comments, KDoc, tests: as above, in every Pardes file, desktop or Android.
- **Commits**: Conventional Commits, as his: `feat(reader): …`, `fix(search): …`, with his scopes for his areas and
  Pardes's own scopes for Pardes's own trees: `android`, `semantic`, `daf`, `branding`, `site`, `feedback`,
  `release`, `skills`, `map` (the change map). Body as his, signed off, as efrul (CLAUDE.md › Authorship).
- **Branches**: `feat/<topic>`, `fix/<topic>` when you choose the name; a session's designated branch wins.
- **PRs inside efrul/Pardes**: what changed, the modules touched, screenshots for UI, how to verify, and the
  change map's entries for the files touched. Never a PR to Zayit (`docs/PARDES_PLAN.md` › Compatibility).

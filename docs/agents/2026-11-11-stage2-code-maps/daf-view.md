# Code map: the daf view, its review, its settings and its layout files

- **For:** stage 2 of Pardes for desktop, requests 33, 34, 29, 21 and "update by lines" (`docs/DESKTOP_STAGE2_PLAN.md`).
- **When:** 2026-11-11. Read-only: nothing was changed, built or run.
- **Code read:** efrul/Pardes at `abcb687` (main of that day). The line numbers are that commit's.
- **By:** an Explore agent of Claude Code, one of four run side by side. The report is as it came back; the brief
  it got is at the end.
- **Checked by hand afterwards:** the end bar (`VerticalBars.kt:136-176`), the review's gate (`DafView.kt:408-418`),
  `reviewItems` (`DafView.kt:930-960`) and `DafMark` (`DafReview.kt:40-80`) read as the report says. Both daf
  switches default to off and both keys are portable (`AppSettings.kt:253`, `:260`, `:848-849`).

## The report

Paths are shortened. D = SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/
and S = SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/. I changed, built and ran nothing.

### A. The review in the right-click menu

- The menu is built in D/DafView.kt:841-849, inside `AmudItem` (723).
  `ContextMenuDataProvider(items = { if (review == null || word == null) emptyList() else reviewItems(...) })`.
- `reviewItems` is at D/DafView.kt:934-959. It adds a `ContextMenuDivider`, then
  `ContextMenuCheck(labels.layout, …) { onSay(lines, DafReason.LAYOUT, it) }` (952) and
  `ContextMenuCheck(labels.words, …)` (953). If the line has an old-style mark (`isFormer`), it also adds a "clear"
  item (954-957).
- D/DafReviewMenu.kt only holds the pieces: `ContextMenuCheck` (39), `ContextMenuCheckRow` (48) and
  `DafReviewsFolderLink` (79). The menu draws them at S/features/bookcontent/BookContentScreen.kt:224
  `is ContextMenuCheck -> passiveItem { ContextMenuCheckRow(item) }`.
- How `daf_review` gates it: D/DafView.kt:412-414 `val reviewOn by graph.appSettings.dafReviewFlow…`, then
  `review = if (reviewOn) … reviews.of(file) else null`. A null `review` gives an empty list at 844. The key is
  AppSettings.kt:952; the flow is 50, the getter/setter 260-265.
- The two checkboxes and their texts are in strings.xml:
  - `daf_review_layout` "הקטע מעומד לא נכון" (47)
  - `daf_review_words` "יש טעות במילים" (48)
  - also `daf_review_clear` "ביטול הסימון של השורה" (49) and `daf_review_open_folder` (50)
  - The labels are read in `reviewLabels()`, DafView.kt:900-906.
- Data class `DafMark`, D/DafReview.kt:42-55. Fields: amud, c, i, t, `ok: Boolean? = null`, cut, at, `layout`,
  `words`, from, to, `version: Int?`.
- It sits inside `DafReviewFile` (18-29): `format: Int = FORMAT` (FORMAT = 1, line 27), tractate, tractateHe,
  library, marks.
- Writing (class `DafReviews`, 194):
  - folder `<library>/pardes-daf/feedback/` (`FOLDER = "feedback"`, 284)
  - one file per tractate, `${tractate}.json` (`fileOf`, 278)
  - written through a `.part` file and an atomic move (`write`, 267-276), pretty-printed with defaults (197-202)
  - entry points are `say` (234) and `mark` (224), through `marked` (253)
- Reading: `DafReviews.of` (213-220) uses `ignoreUnknownKeys`. A file it cannot read is renamed
  `.unreadable-<time>.json`.
- `ok` is still read in three places:
  - `reviewColors`, DafView.kt:917 `when (mark.ok) { true -> REVIEW_OK; false -> REVIEW_WRONG; null -> REVIEW_MOVED }`
  - `isEmpty` (DafReview.kt:56) and `isFormer` (59)
  - `saying()` still writes `ok = if (wrong) false else null` (74)
- Sending: `FeedbackSource` in S/features/settings/feedback/FeedbackPackage.kt:306.
  - `dafReviews(since)` (404-412) reads every `*.json` in the folder as `DafReviewFile`.
  - `dafFolderOf` is at 417.
  - The package field is `daf: List<DafReviewFile>` (36).
  - Other files there: FeedbackSender.kt, FeedbackAutoSender.kt, FeedbackSendCard.kt.

### B. `daf_mark_unsure` and how marks are drawn

- Where it is read:
  - AppSettings.kt:253 (key 951, flow 46)
  - DafView.kt:751 `val markUnsure by graph.appSettings.dafMarkUnsureFlow…`, passed on at 881
- How unsure lines are drawn: D/DafPage.kt:371
  `val unsureInk = if (markUnsure) ink.copy(alpha = ink.alpha * UNSURE_INK) else null`, with `UNSURE_INK = 0.55f`
  (280). The colour is applied at 199 `if (line.unsure && unsureInk != null) addStyle(SpanStyle(color = unsureInk)…)`.
- Which lines count as unsure: `unsure = lines[i].s != 1`, D/DafSetting.kt:414 and 611.
- What a marked passage looks like: a tinted box behind the whole line.
  - `reviewColors` (DafView.kt:909-922) maps line index to colour: `REVIEW_OK 0x3328A745`, `REVIEW_WRONG 0x40DC3545`,
    `REVIEW_MOVED 0x40E0A000` (924-926).
  - It is merged into `highlight` at DafView.kt:803 as `reviewed[word.line]`, lowest priority.
  - Drawn by `drawMarks` (DafPage.kt:612) as rounded rects.
  - `from`/`to` are not used in the tint: it colours the whole line.

### C. VerticalBars.kt

- End bar `EndVerticalBar` (85), top part (139), in order:
  1. `ZoomButtons(tabId)` (140, defined 327):
     - zoom in (337): icon `ZoomIn`, tooltip `zoom_in_tooltip`, `appSettings.zoomIn(tabId)`, `enabled = canZoomIn`
     - zoom out (352): icon `ZoomOut`, tooltip `zoom_out_tooltip`, `zoomOut(tabId)`, `enabled = canZoomOut`
  2. `DiacriticsButton` (146, defined 386): icon `TextDiacritics`; tooltip is one of
     `show_diacritics_tooltip`/`hide_teamim_tooltip`/`hide_nikud_tooltip`/`hide_diacritics_tooltip`;
     `CycleDiacritics`; shown if a book is selected (143) and it `hasNekudot || hasTeamim` (144-145).
  3. Shnayim mikra (155): icon `JournalText`, tooltip `shnayim_mikra_mode_tooltip`, `ToggleShnayimMikra`, shown if
     `isChumash`.
  4. Daf (166): icon `GemaraPage`, tooltip `daf_view_tooltip`, `onEvent(BookContentEvent.ToggleDafView)`,
     `isSelected = uiState.content.dafView`, shown if `selectedBook.isBavli` (165).
- Only the zoom buttons depend on the daf being shown: 331-336
  `val dafViews by appSettings.dafViewsFlow…; val daf = tabId in dafViews`, which changes limits and hints. No button
  is hidden or disabled because of it.
- How the bar knows: the toggle reads the state field `BookContentState.content.dafView`
  (state/BookContentState.kt:214). Zoom reads `AppSettings.dafViewsFlow` (55), filled by `dafShown`/`dafHidden` from
  BookContentView.kt:225-227.
- Bottom part of the end bar: book details (193, `TablerInfoSquare`), targum (253), references (267), sources (285),
  commentaries (299).
- `StartVerticalBar` (37): books (47), table of contents (58), notes (71).

### D. Settings page (features/settings/daf/)

- DafSettingsScreen.kt `DafSettingsView` (43). It uses Jewel's `CheckboxRow` and plain `Text`, with no `SettingCard`.
  The state and events come from `FontsSettingsViewModel`.
  - `CheckboxRow` (53): `settings_daf_mark_unsure` / `_info`, `SetDafMarkUnsure`, key `daf_mark_unsure`
  - `CheckboxRow` (66): `settings_daf_review` / `_info`, `SetDafReview`, key `daf_review`; when on, also
    `DafReviewsFolderLink()` (76)
  - `DafLayoutsCard()` (79)
- DafLayoutsCard.kt:65, content at 83: its own bordered Column (90-99), with strings `daf_layouts_*` and
  `daf_download_failed`/`_retry`.

### E. Layout files

- Folder: `DafLayouts.DIR_NAME = "pardes-daf"` (D/DafLayout.kt:190).
  - `of(libraryDir)` is at 205; `rememberDafLayouts` (DafView.kt:1072) finds the folder beside the seforim.db.
  - `scan` (163) reads the header from the first 4096 bytes; `parse` (183) accepts formats 1..2.
- Model:
  - `DafLayoutFile` (20): format, tractate, tractateHe, amudim, library, `version`
  - `DafAmud` (38): amud, page, block
  - `DafLine` (54): `c` (G/R/T), `b` box, `t` words, `s` sure, `d` bold, `u` `[lineId, from, to]`, `big`
- Version: `versionOf` (108) uses the regex `"version"` (200) on the file's head.
- Manifest: `DafManifest` (DafDownloads.kt:40). Format 1, with `daf` and per-entry `version`, `jsonSha256`, `size`.
- Update check: `DafReleases` (76).
  - URLs: `API_URL` github releases of `SemanticPack.REPOSITORY` (120), fallback
    `SITE_URL = "https://efrul.github.io/Pardes/daf.json"` (121)
  - releases tagged `daf-<n>` (125), asset `pardes-daf.manifest.json` (123)
  - checked by `DafDownloads.refresh` (218)
- Quiet row: `DafUpdateLine` (DafUpdate.kt:48), using `newerFor` (34), strings `daf_update_available` /
  `daf_update_now`. Placed at DafView.kt:336-338, bottom start.
- Download and replace: `DafDownloads.download` (264) and `install` (302): gz to `.download`, unzip, sha check, then
  `DafLayouts.import` (DafLayout.kt:138), which does an atomic move and deletes the tractate's former files.
- Sizes: the only figure I found is DafPageTest.kt:430 "white page … 12 KB of PNG, a drawn one some 880 KB". The test
  resource berakhot-35b.json is 20767 bytes.

### F. Architecture

- Files in D (line counts):
  - DafAmudLinks.kt 100: an amud's comments and the Gemara lines they sit on
  - DafAnchors.kt 285: aligns printed words to library words (highlights, notes, find, `locate`)
  - DafDownloads.kt 362: manifest, releases, downloads
  - DafIndex.kt 102: where each line is placed, `gemaraAt`, comment order `next`
  - DafLayout.kt 210: model and folder
  - DafLemmas.kt 228: matching a dibbur's quoted words
  - DafPage.kt 632: drawing an amud, selection, marks
  - DafReview.kt 287: reviews
  - DafReviewMenu.kt 94: menu checkbox and folder link
  - DafSetting.kt 690: typesetting
  - DafUpdate.kt 85: the quiet row
  - DafView.kt 1324: the screen, the list, the menu, `DafControl`
- (1) Select/focus: `clickOn` (DafView.kt:1297) and `dafHighlight` (1270); `focused` state (433); `DafAmudText`
  gestures (DafPage.kt:422-472). All take layout input.
- (2) Arrow keys: `stepLine` in BookContentView.kt:921-927 calls `DafControl.stepComment` (DafView.kt:207), whose body
  is `control.step` (518-547) over `DafIndex.next`. Layout-based.
- (3) Back to the text:
  - `DafControl.place()` (210) and `control.where` (551)
  - BookContentView.kt:1027-1069 opens the text where the daf was
  - `selectWordInText` (1009) takes the `onWordInText` callback
  - `openOn` (DafView.kt:501)
  - All layout-based except the text-side code.
- (4) Zoom:
  - `AppSettings.zoomIn`/`zoomOut` (301-318) and `zoomDaf` (289)
  - keys in S/core/presentation/utils/WindowUtils.kt:64-75; menu in AppNativeMenuBar.kt:302/308; buttons
    VerticalBars.kt:327
  - pinch/wheel: `rememberTextZoom` at BookContentView.kt:210-218
  - The rescale anchor (DafView.kt:472-485) uses the layout's points; everything else is independent of the layout.
- (5) Typesetting: `layoutAmud` (DafSetting.kt:109), `AmudSetter` (200), `ColumnSetter` (336), drawn by `DafAmudText`
  (DafPage.kt:344). Both take `DafAmud` boxes (`DafLine.b`).

### G. Icons

- The requested `git log -- '*Daf*Icon*' '*daf*icon*'` returned nothing; no file matches those names. The icon is
  icons/src/commonMain/kotlin/io/github/kdroidfilter/seforimapp/icons/GemaraPage.kt (69 lines).
- `git show --stat ccd1866` ("feat(daf): an icon that recalls a page of the Talmud") lists 1988 files. The clone is
  shallow (`is-shallow-repository` true) and ccd1866 is its oldest commit, so the stat shows the whole tree;
  GemaraPage.kt is among them (+69).
- Form: a Kotlin `ImageVector` built once with `ImageVector.Builder` and `path {}`, 16x16 viewport, cached in
  `_GemaraPage`. It lives in the `icons` module, in the same folder as its ~68 sibling icons. Before this it was
  `ColumnsGap` (docs/ZAYIT_CHANGE_MAP.md:2032-2039).
- Separately, the app has SVG resources in SeforimApp/src/jvmMain/resources/icons/*.svg, with light and `_dark`
  pairs.

### Not confirmed

- Exactly how FeedbackSender/FeedbackAutoSender upload the package; I did not read them.
- The full text of `dafHighlight`/`clickOn` and of `loadLinks` (DafView.kt:1211).

## The brief the agent got

Read-only code mapping in the Kotlin desktop app at /home/user/Pardes (Compose Multiplatform + Jewel). I am writing an
implementation plan and need exact anchors (file path + line numbers + the name of the function/val), not design
advice. Do not change, build or run anything. Thoroughness: very thorough within the scope below.

Scope: the "daf view" (צורת הדף, the Vilna page layout of a Talmud amud) and everything around it.
- Main code: SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/daf/
- Its settings page: SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/daf/
- The reader's side bar with buttons:
  SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/ui/components/VerticalBars.kt
- Settings keys: SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/settings/AppSettings.kt (keys
  `daf_review`, `daf_mark_unsure` and any other `daf_*`)
- Strings: SeforimApp/src/commonMain/composeResources/values/strings.xml
- Tests: SeforimApp/src/jvmTest/kotlin/... (same packages)

Answer each of these, each with file:line anchors and a short quote of the deciding lines:

A. The review ("דיווח") in the right-click menu. Where is the context menu of the daf view built (DafReviewMenu.kt and
its caller)? Exactly how does the `daf_review` setting gate it? What are the two checkboxes (their string keys and
Hebrew texts), what data class holds a review mark, which fields exist (I expect `layout`, `words`, and a legacy
`ok`), where is it written to disk (folder, file name, JSON shape, any format/version field), and where is it read
back? Is `ok` still parsed anywhere on the read side? How are the marks sent to the feedback service (which class
picks up the files)?

B. `daf_mark_unsure`: where is it read, and how are "unsure" lines drawn differently? Where are the review marks
themselves drawn on the page (what the user sees on a marked passage)?

C. The reader's end bar (VerticalBars.kt): list every button of the bar that holds zoom in, zoom out, ניקוד
(diacritics) and צורת הדף, in order, with for each: the composable call and its line, icon, tooltip string key,
onClick, and any visibility/enabled condition. Is there already any condition that depends on whether the daf view is
showing? How does the bar know the daf view is on (which state field)? Also list which other buttons the same file
declares (the other bar), briefly.

D. The daf settings page (features/settings/daf/): every card/toggle on it with its composable, the setting key, the
string keys. Which shared setting components does it use (e.g. `SettingCard` from
features/settings/ui/SettingsComponents.kt, or its own)?

E. The layout files: how are they found and loaded (the `pardes-daf/` folder beside the library), the JSON format as
the Kotlin model sees it (per-line fields), the per-tractate version (where in the header), the release manifest and
the update check (which URL/release, which class), the "quiet row" shown inside the daf view when a newer version
exists, and the download/replace code. Any size figures in comments or tests.

F. Architecture of the view for reuse elsewhere: list every .kt file in features/bookcontent/daf/ with its line count
and one line on what it does; then name the pieces that implement (1) selecting/focusing a line or block, (2)
arrow-key movement, (3) "go to the text" / opening the reader at the daf's place, (4) zoom (keys, buttons, pinch), (5)
drawing a line of text into a box (the typesetting function). Say which of these take the layout file's boxes as input
and which are independent of it.

G. Icons: how was the daf button's icon added (look at
`git -C /home/user/Pardes log --stat -3 --format='%h %s' -- '*Daf*Icon*' '*daf*icon*'` and commit ccd1866 with
`git -C /home/user/Pardes show --stat ccd1866`)? In what form are such icons kept (an ImageVector built in Kotlin, an
SVG/XML drawable resource), and in which folder?

Report format: plain text, one section per letter, bullets with `path:line`. Put anything you could not confirm under
a final "Not confirmed" heading rather than guessing. Do not include advice or a plan. Keep it under about 900 words.

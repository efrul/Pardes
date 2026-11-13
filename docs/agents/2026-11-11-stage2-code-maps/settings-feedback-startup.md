# Code map: the settings window and its pages, the feedback's controls, and the start's order

- **For:** stage 2 of Pardes for desktop, requests 30, 35 and 29 (`docs/DESKTOP_STAGE2_PLAN.md`).
- **When:** 2026-11-11. Read-only: nothing was changed, built or run.
- **Code read:** efrul/Pardes at `abcb687` (main of that day). The line numbers are that commit's.
- **By:** an Explore agent of Claude Code, one of four run side by side. The report is as it came back; the brief
  it got is at the end.
- **Checked by hand afterwards:**
  - `SettingsWindow.kt:40-125` and `MainAppWindow.kt:296-308` read as the report says, and so does the tool
    windows' host (`ToolWindows.kt:62-130`).
  - Its last section is right: `DbDeltaUpdateSection` and `DbDeltaUpdateViewModel` have no call site in `jvmMain`
    (only their test), `DbDeltaRecoveryBootstrap.runOnce()` is commented out in `main.kt:173`, and the data page
    (`DataSettingsScreen.kt:150-185`) shows no update of the library. So Pardes for desktop, as Zayit 0.6.12, has
    the delta update's code and no screen for it. `DbDeltaUpdateService.checkAndApply` (`:69`) calls the library's
    `client.checkForUpdate()` first: a check with no download exists one level down.

## The report

P = /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp. Nothing was changed, built or
run.

### A. Settings window

- P/features/settings/SettingsWindow.kt:45
  `NucleusApplicationScope.SettingsWindow(onClose, initialDestination: SettingsDestination? = null)`. Lines 62-65
  wrap it in their own `IntUiTheme(buildThemeDefinition(), buildComponentStyling())`. Line 67:
  `rememberDialogState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(700.dp, 500.dp))`. Line 68
  opens
  `JewelDecoratedDialog(onCloseRequest = onClose, title = settings, icon = AppIcon, visible = true, resizable = true)`.
  Lines 76-80 provide `rememberWindowViewModelStoreOwner()` as both `LocalWindowViewModelStoreOwner` and
  `LocalViewModelStoreOwner`. Line 81: `JewelDialogTitleBar`. Lines 100-107 set up `rememberNavController()` and
  navigate to `initialDestination` with `popUpTo(General)`. The sidebar is 120.dp wide (:117). Line 158: a close
  button.
- What makes it modal is where it is composed. P/core/presentation/window/MainAppWindow.kt:301-306 shows it only
  `if (isVisible && ownerWindowId == openWindow.id)`, with the comment "modal to this window only". If the owner
  window is gone, main.kt:443-449 composes it at app scope instead (app-modal).
- State: `SettingsWindowViewModel` (P/features/settings/SettingsWindowViewModel.kt:17-41) holds
  `SettingsWindowState(isVisible, initialDestination, ownerWindowId)` (SettingsWindowState.kt). The events are
  `OnOpen`, `OnOpenTo(destination)` and `OnClose` (SettingsWindowEvents.kt). `ownerWindowId` is
  `desktopManager.focusedWindowId.value`. One instance app-wide: main.kt:303-305 creates it with
  `metroViewModel(viewModelStoreOwner = windowViewModelOwner)`.
- Call sites that open it:
  - TitleBarActionsButtonsView.kt:43-44 resolves the view model; :169 `OnOpen`
  - MainAppWindow.kt:220 and :453 (Ctrl/Cmd+Comma): `OnOpen`
  - AppNativeMenuBar.kt:178 `OnOpenTo(About)`, :184 `OnOpenTo(Conditions)`, :192 `OnOpen`
  - main.kt:398 (the semantic prompt): `OnOpenTo(SettingsDestination.Data)`
- Opening on a card: P/features/settings/semantic/SemanticPackPrompt.kt:26 keeps an `AtomicBoolean focus`.
  `opened()` (:63) sets it before `open()` (:45-46). `takeFocus()` (:61) is read once by SemanticPackCard.kt:106
  `val prompted = remember { SemanticPackPrompt.takeFocus() }` and passed as `marked`. That gives a
  `BringIntoViewRequester` (:151-157) and a 2.dp focused border (:164). Only the page is a navigation argument; the
  card is chosen through this side channel.
- Navigation: `sealed interface SettingsDestination` (navigation/SettingsDestination.kt:6, data objects; `Daf` at
  :30). `SettingsNavHost` (navigation/SettingsNavHost.kt:24) uses `startDestination = General` and
  `noAnimatedComposable<T>` (:57). Sidebar `allItems` is in ui/SettingsSidebar.kt:68-110, with the selection `when`
  at :119-128.
- Pages in sidebar order (destination → screen):
  1. General → GeneralSettingsScreen (ui/GeneralSettingsScreen.kt:82)
  2. Display → DisplaySettingsScreen (ui/DisplaySettingsScreen.kt)
  3. Profile → ProfileSettingsScreen (ui/ProfileSettingsScreen.kt)
  4. Fonts → FontsSettingsScreen (ui/FontsSettingsScreen.kt)
  5. Daf (label `daf_view` "צורת הדף") → DafSettingsScreen (daf/DafSettingsScreen.kt:36)
  6. Data → DataSettingsScreen (ui/DataSettingsScreen.kt:71)
  7. About → AboutSettingsScreen
  8. Conditions → ConditionsSettingsScreen
- `git show --stat e79ef1c` ("feat(settings): the daf view has a settings page of its own"), 12 files, +192 −79:
  - .github/scripts/daf_release.py
  - strings.xml
  - bookcontent/daf/DafDownloads.kt
  - bookcontent/daf/DafView.kt
  - settings/daf/DafLayoutsCard.kt
  - settings/daf/DafSettingsScreen.kt (+106)
  - navigation/SettingsDestination.kt (+4)
  - navigation/SettingsNavHost.kt (+4)
  - ui/DataSettingsScreen.kt (−3)
  - ui/FontsSettingsScreen.kt (−53 net)
  - ui/SettingsSidebar.kt (+8)
  - docs/ZAYIT_CHANGE_MAP.md

### B. Separate non-modal window precedent

- P/features/home/widgets/ToolWindows.kt:103 `DefaultToolWindow`. It uses `JewelDecoratedWindow` (import
  `dev.nucleusframework.window.jewel.JewelDecoratedWindow`, :23) inside `with(LocalNucleusApplicationScope.current)`
  (:111-117) and
  `rememberWindowState(position = WindowPosition(Alignment.Center), size = tool.toolWindowSize!!)` (:110), with
  `minimumSize = tool.toolWindowMinSize`.
- Open-state: `@Inject @SingleIn(AppScope::class) class ToolWindows` (:45-60), with
  `open = mutableStateListOf<HomeWidget>()` and `reopened = mutableStateMapOf<String, Int>()`. `open(tool)` bumps
  `reopened[id]` if the tool is already open, otherwise adds it. It is exposed as `AppGraph.toolWindows`
  (framework/di/AppGraph.kt:84).
- Bringing it to front: `LaunchedEffect(reopened)` calls
  `window.setMinimized(false); toFront(); requestFocus()` (:119-124).
- Closing: `ToolWindowsHost` (:64-77), composed once at main.kt:460, passes `onClose` =
  `open -= tool; reopened -= tool.id`.
- Title bar:
  `JewelTitleBar(Modifier.newFullscreenControls(), gradientStartColor = ThemeUtils.titleBarGradientColor(), controlButtonsDirection = SystemNative) { Text(title) }`
  (:133-139). The body is `Box(trackActivation().fillMaxSize().background(panelBackground).padding(8.dp))`
  (:140-146).
- Dictionary: DictionaryWindow.kt:79-82 `DictionaryToolWindow` =
  `CompositionLocalProvider(LocalDictionaryWindow provides true) { DefaultToolWindow(...) }`.
  `DictionaryLookup.open(word)` (:68-71) calls `toolWindows.open(DictionaryWidget)`. Size:
  DictionaryWidget.kt:372-373 `DpSize(460.dp, 560.dp)`, min `340×380`.
- Wrapping: there is no own Metro graph and no own IntUiTheme. The window inherits main.kt:263-282 (`LocalAppGraph`,
  `LocalMetroViewModelFactory`, `LocalLayoutDirection Rtl`, `LocalWidgetAntiAliasing`, `IntUiTheme`,
  `NucleusDecoratedWindowTheme`). A view-model owner is made ad hoc: ToolWindows.kt:83
  `rememberWindowViewModelStoreOwner()` (core/presentation/utils/WindowViewModelOwner.kt:11,17). Main windows also
  use `JewelDecoratedWindow` (MainAppWindow.kt:153) and add `LocalOpenWindow`, `LocalWindowViewModelStoreOwner`,
  `LocalViewModelStoreOwner` and `LocalUriHandler` (:309-314).
- P/features/update/UpdateDialog.kt:58: its own `IntUiTheme`, then `JewelDecoratedDialog`, `DpSize(440,340)`,
  `resizable = false` (:62-73). Shown at main.kt:450-455 from `appUpdateService.dialogVisible`.
- P/features/settings/feedback/FeedbackConsentDialog.kt:67: own `IntUiTheme`, `JewelDecoratedDialog`,
  `DpSize(560,600)`, `resizable = false`, `onCloseRequest = autoSender::dismiss`, `JewelDialogTitleBar` (:72-89).
  Shown at main.kt:457-458 from `feedbackAutoSender.askVisible`. Both are composed at app scope.

### C. SettingCard

- ui/SettingsComponents.kt:32
  `internal fun SettingCard(title: StringResource, description: StringResource, checked: Boolean, onCheckedChange: (Boolean) -> Unit)`.
  It draws a rounded 8.dp bordered column, a Checkbox and title Text (:60-64), and an `IconButton` with
  `ExpandCollapseIcon` (:67-72) that toggles `AnimatedVisibility` of the 12sp info description (:75-82).
- Uses:
  - GeneralSettingsScreen.kt:129, :136, :143
  - DisplaySettingsScreen.kt:100, :107
  - DataSettingsScreen.kt:179 (error reporting)
  - siddur/OpenSiddurCard.kt:20 (`OpenSiddurCard` is built on it, `settings_open_siddur`,
    `AppSettings.setOpenSiddurEnabled`, key "open_siddur")
- Cards not built from it:
  - library/LibraryLocationCard.kt:74 `LibraryLocationCard`: folder, move/back-to-default, keep-copy CheckboxRow
    (:126), copies and delete.
  - semantic/SemanticPackCard.kt:100 `SemanticPackCard` → `SemanticPackCardContent` (:144): `PackSection` (:208),
    with the prompt-at-start CheckboxRow at :240-244; `FeedbackSection` (:188).
  - daf/DafSettingsScreen.kt:43 `DafSettingsView`: inline CheckboxRows, not cards. Mark-unsure is :53; review is
    :66, with `DafReviewsFolderLink()` at :76. Then `DafLayoutsCard()` (daf/DafLayoutsCard.kt:65, layouts
    download/update per tractate).
  - feedback/FeedbackSendCard.kt:105 `FeedbackSendCard` → `FeedbackSendCardContent` (:163).
  - DataSettingsScreen.kt:165/167/169 add `LibraryLocationCard()`, `SemanticPackCard()`, `FeedbackSendCard()`. The
    `if (isErrorReportingAvailable)` wrapper is at :177.
  - GeneralSettingsScreen.kt:150 adds `OpenSiddurCard()`.

### D. Feedback

- FeedbackSendCard (Data page). Kind switches persist through `FeedbackSender.keep`
  (feedback/FeedbackSender.kt:84-90) to AppSettings `feedback_kind_<kind>` (AppSettings.kt:466-476, prefix :964).
  Kinds are "grades", "searches", "daf", "publish", "asked-of-others" (FeedbackSender.kt:217-223); the other apps
  are "zayit" and "otzaria" (FeedbackPackage.kt:93-97).
  - Grades: :264 `feedback_send_grades` "ציונים לתוצאות החיפוש: %1$d…"
  - Pardes's own searches: :269 `feedback_send_searches` "היסטוריית החיפושים: %1$d"
  - Daf marks: :275 `feedback_send_daf`
  - Zayit/Otzaria: :281-293, labels `feedback_consent_zayit`/`_otzaria`, with counts
    `feedback_send_zayit`/`_otzaria`; only apps found on the machine are listed.
  - Per-query review: :318 `feedback_send_review`
  - "All again": :190 `feedback_send_all`
  - Publish: :225 `feedback_consent_publish` "פרסום במאגר הפתוח" (kind "publish")
  - Automatic sending: :213 `feedback_send_auto` "שליחה אוטומטית, בכל הפעלה ובכל חיבור לרשת" →
    FeedbackSendViewModel.kt:167-169 `setFeedbackAutoSend` plus `setFeedbackAsked(true)` (keys
    "feedback_auto_send", "feedback_asked")
  - Sender id and renew: :365, :370
  - Send now / save: :418-421
  - Open folder: :424 `feedback_send_open_folder`, shown only after a save; it opens the saved file's parent folder
    (:134-135)
  - Delete sent: :239
- What was sent so far: only the `feedback_send_sent` banner with a date (:385), `feedback_send_nothing_new` with
  the last-sent date (:432, `getFeedbackSentAt("package")`), and `search_feedback_count` on the semantic card
  (SemanticPackCard.kt:202-206). There is no list of sent items.
- Rating-buttons switch: SemanticPackCard.kt:193-197 `search_feedback_setting` "משוב על תוצאות החיפוש" →
  SemanticPackViewModel.kt:170-171 `setSearchFeedbackEnabled` (key "search_feedback").
- Daf review: DafSettingsScreen.kt:66-69 `settings_daf_review` → FontsSettingsViewModel.kt:86-87
  `setDafReviewEnabled` (key "daf_review"). Folder link: bookcontent/daf/DafReviewMenu.kt:79
  `DafReviewsFolderLink`, `daf_review_open_folder` "פתיחת תיקיית המשוב".
- Question at start: FeedbackConsentDialog.kt `FeedbackConsentContent` (:106).
  - Kinds: grades, buttons (`feedback_consent_buttons`, offered when `!isSearchFeedbackEnabled`), searches, daf,
    other apps, publish (:131-171)
  - Answer: `FeedbackAutoSender.answer` (feedback/FeedbackAutoSender.kt:114-127)
  - Asked when `!isFeedbackAsked() || askOfOthers()` (:77). Dismissing asks again at the next start (:130).
- Question at install: P/features/onboarding/feedback/FeedbackStepScreen.kt:56 `FeedbackStepScreen`.
  - Controls: `onboarding_feedback_send` (:126), sources row (`onboarding_feedback_sources`, app_name, other apps;
    :129-150), buttons (:152), publish (:160). All start checked (:102-106).
  - Routed from onboarding/region/RegionConfigScreen.kt:51,71. The val named `feedbackAsked` actually holds
    `isQuestionOpen()` (FeedbackAutoSender.kt:62).
- P/core/feedback/SearchFeedbackStore.kt holds the grade store (`FeedbackGrade`, `SearchFeedback`) and has no UI.

### E. Startup (main.kt)

- :216 `PendingSemanticPack.runOnce()`, :219 `PendingLibraryMove.read()`, :222-229 graph creation, :241
  `computeStartupState` (:114-135: onboarding → DB version check → `showDatabaseUpdate`).
- :283-294 gates: library move window, else onboarding, else `DatabaseUpdateWindow`, else the main app. Everything
  below runs only in the main-app branch.
- :367-373 session restore: `restoreIfEnabled(); sessionRestored = true`.
- :377-379 `if (isAutoUpdateCheckEnabled()) appUpdateService.checkOnStartup()`. It sets state only; `dialogVisible`
  is set by `openDialog()` (framework/update/AppUpdateService.kt:411), called only from the title-bar icon
  (TitleBarActionsButtonsView.kt:91).
- :391-406 sequence:
  `SemanticPackPrompt.openAtStart(appSettings, snapshotFlow{sessionRestored}, desktopManager, dialogVisible){ OnOpenTo(Data) }`.
  Inside (SemanticPackPrompt.kt:40-46): `shouldOpen` check, restored, windows non-empty, focus (5s timeout),
  `updateDialogVisible.first { !it }`. It suspends sequentially, then
  `feedbackAutoSender.run { sessionRestored; windows.isNotEmpty(); dialogVisible.first{!it}; settingsWindowViewModel.state.first { !it.isVisible } }`.
  So the feedback question waits for the settings window, including the prompt's, to close.

### Not confirmed

- `DbDeltaUpdateSection` (settings/dbupdate/DbDeltaUpdateSection.kt:37) has no call site in jvmMain. I found no
  runtime database-delta check at startup beyond `computeStartupState`.
- DafSettingsScreen.kt is 82 lines now while e79ef1c added 106. Later commits changed it; not traced.

## The brief the agent got

Read-only code mapping in the Kotlin desktop app at /home/user/Pardes (Compose Multiplatform + Jewel, windows from
the Nucleus framework). I am writing an implementation plan and need exact anchors (file path + line numbers +
function/val names), not design advice. Do not change, build or run anything. Thoroughness: very thorough within the
scope below.

Base package dir (call it P): /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp
Strings: /home/user/Pardes/SeforimApp/src/commonMain/composeResources/values/strings.xml
Settings keys: P/core/settings/AppSettings.kt

Answer each, with file:line anchors and a short quote of the deciding lines:

A. The settings window. P/features/settings/SettingsWindow.kt: how is it shown (I expect a `JewelDecoratedDialog`),
with what size/position/properties, and what makes it modal? Who decides that it is open (which state holder /
event, and every call site that opens it, e.g. the title bar button in
P/core/presentation/components/TitleBarActionsButtonsView.kt, main.kt, prompts)? Is there a way to open it directly
on a given page or card (how does P/features/settings/semantic/SemanticPackPrompt.kt do it)? How do the settings
pages navigate (P/features/settings/navigation/): the destination type, the sidebar items list, the NavHost. List
every page in sidebar order with its destination name, its screen composable and file. Then show exactly which files
the commit that added the daf page touched: run
`git -C /home/user/Pardes show --stat --format='%h %s' e79ef1c`.

B. The precedent for a separate non-modal window: P/features/home/widgets/ToolWindows.kt and
P/features/home/widgets/library/DictionaryWindow.kt. Which window composable/API do they use (name and import), how
is "open once, a second open brings it to front" implemented, where is its open-state kept, how is it closed, what
are its size and title-bar pieces? Does it get its own Metro graph / theme / CompositionLocals wrapper (what must
wrap the content of a new window so that it looks and works like the main one)? Also note how
P/features/update/UpdateDialog.kt and P/features/settings/feedback/FeedbackConsentDialog.kt show their windows.

C. `SettingCard` in P/features/settings/ui/SettingsComponents.kt: its full signature and what it renders (title,
checkbox, the arrow that opens a description). List each place it is used. Then list the Pardes settings cards that
are NOT built from it, each with file, composable name, and what it holds: the library-location card
(P/features/settings/library/), the semantic pack card (P/features/settings/semantic/SemanticPackCard.kt), the
open-siddur card, the daf page's cards (P/features/settings/daf/), the feedback card(s)
(P/features/settings/feedback/), and anything else Pardes added to P/features/settings/ui/DataSettingsScreen.kt and
GeneralSettingsScreen.kt.

D. Everything about feedback, wherever it is: P/features/settings/feedback/, P/core/feedback/,
P/features/onboarding/feedback/, plus any feedback switch that lives on another card (the rating buttons' switch on
the semantic pack card, the daf review mode on the daf page). For each user-visible control: file:line, composable,
its Hebrew label (string key), the AppSettings key or state behind it. Specifically find: automatic sending, which
sources are included (Pardes's own searches, an installed Zayit's, Otzaria's), "publish" consent, the rating buttons
switch, the link/button that opens the feedback folder, any display of what was sent so far, the consent question at
install and the one shown at start.

E. Startup order in /home/user/Pardes/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/main.kt (and
whatever it delegates to): in what order do these happen and what gates each: session/tabs restore, the app update
dialog, the semantic pack prompt (SemanticPackPrompt), the feedback consent dialog, any database-update check. Quote
the lines that sequence them (e.g. a prompt that waits for the update dialog to close).

Report format: plain text, one section per letter, bullets with `path:line`. Put anything you could not confirm
under a final "Not confirmed" heading rather than guessing. No advice, no plan. Keep it under about 1000 words.

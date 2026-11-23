# Spec: back and forward in a tab, recent places, a lesson mode and pinned headings

- **For:** items 12 to 15 of the proposals the user chose from `docs/INTELLIJ_LESSONS.md` (back and forward inside a tab, recent places with a line of text, a lesson mode for a projector, headings pinned above the text); the choice and its state are in `docs/DESKTOP_STAGE3_PLAN.md`.
- **When:** 2026-11-21. Read-only: nothing was changed, built or run, and no library was at hand; what was not
  checked is marked "(לא נבדק)" in the report.
- **Code read:** efrul/Pardes at `21999b0` (main of that day), with Zayit's submodules at their pins (SeforimLibrary
  `4f07145`, SeforimAcronymizer `dea83b3`); efrul/Nucleus at `65ccd12` and the Nucleus jars Pardes builds against;
  JetBrains/intellij-community at `b2ba8bcb`. The line numbers are those commits'.
- **By:** a general-purpose agent of Claude Code, one of four run side by side. The report is as it came back, in
  Hebrew, but for its headings set one level down and the session's local paths made relative; the brief it got is
  summed up at the end.
- **Checked by hand afterwards:** nothing yet. The user asked for these details before anything is built, and has
  not gone over the open decisions (`docs/DESKTOP_STAGE3_PLAN.md` › ההצעות שנבחרו מדוח IntelliJ).

## The report

<div dir="rtl">

## ניווט וקריאה: מפרט מימוש לפריטים 12–15 (מאומת בקוד)

המפרט נכתב מתוך הקוד עצמו, בלי לשנות שום מאגר: פרדס בקומיט `21999b0` (ענף `ccr-477334af-dunrg9`), זית הבסיס v0.6.12 (`d7777e2e`, לפי שורת הבסיס של `docs/ZAYIT_CHANGE_MAP.md`), SeforimLibrary בנעיצה של זית (`4f07145`), Nucleus בקומיט `65ccd12` לצד קובץ ה-jar של הפורק ב-`nucleus-fork/maven/` (`3.0.0-dev-202610272317-2-tearoff`), ו-intellij-community בקומיט `b2ba8bcb` (נקרא רק ב-`git show HEAD:<path>`). כל מה שלא נבדק מסומן **(לא נבדק)**, ותקציר של תוצאת חיפוש ברשת מסומן **(לא מאומת: תקציר חיפוש)**.

**קיצורי נתיבים:** `app/` = `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/`; `tests/` = `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/`; `tabs/` = `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforim/tabs/`; `android/` = `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/`; `repo/` = `SeforimLibrary/dao/src/commonMain/kotlin/io/github/kdroidfilter/seforimlibrary/dao/repository/`; `ij:` = נתיב ב-intellij-community. ברשומות של מפת השינויים הנתיבים מלאים, כמו במפה.

**של מי כל קובץ** (לפי המפה ו-`git diff` מול הבסיס): "של פרדס" (קובץ שפרדס הוסיף), "של זית ששונה (+x −y)" (יש לו רשומה בסעיף 1 של המפה), "של זית כפי שיצא" (אין לו רשומה; שינוי בו פותח רשומה חדשה). **טביעת רגל:** (א) תצורה; (ב) קובץ חדש של פרדס עם וו של שורה אחת, או עריכה בקובץ של פרדס; (ג) עריכה בתוך קוד של זית; (ד) Nucleus. אף אחד מארבעת הפריטים אינו צריך (ד).

### מה הקוד תיקן בדוח ובהערות המחקר

1. **`HistoryStore` לא רושם כל קפיצה.** הוא רושם ביקור אחד לכל ספר שנפתח בלשונית, שתי שניות אחרי הפתיחה, בסעיף של השורה הנבחרת, ושומר את שורת הכותרת של הסעיף ולא את השורה שבה קראו: `app/features/bookcontent/BookContentViewModel.kt:328-364` (`distinctUntilChanged` על מזהה הספר בשורה 338, `lineId = toc?.lineId` בשורה 361, `HISTORY_RECORD_DEBOUNCE_MS = 2_000L` בשורה 1309). מפתח השורה בטבלה הוא `book:<id>:<tocId>` (`app/core/history/HistoryStore.kt:62`), כך שקפיצה בתוך אותו ספר לא נכנסת להיסטוריה כלל.
2. **פרדס לאנדרואיד לא מחזיק מחסנית מקומות.** `TabHost` רק מוסר את Back ללשונית המוצגת; ה"אחורה" עצמו הוא דגם הפותח של Chrome לאנדרואיד: לשונית זוכרת את `openerId`, ו-Back סוגר אותה וחוזר לפותחת (`android/ui/AppState.kt:94`, `:105`, `:310-347`; `android/ui/shell/ZayitApp.kt:302-309`). זה הדגם לפריט 12 רק בעניין הקישורים.
3. **ב-IntelliJ, "מקומות אחרונים" הם מחסנית ה-Back עצמה**, הפוכה ומוגבלת ל-25 (`ij:platform/platform-impl/src/com/intellij/ide/actions/RecentLocationsDataModel.kt:91-93`), ולכן פריט 13 נשען על פריט 12 יותר ממה שהדוח הניח.
4. **גבול חמש השורות של Sticky Lines מאומת:** `SHOW_STICKY_LINES = true`, `STICKY_LINES_LIMIT = 5` (`ij:platform/ide-core-impl/src/com/intellij/openapi/editor/ex/EditorSettingsExternalizable.java:1154-1155`).
5. **כפתורי אחורה/קדימה של העכבר כבר מגיעים ל-Compose**, בלי שינוי ב-Nucleus: הצד הטבעי ממפה XBUTTON1/2 ב-Windows, לחצנים 8/9 של GDK בלינוקס ו-`buttonNumber` 3/4 במק (`Nucleus/decorated-window-tao/src/main/native/src/events.rs:262-283`, `:836-845`), ו-`AbstractTaoComposeSceneHost.mapButton` הופך אותם ל-`PointerButton.Back`/`Forward`; הצד של Kotlin נבדק ב-`javap` בתוך ה-jar שפרדס טוען מ-`nucleus-fork/maven/`. הספריות הטבעיות שב-jar הן של המהדורה (`nucleus-fork/README.md`), והקומיט של המיפוי (`c784a88`, 2026-09-25) קודם למהדורה `3.0.0-dev-202610272317`; בהרצה זה לא נבדק.
6. **`Alt+←/→` פנויים לגמרי:** בכל האפליקציה יש רק `Ctrl/Cmd+Alt+←/→` להחלפת שולחן עבודה (`app/core/presentation/window/MainAppWindow.kt:223-228`, `:449-458`), חיצים בלי מקש מגדיר בתצוגת הספר ובחלונות קטנים, ו-`processKeyShortcuts` צורך רק את `Alt+W/M/C` הישנים (`app/core/presentation/utils/WindowUtils.kt:88-106`). גם `E` לא בשימוש בשום קיצור.

### תשתית שהפריטים חולקים

| רכיב חדש של פרדס | משמש את | הוו בקוד של זית |
|---|---|---|
| `ReadingPlace`, `TabPlaceHistory`, `TabPlaceHistories` (`app/features/bookcontent/places/`) | 12, 13 | בלי וו משלו |
| `WindowReadingKeys` (`app/core/presentation/window/`) | 13, 14 | ענף אחד במטפל המקשים של החלון, לפני `MainAppWindow.kt:246` |
| `ReadingModeHost` (`app/core/presentation/window/`) | 13, 14 | עוטף את `WindowBody(openWindow)` ב-`MainAppWindow.kt:492` |

לשני הקבצים המשותפים ל-13 ול-14 רשומה אחת בסעיף 2 של המפה, בגוש של הפריט שנבנה ראשון, ובשדה Feature שלה שני הפריטים; הפריט השני רק מעדכן אותה.

המטפל של החלון (`onKeyEvent` של `JewelDecoratedWindow`, `MainAppWindow.kt:168-262`) מקבל מקש רק אם הסצנה לא צרכה אותו: "Fires only when the scene did not consume the event" (`Nucleus/decorated-window-tao/src/main/kotlin/dev/nucleusframework/window/tao/scene/TaoComposeSceneHost.kt:169-174`). לכן Esc מגיע אליו רק אחרי ששדה, חלון קופץ או סרגל החיפוש בעמוד לא לקחו אותו, וזה הסדר הנכון.

---

#### 12. אחורה/קדימה בלשונית

**בקצרה:** לכל לשונית מחסנית של עד 150 מקומות, שנרשם בה מקום רק כשהקריאה באמת קופצת ושני מקומות קרובים בה מתמזגים; `Alt+←`/`Alt+→` (במק `⌘[`/`⌘]`) וכפתורי הצד של העכבר מחזירים ומקדמים בה, ומלשונית שנפתחה מקישור "אחורה" הראשון חוזר ללשונית שפתחה אותה.

**מה רואים:**
- **המקשים.** Windows ולינוקס: `Alt+←` אחורה, `Alt+→` קדימה. מק: `⌘[` אחורה, `⌘]` קדימה, כמו במפת המקשים של IntelliJ למק (`ij:platform/platform-resources/src/keymaps/Mac OS X 10.5+.xml:558-567`); `⌘⌥←/→`, החלופה של IntelliJ שם, תפוסים בפרדס לשולחנות העבודה, ו-`⌥←/→` מזיזים מילה בשדה טקסט במק. `Alt` עם חץ ועם `Shift` או `Ctrl` לא עושה דבר חדש. בחירת הכיוון בממשק מימין לשמאל: ראו החלטה 1.
- **העכבר.** כפתור אחורה (X1) וקדימה (X2) עובדים מעל כל גוף החלון: הטקסט, החלוניות המעוגנות, הסרגלים שבצדדים ופס הניווט שבתחתית. לא מעל רצועת הלשוניות, ולא בחלונית שהוצאה לחלון משלה.
- **מה "אחורה" עושה.** חוזרים לשורה שעמדה בראש הטקסט לפני הקפיצה: היא נבחרת ומוצבת בראש התצוגה, בדיוק כמו לחיצה בתוכן העניינים, והחלוניות (מפרשים, קישורים) עוברות אליה. אם המקום היה בספר אחר, הלשונית פותחת שוב את הספר ההוא במקום ההוא. "קדימה" מחזיר למקום שממנו חזרו.
- **מה נרשם (קפיצה):**

| הפעולה | האירוע | מאיפה הוא נשלח |
|---|---|---|
| לחיצה על סעיף בתוכן העניינים | `LoadAndSelectLine` | `app/features/bookcontent/ui/panels/booktoc/BookTocView.kt:199-204` |
| בחירה בחיפוש של תוכן העניינים | `TocSearchEntrySelected` | `app/features/bookcontent/ui/panels/booktoc/BookTocPanel.kt:97`, `:238` |
| סעיף בתוכן העניינים החלופי | `AltTocEntrySelected` | `BookTocPanel.kt:333` |
| סעיף או ספר בחלון של פס הניווט | `LoadAndSelectLine` / `BookSelected` | `app/features/bookcontent/ui/panels/bookcontent/views/BreadcrumbView.kt:79-87` |
| ספר בעץ הספרים או בחיפוש העץ, באותה לשונית | `BookTreeSearchBookSelected` / `BookSelected` | `app/features/bookcontent/ui/panels/categorytree/CategoryTreePanel.kt:68`, `:87`, `:102` |
| יעד חדש ללשונית שכבר מציגה ספר, למשל מקום שנפתח מחלון המקומות האחרונים (פריט 13); שורת החיפוש, שממנה מקלידים ציטוט, קיימת רק בדף הבית ובדף התוצאות | `OpenBookAtLine` / `OpenBookById` דרך `openDestination` | `app/core/presentation/tabs/TabsContent.kt:547-550` → `BookContentViewModel.kt:268-279` |

- **מה לא נרשם:** הקפיצות מדף הבית (שורת החיפוש, הקטלוג והקפיצה המהירה לסעיף, `app/core/presentation/components/TocJumpDropdown.kt:96-100`, שמוצגים רק בלשונית בלי ספר, `app/features/bookcontent/ui/panels/bookcontent/BookContentPanel.kt:97-108`), כי אין ממנו מקום לחזור אליו; גלילה בגלגלת ובגרירת פס הגלילה (`ContentScrollToLineIndex`, `BookContentView.kt:806`, `:1378`), צעד של שורה בחיצים למעלה ולמטה (`NavigateToPreviousLine`/`NextLine`), `PageUp`/`PageDown`, `Ctrl+Home`/`End`, לחיצה על שורה, המופע הבא והקודם בחיפוש בעמוד, והצגה או הסתרה של חלוניות.
- **מיזוג:** שני מקומות באותו ספר שרחוקים זה מזה פחות מארבע שורות הם מקום אחד. קפיצה חדשה אחרי שחזרו אחורה מוחקת את מה שהיה "קדימה".
- **קצוות:** בלשונית שרק נפתחה אין לאן לחזור, ו-`Alt+←` לא עושה דבר, אלא אם הלשונית נפתחה מקישור (ראו "איך זה עובד", סעיף 5). המקום ה-151 דוחק את הוותיק ביותר. מקום שהספר שלו כבר איננו (עדכון ספרייה, ספר PDF שהוסר) מדולג בשקט. ספרי PDF של המשתמשים לא נרשמים. כל לשונית ומחסנית משלה, והחלפת לשונית לא נרשמת. גרירת לשונית לחלון אחר של אותו שולחן עבודה שומרת את המחסנית. סגירת לשונית לא מוחקת אותה מיד: היא נשארת בזיכרון עד שלשוניות חדשות דוחקות אותה, ופתיחה מחדש ב-`Ctrl+Shift+T`, שמשתמשת באותו מזהה לשונית (`app/framework/desktop/DesktopManager.kt:198-210`), מחזירה אותה **(לא נבדק)**. בהפעלה מחדש של התוכנה המחסניות לא נשמרות, כמו ב-IntelliJ. ציטוט מוקלד או תוצאת חיפוש שנפתחים מדף הבית או מדף התוצאות באותה לשונית: בשלב הראשון אין לאן לחזור, כי דף הבית ודף התוצאות אינם "מקום" (החלטה 4).
- **מחרוזות:** בשלב הראשון אין שום טקסט חדש בממשק. אם יתווסף כפתור, פריט תפריט או דף קיצורים: `place_history_back` = "חזרה למקום הקודם", `place_history_forward` = "קדימה למקום הבא".

**איך זה עובד:**
1. **מקום.** `ReadingPlace(bookId, lineId, lineIndex)`. המקום הנוכחי הוא השורה שבראש הטקסט, `ContentState.anchorId` (`app/features/bookcontent/state/BookContentState.kt:223-225`), שמתעדכנת מכל שמירה של הגלילה (`ContentScrolled` → `ContentUseCase.updateContentScrollPosition`, `app/features/bookcontent/usecases/ContentUseCase.kt:300-338`; בצורת הדף, ה-`onScroll` של `DafView`), או השורה הנבחרת כשהיא בתוך שלוש השורות שמתחת לה או כשאין עוגן. את `lineIndex` נותן `repository.getLine` (`repo/SeforimRepository.kt:1074-1076`). `PreviousPositions` אינו מקום בטקסט: הוא יחסי הפיצול של החלוניות להצגה ולהסתרה (`BookContentState.kt:302-310`, `ContentUseCase.kt:372-388`).
2. **זיהוי קפיצה.** כל האירועים של מסך הקריאה עוברים בפתח אחד, `BookContentViewModel.onEvent` (`BookContentViewModel.kt:442-766`), וכל אירוע בקורוטינה משלו. לפני ה-`when`, `PlaceJumps.leaving(event, state)` מחזיר את המקום הנוכחי רק לאירועי הקפיצה שבטבלה ורק לספר של הספרייה (`pdfIdOf`, `app/features/pdf/MyPdfBooks.kt:42`). אחרי ה-`when`, `landed(leaving, state)` רושם אותו רק אם הקריאה זזה באמת: ספר אחר, או מרחק של ארבע שורות ומעלה בין המקום שעזבו לשורה הנבחרת אחרי הקפיצה. החלפת ספר חוזרת מה-`when` לפני שהספר נטען (`loadBookData` משגר קורוטינה, `:961-1064`), אבל `selectBook` כבר רץ קודם (`:575`, `:921`), ולכן הבדיקה לפי מזהה הספר אמינה. זה הכלל של IntelliJ: מקום ההתחלה של פקודת ניווט נכנס למחסנית רק אם הסמן עבר לשורה אחרת (`ij:platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/IdeDocumentHistoryImpl.kt:156-166`, `:249-299`, `:375-390`).
3. **רישום.** `TabPlaceHistory.record(place)`: אם המקום האחרון במחסנית קרוב (אותו ספר, פחות מארבע שורות) הוא מוחלף, אחרת נוסף; מעל 150 הוותיק יוצא; "קדימה" מתרוקן. מ-IntelliJ: `putLastOrMerge` (`IdeDocumentHistoryImpl.kt:659-678`), `commitBackPlace` (`:392-406`), `MIN_CHANGE_DISTANCE = 4` ו-`canBeMergedWith` (`ij:platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/text/TextEditorState.java:17`, `:95-103`), `editor.navigation.history.stack.size=150` (`ij:platform/util/resources/misc/registry.properties:502`; `IdeDocumentHistoryImpl.kt:79`).
4. **אחורה וקדימה.** שני אירועים חדשים, `NavigateBack` ו-`NavigateForward`. אחורה: המקום האחרון יוצא מהמחסנית, המקום הנוכחי עובר ל"קדימה", ונשלח האירוע הקיים `OpenBookAtLine(bookId, lineId)` (`:562-582`): באותו ספר `loadAndSelectLine` (`:1194-1210`), שבוחר ומציב בראש התצוגה דרך `topAnchorLineId` (`ContentUseCase.kt:197-212`, `BookContentView.kt:400-438`); בספר אחר `selectBook` ו-`loadBookData(forceAnchorId)`. כדי שהקפיצה החוזרת לא תירשם בעצמה, `PlaceJumps` מחזיק תור של "הקפיצות הצפויות", ו-`leaving` מדלג על `OpenBookAtLine` שתואם לראש התור; זה `backInProgress`/`forwardInProgress` של IntelliJ (`IdeDocumentHistoryImpl.kt:421-478`), מותאם לאירועים שרצים כל אחד בקורוטינה משלו. קדימה מדלג על מקומות זהים למקום הנוכחי (`getTargetForwardInfo`, `:458-478`) ומחזיר את המקום הנוכחי לאחורה בלי לרוקן את "קדימה".
5. **לשונית שנפתחה מקישור.** קישור בטקסט פותח לשונית חדשה (`app/core/presentation/text/RefLinkUriHandler.kt:20-38`, מסופק ב-`MainAppWindow.kt:304-305`; הקישורים של פרדס בטקסט, `app/features/bookcontent/quotelinks/VerseQuotes.kt:261` ו-`NoteLinks.kt:58`, עוברים באותו מטפל), וכך גם מפרש, תרגום או מראה מקום (`OpenCommentaryTarget` → `BookContentViewModel.kt:1270-1306`; `OpenBookByIdInNewTab` → `:592-609`), `Ctrl`+לחיצה בעץ (`BookSelectedInNewTab` → `:1213-1267`), תוצאת חיפוש בלשונית חדשה (`app/features/search/SearchResultViewModel.kt:2313-2319`) ושורת היסטוריה בחיפוש הלשוניות (`app/core/presentation/components/TabSearchPopup.kt:216-224`). כולם עוברים ב-`TabsViewModel.openTab` (`tabs/TabsViewModel.kt:191-193`), ושם וו של שורה רושם `TabPlaceHistories.opened(destination.tabId, currentId())`. `NavigateBack` במחסנית ריקה בוחר את הלשונית הפותחת אם היא עוד פתוחה: באותו חלון דרך `desktopManager.tabsViewModelFor(tabId)` (`DesktopManager.kt:161-169`) ו-`TabsEvents.OnSelect`, ובחלון אחר של אותו שולחן עבודה בוחר אותה שם ומביא את החלון קדימה. לשונית הקישור נשארת פתוחה. זה דגם `openerId` של פרדס לאנדרואיד (`android/ui/AppState.kt:310-320`), בלי הסגירה.
6. **המקשים.** `handleBookShortcut` (`app/features/bookcontent/BookContentScreen.kt:975-997`) כבר מגיע לשני המסלולים: מה-`onPreviewKeyEvent` של גוף החלון כשמשהו בו בפוקוס (`app/core/presentation/window/WindowBody.kt:95`), ומהמטפל של החלון כשאין פוקוס (`WindowBody.kt:74` → `MainAppWindow.kt:246`). שורה אחת לפני הבדיקה של `Ctrl/Cmd` (`BookContentScreen.kt:981`) שואלת את `placeHistoryKey(keyEvent)`. חלון הבחירה של פס הניווט צורך חיצים ימינה ושמאלה גם עם `Alt` כל עוד הוא פתוח ("Arrows follow the visual direction", `BreadcrumbPopup.kt:144-149`), וזה בסדר: הוא נסגר ראשון.
7. **העכבר.** `Modifier.placeHistoryButtons(readerEvents)` בשרשרת של ה-`Row` של גוף החלון (`WindowBody.kt:90-96`; `readerEvents` נבנה ב-`:69-72`): `pointerInput` שממתין ב-`PointerEventPass.Initial`, ובלחיצה (`PointerEventType.Press`) עם `event.button == PointerButton.Back`/`Forward` שולח את האירוע וצורך את השינוי. `PointerEvent.button` דורש `@OptIn(ExperimentalComposeUiApi::class)`, כמו ב-`app/core/presentation/tabs/TabsView.kt:1669`. לחיצה על שורה דורשת את הכפתור הראשי (`BookContentView.kt:1658-1664`), כך שכפתור צד לא בוחר שורה. התקדים לוו כזה בשרשרת של החלון הוא `.noteInput()` של פרדס (`app/core/reading/ReadingTimeStore.kt:32-41`, מחובר ב-`MainAppWindow.kt:340`).
8. **איפה המחסניות חיות.** `TabPlaceHistories` הוא `object` של פרדס לפי מזהה לשונית, עם LRU של כ-64 לשוניות, כמו האובייקטים `TypeToSearch` ו-`OpenSiddurSwitch` של פרדס (`app/features/bookcontent/ui/components/TypeToSearch.kt`, `app/features/siddur/OpenSiddurSwitch.kt:17`). לא פרמטר בבנאי של `BookContentViewModel`: הבדיקה של זית `PostSelectLineTest` בונה אותו ביד (`tests/features/bookcontent/PostSelectLineTest.kt:146-158`) והייתה נשברת, כמו שכתוב ב-`.claude/skills/zayit-fidelity/references/lessons.md`. לא נשמר לדיסק: גם ב-IntelliJ המצב שנשמר הוא רק `RecentlyChangedFilesState`.
9. **מ-IntelliJ:** האלגוריתם ושני הכללים, כתובים מחדש בקוטלין. לא מועתקת שום שורה, ולכן אין צורך בהודעות Apache; ה-KDoc יאמר "as IntelliJ's IdeDocumentHistoryImpl", כמו שזית כותב "modeled on IntelliJ's NavBarVmImpl" (`BreadcrumbNavigator.kt:58-61`). תקדים לאותה התנגשות מקשים: במפה של IntelliJ ל-GNOME, `Back`/`Forward` עברו ל-`Shift+Alt+←/→`, כי `Ctrl+Alt+חצים` שייכים שם לשולחן העבודה (`ij:platform/platform-resources/src/keymaps/Default for GNOME.xml:2-5`, `:16-19`; במפה הרגילה `ctrl alt LEFT` ו-`button4`, `$default.xml:296-299`, `:901-904`).

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `app/features/bookcontent/places/ReadingPlace.kt` | חדש | פרדס | `ReadingPlace`, `isNear` ו-`MIN_PLACE_DISTANCE = 4`; כ-30 שורות | (ב) |
| `app/features/bookcontent/places/TabPlaceHistory.kt` | חדש | פרדס | `TabPlaceHistory` (שני `ArrayDeque`, `record`, `back`, `forward`, `remove`, `places`, גבול 150) ו-`object TabPlaceHistories` (לפי לשונית, LRU, `opened`/`openerOf`, `all()` לפריט 13); כ-150 שורות | (ב) |
| `app/features/bookcontent/places/PlaceJumps.kt` | חדש | פרדס | אילו אירועים הם קפיצה, המקום הנוכחי מתוך `BookContentState`, `leaving`/`landed`/`step`, תור הקפיצות הצפויות, בחירת הלשונית הפותחת; כ-110 שורות | (ב) |
| `app/features/bookcontent/places/PlaceHistoryInput.kt` | חדש | פרדס | `placeHistoryKey(KeyEvent)` ו-`Modifier.placeHistoryButtons(onEvent)`; כ-60 שורות | (ב) |
| `app/features/bookcontent/BookContentEvent.kt` | קיים | זית ששונה (+44 −0) | `NavigateBack` ו-`NavigateForward` (`data object` עם KDoc) אחרי `NavigateToNextLine` (`:196`); כ-+6 | (ג) |
| `app/features/bookcontent/BookContentViewModel.kt` | קיים | זית ששונה (+69 −10) | שדה `placeJumps` אחרי `categoryDisplaySettingsUseCase` (`:113`); ב-`onEvent` שורת `leaving` אחרי `:443` ושורת `landed` אחרי סוף ה-`when` (`:764`); ענף `NavigateBack, NavigateForward ->` שקורא ל-`placeJumps.step(...)` עם `desktopManager` ו-`::onEvent`; כ-+9 | (ג) |
| `app/features/bookcontent/BookContentScreen.kt` | קיים | זית ששונה (+137 −20) | ב-`handleBookShortcut`, לפני `:981`: `placeHistoryKey(keyEvent)?.let { onEvent(it); return true }` עם הערה; +2 | (ג) |
| `app/core/presentation/window/WindowBody.kt` | קיים | זית ששונה (+3 −0) | `.placeHistoryButtons(readerEvents)` בשרשרת שב-`:90-96`, עם הערה; +2 | (ג) |
| `tabs/TabsViewModel.kt` | קיים | זית ששונה (+2 −0) | ב-`openTab` (`:191-193`): `TabPlaceHistories.opened(destination.tabId, currentId())` עם הערה; +2 | (ג) |
| `tests/features/bookcontent/places/TabPlaceHistoryTest.kt` | חדש | פרדס | הכללים של המחסנית | (ב) |
| `tests/features/bookcontent/places/PlaceJumpsTest.kt` | חדש | פרדס | מה נחשב קפיצה, מתי נרשם | (ב) |
| `tests/features/bookcontent/places/PlaceHistoryInputTest.kt` | חדש | פרדס | המקשים לפי מערכת | (ב) |
| שלב ב (החלטה 4): `tabs/TabsViewModel.kt` | קיים | זית ששונה | ב-`replaceCurrentTabDestination` (`:196-199`): רישום דף התוצאות שעוזבים; +2 | (ג) |

**רשומות במפה** (`docs/ZAYIT_CHANGE_MAP.md`, דרך `scripts/mapedit.py`, ואחר כך `check_change_map.py`):
- סעיף 1, לכתוב מחדש לפי הקובץ: `BookContentEvent.kt` (שורה 843 במפה: Change מעודכן, Feature מוסיף "Back and forward in a tab", ב-What שורה על `NavigateBack`/`NavigateForward`, Tests `TabPlaceHistoryTest`, `PlaceJumpsTest`); `BookContentViewModel.kt` (912: השדה, שתי השורות סביב ה-`when` והענף; Merge risk "small: two lines around the `when` and one branch"; Smaller "already minimal"); `BookContentScreen.kt` (866: שורה ב-`handleBookShortcut`); `WindowBody.kt` (469); `TabsViewModel.kt` (805). Kind: "Pardes feature hook" לשלושת האחרונים, "Pardes feature in Zayit's file" לשני הראשונים.
- סעיף 2: גוש חדש `### Back and forward in a tab` עם פסקה (מה, ממתי, רשימת הווים, כמו הגוש "Typing over a side pane" בשורות 5970-5999 של המפה), ורשומה לכל אחד משבעת הקבצים החדשים (Feature, Size, What, Reached from, Follows: "IntelliJ's IdeDocumentHistoryImpl and TextEditorState, rewritten; androidApp's openerId", Tests).

**החלטות פתוחות:**
1. **איזה חץ הוא "אחורה" מימין לשמאל.** מומלץ: `Alt+←` אחורה ו-`Alt+→` קדימה, בלי היפוך. כך עושים IntelliJ והדפדפנים גם בעברית: [באג Firefox 1289285](https://bugzilla.mozilla.org/show_bug.cgi?id=1289285), שמבקש להפוך את המקשים בשפות מימין לשמאל, פתוח כתשע שנים, ולפי אותו דיווח Chrome ו-Edge מתנהגים כמוהו **(לא מאומת: תקציר חיפוש)**; סייר הקבצים של Windows בעברית **(לא נבדק)**. את המקשים לומדים ביד, ומי שעובדים בדפדפן ובפרדס זה לצד זה ימצאו אותם באותו מקום. החיצים שבזית הולכים אחרי הכיוון הנראה כשהם מזיזים בין דברים שעל המסך ("Arrows follow the visual direction", `BreadcrumbPopup.kt:144`; זית 0.6.13, שלא מוזגה, העבירה את `Ctrl+Tab` לסדר הרצועה מימין לשמאל, `f1f41116`, ומוזכרת כאן רק כתקדים), ואילו אחורה וקדימה זזים בזמן, ובפרדס אין כפתור עם חץ שהמקש יסתור. החלופה: להפוך (`Alt+→` אחורה), כמו שהממשק כבר מצייר "אחורה" כחץ ימינה ("→ חזרה לכל התוצאות", `strings.xml:907`), וכמו שמבקשים באג Firefox ו-[GNOME Nautilus issue 1430](https://gitlab.gnome.org/GNOME/nautilus/-/issues/1430) **(לא מאומת: תקציר חיפוש; הדף עצמו לא נפתח מהסביבה הזו)**. אם יתווסף פעם כפתור עם חץ, כדאי לחזור להחלטה. במק, בכל מקרה, `⌘[` אחורה.
2. **מה "אחורה" משחזר.** מומלץ: את השורה שעמדה בראש הטקסט, נבחרת ומוצבת בראש, כמו לחיצה בתוכן העניינים: זה מנגנון קיים, והוו הקטן ביותר. החלופה: הגלילה המדויקת והבחירה הקודמת, בפונקציה חדשה של כ-15 שורות בתוך `BookContentViewModel`.
3. **קישורים.** מומלץ: הקישור ממשיך לפתוח לשונית חדשה, ו"אחורה" ראשון בה בוחר את הלשונית שפתחה אותה. החלופה, לחיצה רגילה שממשיכה באותה לשונית ו-`Ctrl`+לחיצה ללשונית חדשה, משנה את ההתנהגות של זית ב-`RefLinkUriHandler.kt` (של זית כפי שיצא), ול-`UriHandler.openUri` אין בכלל מידע על מקשים מגדירים; לא מומלץ.
4. **דף התוצאות ודף הבית.** מומלץ: בשלב שני, דף התוצאות כמקום: וו ב-`TabsViewModel.replaceCurrentTabDestination` רושם את דף החיפוש שעוזבים, ו"אחורה" מחזיר אליו את הלשונית. ה-ViewModels של הלשונית נשארים כשהיעד מתחלף (`SimpleTabViewModelOwner`, ו-`prepare` מחליף רק ארגומנטים: `TabsContent.kt:633-653`), ולכן התוצאות אמורות לחזור כמו שהיו **(לא נבדק)**. דף הבית לא: הוא אותו `BookContentViewModel` בלי ספר, ואין אירוע שמוריד ספר; `Alt+Home` כבר פותח לשונית בית חדשה (`MainAppWindow.kt:210-219`).
5. **כלל המיזוג.** מומלץ: הכלל של IntelliJ בלבד (אותו ספר, פחות מארבע שורות). החלופה: גם אותו סעיף בתוכן העניינים, שמצמצם רשומות בסימנים ארוכים.
6. **חיפוש בעמוד ו-`Ctrl+Home`/`End`.** מומלץ: לא נרשמים בשלב הראשון; הם גלילה של התצוגה ולא אירוע של מסך הקריאה (`BookContentView.kt:811-…`, `:877-898`). החלופה: לרשום את המקום שבו התחיל חיפוש בעמוד, בוו נוסף בקובץ הגדול ביותר של זית.
7. **שמירה לדיסק.** מומלץ: בזיכרון בלבד. החלופה: עם המצב השמור של הלשונית (`app/framework/session/SessionModels.kt`, של זית), כלומר עוד (ג).
8. **כפתור גלוי.** מומלץ: בלי כפתור בשלב הראשון, והמקשים יופיעו בדף קיצורי המקלדת (פריט 10 בדוח).

**סיכונים ולא-ידועים:**
- `anchorId` נשמר לכל היותר כל 200 מ"ש בזמן גלילה ומיד כשהיא נעצרת (`BookContentView.kt:562-590`), ונדחה בזמן טעינה (`ContentUseCase.kt:322-326`); קפיצה באמצע גלילה מהירה עלולה לרשום מקום שמפגר בכמה שורות.
- "אחורה" לספר אחר עובר במסלול של `OpenBookAtLine` ולא של `loadBook`, ולכן החלוניות הפתוחות נשארות פתוחות (כמו בציטוט מוקלד), ולא נסגרות כמו בבחירת ספר מהעץ (`BookContentViewModel.kt:935-955`).
- בצורת הדף ובסריקה, "אחורה" קובע שורה והתצוגה אמורה לעבור לעמוד שלה דרך `lineId` ו-`scrollToLineTimestamp` (`BookContentView.kt:1077-1105`) **(לא נבדק)**.
- כפתורי הצד ב-Wayland, ב-X11 ובמק **(לא נבדק בהרצה)**; ראו התיקון 5 למעלה.
- `Alt+←` בתוך שדה טקסט (חיפוש בעמוד, חיפוש בתוכן העניינים) יחזור אחורה במקום לא לעשות דבר. ב-Windows ובלינוקס אין לצירוף הזה פעולה בשדה; פרדס כבר משתמש ב-`Alt+Home`, כך שצירופי `Alt` מגיעים לתוכנה.
- מספר לחיצות מהירות על "אחורה" שולח כמה `OpenBookAtLine` ברצף; התור של הקפיצות הצפויות חייב להחזיק את כולן.
- סיכון מיזוג: `MainAppWindow.kt` ו-`BookContentViewModel.kt` משתנים ברוב המהדורות של זית (0.6.13 משנה את `MainAppWindow.kt`, סעיף 5 במפה).

**בדיקות:**
- **יחידה, `tests/features/bookcontent/places/TabPlaceHistoryTest.kt`:** `` `a place near the last one replaces it` ``, `` `a place four lines away is kept apart` ``, `` `a place in another book is never near` ``, `` `the oldest place goes past 150` ``, `` `back moves the current place to forward` ``, `` `forward skips the place the reader stands at` ``, `` `a new jump clears forward` ``, `` `a replayed jump is not recorded` ``, `` `the opener comes once the stack is empty` ``, `` `remove drops a place from both stacks` ``.
- **`PlaceJumpsTest.kt`:** `` `a toc click is a jump and a scroll is not` `` (על כל האירועים שבטבלה ומחוצה לה), `` `a jump of three lines is not recorded` ``, `` `a jump to another book is recorded` ``, `` `a pdf book is never recorded` ``, `` `the place is the top line unless the selection is just under it` ``.
- **`PlaceHistoryInputTest.kt`** ב-`runComposeUiTest`, כמו `tests/features/bookcontent/BookContentKeyboardUiTest.kt`: `Alt+Left` נותן `NavigateBack` ב-Windows ובלינוקס, `⌘[` במק, `Alt+Shift+Left` ו-`Ctrl+Alt+Left` לא נותנים כלום.
- **לא ישברו:** `PostSelectLineTest`, `BookContentViewModelTest`, `BookContentEventTest`.
- **בתוכנה** (הכלי `pardes-desktop-run`): שולחן ערוך אורח חיים, קפיצה בתוכן העניינים לסימן רנג, `Alt+←` חוזר לשורה שהייתה בראש, `Alt+→` קדימה; שתי קפיצות במרחק שתי שורות נותנות מקום אחד; גלילה בלבד לא מוסיפה מקום; קישור לפסוק בגמרא פותח לשונית, ו-`Alt+←` בה בוחר את לשונית הגמרא; כפתורי הצד של עכבר ב-Windows, בלינוקס ובמק; "אחורה" בצורת הדף.

**היקף ותלות:** כ-350 שורות קוד בקבצים של פרדס וכ-300 שורות בדיקה; בקוד של זית כ-21 שורות בחמישה קבצים, ועוד 2 בשלב השני. מאמץ M, כשלושה עד שישה ימים. אינו תלוי בשום פריט. פריט 13 קורא ממנו את המקומות של ההפעלה הנוכחית (`TabPlaceHistories.all()`), והלחיצה על כותרת נעוצה (15) ופתיחת מקום אחרון (13) הן קפיצות שהוא רושם. מומלץ לכתוב קודם את `TabPlaceHistory` ובדיקותיו בלי שום וו, ורק אחר כך את הווים.

---

#### 13. מקומות אחרונים עם טקסט

**בקצרה:** `Ctrl+Shift+E` (במק `⌘⇧E`) פותח חלון של 25 המקומות האחרונים, בכל שורה נתיב המקום ותחילת השורה, עם סינון בהקלדה, `Delete` להסרה ותיבה "רק מקומות עם הערה או הדגשה" שאותו מקש מדליק ומכבה.

**מה רואים:**
- חלון קופץ במרכז החלק העליון של החלון, ברוחב של כ-560dp. בכותרת "מקומות אחרונים (12)" ולצדה התיבה; מתחתיה שדה סינון; מתחתיו הרשימה; ובתחתית שורת רמז.
- **שורה ברשימה:** בשורה הראשונה שם הספר ונתיב המקום ("שולחן ערוך, אורח חיים › הלכות שבת › סימן רנג"), בגופן קטן ובצבע משני, והחלק האחרון מודגש, עם סימן קטן כשיש במקום הערה או הדגשה (`NotebookPen` ו-`WritingHand` כבר קיימים במודול `icons`). בשורה השנייה תחילת שורת הטקסט, עד שתי שורות תצוגה, בלי ניקוד וטעמים.
- **מקשים:** חיצים למעלה ולמטה, `Enter` פותח, `Delete` מסיר (וגם `Backspace` כשהשדה ריק), `Esc` סוגר, ואותו `Ctrl+Shift+E` מחליף את התיבה. לחיצה כפולה פותחת, כמו ב-IntelliJ.
- **פתיחה:** אם הספר פתוח בלשונית של החלון, היא נבחרת וקופצת למקום (וקפיצה כזו נרשמת בפריט 12); אחרת נפתחת לשונית חדשה, כמו שורת היסטוריה בחיפוש הלשוניות.
- **מאיפה המקומות:** המקומות שקפיצות השאירו בכל הלשוניות בהפעלה הזו (מפריט 12), והביקורים בספרים שנשמרו בהיסטוריה (`HistoryStore`, נשמר בין הפעלות), ממוזגים לפי הזמן; מקום כפול (אותו ספר ופחות מארבע שורות, או אותו סעיף) מופיע פעם אחת.
- **קצוות:** בלי מקומות: "אין עדיין מקומות אחרונים". עם התיבה ובלי תוצאה: "אין מקומות אחרונים עם הערה או הדגשה". סינון בלי תוצאה: "אין מקומות מתאימים". ספר שאיננו עוד מדולג. ספרי PDF לא מוצגים בשלב הראשון. `Delete` על שורה שבאה מההיסטוריה מוחק אותה גם מדף ההיסטוריה, ושורת הרמז אומרת זאת.
- **מחרוזות מוצעות** (גוש חדש בסוף `strings.xml` תחת `<!-- Pardes: recent places (RecentPlacesPopup) -->`): `recent_places_title` = "מקומות אחרונים (%1$d)"; `recent_places_placeholder` = "סינון לפי ספר, סימן או מילים מהשורה"; `recent_places_annotated_only` = "רק מקומות עם הערה או הדגשה"; `recent_places_empty` = "אין עדיין מקומות אחרונים"; `recent_places_empty_annotated` = "אין מקומות אחרונים עם הערה או הדגשה"; `recent_places_no_match` = "אין מקומות מתאימים"; `recent_places_hint` = "Enter לפתיחה · Delete להסרה מהרשימה (ומההיסטוריה, אם המקום בא ממנה)".

**איך זה עובד:**
1. **מה ההיסטוריה יודעת.** `HistoryStore.recordBookVisit` שומר ספר, סעיף, שורה, כותרת וזמן (`app/core/history/HistoryStore.kt:52-76`), שורה אחת לכל זוג ספר וסעיף (`VisitHistory.sq`, `upsertVisit`), בלי גבול; `query("", limit)` מחזיר לפי הזמן (`:115-127`), `delete(key)` מוחק (`:129-133`). השורה שנשמרת היא שורת הכותרת של הסעיף (ראו התיקון 1 למעלה).
2. **טעינה**, בקורוטינה ובלי לחסום את הממשק: עד 100 מועמדים מ-`historyStore.query("", 100)` (רק `VisitKind.BOOK`) ומ-`TabPlaceHistories.all()`. לכל מועמד: השורה (`repository.getLine`); לשורת היסטוריה, השורה הראשונה בסעיף שאינה הכותרת שלו (`getLineIdsForTocEntry` ו-`getHeadingTocEntryByLineId`, `repo/SeforimRepository.kt:214-223`); הנתיב, כמו של פס הניווט: `getTocEntryIdForLine` ואחריו `getAncestorPath` (`:191-193`, `:1259-1261`; כך בדיוק `ContentUseCase.kt:139-147`, `:237-238`), בלי שורש שחוזר על שם הספר, כמו `buildBreadcrumbPath` (`BreadcrumbView.kt:185-196`); הטקסט, `plainTextFromHtml` (`htmlparser/src/commonMain/kotlin/io/github/kdroidfilter/seforim/htmlparser/HtmlCompose.kt:54-65`) ואחריו `DiacriticsMode.None.apply` (`app/core/presentation/text/DiacriticsMode.kt`); שם הספר, `repository.getBookCore`.
3. **"עם הערה או הדגשה".** בלי שינוי סכֵמה: `NoteStore.loadBook` ו-`notesForLine` (`app/core/annotations/NoteStore.kt:61-83`), `HighlightStore.loadBook` ו-`highlightsForLine` (`app/core/annotations/HighlightStore.kt:65-77`), זיכרון לכל ספר, ובשאילתות יש גם `selectForLine` (`UserNotes.sq`, `UserHighlights.sq`). למקום של קפיצה: השורה ושתי שורות לפניה ואחריה, ההקשר של IntelliJ (`registry.properties:1894`; השורות מ-`getLines(bookId, i - 2, i + 2)`, `repo/SeforimRepository.kt:1083-1090`); לשורת היסטוריה, שהיא סעיף: כל שורה בסעיף. הסינון רץ על כל המועמדים, ורק אחר כך נלקחים 25 הראשונים.
4. **סינון בהקלדה**, במנרמל של פרדס `TitleText` (`app/framework/search/titles/TitleText.kt:12-41`, `internal object` באותו מודול): כל מילה שהוקלדה, אחרי קיפול ניקוד, גרשיים ואותיות סופיות (`TitleText.clean`), צריכה להופיע בתוך מילה של הנתיב או של הטקסט, בכל סדר.
5. **הממשק.** חלון חדש של פרדס שמשתמש בחלקים ה-`internal` של `TabSearchPopup` (`PopupSearchField`, `PopupSectionHeader`, `PopupFooterRow`, `PopupShape`: `TabSearchPopup.kt:254-479`) עם שורה משלו בת שתי שורות ועם בחירה במקלדת, שאין ב-`TabSearchPopup` (שם רק ריחוף ולחיצה, `:327-345`). המצב "פתוח/סגור" שייך לחלון, ו-`ReadingModeHost` מצייר אותו.
6. **המקש** מטופל ב-`WindowReadingKeys`, בענף שלפני `MainAppWindow.kt:246`.
7. **פתיחה:** `TabsViewModel.onEvent(TabsEvents.OnSelect(i))` ואחריו `replaceCurrentTabDestination(TabsDestination.BookContent(bookId, tabId, lineId))` (`tabs/TabsViewModel.kt:196-199`), שמגיע ל-`openDestination` ול-`OpenBookAtLine` (`TabsContent.kt:547-550`, `BookContentViewModel.kt:268-279`); אחרת `openTab`, כמו `VisitEntry.destination()` (`app/core/history/VisitEntryDestination.kt:7-21`).
8. **הסרה:** מקום של קפיצה, `TabPlaceHistories.remove` (גם IntelliJ מוחקת מהמחסנית: `RecentLocationsDataModel.kt:124-150`); שורת היסטוריה, `historyStore.delete(key)`.
9. **מ-IntelliJ:** הרעיון והמספרים, בלי קוד. הרשימה היא מחסנית ה-Back הפוכה, 25 (`ij:platform/platform-impl/src/com/intellij/ide/actions/RecentLocationsDataModel.kt:91-93`; `ij:platform/editor-ui-api/src/com/intellij/ide/ui/UISettingsState.kt:57`), נתיב לכל מקום (`RecentLocationsDataModel.kt:56-63`), שתי שורות הקשר (`:184`), התיבה שאותו קיצור מחליף (`ij:platform/platform-impl/src/com/intellij/ide/actions/RecentLocationsAction.java:127-128`, `:171-175`), `Enter` (`:217-218`), `DELETE` ו-`BACK_SPACE` (`:220-224`), וסינון על הטקסט שמוצג (`:146`). הקיצור `Ctrl+Shift+E` הוא של IntelliJ (`$default.xml:334-336`). ב-IntelliJ "רק מה שנערך" היא רשימה אחרת, מקומות העריכה; כאן זו תיבת סינון על אותה רשימה, כפי שהבקשה מנוסחת (החלטה 2).

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `app/features/bookcontent/places/RecentPlaces.kt` | חדש | פרדס | טעינה, מיזוג, איחוד כפולים, "עם הערה או הדגשה", התאמה לסינון; כ-150 שורות | (ב) |
| `app/core/presentation/components/RecentPlacesPopup.kt` | חדש | פרדס | החלון; כ-220 שורות | (ב) |
| `app/core/presentation/window/WindowReadingKeys.kt` | חדש (משותף עם 14) | פרדס | `Ctrl+Shift+E` / `⌘⇧E` פותח וסוגר את החלון | (ב) |
| `app/core/presentation/window/ReadingModeHost.kt` | חדש (משותף עם 14) | פרדס | מצייר את החלון מעל גוף החלון | (ב) |
| `app/core/presentation/window/MainAppWindow.kt` | קיים | זית ששונה (+21 −24) | ענף `} else if (WindowReadingKeys.handle(keyEvent, openWindow)) { true` לפני `:246` (+2), ו-`ReadingModeHost(openWindow) { WindowBody(openWindow) }` במקום `:492` (+1 −1); משותף עם 14 | (ב), וו (ג) קטן |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | קיים | זית ששונה (+359 −1) | גוש של שבע מחרוזות בסוף הקובץ | (ב) |
| `tests/features/bookcontent/places/RecentPlacesTest.kt` | חדש | פרדס | המיזוג, הסינונים, ההסרה | (ב) |
| `tests/core/presentation/components/RecentPlacesPopupUiTest.kt` | חדש, רשות | פרדס | הקלדה, `Delete`, המקש כמחליף של התיבה | (ב) |

**רשומות במפה:** סעיף 1: `MainAppWindow.kt` (שורה 445 במפה: Change מעודכן; What מוסיף "the window's reading keys, before the reader's shortcuts" ו-"the body inside `ReadingModeHost`"; Feature מוסיף "Recent places" ו-"Lesson mode"), `strings.xml` (1733: הגוש החדש בשורת הרשימה). סעיף 2: גוש `### Recent places` עם פסקה ורשימת ווים, ורשומות ל-`RecentPlaces.kt`, ל-`RecentPlacesPopup.kt` ולבדיקות; לשני הקבצים המשותפים עם 14 רשומה אחת (ראו "תשתית שהפריטים חולקים").

**החלטות פתוחות:**
1. **מקורות.** מומלץ: ההיסטוריה ועוד המקומות של פריט 12. בלי פריט 12 הרשימה גסה: מקום אחד לכל ספר שנפתח. לא מומלץ לרשום כל קפיצה ב-`HistoryStore`: זה משנה החלטה של זית ("Chrome records the navigation, not every position read afterwards", `BookContentViewModel.kt:328-332`) וממלא את דף ההיסטוריה.
2. **משמעות התיבה.** מומלץ: סינון על אותה רשימה (השורה ושתיים מכל צד; לשורת היסטוריה, הסעיף כולו). החלופה, כמו ב-IntelliJ: רשימה נפרדת של המקומות שבהם נכתבו לאחרונה הערות והדגשות. להערות יש `NoteStore.recent` (`NoteStore.kt:72-77`); להדגשות אין שאילתת "אחרונות", והיא תדרוש שאילתה בקובץ `.sq` של פרדס על טבלה של קובץ `.sq` של זית **(לא נבדק שהדבר מותר ב-SQLDelight)**.
3. **פתיחה.** מומלץ: מעבר ללשונית שכבר מציגה את הספר, אחרת לשונית חדשה. חלופות: תמיד הלשונית הנוכחית (מתאים אחרי פריט 12), או תמיד לשונית חדשה.
4. **`Delete` על שורת היסטוריה.** מומלץ: מוחק גם מההיסטוריה, עם הרמז. החלופה: מסתיר רק להפעלה הנוכחית.
5. **מקש וגילוי.** מומלץ: `Ctrl+Shift+E` / `⌘⇧E` בלבד. כפתור בשורת הכותרת (`TitleBarActionsButtonsView`, של זית) או שורה בתחתית חיפוש הלשוניות (`TabSearchPopup.kt`, של זית כפי שיצא) הם (ג).
6. **הקשר.** מומלץ: רק תחילת השורה של המקום. החלופה: גם השורה שלפניה ושאחריה, כמו IntelliJ, במחיר גובה כפול לכל פריט.
7. **מספרים.** 25 מקומות (IntelliJ), ומאגר של 100 מועמדים לסינון.

**סיכונים ולא-ידועים:**
- כל פתיחה של החלון מריצה כמה שאילתות לכל מועמד (שורה, סעיף, נתיב, ספר), כלומר מאות שאילתות קטנות על SQLite מקומי **(לא נמדד)**; לטעון בהדרגה ולהציג מה שמוכן.
- שורת היסטוריה מצביעה על כותרת הסעיף, ולכן צריך שאילתה נוספת כדי להגיע לשורת טקסט.
- ספר PDF נרשם בהיסטוריה עם שם בלבד (נבחר דרך `PdfBookStore.asBook`), בלי שורות.
- בלי פריט 12 הרשימה כמעט חופפת לחלק ההיסטוריה של חיפוש הלשוניות (12 שורות שם, `TabSearchPopup.kt:88`).
- החלון נשען על `Popup(focusable = true)` של Tao, כמו `TabSearchPopup` (`:113-121`).

**בדיקות:**
- **יחידה, `tests/features/bookcontent/places/RecentPlacesTest.kt`:** `` `places are merged by time` ``, `` `a place near another of the same book shows once` ``, `` `twenty five places at most` ``, `` `a note two lines away counts and three lines away does not` ``, `` `a history section counts a highlight anywhere in it` ``, `` `typed words match in any order without nikud` ``, `` `the book title root is dropped from the path` ``, `` `a missing book is skipped` ``. ההיסטוריה על `JdbcSqliteDriver.IN_MEMORY`, כמו `tests/core/history/HistoryStoreTest.kt`.
- **ממשק (רשות):** הקלדה מסננת, `Delete` מסיר, המקש מחליף את התיבה.
- **בתוכנה:** כמה קפיצות בשני ספרים, `Ctrl+Shift+E`, סינון "רנג", `Delete`; הוספת הערה ואז התיבה; פתיחת מקום שהספר שלו פתוח בלשונית אחרת.

**היקף ותלות:** כ-370 שורות קוד וכ-200 שורות בדיקה; בקוד של זית 3 שורות ב-`MainAppWindow.kt` (משותפות עם 14) והמחרוזות. מאמץ S–M, יומיים עד ארבעה. עובד לבד על ההיסטוריה, ומקבל את המקומות המדויקים מפריט 12; את `WindowReadingKeys` ואת `ReadingModeHost` מכניס הראשון מבין 13 ו-14 שייבנה.

---

#### 14. מצב שיעור להקרנה

**בקצרה:** מקש אחד מעביר את החלון למסך מלא, סוגר את כל החלוניות, מסתיר את הסרגלים ומגדיל את הטקסט במכפיל זמני שאינו נכתב להגדרות, ו-`Esc` מחזיר כל ערך למה שהיה.

**מה רואים:**
- **כניסה:** `F5` ב-Windows ובלינוקס, `⌥⌘P` במק (החלטה 1). החלון עובר למסך מלא; כל החלוניות של מסך הקריאה נסגרות: עץ הספרים, תוכן העניינים, ההערות, הקישורים, המפרשים, המקורות, מראי המקומות, פרטי הספר והמפרשים שבחלונית משלהם, גם אלה שהוצאו לחלון נפרד; הסרגלים שבשני הצדדים ופס הניווט שבתחתית נעלמים (החלטה 3); וכל הטקסט בגוף החלון גדל פי 1.5 (מ-16 ל-24 בגודל ברירת המחדל; במצב ההצגה של IntelliJ הגופן הוא 24, `UISettingsState.kt:213`). בראש המסך מופיע לשלוש שניות "מצב שיעור · ‏Esc ליציאה".
- **בזמן השיעור:** `Ctrl+=` ו-`Ctrl+-` (במק `⌘`) משנים את המכפיל ב-25% בין פי 1 לפי 3, ולרגע מוצג "גודל הטקסט בשיעור: 175%"; שום דבר לא נכתב להגדרות. מעל צורת הדף הם מגדילים את הדף כרגיל, כי המכפיל לא מגיע אליו (ראו "איך זה עובד"). אפשר לעבור בין לשוניות; המצב שייך לחלון, ולשונית אחרת מוצגת גם היא בלי חלוניות. רצועת הלשוניות נשארת (החלטה 3). המסך נשאר דולק, כמו היום בכל לשונית של ספר (`MainAppWindow.kt:311-321`).
- **יציאה:** `Esc`, כשאף רכיב אחר לא לוקח אותו (סרגל החיפוש בעמוד נסגר קודם, `BookContentView.kt:967-974`; מילה שסומנה בצורת הדף משתחררת קודם, `app/features/bookcontent/daf/DafView.kt:460-463`; חלון קופץ נסגר קודם), או שוב אותו מקש. חוזרים: מיקום החלון (חלון רגיל, מוגדל או מסך מלא, כמו שהיה), החלוניות כמו שהיו ובגדלים שלהן, גודל הטקסט השמור, והסרגלים. יציאה ממסך מלא בדרך אחרת (`F11`, הכפתור הירוק במק) או מעבר לשולחן עבודה אחר באותו חלון מסיימים גם את מצב השיעור.
- **מחרוזות מוצעות** (גוש בסוף `strings.xml` תחת `<!-- Pardes: lesson mode (LessonMode) -->`): `lesson_mode_hint` = "מצב שיעור · ‏%1$s ליציאה" (עם "Esc"); `lesson_mode_scale` = "גודל הטקסט בשיעור: %1$d%%"; ואם יתווסף כפתור או פריט תפריט, `lesson_mode` = "מצב שיעור".

**איך זה עובד:**
1. **מה יש היום.** `F11` מחליף בין מסך מלא למוגדל, רק מחוץ למק (`MainAppWindow.kt:238-245` ו-`:474-483`), דרך `windowState.placement`, ש-Nucleus הופך ל-`setFullscreen` (`Nucleus/decorated-window-tao/src/main/kotlin/dev/nucleusframework/window/tao/DecoratedWindowComposable.kt:352`, `:615-621`). החלוניות נקבעות מדגלים של כל לשונית (`NavigationState.isVisible`, `TocState.isVisible`, `NotesState.isVisible`, `ContentState.showTargum`/`showCommentaries`/`showSources`/`showReferences`, `detachedCommentators`; `BookContentState.kt:96-310`) ומהגדרה אחת לכל האפליקציה לפרטי הספר (`book_details_pane`, `app/core/settings/AppSettings.kt:601-606`), דרך `desiredPanes` (`app/features/bookcontent/ui/panes/ReaderPanes.kt:701-723`), `selectedTabDemand` (`:521-545`) ו-`PaneSync`, שפותח וסוגר את חלוניות העגינה בגדלים של הלשונית (`:365-519`); Nucleus לא מסתיר אותן במסך מלא (`hideWhileOwnerFullscreenOrMaximized = false`, `:319`). הטקסט: `textSizeFlow` (`AppSettings.kt:38-39`, `:227-243`; 16, בין 14 ל-50, בקפיצות של 2: `:916-919`), מקשי ההגדלה (`zoomIn`/`zoomOut`, `:303-320`; `WindowUtils.kt:62-77`) וההגדלה בעכבר (`rememberAppTextZoom` → `setTextSize`, `app/core/presentation/components/AppTextZoom.kt:12-25`) נכתבים כולם להגדרות. אין היום שום מכפיל זמני.
2. **`LessonMode`**, `object` של פרדס עם מצב לכל חלון: המיקום הקודם, המכפיל, ומזהה הקבוצה של החלון. כניסה שומרת את המיקום, קובעת `WindowPlacement.Fullscreen` ומדליקה את המצב; יציאה מחזירה את המיקום ומכבה. כמו `TogglePresentationModeAction`, ששומרת `full.screen.before.presentation.mode` ומחזירה אותו (`ij:platform/platform-impl/src/com/intellij/ide/actions/TogglePresentationModeAction.java:86-93`), וכמו מצב בלי הסחות, ששומר כל ערך לפני שהוא משנה ומחזיר ביציאה (`ij:platform/platform-impl/src/com/intellij/ide/actions/ToggleDistractionFreeModeAction.java:70-89`, `:93-120`).
3. **חלוניות בלי לשנות שום מצב.** הדגלים של הלשונית וההגדרה של פרטי הספר לא משתנים: החלוניות שהלשונית מבקשת עוברות ב-`selectedTabDemand` דרך `LessonMode.panesShown(groupId, …)` (שורה אחת ב-`ReaderPanes.kt:537`), שמחזיר קבוצה ריקה כל עוד החלון במצב שיעור. `PaneSync` סוגר את החלוניות ופותח אותן ביציאה בגדלים שלהן, באותו מסלול כמו מעבר ללשונית בלי חלוניות, ובלי לשלוח ללשונית אירועי מעקב, כי מה שפתוח שווה למה שמבוקש (`:475-486`). ב-IntelliJ שומרים את פריסת חלונות הכלים ומשחזרים (`TogglePresentationModeAction.java:115-146`); כאן אין מה לשמור. החלוניות מורכבות ברמת האפליקציה ולא בתוך החלון (`app/core/presentation/window/DesktopTabs.kt:66-70`, `app/main.kt:460-464`), ולכן הבדיקה היא לפי מזהה הקבוצה ולא דרך `CompositionLocal`.
4. **המכפיל.** `ReadingModeHost` מספק `LocalDensity` עם `fontScale` כפול המכפיל סביב `WindowBody` (`MainAppWindow.kt:492`): כל `sp` בגוף החלון גדל (טקסט הספר, דף הבית, תוצאות החיפוש, הכותרות הנעוצות), גדלים ב-dp ושורת הכותרת (מחוץ ל-`WindowBody`) לא משתנים. צורת הדף ממירה פיקסלים ל-`sp` דרך אותה צפיפות (`app/features/bookcontent/daf/DafPage.kt:78`, `:188-207`, `:261-262`, `:483`), ולכן העמוד שלה לא גדל וההגדלה שלו ממשיכה לעבוד; הסריקה וספרי ה-PDF הם תמונות.
5. **מקשים.** `WindowReadingKeys` במטפל של החלון: `F5`/`⌥⌘P` מחליפים; `Esc` יוצא רק כשהוא לא נצרך; `Ctrl+±` משנים את המכפיל, לפני `processKeyShortcuts` (`MainAppWindow.kt:248-257`), אלא אם הלשונית מציגה דף (`appSettings.showsDaf(tabId)`, `AppSettings.kt:281`).
6. **הסרגלים ופס הניווט** (החלטה 3): `ReaderBar` ו-`ReaderBreadcrumb` (`WindowBody.kt:135-141`, `:146-155`) חוזרים מיד במצב שיעור, שורה אחת בכל אחד, דרך `LocalOpenWindow.current`.
7. **הרמז** מצויר ב-`ReadingModeHost`, כמו `ClosePinnedTabHint` (`app/core/presentation/tabs/ClosePinnedTabHint.kt`). **שני משגיחים** שם: מיקום שיצא ממסך מלא אחרי שנכנס אליו מסיים את המצב (בלי להחזיר מיקום), ושינוי של `openWindow.groupId` מסיים אותו.

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `app/core/presentation/window/LessonMode.kt` | חדש | פרדס | המצב לכל חלון, כניסה, יציאה, מכפיל, `panesShown`, `isOn`; כ-110 שורות | (ב) |
| `app/core/presentation/window/ReadingModeHost.kt` | חדש (משותף עם 13) | פרדס | הצפיפות, הרמז, המשגיחים, החלון של 13; כ-90 שורות | (ב) |
| `app/core/presentation/window/WindowReadingKeys.kt` | חדש (משותף עם 13) | פרדס | `F5`/`⌥⌘P`, `Esc`, `Ctrl+±`; כ-60 שורות | (ב) |
| `app/core/presentation/window/MainAppWindow.kt` | קיים | זית ששונה (+21 −24) | אותן שלוש שורות של פריט 13 | (ב), וו (ג) קטן |
| `app/features/bookcontent/ui/panes/ReaderPanes.kt` | קיים | זית ששונה (+203 −34) | `:537` עטוף ב-`LessonMode.panesShown(groupId, …)` (+1 −1) | (ג) |
| `app/core/presentation/window/WindowBody.kt` | קיים | זית ששונה (+3 −0) | שורת חזרה ב-`ReaderBreadcrumb` וב-`ReaderBar` (+2); לפי החלטה 3 | (ג) |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | קיים | זית ששונה | `lesson_mode_hint`, `lesson_mode_scale` | (ב) |
| `tests/core/presentation/window/LessonModeTest.kt` | חדש | פרדס | כניסה, יציאה, חלוניות, מכפיל | (ב) |
| `tests/core/presentation/window/WindowReadingKeysTest.kt` | חדש | פרדס | המקשים לפי מערכת ולפי מצב | (ב) |

**רשומות במפה:** סעיף 1: `MainAppWindow.kt` (משותף עם 13), `ReaderPanes.kt` (שורה 1093 במפה: What מוסיף "the desired panes pass through `LessonMode.panesShown`, empty while the window is in lesson mode"), `WindowBody.kt` (469), `strings.xml`. סעיף 2: גוש `### Lesson mode` ורשומות ל-`LessonMode.kt` ולשתי הבדיקות; לשני הקבצים המשותפים עם 13 רשומה אחת (ראו "תשתית שהפריטים חולקים").

**החלטות פתוחות:**
1. **המקש.** מומלץ: `F5` ב-Windows ובלינוקס, המקש שמתחיל מצגת ב-PowerPoint וב-Impress, ו-`⌥⌘P` במק, ההפעלה של Keynote **(לא מאומת: מוסכמות מהזיכרון)**; בפרדס `F5` פנוי (רק `F3` ו-`F11` בשימוש), וגם `P`. חלופות: `Ctrl+Shift+F11`, או כפתור.
2. **המכפיל.** מומלץ: פי 1.5, בין פי 1 לפי 3 בקפיצות של 25%, ונזכר עד סגירת התוכנה (בזיכרון, אף פעם לא בהגדרות). החלופה: חוזר לפי 1.5 בכל כניסה.
3. **מה מוסתר.** מומלץ: החלוניות, הסרגלים שבצדדים ופס הניווט; רצועת הלשוניות נשארת, כדי לעבור בשיעור בין גמרא לראשונים. החלופה: גם שורת הכותרת (`MainAppWindow.kt:307`, עוד שורה), עם סיכון לאזורי הגרירה של החלון המעוטר **(לא נבדק)**.
4. **היקף המכפיל.** מומלץ: כל גוף החלון דרך `fontScale`, שורה אחת. החלופה: רק טקסט הקריאה, דרך `rememberAppTextZoom` (`AppTextZoom.kt`, של זית כפי שיצא, כ-+3 שורות), שמנפיש את השינוי ומשאיר את דף הבית ואת התוצאות כמו שהם.
5. **הגדלה בעכבר בזמן השיעור.** מומלץ: נשארת כמו היום ומשנה את הגודל השמור (בלי וו נוסף). החלופה: להפנות גם אותה למכפיל, בעריכה של `AppTextZoom.kt`.
6. **גילוי.** מומלץ: בלי כפתור בשלב הראשון. חלופות: כפתור בסרגל השמאלי (`VerticalBars.kt`, של זית ששונה) או פריט בתפריט של מק (`AppNativeMenuBar.kt`, של זית ששונה).
7. **צורת הדף בשיעור.** מומלץ: ההגדלה של הדף עצמו. החלופה: לכפול זמנית את ההגדלה שלו ולהחזיר ביציאה, מה שכותב ל-`daf_zoom` ומשאיר אותה כך אם התוכנה נופלת.

**סיכונים ולא-ידועים:**
- יציאה מהתוכנה במצב שיעור שומרת את החלון כמסך מלא (`app/framework/desktop/WindowGeometry.kt:22-37` שומר `placement.name`), והוא ייפתח כך בפעם הבאה, כמו אחרי `F11` היום. המכפיל והחלוניות לא נפגעים, כי מעולם לא נכתבו.
- `fontScale` משנה את גובה כל השורות; `LazyColumn` שומר את הפריט הראשון אבל לא את ההיסט בפיקסלים ("A LazyColumn keeps pixels, not places", `.claude/skills/zayit-fidelity/references/lessons.md`), וייתכן שיידרש `requestScrollToItem` אחרי הכניסה והיציאה **(לא נבדק)**.
- תוויות ב-`sp` רגיל בצורת הדף (`DafView.kt:665`, `:1181`) יגדלו בלי העמוד **(לא נבדק)**.
- מסך מלא של מק (אנימציה, Space חדש) ושל Wayland **(לא נבדק)**.
- בשדה ההצעות של דף הבית `Esc` נצרך רק ב-KeyUp (`HomeView.kt:1828`), ולכן Esc ב-KeyDown יוצא ממצב השיעור כשההצעות פתוחות; תקלה קטנה.

**בדיקות:**
- **יחידה, `LessonModeTest.kt`:** `` `entering saves the placement and goes full screen` ``, `` `leaving restores a floating, maximized or full screen window` ``, `` `panes are hidden only for the window's group` ``, `` `the tab's state is left untouched` ``, `` `the scale stays between one and three` ``, `` `leaving full screen ends the mode` ``, `` `another desktop in the window ends the mode` ``.
- **`WindowReadingKeysTest.kt`:** `F5` מחוץ למק ו-`⌥⌘P` במק; `Esc` רק במצב; `Ctrl+=` משנה את המכפיל במצב ועובר הלאה מחוצה לו ומעל דף; `Ctrl+Shift+E` מחליף את החלון של 13.
- **בתוכנה:** ספר עם מפרשים, קישורים ותוכן עניינים פתוחים בחלון מוגדל; `F5` וצילום מסך; `Ctrl+=`; מעבר לשונית; `Esc` וצילום שצריך להיות זהה לזה שלפני; `F11` במצב; סדר ה-Esc עם סרגל החיפוש פתוח; צורת הדף במצב. `E2eZoomScenario` הקיים (`app/core/e2e/`) הוא מודל לבדיקה אוטומטית.

**היקף ותלות:** כ-260 שורות קוד וכ-150 שורות בדיקה; בקוד של זית 3 שורות ב-`MainAppWindow.kt` (משותפות עם 13), שורה ב-`ReaderPanes.kt` ושתיים ב-`WindowBody.kt`. מאמץ S–M, יומיים עד ארבעה. אינו תלוי בשום פריט; חולק את המארח ואת המקשים עם 13; הכותרות הנעוצות (15) נשארות בשיעור וגדלות איתו.

---

#### 15. כותרות נעוצות

**בקצרה:** מעל הטקסט מוצג תמיד הנתיב בתוכן העניינים של השורה שבראש התצוגה ("סימן רנג › סעיף ב", "דף יב."), שמתחלף כשהקריאה עוברת כותרת, נבנה מאותו מקור כמו פס הניווט, ולחיצה על חלק בו קופצת לכותרת שלו.

**מה רואים:**
- **שורה בראש הכרטיס של הטקסט**, בשטח משלה ולא על גבי השורות: הנתיב של השורה שבראש התצוגה, למשל "הלכות שבת › סימן רנג › סעיף ב" או "פרק ראשון › דף יב.", והחלק האחרון מודגש. הדוגמאות תלויות במבנה תוכן העניינים של כל ספר, ובפרט בשאלה אם סעיפי השולחן ערוך הם רשומות בתוכן העניינים **(לא נבדק: אין ספרייה בסביבה הזו)**. עד חמש רמות, כמו הגבול של IntelliJ, והקודמות מקוצרות ל-"…"; שורש שחוזר על שם הספר לא מוצג, כמו בפס הניווט.
- **לחיצה** על חלק בנתיב קופצת לכותרת שלו, שנבחרת ומוצבת בראש התצוגה, כמו בלחיצה בתוכן העניינים (והקפיצה נרשמת בפריט 12). **לחיצה ימנית:** "הסתרת הכותרות הנעוצות". **בהגדרות › תצוגה** מתג "כותרות נעוצות", דולק כברירת מחדל, כמו ב-IntelliJ.
- **לא מוצג:** בצורת הדף, בסריקה, בספרי PDF, בדף הבית ובדף התוצאות. לפני הכותרת הראשונה בספר מוצג שם הספר, כדי שהשורה לא תקפוץ (החלטה 7).
- **ההבדל מפס הניווט שבתחתית:** פס הניווט עוקב אחרי השורה הנבחרת (`ContentUseCase.kt:139-147`, `:223-231`), והכותרות הנעוצות עוקבות אחרי הגלילה. כשקוראים בלי ללחוץ, השתיים יכולות להראות מקומות שונים.
- **מחרוזות מוצעות** (גוש בסוף `strings.xml` תחת `<!-- Pardes: sticky headings (StickyHeadings) -->`): `sticky_headings` = "כותרות נעוצות"; `sticky_headings_description` = "הנתיב של השורה שבראש הטקסט, כמו סימן וסעיף או דף, נשאר מוצג מעל הטקסט בזמן הגלילה. לחיצה על חלק מהנתיב מעבירה לכותרת שלו."; `sticky_headings_hide` = "הסתרת הכותרות הנעוצות"; `sticky_headings_jump` = "מעבר אל %1$s" (רמז בריחוף).

**איך זה עובד:**
1. **איך הטקסט נבנה.** `BookContentView` מצייר את השורות ב-`LazyColumn` (`BookContentView.kt:1121-1137`), פריט לכל שורה עם מזהה השורה כמפתח (`:1134`), בדפים של Paging 3 (`ContentUseCase.buildLinesPager`, `ContentUseCase.kt:31-46`; `collectAsLazyPagingItems`, `BookContentPanel.kt:233`). את ה-`LazyListState` יוצר `BookContentPanel` (`:158-166`) ומעביר כ-`preservedListState` (`:309`), כך שרכיב לצדו יכול לקרוא את הפריט העליון בלי לגעת ב-`BookContentView`.
2. **השורה העליונה:** הפריט הראשון שרובו מתחת לקצה העליון, הכלל של פרדס לאנדרואיד ב-`placeOnScreen` (`android/ui/reader/ReaderScreen.kt:459-478`), ב-`snapshotFlow` על `layoutInfo`, והשורה שלו מ-`lazyPagingItems.peek(index)`, כמו `BookContentView.kt:325-343`.
3. **הנתיב:** בדיוק כמו של פס הניווט, `getTocEntryIdForLine` ואחריו `getAncestorPath` (`ContentUseCase.kt:139-147`, `:237-238`; `repo/SeforimRepository.kt:191-193`, `:1259-1261`; ה-CTE ב-`TocQueries.sq`, `selectAncestorPath`), עם שני זיכרונות, משורה לסעיף ומסעיף לנתיב. זה מועתק מהקוד של פרדס לאנדרואיד, `ReaderViewModel.followPlace` (`android/ui/reader/ReaderViewModel.kt:265-266`, `:594-602`), שכבר מציג שם את המקום שעל המסך בכותרת המשנה (`ReaderScreen.kt:224-232`, `:994`). `mapLatest`, כך שגרירה מהירה מדלגת על השורות שבדרך, ושאילתה רק לשורה שאינה בזיכרון. `BreadcrumbNavigator` עצמו לא מחשב נתיב: הוא מציג ומדפדף בנתיב ש-`BreadcrumbView` בונה (`BreadcrumbView.kt:52-56`, `:185-196`; `BreadcrumbNavigator.kt:58-64`, `:94`).
4. **המקום על המסך** (החלטה 1): שורה שמורה בתוך הכרטיס. ב-`BookContentPanel.kt:308` ה-`modifier = paneCardModifier` מקבל `.then(stickyHeadingRoom(uiState))`, ריפוד עליון בגובה השורה בתוך הכרטיס, כך שהרשימה, סרגל החיפוש בעמוד (`BookContentView.kt:1387-1400`) ופס הגלילה (`:1371-1382`) מתחילים מתחתיה; ו-`StickyHeadingBar(...)` מצויר אחרי בלוק ה-`run` (אחרי `:343`) על הריפוד הזה. שום קוד של זית לא זז ולא מקבל הזחה חדשה.
5. **איפה לא:** כשהלשונית מציגה דף או סריקה (`DafMode`, `BookContentPanel.kt:253-282`; `BookContentView.kt:1077-1117`) או ספר PDF (`BookContentPanel.kt:245-249`), אין ריפוד ואין שורה.
6. **מ-IntelliJ:** הרעיון, בלי קוד. Sticky Lines הן פס הניווט של השורה העליונה ("relies on FileBreadcrumbsCollector", `ij:platform/platform-impl/src/com/intellij/openapi/editor/impl/stickyLines/StickyLinesCollector.kt:25-28`), עד חמש ודולקות כברירת מחדל (`EditorSettingsExternalizable.java:1154-1155`); שורה נעוצה מוצגת כל עוד תחילת התחום שלה מעל הפאנל וסופו מתחתיו, ונדחפת למעלה כשהתחום מסתיים (`ij:platform/platform-impl/src/com/intellij/openapi/editor/impl/stickyLines/VisualStickyLines.kt:114-152`), תחום של פחות מחמש שורות לא ננעץ (`:19`, `:162-164`), והפאנל לא עובר חצי מגובה העורך (`:158-160`). נלקחים המקור (נתיב הניווט) והגבול; לא נלקחים הציור מעל הטקסט (פרדס שומר לו מקום, כמו כותרת המשנה באנדרואיד וכמו כותרת רצה בספר מודפס) ואנימציית הדחיפה (בהמשך).
7. **מחיר לכל פריים של גלילה:** ה-`snapshotFlow` קורא שוב את `layoutInfo` בכל פריים של גלילה, כמו ה-`scrollData` של `BookContentView` עצמו (`:502-525`) וזרם הטעינה המוקדמת (`:325-343`), אבל פולט רק כשהשורה העליונה מתחלפת; שאילתה רק לשורה שאינה בזיכרון, מחוץ לחוט הממשק; השורה הנעוצה מורכבת מחדש רק כשהנתיב משתנה; ו-`BookContentView` לא מורכב מחדש בגללה. כל לשונית נשארת מורכבת (`TabsContent.kt:101`), ולכן הטעינה מתחילה רק כשהלשונית נבחרת (`LocalTabSelected`, `TabsContent.kt:74`). את המחיר אפשר למדוד ב-`E2eReaderPerfScenario` הקיים (`app/core/e2e/E2eReaderPerfScenario.kt:81`).

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `app/features/bookcontent/ui/panels/bookcontent/views/StickyHeadings.kt` | חדש | פרדס | `StickyHeadingBar`, `stickyHeadingRoom`, `StickyHeadingPlaces` (שני הזיכרונות), השורה העליונה מתוך `layoutInfo`; כ-170 שורות | (ב) |
| `app/features/settings/display/StickyHeadingsCard.kt` | חדש | פרדס | `SettingCard`, כמו `app/features/settings/siddur/OpenSiddurCard.kt`; כ-25 שורות | (ב) |
| `app/features/bookcontent/ui/panels/bookcontent/BookContentPanel.kt` | קיים | זית ששונה (+198 −54) | `:308` מקבל `.then(stickyHeadingRoom(uiState))` (+1 −1); אחרי `:343` הערה ו-`StickyHeadingBar(uiState, bookListState, lazyPagingItems, onEvent)` (+2) | (ג) |
| `app/core/settings/AppSettings.kt` | קיים | זית ששונה (+284 −0) | `KEY_STICKY_HEADINGS = "sticky_headings"`, זרם, `get`/`set`, ב-`PORTABLE_KEYS` (`:859-…`) וב-`clearAll()` (`:821-…`); כ-+10 | (ג) |
| `app/features/settings/ui/DisplaySettingsScreen.kt` | קיים | זית כפי שיצא | אחרי `MaxCommentatorsPerPageCard(...)` (`:114-117`): `StickyHeadingsCard()` עם הערה (+2); רשומה חדשה בסעיף 1 | (ג) |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | קיים | זית ששונה | ארבע מחרוזות | (ב) |
| `tests/features/bookcontent/ui/panels/bookcontent/views/StickyHeadingsTest.kt` | חדש | פרדס | הנתיב, השורה העליונה, הזיכרונות | (ב) |

**רשומות במפה:** סעיף 1: `BookContentPanel.kt` (שורה 1141 במפה), `AppSettings.kt` (617: המפתח החדש בשורת הרשימה, נייד ומאופס ב-`clearAll()`), רשומה חדשה ל-`DisplaySettingsScreen.kt` (Change "modified, +2 −0", Kind "Pardes feature hook", Merge risk "small: one card after Zayit's"), `strings.xml`. סעיף 2: גוש `### Sticky headings` ורשומות לשלושת הקבצים החדשים.

**החלטות פתוחות:**
1. **שורה שמורה או ציור מעל הטקסט.** מומלץ: שורה שמורה, שאף פעם לא מכסה טקסט; התצוגה מתקצרת בכ-24dp כל עוד המתג דולק. חלופה 1, הציור של IntelliJ מעל הטקסט (שתי שורות בלבד ב-`BookContentPanel.kt`): הוא מכסה את ראש השורה הראשונה, וכדי לא להסתיר יעד של קפיצה הוא צריך להיעלם כשהשורה הראשונה מתחילה בדיוק בראש, אבל אז הוא נעלם גם אחרי `PageDown` ואחרי צעד בחיצים (`BookContentView.kt:900-918`, `:380-394`). חלופה 2, ציור מעל הטקסט ועוד `contentPadding` ל-`LazyColumn`, ההתנהגות המדויקת של IntelliJ: עריכה בקובץ הגדול ביותר של זית, וסיכון לחישובים של פס הגלילה.
2. **מקור הנתיב.** מומלץ: `line_toc` והנתיב של כל שורה, מדויק כמו פס הניווט, וכבר רץ באנדרואיד. החלופה: אינדקס כותרות לכל ספר (`getBookToc`, `repo/SeforimRepository.kt:1247-1249`, ומיקומי שורות הכותרת דרך `getLinesByIds`, `:1092-1096`) וחיפוש בינארי בלי שום שאילתה בזמן גלילה; הוא שווה ל-`line_toc` רק בספרים שבהם המחולל בנה אותו כך ("the latest TOC entry whose start line index is <= line's index", `:236-240`) **(לא נבדק על הספרייה)**.
3. **רמות.** מומלץ: עד חמש. החלופה: רק שתיים או שלוש האחרונות.
4. **תוכן העניינים החלופי** (פרשות ועליות שמופיעות בתוך הטקסט, `AltTocState.lineHeadingsByLineId`, `BookContentState.kt:70`): מומלץ: לא בשלב הראשון.
5. **גופן.** מומלץ: של פס הניווט (12sp, `BreadcrumbView.kt:161`), שגדל עם מצב השיעור. החלופה: יחסי לגודל הטקסט.
6. **ברירת מחדל.** מומלץ: דולק, עם מתג. החלופה: כבוי.
7. **לפני הכותרת הראשונה.** מומלץ: שם הספר. החלופה: שורה ריקה.

**סיכונים ולא-ידועים:**
- מה יופיע בפועל תלוי במבנה תוכן העניינים של כל ספר **(לא נבדק)**.
- שורת כותרת שעומדת בראש התצוגה מופיעה פעמיים, בשורה הנעוצה ובטקסט, כמו כותרת רצה בספר מודפס.
- בגרירה מהירה הכותרת מתעדכנת כשהגלילה מאטה (באנדרואיד ההמתנה היא 150 מ"ש, `PLACE_SETTLE_MS`, `ReaderScreen.kt:456`).
- מתג שמשתנה בזמן קריאה מזיז פעם אחת את התצוגה בגובה השורה.
- `bookListState` נוצר מחדש לכל ספר (`remember(selectedBook.id)`, `BookContentPanel.kt:158-166`), והזיכרונות צריכים להתאפס איתו.

**בדיקות:**
- **יחידה, `StickyHeadingsTest.kt`:** `` `the path drops a root repeating the book title` ``, `` `five levels at most, the first ones elided` ``, `` `the top line is the next one once the first is mostly scrolled out` ``, `` `no room on the daf, the scan or a pdf book` ``, `` `one query per line and per toc entry` `` (מאגר מזויף שסופר שאילתות), `` `the switch off leaves no room` ``.
- **בתוכנה:** שולחן ערוך אורח חיים לאורך סימן רנג, מסכת בבלי, חומש; הפעלה וכיבוי של צורת הדף ושל הסריקה; צילום מסך; זמני פריים ב-`E2eReaderPerfScenario` לפני ואחרי.

**היקף ותלות:** כ-200 שורות קוד וכ-120 שורות בדיקה; בקוד של זית 3 שורות ב-`BookContentPanel.kt`, כ-10 ב-`AppSettings.kt` ו-2 ב-`DisplaySettingsScreen.kt`. מאמץ M, שלושה עד חמישה ימים. אינו תלוי בשום פריט; הלחיצות שלו נרשמות בפריט 12, והוא מוצג וגדל במצב השיעור (14).

---

### סדר עבודה מוצע וסך הנגיעה בקוד של זית

1. **14**, שמכניס את `WindowReadingKeys` ואת `ReadingModeHost` (שלוש שורות ב-`MainAppWindow.kt`, שורה ב-`ReaderPanes.kt`, שתיים ב-`WindowBody.kt`).
2. **הליבה של 12 בלי ווים** (`ReadingPlace`, `TabPlaceHistory` ובדיקותיהם), ואז **13**, שמשתמש בה ובמארח.
3. **הווים של 12** (חמישה קבצים, כ-21 שורות).
4. **15** (שלושה קבצים של זית, אחד מהם נפתח לראשונה במפה).

| קובץ של זית | פריטים | שורות בערך |
|---|---|---|
| `app/core/presentation/window/MainAppWindow.kt` | 13, 14 | +3 −1 |
| `app/core/presentation/window/WindowBody.kt` | 12, 14 | +4 |
| `app/features/bookcontent/BookContentEvent.kt` | 12 | +6 |
| `app/features/bookcontent/BookContentViewModel.kt` | 12 | +9 |
| `app/features/bookcontent/BookContentScreen.kt` | 12 | +2 |
| `tabs/TabsViewModel.kt` | 12 (ועוד +2 בשלב ב) | +2 |
| `app/features/bookcontent/ui/panes/ReaderPanes.kt` | 14 | +1 −1 |
| `app/features/bookcontent/ui/panels/bookcontent/BookContentPanel.kt` | 15 | +3 −1 |
| `app/core/settings/AppSettings.kt` | 15 | +10 |
| `app/features/settings/ui/DisplaySettingsScreen.kt` (רשומה חדשה) | 15 | +2 |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | 13, 14, 15 | כ-+16 |

כל פריט נכנס ב-commit משלו בסגנון של אלי (`feat(reader): …`, `feat(tabs): …`), עם הרשומות שלו במפה באותו commit, `check_change_map.py` עובר, `ktlintCheck` ו-`detekt` על המודול, הבדיקות, ובדיקה בתוכנה; ושורה בגרסה הבאה של `desktop/CHANGELOG.md` (של פרדס).

</div>

## The brief the agent got, in short

> Write an exact, code-verified implementation spec in Hebrew, planning only, for four navigation and reading features: back and forward inside a tab by IntelliJ's two rules (a place is recorded only on a real jump, close places merge), with Alt+arrows and the mouse's side buttons; a popup of recent places with their path and the start of their line; one key that enlarges the text for a while, hides every pane and goes full screen, Esc restoring every value; and the heading path of the first visible line pinned above the text. To find out in the code: every navigation path inside a book tab, the keys that are free, whether the mouse's back and forward buttons reach Compose through Nucleus's Tao backend, `HistoryStore`, how full screen, the panes and the text size are kept, and how the reader lays its lines out.

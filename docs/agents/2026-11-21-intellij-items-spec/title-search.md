# Spec: six improvements of the book-title search

- **For:** items 4 to 9 of the proposals the user chose from `docs/INTELLIJ_LESSONS.md` (Hebrew typed on an English layout, ranking by use, recent books in the empty bar, the match highlighted, first-letter keys, an acronym fallback); the choice and its state are in `docs/DESKTOP_STAGE3_PLAN.md`.
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

## מפרט מימוש: שישה שיפורים בחיפוש שמות הספרים (פריטים 4–9)

21.11.2026. מסמך תכנון בלבד: אף קובץ במאגר לא שונה. הפריטים נבחרו מתוך `docs/INTELLIJ_LESSONS.md` (הסעיף "חיפוש והתאמה" ושורות 3–4 בטבלת הצעדים).

**מה נבדק.** פרדס בקומיט `21999b0`; זית הבסיס `d7777e2e` (v0.6.12, לפי שורת ה-`zayit-base` במפת השינויים); תתי-המודולים SeforimLibrary (`4f07145`) ו-SeforimAcronymizer (`dea83b3`), שנקראו בלבד; intellij-community בקומיט `b2ba8bcb8`; Jewel `0.41.0-262.10968.63` כפי שהוא ב-Maven Central (sha256 `b4b2f21e…8cad3`, זהה לעותק שנשמר במחקר הקודם). כל טענה כאן נבדקה בקוד; מה שלא נבדק מסומן **(לא נבדק)**, ומה שנמדד רק על נתוני תחליף מסומן כך.

**קיצורי נתיבים.** הנתיבים יחסיים לשורש המאגר `Pardes/`. כדי שהטבלאות יהיו קריאות:
- `‹desk›/` = `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/`
- `‹test›/` = `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/`
- `‹andr›/` = `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/`
- `‹ij›/` = `intellij-community: ` (מחוץ למאגר)

**טביעות רגל** (כמו בדוח): (א) תצורה; (ב) קובץ של פרדס או וו של שורה אחת; (ג) עריכה בתוך קוד של זית; (ד) Nucleus. אף פריט כאן לא נוגע ב-Nucleus.

**בקצרה, ההמלצות:** פריטים 8 ו-9 נוגעים רק בקבצים של פרדס, ו-9 עדיף על 8 (8 נכנס לתוכו ככלל דירוג); 5 כמעט כולו בקבצים של פרדס; 4, 6 ו-7 דורשים עריכות קטנות ב-`HomeView.kt` של זית, ו-4 גם ב-`SearchHomeViewModel.kt`. באנדרואיד אין "עותק" של האלגוריתם: הקבצים של `framework/search/titles` נבנים שם כמו שהם, ולכן כל קובץ חדש ש-`BookTitleIndex.kt` מפנה אליו חייב להיכנס לרשימת ההעתקה של אנדרואיד, אחרת בניית האנדרואיד נשברת.

### רקע משותף: מה הקוד עושה היום

#### איך `BookTitleIndex` מוצא ספר

**נרמול** (`‹desk›/framework/search/titles/TitleText.kt`, ‏161 שורות, של פרדס):
- `rawWords` (שורות 19–32) מפצל ברווח ובתווי `SEPARATORS` (שורה 160: מקף עברי, מקפים, `/ \ , . : ; ( ) [ ] { } | ! ?`). גרשיים וגרש נשארים בתוך המילה (`QUOTES`, שורה 157), כי הם מסמנים ראשי תיבות.
- `clean` (35–41) מסיר ניקוד וטעמים (U+0591–U+05C7) וכל מה שאינו אות או ספרה, מוריד לאותיות קטנות ומקפל אותיות סופיות (`foldFinal`, ‏45–53).
- `articleForms` (59): המילה עצמה, עם ה"א הידיעה, ובלעדיה (`withoutHe`, שורה 56, רק כשנשארות שתי אותיות לפחות).
- `skeleton` ו-`spellingVariants` (67–96): ו"ו או יו"ד בין שתי אותיות עבריות הן אם קריאה, ושתי מילים הן כתיב זו של זו כשהשלד זהה ואמות הקריאה של אחת כלולות בשנייה (`שלחן`/`שולחן`, `נדה`/`נידה`).
- `queryTokens` (111–131): ראשי תיבות של השאילתה נקראים לפי `TitleAbbreviations` (‏`TitleAbbreviations.kt`, ‏276 מפתחות, מכלי קודש ברשותם, שורות 3–10); מילה בלי גרשיים נקראת גם כמו שהיא (`במדבר`).

**שלבי ההתאמה** ב-`searchBooks` (‏`‹desk›/framework/search/titles/BookTitleIndex.kt`, ‏477 שורות, של פרדס, שורות 109–126):
1. בלי סליחה (`matchTokens(tokens, fuzzy = false)`, שורה 115): כל מילה שהוקלדה, מלבד "ספר", "מסכת", "מס", "מסכתא" (`FILLERS`, שורה 435), נמצאת כמו שהיא או בכתיב אחר בשם הספר, בשם קטגוריה מעליו או בשם מחבר; המילה האחרונה גם כתחילית; בכל סדר. אוצר המילים ממוין, ותחילית היא טווח בו (`wordIds`, ‏356–380).
2. אם לא נמצא כלום: טעות אחת למילה, שתיים במילה של 7 אותיות ומעלה (`LONG_WORD`, ‏436), ממילה של 4 אותיות (`MIN_TYPO_LENGTH`, ‏433); מרחק OSA, שבו החלפת שתי אותיות שכנות היא טעות אחת (`editDistance`, ‏449–475); במילה של 3 אותיות רק אות א, ו או י עודפת או חסרה (`vowelLetterApart`, ‏417–424).
3. אם עדיין לא: כל מילה גם כתחילית (`begun = true`, שורה 116).

**דירוג** (שורות 117–124): `tier` (‏3 = `TIER_WHOLE`, השם הוקלד בשלמותו בכל סדר או אחד מראשי התיבות של הספר; 2 = `TIER_BEGUN`, השאילתה פותחת את השם; 0 בשאר), אחר כך ספרי היסוד של קטגוריה שהשאילתה קוראת בשמה (`ofNamedCategory`), מספר המילים שנמצאו בשם (`titleHits`), ספר יסוד, שם קצר יותר, וסדר הספרייה (`position`). **אין שום אות שימוש.**

**ראשי תיבות היום, שלושה מקורות:**
1. `TitleAbbreviations`, בצד השאילתה: `שוע` נקרא שולחן ערוך, `אוח` אורח חיים.
2. `book_acronym` מהמסד, בצד האינדקס: `BookTitleIndexLoader` קורא `SELECT bookId, term FROM book_acronym` (‏`BookTitleIndexLoader.kt` שורה 96); `acronymKeys` (‏`BookTitleIndex.kt` 413–414) הופך כל מונח למילה מחוברת (`שועאוח`) ולמילותיו ממוינות (`אוח שוע`); המחוברות נכנסות לאוצר המילים (שורה 79), והתאמה מלאה נותנת `TIER_WHOLE` ו-`exactAcronym` (שורות 141–147). המונחים נוצרים בזית על ידי מודל שפה (`SeforimAcronymizer/acronymizer/src/jvmMain/kotlin/io/github/kdroidfilter/seforimacronymizer/Main.kt`, ספריית koog עם OpenAI ו-Gemini) ומועתקים למסד לפי שם הספר המדויק (`SeforimLibrary/generator/otzariasqlite/src/commonMain/kotlin/io/github/kdroidfilter/seforimlibrary/otzariasqlite/Generator.kt`, `fetchAcronymsForTitle`, שורות 1617–1682; הסרת גרשיים עבריים ב-`sanitizeAcronymTerm`, שורה 1704). בנתונים שבתת-המודול (`SeforimAcronymizer/data/acronymizer.sql`) יש 6,962 שמות, 54,540 ראשי תיבות שונים ו-59,863 קישורים, עם טעויות (`ב"ח` רשום לבן איש חי). ספר ששמו בספרייה שונה מהשם שבנתונים לא מקבל כלום.
3. אינדקס החיפוש של זית כגיבוי: `LuceneLookupSearchService.suggestBooks` (‏`‹desk›/framework/search/LuceneLookupSearchService.kt` 258–284) מוסיף אחרי ספרי `BookTitleIndex` את מה שרק אינדקס ה-Lucene מוצא, כשיש פחות מ-`limit`; מתחת ל-2 תווים (`MIN_BOOK_QUERY_LENGTH`, שורה 618) לא מוצע כלום.

**הממשק שמשתמשים בו:** `searchBooks(query, limit): List<BookMatch>` (‏`BookMatch`, שורות 96–106: `book`, `tier`, `titleHits`, `exactAcronym`, והפנימיים `position` ו-`ofNamedCategory`), `searchAuthors(query, limit)` (176–193), `authorsOf(bookId)` (196–197), `bookCount`, `TIER_WHOLE`, `TIER_BEGUN`, `editDistance` (internal). נבנה ב-`BookTitleIndexLoader.load`/`build` (‏23–92) ומוחזק ב-`BookTitleIndexHolder` (104–127), שבונה פעם אחת ומחמם ברקע.

#### מי קורא לו, ושל מי כל קובץ

- `LuceneLookupSearchService.suggestBooks` ← `SearchHomeViewModel.observeQueries` (שורה 293, ההצעות בשורה) ו-`booksAndCategoriesToAdd` (‏422, ספרים להוספה בתוך ספר); `NavigationUseCase.search` (‏`‹desk›/features/bookcontent/usecases/NavigationUseCase.kt` שורה 123, סינון עץ הספרים בקורא); `SearchEntityFinder` (‏`‹desk›/features/search/domain/SearchEntityFinder.kt` שורה 49, החלונית שליד התוצאות).
- הממשק: `‹desk›/features/bookcontent/ui/panels/bookcontent/views/HomeView.kt`: `UnifiedSearchBar` (שורה 504), `SearchBar` (‏1402), `catBookRows` (‏182, סדר השורות), `SuggestionsPanel` (‏831), `SuggestionRow` (‏1292), `withBold` (‏1260). הקריאות לבית המודל מחוברות ב-`‹desk›/core/presentation/tabs/TabsContent.kt` שורות 440–468.

| קובץ | של מי (לפי `docs/ZAYIT_CHANGE_MAP.md` ו-`git diff d7777e2e`) | שורות |
|---|---|---|
| `‹desk›/framework/search/titles/BookTitleIndex.kt`, `TitleText.kt`, `TitleAbbreviations.kt`, `BookTitleIndexLoader.kt` | של פרדס (מפה, סעיף 2, "Books and authors found by name") | 477, 161, 293, 127 |
| `‹desk›/framework/search/LuceneLookupSearchService.kt` | של זית ששונה, ‎+40 −1 | 636 |
| `‹desk›/features/search/SearchHomeViewModel.kt` | של זית ששונה, ‎+254 −15 | 1,066 |
| `‹desk›/features/bookcontent/ui/panels/bookcontent/views/HomeView.kt` | של זית ששונה, ‎+308 −47 | 2,185 |
| `‹desk›/core/presentation/tabs/TabsContent.kt` | של זית ששונה, ‎+45 −2 | 662 |
| `‹desk›/framework/di/modules/AppCoreBindings.kt` | של זית ששונה, ‎+42 −3 | 334 |
| `‹desk›/core/history/HistoryStore.kt` | של זית ששונה, ‎+39 −6 | 224 |
| `SeforimApp/src/commonMain/sqldelight/io/github/kdroidfilter/seforimapp/db/VisitHistory.sq` | של זית כמו ששוחרר | 46 |
| `‹desk›/core/settings/AppSettings.kt` | של זית ששונה, ‎+284 −0 | 1,049 |
| `‹desk›/core/settings/Setting.kt` (‏`SettingsGroup`) | של זית כמו ששוחרר | 76 |
| `‹desk›/features/settings/ui/GeneralSettingsScreen.kt` | של זית ששונה, ‎+7 −2 | 390 |
| `‹desk›/features/search/domain/LibraryCategories.kt` | של פרדס | 232 |
| `‹desk›/features/bookcontent/usecases/NavigationUseCase.kt`, `‹desk›/features/search/domain/SearchEntityFinder.kt` | של זית כמו ששוחררו | 256, 71 |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | של זית ששונה | — |

#### ההיסטוריה (`HistoryStore`)

- **מה נרשם:** `recordBookVisit(bookId, title, timestamp, tocEntryId, lineId)` (‏`HistoryStore.kt` 52–76) כותב שורה אחת לכל זוג (ספר, ערך תוכן) במפתח `book:<id>:<toc>`; ביקור חוזר מעלה את `visitCount` ומעדכן את `visitedAt`, את `title` ("שם הספר - הפרק") ואת `lineId` (‏`VisitHistory.sq` 22–31). הקריאה נעשית פעם אחת לכל ספר שנפתח בלשונית, שתי שניות אחרי הפתיחה (`HISTORY_RECORD_DEBOUNCE_MS`, ‏`BookContentViewModel.kt` 1309), רק בלשונית שבחזית, ולא בלשוניות ששוחזרו בהפעלה (שם 328–364).
- **מה זה אומר:** מספר הפתיחות של ספר הוא סכום `visitCount` של כל השורות שלו, והפתיחה האחרונה היא ה-`visitedAt` הגדול מביניהן.
- **איך קוראים בזול:** אין שאילתה מצטברת לפי ספר. מה שיש: `query("", limit)` (‏115–127) → `selectRecent` (‏`VisitHistory.sq` 33–34, ממוין לפי `visitedAt` עם האינדקס `idx_visit_history_time`, שורה 20), ספרים וחיפושים יחד, ו-`revision` (‏45–46) שעולה בכל כתיבה. הדרך הזולה בלי לגעת בקבצי זית: לקרוא את N השורות האחרונות, לצבור בקוטלין לפי ספר, ולרענן כש-`revision` משתנה. שאילתת `GROUP BY` בקובץ ‎`.sq` חדש של פרדס אפשרית אם SQLDelight מאפשר שאילתה על טבלה שהוגדרה בקובץ אחר של אותו מסד **(לא נבדק)**, ולא נחוצה.
- **האם שורה ריקה כבר מציגה משהו:** לא. כשהשורה הראשית ריקה, `observeQueries` מרוקן הכול ומסתיר (`SearchHomeViewModel.kt` 272–281), והחלון נפתח רק כש-`suggestionsVisible` (‏`HomeView.kt` 1542). ספרים אחרונים קיימים במקומות אחרים: חלון "חיפוש בלשוניות ובהיסטוריה" (Ctrl/Cmd+Shift+A, ‏`‹desk›/core/presentation/components/TabSearchPopup.kt` 154–158) מציג בשדה ריק את 12 הביקורים האחרונים, ספרים וחיפושים (`POPUP_HISTORY_LIMIT`, שורה 87); הווידג'ט "היסטוריה" בדף הבית (‏`‹desk›/features/home/widgets/library/LibraryWidgets.kt` 88–123, ‏40 רשומות), שאינו בפריסת ברירת המחדל (`HomeWidget.kt` 127–134); תפריט המערכת ב-macOS בלבד (`AppNativeMenuBar.kt` 60–64, ‏40 רשומות; `main.kt` 323–325); ודף ההיסטוריה. כלומר פריט 6 קיים בחלקו, אבל לא במקום שבו מקלידים.

#### אנדרואיד

- `androidApp/app/build.gradle.kts`, המשימה `syncDesktopSearch` (שורות 58–75, וההערה שמעליה 51–57), מעתיקה את `TitleText.kt`, `TitleAbbreviations.kt`, `BookTitleIndex.kt` ו-`BookTitleIndexLoader.kt` של המחשב כמו שהם (64–67), ו-`BookTitleIndexTest.kt` רץ גם באנדרואיד (`syncDesktopSemanticTests`, שורה 108). `‹andr›/search/TitleIndex.kt` הוא רק טוען: קורא את הקטלוג ושלוש שאילתות דרך `SplitDatabase` ובונה את האינדקס של המחשב. לכן "העותק של אנדרואיד" אינו עותק: שינוי ב-`BookTitleIndex` מגיע לאנדרואיד בבנייה הבאה, וקובץ חדש ש-`BookTitleIndex.kt` מפנה אליו חייב להיכנס לרשימה המפורשת (שורות 60–72).
- שורת החיפוש של אנדרואיד היא העתקה של זו של המחשב: `‹andr›/ui/search/SearchBarViewModel.kt` (‏`SearchBarUiState` 60–87, ‏`val q = qRaw.trim()` שורה 179), `‹andr›/ui/search/UnifiedSearchBar.kt` (‏`val query = field.text.trim()` שורה 450, `handleTextSearch` 556, הטקסטים העבריים כקבועים 170–190, `SuggestionList` 1397), `‹andr›/search/LuceneLookupSearchService.kt` (וריאנט, `suggestBooks` 257).
- **אין לאנדרואיד תלות ב-Jewel בכלל** (אין "jewel" ב-`androidApp/app/build.gradle.kts` ולא ב-`androidApp/gradle/libs.versions.toml`), ולכן `BookTitleIndex.kt` לא יכול לייבא את `SpeedSearchMatcher`.
- `androidApp/docs/PARITY.md` שורות 207–215 ("Pardes desktop's books and authors found by name") מתעדות את החיבור.

#### ניסויים שהורצו לצורך המפרט

ספריית פרדס לא הייתה זמינה כאן (אין `seforim.db`), ולכן נבנה `BookTitleIndex` אמיתי (עותק של שלושת קובצי `titles`, מהודר ב-Kotlin 2.0.21 ורץ על JDK 21) על **6,962 השמות של SeforimAcronymizer וראשי התיבות שלו כתחליף לספרייה**, בלי קטגוריות, מחברים או סימון ספרי יסוד. בניית האינדקס: כ-0.7 שנייה ב-JVM קר. נבדקו: המרת המקלדת דרך האינדקס, 27 מילים באנגלית, 24 ראשי תיבות מול האינדקס הקיים, מפתחות אותיות ראשונות, מתאם תחיליות שנכתב לניסוי, והפורט של Jewel למתאם של IntelliJ. המספרים מובאים בפריטים; הם אומרים מה קורה לשמות אמיתיים, לא כמה מצליח בספרייה המלאה.

#### 4. תיקון מקלדת אנגלית

**בקצרה:** כששאילתה שכולה אותיות לטיניות לא מוצאת שום ספר או מחבר, היא נקראת כאותיות העבריות שעל אותם מקשים בפריסה הישראלית (SI-1452), ואם הקריאה הזו מוצאת משהו בלי סליחת טעויות, ההצעות מוצגות לפיה מתחת לשורה "חיפשת: …".

**מה רואים:**
- מקלידים `akjb` בשורת החיפוש הראשית (בדף הבית ובשורה של דף התוצאות). בראש חלון ההצעות, מעל השורות, מופיעה שורת מידע אפורה **"חיפשת: שלחן"**, ומתחתיה ההצעות כאילו הוקלד `שלחן`: שולחן ערוך וחלקיו, קיצור שלחן ערוך. בניסוי על שמות התחליף: 95 ספרים, הראשונים "שולחן ערוך הרב" ו"שולחן ערוך, הקדמה".
- `,urv` → "חיפשת: תורה", וראשונים הספרים ששמם פותח ב"תורה".
- `nabv crurv` → "חיפשת: משנה ברורה". משנה ברורה בשורה הראשונה (שם שהוקלד בשלמותו), ו-Enter פותח אותו.
- `ehmur aukji grul` → "חיפשת: קיצור שולחן ערוך" (בניסוי נמצא "קיצור שלחן ערוך").
- `au"g` → "חיפשת: שו"ע", ו-`rnc"o` → "חיפשת: רמב"ם": המירכאות נשארות, כי Shift עם המקש של הגרש נותן `"` בשתי הפריסות.
- `AKJB` (כשה-Caps Lock דולק) → כמו `akjb`.
- שורת "חיפוש במאגר" מציגה ומחפשת את העברית ("חיפוש במאגר: שלחן").
- מילה באנגלית שהוקלדה בכוונה (`torah`, `rashi`, `book`) לא משתנה: אין "חיפשת". שאילתה מעורבת (`akjb ערוך`) לא מומרת. קישור שהודבק (`zayit://…`) לא מומר.
- באנדרואיד: אותו דבר בשורה החכמה (בית ותוצאות), עם אותו נוסח.
- *מחרוזת חדשה:* `search_layout_fixed` = `חיפשת: %1$s`, ב-`SeforimApp/src/commonMain/composeResources/values/strings.xml` אחרי `category_base_books_only` (שורה 552), עם ההערה `<!-- Pardes: Hebrew typed on the English layout, read as the Hebrew letters of its keys (the suggestions' first line) -->`. בלי ניקוד "חיפשת" נקרא בזכר ובנקבה כאחד. באנדרואיד: `private const val SEARCH_LAYOUT_FIXED = "חיפשת: %1\$s"` ליד שורה 188 של `UnifiedSearchBar.kt`.

**איך זה עובד:**
1. **מתי:** `HebrewKeyboard.typedInLatin(text)` נכון כשיש בטקסט לפחות שלוש אותיות לטיניות (או שתיים עם `"`), אין בו אות עברית (U+05D0–U+05EA), ואין בו `://`.
2. **ההמרה:** כל תו של הטקסט הגולמי, אחרי הורדה לאותיות קטנות, עובר בטבלה שלמטה; ספרות, רווחים, `"` ו-`-` נשארים. ההמרה נעשית על הטקסט הגולמי, לפני ש-`TitleText` מפצל, כי `,`, `.`, `;` ו-`/` הם מפרידים שם (`SEPARATORS`, שורה 160) ובמקלדת העברית הם ת, ץ, ף ונקודה. אחר כך אות בסוף מילה הופכת לסופית ואות סופית באמצע מילה לרגילה (`akjb` → שלחנ → שלחן), כך שגם החיפוש במאגר וגם התצוגה מקבלים עברית תקינה; לחיפוש השמות זה לא משנה, כי `TitleText.clean` מקפל סופיות ממילא.
3. **קבלה:** `BookTitleIndex.layoutFixed(query)` מחזיר את העברית רק כשהשאילתה כמו שהוקלדה לא מוצאת ספר (`searchBooks`) ולא מחבר (`searchAuthors`), והעברית מוצאת ספר (`matchTokens(…, fuzzy = false)` ואחריו `matched`) או מחבר (`matchAuthors(…, fuzzy = false)`) **בלי סליחת טעויות**. הכול בזיכרון; לפי ה-KDoc של המחלקה שאילתה עולה פחות ממילישנייה (`BookTitleIndex.kt` 27–28) **(לא נמדד על הספרייה המלאה)**.
4. **שימוש:** ב-`SearchHomeViewModel.observeQueries`, בשורה 262, השאילתה מוחלפת בעברית לכל צינור ההצעות (ספרים, מחברים, מראי מקומות, קטגוריות), ו-`SearchHomeUiState.layoutFixedQuery` מקבל את העברית (או null). השורה מציגה את שורת המידע, ומשתמשת בעברית לסדר השורות (`catBookRows`), לשורת החיפוש במאגר ולספרי ה-PDF האישיים.
5. **למה בלי טעויות:** מתוך 27 מילים באנגלית שהומרו, כשהטעויות נסלחות 7 קיבלו התאמות עבריות שגויות (`rashi`→רשדין מצא את "רשי על דניאל" בהחלפת שתי אותיות; `torah`→אםרשי מצא 15 ספרים; `zohar`, `book`, `test`, `abc`, `ok`). בלי סליחה נשארו רק `abc` (ספר אחד) ו-`ok` (‏392 ספרים, תחילית של שתי אותיות), ושתי האותיות של `ok` נופלות בכלל של שלוש אותיות לפחות. המחיר: שגיאת הקלדה מעל פריסה שגויה (`akjb gruj` = שלחן ערוח) לא תתוקן (בלי סליחה: 0; עם סליחה: 76).

*טבלת המקשים.* נבדקה מול שני קובצי פריסה מקומיים שזהים זה לזה: `/usr/share/X11/xkb/symbols/il`, הווריאנט `basic` ("This is an implemetation of the Israeli standard SI-1452 (2013)", שורה 3; המקשים בשורות 15–69), ו-`/usr/share/vim/vim91/keymap/hebrew_utf-8.vim` ("the standard Israeli keyboard layout", שורות 9–41). **(נבדק מול קובצי הפריסה, לא מול נוסח התקן עצמו; לא נבדק מול פריסות Windows ו-macOS.)**

| מקש | עברית | מקש | עברית | מקש | עברית |
|---|---|---|---|---|---|
| `q` | `/` | `a` | ש | `z` | ז |
| `w` | `'` | `s` | ד | `x` | ס |
| `e` | ק | `d` | ג | `c` | ב |
| `r` | ר | `f` | כ | `v` | ה |
| `t` | א | `g` | ע | `b` | נ |
| `y` | ט | `h` | י | `n` | מ |
| `u` | ו | `j` | ח | `m` | צ |
| `i` | ן | `k` | ל | `,` | ת |
| `o` | ם | `l` | ך | `.` | ץ |
| `p` | פ | `;` | ף | `/` | `.` |
| `` ` `` | `;` | `'` | `,` | | |

לא ממופים (אין עליהם אות): ספרות, `-`, `=`, `[` ו-`]` (מתחלפים זה בזה בפריסה העברית), ו-`"` (זהה בשתיהן). אותיות גדולות נקראות כקטנות (ב-`il(basic)` ‏Shift על מקש אות נותן את האות הלטינית הגדולה). *שימו לב:* בחלק השני של התקן (2018; ‏`il(si2)` באותו קובץ, שורות 74–95) ת עוברת ל-I, ן ל-W, ץ ל-Q, ו-`' , . /` נשארים סימני פיסוק.

*מה נלקח מ-IntelliJ:* רעיון בלבד, בכיוון ההפוך. `FixingLayoutMatcher` (‏`‹ij›/platform/util/text-matching/src/com/intellij/psi/codeStyle/FixingLayoutMatcher.kt` 13–71) ממיר רק כשכל האותיות מעל U+007F (`fixLayout`, ‏31–54), ללטינית, דרך `KeyboardLayoutConverter` (‏`‹ij›/platform/util/text-matching/src/com/intellij/util/text/matching/KeyboardLayoutConverter.kt` 14–46, מפה רוסית מובנית), כגיבוי שרץ רק כשהמתאם הראשי לא מוצא כלום (`MatcherWithFallback.kt` 22–26). פרדס לוקח את הצורה (המרה רק כשכל האותיות מהכתב "הלא נכון", ורק כשלא נמצא כלום) לכיוון לטינית→עברית. שום קוד לא מועתק, ולכן אין צורך בהודעת Apache. לא נלקח: `KeyboardLayoutUtil`, שלומד את הפריסה מאירועי מקשים.

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `‹desk›/framework/search/titles/HebrewKeyboard.kt` | חדש | פרדס | `internal object HebrewKeyboard`: `typedInLatin(text)` ו-`toHebrew(text)` (הטבלה ואחריה הסופיות), כ-45 שורות | (ב) |
| `‹desk›/framework/search/titles/BookTitleIndex.kt` | קיים | פרדס | `fun layoutFixed(query: String): String?` אחרי `searchAuthors` (שורה 193), עם שלושת התנאים, דרך `matchTokens`, `matched` ו-`matchAuthors` הפרטיים; כ-15 שורות | (ב) |
| `‹desk›/framework/search/LuceneLookupSearchService.kt` | קיים | זית ששונה | אחרי `authorsOfBook` (שורות 287–288): `fun layoutFixed(raw: String): String? = titleIndex()?.layoutFixed(raw.trim())` עם KDoc; 3 שורות | (ג) |
| `‹desk›/features/search/SearchHomeViewModel.kt` | קיים | זית ששונה | `SearchHomeUiState` (‏114–144): `val layoutFixedQuery: String? = null` עם KDoc. `observeQueries` שורה 262: `val q = qRaw.trim()` נהיה `val typed = qRaw.trim()`, ‏`val fixed = withContext(Dispatchers.Default) { lookup.layoutFixed(typed) }`, ‏`val q = fixed ?: typed`, ואחריהן `_uiState.value = _uiState.value.copy(layoutFixedQuery = fixed)`; כ-5 שורות, עם הערה "Pardes:" | (ג) |
| `‹desk›/features/bookcontent/ui/panels/bookcontent/views/HomeView.kt` | קיים | זית ששונה | `UnifiedSearchBar`: `myPdfBookSuggestions(searchUi.layoutFixedQuery ?: referenceSearchState.text)` (שורה 609) ו-`layoutFixedQuery = searchUi.layoutFixedQuery` לקריאה ל-`SearchBar` (‏643–725). `SearchBar`: פרמטר חדש; `val query = layoutFixedQuery ?: state.text.toString().trim()` (‏1513); אותו דבר ב-`handleTextSearch` (‏1641); בקריאה ל-`SuggestionsPanel` (‏2032–2062) התווית ו-`textSearchQuery` מ-`query`, ו-`header = layoutFixedQuery?.let { stringResource(Res.string.search_layout_fixed, it) }`. ‏`SuggestionsPanel` (‏831): `header: String? = null`, ‏`Text` אפור מעל ה-`LazyColumn` ולא בתוכו (כדי לא להזיז את אינדקסי הגלילה של שורות 850–868); כ-15 שורות | (ג) |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | קיים | זית ששונה | `search_layout_fixed` אחרי שורה 552 | (ג), שורת משאב |
| `‹test›/framework/search/titles/HebrewKeyboardTest.kt` | חדש | פרדס | הטבלה והתנאים | (ב) |
| `‹test›/framework/search/titles/BookTitleIndexTest.kt` | קיים | פרדס | מקרי `layoutFixed` | (ב) |
| `androidApp/app/build.gradle.kts` | קיים | פרדס | ‏`syncDesktopSearch` (‏60–72): `"io/github/kdroidfilter/seforimapp/framework/search/titles/HebrewKeyboard.kt"`. **חובה**, אחרת `BookTitleIndex.kt` לא מתהדר באנדרואיד. ‏`syncDesktopSemanticTests` (‏95–111): `HebrewKeyboardTest.kt`, רשות | (ב) |
| `‹andr›/search/LuceneLookupSearchService.kt` | קיים | פרדס (וריאנט) | ‏`layoutFixed(raw)` אחרי `authorsOfBook` (שורה 286), כמו במחשב | (ב) |
| `‹andr›/ui/search/SearchBarViewModel.kt` | קיים | פרדס (העתקה) | ‏`SearchBarUiState.layoutFixedQuery` (‏60–87); שורה 179 כמו במחשב, עם `lookup()?.layoutFixed(typed)` | (ב) |
| `‹andr›/ui/search/UnifiedSearchBar.kt` | קיים | פרדס (העתקה) | הקבוע `SEARCH_LAYOUT_FIXED` (ליד 188); ‏`query` (‏450) ו-`handleTextSearch` (‏556) לוקחים את העברית; ‏`SuggestionList` (‏1397) מקבל `header` | (ב) |
| `‹andr›/search/TitleIndex.kt` | קיים | פרדס | בלי שינוי: הוא רק טוען | — |
| `androidApp/docs/PARITY.md` | קיים | פרדס | שורה בסעיף "Pardes desktop's books and authors found by name" (‏207–215) | (ב) |

רשומות במפת השינויים (באותו commit, ואחריהן `python3 .claude/skills/zayit-fidelity/scripts/check_change_map.py`):
- **חדשות, סעיף 2, תחת "Books and authors found by name":** `‹desk›/framework/search/titles/HebrewKeyboard.kt` (Feature: Book and author titles search. Size. What: "`HebrewKeyboard`, Hebrew typed with the keyboard on its English layout read as the letters on its keys in the Israeli layout (SI-1452, as xkb's `il(basic)` has it): `typedInLatin`, `toHebrew`, final letters at word ends." Reached from: `BookTitleIndex.layoutFixed`. Follows: IntelliJ's `FixingLayoutMatcher`, the other way round, an idea and no code. Tests: `HebrewKeyboardTest`, `BookTitleIndexTest`), ו-`‹test›/framework/search/titles/HebrewKeyboardTest.kt`.
- **שכתוב:** `BookTitleIndex.kt` (Size; What: `layoutFixed`), `BookTitleIndexTest.kt` (Size, What), ופסקת הפתיחה של הקבוצה ("Hebrew typed on the English layout").
- **סעיף 1:** `LuceneLookupSearchService.kt` (Change עם ספירת השורות החדשה; What: `layoutFixed`), `SearchHomeViewModel.kt` (What: `layoutFixedQuery` והשאילתה שנקראת כעברית; Why: chosen on 2026-11-21 from docs/INTELLIJ_LESSONS.md), `HomeView.kt` (What: השורות, החיפוש במאגר וספרי ה-PDF מהעברית; `header` של `SuggestionsPanel`), `strings.xml` (מחרוזת אחת, ורשימת הבלוקים).
- **סעיף 3, שורות הטבלה:** `androidApp/app/build.gradle.kts`, `‹andr›/search/LuceneLookupSearchService.kt`, `‹andr›/ui/search/SearchBarViewModel.kt`, `‹andr›/ui/search/UnifiedSearchBar.kt`, `androidApp/docs/PARITY.md`.

**החלטות פתוחות:**
1. **נוסח השורה:** "חיפשת: %1$s" / "מוצגות תוצאות עבור: %1$s" / "האם התכוונת ל: %1$s?". ברירת מחדל: "חיפשת: %1$s".
2. **סליחת טעויות בעברית שהומרה:** בלי סליחה (ברירת מחדל, לפי הניסוי) / עם סליחה (מתקן גם `akjb gruj`, אבל הופך את `torah` ו-`rashi` לעברית).
3. **אורך מינימלי:** שלוש אותיות, או שתיים עם `"` (ברירת מחדל) / שתי אותיות.
4. **החלק השני של SI-1452 (2018):** רק הטבלה הקלאסית (ברירת מחדל) / גם הטבלה של 2018 כניסיון שני כשהראשונה לא מצאה.
5. **הטקסט שבשדה:** נשאר כמו שהוקלד ושורת המידע מסבירה (ברירת מחדל) / מוחלף בעברית כשבוחרים שורה או מחפשים במאגר / מוחלף מיד (לא מומלץ: ממשיכים להקליד באנגלית לתוך עברית).
6. **איפה עוד:** רק בשורה הראשית (ברירת מחדל) / גם בסינון עץ הספרים בקורא (`NavigationUseCase.search`, שורה 123: שורה אחת בקובץ של זית שלא שונה, כלומר רשומה חדשה בסעיף 1) / גם בשדה תוכן העניינים כשנבחר ספר (`tocQuery`, ‏`SearchHomeViewModel.kt` 368).
7. **שאילתה מעורבת** (עברית ולטינית): לא מומרת (ברירת מחדל) / רק המילים הלטיניות מומרות.

**סיכונים ולא-ידועים:**
- התאמות שגויות למילים באנגלית: נמדדו על שמות התחליף (ראו "איך זה עובד", סעיף 5); בספרייה המלאה, עם קטגוריות ומחברים, יש יותר מילים שהעברית השגויה יכולה לפגוש **(לא נמדד)**.
- משתמשים בפריסת 2018, או בפריסה של Windows או macOS שאולי שונה במקשי הפיסוק **(לא נבדק)**, יקבלו ת, ן, ץ שגויות.
- בזמן הקלדה שורת המידע מציגה סופית באות האחרונה של מילה שעדיין מוקלדת (`nab` מציג "משן" עד שמגיע `v`). זה עניין תצוגה בלבד.
- `layoutFixed` מריץ עד ארבעה חיפושים בזיכרון על כל הקשה לטינית; בבית המודל זה בתוך `withContext(Dispatchers.Default)`.
- הספרים שמגיעים רק מאינדקס ה-Lucene (אחרי ספרי `BookTitleIndex`) לא נבדקים בתנאי "לא נמצא כלום". ספר עם שם לטיני ימצא ב-`BookTitleIndex` עצמו, ולכן לא תהיה המרה.

**בדיקות:**
- `‹test›/framework/search/titles/HebrewKeyboardTest.kt`: `toHebrew("akjb") == "שלחן"`; `toHebrew(",urv") == "תורה"`; `toHebrew("AKJB") == "שלחן"`; `toHebrew("au\"g") == "שו\"ע"`; `toHebrew("rnc\"o") == "רמב\"ם"`; `toHebrew("rw") == "ר'"`; `toHebrew("nabv crurv") == "משנה ברורה"`; `typedInLatin("akjb")` נכון; `typedInLatin("ok")` לא נכון (שתי אותיות); `typedInLatin("n\"c")` נכון (שתיים עם מירכאות); `typedInLatin("akjb שלחן")` לא נכון; `typedInLatin(",./")` לא נכון (אין אותיות); `typedInLatin("zayit://book/1")` לא נכון.
- `‹test›/framework/search/titles/BookTitleIndexTest.kt`, על הספרים שכבר בקובץ (שורות 25–39): `layoutFixed("akjb")` הוא "שלחן" (שולחן ערוך, אורח חיים נמצא); `layoutFixed("nabv crurv")` הוא "משנה ברורה"; `layoutFixed("torah")` הוא null; `layoutFixed("משנה")` הוא null; `layoutFixed("ok")` הוא null. הבדיקות האלה ירוצו גם באנדרואיד, כי הקובץ כבר בסנכרון הבדיקות.
- באפליקציה (בעזרת `pardes-desktop-run`): להקליד `akjb`, `nabv crurv`, `,urv`, `torah` בשורה בדף הבית ובדף התוצאות; לבדוק את שורת המידע, את ההצעה הראשונה, ש-Enter על משנה ברורה פותח אותה, שהחיפוש במאגר מחפש "שלחן", ושקישור `zayit://` שהודבק עדיין נפתח. באנדרואיד: `adb shell input text akjb` לתוך השורה (זה בדיוק מקליד אותיות לטיניות).

**היקף ותלות:** S. במחשב: 2 קבצים חדשים (כ-45 שורות קוד וכ-70 שורות בדיקה) וכ-40 שורות ב-5 קבצים קיימים. באנדרואיד: כ-25 שורות ב-4 קבצים ושורה ב-PARITY.md. לא תלוי בפריטים אחרים. פריט 7 צריך להדגיש לפי `layoutFixedQuery` כשהוא קיים, ופריט 6 משתמש באותו פרמטר `header` של `SuggestionsPanel` (מי שנכנס ראשון מוסיף אותו).

#### 5. שקלול לפי שימוש

**בקצרה:** בין ספרים שמתאימים למה שהוקלד באותה דרגה בדיוק, ספר שנפתח הרבה ולאחרונה עולה, לפי `log1p(פתיחות) × exp(−ימים מהפתיחה האחרונה / 30)` מתוך `HistoryStore`, ויש מתג לכבות את זה.

**מה רואים:**
- מי שפותח משנה ברורה כל יום ומקליד `משנה`: היום ראשונים ספרי היסוד ששמם פותח ב"משנה" (משנה ברכות וכו', שעולים בזכות `isBaseBook`, שורה 122, אם הם מסומנים ספרי יסוד בספרייה, **(לא נבדק על הספרייה)**), ומשנה ברורה אחריהם. אחרי השינוי משנה ברורה ראשון בתוך הדרגה שלו (`TIER_BEGUN`, מילה אחת בשם).
- `שולחן ערוך`: ארבעת החלקים באותה דרגה (שתי מילים בשם); החלק שנפתח הכי הרבה לאחרונה ראשון, אורח חיים אצל אחד וחושן משפט אצל אחר.
- *אף פעם לא:* ספר ששמו "שולחן ערוך" בדיוק (`TIER_WHOLE`) תמיד לפני "שולחן ערוך הרב" (`TIER_BEGUN`), גם אם השני נפתח כל יום; ראשי תיבות שהוקלדו בשלמותם (`שוע אוח`, `TIER_WHOLE`) תמיד לפני ספר שנמצא לפי מילים.
- המתג: הגדרות › כללי, אחרי "סידור חכם (ניסיוני)". כותרת `settings_title_rank_by_use` = "ספרים שנפתחו לאחרונה קודם בהצעות"; תיאור `settings_title_rank_by_use_description` = "בין ספרים ששמם מתאים במידה שווה למה שהוקלד בשורת החיפוש, מוצגים קודם הספרים שנפתחו יותר ולאחרונה. ספר ששמו או ראשי התיבות שלו הוקלדו במלואם קודם תמיד. הסדר נלמד מהיסטוריית הספרים שנפתחו, וניקוי ההיסטוריה מאפס אותו." דולק כברירת מחדל (החלטה 4).
- איפה זה פועל: כל מי שקורא ל-`BookTitleIndex.searchBooks` במחשב: השורה (בית ותוצאות), סינון עץ הספרים בקורא וחלונית הישויות שליד התוצאות. באנדרואיד לא משתנה כלום עד שיהיה שם מקור שימוש משלו.

ערכים לדוגמה (מחושבים): פתיחה אחת היום 0.693; אחת לפני שבוע 0.549; אחת לפני חודש 0.255; אחת לפני חודשיים 0.094; שלוש לפני יומיים 1.297; עשר, האחרונה אתמול, 2.319; עשר, האחרונה לפני חודש, 0.882; חמישים, האחרונה אתמול, 3.803. הקבוע 30 יום הוא זמן הדעיכה פי e; זמן מחצית החיים הוא 20.8 יום.

**איך זה עובד:**
1. `TitleUsage` (חדש, בתיקייה המשותפת לאנדרואיד): `fun interface TitleUsage { fun of(bookId: Long): Double }`, ‏`NONE`, ו-`frecency(opens, lastOpenedAt, now)`.
2. `BookUsage` (חדש, מחשב בלבד, `‹desk›/core/history/BookUsage.kt`): `@Inject @SingleIn(AppScope::class)`, מממש `TitleUsage`. ב-scope משלו (`@StructuredScope private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`, כמו `‹desk›/features/pdf/PdfBookStore.kt` שורה 103) הוא אוסף את `historyStore.revision` ומחשב מחדש `@Volatile Map<Long, Double>`: `historyStore.query("", 2_000)`, רק שורות `VisitKind.BOOK` עם `bookId`, קיבוץ לפי ספר, סכום `visitCount`, מקסימום `visitedAt`, `frecency`, ושמירה רק של ערכים מ-0.1 ומעלה. `of(id)` מחזיר 0 כשהמתג כבוי.
3. `BookTitleIndex(books, authors, usage: TitleUsage = TitleUsage.NONE)`. ב-`matched()` (‏129–154) כל `BookMatch` מקבל `internal val use: Double` פעם אחת, ובסדר (שורות 117–124) נכנס `.thenByDescending { it.use }` אחרי `titleHits` ולפני `isBaseBook`. "אותה דרגה" פירושה אותו `tier`, אותו `ofNamedCategory` ואותו `titleHits`. ה-KDoc (שורות 34–37) מקבל "the books read most and lately" במקום המתאים.
4. `BookTitleIndexLoader.load(catalog, dbPath, usage = TitleUsage.NONE)` ו-`build(catalog, usage = TitleUsage.NONE, query)`; הקריאה של אנדרואיד `build(catalog) { … }` (‏`‹andr›/search/TitleIndex.kt` 29) ממשיכה להתהדר כמו שהיא.
5. `AppCoreBindings.provideLuceneLookupSearchService` (שורות 231–247) מקבל `bookUsage: BookUsage` וקורא `BookTitleIndexLoader.load(it, dbPath, usage = bookUsage)` (שורה 243).
6. המתג: `object TitleSearchSettings : SettingsGroup("title_search") { val rankByUse by boolean(true) }`, בהגדרות המוקלדות של זית שמשאירות את ההגדרה מחוץ ל-`AppSettings.kt` (‏`Setting.kt` 27–43; `appSettings[...]` ו-`appSettings.flow(...)`, ‏`AppSettings.kt` 551–561; ‏`clearAll()` מאפס אותן, שורה 833). ההגדרה עוברת עם הגיבוי רק אם המפתח נוסף ל-`PORTABLE_KEYS` (‏859–895).
7. הכרטיס: `TitleSearchCard()` ב-`‹desk›/features/settings/general/TitleSearchCard.kt`, כמו `‹desk›/features/settings/siddur/OpenSiddurCard.kt`, וקריאה אחת אחרי `OpenSiddurCard()` ב-`GeneralSettingsScreen.kt` (שורה 150).

*מה נלקח מ-IntelliJ:* הרעיון. ב-Search Everywhere אותות השימוש המקומיים הם `stat_use_count`, ‏`stat_recency`, ‏`stat_is_most_recent`, ‏`stat_is_most_popular` (`‹ij›/plugins/search-everywhere-ml/ranking/core/src/com/intellij/searchEverywhereMl/ranking/core/features/SearchEverywhereCommonFeaturesProvider.kt` 22–25, נקרא ב-`git show`) ו-`was_selected_in_last_minute/hour/…` (`…/features/statistician/ContributorsLocalStatisticsFields.kt` 14–23), והם נכנסים שם למודל מדרג נלמד. פרדס לוקח נוסחה קבועה ושקופה במקום מודל. המתג בא משרשורי התמיכה של JetBrains, שבהם משתמשים כיבו את הסדר הנלמד ואת "קבצים אחרונים בראש הרשימה" כדי לקבל סדר צפוי **(לא מאומת)**. שום קוד לא מועתק.

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `‹desk›/framework/search/titles/TitleUsage.kt` | חדש | פרדס | הממשק, `NONE`, ‏`frecency`; כ-30 שורות | (ב) |
| `‹desk›/core/history/BookUsage.kt` | חדש | פרדס | `BookUsage` ו-`TitleSearchSettings`; כ-55 שורות | (ב) |
| `‹desk›/features/settings/general/TitleSearchCard.kt` | חדש | פרדס | `SettingCard` (‏`‹desk›/features/settings/ui/SettingsComponents.kt` 32–37) על ההגדרה; כ-25 שורות | (ב) |
| `‹desk›/framework/search/titles/BookTitleIndex.kt` | קיים | פרדס | פרמטר `usage` (‏40–43), השדה `use` ב-`BookMatch` (‏96–106), שורה בסדר (‏121–122), משפט ב-KDoc | (ב) |
| `‹desk›/framework/search/titles/BookTitleIndexLoader.kt` | קיים | פרדס | הפרמטר `usage` ב-`load` (‏23–26) וב-`build` (‏47–50), ומסירה ל-`BookTitleIndex` (שורה 91) | (ב) |
| `‹desk›/framework/di/modules/AppCoreBindings.kt` | קיים | זית ששונה | `provideLuceneLookupSearchService` (‏231–247): פרמטר `bookUsage` ו-`usage = bookUsage`; 2 שורות | (ג) |
| `‹desk›/features/settings/ui/GeneralSettingsScreen.kt` | קיים | זית ששונה | `TitleSearchCard()` אחרי שורה 150, וייבוא | (ב), וו של שורה |
| `‹desk›/core/settings/AppSettings.kt` | קיים | זית ששונה | רק אם ההגדרה ניידת: `TitleSearchSettings.rankByUse.key` ב-`PORTABLE_KEYS` (‏859–895) | (ג) |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | קיים | זית ששונה | שתי מחרוזות, ליד `settings_open_siddur` (‏1161–1162) | (ג), שורות משאב |
| `‹test›/framework/search/titles/TitleUsageTest.kt`, `‹test›/core/history/BookUsageTest.kt` | חדשים | פרדס | הנוסחה; הצבירה מההיסטוריה | (ב) |
| `‹test›/framework/search/titles/BookTitleIndexTest.kt` | קיים | פרדס | דירוג בתוך דרגה, ואף פעם לא בין דרגות | (ב) |
| `androidApp/app/build.gradle.kts` | קיים | פרדס | `syncDesktopSearch`: `TitleUsage.kt`. **חובה** (‏`BookTitleIndex.kt` מפנה אליו) | (ב) |

רשומות במפת השינויים: חדשות בסעיף 2 ל-`TitleUsage.kt`, ‏`BookUsage.kt` (Reached from: `AppCoreBindings.kt`; Follows: `PdfBookStore`'s scope, Zayit's typed settings), ‏`TitleSearchCard.kt` (Reached from: `GeneralSettingsScreen.kt`; Follows: `OpenSiddurCard.kt`), ‏`TitleUsageTest.kt`, ‏`BookUsageTest.kt`; שכתוב של `BookTitleIndex.kt`, ‏`BookTitleIndexLoader.kt`, ‏`BookTitleIndexTest.kt`; בסעיף 1: `AppCoreBindings.kt` (What וספירת השורות), `GeneralSettingsScreen.kt` (What: `TitleSearchCard()`; ה-Kind נשאר "branding hook and Pardes feature hook"), `AppSettings.kt` (אם ניידת), `strings.xml`; בסעיף 3: השורה של `androidApp/app/build.gradle.kts`.

**החלטות פתוחות:**
1. **הנוסחה:** `log1p(n) × exp(−d/30)` (ברירת מחדל) / דליים כמו ב-Search Everywhere (היום, השבוע, החודש) / קבוע זמן אחר מ-30 יום.
2. **הסף:** ציון מתחת ל-0.1 נחשב 0 (ברירת מחדל: פתיחה אחת נשכחת אחרי כ-58 יום, עשר אחרי כ-95) / בלי סף.
3. **המקום בסדר:** אחרי `titleHits`, לפני ספר יסוד (ברירת מחדל: רק שובר שוויון) / מיד אחרי `tier` (חזק יותר: ספר פופולרי עוקף ספר עם יותר מילים בשם באותה דרגה).
4. **ברירת המחדל של המתג:** דולק (ברירת מחדל) / כבוי.
5. **ניידות:** ההגדרה עוברת עם הגיבוי, שורה ב-`PORTABLE_KEYS` (ברירת מחדל) / נשארת במחשב.
6. **ספרי יסוד של קטגוריה שנקראה בשמה** (`תנך`): גם הם מסודרים לפי שימוש ביניהם (ברירת מחדל; הם נשארים לפני כל ספר אחר בדרגה 0, כמו שהתבקש ב-10.11) / נשארים בסדר הספרייה.
7. **מחברים:** בלי שקלול (ברירת מחדל) / גם מחברים לפי הספרים שלהם שנפתחו.
8. **מתג אחד לפריטים 5 ו-6:** לא, לפריט 6 אין מתג כי הוא מופיע רק בפעולה מפורשת (ברירת מחדל) / מתג אחד, "שימוש בהיסטוריה בשורת החיפוש".

**סיכונים ולא-ידועים:**
- סדר פחות צפוי; לכן המתג, ולכן רק שובר שוויון.
- לולאת חיזוק: הספר הראשון נפתח יותר כי הוא ראשון. הדעיכה בזמן מחלישה אותה.
- ההיסטוריה סופרת פתיחות רק בלשונית שבחזית ולא בשחזור הפעלה (`BookContentViewModel.kt` 328–364), כך ש"שימוש" הוא בפועל "פתיחות".
- רענון אחרי כל כתיבה להיסטוריה, כולל כל חיפוש, כי `revision` עולה גם ב-`recordSearchVisit`: קריאה של עד 2,000 שורות עם אינדקס, כמה מילישניות **(לא נמדד)**. אפשר להשהות (debounce).
- השאילתה הראשונה לפני הרענון הראשון יוצאת בלי שקלול. אין לזה נזק.
- `TestLookup.appLookup` (‏`‹test›/framework/search/titles/TestLookup.kt`) והבדיקות הקיימות לא משתנים, כי ברירת המחדל היא `NONE`.

**בדיקות:**
- `‹test›/framework/search/titles/TitleUsageTest.kt`: ‏`frecency(1, now, now)` ≈ 0.693; ‏30 יום ≈ 0.255; עשר פתיחות לפני 90 יום ≈ 0.119.
- `‹test›/framework/search/titles/BookTitleIndexTest.kt`: עם `usage` שנותן 3.0 למשנה ברורה ועם ספר יסוד "משנה ברכות" בלי שימוש, `searchBooks("משנה")` מחזיר את משנה ברורה ראשון, ובלי `usage` את משנה ברכות ראשון. עם ספר ששמו "שולחן ערוך" וספר "שולחן ערוך הרב" שיש לו שימוש גבוה, ‏`searchBooks("שולחן ערוך")` מחזיר את "שולחן ערוך" ראשון. `usage` שמחזיר 0 נותן בדיוק את הסדר של היום.
- `‹test›/core/history/BookUsageTest.kt`, על `UserSettingsDb` בזיכרון כמו `‹test›/core/history/HistoryStoreTest.kt`: שתי שורות לאותו ספר בשני פרקים נצברות לסכום אחד ולמקסימום אחד; שורות חיפוש לא נספרות; מתג כבוי נותן 0.
- באפליקציה: לפתוח את משנה ברורה כמה פעמים, להקליד `משנה` ולראות אותו עולה מעל משנה ברכות; לכבות את המתג ולראות את סדר הספרייה חוזר; לנקות היסטוריה ולראות את הסדר מתאפס.

**היקף ותלות:** S. ‏3 קבצים חדשים (כ-110 שורות), 2 קובצי בדיקה (כ-90 שורות), וכ-10 שורות ב-5 קבצים קיימים. עצמאי. אם פריט 9 נכנס, הניחושים שלו משתמשים בשקלול אחרי מפתחות האיכות שלהם.

#### 6. ספרים אחרונים בשורה ריקה

**בקצרה:** לחיצה בשורת החיפוש הראשית כשהיא ריקה, או ↓, פותחת את חלון ההצעות עם עד שמונה ספרים שנפתחו לאחרונה, מהחדש לישן, כל ספר פעם אחת.

**מה רואים:**
- לוחצים בשדה הריק (בדף הבית או בדף התוצאות אחרי שמחקו את הטקסט): נפתח החלון עם כותרת אפורה "נפתחו לאחרונה", ומתחתיה שורות כמו "משנה ברורה › סימן רסח" (סמל מקום): הספר והמקום שבו התחיל הביקור האחרון. Enter או לחיצה פותחים אותו שם, בלשונית הנוכחית. ספר שהביקור שלו לא נרשם עם מקום מופיע כשורת ספר רגילה (סמל ספר, הנתיב בקטלוג › השם): Enter פותח אותו בתחילתו, ו-Tab הוא "חיפוש בתוך", כמו בכל שורת ספר.
- ↓ בשדה ריק פותח אותו דבר.
- *מחרוזת חדשה:* `search_recent_books` = "נפתחו לאחרונה", באותו בלוק של פריט 4.
- הקלדת אות מחליפה אותן בהצעות הרגילות; מחיקה חזרה לשדה ריק מחזירה אותן, כל עוד החלון לא נסגר.
- לא מופיע: בפוקוס האוטומטי של לשונית חדשה (החלטה 1); כשנבחר ספר, מחבר או קטגוריה (יש צ'יפ); כשההיסטוריה ריקה או נוקתה; בשדה תוכן העניינים של ספר שנבחר, שמציג כבר את התוכן.
- היום: כאמור, שום דבר בשורה עצמה (ראו "ההיסטוריה" למעלה).

**איך זה עובד:**
1. **הנתונים:** `‹desk›/features/search/RecentBooks.kt` (חדש): `internal suspend fun recentBooks(history, resolve, limit)`. היא קוראת `history.query("", 60)`, לוקחת שורות `BOOK` עם `bookId`, את הראשונה לכל ספר (השורות ממוינות מהחדשה), ופותרת כל מזהה: ספר מהספרייה דרך `LibraryCategories.books()` ו-`bookPlace(id)` (‏`LibraryCategories.kt` 140 ו-156: מהקטלוג שבזיכרון, בלי שאילתה למסד); ספר PDF אישי (מזהה מעל 2,100,000,000, ‏`‹desk›/features/pdf/MyPdfBooks.kt` 35–42) דרך `PdfBookStore.asBook(id)` (‏`PdfBookStore.kt` 224); מזהה שאין לו ספר (ספרייה שהתעדכנה) מדולג. התוצאה: `RecentBook(book, path, lineId, place)`, כש-`place` הוא כותרת ההיסטוריה אחרי "<שם הספר> - ".
2. **ה-composable:** `recentBookSuggestions()` באותו קובץ, דרך `LocalAppGraph.current.historyStore`, `revision` ו-`produceState`, כמו `LibraryWidgets.kt` 100–103 ו-`‹desk›/features/pdf/MyPdfBooksSearch.kt` 33–47 (התקדים: גם ספרי ה-PDF מוזרקים כך לשורה, בשורה 609).
3. **הוו ב-`HomeView.kt`:** `UnifiedSearchBar` מעביר `recentBooks = recentBookSuggestions()` ל-`SearchBar`. ב-`SearchBar`: `var recentsAsked`, שנדלק בלחיצה בשדה (בלוק ה-`pointerInput`, שורות 1875–1880) וב-↓ כשהחלון סגור והשדה ריק (ענף חדש לפני שורה 1819), ונכבה ב-Escape, בסגירה ובהקלדה; `val showRecents = recentsAsked && query.isEmpty() && !isTocMode && selectedAuthor == null && scopeChips.isEmpty() && recentBooks.isNotEmpty()`; כש-`showRecents`, השורות נבנות מהספרים האחרונים (`CatBookRow.Jump` עם `ResolvedReference(book, lineId, place)` כשיש מקום, `CatBookRow.Book` כשאין) במקום `catBookRows(...)` (‏1514–1525), כך ש-`catBookRows` עצמו לא משתנה; `showRecents` נוסף למפתחות ה-`LaunchedEffect` (‏1561–1577), ל-`shouldOpen` (‏1578–1587), ל-`showOverlay` (‏1969–1981) ולתנאי של `SuggestionsPanel` (‏2031), עם `header = stringResource(Res.string.search_recent_books)`.
4. **הפתיחה:** שורת מקום עוברת דרך `onPickJump` הקיים ל-`SearchHomeViewModel.openJump` (שורה 934), שפותח ב-`lineId`; שורת ספר דרך `onOpenBook` ל-`openBook` (‏911). בית המודל לא משתנה, וגם לא ה-SQL של זית.

*מה נלקח מ-IntelliJ:* הרעיון: Search Everywhere מציג קבצים אחרונים כשהשאילתה ריקה (`‹ij›/platform/searchEverywhere/backend/src/providers/recentFiles/SeRecentFilesProvider.kt`, נקרא ב-`git show`, ‏`EditorHistoryManager` בשורה 61). שום קוד לא מועתק.

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `‹desk›/features/search/RecentBooks.kt` | חדש | פרדס | `RecentBook`, ‏`recentBooks`, ‏`recentBookSuggestions`; כ-70 שורות | (ב) |
| `‹desk›/features/bookcontent/ui/panels/bookcontent/views/HomeView.kt` | קיים | זית ששונה | `UnifiedSearchBar` (‏643–725): שורה אחת. `SearchBar`: פרמטר, מצב, ענף ↓, הלחיצה, השורות, `shouldOpen`, `showOverlay`, ההצגה ב-`SuggestionsPanel`; כ-20 שורות. `header` של `SuggestionsPanel` אם פריט 4 עוד לא הוסיף אותו | (ג) |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | קיים | זית ששונה | `search_recent_books` | (ג), שורת משאב |
| `‹test›/features/search/RecentBooksTest.kt` | חדש | פרדס | ראו "בדיקות" | (ב) |

רשומות במפת השינויים: חדשות בסעיף 2 ל-`RecentBooks.kt` (Feature: "Recent books in the empty bar", תכונה חדשה עם פסקת פתיחה וההוקים שלה; Reached from: `HomeView.kt`; Follows: `MyPdfBooksSearch.kt` ו-`HistoryWidget`) ול-`RecentBooksTest.kt`; שכתוב בסעיף 1 של `HomeView.kt` (What, ספירת השורות, Why) ושל `strings.xml`.

**החלטות פתוחות:**
1. **מה פותח:** לחיצה בשדה הריק או ↓ בלבד (ברירת מחדל: לשונית חדשה לא נפתחת עם חלון מעל הווידג'טים) / כל פוקוס, כולל האוטומטי של לשונית חדשה (פחות קוד: בלי `recentsAsked`).
2. **איפה נפתח הספר:** במקום שבו התחיל הביקור האחרון, כמו בדף ההיסטוריה (`‹desk›/core/history/VisitEntryDestination.kt`; ברירת מחדל) / בתחילת הספר, כמו כל שורת ספר.
3. **כמה:** 8 (ברירת מחדל) / 5 / 10.
4. **ספרי PDF:** כלולים (ברירת מחדל) / רק ספרי הספרייה.
5. **מקום ליד השם:** "משנה ברורה › סימן רסח" (ברירת מחדל) / רק השם והנתיב בקטלוג.
6. **גם חיפושים אחרונים:** לא (ברירת מחדל; חלון Ctrl+Shift+A מציג אותם) / כן, כשורות חיפוש.
7. **אנדרואיד:** אחר כך (ברירת מחדל) / יחד.

**סיכונים ולא-ידועים:**
- מי שיושב ליד המחשב רואה את הספרים האחרונים; דף ההיסטוריה ווידג'ט ההיסטוריה מציגים את זה כבר.
- "המקום" הוא המקום שבו הספר נפתח, לא המקום שבו הפסיקו לקרוא (ההיסטוריה רושמת את ערך התוכן שתי שניות אחרי הפתיחה).
- הלוגיקה של פתיחת החלון ב-`SearchBar` עדינה (`openRequests`, ‏`handledOpenRequests`, ‏`LocalTabSelected` בשורה 1465, ‏`dismissOnClickOutside`). צריך לבדוק בפועל שהחלון לא נפתח בלשונית שלא נבחרה ולא נשאר פתוח אחרי Escape **(לא נבדק)**.
- ספר שנמחק מהספרייה או קובץ PDF שהוסר: מדולג, ולכן הרשימה יכולה להיות קצרה משמונה.

**בדיקות:**
- `‹test›/features/search/RecentBooksTest.kt`, על `UserSettingsDb` בזיכרון כמו `HistoryStoreTest`: ביקורים בספר א (זמן 3, פרק 1), ב (זמן 2), א (זמן 1, פרק 2) וחיפוש (זמן 4) נותנים [א, ב], כשלא' המקום של זמן 3; מזהה שלא נפתר מדולג; `limit` נשמר; היסטוריה ריקה נותנת רשימה ריקה.
- באפליקציה: ללחוץ בשורה הריקה ולראות את "נפתחו לאחרונה"; ↓ פותח; הקלדה מחליפה; Escape סוגר; לשונית חדשה לא נפתחת עם חלון; ניקוי היסטוריה מרוקן; Enter על שורת מקום פותח בפרק. צילום מסך בעזרת `pardes-desktop-run`.

**היקף ותלות:** S עד M. קובץ חדש (כ-70 שורות) ובדיקה (כ-60), כ-20 שורות ב-`HomeView.kt`, מחרוזת אחת. תלוי בפריט 4 רק בפרמטר `header` המשותף. נשען על פריט 5 רק אם נבחר מתג אחד לשניהם.

#### 7. הדגשת ההתאמה בהצעות

**בקצרה:** בכל הצעת ספר בשורת החיפוש מודגשות בשם הספר האותיות שהתאימו למה שהוקלד: מילים שלמות, תחילית המילה שעוד מוקלדת, והאותיות של ראשי התיבות (`שוע אוח` → **שו**לחן **ע**רוך, **או**רח **ח**יים).

**מה רואים:** השם מודגש בכתב עבה, כמו שהטקסט שהוקלד מודגש כבר בשורת "חיפוש במאגר".
- `משנה ברו` → "**משנה** **ברו**רה".
- `שוע אוח` → "**שו**לחן **ע**רוך, **או**רח **ח**יים".
- `משנב` (ראשי תיבות מ-`book_acronym`) → "**משנ**ה **ב**רורה".
- `כוזרי` → "ה**כוזרי**" (ה"א הידיעה לא הוקלדה ולכן לא מודגשת).
- `שלחן ערוך` (כתיב חסר) → "**שולחן** **ערוך**, אורח חיים" (כתיב אחר מדגיש את כל המילה).
- `שמירת הלשן` (טעות) → "**שמירת** **הלשון**".
- `משנה ברורה חפץ חיים` (מחבר) → רק "**משנה ברורה**".
- `רשי בראשית` → "**רש"י** על **בראשית**" (המירכאות שבתוך המילה בתוך ההדגשה).
- `תנך` (שם קטגוריה) → שום דבר מודגש בבראשית, שנמצא לפי הקטגוריה שלו.
- `akjb` (פריט 4) → לפי העברית: "**שולחן** ערוך, …".
- איפה: שורות הספרים בשורה הראשית (בית ותוצאות), כולל ספרי PDF. לא בשורות מחברים, קטגוריות ותוכן עניינים (החלטה 2). אין מחרוזות חדשות.
- היום: שורות הספרים לא מודגשות בכלל. רק שורת "חיפוש במאגר" מדגישה את מה שהוקלד (`SuggestionsPanel` שורות 940–957, ‏`emphasis = textSearchQuery` בשורה 945), דרך `withBold` (‏1260–1267), שמחפש את הטקסט כמו שהוא (`indexOf`) ומוצא רק התאמה מילולית.

**איך זה עובד:** שתי דרכים להוציא טווח:
- **(א) בתוך `BookTitleIndex`:** `BookMatch.titleRanges` מחושב ב-`matched()` ועובר דרך `ScoredBookHit` (‏`LuceneLookupSearchService.kt` 100–123), ‏`BookSuggestionDto` (‏`SearchHomeViewModel.kt` 99–105) ו-`BookSuggestion` (‏`HomeView.kt` 140–144): שלוש מחלקות נתונים של זית ושלוש המרות, ובלי טווח לספרים שבאו מאינדקס ה-Lucene.
- **(ב) מומלץ:** פונקציה טהורה `TitleHighlight.ranges(title, query)` בתיקיית `titles`, שהשורה קוראת לה כשהיא מצוירת, עם אותם כללי מילים של החיפוש: `wordMatches`, `typoMatch` ו-`vowelLetterApart` של `BookTitleIndex` (‏280–290, ‏393–406, ‏417–424, פונקציות טהורות) עוברים ל-companion כ-`internal`, כך שיש כלל אחד ולא שניים. אין שינוי במחלקות הנתונים.

האלגוריתם של (ב):
1. **מיקומים בשם:** `TitleText.spans(title)` (חדש, ליד `rawWords`): לכל מילה האותיות הנקיות שלה (אותו פיצול ואותו ניקוי של `words` ו-`clean`), ולכל אות המיקום שלה בשם המקורי.
2. **מילות השאילתה:** `TitleText.queryTokens(query)`.
3. **לכל מילה, קודם בלי טעויות:** לכל קריאה שלה, שיבוץ של מילותיה במילים שונות של השם (אותו backtracking של `assignable`, ‏247–267) לפי `wordMatches(word, typed, prefix = מילה אחרונה, fuzzy = false)`. מה מודגש: מילה זהה או זהה עם ה"א הידיעה, כל המילה (בלי ה"א כשלא הוקלדה); תחילית, האותיות שהוקלדו; כתיב אחר, כל המילה.
4. **ראשי תיבות:** מילה שנקראה כקריאה מורחבת (`TitleAbbreviations`), או מילה בודדת שלא שובצה כמילה (`book_acronym`), מיושרת כרצף תחיליות של מילים בשם בעזרת `prefixFragments` של פריט 9: `שוע` נותן [שו][ע], ו-`משנב` נותן [משנ][ב]. כשהיישור נכשל והקריאה המורחבת שובצה, מודגשת האות הראשונה של כל מילה שלה.
5. **רק אחר כך טעויות:** מה שלא הודגש נבדק עם `fuzzy = true`, ומודגשת כל המילה. הסדר חשוב: `משנב` הוא גם טעות אחת מ"משנה", ובלי היישור קודם רק "משנה" כולה הייתה מודגשת.
6. **מאותיות לטווחים:** רצף אותיות במילה אחת הופך לטווח מהמיקום של הראשונה עד המיקום של האחרונה, כך ש-`רש"י` הוא טווח אחד; טווחים צמודים מתמזגים.
7. **הציור:** `SuggestionRow` (‏1292) מקבל `highlights: List<IntRange> = emptyList()`, שחל על החלק האחרון (השם) בלולאה שבשורות 1362–1379 כ-`SpanStyle(fontWeight = FontWeight.Bold)`, דרך פונקציה פרטית `withRanges` ליד `withBold`. שורת הספר ב-`SuggestionsPanel` (‏974–982) מעבירה `remember(title, query) { TitleHighlight.ranges(title, query) }`, כש-`query` הוא `textSearchQuery` (העברית של פריט 4 כשיש). החלק האחרון של `dedupAdjacent(path)` הוא תמיד שם הספר, כי ה-path הוא נתיב הקטגוריה ואחריו השם (`SearchHomeViewModel.kt` 299).

*מה נלקח מ-IntelliJ:* הרעיון שהמתאם מחזיר את הקטעים שהתאימו והרשימה מדגישה אותם: `MinusculeMatcher.match(name): List<MatchedFragment>?` (‏`‹ij›/platform/util/text-matching/src/com/intellij/psi/codeStyle/MinusculeMatcher.kt`; ‏`MatchedFragment.kt` באותו מודול, טווחים חצי-פתוחים). ב-Jewel ההדגשה היא `highlightTextSearch` (‏`‹ij›/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/component/search/Highlight.kt`), שמסומנת `@ExperimentalJewelApi` וקשורה ל-`NodeSearchMatchState` של חיפוש מהיר ברשימות, ולכן לא שמישה כאן כמו שהיא. שום קוד לא מועתק.

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `‹desk›/framework/search/titles/TitleHighlight.kt` | חדש | פרדס | `TitleHighlight.ranges(title, query)`; כ-90 שורות | (ב) |
| `‹desk›/framework/search/titles/TitleText.kt` | קיים | פרדס | `spans(text)` ליד `rawWords` (‏19–32); כ-20 שורות | (ב) |
| `‹desk›/framework/search/titles/BookTitleIndex.kt` | קיים | פרדס | `wordMatches`, ‏`typoMatch`, ‏`vowelLetterApart` עוברים ל-companion כ-`internal`, בלי שינוי התנהגות | (ב) |
| `‹desk›/framework/search/titles/TitleAcronyms.kt` | חדש (מפריט 9) | פרדס | `prefixFragments`; נכנס עם הפריט הראשון מבין 7 ו-9 | (ב) |
| `‹desk›/features/bookcontent/ui/panels/bookcontent/views/HomeView.kt` | קיים | זית ששונה | `SuggestionRow`: פרמטר ושימוש בלולאה (3 שורות); שורת הספר (‏974–982): שורה; `withRanges` פרטי ליד `withBold` (‏1260): 6 שורות | (ג) |
| `‹test›/framework/search/titles/TitleHighlightTest.kt` | חדש | פרדס | ראו "בדיקות" | (ב) |

באנדרואיד אין צורך בשינוי: `BookTitleIndex.kt` לא מפנה ל-`TitleHighlight.kt`, ולכן רשימת ההעתקה לא חייבת אותו. העברה ל-`SuggestionRow` של אנדרואיד (‏`‹andr›/ui/search/UnifiedSearchBar.kt` 1483) היא רשות.

רשומות במפת השינויים: חדשות בסעיף 2 ל-`TitleHighlight.kt` (Reached from: `HomeView.kt`; Follows: `BookTitleIndex`'s word rules, IntelliJ's `MinusculeMatcher.match` as an idea) ול-`TitleHighlightTest.kt`; שכתוב של `TitleText.kt` ו-`BookTitleIndex.kt` (Size, What); בסעיף 1 שכתוב של `HomeView.kt` (What: ההדגשה בשורות הספרים; ספירת השורות).

**החלטות פתוחות:**
1. **סגנון:** כתב עבה (ברירת מחדל, כמו `withBold`) / צבע ההדגשה / רקע מעוגל כמו ב-Jewel.
2. **היקף:** רק שם הספר (ברירת מחדל) / גם בנתיב הקטגוריות ובשורות המחברים והקטגוריות.
3. **התאמה בכתיב אחר או בטעות:** כל המילה (ברירת מחדל) / בלי הדגשה.
4. **ראשי תיבות שלא מתיישרים:** האות הראשונה של כל מילה (ברירת מחדל) / כלום.
5. **אנדרואיד:** אחר כך (ברירת מחדל) / יחד.

**סיכונים ולא-ידועים:**
- הבדל בין כלל ההדגשה לכלל ההתאמה, שמוקטן ע"י `wordMatches` המשותף ובדיקות. ספר שנמצא רק לפי קטגוריה, מחבר או אינדקס ה-Lucene יוצג בלי הדגשה, וזה עלול להיראות לא עקבי.
- מחיר: רק לשורות הנראות (`LazyColumn`, עד כ-8 שורות בגובה 220dp), מיקרו-שניות לשורה **(לא נמדד)**.
- כתב עבה באמצע מילה עברית: אותיות עבריות לא מתחברות, כך שזה אמור להיראות תקין, וצריך גופן עם משקל עבה **(לא נבדק בעין)**.

**בדיקות:**
- `‹test›/framework/search/titles/TitleHighlightTest.kt` (המיקומים חושבו מהמחרוזות):
  - `ranges("שולחן ערוך, אורח חיים", "שוע אוח")` == `[0..1, 6..6, 12..13, 17..17]`.
  - `ranges("משנה ברורה", "משנה ברו")` == `[0..3, 5..7]`.
  - `ranges("הכוזרי", "כוזרי")` == `[1..5]`.
  - `ranges("רש\"י על בראשית", "רשי בראשית")` == `[0..3, 8..13]`.
  - `ranges("שולחן ערוך, אורח חיים", "שלחן")` == `[0..4]`.
  - `ranges("משנה ברורה", "משנה ברורה חפץ חיים")` == `[0..3, 5..9]`.
  - `ranges("משנה ברורה", "משנב")` == `[0..2, 5..5]`.
  - `ranges("בראשית", "תנך")` == `[]`.
- באפליקציה: להקליד `שוע אוח`, `משנה ברו`, `רשי בראשית` ולצלם את החלון בעזרת `pardes-desktop-run`.

**היקף ותלות:** S עד M. ‏2 קבצים חדשים (כ-90 שורות קוד וכ-80 שורות בדיקה), כ-25 שורות בקבצים של פרדס, כ-10 שורות ב-`HomeView.kt`. תלוי ב-`prefixFragments` של פריט 9 (או מביא אותו בעצמו), ומשתמש בעברית של פריט 4.

#### 8. מפתחות אותיות ראשונות

**בקצרה:** לכל שם של שלוש מילים ומעלה מחושבים מראש מפתחות מהאות הראשונה של כל מילה, וגם בדילוג על מילה אחת (`משנה תורה, הלכות שבת` → `מתהש`, ‏`מתש`), כגיבוי כשהשאילתה לא מצאה שום דבר. ההמלצה: לממש את זה ככלל דירוג בתוך פריט 9 ולא כמנגנון נפרד.

**מה רואים:**
- `מתהש` או `מתש` → משנה תורה, הלכות שבת, ואיתו השמות האחרים שיש להם אותו מפתח (בשמות התחליף: 12 ו-7 שמות, כולם "משנה תורה, הלכות ש…").
- `ששכ` → שמירת שבת כהלכתה, אם השם בספרייה (בשמות התחליף אין אותו).
- `שעאח` → שולחן ערוך, אורח חיים (בשמות התחליף נמצא כבר היום, במקום הראשון).
- לא: `מב`, ‏`טז`, ‏`פת` (שתי אותיות); `כהח`, ‏`ערוהש`, ‏`שוע` (יותר מהאות הראשונה, או ה"א הידיעה כאות משלה); `קצשע` (אות מאמצע מילה). שאילתה שהיא מילה אמיתית (`משה`, ‏`דבר`, ‏`שמע`) מוצאת את המילים שלה, לא אותיות ראשונות.
- השורות: כמו ספרים אחרים, אחרי שורת "חיפוש במאגר" (לא "ראשי תיבות מדויקים"). אין מחרוזות חדשות.

**איך זה עובד:**
1. **בבנייה** (ב-`init` של `BookTitleIndex`, אחרי `titleWords`, שורה 45): לכל שם של שלוש מילים ומעלה, האותיות הראשונות של כל המילים, ולכל מילה האותיות הראשונות של כל האחרות (גם הראשונה יכולה לדלג, בניגוד ל-IntelliJ, כי בעברית מילה כמו "ספר" באה ראשונה); רק מפתחות של שלוש אותיות ומעלה. התוצאה `Map<String, IntArray>`; בשמות התחליף 19,059 מפתחות ל-6,962 שמות, 12,935 מהם לשם אחד ו-395 לעשרה שמות ומעלה.
2. **בשאילתה:** מילה פשוטה אחת של שלוש אותיות ומעלה (אחרי `clean`), רק כשהשלבים הקיימים (בלי טעויות, עם טעויות, תחיליות) לא מצאו שום דבר (שורה 116), מחזירה את הספרים של המפתח: קודם בלי דילוג, אחר כך ספר יסוד, שם קצר, סדר הספרייה; עד 20.
3. *למה רק כגיבוי:* 70 מתוך 19,059 המפתחות של שמות התחליף הם גם מילים אמיתיות בשמות (`משה`, ‏`מעשה`, ‏`דבר`, ‏`דעה`, ‏`שמע`, ‏`עמק`, ‏`אהל`, ואפילו `רמבמ`). כמפתח בדרגה רגילה, `משה` היה מקדים ספר שהאותיות הראשונות שלו מ-ש-ה לספרים שהמילה "משה" בשמם.

*מה זה מוסיף על מה שיש:* מפתחות דטרמיניסטיים לכל שם של שלוש מילים ומעלה, בלי מילון ובלי `book_acronym`, שנוצר במודל שפה ומדלג על ספרים ששמם לא תואם לרשימה של ה-Acronymizer. **בשמות התחליף, מתוך 21 ראשי התיבות בני שלוש אותיות ומעלה שנבדקו,** הוא מצא את הספר המבוקש ב-4 (`שעאח`, ‏`שעיד`, ‏`מתהש`, ‏`מתש`; בניסוי, כשמפתחות של שתי אותיות הותרו, נוסף `מב`), ושניים מהם נמצאים כבר היום; החידוש היחיד: `מתהש` ו-`מתש`. בעיקר זה בגלל ש-5,353 מתוך 6,962 השמות הם פירושים ("X על Y"), שראשי התיבות שמקלידים להם מכסים רק את תחילת השם, ושראשי תיבות עבריים לוקחים לרוב יותר מאות אחת מהמילה (שו"ע, משנ"ב, ערוה"ש) או את ה"א הידיעה כאות משלה (כה"ח).

*חפיפה עם 9, והאם שניהם שווים:* כל מפתח של 8 נתפס גם במתאם של 9: אותיות ראשונות הן תחיליות באורך אחד, ו-9 מדלג על מילים בכל מקום. לכן 8 לא מוסיף ל-9 אף ספר שנמצא; הוא מוסיף אות דירוג מדויק, "התבנית מכסה את כל מילות השם, או את כולן חוץ מאחת". **ההמלצה:** לא לממש את 8 כמנגנון נפרד, אלא כמפתח הדירוג הראשון בתוך הגיבוי של 9 (כ-5 שורות). לבדו, 8 זול ומדויק אבל כמעט לא מוצא ספרים חדשים; שניהם בנפרד הם כפילות.

*מה נלקח מ-IntelliJ:* הרעיון. `AbbreviationTokenFilter` (‏`‹ij›/plugins/search-everywhere-lucene/backend/src/providers/files/analysis/AbbreviationTokenFilter.kt`, נקרא ב-`git show HEAD:`): האות הראשונה של כל חלק (שורות 61–68), וכל תת-רצף מסודר שמשמיט עד `allowedSkip` חלקים ותמיד שומר את הראשון (34–55), באורך מינימלי 2 (שורות 21, 41, 50); הוא מופעל עם `allowedSkip = 1` ב-`IndexingAnalyzers.kt` (שורה 69). בקובץ אין כותרת רישיון, והמאגר כולו ב-Apache 2.0; לא מועתק קוד.

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| **במימוש המומלץ, בתוך 9:** `‹desk›/framework/search/titles/TitleAcronyms.kt` | חדש (מפריט 9) | פרדס | מפתח הדירוג "מכסה את כל המילים או את כולן חוץ מאחת" בסדר של הניחושים; כ-5 שורות | (ב) |
| **במימוש נפרד:** `‹desk›/framework/search/titles/TitleAcronyms.kt` | חדש | פרדס | `initialsKeys(words)`; כ-20 שורות | (ב) |
| **במימוש נפרד:** `‹desk›/framework/search/titles/BookTitleIndex.kt` | קיים | פרדס | `private val initials: Map<String, IntArray>` ב-`init`, והגיבוי ב-`searchBooks` (שורה 116, `.ifEmpty { … }`); כ-20 שורות | (ב) |
| `‹test›/framework/search/titles/TitleAcronymsTest.kt`, `BookTitleIndexTest.kt` | חדש, קיים | פרדס | ראו "בדיקות" | (ב) |
| `androidApp/app/build.gradle.kts` | קיים | פרדס | `syncDesktopSearch`: `TitleAcronyms.kt`. **חובה** | (ב) |

רשומות במפת השינויים: במימוש המומלץ, אלה של פריט 9 (מה ש-`TitleAcronyms.kt` ו-`BookTitleIndex.kt` מתארים כולל את הדירוג). במימוש נפרד: `TitleAcronyms.kt` ו-`TitleAcronymsTest.kt` חדשים בסעיף 2, שכתוב של `BookTitleIndex.kt` ושל `BookTitleIndexTest.kt`, ושורת `androidApp/app/build.gradle.kts` בסעיף 3. אין קובץ של זית.

**החלטות פתוחות:**
1. **אופן:** בתוך 9 ככלל דירוג (ברירת מחדל) / מנגנון נפרד / לוותר.
2. **כלל הדילוג:** מילה אחת בכל מקום (ברירת מחדל) / כמו IntelliJ, המילה הראשונה תמיד נשמרת / בנוסף דילוג קבוע על "ספר", "הלכות", "על", "מסכת".
3. **אורך מפתח מינימלי:** 3 (ברירת מחדל) / 2.
4. **רק כגיבוי:** כן (ברירת מחדל) / גם כשיש התאמות אחרות (לא מומלץ, בגלל ההתנגשויות).
5. **ה"א הידיעה כאות משלה** (`כף החיים` → `כהח`): לא (ברירת מחדל; 9 מכסה את זה) / כן.

**סיכונים ולא-ידועים:** כיסוי נמוך (נמדד); התנגשויות עם מילים אמיתיות; מפתחות שמשותפים להרבה שמות (395 מפתחות לעשרה ומעלה); הזיכרון זניח (כ-19 אלף מפתחות). המספרים נמדדו על שמות התחליף, לא על הספרייה המלאה.

**בדיקות:**
- `‹test›/framework/search/titles/TitleAcronymsTest.kt`: `initialsKeys(["משנה","תורה","הלכות","שבת"])` == {`מתהש`, `תהש`, `מהש`, `מתש`, `מתה`}; ‏`initialsKeys(["שולחנ","ערוכ","אורח","חיימ"])` == {`שעאח`, `עאח`, `שאח`, `שעח`, `שעא`}; שם של שתי מילים נותן קבוצה ריקה.
- `BookTitleIndexTest.kt`: `searchBooks("מתש")` מוצא את משנה תורה, הלכות שבת כשאין שום התאמה אחרת; `searchBooks("משה")` עם ספר ששמו מכיל "משה" לא מחזיר שם שזה רק האותיות הראשונות שלו.

**היקף ותלות:** S. במימוש המומלץ כ-5 שורות בתוך 9 ומקרה בדיקה; במימוש נפרד כ-40 שורות וכ-40 שורות בדיקה. במימוש המומלץ תלוי בפריט 9.

#### 9. גיבוי לראשי תיבות

**בקצרה:** כשהשאילתה היא מילה אחת של שלוש אותיות ומעלה ושום דבר לא נמצא, היא נקראת כראשי תיבות שעשויים מתחיליות של מילים בשם (`כהח` → **כ**ף **הח**יים, ‏`ערוהש` → **ערו**ך **הש**ולחן), ומוצעים עד 20 ספרים, הטובים ראשונים.

**מה רואים:**
- `כהח` או `כה"ח` → כף החיים. בשמות התחליף היום אין שום תוצאה (`כהח` אינו מפתח ב-`TitleAbbreviations`, וב-`book_acronym` של "כף החיים על שולחן ערוך אורח חיים" יש `כ״ח`, `כח״י`, `ח״י`, `הח״י`, אבל לא `כה"ח`); הגיבוי מחזיר 13 שמות והספר ביניהם.
- `מתש` → משנה תורה, הלכות שבת, בין הראשונים בזכות הכלל של פריט 8, יחד עם שאר "משנה תורה, הלכות ש…" ששמם מכוסה כולו חוץ ממילה אחת (בשמות התחליף: 7), ואחריהם שמות שדולגו בהם יותר מילים (418 בסך הכול).
- `ערוהש`, ‏`קצוהח`, ‏`נתיהמ`, ‏`בעהט`, ‏`פמג`, ‏`נוב`: נמצאים כבר היום בשמות התחליף, ולכן הגיבוי לא רץ; הוא ירוץ בספרייה שאין בה את ראשי התיבות האלה.
- לא: שתי אותיות (`מב` מתאים ל-525 משמות התחליף); אות מאמצע מילה (`קצשע`); כתיב אחר; וכל שאילתה שמוצאת משהו כבר היום.
- השורות: אחרי שורת "חיפוש במאגר", לא "ראשי תיבות מדויקים". אין מחרוזות חדשות. הגיבוי פועל גם בסינון עץ הספרים בקורא ובחלונית הישויות (דרך `suggestBooks`), וגם באנדרואיד (האינדקס משותף).

**איך זה עובד:**
1. **המתאם:** `prefixFragments(letters, words): List<Fragment>?` ב-`TitleAcronyms.kt` (חדש): האותיות, לפי הסדר, כתחיליות של מילים; כל קטע מתחיל בתחילת מילה; המילים לפי הסדר, ומותר לדלג על מילים; התחילית הארוכה קודם, עם חזרה לאחור. כ-30 שורות (אב-טיפוס נכתב והורץ בניסוי).
2. **ההפעלה:** ב-`searchBooks` (שורה 116), `.ifEmpty { acronymGuesses(tokens) }`, כש-`tokens.size == 1`, ‏`tokens[0].isPlain`, והמילה בת שלוש אותיות לפחות.
3. **הסריקה והדירוג:** כל `titleWords` (כבר בזיכרון). הסדר: מכסה את כל מילות השם או את כולן חוץ מאחת (פריט 8), מתחיל במילה הראשונה, פחות מילים שדולגו, השקלול של פריט 5, ספר יסוד, פחות מילים בשם, סדר הספרייה; `take(20)`; ‏`BookMatch(tier = 0, titleHits = 0, exactAcronym = false)`.
4. **מחיר:** בשמות התחליף (6,962 שמות, JVM 21), 0.5–2 מילישניות לשאילתה אחרי חימום, ועד 13 בשאילתות הראשונות. רץ רק כשאין שום תוצאה.

*מה נלקח מ-IntelliJ:* הכלל של `MinusculeMatcher`: קטעים שמתחילים בתחילת מילה, לפי הסדר (`‹ij›/platform/util/text-matching/src/com/intellij/psi/codeStyle/MinusculeMatcherImpl.kt`: `matchWildcards` שורה 181, `matchFragment` שורה 316, תחילת המילה הבאה שורה 475), והניקוד שלו (`MinusculeMatcher.kt` שורות 155–160: ‎+1000 לתחילת מילה, ‎−1 לכל קטע, ‎−10 לכל מילה שדולגה). הרעיון בלבד, ולכן בלי הודעת רישיון. הפורט הציבורי של Jewel, `SpeedSearchMatcher.patternMatcher`, **קיים ב-0.41** (נבדק ב-`javap` על ה-jar מ-Maven Central: `SpeedSearchMatcher$Companion.patternMatcher(String, boolean, MatchingCaseSensitivity, String)`, ‏`MatchResult.Match.getRanges(): List<IntRange>`, ‏`MatchingCaseSensitivity` עם `None`, `FirstLetter`, `All`; קובצי מחלקה בגרסה 69, כלומר Java 25; הפונקציה `cached()` שמופיעה בקוד הנוכחי של Jewel לא קיימת ב-0.41; ה-API לא מסומן כניסיוני), **אבל לא מומלץ כאן:**
- באנדרואיד אין Jewel, ו-`BookTitleIndex.kt` נבנה שם כמו שהוא; ייבוא של Jewel בו שובר את אנדרואיד.
- עם `matchFromBeginning = false` התבנית מקבלת `*` בהתחלה (`convertToPattern`, ‏`‹ij›/platform/jewel/foundation/src/main/kotlin/org/jetbrains/jewel/foundation/search/SpeedSearchMatcher.kt` 312–371), והקטע הראשון יכול להתחיל באמצע מילה: `פת` מתאים ל"שפתי כהן" ב-1..2 (נבדק). בשמות התחליף זה 449 שמות ל-`פת` מול 255 בתחיליות מילים, ו-9 מול 3 ל-`ערוהש`. עם `true` ההתאמה חייבת להתחיל באות הראשונה של השם, ו-`עאח` כבר לא מתאים לשולחן ערוך, אורח חיים.
- אותיות עבריות אינן גדולות או קטנות, כך שהניקוד של "גבנונים" מצטמצם לתחילות מילים, ומה שנשאר הוא בדיוק המתאם הקטן שלמעלה.

*השוואה: היום, 8 ו-9* (שמות התחליף; נבדקו 24 ראשי תיבות, 21 מהם בני שלוש אותיות ומעלה, והשורות הן מדגם; "נמצא" = הספר המבוקש בין התוצאות; עמודת 8 לפי כלל הדילוג בכל מקום):

| ראשי תיבות | היום (מקום) | 8 (שמות במפתח, נמצא?) | 9 שלנו (שמות, נמצא?) | Jewel (שמות) |
|---|---|---|---|---|
| `כהח` כף החיים | לא נמצא | 0 | 13, כן | 13 |
| `מתש` משנה תורה, הלכות שבת | לא נמצא | 7, כן | 418, כן | 418 |
| `מתהש` | לא נמצא | 12, כן | 366, כן | 366 |
| `שעאח` שולחן ערוך, אורח חיים | 1 | 1, כן | 26, כן | 26 |
| `ערוהש` ערוך השולחן | 1 | 0 | 3, כן | 9 |
| `קצוהח` קצות החושן | 1 | 0 | 1, כן | 1 |
| `בהל` ביאור הלכה | 1 | 0 | 175, כן | 175 |
| `תויט` תוספות יום טוב | 1 | 0 | 156, כן | 156 |
| `קצשע` קיצור שולחן ערוך | לא נמצא | 0 | 1, לא | 1, לא |
| `מב` משנה ברורה (שתי אותיות, מחוץ לכלל) | 12 | 24, כן (מפתח של 2 אותיות) | 525, כן | 697 |
| `פת` פתחי תשובה (שתי אותיות, מחוץ לכלל) | 1 | 1, לא | 255, כן | 449 |
| **נמצא, מתוך 21 בני 3 אותיות ומעלה** | 14 | 4 | 17 | 17 |

מתוך הארבעה בני שלוש אותיות ומעלה שהיום לא נמצאים והשם שלהם בתחליף, 9 מוצא שלושה (`כהח`, ‏`מתהש`, ‏`מתש`), 8 שניים, ו-`קצשע` אף אחד. ארבעה לא נמצאו בשום שיטה: `קצשע`, ושלושה שהשם שלהם לא בתחליף (`ששכ`, ‏`סמע`, ‏`נצב`, שהוא מחבר). עמודות 9 ו-Jewel מוצאות את אותם ספרים; Jewel מחזיר יותר שמות כי הקטע הראשון שלו יכול להתחיל באמצע מילה.

*מה זה מוסיף על מה שיש:* ראשי תיבות שעשויים מתחיליות, לכל שם בספרייה ובלי מילון: תחילית של כמה אותיות (ערוה"ש, משנ"ב), ה"א הידיעה כאות משלה (כה"ח), ודילוג על מילים (מת"ש); `TitleAbbreviations` מכסה רק את 276 המפתחות שלו, ו-`book_acronym` רק את מה שמודל השפה הפיק לשמות שתאמו לרשימה שלו. החפיפה עם 8 מלאה בכיוון אחד: כל מה ש-8 מוצא, 9 מוצא. **שניהם שווים רק כמנגנון אחד:** 9 עם הכלל של 8 בדירוג.

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `‹desk›/framework/search/titles/TitleAcronyms.kt` | חדש | פרדס | `prefixFragments`, ‏`Fragment`, ומפתחות הדירוג; כ-40 שורות | (ב) |
| `‹desk›/framework/search/titles/BookTitleIndex.kt` | קיים | פרדס | `acronymGuesses(tokens)` (כ-25 שורות), שורה ב-`searchBooks` (‏116), ומשפט ב-KDoc (‏26–38) | (ב) |
| `‹test›/framework/search/titles/TitleAcronymsTest.kt` | חדש | פרדס | ראו "בדיקות" | (ב) |
| `‹test›/framework/search/titles/BookTitleIndexTest.kt` | קיים | פרדס | מקרי הגיבוי | (ב) |
| `‹test›/framework/search/titles/TitleSearchCases.kt` | קיים | פרדס | מקרים ב-`HELD_OUT_BOOKS` (למשל `"כה\"ח" to "כף החיים"`), שרצים רק עם ספרייה | (ב) |
| `androidApp/app/build.gradle.kts` | קיים | פרדס | `syncDesktopSearch`: `TitleAcronyms.kt`. **חובה** | (ב) |
| `androidApp/docs/PARITY.md` | קיים | פרדס | שורה בסעיף של אינדקס השמות | (ב) |

בגרסה עם Jewel (לא מומלצת): `BookTitleIndex` מקבל פרמטר `guess: ((String, String) -> List<IntRange>?)?`, ו-`AppCoreBindings.provideLuceneLookupSearchService` (‏231–247) מעביר למבדה על `SpeedSearchMatcher.patternMatcher(p, matchFromBeginning = false)` עם סינון של טווח ראשון שמתחיל בתחילת מילה; זה (ג) ב-`AppCoreBindings.kt`, ובאנדרואיד אין גיבוי.

רשומות במפת השינויים: חדשות בסעיף 2 ל-`TitleAcronyms.kt` (Feature: Book and author titles search; What: "the letters of a word read as the starts of a title's words, in order, words skipped allowed (`prefixFragments`), and the order of such guesses"; Reached from: `BookTitleIndex.kt` (`acronymGuesses`), `TitleHighlight.kt` (פריט 7); Follows: IntelliJ's `MinusculeMatcher` rule, an idea and no code) ול-`TitleAcronymsTest.kt`; שכתוב של `BookTitleIndex.kt` (Size; What: הגיבוי לראשי תיבות), `BookTitleIndexTest.kt` ו-`TitleSearchCases.kt`; בסעיף 3 שורות `androidApp/app/build.gradle.kts` ו-`androidApp/docs/PARITY.md`. אין קובץ של זית.

**החלטות פתוחות:**
1. **מתאם:** משלנו, כ-30 שורות (ברירת מחדל) / של Jewel, במחשב בלבד.
2. **אורך מינימלי:** 3 אותיות (ברירת מחדל) / 2 כשהוקלדו מירכאות.
3. **תקרה:** 20 (ברירת מחדל) / 10 / 30.
4. **מתי:** רק כשאין שום תוצאה (ברירת מחדל) / גם מתחת לתוצאות הרגילות.
5. **מילה אחת בלבד:** כן (ברירת מחדל) / גם שאילתה של כמה מילים.
6. **מקום הניחושים בשורה:** אחרי "חיפוש במאגר" (ברירת מחדל) / ניחוש יחיד שמכסה את כל השם לפני "חיפוש במאגר".

**סיכונים ולא-ידועים:**
- רוחב: 13 עד 418 שמות לתבנית של שלוש אותיות בשמות התחליף. ניחושים מטעים, ולכן תקרה, דירוג והצבה אחרי "חיפוש במאגר".
- בגלל סליחת הטעויות ושלב התחיליות, "שום דבר לא נמצא" קורה לעתים רחוקות, כך שהגיבוי ירוץ מעט. זה טוב לרעש, ומגביל את התועלת.
- לא מכסה אות מאמצע מילה (קצש"ע), כתיב אחר או סדר מילים אחר. אלה נשארים למילון ול-`book_acronym`.
- התועלת בספרייה האמיתית לא ידועה בלי ספרייה: להריץ את `TitleSearchBench` (‏`SEFORIMAPP_DATABASE_PATH` ו-`TITLE_BENCH_OUT`) לפני ואחרי.

**בדיקות:**
- `‹test›/framework/search/titles/TitleAcronymsTest.kt`: `prefixFragments("שוע", ["שולחנ","ערוכ","אורח","חיימ"])` == [(מילה 0, 2 אותיות), (1, 1)]; ‏`prefixFragments("כהח", ["כפ","החיימ"])` == [(0, 1), (1, 2)]; ‏`prefixFragments("ערוהש", ["ערוכ","השולחנ"])` == [(0, 3), (1, 2)]; ‏`prefixFragments("קצשע", ["קיצור","שולחנ","ערוכ"])` == null; ‏`prefixFragments("פת", ["שפתי","כהנ"])` == null.
- `BookTitleIndexTest.kt`, עם ספרים כף החיים, ערוך השולחן וקצות החושן בקובץ הבדיקה: `searchBooks("כהח")` וגם `searchBooks("כה\"ח")` מחזירים את כף החיים ראשון; ‏`searchBooks("מב")` לא מחזיר ניחושים; ‏`searchBooks("ערוך")` מחזיר את התוצאה הרגילה בלי ניחושים; התקרה נשמרת. הבדיקות רצות גם באנדרואיד.
- באפליקציה: להקליד `כהח` בשורה ובסינון עץ הספרים.

**היקף ותלות:** S. קובץ חדש (כ-40 שורות), כ-30 שורות ב-`BookTitleIndex.kt`, כ-80 שורות בדיקה, שורה באנדרואיד. עצמאי. מספק את `prefixFragments` לפריט 7 ואת המקום לכלל של פריט 8.

### סדר מומלץ

1. **9, עם 8 בתוכו:** רק קבצים של פרדס, ומביא את `prefixFragments` לפריט 7.
2. **5:** כמעט רק קבצים של פרדס, ושתי שורות ב-`AppCoreBindings.kt`.
3. **4:** במחשב, ואחר כך באנדרואיד.
4. **7:** אחרי 9 (היישור) ו-4 (העברית).
5. **6:** העריכה הגדולה ביותר ב-`HomeView.kt`.

לכל פריט, לפי כללי הנאמנות: `./gradlew ktlintCheck detekt` על המודול דרך `.claude/skills/pardes-desktop-run/scripts/gradle.sh`, הבדיקות של המודול, הרצה של האפליקציה לשינויי ממשק, `check_change_map.py`, ו-commit בסגנון `feat(search): …` חתום כ-efrul. לשינויי אנדרואיד: בנייה (`cd androidApp && ./gradlew :app:assembleRelease`) ובדיקה באמולטור לפי `zayit-android`; ב-`androidApp/` אין ktlint ו-detekt, ולכן שומרים ידנית על 140 עמודות. `BookTitleIndex.kt` כבר מסומן `@Suppress("TooManyFunctions")`, ואחרי כל הפריטים יהיה בו כ-535 שורות, מתחת ל-600 של `LargeClass`.

### ההחלטות שצריך לקבל

- **4:** נוסח השורה; סליחת טעויות בעברית שהומרה (מומלץ: בלי); אורך מינימלי (מומלץ: 3, או 2 עם מירכאות); גם הטבלה של 2018; האם הטקסט בשדה מוחלף; האם גם בעץ הספרים ובשדה תוכן העניינים.
- **5:** הנוסחה, קבוע הזמן והסף; המקום בסדר (מומלץ: אחרי מספר המילים שנמצאו); ברירת המחדל וניידות המתג; מתג אחד ל-5 ול-6.
- **6:** מה פותח את הרשימה (מומלץ: לחיצה או ↓ בלבד); איפה נפתח הספר (מומלץ: במקום של הביקור האחרון); כמה; ספרי PDF; חיפושים אחרונים.
- **7:** סגנון ההדגשה; רק השם או גם הנתיב והמחברים; מה מודגש בכתיב אחר ובטעות.
- **8:** האם לממש בתוך 9 בלבד (מומלץ) או בנפרד; כלל הדילוג.
- **9:** מתאם משלנו (מומלץ) או של Jewel; אורך מינימלי, תקרה ומקום הניחושים.

</div>

## The brief the agent got, in short

> Write an exact, code-verified implementation spec in Hebrew, planning only, for six book-title search features: a Latin query that finds nothing read as the Hebrew letters on the same keys (SI-1452), also on Android; a frecency tie-breaker from `HistoryStore` inside one match tier, with an off switch; recent books in the empty search bar; the matched letters highlighted in each suggestion; precomputed first-letter keys per title, skipping one word; and IntelliJ's matcher, shipped in Jewel, as a fallback for prefix acronyms. To find out in the code: `BookTitleIndex` and `TitleText`, who calls them and whose each file is, `HistoryStore`, whether suggestions highlight matches, Android's title search, Jewel's matcher in the pinned version, and the full key table.

# Spec: "did you mean" and the ranking of a half-typed word

- **For:** items 10 and 11 of the proposals the user chose from `docs/INTELLIJ_LESSONS.md` ("האם התכוונת ל…" from the index's own words, and the ranking of the word still being typed); the choice and its state are in `docs/DESKTOP_STAGE3_PLAN.md`.
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

## מפרט מימוש: "האם התכוונת ל…" (10) ודירוג מילה חלקית (11)

תכנון בלבד, 21.11.2026: שום קובץ במאגר לא שונה. כל טענה כאן נבדקה בקוד:
- פרדס ב־`21999b0`;
- הבסיס של זית `d7777e2e` (v0.6.12), ו־SeforimLibrary בנעיצה של זית, `4f07145`;
- intellij-community ב־`b2ba8bcb`, בקריאה ב־`git show` בלבד;
- מקורות Lucene 10.5.2 (התג `releases/lucene/10.5.2`), וה־POM וה־jar של `lucene-suggest` 10.5.2, שנקראו בזיכרון בלי לשמור קובץ.

מה שלא נבדק מסומן "(לא נבדק)". מספרי השורות נכונים ל־`21999b0`.

**קיצורים לנתיבים:**

| קיצור | נתיב |
|---|---|
| `app/` | `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/` |
| `appTest/` | `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/` |
| `lib/search/` | `SeforimLibrary/search/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/search/` |
| `lib/searchindex/` | `SeforimLibrary/generator/searchindex/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/searchindex/` |
| `ij/` | ב־intellij-community: `plugins/search-everywhere-lucene/backend/src/providers/files/` |
| `lucene/` | במקורות Lucene 10.5.2: `lucene/core/src/java/org/apache/lucene/` |

### רקע: איך החיפוש של פרדס קורא שאילתה

#### האינדקס ושדותיו (SeforimLibrary של זית; לא עורכים אותו לעולם)

- **אינדקס הטקסט** הוא `seforim.db.lucene`, והוא נבנה ב־`lib/searchindex/BuildLuceneIndex.kt`:
  - כל שורה בספרייה נשמרת כמסמך `type=line`.
  - השדה `text` מחזיק את השורה אחרי `normalizeForIndexDefault` (שורות 270–302 שם): בלי HTML, בלי ניקוד וטעמים, בלי ״ ו־׳, המקף הופך לרווח, והאותיות הסופיות מקופלות (ך→כ, ם→מ, ן→נ, ף→פ, ץ→צ). הוא מנותח ב־`StandardAnalyzer` (שורות 108–126; `lib/searchindex/lucene/LuceneTextIndexWriter.kt:132`).
  - לצדו נשמר `text_ng4`, אותו טקסט ברביעיות אותיות (`LuceneTextIndexWriter.kt:138`).
  - התחיליות (ו, ה, ב, כ, ל, מ, ש) לא מופרדות: "ובתפילין" היא מילה של עצמה באינדקס.
  - " ו־' של ASCII בתוך מילה נשמרים: רש"י ו־ר' הן מילים של עצמן (`docs/SEARCH_SYNTAX_REPORT.md`).
  - האינדקס מאוחד לסגמנט אחד (`LuceneTextIndexWriter.kt:186`), ועדכון מצטבר כותב לתוכו (`lib/search/LuceneSearchEngine.kt:226-227`).
  - **לכן השדה `text` הוא אוסף המילים המנורמלות כפי שהן כתובות בספרים, והוא המילון שבודק האיות צריך לקרוא.**
- **אינדקס החיפוש המקדים** הוא `seforim.db.lookup.lucene` (`lib/searchindex/lucene/LuceneLookupIndexWriter.kt`):
  - יש בו מסמכי `type=book` ו־`type=author`, ושדה מנותח אחד, `q`.
  - לספר, `q` מחזיק את השם, את השם המנורמל, את ראשי התיבות ואת הנושאים (`BuildLuceneIndex.kt:173-200`).
  - המונחים של ספר יסוד חוזרים בו 120 פעמים (`LuceneLookupIndexWriter.kt:27,57,74-76`).
  - האינדקס ממוין לפי ספר יסוד ואחר כך לפי הסדר (שורות 41–44 שם).
  - אין בו מסמכי `toc`, אף ש־`searchTocPrefix` מחפשת אותם (למטה).

#### המנוע: `LuceneSearchEngine.buildSearchContext` (`lib/search/LuceneSearchEngine.kt:641-758`)

- **מירכאות.** `SearchQueryParser.parse` (שורה 652) מפריד את הקטעים שבמירכאות: כל קטע כזה הוא ביטוי מדויק בלי הרחבה (שורה 723). מירכאה שבין שתי אותיות עבריות היא ראשי תיבות ולא מפריד (`SearchQueryParser.kt:46-49`).
- **נרמול.** הטקסט החופשי מנורמל ב־`HebrewTextUtils.normalizeHebrew` (`HebrewTextUtils.kt:32-48`), אותו נרמול כמו באינדקס. אותיות בודדות נזרקות (שורות 667–676).
- **כל מילה חובה.** המסנן בשורה 739 דורש כל מילה באחת משלוש דרכים (שורות 902–925):
  - המילה עצמה ב־`text`;
  - או, כש־near > 0 ויש בה 4 אותיות ומעלה, כל הרביעיות שלה באותה שורה (שורות 885–900 ו־912);
  - או אחת הצורות שלה מהמילון: `MagicDictionaryIndex` קורא את `pardes-lexical.db` של `PardesLexicon` (`app/framework/di/modules/AppCoreBindings.kt:220`).
- **fuzzy לא מוסיף שורות.** חיפוש fuzzy (מרחק עריכה 1, במילים של 4 אותיות ומעלה) נמצא רק בחלק שמדרג (שורות 1115–1126, שנוסף כ־SHOULD בשורה 746).
- **near.** הוא תמיד 5 (`app/features/search/SearchResultViewModel.kt:380`), ואין לו ממשק.
- **המסקנה.** מילה שגויה שאינה באינדקס, שלא כל הרביעיות שלה נמצאות בשורה אחת, ושאין לה צורה במילון, מפילה את כל החיפוש לאפס או כמעט לאפס.

#### הצד של פרדס

**המנוע.** `AppCoreBindings.provideSearchEngine` (`AppCoreBindings.kt:213-224`) בונה שרשרת:
1. `LuceneSearchEngine` עם המילון של פרדס;
2. `withSemanticSearch` (`app/framework/search/SemanticSearchEngine.kt`);
3. `LongQuerySearchEngine`;
4. `HybridSearchEngine` (דרך `installedSemanticSearch`).

הלשונית עוטפת את התוצאה ב־`CatalogOrderEngine` (`SearchResultViewModel.kt:168`).

**`HybridSearchEngine`:**
- ביטוי במירכאות, קטגוריה או שורות נבחרות עוברים לחיפוש הרגיל בלבד (`app/features/search/semantic/HybridSearchEngine.kt:116`).
- ה־facets הם תמיד של החיפוש הרגיל (שורה 209).
- כשהצד הסמנטי עונה, הרשימה ממוזגת: עד 150 מועמדים מכל צד (שורה 564).
- כשהוא לא עונה, הרשימה היא של החיפוש הרגיל (שורות 279–282).

**`executeSearch` (`SearchResultViewModel.kt:1200-1418`):**
- ה־facets והעמוד הראשון רצים יחד (שורות 1318–1333).
- כש־`facets.totalHits == 0` בספרי היסוד, החיפוש עובר לכל הספרים ומסמן `baseBooksHadNoResults` (שורות 1337–1345).
- `progressTotal` נקבע בשורות 1347–1349.
- כשאין תוצאות, הפונקציה יוצאת בשורות 1351–1355 או 1367, בלי לעדכן את `executedQuery`.

**מסך התוצאות:**
- שורת המצב היא `SearchStatusLine` (`app/features/search/SearchResultScreen.kt:1253`).
- ה־Column של כותרת הרשימה נגמר בשורה 532, והקו המפריד בשורה 533.
- "לא נמצאו תוצאות" מוצג בשורות 549–551 (`search_no_results`, `SeforimApp/src/commonMain/composeResources/values/strings.xml:881`).

**של מי כל קובץ**, לפי `docs/ZAYIT_CHANGE_MAP.md` ו־`git diff d7777e2e`:

| קובץ | של מי |
|---|---|
| `lib/search/LuceneSearchEngine.kt`, `SearchQueryParser.kt`, `HebrewTextUtils.kt`, `lib/searchindex/*` | של זית (תת־מודול בנעיצה); אסור לערוך |
| `app/features/search/SearchResultViewModel.kt` | של זית, ששונה בידי פרדס (+432 −53) |
| `app/features/search/SearchResultScreen.kt` | של זית, ששונה (+123 −33) |
| `app/features/search/SearchHomeViewModel.kt` | של זית, ששונה (+254 −15) |
| `app/framework/search/LuceneLookupSearchService.kt` | של זית, ששונה (+40 −1) |
| `app/framework/search/SemanticSearchEngine.kt` | של זית, ששונה (+6 −4) |
| `app/core/presentation/tabs/TabsContent.kt` | של זית, ששונה (+45 −2) |
| `app/framework/di/modules/AppCoreBindings.kt` | של זית, ששונה (+42 −3) |
| `strings.xml` | של זית, ששונה (+359 −1) |
| `SeforimApp/build.gradle.kts`, `gradle/libs.versions.toml` | של זית, ששונו (+29 −0, +5 −1) |
| `app/framework/search/AcronymFrequencyCache.kt`, `app/features/search/SearchTabCache.kt`, `app/features/search/domain/SearchEntityFinder.kt`, `app/features/search/semantic/SemanticSearchFeature.kt` | של זית כפי שיצא |
| `build.gradle.kts` שבשורש, `.editorconfig`, `detekt.yml` | של זית כפי שיצא |
| `HybridSearchEngine.kt`, `PardesLexicon.kt`, `LongQuerySearchEngine.kt`, `CatalogOrderSearch.kt`, `RecentFacets.kt`, `framework/search/titles/*` (`BookTitleIndex` ועוד), `SearchFeedbackButtons.kt` | של פרדס |
| `branding/brand.gradle`, `branding/pardes/assetsExtra/common/licenses/*` | של פרדס |

**גרסאות.** Lucene 10.5.2 בשני הקטלוגים (`gradle/libs.versions.toml:49`, `SeforimLibrary/gradle/libs.versions.toml:7`). `SeforimApp` תלוי ב־`lucene-core` וב־`lucene-analysis-common` (`SeforimApp/build.gradle.kts:196,198`), וכך גם מודול החיפוש של SeforimLibrary (`SeforimLibrary/search/build.gradle.kts:18-19`). אף מודול לא תלוי ב־`lucene-suggest`.

---

#### 10. "האם התכוונת ל…"

**בקצרה:** כשהמילים של חיפוש טקסט מוצאות אפס שורות או מעט מאוד, פרדס מציג מתחת לשורת המצב קישור "האם הכוונה ל: …" לשאילתה שבה כל מילה שאינה באינדקס הוחלפה באיות הקרוב לה מבין המילים של אינדקס הטקסט של הספרייה (`DirectSpellChecker` של Lucene על השדה `text`), ורק אם המנוע של הלשונית מוצא איתה יותר שורות.

**מה רואים:**

1. **בלי החבילה הסמנטית.** מקלידים `תפילון` ולוחצים Enter. שורת המצב אומרת "לא נמצא בספרי יסוד · מוצגות 0 תוצאות מכל הספרים" (המעבר האוטומטי לכל הספרים), והרשימה אומרת "לא נמצאו תוצאות".
   - מתחת לשורת המצב מופיעה שורה חדשה: "האם הכוונה ל: תפילין".
   - התווית אפורה, ו"תפילין" קישור בצבע ההדגשה, עם קו תחתי במעבר עכבר ויד כסמן, כמו "מיון לפי סדר הספרייה".
   - לחיצה עליה כאילו הוקלד בשורת החיפוש `תפילין` ונלחץ Enter: החיפוש רץ באותה לשונית ובאותו היקף, ושורת החיפוש, כותרת הלשונית וההיסטוריה מתעדכנות.
2. **כמה מילים.** `תפילון של יד` מציע `תפילין של יד`. שאר המילים נשארות כפי שהוקלדו.
3. **אות סופית.** `שולחן ערוח` מציע `שולחן ערוך`. באינדקס כתוב `ערוכ`, והאות הסופית חוזרת להצגה.
4. **מירכאות.** `"אמר רבי יוחנון"` מציע `"אמר רבי יוחנן"`. המירכאות נשארות, והביטוי המתוקן נבדק כביטוי מדויק.
5. **עם החבילה הסמנטית.** `תפילון` מציג עד כ־150 שורות שנמצאו לפי משמעות, כי חיפוש השכנים הקרובים מחזיר שורות גם לשאילתה שגויה, ושורת המצב אומרת "לא נמצא בספרי יסוד · מוצגות 150 תוצאות מכל הספרים". שורת ההצעה מופיעה בכל זאת, כי הטריגר הוא מספר השורות שהמילים מצאו (ה־facets), ולא אורך הרשימה.
6. **מעט תוצאות.** שאילתה שהמילים שלה מצאו שתי שורות, כי אחת המילים היא שגיאה נדירה שמופיעה בספרייה פעמיים. ההצעה מופיעה רק לפי הכלל של "מילה נדירה" (החלטה 1).
7. **לא מוצע:**
   - שאילתה שכל המילים שלה נמצאות באינדקס, אבל אף פעם לא יחד (אין כאן שגיאה);
   - מילה עם ״, ׳, " או ' בתוכה (ראשי תיבות וקיצורים), ספרות, אותיות לטיניות, צירוף עם מקף, או מילה של פחות משלוש אותיות;
   - שאילתה של יותר מעשר מילים;
   - איות שלא מוצא יותר שורות באותו היקף;
   - לשונית ששוחזרה מתמונת מצב, עד החיפוש הבא.

**מיקום.** בכותרת של כרטיס התוצאות, אחרי ה־Row של שורת המצב ולפני הקו המפריד (`SearchResultScreen.kt`, בין 531 ל־532). השורה נכנסת ב־`AnimatedVisibility` עם `expandVertically`, כי היא מגיעה רגע אחרי התוצאות.
- זה המקום שבו `docs/COMMON_SOURCES_PLAN.md` (§5) תכנן את שורת המקורות המשותפים, שעוד לא נבנתה. אם ייבנו שתיהן, שורת האיות באה ראשונה.
- לא כפריט ב־`LazyColumn`, מאותה סיבה שהתוכנית ההיא נותנת: הגלילה, השחזור, המקשים ופס הגלילה תלויים במספרי הפריטים.

**מחרוזות.** ב־`strings.xml`, אחרי `search_farther_lines` (שורה 904), תחת ההערה `<!-- Pardes: a spelling of the query's words that finds more lines -->`:

| מפתח | נוסח מוצע | איפה |
|---|---|---|
| `search_did_you_mean` | `האם הכוונה ל:` | התווית האפורה שלפני הקישור (נוסחים אחרים: החלטה 4) |

הקישור עצמו הוא טקסט ההצעה, ולכן אין צורך במחרוזת נוספת.

**אין חלון, לשונית או הגדרה חדשים.** חלון אפשרויות החיפוש, שנדחה ב־3.11.2026 ("not now"), נשאר סגור (`docs/SEARCH_SYNTAX_REPORT.md` › Not built).

**איך זה עובד:**

1. **הטריגר.** ב־`executeSearch`, אחרי ה־facets והמעבר האוטומטי (`SearchResultViewModel.kt:1347-1349`):
   - `facets.totalHits` הוא מספר השורות שהמילים מצאו בהיקף של הלשונית. גם עם החבילה זה החיפוש הרגיל (`HybridSearchEngine.kt:209`).
   - אם המספר קטן מהסף (0, או עד 3 לפי החלטה 1), נפתחת coroutine בת של עבודת החיפוש, והיא קוראת ל־`QuerySpelling.suggest`.
   - הספירה לבדיקה רצה באותו היקף ובאותה בחירה של ספרי יסוד או כל הספרים כמו הספירה שהפעילה את הטריגר: `baseBookOnly && !baseBooksHadNoResults`.
   - חיפוש חדש מבטל אותה יחד עם העבודה (`currentJob?.cancel()`, שורה 1215).
2. **פירוק השאילתה** (ב־`QuerySpelling`):
   - פיצול לפי רווחים, כמו ש־`SearchQueryParser` מצמצם רווחים.
   - בכל מילה מורידים מהקצוות את המירכאות (`"`, `״`) ושומרים אותן במקומן, ומורידים ניקוד וטעמים (`HebrewTextUtils.stripDiacritics`).
   - מילה מועמדת לתיקון רק אם מה שנשאר הוא אותיות עבריות בלבד (U+05D0–U+05EA), שלוש לפחות.
   - המונח שלה הוא `HebrewTextUtils.normalizeHebrew(core)`, עם הסופיות מקופלות, כלומר הצורה שבאינדקס.
3. **אילו מילים מתקנים:**
   - מילה שאינה באינדקס: `reader.docFreq(Term("text", term)) == 0`, במצב `SUGGEST_WHEN_NOT_IN_INDEX`.
   - כשנמצאו כמה שורות: גם מילה שמופיעה בשתי שורות לכל היותר, במצב `SUGGEST_MORE_POPULAR`, ורק איות שמופיע בפי 50 שורות לפחות.
   - לכל היותר שתי מילים מתוקנות. יותר מזה הוא כנראה לא שגיאת הקלדה, ולא מוצע כלום.
   - לכל היותר עשר מילים בשאילתה, כמו התקרה של IntelliJ.
4. **המועמדים.** `DirectSpellChecker` חדש לכל מילה (ה־setters שלו לא בטוחים בין threads, והאובייקט זול):

   | הגדרה | ערך | ברירת המחדל של Lucene, ולמה משנים |
   |---|---|---|
   | `minQueryLength` | 3 | ב־Lucene 4: מילים עבריות של שלוש אותיות היו מדולגות |
   | `maxEdits` | 1 עד ארבע אותיות, 2 מחמש ומעלה | ב־Lucene 2 |
   | `minPrefix` | 1 | ברירת המחדל: האות הראשונה נשמרת (החלטה 5) |
   | `accuracy` | 0.5 | ברירת המחדל. הדמיון הוא `1 − עריכות/האורך הקצר` (`lucene/search/FuzzyTermsEnum.java:256`): עריכה אחת בארבע אותיות נותנת 0.75, שתיים 0.5 |
   | `thresholdFrequency` | `1f` | איות שמופיע בשתי שורות לפחות, כדי ששגיאה חד־פעמית של הספרייה עצמה לא תוצע |

   - היפוך שתי אותיות נחשב עריכה אחת: `FuzzyTermsEnum` נבנה עם `transpositions = true`, ותחילית החובה היא `max(minPrefix, edits − 1)` (`DirectSpellChecker.java` 10.5.2, בתוך `suggestSimilar` המוגנת).
   - הקריאה היא `suggestSimilar(Term("text", term), 3, reader, mode)`: עד שלושה איותים, לפי הדמיון ואחר כך לפי מספר השורות (`SuggestWordScoreComparator`).
   - נשמרים רק איותים של אותיות עבריות בלבד.
5. **ההרכבה מחדש** (הרעיון של IntelliJ, `plugins/search-everywhere-ml/typos/src/com/intellij/searchEverywhereMl/typos/SpellerSuggestionsGenerator.kt:28-48`: כל מילה מתוקנת לבד, הכול מורכב מחדש עם המפרידים, ומילה בלי תיקון נשארת כפי שהוקלדה):
   - קודם האיות הטוב ביותר של כל מילה, ואחר כך האיות הבא של מילה אחת בכל פעם. לכל היותר שלוש מחרוזות.
   - מונח מתוקן מוצג עם אות סופית בסופו (כ→ך, מ→ם, נ→ן, פ→ף, צ→ץ), והמירכאות חוזרות לקצוות המילה.
   - המילים מחוברות ברווח אחד.
6. **הבדיקה.** כל מחרוזת, לפי הסדר, נספרת במנוע של הלשונית עצמה: `lucene.computeFacets(s, DEFAULT_NEAR, bookIds = facetsBookIds, baseBookOnly = counted)?.totalHits`.
   - המחרוזת הראשונה שמוצאת יותר שורות מ־`found`, ופי 10 לפחות כש־`found > 0`, היא ההצעה.
   - זו אותה ספירה שכל חיפוש עושה בהתחלה: עם הצורות מהמילון, הרביעיות, המירכאות והכלל של שאילתה ארוכה.
   - near הוא 5 (`DEFAULT_NEAR`), כמו בכל חיפוש. לשורת החיפוש אין תחביר של near, ולכן אין מה לשמר בהרכבה מחדש.
   - `CatalogOrderEngine` שומר את ה־facets של ארבעת החיפושים האחרונים (`app/features/search/domain/RecentFacets.kt`, `SIZE = 4`). לחיצה על ההצעה מחשבת את אותו המפתח, ולכן תמצא את הספירה מוכנה (לא נבדק בריצה).
7. **ההצגה והלחיצה:**
   - `SearchUiState.spelling` מתעדכן, ורק אם `state.query` הוא עדיין `q`. משווים ל־`state.query` ולא ל־`executedQuery`, שלא מתעדכן כשאין תוצאות (צעד 1).
   - `SpellingLine` מוצגת. לחיצה קוראת ל־`SearchShellActions.onSearchSpelling`, משם ל־`SearchResultViewModel.searchSpelling()`, ומשם ל־`searchFromBar(spelling, persistedSearchState().scope)` (שורות 1978–1993).
   - `searchFromBar` קובעת את השאילתה ואת הטיוטה, מנקה את הלשוניות שנבחרו, שומרת את ההיקף, מריצה `executeSearch`, רושמת היסטוריה ומעדכנת את הכותרת.
   - שורת החיפוש מקבלת את הטקסט החדש דרך `barText` (`SearchResultScreen.kt:349`) ודרך ה־`LaunchedEffect(initialText, inBook)` של `UnifiedSearchBar` (`app/features/bookcontent/ui/panels/bookcontent/views/HomeView.kt:584`).
8. **הקורא.** `QuerySpelling` פותח קורא משלו על `seforim.db.lucene`, כי ה־`SearcherManager` של `LuceneSearchEngine` פרטי, והקובץ ב־SeforimLibrary.
   - הוא נפתח בשימוש הראשון: `NIOFSDirectory` ב־native image, ו־`FSDirectory.open` בכל מקום אחר.
   - `SearcherManager` עם `maybeRefresh` לפני כל שימוש, כמו ש־`LuceneLookupSearchService.kt:35-59,130-139` עושה, כי עדכון מצטבר כותב לאינדקס.
   - נתיב האינדקס מחושב כמו בשורה 218 של `AppCoreBindings.kt`.
9. **כשהחיפוש הסמנטי פועל:** המנגנון אותו מנגנון, והשורה מוצגת מעל תוצאות המשמעות. שאילתה במירכאות היא ממילא החיפוש הרגיל (`HybridSearchEngine.kt:116`).
10. **מה נלקח מ־IntelliJ:** רעיון בלבד. מתקן הטעויות של Search Everywhere הוא לאנגלית בלבד (`Language.ENGLISH`, `en.aff`, ומילה מחוץ לאלפבית האנגלי נחשבת "alien": `plugins/search-everywhere-ml/typos/src/com/intellij/searchEverywhereMl/typos/SearchEverywhereSpellerImpl.kt:56-57,73-81`). מה שעובר הוא הצורה:
    - תיקון לכל מילה, והרכבה מחדש עם המפרידים;
    - דילוג על שאילתה של יותר מעשר מילים (`SpellerSuggestionsGenerator.kt:30-33`).

    לא מועתק קוד של IntelliJ. `DirectSpellChecker` הוא של Apache Lucene, במודול `lucene-suggest`.
11. **למה לא `spellcheck` של Nucleus** (`SpellChecker.suggest` מעל hunspell, NSSpellChecker או ISpellChecker, `efrul/Nucleus: spellcheck/api/spellcheck.api`):
    - זה מילון של מערכת ההפעלה לעברית מודרנית, אם הוא בכלל מותקן, והוא לא מכיר את מילות הספרייה (ארמית, צורות של חז"ל).
    - הוא לא בתלויות של פרדס (לא נבדק אם הוא כבר בגרסת Nucleus שפרדס נועץ).

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `app/framework/search/QuerySpelling.kt` | חדש, כ־170 שורות | פרדס | (1) `@Inject @SingleIn(AppScope::class) class QuerySpelling(private val databasePathProvider: DatabasePathProvider)`. (2) הקורא: `dir` ו־`searcherManager` עצלים. (3) `suspend fun suggest(query: String, found: Long, count: (String) -> Long): String?`. (4) `internal fun spellings(reader: IndexReader, query: String, found: Long): List<String>` לצעדים 2–5. (5) קבועים: `MIN_LETTERS = 3`, `MAX_WORDS = 10`, `MAX_FIXED = 2`, `CANDIDATES = 3`, `TRIES = 3`, `FEW_LINES = 3`, `RARE_LINES = 2`, `MORE_COMMON = 50`, `GAIN = 10`. (6) KDoc שמזכיר את Lucene ואת הרעיון של IntelliJ. במבנה הולך אחרי `LibraryLinks` (מחלקה של פרדס עם נתיב קריאה משלה מ־`DatabasePathProvider`) ואחרי `LuceneLookupSearchService` (הקורא) | (ב) |
| `app/features/search/SpellingLine.kt` | חדש, כ־45 | פרדס | `@Composable internal fun SpellingLine(spelling: String, onSearch: () -> Unit, modifier: Modifier = Modifier)`: Row עם `Text(stringResource(Res.string.search_did_you_mean), color = readingSecondary(), fontSize = 13.sp)` ו־`ResultLink(spelling, JewelTheme.globalColors.outlines.focused, 13.sp) { onSearch() }`. `readingSecondary` (שורה 754) ו־`ResultLink` (שורה 1050) הם `internal` | (ב) |
| `app/features/search/SearchResultViewModel.kt` | קיים | זית ששונה | (1) `SearchUiState.spelling: String? = null` אחרי `progressTotal` (שורה 137), עם הערה "Pardes:". (2) `spelling = null` ב־copy שפותח חיפוש (שורות 1240–1247). (3) פרמטר `private val querySpelling: QuerySpelling` אחרי `libraryCategories` (שורה 152). (4) בתוך `if (facets != null)` (שורות 1347–1349): `val found = facets.totalHits`, `val counted = baseBookOnly && !_uiState.value.baseBooksHadNoResults`, ו־`launch { offerSpelling(q, found) { lucene.computeFacets(it, DEFAULT_NEAR, bookIds = facetsBookIds, baseBookOnly = counted)?.totalHits ?: 0L } }`. (5) `private suspend fun offerSpelling(query, found, count)` ו־`fun searchSpelling()` ליד `searchFromBar`. בערך +20 −0 | (ג) |
| `app/features/search/SearchResultScreen.kt` | קיים | זית ששונה | (1) `val onSearchSpelling: () -> Unit = {}` ב־`SearchShellActions`, אחרי `onGrade` (שורה 137), עם הערה "Pardes:". (2) ב־`SearchResultContentMvi`, בין 531 ל־532: `AnimatedVisibility(visible = state.spelling != null, enter = expandVertically(), exit = shrinkVertically()) { state.spelling?.let { SpellingLine(it, actions.onSearchSpelling) } }`. (3) שני imports. בערך +9 | (ג) |
| `app/core/presentation/tabs/TabsContent.kt` | קיים | זית ששונה | ב־`rememberSearchShellActions` (שורות 475–530), אחרי `onGrade` (שורה 529): `onSearchSpelling = viewModel::searchSpelling,`. ‎+1 | (ג) |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | קיים | זית ששונה | אחרי שורה 904: הערה ו־`search_did_you_mean`. ‎+2 | (ג) |
| `branding/brand.gradle` | קיים | פרדס | לפני `if (brand == 'zayit') return` (שורה 22) ואחרי PDFium (שורה 20): הערה ו־`kotlin.sourceSets.jvmMain.dependencies { implementation("org.apache.lucene:lucene-suggest:${libs.versions.lucene.get()}") }`. התלות לפני ה־return, כי שני המותגים מקמפלים את `src/jvmMain` (lessons.md). אם `libs` לא נגיש בסקריפט Groovy הזה (לא נבדק), כותבים את הגרסה 10.5.2 עם הערה, כמו של PDFium. ‎+3 | (א) |
| `branding/pardes/assetsExtra/common/licenses/README.md` | קיים | פרדס | שורת Lucene: "(lucene-core, lucene-analysis-common, lucene-suggest)". ה־`META-INF/LICENSE.txt` וה־`NOTICE.txt` שב־jar זהים בייט בבייט ל־`Lucene-LICENSE.txt` ול־`Lucene-NOTICE.txt` שכבר נשלחים (נבדק ב־sha256), ולכן אין קובץ רישיון חדש | (ב) |
| `appTest/framework/search/QuerySpellingTest.kt` | חדש, כ־140 | פרדס | בדיקות היחידה (למטה) | (ב) |
| `appTest/features/search/SpellingLineUiTest.kt` | חדש, כ־40 | פרדס | בדיקת UI | (ב) |
| `appTest/framework/search/QuerySpellingE2ETest.kt` | חדש, כ־50 | פרדס | על ספרייה אמיתית; מדלגת כשאין | (ב) |
| `appTest/features/search/domain/AuthorScopeSearchTest.kt` | קיים (+7 −1) | זית ששונה | בקריאה ל־`SearchResultViewModel(` (שורה 56) מקבל את הפרמטר החדש: `QuerySpelling(DatabasePathProvider(...))`, שלא פותח כלום עד שימוש | (ג) |
| `appTest/features/search/domain/CategoryScopeSearchTest.kt`, `appTest/features/search/ScopeFlowBench.kt` | קיים | פרדס | אותו פרמטר, בקריאות ל־`SearchResultViewModel(` (שורה 68 בראשון, 195 בשני) | (ב) |
| `SeforimApp/src/graalvm/reachability-metadata.json` | קיים | זית ששונה | כנראה בלי שינוי: התכונות ש־`FuzzyTermsEnum` צריך (`BoostAttributeImpl`, `MaxNonCompetitiveBoostAttributeImpl`) כבר רשומות בשורות 145 ו־163, כי ה־FuzzyQuery של המנוע משתמש בהן. ה־SPI של `lucene-suggest` (שבעה `Completion*PostingsFormat` ו־`SuggestStopFilterFactory`) עובר דרך הטיפול של native-image ב־ServiceLoader (לא נבדק: הבנייה ב־`desktop-release.yml` תכריע) | (ג), רק אם יידרש |
| אנדרואיד | — | — | לא בצעד הזה (החלטה 7) | — |

**רשומות במפת השינויים** (המפה כתובה באנגלית, ולכן השדות כאן באנגלית):

- **סעיף 2, פיצ'ר חדש "Did you mean".** פסקת פתיחה עם רשימת הווים: `SearchResultViewModel.kt`, `SearchResultScreen.kt`, `TabsContent.kt`, `strings.xml`, `branding/brand.gradle`. ורשומות:
  - `QuerySpelling.kt`:
    - Feature: Did you mean.
    - What: as above.
    - Reached from: `SearchResultViewModel.kt` (`offerSpelling`), injected by Metro.
    - Follows: `LibraryLinks` and `LuceneLookupSearchService`'s reader; Lucene's `DirectSpellChecker`; the shape of IntelliJ's Search Everywhere speller, `SpellerSuggestionsGenerator.combinedCorrections` (an idea, no code).
    - Tests: `QuerySpellingTest`, `QuerySpellingE2ETest`.
  - `SpellingLine.kt`:
    - Reached from: `SearchResultScreen.kt` (`SearchResultContentMvi`).
    - Follows: `SearchStatusLine` and `ResultLink`.
    - Tests: `SpellingLineUiTest`.
  - שלוש רשומות לשלוש הבדיקות החדשות.
- **סעיף 1, רשומות שנכתבות מחדש:**
  - `SearchResultViewModel.kt`:
    - Change: מספרי השורות החדשים.
    - What: סעיף "Did you mean" (השדה, האיפוס, הפרמטר, הוו, שתי הפונקציות).
    - Why: הפריט נבחר מתוך `docs/INTELLIJ_LESSONS.md` (פריט 10), עם תאריך הבחירה.
    - Kind: Pardes feature in Zayit's file.
    - Merge risk: גבוה, כמו היום.
  - `SearchResultScreen.kt`: `onSearchSpelling` והשורה. Kind: Pardes feature hook.
  - `TabsContent.kt`: `rememberSearchShellActions` מוסיף את `onSearchSpelling`.
  - `strings.xml`: בלוק נוסף, `search_did_you_mean`, והספירה בשורת ה־What.
  - `AuthorScopeSearchTest.kt`: ה־ViewModel של הבדיקה מקבל `QuerySpelling`.
  - `CategoryScopeSearchTest.kt` ו־`ScopeFlowBench.kt` (סעיף 2): עדכון What ו־Size.
- **סעיף 3:**
  - השורה של `branding/brand.gradle`: "for both brands, before the zayit brand's return, also Lucene's `lucene-suggest`".
  - השורה של `licenses/README.md`.
- **סוף:** מספרי הקבצים בכותרת (`mapedit.py`), ו־`check_change_map.py` צריך לעבור.

**החלטות פתוחות:**

1. **מה זה "מעט מאוד".**
   - (א) רק כשהמילים מצאו 0 שורות.
   - (ב) גם 1 עד 3 שורות, כשאחת המילים מופיעה בשתי שורות לכל היותר ויש לה איות נפוץ פי 50, והאיות מוצא פי 10 שורות.

   **ברירת מחדל מומלצת:** (ב), עם המספרים שייקבעו אחרי המדידה (בבדיקות). אם המדידה תראה הצעות שגויות, לחזור ל־(א).
2. **ספרייה.**
   - `DirectSpellChecker` (`lucene-suggest`, תוספת של 240KB, הבחירה של הדוח).
   - לולאה של פרדס על `FuzzyTermsEnum` של `lucene-core`: מחלקה ציבורית (`lucene/search/FuzzyTermsEnum.java:44,94`), והיא מה ש־`DirectSpellChecker` עושה בפנים. כ־40 שורות יותר, בלי תלות חדשה, ועובדת גם באנדרואיד עם `lucene-android`.

   **ברירת מחדל מומלצת:** `DirectSpellChecker` במחשב. `FuzzyTermsEnum` אם אנדרואיד נכנס באותו צעד, או אם בניית ה־native image מתלוננת.
3. **איפה התלות.**
   - `branding/brand.gradle`: קובץ של פרדס, טביעת רגל (א), כמו PDFium.
   - הקטלוג ו־`SeforimApp/build.gradle.kts`: שני קבצים של זית, בסגנון שהשדה Smaller במפה מציע.

   **ברירת מחדל מומלצת:** `brand.gradle`.
4. **נוסח התווית.**
   - "האם הכוונה ל:": סתמי.
   - "האם התכוונת ל:": שם הפיצ'ר, בגוף שני יחיד, ניטרלי רק בכתיב בלי ניקוד.
   - "אולי הכוונה ל:".

   **ברירת מחדל מומלצת:** "האם הכוונה ל:".
5. **האות הראשונה.**
   - `minPrefix = 1`: ברירת המחדל של Lucene, מהיר יותר, ופחות הצעות שגויות.
   - `minPrefix = 0`: תופס ח/ת, כ/ק, ס/ש ו־א/ע בתחילת המילה, אבל רק בעריכה אחת, כי בשתי עריכות התחילית נשארת `max(minPrefix, edits − 1) = 1`. איטי יותר.

   **ברירת מחדל מומלצת:** 1, ולמדוד את 0 על אותם סטים.
6. **הלחיצה.**
   - באותה לשונית, כמו הקלדה בשורה.
   - להוסיף Ctrl/Cmd+לחיצה ללשונית חדשה.

   **ברירת מחדל מומלצת:** אותה לשונית בלבד, בלי קיצור נוסף.
7. **אנדרואיד.** יש שם עותק משלו לכל צד החיפוש:
   - `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/LuceneSearchEngine.kt`;
   - ה־ViewModel, עם המעבר האוטומטי לפי עמוד ראשון ריק (`android/ui/search/SearchViewModel.kt:525-531`);
   - המסך, עם מחרוזות כקבועים (`android/ui/search/SearchScreen.kt:169-198`; "לא נמצאו תוצאות" בשורות 538 ו־579);
   - Lucene דרך `:lucene-android` (`androidApp/app/build.gradle.kts:201`). `lucene-suggest` היה מושך את `lucene-analysis-common`, ששלוש מחלקות שלו כבר בתוך `lucene-android` (לא נבדק).

   **ברירת מחדל מומלצת:** מאוחר יותר, דרך `androidApp/docs/PARITY.md`, כש־`QuerySpelling` כתובה בלי תלות במחשב.
8. **הדגשת המילים שתוקנו.**
   - `ResultLink` רגיל, כמו בשורת המצב ובשורת המקורות המתוכננת.
   - המילים שתוקנו מודגשות: Text עם AnnotatedString, במקום `ResultLink`.

   **ברירת מחדל מומלצת:** `ResultLink` רגיל.
9. **אחרי מעבר אוטומטי לכל הספרים.**
   - לחפש את ההצעה איפה שהחיפוש הבא בלשונית ירוץ ממילא, כלומר בכל הספרים (`docs/SEARCH_ORDER_ISSUES.md` #6).
   - לחזור לספרי היסוד בשביל ההצעה.

   **ברירת מחדל מומלצת:** כמו החיפוש הבא. בעיה 6 תוכרע בנפרד.

**סיכונים ולא-ידועים:**

- **הצעה שגויה למילה נכונה ונדירה** שאינה באינדקס (שמות, מילים מודרניות): `מחשב` עלול לקבל `מחשבה`. זה קורה רק כשהמילים לא מצאו כלום, הבדיקה סופרת, וההצעה נשארת הצעה.
- **שגיאות של הספרייה עצמה** (OCR, הקלדה) כמועמדים: הדרישה לשתי שורות לפחות מצמצמת, אבל שגיאה שחוזרת פעמיים עדיין יכולה להיות מוצעת.
- **תחיליות:** תיקון יכול לשנות אות של תחילית, למשל `ובתפלון` שהופך ל־`ובתפלין` ולא ל־`ובתפילין`. הצורות שבאינדקס קובעות, והחיפוש מרחיב אחר כך לצורות בכל מקרה.
- **המילון:** מילה שהמילון מכיר והאינדקס לא מקבלת את הצורות שלה. לכן נמצא משהו, ובסף 0 לא תהיה הצעה, וזה נכון. בכלל "מילה נדירה", צורה שהמילון מכיר יכולה להיות "מתוקנת"; פי 50 מגביל את זה (לא נבדק).
- **אותיות סופיות:** האות הסופית חוזרת תמיד בסוף מילה מתוקנת. מילה שמסתיימת כחוק באות לא־סופית (קיצור בלי גרש) תוצג עם סופית. נדיר.
- **זמן (לא נבדק):**
  - `DirectSpellChecker` על `text`, מיליוני מונחים, עם אוטומט Levenshtein על מילון המונחים: עד שתי עריכות ותחילית של אות אחת.
  - ועוד עד שלוש ספירות `computeFacets`. מילה נפוצה בכל הספרים עולה כמו הספירה הראשונה של החיפוש עצמו.
  - הכול רץ אחרי שהתוצאות כבר מוצגות.
  - **יעד:** פחות מ־300ms על כל הספרייה ב־`QuerySpellingE2ETest`. אם יותר: עריכה אחת בלבד, או `MAX_FIXED = 1`.
- **קורא שני על אינדקס הטקסט:**
  - יותר קבצים פתוחים.
  - ב־Windows, קובצי הסגמנט הישנים של עדכון מצטבר נשארים עד ה־refresh, ולכן `maybeRefresh` לפני כל שימוש.
  - זיכרון: אינדקס המונחים יושב מחוץ לערימה מאז Lucene 9 (לא נבדק).
- **קפיצה בפריסה:** השורה מגיעה אחרי התוצאות ודוחפת את הרשימה שורה אחת למטה. `AnimatedVisibility` מרכך את זה.
- **native image:** צריך לבדוק את ה־SPI של `lucene-suggest`. התכונות של `FuzzyTermsEnum` כבר רשומות.
- **המותג של זית:** הוא חייב להתקמפל. התלות לפני ה־`return`, ובודקים עם `gradle.sh :SeforimApp:compileKotlinJvm -Pseforimapp.brand=zayit`.
- **מיזוג:** הווים נמצאים בשלושה קבצים של זית, ו־`SearchResultViewModel.kt` כבר מסומן בסיכון גבוה.

**בדיקות:**

- **`appTest/framework/search/QuerySpellingTest.kt`**, ליד `PardesLexiconTest.kt` ובאותו דפוס של אינדקס זמני: `IndexWriter(FSDirectory.open(tmp), IndexWriterConfig(StandardAnalyzer()))`, עם שורות מנורמלות ב־`HebrewTextUtils.normalizeHebrew` כמו באינדקס של הגרסה.
  - **השורות:** "תפילין של ראש", "תפילין של יד", "הנחת תפילין בכל יום", "אמר רבי יוחנן" (פעמיים), "שולחן ערוך אורח חיים" (פעמיים), "ברכות" (פעמיים), ו"תפילוני" (פעם אחת).
  - **המקרים, עם `count` מזויף שמחזיר 5:**
    - `` `a word the index lacks gets its spelling one letter away` ``: `תפילון` נותן `תפילין`.
    - `` `the other words stay as typed` ``: `תפילון של יד` נותן `תפילין של יד`.
    - `` `a final letter comes back at the end of a word` ``: `שולחן ערוח` נותן `שולחן ערוך`.
    - `` `quotes stay where they were` ``: `"אמר רבי יוחנון"` נותן `"אמר רבי יוחנן"`.
    - `` `acronyms, numbers, Latin and short words are left alone` ``: `רש"י`, `ה׳`, `ר'`, `5`, `abc` ו־`אב` לא נותנים כלום.
    - `` `a word the index has is not corrected` ``: `תפילין` לא נותן כלום.
    - `` `a spelling in one line only is not offered` ``: המועמד `תפילוני` מופיע בשורה אחת ולא מוצע.
    - `` `the first letter is kept` ``: `חפילין` לא נותן כלום. הבדיקה מתעדת את החלטה 5.
    - `` `more than ten words, or three misspelled words, get nothing` ``.
    - `` `nothing is offered when the spelling finds no more lines` ``: `count` שמחזיר 0.
    - `` `a rare word gets a far commoner spelling when few lines were found` ``: לכלל של החלטה 1.
- **`appTest/features/search/SpellingLineUiTest.kt`**, ליד `SearchFeedbackButtonsUiTest.kt`: `runDesktopComposeUiTest`, `IntUiTheme`, מימין לשמאל. התווית וההצעה מוצגות, ולחיצה קוראת ל־`onSearch` פעם אחת.
- **`appTest/framework/search/QuerySpellingE2ETest.kt`**, ליד `titles/TitleSearchE2ETest.kt`: `SEFORIMAPP_DATABASE_PATH`, ודילוג כשהספרייה חסרה. שגיאות אמיתיות ואיותים צפויים (`תפילון`, `שולחן ערוח`, `אמר רבי יוחנון`, `ברכוח`), והזמן מודפס מול היעד.
- **בדיקות קיימות שחייבות להישאר ירוקות:**
  - `SearchUiStateTest` ו־`SearchShellActionsTest`: שני השדות החדשים הם עם ברירת מחדל.
  - `AuthorScopeSearchTest` ו־`CategoryScopeSearchTest`.
  - `ScopeFlowBench`: שימשיך להתקמפל.
- **סגנון ובנייה:**
  - `.claude/skills/pardes-desktop-run/scripts/gradle.sh :SeforimApp:ktlintCheck :SeforimApp:detekt :SeforimApp:jvmTest`.
  - הקומפילציה של המותג של זית.
- **מדידה לפני קביעת המספרים:**
  - החיפושים בלי תוצאות במילים ב־eval-real של `pardes-semantic`.
  - החיפושים שעברו אוטומטית לכל הספרים בהיסטוריה (42 כאלה ב־`docs/SEARCH_ORDER_ISSUES.md` #6).
  - כמה מקבלים הצעה, כמה מההצעות נכונות, וכמה זמן זה לוקח.
- **באפליקציה** (מיומנות `pardes-desktop-run`, על כל הספרייה):
  1. בלי החבילה: `תפילון` נותן את השורה. בלחיצה: תוצאות, הטקסט בשורת החיפוש, כותרת הלשונית, ושורה בהיסטוריה.
  2. עם החבילה: תוצאות משמעות, והשורה מעליהן.
  3. שגיאה בתוך מירכאות.
  4. חיפוש בתוך ספר, עם מילה נכונה שאינה בספר הזה: אין שורה.
  5. שאילתה נכונה שהמילים שלה לא נפגשות: אין שורה.
  6. חיפוש חדש מהר: לא נשארת שורה ישנה.
  7. ערכת נושא כהה, ימין לשמאל, זום.
  8. בניית native image (`desktop-release.yml` עם `quick`) בגלל ה־SPI.

**היקף ותלות:**
- **היקף:** תשעה קבצים קיימים (ועוד מפת השינויים), חמישה חדשים. כ־250 שורות קוד וכ־230 שורות בדיקה. S עד M: יומיים עד ארבעה, כולל המדידה ובניית ה־native image.
- **תלות:** `lucene-suggest` 10.5.2, או בלי תלות בדרך של `FuzzyTermsEnum`. Lucene של פרדס חייב להישאר באותה גרסה כמו SeforimLibrary.
- **קשר לפריטים אחרים:**
  - לא תלוי בפריט 11.
  - חולק מקום עם שורת המקורות המשותפים, שלא נבנתה.
  - פריט 3 של הדוח (עברית שהוקלדה במקלדת אנגלית) יוכל להזין אותה שורה בעתיד, כמקור נוסף של שאילתה מתוקנת.

---

#### 11. דירוג מילה חלקית

**בקצרה:** המילה האחרונה, שעוד מוקלדת, מדורגת לפי כמה ממנה הוקלד (`boost × אורך התחילית² / אורך המילה`, `LengthScoringPrefixQuery` של IntelliJ), ומאחר שב־`LuceneLookupSearchService`, המקום שהדוח הציע, הציונים לא נקראים היום כלל, המפרט נותן שתי דרכים: (א) כפי שנבחר, עם שינוי המיון של Lucene, ו־(ב) אותו כלל כמפתח דירוג ב־`BookTitleIndex` של פרדס, שהוא מה שקובע את הרשימה שרואים בשורת החיפוש.

**מה רואים:**

**היום.** כשמקלידים בשורת החיפוש, הצעות הספרים הן:
1. קודם הספרים של `BookTitleIndex`, בסדר שלו;
2. אחריהם הספרים שרק אינדקס החיפוש המקדים מוצא (`LuceneLookupSearchService.kt:258-284`);
3. עד 120 בסך הכול (`app/features/search/SearchHomeViewModel.kt:190,293`).

**(א), גרסת Lucene.** אין ממשק או מחרוזות חדשים.
- **מה משתנה:** אילו 360 מועמדים (פי 3 מהגבול) אינדקס החיפוש המקדים לוקח לפני הדירוג שלו ב־Kotlin. זה קורה רק כשיותר מ־360 ספרים מתאימים לתחיליות, וגם `BookTitleIndex` מצא פחות מ־120.
- **דוגמה, בלי אינדקס השמות** (`zayit cli` ב־`app/main.kt:137-153`, או ספרייה בלי קטלוג): `של`.
  - היום: 360 הראשונים לפי ספר יסוד ואחר כך לפי סדר.
  - אחרי השינוי: קודם הספרים שהמילה בהם הכי קרובה ל־"של". מילה "של" בדיוק מקבלת 4, "שלום" מקבלת 2, ומילה בעמדה 1 בשם מוכפלת ב־0.75.
  - הסדר הסופי נשאר של זית: `titleTier`, ואחריו `calculateEnhancedScore`.
- **במחשב עם קטלוג:** כנראה בלי שינוי נראה (לא נבדק; במדידה שבבדיקות).

**(ב), גרסת `BookTitleIndex`.** הרשימה בשורה משתנה.
- **מתי:** בין ספרים שווים בכל שאר המפתחות: אותה דרגה, אותה קטגוריה בשם, אותו מספר מילים שנמצאו בשם, ואותו מעמד של ספר יסוד.
- **מה:** קודם הספר שבו המילה האחרונה פותחת את המילה הקצרה ביותר בשם.
- **דוגמה, על הנתונים של בדיקת היחידה:** `ברכ`.
  - `ברכות`, ספר יסוד, נשאר ראשון כמו היום.
  - אחריו `ברכת שמואל` לפני `ברכות הראיה`: ארבע אותיות מול חמש.
  - היום מספר המילים, ואחריו סדר הספרייה, הם שמכריעים.
- אנדרואיד מקבל את השינוי כפי שהוא.

אין מחרוזות ואין הגדרות חדשות.

**איך זה עובד:**

1. **המחלקה של IntelliJ** (`ij/LengthScoringPrefixQuery.kt`, 252 שורות):
   - Query שהולכת על ה־`TermsEnum` מהתחילית (`seekCeil`, ואחר כך `startsWith`) ולוקחת את ה־postings של כל מונח.
   - היא ממזגת אותם בערימת מינימום, ב־O(k log N) למסמך.
   - מסמך מקבל את הציון של המונח הטוב שמתאים לו: `boost × p² / t`.
   - p ו־t נמדדים בבייטים של UTF-8. אות עברית היא שני בייטים, ולכן היחס לא משתנה והגורם המוחלט מוכפל ב־2.
   - **בנוסף לנוסחה של הדוח**, הציון מוכפל ב־`3/(3+position)` של המופע הראשון של המונח במסמך (`positionBoost = 3`).
   - כשאין צורך בציונים (`COMPLETE_NO_SCORES`), כל מונח שווה 1.
   - IntelliJ משתמשת בה לכל מילה, עם משקל לכל סוג אסימון: 4, 1.5, 0.6, 0.3 או 0.5 (`ij/FileIndex.kt:389-394,472-473`).
2. **גרסאות Lucene:**
   - ב־IntelliJ, `lucene.common` הוא 10.5.1: `lucene-core` ו־`lucene-analysis-common` (`libraries/lucene.common/intellij.libraries.lucene.common.iml`).
   - בפרדס 10.5.2.
   - כל API שהקובץ משתמש בו קיים באותה חתימה במקורות 10.5.2:
     - `Query`: `toString`, `visit`, `equals`, `hashCode` מופשטים; `createWeight`; `classHash`.
     - `Weight(Query)`, שבה `scorerSupplier` ו־`explain` מופשטים.
     - `ScorerSupplier.get` ו־`cost`.
     - `Scorer`: `docID`, `iterator`, `getMaxScore`, ו־`Scorable.score`.
     - `DocIdSetIterator`.
     - `TermsEnum`: `seekCeil`, `term`, `next`, `postings`.
     - `Terms.getDocCount`.
     - `PostingsEnum.POSITIONS`, שהוא short.
     - `QueryVisitor.acceptField` ו־`visitLeaf`.
     - `Explanation.match` ו־`noMatch`.
   - **מסקנה:** הוא מתקמפל כמו שהוא, לפי בדיקת ה־API. הוא לא קומפל כאן: לא נבדק בקומפילציה.
   - **בסגנון הוא לא עובר:**
     - הזחה של שני רווחים;
     - `positionBoost:Int` בלי רווח;
     - `}` ו־`else` בשורות נפרדות;
     - פקודות מחוברות ב־`;`;
     - שורה של 164 תווים (שורה 118);
     - `!!` ב־`explain`.

     כל אלה נגד ההזחה של ארבעה רווחים ו־140 העמודות של `.editorconfig`, ונגד `UnsafeCallOnNullableType` של detekt. ה־`build.gradle.kts` שבשורש של זית מפעיל ktlint על כל מודול חוץ מ־jewel.
3. **מה `LuceneLookupSearchService` עושה היום** (קובץ של זית, +40 −1 של פרדס):
   - **`searchBooksPrefix` (שורות 141–166):**
     - מנרמל (`normalizeHebrew`, שורות 565–614: בלי ניקוד, מירכאות ופיסוק, עם הסופיות מקופלות) ומפצל לפי רווחים.
     - כל מילה היא `PrefixQuery` על `q`, כ־MUST (שורה 152), עם מסנן `type:book`.
     - הקריאה היא `searcher.search(query, limit, bookSort)` (שורה 153), והמיון לפי ספר יסוד, סדר ומזהה (שורות 63–68).
     - `search(Query, int, Sort)` לא מחשב ציונים: ב־`lucene/search/IndexSearcher.java` של 10.5.2 היא קוראת ל־`searchAfter(null, query, n, sort, false)`.
     - `PrefixQuery` עצמה בציון קבוע (`CONSTANT_SCORE_BLENDED_REWRITE`, `lucene/search/PrefixQuery.java:27-33`), ולכן גם היום אין בה פיצוץ סעיפים.
     - **הציון לא בשימוש בכלל.**
   - **`searchBooksWithScoring` (שורות 294–359):**
     - `searchBooksPrefix(raw, limit*3)` (שורה 317), ואחריו סינון: מילים שהוקלדו כמילים, או ראשי תיבות.
     - ציון ב־Kotlin, `calculateEnhancedScore`: שם, ראשי תיבות עם בונוס נדירות, פי 50 לראשי תיבות קצרים, ספר יסוד, מזהה.
     - סדר לפי `titleTier` ואחר כך לפי הציון (שורות 356–358).
   - **`suggestBooks` (שורות 258–284):**
     - קודם אינדקס השמות, `index.searchBooks(q, limit)`.
     - `if (named.size >= limit) return named` (שורה 280).
     - רק אחרת מתווספים הספרים של אינדקס החיפוש המקדים.
   - **`suggestAuthors` (שורות 173–187):** של אינדקס השמות כשהוא קיים. אחרת `authorsMatching` (שורות 203–224), לפי מספר הספרים.
   - **`searchTocPrefix` (שורות 226–252):** מחפשת `type:toc`, שהכותב לא כותב, ואף אחד לא קורא לה.
   - **אינדקס השמות תמיד קיים כשיש קטלוג:** `BookTitleIndexHolder.get()` מחכה לבנייה, ומחזיר null רק אם היא נכשלה (`app/framework/search/titles/BookTitleIndexLoader.kt:114-121`; `AppCoreBindings.kt:239-246`).
   - **המסקנה:** מגיעים לדרך של Lucene רק בשביל הספרים שאחרי אלה של אינדקס השמות, כשהוא מצא פחות מ־120. והסדר שלה מגיע למסך רק דרך השאלה אילו 360 מועמדים נלקחים.
4. **השינוי ב־(א):**
   - ב־`searchBooksPrefix`, המילה האחרונה היא `LengthScoringPrefixQuery`, והשאר `PrefixQuery` כמו היום.
   - המיון הופך ל־`Sort(SortField.FIELD_SCORE, *bookSort.sort)`: קודם הציון, והסדר של זית שובר שוויון.
   - בתוך שאילתה אחת p קבוע, ולכן הסדר הוא לפי `1/t × 3/(3+pos)`: המילה הקצרה ביותר שהאותיות שהוקלדו פותחות, ומוקדם יותר בשם.
   - באינדקס החיפוש המקדים העמדות ממשיכות לרוץ על כל הערכים של `q`: שם, שם מנורמל, ראשי תיבות, נושאים (`lucene/analysis/Analyzer.java:309-311`: `getPositionIncrementGap` מחזיר 0). לכן מילה בשם קודמת לאותה מילה בראשי תיבות או בנושא.
   - 120 החזרות של ספר יסוד לא משפיעות, כי השאילתה לא קוראת תדירויות.
5. **השינוי ב־(ב):**
   - הסדר של `BookTitleIndex.searchBooks` (`app/framework/search/titles/BookTitleIndex.kt:117-124`) מקבל מפתח נוסף, אחרי `isBaseBook` (שורה 122) ולפני מספר המילים בשם (שורה 123): `p²/t`.
   - p הוא אורך הצורה של המילה האחרונה שהתאימה (כפי שהוקלדה, או עם ה' הידיעה, `TitleText.articleForms`). t הוא אורך המילה בשם שהיא פותחת, הקצרה מביניהן. ערך גבוה קודם.
   - זו הנוסחה של IntelliJ בשאילתה אחת, בלי הקוד שלה.
   - `BookMatch` (שורה 96) מקבל שדה internal, שמחושב ב־`matched` (שורות 129–153).
   - משפט הדירוג ב־KDoc של המחלקה (שורות 26–36) מתעדכן.
6. **מה נלקח מ־IntelliJ:**
   - ב־(א): קוד Apache-2.0 שמועתק עם ההודעות שלו.
   - ב־(ב): הנוסחה בלבד. זה רעיון, ולא נדרשת הודעה; שורה ב־KDoc נותנת קרדיט.

**שלבי הרישוי ב־(א)** (`.claude/skills/zayit-fidelity/references/lessons.md` › An add-on of Pardes's own, and someone else's code; `docs/INTELLIJ_LESSONS.md` › רישוי):

1. מעתיקים מ־`b2ba8bcb` ושומרים את השורה הראשונה כפי שהיא: `// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.`
2. מוסיפים מתחתיה, לפי סעיף 4(b): `// Changed by Pardes on <date>: the package; formatted to this repository's ktlint and detekt (indentation, the long line wrapped, checkNotNull for !!). Copied from intellij-community at b2ba8bcb, plugins/search-everywhere-lucene/backend/src/providers/files/LengthScoringPrefixQuery.kt.`
3. **נוסח הרישיון, לפי סעיף 4(a):** `Apache-2.0.txt` כבר נשלח. לא שולחים את `LICENSE.txt` של intellij-community: זה JetBrains Open-Source Build Terms 1.3, והוא חל על הבינאריים.
4. **ה־NOTICE, לפי סעיף 4(d).** תוכן `NOTICE.txt` של intellij-community הוא "This software includes code from IntelliJ IDEA / Copyright (C) JetBrains s.r.o. / https://www.jetbrains.com/idea/". הוא נכנס כ־`IntelliJ-NOTICE.txt` לתיקיית הרישיונות, באחת משתי דרכים:
   - ישירות ב־`branding/pardes/assetsExtra/common/licenses/`;
   - או שמור במקום אחר ומועתק דרך `licenceCopies` (`branding/brand.gradle:89-95`, כמו `Limud-NOTICE.txt`). אז "חמישה קבצים" הופך ל"שישה", גם בפסקה האחרונה של `licenses/README.md` וגם ב־`branding/README.md:31-33`.
5. **רשימת הרישיונות.** שורה ב־`licenses/README.md`, למשל תחת סעיף חדש "### קוד של אחרים בתוך פרדס": "**IntelliJ IDEA Community** (intellij-community): הקובץ LengthScoringPrefixQuery.kt, ששונה בידי פרדס. Copyright 2000-2026 JetBrains s.r.o. and contributors. Apache License 2.0: [Apache-2.0.txt](Apache-2.0.txt), [IntelliJ-NOTICE.txt](IntelliJ-NOTICE.txt)."
6. **סימני מסחר, לפי סעיף 6:** אין שמות או לוגואים של JetBrains או IntelliJ במיתוג. רק הקרדיט העובדתי.
7. **המפה:**
   - רשומה בסעיף 2, עם שורת המקור בשדה Follows;
   - שורה בסעיף 3 ל־NOTICE;
   - השורה של ה־README.
8. **אנדרואיד:** רק אם מעבירים לשם. יש לו רשימת רישיונות משלו (`LicencesSettings`).

**מה משתנה בקוד:**

גרסה (א):

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `app/framework/search/LengthScoringPrefixQuery.kt` | חדש, כ־265 שורות | קוד של JetBrains (Apache-2.0), ששונה בידי פרדס | ההעתק, בחבילה `io.github.kdroidfilter.seforimapp.framework.search`, עם הכותרת והשינויים שבשלבים 1–2. ההתנהגות לא משתנה | (ב), ועוד עבודת רישוי |
| `app/framework/search/LuceneLookupSearchService.kt` | קיים (+40 −1) | זית ששונה | ב־`searchBooksPrefix`, שורות 152–153: השאילתה של המילה האחרונה, ו־`scoredBookSort`, ערך חדש ליד `bookSort` (שורות 63–68). הערה "Pardes:". בערך +5 −2 | (ג) |
| `branding/pardes/assetsExtra/common/licenses/IntelliJ-NOTICE.txt` | חדש, 3 שורות | JetBrains | עותק של `NOTICE.txt` | (ב) |
| `branding/pardes/assetsExtra/common/licenses/README.md` | קיים | פרדס | השורה משלב 5 | (ב) |
| `appTest/framework/search/LengthScoringPrefixQueryTest.kt` | חדש, כ־80 | פרדס | למטה | (ב) |
| `appTest/framework/search/LookupPrefixScoringTest.kt` | חדש, כ־70 | פרדס | למטה | (ב) |

גרסה (ב):

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `app/framework/search/titles/BookTitleIndex.kt` | קיים (477) | פרדס | שדה ב־`BookMatch`, החישוב שלו ב־`matched`, המפתח בסדר של `searchBooks`, ומשפט ב־KDoc. כ־+15 | (ב) |
| `appTest/framework/search/titles/BookTitleIndexTest.kt` | קיים (159) | פרדס | בדיקה אחת, כ־+15 | (ב) |

אנדרואיד מקמפל את שני הקבצים האלה כפי שהם (`syncDesktopSearch` ו־`syncDesktopSemanticTests`, `androidApp/app/build.gradle.kts:51-75,93-114`), ולכן אין צורך בקובץ אנדרואיד.

**רשומות במפת השינויים:**

- **(א):**
  - **סעיף 1, `LuceneLookupSearchService.kt`, מחדש:**
    - Change: מספרי השורות.
    - What: נקודה נוספת. ב־`searchBooksPrefix` המילה האחרונה מדורגת לפי כמה ממנה הוקלד, והמיון מתחיל בציון.
    - Why: הפריט נבחר מתוך `docs/INTELLIJ_LESSONS.md` (פריט 11), עם תאריך הבחירה.
    - Merge risk: בינוני. יש עכשיו גם עריכה בתוך `searchBooksPrefix`.
    - Tests: `LookupPrefixScoringTest`.
  - **סעיף 2, רשומה חדשה ל־`LengthScoringPrefixQuery.kt`:**
    - Feature: Book and author titles search.
    - Size, What.
    - Reached from: `LuceneLookupSearchService.kt` (`searchBooksPrefix`).
    - Follows: "copied from intellij-community (Apache-2.0) at `b2ba8bcb`, `plugins/search-everywhere-lucene/backend/src/providers/files/LengthScoringPrefixQuery.kt`; changed by Pardes on <date>: …".
    - Tests: `LengthScoringPrefixQueryTest`.
    - Notes: "the only file of JetBrains' code in SeforimApp; its header kept".
  - **סעיף 2:** רשומות לשתי הבדיקות.
  - **סעיף 3:**
    - שורה ל־`IntelliJ-NOTICE.txt`: "copied from intellij-community's NOTICE.txt at b2ba8bcb".
    - השורה של `licenses/README.md`.
  - מספרי הקבצים בכותרת.
- **(ב):** סעיף 2, `BookTitleIndex.kt` (Size, ומפתח הדירוג החדש ב־What) ו־`BookTitleIndexTest.kt` (Size, What). מספרי הקבצים לא משתנים.

**החלטות פתוחות:**

1. **איפה.**
   - (א) כפי שנבחר.
   - (ב) `BookTitleIndex`.
   - שתיהן.

   **ברירת מחדל מומלצת:** קודם למדוד את (א) ב־`TitleSearchBench`. אם רשימות משתנות רק לעתים רחוקות או בכלל לא: (ב), שקובעת את מה שרואים, בקובץ של פרדס, בלי עבודת רישוי, וגם באנדרואיד. (א) רק אם המדידה מראה רווח, או בשביל `zayit cli`.
2. **(א) על איזו מילה.**
   - רק האחרונה, כפי שהתבקש.
   - כל מילה, כמו ב־IntelliJ.

   **ברירת מחדל מומלצת:** רק האחרונה.
3. **(א) גורם העמדה.**
   - להשאיר את `3/(3+position)` של IntelliJ: זו ההתנהגות שלהם, והיא מעדיפה את תחילת השם, כמו `titleTier`.
   - להוריד אותו, כדי שיישאר רק `p²/t` של הדוח. זה שינוי נוסף בהעתק.

   **ברירת מחדל מומלצת:** להשאיר.
4. **(א) להכניס את הציון של Lucene לדירוג שב־Kotlin** (`calculateEnhancedScore`).

   **ברירת מחדל מומלצת:** לא. זו עריכה עמוקה של הדירוג של זית, ושוויון לא קורה שם בכלל בגלל הבונוס של המזהה (שורות 488–496).
5. **(א) הקובץ.**
   - בחבילה של פרדס, אחרי עיצוב לפי ktlint.
   - בחבילה ובסגנון של IntelliJ, עם החרגה מהבדיקות. זה דורש עריכה ב־`build.gradle.kts` שבשורש או ב־`.editorconfig`, ושניהם של זית בלי שינוי היום.

   **ברירת מחדל מומלצת:** החבילה של פרדס, אחרי עיצוב.
6. **(א) ה־NOTICE.**
   - ישירות בתיקיית הרישיונות.
   - דרך `licenceCopies`.

   **ברירת מחדל מומלצת:** ישירות. אין לו מקום אחר בקוד המקור.
7. **(ב) המקום בסדר.**
   - אחרי `isBaseBook`: ספרי יסוד נשארים ראשונים.
   - לפניו.

   **ברירת מחדל מומלצת:** אחרי.
8. **אנדרואיד.**
   - (ב) עוברת כמו שהיא.
   - (א) דורשת את `androidApp/app/src/androidMain/kotlin/io/github/kdroidfilter/seforimapp/android/search/LuceneLookupSearchService.kt` (שורות 140–166 שם), ואת רשימת הרישיונות של אנדרואיד.

   **ברירת מחדל מומלצת:** לפי החלטה 1.

**סיכונים ולא-ידועים:**

- **(א):**
  - הסיכון העיקרי: בלי שינוי נראה במחשב (לא נבדק).
  - בייטים ולא אותיות: ספרה היא בייט אחד, ולכן מונח מעורב מוטה מעט.
  - גורם העמדה מעדיף את המילה הראשונה בשם על פני ראשי תיבות.
  - `explain()` מדפיס את t באותיות, בעוד החישוב בבייטים. זה קוסמטי.
  - ה־scorer הריק שלה מתחיל ב־`NO_MORE_DOCS`, אבל אי אפשר להגיע אליו: התאמה נבדקת קודם.
  - מטמון השאילתות: המחלקה ניתנת למטמון, עם `equals` ו־`hashCode` על המונח.
  - native image: מחלקות רגילות, בלי reflection (לא נבדק).
- **(ב):**
  - הסדר בשורה משתנה בהרבה שאילתות, ו־`TitleSearchE2ETest` חייב להישאר ירוק.
  - מילה קצרה ראשונה יכולה לדחוף למטה שם ארוך שמחפשים, בין ספרים שאינם ספרי יסוד.
- **רישוי:** (א) יהיה הקובץ המילולי הראשון של צד שלישי בתוך `SeforimApp`. היום יש שם רעיונות עם קרדיט, ו־`siddur-open` כמודול נפרד. זה תקדים.
- **מיזוג:**
  - העריכה של (א) נמצאת בתוך `searchBooksPrefix` של זית.
  - העותק של אנדרואיד מתרחק מהקובץ שבמחשב.

**בדיקות:**

- **(א) `appTest/framework/search/LengthScoringPrefixQueryTest.kt`:**
  - `ByteBuffersDirectory`, עם השדה `q` במסמכים "של", "ספר של", "שלום", "שלחנות".
  - `` `a word typed whole comes first, then earlier in the name, then the shorter word` ``: הסדר של "של" הוא של, ספר של, שלום, שלחנות. הציונים בבייטים הם 4, 3, 2 ו־1.33.
  - `` `without scores it matches what PrefixQuery matches` ``.
  - `` `two thousand terms with one prefix match without a clause limit` ``: יותר מ־1,024 מונחים.
- **(א) `appTest/framework/search/LookupPrefixScoringTest.kt`:**
  - אינדקס זמני עם השדות של הכותב: `type`; `book_id` שמור ועם DV; `category_id`; `book_title`; `is_base_book` עם DV; `order_index` עם DV; `q`.
  - ספר יסוד "שלמי תודה" (סדר 1), וספר לא־יסוד "של".
  - `searchBooksPrefix("של", 1)`: היום ספר היסוד, ואחרי (א) המילה המדויקת.
- **(א) מדידה:**
  - `TitleSearchE2ETest` ו־`BookSuggestionsE2ETest` לא אמורים להשתנות.
  - `TitleSearchBench` (`SEFORIMAPP_DATABASE_PATH`, `TITLE_BENCH_OUT`): דרך Lucene לפני (א) ואחריו. כמה רשימות משתנות בעשר הראשונות, וכמה פעמים החיתוך ב־360 קורה בכלל.
- **(ב) `BookTitleIndexTest.kt`:**
  - בדיקה חדשה, `` `the last word begun ranks the shorter word it begins first` ``: הספרים ברכות (יסוד), ברכות הראיה, ברכת שמואל. עבור `ברכ` הסדר הוא ברכות, ברכת שמואל, ברכות הראיה.
  - שאר הבדיקות נשארות ירוקות, וגם העותק שאנדרואיד מקמפל מריץ אותן.
- **באפליקציה:**
  - להקליד `ברכ`, `של`, `שולחן ע` בשורת החיפוש, ולהשוות את הרשימות לפני ואחרי.
  - ל־(ב): גם באמולטור של אנדרואיד (מיומנות `zayit-android`).

**היקף ותלות:**
- **(א):** שני קובצי קוד, אחד מהם חדש (כ־265 שורות מועתקות), קובץ הודעה, ה־README, ושתי בדיקות (כ־150 שורות). S: יום או יומיים, ועוד המדידה. אין תלות חדשה, רק `lucene-core`.
- **(ב):** כ־15 שורות קוד וכ־15 שורות בדיקה. XS עד S: פחות מיום.
- שתי הגרסאות לא תלויות בפריט 10.

---

### מה הבדיקה בקוד תיקנה בדוח ובהערות המחקר

1. `LengthScoringPrefixQuery` מכפילה גם ב־`3/(3+position)`. הגורם הזה לא מופיע בנוסחה של הדוח ושל ההערות.
2. "לא להתפוצץ במספר הסעיפים": ה־`PrefixQuery` של פרדס בציון קבוע (`CONSTANT_SCORE_BLENDED_REWRITE`), ולכן גם היא לא מתפוצצת. הרווח הוא בציון, לא בסעיפים.
3. **`LuceneLookupSearchService`:**
   - כל מילה היא תחילית; אין הבחנה של "המילה האחרונה".
   - התוצאות ממוינות לפי שדות, והציון לא מחושב בכלל.
   - אינדקס השמות עונה קודם.
   - לכן "הקוד מתאים למילה האחרונה ב־`LuceneLookupSearchService`" נכון לקומפילציה, אבל בלי שינוי המיון הוא לא משנה שום דבר נראה.
4. "Could be upstream to Zayit": לא רלוונטי. פרדס נשאר על 0.6.12, הרישיון של זית השתנה, וטבלת טביעות הרגל של הדוח עצמו אומרת שהצעה לזית לא מחזירה כלום.
5. `DirectSpellChecker` כפי שהוא היה מדלג על מילים עבריות של שלוש אותיות (`minQueryLength = 4`), ושומר את האות הראשונה (`minPrefix = 1`). המפרט קובע את שניהם במפורש.
6. קובצי הרישיון של `lucene-suggest` זהים בייט בבייט לאלה של Lucene שפרדס כבר שולח, ולכן לא נדרש קובץ רישיון חדש.
7. CLAUDE.md מונה את Lucene 10.3.1 ואת Kotlin 2.2.21. בקטלוג יש 10.5.2 ו־2.4.20. הפריט הזעיר הרביעי של הדוח כבר מזכיר את התיקון.

</div>

## The brief the agent got, in short

> Write an exact, code-verified implementation spec in Hebrew, planning only, for two search features: a corrected query suggested from the terms of Pardes's own Lucene index (`DirectSpellChecker`) when a text search finds nothing or very little; and the last word still being typed scored by how much of it was typed, IntelliJ's `LengthScoringPrefixQuery`, in the lookup that serves the search bar. To find out in the code: where the full-text search runs and on which fields, how Hebrew is normalized, where "no results" shows, whether the spell checker can run on Pardes's reader, what `LuceneLookupSearchService` does, whether IntelliJ's file compiles against Pardes's Lucene, and the steps for copying someone else's code. Not to reopen the search-options window put off on 2026-11-03.

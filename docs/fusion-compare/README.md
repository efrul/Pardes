<div dir="rtl">

# השוואת שיטות מיון על חיפושי המשוב (8.11.2026)

[`fusion_compare.html`](fusion_compare.html) מציג את 10 התוצאות הראשונות של 25 שיטות מיון, זו ליד זו, לכל אחד מ־1,156 החיפושים
שבחבילות המשוב עד 8.11.2026. השיטות הן אלה של [SEARCH_FUSION_RESEARCH.md](../SEARCH_FUSION_RESEARCH.md),
[SEARCH_WORLD_RESEARCH.md](../SEARCH_WORLD_RESEARCH.md) ו־[SEARCH_OTZARIA_AND_LIBRARIES.md](../SEARCH_OTZARIA_AND_LIBRARIES.md).
המטרה היא להתרשם, לא למדוד: אף שיטה לא כוילה, ומה שהקובץ מראה לא מחליף את המדידה של pardes-semantic.

הניתוח של מה שהקובץ מראה: [ANALYSIS.md](ANALYSIS.md).

בדיקה נוספת, על חיפושי מקור (157 שאלות של ציטוט, שהחיפוש הרגיל מוצא): [SOURCE_TEST.md](SOURCE_TEST.md), והקובץ שלה
[`source_test/source_compare.html`](source_test/source_compare.html).

החיפוש הרגיל של אוצריא במקום זה של פרדס, וכתיב מלא/חסר: [OTZARIA_LEXICAL.md](OTZARIA_LEXICAL.md).

מילים נרדפות, המילון הארמי וכתיב מלא/חסר בתוך החיפוש של פרדס, וכמה משפיע הסדר של כל צד: [EXPANSIONS.md](EXPANSIONS.md).

**המימוש (9.11.2026):** ‏`p12wl` הוא עכשיו המיזוג של האפליקציה. האימות שלו, על חיפושי המקור, המשוב, eval-100 ו־eval-real:
[IMPLEMENTATION.md](IMPLEMENTATION.md). הכתיב החסר במילון של פרדס (`PardesLexicon`), והמדידה שלו: [SPELLING.md](SPELLING.md). חיפוש שנבדק לעומק, "לימוני דומים לפגעי" (ים/ין, והמיזוג): [CASE_LIMONI.md](CASE_LIMONI.md). מה הלאה: [docs/SEARCH_PLAN.md](../SEARCH_PLAN.md).

הקובץ נכנס לריפו לפי החלטת המשתמש (8.11.2026). יש בו החיפושים מחבילות המשוב (בלי מזהה השולח), וקטעים מהטקסט של השורות.

## מה הורץ

- **הספרייה:** SeforimLibrary ‏`v3-20261012193827`, הגרסה שבחבילות המשוב.
- **החיפוש הסמנטי:** החבילה `semantic-2-v3-20261012193827`, כלומר התלמיד (`student@695cbe2/pardes-clean-1`, ‏384 ממדים), המודל שבאפליקציה.
  בכל השיטות הוא אחד משני הצדדים. "רגיל בלבד" ו"סמנטי בלבד" מוצגות להשוואה.
- **החיפושים:** כל צירוף של שאילתה והיקף מחבילות המשוב: 1,156 חיפושים מתוך 915 שאילתות. כל אחד רץ דרך המנועים של האפליקציה
  (`HybridSearchEngine`, ‏`LongQuerySearchEngine`, המילון של `PardesLexicon`), בהיקף שבו נעשה:
  - ספר: כל הספר;
  - קטגוריה: כל הספרים שבה. המשוב לא שומר אם בקטגוריה חיפשו רק בספרי היסוד;
  - כל הספרים;
  - אחרת ספרי היסוד, וכל הספרים כשהחיפוש הרגיל לא מצא בהם כלום (המעבר האוטומטי).
- **המועמדים:** מכל חיפוש נשמרו העמוד של האפליקציה (50 הראשונות), ו־150 הראשונות של כל צד עם הציונים. כל שיטה ממיינת את
  אותם מועמדים (`tools/fuse_all.py`).

## השיטות

| מפתח | שיטה |
|---|---|
| `app` | האפליקציה היום: RRF ‏(k=60, סמנטי ×3), ‏`keepLeaders`, ‏`LevelPrecedence` |
| `apprrf` | אותו מיזוג, מחושב מחדש על המועמדים (כשהצד הרגיל מוחלף, OTZARIA_LEXICAL) |
| `lex`, ‏`dense` | רגיל בלבד, סמנטי בלבד (להשוואה) |
| `rrf60`, ‏`rrf60w15`, ‏`rrf20`, ‏`rrf100` | RRF בלי הכללים, במשקל 1.5, ‏k=20, ‏k=100 |
| `cc_mm50`, ‏`cc_mm30`, ‏`cc_z30`, ‏`cc_z50` | צירוף ליניארי של ציונים מנורמלים (min-max או z-score), במשקל 0.5 או 0.3 למילים |
| `dbsf`, ‏`combmnz` | DBSF של Qdrant; ‏CombMNZ |
| `ssl` | המרה של ציון BM25 לסולם הקוסינוס לפי השורות ששני הצדדים מצאו (Si & Callan) |
| `otzaria`, ‏`z_qtype` | משקל המילים לפי סוג השאילתה, כמו אוצריא (עם הנרמול שלהם, או z-score) |
| `p12` | הצעות 1 ו־2 של [SEARCH_ORDER_ISSUES.md](../SEARCH_ORDER_ISSUES.md) |
| `window` | תוספת לפי החלון הטוב בשורה (כמו מיון מחדש לפי ביטוי בוויקיפדיה) |
| `links`, ‏`level` | הכפלה לפי מספר הספרים המקושרים, או לפי רמת הספר (עם רצפה, כמו CourtListener) |
| `p12w` | הצעות 1+2 עם תוספת החלון (נוספה אחרי הניתוח) |
| `p12wl` | ‏`p12w`, ומעליה `LevelPrecedence` של האפליקציה (נוספה אחרי בדיקת המקור, SOURCE_TEST) |
| `p12wl_w2`, ‏`p12wl_w15`, ‏`p12wsl` | ‏`p12wl` עם סמנטי ×2 או ×1.5, ו־`p12wl` שבה "מילה במילה" מקבל גם כתיב אחר וקידומות (נוספו ב־EXPANSIONS; לא בקובץ, שנבנה לפניהן) |
| `diverse` | העמוד של האפליקציה, עם עד 2 שורות מכל ספר בעשר הראשונות |
| `combo` | הכול יחד, במשקלים שנקבעו ביד |

ההגדרה המדויקת של כל אחת נמצאת ב־`order_for` שב־`tools/fuse_all.py`, ובהסבר שבקובץ (מעבר עם העכבר על שם השיטה).

## הסיכום שבקובץ, ומה הוא לא אומר

**עדכון:** אחרי ש־Claude שפט את השורות החסרות (`claude_grades.tsv`), ההשוואה בלי הטיה נמצאת ב־[ANALYSIS.md](ANALYSIS.md), "אחרי השיפוט".
הטבלה כאן היא מלפני השיפוט, בלי `p12w`.

על 85 החיפושים שיש להם ציונים במשוב:

| שיטה | ראשונה טובה | טובות בשלוש הראשונות | משותפות לאפליקציה (מתוך 10) |
|---|---|---|---|
| האפליקציה היום | 41 | 89 | — |
| הצעות 1+2 | 40 | 93 | 7.6 |
| RRF ‏k=100 | 38 | 90 | 9.1 |
| אוצריא / DBSF / CombMNZ | 39 | 78–82 | 7.6–7.9 |
| צירוף ליניארי (min-max / z-score) | 30–36 | 73–82 | 7.8–8.8 |
| סמנטי בלבד | 30 | 79 | 8.6 |
| רגיל בלבד | 22 | 34 | 1.2 |

**הסיכום מוטה לטובת האפליקציה.** הציונים ניתנו על שורות שהאפליקציה הציגה, ולכן שיטה שמעלה שורות אחרות נראית גרועה יותר
ממה שהיא. שורה שלא נשפטה פשוט לא ידועה, ואין לה ציון. כדי להשוות באמת, שופטים קודם את השורות החדשות, כמו
ב־[results-dense-weight.md](https://github.com/efrul/pardes-semantic/blob/main/eval-real/results-dense-weight.md).

מה שכן ברור מהמספרים: רק 1.2 מעשר התוצאות הראשונות של החיפוש הרגיל נמצאות בממוצע גם בעשר של האפליקציה. זה המבנה של RRF
במשקל 3 (SEARCH_FUSION_RESEARCH, סעיף 1).

## להריץ שוב

```
git submodule update --init SeforimLibrary
bash docs/fusion-compare/tools/run.sh <feedback.json> <תיקיית עבודה> <checkout של pardes-semantic>
```

בדיקת המקור: `bash docs/fusion-compare/tools/run_source.sh <תיקיית עבודה> <checkout של pardes-semantic>`. אחריה, החיפוש הרגיל
של אוצריא: `bash docs/fusion-compare/tools/run_otzaria.sh <תיקיית עבודה> <checkout של pardes-semantic>`.

- `feedback.json` הוא הייצוא של שירות המשוב (‏`/admin`).
- תיקיית העבודה מקבלת את הספרייה ואת החבילה (כ־15GB, נפרסות תוך כדי הורדה), את הבנייה, את ההרצה ואת הקובץ (`out/fusion_compare.html`).
- צריך JDK 21, ‏Python 3 עם `zstandard`, ו־curl. ההרצה עצמה לוקחת כמה דקות על 4 ליבות; ההורדה היא החלק הארוך.

| קובץ | מה הוא עושה |
|---|---|
| `tools/run.sh` | הכול, לפי הסדר |
| `tools/fetch_library.py`, ‏`tools/fetch_pack.py` | מורידים את הספרייה ואת החבילה, בלי לשמור את הארכיונים |
| `tools/build.sh` | בונה את `FeedbackHistory.kt` בלי Gradle, מול הקוד של הריפו הזה (כמו `search-order/build.sh` של pardes-semantic) |
| `tools/FeedbackHistory.kt` | מריץ כל חיפוש בהיקף שלו, ושומר את העמוד ואת המועמדים |
| `tools/CategoryStub.kt`, ‏`tools/LoggerStub.kt` | במקום `Category` של SeforimLibrary core ובמקום ה־logger של האפליקציה, לבנייה בלבד |
| `tools/feedback_searches.py` | מוציא מהייצוא את החיפושים (שאילתה והיקף) ואת הציונים |
| `tools/fuse_all.py` | כל שיטות המיון (משתמש ב־`search-order/order_common.py` וב־`eval-real/level_order.py` של pardes-semantic) |
| `tools/make_html.py` | בונה את הקובץ |
| `claude_grades.tsv` | 358 ציונים של Claude לשורות שבשלוש הראשונות שלא היה להן ציון במשוב (`feedback_searches.py` מוסיף אותם) |
| `tools/analyze.py` | המספרים של [ANALYSIS.md](ANALYSIS.md): ממה בנויה כל רשימה, והמדדים לפי הציונים שבמשוב (`out/analysis.txt`) |
| `tools/run_source.sh` | בדיקת המקור של [SOURCE_TEST.md](SOURCE_TEST.md), לפי הסדר |
| `tools/source_searches.py` | מהשאלות של בדיקת המקור (`source_test/queries.tsv`) לחיפושים ולמקורות שלהם |
| `tools/source_eval.py` | המדדים של בדיקת המקור: איפה כל שיטה שמה את המקור |
| `tools/OtzariaLexical.kt`, ‏`tools/swap_lexical.py`, ‏`tools/graded_compare.py`, ‏`tools/run_otzaria.sh` | החיפוש הרגיל של אוצריא במקום זה של פרדס ([OTZARIA_LEXICAL.md](OTZARIA_LEXICAL.md)) |
| `tools/ExtraForms.kt`, ‏`tools/extra_forms_patch.py`, ‏`tools/expansion_tables.py`, ‏`tools/SpellingForms.kt`, ‏`tools/order_shuffle.py`, ‏`tools/merge_runs.py`, ‏`tools/run_expansions.sh` | צורות נוספות בחיפוש הרגיל, והשפעת הסדר ([EXPANSIONS.md](EXPANSIONS.md)) |
| `tools/evalreal_score.py`, ‏`tools/eval100_methods.py`, ‏`tools/run_tests.sh`, ‏`eval_real/grades_new.tsv` | האימות של המימוש ב־eval-real וב־eval-100, והבדיקות בלי Gradle ([IMPLEMENTATION.md](IMPLEMENTATION.md)) |

**למה לא `search-order/build.sh` של pardes-semantic כמו שהוא:** הוא לא מתקמפל מול הקוד הנוכחי של פרדס, כי `MeaningFacets.kt`
משתמש עכשיו ב־`Category` של SeforimLibrary core, שהוא לא מעתיק.

</div>

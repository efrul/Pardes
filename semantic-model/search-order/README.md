<div dir="rtl">

# הכלים של בדיקת הסדר

הכלים שמאחורי [`docs/SEARCH_ORDER_ISSUES.md`](../../docs/SEARCH_ORDER_ISSUES.md): הרצת היסטוריית החיפושים דרך המנועים של
האפליקציה, ניתוח של ממה הרשימה בנויה, מיזוגים אחרים של אותם מועמדים, החיפוש הרגיל בהגדרות אחרות, ומעקב אחרי שורה אחת.

## הקבצים

| קובץ | מה הוא עושה |
|---|---|
| [`harness/History.kt`](harness/History.kt) | מריץ כל חיפוש של `history.tsv` בהיקף שבו נעשה (ספר שנבחר, ספרי יסוד, כל הספרים, מעבר אוטומטי), כמו `SearchResultViewModel`, עם `LongQuerySearchEngine` כמו באפליקציה |
| [`harness/Bm25.kt`](harness/Bm25.kt) | הצד הרגיל שוב, בהגדרות אחרות (k1, ‏b, ההכפלה של ספרי היסוד, דרישת קרבה), לשאילתות של jsonl של `Eval.kt` |
| [`harness/Bm25Knob.kt`](harness/Bm25Knob.kt), [`harness/bm25-knob.patch`](harness/bm25-knob.patch) | הכפתורים של `Bm25.kt`, ושלושת השינויים שהם דורשים בעותק של `LuceneSearchEngine.kt` (רק בבנייה כאן) |
| [`harness/Trace.kt`](harness/Trace.kt) | איפה שורות מסוימות עומדות בחיפוש אחד: ברגיל, בסמנטי וברשימה |
| [`harness/Terms.kt`](harness/Terms.kt) | הצורות של מילה במילון, ו־32 שהחיפוש הרגיל שומר מהן |
| [`harness/Embed.kt`](harness/Embed.kt) | הדמיון של שאילתה לטקסטים, כמו שהאפליקציה מטמיעה אותם |
| [`build.sh`](build.sh) | בונה את כל אלה (וגם את [`Eval.kt`](../eval-100/harness/Eval.kt)) בלי Gradle |
| [`history.py`](history.py) | מוציא את החיפושים מ־`user_settings.db` של זית ושל פרדס ל־`history.tsv` |
| [`analyze.py`](analyze.py) | ממה בנויה הרשימה בכל היקף, ודוח של כל חיפוש (`--report`) |
| [`fusions.py`](fusions.py) | מיזוגים אחרים של המועמדים, מול `grades.tsv` של eval-real ו־`expected.py` של eval-100 |
| [`bm25score.py`](bm25score.py) | הציונים של `Bm25.kt`, לבד וממוזג, ואורך השורות של הרגיל לפי ציון |
| [`order_common.py`](order_common.py) | מה שמשותף: ה־RRF, `verbatimKey`, `holdsQuote` ו־`keepLeaders` של `HybridSearchEngine`, ושורות הספרייה |

## בנייה

```
JAVA=<JDK 21>/bin/java bash build.sh <תיקייה>
```

צריך JDK 21, ‏`patch` ו־`curl`. ה־jars נלקחים מה־cache של Gradle, ואם אינם שם, מ־Maven Central. ב־Windows (Git Bash) התיקייה
צריכה להיות בנתיב של אותיות לטיניות בלבד: Java לא קורא נכון אותיות אחרות שמגיעות מה־shell. הבנייה כותבת את ה־classpath
ל־`<תיקייה>/classpath.txt`. התוכניות מדפיסות את יומן ה־debug של SeforimLibrary ל־stdout; את התוצאות הן כותבות לקבצים
(חוץ מ־`Trace.kt`, ששם מסננים את השורות שמתחילות ב־`Debug:`).

## הרצה

הספרייה היא של `v3-20261012193827` (`seforim.db`, `seforim.db.lucene`, `lexical.db`), ולידה `pardes-model/` ו־`pardes.vec.lucene/`.
להלן `CP="$(cat <תיקייה>/classpath.txt)"`.

**ההיסטוריה** (בעיות 1, 2 ו־6 עד 10 במסמך):
1. `python history.py out zayit=<...>/user_settings.db pardes=<...>/user_settings.db` (עם `--until "YYYY-MM-DD HH:MM"`
   כדי לעצור בזמן מסוים). `visit_history` שומר לכל חיפוש רק את הפעם האחרונה שבה נעשה, ולכן רשימה משתחזרת רק מאותו עותק של
   מסד הנתונים.
2. `"$JAVA" -cp "$CP" HistoryKt seforim.db out/history.tsv out/history.jsonl > /dev/null`: כמה שניות לחיפוש.
3. `python analyze.py out/history.jsonl out/history_meta.json seforim.db --report out/order.md`.

**מיזוגים אחרים** (בעיות 1, 2, 7 ו־9):
1. `Eval.kt` על השאילתות של eval-100 ‏(`queries.tsv`) ושל eval-real (ה־`harness.tsv` של [ה־README שלו](../eval-real/README.md)):
   `"$JAVA" -cp "$CP" EvalKt seforim.db queries.tsv e100.jsonl`, וכך `real.jsonl`.
2. `python fusions.py real.jsonl e100.jsonl seforim.db`; ‏`--diff "<שם>"` מפרט את החיפושים שמשתנים;
   `--trace <jsonl של History.kt> <lineIds>` מראה את המקום של שורות בכל מיזוג.

**החיפוש הרגיל בהגדרות אחרות** (בעיה 5): הגדרה היא `k1,b,boost[,near[,keep]]`. ההגדרות של המסמך:
`1.2,0.75,1;1.2,0.75,0;0.6,0.75,1;2.0,0.75,1;1.2,0,1;1.2,0.4,1;1.2,1.0,1;0.6,0.4,1;0.6,1.0,1;2.0,0.4,1;2.0,1.0,1;0.6,0,1;2.0,0,1;1.2,0.75,1,1;1.2,1.0,1,1;1.2,0.75,1,1,1`.
1. `"$JAVA" -cp "$CP" Bm25Kt seforim.db real.jsonl "<הגדרות>" bm25_real.jsonl > /dev/null`, וכך על `e100.jsonl`.
2. `python bm25score.py real.jsonl e100.jsonl bm25_real.jsonl bm25_e100.jsonl seforim.db`.

**שורה אחת** (בעיות 2 עד 4):
- `"$JAVA" -cp "$CP" TraceKt seforim.db <lineIds> queries.txt`, שורה לכל חיפוש: `base:<שאילתה>` או `all:<שאילתה>`.
- `"$JAVA" -cp "$CP" TermsKt lexical.db words.txt out.txt`, שורה לכל מילה, ואחריה טאב וצורות לבדוק.
- `"$JAVA" -cp "$CP" EmbedKt pardes-model query.txt out.txt`: בשורה הראשונה השאילתה, ובכל שורה אחריה טקסט (ה־HTML של שורה, כמו שהוא שמור).

## מה נשאר מחוץ לריפו

ההיסטוריה של המשתמש וכל מה שנוצר ממנה (`history.tsv`, ה־jsonl, הדוח), וגם ה־jsonl של `Eval.kt`, שיש בו קטעי טקסט של
ספרים, וחלקם ספרים מודרניים שאינם נחלת הכלל.

</div>

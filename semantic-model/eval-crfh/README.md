<div dir="rtl">

# eval-crfh: שאלות בעברית על הבבלי, מבחן חיצוני

[crfh1672/search-queries](https://github.com/crfh1672/search-queries) כותב מערך מבחן לחיפוש לפי משמעות בספרי אוצריא. על
הבבלי יש בו 422 שאלות (טיוטות בשני ענפים שלא מוזגו, `add-berakhot-chagiga` ו־`readd-existing-datasets`): ברכות 125, מגילה
106, מועד קטן 105, חגיגה 51, תענית 35. 386 הן שאלות מושג ו־36 קצרות. לכל שאלה יש ציטוט נכון ו"כמעט־נכון" מאותו ספר. השאלות
בעברית של היום, והתשובות בארמית של הגמרא, ולכן זה בדיוק הגשר שחברותא והמילון אמורים לבנות (`TRAINING_PLAN.md`).

**מגבלות:** אף שאלה לא נבדקה בידי אדם, וכנראה מודל כתב אותן. גם חלק מה"כמעט־נכונים" עונים על השאלה. לכן המדד הוא מקום
הציטוט הנכון בתוך המסכת, ובהשוואה בין מודלים, לא ציון מוחלט. ולמערך **אין רישיון**: השאלות והציטוטים נשארים מחוץ לריפו,
ונקראים מהענפים בזמן המדידה. בריפו נשמרים רק המזהים שלהם, מזהי השורות שלנו והתוצאות.

**ללא דליפה:** חמש המסכתות מחוץ לחברותא, למילון ולשאלות האימון (`sample_targets.OUTSIDE_SET_TRACTATES`, ו־`build_pairs.py
--hold-outside`). זוגות הקישורים, שהמורה למד מהם מאז האימון הראשון, לא סוננו: כ־2% מהם נוגעים במסכתות האלה. לכן המספרים
טובים להשוואה בין מודלים שלמדו מאותם קישורים, ולא כמבחן נעול.

## הקבצים

- [`map_lines.py`](map_lines.py) ממפה כל ציטוט לשורה ב־`seforim.db` של `v3-20261012193827`: הדף שלו, ואז המילים שלו כרצף
  בשורה. כל 844 הציטוטים נמצאו בדף שלהם: 818 בשלמותם, 26 לפי תחילתם (ציטוט שעובר לשורה הבאה).
- [`lines.tsv`](lines.tsv): `id`, `tractate`, `type`, `correct_line`, `near_miss_line`, ואיך כל אחד נמצא.
- [`spec.py`](spec.py) כותב חיפושים בפורמט של `pool_rank.py`: כל שאלה, וכל שורות המסכת שלה כמועמדות (6,974 שורות). אפשר
  לצרף אותם ל־`pool.json` של `eval-pool` (`--merge`), כדי ששלב `pool` אחד ידרג את שניהם.
- [`measure.py`](measure.py): לכל מודל, באיזה מקום הוא שם את השורה הנכונה (1, 3, 10, MRR), וכמה פעמים ה"כמעט־נכון" לפניה.
  לכל זוג מודלים: בכמה שאלות רק אחד מהם שם את השורה ראשונה, או בעשר הראשונות. ההבדל נחשב אמיתי רק אם הוא גדול מפעמיים הרעש
  (מבחן McNemar).
- [`results.md`](results.md): התוצאות. דיקטה בלי אימון כבר שם 365 מהשורות הנכונות בעשר הראשונות, ו־247 ראשונות. לכן
  המקום הראשון הוא המדד.

```
GIT_LFS_SKIP_SMUDGE=1 git clone --filter=blob:none --no-checkout https://github.com/crfh1672/search-queries crfh
git -C crfh fetch origin add-berakhot-chagiga readd-existing-datasets
mkdir data; for b in add-berakhot-chagiga readd-existing-datasets; do git -C crfh archive origin/$b drafts | tar -x -C data; done
python map_lines.py --db seforim.db --drafts data/drafts --out lines.tsv
python spec.py --db seforim.db --drafts data/drafts --out crfh.json --merge pool.json
python ../kaggle_run.py push pool --spec crfh.json --release v3-20261012193827
python measure.py ranks-base.json ranks-student.json ranks-tuned.json --md
```

</div>

<div dir="rtl">

# eval-passages: שורות ארוכות, ווקטור לכל חלק שלהן

בעיה 7 ב־[`docs/SEARCH_ORDER_ISSUES.md`](../../docs/SEARCH_ORDER_ISSUES.md): המודל קורא שורה כיחידה אחת. הוא ממצע את
המילים שלה (mean pooling), ורק את 512 הטוקנים הראשונים שלה (`documentMaxTokens`). שורה ארוכה שמדברת על עניינים רבים מקבלת
ווקטור "מדולל", ומה שאחרי 512 הטוקנים לא נקרא בכלל. למשל, המשנה בבא בתרא ג, א נמצאת במקום 165 בצד הסמנטי על המילים שלה
עצמה ("חזקתן שלש שנים"), ובכל הספרים מפרשים קצרים קודמים למקור.

**ההצעה שנבדקת כאן:** לשורה ארוכה יש גם ווקטורים לחלקים שלה, והציון שלה הוא הטוב מבין הווקטור שלה וווקטורי החלקים. החלקים
הם קטעים מילה במילה של השורה, כמו שההדגשה לפי משמעות חותכת אותה (`Passages.kt`), בשתי צורות:
- **`pass<T>`:** כל משפט או פסוקית של שורה ארוכה מ־T טוקנים.
- **`chunk<C>`:** המשפטים של שורה ארוכה מ־C טוקנים, מחוברים לפי הסדר לנתחים של עד C טוקנים. כך שורה ארוכה מ־512 טוקנים
  נקראת כולה, ושורה ארוכה מתחלקת לכמה נתחים בלבד.

באינדקס זה יהיה מסמך לכל חלק, שהשורה שלו היא ההורה, ו־`DiversifyingChildrenByteKnnVectorQuery` של Lucene מחזירה לכל שורה
את החלק הטוב ביותר שלה. כאן זה נמדד בלי לבנות אינדקס: המודל של החבילה (int8 ONNX, כמו שהאפליקציה מריצה אותו) מדרג
מועמדים ידועים.

## הקבצים

- [`passages.py`](passages.py): החיתוך של `Passages.kt` בפייתון. [`test_passages.py`](test_passages.py) בודק אותו מול
  המקרים של `PassagesTest.kt`.
- [`rank.py`](rank.py): מדרג קובץ חיפושים (במבנה של `pool_rank.py`) בווקטור השורה (`line`, היום) ובכל אחת מהצורות. כותב
  `ranks-<variant>.json` במבנה של `pool_rank.py`, ‏`tokens.json` (האורך של כל מועמד) ו־`stats.json`. בלי טקסט.
- [`lengths.py`](lengths.py): מדגם של שורות הספרייה: כמה ארוכות, כמה טוקנים לא נקראים, וכמה ווקטורים כל צורה תוסיף לאינדקס.
- [`judged.py`](judged.py): החיפושים השפוטים (eval-real, ואצווה 2 של eval-v1), והמועמדים שלהם הם השורות ששפטנו להם.
  כך רואים אם החלקים מקדימים שורה טובה, או שורה ארוכה שקיבלה 0.
- [`quotes.py`](quotes.py): חיפושי הציטוטים של `eval-real/quotes/`, והמועמדים שלהם הם השורות שמכילות את הציטוט: המקור
  (משנה, גמרא, פסוק) והמצטטים. האם המקור ראשון?
- המבחן החיצוני [`../eval-crfh/`](../eval-crfh/): כל שורות המסכת כמועמדות, ו־`measure.py` מודד.

## ההרצה

```
python fetch_library.py --out build/library --release v3-20261012193827
# pardes-model: מתוך pardes-model.zip של ה־Release של החבילה (semantic-2-v3-20261012193827)
python eval-passages/lengths.py --db build/library/seforim.db --model pardes-model --out build/passages/lengths.json
python eval-crfh/spec.py --db build/library/seforim.db --drafts data/drafts --out build/passages/crfh.json
python eval-passages/judged.py spec --out build/passages/judged.json
python eval-passages/quotes.py spec --db build/library/seforim.db --out build/passages/quotes.json
for s in crfh judged quotes; do
  python eval-passages/rank.py --db build/library/seforim.db --model pardes-model \
      --spec build/passages/$s.json --out build/passages/$s
done
python eval-crfh/measure.py build/passages/crfh/ranks-*.json
python eval-passages/judged.py score build/passages/judged.json build/passages/judged/ranks-*.json \
    --tokens build/passages/judged/tokens.json
python eval-passages/quotes.py score build/passages/quotes.json build/passages/quotes/ranks-*.json
```

הכול במעבד. כאן, ב־4 ליבות: crfh בשתי דקות, המדגם של `lengths.py` בשבע דקות, השורות השפוטות בכחצי שעה (69,158 טקסטים,
רובם משפטים), והציטוטים בשעתיים (99,614 טקסטים: המצטטים הם שורות ארוכות). הסריקה של `quotes.py spec` עוברת על כל
הספרייה פעם אחת, בכרבע שעה.

**התוצאות:** [`results.md`](results.md).

</div>

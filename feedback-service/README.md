<div dir="rtl">

# שירות המשוב של פרדס

Worker של Cloudflare עם מסד D1. הוא מקבל את חבילות המשוב שמשתמשים שולחים מפרדס למחשב: מהכרטיס "שליחת משוב" שבהגדרות ›
ניהול נתונים, ומהשליחה האוטומטית שהם מאשרים בשאלה שנפתחת בהפעלה. את החבילות הוא נותן למתחזק
([`semantic-model/feedback/fetch.py`](../semantic-model/feedback/fetch.py)). מבנה החבילה מתואר ב־
[`semantic-model/feedback/README.md`](../semantic-model/feedback/README.md).

## מה נשמר, ומה לא

- כל חבילה נשמרת כמו שהגיעה (JSON): הציונים, החיפושים, הסימונים בצורת הדף, מזהה השולח האקראי, והגרסאות של פרדס ושל
  הספרייה.
- לא נשמרים כתובת ה־IP, ה־User-Agent או כל כותרת אחרת של הבקשה, ואין יומן בקשות (`observability` כבוי ב־`wrangler.toml`).
  הכתובת משמשת רק את מגבלת הקצב, בזיכרון של Cloudflare.
- משתמש שמבקש למחוק את מה ששלח (בכרטיס) מוחק את כל החבילות של מזהה השולח שלו. המחיקה נרשמת, כדי ש־`fetch.py` ימחק בהורדה
  הבאה גם את העותקים שכבר הורדו.

## הכתובות

| בקשה | מה |
|---|---|
| `POST /v1/packages` | חבילה (JSON, עד 1,000,000 בתים) ← `201 {"id"}` |
| `DELETE /v1/senders/<id>` | כל מה שהשולח שלח ← `{"deleted"}` |
| `GET /v1/packages?after=<id>&limit=<n>` | למתחזק: רשימת החבילות שאחרי המזהה, והמחיקות |
| `GET /v1/packages/<id>` | למתחזק: החבילה כמו שהגיעה |

הבקשות של המתחזק נושאות `Authorization: Bearer <FEEDBACK_ADMIN_TOKEN>`.

**מגבלות:**
- חבילה עד 1,000,000 בתים. האפליקציה שולחת חבילה גדולה בחלקים של עד 900,000 בתים.
- עד 200 חבילות ביום לשולח.
- עד 20 בקשות בדקה מכתובת אחת.

## ההקמה, פעם אחת

1. **חשבון Cloudflare.** החינמי מספיק: 100,000 בקשות ביום ו־5GB ב־D1. פותחים פעם אחת את Workers & Pages בלוח הבקרה, ושם נוצר
   תת־הדומיין של workers.dev. הוא חלק מהכתובת שהאפליקציה שולחת אליה, ולכן כדאי לבחור שם שלא מזהה אתכם.
2. **API token:** My Profile › API Tokens › Create Token. בוחרים בתבנית "Edit Cloudflare Workers", ומוסיפים לה הרשאה
   Account › D1 › Edit.
3. **שלושה סודות** ב־Settings › Secrets and variables › Actions של הריפו:
   - `CLOUDFLARE_API_TOKEN`: הטוקן מהשלב הקודם.
   - `CLOUDFLARE_ACCOUNT_ID`: מזהה החשבון, מלוח הבקרה, בצד של Workers & Pages.
   - `FEEDBACK_ADMIN_TOKEN`: מחרוזת אקראית ארוכה, למשל הפלט של `openssl rand -hex 32`.
4. **פרסום:** Actions › Feedback service › Run workflow. הוא בודק את ה־Worker, יוצר את המסד בפעם הראשונה, מחיל את
   ה־migrations, מפרסם, וקובע את הטוקן. שם החשבון לא מופיע ביומן: תת־הדומיין ומזהה המסד מוסתרים.
5. **הכתובת:** כתובת ה־Worker, `https://pardes-feedback.<תת-הדומיין>.workers.dev`, מופיעה בלוח הבקרה. שמים אותה בסוד
   `PARDES_FEEDBACK_URL`, ומשם היא נכנסת לגרסאות של פרדס למחשב (`desktop-release.yml`). גרסה שנבנתה בלי הכתובת לא שואלת
   ולא שולחת, ורק שומרת משוב לקובץ.

אחרי זה, כל שינוי ב־`feedback-service/` שנכנס ל־main מתפרסם לבד. שינוי במסד נכנס כ־migration חדש ב־`migrations/`.

## מקומית

```
npm ci
npm test
npx wrangler d1 migrations apply pardes-feedback --local
echo 'ADMIN_TOKEN=local' > .dev.vars
npx wrangler dev
```

`npm test` מריץ את הבדיקות ב־Miniflare, סביבת ההרצה של Cloudflare. מול ה־Worker המקומי בונים את האפליקציה עם
`-Pfeedback.url=http://127.0.0.1:8787` (או `feedback.url` ב־`local.properties`), ומריצים את `fetch.py` עם
`--url http://127.0.0.1:8787`.

</div>

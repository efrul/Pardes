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

1. **חשבון Cloudflare:** ‏`dash.cloudflare.com/sign-up`, דוא"ל וסיסמה, ואישור בקישור שנשלח בדוא"ל. החינמי מספיק: 100,000 בקשות
   ביום ו־5GB ב־D1. אין צורך בדומיין.
2. **שם החשבון:** כברירת מחדל הוא הדוא"ל ("…'s Account"). כדאי לשנות אותו לשם שלא מזהה אתכם: Configurations › Account Name ›
   Change Name.
3. **תת־הדומיין של workers.dev:** ב־Workers & Pages, ליד Your subdomain. הוא חלק מהכתובת שהאפליקציה שולחת אליה. אם הוא מזהה
   אתכם, או שעוד אין כזה, בוחרים ב־Change שם שלא מזהה.
4. **API token:** ‏Manage Account › API Tokens (או My Profile › API Tokens) › Create Token › התבנית Edit Cloudflare Workers. לתבנית
   מוסיפים שורה: Account › D1 › Edit. אחר כך Continue to summary › Create Token. הטוקן מוצג פעם אחת בלבד.
5. **מזהה החשבון:** ב־Workers & Pages, תחת Account Details, ליד Account ID.
6. **שלושה סודות** ב־Settings › Secrets and variables › Actions של הריפו:
   - `CLOUDFLARE_API_TOKEN`: הטוקן.
   - `CLOUDFLARE_ACCOUNT_ID`: מזהה החשבון.
   - `FEEDBACK_ADMIN_TOKEN`: מחרוזת אקראית ארוכה, שגם נשמרת אצלכם, כי `fetch.py` צריך אותה.
7. **פרסום:** Actions › Feedback service › Run workflow. הוא בודק את ה־Worker, יוצר את המסד בפעם הראשונה, מחיל את
   ה־migrations, מפרסם, וקובע את הטוקן. שם החשבון, הדוא"ל שבו, תת־הדומיין ומזהה המסד מוסתרים ביומן.
8. **הכתובת:** ב־Workers & Pages › pardes-feedback מופיעה `https://pardes-feedback.<תת-הדומיין>.workers.dev`. כשפותחים אותה
   בדפדפן, היא עונה "Pardes feedback service". את הכתובת שמים בסוד `PARDES_FEEDBACK_URL`, ומשם היא נכנסת לגרסאות של פרדס
   למחשב (`desktop-release.yml`). גרסה שנבנתה בלי הכתובת לא שואלת ולא שולחת, ורק שומרת משוב לקובץ.

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

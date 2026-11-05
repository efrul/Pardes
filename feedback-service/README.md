<div dir="rtl">

# שירות המשוב של פרדס

Worker של Cloudflare עם מסד D1. הוא מקבל את חבילות המשוב שמשתמשים שולחים מפרדס למחשב: מהכרטיס "שליחת משוב" שבהגדרות ›
ניהול נתונים, ומהשליחה האוטומטית שהם מאשרים בשאלה שנפתחת בהפעלה. את החבילות הוא נותן למתחזק
([`pardes-semantic/feedback/fetch.py`](https://github.com/efrul/pardes-semantic/blob/main/feedback/fetch.py)). מבנה החבילה מתואר ב־
[`pardes-semantic/feedback/README.md`](https://github.com/efrul/pardes-semantic/blob/main/feedback/README.md).

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
| `GET /v1/packages?bodies=1&after=<id>&limit=<n>` | למתחזק: כמו הרשימה, ועם כל חבילה גם התוכן שלה (`package`), עד 50 בבקשה |
| `GET /v1/packages/<id>` | למתחזק: החבילה כמו שהגיעה |
| `GET /admin` | הדף של המתחזק (למטה) |

הבקשות של המתחזק נושאות `Authorization: Bearer <FEEDBACK_ADMIN_TOKEN>`.

## לראות את הנתונים

הדף `https://pardes-feedback.<תת-הדומיין>.workers.dev/admin` מציג את מה שנשלח:
- **סיכום:** כמה חבילות, שולחים, חיפושים, דירוגים ושורות בצורת הדף; החבילות בכל יום ב־30 הימים האחרונים; חלוקת
  הדירוגים; החיפושים הנפוצים.
- **חיפושים, דירוגים, צורת הדף:** טבלאות עם חיפוש ומיון, והורדה כ־CSV.
- **חבילות:** כל חבילה כמו שהגיעה, והשולחים שמחקו את מה ששלחו.

**הכניסה:** בדף עצמו אין נתונים. הוא מבקש את קוד המנהל, הערך של `FEEDBACK_ADMIN_TOKEN`, וקורא איתו את החבילות מהכתובות של
המתחזק, כמו `fetch.py`. עם "לזכור במכשיר הזה" הקוד נשמר בדפדפן. בלי זה, הוא נשכח כשסוגרים את הלשונית.

**אם הקוד אבד:**
1. בוחרים מחרוזת אקראית ארוכה חדשה.
2. שמים אותה בסוד `FEEDBACK_ADMIN_TOKEN` ב־GitHub.
3. מריצים את Actions › Feedback service › Run workflow.
4. משנים גם את `PARDES_FEEDBACK_ADMIN_TOKEN` אצלכם, בשביל `fetch.py`.

**הבטיחות:** כל טקסט שהגיע בחבילה מוצג כטקסט בלבד, ולא כקוד. ה־CSP של הדף לא מריץ שום סקריפט חוץ מ־`src/admin/app.js`,
ולא מאפשר להציג את הדף בתוך מסגרת של אתר אחר.

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

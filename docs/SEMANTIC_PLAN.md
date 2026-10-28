<div dir="rtl">

# החיפוש לפי משמעות בפרדס: חבילות ועדכונים (תכנון, 2026-10-28; עודכן באותו יום)

**ההחלטה:** הכול במאגר הזה, efrul/Pardes:
- ה־Releases של החבילות;
- ה־Workflow שמפרסם אותן;
- הקוד של החיפוש לפי משמעות והתקנת החבילות מתוך התוכנה (שלבים 2 ו־3, בוצעו).

**חבילה** היא שתי תיקיות שיושבות ליד `seforim.db`:
- `pardes-model`: המודל לשאילתות;
- `pardes.vec.lucene`: אינדקס הווקטורים של הספרייה.

## איך זית עושה את זה

מהקוד של SeforimApp ושל SeforimLibrary (תת־המודול כאן):

1. **הספרייה היא Release ב־kdroidFilter/SeforimLibrary.** היא נבנית ב־GitHub Actions, בהרצה ידנית של `manual-generate-release.yml`:
   - בונים את `seforim.db` מ־Sefaria ומאוצריא, את אינדקסי Lucene ואת `catalog.pb`;
   - אורזים הכול ל־`seforim_bundle.tar.zst` (zstd ברמה 22);
   - מפצלים ל־`.part01`, `.part02` וכו', של 1.9GB כל אחד, כי GitHub מגביל קובץ ל־2GB;
   - מוסיפים `seforim_bundle.tar.zst.manifest.json`, עם גודל ו־sha256 לכל חלק;
   - התג הוא `v<db_version>-<זמן>`, למשל `v3-20261012193827`.
2. **ההתקנה:**
   - התוכנה קוראת את `releases/latest` של SeforimLibrary (`GitHubReleaseFetcher`);
   - מורידה את החלקים ובודקת אותם מול ה־manifest (`DownloadUseCase`);
   - פורסת אותם לתיקיית הספרייה (`ExtractUseCase`);
   - הגרסה נשמרת ב־`release_info.txt`.
3. **עדכונים מצטברים:**
   - בכל Release נבנים גם תיקונים, `patch-vA-vB.db.zst`, מהגרסאות 1, 2, 4, 8 ו־16 אחורה.
   - התוכנה קוראת את `release_meta.json` (ב־GitHub Pages של SefariaExport) ומשווה אותו ל־`schema_meta.db_version` שבמסד.
   - היא מחילה את שרשרת התיקונים על SQLite ועל אינדקס Lucene (`DbDeltaUpdateService`).
   - מספרי השורות נשמרים בין הגרסאות: מקצה המספרים מאותחל מה־Release הקודם. לכן תיקון לא מחליף את כל המסד.
4. **עדכון התוכנה עצמה:** קובצי ההתקנה ב־Releases, ו־Nucleus קורא את `latest.yml`. פרדס למחשב עושה את זה בתגים `desktop-*` של המאגר הזה, תמיד עם `--latest=false`.
5. **החיפוש הסמנטי של זית:**
   - המודל בחבילת Maven פרטית (`seforim-semantic`);
   - הווקטורים בתוך `seforim.db.lucene` של הספרייה, ונבנים ב־`:searchindex:embedCorpus` על GPU;
   - הרישיון שלו לא חל על פרדס. לכן לפרדס יש חיפוש משלו: מודל משלו ואינדקס משלו, בתיקיות נפרדות.

## מה מוכן

- **החבילה הראשונה לפרסום, חבילה 2:**
  - המודל `student@695cbe2/pardes-clean-1`, 384 ממדים;
  - האינדקס של 5,725,672 שורות, לספרייה `v3-20261012193827`;
  - בפורמט של זית: `pardes-semantic.tar.zst.part01` ו־`.part02` (2.04GB יחד), עם `pardes-semantic.tar.zst.manifest.json`.
  - ה־manifest אומר גם מה בחבילה: `pack`, `model`, `dimension`, `library`, `lines`.
  - נבדקה בספריות שהתוכנה פורסת בהן: zstd-jni ו־commons-compress. כל הקבצים יצאו זהים למקור.
  - שמורה במאגר נתונים פרטי ב־Kaggle: `atisinai/pardes-semantic-pack-2`.
- **[`semantic-model/package_pack.py`](../semantic-model/package_pack.py)**: אורז חבילה כזו ובודק אותה.
- **[`.github/workflows/semantic-release.yml`](../.github/workflows/semantic-release.yml)**, כאן. הוא:
  - מוריד את החבילה מ־Kaggle;
  - בודק אותה מול ה־manifest;
  - בונה `pardes-model.zip` ו־`pardes.vec.lucene.zip` להתקנה ידנית, ובראשון גם `pardes-model/pardes-pack.json`: מה שה־manifest אומר על החבילה;
  - יוצר Release עם `--latest=false`. אחריו `android-site.yml` רץ שוב וכותב באתר את `semantic.json`, רשימת החבילות.

  למה דרך Kaggle: הסשן שהכין את החבילה לא יכול ליצור Releases ("not permitted for this session type"). ה־Workflow רץ ב־GitHub, כמו זה של SeforimLibrary.

## שלב 1: פרסום החבילות

1. מוסיפים את הסוד `KAGGLE_API_TOKEN`, טוקן של חשבון ה־Kaggle שהמאגר שלו (‏`atisinai`). המקום: Settings › Secrets and variables › Actions.
2. מריצים: Actions › Semantic pack release › Run workflow, מ־`main`, אחרי שה־Workflow מוזג אליו.
3. התוצאה: Release בשם `semantic-2-v3-20261012193827`, שאינו ה־Latest. בתוכו:
   - שני ה־zip להתקנה ידנית;
   - החבילה בפורמט של זית, להתקנה מתוך התוכנה.

כללים:
- **תג:** `semantic-<מספר חבילה>-<גרסת ספרייה>`.
- **תמיד `--latest=false`:** ה־Latest הוא של האנדרואיד.
- **מעט Releases:** אנדרואיד 0.3.0 עד 0.4.1 קורא רק את 30 ה־Releases האחרונים, לכן מוחקים חבילה ישנה כשיש חדשה.

## שלב 2: הקוד של החיפוש לפי משמעות במאגר הזה (בוצע)

- הקוד עבר לכאן מ־efrul/Pardes-Hybrid-Desktop, יחד עם `semantic-model/`: `features/search/semantic`, החיבור ל־`HybridSearchEngine`, `LevelPrecedence` והבדיקות.
- `desktop-release.yml` בונה אותו, והמודל לא בקובץ ההתקנה: הוא מגיע כחבילה.

## שלב 3: התקנת החבילה מתוך התוכנה (בוצע)

הקוד ב־`features/search/semantic/pack/` ובכרטיס `features/settings/semantic/`; ההסבר ב־[SEMANTIC_SEARCH.md](../SEMANTIC_SEARCH.md).

1. **מציאה (`SemanticPackReleases`):**
   - `GET /repos/efrul/Pardes/releases`, התגים `semantic-*`, בלי טיוטות ובלי גרסאות ניסיון;
   - החבילה עם המספר הגבוה ביותר שהספרייה שלה היא הספרייה המותקנת (הזמן ב־`release_info.txt` שווה לזמן שבתג), ושיש בה manifest;
   - כשה־API חסום: `semantic.json` שבאתר, באותו מבנה.
2. **מה מותקן (`InstalledPack`):** ה־`id` ב־`pardes-model.json`, נתוני ה־commit של האינדקס, ו־`pardes-model/pardes-pack.json` בשביל מספר החבילה. כמו שהחיפוש פותח אותם: מודל ואינדקס של מודלים שונים, או אינדקס של ספרייה אחרת, אינם בשימוש.
3. **הורדה:** כמו `DownloadUseCase`, עם `BundleManifest` ו־sha256 לכל חלק, לתיקייה `.pardes-semantic-download` ליד הספרייה. חלק שהורד עד הסוף בניסיון קודם נשאר.
4. **פריסה (`SemanticPackInstaller`):**
   - בזרם, כמו `ExtractUseCase`, לתיקייה `.pardes-semantic-staged` ליד הספרייה, באותו כונן, עם sha256 של כל הזרם. כשמשהו פגום, ה־sha256 של כל חלק אומר איזה קובץ.
   - רק שתי התיקיות של החבילה; שם אחר נדחה.
   - בדיקה כמו בפתיחה: המודל נקרא (גם ה־sha256 של קובציו), האינדקס של אותו מודל ושל הספרייה המותקנת.
5. **החלפה (`PendingSemanticPack`):**
   - בהפעלה הבאה, לפני שהחיפוש נפתח (`main.kt`), ולא בזמן ריצה: החיפוש מחזיק את הקבצים פתוחים, ו־Windows לא משנה שם של קובץ פתוח. גם `HybridSearchEngine` מחליט בהפעלה אם יש חיפוש לפי משמעות.
   - כל תיקייה ישנה מקבלת את השם `.old`, החדשה נכנסת, ורק אחרי ששתיהן בפנים הישנות נמחקות.
   - אם משהו נכשל, שתי הישנות חוזרות. הפעלה שנעצרה באמצע ממשיכה בהפעלה הבאה: כל תיקייה חדשה מסומנת (`.pardes-staged`) עד הסוף.
6. **מקום פנוי:** נבדק לפני ההורדה (החבילה, עוד פי 1.5 ממנה לפריסה, ו־200MB) ולפני הפריסה.
7. **ממשק:** בהגדרות › ניהול נתונים › חיפוש לפי משמעות: מה מותקן, החבילה האחרונה לספרייה וגודלה, **הורדה והתקנה** (או **עדכון לחבילה N**), **ייבוא מקבצים…**, התקדמות וביטול, ו**הפעלה מחדש עכשיו**.
8. **ייבוא ידני:** ה־manifest, אחד החלקים או הקובץ השלם; אחד משני קובצי ה־zip (את השני מוצאים לידו לפי מה שיש בו, גם אם הדפדפן שינה את שמו); או `pardes-model.json` של תיקיות שכבר נפרסו. קבצי המשתמש נשארים.
9. **עם עדכוני הספרייה:**
   - **עדכון מלא:** `DatabaseCleanupUseCase` מוחק את `pardes.vec.lucene` עם שאר `*.lucene`. ההגדרות מראות שחסר האינדקס, ומציעות את החבילה של הספרייה החדשה כשהיא יוצאת.
   - **עדכון מצטבר:** לא משנה את `release_info.txt`, ולכן האינדקס נשאר בשימוש. רק לשורות החדשות אין וקטור (שלב 4).
   - `IndependentSemanticSearch` בודק עכשיו גם את גרסת הספרייה של האינדקס (`pardes.dbRelease` מול `release_info.txt`).

**עוד לא:** הצעה להוסיף חיפוש לפי משמעות מיד אחרי התקנת הספרייה, במדריך ההגדרה.

## שלב 4: עדכונים מצטברים לאינדקס

- **אחרי כל Release של הספרייה עם תיקון (למשל v3 ל־v4):**
  - מוצאים את השורות שנוספו או השתנו;
  - מחשבים להן וקטורים במודל הקטן. במעבד זה מספיק לאלפי שורות.
  - מפרסמים `semantic-delta-v3-v4`: וקטורים, מספרי שורות, ושורות שנמחקו.
- **בתוכנה:** אחרי התיקון של הספרייה, מוסיפים ומוחקים מסמכים ב־`pardes.vec.lucene`.
- **חבילה מלאה** רק למודל חדש (Kaggle, כ־5 שעות GPU), או כשהתיקונים מצטברים.
- **איפה זה רץ:** ב־Workflow כאן, על מעבד.

## הספרייה עצמה: להמשיך עם זית או לבנות לבד?

- **היום:** פרדס למחשב ולאנדרואיד מתקינים את הספרייה מ־kdroidFilter/SeforimLibrary ומחילים את התיקונים שלה.
- **ההמלצה: להמשיך כך.** מפרסמים כאן רק את מה שפרדס מוסיף.
- **לבנות לבד** פירושו:
  - להעתיק לכאן את `manual-generate-release.yml` ואת המחולל (מתת־המודול, בלי לשנות אותו);
  - כ־7GB לכל Release, ועוד תיקונים;
  - `release_meta.json` משלנו ב־efrul.github.io/Pardes, ולשנות את הכתובות בתוכנה;
  - שעות ריצה לכל Release.

  זה שווה רק אם פרדס צריך ספרים אחרים או תיקונים משלו.

## חלוקת עבודה

- **המאגר:** efrul/Pardes-Hybrid-Desktop יועבר לארכיון; כל העבודה כאן.
- **סשן המודל:** החבילות, `package_pack.py`, `semantic-release.yml`, והתהליך של שלב 4.
- **סשן התוכנה:** התוכנה.

## החלטות פתוחות

1. להוסיף את הסוד `KAGGLE_API_TOKEN` ל־efrul/Pardes, או להעלות את הקבצים בעצמך.
2. הספרייה: של זית (מומלץ), או משלנו.
3. האם להציע את החבילה כבר במדריך ההגדרה, אחרי התקנת הספרייה.

</div>

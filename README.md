<div dir="rtl">

# פרדס: ספרייה תורנית לאנדרואיד

*Pardes is an Android app based on [Zayit](https://github.com/kdroidFilter/Zayit), the Jewish study library by Elie (Elyahou) Gambache, and not made on his behalf: he did not make it, is not responsible for it and does not support it, so please don't report its problems to him or to the Zayit project.*

**להורדה: [efrul.github.io/Pardes](https://efrul.github.io/Pardes/)** · [כל הגרסאות](https://github.com/efrul/Pardes/releases)

פרדס מבוסס על **זית מאת אליהו גמבש** ואינו מטעמו: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה. בשאלות עליו אין לפנות אליו או לפרויקט זית.

</div>

![פרדס: דף הבית, חיפוש, קריאה, מצב כהה, כלים, מעגל השנה, מסך קטן, ווידג'טים וטאבלט](androidApp/docs/release/overview.png)

<div dir="rtl">

## מה יש בו

- הספרייה של זית למחשב (SeforimLibrary), וחיפוש מלא בכל הספרים בלי אינטרנט, באותו מנוע ובאותו אינדקס.
- קריאה עם ניקוד וטעמים, מפרשים, תרגומים וקישורים, הערות והדגשות.
- זמני היום, לוח עברי, מולד וקידוש לבנה, מילון ומעגל השנה.
- ווידג'טים למסך הבית ומצב כהה, מטלפון של 3 אינץ' ועד טאבלט.

## התקנה

1. מורידים את קובץ ה־APK מ[אתר ההורדה](https://efrul.github.io/Pardes/) או מ[עמוד הגרסאות](https://github.com/efrul/Pardes/releases). דרוש Android 8.0 ומעלה (עדכון מצטבר של הספרייה: Android 11 ומעלה; ווידג'טים למסך הבית של הטלפון: Android 12 ומעלה).
2. מאשרים התקנה ממקור לא מוכר.
3. בפתיחה הראשונה מכניסים את הספרייה, באחת משלוש דרכים:
   - הורדה בטלפון: כ־5.5 ג״ב, וכ־14.1 ג״ב פנויים.
   - העברה ממחשב שזית למחשב מותקן בו עם ספרייה עדכנית (מ־11 באוקטובר 2026 ואילך), בכבל USB: ב־Windows מורידים מ[אתר ההורדה](https://efrul.github.io/Pardes/#lib-computer) את `push-desktop-library-windows.zip`, מחלצים אותו לתיקייה (לחיצה ימנית › חלץ הכול) ולוחצים פעמיים על `push-desktop-library.cmd` שבתיקייה שנוצרה; בלינוקס ובמק [`push-desktop-library.sh`](androidApp/tools/push-desktop-library.sh). דרוש adb (Android SDK Platform-Tools).
   - התקנה מקבצי ההפצה של [SeforimLibrary](https://github.com/kdroidFilter/SeforimLibrary/releases/latest): בוחרים יחד את כל החלקים `seforim_bundle.tar.zst.part01`, `part02` וכן הלאה.

## הקוד

- האפליקציה נמצאת ב־[`androidApp/`](androidApp/). הוראות בנייה ב־[androidApp/README.md](androidApp/README.md), ומצב העבודה ב־[androidApp/docs/DEVELOPMENT.md](androidApp/docs/DEVELOPMENT.md).
- GitHub Actions בונה את ה־APK ומפרסם אותו בעמוד הגרסאות כשמפעילים אותו ידנית (Actions › Android release › Run workflow). אתר ההורדה נמצא ב־[`androidApp/site/`](androidApp/site/) ומתעדכן בכל שינוי בו.
- שאר הריפו הוא עותק של הקוד של זית למחשב מ־[kdroidFilter/Zayit](https://github.com/kdroidFilter/Zayit), כפי שהיה ביוני 2026 (לפני זית 0.6.1), עם תוספות קטנות של פרדס: הסקיל `.claude/skills/zayit-android/`, ה־workflows ‏`.github/workflows/android-*.yml` ופסקה על אנדרואיד ב־`CLAUDE.md`. ה־README המקורי שלו נמצא ב־[README-zayit-desktop.md](README-zayit-desktop.md), ובעברית ב־[README_HE-zayit-desktop.md](README_HE-zayit-desktop.md).

## רישיון וקרדיט

- זית ו־SeforimLibrary מאת אליהו גמבש: <span dir="ltr">Copyright (C) 2025 Elie Gambache</span>, תחת GNU AGPL v3 עם תנאי ייחוס נוסף (§7ב). ראו [LICENSE](LICENSE).
- פרדס נבנה מהקוד הפתוח של זית 0.6.7 ושונה ב־2026 כדי לפעול באנדרואיד. גם הוא מופץ תחת AGPL-3.0, והוא מסופק כמות שהוא, ללא כל אחריות. לכל גרסה בעמוד הגרסאות מצורף קוד המקור המלא שלה.
- <span dir="ltr">Powered by the technologies that drive Zayit — https://zayitapp.com/</span>
- הספרים מגיעים מ־[Sefaria](https://www.sefaria.org.il/) ומ[אוצריא](https://github.com/Otzaria/otzaria-library), דרך [SeforimLibrary](https://github.com/kdroidFilter/SeforimLibrary).
- זית, היישום המקורי של אליהו גמבש: [zayitapp.com](https://zayitapp.com/).

</div>

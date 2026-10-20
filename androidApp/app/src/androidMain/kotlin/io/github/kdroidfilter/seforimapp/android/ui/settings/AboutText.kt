// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforimapp.android.ui.settings

/** Settings › אודות (desktop AboutSettingsScreen), in this app's words; also the Android 4.4 edition's. */
internal val ABOUT_MD = """
### אודות פרדס

פרדס הוא יישום לאנדרואיד ללימוד התורה, המבוסס על זית מאת אליהו גמבש ואינו מטעמו: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה. פרדס נבנה מהקוד הפתוח של זית 0.6.1 (רישיון AGPL-3.0), ושונה כדי לפעול באנדרואיד.

גם פרדס מופץ תחת רישיון AGPL-3.0, ו[קוד המקור שלו](https://github.com/efrul/Pardes) זמין תחת אותו רישיון. המודולים הקנייניים של הגרסאות הרשמיות של זית (החיפוש הסמנטי והסידור החכם המלא) אינם כלולים בו.

⁦Powered by the technologies that drive Zayit — https://zayitapp.com/⁩

### תודות

לאליהו גמבש, יוצר [זית](https://zayitapp.com/) ו־[SeforimLibrary](https://github.com/kdroidFilter/SeforimLibrary), שעל הקוד הפתוח שלהם פרדס בנוי.

לפרויקטים [Sefaria](https://www.sefaria.org.il/) ו[אוצריא](https://otzaria.org), שמהם מגיעים הספרים.

> התכנים שלפניכם נאספו, מוינו, סוננו ונערכו בעמל רב ובמסירות על ידי צוות פרויקט 'אוצריא' ([otzaria.org](https://otzaria.org)).

לקרן Apache על Lucene, מנוע החיפוש, ול־JetBrains ול־Google על Kotlin ו־Compose.

אייקון היד הכותבת: Emoji One, ברישיון [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
""".trimIndent()

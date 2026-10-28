package io.github.kdroidfilter.seforimapp.android.ui.settings

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.CONDITIONS_MARKDOWN

/**
 * Desktop AboutSettingsScreen, on its own like desktop's AccentMarkdownView: a section on Pardes, then its thanks.
 * Desktop's files/ABOUT.md is left out: it is Zayit's author on Zayit, in his first person and inviting readers to
 * contact him, which in an app he did not make would read as his voice. Its thanks are kept, in this app's words.
 */
@Composable
internal fun AboutSettings(wide: Boolean) {
    SettingsColumn(wide) { MarkdownText(ABOUT_MD) }
}

/**
 * Desktop ConditionsSettingsScreen: CONDITIONS_MARKDOWN, the same text the onboarding's licence step shows. Its AGPL
 * link opens the licence bundled in the APK.
 */
@Composable
internal fun TermsSettings(wide: Boolean) {
    BundledLicenceLinks { SettingsColumn(wide) { MarkdownText(CONDITIONS_MARKDOWN.trimIndent()) } }
}

private val ABOUT_MD = """
### אודות פרדס

פרדס הוא יישום לאנדרואיד ללימוד התורה, המבוסס על זית מאת אליהו גמבש ואינו מטעמו: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה. פרדס נבנה מהקוד הפתוח של זית 0.6.11 (רישיון AGPL-3.0), ושונה כדי לפעול באנדרואיד.

גם פרדס מופץ תחת רישיון AGPL-3.0, ו[קוד המקור שלו](https://github.com/efrul/Pardes) זמין תחת אותו רישיון. המודולים הקנייניים של הגרסאות הרשמיות של זית (החיפוש הסמנטי, הסידור החכם המלא וקישורי הפסוקים המצוטטים בתלמוד) אינם כלולים בו.

⁦Powered by the technologies that drive Zayit — https://zayitapp.com/⁩

### תודות

לאליהו גמבש, יוצר [זית](https://zayitapp.com/) ו־[SeforimLibrary](https://github.com/kdroidFilter/SeforimLibrary), שעל הקוד הפתוח שלהם פרדס בנוי.

לפרויקטים [Sefaria](https://www.sefaria.org.il/) ו[אוצריא](https://otzaria.org), שמהם מגיעים הספרים.

> התכנים שלפניכם נאספו, מוינו, סוננו ונערכו בעמל רב ובמסירות על ידי צוות פרויקט 'אוצריא' ([otzaria.org](https://otzaria.org)).

לקרן Apache על Lucene, מנוע החיפוש, ול־JetBrains ול־Google על Kotlin ו־Compose.

אייקון היד הכותבת: Emoji One, ברישיון [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
""".trimIndent()

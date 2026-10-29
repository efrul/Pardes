package io.github.kdroidfilter.seforimapp.android.ui.settings

/**
 * The semantic pack card's words, desktop's semantic_pack_* and semantic_problem_* strings (Pardes desktop's
 * SeforimApp/src/commonMain/composeResources/values/strings.xml). Android's own: the files are picked all together
 * ([importHint], [problemNotAPack], [problemMissing]), as the system's picker shows no file's folder, and the
 * notification's lines.
 */
internal object SemanticPackStrings {
    const val title = "חיפוש לפי משמעות"
    const val description = "מוצא גם שורות שאומרות את מה שחיפשתם במילים אחרות. המודל שלו נשמר בתיקיית הספרייה."
    const val installed = "מותקנת חבילה %1\$s, עם %2\$s שורות."
    const val installedUnnumbered = "מותקן, עם %1\$s שורות."
    const val notInstalled = "לא מותקן: החיפוש מוצא רק את המילים שחיפשתם."
    const val noModel = "בתיקיית הספרייה יש אינדקס בלי המודל שלו (pardes-model), ולכן החיפוש לפי משמעות כבוי."
    const val noIndex =
        "בתיקיית הספרייה יש מודל בלי האינדקס שלו (pardes.vec.lucene), למשל אחרי עדכון מלא של הספרייה. לכן החיפוש לפי משמעות כבוי."
    const val otherModel = "המודל והאינדקס שבתיקיית הספרייה הם מחבילות שונות, ולכן החיפוש לפי משמעות כבוי."
    const val otherLibrary = "האינדקס שבתיקיית הספרייה נבנה לספרייה מ־%1\$s, והספרייה המותקנת היא מ־%2\$s. לכן החיפוש לפי משמעות כבוי."
    const val unreadable = "אי אפשר לקרוא את החבילה שבתיקיית הספרייה (%1\$s), ולכן החיפוש לפי משמעות כבוי."
    const val checking = "בודק אם יש חבילה לספרייה…"
    const val available = "חבילה %1\$s זמינה להורדה (%2\$s)."
    const val upToDate = "זו החבילה האחרונה לספרייה הזו."
    const val noneYet = "אין עדיין חבילה לספרייה מ־%1\$s."
    const val unreachable =
        "אי אפשר לבדוק כרגע אם יש חבילה, כי אין חיבור ל־GitHub. אפשר להוריד אותה בדפדפן מדף ההורדות ולייבא אותה."
    const val unknownLibrary = "לא ידוע מאיזו גרסה הספרייה המותקנת, ולכן אי אפשר לבחור לה חבילה. אפשר לייבא חבילה מקבצים."
    const val noLibrary = "צריך להתקין קודם את הספרייה."
    const val download = "הורדה והתקנה"
    const val update = "עדכון לחבילה %1\$s"
    const val import = "ייבוא מקבצים…"
    const val releasesPage = "לדף ההורדות"
    const val cancel = "ביטול"
    const val dismiss = "סגירה"
    const val downloading = "מוריד: %1\$s מתוך %2\$s"
    const val unpacking = "פורס ובודק את החבילה…"
    const val ready = "החבילה מוכנה, והיא תיכנס לשימוש בהפעלה הבאה של היישום."
    const val restart = "הפעלה מחדש עכשיו"
    const val startFailed = "בהפעלה הקודמת התקנת החבילה לא הושלמה (%1\$s), ומה שהיה מותקן נשאר. אפשר לנסות שוב."
    const val problemCorrupted = "הקובץ %1\$s פגום או חלקי. אפשר להוריד אותו שוב."
    const val problemUnusable = "אי אפשר להשתמש בחבילה הזו (%1\$s)."
    const val problemOtherLibrary = "החבילה נבנתה לספרייה מ־%1\$s, והספרייה המותקנת היא מ־%2\$s."
    const val problemFailed = "ההתקנה נכשלה (%1\$s)."
    // desktop's library_problem_no_space
    const val problemNoSpace = "אין מספיק מקום ב־%1\$s: צריך %2\$s, פנויים %3\$s."

    // Android: the picker gives the files picked, not their folder
    const val importHint =
        "בייבוא בוחרים יחד את כל קובצי החבילה שהורדו: את שני קובצי ה־zip, או את pardes-semantic.tar.zst.manifest.json עם החלקים שלו."
    const val problemNotAPack =
        "הקובץ %1\$s אינו קובץ של חבילה לחיפוש לפי משמעות. בוחרים יחד את pardes-model.zip ואת pardes.vec.lucene.zip, או את " +
            "pardes-semantic.tar.zst.manifest.json עם החלקים שלו, שהורדו מדף ההורדות."
    const val problemMissing = "חסר הקובץ %1\$s. בוחרים אותו יחד עם שאר קובצי החבילה."

    // Android: the notification of a download or an import that goes on in the background
    const val failedNotification = "התקנת החבילה לא הושלמה. הפרטים בהגדרות › ניהול נתונים."
}

package io.github.kdroidfilter.seforimapp.android.ui.settings

/**
 * The semantic search card's words, desktop's semantic_pack_* and semantic_problem_* strings (Pardes desktop's
 * SeforimApp/src/commonMain/composeResources/values/strings.xml): what the user reads names the semantic search's files
 * and their version, never "a pack". Android's own: the files are picked all together ([importHint],
 * [problemNotAPack], [problemMissing]), as the system's picker shows no file's folder; they are downloaded in the
 * phone's browser as often as on a computer ([offline]); the room is the library's storage, the phone's or the card's
 * ([problemNoSpace]); and the notification's lines.
 */
internal object SemanticPackStrings {
    const val title = "חיפוש לפי משמעות"
    const val description = "מוצא גם שורות שאומרות את מה שחיפשתם במילים אחרות. בשבילו מורידים פעם אחת שני קבצים: מודל ואינדקס של הספרייה."
    const val installed = "מותקן: גרסה %1\$s של הקבצים, עם %2\$s שורות."
    const val installedUnnumbered = "מותקן, עם %1\$s שורות."
    const val notInstalled = "לא מותקן: החיפוש מוצא רק את המילים שחיפשתם."
    const val noModel = "חסר המודל, ולכן החיפוש לפי משמעות כבוי."
    const val noIndex = "חסר האינדקס, למשל אחרי עדכון מלא של הספרייה, ולכן החיפוש לפי משמעות כבוי."
    const val otherModel = "המודל והאינדקס הם מגרסאות שונות, ולכן החיפוש לפי משמעות כבוי."
    const val unreadable = "אי אפשר לקרוא את הקבצים (%1\$s), ולכן החיפוש לפי משמעות כבוי."
    const val checking = "בודק אם יש קבצים להורדה…"
    const val available = "גרסה %1\$s של הקבצים זמינה להורדה: %2\$s, ובזמן ההתקנה צריך %3\$s פנויים."
    const val upToDate = "זו הגרסה האחרונה של הקבצים."
    const val noneYet = "עוד אין קבצים להורדה."
    const val unreachable = "אין כרגע חיבור ל־GitHub, ולכן אי אפשר להוריד מכאן."
    const val onlineLabel = "עם אינטרנט:"
    const val online = "לוחצים \"הורדה והתקנה\". החיפוש לפי משמעות יפעל מההפעלה הבאה."
    const val offlineLabel = "בלי אינטרנט:"

    // Android: in the phone's browser or on a computer (desktop: "גם במחשב אחר")
    const val offline = "מורידים מ־%1\$s, בדפדפן או במחשב, את %2\$s ואת %3\$s, ולוחצים \"ייבוא מקבצים…\"."
    const val prompt = "לפתוח את המסך הזה בכל הפעלה, עד שהחיפוש לפי משמעות מותקן"
    const val noLibrary = "צריך להתקין קודם את הספרייה."
    const val download = "הורדה והתקנה"
    const val update = "עדכון לגרסה %1\$s"
    const val import = "ייבוא מקבצים…"
    const val releasesPage = "לדף ההורדות"
    const val cancel = "ביטול"
    const val dismiss = "סגירה"
    const val downloading = "מוריד: %1\$s מתוך %2\$s"
    const val unpacking = "פורס ובודק את הקבצים…"
    const val ready = "הקבצים מוכנים, והחיפוש לפי משמעות יפעל מההפעלה הבאה של פרדס."
    const val restart = "הפעלה מחדש עכשיו"
    const val startFailed = "בהפעלה הקודמת הקבצים לא הותקנו, ומה שהיה מותקן נשאר. אפשר לנסות שוב."
    const val problemCorrupted = "הקובץ %1\$s פגום או חלקי. אפשר להוריד אותו שוב."
    const val problemUnusable = "אי אפשר להשתמש בקבצים האלה (%1\$s)."
    const val problemFailed = "ההתקנה נכשלה (%1\$s)."

    // Android: the library's storage, the phone's or the card's (desktop: "בכונן של הספרייה")
    const val problemNoSpace = "אין מספיק מקום בזיכרון שבו שמורה הספרייה: צריך %1\$s, פנויים %2\$s."

    // Android: the picker gives the files picked, not their folder
    const val importHint =
        "בייבוא בוחרים יחד את כל הקבצים שהורדו: את שני קובצי ה־zip, או את pardes-semantic.tar.zst.manifest.json עם החלקים שלו."
    const val problemNotAPack =
        "הקובץ %1\$s אינו מקובצי החיפוש לפי משמעות. בוחרים יחד את pardes-model.zip ואת pardes.vec.lucene.zip, או את " +
            "pardes-semantic.tar.zst.manifest.json עם החלקים שלו, שהורדו מדף ההורדות."
    const val problemMissing = "חסר הקובץ %1\$s. בוחרים אותו יחד עם שאר הקבצים."

    // Android: the notification of a download or an import that goes on in the background
    const val failedNotification = "התקנת הקבצים לא הושלמה. הפרטים בהגדרות › ניהול נתונים."
}

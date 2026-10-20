package io.github.kdroidfilter.seforimapp.legacy.ui

/**
 * This edition's own texts for the install from files, in the main app's tone: the main app picks files with the
 * system picker only, and has no file browser nor a library folder to move in. The steps' titles, the progress, the
 * errors and the end are the main app's own (DesktopStrings, AndroidStrings, installErrorMessage). File types are named
 * without their leading dot, which Android 4.4 draws on the wrong side ([Bidi.marks]).
 */
internal object InstallTexts {
    const val TITLE = "התקנה מקבצים"
    const val NO_LIBRARY_INTRO =
        "אפשר להתקין ספרייה מקבצים שבטלפון או בכרטיס הזיכרון: קבצי part של החבילה (עם קובץ manifest.json), " +
            "קובץ tar.zst, קובץ seforim.db.zst, או תיקייה שיש בה ספרייה של זית למחשב."
    const val ALSO =
        "אפשר לבחור גם קובץ tar.zst אחד, קובץ seforim.db.zst (עם release_info.txt שלידו), או תיקייה שיש בה ספרייה: " +
            "seforim.db והקבצים שלצדו."
    const val SYSTEM_PICKER = "בבורר הקבצים של המערכת"
    const val FOUND = "נמצאו במכשיר"
    const val PLACES = "מקומות"
    const val APP_FOLDER = "תיקיית פרדס (%1\$s)"
    const val PHONE_STORAGE = "זיכרון הטלפון"
    const val CARD = "כרטיס הזיכרון"
    const val DOWNLOADS = "הורדות"
    const val PICK_FOLDER = "בחירת התיקייה הזו"
    const val UP = "לתיקייה שמעל"
    const val EMPTY = "אין כאן תיקיות או קבצים להתקנה."
    const val UNREADABLE = "לא ניתן לקרוא את התיקייה הזו."
    const val NO_PERMISSION = "בלי הרשאת הגישה לאחסון אפשר לבחור רק מתיקיות פרדס ובבורר הקבצים של המערכת."
    const val NOTHING_HERE = "בתיקייה הזו אין ספרייה (seforim.db) ואין קבצים להתקנה."

    /** A folder's library: seforim.db's folder, its size. */
    const val LIBRARY_IN = "ספרייה בתיקייה %1\$s"
    const val PARTS = "%1\$d חלקים"
    const val MOVE_NOTE =
        "הספרייה שבתיקייה %1\$s תועבר לתיקיית הספרייה של פרדס. כשהתיקייה באחסון אחר (למשל בכרטיס הזיכרון), " +
            "הספרייה תועתק אליה ותישאר גם במקומה."
    const val CONTINUE = "המשך"
    const val ALREADY_LIBRARY = "התיקייה שנבחרה היא תיקיית הספרייה של פרדס."
    const val BACKGROUND = "ניתן לצאת מהיישום: ההתקנה תמשיך ברקע."
    const val INSTALLING = "התקנת הספרייה פועלת כעת."
    const val SHOW_INSTALL = "הצגת ההתקנה"
}

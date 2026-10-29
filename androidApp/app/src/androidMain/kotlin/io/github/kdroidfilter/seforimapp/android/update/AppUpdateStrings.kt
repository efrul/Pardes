package io.github.kdroidfilter.seforimapp.android.update

/**
 * Desktop 0.6.10 strings.xml's update_* texts (Nucleus updater: title-bar badge, dialog, the settings banner and its
 * quiet line), with the app named פרדס, and what Android adds around them: Android's own install confirmation, its
 * "install unknown apps" switch, the download in the background, and the checks the downloaded APK must pass.
 */
internal object AppUpdateStrings {
    const val available = "עדכון זמין" // update_titlebar_tooltip, update_dialog_title
    const val title = "עדכוני פרדס"
    const val dialogMessage = "גרסה %1\$s של פרדס זמינה." // update_dialog_message
    const val availableBanner = "גרסה %1\$s זמינה להורדה" // update_available_banner
    const val downloadAndInstall = "הורד והתקן"
    const val install = "התקן עכשיו"
    const val later = "מאוחר יותר" // update_later
    const val downloading = "מוריד…" // update_downloading
    const val downloadingPercent = "מוריד… %1\$d%%"
    const val verifying = "בודק את הקובץ שהורד…"
    const val installing = "מתקין את העדכון… בסוף ההתקנה פרדס ייסגר."
    const val confirming = "אם אנדרואיד מבקש לאשר את ההתקנה, אשרו אותה. אם חלון האישור נסגר, הקישו על \"המשך בהתקנה\"."
    const val continueInstall = "המשך בהתקנה"
    const val stopDownload = "עצור את ההורדה"
    const val dbWarning = "ייתכן שלאחר עדכון זה יהיה צורך לעדכן גם את מסד הנתונים." // update_dialog_db_warning
    const val upToDate = "האפליקציה מעודכנת לגרסה האחרונה" // update_up_to_date
    const val check = "בדוק עדכונים"
    const val checkAgain = "בדוק שוב" // update_check_action
    const val checking = "בודק עדכונים…" // update_checking
    const val checkFailed = "לא ניתן לבדוק עדכונים — ודא שקיים חיבור לאינטרנט" // update_check_failed
    const val notChecked = "לחצו על הכפתור כדי לבדוק אם יצאה גרסה חדשה של פרדס."
    const val size = "גודל ההורדה: %1\$s"
    const val whatsNew = "מה חדש"
    const val versionHeading = "גרסה %1\$s"
    const val whatsNewLink = "יומן השינויים באתר פרדס"
    const val ready = "גרסה %1\$s הורדה ונבדקה, והיא מוכנה להתקנה."
    const val installHint =
        "העדכון מותקן מעל הגרסה הנוכחית, והספרייה וההגדרות נשמרות (וגם הלשוניות, כששמירת המצב בין הפעלות מופעלת). " +
            "בסוף ההתקנה פרדס נסגר, ויש לפתוח אותו מחדש."
    const val permission =
        "כדי להתקין את העדכון יש לאשר לפרדס להתקין אפליקציות: הקישו על \"פתח הגדרות\", הפעילו שם את \"לאפשר מהמקור הזה\" וחזרו לכאן, וההתקנה תמשיך מעצמה."
    const val openSettings = "פתח הגדרות"
    const val cancel = "ביטול"
    const val dismiss = "סגור"
    const val cancelled = "ההתקנה בוטלה. אפשר להתקין את העדכון מאוחר יותר."
    const val libraryBusy =
        "לא ניתן לעדכן את פרדס בזמן שהספרייה מותקנת, מועברת או מתעדכנת: ההתקנה סוגרת את היישום. נסו שוב כשהפעולה תסתיים."
    const val noSpace = "אין מספיק שטח פנוי להורדת העדכון: נדרשים %1\$s, פנויים %2\$s."
    const val downloadFailed = "הורדת העדכון נכשלה: %1\$s"
    const val corrupt = "הקובץ שהורד פגום. נסו להוריד אותו שוב."
    const val notReadable = "אנדרואיד לא הצליח לקרוא את הקובץ שהורד, ולכן הוא לא יותקן."
    const val notPardes = "הקובץ שהורד אינו של פרדס, ולכן לא יותקן."
    const val otherSigner = "הקובץ שהורד אינו חתום במפתח של פרדס המותקן, ולכן לא יותקן."
    const val wrongVersion = "הקובץ שהורד אינו גרסה %1\$s, ולכן לא יותקן."
    const val older = "הקובץ שהורד אינו חדש מהגרסה המותקנת, ולכן לא יותקן."
    const val incompatible = "העדכון דורש גרסת אנדרואיד חדשה מזו שבמכשיר."
    const val installFailed = "התקנת העדכון נכשלה: %1\$s"
    const val installConflict = "אנדרואיד סירב להתקין את העדכון בגלל התנגשות עם יישום מותקן (%1\$s)."
    const val withdrawnForLibrary = "ההתקנה בוטלה כי התחילה פעולה בספרייה. אפשר להתקין את העדכון כשהיא תסתיים."
    const val installStorage = "אין מספיק מקום פנוי להתקנת העדכון."
    const val installIncompatible = "העדכון אינו מתאים למכשיר הזה."
    const val installBlocked = "אנדרואיד חסם את התקנת העדכון."
    const val installInvalid = "אנדרואיד לא קיבל את הקובץ שהורד."
    const val devBuild =
        "גרסת פיתוח (חתומה במפתח הפיתוח): היא אינה מתעדכנת מתוכה, ואי אפשר להתקין מעליה גרסה רשמית. " +
            "מעבר לגרסה הרשמית מחייב להסיר את גרסת הפיתוח, וההסרה מוחקת את הספרייה ואת ההגדרות."
    const val site = "אתר פרדס"
    const val found = "גרסה %1\$s של פרדס זמינה"
    const val details = "פרטים"
    const val updatedTo = "פרדס עודכן לגרסה %1\$s"
    const val tapToOpen = "הקישו כדי לפתוח"
    const val readyTitle = "העדכון לפרדס מוכן להתקנה"
    const val readyText = "הקישו כדי לפתוח את העדכון לגרסה %1\$s"
    const val confirmTitle = "התקנת העדכון לפרדס ממתינה לאישור"
    const val confirmText = "הקישו כדי להמשיך בהתקנה"
    const val downloadingTitle = "מוריד את העדכון לפרדס"
    const val failedTitle = "עדכון פרדס נכשל"
    const val channel = "עדכון האפליקציה"
    // Desktop 0.6.12 settings_auto_update_check
    const val autoCheckTitle = "בדיקת עדכונים אוטומטית בהפעלה"
    const val autoCheckDescription =
        "כאשר אפשרות זו מופעלת, פרדס בודק בכל הפעלה אם יצאה גרסה חדשה (בקשה קטנה לרשימת הגרסאות של פרדס ב-GitHub), " +
            "ומודיע על כך בסרגל העליון. ההורדה וההתקנה רק באישורכם."
}

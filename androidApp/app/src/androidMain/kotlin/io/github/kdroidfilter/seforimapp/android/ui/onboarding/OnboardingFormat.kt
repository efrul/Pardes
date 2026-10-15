package io.github.kdroidfilter.seforimapp.android.ui.onboarding

import java.util.Locale

/** Sizes as desktop prints them (FormatUtils.formatBytes): powers of 1024, two decimals, Hebrew unit. */
internal fun formatBytes(bytes: Long): String {
    val units = listOf(
        DesktopStrings.bytesUnitB,
        DesktopStrings.bytesUnitKb,
        DesktopStrings.bytesUnitMb,
        DesktopStrings.bytesUnitGb,
        DesktopStrings.bytesUnitTb,
    )
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.2f %s", value, units[unitIndex])
}

internal fun formatBytesPerSec(bytesPerSec: Long): String = DesktopStrings.bytesPerSecondPattern.format(formatBytes(bytesPerSec))

/** "2 דקות 14 שניות", as desktop's formatEta. */
internal fun formatEta(totalSeconds: Long): String {
    val secs = totalSeconds.coerceAtLeast(0L)
    val hours = secs / 3600
    val minutes = (secs % 3600) / 60
    val seconds = secs % 60
    val parts = mutableListOf<String>()
    if (hours > 0) {
        parts += String.format(Locale.US, "%d %s", hours, if (hours == 1L) DesktopStrings.etaHoursUnitSingular else DesktopStrings.etaHoursUnitPlural)
    }
    if (minutes > 0) {
        parts += String.format(Locale.US, "%d %s", minutes, if (minutes == 1L) DesktopStrings.etaMinutesUnitSingular else DesktopStrings.etaMinutesUnitPlural)
    }
    if (seconds > 0 || parts.isEmpty()) {
        parts += String.format(Locale.US, "%d %s", seconds, if (seconds == 1L) DesktopStrings.etaSecondsUnitSingular else DesktopStrings.etaSecondsUnitPlural)
    }
    return parts.joinToString(" ")
}

/** Seconds left at the current speed, or null while the speed is unknown. */
internal fun etaSeconds(downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long): Long? {
    if (speedBytesPerSec <= 0L) return null
    val remaining = (totalBytes - downloadedBytes).coerceAtLeast(0L)
    return (remaining + speedBytesPerSec - 1) / speedBytesPerSec
}

/**
 * Texts that only exist on Android: what the phone does differently from desktop (streamed install,
 * picking every part at once, a library pushed to the device, metered networks), and the welcome subtitle,
 * which says this app is Pardes, based on Zayit, instead of who built desktop Zayit. Same tone as desktop.
 */
internal object AndroidStrings {
    const val welcomeSubtitle =
        "יישום חדש, מודרני ומהיר ללימוד התורה. פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו: " +
            "הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה. פרדס שואף לחולל מהפכה בלימוד טקסטים דיגיטליים באמצעות חוויה חלקה, אלגנטית וללא צורך בחיבור לאינטרנט."
    const val installationOfflineDesc =
        "עליכם לבחור קובץ מסד נתונים מוכן שהורד מראש מעמוד ההפצות האחרון. הורידו את כל הקבצים עם הסיומות ‎.part01, ‎.part02 וכו׳ " +
            "מעמוד ההפצה (וכן את קובץ ‎.manifest.json אם קיים), ולאחר מכן בחרו כאן את כולם יחד להמשך ההתקנה."
    const val installationOnlineDesc =
        "נדרש חיבור לאינטרנט להורדת מסד הנתונים הדחוס (%1\$s). הקובץ נפרס ומותקן מקומית במכשיר תוך כדי ההורדה. משך הפעולה תלוי במהירות ההורדה והאחסון."
    const val selectFilesMessage =
        "אנא בחרו יחד את כל קבצי ‎.part של מסד הנתונים (וכן את קובץ ‎.manifest.json אם קיים). לחיצה ארוכה על קובץ מאפשרת לבחור כמה קבצים. " +
            "ניתן גם לבחור קובץ seforim.db מוכן, יחד עם קובץ release_info.txt שלידו."
    const val filesSelected = "נבחרו %1\$d קבצים ✓"
    const val localBundleFound = "נמצאו קבצי מסד נתונים בתיקיית היישום במכשיר."
    const val useLocalBundle = "שימוש בקבצים שנמצאו"
    const val existingLibraryFound = "נמצאה במכשיר ספרייה מותקנת (%1\$s)."
    const val useExistingLibrary = "שימוש בספרייה הקיימת"
    const val diskStreamingNote =
        "במכשיר נייד הספרייה נפרסת תוך כדי ההורדה, כך שהקובץ הדחוס (%1\$s) אינו נשמר באחסון. " +
            "נדרש מקום לספרייה עצמה, %2\$s (מסד הנתונים %3\$s ואינדקס החיפוש %4\$s), ולמרווח ביטחון של %5\$s."
    const val diskRequired = "נדרשים %1\$s פנויים. פנויים כעת: %2\$s."
    const val meteredWarning = "שימו לב: המכשיר מחובר כעת לרשת סלולרית או מוגבלת. ההורדה (%1\$s) עלולה לצרוך חבילת גלישה."
    const val reconnecting = "החיבור נקטע. מנסה להתחבר מחדש…"
    const val backgroundHint = "ניתן לצאת מהיישום: ההורדה תמשיך ברקע."
}

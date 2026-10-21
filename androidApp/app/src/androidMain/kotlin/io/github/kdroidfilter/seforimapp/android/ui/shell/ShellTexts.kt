// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforimapp.android.ui.shell

/**
 * The shell's texts that the Android 4.4 edition shows too, kept here once: the title bar's theme item, the book list's
 * title and its search's empty result, and the history's and favourites' empty states, links and clearing (their
 * pages, their sheets and their Home widgets).
 */
internal object ShellTexts {
    /** Desktop light_theme / dark_theme / system_theme: the theme in use, as the title bar's menu names it. */
    const val THEME_LIGHT = "ערכת נושא בהירה"
    const val THEME_DARK = "ערכת נושא כהה"
    const val THEME_SYSTEM = "ערכת נושא מערכת"

    const val BOOK_LIST = "רשימת הספרים"
    const val NO_BOOKS_FOUND = "לא נמצאו ספרים"

    const val HISTORY_EMPTY = "ההיסטוריה ריקה"
    const val FAVORITES_EMPTY = "אין מועדפים"

    /** A sheet's search that found nothing. */
    const val NO_RESULTS = "אין תוצאות"
    const val SHOW_ALL_HISTORY = "הצג את כל ההיסטוריה"
    const val SHOW_ALL_FAVORITES = "הצג את כל המועדפים"

    /** A row's × (no question asked for one row). */
    const val DELETE = "מחק"

    const val CLEAR_HISTORY = "נקה היסטוריה"
    const val CLEAR_HISTORY_TITLE = "ניקוי היסטוריה"
    const val CLEAR_HISTORY_MESSAGE = "האם אתה בטוח שברצונך למחוק את כל ההיסטוריה? פעולה זו אינה ניתנת לביטול."
    const val CLEAR_HISTORY_CONFIRM = "כן, נקה"
}

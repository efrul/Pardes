package io.github.kdroidfilter.seforimapp.branding

/** Upstream Zayit's names, links and file names (branding/zayit, -Pseforimapp.brand=zayit): the literals upstream has. */
object BrandInfo {
    /** The GitHub button of Settings › כללי. */
    const val SOURCE_URL = "https://github.com/kdroidFilter/Zayit"

    /** The last line of an error report mailed to Sefaria. */
    const val ERROR_REPORT_SIGNATURE = "נשלח מתוך זית (https://zayitapp.com/)"

    /** The start of an exported backup's file name. */
    const val BACKUP_FILE_PREFIX = "zayit_backup_"

    /** The launcher entry whose Linux quicklist the app fills. */
    const val LINUX_DESKTOP_FILE_ID = "zayit.desktop"

    /** The User-Agent of the library downloads. */
    const val USER_AGENT = "SeforimApp/1.0 (+https://github.com/kdroidFilter/SeforimApp)"

    /** The folder a library gets inside a folder the user chooses for it (LibraryLocation.destination). */
    const val LIBRARY_FOLDER_NAME = "Zayit library"
}

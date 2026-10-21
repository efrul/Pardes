package io.github.kdroidfilter.seforimapp.branding

/**
 * Pardes for desktop's names, links and file names, read by the app's code where upstream has Zayit's (branding/zayit
 * has those). The app's displayed name is the `app_name` string (composeResources); who made the app: see the name and
 * authorship rules of the zayit-android skill.
 */
object BrandInfo {
    /** The app's own source code (AGPL), opened by the GitHub button of Settings › כללי. */
    const val SOURCE_URL = "https://github.com/efrul/Pardes"

    /** The last line of an error report mailed to Sefaria. */
    const val ERROR_REPORT_SIGNATURE = "נשלח מתוך פרדס למחשב, המבוסס על זית"

    /** The start of an exported backup's file name. Import reads any `.zip` or `.db` backup, Zayit's included. */
    const val BACKUP_FILE_PREFIX = "pardes_backup_"

    /** The launcher entry whose Linux quicklist the app fills: brand.properties' packageName + ".desktop". */
    const val LINUX_DESKTOP_FILE_ID = "pardes.desktop"

    /** The User-Agent of the library downloads. */
    const val USER_AGENT = "Pardes/1.0 (+https://github.com/efrul/Pardes)"
}

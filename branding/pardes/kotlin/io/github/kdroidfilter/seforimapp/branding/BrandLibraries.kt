package io.github.kdroidfilter.seforimapp.branding

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import java.io.File
import java.util.prefs.Preferences

/**
 * The library Pardes for desktop offers, on first run, to use in place (branding/pardes): an installed desktop Zayit's,
 * which Pardes then opens where it is, without a copy, as efrul/zayit-move points Zayit at a library. Pardes never
 * deletes it (`ForeignLibraries`). branding/zayit offers none: Zayit does not look for itself.
 */
object BrandLibraries {
    /** The app whose library is offered, as the onboarding names it. */
    const val OTHER_APP_NAME = "זית למחשב"

    /** Zayit's app id: FileKit's data folder of desktop Zayit (its Brand.APP_ID). */
    private const val ZAYIT_APP_ID = "io.github.kdroidfilter.seforimapp"

    /** Zayit's preference holding its library's seforim.db (its AppSettings). */
    private const val DATABASE_PATH_KEY = "database_path"

    private const val DATABASE_NAME = "seforim.db"

    /**
     * Where desktop Zayit keeps its library's seforim.db, in the order its DatabasePathProvider looks:
     * - its "database_path" preference, in java.util.prefs' user root (Zayit's `Settings()`), which Pardes reads
     *   directly because its own preferences are a node of their own (`Brand.settings()`). Zayit ignores a path to
     *   lexical.db, and so does this;
     * - its default folder, FileKit's databasesDir under Zayit's app id: `filesDir/databases`, where filesDir is
     *   `%APPDATA%\<app id>` on Windows and `$XDG_DATA_HOME/<app id>` or `~/.local/share/<app id>` on Linux, so the
     *   folder beside Pardes's own data folder.
     *
     * SEFORIMAPP_DATABASE_PATH, which Zayit reads first, is left out: Pardes reads that variable as its own.
     */
    fun otherAppDatabases(): List<File> {
        val setting =
            runCatching { Preferences.userRoot().get(DATABASE_PATH_KEY, "") }
                .getOrNull()
                ?.trim()
                ?.takeUnless { it.isEmpty() || it.endsWith("lexical.db", ignoreCase = true) }
                ?.let(::File)
        val inDefaultFolder =
            runCatching {
                File(FileKit.filesDir.path).resolveSibling(ZAYIT_APP_ID).resolve("databases").resolve(DATABASE_NAME)
            }.getOrNull()
        return listOfNotNull(setting, inDefaultFolder).distinct()
    }

    /**
     * Where desktop Zayit keeps its user database (its searches' history, which the user may send with Pardes's own,
     * `ZayitSearches`): `databases/settings/user_settings.db` under its data folder, beside Pardes's own; and, for a
     * Zayit installed from the Microsoft Store, under its package's roaming folder.
     */
    fun otherAppUserDatabases(): List<File> {
        val inDataFolder =
            runCatching { File(FileKit.filesDir.path).resolveSibling(ZAYIT_APP_ID).resolve(USER_DATABASE) }.getOrNull()
        val inStorePackages =
            runCatching {
                File(System.getenv("LOCALAPPDATA").orEmpty(), "Packages")
                    .listFiles { file -> file.isDirectory && file.name.startsWith(ZAYIT_STORE_PACKAGE) }
                    .orEmpty()
                    .map { it.resolve("LocalCache/Roaming").resolve(ZAYIT_APP_ID).resolve(USER_DATABASE) }
            }.getOrDefault(emptyList())
        return (listOfNotNull(inDataFolder) + inStorePackages).distinct()
    }

    private const val USER_DATABASE = "databases/settings/user_settings.db"

    /** The folder of a Zayit installed from the Microsoft Store begins with this, under `%LOCALAPPDATA%\Packages`. */
    private const val ZAYIT_STORE_PACKAGE = "KdroidFilter.Zayit_"
}

package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.branding.BrandLibraries
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File

/**
 * Another app's library on this machine, which this app may use in place instead of installing its own: the
 * onboarding offers it ([find]) and, when the user accepts, points `database_path` at it ([useInPlace]), with no copy.
 * Which app, and where it keeps its library, comes from `BrandLibraries` (none for an app that offers nothing). A
 * library in a folder the user chooses in the onboarding ([inFolder]) is used in place the same way.
 *
 * Such a library is never this app's to delete. [isForeign] makes the full install's cleanup
 * ([io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase]), its retry
 * ([PendingDbCleanup]) and the reset leave its folder alone: a full update then installs the new library in this app's
 * own folder and points `database_path` there, and a reset only forgets the path. Delta updates still apply in place,
 * and the other app sees them through the same content hash check.
 */
object ForeignLibraries {
    /** Records the folders of the libraries used in place, one per line, in this app's own databases folder. */
    const val MARKER_NAME = "libraries-in-place.txt"

    private const val RELEASE_INFO_NAME = "release_info.txt"
    private const val DATABASE_NAME = "seforim.db"
    private const val SEARCH_INDEX_NAME = "seforim.db.lucene"

    // The owner of a library in a folder the user chose, for the log
    private const val CHOSEN_FOLDER = "a chosen folder"

    /** A library found in [database]'s folder, of release [version] (yyyyMMddHHmmss), belonging to [ownerName]. */
    data class ForeignLibrary(
        val database: File,
        val version: String,
        val ownerName: String,
    )

    /** What a folder the user chose holds: a library this app can open, or why it cannot open what is there. */
    sealed interface InFolder {
        data class Library(
            val library: ForeignLibrary,
        ) : InFolder

        /** No seforim.db there, nor in the library's own folder inside it. */
        data object NoLibrary : InFolder

        /** seforim.db cut into multiplex pieces (a FAT32 phone card's format). */
        data object InPieces : InFolder

        /** seforim.db without its search index: a copy of the one file, not of the library. */
        data object NoSearchIndex : InFolder

        /** No release_info.txt, or one that names no release. */
        data object UnknownRelease : InFolder

        /** A release ([version], yyyyMMddHHmmss) older than this app's minimum. */
        data class TooOld(
            val version: String,
        ) : InFolder
    }

    /** The first library of the other app that this app can open, or null. Cheap: a preference and a few stats. */
    fun find(): ForeignLibrary? = otherAppDatabases().firstNotNullOfOrNull(::usableOrNull)

    /**
     * The library of the folder [chosen] by the user: in the folder itself, else in the library's own folder inside
     * it, where an install to a chosen folder puts it ([LibraryLocation.destination]). A library to [useInPlace], or
     * why this app cannot open it.
     */
    fun inFolder(chosen: File): InFolder {
        val dir = listOf(chosen, LibraryLocation.destination(chosen)).firstOrNull(::holdsDatabase)
        val version = dir?.let(LibraryLocation::version)
        val minimum = DatabaseVersionManager.getMinimumRequiredVersion()
        return when {
            dir == null -> InFolder.NoLibrary
            LibraryLocation.isSplit(dir) -> InFolder.InPieces
            !File(dir, SEARCH_INDEX_NAME).isDirectory -> InFolder.NoSearchIndex
            version == null || version.length != minimum.length || !version.all(Char::isDigit) -> InFolder.UnknownRelease
            version < minimum -> InFolder.TooOld(version)
            else -> InFolder.Library(ForeignLibrary(File(dir, DATABASE_NAME), version, CHOSEN_FOLDER))
        }
    }

    /** Points this app at [library] where it is, and records its folder as one this app never deletes. */
    fun useInPlace(
        library: ForeignLibrary,
        appSettings: AppSettings,
        databasePathProvider: DatabasePathProvider,
    ) {
        library.database.parentFile?.let(::record)
        appSettings.setDatabasePath(library.database.absolutePath)
        databasePathProvider.reset()
        infoln { "[ForeignLibraries] Using the library of ${library.ownerName} in place: ${library.database.absolutePath}" }
    }

    /**
     * True when [dir] holds another app's library: one this app uses (or used) in place, or a folder where the other
     * app keeps its library. This app's own databases folder is never foreign.
     */
    fun isForeign(dir: File?): Boolean {
        val target = dir?.let(::canonical) ?: return false
        if (target == ownDir()) return false
        return (recorded() + otherAppDatabases().mapNotNull { it.parentFile }).any { canonical(it) == target }
    }

    /**
     * A library this app can open in [database]'s folder: the file itself, not cut in pieces, and a release
     * (release_info.txt) at least [DatabaseVersionManager]'s minimum.
     */
    private fun usableOrNull(database: File): ForeignLibrary? {
        val dir = database.parentFile
        if (dir == null || !database.isFile) return null
        // seforim.db, seforim.db001, ...: split by SQLite's multiplex VFS (as a phone keeps it on a FAT32 card),
        // which this app's SQLite cannot open
        if (File(dir, database.name + "001").exists()) return null
        val version = runCatching { File(dir, RELEASE_INFO_NAME).readText().trim() }.getOrNull() ?: return null
        val minimum = DatabaseVersionManager.getMinimumRequiredVersion()
        val recentEnough = version.length == minimum.length && version.all(Char::isDigit) && version >= minimum
        return if (recentEnough) ForeignLibrary(database, version, BrandLibraries.OTHER_APP_NAME) else null
    }

    // seforim.db in [dir], whole or cut in pieces
    private fun holdsDatabase(dir: File): Boolean = File(dir, DATABASE_NAME).isFile || LibraryLocation.isSplit(dir)

    private fun otherAppDatabases(): List<File> = runCatching { BrandLibraries.otherAppDatabases() }.getOrDefault(emptyList())

    private fun canonical(file: File): File? = runCatching { file.canonicalFile }.getOrNull()

    private fun ownDir(): File? = runCatching { File(FileKit.databasesDir.path).canonicalFile }.getOrNull()

    private fun markerFile(): File? = runCatching { File(File(FileKit.databasesDir.path), MARKER_NAME) }.getOrNull()

    private fun recorded(): List<File> =
        markerFile()
            ?.takeIf { it.exists() }
            ?.let { marker -> runCatching { marker.readLines() }.getOrNull() }
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::File)

    private fun record(dir: File) {
        val marker = markerFile() ?: return
        val all = (recorded() + dir).map { it.absolutePath }.toSortedSet()
        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText(all.joinToString("\n"))
        }.onFailure { warnln { "[ForeignLibraries] Could not write marker: ${it.message}" } }
    }
}

package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.branding.BrandInfo
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Where the library is, and where it goes. The library can live in any folder, on any drive: a full install or
 * update goes to [installDir] (the app's own databases folder unless the user chose another), and [move] carries an
 * installed library from one folder to another, or copies it and keeps the original. Notes, settings and sessions are
 * not the library: they stay in the app's own folder whatever happens here.
 *
 * A chosen folder holds the library in a folder of its own ([destination]), since a full install deletes every
 * database file next to the library it replaces. Moving copies, checks every size, points `database_path` at the copy
 * and only then deletes the original: until the app is pointed at the copy nothing has changed, and a failure or a
 * stop before that removes the partial copy. On the same drive the files are renamed instead, with no copy. It runs
 * at startup, before anything opens the library ([PendingLibraryMove]).
 */
object LibraryLocation {
    /**
     * What a library is made of (what the install unpacks), and the open semantic search's model and vector index,
     * which the app looks for next to it (IndependentSemanticSearch): a move that left them behind would turn the
     * semantic search off without a word.
     */
    val NAMES =
        listOf(
            "seforim.db",
            "seforim.db.lucene",
            "seforim.db.lookup.lucene",
            "lexical.db",
            "catalog.pb",
            "release_info.txt",
            "subset_info.txt",
            "pardes-model",
            "pardes.vec.lucene",
        )

    /** What the app leaves next to the library while it is open. A WAL with content belongs to the database. */
    private val LEFTOVERS = listOf("seforim.db-wal", "seforim.db-shm", "seforim.db-journal", "delta-cache")
    private const val WAL_NAME = "seforim.db-wal"

    /** The folders that hold a kept copy of the library, one per line, in the app's own databases folder. */
    private const val COPIES_NAME = "library-copies.txt"

    /** Room left on the target drive beyond the library itself. */
    private const val MARGIN_BYTES = 200L shl 20

    /** The largest file FAT32 can hold; seforim.db is about 8 GB. */
    private const val FAT32_MAX_BYTES = 0xFFFF_FFFFL

    private const val COPY_BUFFER = 4 shl 20

    /** Why a library cannot go where it was asked to. */
    sealed interface Problem {
        data object NoLibrary : Problem

        data object AlreadyThere : Problem

        data object IntoItself : Problem

        /** [dir] already holds a library, or part of one ([names]). */
        data class LibraryThere(
            val dir: String,
            val names: List<String>,
        ) : Problem

        /** [root] is FAT32: no file of 4 GiB or more. */
        data class Fat32(
            val root: String,
        ) : Problem

        data class DriveMissing(
            val root: String,
        ) : Problem

        /** The app may not write in [dir] (a system folder, a read-only drive). */
        data class NotWritable(
            val dir: String,
        ) : Problem

        data class NoSpace(
            val root: String,
            val neededBytes: Long,
            val freeBytes: Long,
        ) : Problem

        /** The copy came out [copiedBytes] instead of [expectedBytes]; the library stayed where it was. */
        data class CopyMismatch(
            val copiedBytes: Long,
            val expectedBytes: Long,
        ) : Problem

        data class Failed(
            val message: String,
        ) : Problem
    }

    class MoveException(
        val problem: Problem,
    ) : IOException(problem.toString())

    /** Thrown when the user stops a move; the library stays where it was. */
    class MoveStopped : IOException("Library move stopped")

    /** The outcome of a move: where the library is now, and what could not be deleted from the old place. */
    data class MoveResult(
        val to: File,
        val bytes: Long,
        val sameDrive: Boolean,
        val keptCopy: Boolean,
        val notDeleted: List<String>,
    )

    /** The app's own databases folder. */
    fun defaultDir(): File = File(FileKit.databasesDir.path).absoluteFile

    /** The folder a full install or update goes to: the one the user chose, else the app's own. */
    fun installDir(appSettings: AppSettings): File = appSettings.getLibraryInstallDir()?.let { File(it).absoluteFile } ?: defaultDir()

    /** The folder of the library the app opens now. */
    fun currentDir(appSettings: AppSettings): File = appSettings.getDatabasePath()?.let { File(it).absoluteFile.parentFile } ?: defaultDir()

    /**
     * Where the library goes when [chosen] is picked: the app's own folder as it is, a folder named for the library
     * as it is, anywhere else a new folder of that name inside it.
     */
    fun destination(chosen: File): File {
        val dir = chosen.absoluteFile
        return when {
            same(dir, defaultDir()) -> defaultDir()
            dir.name.equals(BrandInfo.LIBRARY_FOLDER_NAME, ignoreCase = true) -> dir
            else -> File(dir, BrandInfo.LIBRARY_FOLDER_NAME)
        }
    }

    /** A library the app can open: seforim.db whole, not cut into multiplex pieces (a FAT32 phone card's format). */
    fun exists(dir: File): Boolean = File(dir, "seforim.db").isFile && !isSplit(dir)

    fun isSplit(dir: File): Boolean = File(dir, "seforim.db001").exists()

    /** The library's bytes in [dir]. */
    fun size(dir: File): Long = libraryFiles(dir).sumOf { it.length() }

    /** The library's release (yyyyMMddHHmmss, from release_info.txt), or null. */
    fun version(dir: File): String? = runCatching { File(dir, "release_info.txt").readText().trim() }.getOrNull()?.ifBlank { null }

    /** Free bytes on the drive of [dir] (or of its nearest existing parent); -1 when the drive is not there. */
    fun freeBytes(dir: File): Long = nearestExisting(dir)?.usableSpace ?: -1L

    /** Total bytes of the drive of [dir]; -1 when the drive is not there. */
    fun totalBytes(dir: File): Long = nearestExisting(dir)?.totalSpace ?: -1L

    /** The drive or mount point [dir] is on, as the user knows it ("D:", "/media/user/disk"). */
    fun driveOf(dir: File): String =
        runCatching {
            val existing =
                nearestExisting(dir) ?: return dir
                    .toPath()
                    .root
                    ?.toString()
                    .orEmpty()
            val store = Files.getFileStore(existing.toPath())
            val root =
                existing
                    .toPath()
                    .root
                    ?.toString()
                    .orEmpty()
            if (root.length > 1 && root[1] == ':') root.trimEnd('\\', '/') else store.toString().substringBefore(" (")
        }.getOrDefault("")

    /** FAT (FAT32, FAT16) on the drive of [dir]: no file of 4 GiB or more. exFAT and NTFS are fine. */
    fun isFat(dir: File): Boolean =
        runCatching {
            val type = Files.getFileStore((nearestExisting(dir) ?: return false).toPath()).type().lowercase()
            type == "vfat" || type == "msdos" || type.startsWith("fat")
        }.getOrDefault(false)

    /** Why the library in [from] cannot go to [to] (a [destination]), or null when it can. */
    fun check(
        from: File,
        to: File,
        keep: Boolean,
    ): Problem? {
        val source = from.absoluteFile
        val target = to.absoluteFile
        val sameDrive = sameDrive(source, target)
        val total = size(source)
        val there = NAMES.filter { File(target, it).exists() }
        return when {
            !exists(source) -> Problem.NoLibrary
            same(source, target) -> Problem.AlreadyThere
            target.path.startsWith(source.path + File.separator) -> Problem.IntoItself
            there.isNotEmpty() || isSplit(target) -> Problem.LibraryThere(target.path, there)
            nearestExisting(target) == null -> Problem.DriveMissing(driveOf(target))
            !writable(target) -> Problem.NotWritable(nearestExisting(target)?.path ?: target.path)
            !sameDrive && isFat(target) && largest(source) > FAT32_MAX_BYTES -> Problem.Fat32(driveOf(target))
            (keep || !sameDrive) && freeBytes(target) < total + MARGIN_BYTES ->
                Problem.NoSpace(driveOf(target), total + MARGIN_BYTES, freeBytes(target))
            else -> null
        }
    }

    /**
     * Makes the library's own folder in [chosen] ([destination]) the folder a full install goes to, or says why it
     * cannot be. A library already there is not overwritten.
     */
    fun chooseInstallDir(
        chosen: File,
        appSettings: AppSettings,
    ): Problem? {
        val target = destination(chosen)
        val isDefault = same(target, defaultDir())
        val there = NAMES.filter { File(target, it).exists() }
        val problem =
            when {
                nearestExisting(target) == null -> Problem.DriveMissing(driveOf(target))
                !writable(target) -> Problem.NotWritable(nearestExisting(target)?.path ?: target.path)
                isFat(target) -> Problem.Fat32(driveOf(target))
                !isDefault && (there.isNotEmpty() || isSplit(target)) -> Problem.LibraryThere(target.path, there)
                else -> null
            }
        if (problem == null) appSettings.setLibraryInstallDir(if (isDefault) null else target.path)
        return problem
    }

    /**
     * Moves the library from [from] to [to] (or copies it, with [keep]), checks it, points the app at it and makes
     * [to] the folder later installs and updates go to; then deletes what stays at [from], unless a copy is kept there
     * or it is another app's library ([ForeignLibraries]), which is only ever copied. Must run while nothing has the
     * library open. Throws [MoveException] or [MoveStopped], the library then being where it was.
     */
    fun move(
        from: File,
        to: File,
        keep: Boolean,
        appSettings: AppSettings,
        progress: (done: Long, total: Long) -> Unit,
        stopped: () -> Boolean,
    ): MoveResult {
        val source = from.absoluteFile
        val target = to.absoluteFile
        val keepSource = keep || ForeignLibraries.isForeign(source)
        check(source, target, keepSource)?.let { throw MoveException(it) }

        val total = size(source)
        val sameDrive = sameDrive(source, target)
        val created = !target.exists()
        target.mkdirs()
        if (!keepSource && sameDrive) {
            rename(source, target)
            progress(total, total)
        } else {
            var copiedWhole = false
            try {
                copy(source, target, total, progress, stopped)
                copiedWhole = true
            } finally {
                // A failure or a stop leaves no partial copy behind
                if (!copiedWhole) removeLibrary(target, created)
            }
            val copied = size(target)
            if (copied != total) {
                removeLibrary(target, created)
                throw MoveException(Problem.CopyMismatch(copied, total))
            }
        }

        dropPendingCleanup(target)
        appSettings.setDatabasePath(File(target, "seforim.db").absolutePath)
        appSettings.setLibraryInstallDir(if (same(target, defaultDir())) null else target.path)
        // On disk before the original goes: a crash must not leave the app pointing at a deleted library
        appSettings.flush()
        forgetCopy(target)
        infoln { "[LibraryLocation] Library ${if (keepSource) "copied" else "moved"} from $source to $target ($total bytes)" }

        val notDeleted =
            when {
                keepSource -> {
                    // The original stays: the user can go back to it at once
                    if (!ForeignLibraries.isForeign(source)) recordCopy(source)
                    emptyList()
                }
                sameDrive -> {
                    LEFTOVERS.forEach { deleteRecursively(File(source, it)) }
                    removeIfEmptyLibraryFolder(source)
                    emptyList()
                }
                else -> {
                    val failed = (NAMES + LEFTOVERS).filterNot { deleteRecursively(File(source, it)) }
                    removeIfEmptyLibraryFolder(source)
                    failed
                }
            }
        return MoveResult(target, total, sameDrive, keepSource, notDeleted)
    }

    /**
     * Ends a move the app was killed in. If the app already points at [to], the move was done and only the deletion
     * at [from] is finished (true). Otherwise it is undone (false): what a copy left at [to] is deleted, parts a
     * rename already carried there go back.
     */
    fun undo(
        from: File,
        to: File,
        keep: Boolean,
        appSettings: AppSettings,
    ): Boolean {
        val source = from.absoluteFile
        val target = to.absoluteFile
        if (same(currentDir(appSettings), target)) {
            if (!keep && !ForeignLibraries.isForeign(source)) {
                (NAMES + LEFTOVERS).forEach { deleteRecursively(File(source, it)) }
                removeIfEmptyLibraryFolder(source)
            }
            return true
        }
        for (name in NAMES + WAL_NAME) {
            val there = File(target, name)
            if (!there.exists()) continue
            val here = File(source, name)
            if (here.exists()) {
                deleteRecursively(there)
            } else {
                runCatching { Files.move(there.toPath(), here.toPath()) }
                    .onFailure { warnln { "[LibraryLocation] Could not move ${there.path} back: ${it.message}" } }
            }
        }
        removeIfEmptyLibraryFolder(target)
        warnln { "[LibraryLocation] Undid a move from $source to $target cut short" }
        return false
    }

    /** The kept copies of the library the app does not use now. */
    fun copies(appSettings: AppSettings): List<File> {
        val current = currentDir(appSettings)
        return recordedCopies().filter { exists(it) && !same(it, current) }
    }

    /** Points the app at a kept copy (nothing is copied); the app opens it after a restart. */
    fun switchTo(
        dir: File,
        appSettings: AppSettings,
    ) {
        val target = dir.absoluteFile
        if (!exists(target)) throw MoveException(Problem.NoLibrary)
        val before = currentDir(appSettings)
        dropPendingCleanup(target)
        appSettings.setDatabasePath(File(target, "seforim.db").absolutePath)
        appSettings.setLibraryInstallDir(if (same(target, defaultDir())) null else target.path)
        appSettings.flush()
        forgetCopy(target)
        if (exists(before) && !ForeignLibraries.isForeign(before)) recordCopy(before)
    }

    /** Deletes a kept copy the app does not use; returns the parts that could not be deleted. */
    fun deleteCopy(
        dir: File,
        appSettings: AppSettings,
    ): List<String> {
        val target = dir.absoluteFile
        if (same(target, currentDir(appSettings)) || ForeignLibraries.isForeign(target)) return listOf(target.path)
        val failed = (NAMES + LEFTOVERS).filterNot { deleteRecursively(File(target, it)) }
        removeIfEmptyLibraryFolder(target)
        if (failed.isEmpty()) forgetCopy(target)
        return failed
    }

    /** Whether [dir] is a kept copy, which a full install's cleanup must leave (it is not the library being replaced). */
    fun isKeptCopy(dir: File?): Boolean = dir != null && recordedCopies().any { same(it, dir) }

    private fun libraryFiles(dir: File): List<File> =
        (NAMES + WAL_NAME).map { File(dir, it) }.flatMap { part ->
            when {
                part.isFile -> listOf(part)
                part.isDirectory -> part.walkTopDown().filter { it.isFile }.toList()
                else -> emptyList()
            }
        }

    private fun largest(dir: File): Long = libraryFiles(dir).maxOfOrNull { it.length() } ?: 0L

    private fun copy(
        from: File,
        to: File,
        total: Long,
        progress: (Long, Long) -> Unit,
        stopped: () -> Boolean,
    ) {
        val buffer = ByteArray(COPY_BUFFER)
        var done = 0L
        for (file in libraryFiles(from)) {
            val target = File(to, file.relativeTo(from).path)
            target.parentFile?.mkdirs()
            file.inputStream().use { input ->
                FileOutputStream(target).use { output ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (stopped()) throw MoveStopped()
                        output.write(buffer, 0, n)
                        done += n
                        progress(done, total)
                    }
                    output.fd.sync()
                }
            }
            if (target.length() != file.length()) throw MoveException(Problem.Failed("${file.name}: incomplete copy"))
        }
    }

    // Same drive: each part is renamed into place; if one fails, the ones already moved go back
    private fun rename(
        from: File,
        to: File,
    ) {
        val moved = mutableListOf<String>()
        try {
            for (name in NAMES + WAL_NAME) {
                val a = File(from, name)
                if (!a.exists()) continue
                Files.move(a.toPath(), File(to, name).toPath(), StandardCopyOption.ATOMIC_MOVE)
                moved += name
            }
        } catch (e: IOException) {
            moved.forEach { name -> runCatching { Files.move(File(to, name).toPath(), File(from, name).toPath()) } }
            removeIfEmpty(to)
            throw MoveException(Problem.Failed(e.message ?: e.javaClass.simpleName))
        }
    }

    private fun removeLibrary(
        dir: File,
        created: Boolean,
    ) {
        (NAMES + WAL_NAME).forEach { deleteRecursively(File(dir, it)) }
        if (created) removeIfEmpty(dir)
    }

    private fun deleteRecursively(file: File): Boolean =
        try {
            if (file.exists()) file.deleteRecursively() && !file.exists() else true
        } catch (e: SecurityException) {
            warnln { "[LibraryLocation] Could not delete ${file.path}: ${e.message}" }
            false
        }

    // A folder named for the library goes too once it is empty; other folders are left alone
    private fun removeIfEmptyLibraryFolder(dir: File) {
        if (dir.name.equals(BrandInfo.LIBRARY_FOLDER_NAME, ignoreCase = true)) removeIfEmpty(dir)
    }

    private fun removeIfEmpty(dir: File) {
        if (dir.isDirectory && dir.list().isNullOrEmpty()) dir.delete()
    }

    /** Whether [a] and [b] are on one drive, where a move renames instead of copying. */
    fun sameDrive(
        a: File,
        b: File,
    ): Boolean =
        runCatching {
            val x = nearestExisting(a) ?: return false
            val y = nearestExisting(b) ?: return false
            Files.getFileStore(x.toPath()) == Files.getFileStore(y.toPath())
        }.getOrDefault(false)

    // canWrite() says yes for most folders on Windows: only a file actually made there tells
    private fun writable(dir: File): Boolean {
        val existing = nearestExisting(dir) ?: return false
        return runCatching { File.createTempFile(".library-", ".tmp", existing).delete() }.isSuccess
    }

    private fun nearestExisting(dir: File): File? {
        var d: File? = dir.absoluteFile
        while (d != null && !d.exists()) d = d.parentFile
        return d
    }

    private fun same(
        a: File,
        b: File,
    ): Boolean = runCatching { a.canonicalFile == b.canonicalFile }.getOrDefault(a.absoluteFile == b.absoluteFile)

    // A failed cleanup's retry list (PendingDbCleanup) must not delete a library put where it pointed
    private fun dropPendingCleanup(dir: File) {
        val marker = File(defaultDir(), PendingDbCleanup.MARKER_NAME)
        if (!marker.isFile) return
        val root = dir.absoluteFile.path
        val keep = marker.readLines().filter { it.isNotBlank() && it.trim() != root && !it.trim().startsWith(root + File.separator) }
        if (keep.isEmpty()) marker.delete() else marker.writeText(keep.joinToString("\n"))
    }

    private fun copiesFile(): File = File(defaultDir(), COPIES_NAME)

    private fun recordedCopies(): List<File> =
        runCatching { copiesFile().readLines().filter { it.isNotBlank() }.map { File(it.trim()) } }.getOrDefault(emptyList())

    private fun recordCopy(dir: File) = writeCopies(recordedCopies() + dir.absoluteFile)

    private fun forgetCopy(dir: File) = writeCopies(recordedCopies().filterNot { same(it, dir) })

    private fun writeCopies(dirs: List<File>) {
        val lines = dirs.map { it.absolutePath }.distinct()
        runCatching {
            if (lines.isEmpty()) copiesFile().delete() else copiesFile().writeText(lines.joinToString("\n"))
        }.onFailure { warnln { "[LibraryLocation] Could not record the kept copies: ${it.message}" } }
    }
}

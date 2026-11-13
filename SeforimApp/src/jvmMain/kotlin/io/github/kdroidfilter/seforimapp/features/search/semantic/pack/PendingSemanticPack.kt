package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A semantic pack unpacked beside the library ([SemanticPackInstaller]), put in place at the next start before anything
 * opens the search (main.kt, [runOnce]): each old folder is renamed aside, the new one moved in (a rename: it is on the
 * library's drive), and the old ones deleted once both are in. A failure puts the old ones back, both: a model and an
 * index of two packs don't work together. A start stopped halfway goes on at the next one, the request still there.
 * A failed one keeps the pack it unpacked for the next start, up to [MAX_ATTEMPTS] starts, and a folder still in use
 * (on Windows, by the app before a restart, closing) is waited for a few seconds ([renameWaiting]).
 *
 * The request is kept in the app's own databases folder, as [io.github.kdroidfilter.seforimapp.framework.database.PendingLibraryMove]'s:
 * the library's folder. A library gone from it since drops the pack; another release of the library keeps it, since a
 * pack works on every release ([SemanticPackReleases.newest]).
 */
object PendingSemanticPack {
    private const val MARKER_NAME = "pending-semantic-pack.txt"
    private const val FAILURE_NAME = "semantic-pack-failure.txt"
    private const val ATTEMPTS_NAME = "pending-semantic-pack-attempts.txt"
    private const val OLD_SUFFIX = ".old"

    /** The starts that try to put a pack in place before it is dropped. */
    const val MAX_ATTEMPTS = 3

    // A folder in use is tried again every quarter of a second, for ten seconds
    private const val IN_USE_TRIES = 40
    private const val IN_USE_PAUSE_MS = 250L

    data class Request(
        val libraryDir: File,
    )

    sealed interface Outcome {
        data object Installed : Outcome

        data class Dropped(
            val reason: String,
        ) : Outcome

        data class Failed(
            val message: String,
        ) : Outcome
    }

    fun schedule(
        request: Request,
        markerDir: File = defaultMarkerDir(),
    ) {
        File(markerDir, MARKER_NAME).writeText(request.libraryDir.absolutePath)
        runCatching { File(markerDir, ATTEMPTS_NAME).delete() }
    }

    // The folder is the first line: Pardes 1.0.0 wrote the library's release on a second one
    fun read(markerDir: File = defaultMarkerDir()): Request? =
        runCatching {
            val lines = File(markerDir, MARKER_NAME).takeIf { it.isFile }?.readLines() ?: return null
            val dir = lines.firstOrNull()?.trim()?.ifEmpty { null } ?: return null.also { clear(markerDir) }
            Request(File(dir))
        }.onFailure { warnln { "[PendingSemanticPack] Unreadable request: ${it.message}" } }.getOrNull()

    fun clear(markerDir: File = defaultMarkerDir()) {
        runCatching { File(markerDir, MARKER_NAME).delete() }
        runCatching { File(markerDir, ATTEMPTS_NAME).delete() }
    }

    /** Why the last start could not put a pack in place, once: the settings say so, then it is forgotten. */
    fun takeFailure(markerDir: File = defaultMarkerDir()): String? {
        val file = File(markerDir, FAILURE_NAME)
        return runCatching { file.takeIf { it.isFile }?.readText()?.trim() }.getOrNull().also { runCatching { file.delete() } }
    }

    /**
     * Puts a scheduled pack in place, if there is one; never throws (a failure is logged and kept for [takeFailure]). A
     * failure keeps the request and the unpacked pack for the next start, until the [MAX_ATTEMPTS]th, which drops them.
     */
    fun runOnce(markerDir: File = defaultMarkerDir()): Outcome? {
        val request = read(markerDir) ?: return null
        val outcome = runCatching { apply(request) }.getOrElse { Outcome.Failed(it.message ?: it.toString()) }
        val attempts = if (outcome is Outcome.Failed) attemptsOf(markerDir) + 1 else 0
        if (outcome is Outcome.Failed && attempts < MAX_ATTEMPTS) {
            runCatching { File(markerDir, ATTEMPTS_NAME).writeText(attempts.toString()) }
        } else {
            if (outcome is Outcome.Failed) SemanticPackInstaller.stagingDirOf(request.libraryDir).deleteRecursively()
            clear(markerDir)
        }
        when (outcome) {
            Outcome.Installed -> infoln { "[PendingSemanticPack] Semantic pack put in place in ${request.libraryDir}" }
            is Outcome.Dropped -> warnln { "[PendingSemanticPack] Semantic pack dropped: ${outcome.reason}" }
            is Outcome.Failed -> {
                warnln { "[PendingSemanticPack] Semantic pack not put in place (start $attempts of $MAX_ATTEMPTS): ${outcome.message}" }
                runCatching { File(markerDir, FAILURE_NAME).writeText(outcome.message) }
            }
        }
        return outcome
    }

    /** The starts that already failed to put the scheduled pack in place. */
    private fun attemptsOf(markerDir: File): Int =
        runCatching {
            File(markerDir, ATTEMPTS_NAME)
                .takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.toIntOrNull()
        }.getOrNull() ?: 0

    /**
     * Puts the pack staged beside [Request.libraryDir] in place; [move] renames a folder (a test's can fail). A failure
     * puts back what was there and leaves the pack staged, for [runOnce] to keep or drop.
     */
    internal fun apply(
        request: Request,
        move: (File, File) -> Unit = ::rename,
    ): Outcome {
        val lib = request.libraryDir
        val staged = SemanticPackInstaller.stagingDirOf(lib)
        // Each folder staged, or already in place: moved in by a start that was stopped before it ended
        val complete = SemanticPack.FOLDERS.all { isNew(File(staged, it)) || isNew(File(lib, it)) }
        val dropped =
            when {
                !File(lib, "seforim.db").isFile -> "no library in $lib"
                !complete -> "no complete pack in $staged"
                else -> null
            }
        if (dropped != null) {
            restore(lib, staged, move)
            staged.deleteRecursively()
            return Outcome.Dropped(dropped)
        }
        try {
            for (name in SemanticPack.FOLDERS) {
                val current = File(lib, name)
                if (isNew(current)) continue
                val old = File(lib, name + OLD_SUFFIX)
                if (current.exists()) {
                    // Left by an earlier pack whose old folders could not all be deleted
                    if (old.exists() && !old.deleteRecursively()) throw IOException("Could not delete $old")
                    move(current, old)
                }
                move(File(staged, name), current)
            }
        } catch (e: Exception) {
            restore(lib, staged, move)
            return Outcome.Failed(e.message ?: e.toString())
        }
        for (name in SemanticPack.FOLDERS) {
            File(lib, name + OLD_SUFFIX).deleteRecursively()
            File(File(lib, name), SemanticPackInstaller.STAGED_MARK).delete()
        }
        staged.deleteRecursively()
        return Outcome.Installed
    }

    /** A folder of the staged pack, wherever it is now ([SemanticPackInstaller.STAGED_MARK]). */
    private fun isNew(dir: File): Boolean = File(dir, SemanticPackInstaller.STAGED_MARK).isFile

    /** What was there before the pack: the new folders out, the old ones back in place. */
    private fun restore(
        lib: File,
        staged: File,
        move: (File, File) -> Unit,
    ) {
        for (name in SemanticPack.FOLDERS) {
            val current = File(lib, name)
            val old = File(lib, name + OLD_SUFFIX)
            runCatching {
                if (isNew(current)) {
                    staged.mkdirs()
                    move(current, File(staged, name))
                }
                if (old.exists() && !current.exists()) move(old, current)
            }.onFailure { warnln { "[PendingSemanticPack] Could not restore $current: ${it.message}" } }
        }
    }

    private fun rename(
        from: File,
        to: File,
    ) = renameWaiting(from, to)

    /**
     * Renames [from] to [to] with [move], trying again for a few seconds while the folder is in use: Windows refuses
     * the rename while another process has one of its files open, as the app does in the moments it closes after
     * "restart now". Any other failure (a name taken, a folder missing) is thrown at once.
     */
    internal fun renameWaiting(
        from: File,
        to: File,
        move: (File, File) -> Unit = ::moveOnce,
        pause: (Long) -> Unit = Thread::sleep,
    ) {
        var tries = 0
        while (true) {
            try {
                move(from, to)
                return
            } catch (e: FileSystemException) {
                if (!inUse(e) || ++tries > IN_USE_TRIES) throw e
                pause(IN_USE_PAUSE_MS)
            }
        }
    }

    // What Windows answers for a file in use: access denied, or a FileSystemException of its own ("used by another
    // process"); its subclasses (a name already taken, a folder missing or not empty) are not in use
    private fun inUse(e: FileSystemException): Boolean = e is AccessDeniedException || e.javaClass == FileSystemException::class.java

    private fun moveOnce(
        from: File,
        to: File,
    ) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath())
        }
    }

    private fun defaultMarkerDir(): File = File(FileKit.databasesDir.path).apply { mkdirs() }
}

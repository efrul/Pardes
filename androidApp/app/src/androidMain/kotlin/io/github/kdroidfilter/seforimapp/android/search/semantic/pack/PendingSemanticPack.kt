// Android variant of Pardes desktop's SeforimApp/src/jvmMain/.../features/search/semantic/pack/PendingSemanticPack.kt,
// same package and names. Changes, marked "Android:": the request is kept in the app's own files folder, which every
// caller passes (desktop defaults to FileKit's databases folder); the library's release is release_info.txt's
// (InstalledPack.libraryVersion, desktop's LibraryLocation.version); android.util.Log instead of desktop's logger.
package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A semantic pack unpacked beside the library ([SemanticPackInstaller]), put in place at the next start before anything
 * opens the search (MainActivity, [runOnce]): each old folder is renamed aside, the new one moved in (a rename: it is on
 * the library's drive), and the old ones deleted once both are in. A failure puts the old ones back, both: a model and an
 * index of two packs don't work together. A start stopped halfway goes on at the next one, the request still there.
 *
 * The request is kept in the app's own files folder ([markerDir]): the library's folder, and the library release the
 * pack was checked against. A library replaced since (another release, or none there) drops the pack.
 */
object PendingSemanticPack {
    private const val TAG = "PardesSemantic"
    private const val MARKER_NAME = "pending-semantic-pack.txt"
    private const val FAILURE_NAME = "semantic-pack-failure.txt"
    private const val OLD_SUFFIX = ".old"

    data class Request(
        val libraryDir: File,
        val library: String?,
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
        markerDir: File,
    ) {
        File(markerDir, MARKER_NAME).writeText(listOf(request.libraryDir.absolutePath, request.library.orEmpty()).joinToString("\n"))
    }

    fun read(markerDir: File): Request? =
        runCatching {
            val lines = File(markerDir, MARKER_NAME).takeIf { it.isFile }?.readLines() ?: return null
            val dir = lines.firstOrNull()?.trim()?.ifEmpty { null } ?: return null.also { clear(markerDir) }
            Request(File(dir), lines.getOrNull(1)?.trim()?.ifEmpty { null })
        }.onFailure { Log.w(TAG, "Unreadable request: ${it.message}") }.getOrNull()

    fun clear(markerDir: File) {
        runCatching { File(markerDir, MARKER_NAME).delete() }
    }

    /** Why the last start could not put a pack in place, once: the settings say so, then it is forgotten. */
    fun takeFailure(markerDir: File): String? {
        val file = File(markerDir, FAILURE_NAME)
        return runCatching { file.takeIf { it.isFile }?.readText()?.trim() }.getOrNull().also { runCatching { file.delete() } }
    }

    /** Puts a scheduled pack in place, if there is one; never throws (a failure is logged and kept for [takeFailure]). */
    fun runOnce(markerDir: File): Outcome? {
        val request = read(markerDir) ?: return null
        val outcome = runCatching { apply(request) }.getOrElse { Outcome.Failed(it.message ?: it.toString()) }
        clear(markerDir)
        when (outcome) {
            Outcome.Installed -> Log.i(TAG, "Semantic pack put in place in ${request.libraryDir}")
            is Outcome.Dropped -> Log.w(TAG, "Semantic pack dropped: ${outcome.reason}")
            is Outcome.Failed -> {
                Log.w(TAG, "Semantic pack not put in place: ${outcome.message}")
                runCatching { File(markerDir, FAILURE_NAME).writeText(outcome.message) }
            }
        }
        return outcome
    }

    /** Puts the pack staged beside [Request.libraryDir] in place; [move] renames a folder (a test's can fail). */
    internal fun apply(
        request: Request,
        move: (File, File) -> Unit = ::rename,
    ): Outcome {
        val lib = request.libraryDir
        val staged = SemanticPackInstaller.stagingDirOf(lib)
        val installed = InstalledPack.libraryVersion(lib)
        // Each folder staged, or already in place: moved in by a start that was stopped before it ended
        val complete = SemanticPack.FOLDERS.all { isNew(File(staged, it)) || isNew(File(lib, it)) }
        val dropped =
            when {
                !File(lib, "seforim.db").isFile -> "no library in $lib"
                request.library != null && installed != null && !SemanticPack.sameLibrary(request.library, installed) ->
                    "a pack of the library ${request.library}, the library is now $installed"
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
            staged.deleteRecursively()
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
            }.onFailure { Log.w(TAG, "Could not restore $current: ${it.message}") }
        }
    }

    private fun rename(
        from: File,
        to: File,
    ) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath())
        }
    }
}

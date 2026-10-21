package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File

/**
 * A library move the user asked for in the settings, run at the next start before anything opens the library
 * ([LibraryLocation.move], shown by `LibraryMoveWindow`). Kept in the app's own databases folder: the library's
 * folder, its target, whether the original stays as a copy, and whether the move has started: a request still there
 * once started is a move the app was killed in, which the next start ends ([LibraryLocation.undo]).
 */
object PendingLibraryMove {
    private const val MARKER_NAME = "pending-library-move.txt"

    data class Request(
        val from: File,
        val to: File,
        val keep: Boolean,
        val started: Boolean = false,
    )

    fun schedule(request: Request) {
        val lines = listOf(request.from.absolutePath, request.to.absolutePath, request.keep.toString(), request.started.toString())
        markerFile().writeText(lines.joinToString("\n"))
    }

    fun markStarted(request: Request) = schedule(request.copy(started = true))

    fun read(): Request? =
        runCatching {
            val lines = markerFile().takeIf { it.isFile }?.readLines() ?: return null
            if (lines.size < 3) return null.also { clear() }
            Request(File(lines[0]), File(lines[1]), lines[2].trim().toBoolean(), lines.getOrNull(3)?.trim().toBoolean())
        }.onFailure { warnln { "[PendingLibraryMove] Unreadable request: ${it.message}" } }.getOrNull()

    fun clear() {
        runCatching { markerFile().delete() }
    }

    private fun markerFile(): File = File(File(FileKit.databasesDir.path).apply { mkdirs() }, MARKER_NAME)
}

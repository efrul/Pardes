package io.github.kdroidfilter.seforimapp.features.pdf

import java.io.File
import java.util.Arrays

// Pardes (request 28 of docs/DESKTOP_STAGE2.md): where a PDF book the user adds is kept, as they chose the first time
// (the user's decision of 2026-11-13), and changed in Settings.

/** Where a PDF book the user adds is kept. */
enum class PdfKeep {
    /** Copied to a folder beside the library, on its drive, as the semantic search's packs. */
    BesideLibrary,

    /** Left where it is: the book is its path. */
    InPlace,

    /** Copied to a folder the user chose. */
    ChosenFolder,
}

/** The user's answer, [keep] and the [folder] chosen for [PdfKeep.ChosenFolder]. */
data class PdfKeepAnswer(
    val keep: PdfKeep,
    val folder: File? = null,
)

/** The PDF books' files, kept as [PdfKeep] says. */
object PdfBookFiles {
    /** The folder beside the library that [PdfKeep.BesideLibrary] copies to. */
    const val LIBRARY_FOLDER = "my-books"

    /**
     * The folder a book added is copied to as [keep] says: beside the library's [database] (else the app's own [appDir]),
     * the [chosen] folder, or none when it stays in place (or no folder was chosen).
     */
    fun folderFor(
        keep: PdfKeep,
        database: File?,
        chosen: File?,
        appDir: File,
    ): File? =
        when (keep) {
            PdfKeep.BesideLibrary -> File(database?.absoluteFile?.parentFile ?: appDir, LIBRARY_FOLDER)
            PdfKeep.InPlace -> null
            PdfKeep.ChosenFolder -> chosen
        }

    /**
     * Keeps [source] in [folder] (as it is, where it is, when null or already there) and gives the file kept: a copy under
     * its own name, or under a number when another file has that name, unless that file is the same as the source.
     */
    fun keep(
        source: File,
        folder: File?,
    ): File {
        if (folder == null || source.absoluteFile.parentFile == folder.absoluteFile) return source.absoluteFile
        folder.mkdirs()
        val target =
            generateSequence(1) { it + 1 }
                .map { n -> File(folder, nameFor(source, n)) }
                .first { !it.exists() || sameFile(it, source) }
        if (!target.exists()) {
            val partial = File(folder, target.name + PARTIAL)
            source.copyTo(partial, overwrite = true)
            if (!partial.renameTo(target)) {
                partial.delete()
                error("Could not keep ${source.name} in $folder")
            }
        }
        return target.absoluteFile
    }

    /** The title a book gets from its [file]: its name without the extension. */
    fun titleOf(file: File): String = file.nameWithoutExtension.ifBlank { file.name }

    private fun nameFor(
        source: File,
        n: Int,
    ): String = if (n == 1) source.name else "${source.nameWithoutExtension}-$n.${source.extension}"

    // The same bytes: the size first, then the bytes themselves, a block at a time
    private fun sameFile(
        a: File,
        b: File,
    ): Boolean =
        a.length() == b.length() &&
            a.inputStream().use { x ->
                b.inputStream().use { y ->
                    val blockA = ByteArray(BLOCK)
                    val blockB = ByteArray(BLOCK)
                    var read: Int
                    var same: Boolean
                    do {
                        read = x.readNBytes(blockA, 0, BLOCK)
                        same = read == y.readNBytes(blockB, 0, BLOCK) && Arrays.equals(blockA, 0, read, blockB, 0, read)
                    } while (same && read > 0)
                    same
                }
            }

    // A copy in progress, renamed once whole: a copy cut off is never taken for the book
    private const val PARTIAL = ".part"
    private const val BLOCK = 64 * 1024
}

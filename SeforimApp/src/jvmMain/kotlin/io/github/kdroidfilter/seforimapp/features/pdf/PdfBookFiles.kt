package io.github.kdroidfilter.seforimapp.features.pdf

import java.io.File

// Pardes (requests 28 and 44 of docs/DESKTOP_STAGE2.md): the files of the user's folders of PDF books, read where they
// are, as Zayita and Otzaria read theirs (the user's decision of 2026-11-13).

/**
 * A folder of the user's as the book tree shows it: its [dir], the PDF files right in it ([books], by name) and its
 * subfolders that hold some ([folders], by name), each a category.
 */
class PdfFolderTree(
    val dir: File,
    val books: List<File>,
    val folders: List<PdfFolderTree>,
)

/** The user's folders of PDF books, read as they are on the disk. */
object PdfBookFiles {
    /** The title a book gets from its [file]: its name without the extension. */
    fun titleOf(file: File): String = file.nameWithoutExtension.ifBlank { file.name }

    /**
     * [dir] as the book tree shows it, or null when it holds no PDF file, in it or below it: hidden files and folders
     * (their names starting with a dot) are left out, as is a folder seen already on the way (a link back up).
     */
    fun treeOf(
        dir: File,
        seen: MutableSet<String> = HashSet(),
    ): PdfFolderTree? {
        val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!seen.add(canonical)) return null
        val entries =
            dir
                .listFiles()
                .orEmpty()
                .filterNot { it.name.startsWith(".") }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val books = entries.filter { it.isFile && it.extension.equals(PDF, ignoreCase = true) }
        val folders = entries.filter { it.isDirectory }.mapNotNull { treeOf(it, seen) }
        return if (books.isEmpty() && folders.isEmpty()) null else PdfFolderTree(dir, books, folders)
    }

    /** Whether [file] is in [folder], right in it or below it. */
    fun isIn(
        file: File,
        folder: File,
    ): Boolean {
        val path = file.absoluteFile.normalize().path
        val root = folder.absoluteFile.normalize().path
        return path.startsWith(root.trimEnd(File.separatorChar) + File.separatorChar)
    }

    private const val PDF = "pdf"
}

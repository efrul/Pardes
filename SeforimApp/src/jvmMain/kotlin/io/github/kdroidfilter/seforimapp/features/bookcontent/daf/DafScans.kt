package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.settings.feedback.otzariaDataRoots
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Pardes (request 41 of docs/DESKTOP_STAGE2.md): the scan of a tractate's page, beside the daf Pardes draws, "גם כי יש
// כאלה שמעדיפים מקור וגם להשוואה בקלות". Its source, as the user decided on 2026-11-13: Otzaria's scans on the user's
// own computer first (nothing of them is shipped by Pardes), a file of the user's where there are none.

/**
 * Where Otzaria keeps the Talmud Bavli's scans: one PDF a tractate, beside its text, in its order's folder of its
 * library's `אוצריא/תלמוד בבלי` (`סדר זרעים/ברכות.pdf`, as Otzaria's library has it). Page i of such a file is the
 * tractate's amud i from ב. Pardes's library is made from Otzaria's, so a tractate's title is its file's name.
 */
object OtzariaScans {
    /**
     * The Bavli folders of the libraries that Otzaria's data folders [roots] point at (`library_path.txt`, its library's
     * folder or the `אוצריא` in it).
     */
    fun bavliFolders(roots: List<File> = otzariaDataRoots()): List<File> =
        roots
            .mapNotNull { pointer(File(it, POINTER)) }
            .flatMap { path ->
                val library = File(path)
                listOfNotNull(library, File(library, OTZARIA_DIR), library.parentFile?.let { File(it, OTZARIA_DIR) })
            }.map { File(it, BAVLI_DIR) }
            .filter { it.isDirectory }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }

    /** The scan of [tractate] in the Bavli folders [bavli]: its PDF in one of their orders' folders, or in them. */
    fun find(
        tractate: String,
        bavli: List<File>,
    ): File? {
        val name = "$tractate.pdf"
        return bavli.firstNotNullOfOrNull { dir ->
            File(dir, name).takeIf { it.isFile }
                ?: dir
                    .listFiles { file -> file.isDirectory }
                    .orEmpty()
                    .sortedBy { it.name }
                    .map { File(it, name) }
                    .firstOrNull { it.isFile }
        }
    }

    // A path in one of Otzaria's pointer files (UTF-8, with a byte order mark), as Pardes reads its searches
    private fun pointer(file: File): String? {
        val text = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull() ?: return null
        return text.trim(BYTE_ORDER_MARK, ' ', '\t', '\r', '\n', '"').ifEmpty { null }
    }

    private const val POINTER = "library_path.txt"
    private const val OTZARIA_DIR = "אוצריא"
    private const val BAVLI_DIR = "תלמוד בבלי"
    private val BYTE_ORDER_MARK = Char(0xFEFF)
}

/**
 * The tractates' scans: the file the user linked to a tractate (the user DB's daf_scan, so the backup carries the link),
 * else Otzaria's ([OtzariaScans]), looked for once a start.
 */
@Inject
@SingleIn(AppScope::class)
class DafScans(
    database: UserSettingsDb,
) {
    private val queries = database.dafScansQueries

    // Otzaria's Bavli folders, read at the first scan asked for
    private val bavli by lazy { OtzariaScans.bavliFolders() }

    /** The scan of [tractate] (its book's title), or null when none is linked and Otzaria has none. */
    suspend fun of(tractate: String): File? =
        withContext(Dispatchers.IO) {
            linked(tractate) ?: OtzariaScans.find(tractate, bavli)
        }

    /** The file the user linked to [tractate], or null. */
    suspend fun linked(tractate: String): File? = withContext(Dispatchers.IO) { queries.select(tractate).executeAsOneOrNull()?.let(::File) }

    /** Links [file] to [tractate] as its scan, in place of Otzaria's. */
    suspend fun link(
        tractate: String,
        file: File,
    ) {
        withContext(Dispatchers.IO) { queries.upsert(tractate, file.absolutePath) }
    }

    /** Lets the file linked to [tractate] go: Otzaria's scan, if any, is its scan again. */
    suspend fun unlink(tractate: String) {
        withContext(Dispatchers.IO) { queries.delete(tractate) }
    }
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.settings.feedback.otzariaDataRoots
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

// Pardes (requests 41 and 48 of docs/DESKTOP_STAGE2.md): the scan of a tractate's pages, beside the text and the daf
// Pardes draws, "גם כי יש כאלה שמעדיפים מקור וגם להשוואה בקלות". Its sources, as the user decided on 2026-11-13: a
// folder of scanned tractates the user chooses in the settings, and Otzaria's scans on the user's own computer, found
// by themselves (nothing of them is shipped by Pardes).

/** The daf view's scans' settings: the folder of scanned tractates the user chose (Settings › צורת הדף). */
object DafScanSettings : SettingsGroup("daf_scans") {
    /** The folder's path, looked in before Otzaria's; null for none. A folder of this computer: not carried elsewhere. */
    val folder by json<String?>(null)
}

/**
 * Where Otzaria keeps the Talmud Bavli's scans: one PDF a tractate (`ברכות.pdf`) in a folder `תלמוד בבלי`. The app
 * downloads them with its library and puts them beside its books database, in a folder of their own; its library's
 * repository keeps them beside their texts, in their orders' folders (`אוצריא/תלמוד בבלי/סדר זרעים/ברכות.pdf`). Page i
 * of such a file is the tractate's amud i from ב. Pardes's library is made from Otzaria's, so a tractate's title is its
 * file's name.
 */
object OtzariaScans {
    /**
     * The Bavli folders of the libraries that Otzaria's data folders [roots] point at: the folder of its books database
     * (`library_database_path.txt`), its library's (`library_path.txt`), the `אוצריא` in that or beside it, and the
     * library Otzaria makes by default (`books` in its data folder).
     */
    fun bavliFolders(roots: List<File> = otzariaDataRoots()): List<File> =
        roots
            .flatMap { root ->
                val library = pointer(File(root, POINTER))?.let(::File)
                listOfNotNull(
                    pointer(File(root, DATABASE_POINTER))?.let(::File),
                    library,
                    library?.let { File(it, OTZARIA_DIR) },
                    library?.parentFile?.let { File(it, OTZARIA_DIR) },
                    File(root, DEFAULT_LIBRARY),
                )
            }.map { File(it, BAVLI_DIR) }
            .filter { it.isDirectory }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }

    /**
     * The scan of [tractate] in a folder of the user's [dir]: its PDF right in it, or in a folder in it, [depth] folders
     * down at most, the nearest first and each level's folders by name.
     */
    fun findIn(
        tractate: String,
        dir: File,
        depth: Int = FOLDER_DEPTH,
    ): File? {
        val name = "$tractate.pdf"
        var level = listOf(dir)
        repeat(depth + 1) {
            level.firstNotNullOfOrNull { folder -> File(folder, name).takeIf { it.isFile } }?.let { return it }
            level = level.flatMap { folder -> folder.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name } }
        }
        return null
    }

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

    private const val FOLDER_DEPTH = 3
    private const val POINTER = "library_path.txt"
    private const val DATABASE_POINTER = "library_database_path.txt"
    private const val DEFAULT_LIBRARY = "books"
    private const val OTZARIA_DIR = "אוצריא"
    private const val BAVLI_DIR = "תלמוד בבלי"
    private val BYTE_ORDER_MARK = Char(0xFEFF)
}

/**
 * The tractates' scans: in the folder the user chose ([DafScanSettings.folder]) first, else Otzaria's ([OtzariaScans]),
 * whose folders are looked for once a start.
 */
@Inject
@SingleIn(AppScope::class)
class DafScans(
    private val appSettings: AppSettings,
) {
    // Otzaria's data folders, its own unless a test gives others, and its Bavli folders, read at the first scan asked for
    internal var otzariaRoots: List<File>? = null
    private val bavli by lazy { OtzariaScans.bavliFolders(otzariaRoots ?: otzariaDataRoots()) }

    /** The folder of scans the user chose, its path, or null. */
    val folder: StateFlow<String?> get() = appSettings.flow(DafScanSettings.folder)

    /** Chooses [dir] as the folder of scans, or none. */
    fun setFolder(dir: File?) {
        appSettings[DafScanSettings.folder] = dir?.absolutePath
    }

    /** The scan of [tractate] (its book's title), or null when the folder chosen and Otzaria have none. */
    suspend fun of(tractate: String): File? =
        withContext(Dispatchers.IO) {
            folder.value?.let { OtzariaScans.findIn(tractate, File(it)) } ?: OtzariaScans.find(tractate, bavli)
        }

    /** Otzaria's Bavli folders found on this computer. */
    suspend fun otzariaFolders(): List<File> = withContext(Dispatchers.IO) { bavli }
}

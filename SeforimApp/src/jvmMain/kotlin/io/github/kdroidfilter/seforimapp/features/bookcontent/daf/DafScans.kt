package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.pdf.PdfOutlines
import io.github.kdroidfilter.seforimapp.features.settings.feedback.otzariaDataRoots
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// Pardes (requests 41 and 48 of docs/DESKTOP_STAGE2.md): the scan of a tractate's pages, beside the text and the daf
// Pardes draws, "גם כי יש כאלה שמעדיפים מקור וגם להשוואה בקלות". Its sources, as the user decided on 2026-11-13: a
// folder of scanned tractates the user chooses in the settings, and Otzaria's scans on the user's own computer, found
// by themselves (nothing of them is shipped by Pardes).

/**
 * The daf view's scans' settings: the folder of scanned tractates the user chose (Settings › צורת הדף), whether scans
 * are cut into images, and the pages of its files where tractates begin.
 */
object DafScanSettings : SettingsGroup("daf_scans") {
    /** The folder's path, looked in before Otzaria's; null for none. A folder of this computer: not carried elsewhere. */
    val folder by json<String?>(null)

    /**
     * Whether a scan is cut into an image a page, which shows it at once (DafScanPages), as the user answered when a scan
     * first opened; null until then (request 51 of docs/DESKTOP_STAGE3_PLAN.md). This computer's disk: not carried.
     */
    val cut by json<Boolean?>(null)

    /**
     * The page (from 0) of a file of the folder where a tractate's daf ב. is, as the user set it, by `file|tractate`
     * ([firstPageKey]; request 52 of docs/DESKTOP_STAGE3_PLAN.md). The files of this computer: not carried.
     */
    val firstPages by json<Map<String, Int>>(emptyMap())
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
     * The scan of [tractate] in a folder of the user's [dir]: a PDF right in it, or in a folder in it, [depth] folders
     * down at most, whose name holds the tractate's ([ScanNames], request 43 of docs/DESKTOP_STAGE3_PLAN.md), the other
     * tractates being [tractates]. The file named the tractate first, then a file of the tractate alone, then one of
     * several; among those, the tractate's name nearest the start of the file's, the nearest folder, the name.
     */
    fun findIn(
        tractate: String,
        dir: File,
        tractates: Collection<String> = emptyList(),
        depth: Int = FOLDER_DEPTH,
    ): ScanSource? {
        val found = ArrayList<Found>()
        var level = listOf(dir)
        for (down in 0..depth) {
            for (file in level.flatMap { folder -> folder.listFiles { file -> file.isFile && file.isPdf }.orEmpty().toList() }) {
                val fit = ScanNames.fit(file.name, tractate, tractates) ?: continue
                found += Found(file, fit, ScanNames.indexIn(ScanNames.words(file.name), tractate), down)
            }
            level = level.flatMap { folder -> folder.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name } }
        }
        return found
            .minWithOrNull(compareBy({ it.fit }, { it.index }, { it.down }, { it.file.name }))
            ?.let { ScanSource(it.file, it.fit, personal = true) }
    }

    // A file whose name holds the tractate: how it fits, where the tractate's name is in it, how many folders down
    private class Found(
        val file: File,
        val fit: ScanFit,
        val index: Int,
        val down: Int,
    )

    private val File.isPdf: Boolean get() = !name.startsWith('.') && extension.equals("pdf", ignoreCase = true)

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
 * whose folders are looked for once a start; in a file of the user's, the tractate's place ([ScanSource.first]), set
 * by the user or guessed, by the library's tractates ([repository]).
 */
@Inject
@SingleIn(AppScope::class)
class DafScans(
    private val appSettings: AppSettings,
    private val repository: SeforimRepository,
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

    /** Whether scans are cut into images, as the user answered; null while not asked (request 51). */
    val cut: StateFlow<Boolean?> get() = appSettings.flow(DafScanSettings.cut)

    /** Cuts scans into images from now on, or not ([on]). */
    fun setCut(on: Boolean) {
        appSettings[DafScanSettings.cut] = on
    }

    /** The first pages the user set, by `file|tractate` ([firstPageKey]). */
    val firstPages: StateFlow<Map<String, Int>> get() = appSettings.flow(DafScanSettings.firstPages)

    /**
     * Sets the page (from 0) where daf ב. is in the file and of the tractate of [key] ([firstPageKey]), or lets it be
     * guessed again ([page] null).
     */
    fun setFirstPage(
        key: String,
        page: Int?,
    ) {
        val pages = appSettings[DafScanSettings.firstPages]
        appSettings[DafScanSettings.firstPages] = if (page == null) pages - key else pages + (key to page)
    }

    /**
     * The scan of [tractate] (its book's title), or null when the folder chosen and Otzaria have none: in a file of the
     * folder chosen, at the page the user set, else at the one the tractates named before it in the file's name give
     * (their tables of contents, from the first page set for one of them), and in a file of several its pages only.
     */
    suspend fun of(tractate: String): ScanSource? =
        withContext(Dispatchers.IO) {
            val tractates = tractates()
            val found =
                folder.value?.let { OtzariaScans.findIn(tractate, File(it), tractates.keys) }
                    ?: return@withContext OtzariaScans.find(tractate, bavli)?.let { ScanSource(it) }
            val setFirst = { title: String -> firstPages.value[firstPageKey(found.file, title)] }
            val set = setFirst(tractate)
            if (found.fit != ScanFit.JOINED) return@withContext found.copy(first = set ?: 0, firstSet = set != null)
            val inName = ScanNames.tractatesIn(found.file.name, tractates.keys)
            val amuds = inName.associateWith { title -> tractates[title]?.let { amudsOf(it) } }
            found.copy(
                first = set ?: firstByName(tractate, inName, amuds::get, setFirst) ?: 0,
                count = amuds[tractate],
                firstSet = set != null,
            )
        }

    /**
     * [source] of [tractate] at the page its file's bookmarks give, when the user set none ([firstByBookmarks]): a file
     * of the user's folder, read by PDFium once it is loaded.
     */
    suspend fun byBookmarks(
        source: ScanSource,
        tractate: String,
    ): ScanSource {
        if (!source.personal || source.firstSet) return source
        val file = source.file
        // Pardes: read once a file as it is, not each time its scan is shown again, where a read that failed moved its
        // pages (request 60 of the list after the third testers' build); a failed read is not kept
        val key = "${file.path}|${file.length()}|${file.lastModified()}|$tractate|${source.fit}"
        val first =
            bookmarked[key] ?: run {
                val outline = runSuspendCatching { PdfOutlines.read(file) }.getOrNull() ?: return source
                (firstByBookmarks(tractate, outline, alone = source.fit != ScanFit.JOINED) ?: NO_BOOKMARK).also { bookmarked[key] = it }
            }
        return if (first == NO_BOOKMARK) source else source.copy(first = first)
    }

    // The page each file's bookmarks give a tractate, by the file as it is and the tractate; NO_BOOKMARK for none
    private val bookmarked = ConcurrentHashMap<String, Int>()

    // The Talmud's tractates, the books of its orders by title, read once they are found
    private var bavliBooks: Map<String, Long>? = null

    private suspend fun tractates(): Map<String, Long> =
        bavliBooks ?: runSuspendCatching {
            (CatalogPresets.Ids.Categories.BAVLI_ZERAIM..CatalogPresets.Ids.Categories.BAVLI_TAHAROT)
                .flatMap { repository.getBooksByCategory(it) }
                .associate { it.title to it.id }
        }.getOrDefault(emptyMap()).also { if (it.isNotEmpty()) bavliBooks = it }

    // A tractate's amudim from ב., by its table of contents
    private suspend fun amudsOf(bookId: Long): Int? = runSuspendCatching { amudCount(repository.getTocEntriesForBook(bookId)) }.getOrNull()

    /** Otzaria's Bavli folders found on this computer. */
    suspend fun otzariaFolders(): List<File> = withContext(Dispatchers.IO) { bavli }
}

// A file whose bookmarks give the tractate no page, as DafScans.byBookmarks keeps it
private const val NO_BOOKMARK = -1

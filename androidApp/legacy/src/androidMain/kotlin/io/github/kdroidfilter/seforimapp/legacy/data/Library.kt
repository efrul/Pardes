package io.github.kdroidfilter.seforimapp.legacy.data

import android.content.Context
import android.media.MediaScannerConnection
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import io.github.kdroidfilter.seforimapp.legacy.db.NativeSqliteDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import java.io.File

/**
 * A row of the library tree: a category, or a book in one ([base]: one of the desktop's base books, isBaseBook;
 * [root]: its top category, which the search's category chips name).
 */
data class Entry(val id: Long, val title: String, val isBook: Boolean, val base: Boolean = false, val root: Long = 0)

/**
 * What the reader needs of a book, read without its notes (they reach 4 MB): which links it has (the toolbar shows
 * only those buttons, as the main app) and whether its text has niqqud and cantillation (the ניקוד button).
 */
data class BookInfo(
    val id: Long,
    val title: String,
    val categoryId: Long,
    val totalLines: Int,
    val shortDescription: String?,
    val hasCommentaries: Boolean = false,
    val hasTargum: Boolean = false,
    val hasSources: Boolean = false,
    val hasNekudot: Boolean = false,
    val hasTeamim: Boolean = false,
)

class NoLibraryException : Exception("No library")

/**
 * The library: where it is, and the desktop repository on top of it. The library is the desktop's databases folder
 * (seforim.db, the rest unused here) in one of the folders the main app uses, the app's own folder on the phone or on a
 * memory card (`Android/data/io.github.efrul.pardes/files/library`), or its internal storage. A database in chunks
 * (seforim.db, seforim.db001…, a FAT32 card) opens like one in a single file: the native SQLite reads it through the
 * multiplex VFS.
 */
class Library(private val context: Context) {
    private val prefs = Prefs(context)

    /** A place the library may be in. */
    data class Location(val dir: File, val label: String)

    /** The app's folder on the phone's shared storage, then on each memory card, then its internal storage. */
    fun locations(): List<Location> {
        val external = runCatching { context.getExternalFilesDirs(null).toList() }.getOrDefault(emptyList())
        val list = ArrayList<Location>()
        external.forEachIndexed { i, files ->
            if (files != null) list += Location(File(files, FOLDER), if (i == 0) PHONE_LABEL else CARD_LABEL)
        }
        list += Location(File(context.filesDir, FOLDER), INTERNAL_LABEL)
        return list
    }

    /**
     * The library database: in the folder it was last opened from, else the first location that holds one. A
     * seforim.db dropped next to the library folder (`files/seforim.db`, as the main app also takes it) is moved in.
     */
    fun findDatabase(): File? {
        val dirs = (listOfNotNull(prefs.libraryDir?.let(::File)) + locations().map { it.dir }).distinct()
        dirs.firstOrNull { File(it, DB_NAME).isFile }?.let { return File(it, DB_NAME) }
        for (location in locations()) {
            val dropped = File(location.dir.parentFile, DB_NAME)
            if (dropped.isFile && dropped.length() > 0) {
                location.dir.mkdirs()
                val target = File(location.dir, DB_NAME)
                if (dropped.renameTo(target)) return target
            }
        }
        return null
    }

    /**
     * Makes the library folder on the phone's shared storage, with a note in it, and has Android list it: on Android
     * 4.4 a computer sees over USB (MTP) only what the media scanner knows, so without this the folder to copy the
     * library into would not show.
     */
    fun prepareFolder(): File? {
        val dir = locations().firstOrNull()?.dir ?: return null
        runCatching {
            dir.mkdirs()
            val note = File(dir, NOTE_NAME)
            if (!note.isFile) note.writeText(NOTE_TEXT)
            MediaScannerConnection.scanFile(context, arrayOf(note.path), null, null)
        }
        return dir
    }

    /** The open library: its file, the native driver and the desktop repository on it. */
    class Opened(val file: File, val driver: NativeSqliteDriver, val repository: SeforimRepository) {
        fun <T> query(sql: String, bind: (SqlPreparedStatement.() -> Unit)? = null, row: (SqlCursor) -> T): List<T> =
            driver.executeQuery(null, sql, { cursor ->
                val rows = ArrayList<T>()
                while (cursor.next().value) rows += row(cursor)
                QueryResult.Value(rows)
            }, 0, bind).value

        fun rootEntries(): List<Entry> =
            query("SELECT id, title FROM category WHERE parentId IS NULL ORDER BY orderIndex, title") {
                Entry(it.getLong(0)!!, it.getString(1)!!, isBook = false)
            }

        /** A category's books, then its subcategories (the library tree's order). */
        fun children(categoryId: Long): List<Entry> {
            val books = query("SELECT id, title FROM book WHERE categoryId = ? ORDER BY orderIndex, title", { bindLong(0, categoryId) }) {
                Entry(it.getLong(0)!!, it.getString(1)!!, isBook = true)
            }
            val categories = query("SELECT id, title FROM category WHERE parentId = ? ORDER BY orderIndex, title", { bindLong(0, categoryId) }) {
                Entry(it.getLong(0)!!, it.getString(1)!!, isBook = false)
            }
            return books + categories
        }

        fun categoryTitle(id: Long): String? = query("SELECT title FROM category WHERE id = ?", { bindLong(0, id) }) { it.getString(0) }.firstOrNull()

        /** The categories from the root down to [categoryId]. */
        fun categoryPath(categoryId: Long): List<Entry> {
            val path = ArrayList<Entry>()
            var id: Long? = categoryId
            while (id != null && path.size < 32) {
                val row = query("SELECT id, title, parentId FROM category WHERE id = ?", { bindLong(0, id!!) }) {
                    Triple(it.getLong(0)!!, it.getString(1)!!, it.getLong(2))
                }.firstOrNull() ?: break
                path.add(0, Entry(row.first, row.second, isBook = false))
                id = row.third
            }
            return path
        }

        fun book(id: Long): BookInfo? =
            query(
                """
                SELECT id, title, categoryId, totalLines, heShortDesc,
                    hasCommentaryConnection, hasTargumConnection, hasSourceConnection, hasNekudot, hasTeamim
                FROM book WHERE id = ?
                """.trimIndent(),
                { bindLong(0, id) },
            ) {
                BookInfo(
                    it.getLong(0)!!, it.getString(1)!!, it.getLong(2)!!, it.getLong(3)!!.toInt(), it.getString(4),
                    hasCommentaries = it.getLong(5) == 1L, hasTargum = it.getLong(6) == 1L, hasSources = it.getLong(7) == 1L,
                    hasNekudot = it.getLong(8) == 1L, hasTeamim = it.getLong(9) == 1L,
                )
            }.firstOrNull()

        fun bookTitle(id: Long): String? = query("SELECT title FROM book WHERE id = ?", { bindLong(0, id) }) { it.getString(0) }.firstOrNull()

        /** The table of contents' entry of a line ("פרק כו"), the place the title bar and the search's results name. */
        fun heading(bookId: Long, lineIndex: Int): String? =
            query(
                """
                SELECT tt.text FROM line l JOIN line_toc lt ON lt.lineId = l.id JOIN tocEntry te ON te.id = lt.tocEntryId
                JOIN tocText tt ON tt.id = te.textId WHERE l.bookId = ? AND l.lineIndex = ?
                """.trimIndent(),
                { bindLong(0, bookId); bindLong(1, lineIndex.toLong()) },
            ) { it.getString(0) }.firstOrNull()

        fun lineCount(bookId: Long): Int =
            query("SELECT COUNT(*) FROM line WHERE bookId = ?", { bindLong(0, bookId) }) { it.getLong(0)!!.toInt() }.first()

        /** The line index of a line id (TOC entries and links point at lines by id). */
        fun lineIndex(lineId: Long): Int? = query("SELECT lineIndex FROM line WHERE id = ?", { bindLong(0, lineId) }) { it.getLong(0)!!.toInt() }.firstOrNull()

        /** The book and line index of a line id. */
        fun lineLocation(lineId: Long): Pair<Long, Int>? =
            query("SELECT bookId, lineIndex FROM line WHERE id = ?", { bindLong(0, lineId) }) { it.getLong(0)!! to it.getLong(1)!!.toInt() }.firstOrNull()

        /** Books whose title or one of whose acronyms contains [text], titles that start with it first. */
        fun findBooks(text: String, limit: Int = 60): List<Entry> {
            val q = text.trim()
            if (q.isEmpty()) return emptyList()
            val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            val prefix = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            return query(
                """
                SELECT b.id, b.title FROM book b
                WHERE b.title LIKE ?1 ESCAPE '\' OR b.id IN (SELECT bookId FROM book_acronym WHERE term LIKE ?1 ESCAPE '\')
                ORDER BY (b.title LIKE ?2 ESCAPE '\') DESC, length(b.title), b.orderIndex
                LIMIT ?3
                """.trimIndent(),
                { bindString(0, like); bindString(1, prefix); bindLong(2, limit.toLong()) },
            ) { Entry(it.getLong(0)!!, it.getString(1)!!, isBook = true) }
        }

        /** The category title of each of [bookIds] (a suggestion's second line). */
        fun categoriesOf(bookIds: Collection<Long>): Map<Long, String> {
            if (bookIds.isEmpty()) return emptyMap()
            val marks = bookIds.joinToString(",") { "?" }
            return query(
                "SELECT b.id, c.title FROM book b JOIN category c ON c.id = b.categoryId WHERE b.id IN ($marks)",
                { bookIds.forEachIndexed { i, id -> bindLong(i, id) } },
            ) { it.getLong(0)!! to (it.getString(1) ?: "") }.toMap()
        }

        /** The books whose title is [text], or one of whose acronyms is (a book named whole, which Enter opens). */
        fun booksNamed(text: String): Set<Long> {
            val q = text.trim()
            if (q.isEmpty()) return emptySet()
            return query(
                "SELECT id FROM book WHERE title = ?1 UNION SELECT bookId FROM book_acronym WHERE term = ?1",
                { bindString(0, q) },
            ) { it.getLong(0)!! }.toSet()
        }

        /**
         * Every book under [categoryId] at any depth, or every book of the library for null, in the tree's order (a
         * category's books, then its subcategories, each by its place): the order the search goes through them.
         */
        fun booksUnder(categoryId: Long?): List<Entry> {
            data class Cat(val id: Long, val parentId: Long?)
            val categories = query("SELECT id, parentId FROM category ORDER BY orderIndex, title") { Cat(it.getLong(0)!!, it.getLong(1)) }
            val children = categories.groupBy { it.parentId }
            val books = query("SELECT id, title, categoryId, isBaseBook FROM book ORDER BY orderIndex, title") {
                Entry(it.getLong(0)!!, it.getString(1)!!, isBook = true, base = it.getLong(3) == 1L) to it.getLong(2)!!
            }.groupBy({ it.second }, { it.first })
            val parents = categories.associate { it.id to it.parentId }
            fun rootOf(id: Long): Long {
                var at = id
                repeat(64) { at = parents[at] ?: return at }
                return at
            }
            val out = ArrayList<Entry>()
            fun walk(id: Long, root: Long) {
                books[id]?.let { list -> list.mapTo(out) { it.copy(root = root) } }
                children[id]?.forEach { walk(it.id, root) }
            }
            if (categoryId == null) children[null]?.forEach { walk(it.id, it.id) } else walk(categoryId, rootOf(categoryId))
            return out
        }
    }

    @Volatile
    private var opened: Opened? = null

    val current: Opened? get() = opened

    /** Opens the library (blocking: call it off the main thread). [NoLibraryException] when there is none. */
    fun open(): Opened {
        opened?.let { return it }
        synchronized(this) {
            opened?.let { return it }
            val file = findDatabase() ?: throw NoLibraryException()
            val driver = NativeSqliteDriver.open(file.path)
            val repository = try {
                SeforimRepository(file.path, driver).also {
                    // The repository asks for a 256 MB page cache (desktop's balanced mode): far more than an old phone
                    // gives an app. Back to the driver's.
                    driver.execute(null, "PRAGMA cache_size=-${NativeSqliteDriver.DEFAULT_CACHE_KIB}", 0)
                }
            } catch (e: Throwable) {
                driver.close()
                throw e
            }
            prefs.libraryDir = file.parent
            return Opened(file, driver, repository).also { opened = it }
        }
    }

    /** Closes the library, so that the next [open] looks for it again (after a copy, or a card put back). */
    fun close() {
        synchronized(this) {
            opened?.driver?.close()
            opened = null
        }
    }

    companion object {
        const val DB_NAME = "seforim.db"
        const val FOLDER = "library"
        const val PHONE_LABEL = "זיכרון הטלפון"
        const val CARD_LABEL = "כרטיס הזיכרון"
        const val INTERNAL_LABEL = "אחסון פנימי של האפליקציה"
        private const val NOTE_NAME = "README.txt"
        private const val NOTE_TEXT =
            "פרדס: העתיקו לתיקייה הזו את קבצי הספרייה של זית למחשב (seforim.db והקבצים שלצדו),\n" +
                "או ספרייה קטנה מ-Zayit Subset. אחרי ההעתקה פתחו את פרדס.\n"
    }
}

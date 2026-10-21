package io.github.kdroidfilter.seforimapp.android.data

import android.content.Context
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar.Parsha
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/** A place in a book, as the reader shows favorites: [heading] is the TOC label after "<book> - ". */
data class ReadingEntry(val bookId: Long, val lineId: Long?, val title: String, val heading: String, val time: Long) {
    /** "בראשית - פרק א", as desktop names tabs and history rows. */
    val label: String get() = if (heading.isBlank()) title else "$title - $heading"
}

/**
 * The reader's link to the desktop history and favorites ([UserDataStore.history], [UserDataStore.favorites]), with
 * desktop's recording rules (BookContentViewModel, 0.6.7 lines 344-379): ONE history row per book opened in a tab,
 * written 2 s after the open with the TOC entry read then, titled "<book> - <toc>" (never "X - X"); never for a tab
 * restored with the session, a tab in the background, or the tablet's search preview.
 *
 * The shell [armBookVisit]s a book when a tab opens it; the reader calls [record] whenever its position changes and
 * only an armed book is written. Where the reader stands (for the session, "reopen closed tab" and the tab title) goes
 * to the shell through the tab's TabSession, not through here.
 *
 * Also moves the history and favorites the app kept in SharedPreferences before it used desktop's tables, and the
 * Shnayim Mikra ticks of older Android builds ([migrateOldShnayimMikraTicks]).
 */
class ReadingHistory(private val app: ZayitApplication) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val armed = HashSet<Long>()
    private val pending = HashMap<Long, Job>()
    private val bookTitles = ConcurrentHashMap<Long, String>()

    private val _bookmarks = MutableStateFlow<List<ReadingEntry>>(emptyList())

    /** The favorites behind [bookmarks], with their keys in the favorite table. */
    @Volatile
    private var bookmarkKeys: List<Pair<ReadingEntry, String>> = emptyList()

    /** The favorites, for the reader's star (`bookmarks.any { it.bookId == id && it.heading == heading }`). */
    val bookmarks: StateFlow<List<ReadingEntry>>
        get() {
            start()
            return _bookmarks.asStateFlow()
        }

    private var started = false

    /**
     * Starts following the favorites (and moves the old SharedPreferences data) on first use by a screen: never
     * earlier, as resolving titles opens the library, which must not happen before MainActivity's library check
     * (opening a missing library would create an empty one). The shell also calls it once it is shown, so the old
     * SharedPreferences history and favorites reach the history and favorites pages before any book is opened.
     * Main thread.
     */
    fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) {
            runCatching { migrateLegacyPrefs() }
            runCatching { migrateOldShnayimMikraTicks() }
            app.userData.favorites.revision.collect { runCatching { reloadBookmarks() } }
        }
    }

    /** A tab opened [bookId] (not a restore): its first position read 2 s later goes into the history. */
    fun armBookVisit(bookId: Long) {
        pending.remove(bookId)?.cancel()
        armed += bookId
    }

    /** The reader's position changed: [lineId] is the line read (the selected one, else the first visible), [heading] its chapter. */
    fun record(bookId: Long, lineId: Long?, title: String, heading: String) {
        start()
        if (bookId !in armed) return
        pending.remove(bookId)?.cancel()
        pending[bookId] = scope.launch {
            delay(HISTORY_RECORD_DEBOUNCE_MS)
            armed -= bookId
            pending.remove(bookId)
            runCatching {
                val toc = tocFor(lineId)
                // Some books' root TOC entry repeats the book title: avoid "X - X".
                val tocLabel = (toc?.text ?: heading).takeIf { it.isNotBlank() && it != title }
                app.userData.history.recordBookVisit(
                    bookId = bookId,
                    title = if (tocLabel != null) "$title - $tocLabel" else title,
                    timestamp = System.currentTimeMillis(),
                    tocEntryId = toc?.id,
                    lineId = toc?.lineId,
                )
            }
        }
    }

    fun clearHistory() {
        scope.launch { runCatching { app.userData.history.clearAll() } }
    }

    fun isBookmarked(bookId: Long, heading: String): Boolean = _bookmarks.value.any { it.bookId == bookId && it.heading == heading }

    /**
     * The reader star's click (desktop BreadcrumbActionsView): removes the favorites the star shows for this chapter,
     * else adds one at (book, TOC entry of [lineId]), titled "<book> - <chapter>", reopening at [lineId].
     */
    fun toggleBookmark(bookId: Long, lineId: Long?, title: String, heading: String) {
        start()
        val shown = bookmarkKeys.filter { (entry, _) -> entry.bookId == bookId && entry.heading == heading }.map { it.second }
        scope.launch {
            runCatching {
                if (shown.isNotEmpty()) {
                    shown.forEach { app.userData.favorites.remove(it) }
                } else {
                    val toc = tocFor(lineId)
                    val tocLabel = heading.ifBlank { toc?.text.orEmpty() }.takeIf { it.isNotBlank() && it != title }
                    bookTitles[bookId] = title
                    app.userData.favorites.add(bookId, if (tocLabel != null) "$title - $tocLabel" else title, System.currentTimeMillis(), toc?.id, lineId ?: toc?.lineId)
                }
            }
        }
    }

    fun removeBookmark(entry: ReadingEntry) {
        val keys = bookmarkKeys.filter { it.first == entry }.map { it.second }
        scope.launch { runCatching { keys.forEach { app.userData.favorites.remove(it) } } }
    }

    private suspend fun tocFor(lineId: Long?): TocEntry? = lineId?.let { id ->
        withContext(Dispatchers.IO) { runCatching { app.libraryStore.repository().getTocEntryForLine(id) }.getOrNull() }
    }

    private suspend fun reloadBookmarks() {
        val entries = app.userData.favorites.query()
        val list = entries.map { e ->
            val bookTitle = bookTitles[e.bookId] ?: withContext(Dispatchers.IO) {
                runCatching { app.libraryStore.repository().getBookCore(e.bookId)?.title }.getOrNull()
            }?.also { bookTitles[e.bookId] = it }
            val heading = bookTitle?.let { t -> e.title.removePrefix("$t - ").takeIf { it != e.title } }.orEmpty()
            ReadingEntry(e.bookId, e.lineId, bookTitle ?: e.title, heading, e.createdAt) to e.key
        }
        bookmarkKeys = list
        _bookmarks.value = list.map { it.first }
    }

    /** One-time move of the SharedPreferences history and favorites (the app's first format) into desktop's tables. */
    private suspend fun migrateLegacyPrefs() {
        val sp = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (!sp.contains(KEY_HISTORY) && !sp.contains(KEY_BOOKMARKS)) return
        fun load(key: String): List<ReadingEntry> = runCatching {
            val arr = JSONArray(sp.getString(key, "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ReadingEntry(o.getLong("bookId"), if (o.has("lineId")) o.getLong("lineId") else null, o.getString("title"), o.optString("heading"), o.optLong("time"))
            }
        }.getOrDefault(emptyList())
        load(KEY_HISTORY).asReversed().forEach { e ->
            val toc = tocFor(e.lineId)
            app.userData.history.recordBookVisit(e.bookId, e.label, e.time, toc?.id, toc?.lineId ?: e.lineId)
        }
        load(KEY_BOOKMARKS).asReversed().forEach { e ->
            val toc = tocFor(e.lineId)
            app.userData.favorites.add(e.bookId, e.label, e.time, toc?.id, e.lineId ?: toc?.lineId)
        }
        sp.edit().clear().apply()
    }

    /**
     * Once: the Shnayim Mikra ticks older Android builds kept in their own preferences (sm_<Hebrew year>_<parsha
     * 1–54>_<aliya 1–7>) move to the user DB, desktop's table (a double parsha's stay its first's), so the Home widget
     * and a backup see them before the tool is ever opened. IO thread.
     */
    private suspend fun migrateOldShnayimMikraTicks() {
        val prefs = app.getSharedPreferences(TOOLS_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(OLD_TICKS_MOVED, false)) return
        val old = prefs.all.keys.filter { OLD_TICK.matches(it) }
        val store = app.userData.shnayimMikra
        for (key in old) {
            if (!prefs.getBoolean(key, false)) continue
            val (year, parsha, aliya) = OLD_TICK.matchEntire(key)!!.destructured
            val p = Parsha.entries.getOrNull(parsha.toInt()) ?: continue
            runCatching { store.setRead(year.toLong(), p.name, aliya.toInt() - 1, true) }
        }
        prefs.edit().apply { old.forEach(::remove) }.putBoolean(OLD_TICKS_MOVED, true).apply()
    }

    private companion object {
        const val HISTORY_RECORD_DEBOUNCE_MS = 2_000L
        const val LEGACY_PREFS = "zayit_history"
        const val KEY_HISTORY = "history"
        const val KEY_BOOKMARKS = "bookmarks"
        const val TOOLS_PREFS = "zayit_tools"
        const val OLD_TICKS_MOVED = "sm_moved_to_user_db"
        val OLD_TICK = Regex("""sm_(\d+)_(\d+)_(\d)""")
    }
}

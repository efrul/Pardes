package io.github.kdroidfilter.seforimapp.legacy.data

import android.content.Context
import io.github.kdroidfilter.seforimapp.android.ui.reader.DiacriticsMode
import org.json.JSONArray
import org.json.JSONObject

/** A book opened lately, or a marked passage. */
data class Place(val bookId: Long, val title: String, val lineIndex: Int, val note: String = "")

/** How a book's niqqud and cantillation marks are shown, as Settings names the main app's DiacriticsMode. */
val DiacriticsMode.label: String
    get() = when (this) {
        DiacriticsMode.All -> "ניקוד וטעמים"
        DiacriticsMode.NikudOnly -> "ניקוד בלי טעמים"
        DiacriticsMode.None -> "בלי ניקוד"
    }

/** The book fonts this edition carries (assets/fonts), the main app's defaults among them, or the system's. */
enum class BookFont(val label: String, val asset: String?) {
    NotoSerif("נוטו סריף עברית", "fonts/notoserifhebrew.ttf"),
    FrankRuhl("פרנק רוהל ליברה", "fonts/frankruhllibre.ttf"),
    TaameyFrank("טעמי פרנק", "fonts/taameyfrankclm_medium.ttf"),
    System("גופן המערכת", null),
}

/** The settings, history and bookmarks of this edition (one preferences file; the main app's are not read). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pardes_legacy", Context.MODE_PRIVATE)

    var dark: Boolean
        get() = sp.getBoolean(KEY_DARK, false)
        set(value) = sp.edit().putBoolean(KEY_DARK, value).apply()

    /** The book text's size in sp. */
    var textSize: Int
        get() = sp.getInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE)
        set(value) = sp.edit().putInt(KEY_TEXT_SIZE, value.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)).apply()

    var bookFont: BookFont
        get() = sp.getString(KEY_BOOK_FONT, null)?.let { name -> BookFont.entries.firstOrNull { it.name == name } } ?: BookFont.NotoSerif
        set(value) = sp.edit().putString(KEY_BOOK_FONT, value.name).apply()

    var diacritics: DiacriticsMode
        get() = sp.getString(KEY_DIACRITICS, null)?.let { name -> if (name == "Niqqud") DiacriticsMode.NikudOnly else DiacriticsMode.entries.firstOrNull { it.name == name } } ?: DiacriticsMode.All
        set(value) = sp.edit().putString(KEY_DIACRITICS, value.name).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_SCREEN_ON, true)
        set(value) = sp.edit().putBoolean(KEY_SCREEN_ON, value).apply()

    /** The Home's wallpaper (the main app's תמונת רקע); a phone Android counts as low on memory never shows it. */
    var wallpaper: Boolean
        get() = sp.getBoolean(KEY_WALLPAPER, true)
        set(value) = sp.edit().putBoolean(KEY_WALLPAPER, value).apply()

    /** The commentators a book's pane shows (the main app's per-book choice), null until chosen. */
    fun commentators(bookId: Long): Set<Long>? =
        sp.getString(KEY_COMMENTATORS + bookId, null)?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet()

    fun setCommentators(bookId: Long, ids: Set<Long>) = sp.edit().putString(KEY_COMMENTATORS + bookId, ids.joinToString(",")).apply()

    /** The folder the library was last opened from. */
    var libraryDir: String?
        get() = sp.getString(KEY_LIBRARY_DIR, null)
        set(value) = sp.edit().putString(KEY_LIBRARY_DIR, value).apply()

    /**
     * Why the search last fell back from a level (LegacySearch): the library's release, this app's version code, the
     * level that failed and what Lucene threw, as LegacySearch writes it; null when nothing failed.
     */
    var searchFailure: String?
        get() = sp.getString(KEY_SEARCH_FAILURE, null)
        set(value) = sp.edit().apply { if (value == null) remove(KEY_SEARCH_FAILURE) else putString(KEY_SEARCH_FAILURE, value) }.apply()

    /** Opens the full-text index even with a heap LegacySearch finds too small for it (the user's choice). */
    var searchIndexOnSmallHeap: Boolean
        get() = sp.getBoolean(KEY_SEARCH_SMALL_HEAP, false)
        set(value) = sp.edit().putBoolean(KEY_SEARCH_SMALL_HEAP, value).apply()

    /** Books opened lately, the latest first, each with the line it was left at. */
    val recents: List<Place> get() = readPlaces(KEY_RECENTS)

    fun rememberPlace(place: Place) {
        val list = listOf(place) + recents.filter { it.bookId != place.bookId }
        writePlaces(KEY_RECENTS, list.take(MAX_RECENTS))
    }

    fun lastLine(bookId: Long): Int? = recents.firstOrNull { it.bookId == bookId }?.lineIndex

    fun forgetRecent(bookId: Long) = writePlaces(KEY_RECENTS, recents.filter { it.bookId != bookId })

    fun isBookmarked(bookId: Long, lineIndex: Int): Boolean = bookmarks.any { it.bookId == bookId && it.lineIndex == lineIndex }

    val bookmarks: List<Place> get() = readPlaces(KEY_BOOKMARKS)

    fun addBookmark(place: Place) {
        writePlaces(KEY_BOOKMARKS, listOf(place) + bookmarks.filterNot { it.bookId == place.bookId && it.lineIndex == place.lineIndex })
    }

    fun removeBookmark(place: Place) {
        writePlaces(KEY_BOOKMARKS, bookmarks.filterNot { it.bookId == place.bookId && it.lineIndex == place.lineIndex })
    }

    fun clearRecents() = sp.edit().remove(KEY_RECENTS).apply()

    private fun readPlaces(key: String): List<Place> = runCatching {
        val array = JSONArray(sp.getString(key, null) ?: return emptyList())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Place(o.getLong("book"), o.getString("title"), o.getInt("line"), o.optString("note"))
        }
    }.getOrDefault(emptyList())

    private fun writePlaces(key: String, places: List<Place>) {
        val array = JSONArray()
        places.forEach { p ->
            array.put(JSONObject().put("book", p.bookId).put("title", p.title).put("line", p.lineIndex).put("note", p.note))
        }
        sp.edit().putString(key, array.toString()).apply()
    }

    companion object {
        /** The main app's (desktop AppSettings) default and largest sizes and zoom step; 12 is smaller, for small screens. */
        const val DEFAULT_TEXT_SIZE = 16
        const val MIN_TEXT_SIZE = 12
        const val MAX_TEXT_SIZE = 50
        const val TEXT_SIZE_STEP = 2
        private const val MAX_RECENTS = 30

        private const val KEY_DARK = "dark"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_BOOK_FONT = "book_font"
        private const val KEY_DIACRITICS = "diacritics"
        private const val KEY_SCREEN_ON = "keep_screen_on"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_COMMENTATORS = "commentators_"
        private const val KEY_LIBRARY_DIR = "library_dir"
        private const val KEY_SEARCH_FAILURE = "search_failure"
        private const val KEY_SEARCH_SMALL_HEAP = "search_index_small_heap"
        private const val KEY_RECENTS = "recents"
        private const val KEY_BOOKMARKS = "bookmarks"
    }
}

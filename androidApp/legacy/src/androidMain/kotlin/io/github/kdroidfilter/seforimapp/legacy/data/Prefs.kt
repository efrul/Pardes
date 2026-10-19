package io.github.kdroidfilter.seforimapp.legacy.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A book opened lately, or a marked passage. */
data class Place(val bookId: Long, val title: String, val lineIndex: Int, val note: String = "")

/** How a book's niqqud and cantillation marks are shown (the main app's ניקוד toggle, with the cantillation as a step). */
enum class Diacritics(val label: String) {
    All("ניקוד וטעמים"),
    Niqqud("ניקוד בלי טעמים"),
    None("בלי ניקוד"),
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

    var diacritics: Diacritics
        get() = sp.getString(KEY_DIACRITICS, null)?.let { name -> Diacritics.entries.firstOrNull { it.name == name } } ?: Diacritics.All
        set(value) = sp.edit().putString(KEY_DIACRITICS, value.name).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_SCREEN_ON, true)
        set(value) = sp.edit().putBoolean(KEY_SCREEN_ON, value).apply()

    /** The folder the library was last opened from. */
    var libraryDir: String?
        get() = sp.getString(KEY_LIBRARY_DIR, null)
        set(value) = sp.edit().putString(KEY_LIBRARY_DIR, value).apply()

    /** Books opened lately, the latest first, each with the line it was left at. */
    val recents: List<Place> get() = readPlaces(KEY_RECENTS)

    fun rememberPlace(place: Place) {
        val list = listOf(place) + recents.filter { it.bookId != place.bookId }
        writePlaces(KEY_RECENTS, list.take(MAX_RECENTS))
    }

    fun lastLine(bookId: Long): Int? = recents.firstOrNull { it.bookId == bookId }?.lineIndex

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
        const val DEFAULT_TEXT_SIZE = 20
        const val MIN_TEXT_SIZE = 12
        const val MAX_TEXT_SIZE = 40
        private const val MAX_RECENTS = 30

        private const val KEY_DARK = "dark"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_BOOK_FONT = "book_font"
        private const val KEY_DIACRITICS = "diacritics"
        private const val KEY_SCREEN_ON = "keep_screen_on"
        private const val KEY_LIBRARY_DIR = "library_dir"
        private const val KEY_RECENTS = "recents"
        private const val KEY_BOOKMARKS = "bookmarks"
    }
}

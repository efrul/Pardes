package io.github.kdroidfilter.seforimapp.android.ui.reader

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The reader's own persisted switches (desktop AppSettings.bookDetailsPaneFlow, on by default, and the commentaries
 * pane's isCommentatorsListVisible), apart from AppPrefs: one setting for every tab, as desktop.
 */
class ReaderPrefs private constructor(context: Context) {
    private val sp = context.getSharedPreferences("zayit_reader", Context.MODE_PRIVATE)

    private val _bookDetails = MutableStateFlow(sp.getBoolean(KEY_BOOK_DETAILS, true))

    /** The tablet's book-details pane (desktop "פרטי הספר" end-rail toggle). */
    val bookDetails: StateFlow<Boolean> = _bookDetails.asStateFlow()

    private val _commentatorsList = MutableStateFlow(sp.getBoolean(KEY_COMMENTATORS_LIST, true))

    /** The commentators list beside the commentaries (desktop "הסתר/הצג סרגל מפרשים"). */
    val commentatorsList: StateFlow<Boolean> = _commentatorsList.asStateFlow()

    fun setBookDetails(visible: Boolean) {
        _bookDetails.value = visible
        sp.edit().putBoolean(KEY_BOOK_DETAILS, visible).apply()
    }

    fun setCommentatorsList(visible: Boolean) {
        _commentatorsList.value = visible
        sp.edit().putBoolean(KEY_COMMENTATORS_LIST, visible).apply()
    }

    companion object {
        private const val KEY_BOOK_DETAILS = "book_details_pane"
        private const val KEY_COMMENTATORS_LIST = "commentators_list_visible"

        @Volatile
        private var instance: ReaderPrefs? = null

        fun get(context: Context): ReaderPrefs = instance ?: synchronized(this) { instance ?: ReaderPrefs(context.applicationContext).also { instance = it } }
    }
}

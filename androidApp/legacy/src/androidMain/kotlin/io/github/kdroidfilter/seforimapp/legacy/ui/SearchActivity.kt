package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.data.SearchScope
import io.github.kdroidfilter.seforimapp.android.search.AUTHOR_ERAS
import io.github.kdroidfilter.seforimapp.android.search.BookDetailsLoader
import io.github.kdroidfilter.seforimapp.android.search.OTZARIA_CREDIT
import io.github.kdroidfilter.seforimapp.android.search.OTZARIA_URL
import io.github.kdroidfilter.seforimapp.android.search.SearchEntity
import io.github.kdroidfilter.seforimapp.android.search.SearchEntityFinder
import io.github.kdroidfilter.seforimapp.android.search.SearchService
import io.github.kdroidfilter.seforimapp.android.search.authorYears
import io.github.kdroidfilter.seforimapp.android.search.licenseLabel
import io.github.kdroidfilter.seforimapp.android.search.plainPersonLinks
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.search.AUTHOR_BOOKS_COUNT
import io.github.kdroidfilter.seforimapp.android.ui.search.AUTOCOMPLETE_LOADING
import io.github.kdroidfilter.seforimapp.android.ui.search.AUTOCOMPLETE_NO_RESULTS
import io.github.kdroidfilter.seforimapp.android.ui.search.BREADCRUMB_SEPARATOR
import io.github.kdroidfilter.seforimapp.android.ui.search.CatBookRow
import io.github.kdroidfilter.seforimapp.android.ui.search.HINTS
import io.github.kdroidfilter.seforimapp.android.ui.search.REMOVE_SELECTED_BOOK
import io.github.kdroidfilter.seforimapp.android.ui.search.ROW_ACTION_OPEN
import io.github.kdroidfilter.seforimapp.android.ui.search.ROW_ACTION_SEARCH
import io.github.kdroidfilter.seforimapp.android.ui.search.ROW_ACTION_SEARCH_ALL
import io.github.kdroidfilter.seforimapp.android.ui.search.ROW_ACTION_SEARCH_BASE
import io.github.kdroidfilter.seforimapp.android.ui.search.ROW_ACTION_SEARCH_INSIDE
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_BACK_TO_ALL
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_CLEAR
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_FAILED
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_INDEX_MISSING
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_IN_BOOK_PLACEHOLDER
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_LIBRARY_FOLDER
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_MORE_FROM_BOOK
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_NO_RESULTS
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_PANEL_AUTHOR
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_PANEL_AUTHOR_PAGE
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_PANEL_PARTS
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_PANEL_SOURCE
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_SEARCHING
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_STATUS_ALL
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_STATUS_BASE
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_STATUS_FALLBACK
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_STATUS_IN
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_STOP
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_SWITCH_ALL
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_SWITCH_BASE
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_TABS_LESS
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_TABS_MORE
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_TAB_ALL
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_TEXT_ROW
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_TEXT_ROW_IN_AUTHOR
import io.github.kdroidfilter.seforimapp.android.ui.search.SEARCH_TEXT_ROW_IN_BOOK
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchAvailability
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchBarUiState
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchBarViewModel
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchTreeCategory
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchUiState
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchViewModel
import io.github.kdroidfilter.seforimapp.android.ui.search.TocSuggestionDto
import io.github.kdroidfilter.seforimapp.android.ui.search.catBookRows
import io.github.kdroidfilter.seforimapp.android.ui.search.categoriesAndPlace
import io.github.kdroidfilter.seforimapp.android.ui.search.compactSnippet
import io.github.kdroidfilter.seforimapp.android.ui.search.dedupAdjacent
import io.github.kdroidfilter.seforimapp.android.ui.search.fill
import io.github.kdroidfilter.seforimapp.android.ui.search.flattenResults
import io.github.kdroidfilter.seforimapp.android.ui.search.formatCount
import io.github.kdroidfilter.seforimapp.android.ui.search.tocSuggestionsForDisplay
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.LegacySearch
import io.github.kdroidfilter.seforimapp.legacy.data.NoLibraryException
import io.github.kdroidfilter.seforimapp.legacy.data.ScanSearch
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The search, as the main app's results page (SearchScreen) under its one smart bar (UnifiedSearchBar), on the main
 * app's own view models (SearchViewModel, SearchBarViewModel) and engine: the desktop's prebuilt Lucene index of the
 * library (LegacySearch, Level.Full).
 *
 * As one types, the bar suggests the places it names (ברכות דף ב), the books and authors (from the lookup index), and
 * the text search in the base books or in all of them; in a book picked ("חיפוש בתוך", or the reader's find) its table
 * of contents and the search in it. The suggestions take the results' place while the field is edited, and Back
 * gives the results again. The results come as the main app's: the base books first (all of them when they have
 * nothing), by relevance, one passage per run of a book with "עוד N תוצאות מ…" (that book's results, and the way
 * back), the categories as tabs from the facets, pages of 25 loaded as the list nears its end, the place in the book,
 * the knowledge panel of the book or author named, and the states of a search that failed or of an index missing.
 *
 * Where the library has no full-text index, or the phone cannot open it (LegacySearch's levels Lookup and Scan), the
 * text is searched by reading the books one after the other (ScanSearch), in the same screen and look, under the
 * original's text saying why.
 *
 * A keypad starts in the field; OK runs the row highlighted (the first) or the one chosen in the list, a long OK offers
 * its other actions (פתיחה, חיפוש בתוך, בספרי יסוד / בכל הספרים); Back closes the suggestions, then one book's
 * results, then the screen. The view models and the scan outlive a rotation and are cleared when the screen goes, and
 * the search shown is searched again in a screen built anew after Android ended the app behind it.
 */
class SearchActivity : BaseActivity() {
    /** What the screen keeps across a rotation: its view models, its scan, and what the bar holds. */
    private class Models(
        val opened: Library.Opened,
        val service: SearchService,
        val search: SearchViewModel,
        val bar: SearchBarViewModel,
        val scan: ScanSearch,
    ) {
        /**
         * Where a search the screen asked for starts (its level asked first, off the main thread), and the scan's
         * knowledge panel is found: a rotation meanwhile does not stop them. Its coroutines reach the view models and
         * the scan only, never a screen, and say what changed through [changes]; it ends with [clear].
         */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        /** The last text search started, until it reached the view model or the scan. */
        var searchJob: Job? = null

        /** Bumped when what the screen shows changed outside the view models' flows (the scan chosen, and why). */
        val changes = MutableStateFlow(0)

        /** The results shown are the scan's: the search ran below LegacySearch.Level.Full. */
        var scanMode = false

        /** A text search has run, so the screen shows its results (or their state) when not suggesting. */
        var searched = false

        /** The field is being edited: its suggestions are shown in the results' place. */
        var suggesting = false

        /** What the field holds in the library ([reference]) and in a book picked ([toc]: its TOC, the search in it). */
        var reference = ""
        var toc = ""

        /** The book's field was typed in since the book was picked: Backspace in it then drops no book. */
        var tocEdited = false

        /** The category tabs past the first five are shown ("עוד"), for the tabs of [allTabsOf] only. */
        var allTabs = false
        var allTabsOf: List<SearchTreeCategory>? = null

        /** The last text search, to run again ("נסה שוב", a level that changed meanwhile). */
        var lastQuery = ""
        var lastScope: SearchScope = SearchScope.Global
        var lastExtended = false

        /** Why the text is searched by the scan (LegacySearch.textUnavailable), read when the scan started. */
        var reason: LegacySearch.TextUnavailable? = null

        /** Where the list stood, for the screen built again after a rotation. */
        var listTop = 0 to 0

        /**
         * The knowledge panel of the scan's query: the view model finds the book or author its own searches name
         * (SearchViewModel.entity), and the lookup index, open at LegacySearch.Level.Lookup, finds the scan's the same way.
         */
        val scanEntity = MutableStateFlow<SearchEntity?>(null)
        private var entityJob: Job? = null
        private val bookDetails by lazy { BookDetailsLoader(opened.repository) }

        fun changed() {
            changes.value = changes.value + 1
        }

        /**
         * The scan's search of [q] in [barScope]'s books (an author's, all of them; a TOC entry's book whole), with why
         * the text is not searched in the index ([legacy]'s textUnavailable) and the knowledge panel of [q].
         */
        suspend fun startScan(legacy: LegacySearch, q: String, barScope: SearchScope, extended: Boolean) {
            scanMode = true
            val picked = bar.uiState.value
            val (why, books) = withContext(Dispatchers.IO) {
                val books = runCatching {
                    when (barScope) {
                        is SearchScope.Book -> listOfNotNull(picked.selectedScopeBook?.takeIf { it.id == barScope.bookId } ?: opened.repository.getBookCore(barScope.bookId))
                        is SearchScope.Toc -> listOfNotNull(picked.selectedScopeBook?.takeIf { it.id == barScope.bookId } ?: opened.repository.getBookCore(barScope.bookId))
                        is SearchScope.Author -> opened.repository.getAuthorDetails(barScope.authorId)?.books.orEmpty()
                        else -> null
                    }
                }.getOrNull()
                legacy.textUnavailable() to books
            }
            reason = why
            val title = when (barScope) {
                is SearchScope.Author -> picked.selectedScopeAuthor?.name?.let { "ספרי " + AuthorNames.display(it) }
                else -> books?.singleOrNull()?.title
            }
            scan.search(q, books, title, extended)
            findScanEntity(q)
            changed()
        }

        private fun findScanEntity(q: String) {
            entityJob?.cancel()
            scanEntity.value = null
            entityJob = scope.launch {
                scanEntity.value = withContext(Dispatchers.IO) {
                    runCatching {
                        val lookup = service.lookup() ?: return@runCatching null
                        SearchEntityFinder(lookup, opened.repository, bookDetails).find(q)
                    }.getOrNull()
                }
            }
        }

        /** The view model's search shows its own panel. */
        fun dropScanEntity() {
            entityJob?.cancel()
            scanEntity.value = null
        }

        fun clear() {
            scope.cancel()
            search.clear()
            bar.clear()
            scan.clear()
        }
    }

    /** A row of the list. */
    private sealed class Item {
        /** A suggestion: [main] runs on OK or a tap, [actions] on its buttons (the highlighted row's) or a long OK. */
        class Suggestion(
            val kind: SuggestionKind,
            val parts: List<String>,
            val emphasis: String?,
            val detail: String?,
            val actions: List<RowAction>,
            val always: List<RowAction>,
            val main: RowAction,
        ) : Item() {
            var label: CharSequence? = null
        }

        class Message(val text: String, val small: Boolean) : Item()

        class Result(val hit: SearchResult, val last: Boolean) : Item()

        class More(val hit: SearchResult, val count: Int) : Item() {
            val text: String by lazy { SEARCH_MORE_FROM_BOOK.fill(count, hit.bookTitle) }
        }

        class Entity(val entity: SearchEntity) : Item()

        /** Why the text is searched by the scan, or the search failed: in [full], or a line that opens it (over results). */
        class Notice(val notice: LegacySearch.TextUnavailable, val full: Boolean) : Item()

        data object LoadingMore : Item()
    }

    private var models: Models? = null

    /** False once the screen goes for good (or is built anew on a library opened again): its models are cleared. */
    private var keepModels = true

    private lateinit var root: LinearLayout
    private lateinit var bar: TopBar
    private lateinit var field: EditText
    private lateinit var clearButton: View
    private lateinit var chip: TextView
    private lateinit var tabsBox: FrameLayout
    private lateinit var backLink: TextView
    private lateinit var statusRow: LinearLayout
    private lateinit var statusTexts: FlowRow
    private lateinit var status: TextView
    private lateinit var statusDot: TextView
    private lateinit var statusLink: TextView
    private lateinit var progress: ProgressTrack
    private lateinit var stop: View
    private lateinit var list: ListView
    private lateinit var centerText: TextView
    private lateinit var waiting: View
    private lateinit var overlay: View

    /** Below 360dp (most phones with keys): no logo, the tabs on one scrolling line, a third line of passage. */
    private var compact = false

    /** A list under 320dp: a highlighted row too keeps to its main button, and two buttons go under the text. */
    private var narrow = false

    /** The field's text is being set by the screen, not typed: no suggestions for it. */
    private var settingField = false

    /** Backspace went down in an empty field: its release drops the book or author picked. */
    private var delStartedEmpty = false

    private val adapter = ItemsAdapter<Item>(
        types = 7,
        typeOf = { item ->
            when (item) {
                is Item.Suggestion -> 0
                is Item.Message -> 1
                is Item.Result -> 2
                is Item.More -> 3
                is Item.Entity -> 4
                is Item.Notice -> 5
                Item.LoadingMore -> 6
            }
        },
    ) { item, reuse, _ -> bind(item, reuse) }

    override val heldLibrary: Library.Opened? get() = models?.opened

    /**
     * The library was closed behind this screen and opened again: its view models read through the old connection, so
     * the screen is built anew on the new one, searching again what it had searched.
     */
    override fun onLibraryReopened(library: Library.Opened) {
        val m = models
        if (m != null && m.searched && m.lastQuery.isNotEmpty()) intent.putExtra(EXTRA_QUERY, m.lastQuery).putExtra(EXTRA_RUN, true)
        keepModels = false
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val kept = lastNonConfigurationInstance as? Models
        // Built again after Android ended the app behind it (a rotation keeps the models): the search it showed
        val saved = savedInstanceState?.takeIf { kept == null }
        val run = intent.getBooleanExtra(EXTRA_RUN, false) || saved?.getString(STATE_QUERY) != null
        // A zayit://search link searches at once: no keyboard over its results
        @Suppress("DEPRECATION")
        if (run) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        compact = resources.configuration.screenWidthDp < 360
        narrow = resources.configuration.screenWidthDp < 336
        buildViews()
        // What was typed, which no view of the screen keeps (the field has no id: a rotation sets it from the models)
        saved?.getString(STATE_FIELD)?.let { showField(it) }
        if (kept != null) attach(kept) else open(run, saved)
    }

    @Deprecated("Deprecated in Java")
    override fun onRetainNonConfigurationInstance(): Any? = models?.takeIf { keepModels }

    /** The search shown (its text, where, in all the books or not) and the field, for a screen built again without its models. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_FIELD, field.text.toString())
        val m = models ?: return
        if (!m.searched || m.lastQuery.isEmpty()) return
        outState.putString(STATE_QUERY, m.lastQuery)
        outState.putBoolean(STATE_EXTENDED, m.lastExtended)
        when (val where = m.lastScope) {
            is SearchScope.Book -> outState.putLong(STATE_BOOK, where.bookId)
            is SearchScope.Toc -> {
                outState.putLong(STATE_BOOK, where.bookId)
                outState.putLong(STATE_TOC, where.tocId)
            }
            is SearchScope.Author -> outState.putLong(STATE_AUTHOR, where.authorId)
            else -> Unit
        }
    }

    override fun onResume() {
        super.onResume()
        if (rebuilding) return
        // Back from a book opened from the results: the scan goes on from the book it was in
        models?.scan?.resume()
    }

    override fun onStop() {
        // The scan stops while another screen reads the library (a book opened from here), to go on when this one is back
        models?.scan?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        list.removeCallbacks(renderTask)
        if (!isChangingConfigurations || !keepModels) models?.clear()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val m = models
        when {
            // The suggestions over results: back to the results, as the main app's popup closes
            m != null && m.suggesting && m.searched -> closeSuggestions(m)
            // One book's results: back to all of them, where the list was
            m != null && !m.suggesting && m.searched && state(m).bookFilterIds.isNotEmpty() -> backFromBook(m)
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    // ------------------------------------------------------------------ building

    private fun buildViews() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_back, "חזרה") { finish() }
            title.text = "חיפוש"
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))

        // One card: the bar, the tabs and the status over the list, as the main app's results page.
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(colors.panel, dp(12).toFloat())
        }
        val side = dp(if (compact) 10 else 14)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(side, dp(if (compact) 8 else 12), side, dp(6))
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // The logo beside the bar, as Google's, where there is room for it.
        if (!compact) {
            head.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }, lp(dp(44), dp(44)).apply { marginEnd = dp(10) })
        }
        head.addView(searchBar(), lp(0, WRAP, 1f))
        header.addView(head, lp(MATCH, WRAP))

        tabsBox = FrameLayout(this).apply { visibility = View.GONE }
        header.addView(tabsBox, lp(MATCH, WRAP).apply { topMargin = dp(if (compact) 8 else 10) })

        backLink = label("", 13f, colors.accent, maxLines = 2, typeface = Fonts.ui(this)).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            minHeight = dp(36)
            setPadding(dp(4), 0, dp(4), 0)
            background = states(colors, 0, dp(6).toFloat())
            visibility = View.GONE
        }
        header.addView(backLink, lp(MATCH, WRAP).apply { topMargin = dp(4) })

        statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        statusTexts = FlowRow(this, dp(6), 0, centered = false)
        val grey = Palette.withAlpha(colors.text, 0.72f)
        status = label("", 14f, grey, typeface = Fonts.ui(this)).apply {
            minHeight = dp(if (compact) 24 else 36)
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = label("·", 14f, grey).apply {
            minHeight = dp(if (compact) 24 else 36)
            gravity = Gravity.CENTER_VERTICAL
        }
        statusLink = label("", 14f, colors.accent, maxLines = 1, typeface = Fonts.ui(this)).apply {
            gravity = Gravity.CENTER
            minHeight = dp(36)
            setPadding(dp(4), 0, dp(4), 0)
            background = states(colors, 0, dp(6).toFloat())
        }
        statusTexts.addView(status)
        statusTexts.addView(statusDot)
        statusTexts.addView(statusLink)
        statusRow.addView(statusTexts, lp(0, WRAP, 1f))
        progress = ProgressTrack(this, colors).apply { visibility = View.GONE }
        statusRow.addView(progress, lp(0, dp(4), 1f).apply { marginStart = dp(12) })
        stop = iconButton(R.drawable.ic_close, colors.secondary, SEARCH_STOP, colors) { cancelSearch() }.apply { visibility = View.GONE }
        statusRow.addView(stop)
        header.addView(statusRow, lp(MATCH, WRAP).apply { topMargin = dp(if (compact) 4 else 8) })
        card.addView(header, lp(MATCH, WRAP))
        card.addView(View(this).apply { setBackgroundColor(colors.border) }, lp(MATCH, maxOf(1, dp(1))))

        list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            // The rows' highlight leaves out the space under them (ROW_GAP_DP), as the main app's items are spaced. A layer
            // inset, not an InsetDrawable: AbsListView widens its selector by the padding that one would report
            selector = LayerDrawable(arrayOf<Drawable>(states(colors, 0, dp(6).toFloat()))).apply { setLayerInset(0, 0, 0, 0, dp(ROW_GAP_DP)) }
            cacheColorHint = 0
            // The knowledge panel's links and the notices' buttons are reached by a keypad inside their rows
            itemsCanFocus = true
            setPadding(dp(8), dp(4), dp(8), dp(16))
            clipToPadding = false
            adapter = this@SearchActivity.adapter
            setOnItemClickListener { _, _, position, _ -> activate(position) }
            setOnItemLongClickListener { _, _, position, _ -> moreActions(position) }
            setOnScrollListener(scrolled)
            // The first row is the one Enter runs while the field has the focus; the list's own highlight once in it
            onFocusChangeListener = View.OnFocusChangeListener { _, _ -> requestRender() }
        }
        centerText = label("", prefs.textSize * 0.875f, colors.text, typeface = Fonts.ui(this)).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        waiting = spinner(colors).apply { visibility = View.GONE }
        // Over the results while a tab or one book's results are searched (the main app's filtering loader)
        overlay = FrameLayout(this).apply {
            setBackgroundColor(Palette.withAlpha(colors.panel, 0.4f))
            isClickable = true
            addView(spinner(colors), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            visibility = View.GONE
        }
        val area = FrameLayout(this)
        area.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        area.addView(centerText, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER).apply { setMargins(dp(16), 0, dp(16), 0) })
        area.addView(waiting, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        area.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))
        card.addView(area, lp(MATCH, 0, 1f))
        root.addView(card, lp(MATCH, 0, 1f).apply { setMargins(dp(4), dp(6), dp(4), dp(6)) })
        setContentView(root)
        // A keypad starts in the field (else Android gives the title bar's back button the first focus).
        field.requestFocus()
    }

    /** The main app's bar: a pill with its border (the accent's when focused), the search icon, the chip, the field, ✕. */
    private fun searchBar(): View {
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPaddingRelative(dp(14), 0, dp(4), 0)
        }
        fun border(focused: Boolean) {
            pill.background = roundRect(colors.panel, dp(24).toFloat(), if (focused) colors.accent else colors.border, maxOf(1, dp(if (focused) 2 else 1)))
        }
        border(false)
        pill.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
        }, lp(dp(16), dp(16)).apply { marginEnd = dp(8) })
        // The book or author picked (SelectedChip): its name and ✕, which takes the search back to the whole library.
        chip = label("", 12f, colors.text, maxLines = 1, typeface = Fonts.ui(this)).apply {
            maxWidth = (resources.displayMetrics.widthPixels * 0.4f).toInt()
            setPaddingRelative(dp(10), dp(5), dp(6), dp(5))
            background = states(
                roundRect(colors.panel, dp(14).toFloat(), colors.border, maxOf(1, dp(1))),
                roundRect(colors.pressed, dp(14).toFloat(), colors.border, maxOf(1, dp(1))),
                roundRect(colors.focus, dp(14).toFloat(), colors.accent, maxOf(1, dp(1))),
            )
            @Suppress("DEPRECATION")
            val x = resources.getDrawable(R.drawable.ic_close).mutate()
            @Suppress("DEPRECATION")
            x.setColorFilter(colors.disabled, PorterDuff.Mode.SRC_IN)
            x.setBounds(0, 0, dp(12), dp(12))
            setCompoundDrawablesRelative(null, null, x, null)
            compoundDrawablePadding = dp(6)
            contentDescription = REMOVE_SELECTED_BOOK
            visibility = View.GONE
            keyable(this) { dropChip() }
        }
        pill.addView(chip, lp(WRAP, WRAP).apply { marginEnd = dp(8) })
        field = EditText(this).apply {
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Fonts.ui(this@SearchActivity)
            setTextColor(colors.text)
            setHintTextColor(PLACEHOLDER_GREY)
            hint = barHint
            background = null
            setPadding(0, dp(8), 0, dp(8))
            val initial = intent.getStringExtra(EXTRA_QUERY)?.trim().orEmpty()
            settingField = true
            setText(initial)
            setSelection(initial.length)
            settingField = false
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event == null || event.action == KeyEvent.ACTION_DOWN) enter()
                    true
                } else {
                    false
                }
            }
            setOnKeyListener { _, keyCode, event -> fieldKey(keyCode, event) }
            onFocusChangeListener = View.OnFocusChangeListener { _, focused ->
                border(focused)
                requestRender()
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                    if (!settingField) typed(s?.toString().orEmpty())
                }
            })
        }
        pill.addView(field, lp(0, WRAP, 1f))
        clearButton = iconButton(R.drawable.ic_close, colors.secondary, SEARCH_CLEAR, colors) {
            field.setText("")
            field.requestFocus()
        }.apply {
            // A 36dp circle with a 16dp ✕ (the main app's clear button), only with text
            val size = dp(36)
            layoutParams = LinearLayout.LayoutParams(size, size)
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            background = states(colors, 0, size / 2f)
            visibility = if (field.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        pill.addView(clearButton)
        return pill
    }

    /**
     * A keypad's OK (D-pad centre) runs the field as Enter does (TextView gives its editor-action listener the Enter key
     * only); in an empty field it stays the field's own (it opens the keyboard, where there is one). Backspace (a
     * keypad's C) in a field already empty when it went down drops the book or author picked, as the main app's: the
     * press that deletes the last letter does not.
     */
    private fun fieldKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && !field.text.isNullOrBlank()) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) enter()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            val m = models ?: return false
            val ui = m.bar.uiState.value
            val drop = (ui.selectedScopeBook != null && !m.tocEdited) || (ui.selectedScopeBook == null && ui.selectedScopeAuthor != null)
            val empty = field.text.isNullOrEmpty()
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    delStartedEmpty = empty
                    // Nothing to delete: the press is the chip's
                    return drop && empty
                }
                KeyEvent.ACTION_UP -> {
                    val clear = delStartedEmpty && empty && drop
                    delStartedEmpty = false
                    if (clear) dropChip()
                    return clear
                }
            }
        }
        return false
    }

    // ------------------------------------------------------------------ the view models

    /**
     * Opens the library and the search off the main thread, and makes the view models on them; searches at once for
     * [run] (a zayit://search link), or what [saved] says this screen showed before Android ended the app.
     */
    private fun open(run: Boolean, saved: Bundle?) {
        val restored = saved?.getString(STATE_QUERY)
        val restoredScope = saved?.takeIf { restored != null }?.let { savedScope(it) }
        // The book of the chip: the restored search's (a TOC entry's whole book, as the main app's restored tab), else
        // the reader's find's
        val scopeId = if (restored != null) {
            (restoredScope as? SearchScope.Book)?.bookId ?: (restoredScope as? SearchScope.Toc)?.bookId
        } else {
            intent.getLongExtra(EXTRA_BOOK_SCOPE, 0L).takeIf { it != 0L }
        }
        scope.launch {
            val made = withContext(Dispatchers.IO) {
                runCatching {
                    val lib = app.library.open()
                    val service = app.search.screenService() ?: throw NoLibraryException()
                    val book = scopeId?.let { id -> runCatching { lib.repository.getBookCore(id) }.getOrNull() }
                    Triple(lib, service, book)
                }
            }
            // No library yet (the Home's welcome offers the search too), or one that cannot be read: nothing to search.
            val (lib, service, book) = made.getOrElse { e ->
                toast(if (e is NoLibraryException) "עדיין אין ספרייה במכשיר" else "לא ניתן לפתוח את הספרייה")
                finish()
                return@launch
            }
            val query = restored ?: if (run) intent.getStringExtra(EXTRA_QUERY)?.trim().orEmpty() else ""
            val where = restoredScope ?: book?.let { SearchScope.Book(it.id) } ?: SearchScope.Global
            // Made without the query, which runTextSearch searches once the view model has checked its index: its init
            // would search a query set before that a second time, and on a phone of one core its init can reach the
            // search before the constructor has set the fields the search uses (a NullPointerException on the AVD's
            // release build) when the query is in the destination
            val destination = Destination.Search(query = "", scope = where)
            val m = Models(
                lib,
                service,
                app.search.searchViewModel(service, lib.repository, destination),
                app.search.searchBarViewModel(service, lib.repository),
                ScanSearch(app.library, prefs),
            )
            // The reader's find, or a restored search in a book: the bar in its book (showScope), the text typed
            // searched there; a restored search in an author's books: their chip
            if (book != null) m.bar.onPickBook(book, showSuggestions = false)
            if (restoredScope is SearchScope.Author) m.bar.showScope(restoredScope)
            val typed = field.text.toString()
            if (query.isNotEmpty()) {
                if (book != null) m.toc = query else m.reference = query
            } else if (typed.isNotBlank()) {
                // What was typed while the search opened
                if (book != null) m.toc = typed else m.reference = typed
                m.suggesting = true
                if (book != null) m.bar.onTocQueryChanged(typed) else m.bar.onReferenceQueryChanged(typed)
            }
            attach(m)
            if (query.isNotEmpty()) runTextSearch(query, where, extended = restored != null && saved.getBoolean(STATE_EXTENDED))
        }
    }

    /** Where the search kept in [saved] (onSaveInstanceState) looked. */
    private fun savedScope(saved: Bundle): SearchScope {
        val book = saved.getLong(STATE_BOOK, 0L)
        val toc = saved.getLong(STATE_TOC, 0L)
        val author = saved.getLong(STATE_AUTHOR, 0L)
        return when {
            book != 0L && toc != 0L -> SearchScope.Toc(book, toc)
            book != 0L -> SearchScope.Book(book)
            author != 0L -> SearchScope.Author(author)
            else -> SearchScope.Global
        }
    }

    /** Shows [m] (made now, or kept across a rotation) and follows its flows on the screen's scope. */
    private fun attach(m: Models) {
        models = m
        val ui = m.bar.uiState.value
        showField(if (ui.selectedScopeBook != null) m.toc else m.reference)
        if (m.searched) setTitle(m.lastQuery)
        pendingTop = m.listTop
        scope.launch { m.bar.uiState.collect { requestRender() } }
        scope.launch { m.search.state.collect { onSearchState(m, it) } }
        scope.launch { m.search.breadcrumbs.collect { onBreadcrumbs(it) } }
        scope.launch { m.search.entity.collect { requestRender() } }
        scope.launch { m.scan.state.collect { requestRender() } }
        scope.launch { m.scan.progress.collect { requestRender() } }
        scope.launch { m.scanEntity.collect { requestRender() } }
        scope.launch { m.changes.collect { requestRender() } }
        scope.launch { app.search.level.collect { requestRender() } }
        requestRender()
    }

    /**
     * The main app's results: drawn, and searched again by the scan when the full-text index went between the choice
     * and the search (a failure elsewhere, the folder renamed): its "index missing" would show where the scan can run.
     */
    private fun onSearchState(m: Models, s: SearchUiState) {
        if (m.searched && !m.scanMode && s.availability == SearchAvailability.IndexMissing && !s.isLoading &&
            app.search.level.value != LegacySearch.Level.Full && m.lastQuery.isNotEmpty()
        ) {
            runTextSearch(m.lastQuery, m.lastScope, m.lastExtended)
            return
        }
        requestRender()
    }

    private fun state(m: Models): SearchUiState = if (m.scanMode) m.scan.state.value else m.search.state.value

    // ------------------------------------------------------------------ the field and its suggestions

    /** The user typed [text]: the bar's view model suggests for it, in the library or in the book picked. */
    private fun typed(text: String) {
        val m = models ?: return
        m.suggesting = true
        if (m.bar.uiState.value.selectedScopeBook != null) {
            m.toc = text
            m.tocEdited = text.isNotBlank()
            m.bar.onTocQueryChanged(text)
        } else {
            m.reference = text
            m.bar.onReferenceQueryChanged(text)
        }
        requestRender()
    }

    /** Puts [text] in the field as the screen's, not typed. */
    private fun showField(text: String) {
        if (field.text.toString() == text) return
        settingField = true
        field.setText(text)
        field.setSelection(text.length)
        settingField = false
    }

    /** Enter (the keyboard's search, OK in the field): the highlighted row, the first; else the search. */
    private fun enter() {
        val m = models ?: return
        val first = if (m.suggesting) adapter.items.firstOrNull() as? Item.Suggestion else null
        val ui = m.bar.uiState.value
        when {
            first != null -> first.main.run()
            ui.selectedScopeBook != null && ui.selectedScopeToc != null -> openPicked(m)
            ui.selectedScopeToc == null -> textSearch(m, extended = false)
        }
    }

    /** "חיפוש בתוך" a book: its chip in the field, and the field for its TOC and the search in it. */
    private fun pickBook(m: Models, book: Book) {
        m.bar.onPickBook(book)
        m.reference = ""
        m.toc = ""
        m.tocEdited = false
        m.bar.onTocQueryChanged("")
        showField("")
        m.suggesting = true
        field.requestFocus()
        requestRender()
    }

    /** "חיפוש בתוך" an author: their chip, and their books listed to pick from. */
    private fun pickAuthor(m: Models, author: io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit) {
        m.bar.onPickAuthor(author)
        m.reference = ""
        showField("")
        m.suggesting = true
        field.requestFocus()
        requestRender()
    }

    /** A TOC entry picked: its path in the field, the search scoped to it. */
    private fun pickToc(m: Models, entry: Pair<TocSuggestionDto, List<String>>) {
        m.bar.onPickToc(entry.first.toc)
        val display = entry.second.joinToString(BREADCRUMB_SEPARATOR)
        m.toc = display
        m.tocEdited = true
        showField(display)
        requestRender()
    }

    /** The chip's ✕ (or Backspace in an empty field): the whole library again. */
    private fun dropChip() {
        val m = models ?: return
        val ui = m.bar.uiState.value
        if (ui.selectedScopeBook != null) {
            m.bar.onReferenceQueryChanged("")
            m.bar.onTocQueryChanged("")
            m.reference = ""
            m.toc = ""
            m.tocEdited = false
        } else {
            m.bar.onClearAuthor()
        }
        showField(m.reference)
        // To the field before the chip goes: a keypad's focus would go to the title bar
        field.requestFocus()
        requestRender()
    }

    /** Back over results: the suggestions close and the field shows the query of the results again. */
    private fun closeSuggestions(m: Models) {
        m.suggesting = false
        m.bar.dismissSuggestions()
        val executed = state(m).executedQuery.ifEmpty { m.lastQuery }
        if (m.bar.uiState.value.selectedScopeBook != null) m.toc = executed else m.reference = executed
        showField(executed)
        requestRender()
    }

    /** The suggestions as rows (desktop's book stage, or a book's TOC), as UnifiedSearchBar lists them. */
    private fun suggestionItems(m: Models, ui: SearchBarUiState): List<Item> {
        val text = field.text.toString()
        if (ui === suggestedFor && text == suggestedText) return suggested
        suggestedFor = ui
        suggestedText = text
        val book = ui.selectedScopeBook
        val query = text.trim()
        val hasText = text.isNotBlank()
        val textSearchEnabled = ui.selectedScopeToc == null
        val items = ArrayList<Item>()
        if (book != null) {
            val toc = tocSuggestionsForDisplay(book, ui.tocSuggestions)
            val textRow = textSearchEnabled && hasText
            val total = toc.size + if (textRow) 1 else 0
            val loading = ui.isTocLoading && hasText && text.isNotEmpty()
            when {
                loading && total == 0 -> items += Item.Message(AUTOCOMPLETE_LOADING, small = true)
                (ui.tocSuggestionsVisible || textRow) && total > 0 -> {
                    for (entry in toc) {
                        val open = RowAction(ROW_ACTION_OPEN) { openToc(m, entry) }
                        items += Item.Suggestion(SuggestionKind.PLACE, entry.second, null, null, listOf(open), emptyList(), open)
                    }
                    if (textRow) {
                        val search = RowAction(ROW_ACTION_SEARCH) { textSearch(m, extended = false) }
                        val label = SEARCH_TEXT_ROW_IN_BOOK.replace("%2\$s", book.title).replace("%1\$s", query)
                        items += Item.Suggestion(SuggestionKind.TEXT_SEARCH, listOf(label), query, null, listOf(search), listOf(search), search)
                    }
                }
                ui.tocSuggestionsVisible && toc.isEmpty() && hasText && !loading -> items += Item.Message(AUTOCOMPLETE_NO_RESULTS, small = true)
            }
        } else {
            val rows = catBookRows(query, ui.jumpSuggestions, textSearchEnabled && query.isNotEmpty(), ui.authorSuggestions, ui.bookSuggestions)
            val loading = ui.isReferenceLoading && hasText && text.length >= 2
            val authorName = ui.selectedScopeAuthor?.name?.let(AuthorNames::display)
            when {
                loading && rows.isEmpty() -> items += Item.Message(AUTOCOMPLETE_LOADING, small = true)
                ui.suggestionsVisible && rows.isNotEmpty() -> rows.mapTo(items) { row -> suggestion(m, row, query, authorName) }
                ui.suggestionsVisible && hasText && text.length >= 2 && !loading -> items += Item.Message(AUTOCOMPLETE_NO_RESULTS, small = true)
            }
        }
        suggested = items
        return items
    }

    private var suggestedFor: SearchBarUiState? = null
    private var suggestedText = ""
    private var suggested: List<Item> = emptyList()

    /** One row of the book stage, with what it does (runCatBookRow). */
    private fun suggestion(m: Models, row: CatBookRow, query: String, authorName: String?): Item.Suggestion = when (row) {
        is CatBookRow.Jump -> {
            val open = RowAction(ROW_ACTION_OPEN) { openBook(m, row.reference.book, row.reference.lineId) }
            Item.Suggestion(SuggestionKind.PLACE, listOf(row.reference.book.title, row.reference.label), null, null, listOf(open), emptyList(), open)
        }
        CatBookRow.TextSearch -> {
            // An author's books are all searched: no base books to choose
            val actions = if (authorName != null) {
                listOf(RowAction(ROW_ACTION_SEARCH) { textSearch(m, extended = false) })
            } else {
                listOf(RowAction(ROW_ACTION_SEARCH_BASE) { textSearch(m, extended = false) }, RowAction(ROW_ACTION_SEARCH_ALL) { textSearch(m, extended = true) })
            }
            val label = if (authorName != null) SEARCH_TEXT_ROW_IN_AUTHOR.replace("%2\$s", authorName).replace("%1\$s", query) else SEARCH_TEXT_ROW.replace("%1\$s", query)
            Item.Suggestion(SuggestionKind.TEXT_SEARCH, listOf(label), query, null, actions, actions, actions.first())
        }
        is CatBookRow.Author -> {
            val open = RowAction(ROW_ACTION_OPEN) { showAuthor(m, row.hit.id) }
            val inside = RowAction(ROW_ACTION_SEARCH_INSIDE) { pickAuthor(m, row.hit) }
            val name = AuthorNames.display(row.hit.name) + row.hit.alias?.let { " ($it)" }.orEmpty()
            Item.Suggestion(SuggestionKind.AUTHOR, listOf(name), null, AUTHOR_BOOKS_COUNT.replace("%1\$d", row.hit.bookCount.toString()), listOf(open, inside), listOf(inside), open)
        }
        is CatBookRow.BookRow -> {
            val book = row.suggestion.book
            val open = RowAction(ROW_ACTION_OPEN) { openBook(m, book, null) }
            val inside = RowAction(ROW_ACTION_SEARCH_INSIDE) { pickBook(m, book) }
            Item.Suggestion(SuggestionKind.BOOK, dedupAdjacent(row.suggestion.path), null, null, listOf(open, inside), listOf(inside), open)
        }
    }

    // ------------------------------------------------------------------ searching

    /** The bar's text search ("חיפוש במאגר", "חיפוש בתוך …"): where the bar says, in all the books for [extended]. */
    private fun textSearch(m: Models, extended: Boolean) {
        val q = field.text.toString().trim()
        if (q.isEmpty()) return
        val barScope = m.bar.barScope()
        m.bar.onGlobalExtendedChange(extended)
        m.bar.dismissSuggestions()
        // At once for a text, which deepLinkTarget does not wait for: no turn of the main thread (a rotation) between
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // A zayit:// link pasted into the bar opens what it names (desktop submitSearch)
            when (val link = m.bar.deepLinkTarget(q)) {
                is Destination.Book -> openBookAt(m, link.bookId, link.lineId, link.title)
                is Destination.Search -> {
                    showField(link.query)
                    runTextSearch(link.query, SearchScope.Global, extended)
                }
                else -> runTextSearch(q, barScope, extended)
            }
        }
    }

    /**
     * Searches [q] in [barScope]: by the main app's engine at LegacySearch.Level.Full, else by the scan. The level is
     * asked again first (off the main thread), so that an index copied in or renamed away meanwhile counts. It starts
     * in the models' scope: a screen built again after a rotation meanwhile shows it.
     */
    private fun runTextSearch(q: String, barScope: SearchScope, extended: Boolean) {
        val m = models ?: return
        m.suggesting = false
        m.searched = true
        m.lastQuery = q
        m.lastScope = barScope
        m.lastExtended = extended
        m.allTabs = false
        if (m.bar.uiState.value.selectedScopeBook != null) m.toc = q else m.reference = q
        snippets.evictAll()
        places.clear()
        setTitle(q)
        hideKeyboard()
        // A keypad on the suggestion that started it: to the field, as the list empties (else to the title bar)
        if (list.hasFocus()) field.requestFocus()
        val legacy = app.search
        m.searchJob?.cancel()
        m.searchJob = m.scope.launch {
            withContext(Dispatchers.IO) { runCatching { legacy.screenService() } }
            if (legacy.level.value == LegacySearch.Level.Full) {
                m.scan.cancel()
                m.scanMode = false
                m.dropScanEntity()
                m.changed()
                // Once the view model has checked its index: its init searches a query it finds set then, a second time
                m.search.state.first { it.availability != SearchAvailability.Checking }
                m.search.searchFromBar(q, barScope, widen = extended)
            } else {
                m.search.cancel()
                m.startScan(legacy, q, barScope, extended)
            }
        }
        requestRender()
    }

    /** "בטל חיפוש": the search stops, the results found stay. */
    private fun cancelSearch() {
        val m = models ?: return
        if (m.scanMode) m.scan.cancel() else m.search.cancel()
    }

    private fun backFromBook(m: Models) {
        if (m.scanMode) m.scan.backFromBook() else m.search.backFromBook()
    }

    /** "נסה שוב": the failures forgotten, the search opened again at its best level, and the last search run again. */
    private fun retry() {
        val m = models ?: return
        app.search.retry()
        if (m.lastQuery.isNotEmpty()) runTextSearch(m.lastQuery, m.lastScope, m.lastExtended)
    }

    private fun setTitle(query: String) {
        bar.title.text = if (query.isBlank()) "חיפוש" else "תוצאות חיפוש : $query"
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(field.windowToken, 0)
    }

    // ------------------------------------------------------------------ opening

    /** A book from a suggestion: where it was left ([lineId] null; this edition's books open at the last place read), or at a line. */
    private fun openBook(m: Models, book: Book, lineId: Long?) {
        m.bar.onOpened()
        openBookAt(m, book.id, lineId, book.title)
    }

    private fun openBookAt(m: Models, bookId: Long, lineId: Long?, title: String) {
        if (lineId == null) {
            m.scan.pause()
            BookActivity.start(this, bookId, prefs.lastLine(bookId), title)
            return
        }
        scope.launch {
            val index = withContext(Dispatchers.IO) { runCatching { m.opened.lineIndex(lineId) }.getOrNull() }
            if (index != null && fromBook(bookId)) {
                setResult(RESULT_OK, Intent().putExtra(BookActivity.EXTRA_LINE, index))
                finish()
                return@launch
            }
            m.scan.pause()
            BookActivity.start(this@SearchActivity, bookId, index, title.ifEmpty { null })
        }
    }

    /** A TOC entry: picked, then its book opened there (openToc). */
    private fun openToc(m: Models, entry: Pair<TocSuggestionDto, List<String>>) {
        pickToc(m, entry)
        openPicked(m)
    }

    /** The picked TOC entry's (or book's) place (resolvePickedReference). */
    private fun openPicked(m: Models) {
        scope.launch {
            val (book, line) = m.bar.resolvePickedReference() ?: return@launch
            openBook(m, book, line)
        }
    }

    /**
     * A result: its book at its line, with the search's words found there; in the book the search was opened from
     * (the reader's find), back to it. The main app's search matches the words near each other, the scan the phrase
     * unless asked for each word.
     */
    private fun openResult(m: Models, hit: SearchResult) {
        val find = m.lastQuery.takeIf { it.length >= 2 }
        val words = !m.scanMode || m.scan.progress.value.allWords
        if (fromBook(hit.bookId)) {
            setResult(
                RESULT_OK,
                Intent().putExtra(BookActivity.EXTRA_LINE, hit.lineIndex).putExtra(BookActivity.EXTRA_FIND, find).putExtra(BookActivity.EXTRA_FIND_WORDS, words),
            )
            finish()
            return
        }
        m.scan.pause()
        BookActivity.start(this, hit.bookId, hit.lineIndex, hit.bookTitle, find, words)
    }

    /** Opened by [bookId]'s reader for its find: a place in it goes back there. */
    private fun fromBook(bookId: Long): Boolean = callingActivity != null && intent.getLongExtra(EXTRA_BOOK_SCOPE, 0L) == bookId

    /**
     * An author (their suggestion, the knowledge panel's "לדף המחבר"): their page of the main app (AuthorScreen) in a
     * dialog: era, years and other names, the biography's summary with its notes, and their books, each opened by OK.
     */
    private fun showAuthor(m: Models, authorId: Long) {
        // A dialog over the suggestions, which are there again when it closes
        scope.launch {
            val details = withContext(Dispatchers.IO) { runCatching { m.opened.repository.getAuthorDetails(authorId) }.getOrNull() }
            if (details == null) {
                toast("המחבר לא נמצא")
                return@launch
            }
            lateinit var dialog: android.app.Dialog
            val box = authorPage(details) { book ->
                dialog.dismiss()
                openBook(m, book, null)
            }
            dialog = Dialogs.show(this@SearchActivity, colors, AuthorNames.display(details.name), box)
        }
    }

    private fun authorPage(details: AuthorDetails, onBook: (Book) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), dp(8))
        }
        val grey = colors.secondary
        fun info(title: String, value: String) {
            box.addView(label(SpannableStringBuilder(title).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(": ").append(value)
            }, 14f, colors.text).apply { setPadding(0, dp(2), 0, dp(2)) })
        }
        details.era?.let { AUTHOR_ERAS[it] }?.let { info("תקופה", it) }
        authorYears(details)?.let { info("שנים", it) }
        if (details.aliases.isNotEmpty()) info("כינויים", details.aliases.joinToString(" · "))
        details.bio?.summary?.let { summary ->
            box.addView(label(plainPersonLinks(summary).replace("**", ""), 14f, colors.text).apply {
                setLineSpacing(0f, 1.3f)
                setPadding(0, dp(8), 0, dp(4))
            })
            box.addView(label(AUTHOR_BIO_NOTE, 12f, grey).apply { setPadding(0, dp(4), 0, 0) })
            box.addView(label(AUTHOR_BIO_LICENSE, 12f, grey).apply { setPadding(0, dp(2), 0, dp(4)) })
        }
        if (details.books.isNotEmpty()) {
            box.addView(divider(colors).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(0, dp(10), 0, dp(6)) })
            box.addView(label("ספרים במאגר", 13f, grey, bold = true))
            details.books.sortedWith(compareByDescending<Book> { it.isBaseBook }.thenBy { it.order }).forEach { book ->
                box.addView(TreeRow(this, colors).bind(book.title, iconRes = R.drawable.ic_book).apply { keyable(this) { onBook(book) } })
            }
        }
        return box
    }

    // ------------------------------------------------------------------ the list

    private fun activate(position: Int) {
        val m = models ?: return
        when (val item = adapter.items.getOrNull(position) ?: return) {
            is Item.Suggestion -> item.main.run()
            is Item.Result -> openResult(m, item.hit)
            is Item.More -> if (m.scanMode) m.scan.showMoreFromBook(item.hit.bookId) else m.search.showMoreFromBook(item.hit.bookId)
            is Item.Notice -> if (!item.full) showNotice(item.notice)
            else -> Unit
        }
    }

    /** A long OK (a long press): a suggestion's actions to choose from, as its buttons offer them. */
    private fun moreActions(position: Int): Boolean {
        val item = adapter.items.getOrNull(position) as? Item.Suggestion ?: return false
        val all = (item.actions + item.always).distinct()
        if (all.size < 2) return false
        Dialogs.choices(this, colors, item.parts.last(), all.map { action -> MenuEntry(action.label, null) { action.run() } })
        return true
    }

    private var renderPosted = false
    private val renderTask = Runnable {
        renderPosted = false
        render()
    }

    /** Draws the screen once at the next turn of the main thread, however many of its flows changed meanwhile. */
    private fun requestRender() {
        if (renderPosted || !::list.isInitialized) return
        renderPosted = true
        list.post(renderTask)
    }

    /** What the list holds now, and what it was built from (built again only when that changes). */
    private var shownKey: Any? = null

    /** The scroll a search or the way back from a book asked for, done once its rows are there. */
    private var scrolledStamp = 0L

    /** Where a screen built again after a rotation puts its list, once its rows are there. */
    private var pendingTop: Pair<Int, Int>? = null

    private fun render() {
        val m = models
        if (m == null) {
            area(list = false, center = null, waitingShown = true)
            header(m = null, s = null)
            return
        }
        renderBar(m)
        val ui = m.bar.uiState.value
        if (m.suggesting) {
            val items = suggestionItems(m, ui)
            if (items.isNotEmpty()) {
                header(m = null, s = null)
                val highlight = !list.hasFocus() || list.isInTouchMode
                setItems(items, Triple(items, highlight, narrow))
                area(list = true, center = null, waitingShown = false)
                return
            }
        }
        if (!m.searched) {
            header(m = null, s = null)
            setItems(emptyList(), null)
            area(list = true, center = null, waitingShown = false)
            return
        }
        val s = state(m)
        header(m, s)
        val inBook = s.bookFilterIds.isNotEmpty()
        val oneBook = inBook || s.scopeBook != null || s.scopeTocId != null
        val entity = if (m.scanMode) m.scanEntity.value else m.search.entity.value
        val notice: LegacySearch.TextUnavailable? = when {
            m.scanMode && s.failure != null -> LegacySearch.TextUnavailable.Failed(s.failure.orEmpty())
            m.scanMode -> m.reason
            s.availability == SearchAvailability.IndexMissing -> LegacySearch.TextUnavailable.Missing
            s.failure != null && s.results.isEmpty() -> LegacySearch.TextUnavailable.Failed(s.failure.orEmpty())
            else -> null
        }
        if (!m.scanMode && s.availability == SearchAvailability.Checking) {
            area(list = false, center = null, waitingShown = true)
            return
        }
        val key = listOf(s.results, s.bookCounts, oneBook, entity, notice, m.scanMode, s.isLoadingMore, s.isLoading && s.results.isEmpty())
        if (key != shownKey) {
            val items = ArrayList<Item>()
            // In full in the results' place, as the main app shows it; over the scan's results, a line that opens it
            notice?.let { items += Item.Notice(it, full = s.results.isEmpty()) }
            // A search that failed, or an index missing, is shown alone, as the main app's (no knowledge panel under it);
            // the scan's reason is no failure, and its panel stays
            val alone = notice != null && s.results.isEmpty() && (!m.scanMode || notice is LegacySearch.TextUnavailable.Failed)
            if (!alone) entity?.let { items += Item.Entity(it) }
            for (r in flattenResults(s.results, s.bookCounts, oneBook)) {
                items += Item.Result(r.hit, last = r.more == 0)
                if (r.more > 0) items += Item.More(r.hit, r.more)
            }
            val failedHere = notice is LegacySearch.TextUnavailable.Failed && !m.scanMode
            if (s.results.isEmpty() && (items.isNotEmpty()) && !failedHere && s.availability != SearchAvailability.IndexMissing) {
                items += Item.Message(if (s.isLoading) SEARCH_SEARCHING else SEARCH_NO_RESULTS, small = false)
            }
            if (s.isLoadingMore) items += Item.LoadingMore
            setItems(items, key)
        }
        val empty = adapter.items.isEmpty()
        area(list = !empty, center = if (empty) (if (s.isLoading) SEARCH_SEARCHING else SEARCH_NO_RESULTS) else null, waitingShown = false, filtering = s.isFiltering)
        scrollTo(m, s)
    }

    private fun setItems(items: List<Item>, key: Any?) {
        if (key != null && key == shownKey && adapter.items === items) return
        shownKey = key
        // A keypad in a list that empties goes to the field: Android 4.4 to 7.1 would give it to the title bar's Back,
        // where the next OK leaves the search
        if (items.isEmpty() && list.hasFocus()) field.requestFocus()
        adapter.items = items
    }

    /**
     * Which of the list, a centred message and the spinner shows in the list's place, and the loader over the results
     * while a tab or one book's results are searched ([filtering]): only over results, as the main app's is (its
     * suggestions are a popup over it), where it would take the taps of the suggestions.
     */
    private fun area(list: Boolean, center: String?, waitingShown: Boolean, filtering: Boolean = false) {
        if (!list && this.list.hasFocus()) field.requestFocus()
        this.list.visibility = if (list) View.VISIBLE else View.INVISIBLE
        centerText.text = center
        centerText.visibility = if (center != null) View.VISIBLE else View.GONE
        waiting.visibility = if (waitingShown) View.VISIBLE else View.GONE
        overlay.visibility = if (filtering && list && !waitingShown) View.VISIBLE else View.GONE
    }

    /** The chip, the hint and ✕ of the bar, as its state says. */
    private fun renderBar(m: Models) {
        val ui = m.bar.uiState.value
        val book = ui.selectedScopeBook
        val name = book?.title ?: ui.selectedScopeAuthor?.name?.let(AuthorNames::display)
        if (!TextUtils.equals(chip.text, name)) chip.text = name
        if (name == null) hide(chip) else chip.visibility = View.VISIBLE
        val hint = if (book != null) SEARCH_IN_BOOK_PLACEHOLDER else barHint
        if (!TextUtils.equals(field.hint, hint)) field.hint = hint
    }

    /** This screen's hint, the next of the bar's: one a screen, so that they come in turn (a place, then a text). */
    private val barHint = nextHint()

    /** The tabs, the way back from one book and the status line, over results ([s]); hidden over suggestions. */
    private fun header(m: Models?, s: SearchUiState?) {
        if (m == null || s == null) {
            hide(tabsBox)
            hide(backLink)
            hide(statusRow)
            return
        }
        renderTabs(m, s)
        if (s.bookFilterIds.isNotEmpty()) {
            val text = SEARCH_BACK_TO_ALL.fill(s.results.firstOrNull()?.bookTitle.orEmpty())
            if (!TextUtils.equals(backLink.text, text)) backLink.text = text
            backLink.visibility = View.VISIBLE
            keyable(backLink) { backFromBook(m) }
        } else {
            hide(backLink)
        }
        val ready = m.scanMode || s.availability == SearchAvailability.Ready
        if (!ready) {
            hide(statusRow)
            return
        }
        statusRow.visibility = View.VISIBLE
        if (m.scanMode) scanStatus(m, s) else searchStatus(m, s)
    }

    /**
     * The main app's status line (StatusRow, SearchStatusLine): where the search looked and about how many it found,
     * with the other choice as a link (all the books, or the base books only); while it runs, "מחפש...", the progress
     * and ✕.
     */
    private fun searchStatus(m: Models, s: SearchUiState) {
        val oneBook = s.bookFilterIds.isNotEmpty() || s.scopeBook != null || s.scopeTocId != null
        val loaded = maxOf(s.progressCurrent, s.results.size)
        val total = maxOf(loaded.toLong(), s.progressTotal ?: loaded.toLong())
        val count = formatCount(total)
        val scopeName = if (oneBook) s.scopeBook?.title ?: s.results.firstOrNull()?.bookTitle else s.scopeAuthorName?.let { "ספרי $it" }
        val text = when {
            s.isLoading -> SEARCH_SEARCHING
            scopeName != null -> SEARCH_STATUS_IN.fill(count, scopeName)
            s.baseBooksHadNoResults -> SEARCH_STATUS_FALLBACK.fill(count)
            s.globalExtended -> SEARCH_STATUS_ALL.fill(count)
            else -> SEARCH_STATUS_BASE.fill(count)
        }
        val link = if (!s.isLoading && !s.baseBooksHadNoResults && scopeName == null) {
            (if (s.globalExtended) SEARCH_SWITCH_BASE else SEARCH_SWITCH_ALL) to { switchExtended(m, !s.globalExtended) }
        } else {
            null
        }
        statusLine(text, link, s.isLoading, s.progressTotal?.takeIf { it > 0 }?.let { s.progressCurrent.toFloat() / it })
    }

    /**
     * The scan's status line: while it reads, the book it is in, the how-manieth of how many and what it has found;
     * then how many and where, and the other choice as a link (all the books, the base books only, or each word apart
     * when the phrase was not found).
     */
    private fun scanStatus(m: Models, s: SearchUiState) {
        val p = m.scan.progress.value
        if (p.running) {
            val text = if (p.book == null) SEARCH_SEARCHING else "מחפש ב${p.book} (${p.at + 1} מתוך ${p.of}) · ${formatCount(p.found.toLong())} תוצאות"
            statusLine(text, null, true, if (p.of > 0) p.at.toFloat() / p.of else null)
            return
        }
        val n = s.results.size
        val first = if (p.capped) "${formatCount(n.toLong())} התוצאות הראשונות" else "${formatCount(n.toLong())} תוצאות"
        val narrowed = s.bookFilterIds.isNotEmpty()
        val scopeName = s.results.firstOrNull()?.bookTitle?.takeIf { narrowed } ?: m.scan.scopeTitle
        val text = when {
            p.found == 0 -> SEARCH_NO_RESULTS
            scopeName != null -> "$first ב$scopeName"
            s.baseBooksHadNoResults -> "לא נמצא בספרי יסוד · מוצגות $first מכל הספרים"
            s.globalExtended -> "$first בכל הספרים"
            else -> "$first בספרי יסוד"
        } + if (p.stopped) " (החיפוש הופסק)" else ""
        val link: Pair<String, () -> Unit>? = when {
            p.found == 0 && !p.allWords && m.lastQuery.trim().contains(' ') -> "חיפוש כל מילה בנפרד" to { m.scan.searchWords() }
            scopeName != null || s.baseBooksHadNoResults -> null
            s.globalExtended -> SEARCH_SWITCH_BASE to { switchExtended(m, false) }
            else -> SEARCH_SWITCH_ALL to { switchExtended(m, true) }
        }
        statusLine(text, link, false, null)
    }

    /** The status line's link: the same search in all the books, or in the base books only, kept as the last search's. */
    private fun switchExtended(m: Models, extended: Boolean) {
        m.lastExtended = extended
        if (m.scanMode) m.scan.setGlobalExtended(extended) else m.search.setGlobalExtended(extended)
    }

    /** Sets the status line: [text], "·" and the [link], the progress and ✕ while [running]. */
    private fun statusLine(text: String, link: Pair<String, () -> Unit>?, running: Boolean, fraction: Float?) {
        if (!TextUtils.equals(status.text, text)) status.text = text
        if (link == null) {
            statusDot.visibility = View.GONE
            hide(statusLink)
        } else {
            statusDot.visibility = View.VISIBLE
            if (!TextUtils.equals(statusLink.text, link.first)) statusLink.text = link.first
            statusLink.visibility = View.VISIBLE
            keyable(statusLink) { link.second() }
        }
        // While it runs the status takes its width and the progress the rest; else the status the whole row
        val texts = statusTexts.layoutParams as LinearLayout.LayoutParams
        val width = if (running) WRAP else 0
        val weight = if (running) 0f else 1f
        if (texts.width != width || texts.weight != weight) {
            texts.width = width
            texts.weight = weight
            statusTexts.layoutParams = texts
        }
        progress.visibility = if (running) View.VISIBLE else View.GONE
        progress.fraction = fraction
        when {
            running -> stop.visibility = View.VISIBLE
            // The ✕'s room kept, so that the line does not jump; not on a narrow phone, where the text wants it
            compact -> hide(stop)
            else -> {
                focusOff(stop)
                stop.visibility = View.INVISIBLE
            }
        }
    }

    // The tabs drawn and what they were drawn from
    private var drawnTree: List<SearchTreeCategory>? = null
    private var drawnSelected: Long? = null
    private var drawnAll = false

    /**
     * The category tabs (CategoryTabs): "הכל", the five categories with the most results, the rest behind "עוד"; on a
     * narrow phone all of them on one scrolling line. One at a time: the search runs again in it.
     */
    private fun renderTabs(m: Models, s: SearchUiState) {
        val tree = s.tree
        // "עוד" holds for the tabs it was chosen in, as the main app's (remember(categories)): a search that builds
        // them again (the status line's link, a new search) starts with the top five
        if (m.allTabs && tree !== m.allTabsOf && tree != m.allTabsOf) m.allTabs = false
        if (tree.isEmpty()) {
            hide(tabsBox)
            drawnTree = null
            return
        }
        tabsBox.visibility = View.VISIBLE
        val sorted = tree.sortedByDescending { it.count }
        val selected = sorted.firstOrNull { it.category.id in s.selectedCategoryIds }?.category?.id
        if (tree === drawnTree && selected == drawnSelected && m.allTabs == drawnAll) return
        // The tab a keypad is on gets the focus back: removing it sends the focus to the title bar
        val focused = tabsBox.findFocus()?.tag
        drawnTree = tree
        drawnSelected = selected
        drawnAll = m.allTabs
        tabsBox.removeAllViews()
        val show: (Long?) -> Unit = { id -> if (m.scanMode) m.scan.showOnlyCategory(id) else m.search.showOnlyCategory(id) }
        fun tab(text: String, id: Long?, chosen: Boolean, tag: Any, onClick: () -> Unit) = categoryTab(text, colors, chosen, onClick).apply { this.tag = tag }
        val tabs = ArrayList<TextView>()
        tabs += tab(SEARCH_TAB_ALL, null, selected == null, ALL_TAG) { show(null) }
        if (compact) {
            sorted.forEach { node -> tabs += tab(node.category.title, node.category.id, selected == node.category.id, node.category.id) { show(node.category.id) } }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            tabs.forEach { row.addView(it, lp(WRAP, WRAP).apply { marginEnd = dp(8) }) }
            val scroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
            tabsBox.addView(scroll, FrameLayout.LayoutParams(MATCH, WRAP))
            reveal(scroll, row, tabs.firstOrNull { it.tag == (selected ?: ALL_TAG) })
        } else {
            val shown = if (m.allTabs) sorted else sorted.take(TOP_TABS) + sorted.drop(TOP_TABS).filter { it.category.id == selected }
            shown.forEach { node -> tabs += tab(node.category.title, node.category.id, selected == node.category.id, node.category.id) { show(node.category.id) } }
            if (sorted.size > TOP_TABS) {
                tabs += tab(if (m.allTabs) SEARCH_TABS_LESS else SEARCH_TABS_MORE, null, false, MORE_TAG) {
                    m.allTabs = !m.allTabs
                    m.allTabsOf = tree
                    requestRender()
                }
            }
            val flow = FlowRow(this, dp(8), dp(6), centered = false)
            tabs.forEach { flow.addView(it) }
            tabsBox.addView(flow, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        if (focused != null) tabs.firstOrNull { it.tag == focused }?.requestFocus()
    }

    /**
     * Scrolls a row of tabs to the chosen one. Android 4.4's HorizontalScrollView starts at the left whatever the
     * direction, which in a right-to-left row is its end.
     */
    private fun reveal(scroll: HorizontalScrollView, row: LinearLayout, chosen: View?) {
        row.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                row.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val target = chosen ?: return
                val max = maxOf(0, row.width - scroll.width)
                scroll.scrollTo(((target.left + target.right) / 2 - scroll.width / 2).coerceIn(0, max), 0)
            }
        })
    }

    /**
     * Hides [view]; a keypad's focus in it goes to the results, or the field. Android 4.4 to 7.1 would give it to the
     * title bar's Back, where the next OK leaves the search.
     */
    private fun hide(view: View) {
        if (view.visibility == View.GONE) return
        focusOff(view)
        view.visibility = View.GONE
    }

    /** A keypad's focus in [view], which goes: to the results when they show, else to the field. */
    private fun focusOff(view: View) {
        if (view.hasFocus() && !(adapter.count > 0 && list.visibility == View.VISIBLE && list.requestFocus())) field.requestFocus()
    }

    /** A new search, a tab, one book's results or the way back: the list goes where its state asks, once. */
    private fun scrollTo(m: Models, s: SearchUiState) {
        val items = adapter.items
        if (items.isEmpty()) return
        pendingTop?.let { (index, top) ->
            pendingTop = null
            scrolledStamp = s.scrollTarget.stamp
            if (index > 0 || top != 0) list.setSelectionFromTop(index.coerceAtMost(items.size - 1), top)
            return
        }
        val target = s.scrollTarget
        if (target.stamp == scrolledStamp) return
        scrolledStamp = target.stamp
        val anchored = if (target.anchorId > 0) items.indexOfFirst { it is Item.Result && it.hit.lineId == target.anchorId }.takeIf { it >= 0 } else null
        list.setSelectionFromTop((anchored ?: target.index).coerceIn(0, items.size - 1), target.offset)
    }

    /** As the list scrolls: where it stands (kept for the way back and a rotation), and the next page near its end. */
    private val scrolled = object : AbsListView.OnScrollListener {
        override fun onScrollStateChanged(view: AbsListView, scrollState: Int) = Unit

        override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
            val m = models ?: return
            if (m.suggesting || !m.searched || totalItemCount == 0) return
            val s = state(m)
            if (s.isLoading) return
            val top = list.getChildAt(0)?.top ?: 0
            m.listTop = firstVisibleItem to top
            val anchor = (adapter.items.getOrNull(firstVisibleItem) as? Item.Result)?.hit?.lineId ?: -1L
            if (m.scanMode) m.scan.onScroll(anchor, firstVisibleItem, top) else m.search.onScroll(anchor, firstVisibleItem, top)
            // Infinite scroll: the next page within 10 rows of the end
            if (!m.scanMode && s.hasMore && !s.isLoadingMore && firstVisibleItem + visibleItemCount >= totalItemCount - 10) m.search.loadMore()
        }
    }

    // ------------------------------------------------------------------ the rows

    private fun bind(item: Item, reuse: View?): View = when (item) {
        is Item.Suggestion -> {
            val view = reuse as? SuggestionView ?: SuggestionView(this, colors)
            val highlighted = item === adapter.items.firstOrNull() && (!list.hasFocus() || list.isInTouchMode)
            val titleFirst = narrow && item.kind == SuggestionKind.BOOK && item.parts.size > 1
            val label = item.label ?: suggestionLabel(item.parts, item.emphasis, item.detail, titleFirst, colors, resources.displayMetrics.density).also { item.label = it }
            val shown = if (highlighted && !narrow) item.actions else item.always
            view.bind(label, item.kind, titleFirst, highlighted, shown, under = narrow && shown.size >= 2 && item.always.size >= 2)
        }
        is Item.Message -> (reuse as? MessageView ?: MessageView(this, colors)).bind(item.text, item.small, prefs.textSize * 0.875f)
        is Item.Result -> resultView(item, reuse as? ResultView)
        is Item.More -> (reuse as? MoreView ?: MoreView(this, colors)).apply { text.text = item.text }
        is Item.Entity -> entityView(item.entity)
        is Item.Notice -> if (item.full) noticeView(item.notice) else noticeLine(item.notice)
        Item.LoadingMore -> reuse ?: FrameLayout(this).apply {
            minimumHeight = dp(64)
            addView(spinner(colors), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }
    }

    /** The passages drawn lately, by line: a row drawn again (scrolled back to, a page added) is not parsed again. */
    private val snippets = LruCache<Long, CharSequence>(MAX_SNIPPETS)

    /** Each result's place in its book (its TOC path) as its row shows it ("· סימן קד"), once its breadcrumb is known; "" for none. */
    private val places = HashMap<Long, String>()

    private fun resultView(item: Item.Result, reuse: ResultView?): View {
        val view = reuse ?: ResultView(this, colors, compact)
        val hit = item.hit
        view.lineId = hit.lineId
        if (!TextUtils.equals(view.title.text, hit.bookTitle)) view.title.text = hit.bookTitle
        view.snippet.text = snippets.get(hit.lineId) ?: snippet(hit.snippet).also { snippets.put(hit.lineId, it) }
        view.setLast(item.last)
        view.place.text = places[hit.lineId] ?: placeOf(hit).orEmpty()
        return view
    }

    /** [hit]'s place from its breadcrumb, asked for (once) when not known yet. */
    private fun placeOf(hit: SearchResult): String? {
        val m = models ?: return null
        val pieces = m.search.breadcrumbs.value[hit.lineId]
        if (pieces == null) {
            m.search.requestBreadcrumb(hit)
            return null
        }
        if (pieces.isEmpty()) return null
        return shownPlace(pieces, hit.bookTitle).also { places[hit.lineId] = it }
    }

    private fun shownPlace(pieces: List<String>, bookTitle: String): String =
        categoriesAndPlace(pieces, bookTitle).second?.let { "· $it" }.orEmpty()

    /** New breadcrumbs: the rows on screen without a place get theirs. */
    private fun onBreadcrumbs(map: Map<Long, List<String>>) {
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) as? ResultView ?: continue
            if (row.place.text.isNotEmpty() || places.containsKey(row.lineId)) continue
            val pieces = map[row.lineId]?.takeIf { it.isNotEmpty() } ?: continue
            val place = shownPlace(pieces, row.title.text.toString())
            places[row.lineId] = place
            if (place.isNotEmpty()) row.place.text = place
        }
    }

    /**
     * A passage as the main app shows it (Snippet): on a phone, a little before the first word found and up to 200
     * letters after it (compactSnippet), the words found bold in the text's colour.
     */
    private fun snippet(html: String): CharSequence {
        val text = SpannableStringBuilder(LineText.fromHtml(compactSnippet(html)))
        for (span in text.getSpans(0, text.length, StyleSpan::class.java)) {
            if (span.style and Typeface.BOLD == 0) continue
            text.setSpan(ForegroundColorSpan(colors.text), text.getSpanStart(span), text.getSpanEnd(span), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    // The knowledge panel drawn, for the entity it was drawn for
    private var entityShown: SearchEntity? = null
    private var entityCard: View? = null

    /**
     * The knowledge panel (EntityPanel), the list's first row: a book's title, categories, author, parts (each opens it
     * there) and source with its licence, or an author's name, era and years, biography and books.
     */
    private fun entityView(entity: SearchEntity): View {
        entityCard?.takeIf { entityShown === entity }?.let { return it }
        val m = models
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundRect(colors.panel, dp(12).toFloat(), colors.border, maxOf(1, dp(1)))
        }
        val grey = Palette.withAlpha(colors.text, 0.72f)
        fun section(title: String) {
            box.addView(View(this).apply { setBackgroundColor(colors.border) }, lp(MATCH, maxOf(1, dp(1))).apply { setMargins(0, dp(10), 0, dp(6)) })
            box.addView(label(title, 12f, grey, bold = true, typeface = Fonts.ui(this)))
        }
        fun link(text: String, sizeSp: Float, onClick: () -> Unit) = label(text, sizeSp, colors.accent, typeface = Fonts.ui(this)).apply {
            minHeight = dp(32)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), 0)
            background = states(colors, 0, dp(4).toFloat())
            keyable(this) { onClick() }
        }
        when (entity) {
            is SearchEntity.BookEntity -> {
                val book = entity.book
                box.addView(label(book.title, 22f, colors.text, bold = true, typeface = Fonts.ui(this)))
                if (entity.categories.isNotEmpty()) box.addView(label(entity.categories.joinToString(" › "), 12f, grey, typeface = Fonts.ui(this)).apply { setPadding(0, dp(4), 0, 0) })
                entity.authors.forEach { author ->
                    section(SEARCH_PANEL_AUTHOR)
                    box.addView(link(AuthorNames.display(author.name), 14f) { m?.let { showAuthor(it, author.id) } }, lp(WRAP, WRAP))
                    val facts = listOfNotNull(author.era?.let { AUTHOR_ERAS[it] }, authorYears(author))
                    if (facts.isNotEmpty()) box.addView(label(facts.joinToString(" · "), 12f, grey, typeface = Fonts.ui(this)))
                }
                if (entity.parts.isNotEmpty()) {
                    section(SEARCH_PANEL_PARTS)
                    val parts = FlowRow(this, dp(14), dp(4), centered = false)
                    entity.parts.forEach { part -> parts.addView(link(part.title, 13f) { m?.let { openBookAt(it, book.id, part.lineId, book.title) } }) }
                    box.addView(parts, lp(MATCH, WRAP))
                }
                val editions = entity.editions.distinctBy { (it.heTitle ?: it.title) to it.licenseCode }
                if (editions.isNotEmpty() || entity.fromOtzaria) {
                    section(SEARCH_PANEL_SOURCE)
                    editions.forEach { edition ->
                        val title = edition.heTitle ?: edition.title
                        val url = edition.sourceUrl
                        box.addView(if (url != null) link(title, 13f) { openUrl(url) } else label(title, 13f, colors.text, typeface = Fonts.ui(this)), lp(WRAP, WRAP))
                        box.addView(label(licenseLabel(edition.licenseCode), 12f, grey, typeface = Fonts.ui(this)))
                    }
                    if (entity.fromOtzaria) {
                        box.addView(label(OTZARIA_CREDIT, 12f, grey, typeface = Fonts.ui(this)).apply {
                            setPadding(0, dp(6), 0, 0)
                            background = states(colors, 0, dp(4).toFloat())
                            keyable(this) { openUrl(OTZARIA_URL) }
                        })
                    }
                }
            }
            is SearchEntity.AuthorEntity -> {
                val details = entity.details
                val name = AuthorNames.display(details.name)
                val head = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                head.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_writing_hand)
                    setColorFilter(grey, PorterDuff.Mode.SRC_IN)
                }, lp(dp(20), dp(20)).apply { marginEnd = dp(8) })
                head.addView(label(name, 20f, colors.text, bold = true, typeface = Fonts.ui(this)))
                box.addView(head)
                val facts = listOfNotNull(details.era?.let { AUTHOR_ERAS[it] }, authorYears(details))
                if (facts.isNotEmpty()) box.addView(label(facts.joinToString(" · "), 12f, grey, typeface = Fonts.ui(this)).apply { setPadding(0, dp(6), 0, 0) })
                details.bio?.summary?.let {
                    box.addView(label(plainPersonLinks(it).replace("**", ""), 13f, grey, maxLines = 7, typeface = Fonts.ui(this)).apply {
                        setLineSpacing(0f, 1.25f)
                        setPadding(0, dp(6), 0, 0)
                    })
                }
                box.addView(label(AUTHOR_BOOKS_COUNT.replace("%1\$d", details.books.size.toString()), 12f, grey, typeface = Fonts.ui(this)).apply { setPadding(0, dp(6), 0, 0) })
                box.addView(label(SEARCH_PANEL_AUTHOR_PAGE, 14f, 0xFFFFFFFF.toInt(), bold = true, typeface = Fonts.ui(this)).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(40)
                    setPadding(dp(16), 0, dp(16), 0)
                    background = states(
                        roundRect(colors.accent, dp(8).toFloat()),
                        roundRect(Palette.blend(colors.accent, 0xFF000000.toInt(), 0.15f), dp(8).toFloat()),
                        roundRect(colors.accent, dp(8).toFloat(), colors.text, maxOf(1, dp(2))),
                    )
                    keyable(this) { m?.let { showAuthor(it, details.id) } }
                }, lp(WRAP, WRAP).apply { topMargin = dp(10) })
            }
        }
        val frame = FrameLayout(this).apply {
            setPadding(0, 0, 0, dp(ROW_GAP_DP))
            addView(box, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        entityShown = entity
        entityCard = frame
        return frame
    }

    // The notice drawn, for the reason it was drawn for, and its line over results
    private var noticeShown: LegacySearch.TextUnavailable? = null
    private var noticeCard: View? = null
    private var lineShown: LegacySearch.TextUnavailable? = null
    private var lineCard: View? = null

    /** The notice's title over the scan's results, with "פרטים": a row the list selects, OK (a tap) opens the notice. */
    private fun noticeLine(notice: LegacySearch.TextUnavailable): View {
        lineCard?.takeIf { lineShown == notice }?.let { return it }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val (title, color) = when (notice) {
            LegacySearch.TextUnavailable.Missing -> SEARCH_INDEX_MISSING to colors.text
            is LegacySearch.TextUnavailable.Failed -> SEARCH_FAILED to colors.error
            LegacySearch.TextUnavailable.SmallHeap -> SMALL_HEAP_TITLE to colors.text
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_info)
            setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
        }, lp(dp(18), dp(18)).apply { marginEnd = dp(8) })
        row.addView(label(title, 14f, color, bold = true, maxLines = 2, typeface = Fonts.ui(this)), lp(0, WRAP, 1f))
        row.addView(label(DETAILS, 14f, colors.accent, typeface = Fonts.ui(this)), lp(WRAP, WRAP).apply { marginStart = dp(8) })
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(ROW_GAP_DP))
            addView(row, lp(MATCH, WRAP))
            addView(View(this@SearchActivity).apply { setBackgroundColor(Palette.withAlpha(colors.border, 0.5f)) }, lp(MATCH, maxOf(1, dp(1))).apply { marginStart = dp(8) })
        }
        lineShown = notice
        lineCard = frame
        return frame
    }

    /** The notice in full, over the results (its line's "פרטים"). */
    private fun showNotice(notice: LegacySearch.TextUnavailable) {
        lateinit var dialog: android.app.Dialog
        val view = noticeContent(notice) { dialog.dismiss() }
        dialog = Dialogs.show(this, colors, null, view)
    }

    /**
     * Why the text is not searched in the library's index: the main app's "index missing" (SearchIndexMissing) or
     * "search failed" texts, or a heap too small for it, each with what can be done about it ("נסה שוב").
     */
    private fun noticeView(notice: LegacySearch.TextUnavailable): View {
        noticeCard?.takeIf { noticeShown == notice }?.let { return it }
        val box = noticeContent(notice) {}
        noticeShown = notice
        noticeCard = box
        return box
    }

    /** The notice's texts and its button, which runs [before] (closing a dialog it is in) first. */
    private fun noticeContent(notice: LegacySearch.TextUnavailable, before: () -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        fun button(text: String, onClick: () -> Unit) = label(text, 14f, colors.accent, bold = true, typeface = Fonts.ui(this)).apply {
            gravity = Gravity.CENTER
            minHeight = dp(40)
            setPadding(dp(14), 0, dp(14), 0)
            background = states(
                roundRect(0, dp(8).toFloat(), colors.accent, maxOf(1, dp(1))),
                roundRect(colors.pressed, dp(8).toFloat(), colors.accent, maxOf(1, dp(1))),
                roundRect(colors.focus, dp(8).toFloat(), colors.accent, maxOf(1, dp(2))),
            )
            keyable(this) {
                before()
                onClick()
            }
        }
        when (notice) {
            LegacySearch.TextUnavailable.Missing -> {
                box.addView(label(SEARCH_INDEX_MISSING, 17f, colors.text, bold = true, typeface = Fonts.ui(this)))
                box.addView(label(INDEX_MISSING_TEXT, 15f, colors.text, typeface = Fonts.ui(this)).apply {
                    setLineSpacing(0f, 1.3f)
                    setPadding(0, dp(10), 0, 0)
                })
                box.addView(label(SEARCH_LIBRARY_FOLDER, 13f, colors.secondary, typeface = Fonts.ui(this)).apply { setPadding(0, dp(10), 0, 0) })
                val dir = models?.opened?.file?.parent.orEmpty()
                box.addView(label(dir, 13f, colors.text, typeface = Typeface.MONOSPACE).apply {
                    textDirection = View.TEXT_DIRECTION_LTR
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    setTextIsSelectable(true)
                    setPadding(0, dp(4), 0, 0)
                })
            }
            is LegacySearch.TextUnavailable.Failed -> {
                box.addView(label(SEARCH_FAILED, 15f, colors.error, bold = true, typeface = Fonts.ui(this)))
                if (notice.message.isNotEmpty()) {
                    box.addView(label(notice.message, 12f, colors.secondary).apply {
                        setTextIsSelectable(true)
                        setPadding(0, dp(4), 0, 0)
                    })
                }
            }
            LegacySearch.TextUnavailable.SmallHeap -> {
                box.addView(label(SMALL_HEAP_TITLE, 17f, colors.text, bold = true, typeface = Fonts.ui(this)))
                box.addView(label(SMALL_HEAP_TEXT, 15f, colors.text, typeface = Fonts.ui(this)).apply {
                    setLineSpacing(0f, 1.3f)
                    setPadding(0, dp(10), 0, 0)
                })
                box.addView(button(SMALL_HEAP_OPEN) {
                    prefs.searchIndexOnSmallHeap = true
                    retry()
                }, lp(WRAP, WRAP).apply { topMargin = dp(12) })
            }
        }
        if (notice != LegacySearch.TextUnavailable.SmallHeap) box.addView(button(RETRY) { retry() }, lp(WRAP, WRAP).apply { topMargin = dp(12) })
        return box
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            toast(url)
        }
    }

    companion object {
        const val EXTRA_QUERY = "query"
        const val EXTRA_BOOK_SCOPE = "book_scope"

        /** Search [EXTRA_QUERY]'s text at once (a zayit://search link), rather than suggest. */
        const val EXTRA_RUN = "run"

        private const val TOP_TABS = 5
        private const val MAX_SNIPPETS = 160
        private const val ALL_TAG = "all"
        private const val MORE_TAG = "more"
        private const val RETRY = "נסה שוב"
        private const val DETAILS = "פרטים"

        // What onSaveInstanceState keeps (the search shown, and the field)
        private const val STATE_QUERY = "search_query"
        private const val STATE_EXTENDED = "search_extended"
        private const val STATE_BOOK = "search_book"
        private const val STATE_TOC = "search_toc"
        private const val STATE_AUTHOR = "search_author"
        private const val STATE_FIELD = "search_field"

        // The main app's author page (AuthorScreen), under the biography.
        private const val AUTHOR_BIO_NOTE =
            "שימו לב: הביוגרפיה נכתבה באופן אוטומטי בעזרת בינה מלאכותית על פי המקורות המצוינים בה, ולא עברה בדיקה אנושית. " +
                "ייתכנו טעויות בתאריכים, בשמות ובפרטים; אמתו מול מקור מהימן לפני הסתמכות."
        private const val AUTHOR_BIO_LICENSE = "הביוגרפיה מתפרסמת ברישיון CC BY-SA 4.0, על פי המקורות המצוינים בה."

        /**
         * The main app's SEARCH_INDEX_MISSING_TEXT, word for word up to how to install the library again: this edition
         * does it in its Settings › ספרייה (InstallActivity), and has no "איפוס האפליקציה" nor notes and highlights to
         * export first.
         */
        private const val INDEX_MISSING_TEXT =
            "החיפוש בטקסט המלא משתמש באותו אינדקס שהאפליקציה למחשב שומרת לצד מסד הנתונים: התיקיות seforim.db.lucene " +
                "ו־seforim.db.lookup.lucene והקובץ lexical.db. האינדקס אינו נבנה על המכשיר. כדי לקבל אותו, העבירו את הקבצים מהמחשב " +
                "לתיקיית הספרייה בעזרת סקריפט ההעברה (push-desktop-library), או התקינו את הספרייה מחדש (החבילה המלאה כוללת אותו) " +
                "בהגדרות › ספרייה › " + InstallTexts.TITLE + "."

        // This edition's: the index is there, but the phone's memory for an app is small for it (LegacySearch).
        private const val SMALL_HEAP_TITLE = "אינדקס החיפוש לא נפתח בטלפון הזה"
        private const val SMALL_HEAP_TEXT =
            "אינדקס החיפוש נמצא בספרייה, אבל הזיכרון שאנדרואיד נותן לפרדס בטלפון הזה קטן מדי בשבילו, ולכן הטקסט נסרק " +
                "ספר אחר ספר. אפשר לנסות לפתוח אותו בכל זאת: אם החיפוש ייכשל, פרדס יחזור לסריקה."
        private const val SMALL_HEAP_OPEN = "לפתוח את האינדקס בכל זאת"

        /** Desktop's placeholder grey (HomeView SearchBar, both themes). */
        const val PLACEHOLDER_GREY = 0xFF9AA0A6.toInt()

        private var hint = 0

        /**
         * The bar's hints (UnifiedSearchBar HINTS: a reference, then a text, in turn), one a time the bar is shown:
         * the main app types them out; here a still one, which costs an old phone nothing.
         */
        fun nextHint(): String = HINTS[hint++ % HINTS.size]
    }
}

package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.BackgroundColorSpan
import android.text.style.LineHeightSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.search.AUTHOR_ERAS
import io.github.kdroidfilter.seforimapp.android.search.BookDetailsLoader
import io.github.kdroidfilter.seforimapp.android.search.OTZARIA_CREDIT
import io.github.kdroidfilter.seforimapp.android.search.OTZARIA_URL
import io.github.kdroidfilter.seforimapp.android.search.SearchEntity
import io.github.kdroidfilter.seforimapp.android.search.authorYears
import io.github.kdroidfilter.seforimapp.android.search.licenseLabel
import io.github.kdroidfilter.seforimapp.android.ui.reader.BookFindMatches
import io.github.kdroidfilter.seforimapp.android.ui.reader.CommentatorItem
import io.github.kdroidfilter.seforimapp.android.ui.reader.DiacriticsMode
import io.github.kdroidfilter.seforimapp.android.ui.reader.LineConnections
import io.github.kdroidfilter.seforimapp.android.ui.reader.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.android.ui.reader.ReaderLinks
import io.github.kdroidfilter.seforimapp.android.ui.reader.findAllMatchesOriginal
import io.github.kdroidfilter.seforimapp.android.ui.reader.nextActionLabel
import io.github.kdroidfilter.seforimapp.android.ui.shell.bookShareLink
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.BookInfo
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.Place
import io.github.kdroidfilter.seforimapp.legacy.data.Prefs
import io.github.kdroidfilter.seforimapp.legacy.data.label
import io.github.kdroidfilter.seforimlibrary.core.models.AltTocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * A book, as the main app's phone reader (ReaderScreen): the title bar (☰ the table of contents, the book's title over
 * its category and the heading of the place shown, find, ⋮), the text in an Island, read page by page as it scrolls
 * into view, justified, with the alternative structure's headings (פרשה, עליה, דף) above their lines, the line marked
 * by the accent's bar at its start, and the bottom toolbar with the buttons the book has (בית, מפרשים, תרגומים, מקור,
 * ניקוד, then גודל and עוד, the rest overflowing into עוד). מפרשים, תרגומים and מקור open the pane under the text, an
 * Island with its tabs, which follows the line marked. The find bar (FindBar, ReaderFind) lies across the text's top
 * while it is open; a book opened from a search result opens with it, holding the search's query.
 *
 * On a keypad (Android 4.4 phones with keys): ▲▼ go from line to line, and the pane follows; OK marks the line; ◀▶ turn
 * a page, ◀ forward as a Hebrew book's pages turn, or, with the pane open, go into it (its tabs, then ▼ its content, where
 * OK on a text with links lists them, and the toolbar under it; ▲ at its top, or Back, come back to the text); * opens
 * the find bar (OK or ▼ the next match, ▲ the one before, Back closes it); Menu opens עוד; Back closes the pane; the
 * digits are shortcuts ([SHORTCUTS], listed in עוד).
 */
class BookActivity : BaseActivity() {
    private var bookId = 0L
    private var book: BookInfo? = null
    private lateinit var opened: Library.Opened
    private lateinit var connections: LineConnections
    private lateinit var readerLinks: ReaderLinks
    private lateinit var bar: TopBar
    private lateinit var body: LinearLayout
    private lateinit var textCard: FrameLayout
    private lateinit var list: ListView
    private lateinit var handle: View
    private lateinit var pane: PaneView
    private lateinit var toolbar: LinearLayout
    private val adapter = LinesAdapter()

    /** The line marked (tapped, or OK), whose links the pane shows; -1 for none. */
    private var selected = -1
    private var hitColor = 0

    /** The find in the page once the book is read, and its bar once first opened. */
    private var find: ReaderFind? = null
    private var findBar: FindBar? = null
    private var revealJob: Job? = null

    /** A line a find step put on the keypad's highlight: no pane follows it (a find step marks nothing). */
    private var noFollow = -1

    /** The headings the preferred alternative structure puts above lines (AltHeading), by line id. */
    private var altHeadings: Map<Long, List<AltTocEntry>> = emptyMap()
    private var placeJob: Job? = null
    private var followJob: Job? = null
    private var availableJob: Job? = null
    private var categoryTitle: String? = null
    private var heading: String = ""

    /** The book's categories from the root, and the headings of the place shown from the book's top (עוד's path). */
    private var categoryPath: List<String> = emptyList()
    private var placePath: List<String> = emptyList()

    /** The first line shown that the heading was asked for, and whether a finger is scrolling the text. */
    private var placeIndex = -1
    private var scrolling = false

    /**
     * The line the heading and the path were last worked out for (the main app's placeLineId), which עוד's link, share
     * and favourite are about so that they name the place its path shows; -1 before the first.
     */
    private var namedLine = -1

    /**
     * A fling, or the fast scroller's thumb, is moving the text: the lines it brings in are drawn start-aligned until it
     * stops (justifying a line measures it again and draws it word by word: frames took a fifth longer on the Android 4.4
     * AVD during a fling, and a thumb's drag brings in a screen of lines at each move).
     */
    private var skimming = false

    /** A press is being handed to the text (its ACTION_DOWN): the scroll state it reports is the fast scroller's thumb. */
    private var pressing = false

    /** The fast scroller's thumb is being dragged (from its press to the text's rest). */
    private var thumbDrag = false

    /** The marks the lines are drawn with, to see on coming back from Settings whether they changed. */
    private var marks = DiacriticsMode.All

    /** What the marked line links to, for the toolbar's faded buttons; null until known. */
    private var available: LineConnectionsSnapshot? = null

    /** The pane opened to the top (the text keeps a line), else half the reader. */
    private var expanded = false
    private var style: LineStyle? = null
    private val toolbarButtons = LinkedHashMap<String, RailButton>()
    private var overflowActions: List<Action> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookId = intent.getLongExtra(EXTRA_BOOK, 0L)
        val requested = intent.getIntExtra(EXTRA_LINE, -1).takeIf { it >= 0 }
        val startLine = savedInstanceState?.getInt(STATE_TOP) ?: requested ?: prefs.lastLine(bookId) ?: 0
        // The line asked for (a search result, a heading, a favourite, a link) is marked, as the main app marks it.
        selected = savedInstanceState?.getInt(STATE_SELECTED, -1) ?: requested ?: -1
        expanded = savedInstanceState?.getBoolean(STATE_EXPANDED) ?: false
        val paneAtStart = savedInstanceState?.getString(STATE_PANE)?.let { name -> Pane.entries.firstOrNull { it.name == name } }
        // A keypad's line and where it was on the screen (restored): it comes back there.
        val keypadAtStart = savedInstanceState?.takeIf { it.containsKey(STATE_KEYPAD) }
            ?.let { it.getInt(STATE_KEYPAD) to it.getInt(STATE_KEYPAD_TOP) }
        hitColor = Palette.withAlpha(colors.accent, 0.22f)
        marks = prefs.diacritics

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_menu, "תוכן עניינים") { openToc() }
            title.text = intent.getStringExtra(EXTRA_TITLE) ?: ""
            // Very narrow phones: find moves into the menus so the title stays readable (as the main app).
            if (!narrow()) action(R.drawable.ic_find_in_page, FIND_IN_PAGE) { openFind() }
            overflow { appMenu() }
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))

        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCard = FrameLayout(this).apply { background = island(colors) }
        list = object : ListView(this) {
            // A finger's drag reports its scroll state once it has moved; the fast scroller's thumb reports it in the press
            // itself (FastScroller.beginDrag, on its ACTION_DOWN): told apart here.
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked != MotionEvent.ACTION_DOWN) return super.dispatchTouchEvent(ev)
                pressing = true
                try {
                    return super.dispatchTouchEvent(ev)
                } finally {
                    pressing = false
                }
            }
        }.apply {
            divider = null
            dividerHeight = 0
            cacheColorHint = colors.panel
            isVerticalFadingEdgeEnabled = false
            val r = dp(6).toFloat()
            selector = states(ColorDrawable(0), roundRect(colors.pressed, r), roundRect(colors.accentTint, r))
            isFastScrollEnabled = true
            verticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT
            setPaddingRelative(dp(8), dp(TEXT_TOP_DP), dp(12), dp(6))
            adapter = this@BookActivity.adapter
            setOnItemClickListener { _, _, position, _ -> mark(position) }
            setOnItemLongClickListener { _, _, position, _ ->
                lineActions(position)
                true
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                // A keypad moved to another line: the pane, when open, follows it after a moment.
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    // The place follows a keypad's line (placeOnScreen), once it rests
                    updatePlace()
                    // Also when it came back to the line marked: a follow still on its way would mark the line passed.
                    followJob?.cancel()
                    val stepped = position == noFollow
                    noFollow = -1
                    if (stepped || pane.shown == null || pane.shown == Pane.Details || position == selected) return
                    followJob = scope.launch {
                        delay(FOLLOW_DELAY_MS)
                        mark(position)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
                    val skimmed = skimming
                    when (scrollState) {
                        AbsListView.OnScrollListener.SCROLL_STATE_IDLE -> thumbDrag = false
                        AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL -> if (pressing) thumbDrag = true
                    }
                    skimming = scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING || thumbDrag
                    // The lines a fling or the thumb brought in, justified now that it has stopped
                    if (skimmed && !skimming) justifyShown()
                    scrolling = scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                    if (!scrolling) updatePlace()
                }

                // A keypad's moves (▲▼, a page) scroll the text with no scroll state: the place follows them here.
                override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) {
                    if (!scrolling && first != placeIndex) updatePlace()
                }
            })
            setOnKeyListener { _, keyCode, event -> listKey(keyCode, event) }
        }
        textCard.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        body.addView(textCard, lp(MATCH, 0, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        handle = paneHandle()
        body.addView(handle, lp(MATCH, dp(PANE_HANDLE_DP)))
        pane = PaneView()
        body.addView(pane.root, lp(MATCH, 0).apply { setMargins(dp(4), 0, dp(4), dp(4)) })
        body.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) body.post { sizePane() }
        }
        root.addView(body, lp(MATCH, 0, 1f))
        toolbarLine = View(this).apply { setBackgroundColor(colors.border) }
        root.addView(toolbarLine, lp(MATCH, maxOf(1, dp(1))))
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colors.panel)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            // Shown once the book is read and its buttons known (as the main app): בית is the way out meanwhile.
            visibility = View.INVISIBLE
        }
        root.addView(toolbar, lp(MATCH, WRAP))
        root.viewTreeObserver.addOnGlobalLayoutListener { updateToolbarForKeyboard() }
        setContentView(root)
        pane.show(null)

        val waiting = spinner(colors)
        textCard.addView(waiting, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val lib = app.library.open()
                    val info = lib.book(bookId) ?: error("הספר לא נמצא בספרייה")
                    val sticky = prefs.commentators(bookId)
                        ?: runCatching { lib.repository.getDefaultCommentatorIdsForBook(bookId).toSet() }.getOrDefault(emptySet())
                    val total = info.totalLines.takeIf { it > 0 } ?: lib.lineCount(bookId)
                    Loaded(lib, info, sticky, total, lib.categoryPath(info.categoryId).map { it.title })
                }
            }
            textCard.removeView(waiting)
            result.onFailure { e ->
                toast(e.message ?: "לא ניתן לפתוח את הספר")
                finish()
            }.onSuccess { loaded ->
                opened = loaded.library
                book = loaded.info
                pane.sticky = loaded.sticky
                categoryPath = loaded.categories
                categoryTitle = loaded.categories.lastOrNull()
                connections = LineConnections(loaded.library.repository) { loaded.info.title }
                readerLinks = ReaderLinks(loaded.library.repository)
                bar.title.text = loaded.info.title
                buildToolbar()
                // A search result's query in the find bar, there before the text so that the line opened lands below
                // it; or the bar as it was when Android ended the app.
                val seed = intent.getStringExtra(EXTRA_FIND)?.takeIf { it.trim().length >= 2 }
                find = ReaderFind(scope, app.search, loaded.library.repository, loaded.info.id, seed, searchPhrase = !intent.getBooleanExtra(EXTRA_FIND_WORDS, false), ::onFindChange)
                val findAtStart = savedInstanceState?.takeIf { it.getBoolean(STATE_FIND_OPEN) }?.getString(STATE_FIND_QUERY)
                if (findAtStart != null) {
                    openFind(findAtStart, focus = false)
                } else if (seed != null && savedInstanceState == null) {
                    openFind(seed, focus = false)
                }
                loadAltHeadings(loaded.info)
                adapter.total = loaded.total
                val keypad = keypadAtStart?.takeIf { (line, _) -> !list.isInTouchMode && line < loaded.total }
                if (keypad != null) {
                    // setSelection would put a keypad's line on the first line shown, and an open pane would follow it.
                    place(keypad.first, keypad.second)
                } else {
                    place(startLine.coerceIn(0, maxOf(0, loaded.total - 1)), 0)
                }
                list.post { updatePlace() }
                if (selected >= 0) mark(selected, follow = false)
                if (paneAtStart != null) showPane(paneAtStart)
                // A keypad starts on the text, at the line asked for.
                if (!list.isInTouchMode) list.requestFocus()
            }
        }
    }

    private class Loaded(val library: Library.Opened, val info: BookInfo, val sticky: Set<Long>, val total: Int, val categories: List<String>)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TOP, topLine())
        outState.putInt(STATE_SELECTED, selected)
        outState.putBoolean(STATE_EXPANDED, expanded)
        pane.shown?.let { outState.putString(STATE_PANE, it.name) }
        find?.takeIf { it.open }?.let { f ->
            outState.putBoolean(STATE_FIND_OPEN, true)
            outState.putString(STATE_FIND_QUERY, f.query)
        }
        val keypad = if (list.isInTouchMode) -1 else list.selectedItemPosition
        list.getChildAt(keypad - list.firstVisiblePosition)?.let { row ->
            outState.putInt(STATE_KEYPAD, keypad)
            outState.putInt(STATE_KEYPAD_TOP, row.top - list.paddingTop)
        }
    }

    override val heldLibrary: Library.Opened? get() = if (::opened.isInitialized) opened else null

    override fun onResume() {
        super.onResume()
        if (rebuilding) return
        applySettings()
    }

    /** What Settings change for the book (ניקוד, the size, the font, the screen kept on), applied on coming back. */
    private fun applySettings() {
        if (prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (book == null) return
        val marksChanged = prefs.diacritics != marks
        val drawn = style
        val lookChanged = drawn != null && (drawn.size != prefs.textSize.toFloat() || drawn.typeface !== Fonts.book(this))
        if (!marksChanged && !lookChanged) return
        marks = prefs.diacritics
        if (lookChanged) style = null
        redrawLines(clear = marksChanged)
        pane.restyle()
        updateToolbar()
    }

    override fun onPause() {
        super.onPause()
        val info = book ?: return
        prefs.rememberPlace(Place(info.id, info.title, topLine(), heading))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val line = data.getIntExtra(EXTRA_LINE, -1)
        if (line >= 0) jumpTo(line)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            // The find bar first (the main app's order), unless it is a search result's left as it came: that Back goes
            // back to the results.
            find?.backCloses() == true -> closeFind()
            // A keypad in the pane or the toolbar: back to the text first, the pane left open.
            !list.isInTouchMode && (pane.root.hasFocus() || handle.hasFocus() || toolbar.hasFocus()) -> list.requestFocus()
            pane.shown != null -> closePane()
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    /** A place chosen in the table of contents or found by the search: at the top of the text, and marked. */
    private fun jumpTo(index: Int) {
        place(index, 0)
        mark(index)
        list.post { updatePlace() }
    }

    // ------------------------------------------------------------------ keys

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (book == null) return super.onKeyDown(keyCode, event)
        // 2 and 8 scroll while held; the others act once.
        if (event.repeatCount > 0 && keyCode != KeyEvent.KEYCODE_2 && keyCode != KeyEvent.KEYCODE_8) {
            return keyCode in SHORTCUT_KEYS || super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_1 -> openToc()
            KeyEvent.KEYCODE_2 -> if (pane.shown != null) pane.scroll(up = true) else page(forward = false)
            KeyEvent.KEYCODE_8 -> if (pane.shown != null) pane.scroll(up = false) else page(forward = true)
            // A book with no commentaries has no מפרשים button either.
            KeyEvent.KEYCODE_3 -> if (book?.hasCommentaries == true) togglePane(Pane.Commentaries)
            KeyEvent.KEYCODE_4 -> pane.stepCommentator(towardsLeft = true)
            KeyEvent.KEYCODE_6 -> pane.stepCommentator(towardsLeft = false)
            KeyEvent.KEYCODE_5 -> cycleDiacritics()
            KeyEvent.KEYCODE_7 -> zoom(-1)
            KeyEvent.KEYCODE_9 -> zoom(+1)
            KeyEvent.KEYCODE_0 -> goHome()
            KeyEvent.KEYCODE_STAR -> openFind()
            KeyEvent.KEYCODE_POUND -> toggleFavorite()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    /**
     * ◀ and ▶ on the text turn a page: ◀ forward, as the pages of a Hebrew book turn. With the pane open they go into it
     * instead (the text keeps ▲▼ and the pane 2 and 8), the one way a keypad has past the text to the pane's controls and
     * the toolbar: ▼ on the text only goes from line to line.
     */
    private fun listKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (pane.shown == null) {
                page(forward = keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
            } else if (event.repeatCount == 0) {
                pane.focus()
            }
        }
        return true
    }

    private fun page(forward: Boolean) {
        if (list.isInTouchMode) {
            val step = list.height - dp(48)
            list.scrollListBy(if (forward) step else -step)
            updatePlace()
        } else {
            // ListView's own page step (pageScroll is not public): it moves the keypad's place a page too.
            val code = if (forward) KeyEvent.KEYCODE_PAGE_DOWN else KeyEvent.KEYCODE_PAGE_UP
            list.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // A keypad's Menu key: the toolbar's עוד, the book's menu (the title bar's ⋮ holds the app's, as the main app's)
        if (keyCode == KeyEvent.KEYCODE_MENU && book != null && toolbar.visibility == View.VISIBLE) {
            moreMenu()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun hasKeys(): Boolean {
        val c = resources.configuration
        return c.keyboard != Configuration.KEYBOARD_NOKEYS || c.navigation == Configuration.NAVIGATION_DPAD || c.navigation == Configuration.NAVIGATION_TRACKBALL
    }

    private fun showShortcuts() {
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), dp(6))
        }
        SHORTCUTS.forEach { (key, what) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            row.addView(label(key, 15f, colors.text, bold = true).apply { gravity = Gravity.CENTER }, lp(dp(64), WRAP))
            row.addView(label(what, 14f, colors.text), lp(0, WRAP, 1f))
            rows.addView(row)
        }
        Dialogs.show(this, colors, "מקשי קיצור", rows)
    }

    // ------------------------------------------------------------------ the title bar and the menus

    /** Under 300dp the title bar has no find button (the title keeps its room): find is in עוד and ⋮ (the main app's). */
    private fun narrow(): Boolean = resources.configuration.screenWidthDp < 300

    /**
     * The toolbar's עוד (and a keypad's Menu key), as the main app's (ReaderScreen PhoneToolbar): find under 300dp, the
     * toolbar's buttons that overflow, פרטי הספר, רשימת הספרים, the place's path (to the table of contents there), העתק
     * קישור, שתף, the favourite; then this edition's מקשי קיצור on a phone with keys. Not here: the notes and the
     * multiple selection, which this edition does not have.
     */
    private fun moreEntries(): List<MenuEntry> = buildList {
        val info = book ?: return@buildList
        if (narrow()) add(MenuEntry(FIND_IN_PAGE, R.drawable.ic_find_in_page) { openFind() })
        overflowActions.forEach { a -> add(MenuEntry(a.menuLabel, a.icon) { a.perform() }) }
        add(MenuEntry("פרטי הספר", R.drawable.ic_info) { togglePane(Pane.Details) })
        add(MenuEntry("רשימת הספרים", R.drawable.ic_library) { LibraryActivity.start(this@BookActivity, info.categoryId) })
        add(MenuEntry(placePathText(info), R.drawable.ic_book) { openToc() })
        add(MenuEntry("העתק קישור", R.drawable.ic_link) { copyLink(placeLine()) })
        add(MenuEntry("שתף", R.drawable.ic_share) { shareLink(placeLine()) })
        if (prefs.isBookmarked(info.id, placeLine())) {
            add(MenuEntry("עריכת מועדף", R.drawable.ic_star, tint = colors.accent) { editFavorite() })
        } else {
            add(MenuEntry("הוסף למועדפים", R.drawable.ic_star_border) { toggleFavorite() })
        }
        if (hasKeys()) add(MenuEntry("מקשי קיצור", R.drawable.ic_info) { showShortcuts() })
    }

    /**
     * The title bar's ⋮: the app's items, in the main app's order (TopBar): דף הבית, find under 300dp, מועדפים,
     * היסטוריה, the theme, הגדרות. The book's are in עוד.
     */
    private fun appMenu(): List<MenuEntry> = buildList {
        add(MenuEntry("דף הבית", R.drawable.ic_home) { goHome() })
        if (narrow() && book != null) add(MenuEntry(FIND_IN_PAGE, R.drawable.ic_find_in_page) { openFind() })
        add(MenuEntry("מועדפים", R.drawable.ic_star_border) { PlacesDialogs.favorites(this@BookActivity, colors) })
        add(MenuEntry("היסטוריה", R.drawable.ic_history) { PlacesDialogs.recents(this@BookActivity, colors) })
        add(MenuEntry(if (prefs.dark) "ערכת נושא כהה" else "ערכת נושא בהירה", if (prefs.dark) R.drawable.ic_dark_mode else R.drawable.ic_light_mode) {
            prefs.dark = !prefs.dark
            recreate()
        })
        add(MenuEntry("הגדרות", R.drawable.ic_settings) { startActivity(Intent(this@BookActivity, SettingsActivity::class.java)) })
    }

    /** The place's path, as the main app's עוד names it: the categories › the book › its headings ("תורה › בראשית › פרק כו"). */
    private fun placePathText(info: BookInfo): String =
        (categoryPath + info.title + placePath.dropWhile { it == info.title }).joinToString(" › ")

    /** "<book> - <heading>": the place the favourite and the shared link name (the main app's placeTitle). */
    private fun placeTitle(info: BookInfo): String = heading.takeIf { it.isNotBlank() }?.let { "${info.title} - $it" } ?: info.title

    private fun openToc() {
        val info = book ?: return
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, TocActivity::class.java).putExtra(EXTRA_BOOK, info.id).putExtra(EXTRA_TITLE, info.title)
                .putExtra(EXTRA_LINE, placeLine()),
            REQUEST_TOC,
        )
    }

    private fun goHome() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    /**
     * The line עוד's actions (the link, שתף, the favourite) and the table of contents are about: the one its path and the
     * title bar's place name (the main app's placeLineId), the place on screen until that is known.
     */
    private fun placeLine(): Int = namedLine.takeIf { it in 0 until adapter.total } ?: placeOnScreen()

    /** The line a keypad's highlight is on (the text has focus, out of touch mode), else -1. */
    private fun keypadLine(): Int = if (!list.isInTouchMode && list.hasFocus()) list.selectedItemPosition else -1

    private fun toggleFavorite() {
        val info = book ?: return
        val index = placeLine()
        if (prefs.isBookmarked(info.id, index)) {
            prefs.removeBookmark(Place(info.id, info.title, index))
            toast("הוסר מהמועדפים")
        } else {
            val text = adapter.lineAt(index)?.let { LineText.plain(LineText.stripMarks(it.content, DiacriticsMode.None)).take(80) } ?: ""
            prefs.addBookmark(Place(info.id, info.title, index, heading.ifEmpty { text }))
            toast("נוסף למועדפים")
        }
    }

    /**
     * עריכת מועדף, the starred place (the main app's FavoriteEditSheet, without its folders, which this edition's
     * favourites do not have): the accent's star and the place's name, and "הסר מהמועדפים".
     */
    private fun editFavorite() {
        val info = book ?: return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(8))
        }
        head.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_star)
            setColorFilter(colors.accent, android.graphics.PorterDuff.Mode.SRC_IN)
        }, lp(dp(22), dp(22)).apply { marginEnd = dp(10) })
        head.addView(label(placeTitle(info), 16f, colors.text, bold = true, maxLines = 2), lp(0, WRAP, 1f))
        box.addView(head)
        lateinit var dialog: android.app.Dialog
        box.addView(TreeRow(this, colors).bind("הסר מהמועדפים", iconRes = R.drawable.ic_star_border, iconTint = colors.text).apply {
            keyable(this) {
                dialog.dismiss()
                toggleFavorite()
            }
        })
        dialog = Dialogs.show(this, colors, null, box)
    }

    private fun copyLink(index: Int) = withLineId(index) { id ->
        copy(bookShareLink(bookId, id))
        toast("הקישור הועתק")
    }

    /** שתף: the place's link to the system's share sheet, titled "<book> - <heading>" (the main app's shareLink). */
    private fun shareLink(index: Int) {
        val info = book ?: return
        val title = placeTitle(info)
        withLineId(index) { id ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, bookShareLink(bookId, id)).putExtra(Intent.EXTRA_SUBJECT, title)
            try {
                startActivity(Intent.createChooser(send, title))
            } catch (_: ActivityNotFoundException) {
                copyLink(index)
            }
        }
    }

    /** [then] with the id of line [index]; its page not read (the line marked, far from the text shown), read first. */
    private fun withLineId(index: Int, then: (Long) -> Unit) {
        adapter.lineAt(index)?.let { return then(it.id) }
        scope.launch {
            val id = withContext(Dispatchers.IO) { runCatching { opened.repository.getLines(bookId, index, index).firstOrNull()?.id }.getOrNull() }
            if (id != null) then(id)
        }
    }

    private fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("פרדס", text))
    }

    /** A long press on a line (a long OK on a keypad): what can be done with it. */
    private fun lineActions(index: Int) {
        val line = adapter.lineAt(index) ?: return
        val info = book ?: return
        val entries = buildList {
            add(MenuEntry("העתק את הטקסט", R.drawable.ic_copy) {
                copy(LineText.plain(LineText.stripMarks(line.content, prefs.diacritics)))
                toast("הטקסט הועתק")
            })
            add(MenuEntry("העתק קישור", R.drawable.ic_link) { copyLink(index) })
            add(MenuEntry("הוסף למועדפים", R.drawable.ic_star_border) {
                prefs.addBookmark(Place(info.id, info.title, index, LineText.plain(LineText.stripMarks(line.content, DiacriticsMode.None)).take(80)))
                toast("נוסף למועדפים")
            })
            if (info.hasCommentaries) add(MenuEntry("מפרשים", R.drawable.ic_comment) { mark(index); showPane(Pane.Commentaries) })
            if (info.hasTargum) add(MenuEntry("תרגומים", R.drawable.ic_translate) { mark(index); showPane(Pane.Targum) })
            if (info.hasSources) add(MenuEntry("מקורות", R.drawable.ic_source) { mark(index); showPane(Pane.Sources) })
            // Its links, which a keypad cannot tap
            adapter.renderedAt(index)?.let { addAll(linkEntries(it)) }
        }
        Dialogs.choices(this, colors, null, entries)
    }

    /** The links of [text], each by its own text ("(דברים ו)"), to open from a keypad's menu. */
    private fun linkEntries(text: Spanned): List<MenuEntry> =
        text.getSpans(0, text.length, LineText.Link::class.java).sortedBy { text.getSpanStart(it) }.map { link ->
            val label = text.subSequence(text.getSpanStart(link), text.getSpanEnd(link)).toString().trim()
            MenuEntry(label.ifEmpty { link.url }, R.drawable.ic_link) { openLink(link.url) }
        }

    /**
     * A link tapped (the main app's uriHandler): a citation (seforim://line/<id>) opens its line's book there, in a new
     * reader as the main app opens a new tab; any other link goes to the system.
     */
    private fun openLink(url: String) {
        val lineId = LineText.citedLine(url) ?: return openUrl(url)
        scope.launch {
            val place = withContext(Dispatchers.IO) { runCatching { opened.lineLocation(lineId) }.getOrNull() } ?: return@launch
            start(this@BookActivity, place.first, place.second)
        }
    }

    /**
     * Taps on a text's links: a press on one is the link's (the list's line or the pane's text keeps the others), opened
     * when let go on it; a drag hands the press back to the list, which scrolls.
     */
    private inner class LinkTaps(private val view: TextView) {
        private var pressed: LineText.Link? = null

        /** Whether [e] was a link's. */
        fun onTouch(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = linkAt(e.x, e.y)
                    return pressed != null
                }
                MotionEvent.ACTION_MOVE -> return pressed != null
                MotionEvent.ACTION_UP -> {
                    val link = pressed ?: return false
                    pressed = null
                    if (linkAt(e.x, e.y) === link) openLink(link.url)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> pressed = null
            }
            return false
        }

        private fun linkAt(x: Float, y: Float): LineText.Link? {
            val layout = view.layout ?: return null
            val text = view.text as? Spanned ?: return null
            val line = layout.getLineForVertical((y - view.totalPaddingTop + view.scrollY).toInt())
            val left = x - view.totalPaddingLeft + view.scrollX
            if (left < layout.getLineLeft(line) || left > layout.getLineRight(line)) return null
            val offset = layout.getOffsetForHorizontal(line, left)
            return text.getSpans(offset, offset, LineText.Link::class.java).firstOrNull()
        }
    }

    // ------------------------------------------------------------------ find in the page

    /**
     * Opens the find bar ([query]: a search result's), or with the bar open puts the cursor back in it. [focus]: asked
     * for by the user (the title bar's button, עוד, *): the cursor in the field, the query selected so that typing
     * replaces it, and the keyboard where there is one; a search result's bar asks for none, so the passage stays in view.
     */
    private fun openFind(query: String? = null, focus: Boolean = true) {
        val f = find ?: return
        val bar = findBar ?: makeFindBar()
        val wasOpen = f.open
        f.open(query, focus)
        if (focus || !wasOpen || query != null) bar.setQuery(f.query, selectAll = focus, fromStart = true)
        if (!wasOpen) showFindRoom(true)
        if (focus) {
            bar.field.requestFocus()
            showKeyboard(bar.field)
        }
        updateFindBar()
    }

    /** ✕, Back, Esc: the bar closes, and what it lit; a keypad goes back to the text. */
    private fun closeFind() {
        val f = find ?: return
        val bar = findBar
        bar?.let { hideKeyboard(it.field) }
        revealJob?.cancel()
        f.close()
        showFindRoom(false)
        if (!list.isInTouchMode || bar?.hasFocus() == true) list.requestFocus()
        // The match no longer names the place (placeOnScreen)
        updatePlace()
    }

    private fun makeFindBar(): FindBar {
        val bar = FindBar(this, colors, compact = narrow(), object : FindBar.Actions {
            override fun onQuery(query: String) {
                find?.setQuery(query)
            }

            override fun onStep(forward: Boolean) {
                find?.step(forward) { firstShownLine() }
            }

            override fun onClose() = closeFind()
        })
        if (list.id == View.NO_ID) list.id = View.generateViewId()
        bar.textBelow(list.id)
        bar.visibility = View.GONE
        // Over the text's top, the card's border drawn along it
        textCard.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        findBar = bar
        return bar
    }

    /**
     * The text makes room for the bar while it is open (the main app's FindBarRoom): its first lines can scroll clear of
     * the bar, and a line put at the top lands just below it. The text ends at the bar's band, as it would scrolling
     * under it (the list's padding stays clipped: the list then keeps its first line shown in place as rows change).
     */
    private fun showFindRoom(open: Boolean) {
        findBar?.visibility = if (open) View.VISIBLE else View.GONE
        // The line at the top stays shown, below the bar (the main app's content padding moves the text with it)
        keepPlace { list.setPaddingRelative(list.paddingStart, dp(if (open) FIND_BAR_ROOM_DP else TEXT_TOP_DP), list.paddingEnd, list.paddingBottom) }
        // The pane's place to the top keeps the text's line below the bar
        if (pane.shown != null) body.post { sizePane() }
    }

    private fun onFindChange(change: ReaderFind.Change) {
        when (change) {
            ReaderFind.Change.Counter -> updateFindBar()
            ReaderFind.Change.Lit -> {
                adapter.relight()
                pane.relight()
            }
            is ReaderFind.Change.LitLine -> adapter.relightLine(change.lineId)
            is ReaderFind.Change.Step -> {
                adapter.relightLines(change.previous?.lineIndex ?: -1, change.match.lineIndex)
                showMatch(change.match)
            }
        }
    }

    private fun updateFindBar() {
        val f = find ?: return
        findBar?.setCounter(f.counterText(), none = f.shownResults()?.occurrences == 0)
    }

    /**
     * The match ▲ ▼ reached, brought into view (the main app's revealFindMatch): in the middle of what the bar leaves when
     * it is hidden, a readable one left where it is. Its line may be far (read first) or not laid out yet: tried again a
     * few times as it comes.
     */
    private fun showMatch(match: BookFindMatches.Match) {
        revealJob?.cancel()
        adapter.load(match.lineIndex)
        revealJob = scope.launch {
            var jumped = false
            for (i in 0 until REVEAL_TRIES) {
                delay(REVEAL_STEP_MS)
                when (revealMatch(match, onlyIfHidden = !jumped)) {
                    Reveal.Done -> break
                    Reveal.Far, Reveal.Away -> if (!jumped) {
                        jumped = true
                        quietly(match.lineIndex)
                        place(match.lineIndex, 0)
                    }
                    Reveal.NotReady -> Unit
                }
            }
            // The title bar's place is the match's (placeOnScreen), also when it was readable where it was
            updatePlace()
        }
    }

    private enum class Reveal { Done, Far, Away, NotReady }

    private fun revealMatch(match: BookFindMatches.Match, onlyIfHidden: Boolean): Reveal {
        val row = rowAt(match.lineIndex) ?: return Reveal.Away
        val layout = row.layout
        if (row.currentStart < 0 || layout == null || row.isLayoutRequested) return Reveal.NotReady
        val y = row.top + row.totalPaddingTop + layout.getLineTop(layout.getLineForOffset(row.currentStart))
        val room = dp(FIND_BAR_ROOM_DP)
        val end = list.height - dp(FIND_BOTTOM_RESERVE_DP)
        if (onlyIfHidden && y in room..end) return Reveal.Done
        if (onlyIfHidden && (y < -list.height || y > list.height * 2)) return Reveal.Far
        val target = if (end > room) (room + end) / 2 else room
        quietly(match.lineIndex)
        place(match.lineIndex, row.top + (target - y) - list.paddingTop)
        return Reveal.Done
    }

    private fun showKeyboard(field: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // Opened from a menu, the field's window gets the keyboard once the menu's popup has gone
        field.post { if (!imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)) field.postDelayed({ imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }, KEYBOARD_RETRY_MS) }
    }

    private fun hideKeyboard(field: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
    }

    /** The toolbar hides while the find bar's keyboard is up (the main app's), so the text keeps the room it leaves. */
    private fun updateToolbarForKeyboard() {
        if (book == null || toolbar.visibility == View.INVISIBLE) return
        window.decorView.getWindowVisibleDisplayFrame(visibleFrame)
        val keyboard = window.decorView.rootView.height - visibleFrame.bottom > dp(KEYBOARD_MIN_DP)
        val shown = if (keyboard && findBar?.field?.hasFocus() == true) View.GONE else View.VISIBLE
        if (toolbar.visibility != shown) {
            toolbar.visibility = shown
            toolbarLine?.visibility = shown
        }
    }

    private val visibleFrame = Rect()
    private var toolbarLine: View? = null

    // ------------------------------------------------------------------ the toolbar

    /** A toolbar button: its icon, its label, and the label it has in עוד when it overflows there. */
    private class Action(
        val key: String,
        val icon: Int,
        val label: String,
        val menuLabel: String,
        val selected: Boolean = false,
        val dimmed: Boolean = false,
        val levels: Int? = null,
        val perform: () -> Unit,
    )

    private fun actions(): List<Action> = buildList {
        val info = book ?: return@buildList
        val shown = pane.shown
        val snap = available
        // The table of contents is the title bar's ☰, so the first slot (on the right) goes Home.
        add(Action("home", R.drawable.ic_home, "בית", "דף הבית") { goHome() })
        // Faded only when the line is known to have nothing, and never the open pane's (the main app's availability)
        if (info.hasCommentaries) {
            add(Action("commentaries", R.drawable.ic_comment, "מפרשים", "מפרשים", shown == Pane.Commentaries, snap?.hasCommentaries == false && shown != Pane.Commentaries) { togglePane(Pane.Commentaries) })
        }
        if (info.hasTargum) {
            add(Action("targum", R.drawable.ic_translate, "תרגומים", "תרגומים", shown == Pane.Targum, snap?.targumSources?.isEmpty() == true && shown != Pane.Targum) { togglePane(Pane.Targum) })
        }
        if (info.hasSources) {
            add(Action("sources", R.drawable.ic_source, "מקור", "מקור", shown == Pane.Sources, snap?.sources?.isEmpty() == true && shown != Pane.Sources) { togglePane(Pane.Sources) })
        }
        if (info.hasNekudot || info.hasTeamim) {
            val mode = prefs.diacritics
            add(Action("diacritics", R.drawable.ic_text_format, "ניקוד", mode.nextActionLabel(info.hasTeamim), mode != DiacriticsMode.None, levels = if (info.hasTeamim) mode.shownLevel else null) { cycleDiacritics() })
        }
    }

    /** The row's buttons, as many as fit at 58dp each (four to seven), then גודל and עוד (ReaderScreen PhoneToolbar). */
    private fun buildToolbar() {
        toolbar.removeAllViews()
        toolbarButtons.clear()
        val all = actions()
        val slots = (resources.configuration.screenWidthDp / 58).coerceIn(4, 7)
        val shown = all.take(slots - 2)
        overflowActions = all.drop(slots - 2)
        shown.forEach { a ->
            val button = RailButton(this, colors, a.icon, a.label) { actions().firstOrNull { it.key == a.key }?.perform?.invoke() }
            toolbarButtons[a.key] = button
            toolbar.addView(button, lp(0, WRAP, 1f))
        }
        toolbar.addView(RailButton(this, colors, R.drawable.ic_zoom_in, "גודל") { zoomMenu() }.also { zoomButton = it }, lp(0, WRAP, 1f))
        toolbar.addView(RailButton(this, colors, R.drawable.ic_more_horiz, "עוד") { moreMenu() }.also { moreButton = it }, lp(0, WRAP, 1f))
        toolbar.visibility = View.VISIBLE
        updateToolbar()
    }

    private var zoomButton: View? = null
    private var moreButton: View? = null

    private fun updateToolbar() {
        val all = actions()
        all.forEach { a ->
            toolbarButtons[a.key]?.apply {
                setState(a.selected, a.dimmed, a.levels)
                contentDescription = a.menuLabel
            }
        }
        overflowActions = all.filter { it.key !in toolbarButtons }
    }

    private fun zoomMenu() {
        val anchor = zoomButton ?: return
        Dialogs.popup(anchor, colors, listOf(
            MenuEntry("הגדל", R.drawable.ic_zoom_in) { zoom(+1) },
            MenuEntry("הקטן", R.drawable.ic_zoom_out) { zoom(-1) },
        ))
    }

    private fun moreMenu() {
        val anchor = moreButton ?: return
        Dialogs.popup(anchor, colors, moreEntries())
    }

    /** One step larger or smaller (the main app's zoom step, 2). */
    private fun zoom(direction: Int) {
        val before = prefs.textSize
        prefs.textSize = before + direction * Prefs.TEXT_SIZE_STEP
        if (prefs.textSize == before) return
        style = null
        redrawLines(clear = false)
        pane.restyle()
    }

    /**
     * The lines drawn again (another size, font or marks; [clear] for the marks, which their texts are drawn with). A
     * keypad's line keeps its place on the screen and stays the keypad's: setSelection would move it to the first line
     * shown, and an open pane after it. Else the first line shown stays first.
     */
    private fun redrawLines(clear: Boolean) = keepPlace {
        if (clear) adapter.clearRendered() else adapter.notifyDataSetChanged()
    }

    /**
     * Runs [change] (the rows rebuilt, the text's top padding changed) keeping the text in place: a keypad's line where
     * it is on the screen and still the keypad's (setSelection would move it to the first line shown, and an open pane
     * after it), else the line at the text's top edge as far below it.
     */
    private inline fun keepPlace(change: () -> Unit) {
        val first = list.firstVisiblePosition
        val keypad = if (list.isInTouchMode) -1 else list.selectedItemPosition
        val keypadRow = list.getChildAt(keypad - first)
        val edge = (0 until list.childCount).firstOrNull { list.getChildAt(it).bottom > list.paddingTop }
        val anchor = if (keypadRow != null) keypad else edge?.let { first + it }
        val below = (keypadRow ?: edge?.let { list.getChildAt(it) })?.let { it.top - list.paddingTop }
        change()
        if (anchor == null || below == null) return
        if (keypadRow == null) quietly(anchor)
        place(anchor, below)
    }

    /** Out of touch mode the list selects the line it is put at ([place]): no pane follows line [index] there. */
    private fun quietly(index: Int) {
        if (!list.isInTouchMode) noFollow = index
    }

    /**
     * Puts line [index] [below] px under the text's top padding. The list measures from the padding it had when last
     * measured (setSelectionFromTop): just after the padding changed (the find bar opened), from the new one.
     */
    private fun place(index: Int, below: Int) {
        list.setSelectionFromTop(index, below + list.paddingTop - list.listPaddingTop)
    }

    /** The ניקוד button's step: all marks, the niqqud alone (when the book has cantillation), none. */
    private fun cycleDiacritics() {
        val info = book ?: return
        val next = prefs.diacritics.next(info.hasTeamim)
        prefs.diacritics = next
        marks = next
        adapter.clearRendered()
        pane.restyle()
        updateToolbar()
        toast(next.label)
    }

    // ------------------------------------------------------------------ the place shown

    /**
     * The title bar's second line: the book's category › the heading of the place shown (as the main app), and the
     * headings above it for עוד's path. [settle]: after a moment's pause (the main app's PLACE_SETTLE_MS), as a keypad
     * held down passes many lines, each a read of the library; a line marked is named at once (its debounce of 0).
     */
    private fun updatePlace(settle: Boolean = true) {
        val info = book ?: return
        placeIndex = list.firstVisiblePosition
        placeJob?.cancel()
        placeJob = scope.launch {
            if (settle) delay(PLACE_DELAY_MS)
            val index = placeOnScreen()
            val known = adapter.lineAt(index)?.id
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val lineId = known ?: opened.repository.getLines(bookId, index, index).firstOrNull()?.id
                    val toc = lineId?.let { opened.repository.getTocEntryIdForLine(it) }
                    toc?.let { opened.repository.getAncestorPath(it) }.orEmpty().map { it.text }
                }.getOrNull()
            } ?: emptyList()
            placePath = path
            namedLine = index
            heading = path.lastOrNull()?.takeIf { it.isNotBlank() && it != info.title } ?: ""
            bar.setSubtitle(listOfNotNull(categoryTitle, heading.ifEmpty { null }).joinToString(" › "))
        }
    }

    /**
     * The line the reader stands at (the main app's placeOnScreen): a keypad's line, the one it is about (this
     * edition's); else the line marked while some of it shows, else the find's match while it shows, else the first line
     * mostly below the text's top (below the find bar while it is open).
     */
    private fun placeOnScreen(): Int {
        val top = list.paddingTop
        fun shows(index: Int): Boolean = rowAt(index)?.let { it.bottom > top && it.top < list.height } == true
        val keypad = keypadLine()
        if (keypad >= 0 && shows(keypad)) return keypad
        if (selected >= 0 && shows(selected)) return selected
        find?.current?.takeIf { find?.open == true && shows(it.lineIndex) }?.let { return it.lineIndex }
        return topLine()
    }

    /**
     * The first line whose bottom is below the text's top edge (the main app's firstVisibleItemIndex under its content
     * padding): where ▲ ▼ start before a match is reached, a line half under the find bar included.
     */
    private fun firstShownLine(): Int {
        val top = list.paddingTop
        for (i in 0 until list.childCount) if (list.getChildAt(i).bottom > top) return list.firstVisiblePosition + i
        return list.firstVisiblePosition
    }

    /** The first line mostly below the text's top edge (below the find bar while it is open). */
    private fun topLine(): Int {
        val top = list.paddingTop
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i)
            if (row.bottom <= top) continue
            // More than half of it above the edge: the next one, when there is one
            if (top - row.top > row.height / 2 && i + 1 < list.childCount) return list.firstVisiblePosition + i + 1
            return list.firstVisiblePosition + i
        }
        return list.firstVisiblePosition
    }

    /**
     * The lines shown that a fling brought in, justified one at a time, a message each: together they held the screen a
     * tenth to a quarter of a second on the Android 4.4 AVD. Another fling stops it.
     */
    private fun justifyShown() {
        list.removeCallbacks(justifyNext)
        list.post(justifyNext)
    }

    private val justifyNext: Runnable = object : Runnable {
        override fun run() {
            if (skimming) return
            for (i in 0 until list.childCount) {
                val row = list.getChildAt(i) as? LineView ?: continue
                if (row.justify()) {
                    list.post(this)
                    return
                }
            }
        }
    }

    /** The row of line [index] on screen, if it is. */
    private fun rowAt(index: Int): LineView? = list.getChildAt(index - list.firstVisiblePosition) as? LineView

    // ------------------------------------------------------------------ the marked line and the pane

    /** Marks the line at [index] (its bar), which the pane then shows the links of. */
    private fun mark(index: Int, follow: Boolean = true) {
        followJob?.cancel()
        selected = index
        refreshBars()
        // The line marked names the place while it shows (placeOnScreen), at once as the main app follows a tap
        updatePlace(settle = false)
        available = null
        updateToolbar()
        val line = adapter.lineAt(index)
        // Not read yet (a place far from the text shown, a page let go since): read now, and its arrival comes back.
        if (line != null) loadAvailability(line) else adapter.load(index)
        if (follow) pane.refresh()
    }

    /** The bars of the lines shown, without rebuilding them (which would move a keypad's place in the list). */
    private fun refreshBars() {
        for (i in 0 until list.childCount) {
            val view = list.getChildAt(i) as? LineView ?: continue
            view.bar = view.index == selected
        }
    }

    /** What the marked line links to: the toolbar fades the buttons it has nothing for. */
    private fun loadAvailability(line: Line) {
        availableJob?.cancel()
        availableJob = scope.launch {
            val snap = withContext(Dispatchers.IO) { runCatching { connections.of(line.id) }.getOrNull() }
            if (selected == line.lineIndex) {
                available = snap
                updateToolbar()
            }
        }
    }

    /** A toolbar button's pane: opened, or closed when it is the one shown; with nothing for the line, it says so. */
    private fun togglePane(p: Pane) {
        if (book == null) return
        if (pane.shown == p) {
            closePane()
            return
        }
        val snap = available
        val none = when (p) {
            Pane.Commentaries -> snap?.hasCommentaries == false
            Pane.Targum -> snap?.targumSources?.isEmpty() == true
            Pane.Sources -> snap?.sources?.isEmpty() == true
            Pane.Details -> false
        }
        if (none) {
            toast(
                when (p) {
                    Pane.Commentaries -> "אין מפרשים זמינים לפסקה זו"
                    Pane.Targum -> "אין תרגומים זמינים לשורה זו"
                    else -> "אין מקורות זמינים לשורה זו"
                },
            )
            return
        }
        showPane(p)
    }

    private fun showPane(p: Pane) {
        // A keypad's place is the line it is about; else no line marked: the first one shown.
        val keypad = keypadLine()
        if (keypad >= 0 && keypad != selected && p != Pane.Details) {
            mark(keypad, follow = false)
        } else if (selected < 0 && p != Pane.Details) {
            mark(topLine(), follow = false)
        }
        pane.show(p)
        updateToolbar()
    }

    private fun closePane() {
        // A follow on its way would still mark the line passed through.
        followJob?.cancel()
        pane.show(null)
        updateToolbar()
        if (!list.isInTouchMode) list.requestFocus()
    }

    /** The strip above the pane: a drag moves the pane, a tap (OK) opens it to the top or back to half. */
    private fun paneHandle(): View {
        val frame = FrameLayout(this)
        frame.addView(View(this).apply { background = roundRect(colors.disabled, dp(2).toFloat()) }, FrameLayout.LayoutParams(dp(32), dp(4), Gravity.CENTER))
        frame.contentDescription = "גודל החלונית"
        keyable(frame) {
            expanded = !expanded
            sizePane()
        }
        // ▲ here, the pane's top, goes back to the text on its line: found by a search upwards, the list would take the
        // line nearest the pane instead, and the pane would follow it.
        frame.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_DPAD_UP) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_DOWN) list.requestFocus()
            true
        }
        frame.background = states(colors, 0, 0f)
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startY = 0f
        var startHeight = 0
        var dragging = false
        frame.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY
                    startHeight = pane.root.height
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - startY
                    if (!dragging && abs(dy) > slop) dragging = true
                    // Down to nothing, as the main app's: a floor would keep it above the height that closes it.
                    if (dragging) setPaneHeight((startHeight - dy).toInt().coerceIn(0, maxPaneHeight()))
                }
                MotionEvent.ACTION_UP -> if (dragging) {
                    settlePane()
                } else {
                    frame.performClick()
                }
                MotionEvent.ACTION_CANCEL -> if (dragging) settlePane()
            }
            true
        }
        return frame
    }

    /** The two places of the pane (ReaderScreen PhoneBody): half the reader, or up to a line of text above it. */
    private fun halfPaneHeight(): Int = maxOf(body.height / 2, dp(PHONE_PANE_MIN_DP)).coerceAtMost(maxPaneHeight())

    /** Below the find bar's room while it is open: finding is about the text (the main app's textMin). */
    private fun maxPaneHeight(): Int {
        val text = dp(PHONE_TEXT_MIN_DP) + if (find?.open == true) dp(FIND_BAR_ROOM_DP) else 0
        return maxOf(dp(PANE_FULL_MIN_DP), body.height - dp(PANE_HANDLE_DP) - text - dp(12))
    }

    private fun sizePane() {
        if (pane.shown == null || body.height == 0) return
        setPaneHeight(if (expanded) maxPaneHeight() else halfPaneHeight())
    }

    private fun setPaneHeight(height: Int) {
        val params = pane.root.layoutParams as LinearLayout.LayoutParams
        if (params.height == height && params.weight == 0f) return
        params.height = height
        params.weight = 0f
        pane.root.layoutParams = params
    }

    /** Where a drag left the pane: closed below [PANE_CLOSE_AT] of half, else the nearer of its two places. */
    private fun settlePane() {
        val h = pane.root.height
        val half = halfPaneHeight()
        val full = maxPaneHeight()
        when {
            h < half * PANE_CLOSE_AT -> closePane()
            else -> {
                expanded = h > (half + full) / 2
                sizePane()
            }
        }
    }

    private enum class Pane(val title: String) {
        Commentaries("מפרשים"),
        Targum("תרגומים"),
        Sources("מקורות"),
        Details("פרטי הספר"),
    }

    /**
     * The pane under the text (the main app's phone pane): its tabs (the kinds of links the book has, and פרטי הספר)
     * and ✕, then the commentaries (chips for the commentators, one shown at a time, its name over its text), the
     * targumim or the sources (each under its book's name), or the book's details.
     */
    private inner class PaneView {
        val root = LinearLayout(this@BookActivity).apply {
            orientation = LinearLayout.VERTICAL
            background = island(colors)
        }
        private val tabs = LinearLayout(this@BookActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        private val tabsScroll = HorizontalScrollView(this@BookActivity).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(tabs, FrameLayout.LayoutParams(WRAP, MATCH))
        }
        private val close = iconButton(R.drawable.ic_close, colors.secondary, "סגור", colors) { closePane() }
        private val content = FrameLayout(this@BookActivity)
        var shown: Pane? = null
            private set

        /** The commentators the book's pane shows (the main app's sticky choice), and the one shown now. */
        var sticky: Set<Long> = emptySet()
        private var commentator: Long? = null

        /** The pane's reads (the line's links, their texts, the details): all let go when it shows something else. */
        private var loads = newLoads()
        private var spinnerJob: Job? = null
        private var scrollView: ScrollView? = null
        private var details: SearchEntity.BookEntity? = null
        private var detailsView: View? = null

        /** The tab the tabs were drawn for (drawn again only for another), and its view: a keypad's way into the pane. */
        private var tabsFor: Pane? = null
        private var chosenTab: View? = null

        /** The commentaries' chips and texts, kept while the chips stay the same; the line's links, for סנן. */
        private var commentaries: CommentariesFrame? = null
        private var links: LineConnectionsSnapshot? = null

        init {
            val row = LinearLayout(this@BookActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(44)
                setPaddingRelative(dp(4), 0, 0, 0)
            }
            row.addView(tabsScroll, lp(0, dp(44), 1f))
            row.addView(close)
            root.addView(row, lp(MATCH, WRAP))
            root.addView(content, lp(MATCH, 0, 1f))
        }

        fun show(p: Pane?) {
            shown = p
            root.visibility = if (p == null) View.GONE else View.VISIBLE
            handle.visibility = root.visibility
            if (p == null) {
                cancelLoads()
                content.removeAllViews()
                scrollView = null
                return
            }
            sizePane()
            // Not laid out yet (the pane restored with the screen): sized once it is.
            body.post { sizePane() }
            refresh()
        }

        /** A keypad into the pane (◀ or ▶ on the text): on its chosen tab, then ▼ its content and the toolbar under it. */
        fun focus() {
            chosenTab?.requestFocus()
        }

        private fun newLoads() = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

        private fun cancelLoads() {
            loads.cancel()
            loads = newLoads()
        }

        private fun kinds(): List<Pane> = buildList {
            val info = book
            if (info?.hasCommentaries == true) add(Pane.Commentaries)
            if (info?.hasTargum == true) add(Pane.Targum)
            if (info?.hasSources == true) add(Pane.Sources)
            add(Pane.Details)
        }

        /**
         * PhonePaneTabs: the tabs keep their roomy look when they all fit; else tighter steps (padding, gaps, then the
         * text a size smaller) make them fit; narrower still, the row scrolls, the chosen tab kept in view.
         */
        private fun drawTabs() {
            tabsFor = shown
            // A keypad on the tabs (OK on another one): its focus waits on ✕, then goes to the new chosen tab.
            val focused = tabs.hasFocus()
            if (focused) close.requestFocus()
            tabs.removeAllViews()
            val kinds = kinds()
            val room = resources.displayMetrics.widthPixels - dp(8) - dp(4) - dp(44)
            val paint = android.text.TextPaint()
            val step = TAB_STEPS.firstOrNull { (size, padding, gap) ->
                paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, resources.displayMetrics)
                paint.typeface = Typeface.DEFAULT_BOLD
                val text = kinds.sumOf { paint.measureText(it.title).toDouble() }
                text + dp(padding) * 2 * kinds.size + dp(gap) * (kinds.size - 1) <= room
            } ?: TAB_STEPS.last()
            var chosen: View? = null
            kinds.forEach { p ->
                val on = p == shown
                val tab = label(p.title, step.first, if (on) colors.text else colors.secondary, bold = on, maxLines = 1).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(36)
                    setPadding(dp(step.second), dp(8), dp(step.second), dp(8))
                    background = states(colors, if (on) colors.selected else 0, dp(8).toFloat())
                    keyable(this) { if (p != shown) showPane(p) }
                }
                if (on) chosen = tab
                tabs.addView(tab, lp(WRAP, WRAP).apply { marginEnd = dp(step.third) })
            }
            chosenTab = chosen
            if (focused) chosen?.requestFocus()
            reveal(tabsScroll, tabs, chosen)
        }

        /** Shows the pane's content again: another line marked, another tab, another commentator. */
        fun refresh() {
            val p = shown ?: return
            // The tabs change only with the tab: drawn again for each line, they would slow a keypad's every step.
            if (tabsFor != p) drawTabs()
            cancelLoads()
            if (p == Pane.Details) {
                showDetails()
                return
            }
            if (selected < 0) {
                hint(
                    when (p) {
                        Pane.Commentaries -> "בחר שורה כדי לצפות במפרשים שלה"
                        Pane.Targum -> "בחר שורה כדי לצפות בתרגומים שלה"
                        else -> "בחר שורה כדי לצפות במקורות"
                    },
                )
                return
            }
            loadingSoon()
            val line = adapter.lineAt(selected)
            if (line == null) {
                // Not read yet (a jump far away, a page let go since): read now, and onLineLoaded comes back here.
                adapter.load(selected)
                return
            }
            loads.launch {
                val found = withContext(Dispatchers.IO) {
                    runCatching {
                        val snap = connections.of(line.id)
                        val any = when (p) {
                            Pane.Commentaries -> snap.hasCommentaries
                            Pane.Targum -> snap.targumSources.isNotEmpty()
                            else -> snap.sources.isNotEmpty()
                        }
                        // The lines the texts are read for, once for all the books: a heading stands for its section.
                        val ids = if (!any) {
                            emptyList()
                        } else {
                            runCatching { readerLinks.sectionOrLine(line.id, windowed = p == Pane.Commentaries) }.getOrDefault(listOf(line.id))
                        }
                        snap to ids
                    }.getOrNull()
                }
                spinnerJob?.cancel()
                if (found == null) {
                    hint("לא ניתן לקרוא את הקישורים של השורה")
                    return@launch
                }
                val (snap, ids) = found
                when (p) {
                    Pane.Commentaries -> showCommentaries(snap, ids)
                    Pane.Targum -> showLinks(ids, snap.targumSources, ConnectionType.TARGUM, "אין תרגומים זמינים לשורה זו")
                    Pane.Sources -> showLinks(ids, snap.sources, ConnectionType.SOURCE, "אין מקורות זמינים לשורה זו")
                    Pane.Details -> Unit
                }
            }
        }

        fun onLineLoaded(index: Int) {
            if (index == selected && shown != null && shown != Pane.Details) refresh()
        }

        /** The size or the marks changed: drawn again. */
        fun restyle() {
            if (shown != null) refresh()
        }

        fun scroll(up: Boolean) {
            val view = scrollView ?: return
            val step = view.height - dp(24)
            view.smoothScrollBy(0, if (up) -step else step)
        }

        /** 4 and 6 on a keypad: the commentator chip to the left (the next, right to left) or to the right. */
        fun stepCommentator(towardsLeft: Boolean) {
            val items = commentaries?.takeIf { it.root.parent === content }?.items ?: return
            if (shown != Pane.Commentaries || items.isEmpty()) return
            val i = items.indexOfFirst { it.bookId == commentator }
            val next = (i + if (towardsLeft) 1 else -1).coerceIn(0, items.size - 1)
            if (next == i) return
            commentator = items[next].bookId
            refresh()
        }

        /**
         * The pane's content replaced. A keypad's focus in the old one waits on the chosen tab, then goes to [focus] in
         * the new one: removed with its view, it would jump to the title bar's ☰.
         */
        private fun setContent(view: View, scroll: ScrollView?, focus: View? = null) {
            spinnerJob?.cancel()
            val focused = content.hasFocus()
            if (focused) chosenTab?.requestFocus()
            content.removeAllViews()
            content.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
            scrollView = scroll
            if (focused) focus?.requestFocus()
        }

        private fun loading() = setContent(centered(spinner(colors)), null)

        /** The spinner, once a read takes a moment: a quick one keeps the content until its own comes, built once. */
        private fun loadingSoon() {
            spinnerJob = loads.launch {
                delay(SPINNER_DELAY_MS)
                loading()
            }
        }

        /** EmptyHint: a centred grey line. */
        private fun hint(text: String) = setContent(
            centered(label(text, 15f, colors.secondary).apply {
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(16), dp(24), dp(16))
            }),
            null,
        )

        /** The focus on [scroll] itself, as ScrollView keeps it when its focused view scrolls away: ▲▼ scroll on from it. */
        private fun holdFocus(scroll: ScrollView) {
            val was = scroll.descendantFocusability
            scroll.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            scroll.requestFocus()
            scroll.descendantFocusability = was
        }

        private fun paneSize(): Float = prefs.textSize * PANE_TEXT_SCALE

        private fun showCommentaries(snap: LineConnectionsSnapshot, ids: List<Long>) {
            val all = snap.commentatorGroups.flatMap { it.commentators }
            if (all.isEmpty()) return hint("אין מפרשים זמינים לפסקה זו")
            links = snap
            // The book's chosen commentators the line has; none of them, all it has (a keypad has no quick filter).
            val list = all.filter { it.bookId in sticky }.ifEmpty { all }
            if (list.none { it.bookId == commentator }) commentator = list.first().bookId
            val current = list.first { it.bookId == commentator }
            // The same chips as the line before (the next line, another commentator): kept, and a keypad's focus on them.
            val kept = commentaries?.takeIf { it.root.parent === content && it.items == list }
            val frame = kept ?: CommentariesFrame(list, current.bookId).also {
                commentaries = it
                setContent(it.root, it.scroll, focus = it.chosenChip())
                it.reveal()
            }
            frame.choose(current.bookId)
            val texts = frame.newTexts()
            val header = commentatorHeader(current.name)
            texts.addView(header, lp(MATCH, WRAP))
            loadTexts(ids, current.bookId, ConnectionType.COMMENTARY, texts, header)
        }

        /** The commentaries: סנן and a chip for each commentator of [items], then the chosen one's texts under them. */
        private inner class CommentariesFrame(val items: List<CommentatorItem>, private var chosen: Long) {
            val root = LinearLayout(this@BookActivity).apply { orientation = LinearLayout.VERTICAL }
            val scroll = ScrollView(this@BookActivity)
            private val chips = LinearLayout(this@BookActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            private val chipsScroll = HorizontalScrollView(this@BookActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(chips)
            }

            init {
                chips.addView(chip("סנן", colors, false, R.drawable.ic_filter) { links?.let { filter(it) } }, chipParams())
                items.forEach { chips.addView(chipOf(it), chipParams()) }
                root.addView(chipsScroll, lp(MATCH, WRAP))
                root.addView(scroll, lp(MATCH, 0, 1f))
            }

            /** The chosen commentator's chip, after סנן. */
            fun chosenChip(): View? = chips.getChildAt(1 + items.indexOfFirst { it.bookId == chosen })

            fun reveal() = reveal(chipsScroll, chips, chosenChip())

            /** [id] chosen: its chip and the one chosen before are drawn again in their places, the others kept. */
            fun choose(id: Long) {
                if (id == chosen) return
                val before = chosen
                chosen = id
                redraw(before)
                redraw(id)
                reveal()
            }

            private fun redraw(id: Long) {
                val i = items.indexOfFirst { it.bookId == id }
                if (i < 0) return
                val old = chips.getChildAt(i + 1)
                val view = chipOf(items[i])
                chips.addView(view, i + 1, chipParams())
                // A keypad's focus goes to the new chip before the old one leaves (with it, it would jump to the title bar).
                if (old.hasFocus()) view.requestFocus()
                chips.removeView(old)
            }

            /** A new list for the texts, at the top (a read still on its way for the old one adds to that one, unseen). */
            fun newTexts(): LinearLayout {
                // A keypad's focus there (a name, עוד, the list itself) waits on the chosen chip.
                if (scroll.hasFocus()) chosenChip()?.requestFocus()
                val texts = LinearLayout(this@BookActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, 0, dp(16))
                }
                scroll.removeAllViews()
                scroll.addView(texts)
                scroll.scrollTo(0, 0)
                return texts
            }

            private fun chipOf(c: CommentatorItem): View = chip(c.name, colors, c.bookId == chosen) {
                commentator = c.bookId
                refresh()
            }

            private fun chipParams() = lp(WRAP, WRAP).apply { marginEnd = dp(6) }
        }

        /** The targumim or the sources of the line: each book's name, then its texts. */
        private fun showLinks(ids: List<Long>, books: Map<String, Long>, type: ConnectionType, none: String) {
            if (books.isEmpty()) return hint(none)
            val texts = LinearLayout(this@BookActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(16))
            }
            val scroll = ScrollView(this@BookActivity).apply { addView(texts) }
            setContent(scroll, scroll)
            books.forEach { (name, id) ->
                val header = commentatorHeader(name)
                texts.addView(header, lp(MATCH, WRAP))
                val section = LinearLayout(this@BookActivity).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(section, lp(MATCH, WRAP))
                loadTexts(ids, id, type, section, header)
            }
        }

        /** CommentatorHeader: the name, bold and centred, a tenth larger than the pane's text; it opens the book there. */
        private fun commentatorHeader(name: String): TextView =
            label(name, paneSize() * 1.1f, colors.text, bold = true, maxLines = 2).apply {
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(8), dp(6), dp(8))
                background = states(colors, 0, dp(6).toFloat())
            }

        /** One book's texts for the lines [ids], a page at a time ("עוד" reads the next one). */
        private fun loadTexts(ids: List<Long>, target: Long, type: ConnectionType, into: LinearLayout, header: View, offset: Int = 0) {
            val waiting = spinner(colors)
            into.addView(waiting, lp(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
            loads.launch {
                val page = withContext(Dispatchers.IO) {
                    readerLinks.linksPageOrNull(ids, type, target, offset, LINKS_PAGE)
                }
                into.removeView(waiting)
                // A page that cannot be read says so, the first or one after "עוד": none would end the list as if complete.
                if (page == null) {
                    into.addView(paneNote("לא ניתן לקרוא את הטקסט"))
                    return@launch
                }
                if (offset == 0) {
                    page.firstOrNull()?.let { first ->
                        keyable(header) { start(this@BookActivity, first.link.targetBookId, first.link.targetLineIndex) }
                    }
                    if (page.isEmpty()) into.addView(paneNote("אין טקסט לשורה זו"))
                }
                page.forEach { into.addView(linkText(it, type)) }
                if (page.size == LINKS_PAGE) {
                    into.addView(label("עוד", 15f, colors.accent, bold = true).apply {
                        gravity = Gravity.CENTER
                        minHeight = dp(44)
                        background = states(colors, 0, dp(8).toFloat())
                        keyable(this) {
                            // A keypad's focus stays on the list, which scrolls on to the texts coming in its place.
                            if (hasFocus()) scrollView?.let { holdFocus(it) }
                            into.removeView(this)
                            loadTexts(ids, target, type, into, header, offset + LINKS_PAGE)
                        }
                    }, lp(MATCH, WRAP))
                }
            }
        }

        /** A link's text (LinkItem): the commentary's font, or the targum's with its cantillation, at the pane's size. */
        private fun linkText(item: CommentaryWithText, type: ConnectionType): TextView = PaneText(SpannedString(LineText.render(item.targetText, prefs.diacritics, colors.accent))).apply {
            typeface = if (type == ConnectionType.TARGUM) Fonts.targum(this@BookActivity) else Fonts.commentary(this@BookActivity)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, paneSize())
            setTextColor(colors.text)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            setPaddingRelative(dp(12), 0, dp(12), 0)
            trimmedBlock(LINE_HEIGHT, dp(6), dp(6))
            light()
        }

        /** The find's query lit again in the texts shown (the main app's panes light the bar's query). */
        fun relight() = relight(content)

        private fun relight(view: View) {
            when (view) {
                is PaneText -> view.light()
                is ViewGroup -> for (i in 0 until view.childCount) relight(view.getChildAt(i))
            }
        }

        private fun paneNote(text: String): TextView = label(text, 14f, colors.secondary).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        /** סנן: which of the line's commentators the pane shows, for this book from now on (CommentatorChecklist). */
        private fun filter(snap: LineConnectionsSnapshot) {
            val all = snap.commentatorGroups.flatMap { it.commentators }
            // The book's choice the line has; with none of it the pane shows all the line has, which the list starts with.
            val had = all.filter { it.bookId in sticky }.map { it.bookId }.toSet()
            val before = had.ifEmpty { all.map { it.bookId }.toSet() }
            val chosen = HashSet(before)
            val rows = LinearLayout(this@BookActivity).apply { orientation = LinearLayout.VERTICAL }
            snap.commentatorGroups.forEachIndexed { i, group ->
                if (group.commentators.isEmpty()) return@forEachIndexed
                if (snap.commentatorGroups.size > 1 && group.label.isNotBlank()) {
                    if (i > 0) rows.addView(divider(colors))
                    rows.addView(label(group.label, 13f, colors.text, bold = true).apply { setPadding(dp(10), dp(8), dp(10), dp(2)) })
                }
                group.commentators.forEach { c ->
                    rows.addView(toggleRow(c.name, null, colors, c.bookId in chosen) { on -> if (on) chosen += c.bookId else chosen -= c.bookId })
                }
            }
            Dialogs.show(this@BookActivity, colors, "מפרשים", rows, listOf("אישור" to {
                // The book's choice moves as the line's did (ReaderViewModel updateSelectedCommentators), from what it had
                // of the line's: from all of them, what is left unchecked would not be in it to leave.
                if (chosen != before) {
                    sticky = ReaderLinks.stickyAfter(sticky, had, chosen)
                    prefs.setCommentators(bookId, sticky)
                    refresh()
                }
            }, "ביטול" to {}))
        }

        /** פרטי הספר: the title, its categories, authors, main parts (to their line), editions and licence. */
        private fun showDetails() {
            // The book's, whatever the line: drawn once (a part opened keeps a keypad's focus on it).
            if (detailsView?.parent === content) return
            val known = details
            if (known == null) {
                loadingSoon()
                loads.launch {
                    val loaded = withContext(Dispatchers.IO) { BookDetailsLoader(opened.repository).load(bookId) }
                    if (loaded == null) {
                        hint("לא ניתן לקרוא את פרטי הספר")
                    } else {
                        details = loaded
                        showDetails()
                    }
                }
                return
            }
            val box = LinearLayout(this@BookActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            // BookDetailsPane: the title bold in the book's font, each author with their era and years, the parts from the start
            box.addView(label(known.book.title, 22f, colors.text, bold = true, typeface = Fonts.book(this@BookActivity)))
            if (known.categories.isNotEmpty()) box.addView(label(known.categories.joinToString(" › "), 12f, colors.secondary).apply { setPadding(0, dp(4), 0, 0) })
            known.authors.forEach { author ->
                section(box, "מחבר")
                box.addView(label(AuthorNames.display(author.name), 14f, colors.text).apply { setPadding(0, dp(4), 0, dp(2)) })
                val facts = listOfNotNull(author.era?.let { AUTHOR_ERAS[it] }, authorYears(author))
                if (facts.isNotEmpty()) box.addView(label(facts.joinToString(" · "), 12f, colors.secondary).apply { setPadding(0, 0, 0, dp(4)) })
            }
            if (known.parts.isNotEmpty()) {
                section(box, "חלקי הספר")
                val parts = FlowRow(this@BookActivity, dp(14), dp(2), centered = false)
                known.parts.forEach { part ->
                    parts.addView(label(part.title, 14f, colors.accent).apply {
                        setPadding(dp(2), dp(6), dp(2), dp(6))
                        background = states(colors, 0, dp(4).toFloat())
                        keyable(this) { openLine(part.lineId) }
                    })
                }
                box.addView(parts, lp(MATCH, WRAP))
            }
            val editions = known.editions.distinctBy { (it.heTitle ?: it.title) to it.licenseCode }
            if (editions.isNotEmpty() || known.fromOtzaria) {
                section(box, "מקור ורישיון")
                editions.forEach { edition ->
                    val url = edition.sourceUrl
                    box.addView(label(edition.heTitle ?: edition.title, 13f, if (url != null) colors.accent else colors.text).apply {
                        setPadding(0, dp(4), 0, 0)
                        if (url != null) {
                            background = states(colors, 0, dp(4).toFloat())
                            keyable(this) { openUrl(url) }
                        }
                    })
                    box.addView(label(licenseLabel(edition.licenseCode), 12f, colors.secondary))
                }
                if (known.fromOtzaria) {
                    box.addView(label(OTZARIA_CREDIT, 12f, colors.secondary).apply {
                        setPadding(0, dp(6), 0, 0)
                        background = states(colors, 0, dp(4).toFloat())
                        keyable(this) { openUrl(OTZARIA_URL) }
                    })
                }
            }
            val scroll = ScrollView(this@BookActivity).apply { addView(box) }
            detailsView = scroll
            setContent(scroll, scroll)
        }

        private fun section(box: LinearLayout, title: String) {
            box.addView(divider(colors).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(0, dp(10), 0, dp(6)) })
            box.addView(label(title, 12f, colors.secondary, bold = true))
        }
    }

    private fun openLine(lineId: Long) {
        scope.launch {
            val index = withContext(Dispatchers.IO) { runCatching { opened.lineIndex(lineId) }.getOrNull() } ?: return@launch
            jumpTo(index)
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            toast(url)
        }
    }

    /**
     * Scrolls a row of tabs or chips to the chosen one. Android 4.4's HorizontalScrollView starts at the left whatever
     * the direction, which in a right-to-left row is its end.
     */
    private fun reveal(scroll: HorizontalScrollView, row: LinearLayout, chosen: View?) {
        row.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                @Suppress("DEPRECATION")
                row.viewTreeObserver.removeGlobalOnLayoutListener(this)
                val max = maxOf(0, row.width - scroll.width)
                val target = if (chosen == null) max else (chosen.left + chosen.right) / 2 - scroll.width / 2
                scroll.scrollTo(target.coerceIn(0, max), 0)
            }
        })
    }

    /**
     * A text of the pane: [base], with the find's query lit in it as the bar has it, justified (Justify; the platform's
     * from Android 8.0). A text with links is a keypad's stop too, with the focus's ground: OK lists its links, which a
     * keypad cannot tap (as a line's long OK lists the line's).
     */
    private inner class PaneText(private val base: Spanned) : TextView(this@BookActivity) {
        private var shown: CharSequence = base
        private var gapsFor: Layout? = null
        private var gaps: Justify.Gaps? = null
        private var gapsWidth = -1
        private val hasLinks = base.getSpans(0, base.length, LineText.Link::class.java).isNotEmpty()

        init {
            Justify.prepare(this)
            show(base)
            if (hasLinks) {
                // Not clickable: a tap keeps going to the link under it, or to nothing
                isFocusable = true
                background = states(colors, 0, dp(6).toFloat())
            }
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
            if (hasLinks && isConfirm(keyCode)) true else super.onKeyDown(keyCode, event)

        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
            if (!hasLinks || !isConfirm(keyCode)) return super.onKeyUp(keyCode, event)
            if (!event.isCanceled) Dialogs.choices(this@BookActivity, colors, null, linkEntries(base))
            return true
        }

        private fun isConfirm(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER

        fun light() {
            val query = find?.paneQuery.orEmpty()
            val ranges = if (query.isEmpty()) emptyList() else findAllMatchesOriginal(base.toString(), query)
            show(
                if (ranges.isEmpty()) {
                    base
                } else {
                    val b = SpannableStringBuilder(base)
                    ranges.forEach { b.setSpan(BackgroundColorSpan(hitColor), it.first, it.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                    SpannedString(b)
                },
            )
        }

        private fun show(text: CharSequence) {
            shown = text
            gapsFor = null
            if (Justify.byPlatform) setText(text) else setText(text, BufferType.SPANNABLE)
            justify()
        }

        /** The gaps that justify the text, worked out from its own layout, kept for its width (laid out again, from its text). */
        private fun justify() {
            val layout = layout ?: return
            if (Justify.byPlatform || layout === gapsFor) return
            if (gapsFor != null) {
                gapsFor = null
                setText(shown, BufferType.SPANNABLE)
                return
            }
            gapsFor = layout
            if (gapsWidth != layout.width) {
                gaps = Justify.lines(layout)?.let { Justify.gaps(base, paint, it) }
                gapsWidth = layout.width
            }
            gaps?.let { known -> (text as? Spannable)?.let { Justify.apply(this, it, known) } }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            justify()
        }

        override fun onDraw(canvas: Canvas) {
            justify()
            super.onDraw(canvas)
        }

        private val links = LinkTaps(this)

        override fun onTouchEvent(event: MotionEvent): Boolean = links.onTouch(event) || super.onTouchEvent(event)
    }

    /** An alternative structure's heading's room: [above] its first line (the top level's 4dp) and [below] its last. */
    private class HeadingSpacing(private val above: Int, private val below: Int) : LineHeightSpan {
        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fm: Paint.FontMetricsInt) {
            val spanned = text as? Spanned ?: return
            if (start == spanned.getSpanStart(this)) {
                fm.ascent -= above
                fm.top -= above
            }
            if (end == spanned.getSpanEnd(this)) {
                fm.descent += below
                fm.bottom += below
            }
        }
    }

    /**
     * The preferred alternative structure's headings (the main app's loadAltStructures: דף, then פרשה, chapters, topic,
     * section), by the line they stand above; the lines shown are drawn again with them.
     */
    private fun loadAltHeadings(info: BookInfo) {
        if (!info.hasAltStructures) return
        scope.launch {
            val headings = withContext(Dispatchers.IO) {
                runCatching {
                    val repository = opened.repository
                    val structures = repository.getAltTocStructuresForBook(info.id)
                    val preferred = structures.minByOrNull { altPriority(it.key) } ?: return@runCatching emptyMap()
                    repository.getAltTocEntriesForStructure(preferred.id).filter { it.lineId != null }.groupBy { it.lineId!! }
                }.getOrNull()
            }
            if (headings.isNullOrEmpty()) return@launch
            altHeadings = headings
            redrawLines(clear = true)
        }
    }

    private fun altPriority(key: String): Int = when (key.lowercase()) {
        "daf" -> 0
        "parasha" -> 1
        "chapters" -> 2
        "topic" -> 3
        "section" -> 4
        else -> 100
    }

    // ------------------------------------------------------------------ the lines

    /** The book text's look for a size and font, worked out once: the main app's line height, 1.65 times the size. */
    private class LineStyle(val size: Float, val typeface: Typeface)

    private fun lineStyle(): LineStyle {
        val current = style
        val font = Fonts.book(this)
        if (current != null && current.size == prefs.textSize.toFloat() && current.typeface === font) return current
        return LineStyle(prefs.textSize.toFloat(), font).also { style = it }
    }

    /**
     * A line as drawn, before what the find lights: the alternative structure's headings, then the line's own text from
     * [bodyStart]; and the gaps that justify it, once worked out for a width and a look (the rows' own layout).
     */
    private class Rendered(val text: Spanned, val bodyStart: Int) {
        var gaps: Justify.Gaps? = null
        var gapsWidth = -1
        var gapsStyle: LineStyle? = null
    }

    /** Whether [r]'s gaps were worked out for a layout [width] px wide in [style]. */
    private fun gapsKnown(r: Rendered, width: Int, style: LineStyle?): Boolean = r.gapsWidth == width && r.gapsStyle === style

    /** A line with what the find lights in it, and where the current match starts in it (-1 for none). */
    private class Lit(val text: CharSequence, val currentStart: Int)

    /**
     * A line of the book: its text, and the accent's bar at its start when it is the line marked (selectionBar), from
     * the line's own text down (its headings stand above the bar, as the main app's).
     */
    private inner class LineView : TextView(this@BookActivity) {
        var index = -1
        var lineStyle: LineStyle? = null
        var bar = false
            set(value) {
                if (field != value) {
                    field = value
                    invalidate()
                }
            }
        private val barPaint = Paint().apply { color = colors.accent }

        /** What it shows ([show]): the line, its text with what the find lights, and the layout its gaps are set for. */
        var rendered: Rendered? = null
            private set
        var currentStart = -1
            private set
        private var shown: CharSequence = ""
        private var gapsFor: Layout? = null

        init {
            setTextColor(colors.text)
            // Hebrew lines are right to left; a line of Latin letters only keeps its own direction.
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            gravity = Gravity.START
            setPaddingRelative(dp(BAR_SLOT_DP + 8), 0, 0, 0)
            Justify.prepare(this)
        }

        /** [text], the line [r] (null: a line not read yet) with what the find lights; [current]: its match's start. */
        fun show(r: Rendered?, text: CharSequence, current: Int) {
            rendered = r
            currentStart = current
            shown = text
            gapsFor = null
            // Below Android 8.0 the text is the view's own Spannable, which the gaps that justify it go on
            if (Justify.byPlatform || r == null) setText(text) else setText(text, BufferType.SPANNABLE)
            justify()
        }

        /**
         * The gaps that justify the line (Justify), on its text once laid out: worked out from this layout the first time
         * and kept with the line for its width and look; not while a fling or the fast scroller's thumb passes it
         * ([skimming]: the lines it shows are justified when it stops). Laid out again with them (another width), it
         * starts from its text. False when there was nothing to do.
         */
        fun justify(): Boolean {
            val r = rendered ?: return false
            val layout = layout ?: return false
            if (Justify.byPlatform || layout === gapsFor || skimming) return false
            if (gapsFor != null) {
                gapsFor = null
                setText(shown, BufferType.SPANNABLE)
                return true
            }
            gapsFor = layout
            val style = lineStyle
            if (!gapsKnown(r, layout.width, style)) {
                r.gaps = Justify.lines(layout)?.let { Justify.gaps(r.text, paint, it, r.bodyStart) }
                r.gapsWidth = layout.width
                r.gapsStyle = style
            }
            r.gaps?.let { gaps -> (text as? Spannable)?.let { Justify.apply(this, it, gaps) } }
            return true
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            justify()
        }

        private val links = LinkTaps(this)

        override fun onTouchEvent(event: MotionEvent): Boolean = links.onTouch(event) || super.onTouchEvent(event)

        fun applyStyle(style: LineStyle) {
            lineStyle = style
            typeface = style.typeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.size)
            // LineVerticalPadding: 5dp above and below the line's text.
            trimmedBlock(LINE_HEIGHT, dp(5), dp(5))
        }

        override fun onDraw(canvas: Canvas) {
            justify()
            val layout = layout
            val r = rendered
            super.onDraw(canvas)
            if (!bar) return
            val slot = dp(BAR_SLOT_DP).toFloat()
            val w = dp(BAR_WIDTH_DP).toFloat()
            val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - slot + (slot - w) / 2 else (slot - w) / 2
            val top = if (r != null && r.bodyStart > 0 && layout != null) {
                (totalPaddingTop + layout.getLineTop(layout.getLineForOffset(r.bodyStart)) - dp(BAR_ABOVE_TEXT_DP)).toFloat().coerceAtLeast(0f)
            } else {
                0f
            }
            canvas.drawRect(x, top, x + w, height.toFloat(), barPaint)
        }
    }

    /** The book's lines, read [PAGE] at a time as they come into view, a few hundred kept. */
    private inner class LinesAdapter : BaseAdapter() {
        var total = 0
            set(value) {
                field = value
                notifyDataSetChanged()
            }
        private val pages = object : LinkedHashMap<Int, List<Line>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<Line>>): Boolean = size > MAX_PAGES
        }
        private val rendered = object : LinkedHashMap<Int, Rendered>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Rendered>): Boolean = size > MAX_RENDERED
        }

        /** The lines shown with what the find lights (null: nothing in them), until it lights something else. */
        private val lit = object : LinkedHashMap<Int, Lit?>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Lit?>): Boolean = size > MAX_LIT
        }

        /** The pages asked for and not read yet, the newest last; the one being read; what reads them, one at a time. */
        private val wanted = ArrayList<Int>()
        private var reading = -1
        private var reader: Job? = null

        fun lineAt(index: Int): Line? = pages[index / PAGE]?.getOrNull(index % PAGE)

        /** Line [index] as drawn, when it is (its links, for the long press). */
        fun renderedAt(index: Int): Spanned? = rendered[index]?.text

        /** Reads the page of the line at [index] (the line marked, away from the text shown), unless it is read or asked for. */
        fun load(index: Int) {
            if (index in 0 until total) request(index / PAGE)
        }

        fun clearRendered() {
            rendered.clear()
            lit.clear()
            find?.forgetRanges()
            notifyDataSetChanged()
        }

        /** What the find lights changed: the lines shown are drawn again with it (their rendering kept). */
        fun relight() {
            lit.clear()
            rebind { true }
        }

        /** Lines [a] and [b] (the match left and the one reached) drawn again. */
        fun relightLines(a: Int, b: Int) {
            lit.remove(a)
            lit.remove(b)
            rebind { it == a || it == b }
        }

        /** The line [lineId], whose search highlighting is now known, drawn again. */
        fun relightLine(lineId: Long) {
            rebind { i -> (lineAt(i)?.id == lineId).also { if (it) lit.remove(i) } }
        }

        /** The rows shown whose line [which] picks, given their text again without being rebuilt (a keypad's place kept). */
        private inline fun rebind(which: (Int) -> Boolean) {
            for (i in 0 until list.childCount) {
                val view = list.getChildAt(i) as? LineView ?: continue
                val line = lineAt(view.index) ?: continue
                if (which(view.index)) bind(view, view.index, line)
            }
        }

        override fun getCount(): Int = total

        override fun getItem(position: Int): Any? = lineAt(position)

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView as? LineView ?: LineView()
            val style = lineStyle()
            if (view.lineStyle !== style) view.applyStyle(style)
            view.index = position
            view.bar = position == selected
            val line = lineAt(position)
            if (line == null) {
                // Its page is on its way: an empty line of the text's height meanwhile.
                view.show(null, "", -1)
                view.minHeight = view.lineHeight + dp(10)
                request(position / PAGE)
            } else {
                view.minHeight = 0
                bind(view, position, line)
            }
            return view
        }

        /** [line]'s text in [view]: rendered once, with what the find lights. */
        private fun bind(view: LineView, position: Int, line: Line) {
            val r = rendered[position] ?: render(line).also {
                rendered[position] = it
                lit.remove(position)
            }
            val shown = if (lit.containsKey(position)) lit[position] else lightLine(position, line, r)
            view.show(r, shown?.text ?: r.text, shown?.currentStart ?: -1)
        }

        /**
         * The line as drawn (LineText), with the headings the preferred alternative structure puts above it (AltHeading:
         * bold, a quarter larger for the top level and an eighth for the next, 4dp under them and 4dp over the top
         * level's).
         */
        private fun render(line: Line): Rendered {
            val body = LineText.render(line.content, prefs.diacritics, colors.accent)
            val headings = altHeadings[line.id]
            val text: Spanned
            var bodyStart = 0
            if (headings.isNullOrEmpty()) {
                text = body
            } else {
                val b = SpannableStringBuilder()
                for (h in headings) {
                    val start = b.length
                    b.append(h.text).append('\n')
                    b.setSpan(StyleSpan(Typeface.BOLD), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val size = when (h.level) {
                        0 -> 1.25f
                        1 -> 1.125f
                        else -> 1f
                    }
                    if (size != 1f) b.setSpan(RelativeSizeSpan(size), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    b.setSpan(HeadingSpacing(if (h.level == 0) dp(4) else 0, dp(4)), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                bodyStart = b.length
                b.append(body)
                text = b
            }
            return Rendered(SpannedString(text), bodyStart)
        }

        /**
         * What the find lights in [line]: the literal query's matches, the current one in its own colour (the main app's
         * highlightAnnotatedWithCurrent), or, the bar holding a search result's query, the search's highlighting of the
         * lines it counts, all of the current line's in the current colour. Null for nothing to light; a line whose
         * search highlighting is still being read is lit when it comes (ReaderFind.Change.LitLine).
         */
        private fun lightLine(position: Int, line: Line, r: Rendered): Lit? {
            val f = find ?: return null
            val smart = f.lightsSearch
            val query = f.textQuery
            if (!smart && query.isEmpty()) return null
            val text = r.text
            val body = text.subSequence(r.bodyStart, text.length).toString()
            val current = f.current?.takeIf { it.lineId == line.id }
            val ranges = if (smart) {
                val lines = f.smartLines()
                if (lines != null && !lines.hasLine(position, line.id)) {
                    lit[position] = null
                    return null
                }
                f.searchRanges(line.id, body) ?: return null
            } else {
                findAllMatchesOriginal(body, query)
            }
            if (ranges.isEmpty()) {
                lit[position] = null
                return null
            }
            val b = SpannableStringBuilder(text)
            ranges.forEachIndexed { i, range ->
                val on = current != null && (smart || i == current.ordinal)
                b.setSpan(BackgroundColorSpan(if (on) CURRENT_FIND_MATCH else hitColor), r.bodyStart + range.first, r.bodyStart + range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val start = current?.let { c -> (if (smart) ranges.first() else ranges.getOrNull(c.ordinal))?.first?.plus(r.bodyStart) } ?: -1
            return Lit(SpannedString(b), start).also { lit[position] = it }
        }

        /**
         * The pages are read one at a time, the newest asked for first, and one no longer shown is let go (unless it has
         * the line marked): the library is read under one lock, and the pages a fast-scroll drag passes over would hold
         * up the one it stops on.
         */
        private fun request(page: Int) {
            if (page == reading || pages.containsKey(page)) return
            wanted.remove(page)
            wanted.add(page)
            if (reader?.isActive != true) reader = scope.launch { readPages() }
        }

        private suspend fun readPages() {
            while (wanted.isNotEmpty()) {
                val page = wanted.removeAt(wanted.size - 1)
                val start = page * PAGE
                if (pages.containsKey(page) || !(onScreen(page) || selected in start until start + PAGE)) continue
                reading = page
                val lines = try {
                    withContext(Dispatchers.IO) { runCatching { opened.repository.getLines(bookId, start, start + PAGE - 1) }.getOrNull() }
                } finally {
                    reading = -1
                }
                if (lines == null) continue
                pages[page] = lines
                // The rows are drawn again only for a page shown: each one is a new text layout.
                if (onScreen(page)) notifyDataSetChanged()
                if (selected in start until start + PAGE) {
                    lineAt(selected)?.let { if (available == null) loadAvailability(it) }
                    pane.onLineLoaded(selected)
                }
            }
        }

        private fun onScreen(page: Int): Boolean {
            val start = page * PAGE
            return start <= list.lastVisiblePosition && start + PAGE > list.firstVisiblePosition
        }
    }

    companion object {
        const val EXTRA_BOOK = "book"
        const val EXTRA_LINE = "line"
        const val EXTRA_TITLE = "title"
        const val EXTRA_FIND = "find"
        const val EXTRA_FIND_WORDS = "find_words"
        private const val STATE_TOP = "top"
        private const val STATE_SELECTED = "selected"
        private const val STATE_PANE = "pane"
        private const val STATE_EXPANDED = "expanded"
        private const val STATE_KEYPAD = "keypad"
        private const val STATE_KEYPAD_TOP = "keypad_top"
        private const val STATE_FIND_OPEN = "find_open"
        private const val STATE_FIND_QUERY = "find_query"
        private const val REQUEST_TOC = 1
        private const val PAGE = 50
        private const val MAX_PAGES = 40
        private const val MAX_RENDERED = 300
        private const val MAX_LIT = 128
        private const val LINKS_PAGE = 25
        private const val FOLLOW_DELAY_MS = 300L

        /** The title bar's place, read once the text has stopped this long; the pane's spinner, once a read takes this long. */
        private const val PLACE_DELAY_MS = 150L
        private const val SPINNER_DELAY_MS = 150L

        /** The title bar's find button and its menu item (the main app's FindInPage). */
        private const val FIND_IN_PAGE = "חפש בתוך העמוד"

        /** The current find match's ground, apart from the others as in Chrome (the main app's CurrentFindMatchColor). */
        private const val CURRENT_FIND_MATCH = 0xCCFF9632.toInt()

        /**
         * The text's top padding, and the find bar's room in its place while the bar is open (FindBarRoom: 6 + 48 + 6dp);
         * a match above it or under the last 48dp of the text (FindBottomReserve) is not readable.
         */
        private const val TEXT_TOP_DP = 6
        private const val FIND_BAR_ROOM_DP = 60
        private const val FIND_BOTTOM_RESERVE_DP = 48

        /** How long a match's line is waited for (read, laid out) before it is brought into view, step by step. */
        private const val REVEAL_TRIES = 60
        private const val REVEAL_STEP_MS = 50L

        /** A keyboard not shown at once (a menu's popup still going) is asked for again after this. */
        private const val KEYBOARD_RETRY_MS = 300L

        /** The window's bottom covered by more than this is the keyboard's. */
        private const val KEYBOARD_MIN_DP = 120

        /** READER_LINE_HEIGHT: the lines of the text and of the pane, 1.65 times their size apart. */
        private const val LINE_HEIGHT = 1.65f

        /** paneTextSize: the pane's text, seven eighths of the book's. */
        private const val PANE_TEXT_SCALE = 0.875f

        /** SelectionBarWidth (the bar's slot) and the bar of the line marked. */
        private const val BAR_SLOT_DP = 4
        private const val BAR_WIDTH_DP = 4

        /** The bar of a line with headings starts this far above its own text, under them. */
        private const val BAR_ABOVE_TEXT_DP = 3

        /** ReaderScreen: the pane's handle, the text's line above a pane opened to the top, the half pane's least. */
        private const val PANE_HANDLE_DP = 24
        private const val PHONE_TEXT_MIN_DP = 44
        private const val PHONE_PANE_MIN_DP = 170

        /** The least a pane opened to the top is (a short screen, in landscape). */
        private const val PANE_FULL_MIN_DP = 60

        /** Dragged and let go under this part of its half height, the pane closes (the main app's PANE_CLOSE_AT). */
        private const val PANE_CLOSE_AT = 0.6f

        /** PaneTabSteps: the tabs' text size, the padding inside a tab and the gap between two. */
        private val TAB_STEPS = listOf(Triple(14f, 12, 4), Triple(14f, 8, 2), Triple(13f, 6, 0), Triple(12f, 5, 0))

        private val SHORTCUT_KEYS = setOf(
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND,
        )

        /** The reader's keys, as the menu's מקשי קיצור lists them. */
        private val SHORTCUTS = listOf(
            "▲ ▼" to "השורה הקודמת או הבאה",
            "◀" to "העמוד הבא",
            "▶" to "העמוד הקודם",
            "◀ ▶" to "כשהחלונית פתוחה: מעבר אליה, ומשם ▼ לסרגל הכלים (▲ בראשה: בחזרה לטקסט)",
            "OK" to "סימון השורה (לחיצה ארוכה: פעולות)",
            "1" to "תוכן העניינים",
            "2 / 8" to "גלילה למעלה או למטה (של החלונית, כשהיא פתוחה)",
            "3" to "מפרשים",
            "4 / 6" to "המפרש הבא או הקודם",
            "5" to "ניקוד",
            "7 / 9" to "הקטנת הטקסט או הגדלתו",
            "0" to "דף הבית",
            "*" to "חפש בתוך העמוד (OK או ▼: התוצאה הבאה, ▲: הקודמת)",
            "#" to "הוספה למועדפים או הסרה מהם",
            "תפריט" to "עוד: התפריט של הספר",
            "חזרה" to "סגירת החיפוש, בחזרה לטקסט, סגירת החלונית, ואז יציאה מהספר",
        )

        /**
         * Opens a book at [lineIndex] (or where it was left); [find], a search's query, opens it with the find bar holding
         * it, lit as the search found it (the engine's, else the scan's words, or its phrase unless [findWords]).
         */
        fun start(context: Context, bookId: Long, lineIndex: Int?, title: String? = null, find: String? = null, findWords: Boolean = false) {
            context.startActivity(
                Intent(context, BookActivity::class.java)
                    .putExtra(EXTRA_BOOK, bookId)
                    .putExtra(EXTRA_LINE, lineIndex ?: -1)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_FIND, find)
                    .putExtra(EXTRA_FIND_WORDS, findWords),
            )
        }
    }
}

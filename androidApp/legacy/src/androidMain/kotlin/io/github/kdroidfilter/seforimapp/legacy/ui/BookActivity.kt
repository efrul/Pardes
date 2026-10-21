package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.search.BookDetailsLoader
import io.github.kdroidfilter.seforimapp.android.search.OTZARIA_CREDIT
import io.github.kdroidfilter.seforimapp.android.search.OTZARIA_URL
import io.github.kdroidfilter.seforimapp.android.search.SearchEntity
import io.github.kdroidfilter.seforimapp.android.search.licenseLabel
import io.github.kdroidfilter.seforimapp.android.ui.reader.CommentatorItem
import io.github.kdroidfilter.seforimapp.android.ui.reader.DiacriticsMode
import io.github.kdroidfilter.seforimapp.android.ui.reader.LineConnections
import io.github.kdroidfilter.seforimapp.android.ui.reader.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.android.ui.reader.ReaderLinks
import io.github.kdroidfilter.seforimapp.android.ui.reader.nextActionLabel
import io.github.kdroidfilter.seforimapp.android.ui.shell.bookShareLink
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.BookInfo
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.Place
import io.github.kdroidfilter.seforimapp.legacy.data.Prefs
import io.github.kdroidfilter.seforimapp.legacy.data.label
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
 * into view, the line marked by the accent's bar at its start, and the bottom toolbar with the buttons the book has
 * (בית, מפרשים, תרגומים, מקור, ניקוד, then גודל and עוד, the rest overflowing into עוד). מפרשים, תרגומים and מקור open
 * the pane under the text, an Island with its tabs, which follows the line marked.
 *
 * On a keypad (Android 4.4 phones with keys): ▲▼ go from line to line, and the pane follows; OK marks the line; ◀▶ turn
 * a page, ◀ forward as a Hebrew book's pages turn, or, with the pane open, go into it (its tabs, then ▼ its content and
 * the toolbar under it; ▲ at its top, or Back, come back to the text); Menu opens the menu; Back closes the pane; the
 * digits are shortcuts ([SHORTCUTS], listed in the menu).
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
    private var highlight: Regex? = null
    private var hitColor = 0
    private var placeJob: Job? = null
    private var followJob: Job? = null
    private var availableJob: Job? = null
    private var categoryTitle: String? = null
    private var heading: String = ""

    /** The first line shown that the heading was asked for, and whether a finger is scrolling the text. */
    private var placeIndex = -1
    private var scrolling = false

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
        highlight = intent.getStringExtra(EXTRA_FIND)?.let { LineText.highlighter(it, phrase = !intent.getBooleanExtra(EXTRA_FIND_WORDS, false)) }
        marks = prefs.diacritics

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_menu, "תוכן עניינים") { openToc() }
            title.text = intent.getStringExtra(EXTRA_TITLE) ?: ""
            // Very narrow phones: find moves into the menu so the title stays readable (as the main app).
            if (resources.configuration.screenWidthDp >= 300) action(R.drawable.ic_find_in_page, "חיפוש בספר") { findInBook() }
            overflow { menu(withApp = true) }
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))

        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCard = FrameLayout(this).apply { background = island(colors) }
        list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            cacheColorHint = colors.panel
            isVerticalFadingEdgeEnabled = false
            val r = dp(6).toFloat()
            selector = states(ColorDrawable(0), roundRect(colors.pressed, r), roundRect(colors.accentTint, r))
            isFastScrollEnabled = true
            verticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT
            setPaddingRelative(dp(8), dp(6), dp(12), dp(6))
            adapter = this@BookActivity.adapter
            setOnItemClickListener { _, _, position, _ -> mark(position) }
            setOnItemLongClickListener { _, _, position, _ ->
                lineActions(position)
                true
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                // A keypad moved to another line: the pane, when open, follows it after a moment.
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    // Also when it came back to the line marked: a follow still on its way would mark the line passed.
                    followJob?.cancel()
                    if (pane.shown == null || pane.shown == Pane.Details || position == selected) return
                    followJob = scope.launch {
                        delay(FOLLOW_DELAY_MS)
                        mark(position)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
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
        root.addView(View(this).apply { setBackgroundColor(colors.border) }, lp(MATCH, maxOf(1, dp(1))))
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colors.panel)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            // Shown once the book is read and its buttons known (as the main app): בית is the way out meanwhile.
            visibility = View.INVISIBLE
        }
        root.addView(toolbar, lp(MATCH, WRAP))
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
                    Loaded(lib, info, sticky, total, lib.categoryTitle(info.categoryId))
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
                categoryTitle = loaded.category
                connections = LineConnections(loaded.library.repository) { loaded.info.title }
                readerLinks = ReaderLinks(loaded.library.repository)
                bar.title.text = loaded.info.title
                buildToolbar()
                adapter.total = loaded.total
                val keypad = keypadAtStart?.takeIf { (line, _) -> !list.isInTouchMode && line < loaded.total }
                if (keypad != null) {
                    // setSelection would put a keypad's line on the first line shown, and an open pane would follow it.
                    list.setSelectionFromTop(keypad.first, keypad.second)
                } else {
                    list.setSelection(startLine.coerceIn(0, maxOf(0, loaded.total - 1)))
                }
                list.post { updatePlace() }
                if (selected >= 0) mark(selected, follow = false)
                if (paneAtStart != null) showPane(paneAtStart)
                // A keypad starts on the text, at the line asked for.
                if (!list.isInTouchMode) list.requestFocus()
            }
        }
    }

    private class Loaded(val library: Library.Opened, val info: BookInfo, val sticky: Set<Long>, val total: Int, val category: String?)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TOP, list.firstVisiblePosition)
        outState.putInt(STATE_SELECTED, selected)
        outState.putBoolean(STATE_EXPANDED, expanded)
        pane.shown?.let { outState.putString(STATE_PANE, it.name) }
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
        prefs.rememberPlace(Place(info.id, info.title, list.firstVisiblePosition, heading))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val line = data.getIntExtra(EXTRA_LINE, -1)
        if (requestCode == REQUEST_FIND) {
            highlight = data.getStringExtra(EXTRA_FIND)?.let { LineText.highlighter(it, phrase = !data.getBooleanExtra(EXTRA_FIND_WORDS, false)) }
            adapter.clearRendered()
        }
        if (line >= 0) jumpTo(line)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
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
        list.setSelection(index)
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
            KeyEvent.KEYCODE_STAR -> findInBook()
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

    /** The ⋮ menu (and the keypad's Menu key): the book's menu, then the app's, as the main app's two menus. */
    private fun menu(withApp: Boolean): List<MenuEntry> = buildList {
        val info = book ?: return@buildList
        add(MenuEntry("חיפוש בספר", R.drawable.ic_find_in_page) { findInBook() })
        overflowActions.forEach { a -> add(MenuEntry(a.menuLabel, a.icon) { a.perform() }) }
        add(MenuEntry("תוכן עניינים", R.drawable.ic_menu) { openToc() })
        add(MenuEntry("פרטי הספר", R.drawable.ic_info) { togglePane(Pane.Details) })
        add(MenuEntry("רשימת הספרים", R.drawable.ic_library) { LibraryActivity.start(this@BookActivity, info.categoryId) })
        add(MenuEntry("העתק קישור", R.drawable.ic_link) { copyLink(placeLine()) })
        if (prefs.isBookmarked(info.id, placeLine())) {
            add(MenuEntry("הסר מהמועדפים", R.drawable.ic_star) { toggleFavorite() })
        } else {
            add(MenuEntry("הוסף למועדפים", R.drawable.ic_star_border) { toggleFavorite() })
        }
        if (hasKeys()) add(MenuEntry("מקשי קיצור", R.drawable.ic_info) { showShortcuts() })
        if (withApp) {
            add(MenuEntry("פרדס"))
            add(MenuEntry("דף הבית", R.drawable.ic_home) { goHome() })
            add(MenuEntry("היסטוריה", R.drawable.ic_history) { PlacesDialogs.recents(this@BookActivity, colors) })
            add(MenuEntry("מועדפים", R.drawable.ic_star_border) { PlacesDialogs.favorites(this@BookActivity, colors) })
            add(MenuEntry(if (prefs.dark) "ערכת נושא: כהה" else "ערכת נושא: בהירה", if (prefs.dark) R.drawable.ic_dark_mode else R.drawable.ic_light_mode) {
                prefs.dark = !prefs.dark
                recreate()
            })
            add(MenuEntry("הגדרות", R.drawable.ic_settings) { startActivity(Intent(this@BookActivity, SettingsActivity::class.java)) })
        }
    }

    private fun openToc() {
        val info = book ?: return
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, TocActivity::class.java).putExtra(EXTRA_BOOK, info.id).putExtra(EXTRA_TITLE, info.title)
                .putExtra(EXTRA_LINE, placeLine()),
            REQUEST_TOC,
        )
    }

    private fun findInBook() {
        val info = book ?: return
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, SearchActivity::class.java).putExtra(SearchActivity.EXTRA_BOOK_SCOPE, info.id).putExtra(EXTRA_TITLE, info.title),
            REQUEST_FIND,
        )
    }

    private fun goHome() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    /** The line the menu's actions are about: a keypad's place, else the one marked, else the first one shown. */
    private fun placeLine(): Int = keypadLine().takeIf { it >= 0 } ?: if (selected >= 0) selected else list.firstVisiblePosition

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

    private fun copyLink(index: Int) {
        val line = adapter.lineAt(index)
        if (line != null) return copyLinkTo(line.id)
        // Its page not read (the line marked, far from the text shown): its id is read first.
        scope.launch {
            val id = withContext(Dispatchers.IO) { runCatching { opened.repository.getLines(bookId, index, index).firstOrNull()?.id }.getOrNull() }
            if (id != null) copyLinkTo(id)
        }
    }

    private fun copyLinkTo(lineId: Long) {
        copy(bookShareLink(bookId, lineId))
        toast("הקישור הועתק")
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
        }
        Dialogs.choices(this, colors, null, entries)
    }

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
        if (info.hasCommentaries) {
            add(Action("commentaries", R.drawable.ic_comment, "מפרשים", "מפרשים", shown == Pane.Commentaries, snap?.hasCommentaries == false) { togglePane(Pane.Commentaries) })
        }
        if (info.hasTargum) {
            add(Action("targum", R.drawable.ic_translate, "תרגומים", "תרגומים", shown == Pane.Targum, snap?.targumSources?.isEmpty() == true) { togglePane(Pane.Targum) })
        }
        if (info.hasSources) {
            add(Action("sources", R.drawable.ic_source, "מקור", "מקור", shown == Pane.Sources, snap?.sources?.isEmpty() == true) { togglePane(Pane.Sources) })
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
        Dialogs.popup(anchor, colors, menu(withApp = false))
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
    private fun redrawLines(clear: Boolean) {
        val first = list.firstVisiblePosition
        val keypad = if (list.isInTouchMode) -1 else list.selectedItemPosition
        val keypadRow = list.getChildAt(keypad - first)
        if (clear) adapter.clearRendered() else adapter.notifyDataSetChanged()
        if (keypadRow != null) list.setSelectionFromTop(keypad, keypadRow.top - list.paddingTop) else list.setSelection(first)
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

    /** The title bar's second line: the book's category › the heading of the first line shown (as the main app). */
    private fun updatePlace() {
        val info = book ?: return
        val index = list.firstVisiblePosition
        placeIndex = index
        placeJob?.cancel()
        placeJob = scope.launch {
            // A moment's pause first: a keypad held down passes many lines, each a read of the library.
            delay(PLACE_DELAY_MS)
            val text = withContext(Dispatchers.IO) { runCatching { opened.heading(bookId, index) }.getOrNull() ?: "" }
            heading = text.takeIf { it.isNotBlank() && it != info.title } ?: ""
            bar.setSubtitle(listOfNotNull(categoryTitle, heading.ifEmpty { null }).joinToString(" › "))
        }
    }

    // ------------------------------------------------------------------ the marked line and the pane

    /** Marks the line at [index] (its bar), which the pane then shows the links of. */
    private fun mark(index: Int, follow: Boolean = true) {
        followJob?.cancel()
        selected = index
        refreshBars()
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
            mark(list.firstVisiblePosition, follow = false)
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

    private fun maxPaneHeight(): Int = maxOf(dp(PANE_FULL_MIN_DP), body.height - dp(PANE_HANDLE_DP) - dp(PHONE_TEXT_MIN_DP) - dp(12))

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
        private fun linkText(item: CommentaryWithText, type: ConnectionType): TextView = TextView(this@BookActivity).apply {
            text = LineText.render(item.targetText, prefs.diacritics).also { LineText.highlight(it, highlight, hitColor) }
            typeface = if (type == ConnectionType.TARGUM) Fonts.targum(this@BookActivity) else Fonts.commentary(this@BookActivity)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, paneSize())
            setTextColor(colors.text)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            if (Build.VERSION.SDK_INT >= 26) justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
            setPaddingRelative(dp(12), 0, dp(12), 0)
            trimmedBlock(LINE_HEIGHT, dp(6), dp(6))
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
                setPadding(dp(16), dp(8), dp(16), dp(16))
            }
            box.addView(label(known.book.title, 22f, colors.text, typeface = Fonts.book(this@BookActivity)))
            if (known.categories.isNotEmpty()) box.addView(label(known.categories.joinToString(" › "), 12f, colors.secondary).apply { setPadding(0, dp(4), 0, 0) })
            known.authors.map { AuthorNames.display(it.name) }.forEach { name ->
                section(box, "מחבר")
                box.addView(label(name, 14f, colors.text).apply { setPadding(0, dp(4), 0, dp(4)) })
            }
            if (known.parts.isNotEmpty()) {
                section(box, "חלקי הספר")
                val parts = FlowRow(this@BookActivity, dp(14), dp(2))
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

    // ------------------------------------------------------------------ the lines

    /** The book text's look for a size and font, worked out once: the main app's line height, 1.65 times the size. */
    private class LineStyle(val size: Float, val typeface: Typeface)

    private fun lineStyle(): LineStyle {
        val current = style
        val font = Fonts.book(this)
        if (current != null && current.size == prefs.textSize.toFloat() && current.typeface === font) return current
        return LineStyle(prefs.textSize.toFloat(), font).also { style = it }
    }

    /** A line of the book: its text, and the accent's bar at its start when it is the line marked (selectionBar). */
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

        init {
            setTextColor(colors.text)
            // Hebrew lines are right to left; a line of Latin letters only keeps its own direction.
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            gravity = Gravity.START
            setPaddingRelative(dp(BAR_SLOT_DP + 8), 0, 0, 0)
            if (Build.VERSION.SDK_INT >= 26) justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
        }

        fun applyStyle(style: LineStyle) {
            lineStyle = style
            typeface = style.typeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.size)
            // LineVerticalPadding: 5dp above and below the line's text.
            trimmedBlock(LINE_HEIGHT, dp(5), dp(5))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!bar) return
            val slot = dp(BAR_SLOT_DP).toFloat()
            val w = dp(BAR_WIDTH_DP).toFloat()
            val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - slot + (slot - w) / 2 else (slot - w) / 2
            canvas.drawRect(x, 0f, x + w, height.toFloat(), barPaint)
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
        private val rendered = object : LinkedHashMap<Int, CharSequence>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CharSequence>): Boolean = size > MAX_RENDERED
        }

        /** The pages asked for and not read yet, the newest last; the one being read; what reads them, one at a time. */
        private val wanted = ArrayList<Int>()
        private var reading = -1
        private var reader: Job? = null

        fun lineAt(index: Int): Line? = pages[index / PAGE]?.getOrNull(index % PAGE)

        /** Reads the page of the line at [index] (the line marked, away from the text shown), unless it is read or asked for. */
        fun load(index: Int) {
            if (index in 0 until total) request(index / PAGE)
        }

        fun clearRendered() {
            rendered.clear()
            notifyDataSetChanged()
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
                view.text = ""
                view.minHeight = view.lineHeight + dp(10)
                request(position / PAGE)
            } else {
                view.minHeight = 0
                view.text = rendered.getOrPut(position) {
                    LineText.render(line.content, prefs.diacritics).also { text -> LineText.highlight(text, highlight, hitColor) }
                }
            }
            return view
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
        private const val REQUEST_TOC = 1
        private const val REQUEST_FIND = 2
        private const val PAGE = 50
        private const val MAX_PAGES = 40
        private const val MAX_RENDERED = 300
        private const val LINKS_PAGE = 25
        private const val FOLLOW_DELAY_MS = 300L

        /** The title bar's place, read once the text has stopped this long; the pane's spinner, once a read takes this long. */
        private const val PLACE_DELAY_MS = 150L
        private const val SPINNER_DELAY_MS = 150L

        /** READER_LINE_HEIGHT: the lines of the text and of the pane, 1.65 times their size apart. */
        private const val LINE_HEIGHT = 1.65f

        /** paneTextSize: the pane's text, seven eighths of the book's. */
        private const val PANE_TEXT_SCALE = 0.875f

        /** SelectionBarWidth (the bar's slot) and the bar of the line marked. */
        private const val BAR_SLOT_DP = 4
        private const val BAR_WIDTH_DP = 4

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
            "*" to "חיפוש בספר",
            "#" to "הוספה למועדפים או הסרה מהם",
            "תפריט" to "התפריט",
            "חזרה" to "בחזרה לטקסט, סגירת החלונית, ואז יציאה מהספר",
        )

        /** Opens a book at [lineIndex] (or where it was left), marking [find] as the search found it. */
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

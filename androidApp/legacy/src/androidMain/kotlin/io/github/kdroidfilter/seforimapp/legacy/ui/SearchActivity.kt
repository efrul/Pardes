package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.ui.reader.DiacriticsMode
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceResolver
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.RepositoryReferenceSource
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Entry
import io.github.kdroidfilter.seforimapp.legacy.data.Hit
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.NoLibraryException
import io.github.kdroidfilter.seforimapp.legacy.data.TextSearch
import io.github.kdroidfilter.seforimapp.legacy.db.SqliteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The search, as the main app's one smart bar and its results page (UnifiedSearchBar, SearchScreen): as one types,
 * the places it names (ברכות דף ב, בראשית א ג), the books, and "חיפוש במאגר"; Enter takes the first. The text search
 * reads the base books first, as the main app's does, and all the books when they have nothing or on "חיפוש בכל
 * הספרים"; its results come grouped by book ("עוד N תוצאות מ…"), with the categories they are in as chips. A book
 * picked (the reader's find, or "חיפוש בתוך") sits in the field, and the search is in it; a result in the book the
 * search was opened from goes back to it.
 *
 * The main app searches the desktop's Lucene index, which Android 4.4 cannot run: here the books are read one after
 * the other (TextSearch), so the whole library takes minutes on an old phone, and the base books seconds. The scan
 * holds the library's one connection a book at a time: it pauses while a book opened from here is read, and goes on
 * from where it was when the search is back.
 */
class SearchActivity : BaseActivity() {
    private lateinit var opened: Library.Opened
    private lateinit var resolver: ReferenceResolver
    private lateinit var field: EditText
    private lateinit var clear: View
    private lateinit var bookChip: TextView
    private lateinit var tabs: LinearLayout
    private lateinit var tabsScroll: HorizontalScrollView
    private lateinit var statusRow: View
    private lateinit var status: TextView
    private lateinit var switchLink: TextView
    private lateinit var stop: View
    private lateinit var progress: ProgressBar
    private lateinit var list: ListView

    /** The book the search is in (the reader's find, or "חיפוש בתוך"), null for the library. */
    private var scopeBook: Entry? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var search: TextSearch? = null
    private var showingResults = false
    private var suggestions: List<Row> = emptyList()

    // The text search: its query, where it looked, its results
    private var lastQuery = ""
    private var highlight: Regex? = null
    private var allBooks = false
    private var allWords = false
    private var fellBack = false
    private var stopped = false
    private val hits = ArrayList<Hit>()
    private var roots: List<Entry> = emptyList()
    private var tab: Long? = null

    /** The library's books in the order the search reads them, read once (the first search in the library). */
    private var order: SearchOrder? = null

    // Where the search is: the books it reads (null until they are known) from [nextBook] on, then [fallback] if the
    // base books have nothing
    private var queue: List<Entry>? = null
    private var nextBook = 0
    private var fallback: List<Entry>? = null

    /** The search has not ended: it is reading, or [paused]. */
    private var running = false

    /** Stopped while another screen reads the library, to go on when this one is back. */
    private var paused = false
    private var scan: Scan? = null

    /** The hits the scan found that the list has not taken yet ([take]); its lock also guards the [Scan]. */
    private val arrived = ArrayList<Hit>()

    /** The books whose results are all shown ("עוד N תוצאות מ…" chosen). */
    private val expanded = HashSet<Long>()
    private val headings = HashMap<Pair<Long, Int>, String>()

    /** The places still to look up for the rows drawn, the last one drawn first: one query at a time. */
    private val wanted = LinkedHashMap<Pair<Long, Int>, Hit>()
    private var lookups: Job? = null

    /** The last passages drawn: a row drawn again (scrolled back to, or as more results come) is not parsed again. */
    private val snippets = object : LinkedHashMap<Pair<Long, Int>, CharSequence>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, Int>, CharSequence>): Boolean = size > MAX_SNIPPETS
    }
    private var snippetMarks: DiacriticsMode? = null

    /** The chips drawn (their categories, null for הכל) and the one chosen then: built again only when these change. */
    private var drawnTabs: List<Long?> = emptyList()
    private var drawnTab: Long? = null

    private sealed interface Row {
        /** A suggestion: its icon, its text and second line; a tap (OK) runs [open], a long press [more]. */
        class Suggestion(val icon: Int, val text: CharSequence, val detail: String?, val open: () -> Unit, val more: (() -> Unit)? = null) : Row

        class Found(val hit: Hit, val last: Boolean) : Row

        class More(val bookId: Long, val title: String, val count: Int) : Row
    }

    /** The library's books as the search reads them, the base books apart, and each book's top category (its chip). */
    private class SearchOrder(val base: List<Entry>, val others: List<Entry>, val rootOf: Map<Long, Long>)

    /**
     * One run of the scan, as its thread tells it (under [arrived]'s lock): the book it is at, and whether it is in that
     * book's query, the only time an interrupt stops the scan and not another query on the one connection.
     */
    private class Scan(val from: Int) {
        /** The book it is at: the ones before it are read, and their hits handed over. */
        @Volatile
        var done = from

        @Volatile
        var reading = false

        /** Stopped: it reads no other book, and drops the hits of the one it is in (read again if it goes on). */
        var cancelled = false
    }

    // Each kind of row is handed back only a view of its own kind to reuse.
    private val adapter = ItemsAdapter<Row>(
        types = 3,
        typeOf = { row ->
            when (row) {
                is Row.Suggestion -> 0
                is Row.Found -> 1
                is Row.More -> 2
            }
        },
    ) { row, reuse, _ ->
        when (row) {
            is Row.Suggestion -> (reuse as? TreeRow ?: TreeRow(this, colors)).bind(row.text, iconRes = row.icon, secondLine = row.detail)
            is Row.Found -> hitView(row, reuse as? HitView)
            is Row.More -> moreView(row, reuse as? MoreView)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getLongExtra(EXTRA_BOOK_SCOPE, 0L).takeIf { it != 0L }?.let { id ->
            scopeBook = Entry(id, intent.getStringExtra(BookActivity.EXTRA_TITLE) ?: "", isBook = true)
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_back, "חזרה") { finish() }
            title.text = "חיפוש"
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))

        // One card: the field, the chips and the status over the list, as the main app's results page.
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = island(colors)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(6))
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // The logo beside the field, as Google's, where there is room for it.
        if (resources.configuration.screenWidthDp >= 360) {
            head.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }, lp(dp(44), dp(44)).apply { marginEnd = dp(10) })
        }
        head.addView(searchField(intent.getStringExtra(EXTRA_QUERY) ?: ""), lp(0, WRAP, 1f))
        top.addView(head, lp(MATCH, WRAP))

        tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(tabs)
            visibility = View.GONE
        }
        top.addView(tabsScroll, lp(MATCH, WRAP).apply { topMargin = dp(8) })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        statusRow = row
        val texts = FlowRow(this, dp(6), 0)
        status = label("", 14f, Palette.withAlpha(colors.text, 0.72f), maxLines = 2).apply { minHeight = dp(36); gravity = Gravity.CENTER_VERTICAL }
        switchLink = label("", 14f, colors.accent, maxLines = 1).apply {
            gravity = Gravity.CENTER
            minHeight = dp(36)
            setPadding(dp(4), 0, dp(4), 0)
            background = states(colors, 0, dp(6).toFloat())
        }
        texts.addView(status)
        texts.addView(switchLink)
        row.addView(texts, lp(0, WRAP, 1f))
        stop = iconButton(R.drawable.ic_close, colors.secondary, "בטל חיפוש", colors) { stopSearch() }.apply { visibility = View.GONE }
        row.addView(stop)
        top.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(4) })
        progress = progressLine(colors).apply { visibility = View.GONE }
        top.addView(progress, lp(MATCH, dp(4)).apply { topMargin = dp(2) })
        card.addView(top, lp(MATCH, WRAP))
        card.addView(divider(colors))

        list = ListView(this).apply {
            divider = null
            selector = states(colors, 0, dp(6).toFloat())
            cacheColorHint = 0
            setPadding(dp(6), dp(4), dp(6), dp(6))
            clipToPadding = false
            adapter = this@SearchActivity.adapter
            setOnItemClickListener { _, _, position, _ -> open(position) }
            setOnItemLongClickListener { _, _, position, _ ->
                val suggestion = this@SearchActivity.adapter.items.getOrNull(position) as? Row.Suggestion
                suggestion?.more?.invoke()
                suggestion?.more != null
            }
        }
        card.addView(list, lp(MATCH, 0, 1f))
        root.addView(card, lp(MATCH, 0, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        setContentView(root)
        drawScope()
        // A keypad starts in the field (else Android gives the title bar's back button the first focus).
        field.requestFocus()

        val runAtOnce = intent.getBooleanExtra(EXTRA_RUN, false)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val lib = app.library.open()
                    lib to runCatching { lib.rootEntries() }.getOrDefault(emptyList())
                }
            }
            // No library yet (the Home's welcome offers the search too), or one that cannot be read: nothing to search.
            val (lib, rootEntries) = result.getOrElse { e ->
                toast(if (e is NoLibraryException) "עדיין אין ספרייה במכשיר" else "לא ניתן לפתוח את הספרייה")
                finish()
                return@launch
            }
            bind(lib)
            roots = rootEntries
            if (runAtOnce && field.text.isNotBlank()) startTextSearch(field.text.toString().trim()) else suggest()
        }
    }

    /** What the search reads [lib] through: its connection, the references' lookup and the scan. */
    private fun bind(lib: Library.Opened) {
        opened = lib
        resolver = ReferenceResolver(RepositoryReferenceSource(lib.repository))
        search = TextSearch(lib)
    }

    override val heldLibrary: Library.Opened? get() = if (::opened.isInitialized) opened else null

    /**
     * The query, its results and the book the search had reached are ids in the library: they stay, and the search
     * goes on with the library opened again.
     */
    override fun onLibraryReopened(library: Library.Opened) {
        bind(library)
        goOn()
    }

    override fun onResume() {
        super.onResume()
        if (rebuilding) return
        goOn()
    }

    /** Back from a book opened from here: the search goes on from the book it was in. */
    private fun goOn() {
        if (paused) {
            paused = false
            launchSearch()
        }
    }

    override fun onPause() {
        // Leaving: the scan stops before the screen under this one reads the library again.
        if (isFinishing) stopScan()
        super.onPause()
    }

    override fun onStop() {
        pause()
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // The suggestions over results already found: back to the results, as the main app's popup closes.
        if (!showingResults && hits.isNotEmpty()) {
            field.setText(lastQuery)
            field.setSelection(lastQuery.length)
            // setText runs the watcher, whose suggestions would cover the results again; the ones there were are for
            // the text typed over it.
            suggestJob?.cancel()
            suggestions = emptyList()
            showResults()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    // ------------------------------------------------------------------ the field

    /** The main app's bar: a pill with its border, the search icon, the book picked, the field, and ✕. */
    private fun searchField(initial: String): View {
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPaddingRelative(dp(14), 0, dp(4), 0)
            background = roundRect(colors.panel, dp(24).toFloat(), colors.border, maxOf(1, dp(1)))
        }
        pill.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
        }, lp(dp(16), dp(16)).apply { marginEnd = dp(8) })
        // The book picked (SelectedChip): its title and ✕, which takes the search back to the whole library.
        bookChip = label("", 13f, colors.text, maxLines = 1).apply {
            maxWidth = (resources.displayMetrics.widthPixels * 0.35f).toInt()
            setPadding(dp(8), dp(4), dp(6), dp(4))
            background = states(
                roundRect(colors.accentTint, dp(12).toFloat()),
                roundRect(colors.pressed, dp(12).toFloat()),
                roundRect(colors.focus, dp(12).toFloat(), colors.accent, maxOf(1, dp(1))),
            )
            @Suppress("DEPRECATION")
            val x = resources.getDrawable(R.drawable.ic_close).mutate()
            x.setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
            x.setBounds(0, 0, dp(14), dp(14))
            setCompoundDrawablesRelative(null, null, x, null)
            compoundDrawablePadding = dp(4)
            contentDescription = "הסר ספר שנבחר"
            keyable(this) {
                scopeBook = null
                // To the field before the chip goes: a keypad's focus would go to the title bar.
                field.requestFocus()
                drawScope()
                suggest()
            }
        }
        pill.addView(bookChip, lp(WRAP, WRAP).apply { marginEnd = dp(8) })
        field = EditText(this).apply {
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.text)
            setHintTextColor(PLACEHOLDER_GREY)
            background = null
            setPadding(0, dp(8), 0, dp(8))
            setText(initial)
            setSelection(initial.length)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event == null || event.action == KeyEvent.ACTION_DOWN) enter()
                    true
                } else {
                    false
                }
            }
            // A keypad's OK (D-pad centre) is Enter too: TextView gives its editor-action listener the Enter key only.
            // In an empty field OK stays the field's own (it opens the keyboard, where there is one).
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER || text.isNullOrBlank()) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) enter()
                true
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    clear.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
                    suggest()
                }
            })
        }
        pill.addView(field, lp(0, WRAP, 1f))
        clear = iconButton(R.drawable.ic_close, colors.secondary, "ניקוי", colors) {
            field.setText("")
            field.requestFocus()
        }.apply { visibility = if (initial.isEmpty()) View.INVISIBLE else View.VISIBLE }
        pill.addView(clear)
        return pill
    }

    /** The field's chip and hint for where the search is: a book, or the library. */
    private fun drawScope() {
        val book = scopeBook
        bookChip.text = book?.title ?: ""
        bookChip.visibility = if (book == null) View.GONE else View.VISIBLE
        field.hint = if (book == null) nextHint() else "מה לחפש בספר זה? פרק, סימן או כל מילה"
    }

    // ------------------------------------------------------------------ the suggestions

    /**
     * The rows for what is typed (catBookRows): the places it names, the books named whole, the text search, then the
     * other books; in a book, its places and the search in it. Shown after a pause in the typing.
     */
    private fun suggest() {
        if (!::opened.isInitialized) return
        val q = field.text.toString().trim()
        suggestJob?.cancel()
        if (q.isEmpty()) {
            showSuggestions(emptyList())
            return
        }
        suggestJob = scope.launch {
            delay(SUGGEST_DELAY_MS)
            val book = scopeBook
            val rows = withContext(Dispatchers.IO) {
                val out = ArrayList<Row>()
                if (book != null) {
                    // A place in the book: the reference with the book's title before it.
                    runCatching { resolver.resolve("${book.title} $q", maxBooks = 1, maxResults = 5) }.getOrDefault(emptyList())
                        .filter { it.book.id == book.id }
                        .forEach { ref -> out += Row.Suggestion(R.drawable.ic_find_in_page, ref.label, book.title, { openPlace(ref.book.id, ref.book.title, ref.lineId) }) }
                    out += Row.Suggestion(R.drawable.ic_search, "חיפוש בתוך ${book.title}: $q", null, { startTextSearch(q) })
                    return@withContext out
                }
                val references = runCatching { resolver.resolve(q) }.getOrDefault(emptyList())
                val books = runCatching { opened.findBooks(q, MAX_BOOK_SUGGESTIONS) }.getOrDefault(emptyList())
                val named = runCatching { opened.booksNamed(q) }.getOrDefault(emptySet())
                val categories = runCatching { opened.categoriesOf((books.map { it.id } + references.map { it.book.id }).toSet()) }.getOrDefault(emptyMap())
                val (exact, others) = books.partition { it.id in named }
                fun bookRow(entry: Entry) = Row.Suggestion(R.drawable.ic_book, entry.title, categories[entry.id], { openBook(entry) }, { bookActions(entry) })
                references.forEach { ref ->
                    out += Row.Suggestion(R.drawable.ic_find_in_page, "${ref.book.title} ${ref.label}", categories[ref.book.id], { openPlace(ref.book.id, ref.book.title, ref.lineId) })
                }
                exact.forEach { out += bookRow(it) }
                out += Row.Suggestion(R.drawable.ic_search, "חיפוש במאגר: $q", null, { startTextSearch(q) })
                others.forEach { out += bookRow(it) }
                out
            }
            showSuggestions(rows)
        }
    }

    private fun showSuggestions(rows: List<Row>) {
        suggestions = rows
        showingResults = false
        tabsScroll.visibility = View.GONE
        statusRow.visibility = View.GONE
        progress.visibility = View.GONE
        adapter.items = rows
        list.setSelection(0)
    }

    /** Enter (the keyboard's search, OK in the field): the first row, as the main app's highlighted one. */
    private fun enter() {
        val q = field.text.toString().trim()
        if (q.isEmpty() || !::opened.isInitialized) return
        val first = suggestions.firstOrNull() as? Row.Suggestion
        if (first != null && suggestJob?.isActive != true) first.open() else startTextSearch(q)
    }

    /** A book's long press (the main app's "חיפוש בתוך" beside it): open it, or search in it. */
    private fun bookActions(entry: Entry) {
        Dialogs.choices(this, colors, entry.title, listOf(
            MenuEntry("פתיחה", R.drawable.ic_book) { openBook(entry) },
            MenuEntry("חיפוש בתוך", R.drawable.ic_search) {
                scopeBook = entry
                drawScope()
                field.setText("")
                field.requestFocus()
            },
        ))
    }

    private fun openBook(entry: Entry) {
        pause()
        BookActivity.start(this, entry.id, prefs.lastLine(entry.id), entry.title)
    }

    private fun openPlace(bookId: Long, title: String, lineId: Long) {
        pause()
        scope.launch {
            val index = withContext(Dispatchers.IO) { runCatching { opened.lineIndex(lineId) }.getOrNull() }
            if (index != null && fromBook(bookId)) {
                setResult(RESULT_OK, Intent().putExtra(BookActivity.EXTRA_LINE, index))
                finish()
            } else {
                BookActivity.start(this@SearchActivity, bookId, index, title)
            }
        }
    }

    /** Opened by [bookId]'s reader for its find: a place in it goes back there. */
    private fun fromBook(bookId: Long): Boolean = callingActivity != null && intent.getLongExtra(EXTRA_BOOK_SCOPE, 0L) == bookId

    // ------------------------------------------------------------------ the text search

    /**
     * Searches [q]: in the book picked, else in the base books (and all the books if they have nothing), or all the
     * books after "חיפוש בכל הספרים".
     */
    private fun startTextSearch(q: String, everywhere: Boolean = false, words: Boolean = false) {
        if (search == null || q.isEmpty()) return
        stopScan()
        suggestJob?.cancel()
        // A keypad on the suggestion that started it: to the field, as the list empties (else to the title bar).
        if (list.hasFocus()) field.requestFocus()
        lastQuery = q
        allBooks = everywhere
        allWords = words
        fellBack = false
        stopped = false
        tab = null
        highlight = LineText.highlighter(q, phrase = !words)
        synchronized(arrived) { arrived.clear() }
        hits.clear()
        expanded.clear()
        snippets.clear()
        queue = scopeBook?.let { listOf(it) }
        nextBook = 0
        fallback = null
        running = true
        paused = false
        launchSearch()
        showResults()
    }

    /**
     * Runs the search from where it is to its end. Its progress and the hits found are drawn a few times a second: the
     * scan never waits for the screen, and the list is not drawn again for each book.
     */
    private fun launchSearch() {
        val engine = search ?: return
        searchJob = scope.launch {
            val ticker = launch {
                while (true) {
                    take()
                    drawStatus()
                    delay(REFRESH_MS)
                }
            }
            if (queue == null) {
                val books = order ?: withContext(Dispatchers.IO) { runCatching { searchOrder() }.getOrNull() }?.also { order = it }
                queue = when {
                    books == null -> emptyList()
                    allBooks -> books.base + books.others
                    else -> books.base
                }
                fallback = if (allBooks) null else books?.others
            }
            read(engine)
            ticker.cancel()
            running = false
            take()
            drawStatus()
        }
    }

    /** The library's books in the search's order, and each one's top category: read off the main thread, once. */
    private fun searchOrder(): SearchOrder {
        val all = opened.booksUnder(null)
        val rootOf = HashMap<Long, Long>(all.size * 2)
        for (book in all) rootOf[book.id] = book.root
        return SearchOrder(all.filter { it.base }, all.filterNot { it.base }, rootOf)
    }

    /** Reads [queue] from [nextBook] on, then [fallback] if the base books had nothing. */
    private suspend fun read(engine: TextSearch) {
        while (true) {
            take()
            val books = queue ?: return
            val limit = MAX_HITS - hits.size
            if (nextBook < books.size && limit > 0) {
                val s = Scan(nextBook)
                scan = s
                val q = lastQuery
                val words = allWords
                withContext(Dispatchers.IO) {
                    runCatching {
                        engine.run(
                            q, books.subList(s.from, books.size), limit, words,
                            onBook = { i, _ ->
                                synchronized(arrived) {
                                    if (s.cancelled) throw CancellationException("stopped")
                                    s.done = s.from + i
                                    s.reading = true
                                }
                            },
                            onHits = { found ->
                                synchronized(arrived) {
                                    s.reading = false
                                    if (!s.cancelled) {
                                        arrived += found
                                        s.done++
                                    }
                                }
                            },
                        )
                    }
                    s.reading = false
                }
                scan = null
            }
            nextBook = books.size
            take()
            val others = fallback
            if (others == null || hits.isNotEmpty()) return
            // Nothing in the base books: all the others (the main app's fallback).
            fellBack = true
            fallback = null
            queue = others
            nextBook = 0
        }
    }

    /** Moves the hits found since the last time into the results, and draws them. */
    private fun take() {
        val found = synchronized(arrived) {
            if (arrived.isEmpty()) return
            ArrayList(arrived).also { arrived.clear() }
        }
        hits += found
        if (showingResults) showHits()
    }

    /**
     * Stops the scan where it is, keeping what it found. The book it is reading is stopped (sqlite3_interrupt) only
     * while it is in that book's query: at any other time the interrupt would fail another query on the one
     * connection, a result's place here or the reader's lines.
     */
    private fun stopScan() {
        val s = scan
        scan = null
        if (s != null) {
            synchronized(arrived) {
                s.cancelled = true
                nextBook = s.done
            }
        }
        searchJob?.cancel()
        if (s?.reading == true) search?.cancel()
    }

    /**
     * Stops the search while another screen reads the library, to go on when this one is back (onResume): before a
     * book is opened from here, so that the interrupt cannot meet the reader's first queries, and when the screen
     * goes away.
     */
    private fun pause() {
        if (!running || paused) return
        paused = true
        stopScan()
    }

    private fun stopSearch() {
        if (!running) return
        stopScan()
        running = false
        paused = false
        stopped = true
        take()
        drawStatus()
    }

    private fun showResults() {
        showingResults = true
        statusRow.visibility = View.VISIBLE
        showHits()
        drawStatus()
    }

    /**
     * SearchStatusLine: where the search is; once it ends, how many it found and where, and the other choice as a link
     * (all the books, or the base books only).
     */
    private fun drawStatus() {
        if (!showingResults) return
        if (running) {
            progress.visibility = View.VISIBLE
            stop.visibility = View.VISIBLE
            hide(switchLink)
            val books = queue.orEmpty()
            val at = (scan?.done ?: nextBook).coerceIn(0, maxOf(0, books.size - 1))
            progress.max = maxOf(1, books.size)
            progress.progress = at
            setStatus(if (books.isEmpty()) "מחפש..." else "מחפש ב${books[at].title} (${at + 1} מתוך ${books.size}) · ${hits.size} תוצאות")
            return
        }
        progress.visibility = View.GONE
        hide(stop)
        val book = scopeBook
        val n = hits.size
        val first = if (n >= MAX_HITS) "$n התוצאות הראשונות" else "$n תוצאות"
        setStatus(
            when {
                n == 0 -> "לא נמצאו תוצאות"
                book != null -> "$first ב${book.title}"
                fellBack -> "לא נמצא בספרי יסוד · מוצגות $first מכל הספרים"
                allBooks -> "$first בכל הספרים"
                else -> "$first בספרי יסוד"
            } + if (stopped) " (החיפוש הופסק)" else "",
        )
        val link: Pair<String, () -> Unit>? = when {
            n == 0 && !allWords && lastQuery.trim().contains(' ') -> "חיפוש כל מילה בנפרד" to { startTextSearch(lastQuery, allBooks, words = true) }
            book != null || fellBack -> null
            allBooks -> "רק ספרי יסוד" to { startTextSearch(lastQuery, everywhere = false, words = allWords) }
            else -> "חיפוש בכל הספרים" to { startTextSearch(lastQuery, everywhere = true, words = allWords) }
        }
        if (link == null) {
            hide(switchLink)
        } else {
            switchLink.text = link.first
            switchLink.visibility = View.VISIBLE
            keyable(switchLink) { link.second() }
        }
    }

    /** The status's text, set only when it changes: each change lays the screen out again. */
    private fun setStatus(text: String) {
        if (!TextUtils.equals(status.text, text)) status.text = text
    }

    /**
     * Hides [view]; a keypad's focus in it goes to the results, or the field. Android 4.4 to 7.1 would give it to the
     * title bar's Back, where the next OK leaves the search.
     */
    private fun hide(view: View) {
        if (view.visibility == View.GONE) return
        if (view.hasFocus() && !(adapter.count > 0 && list.requestFocus())) field.requestFocus()
        view.visibility = View.GONE
    }

    /** CategoryTabs: הכל, then the top categories the results are in, in the library's order. */
    private fun drawTabs() {
        val rootOf = order?.rootOf.orEmpty()
        val present = hits.mapNotNullTo(HashSet()) { rootOf[it.bookId] }
        val shown = roots.filter { it.id in present }
        if (scopeBook != null || shown.size < 2) {
            hide(tabsScroll)
            drawnTabs = emptyList()
            return
        }
        tabsScroll.visibility = View.VISIBLE
        val ids = listOf<Long?>(null) + shown.map { it.id }
        if (ids == drawnTabs && tab == drawnTab) return
        // The chip a keypad is on gets the focus back: removing it sends the focus to the title bar.
        val focusedAt = tabs.focusedChild?.let { tabs.indexOfChild(it) } ?: -1
        val focusedId = drawnTabs.getOrNull(focusedAt)
        tabs.removeAllViews()
        drawnTabs = ids
        drawnTab = tab
        fun add(text: String, id: Long?) {
            tabs.addView(chip(text, colors, tab == id) {
                tab = id
                showHits()
            }, lp(WRAP, WRAP).apply { marginEnd = dp(6) })
        }
        add("הכל", null)
        shown.forEach { add(it.title, it.id) }
        if (focusedAt >= 0) tabs.getChildAt(maxOf(0, ids.indexOf(focusedId))).requestFocus() else reveal()
    }

    /**
     * Scrolls the chips to the one chosen (הכל: their start). A HorizontalScrollView keeps its place from the left as
     * chips are added, and Android 4.4's starts at the left whatever the direction: in a right-to-left row, its end.
     */
    private fun reveal() {
        tabs.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                tabs.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val chosen = tabs.getChildAt(maxOf(0, drawnTabs.indexOf(drawnTab))) ?: return
                val max = maxOf(0, tabs.width - tabsScroll.width)
                tabsScroll.scrollTo(((chosen.left + chosen.right) / 2 - tabsScroll.width / 2).coerceIn(0, max), 0)
            }
        })
    }

    /** The results, a book's first one with "עוד N תוצאות מ…" under it, or all of them once that is chosen. */
    private fun showHits() {
        val rows = ArrayList<Row>()
        val filter = tab
        val rootOf = order?.rootOf.orEmpty()
        val shown = if (filter == null) hits else hits.filter { rootOf[it.bookId] == filter }
        for ((bookId, group) in shown.groupBy { it.bookId }) {
            if (bookId in expanded || group.size == 1) {
                group.forEachIndexed { i, hit -> rows += Row.Found(hit, last = i == group.size - 1) }
            } else {
                rows += Row.Found(group.first(), last = false)
                rows += Row.More(bookId, group.first().bookTitle, group.size - 1)
            }
        }
        adapter.items = rows
        drawTabs()
    }

    private fun open(position: Int) {
        when (val row = adapter.items.getOrNull(position) ?: return) {
            is Row.Suggestion -> row.open()
            is Row.More -> {
                expanded += row.bookId
                showHits()
            }
            is Row.Found -> {
                val hit = row.hit
                if (fromBook(hit.bookId)) {
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(BookActivity.EXTRA_LINE, hit.lineIndex).putExtra(BookActivity.EXTRA_FIND, lastQuery)
                            .putExtra(BookActivity.EXTRA_FIND_WORDS, allWords),
                    )
                    finish()
                } else {
                    pause()
                    BookActivity.start(this, hit.bookId, hit.lineIndex, hit.bookTitle, lastQuery, allWords)
                }
            }
        }
    }

    /** A result (ResultView): its book in bold, "· its place", two lines of the passage with the words found bold. */
    private inner class HitView : LinearLayout(this@SearchActivity) {
        val title: TextView
        val place: TextView
        val snippet: TextView
        val line: View
        var key: Pair<Long, Int>? = null

        init {
            orientation = VERTICAL
            val head = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            title = label("", 15f, colors.text, bold = true, maxLines = 1)
            place = label("", 13f, Palette.withAlpha(colors.text, 0.72f), maxLines = 1)
            head.addView(title, lp(WRAP, WRAP).apply { marginEnd = dp(6) })
            head.addView(place, lp(WRAP, WRAP))
            val body = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            body.addView(head)
            snippet = label("", 14f, Palette.withAlpha(colors.text, 0.72f), maxLines = 2, typeface = Fonts.book(context)).apply {
                lineHeightFactor(1.65f)
                setPadding(0, dp(2), 0, dp(2))
            }
            body.addView(snippet, lp(MATCH, WRAP))
            addView(body, lp(MATCH, WRAP))
            line = View(context).apply { setBackgroundColor(Palette.withAlpha(colors.border, 0.5f)) }
            addView(line, lp(MATCH, maxOf(1, dp(1))).apply { marginStart = dp(8) })
        }
    }

    private fun hitView(row: Row.Found, reuse: HitView?): View {
        val view = reuse ?: HitView()
        val hit = row.hit
        val key = hit.bookId to hit.lineIndex
        view.key = key
        view.title.text = hit.bookTitle
        val marks = prefs.diacritics
        if (marks != snippetMarks) {
            snippets.clear()
            snippetMarks = marks
        }
        view.snippet.text = snippets.getOrPut(key) { snippet(hit.content, marks) }
        view.line.visibility = if (row.last) View.VISIBLE else View.GONE
        val known = headings[key]
        view.place.text = known?.let { "· $it" } ?: ""
        if (known == null) lookUp(key, hit)
        return view
    }

    /**
     * Looks up the place of [key]'s result (its table of contents entry) for its row: one query at a time, the last
     * row drawn first, none for a row scrolled past meanwhile (it asks again when it is drawn again).
     */
    private fun lookUp(key: Pair<Long, Int>, hit: Hit) {
        wanted.remove(key)
        wanted[key] = hit
        if (lookups?.isActive == true || !::opened.isInitialized) return
        lookups = scope.launch {
            while (wanted.isNotEmpty()) {
                val next = wanted.keys.last()
                val of = wanted.remove(next) ?: continue
                if (rowOf(next) == null) continue
                val result = withContext(Dispatchers.IO) { runCatching { opened.heading(of.bookId, of.lineIndex) } }
                val error = result.exceptionOrNull()
                if (error != null) {
                    // The scan's interrupt can meet this query on the one connection: once more. Any other failure
                    // is not kept, and the row asks again when it is drawn again.
                    if ((error as? SqliteException)?.interrupted == true) wanted[next] = of
                    continue
                }
                val heading = result.getOrNull()?.takeIf { it.isNotBlank() && it != of.bookTitle } ?: "שורה ${of.lineIndex + 1}"
                headings[next] = heading
                rowOf(next)?.place?.text = "· $heading"
            }
        }
    }

    /** The row on screen that shows [key]'s result, if one does. */
    private fun rowOf(key: Pair<Long, Int>): HitView? {
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) as? HitView ?: continue
            if (row.key == key) return row
        }
        return null
    }

    /** "עוד N תוצאות מ…": the book's other results (ResultLink). */
    private inner class MoreView : LinearLayout(this@SearchActivity) {
        val text: TextView = label("", 13f, colors.accent, maxLines = 1).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            minHeight = dp(36)
            setPadding(dp(8), 0, dp(8), dp(4))
        }

        init {
            orientation = VERTICAL
            addView(text, lp(MATCH, WRAP))
            addView(View(context).apply { setBackgroundColor(Palette.withAlpha(colors.border, 0.5f)) }, lp(MATCH, maxOf(1, dp(1))).apply { marginStart = dp(8) })
        }
    }

    private fun moreView(row: Row.More, reuse: MoreView?): View = (reuse ?: MoreView()).apply {
        text.text = "עוד ${row.count} תוצאות מ${row.title}"
    }

    /**
     * The passage around the first word found, as the main app's compactSnippet: a little before it and up to 200
     * letters after it, the words bold.
     */
    private fun snippet(html: String, marks: DiacriticsMode): CharSequence {
        val text = LineText.render(html, marks)
        val first = highlight?.find(text)
        var from = first?.let { maxOf(0, it.range.first - SNIPPET_BEFORE) } ?: 0
        if (from > 0) {
            val space = text.lastIndexOf(' ', from)
            if (space in 1 until (first?.range?.first ?: 0)) from = space + 1
        }
        val end = text.indexOf(' ', (first?.range?.first ?: 0) + SNIPPET_AFTER)
        val to = if (end > from) end else text.length
        val out = SpannableStringBuilder(if (from > 0) "..." else "").append(text.subSequence(from, to))
        if (to < text.length) out.append("...")
        highlight?.findAll(out)?.forEach { match ->
            out.setSpan(StyleSpan(Typeface.BOLD), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(ForegroundColorSpan(colors.text), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    companion object {
        const val EXTRA_QUERY = "query"
        const val EXTRA_BOOK_SCOPE = "book_scope"

        /** Search [EXTRA_QUERY]'s text at once (a zayit://search link), rather than suggest. */
        const val EXTRA_RUN = "run"
        private const val MAX_HITS = 500
        private const val MAX_BOOK_SUGGESTIONS = 12
        private const val SUGGEST_DELAY_MS = 250L
        private const val SNIPPET_BEFORE = 40
        private const val SNIPPET_AFTER = 200
        private const val MAX_SNIPPETS = 64

        /** How often a running search draws where it is and the hits it found. */
        private const val REFRESH_MS = 250L

        /** Desktop's placeholder grey (HomeView SearchBar, both themes). */
        const val PLACEHOLDER_GREY = 0xFF9AA0A6.toInt()

        /**
         * The bar's hints (UnifiedSearchBar HINTS: a reference, then a text, in turn), one a time the bar is shown:
         * the main app types them out; here a still one, which costs an old phone nothing.
         */
        private val HINTS = listOf(
            "שו\"ע אורח חיים סימן רסח", "הכל בידי שמים חוץ מיראת שמים",
            "רמב\"ם הלכות שבת פרק כ", "ואהבת לרעך כמוך",
            "משנה ברורה סימן רלה סעיף קטן ד", "שמע ישראל ה׳ אלקינו ה׳ אחד",
            "ברכות דף ב עמוד א", "מי שטרח בערב שבת יאכל בשבת",
            "תהילים פרק כג פסוק ד", "אין עוד מלבדו",
        )
        private var hint = 0

        fun nextHint(): String = HINTS[hint++ % HINTS.size]
    }
}

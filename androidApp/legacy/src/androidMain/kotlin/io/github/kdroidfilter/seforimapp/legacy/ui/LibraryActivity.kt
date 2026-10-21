package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Entry
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.NoLibraryException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The library tree of the main app's drawer (LibraryTree): categories open in place, a category's books before its
 * subcategories. On a keypad, OK opens and closes a category, and the arrows do too: ← into it, → out of it, as the
 * tree reads from right to left.
 *
 * Above it the book search ("חיפוש ספר"), as the drawer's: from two letters the tree narrows to the books found, by the
 * library's lookup index as the main app's (its ranking, acronyms; the books' titles in the database without it), with
 * their categories open and the best match marked; Enter opens it. The keyboard comes only with a touch on the field,
 * and a keypad reaches the field from the tree's first row.
 */
class LibraryActivity : BaseActivity() {
    private lateinit var opened: Library.Opened
    private lateinit var list: ListView

    /** A row of the flattened tree; [loading] while its children are being read. */
    private class Node(val entry: Entry, val depth: Int, var open: Boolean = false, var loading: Boolean = false)

    private val nodes = ArrayList<Node>()

    /** The book search's tree: its query, its rows (every category open) and its best match, the one Enter opens. */
    private class Found(val query: String, val nodes: List<Node>, val best: Long?)

    private var found: Found? = null
    private var searchJob: Job? = null
    private lateinit var field: EditText
    private lateinit var clear: View
    private lateinit var none: TextView

    private val adapter = ItemsAdapter<Node> { node, reuse, _ ->
        val row = reuse as? TreeRow ?: TreeRow(this, colors)
        if (node.entry.isBook) {
            row.bind(node.entry.title, node.depth, R.drawable.ic_book, keepArrowSpace = true, marked = found?.best == node.entry.id)
        } else {
            row.bind(node.entry.title, node.depth, R.drawable.ic_folder, if (node.open) R.drawable.ic_arrow_down else R.drawable.ic_arrow_left)
        }
    }

    override val heldLibrary: Library.Opened? get() = if (::opened.isInitialized) opened else null

    /** The tree holds only the library's ids: it stays as it is, open where it was. */
    override fun onLibraryReopened(library: Library.Opened) {
        opened = library
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The search field takes no keyboard until it is touched (the drawer's, without its header)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_back, "חזרה") { finish() }
            title.text = "ספרייה"
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))
        list = ListView(this).apply {
            divider = null
            selector = states(colors, 0, dp(6).toFloat())
            cacheColorHint = 0
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipToPadding = false
            adapter = this@LibraryActivity.adapter
            setOnItemClickListener { _, _, position, _ -> activate(position) }
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN || found != null) return@setOnKeyListener false
                val node = nodes.getOrNull(selectedItemPosition) ?: return@setOnKeyListener false
                when {
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !node.entry.isBook && !node.open -> { toggle(selectedItemPosition); true }
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !node.entry.isBook && node.open -> { toggle(selectedItemPosition); true }
                    else -> false
                }
            }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = island(colors)
        }
        card.addView(searchField(), lp(MATCH, WRAP))
        none = label("לא נמצאו ספרים", 14f, colors.secondary, typeface = Fonts.ui(this)).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            visibility = View.GONE
        }
        val area = FrameLayout(this)
        area.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        area.addView(none, FrameLayout.LayoutParams(MATCH, WRAP))
        card.addView(area, lp(MATCH, 0, 1f))
        root.addView(card, lp(MATCH, 0, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        setContentView(root)

        val focus = intent.getLongExtra(EXTRA_CATEGORY, 0L).takeIf { it != 0L }
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { app.library.open() } }
            // No library yet (the Home's welcome has the library button too), or one that cannot be read.
            opened = result.getOrElse { e ->
                toast(if (e is NoLibraryException) "עדיין אין ספרייה במכשיר" else "לא ניתן לפתוח את הספרייה")
                finish()
                return@launch
            }
            val roots = withContext(Dispatchers.IO) { runCatching { opened.rootEntries() }.getOrDefault(emptyList()) }
            roots.forEach { nodes += Node(it, 0) }
            adapter.items = ArrayList(nodes)
            // Opened from a section of the Home: that section, open, at the top.
            if (focus != null) {
                val path = withContext(Dispatchers.IO) { runCatching { opened.categoryPath(focus) }.getOrDefault(emptyList()) }
                var at = -1
                for (step in path) {
                    val i = (at + 1 until nodes.size).firstOrNull { !nodes[it].entry.isBook && nodes[it].entry.id == step.id } ?: break
                    val node = nodes[i]
                    expand(node)
                    at = nodes.indexOf(node)
                    if (at < 0) break
                }
                if (at >= 0) list.setSelection(at)
            }
            // A keypad starts in the tree: the title bar's Back took the first focus while the list was empty.
            if (!list.isInTouchMode) list.requestFocus()
        }
    }

    private fun activate(position: Int) {
        val node = adapter.items.getOrNull(position) ?: return
        if (found != null) {
            // The search's tree: its categories stay open, a book opens
            if (node.entry.isBook) openBook(node.entry)
            return
        }
        if (node.entry.isBook) {
            openBook(node.entry)
        } else {
            toggle(position)
        }
    }

    private fun openBook(entry: Entry) = BookActivity.start(this, entry.id, prefs.lastLine(entry.id), entry.title)

    /** The drawer's search field (PaneSearchField): a rounded field on the ground colour, its icon, ✕ "נקה" with text. */
    private fun searchField(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            setPaddingRelative(dp(10), 0, dp(2), 0)
            background = roundRect(colors.ground, dp(8).toFloat())
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
        }, lp(dp(18), dp(18)).apply { marginEnd = dp(6) })
        field = EditText(this).apply {
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Fonts.ui(this@LibraryActivity)
            setTextColor(colors.text)
            hint = "חיפוש ספר"
            setHintTextColor(colors.disabled)
            background = null
            setPadding(0, dp(6), 0, dp(6))
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event == null || event.action == KeyEvent.ACTION_DOWN) openBest()
                    true
                } else {
                    false
                }
            }
            // A keypad's OK runs the field as Enter, once it has text
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER || text.isNullOrBlank()) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openBest()
                true
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    clear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                    search(s?.toString().orEmpty())
                }
            })
        }
        row.addView(field, lp(0, WRAP, 1f))
        clear = iconButton(R.drawable.ic_close, colors.secondary, "נקה", colors) {
            field.setText("")
            field.requestFocus()
        }.apply { visibility = View.GONE }
        row.addView(clear)
        return FrameLayout(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(4))
            addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
        }
    }

    /**
     * Narrows the tree to the books [text] names, from two letters and 120 ms after the last one typed (the drawer's):
     * the lookup index's books, best first (the database's titles and acronyms without it), up to 120.
     */
    private fun search(text: String) {
        searchJob?.cancel()
        val q = text.trim()
        if (q.length < MIN_QUERY || !::opened.isInitialized) {
            showFound(null)
            return
        }
        val lib = opened
        searchJob = scope.launch {
            delay(SEARCH_DELAY_MS)
            val ids = app.search.suggestBooks(q, MAX_BOOKS)?.map { it.id }
                ?: withContext(Dispatchers.IO) { runCatching { lib.findBooks(q, MAX_BOOKS).map { it.id } }.getOrDefault(emptyList()) }
            val result = withContext(Dispatchers.IO) { runCatching { foundTree(lib, q, ids) }.getOrNull() } ?: return@launch
            showFound(result)
        }
    }

    /**
     * The books [ids] in the tree (desktop buildBookFilter): the categories that lead to them, open, each one's books
     * before its subcategories, by their place in the library; the best match is the first of [ids] shown.
     */
    private fun foundTree(lib: Library.Opened, query: String, ids: List<Long>): Found {
        if (ids.isEmpty()) return Found(query, emptyList(), null)
        class Cat(val id: Long, val parentId: Long?, val title: String, val order: Long)
        class Book(val id: Long, val title: String, val categoryId: Long, val order: Long)
        val marks = ids.joinToString(",") { "?" }
        val books = lib.query("SELECT id, title, categoryId, orderIndex FROM book WHERE id IN ($marks)", { ids.forEachIndexed { i, id -> bindLong(i, id) } }) {
            Book(it.getLong(0)!!, it.getString(1)!!, it.getLong(2)!!, it.getLong(3) ?: 0L)
        }
        val categories = lib.query("SELECT id, parentId, title, orderIndex FROM category") {
            Cat(it.getLong(0)!!, it.getLong(1), it.getString(2)!!, it.getLong(3) ?: 0L)
        }.associateBy { it.id }
        val used = HashSet<Long>()
        for (book in books) {
            var at: Long? = book.categoryId
            var guard = 0
            while (at != null && guard++ < 64 && used.add(at)) at = categories[at]?.parentId
        }
        val shown = used.mapNotNull { categories[it] }
        val children = shown.filter { it.parentId != null }.groupBy { it.parentId!! }.mapValues { (_, l) -> l.sortedWith(compareBy<Cat> { it.order }.thenBy { it.title }) }
        val byCategory = books.groupBy { it.categoryId }.mapValues { (_, l) -> l.sortedWith(compareBy<Book> { it.order }.thenBy { it.title }) }
        val out = ArrayList<Node>()
        fun walk(cats: List<Cat>, depth: Int) {
            for (cat in cats) {
                out += Node(Entry(cat.id, cat.title, isBook = false), depth, open = true)
                byCategory[cat.id]?.forEach { out += Node(Entry(it.id, it.title, isBook = true), depth + 1) }
                walk(children[cat.id].orEmpty(), depth + 1)
            }
        }
        walk(shown.filter { it.parentId == null || it.parentId !in used }.sortedWith(compareBy<Cat> { it.order }.thenBy { it.title }), 0)
        val inTree = out.filter { it.entry.isBook }.mapTo(HashSet()) { it.entry.id }
        return Found(query, out, ids.firstOrNull { it in inTree })
    }

    /** The search's tree ([result]), "לא נמצאו ספרים" when it found none, or the library's tree again (null). */
    private fun showFound(result: Found?) {
        found = result
        none.visibility = if (result != null && result.best == null) View.VISIBLE else View.GONE
        adapter.items = result?.nodes ?: ArrayList(nodes)
        val best = result?.nodes?.indexOfFirst { it.entry.isBook && it.entry.id == result.best } ?: -1
        if (best >= 0) list.setSelection(maxOf(0, best - 3))
    }

    /** Enter in the field: the best match opens, once the search for what is typed has run. */
    private fun openBest() {
        val result = found ?: return
        if (result.query != field.text.toString().trim()) return
        val id = result.best ?: return
        result.nodes.firstOrNull { it.entry.isBook && it.entry.id == id }?.let { openBook(it.entry) }
    }

    private fun toggle(position: Int) {
        val node = nodes.getOrNull(position) ?: return
        if (node.open) collapse(position) else scope.launch { expand(node) }
    }

    /**
     * Opens [node]'s category: its books, then its subcategories, under it. Once: a second OK (or a held ←) while it
     * reads does nothing, and the children go under the node wherever it is by then.
     */
    private suspend fun expand(node: Node) {
        if (node.open || node.loading) return
        node.loading = true
        val children = withContext(Dispatchers.IO) { runCatching { opened.children(node.entry.id) }.getOrDefault(emptyList()) }
        node.loading = false
        // Rows above it may have opened or closed meanwhile, or its parent closed over it.
        val at = nodes.indexOf(node)
        if (at < 0) return
        node.open = true
        nodes.addAll(at + 1, children.map { Node(it, node.depth + 1) })
        if (found == null) adapter.items = ArrayList(nodes)
    }

    private fun collapse(position: Int) {
        val node = nodes[position]
        node.open = false
        var end = position + 1
        while (end < nodes.size && nodes[end].depth > node.depth) end++
        nodes.subList(position + 1, end).clear()
        if (found == null) adapter.items = ArrayList(nodes)
    }

    companion object {
        private const val EXTRA_CATEGORY = "category"

        /** The drawer's book search: from two letters (MIN_BOOK_QUERY_LENGTH), 120 ms after the last, 120 books at most. */
        private const val MIN_QUERY = 2
        private const val SEARCH_DELAY_MS = 120L
        private const val MAX_BOOKS = 120

        fun start(context: Context, categoryId: Long?) {
            context.startActivity(Intent(context, LibraryActivity::class.java).putExtra(EXTRA_CATEGORY, categoryId ?: 0L))
        }
    }
}

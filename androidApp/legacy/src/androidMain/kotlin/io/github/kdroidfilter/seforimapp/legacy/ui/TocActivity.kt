package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ListView
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A book's table of contents as the main app's TOC pane draws it (TocPane TocTree): entries open in place under their
 * parent, the reader's place marked and its parents open. An entry with entries under it opens and closes; the others
 * take the reader to their line, and a long press takes it to an open entry's own line. On a keypad OK does the same,
 * and ← opens an entry and → closes it, as the tree reads from right to left.
 */
class TocActivity : BaseActivity() {
    private var bookId = 0L
    private lateinit var opened: Library.Opened
    private lateinit var list: ListView

    /** A row of the flattened tree; [loading] while its entries are being read. */
    private class Node(val entry: TocEntry, val depth: Int, var open: Boolean = false, var loading: Boolean = false)

    private val nodes = ArrayList<Node>()

    /** The entry of the reader's place. */
    private var current: Long? = null
    private val adapter = ItemsAdapter<Node> { node, reuse, _ ->
        val arrow = when {
            !node.entry.hasChildren -> null
            node.open -> R.drawable.ic_arrow_down
            else -> R.drawable.ic_arrow_left
        }
        (reuse as? TreeRow ?: TreeRow(this, colors)).bind(node.entry.text, node.depth, arrowRes = arrow, keepArrowSpace = true, marked = node.entry.id == current)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookId = intent.getLongExtra(BookActivity.EXTRA_BOOK, 0L)
        val line = intent.getIntExtra(BookActivity.EXTRA_LINE, 0)
        val title = intent.getStringExtra(BookActivity.EXTRA_TITLE) ?: ""
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_back, "חזרה") { finish() }
            this.title.text = "תוכן עניינים"
            setSubtitle(title)
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))
        list = ListView(this).apply {
            divider = null
            // The keypad's highlight over the rows, translucent: under them, the reader's place (a row with its own
            // ground) would hide it, and that is the row a keypad starts on. A touch shows the row's own pressed ground.
            isDrawSelectorOnTop = true
            selector = states(ColorDrawable(0), ColorDrawable(0), roundRect(colors.focus, dp(6).toFloat()))
            cacheColorHint = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            adapter = this@TocActivity.adapter
            setOnItemClickListener { _, _, position, _ -> activate(position) }
            setOnItemLongClickListener { _, _, position, _ ->
                nodes.getOrNull(position)?.let { go(it.entry) }
                true
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val node = nodes.getOrNull(selectedItemPosition) ?: return@setOnKeyListener false
                when {
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT && node.entry.hasChildren && !node.open -> {
                        toggle(selectedItemPosition)
                        true
                    }
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && node.open -> {
                        toggle(selectedItemPosition)
                        true
                    }
                    else -> false
                }
            }
        }
        val card = LinearLayout(this).apply { background = island(colors) }
        card.addView(list, lp(MATCH, MATCH))
        root.addView(card, lp(MATCH, 0, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        setContentView(root)

        scope.launch {
            opened = withContext(Dispatchers.IO) { app.library.open() }
            val (roots, trail) = withContext(Dispatchers.IO) {
                runCatching {
                    var top = opened.repository.getBookRootToc(bookId)
                    // A root that only repeats the book's title: its entries are the tree's first level.
                    val titleRoot = top.firstOrNull()?.takeIf { it.text == title && it.hasChildren }
                    if (titleRoot != null) top = opened.repository.getTocChildren(titleRoot.id) + top.drop(1)
                    top to trailTo(line)
                }.getOrDefault(emptyList<TocEntry>() to emptyList())
            }
            roots.forEach { nodes += Node(it, 0) }
            current = trail.lastOrNull()?.id
            // The reader's place: its parents open, it in view.
            var at = -1
            for (step in trail) {
                val node = nodes.firstOrNull { it.entry.id == step.id } ?: continue
                if (step.id != current && node.entry.hasChildren) expand(node)
                at = nodes.indexOf(node)
            }
            adapter.items = ArrayList(nodes)
            if (at >= 0) {
                list.setSelection(maxOf(0, at - 2))
                if (!list.isInTouchMode) list.post { list.setSelection(at) }
            }
            // A keypad starts in the tree: the title bar's Back took the first focus while the list was empty.
            if (!list.isInTouchMode) list.requestFocus()
        }
    }

    private fun activate(position: Int) {
        val node = nodes.getOrNull(position) ?: return
        if (node.entry.hasChildren) toggle(position) else go(node.entry)
    }

    private fun toggle(position: Int) {
        val node = nodes.getOrNull(position) ?: return
        if (node.open) {
            node.open = false
            var end = position + 1
            while (end < nodes.size && nodes[end].depth > node.depth) end++
            nodes.subList(position + 1, end).clear()
            adapter.items = ArrayList(nodes)
        } else {
            scope.launch { if (expand(node)) adapter.items = ArrayList(nodes) }
        }
    }

    /**
     * Opens [node]: its entries under it; false when it was open or opening already. Once: a second OK (or a held ←)
     * while it reads does nothing, and the entries go under the node wherever it is by then.
     */
    private suspend fun expand(node: Node): Boolean {
        if (node.open || node.loading) return false
        node.loading = true
        val children = withContext(Dispatchers.IO) { runCatching { opened.repository.getTocChildren(node.entry.id) }.getOrDefault(emptyList()) }
        node.loading = false
        // Entries above it may have opened or closed meanwhile, or its parent closed over it.
        val at = nodes.indexOf(node)
        if (at < 0) return false
        node.open = true
        nodes.addAll(at + 1, children.map { Node(it, node.depth + 1) })
        return true
    }

    /** The entries from the root down to the one of the line at [lineIndex]. */
    private fun trailTo(lineIndex: Int): List<TocEntry> {
        val entryId = opened.query(
            "SELECT lt.tocEntryId FROM line l JOIN line_toc lt ON lt.lineId = l.id WHERE l.bookId = ? AND l.lineIndex = ?",
            { bindLong(0, bookId); bindLong(1, lineIndex.toLong()) },
        ) { it.getLong(0) }.firstOrNull() ?: return emptyList()
        val trail = ArrayList<TocEntry>()
        var id: Long? = entryId
        while (id != null && trail.size < 12) {
            val entry = opened.query(
                """
                SELECT te.id, te.parentId, tt.text, te.level, te.lineId, te.isLastChild, te.hasChildren
                FROM tocEntry te JOIN tocText tt ON tt.id = te.textId WHERE te.id = ?
                """.trimIndent(),
                { bindLong(0, id!!) },
            ) {
                TocEntry(
                    id = it.getLong(0)!!, bookId = bookId, parentId = it.getLong(1), text = it.getString(2) ?: "",
                    level = it.getLong(3)!!.toInt(), lineId = it.getLong(4), isLastChild = it.getLong(5) == 1L,
                    hasChildren = it.getLong(6) == 1L,
                )
            }.firstOrNull() ?: break
            trail.add(0, entry)
            id = entry.parentId
        }
        return trail
    }

    private fun go(entry: TocEntry) {
        val lineId = entry.lineId ?: return
        scope.launch {
            val index = withContext(Dispatchers.IO) { runCatching { opened.lineIndex(lineId) }.getOrNull() } ?: return@launch
            setResult(RESULT_OK, Intent().putExtra(BookActivity.EXTRA_LINE, index))
            finish()
        }
    }
}

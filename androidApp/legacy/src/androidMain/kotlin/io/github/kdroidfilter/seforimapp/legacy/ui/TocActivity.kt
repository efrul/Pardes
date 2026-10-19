package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A book's table of contents, one level at a time (a phone's width has no room for desktop's tree): an entry with
 * children opens them, the others take the reader to their line. It opens at the place the reader is at.
 */
class TocActivity : BaseActivity() {
    private var bookId = 0L
    private lateinit var opened: Library.Opened
    private lateinit var crumbs: LinearLayout
    private lateinit var list: ListView

    /** The entries from the root down to the level shown. */
    private val path = ArrayList<TocEntry>()

    /** The entries from the root to the reader's place, marked in the lists. */
    private var current: Set<Long> = emptySet()
    private val adapter = ItemsAdapter<Row> { row, reuse, _ ->
        val view = reuse as? EntryRow ?: EntryRow(this, colors)
        when (row) {
            is Row.Start -> view.bind("לתחילת ${row.entry.text}", iconRes = R.drawable.ic_back, iconTint = colors.accent)
            is Row.Item -> view.bind(row.entry.text, leads = row.entry.hasChildren, marked = row.entry.id in current)
        }
        view
    }

    private sealed interface Row {
        data class Start(val entry: TocEntry) : Row

        data class Item(val entry: TocEntry) : Row
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookId = intent.getLongExtra(BookActivity.EXTRA_BOOK, 0L)
        val line = intent.getIntExtra(BookActivity.EXTRA_LINE, 0)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors) { finish() }
        bar.title.text = "תוכן העניינים: " + (intent.getStringExtra(BookActivity.EXTRA_TITLE) ?: "")
        root.addView(bar, lp(MATCH, WRAP))
        crumbs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(colors.ground)
            addView(crumbs)
        }, lp(MATCH, WRAP))
        list = ListView(this).apply {
            adapter = this@TocActivity.adapter
            setBackgroundColor(colors.panel)
            cacheColorHint = colors.panel
            divider = null
            setOnItemClickListener { _, _, position, _ ->
                when (val row = this@TocActivity.adapter.items[position]) {
                    is Row.Start -> go(row.entry)
                    is Row.Item -> if (row.entry.hasChildren) {
                        path += row.entry
                        show()
                    } else {
                        go(row.entry)
                    }
                }
            }
            setOnItemLongClickListener { _, _, position, _ ->
                (this@TocActivity.adapter.items[position] as? Row.Item)?.let { go(it.entry) }
                true
            }
        }
        root.addView(list, lp(MATCH, 0, 1f))
        setContentView(root)
        scope.launch {
            opened = withContext(Dispatchers.IO) { app.library.open() }
            // Open at the reader's place: its entry's parents become the path.
            val trail = withContext(Dispatchers.IO) { runCatching { trailTo(line) }.getOrDefault(emptyList()) }
            current = trail.map { it.id }.toSet()
            path.addAll(trail.dropLast(1))
            show()
        }
    }

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

    private fun show() {
        scope.launch {
            var entries = withContext(Dispatchers.IO) {
                runCatching {
                    path.lastOrNull()?.let { opened.repository.getTocChildren(it.id) } ?: opened.repository.getBookRootToc(bookId)
                }.getOrDefault(emptyList())
            }
            // A lone root (the book's own title heading) opens straight away.
            if (path.isEmpty() && entries.size == 1 && entries[0].hasChildren) {
                path += entries[0]
                entries = withContext(Dispatchers.IO) { runCatching { opened.repository.getTocChildren(entries[0].id) }.getOrDefault(emptyList()) }
            }
            val rows = ArrayList<Row>()
            path.lastOrNull()?.takeIf { it.lineId != null }?.let { rows += Row.Start(it) }
            entries.forEach { rows += Row.Item(it) }
            adapter.items = rows
            drawCrumbs()
            val at = rows.indexOfFirst { it is Row.Item && it.entry.id in current }
            list.setSelection(maxOf(0, at - 2))
        }
    }

    private fun drawCrumbs() {
        crumbs.removeAllViews()
        fun step(text: String, depth: Int, last: Boolean) {
            crumbs.addView(label(text, 14f, if (last) colors.text else colors.accent, bold = last, maxLines = 1).apply {
                setPadding(dp(6), dp(4), dp(6), dp(4))
                if (!last) {
                    background = pressable(0, colors.pressed)
                    setOnClickListener {
                        while (path.size > depth) path.removeAt(path.size - 1)
                        show()
                    }
                }
            })
            if (!last) crumbs.addView(label("›", 14f, colors.secondary))
        }
        step("ראשי", 0, path.isEmpty())
        path.forEachIndexed { i, e -> step(e.text, i + 1, i == path.size - 1) }
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

package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ListView
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Entry
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The library tree of the main app's drawer (LibraryTree): categories open in place, a category's books before its
 * subcategories. On a keypad, OK opens and closes a category, and the arrows do too: ← into it, → out of it, as the
 * tree reads from right to left.
 */
class LibraryActivity : BaseActivity() {
    private lateinit var opened: Library.Opened
    private lateinit var list: ListView

    /** A row of the flattened tree. */
    private class Node(val entry: Entry, val depth: Int, var open: Boolean = false)

    private val nodes = ArrayList<Node>()
    private val adapter = ItemsAdapter<Node> { node, reuse, _ ->
        val row = reuse as? TreeRow ?: TreeRow(this, colors)
        if (node.entry.isBook) {
            row.bind(node.entry.title, node.depth, R.drawable.ic_book, keepArrowSpace = true)
        } else {
            row.bind(node.entry.title, node.depth, R.drawable.ic_folder, if (node.open) R.drawable.ic_arrow_down else R.drawable.ic_arrow_left)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_back, "חזרה") { finish() }
            title.text = "ספרייה"
            action(R.drawable.ic_search, "חיפוש ספר") { startActivity(Intent(this@LibraryActivity, SearchActivity::class.java)) }
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
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
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
        card.addView(list, lp(MATCH, MATCH))
        root.addView(card, lp(MATCH, 0, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        setContentView(root)

        val focus = intent.getLongExtra(EXTRA_CATEGORY, 0L).takeIf { it != 0L }
        scope.launch {
            opened = withContext(Dispatchers.IO) { app.library.open() }
            val roots = withContext(Dispatchers.IO) { runCatching { opened.rootEntries() }.getOrDefault(emptyList()) }
            roots.forEach { nodes += Node(it, 0) }
            adapter.items = ArrayList(nodes)
            // Opened from a section of the Home: that section, open, at the top.
            if (focus != null) {
                val path = withContext(Dispatchers.IO) { runCatching { opened.categoryPath(focus) }.getOrDefault(emptyList()) }
                var at = -1
                for (step in path) {
                    val i = nodes.indexOfFirst { !it.entry.isBook && it.entry.id == step.id && (at < 0 || nodes.indexOf(it) > at) }
                    if (i < 0) break
                    if (!nodes[i].open) expand(i)
                    at = i
                }
                if (at >= 0) list.setSelection(at)
            }
        }
    }

    private fun activate(position: Int) {
        val node = nodes.getOrNull(position) ?: return
        if (node.entry.isBook) {
            BookActivity.start(this, node.entry.id, prefs.lastLine(node.entry.id), node.entry.title)
        } else {
            toggle(position)
        }
    }

    private fun toggle(position: Int) {
        val node = nodes.getOrNull(position) ?: return
        if (node.open) collapse(position) else scope.launch { expand(position) }
    }

    /** Opens the category at [position]: its books, then its subcategories, under it. */
    private suspend fun expand(position: Int) {
        val node = nodes.getOrNull(position) ?: return
        val children = withContext(Dispatchers.IO) { runCatching { opened.children(node.entry.id) }.getOrDefault(emptyList()) }
        node.open = true
        nodes.addAll(position + 1, children.map { Node(it, node.depth + 1) })
        adapter.items = ArrayList(nodes)
    }

    private fun collapse(position: Int) {
        val node = nodes[position]
        node.open = false
        var end = position + 1
        while (end < nodes.size && nodes[end].depth > node.depth) end++
        nodes.subList(position + 1, end).clear()
        adapter.items = ArrayList(nodes)
    }

    companion object {
        private const val EXTRA_CATEGORY = "category"

        fun start(context: Context, categoryId: Long?) {
            context.startActivity(Intent(context, LibraryActivity::class.java).putExtra(EXTRA_CATEGORY, categoryId ?: 0L))
        }
    }
}

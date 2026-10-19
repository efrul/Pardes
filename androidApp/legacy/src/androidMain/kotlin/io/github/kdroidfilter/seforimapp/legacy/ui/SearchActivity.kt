package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Entry
import io.github.kdroidfilter.seforimapp.legacy.data.Hit
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.TextSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search: book titles (and their acronyms) as one types, or the text of a book, a category or the whole library
 * (TextSearch) on Enter. Opened from a book for its find-in-book, a chosen line goes back to that book.
 */
class SearchActivity : BaseActivity() {
    private lateinit var opened: Library.Opened
    private lateinit var field: EditText
    private lateinit var modeTitles: TextView
    private lateinit var modeText: TextView
    private lateinit var textOptions: LinearLayout
    private lateinit var matchPhrase: TextView
    private lateinit var matchAll: TextView
    private lateinit var scopeLabel: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var stop: TextView
    private lateinit var list: ListView

    private var textMode = false

    /** The text search's match: the query as a phrase (the default), or its words anywhere in the line. */
    private var allWords = false
    private var where: Scope = Scope.Library
    private var titleJob: Job? = null
    private var searchJob: Job? = null
    private var search: TextSearch? = null
    private var lastQuery = ""

    private val titleAdapter = ItemsAdapter<Entry> { entry, reuse, _ ->
        (reuse as? EntryRow ?: EntryRow(this, colors)).bind(entry.title, iconRes = R.drawable.ic_book, iconTint = colors.secondary)
    }
    private val hitAdapter = ItemsAdapter<Hit> { hit, reuse, _ -> hitView(hit, reuse) }
    private var highlight: Regex? = null

    /** Where the text search looks. */
    private sealed class Scope(val label: String) {
        object Library : Scope("כל הספרייה")

        class Category(val id: Long, title: String) : Scope(title)

        class Book(val id: Long, val title: String) : Scope("הספר $title")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bookScope = intent.getLongExtra(EXTRA_BOOK_SCOPE, 0L)
        if (bookScope != 0L) {
            where = Scope.Book(bookScope, intent.getStringExtra(BookActivity.EXTRA_TITLE) ?: "")
            textMode = true
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors) { finish() }
        bar.title.text = if (bookScope != 0L) "חיפוש בספר" else "חיפוש"
        root.addView(bar, lp(MATCH, WRAP))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.panel)
            setPadding(dp(12), dp(10), dp(12), dp(6))
        }
        field = EditText(this).apply {
            hint = "מה לחפש?"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            textSize = 18f
            setTextColor(colors.text)
            setHintTextColor(colors.secondary)
            setText(intent.getStringExtra(EXTRA_QUERY) ?: "")
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event == null || event.action == KeyEvent.ACTION_DOWN) submit()
                    true
                } else {
                    false
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (!textMode) findTitles()
                }
            })
        }
        top.addView(field, lp(MATCH, WRAP))

        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modeTitles = modeChip("שמות ספרים") { setMode(false) }
        modeText = modeChip("טקסט") { setMode(true) }
        modes.addView(modeTitles, lp(WRAP, WRAP).apply { marginEnd = dp(6) })
        modes.addView(modeText, lp(WRAP, WRAP).apply { marginEnd = dp(6) })
        top.addView(modes, lp(MATCH, WRAP).apply { topMargin = dp(6) })

        textOptions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val match = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        matchPhrase = modeChip("ביטוי מדויק") { setMatch(false) }
        matchAll = modeChip("כל המילים") { setMatch(true) }
        match.addView(matchPhrase, lp(WRAP, WRAP).apply { marginEnd = dp(6) })
        match.addView(matchAll, lp(WRAP, WRAP))
        textOptions.addView(match, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        scopeLabel = label("", 14f, colors.accent, maxLines = 1).apply {
            setPadding(dp(4), dp(8), dp(4), dp(4))
            background = pressable(0, colors.pressed)
            setOnClickListener { chooseScope() }
        }
        textOptions.addView(scopeLabel, lp(MATCH, WRAP))
        top.addView(textOptions, lp(MATCH, WRAP))

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        status = label("", 13f, colors.secondary, maxLines = 2)
        statusRow.addView(status, lp(0, WRAP, 1f))
        stop = label("עצור", 14f, colors.accent, bold = true).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = pressable(0, colors.pressed)
            visibility = View.GONE
            setOnClickListener { stopSearch("החיפוש נעצר") }
        }
        statusRow.addView(stop)
        top.addView(statusRow, lp(MATCH, WRAP).apply { topMargin = dp(4) })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { visibility = View.GONE }
        top.addView(progress, lp(MATCH, WRAP))
        root.addView(top, lp(MATCH, WRAP))
        root.addView(divider(colors))

        list = ListView(this).apply {
            setBackgroundColor(colors.panel)
            cacheColorHint = colors.panel
            divider = null
            setOnItemClickListener { _, _, position, _ -> open(position) }
        }
        root.addView(list, lp(MATCH, 0, 1f))
        setContentView(root)

        setMode(textMode)
        scope.launch {
            opened = withContext(Dispatchers.IO) { app.library.open() }
            search = TextSearch(opened)
            if (!textMode) findTitles()
        }
    }

    override fun onDestroy() {
        search?.cancel()
        super.onDestroy()
    }

    private fun modeChip(text: String, onClick: () -> Unit): TextView = label(text, 14f, colors.text).apply {
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener { onClick() }
    }

    private fun paintChips(vararg chips: Pair<TextView, Boolean>) {
        val radius = dp(16).toFloat()
        chips.forEach { (chip, on) ->
            chip.setTextColor(if (on) colors.panel else colors.text)
            chip.background = if (on) roundRect(colors.accent, radius) else roundRect(colors.panel, radius, colors.border, dp(1))
        }
    }

    private fun setMatch(all: Boolean) {
        allWords = all
        paintChips(matchPhrase to !all, matchAll to all)
        stopSearch(null)
    }

    private fun setMode(text: Boolean) {
        textMode = text
        paintChips(modeTitles to !text, modeText to text)
        paintChips(matchPhrase to !allWords, matchAll to allWords)
        textOptions.visibility = if (text) View.VISIBLE else View.GONE
        scopeLabel.text = "בתוך: ${where.label} ▾"
        stopSearch(null)
        if (text) {
            list.adapter = hitAdapter
            status.text = "הקישו על חיפוש במקלדת כדי לחפש בטקסט"
        } else {
            list.adapter = titleAdapter
            status.text = ""
            if (::opened.isInitialized) findTitles()
        }
    }

    private fun chooseScope() {
        scope.launch {
            val fromBook = intent.getLongExtra(EXTRA_BOOK_SCOPE, 0L)
            val choices = withContext(Dispatchers.IO) {
                val list = ArrayList<Scope>()
                if (fromBook != 0L) {
                    list += Scope.Book(fromBook, intent.getStringExtra(BookActivity.EXTRA_TITLE) ?: "")
                    runCatching { opened.book(fromBook) }.getOrNull()?.let { info ->
                        opened.categoryTitle(info.categoryId)?.let { list += Scope.Category(info.categoryId, it) }
                    }
                }
                list += Scope.Library
                runCatching { opened.rootEntries() }.getOrDefault(emptyList()).forEach { list += Scope.Category(it.id, it.title) }
                list
            }
            AlertDialog.Builder(this@SearchActivity)
                .setTitle("איפה לחפש")
                .setItems(choices.map { it.label }.toTypedArray()) { _, which ->
                    where = choices[which]
                    scopeLabel.text = "בתוך: ${where.label} ▾"
                    stopSearch(null)
                }
                .show()
        }
    }

    private fun submit() {
        if (textMode) startTextSearch() else findTitles()
    }

    private fun findTitles() {
        if (!::opened.isInitialized) return
        val q = field.text.toString().trim()
        titleJob?.cancel()
        titleJob = scope.launch {
            delay(200)
            val found = if (q.isEmpty()) emptyList() else withContext(Dispatchers.IO) { runCatching { opened.findBooks(q) }.getOrDefault(emptyList()) }
            titleAdapter.items = found
            status.text = when {
                q.isEmpty() -> ""
                found.isEmpty() -> "לא נמצא ספר בשם הזה. לחיפוש בטקסט הספרים בחרו \"טקסט\"."
                else -> "${found.size} ספרים"
            }
        }
    }

    private fun startTextSearch() {
        val engine = search ?: return
        val q = field.text.toString().trim()
        if (q.isEmpty()) return
        stopSearch(null)
        lastQuery = q
        val all = allWords
        highlight = LineText.highlighter(q, phrase = !all)
        hitAdapter.items = emptyList()
        val hits = ArrayList<Hit>()
        progress.visibility = View.VISIBLE
        stop.visibility = View.VISIBLE
        status.text = "מכין את החיפוש…"
        val target = where
        searchJob = scope.launch {
            val books = withContext(Dispatchers.IO) {
                runCatching {
                    when (target) {
                        is Scope.Book -> listOf(Entry(target.id, target.title, isBook = true))
                        is Scope.Category -> opened.booksUnder(target.id)
                        Scope.Library -> opened.booksUnder(null)
                    }
                }.getOrDefault(emptyList())
                    // The base books first (the Torah, the Mishnah, the Talmud...), as desktop's results open with them.
                    .sortedByDescending { it.base }
            }
            progress.max = maxOf(1, books.size)
            val started = System.currentTimeMillis()
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    engine.run(
                        q, books, MAX_HITS, all,
                        onBook = { i, book ->
                            withContext(Dispatchers.Main) {
                                progress.progress = i
                                status.text = "מחפש ב${book.title} (${i + 1} מתוך ${books.size}) · ${hits.size} תוצאות"
                            }
                        },
                        onHits = { found ->
                            withContext(Dispatchers.Main) {
                                hits += found
                                hitAdapter.items = ArrayList(hits)
                            }
                        },
                    )
                }
            }
            progress.visibility = View.GONE
            stop.visibility = View.GONE
            val seconds = (System.currentTimeMillis() - started) / 1000
            status.text = when {
                found.isFailure -> "החיפוש נכשל: ${found.exceptionOrNull()?.message}"
                hits.isEmpty() -> "לא נמצאו תוצאות ב${target.label}"
                hits.size >= MAX_HITS -> "נמצאו ${hits.size} תוצאות ראשונות (החיפוש נעצר שם). צמצמו את החיפוש לספר או לקטגוריה."
                else -> "נמצאו ${hits.size} תוצאות ב${target.label}" + if (seconds > 1) " · $seconds שניות" else ""
            }
        }
    }

    private fun stopSearch(message: String?) {
        val job = searchJob ?: return
        if (job.isActive) {
            job.cancel()
            search?.cancel()
            progress.visibility = View.GONE
            stop.visibility = View.GONE
            if (message != null) status.text = "$message · ${hitAdapter.count} תוצאות"
        }
        searchJob = null
    }

    private fun open(position: Int) {
        if (textMode) {
            val hit = hitAdapter.items.getOrNull(position) ?: return
            if (callingActivity != null && intent.getLongExtra(EXTRA_BOOK_SCOPE, 0L) == hit.bookId) {
                setResult(
                    RESULT_OK,
                    Intent().putExtra(BookActivity.EXTRA_LINE, hit.lineIndex).putExtra(BookActivity.EXTRA_FIND, lastQuery)
                        .putExtra(BookActivity.EXTRA_FIND_WORDS, allWords),
                )
                finish()
            } else {
                BookActivity.start(this, hit.bookId, hit.lineIndex, hit.bookTitle, lastQuery, allWords)
            }
        } else {
            val entry = titleAdapter.items.getOrNull(position) ?: return
            BookActivity.start(this, entry.id, prefs.lastLine(entry.id), entry.title)
        }
    }

    /** A hit: its book, and the line around the first word found, the words marked. */
    private fun hitView(hit: Hit, reuse: View?): View {
        val box = reuse as? LinearLayout ?: LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
            background = pressable(colors.panel, colors.pressed)
            addView(label("", 13f, colors.accent, bold = true, maxLines = 1))
            addView(label("", 16f, colors.text, maxLines = 4).apply { typeface = Fonts.book(this@SearchActivity) })
        }
        (box.getChildAt(0) as TextView).text = "${hit.bookTitle} · שורה ${hit.lineIndex + 1}"
        (box.getChildAt(1) as TextView).text = snippet(hit.content)
        return box
    }

    private fun snippet(html: String): CharSequence {
        val text = LineText.render(html, prefs.diacritics)
        val first = highlight?.find(text)
        val from = first?.let { maxOf(0, it.range.first - SNIPPET_BEFORE) } ?: 0
        val clipped = if (from > 0) SpannableStringBuilder("…").append(text.subSequence(from, text.length)) else text
        val out = SpannableStringBuilder(clipped)
        LineText.highlight(out, highlight, colors.searchHit)
        return out
    }

    companion object {
        const val EXTRA_QUERY = "query"
        const val EXTRA_BOOK_SCOPE = "book_scope"
        private const val MAX_HITS = 500
        private const val SNIPPET_BEFORE = 60
    }
}

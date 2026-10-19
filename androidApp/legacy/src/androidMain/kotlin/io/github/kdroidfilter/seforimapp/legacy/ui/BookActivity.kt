package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.ui.reader.LineConnections
import io.github.kdroidfilter.seforimapp.android.ui.reader.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.BookInfo
import io.github.kdroidfilter.seforimapp.legacy.data.Diacritics
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.Place
import io.github.kdroidfilter.seforimapp.legacy.data.Prefs
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A book: its lines in a list read page by page as they scroll into view, the heading of the place shown, the table of
 * contents, and under the text the commentaries, targumim and sources of the line tapped (the main app's reader
 * panes, as tabs over one pane).
 */
class BookActivity : BaseActivity() {
    private var bookId = 0L
    private var book: BookInfo? = null
    private lateinit var opened: Library.Opened
    private lateinit var connections: LineConnections
    private lateinit var bar: TopBar
    private lateinit var heading: TextView
    private lateinit var list: ListView
    private lateinit var frame: FrameLayout
    private lateinit var panel: LinksPanel
    private val adapter = LinesAdapter()

    /** The line tapped, whose links the pane shows; -1 for none. */
    private var selected = -1
    private var highlight: Regex? = null
    private var headingJob: Job? = null
    private var defaultCommentators: List<Long> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookId = intent.getLongExtra(EXTRA_BOOK, 0L)
        val startLine = savedInstanceState?.getInt(STATE_TOP) ?: intent.getIntExtra(EXTRA_LINE, -1).takeIf { it >= 0 } ?: prefs.lastLine(bookId) ?: 0
        selected = savedInstanceState?.getInt(STATE_SELECTED, -1) ?: -1
        highlight = intent.getStringExtra(EXTRA_FIND)?.let { LineText.highlighter(it, phrase = !intent.getBooleanExtra(EXTRA_FIND_WORDS, false)) }
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bar = TopBar(this, colors) { finish() }.apply {
            title.text = intent.getStringExtra(EXTRA_TITLE) ?: ""
            action(R.drawable.ic_toc, "תוכן העניינים") { openToc() }
            action(R.drawable.ic_comment, "פירושים") { togglePanel() }
            overflow { menuItems() }
        }
        root.addView(bar, lp(MATCH, WRAP))
        heading = label(sizeSp = 13f, color = colors.secondary, maxLines = 1).apply {
            setBackgroundColor(colors.panel)
            setPadding(dp(14), dp(5), dp(14), dp(5))
        }
        root.addView(heading, lp(MATCH, WRAP))
        root.addView(divider(colors))
        frame = FrameLayout(this)
        list = ListView(this).apply {
            divider = null
            setBackgroundColor(colors.panel)
            cacheColorHint = colors.panel
            selector = pressable(0, colors.pressed)
            isFastScrollEnabled = true
            adapter = this@BookActivity.adapter
            setOnItemClickListener { _, _, position, _ -> select(position) }
            setOnItemLongClickListener { _, _, position, _ ->
                lineActions(position)
                true
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
                    if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) updateHeading()
                }

                override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) = Unit
            })
        }
        frame.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(frame, lp(MATCH, 0, 1f))
        panel = LinksPanel()
        root.addView(panel.root, lp(MATCH, 0, 0f))
        setContentView(root)
        panel.setVisible(false)

        val waiting = ProgressBar(this)
        frame.addView(waiting, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val lib = app.library.open()
                    val info = lib.book(bookId) ?: error("הספר לא נמצא בספרייה")
                    val defaults = runCatching { lib.repository.getDefaultCommentatorIdsForBook(bookId) }.getOrDefault(emptyList())
                    Triple(lib, info, defaults)
                }
            }
            frame.removeView(waiting)
            result.onFailure { e ->
                toast(e.message ?: "לא ניתן לפתוח את הספר")
                finish()
            }.onSuccess { (lib, info, defaults) ->
                opened = lib
                book = info
                defaultCommentators = defaults
                connections = LineConnections(lib.repository) { info.title }
                bar.title.text = info.title
                adapter.total = info.totalLines.takeIf { it > 0 } ?: withContext(Dispatchers.IO) { lib.lineCount(bookId) }
                list.setSelection(startLine.coerceIn(0, maxOf(0, adapter.total - 1)))
                list.post { updateHeading() }
                if (selected >= 0) select(selected)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TOP, list.firstVisiblePosition)
        outState.putInt(STATE_SELECTED, selected)
    }

    override fun onPause() {
        super.onPause()
        val info = book ?: return
        prefs.rememberPlace(Place(info.id, info.title, list.firstVisiblePosition))
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
        if (line >= 0) jumpTo(line, select = requestCode == REQUEST_FIND)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (panel.visible) {
            panel.setVisible(false)
            select(-1)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun jumpTo(index: Int, select: Boolean) {
        list.setSelection(index)
        if (select) {
            select(index)
        } else if (selected >= 0) {
            // A place chosen in the table of contents: the pane of a line left behind closes.
            panel.setVisible(false)
            select(-1)
        }
        list.post { updateHeading() }
    }

    // ------------------------------------------------------------------ menu

    private fun menuItems(): List<Pair<String, () -> Unit>> = listOf(
        "חיפוש בספר" to { findInBook() },
        "הוסף סימנייה" to { addBookmark() },
        "גודל הטקסט" to { textSizeDialog() },
        "ניקוד: ${prefs.diacritics.label}" to { diacriticsDialog() },
        "העתק קישור לשורה" to { copyLink(if (selected >= 0) selected else list.firstVisiblePosition) },
        "פרטי הספר" to { bookDetails() },
    )

    private fun openToc() {
        val info = book ?: return
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, TocActivity::class.java).putExtra(EXTRA_BOOK, info.id).putExtra(EXTRA_TITLE, info.title)
                .putExtra(EXTRA_LINE, list.firstVisiblePosition),
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

    private fun addBookmark() {
        val info = book ?: return
        val index = if (selected >= 0) selected else list.firstVisiblePosition
        val text = adapter.lineAt(index)?.let { LineText.plain(it.content).take(80) } ?: ""
        prefs.addBookmark(Place(info.id, info.title, index, text))
        toast("נוספה סימנייה")
    }

    private fun textSizeDialog() {
        val seek = SeekBar(this).apply {
            max = Prefs.MAX_TEXT_SIZE - Prefs.MIN_TEXT_SIZE
            progress = prefs.textSize - Prefs.MIN_TEXT_SIZE
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    prefs.textSize = Prefs.MIN_TEXT_SIZE + progress
                    adapter.notifyDataSetChanged()
                    panel.restyle()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        AlertDialog.Builder(this).setTitle("גודל הטקסט").setView(seek).setPositiveButton("סגור", null).show()
    }

    private fun diacriticsDialog() {
        val modes = Diacritics.entries
        AlertDialog.Builder(this)
            .setTitle("ניקוד וטעמים")
            .setSingleChoiceItems(modes.map { it.label }.toTypedArray(), modes.indexOf(prefs.diacritics)) { dialog, which ->
                prefs.diacritics = modes[which]
                adapter.clearRendered()
                panel.restyle()
                dialog.dismiss()
            }
            .show()
    }

    private fun copyLink(index: Int) {
        val line = adapter.lineAt(index) ?: return
        copy("zayit://book/$bookId/line/${line.id}")
        toast("הקישור הועתק")
    }

    private fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("פרדס", text))
    }

    private fun bookDetails() {
        val info = book ?: return
        scope.launch {
            val details = withContext(Dispatchers.IO) {
                runCatching {
                    val path = opened.categoryPath(info.categoryId).joinToString(" › ") { it.title }
                    val authors = opened.repository.getBookCore(info.id)?.authors.orEmpty().joinToString(", ") { it.name }
                    path to authors
                }.getOrDefault("" to "")
            }
            val text = buildString {
                append(info.title)
                if (details.first.isNotEmpty()) append("\n\n").append(details.first)
                if (details.second.isNotEmpty()) append("\n\nמחבר: ").append(details.second)
                append("\n\n").append(info.totalLines).append(" שורות")
                info.shortDescription?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
            }
            AlertDialog.Builder(this@BookActivity).setTitle("פרטי הספר").setMessage(text).setPositiveButton("סגור", null).show()
        }
    }

    private fun lineActions(index: Int) {
        val line = adapter.lineAt(index) ?: return
        val info = book ?: return
        val items = arrayOf("העתק את הטקסט", "הוסף סימנייה", "העתק קישור לשורה", "פירושים ומקורות")
        AlertDialog.Builder(this).setItems(items) { _, which ->
            when (which) {
                0 -> {
                    copy(LineText.plain(LineText.stripMarks(line.content, prefs.diacritics)))
                    toast("הטקסט הועתק")
                }
                1 -> {
                    prefs.addBookmark(Place(info.id, info.title, index, LineText.plain(line.content).take(80)))
                    toast("נוספה סימנייה")
                }
                2 -> copyLink(index)
                3 -> select(index)
            }
        }.show()
    }

    // ------------------------------------------------------------------ heading

    /** "בראשית › פרק א": the table of contents' path to the first line shown. */
    private fun updateHeading() {
        if (book == null) return
        val index = list.firstVisiblePosition
        headingJob?.cancel()
        headingJob = scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { tocPath(index) }.getOrDefault("") }
            heading.text = text.ifEmpty { book?.title ?: "" }
        }
    }

    private fun tocPath(index: Int): String {
        val entry = opened.query(
            """
            SELECT lt.tocEntryId FROM line l JOIN line_toc lt ON lt.lineId = l.id
            WHERE l.bookId = ? AND l.lineIndex = ?
            """.trimIndent(),
            { bindLong(0, bookId); bindLong(1, index.toLong()) },
        ) { it.getLong(0) }.firstOrNull() ?: return ""
        val parts = ArrayList<String>()
        var id: Long? = entry
        while (id != null && parts.size < 8) {
            val row = opened.query(
                "SELECT te.parentId, tt.text FROM tocEntry te JOIN tocText tt ON tt.id = te.textId WHERE te.id = ?",
                { bindLong(0, id!!) },
            ) { it.getLong(0) to (it.getString(1) ?: "") }.firstOrNull() ?: break
            parts.add(0, row.second)
            id = row.first
        }
        if (parts.size > 1 && parts.first() == book?.title) parts.removeAt(0)
        return parts.joinToString(" › ")
    }

    // ------------------------------------------------------------------ selection and the links pane

    private fun select(index: Int) {
        selected = index
        adapter.notifyDataSetChanged()
        if (index < 0) return
        panel.setVisible(true)
        val line = adapter.lineAt(index)
        if (line != null) panel.load(line) else panel.waitFor(index)
    }

    private fun togglePanel() {
        if (panel.visible) {
            panel.setVisible(false)
            select(-1)
        } else {
            select(if (selected >= 0) selected else list.firstVisiblePosition)
        }
    }

    private enum class Kind(val label: String) { Commentary("מפרשים"), Targum("תרגומים"), Sources("מקורות") }

    /** The pane under the text: tabs, the names to choose from, and the chosen one's text for the line. */
    private inner class LinksPanel {
        val root = LinearLayout(this@BookActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.ground)
        }
        private val tabs = LinearLayout(this@BookActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        private val chips = LinearLayout(this@BookActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(6))
        }
        private val body = LinearLayout(this@BookActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(16))
        }
        private val chipsScroll = HorizontalScrollView(this@BookActivity).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        private val bodyScroll = ScrollView(this@BookActivity).apply {
            setBackgroundColor(colors.panel)
            addView(body)
        }
        var visible = false
            private set
        private var snapshot: LineConnectionsSnapshot? = null
        private var line: Line? = null
        private var kind = Kind.Commentary
        private var chosen: Long? = null
        private var waitingFor = -1
        private var loadJob: Job? = null

        init {
            root.addView(View(this@BookActivity).apply { setBackgroundColor(colors.accent) }, lp(MATCH, dp(2)))
            val head = LinearLayout(this@BookActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(tabs, lp(0, WRAP, 1f))
            head.addView(iconButton(R.drawable.ic_close, colors.secondary, "סגור") {
                setVisible(false)
                select(-1)
            })
            root.addView(head, lp(MATCH, WRAP))
            root.addView(chipsScroll, lp(MATCH, WRAP))
            root.addView(bodyScroll, lp(MATCH, 0, 1f))
        }

        fun setVisible(show: Boolean) {
            visible = show
            root.visibility = if (show) View.VISIBLE else View.GONE
            (root.layoutParams as? LinearLayout.LayoutParams)?.let {
                it.weight = if (show) 0.9f else 0f
                root.layoutParams = it
            }
        }

        /** The line was not read yet: load it with its page. */
        fun waitFor(index: Int) {
            waitingFor = index
            message("טוען…")
        }

        fun onLineLoaded(index: Int) {
            if (index == waitingFor) {
                waitingFor = -1
                adapter.lineAt(index)?.let(::load)
            }
        }

        fun load(line: Line) {
            this.line = line
            loadJob?.cancel()
            message("טוען…")
            loadJob = scope.launch {
                val snap = withContext(Dispatchers.IO) { runCatching { connections.of(line.id) }.getOrNull() }
                snapshot = snap
                if (snap == null) {
                    message("לא ניתן לקרוא את הקישורים של השורה")
                    return@launch
                }
                val kinds = available(snap)
                if (kinds.isEmpty()) {
                    tabs.removeAllViews()
                    chips.removeAllViews()
                    message("אין לשורה זו מפרשים, תרגומים או מקורות")
                    return@launch
                }
                if (kind !in kinds) kind = kinds.first()
                showKind(kinds)
            }
        }

        fun restyle() {
            val l = line ?: return
            if (snapshot != null) showKind(available(snapshot!!)) else load(l)
        }

        private fun available(snap: LineConnectionsSnapshot): List<Kind> = buildList {
            if (snap.hasCommentaries) add(Kind.Commentary)
            if (snap.targumSources.isNotEmpty()) add(Kind.Targum)
            if (snap.sources.isNotEmpty()) add(Kind.Sources)
        }

        private fun names(snap: LineConnectionsSnapshot, kind: Kind): List<Pair<String, Long>> = when (kind) {
            Kind.Commentary -> snap.commentatorGroups.flatMap { g -> g.commentators.map { it.name to it.bookId } }
            Kind.Targum -> snap.targumSources.map { it.key to it.value }
            Kind.Sources -> snap.sources.map { it.key to it.value }
        }

        private fun showKind(kinds: List<Kind>) {
            val snap = snapshot ?: return
            tabs.removeAllViews()
            kinds.forEach { k ->
                val count = names(snap, k).size
                tabs.addView(label("${k.label} ($count)", 15f, if (k == kind) colors.accent else colors.secondary, bold = k == kind).apply {
                    setPadding(dp(12), dp(10), dp(12), dp(8))
                    background = pressable(0, colors.pressed)
                    setOnClickListener {
                        kind = k
                        showKind(kinds)
                    }
                })
            }
            val list = names(snap, kind)
            val ids = list.map { it.second }
            if (chosen !in ids) chosen = defaultCommentators.firstOrNull { it in ids } ?: ids.firstOrNull()
            chips.removeAllViews()
            if (kind == Kind.Commentary) {
                snap.commentatorGroups.forEach { group ->
                    if (group.label.isNotEmpty() && snap.commentatorGroups.size > 1) {
                        chips.addView(label(group.label, 12f, colors.secondary).apply { setPadding(dp(8), 0, dp(4), 0) })
                    }
                    group.commentators.forEach { addChip(it.name, it.bookId) }
                }
            } else {
                list.forEach { (name, id) -> addChip(name, id) }
            }
            revealChosen()
            loadTexts()
        }

        /**
         * Scrolls the names to the chosen one. Android 4.4's HorizontalScrollView starts at the left whatever the
         * direction, which in a right-to-left row is its end.
         */
        private fun revealChosen() {
            val chip = chips.findViewWithTag<View>(chosen)
            chips.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    @Suppress("DEPRECATION")
                    chips.viewTreeObserver.removeGlobalOnLayoutListener(this)
                    val max = maxOf(0, chips.width - chipsScroll.width)
                    val target = if (chip == null) max else (chip.left + chip.right) / 2 - chipsScroll.width / 2
                    chipsScroll.scrollTo(target.coerceIn(0, max), 0)
                }
            })
        }

        private fun addChip(name: String, id: Long) {
            val on = id == chosen
            val radius = dp(16).toFloat()
            chips.addView(label(name, 14f, if (on) colors.panel else colors.text).apply {
                tag = id
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = if (on) roundRect(colors.accent, radius) else pressable(roundRect(colors.panel, radius, colors.border, dp(1)), roundRect(colors.pressed, radius))
                setOnClickListener {
                    chosen = id
                    snapshot?.let { showKind(available(it)) }
                }
            }, lp(WRAP, WRAP).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) })
        }

        private fun loadTexts() {
            val l = line ?: return
            val target = chosen ?: return
            val k = kind
            loadJob?.cancel()
            message("טוען…")
            loadJob = scope.launch {
                val texts = withContext(Dispatchers.IO) {
                    runCatching {
                        val base = connections.baseLineIds(l.id).ifEmpty { listOf(l.id) }
                        opened.repository.getCommentariesForLines(base, setOf(target), includeSources = k == Kind.Sources)
                            .filter { it.link.targetBookId == target && it.link.connectionType == typeOf(k) }
                            .distinctBy { it.link.targetLineId }
                    }.getOrNull()
                }
                when {
                    texts == null -> message("לא ניתן לקרוא את הטקסט")
                    texts.isEmpty() -> message("אין טקסט לשורה זו")
                    else -> showTexts(texts, k)
                }
            }
        }

        private fun typeOf(k: Kind): ConnectionType = when (k) {
            Kind.Commentary -> ConnectionType.COMMENTARY
            Kind.Targum -> ConnectionType.TARGUM
            Kind.Sources -> ConnectionType.SOURCE
        }

        private fun showTexts(texts: List<CommentaryWithText>, k: Kind) {
            body.removeAllViews()
            val size = prefs.textSize * 0.85f
            val font = if (k == Kind.Targum) Fonts.targum(this@BookActivity) else Fonts.commentary(this@BookActivity)
            texts.forEach { c ->
                body.addView(TextView(this@BookActivity).apply {
                    text = LineText.render(c.targetText, prefs.diacritics)
                    typeface = font
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                    setTextColor(colors.text)
                    setLineSpacing(0f, 1.2f)
                    setPadding(dp(4), dp(6), dp(4), dp(10))
                    background = pressable(0, colors.pressed)
                    setOnClickListener { start(this@BookActivity, c.link.targetBookId, c.link.targetLineIndex) }
                })
            }
            body.addView(label("לחיצה על הטקסט פותחת את הספר במקומו", 12f, colors.secondary).apply { setPadding(dp(4), dp(4), dp(4), 0) })
            bodyScroll.scrollTo(0, 0)
        }

        private fun message(text: String) {
            body.removeAllViews()
            body.addView(label(text, 14f, colors.secondary).apply { setPadding(dp(4), dp(10), dp(4), dp(10)) })
        }
    }

    // ------------------------------------------------------------------ the lines

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
        private val loading = HashSet<Int>()

        fun lineAt(index: Int): Line? = pages[index / PAGE]?.getOrNull(index % PAGE)

        fun clearRendered() {
            rendered.clear()
            notifyDataSetChanged()
        }

        override fun getCount(): Int = total

        override fun getItem(position: Int): Any? = lineAt(position)

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView as? TextView ?: TextView(this@BookActivity).apply {
                setTextColor(colors.text)
                setLineSpacing(0f, 1.25f)
            }
            view.typeface = Fonts.book(this@BookActivity)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.textSize.toFloat())
            val line = lineAt(position)
            if (line == null) {
                view.text = "…"
                view.gravity = Gravity.CENTER
                view.setPadding(dp(16), dp(8), dp(16), dp(8))
                request(position / PAGE)
            } else {
                val heading = LineText.isHeading(line.content)
                view.text = rendered.getOrPut(position) {
                    LineText.render(line.content, prefs.diacritics).also { text ->
                        LineText.highlight(text, highlight, colors.searchHit)
                    }
                }
                view.gravity = if (heading) Gravity.CENTER_HORIZONTAL else Gravity.START
                view.setTextColor(if (heading) colors.accent else colors.text)
                view.setPadding(dp(16), dp(if (heading) 14 else 5), dp(16), dp(if (heading) 6 else 5))
            }
            view.setBackgroundColor(if (position == selected) colors.selected else 0)
            // Hebrew lines are right to left; a line of Latin letters only keeps its own direction.
            view.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            return view
        }

        private fun request(page: Int) {
            if (!loading.add(page)) return
            val start = page * PAGE
            scope.launch {
                val lines = withContext(Dispatchers.IO) {
                    runCatching { opened.repository.getLines(bookId, start, start + PAGE - 1) }.getOrNull()
                }
                loading.remove(page)
                if (lines != null) {
                    pages[page] = lines
                    notifyDataSetChanged()
                    if (selected in start until start + PAGE) panel.onLineLoaded(selected)
                }
            }
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
        private const val REQUEST_TOC = 1
        private const val REQUEST_FIND = 2
        private const val PAGE = 50
        private const val MAX_PAGES = 40
        private const val MAX_RENDERED = 300

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

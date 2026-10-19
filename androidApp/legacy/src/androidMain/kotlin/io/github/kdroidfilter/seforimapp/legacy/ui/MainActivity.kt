package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Entry
import io.github.kdroidfilter.seforimapp.legacy.data.Library
import io.github.kdroidfilter.seforimapp.legacy.data.NoLibraryException
import io.github.kdroidfilter.seforimapp.legacy.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The home screen: the library tree (the main app's library drawer), a search field, and the books opened lately. With
 * no library it says where to copy one. It also takes the desktop's links (zayit://book/<id>/line/<id>, zayit://search).
 */
class MainActivity : BaseActivity() {
    private lateinit var bar: TopBar
    private lateinit var content: LinearLayout
    private val adapter = ItemsAdapter<Entry> { entry, reuse, _ -> rowFor(entry, reuse) }
    private var listView: ListView? = null
    private var header: LinearLayout? = null

    /** The categories from the root to the one shown; empty at the root. */
    private val path = ArrayList<Entry>()
    private var loadJob: Job? = null
    private var pendingLink: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getLongArray(STATE_PATH)?.let { ids ->
            val titles = savedInstanceState.getStringArray(STATE_TITLES).orEmpty()
            ids.forEachIndexed { i, id -> path += Entry(id, titles.getOrElse(i) { "" }, isBook = false) }
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bar = TopBar(this, colors, onBack = null).apply {
            title.text = getString(R.string.app_name)
            title.typeface = Fonts.logo(this@MainActivity)
            title.textSize = 24f
            action(R.drawable.ic_search, "חיפוש") { openSearch() }
            action(R.drawable.ic_settings, "הגדרות") { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
            overflow {
                listOf(
                    "היסטוריה" to { showPlaces("ספרים אחרונים", prefs.recents, bookmarks = false) },
                    "סימניות" to { showPlaces("סימניות", prefs.bookmarks, bookmarks = true) },
                    "חפש את הספרייה מחדש" to { reload() },
                )
            }
        }
        root.addView(bar, lp(MATCH, WRAP))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, lp(MATCH, 0, 1f))
        setContentView(root)
        if (savedInstanceState == null) pendingLink = intent?.data
        load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let {
            pendingLink = it
            handleLink()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLongArray(STATE_PATH, path.map { it.id }.toLongArray())
        outState.putStringArray(STATE_TITLES, path.map { it.title }.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        // The books opened lately change while a book is open.
        if (app.library.current != null && path.isEmpty()) header?.let { fillHeader(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (path.isNotEmpty() && app.library.current != null) {
            path.removeAt(path.size - 1)
            showLevel()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun reload() {
        app.library.close()
        path.clear()
        load()
    }

    private fun load() {
        loadJob?.cancel()
        showMessage(progress = true, title = "פותח את הספרייה…")
        loadJob = scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { app.library.open() } }
            result.onSuccess {
                showLibrary()
                handleLink()
            }.onFailure { error ->
                if (error is NoLibraryException) showNoLibrary() else showError(error)
            }
        }
    }

    // ------------------------------------------------------------------ states

    private fun showMessage(progress: Boolean, title: String, body: String? = null, action: Pair<String, () -> Unit>? = null) {
        content.removeAllViews()
        listView = null
        header = null
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        if (progress) box.addView(ProgressBar(this), lp(WRAP, WRAP).apply { bottomMargin = dp(16) })
        box.addView(label(title, 18f, colors.text, bold = !progress).apply { gravity = Gravity.CENTER })
        if (body != null) box.addView(label(body, 15f, colors.secondary).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
        if (action != null) box.addView(button(action.first, action.second), lp(WRAP, WRAP).apply { topMargin = dp(16) })
        content.addView(box, lp(MATCH, MATCH))
    }

    private fun showError(error: Throwable) {
        showMessage(
            progress = false,
            title = "לא ניתן לפתוח את הספרייה",
            body = error.message ?: error.javaClass.simpleName,
            action = "נסה שוב" to { reload() },
        )
    }

    /** Where to copy a library: the desktop's databases folder, into the app's folder on the phone or a card. */
    private fun showNoLibrary() {
        content.removeAllViews()
        listView = null
        header = null
        val folder = app.library.prepareFolder()
        val cards = app.library.locations().drop(1).filter { it.label == Library.CARD_LABEL }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val logo = ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }
        text.addView(logo, lp(dp(72), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12) })
        text.addView(label("ברוכים הבאים לפרדס!", 22f, colors.text, bold = true).apply { gravity = Gravity.CENTER })
        text.addView(
            label(ABOUT_LINE, 14f, colors.secondary).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(16))
            },
        )
        text.addView(label("עדיין אין ספרייה במכשיר", 18f, colors.accent, bold = true))
        val steps = buildString {
            append("פרדס קורא את הספרייה של זית למחשב. כדי להעביר אותה:\n\n")
            append("1. במחשב, מצאו את תיקיית הספרייה של זית למחשב, databases. ב־Windows היא:\n")
            append(Bidi.ltr("%APPDATA%\\io.github.kdroidfilter.seforimapp\\databases")).append("\n\n")
            append("2. חברו את הטלפון למחשב בכבל USB, במצב העברת קבצים (MTP).\n\n")
            append("3. העתיקו את הקובץ seforim.db (ואת שאר הקבצים שלצדו) לתיקייה:\n")
            append(folder?.path?.let(::shortPath) ?: "Android/data/io.github.efrul.pardes/files/library")
            if (cards.isNotEmpty()) {
                append("\nאו, בכרטיס הזיכרון, לתיקייה:\n")
                append(shortPath(cards.first().dir.path))
            }
            append("\n\n4. חזרו לפרדס ולחצו על \"חפש שוב\".\n\n")
            append("הספרייה המלאה תופסת כ־8 ג׳יגה־בייט. בטלפון עם מעט מקום אפשר להעביר ספרייה קטנה של ספרים נבחרים, ")
            append("שבונה הכלי Zayit Subset במחשב. בכרטיס זיכרון בפורמט FAT32 אין מקום לקובץ של 8 ג׳יגה־בייט: ")
            append("את הספרייה המלאה שומרים שם מחולקת לחלקים (seforim.db, seforim.db001…), כפי שפרדס לאנדרואיד 8 ומעלה מחלק אותה.")
        }
        text.addView(label(steps, 15f, colors.text).apply { setPadding(0, dp(8), 0, dp(16)); setLineSpacing(0f, 1.15f) })
        text.addView(button("חפש שוב") { reload() }, lp(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
        content.addView(ScrollView(this).apply { addView(text) }, lp(MATCH, MATCH))
    }

    private fun showLibrary() {
        content.removeAllViews()
        val list = ListView(this).apply {
            divider = null
            selector = pressable(0, 0)
            setBackgroundColor(colors.ground)
            cacheColorHint = colors.ground
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addHeaderView(head, null, false)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val entry = adapter.items.getOrNull(position - list.headerViewsCount) ?: return@setOnItemClickListener
            if (entry.isBook) {
                BookActivity.start(this, entry.id, prefs.lastLine(entry.id))
            } else {
                path += entry
                showLevel()
            }
        }
        listView = list
        header = head
        content.addView(list, lp(MATCH, MATCH))
        showLevel()
    }

    private fun showLevel() {
        val opened = app.library.current ?: return
        header?.let { fillHeader(it) }
        val categoryId = path.lastOrNull()?.id
        scope.launch {
            val entries = withContext(Dispatchers.IO) {
                runCatching { if (categoryId == null) opened.rootEntries() else opened.children(categoryId) }.getOrDefault(emptyList())
            }
            adapter.items = entries
            listView?.setSelection(0)
        }
    }

    private fun fillHeader(head: LinearLayout) {
        head.removeAllViews()
        head.addView(searchField(), lp(MATCH, WRAP).apply { setMargins(dp(12), dp(12), dp(12), dp(4)) })
        if (path.isEmpty()) {
            val recents = prefs.recents.take(3)
            if (recents.isNotEmpty()) {
                head.addView(sectionTitle("המשך מהמקום שבו עצרת", colors))
                recents.forEach { place ->
                    head.addView(
                        EntryRow(this, colors).bind(place.title, "שורה ${place.lineIndex + 1}", R.drawable.ic_history).apply {
                            setOnClickListener { BookActivity.start(this@MainActivity, place.bookId, place.lineIndex) }
                        },
                    )
                    head.addView(divider(colors))
                }
            }
            head.addView(sectionTitle("ספרייה", colors))
        } else {
            head.addView(breadcrumb())
        }
    }

    /** "ספרייה › תנ״ך › תורה": each step goes back up to it. */
    private fun breadcrumb(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(6))
        }
        fun step(text: String, depth: Int, last: Boolean) {
            row.addView(
                label(text, 15f, if (last) colors.text else colors.accent, bold = last, maxLines = 1).apply {
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    if (!last) {
                        background = pressable(0, colors.pressed)
                        setOnClickListener {
                            while (path.size > depth) path.removeAt(path.size - 1)
                            showLevel()
                        }
                    }
                },
            )
            if (!last) row.addView(label("›", 15f, colors.secondary))
        }
        step("ספרייה", 0, path.isEmpty())
        path.forEachIndexed { i, entry -> step(entry.title, i + 1, i == path.size - 1) }
        return android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun searchField(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val radius = dp(22).toFloat()
        background = pressable(roundRect(colors.panel, radius, colors.border, dp(1)), roundRect(colors.pressed, radius, colors.border, dp(1)))
        minimumHeight = dp(46)
        setPadding(dp(14), 0, dp(14), 0)
        addView(
            ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_search)
                setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
            },
            lp(dp(22), dp(22)).apply { marginEnd = dp(10) },
        )
        addView(label("חיפוש ספר או טקסט…", 16f, colors.secondary, maxLines = 1))
        setOnClickListener { openSearch() }
    }

    private fun rowFor(entry: Entry, reuse: View?): View {
        val row = reuse as? EntryRow ?: EntryRow(this, colors)
        return row.bind(
            entry.title,
            iconRes = if (entry.isBook) R.drawable.ic_book else R.drawable.ic_folder,
            iconTint = if (entry.isBook) colors.secondary else colors.accent,
            leads = !entry.isBook,
        )
    }

    private fun openSearch() {
        startActivity(Intent(this, SearchActivity::class.java))
    }

    private fun showPlaces(title: String, places: List<Place>, bookmarks: Boolean) {
        if (places.isEmpty()) {
            toast(if (bookmarks) "אין סימניות עדיין. מוסיפים סימנייה מתפריט הספר." else "עדיין לא נפתחו ספרים")
            return
        }
        val placesAdapter = ItemsAdapter<Place> { place, reuse, _ ->
            (reuse as? EntryRow ?: EntryRow(this, colors)).bind(
                place.title,
                place.note.ifEmpty { "שורה ${place.lineIndex + 1}" },
                if (bookmarks) R.drawable.ic_bookmark else R.drawable.ic_history,
            )
        }.apply { items = places }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setAdapter(placesAdapter) { _, which ->
                val place = places[which]
                BookActivity.start(this, place.bookId, place.lineIndex)
            }
            .setNegativeButton("סגור", null)
            .show()
    }

    /** zayit://book/<bookId>/line/<lineId> (desktop's "copy link") or zayit://search?q=…, once the library is open. */
    private fun handleLink() {
        val link = pendingLink ?: return
        val opened = app.library.current ?: return
        pendingLink = null
        if (link.scheme != "zayit") return
        when (link.host) {
            "search" -> startActivity(Intent(this, SearchActivity::class.java).putExtra(SearchActivity.EXTRA_QUERY, link.getQueryParameter("q")))
            "book" -> {
                val segments = link.pathSegments
                val bookId = segments.getOrNull(0)?.toLongOrNull() ?: return
                val lineId = segments.getOrNull(2)?.takeIf { segments.getOrNull(1) == "line" }?.toLongOrNull()
                scope.launch {
                    val index = lineId?.let { id -> withContext(Dispatchers.IO) { runCatching { opened.lineIndex(id) }.getOrNull() } }
                    BookActivity.start(this@MainActivity, bookId, index)
                }
            }
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun shortPath(path: String): String = Bidi.ltr(path)

    private companion object {
        const val STATE_PATH = "path"
        const val STATE_TITLES = "titles"

        /** The main app's sentence (AndroidStrings.welcomeSubtitle): who made what. */
        const val ABOUT_LINE = "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה."
    }
}

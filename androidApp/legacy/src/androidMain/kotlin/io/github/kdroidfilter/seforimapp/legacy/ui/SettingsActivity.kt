package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.formatBytes
import io.github.kdroidfilter.seforimapp.android.ui.reader.DiacriticsMode
import io.github.kdroidfilter.seforimapp.android.ui.settings.ABOUT_MD
import io.github.kdroidfilter.seforimapp.android.ui.shell.ShellTexts
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.BookFont
import io.github.kdroidfilter.seforimapp.legacy.data.LegacySearch
import io.github.kdroidfilter.seforimapp.legacy.data.Prefs
import io.github.kdroidfilter.seforimapp.legacy.data.ThemeMode
import io.github.kdroidfilter.seforimapp.legacy.data.label
import io.github.kdroidfilter.seforimapp.legacy.db.NativeSqlite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Settings as the main app's phone shows them (SettingsScreen): an Island with the list of pages, each with its icon on
 * the accent's tint, its title and a line about it, opening on its own screen: תצוגה, ספרייה, אודות, תנאים and
 * רישיונות (the pages this edition has of the main app's).
 */
class SettingsActivity : BaseActivity() {
    private lateinit var bar: TopBar
    private lateinit var frame: FrameLayout
    private var page: Page? = null

    private enum class Page(val title: String, val summary: String, val icon: Int) {
        Display("תצוגה", "ערכת נושא, תמונת רקע, גודל הטקסט, גופן וניקוד", R.drawable.ic_text_format),
        Library("ספרייה", "מיקום הספרייה וגודלה, התקנה מקבצים וניקוי ההיסטוריה", R.drawable.ic_library),
        About("אודות", "על פרדס ותודות", R.drawable.ic_info),
        Terms("תנאים", "מקורות, זכויות ורישיון", R.drawable.ic_book),
        Licences("רישיונות", "רישיון פרדס ורישיונות הספריות, הגופנים והנתונים", R.drawable.ic_copy),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_back, "חזרה") { onBackPressedCompat() }
            title.text = "הגדרות"
            overflow { shellMenu() }
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))
        frame = FrameLayout(this)
        root.addView(frame, lp(MATCH, 0, 1f))
        setContentView(root)
        val start = savedInstanceState?.getString(STATE_PAGE)?.let { name -> Page.entries.firstOrNull { it.name == name } }
        if (start != null) show(start) else showList()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        page?.let { outState.putString(STATE_PAGE, it.name) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = onBackPressedCompat()

    /** The ⋮ menu's theme changed what תצוגה names, not its colours (System is light before Android 10). */
    override fun onThemeKept() {
        if (page == Page.Display) show(Page.Display)
    }

    private fun onBackPressedCompat() {
        if (page != null) showList() else finish()
    }

    /** A scrolling column of cards, as each page's. */
    private fun pageBody(): LinearLayout {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(24))
        }
        frame.removeAllViews()
        frame.addView(ScrollView(this).apply { addView(body) }, FrameLayout.LayoutParams(MATCH, MATCH))
        return body
    }

    /** SettingsCard: an Island with an optional title (15sp), its rows 10dp apart. */
    private fun card(body: LinearLayout, title: String? = null): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = island(colors)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        if (title != null) box.addView(label(title, 15f, colors.text, bold = true).apply { setPadding(0, 0, 0, dp(6)) })
        body.addView(box, lp(MATCH, WRAP).apply { bottomMargin = dp(10) })
        return box
    }

    // ------------------------------------------------------------------ the list

    private fun showList() {
        val from = page
        page = null
        bar.title.text = "הגדרות"
        val body = pageBody()
        val island = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = island(colors)
        }
        val narrow = resources.configuration.screenWidthDp < 300
        val pad = dp(if (narrow) 10 else 14)
        val tile = dp(if (narrow) 32 else 38)
        var focus: View? = null
        Page.entries.forEachIndexed { i, p ->
            if (i > 0) island.addView(View(this).apply { setBackgroundColor(colors.border) }, lp(MATCH, maxOf(1, dp(1))).apply { marginStart = pad * 2 + tile })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(64)
                setPadding(pad, dp(10), pad, dp(10))
                background = states(colors, 0, dp(10).toFloat())
                keyable(this) { open(p) }
            }
            val icon = FrameLayout(this).apply { background = roundRect(colors.accentTint, dp(10).toFloat()) }
            icon.addView(ImageView(this).apply {
                setImageResource(p.icon)
                setColorFilter(colors.accent, PorterDuff.Mode.SRC_IN)
            }, FrameLayout.LayoutParams(dp(if (narrow) 19 else 22), dp(if (narrow) 19 else 22), Gravity.CENTER))
            row.addView(icon, lp(tile, tile).apply { marginEnd = pad })
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(p.title, 16f, colors.text, bold = true, maxLines = 1))
            texts.addView(label(p.summary, 13f, colors.secondary, maxLines = 2).apply { setPadding(0, dp(2), 0, 0) })
            row.addView(texts, lp(0, WRAP, 1f))
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_arrow_left)
                setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
            }, lp(dp(if (narrow) 18 else 22), dp(if (narrow) 18 else 22)).apply { marginStart = dp(6) })
            // A keypad comes back to the row of the page it leaves, else starts on the first.
            if (focus == null || p == from) focus = row
            island.addView(row, lp(MATCH, WRAP))
        }
        body.addView(island, lp(MATCH, WRAP))
        keypadFocus(focus)
    }

    /**
     * Puts a keypad on [view], a page's first control: building a page removes the row that had the focus, which would
     * then go to the title bar's Back, and the next OK leave the page.
     */
    private fun keypadFocus(view: View?) {
        if (!frame.isInTouchMode) view?.requestFocus()
    }

    private fun open(p: Page) {
        when (p) {
            Page.Terms -> TextActivity.showTerms(this)
            Page.Licences -> TextActivity.showLicences(this)
            else -> show(p)
        }
    }

    private fun show(p: Page) {
        page = p
        bar.title.text = p.title
        when (p) {
            Page.Display -> display()
            Page.Library -> library()
            Page.About -> about()
            else -> showList()
        }
    }

    // ------------------------------------------------------------------ תצוגה

    private fun display() {
        val body = pageBody()
        // The title bar menu's three themes, in its order (System: the phone's, light before Android 10).
        val theme = card(body, "ערכת נושא")
        val themeModes = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.System)
        val themes = choiceChips(listOf("בהירה", "כהה", "מערכת"), themeModes.indexOf(prefs.themeMode)) { i ->
            prefs.themeMode = themeModes[i]
            applyTheme()
        }
        theme.addView(themes)
        val home = card(body)
        home.addView(toggleRow("תמונת רקע", "בדף הבית, מאחורי השם והחיפוש", colors, prefs.wallpaper) { prefs.wallpaper = it })

        val size = card(body, "גודל הטקסט בספרים")
        val sample = label(SAMPLE, 20f, colors.text).apply { setPadding(0, dp(8), 0, dp(4)) }
        val value = label("", 16f, colors.text, bold = true).apply { gravity = Gravity.CENTER }
        fun update() {
            value.text = prefs.textSize.toString()
            sample.typeface = Fonts.book(this)
            sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.textSize.toFloat())
            sample.lineHeightFactor(1.65f)
            sample.text = LineText.stripMarks(SAMPLE, prefs.diacritics)
        }
        val steps = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        steps.addView(stepButton(R.drawable.ic_zoom_out, "הקטן") {
            prefs.textSize = prefs.textSize - Prefs.TEXT_SIZE_STEP
            update()
        })
        steps.addView(value, lp(dp(56), WRAP))
        steps.addView(stepButton(R.drawable.ic_zoom_in, "הגדל") {
            prefs.textSize = prefs.textSize + Prefs.TEXT_SIZE_STEP
            update()
        })
        size.addView(steps)
        size.addView(sample)

        val fonts = BookFont.entries
        val font = card(body, "גופן הספרים")
        font.addView(choiceChips(fonts.map { it.label }, fonts.indexOf(prefs.bookFont), fontOf = { Fonts.asset(this, fonts[it].asset) }) { i ->
            prefs.bookFont = fonts[i]
            update()
        })
        val modes = DiacriticsMode.entries
        val marks = card(body, "ניקוד וטעמים")
        marks.addView(choiceChips(modes.map { it.label }, modes.indexOf(prefs.diacritics)) { i ->
            prefs.diacritics = modes[i]
            update()
        })
        val screen = card(body)
        screen.addView(toggleRow("השאר את המסך דולק", "בזמן הקריאה בספר", colors, prefs.keepScreenOn) { prefs.keepScreenOn = it })
        update()
        // The theme chosen: also where a keypad is after picking one, which recreates the screen.
        keypadFocus(themes.getChildAt(themeModes.indexOf(prefs.themeMode)))
    }

    /** The ZChips of one choice among [labels], wrapping; [fontOf] draws each in its font (the book fonts). */
    private fun choiceChips(labels: List<String>, selected: Int, fontOf: ((Int) -> android.graphics.Typeface)? = null, onPick: (Int) -> Unit): FlowRow {
        val row = FlowRow(this, dp(8), dp(8))
        var current = selected
        fun draw() {
            row.removeAllViews()
            labels.forEachIndexed { i, text ->
                row.addView(chip(text, colors, i == current) {
                    current = i
                    draw()
                    row.getChildAt(i)?.requestFocus()
                    onPick(i)
                }.apply { fontOf?.invoke(i)?.let { typeface = it } })
            }
        }
        draw()
        return row
    }

    private fun stepButton(icon: Int, description: String, onClick: () -> Unit): View =
        iconButton(icon, colors.accent, description, colors) { onClick() }.apply {
            background = states(
                roundRect(colors.panel, dp(22).toFloat(), colors.border, maxOf(1, dp(1))),
                roundRect(colors.pressed, dp(22).toFloat(), colors.border, maxOf(1, dp(1))),
                roundRect(colors.focus, dp(22).toFloat(), colors.accent, maxOf(1, dp(1))),
            )
        }

    // ------------------------------------------------------------------ ספרייה

    private fun library() {
        val body = pageBody()
        val info = card(body, "הספרייה")
        val text = label("…", 14f, colors.text).apply { setLineSpacing(0f, 1.2f) }
        info.addView(text)
        val actions = card(body)
        val search = link("חפש את הספרייה מחדש") {
            app.library.close()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(MainActivity.EXTRA_RELOAD, true))
            finish()
        }
        actions.addView(search)
        // The main app's database update from local files (DatabaseUpdateWindow), for a newer or another library.
        actions.addView(link(InstallTexts.TITLE) {
            InstallActivity.start(this, replacing = app.library.current != null || app.library.findDatabase() != null)
        })
        // The main app's question first (HistoryPage's ניקוי היסטוריה).
        actions.addView(link(ShellTexts.CLEAR_HISTORY) {
            PlacesDialogs.confirmClear(this, colors) {
                prefs.clearRecents()
                toast("ההיסטוריה נוקתה")
            }
        })
        scope.launch { text.text = withContext(Dispatchers.IO) { libraryInfo() } }
        keypadFocus(search)
    }

    private suspend fun libraryInfo(): String {
        val file = app.library.current?.file ?: app.library.findDatabase()
        // Opens the search if it is not open yet, to tell its level
        runCatching { app.search.service() }
        val search = app.search.level.value
        return buildString {
            if (file == null) {
                append("לא נמצאה ספרייה.")
            } else {
                append("מיקום: ").append(Bidi.ltr(file.parent ?: "")).append('\n')
                val location = app.library.locationOf(file.parentFile ?: file)
                location.fileSystem?.let { fs ->
                    append("מערכת קבצים: ").append(Bidi.ltr(if (location.isFat) "FAT32" else fs))
                    if (location.isFat) append(" (קובץ אחד עד 4 ג׳יגה־בייט: מאגר גדול ממנו נשמר בחלקים)")
                    append('\n')
                }
                val chunks = generateSequence(1) { it + 1 }.map { File(file.path + String.format(java.util.Locale.ROOT, "%03d", it)) }.takeWhile { it.isFile }.toList()
                val size = file.length() + chunks.sumOf { it.length() }
                append("גודל המאגר: ").append(formatBytes(size))
                if (chunks.isNotEmpty()) append(" (מחולק ל־${chunks.size + 1} קבצים)")
                append('\n')
                File(file.parentFile, "release_info.txt").takeIf { it.isFile }?.readText()?.trim()?.let {
                    append("מהדורה: ").append(Bidi.ltr(it)).append('\n')
                }
                append("חיפוש: ").append(
                    when (search) {
                        LegacySearch.Level.Full -> "באינדקס החיפוש של הספרייה"
                        LegacySearch.Level.Lookup -> "ספרים ומחברים באינדקס, הטקסט בסריקה"
                        LegacySearch.Level.Scan -> "בסריקה, בלי אינדקס"
                    },
                ).append('\n')
            }
            append("SQLite ").append(NativeSqlite.version())
        }
    }

    // ------------------------------------------------------------------ אודות

    /**
     * The main app's כללי header (AppHeaderCard: icon, name, version, the credit to Zayit), then its About page as it
     * renders it (AboutSettings: ABOUT_MD, אודות פרדס and תודות, its links in the text, a keypad's ▲▼ going through
     * them), then what this edition is.
     */
    private fun about() {
        val body = pageBody()
        val card = card(body)
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }, lp(dp(56), dp(56)).apply { marginEnd = dp(12) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("פרדס", 24f, colors.logo, typeface = Fonts.logo(this)))
        titles.addView(label("גרסה ${versionName()} לאנדרואיד 4.4 עד 7.1", 13f, colors.secondary))
        val credit = link("מבוסס על זית מאת אליהו גמבש") { openUrl("https://github.com/kdroidFilter/Zayit") }
        titles.addView(credit)
        head.addView(titles, lp(0, WRAP, 1f))
        card.addView(head)

        card(body).addView(MarkdownText.view(this, colors, ABOUT_MD))

        val edition = card(body, "הגרסה הזו")
        edition.addView(paragraph(EDITION))
        keypadFocus(credit)
    }

    private fun paragraph(text: CharSequence): TextView = label(text, 14f, colors.text).apply {
        setLineSpacing(0f, 1.2f)
        setPadding(0, dp(8), 0, 0)
    }

    private fun link(text: String, onClick: () -> Unit): TextView = label(text, 15f, colors.accent).apply {
        minHeight = dp(44)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(dp(4), 0, dp(4), 0)
        background = states(colors, 0, dp(6).toFloat())
        keyable(this) { onClick() }
    }

    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""

    private fun openUrl(url: String) = MarkdownText.open(this, url)

    private companion object {
        const val STATE_PAGE = "page"

        /** Bereshit 1:1 with its niqqud and cantillation, to show the font, the size and the marks chosen. */
        const val SAMPLE = "בְּרֵאשִׁ֖ית בָּרָ֣א אֱלֹהִ֑ים אֵ֥ת הַשָּׁמַ֖יִם וְאֵ֥ת הָאָֽרֶץ׃"

        const val EDITION =
            "זוהי הגרסה של פרדס לאנדרואיד 4.4 עד 7.1, שבהם פרדס הרגיל (מאנדרואיד 8.0) אינו פועל. היא קוראת אותה ספרייה, " +
                "במסכים פשוטים יותר: עיון בספרייה, קריאה עם תוכן העניינים, המפרשים, התרגומים והמקורות, מועדפים וחיפוש. " +
                "החיפוש הוא של פרדס הרגיל, באינדקס החיפוש שבספרייה (Lucene). בספרייה בלי האינדקס, או בטלפון שאינו יכול " +
                "לפתוח אותו, הטקסט נחפש בקריאת הספרים עצמם: בספר או בקטגוריה זה מהיר, ובכל הספרייה זה נמשך כמה דקות. " +
                "הכלים, ההערות והעדכונים של הספרייה ושל היישום נמצאים בפרדס הרגיל בלבד. " +
                "בטלפון עם מקשים, מקשי הספרות הם קיצורים בספר (בתפריט: מקשי קיצור)."
    }
}

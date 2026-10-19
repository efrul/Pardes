package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.BookFont
import io.github.kdroidfilter.seforimapp.legacy.data.Diacritics
import io.github.kdroidfilter.seforimapp.legacy.data.Prefs
import io.github.kdroidfilter.seforimapp.legacy.db.NativeSqlite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Settings: the display, the library, and about Pardes (who made what, its licence and source, the terms). */
class SettingsActivity : BaseActivity() {
    private lateinit var body: LinearLayout
    private lateinit var sample: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors) { finish() }
        bar.title.text = "הגדרות"
        root.addView(bar, lp(MATCH, WRAP))
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }
        root.addView(ScrollView(this).apply { addView(body) }, lp(MATCH, 0, 1f))
        setContentView(root)
        build()
    }

    private fun build() {
        body.removeAllViews()

        // ---------------------------------------------------------------- תצוגה
        body.addView(sectionTitle("תצוגה", colors))
        val display = card()
        display.addView(caption("ערכת נושא"))
        display.addView(choices(listOf("בהירה", "כהה"), if (prefs.dark) 1 else 0) { i ->
            prefs.dark = i == 1
            recreate()
        })
        display.addView(caption("גודל הטקסט בספרים"))
        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sizeLabel = label("${prefs.textSize}", 16f, colors.text, bold = true).apply { gravity = Gravity.CENTER }
        sizeRow.addView(stepButton("−") {
            prefs.textSize = prefs.textSize - 1
            sizeLabel.text = "${prefs.textSize}"
            updateSample()
        })
        sizeRow.addView(sizeLabel, lp(dp(48), WRAP))
        sizeRow.addView(stepButton("+") {
            prefs.textSize = prefs.textSize + 1
            sizeLabel.text = "${prefs.textSize}"
            updateSample()
        })
        display.addView(sizeRow)
        sample = label(SAMPLE, 20f, colors.text).apply {
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        display.addView(sample)
        display.addView(caption("גופן הספרים"))
        val fonts = BookFont.entries
        display.addView(choices(fonts.map { it.label }, fonts.indexOf(prefs.bookFont), vertical = true, fontOf = { Fonts.asset(this, fonts[it].asset) }) { i ->
            prefs.bookFont = fonts[i]
            updateSample()
        })
        display.addView(caption("ניקוד וטעמים"))
        val modes = Diacritics.entries
        display.addView(choices(modes.map { it.label }, modes.indexOf(prefs.diacritics), vertical = true) { i ->
            prefs.diacritics = modes[i]
            updateSample()
        })
        display.addView(CheckBox(this).apply {
            text = "השאר את המסך דולק בזמן הקריאה"
            setTextColor(colors.text)
            isChecked = prefs.keepScreenOn
            setOnCheckedChangeListener { _, checked -> prefs.keepScreenOn = checked }
        }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        updateSample()

        // ---------------------------------------------------------------- ספרייה
        body.addView(sectionTitle("ספרייה", colors))
        val library = card()
        val libraryText = label("…", 14f, colors.text).apply { setLineSpacing(0f, 1.15f) }
        library.addView(libraryText)
        library.addView(link("חפש את הספרייה מחדש") {
            app.library.close()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        })
        library.addView(link("נקה את רשימת הספרים האחרונים") {
            prefs.clearRecents()
            toast("הרשימה נוקתה")
        })
        scope.launch {
            val info = withContext(Dispatchers.IO) { libraryInfo() }
            libraryText.text = info
        }

        // ---------------------------------------------------------------- אודות
        body.addView(sectionTitle("אודות", colors))
        val about = card()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }, lp(dp(56), dp(56)).apply { marginEnd = dp(12) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("פרדס", 22f, colors.text, typeface = Fonts.logo(this)))
        titles.addView(label("גרסה ${versionName()} לאנדרואיד 4.4 עד 7.1", 13f, colors.secondary))
        titles.addView(link("מבוסס על זית מאת אליהו גמבש") { openUrl("https://github.com/kdroidFilter/Zayit") })
        head.addView(titles, lp(0, WRAP, 1f))
        about.addView(head)
        ABOUT.forEach { about.addView(paragraph(Bidi.compat(it))) }
        about.addView(link("קוד המקור של פרדס") { openUrl(SOURCE_URL) })
        about.addView(paragraph(EDITION))
        about.addView(divider(colors), lp(MATCH, maxOf(1, dp(0.75f))).apply { topMargin = dp(8); bottomMargin = dp(4) })
        about.addView(link("תנאים") { TextActivity.showTerms(this) })
        about.addView(link("רישיונות") { TextActivity.showLicences(this) })
    }

    private fun libraryInfo(): String {
        val file = app.library.current?.file ?: app.library.findDatabase()
        return buildString {
            if (file == null) {
                append("לא נמצאה ספרייה.")
            } else {
                append("מיקום: ").append(Bidi.ltr(file.parent ?: "")).append('\n')
                val chunks = generateSequence(1) { it + 1 }.map { File(file.path + String.format(java.util.Locale.ROOT, "%03d", it)) }.takeWhile { it.isFile }.toList()
                val size = file.length() + chunks.sumOf { it.length() }
                append("גודל המאגר: ").append(Bidi.ltr(Formatter.formatFileSize(this@SettingsActivity, size)))
                if (chunks.isNotEmpty()) append(" (מחולק ל־${chunks.size + 1} קבצים)")
                append('\n')
                File(file.parentFile, "release_info.txt").takeIf { it.isFile }?.readText()?.trim()?.let {
                    append("מהדורה: ").append(Bidi.ltr(it)).append('\n')
                }
            }
            append("SQLite ").append(NativeSqlite.version())
        }
    }

    private fun updateSample() {
        sample.typeface = Fonts.book(this)
        sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.textSize.toFloat())
        sample.text = LineText.stripMarks(SAMPLE, prefs.diacritics)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundRect(colors.panel, dp(10).toFloat(), colors.border, dp(1))
        setPadding(dp(14), dp(10), dp(14), dp(12))
        body.addView(this, lp(MATCH, WRAP).apply { setMargins(dp(12), 0, dp(12), dp(4)) })
    }

    private fun caption(text: String): TextView = label(text, 14f, colors.secondary).apply { setPadding(0, dp(10), 0, dp(4)) }

    private fun paragraph(text: CharSequence): TextView = label(text, 14f, colors.text).apply {
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(8), 0, 0)
    }

    private fun link(text: String, onClick: () -> Unit): TextView = label(text, 15f, colors.accent).apply {
        setPadding(dp(2), dp(10), dp(2), dp(10))
        background = pressable(0, colors.pressed)
        setOnClickListener { onClick() }
    }

    private fun stepButton(text: String, onClick: () -> Unit): TextView = label(text, 22f, colors.accent, bold = true).apply {
        gravity = Gravity.CENTER
        background = pressable(roundRect(colors.panel, dp(20).toFloat(), colors.border, dp(1)), roundRect(colors.pressed, dp(20).toFloat()))
        layoutParams = lp(dp(44), dp(40))
        setOnClickListener { onClick() }
    }

    /** One choice among [labels]: chips in a row, or rows with a mark. */
    private fun choices(
        labels: List<String>,
        selected: Int,
        vertical: Boolean = false,
        fontOf: ((Int) -> android.graphics.Typeface)? = null,
        onPick: (Int) -> Unit,
    ): View {
        val group = LinearLayout(this).apply { orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        var current = selected
        val views = ArrayList<TextView>()
        fun paint() = views.forEachIndexed { i, v ->
            val on = i == current
            val radius = dp(18).toFloat()
            v.setTextColor(if (on) colors.panel else colors.text)
            v.background = if (on) roundRect(colors.accent, radius) else pressable(roundRect(colors.panel, radius, colors.border, dp(1)), roundRect(colors.pressed, radius))
        }
        labels.forEachIndexed { i, text ->
            val v = label(text, 15f, colors.text, typeface = fontOf?.invoke(i)).apply {
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener {
                    current = i
                    paint()
                    onPick(i)
                }
            }
            views += v
            group.addView(v, lp(if (vertical) MATCH else WRAP, WRAP).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        paint()
        return group
    }

    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            toast(url)
        }
    }

    private companion object {
        const val SOURCE_URL = "https://github.com/efrul/Pardes"

        /** Bereshit 1:1 with its niqqud and cantillation, to show the font, the size and the marks chosen. */
        const val SAMPLE = "בְּרֵאשִׁ֖ית בָּרָ֣א אֱלֹהִ֑ים אֵ֥ת הַשָּׁמַ֖יִם וְאֵ֥ת הָאָֽרֶץ׃"

        /** The main app's About and כללי header (SettingsPages, AboutSettings), word for word. */
        val ABOUT = listOf(
            "פרדס אינו מטעם אליהו גמבש: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה.",
            "הליבה של זית היא קוד פתוח תחת רישיון AGPL-3.0, וגם קוד המקור של פרדס זמין תחת רישיון זה.",
            "המודולים הקנייניים של הגרסאות הרשמיות של זית (החיפוש הסמנטי והסידור החכם המלא) אינם כלולים בפרדס.",
            "⁦Powered by the technologies that drive Zayit — https://zayitapp.com/⁩",
        )

        const val EDITION =
            "זוהי הגרסה של פרדס לאנדרואיד 4.4 עד 7.1, שבהם פרדס הרגיל (מאנדרואיד 8.0) אינו פועל. היא קוראת אותה ספרייה, " +
                "במסכים פשוטים יותר: עיון בספרייה, קריאה עם תוכן העניינים, המפרשים, התרגומים והמקורות, סימניות וחיפוש. " +
                "החיפוש בטקסט קורא את הספרים עצמם, כי מנוע החיפוש של זית (Lucene) דורש אנדרואיד 8.0: בספר או בקטגוריה הוא מהיר, " +
                "ובכל הספרייה הוא נמשך כמה דקות. הכלים, ההערות והעדכונים של הספרייה ושל היישום נמצאים בפרדס הרגיל בלבד."
    }
}

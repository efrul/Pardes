package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.CONDITIONS_MARKDOWN
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.PARDES_LICENSE_URL

/**
 * A text to read: the terms (desktop's CONDITIONS.md under Pardes's name, as the main app shows it), the list of
 * licences, or one licence's text from assets/licenses.
 */
class TextActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors) { finish() }
        bar.title.text = intent.getStringExtra(EXTRA_TITLE) ?: ""
        root.addView(bar, lp(MATCH, WRAP))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.panel)
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        root.addView(ScrollView(this).apply {
            setBackgroundColor(colors.panel)
            addView(body)
        }, lp(MATCH, 0, 1f))
        setContentView(root)
        when (intent.getStringExtra(EXTRA_KIND)) {
            KIND_TERMS -> body.addView(rich(markdownToHtml(Bidi.compat(CONDITIONS_MARKDOWN))))
            KIND_LICENCES -> licences(body)
            else -> {
                val path = intent.getStringExtra(EXTRA_ASSET) ?: ""
                val text = runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrElse { intent.getStringExtra(EXTRA_TEXT) ?: "" }
                body.addView(label(text, 13f, colors.text).apply {
                    // Licence texts are English: left to right, whatever the screen's direction.
                    textDirection = View.TEXT_DIRECTION_LTR
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    setTextIsSelectable(true)
                })
            }
        }
    }

    private fun licences(body: LinearLayout) {
        body.addView(paragraph(LICENCES_INTRO))
        PARDES_LICENCE.forEach { body.addView(paragraph(Bidi.compat(it))) }
        body.addView(sectionTitle("הרכיבים שבפרדס לאנדרואיד 4.4", colors).apply { setPadding(0, dp(16), 0, dp(4)) })
        COMPONENTS.forEach { c ->
            body.addView(EntryRow(this, colors).bind(c.name, c.licence, leads = true).apply {
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnClickListener {
                    if (c.asset != null) showAsset(this@TextActivity, c.name, c.asset) else showPlain(this@TextActivity, c.name, c.licence)
                }
            })
            body.addView(divider(colors))
        }
        body.addView(paragraph(DATA_NOTE))
    }

    private fun paragraph(text: CharSequence): TextView = label(text, 14f, colors.text).apply {
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(6), 0, dp(6))
    }

    /** HTML with its links clickable: Pardes's licence opens the copy in the app, the others the browser. */
    private fun rich(html: String): TextView {
        val text = SpannableStringBuilder(LineText.fromHtml(html))
        text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            val url = span.url
            text.removeSpan(span)
            text.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    if (url == PARDES_LICENSE_URL) {
                        showAsset(this@TextActivity, "רישיון פרדס", "licenses/Pardes-LICENSE.txt")
                    } else {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {
                            toast(url)
                        }
                    }
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return label(text, 15f, colors.text).apply {
            setLineSpacing(0f, 1.2f)
            setLinkTextColor(colors.accent)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private data class Component(val name: String, val licence: String, val asset: String?)

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ASSET = "asset"
        private const val EXTRA_TEXT = "text"
        private const val KIND_TERMS = "terms"
        private const val KIND_LICENCES = "licences"

        fun showTerms(context: Context) = context.startActivity(
            Intent(context, TextActivity::class.java).putExtra(EXTRA_TITLE, "תנאים").putExtra(EXTRA_KIND, KIND_TERMS),
        )

        fun showLicences(context: Context) = context.startActivity(
            Intent(context, TextActivity::class.java).putExtra(EXTRA_TITLE, "רישיונות").putExtra(EXTRA_KIND, KIND_LICENCES),
        )

        fun showAsset(context: Context, title: String, asset: String) = context.startActivity(
            Intent(context, TextActivity::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_ASSET, asset),
        )

        fun showPlain(context: Context, title: String, text: String) = context.startActivity(
            Intent(context, TextActivity::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text),
        )

        /** Enough Markdown for the terms: ## headings, **bold**, [links](…), > quotes and paragraphs. */
        fun markdownToHtml(md: String): String {
            fun inline(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
                .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
            return md.trim().split(Regex("\n\\s*\n")).joinToString("") { block ->
                val b = block.trim()
                when {
                    b.startsWith("### ") -> "<h4>${inline(b.removePrefix("### "))}</h4>"
                    b.startsWith("## ") -> "<h3>${inline(b.removePrefix("## "))}</h3>"
                    b.startsWith("> ") -> "<blockquote>${inline(b.removePrefix("> "))}</blockquote>"
                    else -> "<p>${inline(b.lines().joinToString(" ") { it.trim() })}</p>"
                }
            }
        }

        /** The main app's Settings › רישיונות (LicencesSettings), for what this edition carries. */
        private const val LICENCES_INTRO =
            "פרדס מופץ תחת רישיון GNU AGPL v3 עם תנאי ייחוס נוסף, וכולל רכיבים של אחרים, כל אחד ברישיון שלו. כאן רשומים " +
                "כולם, ונוסח כל רישיון נפתח בלחיצה על שמו."

        private val PARDES_LICENCE = listOf(
            "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה.",
            "זית ו־SeforimLibrary: ⁦Copyright (C) 2025 Elie Gambache⁩, ברישיון GNU AGPL v3 עם תנאי ייחוס נוסף לפי סעיף 7ב:",
            "⁦Powered by the technologies that drive Zayit — https://zayitapp.com/⁩",
            "גם פרדס מופץ תחת רישיון זה, וקוד המקור שלו זמין תחת אותו רישיון. הוא מסופק כמות שהוא, ללא כל אחריות.",
        )

        private const val DATA_NOTE = "הספרים עצמם מגיעים מ־Sefaria ומאוצריא דרך SeforimLibrary, כל ספר ברישיון שלו (ראו תנאים)."

        private const val APACHE = "Apache License 2.0"

        private val COMPONENTS = listOf(
            Component("פרדס, זית ו־SeforimLibrary", "GNU AGPL v3 עם תנאי ייחוס נוסף", "licenses/Pardes-LICENSE.txt"),
            Component("SQLite", "נחלת הכלל (public domain), sqlite.org/copyright.html", null),
            Component("Kotlin, kotlinx.coroutines ו־kotlinx.serialization (JetBrains)", APACHE, "licenses/Apache-2.0.txt"),
            Component("SQLDelight (Cash App)", APACHE, "licenses/Apache-2.0.txt"),
            Component("Kermit (Touchlab)", APACHE, "licenses/Apache-2.0.txt"),
            Component("AndroidX Multidex (Google)", APACHE, "licenses/Apache-2.0.txt"),
            Component("desugar_jdk_libs (Google, מ־OpenJDK)", "GNU GPL v2 עם חריגת Classpath", "licenses/desugar_jdk_libs-LICENSE.txt"),
            Component("Material Icons (Google)", APACHE, "licenses/Apache-2.0.txt"),
            Component("Noto Serif Hebrew", "SIL Open Font License 1.1", "licenses/OFL-Noto-Hebrew.txt"),
            Component("Frank Ruhl Libre", "SIL Open Font License 1.1", "licenses/OFL-Frank-Ruhl-Libre.txt"),
            Component("Taamey Frank CLM (פרויקט הקולמוס)", "GNU GPL v2 עם חריגת גופנים", "licenses/Culmus-LICENSE.txt"),
            Component("GNU GPL v2", "נוסח הרישיון", "licenses/GPL-2.0.txt"),
            Component("הודעות הגופנים", "כפי שהן כתובות בגופנים עצמם", "licenses/Fonts-NOTICE.txt"),
        )
    }
}

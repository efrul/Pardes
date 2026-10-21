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
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.CONDITIONS_MARKDOWN
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.PARDES_LICENSE_URL
import io.github.kdroidfilter.seforimapp.android.ui.settings.DATA_NOTE
import io.github.kdroidfilter.seforimapp.android.ui.settings.LICENCES_INTRO
import io.github.kdroidfilter.seforimapp.android.ui.settings.PARDES_LICENCE
import io.github.kdroidfilter.seforimapp.android.ui.settings.reflow
import io.github.kdroidfilter.seforimapp.legacy.R

/**
 * A text to read: the terms (desktop's CONDITIONS.md under Pardes's name, as the main app shows it), the list of
 * licences, or one licence's text from assets/licenses.
 */
class TextActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_back, "חזרה") { finish() }
            title.text = intent.getStringExtra(EXTRA_TITLE) ?: ""
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))
        // An Island on the ground, as the main app's settings pages.
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = island(colors)
            setPadding(dp(14), dp(10), dp(14), dp(16))
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(24))
            addView(body, lp(MATCH, WRAP))
        }
        root.addView(ScrollView(this).apply { addView(page) }, lp(MATCH, 0, 1f))
        setContentView(root)
        when (intent.getStringExtra(EXTRA_KIND)) {
            KIND_TERMS -> body.addView(rich(markdownToHtml(Bidi.compat(CONDITIONS_MARKDOWN))))
            KIND_LICENCES -> licences(body)
            else -> {
                val path = intent.getStringExtra(EXTRA_ASSET) ?: ""
                val text = runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrElse { intent.getStringExtra(EXTRA_TEXT) ?: "" }
                body.addView(label(reflow(text).joinToString("\n\n"), 13f, colors.text).apply {
                    // Licence texts are English: left to right, whatever the screen's direction.
                    textDirection = View.TEXT_DIRECTION_LTR
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    // Selectable for a touch only: selectable text takes the keypad's ▲▼ to move a cursor it does not
                    // draw, a line at a time, where the page should scroll by half a screen.
                    if (isInTouchMode) setTextIsSelectable(true)
                })
            }
        }
    }

    private fun licences(body: LinearLayout) {
        body.addView(paragraph(LICENCES_INTRO))
        PARDES_LICENCE.forEach { body.addView(paragraph(Bidi.compat(it))) }
        body.addView(label("הרכיבים שבפרדס לאנדרואיד 4.4", 15f, colors.text, bold = true).apply { setPadding(0, dp(16), 0, dp(4)) })
        COMPONENTS.forEachIndexed { i, c ->
            if (i > 0) body.addView(divider(colors))
            body.addView(componentRow(c))
        }
        body.addView(paragraph(DATA_NOTE))
    }

    /** A component's row as a TreeRow draws one, its licence line in full: a TreeRow cuts its second line to one. */
    private fun componentRow(c: Component): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(44)
        setPaddingRelative(dp(8), dp(4), dp(10), dp(4))
        background = states(colors, 0, dp(6).toFloat())
        addView(label(c.name, 15f, colors.text, maxLines = 2))
        addView(label(c.licence, 12f, colors.secondary))
        keyable(this) {
            if (c.asset != null) showAsset(this@TextActivity, c.name, c.asset) else showPlain(this@TextActivity, c.name, c.licence)
        }
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

        private const val APACHE = "Apache License 2.0"

        private val COMPONENTS = listOf(
            Component("פרדס, זית ו־SeforimLibrary", "GNU AGPL v3 עם תנאי ייחוס נוסף", "licenses/Pardes-LICENSE.txt"),
            Component("SQLite", "נחלת הכלל (public domain), sqlite.org/copyright.html", null),
            // As the main app's credit (LicencesSettings): the jar is lucene-android's, built for this edition (jarApi19).
            Component(
                "Apache Lucene 10.5.2 (The Apache Software Foundation)",
                "$APACHE. מנוע החיפוש: lucene-core ושלוש מחלקות של lucene-analysis-common, ששונו כדי לפעול מאנדרואיד 4.4.",
                "licenses/Lucene-LICENSE.txt",
            ),
            Component("Apache Lucene: NOTICE", "Copyright 2001-2025 The Apache Software Foundation", "licenses/Lucene-NOTICE.txt"),
            Component("Kotlin, kotlinx.coroutines ו־kotlinx.serialization (JetBrains)", APACHE, "licenses/Apache-2.0.txt"),
            Component("SQLDelight (Cash App)", APACHE, "licenses/Apache-2.0.txt"),
            Component("Kermit (Touchlab)", APACHE, "licenses/Apache-2.0.txt"),
            Component("HebrewNumerals (kdroidFilter)", APACHE, "licenses/Apache-2.0.txt"),
            Component("jsoup (Jonathan Hedley)", "MIT", "licenses/jsoup-LICENSE.txt"),
            // The install from files, as the main app's (LicencesSettings): tar.zst bundles read with zstd-jni and
            // Commons Compress, which brings Commons IO, Lang and Codec.
            Component("Apache Commons Compress, IO, Lang, Codec (The Apache Software Foundation)", APACHE, "licenses/Apache-2.0.txt"),
            Component("Apache Commons: NOTICE", "Copyright 2001-2025 The Apache Software Foundation", "licenses/Apache-Commons-NOTICE.txt"),
            Component("zstd-jni (Luben Karavelov)", "BSD 2-Clause", "licenses/zstd-jni-LICENSE.txt"),
            Component("Zstandard (Meta Platforms, Inc. and affiliates)", "BSD", "licenses/Zstandard-LICENSE.txt"),
            Component("AndroidX Multidex (Google)", APACHE, "licenses/Apache-2.0.txt"),
            Component("desugar_jdk_libs (Google, מ־OpenJDK)", "GNU GPL v2 עם חריגת Classpath", "licenses/desugar_jdk_libs-LICENSE.txt"),
            Component("Material Symbols, Material Icons (Google)", APACHE, "licenses/Apache-2.0.txt"),
            Component("Bootstrap Icons (The Bootstrap Authors)", "MIT", "licenses/Bootstrap-Icons-LICENSE.txt"),
            // As the main app's credit (LicencesSettings iconCredits): the search's authors.
            Component("Emoji One", "CC BY 4.0, creativecommons.org/licenses/by/4.0. אייקון היד הכותבת (Writing hand, Monotone).", null),
            Component("Noto Serif Hebrew", "SIL Open Font License 1.1", "licenses/OFL-Noto-Hebrew.txt"),
            Component("Frank Ruhl Libre", "SIL Open Font License 1.1", "licenses/OFL-Frank-Ruhl-Libre.txt"),
            // Its own notice (Fonts-NOTICE.txt): the GPL with the font exception, and the MIT text of its layout logic.
            // Culmus-LICENSE.txt is the notice of the Culmus package's other fonts, and never names it.
            Component(
                "Taamey Frank CLM (פרויקט הקולמוס)",
                "ברישיון GPL v2 עם חריג הגופנים; לוגיקת הפריסה של הטעמים ברישיון MIT (בהודעות הגופנים).",
                "licenses/Fonts-NOTICE.txt",
            ),
            Component("GNU GPL v2", "נוסח הרישיון", "licenses/GPL-2.0.txt"),
            Component("הודעות הגופנים", "כפי שהן כתובות בגופנים עצמם", "licenses/Fonts-NOTICE.txt"),
        )
    }
}

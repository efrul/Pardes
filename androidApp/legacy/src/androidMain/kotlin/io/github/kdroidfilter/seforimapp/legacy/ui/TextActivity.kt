package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.CONDITIONS_MARKDOWN
import io.github.kdroidfilter.seforimapp.android.ui.settings.DATA_NOTE
import io.github.kdroidfilter.seforimapp.android.ui.settings.LICENCES_INTRO
import io.github.kdroidfilter.seforimapp.android.ui.settings.PARDES_LICENCE
import io.github.kdroidfilter.seforimapp.android.ui.settings.reflow
import io.github.kdroidfilter.seforimapp.legacy.R

/**
 * A text to read: the terms (desktop's CONDITIONS.md under Pardes's name, as the main app shows it), the licences (the
 * main app's רישיונות page, with what this edition carries), or one licence's text from assets/licenses.
 */
class TextActivity : BaseActivity() {
    private lateinit var page: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = TopBar(this, colors).apply {
            leading(R.drawable.ic_back, "חזרה") { finish() }
            title.text = intent.getStringExtra(EXTRA_TITLE) ?: ""
        }
        topBar = bar
        root.addView(bar, lp(MATCH, WRAP))
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(24))
        }
        root.addView(ScrollView(this).apply { addView(page) }, lp(MATCH, 0, 1f))
        setContentView(root)
        when (intent.getStringExtra(EXTRA_KIND)) {
            KIND_TERMS -> card().addView(MarkdownText.view(this, colors, CONDITIONS_MARKDOWN))
            KIND_LICENCES -> licences()
            else -> {
                val path = intent.getStringExtra(EXTRA_ASSET) ?: ""
                val text = runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrElse { intent.getStringExtra(EXTRA_TEXT) ?: "" }
                // The main app's licence text: its paragraphs joined again (reflow), 14sp, 21sp apart.
                card().addView(label(reflow(text).joinToString("\n\n"), 14f, colors.text).apply {
                    lineHeightFactor(1.5f)
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

    /** An Island on the ground, as the main app's settings cards (SettingsCard): an optional title, rows 10dp apart. */
    private fun card(title: String? = null): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = island(colors)
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        if (title != null) box.addView(label(title, 15f, colors.text, bold = true).apply { setPadding(0, 0, 0, dp(4)) })
        page.addView(box, lp(MATCH, WRAP).apply { bottomMargin = dp(10) })
        return box
    }

    /**
     * The main app's רישיונות (LicencesSettings): its intro; פרדס's licence with the AGPL and the source; then the
     * libraries, icons, fonts and data this edition carries, each with its copyright holders, a note, and its texts.
     */
    private fun licences() {
        page.addView(label(LICENCES_INTRO, 15f, colors.text).apply {
            lineHeightFactor(23f / 15f)
            setPadding(dp(4), 0, dp(4), dp(10))
        })
        val pardes = card("פרדס")
        PARDES_LICENCE.forEach { pardes.addView(rtl(label(Bidi.compat(it), 15f, colors.text)).apply { lineHeightFactor(23f / 15f) }) }
        pardes.addView(docLinks(listOf(Doc(MarkdownText.PARDES_LICENCE_TITLE, asset = MarkdownText.PARDES_LICENCE_ASSET), Doc("קוד המקור", url = SOURCE_URL))))
        credits("ספריות", LIBRARY_CREDITS)
        credits("אייקונים", ICON_CREDITS)
        credits("גופנים", FONT_CREDITS)
        credits("נתונים ותמונות", emptyList(), DATA_NOTE)
    }

    /** CreditsCard: each credit's name (bold), holders and note in grey, its texts as links; a line between credits. */
    private fun credits(title: String, credits: List<Credit>, footer: String? = null) {
        val box = card(title)
        credits.forEachIndexed { i, credit ->
            if (i > 0) box.addView(divider(colors).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(0, dp(8), 0, dp(8)) })
            box.addView(rtl(label(isolated(credit.name), 15f, colors.text, bold = true)))
            box.addView(rtl(label(isolated(credit.holder), 13f, colors.secondary)).apply { setPadding(0, dp(2), 0, 0) })
            credit.note?.let { box.addView(label(it, 13f, colors.secondary).apply { setPadding(0, dp(2), 0, 0) }) }
            box.addView(docLinks(credit.docs))
        }
        if (footer != null) {
            if (credits.isNotEmpty()) box.addView(divider(colors).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(0, dp(8), 0, dp(8)) })
            box.addView(label(footer, 13f, colors.secondary))
        }
    }

    /**
     * Right to left, as the main app lays out a text that starts with an isolated Latin passage (a credit's name, the §7b
     * line): Android 4.4 takes that passage's embedding ([isolated]) for the paragraph's direction.
     */
    private fun rtl(view: TextView): TextView = view.apply { textDirection = View.TEXT_DIRECTION_RTL }

    /** DocLinks: a credit's texts side by side, wrapping, each a stop for a keypad (OK opens it). */
    private fun docLinks(docs: List<Doc>): View {
        val row = FlowRow(this, dp(16), 0, centered = false)
        docs.forEach { doc ->
            row.addView(label(isolated(doc.title), 15f, colors.accent, maxLines = 1).apply {
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(40)
                setPadding(dp(4), 0, dp(4), 0)
                background = states(colors, 0, dp(4).toFloat())
                keyable(this) { open(doc) }
            })
        }
        return row
    }

    private fun open(doc: Doc) {
        val url = doc.url
        if (url != null) MarkdownText.open(this, url) else showAsset(this, doc.title, doc.asset ?: return)
    }

    /** A licence text: bundled under assets/licenses, or online (the Creative Commons ones, the public domain's page). */
    private class Doc(val title: String, val asset: String? = null, val url: String? = null)

    private class Credit(val name: String, val holder: String, val docs: List<Doc>, val note: String? = null)

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ASSET = "asset"
        private const val EXTRA_TEXT = "text"
        private const val KIND_TERMS = "terms"
        private const val KIND_LICENCES = "licences"
        private const val SOURCE_URL = "https://github.com/efrul/Pardes"

        fun showTerms(context: Context) = context.startActivity(
            Intent(context, TextActivity::class.java).putExtra(EXTRA_TITLE, "תנאים").putExtra(EXTRA_KIND, KIND_TERMS),
        )

        fun showLicences(context: Context) = context.startActivity(
            Intent(context, TextActivity::class.java).putExtra(EXTRA_TITLE, "רישיונות").putExtra(EXTRA_KIND, KIND_LICENCES),
        )

        fun showAsset(context: Context, title: String, asset: String) = context.startActivity(
            Intent(context, TextActivity::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_ASSET, asset),
        )

        /**
         * Latin text inside the RTL page kept left to right (the main app isolates it; Android 4.4 knows only the older
         * embeddings, [Bidi.ltr]). A title with Hebrew in it is left to the RTL paragraph, which orders its words right.
         */
        private fun isolated(text: String): String = if (text.any { it in '֐'..'׿' }) text else Bidi.ltr(text)

        private fun bundled(title: String, file: String) = Doc(title, asset = "licenses/$file")

        private val APACHE = bundled("Apache License 2.0", "Apache-2.0.txt")
        private val GPL = bundled("GNU GPL v2", "GPL-2.0.txt")
        private val FONT_NOTICES = bundled("הודעות הגופנים", "Fonts-NOTICE.txt")

        // The main app's credits (LicencesSettings) for what this edition carries, and this edition's own: its SQLite,
        // Multidex and the desugared Java library. Not here: what only the main app has (Compose, requery, KosherJava,
        // the other fonts and icon sets, the widgets' data).
        private val LIBRARY_CREDITS = listOf(
            Credit(
                "Apache Lucene 10.5.2",
                "Copyright 2001-2025 The Apache Software Foundation",
                listOf(bundled("Apache License 2.0 (Lucene)", "Lucene-LICENSE.txt"), bundled("NOTICE (Lucene)", "Lucene-NOTICE.txt")),
                "מנוע החיפוש: lucene-core ושלוש מחלקות של lucene-analysis-common, ששונו כדי לפעול מאנדרואיד 4.4.",
            ),
            Credit(
                "Apache Commons Compress, IO, Lang, Codec",
                "Copyright 2001-2025 The Apache Software Foundation",
                listOf(APACHE, bundled("NOTICE (Apache Commons)", "Apache-Commons-NOTICE.txt")),
            ),
            Credit(
                "zstd-jni, Zstandard",
                "Copyright (c) 2015-present, Luben Karavelov; Copyright (c) Meta Platforms, Inc. and affiliates",
                listOf(bundled("BSD 2-Clause (zstd-jni)", "zstd-jni-LICENSE.txt"), bundled("BSD (Zstandard)", "Zstandard-LICENSE.txt")),
            ),
            Credit("jsoup", "Copyright (c) 2009-2025 Jonathan Hedley", listOf(bundled("MIT (jsoup)", "jsoup-LICENSE.txt"))),
            Credit(
                "SQLite",
                "SQLite and its multiplex VFS: public domain",
                listOf(Doc("נחלת הכלל", url = "https://www.sqlite.org/copyright.html")),
                "מנוע SQLite של היישום, שקורא גם ספרייה שנשמרת בחלקים בכרטיס זיכרון FAT32 (עם רכיב ה-multiplex של SQLite).",
            ),
            Credit("HebrewNumerals", "kdroidFilter", listOf(APACHE)),
            Credit("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Copyright JetBrains s.r.o. and contributors", listOf(APACHE)),
            Credit("SQLDelight, Kermit", "Copyright Square, Inc.; Touchlab", listOf(APACHE)),
            Credit("AndroidX Multidex", "Copyright The Android Open Source Project", listOf(APACHE)),
            Credit(
                "desugar_jdk_libs",
                "Copyright Google LLC; OpenJDK: Copyright Oracle and/or its affiliates",
                listOf(bundled("GNU GPL v2 עם חריגת Classpath", "desugar_jdk_libs-LICENSE.txt")),
                "ספריית Java של אנדרואיד 8 ומעלה, שהיישום כולל כדי לפעול באנדרואיד 4.4.",
            ),
        )

        private val ICON_CREDITS = listOf(
            Credit("Bootstrap Icons", "Copyright (c) 2019-2024 The Bootstrap Authors", listOf(bundled("MIT (Bootstrap Icons)", "Bootstrap-Icons-LICENSE.txt"))),
            Credit("Material Symbols, Material Icons", "Copyright Google LLC", listOf(APACHE)),
            Credit(
                "Emoji One",
                "Emoji One",
                listOf(Doc("CC BY 4.0", url = "https://creativecommons.org/licenses/by/4.0/")),
                "אייקון היד הכותבת (Writing hand, Monotone).",
            ),
        )

        private val FONT_CREDITS = listOf(
            Credit(
                "Taamey Frank CLM",
                "Copyright Yoram Gnat; Hebrew OpenType Layout logic (c) 2003 & 2007, Ralph Hancock & John Hudson",
                listOf(GPL, FONT_NOTICES),
                "ברישיון GPL v2 עם חריג הגופנים; לוגיקת הפריסה של הטעמים ברישיון MIT (בהודעות הגופנים).",
            ),
            Credit("Frank Ruhl Libre", "Copyright 2015 The Frank Ruhl Libre Project Authors", listOf(bundled("SIL OFL 1.1 (Frank Ruhl Libre)", "OFL-Frank-Ruhl-Libre.txt"))),
            Credit("Noto Serif Hebrew", "Copyright 2022 The Noto Project Authors", listOf(bundled("SIL OFL 1.1 (Noto)", "OFL-Noto-Hebrew.txt"))),
        )
    }
}

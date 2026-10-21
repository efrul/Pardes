package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.PARDES_LICENSE_URL

/**
 * The main app's MarkdownText, for the texts it shares with this edition (ABOUT_MD, CONDITIONS_MARKDOWN): headings,
 * paragraphs, quotes, **bold** and [links](…), drawn as one text whose links a touch opens and a keypad's ▲▼ go through
 * (OK opens one), in the accent. Pardes's licence link opens the copy in the app, as the main app's BundledLicenceLinks
 * does; the others open in the browser.
 */
object MarkdownText {
    fun view(activity: Activity, colors: Colors, markdown: String): TextView {
        val text = SpannableStringBuilder(LineText.fromHtml(toHtml(Bidi.compat(markdown))))
        text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            val url = span.url
            text.removeSpan(span)
            text.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = open(activity, url)
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return KeypadLinks(activity).apply {
            setText(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(colors.text)
            typeface = Fonts.ui(activity)
            // Every paragraph right to left, as the main app lays out a line that starts with an isolated Latin passage
            // (the §7b line): Android 4.4 takes that passage's embedding for the paragraph's direction.
            textDirection = View.TEXT_DIRECTION_RTL
            setLineSpacing(0f, 1.2f)
            setLinkTextColor(colors.accent)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    /**
     * A text whose links a keypad goes through within the part of it on screen, the page scrolling between them:
     * LinkMovementMethod takes the whole text for its view (the page scrolls, not the text), so ▲▼ would pick a link a
     * screen away, out of sight, and skip the text in between. Such a step is undone and left to the page (its
     * ScrollView), which scrolls by half a screen; the link is picked once it is in view.
     */
    private class KeypadLinks(context: Context) : TextView(context) {
        private val visible = Rect()

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return super.onKeyDown(keyCode, event)
            val spannable = text as? Spannable ?: return super.onKeyDown(keyCode, event)
            val start = Selection.getSelectionStart(spannable)
            val end = Selection.getSelectionEnd(spannable)
            if (!super.onKeyDown(keyCode, event)) return false
            val layout = layout ?: return true
            val at = Selection.getSelectionStart(spannable)
            if (at < 0 || !getLocalVisibleRect(visible)) return true
            val line = layout.getLineForOffset(at)
            val top = layout.getLineTop(line) + totalPaddingTop
            val bottom = layout.getLineBottom(line) + totalPaddingTop
            if (top >= visible.top && bottom <= visible.bottom) return true
            if (start >= 0) Selection.setSelection(spannable, start, end) else Selection.removeSelection(spannable)
            return false
        }
    }

    /** A link of these texts: the bundled licence for Pardes's, else the browser (its address shown when there is none). */
    fun open(activity: Activity, url: String) {
        if (url == PARDES_LICENSE_URL) {
            TextActivity.showAsset(activity, PARDES_LICENCE_TITLE, PARDES_LICENCE_ASSET)
            return
        }
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, url, Toast.LENGTH_SHORT).show()
        }
    }

    /** Enough Markdown for these texts: ### and ## headings, **bold**, [links](…), > quotes and paragraphs. */
    fun toHtml(md: String): String {
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

    /** Pardes's licence as Settings › רישיונות names it (the main app's Docs.pardes), and its bundled text. */
    const val PARDES_LICENCE_TITLE = "GNU AGPL v3 ותנאי 7ב"
    const val PARDES_LICENCE_ASSET = "licenses/Pardes-LICENSE.txt"
}

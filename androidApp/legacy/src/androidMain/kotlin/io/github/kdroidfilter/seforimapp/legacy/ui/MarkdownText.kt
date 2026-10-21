package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.text.Layout
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
import android.widget.ScrollView
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
     * A text whose links a keypad goes through in their order with the page (its ScrollView) scrolling between them, as a
     * reader expects. LinkMovementMethod takes the whole text for its view (the page scrolls, not the text), so its ▲▼
     * would pick a link a screen away and skip the text in between. Here ▲▼ pick the next link (LinkMovementMethod's
     * order) when it is in sight or within the half screen the page scrolls by, and bring it into view; a link further
     * away is left alone and the page scrolls half a screen towards it.
     *
     * The selection is never left on a link out of sight while the text is focused, and a link picked is brought into
     * view at once: the TextView brings its selection into view only before a frame (onPreDraw's bringPointIntoView), so
     * a selection set and taken back sent the page back to the link it was leaving (a small screen never got past a link
     * whose next was more than 1.5 screens away), and a selection moved between two lines out of sight, which redraws
     * nothing on screen, brought nothing into view. With no link picked, or the one picked out of sight, the next is the
     * first in sight (▼) or the last (▲); OK opens only a link in sight.
     */
    private class KeypadLinks(context: Context) : TextView(context) {
        private val visible = Rect()

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            val down = keyCode == KeyEvent.KEYCODE_DPAD_DOWN
            val ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
            if (!down && keyCode != KeyEvent.KEYCODE_DPAD_UP && !ok) return super.onKeyDown(keyCode, event)
            val spannable = text as? Spannable
            val layout = layout
            val page = page()
            if (spannable == null || layout == null || page == null || !event.hasNoModifiers() || !getLocalVisibleRect(visible)) {
                return super.onKeyDown(keyCode, event)
            }
            val a = Selection.getSelectionStart(spannable)
            val b = Selection.getSelectionEnd(spannable)
            val selStart = minOf(a, b)
            val selEnd = maxOf(a, b)
            val picked = selStart in 0 until selEnd && top(layout, selStart) >= visible.top && bottom(layout, selEnd) <= visible.bottom
            if (ok) {
                if (selStart >= 0 && !picked) Selection.removeSelection(spannable)
                return super.onKeyDown(keyCode, event)
            }
            var next: ClickableSpan? = null
            var nextAt = 0
            for (link in spannable.getSpans(0, spannable.length, ClickableSpan::class.java)) {
                val start = spannable.getSpanStart(link)
                val end = spannable.getSpanEnd(link)
                if (down) {
                    val after = if (picked) start > selStart else top(layout, start) >= visible.top
                    if (after && (next == null || start < nextAt)) {
                        next = link
                        nextAt = start
                    }
                } else {
                    val before = if (picked) end < selEnd else bottom(layout, end) <= visible.bottom
                    if (before && (next == null || end > nextAt)) {
                        next = link
                        nextAt = end
                    }
                }
            }
            val jump = page.maxScrollAmount
            // No link that way: the page's own ▲▼ (it scrolls up to half a screen, or the focus moves on to what is under
            // the text)
            if (next == null) {
                val room = scrollRoom(page, if (down) jump else -jump)
                if (selStart >= 0 && !(picked && stays(layout, selStart, selEnd, room))) Selection.removeSelection(spannable)
                return false
            }
            val start = spannable.getSpanStart(next)
            val end = spannable.getSpanEnd(next)
            val near = if (down) bottom(layout, end) <= visible.bottom + jump else top(layout, start) >= visible.top - jump
            val step = if (near) 0 else scrollRoom(page, if (down) jump else -jump)
            if (step == 0) {
                // ▲ ends the selection at the link's start, so that its first line is the one brought into view
                if (down) Selection.setSelection(spannable, start, end) else Selection.setSelection(spannable, end, start)
                bringPointIntoView(if (down) end else start)
                return true
            }
            page.smoothScrollBy(0, step)
            if (selStart >= 0 && !(picked && stays(layout, selStart, selEnd, step))) Selection.removeSelection(spannable)
            return true
        }

        /** Whether the selection [selStart]–[selEnd], in sight, stays in sight once the page has scrolled by [dy]. */
        private fun stays(layout: Layout, selStart: Int, selEnd: Int, dy: Int): Boolean =
            top(layout, selStart) >= visible.top + dy && bottom(layout, selEnd) <= visible.bottom + dy

        /** The page this text scrolls in. */
        private fun page(): ScrollView? {
            var parent = parent
            while (parent != null) {
                if (parent is ScrollView) return parent
                parent = parent.parent
            }
            return null
        }

        /** The top of [offset]'s line, in this view. */
        private fun top(layout: Layout, offset: Int): Int = layout.getLineTop(layout.getLineForOffset(offset)) + totalPaddingTop

        /** The bottom of the line of the character before [end] (a span's or a selection's end), in this view. */
        private fun bottom(layout: Layout, end: Int): Int = layout.getLineBottom(layout.getLineForOffset(maxOf(0, end - 1))) + totalPaddingTop

        /** [dy] as far as [page] can scroll that way (ScrollView.smoothScrollBy's own bounds). */
        private fun scrollRoom(page: ScrollView, dy: Int): Int {
            val child = page.getChildAt(0) ?: return 0
            val max = maxOf(0, child.height - (page.height - page.paddingTop - page.paddingBottom))
            return (page.scrollY + dy).coerceIn(0, max) - page.scrollY
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

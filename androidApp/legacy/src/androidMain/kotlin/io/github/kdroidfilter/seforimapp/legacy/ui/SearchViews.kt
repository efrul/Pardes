package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.android.ui.search.BREADCRUMB_SEPARATOR
import io.github.kdroidfilter.seforimapp.legacy.R

// The search screen's rows and pieces (SearchActivity), drawn as the main app's UnifiedSearchBar and SearchScreen draw
// theirs: each kind of row is one view the list reuses, its texts set on bind.

/**
 * The space under each row of the search screen's list, as the main app's lists space theirs (Arrangement.spacedBy):
 * inside the row's view, under what it shows, as a ListView's rows have no margins. A result followed by "עוד N
 * תוצאות מ…" has none (the main app's is one item with it); the list's highlight leaves it out (SearchActivity).
 */
const val ROW_GAP_DP = 6

/** What a suggestion leads to, shown by its icon (UnifiedSearchBar SuggestionKind and SuggestionIcon). */
enum class SuggestionKind(val icon: Int) {
    PLACE(R.drawable.ic_journal_bookmark),
    TEXT_SEARCH(R.drawable.ic_search),
    AUTHOR(R.drawable.ic_writing_hand),
    BOOK(R.drawable.ic_book_open_tabs),
}

/** One thing a suggestion row does (RowAction): its button's label, and what it does. */
class RowAction(val label: String, val run: () -> Unit)

/**
 * A suggestion's text (SuggestionRow's label): its [parts] joined by " > " in grey, [emphasis] bold where it is, then
 * [detail] small and grey; [titleFirst] (a narrow list's book row) puts the title first and its categories after it.
 */
fun suggestionLabel(parts: List<String>, emphasis: String?, detail: String?, titleFirst: Boolean, colors: Colors, density: Float): CharSequence {
    val out = SpannableStringBuilder()
    fun bold(part: String) {
        val start = out.length
        out.append(part)
        val at = emphasis?.takeIf { it.isNotEmpty() }?.let { part.indexOf(it) } ?: -1
        if (at >= 0) out.setSpan(StyleSpan(Typeface.BOLD), start + at, start + at + emphasis!!.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    fun grey(text: String, sizeSp: Int = 0) {
        val start = out.length
        out.append(text)
        out.setSpan(ForegroundColorSpan(colors.disabled), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (sizeSp > 0) out.setSpan(AbsoluteSizeSpan((sizeSp * density + 0.5f).toInt()), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (titleFirst) {
        bold(parts.last())
        grey("  " + parts.dropLast(1).joinToString(BREADCRUMB_SEPARATOR))
        return out
    }
    parts.forEachIndexed { i, part ->
        if (i > 0) grey(BREADCRUMB_SEPARATOR)
        bold(part)
    }
    if (detail != null) grey("  $detail", 11)
    return out
}

/**
 * A suggestion row (SuggestionRow): its icon, its text on one line cut at the start so that its end (the book) shows,
 * then its buttons ([RowActionChip]), under the text when a narrow list has two of them. The [highlighted] row, the one
 * Enter runs, has the accent's tint. The buttons answer a touch; a keypad runs the row with OK and its buttons from a
 * long OK, so they take no focus (the list moves from row to row).
 */
class SuggestionView(context: Context, private val colors: Colors) : LinearLayout(context) {
    /** The row itself, with its highlight; the [ROW_GAP_DP] under it is the view's. */
    private val box = LinearLayout(context)
    private val icon = ImageView(context)
    private val text: TextView
    private val chipsBeside = LinearLayout(context)
    private val chipsUnder = LinearLayout(context)
    private val chipClick = OnClickListener { (it.tag as? RowAction)?.run?.invoke() }

    init {
        orientation = VERTICAL
        setPadding(0, 0, 0, context.dp(ROW_GAP_DP))
        box.orientation = VERTICAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.minimumHeight = context.dp(44)
        box.setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(36)
        }
        icon.setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
        row.addView(icon, lp(context.dp(16), context.dp(16)).apply { marginEnd = context.dp(8) })
        text = context.label(sizeSp = 15f, color = colors.text, typeface = Fonts.ui(context)).apply { setSingleLine(true) }
        row.addView(text, lp(0, WRAP, 1f))
        chipsBeside.orientation = HORIZONTAL
        chipsBeside.gravity = Gravity.CENTER_VERTICAL
        row.addView(chipsBeside, lp(WRAP, WRAP).apply { marginStart = context.dp(8) })
        box.addView(row, lp(MATCH, WRAP))
        chipsUnder.orientation = HORIZONTAL
        chipsUnder.gravity = Gravity.END
        box.addView(chipsUnder, lp(MATCH, WRAP).apply { topMargin = context.dp(4) })
        addView(box, lp(MATCH, WRAP))
    }

    fun bind(label: CharSequence, kind: SuggestionKind, titleFirst: Boolean, highlighted: Boolean, shown: List<RowAction>, under: Boolean): SuggestionView {
        icon.setImageResource(kind.icon)
        text.text = label
        text.ellipsize = if (titleFirst) TextUtils.TruncateAt.END else TextUtils.TruncateAt.START
        box.background = if (highlighted) roundRect(Palette.withAlpha(colors.accent, 0.12f), context.dp(4).toFloat()) else null
        chips(chipsBeside, if (under) emptyList() else shown)
        chips(chipsUnder, if (under) shown else emptyList())
        return this
    }

    /** The row's buttons, its views reused. */
    private fun chips(box: LinearLayout, actions: List<RowAction>) {
        box.visibility = if (actions.isEmpty()) GONE else VISIBLE
        while (box.childCount < actions.size) {
            box.addView(context.label(sizeSp = 12f, color = colors.text, maxLines = 1).apply {
                gravity = Gravity.CENTER
                minHeight = context.dp(32)
                setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
                background = states(
                    roundRect(Palette.withAlpha(colors.text, 0.07f), context.dp(6).toFloat()),
                    roundRect(colors.pressed, context.dp(6).toFloat()),
                    roundRect(colors.focus, context.dp(6).toFloat()),
                )
                isClickable = true
                isFocusable = false
                setOnClickListener(chipClick)
            }, lp(WRAP, WRAP).apply { marginStart = context.dp(6) })
        }
        for (i in 0 until box.childCount) {
            val chip = box.getChildAt(i) as TextView
            val action = actions.getOrNull(i)
            chip.visibility = if (action == null) GONE else VISIBLE
            chip.tag = action
            if (action != null && !TextUtils.equals(chip.text, action.label)) chip.text = action.label
        }
    }
}

/**
 * A result (ResultView): its book's title, bold, with its place in the book after it in grey, then two lines of the
 * passage (three on a narrow phone) in grey with the words found bold, and a line and the [ROW_GAP_DP] under it unless
 * "עוד N תוצאות מ…" follows ([MoreView]).
 */
class ResultView(context: Context, colors: Colors, compact: Boolean) : LinearLayout(context) {
    val title: TextView = context.label(sizeSp = 15f, color = colors.text, bold = true, maxLines = 1, typeface = Fonts.ui(context))
    val place: TextView = context.label(sizeSp = 13f, color = Palette.withAlpha(colors.text, 0.72f), maxLines = 1, typeface = Fonts.ui(context))
    val snippet: TextView
    private val line: View

    /** The result shown, to give it its place once known. */
    var lineId = -1L

    init {
        orientation = VERTICAL
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        }
        val head = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(title, lp(WRAP, WRAP).apply { marginEnd = context.dp(6) })
        head.addView(place, lp(0, WRAP, 1f))
        body.addView(head, lp(MATCH, WRAP))
        snippet = context.label(sizeSp = 14f, color = Palette.withAlpha(colors.text, 0.72f), maxLines = if (compact) 3 else 2, typeface = Fonts.ui(context)).apply {
            lineHeightFactor(1.65f)
            setPadding(0, context.dp(2), 0, context.dp(2))
        }
        body.addView(snippet, lp(MATCH, WRAP))
        addView(body, lp(MATCH, WRAP))
        line = View(context).apply { setBackgroundColor(Palette.withAlpha(colors.border, 0.5f)) }
        addView(line, lp(MATCH, maxOf(1, context.dp(1))).apply { marginStart = context.dp(8) })
    }

    fun setLast(last: Boolean) {
        line.visibility = if (last) VISIBLE else GONE
        val gap = if (last) context.dp(ROW_GAP_DP) else 0
        if (paddingBottom != gap) setPadding(0, 0, 0, gap)
    }
}

/** "עוד N תוצאות מ…" under a book's first result (ResultLink): its other results. A row of its own, for a keypad. */
class MoreView(context: Context, colors: Colors) : LinearLayout(context) {
    val text: TextView = context.label(sizeSp = 13f, color = colors.accent, maxLines = 1, typeface = Fonts.ui(context)).apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        minHeight = context.dp(36)
        setPadding(context.dp(8), 0, context.dp(8), context.dp(4))
    }

    init {
        orientation = VERTICAL
        setPadding(0, 0, 0, context.dp(ROW_GAP_DP))
        addView(text, lp(MATCH, WRAP))
        addView(View(context).apply { setBackgroundColor(Palette.withAlpha(colors.border, 0.5f)) }, lp(MATCH, maxOf(1, context.dp(1))).apply { marginStart = context.dp(8) })
    }
}

/**
 * A message in the list's place: the suggestions' "טוען…" and "לא נמצאו תוצאות" (small and grey, [small]), or the
 * results' "מחפש..." and "לא נמצאו תוצאות" ([sizeSp], seven eighths of the reader's text, in the text's colour).
 */
class MessageView(context: Context, private val colors: Colors) : LinearLayout(context) {
    private val text: TextView = context.label(color = colors.text, typeface = Fonts.ui(context)).apply { gravity = Gravity.CENTER }

    init {
        gravity = Gravity.CENTER
        addView(text, lp(MATCH, WRAP))
    }

    fun bind(message: String, small: Boolean, sizeSp: Float): MessageView {
        text.text = message
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (small) 12f else sizeSp)
        text.setTextColor(if (small) colors.disabled else colors.text)
        val pad = context.dp(if (small) 6 else 24)
        setPadding(0, pad, 0, pad)
        return this
    }
}

/**
 * A category tab over the results (CategoryTab): a pill, the accent's tint, border and text when chosen, a grey border
 * otherwise; the keypad's focus as the other chips'.
 */
fun Context.categoryTab(text: String, colors: Colors, selected: Boolean, onClick: () -> Unit): TextView =
    label(text, 13f, if (selected) colors.accent else colors.text, maxLines = 1, typeface = Fonts.ui(this)).apply {
        gravity = Gravity.CENTER
        minHeight = dp(34)
        setPadding(dp(12), dp(4), dp(12), dp(4))
        val r = dp(17).toFloat()
        val stroke = maxOf(1, dp(1))
        background = states(
            roundRect(if (selected) Palette.withAlpha(colors.accent, 0.15f) else 0, r, if (selected) colors.accent else Palette.withAlpha(colors.disabled, 0.5f), stroke),
            roundRect(colors.pressed, r, colors.border, stroke),
            roundRect(colors.focus, r, colors.accent, stroke),
        )
        keyable(this) { onClick() }
    }

/**
 * The search's progress beside its status (StatusRow): a thin rounded track with the accent over [fraction] of it, or
 * while that is not known a segment going across, moved ten times a second rather than at every frame, so that the
 * search keeps an old phone's one core.
 */
class ProgressTrack(context: Context, private val colors: Colors) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var step = 0

    /** From 0 to 1, or null while not known. */
    var fraction: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    private val tick = Runnable {
        step = (step + 1) % STEPS
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2
        paint.color = Palette.withAlpha(colors.disabled, 0.3f)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, paint)
        paint.color = colors.accent
        val known = fraction
        if (known != null) {
            val f = known.coerceIn(0f, 1f)
            // From the start: the right in a right-to-left row
            if (layoutDirection == LAYOUT_DIRECTION_RTL) rect.set(w * (1 - f), 0f, w, h) else rect.set(0f, 0f, w * f, h)
            canvas.drawRoundRect(rect, r, r, paint)
            return
        }
        val segment = w / 3
        val x = (w + segment) * step / STEPS - segment
        val from = if (layoutDirection == LAYOUT_DIRECTION_RTL) w - x - segment else x
        rect.set(maxOf(0f, from), 0f, minOf(w, from + segment), h)
        if (rect.width() > 0) canvas.drawRoundRect(rect, r, r, paint)
        removeCallbacks(tick)
        if (visibility == VISIBLE && windowVisibility == VISIBLE) postDelayed(tick, TICK_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val STEPS = 20
        const val TICK_MS = 100L
    }
}

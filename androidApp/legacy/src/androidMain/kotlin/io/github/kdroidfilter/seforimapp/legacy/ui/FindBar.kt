package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.legacy.R

/**
 * The reader's find-in-page bar, as the main app's (FindBar, on a phone): across the top of the text, on a band of the
 * panel's colour, a raised panel with the search icon, the field ("הקלד כדי לחפש בעמוד..."), the "3/861" counter (red
 * when nothing matches, hidden while counting), ▲ "התוצאה הקודמת", ▼ "התוצאה הבאה" and ✕ "סגירת החיפוש". When
 * [compact] (under 300dp) the icon and the divider give the field their room, the buttons are 36dp, and the counter
 * waits for a query.
 *
 * Keys: Enter, the keyboard's Search and a keypad's OK step to the next match, Shift+Enter to the one before, Esc
 * closes. In the field ▼ and ▲ step too, a keypad's way through the matches while its digits type; ◀ ▶ reach the
 * buttons, and ▼ from them goes down to the text ([textId]).
 */
class FindBar(context: Context, private val colors: Colors, private val compact: Boolean, private val actions: Actions) : FrameLayout(context) {
    interface Actions {
        fun onQuery(query: String)

        fun onStep(forward: Boolean)

        fun onClose()
    }

    val field: EditText
    private val counter: TextView
    private val up: ImageView
    private val down: ImageView
    private val close: ImageView

    /** The field set by the reader (a search result's query), not typed: no query to report. */
    private var setting = false

    init {
        // The band: the bar's 6dp on every side (the main app's FindBarRoom, 60dp, is the band's height)
        val band = context.dp(BAND_DP)
        setPadding(band, band, band, band)
        background = BandBackground(colors, context.resources.displayMetrics.density, band.toFloat())
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(48)
            setPaddingRelative(context.dp(10), 0, context.dp(2), 0)
        }
        if (!compact) {
            bar.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_search)
                setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
            }, lp(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(8) })
        }
        field = EditText(context).apply {
            id = View.generateViewId()
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Fonts.ui(context)
            setTextColor(colors.text)
            // The placeholder a size smaller than the text, as the main app's (14sp under a 15sp field)
            hint = SpannableString(PLACEHOLDER).apply { setSpan(AbsoluteSizeSpan(14, true), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            setHintTextColor(colors.disabled)
            background = null
            setPadding(0, context.dp(8), 0, context.dp(8))
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event == null || event.action == KeyEvent.ACTION_DOWN) actions.onStep(forward = true)
                    true
                } else {
                    false
                }
            }
            setOnKeyListener { _, keyCode, event -> fieldKey(keyCode, event) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (setting) return
                    val text = s?.toString().orEmpty()
                    // One line: pasted line breaks ("העתק עם מקור" ends with "\n\n(<source>)") become spaces, so the
                    // whole query is shown and searched, as the main app's field does
                    val flat = text.replace(LINE_BREAKS, " ")
                    if (flat != text) {
                        setQuery(flat, selectAll = false)
                    }
                    actions.onQuery(flat)
                }
            })
        }
        bar.addView(field, lp(0, WRAP, 1f))
        counter = context.label("", 12f, colors.secondary, maxLines = 1).apply { visibility = GONE }
        bar.addView(counter, lp(WRAP, WRAP).apply { marginStart = context.dp(6) })
        if (!compact) {
            bar.addView(View(context).apply { setBackgroundColor(colors.border) }, lp(maxOf(1, context.dp(1)), context.dp(20)).apply {
                marginStart = context.dp(4)
                marginEnd = context.dp(4)
            })
        }
        // Up/down, not left/right: unambiguous in a right-to-left layout
        up = button(R.drawable.ic_arrow_up, "התוצאה הקודמת", colors.text) { actions.onStep(forward = false) }
        down = button(R.drawable.ic_arrow_down, "התוצאה הבאה", colors.text) { actions.onStep(forward = true) }
        close = button(R.drawable.ic_close, "סגירת החיפוש", colors.secondary) { actions.onClose() }
        listOf(up, down, close).forEach { bar.addView(it) }
        addView(bar, LayoutParams(MATCH, WRAP))
    }

    private fun button(icon: Int, description: String, tint: Int, onClick: () -> Unit): ImageView =
        context.iconButton(icon, tint, description, colors) { onClick() }.apply {
            id = View.generateViewId()
            if (compact) {
                val size = context.dp(36)
                layoutParams = LinearLayout.LayoutParams(size, size)
                val pad = context.dp(7)
                setPadding(pad, pad, pad, pad)
                background = states(colors, 0, size / 2f)
            }
        }

    /** A keypad's ▼ from the buttons goes down to [id], the text: the bar lies over it, which focus search skips. */
    fun textBelow(id: Int) {
        listOf(up, down, close).forEach { it.nextFocusDownId = id }
    }

    /**
     * The field set to [query] without reporting it: [selectAll] selects it, so that typing replaces it; else the
     * cursor goes to its end, or to its start when [fromStart] (a search result's query, shown from its first word).
     */
    fun setQuery(query: String, selectAll: Boolean, fromStart: Boolean = false) {
        setting = true
        field.setText(query)
        when {
            selectAll -> field.setSelection(0, query.length)
            fromStart -> field.setSelection(0)
            else -> field.setSelection(query.length)
        }
        setting = false
    }

    /**
     * The counter: [text] ("3/861", or the count before a match is reached), null while counting or under two
     * letters; [none] when nothing matches, in red, with ▲ ▼ greyed.
     */
    fun setCounter(text: String?, none: Boolean) {
        val shown = text != null && !(compact && field.text.isNullOrEmpty())
        counter.visibility = if (shown) VISIBLE else GONE
        if (shown) {
            counter.text = text
            counter.setTextColor(if (none) colors.error else colors.secondary)
        }
        val tint = if (none) colors.disabled else colors.text
        up.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        down.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
    }

    /**
     * Enter steps (Shift back); a keypad's OK steps once there is a query (in an empty field it stays the field's
     * own); ▼ and ▲ step; Esc closes.
     */
    private fun fieldKey(keyCode: Int, event: KeyEvent): Boolean {
        val pressed = event.action == KeyEvent.ACTION_DOWN
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (pressed) actions.onStep(forward = !event.isShiftPressed)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (field.text.isNullOrBlank()) return false
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) actions.onStep(forward = true)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP -> {
                if (pressed) actions.onStep(forward = keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                if (pressed) actions.onClose()
                true
            }
            else -> false
        }
    }

    /**
     * The band of the panel's colour over the text card's top (its top corners rounded and its border drawn as the
     * card's, so no line scrolled under it shows at the edge), and the bar on it: its 6dp shadow (a blurred copy of its
     * shape, made once for its size: Android 4.4 has no elevation), its panel ground, 10dp corners and 1dp border.
     */
    private class BandBackground(private val colors: Colors, private val density: Float, private val inset: Float) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.panel }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.border
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, density)
        }
        private val shade = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = if (colors.dark) 0x99000000.toInt() else 0x33000000 }
        private val bandPath = Path()
        private val edgePath = Path()
        private val bar = RectF()
        private val outline = RectF()
        private var shadow: Bitmap? = null
        private val blur = 5 * density
        private val drop = 2 * density
        private val radius = 10 * density

        override fun onBoundsChange(bounds: Rect) {
            bandPath.reset()
            bandPath.addRoundRect(RectF(bounds), floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
            // The card's border along the band's sides and top (Ui.island: 10dp corners, 1dp)
            val half = border.strokeWidth / 2
            val l = bounds.left + half
            val t = bounds.top + half
            val r = bounds.right - half
            val b = bounds.bottom.toFloat()
            val c = radius - half
            edgePath.reset()
            edgePath.moveTo(l, b)
            edgePath.lineTo(l, t + c)
            edgePath.arcTo(RectF(l, t, l + 2 * c, t + 2 * c), 180f, 90f)
            edgePath.lineTo(r - c, t)
            edgePath.arcTo(RectF(r - 2 * c, t, r, t + 2 * c), 270f, 90f)
            edgePath.lineTo(r, b)
            bar.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
            outline.set(bar.left + half, bar.top + half, bar.right - half, bar.bottom - half)
            // The old mask is left to the collector: a display list recorded with it may still be drawn once
            shadow = null
            val w = (bar.width() + 2 * blur).toInt()
            val h = (bar.height() + 2 * blur).toInt()
            if (w <= 0 || h <= 0) return
            // An alpha mask, drawn in the shadow's colour: a quarter of a colour bitmap's memory
            shadow = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { mask ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }
                Canvas(mask).drawRoundRect(RectF(blur, blur, blur + bar.width(), blur + bar.height()), radius, radius, paint)
            }
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(bandPath, fill)
            canvas.drawPath(edgePath, border)
            shadow?.let { canvas.drawBitmap(it, bar.left - blur, bar.top - blur + drop, shade) }
            canvas.drawRoundRect(bar, radius, radius, fill)
            canvas.drawRoundRect(outline, radius, radius, border)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        const val PLACEHOLDER = "הקלד כדי לחפש בעמוד..."

        /** The band around the bar: 6 + 48 + 6dp, the main app's FindBarRoom. */
        const val BAND_DP = 6

        private val LINE_BREAKS = Regex("[\r\n]+")
    }
}

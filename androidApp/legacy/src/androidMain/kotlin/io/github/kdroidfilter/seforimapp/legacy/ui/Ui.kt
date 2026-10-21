package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.legacy.PardesApp
import io.github.kdroidfilter.seforimapp.legacy.R

/**
 * The main app's colours (ZayitTheme's desktop "Islands" palette with the gold accent, its default), and the keypad's
 * focus colour, which the main app has no use for: a phone with keys moves a highlight from row to row.
 */
data class Colors(
    val dark: Boolean,
    val ground: Int,
    val panel: Int,
    val border: Int,
    val text: Int,
    val secondary: Int,
    val disabled: Int,
    val accent: Int,
    val accentTint: Int,
    val selected: Int,
    val pressed: Int,
    val focus: Int,
    val searchHit: Int,
    val error: Int,
    /** The title bar: its ground and the accent tone at its peak (ZayitTheme titleBarGradientColor, 25% accent). */
    val barGround: Int,
    val barTone: Int,
    /** The Home logo's gold (light gold in the light theme, the accent in the dark one, as the main app). */
    val logo: Int,
)

object Palette {
    private const val GOLD_LIGHT = 0xFFBE9117.toInt()
    private const val GOLD_DARK = 0xFFD4A843.toInt()

    val light = Colors(
        dark = false,
        ground = 0xFFF2F3F5.toInt(),
        panel = 0xFFFFFFFF.toInt(),
        border = 0xFFEBECF0.toInt(),
        text = 0xFF1F1F1F.toInt(),
        secondary = 0xFF6C707E.toInt(),
        disabled = 0xFFA8ADBD.toInt(),
        accent = GOLD_LIGHT,
        accentTint = withAlpha(GOLD_LIGHT, 0.14f),
        selected = 0xFFEDEFF3.toInt(),
        pressed = 0xFFE4E6EB.toInt(),
        focus = withAlpha(GOLD_LIGHT, 0.22f),
        searchHit = 0xFFFBEBB5.toInt(),
        error = 0xFFDB3B4B.toInt(),
        barGround = 0xFFF7F8FA.toInt(),
        barTone = blend(0xFFF7F8FA.toInt(), GOLD_LIGHT, 0.25f),
        logo = GOLD_LIGHT,
    )

    val dark = Colors(
        dark = true,
        ground = 0xFF1E1F22.toInt(),
        panel = 0xFF2B2D30.toInt(),
        border = 0xFF393B40.toInt(),
        text = 0xFFDFE1E5.toInt(),
        secondary = 0xFF9DA0A8.toInt(),
        disabled = 0xFF6F737A.toInt(),
        accent = GOLD_DARK,
        accentTint = withAlpha(GOLD_DARK, 0.20f),
        selected = 0xFF393B40.toInt(),
        pressed = 0xFF43454A.toInt(),
        focus = withAlpha(GOLD_DARK, 0.26f),
        searchHit = 0xFF5C4A12.toInt(),
        error = 0xFFF75464.toInt(),
        barGround = 0xFF1E1F22.toInt(),
        barTone = blend(0xFF1E1F22.toInt(), GOLD_DARK, 0.25f),
        logo = GOLD_DARK,
    )

    fun withAlpha(color: Int, alpha: Float): Int = (color and 0xFFFFFF) or ((alpha * 255).toInt() shl 24)

    /** Channel by channel in sRGB, fully opaque (desktop ThemeUtils.titleBarGradientColor). */
    fun blend(background: Int, accent: Int, t: Float): Int {
        fun ch(shift: Int) = (((background shr shift) and 0xFF) * (1 - t) + ((accent shr shift) and 0xFF) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/** The bundled fonts (assets/fonts), each read once: Typeface.createFromAsset leaks a copy per call below Android 5. */
object Fonts {
    private val cache = HashMap<String, Typeface>()

    fun asset(context: Context, path: String?): Typeface {
        if (path == null) return Typeface.DEFAULT
        return synchronized(cache) {
            cache.getOrPut(path) { runCatching { Typeface.createFromAsset(context.assets, path) }.getOrDefault(Typeface.DEFAULT) }
        }
    }

    /** The book text's font (Settings; the main app's default is Noto Serif Hebrew). */
    fun book(context: Context): Typeface = asset(context, PardesApp.of(context).prefs.bookFont.asset)

    /**
     * The main app's text for everything but the book (ZayitFonts.ui, merged into every one of its text styles): Noto
     * Serif Hebrew, whatever the book's font. [label] sets it on every text of the screens.
     */
    fun ui(context: Context): Typeface = asset(context, "fonts/notoserifhebrew.ttf")

    /**
     * [ui] in bold, for what measures a bold label's width. The bundled file has one weight (the variable font's
     * regular instance on Android 4.4): Android draws it bolder, as Compose does in the main app.
     */
    fun uiBold(context: Context): Typeface = Typeface.create(ui(context), Typeface.BOLD)


    /** Commentaries, as the main app: Frank Ruhl Libre. */
    fun commentary(context: Context): Typeface = asset(context, "fonts/frankruhllibre.ttf")

    /** Targumim, with their cantillation: Taamey Frank CLM. */
    fun targum(context: Context): Typeface = asset(context, "fonts/taameyfrankclm_medium.ttf")

    /**
     * The word פרדס of the Home, in the icon's letters: Frank Ruhl Libre Black. Android 4.4 cannot pick a weight of the
     * bundled variable font, so this is its Black instance (wght 900) cut down to the four letters, made with fontTools
     * (instancer, then subset; README.md).
     */
    fun logo(context: Context): Typeface = asset(context, "fonts/frankruhllibre_black_pardes.ttf")
}

fun Context.dp(value: Number): Int = (value.toFloat() * resources.displayMetrics.density + 0.5f).toInt()

fun Context.label(
    text: CharSequence = "",
    sizeSp: Float = 16f,
    color: Int,
    bold: Boolean = false,
    maxLines: Int = 0,
    typeface: Typeface? = null,
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    // The main app's UI font unless another is given (the logo, a book font's sample), bold made from it.
    setTypeface(typeface ?: Fonts.ui(context), if (bold) Typeface.BOLD else Typeface.NORMAL)
    if (maxLines > 0) {
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
    }
}

/**
 * Puts the lines of this text [factor] times its size apart (Compose's lineHeight, the main app's READER_LINE_HEIGHT),
 * whatever its font's own spacing. Call it after the font and the size are set.
 */
fun TextView.lineHeightFactor(factor: Float): Float {
    val metrics = paint.fontMetricsInt
    val extra = (textSize * factor - (metrics.descent - metrics.ascent)).coerceAtLeast(0f)
    setLineSpacing(extra, 1f)
    return extra
}

/**
 * A block of text as Compose lays it out (the main app's lines and links): no font padding above the first line or
 * below the last (includeFontPadding false, LineHeightStyle's trim), [top] and [bottom] around it. Before Android 9 the
 * line spacing is added under the last line too: it takes the place of as much of [bottom].
 */
fun TextView.trimmedBlock(factor: Float, top: Int, bottom: Int) {
    includeFontPadding = false
    val extra = lineHeightFactor(factor).toInt()
    val under = if (Build.VERSION.SDK_INT >= 28) bottom else maxOf(0, bottom - extra)
    setPaddingRelative(paddingStart, top, paddingEnd, under)
}

fun roundRect(fill: Int, radius: Float, stroke: Int? = null, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        if (stroke != null) setStroke(strokeWidth, stroke)
    }

/**
 * A background by state: pressed (touch), focused (a keypad's highlight), else [normal]. There is no ripple before
 * Android 5.0. A list's row is not focused but selected, and its list draws the highlight (its selector): the row's
 * own would double it.
 */
fun states(normal: Drawable, pressed: Drawable, focused: Drawable): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), pressed)
    addState(intArrayOf(android.R.attr.state_focused), focused)
    addState(intArrayOf(), normal)
}

fun states(colors: Colors, normal: Int = 0, radius: Float = 0f): StateListDrawable =
    if (radius == 0f) {
        states(ColorDrawable(normal), ColorDrawable(colors.pressed), ColorDrawable(colors.focus))
    } else {
        states(roundRect(normal, radius), roundRect(colors.pressed, radius), roundRect(colors.focus, radius))
    }

/** The main app's Island: a panel with rounded corners (10dp) and a 1dp border, on the ground colour. */
fun Context.island(colors: Colors): Drawable = roundRect(colors.panel, dp(10).toFloat(), colors.border, maxOf(1, dp(1)))

fun lp(width: Int, height: Int, weight: Float = 0f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, height, weight)

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Makes [view] reachable from a keypad and clickable, with [onClick]. */
fun keyable(view: View, onClick: (View) -> Unit) {
    view.isFocusable = true
    view.isClickable = true
    view.setOnClickListener(onClick)
}

/**
 * The main app's title bar background (ZayitTheme TitleBarGradient, desktop's IntUi title bar): its ground with the
 * accent's tone peaking at the left, a gradient from 100dp before the left edge to 400dp into the bar, through the tone.
 */
class TitleBarBackground(private val colors: Colors, private val density: Float) : Drawable() {
    private val paint = Paint()

    override fun onBoundsChange(bounds: Rect) {
        val left = bounds.left.toFloat()
        paint.shader = LinearGradient(
            left - 100 * density, 0f, left + 400 * density, 0f,
            intArrayOf(colors.barGround, colors.barTone, colors.barGround), null, Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) = canvas.drawRect(bounds, paint)

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

/** The main app's ZIconButton: a 22dp icon in a 44dp circle, focusable. */
fun Context.iconButton(icon: Int, tint: Int, description: String, colors: Colors, onClick: (View) -> Unit): ImageView =
    ImageView(this).apply {
        setImageResource(icon)
        setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val pad = dp(11)
        setPadding(pad, pad, pad, pad)
        val size = dp(44)
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = states(colors, 0, size / 2f)
        keyable(this, onClick)
    }

/**
 * A menu row: its label and what it does; with no action, a section's title (grey, not a stop for the keypad). [tint]:
 * the icon's colour when it is not the text's (the reader's starred place, in the accent).
 */
class MenuEntry(val label: String, val icon: Int? = null, val tint: Int? = null, val action: (() -> Unit)? = null)

/**
 * The phone title bar of the main app (PhoneTopBar): an optional leading button on the right, the title in bold with
 * a line under it, then icon actions and the ⋮ menu. The menu also opens from a keypad's Menu key (BaseActivity).
 */
class TopBar(context: Context, private val colors: Colors) : LinearLayout(context) {
    val title: TextView
    val subtitle: TextView
    private val actions = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private var menuButton: ImageView? = null
    private var menuItems: (() -> List<MenuEntry>)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = TitleBarBackground(colors, context.resources.displayMetrics.density)
        minimumHeight = context.dp(52)
        setPadding(context.dp(4), 0, context.dp(4), 0)
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(6), 0, context.dp(6), 0)
        }
        title = context.label(sizeSp = 17f, color = colors.text, bold = true, maxLines = 1)
        subtitle = context.label(sizeSp = 12f, color = colors.secondary, maxLines = 1).apply { visibility = GONE }
        texts.addView(title)
        texts.addView(subtitle)
        addView(texts, lp(0, WRAP, 1f))
        addView(actions, lp(WRAP, WRAP))
    }

    /** The button before the title: Back, the table of contents, the library. */
    fun leading(icon: Int, description: String, onClick: () -> Unit): ImageView =
        context.iconButton(icon, colors.text, description, colors) { onClick() }.also { addView(it, 0) }

    fun setSubtitle(text: CharSequence?) {
        subtitle.text = text
        subtitle.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE
    }

    fun action(icon: Int, description: String, onClick: (View) -> Unit): ImageView =
        context.iconButton(icon, colors.text, description, colors, onClick).also { actions.addView(it) }

    /** The ⋮ menu: [items] are read each time it opens. */
    fun overflow(items: () -> List<MenuEntry>) {
        menuItems = items
        menuButton = action(R.drawable.ic_more_vert, "אפשרויות נוספות") { showMenu() }
    }

    /** Opens the ⋮ menu (its button, or a keypad's Menu key); false when this bar has none. */
    fun showMenu(): Boolean {
        val anchor = menuButton ?: return false
        val list = menuItems?.invoke() ?: return false
        Dialogs.popup(anchor, colors, list)
        return true
    }
}

/**
 * A list adapter over [items], drawing each with [bind] (given the view to reuse, if any). Rows of several kinds give
 * their number ([types]) and each item's ([typeOf], from 0): the list then hands a row only a view of its own kind to
 * reuse, where with one kind it would hand a wrong one and a new row be built at each step of a scroll.
 */
class ItemsAdapter<T>(
    private val types: Int = 1,
    private val typeOf: (item: T) -> Int = { 0 },
    private val bind: (item: T, reuse: View?, parent: ViewGroup) -> View,
) : BaseAdapter() {
    var items: List<T> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): T = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = types

    override fun getItemViewType(position: Int): Int = typeOf(items[position])

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = bind(items[position], convertView, parent)
}

/**
 * A row of the main app's library tree (LibraryTree TreeRow): indented by its depth, a ‹ or ⌄ for a category, a
 * folder or book icon, the title in [titleLines] lines at most (the tree's one, the table of contents' two). Also the
 * rows of the books opened lately and the favourites, and of the menus.
 */
class TreeRow(context: Context, private val colors: Colors, titleLines: Int = 2) : LinearLayout(context) {
    private val arrow = ImageView(context)
    private val icon = ImageView(context)
    private val title: TextView
    private val second: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(44)
        arrow.setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
        addView(arrow, lp(context.dp(16), context.dp(16)).apply { marginEnd = context.dp(6) })
        icon.setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
        addView(icon, lp(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(8) })
        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        title = context.label(sizeSp = 15f, color = colors.text, maxLines = titleLines)
        second = context.label(sizeSp = 12f, color = colors.secondary, maxLines = 1).apply { visibility = GONE }
        texts.addView(title)
        texts.addView(second)
        addView(texts, lp(0, WRAP, 1f))
    }

    /**
     * [arrowRes]: R.drawable.ic_arrow_left (closed) or ic_arrow_down (open) for a category, null for none (with
     * [keepArrowSpace], its room stays, as the tree keeps a book in line with its siblings' titles).
     */
    fun bind(
        text: CharSequence,
        depth: Int = 0,
        iconRes: Int? = null,
        arrowRes: Int? = null,
        keepArrowSpace: Boolean = false,
        marked: Boolean = false,
        secondLine: CharSequence? = null,
        iconTint: Int = colors.secondary,
    ): TreeRow {
        // Indented from the start: the right, in a right-to-left tree.
        setPaddingRelative(context.dp(8 + depth * 16), context.dp(4), context.dp(10), context.dp(4))
        background = states(colors, if (marked) colors.selected else 0, context.dp(6).toFloat())
        arrow.visibility = if (arrowRes != null) VISIBLE else if (keepArrowSpace) INVISIBLE else GONE
        arrowRes?.let { arrow.setImageResource(it) }
        icon.visibility = if (iconRes != null) VISIBLE else GONE
        iconRes?.let {
            icon.setImageResource(it)
            icon.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
        }
        title.text = text
        title.setTypeface(Fonts.ui(context), if (marked) Typeface.BOLD else Typeface.NORMAL)
        second.text = secondLine
        second.visibility = if (secondLine.isNullOrEmpty()) GONE else VISIBLE
        return this
    }
}

/**
 * The main app's ZChip: a rounded chip with a border; chosen, the accent's tint and border and bold text; [icon] a
 * 16dp icon before the text (the commentaries' סנן).
 */
fun Context.chip(text: String, colors: Colors, selected: Boolean, icon: Int? = null, onClick: () -> Unit): TextView =
    label(text, 14f, if (selected) colors.text else colors.secondary, bold = selected, maxLines = 1).apply {
        gravity = Gravity.CENTER
        minHeight = dp(36)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        if (icon != null) {
            // Android 4.4 has no getDrawable(id, theme); the icons are PNGs there, made from the vectors at build time.
            @Suppress("DEPRECATION")
            val drawable = resources.getDrawable(icon).mutate()
            drawable.setColorFilter(if (selected) colors.accent else colors.secondary, PorterDuff.Mode.SRC_IN)
            drawable.setBounds(0, 0, dp(16), dp(16))
            setCompoundDrawablesRelative(drawable, null, null, null)
            compoundDrawablePadding = dp(6)
        }
        val r = dp(16).toFloat()
        val stroke = maxOf(1, dp(1))
        background = states(
            roundRect(if (selected) colors.accentTint else colors.panel, r, if (selected) colors.accent else colors.border, stroke),
            roundRect(colors.pressed, r, colors.border, stroke),
            roundRect(colors.focus, r, colors.accent, stroke),
        )
        keyable(this) { onClick() }
    }

/**
 * A button of the reader's bottom toolbar (ReaderRailButton): a 24dp icon over its 11sp label (two lines at most, 12sp
 * apart), chosen on a rounded selectedRow ground, faded when it has nothing to show for the line. The ניקוד button also
 * shows, in the empty band under its glyph, two dots lit for the layers shown (LevelDots).
 */
class RailButton(context: Context, private val colors: Colors, icon: Int, label: String, onClick: () -> Unit) : LinearLayout(context) {
    private val image = ImageView(context)
    private val dots = LevelDots(context, colors)
    private val text: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = context.dp(48)
        setPadding(0, context.dp(6), 0, context.dp(6))
        image.setImageResource(icon)
        val box = FrameLayout(context)
        box.addView(image, FrameLayout.LayoutParams(context.dp(24), context.dp(24)))
        box.addView(dots, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        dots.visibility = GONE
        addView(box, lp(context.dp(24), context.dp(24)))
        text = context.label(label, 11f, colors.secondary, maxLines = 2).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            lineHeightFactor(12f / 11f)
        }
        addView(text, lp(WRAP, WRAP).apply { topMargin = context.dp(2) })
        contentDescription = label
        keyable(this) { onClick() }
        setState(selected = false, dimmed = false)
    }

    fun setState(selected: Boolean, dimmed: Boolean, levels: Int? = null) {
        background = states(colors, if (selected) colors.selected else 0, context.dp(10).toFloat())
        image.setColorFilter(if (dimmed) colors.disabled else colors.text, PorterDuff.Mode.SRC_IN)
        text.setTextColor(if (dimmed) colors.disabled else colors.secondary)
        dots.visibility = if (levels == null) GONE else VISIBLE
        if (levels != null) dots.levels = levels
    }
}

/** The ניקוד button's two dots (desktop LevelIndicator): lit for each layer shown, none when nothing is. */
class LevelDots(context: Context, private val colors: Colors) : View(context) {
    var levels = 0
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = context.dp(3)
    private val gap = context.dp(3)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(dot * 2 + gap, dot)

    override fun onDraw(canvas: Canvas) {
        if (levels == 0) return
        val r = dot / 2f
        for (i in 0 until 2) {
            paint.color = if (i < levels) colors.text else colors.disabled
            canvas.drawCircle(r + i * (dot + gap), r, r, paint)
        }
    }
}

/**
 * Rows of children that wrap, each row centred (Compose's FlowRow, as the Home's catalog row), or from the start when
 * not [centered] (the search's tabs and status line): Android 4.4 has no such layout of its own.
 */
class FlowRow(context: Context, private val gapX: Int, private val gapY: Int, private val centered: Boolean = true) : ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0
        var widest = 0
        var rowHeight = 0
        var height = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + gapX + child.measuredWidth > width) {
                height += rowHeight + gapY
                x = 0
                rowHeight = 0
            }
            x += (if (x > 0) gapX else 0) + child.measuredWidth
            widest = maxOf(widest, x)
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        height += rowHeight
        // Asked to wrap its children (the search's status beside its progress): as wide as its widest row
        val measured = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) width else minOf(width, widest)
        setMeasuredDimension(measured, if (childCount == 0) 0 else height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        var top = 0
        var row = ArrayList<View>()
        var rowWidth = 0
        fun place() {
            var x = if (centered) (width - rowWidth) / 2 else if (rtl) width - rowWidth else 0
            val rowHeight = row.maxOfOrNull { it.measuredHeight } ?: 0
            // Right to left: the first child at the right, as Compose's FlowRow lays it out.
            for (child in if (rtl) row.asReversed() else row) {
                val y = top + (rowHeight - child.measuredHeight) / 2
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth + gapX
            }
            top += rowHeight + gapY
        }
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            if (row.isNotEmpty() && rowWidth + gapX + child.measuredWidth > width) {
                place()
                row = ArrayList()
                rowWidth = 0
            }
            rowWidth += (if (row.isNotEmpty()) gapX else 0) + child.measuredWidth
            row += child
        }
        if (row.isNotEmpty()) place()
    }
}

/** A section's title over a list or a card, as the main app's settings and Home sections. */
fun Context.sectionTitle(text: String, colors: Colors): TextView = label(text, 14f, colors.text, bold = true).apply {
    setPadding(dp(16), dp(14), dp(16), dp(6))
}

/** A thin line between rows. */
fun Context.divider(colors: Colors): View = View(this).apply {
    setBackgroundColor(colors.border)
    layoutParams = lp(MATCH, maxOf(1, dp(0.75f)))
}

/**
 * A settings switch drawn as the main app's (a pill track and a round thumb, the accent when on): Android 4.4's Switch
 * is Holo's square one.
 */
class Toggle(context: Context, private val colors: Colors) : View(context) {
    var checked = false
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(context.dp(44), context.dp(26))

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        rect.set(0f, 0f, w, h)
        paint.color = if (checked) colors.accent else colors.disabled
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        paint.color = 0xFFFFFFFF.toInt()
        val r = h / 2 - context.dp(3)
        // On is at the end, the left in a right-to-left screen, as the main app's switch
        val cx = if (checked != (layoutDirection == LAYOUT_DIRECTION_RTL)) w - h / 2 else h / 2
        canvas.drawCircle(cx, h / 2, r, paint)
    }
}

/** A settings row: a title, a line under it, and a [Toggle] at its end; the whole row toggles it (OK on a keypad). */
fun Context.toggleRow(title: String, description: String?, colors: Colors, checked: Boolean, onChange: (Boolean) -> Unit): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(10), dp(4), dp(10))
        background = states(colors, 0, dp(6).toFloat())
    }
    val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    texts.addView(label(title, 15f, colors.text))
    if (description != null) texts.addView(label(description, 12f, colors.secondary).apply { setPadding(0, dp(2), 0, 0) })
    row.addView(texts, lp(0, WRAP, 1f).apply { marginEnd = dp(10) })
    val toggle = Toggle(this, colors).apply { this.checked = checked }
    row.addView(toggle, lp(WRAP, WRAP))
    keyable(row) {
        toggle.checked = !toggle.checked
        onChange(toggle.checked)
    }
    return row
}

/**
 * The main app's checkbox (ZCheckRow's Material one): an 18dp rounded square, its border grey, filled with the accent
 * and ticked in white when checked.
 */
class CheckMark(context: Context, private val colors: Colors) : View(context) {
    var checked = false
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tick = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(context.dp(18), context.dp(18))

    override fun onDraw(canvas: Canvas) {
        val size = width.toFloat()
        val stroke = context.dp(2).toFloat()
        val r = context.dp(2).toFloat()
        if (checked) {
            paint.style = Paint.Style.FILL
            paint.color = colors.accent
            rect.set(0f, 0f, size, size)
            canvas.drawRoundRect(rect, r, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.SQUARE
            paint.color = 0xFFFFFFFF.toInt()
            tick.reset()
            tick.moveTo(size * 0.22f, size * 0.52f)
            tick.lineTo(size * 0.42f, size * 0.72f)
            tick.lineTo(size * 0.78f, size * 0.32f)
            canvas.drawPath(tick, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = colors.secondary
            rect.set(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }
}

/**
 * The main app's ZCheckRow: [CheckMark] and its text (15sp, two lines at most), the whole row a stop for a keypad whose
 * OK toggles it.
 */
fun Context.checkRow(text: String, colors: Colors, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
    val mark = CheckMark(this, colors).apply { this.checked = checked }
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(44)
        setPadding(dp(12), dp(4), dp(12), dp(4))
        background = states(colors, 0, dp(8).toFloat())
        addView(mark, lp(WRAP, WRAP).apply { marginEnd = dp(12) })
        addView(label(text, 15f, colors.text, maxLines = 2))
        keyable(this) {
            mark.checked = !mark.checked
            onChange(mark.checked)
        }
    }
}

/**
 * The main app's AccentButton: the accent, its text white and bold, rounded 10dp; disabled, the selected rows' grey
 * with a border and its text faded ([setAccentEnabled]), and not a stop for a keypad.
 */
fun Context.accentButton(text: String, colors: Colors, onClick: () -> Unit): TextView = label(text, 16f, 0xFFFFFFFF.toInt(), bold = true).apply {
    gravity = Gravity.CENTER
    minHeight = dp(44)
    setPadding(dp(22), dp(10), dp(22), dp(10))
    keyable(this) { onClick() }
    setAccentEnabled(colors, true)
}

fun TextView.setAccentEnabled(colors: Colors, enabled: Boolean) {
    val r = context.dp(10).toFloat()
    val stroke = maxOf(1, context.dp(1))
    background = if (enabled) {
        states(
            roundRect(colors.accent, r, colors.accent, stroke),
            roundRect(Palette.blend(colors.accent, 0xFF000000.toInt(), 0.15f), r, colors.accent, stroke),
            roundRect(Palette.blend(colors.accent, 0xFF000000.toInt(), 0.15f), r, colors.text, maxOf(2, context.dp(2))),
        )
    } else {
        roundRect(colors.selected, r, colors.border, stroke)
    }
    setTextColor(if (enabled) 0xFFFFFFFF.toInt() else colors.disabled)
    isEnabled = enabled
    isFocusable = enabled
}

/** A spinning progress indicator in the accent (Android 4.4's Holo one is blue). */
fun Context.spinner(colors: Colors): ProgressBar = ProgressBar(this).apply {
    indeterminateDrawable?.mutate()?.setColorFilter(colors.accent, PorterDuff.Mode.SRC_IN)
}

/** A thin progress line: the accent over the border's colour, rounded. */
fun Context.progressLine(colors: Colors): ProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
    val r = dp(2).toFloat()
    val track = roundRect(colors.border, r)
    val bar = ClipDrawable(roundRect(colors.accent, r), Gravity.START, ClipDrawable.HORIZONTAL)
    progressDrawable = LayerDrawable(arrayOf(track, bar)).apply {
        setId(0, android.R.id.background)
        setId(1, android.R.id.progress)
    }
}

/** A frame that centres one child: for a progress indicator or a message in a list's place. */
fun Context.centered(child: View): FrameLayout = FrameLayout(this).apply {
    addView(child, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
}

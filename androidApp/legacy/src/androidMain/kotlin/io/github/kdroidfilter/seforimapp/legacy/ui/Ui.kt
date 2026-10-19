package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import io.github.kdroidfilter.seforimapp.legacy.PardesApp
import io.github.kdroidfilter.seforimapp.legacy.R

/**
 * The colours of the main app's light and dark themes (ZayitTheme's "Islands" palette, gold accent), with Pardes's
 * olive and gold for the title bar, as on its icon.
 */
data class Colors(
    val dark: Boolean,
    val ground: Int,
    val panel: Int,
    val border: Int,
    val text: Int,
    val secondary: Int,
    val accent: Int,
    val accentTint: Int,
    val selected: Int,
    val pressed: Int,
    val barTop: Int,
    val barBottom: Int,
    val barText: Int,
    val searchHit: Int,
    val error: Int,
)

object Palette {
    val light = Colors(
        dark = false,
        ground = 0xFFF2F3F5.toInt(),
        panel = 0xFFFFFFFF.toInt(),
        border = 0xFFEBECF0.toInt(),
        text = 0xFF1F1F1F.toInt(),
        secondary = 0xFF6C707E.toInt(),
        accent = 0xFFBE9117.toInt(),
        accentTint = 0x24BE9117,
        selected = 0xFFFBF3DC.toInt(),
        pressed = 0xFFE4E6EB.toInt(),
        barTop = 0xFF3E4D24.toInt(),
        barBottom = 0xFF2B3619.toInt(),
        barText = 0xFFF6DC8C.toInt(),
        searchHit = 0xFFFBEBB5.toInt(),
        error = 0xFFDB3B4B.toInt(),
    )

    val dark = Colors(
        dark = true,
        ground = 0xFF1E1F22.toInt(),
        panel = 0xFF2B2D30.toInt(),
        border = 0xFF393B40.toInt(),
        text = 0xFFDFE1E5.toInt(),
        secondary = 0xFF9DA0A8.toInt(),
        accent = 0xFFD4A843.toInt(),
        accentTint = 0x33D4A843,
        selected = 0xFF3A3524.toInt(),
        pressed = 0xFF393B40.toInt(),
        barTop = 0xFF2C3719.toInt(),
        barBottom = 0xFF1C2410.toInt(),
        barText = 0xFFF6DC8C.toInt(),
        searchHit = 0xFF5C4A12.toInt(),
        error = 0xFFF75464.toInt(),
    )
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

    /** The book text's font (Settings). */
    fun book(context: Context): Typeface = asset(context, PardesApp.of(context).prefs.bookFont.asset)

    /** Commentaries, as the main app: Frank Ruhl Libre. */
    fun commentary(context: Context): Typeface = asset(context, "fonts/frankruhllibre.ttf")

    /** Targumim, with their cantillation: Taamey Frank CLM. */
    fun targum(context: Context): Typeface = asset(context, "fonts/taameyfrankclm_medium.ttf")

    /** The word פרדס of the title bar and the home screen, in the icon's font. */
    fun logo(context: Context): Typeface = asset(context, "fonts/frankruhllibre.ttf")
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
    if (typeface != null || bold) setTypeface(typeface ?: Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    if (maxLines > 0) {
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
    }
}

fun roundRect(fill: Int, radius: Float, stroke: Int? = null, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        if (stroke != null) setStroke(strokeWidth, stroke)
    }

/** A background that darkens while pressed (there is no ripple before Android 5.0). */
fun pressable(normal: Drawable, pressed: Drawable): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), pressed)
    addState(intArrayOf(android.R.attr.state_selected), pressed)
    addState(intArrayOf(), normal)
}

fun pressable(normal: Int, pressed: Int): StateListDrawable = pressable(ColorDrawable(normal), ColorDrawable(pressed))

fun Context.iconButton(icon: Int, tint: Int, description: String, onClick: (View) -> Unit): ImageView =
    ImageView(this).apply {
        setImageResource(icon)
        setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER
        val size = dp(48)
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = pressable(0, 0x33FFFFFF)
        setOnClickListener(onClick)
    }

fun lp(width: Int, height: Int, weight: Float = 0f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, height, weight)

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The olive title bar every screen opens with: Back (when [onBack]), the title, then icon actions and a ⋮ menu, in
 * that order from the right.
 */
class TopBar(context: Context, private val colors: Colors, onBack: (() -> Unit)?) : LinearLayout(context) {
    val title: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(colors.barTop, colors.barBottom))
        minimumHeight = context.dp(52)
        setPadding(context.dp(if (onBack == null) 12 else 0), 0, 0, 0)
        if (onBack != null) addView(context.iconButton(R.drawable.ic_back, colors.barText, "חזרה") { onBack() })
        title = context.label(sizeSp = 19f, color = colors.barText, bold = true, maxLines = 1).apply {
            layoutParams = lp(0, WRAP, 1f)
            setPadding(context.dp(4), 0, context.dp(4), 0)
        }
        addView(title)
    }

    fun action(icon: Int, description: String, onClick: (View) -> Unit): ImageView =
        context.iconButton(icon, colors.barText, description, onClick).also { addView(it) }

    /** The ⋮ menu: [items] are its labels and actions, read when it opens. */
    fun overflow(items: () -> List<Pair<String, () -> Unit>>) {
        action(R.drawable.ic_more, "אפשרויות נוספות") { anchor ->
            val list = items()
            PopupMenu(context, anchor).apply {
                list.forEachIndexed { i, (label, _) -> menu.add(0, i, i, label) }
                setOnMenuItemClickListener { item ->
                    list[item.itemId].second()
                    true
                }
            }.show()
        }
    }
}

/** A list adapter over [items], drawing each with [bind] (given the view to reuse, if any). */
class ItemsAdapter<T>(private val bind: (item: T, reuse: View?, parent: ViewGroup) -> View) : BaseAdapter() {
    var items: List<T> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): T = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = bind(items[position], convertView, parent)
}

/** A row of a list: an icon on the right, a title and a second line, and a chevron on the left when it leads further. */
class EntryRow(context: Context, private val colors: Colors) : LinearLayout(context) {
    private val icon = ImageView(context)
    private val title: TextView
    private val subtitle: TextView
    private val chevron = ImageView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(52)
        setPadding(context.dp(12), context.dp(6), context.dp(8), context.dp(6))
        background = pressable(colors.panel, colors.pressed)
        icon.layoutParams = lp(context.dp(24), context.dp(24)).apply { marginEnd = context.dp(14) }
        addView(icon)
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = lp(0, WRAP, 1f)
        }
        title = context.label(sizeSp = 17f, color = colors.text, maxLines = 2)
        subtitle = context.label(sizeSp = 13f, color = colors.secondary, maxLines = 2).apply { visibility = GONE }
        texts.addView(title)
        texts.addView(subtitle)
        addView(texts)
        chevron.setImageResource(R.drawable.ic_chevron)
        chevron.setColorFilter(colors.secondary, PorterDuff.Mode.SRC_IN)
        chevron.layoutParams = lp(context.dp(24), context.dp(24))
        addView(chevron)
    }

    fun bind(text: CharSequence, second: CharSequence? = null, iconRes: Int? = null, iconTint: Int = colors.accent, leads: Boolean = false): EntryRow {
        title.text = text
        subtitle.text = second
        subtitle.visibility = if (second.isNullOrEmpty()) GONE else VISIBLE
        if (iconRes != null) {
            icon.setImageResource(iconRes)
            icon.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
            icon.visibility = VISIBLE
        } else {
            icon.visibility = GONE
        }
        chevron.visibility = if (leads) VISIBLE else GONE
        return this
    }
}

/** A section heading over a list or a card, in the accent colour. */
fun Context.sectionTitle(text: String, colors: Colors): TextView = label(text, 14f, colors.accent, bold = true).apply {
    setPadding(dp(16), dp(14), dp(16), dp(6))
}

/** A thin line between rows. */
fun Context.divider(colors: Colors): View = View(this).apply {
    setBackgroundColor(colors.border)
    layoutParams = lp(MATCH, maxOf(1, dp(0.75f)))
}

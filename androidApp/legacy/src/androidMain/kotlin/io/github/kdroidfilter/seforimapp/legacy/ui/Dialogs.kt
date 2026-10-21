package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

/**
 * Dialogs and menus in the main app's look (a panel with rounded corners and a border, rows of the library tree's
 * kind) rather than Android 4.4's Holo ones. Every row is focusable, so a keypad moves through them and picks with OK.
 */
object Dialogs {
    /**
     * A dialog with [title], [content] and, at its foot, [buttons] (label → action; each closes the dialog first). A
     * keypad starts on button [focusButton] when it is given (a question's answer), else on the dialog's first stop.
     * Button [dangerButton], when given, is in the error colour (the main app's "כן, נקה").
     */
    fun show(
        activity: Activity,
        colors: Colors,
        title: String?,
        content: View,
        buttons: List<Pair<String, () -> Unit>> = listOf("סגור" to {}),
        focusButton: Int = -1,
        dangerButton: Int = -1,
    ): Dialog {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = activity.island(colors)
            setPadding(activity.dp(8), activity.dp(12), activity.dp(8), activity.dp(8))
        }
        if (title != null) {
            box.addView(activity.label(title, 17f, colors.text, bold = true).apply { setPadding(activity.dp(10), 0, activity.dp(10), activity.dp(8)) })
        }
        box.addView(ScrollView(activity).apply { addView(content) }, lp(MATCH, 0, 1f).apply { weight = 1f })
        var focus: View? = null
        if (buttons.isNotEmpty()) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(0, activity.dp(6), 0, 0)
            }
            buttons.forEachIndexed { i, (label, action) ->
                row.addView(activity.label(label, 15f, if (i == dangerButton) colors.error else colors.accent, bold = true).apply {
                    if (i == focusButton) focus = this
                    gravity = Gravity.CENTER
                    minWidth = activity.dp(64)
                    minHeight = activity.dp(40)
                    setPadding(activity.dp(12), 0, activity.dp(12), 0)
                    background = states(colors, 0, activity.dp(8).toFloat())
                    keyable(this) {
                        dialog.dismiss()
                        action()
                    }
                })
            }
            box.addView(row, lp(MATCH, WRAP))
        }
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
        focus?.let { if (!it.isInTouchMode) it.requestFocus() }
        return dialog
    }

    /** A list to pick from: [entries] as rows, [checked] marked; picking one closes the dialog and runs it. */
    fun choices(activity: Activity, colors: Colors, title: String?, entries: List<MenuEntry>, checked: Int = -1): Dialog {
        lateinit var dialog: Dialog
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        entries.forEachIndexed { i, entry ->
            if (entry.action == null) {
                list.addView(header(activity, colors, entry.label, first = i == 0))
                return@forEachIndexed
            }
            list.addView(TreeRow(activity, colors).bind(entry.label, iconRes = entry.icon, marked = i == checked).apply {
                keyable(this) {
                    dialog.dismiss()
                    entry.action.invoke()
                }
            })
        }
        dialog = show(activity, colors, title, list, buttons = listOf("סגור" to {}))
        if (checked >= 0) list.getChildAt(checked)?.requestFocus()
        return dialog
    }

    /** A message, with Close. */
    fun message(activity: Activity, colors: Colors, title: String, text: CharSequence): Dialog =
        show(activity, colors, title, activity.label(text, 15f, colors.text).apply {
            setLineSpacing(0f, 1.2f)
            setPadding(activity.dp(10), 0, activity.dp(10), activity.dp(8))
            setTextIsSelectable(true)
        })

    /**
     * A menu at [anchor]: [entries] as rows in a panel, under the anchor when they fit there, else on the side with
     * more room (over the reader's bottom toolbar), scrolling rather than running past the screen's edge; the first row
     * selected for a keypad. A list builds only the rows in view: a catalog menu of the Home runs to a hundred.
     */
    fun popup(anchor: View, colors: Colors, entries: List<MenuEntry>) {
        val context = anchor.context
        val menu = MenuAdapter(context, colors, entries)
        val list = ListView(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = context.island(colors)
            setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
            divider = null
            selector = states(colors, 0, context.dp(6).toFloat())
            cacheColorHint = 0
            adapter = menu
        }
        val width = minOf(context.dp(280), (context.resources.displayMetrics.widthPixels * 0.85f).toInt())
        // The room under the anchor and over it, in the part of the screen the window shows.
        val frame = Rect()
        anchor.getWindowVisibleDisplayFrame(frame)
        val at = IntArray(2)
        anchor.getLocationOnScreen(at)
        val below = frame.bottom - at[1] - anchor.height
        val above = at[1] - frame.top
        // The rows' own height, not WRAP_CONTENT: Android 4.4 tests the room under the anchor with that -2, always finds
        // it enough, and clips a menu under the bottom toolbar to a few pixels. So a menu that does not fit under its
        // anchor is put over it here (the height is at most the larger room, so it then fits there).
        val height = menu.height(list, width, maxOf(below, above, context.dp(48)))
        val over = height > below
        val window = PopupWindow(list, width, height, true)
        list.setOnItemClickListener { _, _, position, _ ->
            val action = entries[position].action ?: return@setOnItemClickListener
            window.dismiss()
            action()
        }
        window.setBackgroundDrawable(ColorDrawable(0))
        window.isOutsideTouchable = true
        // RTL: the menu hangs from the anchor's right edge; ⋮ sits at the bar's left, so it opens towards the middle.
        window.showAsDropDown(anchor, 0, if (over) -(anchor.height + height) else 0)
        // Without touch, setAdapter has selected the first row a keypad stops on, a section's title above it still in
        // view (setSelection would scroll that title away): the keypad starts there, in the menu's window.
        if (!anchor.isInTouchMode) list.requestFocus()
    }

    /** A [popup]'s rows: an entry, or a section's title (not a stop for the keypad), each kind reused as it scrolls. */
    private class MenuAdapter(private val context: Context, private val colors: Colors, private val entries: List<MenuEntry>) : BaseAdapter() {
        override fun getCount(): Int = entries.size

        override fun getItem(position: Int): MenuEntry = entries[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int = if (entries[position].action == null) 0 else 1

        override fun areAllItemsEnabled(): Boolean = false

        override fun isEnabled(position: Int): Boolean = entries[position].action != null

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val entry = entries[position]
            if (entry.action == null) return header(context, colors, entry.label, first = position == 0, reuse = convertView)
            return (convertView as? TreeRow ?: TreeRow(context, colors)).bind(entry.label, iconRes = entry.icon, iconTint = entry.tint ?: colors.text)
        }

        /** The height of the rows in [list] when it is [width] wide, its padding included: measured up to [max], no further. */
        fun height(list: ListView, width: Int, max: Int): Int {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width - list.paddingLeft - list.paddingRight, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            val reuse = arrayOfNulls<View>(viewTypeCount)
            var total = list.paddingTop + list.paddingBottom
            for (i in 0 until count) {
                val type = getItemViewType(i)
                val row = getView(i, reuse[type], list)
                reuse[type] = row
                row.measure(widthSpec, heightSpec)
                total += row.measuredHeight
                if (total >= max) return max
            }
            return total
        }
    }

    /** A section's title in a menu (the catalog's תורה, נביאים…): grey, with a line above all but the first. */
    private fun header(context: Context, colors: Colors, text: String, first: Boolean, reuse: View? = null): View {
        val box = reuse as? LinearLayout ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(context.divider(colors).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(0, context.dp(4), 0, context.dp(2)) })
            addView(context.label("", 14f, colors.disabled, bold = true, maxLines = 1).apply { setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6)) })
        }
        box.getChildAt(0).visibility = if (first) View.GONE else View.VISIBLE
        (box.getChildAt(1) as TextView).text = text
        return box
    }

    /** A line of text inside a dialog. */
    fun text(activity: Activity, colors: Colors, text: CharSequence): TextView =
        activity.label(text, 15f, colors.text).apply { setPadding(activity.dp(10), activity.dp(4), activity.dp(10), activity.dp(4)) }
}

package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

/**
 * Dialogs and menus in the main app's look (a panel with rounded corners and a border, rows of the library tree's
 * kind) rather than Android 4.4's Holo ones. Every row is focusable, so a keypad moves through them and picks with OK.
 */
object Dialogs {
    /** A dialog with [title], [content] and, at its foot, [buttons] (label → action; each closes the dialog first). */
    fun show(activity: Activity, colors: Colors, title: String?, content: View, buttons: List<Pair<String, () -> Unit>> = listOf("סגור" to {})): Dialog {
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
        if (buttons.isNotEmpty()) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(0, activity.dp(6), 0, 0)
            }
            buttons.forEach { (label, action) ->
                row.addView(activity.label(label, 15f, colors.accent, bold = true).apply {
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

    /** A menu under [anchor]: [entries] as rows in a panel, the first one focused for a keypad. */
    fun popup(anchor: View, colors: Colors, entries: List<MenuEntry>) {
        val context = anchor.context
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = context.island(colors)
            setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
        }
        val width = minOf(context.dp(280), (context.resources.displayMetrics.widthPixels * 0.85f).toInt())
        val window = PopupWindow(ScrollView(context).apply { addView(list) }, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        var first: View? = null
        entries.forEachIndexed { i, entry ->
            if (entry.action == null) {
                list.addView(header(context, colors, entry.label, first = i == 0), lp(MATCH, WRAP))
                return@forEachIndexed
            }
            val row = TreeRow(context, colors).bind(entry.label, iconRes = entry.icon, iconTint = colors.text).apply {
                keyable(this) {
                    window.dismiss()
                    entry.action.invoke()
                }
            }
            if (first == null) first = row
            list.addView(row, lp(MATCH, WRAP))
        }
        window.setBackgroundDrawable(ColorDrawable(0))
        window.isOutsideTouchable = true
        // RTL: the menu hangs from the anchor's right edge; ⋮ sits at the bar's left, so it opens towards the middle.
        window.showAsDropDown(anchor, 0, 0)
        if (!anchor.isInTouchMode) first?.requestFocus()
    }

    /** A section's title in a menu (the catalog's תורה, נביאים…): grey, with a line above all but the first. */
    private fun header(context: android.content.Context, colors: Colors, text: String, first: Boolean): View {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (!first) box.addView(context.divider(colors).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(0, context.dp(4), 0, context.dp(2)) })
        box.addView(context.label(text, 14f, colors.disabled, bold = true, maxLines = 1).apply { setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6)) })
        return box
    }

    /** A line of text inside a dialog. */
    fun text(activity: Activity, colors: Colors, text: CharSequence): TextView =
        activity.label(text, 15f, colors.text).apply { setPadding(activity.dp(10), activity.dp(4), activity.dp(10), activity.dp(4)) }
}

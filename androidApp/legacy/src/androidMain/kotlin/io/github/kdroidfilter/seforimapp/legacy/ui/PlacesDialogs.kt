package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.Activity
import android.app.Dialog
import android.widget.LinearLayout
import io.github.kdroidfilter.seforimapp.android.ui.shell.ShellTexts
import io.github.kdroidfilter.seforimapp.legacy.PardesApp
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Place

/**
 * The books opened lately and the favourites (the main app's היסטוריה and מועדפים pages) as lists to pick from: a row
 * opens its book at its place, its × (מחק) removes it at once, as the main app's; a long press (a long OK on a keypad)
 * offers to remove it too. Clearing the history asks first, in the main app's words. [onChanged] runs after a removal
 * or a clear: the screen under the dialog (the Home's cards) stays resumed and would not see it otherwise.
 */
object PlacesDialogs {
    fun recents(activity: Activity, colors: Colors, onChanged: () -> Unit = {}) {
        val prefs = PardesApp.of(activity).prefs
        val reopen = { recents(activity, colors, onChanged) }
        val clear = ShellTexts.CLEAR_HISTORY to {
            confirmClear(activity, colors, onCancel = reopen) {
                prefs.clearRecents()
                onChanged()
                reopen()
            }
        }
        show(activity, colors, "היסטוריה", prefs.recents, R.drawable.ic_history, ShellTexts.HISTORY_EMPTY, clear, reopen) { place ->
            prefs.forgetRecent(place.bookId)
            onChanged()
        }
    }

    fun favorites(activity: Activity, colors: Colors, onChanged: () -> Unit = {}) {
        val prefs = PardesApp.of(activity).prefs
        show(activity, colors, "מועדפים", prefs.bookmarks, R.drawable.ic_star, ShellTexts.FAVORITES_EMPTY, reopen = { favorites(activity, colors, onChanged) }) { place ->
            prefs.removeBookmark(place)
            onChanged()
        }
    }

    /**
     * The main app's question before the history is cleared (ניקוי היסטוריה): ביטול first, where a keypad starts, and
     * the answer that clears in the error colour.
     */
    fun confirmClear(activity: Activity, colors: Colors, onCancel: () -> Unit = {}, onClear: () -> Unit) {
        Dialogs.show(
            activity,
            colors,
            ShellTexts.CLEAR_HISTORY_TITLE,
            Dialogs.text(activity, colors, ShellTexts.CLEAR_HISTORY_MESSAGE),
            listOf("ביטול" to onCancel, ShellTexts.CLEAR_HISTORY_CONFIRM to onClear),
            focusButton = 0,
            dangerButton = 1,
        ).setOnCancelListener { onCancel() }
    }

    private fun show(
        activity: Activity,
        colors: Colors,
        title: String,
        places: List<Place>,
        icon: Int,
        empty: String,
        clear: Pair<String, () -> Unit>? = null,
        reopen: () -> Unit,
        remove: (Place) -> Unit,
    ) {
        lateinit var dialog: Dialog
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val emptyText = Dialogs.text(activity, colors, empty)
        if (places.isEmpty()) list.addView(emptyText)
        places.forEach { place ->
            val box = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            val row = TreeRow(activity, colors, titleLines = 1).bind(place.title, iconRes = icon, iconTint = colors.accent, secondLine = place.note.ifEmpty { "שורה ${place.lineIndex + 1}" }).apply {
                keyable(this) {
                    dialog.dismiss()
                    BookActivity.start(activity, place.bookId, place.lineIndex, place.title)
                }
                setOnLongClickListener {
                    Dialogs.show(activity, colors, place.title, Dialogs.text(activity, colors, "להסיר מהרשימה?"), listOf("הסר" to {
                        remove(place)
                        dialog.dismiss()
                        reopen()
                    }, "ביטול" to {}))
                    true
                }
            }
            box.addView(row, lp(0, WRAP, 1f))
            // The row's ×, a keypad's second stop on it (◄ from the row): the row goes, the list stays where it was.
            box.addView(activity.iconButton(R.drawable.ic_close, colors.secondary, ShellTexts.DELETE, colors) {
                remove(place)
                val at = list.indexOfChild(box)
                list.removeView(box)
                if (list.childCount == 0) {
                    list.addView(emptyText)
                } else if (!list.isInTouchMode) {
                    (list.getChildAt(minOf(at, list.childCount - 1)) as? LinearLayout)?.getChildAt(0)?.requestFocus()
                }
            })
            list.addView(box, lp(MATCH, WRAP))
        }
        val buttons = ArrayList<Pair<String, () -> Unit>>()
        buttons += "סגור" to {}
        if (clear != null && places.isNotEmpty()) buttons += clear
        dialog = Dialogs.show(activity, colors, title, list, buttons)
        if (places.isNotEmpty() && !list.isInTouchMode) (list.getChildAt(0) as? LinearLayout)?.getChildAt(0)?.requestFocus()
    }
}

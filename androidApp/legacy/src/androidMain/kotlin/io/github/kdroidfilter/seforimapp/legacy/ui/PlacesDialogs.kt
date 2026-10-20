package io.github.kdroidfilter.seforimapp.legacy.ui

import android.app.Activity
import android.app.Dialog
import android.widget.LinearLayout
import io.github.kdroidfilter.seforimapp.legacy.PardesApp
import io.github.kdroidfilter.seforimapp.legacy.R
import io.github.kdroidfilter.seforimapp.legacy.data.Place

/**
 * The books opened lately and the favourites (the main app's היסטוריה and מועדפים pages) as lists to pick from: a row
 * opens its book at its place; a long press (a long OK on a keypad) offers to remove it. [onChanged] runs after a
 * removal or a clear: the screen under the dialog (the Home's cards) stays resumed and would not see it otherwise.
 */
object PlacesDialogs {
    fun recents(activity: Activity, colors: Colors, onChanged: () -> Unit = {}) {
        val prefs = PardesApp.of(activity).prefs
        val clear = "נקה היסטוריה" to {
            prefs.clearRecents()
            onChanged()
        }
        show(activity, colors, "היסטוריה", prefs.recents, R.drawable.ic_history, "אין עדיין ספרים שנפתחו", clear, reopen = { recents(activity, colors, onChanged) }) { place ->
            prefs.forgetRecent(place.bookId)
            onChanged()
        }
    }

    fun favorites(activity: Activity, colors: Colors, onChanged: () -> Unit = {}) {
        val prefs = PardesApp.of(activity).prefs
        show(activity, colors, "מועדפים", prefs.bookmarks, R.drawable.ic_star, "אין עדיין מועדפים. מוסיפים אותם בספר, מתפריט \"עוד\".", reopen = { favorites(activity, colors, onChanged) }) { place ->
            prefs.removeBookmark(place)
            onChanged()
        }
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
        if (places.isEmpty()) list.addView(Dialogs.text(activity, colors, empty))
        places.forEach { place ->
            list.addView(TreeRow(activity, colors).bind(place.title, iconRes = icon, iconTint = colors.accent, secondLine = place.note.ifEmpty { "שורה ${place.lineIndex + 1}" }).apply {
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
            })
        }
        val buttons = ArrayList<Pair<String, () -> Unit>>()
        buttons += "סגור" to {}
        if (clear != null && places.isNotEmpty()) buttons += clear
        dialog = Dialogs.show(activity, colors, title, list, buttons)
        if (places.isNotEmpty() && !list.isInTouchMode) list.getChildAt(0)?.requestFocus()
    }
}

package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Library
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_tree_tooltip

// Pardes (request 97 of docs/DESKTOP_STAGE3_PLAN.md: "שיכולו לפתוח את עץ הספרים בחיפוש, ברירת מחדל סגור - יפתח לפי
// בחירה. בחירה שם תהיה כמו בטאבים - תסמן בשורת החיפוש ויוכלו לחפש שוב"): a search's tree of the books it found,
// Zayit's own pane (SearchCategoryTreePanel), which the category tabs had taken the place of. It is closed until the
// reader opens it, by a button beside the search bar, and what is checked in it is in the bar too, as a tab picked is.

/** Whether a search's results show their tree of books in a pane: one setting, every tab, off until asked for. */
object SearchTreeSettings : SettingsGroup("search_tree") {
    val shown by boolean(false)
}

/**
 * What the reader checked in a search's tree of books, in the order checked: the categories and the books its bar
 * shows, to search again in. The tabs picked above the results are its categories, and no book ([only]).
 */
internal class SearchTreePicks {
    private val categories = LinkedHashSet<Long>()
    private val books = LinkedHashSet<Long>()

    /** The category [id] checked, or no longer: where the bar searches then ([scope]). */
    fun category(
        id: Long,
        checked: Boolean,
    ): SearchScope? {
        if (checked) categories += id else categories -= id
        return scope()
    }

    /** The book [id] checked, or no longer: where the bar searches then ([scope]). */
    fun book(
        id: Long,
        checked: Boolean,
    ): SearchScope? {
        if (checked) books += id else books -= id
        return scope()
    }

    /** These categories alone are checked: the tabs picked, or none at a new search. */
    fun only(tabs: Collection<Long> = emptyList()) {
        categories.clear()
        categories += tabs
        books.clear()
    }

    /** Where the bar searches with what is checked: a category, a book, several together; null with nothing checked. */
    fun scope(): SearchScope? =
        when {
            categories.isEmpty() && books.isEmpty() -> null
            books.isEmpty() && categories.size == 1 -> SearchScope.Category(categories.single())
            categories.isEmpty() && books.size == 1 -> SearchScope.Book(books.single())
            else -> SearchScope.Multi(bookIds = books.toList(), categoryIds = categories.toList())
        }
}

private const val HOVER_ALPHA = 0.14f
private const val SHOWN_ALPHA = 0.22f

/** The button of a search's tree of books, beside its bar: it shows the pane, or hides it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchTreeButton(modifier: Modifier = Modifier) {
    val appSettings = LocalAppGraph.current.appSettings
    val shown by appSettings.flow(SearchTreeSettings.shown).collectAsState()
    val label = stringResource(Res.string.search_tree_tooltip)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val background =
        when {
            shown -> accent.copy(alpha = SHOWN_ALPHA)
            hovered -> accent.copy(alpha = HOVER_ALPHA)
            else -> Color.Transparent
        }
    Tooltip(tooltip = { Text(label) }, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    // The bar keeps the keys: the button is the pointer's
                    .focusProperties { canFocus = false }
                    .toggleable(
                        value = shown,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Switch,
                        onValueChange = { appSettings[SearchTreeSettings.shown] = it },
                    ).pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(imageVector = Library, contentDescription = label, modifier = Modifier.size(18.dp))
        }
    }
}

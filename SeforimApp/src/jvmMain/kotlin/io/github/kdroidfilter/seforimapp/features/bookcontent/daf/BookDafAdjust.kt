package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.settings.daf.BookDafStyleFields
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.book_daf_adjust
import seforimapp.seforimapp.generated.resources.book_daf_adjust_tooltip
import seforimapp.seforimapp.generated.resources.book_daf_own_style

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): a book's daf's style changed where it is read, for every book or for
// the one shown: "בהתאמה אישית לכלל הספרים" (2026-11-23). The fields are the settings' own (BookDafCard). A button at
// the pages' corner, not one more in the end bar: the bar of a Chumash's pages is full at a window 900 pixels tall.

private val AdjustWidth = 400.dp

/**
 * A button at the corner of [book]'s pages that shows the fields of their style over them, or hides them: every book's
 * style, or the book's own once it is given one. Each change sets the pages again.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookDafAdjust(
    book: Book,
    modifier: Modifier = Modifier,
    beside: @Composable () -> Unit = {},
) {
    var open by remember(book.id) { mutableStateOf(false) }
    Column(modifier.padding(8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The pages' other button: the list of their commentators (request 92 of stage 3)
            beside()
            Tooltip({ Text(stringResource(Res.string.book_daf_adjust_tooltip)) }) {
                IconActionButton(
                    key = AllIconsKeys.General.Settings,
                    onClick = { open = !open },
                    contentDescription = stringResource(Res.string.book_daf_adjust),
                )
            }
        }
        if (open) AdjustFields(book)
    }
}

/** The style [book]'s pages are set in, to be changed: a checkbox that gives the book its own, then the style's fields. */
@Composable
private fun AdjustFields(book: Book) {
    val appSettings = LocalAppGraph.current.appSettings
    val all by appSettings.flow(BookDafSettings.style).collectAsState()
    val books by appSettings.flow(BookDafSettings.books).collectAsState()
    val own = books[book.id]
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .width(AdjustWidth)
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                // Taller than a short view since the fields of requests 98 and 99: they scroll in it
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CheckboxRow(
            text = stringResource(Res.string.book_daf_own_style, book.title),
            checked = own != null,
            onCheckedChange = { appSettings[BookDafSettings.books] = if (it) books + (book.id to all) else books - book.id },
        )
        BookDafStyleFields(
            style = own ?: all,
            onStyleChange = { style ->
                if (own !=
                    null
                ) {
                    appSettings[BookDafSettings.books] = books + (book.id to style)
                } else {
                    appSettings[BookDafSettings.style] = style
                }
            },
        )
    }
}

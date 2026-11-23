package io.github.kdroidfilter.seforimapp.features.settings.daf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.BookDafSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.BookDafStyle
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafStyle
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.book_daf_style_aspect
import seforimapp.seforimapp.generated.resources.book_daf_style_aspect_book
import seforimapp.seforimapp.generated.resources.book_daf_style_aspect_tall
import seforimapp.seforimapp.generated.resources.book_daf_style_aspect_wide
import seforimapp.seforimapp.generated.resources.book_daf_style_columns
import seforimapp.seforimapp.generated.resources.book_daf_style_commentary_size
import seforimapp.seforimapp.generated.resources.book_daf_style_layout
import seforimapp.seforimapp.generated.resources.book_daf_style_layout_around
import seforimapp.seforimapp.generated.resources.book_daf_style_layout_bands
import seforimapp.seforimapp.generated.resources.book_daf_style_reset
import seforimapp.seforimapp.generated.resources.book_daf_style_run_on
import seforimapp.seforimapp.generated.resources.book_daf_style_run_on_always
import seforimapp.seforimapp.generated.resources.book_daf_style_run_on_never
import seforimapp.seforimapp.generated.resources.book_daf_style_run_on_tanakh
import seforimapp.seforimapp.generated.resources.book_daf_style_side
import seforimapp.seforimapp.generated.resources.book_daf_style_side_narrow
import seforimapp.seforimapp.generated.resources.book_daf_style_side_none
import seforimapp.seforimapp.generated.resources.book_daf_style_side_regular
import seforimapp.seforimapp.generated.resources.book_daf_style_side_wide
import seforimapp.seforimapp.generated.resources.book_daf_style_square
import seforimapp.seforimapp.generated.resources.book_daf_style_text_size
import seforimapp.seforimapp.generated.resources.settings_book_daf
import seforimapp.seforimapp.generated.resources.settings_book_daf_description
import kotlin.math.abs

// Pardes (request 21 of docs/DESKTOP_STAGE2.md): the settings of a book's own pages (BookDafView), an experiment the
// reader turns on here, with the pages' style: the numbers of the user's rules (docs/BOOK_DAF_RULES.md), each to be
// changed.

/** The letters' sizes offered, in the rules' unit; the rules' own among them. */
private val TEXT_SIZES = listOf(14f, 16f, 18f, 20f, 22f, 24f, 26f, 28f, 30f)
private val COMMENTARY_SIZES = listOf(9f, 10f, 11f, 12f, 12.5f, 13f, 14f, 15f, 16f, 18f)

/** The targum's share of the width (none, narrow, the rules', wide), and the page's height to its width. */
private val SIDE_SHARES = listOf(0f, 0.3f, BookDafStyle.DEFAULT_SIDE_SHARE, 0.46f)
private val ASPECTS = listOf(1.2f, BookDafStyle.DEFAULT_ASPECT, 1.6f)

/** Whether the text's lines run on: in the Tanakh alone, in every book, in none. */
private val RUN_ON = listOf(null, true, false)

/**
 * Settings › צורת הדף: a book's own pages offered for every book with commentaries, off until turned on here, and the
 * style every book's pages are set in.
 */
@Composable
internal fun BookDafCard() {
    val appSettings = LocalAppGraph.current.appSettings
    val enabled by appSettings.flow(BookDafSettings.enabled).collectAsState()
    val style by appSettings.flow(BookDafSettings.style).collectAsState()
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CheckboxRow(
            text = stringResource(Res.string.settings_book_daf),
            checked = enabled,
            onCheckedChange = { appSettings[BookDafSettings.enabled] = it },
        )
        Text(
            text = stringResource(Res.string.settings_book_daf_description),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        if (enabled) {
            BookDafStyleFields(style, onStyleChange = { appSettings[BookDafSettings.style] = it })
        }
    }
}

/** The fields of a book's daf's [style], each change given whole to [onStyleChange]; the last of them resets it. */
@Composable
internal fun BookDafStyleFields(
    style: BookDafStyle,
    onStyleChange: (BookDafStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bands = style.layout == DafStyle.Bands
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Choice(
            label = stringResource(Res.string.book_daf_style_layout),
            options =
                listOf(
                    stringResource(Res.string.book_daf_style_layout_bands),
                    stringResource(Res.string.book_daf_style_layout_around),
                ),
            selected = style.layout.ordinal,
            onSelect = { onStyleChange(style.copy(layout = DafStyle.entries[it])) },
        )
        // Around the text the page is the Talmud's, its letters and columns the Vilna Shas's own
        if (bands) BandsFields(style, onStyleChange)
        CheckboxRow(
            text = stringResource(Res.string.book_daf_style_square),
            checked = style.squareCommentary,
            onCheckedChange = { onStyleChange(style.copy(squareCommentary = it)) },
        )
        if (style != BookDafStyle()) {
            OutlinedButton(onClick = { onStyleChange(BookDafStyle()) }) { Text(stringResource(Res.string.book_daf_style_reset)) }
        }
    }
}

/** The fields of a page of bands: its letters, its columns, the targum beside its text, its shape, its text's paragraphs. */
@Composable
private fun BandsFields(
    style: BookDafStyle,
    onStyleChange: (BookDafStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Choice(
            label = stringResource(Res.string.book_daf_style_text_size),
            options = TEXT_SIZES.map(::sizeLabel),
            selected = nearest(TEXT_SIZES, style.textSize),
            onSelect = { onStyleChange(style.copy(textSize = TEXT_SIZES[it])) },
        )
        Choice(
            label = stringResource(Res.string.book_daf_style_commentary_size),
            options = COMMENTARY_SIZES.map(::sizeLabel),
            selected = nearest(COMMENTARY_SIZES, style.commentarySize),
            onSelect = { onStyleChange(style.copy(commentarySize = COMMENTARY_SIZES[it])) },
        )
        Choice(
            label = stringResource(Res.string.book_daf_style_columns),
            options = BookDafStyle.COLUMNS.map { it.toString() },
            selected = style.columns - BookDafStyle.COLUMNS.first,
            onSelect = { onStyleChange(style.copy(columns = BookDafStyle.COLUMNS.first + it)) },
        )
        Choice(
            label = stringResource(Res.string.book_daf_style_side),
            options =
                listOf(
                    stringResource(Res.string.book_daf_style_side_none),
                    stringResource(Res.string.book_daf_style_side_narrow),
                    stringResource(Res.string.book_daf_style_side_regular),
                    stringResource(Res.string.book_daf_style_side_wide),
                ),
            selected = nearest(SIDE_SHARES, style.sideShare),
            onSelect = { onStyleChange(style.copy(sideShare = SIDE_SHARES[it])) },
        )
        Choice(
            label = stringResource(Res.string.book_daf_style_aspect),
            options =
                listOf(
                    stringResource(Res.string.book_daf_style_aspect_wide),
                    stringResource(Res.string.book_daf_style_aspect_book),
                    stringResource(Res.string.book_daf_style_aspect_tall),
                ),
            selected = nearest(ASPECTS, style.aspect),
            onSelect = { onStyleChange(style.copy(aspect = ASPECTS[it])) },
        )
        Choice(
            label = stringResource(Res.string.book_daf_style_run_on),
            options =
                listOf(
                    stringResource(Res.string.book_daf_style_run_on_tanakh),
                    stringResource(Res.string.book_daf_style_run_on_always),
                    stringResource(Res.string.book_daf_style_run_on_never),
                ),
            selected = RUN_ON.indexOf(style.runOn),
            onSelect = { onStyleChange(style.copy(runOn = RUN_ON[it])) },
        )
    }
}

/** A field of the style: its [label], and its [options] in a list that shows the one [selected]. */
@Composable
private fun Choice(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        ListComboBox(
            items = options,
            selectedIndex = selected.coerceIn(options.indices),
            onSelectedItemChange = onSelect,
            modifier = Modifier.width(CHOICE_WIDTH.dp),
        )
    }
}

private const val CHOICE_WIDTH = 220

/** The index of the value of [values] nearest to [value]. */
private fun nearest(
    values: List<Float>,
    value: Float,
): Int = values.indices.minBy { abs(values[it] - value) }

/** A letters' size as shown: whole when it is. */
private fun sizeLabel(size: Float): String = if (size % 1f == 0f) size.toInt().toString() else size.toString()

package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforimapp.core.presentation.components.PopupShape
import io.github.kdroidfilter.seforimapp.features.settings.daf.StyleChoice
import io.github.kdroidfilter.seforimapp.features.settings.daf.sizeLabel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.book_daf_band_as_others
import seforimapp.seforimapp.generated.resources.book_daf_band_beside
import seforimapp.seforimapp.generated.resources.book_daf_band_columns
import seforimapp.seforimapp.generated.resources.book_daf_band_down
import seforimapp.seforimapp.generated.resources.book_daf_band_rashi
import seforimapp.seforimapp.generated.resources.book_daf_band_remove
import seforimapp.seforimapp.generated.resources.book_daf_band_script
import seforimapp.seforimapp.generated.resources.book_daf_band_size
import seforimapp.seforimapp.generated.resources.book_daf_band_square
import seforimapp.seforimapp.generated.resources.book_daf_band_up
import seforimapp.seforimapp.generated.resources.book_daf_head_detach
import seforimapp.seforimapp.generated.resources.book_daf_head_settings
import kotlin.math.roundToInt

// Pardes (requests 91 and 93 of docs/DESKTOP_STAGE3_PLAN.md: "לחיצה על כותרת מפרש תפתח אותו - כמו שיש בתצוגת מפרשים";
// "כמו שבמפרשים כשעכבר על הכותרת מוצגות אפשרות להוצאה החוצה כך יהיה גם בכותרת מפרש בספר ויהיה בנוסף גם הגדרות - של
// גופן וגודל עמודות וכדומה. ויהיה ניתן בגרירה מהכותרת לשנות את מיקום המפרש ביחס לשאר. יהיה ניתן להזיז לליד הטקסט
// הראשי כמו תרגום"): what a commentator's name on a book's page does, laid over the page where the name is printed. A
// click opens the commentator, as its header in the commentaries pane; the pointer over its line shows two buttons, out
// to a pane of its own and its own letters on the pages; a drag of the name moves it among the bands, or beside the text.

/** A commentator dropped this far above the first band's name goes beside the text, not first among the bands (points). */
private const val BESIDE_ABOVE = 16f

/** How much of the page's width the mark of a drop beside the text covers, how strong it is, and a drop line's width. */
private const val SIDE_MARK = 0.38f
private const val DROP_ALPHA = 0.18f
private const val DROP_STROKE = 2f

/** The width of the fields of a commentator's letters. */
private val FieldsWidth = 340.dp

/** The letters a commentator may be given on the pages, in the rules' unit. */
private val BAND_SIZES = listOf(9f, 10f, 11f, 12f, 12.5f, 13f, 14f, 15f, 16f, 18f, 20f, 22f)

/** Where a commentator dropped on a page goes: beside the text, or in a band before another's (after them all: null). */
internal sealed interface HeadDrop {
    data object Beside : HeadDrop

    data class Before(
        val bookId: Long?,
    ) : HeadDrop
}

/**
 * Where a commentator dropped [y] points down a page goes, by the names on it ([heads]): beside the text when above the
 * first band (where the page has a column there, [hasSide]); else before the band whose name is the first with its
 * middle below, after them all at the page's bottom.
 */
internal fun headDrop(
    heads: List<BandHead>,
    y: Float,
    hasSide: Boolean,
): HeadDrop {
    val bands = heads.filter { !it.isBeside }.sortedBy { it.y0 }
    val first = bands.firstOrNull()
    if (hasSide && (first == null || y < first.y0 - BESIDE_ABOVE)) return HeadDrop.Beside
    return HeadDrop.Before(bands.firstOrNull { (it.y0 + it.y1) / 2 > y }?.bookId)
}

/** These commentators with [id] dropped at [drop] on a page whose names are [heads]. */
internal fun BookDafCommentators.dropped(
    id: Long,
    drop: HeadDrop,
    heads: List<BandHead>,
): BookDafCommentators {
    if (drop is HeadDrop.Beside) return besideText(id, on = true)
    val before = (drop as HeadDrop.Before).bookId
    if (before == id) return this
    if (before != null) return moved(id, before)
    // After the page's last band: before the commentator that follows it among those shown
    val rest = shown.orEmpty() - id
    val last = heads.filter { !it.isBeside && it.bookId != id }.maxByOrNull { it.y0 }
    return moved(id, last?.let { rest.getOrNull(rest.indexOf(it.bookId) + 1) })
}

/**
 * What the commentators' names do on a book's pages: the book's commentators as the reader chose them ([own]) and what
 * a change of them does ([onChange]: the pages are set again); whether a page has a column beside its text ([hasSide]);
 * a click on a name ([onOpen], with its page); a commentator taken out to a pane of its own ([onDetach]); and the name
 * whose letters are being set, by its book and its page ([settings], [onSettings]): kept here, over the pages, since
 * every change sets them again.
 */
internal class BookDafHeadsState(
    val own: BookDafCommentators,
    val hasSide: Boolean,
    val settings: Pair<Long, String>?,
    val onSettings: (Pair<Long, String>?) -> Unit,
    val onOpen: (bookId: Long, page: LaidPage) -> Unit,
    val onDetach: (bookId: Long) -> Unit,
    val onChange: (BookDafCommentators) -> Unit,
)

/** The names of [page]'s commentators, each over where it is printed ([perPoint] pixels a point), as [state] has them act. */
@Composable
internal fun BookDafHeads(
    page: LaidPage,
    perPoint: Float,
    state: BookDafHeadsState,
    modifier: Modifier = Modifier,
) {
    if (page.heads.isEmpty()) return
    val direction = LocalLayoutDirection.current
    val accent = JewelTheme.globalColors.outlines.focused
    // The commentator dragged by its name, and how far down the page the pointer is (points)
    var drag by remember(page) { mutableStateOf<Pair<Long, Float>?>(null) }
    // The page's own coordinates, left to right, whatever the view's direction
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier.drawWithContent {
                drawContent()
                drag?.let { (_, y) -> drawDrop(page, headDrop(page.heads, y, state.hasSide), perPoint, accent) }
            },
        ) {
            for (head in page.heads) {
                key(head.bookId) {
                    HeadStrip(
                        head = head,
                        page = page,
                        perPoint = perPoint,
                        direction = direction,
                        state = state,
                        onDrag = { y -> drag = y?.let { head.bookId to it } },
                        onDrop = {
                            drag?.let { (id, y) ->
                                state.onChange(state.own.dropped(id, headDrop(page.heads, y, state.hasSide), page.heads))
                            }
                            drag = null
                        },
                    )
                }
            }
        }
    }
}

// Where a commentator dragged would go: a line over the band it would stand before, or the text's side marked
private fun DrawScope.drawDrop(
    page: LaidPage,
    drop: HeadDrop,
    perPoint: Float,
    color: Color,
) {
    val bands = page.heads.filter { !it.isBeside }.sortedBy { it.y0 }
    when (drop) {
        HeadDrop.Beside -> {
            val bottom = (bands.firstOrNull()?.y0 ?: page.height) * perPoint
            drawRect(color.copy(alpha = DROP_ALPHA), Offset.Zero, Size(page.width * SIDE_MARK * perPoint, bottom))
        }
        is HeadDrop.Before -> {
            val y = (bands.firstOrNull { it.bookId == drop.bookId }?.y0 ?: page.height) * perPoint
            drawLine(color, Offset(0f, y), Offset(page.width * perPoint, y), strokeWidth = DROP_STROKE * density)
        }
    }
}

/**
 * A commentator's name's line on a page: the name itself opens the commentator on a click and moves it on a drag
 * ([onDrag]: how far down the page the pointer is, in points; [onDrop]); the pointer over the line shows its buttons, at
 * the line's end as the view reads ([direction]).
 */
@Composable
private fun HeadStrip(
    head: BandHead,
    page: LaidPage,
    perPoint: Float,
    direction: LayoutDirection,
    state: BookDafHeadsState,
    onDrag: (Float?) -> Unit,
    onDrop: () -> Unit,
) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val height = with(density) { ((head.y1 - head.y0) * perPoint).toDp() }
    val isSetting = state.settings == head.bookId to page.amud.amud
    Box(
        Modifier
            .offset { IntOffset((head.x0 * perPoint).roundToInt(), (head.y0 * perPoint).roundToInt()) }
            .size(with(density) { ((head.x1 - head.x0) * perPoint).toDp() }, height)
            .hoverable(interaction),
    ) {
        Box(
            Modifier
                .offset { IntOffset(((head.left - head.x0) * perPoint).roundToInt(), 0) }
                .size(with(density) { ((head.right - head.left) * perPoint).toDp() }, height)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(head, perPoint) {
                    detectDragGestures(
                        onDragStart = { at -> currentOnDrag(head.y0 + at.y / perPoint) },
                        onDrag = { change, _ -> currentOnDrag(head.y0 + change.position.y / perPoint) },
                        onDragEnd = { currentOnDrop() },
                        onDragCancel = { currentOnDrag(null) },
                    )
                }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    state.onOpen(head.bookId, page)
                },
        )
        if (hovered || isSetting) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                HeadButtons(head, page, state, isSetting, Modifier.align(Alignment.CenterStart))
            }
        }
    }
}

/** The buttons of a commentator's name: out to a pane of its own, and its own letters on the pages ([isSetting]: shown). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeadButtons(
    head: BandHead,
    page: LaidPage,
    state: BookDafHeadsState,
    isSetting: Boolean,
    modifier: Modifier = Modifier,
) {
    val detach = stringResource(Res.string.book_daf_head_detach)
    val settings = stringResource(Res.string.book_daf_head_settings)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Tooltip({ Text(detach) }) {
            IconActionButton(
                key = AllIconsKeys.Actions.MoveToWindow,
                onClick = { state.onDetach(head.bookId) },
                contentDescription = detach,
            )
        }
        Box {
            Tooltip({ Text(settings) }) {
                IconActionButton(
                    key = AllIconsKeys.General.Settings,
                    onClick = { state.onSettings((head.bookId to page.amud.amud).takeUnless { isSetting }) },
                    contentDescription = settings,
                )
            }
            if (isSetting) {
                // From the buttons toward the page's middle: they stand at its edge
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, with(LocalDensity.current) { 26.dp.roundToPx() }),
                    onDismissRequest = { state.onSettings(null) },
                    properties = PopupProperties(focusable = true),
                ) {
                    BandFields(head, state)
                }
            }
        }
    }
}

/**
 * A commentator's own letters on the pages: their size, its columns, its script (each as the others' until set apart),
 * whether it stands beside the text in the targum's place, and its place among the bands, or off the pages.
 */
@Composable
private fun BandFields(
    head: BandHead,
    state: BookDafHeadsState,
) {
    val own = state.own
    val id = head.bookId
    val style = own.bands[id] ?: BandStyle()
    val asOthers = stringResource(Res.string.book_daf_band_as_others)
    Column(
        Modifier
            .width(FieldsWidth)
            .background(JewelTheme.globalColors.panelBackground, PopupShape)
            .border(1.dp, JewelTheme.globalColors.borders.normal, PopupShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StyleChoice(
            label = stringResource(Res.string.book_daf_band_size),
            options = listOf(asOthers) + BAND_SIZES.map(::sizeLabel),
            selected = style.size?.let { BAND_SIZES.indexOf(it) + 1 } ?: 0,
            onSelect = { state.onChange(own.styled(id, style.copy(size = BAND_SIZES.getOrNull(it - 1)))) },
        )
        StyleChoice(
            label = stringResource(Res.string.book_daf_band_columns),
            options = listOf(asOthers) + BookDafStyle.BAND_COLUMNS.map { it.toString() },
            selected = style.columns ?: 0,
            onSelect = { state.onChange(own.styled(id, style.copy(columns = it.takeIf { n -> n > 0 }))) },
        )
        StyleChoice(
            label = stringResource(Res.string.book_daf_band_script),
            options = listOf(asOthers, stringResource(Res.string.book_daf_band_rashi), stringResource(Res.string.book_daf_band_square)),
            selected = style.square?.let { if (it) 2 else 1 } ?: 0,
            onSelect = { state.onChange(own.styled(id, style.copy(square = listOf(null, false, true)[it]))) },
        )
        if (state.hasSide) {
            CheckboxRow(
                text = stringResource(Res.string.book_daf_band_beside),
                checked = own.beside == id,
                onCheckedChange = { state.onChange(own.besideText(id, it)) },
            )
        }
        BandPlace(id, state)
    }
}

/** A commentator's place among the bands: up or down among them, or off the pages. */
@Composable
private fun BandPlace(
    id: Long,
    state: BookDafHeadsState,
) {
    val own = state.own
    // The bands in their order: the commentator beside the text is none of them
    val order = own.shown.orEmpty() - listOfNotNull(own.beside?.takeIf { it != id })
    val at = if (own.beside == id) -1 else order.indexOf(id)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { state.onChange(own.moved(id, order[at - 1])) }, enabled = at > 0) {
            Text(stringResource(Res.string.book_daf_band_up))
        }
        OutlinedButton(onClick = { state.onChange(own.moved(id, order.getOrNull(at + 2))) }, enabled = at in 0 until order.lastIndex) {
            Text(stringResource(Res.string.book_daf_band_down))
        }
        OutlinedButton(onClick = { state.onChange(own.checked(listOf(id), checked = false)) }) {
            Text(stringResource(Res.string.book_daf_band_remove))
        }
    }
}

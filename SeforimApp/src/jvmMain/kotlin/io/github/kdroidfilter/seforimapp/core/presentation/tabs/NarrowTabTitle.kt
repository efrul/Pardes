package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabItem
import org.jetbrains.jewel.foundation.theme.LocalTextStyle

// Pardes (request 83 of docs/DESKTOP_STAGE3_PLAN.md, a short title for a narrow tab): a book tab's title is the book's
// name then its place, and a tab too narrow for it cut it before the place, so that the tabs of one book read alike.
// Such a tab shows the book's acronym and the place instead.

/** The room, in pixels, the tab drawn here leaves its content ([TabContentBox]); no limit outside a tab. */
internal val LocalTabContentRoom = compositionLocalOf { Int.MAX_VALUE }

/** A tab's box for its content, which tells the content the room it has ([LocalTabContentRoom]). */
@Composable
internal fun TabContentBox(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment) {
        CompositionLocalProvider(LocalTabContentRoom provides constraints.maxWidth, content = content)
    }
}

// Narrower than this, a tab that is not the selected one has no room for its title's place beside its close button.
private val TitleOverCloseWidth = 140.dp

/**
 * Whether a tab [width] wide that is not the selected one shows its close button: always from [TitleOverCloseWidth],
 * and under the pointer ([hovered]) when narrower, as Chrome's narrow tabs keep theirs for the selected one.
 */
internal fun closeShown(
    width: Dp,
    hovered: Boolean,
): Boolean = hovered || width >= TitleOverCloseWidth

// A tab's icon and the gap after it, before its title (SingleLineTabContent).
private val TitleIconRoom = 22.dp

/** The title this tab shows in the strip: its own while it fits the tab, else a book's short form ([narrowTabTitle]). */
@Composable
internal fun TabItem.stripTitle(): String {
    val short = remember(title, shortTitle) { narrowTabTitle(title, shortTitle) } ?: return title
    val room = LocalTabContentRoom.current
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current
    val density = LocalDensity.current
    val needed =
        remember(title, style, density) {
            measurer.measure(title, style, softWrap = false, maxLines = 1).size.width + with(density) { TitleIconRoom.roundToPx() }
        }
    return if (needed <= room) title else short
}

/**
 * What a tab too narrow for its title shows for a book: its acronym ([shortTitle], a pinned tab's label) then its place,
 * as a reference is written ("או"ח רנג" for "שולחן ערוך, אורח חיים - סימן רנג"). The place is what follows the last
 * " - " of [title], where BookContentViewModel joins the book's name and the heading being read, without the unit's
 * word before a number ("סימן", "דף"). Null for a tab without a short title, which is not a book's: its title stays.
 */
internal fun narrowTabTitle(
    title: String,
    shortTitle: String,
): String? {
    if (shortTitle.isBlank()) return null
    val place = title.substringAfterLast(TITLE_JOINT, "").trim()
    return listOf(shortTitle.trim(), place.withoutUnit()).filter(String::isNotEmpty).joinToString(" ")
}

// "סימן רנג" as "רנג": the unit's word goes only before one short word, a number or a daf, never from a heading in
// words ("פרק שלישי", "הלכות שבת").
private fun String.withoutUnit(): String {
    val words = split(' ').filter(String::isNotEmpty)
    val numbered = words.size == 2 && words[0] in PLACE_UNITS && words[1].count(Char::isLetter) <= MAX_NUMBER_LETTERS
    return if (numbered) words[1] else this
}

private const val TITLE_JOINT = " - "

// The most letters a place's number holds ("תרצז", or a daf, "קעו:"): a longer word is a heading in words ("שלישי").
private const val MAX_NUMBER_LETTERS = 4

private val PLACE_UNITS = setOf("דף", "עמוד", "פרק", "פסוק", "משנה", "הלכה", "סימן", "סעיף", "אות", "מזמור", "שער")

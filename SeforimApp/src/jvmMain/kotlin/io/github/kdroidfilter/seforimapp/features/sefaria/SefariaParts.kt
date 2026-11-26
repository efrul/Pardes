package io.github.kdroidfilter.seforimapp.features.sefaria

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.sefaria_back
import seforimapp.seforimapp.generated.resources.sefaria_generation_amoraim
import seforimapp.seforimapp.generated.resources.sefaria_generation_between
import seforimapp.seforimapp.generated.resources.sefaria_generation_tannaim
import seforimapp.seforimapp.generated.resources.sefaria_generation_zugot

// The parts the windows of Sefaria's topics and persons are made of, as the dictionary's: pills, rows lit on hover.

/** True in the topics' and persons' own windows (as LocalDictionaryWindow): only they take a topic or a person asked for. */
internal val LocalSefariaWindow = staticCompositionLocalOf { false }

/** A name as a pill, with its [count] after it; lit when [lit], as the dictionary's chips. */
@Composable
internal fun SefariaChip(
    text: String,
    count: Int?,
    lit: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (lit) {
                    accent.copy(alpha = 0.16f)
                } else {
                    JewelTheme.globalColors.text.normal
                        .copy(alpha = 0.05f)
                },
            ).clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Normal,
            color = if (lit) accent else JewelTheme.globalColors.text.normal,
            maxLines = 1,
        )
        if (count != null) Text(count.toString(), fontSize = 11.sp, color = JewelTheme.globalColors.text.info, maxLines = 1)
    }
}

/** Pills in rows that wrap. */
@Composable
internal fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

/** A titled group of pills, as a page's section; nothing when [count] is 0. */
@Composable
internal fun ChipSection(
    title: String,
    count: Int,
    content: @Composable () -> Unit,
) {
    if (count == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = JewelTheme.globalColors.text.info)
        ChipFlow(content)
    }
}

/**
 * A line of the library: its book and place in [accent], then its text, from the words around [around] when given (a
 * name, a word searched) with them bold. A click opens it.
 */
@Composable
internal fun SourceRow(
    line: SefariaLine,
    accent: Color,
    around: String? = null,
    showBook: Boolean = true,
    onOpen: () -> Unit,
) {
    HoverBox(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (showBook) line.heRef.ifEmpty { line.bookTitle } else line.heRef.removePrefix(line.bookTitle).trimStart(',', ' '),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                marked(snippet(plainText(line.content), around), around),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = JewelTheme.globalColors.text.normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A page's title, with the way back at the start of the reading direction. */
@Composable
internal fun BackTitle(
    title: String,
    accent: Color,
    onBack: () -> Unit,
    end: @Composable () -> Unit = {},
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
            Icon(if (rtl) AllIconsKeys.General.ChevronRight else AllIconsKeys.General.ChevronLeft, stringResource(Res.string.sefaria_back))
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, modifier = Modifier.weight(1f))
        end()
    }
}

@Composable
internal fun SefariaHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = JewelTheme.globalColors.text.info)
    }
}

/** A person's [generation] as Sefaria codes it, in words (T3: התנאים, דור 3); null for a code it does not know. */
@Composable
internal fun generationLabel(generation: String): String? {
    val code = generation.trim()
    val number = code.drop(1).replace('/', '–')
    return when {
        code == "TA" -> stringResource(Res.string.sefaria_generation_between)
        code.startsWith("T") && number.isNotEmpty() -> stringResource(Res.string.sefaria_generation_tannaim, number)
        code.startsWith("A") && number.isNotEmpty() -> stringResource(Res.string.sefaria_generation_amoraim, number.replace("A", ""))
        code.startsWith("Z") && number.isNotEmpty() -> stringResource(Res.string.sefaria_generation_zugot, number)
        else -> null
    }
}

private val TAG = Regex("<[^>]+>")
private val ENTITY = Regex("&#?[A-Za-z0-9]+;")
private val SPACES = Regex("\\s+")
private const val NIKUD = "[\\u0591-\\u05C7]*"
private const val SNIPPET_BEFORE = 60
private const val SNIPPET_CHARS = 220

/** A line's HTML as plain text: its tags and entities out, its spaces one. */
internal fun plainText(html: String): String =
    html
        .replace(TAG, " ")
        .replace(ENTITY, " ")
        .replace(SPACES, " ")
        .trim()

/** [word], letter by letter with any nikud between, to find in a text with its nikud. */
private fun pattern(word: String): Regex =
    Regex(
        word
            .filter { it in 'א'..'ת' || it == ' ' }
            .trim()
            .map {
                if (it ==
                    ' '
                ) {
                    "\\s+"
                } else {
                    Regex.escape(it.toString())
                }
            }.joinToString(NIKUD),
    )

/** [text] from a little before [around] (its first place in it), [SNIPPET_CHARS] long at most; its start otherwise. */
internal fun snippet(
    text: String,
    around: String?,
): String {
    val at = around?.takeIf { it.any { c -> c in 'א'..'ת' } }?.let { pattern(it).find(text)?.range?.first } ?: 0
    val start = (at - SNIPPET_BEFORE).coerceAtLeast(0).let { if (it == 0) 0 else text.indexOf(' ', it).coerceIn(0, at) }
    val piece = text.substring(start).take(SNIPPET_CHARS)
    return (if (start > 0) "…" else "") + piece + (if (start + piece.length < text.length) "…" else "")
}

/** [text] with each place of [word] bold. */
private fun marked(
    text: String,
    word: String?,
): AnnotatedString {
    val places = word?.takeIf { it.any { c -> c in 'א'..'ת' } }?.let { pattern(it).findAll(text).map { m -> m.range }.toList() }.orEmpty()
    return buildAnnotatedString {
        append(text)
        places.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.first, it.last + 1) }
    }
}

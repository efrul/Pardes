package io.github.kdroidfilter.seforimapp.features.sefaria

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberCopyToClipboard
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.ContextMenuItemOption
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.sefaria_copy_mishnah
import seforimapp.seforimapp.generated.resources.sefaria_copy_story
import seforimapp.seforimapp.generated.resources.sefaria_copy_sugya
import seforimapp.seforimapp.generated.resources.sefaria_mark_mishnah
import seforimapp.seforimapp.generated.resources.sefaria_mark_story
import seforimapp.seforimapp.generated.resources.sefaria_mark_sugya

/**
 * The reader's menu items of the passage of Sefaria's a line is in (SefariaSugyot): the sugya (or the Mishnah in the
 * Gemara, or the story of the Tanakh) marked in the text, as a reading's range is marked ([BookContentEvent.OpenBookAtLine]
 * with its last line), or copied whole.
 */
class SugyaMenu internal constructor(
    private val lines: SefariaLines,
    @StructuredScope private val scope: CoroutineScope,
    private val copy: (String) -> Unit,
    private val onEvent: (BookContentEvent) -> Unit,
    private val labels: Map<Char, Pair<String, String>>,
) {
    /** The items for the line [line] right-clicked; none when it is in no passage or the passages are not read yet. */
    fun items(line: Line?): List<ContextMenuItem> {
        val heRef = line?.heRef?.takeIf { it.isNotBlank() } ?: return emptyList()
        val passage = SefariaSugyot.ifRead()?.of(heRef) ?: return emptyList()
        val (mark, copyLabel) = labels[passage.kind] ?: return emptyList()
        return listOf(
            ContextMenuItemOption(icon = AllIconsKeys.Actions.Selectall, label = mark) {
                scope.launch {
                    val ends = lines.linesOf(listOf(passage.lines.first(), passage.lines.last()))
                    val first = ends[passage.lines.first()] ?: return@launch
                    val last = ends[passage.lines.last()] ?: return@launch
                    onEvent(BookContentEvent.OpenBookAtLine(first.bookId, first.id, last.id))
                }
            },
            ContextMenuItemOption(icon = AllIconsKeys.Actions.Copy, label = copyLabel) {
                scope.launch {
                    val ends = lines.linesOf(listOf(passage.lines.first(), passage.lines.last()))
                    val first = ends[passage.lines.first()] ?: return@launch
                    val last = ends[passage.lines.last()] ?: return@launch
                    val text = lines.range(first.bookId, first.lineIndex, last.lineIndex).joinToString("\n") { plainText(it.content) }
                    if (text.isNotBlank()) copy(text)
                }
            },
        )
    }
}

/** The reader's [SugyaMenu], the passages read as soon as the reader shows: the menu reads them without waiting. */
@Composable
fun rememberSugyaMenu(onEvent: (BookContentEvent) -> Unit): SugyaMenu {
    val lines = LocalAppGraph.current.sefariaLines
    val scope = rememberCoroutineScope()
    val copy = rememberCopyToClipboard()
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { SefariaSugyot.shipped } }
    val labels =
        mapOf(
            'S' to (stringResource(Res.string.sefaria_mark_sugya) to stringResource(Res.string.sefaria_copy_sugya)),
            'M' to (stringResource(Res.string.sefaria_mark_mishnah) to stringResource(Res.string.sefaria_copy_mishnah)),
            'B' to (stringResource(Res.string.sefaria_mark_story) to stringResource(Res.string.sefaria_copy_story)),
        )
    return remember(lines, scope, copy, onEvent, labels) { SugyaMenu(lines, scope, copy, onEvent, labels) }
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_scan_first_amud_a
import seforimapp.seforimapp.generated.resources.daf_scan_first_amud_b
import seforimapp.seforimapp.generated.resources.daf_scan_first_fix
import seforimapp.seforimapp.generated.resources.daf_scan_first_page
import seforimapp.seforimapp.generated.resources.daf_scan_first_page_guess
import seforimapp.seforimapp.generated.resources.daf_scan_first_reset
import seforimapp.seforimapp.generated.resources.daf_scan_first_top
import seforimapp.seforimapp.generated.resources.save_button
import seforimapp.seforimapp.generated.resources.settings_cancel

// Pardes (request 52 of docs/DESKTOP_STAGE3_PLAN.md): "בתיקייה אישית של גמרות, לקבוע לכל מסכת באיזה עמוד בקובץ נמצא
// דף ב", above the tractate's scan, where its pages show whether it is right.

/**
 * The line above the pages of [source], a file of the user's folder: the file's name, the page of its daf ב., set by
 * the user or guessed, and "תיקון", which asks what the page at the top ([topPage], the file's page) really is: its daf
 * and amud. [onSet] takes the file's page of daf ב. found so (null when guessed again) and the tractate's page that is
 * then at the top.
 */
@Composable
internal fun ScanFirstPageBar(
    source: ScanSource,
    topPage: () -> Int,
    onSet: (first: Int?, page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember(source) { mutableStateOf(false) }
    var top by remember { mutableIntStateOf(0) }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editing) {
            FirstPageEditor(source, top, onCancel = { editing = false }) { first, page ->
                editing = false
                onSet(first, page)
            }
        } else {
            val info = JewelTheme.globalColors.text.info
            Text(source.file.name, Modifier.weight(1f, fill = false), color = info, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val said = if (source.firstSet) Res.string.daf_scan_first_page else Res.string.daf_scan_first_page_guess
            Text(stringResource(said, source.first + 1), color = info)
            Link(
                text = stringResource(Res.string.daf_scan_first_fix),
                onClick = {
                    top = topPage()
                    editing = true
                },
            )
        }
    }
}

// What the page at the top (the file's page [top], titled by its amud) really is: a daf typed, amud a or b, and the
// file's page of daf ב. then ([firstFromTop]); back to the guess when the user set it
@Composable
private fun RowScope.FirstPageEditor(
    source: ScanSource,
    top: Int,
    onCancel: () -> Unit,
    onSet: (first: Int?, page: Int) -> Unit,
) {
    val daf = rememberTextFieldState()
    var amudB by remember { mutableStateOf(false) }
    val amud = dafTyped(daf.text.toString())?.let { "$it${if (amudB) 'b' else 'a'}" }
    val first = amud?.let { firstFromTop(top, it) }
    val save = { if (amud != null && first != null) onSet(first, top - first) }
    Text(stringResource(Res.string.daf_scan_first_top, amudTitle(amudOfScanPage(top - source.first))))
    TextField(
        state = daf,
        modifier =
            Modifier.width(DAF_FIELD_WIDTH).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter -> save()
                    Key.Escape -> onCancel()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    )
    RadioButtonRow(text = stringResource(Res.string.daf_scan_first_amud_a), selected = !amudB, onClick = { amudB = false })
    RadioButtonRow(text = stringResource(Res.string.daf_scan_first_amud_b), selected = amudB, onClick = { amudB = true })
    DefaultButton(onClick = save, enabled = first != null) { Text(stringResource(Res.string.save_button)) }
    OutlinedButton(onClick = onCancel) { Text(stringResource(Res.string.settings_cancel)) }
    if (source.firstSet) Link(text = stringResource(Res.string.daf_scan_first_reset), onClick = { onSet(null, top - source.first) })
}

private val DAF_FIELD_WIDTH = 56.dp

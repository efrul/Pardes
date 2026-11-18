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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.firstPageOfKey
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.pickScansFolder
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_scan_first_reset
import seforimapp.seforimapp.generated.resources.daf_scans_choose
import seforimapp.seforimapp.generated.resources.daf_scans_cut
import seforimapp.seforimapp.generated.resources.daf_scans_cut_description
import seforimapp.seforimapp.generated.resources.daf_scans_cut_enabled
import seforimapp.seforimapp.generated.resources.daf_scans_cut_remove
import seforimapp.seforimapp.generated.resources.daf_scans_description
import seforimapp.seforimapp.generated.resources.daf_scans_first_page_row
import seforimapp.seforimapp.generated.resources.daf_scans_first_pages
import seforimapp.seforimapp.generated.resources.daf_scans_first_pages_description
import seforimapp.seforimapp.generated.resources.daf_scans_first_pages_none
import seforimapp.seforimapp.generated.resources.daf_scans_folder
import seforimapp.seforimapp.generated.resources.daf_scans_no_folder
import seforimapp.seforimapp.generated.resources.daf_scans_no_otzaria
import seforimapp.seforimapp.generated.resources.daf_scans_otzaria
import seforimapp.seforimapp.generated.resources.daf_scans_remove
import seforimapp.seforimapp.generated.resources.daf_scans_title
import java.io.File

// Pardes (requests 41, 48 and 56 of docs/DESKTOP_STAGE2.md): where the tractates' scans are looked for, in the settings
// rather than over the scan (the user's decision of 2026-11-13), and their pages cut into images, by the user's word
// (request 51 of docs/DESKTOP_STAGE3_PLAN.md), and the pages of the user's files where tractates begin (request 52).

/**
 * Settings › צורת הדף: where the tractates' scans are looked for (DafScans): the folder of scanned tractates the user
 * chose, with a button that chooses another and one that lets it go, and Otzaria's folders, found by themselves; the
 * pages of its files where tractates begin, as the user set them; and the room their pages cut into images take
 * (DafScanPages), with a button that removes them.
 */
@Composable
fun DafScansCard() {
    val scans = LocalAppGraph.current.dafScans
    val scope = rememberCoroutineScope()
    val folder by scans.folder.collectAsState()
    val otzaria by produceState(emptyList<File>()) { value = scans.otzariaFolders() }
    val info = JewelTheme.globalColors.text.info
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
        Text(text = stringResource(Res.string.daf_scans_title), fontSize = 15.sp)
        Text(text = stringResource(Res.string.daf_scans_description), fontSize = 12.sp, color = info)
        Text(text = stringResource(Res.string.daf_scans_folder), fontSize = 13.sp)
        Text(
            text = folder?.let(::isolatedPath) ?: stringResource(Res.string.daf_scans_no_folder),
            fontSize = 12.sp,
            color = info,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { scope.launch { pickScansFolder()?.let(scans::setFolder) } }) {
                Text(stringResource(Res.string.daf_scans_choose))
            }
            if (folder != null) {
                OutlinedButton(onClick = { scans.setFolder(null) }) { Text(stringResource(Res.string.daf_scans_remove)) }
            }
        }
        Text(text = stringResource(Res.string.daf_scans_otzaria), fontSize = 13.sp)
        if (otzaria.isEmpty()) Text(stringResource(Res.string.daf_scans_no_otzaria), fontSize = 12.sp, color = info)
        for (dir in otzaria) Text(isolatedPath(dir.path), fontSize = 12.sp, color = info)
        FirstPages()
        CutPages()
    }
}

// The pages of the user's files where tractates' daf ב. is, as the user set them above their scans (request 52 of
// stage 3): each one changed, or guessed again
@Composable
private fun FirstPages(modifier: Modifier = Modifier) {
    val scans = LocalAppGraph.current.dafScans
    val pages by scans.firstPages.collectAsState()
    val info = JewelTheme.globalColors.text.info
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(Res.string.daf_scans_first_pages), fontSize = 13.sp)
        Text(text = stringResource(Res.string.daf_scans_first_pages_description), fontSize = 12.sp, color = info)
        if (pages.isEmpty()) Text(stringResource(Res.string.daf_scans_first_pages_none), fontSize = 12.sp, color = info)
        for ((key, page) in pages.toSortedMap()) {
            FirstPageRow(key, page) { scans.setFirstPage(key, it) }
        }
    }
}

// A tractate's daf ב. in a file of the user's: its page in the file (from 1, as typed), and back to the guess
@Composable
private fun FirstPageRow(
    key: String,
    page: Int,
    onSet: (Int?) -> Unit,
) {
    val (file, tractate) = firstPageOfKey(key)
    val field = rememberTextFieldState((page + 1).toString())
    val set by rememberUpdatedState(onSet)
    LaunchedEffect(field) {
        snapshotFlow {
            field.text
                .toString()
                .trim()
                .toIntOrNull()
        }.filterNotNull()
            .filter { it >= 1 }
            .collect { set(it - 1) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.daf_scans_first_page_row, tractate, file), Modifier.weight(1f, fill = false), fontSize = 12.sp)
        TextField(state = field, modifier = Modifier.width(PAGE_FIELD_WIDTH))
        Link(text = stringResource(Res.string.daf_scan_first_reset), onClick = { set(null) })
    }
}

private val PAGE_FIELD_WIDTH = 64.dp

// The scans' pages cut into images beside the library (request 56), by the user's word (request 51 of stage 3): whether
// they are cut, the room they take, and their removal
@Composable
private fun CutPages(modifier: Modifier = Modifier) {
    val pages = LocalAppGraph.current.dafScanPages
    val scans = LocalAppGraph.current.dafScans
    val cutting by scans.cut.collectAsState()
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pages) { bytes = pages.bytes() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(Res.string.daf_scans_cut), fontSize = 13.sp)
        CheckboxRow(
            text = stringResource(Res.string.daf_scans_cut_enabled),
            checked = cutting == true,
            onCheckedChange = scans::setCut,
        )
        Text(
            text = stringResource(Res.string.daf_scans_cut_description, formatBytes(bytes)),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        if (bytes > 0) {
            OutlinedButton(onClick = {
                scope.launch {
                    pages.clear()
                    bytes = pages.bytes()
                }
            }) { Text(stringResource(Res.string.daf_scans_cut_remove)) }
        }
    }
}

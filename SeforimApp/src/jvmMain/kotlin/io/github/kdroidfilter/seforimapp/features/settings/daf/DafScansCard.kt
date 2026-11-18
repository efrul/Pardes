package io.github.kdroidfilter.seforimapp.features.settings.daf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.pickScansFolder
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_scans_choose
import seforimapp.seforimapp.generated.resources.daf_scans_cut
import seforimapp.seforimapp.generated.resources.daf_scans_cut_description
import seforimapp.seforimapp.generated.resources.daf_scans_cut_enabled
import seforimapp.seforimapp.generated.resources.daf_scans_cut_remove
import seforimapp.seforimapp.generated.resources.daf_scans_description
import seforimapp.seforimapp.generated.resources.daf_scans_folder
import seforimapp.seforimapp.generated.resources.daf_scans_no_folder
import seforimapp.seforimapp.generated.resources.daf_scans_no_otzaria
import seforimapp.seforimapp.generated.resources.daf_scans_otzaria
import seforimapp.seforimapp.generated.resources.daf_scans_remove
import seforimapp.seforimapp.generated.resources.daf_scans_title
import java.io.File

// Pardes (requests 41, 48 and 56 of docs/DESKTOP_STAGE2.md): where the tractates' scans are looked for, in the settings
// rather than over the scan (the user's decision of 2026-11-13), and their pages cut into images, by the user's word
// (request 51 of docs/DESKTOP_STAGE3_PLAN.md).

/**
 * Settings › צורת הדף: where the tractates' scans are looked for (DafScans): the folder of scanned tractates the user
 * chose, with a button that chooses another and one that lets it go, and Otzaria's folders, found by themselves; and
 * the room their pages cut into images take (DafScanPages), with a button that removes them.
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
        CutPages()
    }
}

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

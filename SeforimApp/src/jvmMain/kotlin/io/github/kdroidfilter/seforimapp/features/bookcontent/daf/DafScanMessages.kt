package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_scan_choose
import seforimapp.seforimapp.generated.resources.daf_scan_cut_no
import seforimapp.seforimapp.generated.resources.daf_scan_cut_question
import seforimapp.seforimapp.generated.resources.daf_scan_cut_question_hint
import seforimapp.seforimapp.generated.resources.daf_scan_cut_yes
import seforimapp.seforimapp.generated.resources.daf_scan_missing
import seforimapp.seforimapp.generated.resources.daf_scan_missing_hint

// Pardes (requests 41 to 47 of docs/DESKTOP_STAGE2.md, and 51 of docs/DESKTOP_STAGE3_PLAN.md): what a tractate's scan
// view (DafScanView.kt) shows in its pages' place before there are pages: no scan found, or whether to cut the scans.

/** No scan of [tractate]: where it is looked for, and a button that chooses the folder of scans ([onChoose]). */
@Composable
internal fun NoScan(
    tractate: String,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(max = 520.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.daf_scan_missing, tractate), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(Res.string.daf_scan_missing_hint),
            color = JewelTheme.globalColors.text.info,
            textAlign = TextAlign.Center,
        )
        DefaultButton(onClick = onChoose) { Text(stringResource(Res.string.daf_scan_choose)) }
    }
}

/**
 * Whether to cut the scans into images, asked in the scan's place the first time one opens: what it gives, the room it
 * takes and where, and the two answers ([onAnswer]), which the settings change (request 51 of DESKTOP_STAGE3_PLAN.md).
 */
@Composable
internal fun CutQuestion(
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(max = 520.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.daf_scan_cut_question), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(Res.string.daf_scan_cut_question_hint),
            color = JewelTheme.globalColors.text.info,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultButton(onClick = { onAnswer(true) }) { Text(stringResource(Res.string.daf_scan_cut_yes)) }
            OutlinedButton(onClick = { onAnswer(false) }) { Text(stringResource(Res.string.daf_scan_cut_no)) }
        }
    }
}

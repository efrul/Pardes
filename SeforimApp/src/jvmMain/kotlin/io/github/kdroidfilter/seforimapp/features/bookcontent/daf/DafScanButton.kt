package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.icons.ScanPage
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_scan
import seforimapp.seforimapp.generated.resources.daf_scan_tooltip

/**
 * Pardes (requests 41 and 46 of docs/DESKTOP_STAGE2.md): the end bar's button of a Talmud tractate, under the daf's
 * own: the tractate's scan ([DafScanReader]) in place of its text or its daf, while [selected]; [onClick] turns it.
 */
@Composable
fun DafScanButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(Res.string.daf_scan_tooltip),
        onClick = onClick,
        isSelected = selected,
        icon = ScanPage,
        label = stringResource(Res.string.daf_scan),
        modifier = modifier,
        iconDescription = stringResource(Res.string.daf_scan),
    )
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.ScanPage
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_scan
import seforimapp.seforimapp.generated.resources.daf_scan_tooltip

/**
 * Pardes (request 41 of docs/DESKTOP_STAGE2.md): the end bar's button while a daf is shown, under the daf's own: the
 * page's scan shown in place of the daf Pardes draws ([DafScanView]), in every tab, as the daf's marks are.
 */
@Composable
fun DafScanButton(modifier: Modifier = Modifier) {
    val appSettings = LocalAppGraph.current.appSettings
    val shown by appSettings.dafScanFlow.collectAsState()
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(Res.string.daf_scan_tooltip),
        onClick = { appSettings.setDafScanShown(!shown) },
        isSelected = shown,
        icon = ScanPage,
        label = stringResource(Res.string.daf_scan),
        modifier = modifier,
        iconDescription = stringResource(Res.string.daf_scan),
    )
}

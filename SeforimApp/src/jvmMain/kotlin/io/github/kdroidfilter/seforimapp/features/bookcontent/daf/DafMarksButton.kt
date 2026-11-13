package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.MarkedLine
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_marks
import seforimapp.seforimapp.generated.resources.daf_marks_tooltip

/**
 * The end bar's button while a daf is shown, in the diacritics' place (the printed page has none to hide): the daf's
 * marks shown or hidden, the lines its alignment is not sure of drawn lighter and the lines the reader's review named
 * tinted.
 */
@Composable
fun DafMarksButton(modifier: Modifier = Modifier) {
    val appSettings = LocalAppGraph.current.appSettings
    val shown by appSettings.dafMarksFlow.collectAsState()
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(Res.string.daf_marks_tooltip),
        onClick = { appSettings.setDafMarksShown(!shown) },
        isSelected = shown,
        icon = MarkedLine,
        label = stringResource(Res.string.daf_marks),
        modifier = modifier,
        iconDescription = stringResource(Res.string.daf_marks),
    )
}

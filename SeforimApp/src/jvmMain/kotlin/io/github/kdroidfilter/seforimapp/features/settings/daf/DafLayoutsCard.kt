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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafDownloads
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafLayouts
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.rememberDafLayouts
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_download_failed
import seforimapp.seforimapp.generated.resources.daf_download_retry
import seforimapp.seforimapp.generated.resources.daf_layouts_cancel
import seforimapp.seforimapp.generated.resources.daf_layouts_checking
import seforimapp.seforimapp.generated.resources.daf_layouts_current
import seforimapp.seforimapp.generated.resources.daf_layouts_description
import seforimapp.seforimapp.generated.resources.daf_layouts_download
import seforimapp.seforimapp.generated.resources.daf_layouts_none
import seforimapp.seforimapp.generated.resources.daf_layouts_progress
import seforimapp.seforimapp.generated.resources.daf_layouts_status
import seforimapp.seforimapp.generated.resources.daf_layouts_title
import seforimapp.seforimapp.generated.resources.daf_layouts_update_hint

/**
 * Settings › ניהול נתונים: the daf view's layouts (צורת הדף) as released for download, how many of them the library's
 * folder has as released, and every one it lacks, or has otherwise, downloaded at once ([DafDownloads]). The download
 * goes on when the settings close.
 */
@Composable
fun DafLayoutsCard() {
    val layouts = rememberDafLayouts()
    val downloads = remember(layouts) { DafDownloads.of(layouts) }
    val state by downloads.state.collectAsState()
    // Each time the card is shown: a tractate may have been imported or downloaded from its page since
    LaunchedEffect(downloads) {
        downloads.refresh()
        downloads.recount()
    }
    DafLayoutsCardContent(
        state = state,
        onDownload = { downloads.download(state.toGet) },
        onCancel = downloads::cancel,
        onRetry = { if (state.manifest != null) downloads.download(state.toGet) else downloads.refresh(force = true) },
    )
}

@Composable
internal fun DafLayoutsCardContent(
    state: DafDownloads.State,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(Res.string.daf_layouts_title), fontSize = 15.sp)
        Hint(stringResource(Res.string.daf_layouts_description, DafLayouts.DIR_NAME))
        val manifest = state.manifest
        val work = state.work
        when {
            work != null -> {
                val percent = if (work.total > 0) (work.done * PERCENT / work.total).toInt() else 0
                Text(
                    text = stringResource(Res.string.daf_layouts_progress, work.tractateHe, work.index + 1, work.count, percent),
                    fontSize = 13.sp,
                )
                AnimatedHorizontalProgressBar(value = percent / PERCENT.toFloat(), modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onCancel) { Text(stringResource(Res.string.daf_layouts_cancel)) }
            }
            state.checking -> Text(text = stringResource(Res.string.daf_layouts_checking), fontSize = 13.sp)
            manifest != null -> {
                val current = manifest.tractates.count { state.installedOf(it) == DafDownloads.Installed.CURRENT }
                Text(
                    text = stringResource(Res.string.daf_layouts_status, current, manifest.tractates.size, manifest.daf),
                    fontSize = 13.sp,
                )
                val toGet = state.toGet
                if (toGet.isEmpty()) {
                    Text(text = stringResource(Res.string.daf_layouts_current), fontSize = 13.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DefaultButton(onClick = onDownload) {
                            Text(stringResource(Res.string.daf_layouts_download, toGet.size, formatBytes(toGet.sumOf { it.size })))
                        }
                    }
                    if (toGet.any { state.installedOf(it) == DafDownloads.Installed.OTHER }) {
                        Hint(stringResource(Res.string.daf_layouts_update_hint))
                    }
                }
            }
            state.checked && state.problem == null -> Text(text = stringResource(Res.string.daf_layouts_none), fontSize = 13.sp)
        }
        state.problem?.takeIf { work == null }?.let { problem ->
            Text(
                text = stringResource(Res.string.daf_download_failed, problem),
                fontSize = 13.sp,
                color = JewelTheme.globalColors.text.error,
            )
            OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.daf_download_retry)) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
}

private const val PERCENT = 100L

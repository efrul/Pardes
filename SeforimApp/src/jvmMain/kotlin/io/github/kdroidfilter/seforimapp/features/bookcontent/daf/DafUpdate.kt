package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_download_progress
import seforimapp.seforimapp.generated.resources.daf_update_available
import seforimapp.seforimapp.generated.resources.daf_update_now

/**
 * The tractate's newer version among the released layouts than the folder's file ([DafDownloads.State]), or null: with
 * the releases not read, the file the release's own, or a later one than it.
 */
internal fun DafDownloads.State.newerFor(tractate: String): DafManifest.Entry? {
    val entry = manifest?.entryFor(tractate) ?: return null
    val released = entry.version ?: return null
    if (installedOf(entry) != DafDownloads.Installed.OTHER) return null
    val mine = versions[entry.tractate]
    return entry.takeIf { mine == null || mine < released }
}

/**
 * A quiet line on the daf of [tractate] when its layout was released again since the folder's file: the new version
 * and a link that downloads it, then [onUpdate] for the daf to read the folder again. The releases are read once a
 * run, when the app checks for updates by itself (its settings' switch).
 */
@Composable
internal fun DafUpdateLine(
    tractate: String,
    downloads: DafDownloads,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appSettings = LocalAppGraph.current.appSettings
    val state by downloads.state.collectAsState()
    LaunchedEffect(downloads) { if (appSettings.isAutoUpdateCheckEnabled()) downloads.refresh() }
    val currentOnUpdate by rememberUpdatedState(onUpdate)
    val shownAt = remember(downloads) { state.revision }
    LaunchedEffect(state.revision) { if (state.revision != shownAt) currentOnUpdate() }

    val entry = state.newerFor(tractate)
    val work = state.work
    if (entry == null && work == null) return
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(JewelTheme.globalColors.panelBackground)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (work != null) {
            val percent = if (work.total > 0) (work.done * PERCENT / work.total).toInt() else 0
            Text(stringResource(Res.string.daf_download_progress, work.tractateHe, percent), fontSize = 12.sp)
        } else if (entry != null) {
            Text(stringResource(Res.string.daf_update_available, entry.version ?: 0), fontSize = 12.sp)
            Link(text = stringResource(Res.string.daf_update_now), onClick = { downloads.download(listOf(entry)) })
        }
    }
}

private const val PERCENT = 100L

package io.github.kdroidfilter.seforimapp.features.settings.daf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafDownloads
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafLayouts
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafManifest
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.rememberDafLayouts
import io.github.kdroidfilter.seforimapp.features.settings.downloads.StartOffers
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_download_failed
import seforimapp.seforimapp.generated.resources.daf_download_retry
import seforimapp.seforimapp.generated.resources.daf_layouts_available_version
import seforimapp.seforimapp.generated.resources.daf_layouts_cancel
import seforimapp.seforimapp.generated.resources.daf_layouts_checking
import seforimapp.seforimapp.generated.resources.daf_layouts_current
import seforimapp.seforimapp.generated.resources.daf_layouts_description
import seforimapp.seforimapp.generated.resources.daf_layouts_download
import seforimapp.seforimapp.generated.resources.daf_layouts_get_one
import seforimapp.seforimapp.generated.resources.daf_layouts_hide_all
import seforimapp.seforimapp.generated.resources.daf_layouts_installed_other
import seforimapp.seforimapp.generated.resources.daf_layouts_installed_version
import seforimapp.seforimapp.generated.resources.daf_layouts_none
import seforimapp.seforimapp.generated.resources.daf_layouts_not_installed
import seforimapp.seforimapp.generated.resources.daf_layouts_progress
import seforimapp.seforimapp.generated.resources.daf_layouts_show_all
import seforimapp.seforimapp.generated.resources.daf_layouts_status
import seforimapp.seforimapp.generated.resources.daf_layouts_title
import seforimapp.seforimapp.generated.resources.daf_layouts_update_hint
import seforimapp.seforimapp.generated.resources.daf_layouts_update_one
import seforimapp.seforimapp.generated.resources.daf_layouts_version

/**
 * Settings › צורת הדף: the daf view's layouts as released for download, how many of them the library's
 * folder has as released, and every one it lacks, or has otherwise, downloaded at once or one by one ([DafDownloads]):
 * each tractate has a version of its own, and a row with the folder's and the release's. The download goes on when the
 * settings close. Marked and brought into view when a start opened the settings for a newer version (StartOffers).
 */
@Composable
fun DafLayoutsCard() {
    val layouts = rememberDafLayouts()
    val downloads = remember(layouts) { DafDownloads.of(layouts) }
    val state by downloads.state.collectAsState()
    val offered = remember { StartOffers.take(SettingsDestination.Daf) }
    // Each time the card is shown: a tractate may have been imported or downloaded from its page since
    LaunchedEffect(downloads) {
        downloads.refresh()
        downloads.recount()
    }
    DafLayoutsCardContent(
        state = state,
        onDownload = downloads::download,
        onCancel = downloads::cancel,
        onRetry = { if (state.manifest != null) downloads.download(state.toGet) else downloads.refresh(force = true) },
        marked = offered,
    )
}

/** The card for [state]; [marked] when the settings were opened for it: it comes into view, its frame lit. */
@Composable
internal fun DafLayoutsCardContent(
    state: DafDownloads.State,
    onDownload: (List<DafManifest.Entry>) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    marked: Boolean = false,
) {
    val inView = remember { BringIntoViewRequester() }
    if (marked) {
        // Once the screen is laid out, and again once the check has settled the card's lines
        LaunchedEffect(state.checking) {
            withFrameNanos {}
            inView.bringIntoView()
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val colors = JewelTheme.globalColors
    Column(
        modifier =
            modifier
                .bringIntoViewRequester(inView)
                .fillMaxWidth()
                .clip(shape)
                .border(if (marked) 2.dp else 1.dp, if (marked) colors.outlines.focused else colors.borders.normal, shape)
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
            manifest != null -> ReleaseLines(manifest, state, onDownload)
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

/** The release's lines: how many tractates are current, a button that downloads the others, and each tractate's row. */
@Composable
private fun ReleaseLines(
    manifest: DafManifest,
    state: DafDownloads.State,
    onDownload: (List<DafManifest.Entry>) -> Unit,
) {
    // One column, with the card's spacing between its lines
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                DefaultButton(onClick = { onDownload(toGet) }) {
                    Text(stringResource(Res.string.daf_layouts_download, toGet.size, formatBytes(toGet.sumOf { it.size })))
                }
            }
            if (toGet.any { state.installedOf(it) == DafDownloads.Installed.OTHER }) {
                Hint(stringResource(Res.string.daf_layouts_update_hint))
            }
        }
        Tractates(manifest, state, onDownload)
    }
}

/**
 * A row for each tractate of the release: its version in the folder and the release's, and, for one the folder lacks or
 * has otherwise, a button that downloads it alone. Those to download are always listed, the others when asked.
 */
@Composable
private fun Tractates(
    manifest: DafManifest,
    state: DafDownloads.State,
    onDownload: (List<DafManifest.Entry>) -> Unit,
) {
    var all by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (entry in manifest.tractates) {
            val installed = state.installedOf(entry)
            if (installed == DafDownloads.Installed.CURRENT && !all) continue
            val line = tractateLine(entry, installed, state.versions[entry.tractate])
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = line, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (installed != DafDownloads.Installed.CURRENT) {
                    val missing = installed == DafDownloads.Installed.MISSING
                    val label = if (missing) Res.string.daf_layouts_get_one else Res.string.daf_layouts_update_one
                    Link(text = stringResource(label), onClick = { onDownload(listOf(entry)) })
                }
            }
        }
        Link(
            text = stringResource(if (all) Res.string.daf_layouts_hide_all else Res.string.daf_layouts_show_all),
            onClick = { all = !all },
        )
    }
}

/** A tractate's line: its name, what the folder has of it ([mine]: its file's version), and the release's version. */
@Composable
private fun tractateLine(
    entry: DafManifest.Entry,
    installed: DafDownloads.Installed,
    mine: Int?,
): String {
    val released = entry.version
    val parts =
        when (installed) {
            DafDownloads.Installed.CURRENT -> listOfNotNull(released?.let { stringResource(Res.string.daf_layouts_version, it) })
            DafDownloads.Installed.MISSING ->
                listOfNotNull(
                    stringResource(Res.string.daf_layouts_not_installed),
                    released?.let { stringResource(Res.string.daf_layouts_available_version, it) },
                )
            DafDownloads.Installed.OTHER ->
                listOfNotNull(
                    mine?.let { stringResource(Res.string.daf_layouts_installed_version, it) }
                        ?: stringResource(Res.string.daf_layouts_installed_other),
                    released?.let { stringResource(Res.string.daf_layouts_available_version, it) },
                )
        }
    return (listOf(entry.tractateHe) + parts).joinToString(" · ")
}

@Composable
private fun Hint(text: String) {
    Text(text = text, fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
}

private const val PERCENT = 100L

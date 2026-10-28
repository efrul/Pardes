package io.github.kdroidfilter.seforimapp.features.settings.semantic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.PackProblem
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPack
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPackReleases
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.library_problem_no_space
import seforimapp.seforimapp.generated.resources.semantic_pack_available
import seforimapp.seforimapp.generated.resources.semantic_pack_cancel
import seforimapp.seforimapp.generated.resources.semantic_pack_checking
import seforimapp.seforimapp.generated.resources.semantic_pack_description
import seforimapp.seforimapp.generated.resources.semantic_pack_dismiss
import seforimapp.seforimapp.generated.resources.semantic_pack_download
import seforimapp.seforimapp.generated.resources.semantic_pack_downloading
import seforimapp.seforimapp.generated.resources.semantic_pack_import
import seforimapp.seforimapp.generated.resources.semantic_pack_import_hint
import seforimapp.seforimapp.generated.resources.semantic_pack_installed
import seforimapp.seforimapp.generated.resources.semantic_pack_installed_unnumbered
import seforimapp.seforimapp.generated.resources.semantic_pack_no_index
import seforimapp.seforimapp.generated.resources.semantic_pack_no_library
import seforimapp.seforimapp.generated.resources.semantic_pack_no_model
import seforimapp.seforimapp.generated.resources.semantic_pack_none_yet
import seforimapp.seforimapp.generated.resources.semantic_pack_not_installed
import seforimapp.seforimapp.generated.resources.semantic_pack_other_library
import seforimapp.seforimapp.generated.resources.semantic_pack_other_model
import seforimapp.seforimapp.generated.resources.semantic_pack_ready
import seforimapp.seforimapp.generated.resources.semantic_pack_releases_page
import seforimapp.seforimapp.generated.resources.semantic_pack_restart
import seforimapp.seforimapp.generated.resources.semantic_pack_start_failed
import seforimapp.seforimapp.generated.resources.semantic_pack_title
import seforimapp.seforimapp.generated.resources.semantic_pack_unknown_library
import seforimapp.seforimapp.generated.resources.semantic_pack_unpacking
import seforimapp.seforimapp.generated.resources.semantic_pack_unreachable
import seforimapp.seforimapp.generated.resources.semantic_pack_unreadable
import seforimapp.seforimapp.generated.resources.semantic_pack_up_to_date
import seforimapp.seforimapp.generated.resources.semantic_pack_update
import seforimapp.seforimapp.generated.resources.semantic_problem_corrupted
import seforimapp.seforimapp.generated.resources.semantic_problem_failed
import seforimapp.seforimapp.generated.resources.semantic_problem_missing
import seforimapp.seforimapp.generated.resources.semantic_problem_not_a_pack
import seforimapp.seforimapp.generated.resources.semantic_problem_other_library
import seforimapp.seforimapp.generated.resources.semantic_problem_unusable
import java.io.File

/** Settings › ניהול נתונים: Pardes's semantic search, its pack beside the library, downloaded or imported from files. */
@Composable
fun SemanticPackCard() {
    val viewModel: SemanticPackViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    SemanticPackCardContent(
        state = state,
        actions =
            SemanticPackActions(
                install = viewModel::install,
                importFiles = {
                    scope.launch {
                        // Off the GTK event-loop thread, as the data screen's other pickers (see pickDatabaseParts)
                        val file =
                            withContext(Dispatchers.IO) { FileKit.openFilePicker(type = FileKitType.File(extensions = IMPORT_EXTENSIONS)) }
                        file?.let { viewModel.import(File(it.path)) }
                    }
                },
                cancel = viewModel::cancel,
                restart = viewModel::restart,
                dismiss = viewModel::dismiss,
            ),
    )
}

/** What the card's buttons do. */
internal class SemanticPackActions(
    val install: () -> Unit,
    val importFiles: () -> Unit,
    val cancel: () -> Unit,
    val restart: () -> Unit,
    val dismiss: () -> Unit,
)

/** The card for [state], its buttons calling [actions]. */
@Composable
internal fun SemanticPackCardContent(
    state: SemanticPackViewModel.State,
    actions: SemanticPackActions,
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
        Text(text = stringResource(Res.string.semantic_pack_title), fontSize = 15.sp)
        Text(
            text = stringResource(Res.string.semantic_pack_description),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        if (state.hasLibrary) {
            PackSection(state, actions)
        } else {
            Text(text = stringResource(Res.string.semantic_pack_no_library), fontSize = 13.sp)
        }
    }
}

@Composable
private fun PackSection(
    state: SemanticPackViewModel.State,
    actions: SemanticPackActions,
) {
    val work = state.work
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = installedText(state.installed), fontSize = 13.sp)
        Text(text = availableText(state.available, state.installed), fontSize = 12.sp, color = JewelTheme.globalColors.text.info)

        when (work) {
            is SemanticPackViewModel.Work.Downloading -> {
                AnimatedHorizontalProgressBar(
                    value = work.read.toFloat() / work.total.coerceAtLeast(1L),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(Res.string.semantic_pack_downloading, formatBytes(work.read), formatBytes(work.total)),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
            }
            is SemanticPackViewModel.Work.Unpacking -> {
                AnimatedHorizontalProgressBar(value = work.progress, modifier = Modifier.fillMaxWidth())
                Text(text = stringResource(Res.string.semantic_pack_unpacking), fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
            }
            SemanticPackViewModel.Work.Ready ->
                InlineSuccessBanner(text = stringResource(Res.string.semantic_pack_ready), modifier = Modifier.fillMaxWidth())
            is SemanticPackViewModel.Work.Failed ->
                InlineErrorBanner(text = problemText(work.problem), modifier = Modifier.fillMaxWidth())
            SemanticPackViewModel.Work.Idle -> Unit
        }
        state.startFailure?.let { failure ->
            InlineInformationBanner(
                text = stringResource(Res.string.semantic_pack_start_failed, failure),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.busy ->
                    OutlinedButton(onClick = actions.cancel) { Text(stringResource(Res.string.semantic_pack_cancel)) }
                work == SemanticPackViewModel.Work.Ready ->
                    DefaultButton(onClick = actions.restart) { Text(stringResource(Res.string.semantic_pack_restart)) }
                else -> {
                    offered(state.available, state.installed)?.let { pack ->
                        DefaultButton(onClick = actions.install) {
                            val label =
                                if (state.installed is InstalledPack.InUse) {
                                    stringResource(Res.string.semantic_pack_update, pack.pack)
                                } else {
                                    stringResource(Res.string.semantic_pack_download)
                                }
                            Text(label)
                        }
                    }
                    OutlinedButton(onClick = actions.importFiles) { Text(stringResource(Res.string.semantic_pack_import)) }
                    if (state.available is SemanticPackViewModel.Available.Unreachable) {
                        OutlinedButton(onClick = { UrlOpener.open(SemanticPackReleases.RELEASES_PAGE) }) {
                            Text(stringResource(Res.string.semantic_pack_releases_page))
                        }
                    }
                    if (work is SemanticPackViewModel.Work.Failed || state.startFailure != null) {
                        OutlinedButton(onClick = actions.dismiss) { Text(stringResource(Res.string.semantic_pack_dismiss)) }
                    }
                }
            }
        }
        if (!state.busy && work != SemanticPackViewModel.Work.Ready) {
            Text(text = stringResource(Res.string.semantic_pack_import_hint), fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
        }
    }
}

/** What the file picker shows: a pack's manifest, its bundle or parts, its zips, or its unzipped model's description. */
private val IMPORT_EXTENSIONS = listOf("json", "zst", "zip") + (1..9).map { "part%02d".format(it) }

/** The newest pack, when it is not the one installed. */
private fun offered(
    available: SemanticPackViewModel.Available,
    installed: InstalledPack,
): SemanticPackReleases.Available? {
    val pack = (available as? SemanticPackViewModel.Available.Pack)?.pack ?: return null
    return pack.takeUnless { installed is InstalledPack.InUse && installed.pack != null && installed.pack >= it.pack }
}

@Composable
private fun installedText(installed: InstalledPack): String =
    when (installed) {
        InstalledPack.None -> stringResource(Res.string.semantic_pack_not_installed)
        is InstalledPack.InUse -> {
            val lines = installed.lines?.let { "%,d".format(it) } ?: "?"
            installed.pack?.let { stringResource(Res.string.semantic_pack_installed, it, lines) }
                ?: stringResource(Res.string.semantic_pack_installed_unnumbered, lines)
        }
        InstalledPack.NoModel -> stringResource(Res.string.semantic_pack_no_model)
        InstalledPack.NoIndex -> stringResource(Res.string.semantic_pack_no_index)
        InstalledPack.OtherModel -> stringResource(Res.string.semantic_pack_other_model)
        is InstalledPack.OtherLibrary ->
            stringResource(Res.string.semantic_pack_other_library, libraryDate(installed.pack), libraryDate(installed.installed))
        is InstalledPack.Unreadable -> stringResource(Res.string.semantic_pack_unreadable, installed.message)
    }

@Composable
private fun availableText(
    available: SemanticPackViewModel.Available,
    installed: InstalledPack,
): String =
    when (available) {
        SemanticPackViewModel.Available.Checking -> stringResource(Res.string.semantic_pack_checking)
        is SemanticPackViewModel.Available.Pack ->
            if (offered(available, installed) == null) {
                stringResource(Res.string.semantic_pack_up_to_date)
            } else {
                stringResource(Res.string.semantic_pack_available, available.pack.pack, formatBytes(available.pack.bundleBytes))
            }
        is SemanticPackViewModel.Available.NoneYet -> stringResource(Res.string.semantic_pack_none_yet, libraryDate(available.library))
        SemanticPackViewModel.Available.Unreachable -> stringResource(Res.string.semantic_pack_unreachable)
        SemanticPackViewModel.Available.UnknownLibrary -> stringResource(Res.string.semantic_pack_unknown_library)
    }

/** Why the pack was not installed, for the user. */
@Composable
private fun problemText(problem: Throwable): String =
    when (problem) {
        is PackProblem.NotAPack -> stringResource(Res.string.semantic_problem_not_a_pack, isolatedPath(problem.name))
        is PackProblem.Missing -> stringResource(Res.string.semantic_problem_missing, isolatedPath(problem.name))
        is PackProblem.Corrupted -> stringResource(Res.string.semantic_problem_corrupted, isolatedPath(problem.name))
        is PackProblem.Unusable -> stringResource(Res.string.semantic_problem_unusable, problem.reason)
        is PackProblem.OtherLibrary ->
            stringResource(Res.string.semantic_problem_other_library, libraryDate(problem.pack), libraryDate(problem.installed))
        is PackProblem.NoSpace ->
            stringResource(
                Res.string.library_problem_no_space,
                isolatedPath(problem.dir),
                formatBytes(problem.needed),
                formatBytes(problem.free),
            )
        else -> stringResource(Res.string.semantic_problem_failed, problem.message ?: problem.toString())
    }

/** A library release (`20261012193827`, `v3-20261012193827`) as its date, 12.10.2026; as written when it is not one. */
internal fun libraryDate(release: String): String {
    val key = SemanticPack.libraryKey(release)
    if (key.length < 8 || !key.take(8).all(Char::isDigit)) return release
    return "${key.substring(6, 8)}.${key.substring(4, 6)}.${key.substring(0, 4)}"
}

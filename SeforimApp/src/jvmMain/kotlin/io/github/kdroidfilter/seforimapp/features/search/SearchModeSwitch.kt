package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchMode
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchModeSettings
import io.github.kdroidfilter.seforimapp.features.search.domain.defaultSearchMode
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_mode_exact
import seforimapp.seforimapp.generated.resources.search_mode_exact_tooltip
import seforimapp.seforimapp.generated.resources.search_mode_general
import seforimapp.seforimapp.generated.resources.search_mode_general_tooltip
import seforimapp.seforimapp.generated.resources.search_mode_hybrid
import seforimapp.seforimapp.generated.resources.search_mode_hybrid_missing
import seforimapp.seforimapp.generated.resources.search_mode_hybrid_tooltip

// Pardes (request 54 of docs/DESKTOP_STAGE3_PLAN.md): the three modes of a search beside the search bar, "מדויק |
// כללי | היברידי", the design the user approved on 2026-11-18 (variant ב: in the bar's row, on the home page and over
// the results).

/**
 * The search modes as three buttons, [selected] marked, [onSelect] given the one chosen, each explained in its tooltip.
 * Hybrid needs the search by meaning: without it, its button is dimmed, its tooltip says where it is installed, and it
 * is not chosen.
 */
@Composable
fun SearchModeSwitch(
    selected: SearchMode,
    onSelect: (SearchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meaning = rememberMeaningInstalled()
    SegmentedControl(
        buttons =
            SearchMode.entries.map { mode ->
                val available = mode != SearchMode.HYBRID || meaning
                SegmentedControlButtonData(
                    selected = mode == selected,
                    content = { ModeLabel(mode, available) },
                    onSelect = { if (available) onSelect(mode) },
                )
            },
        modifier = modifier,
    )
}

/** The mode the next searches take ([SearchModeSettings.last]), chosen before a search: the home page's bar. */
@Composable
fun NextSearchModeSwitch(modifier: Modifier = Modifier) {
    val appSettings = LocalAppGraph.current.appSettings
    val last by appSettings.flow(SearchModeSettings.last).collectAsState()
    val meaning = rememberMeaningInstalled()
    SearchModeSwitch(
        selected = last ?: appSettings.defaultSearchMode(meaning),
        onSelect = { appSettings[SearchModeSettings.last] = it },
        modifier = modifier,
    )
}

// A mode's name, and what it does in its tooltip; dimmed when it is not there
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModeLabel(
    mode: SearchMode,
    available: Boolean,
) {
    val (name, tip) =
        when (mode) {
            SearchMode.EXACT -> Res.string.search_mode_exact to Res.string.search_mode_exact_tooltip
            SearchMode.GENERAL -> Res.string.search_mode_general to Res.string.search_mode_general_tooltip
            SearchMode.HYBRID ->
                Res.string.search_mode_hybrid to
                    (if (available) Res.string.search_mode_hybrid_tooltip else Res.string.search_mode_hybrid_missing)
        }
    Tooltip(tooltip = { Text(stringResource(tip)) }) {
        Text(
            stringResource(name),
            color = if (available) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.disabled,
        )
    }
}

// Whether the search by meaning is installed (its pack in the library's folder), read once off the UI thread
@Composable
private fun rememberMeaningInstalled(): Boolean {
    val appSettings = LocalAppGraph.current.appSettings
    val installed by produceState(true, appSettings) {
        value = withContext(Dispatchers.IO) { InstalledPack.read(LibraryLocation.currentDir(appSettings)) is InstalledPack.InUse }
    }
    return installed
}

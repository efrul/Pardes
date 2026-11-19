package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchMode
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchModeSettings
import io.github.kdroidfilter.seforimapp.features.search.domain.defaultSearchMode
import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.InstalledPack
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.MaterialSymbolsMagicButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ToggleableIconButton
import org.jetbrains.jewel.ui.component.Tooltip
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.find_smart_mode
import seforimapp.seforimapp.generated.resources.search_mode_hybrid_missing

// Pardes (request 54 of docs/DESKTOP_STAGE3_PLAN.md): a search by its words alone or by meaning too, as the user
// decided on 2026-11-19 ("תוריד את מדויק, והעיצוב יתבסס על המצבים הקיימים בחיפוש בתוך ספר"): the in-book find's own
// toggle of its smart find, at the search bar's end.

/**
 * The search by meaning turned on or off ([selected], hybrid or general) as the in-book find turns its smart find:
 * the find bar's toggle and icon, lit when on, its tooltip the same; [onSelect] is given the mode chosen. Without the
 * search by meaning installed, it is off and disabled, and its tooltip says where it is installed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchModeSwitch(
    selected: SearchMode,
    onSelect: (SearchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meaning = rememberMeaningInstalled()
    val on = meaning && selected == SearchMode.HYBRID
    val label = stringResource(if (meaning) Res.string.find_smart_mode else Res.string.search_mode_hybrid_missing)
    Tooltip(tooltip = { Text(label) }, modifier = modifier) {
        ToggleableIconButton(
            value = on,
            onValueChange = { onSelect(if (it) SearchMode.HYBRID else SearchMode.GENERAL) },
            enabled = meaning,
            focusable = false,
        ) {
            Icon(
                imageVector = MaterialSymbolsMagicButton,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = if (on) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.text.info,
            )
        }
    }
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

// Whether the search by meaning is installed (its pack in the library's folder), read once off the UI thread
@Composable
private fun rememberMeaningInstalled(): Boolean {
    val appSettings = LocalAppGraph.current.appSettings
    val installed by produceState(true, appSettings) {
        value = withContext(Dispatchers.IO) { InstalledPack.read(LibraryLocation.currentDir(appSettings)) is InstalledPack.InUse }
    }
    return installed
}

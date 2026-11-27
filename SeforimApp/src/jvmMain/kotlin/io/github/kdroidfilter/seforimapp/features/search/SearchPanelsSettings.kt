package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Contextual_token
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_area_pane
import seforimapp.seforimapp.generated.resources.search_area_pane_tooltip

// Pardes: the area beside a search's results (area/SearchArea.kt), the book, the author, the sage or the topic its query
// names and the sources its first results link to, in a pane of the dock (area/SearchAreaPane.kt), shown or hidden from
// the search's end bar as the reader's panes are from theirs, one setting for every search tab. The user's words on the
// testers' builds: "צריך אפשרות להסתיר את הדברים הללו בתוצאות חיפוש כמו בשאר המקומות", "עדיף פשוט שיהיה את המתג
// בסרגל השמאלי", then "שיוכלו לשלוט בגובה ומיקום חלונית הנושאים".

/** Whether the pane of the area beside a search's results shows, when its search names something or links to sources. */
object SearchPanelsSettings : SettingsGroup("search_panels") {
    val area by boolean(true)
}

/** The search's end bar's button of the area's pane, lit while it is asked for. */
@Composable
fun SearchAreaPaneButton() {
    val appSettings = LocalAppGraph.current.appSettings
    val shown by appSettings.flow(SearchPanelsSettings.area).collectAsState()
    val label = stringResource(Res.string.search_area_pane)
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(Res.string.search_area_pane_tooltip),
        onClick = { appSettings[SearchPanelsSettings.area] = !shown },
        isSelected = shown,
        icon = Contextual_token,
        iconDescription = label,
        label = label,
    )
}

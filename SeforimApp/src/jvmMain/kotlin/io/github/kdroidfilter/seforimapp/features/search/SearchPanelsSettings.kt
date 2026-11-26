package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Manage_search
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_card_entity
import seforimapp.seforimapp.generated.resources.search_card_entity_tooltip

// Pardes: the cards beside a search's results, the book or the author its query names (EntityPanel) and the topic it
// names (SearchTopicCard), shown or hidden from the search's end bar as the reader's panes are from theirs, one setting
// each for every search tab. The user's words on the testers' builds of Sefaria's content: "צריך אפשרות להסתיר את
// הדברים הללו בתוצאות חיפוש כמו בשאר המקומות", then "עדיף פשוט שיהיה את המתג בסרגל השמאלי".

/** Whether the panel of the book or the author a search's query names shows beside its results. */
object SearchPanelsSettings : SettingsGroup("search_panels") {
    val entity by boolean(true)
}

/** The search's end bar's button of the book or author panel beside the results, lit while it shows. */
@Composable
fun SearchEntityPanelButton() {
    val appSettings = LocalAppGraph.current.appSettings
    val shown by appSettings.flow(SearchPanelsSettings.entity).collectAsState()
    val label = stringResource(Res.string.search_card_entity)
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(Res.string.search_card_entity_tooltip),
        onClick = { appSettings[SearchPanelsSettings.entity] = !shown },
        isSelected = shown,
        icon = Manage_search,
        iconDescription = label,
        label = label,
    )
}

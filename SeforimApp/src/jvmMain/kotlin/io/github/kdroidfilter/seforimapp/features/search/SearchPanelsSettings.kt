package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.settings.Setting
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.sefaria.topics.SearchTopicCardSetting
import io.github.kdroidfilter.seforimapp.features.settings.ui.SettingCard
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Link
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_anywhere_keys_hide
import seforimapp.seforimapp.generated.resources.settings_search_entity_panel
import seforimapp.seforimapp.generated.resources.settings_search_entity_panel_description

// Pardes: the cards beside a search's results, the book or the author its query names (EntityPanel) and the topic it
// names (SearchTopicCard), each hidden for good from its own "אל תציג עוד", as the search from anywhere's line of keys
// is (SearchAnywhereSettings), and brought back in Settings › כללי. The user's word on the testers' build of
// Sefaria's content: "צריך אפשרות להסתיר את הדברים הללו בתוצאות חיפוש כמו בשאר המקומות".

/** Whether the panel of the book or the author a search's query names shows beside its results. */
object SearchPanelsSettings : SettingsGroup("search_panels") {
    val entity by boolean(true)
}

/** "אל תציג עוד", at a card's end: [setting] turned off, and the card gone until Settings › כללי brings it back. */
@Composable
internal fun HideForGoodLink(
    setting: Setting<Boolean>,
    modifier: Modifier = Modifier,
) {
    val appSettings = LocalAppGraph.current.appSettings
    Link(
        text = stringResource(Res.string.search_anywhere_keys_hide),
        onClick = { appSettings[setting] = false },
        modifier = modifier,
    )
}

/** Settings › כללי: whether the cards beside a search's results show, for who hid them there; a card each. */
@Composable
internal fun ColumnScope.SearchPanelsCards() {
    val appSettings = LocalAppGraph.current.appSettings
    val entity by appSettings.flow(SearchPanelsSettings.entity).collectAsState()
    SettingCard(
        title = Res.string.settings_search_entity_panel,
        description = Res.string.settings_search_entity_panel_description,
        checked = entity,
        onCheckedChange = { appSettings[SearchPanelsSettings.entity] = it },
    )
    SearchTopicCardSetting()
}

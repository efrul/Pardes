package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.settings.ui.SettingCard
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_anywhere_keys
import seforimapp.seforimapp.generated.resources.search_anywhere_keys_hide
import seforimapp.seforimapp.generated.resources.settings_search_anywhere_keys
import seforimapp.seforimapp.generated.resources.settings_search_anywhere_keys_description

// Pardes (request 96 of docs/DESKTOP_STAGE3_PLAN.md): the line under the search from anywhere that says which keys open
// what it finds where. The user's word on the testers' build: "אפשרות לאל תציג עוד את כללים בחלון החיפוש": the line
// has what hides it for good at its end, and Settings › כללי brings it back.

/** The search from anywhere's settings: whether the line of its [keys] shows under it, until the reader hides it. */
object SearchAnywhereSettings : SettingsGroup("search_anywhere") {
    val keys by boolean(true)
}

/** The line of the bar's keys, with what hides it for good ([onHide] told once it has); nothing once hidden. */
@Composable
internal fun SearchAnywhereKeysLine(
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appSettings = LocalAppGraph.current.appSettings
    val shown by appSettings.flow(SearchAnywhereSettings.keys).collectAsState()
    if (!shown) return
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(
                Res.string.search_anywhere_keys,
                if (PlatformInfo.isMacOS) "⌥" else "Alt",
                if (PlatformInfo.isMacOS) "⌘" else "Ctrl",
            ),
            color = JewelTheme.globalColors.text.disabled,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Link(
            text = stringResource(Res.string.search_anywhere_keys_hide),
            onClick = {
                appSettings[SearchAnywhereSettings.keys] = false
                onHide()
            },
        )
    }
}

/** Settings › כללי: whether the search from anywhere shows the line of its keys, for who hid it there. */
@Composable
internal fun SearchAnywhereKeysCard() {
    val appSettings = LocalAppGraph.current.appSettings
    val shown by appSettings.flow(SearchAnywhereSettings.keys).collectAsState()
    SettingCard(
        title = Res.string.settings_search_anywhere_keys,
        description = Res.string.settings_search_anywhere_keys_description,
        checked = shown,
        onCheckedChange = { appSettings[SearchAnywhereSettings.keys] = it },
    )
}

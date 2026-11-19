package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.tab_duplicate

/** A tab menu's item of Pardes's: its [label], its [icon] and what it does ([onClick]). */
@Immutable
internal data class PardesTabAction(
    val label: String,
    val icon: IconKey,
    val onClick: () -> Unit,
)

/**
 * Pardes's items of the menu of the tab [tabId], after Zayit's own and before the closing ones: a copy of the tab, as
 * Chrome's "Duplicate", and its window's split view, as Chrome's (request 59 of docs/DESKTOP_STAGE3_PLAN.md).
 */
@Composable
internal fun pardesTabActions(tabId: String): List<PardesTabAction> {
    val desktopManager = LocalAppGraph.current.desktopManager
    val openWindow = LocalOpenWindow.current
    return listOf(
        PardesTabAction(stringResource(Res.string.tab_duplicate), AllIconsKeys.Actions.Copy) {
            desktopManager.duplicateTab(tabId, openWindow.id)
        },
    ) + openWindow.splitTabActions(tabId)
}

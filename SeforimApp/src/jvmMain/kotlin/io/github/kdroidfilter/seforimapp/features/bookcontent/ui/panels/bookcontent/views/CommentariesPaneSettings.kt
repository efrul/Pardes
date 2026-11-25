@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.DockSide
import dev.nucleusframework.window.tao.SatellitePlacement
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.LocalPaneSatellite
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph

// Pardes (request 99 of docs/DESKTOP_STAGE3_PLAN.md: "שיוכלו בהגדרות להגדיר בנפרד את מספר העמודות במפרשים בתצוגה
// לאורך ותצוגה לגובה"; and on the testers' build, "עשית את ההגדרה לכמות עמודים במצב מפרשים מאונך?"): the most
// commentators a page of the commentaries pane shows is set apart for a pane that stands at the window's side, tall,
// and for one across it, wide. Zayit's own setting (Settings › תצוגה) stays the wide pane's.

/** The most commentators a page of a commentaries pane at the window's side shows (0: automatic), as Zayit's own for the others. */
object CommentariesPaneSettings : SettingsGroup("commentaries_pane") {
    val tallMaxPerPage by json(0)
}

/** The most commentators a page shows in a pane docked at [side] (none: a floating one): [tall] at a side, else [wide]. */
internal fun maxCommentatorsPerPage(
    side: DockSide?,
    wide: Int,
    tall: Int,
): Int = if (side == DockSide.Left || side == DockSide.Right) tall.coerceIn(0, AppSettings.MAX_COMMENTATORS_PER_PAGE_LIMIT) else wide

/** The most commentators a page shows in the pane this is drawn in, by where the pane is docked. */
@Composable
internal fun rememberMaxCommentatorsPerPage(): Int {
    val appSettings = LocalAppGraph.current.appSettings
    val wide by appSettings.maxCommentatorsPerPageFlow.collectAsState()
    val tall by appSettings.flow(CommentariesPaneSettings.tallMaxPerPage).collectAsState()
    val placement = LocalPaneSatellite.current?.satellite?.placement
    return maxCommentatorsPerPage((placement as? SatellitePlacement.Docked)?.side, wide, tall)
}

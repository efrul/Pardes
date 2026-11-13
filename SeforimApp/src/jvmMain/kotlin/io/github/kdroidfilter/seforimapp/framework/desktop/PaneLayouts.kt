@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.DockSide
import dev.nucleusframework.window.tao.SatelliteLayoutSnapshot
import dev.nucleusframework.window.tao.SatellitePlacement
import dev.nucleusframework.window.tao.WindowAnchor
import dev.nucleusframework.window.tao.WindowPositioner
import kotlinx.serialization.Serializable

/**
 * Where a reader pane was in its window, saved with the window ([PaneLayouts]): docked on [side] (a [DockSide]'s
 * name) at [order], with its own [extent] and [weight], in the side's [outer] band (the full length of the window) or
 * its inner one, or floating ([side] null) where its positioner put it, [width] by [height]. [id] is the pane's own
 * (ReaderPane.id), without the window's: a restored window has a new one.
 */
@Serializable
data class SavedPane(
    val id: String,
    val side: String? = null,
    val order: Int = 0,
    val extent: Float? = null,
    val weight: Float = 1f,
    val parentAnchor: String? = null,
    val childAnchor: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val outer: Boolean = false,
)

/**
 * A reader window's two docks as the user left them (SessionModels' WindowSnapshot.panes): every pane's place, the
 * thickness of each split side (a [DockSide]'s name to dp) and the order of the outer bands (the sides' names, the
 * one that owns the corners first), of the navigation dock and of the one around the text. Which panes are open is
 * the tab's (PaneSync), not kept here.
 */
@Serializable
data class SavedPanes(
    val panes: List<SavedPane> = emptyList(),
    val navigationExtents: Map<String, Float> = emptyMap(),
    val lineExtents: Map<String, Float> = emptyMap(),
    val navigationOuterBands: List<String> = emptyList(),
    val lineOuterBands: List<String> = emptyList(),
) {
    /**
     * The place saved for pane [paneId], when it can be given again: on one of its [sides], or floating if it is
     * [floatable]; null otherwise.
     */
    fun placementOf(
        paneId: String,
        sides: Set<DockSide>,
        floatable: Boolean,
    ): SatellitePlacement? =
        panes.firstOrNull { it.id == paneId }?.let(PaneLayouts::placementOf)?.takeIf { placement ->
            when (placement) {
                is SatellitePlacement.Docked -> placement.side in sides
                is SatellitePlacement.Floating -> floatable
            }
        }

    /** The thickness saved for each split side of a window's [navigation] dock, or of its other one. */
    fun extentsOf(navigation: Boolean): Map<DockSide, Float> =
        (if (navigation) navigationExtents else lineExtents).mapNotNull { (side, dp) -> dockSide(side)?.let { it to dp } }.toMap()

    /**
     * What a window's [navigation] dock, or its other one, gets back before its panes are declared: its sides'
     * thickness and its outer bands' order, as a snapshot naming no pane (SatelliteWorkspace.restore leaves those alone).
     */
    fun dockOf(navigation: Boolean): SatelliteLayoutSnapshot =
        SatelliteLayoutSnapshot(
            satellites = emptyMap(),
            dockExtents = extentsOf(navigation).mapValues { (_, dp) -> dp.dp },
            outerBandOrder = (if (navigation) navigationOuterBands else lineOuterBands).mapNotNull(::dockSide),
        )
}

/**
 * A reader window's pane docks saved with the window and given back when it is restored, as the user asked (request ב
 * of docs/DESKTOP_STAGE2.md, step 3 of the docking): a pane moved to another side or to the full length of the window,
 * sized or pulled out stays so after a restart. Read from the docks' own [SatelliteLayoutSnapshot]s; given back as each pane's first placement
 * (ReaderPanes.WindowPanes) rather than by the workspace's restore, which would also set the panes open or closed:
 * that is the selected tab's, which PaneSync applies.
 */
object PaneLayouts {
    /** The panes of window [groupId] in its [navigation] and [line] docks; null when neither dock is there yet. */
    fun save(
        groupId: String,
        navigation: SatelliteLayoutSnapshot?,
        line: SatelliteLayoutSnapshot?,
    ): SavedPanes? {
        if (navigation == null && line == null) return null
        val prefix = "$groupId-"
        val panes =
            listOfNotNull(navigation, line).flatMap { dock ->
                dock.satellites.mapNotNull { (id, saved) ->
                    if (id.startsWith(prefix)) savedPane(id.removePrefix(prefix), saved.placement) else null
                }
            }
        return SavedPanes(
            panes = panes.sortedBy { it.id },
            navigationExtents = extentsOf(navigation),
            lineExtents = extentsOf(line),
            navigationOuterBands = navigation?.outerBandOrder.orEmpty().map { it.name },
            lineOuterBands = line?.outerBandOrder.orEmpty().map { it.name },
        )
    }

    // A dock's split sides' thickness, by side name, in dp
    private fun extentsOf(dock: SatelliteLayoutSnapshot?): Map<String, Float> =
        dock
            ?.dockExtents
            .orEmpty()
            .entries
            .associate { (side, extent) -> side.name to extent.value }

    /** [saved]'s placement again; null when its side or anchors are no longer known. */
    fun placementOf(saved: SavedPane): SatellitePlacement? =
        when (val side = saved.side) {
            null -> floatingOf(saved)
            else -> dockSide(side)?.let { SatellitePlacement.Docked(it, saved.order, saved.extent?.dp, saved.weight, saved.outer) }
        }

    // A floating pane's placement again: its anchors known, and its size more than nothing
    private fun floatingOf(saved: SavedPane): SatellitePlacement.Floating? {
        val parent = anchor(saved.parentAnchor) ?: return null
        val child = anchor(saved.childAnchor) ?: return null
        if (saved.width <= 0f || saved.height <= 0f) return null
        return SatellitePlacement.Floating(
            positioner = WindowPositioner(parentAnchor = parent, childAnchor = child, offset = DpOffset(saved.x.dp, saved.y.dp)),
            size = DpSize(saved.width.dp, saved.height.dp),
        )
    }

    private fun savedPane(
        id: String,
        placement: SatellitePlacement,
    ): SavedPane? =
        when (placement) {
            is SatellitePlacement.Docked ->
                SavedPane(id, placement.side.name, placement.order, placement.extent?.value, placement.weight, outer = placement.outer)
            // A pane floating where it was anchored to a part of the window only would land elsewhere: not kept
            is SatellitePlacement.Floating ->
                placement.takeIf { it.anchorRect == null }?.let {
                    val positioner = it.positioner
                    SavedPane(
                        id = id,
                        parentAnchor = positioner.parentAnchor.name,
                        childAnchor = positioner.childAnchor.name,
                        x = positioner.offset.x.value,
                        y = positioner.offset.y.value,
                        width = it.size.width.value,
                        height = it.size.height.value,
                    )
                }
        }

    private fun anchor(name: String?): WindowAnchor? = WindowAnchor.entries.firstOrNull { it.name == name }
}

private fun dockSide(name: String): DockSide? = DockSide.entries.firstOrNull { it.name == name }

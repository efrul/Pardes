@file:OptIn(ExperimentalNucleusApi::class, ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.DockSide
import dev.nucleusframework.window.tao.SatelliteLayoutSnapshot
import dev.nucleusframework.window.tao.SatellitePlacement
import dev.nucleusframework.window.tao.SatelliteSnapshot
import dev.nucleusframework.window.tao.WindowAnchor
import dev.nucleusframework.window.tao.WindowPositioner
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A reader window's docks saved with the window and given back to its panes when it is restored. */
class PaneLayoutsTest {
    private val floating =
        SatellitePlacement.Floating(
            positioner = WindowPositioner(WindowAnchor.TopLeft, WindowAnchor.TopLeft, DpOffset(120.dp, 80.dp)),
            size = DpSize(420.dp, 300.dp),
        )

    private val line =
        SatelliteLayoutSnapshot(
            satellites =
                mapOf(
                    "w-1-comments" to SatelliteSnapshot(SatellitePlacement.Docked(DockSide.Left, 2, 260.dp, 1.5f), isOpen = true),
                    "w-1-targum" to SatelliteSnapshot(floating, isOpen = false),
                    // Another window's pane: not this one's
                    "w-2-sources" to SatelliteSnapshot(SatellitePlacement.Docked(DockSide.Bottom, 0, null, 1f), isOpen = true),
                ),
            dockExtents = mapOf(DockSide.Bottom to 180.dp),
        )

    private val navigation =
        SatelliteLayoutSnapshot(
            satellites = mapOf("w-1-notes" to SatelliteSnapshot(SatellitePlacement.Docked(DockSide.Right, 1, null, 1f), isOpen = false)),
            dockExtents = emptyMap(),
        )

    @Test
    fun `a window's panes are saved by their own ids, docked or floating, with its split sides`() {
        val saved = PaneLayouts.save("w-1", navigation, line)!!

        assertEquals(listOf("comments", "notes", "targum"), saved.panes.map { it.id })
        assertEquals(SatellitePlacement.Docked(DockSide.Left, 2, 260.dp, 1.5f), saved.placementOf("comments", LINE_SIDES, floatable = true))
        assertEquals(floating, saved.placementOf("targum", LINE_SIDES, floatable = true))
        val notes = saved.placementOf("notes", setOf(DockSide.Right), floatable = true)
        assertEquals(SatellitePlacement.Docked(DockSide.Right, 1, null, 1f), notes)
        assertEquals(mapOf(DockSide.Bottom to 180f), saved.extentsOf(navigation = false))
        assertEquals(emptyMap(), saved.extentsOf(navigation = true))
        assertNull(PaneLayouts.save("w-1", null, null))
    }

    @Test
    fun `a place a pane can no longer have is not given back`() {
        val saved = PaneLayouts.save("w-1", navigation, line)!!

        // A side the pane may not dock on, a pane that may not float, a pane never saved
        assertNull(saved.placementOf("comments", setOf(DockSide.Bottom), floatable = true))
        assertNull(saved.placementOf("targum", LINE_SIDES, floatable = false))
        assertNull(saved.placementOf("sources", LINE_SIDES, floatable = true))
        // Names this version does not know
        assertNull(PaneLayouts.placementOf(SavedPane("x", side = "Middle")))
        assertNull(PaneLayouts.placementOf(SavedPane("x", parentAnchor = "TopLeft", childAnchor = "Somewhere", width = 10f, height = 10f)))
    }

    @Test
    fun `a pane floating on part of the window only is not saved`() {
        val anchored = floating.copy(anchorRect = DpRect(0.dp, 0.dp, 100.dp, 100.dp))
        val dock = SatelliteLayoutSnapshot(mapOf("w-1-targum" to SatelliteSnapshot(anchored, isOpen = true)), emptyMap())

        assertEquals(emptyList(), PaneLayouts.save("w-1", null, dock)!!.panes)
    }

    @Test
    fun `a pane at the window's full length keeps its band, and the dock its outer bands' order`() {
        val links = SatellitePlacement.Docked(DockSide.Left, 0, 220.dp, 1f, outer = true)
        val dock =
            SatelliteLayoutSnapshot(
                satellites = mapOf("w-1-links" to SatelliteSnapshot(links, isOpen = true)),
                dockExtents = mapOf(DockSide.Bottom to 180.dp),
                outerBandOrder = listOf(DockSide.Left, DockSide.Bottom),
            )
        val saved = PaneLayouts.save("w-1", null, dock)!!

        assertEquals(links, saved.placementOf("links", LINE_SIDES, floatable = true))
        assertEquals(
            SatelliteLayoutSnapshot(emptyMap(), mapOf(DockSide.Bottom to 180.dp), listOf(DockSide.Left, DockSide.Bottom)),
            saved.dockOf(navigation = false),
        )
        assertEquals(SatelliteLayoutSnapshot(emptyMap(), emptyMap(), emptyList()), saved.dockOf(navigation = true))
        // A side name this version does not know is left out
        assertEquals(listOf(DockSide.Left), SavedPanes(lineOuterBands = listOf("Middle", "Left")).dockOf(navigation = false).outerBandOrder)
        // In the session file; a file written before the outer bands has none
        assertEquals(saved, ProtoBuf.decodeFromByteArray<SavedPanes>(ProtoBuf.encodeToByteArray(saved)))
        val olderFile = ProtoBuf.encodeToByteArray(OlderSavedPanes(listOf(OlderSavedPane("links", "Left"))))
        val older = ProtoBuf.decodeFromByteArray<SavedPanes>(olderFile)
        assertEquals(SatellitePlacement.Docked(DockSide.Left), older.placementOf("links", LINE_SIDES, floatable = true))
        assertEquals(emptyList(), older.lineOuterBands)
    }

    @Test
    fun `the docks go into the session file, and an older file has none`() {
        val window = WindowSnapshot(selectedIndex = 1, panes = PaneLayouts.save("w-1", navigation, line))

        assertEquals(window, ProtoBuf.decodeFromByteArray<WindowSnapshot>(ProtoBuf.encodeToByteArray(window)))
        val older = ProtoBuf.encodeToByteArray(OlderWindowSnapshot(selectedIndex = 1))
        assertNull(ProtoBuf.decodeFromByteArray<WindowSnapshot>(older).panes)
    }

    // A window as the session file had it before the docks were saved: its first four fields
    @Serializable
    private data class OlderWindowSnapshot(
        val destinations: List<String> = emptyList(),
        val selectedIndex: Int = 0,
        val titles: Map<String, String> = emptyMap(),
        val geometry: String? = null,
    )

    // A pane and the docks as the session file had them before the outer bands
    @Serializable
    private data class OlderSavedPane(
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
    )

    @Serializable
    private data class OlderSavedPanes(
        val panes: List<OlderSavedPane> = emptyList(),
        val navigationExtents: Map<String, Float> = emptyMap(),
        val lineExtents: Map<String, Float> = emptyMap(),
    )

    private companion object {
        val LINE_SIDES = setOf(DockSide.Left, DockSide.Right, DockSide.Bottom)
    }
}

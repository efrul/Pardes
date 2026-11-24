@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.DockSide
import kotlin.test.Test
import kotlin.test.assertEquals

/** The most commentators a page of the commentaries pane shows, by where the pane is docked. */
class CommentariesPaneSettingsTest {
    @Test
    fun `a pane at the window's side takes its own number, the others Zayit's`() {
        assertEquals(2, maxCommentatorsPerPage(DockSide.Left, wide = 4, tall = 2))
        assertEquals(2, maxCommentatorsPerPage(DockSide.Right, wide = 4, tall = 2))
        assertEquals(4, maxCommentatorsPerPage(DockSide.Bottom, wide = 4, tall = 2))
        assertEquals(4, maxCommentatorsPerPage(DockSide.Top, wide = 4, tall = 2))
        assertEquals(4, maxCommentatorsPerPage(null, wide = 4, tall = 2), "a floating pane")
        assertEquals(0, maxCommentatorsPerPage(DockSide.Left, wide = 4, tall = 0), "automatic at the side")
    }
}

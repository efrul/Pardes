package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.ui.state.ToggleableState
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentatorsCheckStateTest {
    private val group = listOf("רש\"י", "רשב\"ם", "אבן עזרא")

    @Test
    fun `a group is checked when all its commentators are`() {
        assertEquals(ToggleableState.On, commentatorsCheckState(group, group.toSet() + "רמב\"ן"))
    }

    @Test
    fun `a group is half checked when some of its commentators are`() {
        assertEquals(ToggleableState.Indeterminate, commentatorsCheckState(group, setOf("רש\"י")))
    }

    @Test
    fun `a group is unchecked when none of its commentators is, or it has none`() {
        assertEquals(ToggleableState.Off, commentatorsCheckState(group, setOf("רמב\"ן")))
        assertEquals(ToggleableState.Off, commentatorsCheckState(emptyList(), setOf("רש\"י")))
    }
}

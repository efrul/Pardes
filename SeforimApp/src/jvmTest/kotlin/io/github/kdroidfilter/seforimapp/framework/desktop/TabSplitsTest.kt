package io.github.kdroidfilter.seforimapp.framework.desktop

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Two tabs of a window side by side (request 59): when the pair is shown, each one's half, and the session's split. */
class TabSplitsTest {
    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun `a pair is shown while its two tabs stand next to each other`() {
        val splits = TabSplits()
        splits.split("w", "c", "b")
        assertEquals(TabSplit("c", "b"), splits.of("w", ids))
        assertNull(splits.of("w", listOf("b", "a", "c")), "moved apart")
        assertNull(splits.of("w", listOf("a", "b")), "one closed or moved away")
        assertNull(splits.of("other", ids))
        splits.end("w")
        assertNull(splits.of("w", ids))
    }

    @Test
    fun `a tab with itself is no pair`() {
        val splits = TabSplits()
        splits.split("w", "a", "a")
        assertNull(splits.saved("w"))
    }

    @Test
    fun `the window's earlier tab on the left, as the strip lays it out, whichever of the two is selected`() {
        val split = TabSplit("b", "c", ratio = 0.3f)
        assertEquals(SplitHalf(left = true, ratio = 0.3f), splitHalf(split, ids, "b", "b"))
        assertEquals(SplitHalf(left = false, ratio = 0.3f), splitHalf(split, ids, "b", "c"))
        assertEquals(SplitHalf(left = true, ratio = 0.3f), splitHalf(split, ids, "c", "b"))
        assertEquals(SplitHalf(left = true, ratio = 0.3f), splitHalf(TabSplit("c", "b", 0.3f), ids, "c", "b"))
    }

    @Test
    fun `no half while another tab is selected, nor for a tab outside the pair`() {
        val split = TabSplit("b", "c")
        assertNull(splitHalf(split, ids, "a", "b"))
        assertNull(splitHalf(split, ids, "b", "d"))
        assertNull(splitHalf(null, ids, "b", "b"))
    }

    @Test
    fun `the split moves between a quarter and three quarters`() {
        val splits = TabSplits()
        splits.split("w", "a", "b")
        splits.setRatio("w", 0.9f)
        assertEquals(0.75f, splits.saved("w")?.ratio)
        splits.setRatio("w", 0.1f)
        assertEquals(0.25f, splits.saved("w")?.ratio)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `a window keeps its split in the session, and one saved before it reads with none`() {
        val window =
            WindowSnapshot(
                destinations = listOf(TabsDestination.Home("a"), TabsDestination.Home("b")),
                split = TabSplit("a", "b", ratio = 0.4f),
            )
        assertEquals(window, ProtoBuf.decodeFromByteArray<WindowSnapshot>(ProtoBuf.encodeToByteArray(window)))
        val before = WindowSnapshot(destinations = listOf(TabsDestination.Home("a")))
        assertNull(ProtoBuf.decodeFromByteArray<WindowSnapshot>(ProtoBuf.encodeToByteArray(before)).split)
    }
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.ui.input.key.Key
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.NavigationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The keys of the book tree and of the contents (request 47): as code editors' trees have them, right to left. */
class TreeKeysTest {
    // תנ"ך (open) › תורה (open) › בראשית, שמות; נביאים (closed); משנה (open, empty)
    private val tree =
        listOf(
            TreeNode(0, folder = true, open = true),
            TreeNode(1, folder = true, open = true),
            TreeNode(2),
            TreeNode(2),
            TreeNode(1, folder = true, open = false),
            TreeNode(0, folder = true, open = true),
        )

    private fun move(
        focused: Int,
        key: TreeKey,
        page: Int = 3,
    ) = treeMove(tree, focused, key, page)

    @Test
    fun `right to left, the arrow left goes into a folder and the arrow right out of it`() {
        assertEquals(TreeKey.IN, treeKeyOf(Key.DirectionLeft, rtl = true))
        assertEquals(TreeKey.OUT, treeKeyOf(Key.DirectionRight, rtl = true))
        assertEquals(TreeKey.IN, treeKeyOf(Key.DirectionRight, rtl = false))
        assertEquals(TreeKey.OUT, treeKeyOf(Key.DirectionLeft, rtl = false))
        assertNull(treeKeyOf(Key.A, rtl = true))
    }

    @Test
    fun `up and down a row, the ends, and a page, kept inside the tree`() {
        assertEquals(TreeMove.Focus(3), move(2, TreeKey.DOWN))
        assertEquals(TreeMove.Focus(1), move(2, TreeKey.UP))
        assertEquals(TreeMove.Focus(0), move(0, TreeKey.UP))
        assertEquals(TreeMove.Focus(5), move(5, TreeKey.DOWN))
        assertEquals(TreeMove.Focus(0), move(4, TreeKey.HOME))
        assertEquals(TreeMove.Focus(5), move(1, TreeKey.END))
        assertEquals(TreeMove.Focus(4), move(1, TreeKey.PAGE_DOWN))
        assertEquals(TreeMove.Focus(5), move(4, TreeKey.PAGE_DOWN))
        assertEquals(TreeMove.Focus(0), move(2, TreeKey.PAGE_UP))
    }

    @Test
    fun `before any row, the first, or the last for End`() {
        assertEquals(TreeMove.Focus(0), move(-1, TreeKey.DOWN))
        assertEquals(TreeMove.Focus(0), move(-1, TreeKey.IN))
        assertEquals(TreeMove.Focus(5), move(-1, TreeKey.END))
        assertNull(treeMove(emptyList(), -1, TreeKey.DOWN, 3))
    }

    @Test
    fun `in opens a closed folder, goes into an open one, and does nothing on a leaf or an empty folder`() {
        assertEquals(TreeMove.Click(4), move(4, TreeKey.IN))
        assertEquals(TreeMove.Focus(1), move(0, TreeKey.IN))
        assertNull(move(2, TreeKey.IN))
        assertNull(move(5, TreeKey.IN))
    }

    @Test
    fun `out closes an open folder, else goes up to the parent, and does nothing at the top`() {
        assertEquals(TreeMove.Click(1), move(1, TreeKey.OUT))
        assertEquals(TreeMove.Focus(1), move(3, TreeKey.OUT))
        assertEquals(TreeMove.Focus(0), move(4, TreeKey.OUT))
        assertEquals(TreeMove.Click(0), move(0, TreeKey.OUT))
        assertNull(treeMove(listOf(TreeNode(0)), 0, TreeKey.OUT, 3))
    }

    @Test
    fun `Enter clicks the row, a folder as a leaf`() {
        assertEquals(TreeMove.Click(2), move(2, TreeKey.ENTER))
        assertEquals(TreeMove.Click(4), move(4, TreeKey.ENTER))
    }

    @Test
    fun `a list takes Home, End and the page keys alone`() {
        assertEquals(0, listMove(10, 6, Key.MoveHome, 4))
        assertEquals(9, listMove(10, 6, Key.MoveEnd, 4))
        assertEquals(9, listMove(10, 6, Key.PageDown, 4))
        assertEquals(2, listMove(10, 6, Key.PageUp, 4))
        assertNull(listMove(10, 6, Key.DirectionDown, 4))
        assertNull(listMove(0, 0, Key.MoveEnd, 4))
    }

    @Test
    fun `the book tree's row selected, a book before its category, an override before the selection`() {
        val navigation = NavigationState()
        assertEquals("book_7", navigation.treeSelectedKey(bookOverride = 7, categoryOverride = 3))
        assertEquals("category_3", navigation.treeSelectedKey(categoryOverride = 3))
        assertNull(navigation.treeSelectedKey())
    }
}

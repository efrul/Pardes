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
    fun `in opens a closed folder, and from an open one or a leaf goes on to the next row, as IntelliJ's trees`() {
        assertEquals(TreeMove.Click(4), move(4, TreeKey.IN))
        assertEquals(TreeMove.Focus(1), move(0, TreeKey.IN))
        assertEquals(TreeMove.Focus(3), move(2, TreeKey.IN), "a leaf: the next row")
        assertEquals(TreeMove.Focus(4), move(3, TreeKey.IN), "the last leaf of a folder: the row after it")
        assertNull(move(5, TreeKey.IN), "the last row")
    }

    @Test
    fun `out closes an open folder, else goes up to the parent`() {
        assertEquals(TreeMove.Click(1), move(1, TreeKey.OUT))
        assertEquals(TreeMove.Focus(1), move(3, TreeKey.OUT))
        assertEquals(TreeMove.Focus(0), move(4, TreeKey.OUT))
        assertEquals(TreeMove.Click(0), move(0, TreeKey.OUT))
        assertNull(treeMove(listOf(TreeNode(0)), 0, TreeKey.OUT, 3))
    }

    @Test
    fun `out on a closed row at the top goes to the nearest open folder above it, else to the first row, as IntelliJ's`() {
        // תנ"ך (open) › תורה (open) › בראשית; נביאים (closed); כתובים (closed)
        val top =
            listOf(
                TreeNode(0, folder = true, open = true),
                TreeNode(1, folder = true, open = true),
                TreeNode(2),
                TreeNode(0, folder = true, open = false),
                TreeNode(0, folder = true, open = false),
            )
        assertEquals(TreeMove.Focus(1), treeMove(top, 4, TreeKey.OUT, 3), "the nearest open folder, תורה")
        assertEquals(TreeMove.Focus(1), treeMove(top, 3, TreeKey.OUT, 3))
        val closed = List(3) { TreeNode(0, folder = true, open = false) }
        assertEquals(TreeMove.Focus(0), treeMove(closed, 2, TreeKey.OUT, 3), "none open: the first row")
        assertNull(treeMove(closed, 0, TreeKey.OUT, 3))
    }

    @Test
    fun `the keypad's plus opens a closed folder and its minus closes an open one, as IntelliJ's trees`() {
        assertEquals(TreeKey.EXPAND, treeKeyOf(Key.NumPadAdd, rtl = true))
        assertEquals(TreeKey.COLLAPSE, treeKeyOf(Key.NumPadSubtract, rtl = true))
        assertEquals(TreeMove.Click(4), move(4, TreeKey.EXPAND))
        assertNull(move(1, TreeKey.EXPAND), "open already")
        assertEquals(TreeMove.Click(1), move(1, TreeKey.COLLAPSE))
        assertNull(move(4, TreeKey.COLLAPSE), "closed already")
        assertNull(move(2, TreeKey.COLLAPSE), "a leaf: not to its parent, as the arrow does")
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

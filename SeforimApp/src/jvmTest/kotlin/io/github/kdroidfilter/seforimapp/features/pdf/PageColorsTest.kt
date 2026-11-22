package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** A page drawn as an image takes the dark theme's text as its ink and its panel as its paper (request 42). */
class PageColorsTest {
    private val ink = Color(0xFFBCBEC4)
    private val paper = Color(0xFF1E1F22)

    // A channel of a gray [c] (0 to 255) through the row [row] of the matrix, as Skia draws it
    private fun through(
        row: Int,
        c: Float,
    ): Float {
        val values = inkOnPaper(ink, paper).values
        return values[row * 5] * c + values[row * 5 + 1] * c + values[row * 5 + 2] * c + values[row * 5 + 4]
    }

    @Test
    fun `black is drawn as the theme's text, white as its panel`() {
        assertEquals(ink.red * 255, through(0, 0f), 0.01f)
        assertEquals(ink.blue * 255, through(2, 0f), 0.01f)
        assertEquals(paper.red * 255, through(0, 255f), 0.01f)
        assertEquals(paper.green * 255, through(1, 255f), 0.01f)
    }

    @Test
    fun `a gray between them falls between them, and the alpha stays`() {
        val half = through(0, 127.5f)

        assertEquals((ink.red + paper.red) / 2 * 255, half, 0.01f)
        val alpha = inkOnPaper(ink, paper).values.copyOfRange(15, 20).toList()
        assertEquals(listOf(0f, 0f, 0f, 1f, 0f), alpha)
    }
}

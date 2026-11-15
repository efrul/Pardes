package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Two pages side by side, as a book open: their frames, and the lines on each. */
val PageSpread: ImageVector
    get() {
        if (_PageSpread != null) return _PageSpread!!

        _PageSpread =
            ImageVector
                .Builder(
                    name = "PageSpread",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        // The two pages' frames, each a ring
                        frame(0.5f)
                        frame(8.5f)
                        // The lines on each page
                        for (top in PAGE_LINES) {
                            bar(2.5f, top)
                            bar(10.5f, top)
                        }
                    }
                }.build()

        return _PageSpread!!
    }

private val PAGE_LINES = floatArrayOf(5f, 7.25f, 9.5f)

private const val PAGE_WIDTH = 7f
private const val PAGE_TOP = 2f
private const val PAGE_HEIGHT = 12f
private const val THICKNESS = 1.25f
private const val LINE_WIDTH = 3f
private const val LINE_HEIGHT = 1.25f

// A page's frame from [left]: its outline clockwise, its inside counterclockwise, a ring
private fun PathBuilder.frame(left: Float) {
    moveTo(left, PAGE_TOP)
    horizontalLineToRelative(PAGE_WIDTH)
    verticalLineToRelative(PAGE_HEIGHT)
    horizontalLineToRelative(-PAGE_WIDTH)
    close()
    moveTo(left + THICKNESS, PAGE_TOP + THICKNESS)
    verticalLineToRelative(PAGE_HEIGHT - THICKNESS * 2)
    horizontalLineToRelative(PAGE_WIDTH - THICKNESS * 2)
    verticalLineToRelative(-(PAGE_HEIGHT - THICKNESS * 2))
    close()
}

private fun PathBuilder.bar(
    left: Float,
    top: Float,
) {
    moveTo(left, top)
    horizontalLineToRelative(LINE_WIDTH)
    verticalLineToRelative(LINE_HEIGHT)
    horizontalLineToRelative(-LINE_WIDTH)
    close()
}

private var _PageSpread: ImageVector? = null

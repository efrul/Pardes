package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A whole page within the view's corners, as a page fitted in it: its frame, the lines on it, and the four corners. */
val PageFit: ImageVector
    get() {
        if (_PageFit != null) return _PageFit!!

        _PageFit =
            ImageVector
                .Builder(
                    name = "PageFit",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        // The page's frame, a ring, as PageSpread's
                        moveTo(PAGE_LEFT, PAGE_TOP)
                        horizontalLineToRelative(PAGE_WIDTH)
                        verticalLineToRelative(PAGE_HEIGHT)
                        horizontalLineToRelative(-PAGE_WIDTH)
                        close()
                        moveTo(PAGE_LEFT + THICKNESS, PAGE_TOP + THICKNESS)
                        verticalLineToRelative(PAGE_HEIGHT - THICKNESS * 2)
                        horizontalLineToRelative(PAGE_WIDTH - THICKNESS * 2)
                        verticalLineToRelative(-(PAGE_HEIGHT - THICKNESS * 2))
                        close()
                        // The lines on it
                        for (top in PAGE_LINES) bar(PAGE_LEFT + 2f, top)
                        // The view's corners around it
                        corner(0.5f, 0.5f, 1f, 1f)
                        corner(15.5f, 0.5f, -1f, 1f)
                        corner(0.5f, 15.5f, 1f, -1f)
                        corner(15.5f, 15.5f, -1f, -1f)
                    }
                }.build()

        return _PageFit!!
    }

private val PAGE_LINES = floatArrayOf(6f, 7.75f, 9.5f)

private const val PAGE_LEFT = 4.5f
private const val PAGE_TOP = 3.5f
private const val PAGE_WIDTH = 7f
private const val PAGE_HEIGHT = 9f
private const val THICKNESS = 1.25f
private const val LINE_WIDTH = 3f
private const val LINE_HEIGHT = 1.25f
private const val CORNER = 3f

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

// A corner at ([x], [y]), its two arms going towards [dx] and [dy] (1 or -1)
private fun PathBuilder.corner(
    x: Float,
    y: Float,
    dx: Float,
    dy: Float,
) {
    moveTo(x, y)
    horizontalLineToRelative(CORNER * dx)
    verticalLineToRelative(THICKNESS * dy)
    horizontalLineToRelative(-(CORNER - THICKNESS) * dx)
    verticalLineToRelative((CORNER - THICKNESS) * dy)
    horizontalLineToRelative(-THICKNESS * dx)
    close()
}

private var _PageFit: ImageVector? = null

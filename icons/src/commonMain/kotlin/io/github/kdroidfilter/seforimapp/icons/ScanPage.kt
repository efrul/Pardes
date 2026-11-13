package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A page in a scanner's frame: its four corners, and the page's lines inside them. */
val ScanPage: ImageVector
    get() {
        if (_ScanPage != null) return _ScanPage!!

        _ScanPage =
            ImageVector
                .Builder(
                    name = "ScanPage",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        // The frame's corners, each two arms from its point
                        corner(1f, 1f, 1f, 1f)
                        corner(15f, 1f, -1f, 1f)
                        corner(1f, 15f, 1f, -1f)
                        corner(15f, 15f, -1f, -1f)
                        // The page's lines
                        for (top in PAGE_LINES) line(5f, top, 6f, 1.25f)
                    }
                }.build()

        return _ScanPage!!
    }

private val PAGE_LINES = floatArrayOf(5.25f, 7.375f, 9.5f)

private const val ARM = 4f
private const val THICKNESS = 1.5f

// A corner at [x], [y], its arms going [dx] across and [dy] down (each 1 or -1)
private fun PathBuilder.corner(
    x: Float,
    y: Float,
    dx: Float,
    dy: Float,
) {
    moveTo(x, y)
    horizontalLineToRelative(dx * ARM)
    verticalLineToRelative(dy * THICKNESS)
    horizontalLineToRelative(-dx * (ARM - THICKNESS))
    verticalLineToRelative(dy * (ARM - THICKNESS))
    horizontalLineToRelative(-dx * THICKNESS)
    close()
}

private fun PathBuilder.line(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
) {
    moveTo(left, top)
    horizontalLineToRelative(width)
    verticalLineToRelative(height)
    horizontalLineToRelative(-width)
    close()
}

private var _ScanPage: ImageVector? = null

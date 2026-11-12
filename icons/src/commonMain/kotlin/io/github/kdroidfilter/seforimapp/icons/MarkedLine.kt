package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A page's lines, the middle one marked: a frame around it. */
val MarkedLine: ImageVector
    get() {
        if (_MarkedLine != null) return _MarkedLine!!

        _MarkedLine =
            ImageVector
                .Builder(
                    name = "MarkedLine",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.EvenOdd,
                    ) {
                        // The lines above and below
                        frame(2f, 1.75f, 12f, 1.5f, 0f)
                        frame(2f, 12.75f, 12f, 1.5f, 0f)
                        // The mark: a rounded frame, its inside left open, around the middle line
                        frame(0.75f, 5f, 14.5f, 6f, 1.5f)
                        frame(2f, 6.25f, 12f, 3.5f, 0.6f)
                        frame(3.75f, 7.25f, 8.5f, 1.5f, 0f)
                    }
                }.build()

        return _MarkedLine!!
    }

/** A rectangle with its corners rounded by [radius], drawn clockwise. */
private fun PathBuilder.frame(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
) {
    moveTo(left + radius, top)
    horizontalLineToRelative(width - 2 * radius)
    arcToRelative(radius, radius, 0f, false, true, radius, radius)
    verticalLineToRelative(height - 2 * radius)
    arcToRelative(radius, radius, 0f, false, true, -radius, radius)
    horizontalLineToRelative(2 * radius - width)
    arcToRelative(radius, radius, 0f, false, true, -radius, -radius)
    verticalLineToRelative(2 * radius - height)
    arcToRelative(radius, radius, 0f, false, true, radius, -radius)
    close()
}

private var _MarkedLine: ImageVector? = null

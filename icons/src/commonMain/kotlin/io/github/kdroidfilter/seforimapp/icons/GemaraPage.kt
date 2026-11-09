package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A page of the Talmud as printed: the Gemara's lines in the middle, and its two commentaries, each a column beside
 * them that turns over them at the top, where the two meet.
 */
val GemaraPage: ImageVector
    get() {
        if (_GemaraPage != null) return _GemaraPage!!

        _GemaraPage =
            ImageVector
                .Builder(
                    name = "GemaraPage",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        // The commentary on the left, down its column from its lines above the Gemara
                        moveTo(1f, 1f)
                        horizontalLineTo(7.25f)
                        verticalLineTo(2.5f)
                        horizontalLineTo(2.5f)
                        verticalLineTo(15f)
                        horizontalLineTo(1f)
                        close()
                        // The one on the right
                        moveTo(8.75f, 1f)
                        horizontalLineTo(15f)
                        verticalLineTo(15f)
                        horizontalLineTo(13.5f)
                        verticalLineTo(2.5f)
                        horizontalLineTo(8.75f)
                        close()
                        // The Gemara's lines
                        for (top in GEMARA_LINES) line(4.5f, top, 7f, 1.5f)
                    }
                }.build()

        return _GemaraPage!!
    }

private val GEMARA_LINES = floatArrayOf(5f, 8.25f, 11.5f)

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

private var _GemaraPage: ImageVector? = null

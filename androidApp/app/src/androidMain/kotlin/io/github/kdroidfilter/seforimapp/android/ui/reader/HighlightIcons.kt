package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Desktop 0.6.10 SeforimApp/src/jvmMain/resources/icons/highlighter.svg and eraser.svg (0.6.8 c33a13ee): Zayit's own
// icons in the Jewel new-UI style (16 px grid, 1 px stroke), the marker beside "highlight" and the swatch that clears.
// The same paths, drawn in black for Icon's tint.

private fun ImageVector.Builder.stroke(
    path: String,
    cap: StrokeCap = StrokeCap.Butt,
    join: StrokeJoin = StrokeJoin.Miter,
) = addPath(pathData = addPathNodes(path), stroke = SolidColor(Color.Black), strokeLineWidth = 1f, strokeLineCap = cap, strokeLineJoin = join)

/** A highlighter marker, tilted 45° over the line it draws. */
val Highlighter: ImageVector by lazy {
    ImageVector.Builder(name = "Highlighter", defaultWidth = 16.dp, defaultHeight = 16.dp, viewportWidth = 16f, viewportHeight = 16f)
        .apply {
            addGroup(rotate = -45f, pivotX = 8.5f, pivotY = 7.5f)
            // <rect x="7.5" y="4.5" width="7" height="6" rx="1.5"/>
            stroke("M9 4.5H13A1.5 1.5 0 0 1 14.5 6V9A1.5 1.5 0 0 1 13 10.5H9A1.5 1.5 0 0 1 7.5 9V6A1.5 1.5 0 0 1 9 4.5Z")
            stroke("M7.5 6H5.5V9H7.5", join = StrokeJoin.Round)
            stroke("M5.5 6L2.5 7.5V9H5.5", join = StrokeJoin.Round)
            clearGroup()
            stroke("M1.5 14.5H9.5", cap = StrokeCap.Round)
        }.build()
}

/** An eraser: clears a highlight. */
val Eraser: ImageVector by lazy {
    ImageVector.Builder(name = "Eraser", defaultWidth = 16.dp, defaultHeight = 16.dp, viewportWidth = 16f, viewportHeight = 16f)
        .apply {
            stroke(
                "M4.5 14.5L1.8 11.8C1.13 11.13 1.13 10.13 1.8 9.53L8.2 3.13C8.87 2.47 9.87 2.47 10.47 3.13L14.2 6.87C14.87 7.53 14.87 8.53 14.2 9.13L8.83 14.5",
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            stroke("M4.5 14.5H14.5", cap = StrokeCap.Round)
            stroke("M3.5 8L9.5 14", cap = StrokeCap.Round)
        }.build()
}

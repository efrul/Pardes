package io.github.kdroidfilter.seforimapp.icons

/*
   The fits of the pages in their view, as PDF readers' icons show them: Material Symbols' fit_page_height (a whole
   page in the view's height) and fit_page_width (the view's width), by Google, and two thirds of a page in the view's
   height drawn after them.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A whole page in the view's height (Material Symbols' fit_page_height): a page, its two arrows to its top and bottom. */
val PageFit: ImageVector
    get() {
        if (_PageFit != null) return _PageFit!!

        _PageFit =
            fitIcon("PageFit") {
                page()
                moveTo(360f, 360f)
                horizontalLineToRelative(240f)
                lineTo(480f, 240f)
                lineTo(360f, 360f)
                close()
                moveToRelative(120f, 360f)
                lineToRelative(120f, -120f)
                horizontalLineTo(360f)
                lineToRelative(120f, 120f)
                close()
            }

        return _PageFit!!
    }

/**
 * Two thirds of a page in the view's height, after fit_page_height: the page split at two thirds of its height, its
 * two arrows to its top and to that line.
 */
val PageFitTwoThirds: ImageVector
    get() {
        if (_PageFitTwoThirds != null) return _PageFitTwoThirds!!

        _PageFitTwoThirds =
            fitIcon("PageFitTwoThirds") {
                page()
                // The line two thirds down the page, the view's bottom edge
                moveTo(240f, 627f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(480f)
                verticalLineToRelative(80f)
                close()
                moveTo(380f, 325f)
                horizontalLineToRelative(200f)
                lineTo(480f, 225f)
                lineTo(380f, 325f)
                close()
                moveToRelative(100f, 160f)
                lineToRelative(100f, -100f)
                horizontalLineTo(380f)
                lineToRelative(100f, 100f)
                close()
            }

        return _PageFitTwoThirds!!
    }

/** The view's width (Material Symbols' fit_page_width): a frame wider than tall, its two arrows to its sides. */
val PageFitWidth: ImageVector
    get() {
        if (_PageFitWidth != null) return _PageFitWidth!!

        _PageFitWidth =
            fitIcon("PageFitWidth") {
                moveTo(160f, 800f)
                quadToRelative(-33f, 0f, -56.5f, -23.5f)
                reflectiveQuadTo(80f, 720f)
                verticalLineToRelative(-480f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(160f, 160f)
                horizontalLineToRelative(640f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(880f, 240f)
                verticalLineToRelative(480f)
                quadToRelative(0f, 33f, -23.5f, 56.5f)
                reflectiveQuadTo(800f, 800f)
                horizontalLineTo(160f)
                close()
                moveToRelative(640f, -560f)
                horizontalLineTo(160f)
                verticalLineToRelative(480f)
                horizontalLineToRelative(640f)
                verticalLineToRelative(-480f)
                close()
                moveTo(360f, 600f)
                verticalLineToRelative(-240f)
                lineTo(240f, 480f)
                lineToRelative(120f, 120f)
                close()
                moveToRelative(360f, -120f)
                lineTo(600f, 360f)
                verticalLineToRelative(240f)
                lineToRelative(120f, -120f)
                close()
            }

        return _PageFitWidth!!
    }

// A Material Symbols icon of filled paths drawn by [draw]
private fun fitIcon(
    name: String,
    draw: PathBuilder.() -> Unit,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply { path(fill = SolidColor(Color.Black), pathBuilder = draw) }
        .build()

// fit_page_height's page, taller than wide, with rounded corners: its outline, and its inside the other way, a ring
private fun PathBuilder.page() {
    moveTo(240f, 880f)
    quadToRelative(-33f, 0f, -56.5f, -23.5f)
    reflectiveQuadTo(160f, 800f)
    verticalLineToRelative(-640f)
    quadToRelative(0f, -33f, 23.5f, -56.5f)
    reflectiveQuadTo(240f, 80f)
    horizontalLineToRelative(480f)
    quadToRelative(33f, 0f, 56.5f, 23.5f)
    reflectiveQuadTo(800f, 160f)
    verticalLineToRelative(640f)
    quadToRelative(0f, 33f, -23.5f, 56.5f)
    reflectiveQuadTo(720f, 880f)
    horizontalLineTo(240f)
    close()
    moveToRelative(480f, -80f)
    verticalLineToRelative(-640f)
    horizontalLineTo(240f)
    verticalLineToRelative(640f)
    horizontalLineToRelative(480f)
    close()
}

private var _PageFit: ImageVector? = null
private var _PageFitTwoThirds: ImageVector? = null
private var _PageFitWidth: ImageVector? = null

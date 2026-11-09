package io.github.kdroidfilter.seforimapp.android.ui.onboarding

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The onboarding's illustrations that desktop's `icons` module lacks as drawn here: the folded map and the check. */
internal object OnboardingIcons {
    val FoldedMap: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Map",
                defaultWidth = 16.dp,
                defaultHeight = 16.dp,
                viewportWidth = 16f,
                viewportHeight = 16f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                ) {
                    moveTo(15.817f, 0.113f)
                    arcTo(0.5f, 0.5f, 0f, false, true, 16f, 0.5f)
                    verticalLineToRelative(14f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, -0.402f, 0.49f)
                    lineToRelative(-5f, 1f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, -0.196f, 0f)
                    lineTo(5.5f, 15.01f)
                    lineToRelative(-4.902f, 0.98f)
                    arcTo(0.5f, 0.5f, 0f, false, true, 0f, 15.5f)
                    verticalLineToRelative(-14f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, 0.402f, -0.49f)
                    lineToRelative(5f, -1f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, 0.196f, 0f)
                    lineTo(10.5f, 0.99f)
                    lineToRelative(4.902f, -0.98f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, 0.415f, 0.103f)
                    moveTo(10f, 1.91f)
                    lineToRelative(-4f, -0.8f)
                    verticalLineToRelative(12.98f)
                    lineToRelative(4f, 0.8f)
                    close()
                    moveToRelative(1f, 12.98f)
                    lineToRelative(4f, -0.8f)
                    verticalLineTo(1.11f)
                    lineToRelative(-4f, 0.8f)
                    close()
                    moveToRelative(-6f, -0.8f)
                    verticalLineTo(1.11f)
                    lineToRelative(-4f, 0.8f)
                    verticalLineToRelative(12.98f)
                    close()
                }
            }.build()
    }

    val Check2Circle: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Check2Circle",
                defaultWidth = 16.dp,
                defaultHeight = 16.dp,
                viewportWidth = 16f,
                viewportHeight = 16f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                ) {
                    moveTo(2.5f, 8f)
                    arcToRelative(5.5f, 5.5f, 0f, false, true, 8.25f, -4.764f)
                    arcToRelative(0.5f, 0.5f, 0f, false, false, 0.5f, -0.866f)
                    arcTo(6.5f, 6.5f, 0f, true, false, 14.5f, 8f)
                    arcToRelative(0.5f, 0.5f, 0f, false, false, -1f, 0f)
                    arcToRelative(5.5f, 5.5f, 0f, true, true, -11f, 0f)
                }
                path(
                    fill = SolidColor(Color.Black),
                ) {
                    moveTo(15.354f, 3.354f)
                    arcToRelative(0.5f, 0.5f, 0f, false, false, -0.708f, -0.708f)
                    lineTo(8f, 9.293f)
                    lineTo(5.354f, 6.646f)
                    arcToRelative(0.5f, 0.5f, 0f, true, false, -0.708f, 0.708f)
                    lineToRelative(3f, 3f)
                    arcToRelative(0.5f, 0.5f, 0f, false, false, 0.708f, 0f)
                    close()
                }
            }.build()
    }
}

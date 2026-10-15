package io.github.kdroidfilter.seforimapp.android.ui.onboarding

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Onboarding illustrations, copied path for path from the desktop `icons` module. */
internal object OnboardingIcons {
    val DownloadForOffline: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Download_for_offline",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000)),
                ) {
                    moveTo(280f, 680f)
                    horizontalLineToRelative(400f)
                    verticalLineToRelative(-80f)
                    horizontalLineTo(280f)
                    close()
                    moveToRelative(200f, -120f)
                    lineToRelative(160f, -160f)
                    lineToRelative(-56f, -56f)
                    lineToRelative(-64f, 62f)
                    verticalLineToRelative(-166f)
                    horizontalLineToRelative(-80f)
                    verticalLineToRelative(166f)
                    lineToRelative(-64f, -62f)
                    lineToRelative(-56f, 56f)
                    close()
                    moveToRelative(0f, 320f)
                    quadToRelative(-83f, 0f, -156f, -31.5f)
                    reflectiveQuadTo(197f, 763f)
                    reflectiveQuadToRelative(-85.5f, -127f)
                    reflectiveQuadTo(80f, 480f)
                    reflectiveQuadToRelative(31.5f, -156f)
                    reflectiveQuadTo(197f, 197f)
                    reflectiveQuadToRelative(127f, -85.5f)
                    reflectiveQuadTo(480f, 80f)
                    reflectiveQuadToRelative(156f, 31.5f)
                    reflectiveQuadTo(763f, 197f)
                    reflectiveQuadToRelative(85.5f, 127f)
                    reflectiveQuadTo(880f, 480f)
                    reflectiveQuadToRelative(-31.5f, 156f)
                    reflectiveQuadTo(763f, 763f)
                    reflectiveQuadToRelative(-127f, 85.5f)
                    reflectiveQuadTo(480f, 880f)
                    moveToRelative(0f, -80f)
                    quadToRelative(134f, 0f, 227f, -93f)
                    reflectiveQuadToRelative(93f, -227f)
                    reflectiveQuadToRelative(-93f, -227f)
                    reflectiveQuadToRelative(-227f, -93f)
                    reflectiveQuadToRelative(-227f, 93f)
                    reflectiveQuadToRelative(-93f, 227f)
                    reflectiveQuadToRelative(93f, 227f)
                    reflectiveQuadToRelative(227f, 93f)
                    moveToRelative(0f, -320f)
                }
            }.build()
    }

    val Unarchive: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Unarchive",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000)),
                ) {
                    moveTo(480f, 400f)
                    lineTo(320f, 560f)
                    lineToRelative(56f, 56f)
                    lineToRelative(64f, -64f)
                    verticalLineToRelative(168f)
                    horizontalLineToRelative(80f)
                    verticalLineToRelative(-168f)
                    lineToRelative(64f, 64f)
                    lineToRelative(56f, -56f)
                    close()
                    moveToRelative(-280f, -80f)
                    verticalLineToRelative(440f)
                    horizontalLineToRelative(560f)
                    verticalLineToRelative(-440f)
                    close()
                    moveToRelative(0f, 520f)
                    quadToRelative(-33f, 0f, -56.5f, -23.5f)
                    reflectiveQuadTo(120f, 760f)
                    verticalLineToRelative(-499f)
                    quadToRelative(0f, -14f, 4.5f, -27f)
                    reflectiveQuadToRelative(13.5f, -24f)
                    lineToRelative(50f, -61f)
                    quadToRelative(11f, -14f, 27.5f, -21.5f)
                    reflectiveQuadTo(250f, 120f)
                    horizontalLineToRelative(460f)
                    quadToRelative(18f, 0f, 34.5f, 7.5f)
                    reflectiveQuadTo(772f, 149f)
                    lineToRelative(50f, 61f)
                    quadToRelative(9f, 11f, 13.5f, 24f)
                    reflectiveQuadToRelative(4.5f, 27f)
                    verticalLineToRelative(499f)
                    quadToRelative(0f, 33f, -23.5f, 56.5f)
                    reflectiveQuadTo(760f, 840f)
                    close()
                    moveToRelative(16f, -600f)
                    horizontalLineToRelative(528f)
                    lineToRelative(-34f, -40f)
                    horizontalLineTo(250f)
                    close()
                    moveToRelative(264f, 300f)
                }
            }.build()
    }

    val InkPen: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Ink_pen",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000)),
                ) {
                    moveToRelative(490f, -527f)
                    lineToRelative(37f, 37f)
                    lineToRelative(217f, -217f)
                    lineToRelative(-37f, -37f)
                    close()
                    moveTo(200f, 760f)
                    horizontalLineToRelative(37f)
                    lineToRelative(233f, -233f)
                    lineToRelative(-37f, -37f)
                    lineToRelative(-233f, 233f)
                    close()
                    moveToRelative(355f, -205f)
                    lineTo(405f, 405f)
                    lineToRelative(167f, -167f)
                    lineToRelative(-29f, -29f)
                    lineToRelative(-219f, 219f)
                    lineToRelative(-56f, -56f)
                    lineToRelative(218f, -219f)
                    quadToRelative(24f, -24f, 56.5f, -24f)
                    reflectiveQuadToRelative(56.5f, 24f)
                    lineToRelative(29f, 29f)
                    lineToRelative(50f, -50f)
                    quadToRelative(12f, -12f, 28.5f, -12f)
                    reflectiveQuadToRelative(28.5f, 12f)
                    lineToRelative(93f, 93f)
                    quadToRelative(12f, 12f, 12f, 28.5f)
                    reflectiveQuadTo(828f, 282f)
                    close()
                    moveTo(270f, 840f)
                    horizontalLineTo(120f)
                    verticalLineToRelative(-150f)
                    lineToRelative(285f, -285f)
                    lineToRelative(150f, 150f)
                    close()
                }
            }.build()
    }

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

    val FileArrowDown: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "FileArrowDown",
                defaultWidth = 16.dp,
                defaultHeight = 16.dp,
                viewportWidth = 16f,
                viewportHeight = 16f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                ) {
                    moveTo(8f, 5f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, 0.5f, 0.5f)
                    verticalLineToRelative(3.793f)
                    lineToRelative(1.146f, -1.147f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, 0.708f, 0.708f)
                    lineToRelative(-2f, 2f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, -0.708f, 0f)
                    lineToRelative(-2f, -2f)
                    arcToRelative(0.5f, 0.5f, 0f, true, true, 0.708f, -0.708f)
                    lineTo(7.5f, 9.293f)
                    verticalLineTo(5.5f)
                    arcTo(0.5f, 0.5f, 0f, false, true, 8f, 5f)
                }
                path(
                    fill = SolidColor(Color.Black),
                ) {
                    moveTo(4f, 0f)
                    arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                    verticalLineToRelative(12f)
                    arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                    horizontalLineToRelative(8f)
                    arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                    verticalLineTo(2f)
                    arcToRelative(2f, 2f, 0f, false, false, -2f, -2f)
                    close()
                    moveToRelative(0f, 1f)
                    horizontalLineToRelative(8f)
                    arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                    verticalLineToRelative(12f)
                    arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
                    horizontalLineTo(4f)
                    arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
                    verticalLineTo(2f)
                    arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                }
            }.build()
    }

    val Speed: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Speed",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000)),
                ) {
                    moveTo(418f, 620f)
                    quadToRelative(24f, 24f, 62f, 23.5f)
                    reflectiveQuadToRelative(56f, -27.5f)
                    lineToRelative(224f, -336f)
                    lineToRelative(-336f, 224f)
                    quadToRelative(-27f, 18f, -28.5f, 55f)
                    reflectiveQuadToRelative(22.5f, 61f)
                    moveToRelative(62f, -460f)
                    quadToRelative(59f, 0f, 113.5f, 16.5f)
                    reflectiveQuadTo(696f, 226f)
                    lineToRelative(-76f, 48f)
                    quadToRelative(-33f, -17f, -68.5f, -25.5f)
                    reflectiveQuadTo(480f, 240f)
                    quadToRelative(-133f, 0f, -226.5f, 93.5f)
                    reflectiveQuadTo(160f, 560f)
                    quadToRelative(0f, 42f, 11.5f, 83f)
                    reflectiveQuadToRelative(32.5f, 77f)
                    horizontalLineToRelative(552f)
                    quadToRelative(23f, -38f, 33.5f, -79f)
                    reflectiveQuadToRelative(10.5f, -85f)
                    quadToRelative(0f, -36f, -8.5f, -70f)
                    reflectiveQuadTo(766f, 420f)
                    lineToRelative(48f, -76f)
                    quadToRelative(30f, 47f, 47.5f, 100f)
                    reflectiveQuadTo(880f, 554f)
                    reflectiveQuadToRelative(-13f, 109f)
                    reflectiveQuadToRelative(-41f, 99f)
                    quadToRelative(-11f, 18f, -30f, 28f)
                    reflectiveQuadToRelative(-40f, 10f)
                    horizontalLineTo(204f)
                    quadToRelative(-21f, 0f, -40f, -10f)
                    reflectiveQuadToRelative(-30f, -28f)
                    quadToRelative(-26f, -45f, -40f, -95.5f)
                    reflectiveQuadTo(80f, 560f)
                    quadToRelative(0f, -83f, 31.5f, -155.5f)
                    reflectiveQuadToRelative(86f, -127f)
                    reflectiveQuadToRelative(127.5f, -86f)
                    reflectiveQuadTo(480f, 160f)
                    moveToRelative(7f, 313f)
                }
            }.build()
    }

    val Timer: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Timer",
                defaultWidth = 15.dp,
                defaultHeight = 15.dp,
                viewportWidth = 15f,
                viewportHeight = 15f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.EvenOdd,
                ) {
                    moveTo(7.49998f, 0.849976f)
                    curveTo(7.22383f, 0.849976f, 6.99998f, 1.07383f, 6.99998f, 1.34998f)
                    verticalLineTo(3.52234f)
                    curveTo(6.99998f, 3.79848f, 7.22383f, 4.02234f, 7.49998f, 4.02234f)
                    curveTo(7.77612f, 4.02234f, 7.99998f, 3.79848f, 7.99998f, 3.52234f)
                    verticalLineTo(1.8718f)
                    curveTo(10.8862f, 2.12488f, 13.15f, 4.54806f, 13.15f, 7.49998f)
                    curveTo(13.15f, 10.6204f, 10.6204f, 13.15f, 7.49998f, 13.15f)
                    curveTo(4.37957f, 13.15f, 1.84998f, 10.6204f, 1.84998f, 7.49998f)
                    curveTo(1.84998f, 6.10612f, 2.35407f, 4.83128f, 3.19049f, 3.8459f)
                    curveTo(3.36919f, 3.63538f, 3.34339f, 3.31985f, 3.13286f, 3.14115f)
                    curveTo(2.92234f, 2.96245f, 2.60681f, 2.98825f, 2.42811f, 3.19877f)
                    curveTo(1.44405f, 4.35808f, 0.849976f, 5.86029f, 0.849976f, 7.49998f)
                    curveTo(0.849976f, 11.1727f, 3.82728f, 14.15f, 7.49998f, 14.15f)
                    curveTo(11.1727f, 14.15f, 14.15f, 11.1727f, 14.15f, 7.49998f)
                    curveTo(14.15f, 3.82728f, 11.1727f, 0.849976f, 7.49998f, 0.849976f)
                    close()
                    moveTo(6.74049f, 8.08072f)
                    lineTo(4.22363f, 4.57237f)
                    curveTo(4.15231f, 4.47295f, 4.16346f, 4.33652f, 4.24998f, 4.25f)
                    curveTo(4.33649f, 4.16348f, 4.47293f, 4.15233f, 4.57234f, 4.22365f)
                    lineTo(8.08069f, 6.74051f)
                    curveTo(8.56227f, 7.08599f, 8.61906f, 7.78091f, 8.19998f, 8.2f)
                    curveTo(7.78089f, 8.61909f, 7.08597f, 8.56229f, 6.74049f, 8.08072f)
                    close()
                }
            }.build()
    }

    val InstallDesktop: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Install_desktop",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000)),
                ) {
                    moveTo(320f, 840f)
                    verticalLineToRelative(-80f)
                    horizontalLineTo(160f)
                    quadToRelative(-33f, 0f, -56.5f, -23.5f)
                    reflectiveQuadTo(80f, 680f)
                    verticalLineToRelative(-480f)
                    quadToRelative(0f, -33f, 23.5f, -56.5f)
                    reflectiveQuadTo(160f, 120f)
                    horizontalLineToRelative(320f)
                    verticalLineToRelative(80f)
                    horizontalLineTo(160f)
                    verticalLineToRelative(480f)
                    horizontalLineToRelative(640f)
                    verticalLineToRelative(-120f)
                    horizontalLineToRelative(80f)
                    verticalLineToRelative(120f)
                    quadToRelative(0f, 33f, -23.5f, 56.5f)
                    reflectiveQuadTo(800f, 760f)
                    horizontalLineTo(640f)
                    verticalLineToRelative(80f)
                    close()
                    moveToRelative(360f, -280f)
                    lineTo(480f, 360f)
                    lineToRelative(56f, -56f)
                    lineToRelative(104f, 103f)
                    verticalLineToRelative(-287f)
                    horizontalLineToRelative(80f)
                    verticalLineToRelative(287f)
                    lineToRelative(104f, -103f)
                    lineToRelative(56f, 56f)
                    close()
                }
            }.build()
    }
}

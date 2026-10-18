package io.github.kdroidfilter.seforimapp.android

import android.os.Build
import androidx.compose.ui.graphics.vector.PathNode

/**
 * Vector icons on Android 11 and 12. Compose draws a relative moveTo that follows a close() with Path.rMoveTo, and
 * the Skia of Android 11 moves it from the last point the closed contour reached, where Compose, SVG and Android 13's
 * Skia move it from the contour's start. Most Material Symbols paths start their later shapes that way
 * ("...Zm-280-80v440h560..."), so those shapes were drawn out of place: a box filled black with its hole moved aside.
 *
 * Compose's ImageVector.Builder passes the data of every path and clip path through [absoluteMoves] first: the
 * build rewrites its addPath and addGroup to do so (build-logic, ComposeVectorMovesFactory). Every icon is covered
 * that way, whoever built it: this app, the desktop icons module, Material icons, vectors read from XML.
 */
object VectorPathCompat {
    /**
     * [nodes] with each relative moveTo that directly follows a close() turned into an absolute moveTo to the same
     * point, Compose's own. The same list on Android 13 and later, and when no such moveTo is in it.
     */
    @JvmStatic
    fun absoluteMoves(nodes: List<PathNode>?): List<PathNode>? {
        if (nodes == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return nodes
        if ((1 until nodes.size).none { nodes[it] is PathNode.RelativeMoveTo && nodes[it - 1] is PathNode.Close }) return nodes
        // The current point and the contour's start, followed as Compose's PathParser follows them
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        var afterClose = false
        val result = ArrayList<PathNode>(nodes.size)
        for (node in nodes) {
            var out = node
            when (node) {
                is PathNode.Close -> {
                    x = startX
                    y = startY
                }
                is PathNode.RelativeMoveTo -> {
                    x += node.dx
                    y += node.dy
                    startX = x
                    startY = y
                    if (afterClose) out = PathNode.MoveTo(x, y)
                }
                is PathNode.MoveTo -> {
                    x = node.x
                    y = node.y
                    startX = x
                    startY = y
                }
                is PathNode.LineTo -> {
                    x = node.x
                    y = node.y
                }
                is PathNode.RelativeLineTo -> {
                    x += node.dx
                    y += node.dy
                }
                is PathNode.HorizontalTo -> x = node.x
                is PathNode.RelativeHorizontalTo -> x += node.dx
                is PathNode.VerticalTo -> y = node.y
                is PathNode.RelativeVerticalTo -> y += node.dy
                is PathNode.CurveTo -> {
                    x = node.x3
                    y = node.y3
                }
                is PathNode.RelativeCurveTo -> {
                    x += node.dx3
                    y += node.dy3
                }
                is PathNode.ReflectiveCurveTo -> {
                    x = node.x2
                    y = node.y2
                }
                is PathNode.RelativeReflectiveCurveTo -> {
                    x += node.dx2
                    y += node.dy2
                }
                is PathNode.QuadTo -> {
                    x = node.x2
                    y = node.y2
                }
                is PathNode.RelativeQuadTo -> {
                    x += node.dx2
                    y += node.dy2
                }
                is PathNode.ReflectiveQuadTo -> {
                    x = node.x
                    y = node.y
                }
                is PathNode.RelativeReflectiveQuadTo -> {
                    x += node.dx
                    y += node.dy
                }
                // The arc's end point (Compose names it arcStart)
                is PathNode.ArcTo -> {
                    x = node.arcStartX
                    y = node.arcStartY
                }
                is PathNode.RelativeArcTo -> {
                    x += node.arcStartDx
                    y += node.arcStartDy
                }
            }
            afterClose = node is PathNode.Close
            result.add(out)
        }
        return result
    }
}

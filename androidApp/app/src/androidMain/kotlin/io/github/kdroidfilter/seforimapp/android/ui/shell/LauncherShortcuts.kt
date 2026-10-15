package io.github.kdroidfilter.seforimapp.android.ui.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import io.github.kdroidfilter.seforimapp.android.MainActivity
import io.github.kdroidfilter.seforimapp.android.data.VisitEntry
import kotlin.math.roundToInt

/**
 * Launcher shortcuts: the Android counterpart of desktop's jump list, dock menu and Linux quicklist
 * (core/presentation/components/AppJumpList.kt, AppDockMenu.kt, AppLinuxQuicklist.kt): desktop's "לשונית חדשה" task
 * (menu_new_tab, a new Home tab), then the books read last as zayit://book links, routed by MainActivity like any
 * other deep link.
 */
object LauncherShortcuts {
    /** Explicit intent action of the "לשונית חדשה" shortcut: a new Home tab (desktop SCHEME_NEW_TAB → OnAdd). */
    const val ACTION_NEW_TAB = "io.github.kdroidfilter.seforimapp.android.action.NEW_TAB"

    private const val MAX_BOOKS = 3
    private const val ICON_BACKGROUND = 0xFF2B3618.toInt() // the launcher icon's olive (@color/pardes_olive_*)
    private const val ICON_FOREGROUND = 0xFFE3BE62.toInt() // its gold (@color/pardes_gold_*)

    /** Blocking (binder calls, bitmaps): call off the main thread. */
    fun update(context: Context, recent: List<VisitEntry>) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val activity = ComponentName(context, MainActivity::class.java)
        val newTab = ShortcutInfo.Builder(context, "new-tab")
            .setShortLabel("לשונית חדשה")
            .setLongLabel("לשונית חדשה")
            .setIcon(adaptiveIcon(context, Icons.Outlined.Add))
            .setIntent(Intent(ACTION_NEW_TAB).setComponent(activity))
            .setRank(0)
            .build()
        val bookIcon by lazy { adaptiveIcon(context, Icons.AutoMirrored.Outlined.MenuBook) }
        val books = recent.asSequence()
            .filter { it.bookId != null }
            .distinctBy { it.bookId }
            .take(minOf(MAX_BOOKS, manager.maxShortcutCountPerActivity - 1))
            .mapIndexed { i, e ->
                ShortcutInfo.Builder(context, "book-${e.bookId}")
                    .setShortLabel(e.title.substringBefore(" - ").take(24).ifBlank { e.title.take(24) })
                    .setLongLabel(e.title.take(60))
                    .setIcon(bookIcon)
                    .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse(bookShareLink(e.bookId!!, e.lineId))).setComponent(activity))
                    .setRank(i + 1)
                    .build()
            }.toList()
        runCatching { manager.dynamicShortcuts = listOf(newTab) + books }
    }

    /** An adaptive launcher icon drawn from a Material icon, gold on the launcher icon's black. */
    private fun adaptiveIcon(context: Context, vector: ImageVector): Icon {
        val density = context.resources.displayMetrics.density
        val size = (108 * density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ICON_BACKGROUND)
        val glyph = 44 * density
        canvas.translate((size - glyph) / 2f, (size - glyph) / 2f)
        canvas.scale(glyph / vector.viewportWidth, glyph / vector.viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ICON_FOREGROUND }
        fun draw(group: VectorGroup) {
            for (node in group) {
                when (node) {
                    is VectorPath -> {
                        val path = node.pathData.toPath().asAndroidPath()
                        path.fillType = if (node.pathFillType == PathFillType.EvenOdd) android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING
                        canvas.drawPath(path, paint)
                    }
                    is VectorGroup -> draw(node)
                }
            }
        }
        draw(vector.root)
        return Icon.createWithAdaptiveBitmap(bitmap)
    }
}

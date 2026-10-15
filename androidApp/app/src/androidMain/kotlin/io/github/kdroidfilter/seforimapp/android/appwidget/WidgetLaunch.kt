package io.github.kdroidfilter.seforimapp.android.appwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.kdroidfilter.seforimapp.android.MainActivity
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.Limud
import io.github.kdroidfilter.seforimapp.android.data.LimudResources
import io.github.kdroidfilter.seforimapp.android.data.limudOfDay
import io.github.kdroidfilter.seforimapp.android.data.resolvePlace
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.Overlay
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.resetToolsStateToUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val ACTION_WIDGET = "io.github.kdroidfilter.seforimapp.android.action.WIDGET"
private const val EXTRA_TARGET = "widget_target"
private const val EXTRA_TOOL = "widget_tool"
private const val EXTRA_LIMUD = "widget_limud"
private const val EXTRA_DATE = "widget_date"
private const val EXTRA_ISRAEL = "widget_israel"
private const val TARGET_TOOL = "tool"
private const val TARGET_LIMUD = "limud"

/**
 * What a tap on a launcher widget opens, as the Home's widget would: the Home, the widget's tool page (desktop opens it
 * as the widget's tool window, HomeWidget.toolWindowSize; Android's HomeWidget.tool), or a limud's passage in a new tab
 * (desktop LuachWidgets rememberOpenInLibrary).
 */
internal sealed interface WidgetLaunch {
    data object Home : WidgetLaunch

    data class Tool(val tool: ToolKind) : WidgetLaunch

    /** The passage of [limud] on [date], the day the widget showed (the reader marks where it ends). */
    data class LimudPassage(val limud: Limud, val date: LocalDate, val inIsrael: Boolean) : WidgetLaunch
}

/**
 * MainActivity with what to open; an intent carries no data, so MainActivity's link handling leaves it alone.
 * PendingIntents differ by request code only (extras don't tell them apart): one per target.
 */
internal fun WidgetLaunch.pendingIntent(context: Context): PendingIntent {
    val intent = Intent(ACTION_WIDGET).setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val requestCode =
        when (this) {
            WidgetLaunch.Home -> 1
            is WidgetLaunch.Tool -> {
                intent.putExtra(EXTRA_TARGET, TARGET_TOOL).putExtra(EXTRA_TOOL, tool.name)
                100 + tool.ordinal
            }
            is WidgetLaunch.LimudPassage -> {
                intent.putExtra(EXTRA_TARGET, TARGET_LIMUD)
                    .putExtra(EXTRA_LIMUD, limud.name)
                    .putExtra(EXTRA_DATE, date.toString())
                    .putExtra(EXTRA_ISRAEL, inIsrael)
                200 + limud.ordinal
            }
        }
    return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** The widget tap [intent] asks for; null for any other intent, or when Recents relaunches an old one. */
internal fun widgetLaunchOf(intent: Intent?): WidgetLaunch? {
    if (intent == null || intent.action != ACTION_WIDGET) return null
    if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null
    val target =
        when (intent.getStringExtra(EXTRA_TARGET)) {
            TARGET_TOOL -> {
                val name = intent.getStringExtra(EXTRA_TOOL)
                ToolKind.entries.firstOrNull { it.name == name }?.let { WidgetLaunch.Tool(it) }
            }
            TARGET_LIMUD -> {
                val name = intent.getStringExtra(EXTRA_LIMUD)
                val limud = Limud.entries.firstOrNull { it.name == name }
                val date = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (limud != null && date != null) WidgetLaunch.LimudPassage(limud, date, intent.getBooleanExtra(EXTRA_ISRAEL, false)) else null
            }
            else -> null
        }
    return target ?: WidgetLaunch.Home
}

/**
 * Opens it above whatever the app shows, as a zayit:// link does (ZayitApp's entry): the overlays go first. Main thread,
 * once the library is open (MainActivity's Startup.Ready).
 */
internal suspend fun WidgetLaunch.open(context: Context, app: ZayitApplication, appVm: AppViewModel) {
    repeat(appVm.state.value.overlays.size) { appVm.pop() }
    when (this) {
        WidgetLaunch.Home -> {
            val state = appVm.state.value
            if (state.selectedTab.destination != Destination.Home) {
                // The Home tab already open rather than one more on each tap
                val home = state.tabs.indexOfFirst { it.destination == Destination.Home }
                if (home >= 0) appVm.selectTab(home) else appVm.newHomeTab()
            }
        }
        is WidgetLaunch.Tool -> {
            // The tool shows what the launcher widget showed: the user's place, today, now
            resetToolsStateToUser()
            appVm.push(Overlay.Tool(tool))
        }
        is WidgetLaunch.LimudPassage -> openPassage(context, app, appVm, this)
    }
}

/** LuachWidgets rememberOpenInLibrary: the book read on tap, a short notice when the library doesn't have it. */
private suspend fun openPassage(context: Context, app: ZayitApplication, appVm: AppViewModel, passage: WidgetLaunch.LimudPassage) {
    val opened =
        withContext(Dispatchers.IO) {
            runCatching {
                LimudResources.install(app)
                limudOfDay(passage.date, passage.inIsrael, setOf(passage.limud)).firstOrNull()?.place?.let { app.libraryStore.repository().resolvePlace(it) }
            }.getOrNull()
        }
    if (opened == null) {
        Toast.makeText(context, "הספר לא נמצא בספרייה", Toast.LENGTH_SHORT).show()
    } else {
        val book = opened.book
        appVm.openInNewTab(Destination.Book(bookId = book.id, lineId = opened.lineId, title = book.title, endLineId = opened.endLineId), book.title)
    }
}

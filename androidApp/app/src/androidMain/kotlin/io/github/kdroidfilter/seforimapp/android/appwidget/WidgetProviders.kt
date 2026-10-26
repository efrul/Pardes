package io.github.kdroidfilter.seforimapp.android.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * The launcher (home-screen) widgets: Android only, desktop 0.6.10 has no OS widgets. Each one shows what a widget of
 * the app's Home shows (desktop features/home/widgets, ported in ui/home/widgets), drawn as RemoteViews by
 * [ZayitWidgets] from the same data/ functions. No polling (updatePeriodMillis 0): a broadcast only hands its widgets
 * over, the work runs off the main thread while the broadcast is kept alive ([goAsync]) for a bounded time.
 */
internal abstract class ZayitWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {
    // Added, or updated by the system (after a boot, the app updated): those widgets
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ZayitWidgets.onBroadcast(this, context, WidgetRequest.Widgets(kind, appWidgetIds))
    }

    // Resized: the launcher picks among the sizes it was given, but the sizes themselves changed; that widget alone
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        ZayitWidgets.onBroadcast(this, context, WidgetRequest.Widgets(kind, intArrayOf(appWidgetId)))
    }

    // The last one of its kind was removed: the shared alarm follows the kinds still placed, or is cancelled
    override fun onDisabled(context: Context) {
        ZayitWidgets.onBroadcast(this, context, WidgetRequest.Alarm)
    }
}

/** "זמני היום" (desktop ZmanimWidget). */
internal class ZmanimWidgetProvider : ZayitWidgetProvider(WidgetKind.ZMANIM)

/** "הזמן הבא" (desktop LuachWidgets NextZmanWidget). */
internal class NextZmanWidgetProvider : ZayitWidgetProvider(WidgetKind.NEXT_ZMAN)

/** "לוח עברי" (desktop CalendarWidget and the upcoming events of LuachWidgets). */
internal class LuachWidgetProvider : ZayitWidgetProvider(WidgetKind.LUACH)

/** "לימוד יומי" (desktop LuachWidgets LimudWidget). */
internal class LimudWidgetProvider : ZayitWidgetProvider(WidgetKind.LIMUD)

/** "חורבן הבית" (desktop TempleCountdownWidget). */
internal class TempleWidgetProvider : ZayitWidgetProvider(WidgetKind.TEMPLE)

/** "חיפוש בפרדס": opens the Home, where the search bar is. */
internal class SearchWidgetProvider : ZayitWidgetProvider(WidgetKind.SEARCH)

/**
 * Redraws every placed widget: from [ZayitWidgets]'s alarm (the next zman, midnight, Jerusalem's sunset) and when the
 * clock, the time zone or the locale changes under them.
 */
internal class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = if (intent.action == ZayitWidgets.ACTION_REFRESH) WidgetRequest.All else WidgetRequest.AllAfterClockChange
        ZayitWidgets.onBroadcast(this, context, request)
    }
}

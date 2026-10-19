package io.github.kdroidfilter.seforimapp.android.appwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A launcher widget: its provider, the size it is drawn at when the launcher gives none, and whether what it shows
 * turns with the day (midnight where the user is).
 */
internal enum class WidgetKind(
    val provider: Class<out AppWidgetProvider>,
    val fallback: SizeF,
    val followsDay: Boolean,
) {
    ZMANIM(ZmanimWidgetProvider::class.java, SizeF(110f, 140f), true),
    NEXT_ZMAN(NextZmanWidgetProvider::class.java, SizeF(170f, 60f), true),
    LUACH(LuachWidgetProvider::class.java, SizeF(170f, 140f), true),
    LIMUD(LimudWidgetProvider::class.java, SizeF(170f, 140f), true),
    TEMPLE(TempleWidgetProvider::class.java, SizeF(170f, 140f), false),
    SEARCH(SearchWidgetProvider::class.java, SizeF(230f, 56f), false),
}

/** What a broadcast or the app asks [ZayitWidgets] for. */
internal sealed interface WidgetRequest {
    /** [ids] of [kind]: added, updated by the system (boot, the app updated), or resized. */
    class Widgets(val kind: WidgetKind, val ids: IntArray) : WidgetRequest {
        override fun toString() = "$kind ${ids.joinToString(",")}"
    }

    /** Every placed widget: the alarm, the app leaving the screen, a setting they show. */
    data object All : WidgetRequest

    /** Every placed widget, its day worked out again: the clock, the time zone or the locale changed. */
    data object AllAfterClockChange : WidgetRequest

    /** None drawn unless a change is due, the alarm set again: the last widget of a kind was removed. */
    data object Alarm : WidgetRequest
}

/**
 * Draws the launcher widgets and keeps them current without polling: each redraw, before it draws, sets one inexact
 * alarm for the next moment one of them changes (a zman passing, midnight, Jerusalem's sunset for the Temple count).
 * Settings they show ([io.github.kdroidfilter.seforimapp.android.data.AppPrefs]) and the app leaving the screen redraw
 * them too.
 * Never opens the library: everything comes from data/'s calendar and zmanim functions, the day's kept in [WidgetCache].
 *
 * A broadcast must never ANR the app (the system kills the process, the open reader with it): the work runs on a
 * thread of its own, a broadcast is held open ([BroadcastReceiver.goAsync]) only until its widgets are drawn or its
 * budget runs out, whichever comes first, and the drawing goes on after it. Only what is asked is drawn (the widgets of
 * the broadcast, at the sizes their launcher gave), and requests arriving while a redraw runs are merged into one next
 * redraw rather than queued one by one.
 */
internal object ZayitWidgets {
    private const val TAG = "ZayitWidgets"
    const val ACTION_REFRESH = "io.github.kdroidfilter.seforimapp.android.action.REFRESH_WIDGETS"

    // Android 12+ stretches any window shorter than this without the exact-alarm permission, which the app doesn't ask
    private const val WINDOW_MS = 10 * 60_000L
    private const val SOON_MS = 500L
    private const val MIN_DELAY_MS = 30_000L
    private const val DAY_MS = 24 * 60 * 60_000L

    // RemoteViews(Map) takes at most 16 sizes
    private const val MAX_SIZES = 16

    // The system gives a foreground broadcast 10 s from when it handed it over, which can be well before onReceive: a
    // busy main thread, or the process being started for it. While the process is young (COLD_START_MS since the
    // system asked for it, when its main thread is busiest and onReceive's lateness can't be told) a broadcast is held
    // no later than COLD_START_BUDGET_MS after that ask, or not at all once it passed; later, WARM_BUDGET_MS from
    // onReceive, which leaves the main thread 7.5 s of lateness. The ask's time is fixed for the process's life
    // (Process.getStartRequestedUptimeMillis), so it only bounds the broadcasts of its start.
    private const val COLD_START_MS = 30_000L
    private const val COLD_START_BUDGET_MS = 8_000L
    private const val WARM_BUDGET_MS = 2_500L

    // A redraw that asked for widgets (still "טוען…", maybe) sets the alarm this soon at the latest before drawing,
    // replaced once they are drawn: cut short (the process killed or frozen once its broadcast is finished), they are
    // drawn again by it rather than at the next change, a day away for the search and Temple widgets
    private const val PROVISIONAL_MS = 60_000L

    // A redraw this slow is logged as a warning
    private const val SLOW_MS = 1_000L

    // Where the alarm was last set for (wall clock), kept across processes: a redraw that finds it passed (setWindow
    // may deliver it up to WINDOW_MS late, and the alarm set again replaces it) draws every widget, as the alarm would
    private const val STATE_PREFS = "zayit_widgets"
    private const val KEY_ALARM_AT = "alarm_at"

    /** The widgets' own thread: the app's Default pool, busy at a cold start, can't hold them back. */
    private val worker: Executor by lazy { Executors.newSingleThreadExecutor { Thread(it, TAG).apply { isDaemon = true } } }

    /** Waits for a broadcast's widgets (suspended, holding no thread) to finish it in time. */
    private val waiter = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What the next redraw draws: every request since the last one started, merged. */
    private class Batch {
        var all = false
        var clockChanged = false
        val ids = LinkedHashMap<WidgetKind, MutableSet<Int>>()
        val done: CompletableJob = Job()

        fun add(request: WidgetRequest) {
            when (request) {
                is WidgetRequest.Widgets -> ids.getOrPut(request.kind) { LinkedHashSet() }.addAll(request.ids.asList())
                WidgetRequest.All -> all = true
                WidgetRequest.AllAfterClockChange -> {
                    all = true
                    clockChanged = true
                }
                WidgetRequest.Alarm -> Unit
            }
        }
    }

    private val lock = Any()
    private var next: Batch? = null // guarded by lock
    private var draining = false // guarded by lock

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var soonContext: Context? = null
    private val soon = Runnable { soonContext?.let { refreshAll(it) } }

    /** Main thread: one redraw for a burst of preference writes (setRegion writes the country, then the city). */
    fun refreshSoon(context: Context) {
        soonContext = context.applicationContext
        handler.removeCallbacks(soon)
        handler.postDelayed(soon, SOON_MS)
    }

    /** Every placed widget, from the app (no broadcast to hold). */
    fun refreshAll(context: Context) {
        enqueue(context, WidgetRequest.All)
    }

    /**
     * A broadcast's [request], from its onReceive: held open until drawn, finished by its budget in any case (the
     * drawing goes on). Returns at once.
     */
    fun onBroadcast(receiver: BroadcastReceiver, context: Context, request: WidgetRequest) {
        val budget = broadcastBudget(SystemClock.uptimeMillis())
        // None left: finished as onReceive returns, the drawing going on unheld
        if (budget <= 0L) {
            enqueue(context, request)
            return
        }
        val pending = receiver.goAsync()
        val done = enqueue(context, request)
        if (pending == null) return
        if (done == null) return pending.finish()
        // Undispatched: the timeout runs from now, whatever the IO pool is doing
        waiter.launch(start = CoroutineStart.UNDISPATCHED) {
            if (withTimeoutOrNull(budget) { done.join() } == null) {
                Log.w(TAG, "$request: broadcast finished at its $budget ms budget, still drawing")
            }
            try {
                pending.finish()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "$request", e)
            }
        }
    }

    /** How long a broadcast received at [received] (uptime) may be held open; 0 when not at all. */
    private fun broadcastBudget(received: Long): Long {
        // API 33. Android 12 has only the moment the process was bound (Process.getStartUptimeMillis), later than the
        // ask by the time the start took, seconds on a slow phone: there the budget is halved, which leaves 6 s for it
        val requested = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val start = if (requested) Process.getStartRequestedUptimeMillis() else Process.getStartUptimeMillis()
        if (start <= 0L || received - start >= COLD_START_MS) return WARM_BUDGET_MS
        val coldBudget = if (requested) COLD_START_BUDGET_MS else COLD_START_BUDGET_MS / 2
        return (start + coldBudget - received).coerceIn(0L, coldBudget)
    }

    /** Adds [request] to the next redraw, starting one unless one is under way; null outside the app. */
    private fun enqueue(context: Context, request: WidgetRequest): Job? {
        // None can be placed below Android 12: the widgets' receivers are off below API 31 (AndroidManifest.xml)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val app = context.applicationContext as? ZayitApplication ?: return null
        synchronized(lock) {
            val batch = next ?: Batch().also { next = it }
            batch.add(request)
            if (!draining) {
                draining = true
                worker.execute { drain(app) }
            }
            return batch.done
        }
    }

    /** The widgets' thread: redraws until nothing more is asked. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun drain(app: ZayitApplication) {
        while (true) {
            val batch =
                synchronized(lock) {
                    next.also { next = null } ?: run {
                        draining = false
                        return
                    }
                }
            try {
                update(app, batch)
            } catch (e: Throwable) {
                Log.w(TAG, "widgets", e)
            } finally {
                batch.done.complete()
            }
        }
    }

    @SuppressLint("ApplySharedPref") // commit() on purpose, see below
    @RequiresApi(Build.VERSION_CODES.S)
    private fun update(app: ZayitApplication, batch: Batch) {
        val started = SystemClock.uptimeMillis()
        if (batch.clockChanged) WidgetCache.clear()
        val manager = AppWidgetManager.getInstance(app) ?: return
        val alarms = app.getSystemService(AlarmManager::class.java)
        val state = app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val placed = WidgetKind.entries.associateWith { manager.getAppWidgetIds(ComponentName(app, it.provider)) }.filterValues { it.isNotEmpty() }
        val now = System.currentTimeMillis()
        val alarmAt = state.getLong(KEY_ALARM_AT, 0L)
        val full = batch.all || alarmAt <= now
        // The ones asked for first, then (when every one is drawn) the others; a widget just added may not be listed yet
        val targets = LinkedHashMap<WidgetKind, MutableSet<Int>>()
        batch.ids.forEach { (kind, ids) -> targets.getOrPut(kind) { LinkedHashSet() }.addAll(ids) }
        if (full) placed.forEach { (kind, ids) -> targets.getOrPut(kind) { LinkedHashSet() }.addAll(ids.asList()) }
        val kinds = placed.keys + targets.keys
        // commit(), not apply(), throughout: this thread may wait on the disk, and an apply() still being written holds
        // back a broadcast's finish() (QueuedWork) past its budget
        if (kinds.isEmpty()) {
            alarms?.cancel(alarmIntent(app))
            state.edit().remove(KEY_ALARM_AT).commit()
            return
        }
        val data = WidgetData(app, now, WidgetCache.day(app, now))
        // The next change of any placed widget, not only of the ones drawn; never after the one already set, which
        // the widgets left as they were still wait for. It needs the day's data only, not the drawings
        val nextChange = nextChangeAt(kinds, data).let { if (full) it else minOf(it, alarmAt) }
        // Set before drawing: the broadcast may be finished while this draws, and the alarm that brought it is used up;
        // a process killed or frozen halfway still has one. Widgets asked for get a provisional one, PROVISIONAL_MS
        // away at most, replaced below once they are drawn. Its time is stored after drawing, so that a redraw cut short
        // leaves the stored one passed and the next request draws every widget; only a sooner one is stored now, so
        // that a request (in another process, maybe) before it fires sets the alarm no later
        val early = if (batch.ids.isEmpty()) nextChange else minOf(nextChange, now + PROVISIONAL_MS)
        var stored = alarmAt
        if (early < stored) {
            state.edit().putLong(KEY_ALARM_AT, early).commit()
            stored = early
        }
        setAlarm(alarms, app, early)
        var widgets = 0
        var views = 0
        targets.forEach { (kind, ids) ->
            ids.forEach { id ->
                views += draw(manager, kind, id, data)
                widgets++
            }
        }
        if (nextChange != early) setAlarm(alarms, app, nextChange)
        if (nextChange != stored) state.edit().putLong(KEY_ALARM_AT, nextChange).commit()
        val took = SystemClock.uptimeMillis() - started
        val line = "drew $widgets widgets ($views views${if (full) ", all" else ""}) in $took ms"
        if (took >= SLOW_MS) Log.w(TAG, line) else Log.d(TAG, line)
    }

    /**
     * One widget, one layout per size its launcher may show it at (portrait and landscape, or every size on a tablet):
     * it picks the one that fits as the widget is resized, without waiting for the app. A size that fails is left out;
     * with none, the widget keeps what it shows (its last drawing, or the initial "טוען…"). Returns the layouts built.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun draw(manager: AppWidgetManager, kind: WidgetKind, id: Int, data: WidgetData): Int {
        val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull() ?: Bundle.EMPTY
        val views =
            sizesOf(options, kind.fallback).mapNotNull { size ->
                runCatching { size to kind.render(data, size) }.onFailure { Log.w(TAG, "$kind $id $size", it) }.getOrNull()
            }
        if (views.isEmpty()) return 0
        runCatching { manager.updateAppWidget(id, views.singleOrNull()?.second ?: RemoteViews(views.toMap())) }
            .onFailure { Log.w(TAG, "$kind $id", it) }
        return views.size
    }

    /**
     * The one alarm, set again for [at] (it replaces the one set before). RTC, not RTC_WAKEUP: a sleeping phone shows
     * nothing, it is redrawn when it wakes.
     */
    private fun setAlarm(alarms: AlarmManager?, context: Context, at: Long) {
        alarms?.setWindow(AlarmManager.RTC, at, WINDOW_MS, alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The sizes the launcher gave for the widget, at most [MAX_SIZES]; its bounds when it gave none. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun sizesOf(options: Bundle, fallback: SizeF): List<SizeF> {
        val given = BundleCompat.getParcelableArrayList<SizeF>(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            ?.filter { it.width > 0f && it.height > 0f }
            ?.distinct()
            ?.take(MAX_SIZES)
        if (!given.isNullOrEmpty()) return given
        // Launchers that give no sizes still give the bounds: narrowest and tallest in portrait, the other way in landscape
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat()
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
        return listOf(SizeF(minWidth, maxHeight), SizeF(maxWidth, minHeight))
            .filter { it.width > 0f && it.height > 0f }
            .distinct()
            .ifEmpty { listOf(fallback) }
    }

    /** The next moment a placed widget shows something else, a second after it, between 30 s and a day from now. */
    private fun nextChangeAt(kinds: Set<WidgetKind>, d: WidgetData): Long {
        val now = d.now.time
        val candidates = buildList {
            if (kinds.any { it.followsDay }) add(d.nextMidnight)
            if (WidgetKind.ZMANIM in kinds) {
                // The next zman's highlight moves on, and the Shabbat rows go once passed
                d.zmanRows.mapNotNull { it.time?.time }.filter { it > now }.minOrNull()?.let { add(it) }
                listOfNotNull(d.shabbat.entryTime?.time, d.shabbat.exitTime?.time).filter { it > now }.minOrNull()?.let { add(it) }
            }
            if (WidgetKind.NEXT_ZMAN in kinds) d.upcomingZmanim.firstOrNull()?.let { add(it.time.time) }
            if (WidgetKind.TEMPLE in kinds) add(d.templeTurnsAt)
        }
        return ((candidates.minOrNull() ?: (now + DAY_MS)) + 1_000L).coerceIn(now + MIN_DELAY_MS, now + DAY_MS)
    }
}

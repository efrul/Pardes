package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.City
import io.github.kdroidfilter.seforimapp.android.data.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.android.data.LimudResources
import io.github.kdroidfilter.seforimapp.android.data.kiddushLevanaEarliestFor
import io.github.kdroidfilter.seforimapp.android.data.kiddushLevanaLatestFor
import io.github.kdroidfilter.seforimapp.android.data.zmanimOpinionFor
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.Overlay
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalAppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.Date

/**
 * What the Home widgets share: the day and moment they show, and where. A zman card sets [targetTime], the Earth
 * widget [selectDate] / [selectLocation], and every widget follows. Port of desktop 0.6.7
 * features/home/widgets/HomeWidgetsState.kt; the user's place and community are kept up to date by
 * [rememberHomeWidgetsState] (desktop builds a new state when they change), and the tools share one state for the app
 * (desktop ToolWindows' rememberToolsState), so a day picked in the calendar moves the limud, the tefila and the rest.
 */
@Stable
class HomeWidgetsState internal constructor(
    val app: ZayitApplication,
    internal val layout: HomeWidgetsLayout,
) {
    /** The user's region (desktop HomeUserLocation), from the settings. */
    var userCity by mutableStateOf(app.prefs.userCityFlow.value)
        internal set

    /** The stored community code; its luach and Kiddush Levana opinions follow. */
    var community by mutableStateOf(app.prefs.communityFlow.value)
        internal set

    // עדות המזרח read the אור החיים, everyone else עתים לבינה
    val zmanimOpinion get() = zmanimOpinionFor(community)
    val kiddushLevanaEarliest get() = kiddushLevanaEarliestFor(community)
    val kiddushLevanaLatest get() = kiddushLevanaLatestFor(community)

    /** The user's own region, whatever location is picked on the globe. */
    val userInIsrael get() = userCity.inIsrael

    // Picked on the globe for a look around; never written to the user settings
    private var picked by mutableStateOf<City?>(null)

    /** The place shown: the one picked, else the user's. */
    val city: City get() = picked ?: userCity
    val location: EarthWidgetLocation get() = city.location
    val cityLabel: String? get() = city.name.ifBlank { null }
    val inIsrael: Boolean get() = city.inIsrael

    private var today by mutableStateOf(city.today())

    var selectedDate by mutableStateOf(today)
        private set

    /** The moment picked on a zman card; null shows [selectedDate] at noon (or now, today). */
    var targetTime by mutableStateOf<Date?>(null)

    /** The instant being played (the solar system's play), the one clock every widget reads; null when not playing. */
    var playingMillis by mutableStateOf<Long?>(null)
        private set

    // Its own state: the day widgets recompose when the day changes, not at each frame of the play
    private var playingDay by mutableStateOf<LocalDate?>(null)

    val playing: Boolean get() = playingMillis != null

    private fun setPlaying(millis: Long?) {
        playingMillis = millis
        playingDay = millis?.let { Instant.ofEpochMilli(it).atZone(location.timeZone.toZoneId()).toLocalDate() }
    }

    /** Plays from the moment shown, or stops on the moment reached: it stays shown, to the minute, everywhere. */
    fun togglePlay() {
        val reached = playingMillis
        if (reached == null) {
            setPlaying(skyTimeMillis ?: System.currentTimeMillis())
        } else {
            setPlaying(null)
            selectDate(Instant.ofEpochMilli(reached).atZone(location.timeZone.toZoneId()).toLocalDate())
            targetTime = Date(reached)
        }
    }

    /** Stops playing where it is (its page closing). */
    fun stopPlay() {
        if (playing) togglePlay()
    }

    /** Runs the clock while playing, at [daysPerSecond] simulated days a real second (read at each frame). */
    suspend fun runPlayClock(daysPerSecond: () -> Float) {
        var last = 0L
        while (playing) {
            withFrameNanos { now ->
                if (last != 0L) {
                    playingMillis?.let { setPlaying(it + ((now - last) / 1e9 * daysPerSecond() * DAY_MILLIS).toLong()) }
                }
                last = now
            }
        }
    }

    /** The day shown: the one played at while the solar system plays, else [selectedDate]. */
    val shownDate: LocalDate get() = playingDay ?: selectedDate

    /** The moment the sky and the solar system show; null means now. */
    val skyTimeMillis: Long? get() = playingMillis ?: restingSkyMillis

    /** [skyTimeMillis] when not playing. */
    internal val restingSkyMillis: Long?
        get() =
            targetTime?.time ?: if (selectedDate == today) {
                null
            } else {
                selectedDate
                    .atTime(12, 0)
                    .atZone(location.timeZone.toZoneId())
                    .toInstant()
                    .toEpochMilli()
            }

    /** The solar system's full page (desktop's window), open from its card. */
    var solarSystemFullscreen by mutableStateOf(false)

    /** Opens a book in a new tab (set by the host: the Home, or the tools, which close themselves first). */
    var openBook: (Book, Long?) -> Unit = { _, _ -> }

    /** Opens a passage to learn (desktop rememberOpenInLibrary): at its first line, its end marked, Shnayim Mikra's verses doubled. */
    var openPassage: (book: Book, lineId: Long?, endLineId: Long?, shnayimMikra: Boolean) -> Unit = { b, l, _, _ -> openBook(b, l) }

    /** Opens a page of the app (History, Favorites) in a tab. */
    var openPage: (Destination) -> Unit = {}

    /** Runs a search again in a new tab (a history entry), where it ran: its book, TOC entry or author, base or all books. */
    var openSearch: (Destination.Search) -> Unit = {}

    /** Opens a tool's page (the tefila's "פתח סידור"). */
    var openTool: (ToolKind) -> Unit = {}

    /** The Home's edit mode: widgets can be moved and removed, and the gallery adds new ones. */
    var editingWidgets by mutableStateOf(false)

    /** The widget whose resize frame shows, in edit mode. */
    var selectedWidget by mutableStateOf<String?>(null)

    /** A widget being dragged or resized. */
    internal val drag = WidgetDrag()

    /** The widget just removed, while its undo is offered. */
    var lastRemoved by mutableStateOf<RemovedWidget?>(null)
        private set

    fun removeWidget(widget: HomeWidget) {
        lastRemoved = layout.remove(widget)
    }

    fun undoRemove() {
        lastRemoved?.let(layout::restore)
        lastRemoved = null
    }

    fun forgetRemoved(removed: RemovedWidget) {
        if (lastRemoved === removed) lastRemoved = null
    }

    /** The widget whose [HomeWidget.Options] page is open, from an item of its menu. */
    var optionsOpen by mutableStateOf<HomeWidget?>(null)

    /** A widget's setting as saved, followed by the composition. */
    @Composable
    operator fun <T> get(setting: Setting<T>): T = layout.flow(setting).collectAsState().value

    /** The value now, outside composition. */
    fun <T> peek(setting: Setting<T>): T = layout.store[setting]

    /** Saved at once; the widget it belongs to resized if its content asks. */
    operator fun <T> set(
        setting: Setting<T>,
        value: T,
    ) = layout.set(setting, value)

    /** Back to [widget]'s default settings. */
    fun resetSettings(widget: HomeWidget) = layout.resetSettings(widget)

    fun selectDate(date: LocalDate) {
        // A day picked while playing: the play stops there, rather than going on and dropping it at the pause
        if (playingMillis != null) setPlaying(null)
        selectedDate = date
        targetTime = null
    }

    /** Back to today, where the place shown is (Android's "חזרה להיום"). */
    fun backToToday() {
        today = city.today()
        selectDate(today)
    }

    /** Today where the place shown is; a day that rolled over since moves the selection along with it. */
    val todayDate: LocalDate get() = today

    internal fun refreshToday() {
        val now = city.today()
        if (now != today) {
            if (selectedDate == today) selectedDate = now
            today = now
        }
    }

    fun selectLocation(city: City) {
        picked = city
        targetTime = null
        // Another time zone may already be on another day
        val newToday = city.today()
        if (newToday != today) {
            today = newToday
            selectedDate = newToday
        }
    }

    /** Back to the user's own place. */
    fun clearLocation() {
        picked = null
        refreshToday()
    }

    /** Shows what [other] shows: its place, its day and its moment (a tool opened from a Home widget). */
    internal fun showSameAs(other: HomeWidgetsState) {
        if (other === this) return
        if (playingMillis != null) setPlaying(null)
        picked = other.picked
        today = other.today
        selectedDate = other.selectedDate
        targetTime = other.targetTime
    }

    /** Back to the user's own place, today, now: what a launcher widget shows (a tool opened from one). */
    internal fun showUserToday() {
        if (playingMillis != null) setPlaying(null)
        // The region as saved now: the tools may not have been composed since it changed
        userCity = app.prefs.userCityFlow.value
        picked = null
        today = city.today()
        selectedDate = today
        targetTime = null
    }

    internal fun updateUser(city: City, community: String) {
        if (city != userCity) {
            userCity = city
            refreshToday()
        }
        this.community = community
    }
}

private const val DAY_MILLIS = 86_400_000.0

/** The tools' one state for the app (desktop ToolWindows: tool windows share one HomeWidgetsState). */
private var toolsState: HomeWidgetsState? = null

/**
 * The state of a Home page, following the user's region and community; widgets open books in a new tab. One per Home
 * composition, as desktop's (remember in HomeBody).
 */
@Composable
fun rememberHomeWidgetsState(
    /** Opens a tool where the Home wants it (a panel on a tablet), true when it did; else the tool's page opens. */
    openTool: (ToolKind) -> Boolean = { false },
): HomeWidgetsState {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val state = remember(app) { newState(app) }
    state.followUser(app)
    val appVm: AppViewModel = LocalAppViewModel.current ?: viewModel()
    val hostOpenTool by rememberUpdatedState(openTool)
    SideEffect {
        state.openBook = { book, lineId -> appVm.openInNewTab(Destination.Book(book.id, lineId, book.title), book.title) }
        state.openPassage = { book, lineId, endLineId, sm ->
            appVm.openInNewTab(Destination.Book(book.id, lineId, book.title, endLineId = endLineId, shnayimMikra = sm), book.title)
        }
        state.openPage = { destination -> appVm.showPage(destination) }
        state.openSearch = { search -> appVm.openInNewTab(search, search.query) }
        state.openTool = { tool ->
            // The tool shows what the widget showed: the same place, day and moment
            toolsStateOf(app).showSameAs(state)
            if (!hostOpenTool(tool)) appVm.push(Overlay.Tool(tool))
        }
    }
    return state
}

private fun toolsStateOf(app: ZayitApplication): HomeWidgetsState = toolsState ?: newState(app).also { toolsState = it }

/**
 * Main thread: the tools back to the user's place, today and now, before a launcher widget opens one, as the Home's
 * widgets make it show what they showed ([HomeWidgetsState.showSameAs]): a day or a place picked in a tool earlier is
 * not what the launcher widget showed. Nothing to do before a tool was ever opened (a new state starts there).
 */
internal fun resetToolsStateToUser() {
    toolsState?.showUserToday()
}

/**
 * The tools' shared state (desktop rememberToolsState): a day picked in one tool shows in the others, until the app
 * ends. A book opened from a tool opens in a new tab and closes the tool pages.
 */
@Composable
fun rememberToolsState(): HomeWidgetsState {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val state = remember(app) { toolsStateOf(app) }
    state.followUser(app)
    val appVm: AppViewModel = LocalAppViewModel.current ?: viewModel()
    SideEffect {
        state.openBook = { book, lineId ->
            appVm.openInNewTab(Destination.Book(book.id, lineId, book.title), book.title)
            repeat(appVm.state.value.overlays.size) { appVm.pop() }
        }
        state.openPassage = { book, lineId, endLineId, sm ->
            appVm.openInNewTab(Destination.Book(book.id, lineId, book.title, endLineId = endLineId, shnayimMikra = sm), book.title)
            repeat(appVm.state.value.overlays.size) { appVm.pop() }
        }
        state.openPage = { destination ->
            appVm.showPage(destination)
            repeat(appVm.state.value.overlays.size) { appVm.pop() }
        }
        state.openSearch = { search ->
            appVm.openInNewTab(search, search.query)
            repeat(appVm.state.value.overlays.size) { appVm.pop() }
        }
        state.openTool = { tool -> appVm.push(Overlay.Tool(tool)) }
    }
    return state
}

private fun newState(app: ZayitApplication): HomeWidgetsState {
    LimudResources.install(app)
    PlatformInfo.install(app)
    return HomeWidgetsState(app, HomeWidgetsLayout(HomeWidgetStore.get(app)))
}

@Composable
private fun HomeWidgetsState.followUser(app: ZayitApplication) {
    val city by app.prefs.userCityFlow.collectAsState()
    val community by app.prefs.communityFlow.collectAsState()
    SideEffect { updateUser(city, community) }
    // The day rolls over while the page stays open
    val minute = rememberMinute()
    LaunchedEffect(minute) { refreshToday() }
}

/**
 * [compute] for [keys], run off the UI thread, the first time too: null until that first value is ready (the card shows
 * [WidgetCardLoading] meanwhile), so opening the Home never waits for its calendars. Then the last value stays shown
 * until the new one is ready (desktop rememberOffMain). Desktop lets a failure through; here it is logged and the value
 * stays null (the card's spinner stops by itself).
 *
 * With a [tag] (one per call site), the value is also kept for the app's life with its keys, as the tools'
 * rememberToolValue: a Home built again (a new tab, one the app had let go) shows it on its first frame, not a spinner
 * that fills a moment later, as long as the keys are the same.
 */
@Composable
fun <T> rememberOffMain(
    vararg keys: Any?,
    tag: String? = null,
    compute: () -> T,
): T? {
    val latest by rememberUpdatedState(compute)
    val keyList = keys.toList()
    val cached = tag?.let { OffMainValues.cached<T>(it, keyList) }
    return produceState(cached, *keys) {
        value = tag?.let { OffMainValues.cached<T>(it, keyList) }
            ?: withContext(Dispatchers.Default) { runCatching { latest() }.onFailure { Log.w("Zayit", "widget value", it) }.getOrNull() }
                ?.also { if (tag != null) OffMainValues.keep(tag, keyList, it) }
    }.value
}

/** The last value of each tagged [rememberOffMain] call site, with its keys. Main thread only. */
private object OffMainValues {
    private val values = HashMap<String, Pair<List<Any?>, Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> cached(
        tag: String,
        keys: List<Any?>,
    ): T? = values[tag]?.takeIf { it.first == keys }?.second as T?

    fun keep(
        tag: String,
        keys: List<Any?>,
        value: Any,
    ) {
        values[tag] = keys to value
    }
}

/**
 * Now, updated on every new minute (desktop LuachWidgets.rememberMinute). Not while its tab is in the background or its
 * Home is parked behind a book (TabHost): nobody sees it then, and it is brought up to date as it is shown again.
 */
@Composable
fun rememberMinute(): Date {
    val front = isTabInFront()
    val now by produceState(Date(), front) {
        if (!front) return@produceState
        value = Date()
        while (true) {
            kotlinx.coroutines.delay(60_000 - System.currentTimeMillis() % 60_000)
            value = Date()
        }
    }
    return now
}

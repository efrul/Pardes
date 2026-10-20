package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind

/**
 * A Home widget, placed by [HomeWidgetsGrid] on an area of a grid of [HOME_GRID_COLUMNS] columns, as on Android's home
 * screen. Widgets never talk to each other: they read and write the shared [HomeWidgetsState], so any of them can be
 * moved, resized or removed. Port of desktop 0.6.1 features/home/widgets/HomeWidget.kt; a tool's own window
 * (toolWindowSize) is the [tool] page here.
 */
interface HomeWidget {
    /** Stable key, for persisting the user's widget order (desktop's ids, so a layout reads the same on both). */
    val id: String

    /** Its name in the widget gallery. */
    val title: String

    /** Its size when added, in cells. */
    val defaultSpan: CellSpan

    /** How small and how large the user can resize it, in cells (Android's minResizeWidth, maxResizeWidth…). */
    val minSpan: CellSpan get() = defaultSpan
    val maxSpan: CellSpan get() = CellSpan(HOME_GRID_COLUMNS, defaultSpan.h * 2)

    /** The height its content needs at [width], when it decides it: it can't be made shorter. */
    fun heightAt(width: Dp): Dp? = null

    /** Its own settings, read and written through [HomeWidgetsState]; reset together from its options. */
    val settings: WidgetSettings? get() = null

    /** Its settings as saved, given at the start and on each change, for a widget whose size follows them (its [heightAt]). */
    fun applySettings(store: HomeWidgetStore) {}

    val isSupported: Boolean get() = true

    /** The tool page that shows it alone, from the Tools menu; null for a widget only. */
    val tool: ToolKind? get() = null

    /** Fills the size the host gives it through [modifier]. */
    @Composable
    fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    )

    /** Its picture in the gallery; the live content by default. */
    @Composable
    fun Preview(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = Content(state, modifier)

    /** Its own items atop its menu (a long press): actions, check marks; none by default. */
    @Composable
    fun menuItems(state: HomeWidgetsState): List<WidgetMenuItem> = emptyList()

    /** Its settings page, for an item of its menu to open (see [HomeWidgetsState.optionsOpen]). */
    @Composable
    fun Options(state: HomeWidgetsState) {}
}

/** An item of a widget's own menu: [checked] shows a check mark, null a plain action. */
@Immutable
data class WidgetMenuItem(
    val label: String,
    val checked: Boolean? = null,
    val onClick: () -> Unit = {},
)

/** A widget on the Home grid, on [cell]. */
@Immutable
data class WidgetPlacement(
    val widget: HomeWidget,
    val cell: CellRect,
)

/** Desktop framework/platform/PlatformInfo.isLowEnd, on Android: a low-RAM (Android Go) device. */
object PlatformInfo {
    @Volatile
    var isLowEnd: Boolean = false
        private set

    fun install(context: Context) {
        isLowEnd = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
}

/**
 * Every widget the user can place, shown by default or not: desktop's widgetprocessor writes this list from every
 * HomeWidget object, by package then by name (a gallery grouped by theme); there is no KSP here, so it is written out.
 */
val availableHomeWidgets: List<HomeWidget> by lazy {
    listOf(
        CalendarWidget,
        EarthWidget,
        DictionaryWidget,
        FavoritesWidget,
        HistoryWidget,
        LearningSplitWidget,
        NotesWidget,
        LimudWidget,
        MoladWidget,
        NextZmanWidget,
        ShnayimMikraWidget,
        TefilaWidget,
        UpcomingEventsWidget,
        MeasuresWidget,
        SkyWidget,
        SolarSystemWidget,
        TempleCountdownWidget,
        ZmanimWidget,
    )
}

/** The default Home layout: the zmanim and the Earth, then the Temple, the solar system and the sky. */
val homeWidgets: List<WidgetPlacement>
    get() =
        listOf(
            WidgetPlacement(ZmanimWidget, CellRect(0, 0, 13, 4)),
            WidgetPlacement(EarthWidget, CellRect(13, 0, 7, 4)),
            WidgetPlacement(TempleCountdownWidget, CellRect(0, 4, 6, 3)),
            WidgetPlacement(SolarSystemWidget, CellRect(6, 4, 9, 3)),
            WidgetPlacement(SkyWidget, CellRect(15, 4, 5, 3)),
        )

/** The default Home layout of a low-end machine, which can't run the heavy widgets: the zmanim and the Temple. */
val lowEndHomeWidgets: List<WidgetPlacement>
    get() =
        listOf(
            WidgetPlacement(ZmanimWidget, CellRect(0, 0, 12, 4)),
            WidgetPlacement(TempleCountdownWidget, CellRect(12, 0, 7, 4)),
        )

/** [lowEndHomeWidgets] on a low-end machine, [homeWidgets] elsewhere. */
internal fun defaultLayout(lowEnd: Boolean = PlatformInfo.isLowEnd): List<WidgetPlacement> = if (lowEnd) lowEndHomeWidgets else homeWidgets

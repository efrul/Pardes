package io.github.kdroidfilter.seforimapp.android.ui.tools

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.Cities
import io.github.kdroidfilter.seforimapp.android.data.City
import io.github.kdroidfilter.seforimapp.android.data.ZmanimOpinion
import io.github.kdroidfilter.seforimapp.android.data.ZmanimTimes
import io.github.kdroidfilter.seforimapp.android.data.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.android.data.hebrewFormatter
import io.github.kdroidfilter.seforimapp.android.data.hebrewWeekday
import io.github.kdroidfilter.seforimapp.android.data.worldPlaces
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.Overlay
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.LimudWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.LuachPicker
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.WidgetOptions
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalAppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.shell.PhoneHeader
import io.github.kdroidfilter.seforimapp.android.ui.shell.WideBreakpoint
import io.github.kdroidfilter.seforimapp.android.ui.shell.WindowBackScope
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinLocalDate
import java.time.DayOfWeek
import java.time.LocalDate

// The frame of the tools: desktop opens each one alone in a window of its own (features/home/widgets/ToolWindows.kt,
// HomeWidget.toolWindowSize), the widget's content filling it. Here a tool is a page on a phone, and on a tablet the
// small ones are a panel over the screen at desktop's window size (ToolPanelDialog); the tools share one state, the
// day and the place they show (desktop rememberToolsState).

/** Lets a tool open a book in a new tab (closing the tool pages) or switch to another tool. */
class ToolNav(private val appVm: AppViewModel) {
    fun openBook(book: Book, lineId: Long?) {
        appVm.openInNewTab(Destination.Book(book.id, lineId, book.title), book.title)
        repeat(appVm.state.value.overlays.size) { appVm.pop() }
    }

    fun openTool(kind: ToolKind) = appVm.push(Overlay.Tool(kind))
}

@Composable
fun rememberToolNav(): ToolNav {
    val appVm: AppViewModel = LocalAppViewModel.current ?: viewModel()
    return remember(appVm) { ToolNav(appVm) }
}

/** Android-only tool preferences of older builds (Shnayim Mikra ticks before the user DB had them). */
fun toolPrefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences("zayit_tools", Context.MODE_PRIVATE)

/** The user's place from the region settings (desktop HomeUserLocation), following changes made in Settings. */
@Composable
fun rememberUserCity(): City {
    val app = LocalContext.current.applicationContext as ZayitApplication
    return app.prefs.userCityFlow.collectAsState().value
}

/** The luach of the user's community (desktop HomeWidgetsState.zmanimOpinion), following changes made in Settings. */
@Composable
fun rememberZmanimOpinion(): ZmanimOpinion {
    val app = LocalContext.current.applicationContext as ZayitApplication
    return app.prefs.zmanimOpinion.collectAsState().value
}

// More than a session of looking around the tools needs, little enough to keep for the app's life
private const val MAX_TOOL_VALUES = 64

/**
 * The tools' values worked out off the UI thread, by a tag and their inputs, kept while the app runs. A tool page is
 * rebuilt each time it opens (desktop keeps the tool's window, and its values, while it is open): with these it shows
 * its content on its first frame again, not placeholders that fill a moment later. Main thread only: read in composition,
 * written once withContext is back.
 */
private object ToolValues {
    private val values =
        object : LinkedHashMap<List<Any?>, Any>(MAX_TOOL_VALUES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Any?>, Any>?): Boolean = size > MAX_TOOL_VALUES
        }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> cached(key: List<Any?>): T? = values[key] as T?

    suspend fun <T : Any> load(
        key: List<Any?>,
        compute: () -> T,
    ): T? = cached<T>(key) ?: withContext(Dispatchers.Default) { runCatching { compute() }.getOrNull() }?.also { values[key] = it }
}

/**
 * [compute] for [keys] off the UI thread, as the home's rememberOffMain (desktop's), but kept under [tag] for the next
 * opening: null only until the first value for these inputs is ready (or if it fails), then the last value stays shown
 * until the new one is. A [tag] names one computation, so its keys always lead to the same type.
 */
@Composable
internal fun <T : Any> rememberToolValue(
    tag: String,
    vararg keys: Any?,
    compute: () -> T,
): T? {
    val key = listOf(tag, *keys)
    val latest by rememberUpdatedState(compute)
    val cached = ToolValues.cached<T>(key)
    val produced = produceState(cached, key) { value = ToolValues.load(key) { latest() } }
    return cached ?: produced.value
}

/** The day's zmanim at [city] by [opinion], kept as [rememberToolValue]: the siddur, the day's tefila and the rest share them. */
@Composable
internal fun rememberToolZmanim(
    date: LocalDate,
    city: City,
    opinion: ZmanimOpinion,
): ZmanimTimes? = rememberToolValue(ZMANIM_TAG, date, city, opinion) { computeZmanimTimes(date, city.location, opinion, city.inIsrael) }

/** [rememberToolZmanim] for code outside composition. */
internal suspend fun toolZmanim(
    date: LocalDate,
    city: City,
    opinion: ZmanimOpinion,
): ZmanimTimes? = ToolValues.load(listOf(ZMANIM_TAG, date, city, opinion)) { computeZmanimTimes(date, city.location, opinion, city.inIsrael) }

private const val ZMANIM_TAG = "zmanim"

/** A phone-sized window: pickers and options open as bottom sheets there, as popups and dialogs elsewhere. */
@Composable
internal fun isPhoneWindow(): Boolean = LocalConfiguration.current.screenWidthDp < 600

/**
 * Desktop's tool window size (HomeWidget.toolWindowSize) of the tools that open as a centred panel on a tablet; null
 * for the ones that keep a page of their own (the siddur, the solar system, the zmanim).
 */
internal val ToolKind.panelSize: DpSize?
    get() =
        when (this) {
            ToolKind.HebrewCalendar -> DpSize(420.dp, 460.dp)
            ToolKind.Dictionary -> DpSize(460.dp, 560.dp)
            ToolKind.DailyStudy -> DpSize(380.dp, 240.dp)
            ToolKind.Molad -> DpSize(280.dp, 200.dp)
            ToolKind.ShnayimMikra -> DpSize(320.dp, 360.dp)
            ToolKind.DailyPrayer -> DpSize(380.dp, 320.dp)
            ToolKind.Measures -> DpSize(400.dp, 400.dp)
            else -> null
        }

/** Whether [tool] opens as a panel in a window [wide] enough for it. */
internal fun opensAsPanel(
    tool: ToolKind,
    wide: Boolean,
) = wide && tool.panelSize != null

/** True inside a [ToolPanelDialog]: [ToolPage] draws the panel's own small title bar instead of the page header. */
internal val LocalToolPanel = staticCompositionLocalOf<(() -> Unit)?> { null }

// Desktop's title bar and TOOL_PADDING around a tool's content
private val PANEL_TITLE_HEIGHT = 44.dp
private val PANEL_PADDING = 8.dp

// The line under the panel's title bar (desktop's title bar has none): counted in a height worked out from the content
private val PANEL_DIVIDER = 1.dp

/**
 * [tool] alone in a centred panel of desktop's window size (DefaultToolWindow): its title bar with a close button, its
 * content; the tools' options open over it. A tool whose content decides its height (the limud, its heightAt) gets just
 * that.
 */
@Composable
internal fun ToolPanelDialog(
    tool: ToolKind,
    onClose: () -> Unit,
) {
    val size = tool.panelSize ?: return
    val c = Zayit.colors
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        WindowBackScope {
            val shape = RoundedCornerShape(12.dp)
            // Desktop's size, within 95% of the screen. Not fillMax*(0.95f) after the max: that took 5% off the desktop
            // size too, and the limud's exact height then lost its last row
            val screen = LocalConfiguration.current
            val width = min(size.width, screen.screenWidthDp.dp * 0.95f)
            val contentHeight = if (tool == ToolKind.DailyStudy) LimudWidget.heightAt(width - PANEL_PADDING * 2) else null
            val height = min(contentHeight?.let { it + PANEL_TITLE_HEIGHT + PANEL_DIVIDER + PANEL_PADDING * 2 } ?: size.height, screen.screenHeightDp.dp * 0.95f)
            CompositionLocalProvider(LocalToolPanel provides onClose) {
                Box(
                    Modifier
                        .size(width, height)
                        .clip(shape)
                        // Desktop's tool window: the panel's background around the widget's card
                        .background(c.panel)
                        .border(1.dp, c.panelBorder, shape),
                ) {
                    ToolContent(tool, onClose)
                }
            }
        }
    }
}

/**
 * The panel of [tool] while it is set: it closes on its own when the app goes elsewhere (a book opened from it, another
 * tool, a tab), as its desktop window leaves the main window's way.
 */
@Composable
internal fun ToolPanelHost(
    tool: ToolKind?,
    onClose: () -> Unit,
) {
    if (tool == null) return
    val appVm: AppViewModel = LocalAppViewModel.current ?: viewModel()
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(tool, appVm) {
        val start = appVm.state.value
        appVm.state.first { it.selectedTab.id != start.selectedTab.id || it.overlays != start.overlays || it.tabs.size != start.tabs.size }
        close()
    }
    ToolPanelDialog(tool, onClose)
}

/**
 * Full-screen tool page: the shared header, then the content (above the navigation bar) in a box that knows whether
 * the window is wide (≥ 840dp). Inside a [ToolPanelDialog], the panel's title bar and its padding instead.
 */
@Composable
fun ToolPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable (wide: Boolean) -> Unit,
) {
    val c = Zayit.colors
    val panelClose = LocalToolPanel.current
    if (panelClose != null) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(PANEL_TITLE_HEIGHT).background(c.panel).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                actions()
                ZIconButton(Icons.Outlined.Close, "סגור", panelClose, tint = c.textSecondary)
            }
            HorizontalDivider(thickness = PANEL_DIVIDER, color = c.divider)
            Box(Modifier.weight(1f).fillMaxWidth().padding(PANEL_PADDING)) { content(false) }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(c.ground)) {
        PhoneHeader(title, onBack, actions)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
            content(maxWidth >= WideBreakpoint)
        }
    }
}

/** Whether the tool is drawn in a tablet panel ([ToolPanelDialog]), where only its desktop content shows. */
@Composable
internal fun inToolPanel(): Boolean = LocalToolPanel.current != null

/** The widget options a tool's gear opened (desktop ToolWindowsHost's WidgetOptionsDialog). */
@Composable
internal fun ToolOptionsHost(state: HomeWidgetsState) {
    val widget = state.optionsOpen ?: return
    WidgetOptions(widget, state, sheet = isPhoneWindow(), onClose = { state.optionsOpen = null })
}

/** One tool's content with its frame, as a page or in a panel; the dispatcher of [ToolScreen] and the panels. */
@Composable
internal fun ToolContent(
    tool: ToolKind,
    onBack: () -> Unit,
) {
    when (tool) {
        ToolKind.Siddur -> SiddurTool(onBack)
        ToolKind.HebrewCalendar -> HebrewCalendarTool(onBack)
        ToolKind.Dictionary -> DictionaryTool(onBack)
        ToolKind.DailyStudy -> DailyStudyTool(onBack)
        ToolKind.Molad -> MoladTool(onBack)
        ToolKind.ShnayimMikra -> ShnayimMikraTool(onBack)
        ToolKind.DailyPrayer -> DailyPrayerTool(onBack)
        ToolKind.Measures -> MeasuresTool(onBack)
        ToolKind.YearCycle -> YearCycleTool(onBack)
        ToolKind.Zmanim -> ZmanimTool(onBack)
    }
    ToolOptionsHost(rememberToolsState())
}

/** Scrolling column centred at a readable width. */
@Composable
fun ToolColumn(
    maxWidth: Dp = 760.dp,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** Card with a bold title, an optional subtitle and trailing actions (the Android extras under a tool's content). */
@Composable
fun ToolCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.panelBorder, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.width(8.dp))
                Text(subtitle, fontSize = 13.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }
        content()
    }
}

@Composable
fun GearButton(onClick: () -> Unit) {
    ZIconButton(Icons.Outlined.Settings, "אפשרויות", onClick, tint = Zayit.colors.textSecondary)
}

/** Accent-tinted label chip. */
@Composable
fun LabelChip(
    text: String,
    modifier: Modifier = Modifier,
    minWidth: Dp = 64.dp,
) {
    val c = Zayit.colors
    Box(
        modifier
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(8.dp))
            .background(c.accentTint)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** "label   value" line: secondary label on the reading side, value after it. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 110.dp,
    valueColor: Color = Zayit.colors.text,
    valueWeight: FontWeight = FontWeight.Normal,
    valueSize: TextUnit = 16.sp,
) {
    val c = Zayit.colors
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.width(labelWidth).padding(top = 2.dp))
        Text(value, color = valueColor, fontSize = valueSize, fontWeight = valueWeight, modifier = Modifier.weight(1f))
    }
}

/** Underlined source citation that opens the book. */
@Composable
fun SourceLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    Text(
        text,
        color = c.textSecondary,
        fontSize = 13.sp,
        textDecoration = TextDecoration.Underline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 2.dp, vertical = 2.dp),
    )
}

/**
 * Desktop siddur BookHeader's day label: "יום שלישי, כ״ב תשרי תשפ״ז · ראש חודש", the day's yom tov and Rosh Chodesh
 * after it.
 */
internal fun toolDayLabel(
    date: LocalDate,
    inIsrael: Boolean,
): String {
    val day = JewishCalendar(date.toKotlinLocalDate(), inIsrael)
    val weekday = if (date.dayOfWeek == DayOfWeek.SATURDAY) "שבת" else "יום ${date.hebrewWeekday()}"
    val label = "$weekday, ${hebrewFormatter.format(day)}"
    val occasion =
        listOf(hebrewFormatter.formatYomTov(day), hebrewFormatter.formatRoshChodesh(day))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
    return if (occasion.isEmpty()) label else "$label · $occasion"
}

/**
 * The day the tools show (desktop siddur DayNavigator): the day before, the day itself — a tap opens the luach to pick
 * another or come back to today (LuachPopup) — and the day after. It moves the tools' shared day, so every tool
 * follows; [showCity] adds the place, picked for a look around (never written to the settings). [step] days a press.
 */
@Composable
internal fun ToolDayBar(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
    showCity: Boolean = false,
    step: Long = 1,
    previousLabel: String = "היום הקודם",
    nextLabel: String = "היום הבא",
) {
    DayNavigator(
        date = state.shownDate,
        today = state.todayDate,
        inIsrael = state.inIsrael,
        onDay = state::selectDate,
        onToday = state::backToToday,
        modifier = modifier,
        away = state.shownDate != state.todayDate || state.targetTime != null,
        step = step,
        previousLabel = previousLabel,
        nextLabel = nextLabel,
        extra = if (showCity) ({ CityPicker(state.city, onSelect = state::selectLocation) }) else null,
    )
}

/** [ToolDayBar] for any day: the siddur's own one (desktop's siddur tab keeps its day). */
@Composable
internal fun DayNavigator(
    date: LocalDate,
    today: LocalDate,
    inIsrael: Boolean,
    onDay: (LocalDate) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
    away: Boolean = date != today,
    step: Long = 1,
    previousLabel: String = "היום הקודם",
    nextLabel: String = "היום הבא",
    extra: (@Composable () -> Unit)? = null,
) {
    val c = Zayit.colors
    val label = remember(date, inIsrael) { toolDayLabel(date, inIsrael) }
    var picking by remember { mutableStateOf(false) }
    val sheet = isPhoneWindow()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.panel)
            .border(1.dp, c.panelBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // In a right-to-left page the previous day is on the right
            ZIconButton(Icons.Outlined.ChevronRight, previousLabel, { onDay(date.minusDays(step)) })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { picking = !picking }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(label, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("⁦${date.dayOfMonth}.${date.monthValue}.${date.year}⁩", color = c.textSecondary, fontSize = 12.sp, maxLines = 1)
                }
                if (picking) {
                    LuachPicker(
                        selected = date,
                        inIsrael = inIsrael,
                        onSelect = onDay,
                        onDismiss = { picking = false },
                        sheet = sheet,
                        today = today,
                        onToday = onToday,
                    )
                }
            }
            ZIconButton(Icons.Outlined.ChevronLeft, nextLabel, { onDay(date.plusDays(step)) })
        }
        if (away || extra != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                extra?.invoke()
                if (away) {
                    Text(
                        "חזרה להיום",
                        color = c.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onToday).heightIn(min = 36.dp).padding(horizontal = 8.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

/**
 * Another place to look at, never written to the region settings: desktop's globe location menu
 * (EarthWidgetZmanimView LocationMenuContent, 320 × 260), the countries and the chosen country's cities side by side.
 * A bottom sheet on a phone. [dark] draws its button over the Earth widget's black.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPicker(
    city: City,
    onSelect: (City) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val c = Zayit.colors
    var open by remember { mutableStateOf(false) }
    val sheet = isPhoneWindow()
    val fg = if (dark) Color.White else c.text
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, if (dark) Color.White.copy(alpha = 0.35f) else c.panelBorder, RoundedCornerShape(10.dp))
                .background(if (dark) Color.Black.copy(alpha = 0.55f) else c.panel)
                .clickable(role = Role.Button) { open = true }
                .heightIn(min = 36.dp)
                .widthIn(max = 200.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.LocationOn, null, tint = if (dark) Color.White else c.accent, modifier = Modifier.size(16.dp))
            Text(city.name.ifBlank { city.country }, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = fg, modifier = Modifier.size(16.dp))
        }
        // Only while its tab is in front: a sheet is its own window, and stayed over a tab opened from outside
        if (open && isTabInFront()) {
            val lists: @Composable (Modifier) -> Unit = { mod ->
                var country by remember { mutableStateOf(city.country.takeIf { it in worldPlaces } ?: worldPlaces.keys.first()) }
                Row(mod, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocationList(worldPlaces.keys.toList(), country, { country = it }, Modifier.weight(1f))
                    VerticalDivider(color = c.divider)
                    LocationList(worldPlaces[country]?.keys?.toList().orEmpty(), city.name.takeIf { city.country == country }, { name ->
                        open = false
                        Cities.firstOrNull { it.country == country && it.name == name }?.let(onSelect)
                    }, Modifier.weight(1f))
                }
            }
            if (sheet) {
                ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.panel) {
                    lists(Modifier.fillMaxWidth().height(380.dp).navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 12.dp))
                }
            } else {
                DropdownMenu(expanded = true, onDismissRequest = { open = false }, containerColor = c.panel) {
                    lists(Modifier.width(320.dp).height(260.dp).padding(horizontal = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun LocationList(
    items: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    val c = Zayit.colors
    Column(modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Text(
                item,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = if (item == selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (item == selected) c.selectedRow else Color.Transparent)
                    .clickable { onSelect(item) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

/** Generic dropdown field (unit pickers, nusach…). */
@Composable
fun <T> DropdownField(
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, c.panelBorder, RoundedCornerShape(10.dp))
                .background(c.panel)
                .clickable(role = Role.Button) { open = true }
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(value), color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.panel) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), color = c.text, fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** Two equal columns on wide windows, stacked otherwise. */
@Composable
fun ColumnScope.AdaptiveColumns(
    wide: Boolean,
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
) {
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = first)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = second)
        }
    } else {
        first()
        second()
    }
}

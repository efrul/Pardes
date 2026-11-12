package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCardLoading
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetMenuItem
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.home.widgets.shownDateHere
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_study_plans
import java.time.LocalDate
import java.util.UUID
import java.util.WeakHashMap

// Pardes's own widget (request 19 of docs/DESKTOP_STAGE2.md, after the mock-up the user approved on 2026-11-12): the
// user's daily study plans, each with the day's part, its pace and a box to tick once it is done; a click opens the
// book on the part, marked through its end. The plans are kept in the user DB (core/study/StudyPlanStore.kt) and edited
// in StudyPlanEditor.kt. Ideas only from Otzaria's "לשקוד" add-on, which has no licence.

/** A plan as the card shows it on a day: its book in this library (null: not there), its units and the day's part. */
@Immutable
internal data class ShownPlan(
    val plan: StudyPlan,
    val book: Book?,
    val name: UnitName,
    val units: List<StudyUnit>,
    val part: DayPart,
)

/** [plan]'s book in this library: by its id while it has the same title, else by its title. */
internal suspend fun SeforimRepository.bookOf(plan: StudyPlan): Book? =
    getBookCore(plan.bookId)?.takeIf { it.title == plan.bookTitle } ?: getBookByTitle(plan.bookTitle)

/** [plan] on [day], in Israel or abroad ([inIsrael]): its book read from the library, the day's part reckoned. */
internal suspend fun SeforimRepository.shownPlan(
    plan: StudyPlan,
    day: LocalDate,
    inIsrael: Boolean,
): ShownPlan {
    val book = bookOf(plan) ?: return ShownPlan(plan, null, UnitName("יחידה", "יחידות"), emptyList(), DayPart.Finished)
    val entries = getTocEntriesForBook(book.id)
    val units = studyUnits(entries, plan.level)
    val name = unitNameOf(entries.filter { it.level == plan.level }.map { it.text })

    fun studyDay(date: LocalDate) = isStudyDay(date, plan.weekdays, inIsrael)
    val before = day < plan.startDay
    val part =
        dayPart(
            startIndex = startIndexOf(units, plan.start),
            count = units.size,
            perDay = plan.perDay,
            restart = plan.restart,
            daysBefore = if (before) 0 else studyDaysBetween(plan.startDay, day, ::studyDay),
            studyDay = studyDay(day),
            beforeStart = before,
        )
    return ShownPlan(plan, book, name, units, part)
}

/** Where a part is in its book: the line its first unit starts on, and the last line of its last one. */
internal suspend fun SeforimRepository.linesOf(
    book: Book,
    units: List<StudyUnit>,
    indexes: List<Int>,
): Pair<Long?, Long?> {
    val first = units.getOrNull(indexes.first()) ?: return null to null
    // A part across the book's end is marked to the end
    val last = units[indexes.last()].takeIf { indexes.last() >= indexes.first() }
    val endIndex = (last?.nextLineId?.let { getLine(it)?.lineIndex }?.minus(1) ?: (book.totalLines - 1)).coerceAtLeast(0)
    return first.lineId to getLineByIndex(book.id, endIndex.coerceAtMost(book.totalLines - 1))?.id
}

// The plan being edited from each Home (a window's), a new one with id 0: Home's state is Zayit's, so it is kept here
private val editing = WeakHashMap<HomeWidgetsState, MutableState<StudyPlan?>>()

internal val HomeWidgetsState.editedPlan: MutableState<StudyPlan?> get() = editing.getOrPut(this) { mutableStateOf(null) }

/** A new plan, from [day] on, every day but Shabbat and Yom Tov, its book still to choose. */
internal fun newPlan(day: LocalDate): StudyPlan =
    StudyPlan(
        bookId = 0,
        bookTitle = "",
        level = 1,
        start = emptyList(),
        startDay = day,
        weekdays =
            java.time.DayOfWeek.entries
                .toSet() - java.time.DayOfWeek.SATURDAY,
    )

/** The user's study plans: the day's part of each, a tick once it is learnt, a click to learn it. */
internal object StudyWidget : HomeWidget {
    override val id = "study_plans"
    override val title = Res.string.home_widget_name_study_plans
    override val defaultSpan = CellSpan(6, 4)
    override val minSpan = CellSpan(4, 3)
    override val maxSpan = CellSpan(12, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val graph = LocalAppGraph.current
        val store = graph.studyPlanStore
        val revision by store.revision.collectAsState()
        val day = state.shownDateHere()
        val inIsrael = state.inIsrael
        val plans by produceState<List<ShownPlan>?>(null, revision, day, inIsrael) {
            // No library yet (being installed or replaced): the plans without their parts
            value =
                runSuspendCatching { store.plans().map { graph.repository.shownPlan(it, day, inIsrael) } }.getOrNull()
                    ?: emptyList()
        }
        val done by produceState(emptySet<Long>(), revision, day) { value = store.doneOn(day) }
        val shown = plans ?: return WidgetCardLoading(modifier)
        val scope = rememberCoroutineScope()
        StudyCard(
            plans = shown,
            done = done,
            today = day == LocalDate.now(),
            onDone = { plan, checked -> scope.launch { store.setDone(plan.id, day, checked) } },
            onOpen = { plan ->
                val book = plan.book
                val part = plan.part
                if (book != null && part is DayPart.Part) {
                    scope.launch {
                        val (lineId, endLineId) = graph.repository.linesOf(book, plan.units, part.indexes)
                        state.openTab(
                            TabsDestination.BookContent(
                                bookId = book.id,
                                tabId = UUID.randomUUID().toString(),
                                lineId = lineId,
                                endLineId = endLineId,
                            ),
                        )
                    }
                }
            },
            onEdit = { state.editedPlan.value = it },
            onNew = { state.editedPlan.value = newPlan(LocalDate.now()) },
            modifier = modifier,
        )
    }

    @Composable
    override fun menuItems(state: HomeWidgetsState) =
        listOf(WidgetMenuItem("תוכנית חדשה…", icon = AllIconsKeys.General.Add) { state.editedPlan.value = newPlan(LocalDate.now()) })

    @Composable
    override fun Detached(state: HomeWidgetsState) {
        val edited by state.editedPlan
        edited?.let { plan -> StudyPlanEditor(plan, state, onClose = { state.editedPlan.value = null }) }
    }
}

/** The card: its title, a row for each plan, and the link to a new one; a word on what it is for while it has none. */
@Composable
internal fun StudyCard(
    plans: List<ShownPlan>,
    done: Set<Long>,
    today: Boolean,
    onDone: (StudyPlan, Boolean) -> Unit,
    onOpen: (ShownPlan) -> Unit,
    onEdit: (StudyPlan) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = rememberAccentColor(JewelTheme.isDark)
    val info = JewelTheme.globalColors.text.info
    PanelCard(modifier) {
        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.size(8.dp))
                Text("הלימוד שלי", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            if (plans.isEmpty()) {
                Text(
                    "עוד אין כאן תוכניות. בוחרים ספר, כמה לומדים ביום ובאילו ימים, והחלק של כל יום מופיע כאן.",
                    fontSize = 12.sp,
                    color = info,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                plans.forEach { plan ->
                    PlanRow(plan, plan.plan.id in done, today, accent, onDone, onOpen, onEdit)
                }
            }
            HoverBox(onClick = onNew, modifier = Modifier.testTag("study-new-plan")) {
                Text(
                    "+ תוכנית חדשה",
                    fontSize = 13.sp,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** One plan: the box ticked once the day's part is learnt, the book, the part, its pace and, while learning, how far. */
@Suppress("LongParameterList") // the row's data and its three actions
@Composable
private fun PlanRow(
    plan: ShownPlan,
    isDone: Boolean,
    today: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onDone: (StudyPlan, Boolean) -> Unit,
    onOpen: (ShownPlan) -> Unit,
    onEdit: (StudyPlan) -> Unit,
) {
    val info = JewelTheme.globalColors.text.info
    val part = plan.part
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(hover)
            .padding(vertical = 4.dp)
            .testTag("study-plan-${plan.plan.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = isDone,
            onCheckedChange = { onDone(plan.plan, it) },
            enabled = part is DayPart.Part,
            modifier = Modifier.padding(top = 2.dp),
        )
        HoverBox(
            onClick = if (part is DayPart.Part) ({ onOpen(plan) }) else null,
            modifier = Modifier.weight(1f).alpha(if (isDone) 0.55f else 1f),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(
                    plan.book?.title ?: plan.plan.bookTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(partText(plan), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(metaText(plan, isDone, today), fontSize = 12.sp, color = info, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (part is DayPart.Part && !isDone) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(0.6f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(info.copy(alpha = 0.25f)),
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(part.progress).background(accent))
                    }
                }
            }
        }
        IconButton(
            onClick = { onEdit(plan.plan) },
            modifier = Modifier.size(24.dp).alpha(if (hovered) 1f else 0f).testTag("study-edit-${plan.plan.id}"),
        ) {
            Icon(AllIconsKeys.Actions.Edit, contentDescription = "עריכת התוכנית", tint = info)
        }
    }
}

/** What the day holds: its part by headings, or why there is none. */
private fun partText(plan: ShownPlan): String =
    when (val part = plan.part) {
        is DayPart.Part -> partLabel(part.indexes.map { plan.units[it] })
        DayPart.Rest -> "אין לימוד היום"
        DayPart.NotYet -> "מתחילים ב־${plan.plan.startDay.dayOfMonth}.${plan.plan.startDay.monthValue}"
        DayPart.Finished -> if (plan.book == null) "הספר לא נמצא בספרייה" else "סיימת את הספר"
    }

/** "6 סעיפים ביום · יום 41", "5 פרקים ביום, חוץ משבת ויום טוב", "פרק ביום · סיימת להיום". */
private fun metaText(
    plan: ShownPlan,
    isDone: Boolean,
    today: Boolean,
): String {
    val pace = listOfNotNull(perDayLabel(plan.plan.perDay, plan.name), weekdaysLabel(plan.plan.weekdays)).joinToString(", ")
    val part = plan.part as? DayPart.Part ?: return pace
    return when {
        isDone -> "$pace · ${if (today) "סיימת להיום" else "סומן"}"
        else -> "$pace · יום ${part.day}"
    }
}

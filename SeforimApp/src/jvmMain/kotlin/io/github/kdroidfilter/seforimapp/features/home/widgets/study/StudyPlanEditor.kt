package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.nucleusframework.application.LocalNucleusApplicationScope
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.jewel.JewelDecoratedDialog
import dev.nucleusframework.window.jewel.JewelDialogTitleBar
import dev.nucleusframework.window.newFullscreenControls
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BookSuggestion
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.CatBookRow
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.SuggestionKind
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.SuggestionRow
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.baseBooksDetail
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.catBookRows
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.dedupAdjacent
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.luach.hebrewFormatter
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.luach.LuachDateButton
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.scope_base_books_label
import java.time.DayOfWeek
import java.time.LocalDate

// Pardes's own: the editor of a study plan (request 19 of docs/DESKTOP_STAGE2.md), as the approved mock-up has it —
// the book, the depth of its table of contents studied by, where to start, how many a day, from which date, on which
// days, and whether to start again at its end, with the day's part beneath, as the card will show it.

/** The book [title] under [categoryPath] (its categories' titles, the root first). */
private suspend fun SeforimRepository.categoryPathOf(book: Book): List<String> = pathOf(book.categoryId)

/** The titles of the category [categoryId] and those above it, the root first. */
private suspend fun SeforimRepository.pathOf(categoryId: Long): List<String> {
    val path = ArrayDeque<String>()
    var category = getCategory(categoryId)
    while (category != null) {
        path.addFirst(category.title)
        category = category.parentId?.let { getCategory(it) }
    }
    return path
}

/**
 * Pardes (request 64 of stage 3): the books and categories [text] names, as the search's bar lists them: the books its
 * lookup suggests (acronyms too: שוע), each with its categories' titles, and the categories it names, in the bar's
 * order ([catBookRows]), a few of each; not the commentaries on a book by their time, which it searches as categories.
 */
private suspend fun sourcesNamed(
    graph: AppGraph,
    text: String,
): List<CatBookRow> =
    withContext(Dispatchers.Default) {
        val repository = graph.repository
        val books =
            graph.lookupSearch.suggestBooks(text, limit = BOOK_SUGGESTIONS).map { hit ->
                BookSuggestion(hit.toBook(), repository.pathOf(hit.categoryId) + hit.title, hit.exactAcronym)
            }
        val categories = graph.libraryCategories.named(text, limit = CATEGORY_SUGGESTIONS).filter { repository.getCategory(it.id) != null }
        catBookRows(text, jumps = emptyList(), textSearch = false, authors = emptyList(), books = books, categories = categories)
            .filter { it is CatBookRow.Book || it is CatBookRow.Category }
    }

private const val BOOK_SUGGESTIONS = 6
private const val CATEGORY_SUGGESTIONS = 4

/** The editor of [plan] (a new one with id 0) in a window of its own: saved, cancelled or deleted, then [onClose]. */
@Composable
internal fun StudyPlanEditor(
    plan: StudyPlan,
    state: HomeWidgetsState,
    onClose: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val repository = graph.repository
    val store = graph.studyPlanStore
    val scope = rememberCoroutineScope()
    val inIsrael = state.inIsrael
    val fields = remember { PlanFields(plan) }
    LaunchedEffect(plan.id) {
        if (plan.id != 0L && plan.category != 0L) {
            fields.category = runSuspendCatching { repository.categoryOf(plan) }.getOrNull()
        } else if (plan.id != 0L) {
            fields.book = runSuspendCatching { repository.bookOf(plan) }.getOrNull()
        }
        fields.bookLoaded = true
    }
    LaunchedEffect(fields) { fields.readPerDay() }
    val entries by produceState<List<TocEntry>>(emptyList(), fields.book) {
        value = fields.book?.let { runSuspendCatching { repository.getTocEntriesForBook(it.id) }.getOrNull() }.orEmpty()
    }
    // Pardes: a category's books with their contents (request 64 of stage 3)
    val books by produceState<List<PlanBook>>(emptyList(), fields.category, fields.baseBooksOnly) {
        value = fields.category?.let { runSuspendCatching { repository.planBooks(it, fields.baseBooksOnly) }.getOrNull() }.orEmpty()
    }
    val levels = remember(entries) { studyLevels(entries) }
    // A book whose depths do not have the one chosen: its first
    val shownLevel = levels.firstOrNull { it.level == fields.level } ?: levels.firstOrNull()
    val names = remember(books, levels, fields.byCategory) { if (fields.byCategory) categoryUnitNames(books) else bookUnitNames(levels) }
    // A category's unit: the one chosen while its books have it, else their first
    val shownName = if (fields.byCategory) names.firstOrNull { it.one == fields.unitName } ?: names.firstOrNull() else null
    val units =
        remember(entries, books, shownLevel, shownName, fields.unitName, fields.byCategory) {
            when {
                fields.byCategory -> shownName?.let { categoryUnits(books, it.one) }.orEmpty()
                else -> shownLevel?.let { bookUnits(entries, it.level, fields.unitName) }.orEmpty()
            }
        }
    val startIndex = remember(units, fields.start) { startIndexOf(units, fields.start) }
    val preview = rememberPreview(fields, units, startIndex, inIsrael)

    fun save() {
        val unit = units.getOrNull(startIndex) ?: return
        val category = fields.category
        val chosen = fields.book
        scope.launch {
            if (fields.byCategory && category != null && shownName != null) {
                val path = runSuspendCatching { repository.pathOf(category.id) }.getOrNull().orEmpty()
                store.save(fields.planOf(plan, category, path, shownName.one, unit.headings))
            } else if (chosen != null && shownLevel != null) {
                val path = runSuspendCatching { repository.categoryPathOf(chosen) }.getOrNull().orEmpty()
                store.save(fields.planFrom(plan, chosen, path, shownLevel.level, unit.headings))
            } else {
                return@launch
            }
            onClose()
        }
    }

    EditorWindow(onClose) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BookRows(fields, levels, shownLevel, units, startIndex, names = names, shownName = shownName)
            ScheduleRows(fields, inIsrael)
            preview?.let { part -> PreviewBox(part, units, fields.periodDays) }
        }
        Spacer(Modifier.height(8.dp))
        Divider(orientation = Orientation.Horizontal)
        Spacer(Modifier.height(8.dp))
        val delete: () -> Unit = {
            scope.launch {
                store.delete(plan.id)
                onClose()
            }
        }
        EditorButtons(
            canSave = (if (fields.byCategory) fields.category != null else fields.book != null) && units.isNotEmpty(),
            onSave = ::save,
            onClose = onClose,
            onDelete = delete.takeIf { plan.id != 0L },
        )
    }
}

/** The editor's fields as the user sets them, from [plan]'s; the book is read when the editor opens. */
@Stable
private class PlanFields(
    plan: StudyPlan,
) {
    var book by mutableStateOf<Book?>(null)
    var bookLoaded by mutableStateOf(plan.id == 0L)

    // Pardes: a whole category studied, its unit by its name, and a part for several days (request 64 of stage 3)
    var byCategory by mutableStateOf(plan.category != 0L)
    var category by mutableStateOf<Category?>(null)
    var baseBooksOnly by mutableStateOf(plan.baseBooksOnly)
    var unitName by mutableStateOf(plan.unitName)
    var periodDays by mutableIntStateOf(plan.periodDays)
    var level by mutableIntStateOf(plan.level)
    var start by mutableStateOf(plan.start)
    val perDayField = TextFieldState(plan.perDay.toString())
    var perDay by mutableIntStateOf(plan.perDay)
    var startDay by mutableStateOf(plan.startDay)
    var weekdays by mutableStateOf(plan.weekdays)
    var restart by mutableStateOf(plan.restart)

    /** Another book, or another depth: the start goes back to its first unit. */
    fun pickBook(picked: Book) {
        book = picked
        byCategory = false
        unitName = ""
        start = emptyList()
    }

    fun pickLevel(picked: Int) {
        level = picked
        unitName = ""
        start = emptyList()
    }

    /** A whole category, or its base books alone ([only]), found as a book is: the start goes back to its first unit. */
    fun pickCategory(
        picked: Category,
        only: Boolean,
    ) {
        category = picked
        baseBooksOnly = only
        byCategory = true
        unitName = ""
        start = emptyList()
    }

    /** A unit by its name: a category's, or a book's daf. */
    fun pickUnit(picked: String) {
        unitName = picked
        start = emptyList()
    }

    /** [day] chosen or not, while some day stays chosen: a plan is learnt on some day. */
    fun toggle(day: DayOfWeek) {
        val next = if (day in weekdays) weekdays - day else weekdays + day
        if (next.isNotEmpty()) weekdays = next
    }

    /** Follows the field of how many a day: a whole number from 1 to 999 is taken, anything else keeps the last. */
    suspend fun readPerDay() {
        snapshotFlow { perDayField.text.toString() }.collect { text ->
            text
                .trim()
                .toIntOrNull()
                ?.takeIf { it in 1..999 }
                ?.let { perDay = it }
        }
    }

    /** [plan] with these fields: the book [chosen], under [path], studied by [level] from [start]. */
    fun planFrom(
        plan: StudyPlan,
        chosen: Book,
        path: List<String>,
        level: Int,
        start: List<String>,
    ): StudyPlan =
        plan.copy(
            bookId = chosen.id,
            bookTitle = chosen.title,
            categoryPath = path,
            level = level,
            start = start,
            perDay = perDay,
            startDay = startDay,
            weekdays = weekdays,
            restart = restart,
            category = 0,
            baseBooksOnly = false,
            unitName = unitName,
            periodDays = periodDays,
        )

    /** [plan] with these fields: the category [chosen], its [path] its own, studied by the unit [unit] from [start]. */
    fun planOf(
        plan: StudyPlan,
        chosen: Category,
        path: List<String>,
        unit: String,
        start: List<String>,
    ): StudyPlan =
        plan.copy(
            bookId = 0,
            bookTitle = chosen.title,
            categoryPath = path,
            level = 0,
            start = start,
            perDay = perDay,
            startDay = startDay,
            weekdays = weekdays,
            restart = restart,
            category = chosen.id,
            baseBooksOnly = baseBooksOnly,
            unitName = unit,
            periodDays = periodDays,
        )
}

/** The day's part the fields give today, as the card will show it; null while the book has no units. */
@Composable
private fun rememberPreview(
    fields: PlanFields,
    units: List<StudyUnit>,
    startIndex: Int,
    inIsrael: Boolean,
): DayPart? {
    val today = LocalDate.now()
    return remember(units, startIndex, fields.perDay, fields.startDay, fields.weekdays, fields.restart, fields.periodDays, inIsrael) {
        if (units.isEmpty()) {
            null
        } else {
            val before = today < fields.startDay
            // Pardes: a part for several days, of its period (request 64 of stage 3)
            val daily = fields.periodDays <= 1
            dayPart(
                startIndex = startIndex,
                count = units.size,
                perDay = fields.perDay,
                restart = fields.restart,
                daysBefore =
                    when {
                        before -> 0
                        daily -> studyDaysBetween(fields.startDay, today) { isStudyDay(it, fields.weekdays, inIsrael) }
                        else -> periodIndex(fields.startDay, today, fields.periodDays)
                    },
                studyDay = !daily || isStudyDay(today, fields.weekdays, inIsrael),
                beforeStart = before,
            )
        }
    }
}

/** The editor's window: its title bar, and [content] in a column over the panel's background. */
@Composable
private fun EditorWindow(
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    with(LocalNucleusApplicationScope.current) {
        IntUiTheme(theme = ThemeUtils.buildThemeDefinition(), styling = ThemeUtils.buildComponentStyling()) {
            JewelDecoratedDialog(
                onCloseRequest = onClose,
                title = "תוכנית לימוד",
                state = rememberDialogState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(600.dp, 680.dp)),
                visible = true,
                resizable = true,
            ) {
                JewelDialogTitleBar(
                    modifier = Modifier.newFullscreenControls(),
                    gradientStartColor = ThemeUtils.titleBarGradientColor(),
                    controlButtonsDirection = ControlButtonsDirection.SystemNative,
                ) {
                    Text("תוכנית לימוד")
                }
                Column(
                    Modifier
                        .trackActivation()
                        .fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground)
                        .padding(16.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * A book or a whole category (request 64 of stage 3), the depth of its contents or the unit studied by ([names],
 * [shownName]), where to start and how much for how many days.
 */
@Suppress("LongParameterList") // the rows' data, as the editor works it out
@Composable
private fun BookRows(
    fields: PlanFields,
    levels: List<StudyLevel>,
    shownLevel: StudyLevel?,
    units: List<StudyUnit>,
    startIndex: Int,
    names: List<UnitName> = emptyList(),
    shownName: UnitName? = null,
) {
    // A book or a whole category, found as the search's bar finds them
    FormRow("לומדים") {
        val title = fields.category?.title
        // A category in its base books alone, as the bar's chip names it
        val category = if (title != null && fields.baseBooksOnly) stringResource(Res.string.scope_base_books_label, title) else title
        val chosen = if (fields.byCategory) category else fields.book?.title
        if (fields.bookLoaded) SourceField(chosen, onPickBook = fields::pickBook, onPickCategory = fields::pickCategory)
    }
    if (fields.byCategory && names.size > 1) {
        FormRow("לומדים לפי") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { option -> Choice(option.one, chosen = option == shownName) { fields.pickUnit(option.one) } }
                }
                Hint("ספר אחרי ספר, כסדר רשימת הספרים")
            }
        }
    }
    if (!fields.byCategory && fields.book != null && levels.isEmpty()) {
        Hint("לספר הזה אין תוכן עניינים לחלק לפיו.")
    }
    if (!fields.byCategory && levels.isNotEmpty()) {
        FormRow("לומדים לפי") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    levels.forEach { option ->
                        Choice(option.name.one, chosen = fields.unitName.isEmpty() && option.level == shownLevel?.level) {
                            fields.pickLevel(option.level)
                        }
                    }
                    // Pardes: a daf, two amudim, where the book goes by amud (request 64 of stage 3)
                    names.drop(levels.size).forEach { option ->
                        Choice(option.one, chosen = fields.unitName == option.one) { fields.pickUnit(option.one) }
                    }
                }
                Hint("הרמות שבתוכן העניינים של הספר")
            }
        }
    }
    if (units.isNotEmpty()) {
        FormRow("מתחילים מ־") {
            ListComboBox(
                items = units.map { it.headings.joinToString(", ") },
                selectedIndex = startIndex,
                onSelectedItemChange = { index -> units.getOrNull(index)?.let { fields.start = it.headings } },
                modifier = Modifier.fillMaxWidth().testTag("study-start"),
            )
        }
        // How much for how many days: a part a day, or for a few days or a week (request 64 of stage 3)
        val name = shownName ?: names.firstOrNull { it.one == fields.unitName } ?: shownLevel?.name
        FormRow("הספק") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(fields.perDayField, Modifier.width(72.dp).testTag("study-per-day"))
                Text(if (fields.perDay == 1) name?.one.orEmpty() else name?.many.orEmpty())
                ListComboBox(
                    items = PERIOD_DAYS.map(::periodLabel),
                    selectedIndex = PERIOD_DAYS.indexOf(fields.periodDays).coerceAtLeast(0),
                    onSelectedItemChange = { index -> fields.periodDays = PERIOD_DAYS[index] },
                    modifier = Modifier.width(140.dp).testTag("study-period"),
                )
            }
        }
    }
}

/** One of a row's choices: full while chosen, outlined otherwise, as the depths' buttons are. */
@Composable
private fun Choice(
    text: String,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    if (chosen) DefaultButton(onClick = onClick) { Text(text) } else OutlinedButton(onClick = onClick) { Text(text) }
}

/** From which date, on which days, and whether to start again at the book's end. */
@Composable
private fun ScheduleRows(
    fields: PlanFields,
    inIsrael: Boolean,
) {
    val accent = rememberAccentColor(JewelTheme.isDark)
    // One column, with the editor's spacing between its rows
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormRow("מתאריך") {
            LuachDateButton(
                label = hebrewFormatter.format(JewishCalendar(fields.startDay.toKotlinLocalDate(), inIsrael)),
                selected = fields.startDay,
                inIsrael = inIsrael,
                accent = accent,
                onSelect = { fields.startDay = it },
            )
        }
        // Pardes: the days of a part a day; a part for several days is learnt on any of them (request 64 of stage 3)
        if (fields.periodDays <= 1) {
            FormRow("בימים") {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WEEK.forEach { day -> DayToggle(weekdayLetter(day), day in fields.weekdays, accent) { fields.toggle(day) } }
                    }
                    Hint("כששבת לא מסומנת, מדלגים גם על יום טוב")
                }
            }
        }
        FormRow("") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Checkbox(checked = fields.restart, onCheckedChange = { fields.restart = it })
                Text(if (fields.byCategory) "בסוף הקטגוריה, להתחיל מחדש" else "בסוף הספר, להתחיל מחדש")
            }
        }
    }
}

/** Save and cancel, and with [onDelete] (a plan already saved) a delete asked for twice. */
@Composable
private fun EditorButtons(
    canSave: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var deleting by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DefaultButton(onClick = onSave, enabled = canSave) { Text("שמור") }
        OutlinedButton(onClick = onClose) { Text("ביטול") }
        Spacer(Modifier.weight(1f))
        if (onDelete != null) {
            HoverBox(onClick = { if (deleting) onDelete() else deleting = true }) {
                Text(
                    if (deleting) "לחיצה נוספת מוחקת" else "מחק תוכנית",
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
    LaunchedEffect(deleting) {
        if (deleting) {
            delay(DELETE_CONFIRM_MS)
            deleting = false
        }
    }
}

private const val DELETE_CONFIRM_MS = 4_000L

// Sunday first, as the week is counted
private val WEEK = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }

/** A field of the form: its [label] beside it. */
@Composable
private fun FormRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(110.dp).padding(top = 6.dp), fontWeight = FontWeight.Medium)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = JewelTheme.globalColors.text.info, modifier = Modifier.padding(top = 4.dp))
}

/** A day of the week, a round toggle: full in the accent while chosen. */
@Composable
private fun DayToggle(
    letter: String,
    chosen: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (chosen) accent else Color.Transparent)
            .border(1.dp, if (chosen) accent else JewelTheme.globalColors.borders.normal, CircleShape)
            .clickable(onClick = onClick)
            .testTag("study-day-$letter"),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = if (chosen) Color.White else JewelTheme.globalColors.text.normal, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Pardes (request 64 of stage 3, the user's word: "זה יהיה אוטומטי כמו בחיפוש הרגיל, הוא יזהה ספר או קטגוריה"): the book
 * or category [chosen] (its title), or a field that finds either as the search's bar does ([sourcesNamed]), each in a
 * row of the bar's ([SuggestionRow]: its icon, its path, and a category in its base books alone so marked); up and down
 * mark one, Enter or a click picks it, Escape lets them go and puts the title picked back.
 */
@Composable
private fun SourceField(
    chosen: String?,
    onPickBook: (Book) -> Unit,
    onPickCategory: (Category, Boolean) -> Unit,
) {
    val graph = LocalAppGraph.current
    val repository = graph.repository
    val scope = rememberCoroutineScope()
    val field = remember { TextFieldState(chosen.orEmpty()) }
    // The title picked last, in the field: nothing to look for while it is there
    var picked by remember { mutableStateOf(chosen.orEmpty()) }
    var rows by remember { mutableStateOf<List<CatBookRow>>(emptyList()) }
    var marked by remember(rows) { mutableIntStateOf(0) }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString().trim() }.collectLatest { text ->
            if (text.length < 2 || text == picked) {
                rows = emptyList()
                return@collectLatest
            }
            delay(SEARCH_DELAY_MS)
            rows = runSuspendCatching { sourcesNamed(graph, text) }.getOrNull().orEmpty()
        }
    }

    val baseBooksLabel = stringResource(Res.string.scope_base_books_label)

    fun pick(row: CatBookRow) {
        val title =
            when (row) {
                is CatBookRow.Book -> row.suggestion.book.title
                is CatBookRow.Category -> if (row.hit.baseBooksOnly) baseBooksLabel.format(row.hit.title) else row.hit.title
                else -> return
            }
        rows = emptyList()
        picked = title
        field.edit { replace(0, length, title) }
        scope.launch {
            when (row) {
                is CatBookRow.Book ->
                    onPickBook(runSuspendCatching { repository.getBookCore(row.suggestion.book.id) }.getOrNull() ?: row.suggestion.book)
                is CatBookRow.Category ->
                    runSuspendCatching { repository.getCategory(row.hit.id) }.getOrNull()?.let { onPickCategory(it, row.hit.baseBooksOnly) }
                else -> Unit
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        TextField(
            field,
            Modifier.fillMaxWidth().testTag("study-source").onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || rows.isEmpty()) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> marked = (marked + 1).coerceAtMost(rows.lastIndex)
                    Key.DirectionUp -> marked = (marked - 1).coerceAtLeast(0)
                    Key.Enter, Key.NumPadEnter -> pick(rows[marked])
                    // The text typed goes, the title picked comes back
                    Key.Escape -> {
                        rows = emptyList()
                        if (picked.isNotEmpty()) field.edit { replace(0, length, picked) }
                    }
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
            placeholder = { Text("שם של ספר או של קטגוריה") },
        )
        rows.forEachIndexed { index, row ->
            when (row) {
                is CatBookRow.Book ->
                    SuggestionRow(
                        parts = dedupAdjacent(row.suggestion.path),
                        onClick = { pick(row) },
                        kind = SuggestionKind.BOOK,
                        highlighted = index == marked,
                    )
                is CatBookRow.Category ->
                    SuggestionRow(
                        parts = row.hit.path,
                        onClick = { pick(row) },
                        kind = SuggestionKind.CATEGORY,
                        highlighted = index == marked,
                        detail = baseBooksDetail(row.hit),
                    )
                else -> Unit
            }
        }
    }
}

private const val SEARCH_DELAY_MS = 150L

/** The day's part as the card will show it (its period's, for a part of [periodDays] days), and how it is reckoned. */
@Composable
private fun PreviewBox(
    part: DayPart,
    units: List<StudyUnit>,
    periodDays: Int = 1,
) {
    val text =
        when (part) {
            is DayPart.Part -> partLabel(part.indexes.map { units[it] })
            DayPart.Rest -> "אין לימוד היום"
            DayPart.NotYet -> "התוכנית עוד לא התחילה"
            DayPart.Finished -> "התוכנית הגיעה לסופה"
        }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        val now =
            when (periodDays) {
                1 -> "היום"
                7 -> "השבוע"
                else -> "בחלק הזה"
            }
        Text(now, fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Hint(if (periodDays <= 1) "החלק של כל יום נקבע לפי התאריך, מיום ההתחלה ובימים שסומנו" else "החלק נקבע לפי התאריך, מיום ההתחלה")
    }
}

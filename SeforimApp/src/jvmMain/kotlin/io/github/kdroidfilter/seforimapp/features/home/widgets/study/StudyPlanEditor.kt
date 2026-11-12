package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.luach.hebrewFormatter
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.luach.LuachDateButton
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinLocalDate
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
import java.time.DayOfWeek
import java.time.LocalDate

// Pardes's own: the editor of a study plan (request 19 of docs/DESKTOP_STAGE2.md), as the approved mock-up has it —
// the book, the depth of its table of contents studied by, where to start, how many a day, from which date, on which
// days, and whether to start again at its end, with the day's part beneath, as the card will show it.

/** The book [title] under [categoryPath] (its categories' titles, the root first). */
private suspend fun SeforimRepository.categoryPathOf(book: Book): List<String> {
    val path = ArrayDeque<String>()
    var category = getCategory(book.categoryId)
    while (category != null) {
        path.addFirst(category.title)
        category = category.parentId?.let { getCategory(it) }
    }
    return path
}

/** The books whose title has [text], an exact title first, then those it starts, then the shortest. */
private suspend fun SeforimRepository.booksLike(text: String): List<Book> =
    findBooksByTitleLikeCore("%$text%", BOOK_SUGGESTIONS * 4)
        .sortedWith(compareBy({ it.title != text }, { !it.title.startsWith(text) }, { it.title.length }))
        .take(BOOK_SUGGESTIONS)

private const val BOOK_SUGGESTIONS = 6

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
    val accent = rememberAccentColor(JewelTheme.isDark)
    val inIsrael = state.inIsrael

    var book by remember { mutableStateOf<Book?>(null) }
    var bookLoaded by remember { mutableStateOf(plan.id == 0L) }
    LaunchedEffect(plan.id) {
        if (plan.id != 0L) book = runSuspendCatching { repository.bookOf(plan) }.getOrNull()
        bookLoaded = true
    }
    val entries by produceState<List<TocEntry>>(emptyList(), book) {
        value = book?.let { runSuspendCatching { repository.getTocEntriesForBook(it.id) }.getOrNull() }.orEmpty()
    }
    val levels = remember(entries) { studyLevels(entries) }
    var level by remember { mutableIntStateOf(plan.level) }
    // A book whose depths do not have the one chosen: its first
    val shownLevel = levels.firstOrNull { it.level == level } ?: levels.firstOrNull()
    val units = remember(entries, shownLevel) { shownLevel?.let { studyUnits(entries, it.level) }.orEmpty() }
    var start by remember { mutableStateOf(plan.start) }
    val startIndex = remember(units, start) { startIndexOf(units, start) }
    val perDayField = remember { TextFieldState(plan.perDay.toString()) }
    var perDay by remember { mutableIntStateOf(plan.perDay) }
    LaunchedEffect(perDayField) {
        snapshotFlow { perDayField.text.toString() }.collect { text ->
            text
                .trim()
                .toIntOrNull()
                ?.takeIf { it in 1..999 }
                ?.let { perDay = it }
        }
    }
    var startDay by remember { mutableStateOf(plan.startDay) }
    var weekdays by remember { mutableStateOf(plan.weekdays) }
    var restart by remember { mutableStateOf(plan.restart) }
    var deleting by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val preview =
        remember(units, startIndex, perDay, startDay, weekdays, restart, inIsrael) {
            if (units.isEmpty()) {
                null
            } else {
                val before = today < startDay
                dayPart(
                    startIndex = startIndex,
                    count = units.size,
                    perDay = perDay,
                    restart = restart,
                    daysBefore = if (before) 0 else studyDaysBetween(startDay, today) { isStudyDay(it, weekdays, inIsrael) },
                    studyDay = isStudyDay(today, weekdays, inIsrael),
                    beforeStart = before,
                )
            }
        }

    fun save() {
        val chosen = book ?: return
        val unit = units.getOrNull(startIndex) ?: return
        val level = shownLevel?.level ?: return
        scope.launch {
            val path = runSuspendCatching { repository.categoryPathOf(chosen) }.getOrNull().orEmpty()
            store.save(
                plan.copy(
                    bookId = chosen.id,
                    bookTitle = chosen.title,
                    categoryPath = path,
                    level = level,
                    start = unit.headings,
                    perDay = perDay,
                    startDay = startDay,
                    weekdays = weekdays,
                    restart = restart,
                ),
            )
            onClose()
        }
    }

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
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FormRow("ספר") {
                            if (bookLoaded) {
                                BookField(book, onPick = { picked ->
                                    book = picked
                                    start = emptyList()
                                })
                            }
                        }
                        if (book != null && levels.isEmpty()) {
                            Hint("לספר הזה אין תוכן עניינים לחלק לפיו.")
                        }
                        if (levels.isNotEmpty()) {
                            FormRow("לומדים לפי") {
                                Column {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        levels.forEach { option ->
                                            val selected = option.level == shownLevel?.level
                                            val pick = {
                                                level = option.level
                                                start = emptyList()
                                            }
                                            if (selected) {
                                                DefaultButton(onClick = pick) { Text(option.name.one) }
                                            } else {
                                                OutlinedButton(onClick = pick) { Text(option.name.one) }
                                            }
                                        }
                                    }
                                    Hint("הרמות שבתוכן העניינים של הספר")
                                }
                            }
                            FormRow("מתחילים מ־") {
                                ListComboBox(
                                    items = units.map { it.headings.joinToString(", ") },
                                    selectedIndex = startIndex,
                                    onSelectedItemChange = { index -> units.getOrNull(index)?.let { start = it.headings } },
                                    modifier = Modifier.fillMaxWidth().testTag("study-start"),
                                )
                            }
                            FormRow("בכל יום") {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextField(perDayField, Modifier.width(72.dp).testTag("study-per-day"))
                                    Text(if (perDay == 1) shownLevel?.name?.one.orEmpty() else shownLevel?.name?.many.orEmpty())
                                }
                            }
                        }
                        FormRow("מתאריך") {
                            LuachDateButton(
                                label = hebrewFormatter.format(JewishCalendar(startDay.toKotlinLocalDate(), inIsrael)),
                                selected = startDay,
                                inIsrael = inIsrael,
                                accent = accent,
                                onSelect = { startDay = it },
                            )
                        }
                        FormRow("בימים") {
                            Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    WEEK.forEach { day ->
                                        DayToggle(weekdayLetter(day), day in weekdays, accent) {
                                            val next = if (day in weekdays) weekdays - day else weekdays + day
                                            // A plan is learnt on some day
                                            if (next.isNotEmpty()) weekdays = next
                                        }
                                    }
                                }
                                Hint("כששבת לא מסומנת, מדלגים גם על יום טוב")
                            }
                        }
                        FormRow("") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Checkbox(checked = restart, onCheckedChange = { restart = it })
                                Text("בסוף הספר, להתחיל מחדש")
                            }
                        }
                        preview?.let { part -> PreviewBox(part, units) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Divider(orientation = Orientation.Horizontal)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DefaultButton(onClick = ::save, enabled = book != null && units.isNotEmpty()) { Text("שמור") }
                        OutlinedButton(onClick = onClose) { Text("ביטול") }
                        Spacer(Modifier.weight(1f))
                        if (plan.id != 0L) {
                            HoverBox(onClick = {
                                if (deleting) {
                                    scope.launch {
                                        store.delete(plan.id)
                                        onClose()
                                    }
                                } else {
                                    deleting = true
                                }
                            }) {
                                Text(
                                    if (deleting) "לחיצה נוספת מוחקת" else "מחק תוכנית",
                                    color = Color(0xFFC62828),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
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

/** The book chosen, or a field to look for one by its title, with the books found under it. */
@Composable
private fun BookField(
    book: Book?,
    onPick: (Book) -> Unit,
) {
    val repository = LocalAppGraph.current.repository
    val field = remember { TextFieldState(book?.title.orEmpty()) }
    var found by remember { mutableStateOf<List<Book>>(emptyList()) }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString().trim() }.collect { text ->
            if (text.length < 2 || text == book?.title) {
                found = emptyList()
                return@collect
            }
            delay(SEARCH_DELAY_MS)
            found = runSuspendCatching { repository.booksLike(text) }.getOrNull().orEmpty()
        }
    }
    Column(Modifier.fillMaxWidth()) {
        TextField(field, Modifier.fillMaxWidth().testTag("study-book"), placeholder = { Text("שם הספר") })
        found.forEach { candidate ->
            HoverBox(onClick = {
                found = emptyList()
                field.edit { replace(0, length, candidate.title) }
                onPick(candidate)
            }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    candidate.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private const val SEARCH_DELAY_MS = 150L

/** The day's part as the card will show it, and how it is reckoned. */
@Composable
private fun PreviewBox(
    part: DayPart,
    units: List<StudyUnit>,
) {
    val text =
        when (part) {
            is DayPart.Part -> partLabel(part.indexes.map { units[it] })
            DayPart.Rest -> "אין לימוד היום"
            DayPart.NotYet -> "התוכנית עוד לא התחילה"
            DayPart.Finished -> "התוכנית הגיעה לסוף הספר"
        }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text("היום", fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Hint("החלק של כל יום נקבע לפי התאריך, מיום ההתחלה ובימים שסומנו")
    }
}

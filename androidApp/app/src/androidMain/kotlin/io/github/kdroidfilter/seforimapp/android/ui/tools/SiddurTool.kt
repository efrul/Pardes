package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.AppPrefs
import io.github.kdroidfilter.seforimapp.android.data.JewishDay
import io.github.kdroidfilter.seforimapp.android.data.KiddushLevanaEarliestOpinion
import io.github.kdroidfilter.seforimapp.android.data.KiddushLevanaLatestOpinion
import io.github.kdroidfilter.seforimapp.android.data.LibraryLookup
import io.github.kdroidfilter.seforimapp.android.data.SiddurData
import io.github.kdroidfilter.seforimapp.android.data.SiddurNusach
import io.github.kdroidfilter.seforimapp.android.data.SiddurPart
import io.github.kdroidfilter.seforimapp.android.data.SiddurSection
import io.github.kdroidfilter.seforimapp.android.data.moladInfo
import io.github.kdroidfilter.seforimapp.android.data.nextDay
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.components.EmptyHint
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.LoadingBox
import io.github.kdroidfilter.seforimapp.android.ui.components.ZCheckRow
import io.github.kdroidfilter.seforimapp.android.ui.components.ZChip
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.ltr
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberClock
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberMinute
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

// "סידור חכם". Desktop's smart siddur (SeforimApp/addons/siddur/enabled/.../SiddurScreen.kt) is built on a private
// package (seforim-siddur: its templates and day book) that community builds, and this app, don't have. This is the
// fallback: the library's siddurim, their sections found by their headings (SiddurData), the day's lines judged by
// their conditions. It keeps the screen's portable behaviours: the nusach of the community (nusachOf) and the user's
// choices under desktop's keys (siddur_nusach, siddur_minyan, siddur_beit_avel), the day and its luach (DayNavigator),
// the tefila of the hour opened first, the Kiddush Levana window of the nusach (levanaOpinions), the screen kept on.

/** Desktop nusachOf, as the library's siddurim: the code picked, else the community's. */
private fun nusachOf(
    code: String?,
    community: String?,
): SiddurNusach =
    when (AppPrefs.siddurNusachFor(code, community)) {
        "SEFARD" -> SiddurNusach.Sefard
        "EDOT_HAMIZRACH" -> SiddurNusach.Edot
        else -> SiddurNusach.Ashkenaz
    }

private val SiddurNusach.code: String
    get() =
        when (this) {
            SiddurNusach.Ashkenaz -> "ASHKENAZ"
            SiddurNusach.Sefard -> "SEFARD"
            SiddurNusach.Edot -> "EDOT_HAMIZRACH"
        }

/** Desktop levanaOpinions: Ashkenaz blesses from the third day to the middle of the month, the others seven to fifteen. */
private fun levanaOpinions(nusach: SiddurNusach) =
    if (nusach == SiddurNusach.Ashkenaz) {
        KiddushLevanaEarliestOpinion.DAYS_3 to KiddushLevanaLatestOpinion.BETWEEN_MOLDOS
    } else {
        KiddushLevanaEarliestOpinion.DAYS_7 to KiddushLevanaLatestOpinion.DAYS_15
    }

/** The tefila of the hour among [parts]: shacharit until chatzos, mincha until sunset, then the evening's. */
private fun partOfTheHour(
    parts: List<SiddurPart>,
    now: Long,
    chatzos: Date?,
    sunset: Date?,
): SiddurPart? {
    val order =
        when {
            chatzos != null && now < chatzos.time -> listOf(SiddurPart.ShacharitYomTov, SiddurPart.ShacharitShabbat, SiddurPart.Shacharit)
            sunset != null && now < sunset.time -> listOf(SiddurPart.MinchaShabbat, SiddurPart.Mincha)
            else -> listOf(SiddurPart.KabbalatShabbat, SiddurPart.MaarivShabbat, SiddurPart.Maariv)
        }
    return order.firstOrNull { it in parts }
}

@Composable
fun SiddurTool(onBack: () -> Unit) {
    val c = Zayit.colors
    val state = rememberToolsState()
    val app = state.app
    val prefs = app.prefs
    val nav = rememberToolNav()
    val scope = rememberCoroutineScope()
    val inIsrael = state.userInIsrael
    // Desktop's siddur tab keeps a day of its own, from today where the user is
    val today = state.userCity.today()
    var epochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(epochDay)
    val jc = remember(date, inIsrael) { JewishDay.calendar(date, inIsrael) }
    val nusachCode by prefs.siddurNusach.collectAsState()
    val community by prefs.communityFlow.collectAsState()
    val minyan by prefs.siddurMinyan.collectAsState()
    val beitAvel by prefs.siddurBeitAvel.collectAsState()
    val nusach = nusachOf(nusachCode, community)
    val toolPrefs = remember(app) { toolPrefs(app) }
    var showAll by remember { mutableStateOf(toolPrefs.getBoolean(KEY_SHOW_ALL, false)) }
    var found by remember(nusach) { mutableStateOf<Map<SiddurPart, SiddurSection?>?>(null) }
    var fallback by remember(nusach) { mutableStateOf(false) }
    // Neither the nusach's siddurim nor Ashkenaz's give a tefila: one message rather than every tefila "not found"
    var noSiddur by remember(nusach) { mutableStateOf(false) }
    // Then, the siddur that is in the library all the same, no tefila found in it: a partial library keeps a siddur's
    // book with none of the lines its headings open on, and "no siddur in the library" contradicted the tools menu
    var siddurWithoutTefilot by remember(nusach) { mutableStateOf<String?>(null) }
    var openPart by rememberSaveable { mutableStateOf<String?>(null) }
    var openedOfTheHour by rememberSaveable { mutableStateOf(false) }
    KeepScreenOn()

    LaunchedEffect(nusach) {
        val repo = app.libraryStore.repository()
        var loaded = runCatching { SiddurData.loadSections(repo, nusach.books) }.getOrDefault(emptyList())
        fallback = false
        if (loaded.isEmpty() && nusach != SiddurNusach.Ashkenaz) {
            loaded = runCatching { SiddurData.loadSections(repo, SiddurNusach.Ashkenaz.books) }.getOrDefault(emptyList())
            // Said only when Ashkenaz is there to be shown
            fallback = loaded.isNotEmpty()
        }
        siddurWithoutTefilot =
            if (loaded.isEmpty()) {
                runCatching { LibraryLookup.findBook(repo, (nusach.books + SiddurNusach.Ashkenaz.books).distinct()) }.getOrNull()?.title
            } else {
                null
            }
        noSiddur = loaded.isEmpty()
        found = withContext(Dispatchers.Default) { SiddurPart.entries.associateWith { SiddurData.findSection(loaded, it.candidates) } }
    }

    val parts = remember(jc, showAll) { if (showAll) SiddurPart.entries.toList() else SiddurData.partsFor(jc, jc.nextDay()) }
    // Opens on the tefila of the hour, once (desktop siddurOfTheHour). Marked done only once decided: until then a
    // phone shows neither the list nor a text it would leave at once (the list swapped for the text was the flicker)
    val city = state.userCity
    LaunchedEffect(found != null) {
        val f = found ?: return@LaunchedEffect
        if (openedOfTheHour) return@LaunchedEffect
        if (date == today) {
            val zmanim = toolZmanim(today, city, state.zmanimOpinion)
            val part = partOfTheHour(parts, System.currentTimeMillis(), zmanim?.chatzosHayom, zmanim?.sunset)
            if (part != null && f[part] != null && openPart == null) openPart = part.name
        }
        openedOfTheHour = true
    }
    val decidingHour = !openedOfTheHour && openPart == null && date == today
    val current = openPart?.let { name -> SiddurPart.entries.firstOrNull { it.name == name } }
    val currentSection = current?.let { found?.get(it) }

    ToolPage(ToolKind.Siddur.title, onBack) { wide ->
        BackHandler(enabled = current != null && !wide) { openPart = null }
        val overview: @Composable (Modifier) -> Unit = { mod ->
            Box(mod.verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DayNavigator(
                        date = date,
                        today = today,
                        inIsrael = inIsrael,
                        onDay = { epochDay = it.toEpochDay() },
                        onToday = { epochDay = today.toEpochDay() },
                    )
                    SiddurChoices(
                        nusach = nusach,
                        onNusach = { prefs.setSiddurNusach(it.code) },
                        minyan = minyan,
                        onMinyan = prefs::setSiddurMinyan,
                        beitAvel = beitAvel,
                        onBeitAvel = prefs::setSiddurBeitAvel,
                        showAll = showAll,
                        onShowAll = {
                            showAll = it
                            toolPrefs.edit().putBoolean(KEY_SHOW_ALL, it).apply()
                        },
                    )
                    if (fallback) InfoBanner("נוסח זה עדיין אינו זמין, ומוצג נוסח אשכנז")
                    DayCard(state, jc, date, today, nusach, beitAvel)
                    ToolCard(ToolKind.Siddur.title, subtitle = JewishDay.occasion(jc)) {
                        // The day's parts are known before the siddurim are searched: their rows hold the card's size
                        val f = found
                        if (f != null && noSiddur) {
                            NoSiddurNotice(nusach, siddurWithoutTefilot)
                        } else {
                            parts.forEachIndexed { i, p ->
                                if (i > 0) HorizontalDivider(color = c.divider)
                                PartRow(p, f?.get(p), selected = p == current, loading = f == null) { openPart = p.name }
                            }
                        }
                    }
                }
            }
        }
        val text: @Composable (Modifier) -> Unit = { mod ->
            val p = current
            val s = currentSection
            Box(mod) {
                when {
                    p == null && decidingHour -> LoadingBox(Modifier.fillMaxSize())
                    p == null -> EmptyHint(if (found != null && noSiddur) noSiddurTitle(siddurWithoutTefilot) else "בחרו תפילה מהרשימה", Modifier.fillMaxSize())
                    // A part kept over a rotation or a new nusach: its siddurim are still being searched
                    found == null -> LoadingBox(Modifier.fillMaxSize())
                    s == null -> EmptyHint("${p.label} לא נמצא בסידור זה", Modifier.fillMaxSize())
                    else ->
                        SiddurText(
                            p,
                            s,
                            jc,
                            showAll,
                            onClose = { openPart = null },
                            onOpenBook = {
                                scope.launch {
                                    val line = withContext(Dispatchers.IO) { runCatching { app.libraryStore.repository().getLineByIndex(s.book.id, s.start) }.getOrNull() }
                                    nav.openBook(s.book, line?.id)
                                }
                            },
                        )
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                overview(Modifier.width(430.dp).fillMaxHeight())
                text(Modifier.weight(1f).fillMaxHeight().padding(top = 10.dp, bottom = 10.dp, end = 10.dp))
            }
        } else if (current != null) {
            text(Modifier.fillMaxSize().padding(10.dp))
        } else if (decidingHour) {
            LoadingBox(Modifier.fillMaxSize())
        } else {
            overview(Modifier.fillMaxSize())
        }
    }
}

private const val NO_SIDDUR = "לא נמצא סידור בספרייה"

/** What the siddur lacks: no siddur at all, or [bookWithoutTefilot], in the library with no tefila found in it. */
private fun noSiddurTitle(bookWithoutTefilot: String?) = bookWithoutTefilot?.let { "התפילות לא נמצאו ב$it" } ?: NO_SIDDUR

/**
 * No tefila to show: the library has none of the siddurim they are taken from (which ones they are), or has one
 * ([bookWithoutTefilot]) without the headings they are found by.
 */
@Composable
private fun NoSiddurNotice(
    nusach: SiddurNusach,
    bookWithoutTefilot: String?,
) {
    val c = Zayit.colors
    val books = (nusach.books + SiddurNusach.Ashkenaz.books).distinct().joinToString(", ")
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(noSiddurTitle(bookWithoutTefilot), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            if (bookWithoutTefilot != null) {
                "הספר נמצא בספרייה, אך לא נמצאו בו כותרות התפילות שהסידור החכם בנוי מהן"
            } else {
                "התפילות נלקחות מספרי הסידור שבספרייה: $books"
            },
            color = c.textSecondary,
            fontSize = 13.sp,
        )
    }
}

/** Whether the library has one of the siddurim the siddur is built from (desktop shows it when installedSiddur); null until known. */
@Composable
internal fun rememberSiddurInLibrary(): Boolean? {
    val app = LocalContext.current.applicationContext as ZayitApplication
    return produceState(siddurInLibrary?.second, app) {
        value =
            withContext(Dispatchers.IO) {
                val repo = app.libraryStore.repository()
                siddurInLibrary?.takeIf { it.first === repo }?.second
                    ?: runCatching { LibraryLookup.findBook(repo, SiddurNusach.entries.flatMap { it.books }) != null }
                        .getOrNull()
                        ?.also { siddurInLibrary = repo to it }
            }
    }.value
}

// Known once per library opened: a new one (an install, an update) is another repository
@Volatile
private var siddurInLibrary: Pair<SeforimRepository, Boolean>? = null

/** Desktop: the screen stays awake while the siddur is shown (the General setting, on by default). */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    val app = view.context.applicationContext as ZayitApplication
    val keepOn by app.prefs.keepScreenOn.collectAsState()
    DisposableEffect(view, keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
}

/** Desktop BookHeader's choices: the nusach, במניין, בבית האבל and what isn't said today, each saved at once. */
@Composable
private fun SiddurChoices(
    nusach: SiddurNusach,
    onNusach: (SiddurNusach) -> Unit,
    minyan: Boolean,
    onMinyan: (Boolean) -> Unit,
    beitAvel: Boolean,
    onBeitAvel: (Boolean) -> Unit,
    showAll: Boolean,
    onShowAll: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(SiddurNusach.entries) { n -> ZChip(n.label, n == nusach, { onNusach(n) }) }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ZCheckRow("במניין", minyan, { onMinyan(!minyan) })
            ZCheckRow("בבית האבל", beitAvel, { onBeitAvel(!beitAvel) })
        }
        ZCheckRow("הצג גם את מה שאין אומרים היום", showAll, { onShowAll(!showAll) })
    }
}

/** The day at a glance: its times, what changes in the tefila, and the evening's notes (omer, chanuka, the moon). */
@Composable
private fun DayCard(
    state: HomeWidgetsState,
    jc: JewishCalendar,
    date: LocalDate,
    today: LocalDate,
    nusach: SiddurNusach,
    beitAvel: Boolean,
) {
    val c = Zayit.colors
    val city = state.userCity
    val location = city.location
    val opinion = state.zmanimOpinion
    val inIsrael = state.userInIsrael
    val now = rememberMinute()
    val z = rememberToolZmanim(date, city, opinion)
    val levana =
        rememberToolValue("siddur.levana", date, inIsrael, nusach, now.time / 3_600_000L) {
            val (earliest, latest) = levanaOpinions(nusach)
            moladInfo(now, date, inIsrael, earliest, latest)
        }
    val clock = rememberClock(location.timeZone)
    // Desktop's "EEEE d/M HH:mm", the date and its time kept on one line, in one left-to-right piece
    val levanaFormat =
        remember(location) {
            val weekday = SimpleDateFormat("EEEE", Locale.forLanguageTag("he")).apply { timeZone = location.timeZone }
            val moment = SimpleDateFormat("d/M\u00A0HH:mm", Locale.US).apply { timeZone = location.timeZone }
            val format: (Date) -> String = { d -> "${weekday.format(d)} ${moment.format(d).ltr()}" }
            format
        }

    // Until a value is worked out its place stays blank, as wide as one: the lines wrap as they will once it is there
    fun AnnotatedString.Builder.orBlank(
        text: String?,
        sample: () -> String,
    ) {
        if (text != null) append(text) else withStyle(SpanStyle(color = Color.Transparent)) { append(sample()) }
    }

    fun AnnotatedString.Builder.time(d: Date?) = orBlank(if (z == null) null else d?.let(clock) ?: "--:--") { clock(now) }
    val highlights =
        remember(jc, beitAvel) {
            runCatching { JewishDay.highlights(jc) }.getOrDefault(emptyList()).map { if (beitAvel && it == "תחנון") "אין תחנון" else it }.distinct()
        }
    ToolCard("היום בתפילה", subtitle = city.name.ifBlank { null }) {
        Text(
            buildAnnotatedString {
                append("סוף זמן ק\"ש ")
                time(z?.sofZmanShmaGra)
                append(" · סוף זמן תפילה ")
                time(z?.sofZmanTfilaGra)
                append(" · שקיעת החמה ")
                time(z?.sunset)
                append(" · צאת הכוכבים ")
                time(z?.tzais)
            },
            color = c.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            highlights.forEach { LabelChip(it, minWidth = 0.dp) }
        }
        JewishDay.omerTonight(jc)?.let { Text(it, color = c.text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        JewishDay.chanukaTonight(jc)?.let { Text(it, color = c.text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        if (date == today) {
            Text(
                buildAnnotatedString {
                    append("זמן קידוש לבנה: מ")
                    orBlank(levana?.let { levanaFormat(it.kiddushLevanaStart) }) { levanaFormat(now) }
                    append(" עד ")
                    orBlank(levana?.let { levanaFormat(it.kiddushLevanaEnd) }) { levanaFormat(now) }
                },
                color = c.text,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            if (levana != null && now.after(levana.kiddushLevanaStart) && now.before(levana.kiddushLevanaEnd)) {
                Text("אפשר לקדש את הלבנה עכשיו", color = c.accent, fontSize = 14.sp)
            }
        }
    }
}

/** One tefila of the day; while [loading] (its siddurim still searched) its row holds its place, its source line blank. */
@Composable
private fun PartRow(
    part: SiddurPart,
    section: SiddurSection?,
    selected: Boolean,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Zayit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.selectedRow else c.panel)
            .clickable(enabled = section != null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .alpha(if (section != null || loading) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(part.label, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    section != null -> (listOf(section.book.title) + section.path.drop(1) + section.entry.text).distinct().joinToString(" › ")
                    loading -> " "
                    else -> "לא נמצא בסידור זה"
                },
                color = c.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (section != null) Icon(Icons.Outlined.ChevronLeft, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SiddurText(
    part: SiddurPart,
    section: SiddurSection,
    jc: JewishCalendar,
    showAll: Boolean,
    onClose: () -> Unit,
    onOpenBook: () -> Unit,
) {
    val c = Zayit.colors
    val app = LocalView.current.context.applicationContext as ZayitApplication
    val textSize by app.prefs.textSize.collectAsState()
    val scope = rememberCoroutineScope()
    var lines by remember(section) { mutableStateOf<List<Line>?>(null) }
    LaunchedEffect(section) {
        lines =
            withContext(Dispatchers.IO) {
                runCatching { app.libraryStore.repository().getLines(section.book.id, section.start, minOf(section.end, section.start + MAX_LINES)) }.getOrDefault(emptyList())
            }.sortedBy { it.lineIndex }
    }
    val visible =
        remember(lines, jc, showAll) {
            val all = lines.orEmpty()
            val applies = SiddurData.applicability(all.map { it.content }, jc)
            all.indices.mapNotNull { i ->
                if (applies[i] == false && !showAll) null else all[i] to (applies[i] != false)
            }
        }
    val listState = rememberLazyListState()
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(c.panel)
            .border(1.dp, c.panelBorder, RoundedCornerShape(14.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ZIconButton(Icons.Outlined.Close, "סגור", onClose)
            Column(Modifier.weight(1f)) {
                Text("${part.label} · ${JewishDay.occasion(jc)}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(section.book.title, color = c.textSecondary, fontSize = 12.sp, maxLines = 1)
            }
            ZIconButton(Icons.AutoMirrored.Outlined.OpenInNew, "פתח בספר", onOpenBook)
        }
        if (section.children.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text("מעבר אל", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                items(section.children) { (title, index) ->
                    ZChip(title, false, {
                        val target = visible.indexOfFirst { it.first.lineIndex >= index }
                        if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                    })
                }
            }
        }
        HorizontalDivider(color = c.divider)
        val list = lines
        when {
            list == null -> LoadingBox(Modifier.fillMaxSize())
            list.isEmpty() -> EmptyHint("הטקסט לא נמצא", Modifier.fillMaxSize())
            else ->
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)) {
                    itemsIndexed(visible, key = { _, item -> item.first.id }) { _, (line, applies) ->
                        // Citation links in the accent, as in the books (0.6.5 f443277d colors links)
                        val text = remember(line.content, textSize, c.accent) { buildAnnotatedFromHtml(line.content, textSize, linkColor = c.accent) }
                        Text(
                            text,
                            color = c.text,
                            fontFamily = ZayitFonts.book,
                            fontSize = textSize.sp,
                            lineHeight = (textSize * 1.7f).sp,
                            modifier = Modifier.padding(vertical = 3.dp).alpha(if (applies) 1f else 0.4f),
                        )
                    }
                }
        }
    }
}

private const val MAX_LINES = 2500
private const val KEY_SHOW_ALL = "siddur_show_all"

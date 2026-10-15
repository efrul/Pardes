package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.data.ALIYOS
import io.github.kdroidfilter.seforimapp.android.data.MikraWeek
import io.github.kdroidfilter.seforimapp.android.data.TorahText
import io.github.kdroidfilter.seforimapp.android.data.VerseText
import io.github.kdroidfilter.seforimapp.android.data.aliyaPlace
import io.github.kdroidfilter.seforimapp.android.data.aliyaRange
import io.github.kdroidfilter.seforimapp.android.data.aliyosOf
import io.github.kdroidfilter.seforimapp.android.data.mikraWeek
import io.github.kdroidfilter.seforimapp.android.data.parshaPlace
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.components.EmptyHint
import io.github.kdroidfilter.seforimapp.android.ui.components.LoadingBox
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.ShnayimMikraWidget
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberMikraDone
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberOpenInLibrary
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import kotlinx.coroutines.launch

/**
 * "שנים מקרא ואחד תרגום": desktop's Shnayim Mikra widget alone, as its tool window
 * (features/home/widgets/luach/ShnayimMikraWidget.kt, 320 × 360): the week's parsha (mikraWeek: a double one as one,
 * וזאת הברכה until Simchat Torah) by its seven aliyos, each ticked once read, in the user DB shared with desktop
 * (ShnayimMikraStore). In the tablet panel a tap opens the aliya in the Chumash, as desktop. The page adds Android's
 * reader, each verse twice and then Onkelos: the list then the reader on a phone, side by side when wide.
 */
@Composable
fun ShnayimMikraTool(onBack: () -> Unit) {
    val state = rememberToolsState()
    val nav = rememberToolNav()
    if (inToolPanel()) {
        ToolPage(
            ToolKind.ShnayimMikra.title,
            onBack,
            actions = { ZIconButton(Icons.Outlined.AutoStories, "קריאה עם תרגום", { nav.openTool(ToolKind.ShnayimMikra) }, tint = Zayit.colors.textSecondary) },
        ) { ShnayimMikraWidget.Content(state, Modifier.fillMaxSize()) }
        return
    }
    val day = state.shownDate
    val inIsrael = state.inIsrael
    val week = remember(day, inIsrael) { mikraWeek(day, inIsrael) }
    val done = rememberMikraDone(state, week)
    val store = state.app.userData.shnayimMikra
    val scope = rememberCoroutineScope()
    val openPlace = rememberOpenInLibrary(state, shnayimMikra = true)
    var reading by rememberSaveable(week.parsha, week.year) { mutableIntStateOf(-1) }

    fun setDone(
        aliya: Int,
        read: Boolean,
    ) {
        scope.launch { runCatching { store.setRead(week.year, week.parsha.name, aliya, read) } }
    }

    ToolPage(ToolKind.ShnayimMikra.title, onBack) { wide ->
        BackHandler(enabled = reading >= 0) { reading = -1 }
        val list: @Composable (Modifier) -> Unit = { mod ->
            Box(mod.verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    ToolDayBar(state, step = 7, previousLabel = "השבוע הקודם", nextLabel = "השבוע הבא")
                    Spacer(Modifier.height(10.dp))
                    AliyosCard(week, done, reading, onToggle = { i -> setDone(i, i !in done) }, onOpen = { reading = it })
                }
            }
        }
        val reader: @Composable (Modifier) -> Unit = { mod ->
            Box(mod) {
                if (reading in 0..6) {
                    AliyaReader(
                        state,
                        week,
                        reading,
                        done = reading in done,
                        onDone = { setDone(reading, it) },
                        onClose = { reading = -1 },
                        onOpenBook = { openPlace(aliyaPlace(week.parsha, reading)) },
                    )
                } else {
                    EmptyHint("בחרו עלייה כדי לקרוא שנים מקרא ואחד תרגום", Modifier.fillMaxSize())
                }
            }
        }
        when {
            wide ->
                Row(Modifier.fillMaxSize()) {
                    list(Modifier.width(400.dp).fillMaxHeight())
                    reader(Modifier.weight(1f).fillMaxHeight().padding(top = 10.dp, bottom = 10.dp, end = 10.dp))
                }
            reading in 0..6 -> reader(Modifier.fillMaxSize().padding(10.dp))
            else -> list(Modifier.fillMaxSize())
        }
    }
}

/** The week's aliyos, as the widget lists them: a tick, the aliya (in the accent once read), its verses. */
@Composable
private fun AliyosCard(
    week: MikraWeek,
    done: Set<Int>,
    reading: Int,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val c = Zayit.colors
    val aliyos = remember(week.parsha) { aliyosOf(week.parsha) }
    ToolCard("שנים מקרא · ${week.name}", subtitle = "⁦${done.size}/${aliyos.size}⁩") {
        aliyos.forEachIndexed { i, (from, to) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (i == reading) c.selectedRow else c.panel)
                    .clickable(role = Role.Button) { onOpen(i) }
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = i in done,
                    onCheckedChange = { onToggle(i) },
                    colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary, checkmarkColor = c.onAccent),
                )
                Text(ALIYOS[i], color = if (i in done) c.accent else c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                Text(aliyaRange(from, to), color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ChevronLeft, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AliyaReader(
    state: HomeWidgetsState,
    week: MikraWeek,
    aliya: Int,
    done: Boolean,
    onDone: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenBook: () -> Unit,
) {
    val c = Zayit.colors
    val textSize by state.app.prefs.textSize.collectAsState()
    var verses by remember(week.parsha, aliya) { mutableStateOf<List<VerseText>?>(null) }
    LaunchedEffect(week.parsha, aliya) {
        verses = runCatching { TorahText.aliya(state.app.libraryStore.repository(), week.parsha, aliya) }.getOrDefault(emptyList())
    }
    val (from, to) = remember(week.parsha, aliya) { aliyosOf(week.parsha)[aliya] }
    val book = remember(week.parsha) { parshaPlace(week.parsha)?.bookTitle.orEmpty() }
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
                Text("${week.name} · ${ALIYOS[aliya]}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$book ${aliyaRange(from, to)}", color = c.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Checkbox(
                checked = done,
                onCheckedChange = { onDone(it) },
                colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary, checkmarkColor = c.onAccent),
            )
            ZIconButton(Icons.AutoMirrored.Outlined.OpenInNew, "פתח בספר", onOpenBook)
        }
        HorizontalDivider(color = c.divider)
        val list = verses
        when {
            list == null -> LoadingBox(Modifier.fillMaxSize())
            list.isEmpty() -> EmptyHint("הפסוקים לא נמצאו בספרייה", Modifier.fillMaxSize())
            else ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)) {
                    items(list, key = { it.chapter * 1000 + it.verse }) { v -> VerseBlock(v, textSize) }
                }
        }
    }
}

@Composable
private fun VerseBlock(
    v: VerseText,
    textSize: Float,
) {
    val c = Zayit.colors
    val hebrew = remember(v.hebrew, textSize) { buildAnnotatedFromHtml(v.hebrew, textSize) }
    val targum = remember(v.targum, textSize) { v.targum?.let { buildAnnotatedFromHtml(HebrewTextUtils.removeTeamim(it), textSize * 0.92f) } }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(hebrew, color = c.text, fontFamily = ZayitFonts.book, fontSize = textSize.sp, lineHeight = (textSize * 1.7f).sp)
        Spacer(Modifier.height(4.dp))
        Text(hebrew, color = c.text, fontFamily = ZayitFonts.book, fontSize = textSize.sp, lineHeight = (textSize * 1.7f).sp)
        if (targum != null) {
            Spacer(Modifier.height(4.dp))
            Text(targum, color = c.textSecondary, fontFamily = ZayitFonts.targum, fontSize = (textSize * 0.92f).sp, lineHeight = (textSize * 1.6f).sp)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = c.divider)
    }
}

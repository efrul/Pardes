package io.github.kdroidfilter.seforimapp.features.settings.daf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.firstPageKey
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.pickScansFolder
import io.github.kdroidfilter.seforimapp.features.pdf.rememberPdfiumLoaded
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.daf_scan_first_reset
import seforimapp.seforimapp.generated.resources.daf_scans_choose
import seforimapp.seforimapp.generated.resources.daf_scans_cut
import seforimapp.seforimapp.generated.resources.daf_scans_cut_description
import seforimapp.seforimapp.generated.resources.daf_scans_cut_enabled
import seforimapp.seforimapp.generated.resources.daf_scans_cut_remove
import seforimapp.seforimapp.generated.resources.daf_scans_description
import seforimapp.seforimapp.generated.resources.daf_scans_first_guessed
import seforimapp.seforimapp.generated.resources.daf_scans_first_page_row
import seforimapp.seforimapp.generated.resources.daf_scans_first_pages
import seforimapp.seforimapp.generated.resources.daf_scans_first_pages_checked
import seforimapp.seforimapp.generated.resources.daf_scans_first_pages_description
import seforimapp.seforimapp.generated.resources.daf_scans_first_pages_none
import seforimapp.seforimapp.generated.resources.daf_scans_first_pages_set
import seforimapp.seforimapp.generated.resources.daf_scans_folder
import seforimapp.seforimapp.generated.resources.daf_scans_no_folder
import seforimapp.seforimapp.generated.resources.daf_scans_no_otzaria
import seforimapp.seforimapp.generated.resources.daf_scans_otzaria
import seforimapp.seforimapp.generated.resources.daf_scans_remove
import seforimapp.seforimapp.generated.resources.daf_scans_title
import java.io.File

// Pardes (requests 41, 48 and 56 of docs/DESKTOP_STAGE2.md): where the tractates' scans are looked for, in the settings
// rather than over the scan (the user's decision of 2026-11-13), and their pages cut into images, by the user's word
// (request 51 of docs/DESKTOP_STAGE3_PLAN.md), and the pages of the user's files where tractates begin (request 52).

/**
 * Settings › צורת הדף: where the tractates' scans are looked for (DafScans): the folder of scanned tractates the user
 * chose, with a button that chooses another and one that lets it go, and Otzaria's folders, found by themselves; the
 * pages of its files where tractates begin, as the user set them; and the room their pages cut into images take
 * (DafScanPages), with a button that removes them.
 */
@Composable
fun DafScansCard() {
    val scans = LocalAppGraph.current.dafScans
    val scope = rememberCoroutineScope()
    val folder by scans.folder.collectAsState()
    val otzaria by produceState(emptyList<File>()) { value = scans.otzariaFolders() }
    val info = JewelTheme.globalColors.text.info
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(Res.string.daf_scans_title), fontSize = 15.sp)
        Text(text = stringResource(Res.string.daf_scans_description), fontSize = 12.sp, color = info)
        Text(text = stringResource(Res.string.daf_scans_folder), fontSize = 13.sp)
        Text(
            text = folder?.let(::isolatedPath) ?: stringResource(Res.string.daf_scans_no_folder),
            fontSize = 12.sp,
            color = info,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { scope.launch { pickScansFolder()?.let(scans::setFolder) } }) {
                Text(stringResource(Res.string.daf_scans_choose))
            }
            if (folder != null) {
                OutlinedButton(onClick = { scans.setFolder(null) }) { Text(stringResource(Res.string.daf_scans_remove)) }
            }
        }
        Text(text = stringResource(Res.string.daf_scans_otzaria), fontSize = 13.sp)
        if (otzaria.isEmpty()) Text(stringResource(Res.string.daf_scans_no_otzaria), fontSize = 12.sp, color = info)
        for (dir in otzaria) Text(isolatedPath(dir.path), fontSize = 12.sp, color = info)
        FirstPages()
        CutPages()
    }
}

// The page of each tractate's daf ב. in the files of the user's folder (request 52 of stage 3), set here and no longer
// over the scan, one tractate or several at once (request 65 of the list after the third testers' build: "הגדרת עמוד
// לדף ב - תהיה דרך ההגדרות ולא מתצוגת המסכת")
@Composable
private fun FirstPages(modifier: Modifier = Modifier) {
    val scans = LocalAppGraph.current.dafScans
    val folder by scans.folder.collectAsState()
    val pages by scans.firstPages.collectAsState()
    // The file's bookmarks guess as the scan does, once PDFium is loaded
    val loaded = rememberPdfiumLoaded()
    val rows by produceState(emptyList<FirstPage>(), folder, pages, loaded) {
        value =
            runSuspendCatching {
                scans.inFolder(bookmarks = loaded == true).map { (tractate, source) ->
                    FirstPage(firstPageKey(source.file, tractate), tractate, source.file.name, source.first, source.firstSet)
                }
            }.getOrDefault(emptyList())
    }
    FirstPagesContent(rows, onSet = scans::setFirstPage, modifier = modifier)
}

/**
 * A tractate of the user's folder in its file ([file], its name): the key of its first page ([key], `firstPageKey`),
 * the file's page of its daf ב. (from 0), and whether the user set it ([set]) or it is guessed.
 */
@Immutable
internal data class FirstPage(
    val key: String,
    val tractate: String,
    val file: String,
    val page: Int,
    val set: Boolean,
)

/**
 * The tractates of the user's folder ([rows]), each with its file's page of daf ב. as a PDF reader counts its pages
 * (from 1), saved as typed ([onSet]: its key and the page from 0, or null to guess it again) on Enter or as its field is
 * left, not at each key (a page set opens the scans shown again); "לפי ניחוש" beside a page guessed, "חזרה לניחוש"
 * beside one set; and the tractates checked set to one page at once.
 */
@Composable
internal fun FirstPagesContent(
    rows: List<FirstPage>,
    onSet: (key: String, page: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = JewelTheme.globalColors.text.info
    var checked by remember { mutableStateOf(emptySet<String>()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(Res.string.daf_scans_first_pages), fontSize = 13.sp)
        Text(text = stringResource(Res.string.daf_scans_first_pages_description), fontSize = 12.sp, color = info)
        if (rows.isEmpty()) {
            Text(stringResource(Res.string.daf_scans_first_pages_none), fontSize = 12.sp, color = info)
            return@Column
        }
        for (row in rows) {
            FirstPageRow(
                row,
                checked = row.key in checked,
                onCheck = { on -> checked = if (on) checked + row.key else checked - row.key },
            ) { onSet(row.key, it) }
        }
        CheckedPages(enabled = checked.isNotEmpty()) { page ->
            checked.forEach { onSet(it, page) }
            checked = emptySet()
        }
    }
}

// A tractate's daf ב. in its file: checked or not, its page in the file (from 1, as typed), guessed or back to the guess
@Composable
private fun FirstPageRow(
    row: FirstPage,
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
    onSet: (Int?) -> Unit,
) {
    val field = rememberTextFieldState((row.page + 1).toString())
    // Shown anew when it changes otherwise: another tractate set before it in its file, or several at once
    LaunchedEffect(row.page) { field.setTextAndPlaceCursorAtEnd((row.page + 1).toString()) }
    val save = { pageTyped(field.text)?.takeIf { it != row.page }?.let(onSet) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckboxRow(
            text = stringResource(Res.string.daf_scans_first_page_row, row.tractate, row.file),
            checked = checked,
            onCheckedChange = onCheck,
            // The fields one under the other
            modifier = Modifier.weight(1f),
        )
        TextField(
            state = field,
            modifier =
                Modifier
                    .width(PAGE_FIELD_WIDTH)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key in ENTER_KEYS) save()
                        false
                    }.onFocusChanged { if (!it.isFocused) save() },
        )
        if (row.set) {
            Link(text = stringResource(Res.string.daf_scan_first_reset), onClick = { onSet(null) })
        } else {
            Text(stringResource(Res.string.daf_scans_first_guessed), fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
        }
    }
}

// One page typed for every tractate checked, and its button
@Composable
private fun CheckedPages(
    enabled: Boolean,
    onSet: (Int) -> Unit,
) {
    val field = rememberTextFieldState()
    val page = pageTyped(field.text)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.daf_scans_first_pages_checked), fontSize = 12.sp)
        TextField(state = field, modifier = Modifier.width(PAGE_FIELD_WIDTH), enabled = enabled)
        OutlinedButton(
            onClick = {
                page?.let(onSet)
                field.clearText()
            },
            enabled = enabled && page != null,
        ) {
            Text(stringResource(Res.string.daf_scans_first_pages_set))
        }
    }
}

/** A page of a file as typed, from 1, as the page from 0; null for none. */
internal fun pageTyped(text: CharSequence): Int? =
    text
        .toString()
        .trim()
        .toIntOrNull()
        ?.takeIf { it >= 1 }
        ?.minus(1)

private val ENTER_KEYS = setOf(Key.Enter, Key.NumPadEnter)

private val PAGE_FIELD_WIDTH = 64.dp

// The scans' pages cut into images beside the library (request 56), by the user's word (request 51 of stage 3): whether
// they are cut, the room they take, and their removal
@Composable
private fun CutPages(modifier: Modifier = Modifier) {
    val pages = LocalAppGraph.current.dafScanPages
    val scans = LocalAppGraph.current.dafScans
    val cutting by scans.cut.collectAsState()
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pages) { bytes = pages.bytes() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(Res.string.daf_scans_cut), fontSize = 13.sp)
        CheckboxRow(
            text = stringResource(Res.string.daf_scans_cut_enabled),
            checked = cutting == true,
            onCheckedChange = scans::setCut,
        )
        Text(
            text = stringResource(Res.string.daf_scans_cut_description, formatBytes(bytes)),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        if (bytes > 0) {
            OutlinedButton(onClick = {
                scope.launch {
                    pages.clear()
                    bytes = pages.bytes()
                }
            }) { Text(stringResource(Res.string.daf_scans_cut_remove)) }
        }
    }
}

package io.github.kdroidfilter.seforimapp.android.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.UserNote
import io.github.kdroidfilter.seforimapp.android.data.guardedWrite
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.shell.DialogSystemBarsOver
import io.github.kdroidfilter.seforimapp.android.ui.shell.PageSearchField
import io.github.kdroidfilter.seforimapp.android.ui.shell.PhoneHeader
import io.github.kdroidfilter.seforimapp.android.ui.shell.WindowBackScope
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun noteDate(millis: Long): String = SimpleDateFormat("d.M.yyyy", Locale.US).format(Date(millis))

/**
 * "ההערות שלי", the user's notes of every book (desktop 0.6.1 features/notes/NotesScreen.kt): grouped by book, the
 * book of the latest note first, searchable by note, passage or book ("חיפוש בהערות"); opening one opens its book on
 * its line with the notes pane showing it ([onOpen] gets book, line and title). A full-screen page over the tabs.
 */
@Composable
fun NotesPage(onOpen: (bookId: Long, lineId: Long, title: String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The header under the status bar, the page ground under the navigation bar
        DialogSystemBarsOver(status = Zayit.colors.titleBarTone, navigation = Zayit.colors.ground)
        WindowBackScope {
            Box(Modifier.fillMaxSize().background(Zayit.colors.ground)) {
                Column(Modifier.fillMaxSize()) {
                    PhoneHeader("ההערות שלי", onBack = onDismiss)
                    NotesContent(onOpen = { b, l, t ->
                        onDismiss()
                        onOpen(b, l, t)
                    })
                }
            }
        }
    }
}

@Composable
private fun NotesContent(onOpen: (Long, Long, String) -> Unit) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val scope = rememberCoroutineScope()
    val written by app.userData.revision.collectAsState()
    val loaded by produceState<Pair<List<UserNote>, Map<Long, String>>?>(null, written) {
        value = withContext(Dispatchers.IO) {
            val notes = runCatching { app.userData.allNotes() }.getOrDefault(emptyList())
            val titles = notes.map { it.bookId }.distinct().associateWith { id ->
                runCatching { app.libraryStore.repository().getBookCore(id)?.title }.getOrNull().orEmpty()
            }
            notes to titles
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val (notes, titles) = loaded ?: (emptyList<UserNote>() to emptyMap())
    val shown = remember(notes, titles, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            notes
        } else {
            notes.filter { n -> n.note.contains(q, ignoreCase = true) || n.quote.contains(q, ignoreCase = true) || titles[n.bookId].orEmpty().contains(q, ignoreCase = true) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 840.dp
        LazyColumn(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxSize().padding(horizontal = if (wide) 24.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Box(Modifier.padding(top = 10.dp)) { PageSearchField(query, { query = it }, "חיפוש בהערות") } }
            if (loaded != null && shown.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.EditNote, null, tint = Zayit.colors.textSecondary, modifier = Modifier.size(40.dp))
                        Text("אין הערות", color = Zayit.colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            // Books in the order of their latest note (the notes come newest first)
            items(shown.groupBy { it.bookId }.entries.toList(), key = { it.key }) { (bookId, bookNotes) ->
                val title = titles[bookId].orEmpty()
                BookNotesCard(title, bookNotes, onOpen = { onOpen(it.bookId, it.lineId, title) }, onDelete = { n -> scope.launch { guardedWrite("Note removal") { app.userData.removeNote(n.bookId, n.id) } } })
            }
            item { Box(Modifier.padding(bottom = 16.dp)) }
        }
    }
}

@Composable
private fun BookNotesCard(title: String, notes: List<UserNote>, onOpen: (UserNote) -> Unit, onDelete: (UserNote) -> Unit) {
    val c = Zayit.colors
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, c.panelBorder, shape).background(c.panel).padding(6.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronLeft, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(notes.size.toString(), fontSize = 12.sp, color = c.textSecondary, modifier = Modifier.padding(end = 10.dp))
        }
        if (expanded) {
            Column(Modifier.padding(start = 10.dp, end = 4.dp, bottom = 6.dp)) {
                notes.forEachIndexed { i, note ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(note) }.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.EditNote, null, tint = c.accent, modifier = Modifier.size(17.dp))
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(note.note, fontSize = 15.sp, color = c.text, fontFamily = ZayitFonts.ui, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val subtitle = listOf(note.quote, noteDate(note.updatedAt)).filter { it.isNotBlank() }.joinToString(" · ")
                            Text(subtitle, fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ZIconButton(Icons.Outlined.Close, "מחק", { onDelete(note) }, tint = c.textSecondary)
                    }
                    if (i < notes.lastIndex) HorizontalDivider(color = c.divider.copy(alpha = 0.5f), modifier = Modifier.padding(start = 28.dp))
                }
            }
        }
    }
}

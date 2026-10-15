package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.UserNote
import io.github.kdroidfilter.seforimapp.android.data.guardedWrite
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Desktop's "dd.MM.yy · HH:mm" stamp under each note, locale-neutral on purpose. */
private fun formatNoteTime(millis: Long): String = SimpleDateFormat("dd.MM.yy · HH:mm", Locale.US).format(Date(millis))

/**
 * The notes pane (desktop features/bookcontent/ui/panels/notes/NotesPanel.kt): the notes of the selected lines (a
 * section, a multi-selection), oldest first, each a card that saves itself 500 ms after typing and when it loses the
 * focus (a blank one is deleted), with its date and a delete button; then a draft, the one "הוסף הערה" anchored to a
 * selection, or else a whole-line draft for a line without notes. Phone: kept above the keyboard.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NotesPane(vm: ReaderViewModel, state: ReaderUiState, modifier: Modifier = Modifier, header: @Composable () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val bookId = state.book?.id ?: return
    // Writes outlive the pane (an edit flushed as it closes) and run one at a time: a draft's debounce and its focus
    // loss must not both see "not created yet" and add the note twice
    val writes = remember { Mutex() }
    fun write(block: suspend () -> Unit) {
        app.appScope.launch { writes.withLock { guardedWrite("Note write") { block() } } }
    }
    LaunchedEffect(bookId) { app.userData.loadNotes(bookId) }
    val notesByBook by app.userData.notesByBook.collectAsState()
    val selected = state.selectedLineIds
    val allNotes = remember(notesByBook, bookId, selected) { notesByBook[bookId].orEmpty().filter { it.lineId in selected }.sortedBy { it.id } }
    val primary = state.primaryLineId?.let { vm.lineById(it) }
    val draft = state.noteDraft
    var draftCreatedId by remember(primary?.id, draft) { mutableStateOf<Long?>(null) }
    val notes = remember(allNotes, draftCreatedId) { allNotes.filter { it.id != draftCreatedId } }
    val autoDraft = remember(draft, notes, primary) {
        if (draft != null || notes.isNotEmpty() || primary == null) {
            null
        } else {
            val plain = vm.plainText(primary)
            if (plain.isEmpty()) null else NoteDraftAnchor(primary.id, 0, plain.length, plain.trim())
        }
    }
    val effective = draft ?: autoDraft
    Column(modifier.imePadding()) {
        header()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (notes.isEmpty() && effective == null) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.EditNote, null, tint = Zayit.colors.textSecondary.copy(alpha = 0.55f), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("אין הערות על השורות שנבחרו. בחר טקסט ולחץ \"הוסף הערה\".", textAlign = TextAlign.Center, color = Zayit.colors.textSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(note.quote.takeIf { it.isNotBlank() }, strong = false) {
                            NoteEditor(
                                initialText = note.note,
                                timestamp = note.updatedAt,
                                clearOnDelete = false,
                                onPersist = { text -> write { app.userData.updateNote(bookId, note.id, text) } },
                                onDelete = { write { app.userData.removeNote(bookId, note.id) } },
                                flushOnDispose = true,
                            )
                        }
                    }
                    if (effective != null) {
                        item(key = "draft-${effective.lineId}-${effective.startOffset}-${effective.endOffset}") {
                            NoteCard(effective.quote.takeIf { it.isNotBlank() }, strong = true) {
                                NoteEditor(
                                    initialText = "",
                                    timestamp = null,
                                    // Once per "הוסף הערה": composed again later (scrolled back into a pane the find
                                    // bar's keyboard shrank), the editor leaves the focus where it is
                                    autoFocus = { draft != null && vm.claimNoteDraftFocus(draft) },
                                    clearOnDelete = true,
                                    onPersist = { text ->
                                        write {
                                            val id = draftCreatedId
                                            when {
                                                text.isBlank() && id != null -> {
                                                    app.userData.removeNote(bookId, id)
                                                    draftCreatedId = null
                                                }
                                                text.isNotBlank() && id == null ->
                                                    draftCreatedId = app.userData.addNote(bookId, effective.lineId, effective.startOffset, effective.endOffset, text, effective.quote)
                                                text.isNotBlank() && id != null -> app.userData.updateNote(bookId, id, text)
                                            }
                                        }
                                    },
                                    onDelete = {
                                        write {
                                            draftCreatedId?.let { app.userData.removeNote(bookId, it) }
                                            draftCreatedId = null
                                        }
                                        if (draft != null) vm.consumeNoteDraft()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        // Hint while drafting a line's first note (desktop no_notes_saved + notes_info_word_level, a long press here)
        // Not while typing: on a phone the keyboard leaves the pane little room, the editor's
        if (allNotes.isEmpty() && effective != null && !WindowInsets.isImeVisible) {
            Text("אין הערות שמורות על קטע זה", textAlign = TextAlign.Center, color = Zayit.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(8.dp))
            InfoBanner(
                "אין צורך לכתוב הערה על השורה כולה: ניתן לסמן מילים מסוימות בלחיצה ארוכה ולבחור \"הוסף הערה\" כדי לכתוב הערה על קטע מדויק בלבד.",
                Modifier.padding(8.dp),
            )
        }
    }
}

/** Desktop NoteCard: a bordered card with an accent stripe on the start edge (right in RTL), the passage in italics. */
@Composable
private fun NoteCard(quote: String?, strong: Boolean, content: @Composable () -> Unit) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(10.dp)
    val stripe = if (strong) c.accent else c.accent.copy(alpha = 0.4f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, if (strong) c.accent.copy(alpha = 0.5f) else c.panelBorder, shape)
            .drawBehind {
                val w = 3.dp.toPx()
                val inset = 8.dp.toPx()
                val edge = 5.dp.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - edge - w else edge
                drawRoundRect(stripe, Offset(x, inset), Size(w, (size.height - 2 * inset).coerceAtLeast(0f)), CornerRadius(w / 2, w / 2))
            }
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
    ) {
        if (quote != null) {
            Text(quote, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = c.textSecondary, textAlign = TextAlign.Justify, fontFamily = ZayitFonts.book, maxLines = 4, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
        }
        content()
    }
}

/** Desktop NoteEditor: no save button; edits are saved 500 ms after typing and on focus loss. */
@OptIn(FlowPreview::class)
@Composable
private fun NoteEditor(
    initialText: String,
    timestamp: Long?,
    clearOnDelete: Boolean,
    onPersist: (String) -> Unit,
    onDelete: () -> Unit,
    flushOnDispose: Boolean = false,
    /** Asked when the editor is composed: true takes the cursor (a new draft's editor). */
    autoFocus: () -> Boolean = { false },
) {
    val c = Zayit.colors
    var text by remember { mutableStateOf(initialText) }
    val latest by rememberUpdatedState(onPersist)
    var persisted by remember { mutableStateOf(initialText) }
    val persist: (String) -> Unit = { t ->
        persisted = t
        latest(t)
    }
    // A saved note's last keystrokes, when the pane closes or the line changes before the debounce (Android only: a
    // tap on the text does not take the focus away, so no focus loss saves them first)
    if (flushOnDispose) {
        DisposableEffect(Unit) { onDispose { if (text != persisted) persist(text) } }
    }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (autoFocus()) runCatching { focus.requestFocus() } }
    LaunchedEffect(Unit) {
        snapshotFlow { text }.drop(1).debounce(500).distinctUntilChanged().collect { persist(it) }
    }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, c.panelBorder, RoundedCornerShape(6.dp)).padding(8.dp)) {
            if (text.isEmpty()) Text("כתוב הערה...", color = c.textDisabled, fontSize = 15.sp)
            BasicTextField(
                text,
                { text = it },
                Modifier.fillMaxWidth().heightIn(max = 220.dp).focusRequester(focus).onFocusChanged { f ->
                    if (hadFocus && !f.isFocused) persist(text)
                    hadFocus = f.isFocused
                },
                textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = ZayitFonts.ui),
                cursorBrush = SolidColor(c.accent),
            )
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(timestamp?.takeIf { it > 0 }?.let(::formatNoteTime).orEmpty(), fontSize = 11.sp, color = c.textSecondary.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            // Touch has no hover: the delete button shows whenever there is something to delete
            if (text.isNotBlank()) {
                ZIconButton(Icons.Outlined.Delete, "מחק הערה", {
                    if (clearOnDelete) text = ""
                    onDelete()
                }, tint = c.textSecondary)
            }
        }
    }
}

/** For the all-notes screen: the note's line, as [UserNote] carries it. */
internal fun UserNote.openTarget(): Pair<Long, Long> = bookId to lineId

package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.my_pdf_books
import seforimapp.seforimapp.generated.resources.my_pdf_books_add
import seforimapp.seforimapp.generated.resources.pdf_folders_books
import seforimapp.seforimapp.generated.resources.pdf_folders_description
import seforimapp.seforimapp.generated.resources.pdf_folders_missing
import seforimapp.seforimapp.generated.resources.pdf_folders_none
import seforimapp.seforimapp.generated.resources.pdf_folders_remove
import seforimapp.seforimapp.generated.resources.pdf_folders_rescan

// Pardes (requests 28 and 44 of docs/DESKTOP_STAGE2.md): the user's folders of PDF books, in Settings › ניהול נתונים.

/**
 * Settings › ניהול נתונים: the user's folders of PDF books ([PdfBookStore.library]), each with its path and its books, or
 * a word that it was not found, and a link that takes it out; a button that adds a folder, and one that reads them
 * again (books added to them or taken out since).
 */
@Composable
fun PdfBooksCard() {
    val store = LocalAppGraph.current.pdfBookStore
    val scope = rememberCoroutineScope()
    val revision by store.revision.collectAsState()
    val library by produceState(PdfLibrary(), revision) { value = store.library() }
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
        Text(text = stringResource(Res.string.my_pdf_books), fontSize = 15.sp)
        Text(text = stringResource(Res.string.pdf_folders_description), fontSize = 12.sp, color = info)
        if (library.folders.isEmpty()) Text(stringResource(Res.string.pdf_folders_none), fontSize = 12.sp, color = info)
        for (shown in library.folders) {
            FolderRow(shown, onRemove = { scope.launch { store.removeFolder(shown.folder.id) } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { pickPdfFolder()?.let { store.addFolder(it) } } }) {
                Text(stringResource(Res.string.my_pdf_books_add))
            }
            if (library.folders.isNotEmpty()) {
                OutlinedButton(onClick = store::rescan) { Text(stringResource(Res.string.pdf_folders_rescan)) }
            }
        }
    }
}

/** A folder of the user's: its name, its path and its books (or that it was not found), and [onRemove]'s link. */
@Composable
private fun FolderRow(
    shown: PdfFolderShown,
    onRemove: () -> Unit,
) {
    val dir = shown.folder.dir
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(dir.name.ifEmpty { dir.path }, fontSize = 13.sp)
            val books =
                if (shown.found) {
                    stringResource(Res.string.pdf_folders_books, shown.books)
                } else {
                    stringResource(Res.string.pdf_folders_missing)
                }
            Text("${isolatedPath(dir.path)} · $books", fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
        }
        Link(stringResource(Res.string.pdf_folders_remove), onClick = onRemove)
    }
}

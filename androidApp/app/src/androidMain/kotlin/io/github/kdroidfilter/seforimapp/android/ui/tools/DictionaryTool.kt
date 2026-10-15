package io.github.kdroidfilter.seforimapp.android.ui.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.DictionaryCard
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberToolsState

/**
 * "מילון": desktop's Dictionary widget alone, as its tool window (features/home/widgets/library/DictionaryWidget.kt,
 * 460 × 560): the dictionaries ticked in its options, a word looked up in the one its chip picks or in all, an entry
 * read in the card and opened in its book from there. The dictionaries are read as the page opens.
 */
@Composable
fun DictionaryTool(onBack: () -> Unit) {
    val state = rememberToolsState()
    ToolPage(ToolKind.Dictionary.title, onBack) {
        if (inToolPanel()) {
            DictionaryCard(state, Modifier.fillMaxSize(), readNow = true)
            return@ToolPage
        }
        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.TopCenter) {
            DictionaryCard(state, Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight(), readNow = true)
        }
    }
}

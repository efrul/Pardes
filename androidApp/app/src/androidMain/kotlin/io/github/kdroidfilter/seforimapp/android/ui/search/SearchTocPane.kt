package io.github.kdroidfilter.seforimapp.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

/** Desktop string table_of_contents (the pane's title). */
internal const val TABLE_OF_CONTENTS = "תוכן עניינים"

private class VisibleEntry(val entry: TocEntry, val depth: Int, val hasChildren: Boolean)

/**
 * The scoped search's TOC pane (tablets): the book's entries that have results, with their counts, those with results
 * opened; checking entries narrows the results to them, searched whole. Port of desktop 0.6.10 BookTocPanel.kt
 * SearchBookTocPanel over BookTocView (showCounts, onlyWithResults, multiSelectIds).
 */
@Composable
internal fun SearchTocPane(
    tree: SearchTocTree,
    counts: Map<Long, Int>,
    selected: Set<Long>,
    scopeTocId: Long?,
    onToggle: (tocId: Long, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    var expanded by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    // Desktop: the entries with results that have children open as the counts come
    LaunchedEffect(tree, counts) {
        val withChildren = tree.children.keys
        expanded = expanded + counts.keys.filter { it in withChildren && (counts[it] ?: 0) > 0 }
    }
    val visible = remember(tree, counts, expanded) {
        val out = ArrayList<VisibleEntry>()
        fun walk(entries: List<TocEntry>, depth: Int) {
            for (entry in entries) {
                if ((counts[entry.id] ?: 0) <= 0) continue
                val children = tree.children[entry.id].orEmpty().filter { (counts[it.id] ?: 0) > 0 }
                out += VisibleEntry(entry, depth, children.isNotEmpty())
                if (entry.id in expanded) walk(children, depth + 1)
            }
        }
        walk(tree.roots, 0)
        out
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        items(visible, key = { it.entry.id }) { v ->
            val entry = v.entry
            val isOpen = entry.id in expanded
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (entry.id == scopeTocId) c.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(role = Role.Checkbox) { onToggle(entry.id, entry.id !in selected) }
                    .padding(start = (v.depth * 14).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(50)).then(if (v.hasChildren) Modifier.clickable { expanded = if (isOpen) expanded - entry.id else expanded + entry.id } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (v.hasChildren) {
                        Icon(if (isOpen) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                Checkbox(
                    checked = entry.id in selected,
                    onCheckedChange = { onToggle(entry.id, it) },
                    colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.textSecondary),
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(entry.text, fontSize = 13.sp, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text((counts[entry.id] ?: 0).toString(), fontSize = 12.sp, color = c.textDisabled, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

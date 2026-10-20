package io.github.kdroidfilter.seforimapp.android.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.search.AUTHOR_ERAS
import io.github.kdroidfilter.seforimapp.android.search.OTZARIA_CREDIT
import io.github.kdroidfilter.seforimapp.android.search.OTZARIA_URL
import io.github.kdroidfilter.seforimapp.android.search.licenseLabel
import io.github.kdroidfilter.seforimapp.android.search.authorYears
import io.github.kdroidfilter.seforimapp.android.ui.components.LoadingBox
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/**
 * The book-details pane (desktop features/bookcontent/ui/panels/bookdetails/BookDetails.kt): title, categories, each
 * author (to the author's page) with era and years, the book's main parts (to their line), editions with source and
 * license, and the Otzaria credit for books that came through it.
 */
@Composable
fun BookDetailsPane(vm: ReaderViewModel, onOpenPart: (lineId: Long) -> Unit, onOpenAuthor: (Long, String) -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(vm) { vm.requestDetails() }
    val details by vm.details.collectAsState()
    val entity = details ?: return LoadingBox(modifier.fillMaxSize())
    val c = Zayit.colors
    val context = LocalContext.current
    fun openUrl(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(entity.book.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = c.text, fontFamily = Zayit.fonts.book)
        if (entity.categories.isNotEmpty()) Text(entity.categories.joinToString(" › "), fontSize = 12.sp, color = c.textSecondary)
        entity.authors.forEach { author ->
            PanelSection("מחבר") {
                Link(AuthorNames.display(author.name), 14.sp) { onOpenAuthor(author.id, AuthorNames.display(author.name)) }
                val facts = listOfNotNull(author.era?.let { AUTHOR_ERAS[it] }, authorYears(author))
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontSize = 12.sp, color = c.textSecondary)
            }
        }
        if (entity.parts.isNotEmpty()) {
            PanelSection("חלקי הספר") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entity.parts.forEach { part -> Link(part.title, 14.sp) { onOpenPart(part.lineId) } }
                }
            }
        }
        val editions = entity.editions.distinctBy { (it.heTitle ?: it.title) to it.licenseCode }
        if (editions.isNotEmpty() || entity.fromOtzaria) {
            PanelSection("מקור ורישיון") {
                editions.forEach { edition ->
                    val title = edition.heTitle ?: edition.title
                    val url = edition.sourceUrl
                    if (url != null) Link(title, 13.sp) { openUrl(url) } else Text(title, fontSize = 13.sp, color = c.text)
                    Text(licenseLabel(edition.licenseCode), fontSize = 12.sp, color = c.textSecondary)
                }
                if (entity.fromOtzaria) {
                    Text(
                        OTZARIA_CREDIT,
                        fontSize = 12.sp,
                        color = c.textSecondary,
                        modifier = Modifier.clickable { openUrl(OTZARIA_URL) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = Zayit.colors.divider, modifier = Modifier.padding(bottom = 6.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Zayit.colors.textSecondary)
        content()
    }
}

@Composable
private fun Link(text: String, size: TextUnit, onClick: () -> Unit) {
    Text(text, color = Zayit.colors.accent, fontSize = size, modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp))
}

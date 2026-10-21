package io.github.kdroidfilter.seforimapp.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit

/** How far above the found line the preview scrolls to (desktop FOUND_LINE_MARGIN, px). */
private const val FOUND_LINE_MARGIN = 40

/**
 * The selected result in its book, to read without opening it: its place as a title, then the found line marked by a
 * bar, between the lines around it, faded; in the books' font and size, over the whole width as the book's text; the
 * search's words highlighted. A tap on the found line opens the result (as the list), on another line the book there.
 * Port of desktop 0.6.7 SearchResultScreen.kt PassagePreview (two-pane layout only): 0.6.1's with the 0.6.4 fix 25dde83d
 * (the found line's bar drawn, not measured) and the 0.6.5 fixes 46a1ecb8 (links in the accent) and e3051846 (pinch
 * zoom, given by the caller's modifier). 0.6.3's 4fe26694 (the found line highlighted by meaning alone) needs the
 * semantic engine, which this app does not have: the lexical highlights stay on every line, as in community builds.
 */
@Composable
internal fun PassagePreviewPane(
    preview: PassagePreview,
    pieces: List<String>?,
    textSize: Float,
    onOpen: () -> Unit,
    onOpenLine: (lineId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zayit.colors
    val hit = preview.hit
    val accent = c.accent
    val ink = c.text
    val (categories, place) = remember(pieces, hit.bookTitle) { categoriesAndPlace(pieces, hit.bookTitle) }
    val highlight = accent.copy(alpha = 0.22f)
    val bookFont = Zayit.fonts.book
    val scroll = rememberScrollState()
    // The found line in view, once laid out
    var foundTop by remember(hit.lineId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(foundTop) { foundTop?.let { scroll.animateScrollTo((it - FOUND_LINE_MARGIN).coerceAtLeast(0)) } }

    Column(modifier.fillMaxWidth().card()) {
        Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 14.dp)) {
            if (categories.isNotEmpty()) Text(categories, fontSize = 12.sp, color = readingSecondary(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hit.bookTitle, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (place != null) Text(place, fontSize = 14.sp, color = readingSecondary(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            preview.lines.forEach { previewLine ->
                val line = previewLine.line
                val found = line.id == hit.lineId
                // Links and footnote markers in the books' color (the accent), not the parser's default blue (desktop
                // 0.6.5 46a1ecb8)
                val annotated = remember(line.id, textSize, accent) { buildAnnotatedFromHtml(line.content, textSize, footnoteMarkerColor = accent) }
                val text = remember(annotated, previewLine.ranges, highlight) { annotated.withBackground(previewLine.ranges, highlight) }
                // The found line: a bar on its side, the text at full strength. Drawn, not measured (desktop 0.6.4
                // 25dde83d): an intrinsic height at a collapsed pane's 0 width overflows Constraints
                Text(
                    text = text,
                    fontFamily = bookFont,
                    fontSize = textSize.sp,
                    lineHeight = (textSize * 1.8f).sp,
                    color = if (found) ink else ink.copy(alpha = 0.45f),
                    textAlign = TextAlign.Justify,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .drawBehind {
                                if (found) {
                                    val bar = 3.dp.toPx()
                                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - bar else 0f
                                    drawRect(accent, Offset(x, 0f), Size(bar, size.height))
                                }
                            }.clickable { if (found) onOpen() else onOpenLine(line.id) }
                            .onGloballyPositioned { if (found) foundTop = it.positionInParent().y.toInt() }
                            .padding(start = 17.dp),
                )
            }
        }
    }
}

package io.github.kdroidfilter.seforimapp.android.ui.reader

import android.content.res.AssetManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import io.github.kdroidfilter.seforim.htmlparser.COMMENT_SEGMENT_TAG
import io.github.kdroidfilter.seforim.htmlparser.CommentAnchor
import io.github.kdroidfilter.seforim.htmlparser.commentAnchorsOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.util.concurrent.ConcurrentHashMap

/*
 * Desktop 0.6.12 features/bookcontent/segments (CommentSegments.kt and CommentAnchorTargets.kt, 2370372c and b41e8cf4):
 * the passages of a line that commentaries explain, and where each base book's anchors lead. The same names and logic;
 * what differs:
 * - the tables are read from the APK's assets (comment-anchors/<bookId>.pb, copied from desktop's resources by the
 *   build, DesktopTextAssets) rather than from the classpath, so [commentAnchorIndex] takes the [AssetManager];
 * - SelectedPassage is part of ReaderUiState's line selection, as desktop's is of ContentState's
 *   (ReaderViewModel.withPassageOfItsSelection).
 */

/** A passage of a line that commentaries explain, [start] to [end] (exclusive) in the displayed text. */
@Immutable
data class CommentSegment(
    val start: Int,
    val end: Int,
    val anchors: Set<CommentAnchor>,
)

/** The passages of this line, from its commentary anchors, in text order. */
fun AnnotatedString.commentSegments(): List<CommentSegment> =
    getStringAnnotations(COMMENT_SEGMENT_TAG, 0, length).mapNotNull { annotation ->
        val anchors = commentAnchorsOf(annotation.item).toSet()
        // The passage starts on its first word, not on the space an anchor may leave before it
        val start = (annotation.start until annotation.end).firstOrNull { !text[it].isWhitespace() }
        if (anchors.isEmpty() || start == null) null else CommentSegment(start, annotation.end, anchors)
    }

fun List<CommentSegment>.at(offset: Int): CommentSegment? = firstOrNull { offset >= it.start && offset < it.end }

/** The commentary lines [segment]'s anchors lead to, among [targets] (its line's), in the books [openBooks]. */
fun passageOf(
    lineId: Long,
    segment: CommentSegment,
    targets: Map<CommentAnchor, AnchorTarget>,
    openBooks: Set<Long>,
): SelectedPassage? {
    val reached = segment.anchors.mapNotNull { targets[it] }.filter { it.bookId in openBooks }
    if (reached.isEmpty()) return null
    return SelectedPassage(
        lineId = lineId,
        anchors = segment.anchors,
        targetLineIds = reached.flatMapTo(mutableSetOf()) { it.lineIds },
        targetBookIds = reached.mapTo(mutableSetOf()) { it.bookId },
    )
}

/**
 * A passage selected in the text: its [lineId], its [anchors] (which tell it among the line's passages) and the
 * commentary lines they lead to. Part of the tab's line selection ([ReaderUiState.selectedPassage]), never held apart
 * from it.
 */
@Immutable
data class SelectedPassage(
    val lineId: Long,
    val anchors: Set<CommentAnchor>,
    val targetLineIds: Set<Long>,
    val targetBookIds: Set<Long>,
)

/**
 * Where the commentary anchors of one base book lead: for each of its lines, each anchor (commentator as the anchor
 * names it, number) with the commentary lines it explains. Resolved offline against the DB (desktop's
 * `CommentAnchorTargetsGen`) and bundled under [RESOURCE_DIR], until the DB carries it.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
class BookAnchorTargets(
    // The commentators as the anchors name them, and the commentary book each leads to
    @ProtoNumber(1) val commentators: List<String> = emptyList(),
    @ProtoNumber(2) val commentatorBooks: List<Long> = emptyList(),
    @ProtoNumber(3) val lines: List<LineAnchorTargets> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class LineAnchorTargets(
    @ProtoNumber(1) val lineId: Long,
    @ProtoNumber(2) val anchors: List<AnchorTargets> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class AnchorTargets(
    // Index in BookAnchorTargets.commentators
    @ProtoNumber(1) val commentator: Int,
    @ProtoNumber(2) val order: Int,
    @ProtoNumber(3) val targetLineIds: List<Long> = emptyList(),
)

/** One anchor's commentary lines, and the commentary book they belong to. */
class AnchorTarget(
    val bookId: Long,
    val lineIds: List<Long>,
)

/** The resolved anchors of one base book, by line. */
class CommentAnchorIndex(
    private val byLine: Map<Long, Map<CommentAnchor, AnchorTarget>>,
    /** The commentators named by this book's resolved anchors, for the parser to keep them only. */
    val commentators: Set<String>,
) {
    fun of(lineId: Long): Map<CommentAnchor, AnchorTarget> = byLine[lineId].orEmpty()

    companion object {
        val EMPTY = CommentAnchorIndex(emptyMap(), emptySet())
    }
}

internal const val RESOURCE_DIR = "comment-anchors"

private val indexes = ConcurrentHashMap<Long, CommentAnchorIndex>()

/** The resolved anchors of [bookId], read once from its bundled file; [CommentAnchorIndex.EMPTY] for a book without. */
@OptIn(ExperimentalSerializationApi::class)
fun commentAnchorIndex(assets: AssetManager, bookId: Long): CommentAnchorIndex =
    indexes.getOrPut(bookId) {
        val bytes =
            runCatching { assets.open("$RESOURCE_DIR/$bookId.pb").use { it.readBytes() } }.getOrNull()
                ?: return@getOrPut CommentAnchorIndex.EMPTY
        val table = ProtoBuf.decodeFromByteArray(BookAnchorTargets.serializer(), bytes)
        CommentAnchorIndex(
            byLine =
                table.lines.associate { line ->
                    line.lineId to
                        line.anchors.associate {
                            CommentAnchor(table.commentators[it.commentator], it.order) to
                                AnchorTarget(table.commentatorBooks[it.commentator], it.targetLineIds)
                        }
                },
            commentators = table.commentators.toSet(),
        )
    }

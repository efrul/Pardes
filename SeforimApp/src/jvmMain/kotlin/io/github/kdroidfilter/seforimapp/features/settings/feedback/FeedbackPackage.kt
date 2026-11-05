package io.github.kdroidfilter.seforimapp.features.settings.feedback

import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafLayouts
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafMark
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafReviewFile
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafReviews
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Pardes: what a user sends to improve the search and the daf's layouts (Settings › ניהול נתונים, [FeedbackSendCard]),
 * one JSON document: the grades given to search results in feedback mode, the searches made (only when the user
 * includes them), and the daf view's line review, with the versions they were made on. Nothing else from the user
 * database (notes, highlights, favorites, books opened) and nothing that names the user or the computer: [sender] is a
 * random id the user can renew, for asking to delete what was sent and for telling sends apart. pardes-semantic/feedback/
 * collect.py reads it; the daf part is pardes-rashi-ocr's review files as they are.
 */
@Serializable
data class FeedbackPackage(
    val format: Int = FORMAT,
    val app: String = APP,
    val appVersion: String? = null,
    val sender: String,
    val sentAt: String,
    /** The library release the searches ran on (its release_info.txt). */
    val library: String? = null,
    val grades: List<GradeRow> = emptyList(),
    val searches: List<SearchRow> = emptyList(),
    val daf: List<DafReviewFile> = emptyList(),
) {
    companion object {
        const val FORMAT = 1
        const val APP = "Pardes"
    }
}

/** A search result graded in feedback mode (the user database's search_feedback). */
@Serializable
data class GradeRow(
    val query: String,
    /** The search's key, as the history keys it: the query with its scope and extension. */
    val search: String,
    val lineId: Long,
    val bookId: Long,
    /** 3 exactly what was looked for, 2 relevant, 0 not relevant. */
    val grade: Int,
    /** The result's place among the search's results, from 0. */
    val rank: Int,
    val categoryId: Long? = null,
    val scopeBookId: Long? = null,
    val tocId: Long? = null,
    val extended: Boolean = false,
    /** The semantic model the search ran with, null without one. */
    val model: String? = null,
    val gradedAt: Long,
)

/** A search made (the user database's visit_history), with the scope it ran in. */
@Serializable
data class SearchRow(
    val query: String,
    val categoryId: Long? = null,
    val bookId: Long? = null,
    val tocId: Long? = null,
    val extended: Boolean = false,
    val count: Long = 1,
    val lastAt: Long,
)

/** Each kind of feedback as it stands, before the user picks what to send. */
data class FeedbackContents(
    val grades: List<GradeRow> = emptyList(),
    val searches: List<SearchRow> = emptyList(),
    val daf: List<DafReviewFile> = emptyList(),
) {
    val dafMarks: Int get() = daf.sumOf { it.marks.size }

    val isEmpty: Boolean get() = grades.isEmpty() && searches.isEmpty() && dafMarks == 0

    /** The queries of the kinds [choice] sends, each once, those left out too: for the user to look over before sending. */
    fun queries(choice: FeedbackChoice): List<String> =
        buildList {
            if (choice.grades) grades.mapTo(this) { it.query }
            if (choice.searches) searches.mapTo(this) { it.query }
        }.distinct()

    /** Each kind as [choice] keeps it: a kind left out empty, and the queries left out gone from the others. */
    fun kept(choice: FeedbackChoice): FeedbackContents =
        FeedbackContents(
            grades = if (choice.grades) grades.filter { it.query !in choice.leftOut } else emptyList(),
            searches = if (choice.searches) searches.filter { it.query !in choice.leftOut } else emptyList(),
            daf = if (choice.daf) daf.filter { it.marks.isNotEmpty() } else emptyList(),
        )

    /** The newest time each kind holds, 0 for a kind without any: a package of these is sent up to it. */
    fun newest(): FeedbackSent =
        FeedbackSent(
            grades = grades.maxOfOrNull { it.gradedAt } ?: 0,
            searches = searches.maxOfOrNull { it.lastAt } ?: 0,
            daf = daf.flatMap { it.marks }.mapNotNull { markedAt(it.at) }.maxOrNull() ?: 0,
        )
}

/** Which kinds go out, and the queries the user left out of them. */
data class FeedbackChoice(
    val grades: Boolean = true,
    val searches: Boolean = true,
    val daf: Boolean = true,
    val leftOut: Set<String> = emptySet(),
)

/** When each kind was last sent: what is newer goes in the next package. Epoch millis, 0 for never. */
data class FeedbackSent(
    val grades: Long = 0,
    val searches: Long = 0,
    val daf: Long = 0,
)

/** The package of [contents] that [choice] keeps, from [sender]. */
fun feedbackPackage(
    contents: FeedbackContents,
    choice: FeedbackChoice,
    sender: String,
    appVersion: String?,
    library: String?,
    now: Instant = Instant.now(),
): FeedbackPackage {
    val kept = contents.kept(choice)
    return FeedbackPackage(
        appVersion = appVersion,
        sender = sender,
        sentAt = now.toString(),
        library = library,
        grades = kept.grades,
        searches = kept.searches,
        daf = kept.daf,
    )
}

/** When a daf mark was made, in epoch millis as the sent times are (its own time is finer), null if it does not say. */
internal fun markedAt(at: String): Long? = runCatching { Instant.parse(at).toEpochMilli() }.getOrNull()

/** The package as the receiver and collect.py read it. */
fun FeedbackPackage.toJson(): String = feedbackJson.encodeToString(FeedbackPackage.serializer(), this)

/** The JSON a part of a package holds at most: the service takes 1,000,000 bytes (feedback-service/src/package.js). */
const val PART_BYTES = 900_000

/**
 * The package in parts whose JSON holds at most [maxBytes] each, each a package itself with the same sender and time:
 * the grades, the searches and the daf's marks shared out in their order, a tractate's review over as many parts as
 * its marks need (the receiver keeps one mark a line, whichever part brought it). One part when it is small enough.
 */
fun FeedbackPackage.parts(maxBytes: Int = PART_BYTES): List<FeedbackPackage> {
    val shell = copy(grades = emptyList(), searches = emptyList(), daf = emptyList())
    if (bytes(toJson()) <= maxBytes) return listOf(this)
    val room = maxBytes - bytes(shell.toJson())
    val parts = mutableListOf<FeedbackPackage>()
    var part = Part()

    // A row's bytes in [part], into it, or into a new part when it would not hold them
    fun add(
        size: (Part) -> Int,
        put: (Part) -> Unit,
    ) {
        if (!part.isEmpty && part.size + size(part) > room) {
            parts += part.toPackage(shell)
            part = Part()
        }
        part.size += size(part)
        put(part)
    }
    grades.forEach { row ->
        val size = bytes(feedbackJson.encodeToString(GradeRow.serializer(), row)) + 1
        add({ size }) { it.grades += row }
    }
    searches.forEach { row ->
        val size = bytes(feedbackJson.encodeToString(SearchRow.serializer(), row)) + 1
        add({ size }) { it.searches += row }
    }
    daf.forEach { review ->
        val head = bytes(feedbackJson.encodeToString(DafReviewFile.serializer(), review.copy(marks = emptyList()))) + 1
        review.marks.forEach { mark ->
            val size = bytes(feedbackJson.encodeToString(DafMark.serializer(), mark)) + 1
            // A tractate's first mark in a part brings its review's head
            add({ p -> size + if (p.daf.lastOrNull()?.tractate == review.tractate) 0 else head }) { p ->
                val last = p.daf.lastOrNull()
                if (last?.tractate == review.tractate) {
                    p.daf[p.daf.lastIndex] = last.copy(marks = last.marks + mark)
                } else {
                    p.daf += review.copy(marks = listOf(mark))
                }
            }
        }
    }
    if (!part.isEmpty) parts += part.toPackage(shell)
    return parts
}

/** A part being filled, and the bytes its rows take. */
private class Part {
    var size = 0
    val grades = mutableListOf<GradeRow>()
    val searches = mutableListOf<SearchRow>()
    val daf = mutableListOf<DafReviewFile>()
    val isEmpty: Boolean get() = grades.isEmpty() && searches.isEmpty() && daf.isEmpty()

    fun toPackage(shell: FeedbackPackage) = shell.copy(grades = grades.toList(), searches = searches.toList(), daf = daf.toList())
}

private fun bytes(text: String): Int = text.toByteArray(Charsets.UTF_8).size

private val feedbackJson =
    Json {
        encodeDefaults = true
        explicitNulls = false
    }

/** What there is to send since [sent]: read from the user database and the daf reviews' folder. */
class FeedbackSource(
    private val database: UserSettingsDb,
    private val dafReviewsFolder: () -> File?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(sent: FeedbackSent): FeedbackContents {
        val queries = database.searchFeedbackQueries
        val grades =
            queries.gradesSince(sent.grades).executeAsList().map { row ->
                GradeRow(
                    query = row.query,
                    search = row.searchKey,
                    lineId = row.lineId,
                    bookId = row.bookId,
                    grade = row.grade.toInt(),
                    rank = row.rank.toInt(),
                    categoryId = row.searchCategoryId,
                    scopeBookId = row.searchBookId,
                    tocId = row.searchTocId,
                    extended = row.searchExtended,
                    model = row.model,
                    gradedAt = row.gradedAt,
                )
            }
        val searches =
            queries.searchesSince(sent.searches).executeAsList().mapNotNull { row ->
                val query = row.searchQuery.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SearchRow(
                    query = query,
                    categoryId = row.searchCategoryId,
                    bookId = row.searchBookId,
                    tocId = row.searchTocId,
                    extended = row.searchExtended,
                    count = row.visitCount,
                    lastAt = row.visitedAt,
                )
            }
        return FeedbackContents(grades, searches, dafReviews(sent.daf))
    }

    /**
     * Each tractate's review with its marks newer than [since] (the files are left as they are). A mark that does not
     * say when it was made goes in every package; the receiver keeps one mark a line.
     */
    private fun dafReviews(since: Long): List<DafReviewFile> {
        val folder = dafReviewsFolder()?.takeIf { it.isDirectory } ?: return emptyList()
        return folder
            .listFiles { f -> f.isFile && f.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull { file -> runCatching { json.decodeFromString(DafReviewFile.serializer(), file.readText()) }.getOrNull() }
            .map { review -> review.copy(marks = review.marks.filter { mark -> (markedAt(mark.at) ?: Long.MAX_VALUE) > since }) }
            .filter { it.marks.isNotEmpty() }
    }

    companion object {
        /** The daf reviews' folder of the library in [libraryDir], as DafLayouts.of(libraryDir).reviews.folder. */
        fun dafFolderOf(libraryDir: File): File = File(File(libraryDir, DafLayouts.DIR_NAME), DafReviews.FOLDER)
    }
}

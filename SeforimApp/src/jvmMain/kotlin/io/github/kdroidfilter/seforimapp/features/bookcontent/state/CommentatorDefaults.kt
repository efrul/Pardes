package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.sanitizeCommentatorName
import io.github.kdroidfilter.seforimapp.features.search.domain.LibraryCategories
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A reader's default commentators for a book, by names rather than ids: the groups checked, as the list names them, and
 * the commentators checked or unchecked one by one, by their names beside the book ("רש״י", not "רש״י על בראשית"). The
 * same default then reads on the books beside it, and on a new library.
 */
@Serializable
data class CommentatorDefault(
    val all: Boolean = false,
    val groups: Set<String> = emptySet(),
    val hiddenGroups: Set<String> = emptySet(),
    val names: Set<String> = emptySet(),
    val excluded: Set<String> = emptySet(),
) {
    /** This default on a book whose commentators are [commentators] (name to id); one it does not have is left out. */
    fun selection(commentators: Map<String, Long>): CommentatorSelection =
        CommentatorSelection(
            remembered = names.mapNotNullTo(LinkedHashSet()) { commentators[it] },
            all = all,
            groups = groups,
            hiddenGroups = hiddenGroups,
            excluded = excluded.mapNotNullTo(LinkedHashSet()) { commentators[it] },
        )

    companion object {
        /** [selection] by names, [nameOf] giving a commentator's (null: unknown, left out). */
        fun of(
            selection: CommentatorSelection,
            nameOf: (Long) -> String?,
        ): CommentatorDefault =
            CommentatorDefault(
                all = selection.all,
                groups = selection.groups,
                hiddenGroups = selection.hiddenGroups,
                names = selection.remembered.mapNotNullTo(LinkedHashSet(), nameOf),
                excluded = selection.excluded.mapNotNullTo(LinkedHashSet(), nameOf),
            )
    }
}

/** A default as kept: set on the book [bookTitle] under [categoryPath] (its categories' titles, the root first). */
data class KeptCommentatorDefault(
    val categoryPath: List<String>,
    val bookTitle: String,
    val default: CommentatorDefault,
    val updatedAt: Long,
) {
    companion object {
        /**
         * The default of [kept] that the book [bookTitle] under [categoryPath] takes: its own; else that of a book
         * beside it, in its category; else that of a book under the category above (קידושין's for every tractate of
         * the Bavli). Of several, the last one set. Null when none: the library's own default then.
         */
        fun forBook(
            kept: List<KeptCommentatorDefault>,
            categoryPath: List<String>,
            bookTitle: String,
        ): KeptCommentatorDefault? {
            kept.firstOrNull { it.categoryPath == categoryPath && it.bookTitle == bookTitle }?.let { return it }
            kept.filter { it.categoryPath == categoryPath }.maxByOrNull { it.updatedAt }?.let { return it }
            // The library's top categories have nothing above them: a default never crosses from one to another
            val above = categoryPath.dropLast(1).ifEmpty { return null }
            return kept.filter { it.categoryPath.take(above.size) == above }.maxByOrNull { it.updatedAt }
        }
    }
}

/**
 * What the commentators list says of the reader's default for a book: the category whose books share one set on it
 * ([sharedIn]; null for a book that can have none, as the catalog doesn't have it), whether one applies to the book
 * ([isSet]), and whether it is what the book shows ([isShown]).
 */
data class CommentatorDefaultState(
    val sharedIn: String? = null,
    val isSet: Boolean = false,
    val isShown: Boolean = false,
)

/**
 * The readers' default commentators, kept in the user's database (`commentator_default`) and so in its backups: set on
 * a book from its commentators list, read when a book opens with no commentators chosen in its tab, before the
 * library's own default ([KeptCommentatorDefault.forBook] says which one a book takes).
 */
@Inject
@SingleIn(AppScope::class)
class CommentatorDefaults(
    database: UserSettingsDb,
    private val library: LibraryCategories,
) {
    private val queries = database.commentatorDefaultsQueries

    private val _revision = MutableStateFlow(0L)

    /** Bumped when a default is set or let go: what shows one reads it again. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** The reader's default for [bookId], as its commentator selection; null when none applies to it. */
    suspend fun of(bookId: Long): CommentatorSelection? {
        val (title, path) = library.bookPlace(bookId) ?: return null
        val kept = KeptCommentatorDefault.forBook(kept(), path, title) ?: return null
        return kept.default.selection(commentatorsOf(bookId, title))
    }

    /** Whether a default of the reader's applies to [bookId], and whether it is what [selection] shows. */
    suspend fun stateOf(
        bookId: Long,
        selection: CommentatorSelection,
    ): CommentatorDefaultState {
        val (title, path) = library.bookPlace(bookId) ?: return CommentatorDefaultState()
        // The books that take a default set here: those under the category above the book's (KeptCommentatorDefault.forBook)
        val sharedIn = path.dropLast(1).lastOrNull() ?: path.lastOrNull()
        val kept = KeptCommentatorDefault.forBook(kept(), path, title)?.default ?: return CommentatorDefaultState(sharedIn)
        // On the book it was set on, the names it kept; on another, what its names give there
        val shown = kept == byNames(bookId, title, selection) || kept.selection(commentatorsOf(bookId, title)) == selection
        return CommentatorDefaultState(sharedIn, isSet = true, isShown = shown)
    }

    /** Sets [selection], [bookId]'s, as the default of the book and of the books beside it. */
    suspend fun set(
        bookId: Long,
        selection: CommentatorSelection,
    ) {
        val (title, path) = library.bookPlace(bookId) ?: return
        val default = byNames(bookId, title, selection)
        withContext(Dispatchers.IO) {
            queries.upsert(
                categoryPath = Json.encodeToString(path),
                bookTitle = title,
                selection = Json.encodeToString(default),
                updatedAt = System.currentTimeMillis(),
            )
        }
        _revision.update { it + 1 }
    }

    /**
     * Lets go of the default [bookId] takes, its own or that of the book it has it from: the books that took it open
     * with the library's own again.
     */
    suspend fun clear(bookId: Long) {
        val (title, path) = library.bookPlace(bookId) ?: return
        val kept = KeptCommentatorDefault.forBook(kept(), path, title) ?: return
        withContext(Dispatchers.IO) { queries.delete(Json.encodeToString(kept.categoryPath), kept.bookTitle) }
        _revision.update { it + 1 }
    }

    private suspend fun kept(): List<KeptCommentatorDefault> =
        withContext(Dispatchers.IO) {
            queries.selectAll().executeAsList().mapNotNull { row ->
                runCatching {
                    KeptCommentatorDefault(
                        categoryPath = Json.decodeFromString(row.categoryPath),
                        bookTitle = row.bookTitle,
                        default = Json.decodeFromString(row.selection),
                        updatedAt = row.updatedAt,
                    )
                }.getOrNull()
            }
        }

    // The book's commentators by their names beside it
    private suspend fun commentatorsOf(
        bookId: Long,
        bookTitle: String,
    ): Map<String, Long> =
        library.commentariesOn(bookId).entries.associate { (id, title) -> sanitizeCommentatorName(title, bookTitle) to id }

    private suspend fun byNames(
        bookId: Long,
        bookTitle: String,
        selection: CommentatorSelection,
    ): CommentatorDefault {
        val titles = library.commentariesOn(bookId)
        val books = library.books()
        return CommentatorDefault.of(selection) { id -> (titles[id] ?: books[id]?.title)?.let { sanitizeCommentatorName(it, bookTitle) } }
    }
}

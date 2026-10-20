package io.github.kdroidfilter.seforimapp.legacy.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.android.search.SearchService
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchBarViewModel
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchViewModel
import io.github.kdroidfilter.seforimapp.legacy.db.SqliteException
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneTuning
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.lucene.store.AndroidHeapFilesDirectory
import org.apache.lucene.store.NIOFSDirectory

/**
 * The desktop search in this edition: the main app's SearchService, on the prebuilt Lucene indexes of the release
 * next to seforim.db, at one of three levels:
 *  - [Level.Full]: the full-text index (seforim.db.lucene, with lexical.db's forms of each word), the main app's search;
 *  - [Level.Lookup]: the lookup index alone (seforim.db.lookup.lucene, 1.4 MB in the release, so it fits wherever the
 *    database does): book and author suggestions, while the text is searched by the scan (TextSearch);
 *  - [Level.Scan]: neither, as before: the scan, and books found by their titles in the database.
 * The level is what the library folder holds, less what this phone cannot run: a heap under [SMALL_HEAP_BYTES] even
 * with android:largeHeap keeps to the lookup (unless Prefs.searchIndexOnSmallHeap), and an index that failed is left
 * closed. Anything Lucene throws while opening or searching (a VerifyError or other LinkageError, an OutOfMemoryError,
 * an IOException such as a CorruptIndexException or a missing file) closes that index for the session, and is recorded
 * with the library's release and this app's version code (Prefs.searchFailure), so that a failing index is not opened
 * again at every start, until the library or the app changes or [retry] asks again.
 *
 * Opening runs on a background thread ([prepare], [service]), never in attachBaseContext: the first open loads about
 * 1,265 Lucene classes. It also runs a small search on each index, so that a class this Android lacks, a missing or
 * corrupt file shows up then rather than in the user's first search.
 *
 * The search screen uses [service] with the main app's view models ([searchViewModel], [searchBarViewModel]), and
 * [search], [suggestBooks] and [highlightRanges] directly.
 */
class LegacySearch(context: Context, private val library: Library, private val prefs: Prefs) {
    /** From the least to the most: compare them. */
    enum class Level { Scan, Lookup, Full }

    private val heapBytes = Runtime.getRuntime().maxMemory()

    @Suppress("DEPRECATION")
    private val versionCode = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionCode }.getOrDefault(0)

    /**
     * How Lucene runs here, against a 48 MB heap or so, a core or two and a FUSE memory card: one slice, 8 forms of
     * each word instead of 32, the facets after the first page, a query cache of 2 MB (4 MB from a heap of 96 MB), and
     * the terms index with the small files in the heap. Measured on the JVM (HeapBench, the dev subset's index): a
     * three-word search then runs in a 12 MB heap, where the main app's settings need 24 MB with 2 slices, and over all
     * books makes 12,729 reads instead of 25,177 (none of them in the terms index, 8,342 before).
     */
    val tuning = LuceneTuning(
        slices = 1,
        maxSynonymTermsPerToken = FORMS_PER_WORD,
        facetsWithFirstPage = false,
        queryCacheBytes = if (heapBytes >= LARGE_HEAP_BYTES) 4L shl 20 else 2L shl 20,
        directory = { path -> AndroidHeapFilesDirectory(NIOFSDirectory(path), HEAP_EXTENSIONS, HEAP_MAX_FILE_BYTES) },
    )

    init {
        // Read by lucene-android when an index opens (Lucene99HnswVectorsFormat): the vectors (semantic search) are
        // never searched here, so they are not opened, and a library may be copied without their files.
        // Its read buffers stay Lucene's 1 KB (lucene.android.niofsBufferSize unset). 8 KB ones cut a three-word search
        // of the dev subset from 12,729 reads to 3,843 on the JVM, but on Android 4.4 each is a fresh allocation for
        // each postings and norms reader, and Dalvik met nearly every one with a full collection ("Grow heap (frag
        // case) ... for 8204-byte allocation", 1,700 of them): the same search took 76 s instead of 46 s on the
        // emulator.
        System.setProperty(SKIP_VECTORS_PROPERTY, "true")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _level = MutableStateFlow(Level.Scan)

    /** The level searches run at now: set when the search opens ([service]), lowered when an index fails. */
    val level: StateFlow<Level> = _level.asStateFlow()

    /** The library folder the search was opened for, and its service (one for the session: it reopens what [allows]). */
    private class Opened(val dir: File, val service: SearchService)

    @Volatile
    private var opened: Opened? = null

    /** The indexes that failed in this session for reasons not their own (a database read), left closed until [retry]. */
    private val sessionFailures = HashSet<String>()

    /** Opens the search in the background, if it is not open yet. */
    fun prepare() {
        scope.launch { runCatching { service() } }
    }

    /**
     * The main app's SearchService on the open library, opened and tried at the level this phone can run (blocking
     * work on Dispatchers.IO), or null without a library or at [Level.Scan]. The same service for a library folder:
     * after a failure it leaves the failed index closed (its engine() or lookup() returns null).
     */
    suspend fun service(): SearchService? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = runCatching { library.open().file.parentFile }.getOrNull()
            if (dir == null) {
                _level.value = Level.Scan
                return@withLock null
            }
            val target = chooseLevel(dir)
            val current = opened
            if (current != null && current.dir == dir && target == _level.value) {
                return@withLock current.service.takeIf { target != Level.Scan }
            }
            // A new library folder, or another level (an index copied in or removed, a failure, retry): open again
            current?.service?.close()
            val service = current?.service?.takeIf { current.dir == dir }
                ?: SearchService(LibraryStore(library), tuning, allowed = ::allows, onFailure = ::reportFailure)
            opened = Opened(dir, service)
            _level.value = target
            tryOpen(dir, service)
            service.takeIf { _level.value != Level.Scan }
        }
    }

    /** The level the folder's indexes allow on this phone, without loading Lucene. */
    private fun chooseLevel(dir: File): Level {
        val failed = recordedFailures(dir) + synchronized(sessionFailures) { sessionFailures.toSet() }
        val text = SearchService.isIndex(File(dir, SearchService.TEXT_INDEX)) && TEXT !in failed &&
            (heapBytes >= SMALL_HEAP_BYTES || prefs.searchIndexOnSmallHeap)
        val lookup = SearchService.isIndex(File(dir, SearchService.LOOKUP_INDEX)) && LOOKUP !in failed
        return when {
            text -> Level.Full
            lookup -> Level.Lookup
            else -> Level.Scan
        }
    }

    /** What SearchService may open now: the full-text index at [Level.Full], the lookup index unless it failed. */
    private fun allows(index: String): Boolean {
        val dir = opened?.dir ?: return false
        return when (index) {
            SearchService.TEXT_INDEX -> _level.value == Level.Full
            else -> _level.value != Level.Scan && LOOKUP !in recordedFailures(dir) && synchronized(sessionFailures) { LOOKUP !in sessionFailures }
        }
    }

    /** Opens the indexes the level allows and searches each once; a failure lowers the level and tries again. */
    private suspend fun tryOpen(dir: File, service: SearchService) {
        while (_level.value != Level.Scan) {
            val level = _level.value
            val start = SystemClock.elapsedRealtime()
            var part = TEXT
            try {
                if (level == Level.Full) {
                    lucene(TEXT, dir) { service.engine()?.searchBooksByTitlePrefix(PROBE_QUERY, 1) }
                }
                part = LOOKUP
                lucene(LOOKUP, dir) { service.lookup()?.suggestBooks(PROBE_QUERY, 1) }
                Log.i(TAG, "search: $level, opened in ${SystemClock.elapsedRealtime() - start} ms (heap ${heapBytes shr 20} MB)")
                return
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Recorded already when it came from Lucene; either way that index stays closed now
                if (!isLuceneFailure(t)) Log.w(TAG, "search: the $part index could not be read", t)
                synchronized(sessionFailures) { sessionFailures += part }
            }
            service.close()
            _level.value = chooseLevel(dir)
        }
        Log.i(TAG, "search: ${Level.Scan} (heap ${heapBytes shr 20} MB)")
    }

    /** Runs a call on one index ([TEXT] or [LOOKUP]); what Lucene throws there is recorded as that index's failure. */
    private inline fun <T> lucene(index: String, dir: File, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        if (isLuceneFailure(t)) recordFailure(dir, index, t)
        throw t
    }

    /**
     * A search failed with [error] (SearchService's onFailure, from the main app's view models): when it came from
     * Lucene, the full-text index is closed for the session and the failure recorded.
     */
    fun reportFailure(error: Throwable) {
        val dir = opened?.dir ?: return
        if (!isLuceneFailure(error)) return
        recordFailure(dir, TEXT, error)
        reopen()
    }

    /** Forgets the recorded failures and opens the search again at the best level (the search screen's "נסה שוב"). */
    fun retry() {
        prefs.searchFailure = null
        synchronized(sessionFailures) { sessionFailures.clear() }
        reopen()
    }

    /** [service] in the background: closes what failed, opens what is now allowed. */
    private fun reopen() {
        scope.launch { runCatching { service() } }
    }

    /** A search on the full-text index: what Lucene throws is recorded, and the search reopened without it. */
    private suspend fun <T> textSearch(dir: File, block: suspend () -> T): T = try {
        lucene(TEXT, dir) { block() }
    } catch (t: Throwable) {
        if (t !is CancellationException && isLuceneFailure(t)) reopen()
        throw t
    }

    // ------------------------------------------------------------------ for the search screen

    /** A search of the full-text index: its first page and number of hits, then [more]. Close it when done. */
    class Results internal constructor(
        val query: String,
        val total: Long,
        val hits: List<LineHit>,
        val isLastPage: Boolean,
        private val session: SearchSession,
        private val next: suspend (SearchSession) -> List<LineHit>?,
    ) : AutoCloseable {
        /** The next page, or null after the last one. */
        suspend fun more(): List<LineHit>? = next(session)

        override fun close() = session.close()
    }

    /**
     * Searches [query] in the base books or in all books, as the main app's first page (with snippets); null below
     * [Level.Full] or for a query of nothing. Throws what the search threw, after recording it.
     */
    suspend fun search(query: String, baseBooksOnly: Boolean, pageSize: Int = PAGE_SIZE): Results? = withContext(Dispatchers.IO) {
        val service = service() ?: return@withContext null
        val dir = opened?.dir ?: return@withContext null
        textSearch(dir) {
            val engine = service.engine() ?: return@textSearch null
            val session = engine.openSession(query, baseBookOnly = baseBooksOnly) ?: return@textSearch null
            val page = try {
                session.nextPage(pageSize)
            } catch (t: Throwable) {
                session.close()
                throw t
            }
            Results(query, page?.totalHits ?: 0L, page?.hits.orEmpty(), page?.isLastPage ?: true, session) { s ->
                withContext(Dispatchers.IO) { textSearch(dir) { s.nextPage(pageSize) }?.hits }
            }
        }
    }

    /** Books for what is typed, by the lookup index's ranking (the main app's); null without it. */
    suspend fun suggestBooks(query: String, limit: Int): List<Book>? = withContext(Dispatchers.IO) {
        val lookup = service()?.lookup() ?: return@withContext null
        runCatching { lookup.suggestBooks(query, limit).map { it.toBook() } }
            .onFailure { if (it is CancellationException) throw it else Log.w(TAG, "search: suggestions", it) }
            .getOrNull()
    }

    /** The ranges of [text] that a search of [query] finds (the main app's highlights); empty below [Level.Full]. */
    suspend fun highlightRanges(text: String, query: String): List<IntRange> = withContext(Dispatchers.IO) {
        runCatching { service()?.engine()?.highlightRanges(text, query) }.getOrNull().orEmpty()
    }

    /** The main app's results view model on [service] (from [service]); the activity calls clear() when it finishes. */
    fun searchViewModel(service: SearchService, repository: SeforimRepository, destination: Destination.Search): SearchViewModel =
        SearchViewModel.factory(repository, service, destination).create(SearchViewModel::class.java)

    /** The main app's search bar view model on [service]. */
    fun searchBarViewModel(service: SearchService, repository: SeforimRepository): SearchBarViewModel =
        SearchBarViewModel.factory(repository, service).create(SearchBarViewModel::class.java)

    // ------------------------------------------------------------------ failures

    /** The indexes recorded as failed for this library release and this app's version. */
    private fun recordedFailures(dir: File): Set<String> {
        val parts = prefs.searchFailure?.split('|', limit = 4) ?: return emptySet()
        if (parts.size < 4 || parts[0] != release(dir) || parts[1] != versionCode.toString()) return emptySet()
        return parts[2].split(',').toSet()
    }

    private fun recordFailure(dir: File, index: String, error: Throwable) {
        Log.e(TAG, "search: the $index index failed, not opened again for this library", error)
        val failed = recordedFailures(dir) + index
        val message = (error.javaClass.name + ": " + error.message).replace('\n', ' ').take(MAX_MESSAGE)
        prefs.searchFailure = listOf(release(dir), versionCode.toString(), failed.sorted().joinToString(","), message).joinToString("|")
    }

    /** The library's release (release_info.txt, as desktop writes it), or "" when it has none. */
    private fun release(dir: File): String = runCatching { File(dir, RELEASE_INFO).readText().trim() }.getOrDefault("")

    companion object {
        private const val TAG = "Pardes"
        private const val TEXT = "text"
        private const val LOOKUP = "lookup"
        private const val RELEASE_INFO = "release_info.txt"
        private const val MAX_MESSAGE = 300
        private const val PROBE_QUERY = "בראשית"
        const val PAGE_SIZE = 25

        /** The main app's 32 forms of each word make a three-word search 600 to 1,000 terms. */
        const val FORMS_PER_WORD = 8

        /** Below this heap, even with android:largeHeap, the full-text index stays closed (Level.Lookup). */
        const val SMALL_HEAP_BYTES = 40L shl 20
        private const val LARGE_HEAP_BYTES = 96L shl 20

        /**
         * The files kept in the heap (AndroidHeapFilesDirectory): the terms index, looked up for each form of each
         * word (8,342 reads of 1 KB in a search of three words over the dev subset), the points and stored fields
         * indexes, and the small metadata files. The release's are under 0.5 MB each.
         */
        val HEAP_EXTENSIONS = setOf("tip", "kdi", "fdx", "tmd", "psm", "nvm", "dvm", "kdm", "fdm")
        const val HEAP_MAX_FILE_BYTES = 2L shl 20

        private const val SKIP_VECTORS_PROPERTY = "lucene.android.skipVectors"

        /** What Lucene throws when it cannot run or read here, as against a database read or a cancelled search. */
        fun isLuceneFailure(t: Throwable): Boolean = when {
            t is CancellationException -> false
            t is SqliteException -> false
            t is Error || t is IOException -> true
            else -> generateSequence(t) { it.cause }.take(8).any { e -> e.stackTrace.any { it.className.startsWith("org.apache.lucene.") } }
        }
    }
}

package io.github.kdroidfilter.seforimapp.android.search

import android.system.Os
import android.util.Log
import io.github.kdroidfilter.seforimapp.android.data.LibraryStore
import io.github.kdroidfilter.seforimapp.features.search.semantic.HybridSearchEngine
import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimapp.features.search.semantic.LibraryLines
import io.github.kdroidfilter.seforimapp.features.search.semantic.VectorIndexBuilder
import io.github.kdroidfilter.seforimapp.framework.search.AcronymFrequencyCache
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SnippetProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The desktop search on the installed library: the same prebuilt Lucene indexes and dictionary that the desktop app
 * keeps next to seforim.db (wired like Pardes desktop's AppCoreBindings provideSearchEngine, withSemanticSearch and
 * provideLuceneLookupSearchService), with Pardes's semantic search fused in when its pack is beside the library and the
 * device runs it ([SemanticSearchSupport]). Nothing is built on the device; when the files are missing, [engine] and
 * [lookup] return null and the UI explains how to get them.
 */
class SearchService(private val store: LibraryStore) {
    data class Part(val name: String, val present: Boolean, val bytes: Long)

    data class Status(val text: Part, val lookup: Part, val dictionary: Part) {
        val textSearchAvailable: Boolean get() = text.present
    }

    private class Opened(val key: String, val engine: SearchEngine?, val lookup: LuceneLookupSearchService?, val acronyms: AcronymFrequencyCache?) {
        fun close() {
            runCatching { engine?.close() }
            runCatching { lookup?.close() }
            runCatching { acronyms?.close() }
        }
    }

    private val mutex = Mutex()
    private var opened: Opened? = null

    val libraryDir: File get() = store.libraryDir

    /** Full-text engine, or null when seforim.db.lucene is missing. Opening can throw if Lucene cannot run here. */
    suspend fun engine(): SearchEngine? = open().engine

    /** Book title / acronym lookup, or null when seforim.db.lookup.lucene is missing. */
    suspend fun lookup(): LuceneLookupSearchService? = open().lookup

    /** Cheap check (no Lucene class is loaded) that the full-text index is installed. */
    suspend fun isTextSearchAvailable(): Boolean = withContext(Dispatchers.IO) { isIndex(File(store.libraryDir, TEXT_INDEX)) }

    suspend fun status(): Status = withContext(Dispatchers.IO) {
        val dir = store.libraryDir
        fun part(name: String): Part {
            val f = File(dir, name)
            val present = if (name == DICTIONARY) f.isFile && f.length() > 0 else isIndex(f)
            return Part(name, present, if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length())
        }
        Status(part(TEXT_INDEX), part(LOOKUP_INDEX), part(DICTIONARY))
    }

    /** Releases the open index readers and database connections (the next call reopens them). */
    suspend fun close() = mutex.withLock {
        opened?.close()
        opened = null
    }

    private suspend fun open(): Opened = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = store.libraryDir
            val key = libraryKey(dir)
            opened?.let { if (it.key == key) return@withLock it }
            // The library was replaced (reinstall, files pushed from a computer): drop handles on the old files.
            opened?.close()
            opened = null
            val text = File(dir, TEXT_INDEX)
            val lookupDir = File(dir, LOOKUP_INDEX)
            val engine = if (isIndex(text)) {
                // The repository is looked up per page: the installer closes and reopens it when it replaces the library.
                val snippets = SnippetProvider { lines -> RepositorySnippetSourceProvider(store.repository()).getSnippetSources(lines) }
                withSemanticSearch(LuceneSearchEngine(text.toPath(), snippets, dictionaryPath = File(dir, DICTIONARY).toPath()), text)
            } else {
                null
            }
            val acronyms = if (isIndex(lookupDir) && store.databaseFile.isFile) {
                runCatching { AcronymFrequencyCache(store.databaseFile) }.onFailure { Log.w(TAG, "acronym cache", it) }.getOrNull()
            } else {
                null
            }
            val lookup = if (isIndex(lookupDir)) {
                // When Lucene cannot load here this throws on every call (each keystroke on home): do not leak the cache.
                runCatching { LuceneLookupSearchService(lookupDir.toPath(), acronymCache = acronyms) }.onFailure { acronyms?.close() }.getOrThrow()
            } else {
                null
            }
            Opened(key, engine, lookup, acronyms).also { opened = it }
        }
    }

    /**
     * Desktop's withSemanticSearch: [lexical] fused with Pardes's semantic search, which loads its model when the first
     * search asks for it and searches by the words alone without its pack ([HybridSearchEngine]); [lexical] itself on a
     * device that cannot run the model. A line only the meaning finds is read from the library as desktop reads it.
     */
    private fun withSemanticSearch(lexical: LuceneSearchEngine, indexDir: File): SearchEngine {
        if (!SemanticSearchSupport.supported) return lexical
        val titles = HashMap<Long, String>()
        return HybridSearchEngine(lexical, indexDir.toPath(), LibraryLines { store.driver() }) { lineId, query ->
            val repository = store.repository()
            repository.getLine(lineId)?.let { line ->
                // Desktop reads getBook(id).title for each line found: its row and four more tables. The row alone, once a book
                val title = synchronized(titles) { titles[line.bookId] }
                    ?: repository.getBookCore(line.bookId)?.title.orEmpty().also { found -> synchronized(titles) { titles[line.bookId] = found } }
                LineHit(
                    bookId = line.bookId,
                    bookTitle = title,
                    lineId = lineId,
                    lineIndex = line.lineIndex,
                    snippet = lexical.buildSnippet(line.content, query, 5),
                    score = 0f,
                    rawText = line.content,
                )
            }
        }
    }

    /** Changes when any search file is replaced: index commits by name, databases by inode, the semantic pack's model too. */
    private fun libraryKey(dir: File): String {
        fun segments(name: String) = File(dir, name).list()?.filter { it.startsWith("segments_") }?.sorted()?.joinToString(",").orEmpty()
        fun inode(file: File) = runCatching { Os.stat(file.path).st_ino }.getOrDefault(-1L)
        val model = File(dir, "${IndependentSemanticSearch.OPEN_MODEL_DIR_NAME}/pardes-model.json")
        return listOf(
            dir.path, segments(TEXT_INDEX), segments(LOOKUP_INDEX), inode(File(dir, DICTIONARY)), inode(store.databaseFile),
            segments(VectorIndexBuilder.DIR_NAME), inode(model),
        ).joinToString("|")
    }

    companion object {
        private const val TAG = "Zayit"

        // Same names as the desktop (AppCoreBindings): "<db>.lucene", "<db>.lookup.lucene" and lexical.db beside seforim.db.
        const val TEXT_INDEX = "${LibraryStore.DB_NAME}.lucene"
        const val LOOKUP_INDEX = "${LibraryStore.DB_NAME}.lookup.lucene"
        const val DICTIONARY = "lexical.db"

        /** A Lucene index directory has at least one commit point (segments_N). */
        fun isIndex(dir: File): Boolean = dir.list()?.any { it.startsWith("segments_") } == true
    }
}

/**
 * [SearchEngine.computeFacets] on [Dispatchers.IO], stopped between chunks of hits once the calling coroutine is
 * cancelled. Never wrap a search in runInterruptible: an interrupted read closes the NIOFSDirectory FileChannel, and the
 * engine shares one index reader with every search, which would then all fail until the engine is reopened.
 */
suspend fun SearchEngine.countFacets(query: String, near: Int, bookIds: Collection<Long>? = null, baseBookOnly: Boolean = false): SearchFacets? =
    withContext(Dispatchers.IO) {
        val job = coroutineContext.job
        // The facets are the words' alone, the hybrid's too (HybridSearchEngine.computeFacets)
        val engine = (this@countFacets as? HybridSearchEngine)?.lexicalEngine ?: this@countFacets
        if (engine is LuceneSearchEngine) {
            engine.computeFacetsCancellable(query, near, bookIds = bookIds, baseBookOnly = baseBookOnly) { !job.isActive }
        } else {
            engine.computeFacets(query, near, bookIds = bookIds, baseBookOnly = baseBookOnly)
        }
    }

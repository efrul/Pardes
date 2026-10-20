// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforimlibrary.search

import java.nio.file.Path
import org.apache.lucene.store.Directory
import org.apache.lucene.store.NIOFSDirectory

/**
 * How the search runs Lucene on this device. Android only: desktop has its constants. The defaults are this app's
 * values, the constants they were before, and this app never passes another. The Android 4.4 edition does (its
 * data/LegacySearch.kt): a phone there has a heap of 48 MB or so, a slow core or two and its library on a FUSE memory
 * card, where four slices of a three-word search, with the facets counted beside them, would not fit.
 */
class LuceneTuning(
    /** Partitions a search is split into, each searched on a thread of its own; 1 searches on the caller's thread. */
    val slices: Int = minOf(Runtime.getRuntime().availableProcessors(), 4),
    /** The most dictionary forms (inflections, spellings) a word of the query is searched with, itself included. */
    val maxSynonymTermsPerToken: Int = 32,
    /** The facets (category tabs, "עוד N תוצאות") are counted beside the first page; false: once it is shown. */
    val facetsWithFirstPage: Boolean = true,
    /** Lucene's query cache for every searcher, in bytes; null keeps Lucene's (up to 32 MB, a 20th of the heap). */
    val queryCacheBytes: Long? = null,
    /** Opens an index folder, the full-text one and the lookup one. MMapDirectory needs java.lang.foreign. */
    val directory: (Path) -> Directory = ::NIOFSDirectory,
)

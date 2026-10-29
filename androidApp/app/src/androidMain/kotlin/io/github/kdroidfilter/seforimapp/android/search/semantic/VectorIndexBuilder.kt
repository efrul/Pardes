// Android variant of Pardes desktop's SeforimApp/src/jvmMain/.../features/search/semantic/VectorIndexBuilder.kt, same
// package and object, with its read side only: the names and readInfo, which the search and the pack installer check.
// Building the index (build, linesOf, main) is desktop tooling run on a computer over the library's JDBC database; a
// phone only reads the index the pack brings. Changes, marked "Android:": the index is opened with NIOFSDirectory
// (FSDirectory.open would pick MMapDirectory, which needs java.lang.foreign).
package io.github.kdroidfilter.seforimapp.features.search.semantic

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.NIOFSDirectory
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Pardes vector index (`pardes.vec.lucene`, next to seforim.db.lucene), built on a computer by desktop's
 * VectorIndexBuilder from the vectors of semantic-model/embed_corpus.py: its documents have the fields of
 * SeforimLibrary's LuceneTextIndexWriter that [IndependentSemanticSearch] reads (line_id, book_id, and the int8 vector
 * in `vec`, or `vec_base` for a base book's line), and its commit records the model it was embedded with
 * ([KEY_MODEL]): the app searches it only with that model.
 */
object VectorIndexBuilder {
    const val DIR_NAME = "pardes.vec.lucene"
    const val KEY_MODEL = "pardes.model"
    const val KEY_DIMENSION = "pardes.dimension"
    const val KEY_LINES = "pardes.lines"
    const val KEY_DB_RELEASE = "pardes.dbRelease"

    data class Info(
        val model: String?,
        val dimension: Int?,
        val lines: Long?,
        val dbRelease: String?,
    )

    /** What the commit of the vector index at [dir] says, or null when there is no index there. */
    fun readInfo(dir: Path): Info? {
        if (!Files.isDirectory(dir)) return null
        // Android: NIOFSDirectory
        NIOFSDirectory(dir).use { directory ->
            if (!DirectoryReader.indexExists(directory)) return null
            DirectoryReader.open(directory).use { reader ->
                val data = reader.indexCommit.userData
                return Info(
                    data[KEY_MODEL],
                    data[KEY_DIMENSION]?.toIntOrNull(),
                    data[KEY_LINES]?.toLongOrNull(),
                    data[KEY_DB_RELEASE],
                )
            }
        }
    }
}

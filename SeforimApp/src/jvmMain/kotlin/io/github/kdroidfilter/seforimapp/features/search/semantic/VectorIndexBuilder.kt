package io.github.kdroidfilter.seforimapp.features.search.semantic

import org.apache.lucene.codecs.KnnVectorsFormat
import org.apache.lucene.codecs.lucene104.Lucene104Codec
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat
import org.apache.lucene.document.Document
import org.apache.lucene.document.IntPoint
import org.apache.lucene.document.KnnByteVectorField
import org.apache.lucene.document.StoredField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.store.FSDirectory
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.BitSet
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Builds the Pardes vector index (`pardes.vec.lucene`, next to seforim.db.lucene) from the vectors of
 * semantic-model/embed_corpus.py (ids.i64 + vecs.f32 + meta.txt, the format SeforimLibrary's generator reads, or
 * vecs.i8 of quantize_vectors.py, the same vectors already quantized) and seforim.db (each line's book and whether it
 * is a base book).
 *
 * A vector-only index of its own rather than vectors in seforim.db.lucene: the full-text index stays the library
 * release's as downloaded, and the semantic one can be replaced alone. Its documents have the fields of
 * SeforimLibrary's LuceneTextIndexWriter that [IndependentSemanticSearch] reads (line_id, book_id, and the int8
 * vector in `vec`, or `vec_base` for a base book's line), and its commit records the model it was embedded with
 * ([KEY_MODEL]): the app searches it only with that model.
 *
 *     java -cp <app classpath> io.github.kdroidfilter.seforimapp.features.search.semantic.VectorIndexBuilder \
 *         --vectors build/vectors --db seforim.db --out pardes.vec.lucene
 */
object VectorIndexBuilder {
    const val DIR_NAME = "pardes.vec.lucene"
    const val KEY_MODEL = "pardes.model"
    const val KEY_DIMENSION = "pardes.dimension"
    const val KEY_LINES = "pardes.lines"
    const val KEY_DB_RELEASE = "pardes.dbRelease"

    private const val FIELD_LINE_ID = "line_id"
    private const val FIELD_BOOK_ID = "book_id"
    private const val FIELD_VEC = "vec"
    private const val FIELD_VEC_BASE = "vec_base"
    private const val FLOAT_VECTORS = "vecs.f32"
    private const val INT8_VECTORS = "vecs.i8"

    data class Info(
        val model: String?,
        val dimension: Int?,
        val lines: Long?,
        val dbRelease: String?,
    )

    /** What the commit of the vector index at [dir] says, or null when there is no index there. */
    fun readInfo(dir: Path): Info? {
        if (!Files.isDirectory(dir)) return null
        FSDirectory.open(dir).use { directory ->
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

    /** Builds the index at [out] (replacing one there); returns the number of vectors written. */
    fun build(
        vectorsDir: Path,
        db: Path,
        out: Path,
        modelId: String,
        dbRelease: String?,
        log: (String) -> Unit,
    ): Long {
        val meta = Files.readString(vectorsDir.resolve("meta.txt")).trim().split(Regex("\\s+"))
        val count = meta[0].toLong()
        val dimension = meta[1].toInt()
        val ids = ByteBuffer.wrap(Files.readAllBytes(vectorsDir.resolve("ids.i64"))).order(ByteOrder.LITTLE_ENDIAN)
        require(ids.remaining().toLong() >= count * 8) { "ids.i64 holds fewer than $count ids" }

        // int8 rows when quantize_vectors.py made them (quantizeInt8 done ahead), else float32 rows quantized here
        val int8 = Files.isRegularFile(vectorsDir.resolve(INT8_VECTORS))
        val vectorsFile = vectorsDir.resolve(if (int8) INT8_VECTORS else FLOAT_VECTORS)
        val rowSize = if (int8) dimension else dimension * 4
        require(Files.size(vectorsFile) >= count * rowSize) { "${vectorsFile.fileName} holds fewer than $count vectors" }

        val (bookOf, isBase) = linesOf(db)
        log("$count vectors of $dimension dimensions (${vectorsFile.fileName}); ${bookOf.size} line ids in ${db.fileName}")

        val workers = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(workers)
        // Plain HNSW over the int8 bytes, as LuceneTextIndexWriter: the quantized formats would also keep the floats
        val vectorsFormat = Lucene99HnswVectorsFormat(16, 100, workers, executor)
        val config =
            IndexWriterConfig().apply {
                openMode = IndexWriterConfig.OpenMode.CREATE
                codec =
                    object : Lucene104Codec() {
                        override fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat = vectorsFormat
                    }
                ramBufferSizeMB = 1024.0
            }
        var written = 0L
        var missing = 0L
        try {
            FSDirectory.open(out).use { directory ->
                IndexWriter(directory, config).use { writer ->
                    DataInputStream(BufferedInputStream(Files.newInputStream(vectorsFile), 1 shl 20))
                        .use { input ->
                            val row = ByteArray(rowSize)
                            val rowBuffer = ByteBuffer.wrap(row).order(ByteOrder.LITTLE_ENDIAN)
                            val floats = FloatArray(dimension)
                            for (i in 0 until count) {
                                input.readFully(row)
                                val lineId = ids.long
                                val book = if (lineId in 0 until bookOf.size) bookOf[lineId.toInt()] else 0
                                if (book == 0) {
                                    // A line the database no longer has (vectors of another release)
                                    missing++
                                    continue
                                }
                                val vector =
                                    if (int8) {
                                        row.copyOf()
                                    } else {
                                        rowBuffer.rewind()
                                        for (j in 0 until dimension) floats[j] = rowBuffer.float
                                        OnnxEmbeddingProvider.quantizeInt8(floats)
                                    }
                                val field = if (isBase[book]) FIELD_VEC_BASE else FIELD_VEC
                                writer.addDocument(
                                    Document().apply {
                                        add(StoredField(FIELD_LINE_ID, lineId))
                                        add(IntPoint(FIELD_LINE_ID, lineId.toInt()))
                                        add(StoredField(FIELD_BOOK_ID, book.toLong()))
                                        add(IntPoint(FIELD_BOOK_ID, book))
                                        add(KnnByteVectorField(field, vector, VectorSimilarityFunction.COSINE))
                                    },
                                )
                                written++
                                if (written % 200_000 == 0L) log("$written/$count")
                            }
                        }
                    log("Merging into one segment (one HNSW graph per field)")
                    writer.forceMerge(1)
                    writer.setLiveCommitData(
                        buildMap {
                            put(KEY_MODEL, modelId)
                            put(KEY_DIMENSION, dimension.toString())
                            put(KEY_LINES, written.toString())
                            dbRelease?.let { put(KEY_DB_RELEASE, it) }
                        }.entries,
                    )
                    writer.commit()
                }
            }
        } finally {
            executor.shutdown()
        }
        if (missing > 0) log("$missing vectors skipped: their lines are not in ${db.fileName}")
        return written
    }

    /** Each line's book (0: no such line), by line id, and which books are base books, by book id. */
    private fun linesOf(db: Path): Pair<IntArray, BitSet> {
        DriverManager.getConnection("jdbc:sqlite:file:$db?mode=ro").use { con ->
            con.createStatement().use { st ->
                val maxId =
                    st.executeQuery("SELECT max(id) FROM line").use { rs ->
                        rs.next()
                        rs.getInt(1)
                    }
                val bookOf = IntArray(maxId + 1)
                st.executeQuery("SELECT id, bookId FROM line").use { rs ->
                    while (rs.next()) bookOf[rs.getInt(1)] = rs.getInt(2)
                }
                val base = BitSet()
                st.executeQuery("SELECT id FROM book WHERE isBaseBook = 1").use { rs ->
                    while (rs.next()) base.set(rs.getInt(1))
                }
                return bookOf to base
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val opts = args.toList().chunked(2).associate { it[0].removePrefix("--") to it.getOrElse(1) { "" } }
        val vectors = opts["vectors"]?.let(Path::of)
        val db = opts["db"]?.let(Path::of)
        val out = opts["out"]?.let(Path::of)
        if (vectors == null || db == null || out == null) {
            System.err.println("usage: VectorIndexBuilder --vectors <dir> --db <seforim.db> --out <pardes.vec.lucene> [--model <id>]")
            exitProcess(2)
        }
        val info =
            vectors
                .resolve("embed-info.json")
                .takeIf(Files::isRegularFile)
                ?.let { JSONObject(Files.readString(it, StandardCharsets.UTF_8)) }
        val model = opts["model"] ?: info?.optString("model")?.takeIf { it.isNotEmpty() }
        if (model == null) {
            System.err.println("No model id: pass --model, or keep embed-info.json next to the vectors")
            exitProcess(2)
        }
        val release =
            opts["release"] ?: info?.optString("dbRelease")?.takeIf { it.isNotEmpty() }
                ?: db.resolveSibling("release_info.txt").takeIf(Files::isRegularFile)?.let { Files.readString(it).trim() }
        val start = System.nanoTime()
        val n = build(vectors, db, out, model, release) { println(it) }
        println("$n vectors -> $out (model $model, release $release) in ${(System.nanoTime() - start) / 1_000_000_000}s")
    }
}

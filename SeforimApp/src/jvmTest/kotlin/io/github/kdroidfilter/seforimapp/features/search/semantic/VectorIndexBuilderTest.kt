package io.github.kdroidfilter.seforimapp.features.search.semantic

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class VectorIndexBuilderTest {
    private val root = createTempDirectory().toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `float32 and int8 vectors give the same index, base books apart, missing lines skipped`() {
        val db = database()
        // Line 4 is not in the database (vectors of another release)
        val ids = longArrayOf(1, 2, 4, 3)
        val floats = ids.map { id -> FloatArray(DIM) { j -> ((id * 7 + j * 3) % 11 - 5) / 5f } }

        val fromFloats = vectors("f32", ids) { dir -> write(dir.resolve("vecs.f32"), floats) }
        val fromInt8 =
            vectors("i8", ids) { dir ->
                Files.write(dir.resolve("vecs.i8"), floats.map(OnnxEmbeddingProvider::quantizeInt8).reduce(ByteArray::plus))
            }

        val a = root.toPath().resolve("a")
        val b = root.toPath().resolve("b")
        assertEquals(3, VectorIndexBuilder.build(fromFloats, db, a, MODEL, "r1") {})
        assertEquals(3, VectorIndexBuilder.build(fromInt8, db, b, MODEL, "r1") {})

        val expected = mapOf(1L to floats[0], 2L to floats[1], 3L to floats[3]).mapValues { OnnxEmbeddingProvider.quantizeInt8(it.value) }
        for (index in listOf(a, b)) {
            assertEquals(VectorIndexBuilder.Info(MODEL, DIM, 3, "r1"), VectorIndexBuilder.readInfo(index))
            val (plain, base) = vectorsOf(index)
            assertEquals(setOf(1L, 2L), plain.keys)
            assertEquals(setOf(3L), base.keys)
            for ((id, vector) in plain + base) assertContentEquals(expected.getValue(id), vector)
        }
    }

    /** Line id -> int8 vector, of the `vec` and the `vec_base` field. */
    private fun vectorsOf(index: Path): Pair<Map<Long, ByteArray>, Map<Long, ByteArray>> =
        FSDirectory.open(index).use { directory ->
            DirectoryReader.open(directory).use { reader ->
                val leaf = reader.leaves().single().reader()
                val stored = leaf.storedFields()

                fun field(name: String): Map<Long, ByteArray> {
                    val values = leaf.getByteVectorValues(name) ?: return emptyMap()
                    return (0 until values.size()).associate { ord ->
                        val lineId = stored.document(values.ordToDoc(ord)).getField("line_id").numericValue()
                        lineId.toLong() to values.vectorValue(ord).copyOf()
                    }
                }
                field("vec") to field("vec_base")
            }
        }

    private fun vectors(
        name: String,
        ids: LongArray,
        rows: (Path) -> Unit,
    ): Path =
        root.toPath().resolve(name).also { dir ->
            Files.createDirectories(dir)
            val buffer = ByteBuffer.allocate(ids.size * 8).order(ByteOrder.LITTLE_ENDIAN)
            ids.forEach(buffer::putLong)
            Files.write(dir.resolve("ids.i64"), buffer.array())
            Files.writeString(dir.resolve("meta.txt"), "${ids.size} $DIM\n")
            rows(dir)
        }

    private fun write(
        file: Path,
        rows: List<FloatArray>,
    ) {
        val buffer = ByteBuffer.allocate(rows.size * DIM * 4).order(ByteOrder.LITTLE_ENDIAN)
        rows.forEach { row -> row.forEach(buffer::putFloat) }
        Files.write(file, buffer.array())
    }

    /** Lines 1 and 2 in book 10, line 3 in book 20 (a base book). */
    private fun database(): Path {
        val db = root.toPath().resolve("seforim.db")
        DriverManager.getConnection("jdbc:sqlite:$db").use { con ->
            con.createStatement().use { st ->
                st.executeUpdate("CREATE TABLE book (id INTEGER PRIMARY KEY, isBaseBook INTEGER)")
                st.executeUpdate("CREATE TABLE line (id INTEGER PRIMARY KEY, bookId INTEGER)")
                st.executeUpdate("INSERT INTO book VALUES (10, 0), (20, 1)")
                st.executeUpdate("INSERT INTO line VALUES (1, 10), (2, 10), (3, 20)")
            }
        }
        return db
    }

    private companion object {
        const val DIM = 8
        const val MODEL = "student@0000000/pardes-clean-1"
    }
}

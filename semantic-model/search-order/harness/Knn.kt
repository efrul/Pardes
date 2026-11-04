import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimapp.features.search.semantic.OnnxEmbeddingProvider
import org.apache.lucene.document.IntPoint
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnByteVectorQuery
import org.apache.lucene.search.ScoreMode
import org.apache.lucene.search.knn.KnnSearchStrategy
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import java.util.BitSet
import java.util.Properties

// Where the dense side's time goes in a category scope: each field's filtered KNN on its own, the filter's size, and
// an exact search over the filter's vectors (what Lucene falls back to), for the same queries.
// args: seforim.db queries.txt categoryIds(comma separated)
fun main(args: Array<String>) {
    val db = Path.of(args[0])
    val indexPath = Path.of("$db.lucene")
    val spec = IndependentSemanticSearch.findOpenModel(indexPath)!!
    val provider = OnnxEmbeddingProvider.open(spec)
    val dir = FSDirectory.open(IndependentSemanticSearch.openVectorIndexDir(indexPath))
    val reader = DirectoryReader.open(dir)
    val searcher = IndexSearcher(reader)
    System.err.println("segments ${reader.leaves().size}, docs ${reader.maxDoc()}")
    val con = DriverManager.getConnection("jdbc:sqlite:$db", Properties().apply { put("open_mode", "1") })

    fun booksUnder(categoryId: Long): List<Int> =
        con.prepareStatement(
            "WITH RECURSIVE sub(id) AS (SELECT ? UNION ALL SELECT c.id FROM category c JOIN sub ON c.parentId = sub.id) " +
                "SELECT b.id FROM book b WHERE b.categoryId IN (SELECT id FROM sub) ORDER BY b.id",
        ).use { ps ->
            ps.setLong(1, categoryId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getInt(1)) } }
        }
    val queries = File(args[1]).readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    fun ms(t: Long) = (System.nanoTime() - t) / 1_000_000

    for (cat in args[2].split(',').map { it.toLong() }) {
        val books = booksUnder(cat)
        val filter = IntPoint.newSetQuery("book_id", *books.toIntArray())
        // How many vectors of each field the filter keeps
        val counts =
            listOf("vec", "vec_base").associateWith { field ->
                var n = 0
                val w = searcher.createWeight(searcher.rewrite(filter), ScoreMode.COMPLETE_NO_SCORES, 1f)
                for (leaf in reader.leaves()) {
                    val values = leaf.reader().getByteVectorValues(field) ?: continue
                    val bits = BitSet()
                    w.scorer(leaf)?.iterator()?.let { while (true) { val d = it.nextDoc(); if (d == Int.MAX_VALUE) break; bits.set(d) } }
                    val it = values.iterator()
                    while (true) {
                        val d = it.nextDoc()
                        if (d == Int.MAX_VALUE) break
                        if (bits[d]) n++
                    }
                    System.err.println("$field: ${values.size()} vectors in all")
                }
                n
            }
        println("## category $cat: ${books.size} books; filtered vectors $counts")
        // The filter's docs per field, for an exact search: the ground truth, and what Lucene falls back to
        val w = searcher.createWeight(searcher.rewrite(filter), ScoreMode.COMPLETE_NO_SCORES, 1f)
        fun exact(field: String, target: ByteArray): List<Int> {
            val heap = java.util.PriorityQueue<Pair<Int, Float>>(compareBy { it.second })
            for (leaf in reader.leaves()) {
                val values = leaf.reader().getByteVectorValues(field) ?: continue
                val docs = w.scorer(leaf)?.iterator() ?: continue
                val it = values.iterator()
                var d = docs.nextDoc()
                while (d != Int.MAX_VALUE) {
                    val v = if (it.docID() < d) it.advance(d) else it.docID()
                    if (v == Int.MAX_VALUE) break
                    if (v == d) {
                        val score = VectorSimilarityFunction.COSINE.compare(target, values.vectorValue(it.index()))
                        if (heap.size < 150) heap.add((leaf.docBase + d) to score)
                        else if (score > heap.peek().second) { heap.poll(); heap.add((leaf.docBase + d) to score) }
                        d = docs.nextDoc()
                    } else {
                        d = docs.advance(v)
                    }
                }
            }
            return heap.map { it.first }
        }
        val noFiltered = KnnSearchStrategy.Hnsw(0)
        for (q in queries) {
            val vec = provider.embed(q)
            repeat(2) { round ->
                val res = LinkedHashMap<String, Pair<Long, Set<Int>>>()
                fun run(name: String, block: () -> List<Int>) {
                    val t = System.nanoTime()
                    val ids = block().toSet()
                    res[name] = ms(t) to ids
                }
                for (field in listOf("vec", "vec_base")) {
                    run("$field:unfiltered") { searcher.search(KnnByteVectorQuery(field, vec, 150), 150).scoreDocs.map { it.doc } }
                    run("$field:default") { searcher.search(KnnByteVectorQuery(field, vec, 150, filter), 150).scoreDocs.map { it.doc } }
                    run("$field:hnsw0") { searcher.search(KnnByteVectorQuery(field, vec, 150, filter, noFiltered), 150).scoreDocs.map { it.doc } }
                    run("$field:exact") { exact(field, vec) }
                }
                if (round == 1) {
                    val line = res.entries.joinToString("  ") { (name, r) ->
                        val truth = res[name.substringBefore(':') + ":exact"]!!.second
                        val recall = if (name.endsWith("unfiltered") || truth.isEmpty()) "" else "/" + (r.second.count { it in truth } * 100 / truth.size) + "%"
                        "$name ${r.first}ms$recall"
                    }
                    println("$q\t$line")
                    System.out.flush()
                }
            }
        }
    }
}

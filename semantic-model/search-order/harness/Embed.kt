import io.github.kdroidfilter.seforimapp.features.search.semantic.OnnxEmbeddingProvider
import io.github.kdroidfilter.seforimapp.features.search.semantic.OpenModelCleaner
import io.github.kdroidfilter.seforimapp.features.search.semantic.OpenModelSpec
import java.io.File
import java.nio.file.Path

// The similarity of a query to texts, as the app embeds them: the query with the model's query prefix, each text as a
// document (OpenModelCleaner, then the document prefix), by the float model before int8. Prints the cosine and Lucene's
// COSINE score, (1 + cos) / 2, the scale of the dense hits.
//
// args: pardes-model dir, a UTF-8 file (the query on the first line, then a text a line: a line's HTML as stored),
// out file
fun main(args: Array<String>) {
    val spec = OpenModelSpec.read(Path.of(args[0])) ?: error("no pardes-model.json in ${args[0]}")
    val lines = File(args[1]).readLines().filter { it.isNotBlank() }
    OnnxEmbeddingProvider.open(spec).use { model ->
        val q = model.embedQuery(lines[0])
        val texts = lines.drop(1)
        val docs = model.embedPassages(texts) ?: error("the model embeds no passages")
        File(args[2]).bufferedWriter().use { out ->
            out.write("query: ${lines[0]}\n")
            texts.forEachIndexed { i, t ->
                val cos = q.indices.sumOf { (q[it] * docs[i][it]).toDouble() }
                out.write("%.3f (Lucene %.3f)  %s\n".format(cos, (1 + cos) / 2, OpenModelCleaner.clean(t)))
            }
        }
    }
}

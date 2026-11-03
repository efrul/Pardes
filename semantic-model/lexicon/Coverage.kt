import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.search.MagicDictionaryIndex
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.file.Path

// args: lexical.db, index dir, kk.tsv (word, infl, syn, ketiv, folds), out.tsv
fun main(args: Array<String>) {
    IndexSearcher.setMaxClauseCount(100_000)
    val dict = MagicDictionaryIndex.load(HebrewTextUtils::normalizeHebrew, Path.of(args[0])) ?: error("no dict")
    val reader = DirectoryReader.open(FSDirectory.open(Path.of(args[1])))
    val s = IndexSearcher(reader)
    fun df(t: String) = reader.docFreq(Term("text", t))
    fun count(terms: Collection<String>): Int {
        val ts = terms.filter { it.isNotBlank() && !it.contains(' ') }.distinct()
        if (ts.isEmpty()) return 0
        val any = BooleanQuery.Builder().apply { ts.forEach { add(TermQuery(Term("text", it)), BooleanClause.Occur.SHOULD) } }.build()
        val q = BooleanQuery.Builder().add(TermQuery(Term("type", "line")), BooleanClause.Occur.FILTER).add(any, BooleanClause.Occur.MUST).build()
        return s.count(q)
    }
    File(args[3]).bufferedWriter().use { out ->
        out.write("word\tpardesAll\tkept\tlinesKept\tlinesAllPardes\tlinesKeptPlusInfl\tlinesKeptPlusKetiv\tlinesKeptPlusSyn\tnewInfl\tnewInflInIndex\tnewKetivInIndex\ttopNewInfl\ttopNewKetiv\ttopNewSyn\n")
        for (line in File(args[2]).readLines()) {
            val c = line.split('\t')
            val w = c[0]
            val infl = c.getOrElse(1) { "" }.split(' ').filter { it.isNotBlank() }
            val syn = c.getOrElse(2) { "" }.split(' ').filter { it.isNotBlank() }
            val ket = c.getOrElse(3) { "" }.split(' ').filter { it.isNotBlank() }
            val e = dict.expansionFor(w)
            val ordered = if (e == null) listOf(w) else LinkedHashSet<String>().apply { add(w); addAll(e.base); addAll(e.surface); addAll(e.variants) }.toList()
            val kept = ordered.take(32)
            val pset = ordered.toSet()
            val newInfl = infl.filter { it !in pset }
            val newKet = ket.filter { it !in pset }
            val newSyn = syn.filter { it !in pset && it !in infl }
            fun top(xs: List<String>) = xs.map { it to df(it) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(6).joinToString(" ") { "${it.first}:${it.second}" }
            out.write(listOf(w, ordered.size, kept.size, count(kept), count(ordered), count(kept + newInfl), count(kept + newKet), count(kept + newSyn),
                newInfl.size, newInfl.count { df(it) > 0 }, newKet.count { df(it) > 0 }, top(newInfl), top(newKet), top(newSyn)).joinToString("\t") + "\n")
        }
    }
}

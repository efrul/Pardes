import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.file.Path
// args: index dir, words file (one per line), out (word \t df)
fun main(args: Array<String>) {
    val r = DirectoryReader.open(FSDirectory.open(Path.of(args[0])))
    File(args[2]).bufferedWriter().use { o -> File(args[1]).forEachLine { w -> if (w.isNotBlank()) o.write("$w\t${r.docFreq(Term("text", w))}\n") } }
}

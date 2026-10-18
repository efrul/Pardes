import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.TokenizerFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.VectorUtil;

/**
 * JVM smoke test of the :lucene-android jar (single-file program, no build needed):
 * java -cp build/libs/lucene-android-10.5.2-android.jar smoke/LuceneSmoke.java <index dir> [text field] [stored field]
 *
 * <p>Reads a copy of a desktop index (default fields: those of seforim.db.lookup.lucene; the stored text is re-analyzed into the query terms) through
 * NIOFSDirectory with Term/Phrase/Prefix queries and a doc-values sort, then writes, deletes and
 * merges a small index to cover the write path. Any failed expectation throws.
 * With -Dlucene.android.forceFallbacks=true the jar runs its Android 11/12 code in place of the
 * API 33 JDK methods (see AndroidCompat); the output must not change apart from the "jar:" line.
 */
public class LuceneSmoke {
  public static void main(String[] args) throws Exception {
    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
    final Path indexDir = Paths.get(args[0]);
    final String textField = args.length > 1 ? args[1] : "q";
    final String storedField = args.length > 2 ? args[2] : "book_title";
    final Analyzer analyzer = new StandardAnalyzer();

    final boolean patched = LuceneSmoke.class.getClassLoader()
        .getResource("org/apache/lucene/util/AndroidCompat.class") != null;
    System.out.println("jar: " + (patched ? "lucene-android" + (api33() ? "" : " (API 30-32 code)") : "original lucene-core")
        + ", FSDirectory.open -> " + FSDirectory.open(indexDir).getClass().getSimpleName());
    check(!patched || FSDirectory.open(indexDir) instanceof NIOFSDirectory, "FSDirectory.open is NIOFS");
    System.out.println("tokenizers (SPI): " + TokenizerFactory.availableTokenizers());
    System.out.println("codecs (SPI): " + Codec.availableCodecs());
    System.out.println("dotProduct: " + VectorUtil.dotProduct(new float[] {1, 2}, new float[] {3, 4}));

    try (Directory dir = new NIOFSDirectory(indexDir);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      final SegmentInfos infos = SegmentInfos.readLatestCommit(dir);
      for (SegmentCommitInfo si : infos) {
        System.out.println("segment " + si.info.name + ": codec " + si.info.getCodec().getName()
            + ", " + si.info.maxDoc() + " docs, sort " + si.info.getIndexSort());
      }
      final IndexSearcher searcher = new IndexSearcher(reader);
      System.out.println("docs: " + reader.numDocs());

      // A stored text of two words or more (e.g. a book title) re-analyzed gives terms that must be found again.
      String text = null;
      List<String> tokens = List.of();
      for (int doc = 0; doc < reader.maxDoc() && tokens.size() < 2; doc++) {
        final String stored = reader.storedFields().document(doc).get(storedField);
        if (stored != null) {
          text = stored;
          tokens = analyze(analyzer, textField, stored);
        }
      }
      check(text != null, "a document with stored field " + storedField);
      System.out.println("stored '" + text + "' -> " + tokens);
      check(tokens.size() >= 2, "at least two tokens");

      final int termHits = searcher.count(new TermQuery(new Term(textField, tokens.get(0))));
      final PhraseQuery phrase = new PhraseQuery(textField, tokens.get(0), tokens.get(1));
      final int phraseHits = searcher.count(phrase);
      final int sloppyHits = searcher.count(new PhraseQuery(3, textField, tokens.get(1), tokens.get(0)));
      final String prefix = tokens.get(0).substring(0, Math.min(2, tokens.get(0).length()));
      final int prefixHits = searcher.count(new PrefixQuery(new Term(textField, prefix)));
      System.out.println("TermQuery " + tokens.get(0) + ": " + termHits);
      System.out.println("PhraseQuery " + phrase + ": " + phraseHits);
      System.out.println("sloppy PhraseQuery (slop 3, reversed): " + sloppyHits);
      System.out.println("PrefixQuery " + prefix + "*: " + prefixHits);
      check(termHits > 0 && phraseHits > 0 && sloppyHits > 0 && prefixHits >= termHits, "hits");

      final Sort sort = new Sort(new SortField("is_base_book", SortField.Type.INT, true),
          new SortField("order_index", SortField.Type.INT), new SortField("book_id", SortField.Type.LONG));
      final TopDocs sorted = searcher.search(new TermQuery(new Term("type", "book")), 3, sort);
      for (ScoreDoc hit : sorted.scoreDocs) {
        System.out.println("sorted book: " + reader.storedFields().document(hit.doc).get("book_title"));
      }
    }

    // Write path: compound files, deletions (.liv), merge, plus the in-memory directory.
    final Path tmp = Files.createTempDirectory("lucene-android-smoke");
    for (Directory dir : new Directory[] {new NIOFSDirectory(tmp), new ByteBuffersDirectory()}) {
      try (dir) {
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
          for (int i = 0; i < 50; i++) {
            final Document doc = new Document();
            doc.add(new StringField("id", "d" + i, Field.Store.YES));
            doc.add(new TextField("body", "בראשית ברא אלהים " + i, Field.Store.NO));
            writer.addDocument(doc);
            if (i % 10 == 9) {
              writer.commit();
            }
          }
          writer.deleteDocuments(new Term("id", "d3"));
          writer.commit();
          writer.forceMerge(1);
        }
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
          final int hits = new IndexSearcher(reader).count(new PhraseQuery("body", "בראשית", "ברא"));
          final String runtime = SegmentInfos.readLatestCommit(dir).info(0).info.getDiagnostics()
              .get("java.runtime.version");
          System.out.println(dir.getClass().getSimpleName() + " written: " + reader.numDocs()
              + " docs, phrase hits " + hits + ", diagnostics java.runtime.version=" + runtime);
          check(reader.numDocs() == 49 && hits == 49, "written index");
        }
      }
    }
    try (var files = Files.list(tmp)) {
      files.forEach(p -> p.toFile().delete());
    }
    Files.delete(tmp);
    System.out.println("OK");
  }

  private static List<String> analyze(Analyzer analyzer, String field, String text) throws Exception {
    final List<String> tokens = new ArrayList<>();
    try (TokenStream stream = analyzer.tokenStream(field, text)) {
      final CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
      stream.reset();
      while (stream.incrementToken()) {
        tokens.add(term.toString());
      }
      stream.end();
    }
    return tokens;
  }

  /** AndroidCompat.API_33 (package-private): false where the jar replaces the API 33 JDK methods. */
  private static boolean api33() throws ReflectiveOperationException {
    final java.lang.reflect.Field field =
        Class.forName("org.apache.lucene.util.AndroidCompat").getDeclaredField("API_33");
    field.setAccessible(true);
    return field.getBoolean(null);
  }

  private static void check(boolean ok, String what) {
    if (!ok) {
      throw new AssertionError("failed: " + what);
    }
  }
}

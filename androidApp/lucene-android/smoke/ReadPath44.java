import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.QueryBuilder;

/**
 * JVM test of the Lucene calls of the main app's search, for the Android 4.4 jar (single-file program):
 * ./run-desugared.sh ReadPath44.java <full-text index> <lookup index> [single] [heap] [buffer=N] [skipVectors] [legacy]
 *
 * <p>The calls of app/.../android/search/LuceneSearchEngine.kt and LuceneLookupSearchService.kt, in the same shapes:
 * NIOFSDirectory, SearcherManager with a partitioned IndexSearcher on an executor (or a single one: "single", as on a
 * one-core phone) and a custom caching policy, StandardAnalyzer + QueryBuilder, Term/Boolean/Phrase/MultiPhrase/
 * Prefix/Fuzzy/Boost/IntPoint exact and set queries, stored fields, doc values, a custom CollectorManager, count()
 * and searchAfter(); then the lookup index with an LRUQueryCache and a doc-values Sort. run-desugared.sh runs it
 * with the jar and this class desugared for minSdk 19 (D8 with desugar_jdk_libs_nio), on the desugared library's
 * emulated file system as on Android 4.4. Expected figures: README.md.
 *
 * <p>Options after the two folders: {@code single}, one slice; {@code heap}, both directories wrapped in an
 * AndroidHeapFilesDirectory for {@link #HEAP_EXTENSIONS}; {@code buffer=N}, the system property
 * lucene.android.niofsBufferSize; {@code skipVectors}, lucene.android.skipVectors (the index may then lack its vector
 * files); {@code legacy}, single, heap and skipVectors, as Pardes for Android 4.4 runs (legacy/.../data/LegacySearch.kt,
 * which leaves the buffers at 1 KB). The figures do not change with any of them.
 */
public class ReadPath44 {
  static boolean single;
  static boolean heap;
  /** As LegacySearch's HEAP_EXTENSIONS and HEAP_MAX_FILE_BYTES (Pardes for Android 4.4). */
  static final Set<String> HEAP_EXTENSIONS = Set.of("tip", "kdi", "fdx", "tmd", "psm", "nvm", "dvm", "kdm", "fdm");
  static final long HEAP_MAX_FILE_BYTES = 2L << 20;
  static final ExecutorService executor = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });

  public static void main(String[] a) throws Exception {
    List<String> options = Arrays.asList(a).subList(2, a.length);
    boolean legacy = options.contains("legacy");
    single = legacy || options.contains("single");
    heap = legacy || options.contains("heap");
    for (String o : options) if (o.startsWith("buffer=")) System.setProperty("lucene.android.niofsBufferSize", o.substring(7));
    if (legacy || options.contains("skipVectors")) System.setProperty("lucene.android.skipVectors", "true");
    System.out.println("mode " + (single ? "single (cores == 1: IndexSearcher(reader))" : "partitioned (IndexSearcher(reader, executor), slices with partitions)")
        + (heap ? ", heap files" : "") + ", buffer " + System.getProperty("lucene.android.niofsBufferSize", "default")
        + (Boolean.getBoolean("lucene.android.skipVectors") ? ", vectors skipped" : ""));
    IndexSearcher.setMaxClauseCount(Math.max(IndexSearcher.getMaxClauseCount(), 4096));
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = open(Paths.get(a[0]))) {
      SearcherManager mgr = new SearcherManager(dir, new SearcherFactory() {
        @Override public IndexSearcher newSearcher(IndexReader reader, IndexReader prev) {
          int docsPerSlice = Math.max(1000, reader.maxDoc() / 4 + 1);
          IndexSearcher s = single ? new IndexSearcher(reader) : new IndexSearcher(reader, executor) {
            @Override protected LeafSlice[] slices(List<LeafReaderContext> leaves) { return slices(leaves, docsPerSlice, Integer.MAX_VALUE, true); }
          };
          s.setQueryCachingPolicy(new QueryCachingPolicy() {
            public void onUse(Query q) {}
            public boolean shouldCache(Query q) {
              return (q instanceof TermQuery tq && tq.getTerm().field().equals("type")) || (q instanceof PointRangeQuery pq && pq.getField().equals("is_base_book"));
            }
          });
          return s;
        }
      });
      mgr.addListener(new ReferenceManager.RefreshListener() { public void beforeRefresh() {} public void afterRefresh(boolean d) {} });
      mgr.maybeRefresh();
      IndexSearcher s = mgr.acquire();
      try {
        System.out.println("docs " + s.getIndexReader().numDocs() + ", slices " + s.getSlices().length);
        // a stored title, analyzed
        StoredFields sf0 = s.storedFields();
        String title = null;
        for (int i = 0; i < 50 && title == null; i++) { Document d = sf0.document(s.getIndexReader().maxDoc() - 1 - i); title = d.get("book_title"); }
        List<String> toks = analyze(analyzer, "text", title + " " + title);
        String t0 = toks.get(0);
        // title suggestions
        Query sug = new BooleanQuery.Builder().add(new TermQuery(new Term("type", "book_title")), BooleanClause.Occur.FILTER)
            .add(new PrefixQuery(new Term("title", t0.substring(0, Math.min(2, t0.length())))), BooleanClause.Occur.MUST).build();
        TopDocs top = s.search(sug, 20);
        for (ScoreDoc sd : top.scoreDocs) s.storedFields().document(sd.doc);
        System.out.println("suggestions " + top.totalHits);
        // main query, as buildSearchContext
        BooleanQuery.Builder b = new BooleanQuery.Builder();
        b.add(new TermQuery(new Term("type", "line")), BooleanClause.Occur.FILTER);
        b.add(IntPoint.newExactQuery("is_base_book", 1), BooleanClause.Occur.FILTER);
        b.add(IntPoint.newSetQuery("book_id", 7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91, 98), BooleanClause.Occur.FILTER);
        b.add(IntPoint.newExactQuery("category_id", 12), BooleanClause.Occur.SHOULD);
        QueryBuilder qb = new QueryBuilder(analyzer);
        Query phrase = qb.createPhraseQuery("text", String.join(" ", toks.subList(0, Math.min(2, toks.size()))), 3);
        if (phrase != null) b.add(phrase, BooleanClause.Occur.SHOULD);
        Query exact = qb.createPhraseQuery("text", t0, 0);
        if (exact != null) b.add(exact, BooleanClause.Occur.SHOULD);
        Query bq = qb.createBooleanQuery("text", String.join(" ", toks), BooleanClause.Occur.SHOULD);
        if (bq != null) b.add(bq, BooleanClause.Occur.SHOULD);
        MultiPhraseQuery.Builder mp = new MultiPhraseQuery.Builder();
        for (String t : toks.subList(0, Math.min(2, toks.size()))) mp.add(new Term[] {new Term("text", t), new Term("text", t + "ים")});
        mp.setSlop(2);
        b.add(mp.build(), BooleanClause.Occur.SHOULD);
        b.add(new BoostQuery(new TermQuery(new Term("text", t0)), 2.0f), BooleanClause.Occur.SHOULD);
        b.add(new PrefixQuery(new Term("text", t0.substring(0, Math.min(3, t0.length())))), BooleanClause.Occur.SHOULD);
        b.add(new FuzzyQuery(new Term("text", t0), 1), BooleanClause.Occur.SHOULD);
        if (t0.length() >= 4) b.add(new TermQuery(new Term("text_ng4", t0.substring(0, 4))), BooleanClause.Occur.SHOULD);
        Query q = b.build();
        TopDocs res = s.search(q, 25);
        System.out.println("results " + res.totalHits + " " + res.scoreDocs.length);
        StoredFields stored = s.storedFields();
        for (ScoreDoc sd : res.scoreDocs) {
          Document d = stored.document(sd.doc);
          d.getField("book_id").numericValue().longValue(); d.get("book_title");
        }
        if (res.scoreDocs.length > 0) System.out.println("after " + s.searchAfter(res.scoreDocs[res.scoreDocs.length - 1], q, 25).scoreDocs.length);
        // facets: a custom CollectorManager over doc values + stored fields
        Query match = new BooleanQuery.Builder().add(new TermQuery(new Term("type", "line")), BooleanClause.Occur.FILTER)
            .add(new TermQuery(new Term("text", t0)), BooleanClause.Occur.MUST).build();
        int facets = s.search(match, new CollectorManager<Collector, Integer>() {
          public Collector newCollector() {
            return new Collector() {
              int n;
              public LeafCollector getLeafCollector(LeafReaderContext ctx) throws IOException {
                NumericDocValues dv = ctx.reader().getNumericDocValues("book_id");
                StoredFields lsf = ctx.reader().storedFields();
                FieldInfos fis = ctx.reader().getFieldInfos();
                fis.fieldInfo("book_id").getDocValuesType();
                return new LeafCollector() {
                  public void setScorer(Scorable sc) {}
                  public void collect(int doc) throws IOException {
                    if (dv != null && dv.advanceExact(doc)) dv.longValue();
                    lsf.document(doc, Set.of("ancestor_category_ids"));
                    n++;
                  }
                };
              }
              public ScoreMode scoreMode() { return ScoreMode.COMPLETE_NO_SCORES; }
            };
          }
          public Integer reduce(Collection<Collector> cs) { return cs.size(); }
        });
        System.out.println("facet collectors " + facets);
        Query bookLines = new BooleanQuery.Builder().add(new TermQuery(new Term("type", "line")), BooleanClause.Occur.FILTER)
            .add(IntPoint.newExactQuery("book_id", 7), BooleanClause.Occur.FILTER).build();
        System.out.println("count " + s.count(bookLines) + " / " + s.count(new MatchAllDocsQuery()));
      } finally {
        mgr.release(s);
      }
      mgr.maybeRefresh();
      mgr.close();
    }
    // lookup index (LuceneLookupSearchService)
    try (Directory dir = open(Paths.get(a[1]))) {
      SearcherManager mgr = new SearcherManager(dir, new SearcherFactory() {
        @Override public IndexSearcher newSearcher(IndexReader reader, IndexReader prev) {
          IndexSearcher s = new IndexSearcher(reader);
          s.setQueryCache(new LRUQueryCache(256, 8L * 1024 * 1024));
          return s;
        }
      });
      mgr.maybeRefresh();
      IndexSearcher s = mgr.acquire();
      try {
        Sort bookSort = new Sort(new SortField("is_base_book", SortField.Type.INT, true), new SortField("order_index", SortField.Type.INT), new SortField("book_id", SortField.Type.LONG));
        Sort authorSort = new Sort(new SortField("book_count", SortField.Type.LONG, true));
        List<String> toks = analyze(analyzer, "q", "בראשית רבה");
        Query q = new BooleanQuery.Builder().add(new TermQuery(new Term("type", "book")), BooleanClause.Occur.FILTER)
            .add(new PrefixQuery(new Term("q", toks.get(0))), BooleanClause.Occur.MUST).build();
        TopDocs td = s.search(q, 20, bookSort);
        for (ScoreDoc sd : td.scoreDocs) s.storedFields().document(sd.doc);
        TopDocs ta = s.search(new TermQuery(new Term("type", "author")), 10, authorSort);
        System.out.println("lookup " + td.totalHits + ", authors " + ta.totalHits);
      } finally {
        mgr.release(s);
      }
      mgr.close();
    }
    executor.shutdown();
  }

  static Directory open(Path path) throws IOException {
    Directory dir = new NIOFSDirectory(path);
    return heap ? new AndroidHeapFilesDirectory(dir, HEAP_EXTENSIONS, HEAP_MAX_FILE_BYTES) : dir;
  }

  static List<String> analyze(Analyzer an, String field, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = an.tokenStream(field, text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(term.toString());
      ts.end();
    }
    return out;
  }
}

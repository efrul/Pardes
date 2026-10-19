import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.AndroidCompat;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CompatVarHandle;

/**
 * JVM test of the Android 8.0-12 (API 26-32) code of the :lucene-android jar against the JDK
 * methods it stands in for (single-file program, no build needed):
 * java -XX:-OmitStackTraceInFastThrow -Dlucene.android.forceFallbacks=true -cp build/libs/lucene-android-10.5.2-android.jar smoke/CompatFallbacks.java [rounds] [seed]
 *
 * <p>Calls every API 33 stand-in of AndroidCompat and CompatVarHandle with random and edge-case
 * inputs and compares each outcome with the JDK method's: the result (for set, the bytes written),
 * or the exception's class and message (class only for NullPointerException, whose helpful message
 * names local variables, and WrongMethodTypeException/UnsupportedOperationException). Then CheckIndex
 * on a small in-memory index runs the rewritten PrintStream/ByteArrayOutputStream.toString call
 * sites and the Lucene code behind the stand-ins. Without the property it checks the API 33+ path
 * (the JDK methods behind the stand-ins) the same way. Any difference throws.
 */
public class CompatFallbacks {
  private static Random random;
  private static long comparisons;

  public static void main(String[] args) throws Exception {
    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
    // Once an implicit exception (ArrayStoreException from toArray, ...) is frequent, HotSpot's compiled code throws a
    // preallocated one without a message, so the JDK call and its stand-in could differ by when C2 got to them.
    if (!ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-XX:-OmitStackTraceInFastThrow")) {
      System.err.println("Run with -XX:-OmitStackTraceInFastThrow: exception messages are compared.");
      System.exit(2);
    }
    final int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
    final long seed = args.length > 1 ? Long.parseLong(args[1]) : new Random().nextLong();
    random = new Random(seed);
    final Field api33 = AndroidCompat.class.getDeclaredField("API_33");
    api33.setAccessible(true);
    System.out.println(
        (api33.getBoolean(null) ? "API 33+ path (JDK methods)" : "API 26-32 fallbacks")
            + ", " + rounds + " rounds, seed " + seed);
    arrays(rounds);
    fma(rounds);
    varHandles(rounds);
    others(rounds);
    checkIndex();
    System.out.println("OK, " + comparisons + " comparisons");
  }

  // ---- Arrays ----

  private static void arrays(int rounds) {
    for (int r = 0; r < rounds; r++) {
      final byte[] a1 = bytes(null);
      final byte[] b1 = random.nextInt(10) == 0 ? a1 : bytes(a1);
      final int[] x = range(a1);
      final int[] y = range(b1);
      same("mismatch(byte[])", () -> Arrays.mismatch(a1, x[0], x[1], b1, y[0], y[1]),
          () -> AndroidCompat.mismatch(a1, x[0], x[1], b1, y[0], y[1]));
      same("compareUnsigned(byte[])", () -> Arrays.compareUnsigned(a1, x[0], x[1], b1, y[0], y[1]),
          () -> AndroidCompat.compareUnsigned(a1, x[0], x[1], b1, y[0], y[1]));
      same("equals(byte[])", () -> Arrays.equals(a1, x[0], x[1], b1, y[0], y[1]),
          () -> AndroidCompat.equals(a1, x[0], x[1], b1, y[0], y[1]));

      final char[] a2 = chars(null);
      final char[] b2 = random.nextInt(10) == 0 ? a2 : chars(a2);
      final int[] x2 = range(a2);
      final int[] y2 = range(b2);
      same("mismatch(char[])", () -> Arrays.mismatch(a2, x2[0], x2[1], b2, y2[0], y2[1]),
          () -> AndroidCompat.mismatch(a2, x2[0], x2[1], b2, y2[0], y2[1]));
      same("compare(char[])", () -> Arrays.compare(a2, x2[0], x2[1], b2, y2[0], y2[1]),
          () -> AndroidCompat.compare(a2, x2[0], x2[1], b2, y2[0], y2[1]));
      same("equals(char[])", () -> Arrays.equals(a2, x2[0], x2[1], b2, y2[0], y2[1]),
          () -> AndroidCompat.equals(a2, x2[0], x2[1], b2, y2[0], y2[1]));

      final int[] a3 = ints(null);
      final int[] b3 = random.nextInt(10) == 0 ? a3 : ints(a3);
      final int[] x3 = range(a3);
      final int[] y3 = range(b3);
      same("mismatch(int[])", () -> Arrays.mismatch(a3, x3[0], x3[1], b3, y3[0], y3[1]),
          () -> AndroidCompat.mismatch(a3, x3[0], x3[1], b3, y3[0], y3[1]));
      same("compare(int[])", () -> Arrays.compare(a3, x3[0], x3[1], b3, y3[0], y3[1]),
          () -> AndroidCompat.compare(a3, x3[0], x3[1], b3, y3[0], y3[1]));
      same("equals(int[])", () -> Arrays.equals(a3, x3[0], x3[1], b3, y3[0], y3[1]),
          () -> AndroidCompat.equals(a3, x3[0], x3[1], b3, y3[0], y3[1]));

      final long[] a4 = longs(null);
      final long[] b4 = random.nextInt(10) == 0 ? a4 : longs(a4);
      final int[] x4 = range(a4);
      final int[] y4 = range(b4);
      same("compare(long[])", () -> Arrays.compare(a4, x4[0], x4[1], b4, y4[0], y4[1]),
          () -> AndroidCompat.compare(a4, x4[0], x4[1], b4, y4[0], y4[1]));
      same("equals(long[])", () -> Arrays.equals(a4, x4[0], x4[1], b4, y4[0], y4[1]),
          () -> AndroidCompat.equals(a4, x4[0], x4[1], b4, y4[0], y4[1]));
    }
  }

  /** Mostly a valid [from, to) of the array, sometimes from > to, negative or past the end. */
  private static int[] range(Object array) {
    final int length = array == null ? 3 : java.lang.reflect.Array.getLength(array);
    if (random.nextInt(8) == 0) {
      return new int[] {random.nextInt(length + 5) - 2, random.nextInt(length + 5) - 2};
    }
    final int from = random.nextInt(length + 1);
    return new int[] {from, from + random.nextInt(length - from + 1)};
  }

  /**
   * Values for an array: random ones, or from a few extremes so that two arrays share prefixes.
   * Starting from "like" (when given), a copy with an element changed, cut or added.
   */
  private static long[] values(long[] like, long[] extremes, int bits) {
    if (random.nextInt(60) == 0) {
      return null;
    }
    long[] values;
    if (like != null && random.nextInt(3) != 0) {
      values = Arrays.copyOf(like, Math.max(0, like.length + random.nextInt(5) - 2));
      if (values.length > 0 && random.nextBoolean()) {
        values[random.nextInt(values.length)] = value(extremes, bits);
      }
    } else {
      values = new long[random.nextInt(12)];
      for (int i = 0; i < values.length; i++) {
        values[i] = value(extremes, bits);
      }
    }
    return values;
  }

  private static long value(long[] extremes, int bits) {
    return random.nextInt(3) == 0 ? random.nextLong() >> (64 - bits) : extremes[random.nextInt(extremes.length)];
  }

  private static byte[] bytes(byte[] like) {
    final long[] v = values(widen(like), new long[] {0, 1, -1, 127, -128, 64}, 8);
    if (v == null) {
      return null;
    }
    final byte[] out = new byte[v.length];
    for (int i = 0; i < v.length; i++) {
      out[i] = (byte) v[i];
    }
    return out;
  }

  private static char[] chars(char[] like) {
    final long[] v =
        values(like == null ? null : new String(like).chars().asLongStream().toArray(),
            new long[] {0, 1, 'a', 0x7FFF, 0x8000, 0xFFFF}, 16);
    if (v == null) {
      return null;
    }
    final char[] out = new char[v.length];
    for (int i = 0; i < v.length; i++) {
      out[i] = (char) v[i];
    }
    return out;
  }

  private static int[] ints(int[] like) {
    final long[] v =
        values(like == null ? null : Arrays.stream(like).asLongStream().toArray(),
            new long[] {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}, 32);
    return v == null ? null : Arrays.stream(v).mapToInt(l -> (int) l).toArray();
  }

  private static long[] longs(long[] like) {
    return values(like, new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}, 64);
  }

  private static long[] widen(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    final long[] out = new long[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      out[i] = bytes[i];
    }
    return out;
  }

  // ---- Math.fma ----

  private static void fma(int rounds) {
    final float[] specials = {
      0f, -0f, 1f, -1f, Float.MIN_VALUE, -Float.MIN_VALUE, Float.MIN_NORMAL, Float.MAX_VALUE,
      -Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, 0x1p-75f, 3f
    };
    for (float a : specials) {
      for (float b : specials) {
        for (float c : specials) {
          sameFma(a, b, c);
        }
      }
    }
    int naiveWrong = 0;
    for (int r = 0; r < rounds; r++) {
      // Any bit pattern (NaN, infinities, subnormals included).
      sameFma(Float.intBitsToFloat(random.nextInt()), Float.intBitsToFloat(random.nextInt()),
          Float.intBitsToFloat(random.nextInt()));
      // Magnitudes that make the sum cancel or round.
      final float a = (float) random.nextGaussian();
      final float b = (float) random.nextGaussian();
      final float c = random.nextBoolean() ? -a * b : (float) (random.nextGaussian() * Math.scalb(1.0, random.nextInt(60) - 30));
      sameFma(a, b, Math.nextAfter(c, random.nextGaussian()));
      // The double-rounding trap: x + p lands in double on the midpoint between x and its float
      // neighbour, k^2 * 2^(e-70) away from the exact sum (k < 362 keeps that under half a double
      // ulp). An odd x then makes plain (float) (a * b + c) round the wrong way.
      final int e = random.nextInt(200) - 100;
      final float x = Math.scalb((float) ((1 << 23) | (random.nextInt(1 << 22) << 1) | 1), e - 23);
      final int k = 1 + random.nextInt(361);
      final float pa = Math.scalb(1f + k * 0x1p-23f, e - 24);
      final float pb = 1f - k * 0x1p-23f;
      final float sign = random.nextBoolean() ? 1f : -1f;
      // Below the upper midpoint of x, or (negated product) above the lower midpoint of x.
      final boolean up = random.nextBoolean();
      final float fa = sign * (up ? pa : -pa);
      final float fc = sign * x;
      sameFma(fa, pb, fc);
      if (Float.floatToIntBits((float) ((double) fa * pb + fc)) != Float.floatToIntBits(Math.fma(fa, pb, fc))) {
        naiveWrong++;
      }
    }
    System.out.println("fma: " + naiveWrong + " of " + rounds + " trap cases where (float) (a * b + c) in double is wrong");
    check(naiveWrong > rounds / 4, "the trap cases trap the naive formula");
  }

  private static void sameFma(float a, float b, float c) {
    comparisons++;
    final int expected = Float.floatToIntBits(Math.fma(a, b, c));
    final int actual = Float.floatToIntBits(AndroidCompat.fma(a, b, c));
    if (expected != actual) {
      throw new AssertionError("fma(" + a + ", " + b + ", " + c + "): JDK " + Float.intBitsToFloat(expected)
          + ", compat " + Float.intBitsToFloat(actual));
    }
  }

  // ---- VarHandle byte-array views ----

  private static void varHandles(int rounds) {
    final Class<?>[] views = {short[].class, char[].class, int[].class, long[].class, float[].class, double[].class};
    final ByteOrder[] orders = {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN, ByteOrder.nativeOrder()};
    for (Class<?> view : new Class<?>[] {byte[].class, boolean[].class, Object[].class, int.class, String.class, null}) {
      for (ByteOrder order : new ByteOrder[] {ByteOrder.BIG_ENDIAN, null}) {
        same("byteArrayViewVarHandle(" + view + ", " + order + ")",
            () -> MethodHandles.byteArrayViewVarHandle(view, order) != null,
            () -> CompatVarHandle.byteArrayViewVarHandle(view, order) != null);
      }
    }
    for (Class<?> view : views) {
      for (ByteOrder order : orders) {
        final VarHandle jdk = MethodHandles.byteArrayViewVarHandle(view, order);
        final CompatVarHandle compat = CompatVarHandle.byteArrayViewVarHandle(view, order);
        final String name = view.getSimpleName() + " " + order;
        for (int r = 0; r < rounds / 10; r++) {
          final byte[] array = random.nextInt(100) == 0 ? null : new byte[random.nextInt(13)];
          if (array != null) {
            random.nextBytes(array);
          }
          final int index = random.nextInt(array == null ? 3 : array.length + 4) - 2;
          final long value = random.nextLong();
          same(name + " get short", () -> (short) jdk.get(array, index), () -> CompatVarHandle.getShort(compat, array, index));
          same(name + " get int", () -> (int) jdk.get(array, index), () -> CompatVarHandle.getInt(compat, array, index));
          same(name + " get long", () -> (long) jdk.get(array, index), () -> CompatVarHandle.getLong(compat, array, index));
          same(name + " set short",
              () -> set(array, copy -> jdk.set(copy, index, (short) value)),
              () -> set(array, copy -> CompatVarHandle.setShort(compat, copy, index, (short) value)));
          same(name + " set int",
              () -> set(array, copy -> jdk.set(copy, index, (int) value)),
              () -> set(array, copy -> CompatVarHandle.setInt(compat, copy, index, (int) value)));
          same(name + " set long",
              () -> set(array, copy -> jdk.set(copy, index, value)),
              () -> set(array, copy -> CompatVarHandle.setLong(compat, copy, index, value)));
        }
      }
    }
  }

  private interface ByteArrayWrite {
    void write(byte[] copy);
  }

  /** Writes into a copy of the array and returns the copy's bytes. */
  private static String set(byte[] array, ByteArrayWrite write) {
    final byte[] copy = array == null ? null : array.clone();
    write.write(copy);
    return Arrays.toString(copy);
  }

  // ---- toArray, getOpaque, ByteArrayOutputStream.toString ----

  private static void others(int rounds) {
    final List<IntFunction<Object[]>> generators =
        List.of(String[]::new, Object[]::new, CharSequence[]::new, n -> new String[3], n -> new Integer[n]);
    for (int r = 0; r < rounds / 10; r++) {
      final List<String> words = new ArrayList<>();
      for (int i = random.nextInt(6); i > 0; i--) {
        words.add("w" + random.nextInt(20));
      }
      final List<Collection<String>> collections =
          List.of(new ArrayList<>(words), new LinkedList<>(words), List.copyOf(words), Arrays.asList(words.toArray(new String[0])),
              new HashSet<>(words), new TreeSet<>(words), new LinkedHashSet<>(words), Set.copyOf(words),
              new PriorityQueue<>(words), new ArrayDeque<>(words), Collections.unmodifiableList(words));
      for (Collection<String> collection : collections) {
        for (IntFunction<Object[]> generator : generators) {
          same("toArray " + collection.getClass().getSimpleName(),
              () -> describe(collection.toArray(generator)), () -> describe(AndroidCompat.toArray(collection, generator)));
        }
        same("toArray(null)", () -> collection.toArray((IntFunction<Object[]>) null),
            () -> AndroidCompat.toArray(collection, (IntFunction<Object[]>) null));
      }

      final AtomicInteger atomic = new AtomicInteger(random.nextInt());
      same("getOpaque", atomic::getOpaque, () -> AndroidCompat.getOpaque(atomic));

      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      final byte[] bytes = new byte[random.nextInt(40)];
      random.nextBytes(bytes);
      out.write(bytes, 0, bytes.length);
      for (Charset charset : List.of(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.UTF_16,
          StandardCharsets.US_ASCII, Charset.forName("windows-1255"))) {
        same("ByteArrayOutputStream.toString(" + charset + ")", () -> out.toString(charset),
            () -> AndroidCompat.toString(out, charset));
      }
    }
  }

  private static String describe(Object[] array) {
    return array.getClass().getComponentType().getSimpleName() + Arrays.toString(array);
  }

  // ---- Lucene ----

  /**
   * CheckIndex with two threads and verbose output: its per-segment PrintStream(..., UTF_8) and
   * ByteArrayOutputStream.toString(UTF_8) are rewritten call sites, blocktree Stats.toString too.
   */
  private static void checkIndex() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        for (int i = 0; i < 2000; i++) {
          final Document doc = new Document();
          doc.add(new StringField("id", "d" + i, Store.YES));
          doc.add(new TextField("body", "בראשית ברא אלהים " + i + " " + random.nextInt(100), Store.YES));
          doc.add(new IntPoint("n", i));
          doc.add(new NumericDocValuesField("dv", random.nextLong()));
          doc.add(new SortedDocValuesField("sdv", new BytesRef("v" + random.nextInt(50))));
          writer.addDocument(doc);
          if (i % 500 == 499) {
            writer.commit();
          }
        }
        writer.deleteDocuments(new Term("id", "d7"));
        writer.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        final int hits = new IndexSearcher(reader).count(new PhraseQuery("body", "בראשית", "ברא"));
        check(reader.numDocs() == 1999 && hits == 1999, "written index: " + reader.numDocs() + " docs, " + hits + " hits");
      }
      final ByteArrayOutputStream log = new ByteArrayOutputStream();
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setThreadCount(2);
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS);
        checker.setInfoStream(new PrintStream(log, true, StandardCharsets.UTF_8), true);
        final CheckIndex.Status status = checker.checkIndex();
        check(status.clean, "CheckIndex clean:\n" + log.toString(StandardCharsets.UTF_8));
        System.out.println("CheckIndex: clean, " + status.segmentInfos.size() + " segments, "
            + log.toString(StandardCharsets.UTF_8).lines().count() + " lines of output");
      }
    }
  }

  // ---- comparing ----

  /** The result of a call, or its exception as class and message. */
  private static Object outcome(Supplier<?> call) {
    try {
      return call.get();
    } catch (NullPointerException | WrongMethodTypeException | UnsupportedOperationException e) {
      return e.getClass().getName();
    } catch (RuntimeException e) {
      return e.getClass().getName() + ": " + e.getMessage();
    }
  }

  private static void same(String what, Supplier<?> jdk, Supplier<?> compat) {
    comparisons++;
    final Object expected = outcome(jdk);
    final Object actual = outcome(compat);
    if (!Objects.deepEquals(expected, actual)) {
      throw new AssertionError(what + ": JDK " + expected + ", compat " + actual);
    }
  }

  private static void check(boolean ok, String what) {
    if (!ok) {
      throw new AssertionError("failed: " + what);
    }
  }
}

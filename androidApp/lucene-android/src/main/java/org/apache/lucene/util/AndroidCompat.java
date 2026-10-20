/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Checksum;

/**
 * Android: static stand-ins for JDK methods that lucene-core 10.5 calls but Android does not have.
 * The :lucene-android rewriter redirects each call site to the method of the same name here (the
 * receiver becomes the first parameter); behavior matches the JDK methods.
 *
 * <p>The first group is missing even on Android 13 (API 34/35 methods) and always runs here. The
 * second group exists from API 33: there each method hands over to the JDK method through {@link
 * AndroidCompatApi33}, so Android 13+ runs what it ran before; Android 8.0 to 12 (API 26-32) run
 * the equivalent code here.
 *
 * <p>Not here: the Java 10-11 methods that AGP's D8 backports at the app's minSdk ({@code
 * List/Set/Map.copyOf}, {@code String.isBlank}, {@code Predicate.not}, {@code
 * Math.multiplyExact(long, int)}, and at minSdk 26 also the API 30 {@code List.of} and {@code
 * Objects.check*}). D8 rewrites those call sites in the APK for every Android version, Android
 * 13 included, with the same results for what Lucene passes; only reflection could reach the
 * platform method there, on hot paths (every IOContext).
 */
public final class AndroidCompat {
  private AndroidCompat() {}

  /**
   * System property that makes {@link #API_33} false on any VM, so the API 26-32 code below can be
   * tested on a JVM: {@code -Dlucene.android.forceFallbacks=true}.
   */
  public static final String FORCE_FALLBACKS = "lucene.android.forceFallbacks";

  /**
   * True on Android 13 (API 33) and later, and on a JVM (there is no android.os.Build): the JDK
   * methods of {@link AndroidCompatApi33} exist. android.os.Build is read reflectively, once,
   * because this jar is built without android.jar. Always false in the Android 4.4 jar ({@link
   * AndroidCompatFlags#polymorphicCalls}), whose AndroidCompatApi33 is a stub, even where that
   * edition runs on Android 13.
   */
  static final boolean API_33 =
      !Boolean.getBoolean(FORCE_FALLBACKS) && AndroidCompatFlags.polymorphicCalls() && sdkInt() >= 33;

  /**
   * True on Android 11 (API 30) and later, and on a JVM: a constructor's MethodHandle may be given
   * another return type with asType. The ART of Android 8 and 9 creates the instance from the
   * handle's type (seen on the API 26 and 28 emulators; Android 10 was not checked, so it is treated
   * the same), so there AttributeFactory uses the reflected constructor instead. {@link
   * #FORCE_FALLBACKS} makes it false too, and so does the Android 4.4 jar, whose
   * AndroidCompatMethodHandles is a stub.
   */
  static final boolean API_30 =
      !Boolean.getBoolean(FORCE_FALLBACKS) && AndroidCompatFlags.polymorphicCalls() && sdkInt() >= 30;

  private static int sdkInt() {
    final Class<?> version;
    try {
      version = Class.forName("android.os.Build$VERSION");
    } catch (ClassNotFoundException e) {
      return Integer.MAX_VALUE; // not Android
    }
    try {
      return version.getField("SDK_INT").getInt(null);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      return 0; // Android, level unknown: the fallbacks, which run on every level
    }
  }

  /** {@code HashMap.newHashMap(int)}: Java 19, API 35. */
  public static <K, V> HashMap<K, V> newHashMap(int numMappings) {
    if (numMappings < 0) {
      throw new IllegalArgumentException("Negative number of mappings: " + numMappings);
    }
    return new HashMap<>((int) Math.ceil(numMappings / 0.75d));
  }

  /** {@code LongBuffer.put(int, long[], int, int)}: Java 16, API 35. */
  public static LongBuffer put(LongBuffer buffer, int index, long[] src, int offset, int length) {
    Objects.checkFromIndexSize(index, length, buffer.limit());
    Objects.checkFromIndexSize(offset, length, src.length);
    buffer.duplicate().position(index).put(src, offset, length);
    return buffer;
  }

  /** {@code ByteBuffer.get(int, byte[], int, int)}: Java 13, API 35. */
  public static ByteBuffer get(ByteBuffer buffer, int index, byte[] dst, int offset, int length) {
    Objects.checkFromIndexSize(index, length, buffer.limit());
    Objects.checkFromIndexSize(offset, length, dst.length);
    buffer.duplicate().position(index).get(dst, offset, length);
    return buffer;
  }

  /** {@code Stream.toList()}: Java 16, API 34. */
  public static <T> List<T> toList(Stream<T> stream) {
    return Collections.unmodifiableList(stream.collect(Collectors.toList()));
  }

  /** {@code Checksum.update(ByteBuffer)}: Java 9, API 34. */
  public static void update(Checksum checksum, ByteBuffer buffer) {
    final int pos = buffer.position();
    final int limit = buffer.limit();
    final int rem = limit - pos;
    if (rem <= 0) {
      return;
    }
    if (buffer.hasArray()) {
      checksum.update(buffer.array(), pos + buffer.arrayOffset(), rem);
    } else {
      final byte[] chunk = new byte[Math.min(rem, 4096)];
      while (buffer.hasRemaining()) {
        final int length = Math.min(buffer.remaining(), chunk.length);
        buffer.get(chunk, 0, length);
        checksum.update(chunk, 0, length);
      }
    }
    buffer.position(limit);
  }

  /** {@code StringBuilder.isEmpty()} (from {@code CharSequence.isEmpty()}): Java 15, API 35. */
  public static boolean isEmpty(CharSequence chars) {
    return chars.length() == 0;
  }

  /** {@code List.addFirst(E)}: Java 21, API 35. */
  public static <E> void addFirst(List<E> list, E element) {
    list.add(0, element);
  }

  /** {@code List.getFirst()}: Java 21, API 35. */
  public static <E> E getFirst(List<E> list) {
    if (list.isEmpty()) {
      throw new NoSuchElementException();
    }
    return list.get(0);
  }

  // ---- API 33: the JDK method on Android 13+, the code below on Android 8.0 to 12 ----

  /** {@code Arrays.mismatch(byte[], int, int, byte[], int, int)}: Java 9, API 33. */
  public static int mismatch(byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.mismatch(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    final int aLength = aTo - aFrom;
    final int bLength = bTo - bFrom;
    final int length = Math.min(aLength, bLength);
    final int i = firstDifference(a, aFrom, b, bFrom, length);
    return i < 0 && aLength != bLength ? length : i;
  }

  /** {@code Arrays.mismatch(char[], int, int, char[], int, int)}: Java 9, API 33. */
  public static int mismatch(char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.mismatch(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    final int aLength = aTo - aFrom;
    final int bLength = bTo - bFrom;
    final int length = Math.min(aLength, bLength);
    final int i = firstDifference(a, aFrom, b, bFrom, length);
    return i < 0 && aLength != bLength ? length : i;
  }

  /** {@code Arrays.mismatch(int[], int, int, int[], int, int)}: Java 9, API 33. */
  public static int mismatch(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.mismatch(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    final int aLength = aTo - aFrom;
    final int bLength = bTo - bFrom;
    final int length = Math.min(aLength, bLength);
    final int i = firstDifference(a, aFrom, b, bFrom, length);
    return i < 0 && aLength != bLength ? length : i;
  }

  /** {@code Arrays.compareUnsigned(byte[], int, int, byte[], int, int)}: Java 9, API 33. */
  public static int compareUnsigned(byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.compareUnsigned(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    final int aLength = aTo - aFrom;
    final int bLength = bTo - bFrom;
    final int i = firstDifference(a, aFrom, b, bFrom, Math.min(aLength, bLength));
    if (i >= 0) {
      return Byte.toUnsignedInt(a[aFrom + i]) - Byte.toUnsignedInt(b[bFrom + i]);
    }
    return aLength - bLength;
  }

  /** {@code Arrays.compare(char[], int, int, char[], int, int)}: Java 9, API 33. */
  public static int compare(char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.compare(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    final int aLength = aTo - aFrom;
    final int bLength = bTo - bFrom;
    final int i = firstDifference(a, aFrom, b, bFrom, Math.min(aLength, bLength));
    if (i >= 0) {
      return Character.compare(a[aFrom + i], b[bFrom + i]);
    }
    return aLength - bLength;
  }

  /** {@code Arrays.compare(int[], int, int, int[], int, int)}: Java 9, API 33. */
  public static int compare(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.compare(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    final int aLength = aTo - aFrom;
    final int bLength = bTo - bFrom;
    final int i = firstDifference(a, aFrom, b, bFrom, Math.min(aLength, bLength));
    if (i >= 0) {
      return Integer.compare(a[aFrom + i], b[bFrom + i]);
    }
    return aLength - bLength;
  }

  /** {@code Arrays.compare(long[], int, int, long[], int, int)}: Java 9, API 33. */
  public static int compare(long[] a, int aFrom, int aTo, long[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.compare(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    final int aLength = aTo - aFrom;
    final int bLength = bTo - bFrom;
    final int i = firstDifference(a, aFrom, b, bFrom, Math.min(aLength, bLength));
    if (i >= 0) {
      return Long.compare(a[aFrom + i], b[bFrom + i]);
    }
    return aLength - bLength;
  }

  /** {@code Arrays.equals(byte[], int, int, byte[], int, int)}: Java 9, API 33. */
  public static boolean equals(byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.equals(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    return aTo - aFrom == bTo - bFrom && firstDifference(a, aFrom, b, bFrom, aTo - aFrom) < 0;
  }

  /** {@code Arrays.equals(char[], int, int, char[], int, int)}: Java 9, API 33. */
  public static boolean equals(char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.equals(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    return aTo - aFrom == bTo - bFrom && firstDifference(a, aFrom, b, bFrom, aTo - aFrom) < 0;
  }

  /** {@code Arrays.equals(int[], int, int, int[], int, int)}: Java 9, API 33. */
  public static boolean equals(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.equals(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    return aTo - aFrom == bTo - bFrom && firstDifference(a, aFrom, b, bFrom, aTo - aFrom) < 0;
  }

  /** {@code Arrays.equals(long[], int, int, long[], int, int)}: Java 9, API 33. */
  public static boolean equals(long[] a, int aFrom, int aTo, long[] b, int bFrom, int bTo) {
    if (API_33) {
      return AndroidCompatApi33.equals(a, aFrom, aTo, b, bFrom, bTo);
    }
    rangeCheck(a.length, aFrom, aTo);
    rangeCheck(b.length, bFrom, bTo);
    return aTo - aFrom == bTo - bFrom && firstDifference(a, aFrom, b, bFrom, aTo - aFrom) < 0;
  }

  /** Arrays.rangeCheck: the exceptions of the Arrays range methods, in the JDK's order. */
  private static void rangeCheck(int arrayLength, int fromIndex, int toIndex) {
    if (fromIndex > toIndex) {
      throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
    }
    if (fromIndex < 0) {
      throw new ArrayIndexOutOfBoundsException(fromIndex);
    }
    if (toIndex > arrayLength) {
      throw new ArrayIndexOutOfBoundsException(toIndex);
    }
  }

  /** Index of the first difference of a[aFrom..] and b[bFrom..] within length, else -1. */
  private static int firstDifference(byte[] a, int aFrom, byte[] b, int bFrom, int length) {
    for (int i = 0; i < length; i++) {
      if (a[aFrom + i] != b[bFrom + i]) {
        return i;
      }
    }
    return -1;
  }

  private static int firstDifference(char[] a, int aFrom, char[] b, int bFrom, int length) {
    for (int i = 0; i < length; i++) {
      if (a[aFrom + i] != b[bFrom + i]) {
        return i;
      }
    }
    return -1;
  }

  private static int firstDifference(int[] a, int aFrom, int[] b, int bFrom, int length) {
    for (int i = 0; i < length; i++) {
      if (a[aFrom + i] != b[bFrom + i]) {
        return i;
      }
    }
    return -1;
  }

  private static int firstDifference(long[] a, int aFrom, long[] b, int bFrom, int length) {
    for (int i = 0; i < length; i++) {
      if (a[aFrom + i] != b[bFrom + i]) {
        return i;
      }
    }
    return -1;
  }

  /**
   * {@code Math.fma(float, float, float)}: Java 9, API 33. Below API 33 Lucene never calls it: the
   * only caller (DefaultVectorUtilSupport, float vectors) first checks
   * Constants.HAS_FAST_SCALAR_FMA, which needs Hotspot's UseFMA flag. It is exact anyway, a * b + c
   * rounded once to float like the JDK method: the product of two floats fits a double exactly, the
   * sum is rounded to a double "to odd" (the exact error of the double sum, from TwoSum, sets the
   * last bit), and rounding that to float is then correct, a double having more than float's 24 +
   * 2 bits (Boldo and Melquiond, "Emulation of FMA and correctly rounded sums: proved algorithms
   * using rounding to odd", 2008). Plain (float) (a * b + c) in double can round twice the wrong
   * way.
   */
  public static float fma(float a, float b, float c) {
    if (API_33) {
      return AndroidCompatApi33.fma(a, b, c);
    }
    final double product = (double) a * (double) b;
    final double sum = product + c;
    if (Double.isNaN(sum) || Double.isInfinite(sum)) {
      return (float) sum;
    }
    final double cPart = sum - product;
    final double error = (product - (sum - cPart)) + (c - cPart);
    if (error != 0 && (Double.doubleToRawLongBits(sum) & 1) == 0) {
      return (float) (error > 0 ? Math.nextUp(sum) : Math.nextDown(sum));
    }
    return (float) sum;
  }

  /** {@code AtomicInteger.getOpaque()}: Java 9, API 33. A volatile read is at least as strong. */
  public static int getOpaque(AtomicInteger value) {
    if (API_33) {
      return AndroidCompatApi33.getOpaque(value);
    }
    return value.get();
  }

  /** {@code Collection.toArray(IntFunction)}: Java 11, API 33. Below, its JDK default method. */
  public static <T> T[] toArray(Collection<?> collection, IntFunction<T[]> generator) {
    if (API_33) {
      return AndroidCompatApi33.toArray(collection, generator);
    }
    return collection.toArray(generator.apply(0));
  }

  /** {@code ByteArrayOutputStream.toString(Charset)}: Java 10, API 33. */
  public static String toString(ByteArrayOutputStream out, Charset charset) {
    if (API_33) {
      return AndroidCompatApi33.toString(out, charset);
    }
    // As the JDK method: the bytes so far, malformed input replaced.
    return new String(out.toByteArray(), charset);
  }
}

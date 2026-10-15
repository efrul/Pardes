/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Checksum;

/**
 * Android: static stand-ins for JDK methods that lucene-core 10.4 calls but Android 13 (API 33)
 * does not have. The :lucene-android rewriter redirects each call site to the method of the same
 * name here (the receiver becomes the first parameter); behavior matches the JDK methods.
 */
public final class AndroidCompat {
  private AndroidCompat() {}

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
}

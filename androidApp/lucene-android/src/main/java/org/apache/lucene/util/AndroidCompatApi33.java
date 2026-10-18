/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * Android: the JDK methods lucene-core calls that ART only has from API 33 (Android 13), one static
 * method each. {@link AndroidCompat} and {@link CompatVarHandle} call them only when {@link
 * AndroidCompat#API_33}, so on Android 11 and 12 this class is never loaded. Its references, which
 * do not resolve there, are kept out of the classes that do run: an unresolved method makes ART's
 * verifier fail the whole class softly, which then runs with extra checks. check-min-api.sh checks
 * this class against API 33 and everything else against API 30.
 */
final class AndroidCompatApi33 {
  private AndroidCompatApi33() {}

  static int mismatch(byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    return Arrays.mismatch(a, aFrom, aTo, b, bFrom, bTo);
  }

  static int mismatch(char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    return Arrays.mismatch(a, aFrom, aTo, b, bFrom, bTo);
  }

  static int mismatch(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    return Arrays.mismatch(a, aFrom, aTo, b, bFrom, bTo);
  }

  static int compareUnsigned(byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    return Arrays.compareUnsigned(a, aFrom, aTo, b, bFrom, bTo);
  }

  static int compare(char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    return Arrays.compare(a, aFrom, aTo, b, bFrom, bTo);
  }

  static int compare(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    return Arrays.compare(a, aFrom, aTo, b, bFrom, bTo);
  }

  static int compare(long[] a, int aFrom, int aTo, long[] b, int bFrom, int bTo) {
    return Arrays.compare(a, aFrom, aTo, b, bFrom, bTo);
  }

  static boolean equals(byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    return Arrays.equals(a, aFrom, aTo, b, bFrom, bTo);
  }

  static boolean equals(char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    return Arrays.equals(a, aFrom, aTo, b, bFrom, bTo);
  }

  static boolean equals(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    return Arrays.equals(a, aFrom, aTo, b, bFrom, bTo);
  }

  static boolean equals(long[] a, int aFrom, int aTo, long[] b, int bFrom, int bTo) {
    return Arrays.equals(a, aFrom, aTo, b, bFrom, bTo);
  }

  static float fma(float a, float b, float c) {
    return Math.fma(a, b, c);
  }

  static int getOpaque(AtomicInteger value) {
    return value.getOpaque();
  }

  static <T> T[] toArray(Collection<?> collection, IntFunction<T[]> generator) {
    return collection.toArray(generator);
  }

  static String toString(ByteArrayOutputStream out, Charset charset) {
    return out.toString(charset);
  }

  // The VarHandle travels as an Object, so CompatVarHandle's own field and signatures stay free of
  // the type. Each call keeps the exact descriptor of the call site it replaces.

  static Object byteArrayViewVarHandle(Class<?> viewArrayClass, ByteOrder order) {
    return MethodHandles.byteArrayViewVarHandle(viewArrayClass, order);
  }

  static short getShort(Object handle, byte[] array, int index) {
    return (short) ((VarHandle) handle).get(array, index);
  }

  static int getInt(Object handle, byte[] array, int index) {
    return (int) ((VarHandle) handle).get(array, index);
  }

  static long getLong(Object handle, byte[] array, int index) {
    return (long) ((VarHandle) handle).get(array, index);
  }

  static void setShort(Object handle, byte[] array, int index, short value) {
    ((VarHandle) handle).set(array, index, value);
  }

  static void setInt(Object handle, byte[] array, int index, int value) {
    ((VarHandle) handle).set(array, index, value);
  }

  static void setLong(Object handle, byte[] array, int index, long value) {
    ((VarHandle) handle).set(array, index, value);
  }
}

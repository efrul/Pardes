/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * Android: the Android 4.4 jar's (jarApi19 in ../build.gradle.kts, minSdk 19) class of this name,
 * compiled in place of src/main/java's: the same methods, none of them called, since
 * AndroidCompat.API_33 is false in this jar ({@link AndroidCompatFlags#polymorphicCalls}). D8 cannot
 * dex the VarHandle get/set of the main one below API 26, and AGP stops the build on them.
 */
final class AndroidCompatApi33 {
  private AndroidCompatApi33() {}

  private static UnsupportedOperationException api33() {
    return new UnsupportedOperationException("no API 33 calls in the Android 4.4 jar");
  }

  static int mismatch(byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    throw api33();
  }

  static int mismatch(char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    throw api33();
  }

  static int mismatch(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    throw api33();
  }

  static int compareUnsigned(byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    throw api33();
  }

  static int compare(char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    throw api33();
  }

  static int compare(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    throw api33();
  }

  static int compare(long[] a, int aFrom, int aTo, long[] b, int bFrom, int bTo) {
    throw api33();
  }

  static boolean equals(byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    throw api33();
  }

  static boolean equals(char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    throw api33();
  }

  static boolean equals(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    throw api33();
  }

  static boolean equals(long[] a, int aFrom, int aTo, long[] b, int bFrom, int bTo) {
    throw api33();
  }

  static float fma(float a, float b, float c) {
    throw api33();
  }

  static int getOpaque(AtomicInteger value) {
    throw api33();
  }

  static <T> T[] toArray(Collection<?> collection, IntFunction<T[]> generator) {
    throw api33();
  }

  static String toString(ByteArrayOutputStream out, Charset charset) {
    throw api33();
  }

  static Object byteArrayViewVarHandle(Class<?> viewArrayClass, ByteOrder order) {
    throw api33();
  }

  static short getShort(Object handle, byte[] array, int index) {
    throw api33();
  }

  static int getInt(Object handle, byte[] array, int index) {
    throw api33();
  }

  static long getLong(Object handle, byte[] array, int index) {
    throw api33();
  }

  static void setShort(Object handle, byte[] array, int index, short value) {
    throw api33();
  }

  static void setInt(Object handle, byte[] array, int index, int value) {
    throw api33();
  }

  static void setLong(Object handle, byte[] array, int index, long value) {
    throw api33();
  }
}

/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.nio.ByteOrder;

/**
 * Android: stand-in for java.lang.invoke.VarHandle, public on ART only from API 33. lucene-core
 * creates VarHandles only with MethodHandles.byteArrayViewVarHandle (the BitUtil.VH_* fields) and
 * only calls plain get and set on them, with (byte[], int) coordinates and short, int or long
 * values. The rewriter remaps the VarHandle type to this class, the factory to {@link
 * #byteArrayViewVarHandle} and each get/set call site to the static method of its descriptor
 * (receiver first); it fails on any other VarHandle use.
 *
 * <p>On API 33+ (see {@link AndroidCompat#API_33}) the JDK VarHandle does the work, as before.
 * Below, the bytes are assembled here with the JDK's rules: the index check of a byte-array view
 * (index in [0, length - size]), and a call whose value type differs from the view type only works
 * for a widening conversion (an int get on a short view sign-extends, a short value can be stored
 * into an int view...); anything else is a WrongMethodTypeException, as on the JDK. Lucene itself
 * only uses each handle with its own type.
 */
public final class CompatVarHandle {
  private static final boolean API_33 = AndroidCompat.API_33;

  // The view types byteArrayViewVarHandle supports.
  private static final int SHORT = 0;
  private static final int CHAR = 1;
  private static final int INT = 2;
  private static final int LONG = 3;
  private static final int FLOAT = 4;
  private static final int DOUBLE = 5;

  /** The JDK VarHandle on API 33+ (see AndroidCompatApi33 for why it is an Object), else null. */
  private final Object handle;

  private final Class<?> viewArrayClass;
  private final int view;
  private final boolean bigEndian;

  private CompatVarHandle(Object handle, Class<?> viewArrayClass, int view, boolean bigEndian) {
    this.handle = handle;
    this.viewArrayClass = viewArrayClass;
    this.view = view;
    this.bigEndian = bigEndian;
  }

  /** {@code MethodHandles.byteArrayViewVarHandle(Class, ByteOrder)}: Java 9, API 33. */
  public static CompatVarHandle byteArrayViewVarHandle(Class<?> viewArrayClass, ByteOrder order) {
    if (API_33) {
      final Object handle = AndroidCompatApi33.byteArrayViewVarHandle(viewArrayClass, order);
      return new CompatVarHandle(handle, viewArrayClass, -1, order == ByteOrder.BIG_ENDIAN);
    }
    if (order == null) {
      throw new NullPointerException();
    }
    if (!viewArrayClass.isArray()) {
      throw new IllegalArgumentException("not an array: " + viewArrayClass);
    }
    final Class<?> component = viewArrayClass.getComponentType();
    final int view;
    if (component == short.class) {
      view = SHORT;
    } else if (component == char.class) {
      view = CHAR;
    } else if (component == int.class) {
      view = INT;
    } else if (component == long.class) {
      view = LONG;
    } else if (component == float.class) {
      view = FLOAT;
    } else if (component == double.class) {
      view = DOUBLE;
    } else {
      throw new UnsupportedOperationException();
    }
    return new CompatVarHandle(null, viewArrayClass, view, order == ByteOrder.BIG_ENDIAN);
  }

  /** {@code (short) handle.get(byte[], int)}: only a short view gives a short. */
  public static short getShort(CompatVarHandle vh, byte[] array, int index) {
    if (API_33) {
      return AndroidCompatApi33.getShort(vh.handle, array, index);
    }
    if (vh.view != SHORT) {
      throw vh.wrongType("(byte[],int)short");
    }
    return read16(array, index, vh.bigEndian);
  }

  /** {@code (int) handle.get(byte[], int)}: an int view, or a short or char view widened. */
  public static int getInt(CompatVarHandle vh, byte[] array, int index) {
    if (API_33) {
      return AndroidCompatApi33.getInt(vh.handle, array, index);
    }
    switch (vh.view) {
      case INT:
        return read32(array, index, vh.bigEndian);
      case SHORT:
        return read16(array, index, vh.bigEndian);
      case CHAR:
        return (char) read16(array, index, vh.bigEndian);
      default:
        throw vh.wrongType("(byte[],int)int");
    }
  }

  /** {@code (long) handle.get(byte[], int)}: a long view, or a short, char or int view widened. */
  public static long getLong(CompatVarHandle vh, byte[] array, int index) {
    if (API_33) {
      return AndroidCompatApi33.getLong(vh.handle, array, index);
    }
    switch (vh.view) {
      case LONG:
        return read64(array, index, vh.bigEndian);
      case INT:
        return read32(array, index, vh.bigEndian);
      case SHORT:
        return read16(array, index, vh.bigEndian);
      case CHAR:
        return (char) read16(array, index, vh.bigEndian);
      default:
        throw vh.wrongType("(byte[],int)long");
    }
  }

  /** {@code handle.set(byte[], int, short)}: any view but char takes a short, widened. */
  public static void setShort(CompatVarHandle vh, byte[] array, int index, short value) {
    if (API_33) {
      AndroidCompatApi33.setShort(vh.handle, array, index, value);
    } else if (vh.view == SHORT) {
      write16(array, index, value, vh.bigEndian);
    } else if (vh.view == CHAR) {
      throw vh.wrongType("(byte[],int,short)void");
    } else {
      storeWidened(vh, array, index, value);
    }
  }

  /** {@code handle.set(byte[], int, int)}: an int view, or a long, float or double view widened. */
  public static void setInt(CompatVarHandle vh, byte[] array, int index, int value) {
    if (API_33) {
      AndroidCompatApi33.setInt(vh.handle, array, index, value);
    } else if (vh.view == INT) {
      write32(array, index, value, vh.bigEndian);
    } else if (vh.view == SHORT || vh.view == CHAR) {
      throw vh.wrongType("(byte[],int,int)void");
    } else {
      storeWidened(vh, array, index, value);
    }
  }

  /** {@code handle.set(byte[], int, long)}: a long view, or a float or double view widened. */
  public static void setLong(CompatVarHandle vh, byte[] array, int index, long value) {
    if (API_33) {
      AndroidCompatApi33.setLong(vh.handle, array, index, value);
    } else if (vh.view == LONG) {
      write64(array, index, value, vh.bigEndian);
    } else if (vh.view == FLOAT || vh.view == DOUBLE) {
      storeWidened(vh, array, index, value);
    } else {
      throw vh.wrongType("(byte[],int,long)void");
    }
  }

  /**
   * Stores a short, int or long value into an int, long, float or double view it widens to. The
   * value is passed as a long, which holds each of them exactly; (float) of that long rounds the
   * same as (float) of the original int or short.
   */
  private static void storeWidened(CompatVarHandle vh, byte[] array, int index, long value) {
    switch (vh.view) {
      case INT:
        write32(array, index, (int) value, vh.bigEndian);
        break;
      case LONG:
        write64(array, index, value, vh.bigEndian);
        break;
      case FLOAT:
        write32(array, index, Float.floatToRawIntBits((float) value), vh.bigEndian);
        break;
      case DOUBLE:
        write64(array, index, Double.doubleToRawLongBits((double) value), vh.bigEndian);
        break;
      default:
        throw new AssertionError(vh.view);
    }
  }

  /** The JDK's check for a view of {@code size} bytes: index in [0, length - size]. */
  private static void checkIndex(byte[] array, int index, int size) {
    final int length = array.length - (size - 1);
    if (index < 0 || index >= length) {
      throw new ArrayIndexOutOfBoundsException(
          "Index " + index + " out of bounds for length " + length);
    }
  }

  private static short read16(byte[] a, int i, boolean bigEndian) {
    checkIndex(a, i, 2);
    return bigEndian
        ? (short) ((a[i] << 8) | (a[i + 1] & 0xFF))
        : (short) ((a[i] & 0xFF) | (a[i + 1] << 8));
  }

  private static int read32(byte[] a, int i, boolean bigEndian) {
    checkIndex(a, i, 4);
    return int32(a, i, bigEndian);
  }

  private static long read64(byte[] a, int i, boolean bigEndian) {
    checkIndex(a, i, 8);
    return bigEndian
        ? ((long) int32(a, i, true) << 32) | (int32(a, i + 4, true) & 0xFFFFFFFFL)
        : (int32(a, i, false) & 0xFFFFFFFFL) | ((long) int32(a, i + 4, false) << 32);
  }

  /** Four bytes at i, unchecked. */
  private static int int32(byte[] a, int i, boolean bigEndian) {
    return bigEndian
        ? (a[i] << 24) | ((a[i + 1] & 0xFF) << 16) | ((a[i + 2] & 0xFF) << 8) | (a[i + 3] & 0xFF)
        : (a[i] & 0xFF) | ((a[i + 1] & 0xFF) << 8) | ((a[i + 2] & 0xFF) << 16) | (a[i + 3] << 24);
  }

  private static void write16(byte[] a, int i, short v, boolean bigEndian) {
    checkIndex(a, i, 2);
    a[bigEndian ? i + 1 : i] = (byte) v;
    a[bigEndian ? i : i + 1] = (byte) (v >>> 8);
  }

  private static void write32(byte[] a, int i, int v, boolean bigEndian) {
    checkIndex(a, i, 4);
    for (int k = 0; k < 4; k++) {
      a[bigEndian ? i + 3 - k : i + k] = (byte) (v >>> (8 * k));
    }
  }

  private static void write64(byte[] a, int i, long v, boolean bigEndian) {
    checkIndex(a, i, 8);
    for (int k = 0; k < 8; k++) {
      a[bigEndian ? i + 7 - k : i + k] = (byte) (v >>> (8 * k));
    }
  }

  // Android: the WrongMethodTypeException (API 26) comes from AndroidCompatMethodHandles, typed RuntimeException here.
  // Dalvik (Android 4.4) types a value of a class it cannot resolve as java.lang.Object, and "throw" of an Object
  // rejects the whole class (VerifyError), so every byte-array view of BitUtil would fail there; and dexopt leaves a
  // class that names a missing class unverified and unoptimized, to verify at every start. Only a wrong view/call type
  // reaches this, never Lucene.
  private RuntimeException wrongType(String callType) {
    return AndroidCompatMethodHandles.wrongMethodType(
        "cannot convert " + viewArrayClass.getSimpleName() + " view to " + callType);
  }
}

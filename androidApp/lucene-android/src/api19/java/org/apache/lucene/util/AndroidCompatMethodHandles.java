/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

/**
 * Android: the Android 4.4 jar's (jarApi19 in ../build.gradle.kts, minSdk 19) class of this name,
 * compiled in place of src/main/java's, without java.lang.invoke (API 26): D8 cannot dex the
 * signature-polymorphic invokeExact below API 26, and a class naming a type Dalvik lacks is left
 * unverified and unoptimized by dexopt. AttributeFactory never calls the first two methods here,
 * since AndroidCompat.API_30 is false in this jar ({@link AndroidCompatFlags#polymorphicCalls}).
 */
final class AndroidCompatMethodHandles {
  private AndroidCompatMethodHandles() {}

  static Object findAttributeImplCtor(Class<? extends AttributeImpl> clazz) {
    throw new UnsupportedOperationException("no MethodHandle in the Android 4.4 jar");
  }

  static AttributeImpl newInstance(Object constructor) {
    throw new UnsupportedOperationException("no MethodHandle in the Android 4.4 jar");
  }

  /**
   * CompatVarHandle's exception for a wrong view or call type: the JDK's WrongMethodTypeException
   * where there is one (API 26, a JVM), looked up by name; else an IllegalStateException.
   */
  static RuntimeException wrongMethodType(String message) {
    try {
      return (RuntimeException)
          Class.forName("java.lang.invoke.WrongMethodTypeException")
              .getConstructor(String.class)
              .newInstance(message);
    } catch (ReflectiveOperationException | LinkageError e) {
      return new IllegalStateException(message);
    }
  }
}

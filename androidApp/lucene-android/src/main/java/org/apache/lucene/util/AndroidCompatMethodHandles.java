/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;

/**
 * Android: the java.lang.invoke code (API 26) of AttributeFactory and CompatVarHandle, one static
 * method each, so that those classes name no java.lang.invoke type. AttributeFactory takes the
 * constructor handles only when {@link AndroidCompat#API_30} and CompatVarHandle the exception only
 * for a wrong call type, which Lucene never makes, so on Android 8.0 to 10 this class is never
 * loaded; the handles travel as Objects, as AndroidCompatApi33's VarHandles do.
 *
 * <p>The Android 4.4 jar (jarApi19 in ../build.gradle.kts) compiles src/api19/java's class of this
 * name instead, which names no java.lang.invoke type either: D8 cannot dex the signature-polymorphic
 * invokeExact below API 26 (AGP stops the build), and there API_30 is false on every level.
 */
final class AndroidCompatMethodHandles {
  private AndroidCompatMethodHandles() {}

  private static final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
  private static final MethodType NO_ARG_CTOR = MethodType.methodType(void.class);
  private static final MethodType NO_ARG_RETURNING_ATTRIBUTEIMPL =
      MethodType.methodType(AttributeImpl.class);

  /** AttributeFactory.findAttributeImplCtor: the class's no-arg constructor, typed ()AttributeImpl. */
  static Object findAttributeImplCtor(Class<? extends AttributeImpl> clazz) {
    try {
      return lookup.findConstructor(clazz, NO_ARG_CTOR).asType(NO_ARG_RETURNING_ATTRIBUTEIMPL);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalArgumentException(
          "Cannot lookup accessible no-arg constructor for: " + clazz.getName(), e);
    }
  }

  /**
   * A new instance from a handle of {@link #findAttributeImplCtor}: the invokeExact of the two call
   * sites it replaces, with their descriptor ()AttributeImpl.
   */
  static AttributeImpl newInstance(Object constructor) throws Throwable {
    return (AttributeImpl) ((MethodHandle) constructor).invokeExact();
  }

  /** CompatVarHandle's exception for a wrong view or call type, as the JDK VarHandle throws. */
  static RuntimeException wrongMethodType(String message) {
    return new WrongMethodTypeException(message);
  }
}

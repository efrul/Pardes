/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

/** An AttributeFactory creates instances of {@link AttributeImpl}s. */
public abstract class AttributeFactory {

  /**
   * Returns an {@link AttributeImpl} for the supplied {@link Attribute} interface class.
   *
   * @throws UndeclaredThrowableException A wrapper runtime exception thrown if the constructor of
   *     the attribute class throws a checked exception. Note that attributes should not throw or
   *     declare checked exceptions; this may be verified and fail early in the future.
   */
  public abstract AttributeImpl createAttributeInstance(Class<? extends Attribute> attClass)
      throws UndeclaredThrowableException;

  // Android: java.lang.invoke is API 26, so the lookup, its two MethodTypes and the invokeExact
  // calls are in AndroidCompatMethodHandles, which only the API 30+ path loads, and the handle
  // travels as an Object. As static fields here they made AttributeFactory's static initializer
  // throw NoClassDefFoundError on Android 4.4-7.1 (no TokenStream, no analyzer); and D8 cannot dex
  // invokeExact below API 26, so the Android 4.4 jar replaces that one class with a stub.
  /**
   * Returns a correctly typed {@link java.lang.invoke.MethodHandle} for the no-arg ctor of the
   * given class.
   */
  static final Object findAttributeImplCtor(Class<? extends AttributeImpl> clazz) {
    return AndroidCompatMethodHandles.findAttributeImplCtor(clazz);
  }

  // Android: below API 30 (AndroidCompat.API_30 false) the attributes come from their reflected
  // public no-arg constructor instead of findAttributeImplCtor's handle. The ART of Android 8 and 9
  // creates the instance of a constructor handle from the handle's type, which asType made
  // AttributeImpl, so every analyzer failed (InstantiationException: "Can't instantiate abstract
  // class", no search).
  static final Constructor<? extends AttributeImpl> findAttributeImplReflectedCtor(
      Class<? extends AttributeImpl> clazz) {
    try {
      return clazz.getConstructor();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "Cannot lookup accessible no-arg constructor for: " + clazz.getName(), e);
    }
  }

  // Android: the reflected constructor's instance, its exceptions thrown as invokeExact throws them.
  static final AttributeImpl newAttributeImpl(Constructor<? extends AttributeImpl> ctor) {
    try {
      return ctor.newInstance();
    } catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new UndeclaredThrowableException(cause);
    } catch (ReflectiveOperationException e) {
      throw new UndeclaredThrowableException(e);
    }
  }

  /**
   * This is the default factory that creates {@link AttributeImpl}s using the class name of the
   * supplied {@link Attribute} interface class by appending <code>Impl</code> to it.
   */
  public static final AttributeFactory DEFAULT_ATTRIBUTE_FACTORY = new DefaultAttributeFactory();

  private static final class DefaultAttributeFactory extends AttributeFactory {
    // Android: CompatClassValue, as the rewriter maps ClassValue (API 34) in the classes it copies;
    // the handles as Objects (see findAttributeImplCtor).
    private final CompatClassValue<Object> constructors =
        new CompatClassValue<Object>() {
          @Override
          protected Object computeValue(Class<?> attClass) {
            return findAttributeImplCtor(findImplClass(attClass.asSubclass(Attribute.class)));
          }
        };

    // Android: the reflected constructors, used below API 30 (see findAttributeImplReflectedCtor).
    private final CompatClassValue<Constructor<? extends AttributeImpl>> reflectedConstructors =
        new CompatClassValue<Constructor<? extends AttributeImpl>>() {
          @Override
          protected Constructor<? extends AttributeImpl> computeValue(Class<?> attClass) {
            return findAttributeImplReflectedCtor(
                findImplClass(attClass.asSubclass(Attribute.class)));
          }
        };

    DefaultAttributeFactory() {}

    @Override
    public AttributeImpl createAttributeInstance(Class<? extends Attribute> attClass) {
      // Android: see findAttributeImplReflectedCtor.
      if (!AndroidCompat.API_30) {
        return newAttributeImpl(reflectedConstructors.get(attClass));
      }
      try {
        // Android: (AttributeImpl) handle.invokeExact(), see findAttributeImplCtor.
        return AndroidCompatMethodHandles.newInstance(constructors.get(attClass));
      } catch (Error | RuntimeException e) {
        throw e;
      } catch (Throwable e) {
        throw new UndeclaredThrowableException(e);
      }
    }

    private Class<? extends AttributeImpl> findImplClass(Class<? extends Attribute> attClass) {
      try {
        return Class.forName(attClass.getName() + "Impl", true, attClass.getClassLoader())
            .asSubclass(AttributeImpl.class);
      } catch (ClassNotFoundException cnfe) {
        throw new IllegalArgumentException(
            "Cannot find implementing class for: " + attClass.getName(), cnfe);
      }
    }
  }

  /**
   * <b>Expert</b>: AttributeFactory returning an instance of the given {@code clazz} for the
   * attributes it implements. For all other attributes it calls the given delegate factory as
   * fallback. This class can be used to prefer a specific {@code AttributeImpl} which combines
   * multiple attributes over separate classes.
   *
   * @lucene.internal
   */
  public abstract static class StaticImplementationAttributeFactory<A extends AttributeImpl>
      extends AttributeFactory {
    private final AttributeFactory delegate;
    private final Class<A> clazz;

    /**
     * <b>Expert</b>: Creates an AttributeFactory returning {@code clazz} as instance for the
     * attributes it implements and for all other attributes calls the given delegate factory.
     */
    protected StaticImplementationAttributeFactory(AttributeFactory delegate, Class<A> clazz) {
      this.delegate = delegate;
      this.clazz = clazz;
    }

    @Override
    public final AttributeImpl createAttributeInstance(Class<? extends Attribute> attClass) {
      return attClass.isAssignableFrom(clazz)
          ? createInstance()
          : delegate.createAttributeInstance(attClass);
    }

    /** Creates an instance of {@code A}. */
    protected abstract A createInstance();

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || other.getClass() != this.getClass()) return false;
      @SuppressWarnings("rawtypes")
      final StaticImplementationAttributeFactory af = (StaticImplementationAttributeFactory) other;
      return this.delegate.equals(af.delegate) && this.clazz == af.clazz;
    }

    @Override
    public int hashCode() {
      return 31 * delegate.hashCode() + clazz.hashCode();
    }
  }

  /**
   * Returns an AttributeFactory returning an instance of the given {@code clazz} for the attributes
   * it implements. The given {@code clazz} must have a public no-arg constructor. For all other
   * attributes it calls the given delegate factory as fallback. This method can be used to prefer a
   * specific {@code AttributeImpl} which combines multiple attributes over separate classes.
   *
   * <p>Please save instances created by this method in a static final field, because on each call,
   * this does reflection for creating a {@link java.lang.invoke.MethodHandle}.
   */
  public static <A extends AttributeImpl> AttributeFactory getStaticImplementation(
      AttributeFactory delegate, Class<A> clazz) {
    // Android: see findAttributeImplReflectedCtor.
    if (!AndroidCompat.API_30) {
      final Constructor<? extends AttributeImpl> reflected = findAttributeImplReflectedCtor(clazz);
      return new StaticImplementationAttributeFactory<A>(delegate, clazz) {
        @Override
        @SuppressWarnings("unchecked")
        protected A createInstance() {
          return (A) newAttributeImpl(reflected);
        }
      };
    }
    final Object constr = findAttributeImplCtor(clazz);
    return new StaticImplementationAttributeFactory<A>(delegate, clazz) {
      @Override
      @SuppressWarnings("unchecked")
      protected A createInstance() {
        try {
          // be explicit with casting, so javac compiles correct call to polymorphic signature:
          // Android: (AttributeImpl) constr.invokeExact(), see findAttributeImplCtor.
          final AttributeImpl impl = AndroidCompatMethodHandles.newInstance(constr);
          // now cast to generic type:
          return (A) impl;
        } catch (Error | RuntimeException e) {
          throw e;
        } catch (Throwable e) {
          throw new UndeclaredThrowableException(e);
        }
      }
    };
  }
}

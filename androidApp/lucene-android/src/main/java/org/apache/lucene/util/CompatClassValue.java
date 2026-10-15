/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Android: java.lang.ClassValue only exists from API 34. The :lucene-android rewriter retargets
 * every ClassValue subclass and call site in lucene-core (AttributeSource, AttributeFactory,
 * VirtualMethod) to this class, which has the same API. Values are kept strongly per Class; Lucene
 * only caches small values for its own attribute/analysis classes, which are never unloaded on ART.
 */
public abstract class CompatClassValue<T> {
  private static final Object NULL = new Object();

  private final ConcurrentHashMap<Class<?>, Object> values = new ConcurrentHashMap<>();

  protected CompatClassValue() {}

  protected abstract T computeValue(Class<?> type);

  @SuppressWarnings("unchecked")
  public T get(Class<?> type) {
    Object value = values.get(type);
    if (value == null) {
      // computeValue runs outside the map lock (it may itself use other ClassValues); like
      // ClassValue, the first value stored wins when two threads race.
      final T computed = computeValue(type);
      final Object previous = values.putIfAbsent(type, computed == null ? NULL : computed);
      value = previous != null ? previous : (computed == null ? NULL : computed);
    }
    return value == NULL ? null : (T) value;
  }

  public void remove(Class<?> type) {
    values.remove(type);
  }
}

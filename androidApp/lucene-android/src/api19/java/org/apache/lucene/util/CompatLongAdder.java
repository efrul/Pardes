/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Android: java.util.concurrent.atomic.LongAdder is API 24 and core library desugaring does not add it. In the
 * Android 4.4 jar (the rewriter's --api19 mode) the :lucene-android rewriter retargets it here (LRUQueryCache's
 * hit/miss counters, built by IndexSearcher's static initializer, and OnHeapHnswGraph). The members lucene-core
 * calls, on one AtomicLong.
 */
public final class CompatLongAdder {
  private final AtomicLong value = new AtomicLong();

  public void add(long x) {
    value.addAndGet(x);
  }

  public void increment() {
    value.incrementAndGet();
  }

  public long sum() {
    return value.get();
  }

  @Override
  public String toString() {
    return Long.toString(sum());
  }
}

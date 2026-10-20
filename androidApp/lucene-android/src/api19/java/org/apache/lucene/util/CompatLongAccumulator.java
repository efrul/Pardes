/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongBinaryOperator;

/**
 * Android: java.util.concurrent.atomic.LongAccumulator is API 24 and core library desugaring does not add it. In the
 * Android 4.4 jar (the rewriter's --api19 mode) the :lucene-android rewriter retargets it here (MaxScoreAccumulator,
 * which every top-docs search creates).
 */
public final class CompatLongAccumulator {
  private final LongBinaryOperator function;
  private final AtomicLong value;

  public CompatLongAccumulator(LongBinaryOperator accumulatorFunction, long identity) {
    this.function = accumulatorFunction;
    this.value = new AtomicLong(identity);
  }

  public void accumulate(long x) {
    long prev;
    long next;
    do {
      prev = value.get();
      next = function.applyAsLong(prev, x);
    } while (next != prev && !value.compareAndSet(prev, next));
  }

  public long get() {
    return value.get();
  }
}

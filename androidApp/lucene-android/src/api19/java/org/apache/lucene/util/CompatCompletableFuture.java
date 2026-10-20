/*
 * Android compatibility code for Apache Lucene, built into the :lucene-android jar.
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Android: java.util.concurrent.CompletableFuture is API 24 and core library desugaring does not add it. In the
 * Android 4.4 jar (the rewriter's --api19 mode) the :lucene-android rewriter retargets it here: the members
 * lucene-core calls (TotalHitCountCollectorManager with intra-segment partitions, MergePolicy's merge futures,
 * CheckIndex's concurrent check), on a latch.
 */
public class CompatCompletableFuture<T> implements Future<T> {
  private final CountDownLatch done = new CountDownLatch(1);
  private volatile boolean completed;
  private volatile T value;
  private volatile Throwable failure;

  public CompatCompletableFuture() {}

  public static <U> CompatCompletableFuture<U> completedFuture(U value) {
    final CompatCompletableFuture<U> f = new CompatCompletableFuture<>();
    f.complete(value);
    return f;
  }

  public static <U> CompatCompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
    final CompatCompletableFuture<U> f = new CompatCompletableFuture<>();
    executor.execute(
        () -> {
          try {
            f.complete(supplier.get());
          } catch (Throwable t) {
            f.completeExceptionally(
                t instanceof CompatCompletionException && t.getCause() != null ? t.getCause() : t);
          }
        });
    return f;
  }

  /** Completes when every given future has; exceptionally with the first failure met. */
  public static CompatCompletableFuture<Void> allOf(CompatCompletableFuture<?>... futures) {
    final CompatCompletableFuture<Void> all = new CompatCompletableFuture<>();
    if (futures.length == 0) {
      all.complete(null);
      return all;
    }
    final java.util.concurrent.atomic.AtomicInteger left =
        new java.util.concurrent.atomic.AtomicInteger(futures.length);
    for (CompatCompletableFuture<?> f : futures) {
      f.whenDone(
          () -> {
            if (f.failure != null) {
              all.completeExceptionally(f.failure);
            } else if (left.decrementAndGet() == 0) {
              all.complete(null);
            }
          });
    }
    return all;
  }

  private final java.util.List<Runnable> callbacks = new java.util.ArrayList<>();

  private void whenDone(Runnable callback) {
    synchronized (callbacks) {
      if (!completed) {
        callbacks.add(callback);
        return;
      }
    }
    callback.run();
  }

  private boolean finish(T v, Throwable t) {
    final java.util.List<Runnable> run;
    synchronized (callbacks) {
      if (completed) {
        return false;
      }
      value = v;
      failure = t;
      completed = true;
      run = new java.util.ArrayList<>(callbacks);
      callbacks.clear();
    }
    done.countDown();
    for (Runnable r : run) {
      r.run();
    }
    return true;
  }

  public boolean complete(T v) {
    return finish(v, null);
  }

  public boolean completeExceptionally(Throwable t) {
    return finish(null, t);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return finish(null, new CancellationException());
  }

  @Override
  public boolean isCancelled() {
    return completed && failure instanceof CancellationException;
  }

  @Override
  public boolean isDone() {
    return completed;
  }

  private T report() throws ExecutionException {
    final Throwable t = failure;
    if (t == null) {
      return value;
    }
    if (t instanceof CancellationException) {
      throw (CancellationException) t;
    }
    throw new ExecutionException(t);
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    done.await();
    return report();
  }

  @Override
  public T get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    if (!done.await(timeout, unit)) {
      throw new TimeoutException();
    }
    return report();
  }

  /** The value if completed normally, else valueIfAbsent; a failure is thrown as CompatCompletionException. */
  public T getNow(T valueIfAbsent) {
    if (!completed) {
      return valueIfAbsent;
    }
    final Throwable t = failure;
    if (t == null) {
      return value;
    }
    if (t instanceof CancellationException) {
      throw (CancellationException) t;
    }
    throw new CompatCompletionException(t);
  }
}
